package com.ello.litertchat

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.SslErrorHandler
import android.net.http.SslError
import android.os.Message
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URI

/** Private browser surface used by the Compose UI. */
internal object LocalBrowserSession {
    @Volatile private var view: WebView? = null
    @Volatile private var pageLoaded: CompletableDeferred<Unit>? = null
    @Volatile private var apiCaptureArgs: org.json.JSONObject? = null
    @Volatile private var apiCapturePageUrl: String = ""
    @Volatile private var apiCaptureHost: String = ""
    private val apiCaptureRequests = mutableListOf<String>()
    private val apiCaptureSamples = mutableListOf<ApiAnythingSample>()
    private data class ApiAnythingSample(val pageUrl: String, val args: org.json.JSONObject, val requestUrls: List<String>)
    private val mutableBrowserState = MutableStateFlow(BrowserUiState())
    val browserState = mutableBrowserState.asStateFlow()

    data class BrowserUiState(
        val url: String = "",
        val title: String = "",
        val progress: Int = 0,
        val loading: Boolean = false,
        val canGoBack: Boolean = false,
        val canGoForward: Boolean = false,
        val error: String? = null,
    )

    @SuppressLint("SetJavaScriptEnabled")
    fun create(context: android.content.Context): WebView {
        val previousUrl = view?.url.orEmpty()
        return WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(true)
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.loadsImagesAutomatically = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.safeBrowsingEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_YES
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(sourceView: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                val parent = sourceView ?: view ?: return false
                val child = WebView(parent.context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                }
                var forwardedUrl = ""
                child.webViewClient = object : WebViewClient() {
                    override fun onPageStarted(childView: WebView?, url: String?, favicon: Bitmap?) {
                        val destination = url.orEmpty()
                        if ((destination.startsWith("https://") || destination.startsWith("http://")) && destination != forwardedUrl) {
                            forwardedUrl = destination
                            parent.post {
                                parent.loadUrl(destination)
                                childView?.stopLoading()
                                childView?.destroy()
                            }
                        }
                    }
                }
                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                transport.webView = child
                resultMsg.sendToTarget()
                return true
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                mutableBrowserState.value = mutableBrowserState.value.copy(progress = newProgress.coerceIn(0, 100))
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                mutableBrowserState.value = mutableBrowserState.value.copy(title = title.orEmpty())
            }
        }
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: android.webkit.WebResourceRequest?): android.webkit.WebResourceResponse? {
                request?.let(::recordApiAnythingRequest)
                return null
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (pageLoaded?.isCompleted != false) pageLoaded = CompletableDeferred()
                mutableBrowserState.value = mutableBrowserState.value.copy(
                    url = url.orEmpty(), title = view?.title.orEmpty(), progress = 5, loading = true, error = null,
                    canGoBack = view?.canGoBack() == true, canGoForward = view?.canGoForward() == true,
                )
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                pageLoaded?.complete(Unit)
                CookieManager.getInstance().flush()
                mutableBrowserState.value = mutableBrowserState.value.copy(
                    url = view?.url ?: url.orEmpty(), title = view?.title.orEmpty(), progress = 100, loading = false,
                    canGoBack = view?.canGoBack() == true, canGoForward = view?.canGoForward() == true,
                )
            }
            override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    pageLoaded?.complete(Unit)
                    mutableBrowserState.value = mutableBrowserState.value.copy(
                        url = request.url.toString(), loading = false, error = error?.description?.toString() ?: "The page could not be loaded.",
                    )
                }
            }

            override fun onReceivedHttpError(view: WebView?, request: android.webkit.WebResourceRequest?, errorResponse: android.webkit.WebResourceResponse?) {
                if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 0) >= 400) {
                    pageLoaded?.complete(Unit)
                    mutableBrowserState.value = mutableBrowserState.value.copy(
                        loading = false, error = "This page returned an error (${errorResponse?.statusCode}).",
                    )
                }
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                handler?.cancel()
                pageLoaded?.complete(Unit)
                mutableBrowserState.value = mutableBrowserState.value.copy(
                    loading = false, error = "The secure connection could not be verified. The page was blocked.",
                )
            }
        }
        view = this
        if (previousUrl.startsWith("https://") || previousUrl.startsWith("http://")) {
            post { if (url.isNullOrBlank()) loadUrl(previousUrl) }
        }
        }
    }

    fun attach(webView: WebView) {
        view = webView
        mutableBrowserState.value = mutableBrowserState.value.copy(
            url = webView.url.orEmpty(), title = webView.title.orEmpty(),
            canGoBack = webView.canGoBack(), canGoForward = webView.canGoForward(),
        )
    }

    suspend fun startApiAnythingCapture(argsJson: String, reset: Boolean): Map<String, String> = withContext(Dispatchers.Main) {
        val web = checkNotNull(view) { "Open the website in Ello's browser before learning an API Anything operation." }
        val pageUrl = web.url.orEmpty()
        val parsed = runCatching { URI(pageUrl) }.getOrNull()
        require(parsed?.scheme.equals("https", true) && !parsed?.host.isNullOrBlank()) {
            "API Anything learning requires an HTTPS page in Ello's visible browser."
        }
        require(CookieManager.getInstance().getCookie(pageUrl).isNullOrBlank()) {
            "This API Anything learning flow only captures logged-out public requests. Sign out of the site, then retry."
        }
        val args = runCatching { org.json.JSONObject(argsJson) }.getOrElse { error("Sample arguments must be a JSON object.") }
        require(args.length() in 1..12) { "Provide 1 to 12 sample arguments that change between the two examples." }
        val sensitive = Regex("(?i)(email|phone|password|passwd|token|secret|auth|session|credential|card|cvv|ssn|aadhaar|passport)")
        val keys = args.keys().asSequence().toList()
        require(keys.all { it.matches(Regex("[A-Za-z][A-Za-z0-9_]{0,39}") ) && !sensitive.containsMatchIn(it) }) {
            "Sample argument names must be simple public search fields; credentials and personal identifiers cannot be captured."
        }
        require(keys.all { args.opt(it) is String && args.optString(it).length >= 3 && !sensitive.containsMatchIn(args.optString(it)) &&
            !args.optString(it).contains('@') && !Regex("\\+?\\d[\\d ().-]{8,}\\d").containsMatchIn(args.optString(it)) }) {
            "Use text examples of at least three characters. Credential-like or personal values are not allowed in capture."
        }
        val storageCheck = evaluateForAgent("""(() => {try{const keys=[...Object.keys(localStorage),...Object.keys(sessionStorage)];return keys.some(k=>/(auth|token|session|credential|password|login)/i.test(k))?'credential-state':'clear'}catch(e){return 'unavailable'}})()""")
        require(storageCheck == "clear" || storageCheck == "unavailable") {
            "This site has sign-in state in browser storage. Use a logged-out browser session for public API learning."
        }
        if (reset) synchronized(apiCaptureSamples) { apiCaptureSamples.clear() }
        synchronized(apiCaptureRequests) { apiCaptureRequests.clear() }
        apiCaptureArgs = args
        apiCapturePageUrl = pageUrl
        apiCaptureHost = parsed.host.lowercase()
        mapOf("status" to "succeeded", "result" to "Public request capture started for ${parsed.host}. Only unsigned HTTPS GET request URLs are kept in memory on this phone; request headers, cookies, and response bodies are never captured. Now run the website search using this example: ${args.keys().asSequence().joinToString { "$it=${args.optString(it)}" } }.")
    }

    fun stopApiAnythingCapture(): Map<String, String> {
        val args = apiCaptureArgs ?: error("There is no API Anything capture running. Start one with the first or second example arguments.")
        apiCaptureArgs = null
        val requests = synchronized(apiCaptureRequests) { apiCaptureRequests.distinct().take(200).toList().also { apiCaptureRequests.clear() } }
        require(requests.isNotEmpty()) { "No eligible public JSON GET requests were captured. Run a site search while capture is active, then stop capture." }
        synchronized(apiCaptureSamples) {
            apiCaptureSamples += ApiAnythingSample(apiCapturePageUrl, org.json.JSONObject(args.toString()), requests)
            while (apiCaptureSamples.size > 2) apiCaptureSamples.removeAt(0)
        }
        return mapOf("status" to "succeeded", "result" to "Captured ${requests.size} eligible public request URLs for sample ${apiCaptureSamples.size}/2. URLs and sample values stay in memory on this phone. Start capture with a second, different example, run that search, stop capture, then call api_anything_learn_operation with a third holdout example.")
    }

    fun apiAnythingCaptureSamples(): List<Triple<String, org.json.JSONObject, List<String>>> = synchronized(apiCaptureSamples) {
        apiCaptureSamples.map { Triple(it.pageUrl, org.json.JSONObject(it.args.toString()), it.requestUrls.toList()) }
    }

    fun clearApiAnythingCaptureSamples() {
        synchronized(apiCaptureSamples) { apiCaptureSamples.clear() }
    }

    private fun recordApiAnythingRequest(request: android.webkit.WebResourceRequest) {
        val args = apiCaptureArgs ?: return
        if (request.method != "GET" || !request.url.scheme.equals("https", true)) return
        val url = request.url
        val host = url.host?.lowercase().orEmpty()
        val captureHost = apiCaptureHost
        if (host != captureHost && !host.endsWith(".$captureHost") && !captureHost.endsWith(".$host")) return
        val headers = request.requestHeaders.orEmpty()
        if (headers.keys.any { it.equals("cookie", true) || it.equals("authorization", true) || it.equals("proxy-authorization", true) }) return
        val accept = headers.entries.firstOrNull { it.key.equals("accept", true) }?.value.orEmpty()
        val path = url.path.orEmpty().lowercase()
        val ajax = headers.entries.any { (name, value) ->
            name.equals("x-requested-with", true) || name.equals("sec-fetch-dest", true) && value.equals("empty", true)
        }
        if (!(accept.contains("json", true) || ajax || path.contains("/api/") || path.endsWith("/graphql") || path.contains("graphql"))) return
        if (path.matches(Regex(".*\\.(js|css|png|jpe?g|gif|webp|svg|woff2?|ttf|ico|map)$")) ||
            Regex("(?i)/(login|auth|account|checkout|payment|order|booking|reservation|appointment)(/|$)").containsMatchIn(path)) return
        val forbidden = Regex("(?i)(token|secret|password|passwd|api.?key|authorization|session|credential|email|phone|mobile|card|cvv|ssn|aadhaar|passport)")
        if (url.queryParameterNames.any(forbidden::containsMatchIn)) return
        if (url.queryParameterNames.flatMap { key -> url.getQueryParameters(key) }.any {
                it.contains('@') || Regex("\\+?\\d[\\d ().-]{8,}\\d").containsMatchIn(it)
            }) return
        val safeUrl = url.toString().takeIf { it.length <= 4096 } ?: return
        synchronized(apiCaptureRequests) {
            if (apiCaptureArgs === args && apiCaptureRequests.size < 200) apiCaptureRequests.add(safeUrl)
        }
    }

    suspend fun navigate(url: String): String = withContext(Dispatchers.Main) {
        val candidate = url.trim().let { if (it.matches(Regex("^[A-Za-z][A-Za-z0-9+.-]*:.*"))) it else "https://$it" }
        val parsed = runCatching { URI(candidate) }.getOrElse { throw IllegalArgumentException("That address is malformed.") }
        require(parsed.scheme.equals("https", true) || parsed.scheme.equals("http", true)) { "Only regular HTTP or HTTPS web pages can be opened." }
        require(!parsed.host.isNullOrBlank() && parsed.userInfo == null) { "That address does not contain a valid website host." }
        val safeUrl = parsed.toASCIIString()
        if (view == null) withTimeoutOrNull(3_000) { while (view == null) delay(50) }
        val web = checkNotNull(view) { "Ello's on-device browser could not open." }
        val deferred = CompletableDeferred<Unit>()
        pageLoaded = deferred
        mutableBrowserState.value = mutableBrowserState.value.copy(url = safeUrl, progress = 0, loading = true, error = null)
        web.loadUrl(safeUrl)
        val loaded = withTimeoutOrNull(15_000) { deferred.await(); true } ?: false
        if (!loaded) {
            mutableBrowserState.value = mutableBrowserState.value.copy(loading = false, error = "This page is taking too long. You can keep waiting or try again.")
            throw IllegalStateException("The page did not finish loading within 15 seconds.")
        }
        safeUrl
    }

    suspend fun agentSnapshot(): String = evaluateForAgent(
        """(() => {
          const visible = e => { const r=e.getBoundingClientRect(), s=getComputedStyle(e); return r.width>0 && r.height>0 && s.visibility!=='hidden' && s.display!=='none'; };
          const nodes=[...document.querySelectorAll('a,button,input,textarea,select,[contenteditable="true"],[role="button"],[role="link"],[role="checkbox"],[role="radio"],[role="combobox"],[role="option"],[role="listbox"],[role="menuitem"],[role="tab"],[aria-haspopup="listbox"]')].filter(visible).slice(0,120);
          const elements=nodes.map((e,i)=>{
            e.setAttribute('data-ello-agent-ref',String(i));
            const associatedLabel=e.labels?.[0]?.innerText||e.closest('label')?.innerText||'';
            const label=(associatedLabel||e.getAttribute('aria-label')||e.getAttribute('placeholder')||e.getAttribute('data-placeholder')||e.getAttribute('title')||e.innerText||e.value||'').trim().replace(/\\s+/g,' ').slice(0,180);
            const type=(e.getAttribute('type')||e.tagName).toLowerCase();
            const href=e.tagName==='A' ? e.href : '';
            const options=e.tagName==='SELECT'?[...e.options].slice(0,30).map(o=>({label:(o.label||o.textContent||'').trim().replace(/\\s+/g,' ').slice(0,120),value:o.value,disabled:o.disabled,selected:o.selected})):[];
            const formSubmit=(e.tagName==='BUTTON'&&e.type==='submit')||(e.tagName==='INPUT'&&['submit','image'].includes((e.type||'').toLowerCase()));
            const disabled=!!e.disabled||e.getAttribute('aria-disabled')==='true';
            return {ref:String(i),tag:e.tagName.toLowerCase(),role:(e.getAttribute('role')||'').slice(0,40),type,label,name:(e.name||'').slice(0,100),placeholder:(e.placeholder||e.getAttribute('data-placeholder')||'').slice(0,120),contenteditable:!!e.isContentEditable,required:!!e.required,formSubmit,href:href.slice(0,500),options,disabled};
          });
          const text=(document.body?.innerText||'').replace(/\b(?:\d[ -]*?){13,19}\b/g,'[payment number hidden]').replace(/\n{3,}/g,'\n\n').slice(0,10000);
          return JSON.stringify({url:location.href,title:document.title,text,elements});
        })()""",
    )

    /** Waits for a visible page-text cue, then returns a fresh snapshot for SPA booking flows. */
    suspend fun agentWait(expectedText: String, timeoutMs: Int): Map<String, String> {
        val cue = expectedText.trim().take(160)
        val timeout = timeoutMs.coerceIn(500, 12_000)
        var matched = cue.isBlank()
        if (cue.isBlank()) {
            delay(timeout.toLong())
        } else {
            val encodedCue = org.json.JSONObject.quote(cue.lowercase())
            matched = withTimeoutOrNull(timeout.toLong()) {
                while (true) {
                    val present = evaluateForAgent(
                        "(document.body?.innerText||'').toLowerCase().includes($encodedCue) ? 'yes' : 'no'",
                    ) == "yes"
                    if (present) return@withTimeoutOrNull true
                    delay(250)
                }
                @Suppress("UNREACHABLE_CODE") false
            } ?: false
        }
        return mapOf(
            "condition" to if (cue.isBlank()) "delay elapsed" else "visible page text contains: $cue",
            "matched" to matched.toString(),
            "page" to agentSnapshot(),
            "status" to "succeeded",
        )
    }

    suspend fun agentClick(ref: String): String = evaluateForAgent(
        """(() => { const e=document.querySelector('[data-ello-agent-ref='+${org.json.JSONObject.quote(ref)}+']'); if(!e) return JSON.stringify({ok:false,error:'That page element is no longer available. Take a fresh page snapshot.'}); if(e.disabled||e.getAttribute('aria-disabled')==='true') return JSON.stringify({ok:false,error:'That page element is disabled.'}); e.click(); return JSON.stringify({ok:true,label:(e.innerText||e.getAttribute('aria-label')||e.value||'').trim().slice(0,180),href:e.href||''}); })()""",
    )

    /** Builds a local-only summary for the approval dialog; form values are not returned to Gemini. */
    suspend fun agentActionPreview(ref: String): String = evaluateForAgent(
        """(() => {
          const e=document.querySelector('[data-ello-agent-ref='+${org.json.JSONObject.quote(ref)}+']');
          if(!e) return JSON.stringify({ok:false,error:'That page element is no longer available. Take a fresh page snapshot.'});
          const form=e.form||e.closest('form');
          const container=form||e.closest('[role="form"],[role="dialog"],dialog,main')||document.body;
          const labelFor=x=>(x.labels?.[0]?.innerText||x.closest('label')?.innerText||x.getAttribute('aria-label')||x.getAttribute('data-placeholder')||x.placeholder||x.name||x.type||x.tagName).trim().replace(/\s+/g,' ').slice(0,100);
          const privateField=/password|one.?time|otp|verification.?code|credit.?card|debit.?card|card.?number|cardholder|cvv|cvc|security.?code|bank.?account|routing.?number|social.?security|passport|tax.?id|national.?id|driver.?license|aadhaar|token|secret/i;
          const candidates=[...container.querySelectorAll('input,textarea,select,[contenteditable="true"]')];
          const fields=candidates.filter(x=>x&&x.type!=='hidden'&&x.type!=='submit'&&x.type!=='button'&&x.type!=='reset'&&x.type!=='file'&&(()=>{const r=x.getBoundingClientRect(),s=getComputedStyle(x);return r.width>0&&r.height>0&&s.visibility!=='hidden'&&s.display!=='none'})()).slice(0,20);
          for(const x of form?.querySelectorAll('[contenteditable="true"]')||[]) if(!fields.includes(x)) fields.push(x);
          const values=fields.map(x=>{
            const label=labelFor(x), type=(x.type||x.tagName).toLowerCase();
            let value='';
            if(privateField.test(label+' '+(x.name||'')+' '+type)) value='[hidden for privacy]';
            else if(type==='checkbox'||type==='radio') value=x.checked?'selected':'not selected';
            else if(x.tagName==='SELECT') value=x.selectedOptions?.[0]?.label||x.value||'';
            else value=x.isContentEditable?x.innerText:(x.value||'');
            return {label,value:String(value).trim().replace(/\s+/g,' ').slice(0,180)};
          }).filter(x=>x.value);
          const action=new URL(form?.action||location.href,location.href);
          return JSON.stringify({ok:true,action:(e.innerText||e.getAttribute('aria-label')||e.value||e.tagName).trim().slice(0,180),method:form?(form.method||'GET').toUpperCase():'UI action',destination:action.origin+action.pathname,fields:values.slice(0,8),context:(container.innerText||'').trim().replace(/\s+/g,' ').slice(0,500)});
        })()""",
    )

    /** API Anything tier-two fallback: fetch a read from a temporary page on the request's own origin. */
    suspend fun apiAnythingBrowserRead(request: okhttp3.Request, maxBytes: Int): String = withContext(Dispatchers.Main) {
        require(request.url.isHttps) { "API Anything browser reads require HTTPS." }
        require(request.method == "GET" || request.method == "POST") { "API Anything browser fallback only supports reads." }
        val origin = request.url.newBuilder().encodedPath("/").query(null).fragment(null).build().toString()
        val expectedOrigin = request.url.scheme + "://" + request.url.host + if (request.url.port == 443) "" else ":${request.url.port}"
        if (view == null) withTimeoutOrNull(3_000L) { while (view == null) delay(50) }
        val context = view?.context ?: throw IllegalStateException("Open Ello's browser once before using its API Anything browser fallback.")
        val temp = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        try {
            val loaded = CompletableDeferred<Unit>()
            temp.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) { loaded.complete(Unit) }
                override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                    if (request?.isForMainFrame == true) loaded.completeExceptionally(IllegalStateException("The API Anything source website could not be opened in its browser."))
                }
            }
            temp.loadUrl(origin)
            withTimeout(15_000L) { loaded.await() }
            val headers = org.json.JSONObject()
            request.headers.names().forEach { name ->
                if (!name.equals("cookie", true) && !name.equals("authorization", true) &&
                    !name.equals("host", true) && !name.equals("content-length", true) &&
                    !name.equals("connection", true) && !name.startsWith("sec-", true)
                ) headers.put(name, request.header(name).orEmpty())
            }
            val bodyBuffer = okio.Buffer()
            request.body?.writeTo(bodyBuffer)
            val urlJson = org.json.JSONObject.quote(request.url.toString())
            val originJson = org.json.JSONObject.quote(expectedOrigin)
            val methodJson = org.json.JSONObject.quote(request.method)
            val headersJson = headers.toString()
            val bodyJson = if (request.body == null) "null" else org.json.JSONObject.quote(bodyBuffer.readUtf8())
            val limit = maxBytes.coerceIn(1, 1_000_000)
            val script = """(async()=>{try{if(location.origin!==$originJson)return JSON.stringify({error:'The website redirected the browser away from the API origin.'});const r=await fetch($urlJson,{method:$methodJson,credentials:'include',headers:$headersJson,body:$bodyJson});const t=await r.text();return JSON.stringify({status:r.status,contentType:r.headers.get('content-type')||'',text:t.slice(0,${limit + 1})})}catch(e){return JSON.stringify({error:String(e).slice(0,300)})}})()"""
            val raw = withTimeout(25_000L) {
                val result = CompletableDeferred<String>()
                temp.evaluateJavascript(script) { result.complete(it.orEmpty()) }
                result.await()
            }
            runCatching { org.json.JSONTokener(raw).nextValue() as? String }.getOrNull() ?: raw
        } finally {
            temp.stopLoading()
            temp.destroy()
        }
    }

    suspend fun agentFill(ref: String, value: String): String {
        val encodedValue = org.json.JSONObject.quote(value.take(2000))
        val encodedRef = org.json.JSONObject.quote(ref)
        return evaluateForAgent(
            """(() => { const e=document.querySelector('[data-ello-agent-ref='+$encodedRef+']'); if(!e) return JSON.stringify({ok:false,error:'That page field is no longer available. Take a fresh page snapshot.'}); const label=e.labels?.[0]?.innerText||e.closest('label')?.innerText||''; const meta=((e.type||'')+' '+(e.name||'')+' '+(e.autocomplete||'')+' '+(e.getAttribute('aria-label')||'')+' '+(e.getAttribute('data-placeholder')||'')+' '+(e.placeholder||'')+' '+label).toLowerCase(); if(/password|one.?time|otp|verification.?code|credit.?card|debit.?card|card.?number|cardholder|cvv|cvc|security.?code|bank.?account|routing.?number|social.?security|passport|tax.?id|national.?id|driver.?license|aadhaar/.test(meta)) return JSON.stringify({ok:false,error:'Ello will not fill passwords, verification codes, payment or bank details, or government identifiers.'}); const editable=e.isContentEditable; if(!editable&&(!['INPUT','TEXTAREA'].includes(e.tagName) || ['hidden','submit','button','checkbox','radio','file'].includes((e.type||'').toLowerCase()))) return JSON.stringify({ok:false,error:'That control is not a supported text field.'}); if(editable){e.focus();e.textContent=$encodedValue;e.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:$encodedValue}));e.dispatchEvent(new Event('change',{bubbles:true}));}else{const setter=Object.getOwnPropertyDescriptor(Object.getPrototypeOf(e),'value')?.set; if(setter) setter.call(e,$encodedValue); else e.value=$encodedValue; e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true}));} return JSON.stringify({ok:true,field:(label||e.getAttribute('aria-label')||e.getAttribute('data-placeholder')||e.name||e.placeholder||e.type||'editable text field').trim().slice(0,120)}); })()""",
        )
    }

    /** Inserts saved payment data locally after matching its precise card field type. */
    suspend fun agentFillPayment(ref: String, value: String, field: String): String {
        require(field in setOf("card_number", "cardholder", "expiry", "expiry_month", "expiry_year")) { "That payment field type is not supported." }
        val encodedValue = org.json.JSONObject.quote(value.take(40))
        val encodedRef = org.json.JSONObject.quote(ref)
        val encodedKind = org.json.JSONObject.quote(field)
        return evaluateForAgent(
            """(() => {
              const e=document.querySelector('[data-ello-agent-ref='+$encodedRef+']');
              if(!e) return JSON.stringify({ok:false,error:'That payment field is no longer available. Take a fresh browser snapshot.'});
              const label=(e.labels?.[0]?.innerText||e.closest('label')?.innerText||e.getAttribute('aria-label')||e.getAttribute('placeholder')||e.name||'').trim();
              const meta=(label+' '+(e.type||'')+' '+(e.name||'')+' '+(e.autocomplete||'')+' '+(e.getAttribute('autocomplete')||'')+' '+(e.id||'')).toLowerCase();
              if(/cvv|cvc|security.?code|card.?verification|pin|password|one.?time|otp/.test(meta)) return JSON.stringify({ok:false,error:'Ello never fills a card security code, PIN, password, or verification code. Enter it directly on the website if required.'});
              const kind=$encodedKind;
              const matches=kind==='card_number'?/(cc-number|card.?number|card.?no|credit.?card|debit.?card|\bpan\b)/.test(meta)
                :kind==='cardholder'?/(cc-name|cardholder|name.?on.?card|card.?name)/.test(meta)
                :kind==='expiry'?/(cc-exp|expir|valid.?thru|valid.?through)/.test(meta)&&!/(month|year)/.test(meta)
                :kind==='expiry_month'?/(cc-exp-month|expir|valid.?thru|valid.?through)/.test(meta)&&/(month|mm)/.test(meta)
                :kind==='expiry_year'?/(cc-exp-year|expir|valid.?thru|valid.?through)/.test(meta)&&/(year|yyyy|yy)/.test(meta):false;
              if(!matches) return JSON.stringify({ok:false,error:'That visible field does not clearly match the selected card detail. Ello did not fill it.'});
              const rect=e.getBoundingClientRect(),style=getComputedStyle(e);
              if(!(rect.width>0&&rect.height>0&&style.visibility!=='hidden'&&style.display!=='none')) return JSON.stringify({ok:false,error:'That payment field is not visible.'});
              if(e.tagName==='SELECT'){
                const wanted=String($encodedValue).trim().toLowerCase();
                const numeric=Number(wanted);
                const months=['january','february','march','april','may','june','july','august','september','october','november','december'];
                const option=[...e.options].find(o=>{
                  if(o.disabled)return false;
                  const v=(o.value||'').trim().toLowerCase(),t=(o.label||o.textContent||'').trim().toLowerCase();
                  if(v===wanted||t===wanted)return true;
                  if(kind==='expiry_month'&&numeric>=1&&numeric<=12)return Number(v)===numeric||Number(t)===numeric||t===months[numeric-1]||months[numeric-1].startsWith(t);
                  if(kind==='expiry_year'&&numeric>=2000)return Number(v)===numeric||Number(t)===numeric||Number(v)===numeric%100||Number(t)===numeric%100;
                  return false;
                });
                if(!option)return JSON.stringify({ok:false,error:'The saved expiry does not match an available dropdown option.'});
                const setter=Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype,'value')?.set;
                if(setter)setter.call(e,option.value);else e.value=option.value;
                e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));
              } else {
                if(!['INPUT','TEXTAREA'].includes(e.tagName)||['hidden','submit','button','checkbox','radio','file','password'].includes((e.type||'').toLowerCase())) return JSON.stringify({ok:false,error:'That payment control is not a supported visible text field.'});
                const setter=Object.getOwnPropertyDescriptor(Object.getPrototypeOf(e),'value')?.set;
                if(setter)setter.call(e,$encodedValue);else e.value=$encodedValue;
                e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));
              }
              return JSON.stringify({ok:true,field:label.slice(0,100)||kind});
            })()""",
        )
    }

    suspend fun agentSelect(ref: String, choice: String): String {
        val encodedRef = org.json.JSONObject.quote(ref)
        val encodedChoice = org.json.JSONObject.quote(choice.trim().take(200))
        return evaluateForAgent(
            """(() => { const e=document.querySelector('[data-ello-agent-ref='+$encodedRef+']'); if(!e||e.tagName!=='SELECT') return JSON.stringify({ok:false,error:'That dropdown is no longer available. Take a fresh page snapshot.'}); const wanted=$encodedChoice.trim().toLowerCase(); const option=[...e.options].find(o=>!o.disabled&&((o.value||'').trim().toLowerCase()===wanted||(o.label||o.textContent||'').trim().toLowerCase()===wanted)); if(!option) return JSON.stringify({ok:false,error:'That choice is not available in this dropdown. Take a fresh snapshot of its options.'}); const setter=Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype,'value')?.set; if(setter) setter.call(e,option.value); else e.value=option.value; e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return JSON.stringify({ok:true,field:(e.labels?.[0]?.innerText||e.name||'dropdown').trim().slice(0,120),selected:(option.label||option.textContent||'').trim().slice(0,120)}); })()""",
        )
    }

    suspend fun agentScroll(direction: String): String {
        val y = if (direction == "up") -Math.round(0.75f * (view?.height ?: 800)) else Math.round(0.75f * (view?.height ?: 800))
        return evaluateForAgent("window.scrollBy({top:$y,behavior:'smooth'}); JSON.stringify({ok:true})")
    }

    suspend fun agentBack(): String = withContext(Dispatchers.Main) {
        val web = checkNotNull(view) { "Ello's browser is not open." }
        if (!web.canGoBack()) return@withContext "There is no previous page in this tab."
        val deferred = CompletableDeferred<Unit>()
        pageLoaded = deferred
        web.goBack()
        withTimeoutOrNull(12_000) { deferred.await() }
        "Went back to ${web.url.orEmpty()}"
    }

    private suspend fun evaluateForAgent(script: String): String = withContext(Dispatchers.Main) {
        val web = checkNotNull(view) { "Ello's browser is not open." }
        val raw = withTimeout(10_000) {
            val result = CompletableDeferred<String>()
            web.evaluateJavascript(script) { result.complete(it.orEmpty()) }
            result.await()
        }
        runCatching { org.json.JSONTokener(raw).nextValue() as? String }.getOrNull() ?: raw
    }

    fun goBack() { view?.let { web -> web.post { if (web.canGoBack()) web.goBack() } } }
    fun goForward() { view?.let { web -> web.post { if (web.canGoForward()) web.goForward() } } }
    fun reload() { view?.let { web -> web.post { pageLoaded = CompletableDeferred(); mutableBrowserState.value = mutableBrowserState.value.copy(loading = true, progress = 0, error = null); web.reload() } } }
    fun stop() { view?.let { web -> web.post { web.stopLoading(); pageLoaded?.complete(Unit); mutableBrowserState.value = mutableBrowserState.value.copy(loading = false, progress = 0) } } }
    fun showLoadError(message: String) { mutableBrowserState.value = mutableBrowserState.value.copy(loading = false, error = message) }

}

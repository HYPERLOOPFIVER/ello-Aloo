package com.ello.litertchat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Xml
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

internal data class FeedStory(
    val title: String,
    val summary: String,
    val publisher: String,
    val link: String,
    val publishedAt: Long,
    val topic: String,
)

internal data class FeedMarketQuote(
    val label: String,
    val symbol: String,
    val price: Double,
    val changePercent: Double,
    val currency: String,
)

private object FeedRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(18, TimeUnit.SECONDS)
        .build()

    suspend fun loadNews(topics: List<String>): List<FeedStory> = withContext(Dispatchers.IO) {
        topics.take(5).flatMap { topic ->
            runCatching { fetchTopic(topic) }.getOrDefault(emptyList())
        }.distinctBy { it.link }.sortedByDescending { it.publishedAt }.take(30)
    }

    private fun fetchTopic(topic: String): List<FeedStory> {
        val query = URLEncoder.encode(topic, "UTF-8")
        val url = "https://news.google.com/rss/search?q=$query&hl=en-US&gl=US&ceid=US:en"
        val request = Request.Builder().url(url).header("User-Agent", "Ello/1.0 (Android; personal news reader)").build()
        return client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "News service returned ${response.code}" }
            val parser = Xml.newPullParser().apply {
                setInput(response.body?.byteStream(), null)
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            }
            val stories = mutableListOf<FeedStory>()
            var event = parser.eventType
            var inItem = false
            var tag = ""
            var title = ""
            var description = ""
            var link = ""
            var pubDate = ""
            while (event != XmlPullParser.END_DOCUMENT && stories.size < 10) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        tag = parser.name.lowercase(Locale.ROOT)
                        if (tag == "item") {
                            inItem = true; title = ""; description = ""; link = ""; pubDate = ""
                        }
                    }
                    XmlPullParser.TEXT -> if (inItem) when (tag) {
                        "title" -> title += parser.text
                        "description" -> description += parser.text
                        "link" -> link += parser.text
                        "pubdate" -> pubDate += parser.text
                    }
                    XmlPullParser.END_TAG -> if (parser.name.equals("item", true)) {
                        val cleanTitle = title.xmlText().trim()
                        if (cleanTitle.isNotBlank() && link.startsWith("http")) {
                            val chunks = cleanTitle.split(" - ")
                            val publisher = chunks.lastOrNull().orEmpty().ifBlank { "Google News" }
                            val headline = if (chunks.size > 1) chunks.dropLast(1).joinToString(" - ") else cleanTitle
                            val time = runCatching {
                                SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).parse(pubDate.trim())?.time
                            }.getOrNull() ?: 0L
                            stories += FeedStory(headline, description.xmlText().take(500), publisher, link.trim(), time, topic)
                        }
                        inItem = false; tag = ""
                    }
                }
                event = parser.next()
            }
            stories
        }
    }

    suspend fun loadMarkets(): List<FeedMarketQuote> = withContext(Dispatchers.IO) {
        listOf("^NSEI" to "NIFTY 50", "^BSESN" to "SENSEX", "^GSPC" to "S&P 500").mapNotNull { (symbol, label) ->
            runCatching {
                val url = "https://query1.finance.yahoo.com/v8/finance/chart/${URLEncoder.encode(symbol, "UTF-8")}?range=5d&interval=1d"
                val response = client.newCall(Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 ElloNews/1.0").build()).execute().use { result ->
                    check(result.isSuccessful) { "Market data returned ${result.code}" }
                    JSONObject(result.body?.string().orEmpty())
                }
                val result = response.getJSONObject("chart").getJSONArray("result").getJSONObject(0)
                val meta = result.getJSONObject("meta")
                val closes = result.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0).getJSONArray("close")
                val values = (0 until closes.length()).mapNotNull { if (closes.isNull(it)) null else closes.optDouble(it).takeIf(Double::isFinite) }
                val current = meta.optDouble("regularMarketPrice", values.lastOrNull() ?: return@runCatching null)
                val previous = meta.optDouble("chartPreviousClose", values.dropLast(1).lastOrNull() ?: return@runCatching null)
                FeedMarketQuote(label, symbol, current, if (previous == 0.0) 0.0 else (current - previous) / previous * 100.0, meta.optString("currency", ""))
            }.getOrNull()
        }
    }

    private fun String.xmlText(): String = replace(Regex("<[^>]*>"), " ")
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
        .replace(Regex("\\s+"), " ")
}

internal fun feedInterests(context: Context): List<String> = context.getSharedPreferences("ello_feed", Context.MODE_PRIVATE)
    .getString("topics", "")!!.split("\u001f").map(String::trim).filter(String::isNotEmpty)

@Composable
internal fun FeedPage(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("ello_feed", Context.MODE_PRIVATE) }
    val selectedTopics = remember { mutableStateListOf<String>().apply { addAll(feedInterests(context)) } }
    val savedTopics = remember { mutableStateListOf<String>().apply { addAll(feedInterests(context)) } }
    var showTopics by rememberSaveable { mutableStateOf(selectedTopics.isEmpty()) }
    var news by remember { mutableStateOf<List<FeedStory>>(emptyList()) }
    var markets by remember { mutableStateOf<List<FeedMarketQuote>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var marketLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var marketError by remember { mutableStateOf<String?>(null) }
    var showMarkets by rememberSaveable { mutableStateOf(prefs.getBoolean("show_markets", false)) }
    val savedTopicKey = savedTopics.joinToString("\u001f")

    fun refreshNews() {
        if (selectedTopics.isEmpty()) { showTopics = true; return }
        scope.launch {
            loading = true; error = null
            runCatching { FeedRepository.loadNews(selectedTopics.toList()) }
                .onSuccess { news = it; if (it.isEmpty()) error = "No stories came back. Try refreshing in a moment." }
                .onFailure { error = it.localizedMessage ?: "Could not load stories." }
            loading = false
        }
    }
    fun refreshMarkets() {
        scope.launch {
            marketLoading = true; marketError = null
            runCatching { FeedRepository.loadMarkets() }
                .onSuccess { markets = it; if (it.isEmpty()) marketError = "Market quotes aren’t available right now." }
                .onFailure { marketError = "Market quotes aren’t available right now." }
            marketLoading = false
        }
    }
    LaunchedEffect(savedTopicKey, showTopics) {
        if (savedTopics.isNotEmpty() && !showTopics) {
            loading = true
            runCatching { FeedRepository.loadNews(savedTopics.toList()) }
                .onSuccess { news = it; if (it.isEmpty()) error = "No stories came back. Try refreshing in a moment." }
                .onFailure { error = it.localizedMessage ?: "Could not load stories." }
            loading = false
        }
    }
    LaunchedEffect(showMarkets) {
        prefs.edit().putBoolean("show_markets", showMarkets).apply()
        if (showMarkets && markets.isEmpty()) refreshMarkets()
    }

    Column(modifier.fillMaxSize().background(ElloPalette.Canvas)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("YOUR DAY, IN BRIEF", color = ElloPalette.Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp)
                Text("The Feed", color = ElloPalette.Text, fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
            }
            IconButton(onClick = { showTopics = true }) { Icon(Icons.Default.Edit, "Edit interests", tint = ElloPalette.Muted) }
            IconButton(onClick = { refreshNews(); if (showMarkets) refreshMarkets() }) { Icon(Icons.Default.Refresh, "Refresh feed", tint = ElloPalette.Muted) }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Surface(color = ElloPalette.Surface, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, ElloPalette.Border)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Box(Modifier.size(32.dp).clip(CircleShape).background(ElloPalette.Accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Article, null, tint = ElloPalette.Accent, modifier = Modifier.size(17.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Latest stories", color = ElloPalette.Text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                Text("Headlines from your selected topics", color = ElloPalette.Muted, fontSize = 11.sp)
                            }
                        }
                        news.take(3).forEach { story ->
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(story.title, color = ElloPalette.Text.copy(alpha = .94f), fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (story.summary.isNotBlank()) Text(story.summary, color = ElloPalette.Muted, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        androidx.compose.material3.Button(
                            onClick = {
                                refreshNews()
                            },
                            enabled = !loading,
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = ElloPalette.Accent.copy(alpha = .15f), contentColor = ElloPalette.Accent),
                            shape = RoundedCornerShape(15.dp),
                        ) {
                            if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = ElloPalette.Accent)
                            else Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Refresh stories", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            item {
                Surface(color = ElloPalette.Surface, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, ElloPalette.Border)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ShowChart, null, tint = ElloPalette.Accent, modifier = Modifier.size(19.dp))
                            Text("  Market watch", color = ElloPalette.Text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            androidx.compose.material3.Switch(checked = showMarkets, onCheckedChange = { showMarkets = it })
                        }
                        Text("Show index movements in your feed", color = ElloPalette.Muted, fontSize = 12.sp)
                        AnimatedVisibility(showMarkets) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (marketLoading) LinearLoadingRow("Getting latest available quotes…")
                                if (marketError != null) Text(marketError!!, color = ElloPalette.Muted, fontSize = 12.sp)
                                markets.forEach { quote ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(quote.label, color = ElloPalette.Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                        Text(quote.price.formatQuote(), color = ElloPalette.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                        val positive = quote.changePercent >= 0
                                        Text("  ${if (positive) "+" else ""}${"%.2f".format(Locale.US, quote.changePercent)}%", color = if (positive) Color(0xFF6BD6A4) else Color(0xFFFF7A83), fontSize = 12.sp)
                                    }
                                }
                                if (markets.isNotEmpty()) Text("Latest available quotes · not investment advice", color = ElloPalette.Muted, fontSize = 10.sp)
                                TextButton(onClick = { refreshMarkets() }) { Text("Refresh markets", color = ElloPalette.Accent) }
                            }
                        }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Picked for you", color = ElloPalette.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                        Text(selectedTopics.joinToString(" · ").ifBlank { "Choose your interests to personalize this feed" }, color = ElloPalette.Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = ElloPalette.Accent, strokeWidth = 2.dp)
                }
            }
            if (error != null) item { Text(error!!, color = ElloPalette.Muted, fontSize = 13.sp) }
            if (!loading && news.isEmpty() && error == null) item {
                Surface(onClick = { showTopics = true }, color = ElloPalette.Surface, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, ElloPalette.Border)) {
                    Text("Pick a few interests and Ello will gather stories for you.", color = ElloPalette.Muted, modifier = Modifier.padding(20.dp))
                }
            }
            items(news, key = { it.link }) { story -> StoryCard(story) }
        }
    }

    if (showTopics) {
        val available = listOf("Artificial intelligence", "Technology", "Business", "Startups", "Science", "Health", "Climate", "World news", "India", "Sports", "Gaming", "Personal finance", "Design", "Space")
        AlertDialog(
            onDismissRequest = { if (selectedTopics.isNotEmpty()) showTopics = false },
            containerColor = ElloPalette.Surface,
            title = { Text("What are you into?", color = ElloPalette.Text, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Pick a few topics. You can change them anytime.", color = ElloPalette.Muted, fontSize = 13.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        available.forEach { topic ->
                            val selected = topic in selectedTopics
                            FilterChip(
                                selected = selected,
                                onClick = { if (selected) selectedTopics.remove(topic) else if (selectedTopics.size < 5) selectedTopics.add(topic) },
                                label = { Text(topic, fontSize = 12.sp) },
                            )
                        }
                    }
                    if (selectedTopics.size >= 5) Text("Up to 5 topics", color = ElloPalette.Muted, fontSize = 11.sp)
                }
            },
            confirmButton = { TextButton(onClick = {
                if (selectedTopics.isNotEmpty()) {
                    prefs.edit().putString("topics", selectedTopics.joinToString("\u001f")).apply()
                    savedTopics.clear(); savedTopics.addAll(selectedTopics)
                    showTopics = false
                    news = emptyList()
                }
            }, enabled = selectedTopics.isNotEmpty()) { Text("Save interests", color = ElloPalette.Accent) } },
            dismissButton = { if (selectedTopics.isNotEmpty()) TextButton(onClick = { showTopics = false }) { Text("Cancel", color = ElloPalette.Muted) } },
        )
    }
}

@Composable
private fun LinearLoadingRow(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = ElloPalette.Accent)
        Text(label, color = ElloPalette.Muted, fontSize = 11.sp)
    }
}

@Composable
private fun StoryCard(story: FeedStory) {
    val context = LocalContext.current
    Surface(
        onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(story.link))) } },
        color = ElloPalette.Surface,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, ElloPalette.Border),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(story.topic.uppercase(Locale.ROOT), color = ElloPalette.Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                if (story.publishedAt > 0L) Text(relativeTimeText(story.publishedAt), color = ElloPalette.Muted, fontSize = 10.sp)
            }
            Text(story.title, color = ElloPalette.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (story.summary.isNotBlank()) Text(story.summary, color = ElloPalette.Muted, fontSize = 12.sp, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(story.publisher, color = ElloPalette.Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Default.OpenInNew, "Open story", tint = ElloPalette.Muted, modifier = Modifier.size(15.dp))
            }
        }
    }
}

private fun relativeTimeText(publishedAt: Long): String {
    val delta = (System.currentTimeMillis() - publishedAt).coerceAtLeast(0) / 60_000
    return when {
        delta < 1 -> "just now"
        delta < 60 -> "${delta}m ago"
        delta < 24 * 60 -> "${delta / 60}h ago"
        else -> "${delta / (24 * 60)}d ago"
    }
}

private fun Double.formatQuote(): String = if (this >= 1000) String.format(Locale.US, "%,.2f", this) else String.format(Locale.US, "%.2f", this)

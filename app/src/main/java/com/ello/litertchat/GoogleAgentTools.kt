package com.ello.litertchat

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Google Workspace REST actions use OAuth tokens held by GoogleApiAccess on this device. */
internal class GoogleAgentTools(
    private val context: Context,
    private val approvals: kotlinx.coroutines.channels.Channel<McpApprovalRequest>,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    fun execute(name: String, args: JSONObject): Map<String, String> = try {
        when (name) {
            "google_gmail_search" -> gmailSearch(args)
            "google_gmail_read" -> gmailRead(args)
            "google_gmail_send" -> gmailSend(args)
            "google_gmail_send_to_contact" -> gmailSendToContact(args)
            "google_gmail_reply" -> gmailReply(args)
            "google_calendar_list" -> calendarList(args)
            "google_calendar_create" -> calendarCreate(args)
            "google_calendar_update" -> calendarUpdate(args)
            "google_calendar_delete" -> calendarDelete(args)
            "google_tasks_list_lists" -> tasksListLists(args)
            "google_tasks_list" -> tasksList(args)
            "google_tasks_create" -> tasksCreate(args)
            "google_tasks_update" -> tasksUpdate(args)
            "google_tasks_delete" -> tasksDelete(args)
            else -> mapOf("error" to "Unsupported Google service operation.", "status" to "failed")
        }
    } catch (error: Throwable) {
        mapOf("error" to (error.localizedMessage ?: "The Google service operation failed."), "status" to "failed")
    }

    private fun gmailSearch(args: JSONObject): Map<String, String> {
        val query = args.optString("query").trim().take(500)
        require(query.isNotBlank()) { "Tell Ello what emails to search for." }
        val token = GoogleApiAccess.requireGmailToken(context)
        val url = "https://gmail.googleapis.com/gmail/v1/users/me/messages".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("maxResults", args.optInt("max_results", 8).coerceIn(1, 15).toString())
            .build()
        val list = requestJson(Request.Builder().url(url).header("Authorization", "Bearer $token").get().build())
        val messages = list.optJSONArray("messages") ?: JSONArray()
        val summaries = JSONArray()
        for (index in 0 until minOf(messages.length(), 12)) {
            val id = messages.optJSONObject(index)?.optString("id").orEmpty()
            if (id.isBlank()) continue
            val itemUrl = "https://gmail.googleapis.com/gmail/v1/users/me/messages".toHttpUrl().newBuilder()
                .addPathSegment(id)
                .addQueryParameter("format", "metadata")
                .addQueryParameter("metadataHeaders", "From")
                .addQueryParameter("metadataHeaders", "To")
                .addQueryParameter("metadataHeaders", "Subject")
                .addQueryParameter("metadataHeaders", "Date")
                .build()
            val message = requestJson(Request.Builder().url(itemUrl).header("Authorization", "Bearer $token").get().build())
            summaries.put(JSONObject()
                .put("id", id)
                .put("thread_id", message.optString("threadId"))
                .put("snippet", message.optString("snippet").take(500))
                .put("headers", selectedHeaders(message)))
        }
        return mapOf("messages" to summaries.toString(), "count" to summaries.length().toString(), "status" to "succeeded")
    }

    private fun gmailRead(args: JSONObject): Map<String, String> {
        val id = args.optString("message_id").trim()
        require(id.matches(Regex("[A-Za-z0-9_-]{1,200}"))) { "Use a Gmail message ID returned by search." }
        val token = GoogleApiAccess.requireGmailToken(context)
        val url = "https://gmail.googleapis.com/gmail/v1/users/me/messages".toHttpUrl().newBuilder()
            .addPathSegment(id).addQueryParameter("format", "full").build()
        val message = requestJson(Request.Builder().url(url).header("Authorization", "Bearer $token").get().build())
        val headers = selectedHeaders(message)
        val payload = message.optJSONObject("payload") ?: JSONObject()
        val body = extractPlainText(payload).take(MAX_EMAIL_BODY_CHARS)
        return mapOf("message" to JSONObject()
            .put("id", id)
            .put("thread_id", message.optString("threadId"))
            .put("headers", headers)
            .put("snippet", message.optString("snippet").take(500))
            .put("body_text", body)
            .toString(), "status" to "succeeded")
    }

    private fun gmailSend(args: JSONObject): Map<String, String> {
        val to = args.optString("to").trim()
        val subject = args.optString("subject").trim()
        val body = args.optString("body").trim()
        require(to.matches(EMAIL)) { "Provide one valid recipient email address." }
        require(subject.isNotBlank() && subject.length <= 300) { "Provide an email subject up to 300 characters." }
        require(body.isNotBlank() && body.length <= MAX_EMAIL_BODY_CHARS) { "Provide an email message up to $MAX_EMAIL_BODY_CHARS characters." }
        require(!subject.contains('\r') && !subject.contains('\n')) { "The email subject cannot contain line breaks." }
        val preview = "To: $to\nSubject: $subject\n\n${body.take(700)}${if (body.length > 700) "…" else ""}"
        val allowed = runBlocking(Dispatchers.IO) { askMcpApproval(approvals, "Send this Gmail message", preview) }
        if (!allowed) return mapOf("result" to "The user declined to send the email.", "status" to "denied")
        val token = GoogleApiAccess.requireGmailToken(context)
        val encodedSubject = Base64.encodeToString(subject.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val rawEmail = "To: $to\r\nSubject: =?UTF-8?B?$encodedSubject?=\r\nMIME-Version: 1.0\r\nContent-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: 8bit\r\n\r\n$body"
        val raw = Base64.encodeToString(rawEmail.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        val payload = JSONObject().put("raw", raw).toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder().url("https://gmail.googleapis.com/gmail/v1/users/me/messages/send")
            .header("Authorization", "Bearer $token").post(payload).build()
        val result = requestJson(request)
        return mapOf("result" to "Gmail accepted the message for $to.", "message_id" to result.optString("id"), "status" to "succeeded")
    }

    private fun gmailSendToContact(args: JSONObject): Map<String, String> {
        val query = args.optString("contact_name").trim().replace(Regex("^my\\s+", RegexOption.IGNORE_CASE), "").trim()
        require(query.isNotBlank()) { "Provide the saved contact's name or relationship." }
        val matches = ProfilePeopleStore.load(context).filter { person ->
            person.name.equals(query, ignoreCase = true) || person.relationship.equals(query, ignoreCase = true)
        }
        require(matches.size == 1) {
            if (matches.isEmpty()) "No saved contact matches '$query'. Add them in Profile first."
            else "More than one saved contact matches '$query'. Use the person's exact saved name."
        }
        val recipient = matches.single().email.trim()
        require(recipient.isNotBlank()) { "The saved contact does not have an email address. Update the contact in Profile first." }
        val resolved = JSONObject(args.toString()).put("to", recipient)
        return gmailSend(resolved)
    }

    private fun gmailReply(args: JSONObject): Map<String, String> {
        val id = args.optString("message_id").trim()
        require(id.matches(Regex("[A-Za-z0-9_-]{1,200}"))) { "Use a Gmail message ID returned by search." }
        val body = args.optString("body").trim()
        require(body.isNotBlank() && body.length <= MAX_EMAIL_BODY_CHARS) {
            "Provide a reply up to $MAX_EMAIL_BODY_CHARS characters."
        }

        val token = GoogleApiAccess.requireGmailToken(context)
        val originalUrl = "https://gmail.googleapis.com/gmail/v1/users/me/messages".toHttpUrl().newBuilder()
            .addPathSegment(id)
            .addQueryParameter("format", "metadata")
            .addQueryParameter("metadataHeaders", "From")
            .addQueryParameter("metadataHeaders", "Reply-To")
            .addQueryParameter("metadataHeaders", "Subject")
            .addQueryParameter("metadataHeaders", "Message-ID")
            .addQueryParameter("metadataHeaders", "References")
            .build()
        val original = requestJson(Request.Builder().url(originalUrl).header("Authorization", "Bearer $token").get().build())
        val headers = original.optJSONObject("payload")?.optJSONArray("headers") ?: JSONArray()
        fun header(name: String): String = (0 until headers.length()).mapNotNull { headers.optJSONObject(it) }
            .firstOrNull { it.optString("name").equals(name, true) }?.optString("value").orEmpty()

        val replyTarget = header("Reply-To").ifBlank { header("From") }
        val addresses = Regex("[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?(?:\\.[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?)+", RegexOption.IGNORE_CASE)
            .findAll(replyTarget).map { it.value }.distinctBy { it.lowercase() }.toList()
        require(addresses.size == 1 && addresses.single().matches(EMAIL)) {
            "Ello could not safely identify one reply recipient from that email."
        }
        val to = addresses.single()
        val threadId = original.optString("threadId").trim()
        require(threadId.matches(Regex("[A-Za-z0-9_-]{1,200}"))) { "Gmail did not return a valid thread ID for that message." }
        val originalMessageId = header("Message-ID").trim()
        require(originalMessageId.matches(Regex("<[^<>\\r\\n]{1,500}>"))) { "Gmail did not return a valid Message-ID for that email." }
        val subject = header("Subject").replace(Regex("[\\r\\n]+"), " ").trim().take(300)
        require(subject.isNotBlank()) { "Gmail did not return a subject for that email." }
        val replySubject = if (subject.startsWith("Re:", ignoreCase = true)) subject else "Re: $subject"
        val existingReferences = Regex("<[^<>\\s]{1,500}>").findAll(header("References").replace(Regex("[\\r\\n]"), " "))
            .map { it.value }.toList().takeLast(20)
        val references = (existingReferences + originalMessageId).distinct().joinToString(" ")

        val preview = "To: $to\nSubject: $replySubject\n\n${body.take(700)}${if (body.length > 700) "…" else ""}"
        val allowed = runBlocking(Dispatchers.IO) { askMcpApproval(approvals, "Send this Gmail reply", preview) }
        if (!allowed) return mapOf("result" to "The user declined to send the email reply.", "status" to "denied")

        val encodedSubject = Base64.encodeToString(replySubject.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val rawEmail = "To: $to\r\nSubject: =?UTF-8?B?$encodedSubject?=\r\nIn-Reply-To: $originalMessageId\r\nReferences: $references\r\nMIME-Version: 1.0\r\nContent-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: 8bit\r\n\r\n$body"
        val raw = Base64.encodeToString(rawEmail.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        val payload = JSONObject().put("raw", raw).put("threadId", threadId).toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder().url("https://gmail.googleapis.com/gmail/v1/users/me/messages/send")
            .header("Authorization", "Bearer $token").post(payload).build()
        val result = requestJson(request)
        return mapOf("result" to "Gmail accepted the reply to $to.", "message_id" to result.optString("id"), "status" to "succeeded")
    }

    private fun calendarList(args: JSONObject): Map<String, String> {
        val from = args.optString("time_min").trim()
        val to = args.optString("time_max").trim()
        require(from.isNotBlank() && to.isNotBlank()) { "Provide the requested time window as RFC 3339 date-times." }
        val token = GoogleApiAccess.requireCalendarToken(context)
        val url = "https://www.googleapis.com/calendar/v3/calendars/primary/events".toHttpUrl().newBuilder()
            .addQueryParameter("timeMin", from)
            .addQueryParameter("timeMax", to)
            .addQueryParameter("singleEvents", "true")
            .addQueryParameter("orderBy", "startTime")
            .addQueryParameter("maxResults", args.optInt("max_results", 20).coerceIn(1, 50).toString())
            .build()
        val result = requestJson(Request.Builder().url(url).header("Authorization", "Bearer $token").get().build())
        val events = result.optJSONArray("items") ?: JSONArray()
        val summaries = JSONArray()
        for (i in 0 until events.length()) {
            val event = events.optJSONObject(i) ?: continue
            summaries.put(JSONObject()
                .put("id", event.optString("id"))
                .put("summary", event.optString("summary"))
                .put("description", event.optString("description").take(1000))
                .put("location", event.optString("location"))
                .put("start", event.optJSONObject("start") ?: JSONObject())
                .put("end", event.optJSONObject("end") ?: JSONObject())
                .put("htmlLink", event.optString("htmlLink")))
        }
        return mapOf("events" to summaries.toString(), "count" to summaries.length().toString(), "status" to "succeeded")
    }

    private fun calendarCreate(args: JSONObject): Map<String, String> {
        val title = args.optString("title").trim()
        val start = args.optString("start").trim()
        val end = args.optString("end").trim()
        val timezone = args.optString("time_zone", java.util.TimeZone.getDefault().id).trim()
        val attendeeText = args.optString("attendees").trim()
        require(title.isNotBlank() && title.length <= 300) { "Provide an event title up to 300 characters." }
        val parsedStart = runCatching { java.time.OffsetDateTime.parse(start) }.getOrNull()
            ?: error("Use an RFC 3339 start date-time with a timezone offset, such as 2026-05-10T14:00:00+05:30.")
        val parsedEnd = runCatching { java.time.OffsetDateTime.parse(end) }.getOrNull()
            ?: error("Use an RFC 3339 end date-time with a timezone offset, such as 2026-05-10T15:00:00+05:30.")
        require(parsedEnd.isAfter(parsedStart)) { "The event end must be later than its start." }
        require(runCatching { java.time.ZoneId.of(timezone) }.isSuccess) { "Provide a valid IANA time zone, such as Asia/Kolkata." }
        val attendees = attendeeText.split(',').map(String::trim).filter(String::isNotBlank)
        require(attendees.all { it.matches(EMAIL) }) { "Every attendee must be a valid email address." }
        val details = "Create calendar event: $title\n$start – $end ($timezone)${if (attendees.isEmpty()) "" else "\nInvite: ${attendees.joinToString()}"}"
        val allowed = runBlocking(Dispatchers.IO) { askMcpApproval(approvals, "Create this Google Calendar event", details) }
        if (!allowed) return mapOf("result" to "The user declined to create the calendar event.", "status" to "denied")
        val token = GoogleApiAccess.requireCalendarToken(context)
        val event = JSONObject()
            .put("summary", title)
            .put("description", args.optString("description").take(4000))
            .put("location", args.optString("location").take(500))
            .put("start", JSONObject().put("dateTime", start).put("timeZone", timezone))
            .put("end", JSONObject().put("dateTime", end).put("timeZone", timezone))
        if (attendees.isNotEmpty()) event.put("attendees", JSONArray().apply { attendees.forEach { put(JSONObject().put("email", it)) } })
        val sendUpdates = if (attendees.isEmpty()) "none" else "all"
        val url = "https://www.googleapis.com/calendar/v3/calendars/primary/events".toHttpUrl().newBuilder()
            .addQueryParameter("sendUpdates", sendUpdates).build()
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token")
            .post(event.toString().toRequestBody(JSON_MEDIA_TYPE)).build()
        val result = requestJson(request)
        return mapOf("result" to "Google Calendar created the event.", "event_id" to result.optString("id"), "event_url" to result.optString("htmlLink"), "status" to "succeeded")
    }

    private fun calendarUpdate(args: JSONObject): Map<String, String> {
        val eventId = args.optString("event_id").trim()
        require(eventId.isNotBlank() && eventId.length <= 512) { "Use an event ID returned by Google Calendar." }
        val patch = JSONObject()
        args.optString("title").trim().takeIf(String::isNotBlank)?.let {
            require(it.length <= 300) { "Event title is too long." }
            patch.put("summary", it)
        }
        args.optString("description").takeIf { args.has("description") }?.let { patch.put("description", it.take(4000)) }
        args.optString("location").takeIf { args.has("location") }?.let { patch.put("location", it.take(500)) }
        val start = args.optString("start").trim()
        val end = args.optString("end").trim()
        require(start.isBlank() == end.isBlank()) { "To reschedule an event, provide both its new start and end." }
        if (start.isNotBlank()) {
            val parsedStart = runCatching { java.time.OffsetDateTime.parse(start) }.getOrNull()
                ?: error("Use an RFC 3339 start date-time with a timezone offset.")
            val parsedEnd = runCatching { java.time.OffsetDateTime.parse(end) }.getOrNull()
                ?: error("Use an RFC 3339 end date-time with a timezone offset.")
            require(parsedEnd.isAfter(parsedStart)) { "The event end must be later than its start." }
            val timezone = args.optString("time_zone", java.util.TimeZone.getDefault().id).trim()
            require(runCatching { java.time.ZoneId.of(timezone) }.isSuccess) { "Provide a valid IANA time zone." }
            patch.put("start", JSONObject().put("dateTime", start).put("timeZone", timezone))
            patch.put("end", JSONObject().put("dateTime", end).put("timeZone", timezone))
        }
        require(patch.length() > 0) { "Provide at least one event field to update." }
        val token = GoogleApiAccess.requireCalendarToken(context)
        val baseUrl = "https://www.googleapis.com/calendar/v3/calendars/primary/events".toHttpUrl().newBuilder()
            .addPathSegment(eventId).build()
        val existing = requestJson(Request.Builder().url(baseUrl).header("Authorization", "Bearer $token").get().build())
        val changes = buildList {
            if (patch.has("summary")) add("Title → ${patch.optString("summary")}")
            if (patch.has("start")) add("Time → $start – $end")
            if (patch.has("location")) add("Location → ${patch.optString("location")}")
            if (patch.has("description")) add("Description updated")
        }.joinToString("\n")
        val details = "Event: ${existing.optString("summary")}\nCurrent time: ${existing.optJSONObject("start")?.optString("dateTime") ?: existing.optJSONObject("start")?.optString("date").orEmpty()}\n$changes\nCalendar guests may be notified of this change."
        val allowed = runBlocking(Dispatchers.IO) { askMcpApproval(approvals, "Review Google Calendar change", details) }
        if (!allowed) return mapOf("result" to "The user declined to update the calendar event.", "status" to "denied")
        val url = baseUrl.newBuilder().addQueryParameter("sendUpdates", "all").build()
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token")
            .patch(patch.toString().toRequestBody(JSON_MEDIA_TYPE)).build()
        val updated = requestJson(request)
        return mapOf("result" to "Google Calendar updated the event.", "event_id" to updated.optString("id"), "event_url" to updated.optString("htmlLink"), "status" to "succeeded")
    }

    private fun calendarDelete(args: JSONObject): Map<String, String> {
        val eventId = args.optString("event_id").trim()
        require(eventId.isNotBlank() && eventId.length <= 512) { "Use an event ID returned by Google Calendar." }
        val token = GoogleApiAccess.requireCalendarToken(context)
        val eventUrl = "https://www.googleapis.com/calendar/v3/calendars/primary/events".toHttpUrl().newBuilder()
            .addPathSegment(eventId).build()
        val existing = requestJson(Request.Builder().url(eventUrl).header("Authorization", "Bearer $token").get().build())
        val start = existing.optJSONObject("start")?.optString("dateTime")
            ?: existing.optJSONObject("start")?.optString("date").orEmpty()
        val details = "Delete calendar event: ${existing.optString("summary")}\nWhen: $start\nGuests will be notified that this event was cancelled."
        val allowed = runBlocking(Dispatchers.IO) { askMcpApproval(approvals, "Review calendar cancellation", details) }
        if (!allowed) return mapOf("result" to "The user declined to delete the calendar event.", "status" to "denied")
        val deleteUrl = eventUrl.newBuilder().addQueryParameter("sendUpdates", "all").build()
        val request = Request.Builder().url(deleteUrl).header("Authorization", "Bearer $token").delete().build()
        requestNoContent(request)
        return mapOf("result" to "Google Calendar deleted the event and notified its guests.", "event_id" to eventId, "status" to "succeeded")
    }

    private fun tasksList(args: JSONObject): Map<String, String> {
        val token = GoogleApiAccess.requireTasksToken(context)
        val listId = args.optString("task_list_id", "@default").trim()
        require(listId.isNotBlank() && listId.length <= 200) { "Provide a valid task list ID." }
        val url = "https://tasks.googleapis.com/tasks/v1/lists".toHttpUrl().newBuilder()
            .addPathSegment(listId).addPathSegment("tasks")
            .addQueryParameter("showCompleted", args.optBoolean("show_completed", false).toString())
            .addQueryParameter("maxResults", args.optInt("max_results", 20).coerceIn(1, 50).toString())
            .build()
        val result = requestJson(Request.Builder().url(url).header("Authorization", "Bearer $token").get().build())
        val items = result.optJSONArray("items") ?: JSONArray()
        val tasks = JSONArray()
        for (i in 0 until items.length()) {
            val task = items.optJSONObject(i) ?: continue
            tasks.put(JSONObject()
                .put("id", task.optString("id"))
                .put("title", task.optString("title"))
                .put("notes", task.optString("notes").take(1000))
                .put("due", task.optString("due"))
                .put("status", task.optString("status"))
                .put("completed", task.optString("completed"))
                .put("assigned", task.optJSONObject("assignmentInfo") != null))
        }
        return mapOf("tasks" to tasks.toString(), "count" to tasks.length().toString(), "status" to "succeeded")
    }

    private fun tasksListLists(args: JSONObject): Map<String, String> {
        val token = GoogleApiAccess.requireTasksToken(context)
        val url = "https://tasks.googleapis.com/tasks/v1/users/@me/lists".toHttpUrl().newBuilder()
            .addQueryParameter("maxResults", args.optInt("max_results", 50).coerceIn(1, 100).toString())
            .build()
        val result = requestJson(Request.Builder().url(url).header("Authorization", "Bearer $token").get().build())
        val items = result.optJSONArray("items") ?: JSONArray()
        val lists = JSONArray()
        for (i in 0 until items.length()) {
            val list = items.optJSONObject(i) ?: continue
            lists.put(JSONObject().put("id", list.optString("id")).put("title", list.optString("title")))
        }
        return mapOf("task_lists" to lists.toString(), "count" to lists.length().toString(), "status" to "succeeded")
    }

    private fun tasksCreate(args: JSONObject): Map<String, String> {
        val listId = args.optString("task_list_id", "@default").trim()
        val title = args.optString("title").trim()
        require(listId.isNotBlank() && listId.length <= 200) { "Provide a valid task list ID." }
        require(title.isNotBlank() && title.length <= 1024) { "Provide a task title up to 1024 characters." }
        val dueDate = args.optString("due").trim()
        val due = dueDate.takeIf(String::isNotBlank)?.let(::googleTaskDueTimestamp).orEmpty()
        val details = "Create Google Task: $title${if (dueDate.isBlank()) "" else "\nDue date: $dueDate"}${if (args.optString("notes").isBlank()) "" else "\nNotes: ${args.optString("notes").take(500)}"}"
        val allowed = runBlocking(Dispatchers.IO) { askMcpApproval(approvals, "Create this Google Task", details) }
        if (!allowed) return mapOf("result" to "The user declined to create the task.", "status" to "denied")
        val token = GoogleApiAccess.requireTasksToken(context)
        val url = "https://tasks.googleapis.com/tasks/v1/lists".toHttpUrl().newBuilder()
            .addPathSegment(listId).addPathSegment("tasks").build()
        val task = JSONObject().put("title", title).put("notes", args.optString("notes").take(4000))
        if (due.isNotBlank()) task.put("due", due)
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token")
            .post(task.toString().toRequestBody(JSON_MEDIA_TYPE)).build()
        val result = requestJson(request)
        return mapOf("result" to "Google Tasks created the task.", "task_id" to result.optString("id"), "title" to result.optString("title"), "status" to "succeeded")
    }

    private fun tasksUpdate(args: JSONObject): Map<String, String> {
        val listId = args.optString("task_list_id", "@default").trim()
        val taskId = args.optString("task_id").trim()
        require(listId.isNotBlank() && listId.length <= 200) { "Provide a valid task list ID." }
        require(taskId.isNotBlank() && taskId.length <= 200) { "Use a task ID returned by Google Tasks." }
        val patch = JSONObject()
        args.optString("title").trim().takeIf(String::isNotBlank)?.let {
            require(it.length <= 1024) { "Task title is too long." }
            patch.put("title", it)
        }
        args.optString("notes").takeIf { args.has("notes") }?.let { patch.put("notes", it.take(4000)) }
        args.optString("due").trim().takeIf(String::isNotBlank)?.let {
            patch.put("due", googleTaskDueTimestamp(it))
        }
        val status = args.optString("status").trim()
        if (status.isNotBlank()) {
            require(status in setOf("completed", "needsAction")) { "Task status must be completed or needsAction." }
            patch.put("status", status)
        }
        require(patch.length() > 0) { "Provide a task field or status to update." }
        val token = GoogleApiAccess.requireTasksToken(context)
        val taskUrl = tasksTaskUrl(listId, taskId)
        val existing = requestJson(Request.Builder().url(taskUrl).header("Authorization", "Bearer $token").get().build())
        val changes = buildList {
            if (patch.has("title")) add("Title → ${patch.optString("title")}")
            if (patch.has("due")) add("Due date → ${args.optString("due")}")
            if (patch.has("status")) add("Status → ${patch.optString("status")}")
            if (patch.has("notes")) add("Notes updated")
        }.joinToString("\n")
        val details = "Task: ${existing.optString("title")}\n$changes"
        val allowed = runBlocking(Dispatchers.IO) { askMcpApproval(approvals, "Review Google Task change", details) }
        if (!allowed) return mapOf("result" to "The user declined to update the task.", "status" to "denied")
        val request = Request.Builder().url(taskUrl).header("Authorization", "Bearer $token")
            .patch(patch.toString().toRequestBody(JSON_MEDIA_TYPE)).build()
        val updated = requestJson(request)
        return mapOf("result" to "Google Tasks updated the task.", "task_id" to updated.optString("id"), "title" to updated.optString("title"), "task_status" to updated.optString("status"), "status" to "succeeded")
    }

    private fun tasksDelete(args: JSONObject): Map<String, String> {
        val listId = args.optString("task_list_id", "@default").trim()
        val taskId = args.optString("task_id").trim()
        require(listId.isNotBlank() && listId.length <= 200) { "Provide a valid task list ID." }
        require(taskId.isNotBlank() && taskId.length <= 200) { "Use a task ID returned by Google Tasks." }
        val token = GoogleApiAccess.requireTasksToken(context)
        val taskUrl = tasksTaskUrl(listId, taskId)
        val existing = requestJson(Request.Builder().url(taskUrl).header("Authorization", "Bearer $token").get().build())
        require(existing.optJSONObject("assignmentInfo") == null) {
            "This task is assigned from Google Docs or Chat. Deleting it would also delete the original task, so Ello won't delete it here."
        }
        val details = "Delete Google Task: ${existing.optString("title")}\nDue: ${existing.optString("due").ifBlank { "No due date" }}"
        val allowed = runBlocking(Dispatchers.IO) { askMcpApproval(approvals, "Review task deletion", details) }
        if (!allowed) return mapOf("result" to "The user declined to delete the task.", "status" to "denied")
        val request = Request.Builder().url(taskUrl).header("Authorization", "Bearer $token").delete().build()
        requestNoContent(request)
        return mapOf("result" to "Google Tasks deleted the task.", "task_id" to taskId, "status" to "succeeded")
    }

    private fun tasksTaskUrl(listId: String, taskId: String) =
        "https://tasks.googleapis.com/tasks/v1/lists".toHttpUrl().newBuilder()
            .addPathSegment(listId).addPathSegment("tasks").addPathSegment(taskId).build()

    private fun googleTaskDueTimestamp(value: String): String {
        val date = runCatching { java.time.LocalDate.parse(value) }.getOrNull()
            ?: error("Google Tasks supports a due date but not a time. Use YYYY-MM-DD; use schedule_notification for a time reminder.")
        return "${date}T00:00:00.000Z"
    }

    private fun requestNoContent(request: Request) {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val details = runCatching { JSONObject(text).optJSONObject("error")?.optString("message") }.getOrNull().orEmpty()
                error("Google API returned HTTP ${response.code}${if (details.isBlank()) "." else ": ${details.take(300)}"}")
            }
        }
    }

    private fun requestJson(request: Request): JSONObject = client.newCall(request).execute().use { response ->
        val text = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            val details = runCatching { JSONObject(text).optJSONObject("error")?.optString("message") }.getOrNull().orEmpty()
            error("Google API returned HTTP ${response.code}${if (details.isBlank()) "." else ": ${details.take(300)}"}")
        }
        runCatching { JSONObject(text) }.getOrElse { error("Google API returned an invalid JSON response.") }
    }

    private fun selectedHeaders(message: JSONObject): JSONObject {
        val wanted = setOf("from", "to", "subject", "date")
        val result = JSONObject()
        val headers = message.optJSONObject("payload")?.optJSONArray("headers") ?: JSONArray()
        for (i in 0 until headers.length()) {
            val item = headers.optJSONObject(i) ?: continue
            val name = item.optString("name").lowercase()
            if (name in wanted) result.put(name, item.optString("value").take(500))
        }
        return result
    }

    private fun extractPlainText(part: JSONObject): String {
        if (part.optString("mimeType").equals("text/plain", true)) {
            val encoded = part.optJSONObject("body")?.optString("data").orEmpty()
            if (encoded.isNotBlank()) return runCatching {
                String(Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
            }.getOrDefault("")
        }
        val children = part.optJSONArray("parts") ?: return ""
        return (0 until children.length()).joinToString("\n") { index -> extractPlainText(children.optJSONObject(index) ?: JSONObject()) }
    }

    private companion object {
        val EMAIL = Regex("^[^\\s<>@,;]+@[^\\s<>@,;]+\\.[^\\s<>@,;]+$")
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val MAX_EMAIL_BODY_CHARS = 12_000
    }
}

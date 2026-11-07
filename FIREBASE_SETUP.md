# Firebase setup

This app uses Firebase Authentication for email OTP sign-in and Firestore for user profile sync.

## Firebase project

1. In Firebase project `ello-744e4`, enable Email/Password authentication and create a Cloud Firestore database.
2. Deploy the owner-only Firestore rules from the repository root:

   ```powershell
   firebase deploy --only firestore:rules --project ello-744e4
   ```

3. Configure the email OTP sender secrets in Firebase Functions Secret Manager:

   ```powershell
   firebase functions:secrets:set GMAIL_APP_PASSWORD --project ello-744e4
   firebase functions:secrets:set OTP_HASH_PEPPER --project ello-744e4
   firebase functions:secrets:set OTP_SENDER_EMAIL --project ello-744e4
   firebase functions:secrets:set GEMINI_API_KEY --project ello-744e4
   ```

   Use a fresh Gmail app password and a random pepper of at least 32 bytes. Keep both server-side; neither belongs in the app or source files.

4. Deploy the Functions API:

   ```powershell
   firebase deploy --only functions --project ello-744e4
   ```

The API serves `/api/requestEmailCode`, `/api/verifyEmailCode`, and `/api/agentTurn` in `asia-south1`. `GEMINI_API_KEY` must be a Google AI Studio Gemini API key and stays in Firebase Secret Manager; never put it in `secrets.properties`, Android resources, or the APK. The Android agent sends chat context to this endpoint, which authenticates Firebase users, rate-limits requests, and calls Gemini. Chat history is kept on the device; the function does not persist prompts or responses. Configure `emailOtpApiBaseUrl` or `agentApiBaseUrl` in the ignored root `secrets.properties` file only when using a non-default endpoint.

The OTP service rate-limits requests, stores only hashed codes in Firestore, expires codes after ten minutes, caps verification attempts, and consumes each code once. Firestore rules deny client access to OTP challenge and rate-limit collections.

## Android configuration

The cloud agent acts through Ello's registered on-device tools, imported skills, and user-approved MCP tools. A task you start runs in an Android foreground service with an ongoing notification, so it can continue while the app is minimized. Tool-call context is checkpointed before and after external actions; if Android kills the service, Ello restores the task and checks the current state before deciding whether to continue. The current planner limit is 24 tool actions per request. Connect Gmail, Google Calendar, and Google Tasks in Settings → Google services before asking Ello to use them. Read requests run against the connected account from the phone; Gmail send, event creation/invites, task creation, and consequential browser actions require an in-app approval. Google access tokens stay encrypted on the device and are not sent to Firebase. SMS/calendar drafts opened in another Android app still require review there. The agent does not provide arbitrary app control or phone calling.

The Google tools currently support Gmail search/read/send/reply, primary-calendar event list/create/update/delete, Google Tasks list discovery and task list/create/update/delete. Gmail reply recipients are resolved locally from the original message; Ello shows the recipient and full reply for approval before sending. A saved Gmail contact can be addressed by name; Ello resolves the address locally and shows it in the send approval. The API Anything catalog can run supported public JSON, HTML CSS-selector, embedded-JSON, and plain-text reads over a direct request, with a same-origin on-device browser fallback for selected reads; bookings still run through the visible browser and its approval flow. Profile memories can be explicitly saved, read by relevant category, or removed; they remain in on-device Profile storage and are sent to Gemini only when the agent requests a relevant category. Ello requires explicit approval before sending or changing/removing external data. The Gemini API key stays in Firebase Secret Manager; user Google OAuth tokens stay on the device.

## Website actions and API Anything

Ello's in-app browser is driven on the phone by Gemini function calls. It can search, navigate, inspect visible page controls, wait for dynamic page text, fill ordinary text fields, choose native dropdown options, and click. The user can also ask Ello to fill a matching name, email, phone, or home/work address from the on-device profile; that value is inserted locally and is not sent to Gemini. Ello blocks password, one-time-code, payment-card, and government-ID fields, and asks before consequential clicks. The website remains responsible for availability and final confirmation.

Ello bundles the API Anything v0.3.2 site catalog and runs supported signed-out, read-only JSON, HTML CSS-selector, embedded-JSON, and plain-text reads on the phone. It tries direct HTTPS first, then can retry a selected read through a temporary WebView fetch restricted to the exact same origin. That fallback can use the origin's WebView cookies; the runner only resolves explicitly declared CSRF-style cookie references, never session-token references or logged-in operations. The result reports tier 1 (direct) or tier 2 (browser fetch). In this catalog snapshot, 310 operations across 114 sites pass Ello's static support filter; this is an eligibility count, not a live availability check. The user-facing agent can inspect argument links between operations, follow safe same-site GET lookups, and continue catalog pagination where provided. The catalog source commit and MIT license are packaged under `app/src/main/assets/api_anything/`.

This is a native Android implementation of that read-only slice, not the full Node package. The upstream MCP entry point uses stdio and requires Node.js 22.13+; Ello's phone-side MCP client accepts Streamable HTTP over HTTPS. The package keeps browser sessions on the machine running it, so it should not be moved into the shared Firebase function. Its bundled catalog does not ship booking writes; Ello uses its visible browser for form and booking actions. Before a consequential submission, the on-device approval dialog shows the target, non-sensitive entered fields, and page context; sensitive fields are masked. Ello then inspects the site's response for confirmation.

Add only the public Firebase app configuration required by your setup. Do not place service-account JSON, email credentials, API keys, or Firebase Admin credentials in the Android project.

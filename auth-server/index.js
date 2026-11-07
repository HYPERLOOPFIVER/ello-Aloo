const { onRequest } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const { health, requestEmailCode, verifyEmailCode, agentTurn } = require("./core");
const gmailAppPassword = defineSecret("GMAIL_APP_PASSWORD");
const otpHashPepper = defineSecret("OTP_HASH_PEPPER");
const otpSenderEmail = defineSecret("OTP_SENDER_EMAIL");
const geminiApiKey = defineSecret("GEMINI_API_KEY");
const handlers = {
  "/health": health,
  "/requestEmailCode": requestEmailCode,
  "/verifyEmailCode": verifyEmailCode,
  "/api/requestEmailCode": requestEmailCode,
  "/api/verifyEmailCode": verifyEmailCode,
  "/agentTurn": agentTurn,
  "/api/agentTurn": agentTurn,
};

exports.api = onRequest(
  {
    region: "asia-south1",
    timeoutSeconds: 60,
    memory: "512MiB",
    maxInstances: 20,
    invoker: "public",
    secrets: [gmailAppPassword, otpHashPepper, otpSenderEmail, geminiApiKey],
  },
  async (req, res) => {
    const pathname = new URL(req.originalUrl || req.url || "/", "https://ello.invalid").pathname;
    const route = handlers[pathname] || handlers[pathname.replace(/^\/api\//, "/")];
    if (!route) return res.status(404).json({ error: "Not found." });
    try {
      await route(req, res);
    } catch (error) {
      console.error("Ello API request failed", error.message);
      if (!res.headersSent) res.status(500).json({ error: "The Ello API could not complete this request." });
    }
  },
);


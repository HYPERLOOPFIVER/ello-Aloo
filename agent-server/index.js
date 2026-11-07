const { onRequest } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const { agentTurn } = require("./core");

const geminiApiKey = defineSecret("GEMINI_API_KEY");

exports.agentTurn = onRequest(
  {
    region: "asia-south1",
    timeoutSeconds: 60,
    memory: "512MiB",
    maxInstances: 20,
    invoker: "public",
    secrets: [geminiApiKey],
  },
  agentTurn,
);

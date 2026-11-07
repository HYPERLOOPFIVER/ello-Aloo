const { onRequest } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const { initializeApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore, FieldValue, Timestamp } = require("firebase-admin/firestore");
const nodemailer = require("nodemailer");
const crypto = require("node:crypto");

initializeApp();

const gmailAppPassword = defineSecret("GMAIL_APP_PASSWORD");
const otpPepper = defineSecret("OTP_HASH_PEPPER");
const senderAddress = defineSecret("OTP_SENDER_EMAIL");

const db = getFirestore();
const OTP_TTL_MS = 10 * 60 * 1000;
const RESEND_WAIT_MS = 60 * 1000;
const MAX_ATTEMPTS = 5;
const GENERIC_SEND_MESSAGE = "If that address can receive a code, we sent one.";

function normalizeEmail(value) {
  const email = String(value || "").trim().toLowerCase();
  if (email.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    const error = new Error("Enter a valid email address.");
    error.status = 400;
    throw error;
  }
  return email;
}

function emailKey(email) {
  return crypto.createHash("sha256").update(email).digest("hex");
}

function codeDigest(email, code, pepper) {
  return crypto.createHmac("sha256", pepper).update(`${email}:${code}`).digest("hex");
}

function safeEqualHex(left, right) {
  const a = Buffer.from(left, "hex");
  const b = Buffer.from(right, "hex");
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}

function json(res, status, body) {
  res.set("Cache-Control", "no-store");
  res.set("X-Content-Type-Options", "nosniff");
  res.status(status).json(body);
}

async function sendWithGmail(to, code) {
  const from = senderAddress.value();
  const subject = "Your Ello sign-in code";
  const body = `Your Ello sign-in code is ${code}. It expires in 10 minutes. If you did not request this code, you can ignore this email.`;
  const transporter = nodemailer.createTransport({
    service: "gmail",
    auth: { user: from, pass: gmailAppPassword.value() },
  });
  await transporter.sendMail({ from: `Ello <${from}>`, to, subject, text: body });
}

exports.requestEmailCode = onRequest(
  { region: "asia-south1", secrets: [gmailAppPassword, otpPepper, senderAddress] },
  async (req, res) => {
    if (req.method !== "POST") return json(res, 405, { error: "Use POST." });
    try {
      const email = normalizeEmail(req.body?.email);
      const key = emailKey(email);
      const ref = db.collection("emailOtpChallenges").doc(key);
      const requesterIp = String(req.ip || req.socket?.remoteAddress || "unknown");
      const ipRef = db.collection("emailOtpRateLimits").doc(
        crypto.createHmac("sha256", otpPepper.value()).update(requesterIp).digest("hex"),
      );
      const now = Date.now();
      const code = crypto.randomInt(0, 1_000_000).toString().padStart(6, "0");
      const sendResult = await db.runTransaction(async (tx) => {
        const [snap, ipSnap] = await Promise.all([tx.get(ref), tx.get(ipRef)]);
        const previous = snap.exists ? snap.data() : {};
        const previousIp = ipSnap.exists ? ipSnap.data() : {};
        const hour = Math.floor(now / 3_600_000);
        if (previousIp.hour === hour && (previousIp.count || 0) >= 10) return "throttled";
        if (previous.lastSentAt?.toMillis?.() > now - RESEND_WAIT_MS) return "throttled";
        const dayKey = new Date(now).toISOString().slice(0, 10);
        const dailyCount = previous.dayKey === dayKey ? (previous.dailyCount || 0) : 0;
        if (dailyCount >= 5) return "throttled";
        tx.set(ref, {
          email,
          codeHash: codeDigest(email, code, otpPepper.value()),
          expiresAt: Timestamp.fromMillis(now + OTP_TTL_MS),
          lastSentAt: Timestamp.fromMillis(now),
          dayKey,
          dailyCount: dailyCount + 1,
          attempts: 0,
          updatedAt: FieldValue.serverTimestamp(),
        });
        tx.set(ipRef, {
          hour,
          count: previousIp.hour === hour ? (previousIp.count || 0) + 1 : 1,
          updatedAt: FieldValue.serverTimestamp(),
        });
        return "send";
      });
      if (sendResult === "send") {
        try {
          await sendWithGmail(email, code);
        } catch (error) {
          await ref.delete();
          console.error("Gmail OTP delivery failed", error.message);
          return json(res, 502, { error: "Email delivery failed. Try again shortly." });
        }
      }
      return json(res, 200, { message: GENERIC_SEND_MESSAGE, retryAfterSeconds: RESEND_WAIT_MS / 1000 });
    } catch (error) {
      return json(res, error.status || 500, { error: error.status ? error.message : "Could not send a sign-in code." });
    }
  },
);

exports.verifyEmailCode = onRequest(
  { region: "asia-south1", secrets: [otpPepper] },
  async (req, res) => {
    if (req.method !== "POST") return json(res, 405, { error: "Use POST." });
    try {
      const email = normalizeEmail(req.body?.email);
      const code = String(req.body?.code || "").trim();
      if (!/^\d{6}$/.test(code)) return json(res, 400, { error: "Enter the 6-digit code." });
      const ref = db.collection("emailOtpChallenges").doc(emailKey(email));
      const verification = await db.runTransaction(async (tx) => {
        const snap = await tx.get(ref);
        if (!snap.exists) return { error: "That code is invalid or expired." };
        const challenge = snap.data();
        if (challenge.expiresAt?.toMillis?.() <= Date.now()) {
          tx.delete(ref);
          return { error: "That code is invalid or expired." };
        }
        if ((challenge.attempts || 0) >= MAX_ATTEMPTS) {
          tx.delete(ref);
          return { error: "Too many attempts. Request a new code." };
        }
        if (!safeEqualHex(challenge.codeHash, codeDigest(email, code, otpPepper.value()))) {
          tx.update(ref, { attempts: FieldValue.increment(1) });
          return { error: "That code is incorrect." };
        }
        tx.delete(ref);
        return { ok: true };
      });
      if (!verification.ok) return json(res, 400, { error: verification.error });

      const auth = getAuth();
      const deterministicUid = `email_${emailKey(email).slice(0, 56)}`;
      let user;
      try {
        user = await auth.getUser(deterministicUid);
      } catch (error) {
        if (error.code !== "auth/user-not-found") throw error;
        try {
          user = await auth.getUserByEmail(email);
        } catch (emailError) {
          if (emailError.code !== "auth/user-not-found") throw emailError;
          user = await auth.createUser({ uid: deterministicUid, email, emailVerified: true, disabled: false });
        }
      }
      await auth.updateUser(user.uid, { email, emailVerified: true, disabled: false });
      const customToken = await auth.createCustomToken(user.uid);
      return json(res, 200, { customToken, email });
    } catch (error) {
      return json(res, error.status || 500, { error: error.status ? error.message : "Could not verify that code." });
    }
  },
);


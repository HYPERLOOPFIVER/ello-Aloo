const http = require("node:http");
const { health, requestEmailCode, verifyEmailCode } = require("./core");

const routes = {
  "/health": health,
  "/api/requestEmailCode": requestEmailCode,
  "/api/verifyEmailCode": verifyEmailCode,
};

function send(res, status, body) {
  res.writeHead(status, { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store", "X-Content-Type-Options": "nosniff" });
  res.end(JSON.stringify(body));
}

const server = http.createServer((req, res) => {
  const pathname = new URL(req.url, "http://localhost").pathname;
  const handler = routes[pathname];
  if (!handler) return send(res, 404, { error: "Not found." });
  const chunks = [];
  let size = 0;
  let tooLarge = false;
  const maxSize = 16 * 1024;
  req.on("data", (chunk) => {
    if (tooLarge) return;
    size += chunk.length;
    if (size > maxSize) {
      tooLarge = true;
      send(res, 413, { error: "Request too large." });
      return;
    }
    chunks.push(chunk);
  });
  req.on("end", async () => {
    if (tooLarge) return;
    try {
      const raw = Buffer.concat(chunks).toString("utf8");
      req.body = raw ? JSON.parse(raw) : {};
      req.headers = req.headers || {};
      res.status = (status) => { res.statusCode = status; return res; };
      res.json = (body) => { res.setHeader("Content-Type", "application/json; charset=utf-8"); res.end(JSON.stringify(body)); };
      await handler(req, res);
    } catch {
      if (!res.headersSent) send(res, 400, { error: "Invalid JSON request." });
    }
  });
});

const port = Number(process.env.PORT || 8787);
server.listen(port, "0.0.0.0", () => console.log(`Ello auth API listening on ${port}`));

export function sendJson(res, status, payload, headers = {}) {
  const body = JSON.stringify(payload);
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "content-length": Buffer.byteLength(body),
    "cache-control": "no-store",
    ...headers,
  });
  res.end(body);
}

export function sendError(res, status, message, type = "invalid_request_error", extra = {}) {
  sendJson(res, status, { error: { message, type, ...extra } });
}

export async function readBody(req, limit) {
  const chunks = [];
  let size = 0;
  for await (const chunk of req) {
    size += chunk.length;
    if (limit && size > limit) {
      const err = new Error("payload too large");
      err.status = 413;
      throw err;
    }
    chunks.push(chunk);
  }
  if (!chunks.length) return null;
  return Buffer.concat(chunks);
}

export async function readJsonBody(req, limit) {
  const buf = await readBody(req, limit);
  if (!buf) return {};
  try {
    return JSON.parse(buf.toString("utf8"));
  } catch {
    const err = new Error("invalid json");
    err.status = 400;
    throw err;
  }
}

export class SseStream {
  constructor(res) {
    this.res = res;
    this.closed = false;
    res.writeHead(200, {
      "content-type": "text/event-stream; charset=utf-8",
      "cache-control": "no-cache, no-transform",
      connection: "keep-alive",
      "x-accel-buffering": "no",
    });
    this.ping = setInterval(() => {
      if (!this.closed) this.res.write(": ping\n\n");
    }, 10_000);
    res.on("close", () => this.close());
  }

  send(payload) {
    if (this.closed) return;
    this.res.write(`data: ${JSON.stringify(payload)}\n\n`);
  }

  comment(text) {
    if (!this.closed) this.res.write(`: ${text}\n\n`);
  }

  done() {
    if (this.closed) return;
    this.res.write("data: [DONE]\n\n");
    this.close();
  }

  close() {
    if (this.closed) return;
    this.closed = true;
    clearInterval(this.ping);
    try {
      this.res.end();
    } catch {

    }
  }
}

export async function* sseEvents(response) {
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true }).replaceAll("\r\n", "\n");
      let index;
      while ((index = buffer.indexOf("\n\n")) !== -1) {
        const block = buffer.slice(0, index);
        buffer = buffer.slice(index + 2);
        const data = block
          .split("\n")
          .filter((line) => line.startsWith("data:"))
          .map((line) => line.slice(5).replace(/^ /, ""))
          .join("\n");
        if (data) yield data;
      }
    }
  } finally {
    try {
      await reader.cancel();
    } catch {

    }
  }
}

export function bearer(req) {
  const header = String(req.headers.authorization || "");
  const match = header.match(/^Bearer\s+(.+)$/i);
  if (match) return match[1].trim();
  const apiKey = req.headers["x-api-key"];
  return typeof apiKey === "string" ? apiKey.trim() : "";
}

export function clientIp(req) {
  const forwarded = String(req.headers["x-forwarded-for"] || "")
    .split(",")[0]
    .trim();
  return forwarded || req.socket?.remoteAddress || "";
}

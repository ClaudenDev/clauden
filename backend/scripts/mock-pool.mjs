import http from "node:http";

const port = Number(process.env.MOCK_POOL_PORT || 18799);

http
  .createServer(async (req, res) => {
    if (req.url === "/health") {
      res.writeHead(200, { "content-type": "application/json" });
      return res.end(JSON.stringify({ ok: true }));
    }
    if (!req.url.endsWith("/chat/completions")) {
      res.writeHead(404).end();
      return;
    }
    const chunks = [];
    for await (const chunk of req) chunks.push(chunk);
    const body = JSON.parse(Buffer.concat(chunks).toString("utf8") || "{}");
    const last = body.messages?.at(-1)?.content || "";
    const answer = `echo: ${typeof last === "string" ? last : "[parts]"}`;

    if (!body.stream) {
      res.writeHead(200, { "content-type": "application/json" });
      res.end(
        JSON.stringify({
          choices: [{ index: 0, message: { role: "assistant", content: answer }, finish_reason: "stop" }],
          usage: { total_tokens: 12 },
        }),
      );
      return;
    }

    res.writeHead(200, { "content-type": "text/event-stream" });
    for (const piece of answer.match(/.{1,4}/gs) || []) {
      res.write(
        `data: ${JSON.stringify({
          choices: [{ index: 0, delta: { content: piece } }],
        })}\n\n`,
      );
    }
    res.write(`data: ${JSON.stringify({ choices: [{ index: 0, delta: {}, finish_reason: "stop" }] })}\n\n`);
    res.write("data: [DONE]\n\n");
    res.end();
  })
  .listen(port, "127.0.0.1", () => {
    console.log(`mock pool on http://127.0.0.1:${port}`);
  });

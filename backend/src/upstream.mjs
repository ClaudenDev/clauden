import { sseEvents } from "./http.mjs";

export class UpstreamError extends Error {
  constructor(message, status = 502, type = "upstream_error") {
    super(message);
    this.name = "UpstreamError";
    this.status = status;
    this.type = type;
  }
}

export class InferencePool {
  constructor({ config, log }) {
    this.config = config;
    this.log = log;
  }

  get enabled() {
    return Boolean(this.config.upstream.base);
  }

  headers(account) {
    const headers = { "content-type": "application/json", accept: "text/event-stream" };
    if (this.config.upstream.key) {
      headers.authorization = `Bearer ${this.config.upstream.key}`;
    }
    if (account) headers["x-clauden-account"] = String(account);
    return headers;
  }

  buildRequest(body, { model, stream, system }) {
    const messages = [];
    if (system) messages.push({ role: "system", content: system });
    for (const message of body.messages || []) {
      messages.push({
        role: message.role === "assistant" ? "assistant" : message.role === "system" ? "system" : "user",
        content: normalizeContent(message.content),
      });
    }
    const payload = {
      model: model || this.config.upstream.model,
      messages,
      stream: Boolean(stream),
    };
    if (body.temperature !== undefined) payload.temperature = body.temperature;
    if (body.top_p !== undefined) payload.top_p = body.top_p;
    const maxTokens = Number(body.max_tokens || body.max_completion_tokens || 0);
    payload.max_tokens = maxTokens > 0
      ? Math.min(maxTokens, this.config.upstream.maxTokens)
      : this.config.upstream.maxTokens;
    return payload;
  }

  async complete(body, options = {}) {
    if (!this.enabled) throw new UpstreamError("Inference pool is not configured", 503);
    const payload = this.buildRequest(body, { ...options, stream: false });
    const res = await this.#fetch(payload, options.signal, options.account);
    const data = await res.json().catch(() => null);
    const text = data?.choices?.[0]?.message?.content;
    if (typeof text !== "string" || !text.trim()) {
      throw new UpstreamError("Inference pool returned an empty answer", 502, "empty_answer");
    }
    return { text, usage: data?.usage || null };
  }

  async *stream(body, options = {}) {
    if (!this.enabled) throw new UpstreamError("Inference pool is not configured", 503);
    const payload = this.buildRequest(body, { ...options, stream: true });
    const res = await this.#fetch(payload, options.signal, options.account);
    let finished = false;
    for await (const raw of sseEvents(res)) {
      if (raw === "[DONE]") break;
      let event;
      try {
        event = JSON.parse(raw);
      } catch {
        continue;
      }
      if (event.error) {
        throw new UpstreamError(event.error.message || "Inference pool error", 502);
      }
      const choice = event.choices?.[0];
      if (!choice) continue;
      const delta = choice.delta || {};
      const reasoning = delta.reasoning_content ?? delta.reasoning ?? "";
      const content = typeof delta.content === "string" ? delta.content : "";
      if (content || reasoning) {
        yield { delta: content, reasoning: String(reasoning || "") };
      }
      if (choice.finish_reason) {
        finished = true;
        yield { delta: "", reasoning: "", finish: choice.finish_reason };
      }
    }
    if (!finished) yield { delta: "", reasoning: "", finish: "stop" };
  }

  async #fetch(payload, signal, account) {
    const controller = new AbortController();
    const timer = setTimeout(
      () => controller.abort(new Error("upstream timeout")),
      this.config.upstream.timeoutMs,
    );
    const onAbort = () => controller.abort(signal?.reason);
    if (signal) {
      if (signal.aborted) controller.abort(signal.reason);
      else signal.addEventListener("abort", onAbort, { once: true });
    }
    try {
      const res = await fetch(`${this.config.upstream.base}/chat/completions`, {
        method: "POST",
        headers: this.headers(account),
        body: JSON.stringify(payload),
        signal: controller.signal,
      });
      if (!res.ok) {
        const text = await res.text().catch(() => "");
        this.log(`pool: ${res.status} ${text.slice(0, 400)}`);
        throw new UpstreamError(
          res.status === 429
            ? "Слишком много запросов, попробуйте через минуту"
            : "Сервис временно недоступен",
          res.status === 429 ? 429 : 502,
        );
      }
      return res;
    } catch (err) {
      if (err instanceof UpstreamError) throw err;
      if (err?.name === "AbortError" || signal?.aborted) {
        throw new UpstreamError("Запрос отменён", 499, "cancelled");
      }
      throw new UpstreamError("Нет связи с сервисом", 502, "network");
    } finally {
      clearTimeout(timer);
    }
  }
}

function normalizeContent(content) {
  if (typeof content === "string") return content;
  if (Array.isArray(content)) {
    return content
      .map((part) => {
        if (typeof part === "string") return part;
        if (part?.type === "text") return part.text || "";
        if (part?.type === "image_url" || part?.image_url) {

          return part;
        }
        return "";
      })
      .filter(Boolean);
  }
  return content == null ? "" : String(content);
}

import { sseEvents } from "./http.mjs";
import { PoolExhausted } from "./keypool.mjs";

export class AnthropicPoolClient {
  constructor({ config, pool, log }) {
    this.config = config;
    this.pool = pool;
    this.log = log;
  }

  get enabled() {
    return Boolean(this.config.pool.secret) && this.pool.enabled;
  }

  buildBody(body, { model, system, maxTokens }) {
    const messages = [];
    for (const message of body.messages || []) {
      const role = message.role === "assistant" ? "assistant" : "user";
      if (role === "system") continue;
      messages.push({ role, content: toAnthropicContent(message.content) });
    }
    if (!messages.length) messages.push({ role: "user", content: "" });
    const payload = {
      model: model || this.config.pool.model,
      max_tokens: Math.min(
        Number(maxTokens || body.max_tokens || 0) || this.config.pool.maxTokens,
        this.config.pool.maxTokens,
      ),
      messages,
      stream: true,
    };
    const sys = system || body.system;
    if (sys) payload.system = stringSystem(sys);
    if (body.temperature !== undefined) payload.temperature = body.temperature;
    if (body.top_p !== undefined) payload.top_p = body.top_p;
    return payload;
  }

  async *stream(body, options = {}) {
    const attempts = this.pool.entries.length + 1;
    let lastError = null;
    for (let attempt = 0; attempt < attempts; attempt += 1) {
      let entry;
      try {
        entry = this.pool.pick();
      } catch (err) {
        if (lastError) throw lastError;
        throw err;
      }
      try {
        const res = await this.#request(this.buildBody(body, options), entry, options.signal);
        if (!res.ok) {
          const text = await res.text().catch(() => "");
          const kind = this.pool.reportFailure(entry, { status: res.status, message: text });
          lastError = new Error(
            res.status === 429
              ? "Лимит ключа исчерпан, переключаемся"
              : "Ключ отклонён, переключаемся",
          );
          lastError.status = res.status;
          yield {
            delta: "",
            reasoning: "",
            failover: { key: entry.id, reason: kind, status: res.status },
          };
          continue;
        }
        this.pool.reportSuccess(entry);
        yield* this.#events(res);
        return;
      } catch (err) {
        if (err?.name === "AbortError" || options.signal?.aborted) throw err;
        this.pool.reportFailure(entry, { status: 0, message: err.message });
        lastError = err;
        yield { delta: "", reasoning: "", failover: { key: entry.id, reason: "network" } };
      }
    }
    throw lastError || new PoolExhausted();
  }

  async complete(body, options = {}) {
    let text = "";
    for await (const chunk of this.stream(body, options)) {
      if (chunk.delta) text += chunk.delta;
    }
    return { text };
  }

  async #request(payload, entry, signal) {
    const controller = new AbortController();
    const timer = setTimeout(
      () => controller.abort(new Error("pool timeout")),
      this.config.pool.timeoutMs,
    );
    const onAbort = () => controller.abort(signal?.reason);
    if (signal) {
      if (signal.aborted) controller.abort(signal.reason);
      else signal.addEventListener("abort", onAbort, { once: true });
    }
    try {
      return await fetch(`${this.config.pool.base}/v1/messages`, {
        method: "POST",
        headers: {
          "content-type": "application/json",
          accept: "text/event-stream",
          "anthropic-version": "2023-06-01",
          "x-api-key": entry.key,
        },
        body: JSON.stringify(payload),
        signal: controller.signal,
      });
    } catch (err) {
      if (err?.name === "AbortError") throw err;
      throw new Error(`anthropic: ${err.message}`);
    } finally {
      clearTimeout(timer);
    }
  }

  async *#events(res) {
    for await (const raw of sseEvents(res)) {
      let event;
      try {
        event = JSON.parse(raw);
      } catch {
        continue;
      }
      if (event.type === "content_block_delta") {
        const delta = event.delta || {};
        if (delta.type === "text_delta" && delta.text) {
          yield { delta: delta.text, reasoning: "" };
        } else if (delta.type === "thinking_delta" && delta.thinking) {
          yield { delta: "", reasoning: delta.thinking };
        }
      } else if (event.type === "message_delta") {
        const stop = event.delta?.stop_reason;
        if (stop) yield { delta: "", reasoning: "", finish: stop };
      } else if (event.type === "error") {
        throw new Error(event.error?.message || "anthropic stream error");
      }
    }
  }
}

function toAnthropicContent(content) {
  if (typeof content === "string") return content;
  if (!Array.isArray(content)) return String(content ?? "");
  const parts = [];
  for (const part of content) {
    if (typeof part === "string") {
      parts.push({ type: "text", text: part });
      continue;
    }
    if (part?.type === "text") parts.push({ type: "text", text: part.text || "" });
    else if (part?.type === "image_url" || part?.image_url) {
      const url = typeof part.image_url === "string" ? part.image_url : part.image_url?.url;
      const parsed = parseDataUrl(url);
      if (parsed) {
        parts.push({
          type: "image",
          source: { type: "base64", media_type: parsed.mime, data: parsed.data },
        });
      }
    }
  }
  return parts.length ? parts : "";
}

function parseDataUrl(url) {
  const match = String(url || "").match(/^data:([^;]+);base64,(.+)$/);
  if (!match) return null;
  return { mime: match[1], data: match[2] };
}

function stringSystem(value) {
  if (!value) return "";
  if (typeof value === "string") return value;
  if (Array.isArray(value)) {
    return value
      .map((part) => (typeof part === "string" ? part : part?.text || ""))
      .filter(Boolean)
      .join("\n");
  }
  return String(value);
}

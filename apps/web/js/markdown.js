const marked = window.marked;
const DOMPurify = window.DOMPurify;
const hljs = window.hljs;

marked.setOptions({
  gfm: true,
  breaks: false,
  headerIds: false,
  mangle: false,
});

const renderer = new marked.Renderer();
const originalCode = renderer.code.bind(renderer);
const originalLink = renderer.link.bind(renderer);

renderer.code = function ({ text, lang, escaped }) {
  const language = String(lang || "").trim().split(/\s+/)[0].toLowerCase();
  let highlighted = "";
  try {
    highlighted = language && hljs.getLanguage(language)
      ? hljs.highlight(text, { language, ignoreIllegals: true }).value
      : hljs.highlightAuto(text).value;
  } catch {
    highlighted = escapeHtml(text);
  }
  const canOpen = ["html", "svg", "markdown", "md"].includes(language);
  const id = `code-${Math.random().toString(36).slice(2, 9)}`;
  return `<div class="code-block" data-code-id="${id}" data-lang="${escapeHtml(language)}">
    <div class="code-head">
      <span class="code-lang">${escapeHtml(language || "text")}</span>
      ${canOpen ? `<button class="btn btn-ghost btn-sm" data-artifact-open="${id}">Открыть</button>` : ""}

      <button class="icon-btn sm" data-copy-code="${id}" title="Скопировать">${copyIcon()}</button>

    </div>

    <pre><code class="hljs" data-raw="${encodeURIComponent(text)}">${highlighted}</code></pre>
  </div>`;

};

renderer.link = function (token) {
  const html = originalLink(token);
  return html.replace("<a ", '<a target="_blank" rel="noopener noreferrer" ');
};

function copyIcon() {
  return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="12" height="12" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>';
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

const ARTIFACT_RE = /<(?:ant)?artifact\s+identifier="([^"]*)"\s+type="([^"]*)"\s+title="([^"]*)">\s*([\s\S]*?)\s*<\/(?:ant)?artifact>/g;

function artifactLang(type, name) {
  const value = String(type || "").toLowerCase();
  const title = String(name || "").toLowerCase();
  if (value.includes("html") || title.endsWith(".html")) return "html";
  if (value.includes("svg") || title.endsWith(".svg")) return "svg";
  if (value.includes("markdown") || title.endsWith(".md")) return "markdown";
  if (value.includes("python") || title.endsWith(".py")) return "python";
  if (value.includes("javascript") || title.endsWith(".js") || title.endsWith(".ts")) return "javascript";
  if (value.includes("css") || title.endsWith(".css")) return "css";
  if (value.includes("json") || title.endsWith(".json")) return "json";
  return "text";
}

function decodeEntities(value) {
  return String(value || "")
    .replaceAll("&quot;", '"')
    .replaceAll("&lt;", "<")
    .replaceAll("&gt;", ">")
    .replaceAll("&amp;", "&");
}

function expandArtifacts(text) {
  return String(text || "").replace(ARTIFACT_RE, (_match, id, type, title, content) => {
    const lang = artifactLang(type, title);
    const safeTitle = escapeHtml(decodeEntities(title) || "Артефакт");
    const safeId = escapeHtml(id);
    return `\n\n<div class="artifact-inline" data-artifact-lang="${lang}" data-artifact-title="${safeTitle}"><div class="artifact-inline-head"><span class="artifact-inline-title">${safeTitle}</span><button class="btn btn-ghost btn-sm" data-inline-artifact="${safeId}">Открыть</button></div><pre><code>${escapeHtml(content)}</code></pre></div>\n\n`;
  });
}

const MATH_GREEK = {
  alpha: "α", beta: "β", gamma: "γ", delta: "δ", epsilon: "ε", varepsilon: "ε",
  zeta: "ζ", eta: "η", theta: "θ", vartheta: "ϑ", iota: "ι", kappa: "κ",
  lambda: "λ", mu: "μ", nu: "ν", xi: "ξ", pi: "π", rho: "ρ", sigma: "σ",
  tau: "τ", upsilon: "υ", phi: "φ", varphi: "φ", chi: "χ", psi: "ψ", omega: "ω",
  Gamma: "Γ", Delta: "Δ", Theta: "Θ", Lambda: "Λ", Xi: "Ξ", Pi: "Π",
  Sigma: "Σ", Phi: "Φ", Psi: "Ψ", Omega: "Ω",
};

const MATH_SYMBOLS = {
  cdot: "·", times: "×", div: "÷", pm: "±", mp: "∓", leq: "≤", le: "≤",
  geq: "≥", ge: "≥", neq: "≠", ne: "≠", approx: "≈", equiv: "≡", sim: "∼",
  propto: "∝", infty: "∞", partial: "∂", nabla: "∇", sum: "∑", prod: "∏",
  int: "∫", iint: "∬", oint: "∮", sqrt: "√", in: "∈", notin: "∉",
  subset: "⊂", subseteq: "⊆", supset: "⊃", supseteq: "⊇", cup: "∪", cap: "∩",
  forall: "∀", exists: "∃", neg: "¬", land: "∧", lor: "∨", rightarrow: "→",
  to: "→", leftarrow: "←", gets: "←", leftrightarrow: "↔", Rightarrow: "⇒",
  Leftarrow: "⇐", Leftrightarrow: "⇔", mapsto: "↦", uparrow: "↑", downarrow: "↓",
  degree: "°", circ: "∘", angle: "∠", perp: "⊥", parallel: "∥", ldots: "…",
  dots: "…", cdots: "⋯", quad: "  ", qquad: "    ",
};

const SUPER = { 0: "⁰", 1: "¹", 2: "²", 3: "³", 4: "⁴", 5: "⁵", 6: "⁶", 7: "⁷", 8: "⁸", 9: "⁹", "+": "⁺", "-": "⁻", n: "ⁿ", i: "ⁱ" };
const SUB = { 0: "₀", 1: "₁", 2: "₂", 3: "₃", 4: "₄", 5: "₅", 6: "₆", 7: "₇", 8: "₈", 9: "₉", "+": "₊", "-": "₋", "=": "₌", a: "ₐ", e: "ₑ", i: "ᵢ", j: "ⱼ", n: "ₙ", x: "ₓ" };

function unlatex(value) {
  let text = String(value || "");
  text = text.replace(/\\(?:left|right|displaystyle|textstyle|limits)\b/g, "");
  text = text.replace(/\\(?:,|;|!|:| )/g, " ");
  text = text.replace(/\\(?:mathrm|mathbf|mathit|mathsf|mathtt|text|textrm|textit)\s*\{([^{}]*)\}/g, "$1");
  for (let i = 0; i < 3; i += 1) {
    text = text.replace(/\\frac\s*\{([^{}]*)\}\s*\{([^{}]*)\}/g, "($1)/($2)");
  }
  text = text.replace(/\\sqrt\s*\{([^{}]*)\}/g, "√($1)");
  text = text.replace(/\\(?:overline|bar)\s*\{([^{}]*)\}/g, "$1̄");
  text = text.replace(/\\vec\s*\{([^{}]*)\}/g, "$1⃗");
  text = text.replace(/\\(?:begin|end)\s*\{[^{}]*\}/g, "");
  text = text.replace(/\\[a-zA-Z]+/g, (match) => {
    const name = match.slice(1);
    return MATH_GREEK[name] || MATH_SYMBOLS[name] || match;
  });
  text = text.replace(/\^\{?([0-9+\-ni])\}?/g, (_, ch) => SUPER[ch] || `^${ch}`);
  text = text.replace(/_\{?([0-9+\-=aeijnx])\}?/g, (_, ch) => SUB[ch] || `_${ch}`);
  return text.replace(/&/g, " ").replace(/\\\\/g, "\n").replace(/\{\}/g, "");
}

function normalizeLatex(source) {
  const text = String(source || "");
  if (!text.includes("$") && !text.includes("\\(") && !text.includes("\\[")) return text;
  return text.replace(/\$\$([\s\S]+?)\$\$|\\\[([\s\S]+?)\\\]|\\\(([\s\S]+?)\\\)|\$([^$\n]+?)\$/g, (_, a, b, c, d) => {
    const body = a || b || c || d || "";
    return unlatex(body).trim();
  });
}

export function renderMarkdown(text) {
  if (!text) return "";
  const raw = marked.parse(expandArtifacts(normalizeLatex(text)), { renderer });
  return DOMPurify.sanitize(raw, {
    ADD_ATTR: ["target", "rel", "data-code-id", "data-lang", "data-copy-code", "data-artifact-open", "data-raw", "data-inline-artifact", "data-artifact-lang", "data-artifact-title"],
    ALLOW_DATA_ATTR: true,
  });
}

export function highlightIn(container) {
  container.querySelectorAll("pre code:not(.hljs)").forEach((node) => {
    try { hljs.highlightElement(node); } catch {  }
  });
}

export function enhanceMarkdown(container) {
  container.querySelectorAll("[data-inline-artifact]").forEach((button) => {
    if (button.dataset.wired) return;
    button.dataset.wired = "1";
    button.addEventListener("click", (e) => {
      e.stopPropagation();
      const card = button.closest(".artifact-inline");
      const code = card?.querySelector("code")?.textContent || "";
      if (!code) return;
      const lang = card?.dataset.artifactLang || "text";
      const title = card?.dataset.artifactTitle || `artifact.${lang === "markdown" ? "md" : lang}`;
      window.dispatchEvent(new CustomEvent("clauden:artifact", { detail: { code, lang, name: title } }));
    });
  });
  container.querySelectorAll("[data-artifact-open]").forEach((button) => {
    if (button.dataset.wired) return;
    button.dataset.wired = "1";
    button.addEventListener("click", (e) => {
      e.stopPropagation();
      const id = button.dataset.artifactOpen;
      const block = container.querySelector(`[data-code-id="${id}"]`);
      if (!block) return;
      const lang = block.dataset.lang || "text";
      const code = block.querySelector("code")?.textContent || "";
      window.dispatchEvent(new CustomEvent("clauden:artifact", {
        detail: { code, lang, name: `artifact.${lang === "markdown" ? "md" : lang}` },
      }));
    });
  });
  container.querySelectorAll("[data-copy-code]").forEach((button) => {
    if (button.dataset.wired) return;
    button.dataset.wired = "1";
    button.addEventListener("click", async (e) => {
      e.stopPropagation();
      const id = button.dataset.copyCode;
      const code = container.querySelector(`[data-code-id="${id}"] code`);
      const raw = code?.dataset?.raw;
      const text = raw ? decodeURIComponent(raw) : code?.textContent || "";
      try {
        await navigator.clipboard.writeText(text);
        button.innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m4 12 5 5L20 6"/></svg>';
        setTimeout(() => { button.innerHTML = copyIcon(); }, 1600);
      } catch {  }
    });
  });
}

export function extractCodeBlocks(text) {
  const blocks = [];
  const re = /```([a-zA-Z0-9_+-]*)\n([\s\S]*?)```/g;
  let match;
  let index = 0;
  while ((match = re.exec(String(text || ""))) !== null) {
    const lang = (match[1] || "").toLowerCase();
    const code = match[2];
    blocks.push({ index, lang, code });
    index += 1;
  }
  return blocks;
}

export function stripMarkdown(text) {
  return String(text || "")
    .replace(/<(?:ant)?artifact[\s\S]*?<\/(?:ant)?artifact>/g, " ")
    .replace(/```[\s\S]*?```/g, " ")
    .replace(/`([^`]+)`/g, "$1")
    .replace(/!\[[^\]]*\]\([^)]*\)/g, " ")
    .replace(/\[([^\]]+)\]\([^)]*\)/g, "$1")
    .replace(/[#>*_~|-]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

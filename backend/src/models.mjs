import { readJson } from "./store.mjs";
import path from "node:path";

const CONTEXT_WINDOW = 1_000_000;

export const MODEL_FAMILIES = [
  { id: "claude-fable-5-2", label: "Claude Fable 5.2", minPlan: null },
  { id: "claude-opus-5-5", label: "Claude Opus 5.5", minPlan: null },
  { id: "claude-sonnet-5-5", label: "Claude Sonnet 5.5", minPlan: null },
  { id: "claude-haiku-5-5", label: "Claude Haiku 5.5", minPlan: null },
  { id: "claude-fable-5-1", label: "Claude Fable 5.1", minPlan: null },
  { id: "claude-fable-5", label: "Claude Fable 5", minPlan: null },
  { id: "claude-opus-5", label: "Claude Opus 5", minPlan: null },
  { id: "claude-opus-4-8", label: "Claude Opus 4.8", minPlan: null },
  { id: "claude-sonnet-5", label: "Claude Sonnet 5", minPlan: null },
];

const PAID_ONLY = new Set(["claude-fable-5-2"]);

export class ModelRegistry {
  constructor({ config, log }) {
    this.config = config;
    this.log = log;
    this.overridesFile = path.join(config.root, "config", "aliases.json");
  }

  overrides() {
    const data = readJson(this.overridesFile, null);
    return data && typeof data === "object" ? data : {};
  }

  normalize(value) {
    const raw = String(value || "").trim().toLowerCase();
    if (!raw) return "";
    const dotted = raw.includes(".") ? raw.replaceAll(".", "-") : raw;
    for (const family of MODEL_FAMILIES) {
      if (family.id === raw || family.id === dotted) return family.id;
      if (family.label.toLowerCase() === raw) return family.id;
    }

    const stripped = dotted.split("/").pop().replace(/\[.*\]$/, "");
    return MODEL_FAMILIES.some((f) => f.id === stripped) ? stripped : "";
  }

  list(planId = "") {
    const overrides = this.overrides();
    return MODEL_FAMILIES.map((family) => {
      const extra = overrides[family.id] || {};
      return {
        id: family.id,
        object: "model",
        created: 0,
        owned_by: "anthropic",
        display_name: extra.label || family.label,
        name: extra.label || family.label,
        context_window: Number(extra.context || CONTEXT_WINDOW),
        available: !PAID_ONLY.has(family.id) || Boolean(planId && planId !== "Free"),
      };
    });
  }

  allows(planId, modelId) {
    if (!PAID_ONLY.has(modelId)) return true;
    return Boolean(planId) && planId !== "Free";
  }
}

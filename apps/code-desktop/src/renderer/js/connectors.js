const KEY = "cc:connectors";

export function loadConnectors() {
  try {
    const list = JSON.parse(localStorage.getItem(KEY) || "[]");
    return Array.isArray(list) ? list : [];
  } catch {
    return [];
  }
}

export function saveConnectors(list) {
  localStorage.setItem(KEY, JSON.stringify(Array.isArray(list) ? list : []));
}

export function connectorPayload() {
  return loadConnectors()
    .filter((item) => item && item.enabled !== false && item.url)
    .map((item) => ({
      id: item.id,
      title: item.title || item.url,
      url: item.url,
      headers: item.headers || "",
    }));
}

export function newConnectorId() {
  return `mcp_${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`;
}

export function defaultEffort() {
  const saved = localStorage.getItem("cc:effort");
  return saved == null ? "medium" : saved;
}

export function setDefaultEffort(value) {
  localStorage.setItem("cc:effort", String(value || ""));
}

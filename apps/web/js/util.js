export const $ = (sel, root = document) => root.querySelector(sel);
export const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];

export function el(tag, attrs = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs || {})) {
    if (value == null || value === false) continue;
    if (key === "class") node.className = value;
    else if (key === "html") node.innerHTML = value;
    else if (key === "text") node.textContent = value;
    else if (key === "dataset") Object.assign(node.dataset, value);
    else if (key === "style" && typeof value === "object") Object.assign(node.style, value);
    else if (key.startsWith("on") && typeof value === "function") node.addEventListener(key.slice(2), value);
    else if (value === true) node.setAttribute(key, "");
    else node.setAttribute(key, value);
  }
  for (const child of children.flat(3)) {
    if (child == null || child === false) continue;
    node.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
  return node;
}

export function frag(...children) {
  const f = document.createDocumentFragment();
  for (const child of children.flat(3)) {
    if (child == null || child === false) continue;
    f.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
  return f;
}

export function clear(node) {
  while (node.firstChild) node.removeChild(node.firstChild);
  return node;
}

export function go(path, { replace = false } = {}) {
  const target = String(path || "/").startsWith("/") ? String(path) : `/${path}`;
  if (replace) history.replaceState(null, "", target);
  else history.pushState(null, "", target);
  window.dispatchEvent(new PopStateEvent("popstate"));
}

const ICONS = {
  plus: '<path d="M12 5v14M5 12h14"/>',
  search: '<circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/>',
  chat: '<path d="M21 12a8 8 0 0 1-8 8H7l-4 3V12a8 8 0 0 1 8-8h2a8 8 0 0 1 8 8Z"/>',
  folder: '<path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Z"/>',
  code: '<path d="m8 6-6 6 6 6M16 6l6 6-6 6"/>',
  sparkles: '<path d="M12 3l1.8 4.6L18 9.5l-4.2 1.9L12 16l-1.8-4.6L6 9.5l4.2-1.9L12 3Z"/><path d="M19 14l.9 2.1L22 17l-2.1.9L19 20l-.9-2.1L16 17l2.1-.9L19 14Z"/>',
  settings: '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.9.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.9 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.9l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.9.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.9-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.9V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1Z"/>',
  arrowUp: '<path d="M12 19V5M6 11l6-6 6 6"/>',
  arrowLeft: '<path d="M19 12H5M11 18l-6-6 6-6"/>',
  arrowRight: '<path d="M5 12h14M13 6l6 6-6 6"/>',
  chevronDown: '<path d="m6 9 6 6 6-6"/>',
  chevronLeft: '<path d="m15 6-6 6 6 6"/>',
  chevronRight: '<path d="m9 6 6 6-6 6"/>',
  close: '<path d="M6 6l12 12M18 6 6 18"/>',
  menu: '<path d="M4 6h16M4 12h16M4 18h16"/>',
  more: '<circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/>',
  copy: '<rect x="9" y="9" width="12" height="12" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>',
  check: '<path d="m4 12 5 5L20 6"/>',
  refresh: '<path d="M20 11a8 8 0 1 0-2.3 5.7"/><path d="M20 4v7h-7"/>',
  thumbsUp: '<path d="M7 10v11H4a1 1 0 0 1-1-1v-9a1 1 0 0 1 1-1h3Zm0 0 4-7a2 2 0 0 1 2 2v4h5.2a2 2 0 0 1 2 2.4l-1.4 7A2 2 0 0 1 16.8 21H7"/>',
  thumbsDown: '<path d="M17 14V3h3a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1h-3Zm0 0-4 7a2 2 0 0 1-2-2v-4H5.8a2 2 0 0 1-2-2.4l1.4-7A2 2 0 0 1 7.2 3H17"/>',
  speaker: '<path d="M11 5 6 9H3v6h3l5 4V5Z"/><path d="M15.5 8.5a5 5 0 0 1 0 7M18.5 5.5a9 9 0 0 1 0 13"/>',
  mic: '<rect x="9" y="2" width="6" height="12" rx="3"/><path d="M5 10a7 7 0 0 0 14 0M12 17v5"/>',
  share: '<path d="M4 12v7a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-7"/><path d="M16 6l-4-4-4 4M12 2v14"/>',
  pin: '<path d="M12 17v5M5 9l7-7 7 7-2 1v5l2 2H5l2-2v-5l-2-1Z"/>',
  trash: '<path d="M3 6h18M8 6V4a1 1 0 0 1 1-1h6a1 1 0 0 1 1 1v2M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/>',
  edit: '<path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z"/>',
  download: '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3"/>',
  upload: '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M17 8l-5-5-5 5M12 3v12"/>',
  file: '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z"/><path d="M14 2v6h6"/>',
  image: '<rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="9" cy="9" r="2"/><path d="m21 15-5-5L5 21"/>',
  video: '<rect x="2" y="5" width="14" height="14" rx="2"/><path d="m22 8-6 4 6 4V8Z"/>',
  link: '<path d="M10 13a5 5 0 0 0 7 0l3-3a5 5 0 0 0-7-7l-1.5 1.5"/><path d="M14 11a5 5 0 0 0-7 0l-3 3a5 5 0 0 0 7 7l1.5-1.5"/>',
  paperclip: '<path d="m21 11-9 9a6 6 0 0 1-8.5-8.5l9-9a4 4 0 0 1 5.7 5.7l-9 9a2 2 0 0 1-2.9-2.9l8.5-8.5"/>',
  globe: '<circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3a15 15 0 0 1 0 18 15 15 0 0 1 0-18Z"/>',
  lock: '<rect x="4" y="10" width="16" height="11" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/>',
  sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>',
  moon: '<path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8Z"/>',
  monitor: '<rect x="2" y="3" width="20" height="14" rx="2"/><path d="M8 21h8M12 17v4"/>',
  user: '<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>',
  creditCard: '<rect x="2" y="5" width="20" height="14" rx="2"/><path d="M2 10h20"/>',
  key: '<circle cx="7.5" cy="15.5" r="4.5"/><path d="m10.7 12.3 8.3-8.3M17 6l2 2M14 3l2 2"/>',
  chart: '<path d="M3 3v18h18"/><path d="M7 15v3M12 10v8M17 6v12"/>',
  palette: '<path d="M12 3a9 9 0 1 0 0 18h1.5a2 2 0 0 0 0-4H12a2 2 0 0 1 0-4h5a4 4 0 0 0 4-4 6 6 0 0 0-9-6Z"/><circle cx="7.5" cy="10.5" r="1"/><circle cx="12" cy="7.5" r="1"/><circle cx="16.5" cy="10.5" r="1"/>',
  plug: '<path d="M9 2v6M15 2v6M6 8h12v4a6 6 0 0 1-6 6 6 6 0 0 1-6-6V8ZM12 18v4"/>',
  shield: '<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z"/>',
  logout: '<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><path d="m16 17 5-5-5-5M21 12H9"/>',
  github: '<path d="M9 19c-5 1.5-5-2.5-7-3m14 6v-3.9a3.4 3.4 0 0 0-.9-2.6c3-.3 6.1-1.5 6.1-6.7A5.2 5.2 0 0 0 19.8 5a4.9 4.9 0 0 0-.1-3.6s-1.1-.3-3.6 1.4a12.3 12.3 0 0 0-6.4 0C7.2 1.1 6.1 1.4 6.1 1.4A4.9 4.9 0 0 0 6 5a5.2 5.2 0 0 0-1.4 3.6c0 5.2 3.1 6.4 6.1 6.7A3.4 3.4 0 0 0 9.8 18V22"/>',
  terminal: '<path d="m4 17 6-6-6-6M12 19h8"/>',
  play: '<path d="M6 4l14 8-14 8V4Z"/>',
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  alert: '<path d="M12 3 2 21h20L12 3Z"/><path d="M12 9v5M12 18h.01"/>',
  external: '<path d="M15 3h6v6M10 14 21 3M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/>',
  bookmark: '<path d="M6 3h12v18l-6-4-6 4V3Z"/>',
  archive: '<rect x="3" y="4" width="18" height="5" rx="1"/><path d="M5 9v10a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V9M10 13h4"/>',
  brain: '<path d="M9.5 3a3 3 0 0 0-3 3 3 3 0 0 0-1.7 5.4A3 3 0 0 0 6 17a3 3 0 0 0 3.5 3V3Z"/><path d="M14.5 3a3 3 0 0 1 3 3 3 3 0 0 1 1.7 5.4A3 3 0 0 1 18 17a3 3 0 0 1-3.5 3V3Z"/>',
  users: '<circle cx="9" cy="8" r="3.5"/><path d="M2.5 20a6.5 6.5 0 0 1 13 0"/><path d="M16 4.5a3.5 3.5 0 0 1 0 7M17.5 14.5a6.5 6.5 0 0 1 4 5.5"/>',
  star: '<path d="m12 3 2.8 5.8 6.2.9-4.5 4.4 1 6.2-5.5-3-5.5 3 1-6.2L3 9.7l6.2-.9L12 3Z"/>',
  layers: '<path d="m12 2 10 5-10 5L2 7l10-5Z"/><path d="m2 12 10 5 10-5M2 17l10 5 10-5"/>',
  wand: '<path d="m15 4 5 5L7 22H2v-5L15 4Z"/><path d="M14 5l5 5M6 2v3M4.5 3.5h3M20 15v3M18.5 16.5h3"/>',
  stop: '<rect x="6" y="6" width="12" height="12" rx="2"/>',
  send: '<path d="M22 2 11 13M22 2l-7 20-4-9-9-4 20-7Z"/>',
  inbox: '<path d="M22 12h-6l-2 3h-4l-2-3H2"/><path d="M5.5 5h13L22 12v6a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2v-6L5.5 5Z"/>',
  lockOpen: '<rect x="4" y="10" width="16" height="11" rx="2"/><path d="M8 10V7a4 4 0 0 1 7.5-2"/>',
  qr: '<rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/><path d="M14 14h3v3h-3zM20 14h1M14 20h1M20 20h1"/>',
  panel: '<rect x="3" y="4" width="18" height="16" rx="2"/><path d="M9 4v16"/>',
  panelLeft: '<rect x="3" y="4" width="18" height="16" rx="2"/><path d="M9 4v16"/>',
  wave: '<path d="M3 12h2l2-6 3 12 3-9 2 5 2-2h4"/>',
  chevronUp: '<path d="m18 15-6-6-6 6"/>',
  circle: '<circle cx="12" cy="12" r="8"/>',
  briefcase: '<rect x="3" y="7" width="18" height="13" rx="2"/><path d="M8 7V5a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2M3 12h18"/>',
  pen: '<path d="m12 19 7-7 3 3-7 7-3-3ZM18 13l-1.5-7.5L2 2l3.5 14.5L13 18M2 2l7.586 7.586"/><circle cx="11" cy="11" r="2"/>',
  book: '<path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2Z"/>',
  sliders: '<path d="M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3M1 14h6M9 8h6M17 16h6"/>',
  filter: '<path d="M3 5h18l-7 8v6l-4 2v-8Z"/>',
  sort: '<path d="M7 4v16M3 8l4-4 4 4M17 20V4M13 16l4 4 4-4"/>',
  popout: '<path d="M14 4h6v6M20 4l-8 8M18 13v6a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h6"/>',
  robot: '<rect x="4" y="8" width="16" height="12" rx="3"/><path d="M12 4v4M9 13h.01M15 13h.01M9 17h6"/>',
  layout: '<rect x="3" y="3" width="18" height="18" rx="2"/><path d="M3 9h18M9 21V9"/>',
  bell: '<path d="M18 8a6 6 0 1 0-12 0c0 7-3 9-3 9h18s-3-2-3-9M13.7 21a2 2 0 0 1-3.4 0"/>',
  heart: '<path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1-1.1a5.5 5.5 0 0 0-7.8 7.8l1.1 1L12 21l7.7-7.6 1.1-1a5.5 5.5 0 0 0 0-7.8Z"/>',
  shieldCheck: '<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z"/><path d="m9 12 2 2 4-4"/>',
  cpu: '<rect x="6" y="6" width="12" height="12" rx="2"/><path d="M9 2v3M15 2v3M9 19v3M15 19v3M2 9h3M2 15h3M19 9h3M19 15h3"/>',
  calendar: '<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M8 3v4M16 3v4M3 10h18"/>',
  bolt: '<path d="M13 2 4 14h7l-1 8 9-12h-7l1-8Z"/>',
  info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v5M12 8h.01"/>',
};

const MASCOT_BODY =
  "M48.52 78.82C47.46 78.62 46.40 78.06 45.68 77.34C43.98 75.63 43.68 73.14 44.64 68.70C45.07 66.74 45.65 63.40 45.65 62.94C45.65 62.03 45.08 61.79 44.28 62.36C44.04 62.53 42.07 64.46 39.89 66.65C35.71 70.86 34.92 71.55 33.47 72.24C32.37 72.76 31.41 72.93 30.38 72.80C28.28 72.54 26.76 71.52 25.91 69.81C25.57 69.13 25.51 68.90 25.47 68.20C25.35 66.40 26.02 65.02 27.92 63.12C28.95 62.08 30.94 60.30 32.32 59.18C33.76 58.02 35.35 56.63 35.81 56.15C36.29 55.65 36.31 55.29 35.87 55.07C35.36 54.81 34.38 54.92 30.81 55.63C29.16 55.97 27.18 56.33 26.07 56.51C24.83 56.71 22.29 56.68 21.58 56.46C19.98 55.97 19.10 55.08 18.55 53.39C18.43 53.00 18.40 52.65 18.43 51.93C18.46 51.06 18.50 50.91 18.84 50.23C19.70 48.49 21.21 47.41 23.70 46.73C25.08 46.36 27.53 46.09 32.13 45.80C33.59 45.71 36.10 45.37 36.25 45.25C36.47 45.06 36.40 44.61 36.11 44.38C35.96 44.27 34.71 43.49 33.32 42.66C31.94 41.83 30.49 40.95 30.10 40.70C27.52 39.04 25.96 37.64 25.31 36.40C24.14 34.15 24.38 32.37 26.09 30.64C27.27 29.44 28.76 28.89 30.62 28.98C32.28 29.07 33.41 29.52 35.40 30.89C37.63 32.43 39.59 34.32 43.35 38.57C44.49 39.85 45.08 40.16 45.42 39.64C45.79 39.09 45.39 37.30 43.65 31.64C42.44 27.69 42.35 25.95 43.27 24.08C43.90 22.80 45.20 21.76 46.73 21.33C46.99 21.25 47.69 21.20 48.44 21.20C49.95 21.20 50.75 21.40 51.66 22.01C52.41 22.51 52.56 22.64 53.06 23.28C54.35 24.92 54.79 27.16 55.22 34.28C55.41 37.33 55.51 38.12 55.74 38.40C55.79 38.47 56.01 38.53 56.23 38.53C56.59 38.53 56.68 38.47 57.27 37.87C57.62 37.50 58.16 36.90 58.47 36.52C63.85 29.90 65.77 28.12 68.33 27.42C69.21 27.17 71.19 27.18 71.92 27.43C73.36 27.92 74.41 28.80 74.97 30.00C75.23 30.54 75.29 30.81 75.32 31.50C75.37 32.54 75.20 33.28 74.61 34.47C73.80 36.13 72.61 37.45 68.65 41.08C67.13 42.48 65.71 43.98 65.41 44.51C65.21 44.87 65.26 45.40 65.51 45.64C65.71 45.82 65.84 45.85 66.56 45.84C67.01 45.84 67.60 45.80 67.87 45.76C71.39 45.19 72.61 45.06 74.41 45.06C76.57 45.05 77.29 45.19 78.65 45.86C81.41 47.22 82.47 50.39 81.03 52.98C80.26 54.36 78.98 55.25 77.05 55.76C76.20 55.99 73.32 56.10 71.22 56.00C70.24 55.95 68.75 55.88 67.91 55.85C65.77 55.75 65.09 55.95 65.09 56.64C65.09 57.03 65.41 57.34 67.48 58.99C71.72 62.35 73.29 63.91 74.07 65.53C74.59 66.60 74.69 67.08 74.63 68.15C74.57 69.23 74.39 69.75 73.78 70.70C72.79 72.20 71.07 73.10 69.14 73.10C68.14 73.10 67.38 72.90 66.46 72.40C65.08 71.66 64.05 70.66 60.55 66.70C59.69 65.73 58.48 64.43 57.86 63.79C56.83 62.75 56.69 62.65 56.37 62.65C55.86 62.65 55.71 62.90 55.71 63.82C55.71 64.22 55.78 64.92 55.86 65.38C56.12 66.88 56.40 69.99 56.40 71.48C56.39 73.26 56.15 74.40 55.51 75.66C54.77 77.09 53.37 78.25 51.82 78.70C51.07 78.92 49.38 78.98 48.52 78.82Z";
const MASCOT_EYE_LEFT =
  "M44.42 55.04C43.58 54.82 42.98 54.15 42.64 53.04C42.21 51.65 42.27 47.83 42.73 46.59C43.40 44.79 45.44 44.22 46.72 45.46C47.21 45.94 47.37 46.26 47.61 47.19C47.77 47.80 47.80 48.27 47.80 49.86C47.80 52.95 47.44 54.11 46.29 54.79C45.81 55.07 44.97 55.18 44.42 55.04Z";
const MASCOT_EYE_RIGHT =
  "M55.50 55.00C54.17 54.50 53.57 53.02 53.57 50.18C53.56 48.69 53.73 47.21 53.97 46.56C54.48 45.20 55.83 44.48 57.08 44.92C57.73 45.15 58.21 45.60 58.54 46.27C58.97 47.17 59.06 47.89 59.01 50.31C58.96 52.73 58.83 53.36 58.21 54.17C57.61 54.95 56.38 55.32 55.50 55.00Z";
let mascotSeq = 0;

export function claudeMark(size = 24, extra = "") {
  mascotSeq += 1;
  const id = `cl-eyes-${mascotSeq}`;
  return (
    `<svg class="${extra}" width="${size}" height="${size}" viewBox="0 0 100 100" aria-hidden="true">` +
    `<defs><mask id="${id}" maskUnits="userSpaceOnUse" x="0" y="0" width="100" height="100">` +
    `<rect x="0" y="0" width="100" height="100" fill="#fff"/>` +
    `<g${size <= 32 ? ' transform="translate(50 50) scale(1.7) translate(-50 -50)"' : ""}>` +
    `<path d="${MASCOT_EYE_LEFT}" fill="#000" stroke="#000" stroke-width="1.6" stroke-linejoin="round"/><path d="${MASCOT_EYE_RIGHT}" fill="#000" stroke="#000" stroke-width="1.6" stroke-linejoin="round"/>` +
    `</g>` +
    `</mask></defs>` +
    `<path fill="currentColor" mask="url(#${id})" d="${MASCOT_BODY}"/></svg>`
  );
}

export function claudeTile(size = 88) {
  return `<span class="welcome-mark" style="width:${size}px;height:${size}px">${
    claudeMark(Math.round(size * 0.6))
  }</span>`;
}

export function icon(name, cls = "") {
  const path = ICONS[name] || ICONS.sparkles;
  return `<svg class="${cls}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${path}</svg>`;
}

export function iconNode(name, cls = "") {
  const wrap = document.createElement("span");
  wrap.innerHTML = icon(name, cls);
  return wrap.firstElementChild;
}

export function plural(n, one, few, many) {
  const value = Math.abs(Number(n) || 0) % 100;
  const last = value % 10;
  if (value > 10 && value < 20) return many;
  if (last > 1 && last < 5) return few;
  if (last === 1) return one;
  return many;
}

export function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

export function timeAgo(ts) {
  if (!ts) return "";
  const diff = Date.now() - Number(ts);
  if (diff < 60_000) return "сейчас";
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} мин`;
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)} ч`;
  if (diff < 7 * 86_400_000) return `${Math.floor(diff / 86_400_000)} д`;
  const d = new Date(Number(ts));
  return d.toLocaleDateString("ru-RU", { day: "numeric", month: "short" });
}

export function formatDate(ts) {
  if (!ts) return "";
  return new Date(Number(ts)).toLocaleDateString("ru-RU", { day: "numeric", month: "long", year: "numeric" });
}

export function formatTime(ts) {
  if (!ts) return "";
  return new Date(Number(ts)).toLocaleTimeString("ru-RU", { hour: "2-digit", minute: "2-digit" });
}

export function bytes(size) {
  const n = Number(size) || 0;
  if (n < 1024) return `${n} Б`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(n < 10_240 ? 1 : 0)} КБ`;
  if (n < 1024 * 1024 * 1024) return `${(n / 1024 / 1024).toFixed(1)} МБ`;
  return `${(n / 1024 / 1024 / 1024).toFixed(2)} ГБ`;
}

export function initials(name, email = "") {
  const source = String(name || email || "?").trim();
  const parts = source.split(/\s+/).filter(Boolean);
  if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase();
  return source.slice(0, 2).toUpperCase();
}

export function debounce(fn, ms = 250) {
  let timer = 0;
  return (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), ms);
  };
}

export function throttleRaf(fn) {
  let queued = false;
  let lastArgs;
  return (...args) => {
    lastArgs = args;
    if (queued) return;
    queued = true;
    requestAnimationFrame(() => {
      queued = false;
      fn(...lastArgs);
    });
  };
}

export async function copyText(text) {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    const area = el("textarea", { style: { position: "fixed", opacity: "0" } });
    area.value = text;
    document.body.append(area);
    area.select();
    const ok = document.execCommand("copy");
    area.remove();
    return ok;
  }
}

export function downloadBlob(blob, name) {
  const url = URL.createObjectURL(blob);
  const a = el("a", { href: url, download: name });
  document.body.append(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 4000);
}

export function downloadText(text, name, mime = "text/plain;charset=utf-8") {
  downloadBlob(new Blob([text], { type: mime }), name);
}

export function toast(message, kind = "", ms = 2600) {
  const root = $("#toast-root");
  if (!root) return;
  const node = el("div", { class: `toast ${kind}`, text: message });
  root.append(node);
  setTimeout(() => {
    node.style.transition = "opacity .2s ease, transform .2s ease";
    node.style.opacity = "0";
    node.style.transform = "translateY(6px)";
    setTimeout(() => node.remove(), 220);
  }, ms);
}

export function confirmDialog({ title, text, confirm = "Удалить", danger = true }) {
  return new Promise((resolve) => {
    const root = $("#modal-root");
    const onKey = (event) => {
      if (event.key !== "Escape") return;
      if (root.lastElementChild !== backdrop) return;
      event.preventDefault();
      close(false);
    };
    const close = (value) => {
      document.removeEventListener("keydown", onKey, true);
      backdrop.remove();
      resolve(value);
    };
    const backdrop = el("div", {
      class: "modal-backdrop",
      onclick: (e) => { if (e.target === backdrop) close(false); },
    },
      el("div", { class: "modal", style: { width: "min(420px, 100%)" } },
        el("div", { class: "modal-head" }, el("h2", { class: "modal-title", text: title })),
        el("div", { class: "modal-body" }, el("p", { class: "muted small", style: { margin: "0" }, text })),
        el("div", { class: "modal-foot" },
          el("button", { class: "btn btn-ghost", text: "Отмена", onclick: () => close(false) }),
          el("button", { class: `btn ${danger ? "btn-danger" : "btn-primary"}`, text: confirm, onclick: () => close(true) }),
        ),
      ),
    );
    root.append(backdrop);
    document.addEventListener("keydown", onKey, true);
    setTimeout(() => backdrop.querySelector(".btn-danger, .btn-primary")?.focus(), 20);
  });
}

export function promptDialog({ title, label = "", value = "", placeholder = "", multiline = false, confirm = "Сохранить" }) {
  return new Promise((resolve) => {
    const root = $("#modal-root");
    const onKey = (event) => {
      if (event.key !== "Escape") return;
      if (root.lastElementChild !== backdrop) return;
      event.preventDefault();
      close(null);
    };
    const close = (v) => {
      document.removeEventListener("keydown", onKey, true);
      backdrop.remove();
      resolve(v);
    };
    const field = multiline
      ? el("textarea", { class: "textarea", placeholder, value })
      : el("input", { class: "input", placeholder, value });
    const backdrop = el("div", {
      class: "modal-backdrop",
      onclick: (e) => { if (e.target === backdrop) close(null); },
    },
      el("div", { class: "modal" },
        el("div", { class: "modal-head" }, el("h2", { class: "modal-title", text: title })),
        el("div", { class: "modal-body" },
          el("div", { class: "field" }, label ? el("label", { text: label }) : null, field),
        ),
        el("div", { class: "modal-foot" },
          el("button", { class: "btn btn-ghost", text: "Отмена", onclick: () => close(null) }),
          el("button", { class: "btn btn-primary", text: confirm, onclick: () => close(field.value.trim()) }),
        ),
      ),
    );
    root.append(backdrop);
    document.addEventListener("keydown", onKey, true);
    setTimeout(() => field.focus(), 30);
    field.addEventListener("keydown", (e) => {
      if (e.key === "Enter" && !multiline) { e.preventDefault(); close(field.value.trim()); }
    });
  });
}

export function menuAt(anchor, items) {
  $$(".menu").forEach((m) => m.remove());
  const rect = anchor.getBoundingClientRect();
  const menu = el("div", { class: "menu" });
  for (const item of items) {
    if (item === "-") { menu.append(el("div", { class: "menu-sep" })); continue; }
    menu.append(el("button", {
      class: `menu-item ${item.danger ? "danger" : ""}`,
      onclick: (e) => { e.stopPropagation(); menu.remove(); item.onClick?.(); },
    }, el("span", { html: icon(item.icon || "chevronRight") }), el("span", { text: item.label })));
  }
  document.body.append(menu);
  const width = menu.offsetWidth;
  const height = menu.offsetHeight;
  let left = Math.min(rect.left, window.innerWidth - width - 8);
  let top = rect.bottom + 4;
  if (top + height > window.innerHeight - 8) top = Math.max(8, rect.top - height - 4);
  menu.style.left = `${Math.max(8, left)}px`;
  menu.style.top = `${top}px`;
  const close = (e) => {
    if (!menu.contains(e.target)) { menu.remove(); document.removeEventListener("mousedown", close); }
  };
  setTimeout(() => document.addEventListener("mousedown", close), 0);
  return menu;
}

export function openModal(content, { wide = false, full = false, onClose } = {}) {
  const root = $("#modal-root");
  const backdrop = el("div", {
    class: "modal-backdrop",
    onclick: (e) => { if (e.target === backdrop) close(); },
  });
  const box = el("div", { class: `modal ${wide ? "wide" : ""} ${full ? "full" : ""}` });
  box.append(content);
  backdrop.append(box);
  root.append(backdrop);

  const onKey = (event) => {
    if (event.key !== "Escape") return;
    if (root.lastElementChild !== backdrop) return;
    event.preventDefault();
    close();
  };
  document.addEventListener("keydown", onKey, true);
  function close() {
    document.removeEventListener("keydown", onKey, true);
    backdrop.remove();
    onClose?.();
  }
  return { close, box, backdrop };
}

export function isMobile() {
  return window.matchMedia("(max-width: 900px)").matches;
}

export function relTimeShort(ts) {
  const d = new Date(Number(ts) || 0);
  const today = new Date();
  const sameDay = d.toDateString() === today.toDateString();
  if (sameDay) return d.toLocaleTimeString("ru-RU", { hour: "2-digit", minute: "2-digit" });
  return d.toLocaleDateString("ru-RU", { day: "numeric", month: "short" });
}

export function safeJsonParse(text, fallback = null) {
  try { return JSON.parse(text); } catch { return fallback; }
}

export function uid(prefix = "") {
  const value = crypto.randomUUID ? crypto.randomUUID() : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
  return prefix ? `${prefix}-${value}` : value;
}

export function escapeRegExp(value) {
  return String(value).replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

export function highlightMatch(text, query) {
  const safe = escapeHtml(text);
  if (!query) return safe;
  const re = new RegExp(`(${escapeRegExp(escapeHtml(query))})`, "ig");
  return safe.replace(re, "<mark>$1</mark>");
}

export function titleFromText(text, max = 48) {
  const oneLine = String(text || "").replace(/\s+/g, " ").trim();
  if (!oneLine) return "Новый чат";
  return oneLine.length <= max ? oneLine : `${oneLine.slice(0, max - 1).trimEnd()}…`;
}

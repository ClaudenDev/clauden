import {
  api, customApi, isDesktopApp, isCustomApiSupported,
  streamClaudeDesktop, streamCustomApi,
} from "./api.js";
import { store, loadSettings, loadTasks, saveTasks, loadScheduled, saveScheduled } from "./store.js";
import { uid, plural, go } from "./util.js";

let started = false;
let runningScheduled = false;
let lastBreakAt = Date.now();
let breakSnoozeUntil = 0;

export function addTask(userId, title, dueAt = 0) {
  const text = String(title || "").trim().slice(0, 300);
  if (!text) return null;
  const task = {
    id: uid("task"),
    title: text,
    done: false,
    createdAt: Date.now(),
    dueAt: Number(dueAt) || 0,
  };
  saveTasks(userId, [task, ...loadTasks(userId)]);
  return task;
}

export function toggleTask(userId, id) {
  const next = loadTasks(userId).map((task) => (task.id === id ? { ...task, done: !task.done } : task));
  saveTasks(userId, next);
  return next;
}

export function deleteTask(userId, id) {
  const next = loadTasks(userId).filter((task) => task.id !== id);
  saveTasks(userId, next);
  return next;
}

export function repeatLabel(repeat) {
  if (repeat === "daily") return "Каждый день";
  if (repeat === "weekly") return "Каждую неделю";
  return "Один раз";
}

export function nextRunAt(runAt, repeat, now = Date.now()) {
  if (repeat !== "daily" && repeat !== "weekly") return 0;
  const step = repeat === "daily" ? 24 * 60 * 60 * 1000 : 7 * 24 * 60 * 60 * 1000;
  let next = Number(runAt) || now;
  if (next <= now) next += step;
  while (next <= now) next += step;
  return next;
}

export function addScheduled(userId, { title, prompt, runAt, repeat = "once" }) {
  const text = String(prompt || "").trim().slice(0, 20_000);
  const when = Number(runAt) || 0;
  if (!text || when <= Date.now()) return null;
  const task = {
    id: uid("sched"),
    title: String(title || "").trim().slice(0, 120) || text.slice(0, 60),
    prompt: text,
    runAt: when,
    repeat: ["once", "daily", "weekly"].includes(repeat) ? repeat : "once",
    createdAt: Date.now(),
    lastRunAt: 0,
    lastStatus: "",
    lastChatId: "",
  };
  saveScheduled(userId, [...loadScheduled(userId), task]);
  return task;
}

export function deleteScheduled(userId, id) {
  const next = loadScheduled(userId).filter((task) => task.id !== id);
  saveScheduled(userId, next);
  return next;
}

export async function ensureNotifications() {
  if (typeof Notification === "undefined") return false;
  if (Notification.permission === "granted") return true;
  if (Notification.permission === "denied") return false;
  try {
    return (await Notification.requestPermission()) === "granted";
  } catch {
    return false;
  }
}

export function isQuietNow(settings, date = new Date()) {
  if (!settings?.quietHours) return false;
  const days = Array.isArray(settings.quietDays) ? settings.quietDays : [];
  const day = date.getDay() === 0 ? 7 : date.getDay();
  if (days.length && !days.includes(day)) return false;
  const minutes = date.getHours() * 60 + date.getMinutes();
  const start = Number(settings.quietStart) || 0;
  const end = Number(settings.quietEnd) || 0;
  if (start === end) return true;
  if (start < end) return minutes >= start && minutes < end;
  return minutes >= start || minutes < end;
}

export function quietRangeLabel(settings) {
  const fmt = (value) => `${String(Math.floor((Number(value) || 0) / 60)).padStart(2, "0")}:${String((Number(value) || 0) % 60).padStart(2, "0")}`;
  return `${fmt(settings.quietStart)}–${fmt(settings.quietEnd)}`;
}

function notify(ctx, title, body, onClick) {
  if (typeof Notification !== "undefined" && Notification.permission === "granted") {
    try {
      const note = new Notification(title, { body, tag: `clauden-${title}` });
      if (onClick) note.onclick = onClick;
      return;
    } catch {

    }
  }
  ctx?.toast?.(`${title}. ${body}`);
}

function checkBreak(ctx) {
  const settings = loadSettings();
  if (!settings.breakReminders) {
    lastBreakAt = Date.now();
    return;
  }
  if (Date.now() < breakSnoozeUntil || isQuietNow(settings)) return;
  const every = Math.max(5, Number(settings.breakInterval) || 45) * 60_000;
  if (Date.now() - lastBreakAt < every) return;
  lastBreakAt = Date.now();
  notify(ctx, "Пора сделать перерыв", "Отвлекитесь от экрана на пару минут. Нажмите, чтобы отложить на 15 минут.", () => {
    breakSnoozeUntil = Date.now() + 15 * 60_000;
    ctx?.toast?.("Напоминание отложено на 15 минут");
  });
}

const SCHEDULED_SYSTEM =
  "Это отложенный запрос пользователя. Выполни его полностью и дай готовый результат.";

async function executeScheduled(ctx, task, settings) {
  if (isDesktopApp() && settings.provider === "claude" && ctx.state.claude?.linked) {
    let conversationId = "";
    const text = await streamClaudeDesktop({
      conversationId: "",
      model: settings.claudeModel || "",
      messages: [{ role: "user", content: task.prompt }],
      system: SCHEDULED_SYSTEM,
      webSearch: false,
      thinking: true,
    }, {
      handlers: { onConversation: (id) => { conversationId = id; } },
    });
    return { content: text || "", provider: "claude", conversationId };
  }
  if (isDesktopApp() && isCustomApiSupported() && settings.provider === "custom") {
    let list = Array.isArray(ctx.state.customApis) ? ctx.state.customApis : [];
    if (!list.length) {
      try { list = await customApi.list(); } catch { list = []; }
    }
    const item = list.find((entry) => entry.id === settings.customApiId) || list[0];
    const model = settings.customModel || item?.models?.[0]?.id || "";
    if (item && model) {
      const text = await streamCustomApi({
        apiId: item.id,
        model,
        messages: [{ role: "user", content: task.prompt }],
        system: SCHEDULED_SYSTEM,
      }, { handlers: {} });
      return { content: text || "", provider: "custom", customApiId: item.id, customModel: model };
    }
  }
  const response = await api.chatOnce({
    model: settings.model,
    messages: [{ role: "user", content: task.prompt }],
    clauden_system: SCHEDULED_SYSTEM,
  });
  return { content: response?.choices?.[0]?.message?.content || "", provider: "clauden" };
}

export async function runDueScheduled(ctx) {
  if (runningScheduled) return;
  const userId = ctx?.state?.user?.id;
  if (!userId || (typeof navigator !== "undefined" && navigator.onLine === false)) return;
  const tasks = loadScheduled(userId);
  const now = Date.now();
  const due = tasks.filter((task) => task.runAt > 0 && task.runAt <= now);
  if (!due.length) return;

  runningScheduled = true;
  let changed = false;
  for (const task of due) {
    const settings = loadSettings();
    let status = "";
    let chatId = "";
    try {
      const result = await executeScheduled(ctx, task, settings);
      const content = result.content || "";
      if (!content.trim()) throw new Error("Пустой ответ");
      const stamp = Date.now();
      const chat = {
        id: uid("chat"),
        title: (task.title || task.prompt).slice(0, 80),
        createdAt: stamp,
        updatedAt: stamp,
        pinned: false,
        projectId: "",
        provider: result.provider,
        ...(result.conversationId ? { claudeConversationId: result.conversationId } : {}),
        ...(result.customApiId ? { customApiId: result.customApiId } : {}),
        ...(result.customModel ? { customModel: result.customModel } : {}),
        messages: [
          { id: uid("msg"), role: "user", content: task.prompt, ts: stamp },
          { id: uid("msg"), role: "assistant", content, ts: stamp },
        ],
      };
      await store.putChat(chat);
      chatId = chat.id;
      status = "Готово";
      notify(ctx, `Отложенный запрос: ${task.title}`, content.replace(/\s+/g, " ").slice(0, 140), () => {
        go(`/chat/${chat.id}`);
      });
    } catch (err) {
      status = err?.message || "Не удалось выполнить";
      notify(ctx, `Отложенный запрос не выполнен: ${task.title}`, status.slice(0, 140));
    }
    const current = loadScheduled(userId);
    const next = current.map((item) =>
      item.id === task.id
        ? {
            ...item,
            lastRunAt: Date.now(),
            lastStatus: status,
            lastChatId: chatId,
            runAt: nextRunAt(task.runAt, task.repeat),
          }
        : item,
    );
    saveScheduled(userId, next);
    changed = true;
  }
  runningScheduled = false;

  if (changed) {
    try {
      ctx.state.chats = await store.allChats();
      ctx.refreshSidebar?.();
      ctx.backupSoon?.();
      if (ctx.state.route?.name === "settings" && ["tasks", "scheduled", "reflect"].includes(ctx.state.route.id)) {
        ctx.renderView?.();
      }
    } catch {

    }
  }
}

export function startRoutines(ctx) {
  if (started) return;
  started = true;
  const tick = () => {
    checkBreak(ctx);
    runDueScheduled(ctx).catch(() => {});
  };
  setTimeout(tick, 4000);
  setInterval(tick, 30_000);
}

export function tasksSummary(tasks) {
  const open = tasks.filter((task) => !task.done).length;
  return open ? `${open} ${plural(open, "открытая", "открытые", "открытых")}` : "Нет открытых";
}

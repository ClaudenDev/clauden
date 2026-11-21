export const DEFAULT_PLANS = {
  Free: {
    label: "Free",
    paid: false,
    price: 0,
    compareAt: 0,
    tagline: "Только Claude-аккаунт, без моделей Clauden",
    features: [
      "Вход через Claude-аккаунт",
      "Запросы с вашего аккаунта claude.ai",
      "Покупка подписки Clauden в рублях",
    ],
    cta: "Купить подписку",
    group: "",
    badge: "",
    order: 0,
  },
  Pro: {
    label: "Pro",
    paid: true,
    seat: true,
    price: 20,
    compareAt: 0,
    tagline: "Для исследований, кода и порядка",
    features: [
      "Проекты с инструкциями и файлами",
      "Задачи, память и навыки",
      "Больше лимитов каждые 5 часов и каждую неделю",
      "Доступ к мощным моделям",
      "Приоритетная поддержка",
    ],
    cta: "Получить Pro",
    group: "",
    badge: "",
    order: 1,
  },
  Max: {
    label: "Max",
    paid: true,
    seat: true,
    price: 100,
    compareAt: 0,
    tagline: "5× лимитов Pro каждые 5 часов и каждую неделю",
    features: [
      "Всё из Pro",
      "В 5 раз больше лимитов",
      "Приоритетный доступ",
      "Ранний доступ к новым возможностям",
    ],
    cta: "Выбрать Max",
    group: "max_ultra",
    badge: "",
    order: 2,
  },
  Ultra: {
    label: "Ultra",
    paid: true,
    seat: true,
    price: 180,
    compareAt: 200,
    tagline: "7,4× лимитов Pro каждую неделю",
    features: [
      "Всё из Pro",
      "До 7,4× больше лимитов Pro",
      "Максимальный приоритет",
      "Ранний доступ к новым возможностям",
      "Выделенная поддержка",
    ],
    cta: "Выбрать Ultra",
    group: "max_ultra",
    badge: "Скидка $20",
    order: 3,
  },
};

export const PLAN_LIMITS = {
  Free: { window5h: 0, week: 0 },
  Pro: { window5h: 45, week: 300 },
  Max: { window5h: 225, week: 1500 },
  Ultra: { window5h: 333, week: 2220 },
};

export function buildPlans(overrides = {}) {
  const plans = {};
  for (const [id, base] of Object.entries(DEFAULT_PLANS)) {
    const extra = Object.fromEntries(
      Object.entries(overrides[id] || {}).filter(
        ([, value]) => value !== undefined && value !== "",
      ),
    );
    plans[id] = { ...base, ...extra };
  }
  return plans;
}

export function normalizePlanId(plans, value) {
  const raw = String(value || "").trim();
  if (!raw) return "";
  const byId = Object.keys(plans).find((id) => id.toLowerCase() === raw.toLowerCase());
  if (byId) return byId;
  const byLabel = Object.entries(plans).find(
    ([, plan]) => String(plan.label || "").toLowerCase() === raw.toLowerCase(),
  );
  return byLabel ? byLabel[0] : "";
}

export function planFor(plans, value, fallbackId = "Free") {
  const id = normalizePlanId(plans, value) || fallbackId;
  const plan = plans[id] || plans[fallbackId];
  return {
    id,
    label: plan?.label || id,
    paid: Boolean(plan?.paid),
    limits: PLAN_LIMITS[id] || PLAN_LIMITS.Free,
  };
}

export function publicPlans(plans) {
  return Object.entries(plans)
    .sort((a, b) => (a[1].order ?? 99) - (b[1].order ?? 99))
    .map(([id, plan]) => ({
      id,
      label: plan.label || id,
      paid: Boolean(plan.paid),
      price: Number(plan.price) || 0,
      compareAt: Number(plan.compareAt) || 0,
      tagline: plan.tagline || "",
      features: Array.isArray(plan.features) ? plan.features : [],
      cta: plan.cta || "",
      group: plan.group || "",
      badge: plan.badge || "",
      order: Number(plan.order) || 0,
    }));
}

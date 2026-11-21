import { h, icon, rpc, mascotNode } from "./ui.js";

export function renderLogin(root, { onDone, apiUrl = "https://clauden.best", email = "" }) {
  root.replaceChildren();

  const errorBox = h("div", { class: "login-error hidden" });
  const emailInput = h("input", {
    type: "email",
    value: email,
    placeholder: "you@example.com",
    autocomplete: "username",
    spellcheck: "false",
  });
  const passInput = h("input", {
    type: "password",
    placeholder: "••••••••",
    autocomplete: "current-password",
  });
  const submit = h("button", { class: "login-submit", type: "submit" }, "Войти");
  const urlLabel = h("a", {
    href: "#",
    text: apiUrl.replace(/^https?:\/\//, ""),
    onclick: (event) => {
      event.preventDefault();
      const value = prompt("Адрес сервера", apiUrl);
      if (value && value.trim()) {
        rpc(window.clauden.auth.setApiUrl(value.trim())).then(() => {
          urlLabel.textContent = value.trim().replace(/^https?:\/\//, "");
        });
      }
    },
  });

  const form = h(
    "form",
    {
      onsubmit: async (event) => {
        event.preventDefault();
        errorBox.classList.add("hidden");
        const emailValue = emailInput.value.trim();
        const password = passInput.value;
        if (!emailValue || !password) {
          showError("Введите почту и пароль");
          return;
        }
        submit.disabled = true;
        submit.textContent = "Входим…";
        try {
          const data = await rpc(window.clauden.auth.login({ email: emailValue, password }));
          const auth = await rpc(window.clauden.auth.state());
          onDone({ ...auth, user: data?.user || auth.user });
        } catch (err) {
          showError(err.message || "Не удалось войти");
          submit.disabled = false;
          submit.textContent = "Войти";
        }
      },
    },
    h("div", { class: "field" }, h("label", { text: "Почта" }), emailInput),
    h("div", { class: "field" }, h("label", { text: "Пароль" }), passInput),
    errorBox,
    submit,
  );

  function showError(message) {
    errorBox.textContent = message;
    errorBox.classList.remove("hidden");
  }

  const card = h(
    "div",
    { class: "login-card" },
    h(
      "div",
      { class: "login-brand" },
      mascotNode(30),
      h("h1", { text: "Clauden Code" }),
    ),
    h("p", {
      class: "login-sub",
      text: "Войдите аккаунтом Clauden — сессии, репозитории и агент подтянутся автоматически.",
    }),
    form,
    h("div", { class: "login-foot" }, h("span", { text: "Сервер:" }), urlLabel),
  );

  root.append(h("div", { class: "login" }, card));
  setTimeout(() => emailInput.focus(), 60);
}

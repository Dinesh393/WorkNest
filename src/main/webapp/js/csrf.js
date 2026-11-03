(() => {
  "use strict";

  const script = document.currentScript;
  if (!script || !script.src) return;

  const scriptPath = new URL(script.src, window.location.href).pathname;
  const contextPath = scriptPath.slice(0, -"/js/csrf.js".length);
  const tokenRequest = window.fetch.bind(window);
  const tokenPromise = tokenRequest(`${contextPath}/csrf-token`, {
    method: "GET",
    credentials: "same-origin",
    cache: "no-store",
    headers: { Accept: "application/json" }
  }).then(async response => {
    if (!response.ok) throw new Error("token-unavailable");
    const payload = await response.json();
    if (!payload || typeof payload.token !== "string" || !payload.token) {
      throw new Error("token-unavailable");
    }
    return payload.token;
  });

  const showFailure = form => {
    let message = form && form.querySelector(".csrf-security-error");
    if (!message) {
      message = document.createElement("p");
      message.className = "csrf-security-error";
      message.setAttribute("role", "alert");
      message.textContent = "Security check failed. Refresh the page and try again.";
      if (form) form.prepend(message);
      else document.body.prepend(message);
    }
    message.hidden = false;
  };

  const originalFetch = window.fetch.bind(window);
  window.fetch = function (resource, init) {
    const options = init || {};
    const requestMethod = resource instanceof Request ? resource.method : "GET";
    const method = String(options.method || requestMethod).toUpperCase();
    if (["GET", "HEAD", "OPTIONS", "TRACE"].includes(method)) {
      return originalFetch(resource, init);
    }

    return tokenPromise.then(token => {
      const headers = new Headers(resource instanceof Request ? resource.headers : undefined);
      new Headers(options.headers || undefined).forEach((value, key) => headers.set(key, value));
      headers.set("X-CSRF-Token", token);
      return originalFetch(resource, {
        ...options,
        headers,
        credentials: options.credentials || "same-origin"
      });
    }, error => {
      showFailure(null);
      throw error;
    });
  };

  const resubmitting = new WeakSet();
  document.addEventListener("submit", event => {
    const form = event.target;
    if (!(form instanceof HTMLFormElement) || resubmitting.has(form)) return;
    const tokenField = form.querySelector('input[name="_csrf"]');
    if (tokenField && tokenField.value) return;

    event.preventDefault();
    tokenPromise.then(token => {
      let field = form.querySelector('input[name="_csrf"]');
      if (!field) {
        field = document.createElement("input");
        field.type = "hidden";
        field.name = "_csrf";
        form.appendChild(field);
      }
      field.value = token;
      resubmitting.add(form);
      if (typeof form.requestSubmit === "function") form.requestSubmit(event.submitter || undefined);
      else form.submit();
      resubmitting.delete(form);
    }).catch(() => showFailure(form));
  }, true);
})();

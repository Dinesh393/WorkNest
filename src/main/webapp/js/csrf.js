(() => {
  "use strict";

  const script = document.currentScript;
  if (!script || !script.src) return;

  const scriptPath = new URL(script.src, window.location.href).pathname;
  const contextPath = scriptPath.slice(0, -"/js/csrf.js".length);
  const tokenRequest = window.fetch.bind(window);
  let cachedToken = null;
  let tokenPromise = null;
  const getToken = () => {
    if (cachedToken) return Promise.resolve(cachedToken);
    if (!tokenPromise) {
      tokenPromise = tokenRequest(`${contextPath}/csrf-token`, {
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
        cachedToken = payload.token;
        return cachedToken;
      }).catch(error => {
        tokenPromise = null;
        throw error;
      });
    }
    return tokenPromise;
  };

  // Warm the session token as soon as the page loads so the first action does
  // not have to wait for an extra request. A failed warm-up remains retryable.
  getToken().catch(() => {});

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
  const navigationProgress = document.createElement("div");
  navigationProgress.setAttribute("aria-hidden", "true");
  navigationProgress.style.cssText = "position:fixed;top:0;left:0;width:100%;height:3px;z-index:2147483647;pointer-events:none;background:var(--primary,var(--color-primary,var(--wn-purple,#1D4ED8)));transform:scaleX(0);transform-origin:left;opacity:0;transition:transform 180ms ease,opacity 180ms ease";
  document.documentElement.appendChild(navigationProgress);
  document.addEventListener("click", event => {
    if (event.defaultPrevented || event.button !== 0 || event.metaKey
        || event.ctrlKey || event.shiftKey || event.altKey) return;
    const link = event.target instanceof Element ? event.target.closest("a[href]") : null;
    if (!link || link.target && link.target !== "_self" || link.hasAttribute("download")) return;
    const destination = new URL(link.href, window.location.href);
    if (destination.origin !== window.location.origin
        || (destination.pathname === window.location.pathname
            && destination.search === window.location.search)) return;
    navigationProgress.style.opacity = "1";
    navigationProgress.style.transform = "scaleX(.78)";
  });

  window.fetch = function (resource, init) {
    const options = init || {};
    const requestMethod = resource instanceof Request ? resource.method : "GET";
    const method = String(options.method || requestMethod).toUpperCase();
    if (["GET", "HEAD", "OPTIONS", "TRACE"].includes(method)) {
      return originalFetch(resource, init);
    }

    return getToken().catch(error => {
      showFailure(null);
      throw error;
    }).then(token => {
      const headers = new Headers(resource instanceof Request ? resource.headers : undefined);
      new Headers(options.headers || undefined).forEach((value, key) => headers.set(key, value));
      headers.set("X-CSRF-Token", token);
      return originalFetch(resource, {
        ...options,
        headers,
        credentials: options.credentials || "same-origin"
      });
    });
  };

  const resubmitting = new WeakSet();
  const pendingForms = new WeakSet();
  document.addEventListener("submit", event => {
    const form = event.target;
    if (!(form instanceof HTMLFormElement) || resubmitting.has(form)) return;
    if (pendingForms.has(form)) {
      event.preventDefault();
      return;
    }
    const tokenField = form.querySelector('input[name="_csrf"]');
    if (tokenField && tokenField.value) return;

    event.preventDefault();
    pendingForms.add(form);
    const submitter = event.submitter instanceof HTMLElement ? event.submitter : null;
    const previousDisabled = submitter ? submitter.disabled : false;
    if (submitter) {
      submitter.disabled = true;
      submitter.setAttribute("aria-busy", "true");
    }
    const pendingMessage = document.createElement("p");
    pendingMessage.className = "csrf-pending-message";
    pendingMessage.setAttribute("role", "status");
    pendingMessage.textContent = "Preparing secure submission…";
    if (submitter) submitter.insertAdjacentElement("afterend", pendingMessage);
    else form.prepend(pendingMessage);
    form.setAttribute("aria-busy", "true");

    getToken().then(token => {
      let field = form.querySelector('input[name="_csrf"]');
      if (!field) {
        field = document.createElement("input");
        field.type = "hidden";
        field.name = "_csrf";
        form.appendChild(field);
      }
      field.value = token;
      if (submitter) submitter.disabled = previousDisabled;
      if (submitter) submitter.removeAttribute("aria-busy");
      pendingMessage.remove();
      form.removeAttribute("aria-busy");
      pendingForms.delete(form);
      resubmitting.add(form);
      if (typeof form.requestSubmit === "function") form.requestSubmit(submitter || undefined);
      else form.submit();
      resubmitting.delete(form);
    }).catch(() => {
      if (submitter) {
        submitter.disabled = previousDisabled;
        submitter.removeAttribute("aria-busy");
      }
      pendingMessage.remove();
      form.removeAttribute("aria-busy");
      pendingForms.delete(form);
      showFailure(form);
    });
  }, true);
})();

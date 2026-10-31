(function () {
    "use strict";

    const scriptUrl = document.currentScript && document.currentScript.src;
    const scriptPath = scriptUrl ? new URL(scriptUrl).pathname : "";
    const jsMarker = scriptPath.lastIndexOf("/js/");
    const contextPath = jsMarker >= 0 ? scriptPath.slice(0, jsMarker) : "";
    const actions = document.querySelector(".site-header__actions");
    if (!actions || document.querySelector(".notification-host")) return;

    const host = document.createElement("div");
    host.className = "notification-host";

    const toggle = document.createElement("button");
    toggle.type = "button";
    toggle.className = "notification-toggle";
    toggle.setAttribute("aria-label", "Notifications");
    toggle.setAttribute("aria-expanded", "false");
    toggle.setAttribute("aria-controls", "notification-panel");
    toggle.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9"/><path d="M10 21h4"/></svg>';

    const count = document.createElement("span");
    count.className = "notification-count";
    count.hidden = true;
    count.setAttribute("aria-hidden", "true");
    toggle.appendChild(count);

    const panel = document.createElement("section");
    panel.className = "notification-panel";
    panel.id = "notification-panel";
    panel.setAttribute("aria-labelledby", "notification-panel-title");
    panel.hidden = true;

    const panelHeader = document.createElement("div");
    panelHeader.className = "notification-panel__header";
    const panelTitle = document.createElement("h2");
    panelTitle.className = "notification-panel__title";
    panelTitle.id = "notification-panel-title";
    panelTitle.textContent = "Notifications";
    const markAll = document.createElement("button");
    markAll.type = "button";
    markAll.className = "notification-mark-all";
    markAll.textContent = "Mark all read";
    markAll.hidden = true;
    panelHeader.append(panelTitle, markAll);

    const list = document.createElement("div");
    list.className = "notification-list";
    list.setAttribute("aria-live", "polite");
    list.setAttribute("aria-busy", "false");
    panel.append(panelHeader, list);
    host.append(toggle, panel);
    actions.insertBefore(host, actions.firstChild);

    let lastFocus = null;
    let loading = false;

    function setUnreadCount(value) {
        const unread = Math.max(0, Number(value) || 0);
        count.textContent = unread > 99 ? "99+" : String(unread);
        count.hidden = unread === 0;
        toggle.setAttribute("aria-label", unread ? `Notifications, ${unread} unread` : "Notifications");
        markAll.hidden = unread === 0;
    }

    function showState(message, isError) {
        const state = document.createElement("p");
        state.className = "notification-state" + (isError ? " notification-state--error" : "");
        state.textContent = message;
        list.replaceChildren(state);
    }

    function formatTime(value) {
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return "";
        return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(date);
    }

    function renderItems(items) {
        if (!items.length) {
            showState("You're all caught up. New project activity will appear here.", false);
            return;
        }
        const fragment = document.createDocumentFragment();
        items.forEach(item => {
            const link = document.createElement("a");
            link.className = "notification-link" + (item.read ? "" : " notification-link--unread");
            link.href = item.href || (contextPath + "/");
            const dot = document.createElement("span");
            dot.className = "notification-dot";
            dot.setAttribute("aria-hidden", "true");
            const copy = document.createElement("span");
            copy.className = "notification-copy";
            const title = document.createElement("span");
            title.className = "notification-item-title";
            title.textContent = item.title || "WorkNest update";
            const message = document.createElement("span");
            message.className = "notification-item-message";
            message.textContent = item.message || "There is new activity on your project.";
            copy.append(title, message);
            const timeText = formatTime(item.createdAt);
            if (timeText) {
                const time = document.createElement("time");
                time.className = "notification-item-time";
                time.dateTime = item.createdAt;
                time.textContent = timeText;
                copy.appendChild(time);
            }
            link.appendChild(dot);
            link.appendChild(copy);
            link.addEventListener("click", async event => {
                if (item.read) return;
                event.preventDefault();
                const destination = link.href;
                await markRead(item.id);
                window.location.assign(destination);
            });
            fragment.appendChild(link);
        });
        list.replaceChildren(fragment);
    }

    async function loadNotifications() {
        if (loading) return;
        loading = true;
        try {
            const response = await fetch(contextPath + "/notifications", {
                credentials: "same-origin", cache: "no-store", headers: { Accept: "application/json" }
            });
            let data = {};
            try { data = await response.json(); } catch (_) { /* handled below */ }
            if (response.status === 401) {
                window.location.replace(contextPath + "/login.html");
                return;
            }
            if (!response.ok) throw new Error(data.error || "Notifications are unavailable.");
            setUnreadCount(data.unreadCount);
            renderItems(Array.isArray(data.notifications) ? data.notifications : []);
        } catch (_) {
            const retry = document.createElement("button");
            retry.type = "button";
            retry.className = "notification-state notification-state--error";
            retry.textContent = "Could not load notifications. Try again.";
            retry.addEventListener("click", loadNotifications);
            list.replaceChildren(retry);
        } finally {
            loading = false;
            list.setAttribute("aria-busy", "false");
        }
    }

    async function postAction(payload) {
        const response = await fetch(contextPath + "/notifications", {
            method: "POST", credentials: "same-origin", cache: "no-store",
            headers: { "Content-Type": "application/json", Accept: "application/json" },
            body: JSON.stringify(payload)
        });
        let data = {};
        try { data = await response.json(); } catch (_) { /* handled by status */ }
        if (response.status === 401) {
            window.location.replace(contextPath + "/login.html");
            throw new Error("Your session has expired.");
        }
        if (!response.ok) throw new Error(data.error || "Unable to update notifications.");
    }

    async function markRead(id) {
        try { await postAction({ action: "mark-read", notificationId: id }); }
        catch (_) { /* Navigation should still work if marking read fails. */ }
    }

    toggle.addEventListener("click", async () => {
        const opening = panel.hidden;
        panel.hidden = !opening;
        toggle.setAttribute("aria-expanded", String(opening));
        if (opening) {
            lastFocus = toggle;
            const header = document.querySelector(".site-header");
            if (header) panel.style.setProperty("--notification-panel-top", (header.getBoundingClientRect().bottom + 8) + "px");
            await loadNotifications();
        }
    });

    markAll.addEventListener("click", async () => {
        markAll.disabled = true;
        try {
            await postAction({ action: "mark-all-read" });
            await loadNotifications();
        } catch (_) {
            showState("Could not update notifications. Please try again.", true);
        } finally {
            markAll.disabled = false;
        }
    });

    document.addEventListener("keydown", event => {
        if (event.key === "Escape" && !panel.hidden) {
            panel.hidden = true;
            toggle.setAttribute("aria-expanded", "false");
            if (lastFocus) lastFocus.focus();
        }
    });
    document.addEventListener("click", event => {
        if (!panel.hidden && !host.contains(event.target)) {
            panel.hidden = true;
            toggle.setAttribute("aria-expanded", "false");
        }
    });

    loadNotifications();
    window.setInterval(() => {
        if (document.visibilityState === "visible") loadNotifications();
    }, 60000);
})();

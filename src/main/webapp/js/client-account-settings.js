(function () {
    "use strict";

    const panels = Array.from(document.querySelectorAll("[data-settings-panel]"));
    const tabs = Array.from(document.querySelectorAll("[data-settings-tab]"));
    const profileForm = document.getElementById("profile-form");
    const organizationForm = document.getElementById("organization-form");
    const loadMessage = document.getElementById("profile-load-message");
    const profileError = document.getElementById("profile-error-message");
    const saveMessage = document.getElementById("profile-save-message");
    const organizationLoadMessage = document.getElementById("organization-load-message");
    const organizationErrorMessage = document.getElementById("organization-error-message");
    const organizationSaveMessage = document.getElementById("organization-save-message");
    const deleteForm = document.getElementById("delete-account-form");
    const deleteMessage = document.getElementById("delete-message");
    const deleteButton = document.getElementById("delete-account-button");
    let csrfToken = "";

    function setMessage(element, message, kind) {
        if (!element) return;
        element.textContent = message;
        element.hidden = !message;
        element.classList.remove("settings-message--error", "settings-message--success");
        if (kind === "error") element.classList.add("settings-message--error");
        if (kind === "success") element.classList.add("settings-message--success");
    }

    function activatePanel(name, updateHash) {
        const target = panels.find(panel => panel.dataset.settingsPanel === name);
        if (!target) return;
        panels.forEach(panel => { panel.hidden = panel !== target; });
        tabs.forEach(tab => {
            if (tab.dataset.settingsTab === name) tab.setAttribute("aria-current", "page");
            else tab.removeAttribute("aria-current");
        });
        if (updateHash && window.location.hash !== "#" + name) {
            window.history.replaceState(null, "", "#" + name);
        }
        const heading = target.querySelector("h2");
        if (heading) heading.focus({ preventScroll: true });
    }

    tabs.forEach(tab => tab.addEventListener("click", () => activatePanel(tab.dataset.settingsTab, true)));
    window.addEventListener("hashchange", () => activatePanel(window.location.hash.slice(1) || "profile", false));
    activatePanel(window.location.hash.slice(1) || "profile", false);

    async function readJson(response) {
        if (response.status === 401) {
            window.location.assign("../../login.html");
            throw new Error("Your session has ended. Sign in again to continue.");
        }
        const contentType = response.headers.get("content-type") || "";
        if (!contentType.toLowerCase().includes("application/json")) {
            if (response.status === 404) {
                throw new Error("The profile service is not published to Tomcat yet. Save the project, clean it, and republish WorkNest from Eclipse.");
            }
            throw new Error("The profile service returned an unexpected response. Check the Tomcat console and try again.");
        }
        let data;
        try { data = await response.json(); }
        catch (error) { throw new Error("The profile service returned invalid data. Check the Tomcat console and try again."); }
        if (!response.ok || data.success === false) {
            throw new Error(data.error || "The request could not be completed. Please try again.");
        }
        return data;
    }

    async function loadAccount() {
        setMessage(loadMessage, "Loading your account details…");
        setMessage(profileError, "");
        try {
            const response = await fetch("../../client-account", {
                method: "GET",
                credentials: "same-origin",
                headers: { Accept: "application/json" },
                cache: "no-store"
            });
            const account = await readJson(response);
            document.getElementById("full-name").value = account.fullName || "";
            document.getElementById("email-address").value = account.email || "";
            document.getElementById("account-created").textContent = formatDate(account.createdAt);
            document.getElementById("account-project-count").textContent = String(account.projectCount ?? 0);
            document.getElementById("account-active-count").textContent = String(account.activeProjectCount ?? 0);
            populateOrganization(account.organization || {});
            csrfToken = account.csrfToken || "";
            document.getElementById("delete-csrf-token").value = csrfToken;
            profileForm.hidden = false;
            organizationForm.hidden = !account.profileStorageReady;
            setMessage(loadMessage, "");
            setMessage(organizationLoadMessage, account.profileSetupError || "", account.profileStorageReady ? "" : "error");
        } catch (error) {
            setMessage(loadMessage, "");
            setMessage(profileError, error.message || "Account details could not be loaded.", "error");
            setMessage(organizationErrorMessage, error.message || "Organization details could not be loaded.", "error");
        }
    }

    function populateOrganization(organization) {
        document.getElementById("organization-name").value = organization.organizationName || "";
        document.getElementById("organization-type").value = organization.organizationType || "";
        document.getElementById("organization-industry").value = organization.industry || "";
        document.getElementById("organization-website").value = organization.website || "";
        document.getElementById("organization-country").value = organization.country || "";
        document.getElementById("organization-city").value = organization.city || "";
        document.getElementById("organization-about").value = organization.businessDescription || "";

    }

    function formatDate(value) {
        if (!value) return "Not available";
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return "Not available";
        return new Intl.DateTimeFormat(undefined, { year: "numeric", month: "long", day: "numeric" }).format(date);
    }

    profileForm.addEventListener("submit", async event => {
        event.preventDefault();
        setMessage(saveMessage, "Saving your changes…");
        const submit = profileForm.querySelector("button[type='submit']");
        submit.disabled = true;
        try {
            const body = new URLSearchParams({ fullName: document.getElementById("full-name").value, csrfToken });
            const response = await fetch("../../client-account", {
                method: "POST",
                credentials: "same-origin",
                headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8", Accept: "application/json" },
                body
            });
            const result = await readJson(response);
            document.getElementById("full-name").value = result.fullName || "";
            setMessage(saveMessage, result.message || "Profile details saved.", "success");
        } catch (error) {
            setMessage(saveMessage, error.message || "Your profile could not be saved.", "error");
        } finally {
            submit.disabled = false;
        }
    });

    organizationForm.addEventListener("submit", async event => {
        event.preventDefault();
        const submit = organizationForm.querySelector("button[type='submit']");
        submit.disabled = true;
        setMessage(organizationSaveMessage, "Saving organization details…");
        try {
            const body = new URLSearchParams(new FormData(organizationForm));
            body.set("action", "saveOrganization");
            body.set("csrfToken", csrfToken);
            const response = await fetch("../../client-account", {
                method: "POST",
                credentials: "same-origin",
                headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8", Accept: "application/json" },
                body
            });
            const result = await readJson(response);
            populateOrganization(result.organization || {});
            setMessage(organizationSaveMessage, result.message || "Organization details saved.", "success");
        } catch (error) {
            setMessage(organizationSaveMessage, error.message || "Organization details could not be saved.", "error");
        } finally {
            submit.disabled = false;
        }
    });

    deleteForm.addEventListener("submit", async event => {
        event.preventDefault();
        setMessage(deleteMessage, "Checking your account…");
        deleteButton.disabled = true;
        try {
            const body = new URLSearchParams({
                currentPassword: document.getElementById("current-password").value,
                confirmation: document.getElementById("delete-confirmation").value,
                csrfToken
            });
            const response = await fetch("../../delete-client-account", {
                method: "POST",
                credentials: "same-origin",
                headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8", Accept: "application/json" },
                body
            });
            const result = await readJson(response);
            if (result.redirect) window.location.assign(result.redirect);
            else setMessage(deleteMessage, result.message || "Account deleted.", "success");
        } catch (error) {
            setMessage(deleteMessage, error.message || "Your account could not be deleted.", "error");
            deleteButton.disabled = false;
        }
    });

    loadAccount();
}());

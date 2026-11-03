(function () {
    "use strict";

    const script = document.currentScript;
    const scriptPath = script && new URL(script.src, window.location.href).pathname;
    const marker = "/js/worknest-footer.js";
    const markerIndex = scriptPath ? scriptPath.lastIndexOf(marker) : -1;
    const appRoot = markerIndex >= 0 ? scriptPath.slice(0, markerIndex) : "";
    const path = window.location.pathname;
    const isClient = path.includes("/dashboard/client/");
    const isFreelancer = path.includes("/dashboard/freelancer/");
    const isDashboard = isClient || isFreelancer;

    if (!document.getElementById("worknest-footer-styles")) {
        const stylesheet = document.createElement("link");
        stylesheet.id = "worknest-footer-styles";
        stylesheet.rel = "stylesheet";
        stylesheet.href = appRoot + "/css/worknest-footer.css?v=20261103-footer";
        document.head.appendChild(stylesheet);
    }

    function addLink(parent, label, href) {
        const link = document.createElement("a");
        link.textContent = label;
        link.href = href;
        parent.appendChild(link);
        return link;
    }

    function element(tag, className, text) {
        const node = document.createElement(tag);
        if (className) node.className = className;
        if (text) node.textContent = text;
        return node;
    }

    function group(label, heading) {
        const nav = element("nav", "worknest-footer__group");
        nav.setAttribute("aria-label", label);
        nav.appendChild(element("h2", "", heading));
        return nav;
    }

    document.querySelectorAll("footer").forEach(footer => footer.remove());

    const legalLinks = [
        ["Help and support", appRoot + "/legal/help.html"],
        ["Terms and conditions", appRoot + "/legal/terms.html"],
        ["Privacy policy", appRoot + "/legal/privacy.html"]
    ];

    /* ---------- Client and freelancer dashboards ---------- */
    if (isDashboard) {
        const config = isClient
            ? {
                badge: "Client workspace",
                tagline: "Post projects, review proposals and keep work moving with the right freelancer.",
                links: [
                    ["Overview", "index.html"],
                    ["Your projects", "index.html#your-projects"],
                    ["Profile and settings", "profile-settings.html"]
                ],
                actionText: "Ready for the next job? Describe what you need and set your budget.",
                actionLabel: "Post a project",
                actionHref: "create-project.html"
            }
            : {
                badge: "Freelancer workspace",
                tagline: "Find projects, send proposals and deliver great work for your clients.",
                links: [
                    ["Overview", "index.html"],
                    ["My projects", "my-projects.html"],
                    ["My proposals", "my-proposals.html"],
                    ["My profile", "profile.html"]
                ],
                actionText: "Looking for your next project? Browse open work and send a proposal.",
                actionLabel: "Find work",
                actionHref: "find-work.html"
            };

        const footer = element("footer", "worknest-footer worknest-footer--dashboard");
        const inner = element("div", "worknest-footer__inner");

        const brand = element("div", "worknest-footer__brand");
        const wordmark = element("a", "worknest-footer__wordmark");
        wordmark.href = "index.html";
        wordmark.setAttribute("aria-label", "WorkNest dashboard home");
        wordmark.append("worknest", element("span", "worknest-footer__period", "."));
        brand.append(
            wordmark,
            element("p", "", config.tagline),
            element("span", "worknest-footer__badge", config.badge)
        );

        const workspace = group("Workspace links", "Workspace");
        config.links.forEach(([label, href]) => addLink(workspace, label, href));

        const action = element("div", "worknest-footer__group worknest-footer__action");
        action.appendChild(element("h2", "", "Quick action"));
        action.appendChild(element("p", "", config.actionText));
        const cta = addLink(action, config.actionLabel, config.actionHref);
        cta.className = "worknest-footer__cta";
        cta.append(element("span", "", "→"));
        cta.lastChild.setAttribute("aria-hidden", "true");

        inner.append(brand, workspace, action);

        const bottom = element("div", "worknest-footer__bottom");
        const bottomInner = element("div", "worknest-footer__bottom-inner");
        bottomInner.appendChild(element(
            "p", "", "© " + new Date().getFullYear() + " WorkNest. Built for better collaboration."
        ));
        const legal = element("nav", "worknest-footer__legal");
        legal.setAttribute("aria-label", "Help and legal links");
        legalLinks.forEach(([label, href]) => addLink(legal, label, href));
        bottomInner.appendChild(legal);
        bottom.appendChild(bottomInner);

        footer.append(inner, bottom);
        document.body.appendChild(footer);

        /* Back to top */
        const top = element("button", "worknest-back-to-top");
        top.type = "button";
        top.setAttribute("aria-label", "Back to top");
        top.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 19V5"></path><path d="m5 12 7-7 7 7"></path></svg>';
        top.addEventListener("click", () => {
            const reduce = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
            window.scrollTo({ top: 0, behavior: reduce ? "auto" : "smooth" });
        });
        document.body.appendChild(top);

        let ticking = false;
        function updateTop() {
            ticking = false;
            top.classList.toggle("is-visible", window.scrollY > 480);
        }
        window.addEventListener("scroll", () => {
            if (!ticking) {
                ticking = true;
                window.requestAnimationFrame(updateTop);
            }
        }, { passive: true });
        updateTop();
        return;
    }

    /* ---------- Public and legal pages (unchanged) ---------- */
    const footer = document.createElement("footer");
    footer.className = "worknest-footer";
    const inner = document.createElement("div");
    inner.className = "worknest-footer__inner";

    const brand = document.createElement("div");
    brand.className = "worknest-footer__brand";
    const wordmark = document.createElement("a");
    wordmark.className = "worknest-footer__wordmark";
    wordmark.href = appRoot + "/login.html";
    wordmark.textContent = "WorkNest";
    const tagline = document.createElement("p");
    tagline.textContent = "Project collaboration for clients and freelancers.";
    brand.append(wordmark, tagline);

    const workspace = group("Workspace links", "Workspace");
    [
        ["Client dashboard", appRoot + "/dashboard/client/index.html"],
        ["Freelancer dashboard", appRoot + "/dashboard/freelancer/index.html"]
    ].forEach(([label, href]) => addLink(workspace, label, href));

    const legal = group("Help and legal links", "Help and legal");
    legalLinks.forEach(([label, href]) => addLink(legal, label, href));

    inner.append(brand, workspace, legal);
    footer.appendChild(inner);
    const bottom = document.createElement("div");
    bottom.className = "worknest-footer__bottom";
    const copyright = document.createElement("p");
    copyright.textContent = "© WorkNest. Built for better collaboration.";
    bottom.appendChild(copyright);
    footer.appendChild(bottom);
    document.body.appendChild(footer);
}());

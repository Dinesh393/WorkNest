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

    if (!document.getElementById("worknest-footer-styles")) {
        const stylesheet = document.createElement("link");
        stylesheet.id = "worknest-footer-styles";
        stylesheet.rel = "stylesheet";
        stylesheet.href = appRoot + "/css/worknest-footer.css";
        document.head.appendChild(stylesheet);
    }

    const workspaceLinks = isClient
        ? [
            ["Overview", "index.html"],
            ["Your projects", "index.html#your-projects"],
            ["Post a project", "create-project.html"]
        ]
        : isFreelancer
            ? [
                ["Overview", "index.html"],
                ["Find work", "find-work.html"],
                ["My projects", "my-projects.html"],
                ["My proposals", "my-proposals.html"],
                ["My profile", "profile.html"]
            ]
            : [
                ["Client dashboard", appRoot + "/dashboard/client/index.html"],
                ["Freelancer dashboard", appRoot + "/dashboard/freelancer/index.html"]
            ];

    function addLink(parent, label, href) {
        const link = document.createElement("a");
        link.textContent = label;
        link.href = href;
        parent.appendChild(link);
    }

    document.querySelectorAll("footer").forEach(footer => footer.remove());

    const footer = document.createElement("footer");
    footer.className = "worknest-footer";
    const inner = document.createElement("div");
    inner.className = "worknest-footer__inner";

    const brand = document.createElement("div");
    brand.className = "worknest-footer__brand";
    const wordmark = document.createElement("a");
    wordmark.className = "worknest-footer__wordmark";
    wordmark.href = isClient || isFreelancer ? "index.html" : appRoot + "/login.html";
    wordmark.textContent = "WorkNest";
    const tagline = document.createElement("p");
    tagline.textContent = "Project collaboration for clients and freelancers.";
    brand.append(wordmark, tagline);

    const workspace = document.createElement("nav");
    workspace.className = "worknest-footer__group";
    workspace.setAttribute("aria-label", "Workspace links");
    const workspaceHeading = document.createElement("h2");
    workspaceHeading.textContent = "Workspace";
    workspace.appendChild(workspaceHeading);
    workspaceLinks.forEach(([label, href]) => addLink(workspace, label, href));

    const legal = document.createElement("nav");
    legal.className = "worknest-footer__group";
    legal.setAttribute("aria-label", "Help and legal links");
    const legalHeading = document.createElement("h2");
    legalHeading.textContent = "Help and legal";
    legal.appendChild(legalHeading);
    addLink(legal, "Help and support", appRoot + "/legal/help.html");
    addLink(legal, "Terms and conditions", appRoot + "/legal/terms.html");
    addLink(legal, "Privacy policy", appRoot + "/legal/privacy.html");

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

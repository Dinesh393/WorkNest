(function () {
    "use strict";

    const fields = [
        ["Organization type", "organizationType"],
        ["Industry", "industry"],
        ["Location", "location"],
        ["Website", "website"],
        ["About", "businessDescription"]
    ];

    function text(value) {
        return value === null || value === undefined ? "" : String(value).trim();
    }

    function getLocation(organization) {
        return [text(organization.city), text(organization.country)].filter(Boolean).join(", ");
    }

    function searchValue(organization) {
        return text(organization && organization.organizationName).toLocaleLowerCase();
    }

    function websiteLink(value) {
        const raw = text(value);
        if (!raw) return null;
        try {
            const url = new URL(raw);
            if (url.protocol !== "https:" && url.protocol !== "http:") return null;
            const link = document.createElement("a");
            link.href = url.href;
            link.target = "_blank";
            link.rel = "noopener noreferrer";
            link.textContent = raw;
            return link;
        } catch (_) {
            return null;
        }
    }

    function renderInto(container, organization, options) {
        if (!container) return;
        const data = organization && typeof organization === "object" ? organization : {};
        const settings = options || {};
        const panel = document.createElement(settings.compact ? "div" : "section");
        panel.className = "client-organization" + (settings.compact ? " client-organization--compact" : "");
        if (!settings.compact && settings.labelId) panel.setAttribute("aria-labelledby", settings.labelId);

        const heading = document.createElement(settings.compact ? "h3" : "h2");
        heading.className = "client-organization__heading";
        heading.textContent = text(data.organizationName) || "Client organization";
        panel.appendChild(heading);

        const contactName = text(settings.clientName);
        if (contactName) {
            const contact = document.createElement("p");
            contact.className = "client-organization__contact";
            contact.textContent = "Client contact: " + contactName;
            panel.appendChild(contact);
        }

        const values = {
            organizationType: text(data.organizationType),
            industry: text(data.industry),
            location: getLocation(data),
            website: text(data.website),
            businessDescription: text(data.businessDescription)
        };
        const list = document.createElement("dl");
        list.className = "client-organization__grid";
        let count = 0;
        fields.forEach(([label, key]) => {
            const value = values[key];
            if (!value) return;
            const item = document.createElement("div");
            item.className = "client-organization__item";
            const term = document.createElement("dt");
            term.textContent = label;
            const detail = document.createElement("dd");
            if (key === "website") {
                const link = websiteLink(value);
                if (link) detail.appendChild(link);
                else detail.textContent = value;
            } else {
                detail.textContent = value;
            }
            item.append(term, detail);
            list.appendChild(item);
            count += 1;
        });
        if (count) {
            panel.appendChild(list);
        } else {
            const empty = document.createElement("p");
            empty.className = "client-organization__empty";
            empty.textContent = "This client has not shared organization details yet.";
            panel.appendChild(empty);
        }
        container.replaceChildren(panel);
    }

    window.WorkNestOrganizations = { renderInto, searchValue };
}());

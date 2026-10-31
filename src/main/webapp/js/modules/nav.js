
export function initNavigation() {
    const toggleButton = document.querySelector("[data-nav-toggle]");
    const navigationMenu = document.querySelector("[data-nav-menu]");

    if (!toggleButton || !navigationMenu) {
        return;
    }

    const mobileQuery = window.matchMedia("(max-width: 47.999rem)");

    function setMenuOpen(isOpen, returnFocus = false) {
        navigationMenu.classList.toggle("is-open", isOpen);
        toggleButton.setAttribute("aria-expanded", String(isOpen));
        toggleButton.setAttribute(
            "aria-label",
            isOpen ? "Close navigation" : "Open navigation"
        );

        if (isOpen && mobileQuery.matches) {
            const firstLink = navigationMenu.querySelector("a");

            if (firstLink) {
                firstLink.focus();
            }
        }

        if (!isOpen && returnFocus) {
            toggleButton.focus();
        }
    }

    toggleButton.addEventListener("click", () => {
        const isExpanded =
            toggleButton.getAttribute("aria-expanded") === "true";

        setMenuOpen(!isExpanded);
    });

    navigationMenu.addEventListener("click", (event) => {
        const clickedLink = event.target.closest("a");

        if (clickedLink && mobileQuery.matches) {
            setMenuOpen(false);
        }
    });

    document.addEventListener("keydown", (event) => {
        if (
            event.key === "Escape" &&
            toggleButton.getAttribute("aria-expanded") === "true"
        ) {
            setMenuOpen(false, true);
        }
    });

    document.addEventListener("click", (event) => {
        if (
            mobileQuery.matches &&
            toggleButton.getAttribute("aria-expanded") === "true" &&
            !navigationMenu.contains(event.target) &&
            !toggleButton.contains(event.target)
        ) {
            setMenuOpen(false);
        }
    });

    mobileQuery.addEventListener("change", () => {
        setMenuOpen(false);
    });
}


import { initNavigation } from "./modules/nav.js";

initNavigation();

const yearElement = document.querySelector("[data-current-year]");

if (yearElement) {
    yearElement.textContent = String(new Date().getFullYear());
}

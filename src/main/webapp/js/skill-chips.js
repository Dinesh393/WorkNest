/* WorkNest skill chips
   - Type a skill with spaces, then press Enter (or comma/semicolon) to make a chip.
   - The original input stays in the form (type="hidden") and always holds
     "Java, MySQL, HTML", so the servlet receives the same requiredSkills value.
   - Backspace on an empty box removes the last chip. Enter never submits the form.
*/
(function () {
    "use strict";

    function initSkillChips(hiddenInput, options) {
        var opts = Object.assign({ max: 500, maxEach: 60, onChange: null }, options || {});
        var skills = [];

        var container = document.createElement("div");
        container.className = "skill-chips";

        var entry = document.createElement("input");
        entry.type = "text";
        entry.id = hiddenInput.id + "-entry";
        entry.className = "skill-chips__entry";
        entry.autocomplete = "off";
        entry.maxLength = opts.maxEach;
        entry.placeholder = hiddenInput.getAttribute("data-placeholder") || "Type a skill, then press Enter";
        entry.setAttribute("aria-describedby", hiddenInput.getAttribute("aria-describedby") || "");

        var label = document.querySelector('label[for="' + hiddenInput.id + '"]');
        if (label) label.setAttribute("for", entry.id);

        hiddenInput.type = "hidden";
        hiddenInput.parentNode.insertBefore(container, hiddenInput);
        container.appendChild(entry);
        container.setAttribute("role", "group");

        function joined(list) { return list.join(", "); }

        function sync() {
            hiddenInput.value = joined(skills);
            container.classList.remove("is-invalid");
            if (typeof opts.onChange === "function") opts.onChange(skills.slice());
        }

        function render() {
            Array.prototype.slice.call(container.querySelectorAll(".skill-chip")).forEach(function (c) { c.remove(); });
            skills.forEach(function (skill, index) {
                var chip = document.createElement("span");
                chip.className = "skill-chip";
                chip.appendChild(document.createTextNode(skill));

                var remove = document.createElement("button");
                remove.type = "button";
                remove.setAttribute("aria-label", "Remove " + skill);
                remove.textContent = "\u00D7";
                remove.addEventListener("click", function () {
                    skills.splice(index, 1);
                    render();
                    sync();
                    entry.focus();
                });
                chip.appendChild(remove);
                container.insertBefore(chip, entry);
            });
        }

        function add(raw) {
            var skill = String(raw || "").replace(/\s+/g, " ").trim();
            if (!skill) return false;
            if (skill.length > opts.maxEach) return false;
            var exists = skills.some(function (s) { return s.toLowerCase() === skill.toLowerCase(); });
            if (exists) return false;
            if (joined(skills.concat(skill)).length > opts.max) return false;
            skills.push(skill);
            return true;
        }

        function commit() {
            var changed = add(entry.value);
            entry.value = "";
            if (changed) { render(); sync(); }
        }

        entry.addEventListener("input", function () {
            if (!/[,;]/.test(entry.value)) return;
            var parts = entry.value.split(/[,;]/);
            var rest = parts.pop();
            var changed = false;
            parts.forEach(function (p) { if (add(p)) changed = true; });
            entry.value = rest;
            if (changed) { render(); sync(); }
        });

        entry.addEventListener("keydown", function (event) {
            if (event.key === "Enter") {
                event.preventDefault();
                commit();
            } else if (event.key === "Backspace" && entry.value === "" && skills.length) {
                skills.pop();
                render();
                sync();
            }
        });

        entry.addEventListener("blur", commit);
        container.addEventListener("click", function (event) {
            if (event.target === container) entry.focus();
        });

        if (hiddenInput.form) {
            hiddenInput.form.addEventListener("submit", commit, true);
        }

        function set(value) {
            skills = [];
            String(value || "").split(/[,;]+/).forEach(add);
            entry.value = "";
            render();
            sync();
        }

        var controller = {
            container: container,
            entry: entry,
            set: set,
            get: function () { return skills.slice(); },
            commit: commit,
            focus: function () { entry.focus(); }
        };

        hiddenInput.skillChips = controller;
        if (hiddenInput.value) set(hiddenInput.value);
        return controller;
    }

    window.initSkillChips = initSkillChips;
})();

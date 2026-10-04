class RatingsPage {
    constructor() {
        this.data = null;
        this.teamFilter = document.getElementById("team-filter");
        this.positionFilter = document.getElementById("position-filter");
        this.searchInput = document.getElementById("search");
        this.minToi = document.getElementById("min-toi");
        this.rowsBody = document.getElementById("rating-rows");
        this.statsBox = document.getElementById("stats");
        this.errorBox = document.getElementById("error");
        this.statsBox.innerHTML = `<article class="stat"><div class="label">Считаю RAPM…</div></article>`;
        this.teamFilter.addEventListener("change", () => this.renderTable());
        this.positionFilter.addEventListener("change", () => this.renderTable());
        this.searchInput.addEventListener("input", () => this.renderTable());
        this.minToi.addEventListener("change", () => this.renderTable());
    }

    async load() {
        try {
            const response = await fetch("/api/ratings");
            if (!response.ok) {
                throw new Error("Сервер вернул " + response.status);
            }
            this.data = await response.json();
            this.renderStats();
            this.renderTable();
        } catch (error) {
            this.errorBox.hidden = false;
            this.errorBox.classList.remove("hidden");
            this.errorBox.textContent = "Не удалось загрузить рейтинг: " + error.message;
        }
    }

    renderStats() {
        const cards = [
            { value: this.data.nWithMinToi, label: "с ТОИ ≥ " + this.data.minToiMinutes + " мин" },
            { value: this.data.nAvangard, label: "из них играли за T113" },
            { value: this.format(this.data.medianRating, 3), label: "медиана рейтинга" },
            { value: this.data.bootstrapDraws, label: "реплик bootstrap" },
        ];
        this.statsBox.innerHTML = cards.map((card) => {
            return `<article class="stat"><div class="value">${card.value}</div>` +
                `<div class="label">${card.label}</div></article>`;
        }).join("");
    }

    visibleRows() {
        const query = this.searchInput.value.trim().toLowerCase();
        const team = this.teamFilter.value;
        const position = this.positionFilter.value;
        const onlyMin = this.minToi.checked;
        const forwards = new Set(["C", "LW", "RW"]);
        return this.data.players.filter((row) => {
            const matchesQuery = row.playerId.toLowerCase().includes(query) ||
                (row.primaryTeamId || "").toLowerCase().includes(query);
            const matchesTeam = team === "all" ||
                (team === "avangard" && row.playedForAvangard) ||
                (team === "opponents" && !row.playedForAvangard);
            const matchesPosition = position === "all" ||
                (position === "D" && row.position === "D") ||
                (position === "F" && forwards.has(row.position));
            const matchesMin = !onlyMin || row.meetsMinToi;
            return matchesQuery && matchesTeam && matchesPosition && matchesMin;
        });
    }

    renderTable() {
        const rows = this.visibleRows();
        const span = this.scale(rows);
        this.rowsBody.innerHTML = rows.map((row) => {
            const avangard = row.playedForAvangard ? " · T113" : "";
            return `<tr>` +
                `<td>${row.playerId}</td>` +
                `<td>${row.position}</td>` +
                `<td>${row.primaryTeamId || "—"}${avangard}</td>` +
                `<td class="num">${row.nGames}</td>` +
                `<td class="num">${this.format(row.toiMin, 1)}</td>` +
                `<td class="num">${this.format(row.rawPer60, 2)}</td>` +
                `<td class="num">${this.format(row.evPer60, 2)}</td>` +
                `<td class="num">${this.format(row.rapm, 2)}</td>` +
                `<td class="num">${this.format(row.rating, 3)}</td>` +
                `<td class="num">${this.format(row.lo, 3)}</td>` +
                `<td class="num">${this.format(row.hi, 3)}</td>` +
                `<td>${this.intervalHtml(row, span)}</td>` +
                `</tr>`;
        }).join("");
    }

    scale(rows) {
        const values = rows.flatMap((row) => [row.lo, row.hi, row.rating]);
        const min = Math.min(...values, -0.1);
        const max = Math.max(...values, 0.1);
        return { min, max };
    }

    intervalHtml(row, span) {
        const width = Math.max(1e-6, span.max - span.min);
        const left = ((row.lo - span.min) / width) * 100;
        const right = ((row.hi - span.min) / width) * 100;
        const mark = ((row.rating - span.min) / width) * 100;
        return `<div class="interval">` +
            `<span class="interval-bar" style="left:${left}%;width:${Math.max(2, right - left)}%"></span>` +
            `<span class="interval-mark" style="left:${mark}%"></span>` +
            `</div>`;
    }

    format(value, digits) {
        return Number(value).toLocaleString("ru-RU", {
            minimumFractionDigits: digits,
            maximumFractionDigits: digits,
        });
    }
}

document.addEventListener("DOMContentLoaded", () => {
    new RatingsPage().load();
});

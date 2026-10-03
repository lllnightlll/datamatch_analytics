class ToiPage {
    constructor() {
        this.data = null;
        this.splitFilter = document.getElementById("split-filter");
        this.teamFilter = document.getElementById("team-filter");
        this.positionFilter = document.getElementById("position-filter");
        this.searchInput = document.getElementById("search");
        this.minToi = document.getElementById("min-toi");
        this.rowsBody = document.getElementById("toi-rows");
        this.statsBox = document.getElementById("stats");
        this.errorBox = document.getElementById("error");
        this.downloadJson = document.getElementById("download-json");
        this.downloadCsv = document.getElementById("download-csv");
        this.statsBox.innerHTML = `<article class="stat"><div class="label">Считаю смены…</div></article>`;
        this.splitFilter.addEventListener("change", () => this.load());
        this.teamFilter.addEventListener("change", () => this.renderTable());
        this.positionFilter.addEventListener("change", () => this.renderTable());
        this.searchInput.addEventListener("input", () => this.renderTable());
        this.minToi.addEventListener("change", () => this.renderTable());
    }

    async load() {
        const split = this.splitFilter.value;
        this.downloadJson.href = "/api/toi?split=" + split;
        this.downloadCsv.href = "/api/toi.csv?split=" + split;
        try {
            const response = await fetch("/api/toi?split=" + split);
            if (!response.ok) {
                throw new Error("Сервер вернул " + response.status);
            }
            this.data = await response.json();
            this.renderStats();
            this.renderTable();
        } catch (error) {
            this.showError("Не удалось загрузить ТОИ: " + error.message);
        }
    }

    showError(message) {
        this.errorBox.hidden = false;
        this.errorBox.classList.remove("hidden");
        this.errorBox.textContent = message;
    }

    renderStats() {
        const { summary, minToiMinutes } = this.data;
        const cards = [
            { value: summary.nSkaters, label: "полевых с ТОИ > 0" },
            { value: summary.nWithMinToi, label: "с ТОИ ≥ " + minToiMinutes + " мин" },
            { value: summary.nAvangardSkaters, label: "играли за T113" },
            { value: this.formatMinutes(summary.medianToiMinutes), label: "медиана ТОИ, мин" },
        ];
        this.statsBox.innerHTML = cards.map((card) => {
            return `<article class="stat"><div class="value">${card.value}</div>` +
                `<div class="label">${card.label}</div></article>`;
        }).join("");
    }

    renderTable() {
        const query = this.searchInput.value.trim().toLowerCase();
        const team = this.teamFilter.value;
        const position = this.positionFilter.value;
        const onlyMin = this.minToi.checked;
        const forwards = new Set(["C", "LW", "RW"]);
        const rows = this.data.players.filter((row) => {
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
        this.rowsBody.innerHTML = rows.map((row) => {
            const threshold = row.meetsMinToi
                ? `<span class="pill shared">достаточный</span>`
                : `<span class="pill train_only">мало льда</span>`;
            const avangard = row.playedForAvangard ? " · T113" : "";
            return `<tr>` +
                `<td>${row.playerId}</td>` +
                `<td>${row.position}</td>` +
                `<td>${row.primaryTeamId || "—"}${avangard}</td>` +
                `<td class="num">${row.nGames}</td>` +
                `<td class="num">${this.formatMinutes(row.toiMin)}</td>` +
                `<td class="num">${this.formatMinutes(row.toi5v5Min)}</td>` +
                `<td class="num">${this.formatMinutes(row.toiSpecialMin)}</td>` +
                `<td>${threshold}</td>` +
                `</tr>`;
        }).join("");
    }

    formatMinutes(value) {
        return Number(value).toLocaleString("ru-RU", {
            minimumFractionDigits: 1,
            maximumFractionDigits: 1,
        });
    }
}

document.addEventListener("DOMContentLoaded", () => {
    new ToiPage().load();
});

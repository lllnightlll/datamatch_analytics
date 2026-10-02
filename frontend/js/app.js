/**
 * Экран инвентаря: загрузка /api/inventory и отрисовка таблиц.
 * На шаге 1 нет загрузки пользовательских файлов — данные читает Kotlin из data/.
 */
class InventoryPage {
    constructor() {
        this.data = null;
        this.searchInput = document.getElementById("search");
        this.statusFilter = document.getElementById("status-filter");
        this.rowsBody = document.getElementById("event-rows");
        this.statsBox = document.getElementById("stats");
        this.familiesBox = document.getElementById("families");
        this.errorBox = document.getElementById("error");
        this.statsBox.innerHTML = `<article class="stat"><div class="label">Загрузка инвентаря…</div></article>`;
        this.searchInput.addEventListener("input", () => this.renderTable());
        this.statusFilter.addEventListener("change", () => this.renderTable());
    }

    async load() {
        try {
            const response = await fetch("/api/inventory");
            if (!response.ok) {
                throw new Error("Сервер вернул " + response.status);
            }
            this.data = await response.json();
            this.renderStats();
            this.renderFamilies();
            this.renderTable();
        } catch (error) {
            this.showError("Не удалось загрузить инвентарь: " + error.message);
        }
    }

    showError(message) {
        this.errorBox.hidden = false;
        this.errorBox.classList.remove("hidden");
        this.errorBox.textContent = message;
    }

    renderStats() {
        const { train, test, nSharedEventTypes, nTrainOnlyEventTypes } = this.data;
        const cards = [
            { value: train.nGames, label: "матчей train" },
            { value: this.formatNumber(train.nEvents), label: "событий train" },
            { value: test.nGames, label: "матчей теста (T113)" },
            { value: test.nEventSkaters, label: "полевых actor_id в тесте" },
            { value: nSharedEventTypes, label: "типов в train ∩ test" },
            { value: nTrainOnlyEventTypes, label: "типов только в train" },
            { value: train.nRosterSkaters, label: "полевых в players.csv" },
            { value: test.nEventGoalies, label: "вратарей-actor в тесте (вне рейтинга)" },
        ];
        this.statsBox.innerHTML = cards.map((card) => {
            return `<article class="stat"><div class="value">${card.value}</div>` +
                `<div class="label">${card.label}</div></article>`;
        }).join("");
    }

    renderFamilies() {
        const maxTrain = Math.max(...this.data.families.map((row) => row.trainCount), 1);
        this.familiesBox.innerHTML = this.data.families.map((row) => {
            const width = Math.max(4, Math.round((row.trainCount / maxTrain) * 100));
            return `<div class="family-row">` +
                `<span>${row.family}</span>` +
                `<div class="bar"><span style="width:${width}%"></span></div>` +
                `<span class="num">${this.formatNumber(row.trainCount)}</span>` +
                `</div>`;
        }).join("");
    }

    renderTable() {
        const query = this.searchInput.value.trim().toLowerCase();
        const status = this.statusFilter.value;
        const statusLabel = {
            shared: "в обоих",
            train_only: "только train",
            test_only: "только test",
        };
        const rows = this.data.eventTypes.filter((row) => {
            const matchesQuery = row.eventType.toLowerCase().includes(query) ||
                row.family.toLowerCase().includes(query);
            const matchesStatus = status === "all" || row.status === status;
            return matchesQuery && matchesStatus;
        });
        this.rowsBody.innerHTML = rows.map((row) => {
            return `<tr>` +
                `<td>${row.eventType}</td>` +
                `<td>${row.family}</td>` +
                `<td><span class="pill ${row.status}">${statusLabel[row.status]}</span></td>` +
                `<td class="num">${this.formatNumber(row.trainCount)}</td>` +
                `<td class="num">${this.formatNumber(row.testCount)}</td>` +
                `</tr>`;
        }).join("");
    }

    formatNumber(value) {
        return Number(value).toLocaleString("ru-RU");
    }
}

document.addEventListener("DOMContentLoaded", () => {
    new InventoryPage().load();
});

class DeliverPage {
    constructor() {
        this.statsBox = document.getElementById("stats");
        this.rowsBody = document.getElementById("rows");
        this.pathHint = document.getElementById("path-hint");
        this.errorBox = document.getElementById("error");
        this.statsBox.innerHTML = `<article class="stat"><div class="label">Собираю CSV…</div></article>`;
    }

    async load() {
        try {
            const response = await fetch("/api/test-ratings");
            if (!response.ok) {
                throw new Error("Сервер вернул " + response.status);
            }
            this.data = await response.json();
            this.render();
        } catch (error) {
            this.errorBox.hidden = false;
            this.errorBox.classList.remove("hidden");
            this.errorBox.textContent = "Не удалось загрузить сдачу: " + error.message;
        }
    }

    render() {
        this.pathHint.textContent = "Файл на диске: " + this.data.path;
        const cards = [
            { value: this.data.nRows, label: "строк в test_ratings.csv" },
            { value: this.data.nAvangard, label: "играли за T113" },
            { value: this.data.minToiMinutes, label: "порог ТОИ, мин" },
            { value: this.data.columns.join(", "), label: "колонки" },
        ];
        this.statsBox.innerHTML = cards.map((card) => {
            return `<article class="stat"><div class="value">${card.value}</div>` +
                `<div class="label">${card.label}</div></article>`;
        }).join("");
        this.rowsBody.innerHTML = this.data.rows.map((row) => {
            return `<tr>` +
                `<td>${row.playerId}</td>` +
                `<td class="num">${this.format(row.rating)}</td>` +
                `<td class="num">${this.format(row.lo)}</td>` +
                `<td class="num">${this.format(row.hi)}</td>` +
                `</tr>`;
        }).join("");
    }

    format(value) {
        return Number(value).toLocaleString("ru-RU", {
            minimumFractionDigits: 4,
            maximumFractionDigits: 4,
        });
    }
}

document.addEventListener("DOMContentLoaded", () => {
    new DeliverPage().load();
});

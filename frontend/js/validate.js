class ValidatePage {
    constructor() {
        this.statsBox = document.getElementById("stats");
        this.barsBox = document.getElementById("bars");
        this.corrBody = document.getElementById("corr-rows");
        this.playerBody = document.getElementById("player-rows");
        this.errorBox = document.getElementById("error");
        this.statsBox.innerHTML = `<article class="stat"><div class="label">Делю матчи…</div></article>`;
    }

    async load() {
        try {
            const response = await fetch("/api/validate");
            if (!response.ok) {
                throw new Error("Сервер вернул " + response.status);
            }
            this.data = await response.json();
            this.renderStats();
            this.renderBars();
            this.renderCorrelations();
            this.renderPlayers();
        } catch (error) {
            this.errorBox.hidden = false;
            this.errorBox.classList.remove("hidden");
            this.errorBox.textContent = "Не удалось загрузить проверку: " + error.message;
        }
    }

    renderStats() {
        const headline = this.data.correlations.find((row) => {
            return row.predictor === "rating" && row.outcome === "ga_per60";
        });
        const baseline = this.data.correlations.find((row) => {
            return row.predictor === "ga_per60" && row.outcome === "ga_per60";
        });
        const cards = [
            { value: this.data.nPlayers, label: "игроков в обеих половинах" },
            { value: this.data.nFitMatches + " / " + this.data.nOutMatches, label: "матчи fit / out" },
            { value: this.format(headline ? headline.spearman : 0, 3), label: "Спирмен рейтинг → G+A/60" },
            { value: this.format(baseline ? baseline.spearman : 0, 3), label: "Спирмен G+A → G+A (база)" },
        ];
        this.statsBox.innerHTML = cards.map((card) => {
            return `<article class="stat"><div class="value">${card.value}</div>` +
                `<div class="label">${card.label}</div></article>`;
        }).join("");
    }

    renderBars() {
        const rows = this.data.correlations.filter((row) => row.outcome === "ga_per60");
        const max = Math.max(...rows.map((row) => Math.abs(row.spearman)), 0.01);
        const labels = {
            rating: "рейтинг (RAPM)",
            raw_per60: "сырая ценность /60",
            ga_per60: "G+A /60",
            shots_per60: "броски /60",
            plus_minus_per60: "плюс-минус /60",
        };
        this.barsBox.innerHTML = rows.map((row) => {
            const width = Math.max(4, Math.round((Math.abs(row.spearman) / max) * 100));
            const cls = row.spearman >= 0 ? "bar" : "bar bar-neg";
            return `<div class="family-row">` +
                `<span>${labels[row.predictor] || row.predictor}</span>` +
                `<div class="${cls}"><span style="width:${width}%"></span></div>` +
                `<span class="num">${this.format(row.spearman, 3)}</span>` +
                `</div>`;
        }).join("");
    }

    renderCorrelations() {
        this.corrBody.innerHTML = this.data.correlations.map((row) => {
            return `<tr>` +
                `<td>${row.predictor}</td>` +
                `<td>${row.outcome}</td>` +
                `<td class="num">${this.format(row.pearson, 3)}</td>` +
                `<td class="num">${this.format(row.spearman, 3)}</td>` +
                `<td class="num">${row.n}</td>` +
                `</tr>`;
        }).join("");
    }

    renderPlayers() {
        this.playerBody.innerHTML = this.data.players.map((row) => {
            return `<tr>` +
                `<td>${row.playerId}</td>` +
                `<td>${row.position}</td>` +
                `<td class="num">${this.format(row.rating, 3)}</td>` +
                `<td class="num">${this.format(row.fitGaPer60, 2)}</td>` +
                `<td class="num">${this.format(row.outGaPer60, 2)}</td>` +
                `<td class="num">${this.format(row.outXgPer60, 2)}</td>` +
                `<td class="num">${this.format(row.outAvPer60, 2)}</td>` +
                `<td class="num">${this.format(row.fitToiMin, 1)}</td>` +
                `<td class="num">${this.format(row.outToiMin, 1)}</td>` +
                `</tr>`;
        }).join("");
    }

    format(value, digits) {
        return Number(value).toLocaleString("ru-RU", {
            minimumFractionDigits: digits,
            maximumFractionDigits: digits,
        });
    }
}

document.addEventListener("DOMContentLoaded", () => {
    new ValidatePage().load();
});

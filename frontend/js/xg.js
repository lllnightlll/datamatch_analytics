class XgPage {
    constructor() {
        this.statsBox = document.getElementById("stats");
        this.calibrationBox = document.getElementById("calibration");
        this.coefBody = document.getElementById("coef-rows");
        this.playerBody = document.getElementById("player-rows");
        this.errorBox = document.getElementById("error");
        this.statsBox.innerHTML = `<article class="stat"><div class="label">Учу логит…</div></article>`;
    }

    async load() {
        try {
            const response = await fetch("/api/xg");
            if (!response.ok) {
                throw new Error("Сервер вернул " + response.status);
            }
            this.data = await response.json();
            this.renderStats();
            this.renderCalibration();
            this.renderCoefficients();
            this.renderPlayers();
        } catch (error) {
            this.errorBox.hidden = false;
            this.errorBox.classList.remove("hidden");
            this.errorBox.textContent = "Не удалось загрузить xG: " + error.message;
        }
    }

    renderStats() {
        const { holdout, test } = this.data;
        const cards = [
            { value: this.format(holdout.logLoss, 3), label: "logloss holdout (матчи)" },
            { value: this.format(holdout.brier, 3), label: "Brier holdout" },
            { value: test.nGoals, label: "голов в тесте" },
            { value: this.format(test.sumXg, 1), label: "сумма xG на тесте" },
        ];
        this.statsBox.innerHTML = cards.map((card) => {
            return `<article class="stat"><div class="value">${card.value}</div>` +
                `<div class="label">${card.label}</div></article>`;
        }).join("");
    }

    renderCalibration() {
        const max = Math.max(
            ...this.data.calibration.map((bin) => Math.max(bin.predicted, bin.actual)),
            0.01,
        );
        this.calibrationBox.innerHTML = this.data.calibration.map((bin) => {
            const predW = Math.max(4, Math.round((bin.predicted / max) * 100));
            const actW = Math.max(4, Math.round((bin.actual / max) * 100));
            return `<div class="family-row">` +
                `<span>бин ${bin.bin} · n=${bin.n}</span>` +
                `<div>` +
                `<div class="bar"><span style="width:${predW}%"></span></div>` +
                `<div class="bar bar-actual"><span style="width:${actW}%"></span></div>` +
                `</div>` +
                `<span class="num">${this.format(bin.predicted, 3)} / ${this.format(bin.actual, 3)}</span>` +
                `</div>`;
        }).join("");
    }

    renderCoefficients() {
        this.coefBody.innerHTML = this.data.coefficients.map((row) => {
            return `<tr><td>${row.name}</td><td class="num">${this.format(row.weight, 3)}</td></tr>`;
        }).join("");
    }

    renderPlayers() {
        this.playerBody.innerHTML = this.data.topTestPlayers.map((row) => {
            return `<tr>` +
                `<td>${row.playerId}</td>` +
                `<td>${row.position}</td>` +
                `<td class="num">${row.nAttempts}</td>` +
                `<td class="num">${row.nGoals}</td>` +
                `<td class="num">${this.format(row.sumXg, 2)}</td>` +
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
    new XgPage().load();
});

class ThreatPage {
    constructor() {
        this.statsBox = document.getElementById("stats");
        this.familiesBox = document.getElementById("families");
        this.headRow = document.getElementById("player-head");
        this.playerBody = document.getElementById("player-rows");
        this.errorBox = document.getElementById("error");
        this.canvas = document.getElementById("heatmap");
        this.statsBox.innerHTML = `<article class="stat"><div class="label">Считаю сетку…</div></article>`;
    }

    async load() {
        try {
            const response = await fetch("/api/threat");
            if (!response.ok) {
                throw new Error("Сервер вернул " + response.status);
            }
            this.data = await response.json();
            this.familyNames = this.data.families.map((row) => row.family);
            this.renderStats();
            this.renderHeatmap();
            this.renderFamilies();
            this.renderPlayers();
        } catch (error) {
            this.errorBox.hidden = false;
            this.errorBox.classList.remove("hidden");
            this.errorBox.textContent = "Не удалось загрузить угрозу: " + error.message;
        }
    }

    renderStats() {
        const cards = [
            { value: this.format(this.data.globalRate, 3), label: "P(бросок скоро) среднее" },
            { value: this.data.nObservations.toLocaleString("ru-RU"), label: "наблюдений сетки (train)" },
            { value: this.data.lookaheadEvents, label: "окно lookahead, события" },
            { value: this.data.topTestPlayers.length, label: "игроков в топе теста" },
        ];
        this.statsBox.innerHTML = cards.map((card) => {
            return `<article class="stat"><div class="value">${card.value}</div>` +
                `<div class="label">${card.label}</div></article>`;
        }).join("");
    }

    renderHeatmap() {
        const ctx = this.canvas.getContext("2d");
        const grid = this.data.heatmap;
        const rows = grid.length;
        const cols = grid[0].length;
        const pad = 16;
        const width = this.canvas.width - pad * 2;
        const height = this.canvas.height - pad * 2;
        const cellW = width / cols;
        const cellH = height / rows;
        let min = Infinity;
        let max = -Infinity;
        grid.forEach((row) => {
            row.forEach((value) => {
                if (value < min) min = value;
                if (value > max) max = value;
            });
        });
        ctx.fillStyle = "#0c1014";
        ctx.fillRect(0, 0, this.canvas.width, this.canvas.height);
        for (let row = 0; row < rows; row += 1) {
            for (let col = 0; col < cols; col += 1) {
                const t = (grid[row][col] - min) / Math.max(1e-9, max - min);
                ctx.fillStyle = this.heatColor(t);
                const canvasRow = rows - 1 - row;
                ctx.fillRect(pad + col * cellW, pad + canvasRow * cellH, cellW + 0.5, cellH + 0.5);
            }
        }
        ctx.strokeStyle = "rgba(232, 238, 243, 0.35)";
        ctx.beginPath();
        ctx.moveTo(pad + width / 2, pad);
        ctx.lineTo(pad + width / 2, pad + height);
        ctx.stroke();
        ctx.fillStyle = "#e8eef3";
        ctx.beginPath();
        ctx.arc(pad + width - 6, pad + height / 2, 4, 0, Math.PI * 2);
        ctx.fill();
    }

    heatColor(t) {
        const clamp = Math.max(0, Math.min(1, t));
        const r = Math.round(26 + clamp * 210);
        const g = Math.round(36 + clamp * 140);
        const b = Math.round(48 + (1 - clamp) * 80);
        return `rgb(${r},${g},${b})`;
    }

    renderFamilies() {
        const maxAbs = Math.max(
            ...this.data.families.map((row) => Math.abs(row.totalValue)),
            0.01,
        );
        this.familiesBox.innerHTML = this.data.families.map((row) => {
            const width = Math.max(4, Math.round((Math.abs(row.totalValue) / maxAbs) * 100));
            const cls = row.totalValue >= 0 ? "bar" : "bar bar-neg";
            return `<div class="family-row">` +
                `<span>${row.family} · n=${row.nActions.toLocaleString("ru-RU")}</span>` +
                `<div class="${cls}"><span style="width:${width}%"></span></div>` +
                `<span class="num">${this.format(row.totalValue, 2)}</span>` +
                `</div>`;
        }).join("");
    }

    renderPlayers() {
        const familyHeads = this.familyNames.map((name) => `<th class="num">${name}</th>`).join("");
        this.headRow.innerHTML = `<th>Игрок</th><th>Амплуа</th>` +
            `<th class="num">Действия</th><th class="num">Σ ценность</th>` +
            familyHeads;
        this.playerBody.innerHTML = this.data.topTestPlayers.map((row) => {
            const familyCells = this.familyNames.map((name) => {
                return `<td class="num">${this.format(row.byFamily[name] || 0, 2)}</td>`;
            }).join("");
            return `<tr>` +
                `<td>${row.playerId}</td>` +
                `<td>${row.position}</td>` +
                `<td class="num">${row.nActions}</td>` +
                `<td class="num">${this.format(row.totalValue, 2)}</td>` +
                familyCells +
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
    new ThreatPage().load();
});

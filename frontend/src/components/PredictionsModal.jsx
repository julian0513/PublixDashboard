import { useEffect, useMemo, useState } from "react";
import ForecastTable from "./ForecastTable.jsx";
import Modal from "./Modal.jsx";
// Be flexible: work with either { getForecast } or { forecast } exports
import * as API from "../services/apiClient.jsx";

/** ---------- small helpers (local-time safe) ---------- */
const OCT_START = "2025-10-01";
const OCT_END   = "2025-10-31";

const toLocalISO = (d) => {
    const yyyy = d.getFullYear();
    const mm = String(d.getMonth() + 1).padStart(2, "0");
    const dd = String(d.getDate()).padStart(2, "0");
    return `${yyyy}-${mm}-${dd}`;
};
const todayLocalISO = () => toLocalISO(new Date());

function fmt(d) {
    return new Date(d + "T00:00:00").toLocaleDateString(undefined, {
        year: "numeric", month: "short", day: "numeric",
    });
}

/** ---------- unified baseline fetch (prefers API client; has timeout fallback) ---------- */
const API_BASE = import.meta.env.VITE_API_BASE || "http://localhost:8080";
const DEFAULT_TIMEOUT_MS = 8000;

async function fetchWithTimeout(url, opts = {}, timeoutMs = DEFAULT_TIMEOUT_MS) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    try {
        const res = await fetch(url, { ...opts, signal: controller.signal });
        if (!res.ok) throw new Error(`${opts.method || "GET"} ${url} → ${res.status}`);
        return res.json();
    } finally {
        clearTimeout(timer);
    }
}

// Always request the historical baseline → mode "seed"
async function fetchBaseline({ date, topK }) {
    const fn = API.getForecast ?? API.forecast;
    if (typeof fn === "function") {
        return fn({ start: date, end: date, mode: "seed", topK });
    }
    const url = new URL(`${API_BASE}/api/forecast`);
    url.searchParams.set("start", date);
    url.searchParams.set("end", date);
    url.searchParams.set("mode", "seed");
    url.searchParams.set("topK", String(topK));
    return fetchWithTimeout(url.toString());
}

/** ---------- Component ---------- */
export default function PredictionsModal({ open, onClose, defaultDate, defaultTopK = 10 }) {
    const [date, setDate] = useState(defaultDate || todayLocalISO());
    const [topK, setTopK] = useState(defaultTopK);
    const [loading, setLoading] = useState(false);
    const [baseline, setBaseline] = useState({ items: [], startDate: "", endDate: "" });
    const [error, setError] = useState("");

    const label = useMemo(
        () => (baseline.startDate
            ? `Baseline for ${fmt(baseline.startDate)} — ${fmt(baseline.endDate || baseline.startDate)}`
            : "Baseline"),
        [baseline.startDate, baseline.endDate]
    );

    // re-fetch a historical single-day baseline on any date/topK change while open
    useEffect(() => {
        if (!open || !date) return;
        let cancel = false;
        (async () => {
            setLoading(true);
            setError("");
            try {
                const res = await fetchBaseline({ date, topK: Math.max(1, Number(topK || 1)) });
                if (!cancel) {
                    setBaseline({
                        items: res.items || [],
                        startDate: res.startDate || date,
                        endDate: res.endDate || date,
                    });
                }
            } catch (e) {
                if (!cancel) setError(String(e.message || e));
            } finally {
                if (!cancel) setLoading(false);
            }
        })();
        return () => { cancel = true; };
    }, [open, date, topK]);

    return (
        <Modal
            open={open}
            onClose={onClose}
            title="Baseline by Day"
            primary={false} // no confirm button for this viewer
        >
            <div className="space-y-4">
                <div className="grid gap-3 md:grid-cols-3">
                    <div>
                        <label htmlFor="baselineDate" className="block text-sm mb-1">Date</label>
                        <input
                            id="baselineDate"
                            type="date"
                            className="w-full border rounded p-2"
                            value={date}
                            onChange={(e) => setDate(e.target.value)}
                            min={OCT_START}
                            max={OCT_END}
                        />
                    </div>
                    <div>
                        <label htmlFor="baselineTopK" className="block text-sm mb-1">Top K</label>
                        <input
                            id="baselineTopK"
                            type="number"
                            step={1}
                            inputMode="numeric"
                            min={1}
                            className="w-full border rounded p-2"
                            value={topK}
                            onChange={(e) => setTopK(Math.max(1, Number(e.target.value || 1)))}
                        />
                    </div>
                    <div className="flex items-end">
                        <div className="text-sm opacity-70">
                            Historical baseline — updates instantly when you change the date.
                        </div>
                    </div>
                </div>

                {loading && <div className="text-sm" aria-busy="true">Loading…</div>}
                {error && <div className="text-sm text-red-600">Could not load baseline: {error}</div>}

                {!loading && !error && (
                    <ForecastTable items={baseline.items} label={label} />
                )}
            </div>
        </Modal>
    );
}

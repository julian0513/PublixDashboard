import React, { useEffect, useMemo, useState } from "react";
import { getForecast, createSale, trainModel } from "./services/apiClient.jsx";
import DateRangeControls from "./components/DateRangeControls.jsx";
import ForecastTable from "./components/ForecastTable.jsx";
import SalesModal from "./components/SalesModal.jsx";
import Toast from "./components/Toast.jsx";
import PredictionsModal from "./components/PredictionsModal.jsx";
import publixLogo from "./assets/publix-logo.png";

/** ---------- small utils ---------- */
const fmt = (d) =>
    new Date(d + "T00:00:00").toLocaleDateString(undefined, {
        year: "numeric",
        month: "short",
        day: "numeric",
    });

/** ---------- networking helpers (timeouts) ---------- */
const API_BASE = import.meta.env.VITE_API_BASE || "http://localhost:8080";
const DEFAULT_TIMEOUT_MS = 8000;

async function fetchJSON(url, opts = {}, timeoutMs = DEFAULT_TIMEOUT_MS) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    try {
        const res = await fetch(url, { ...opts, signal: controller.signal });
        if (!res.ok) throw new Error(`${opts.method || "GET"} ${url} → ${res.status}`);
        return res.status === 204 ? null : res.json();
    } finally {
        clearTimeout(timer);
    }
}

async function listSalesByDateAPI(date) {
    const url = `${API_BASE}/api/sales?date=${encodeURIComponent(date)}`;
    return fetchJSON(url);
}

async function deleteSaleAPI(id) {
    const url = `${API_BASE}/api/sales/${encodeURIComponent(id)}`;
    await fetchJSON(url, { method: "DELETE" });
    return { ok: true };
}

/** ---------- date helpers (local) ---------- */
function isoToday() {
    const d = new Date();
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, "0");
    const day = String(d.getDate()).padStart(2, "0");
    return `${y}-${m}-${day}`; // local YYYY-MM-DD
}

// October-only utilities (keep UI calm outside October without errors/toasts)
const OCT_START = "2025-10-01";
const OCT_END = "2025-10-31";
function inOctober(date) {
    return date >= OCT_START && date <= OCT_END;
}
function clampToOctober(date) {
    if (!date || date < OCT_START) return OCT_START;
    if (date > OCT_END) return OCT_END;
    return date;
}

// Map legacy UI modes to backend modes (no UI change needed)
function normalizeMode(uiMode) {
    const m = (uiMode || "").toLowerCase();
    if (m === "historical" || m === "baseline") return "seed";
    if (m === "auto" || m === "updated") return "live";
    return "seed";
}

// Safely read start/end from either the new shape (dateRange) or legacy fields
function getRange(res) {
    if (res?.dateRange) return { startDate: res.dateRange.start, endDate: res.dateRange.end };
    return { startDate: res?.startDate, endDate: res?.endDate };
}

export default function App() {
    /** ---------- baseline (predictions tab header) ---------- */
    const [baseline, setBaseline] = useState({
        items: [],
        startDate: OCT_START,
        endDate: OCT_END,
    });

    /** ---------- predictions tab state ---------- */
    const [start, setStart] = useState(OCT_START);
    const [end, setEnd] = useState(OCT_END);
    const [topK, setTopK] = useState(10);
    const [mode, setMode] = useState("historical"); // "historical" | "auto"
    const [list, setList] = useState([]);
    const [loading, setLoading] = useState(false);

    /** ---------- tabs & modals ---------- */
    const [tab, setTab] = useState("predictions"); // "predictions" | "today"
    const [salesOpen, setSalesOpen] = useState(false);
    const [predModalOpen, setPredModalOpen] = useState(false);
    const [toast, setToast] = useState({ show: false, title: "", message: "" });

    /** ---------- today tab state ---------- */
        // Clamp defaults so initial render never calls non-October APIs
    const initialToday = clampToOctober(isoToday());
    const [todayDate, setTodayDate] = useState(initialToday); // sales table date
    const [todayStart, setTodayStart] = useState(initialToday); // forecast range start
    const [todayEnd, setTodayEnd] = useState(initialToday); // forecast range end
    const [todayTopK, setTodayTopK] = useState(10);
    const [todaySales, setTodaySales] = useState([]);
    const [todayLoading, setTodayLoading] = useState(false);
    const [nowcast, setNowcast] = useState({ items: [], startDate: "", endDate: "" });
    const [training, setTraining] = useState(false);

    /** ---------- load baseline once ---------- */
    useEffect(() => {
        (async () => {
            try {
                const res = await getForecast({
                    start: OCT_START,
                    end: OCT_END,
                    mode: normalizeMode("historical"), // → "seed"
                    topK: 10,
                });
                const { startDate, endDate } = getRange(res);
                setBaseline({
                    items: res.items || [],
                    startDate: startDate || OCT_START,
                    endDate: endDate || OCT_END,
                });
            } catch (e) {
                setToast({
                    show: true,
                    title: "Could not load baseline",
                    message: String(e.message || e),
                });
                console.error(e);
            }
        })();
    }, []);

    /** ---------- predictions tab: auto-fetch on changes ---------- */
    useEffect(() => {
        if (tab !== "predictions") return;
        let alive = true;
        setLoading(true);
        const t = setTimeout(async () => {
            try {
                const res = await getForecast({
                    start,
                    end,
                    mode: normalizeMode(mode),
                    topK,
                });
                if (!alive) return;
                setList(res.items || []);
            } catch (e) {
                if (!alive) return;
                setToast({
                    show: true,
                    title: "Could not load prediction",
                    message: String(e.message || e),
                });
            } finally {
                alive && setLoading(false);
            }
        }, 250);
        return () => {
            alive = false;
            clearTimeout(t);
        };
    }, [tab, start, end, mode, topK]);

    /** ---------- today tab helpers ---------- */
    async function refreshTodaySales(d = todayDate) {
        // If the chosen date is outside October, keep UI quiet and show an empty table.
        if (!inOctober(d)) {
            setTodaySales([]);
            return;
        }
        setTodayLoading(true);
        try {
            const rows = await listSalesByDateAPI(d);
            setTodaySales(rows || []);
        } catch (e) {
            // Quietly degrade to an empty table on fetch errors to avoid bothering users.
            console.warn("Sales fetch failed, showing empty list:", e);
            setTodaySales([]);
        } finally {
            setTodayLoading(false);
        }
    }

    // Load sales when switching to Today tab or changing the Sales Table Date
    useEffect(() => {
        if (tab === "today") refreshTodaySales(todayDate);
    }, [tab, todayDate]);

    // Single action for the button: retrain (live) then fetch prediction for selected range
    const runTrainAndNowcast = async () => {
        setTraining(true);
        try {
            await trainModel({ mode: "live" }); // 1) live retrain (seed + current rows)
            const res = await getForecast({
                start: todayStart,
                end: todayEnd,
                mode: normalizeMode("auto"), // → "live"
                topK: todayTopK,
            }); // 2) prediction
            const { startDate, endDate } = getRange(res);
            setNowcast({
                items: res.items || [],
                startDate,
                endDate,
            });
            setToast({
                show: true,
                title: "New Prediction Ready",
                message: "Based on history + current sales performance",
            });
        } catch (e) {
            setToast({ show: true, title: "Prediction failed", message: String(e.message || e) });
        } finally {
            setTraining(false);
        }
    };

    // NOTE: No auto-refreshing on Today tab. We intentionally removed any effect
    // that would call a prediction fetch when inputs/sales change.

    const saveSale = async ({ productName, units, date, createdAt }) => {
        try {
            await createSale({ productName, units, date, createdAt });
            setSalesOpen(false);

            // Reflect the saved date immediately in the Sales table
            const clamped = clampToOctober(date);
            setTodayDate(clamped);
            await refreshTodaySales(clamped);

            // Do NOT refresh prediction here — wait for explicit button click
            setToast({
                show: true,
                title: "Sales Recorded",
                message: `${productName} — ${units} units on ${fmt(clamped)}${createdAt ? ` at ${createdAt.slice(11, 16)}` : ""}`,
            });
        } catch (e) {
            setToast({
                show: true,
                title: "Could not record sale",
                message: String(e.message || e),
            });
        }
    };

    const deleteSale = async (id) => {
        try {
            await deleteSaleAPI(id);
            await refreshTodaySales(todayDate);
            // Do NOT refresh prediction here — wait for explicit button click
        } catch (e) {
            setToast({ show: true, title: "Delete failed", message: String(e.message || e) });
        }
    };

    const predictionLabel = useMemo(() => {
        const r = `Prediction for ${fmt(start)} – ${fmt(end)}`;
        return mode === "historical" ? `${r} (historical baseline)` : `${r} (history + current performance)`;
    }, [start, end, mode]);

    return (
        <div className="min-h-full">
            {/* Header */}
            <header className="sticky top-0 bg-mist/80 backdrop-blur border-b border-chrome">
                <div className="max-w-6xl mx-auto px-5 py-4 flex items-center justify-between">
                    <div className="flex items-center gap-3">
                        <img src={publixLogo} alt="Publix" className="h-8 w-auto" />
                        <div>
                            <h1 className="text-xl font-semibold">Welcome to Publix AI Sales Forecaster</h1>
                            <p className="text-sm opacity-60">Where using Artificial Intelligence is a pleasure.</p>
                        </div>
                    </div>
                    <nav className="flex gap-1 rounded-xl bg-white border border-chrome p-1 shadow-soft">
                        <button
                            onClick={() => setTab("predictions")}
                            className={`px-3 py-1.5 rounded-lg ${tab === "predictions" ? "bg-black text-white" : "hover:bg-mist"}`}
                            aria-pressed={tab === "predictions"}
                        >
                            Predictions
                        </button>
                        <button
                            onClick={() => setTab("today")}
                            className={`px-3 py-1.5 rounded-lg ${tab === "today" ? "bg-black text-white" : "hover:bg-mist"}`}
                            aria-pressed={tab === "today"}
                        >
                            Today
                        </button>
                    </nav>
                </div>
            </header>

            <main className="max-w-6xl mx-auto px-5 py-8 space-y-8">
                {/* ---- PREDICTIONS ONLY: Baseline block + Baseline-by-day modal trigger ---- */}
                {tab === "predictions" && (
                    <section className="space-y-4">
                        <h2 className="text-lg font-semibold">Today’s Agenda</h2>
                        <div className="bg-white rounded-2xl shadow-soft border border-chrome">
                            <div className="px-5 py-4 border-b border-chrome flex items-center justify-between gap-3">
                                <div>
                                    <div className="font-medium">🟢 Predicted top Halloween candy sellers this year (October 2025 🎃)</div>
                                    <div className="text-sm opacity-60">
                                        *Note — Based on 10 year history from October 2015–2024. This is the baseline historical prediction.
                                    </div>
                                </div>
                                <button
                                    onClick={() => setPredModalOpen(true)}
                                    className="px-3 py-2 rounded-lg border border-chrome bg-white hover:bg-mist whitespace-nowrap"
                                >
                                    View Baseline by Day
                                </button>
                            </div>
                            <div className="p-5">
                                <ForecastTable
                                    items={baseline.items}
                                    label={`✨ Historical Baseline for ${fmt(baseline.startDate)} – ${fmt(baseline.endDate)}`}
                                />
                            </div>
                        </div>
                    </section>
                )}

                {/* Tabs */}
                {tab === "predictions" ? (
                    <section className="space-y-6">
                        <div className="bg-white rounded-2xl shadow-soft border border-chrome p-5 space-y-5">
                            <h3 className="text-base font-semibold">Custom Date Range</h3>

                            <DateRangeControls start={start} end={end} topK={topK} onStart={setStart} onEnd={setEnd} onTopK={setTopK} />

                            <div className="flex flex-wrap items-center gap-4">
                                <label className="inline-flex items-center gap-2">
                                    <input
                                        type="radio"
                                        name="mode"
                                        value="historical"
                                        checked={mode === "historical"}
                                        onChange={() => setMode("historical")}
                                    />
                                    <span>Historical (baseline)</span>
                                </label>
                                <label className="inline-flex items-center gap-2">
                                    <input type="radio" name="mode" value="auto" checked={mode === "auto"} onChange={() => setMode("auto")} />
                                    <span>Updated (history + performance)</span>
                                </label>

                                {loading && (
                                    <span className="text-sm opacity-70" aria-busy="true">
                    Loading…
                  </span>
                                )}
                            </div>

                            {list?.length > 0 && <ForecastTable items={list} label={predictionLabel} />}
                        </div>
                    </section>
                ) : (
                    <section className="space-y-6">
                        <h2 className="text-lg font-semibold">Today - Dynamically add/remove sales based on date</h2>

                        {/* Controls row — ONLY Enter Sales + Run New Prediction */}
                        <div className="bg-white rounded-2xl shadow-soft border border-chrome p-5 grid gap-4 grid-cols-1 sm:grid-cols-2 lg:grid-cols-3">
                            {/* date for the sales table only */}
                            <div>
                                <label className="block text-sm mb-1">Sales Table Date</label>
                                <input
                                    type="date"
                                    className="w-full border rounded p-2"
                                    value={todayDate}
                                    onChange={(e) => setTodayDate(clampToOctober(e.target.value))}
                                    min={OCT_START}
                                    max={OCT_END}
                                />
                            </div>

                            {/* spacer */}
                            <div className="hidden lg:block" />

                            {/* actions */}
                            <div className="flex flex-wrap items-end gap-2">
                                <button
                                    onClick={() => setSalesOpen(true)}
                                    className="w-full sm:w-auto inline-flex items-center gap-2 px-4 py-2 rounded-lg border border-chrome bg-mist hover:bg-chrome"
                                >
                                    <span className="text-xl leading-none">＋</span>
                                    <span>Enter Sales</span>
                                </button>

                                <button
                                    onClick={runTrainAndNowcast}
                                    className="w-full sm:w-auto inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-black text-white disabled:opacity-50"
                                    disabled={training}
                                    aria-busy={training ? "true" : "false"}
                                >
                                    {training ? "Running…" : "Run New Prediction"}
                                </button>
                            </div>
                        </div>

                        {/* Sales table (middle) */}
                        <div className="bg-white rounded-2xl shadow-soft border border-chrome p-5">
                            <div className="flex items-center justify-between mb-3">
                                <h3 className="font-semibold">Sales for {fmt(todayDate)}</h3>
                                <button
                                    onClick={() => refreshTodaySales(todayDate)}
                                    className="px-3 py-1.5 rounded-lg border border-chrome bg-white hover:bg-mist disabled:opacity-50"
                                    disabled={todayLoading}
                                    aria-busy={todayLoading ? "true" : "false"}
                                >
                                    {todayLoading ? "Refreshing…" : "Reload"}
                                </button>
                            </div>

                            {todaySales?.length ? (
                                <div className="overflow-x-auto">
                                    <table className="min-w-full border text-sm">
                                        <thead>
                                        <tr className="bg-gray-50">
                                            <th className="p-2 border">Product</th>
                                            <th className="p-2 border">Units</th>
                                            <th className="p-2 border">Date</th>
                                            <th className="p-2 border">Actions</th>
                                        </tr>
                                        </thead>
                                        <tbody>
                                        {todaySales.map((r) => (
                                            <tr key={r.id}>
                                                <td className="p-2 border">{r.productName}</td>
                                                <td className="p-2 border">{r.units}</td>
                                                <td className="p-2 border">{r.date}</td>
                                                <td className="p-2 border">
                                                    <button onClick={() => deleteSale(r.id)} className="px-2 py-1 border rounded hover:bg-mist">
                                                        Delete
                                                    </button>
                                                </td>
                                            </tr>
                                        ))}
                                        </tbody>
                                    </table>
                                </div>
                            ) : (
                                <p className="text-sm opacity-70">No sales for this date yet.</p>
                            )}
                        </div>

                        {/* Updated Prediction (bottom) — only updates on button click */}
                        <div className="bg-white rounded-2xl shadow-soft border border-chrome p-5 space-y-5">
                            <h3 className="text-base font-semibold">Updated Prediction</h3>

                            <DateRangeControls
                                start={todayStart}
                                end={todayEnd}
                                topK={todayTopK}
                                onStart={(d) => setTodayStart(clampToOctober(d))}
                                onEnd={(d) => setTodayEnd(clampToOctober(d))}
                                onTopK={setTodayTopK}
                            />

                            {nowcast.items.length > 0 ? (
                                <ForecastTable
                                    items={nowcast.items}
                                    label={`Updated Prediction — ${fmt(nowcast.startDate)} – ${fmt(nowcast.endDate)} (history + current performance)`}
                                />
                            ) : (
                                <p className="text-sm opacity-70">
                                    No prediction yet. Click <span className="font-medium">Run New Prediction</span>.
                                </p>
                            )}
                        </div>
                    </section>
                )}
            </main>

            {/* Modals & Toast */}
            <SalesModal open={salesOpen} onClose={() => setSalesOpen(false)} onSaved={saveSale} defaultDate={todayDate} />

            {/* Baseline-by-day modal ONLY on Predictions tab */}
            {tab === "predictions" && (
                <PredictionsModal open={predModalOpen} onClose={() => setPredModalOpen(false)} defaultDate={todayDate} defaultTopK={5} />
            )}

            <Toast
                show={toast.show}
                title={toast.title}
                message={toast.message}
                onClose={() => setToast({ show: false, title: "", message: "" })}
            />
        </div>
    );
}

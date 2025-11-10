import React from "react";

const OCT_START = "2025-10-01";
const OCT_END   = "2025-10-31";

/** Local date helpers — avoid UTC conversions entirely. */
const todayLocalISO = () => {
    const d = new Date();
    const yyyy = d.getFullYear();
    const mm = String(d.getMonth() + 1).padStart(2, "0");
    const dd = String(d.getDate()).padStart(2, "0");
    return `${yyyy}-${mm}-${dd}`;
};
const parseISO = (iso) => {
    const [y, m, d] = iso.split("-").map(Number);
    return new Date(y, m - 1, d); // local time
};
const toLocalISO = (d) => {
    const yyyy = d.getFullYear();
    const mm = String(d.getMonth() + 1).padStart(2, "0");
    const dd = String(d.getDate()).padStart(2, "0");
    return `${yyyy}-${mm}-${dd}`;
};
/** Safely add days in local time; no toISOString() to avoid TZ shifts. */
const addDaysISO = (iso, n) => {
    const d = parseISO(iso);
    d.setDate(d.getDate() + n);
    return toLocalISO(d);
};

/** Clamp utilities */
function clampToOct(iso) {
    if (!iso) return OCT_START;
    if (iso < OCT_START) return OCT_START;
    if (iso > OCT_END) return OCT_END;
    return iso;
}
function clampRange(s, e) {
    const cs = clampToOct(s);
    const ce = clampToOct(e);
    return cs <= ce ? [cs, ce] : [ce, cs];
}

/**
 * DateRangeControls
 * Reusable date-range selector with quick picks, clamped to Oct 2025.
 * Props:
 *  - start, end, topK
 *  - onStart(iso), onEnd(iso), onTopK(n)
 *  - hideTopK (bool)
 */
export default function DateRangeControls({
                                              start, end, topK,
                                              onStart, onEnd, onTopK,
                                              hideTopK = false,
                                          }) {
    const changeStart = (v) => {
        const [cs, ce] = clampRange(v, end || v);
        onStart(cs); onEnd(ce);
    };
    const changeEnd = (v) => {
        const [cs, ce] = clampRange(start || v, v);
        onStart(cs); onEnd(ce);
    };

    const setQuick = (key) => {
        if (key === "today") {
            const t = clampToOct(todayLocalISO());
            onStart(t); onEnd(t);
        } else if (key === "next7") {
            const base = clampToOct(todayLocalISO());
            const end7 = addDaysISO(base, 6); // inclusive 7 days
            const [cs, ce] = clampRange(base, end7);
            onStart(cs); onEnd(ce);
        } else if (key === "oct2025") {
            onStart(OCT_START); onEnd(OCT_END);
        }
    };

    return (
        <div className="bg-white rounded-2xl shadow-soft border border-chrome p-4 flex flex-col gap-3 md:flex-row md:items-end">
            <div>
                <label htmlFor="startDate" className="block text-xs uppercase tracking-wide opacity-60 mb-1">Start date</label>
                <input
                    id="startDate"
                    type="date"
                    className="border rounded p-2"
                    min={OCT_START}
                    max={OCT_END}
                    value={start}
                    onChange={(e) => changeStart(e.target.value)}
                />
            </div>

            <div>
                <label htmlFor="endDate" className="block text-xs uppercase tracking-wide opacity-60 mb-1">End date</label>
                <input
                    id="endDate"
                    type="date"
                    className="border rounded p-2"
                    min={OCT_START}
                    max={OCT_END}
                    value={end}
                    onChange={(e) => changeEnd(e.target.value)}
                />
            </div>

            {!hideTopK && (
                <div>
                    <label htmlFor="topK" className="block text-xs uppercase tracking-wide opacity-60 mb-1">Top K</label>
                    <input
                        id="topK"
                        type="number"
                        step={1}
                        inputMode="numeric"
                        className="border rounded p-2 w-28"
                        min={1}
                        value={topK}
                        onChange={(e) => onTopK?.(Math.max(1, Number(e.target.value || 1)))}
                    />
                </div>
            )}

            <div className="flex-1" />

            <div className="flex flex-wrap gap-2">
                {[
                    { key: "today",   label: "Today" },
                    { key: "next7",   label: "Next 7 days" },
                    { key: "oct2025", label: "Oct 2025" },
                ].map((q) => (
                    <button
                        key={q.key}
                        type="button"
                        onClick={() => setQuick(q.key)}
                        className="px-3 py-2 rounded-lg border border-chrome bg-white hover:bg-mist"
                    >
                        {q.label}
                    </button>
                ))}
            </div>
        </div>
    );
}

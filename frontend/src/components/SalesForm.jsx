import { useEffect, useState } from "react";

const OCT_START = "2025-10-01";
const OCT_END   = "2025-10-31";

function todayLocalISO() {
    const d = new Date();
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, "0");
    const day = String(d.getDate()).padStart(2, "0");
    return `${y}-${m}-${day}`;
}
function clampToOct(iso) {
    if (!iso) return OCT_START;
    if (iso < OCT_START) return OCT_START;
    if (iso > OCT_END) return OCT_END;
    return iso;
}

export default function SalesForm({ defaultDate, onCreated }) {
    const [productName, setProductName] = useState("");
    const [units, setUnits] = useState("");
    const [date, setDate] = useState(clampToOct(defaultDate || todayLocalISO()));
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState("");

    // keep in sync if parent changes defaultDate
    useEffect(() => {
        if (defaultDate) setDate(clampToOct(defaultDate));
    }, [defaultDate]);

    const canSubmit =
        productName.trim().length > 0 &&
        Number.isInteger(Number(units)) &&
        Number(units) >= 1 &&
        !!date;

    async function submit(e) {
        e.preventDefault();
        setError("");
        if (!canSubmit) {
            setError("Please enter a product, a whole number of units (≥ 1), and a date.");
            return;
        }
        try {
            setSubmitting(true);
            const name = productName.trim();
            const unitsInt = Math.max(1, parseInt(units, 10));
            await onCreated?.({ productName: name, units: unitsInt, date });
            // reset fields but keep date
            setProductName("");
            setUnits("");
        } catch (err) {
            setError(String(err?.message || err));
        } finally {
            setSubmitting(false);
        }
    }

    return (
        <form onSubmit={submit} className="grid gap-3 md:grid-cols-4 items-end">
            <div className="col-span-1">
                <label htmlFor="sf-product" className="block text-sm mb-1">Product</label>
                <input
                    id="sf-product"
                    data-autofocus
                    className="w-full border rounded p-2"
                    value={productName}
                    onChange={(e) => setProductName(e.target.value)}
                    placeholder="e.g., Snickers"
                    disabled={submitting}
                    required
                />
            </div>

            <div className="col-span-1">
                <label htmlFor="sf-units" className="block text-sm mb-1">Units</label>
                <input
                    id="sf-units"
                    type="number"
                    step={1}
                    inputMode="numeric"
                    min={1}
                    className="w-full border rounded p-2"
                    value={units}
                    onChange={(e) => setUnits(e.target.value)}
                    placeholder="e.g., 12"
                    disabled={submitting}
                    required
                />
            </div>

            <div className="col-span-1">
                <label htmlFor="sf-date" className="block text-sm mb-1">Date</label>
                <input
                    id="sf-date"
                    type="date"
                    className="w-full border rounded p-2"
                    value={date}
                    onChange={(e) => setDate(clampToOct(e.target.value))}
                    min={OCT_START}
                    max={OCT_END}
                    disabled={submitting}
                    required
                />
            </div>

            <button
                type="submit"
                className="col-span-1 border rounded p-2 bg-black text-white disabled:opacity-50"
                disabled={!canSubmit || submitting}
                aria-busy={submitting ? "true" : "false"}
            >
                {submitting ? "Adding…" : "Add Sale"}
            </button>

            {error && (
                <div className="md:col-span-4 text-sm text-red-600" aria-live="polite">
                    {error}
                </div>
            )}
        </form>
    );
}

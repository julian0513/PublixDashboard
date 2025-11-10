import React, { useEffect, useState } from "react";
import Modal from "./Modal.jsx";

const OCT_START = "2025-10-01";
const OCT_END   = "2025-10-31";

/** Clamp helper to keep dates within October window */
function clampToOct(iso) {
    if (!iso) return OCT_START;
    if (iso < OCT_START) return OCT_START;
    if (iso > OCT_END) return OCT_END;
    return iso;
}

/** Convert 12h time + AM/PM into naive ISO datetime (YYYY-MM-DDTHH:MM:SS) */
function toIsoNaive(dateStr /* 'YYYY-MM-DD' */, timeStr /* 'hh:mm' */, ampm /* 'AM'|'PM' */) {
    if (!timeStr) return null;
    // basic validation: hh:mm, 1-2 digit hour, 2 digit minute
    const m = /^\s*(\d{1,2}):(\d{2})\s*$/.exec(timeStr);
    if (!m) return null;
    let h = Number(m[1]);
    const mm = Number(m[2]);
    if (!(h >= 1 && h <= 12) || !(mm >= 0 && mm <= 59)) return null;
    const up = String(ampm || "").toUpperCase();
    if (up !== "AM" && up !== "PM") return null;
    if (up === "PM" && h < 12) h += 12;
    if (up === "AM" && h === 12) h = 0;
    const hh = String(h).padStart(2, "0");
    const mins = String(mm).padStart(2, "0");
    return `${dateStr}T${hh}:${mins}:00`;
}

export default function SalesModal({ open, onClose, onSaved, defaultDate }) {
    const [productName, setProductName] = useState("");
    const [units, setUnits] = useState("");
    const [date, setDate] = useState(clampToOct(defaultDate || OCT_START));
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState("");

    // NEW: time-of-entry inputs (12-hour clock + AM/PM)
    const [timeStr, setTimeStr] = useState(""); // 'hh:mm'
    const [ampm, setAmPm] = useState("PM");     // 'AM' | 'PM'

    // Reset on open, prefilling date from parent
    useEffect(() => {
        if (open) {
            setProductName("");
            setUnits("");
            setDate(clampToOct(defaultDate || OCT_START));
            setTimeStr("");
            setAmPm("PM");
            setError("");
        }
    }, [open, defaultDate]);

    const canSubmit =
        productName.trim().length > 0 &&
        Number.isInteger(Number(units)) &&
        Number(units) >= 1 &&
        !!date;

    const save = async () => {
        if (saving) return;
        if (!canSubmit) {
            setError("Please enter a product, a whole number of units (≥ 1), and a valid date.");
            return;
        }

        // Build optional createdAt if the user entered a time
        let createdAt = null;
        if (timeStr.trim() !== "") {
            createdAt = toIsoNaive(date, timeStr, ampm);
            if (!createdAt) {
                setError("Please provide a valid time (hh:mm) and choose AM/PM.");
                return;
            }
        }

        try {
            setSaving(true);
            setError("");
            const u = Math.max(1, parseInt(units, 10));

            // Pass createdAt when present; parent can forward it to the API
            await onSaved({ productName: productName.trim(), units: u, date, createdAt });

            // Reset text fields, keep date as-is for rapid entries
            setProductName("");
            setUnits("");
            setTimeStr("");
            setAmPm("PM");
        } catch (e) {
            setError(String(e?.message || e));
        } finally {
            setSaving(false);
        }
    };

    return (
        <Modal
            open={open}
            title="Enter Sales"
            onClose={onClose}
            onPrimary={save}
            primaryLabel={saving ? "Saving…" : "Save"}
        >
            <div className="flex flex-col gap-3">
                <div>
                    <label htmlFor="sm-product" className="text-sm block mb-1 opacity-70">Candy name</label>
                    <input
                        id="sm-product"
                        data-autofocus
                        value={productName}
                        onChange={(e) => setProductName(e.target.value)}
                        placeholder="e.g., Reese’s Peanut Butter Cups"
                        className="w-full px-3 py-2 border border-chrome rounded-lg"
                        disabled={saving}
                    />
                </div>

                <div>
                    <label htmlFor="sm-units" className="text-sm block mb-1 opacity-70">Units sold</label>
                    <input
                        id="sm-units"
                        type="number"
                        min={1}
                        step={1}
                        inputMode="numeric"
                        value={units}
                        onChange={(e) => setUnits(e.target.value)}
                        className="w-full px-3 py-2 border border-chrome rounded-lg"
                        disabled={saving}
                    />
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                    <div className="sm:col-span-1">
                        <label htmlFor="sm-date" className="text-sm block mb-1 opacity-70">Date (October only)</label>
                        <input
                            id="sm-date"
                            type="date"
                            value={date}
                            onChange={(e) => setDate(clampToOct(e.target.value))}
                            min={OCT_START}
                            max={OCT_END}
                            className="w-full px-3 py-2 border border-chrome rounded-lg"
                            disabled={saving}
                        />
                    </div>

                    {/* NEW: time-of-entry (optional) */}
                    <div>
                        <label htmlFor="sm-time" className="text-sm block mb-1 opacity-70">Time of entry</label>
                        <input
                            id="sm-time"
                            type="text"
                            inputMode="numeric"
                            placeholder="hh:mm"
                            value={timeStr}
                            onChange={(e) => setTimeStr(e.target.value)}
                            className="w-full px-3 py-2 border border-chrome rounded-lg"
                            disabled={saving}
                            aria-describedby="sm-time-hint"
                        />
                        <p id="sm-time-hint" className="text-xs opacity-60 mt-1">Use 12-hour format (ex: 2:05)</p>
                    </div>

                    <div>
                        <label htmlFor="sm-ampm" className="text-sm block mb-1 opacity-70">AM/PM</label>
                        <select
                            id="sm-ampm"
                            value={ampm}
                            onChange={(e) => setAmPm(e.target.value)}
                            className="w-full px-3 py-2 border border-chrome rounded-lg"
                            disabled={saving}
                        >
                            <option value="AM">AM</option>
                            <option value="PM">PM</option>
                        </select>
                    </div>
                </div>

                {!!error && (
                    <p className="text-sm text-red-600" aria-live="polite">{error}</p>
                )}

                <p className="text-xs opacity-60">
                    Tip: Time is essential when making intraday forecasts.
                </p>
            </div>
        </Modal>
    );
}

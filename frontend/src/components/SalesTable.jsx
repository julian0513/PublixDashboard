import React, { useMemo } from "react";

export default function SalesTable({ rows, onDelete }) {
    const data = Array.isArray(rows) ? rows : [];
    const intFmt = useMemo(
        () => new Intl.NumberFormat(undefined, { maximumFractionDigits: 0 }),
        []
    );

    if (!data.length) {
        return <p className="text-sm opacity-70">No sales for this date yet.</p>;
    }

    return (
        <div className="mt-3 overflow-x-auto">
            <table className="min-w-full border text-sm" aria-label="Sales table">
                <thead className="bg-gray-50">
                <tr>
                    <th scope="col" className="p-2 border text-left">Product</th>
                    <th scope="col" className="p-2 border text-right">Units</th>
                    <th scope="col" className="p-2 border text-left">Date</th>
                    <th scope="col" className="p-2 border text-left">Actions</th>
                </tr>
                </thead>
                <tbody>
                {data.map((r, i) => {
                    const key = r?.id ?? `${r?.productName ?? "item"}_${r?.date ?? "date"}_${i}`;
                    const units =
                        Number.isFinite(Number(r?.units)) ? intFmt.format(Number(r.units)) : "—";
                    return (
                        <tr key={key}>
                            <td className="p-2 border">{r?.productName ?? "—"}</td>
                            <td className="p-2 border text-right tabular-nums">{units}</td>
                            <td className="p-2 border">{r?.date ?? "—"}</td>
                            <td className="p-2 border">
                                <button
                                    type="button"
                                    onClick={() => onDelete?.(r?.id)}
                                    className="px-2 py-1 border rounded hover:bg-mist"
                                    aria-label={
                                        r?.productName
                                            ? `Delete ${r.productName} on ${r?.date ?? "this date"}`
                                            : "Delete row"
                                    }
                                >
                                    Delete
                                </button>
                            </td>
                        </tr>
                    );
                })}
                </tbody>
            </table>
        </div>
    );
}

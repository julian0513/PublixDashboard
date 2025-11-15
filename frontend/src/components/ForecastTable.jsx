import React, { useId, useMemo } from "react";
import ProductTrendGraph from "./ProductTrendGraph.jsx";

/**
 * ForecastTable
 * Renders a ranked list of forecast items with predicted units and optional confidence.
 * - Defensive number formatting (thousands separators, clamps, graceful "—" on missing)
 * - Accessible: heading associated to table via aria-labelledby; header scopes set
 * - onProductClick: optional callback when a product name is clicked
 */
export default function ForecastTable({ items = [], label = "Forecast", onProductClick, mode = "seed" }) {
    const headingId = useId();

    // Formatters once per mount
    const intFmt = useMemo(
        () => new Intl.NumberFormat(undefined, { maximumFractionDigits: 0 }),
        []
    );
    const pctFmt = useMemo(
        () => new Intl.NumberFormat(undefined, { style: "percent", maximumFractionDigits: 0 }),
        []
    );

    const countLabel = `${items.length} ${items.length === 1 ? "item" : "items"}`;

    return (
        <div className="bg-white rounded-2xl shadow-soft border border-chrome">
            <div className="px-5 py-4 border-b border-chrome flex items-baseline justify-between">
                <h3 id={headingId} className="text-lg font-semibold">
                    {label}
                </h3>
                <span className="text-sm opacity-60">{countLabel}</span>
            </div>

            <div className="overflow-x-auto">
                <table className="w-full text-sm" aria-labelledby={headingId}>
                    <thead className="bg-mist">
                        <tr>
                            <th scope="col" className="text-left px-5 py-3">🏆 Rank</th>
                            <th scope="col" className="text-left px-5 py-3">🍭 Product</th>
                            <th scope="col" className="text-right px-5 py-3">🔮 Predicted Units</th>
                            <th scope="col" className="text-right px-5 py-3">👑 Confidence</th>
                        </tr>
                    </thead>

                    <tbody>
                        {items.map((it, i) => {
                            const unitsNum = Number.isFinite(it?.predictedUnits)
                                ? Math.max(0, Math.round(it.predictedUnits))
                                : null;
                            const confNum = Number.isFinite(it?.confidence)
                                ? Math.min(1, Math.max(0, it.confidence))
                                : null;

                            const productName = it?.productName;
                            return (
                                <tr key={`${productName ?? "item"}__${i}`} className="border-t border-chrome">
                                    <td className="px-5 py-3">{i + 1}</td>
                                    <td className="px-5 py-3">
                                        <div className="flex items-center gap-2 group">
                                            {productName && onProductClick ? (
                                                <button
                                                    onClick={() => onProductClick(productName)}
                                                    className="text-left hover:underline hover:text-blue-600 cursor-pointer"
                                                >
                                                    {productName}
                                                </button>
                                            ) : (
                                                <span>{productName ?? "—"}</span>
                                            )}
                                        {productName && (
                                            <div className="opacity-0 group-hover:opacity-100 transition-opacity">
                                                <ProductTrendGraph productName={productName} mode={mode} />
                                            </div>
                                        )}
                                        </div>
                                    </td>
                                    <td className="px-5 py-3 text-right tabular-nums">
                                        {unitsNum !== null ? intFmt.format(unitsNum) : "—"}
                                    </td>
                                    <td className="px-5 py-3 text-right tabular-nums">
                                        {confNum !== null ? pctFmt.format(confNum) : "—"}
                                    </td>
                                </tr>
                            );
                        })}

                        {items.length === 0 && (
                            <tr>
                                <td colSpan={4} className="px-5 py-10 text-center opacity-60">No data</td>
                            </tr>
                        )}
                    </tbody>
                </table>
            </div>
        </div>
    );
}

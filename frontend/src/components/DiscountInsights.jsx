import React, { useEffect, useState } from "react";
import { getDiscountEffectiveness, getOptimalDiscount } from "../services/apiClient.jsx";

/**
 * DiscountInsights Component
 * 
 * Displays discount effectiveness analysis for a product.
 */
export default function DiscountInsights({ productName }) {
    const [effectiveness, setEffectiveness] = useState([]);
    const [optimalDiscount, setOptimalDiscount] = useState(null);
    const [isPredicted, setIsPredicted] = useState(false);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);

    useEffect(() => {
        if (!productName) {
            setEffectiveness([]);
            setOptimalDiscount(null);
            return;
        }

        setLoading(true);
        setError(null);

        Promise.all([
            getDiscountEffectiveness({ productName }),
            getOptimalDiscount({ productName }),
        ])
            .then(([effResponse, optData]) => {
                // Handle both DTO format (effResponse.items) and legacy format (effResponse is array)
                const effectivenessData = effResponse.items || effResponse;
                const predicted = effResponse.isPredicted !== undefined ? effResponse.isPredicted : false;
                const optimalFromResponse = effResponse.optimalDiscountPercent || null;
                
                setEffectiveness(Array.isArray(effectivenessData) ? effectivenessData : []);
                setOptimalDiscount(optimalFromResponse || optData?.optimalDiscountPercent || null);
                setIsPredicted(predicted);
            })
            .catch((e) => {
                setError(e.message);
                setEffectiveness([]);
                setOptimalDiscount(null);
                setIsPredicted(false);
            })
            .finally(() => {
                setLoading(false);
            });
    }, [productName]);

    if (!productName) {
        return (
            <div className="text-sm opacity-60">
                Select a product to see discount insights
            </div>
        );
    }

    if (loading) {
        return <div className="text-sm opacity-70">Loading discount insights...</div>;
    }

    if (error) {
        return <div className="text-sm text-red-600">Error: {error}</div>;
    }

    // Group by discount percent and calculate averages
    const discountGroups = effectiveness.reduce((acc, item) => {
        const key = item.discountPercent || 0;
        if (!acc[key]) {
            acc[key] = {
                discountPercent: key,
                totalUnits: 0,
                totalRevenue: 0,
                avgLift: 0,
                count: 0,
            };
        }
        acc[key].totalUnits += item.unitsSold || 0;
        acc[key].totalRevenue += parseFloat(item.revenue || 0);
        acc[key].avgLift += parseFloat(item.salesLiftPercent || 0);
        acc[key].count += 1;
        return acc;
    }, {});

    const summary = Object.values(discountGroups).map((group) => ({
        discountPercent: group.discountPercent,
        avgUnits: Math.round(group.totalUnits / group.count),
        avgRevenue: group.totalRevenue / group.count,
        avgLift: group.avgLift / group.count,
    })).sort((a, b) => {
        const liftDiff = (b.avgLift || 0) - (a.avgLift || 0);
        // Secondary sort by discount percent if sales lift is equal
        return liftDiff !== 0 ? liftDiff : b.discountPercent - a.discountPercent;
    });

    const currencyFmt = new Intl.NumberFormat(undefined, {
        style: "currency",
        currency: "USD",
        minimumFractionDigits: 2,
    });

    const pctFmt = new Intl.NumberFormat(undefined, {
        style: "percent",
        maximumFractionDigits: 1,
    });

    return (
        <div className="space-y-4">
            {/* Prediction indicator */}
            {isPredicted && (
                <div className="p-3 bg-blue-50 border border-blue-200 rounded-lg">
                    <div className="flex items-center gap-2 text-sm text-blue-800">
                        <span className="text-base">✨</span>
                        <span className="font-medium">
                            Predicted Discount Insights
                        </span>
                    </div>
                    <div className="text-xs text-blue-600 mt-1">
                        This product is new or has limited history. Showing predicted discount effectiveness based on similar products.
                    </div>
                </div>
            )}
            
            {optimalDiscount != null && optimalDiscount > 0 && (
                <div className="p-4 bg-gradient-to-r from-green-50 to-emerald-50 border border-green-200 rounded-lg shadow-sm">
                    <div className="flex items-center gap-2 mb-2">
                        <span className="text-lg">💡</span>
                        <div className="text-sm font-semibold text-green-800">
                            {isPredicted ? 'Predicted ' : ''}Optimal Discount Recommendation
                        </div>
                    </div>
                    <div className="text-2xl font-bold text-green-900 mb-1">
                        {pctFmt.format(optimalDiscount / 100)}
                    </div>
                    <div className="text-xs text-green-700">
                        This discount percentage {isPredicted ? 'is predicted to maximize' : 'maximizes'} sales volume for <span className="font-medium">{productName}</span>
                    </div>
                </div>
            )}

            {summary.length > 0 ? (
                <div className="space-y-3">
                    <div className="flex items-center justify-between">
                        <h4 className="text-sm font-semibold">Discount Performance Analysis</h4>
                        <span className="text-xs opacity-60">
                            Historical data across all discount levels
                        </span>
                    </div>
                    <div className="space-y-2">
                        {summary.map((item, idx) => {
                            // Compare discount percentages (both are in percentage form, e.g., 15 for 15%)
                            const isOptimal = optimalDiscount != null && 
                                            Math.abs(item.discountPercent - optimalDiscount) < 0.01;
                            return (
                                <div
                                    key={`${item.discountPercent}_${idx}`}
                                    className={`flex items-center justify-between p-3 rounded border transition-colors ${
                                        isOptimal 
                                            ? "bg-green-50 border-green-200" 
                                            : "bg-mist border-chrome hover:bg-chrome"
                                    }`}
                                >
                                    <div className="flex items-center gap-3">
                                        {isOptimal && (
                                            <span className="text-xs font-semibold text-green-600">⭐</span>
                                        )}
                                        <span className="text-sm font-medium">
                                            {pctFmt.format(item.discountPercent / 100)} off
                                        </span>
                                    </div>
                                    <div className="flex items-center gap-4 text-xs">
                                        <span className="opacity-70">
                                            Avg Units: <span className="font-medium">{item.avgUnits.toLocaleString()}</span>
                                        </span>
                                        {item.avgLift > 0 && (
                                            <span className="text-green-600 font-medium">
                                                +{pctFmt.format(item.avgLift / 100)} sales lift
                                            </span>
                                        )}
                                        <span className="opacity-60">
                                            Avg Revenue: <span className="font-medium">{currencyFmt.format(item.avgRevenue)}</span>
                                        </span>
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                </div>
            ) : (
                <div className="text-sm opacity-60 italic p-4 bg-mist rounded border border-chrome">
                    No discount effectiveness data available for <span className="font-medium">{productName}</span>. 
                    This may indicate the product hasn't had discount promotions tracked yet.
                </div>
            )}
        </div>
    );
}


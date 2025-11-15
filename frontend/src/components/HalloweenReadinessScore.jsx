import React, { useEffect, useState } from "react";
import { getHalloweenReadiness } from "../services/apiClient.jsx";

/**
 * HalloweenReadinessScore Component
 * 
 * Displays a composite "Halloween Readiness Score" (0-100) for a product.
 * Shows breakdown of factors:
 * - Trend Score (0-25): Historical performance trend
 * - Discount Score (0-25): Discount effectiveness
 * - Basket Score (0-25): Cross-sell potential
 * - Demand Score (0-25): Predicted demand
 * 
 * Provides actionable recommendations for managers.
 */
export default function HalloweenReadinessScore({ productName }) {
    const [score, setScore] = useState(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);

    useEffect(() => {
        if (!productName) {
            setScore(null);
            return;
        }

        setLoading(true);
        setError(null);

        getHalloweenReadiness({ productName })
            .then((data) => {
                setScore(data);
            })
            .catch((e) => {
                setError(e.message);
                setScore(null);
            })
            .finally(() => {
                setLoading(false);
            });
    }, [productName]);

    if (!productName) {
        return (
            <div className="text-sm opacity-60">
                Select a product to see Halloween Readiness Score
            </div>
        );
    }

    if (loading) {
        return <div className="text-sm opacity-70">Calculating readiness score...</div>;
    }

    if (error) {
        return <div className="text-sm text-red-600">Error: {error}</div>;
    }

    if (!score) {
        return (
            <div className="text-sm opacity-60 italic p-4 bg-mist rounded border border-chrome">
                No readiness score available for <span className="font-medium">{productName}</span>.
            </div>
        );
    }

    // Determine score color
    const getScoreColor = (overallScore) => {
        if (overallScore >= 85) return "text-green-600";
        if (overallScore >= 70) return "text-blue-600";
        if (overallScore >= 55) return "text-yellow-600";
        if (overallScore >= 40) return "text-orange-600";
        return "text-red-600";
    };

    const getScoreBgColor = (overallScore) => {
        if (overallScore >= 85) return "bg-green-50 border-green-200";
        if (overallScore >= 70) return "bg-blue-50 border-blue-200";
        if (overallScore >= 55) return "bg-yellow-50 border-yellow-200";
        if (overallScore >= 40) return "bg-orange-50 border-orange-200";
        return "bg-red-50 border-red-200";
    };

    return (
        <div className="space-y-4">
            {/* Overall Score Card */}
            <div className={`p-6 rounded-lg border-2 ${getScoreBgColor(score.overallScore)}`}>
                <div className="flex items-center justify-between mb-4">
                    <div>
                        <div className="text-sm font-medium opacity-70 mb-1">Halloween Readiness Score</div>
                        <div className={`text-4xl font-bold ${getScoreColor(score.overallScore)}`}>
                            {score.overallScore}/100
                        </div>
                    </div>
                    <div className="text-3xl">
                        {score.overallScore >= 85 ? "🎃" : score.overallScore >= 70 ? "👻" : "🍬"}
                    </div>
                </div>
                <div className="text-sm font-medium text-gray-800">
                    {score.recommendation}
                </div>
            </div>

            {/* Score Breakdown */}
            <div>
                <h4 className="text-sm font-semibold mb-3">Score Breakdown</h4>
                <div className="space-y-3">
                    {[
                        {
                            label: "Trend Score",
                            value: score.trendScore,
                            max: 25,
                            desc: "Historical performance trend",
                            tooltip: "Measures how the product's sales have changed over the 10-year period (2015-2024). Higher scores indicate increasing sales trends, suggesting growing popularity."
                        },
                        {
                            label: "Discount Score",
                            value: score.discountScore,
                            max: 25,
                            desc: "Discount effectiveness",
                            tooltip: "Evaluates how well discounts drive sales for this product. Based on average sales lift from historical discount promotions. Higher scores mean discounts are more effective at boosting sales."
                        },
                        {
                            label: "Basket Score",
                            value: score.basketScore,
                            max: 25,
                            desc: "Cross-sell potential",
                            tooltip: "Assesses the product's cross-selling potential based on frequently bought together items. Higher scores indicate strong associations with other products, suggesting good placement opportunities."
                        },
                        {
                            label: "Demand Score",
                            value: score.demandScore,
                            max: 25,
                            desc: "Predicted demand",
                            tooltip: "Estimates expected demand based on historical average sales volume. Higher scores indicate consistently strong sales performance, suggesting reliable demand for the product."
                        },
                    ].map((item) => (
                        <div key={item.label} className="space-y-1">
                            <div className="flex items-center justify-between text-sm">
                                <div className="flex items-center gap-2 group relative">
                                    <span className="font-medium">{item.label}</span>
                                    <span className="text-xs opacity-60">({item.desc})</span>
                                    {/* Hover Tooltip */}
                                    <div className="absolute left-0 top-full mt-2 w-64 p-3 bg-gray-900 text-white text-xs rounded-lg shadow-lg opacity-0 invisible group-hover:opacity-100 group-hover:visible transition-all duration-200 z-10 pointer-events-none">
                                        <div className="font-semibold mb-1">{item.label}</div>
                                        <div className="opacity-90">{item.tooltip}</div>
                                        {/* Tooltip arrow */}
                                        <div className="absolute -top-1 left-4 w-2 h-2 bg-gray-900 transform rotate-45"></div>
                                    </div>
                                    <span className="text-xs text-blue-600 cursor-help">ℹ️</span>
                                </div>
                                <span className="font-semibold">
                                    {item.value}/{item.max}
                                </span>
                            </div>
                            <div className="w-full bg-gray-200 rounded-full h-2">
                                <div
                                    className="bg-blue-600 h-2 rounded-full transition-all"
                                    style={{ width: `${(item.value / item.max) * 100}%` }}
                                />
                            </div>
                        </div>
                    ))}
                </div>
            </div>
        </div>
    );
}


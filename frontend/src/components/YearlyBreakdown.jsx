import React, { useEffect, useMemo, useState } from "react";
import { getYearlyAnalysis } from "../services/apiClient.jsx";

/**
 * YearlyBreakdown Component
 * 
 * Modern, dynamic year-by-year analysis for a product (2015-2025).
 * Features:
 * - Year dropdown selector in top left
 * - Instant loading with optimized queries
 * - Prominent sales metrics with visual indicators
 * - Dynamic 2025 updates as new sales are added
 * - Week-by-week discount breakdown
 * 
 * Used in ProductDetailModal to provide historical context.
 */
export default function YearlyBreakdown({ productName }) {
    const [yearlyData, setYearlyData] = useState([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const [selectedYear, setSelectedYear] = useState(null);

    // Memoize available years to avoid recalculation
    const availableYears = useMemo(() => {
        if (!yearlyData || yearlyData.length === 0) return [];
        return yearlyData.map(d => d.year).sort((a, b) => b - a); // Descending (newest first)
    }, [yearlyData]);

    useEffect(() => {
        if (!productName) {
            setYearlyData([]);
            return;
        }

        setLoading(true);
        setError(null);

        getYearlyAnalysis({ productName })
            .then((data) => {
                const dataArray = Array.isArray(data) ? data : [];
                setYearlyData(dataArray);
                // Auto-select most recent year with data
                if (dataArray.length > 0) {
                    const latestYear = Math.max(...dataArray.map((d) => d.year || 0));
                    setSelectedYear(latestYear);
                } else {
                    setSelectedYear(new Date().getFullYear());
                }
            })
            .catch((e) => {
                setError(e.message);
                setYearlyData([]);
            })
            .finally(() => {
                setLoading(false);
            });
    }, [productName]);

    // Memoize selected year data for instant rendering
    const selectedYearData = useMemo(() => {
        if (!selectedYear || !yearlyData || yearlyData.length === 0) return null;
        return yearlyData.find((d) => d.year === selectedYear) || yearlyData[yearlyData.length - 1];
    }, [selectedYear, yearlyData]);

    if (!productName) {
        return (
            <div className="text-sm opacity-60 text-center py-8">
                Select a product to see year-by-year breakdown
            </div>
        );
    }

    if (loading) {
        return (
            <div className="flex items-center justify-center py-12">
                <div className="text-center">
                    <div className="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600 mb-3"></div>
                    <div className="text-sm opacity-70">Loading yearly breakdown...</div>
                </div>
            </div>
        );
    }

    if (error) {
        return (
            <div className="p-4 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
                <strong>Error:</strong> {error}
            </div>
        );
    }

    if (yearlyData.length === 0) {
        return (
            <div className="text-sm opacity-60 italic p-6 bg-mist rounded-lg border border-chrome text-center">
                No yearly data available for <span className="font-medium">{productName}</span>.
            </div>
        );
    }

    const currentYear = new Date().getFullYear();
    const isCurrentYear = selectedYear === currentYear;

    const currencyFmt = new Intl.NumberFormat(undefined, {
        style: "currency",
        currency: "USD",
        minimumFractionDigits: 0,
        maximumFractionDigits: 0,
    });

    // Calculate year-over-year change if we have previous year data
    const getYearOverYearChange = (year) => {
        const currentYearData = yearlyData.find((d) => d.year === year);
        const previousYearData = yearlyData.find((d) => d.year === year - 1);

        if (!currentYearData || !previousYearData || previousYearData.totalUnitsSold === 0) {
            return null;
        }

        const change = ((currentYearData.totalUnitsSold - previousYearData.totalUnitsSold) / previousYearData.totalUnitsSold) * 100;
        return change;
    };

    const yoyChange = selectedYearData ? getYearOverYearChange(selectedYear) : null;
    const isPositiveChange = yoyChange !== null && yoyChange > 0;

    return (
        <div className="space-y-6">
            {/* Year Selector - Dropdown in top left */}
            <div className="flex items-center gap-3">
                <label htmlFor="year-select" className="text-sm font-medium text-gray-700">
                    Year:
                </label>
                <select
                    id="year-select"
                    value={selectedYear || ''}
                    onChange={(e) => setSelectedYear(parseInt(e.target.value))}
                    className="px-4 py-2 border border-gray-300 rounded-lg bg-white text-sm font-medium text-gray-900 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-all"
                >
                    {availableYears.map((year) => (
                        <option key={year} value={year}>
                            {year} {year === currentYear ? '(Current)' : ''}
                        </option>
                    ))}
                </select>
            </div>

            {selectedYearData && (
                <div className="space-y-6 animate-fadeIn">
                    {/* Sales Metrics - Prominent Display */}
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                        {/* Total Units Sold */}
                        <div className="p-5 bg-gradient-to-br from-blue-50 to-indigo-50 border border-blue-200 rounded-xl shadow-sm hover:shadow-md transition-shadow">
                            <div className="flex items-center justify-between mb-2">
                                <div className="text-sm font-medium text-blue-700">Units Sold</div>
                                <div className="text-2xl">📦</div>
                            </div>
                            <div className="text-3xl font-bold text-blue-900 mb-1">
                                {selectedYearData.totalUnitsSold?.toLocaleString() || 0}
                            </div>
                            <div className="text-xs text-blue-600">
                                October {selectedYearData.year}
                            </div>
                            {yoyChange !== null && (
                                <div className={`mt-2 text-xs font-medium ${isPositiveChange ? 'text-green-600' : 'text-red-600'}`}>
                                    {isPositiveChange ? '↑' : '↓'} {Math.abs(yoyChange).toFixed(1)}% vs {selectedYearData.year - 1}
                                </div>
                            )}
                        </div>

                        {/* Total Revenue */}
                        <div className="p-5 bg-gradient-to-br from-green-50 to-emerald-50 border border-green-200 rounded-xl shadow-sm hover:shadow-md transition-shadow">
                            <div className="flex items-center justify-between mb-2">
                                <div className="text-sm font-medium text-green-700">Revenue</div>
                                <div className="text-2xl">💰</div>
                            </div>
                            <div className="text-3xl font-bold text-green-900 mb-1">
                                {currencyFmt.format(selectedYearData.totalRevenue || 0)}
                            </div>
                            <div className="text-xs text-green-600">
                                October {selectedYearData.year}
                            </div>
                            {isCurrentYear && (
                                <div className="mt-2 text-xs font-medium text-blue-600">
                                    ✨ Live data - updates dynamically
                                </div>
                            )}
                        </div>

                        {/* Discounts Summary */}
                        <div className="p-5 bg-gradient-to-br from-purple-50 to-pink-50 border border-purple-200 rounded-xl shadow-sm hover:shadow-md transition-shadow">
                            <div className="flex items-center justify-between mb-2">
                                <div className="text-sm font-medium text-purple-700">Active Discounts</div>
                                <div className="text-2xl">🏷️</div>
                            </div>
                            <div className="text-3xl font-bold text-purple-900 mb-1">
                                {selectedYearData.weekDiscounts ? Object.keys(selectedYearData.weekDiscounts).length : 0}
                            </div>
                            <div className="text-xs text-purple-600">
                                Weeks with discounts
                            </div>
                        </div>
                    </div>

                    {/* Week-by-Week Discounts - Enhanced Display */}
                    <div className="bg-white border border-chrome rounded-xl p-5 shadow-sm">
                        <div className="flex items-center gap-2 mb-4">
                            <div className="text-xl">🎯</div>
                            <h3 className="text-lg font-semibold text-gray-900">
                                Discounts in October {selectedYearData.year}
                            </h3>
                        </div>
                        {selectedYearData.weekDiscountDetails && Object.keys(selectedYearData.weekDiscountDetails).length > 0 ? (
                            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-3">
                                {Object.entries(selectedYearData.weekDiscountDetails)
                                    .sort(([weekA], [weekB]) => parseInt(weekA) - parseInt(weekB))
                                    .map(([week, discountInfo]) => {
                                        const discountPercent = discountInfo.discountPercent || discountInfo;
                                        const startDate = discountInfo.startDate ? new Date(discountInfo.startDate) : null;
                                        const endDate = discountInfo.endDate ? new Date(discountInfo.endDate) : null;
                                        
                                        // Format date range
                                        const formatDate = (date) => {
                                            if (!date) return '';
                                            return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
                                        };
                                        
                                        const dateRange = startDate && endDate 
                                            ? `${formatDate(startDate)} - ${formatDate(endDate)}`
                                            : startDate 
                                                ? formatDate(startDate)
                                                : '';
                                        
                                        return (
                                            <div
                                                key={`week_${week}_${selectedYearData.year}`}
                                                className="p-4 bg-gradient-to-br from-green-50 to-emerald-50 border border-green-300 rounded-lg hover:shadow-md transition-all hover:scale-105"
                                            >
                                                <div className="text-xs font-medium text-green-700 mb-1">
                                                    Week {week}
                                                </div>
                                                <div className="text-2xl font-bold text-green-900">
                                                    {typeof discountPercent === 'number' ? discountPercent : discountPercent}%
                                                </div>
                                                <div className="text-xs text-green-600 mt-1">
                                                    off
                                                </div>
                                                {dateRange && (
                                                    <div className="text-xs text-green-500 mt-2 pt-2 border-t border-green-200">
                                                        {dateRange}
                                                    </div>
                                                )}
                                            </div>
                                        );
                                    })}
                            </div>
                        ) : selectedYearData.weekDiscounts && Object.keys(selectedYearData.weekDiscounts).length > 0 ? (
                            // Fallback to old format if weekDiscountDetails not available
                            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-3">
                                {Object.entries(selectedYearData.weekDiscounts)
                                    .sort(([weekA], [weekB]) => parseInt(weekA) - parseInt(weekB))
                                    .map(([week, discountPercent]) => (
                                        <div
                                            key={`week_${week}_${selectedYearData.year}`}
                                            className="p-4 bg-gradient-to-br from-green-50 to-emerald-50 border border-green-300 rounded-lg hover:shadow-md transition-all hover:scale-105"
                                        >
                                            <div className="text-xs font-medium text-green-700 mb-1">
                                                Week {week}
                                            </div>
                                            <div className="text-2xl font-bold text-green-900">
                                                {discountPercent}%
                                            </div>
                                            <div className="text-xs text-green-600 mt-1">
                                                off
                                            </div>
                                        </div>
                                    ))}
                            </div>
                        ) : (
                            <div className="text-sm opacity-60 italic p-4 bg-mist rounded-lg border border-chrome text-center">
                                No active discounts recorded for October {selectedYearData.year}.
                            </div>
                        )}
                    </div>

                    {/* Current Year Indicator */}
                    {isCurrentYear && (
                        <div className="p-4 bg-gradient-to-r from-blue-50 to-indigo-50 border border-blue-200 rounded-lg">
                            <div className="flex items-center gap-2 text-sm text-blue-800">
                                <span className="text-lg">✨</span>
                                <span className="font-medium">
                                    This is the current year. Data updates dynamically as new sales are recorded.
                                </span>
                            </div>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}

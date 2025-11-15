import React, { useEffect, useState } from "react";
import { getProductTrend } from "../services/apiClient.jsx";

/**
 * ProductTrendGraph Component
 * 
 * Displays a mini sparkline graph showing trend for a product.
 * - Historical baseline (mode="seed"): 2015-2024 only (locked historical data)
 * - Current predictions (mode="live"): 2015-2025 including current year
 * 
 * Shows on hover over product names in forecast tables.
 * 
 * Features:
 * - Color-coded trend: green (increasing), red (decreasing), yellow (stable)
 * - Tooltip with peak year, average units, and growth rate
 * - Responsive SVG-based sparkline
 */
export default function ProductTrendGraph({ productName, className = "", mode = "seed" }) {
    const [trendData, setTrendData] = useState([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);

    useEffect(() => {
        if (!productName) {
            setTrendData([]);
            return;
        }

        setLoading(true);
        setError(null);

        getProductTrend({ productName, mode })
            .then((data) => {
                setTrendData(Array.isArray(data) ? data : []);
            })
            .catch((e) => {
                setError(e.message);
                setTrendData([]);
            })
            .finally(() => {
                setLoading(false);
            });
    }, [productName]);

    if (loading) {
        return (
            <div className={`text-xs opacity-60 ${className}`}>
                Loading trend...
            </div>
        );
    }

    if (error || trendData.length === 0) {
        return null; // Don't show anything if no data
    }

    // Calculate trend metrics
    const units = trendData.map((d) => d.units || 0);
    const minUnits = Math.min(...units);
    const maxUnits = Math.max(...units);
    const range = maxUnits - minUnits || 1; // Avoid division by zero

    // Determine trend direction (comparing first half vs second half)
    const firstHalf = units.slice(0, Math.floor(units.length / 2));
    const secondHalf = units.slice(Math.floor(units.length / 2));
    const firstAvg = firstHalf.reduce((a, b) => a + b, 0) / firstHalf.length;
    const secondAvg = secondHalf.reduce((a, b) => a + b, 0) / secondHalf.length;
    const growthRate = ((secondAvg - firstAvg) / firstAvg) * 100;

    // Color based on trend
    let trendColor = "#94a3b8"; // Default gray
    if (growthRate > 5) {
        trendColor = "#10b981"; // Green for increasing
    } else if (growthRate < -5) {
        trendColor = "#ef4444"; // Red for decreasing
    } else {
        trendColor = "#eab308"; // Yellow for stable
    }

    // Find peak year
    const peakIndex = units.indexOf(maxUnits);
    const peakYear = trendData[peakIndex]?.year || "N/A";
    const avgUnits = Math.round(units.reduce((a, b) => a + b, 0) / units.length);

    // SVG dimensions
    const width = 120;
    const height = 30;
    const padding = 2;

    // Generate path for sparkline
    const points = units.map((unit, index) => {
        const x = padding + (index * (width - 2 * padding)) / (units.length - 1 || 1);
        const y = height - padding - ((unit - minUnits) / range) * (height - 2 * padding);
        return `${x},${y}`;
    });

    const pathData = `M ${points.join(" L ")}`;

    return (
        <div
            className={`inline-flex items-center gap-2 ${className}`}
            title={`Trend (2015-${trendData[trendData.length - 1]?.year || 2025})\nPeak: ${peakYear} (${maxUnits.toLocaleString()} units)\nAverage: ${avgUnits.toLocaleString()} units\nGrowth: ${growthRate > 0 ? "+" : ""}${growthRate.toFixed(1)}%`}
        >
            <svg
                width={width}
                height={height}
                className="inline-block"
                viewBox={`0 0 ${width} ${height}`}
                preserveAspectRatio="none"
            >
                {/* Background area fill */}
                <path
                    d={`${pathData} L ${width - padding},${height - padding} L ${padding},${height - padding} Z`}
                    fill={trendColor}
                    fillOpacity="0.1"
                />
                {/* Trend line */}
                <path
                    d={pathData}
                    fill="none"
                    stroke={trendColor}
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                />
            </svg>
            <span
                className="text-xs font-medium"
                style={{ color: trendColor }}
            >
                {growthRate > 5 ? "↑" : growthRate < -5 ? "↓" : "→"}
            </span>
        </div>
    );
}


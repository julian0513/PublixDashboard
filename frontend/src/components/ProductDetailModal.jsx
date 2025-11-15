import React, { useState } from "react";
import Modal from "./Modal.jsx";
import BasketAnalysis from "./BasketAnalysis.jsx";
import DiscountInsights from "./DiscountInsights.jsx";
import YearlyBreakdown from "./YearlyBreakdown.jsx";
import HalloweenReadinessScore from "./HalloweenReadinessScore.jsx";

/**
 * ProductDetailModal Component
 * 
 * Shows comprehensive product information including:
 * - Top 10 frequently bought together items (automatically displayed)
 * - Discount insights and optimal discount (automatically displayed)
 * 
 * All data is automatically loaded when a product is selected.
 */
export default function ProductDetailModal({ open, onClose, productName }) {
    const [activeTab, setActiveTab] = useState("basket"); // "basket" | "discounts" | "yearly" | "readiness"

    if (!open) return null;

    return (
        <Modal 
            open={open} 
            onClose={onClose} 
            title={`Product Details: ${productName || "Select a Product"}`}
        >
            <div className="space-y-4">
                {!productName ? (
                    <div className="text-center py-8 text-sm opacity-60">
                        Please select a product to view details
                    </div>
                ) : (
                    <>
                        {/* Tabs */}
                        <div className="flex gap-2 border-b border-chrome">
                            <button
                                onClick={() => setActiveTab("basket")}
                                className={`px-4 py-2 text-sm font-medium ${
                                    activeTab === "basket"
                                        ? "border-b-2 border-black text-black"
                                        : "text-gray-600 hover:text-black"
                                }`}
                            >
                                🛒 Frequently Bought Together
                            </button>
                            <button
                                onClick={() => setActiveTab("discounts")}
                                className={`px-4 py-2 text-sm font-medium ${
                                    activeTab === "discounts"
                                        ? "border-b-2 border-black text-black"
                                        : "text-gray-600 hover:text-black"
                                }`}
                            >
                                💰 Discount Insights
                            </button>
                            <button
                                onClick={() => setActiveTab("yearly")}
                                className={`px-4 py-2 text-sm font-medium ${
                                    activeTab === "yearly"
                                        ? "border-b-2 border-black text-black"
                                        : "text-gray-600 hover:text-black"
                                }`}
                            >
                                📅 Year-by-Year (2015-2025)
                            </button>
                            <button
                                onClick={() => setActiveTab("readiness")}
                                className={`px-4 py-2 text-sm font-medium ${
                                    activeTab === "readiness"
                                        ? "border-b-2 border-black text-black"
                                        : "text-gray-600 hover:text-black"
                                }`}
                            >
                                🎃 Halloween Readiness
                            </button>
                        </div>

                        {/* Tab Content */}
                        <div className="min-h-[300px]">
                            {activeTab === "basket" && (
                                <BasketAnalysis productName={productName} topK={10} />
                            )}

                            {activeTab === "discounts" && (
                                <DiscountInsights productName={productName} />
                            )}

                            {activeTab === "yearly" && (
                                <YearlyBreakdown productName={productName} />
                            )}

                            {activeTab === "readiness" && (
                                <HalloweenReadinessScore productName={productName} />
                            )}
                        </div>
                    </>
                )}
            </div>
        </Modal>
    );
}


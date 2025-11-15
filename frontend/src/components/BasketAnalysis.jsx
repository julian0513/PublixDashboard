import React, { useEffect, useState } from "react";
import { getFrequentlyBoughtTogether } from "../services/apiClient.jsx";

/**
 * BasketAnalysis Component
 * 
 * Simple, modern Apple-inspired UI for displaying frequently bought together items.
 * Clean design with minimal visual elements - just product name and count.
 * 
 * @param {string} productName - The product to analyze
 * @param {number} topK - Maximum number of items to display (default: 10)
 * @param {number} minConfidence - Minimum confidence threshold (default: 0.1)
 */
export default function BasketAnalysis({ productName, topK = 10, minConfidence = 0.1 }) {
    const [associations, setAssociations] = useState([]);
    const [isPredicted, setIsPredicted] = useState(false);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);

    useEffect(() => {
        if (!productName) {
            setAssociations([]);
            return;
        }

        setLoading(true);
        setError(null);
        getFrequentlyBoughtTogether({ productName, minConfidence })
            .then((response) => {
                // Handle both DTO format (response.items) and legacy format (response is array)
                const data = response.items !== undefined ? response.items : (Array.isArray(response) ? response : []);
                const predicted = response.isPredicted !== undefined ? response.isPredicted : false;
                
                const limitedData = Array.isArray(data) ? data.slice(0, topK) : [];
                setAssociations(limitedData);
                setIsPredicted(predicted);
            })
            .catch((e) => {
                setError(e.message);
                setAssociations([]);
                setIsPredicted(false);
            })
            .finally(() => {
                setLoading(false);
            });
    }, [productName, minConfidence, topK]);

    if (!productName) {
        return (
            <div className="text-sm opacity-60 text-center py-8">
                Select a product to see frequently bought together items
            </div>
        );
    }

    if (loading) {
        return (
            <div className="flex items-center justify-center py-12">
                <div className="text-center">
                    <div className="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-gray-400 mb-3"></div>
                    <div className="text-sm opacity-70">Loading...</div>
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

    if (associations.length === 0) {
        return (
            <div className="p-6 bg-gray-50 border border-gray-200 rounded-xl text-center">
                <div className="text-sm font-medium text-gray-700 mb-1">
                    No frequently bought together items found
                </div>
                <div className="text-xs text-gray-500 mb-3">
                    This may indicate the product is new or has limited transaction history.
                </div>
                <div className="text-xs text-gray-400 font-mono bg-gray-100 p-2 rounded mt-2">
                    Product searched: {productName}
                </div>
            </div>
        );
    }

    return (
        <div className="space-y-3">
            <div className="flex items-center justify-between mb-4">
                <h3 className="text-lg font-semibold text-gray-900">
                    {isPredicted ? "Predicted " : ""}Frequently Bought Together
                </h3>
                <div className="text-xs text-gray-500">
                    {associations.length} {associations.length === 1 ? 'item' : 'items'}
                </div>
            </div>
            
            {/* Show prediction indicator if this is predicted data */}
            {isPredicted && (
                <div className="mb-3 p-3 bg-blue-50 border border-blue-200 rounded-lg">
                    <div className="flex items-center gap-2 text-sm text-blue-800">
                        <span className="text-base">✨</span>
                        <span className="font-medium">
                            Predicted Frequently Bought Together Items
                        </span>
                    </div>
                    <div className="text-xs text-blue-600 mt-1">
                        This product is new or has limited history. Showing predicted associations based on similar products.
                    </div>
                </div>
            )}

            <div className="space-y-1">
                {associations.map((item, idx) => (
                    <div
                        key={`${item.associatedProduct}_${idx}`}
                        className="flex items-center justify-between p-4 bg-white border border-gray-200 rounded-lg hover:border-gray-300 hover:shadow-sm transition-all"
                    >
                        <div className="flex-1 min-w-0">
                            <div className="text-sm font-medium text-gray-900 truncate">
                                {item.associatedProduct}
                            </div>
                        </div>
                        <div className="ml-4 text-sm text-gray-600 font-medium">
                            {item.coOccurrenceCount?.toLocaleString() || 0} times
                        </div>
                    </div>
                ))}
            </div>
        </div>
    );
}

/** src/services/apiClient.jsx
 * Clean, typed-ish fetch helpers for the Spring API gateway.
 *
 * Endpoints used here (served by backend):
 *   GET  /api/forecast?start=YYYY-MM-DD&end=YYYY-MM-DD&topK=10&mode=seed|live
 *   POST /api/ml/train?mode=seed|live
 *   GET  /api/sales?date=YYYY-MM-DD
 *   POST /api/sales
 *   DELETE /api/sales/{id}
 *   POST /api/sales/undo
 */


const rawBase = import.meta.env.VITE_API_BASE ?? "http://localhost:8080";
const API_BASE = rawBase.endsWith("/api")
    ? rawBase
    : `${rawBase.replace(/\/+$/, "")}/api`;

function buildHeaders(hasBody) {
    const h = {};
    if (hasBody) h["Content-Type"] = "application/json";
    return h;
}

async function request(path, { method = "GET", headers = {}, body, signal } = {}) {
    const res = await fetch(`${API_BASE}${path}`, {
        method,
        headers: { ...buildHeaders(!!body), ...headers },
        body: body ? JSON.stringify(body) : undefined,
        signal,
    });

    const isJson = (res.headers.get("content-type") || "").includes("application/json");
    const payload = isJson ? await res.json().catch(() => null) : await res.text();

    if (!res.ok) {
        const msg = isJson && payload && payload.message ? payload.message : res.statusText || "Request failed";
        throw new Error(`${method} ${path} → ${res.status} ${msg}`);
    }
    return payload;
}

// ---------------- Forecasts (ALL via scikit-learn) ----------------

/**
 * Get forecast from the ML service (proxied by backend).
 * mode: "seed" (frozen AI baseline) or "live" (updated model)
 */
export async function getForecast({ start, end, topK = 10, mode = "seed", signal } = {}) {
    if (!start || !end) throw new Error("start and end are required (YYYY-MM-DD)");
    const q = new URLSearchParams({ start, end, topK: String(topK), mode });
    return request(`/forecast?${q.toString()}`, { method: "GET", signal });
}

/**
 * Intraday end-of-day forecast for a single date.
 * Params:
 *   - date: 'YYYY-MM-DD'  (required)
 *   - asOf: 'YYYY-MM-DDTHH:MM:SS' (naive local, optional but recommended)
 *   - topK: number        (default 10)
 *   - mode: 'live'|'seed' (default 'live')
 *   - openHour, closeHour: store hours (defaults match backend)
 */
export async function getForecastIntraday({ date, asOf, topK = 10, mode = "live", openHour = 8, closeHour = 22 }, { signal } = {}) {
    if (!date) throw new Error("date is required (YYYY-MM-DD)");
    const params = new URLSearchParams({
        date_str: date,
        mode,
        top_k: String(topK),
        open_hour: String(openHour),
        close_hour: String(closeHour),
    });
    if (asOf) params.set("as_of", asOf);

    // Gateway path (Spring should proxy to FastAPI and add the ML secret)
    return request(`/ml/forecast_intraday?${params.toString()}`, { signal });
}

// ---------------- Training (user-triggered) ----------------

/**
 * Trigger model training.
 * mode: "seed" trains history_seed.joblib; "live" trains model_live.joblib
 */
export async function trainModel({ mode = "live", signal } = {}) {
    const q = new URLSearchParams({ mode });
    return request(`/ml/train?${q.toString()}`, { method: "POST", signal });
}

// ---------------- Sales (add/delete/list) ----------------

export async function listSalesByDate(date, { signal } = {}) {
    if (!date) throw new Error("date is required (YYYY-MM-DD)");
    const q = new URLSearchParams({ date });
    return request(`/sales?${q.toString()}`, { method: "GET", signal });
}

/**
 * Create a sales row. If createdAt is provided, the server will use it.
 * Arguments:
 *  - productName: string (required)
 *  - units: number (required, >=1)
 *  - date: 'YYYY-MM-DD' (required)
 *  - createdAt: 'YYYY-MM-DDTHH:MM:SS' (optional; naive local)
 */
export async function createSale({ productName, units, date, createdAt }, { signal } = {}) {
    if (!productName || productName.trim() === "") throw new Error("productName is required");
    if (units == null || Number.isNaN(Number(units))) throw new Error("units must be a number");
    if (!date) throw new Error("date is required (YYYY-MM-DD)");
    const body = { productName, units: Number(units), date };
    if (createdAt) body.createdAt = createdAt;
    return request(`/sales`, { method: "POST", body, signal });
}

export async function deleteSale(id, { signal } = {}) {
    if (!id) throw new Error("id is required");
    return request(`/sales/${id}`, { method: "DELETE", signal });
}

// Optional: only if you expose POST /api/sales/undo
export async function undoChange({ changeId }, { signal } = {}) {
    if (!changeId) throw new Error("changeId is required");
    return request(`/sales/undo`, { method: "POST", body: { changeId }, signal });
}

// ---------------- Basket Analysis (frequently bought together) ----------------

/**
 * Get frequently bought together items for a product.
 * productName: string (required)
 * minConfidence: number (optional, 0-1)
 */
export async function getFrequentlyBoughtTogether({ productName, minConfidence }, { signal } = {}) {
    if (!productName) throw new Error("productName is required");
    const params = new URLSearchParams({ productName });
    if (minConfidence != null) params.set("minConfidence", String(minConfidence));
    
    return await request(`/basket/frequently-bought-together?${params.toString()}`, { method: "GET", signal });
}

/**
 * Generate sample transaction data for testing.
 * startDate, endDate: 'YYYY-MM-DD' (optional, defaults to October 2025)
 * transactionsPerDay: number (optional, default 50)
 */
export async function generateSampleData({ startDate, endDate, transactionsPerDay = 50 }, { signal } = {}) {
    const params = new URLSearchParams({ transactionsPerDay: String(transactionsPerDay) });
    if (startDate) params.set("startDate", startDate);
    if (endDate) params.set("endDate", endDate);
    return request(`/basket/generate-sample-data?${params.toString()}`, { method: "POST", signal });
}

/**
 * Recalculate basket analysis from transaction data.
 * startDate, endDate: 'YYYY-MM-DD' (required)
 */
export async function recalculateBasketAnalysis({ startDate, endDate }, { signal } = {}) {
    if (!startDate || !endDate) throw new Error("startDate and endDate are required (YYYY-MM-DD)");
    const params = new URLSearchParams({ startDate, endDate });
    return request(`/basket/recalculate?${params.toString()}`, { method: "POST", signal });
}

// ---------------- Discount Analysis ----------------

/**
 * Get active discounts for a date.
 * date: 'YYYY-MM-DD' (optional, defaults to today)
 */
export async function getActiveDiscounts({ date }, { signal } = {}) {
    const params = new URLSearchParams();
    if (date) params.set("date", date);
    return request(`/discounts/active?${params.toString()}`, { method: "GET", signal });
}

/**
 * Get active discounts for a specific product.
 * productName: string (required)
 * date: 'YYYY-MM-DD' (optional, defaults to today)
 */
export async function getActiveDiscountsForProduct({ productName, date }, { signal } = {}) {
    if (!productName) throw new Error("productName is required");
    const params = new URLSearchParams({ productName });
    if (date) params.set("date", date);
    return request(`/discounts/active-for-product?${params.toString()}`, { method: "GET", signal });
}

/**
 * Get discount effectiveness data for a product.
 * productName: string (required)
 * startDate, endDate: 'YYYY-MM-DD' (optional)
 */
export async function getDiscountEffectiveness({ productName, startDate, endDate }, { signal } = {}) {
    if (!productName) throw new Error("productName is required");
    const params = new URLSearchParams({ productName });
    if (startDate) params.set("startDate", startDate);
    if (endDate) params.set("endDate", endDate);
    return request(`/discounts/effectiveness?${params.toString()}`, { method: "GET", signal });
}

/**
 * Find the optimal discount percentage for a product.
 * productName: string (required)
 */
export async function getOptimalDiscount({ productName }, { signal } = {}) {
    if (!productName) throw new Error("productName is required");
    const params = new URLSearchParams({ productName });
    return request(`/discounts/optimal?${params.toString()}`, { method: "GET", signal });
}

/**
 * Create a new discount promotion.
 * productName: string (required)
 * discountPercent: number (required, 0-100)
 * startDate, endDate: 'YYYY-MM-DD' (required)
 * description: string (optional)
 */
export async function createDiscount({ productName, discountPercent, startDate, endDate, description }, { signal } = {}) {
    if (!productName) throw new Error("productName is required");
    if (discountPercent == null) throw new Error("discountPercent is required");
    if (!startDate || !endDate) throw new Error("startDate and endDate are required");
    const body = { productName, discountPercent: Number(discountPercent), startDate, endDate };
    if (description) body.description = description;
    return request(`/discounts`, { method: "POST", body, signal });
}

// ---------------- Yearly Analysis ---------------- 

/**
 * Get year-by-year analysis for a product (2015-2024).
 * Returns top 10 items, discounts, and sales data for each year.
 * productName: string (required)
 */
export async function getYearlyAnalysis({ productName }, { signal } = {}) {
    if (!productName) throw new Error("productName is required");
    const params = new URLSearchParams({ productName });
    return request(`/yearly/analysis?${params.toString()}`, { method: "GET", signal });
}

/**
 * Get trend data for a product.
 * - mode="seed" (historical baseline): Returns 2015-2024 only (locked historical data)
 * - mode="live" (current predictions): Returns 2015-2025 including current year
 * 
 * productName: string (required)
 * mode: string (optional, default "seed") - "seed" for historical, "live" for current
 */
export async function getProductTrend({ productName, mode = "seed" }, { signal } = {}) {
    if (!productName) throw new Error("productName is required");
    const params = new URLSearchParams({ productName });
    if (mode) params.set("mode", mode);
    return request(`/sales/trend?${params.toString()}`, { method: "GET", signal });
}

// ---------------- Halloween Readiness ---------------- 

/**
 * Get Halloween Readiness Score for a product (0-100).
 * Returns composite score with breakdown of factors.
 * productName: string (required)
 */
export async function getHalloweenReadiness({ productName }, { signal } = {}) {
    if (!productName) throw new Error("productName is required");
    const params = new URLSearchParams({ productName });
    return request(`/halloween/readiness?${params.toString()}`, { method: "GET", signal });
}
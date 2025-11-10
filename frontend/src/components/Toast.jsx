import React, { useEffect, useRef, useState } from "react";

const AUTO_HIDE_MS = 2000; // how long it stays fully visible
const EXIT_MS = 500;       // must match the CSS transition duration

export default function Toast({ show, title, message, onClose }) {
    const [visible, setVisible] = useState(show); // drives enter/exit animation
    const hideTimerRef = useRef(null);
    const closeTimerRef = useRef(null);

    // Sync animation + timers with `show`
    useEffect(() => {
        // Clear any previous timers
        clearTimeout(hideTimerRef.current);
        clearTimeout(closeTimerRef.current);

        if (show) {
            // enter: make it visible, then schedule auto-hide
            setVisible(true);
            hideTimerRef.current = setTimeout(() => {
                // start exit animation
                setVisible(false);
                // after animation ends, tell parent to hide (if still shown)
                closeTimerRef.current = setTimeout(() => {
                    // Only invoke onClose if parent hasn't already hidden it
                    if (show) onClose?.();
                }, EXIT_MS);
            }, AUTO_HIDE_MS);
        } else {
            // parent hid it early → begin/keep exit state; no onClose needed
            setVisible(false);
        }

        return () => {
            clearTimeout(hideTimerRef.current);
            clearTimeout(closeTimerRef.current);
        };
    }, [show, onClose]);

    // Fully unmount only when both `show` and `visible` are false (after exit)
    if (!show && !visible) return null;

    return (
        <div className="fixed inset-0 pointer-events-none flex items-start justify-center mt-6 z-50">
            <div
                className={`pointer-events-auto max-w-md w-full mx-4 rounded-xl shadow-lg border border-chrome bg-white overflow-hidden transform transition-all duration-500
        ${visible ? "opacity-100 scale-100 translate-y-0" : "opacity-0 scale-95 -translate-y-1"}`}
                role="alert"
                aria-live="assertive"
                aria-atomic="true"
            >
                <div className="px-4 py-3 border-b border-chrome font-semibold">{title}</div>
                <div className="px-4 py-3 text-sm">{message}</div>
            </div>
        </div>
    );
}

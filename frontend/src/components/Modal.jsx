import React, { useEffect, useId, useRef } from "react";

export default function Modal({
                                  open,
                                  title,
                                  children,
                                  onClose = () => {},
                                  primary = true,
                                  onPrimary,
                                  primaryLabel = "Confirm",
                              }) {
    const headingId = useId();
    const dialogRef = useRef(null);
    const showPrimary = primary !== false && typeof onPrimary === "function";

    // Close on Escape
    useEffect(() => {
        if (!open) return;
        const onKey = (e) => {
            if (e.key === "Escape") onClose();
        };
        window.addEventListener("keydown", onKey);
        return () => window.removeEventListener("keydown", onKey);
    }, [open, onClose]);

    // Lock background scroll while open
    useEffect(() => {
        if (!open) return;
        const prev = document.body.style.overflow;
        document.body.style.overflow = "hidden";
        return () => {
            document.body.style.overflow = prev;
        };
    }, [open]);

    // Move focus into the dialog
    useEffect(() => {
        if (!open) return;
        const root = dialogRef.current;
        if (!root) return;
        const focusTarget =
            root.querySelector("[data-autofocus]") ||
            root.querySelector("button, [href], input, select, textarea, [tabindex]:not([tabindex='-1'])") ||
            root;
        focusTarget?.focus();
    }, [open]);

    if (!open) return null;

    return (
        <div className="fixed inset-0 z-50">
            {/* Backdrop */}
            <div
                className="absolute inset-0 bg-black/20"
                onClick={onClose}
                aria-hidden="true"
            />

            {/* Dialog */}
            <div className="absolute inset-0 flex items-center justify-center p-4">
                <div
                    ref={dialogRef}
                    className="w-full max-w-4xl bg-white rounded-2xl shadow-soft border border-chrome outline-none flex flex-col"
                    role="dialog"
                    aria-modal="true"
                    aria-labelledby={title ? headingId : undefined}
                    aria-label={title ? undefined : "Dialog"}
                    tabIndex={-1}
                >
                    {title && (
                        <div className="px-5 py-4 border-b border-chrome flex-shrink-0">
                            <h3 id={headingId} className="text-lg font-semibold">
                                {title}
                            </h3>
                        </div>
                    )}

                    <div className="p-6 flex-1 min-h-0">{children}</div>

                    <div className="px-5 py-4 border-t border-chrome flex gap-3 justify-end flex-shrink-0">
                        <button
                            type="button"
                            className="px-4 py-2 rounded-lg bg-chrome"
                            onClick={onClose}
                        >
                            Cancel
                        </button>

                        {showPrimary && (
                            <button
                                type="button"
                                className="px-4 py-2 rounded-lg bg-black text-white"
                                onClick={onPrimary}
                                data-autofocus
                            >
                                {primaryLabel}
                            </button>
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
}

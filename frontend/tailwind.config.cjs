/** @type {import('tailwindcss').Config} */
module.exports = {
    content: ["./index.html", "./src/**/*.{js,jsx}"],
    theme: {
        extend: {
            fontFamily: {
                ui: [
                    "-apple-system","BlinkMacSystemFont","SF Pro Text","Segoe UI",
                    "Roboto","Helvetica Neue","Arial","system-ui","sans-serif"
                ],
            },
            colors: { ink:"#0b0b0c", mist:"#f5f6f7", chrome:"#e9eaec" },
            boxShadow: { soft:"0 10px 30px rgba(0,0,0,0.06)" },
            borderRadius: { xl:"0.9rem","2xl":"1.25rem" },
        },
    },
    plugins: [],
};

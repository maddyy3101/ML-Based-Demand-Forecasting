/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,jsx}"],
  theme: {
    extend: {
      colors: {
        pg: {
          base: "var(--bg-base)",
          surface: "var(--bg-surface)",
          elevated: "var(--bg-elevated)",
          overlay: "var(--bg-overlay)",
          border: "var(--border)",
          borderHi: "var(--border-hi)",
          navy: "var(--navy)",
          navyLight: "var(--navy-light)",
          orange: "var(--orange)",
          green: "var(--green)",
          amber: "var(--amber)",
          red: "var(--red)",
          purple: "var(--purple)",
        },
      },
      fontFamily: {
        display: ["Sora", "sans-serif"],
        body: ["Manrope", "sans-serif"],
        mono: ["'JetBrains Mono'", "monospace"],
      },
    },
  },
  plugins: [],
};

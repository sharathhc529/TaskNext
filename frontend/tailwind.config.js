/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        cyber: {
          dark: '#0a0e17',
          card: '#111827',
          border: '#1f2937',
          accent: '#06b6d4',
          danger: '#ef4444',
          warning: '#f59e0b',
          success: '#10b981',
          muted: '#9ca3af',
        }
      }
    },
  },
  plugins: [],
}

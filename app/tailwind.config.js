/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  darkMode: 'media',
  theme: { extend: { colors: { accent: 'var(--accent)', ink: 'var(--ink)', mute: 'var(--mute)', line: 'var(--line)', surface: 'var(--card)', page: 'var(--bg)', ok: 'var(--ok)', bad: 'var(--bad)' } } },
  plugins: [],
};

/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./src/**/*.{html,ts}'],
  // Preflight off: global styles.scss and PrimeNG own the base layer.
  corePlugins: { preflight: false },
  theme: {
    extend: {
      colors: {
        ink: 'var(--ink)',
        muted: 'var(--muted)',
        line: 'var(--line)',
        accent: 'var(--accent)',
        'accent-2': 'var(--accent-2)',
        panel: 'var(--panel)',
        danger: 'var(--danger)'
      },
      fontFamily: {
        sans: ['IBM Plex Sans', 'sans-serif'],
        display: ['IBM Plex Serif', 'serif']
      },
      borderRadius: {
        brand: 'var(--radius)'
      }
    }
  },
  plugins: []
};

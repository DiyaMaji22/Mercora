/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    './app/**/*.{js,ts,jsx,tsx,mdx}',
    './components/**/*.{js,ts,jsx,tsx,mdx}',
  ],
  theme: {
    extend: {
      colors: {
        // "Auction floor" palette: deep ink for structure/trust, amber as
        // the one urgency signal (stock countdowns, hold timers), a
        // distinct red-orange reserved only for expiry/danger states so it
        // never competes with the amber for attention.
        ink: {
          DEFAULT: '#0F1B2D',
          soft: '#17253B',
          muted: '#243450',
        },
        paper: {
          DEFAULT: '#EDEFF2',
          raised: '#FFFFFF',
        },
        signal: {
          DEFAULT: '#FFB020',
          dim: '#B9821A',
        },
        danger: {
          DEFAULT: '#E14F3D',
          dim: '#B93F30',
        },
        success: {
          DEFAULT: '#2FA36B',
          dim: '#227A50',
        },
        muted: '#7C8798',
      },
      fontFamily: {
        display: ['"Helvetica Neue"', 'Arial', 'system-ui', 'sans-serif'],
        body: ['-apple-system', 'BlinkMacSystemFont', 'Segoe UI', 'Roboto', 'sans-serif'],
        mono: ['"SF Mono"', 'ui-monospace', 'Menlo', 'monospace'],
      },
      letterSpacing: {
        tightest: '-0.04em',
      },
    },
  },
  plugins: [],
};

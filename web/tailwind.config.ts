import type { Config } from 'tailwindcss';

/**
 * Light, low-contrast palette. The token names are the ones the pages already
 * use — `ink` is the surface ramp, `brand` is the single ink accent, `slate` is
 * the text ramp — so a restyle stays in this file instead of touching markup.
 */
const config: Config = {
  content: ['./src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // surfaces: 950 page, 900 card, 850 subtle, 800 hover, 700/600 strokes
        ink: {
          950: '#F5F6F8',
          900: '#FFFFFF',
          850: '#F7F8FA',
          800: '#F1F3F6',
          700: '#E6E9EE',
          600: '#D5DAE1',
        },
        // the one accent: near-black ink (buttons, active nav, links)
        brand: {
          400: '#2A303A',
          500: '#16191F',
          600: '#0B0E12',
        },
        // text ramp: 100 strongest → 700 faintest
        slate: {
          50: '#0E1116',
          100: '#14171C',
          200: '#22272F',
          300: '#3A424D',
          400: '#5B6472',
          500: '#7B8593',
          600: '#9AA3B0',
          700: '#C3C9D2',
        },
        line: '#E7EAEF',
      },
      boxShadow: {
        // soft, neutral — no coloured glow
        glow: '0 1px 2px rgba(16, 20, 27, 0.06)',
        card: '0 1px 2px rgba(16, 20, 27, 0.04), 0 8px 24px -16px rgba(16, 20, 27, 0.18)',
      },
      borderRadius: {
        xl: '0.75rem',
        '2xl': '0.875rem',
      },
    },
  },
  plugins: [],
};

export default config;

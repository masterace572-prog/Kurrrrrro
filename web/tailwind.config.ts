import type { Config } from 'tailwindcss';

/**
 * Dark palette. The token names are the ones the pages already use — `ink` is
 * the surface ramp, `brand` is the single light accent, `slate` is the text
 * ramp — so a restyle stays in this file instead of touching markup.
 */
const config: Config = {
  content: ['./src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // surfaces: 950 page, 900 card, 850 subtle, 800 hover, 700/600 strokes
        ink: {
          950: '#0F1115',
          900: '#161A1F',
          850: '#1B2026',
          800: '#22272E',
          700: '#2C323A',
          600: '#3A414B',
        },
        // the one accent: off-white (buttons, active nav, links)
        brand: {
          400: '#B9C6D8',
          500: '#E7EBF1',
          600: '#F7F9FB',
        },
        // text ramp: 50 strongest → 700 faintest
        slate: {
          50: '#F7F9FB',
          100: '#ECEFF3',
          200: '#D8DDE5',
          300: '#B9C0CB',
          400: '#939CAA',
          500: '#7B8494',
          600: '#646D7C',
          700: '#4A525F',
        },
        line: '#262C34',
      },
      boxShadow: {
        // soft, neutral — no coloured glow
        glow: '0 1px 2px rgba(0, 0, 0, 0.4)',
        card: '0 1px 2px rgba(0, 0, 0, 0.3), 0 12px 32px -20px rgba(0, 0, 0, 0.8)',
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

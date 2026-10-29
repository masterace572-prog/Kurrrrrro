import type { Config } from 'tailwindcss';

const config: Config = {
  content: ['./src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        ink: {
          950: '#08090d',
          900: '#0d0f14',
          850: '#12141b',
          800: '#171a22',
          700: '#232734',
          600: '#2f3444',
        },
        brand: {
          400: '#ff5d73',
          500: '#ff3355',
          600: '#e01d3f',
        },
      },
      boxShadow: {
        glow: '0 0 0 1px rgba(255,51,85,0.25), 0 12px 40px -12px rgba(255,51,85,0.45)',
      },
    },
  },
  plugins: [],
};

export default config;

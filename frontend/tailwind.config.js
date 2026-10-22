/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./src/**/*.{html,ts}'],
  theme: {
    extend: {
      colors: {
        brand: {
          400: '#4ade80',
          500: '#22c55e',
          600: '#16a34a',
          950: '#052e16',
        },
      },
    },
  },
  plugins: [],
};

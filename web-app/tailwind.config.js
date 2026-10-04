/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      fontFamily: {
        sans: ['Inter', 'system-ui', '-apple-system', 'sans-serif'],
        mono: ['"SFMono-Regular"', 'ui-monospace', 'SFMono-Regular', 'Menlo', 'Consolas', 'monospace'],
      },
      colors: {
        bg: '#050505',
        panel: '#0a0a0a',
        edge: '#18181b',
      },
    },
  },
  plugins: [require('daisyui')],
  daisyui: {
    themes: [
      {
        night: {
          'color-scheme': 'dark',
          primary: '#0070F3',
          'primary-content': '#ffffff',
          secondary: '#10B981',
          'secondary-content': '#052e1b',
          accent: '#7c3aed',
          'accent-content': '#f8f7ff',
          neutral: '#18181b',
          'neutral-content': '#d4d4d8',
          'base-100': '#050505',
          'base-200': '#0a0a0a',
          'base-300': '#1c1c1f',
          'base-content': '#e4e4e7',
          info: '#0ea5e9',
          success: '#10B981',
          warning: '#f59e0b',
          error: '#ef4444',
          'rounded-box': '1rem',
          'rounded-btn': '0.75rem',
          'rounded-badge': '9999px',
        },
      },
      'black',
    ],
  },
}
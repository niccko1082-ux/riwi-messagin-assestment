import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// sockjs-client referencia `global` (Node); en el navegador se mapea a window.
export default defineConfig({
  plugins: [react()],
  define: { global: 'window' },
});

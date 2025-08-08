import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react-swc'
import { viteSingleFile } from 'vite-plugin-singlefile'

// Build into ../src/main/resources/web so it's packed in the mod jar
export default defineConfig({
  plugins: [react(), viteSingleFile()],
  build: {
    outDir: '../src/main/resources',
    emptyOutDir: false,
    assetsInlineLimit: 100000000,
    cssCodeSplit: false,
    rollupOptions: {
      output: {
        manualChunks: undefined
      }
    }
  },
  server: {
    port: 4444,
    // Listen on all interfaces; actual Host header will still be validated below
    host: true,
    open: false,
    // Allow localhost, LAN access, and any subdomain of captain.dum88.nl
    allowedHosts: true,
    // Proxy API calls to the backend on port 4445
    proxy: {
      '/api': {
        target: 'http://localhost:4445',
        changeOrigin: true,
        secure: false,
        // If your backend doesn't include the /api prefix, uncomment the next line
        // rewrite: (path) => path.replace(/^\/api/, '')
      },
    },
  },
  // Ensure "vite preview" uses the same host checks when testing builds
  preview: {
    host: true,
    allowedHosts: [
      'localhost',
      '127.0.0.1',
      '0.0.0.0',
      'fastfile.captain.dum88.nl',
      '.captain.dum88.nl'
    ],
  }
})

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
  host: true,
  allowedHosts: ['gokg8wc44c00w08gg0wgscc0.captain.dum88.nl'],
    proxy: {
      '/api': 'http://localhost:4445'
    }
  }
})

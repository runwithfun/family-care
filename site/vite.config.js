import { defineConfig } from 'vite'

export default defineConfig({
  base: '/family-care/',
  publicDir: 'public',
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    assetsDir: 'assets',
    assetsInlineLimit: 0,
  },
})

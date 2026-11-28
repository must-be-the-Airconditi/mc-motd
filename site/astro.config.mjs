import { defineConfig } from 'astro/config';

export default defineConfig({
  site: 'https://must-be-the-airconditi.github.io',
  base: '/mc-motd',
  outDir: 'dist',
  build: { assets: '_assets' },
});

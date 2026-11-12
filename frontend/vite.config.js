import { defineConfig } from 'vite';

export default defineConfig({
  // GitHub Pages hosts project sites below /<repository-name>/.
  base: process.env.GITHUB_PAGES ? '/Forensight-/' : '/',
});

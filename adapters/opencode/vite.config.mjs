import { defineConfig } from "vite"
import { gui } from "@opencode/plugin/gui/vite"
// Source has already passed real TypeScript checking/emission. No native esbuild process needed.
export default defineConfig({
  plugins: [gui({ entry: "build/src/gui.js" })], esbuild: false,
  build: { target: "esnext", minify: false, cssMinify: false },
})

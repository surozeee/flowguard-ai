import path from "node:path";
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "src"),
    },
  },
  server: {
    port: 5173,
    proxy: {
      "/api": process.env.VITE_API_GATEWAY_URL ?? "http://localhost:8088",
      "/oauth2": process.env.VITE_API_GATEWAY_URL ?? "http://localhost:8088",
    },
  },
});

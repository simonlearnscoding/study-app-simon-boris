import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],

  server: {
    fs: {
      allow: [
        // Default Vite allowed dirs
        "/home/simon/code/", // Your project root
        // Add fontsource
        "/home/simon/code/node_modules/@fontsource/roboto",
      ],
    },
  },
});

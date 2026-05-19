import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
export default defineConfig({
    plugins: [react()],
    build: {
        outDir: 'dist',
        assetsDir: 'assets',
        cssCodeSplit: false,
        rollupOptions: {
            output: {
                inlineDynamicImports: true,
                entryFileNames: 'app.js',
                assetFileNames: function (assetInfo) { var _a; return ((_a = assetInfo.name) === null || _a === void 0 ? void 0 : _a.endsWith('.css')) ? 'app.css' : 'assets/[name][extname]'; },
            },
        },
    },
});

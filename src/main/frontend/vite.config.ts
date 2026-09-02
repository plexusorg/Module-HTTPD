import {svelte} from '@sveltejs/vite-plugin-svelte';
import tailwindcss from '@tailwindcss/vite';
import {defineConfig} from 'vite';
import {fileURLToPath, URL} from 'node:url';

export default defineConfig({
    base: '/app/',
    plugins: [svelte(), tailwindcss()],
    resolve: {
        alias: {
            $lib: fileURLToPath(new URL('./src/lib', import.meta.url))
        }
    },
    build: {
        outDir: '../../../build/generated/frontend-resources/httpd/app',
        emptyOutDir: true
    }
});

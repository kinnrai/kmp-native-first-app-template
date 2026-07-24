import { loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

export default defineConfig(({ mode }) => {
  const environment = loadEnv(mode, '.', '');
  return {
    root: '.',
    plugins: [react()],
    build: {
      outDir: 'dist',
      emptyOutDir: true,
      rolldownOptions: {
        output: {
          codeSplitting: {
            groups: [
              {
                name: 'react',
                test: /node_modules[\\/](react|react-dom|scheduler)/,
                priority: 40,
              },
              {
                name: 'kotlin-runtime',
                test: /productionLibrary[\\/](kotlin-|kotlinx-|seskar)/,
                priority: 30,
              },
              {
                name: 'ktor',
                test: /productionLibrary[\\/]ktor-/,
                priority: 25,
              },
              {
                name: 'shared-logic',
                test: /app[\\/]sharedLogic[\\/]build[\\/]dist[\\/]js[\\/]productionLibrary/,
                priority: 20,
              },
            ],
          },
        },
      },
    },
    server: {
      port: 5173,
      strictPort: true,
      proxy: {
        '/api': {
          target: environment.TASK_API_DEV_SERVER || 'http://127.0.0.1:8080',
          changeOrigin: true,
        },
      },
    },
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
    },
  };
});

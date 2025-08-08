# SynM Frontend (React + Vite + Mantine)

- Dev: `npm install` then `npm run dev` (proxy to http://localhost:4444 for /api)
- Build: `npm run build` (outputs to ../src/main/resources/web for packaging in mod jar)
- Demo: `npm run demo` starts a mock API on http://localhost:4444 and Vite dev server on http://localhost:5173

The backend web server should serve `/` from `index.html` in `src/main/resources/web`.

Demo notes:
- The mock API lives in `mock/server.mjs` and implements minimal endpoints used by the UI: `/api/auth/status`, `/api/auth/init`, `/api/players`, `/api/player/:uuid`, and `/api/action/:action`.
- The dev server proxies `/api` to `http://localhost:4444` (see `vite.config.ts`).
- Stop the demo with Ctrl+C (it will terminate both the API and dev server).

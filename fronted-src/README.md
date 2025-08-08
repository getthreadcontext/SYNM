# SynM Frontend (React + Vite + Mantine)

- Dev: `npm install` then `npm run dev` (proxy to http://localhost:4444 for /api)
- Build: `npm run build` (outputs to ../src/main/resources/web for packaging in mod jar)
- Demo: `npm run demo` starts a mock API on http://localhost:4445 and Vite dev server on http://localhost:4444

The backend web server should serve `/` from `index.html` in `src/main/resources/web`.

Demo notes:
- The mock API lives in `mock/server.mjs` and implements minimal endpoints used by the UI: `/api/auth/status`, `/api/auth/init`, `/api/players`, `/api/player/:uuid`, and `/api/action/:action`.
- The dev server proxies `/api` to `http://localhost:4444` (see `vite.config.ts`).
- Stop the demo with Ctrl+C (it will terminate both the API and dev server).

Deploying to Coolify (container)
1. In `fronted-src`, the provided `Dockerfile` builds and runs the mock API on port 4444.
2. In Coolify, create a new Application from your Git repo and select the `fronted-src` directory as the context.
3. Build settings:
	- Dockerfile: `fronted-src/Dockerfile`
	- Exposed port: 4444
	- Build context: repository root (the Dockerfile already references fronted-src/ paths)
4. Deploy. Coolify will run `node mock/server.mjs` and expose `/:4444`. The frontend is not served by this container. You can:
	- Use Vite preview (not ideal for prod) or
	- Serve `npm run build:web` output (fronted-src/dist) using a static site container (e.g., nginx) and proxy `/api` to this service.

Recommended production setup
- Service A (API): Use the provided Dockerfile to run the API at port 4445.
- Service B (Static web): An nginx container serving the built SPA. Example `nginx.conf`:
  - Root: `/usr/share/nginx/html`
  - Try files: `try_files $uri /index.html;`
  - Proxy `/api` to Service A at `http://synm-demo-api:4444`.

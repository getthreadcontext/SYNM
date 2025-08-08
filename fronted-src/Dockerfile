# Build stage
FROM node:20-alpine AS build
WORKDIR /app
# When the build context is the repo root, reference files under fronted-src/
COPY fronted-src/package.json fronted-src/package-lock.json* ./
RUN npm ci --include=dev || npm install
COPY fronted-src/. .
# Build a standalone web bundle for optional static hosting (not used by demo)
RUN npm run build:web || true

# Runtime stage
FROM node:20-alpine AS runner
WORKDIR /app
ENV NODE_ENV=development
# Defaults suitable for Coolify
ENV VITE_DEMO=1 \
	PORT=4444 \
	API_TARGET=http://127.0.0.1:4445 \
	PROXY_DISABLE=0
# Install dependencies (including dev) because demo uses Vite
COPY fronted-src/package.json fronted-src/package-lock.json* ./
RUN npm ci || npm install
# Copy runtime sources (include vite.config.ts, index.html, src, mock, etc.)
COPY fronted-src/. .
# Expose the web (4444) and API (4445) ports
EXPOSE 4444 4445
# Start both API and Vite dev server via demo script (proxy enabled by default)
CMD ["sh", "-lc", "npm run demo"]

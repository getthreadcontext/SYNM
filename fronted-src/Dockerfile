# Build stage
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci --include=dev || npm install
COPY . .
# Build a standalone web bundle for optional static hosting
RUN npm run build:web

# Runtime stage
FROM node:20-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
# Install only production dependencies for the API
COPY package.json package-lock.json* ./
RUN npm ci --omit=dev || npm install --omit=dev
# Copy runtime sources
COPY mock ./mock
# Expose the web (4444) and API (4445) ports
EXPOSE 4444 4445
# Start both API and Vite dev server via demo script
CMD ["npm", "run", "demo"]

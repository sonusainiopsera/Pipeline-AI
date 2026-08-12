# ── Stage 1: Build ───────────────────────────────────────────────────────────
FROM node:22-alpine AS builder

WORKDIR /app

# Copy package manifests first for cached dependency layer
COPY package*.json ./
RUN npm ci

COPY . .

# Accept API URL at build time (used when VITE_API_URL is set in the environment)
ARG VITE_API_URL
ENV VITE_API_URL=$VITE_API_URL

RUN npm run build

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
# Node.js, npm, node_modules, and TypeScript sources are discarded
FROM nginx:stable-alpine

# Remove the default nginx config; replaced by our SPA-routing config
RUN rm -f /etc/nginx/conf.d/default.conf

COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]

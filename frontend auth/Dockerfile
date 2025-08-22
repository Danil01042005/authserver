FROM node:20-alpine AS build
WORKDIR /app

ARG NPM_REGISTRY=https://registry.npmmirror.com/
ARG HTTP_PROXY
ARG HTTPS_PROXY
ENV npm_config_registry=$NPM_REGISTRY \
    npm_config_fetch_timeout=120000 \
    npm_config_fetch_retries=5 \
    npm_config_https_proxy=$HTTPS_PROXY \
    npm_config_proxy=$HTTP_PROXY

COPY package*.json ./
RUN npm config set registry "$NPM_REGISTRY" \
    && if [ -n "$HTTP_PROXY" ]; then npm config set proxy "$HTTP_PROXY"; npm config set http-proxy "$HTTP_PROXY"; fi \
    && if [ -n "$HTTPS_PROXY" ]; then npm config set https-proxy "$HTTPS_PROXY"; fi \
    && npm install --legacy-peer-deps --no-audit --no-fund

COPY . .
ARG VITE_API_BASE
ENV VITE_API_BASE=$VITE_API_BASE
RUN npm run build


FROM nginx:alpine AS runtime
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]



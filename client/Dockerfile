# ---------- build ----------
FROM node:20-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# Same-origin: фронт ходит на /api, который nginx проксирует в API-контейнер.
# Если хочешь, чтобы фронт ходил на отдельный домен API — передай build-arg:
#   docker build --build-arg VITE_API_URL=https://api.example.com ...
ARG VITE_API_URL=""
ENV VITE_API_URL=$VITE_API_URL
RUN npm run build

# ---------- serve ----------
FROM nginx:1.27-alpine AS runtime
COPY --from=build /app/dist /usr/share/nginx/html
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80

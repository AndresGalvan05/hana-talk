# Static assets are arch-independent: the node stage always runs natively on
# the build host ($BUILDPLATFORM), only the nginx stage is per-target-arch.
# The ARG default keeps the legacy (non-BuildKit) builder working locally.
ARG BUILDPLATFORM=linux/amd64
FROM --platform=$BUILDPLATFORM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# Empty API base makes the app call same-origin /api/... paths; in the cluster
# the ingress routes those to core-api, so production never crosses origins.
ENV VITE_API_URL=""
RUN npm run build

FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80

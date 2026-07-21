# Go cross-compiles natively via GOOS/GOARCH, so unlike core-api's Gradle
# build (which needs a $BUILDPLATFORM trick to avoid running the JVM build
# under QEMU emulation), the builder here can just run on $BUILDPLATFORM and
# target $TARGETARCH directly — no emulation at any point. The ARG defaults
# keep the legacy (non-BuildKit) builder working for local compose, same as
# core-api's Dockerfile.
ARG BUILDPLATFORM=linux/amd64
ARG TARGETARCH=amd64
FROM --platform=$BUILDPLATFORM golang:1-alpine AS builder
WORKDIR /app
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux GOARCH=$TARGETARCH go build -o /event-worker ./cmd/event-worker

FROM alpine:3.20
COPY --from=builder /event-worker /usr/local/bin/event-worker
EXPOSE 8090
ENTRYPOINT ["event-worker"]

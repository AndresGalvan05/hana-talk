# Go cross-compiles natively via GOOS/GOARCH, so unlike core-api's Gradle
# build (which needs a $BUILDPLATFORM trick to avoid running the JVM build
# under QEMU emulation), the builder here can just run on $BUILDPLATFORM and
# target $TARGETARCH directly — no emulation at any point. BUILDPLATFORM
# keeps a hardcoded default so the legacy (non-BuildKit) builder used by
# local compose still resolves --platform. TARGETARCH must stay bare (no
# default) -- giving it a literal default here shadows BuildKit's
# per-platform injection during the multi-arch push build, so every
# platform silently got GOARCH=amd64 (this broke arm64 prod once already).
# Under the legacy builder TARGETARCH is simply unset, which is fine: Go
# treats an empty GOARCH as "use the host's native arch", correct for local
# dev either way.
ARG BUILDPLATFORM=linux/amd64
FROM --platform=$BUILDPLATFORM golang:1-alpine AS builder
ARG TARGETARCH
WORKDIR /app
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux GOARCH=$TARGETARCH go build -o /event-worker ./cmd/event-worker

FROM alpine:3.20
COPY --from=builder /event-worker /usr/local/bin/event-worker
EXPOSE 8090
ENTRYPOINT ["event-worker"]

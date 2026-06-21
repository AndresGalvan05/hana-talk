# infra/k8s

**Status:** Not started — Phase 4 (weeks 9–10)

Raw Kubernetes manifests for the full HanaTalk stack on k3s (Oracle Cloud Always Free).

Planned layout:
```
k8s/
├── core-api/
├── ai-exercise-svc/
├── event-worker/
├── kafka/
├── postgres/
├── mongodb/
└── ingress/
```

**Approach:** Start with raw manifests to build genuine understanding of Deployments,
Services, Ingress, ConfigMaps/Secrets, and PersistentVolumes. Migrate to Helm once
dev/prod duplication becomes a real, felt problem (trigger: second environment needed).

Namespaces: `dev` and `prod`.
Ingress: Traefik (bundled with k3s), routing through Cloudflare proxy.

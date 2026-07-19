# cluster-provisioning

## ADDED Requirements

### Requirement: Documented single-node k3s cluster on Oracle Always Free
The project SHALL provide a runbook (`infra/k8s/README.md`) that takes a fresh
Oracle A1 Flex VM (aarch64, 4 OCPU / 24 GB, Ubuntu) to a working single-node
k3s cluster, such that the entire cluster can be rebuilt from scratch by
following it.

#### Scenario: Rebuild from scratch
- **WHEN** a fresh A1 Flex VM exists and the runbook is followed end-to-end
- **THEN** a single-node k3s cluster is running with Traefik and the
  `local-path` StorageClass available, ready for `kubectl apply` of the
  project manifests

#### Scenario: User-owned prerequisites are explicit
- **WHEN** a reader starts the runbook
- **THEN** it states up front which steps are manual user actions outside the
  repo (create the VM, register/point `hanatalk.online` at Cloudflare, create
  the Cloudflare Origin CA certificate)

### Requirement: Network exposure limited to HTTP(S)
The cluster host SHALL expose only ports 80 and 443 to the public internet;
the k3s API server (6443) MUST NOT be publicly reachable and SSH MUST be
key-only.

#### Scenario: Public port scan
- **WHEN** the VM is scanned from the internet after provisioning
- **THEN** only 22 (key-only SSH), 80, and 443 accept connections, with both
  the Oracle security list/NSG and host iptables configured accordingly

#### Scenario: Cluster administration
- **WHEN** the operator needs `kubectl` access from their machine
- **THEN** the runbook's documented path (SSH session or SSH tunnel with the
  copied kubeconfig) works without opening 6443 publicly

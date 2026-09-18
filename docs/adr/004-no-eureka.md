# ADR-004: Static Docker/Kubernetes DNS routing instead of Eureka

## Status
Accepted (scope trade-off, reversible)

## Context
The original spec calls for Spring Cloud Gateway + Eureka for service
discovery. Standing up Eureka properly means a 7th backend module (a
Eureka server), every other service registering as a Eureka client, and
the gateway resolving routes via `lb://service-name` with a discovery
locator — a meaningful amount of additional moving parts for a project
already covering six business services, a frontend, and an AI service.

## Decision
Route statically in `api-gateway/application.yml` using environment
variables that resolve to Docker Compose service names or Kubernetes
Service DNS names (`http://inventory-service:8082`, etc.), both of which
already provide stable, resolvable hostnames without an extra discovery
layer.

## Consequences
**Positive:**
- One fewer service to build, deploy, and keep healthy.
- Docker Compose and Kubernetes both already solve "find the current IP for
  this logical service name" via their built-in DNS — Eureka would be
  solving a problem that's already solved at the platform layer in both of
  this project's actual deployment targets.

**Negative:**
- No client-side load balancing across replicas the way `lb://` +
  Ribbon/Spring Cloud LoadBalancer would provide — in Kubernetes this gap
  is closed by the Service's own load balancing (kube-proxy round-robins
  across pod IPs transparently), but in a bare Docker Compose setup with
  multiple replicas of one service, you'd need a reverse proxy in front to
  get the same effect (not needed for local dev's single-replica-per-service
  setup, but worth knowing if compose is ever scaled up).
- If this platform is later deployed somewhere without built-in service DNS
  (e.g. plain VMs), Eureka (or Consul, or another discovery mechanism)
  becomes necessary again.

## How to add Eureka back
1. Add a `eureka-server` module (`spring-cloud-starter-netflix-eureka-server`).
2. Add `spring-cloud-starter-netflix-eureka-client` to each service, with
   `eureka.client.service-url.defaultZone` pointing at the Eureka server.
3. In `api-gateway/application.yml`, change each route's `uri` from
   `http://service-name:port` to `lb://service-name` (matching the
   `spring.application.name` each service registers under) and set
   `spring.cloud.gateway.discovery.locator.enabled: true`.

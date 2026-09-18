# Stateful infrastructure (Postgres, Redis Cluster, Kafka)

Deliberately **not** included as raw StatefulSet manifests here. Running
Postgres, a real Redis Cluster, and a Kafka cluster reliably in Kubernetes
means correctly handling PodDisruptionBudgets, anti-affinity, PVC storage
classes, backup/restore, and cluster bootstrapping/rebalancing - and the
right answer in almost all real deployments is to use a managed service or
a purpose-built operator rather than hand-rolled YAML:

- **PostgreSQL**: use a managed instance (RDS, Cloud SQL, etc.) or the
  [CloudNativePG](https://cloudnative-pg.io/) operator for in-cluster HA.
- **Redis Cluster**: use a managed instance (ElastiCache, Memorystore) or
  the [Redis Operator](https://github.com/spotahome/redis-operator) for
  in-cluster 6-node (3 master / 3 replica) clusters matching the spec.
- **Kafka**: use a managed service (MSK, Confluent Cloud) or
  [Strimzi](https://strimzi.io/) for in-cluster clusters with proper
  partition/replica management.

For **local development**, docker-compose.yml at the repo root runs
single-node Postgres/Redis/Kafka/Zookeeper containers - see that file and
the root README for the full local dev stack. The application-layer code
(Lua scripts, Kafka topic configs, connection settings) is written to be
cluster-safe when pointed at real clustered infrastructure; see the
comments in `backend/inventory-service` around Redis key design (hash-tag
compatible key patterns) and `backend/*/config/KafkaConfig.java` (topic
partition counts, idempotent producers).

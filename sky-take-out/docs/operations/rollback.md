# Application rollback runbook

Rollback means selecting the previously approved immutable application image. It does not mean reverting a Flyway file, dropping a database, running `docker compose down -v`, or deleting a named volume.

## Automated drill

From a clean checkout with Docker Engine available:

```powershell
powershell -NoProfile -File sky-take-out/scripts/rollback-drill.ps1
```

The drill archives A and B independently from their resolved Git objects, builds each clean source context with its matching OCI revision, then starts A. It writes a sentinel order directly through MySQL on the private Compose network, starts B with an intentionally unresolvable RabbitMQ hostname, requires B to stay running while returning readiness `DOWN`, restores the exact A image, and compares both the sentinel and complete successful Flyway history. It stops the application at exit but deliberately keeps dependency containers and named volumes.

Each run receives a validated unique run ID and therefore its own Compose project, network, containers, and named volumes. The script refuses to adopt resources already carrying that project label. To inspect the sequencing without Docker:

```powershell
powershell -NoProfile -File sky-take-out/scripts/rollback-drill.ps1 -ConfigOnly
```

`-ConfigOnly` is a static contract check only. It is not evidence of runtime rollback, database preservation, or Docker health.

## Production rollback

Record `STABLE_IMAGE` by digest before deployment and keep it available. If candidate readiness fails, traffic must remain on stable. If failure occurs after switching traffic:

```powershell
$env:VCS_REF = '<previous-approved-40-character-commit>'
docker pull '<registry>/sky-take-out@sha256:<previous-approved-digest>'
docker image tag '<registry>/sky-take-out@sha256:<previous-approved-digest>' "sky-take-out:$env:VCS_REF"
docker compose -f sky-take-out/compose.yml --env-file sky-take-out/.env up -d --no-build app
docker compose -f sky-take-out/compose.yml --env-file sky-take-out/.env exec -T app wget -qO- http://localhost:8081/actuator/health/readiness
```

Switch the load balancer upstream to the restored instance on port 8080 only after readiness passes. Confirm the OCI revision, sentinel/business probes, and Flyway history. Port 8081 remains private.

```powershell
docker image inspect "sky-take-out:$env:VCS_REF" --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}'
docker compose -f sky-take-out/compose.yml --env-file sky-take-out/.env exec -T mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" -e "SELECT installed_rank,version,description,checksum,success FROM flyway_schema_history ORDER BY installed_rank"'
```

If the old binary cannot run against the expanded schema, stop traffic and restore the pre-deployment backup into a separate database, validate it, then atomically repoint the application. Never overwrite the only database copy. Prefer a forward corrective Flyway migration whenever data written since deployment must be retained.

## Failure diagnosis

Capture application logs, dependency health, candidate/stable digests, OCI revisions, readiness output, and Flyway history. Distinguish infrastructure DOWN (database/RabbitMQ), migration validation failure, application startup failure, and business smoke failure. Do not repeatedly restart a failing candidate or mutate data to force readiness.

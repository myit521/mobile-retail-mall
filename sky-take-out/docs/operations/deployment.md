# Backend deployment runbook

This runbook deploys the single `sky-server` artifact. Run commands from a clean checkout of the exact release commit. Port `8080` is the application entry point; management port `8081` is container/internal-host only and must never be exposed by a public load balancer or firewall rule.

## Required configuration

Copy `.env.example` to a deployment-local `.env` excluded from Git and replace every example value. A secret manager is preferred. Required for the base stack: `MYSQL_DATABASE`, `MYSQL_USERNAME`, `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `REDIS_PASSWORD`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`, `SKY_JWT_ADMIN_SECRET_KEY`, `SKY_JWT_USER_SECRET_KEY`, and `VCS_REF`. `GRAFANA_ADMIN_PASSWORD` is additionally required with `compose.monitoring.yml`. `MANAGEMENT_SERVER_ADDRESS` and `MANAGEMENT_SERVER_PORT` are mapped explicitly but must keep management access private; Compose still publishes only port 8080.

Optional integrations are disabled or empty in Compose. Configure them only when used: `WECHAT_APP_ID`, `WECHAT_APP_SECRET`, `WECHAT_MCH_ID`, `WECHAT_MCH_SERIAL_NO`, `WECHAT_PRIVATE_KEY_FILE`, `WECHAT_API_V3_KEY`, `WECHAT_PAY_CERT_FILE`, `WECHAT_NOTIFY_URL`, `WECHAT_REFUND_NOTIFY_URL`, `ALIYUN_OSS_ENDPOINT`, `ALIYUN_OSS_ACCESS_KEY_ID`, `ALIYUN_OSS_ACCESS_KEY_SECRET`, `ALIYUN_OSS_BUCKET_NAME`, `BAIDU_MAP_AK`, `SHOP_ADDRESS`, `SILICONFLOW_API_KEY`, `SILICONFLOW_BASE_URL`, `SILICONFLOW_MODEL`, `SILICONFLOW_TIMEOUT`, and `SILICONFLOW_ENABLED`. Operational tuning variables are `SKY_WEBSOCKET_TICKET_TTL`, `SKY_WEBSOCKET_TICKET_KEY_PREFIX`, `SKY_OUTBOX_ENABLED`, `SKY_OUTBOX_WORKER_ID`, `SKY_OUTBOX_LEASE_DURATION`, `SKY_OUTBOX_INITIAL_BACKOFF`, `SKY_OUTBOX_MAX_BACKOFF`, `SKY_OUTBOX_CONFIRM_TIMEOUT`, `SKY_OUTBOX_MAX_ATTEMPTS`, `SKY_OUTBOX_POLL_DELAY_MS`, `SKY_OUTBOX_HEALTH_PENDING_THRESHOLD`, `SKY_OUTBOX_HEALTH_FAILED_THRESHOLD`, `SKY_OUTBOX_HEALTH_REFRESH_MS`, and `SKY_MESSAGING_CONSUMER_MAX_ATTEMPTS`.

Do not pass credentials as Docker build arguments. The only build argument is the public source revision.

Every variable above is explicitly mapped into the application container by `compose.yml`; `--env-file` alone does not inject container variables. Validate names without printing secret values with `rollback-drill.ps1 -ConfigOnly`.

## Clean checkout and image

```powershell
git clone https://github.com/myit521/mobile-retail-mall.git
Set-Location mobile-retail-mall
git checkout --detach <approved-40-character-commit>
git status --porcelain # must be empty
$env:VCS_REF = git rev-parse HEAD
docker compose -f sky-take-out/compose.yml --env-file sky-take-out/.env build app
docker image inspect "sky-take-out:$env:VCS_REF" --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}'
```

Promote the immutable image digest, not a mutable tag. Verify the printed OCI revision equals the approved commit.

## Database entry modes and backup

For an empty database, start dependencies and then the application; Flyway applies V1 through the current version.

The Compose MySQL initialization hook grants the configured application user
`SELECT` on only `performance_schema.user_variables_by_thread`, which Flyway
uses when opening MySQL 8.4 connections. Docker runs that hook only for a new
`mysql-data` volume. When adopting an existing volume created before this hook,
do not delete the volume: validate that `MYSQL_USERNAME` contains only letters,
digits, and underscores, then have an administrator apply that same one-table
grant to the configured `'<MYSQL_USERNAME>'@'%'` account before starting the
application. Confirm the application account can execute
`SELECT COUNT(*) FROM performance_schema.user_variables_by_thread`; do not grant
`performance_schema.*` or broader privileges.

The following upgrade is idempotent and preserves the existing volume. It keeps
both passwords inside the MySQL container environment and validates the only
identifier interpolated into SQL:

```powershell
$mysqlUserLine = @(Get-Content sky-take-out/.env | Where-Object { $_ -match '^MYSQL_USERNAME=' })
if ($mysqlUserLine.Count -ne 1) { throw 'Expected exactly one MYSQL_USERNAME.' }
$mysqlUser = ($mysqlUserLine[0] -split '=', 2)[1]
if ($mysqlUser -notmatch '^[A-Za-z0-9_]+$') { throw 'Unsafe MYSQL_USERNAME.' }
"GRANT SELECT ON performance_schema.user_variables_by_thread TO ``$mysqlUser``@'%';" |
  docker compose -f sky-take-out/compose.yml --env-file sky-take-out/.env exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --user=root'
'SELECT COUNT(*) FROM performance_schema.user_variables_by_thread;' |
  docker compose -f sky-take-out/compose.yml --env-file sky-take-out/.env exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --batch --skip-column-names --user="$MYSQL_USER" "$MYSQL_DATABASE"'
```

Use `docker compose down --volumes` only for an explicitly disposable local
reset, never as the upgrade procedure for retained data.

```powershell
docker compose -f sky-take-out/compose.yml --env-file sky-take-out/.env up -d --wait mysql redis rabbitmq
docker compose -f sky-take-out/compose.yml --env-file sky-take-out/.env exec -T mysql sh -c 'exec mysqldump --single-transaction --routines --triggers -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' > "sky-predeploy-$env:VCS_REF.sql"
docker compose -f sky-take-out/compose.yml --env-file sky-take-out/.env up -d --no-build app
```

For an existing pre-Flyway database, first take the same backup and verify it can be restored to a separate MySQL instance. The schema must match the V1 baseline. `baseline-on-migrate=true` records version 1, then Flyway applies V2 onward. Never baseline an empty database, and never edit an applied migration.

```powershell
docker compose -f sky-take-out/compose.yml --env-file sky-take-out/.env exec -T mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" -e "SELECT * FROM flyway_schema_history ORDER BY installed_rank"'
```

Rehearse restore into a separate empty validation database/container; never restore over the only production copy:

```powershell
Get-Content "sky-predeploy-$env:VCS_REF.sql" -Raw | docker exec -i <separate-validation-mysql-container> sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"'
docker exec <separate-validation-mysql-container> sh -c 'exec mysqlcheck -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"'
```

## Readiness gate and traffic switch

Keep the candidate outside the production upstream until its internal readiness endpoint returns HTTP 200 with `status=UP`. Check from the private host/container network; do not publish 8081.

```powershell
docker compose -f sky-take-out/compose.yml --env-file sky-take-out/.env exec -T app wget -qO- http://localhost:8081/actuator/health/readiness
docker compose -f sky-take-out/compose.yml --env-file sky-take-out/.env ps
```

Only after readiness passes, update the reverse proxy/load balancer upstream to the candidate's port 8080, drain old connections, and retain the previous image digest until the observation window ends. If readiness fails, do not switch traffic; follow `rollback.md`.

## Expand, migrate, contract

Schema changes must be forward compatible:

1. **Expand:** add nullable columns/tables/indexes without removing fields used by the old binary.
2. **Migrate:** deploy code that can tolerate old and new representations, backfill in bounded/restartable batches, and measure completion.
3. **Contract:** only in a later release, after every old binary is retired and backfill is verified, add constraints or remove old structures with a new Flyway migration.

A failed application rollout is handled by selecting the prior image. A bad committed data migration is handled by a forward corrective migration or a rehearsed full restore; do not reverse it by deleting volumes.

## Diagnosis

```powershell
docker compose -f sky-take-out/compose.yml --env-file sky-take-out/.env ps
docker compose -f sky-take-out/compose.yml --env-file sky-take-out/.env logs --since 15m app mysql redis rabbitmq
docker compose -f sky-take-out/compose.yml --env-file sky-take-out/.env exec -T mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" -e "SELECT installed_rank,version,description,checksum,success FROM flyway_schema_history ORDER BY installed_rank"'
docker image inspect "sky-take-out:$env:VCS_REF" --format '{{json .Config.Labels}}'
```

Do not paste environment dumps, connection strings, callback bodies, tokens, or passwords into tickets. Redact logs before sharing.

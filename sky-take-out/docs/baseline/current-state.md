# Current repository state baseline

## Capture point

- Baseline commit: `552be2ce32a0d35dc638bc691ea789fdecabe8b5`.
- Branch at capture: `codex/enterprise-project-hardening`.
- Protected working-tree state at capture: modified `project-rjwm-weixin-uniapp-develop-wsy/pages/home/index.vue`; untracked `.claude/`, `nul`, and `openspec/`. These items are outside this baseline commit and were not edited or staged.
- Repository-root `nul` is untracked (not a tracked artifact to remove). It remains untouched and untracked under the recorded controller ruling.

## Runtime and build

- Java source and target: 17 (`maven.compiler.source` and `maven.compiler.target`).
- Spring Boot parent: 2.7.3.
- Maven reactor: `sky-take-out` (packaging `pom`), with `sky-common`, `sky-pojo`, and `sky-server` modules.
- The executable service artifact is built by `sky-server` through `spring-boot-maven-plugin`.

## Configuration and persistence

- The active profile is `dev`; `application.yml` optionally imports `application-dev.local.yml`.
- MySQL is configured through Druid using `sky.datasource.*`; the development example names database `sky_take_out` on port 3306.
- Redis is configured through `sky.redis.*`; the development example uses localhost:6379, database 10.
- The immutable application schema baseline is Flyway migration `sky-server/src/main/resources/db/migration/V1__baseline_schema.sql`.
- `sky-server/src/main/resources/db/sky.sql` remains a legacy import-only snapshot for adoption verification; it is not an application migration path. Populated databases baseline at Flyway version `1`, while empty databases execute V1.
- Additional SQL scripts are `sky-server/src/main/resources/sql/id_segment.sql` and `sky-server/src/main/resources/sql/payment_callback_log.sql`.
- Mapper XML resources are resolved from `classpath:mapper/*.xml`.

## Message topology

- Direct exchange: `order.event.exchange`.
- Queue `order.timeout.delay.queue` is durable, has a 15-minute TTL, and dead-letters to `order.event.exchange` with routing key `order.timeout.process`.
- Durable queue `order.timeout.process.queue` is bound with routing key `order.timeout.process`.
- Durable queues `order.paid.notify.queue` and `order.paid.audit.queue` are both bound with routing key `order.paid`.
- Rabbit listener mode is automatic acknowledgement with prefetch 10.

## Delivery safety-net gaps at capture

- No tracked or CI-enforced backend tests exist at the baseline commit. Six ignored, untracked Java test sources are present under `sky-server/src/test/` in this working tree; one local source required a Mockito void-stub correction so this working tree could compile. All six remain outside the Task 1 baseline commit and will be explicitly assessed by Task 2.
- No backend CI workflow is tracked; the only tracked CI file is `project-sky-admin-vue-ts/.gitlab-ci.yml`.
- No tracked Docker Compose file or Dockerfile exists for the backend.
- `sky-server.log` was tracked at capture and is removed from tracking by this baseline commit while remaining in the working tree; `/sky-server.log`, `**/target/`, and `performance/results/` are ignored, with `!.gitkeep` retained for placeholder files.

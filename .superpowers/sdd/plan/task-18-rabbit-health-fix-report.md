# Task 18: RabbitMQ health semantics

## Root cause

The Compose RabbitMQ healthcheck used `rabbitmq-diagnostics -q ping`. In the local `rabbitmq:3.13.7-management-alpine` image, `help ping` says it checks the node OS process, EPMD registration, and CLI authentication. `help check_running` says it exits nonzero unless the RabbitMQ application is running. The smoke test needs `rabbitmqctl list_vhosts`, which fails while that application is stopped. Thus Compose could report Healthy before the broker could serve the smoke check.

## RED and GREEN

- Added a ConfigOnly Compose contract in `scripts/compose-smoke.ps1` requiring the rendered RabbitMQ health command to check the running application.
- RED: `powershell -NoProfile -ExecutionPolicy Bypass -File sky-take-out/scripts/compose-smoke.ps1 -ConfigOnly` exited 1 on the original `ping` configuration with `RabbitMQ healthcheck must require the rabbit application to be running`.
- Changed only the RabbitMQ healthcheck command in `compose.yml` from `ping` to `check_running`.
- GREEN: the same ConfigOnly command exited 0 and printed `PASS: Compose config is safe and traceable to bcfa4b4b8b7749dcd95cedda7e5af7105a875aa8.`
- `powershell -NoProfile -ExecutionPolicy Bypass -File sky-take-out/scripts/tests/image-revision-tests.ps1` exited 0 for both scripts. `git diff --check -- sky-take-out/compose.yml sky-take-out/scripts/compose-smoke.ps1` exited 0.

## Focused Docker verification

Commands run against the local image:

- `docker run --rm --entrypoint rabbitmq-diagnostics rabbitmq:3.13.7-management-alpine help ping`
- `docker run --rm --entrypoint rabbitmq-diagnostics rabbitmq:3.13.7-management-alpine help check_running`
- Started only the RabbitMQ Compose service in a uniquely named project using `docker compose ... up --detach --wait --wait-timeout 120 rabbitmq`.
- Before stopping the application: `check_running` exited 0.
- After `rabbitmqctl stop_app`: `ping` exited 0, `check_running` exited 69, and `list_vhosts --quiet` exited 64.
- After `rabbitmqctl start_app`: `check_running` and `list_vhosts --quiet` both exited 0.
- `docker compose ... down --volumes --remove-orphans` removed that project's container, network, and volume.

An initial isolated `docker run` probe could not boot because its temporary container reported `/var/lib/rabbitmq/.erlang.cookie: eacces`; that container was removed. The Compose probe above uses the smoke stack's named-volume configuration and completed successfully. The full compose-smoke runtime was left to the main task.

## Self-review and remaining risk

The diff changes one Compose health command and adds one ConfigOnly contract. It adds no wait or Java production change, and it does not modify unrelated user files. The focused probe proves the health command tracks the RabbitMQ application state under Compose. It does not replace a full restart and persistence smoke run.

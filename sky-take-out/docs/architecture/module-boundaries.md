# Modular-monolith boundaries

The backend remains one Spring Boot application and one executable `sky-server` jar. The Java packages below define ownership inside that deployment; they are not separate Maven modules or services.

## Package contract

Each declared module has two package roots:

- `com.sky.<module>.api`: public application/query interfaces and immutable/stable event payloads that other modules may import.
- `com.sky.<module>.internal`: controllers, application implementations, persistence mappers, messaging adapters, schedulers, and other owner-only code.

Code outside a module must never import `com.sky.<module>.internal`. Cross-module calls use an `api` interface, and cross-module messages use event payloads below `api.event`. Package matching is segment-aware: names such as `internalized` and `mapperx` do not count as `internal` or `mapper` packages.

## Ownership

| Module | Owns | Public boundary |
| --- | --- | --- |
| `order` | Order HTTP adapters, order application behavior, order/detail and ID-segment persistence, order event publication | `OrderApplicationService`, `api.event` messages |
| `inventory` | Stock HTTP adapter, deduction/return/check/alert behavior, stock logs/checks/alerts and stock-field persistence | `InventoryService` |
| `notification` | Order-event consumption and WebSocket delivery | `OrderNotificationPort` |
| `payment` | WeChat callback HTTP adapter and callback-log persistence | No cross-module entry point; it calls order through `OrderApplicationService` |
| `auth` | Employee/user HTTP adapters, login/session behavior, employee/user persistence | `EmployeeService`, `UserService`, `TokenSessionService` |
| `product` | Category, phone-model, product, location, and shopping-cart HTTP/application/persistence behavior | Product application service interfaces in `product.api` |

Shared request/response/entity types remain in `sky-pojo`. Existing memo, report, upload, and shop-status features remain in their legacy packages because this boundary change does not declare owners for them.

## Collaboration paths

- Order requests inventory changes through `InventoryService`; order passes its own detail data into inventory for stock returns, so inventory never reaches into order persistence.
- Order requests user lookup and shopping-cart behavior through the auth and product APIs; it never imports their mappers.
- Order publishes stable `api.event` payloads. Notification consumes them and delegates WebSocket delivery through `OrderNotificationPort`.
- The two pre-migration `com.sky.message` classes remain as wire-only serialization shells because durable RabbitMQ queues may still contain their class descriptors. Only notification's internal messaging adapter may depend on them; publishers and business code continue to use `order.api.event`.
- Product shopping-cart behavior checks availability through `InventoryService`.
- Payment callbacks invoke `OrderApplicationService`; their callback-log mapper stays inside payment application code.

`ModuleBoundaryTest` enforces owner placement, the `api`/`internal` layout, direct-controller persistence prohibition, cross-module API-only access, and isolation of the two exact legacy wire shells. Adding a new business owner requires adding its package root and ownership mapping to that test in the same change.

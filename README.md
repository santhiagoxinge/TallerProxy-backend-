# Microservices Monitoring — Backend

Spring Boot backend for the **Proxy pattern workshop**: three microservices (Inventory, Orders, Payments)
sit behind a **logging proxy** that intercepts every call and records usage metrics, response times and errors.
The [frontend](https://github.com/santhiagoxinge/TallerProxy-Frontend) renders that data as a real-time
observability dashboard.

## Proxy pattern

```
Client ─▶ Controller ─▶ LoggingProxy<T> ─▶ InventoryService | OrderService | PaymentService ─▶ PostgreSQL
                              │                                                               (products, orders, payments)
                              └─▶ LogStore ─▶ PostgreSQL (call_logs)
```

| Piece | Role |
| --- | --- |
| `MicroserviceProxy<T>` | Generic interface: `T execute(String operation, Object... params)` |
| `InventoryService`, `OrderService`, `PaymentService` | Real services with implemented operations |
| `LoggingProxy<T>` | Wraps any service. Logs the start, then duration and result; on failure logs the error with a summarized stack trace and rethrows |
| `ServiceRegistry` | Exposes only the proxied services, so callers can never reach a raw service |
| `LogStore` / `JdbcLogStore` | Audit trail persisted in PostgreSQL (newest 5000 rows kept) and metric aggregation. `InMemoryLogStore` exists only for unit tests |

Every log entry contains `requestId` (UUID), `serviceId`, `operation`, `durationMs`, `status` (`SUCCESS` / `ERROR`),
`timestamp`, the input `params`, the `response` (or the summarized `error`).

`PaymentService` fails on purpose for 10% of its calls (`PAYMENT_FAILURE_RATE`).

## Stack

- Java 21, Spring Boot 4.1 (Spring JDBC)
- PostgreSQL on Supabase
- Maven (wrapper included)

## Run locally

Create the database first with [`db/schema.sql`](db/schema.sql), then:

```bash
export DATABASE_URL="jdbc:postgresql://<host>:5432/postgres?sslmode=require"
export DATABASE_USER="<user>"
export DATABASE_PASSWORD="<password>"
./mvnw spring-boot:run          # http://localhost:8080
./mvnw test                     # unit tests (no database needed)
```

Set `PORT` to use another port.

## Configuration

| Variable | Default | Description |
| --- | --- | --- |
| `DATABASE_URL` | — (required) | JDBC URL of the PostgreSQL database |
| `DATABASE_USER` | — (required) | Database user (limited to the `proxy_workshop` schema) |
| `DATABASE_PASSWORD` | — (required) | Database password |
| `DB_POOL_SIZE` | `5` | Maximum JDBC connections |
| `PORT` | `8080` | HTTP port (injected by the hosting platform) |
| `CORS_ALLOWED_ORIGINS` | `*` | Comma-separated origins allowed to call `/api/**` |
| `PAYMENT_FAILURE_RATE` | `0.10` | Share of payment calls that fail intentionally |

## API

### Services (all go through the logging proxy)

`POST /api/services/{service}/{operation}` with an optional JSON body.

| Service | Operations | Body |
| --- | --- | --- |
| `inventory` | `listProducts` | — |
| | `checkStock` | `{"sku": "SKU-MOUSE"}` |
| | `reserve`, `restock` | `{"sku": "SKU-MOUSE", "quantity": 3}` |
| `orders` | `create` | `{"customerId": "CUS-1", "sku": "SKU-MOUSE", "quantity": 2, "unitPrice": 25.5}` |
| | `get`, `cancel` | `{"orderId": "ORD-1001"}` |
| | `list` | — |
| `payments` | `charge` | `{"orderId": "ORD-1001", "amount": 51}` |
| | `refund`, `status` | `{"paymentId": "PAY-1A2B3C4D"}` |

```bash
curl -X POST http://localhost:8080/api/services/inventory/checkStock \
  -H "Content-Type: application/json" -d '{"sku":"SKU-MOUSE"}'
```

Errors: `400` invalid parameters, `404` unknown service / operation / resource, `503` simulated payment failure.

### Metrics

| Endpoint | Description |
| --- | --- |
| `GET /api/metrics/summary` | Total calls, error rate and average response time, overall and per service |
| `GET /api/metrics/logs?service=&status=&from=&to=&page=&size=` | Filtered logs, newest first, paginated. `status` is `SUCCESS` or `ERROR`; `from` / `to` are ISO-8601 instants (`2026-10-30T14:00:00Z`) |
| `POST /api/metrics/simulate-load` | Generates 50 random calls to populate the logs |

## Deploy (Render)

Live API: https://taller-proxy-backend.onrender.com

1. Create a **Web Service** on Render from this repository, with runtime **Docker** (the included `Dockerfile` is used).
2. Use the free plan and set the health check path to `/`. Render injects `PORT` automatically.
3. Optionally set `CORS_ALLOWED_ORIGINS` to the frontend URL (defaults to any origin).

On the free plan the service sleeps after inactivity, so the first request can take about a minute.
Set `DATABASE_URL`, `DATABASE_USER` and `DATABASE_PASSWORD` as environment variables (use the Supabase session
pooler host, since Render has no IPv6 egress). Data and logs persist across restarts and redeploys.

## Project structure

```
src/main/java/com/proxy/monitoring
├── proxy/        MicroserviceProxy, LoggingProxy
├── service/      Inventory, Order, Payment services, ServiceRegistry, LoadSimulator
├── controller/   ServiceController, MetricsController, HealthController
├── store/        LogStore, JdbcLogStore, InMemoryLogStore
├── model/        LogEntry, MetricsSummary, ServiceSummary, PagedLogs, CallStatus
├── exception/    Domain exceptions and ApiExceptionHandler
└── config/       CorsConfig

db/schema.sql     PostgreSQL schema, seed data and security setup
```

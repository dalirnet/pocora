# Pocora — Platform

Operator reference for running Pocora as a multi-tenant service for many families.

> For a product overview see [`README.md`](./README.md). For technical implementation see [`STRUCTURE.md`](./STRUCTURE.md).

---

## Deployment Modes

| Mode        | Who hosts the hub | Platform supervisor needed |
| ----------- | ----------------- | -------------------------- |
| Self-hosted | The family        | No                         |
| Platform    | The operator      | Yes                        |

Both modes use the same hub codebase. Platform mode enables multi-tenancy and the supervisor panel. Self-hosted families bypass this layer entirely.

---

## Architecture

```
┌─────────────────────────┐
│   PLATFORM SUPERVISOR   │
│   families              │
│   subscriptions         │
│   shared presets        │
│   platform health       │
└────────────┬────────────┘
             ▼
┌─────────────────────────┐
│       HUB SERVER        │
│   multi-tenant          │
│   many families         │
└────────────┬────────────┘
             │
     ┌───────┴────────┐
     ▼                ▼
 Family A          Family B
 parent app        parent app
 agent(s)          agent(s)
```

Each family is fully isolated — data, devices, rules, and schedules are private and inaccessible to other families.

---

## Supervisor Capabilities

### Family Management

- Register a new family (creates an isolated tenant on the hub)
- View all families — name, registration date, device count, last active
- Suspend, delete, or reset a family account

### Subscription Management

- Define subscription plans with per-plan limits
- Set limits — max child devices, data retention period, feature access
- View, extend, upgrade, or cancel any family's subscription
- Export billing data

### Shared Presets

Platform-wide presets are maintained centrally and available to all families:

| Preset        | Maintained by  |
| ------------- | -------------- |
| Iran internet | Platform supervisor |
| aparat.com    | Platform supervisor |
| Educational   | Platform supervisor |
| Social media  | Platform supervisor |

Families may use shared presets or define custom rules on top.

### Platform Health

- Active family and device counts
- Hub server status (CPU, memory, storage)
- Devices that have not checked in for an unusually long time
- Error logs and diagnostics

---

## Subscription Plans

| Plan     | Devices    | Data retention | Price       |
| -------- | ---------- | -------------- | ----------- |
| Free     | 1 child    | 7 days         | Free        |
| Family   | 3 children | 30 days        | Monthly fee |
| Extended | 6 children | 90 days        | Monthly fee |

Plans are fully configurable — these are examples, not fixed values.

---

## Data Isolation

Each family's data is stored in a separate schema or namespace in Postgres. The platform supervisor can view aggregate metrics but has no access to individual family telemetry or child device data.

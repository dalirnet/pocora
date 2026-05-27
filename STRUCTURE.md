# Pocora — Structure

Technical design reference for contributors and implementers.

> For a product overview see [`README.md`](./README.md). For platform / multi-tenant deployment see [`PLATFORM.md`](./PLATFORM.md).

- **Repo:** https://github.com/dalirnet/pocora (private)
- **Domain:** pocora.ir
- **Naming:** `CORA` = **C**lock · **O**versight · **R**oute · **A**pps — one module per concern: time, telemetry, traffic, device.

---

## 1. Philosophy

Pocora does not use Device Owner (DPC) or root. It relies on a single trust mechanism:

> The child needs the app open to have internet. That moment is used to sync everything.

This works well for children aged 8–14. Older or more determined users may find workarounds — that is an accepted tradeoff, not a design failure. Pocora is not a cage. It is a bridge.

---

## 2. Architecture

```
┌──────────────┐        ┌────────────────────────────┐
│  PARENT APP  │        │  AGENT APP                 │
│              │        │  (child's device)          │
│  dashboard   │        │  xray (local VPN)          │
│  alerts      │        │  filters traffic on-device │
│  schedules   │        │  connects directly to web  │
└──────┬───────┘        └────────────┬───────────────┘
       │ HTTPS / WS                  │ HTTPS / WS
       │ on demand                   │ on interval
       │ (rules, commands)           │ (telemetry, confirmations)
       ▼                             ▼
       ┌─────────────────────────────────┐
       │           HUB SERVER            │
       │  REST API                       │
       │  rule compiler (presets→xray)   │
       │  Postgres (rules + telemetry)   │
       │  push notifications             │
       └─────────────────────────────────┘
```

| Component  | Role                                                                                                    |
| ---------- | ------------------------------------------------------------------------------------------------------- |
| Parent app | Dashboard, alerts, schedule and rule management                                                         |
| Hub        | Compiles presets → xray config, stores telemetry, evaluates schedules. Traffic never passes through it. |
| Agent      | Runs xray as a local VPN, filters on-device, polls hub for config, sends telemetry back                 |

---

## 3. Core Flows

### Internet Requires the App

The agent runs xray as a local VPN. All traffic is intercepted and filtered on-device — allowed requests go directly to internet, blocked ones are dropped.

| State             | Outcome                                         |
| ----------------- | ----------------------------------------------- |
| App open          | VPN up, internet works filtered by cached rules |
| App swiped away   | VPN service keeps running in the background     |
| App force-stopped | VPN dies, no internet, child must reopen        |

### First Run (parent, one time)

1. Grant VPN permission
2. Grant usage access (screen time reading)
3. Activate device admin (raises the bar for uninstalling)
4. Grant battery optimization exemption (keeps service alive)
5. Agent records baseline — current boot time and per-app traffic counters
6. Device registers with hub — appears in parent dashboard immediately

No router changes. No factory reset.

---

## 4. Capabilities

### Traffic Control

The parent manages traffic through named presets — toggled on or off per child. Each preset maps to a bundle of xray rules compiled by the hub.

| Preset        | Action                       |
| ------------- | ---------------------------- |
| Free internet | `DIRECT`                     |
| Iran internet | `DIRECT`                     |
| aparat.com    | `DIRECT`                     |
| Social media  | `BLOCK`                      |
| Educational   | `PROXY` → parent's server    |
| Custom        | `BLOCK` / `DIRECT` / `PROXY` |

The parent never touches xray config. The hub compiles it and pushes it to the agent on next sync.

Delivery status per preset:

| Status     | Meaning                                        |
| ---------- | ---------------------------------------------- |
| ⏳ Pending | Change sent, waiting for agent confirmation    |
| ✅ Applied | Agent confirmed config is active               |
| ⚠️ Failed  | Agent did not respond, will retry on next sync |

### Reporting

On every hub sync, the agent sends:

- Per-app screen time (daily)
- Per-app traffic delta (calculated from baseline)
- Full installed app list with versions
- Heartbeat gaps — exact periods when the agent was not running
- Reboot events — detected via boot time comparison

Even if the child closes the app or reboots the device, the next time they open Pocora for internet the hub receives a complete picture of what happened during the gap.

### Presence Schedules

The parent defines expected online hours per device in two layers:

- **Weekly template** — repeating default (e.g. school days 7am–10am, weekends 10am–9pm)
- **This-week override** — per-day adjustments for holidays, exams, or sick days

| Situation                      | Result             |
| ------------------------------ | ------------------ |
| Online during expected window  | ✅ All good        |
| Silent during expected window  | ⚠️ Parent notified |
| Online outside expected window | ⚠️ Parent notified |
| Silent outside expected window | 😴 Ignored         |

### App Management

**Suggest install:** Parent browses a curated list (Google Play, Bazar, Myket) → sends suggestion → child approves or ignores → on approval, agent downloads APK and opens Android's standard install prompt → outcome reported back.

**Request removal:** Parent requests removal → child approves or ignores → on approval, Android's standard uninstall prompt opens → outcome reported back.

Both flows keep the child in the loop.

### Tamper Detection

If the child disables the VPN, accessibility service, or device admin while the agent is running:

- Hub is notified immediately
- Parent receives a push alert
- Child sees "internet unavailable" in the app

### Hub Unreachable

- xray continues filtering with the last received rules
- Telemetry is queued locally and synced when hub reconnects
- Child's experience is unchanged — app shows a subtle offline indicator

---

## 5. The Baseline Trick

At first run, the agent saves:

- `baseline_reboot` — timestamp of the current OS boot
- `baseline_traffic` — per-app traffic counters at that moment

On every hub reconnect:

```
current boot time == baseline_reboot?

  YES → no reboot occurred
        → calculate traffic delta from baseline
        → report to hub

  NO  → device was rebooted
        → report reboot event to hub
        → reset baseline to current values
```

The moment the child opens the app for internet, all buffered truth arrives at the hub.

---

## 6. Agent Local Storage

The agent uses a small SQLite database for state that cannot be read fresh from the OS:

| Table          | Purpose                                                           |
| -------------- | ----------------------------------------------------------------- |
| `usage_stats`  | OS does not retain daily history — agent builds its own snapshots |
| `baseline`     | Reference point for reboot detection and traffic delta            |
| `pending_cmds` | Commands from hub not yet executed — must survive app kills       |

Everything else (installed apps, boot time, current traffic counters) is read from the OS on demand.

---

## 7. Limitations

| Scenario                    | Status                                         |
| --------------------------- | ---------------------------------------------- |
| Child swipes app away       | ✅ VPN keeps running                           |
| Child force-stops the app   | ⚠️ VPN dies — gap detected on next reconnect   |
| Child disables VPN manually | ⚠️ Detected immediately, parent alerted        |
| Child reboots the device    | ⚠️ Detected via boot time delta on reconnect   |
| Child tries to uninstall    | ⚠️ Device admin raises the bar, not impossible |
| Determined teenager (14+)   | ❌ A motivated user can bypass with effort     |

A child who truly wants to bypass Pocora probably will — and that is a conversation worth having.

---

> **Current target:** Android. The design intentionally avoids platform-specific constraints — the same approach applies to other mobile platforms in the future.

# Pocora

A self-hosted parental supervision app — private, no third-party services, and designed to start conversations rather than silently enforce rules.

Parent and child apps are available for both **Android and iOS**, free of charge.

> Technical design: [`STRUCTURE.md`](./STRUCTURE.md) · Platform / supervisor layer: [`PLATFORM.md`](./PLATFORM.md)

---

## The Idea

Most parental control tools act like silent walls — they block, punish, and report without the child ever understanding why.

Pocora works differently. It creates **moments for conversation**.

> The technology triggers the moment. The family handles it.

A parent seeing "TikTok — 3 hours today" is a reason to sit down and talk, not a reason to punish silently. A child receiving an app removal request is an invitation to explain themselves. A child who bypasses the app entirely — that too is worth discussing.

---

## Who It's For

**Families in restricted countries (e.g. Iran)**
Children here already use dozens of unverified VPN apps just to reach the internet — invisible and uncontrolled from the parent's side. Pocora replaces that chaos with one parent-controlled tunnel. The child gets safe, working internet. The parent gets visibility.

**Families everywhere else**
Pocora works as a supervision and filtering tool — no third-party apps, no data sent to corporations, fully self-hosted. Same architecture, different context.

---

## How It Works

The child opens the app themselves — because without it, there is no internet. That is the only lock needed.

```
Child wants internet
        ↓
Opens Pocora → VPN starts → internet works (filtered by cached rules)
        ↓
Parent sees activity on their dashboard
```

The parent sets it up once on the child's device. No router changes. No factory reset.

---

## What Parents See

| Signal         | Detail                                                 |
| -------------- | ------------------------------------------------------ |
| App list       | Every installed app, last used time, screen time today |
| Presence       | Whether the child is online during expected hours      |
| Heartbeat gaps | When the agent was not running, and for how long       |
| Reboot events  | Whether the device was restarted and when              |
| Traffic usage  | Per-app data consumption since last session            |

---

## What Parents Can Do

- Set a **weekly schedule** with per-day overrides for holidays or exams
- **Block, allow, or proxy** content categories and apps via named presets
- **Suggest app installs** from Google Play, Bazar, or Myket — child confirms or ignores
- **Request app removal** — child confirms or ignores
- Receive **instant alerts** on tamper attempts

---

## Components

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
       ▼                             ▼
       ┌─────────────────────────────────┐
       │           HUB SERVER            │
       │  REST API                       │
       │  rule compiler (presets→xray)   │
       │  Postgres (rules + telemetry)   │
       │  push notifications             │
       └─────────────────────────────────┘
```

- **Parent app** — dashboard, alerts, schedule and rule management.
- **Hub** — compiles presets into xray config, stores telemetry, evaluates schedules. Device traffic never passes through it.
- **Agent** — runs xray as a local VPN, filters traffic on-device, syncs with hub on interval.

`CORA` = **C**lock · **O**versight · **R**oute · **A**pps — one module per concern.

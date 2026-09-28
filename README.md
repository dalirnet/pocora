# Pocora

A self-hosted parental supervision app for families in Iran. Private, no third-party services, and built to start conversations rather than silently enforce rules.

Free and open source. Parents use the hub's dashboard, a PWA that installs to the phone's home screen or the desktop. The child agent is an **Android** app, since Android is what children in Iran carry.

> Technical design: [`STRUCTURE.md`](./STRUCTURE.md)

---

## The Idea

Most parental control tools act like silent walls. They block, punish, and report without the child ever understanding why.

Pocora creates **moments for conversation**.

> The technology triggers the moment. The family handles it.

"Aparat, 3 hours today" is a reason to sit down and talk. An app removal request is an invitation for the child to explain. A child who turns Pocora off is a conversation too.

Pocora is not a lock. The child can turn it off. That is a visible choice, not a blocked one.

---

## Who It's For

**Families in Iran.**

Outside Iran, tools like Google Family Link already cover parental supervision. Inside Iran they are unavailable or unreliable. Pocora fills that gap.

Pocora does not bypass the national filter and does not route traffic anywhere. It filters and observes the internet that is already reachable, on the device itself. Nothing leaves the hub the family chose.

Pocora assumes the family has decided the child uses domestic internet only. A parent who allows a VPN for filtered apps has already accepted that connection and does not need Pocora. For everyone else, a VPN appearing on the child's device is exactly the kind of moment Pocora exists to surface.

---

## How It Works

The parent installs the agent on the child's device once. No router changes, no factory reset.

```
Agent runs in background → local VPN filters reachable internet
        ↓
Syncs app usage, traffic, and rules with the hub on interval
        ↓
Parent sees the timeline in the dashboard
```

Everything is **blocked by default**. The parent draws a **timeline**: each block opens allowed apps and traffic for a time range. Inside a block, the hub expects the agent to be alive. If it goes silent, the parent knows.

---

## What Parents See

| View      | Detail                                                       |
| --------- | ------------------------------------------------------------ |
| Timeline  | Active and inactive periods drawn over the parent's schedule |
| App usage | Every app, screen time and traffic, offline apps included    |
| App list  | Every installed app with version and last used time          |
| Signals   | VPN turned off, VPN app installed, watched app used, reboot  |

## What Parents Can Do

- Draw the **timeline**: time blocks with allowed apps and traffic presets
- Set per-day **overrides** for holidays, exams, or sick days
- Mark apps to **watch**: using one creates an alert
- **Suggest app installs** from Google Play, Bazar, or Myket. Child confirms or ignores
- **Request app removal**. Child confirms or ignores

Alerts are read in the dashboard. There is no push notification.

---

## Components

```
┌─────────────────────────────────┐          ┌──────────────────────────────┐
│           HUB SERVER            │          │  AGENT  (child's device)     │
│  parent dashboard (PWA)         │          │                              │
│  REST API                       │◄─────────┤  xray local VPN              │
│  rule compiler (timeline→xray)  │  HTTPS   │  filters traffic on-device   │
│  silence detection              │  on      │  reads app usage             │
│  SQLite (rules + telemetry)     │  interval│                              │
│  alerts (pull, no push)         │          │                              │
└──────────────┬──────────────────┘          └──────────────────────────────┘
               │ PWA, HTTPS / WS
               ▼
            PARENT
```

- **Hub**: serves the parent dashboard as a PWA, compiles the timeline into xray config, stores telemetry, detects silence, keeps alerts. Device traffic never passes through it.
- **Agent**: native Android app. Runs xray as a local VPN, filters on-device, reports usage, syncs on interval.

`CORA` = **C**lock · **O**versight · **R**oute · **A**pps. One module per concern.

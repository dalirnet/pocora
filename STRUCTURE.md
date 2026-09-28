# Pocora — Structure

Technical design reference for contributors and implementers.

> Product overview: [`README.md`](./README.md)

- **Repo:** https://github.com/dalirnet/pocora (private)
- **Domain:** pocora.ir
- **Naming:** `CORA` = **C**lock · **O**versight · **R**oute · **A**pps. One module per concern: time, telemetry, traffic, device.

---

## 1. Philosophy

Pocora is built for families in Iran. Outside Iran, Google Family Link already covers parental supervision. Inside Iran it is unavailable.

Three principles:

1. **No routing.** Pocora does not bypass the national filter and never sends traffic through another server. The local VPN exists only to filter and observe the internet already reachable from the device.
2. **No cage.** No Device Owner, no root. The child can turn Pocora off. Pocora makes that a deliberate act in Settings, and the parent hears about it.
3. **Observe always, enforce network only.** App usage is recorded at all times, offline apps included. Enforcement means cutting internet, nothing more.

> The agent runs in the background and syncs on interval. If it goes silent, the hub notices and the parent is told. What happens next is a conversation.

This works well for children aged 8 to 14. Older or more determined users may find workarounds. That is an accepted tradeoff, not a design failure.

---

## 2. Architecture

```
┌─────────────────────────────────┐          ┌──────────────────────────────┐
│           HUB SERVER            │          │  AGENT  (child's device)     │
│  parent dashboard (PWA)         │          │                              │
│  REST API                       │◄─────────┤  tunnel:   xray local VPN    │
│  rule compiler (timeline→xray)  │  HTTPS   │  reporter: usage, heartbeat  │
│  silence detection              │  on      │                              │
│  SQLite (rules + telemetry)     │  interval│                              │
│  alerts (pull, no push)         │          │                              │
└──────────────┬──────────────────┘          └──────────────────────────────┘
               │ PWA, HTTPS / WS
               ▼
            PARENT
```

Two components. There is no separate parent app.

| Component | Role                                                                                                                   |
| --------- | ---------------------------------------------------------------------------------------------------------------------- |
| Hub       | Serves the parent dashboard as a PWA. Compiles the timeline to xray config, stores telemetry, detects silence, keeps alerts. Carries no device traffic. |
| Agent     | Native Android app. Runs xray as a local VPN, filters on-device, reports usage and heartbeat, syncs on interval.       |

The dashboard needs no OS access, so a PWA served by the hub covers every parent device with one codebase and no app store. The parent installs it to the phone's home screen or the desktop.

**Minimum Android version: 10.** Per-app rules rely on connection-to-UID lookup, available from Android 10.

---

## 3. Hub

### Dashboard

The parent's only interface, served as an installable PWA. Timeline editing, app list and usage, signals, alerts, install and removal requests.

### Rule compiler

Compiles a family's whole week (template plus overrides) into one xray config per device and pushes it on next sync. Delivery is tracked per change:

| Status     | Meaning                                     |
| ---------- | ------------------------------------------- |
| ⏳ Pending | Sent, waiting for agent confirmation        |
| ✅ Applied | Agent confirmed the config is active        |
| ⚠️ Failed  | Agent did not respond, retry on next sync   |

### Silence detection

- The agent sends a heartbeat every 60 or 120 seconds, configurable per hub.
- Inside a timeline block, 10 missed heartbeats in a row mark the device **inactive**: 10 or 20 minutes. Shorter drops are mobile network noise and are ignored.
- Outside any block, silence is ignored.
- The hub keeps an active/inactive line per device, drawn over the parent's timeline.
- When the child turns the VPN off in Settings, the agent sends one last message so the hub can tell "turned off" from plain silence.

### Alerts

There is no push notification. Push services are unreliable in Iran and would add a third party.

The hub keeps an alert list per family. The dashboard loads it when opened and keeps it live with a WebSocket or long polling while open. Everything is also on the timeline, so nothing is lost if the parent opens the dashboard days later.

### Administration

One hub serves one family or many. There is no separate admin app.

- An **administrator** user is defined in the hub's env file (username and password). It is created at startup and cannot be created through the API.
- The administrator registers families. Each family gets a parent account and an isolated tenant.
- Parents log in to their own family only. The administrator has no access to family telemetry: app usage, timelines, or signals.
- The administrator sees aggregate health: family and device counts, hub CPU, memory, storage, devices silent for an unusually long time, error logs.
- The administrator maintains **shared presets** that every family can use or extend.
- Each family has its own SQLite file. One more file holds the administrator, the family list, and shared presets. No database server, backup is a file copy.

A self-hosting family runs the same hub with one family registered. The hub is a single process, which SQLite requires and this load never exceeds.

---

## 4. Agent

### Two services

| Service  | Role                                               | Survives VPN off |
| -------- | -------------------------------------------------- | ---------------- |
| Tunnel   | xray VPN, traffic filtering, per-app network rules | No               |
| Reporter | Heartbeat, screen time, app list, hub sync         | Yes              |

If the child turns the VPN off, the reporter keeps sending. The parent still sees which apps were used, and which VPN app is active, if any.

The tunnel always lets its own traffic through: the hub connection and DNS. Nothing else is exempt.

### Always-on VPN

During setup the parent enables two Android settings for Pocora: **Always-on VPN** and **Block connections without VPN**.

| State                          | Outcome                                                                |
| ------------------------------ | ---------------------------------------------------------------------- |
| App open or swiped away        | VPN up, internet works, filtered by cached rules                       |
| App force-stopped or rebooted  | Android restarts the VPN automatically                                 |
| Another VPN app tries to start | Refused by Android until the child turns Always-on off                 |
| Child turns Always-on off      | Normal unfiltered internet, agent sends a last message, VPN-off signal |

### Per-app rules

All apps go through the tunnel. The agent resolves each connection to its app UID and xray applies the current block's app and preset rules. No tunnel restart at block boundaries, and per-app domains are visible.

The agent switches blocks by itself using hub-synced time, so changing the device clock does not skip a block.

### Hub unreachable

- Tunnel keeps filtering with the last compiled week
- Signals are queued locally and sent when the hub is back. Usage is read back from Android on reconnect
- Child's experience is unchanged, the app shows a subtle offline indicator

### Local storage

No database on the agent. Android already keeps app usage for about a week, the installed app list, boot time, and traffic counters, and the hub stores the long history. The agent keeps only what Android does not:

| Item           | Storage   | Purpose                                                       |
| -------------- | --------- | ------------------------------------------------------------- |
| `boot_time`    | DataStore | Boot time at last sync, used for reboot detection             |
| `heartbeat`    | DataStore | Last heartbeat time, used to report exact gaps on reconnect   |
| `week_rules`   | File      | Compiled week, agent switches blocks without the hub          |
| `pending_cmds` | DataStore | Commands from hub not yet executed, must survive app kills    |
| `event_queue`  | DataStore | Signals raised while the hub was unreachable, sent on reconnect |

Usage is never queued. After a gap the agent reads it back from Android's own history.

---

## 5. Timeline and Presets

### Timeline

The timeline is the parent's main control. The base rule is **block all**. Each block is an exception that opens things up for a time range.

A block defines:

- Time range, e.g. school days 8am to 2pm
- Allowed apps: these get internet, all others get none
- Presets: what those apps may reach. An allowed app in a block with no `DIRECT` preset gets nothing.

Two layers:

- **Weekly template**: repeating default
- **This-week override**: per-day adjustments for holidays, exams, or sick days

| Time              | Internet                 | Silence flagged | App usage recorded |
| ----------------- | ------------------------ | --------------- | ------------------ |
| Inside a block    | Block's apps and presets | Yes             | Yes                |
| Outside any block | None                     | No              | Yes                |

A parent who wants full-time tracking draws one 24-hour block.

### Presets

Building blocks used inside timeline blocks. Each maps to a bundle of xray rules.

| Preset        | Action             |
| ------------- | ------------------ |
| All internet  | `DIRECT`           |
| Iran internet | `DIRECT`           |
| aparat.com    | `DIRECT`           |
| Games         | `BLOCK`            |
| Custom        | `BLOCK` / `DIRECT` |

`DIRECT`: the request goes straight to the internet as the device normally reaches it. `BLOCK`: dropped on-device. There is no proxy action.

`BLOCK` always wins over `DIRECT`. A typical block is "All internet" plus one or more `BLOCK` presets that subtract from it.

### Watch list

Separately from the timeline, the parent can mark any app as **watch**. A watched app used at any time becomes an alert, once per usage session. All other usage is shown on the timeline without one.

---

## 6. Reporting and Signals

### Reporting

On every sync the agent sends:

- Per-app screen time, every app, offline ones included
- Per-app traffic, read from the OS network stats on the tun interface
- Full installed app list with versions, VPN apps flagged, active VPN app if any
- Heartbeat gaps: exact periods the agent was not running
- Reboot events

After a gap, the agent backfills app usage from OS history for the missing period. The OS keeps a few days, so a long gap loses its earliest days. That is acceptable: by then the alert has done its job and the family has handled it.

**Reboot detection.** The agent saves the boot time at every sync. On reconnect, a different boot time means a reboot happened. It is recorded on the timeline and the saved value is updated.

### Signals

Events that appear on the timeline.

| Event                         | Alert                 |
| ----------------------------- | --------------------- |
| Agent inactive inside a block | Yes, once per episode |
| VPN turned off in Settings    | Yes                   |
| Another VPN app active        | Yes                   |
| VPN app installed             | Yes                   |
| Device admin deactivated      | Yes                   |
| Watched app used              | Yes                   |
| Reboot                        | No                    |

---

## 7. Setup

One time, done by the parent.

In the hub dashboard:

1. Add a child and pick a **starter timeline** (e.g. school days, holiday, always on). Required before pairing, so the child's phone never starts on an empty block-all timeline.
2. Show the pairing code.

On the child's device:

3. Install the agent and enter the pairing code
4. Grant VPN permission
5. Enable Always-on VPN and Block connections without VPN
6. Grant usage access (screen time reading)
7. Activate device admin (raises the bar for uninstalling)
8. Grant battery optimization exemption (keeps services alive)
9. Agent saves the boot time and pulls the compiled week
10. Device appears in the dashboard

No router changes. No factory reset.

---

## 8. App Management

**Suggest install:** parent picks from a curated list (Google Play, Bazar, Myket) → child approves or ignores → on approval the agent opens the app's store page and the store installs it → outcome reported back.

**Request removal:** parent requests → child approves or ignores → on approval Android's standard uninstall prompt opens → outcome reported back.

Both flows keep the child in the loop.

---

## 9. Limitations

| Scenario                       | Status                                                                |
| ------------------------------ | --------------------------------------------------------------------- |
| Child swipes app away          | ✅ VPN keeps running                                                  |
| Child force-stops the app      | ✅ Always-on restarts it                                              |
| Child reboots the device       | ✅ Always-on restarts it, reboot recorded                             |
| Child turns Always-on off      | ⚠️ Unfiltered internet, VPN-off signal, usage still reported          |
| Child installs another VPN app | ⚠️ Refused until Always-on is off, install and activation are signals |
| Child tries to uninstall       | ⚠️ Device admin raises the bar, not impossible                        |
| Child uses an offline app      | ⚠️ Cannot be blocked, minutes are recorded                            |
| Determined teenager (14+)      | ❌ A motivated user can bypass with effort                            |

A child who truly wants to bypass Pocora probably will. That is a conversation worth having.

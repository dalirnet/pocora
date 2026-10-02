# Pocora

Parental supervision for families in Iran. Two Android apps, one on the parent's phone and one on the child's, talk directly over the home Wi-Fi. No server, no account, nothing leaves the home. Persian by default, and English.

Pocora is not a lock. It turns what happens on the child's phone into moments for a conversation: "Aparat, 3 hours today" is a reason to talk, and so is a child turning Pocora off.

> Presets: [`presets.json`](./android/app/src/main/assets/presets.json) · Android project: [`android/`](./android/README.md) · Landing page: [`docs/`](./docs/), published at [pocora.ir](https://pocora.ir/)

## Principles

1. **No server.** The two apps talk only to each other, on the same Wi-Fi or a phone's hotspot.
2. **No routing.** Pocora does not bypass the national filter. Its local VPN only cuts apps that must not have internet now.
3. **No cage.** No root, no Device Owner. The child can turn Pocora off; the parent hears about it.
4. **Observe always, enforce network only.** Usage is recorded at all times. Enforcement means cutting internet, nothing more.

Made for children aged 8 to 16. Outside Iran, Google Family Link already does this job.

## How it works

```
┌─────────────────────────────┐   sync every minute    ┌─────────────────────────────┐
│ PARENT APP (parent's phone) │◄───────────────────────┤ CHILD APP (child's phone)   │
│ schedule editor, alerts,    │                        │ local VPN, usage, events,   │
│ last snapshot of each child ├───────────────────────►│ rules + 7 days of record    │
└─────────────────────────────┘  changes, while open   └─────────────────────────────┘
                  same Wi-Fi, mutual TLS, nothing queued, no server
```

- **The child's phone is the source of truth.** It holds the rules and the last 7 days, and keeps enforcing them when no parent is around.
- **The parent sees things when the phones meet**, usually in the evening. Away from the child, the parent app shows the last snapshot with its time.
- **Changes need the child's phone in reach.** Nothing is queued; a change that gets no answer offers a retry.
- **A family** can have two parents and many children. Each parent pairs with each child; parents never sync with each other.

## Rules

Each child has three settings, all picked from [presets](./android/app/src/main/assets/presets.json):

| Setting      | What it says                                                                                                                                                                                                                                                                                                                    |
| ------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Schedule** | When the internet is on. One week from Saturday, each day 48 half-hour marks, each **Allowed** or **Limited** (the default). Presets fit the Iranian year: school shifts, exams, Ramadan, summer. The app suggests the one for the time of year and the day before a holiday.                                                   |
| **Apps**     | Which apps have internet in an Allowed mark. Lists are built from groups (School, Games, Video, Messaging…), by age (Kids, Everyday, Teen) or purpose. Single apps can be always in or always out. System apps always have internet, VPN apps never.                                                                            |
| **Quota**    | How much data the child may use: seven levels from 20 MB to 1 GB an hour, or No limit. The parent can also give more data for the internet time on now. Shown per hour, applied per half hour: when a half hour's share is used up, internet stops until the next one. The app also shows the most it can add up to in a month. |

On top of the preset the parent changes single days, for this week or every week, or with one tap: 30 more minutes, more data, stop internet, allow internet. Apps can be marked **watched**: using one raises an alert.

**Alerts** reach the parent as local notifications when the phones meet: Pocora stopped, VPN turned off, another VPN app, a VPN app installed, device admin off, a watched app used, a child missing for 7 days. The child sees the same list on the Activity tab of its own app.

## Connection and security

- **Discovery:** both apps announce `_pocora._tcp` with Android's NSD, carrying only a random id and a port (parent 47601, child 47602). On a hotspot, where discovery is unreliable, the child tries the gateway address; with both apps on one phone, loopback.
- **Pairing:** the parent app shows a QR code with its id, certificate fingerprint and port. The child scans it and asks; the parent accepts. Both keep the other's fingerprint. A leaked code pairs nothing without the parent's approval. With both apps on one phone, no camera can scan its own screen: the parent app hands its code and a one-time token to the child app, through a receiver only an app signed with Pocora's key may reach, and accepts the request that brings the token back.
- **Security:** every connection is TLS in both directions, each side pinned to the other's certificate fingerprint, kept in Android's Keystore. Another device on the Wi-Fi cannot read or change anything.
- **Parent app password:** a 4-digit PIN, asked every time the app comes back to the screen, since the child may use the same phone. Only a salted hash is stored.

## Project

```
docs/           the Persian landing page, served by GitHub Pages from main; tools/icons.sh writes its icon sprite
assets/         source artwork: fonts/ (Dana), icon/ (the app icon as SVG)
android/        one Gradle project that builds both apps
  Makefile      check, image, phones, icons, format, test, build, release, run, run-both, pair, publish, clean
  scripts/      one shell script per Makefile target
  version.properties  the released version, written by make publish
  app/src/main/java/ir/pocora/
    ui/         screens and drawn components; colours in AppColors, sizes in Dimens, icons in AppIcons
    agent/      the child's phone at work: clock, rules, data per mark, events, sync
    parent/     the parent's phone at work: snapshots, contact, alerts, actions
    service/    agent, listener and tunnel services, notifications, receivers, tile
    transport/  TLS, frames, discovery, pairing
    protocol/   messages, pairing code, ports and timeouts
    model/      schedule, apps, quota, events, snapshot, Iranian calendar
    preset/     loading the shipped presets
    config/     settings, language, password, this phone's identity, paired phones
    debug/      file logger
```

**Two apps, one codebase.** Product flavors `child` and `parent` differ only in application id, app name, a small manifest each, and one generated constant read through `Role.current`. Both can be installed side by side. The parent build never asks for VPN, usage or device-admin access.

**Stack.** Kotlin only (no Java sources), Jetpack Compose with Material 3, the [Solar](https://icon-sets.iconify.design/solar/) icon set by 480 Design (CC BY 4.0, written into `ui/AppIcons.kt` by `make icons`), kotlinx.serialization, the platform's TLS sockets with length-prefixed JSON, `NsdManager`, `VpnService` with no core, ML Kit to read QR codes and ZXing to draw them. No database: JSON files, SharedPreferences and the Keystore. Android 9 (API 28) and up.

```bash
cd android
make check            # check this machine can build the project
make test             # unit tests of both apps
make build            # debug APKs
make run ROLE=parent  # build, install and open one app (ROLE=child by default)
```

More, including virtual phones and pairing without a camera: [`android/README.md`](./android/README.md).

## Download and releases

The apps are on the [Releases](https://github.com/dalirnet/pocora/releases) page: `pocora-parent-<version>.apk` for the parent's phone and `pocora-child-<version>.apk` for the child's, with their SHA-256 sums. Each release also carries `pocora-parent.apk` and `pocora-child.apk`, the same files without the version, so `releases/latest/download/pocora-parent.apk` always downloads the newest; the landing page links there.

- **Every push** runs `.github/workflows/build.yml`: tests, debug APKs kept as a run artifact for 14 days.
- **A version tag** runs `.github/workflows/release.yml`: tests, signed and minified APKs, and a GitHub Release with generated notes.

To release, run `make publish` in `android/` with a clean tree. It asks for a patch, minor or major bump (or a rebuild of the current version), writes it into `android/version.properties` with the next `versionCode`, commits it, merges into `main`, and pushes the `vX.Y.Z` tag that starts the release. A rebuild replaces the published release and tag, so it asks you to type the tag to confirm.

Release signing needs four repository secrets, set once. Keep the keystore safe: every future release must be signed with it, or Android refuses to update the installed app.

```bash
keytool -genkeypair -keystore pocora.jks -alias pocora -keyalg RSA -keysize 4096 -validity 10000
base64 -i pocora.jks | gh secret set POCORA_KEYSTORE_BASE64
gh secret set POCORA_KEYSTORE_PASSWORD
gh secret set POCORA_KEY_ALIAS     # pocora
gh secret set POCORA_KEY_PASSWORD
```

## Code style

- ktlint, official Kotlin style, 4 spaces, lines up to 120. `make format` formats Kotlin, JSON and XML.
- Names in full, never abbreviated; a constant with a measure names its unit: `SYNC_INTERVAL_MILLISECONDS`.
- One file per concern. Nothing twice: a pattern used in two places becomes one shared function.
- No text in code: every label is a string resource, Persian in `values/`, English in `values-en/`. No colour or size outside `AppColors` and `Dimens`.
- Comments say why, not what.
- Unit tests for plain logic, named `function_case`.

## Limitations

| Case                                           | Result                                                                             |
| ---------------------------------------------- | ---------------------------------------------------------------------------------- |
| App swiped away, force-stopped, phone rebooted | Always-on VPN brings it back; stops and reboots are logged                         |
| Child turns Always-on VPN off                  | Internet is open, the parent gets an alert, usage is still recorded                |
| Child installs another VPN app                 | Refused while Always-on is on; installing it is an alert                           |
| Offline apps                                   | Cannot be blocked; their time is recorded                                          |
| Phones apart                                   | Nothing is seen or changed until they meet; after 7 days the oldest record is gone |
| Child clears Pocora's data                     | The record is lost; the child shows as missing                                     |
| Router isolates Wi-Fi devices                  | Use a phone's hotspot                                                              |
| Parent has an iPhone                           | Not supported                                                                      |

A child who truly wants to get around Pocora probably will. That is a conversation worth having.

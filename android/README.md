# Pocora — Android

One Gradle project that builds both apps: **Pocora Child** and **Pocora Parent**.

> Overview, design, project layout and code style: [`README.md`](../README.md)

## Requirements

- Min SDK 28 (Android 9)
- Target SDK 35
- JDK 17 or later

## Build

```bash
make               # list every target

make check         # check this machine can build and run the project
make image         # install the system image for the virtual phones
make phones        # create and start the two virtual phones
make phones-stop   # shut the virtual phones down

make presets       # write the presets JSON from ../preset/
make format        # format Kotlin, JSON and XML (requires ktlint, jq, xmllint)
make test          # run the unit tests of both apps

make build         # debug APKs, child and parent
make release       # release APKs, minified

make run           # build, install and open the child app on its phone
make run ROLE=parent
make run-both      # both apps, each on its own phone
make pair          # pair the two virtual phones, in place of scanning the code

make publish       # cut a release: bump the version, merge to main, tag; GitHub builds it

make clean         # remove build output and caches
```

Every target runs one script from `scripts/`. The scripts share `scripts/common.sh`, which holds the constants (roles, phone names and ports, the system image) and the helpers.

| Script | Does |
| --- | --- |
| `common.sh` | Constants and helpers. Sourced by the others, never run |
| `check.sh` | Checks Java, the SDK and Gradle, and reports the optional tools |
| `image.sh` | Downloads and installs the system image, continuing after a failure |
| `phones.sh` | Creates, starts and stops the two virtual phones |
| `presets.sh` | Writes `presets.json` from `../preset/` through `presets.py` |
| `format.sh` | Formats Kotlin, JSON and XML |
| `test.sh` | Runs the unit tests and counts them |
| `compile.sh` | Builds both apps, debug or release |
| `run.sh` | Builds, installs and opens one app or both |
| `pair.sh` | Pairs the two virtual phones without the camera |
| `publish.sh` | Bumps the version, merges to main and pushes the tag that starts the GitHub release |
| `clean.sh` | Removes build output and caches |

## Virtual phones

`make phones` creates two Android 15 virtual phones and starts them:

| Phone | For | Serial |
| --- | --- | --- |
| `pocora-child` | The child app | `emulator-5554` |
| `pocora-parent` | The parent app | `emulator-5556` |

`make run` sends each app to its own phone. To use another device, such as a real phone, pass its serial: `make run ROLE=child SERIAL=<serial>`. `make run-both SERIAL=<serial>` puts both apps on that one device.

The two virtual phones cannot find each other: each sits behind its own virtual router, and neither has a camera to scan the other's screen. `make pair` stands in for both. Open the parent app, add a child and stay on the pairing code, then run `make pair` and tap Accept. It forwards the parent's port to this computer, which is the child phone's gateway, and hands the code to the child app. Debug builds only.

Two phones use about 3 GB of memory. On an 8 GB computer they start one after the other and can still stall. Both apps on one phone pair too, over loopback: stop the child phone, `make run-both SERIAL=emulator-5556`, then `make pair`.

They need the emulator and one system image, installed once:

```bash
sdkmanager "emulator"
make image
```

`make image` downloads the 1.8 GB image with curl, so after a failed download it continues where it stopped. `sdkmanager` would start again from the beginning each time. Run `make image` again until it says Done.

## No Java sources

The project is compiled by the Kotlin compiler alone. That is why the role is a generated Kotlin constant, not a `BuildConfig` field: `BuildConfig` is generated as Java, and compiling Java for Android fails on the newest JDKs.

For the same reason the scripts pass `--no-configuration-cache`: storing the cache makes Gradle prepare the Java compiler even when there is nothing for it to compile.

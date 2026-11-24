# Home Control (HVAC controller app)

The Android front end for the house: heat pumps, hot water, solar, pool, alarm and garage doors.
It runs on two wall tablets and a phone, and talks only to Home Assistant. n8n does the heat pump
logic. The whole system is described in [docs/SYSTEM-REFERENCE.md](docs/SYSTEM-REFERENCE.md).

## Installing and updating the app

The app is installed and updated through [Obtainium](https://github.com/ImranR98/Obtainium) from
the releases of **[cstone1983/app-builder](https://github.com/cstone1983/app-builder)**, titled
**`hvac <version>`** (tags `hvac-v<version>`).

1. A change is pushed to `main` here.
2. app-builder checks out `main`, builds `:app:assembleRelease` unsigned, signs it with the one
   shared release key and creates a pre-release.
3. Promoting that pre-release to a full release is what deploys it. Obtainium on each device then
   installs it over the one before.

Things that follow from that:

- **Version numbers.** app-builder sets `versionName` to this repo's `versionName` plus its run
  number (`85.0` becomes `85.0.57`) and `versionCode` to `1000 + run`. Keep `versionName` in
  `app/build.gradle.kts` as plain dotted numbers and `versionCode` below 1000.
- **No secrets are built in.** app-builder has no `.env`, and its releases are public, so a
  release carries no Home Assistant token and no GitHub token. See "First start" below.
- **The two Home Assistant addresses are built in** (85.0, the owner's choice): the main address
  is Nabu Casa (`DEFAULT_MAIN_HA_URL` in `api/BuildDefaults.kt`) and the home address is
  `http://10.10.11.103:8123/` (`DEFAULT_HOME_HA_URL`). Addresses saved in Settings > Connection
  still win. The home address is used only on the home Wi-Fi, by its saved name, on every device.
- **This repo publishes no APK.** `.github/workflows/android-release.yml` is only a build check:
  unit tests plus the same unsigned release build.

### First start after a fresh install

A fresh install opens on a sign-in screen. Type a Home Assistant username and password, check the
device name, and tap LOG IN. If the account has two-step verification, the screen then asks for the
code. Home Assistant makes a long-lived access token for the device, listed under the device name
in the user's profile (Security > Long-lived access tokens); the app keeps that token and nothing
else. The password is not stored.

It signs in through the built-in main address (Nabu Casa), shown under the heading, so it works on
any network. CHANGE SERVER ADDRESS changes it; it can't be a home network address, because the main
address is the one used away from home.

To have a device talk to Home Assistant directly at home, open Settings > Connection while on the
home Wi-Fi and tap USE THIS WI-FI (it asks for location access once: Android only shows Wi-Fi names
to apps that have it). Until then, and on any other network, the device uses Nabu Casa.

USE A LONG-LIVED TOKEN INSTEAD swaps the three fields for one where a token can be pasted, for an
account that does not sign in with a password.

A failed login is never retried by the app: Home Assistant counts failures and, if `ip_ban` is
set up, bans the address after enough of them. The screen shows what Home Assistant answered.

The token and addresses are kept on the device and survive every update. They are lost only if the
app is uninstalled. Signing in again after that makes a new token; if the old name is still listed
in Home Assistant, the new one gets " (2)" added, and the old entry can be deleted there.

The default device names per tablet model are in `signIn.deviceNames` in `layout_config.json`.

## Layout updates (over the air)

`layout_config.json` holds the entities, labels, colours, thresholds and timings. Each device
checks `main` every five minutes and offers a changed file on its Updates page; no reinstall is
needed. This reads the file straight from the branch and never uses a GitHub release. The repo is
public, so it works without a GitHub token.

There are two copies of the file, `layout_config.json` and `app/src/main/assets/layout_config.json`,
and they must be identical. The build check fails if they differ.

## Building locally

Prerequisite: [Android Studio](https://developer.android.com/studio).

1. Copy `.env.example` to `.env` and fill in `HA_URL`, `HA_BACKUP_URL`, `HA_TOKEN` and, if you
   like, `GITHUB_TOKEN`. `.env` is gitignored. Never commit it.
2. Build and test:

   ```bash
   ./gradlew assemblePanel testDebugUnitTest
   ```

Build types:

| Build type | Use |
|---|---|
| `release` | What the devices run. Built unsigned and signed by app-builder. |
| `panel` | The same app for installing from Android Studio: not debuggable, R8 off, signed with this PC's debug key. It cannot install over an app-builder release without uninstalling first, because the key differs. |
| `debug` | Debuggable, for development. Noticeably slower on the tablets. |

`release` and `panel` must stay the same app. Do not add build-type source folders, and keep R8
off in both until Moshi's reflection adapters have keep rules.

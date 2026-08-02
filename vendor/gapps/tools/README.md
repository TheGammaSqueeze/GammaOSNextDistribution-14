# Keeping the Google-apps seeds fresh

`vendor/gapps` (MindTheGapps, Android 14 / `upsilon`) provides the Google apps for
the `bgN` "Full Google" builds. The APKs in it are **bootstrap seeds**: on a
freshly flashed device Play Store and Play Services update themselves over the
network. The problem is the seeds are years out of date (GmsCore in particular:
the upstream `upsilon` branch pins `23.25.18` from mid-2023 and is frozen). So the
first thing a newly flashed device does after setup is download, reinstall and
re-dexopt hundreds of MB of Google apps at once, which pins a low-power handheld
for a long time.

These tools move that work to **build time**:

1. **Refresh** the stale seeds to the newest build that still runs on the target
   platform, so the runtime update is a small delta or a no-op.
2. **Trim** optional Google apps out of the seed set, so there is nothing to
   auto-update / re-dexopt for them.

## Usage

From the repo root:

```
./update-gapps.sh check     # report seed vs latest, download nothing
./update-gapps.sh refresh   # refresh seed APKs in place
./update-gapps.sh trim      # remove optional apps + regenerate makefiles
./update-gapps.sh all       # refresh + trim  (the usual release-prep run)
```

Everything is driven by `gapps-seeds.json`. After running, review with
`git status vendor/gapps` and `git diff --stat vendor/gapps` before committing.

## What gets refreshed

`refresh` lists the apps that actually drive first-boot data: `GmsCore`,
`Phonesky`, `GoogleServicesFramework`. APKs are fetched from apkcombo.com (no
Google account needed). The mirror is treated as **untrusted**: every downloaded
APK must pass all of these before it is installed, or it is rejected:

* package name matches,
* signing certificate SHA-256 equals Google's release cert (this is what keeps
  the `;PRESIGNED` entries valid and proves authenticity),
* `minSdk <= max_sdk` (34 = Android 14), so we never seed a build that refuses to
  run on the target,
* for arch-specific apps, the APK's native code contains the target ABI,
* the version is newer than the seed it replaces (unless `--force`).

### GmsCore needs a staged APK (important)

apkcombo and other consumer mirrors gate downloads by the **visitor's device
profile**, so for a given API level they will not reliably serve an
`arm64-v8a` build of GmsCore (they tend to offer either an `arm64` build tagged
for a newer Android than the target, or a 32-bit `armeabi-v7a` build). The tool
**correctly refuses** those rather than ship a broken-ABI Play Services.

For GmsCore, download the exact variant by hand from APKMirror:

> Google Play services -> the newest release whose variant is
> **`arm64-v8a` + `nodpi`** and **Android <= 14** ("Android 9.0+" etc.)

Drop that `.apk` into `vendor/gapps/tools/staging/` and run
`./update-gapps.sh refresh` again. A staged APK is preferred over the network
fetch and still passes every check above before being installed. Delete it from
`staging/` once installed.

## What gets trimmed

`trim` removes optional apps from the seed set and regenerates the makefiles.
The default set (edit `gapps-seeds.json` to keep any of them):

* Velvet + VelvetTitan (Google Search / Assistant, the largest by far)
* SpeechServicesByGoogle, talkback (accessibility TTS)
* MarkupGoogle, Wellbeing (Digital Wellbeing), GoogleRestore

Essentials are always kept: GmsCore, Phonesky, GoogleServicesFramework,
GooglePartnerSetup, SetupWizard, the sync adapters, dialer support and the GMS
overlays.

Trimming edits the `proprietary-files-*.txt` manifests, deletes the on-disk APK
dirs, and re-runs `setup-makefiles.sh` to regenerate `*-vendor.mk` / `Android.bp`
without the removed apps. Removing an app also removes the network update and the
first-boot dexopt for it.

## About "deferring Play auto-update"

There is **no reliable build-time knob** to change Play Store's auto-update
cadence. It is not a framework `Settings` key; it lives in Play's own storage in
`/data` (wiped on flash) and is largely driven server-side via GServices/checkin.
The effective, real way to stop Play from hammering the device on first boot is
exactly refresh + trim above: refresh removes the reason it downloads (stale
seeds), trim removes the apps it would otherwise update. If a seedable finsky/
GServices default is ever identified, `overlay/GmsSettingsProviderOverlay` is
where a `Settings.Global` default would be added.

## Notes

* The shipped Google target is `arm64_bgN` (arm64 only), so `arches` in the
  config is `["arm64"]`. Add `"arm"` / `"x86_64"` if you ever ship those.
* `vendor/gapps` is flattened into the monorepo (no upstream `repo sync` pulls
  it), and the `upsilon` branch it came from is frozen at 2025-02-03, so these
  tools are the update path, not a manifest bump.

# Syncthing for GammaOS

The official Syncthing release, unmodified, packaged as a native background service.

| | |
|---|---|
| Version | v2.1.5 "Hafnium Hornet" (2026-09-08) |
| Upstream | https://github.com/syncthing/syncthing/releases/tag/v2.1.5 |
| Archive | syncthing-linux-arm64-v2.1.5.tar.gz |
| sha256 of `prebuilt/arm64/syncthing` | b13cd05fe3171d062116a430ec1b1d333c23b477fe719d27879d501ad9350f97 |
| Licence | MPL-2.0 (NOTICE) |

## Layout on the device

- `/system/bin/syncthing` - the daemon (static Go binary, arm64).
- `/system/etc/resolv.conf` - resolver file for Go's pure DNS resolver (see Android.bp).
- `SSL_CERT_DIR=/system/etc/security/cacerts` in the service environment: the linux build of Go
  does not know Android's CA store path (syncthing.rc).
- `/data/misc/syncthing` - home: config.xml (holds the REST API key), keys, database. The daemon
  runs as a single process (STMONITORED=1); its recent log is served from memory by the REST API.
- Init service `syncthing` (syncthing.rc), SELinux domain `syncthing`
  (system/sepolicy/private/syncthing.te).

## Where folders can live

The daemon runs outside the app sandbox, so a synced folder must sit on a raw storage mount:

- internal storage: `/data/media/<user>/...` (what apps see as `/storage/emulated/<user>` and
  `/sdcard`), including `Android/data/<package>/...`;
- a removable card: `/mnt/media_rw/<volume>/...` (vold's raw mount underneath the FUSE view at
  `/storage/<volume>`; the service carries the `external_storage` group to reach it).

All three clients map a picked or typed `/storage/...` or `/sdcard/...` path onto those mounts
(`canonicalFolderPath` in NanoSyncthing.cpp and SyncthingClient.java) and refuse anything else,
since the SELinux domain covers exactly these two trees. Files the daemon writes under
`/data/media` bypass the FUSE layer, so the media store only notices them at its next scan; the
nano game and save folders do not depend on it.

A folder on a card gets "ignore permissions" (FAT keeps none) and a full rescan interval of at
most ten minutes. When the card is pulled, the daemon fails the folder's health check ("folder
path missing") and stops syncing it, so nothing is deleted on the other devices; when the card is
back, the folder recovers at its next full rescan, which is what the short interval bounds.

## Control

Everything is property driven; no UI starts the service directly.

- `persist.gammaos.syncthing.enabled` 0/1: off / run from boot.
- `sys.gammaos.syncthing.restart` = 1: restart the daemon (cleared by init).

The REST API listens on 127.0.0.1:8384 with the API key from
`/data/misc/syncthing/config.xml` (`<apikey>`). The daemon runs as `system`, so the two Settings
apps (uid system) and the nano menu (root, domain bootanim, allowed by policy) read the key
directly. The key is deliberately never exported as a property, which any app could read.

## Updating the binary

1. Download `syncthing-linux-arm64-<ver>.tar.gz` from the upstream release page and verify it
   against the upstream sha256sum.txt.asc.
2. Replace `prebuilt/arm64/syncthing`, update the version and checksum in this file and copy the
   release's LICENSE.txt over NOTICE if it changed.
3. Re-run the regression and load scripts in docs/syncthing-plan.md on the RG DS Plus with
   SELinux enforcing and check `dmesg` for `avc: denied` lines naming `syncthing`.

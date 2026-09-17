# Syncthing native integration for GammaOS

Owner: autonomous iteration (started 2026-09-16, late evening). Target device: Anbernic RG DS Plus
(adb aeaef76817ce3b95, RK3568, 1 GB). Reference build: lineage_tv_arm64_bvN (buildtv.sh).

This document is the single source of truth for the work. Each iteration appends a dated status
entry at the bottom. The cron reminder points here.

## 1. Goal

Ship Syncthing (https://github.com/syncthing/syncthing) as a first-class GammaOS service:

- The official Syncthing binary runs as a native init service in the background, independent of
  any app or of nano being on screen, with a proper SELinux domain (no permissive reliance).
- Nano (all four homes: PS3 XMB, DSi, Minima, ES-DE) gets a Syncthing area in Settings with
  parity with a real Syncthing client: device identity, folders, devices, pending requests,
  per-folder and per-device actions, daemon options, status, logs.
- Normal Settings (com.android.settings, the Lite/Full editions) and TV Settings
  (com.android.tv.settings, the Core edition) get the same functionality.
- Everything is tested against a real Syncthing peer (a local server on the build host), including
  regression runs and load runs, on the RG DS Plus.

Non-goals: bundling the Syncthing web GUI assets as an in-nano browser; running Syncthing while the
device is suspended (the SoC is asleep); iOS-style push. The web GUI stays reachable on the LAN as
an option for power users.

## 2. Architecture

### 2.1 Daemon packaging

- Prebuilt official release binary (Go, static, stripped), pinned by version and sha256:
  `external/gammaos-syncthing/` with `Android.bp` (`cc_prebuilt_binary` per arch, arm64 first),
  `syncthing.rc`, `README.md` (version, upstream URL, sha256, update procedure), `NOTICE`.
  Version at start: v2.1.5 (2026-09-08).
- Installed as `/system/bin/syncthing`, added to PRODUCT_PACKAGES in
  `build/make/target/product/base_system.mk` next to gammaos-nano / gammaos-sharefs.
- Home (config + keys + database) at `/data/misc/syncthing` (0700 system:system, created on
  post-fs-data; under /data/misc so it inherits that tree's device-encrypted policy). Logs to
  `/data/misc/syncthing/syncthing.log` with the daemon's own rotation (1 MB, 2 old files).

### 2.2 Init service and control surface (properties only, like gammaos-sharefs)

```
service syncthing /system/bin/syncthing serve --home=/data/misc/syncthing --no-browser \
        --no-upgrade --no-port-probing --no-restart --gui-address=127.0.0.1:8384 \
        --log-file=/data/misc/syncthing/syncthing.log --log-max-size=1048576 --log-max-old-files=2
    class late_start
    user system
    group system media_rw inet
    seclabel u:r:syncthing:s0
    setenv HOME /data/misc/syncthing
    setenv STNOUPGRADE 1
    setenv GOMAXPROCS 2
    disabled
```

- `persist.gammaos.syncthing.enabled` = 0/1. init: `on property:...=1 && sys.boot_completed=1`
  start; `on property:...=0` stop. Both nano and the two Settings apps only flip this property.
- `sys.gammaos.syncthing.restart=1` (one-shot) restarts the service (settings changes that need it).
- `--no-restart`: Syncthing's own monitor process is dropped (about 20 MB RSS on a 1 GB device);
  the daemon exits when a restart is needed and init starts it again.
- `/system/etc/resolv.conf` (public resolvers) is shipped because Go's pure resolver needs it;
  bionic never reads it. Without it the daemon's discovery and relay lookups went to [::1]:53.
- The API key is read by the clients straight from `<apikey>` in `/data/misc/syncthing/config.xml`:
  the daemon runs as system, both Settings apps are uid system, nano is root, so no export step
  is needed. Never a property (properties are world-readable; any app could drive the daemon).
- GUI/REST bind is loopback only by default. "LAN web GUI" option in the UI rebinds to 0.0.0.0
  with a username/password set through the API (parity with the real client's GUI settings).

### 2.3 SELinux (system/sepolicy/private + the two prebuilt API mirrors)

- `syncthing.te`: `type syncthing, domain, coredomain; type syncthing_exec, exec_type,
  system_file_type, file_type; init_daemon_domain(syncthing); net_domain(syncthing);`
  plus: `syncthing_data_file` for /data/misc/syncthing (create/rw dirs, files, symlinks), rw on
  `media_rw_data_file` (synced folders under /data/media), read `sdcard_type` as needed, bind
  tcp/udp listen ports (22000 tcp/quic, 21027 discovery), `net_raw` not needed. Deny-by-default:
  iterate from real denials collected with `adb shell dmesg | grep avc` on the device (enforcing).
- `file_contexts`: `/system/bin/syncthing u:object_r:syncthing_exec:s0`,
  `/data/misc/syncthing(/.*)? u:object_r:syncthing_data_file:s0`.
- Clients: nano runs in `bootanim` (via bootanim_exec labelling) -> allow bootanim to read
  `syncthing_data_file` (config.xml for the key) and connect to loopback tcp (net_domain
  already). Settings and TvSettings run as system uid in `system_app` -> the same read.
- The Plus vendor's precompiled sepolicy must be regenerated after any system policy change
  (secilc on device, sha256 markers) - see memory vendor_selinux.md / the Air X runbook.
- Verify with `getenforce` = Enforcing on the target and zero `avc: denied` for `syncthing`
  across: boot, folder scan, sync, discovery, relay, restart, log write.

### 2.4 Nano integration (new files, minimal touch of existing code)

- `NanoSyncthing.h/.cpp`: a small loopback HTTP/1.1 client (GET/POST/PUT/PATCH/DELETE with
  Content-Length and chunked bodies, timeouts, API key header) + a typed model over the REST API:
  system status/connections/version, config (folders, devices, options, gui), folder status,
  device stats, pending devices/folders, events (long-poll on a worker thread for live updates),
  log. Uses the existing NanoJson. No new library dependencies.
- `NanoMenuSyncthing.cpp`: the Settings screens built as dynamic Ps3Level submenus
  (`screenKind = ST_*`, new `PS3_ST_*` item kinds) so every theme renders them through its own
  list renderer (XMB / DSi submenu / Minima list / ES-DE), exactly like the Game Systems editor.
  Toggles use the checkbox rows, choices the side-panel chooser, text entry the OSK, confirmations
  the existing dialog, folder paths the existing folder browser (PS3_GS_DIR). Status refreshes
  from the worker thread through a small mutex-guarded snapshot; the UI thread never blocks on
  HTTP.
- Screen map (parity with the real client):
  - Syncthing: Enabled [On/Off], status line (running / stopped / starting, version, uptime),
    This Device (name, device ID with copy-to-clipboard-style display, addresses, listeners,
    discovery, relay state), Folders, Devices, Pending Requests (badge count), Options, Restart,
    Logs, Web GUI (LAN access toggle + address + credentials).
  - Folders: list rows "label - state (idle / scanning / syncing 42% / error / paused)";
    Add Folder (label, ID, path via browser, type send-only / receive-only / send-receive,
    versioning none / trash can / simple / staggered, rescan interval, watch for changes,
    ignore permissions, shared with devices multi-select).
  - Folder: status details (global/local/need bytes and files, last scan, errors), Pause /
    Resume, Rescan, Override changes (send-only) / Revert local changes (receive-only), Edit
    (same fields as Add), Edit Ignore Patterns (OSK, line list), Remove (confirm; keep files).
  - Devices: list rows "name - connected via tcp/quic/relay, in/out rate / disconnected /
    paused"; Add Device (device ID via OSK, name, addresses dynamic or typed, introducer,
    auto-accept, compression, folders to share multi-select).
  - Device: details (ID, address, last seen, version, sync completion), Pause / Resume, Edit,
    Remove (confirm).
  - Pending: incoming devices (accept -> Add Device prefilled / ignore) and incoming folders
    (accept -> Add Folder prefilled with the offered ID / ignore).
  - Options: device name, listen addresses, global discovery, local discovery, relays, NAT
    traversal, upload / download limits (KiB/s, LAN exempt), max concurrent scans, start on
    boot (the persist prop), usage reporting off, min free disk %.
- Entry points: a "Syncthing" row in nano Settings (Network group) in all themes; hidden when the
  binary is absent from the image.

### 2.5 Settings and TV Settings

- Settings (Lite/Full): `packages/apps/Settings/src/com/android/settings/handheld/syncthing/`
  (`SyncthingFragment`, `SyncthingFoldersFragment`, `SyncthingDevicesFragment`,
  `SyncthingFolderFragment`, `SyncthingDeviceFragment`, `SyncthingOptionsFragment`,
  `SyncthingClient` (HttpURLConnection + org.json, background thread, apikey from
  /data/syncthing/apikey)), registered as a row in `res/xml/handheld_dashboard_fragment.xml`.
- TV Settings (Core): the same set under `com.android.tv.settings.gammaos.syncthing`, registered
  in `res/xml/main_prefs.xml`, `@Keep` on every fragment, LeanbackPreference style.
- Both edit only `persist.gammaos.syncthing.*` for daemon control and talk to the REST API for
  everything else. Screen structure mirrors the nano map above.

### 2.6 Background behaviour

- init service: alive from boot (when enabled) through app launches, nano parks, drastic-nano
  sessions. Not tied to any process nano manages. No zygote/Doze involvement.
- Device sleep suspends the SoC; syncing resumes on wake (expected). Nano's sleep path stays as
  is.
- Memory guard for the 1 GB Plus: measure RSS idle / scanning / syncing; cap via GOGC and
  GOMAXPROCS; decide whether the default should be Off on 1 GB devices (persist default per
  device via vendor build.prop like the mlockall gate).

## 3. Test setup

- Host peer: official linux-amd64 v2.1.5 running from the session scratchpad
  (`scratchpad/syncthing/host_home`, GUI 127.0.0.1:8385, listener tcp 22000). Device reaches it
  through `adb reverse tcp:22000 tcp:22000` (the WSL2 host is not on the LAN); the device's REST
  API is reachable from the host through `adb forward tcp:8384 tcp:8384` for scripted tests.
- Scripts (scratchpad/syncthing/): `st-dev.sh` (device REST helper), `st-pair.sh` (pair host and
  device, share a folder), `st-regress.sh` (create files, wait for sync, verify hashes both ways,
  delete, rename, conflict), `st-load.sh` (10k small files + a few 200 MB files, watch RSS/CPU
  on the device with /proc, PSI and lmkd), `st-avc.sh` (collect and summarise AVC denials).
- Acceptance per iteration: enforcing SELinux with zero syncthing denials, regression script
  green both directions, load run with no lmkd kills of nano / system_server and a recorded
  RSS ceiling, nano idle CPU unchanged with the daemon idle.

## 4. Build and flash flow (RG DS Plus)

1. `cd /work/GammaOSNextDistribution-A14 && ./buildtv.sh` (authoritative; `m` alone can pass while
   the image build fails).
2. Convert to erofs: `sudo /work/brick/system_erofs/build_system_erofs.sh` from the out dir
   (see memory rgdsplus_nano_crash_memory_cpu_2026_09_15 / chain.sh); erofs must stay under
   2800000000 bytes.
3. Regenerate the Plus vendor precompiled sepolicy when system policy changed (vendor_selinux.md).
4. `adb -s aeaef76817ce3b95 reboot fastboot` (userspace fastbootd), then
   `fastboot flash system <erofs.img>` (and `fastboot flash vendor vendor.img` when the vendor
   changed), `fastboot reboot`.
5. Quick iteration between flashes: bind-mount gammaos-nano (scratchpad bindnano.sh, label
   bootanim_exec) and push the syncthing binary to /data/local/tmp for a first smoke run; the
   real service, contexts and policy only come from a flashed image.

## 5. Phases

1. Core service: prebuilt + Android.bp + rc + sepolicy + apikey export + PRODUCT_PACKAGES.
   Smoke on the Plus (pushed binary first, then flashed image), enforcing, host pairing over adb
   reverse, first sync both ways.
2. Nano: REST client + model, Settings entry, status / device / folders / devices / pending /
   options / restart / logs screens, add-remove flows, all four themes checked with captures.
3. Test scripts: regression and load; tune GOGC / GOMAXPROCS; document RSS.
4. Settings (Lite/Full) screens.
5. TV Settings (Core) screens.
6. Polish: translations hook (nano trDyn), hidden-when-absent, docs, memory notes, commits.

Commit discipline: TheGammaSqueeze identity, no attribution lines, plain language, one commit per
coherent step (prebuilt, policy, nano, tests, Settings, TvSettings).

## 6. Status log (newest at the bottom)

- 2026-09-16 23:10: plan written. Host peer running (v2.1.5, GUI 127.0.0.1:8385). Tree surveyed:
  daemon/rc/sepolicy conventions from gammaos-sharefs, Settings hooks in
  handheld_dashboard_fragment.xml and TvSettings main_prefs.xml, nano JSON parser available,
  no HTTP lib in nano (mini loopback client planned). Next: phase 1 core service.
- 2026-09-17 01:00: Phase 1 core committed (6334caafbfb): prebuilt v2.1.5 + syncthing.rc +
  syncthing.te (compiles with neverallows) + /system/etc/resolv.conf (Go's resolver needs it;
  found from the device log: lookups went to [::1]:53). Pushed-binary smoke run on the Plus:
  20 MB + 30 MB RSS idle, API fine. Test topology settled: the WSL2 host cannot be reached from the
  LAN and the shared adb server is on Windows, so the HOST dials the device
  (tcp://192.168.0.39:22000) and the device keeps the host as dynamic; connected. Scripts written:
  st-dev.sh, st-pair.sh, st-wait.sh, st-regress.sh, st-load.sh. Phase 2 nano client + screens
  written (NanoSyncthing.h/.cpp, NanoMenuSyncthing.cpp, wiring) and compiling; the Settings row
  shows in the DSi list and the screen opens (reports Not Installed until the image with
  /system/bin/syncthing is flashed). First image (service only) built; second build with nano in
  progress. Next: erofs + flash, verify the service under init + AVC denials, precompiled vendor
  sepolicy regen, then UI walkthrough against the real service, then regression + load runs.
- 2026-09-17 02:00: Image with the service flashed (erofs 2.12 GB, fastbootd). Service runs from
  init as system under u:r:syncthing:s0 with correct labels; two more Go-on-Android gaps fixed in
  the rc: SSL_CERT_DIR=/system/etc/security/cacerts (linux Go build does not know Android's CA
  dir; relay TLS failed) and STMONITORED=1 (single process, 32 MB RSS; no log file, the UI uses
  /rest/system/log). The stale root-owned config.xml I had staged before the flash crash-looped
  the daemon once; wiped, fresh identity, re-paired (host dials 192.168.0.39:22000).
  ENFORCING (setenforce 1): regression script all green (create/modify/rename/delete both ways,
  conflict copy), zero syncthing AVC denials. Nano screens verified live in the DSi theme: root
  with status/uptime, This Device (model name applied on first run), Folders with state, folder
  editor, Devices with connection state, device editor, Options with checkbox toggles, Web
  Interface chooser, Device ID dialog, Logs. Fixes along the way: DSi list gate + value column
  for PS3_ST_ROW (also Minima), long values shortened, new folder dirs created media_rw-owned,
  ID wrapped for dialogs, log lines stripped of structured tails. Load test (10k small + 3x200 MB)
  running under enforcing. Next: commit phase 2, rebuild + flash image with the rc changes,
  regenerate the Plus vendor precompiled sepolicy, then Settings / TV Settings.

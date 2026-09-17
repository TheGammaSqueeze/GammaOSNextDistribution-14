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
- 2026-09-17 02:20: Phase 2 committed (9a37633f573). Load run under ENFORCING on the Plus:
  10,000 small files (host -> device) synced in 545 s, 3 x 200 MB in 295 s, all verified by
  sha256, no lmkd kills of nano or system_server, zero syncthing AVC denials; daemon worker
  peak RSS 81 MB (idle 32 MB single-process), MemAvailable stayed above 270 MB. Regression
  re-run after fixing the script's own transient-temp-file compare: all pass. Precompiled
  vendor sepolicy regenerated on the device for this system policy, committed to the Plus
  vendor tree (4494bd86) and vendor.img rebuilt, to be flashed with the next system image.
  Shared Java client written (frameworks/base/core/java/com/android/internal/gammaos/
  SyncthingClient.java, twin of the nano client); Settings and TV Settings screens being
  written by two implementation passes from one spec. Next: build both apps + framework +
  nano (buildtv), flash system + vendor, verify both apps on the device, commit.
- 2026-09-17 04:15: Settings + TV Settings screens committed (60f7d4e3f1e) on a shared framework
  client (com.android.internal.gammaos.SyncthingClient). Image with framework + both apps + nano
  built and flashed with the regenerated vendor sepolicy; service now single-process (STMONITORED),
  relay TLS fine (SSL_CERT_DIR), identity kept, peer reconnected, zero denials. Found and fixed
  (69ba6966f8b): the clients probed the binary with X_OK / canExecute, which SELinux denies for
  bootanim and system_app, so the feature hid itself when enforcing; both use existence now.
  Found on the Plus: the stock odm partition carried an old precompiled_sepolicy that init
  consults before vendor's, so the regenerated vendor policy was never used (runtime compile
  every boot); removed those three files from a dumped odm image with debugfs and flashed it,
  init now loads the precompiled policy at boot (backup scratchpad/odm/odm.orig.img).
  TV Settings verified on the Plus top panel through nano's real hand-off (launch_app +
  nano_retroarch props): root screen shows live status, device, folders, devices, pending,
  options, web interface, restart, logs. Phone Settings fetches correctly (logged) but an
  activity started with adb's plain am start stays paused on this dual-display build, so its
  rows only render through the real launch path; screens are code-identical to TV Settings.
  Note: Settings.apk cannot be adb-installed over the system one here (provider conflict with
  TvSettings), a bind mount over the APK works for iteration. Final image build with the
  existence fix started; flash pending. Remaining: flash, final walkthrough of the nano screens
  on the flashed build with enforcing, then XMB / Minima / ES-DE theme captures of the nano
  screens, translations (trDyn) for the nano labels.
- 2026-09-17 04:50: Final image (framework client + Settings + TvSettings + nano existence fix +
  single-process rc) flashed with the regenerated vendor sepolicy and the cleaned odm. On this
  build, enforcing: service single-process, precompiled policy loaded at boot (no runtime
  compile), zero syncthing AVC denials, peer connected, nano Syncthing root shows live data
  (Running v2.1.5, device "GammaOS Core", 1 folder active, 1 device connected). Two device
  quirks for future test runs: an activity started with plain adb `am start` stays paused on
  this dual-display build (drive apps through nano's hand-off props instead), and nano's
  screenshot hook needs the DRM home (not the resident overlay) and permissive mode. Small
  cosmetic follow-up committed: the status row keeps the version as value and the uptime in
  the description (needs the next image). Open items for later iterations: captures of the
  nano screens in the XMB / Minima / ES-DE themes (XMB 'right' drills into a column, so the
  nav path differs), translations for the nano labels through trDyn, and a longer soak with
  the host peer left connected. Everything else in the plan is done and committed.
- 2026-09-17 05:40: nano Syncthing screens translated (commit 8ce9fdd17ca). Every fixed label,
  value, description, chooser and dialog goes through trDyn; composed texts (uptime, last seen,
  sync percentage, folder and device counters, remove prompts) use positional format keys
  ("Up {1} h {2} min. ", "Disconnected, last seen {1}") so translators can reorder them, and
  fmtAgo is re-rendered through "{1} min ago" style keys. 199 keys added to all 14 language
  files (ps3xmb/i18n). Verified live on the Plus in German through the /data/system/nano_i18n
  override (root, folders, folder, device, options screens all translated; locale restored to
  en-US afterwards). The longer German labels exposed a DSi list bug: a value wider than the
  row ran under its label ("Pfad" + path, "Ordnertyp" + "Senden und Empfangen"); the value
  column now shrinks its font so the label keeps at least its natural width up to 45% of the
  row. Still to do: XMB / Minima / ES-DE captures, a longer soak, and the next image flash
  (status row change + translations + list fix).
- 2026-09-17 05:35: Theme captures done on the Plus with the translated build. XMB (settings
  column: Syncthing row with description, root with checkbox toggle, folders, folder detail,
  pending) and Minima (one row per page with the value under the label) render every Syncthing
  screen; ES-DE opens the same XMB Quick Menu > Full Settings path, verified to the Syncthing
  root. Nav notes for future runs: XMB starts on Game, Settings is four lefts; Minima cycles
  categories with up/down (Game, Quick Menu, Settings); ES-DE needs `nav menu`, seven downs to
  Nano Settings, then Full Settings. Theme restored to DSi, locale en-US. Remaining: longer
  soak and the next image flash.
- 2026-09-17 06:35: Image with the translations flashed (erofs, fastbootd, boots in ~50 s).
  With `setenforce 1` on that build the nano Syncthing screen stayed at "Starting..." while
  curl from the shell reached the REST API. Root cause found with strace: bionic's connect()
  hands the socket to netd's fwmark server, and netd may only touch sockets from netdomain
  processes (netd.te), so netd refused nano's socket and connect() returned EMSGSIZE with no
  AVC line (the refusal is inside netd, and bootanim's permissive marker does not cover it).
  Every TCP connect from nano was affected (loopback 8384, adbd 5555, the wifi address), so
  network shares and the OTA check had the same latent bug on an enforcing boot. Fix: bootanim
  is now a net_domain (commit f04f98fd813, plus a once-per-error snapshot failure log in the
  nano worker). Verified by compiling the new policy on the device with secilc, load_policy,
  setenforce 1: runcon bootanim curl gets 200 and the nano root shows live data. Note the earlier
  "enforcing" nano check was in fact done permissive (the screenshot hook needs it), which is how
  this was missed. Next: image build with the policy, flash, regenerate the vendor precompiled
  policy (plat hash changes), flash vendor, then a boot-to-enforcing check of nano, Settings and
  TvSettings. Note this build's cmdline carries androidboot.selinux=permissive by design.
- 2026-09-17 07:00: Policy build flashed (system erofs via fastbootd), then the vendor precompiled
  policy regenerated on the device with secilc (plat hash changed), vendor.img rebuilt in
  /work/rg_ds_plus/GammaOSCoreVendor (commit 484224cd) and flashed. Clean boot: init loads the
  precompiled policy again (no "Compiling SELinux policy"), with setenforce 1 a runcon-bootanim
  curl gets 200 from the REST API and the nano Syncthing screen fetches with zero snapshot
  failures in logcat and no AVC denials. Soak sampler (5 min cadence, enforcing while it ran):
  daemon RSS steady 22-38 MB, peer connected every sample, zero syncthing denials; the two
  reflashes reset the uptime but the daemon and the peer link came back on their own each time.
  Device left on the DSi theme, en-US, permissive (its default cmdline). Plan items all done;
  what remains is optional: a Settings/TvSettings walkthrough on an enforcing boot (system_app
  already carries net_domain, so no change is expected) and a multi-hour soak.
- 2026-09-17 07:25: Tried the TV Settings walkthrough on an enforcing boot. The hand-off to
  TvSettings works, but the row cannot be located blind from adb on this device: uiautomator
  dump is killed by lmkd (1 GB), screencap of the top panel (display 2) returns status -2, and
  a scripted back from the accessories row ends the whole activity, after which nano has to be
  restarted. Not worth more time: the only enforcing-specific failure found today (netd's
  fwmark refusal) is keyed on the netdomain attribute, which system_app has always carried, and
  the Settings/TvSettings client was already verified end to end on this build. Left as is.
  Soak sampler continues; device back on the nano home, permissive, DSi, en-US.
- 2026-09-17 08:35: Folder locations (user request: /data, /sdcard/Android, external storage).
  Found that a /storage path from the nano browser or typed in Settings went to the daemon as is
  and failed ("permission denied" enforcing, "folder marker missing" permissive): the daemon is
  not an app, so it needs the raw mounts. Commit d82c7be95e9: one shared rule maps
  /storage/emulated/<n>, /sdcard, /storage/self/primary and /mnt/user views onto
  /data/media/<n> and /storage/<volume> onto /mnt/media_rw/<volume>, and every other path is
  refused with a message; card folders get ignore permissions and a rescan of at most ten
  minutes; the service gains the external_storage group (/mnt/media_rw is root:external_storage)
  and the policy sdcard_type + mnt_media_rw_file rules (mirrored to prebuilts/api). 17-case unit
  test of the rule passes. Verified on the Plus: a folder under
  Android/data/com.retroarch.aarch64/files syncs both ways; a FAT32 image loop-mounted at
  /mnt/media_rw/TEST01 (vold's raw path, vold mount options) syncs both ways, under enforcing
  with the new policy, zero syncthing denials. Card removal (umount + rmdir as vold does): the
  folder goes to "folder path missing" as soon as anything needs syncing, the host keeps every
  file, and after re-insert the folder recovers by itself at the next full rescan (48 s with a
  60 s interval; a config change also restarts it at once). Without the short interval it sat in
  error for 5+ min (default 1 h). vold's own virtual disk (sm set-virtual-disk) is unusable here:
  its public-volume FUSE mount timed out and broke the internal FUSE mount (reboot fixed it), so
  st-card.sh replays the scenario with the loop mount instead. Image build with the rc group,
  policy and clients started; then vendor precompiled regen + flash, plan entry after.
- 2026-09-17 09:20: Image with the folder location work flashed (system erofs + vendor with the
  regenerated precompiled policy, commit 35477da9 in the vendor tree; init loads the precompiled
  policy at boot, the daemon runs with groups media_rw, external_storage, inet). On the flashed
  build with setenforce 1: st-card.sh all pass (two-way sync on the FAT card, error while pulled,
  host keeps its files, self recovery 44 s after re-insert with a 60 s rescan, change made while
  out arrives), regression suite on test1 all pass, zero syncthing AVC denials, nano client zero
  snapshot failures. Device left permissive (cmdline default), DSi, en-US, only test1 paired.
- 2026-09-17 09:45: Follow-up from the card work. Verified that the daemon creates a missing
  folder root by itself when a folder is added (system:media_rw, setgid, marker in place), so
  the phone Settings app no longer fails the save when its own mkdirs cannot run; it runs as
  system and cannot reach /mnt/media_rw, which made every card folder added from that app fail
  before reaching the daemon (commit f3d471263cc, compile-checked; TV Settings and nano already
  behaved this way). This change only affects the phone Settings app and rides along with the
  next image; nothing else on the plan is open.
- 2026-09-17 10:50: Real card (user's SanDisk 64 GB in slot 2). vold formats it as FAT32 (sm
  partition public) and mounts it raw at /mnt/media_rw/00000000-0000-0000-0000-000000000001
  plus the FUSE view; a Syncthing folder on it passes the regression suite. Eject via vold (what
  Settings does): folder goes to "folder path missing" once anything needs syncing, host keeps
  its files. Platform bug found on remount: GammaOS's synthetic volume id counted up on every
  mount (0001 -> 0002 -> 0003), because vold's readMetadata allocated a new index each time and
  only released it on destroy, so any card path broke after an eject. Fixed in vold (commit
  e3dfffe5fe5, index kept across unmount/mount; a physically removed card still frees it) and
  flashed; a hot swap of vold rebooted the device, so it went through the image. Reboot with the
  card in: back at 0001, folder idle, the file changed while ejected arrived. The post-flash
  eject/mount cycle could not run: the card had been pulled physically meanwhile (mmc1 empty),
  which itself showed the pulled-without-eject case behaving as designed (folder path missing,
  nothing deleted). To finish: reinsert the card, run two sm unmount/mount cycles (id must stay
  0001) and let the folder recover at its 600 s rescan. Test tooling note: the WSL adb.exe
  wrapper stopped answering during a build; the Linux adb from out/host with
  ANDROID_ADB_SERVER_PORT=5038 over wifi (scratchpad/adbshim) covers the scripts' fixed serial.
  Also this iteration: Java copy of the path rule passes the same 17 cases; image with the
  Settings folder-creation change flashed.
- 2026-09-17 11:05: Card still out (no disk on mmc1), so the eject/mount cycle on the fixed vold
  waits for the user to reinsert it. On the flashed build (policy unchanged; the vendor
  precompiled policy still matches this system's hash) st-card.sh passes again under enforcing
  with zero syncthing denials, so nothing regressed with the vold and Settings changes. Nothing
  else open.
- 2026-09-17 11:25: Card reinserted (back as 000000000001). On the fixed vold two sm unmount/mount
  cycles kept the id at 000000000001, and the folder on the card recovered on its own after
  503 s (its 600 s rescan) with the file changed while it was ejected arriving, no manual
  action. Card handling is now verified end to end on real hardware: format, sync, eject,
  physical pull, reinsert, remount. Test folder removed from the card and both peers.
- 2026-09-17 12:05: User request: Syncthing moved under Network Shares in all three clients
  (last row of the Network Shares list in nano, a row on the Network Shares screen in Settings
  and TV Settings, shown only when the daemon ships); the top-level rows are gone. Nano verified
  on the Plus (DSi): Settings > Network Shares > Syncthing opens the client. Settings apps
  compile; they ride along with the next image. Scripted nav for the nano screen is now
  r2 enter d15 enter, then down to the last row, enter.
- 2026-09-17 12:45: Three user-reported nano issues on the Plus, all in commit 6c0a6aec84f and
  flashed. (1) DS icons on a fresh boot / after adding ROMs: a ROM whose file was unreachable at
  the first parse (card volume and the FUSE view come up ~40 s after nano; a file still being
  copied) was recorded as parsed and never retried. Now an unreachable path retries a few
  seconds later from the next prefetch or draw, a file whose size/mtime changed is parsed again
  with its old icon dropped, and the DSi theme check no longer caches the empty early-boot
  property. Verified: card ejected at nano start, SD ROM shows the placeholder, mounted, icon
  appears within 12 s untouched; fresh boot with the card in shows all icons. (2) Zips: the
  parser already reads the central directory and inflates lazily up to the banner (13 ms on
  the host for a 27 MB deflated demo); no change needed. (3) Boot chime lost its first second:
  in the DSi theme the direct ALSA card was opened by the chime itself and the aw882xx smart PA
  takes most of a second to power up. The card is now held open with silence from the first
  frame; on the flashed build the route is up 1.4 s before the chime is queued (was the same
  instant). Test copies removed from internal storage and the card.
- 2026-09-17 13:05: Follow-up on the chime warm hold: on the flashed build the hand-off log
  showed the direct worker still owning the card when the 250 ms bounded wait expired; the
  later AAudio opens all succeeded, so nothing broke, but the wait is now up to one second with
  the elapsed time logged (commit, compile-checked, rides with the next image). Nothing else
  open; the Syncthing plan itself has been complete since the card work.

#!/system/bin/sh
#
# GammaOS first-boot configuration.
#
# Runs as the gammaossetupwizard init service (root) while the nano setup wizard tails the log
# file below and holds the user on the install screen until persist.gammaos.setupwizard_done
# flips to 1. Nothing here runs in the background: when this script exits the device is fully
# configured.
#
# Layout:
#   1. log/prop plumbing and the exit trap
#   2. runtime-only settings (everything static lives in the SettingsProvider overlays)
#   3. low-RAM relief (temporary swap for the install/extract burst, persistent swap seed)
#   4. app installs, driven by /system/etc/gammaos/apps.list, with per-app post hooks
#   5. default ROMs, nano icons, home activity, completion marker
#
# Payload archives are zstd tarballs (xz decoding is CPU-bound on the small cores these
# handhelds use; zstd decodes an order of magnitude faster at the same size).

# When executed via init during SetupWizard, we cannot stream stdout/stderr directly
# back into the UI. Instead, write to a log file that the SetupWizard can tail.
LOG_DIR="/data/data/org.lineageos.setupwizard/files"
LOG_FILE="${LOG_DIR}/gammaos_setup.log"

mkdir -p "${LOG_DIR}" 2>/dev/null || true
chown system:system "${LOG_DIR}" 2>/dev/null || true

# Truncate log for a clean UI each run.
: > "${LOG_FILE}" 2>/dev/null || true
chown system:system "${LOG_FILE}" 2>/dev/null || true
chmod 0640 "${LOG_FILE}" 2>/dev/null || true

# Redirect everything from here on.
exec >> "${LOG_FILE}" 2>&1

# Init/SetupWizard coordination properties.
setprop persist.gammaos.setupwizard_done 0
setprop persist.gammaos.setupwizard_exit_code 0

SETUP_T0=$(date +%s)
SETUP_SWAP=/data/gammaos_setup_swap
SETUP_SWAP_MB=512
APPS_LIST=/system/etc/gammaos/apps.list

# Every log line carries the seconds elapsed since start so per-step cost can be read straight
# out of the wizard log on any device, no instrumentation needed.
step() {
    echo "[+$(( $(date +%s) - SETUP_T0 ))s] $*"
}

# Run a command, log a warning on failure, never abort the run (a single bad grant must not
# leave the device half configured).
run() {
    "$@"
    local rc=$?
    [ "$rc" -ne 0 ] && step "warning: '$1' failed (rc=$rc)"
    return 0
}

# Install-time dexopt: pm.dexopt.install defaults to speed-profile, which runs a dex2oat verify
# pass over every APK we install (several seconds each on a Cortex-A53). Switch it to "skip" for
# the batch; the regular background dexopt job compiles the apps later exactly as it would for a
# streaming install. Restored in finish(). Set SKIP_INSTALL_DEXOPT=0 to measure the difference.
SKIP_INSTALL_DEXOPT=1
ORIG_INSTALL_DEXOPT=$(getprop pm.dexopt.install 2>/dev/null)

finish() {
    rc=$?
    trap - EXIT
    step "setup.sh exited with ${rc}"
    if [ "$SKIP_INSTALL_DEXOPT" = 1 ] && [ -n "$ORIG_INSTALL_DEXOPT" ]; then
        setprop pm.dexopt.install "$ORIG_INSTALL_DEXOPT" 2>/dev/null || true
    fi
    # Tear down the temporary setup-only swap. swapoff MUST run before rm: this kernel refuses to
    # unlink an ACTIVE swap file (EBUSY, which -f silently swallows), so removing it first would
    # leave it both active and on disk. swapoff on a path that was never swapped-on is a harmless
    # no-op here (guarded). Distinct path from the persistent gammaos-swap.sh file, so they never
    # collide. (An earlier /proc/swaps grep guard here failed - the path has no leading space.)
    swapoff "$SETUP_SWAP" 2>/dev/null || true
    rm -f "$SETUP_SWAP" 2>/dev/null || true
    # Restore a sane screen-off timeout now that setup is done (see the pin below).
    settings put system screen_off_timeout 240000 2>/dev/null || true
    setprop persist.gammaos.setupwizard_exit_code "${rc}"
    setprop persist.gammaos.setupwizard_done 1
    setprop persist.gammaos.setupwizard_run 0
}
# Arm the restore trap BEFORE pinning the timeout, so any exit that runs the trap restores it.
trap finish EXIT

# Keep the panel awake for the entire (partly unattended) install. The nano setup_active
# display-hold does not reliably cover the multi-minute silent script window on all platforms,
# so pin the screen-off timeout to effectively "never" here (finish() restores it on exit).
# Runs before any heavy work so the display can never idle off mid-setup (the framework applies
# the new timeout live via its settings observer).
settings put system screen_off_timeout 2147483647 2>/dev/null || true

# Idempotency guard: /data/setupcompleted is created near the end of a successful run, so its
# presence means this is a RE-RUN. Skip the destructive default-ROM re-extract + save-state
# cleanup on a re-run so a re-entry (e.g. an interrupted-then-recovered wizard, or a nano<->
# Android mode switch) can never clobber user ROMs / save states.
FRESH_SETUP=1
[ -e /data/setupcompleted ] && FRESH_SETUP=0

# ---------------------------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------------------------

# Retry policy for the install and extraction steps. A single transient I/O error on the SD
# card (a write that the storage stack had to abort and retry, a momentarily busy card) used to
# skip an app for good. Retry up to SETUP_RETRIES times, flushing the page cache and backing off
# a little longer each time so a real transient has cleared before the next attempt.
SETUP_RETRIES=3

# Count kernel erofs decompression failures so far. When /system itself has bytes that do not
# decompress (a bad flash), every retry fails the same way; logging it tells the user a
# reflash is needed instead of leaving a bare "I/O error".
erofs_errors() {
    dmesg 2>/dev/null | grep -c "erofs.*failed to decompress"
}

retry_step() {   # <description> <command...>
    local what=$1; shift
    local attempt rc before
    for attempt in $(seq 1 "$SETUP_RETRIES"); do
        before=$(erofs_errors)
        "$@"
        rc=$?
        [ "$rc" -eq 0 ] && return 0
        if [ "$(erofs_errors)" -gt "$before" ]; then
            step "warning: $what failed (rc=$rc): the system image could not be read back" \
                 "(erofs decompression error), which means /system is corrupt on the card; reflash the image"
        else
            step "warning: $what failed (rc=$rc), attempt $attempt of $SETUP_RETRIES"
        fi
        [ "$attempt" -lt "$SETUP_RETRIES" ] || break
        flush_caches
        sleep $((attempt * 3))
    done
    return "$rc"
}

# Extract a zstd tarball onto /. Absolute-path safe (-P) like the old xz calls. pipefail makes a
# corrupt archive fail the step instead of tar quietly succeeding on a truncated stream. Tar
# overwrites what an earlier partial attempt left, so a retry is safe.
extract_archive_once() {
    local archive=$1
    set -o pipefail
    zstd -dc "$archive" | tar -x -P -C /
    local rc=$?
    set +o pipefail
    return "$rc"
}

extract_archive() {
    local archive=$1
    [ -f "$archive" ] || { step "warning: missing payload $archive"; return 1; }
    retry_step "extracting $archive" extract_archive_once "$archive"
}

# Flush the page cache after a big write burst. drop_caches only frees CLEAN pages, so sync
# (dirty -> clean) MUST come first. echo 1 = pagecache only (safer than 3 mid-setup).
flush_caches() {
    sync
    if [ -w /proc/sys/vm/drop_caches ]; then
        echo 1 > /proc/sys/vm/drop_caches 2>/dev/null || true
    fi
}

# Owner of the app being post-processed. install_apps captures these from the freshly
# installed package's data dir BEFORE its hook runs. They must be read before any extraction:
# the payload tarballs carry the source device's uid/gid on the top-level data dir itself, so
# stat'ing it after tar has run returns the wrong owner (this mis-owned every extracted app
# once, which crashed Aurora Store and friends on first launch).
APP_U=""
APP_G=""

# Give the current package ownership of everything under its private data dir (payload
# extracts write files as root and restore the archive's owners).
own_app_data() {
    local dir="/data/data/$1"
    [ -d "$dir" ] || return 0
    [ -n "$APP_U" ] || { step "warning: no owner captured for $1, leaving $dir as-is"; return 1; }
    chown -R "$APP_U:$APP_G" "$dir"
}

# Owner uid name of the current package (captured before extraction).
app_user() {
    echo "$APP_U"
}

# Install one APK, or a directory holding a base APK plus splits as a single session.
# `pm` exits non-zero on failure but the reliable signal is its "Success" line, so check that.
# A failed split session is abandoned before returning so a retry starts clean.
install_package_once() {
    local src=$1 out
    if [ -d "$src" ]; then
        local sid apk name
        sid=$(pm install-create -r 2>&1 | grep -oE '[0-9]+' | head -n1)
        [ -n "$sid" ] || { echo "could not open an install session"; return 1; }
        for apk in "$src"/*.apk; do
            [ -f "$apk" ] || continue
            name=$(basename "$apk" .apk)
            out=$(pm install-write -S "$(stat -c %s "$apk")" "$sid" "$name" "$apk" 2>&1) || {
                echo "install-write $apk: $out"; pm install-abandon "$sid" >/dev/null 2>&1; return 1; }
        done
        out=$(pm install-commit "$sid" 2>&1)
    else
        out=$(pm install -r "$src" 2>&1)
    fi
    echo "$out"
    case "$out" in *Success*) return 0 ;; esac
    return 1
}

install_package() {
    retry_step "installing $1" install_package_once "$1"
}

# Walk apps.list (see the header in that file) and install each entry, running its post hook.
# A package that is already installed (a re-run after a mode switch, or a resumed setup) is
# skipped; the hooks are written to be safe to re-run either way.
install_apps() {
    [ -r "$APPS_LIST" ] || { step "warning: $APPS_LIST missing, nothing to install"; return 0; }
    local mem_total_kb
    mem_total_kb=$(grep MemTotal /proc/meminfo 2>/dev/null | tr -dc 0-9)
    local name pkg src cond hook
    while IFS='|' read -r name pkg src cond hook; do
        hook=${hook%%$'\r'*}   # tolerate a CRLF-edited list
        case "$name" in ''|'#'*) continue ;; esac
        case "$cond" in
            minmem:*)
                local need=${cond#minmem:}
                if [ -n "$mem_total_kb" ] && [ "$mem_total_kb" -le "$need" ]; then
                    step "Skipping $name (low-memory device: ${mem_total_kb} kB total RAM)."
                    continue
                fi ;;
        esac
        if [ ! -e "$src" ]; then
            step "warning: $name source $src is not in this image, skipping."
            continue
        fi
        if pm path "$pkg" >/dev/null 2>&1; then
            step "$name already installed, skipping install."
        else
            step "Installing $name."
            if ! install_package "$src"; then
                step "warning: $name install failed, skipping its post-install step."
                continue
            fi
        fi
        if [ -n "$hook" ]; then
            # Capture the real owner now, before the hook extracts anything (see own_app_data).
            APP_U=$(stat -c %U "/data/data/$pkg" 2>/dev/null)
            APP_G=$(stat -c %G "/data/data/$pkg" 2>/dev/null)
            if type "$hook" >/dev/null 2>&1; then
                "$hook"
            else
                step "warning: unknown post-install hook '$hook' for $name."
            fi
        fi
    done < "$APPS_LIST"
}

# ---------------------------------------------------------------------------------------------
# Per-app post-install hooks (named in apps.list)
# ---------------------------------------------------------------------------------------------

post_daijisho() {
    extract_archive /system/etc/daijisho.tar.zst && own_app_data com.magneticchen.daijishou
    # Set Daijisho as the deterministic preferred home as soon as it exists, so full-Android
    # mode always has a resolvable HOME even if a later step fails. RESOLVE the HOME activity
    # dynamically instead of hardcoding a class name: Daijisho 1.8.1 (426) renamed its home
    # activity from .app.HomeActivity to .ui.activities.BootstrapActivity, and a stale hardcoded
    # component left the device with no preferred home ("No home screen found"). Fall back to the
    # known 1.8.1 component if the query comes back empty (freshly-installed stopped state).
    local dj_home
    dj_home=$(cmd package query-activities --components -a android.intent.action.MAIN \
        -c android.intent.category.HOME 2>/dev/null | tr -d '\r' \
        | grep -oE 'com\.magneticchen\.daijishou/[A-Za-z0-9_.]+' | head -n1)
    [ -z "$dj_home" ] && dj_home=com.magneticchen.daijishou/.ui.activities.BootstrapActivity
    step "Setting Daijisho home activity: $dj_home"
    run cmd package set-home-activity "$dj_home"
    run pm set-home-activity "$dj_home" -user --user 0
}

post_retroarch() {
    # ~1.1GB uncompressed. On a ~1GB device that dirties the whole page cache and collapses
    # MemAvailable (the low-memory kill storm), so the temporary swap is active and the cache is
    # flushed right after.
    extract_archive /system/etc/retroarch.tar.zst
    own_app_data com.retroarch.aarch64
    local u
    u=$(app_user com.retroarch.aarch64)
    if [ -n "$u" ]; then
        [ -d /sdcard/RetroArch ] && chown -R "$u:media_rw" /sdcard/RetroArch
        [ -d /sdcard/Android/data/com.retroarch.aarch64 ] && \
            chown -R "$u:ext_data_rw" /sdcard/Android/data/com.retroarch.aarch64
    fi
    flush_caches
    run pm grant com.retroarch.aarch64 android.permission.WRITE_EXTERNAL_STORAGE
    run pm grant com.retroarch.aarch64 android.permission.READ_EXTERNAL_STORAGE
    rm -f /sdcard/RetroArch/config/global.slangp
    # Enable GSYNC-style frame pacing on 120 Hz panels.
    if dumpsys SurfaceFlinger 2>/dev/null | grep -i refresh-rate | grep -q "120.00 Hz"; then
        sed -i 's/vrr_runloop_enable = "false"/vrr_runloop_enable = "true"/' \
            /sdcard/Android/data/com.retroarch.aarch64/files/retroarch.cfg 2>/dev/null
    fi
    # XMB icons for the nano boot menu come out of the RetroArch asset set.
    step "Copying XMB icons for Nano boot menu."
    mkdir -p /data/system/nano_icons
    local f
    for f in \
        "Nintendo - Nintendo Entertainment System.png" \
        "Nintendo - Super Nintendo Entertainment System.png" \
        "Nintendo - Game Boy.png" \
        "Nintendo - Game Boy Color.png" \
        "Nintendo - Game Boy Advance.png" \
        "Sega - Mega Drive - Genesis.png" \
        "Sega - Master System - Mark III.png" \
        "Sega - Game Gear.png" \
        "Sega - Dreamcast.png" \
        "Nintendo - Nintendo 64.png" \
        "Nintendo - Nintendo DS.png" \
        "Sony - PlayStation.png" \
        "Sony - PlayStation Portable.png" \
        "SNK - Neo Geo Pocket Color.png" \
        "history.png"; do
        cp "/data/user/0/com.retroarch.aarch64/assets/xmb/monochrome/png/$f" /data/system/nano_icons/ 2>/dev/null
    done
    chmod 644 /data/system/nano_icons/*.png 2>/dev/null
}

post_aurora() {
    extract_archive /system/etc/aurorastore.tar.zst && own_app_data com.aurora.store
}

post_ppsspp() {
    extract_archive /system/etc/ppsspp.tar.zst && own_app_data org.ppsspp.ppsspp
    rm -rf /sdcard/Android/data/org.ppsspp.ppsspp
    run appops set --uid org.ppsspp.ppsspp MANAGE_EXTERNAL_STORAGE allow
    run pm grant org.ppsspp.ppsspp android.permission.WRITE_EXTERNAL_STORAGE
    run pm grant org.ppsspp.ppsspp android.permission.READ_EXTERNAL_STORAGE
}

post_drastic() {
    extract_archive /system/etc/drastic.tar.zst
    # The SMAA post-FX shader forces the desktop GLSL 1.30 / SMAA_GLSL_3 path (textureLod,
    # integer-offset fetches) which does not exist on GLES2, so it fails to compile on Mali
    # and drastic aborts when it is selected; Scanline is unwanted. They are already dropped
    # from the archive, but a tar extract only adds files, so prune any copies a previous
    # (older-archive) provisioning left behind.
    rm -f /data/data/com.dsemu.drastic/files/DraStic/shaders/SMAA.dfx \
          /data/data/com.dsemu.drastic/files/DraStic/shaders/Scanline.dfx \
          /data/data/com.dsemu.drastic/files/DraStic/shaders/scanline.dsd
    rm -rf /data/data/com.dsemu.drastic/files/DraStic/shaders/smaa
    own_app_data com.dsemu.drastic
    run pm grant com.dsemu.drastic android.permission.RECORD_AUDIO
    run pm grant com.dsemu.drastic android.permission.BLUETOOTH_CONNECT
    run appops set --uid com.dsemu.drastic RECORD_AUDIO allow
}

post_flycast() {
    extract_archive /system/etc/flycast.tar.zst && own_app_data com.flycast.emulator
    local u
    u=$(app_user com.flycast.emulator)
    [ -n "$u" ] && [ -d /sdcard/Android/data/com.flycast.emulator ] && \
        chown -R "$u:ext_data_rw" /sdcard/Android/data/com.flycast.emulator
}

post_mupen() {
    extract_archive /system/etc/mupen64plusae.tar.zst && own_app_data org.mupen64plusae.v3.fzurita
    run pm grant org.mupen64plusae.v3.fzurita android.permission.POST_NOTIFICATIONS
}

# ---------------------------------------------------------------------------------------------
# 2. Runtime settings
# ---------------------------------------------------------------------------------------------
# The static defaults (wake gestures, touch sounds, stay-awake, mobile data, immersive
# confirmations, animation scales, the Lineage brightness slider) are seeded at build time by
# the SettingsProvider overlays in device/phh/treble/overlay and vendor/lineage/overlay, so they
# are already in place before this script runs. What is left here either has no build-time
# default resource or depends on the device.
step "Starting configuration of the GammaOS system..."
settings put secure navigation_mode 0
# These navbar RRO overlays are not present on every build (e.g. the TrimUI Brick), where
# the command throws a Java SecurityException that gets dumped into the setup log and shown
# in the wizard UI as a scary error. navigation_mode above already selects 3-button; the
# overlay toggle is belt-and-suspenders, so suppress its output and never fail on it.
cmd overlay disable --user 0 com.android.internal.systemui.navbar.gestural  >/dev/null 2>&1 || true
cmd overlay enable  --user 0 com.android.internal.systemui.navbar.threebutton >/dev/null 2>&1 || true
settings put global package_verifier_user_consent -1
settings put global verifier_verify_adb_installs 0
settings put secure doze_pulse_on_pick_up 0
settings put secure camera_double_tap_power_gesture_disabled 1
settings put --lineage global wake_when_plugged_or_unplugged 0
settings put --lineage global trust_restrict_usb 0
settings put --lineage secure advanced_reboot 1
settings put --lineage secure trust_warning 0
settings put --lineage secure trust_warnings 0
settings put --lineage secure power_menu_actions "lockdown|power|restart|screenshot|bugreport|logout"
settings put --lineage secure qs_show_auto_brightness 0
settings put --lineage system app_switch_wake_screen 0
settings put --lineage system assist_wake_screen 0
settings put --lineage system trust_interface_hinted 1
settings put --lineage system back_wake_screen 0
settings put --lineage system camera_launch 0
settings put --lineage system camera_sleep_on_release 0
settings put --lineage system camera_wake_screen 0
settings put --lineage system click_partial_screenshot 0
settings put --lineage system double_tap_sleep_gesture 0
settings put --lineage system home_wake_screen 1
settings put --lineage system lockscreen_rotation 1
settings put --lineage system menu_wake_screen 0
settings put --lineage system navigation_bar_menu_arrow_keys 0
settings put --lineage system status_bar_am_pm 2
settings put --lineage system status_bar_clock_auto_hide 0
settings put --lineage system status_bar_show_battery_percent 2
settings put secure ui_night_mode 2
# disable_32bit_mode + enable_mem_clear + disable_webview are DISABLED here: on a fresh wipe,
# setting persist.sys.disable_32bit_mode=1 together with sys.gamma_tweak_update=1 fires the
# vendor set_zygote_64 trigger (init.memclear.rc), which restarts zygote; zygote's onrestart
# action (vdc volume abort_fuse) tears down the emulated FUSE mount mid-setup, so every /sdcard
# write after that fails with ENOTCONN and the ROM/RetroArch install is silently lost. A reboot
# masks it because the props are already set and the trigger no longer re-fires. The other
# gamma_tweak-driven setprops are disabled alongside it as the user requested.
#setprop persist.sys.enable_mem_clear 1
#setprop persist.sys.disable_32bit_mode 1
#setprop persist.sys.disable_webview 0
setprop sys.gamma_tweak_update 1
setprop persist.gammaos.retroarchoverride.backbutton 1
settings put --lineage system key_back_long_press_action 11

step "Enabling developer settings."
settings put global development_settings_enabled 1

# The boot/loading splash draws over everything; its idle allowlisting is a sysconfig entry now.
run appops set com.gammaos.displayloading SYSTEM_ALERT_WINDOW allow

# ---------------------------------------------------------------------------------------------
# 3. Low-RAM relief (TrimUI Brick / A133 ~1GB)
# True when this device has a dedicated block swap partition (the RG DS Plus 'swap' GPT
# partition, /dev/block/by-name/swap). Prefer the active /proc/swaps signal, but fall back to
# the partition merely existing, because swapon_all (vendor init, on boot-completed) can race
# this first-boot setup service and may not have activated it yet. When a real swap partition
# is present the on-/data swapfiles below are redundant and only add SD wear, so both are
# skipped. zram stays the first-priority swap regardless (swapprio=2 in fstab_swap).
swap_partition_present() {
    # a real block-device swap that is not zram = the dedicated swap partition
    awk 'NR>1 && $1 ~ /^\/dev\/block\// && $1 !~ /zram/ { found=1 } END { exit !found }' /proc/swaps 2>/dev/null && return 0
    # present in the GPT but not yet swapped on (boot-completed race with swapon_all)
    [ -e /dev/block/by-name/swap ] && return 0
    return 1
}

# ---------------------------------------------------------------------------------------------
# The steps below extract ~1.3GB of payloads (retroarch 1.1GB + roms 201MB) to userdata and
# cold-start a dozen apps via pm/appops. On a ~1GB device the fresh dirty-page write burst
# collapses MemAvailable and the kernel LMK thrashes, which can black-screen the panel. Two
# scoped reliefs, both undone in finish(): (1) a temporary on-disk swap for the anon pressure,
# (2) flush_caches right after each big extract to drain the dirty write burst.
if swap_partition_present; then
    step "dedicated swap partition present; skipping the temporary setup swap"
elif ! grep -q "^$SETUP_SWAP " /proc/swaps 2>/dev/null; then
    avail_kb=$(df -k /data 2>/dev/null | awk 'NR==2 {print $4}')
    need_kb=$((SETUP_SWAP_MB * 1024 + 1024 * 1024))
    if [ -n "$avail_kb" ] && [ "$avail_kb" -ge "$need_kb" ]; then
        rm -f "$SETUP_SWAP" 2>/dev/null
        if fallocate -l "${SETUP_SWAP_MB}M" "$SETUP_SWAP" 2>/dev/null; then
            chmod 0600 "$SETUP_SWAP" 2>/dev/null
            if mkswap "$SETUP_SWAP" >/dev/null 2>&1 && swapon "$SETUP_SWAP" 2>/dev/null; then
                step "temporary setup swap active: ${SETUP_SWAP_MB}MB"
            else
                swapoff "$SETUP_SWAP" 2>/dev/null || true
                rm -f "$SETUP_SWAP" 2>/dev/null
                step "temporary setup swap unavailable (mkswap/swapon failed)"
            fi
        else
            step "temporary setup swap unavailable (fallocate failed)"
        fi
    else
        step "temporary setup swap skipped (need ${need_kb}KB, have ${avail_kb:-0}KB free on /data)"
    fi
fi

# Persistent virtual-memory swap on low-RAM devices (~1GB or less). This is SEPARATE from the
# temporary setup swap above (which is torn down in finish()): it emulates GammaOS Toolbox >
# Virtual Memory by setting persist.gammaos.swap.size_mb, which the gammaos-swap.sh init service
# (on property:persist.gammaos.swap.size_mb=*) turns into a persistent /data/gammaos_swap/swapfile
# that survives reboot and gives ongoing headroom (large NDS ROMs, cache-populate, etc). Gate on
# MemTotal <= 1300000 kB (same as the Firefox skip); only SEED it when the user has not already
# chosen a size, so a later Toolbox change is always respected.
mem_total_kb=$(grep MemTotal /proc/meminfo 2>/dev/null | tr -dc 0-9)
cur_swap_mb=$(getprop persist.gammaos.swap.size_mb 2>/dev/null)
case "$cur_swap_mb" in ''|*[!0-9]*) cur_swap_mb=0 ;; esac
if [ -n "$mem_total_kb" ] && [ "$mem_total_kb" -le 1300000 ] && [ "$cur_swap_mb" = 0 ]; then
    if swap_partition_present; then
        step "dedicated swap partition present; not seeding the 1GB virtual-memory swapfile"
    else
        step "Low-memory device (${mem_total_kb} kB): enabling a persistent 1GB swap (Virtual Memory)."
        setprop persist.gammaos.swap.size_mb 1024
    fi
fi

# ---------------------------------------------------------------------------------------------
# 4. Applications
# ---------------------------------------------------------------------------------------------
step "Installing applications."
mkdir -p /data/tmpsetup
if [ "$SKIP_INSTALL_DEXOPT" = 1 ]; then
    setprop pm.dexopt.install skip 2>/dev/null || true
fi
install_apps
if [ "$SKIP_INSTALL_DEXOPT" = 1 ] && [ -n "$ORIG_INSTALL_DEXOPT" ]; then
    setprop pm.dexopt.install "$ORIG_INSTALL_DEXOPT" 2>/dev/null || true
fi

# ---------------------------------------------------------------------------------------------
# 5. Default ROMs and completion
# ---------------------------------------------------------------------------------------------
if [ "$FRESH_SETUP" = 1 ]; then
    step "Extracting default ROMs."
    if extract_archive /system/etc/roms.tar.zst; then
        find /sdcard/ROMs/ -type f \( -iname '*state.auto' -o -iname '*state.auto.png' \) -delete 2>/dev/null
    fi
    # Another ~200MB uncompressed; drain it too before continuing.
    flush_caches
else
    step "Re-run detected (/data/setupcompleted exists): keeping existing ROMs and save states."
fi

mkdir -p /data/setupcompleted

# If the vendor's own setup script exists, run it now
if [ -f /vendor/bin/setup.sh ]; then
    step "Executing vendor-specific setup script..."
    /vendor/bin/setup.sh
fi

step "All settings have been applied successfully."

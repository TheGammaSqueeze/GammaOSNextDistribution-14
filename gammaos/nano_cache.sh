#!/system/bin/sh
# nano_cache.sh — DE cache manager for GammaOS Nano quick resume
# Caches RetroArch ROM, core, config, saves, states, shared_prefs
# in Device Encrypted storage for fast boot resume via bind mounts.
#
# Operations: populate, mount, unmount, sync_back, sync_to_cache, clear_rom
# Triggered via sys.gammaos.nano.cache_op property from init.rc

CACHE="/data/system/nano_cache"
MANIFEST="$CACHE/manifest"
TAG="NanoCache"

log_i() { log -t "$TAG" -p i "$1"; }
log_w() { log -t "$TAG" -p w "$1"; }
log_e() { log -t "$TAG" -p e "$1"; }

# Atomic copy: write to a temp in the destination dir then rename into place, so
# a concurrent reader (a resume-boot cache scan) or an interrupted copy (a short
# game session powering off mid-populate) never observes a truncated destination.
# rename() within the same filesystem is atomic. Falls back nowhere: on failure
# the temp is removed and the previous destination (if any) is left intact.
atomic_copy() {
    local src="$1" dst="$2"
    local tmp="${dst}.tmp.$$"
    if cp -p "$src" "$tmp" 2>/dev/null && mv -f "$tmp" "$dst" 2>/dev/null; then
        return 0
    fi
    rm -f "$tmp" 2>/dev/null
    return 1
}

# Delta sync: copy file only if mtime or size differs
delta_sync_file() {
    local src="$1" dst="$2"
    [ ! -f "$src" ] && return 0
    if [ ! -f "$dst" ]; then
        atomic_copy "$src" "$dst" && return 0
        return 1
    fi
    local src_stat dst_stat
    src_stat=$(stat -c '%Y_%s' "$src" 2>/dev/null)
    dst_stat=$(stat -c '%Y_%s' "$dst" 2>/dev/null)
    if [ "$src_stat" != "$dst_stat" ]; then
        atomic_copy "$src" "$dst" && return 0
        return 1
    fi
    return 0
}

# Delta sync directory: sync all files in src dir to dst dir
delta_sync_dir() {
    local src_dir="$1" dst_dir="$2" filter="$3"
    [ ! -d "$src_dir" ] && return 0
    mkdir -p "$dst_dir" 2>/dev/null
    for f in "$src_dir"/*; do
        [ ! -f "$f" ] && continue
        local base=$(basename "$f")
        # If filter provided, only sync files matching the pattern
        if [ -n "$filter" ]; then
            case "$base" in
                ${filter}*) ;;
                *) continue ;;
            esac
        fi
        delta_sync_file "$f" "$dst_dir/$base"
    done
}

# Convert FUSE/sdcard paths to raw CE paths
to_raw_path() {
    local p="$1"
    p=$(echo "$p" | sed 's|^/storage/emulated/0/|/data/media/0/|')
    p=$(echo "$p" | sed 's|^/sdcard/|/data/media/0/|')
    # External SD: /storage/<UUID>/ -> /mnt/media_rw/<UUID>/
    # The gammaoscustomization init domain cannot access the user FUSE
    # view under /storage/, so rewrite external SD paths to the raw
    # vold mount at /mnt/media_rw/<UUID>/ which is readable from init.
    p=$(echo "$p" | sed 's|^/storage/|/mnt/media_rw/|')
    echo "$p"
}

# Resolve the active ROM path from launch_rom / qr_rom with file
# fallbacks. External SD paths (/storage/<UUID>/...) routinely exceed
# PROP_VALUE_MAX (92 bytes) and cause the property write in NanoMenu
# to silently fail; the backup files /data/system/nano_launch_rom.txt
# and /data/system/nano_qr_rom.txt keep the value reachable.
#
# resolve_launch_rom — for do_populate (retroarch): prefers launch_rom
#   (the current launch target), with qr_rom as a defensive fallback.
# resolve_qr_rom     — for do_populate_drastic: uses ONLY qr_rom. The
#   drastic NanoMenu path explicitly clears launch_rom, so a stale
#   launch_rom file from a previous retroarch launch must not bleed
#   into the drastic rom staging path.
resolve_launch_rom() {
    # For RetroArch populate: prefer file-backed paths over props at
    # each tier. Props silently fail for paths > 92 bytes, leaving
    # stale values from shorter previous-session paths. Files are
    # always written regardless of length.
    #
    # Priority chain:
    #   1. nano_launch_rom.txt (current session's launch target)
    #   2. sys.gammaos.nano.launch_rom prop (fallback if file missing)
    #   3. nano_qr_rom.txt (defensive: last QR-primed ROM)
    #   4. persist.gammaos.nano.qr_rom prop (last resort)
    local p=""
    if [ -s /data/system/nano_launch_rom.txt ]; then
        p=$(cat /data/system/nano_launch_rom.txt)
    fi
    if [ -z "$p" ]; then
        p="$(getprop sys.gammaos.nano.launch_rom)"
    fi
    if [ -z "$p" ] && [ -s /data/system/nano_qr_rom.txt ]; then
        p=$(cat /data/system/nano_qr_rom.txt)
    fi
    if [ -z "$p" ]; then
        p="$(getprop persist.gammaos.nano.qr_rom)"
    fi
    echo "$p"
}
resolve_qr_rom() {
    # Prefer the file-backed path over the persist prop. The file is
    # always written regardless of path length, but the persist prop
    # silently fails for paths > 92 bytes (PROP_VALUE_MAX). Without
    # this priority, a stale short prop survives across sessions and
    # populate_drastic caches the wrong ROM.
    local p=""
    if [ -s /data/system/nano_qr_rom.txt ]; then
        p=$(cat /data/system/nano_qr_rom.txt)
    fi
    if [ -z "$p" ]; then
        p="$(getprop persist.gammaos.nano.qr_rom)"
    fi
    echo "$p"
}

# Bind mount targets use /mnt/user/0/emulated/0/ — mount ON TOP of FUSE/sdcardfs.
# FUSE daemon doesn't see bind mounts at /data/media/0/ (separate namespace),
# but mounting on top of /mnt/user/0/emulated/0/ propagates to /storage/.
to_mnt_path() {
    local p="$1"
    p=$(echo "$p" | sed 's|^/data/media/0/|/mnt/user/0/emulated/0/|')
    p=$(echo "$p" | sed 's|^/storage/emulated/0/|/mnt/user/0/emulated/0/|')
    p=$(echo "$p" | sed 's|^/sdcard/|/mnt/user/0/emulated/0/|')
    echo "$p"
}

# Read a key from the manifest
manifest_get() {
    local key="$1"
    [ ! -f "$MANIFEST" ] && return 1
    grep "^${key}=" "$MANIFEST" 2>/dev/null | head -1 | sed "s/^${key}=//"
}

# Parse retroarch.cfg for a config value
cfg_get() {
    local cfg="$1" key="$2"
    grep "^${key} " "$cfg" 2>/dev/null | head -1 | sed 's/^[^"]*"//;s/"[^"]*$//'
}

# ============================================================
# POPULATE: Cache ROM + delta sync everything else
# ============================================================
do_populate() {
    log_i "populate: starting"

    local rom_path="$(resolve_launch_rom)"
    local core_path="$(getprop sys.gammaos.nano.launch_core)"

    # Fallback to QR persist core if launch prop was already cleared
    if [ -z "$core_path" ]; then
        core_path="$(getprop persist.gammaos.nano.qr_core)"
    fi

    if [ -z "$rom_path" ]; then
        log_w "populate: no launch_rom or qr_rom set, skipping"
        setprop sys.gammaos.nano.cache_ready 1
        return 0
    fi

    # Convert paths to raw CE
    local rom_raw=$(to_raw_path "$rom_path")
    local core_raw=$(to_raw_path "$core_path")

    # Core path may already be a raw data path
    case "$core_path" in
        /data/*) core_raw="$core_path" ;;
    esac

    # CE storage paths under /data/media/ are encrypted until the user
    # credential is unlocked. During QR boot, populate can fire before
    # CE is available. Wait up to 30s so the ROM path resolves.
    if [ ! -f "$rom_raw" ]; then
        case "$rom_raw" in
            /data/media/*)
                local ce_ready=$(getprop sys.user.0.ce_available)
                if [ "$ce_ready" != "true" ]; then
                    log_i "populate: ROM not yet visible, waiting for CE storage..."
                    local i=0
                    while [ $i -lt 60 ]; do
                        ce_ready=$(getprop sys.user.0.ce_available)
                        [ "$ce_ready" = "true" ] && break
                        sleep 0.5
                        i=$((i + 1))
                    done
                    if [ "$ce_ready" = "true" ]; then
                        log_i "populate: CE storage ready"
                    fi
                fi
                ;;
        esac
    fi
    if [ ! -f "$rom_raw" ]; then
        log_e "populate: ROM not found at $rom_raw"
        setprop sys.gammaos.nano.cache_ready 1
        return 1
    fi

    # Create cache directories
    mkdir -p "$CACHE/rom" "$CACHE/cores" "$CACHE/config" \
             "$CACHE/saves" "$CACHE/states" "$CACHE/shared_prefs" 2>/dev/null

    # 1. COPY ROM (first priority)
    local rom_file=$(basename "$rom_raw")
    local rom_dir=$(basename "$(dirname "$rom_raw")")
    local rom_basename="${rom_file%.*}"
    local cached_rom="$CACHE/rom/$rom_file"

    # Only copy if different ROM or changed
    local old_rom=$(manifest_get rom_cached)
    if [ "$old_rom" != "$rom_file" ]; then
        # Different ROM — clear old ROM first
        rm -f "$CACHE/rom/"* 2>/dev/null
        log_i "populate: caching ROM $rom_file"
        atomic_copy "$rom_raw" "$cached_rom"
    else
        delta_sync_file "$rom_raw" "$cached_rom"
    fi
    # Extract zip for cores that need fullpath (e.g. gpSP can't read zips)
    case "$rom_file" in
        *.zip|*.ZIP)
            log_i "populate: extracting zip $rom_file"
            unzip -o -q "$cached_rom" -d "$CACHE/rom/" 2>/dev/null
            ;;
    esac

    # 2. Delta sync core (clear old cores first — only one core at a time)
    if [ -n "$core_raw" ] && [ -f "$core_raw" ]; then
        local core_file=$(basename "$core_raw")
        local old_core=$(ls "$CACHE/cores/"*.so 2>/dev/null | head -1)
        if [ -n "$old_core" ] && [ "$(basename "$old_core")" != "$core_file" ]; then
            rm -f "$CACHE/cores/"*.so 2>/dev/null
            log_i "populate: cleared old core(s), caching $core_file"
        fi
        delta_sync_file "$core_raw" "$CACHE/cores/$core_file"
    fi

    # 3. Delta sync retroarch.cfg
    local config_raw="/mnt/user/0/emulated/0/Android/data/com.retroarch.aarch64/files/retroarch.cfg"
    if [ -f "$config_raw" ]; then
        delta_sync_file "$config_raw" "$CACHE/config/retroarch.cfg"
    fi

    # 4. Parse config for save/state directories
    local saves_raw="/data/media/0/RetroArch/saves"
    local states_raw="/data/media/0/RetroArch/states"
    if [ -f "$CACHE/config/retroarch.cfg" ]; then
        local cfg_saves=$(cfg_get "$CACHE/config/retroarch.cfg" "savefile_directory")
        local cfg_states=$(cfg_get "$CACHE/config/retroarch.cfg" "savestate_directory")
        [ -n "$cfg_saves" ] && saves_raw=$(to_raw_path "$cfg_saves")
        [ -n "$cfg_states" ] && states_raw=$(to_raw_path "$cfg_states")
    fi

    # 5. Delta sync saves (only files matching ROM basename)
    # Check both configured save dir AND the ROM directory
    local rom_parent_dir=$(dirname "$rom_raw")
    delta_sync_dir "$saves_raw" "$CACHE/saves" "$rom_basename"
    for f in "$rom_parent_dir/${rom_basename}".srm "$rom_parent_dir/${rom_basename}".sav; do
        [ -f "$f" ] && delta_sync_file "$f" "$CACHE/saves/$(basename "$f")"
    done

    # 6. Delta sync states (only files matching ROM basename)
    delta_sync_dir "$states_raw" "$CACHE/states" "$rom_basename"
    for f in "$rom_parent_dir/${rom_basename}".state*; do
        [ -f "$f" ] && delta_sync_file "$f" "$CACHE/states/$(basename "$f")"
    done

    # 7. Copy saves/states INTO the ROM cache directory so RetroArch
    # finds them alongside the ROM (no FUSE needed for save loading)
    for f in "$CACHE/saves/${rom_basename}"*; do
        [ -f "$f" ] && cp -p "$f" "$CACHE/rom/"
    done
    for f in "$CACHE/states/${rom_basename}"*; do
        [ -f "$f" ] && cp -p "$f" "$CACHE/rom/"
    done

    # 7. Delta sync shared_prefs
    local sprefs="/data/user/0/com.retroarch.aarch64/shared_prefs"
    if [ -d "$sprefs" ]; then
        delta_sync_dir "$sprefs" "$CACHE/shared_prefs"
    fi

    # 8. Write manifest atomically
    local core_file=$(basename "$core_raw" 2>/dev/null)
    local saves_mnt=$(to_mnt_path "$saves_raw")
    local states_mnt=$(to_mnt_path "$states_raw")
    cat > "${MANIFEST}.tmp" <<MEOF
rom_source=$rom_raw
rom_cached=$rom_file
rom_system_dir=$rom_dir
core_source=$core_raw
core_cached=$core_file
config_source=$config_raw
saves_dir=$saves_mnt
saves_raw=$saves_raw
states_dir=$states_mnt
states_raw=$states_raw
shared_prefs_dir=/data/user/0/com.retroarch.aarch64/shared_prefs
rom_parent_raw=$rom_parent_dir
rom_basename=$rom_basename
MEOF
    mv "${MANIFEST}.tmp" "$MANIFEST"
    chmod 0664 "$MANIFEST"

    # Make cache world-readable so RetroArch (app UID) can access
    # files directly via DE paths without FUSE
    chmod 0755 "$CACHE" "$CACHE/rom" "$CACHE/cores" "$CACHE/config" \
               "$CACHE/saves" "$CACHE/states" "$CACHE/shared_prefs" 2>/dev/null
    find "$CACHE" -type f -exec chmod 0644 {} \; 2>/dev/null

    # Verify staging before declaring the cache usable. An armed QR boot
    # against a cache missing its ROM previews nothing, and a launch
    # rewritten to a cache core path that does not exist strands
    # RetroArch in its menu. Mirror populate_drastic: a definitively
    # failed ROM stage also disarms QR so the next boot goes straight
    # to the XMB instead of a doomed resume.
    if [ ! -f "$cached_rom" ]; then
        log_e "populate: ROM staging failed ($rom_raw) -- clearing qr_prepared, cache not ready"
        setprop persist.gammaos.nano.qr_prepared 0
        return 1
    fi
    if [ -n "$core_raw" ] && [ ! -f "$CACHE/cores/$(basename "$core_raw")" ]; then
        log_e "populate: core staging failed ($core_raw) -- cache not ready"
        return 1
    fi

    log_i "populate: done (rom=$rom_file core=$core_file basename=$rom_basename)"
    setprop sys.gammaos.nano.cache_ready 1
}

# ============================================================
# MOUNT: Signal cache readiness for direct DE path launch.
# No FUSE bind mounts needed — RootWindowContainer passes DE
# cache paths directly in the intent. Saves/states are co-located
# with the ROM in nano_cache/rom/ so RetroArch finds them.
# ============================================================
do_mount() {
    log_i "mount: starting (direct DE path mode)"

    if [ ! -f "$MANIFEST" ]; then
        log_w "mount: no manifest found"
        return 1
    fi

    local rom_file=$(manifest_get rom_cached)
    local core_file=$(manifest_get core_cached)

    # Verify critical cached files exist and are readable
    if [ -z "$rom_file" ] || [ ! -f "$CACHE/rom/$rom_file" ]; then
        log_e "mount: cached ROM missing"
        return 1
    fi
    if [ -z "$core_file" ] || [ ! -f "$CACHE/cores/$core_file" ]; then
        log_e "mount: cached core missing"
        return 1
    fi
    if [ ! -f "$CACHE/config/retroarch.cfg" ]; then
        log_e "mount: cached config missing"
        return 1
    fi

    # Ensure world-readable (may have been created by a previous populate)
    chmod 0755 "$CACHE" "$CACHE/rom" "$CACHE/cores" "$CACHE/config" 2>/dev/null
    find "$CACHE" -type f -exec chmod 0644 {} \; 2>/dev/null

    log_i "mount: cache verified (rom=$rom_file core=$core_file)"
    log_i "mount: DE paths: ROM=$CACHE/rom/$rom_file"
    log_i "mount: DE paths: CORE=$CACHE/cores/$core_file"
    log_i "mount: DE paths: CONFIG=$CACHE/config/retroarch.cfg"
    setprop sys.gammaos.nano.cache_mounted 1
}

# ============================================================
# UNMOUNT: Lazy unmount all bind mounts
# ============================================================
do_unmount() {
    log_i "unmount: clearing cache_mounted flag (no bind mounts in DE direct mode)"
    setprop sys.gammaos.nano.cache_mounted 0
}

# ============================================================
# SYNC_BACK: Delta sync cache -> CE (after in-game teardown)
# ============================================================
do_sync_back() {
    log_i "sync_back: syncing cache -> CE"

    if [ ! -f "$MANIFEST" ]; then
        log_w "sync_back: no manifest"
        return 0
    fi

    local saves_raw=$(manifest_get saves_raw)
    local states_raw=$(manifest_get states_raw)
    local config_raw=$(manifest_get config_source)
    local sprefs_raw=$(manifest_get shared_prefs_dir)
    local rom_basename=$(manifest_get rom_basename)

    # In DE direct mode, RetroArch writes saves/states to nano_cache/rom/
    # (co-located with the ROM). Sync these back to CE locations.
    local rom_parent=$(manifest_get rom_parent_raw)

    # Sync saves from cache/rom/ and cache/saves/ -> CE
    if [ -n "$rom_parent" ] && [ -d "$rom_parent" ]; then
        for f in "$CACHE/rom/${rom_basename}".srm "$CACHE/rom/${rom_basename}".sav \
                 "$CACHE/saves/${rom_basename}".srm "$CACHE/saves/${rom_basename}".sav; do
            [ -f "$f" ] && delta_sync_file "$f" "$rom_parent/$(basename "$f")"
        done
    fi
    if [ -d "$CACHE/saves" ] && [ -d "$saves_raw" ]; then
        delta_sync_dir "$CACHE/saves" "$saves_raw" "$rom_basename"
    fi
    log_i "sync_back: saves done"

    # Sync states from cache/rom/ and cache/states/ -> CE
    if [ -n "$rom_parent" ] && [ -d "$rom_parent" ]; then
        for f in "$CACHE/rom/${rom_basename}".state* "$CACHE/states/${rom_basename}".state*; do
            [ -f "$f" ] && delta_sync_file "$f" "$rom_parent/$(basename "$f")"
        done
    fi
    if [ -d "$CACHE/states" ] && [ -d "$states_raw" ]; then
        delta_sync_dir "$CACHE/states" "$states_raw" "$rom_basename"
    fi
    log_i "sync_back: states done"

    # NOTE: Do NOT sync config cache -> CE during sync_back.
    # During cached mode, RetroArch may modify the config to reflect
    # the restricted cached environment (empty shader/system dirs, etc.).
    # Config only flows CE -> cache (during populate and sync_to_cache).
    log_i "sync_back: config skipped (CE -> cache only)"

    # Sync shared_prefs cache -> CE
    if [ -d "$CACHE/shared_prefs" ] && [ -d "$sprefs_raw" ]; then
        delta_sync_dir "$CACHE/shared_prefs" "$sprefs_raw"
        log_i "sync_back: shared_prefs done"
    fi

    log_i "sync_back: complete"
}

# ============================================================
# SYNC_TO_CACHE: Delta sync CE -> cache (on shutdown)
# ============================================================
do_sync_to_cache() {
    log_i "sync_to_cache: syncing CE -> cache"

    if [ ! -f "$MANIFEST" ]; then
        log_w "sync_to_cache: no manifest"
        return 0
    fi

    local saves_raw=$(manifest_get saves_raw)
    local states_raw=$(manifest_get states_raw)
    local config_raw=$(manifest_get config_source)
    local sprefs_raw=$(manifest_get shared_prefs_dir)
    local rom_basename=$(manifest_get rom_basename)

    # Sync saves CE -> cache (check both configured dir and ROM dir)
    local rom_parent=$(manifest_get rom_parent_raw)
    if [ -d "$saves_raw" ] && [ -d "$CACHE/saves" ]; then
        delta_sync_dir "$saves_raw" "$CACHE/saves" "$rom_basename"
    fi
    if [ -n "$rom_parent" ]; then
        for f in "$rom_parent/${rom_basename}".srm "$rom_parent/${rom_basename}".sav; do
            [ -f "$f" ] && delta_sync_file "$f" "$CACHE/saves/$(basename "$f")"
        done
    fi
    log_i "sync_to_cache: saves done"

    # Sync states CE -> cache (check both configured dir and ROM dir)
    if [ -d "$states_raw" ] && [ -d "$CACHE/states" ]; then
        delta_sync_dir "$states_raw" "$CACHE/states" "$rom_basename"
    fi
    if [ -n "$rom_parent" ]; then
        for f in "$rom_parent/${rom_basename}".state*; do
            [ -f "$f" ] && delta_sync_file "$f" "$CACHE/states/$(basename "$f")"
        done
    fi
    log_i "sync_to_cache: states done"

    # Sync config CE -> cache
    if [ -n "$config_raw" ] && [ -f "$config_raw" ]; then
        delta_sync_file "$config_raw" "$CACHE/config/retroarch.cfg"
        log_i "sync_to_cache: config done"
    fi

    # Sync shared_prefs CE -> cache
    if [ -d "$sprefs_raw" ] && [ -d "$CACHE/shared_prefs" ]; then
        delta_sync_dir "$sprefs_raw" "$CACHE/shared_prefs"
        log_i "sync_to_cache: shared_prefs done"
    fi

    log_i "sync_to_cache: complete"
}

# ============================================================
# CLEAR_ROM: Remove ROM from cache (normal exit only)
# ============================================================
do_clear_rom() {
    log_i "clear_rom: removing ROM from cache"
    rm -f "$CACHE/rom/"* 2>/dev/null

    # Update manifest: clear rom entries
    if [ -f "$MANIFEST" ]; then
        sed -i 's/^rom_cached=.*/rom_cached=/' "$MANIFEST"
        sed -i 's/^rom_source=.*/rom_source=/' "$MANIFEST"
    fi

    log_i "clear_rom: done"
}

# ============================================================
# POPULATE_DRASTIC: Cache drastic native libs + BIOS + ROM
# Drastic is a commercial emulator; we never ship its binary.
# We source libdrastic_arm64.so from the user's installed APK
# at runtime. If drastic isn't installed, fall back gracefully
# (the Nano path will use the intent launch instead of native
# quick-resume).
#
# Layout produced:
#   $CACHE/drastic/libdrastic_arm64.so
#   $CACHE/drastic/libdrastic_cpu.so
#   $CACHE/drastic/system/drastic_bios_arm7.bin
#   $CACHE/drastic/system/drastic_bios_arm9.bin
#   $CACHE/drastic/system/nds_firmware_modified.bin
#   $CACHE/drastic/system/game_database.xml
#   $CACHE/drastic/user/config/           (empty; drastic writes here)
#   $CACHE/drastic/rom/<romfile>.nds
#
# The /user and /system split mirrors drastic's "User/" and
# "DraStic/" virtual path prefixes (see DraSticPathCache.smali
# getRealPath). FakeJNI.DraSticPathCache::open translates those
# prefixes to the corresponding cache subdirectories.
# ============================================================
do_populate_drastic() {
    log_i "populate_drastic: starting"

    local dcache="$CACHE/drastic"

    # ---- Locate the installed drastic APK ----
    local apk_dir=""
    local apk_path=""
    for d in /data/app/~~*/com.dsemu.drastic-*; do
        [ -d "$d" ] || continue
        apk_dir="$d"
        apk_path="$d/base.apk"
        break
    done
    if [ -z "$apk_dir" ] || [ ! -f "$apk_path" ]; then
        log_w "populate_drastic: com.dsemu.drastic not installed"
        setprop sys.gammaos.nano.cache_ready 1
        return 1
    fi
    log_i "populate_drastic: drastic APK at $apk_path"

    # ---- Locate the source native lib dir ----
    # Android normally extracts native libs to <apk_dir>/lib/arm64/ when
    # extractNativeLibs=true, or leaves them inside the APK when false.
    # We handle both.
    local src_lib_dir="$apk_dir/lib/arm64"
    local use_unzip=0
    if [ ! -f "$src_lib_dir/libdrastic_arm64.so" ]; then
        log_i "populate_drastic: native libs not extracted, will unzip from APK"
        use_unzip=1
    fi

    # ---- Build cache directory structure ----
    # User writable subdirs drastic expects:
    #   backup/     -- .dsv save files (autosave, created fresh)
    #   savestates/ -- .dss save states
    #   microphone/ -- recorded mic input
    #   input_record/ -- replay records
    #   cheats/     -- .cht cheat files
    #   slot2/      -- GBA slot2 cartridge dumps
    # Missing subdirs trigger fclose(NULL) crashes when drastic's
    # open-for-write path fails with ENOENT.
    mkdir -p "$dcache/system" \
             "$dcache/user/config" \
             "$dcache/user/backup" \
             "$dcache/user/savestates" \
             "$dcache/user/microphone" \
             "$dcache/user/input_record" \
             "$dcache/user/cheats" \
             "$dcache/user/slot2" \
             "$dcache/rom" 2>/dev/null

    # ---- Copy / extract native libs ----
    if [ "$use_unzip" = "0" ]; then
        delta_sync_file "$src_lib_dir/libdrastic_arm64.so" "$dcache/libdrastic_arm64.so"
        delta_sync_file "$src_lib_dir/libdrastic_cpu.so"  "$dcache/libdrastic_cpu.so"
    else
        # unzip -j strips directory components
        unzip -o -j -q "$apk_path" "lib/arm64-v8a/libdrastic_arm64.so" -d "$dcache" 2>/dev/null
        unzip -o -j -q "$apk_path" "lib/arm64-v8a/libdrastic_cpu.so"  -d "$dcache" 2>/dev/null
    fi
    if [ ! -f "$dcache/libdrastic_arm64.so" ]; then
        log_e "populate_drastic: failed to obtain libdrastic_arm64.so"
        setprop sys.gammaos.nano.cache_ready 1
        return 1
    fi

    # ---- Binary patch: short-circuit drastic's initialize_audio ----
    #
    # Drastic's initialize_audio at libdrastic_arm64.so:0x1d760
    # calls libOpenSLES::slCreateEngine, which internally does a
    # binder waitForService("media.audio_flinger"). On cold boot,
    # that wait blocks for ~15 seconds because audioserver only
    # registers its binder service near sys.boot_completed time.
    # Drastic's DS CPU emulation is gated on initialize_audio
    # returning, so the user-visible first frame lands at T+15s.
    #
    # Patch: replace the function entry with a single `ret`. The
    # caller at 0x7304c does not check the return value, and
    # drastic's per-frame audio mix loop is gated on _SoundEnabled
    # config so the NULL audio objects at master+0x10..0x28 are
    # never touched after init when sound is disabled. Confirmed
    # safe via cross-agent disasm analysis.
    #
    # Offset: 0x1d760 (120672)
    # Original: ff 43 03 d1  (d10343ff = sub sp, sp, #0xd0)
    # Patched:  c0 03 5f d6  (d65f03c0 = ret)
    #
    # Idempotent: if already patched, no-op.
    local lib="$dcache/libdrastic_arm64.so"
    local cur_hdr
    cur_hdr=$(dd if="$lib" bs=1 count=4 skip=120672 2>/dev/null | od -An -v -tx1 | tr -d ' \n')
    if [ "$cur_hdr" = "ff4303d1" ]; then
        log_i "populate_drastic: patching libdrastic initialize_audio (0x1d760)"
        printf '\xc0\x03\x5f\xd6' | dd of="$lib" bs=1 count=4 seek=120672 conv=notrunc 2>/dev/null
        # Verify
        cur_hdr=$(dd if="$lib" bs=1 count=4 skip=120672 2>/dev/null | od -An -v -tx1 | tr -d ' \n')
        if [ "$cur_hdr" = "c0035fd6" ]; then
            log_i "populate_drastic: patch verified"
        else
            log_w "populate_drastic: patch verify FAILED (got $cur_hdr)"
        fi
    elif [ "$cur_hdr" = "c0035fd6" ]; then
        log_i "populate_drastic: libdrastic already patched"
    else
        log_w "populate_drastic: unexpected bytes at 0x1d760 ($cur_hdr) -- skipping patch"
    fi

    # Make libdrastic_arm64.so group-writable so gammaos-nano
    # (group system) can re-apply the patch if needed. 0664 instead
    # of the default 0644.
    chmod 0664 "$lib" 2>/dev/null
    chown root:system "$lib" 2>/dev/null

    # ---- Copy system files from drastic's app data (BIOS, firmware, db) ----
    # These live at /data/user/0/com.dsemu.drastic/files/DraStic/ on a
    # drastic install that has been launched at least once. On a fresh
    # install the BIOS bins may only exist inside drastic_bios.zip.
    local drastic_root="/data/user/0/com.dsemu.drastic/files/DraStic"
    if [ -d "$drastic_root/system" ]; then
        delta_sync_file "$drastic_root/system/drastic_bios_arm7.bin" "$dcache/system/drastic_bios_arm7.bin"
        delta_sync_file "$drastic_root/system/drastic_bios_arm9.bin" "$dcache/system/drastic_bios_arm9.bin"
        delta_sync_file "$drastic_root/system/nds_firmware_modified.bin" "$dcache/system/nds_firmware_modified.bin"
    fi
    # game_database.xml lives at the DraStic root, not under system/ --
    # drastic requests it via the bare "DraStic/game_database.xml"
    # virtual path.
    if [ -f "$drastic_root/game_database.xml" ]; then
        delta_sync_file "$drastic_root/game_database.xml" "$dcache/game_database.xml"
    fi

    # Fallback: if we don't have the extracted BIOS binaries, unzip them
    # from drastic_bios.zip (ships with the drastic APK assets).
    if [ ! -f "$dcache/system/drastic_bios_arm7.bin" ] || \
       [ ! -f "$dcache/system/drastic_bios_arm9.bin" ]; then
        if [ -f "$drastic_root/drastic_bios.zip" ]; then
            log_i "populate_drastic: extracting drastic_bios.zip"
            unzip -o -j -q "$drastic_root/drastic_bios.zip" \
                "drastic_bios_arm7.bin" "drastic_bios_arm9.bin" \
                -d "$dcache/system" 2>/dev/null
        else
            # Last-resort: pull from APK assets
            unzip -o -j -q "$apk_path" "assets/drastic_bios.zip" -d "$dcache" 2>/dev/null
            if [ -f "$dcache/drastic_bios.zip" ]; then
                unzip -o -j -q "$dcache/drastic_bios.zip" \
                    "drastic_bios_arm7.bin" "drastic_bios_arm9.bin" \
                    -d "$dcache/system" 2>/dev/null
                rm -f "$dcache/drastic_bios.zip"
            fi
        fi
    fi
    if [ ! -f "$dcache/system/drastic_bios_arm7.bin" ]; then
        log_w "populate_drastic: drastic_bios_arm7.bin missing (drastic may refuse to boot ROMs)"
    fi

    # ---- Copy user's drastic.cfg if it exists ----
    # Missing config is fine -- drastic builds one from defaults.
    if [ -f "$drastic_root/config/drastic.cfg" ]; then
        delta_sync_file "$drastic_root/config/drastic.cfg" "$dcache/user/config/drastic.cfg"
    fi

    # ---- Copy the ROM ----
    # Stage the ROM into the drastic DE cache. If any step of this
    # fails we clear qr_prepared so the next boot falls back to the
    # plain XMB instead of the drastic QR fast-path, which would
    # dead-lock on an empty rom dir.
    local rom_staged=0
    # Drastic nano uses its own ROM path file so it doesn't collide
    # with QR drastic state. Check it first, fall back to QR path.
    local rom_path=""
    if [ -s /data/system/nano_drastic_nano_rom.txt ]; then
        rom_path=$(cat /data/system/nano_drastic_nano_rom.txt)
    fi
    if [ -z "$rom_path" ]; then
        rom_path="$(resolve_qr_rom)"
    fi
    if [ -n "$rom_path" ]; then
        local rom_raw=$(to_raw_path "$rom_path")
        if [ -f "$rom_raw" ]; then
            local rom_file=$(basename "$rom_raw")
            # Clear ALL previous ROMs before copying the new one.
            # Previous code only cleared *.nds, leaving stale non-NDS
            # files from cross-system QR primes (e.g. a GBA ROM cached
            # when the user switched from a libretro QR to drastic QR).
            rm -f "$dcache/rom/"* 2>/dev/null
            atomic_copy "$rom_raw" "$dcache/rom/$rom_file"
            if [ -f "$dcache/rom/$rom_file" ]; then
                log_i "populate_drastic: cached ROM $rom_file"
                rom_staged=1
            else
                log_e "populate_drastic: failed to copy ROM from $rom_raw"
            fi
        else
            log_w "populate_drastic: ROM not found at $rom_raw (resolved from $rom_path)"
        fi
    else
        log_w "populate_drastic: no ROM path in launch_rom, qr_rom, or nano_*_rom.txt"
    fi
    if [ "$rom_staged" = "0" ]; then
        log_w "populate_drastic: ROM staging failed -- clearing qr_prepared so next boot falls back to XMB"
        setprop persist.gammaos.nano.qr_prepared 0
    fi

    # ---- Permissions: world-readable for dirs, writable for user-subdirs ----
    # The dirs under user/ need to be writable by the bootanim-domain
    # gammaos-nano process (UID 1003 "graphics") so drastic can create
    # its autosave .dsv files on first boot.
    chmod 0755 "$dcache" "$dcache/system" "$dcache/rom" 2>/dev/null
    chmod 0777 "$dcache/user" \
               "$dcache/user/config" \
               "$dcache/user/backup" \
               "$dcache/user/savestates" \
               "$dcache/user/microphone" \
               "$dcache/user/input_record" \
               "$dcache/user/cheats" \
               "$dcache/user/slot2" 2>/dev/null
    find "$dcache" -type f -exec chmod 0644 {} \; 2>/dev/null

    log_i "populate_drastic: done"
    setprop sys.gammaos.nano.cache_ready 1
}

# ============================================================
# Main dispatch
# ============================================================
OP="$1"
case "$OP" in
    populate)         do_populate ;;
    populate_drastic) do_populate_drastic ;;
    mount)            do_mount ;;
    unmount)          do_unmount ;;
    sync_back)        do_sync_back ;;
    sync_to_cache)    do_sync_to_cache ;;
    clear_rom)        do_clear_rom ;;
    *)
        log_e "unknown operation: $OP"
        exit 1
        ;;
esac

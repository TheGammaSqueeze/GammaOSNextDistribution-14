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

# Delta sync: copy file only if mtime or size differs
delta_sync_file() {
    local src="$1" dst="$2"
    [ ! -f "$src" ] && return 0
    if [ ! -f "$dst" ]; then
        cp -p "$src" "$dst" 2>/dev/null && return 0
        return 1
    fi
    local src_stat dst_stat
    src_stat=$(stat -c '%Y_%s' "$src" 2>/dev/null)
    dst_stat=$(stat -c '%Y_%s' "$dst" 2>/dev/null)
    if [ "$src_stat" != "$dst_stat" ]; then
        cp -p "$src" "$dst" 2>/dev/null && return 0
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

    local rom_path="$(getprop sys.gammaos.nano.launch_rom)"
    local core_path="$(getprop sys.gammaos.nano.launch_core)"

    # Fallback to QR persist properties if launch props were already cleared
    if [ -z "$rom_path" ]; then
        rom_path="$(getprop persist.gammaos.nano.qr_rom)"
    fi
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
        cp -p "$rom_raw" "$cached_rom"
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
# Main dispatch
# ============================================================
OP="$1"
case "$OP" in
    populate)       do_populate ;;
    mount)          do_mount ;;
    unmount)        do_unmount ;;
    sync_back)      do_sync_back ;;
    sync_to_cache)  do_sync_to_cache ;;
    clear_rom)      do_clear_rom ;;
    *)
        log_e "unknown operation: $OP"
        exit 1
        ;;
esac

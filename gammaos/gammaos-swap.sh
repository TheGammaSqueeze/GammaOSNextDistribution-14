#!/system/bin/sh

# GammaOS user-configurable virtual memory (swap file).
#
# Applies the swap size selected in Settings / TV Settings / the nano menu. The
# single source of truth is the persist property persist.gammaos.swap.size_mb
# (an integer number of megabytes; 0 or empty means swap is disabled). The swap
# file lives in a Device-Encrypted area of /data so it is reachable on every boot
# before the user unlocks the device.
#
# This runs as a oneshot service in the permissive gammaoscustomization SELinux
# domain, started from `on post-fs-data` (so /data is mounted) and restarted from
# an `on property:persist.gammaos.swap.size_mb=*` trigger so a size change in any
# settings UI applies immediately without a reboot. It is idempotent: a boot that
# already has the right swap file active does nothing, and it only rewrites the
# file when the requested size actually changes (avoiding needless flash wear).

SWAPDIR=/data/gammaos_swap
SWAPFILE=$SWAPDIR/swapfile
TAG=gammaos_swap

# Never let a typo fill the userdata partition. 16 GB is far above anything a
# handheld needs and still leaves room on the smallest supported storage.
MAX_MB=16384

log_i() { log -p i -t "$TAG" "$1"; }

size_mb=$(getprop persist.gammaos.swap.size_mb)
# Sanitize: anything that is not a run of digits is treated as 0 (disabled).
case "$size_mb" in
    ''|*[!0-9]*) size_mb=0 ;;
esac

is_active() { grep -q " $SWAPFILE " /proc/swaps 2>/dev/null || grep -q "^$SWAPFILE " /proc/swaps 2>/dev/null; }
file_bytes() { if [ -f "$SWAPFILE" ]; then stat -c %s "$SWAPFILE" 2>/dev/null || echo 0; else echo 0; fi; }

teardown() {
    if is_active; then
        swapoff "$SWAPFILE" 2>/dev/null && log_i "swapoff $SWAPFILE"
    fi
    rm -f "$SWAPFILE" 2>/dev/null
}

# Disabled -> make sure any existing swap file is off and removed.
if [ "$size_mb" -le 0 ]; then
    teardown
    log_i "swap disabled (persist.gammaos.swap.size_mb=$size_mb)"
    exit 0
fi

# Clamp an unreasonably large request instead of failing outright.
if [ "$size_mb" -gt "$MAX_MB" ]; then
    log_i "requested ${size_mb}MB exceeds cap, clamping to ${MAX_MB}MB"
    size_mb=$MAX_MB
fi

# RG DS Plus nano: the swap file is read and written on the microSD that also holds the ROMs. Idle I/O class
# (fallocate, mkswap and swapon inherit it), so a game's reads always go first; the swap partition and zram cover
# memory until the file is on. Full Android and other devices are unchanged.
if [ "$(getprop ro.gammaos.device)" = anbernicrgdsplus ] && [ "$(getprop sys.gammaos.minimal_boot)" = 1 ]; then
    if ionice -c 3 -p $$ 2>/dev/null; then
        log_i "idle I/O priority (a game's reads go first)"
    fi
fi

# RG DS Plus nano: swapon of the file reads every f2fs node block of it from the microSD (2081
# reads, 2.5 s on an idle system; 9-11 s at boot, overlapping zygote and system_server). zram and
# the swap partition (2.4 GB, higher priority) cover boot, which used under 200 MB of swap, so the
# boot-time property trigger leaves the file to the sys.boot_completed start, which waits until
# the game storage is up. A file that is already active (a resize) is not delayed.
if [ "$(getprop ro.gammaos.device)" = anbernicrgdsplus ] && [ "$(getprop sys.gammaos.minimal_boot)" = 1 ] \
        && ! is_active; then
    if [ "$(getprop sys.boot_completed)" != 1 ]; then
        log_i "swap file activation deferred until boot completes"
        exit 0
    fi
    if [ "$(cut -d. -f1 /proc/uptime)" -lt 300 ]; then
        n=0
        while [ "$(getprop sys.gammaos.nano.ext_storage_ready)" != 1 ] && [ $n -lt 120 ]; do sleep 1; n=$((n + 1)); done
        sleep 30
    fi
fi

# mksh does 32-bit arithmetic, so $((size_mb * 1024 * 1024)) wraps from 2048 MB up (8192 gives 0)
# and the size checks below never matched: the file was deleted and rewritten on every boot.
# toybox expr is 64-bit.
want_bytes=$(expr "$size_mb" \* 1048576)
have_bytes=$(file_bytes)

# Already active at exactly the requested size -> nothing to do (fast boot path).
if is_active && [ "$have_bytes" = "$want_bytes" ]; then
    log_i "swap already active at ${size_mb}MB"
    exit 0
fi

# Refuse to create a swap file we do not have room for. Keep a 512 MB headroom so
# enabling swap never wedges the data partition.
avail_kb=$(df -k "$SWAPDIR" 2>/dev/null | awk 'NR==2 {print $4}')
if [ -z "$avail_kb" ]; then
    avail_kb=$(df -k /data 2>/dev/null | awk 'NR==2 {print $4}')
fi
if [ -n "$avail_kb" ]; then
    # An inactive swap file (always the case at boot) is reused or replaced, so only the growth
    # plus the headroom has to be free. Counting its whole size again deleted a working file at
    # boot once /data had less than size + 512 MB free. An active file is not credited: if the
    # swapoff below fails, rm only unlinks it and its blocks stay in use. expr: have_bytes is
    # 64-bit; expr exits 1 for a result of 0, so only its output is used.
    have_kb=0
    if ! is_active; then
        have_kb=$(expr "$have_bytes" / 1024)
    fi
    need_kb=$(expr "$size_mb" \* 1024 + 524288 - "${have_kb:-0}")
    if [ "$avail_kb" -lt "$need_kb" ]; then
        log_i "not enough free space for ${size_mb}MB swap (avail ${avail_kb}KB), leaving swap off"
        teardown
        exit 1
    fi
fi

# Recreating: drop the old swap first so we can resize/rewrite the file safely.
if is_active; then
    swapoff "$SWAPFILE" 2>/dev/null && log_i "swapoff $SWAPFILE (resize)"
fi

mkdir -p "$SWAPDIR" 2>/dev/null
chmod 0700 "$SWAPDIR" 2>/dev/null

if [ ! -f "$SWAPFILE" ] || [ "$have_bytes" != "$want_bytes" ]; then
    log_i "(re)creating swap file: have ${have_bytes} bytes, want ${want_bytes} bytes"
    rm -f "$SWAPFILE"
    if ! fallocate -l "${size_mb}M" "$SWAPFILE" 2>/dev/null; then
        log_i "fallocate ${size_mb}M failed"
        rm -f "$SWAPFILE"
        exit 1
    fi
fi

chmod 0600 "$SWAPFILE" 2>/dev/null
if ! mkswap "$SWAPFILE" >/dev/null 2>&1; then
    log_i "mkswap failed"
    rm -f "$SWAPFILE"
    exit 1
fi
swapon_start=$SECONDS
if swapon "$SWAPFILE" 2>/dev/null; then
    log_i "swap enabled: ${size_mb}MB at $SWAPFILE (swapon took $((SECONDS - swapon_start))s)"
else
    log_i "swapon failed for $SWAPFILE"
    rm -f "$SWAPFILE"
    exit 1
fi
exit 0

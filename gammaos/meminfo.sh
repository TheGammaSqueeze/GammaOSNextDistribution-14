#!/system/bin/sh
#
# meminfo.sh - GammaOS memory usage reporter
#
# Shows memory usage the same way the GammaOS power menu does.
# This matches the Android framework's "available memory" calculation:
#   available = MemFree + Cached   (from /proc/meminfo)
#   used      = total  - available
#
# These numbers represent the WORST-CASE memory pressure: disk cache,
# file-backed pages, and reclaimable slabs are all counted as "available"
# because the kernel will reclaim them instantly when an app needs RAM.
# This is why the "used" figure here is LOWER than what Android Settings
# shows - Settings counts every byte attributed to a process (PSS) plus
# kernel/GPU overhead, even if most of it is reclaimable cache.
#
# No root required. Works over adb shell or a terminal emulator.
#
# The calculation is taken directly from:
#   frameworks/base/core/jni/android_util_Process.cpp
#     android_os_Process_getFreeMemory()  -> MemFree + Cached
#     android_os_Process_getTotalMemory() -> sysinfo(totalram)
#   Called by ActivityManager.getMemoryInfo() which the power menu reads.
#
# Usage:
#   adb shell sh /path/to/meminfo.sh

# Parse the fields we need from /proc/meminfo (values are in kB)
while IFS=': ' read -r key val _unit; do
    case "$key" in
        MemTotal)    mem_total_kb=$val ;;
        MemFree)     mem_free_kb=$val ;;
        Cached)      mem_cached_kb=$val ;;
    esac
done < /proc/meminfo

# Available = MemFree + Cached (same as android_os_Process_getFreeMemory)
mem_available_kb=$(( mem_free_kb + mem_cached_kb ))

# Used = Total - Available
mem_used_kb=$(( mem_total_kb - mem_available_kb ))

# Convert to MB for readability
mem_total_mb=$(( mem_total_kb / 1024 ))
mem_used_mb=$(( mem_used_kb / 1024 ))
mem_available_mb=$(( mem_available_kb / 1024 ))
mem_free_mb=$(( mem_free_kb / 1024 ))
mem_cached_mb=$(( mem_cached_kb / 1024 ))

# Usage percentage
if [ "$mem_total_kb" -gt 0 ]; then
    pct=$(( mem_used_kb * 100 / mem_total_kb ))
else
    pct=0
fi

echo "GammaOS Memory Usage (power menu style)"
echo "========================================="
echo "Total:      ${mem_total_mb} MB"
echo "Used:       ${mem_used_mb} MB  (${pct}%)"
echo "Available:  ${mem_available_mb} MB"
echo ""
echo "Breakdown:"
echo "  MemFree:  ${mem_free_mb} MB  (truly unused)"
echo "  Cached:   ${mem_cached_mb} MB  (reclaimable on demand)"

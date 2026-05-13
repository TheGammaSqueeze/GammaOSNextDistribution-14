#!/system/bin/sh
# GammaOS Nano joypad uevent fixup.
#
# After the 2026-05-13 boot-speed work (vold ACQUIRE_LOCK fix on
# supports*Checkpoint) apexd activates ~10 s earlier than before, which
# means trimui_inputd's TRIMUI Player1 uinput add netlink message and
# the apex loop/dm-verity uevent flood now arrive in ueventd's queue
# within ~1 s of each other. On the Brick this causes ueventd to drop
# the TRIMUI Player1 uevent, so /dev/input/event3 is never created.
# NanoMenu can't grab the joypad fd and the BTN_A -> RetroArch launch
# chain stalls (user sees the home app come up with unresponsive
# controls).
#
# Workaround: re-emit the "add" uevent on every /sys/class/input/event*
# the kernel has registered. ueventd handles each re-emit as a fresh
# add notification and creates the missing /dev/input/eventN nodes.
# We retry a few times because trimui_inputd may not have registered
# its uinput device yet when we first fire (the service's start trigger
# does not guarantee the uinput device is ready -- only that the
# binary is running).

TAG="joypad-uevent-fixup"
log -t "$TAG" -p i "starting"

# 30 s budget. The check loop sleeps 1 s between iterations and aborts
# as soon as /dev/input/event3 (TRIMUI Player1's evdev node) exists.
DEADLINE=$(($(date +%s) + 30))

while [ "$(date +%s)" -lt "$DEADLINE" ]; do
    found_any=0
    for d in /sys/class/input/event*/uevent; do
        if [ -f "$d" ]; then
            found_any=1
            echo add > "$d" 2>/dev/null
        fi
    done

    if [ "$found_any" = "1" ] && [ -e /dev/input/event3 ]; then
        log -t "$TAG" -p i "event3 ready, exit"
        exit 0
    fi

    sleep 1
done

log -t "$TAG" -p w "deadline reached, /dev/input/event3 still missing"
exit 0

#!/system/bin/sh

# GammaOS portrait-native mode (default OFF). Invoked as `gammaos-on-boot.sh
# --orient` from an `on property:persist.gammaos.nano.natural_orientation=portrait`
# trigger in gammaos.rc, so it runs AFTER persistent properties are loaded (they
# are NOT available at post-fs, where this service otherwise starts, so a guard
# read there is always empty) and BEFORE surfaceflinger (class core) reads the
# orientation. When the prop is portrait, override the vendor-set SurfaceFlinger
# primary display orientation to ORIENTATION_0 so a physically portrait panel
# presents portrait-native - letting landscape-locked apps (e.g. PPSSPP) render
# landscape and be presented rotated, instead of the black screen they get when
# WM-rotated into a portrait surface on a landscape-native panel. ro. props are
# write-once (the vendor sets ORIENTATION_270 early), so plain setprop is
# rejected - resetprop_phh writes the property area directly. Never restart
# surfaceflinger (its onrestart restarts zygote = soft reboot). The guard has no
# else, so an unset prop is a byte-for-byte no-op; [ -x ] fails closed.
if [ "$1" = "--orient" ]; then
    # persist.gammaos.nano.natural_orientation selects the SurfaceFlinger primary
    # display orientation for physically-portrait panels. The correct value is
    # device-specific because it encodes the panel's physical mount, not just a
    # software preference (phh does the same per-device, e.g. ORIENTATION_0 for
    # Rebecco/K70 and ORIENTATION_90 for bq/Aquaris_M10). Accept `portrait` (the
    # common case, == ORIENTATION_0) plus explicit 0/90/180/270 so each portrait
    # handheld can be dialled to its own mount. Anything else (unset, `landscape`,
    # `normal`) leaves the vendor default untouched = byte-for-byte no-op.
    so=""
    case "$(getprop persist.gammaos.nano.natural_orientation)" in
        portrait|0) so=ORIENTATION_0 ;;
        90)         so=ORIENTATION_90 ;;
        180)        so=ORIENTATION_180 ;;
        270)        so=ORIENTATION_270 ;;
    esac
    if [ -n "$so" ] && [ -x /system/bin/resetprop_phh ]; then
        /system/bin/resetprop_phh ro.surface_flinger.primary_display_orientation "$so"
        # This trigger fires when the persistent prop is loaded, which is after
        # surfaceflinger has already read (and cached) the vendor orientation.
        # Restart it once so it picks up the override. Its onrestart also restarts
        # zygote if zygote is already up - a single soft restart while portrait-native
        # mode is enabled, not a loop (the trigger fires once per boot).
        setprop ctl.restart surfaceflinger
    fi
    exit 0
fi

setprop ctl.start media.swcodec

for i in wpa p2p; do
    if [ ! -f /data/misc/wifi/${i}_supplicant.conf ]; then
        cp /vendor/etc/wifi/wpa_supplicant.conf /data/misc/wifi/${i}_supplicant.conf
    fi
    chmod 0660 /data/misc/wifi/${i}_supplicant.conf
    chown wifi:system /data/misc/wifi/${i}_supplicant.conf
done

if grep -qF android.hardware.boot /vendor/manifest.xml || \
   grep -qF android.hardware.boot /vendor/etc/vintf/manifest.xml; then
    bootctl mark-boot-successful
fi

setprop ctl.restart sec-light-hal-2-0
setprop ctl.stop storageproxyd

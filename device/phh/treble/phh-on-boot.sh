#!/system/bin/sh

vndk="$(getprop persist.sys.vndk)"
[ -z "$vndk" ] && vndk="$(getprop ro.vndk.version |grep -oE '^[0-9]+')"

[ "$(getprop vold.decrypt)" = "trigger_restart_min_framework" ] && exit 0

setprop ctl.start media.swcodec

for i in wpa p2p;do
	if [ ! -f /data/misc/wifi/${i}_supplicant.conf ];then
		cp /vendor/etc/wifi/wpa_supplicant.conf /data/misc/wifi/${i}_supplicant.conf
	fi
	chmod 0660 /data/misc/wifi/${i}_supplicant.conf
	chown wifi:system /data/misc/wifi/${i}_supplicant.conf
done

if [ -f /vendor/bin/mtkmal ];then
    if [ "$(getprop persist.mtk_ims_support)" = 1 ] || [ "$(getprop persist.mtk_epdg_support)" = 1 ];then
        setprop persist.mtk_ims_support 0
        setprop persist.mtk_epdg_support 0
        reboot
    fi
fi

if grep -qF android.hardware.boot /vendor/manifest.xml || grep -qF android.hardware.boot /vendor/etc/vintf/manifest.xml ;then
	bootctl mark-boot-successful
fi

setprop ctl.restart sec-light-hal-2-0
if find /sys/firmware -name support_fod |grep -qE .;then
	setprop ctl.restart vendor.fps_hal
fi

setprop ctl.stop storageproxyd

sleep 10

minijailSrc=/system/system_ext/apex/com.android.vndk.v28/lib/libminijail.so
minijailSrc64=/system/system_ext/apex/com.android.vndk.v28/lib64/libminijail.so
if [ "$vndk" = 27 ];then
    mount $minijailSrc64 /vendor/lib64/libminijail_vendor.so
    mount $minijailSrc /vendor/lib/libminijail_vendor.so
fi

if [ "$vndk" = 28 ];then
    mount $minijailSrc64 /vendor/lib64/libminijail_vendor.so
    mount $minijailSrc /vendor/lib/libminijail_vendor.so
    mount $minijailSrc64 /system/lib64/vndk-28/libminijail.so
    mount $minijailSrc /system/lib/vndk-28/libminijail.so
    mount $minijailSrc64 /vendor/lib64/libminijail.so
    mount $minijailSrc /vendor/lib/libminijail.so
fi

#Clear looping services
sleep 30
getprop | \
    grep restarting | \
    sed -nE -e 's/\[([^]]*).*/\1/g'  -e 's/init.svc.(.*)/\1/p' |
    while read -r svc ;do
        setprop ctl.stop "$svc"
    done

# GammaOS: Disable bloat packages that can't be removed at build time
# (APEX-bundled or deeply inherited). Only runs once per data wipe.
if [ ! -f /data/local/tmp/.gammaos_debloated ]; then
    for pkg in \
        com.android.rkpdapp \
        com.android.devicelockcontroller \
        com.android.messaging \
        com.android.providers.calendar \
        com.android.phone
    do
        pm disable-user --user 0 "$pkg" 2>/dev/null
    done
    touch /data/local/tmp/.gammaos_debloated
fi

# GammaOS: Aggressively compact all processes starting immediately.
# Loop for 60 seconds to catch processes as they spawn during boot.
# Full compaction (madvise PAGEOUT) pushes cold pages to ZRAM.
compact_all() {
    # system_server via PID (native path — stronger than compactAllSystem)
    ss_pid="$(pidof system_server 2>/dev/null)"
    [ -n "$ss_pid" ] && am compact native full "$ss_pid" 2>/dev/null
    # All managed (Java) processes
    for proc in \
        com.android.systemui \
        com.android.launcher3 \
        com.android.se \
        com.android.networkstack.process \
        com.android.inputmethod.latin \
        com.android.providers.media.module \
        android.ext.services \
        com.android.permissioncontroller
    do
        am compact full "$proc" 2>/dev/null
    done
}

end=$(($(date +%s) + 60))
while [ "$(date +%s)" -lt "$end" ]; do
    compact_all
    sleep 10
done

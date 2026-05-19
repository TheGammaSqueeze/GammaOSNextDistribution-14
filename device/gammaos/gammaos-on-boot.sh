#!/system/bin/sh

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

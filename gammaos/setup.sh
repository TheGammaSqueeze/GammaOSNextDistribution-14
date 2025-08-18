#!/system/bin/sh

echo "Starting configuration of the GammaOS system..."
        settings put global package_verifier_user_consent -1
	settings put secure doze_pulse_on_pick_up 0
	settings put secure camera_double_tap_power_gesture_disabled 1
	settings put secure wake_gesture_enabled 0
	settings put --lineage global wake_when_plugged_or_unplugged 0
	settings put --lineage global trust_restrict_usb 0
	settings put --lineage secure advanced_reboot 1
	settings put --lineage secure trust_warning 0
	settings put --lineage secure trust_warnings 0
	settings put --lineage secure power_menu_actions "lockdown|power|restart|screenshot|bugreport|logout"
	settings put --lineage secure qs_show_auto_brightness 0
	settings put --lineage secure qs_show_brightness_slider 1
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
	settings put --lineage system key_back_long_press_action 2
	settings put --lineage system lockscreen_rotation 1
	settings put --lineage system menu_wake_screen 0
	settings put --lineage system navigation_bar_menu_arrow_keys 0
	settings put --lineage system status_bar_am_pm 2
	settings put --lineage system status_bar_brightness_control 1
	settings put --lineage system status_bar_clock_auto_hide 0
	settings put --lineage system status_bar_show_battery_percent 2
	settings put secure immersive_mode_confirmations confirmed
	settings put secure ui_night_mode 2
	settings put global window_animation_scale 1
	settings put global transition_animation_scale 1
	settings put global animator_duration_scale 1
	settings put system sound_effects_enabled 0
	setprop persist.sys.enable_mem_clear 1
	setprop persist.sys.disable_32bit_mode 1
	setprop persist.sys.disable_webview 0
	setprop sys.gamma_tweak_update 1
    setprop persist.gammaos.retroarchoverride.backbutton 1
    settings put --lineage system key_back_long_press_action 11

setprop ctl.stop "tee-supplicant"

echo "Enabling developer settings and configuring system behaviors."
settings put global development_settings_enabled 1
settings put global stay_on_while_plugged_in 0
settings put global mobile_data_always_on 0

echo "Installing applications."
mkdir -p /data/tmpsetup

echo "Installing MiXplorer."
pm install /system/etc/MiXplorer_v6.64.3-API29_B23090720.apk

echo "Installing FireFox"
pm install /system/etc/firefox-fenix-139.0.multi.android-arm64-v8a.apk

echo "Installing flycast DC emulator." && \
pm install /system/etc/flycast-release.apk && \
launcheruser=$( stat -c "%U" /data/data/com.flycast.emulator) && \
launchergroup=$( stat -c "%G" /data/data/com.flycast.emulator)
#tar -xJvf /system/etc/flycast.tar.xz -P -C / && \
#chown -R $launcheruser:$launchergroup /data/data/com.flycast.emulator && \
#chown -R $launcheruser:ext_data_rw /sdcard/Android/data/com.flycast.emulator

echo "Installing M64Plus FZ N64 Emulator." && \
pm install /system/etc/mupen64plusae_3.0.335.apk && \
launcheruser=$( stat -c "%U" /data/data/org.mupen64plusae.v3.fzurita) && \
launchergroup=$( stat -c "%G" /data/data/org.mupen64plusae.v3.fzurita) && \
tar -xvf /system/etc/mupen64plusae.tar.gz -C / && \
chown -R $launcheruser:$launchergroup /data/data/org.mupen64plusae.v3.fzurita && \
pm grant org.mupen64plusae.v3.fzurita android.permission.POST_NOTIFICATIONS

echo "Installing PPSSPP PSP emulator." && \
pm install /system/etc/ppsspp_1.18.1.apk && \
launcheruser=$( stat -c "%U" /data/data/org.ppsspp.ppsspp) && \
launchergroup=$( stat -c "%G" /data/data/org.ppsspp.ppsspp) && \
tar -xJvf /system/etc/ppsspp.tar.xz -P -C / && \
chown -R $launcheruser:$launchergroup /data/data/org.ppsspp.ppsspp && \
rm -rf /sdcard/Android/data/org.ppsspp.ppsspp && \
appops set --uid org.ppsspp.ppsspp MANAGE_EXTERNAL_STORAGE allow && \
pm grant org.ppsspp.ppsspp android.permission.WRITE_EXTERNAL_STORAGE && \
pm grant org.ppsspp.ppsspp android.permission.READ_EXTERNAL_STORAGE

echo "Installing drastic DS emulator."
pm install /system/etc/drastic_r2.6.0.4a.apk
launcheruser=$( stat -c "%U" /data/data/com.dsemu.drastic)
launchergroup=$( stat -c "%G" /data/data/com.dsemu.drastic)
tar -xvf /system/etc/drastic.tar.gz -C /
chown -R $launcheruser:$launchergroup /data/data/com.dsemu.drastic

echo "Installing Daijisho." && \
mkdir -p /sdcard/daijisho && \
cp /system/etc/daijisho412.apk.xz /sdcard/daijisho && \
cd /sdcard/daijisho && \
xz -d daijisho412.apk.xz

pm install /sdcard/daijisho/daijisho412.apk

cd /
rm -rf /sdcard/daijisho
launcheruser=$( stat -c "%U" /data/data/com.magneticchen.daijishou) && \
launchergroup=$( stat -c "%G" /data/data/com.magneticchen.daijishou) && \
tar -xJvf /system/etc/daijisho.tar.xz -P -C / && \
chown -R $launcheruser:$launchergroup /data/data/com.magneticchen.daijishou

echo "Installing Aurora Store." && \
pm install /system/etc/AuroraStore_4.6.2.apk && \
launcheruser=$( stat -c "%U" /data/data/com.aurora.store) && \
launchergroup=$( stat -c "%G" /data/data/com.aurora.store) && \
tar -xvf /system/etc/aurorastore.tar.gz -C / && \
chown -R $launcheruser:$launchergroup /data/data/com.aurora.store

echo "Installing RetroArch." && \
pm install /system/etc/RetroArch_aarch64.apk && \
launcheruser=$(stat -c "%U" /data/data/com.retroarch.aarch64) && \
launchergroup=$(stat -c "%G" /data/data/com.retroarch.aarch64) && \
tar -xJvf /system/etc/retroarch.tar.xz -P -C / && \
chown -R $launcheruser:$launchergroup /data/data/com.retroarch.aarch64 && \
chown -R $launcheruser:media_rw /sdcard/RetroArch && \
chown -R $launcheruser:ext_data_rw /sdcard/Android/data/com.retroarch.aarch64

echo "Installing GammaOS Splash app."
pm install /system/etc/gammaos-displayloading.apk
appops set com.gammaos.displayloading SYSTEM_ALERT_WINDOW allow
cmd deviceidle whitelist +com.gammaos.displayloading
pm install system/etc/Toast.apk
pm grant bellavita.toast android.permission.POST_NOTIFICATIONS

echo "Granting permissions to applications."
cmd package set-home-activity com.magneticchen.daijishou/.app.HomeActivity
pm set-home-activity com.magneticchen.daijishou/.app.HomeActivity -user --user 0

echo "Extracting and setting up ROMs."
tar -xJvf /system/etc/roms.tar.xz -P -C / && \
find /sdcard/ROMs/ -type f \( -iname '*state.auto' -o -iname '*state.auto.png' \) -delete
find /sdcard/ROMs/ -type f \( -iname '*state.auto' -o -iname '*state.auto.png' \) -exec rm -f {} \;

echo "Granting read/write permissions to RetroArch."
pm grant com.retroarch.aarch64 android.permission.WRITE_EXTERNAL_STORAGE
pm grant com.retroarch.aarch64 android.permission.READ_EXTERNAL_STORAGE

mkdir -p /data/setupcompleted
sleep 4
settings put system screen_off_timeout 240000
rm /sdcard/RetroArch/config/global.slangp

tar -xvf /system/etc/gboard.tar.gz -C /
cd /sdcard/gboard/

#echo "Installing GBoard."
#session_id=$(pm install-create -r | cut -d '[' -f2 | cut -d ']' -f1)
#    for apk in *.apk; do
#        pm install-write $session_id $(basename $apk) $apk
#    done
#pm install-commit $session_id && \
#ime enable com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME
#cd /
#rm -rf /sdcard/gboard

#ime enable --user 0 com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME

# Enable GSYNC for high refresh rate devices
dumpsys SurfaceFlinger | grep -i refresh-rate | grep -q "120.00 Hz" && sed -i 's/vrr_runloop_enable = "false"/vrr_runloop_enable = "true"/' /sdcard/Android/data/com.retroarch.aarch64/files/retroarch.cfg

# If the vendor’s own setup script exists, run it now
if [ -f /vendor/bin/setup.sh ]; then
    echo "Executing vendor-specific setup script..."
    /vendor/bin/setup.sh
fi

echo "All settings have been applied successfully."

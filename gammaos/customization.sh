#!/system/bin/sh

if [ ! -d /data/setupcompleted ] && [ -z $(getprop persist.sys.device_provisioned) ]; then
    settings put global package_verifier_user_consent -1
    settings put system screen_off_timeout 1800000
    setenforce 0
    settings put secure navigation_mode 2
    cmd bluetooth_manager disable
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
    settings put --lineage system enable_taskbar 1
    settings put --lineage secure berry_black_theme 1
    settings put --lineage system navigation_bar_hint 0
    settings put --lineage system key_back_long_press_action 11

    echo "Installing Magisk."
    pm install /system/etc/magisk.apk
    am force-stop com.topjohnwu.magisk
    launcheruser=$(stat -c "%U" /data/data/com.topjohnwu.magisk)
    launchergroup=$(stat -c "%G" /data/data/com.topjohnwu.magisk)
    am force-stop com.topjohnwu.magisk
    tar -xvf /system/etc/magisk.tar.gz -C /
    am force-stop com.topjohnwu.magisk
    tar -xvf /system/etc/magisk2.tar.gz -C /
    am force-stop com.topjohnwu.magisk
    chown -R $launcheruser:$launchergroup /data/data/com.topjohnwu.magisk
    am force-stop com.topjohnwu.magisk
    chown -R $launcheruser:$launchergroup /data_mirror/data_de/null/0/com.topjohnwu.magisk
    am force-stop com.topjohnwu.magisk
    chown -R $launcheruser:$launchergroup /data_mirror/cur_profiles/0/com.topjohnwu.magisk
    am force-stop com.topjohnwu.magisk
    chown -R $launcheruser:$launchergroup /data_mirror/ref_profiles/com.topjohnwu.magisk
    am force-stop com.topjohnwu.magisk
    chown -R $launcheruser:$launchergroup /data/misc/profiles/cur/0/com.topjohnwu.magisk
    am force-stop com.topjohnwu.magisk
    chown -R $launcheruser:$launchergroup /data/misc/profiles/ref/com.topjohnwu.magisk
    am force-stop com.topjohnwu.magisk
    chown -R $launcheruser:$launchergroup /data/user_de/0/com.topjohnwu.magisk/
    am force-stop com.topjohnwu.magisk

    # If the vendor’s own customization script exists, run it now
    if [ -f /vendor/etc/customization.sh ]; then
        echo "Executing vendor-specific setup script..."
        /vendor/etc/customization.sh
    fi

    setprop ctl.stop "tee-supplicant"

else
    setenforce 0
    setprop ctl.stop "tee-supplicant"
    
    # If the vendor’s own customizationload script exists, run it now
    if [ -f /vendor/bin/customizationload.sh ]; then
        echo "Executing vendor-specific setup script..."
        /vendor/bin/customizationload.sh
    fi

fi

#!/vendor/bin/sh

# Check if VirtIO Wi-Fi is enabled. If so, create a mac80211_hwsim radio
# and run the DHCP client
wifi_virtio=`getprop ro.boot.qemu.virtiowifi`
case "$wifi_virtio" in
    1) wifi_mac_prefix=`getprop vendor.net.wifi_mac_prefix`
      if [ -n "$wifi_mac_prefix" ]; then
          /vendor/bin/mac80211_create_radios 1 $wifi_mac_prefix || exit 1
      fi
      setprop ctl.start dhcpclient_wifi
      ;;
esac

# set up the second interface (for inter-emulator connections)
# if required
my_ip=`getprop vendor.net.shared_net_ip`
case "$my_ip" in
    "")
    ;;
    *) ifconfig eth1 "$my_ip" netmask 255.255.255.0 up
    ;;
esac


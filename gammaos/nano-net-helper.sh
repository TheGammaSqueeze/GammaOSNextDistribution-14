#!/system/bin/sh
# GammaOS Nano network helper
#
# Nano boot menu runs as uid 1003 (graphics). Wi-Fi and Bluetooth
# service calls enforce caller uid against SHELL/ROOT, so the cmd
# binaries return empty when invoked from nano directly. This helper
# runs as root via init property trigger and writes plain-text
# results that nano reads back.
#
# Invocation: nano writes any needed args to
# /data/system/nano_net_arg.txt, sets sys.gammaos.nano.net_cmd=idle
# to force a transition, then sys.gammaos.nano.net_cmd=<cmd>. Init
# fires the nano-net-helper service which runs this script; the
# script reads the current cmd from the property and dispatches.
# Completion is signalled by sys.gammaos.nano.net_ack=<cmd>; results
# land in /data/system/nano_net_<cmd>.txt with mode 0644 so uid
# 1003 can read.

CMD=$(getprop sys.gammaos.nano.net_cmd)
OUT="/data/system/nano_net_${CMD}.txt"
ARG_FILE="/data/system/nano_net_arg.txt"

ARG=""
if [ -f "$ARG_FILE" ]; then
    ARG=$(cat "$ARG_FILE" 2>/dev/null)
fi

case "$CMD" in
    wifi_list)
        cmd wifi list-networks > "$OUT" 2>&1
        ;;
    wifi_scan_start)
        cmd wifi start-scan > "$OUT" 2>&1
        ;;
    wifi_scan_read)
        cmd wifi list-scan-results > "$OUT" 2>&1
        ;;
    wifi_status)
        cmd wifi status > "$OUT" 2>&1
        ;;
    wifi_enable)
        cmd wifi set-wifi-enabled enabled > "$OUT" 2>&1
        ;;
    wifi_disable)
        cmd wifi set-wifi-enabled disabled > "$OUT" 2>&1
        ;;
    wifi_connect)
        # ARG is tab-separated: ssid<TAB>security<TAB>password
        SSID=$(printf '%s' "$ARG" | awk -F '\t' '{print $1}')
        SEC=$(printf '%s' "$ARG" | awk -F '\t' '{print $2}')
        PASS=$(printf '%s' "$ARG" | awk -F '\t' '{print $3}')
        if [ -z "$SEC" ] || [ "$SEC" = "open" ]; then
            cmd wifi connect-network "$SSID" open > "$OUT" 2>&1
        else
            cmd wifi connect-network "$SSID" "$SEC" "$PASS" > "$OUT" 2>&1
        fi
        ;;
    wifi_forget)
        cmd wifi forget-network "$ARG" > "$OUT" 2>&1
        ;;
    bt_list)
        dumpsys bluetooth_manager > "$OUT" 2>&1
        ;;
    bt_scan_start)
        cmd bluetooth_manager scan start > "$OUT" 2>&1
        ;;
    bt_scan_read)
        dumpsys bluetooth_manager > "$OUT" 2>&1
        ;;
    bt_enable)
        cmd bluetooth_manager enable > "$OUT" 2>&1
        ;;
    bt_disable)
        cmd bluetooth_manager disable > "$OUT" 2>&1
        ;;
    bt_pair)
        cmd bluetooth_manager pair "$ARG" > "$OUT" 2>&1
        ;;
    bt_unpair)
        cmd bluetooth_manager unpair "$ARG" > "$OUT" 2>&1
        ;;
    idle|"")
        exit 0
        ;;
    *)
        echo "nano-net-helper: unknown cmd: $CMD" > "$OUT"
        ;;
esac

chmod 0644 "$OUT" 2>/dev/null
setprop sys.gammaos.nano.net_ack "$CMD"

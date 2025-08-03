#!/system/bin/sh -e
ADB_KEYS_FILE="/data/misc/adb/adb_keys"

if [[ -f "$ADB_KEYS_FILE" ]]; then
    echo "$0: '$ADB_KEYS_FILE' already exists, skipping"
else
    KEY=`getprop ro.boot.qemu.adb.pubkey`
    echo "$KEY" > "$ADB_KEYS_FILE"
    chmod 0640 "$ADB_KEYS_FILE"
fi

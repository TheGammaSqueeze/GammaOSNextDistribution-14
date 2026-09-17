#!/system/bin/sh
# On-device REST helper: st-api-device.sh METHOD PATH [BODYFILE]
key=$(grep -oE 'apikey>[^<]+' /data/misc/syncthing/config.xml | cut -d'>' -f2)
if [ -n "$3" ]; then curl -s -X "$1" -H "X-API-Key: $key" -H 'Content-Type: application/json' --data-binary @"$3" "http://127.0.0.1:8384$2"
else curl -s -X "$1" -H "X-API-Key: $key" "http://127.0.0.1:8384$2"; fi

#!/bin/bash
# REST helper for the two test peers. usage: st-dev.sh dev|host METHOD /rest/path [json-body]
# dev  = the RG DS Plus daemon, via the on-device helper (loopback 8384, key from config.xml)
# host = the local peer in scratchpad/syncthing/host_home (GUI 127.0.0.1:8385)
S=${ST_TEST_DIR:-$(dirname $0)/work}
D=aeaef76817ce3b95; who=$1; m=$2; p=$3; body=$4
if [ "$who" = host ]; then
  key=$(grep -oE 'apikey>[^<]+' $S/host_home/config.xml | cut -d'>' -f2)
  if [ -n "$body" ]; then curl -s -X "$m" -H "X-API-Key: $key" -H "Content-Type: application/json" --data "$body" "http://127.0.0.1:8385$p"
  else curl -s -X "$m" -H "X-API-Key: $key" "http://127.0.0.1:8385$p"; fi
else
  if [ -n "$body" ]; then printf '%s' "$body" > /tmp/st.body.$$; adb -s $D push /tmp/st.body.$$ /data/local/tmp/st.body >/dev/null 2>&1; rm -f /tmp/st.body.$$
    adb -s $D shell "sh /data/local/tmp/st-api-device.sh $m '$p' /data/local/tmp/st.body" | tr -d '\r'
  else adb -s $D shell "sh /data/local/tmp/st-api-device.sh $m '$p'" | tr -d '\r'; fi
fi

#!/system/bin/sh
# Raise the codec DAPM speaker power-down delay (pmdown_time) early, before the boot
# chime's speaker amp would power down (default is short, e.g. rk817 = 5000 ms). On a first
# boot the setup wizard idles far longer than that between the chime and the home, so the
# external speaker amp powers off and the vendor audio HAL never re-powers it -> the whole
# system is silent after setup (a reboot "fixes" it because a normal boot's continuous
# chime->ambiance bed never idles that long). Keeping the amp warm through the setup idle
# fixes it without holding card0 (which would EBUSY the audio HAL). Runs from init, not
# nano, so it is independent of nano's render mode. Device-agnostic: writes every codec
# dailink pmdown_time that exists, retried for a few seconds until the codec is probed.
TAG="nano-pmdown"
i=0
while [ $i -lt 20 ]; do
    wrote=0
    for f in /sys/devices/platform/*sound*/*/pmdown_time /sys/devices/platform/*sound*/pmdown_time; do
        if [ -w "$f" ]; then
            echo 3600000 > "$f" 2>/dev/null
            wrote=1
        fi
    done
    if [ "$wrote" = "1" ]; then
        log -t "$TAG" -p i "raised pmdown_time to keep the speaker amp warm through setup"
        exit 0
    fi
    sleep 0.5
    i=$((i + 1))
done
log -t "$TAG" -p e "no codec pmdown_time node found"
exit 0

#!/bin/bash
# Removable card scenario without a card: a FAT32 image loop-mounted at the raw path vold uses
# (/mnt/media_rw/<volume>), a folder synced on it both ways with the host peer, then the card is
# "pulled" (umount + rmdir, as vold does), a change is made on the host meanwhile, and the card is
# put back; the folder must go to "folder path missing" while out, delete nothing on the host, and
# recover on its own at the next rescan. usage: st-card.sh [rescanS]   (default 60)
# Do not use vold's virtual disk (sm set-virtual-disk) for this on a 1 GB device: its FUSE mount
# for the public volume fails and takes the internal FUSE mount down with it.
S=${ST_TEST_DIR:-$(dirname $0)/work}
D=aeaef76817ce3b95; FID=cardtest; VOL=TEST01; DP=/mnt/media_rw/$VOL/sync; HP=$S/host_share/$FID; IMG=/data/local/tmp/cardtest.img
RESCAN=${1:-60}; fail=0; ok(){ echo "PASS $1"; }; bad(){ echo "FAIL $1"; fail=1; }
st(){ $S/st-dev.sh $1 GET "/rest/db/status?folder=$FID" | python3 -c "import sys,json;d=json.load(sys.stdin);print(d.get('state'),d.get('localFiles'),d.get('needFiles'),(d.get('error') or '')[:40])" 2>/dev/null; }
mnt(){ adb -s $D shell "L=\$(losetup -a | grep cardtest | cut -d: -f1); [ -z \"\$L\" ] && { L=\$(losetup -f); losetup \$L $IMG; }; mkdir -p /mnt/media_rw/$VOL; mount -t vfat -o uid=1023,gid=1023,fmask=7,dmask=7 \$L /mnt/media_rw/$VOL" >/dev/null; }
echo "== card image"
adb -s $D shell "[ -f $IMG ] || { dd if=/dev/zero of=$IMG bs=1M count=320 status=none; L=\$(losetup -f); losetup \$L $IMG; newfs_msdos -F 32 -c 8 -O android -L $VOL \$L >/dev/null 2>&1; losetup -d \$L; }" >/dev/null
mnt; adb -s $D shell "mount | grep -q /mnt/media_rw/$VOL" || { echo "card mount failed"; exit 1; }
$S/st-pair.sh $FID $DP $HP || bad "pair"
echo "== sync both ways"
mkdir -p $HP/fromhost; head -c 300000 /dev/urandom > $HP/fromhost/a.bin
adb -s $D shell "mkdir -p '$DP/fromdev'; head -c 200000 /dev/urandom > '$DP/fromdev/b.bin'" >/dev/null
$S/st-dev.sh host POST "/rest/db/scan?folder=$FID" >/dev/null; $S/st-dev.sh dev POST "/rest/db/scan?folder=$FID" >/dev/null; sleep 5; $S/st-wait.sh $FID 120 >/dev/null
[ -f $HP/fromdev/b.bin ] && adb -s $D shell "[ -f '$DP/fromhost/a.bin' ]" && ok "two-way sync on the card" || bad "two-way sync on the card"
$S/st-dev.sh dev PATCH /rest/config/folders/$FID "{\"rescanIntervalS\":$RESCAN}" >/dev/null; sleep 3
echo "== pull the card"
adb -s $D shell "umount /mnt/media_rw/$VOL && rmdir /mnt/media_rw/$VOL" >/dev/null
echo late > $HP/fromhost/late.txt; $S/st-dev.sh host POST "/rest/db/scan?folder=$FID" >/dev/null; sleep 8
case "$(st dev)" in error*) ok "folder in error while the card is out ($(st dev))";; *) bad "folder not in error while out ($(st dev))";; esac
[ "$(cd $HP && find . -type f -not -path './.stfolder*' | wc -l)" = 3 ] && ok "host kept its files" || bad "host lost files"
echo "== put the card back (no manual scan)"
mnt; t0=$(date +%s); rec=""
for i in $(seq 1 $(( RESCAN / 5 + 12 ))); do sleep 5; case "$(st dev)" in idle*) rec=$(( $(date +%s) - t0 )); break;; esac; done
[ -n "$rec" ] && ok "recovered on its own after ${rec}s (rescan $RESCAN s)" || bad "no recovery within $(( RESCAN + 60 ))s"
$S/st-wait.sh $FID 120 >/dev/null; adb -s $D shell "[ -f '$DP/fromhost/late.txt' ]" && ok "change made while out arrived" || bad "change made while out missing"
echo "== cleanup"
$S/st-dev.sh dev DELETE /rest/config/folders/$FID >/dev/null; $S/st-dev.sh host DELETE /rest/config/folders/$FID >/dev/null
adb -s $D shell "umount /mnt/media_rw/$VOL 2>/dev/null; rmdir /mnt/media_rw/$VOL 2>/dev/null; L=\$(losetup -a | grep cardtest | cut -d: -f1); [ -n \"\$L\" ] && losetup -d \$L; rm -f $IMG" >/dev/null
[ $fail = 0 ] && echo "CARD: ALL PASS" || echo "CARD: FAILURES"; exit $fail

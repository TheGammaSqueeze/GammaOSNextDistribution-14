#!/bin/bash
# Verify the three ways a share is reachable, which are three different consumers with three
# different mechanisms:
#
#   1. /mnt/shares/<name>   - the menu, which reads plain paths
#   2. /storage/<name>      - apps that open a file by name, which is every emulator on this device
#   3. content:// SAF root  - apps that use the system picker
#
# All three have to work, and the second has to be indistinguishable from internal storage, because
# an app that can tell the difference will treat a share as second-class or refuse it outright.

set -u
NAME="${1:-Share 1}"
AUTH="com.gammaos.shares.documents"
PASS=0; FAIL=0
ok()  { PASS=$((PASS+1)); printf "  PASS  %s\n" "$1"; }
bad() { FAIL=$((FAIL+1)); printf "  FAIL  %s -- %s\n" "$1" "${2:-}"; }
D() { adb shell "$@" 2>&1 | tr -d '\r'; }

MEDIUM=ec1407c3860bab86b16c866ab6b1d5094203967cd27ea52277632a9f4be5f5ed

# Several checks below assert against the test fixture (media/medium.bin with a known checksum,
# docs/tiny.txt at exactly 1024 bytes). Pointed at somebody's real share those files do not exist,
# and the suite reported five failures that were nothing but the fixture being absent - which is the
# mirror image of a test passing for the wrong reason, and just as misleading. Detect the fixture
# once and skip those checks explicitly rather than failing them.
HAVE_FIXTURE=no
adb shell "[ -f '/mnt/shares/$NAME/media/medium.bin' ] && echo y" 2>/dev/null | tr -d '\r' | grep -q y \
    && HAVE_FIXTURE=yes
skip() { printf "  ....  SKIP  %s (share has no test fixture)\n" "$1"; }

echo "=============================================================="
echo " share reachability: '$NAME'"
echo "=============================================================="

# Prove the rig before testing the product.
#
# The device reaches the test servers only through adb reverse tunnels, and those are silently lost
# whenever adbd restarts. When that happened, every check here failed with "No route to host" and
# read exactly like the share had broken. Checking first means a dead rig is reported as a dead rig.
TUNNELS=$(adb reverse --list 2>/dev/null | wc -l)
if [ "${TUNNELS:-0}" -lt 5 ]; then
    echo "  ABORT: only $TUNNELS adb reverse tunnels are up, so the device cannot reach the test"
    echo "         servers. Re-run postflash.sh. This is the rig, not the share."
    exit 99
fi
echo "  rig: $TUNNELS reverse tunnels up"

echo "-- 1. the menu's view (/mnt/shares) --"
D "mountpoint -q '/mnt/shares/$NAME' && echo y" | grep -q y \
    && ok "mounted at /mnt/shares/$NAME" || bad "mounted at /mnt/shares/$NAME" "not a mount point"
if [ "$HAVE_FIXTURE" = yes ]; then
  [ "$(D "sha256sum '/mnt/shares/$NAME/media/medium.bin' 2>/dev/null | cut -d' ' -f1")" = "$MEDIUM" ] \
      && ok "4MB read via /mnt/shares is byte-exact" || bad "4MB read via /mnt/shares" "checksum mismatch"
else skip "byte-exact read via /mnt/shares"; fi

echo "-- 2. the app path view (/storage), which must match internal storage --"
D "ls -d '/storage/$NAME'" | grep -q "$NAME" \
    && ok "visible at /storage/$NAME" || bad "visible at /storage/$NAME" "not present"
if [ "$HAVE_FIXTURE" = yes ]; then
  [ "$(D "sha256sum '/storage/$NAME/media/medium.bin' 2>/dev/null | cut -d' ' -f1")" = "$MEDIUM" ] \
      && ok "4MB read via /storage is byte-exact" || bad "4MB read via /storage" "checksum mismatch"
else skip "byte-exact read via /storage"; fi

# The comparison that matters: anything differing here is something an app could notice.
for attr in 'ls -Zd %s | cut -d" " -f1' 'stat -c %%U:%%G/%%a %s' 'stat -f -c %%T %s'; do
    A=$(D "$(printf "$attr" "'/storage/emulated/0'")")
    B=$(D "$(printf "$attr" "'/storage/$NAME'")")
    if [ "$A" = "$B" ]; then ok "matches internal storage: $A"
    else bad "differs from internal storage" "emulated='$A' share='$B'"; fi
done

# The directory comparison above is weaker than it looks: the share's root is synthesised by the
# daemon, so it says nothing about the entries inside. A backend that passes the server's own mode
# through shows up only on a real file, and the mount carries default_permissions so those bits are
# what the kernel enforces against an app.
FMODE_INT=$(D "stat -c %a /storage/emulated/0/.sharefs_probe 2>/dev/null" )
D "touch /storage/emulated/0/.sharefs_probe" >/dev/null
FMODE_INT=$(D "stat -c %a /storage/emulated/0/.sharefs_probe")
FMODE_SHR=$(D "stat -c %a '/storage/$NAME/media/medium.bin'")
DMODE_SHR=$(D "stat -c %a '/storage/$NAME/media'")
D "rm -f /storage/emulated/0/.sharefs_probe" >/dev/null
if [ "$HAVE_FIXTURE" != yes ]; then skip "presented file/dir modes"; else
[ "$FMODE_SHR" = "660" ] \
    && ok "file mode on the share is 660, not the server's own (internal: $FMODE_INT)" \
    || bad "file mode on the share" "got '$FMODE_SHR', expected 660 - a server mode is leaking through"
[ "$DMODE_SHR" = "770" ] \
    && ok "directory mode on the share is 770" \
    || bad "directory mode on the share" "got '$DMODE_SHR', expected 770"
fi

# Writability through the app path, since read-only-but-looks-writable is the subtle failure.
STAMP="saf$(date +%s)"
D "touch '/storage/$NAME/writetest/$STAMP' 2>&1" >/dev/null
D "ls '/storage/$NAME/writetest/$STAMP'" | grep -q "$STAMP" \
    && { ok "writable via /storage (as root)"; D "rm -f '/storage/$NAME/writetest/$STAMP'" >/dev/null; } \
    || bad "writable via /storage (as root)" "could not create a file"

# A root write proves the daemon works and says nothing about whether an app can use the share,
# because root bypasses DAC entirely. The obvious way to fix that is `adb unroot`, and it is a trap
# on this device twice over: adbd is configured to come back as root anyway (the check reported
# "could not drop root"), and restarting adbd **drops every adb reverse tunnel**, which is how the
# device reaches the test servers. That turned every later check in this suite and the whole
# enforcing run into "No route to host" failures that looked like a product regression.
#
# So the app-level evidence is gathered two other ways instead, neither of which restarts adbd:
#   - the mode and ownership assertions above, since 0660 root:everybody with the fuse: label is
#     exactly what /storage/emulated/0 presents and what the kernel checks an app against; and
#   - the SAF document read below, which is served by the GammaShares provider in its own app
#     process rather than by this root shell.
# What is still not covered is a write by a real 10xxx-uid app; that needs a test app, not a shell.
printf "  ....  app-level access is covered by the mode assertions and the SAF read below,\n"
printf "  ....  not by adb unroot: it cannot drop root here and it kills the reverse tunnels\n"

echo "-- 3. the Storage Access Framework view --"
PKG=$(D "pm list packages | grep -c gammaos.shares")
[ "${PKG:-0}" != "0" ] && ok "GammaShares is installed" || bad "GammaShares is installed" "package not found"

ROOTS=$(D "content query --uri content://$AUTH/root 2>&1")
if echo "$ROOTS" | grep -q "$NAME"; then
    ok "SAF publishes a root for '$NAME'"
    echo "$ROOTS" | head -3 | sed 's/^/      /'
else
    bad "SAF publishes a root for '$NAME'" "$(echo "$ROOTS" | head -1)"
fi

# A provider that lists a root but cannot enumerate it is only half working. Assert on the shape of
# what comes back rather than on particular names: the share's contents are whatever is on the
# server, so requiring a specific directory makes the test fail when the data changes rather than
# when the provider breaks.
# Retry a provider error.
#
# This device has 968MB of RAM, and under the memory pressure of a full test run lowmemorykiller
# evicts the provider process mid-query:
#   lowmemorykiller: Kill 'com.gammaos.shares' (24624), uid 1000, oom_score_adj 905
#   ActivityManager: Process com.gammaos.shares has died: cch+5 CEM
# `content query` reports that as "Error while accessing provider" and gives up, which looked like
# an enforcing-mode failure the first time it happened. A real app never sees this, because the
# framework restarts a provider on the next access; only this CLI treats it as fatal. So retry, and
# say so when it happens rather than hiding it.
DOCS=""
for attempt in 1 2 3; do
    DOCS=$(D "content query --uri content://$AUTH/document/%2Fmnt%2Fshares%2F${NAME// /%20}/children 2>&1")
    echo "$DOCS" | grep -q "Error while accessing provider" || break
    printf "  ....  provider was evicted (low memory), retrying (%d)\n" "$attempt"
    sleep 3
done
NROWS=$(echo "$DOCS" | grep -c "^Row:")
if [ "${NROWS:-0}" -gt 0 ] && echo "$DOCS" | grep -q "document_id=/mnt/shares/$NAME/"; then
    ok "SAF enumerates the share ($NROWS entries, ids rooted correctly)"
    echo "$DOCS" | head -2 | sed 's/^/      /'
else
    bad "SAF enumerates the share" "$(echo "$DOCS" | head -1)"
fi

# Reading a document through SAF is the part an app actually depends on.
if [ "$HAVE_FIXTURE" = yes ]; then
  SAFREAD=$(D "content read --uri content://$AUTH/document/%2Fmnt%2Fshares%2F${NAME// /%20}%2Fdocs%2Ftiny.txt 2>&1 | wc -c")
  [ "${SAFREAD:-0}" = "1024" ] \
      && ok "SAF opens a document and reads all 1024 bytes" \
      || bad "SAF opens a document" "read $SAFREAD bytes, expected 1024"
else
  # No fixture: prove the same path with whatever the share actually holds - open the first file
  # the provider enumerated and check the bytes come back.
  # Pick a FILE, not a directory: the first enumerated row is often a folder, and reading a folder
  # as a document correctly returns 0 bytes - which looked like a provider failure the first time.
  FIRST=$(echo "$DOCS" | grep -v 'mime_type=vnd.android.document/directory' \
          | grep -m1 -oE 'document_id=[^,]+' | sed 's/document_id=//')
  if [ -n "$FIRST" ]; then
    ENC=$(python3 -c "import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1],safe=''))" "$FIRST")
    N=$(D "content read --uri content://$AUTH/document/$ENC 2>/dev/null | head -c 4096 | wc -c")
    [ "${N:-0}" -gt 0 ] && ok "SAF opens a document from this share ($N bytes of '$(basename "$FIRST")')" \
                        || bad "SAF opens a document" "read 0 bytes from $FIRST"
  else skip "SAF document read"; fi
fi

echo
echo "  $PASS passed, $FAIL failed"
exit $FAIL

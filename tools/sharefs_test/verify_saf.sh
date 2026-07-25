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

echo "=============================================================="
echo " share reachability: '$NAME'"
echo "=============================================================="

echo "-- 1. the menu's view (/mnt/shares) --"
D "mountpoint -q '/mnt/shares/$NAME' && echo y" | grep -q y \
    && ok "mounted at /mnt/shares/$NAME" || bad "mounted at /mnt/shares/$NAME" "not a mount point"
[ "$(D "sha256sum '/mnt/shares/$NAME/media/medium.bin' 2>/dev/null | cut -d' ' -f1")" = "$MEDIUM" ] \
    && ok "4MB read via /mnt/shares is byte-exact" || bad "4MB read via /mnt/shares" "checksum mismatch"

echo "-- 2. the app path view (/storage), which must match internal storage --"
D "ls -d '/storage/$NAME'" | grep -q "$NAME" \
    && ok "visible at /storage/$NAME" || bad "visible at /storage/$NAME" "not present"
[ "$(D "sha256sum '/storage/$NAME/media/medium.bin' 2>/dev/null | cut -d' ' -f1")" = "$MEDIUM" ] \
    && ok "4MB read via /storage is byte-exact" || bad "4MB read via /storage" "checksum mismatch"

# The comparison that matters: anything differing here is something an app could notice.
for attr in 'ls -Zd %s | cut -d" " -f1' 'stat -c %%U:%%G/%%a %s' 'stat -f -c %%T %s'; do
    A=$(D "$(printf "$attr" "'/storage/emulated/0'")")
    B=$(D "$(printf "$attr" "'/storage/$NAME'")")
    if [ "$A" = "$B" ]; then ok "matches internal storage: $A"
    else bad "differs from internal storage" "emulated='$A' share='$B'"; fi
done

# Writability through the app path, since read-only-but-looks-writable is the subtle failure.
STAMP="saf$(date +%s)"
D "touch '/storage/$NAME/writetest/$STAMP' 2>&1" >/dev/null
D "ls '/storage/$NAME/writetest/$STAMP'" | grep -q "$STAMP" \
    && { ok "writable via /storage"; D "rm -f '/storage/$NAME/writetest/$STAMP'" >/dev/null; } \
    || bad "writable via /storage" "could not create a file"

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
DOCS=$(D "content query --uri content://$AUTH/document/%2Fmnt%2Fshares%2F${NAME// /%20}/children 2>&1")
NROWS=$(echo "$DOCS" | grep -c "^Row:")
if [ "${NROWS:-0}" -gt 0 ] && echo "$DOCS" | grep -q "document_id=/mnt/shares/$NAME/"; then
    ok "SAF enumerates the share ($NROWS entries, ids rooted correctly)"
    echo "$DOCS" | head -2 | sed 's/^/      /'
else
    bad "SAF enumerates the share" "$(echo "$DOCS" | head -1)"
fi

# Reading a document through SAF is the part an app actually depends on.
SAFREAD=$(D "content read --uri content://$AUTH/document/%2Fmnt%2Fshares%2F${NAME// /%20}%2Fdocs%2Ftiny.txt 2>&1 | wc -c")
[ "${SAFREAD:-0}" = "1024" ] \
    && ok "SAF opens a document and reads all 1024 bytes" \
    || bad "SAF opens a document" "read $SAFREAD bytes, expected 1024"

echo
echo "  $PASS passed, $FAIL failed"
exit $FAIL

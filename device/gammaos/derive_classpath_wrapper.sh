#!/system/bin/sh
# Fast derive_classpath wrapper for nano mode.
# If the cached classpath exports file exists and we're in minimal_boot,
# exit immediately - the cached file from the previous boot is valid.
# Otherwise fall through to the real derive_classpath binary.
OUTFILE="$1"
if [ -z "$OUTFILE" ]; then
    OUTFILE="/data/system/environ/classpath"
fi
if [ "$(getprop sys.gammaos.minimal_boot)" = "1" ] && [ -s "$OUTFILE" ]; then
    log -t derive_classpath "Nano mode: reusing cached $OUTFILE"
    exit 0
fi
exec /data/local/tmp/derive_classpath_orig "$@"

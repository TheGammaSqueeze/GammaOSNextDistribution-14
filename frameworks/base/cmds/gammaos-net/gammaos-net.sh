#!/system/bin/sh
# gammaos-net - Wi-Fi and Bluetooth control for the GammaOS Nano menu.
# Uses the same framework Manager APIs that packages/apps/Settings uses
# (WifiManager.connect(int,...), BluetoothAdapter.startDiscovery(),
# BluetoothDevice.createBond()) rather than falling back to `cmd wifi`
# / `cmd bluetooth_manager` shell commands that don't expose the
# saved-network-by-id or discovery flows we need.
#
# When invoked from gammaos-nano (which runs in `class core animation`
# and therefore in init's bootstrap mount namespace), /apex only has
# the bootstrap apex set and /apex/com.android.art is missing, so
# app_process can't link libnativeloader.so. nsenter into init's
# namespace (pid 1, which lives in the default namespace post-apex)
# before exec'ing app_process so the full apex set is visible.
# nsenter needs CAP_SYS_ADMIN; nano's rc grants it.
#
# Nano is started by init with `class core animation`, which launches
# before derive_classpath has populated BOOTCLASSPATH /
# DEX2OATBOOTCLASSPATH in init's exported env. Nano's own environ ends
# up with just PATH, and popen() inherits that -- so by the time we
# exec app_process, ART's Runtime::Create aborts during
# JNI_CreateJavaVM with "Check failed: !location.empty() BOOTCLASSPATH
# and DEX2OATBOOTCLASSPATH must not be empty". We run derive_classpath
# (the canonical tool in the com.android.sdkext apex) to recompute
# these and re-export them. Because derive_classpath itself lives in
# an apex that is NOT visible in nano's bootstrap namespace, this has
# to happen AFTER the nsenter switch.
export CLASSPATH=/system/framework/gammaos-net.jar
if [ ! -e /apex/com.android.art/lib64/libnativeloader.so ]; then
    CPFILE=/data/local/tmp/gammaos-net-cp.$$
    exec /system/bin/nsenter -t 1 -m -- /system/bin/sh -c '
CPFILE="$1"; shift
/apex/com.android.sdkext/bin/derive_classpath "$CPFILE"
while read action key value; do
    [ "$action" = "export" ] && [ -n "$key" ] && export "$key=$value"
done < "$CPFILE"
rm -f "$CPFILE"
exec /system/bin/app_process /system/bin com.gammaos.net.GammaosNet "$@"
' sh "$CPFILE" "$@"
fi
exec app_process /system/bin com.gammaos.net.GammaosNet "$@"

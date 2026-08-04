/*
 * GammaOS Nano Runtime-Permission Grant Bridge
 *
 * On the GammaOS Nano handhelds the user drives everything with a d-pad / gamepad and
 * there is usually no touchscreen, so the standard Android runtime-permission prompt is
 * both an annoyance and, on this non-leanback ATV GSI, historically hard to dismiss with
 * a controller. Frontends such as RetroArch also need all-files storage access to reach
 * the ROM directories, which is gated behind the MANAGE_EXTERNAL_STORAGE special access.
 *
 * This bridge runs inside the warm system_server JVM (uid 1000, domain system_server),
 * which already holds GRANT_RUNTIME_PERMISSIONS and MANAGE_APP_OPS_MODES, so it can call
 * PackageManager.grantRuntimePermission / updatePermissionFlags and AppOpsManager
 * .setUidMode directly with no reflection and no new SELinux policy.
 *
 * It pre-grants the storage and microphone runtime permissions (only the ones each app
 * actually declares in its manifest) to non-system user apps so no prompt ever appears,
 * and it flips the MANAGE_EXTERNAL_STORAGE app-op to ALLOWED for an emulator/frontend
 * allowlist (RetroArch by default, extensible via a chunked system property). Grants are
 * marked GRANTED_BY_DEFAULT (not SYSTEM_FIXED) so the user can still revoke them from
 * Settings if they want to.
 *
 * The sweep runs entirely on a daemon HandlerThread, never on the boot thread. It is safe
 * in full Android (self-gates on sys.gammaos.minimal_boot, i.e. nano builds) and is
 * additionally kill-switchable via persist.gammaos.perm.autogrant=0.
 */

package com.android.server.gammaos;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.util.Slog;

import java.util.HashSet;
import java.util.Set;

public final class NanoPermGrantBridge {
    private static final String TAG = "NanoPermGrantBridge";

    // Kill-switch: persist.gammaos.perm.autogrant=0 disables the whole bridge.
    private static final String AUTOGRANT_PROP = "persist.gammaos.perm.autogrant";
    // Chunked allowlist (base + _0.._4) of packages that may receive all-files access.
    private static final String MES_ALLOWLIST_PROP = "persist.gammaos.perm.mestorage";
    // The exact package nano itself hard-codes for RetroArch; always in the allowlist.
    private static final String RETROARCH_PKG = "com.retroarch.aarch64";

    // Runtime permissions we auto-grant when an app declares them. Mirrors the STORAGE and
    // MICROPHONE groups in DefaultPermissionGrantPolicy. READ/WRITE_EXTERNAL_STORAGE are
    // only meaningful (and only grantable) for apps targeting <=API 28/32; the declared-
    // permission intersect below means we only attempt them where the manifest asks, so
    // there are no wasted or invalid grants on modern targets.
    private static final String[] STORAGE_PERMISSIONS = {
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.ACCESS_MEDIA_LOCATION,
        Manifest.permission.READ_MEDIA_AUDIO,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    };
    private static final String[] MICROPHONE_PERMISSIONS = {
        Manifest.permission.RECORD_AUDIO,
    };

    // Package prefixes nano never treats as user apps; skip them to avoid touching
    // system / platform components. Mirrors the exact list used by the nano app-cache
    // package-change receiver in SystemServer.
    private static final String[] SKIP_PREFIXES = {
        "com.android.",
        "org.lineageos.",
        "com.gammaos.",
        "com.topjohnwu.",
    };

    // Keep a static reference so the owning instance is never GC'd.
    private static NanoPermGrantBridge sInstance;
    private static boolean sStarted;

    private final Context mContext;
    private Handler mHandler;

    private NanoPermGrantBridge(Context context) {
        mContext = context;
    }

    /** Called from SystemServer's minimal-boot bring-up thread (after boot_completed). */
    public static synchronized void start(Context context) {
        if (sStarted) return;
        if (!SystemProperties.getBoolean("sys.gammaos.minimal_boot", false)) {
            return; // only the nano home benefits from silent grants
        }
        if (!SystemProperties.getBoolean(AUTOGRANT_PROP, true)) {
            return; // explicit kill-switch
        }
        sStarted = true;
        sInstance = new NanoPermGrantBridge(context);
        sInstance.init();
    }

    private void init() {
        HandlerThread ht = new HandlerThread("NanoPermGrant");
        ht.setDaemon(true);
        ht.start();
        mHandler = new Handler(ht.getLooper());
        // Full initial sweep, off the boot thread.
        mHandler.post(this::sweepAll);
        Slog.i(TAG, "GammaOS Nano: permission grant bridge started");
    }

    /**
     * Sweep a single package (called from the package-change receiver so a freshly
     * sideloaded emulator/frontend is granted the moment it installs). Runs on the
     * bridge's own HandlerThread if the bridge is active, otherwise inline; either way
     * off the caller's critical path since the receiver already dispatches on a
     * HandlerThread. Safe no-op if the bridge never started (gated builds).
     */
    public static void sweepPackage(Context context, String pkg) {
        if (context == null || pkg == null) return;
        if (!SystemProperties.getBoolean("sys.gammaos.minimal_boot", false)) return;
        if (!SystemProperties.getBoolean(AUTOGRANT_PROP, true)) return;
        final NanoPermGrantBridge inst = sInstance;
        final Runnable r = () -> {
            try {
                PackageManager pm = context.getPackageManager();
                PackageInfo pi = pm.getPackageInfo(pkg,
                        PackageManager.GET_PERMISSIONS
                                | PackageManager.MATCH_UNINSTALLED_PACKAGES);
                grantForPackage(context, pm, pi, android.os.Process.myUserHandle());
            } catch (Throwable t) {
                Slog.w(TAG, "sweepPackage failed for " + pkg + ": " + t);
            }
        };
        if (inst != null && inst.mHandler != null) {
            inst.mHandler.post(r);
        } else {
            r.run();
        }
    }

    private void sweepAll() {
        try {
            PackageManager pm = mContext.getPackageManager();
            UserHandle user = android.os.Process.myUserHandle();
            for (PackageInfo pi : pm.getInstalledPackages(
                    PackageManager.GET_PERMISSIONS
                            | PackageManager.MATCH_UNINSTALLED_PACKAGES)) {
                // One bad app must not abort the whole sweep.
                try {
                    grantForPackage(mContext, pm, pi, user);
                } catch (Throwable t) {
                    Slog.w(TAG, "grantForPackage failed for "
                            + (pi != null ? pi.packageName : "?") + ": " + t);
                }
            }
            Slog.i(TAG, "GammaOS Nano: permission sweep complete");
        } catch (Throwable t) {
            Slog.w(TAG, "sweepAll failed: " + t);
        }
    }

    private static void grantForPackage(Context ctx, PackageManager pm, PackageInfo pi,
            UserHandle user) {
        if (pi == null || pi.packageName == null || pi.applicationInfo == null) return;
        final String pkg = pi.packageName;
        final ApplicationInfo ai = pi.applicationInfo;

        // Never touch system / updated-system packages.
        if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                || (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {
            return;
        }
        for (String prefix : SKIP_PREFIXES) {
            if (pkg.startsWith(prefix)) return;
        }

        // Only grant a permission the app actually declares in its manifest. Forcing a
        // perm the app never requested would be a policy violation and can destabilize PM.
        final Set<String> declared = new HashSet<>();
        if (pi.requestedPermissions != null) {
            for (String p : pi.requestedPermissions) {
                if (p != null) declared.add(p);
            }
        }
        if (declared.isEmpty()) return;

        // GRANTED_BY_DEFAULT (not SYSTEM_FIXED): silent by default but the user can still
        // revoke it in Settings if they choose to.
        final int flagMask = PackageManager.FLAG_PERMISSION_GRANTED_BY_DEFAULT;

        grantSet(pm, pkg, declared, STORAGE_PERMISSIONS, flagMask, user);
        grantSet(pm, pkg, declared, MICROPHONE_PERMISSIONS, flagMask, user);

        // MANAGE_EXTERNAL_STORAGE (all-files access) is an app-op-backed special access,
        // NOT a normal runtime perm, so grantRuntimePermission does not work for it. The
        // correct pre-grant is AppOpsManager.setUidMode(OPSTR_MANAGE_EXTERNAL_STORAGE,
        // uid, MODE_ALLOWED). Restrict it to the emulator/frontend allowlist since it is
        // the strongest storage grant on the platform.
        if (declared.contains(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
                && readMesAllowlist().contains(pkg)) {
            try {
                AppOpsManager ao = ctx.getSystemService(AppOpsManager.class);
                if (ao != null) {
                    // setUidMode (not setMode) so it survives package reinstall for the uid.
                    ao.setUidMode(AppOpsManager.OPSTR_MANAGE_EXTERNAL_STORAGE,
                            ai.uid, AppOpsManager.MODE_ALLOWED);
                }
            } catch (Throwable t) {
                Slog.w(TAG, "MANAGE_EXTERNAL_STORAGE grant failed for " + pkg + ": " + t);
            }
        }
    }

    private static void grantSet(PackageManager pm, String pkg, Set<String> declared,
            String[] perms, int flagMask, UserHandle user) {
        for (String p : perms) {
            if (!declared.contains(p)) continue;
            try {
                if (pm.checkPermission(p, pkg) != PackageManager.PERMISSION_GRANTED) {
                    pm.grantRuntimePermission(pkg, p, user);
                }
                pm.updatePermissionFlags(p, pkg, flagMask, flagMask, user);
            } catch (Throwable ignore) {
                // A perm may be split/removed on this SDK, or the app may target a level
                // where it is not a runtime perm; skip it rather than abort the package.
            }
        }
    }

    /**
     * Built-in RetroArch package unioned with the chunked prop
     * persist.gammaos.perm.mestorage (base + _0.._4), parsed like the QS blacklist reader
     * so a value can exceed PROP_VALUE_MAX. Empty prop => just the RetroArch default.
     */
    private static Set<String> readMesAllowlist() {
        final Set<String> out = new HashSet<>();
        out.add(RETROARCH_PKG);
        StringBuilder sb = new StringBuilder();
        String base = SystemProperties.get(MES_ALLOWLIST_PROP, "").trim();
        if (!base.isEmpty()) {
            sb.append(base);
        }
        for (int i = 0; i < 5; i++) {
            String part = SystemProperties.get(MES_ALLOWLIST_PROP + "_" + i, "").trim();
            if (part.isEmpty()) continue;
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ',') {
                sb.append(',');
            }
            sb.append(part);
        }
        for (String raw : sb.toString().split(",")) {
            if (raw == null) continue;
            String spec = raw.trim();
            if (!spec.isEmpty() && spec.indexOf(' ') == -1) {
                out.add(spec);
            }
        }
        return out;
    }
}

/*
 * Copyright (C) 2026 GammaOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.dualstack;

import android.os.SystemProperties;
import android.util.ArraySet;

/**
 * GammaOS Dual-Stack system property helpers.
 *
 * <p>Dual-Stack per-app allowlisting is configured via:
 * <ul>
 *     <li>{@code persist.gammaos.dualstack.pkgs} (comma-separated)</li>
 *     <li>{@code persist.gammaos.dualstack.pkgs_#} (comma-separated, # starts at 1)</li>
 * </ul>
 *
 * <p>This supports Android system property value length limits by allowing the allowlist to be
 * split across an arbitrary number of properties.
 */
public final class DualStackPropertyUtils {

    /** Base property used for Dual-Stack package allowlisting (comma-separated). */
    public static final String PROP_DUALSTACK_PKGS = "persist.gammaos.dualstack.pkgs";

    /**
     * Base property used for the nano "Run on primary screen" per-app allowlist (comma-separated,
     * with {@code _#} continuations). Packages here are launched on the primary/bottom display
     * (DEFAULT_DISPLAY) even when {@code persist.gammaos.nano.primary_display} routes normal
     * launches to a different panel - so a dual-SCREEN app (one that opens a second activity on the
     * other physical display, e.g. rip.moth.cocoonshell) gets its main activity on the bottom and
     * spans both screens. This is distinct from Dual-STACK (tall single canvas): a dual-screen app
     * must keep seeing the secondary display, which Dual-Stack deliberately hides.
     */
    public static final String PROP_NANO_PRIMARY_PKGS = "persist.gammaos.nano.primary_pkgs";

    /**
     * Base property used for the nano "Keep Running in Background" per-app allowlist (comma-separated,
     * with {@code _#} continuations). Packages here are NOT force-stopped when the user exits them
     * back to the nano menu (back-hold in PhoneWindowManager, or the nano quick-menu Close App) - the
     * app is left alive so re-launching it resumes warm. Written from nano's per-app option menu.
     * Explicit "Kill All / Kill Background" actions still stop the package.
     */
    public static final String PROP_NANO_BACKGROUND_PKGS = "persist.gammaos.nano.background_pkgs";

    private DualStackPropertyUtils() {}

    /**
     * Returns a sanitized set of whitelisted packages built from {@link #PROP_DUALSTACK_PKGS}
     * and any sequential {@code persist.gammaos.dualstack.pkgs_#} properties.
     */
    public static ArraySet<String> getWhitelistedPackages() {
        final ArraySet<String> out = new ArraySet<>();
        addPackagesFromRaw(out, SystemProperties.get(PROP_DUALSTACK_PKGS, ""));

        // Support arbitrarily many continuation properties:
        // persist.gammaos.dualstack.pkgs_1, persist.gammaos.dualstack.pkgs_2, ...
        for (int i = 1; ; i++) {
            final String raw = SystemProperties.get(PROP_DUALSTACK_PKGS + "_" + i, "");
            if (raw == null || raw.isEmpty()) {
                break;
            }
            addPackagesFromRaw(out, raw);
        }
        return out;
    }

    /**
     * Returns true if {@code packageName} is present in any of the Dual-Stack allowlist properties.
     */
    public static boolean isPackageWhitelisted(String packageName) {
        return isPackageInList(PROP_DUALSTACK_PKGS, packageName);
    }

    /**
     * Returns true if {@code packageName} is in the nano "Run on primary screen" allowlist
     * ({@link #PROP_NANO_PRIMARY_PKGS} and its {@code _#} continuations).
     */
    public static boolean isRunOnPrimaryScreen(String packageName) {
        return isPackageInList(PROP_NANO_PRIMARY_PKGS, packageName);
    }

    /**
     * Returns true if {@code packageName} is in the nano "Keep Running in Background" allowlist
     * ({@link #PROP_NANO_BACKGROUND_PKGS} and its {@code _#} continuations), meaning nano should not
     * force-stop it when the user exits it back to the menu.
     */
    public static boolean isKeepAliveInBackground(String packageName) {
        return isPackageInList(PROP_NANO_BACKGROUND_PKGS, packageName);
    }

    /**
     * Returns true if {@code packageName} is present in {@code baseProp} or any of its sequential
     * {@code baseProp_#} continuation properties (comma-separated lists, sanitized).
     */
    public static boolean isPackageInList(String baseProp, String packageName) {
        if (packageName == null || packageName.isEmpty()) return false;

        if (containsPackageInRaw(packageName, SystemProperties.get(baseProp, ""))) {
            return true;
        }
        for (int i = 1; ; i++) {
            final String raw = SystemProperties.get(baseProp + "_" + i, "");
            if (raw == null || raw.isEmpty()) {
                break;
            }
            if (containsPackageInRaw(packageName, raw)) {
                return true;
            }
        }
        return false;
    }

    private static void addPackagesFromRaw(ArraySet<String> out, String raw) {
        if (raw == null || raw.isEmpty()) return;

        final int n = raw.length();
        int start = 0;
        for (int i = 0; i <= n; i++) {
            if (i == n || raw.charAt(i) == ',') {
                final String token = raw.substring(start, i).trim();
                if (!token.isEmpty() && isValidAndroidPackageName(token)) {
                    out.add(token);
                }
                start = i + 1;
            }
        }
    }

    private static boolean containsPackageInRaw(String packageName, String raw) {
        if (raw == null || raw.isEmpty()) return false;

        final int n = raw.length();
        int start = 0;
        for (int i = 0; i <= n; i++) {
            if (i == n || raw.charAt(i) == ',') {
                final String token = raw.substring(start, i).trim();
                if (!token.isEmpty() && isValidAndroidPackageName(token)
                        && token.equals(packageName)) {
                    return true;
                }
                start = i + 1;
            }
        }
        return false;
    }

    /**
     * Basic validator for an Android application package name.
     *
     * <p>Matches the common constraints:
     * <ul>
     *     <li>Contains at least one dot.</li>
     *     <li>Each segment starts with a letter.</li>
     *     <li>Segments may contain letters, digits, and underscores.</li>
     * </ul>
     */
    public static boolean isValidAndroidPackageName(String pkg) {
        if (pkg == null) return false;

        final int n = pkg.length();
        if (n < 3) return false; // smallest plausible: "a.b"

        boolean hasDot = false;
        boolean segmentStart = true;

        for (int i = 0; i < n; i++) {
            final char c = pkg.charAt(i);
            if (c == '.') {
                // Disallow empty segments and trailing dots.
                if (segmentStart) return false;
                hasDot = true;
                segmentStart = true;
                continue;
            }

            final boolean isLetter =
                    (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
            final boolean isDigit = (c >= '0' && c <= '9');
            final boolean isUnderscore = (c == '_');

            if (!(isLetter || isDigit || isUnderscore)) {
                return false;
            }

            if (segmentStart) {
                // First character of each segment must be a letter.
                if (!isLetter) return false;
                segmentStart = false;
            }
        }

        if (segmentStart) return false; // ends with '.'
        return hasDot;
    }
}
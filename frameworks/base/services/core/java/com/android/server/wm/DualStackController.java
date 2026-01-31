/*
 * GammaOS Dual-Stack per-app display mirroring controller.
 *
 * This controller:
 *  - Blocks Presentation/secondary display visibility to whitelisted packages (handled in DMS).
 *  - When enabled and a whitelisted package is top-resumed on the default display, mirrors its
 *    surface to the secondary internal display and crops the mirror to the top or bottom half.
 *  - Applies a mask on the default display to hide the opposite half, giving the effect of a
 *    single tall logical canvas split perfectly across two physical displays.
 *  - Reacts to system property changes at runtime.
 */
package com.android.server.wm;

import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.Display.TYPE_INTERNAL;
 
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.IApplicationThread;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import android.graphics.Rect;
import android.app.TaskStackListener;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.ArraySet;
import android.util.Slog;
import android.util.Log;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.DisplayInfo;
import android.view.SurfaceControl;
import android.view.SurfaceControl.Transaction;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Arrays;

final class DualStackController {
    private static final String TAG = "DualStackController";

    private static final String PROP_ENABLED = "persist.gammaos.dualstack.enabled";
    private static final String PROP_SWAP = "persist.gammaos.dualstack.swap";
    private static final String PROP_PKGS = "persist.gammaos.dualstack.pkgs";
    private static final String PROP_KILL_PKGS = "persist.gammaos.dualstack.killpackages.enabled";
    private static final String PROP_LAUNCH_GUARD_ENABLED = "persist.gammaos.launch.guard.enabled";


    // Runtime (non-persistent) signal used by DisplayManagerService to hide the secondary
    // internal display from apps while dual-stack is actively mirroring.
    private static final String PROP_RUNTIME_ACTIVE = "sys.gammaos.dualstack.active";

    // Packages that must never be force-stopped by DualStack kill logic.
    // Explicitly includes launchers/providers you listed as "never kill".
    private static final String[] NEVER_KILL_PACKAGES = new String[] {
            "com.android.providers.media",
            "com.android.externalstorage",
            "com.android.providers.downloads",
            "com.android.mtp",
            "com.android.launcher3",
            "android.ext.services",
            "org.lineageos.audiofx",
            "com.android.providers.media.module",
            "com.android.devicelockcontroller",
    };

    private static final int DUALSTACK_TALL_WIDTH = 640;
    private static final int DUALSTACK_TALL_HEIGHT = 960;

    private final WindowManagerService mWm;

    private boolean mEnabled;
    private boolean mKillPackagesEnabled;
    private int mLastKillTaskId = -1;
    private int mLastElevateTaskId = -1;
    private int mElevateSeq = 0;

    private static final int DUALSTACK_ELEVATE_ATTEMPTS = 3;
    private static final int DUALSTACK_ELEVATE_INTERVAL_MS = 5000;

    // Avoid clearing the forced tall size while Home/Recents transitions are in-flight.
    // We tear down mirroring immediately, but defer the display-size reset until the new
    // foreground is stable to prevent mid-transition resizes that break overview.
    private static final int DUALSTACK_CLEAR_FORCED_SIZE_INITIAL_DELAY_MS = 250;
    private static final int DUALSTACK_CLEAR_FORCED_SIZE_RETRY_INTERVAL_MS = 250;
    private static final int DUALSTACK_CLEAR_FORCED_SIZE_MAX_WAIT_MS = 5000;

    private int mDeferredClearSeq = 0;
    private boolean mDeferredClearScheduled;
    private long mDeferredClearStartUptimeMs;
 
    // Reflection-based transition detection (avoid compile-time dependency on internal types).
    // Goal: never clear forced display size while Shell transitions are active, otherwise
    // Home/Recents transitions can span 640x960 -> 640x480 and overview breaks.
    private static boolean sTransitionReflectionFetched;
    private static Method sAtmsGetTransitionControllerMethod;
    private static Field sAtmsTransitionControllerField;
    private static Method sTcInTransitionMethod;
    private static Method sTcIsCollectingMethod;
    private static Method sTcIsPlayingMethod;

    private static boolean isShellTransitionActive(Object atms) {
        if (atms == null) return false;
        try {
            if (!sTransitionReflectionFetched) {
                sTransitionReflectionFetched = true;
                try {
                    sAtmsGetTransitionControllerMethod =
                            atms.getClass().getDeclaredMethod("getTransitionController");
                    sAtmsGetTransitionControllerMethod.setAccessible(true);
                } catch (Throwable ignored) {
                    sAtmsGetTransitionControllerMethod = null;
                }
                try {
                    sAtmsTransitionControllerField =
                            atms.getClass().getDeclaredField("mTransitionController");
                    sAtmsTransitionControllerField.setAccessible(true);
                } catch (Throwable ignored) {
                    sAtmsTransitionControllerField = null;
                }
            }

            Object tc = null;
            if (sAtmsGetTransitionControllerMethod != null) {
                try {
                    tc = sAtmsGetTransitionControllerMethod.invoke(atms);
                } catch (Throwable ignored) {
                }
            }
            if (tc == null && sAtmsTransitionControllerField != null) {
                try {
                    tc = sAtmsTransitionControllerField.get(atms);
                } catch (Throwable ignored) {
                }
            }
            if (tc == null) return false;

            // Lazily resolve controller methods from the runtime class.
            final Class<?> c = tc.getClass();
            if (sTcInTransitionMethod == null
                    && sTcIsCollectingMethod == null
                    && sTcIsPlayingMethod == null) {
                try {
                    sTcInTransitionMethod = c.getDeclaredMethod("inTransition");
                    sTcInTransitionMethod.setAccessible(true);
                } catch (Throwable ignored) {
                    sTcInTransitionMethod = null;
                }
                try {
                    sTcIsCollectingMethod = c.getDeclaredMethod("isCollecting");
                    sTcIsCollectingMethod.setAccessible(true);
                } catch (Throwable ignored) {
                    sTcIsCollectingMethod = null;
                }
                try {
                    sTcIsPlayingMethod = c.getDeclaredMethod("isPlaying");
                    sTcIsPlayingMethod.setAccessible(true);
                } catch (Throwable ignored) {
                    sTcIsPlayingMethod = null;
                }
            }

            if (sTcInTransitionMethod != null) {
                try {
                    final Object r = sTcInTransitionMethod.invoke(tc);
                    if (r instanceof Boolean && ((Boolean) r)) return true;
                } catch (Throwable ignored) {
                }
            }
            if (sTcIsPlayingMethod != null) {
                try {
                    final Object r = sTcIsPlayingMethod.invoke(tc);
                    if (r instanceof Boolean && ((Boolean) r)) return true;
                } catch (Throwable ignored) {
                }
            }
            if (sTcIsCollectingMethod != null) {
               try {
                    final Object r = sTcIsCollectingMethod.invoke(tc);
                    if (r instanceof Boolean && ((Boolean) r)) return true;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    // Which Task (not just Activity) is currently being dual-stacked on DEFAULT_DISPLAY.
    // DraStic bounces between multiple activities (DraSticActivity, DraSticEmuActivity,
    // GameMenu) but they all live in the same task.
    private Task mActiveTask;
    private boolean mSwapHalves;
    private ArraySet<String> mWhitelist = new ArraySet<>();

    // Active state
    // Single render target: transform the app surface on the primary to show the bottom half.
    // One mirror only on the secondary to show the top half.
    private SurfaceControl mSecondaryMirror; // mirror on secondary overlay (top half)
    private SurfaceControl mSourceSurface;   // last source we mirrored from
    private SurfaceControl mAppXformTarget;  // the app surface we transform on the primary
    private ActivityRecord mActiveActivity;
    private int mSecondaryDisplayId = -1;
    private boolean mForcedTallSizeApplied;
    // Set while we are in the middle of clearing the forced tall size via
    // WindowManagerService.clearForcedDisplaySize(). This prevents re-entrant
    // recursion through DisplayContent.reconfigureDisplayLocked() →
    // DualStackController.maybeApplyDisplayProjectionsLocked() →
    // updateMirroringIfNeeded() → clearForcedTallSizeIfNeeded().
    private boolean mClearingTallSize;
    // Re-try latch used when we enabled dual-stack but the BLAST wasn't up yet
    private boolean mAwaitingFirstValidSurface;

    DualStackController(WindowManagerService wm) {
        mWm = wm;
        reloadProperties();

        // Re-apply dual-stack transforms whenever focus changes (e.g. menu ↔ gameplay).
        wm.mAtmService.getTaskChangeNotificationController()
                .registerTaskStackListener(new TaskStackListener() {
                    @Override
                    public void onTaskFocusChanged(int taskId, boolean focused) {
                        if (!focused) return;
                        synchronized (mWm.mGlobalLock) {
                            WindowManagerService.boostPriorityForLockedSection();
                            try {
                                // Only react to focus changes for the currently active dual-stack
                                // task. Avoid poking dual-stack during Home/Recents transitions,
                                // which can cause forced-size churn and break overview.
                                if (mActiveTask == null || mActiveTask.mTaskId != taskId) {
                                    return;
                                }
                                final DisplayContent dc =
                                        mWm.mRoot.getDisplayContent(DEFAULT_DISPLAY);
                                if (dc != null) {
                                    maybeApplyDisplayProjectionsLocked(dc);
                                }
                            } finally {
                                WindowManagerService.resetPriorityAfterLockedSection();
                            }
                        }
                    }
                });
    }

    private void reloadProperties() {
        mEnabled = SystemProperties.getBoolean(PROP_ENABLED, false);
        mSwapHalves = SystemProperties.getBoolean(PROP_SWAP, false);
        mKillPackagesEnabled = SystemProperties.getBoolean(PROP_KILL_PKGS, false);
        final String raw = SystemProperties.get(PROP_PKGS, "");
        final ArraySet<String> list = new ArraySet<>();
        if (raw != null && !raw.isEmpty()) {
            final String[] parts = raw.split(",");
            for (int i = 0; i < parts.length; i++) {
                final String p = parts[i].trim();
                if (!p.isEmpty()) list.add(p);
            }
        }
        mWhitelist = list;
    }

    private boolean isEligibleTopApp(ActivityRecord r) {
        if (r == null) return false;
        if (!mEnabled) return false;
        return mWhitelist.contains(r.packageName);
    }

    /**
     * Returns true if the given activity should be treated as fully compatible with the
     * current dual-stack logical display configuration. When this is true, WindowManager
     * should avoid putting the activity into size-compat mode so that it can render at the
     * full logical resolution (for example 640x960 on the tall canvas).
     */
    boolean shouldDisableSizeCompatFor(ActivityRecord r) {
        if (!isEligibleTopApp(r)) return false;
        // Only affect activities shown on the default logical display.
        if (r.getDisplayId() != DEFAULT_DISPLAY) return false;
        return true;
    }

    /**
     * Returns true if there is any resumed, visible activity from a whitelisted package
     * on the default display. This lets us keep the tall override as long as the package
     * is in the foreground, regardless of which concrete ActivityRecord is on top.
     */
    private boolean isWhitelistedPackageInForegroundOnDefaultDisplay() {
        if (!mEnabled || mWhitelist.isEmpty()) {
            return false;
        }

        final boolean[] found = new boolean[1];
        mWm.mRoot.forAllActivities((r) -> {
            if (found[0] || r == null) return;
            if (!isEligibleTopApp(r)) return;
            if (r.getDisplayId() != DEFAULT_DISPLAY) return;
            if (!r.isVisibleRequested()) return;
            if (!r.isState(ActivityRecord.State.RESUMED)) return;
            found[0] = true;
        });
        return found[0];
    }

    void updateMirroringIfNeeded(Transaction t) {
        reloadProperties();
        if (!mEnabled) {
            cancelDeferredForcedTallSizeClearLocked();
            setRuntimeDualStackActive(false);
            mLastKillTaskId = -1;
            mLastElevateTaskId = -1;
            clearForcedTallSizeIfNeeded();
            teardown(t);
            return;
        }

        final DisplayContent primary = mWm.mRoot.getDisplayContent(DEFAULT_DISPLAY);
        if (primary == null) {
            cancelDeferredForcedTallSizeClearLocked();
            setRuntimeDualStackActive(false);
            mLastKillTaskId = -1;
            mLastElevateTaskId = -1;
            clearForcedTallSizeIfNeeded();
            teardown(t);
            return;
        }

        final DisplayContent secondary = findSecondaryInternalDisplayLocked();
        if (secondary == null) {
            cancelDeferredForcedTallSizeClearLocked();
            setRuntimeDualStackActive(false);
            mLastKillTaskId = -1;
            mLastElevateTaskId = -1;
            clearForcedTallSizeIfNeeded();
            teardown(t);
            return;
        }

        // Only keep dual-stack active while some ACTUALLY RESUMED activity from a
        // whitelisted package is in the foreground on the default display. We key
        // the "session" on the Task so that internal activity swaps (Emu <-> GameMenu)
        // do not tear down the mirror.
        final boolean pkgInFg = isWhitelistedPackageInForegroundOnDefaultDisplay();
        if (!pkgInFg) {
            mLastKillTaskId = -1;
            mLastElevateTaskId = -1;
            // Tear down mirrors immediately so Home/Recents is not shown split/cropped.
            // Defer clearing the forced tall size until the new foreground is stable to
            // avoid mid-transition resizes (seen in logs as sb=640x960 -> eb=640x480).
            teardown(t);
            scheduleDeferredForcedTallSizeClearLocked();
            return;
        }
  
        // Dual-stack is (or remains) active again, cancel any pending size reset.
        cancelDeferredForcedTallSizeClearLocked();
 
        final ActivityRecord top = mWm.mRoot.getTopResumedActivity();
        if (top == null || top.getDisplayId() != DEFAULT_DISPLAY) {
            // Package is still considered foreground, but we do not yet have a
            // concrete top activity with a surface. Keep any existing dual-stack
            // state alive and wait for the next traversal.
            return;
        }

        final Task topTask = top.getTask();
        if (topTask == null || !mWhitelist.contains(top.packageName)) {
            // This should not normally happen if pkgInFg is true, but be defensive.
            setRuntimeDualStackActive(false);
            mLastKillTaskId = -1;
            clearForcedTallSizeIfNeeded();
            teardown(t);
            return;
        }

        setRuntimeDualStackActive(true);
        applyForcedTallSizeIfNeeded();

        // Treat the dual-stack "session" as Task-scoped, not Activity-scoped.
        // This avoids tearing down mirrors on Emu <-> GameMenu swaps within the same task.
        final boolean enteringNewDualStackTask = mActiveTask != topTask;
        final boolean taskOrDisplayChanged =
                mActiveTask != topTask
                || mSecondaryDisplayId != secondary.getDisplayId();

        if (taskOrDisplayChanged) {
            teardown(t);
            mActiveTask = topTask;
            mSecondaryDisplayId = secondary.getDisplayId();
        }

        if (enteringNewDualStackTask) {
            maybeKillBackgroundAppsForDualStackLocked(topTask, top.packageName);
            // Always elevate DualStack app scheduling (no prop gating).
            // Apply 3 times, 5 seconds apart, to catch newly spawned threads.
            scheduleDualStackElevation(topTask.mTaskId, top.packageName, mWm.mCurrentUserId);
        }
        // Track the currently top-resumed ActivityRecord for logging / debugging.
        mActiveActivity = top;

        // Always ensure our mirrors are bound to the current render surface on each traversal.
        // createSurfacesIfNeeded() will cheaply no-op when the source hasn't changed and
        // both mirrors are already valid.
        createSurfacesIfNeeded(t, primary, secondary, top);

        if (mAppXformTarget == null || !mAppXformTarget.isValid()
                || mSecondaryMirror == null || !mSecondaryMirror.isValid()) {
            // No usable mirrors yet (e.g. BLAST not ready). We'll try again on the next traversal.
            mAwaitingFirstValidSurface = true;
            return;
        }
        if (mAwaitingFirstValidSurface) {
            createSurfacesIfNeeded(t, primary, secondary, top);
            if (mAppXformTarget != null && mAppXformTarget.isValid()) mAwaitingFirstValidSurface = false;
        }

        if (mAppXformTarget == null || !mAppXformTarget.isValid()
                || mSecondaryMirror == null || !mSecondaryMirror.isValid()) {
            return;
        }

        applyTransforms(t, primary, secondary, top);
    }


    private void setRuntimeDualStackActive(boolean active) {
        final String desired = active ? "1" : "0";
        final String current = SystemProperties.get(PROP_RUNTIME_ACTIVE, "0");
        if (desired.equals(current)) return;
        try {
            SystemProperties.set(PROP_RUNTIME_ACTIVE, desired);
        } catch (Throwable e) {
            // Non-fatal: mirroring must continue even if we can't set the runtime prop.
            Slog.w(TAG, "Failed setting " + PROP_RUNTIME_ACTIVE + "=" + desired, e);
        }
    }

    private DisplayContent findSecondaryInternalDisplayLocked() {
        final DisplayContent[] out = new DisplayContent[1];
        mWm.mRoot.forAllDisplays(dc -> {
            if (dc.getDisplayId() == DEFAULT_DISPLAY) return;
            final DisplayInfo di = dc.getDisplayInfo();
            if (di.type == TYPE_INTERNAL) {
                out[0] = dc;
            }
        });
        return out[0];
    }

    /**
     * Force the default display to a tall logical size while dual-stack is active.
     *
     * Instead of talking directly to DisplayManagerInternal, we reuse
     * WindowManagerService's "forced display size" path (same as `adb shell wm size`),
     * so that WM's own DisplayContent / DisplayFrames and app configurations
     * actually see the 640x960 canvas.
     */
    void applyForcedTallSizeIfNeeded() {
        if (mForcedTallSizeApplied) {
            return;
        }

        final DisplayContent primary = mWm.mRoot.getDisplayContent(DEFAULT_DISPLAY);
        if (primary == null) {
            return;
        }

        final DisplayInfo current = primary.getDisplayInfo();
        // If we're already effectively at the desired logical size, just mark it applied.
        if (current.logicalWidth == DUALSTACK_TALL_WIDTH
                && current.logicalHeight == DUALSTACK_TALL_HEIGHT) {
            mForcedTallSizeApplied = true;
            return;
        }

        try {
            // This updates DisplayContent, DisplayFrames, app configuration, and
            // also drives the DisplayManager / SurfaceFlinger side.
            mWm.setForcedDisplaySize(DEFAULT_DISPLAY,
                    DUALSTACK_TALL_WIDTH,
                    DUALSTACK_TALL_HEIGHT);
            mForcedTallSizeApplied = true;
            Slog.d(TAG, "DualStack forced tall size "
                    + DUALSTACK_TALL_WIDTH + "x" + DUALSTACK_TALL_HEIGHT
                    + " on display " + DEFAULT_DISPLAY);
        } catch (Exception e) {
            Slog.w(TAG, "Failed to apply forced tall display size", e);
        }
    }

    private void createSurfacesIfNeeded(Transaction t, DisplayContent primary,
            DisplayContent secondary, ActivityRecord top) {
        // Create two mirrors of the *entire* primary display windowing tree:
        //  - one shown on the primary display (bottom half by default),
        //  - one shown on the secondary internal display (top half by default).
        //
        // By mirroring the windowing layer instead of only the top activity's surface, we
        // bring all system UI that is part of the primary display into scope (status bar,
        // taskbar, notification shade, volume dialogs, overlays, etc.).
        //
        // The original surfaces remain owned by WM and continue to receive input. Visually
        // we place the mirrors above the real window tree and apply different crops on each
        // display to achieve the split.
        SurfaceControl source = null;
        if (primary != null) {
            final SurfaceControl wl = primary.getWindowingLayer();
            if (wl != null && wl.isValid()) {
                source = wl;
            }
        }

        if (source == null) {
            Slog.w(TAG, "createSurfacesIfNeeded: no valid windowing layer surface");
            return;
        }

        final boolean havePrimary = mAppXformTarget != null && mAppXformTarget.isValid();
        final boolean haveSecondary = mSecondaryMirror != null && mSecondaryMirror.isValid();

        // If both mirrors are already alive and we are still mirroring from the same
        // windowing layer surface, keep them and just update crops / matrices in
        // applyTransforms().
        if (havePrimary && haveSecondary && mSourceSurface == source) {
            return;
        }

        // Tear down any half-alive mirrors so we can recreate a clean pair.
        if (mAppXformTarget != null) {
            try {
                t.remove(mAppXformTarget);
            } catch (Exception ignored) {
            }
            mAppXformTarget = null;
        }
        if (mSecondaryMirror != null) {
            try {
                t.remove(mSecondaryMirror);
            } catch (Exception ignored) {
            }
            mSecondaryMirror = null;
        }
 
        // From this point on we are rebinding to a fresh source.
        mSourceSurface = source;

        // Primary mirror.
        mAppXformTarget = SurfaceControl.mirrorSurface(source);
        // Attach above the real windowing tree so the mirror is the only visible content.
        t.reparent(mAppXformTarget, primary.getOverlayLayer());
        t.setLayer(mAppXformTarget, Integer.MAX_VALUE / 2);
        t.show(mAppXformTarget);

        // Secondary mirror.
        mSecondaryMirror = SurfaceControl.mirrorSurface(source);
        t.reparent(mSecondaryMirror, secondary.getOverlayLayer());
        t.setLayer(mSecondaryMirror, Integer.MAX_VALUE / 2);
        t.show(mSecondaryMirror);
    }

    private void applyTransforms(Transaction t, DisplayContent primary,
            DisplayContent secondary, ActivityRecord top) {
        if (primary == null || secondary == null || top == null) {
            return;
        }

        // If we are not in the tall override state or we do not have valid targets yet,
        // reset everything back to identity and bail.
        if (!mForcedTallSizeApplied
                || mAppXformTarget == null
                || !mAppXformTarget.isValid()
                || mSecondaryMirror == null
                || !mSecondaryMirror.isValid()) {

            if (mAppXformTarget != null) {
                t.setWindowCrop(mAppXformTarget, (Rect) null);
                t.setMatrix(mAppXformTarget, 1f, 0f, 0f, 1f);
                t.setPosition(mAppXformTarget, 0f, 0f);
            }
            if (mSecondaryMirror != null) {
                t.setWindowCrop(mSecondaryMirror, (Rect) null);
                t.setMatrix(mSecondaryMirror, 1f, 0f, 0f, 1f);
                t.setPosition(mSecondaryMirror, 0f, 0f);
            }
            return;
        }

        final DisplayInfo primaryInfo = primary.getDisplayInfo();
        final DisplayInfo secondaryInfo = secondary.getDisplayInfo();
        // When dual-stack is active we do not want WM's size-compat / aspect-ratio
        // letterbox overlays ("Letterbox - left/right/bottom") to be visible, since
        // they introduce black borders that then get mirrored and scaled across
        // both physical panels. Instead we rely on the dual-stack mirror itself
        // to provide the visual framing.
        suppressLetterboxForTopActivity(t, top);

        // We are mirroring the full primary windowing tree, so always split the full
        // forced tall canvas (640x960) rather than attempting to infer per-app bounds.
        int appW = DUALSTACK_TALL_WIDTH;
        int appH = DUALSTACK_TALL_HEIGHT;
        int contentLeft = 0;

        if (appW <= 0 || appH <= 0) {
            return;
        }

        final int halfH = appH / 2;

        // Define halves in root / Activity space. For compat apps, contentLeft is the
        // root X of the real 3:4 content region, so cropping from [contentLeft, ...]
        // skips WM's own left/right letterbox bars entirely.
        final Rect topRect = new Rect(contentLeft, 0, contentLeft + appW, halfH);
        final Rect bottomRect = new Rect(contentLeft, halfH, contentLeft + appW, appH);

        // Decide which half goes where.
        final Rect primaryCrop = mSwapHalves ? topRect : bottomRect;   // bottom by default
        final Rect secondaryCrop = mSwapHalves ? bottomRect : topRect; // top by default

        final int primaryDw = primaryInfo.logicalWidth;
        final int primaryDh = primaryInfo.logicalHeight;
        final int secondaryDw = secondaryInfo.logicalWidth;
        final int secondaryDh = secondaryInfo.logicalHeight;

        if (primaryDw <= 0 || primaryDh <= 0 || secondaryDw <= 0 || secondaryDh <= 0) {
            return;
        }

        // Scale so that one half of the app canvas fills the primary's full *logical* height.
        // Because Display 0 uses a 0.5 Y-scale when we force 640x960 → 640x480, we work in
        // the tall logical space here and let the display transform do the final compression.
        final float primarySx = (float) primaryDw / (float) appW;
        final float primarySy = (float) primaryDh / (float) halfH;

        // For the secondary display, scale the complementary half so it fills the entire
        // 640x480 panel as well. For DraStic (640x960) this degenerates to identity; for
        // RSDK (480x640) this stretches 3:4 content to the full panel width/height.
        final float secondarySx = (float) secondaryDw / (float) appW;
        final float secondarySy = (float) secondaryDh / (float) halfH;

        // For compat / letterboxed apps, WM typically centers the app in a narrower box
        // inside the 640-wide logical canvas. Because we crop starting at contentLeft we
        // have already removed the left bar from the source. However, depending on how
        // appBounds are defined, there can still be an extra translation baked into the
        // underlying layer. Compensate that by injecting an opposite X translation so
        // the mirrored content starts at X=0 on both displays.
        float primaryTx = 0f;
        float secondaryTx = 0f;
        if (contentLeft > 0 && appW < DUALSTACK_TALL_WIDTH) {
            primaryTx = -contentLeft * primarySx;
            secondaryTx = -contentLeft * secondarySx;
        }

        // Safety: if the secondary is temporarily half-height due to a just-resized BLAST, clamp.
        final Rect safeSecondaryCrop = new Rect(secondaryCrop);
        if (safeSecondaryCrop.bottom - safeSecondaryCrop.top > halfH) {
            safeSecondaryCrop.bottom = safeSecondaryCrop.top + halfH;
        }

        // PRIMARY:
        // Show the chosen half (bottom by default) scaled to the tall logical height.
        if (mAppXformTarget != null) {
            t.setWindowCrop(mAppXformTarget, primaryCrop);
            t.setMatrix(mAppXformTarget, primarySx, 0f, 0f, primarySy);
            // For the default (bottom on primary), shift it up by half a canvas.
            // When swapped, leave it at y=0 so the top half sits “normally”.
            final float primaryTy = mSwapHalves
                    ? 0f
                    : -halfH * primarySy;
            // Apply horizontal compensation so that the app content starts at X=0 on
            // the primary display even if WM laid it out with an internal margin.
            t.setPosition(mAppXformTarget, primaryTx, primaryTy);

            Slog.d(TAG, "DualStack primary crop=" + primaryCrop
                    + " sx=" + primarySx + " sy=" + primarySy);
        }

        // SECONDARY:
        // Show the complementary half scaled to fill the secondary panel.
        if (mSecondaryMirror != null) {
            t.setWindowCrop(mSecondaryMirror, safeSecondaryCrop);
            t.setMatrix(mSecondaryMirror, secondarySx, 0f, 0f, secondarySy);
            // Apply the same X compensation so the secondary display lines up with
            // the primary and there is no visible left gutter.
            t.setPosition(mSecondaryMirror, secondaryTx, 0f);

            Slog.d(TAG, "DualStack secondary crop=" + secondaryCrop
                    + " app=" + appW + "x" + appH
                    + " disp0=" + primaryDw + "x" + primaryDh
                    + " disp2=" + secondaryDw + "x" + secondaryDh
                    + " sx=" + secondarySx + " sy=" + secondarySy);
        }
    }
 
    /**
     * When dual-stack is active we do not want WM's size-compat / aspect-ratio
     * letterbox overlays ("Letterbox - left/right/bottom") to be visible, since
     * they introduce black borders that then get mirrored and scaled across the
     * two physical displays.
     *
     * This hides all letterbox windows associated with the current top activity's
     * task while dual-stack is active. Input regions remain managed by WM for the
     * real app content; we only suppress the visual black bars.
     */
    private void suppressLetterboxForTopActivity(Transaction t, ActivityRecord top) {
        if (top == null) {
            return;
        }

        top.forAllWindows(ws -> {
            if (ws == null) return;

            // Match the standard letterbox window naming used by WM:
            // "Letterbox - left", "Letterbox - right", "Letterbox - bottom".
            final CharSequence titleCs =
                    (ws.mAttrs != null) ? ws.mAttrs.getTitle() : null;
            if (titleCs == null) return;

            final String title = titleCs.toString();
            if (!title.startsWith("Letterbox - ")) return;

            final SurfaceControl sc = ws.getSurfaceControl();
            if (sc == null || !sc.isValid()) return;
            t.hide(sc);
        }, true /* traverseTopToBottom */);
    }

    /**
     * Entry point from DisplayContent / task-focus hooks to make sure our dual-stack
     * mirrors and crops are up to date after things like config changes or menu ↔ game
     * transitions.
     */
    void maybeApplyDisplayProjectionsLocked(DisplayContent dc) {
        if (dc == null || dc.getDisplayId() != DEFAULT_DISPLAY) {
            return;
        }

        // Run a one-off dual-stack update on a fresh transaction so we re-create / re-crop
        // mirrors even if the normal traversal did not just run.
        final Transaction t = new Transaction();
        updateMirroringIfNeeded(t);
        t.apply();
    }
 
    private void maybeKillBackgroundAppsForDualStackLocked(Task dualStackTask,
            String dualStackPackage) {
        if (!mEnabled || !mKillPackagesEnabled) {
            return;
        }
        if (dualStackTask == null || dualStackPackage == null || dualStackPackage.isEmpty()) {
            return;
        }

        final int taskId = dualStackTask.mTaskId;
        if (taskId == mLastKillTaskId) {
            return;
        }

        // IMPORTANT: We intentionally do NOT keep other "foreground" packages on other displays.
        // When a dualstack app enters the foreground, we want to kill everything else,
        // including whatever is currently focused/resumed on the secondary display.
        final ArraySet<String> keep = new ArraySet<>();
        keep.add(dualStackPackage); // Only keep the dualstack app itself.
        keep.add("android");
        keep.add("com.android.systemui");
        addNeverKillPackages(keep);

        final int userId = mWm.mCurrentUserId;
        final ArraySet<String> keepCopy = new ArraySet<>(keep);
        mLastKillTaskId = taskId;

        mWm.mH.post(() -> killAllAppsExcept(keepCopy, userId));
    }

    private void scheduleDualStackElevation(int taskId, String pkg, int userId) {
        if (pkg == null || pkg.isEmpty()) {
            return;
        }
        if (taskId == mLastElevateTaskId) {
            return;
        }

        mLastElevateTaskId = taskId;
        final int seq = ++mElevateSeq;

        for (int attempt = 0; attempt < DUALSTACK_ELEVATE_ATTEMPTS; attempt++) {
            final int attemptIndex = attempt;
            final long delayMs = (long) attemptIndex * (long) DUALSTACK_ELEVATE_INTERVAL_MS;
            mWm.mH.postDelayed(() -> {
                if (seq != mElevateSeq) {
                    return;
                }
                elevateDualStackPackage(pkg, userId, attemptIndex + 1);
            }, delayMs);
        }
    }

    private void elevateDualStackPackage(String pkg, int userId, int attemptNo) {
        final Context context = mWm.mContext;
        final ActivityManager am =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) {
            return;
        }

        final List<ActivityManager.RunningAppProcessInfo> running = am.getRunningAppProcesses();
        if (running == null || running.isEmpty()) {
            return;
        }

        final ArraySet<Integer> pids = new ArraySet<>();
        for (int i = 0; i < running.size(); i++) {
            final ActivityManager.RunningAppProcessInfo proc = running.get(i);
            if (proc == null || proc.pkgList == null || proc.pid <= 0) {
                continue;
            }
            if (android.os.UserHandle.getUserId(proc.uid) != userId) {
                continue;
            }
            boolean matches = false;
            for (int j = 0; j < proc.pkgList.length; j++) {
                final String p = proc.pkgList[j];
                if (pkg.equals(p)) {
                    matches = true;
                    break;
                }
            }
            if (matches) {
                pids.add(proc.pid);
            }
        }

        if (pids.isEmpty()) {
            return;
        }

        final int nice = -20;
        final int rtPrio = 90;

        for (int i = 0; i < pids.size(); i++) {
            final int pid = pids.valueAt(i);

            // Process main thread tid is typically pid; apply anyway.
            applyThreadNice(pid, nice);
            applyThreadRt(pid, rtPrio);

            // Apply to all threads.
            final File taskDir = new File("/proc/" + pid + "/task");
            final File[] threads = taskDir.listFiles();
            if (threads == null || threads.length == 0) {
                continue;
            }
            for (int t = 0; t < threads.length; t++) {
                final File tf = threads[t];
                if (tf == null) continue;
                final String name = tf.getName();
                if (name == null || name.isEmpty()) continue;
                final int tid;
                try {
                    tid = Integer.parseInt(name);
                } catch (NumberFormatException ignored) {
                    continue;
                }
                applyThreadNice(tid, nice);
                applyThreadRt(tid, rtPrio);
            }
        }

        Slog.i(TAG, "DualStack elevated " + pkg
                + " (attempt " + attemptNo + "/" + DUALSTACK_ELEVATE_ATTEMPTS + ")"
                + " pids=" + pids);
    }

    private static void applyThreadNice(int tid, int nice) {
        try {
            // Process.setThreadPriority maps directly to Linux nice ranges (-20..19).
            Process.setThreadPriority(tid, nice);
        } catch (Throwable ignored) {
        }
    }

    private static void applyThreadRt(int tid, int prio) {
        try {
            // Best-effort. Some kernels/selinux policies will reject RT for non-root.
            Process.setThreadScheduler(tid, Process.SCHED_FIFO, prio);
        } catch (Throwable ignored) {
        }
    }

    private static void addNeverKillPackages(ArraySet<String> out) {
        if (out == null) return;
        for (int i = 0; i < NEVER_KILL_PACKAGES.length; i++) {
            out.add(NEVER_KILL_PACKAGES[i]);
        }
    }

    private static boolean isNeverKillPackage(String pkg) {
        if (pkg == null) return false;
        for (int i = 0; i < NEVER_KILL_PACKAGES.length; i++) {
            if (pkg.equals(NEVER_KILL_PACKAGES[i])) {
                return true;
            }
        }
        return false;
    }
 
    private void cancelDeferredForcedTallSizeClearLocked() {
        if (!mDeferredClearScheduled) return;
        mDeferredClearScheduled = false;
        mDeferredClearStartUptimeMs = 0L;
        // Invalidate any already-posted callbacks.
        mDeferredClearSeq++;
    }

    private void scheduleDeferredForcedTallSizeClearLocked() {
        if (!mForcedTallSizeApplied) return;
        if (mDeferredClearScheduled) return;
        mDeferredClearScheduled = true;
        mDeferredClearStartUptimeMs = SystemClock.uptimeMillis();
        final int seq = ++mDeferredClearSeq;
        mWm.mH.postDelayed(() -> performDeferredForcedTallSizeClear(seq),
                DUALSTACK_CLEAR_FORCED_SIZE_INITIAL_DELAY_MS);
    }

    private void performDeferredForcedTallSizeClear(int seq) {
        synchronized (mWm.mGlobalLock) {
            WindowManagerService.boostPriorityForLockedSection();
            try {
                if (!mDeferredClearScheduled || seq != mDeferredClearSeq) return;

                reloadProperties();
                if (!mEnabled) {
                    mDeferredClearScheduled = false;
                    mDeferredClearStartUptimeMs = 0L;
                    clearForcedTallSizeIfNeeded();
                    return;
                }

                // If a whitelisted app returned to foreground, cancel the pending reset.
                if (isWhitelistedPackageInForegroundOnDefaultDisplay()) {
                    mDeferredClearScheduled = false;
                    mDeferredClearStartUptimeMs = 0L;
                    return;
                }

                final long waited = SystemClock.uptimeMillis() - mDeferredClearStartUptimeMs;

                // Never clear forced size while shell transitions are active, otherwise
                // Home/Recents transitions can span 640x960 -> 640x480 and overview breaks.
                if (isShellTransitionActive(mWm.mAtmService)
                        && waited < DUALSTACK_CLEAR_FORCED_SIZE_MAX_WAIT_MS) {
                    mWm.mH.postDelayed(() -> performDeferredForcedTallSizeClear(seq),
                            DUALSTACK_CLEAR_FORCED_SIZE_RETRY_INTERVAL_MS);
                    return;
                }
                final ActivityRecord top = mWm.mRoot.getTopResumedActivity();
                final String topPkg = (top != null) ? top.packageName : null;
                final boolean stableNonWhitelistedTop =
                        top != null
                                && top.getDisplayId() == DEFAULT_DISPLAY
                                && top.isVisibleRequested()
                                && top.isState(ActivityRecord.State.RESUMED)
                                && (topPkg == null || !mWhitelist.contains(topPkg));

                if (!stableNonWhitelistedTop && waited < DUALSTACK_CLEAR_FORCED_SIZE_MAX_WAIT_MS) {
                    // Still in an unstable transition (top-resumed not settled). Retry shortly.
                    mWm.mH.postDelayed(() -> performDeferredForcedTallSizeClear(seq),
                            DUALSTACK_CLEAR_FORCED_SIZE_RETRY_INTERVAL_MS);
                    return;
                }

                mDeferredClearScheduled = false;
                mDeferredClearStartUptimeMs = 0L;
                clearForcedTallSizeIfNeeded();
            } finally {
                WindowManagerService.resetPriorityAfterLockedSection();
            }
        }
    }

    private void killAllAppsExcept(ArraySet<String> keepPackages, int userId) {
        final Context context = mWm.mContext;
        final ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        final PackageManager pm = context.getPackageManager();

        if (am == null || pm == null) {
            return;
        }

        // Ensure the never-kill list is always enforced even if caller forgets.
        addNeverKillPackages(keepPackages);

        // Keep the current IME to avoid input disruptions while switching apps.
        try {
            final String ime = Settings.Secure.getStringForUser(
                    context.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD, userId);
            if (ime != null && !ime.isEmpty()) {
                final int slash = ime.indexOf('/');
                final String imePkg = (slash > 0) ? ime.substring(0, slash) : ime;
                if (!imePkg.isEmpty()) {
                    keepPackages.add(imePkg);
                }
            }
        } catch (Throwable ignored) {
        }

        final List<ActivityManager.RunningAppProcessInfo> running = am.getRunningAppProcesses();
        if (running == null || running.isEmpty()) {
            return;
        }

        // Collect candidate packages to kill. Prefer pkgList (accurate for shared processes),
        // and fall back to processName parsing to match LegacyGlobalActions kill-all behaviour.
        final ArraySet<String> candidates = new ArraySet<>();
        for (int i = 0; i < running.size(); i++) {
            final ActivityManager.RunningAppProcessInfo proc = running.get(i);
            if (proc == null) {
                continue;
            }

            if (proc.pkgList != null && proc.pkgList.length > 0) {
                for (int j = 0; j < proc.pkgList.length; j++) {
                    final String pkg = proc.pkgList[j];
                    if (pkg != null && !pkg.isEmpty()) {
                        candidates.add(pkg);
                    }
                }
                continue;
            }

            // LegacyGlobalActions uses processName directly; we strip any ":suffix" to
            // recover the base package name where possible.
            final String processName = proc.processName;
            if (processName != null && !processName.isEmpty()) {
                final int colon = processName.indexOf(':');
                final String basePkg = (colon > 0) ? processName.substring(0, colon) : processName;
                if (!basePkg.isEmpty()) {
                    candidates.add(basePkg);
                }
            }
        }

        if (candidates.isEmpty()) {
            return;
        }

        final ArraySet<String> killed = new ArraySet<>();

        // If GammaOS launch-guard is enabled, prefer RetroArch's clean quit path before
        // killing anything else.
        for (int i = 0; i < candidates.size(); i++) {
            final String pkg = candidates.valueAt(i);
            if (pkg == null || pkg.isEmpty()) {
                continue;
            }
            if (isNeverKillPackage(pkg)) {
                continue;
            }
            if (keepPackages.contains(pkg) || killed.contains(pkg)) {
                continue;
            }
            if (!shouldAttemptRetroarchCleanQuit(pkg)) {
                continue;
            }

            try {
                final boolean needsForceStop = requestRetroarchCleanQuit(pkg, userId, am);
                if (!needsForceStop) {
                    killed.add(pkg);
                    Slog.i(TAG, "DualStack RetroArch clean quit succeeded: " + pkg);
                    continue;
                }
                Slog.i(TAG, "DualStack RetroArch clean quit timed out; force-stopping: " + pkg);

                final ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                if (ai == null) {
                    continue;
                }
                // Match LegacyGlobalActions kill-all: do not force-stop system packages.
                if ((ai.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0) {
                    continue;
                }
                // Never kill core system UIDs.
                if (ai.uid < Process.FIRST_APPLICATION_UID) {
                    continue;
                }

                am.forceStopPackage(pkg);
                killed.add(pkg);
                Slog.i(TAG, "DualStack killed package: " + pkg);
            } catch (PackageManager.NameNotFoundException ignored) {
            } catch (Throwable e) {
                Slog.w(TAG, "DualStack failed to clean-quit/kill RetroArch package " + pkg, e);
            }
        }

        // Kill everything else (except keepPackages) aggressively, mirroring LegacyGlobalActions.
        for (int i = 0; i < candidates.size(); i++) {
           final String pkg = candidates.valueAt(i);
            if (pkg == null || pkg.isEmpty()) {
                continue;
            }
            if (isNeverKillPackage(pkg)) {
                continue;
            }
            if (keepPackages.contains(pkg) || killed.contains(pkg)) {
                continue;
            }
            try {
                final ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                if (ai == null) {
                    continue;
                }
                // Match LegacyGlobalActions kill-all: only force-stop non-system packages.
                if ((ai.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0) {
                    continue;
                }
                // Never kill core system UIDs.
                if (ai.uid < Process.FIRST_APPLICATION_UID) {
                    continue;
                }
                am.forceStopPackage(pkg);
                killed.add(pkg);
                Slog.i(TAG, "DualStack killed package: " + pkg);
            } catch (PackageManager.NameNotFoundException ignored) {
            } catch (Throwable e) {
                Slog.w(TAG, "DualStack failed to kill package " + pkg, e);
            }
        }
        Slog.i(TAG, "DualStack kill sweep complete. Kept=" + keepPackages + " killed=" + killed);
    }

 
    private static boolean shouldAttemptRetroarchCleanQuit(String pkg) {
        if (pkg == null) return false;
        if (!pkg.startsWith("com.retroarch")) return false;
        return SystemProperties.getBoolean(PROP_LAUNCH_GUARD_ENABLED, /* def */ false);
    }
 
    /**
     * Best-effort wait until RetroArch's task becomes focused on its display.
     *
     * During dual-stack entry, the launching dual-stack app can remain top-resumed on display 0
     * while RetroArch is brought forward and focused on another display. Top-resumed is therefore
     * not a reliable signal for whether RetroArch will receive injected input.
     */
    private static boolean waitForRetroarchTaskFocused(ActivityManager am, int retroTaskId,
            int userId, int expectedDisplayId, long timeoutMs) {
        final long end = SystemClock.uptimeMillis() + timeoutMs;
        while (SystemClock.uptimeMillis() < end) {
            try {
                final List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(50);
                if (tasks != null) {
                    for (int i = 0, size = tasks.size(); i < size; i++) {
                        final ActivityManager.RunningTaskInfo info = tasks.get(i);
                        if (info == null) continue;
                        if (info.taskId != retroTaskId) continue;
                        if (info.userId != userId) continue;
                        if (expectedDisplayId != -1 && info.displayId != expectedDisplayId) continue;
                        if (info.isFocused) return true;
                        break; // Found the task but it is not focused yet.
                    }
                }
            } catch (Throwable ignored) {
            }
            SystemClock.sleep(50);
        }
        return false;
    }

    /**
     * Attempt to cleanly quit RetroArch (any com.retroarch* package) by:
     *  - finding a running RetroArch task for the given user
     *  - bringing it to the foreground
     *  - injecting an ESC key (down+up)
     *  - waiting up to 10 seconds for the com.retroarch* process to exit
     *
     * Returns true if the caller should still force-stop afterwards, or false if the app
     * appears to have exited cleanly.
     *
     * This is intentionally aligned with ActivityTaskManagerService.requestRetroarchCleanQuit(). :contentReference[oaicite:0]{index=0}
     */
    private boolean requestRetroarchCleanQuit(String targetPkg, int userId, ActivityManager am) {
        int retroTaskId = -1;
        int retroDisplayId = -1;
        int escDisplayId = DEFAULT_DISPLAY;
        long reinjectAtUptime = 0L;
        boolean allowReinject = false;
        boolean reinjected = false;
        try {
            // Use ActivityManager.getRunningTasks() (system_server has privilege). This avoids
            // depending on IActivityTaskManager#getTasks() signatures across branches.
            final List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(Integer.MAX_VALUE);
            if (tasks != null) {
                for (int i = 0, size = tasks.size(); i < size; i++) {
                    final ActivityManager.RunningTaskInfo info = tasks.get(i);
                    if (info == null || info.topActivity == null) continue;
                    if (info.userId != userId) continue;
                    final String pkg = info.topActivity.getPackageName();
                    if (pkg != null && pkg.startsWith("com.retroarch")) {
                        retroTaskId = info.taskId;
                        retroDisplayId = info.displayId;
                        break;
                    }
                }
            }
        } catch (Throwable ignored) {
            // If we fail to enumerate tasks for any reason, fall back to force-stop.
        }

        if (retroTaskId != -1) {
            escDisplayId = (retroDisplayId != -1) ? retroDisplayId : DEFAULT_DISPLAY;
            Bundle opts = null;
            if (retroDisplayId != -1) {
                try {
                    // Keep RetroArch on its current display (0/1/2) while bringing it forward.
                    opts = ActivityOptions.makeBasic()
                            .setLaunchDisplayId(retroDisplayId)
                            .toBundle();
                } catch (Throwable ignored) {
                    opts = null;
                }
            }

            // Bring RetroArch forward first, then only inject ESC once we can confirm focus.
            try {
                // Direct call into ATMS (same process). Use a stable callingPackage.
                mWm.mAtmService.moveTaskToFront(
                        (IApplicationThread) null,
                        "android",
                        retroTaskId,
                        ActivityManager.MOVE_TASK_NO_USER_ACTION,
                        opts);
            } catch (Throwable t) {
                Slog.w(TAG, "DualStack failed to move RetroArch task to front"
                        + " taskId=" + retroTaskId + " displayId=" + retroDisplayId, t);
            }

            final boolean focused = waitForRetroarchTaskFocused(
                    am, retroTaskId, userId, retroDisplayId, 5000 /* ms */);
            if (focused) {
                // Allow a brief settle so the input target is stable.
                SystemClock.sleep(250);
            } else {
                // Still inject, but target RetroArch's display explicitly so we do not land on display 0.
                Slog.w(TAG, "DualStack RetroArch not focused in time; injecting ESC anyway"
                        + " taskId=" + retroTaskId + " displayId=" + retroDisplayId
                        + " escDisplayId=" + escDisplayId);
            }
            Slog.i(TAG, "DualStack injecting RetroArch ESC"
                    + " taskId=" + retroTaskId + " displayId=" + escDisplayId);
            injectGammaEscapeKey(escDisplayId);
            allowReinject = true;
            reinjectAtUptime = SystemClock.uptimeMillis() + 1500;
        }

        // Wait up to 10 seconds for any com.retroarch* process for this user to go away.
        final long waitUntil = SystemClock.uptimeMillis() + 10000;
        try {
            while (SystemClock.uptimeMillis() < waitUntil) {
                final List<ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
                boolean found = false;
                if (procs != null) {
                    for (int i = 0, size = procs.size(); i < size; i++) {
                        final ActivityManager.RunningAppProcessInfo p = procs.get(i);
                        if (p == null || p.processName == null) continue;
                        if (!p.processName.startsWith("com.retroarch")) continue;
                        if (UserHandle.getUserId(p.uid) != userId) continue;
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    // App appears to have exited on its own; no need to force-stop.
                    return false;
                }

                if (allowReinject && !reinjected && reinjectAtUptime > 0L
                        && SystemClock.uptimeMillis() >= reinjectAtUptime) {
                    reinjected = true;
                    Slog.i(TAG, "DualStack re-injecting RetroArch ESC"
                            + " taskId=" + retroTaskId + " displayId=" + escDisplayId);
                    injectGammaEscapeKey(escDisplayId);
                }

                SystemClock.sleep(200);
            }
        } catch (Throwable ignored) {
            // If anything goes wrong while waiting, fall back to force-stop.
        }

        // Still appears to be running; caller should force-stop.
        return true;
    }

    /**
     * Injects a synthetic ESC keypress (down+up) into the input pipeline,
     * mirroring the behaviour used in ActivityTaskManagerService.injectGammaEscapeKey(). :contentReference[oaicite:1]{index=1}
     */
    private static Method sInputEventSetDisplayId;
    private static boolean sInputEventSetDisplayIdFetched;

    private static void maybeSetInputEventDisplayId(InputEvent ev, int displayId) {
        if (ev == null || displayId == -1) return;

        if (!sInputEventSetDisplayIdFetched) {
            sInputEventSetDisplayIdFetched = true;
            try {
                sInputEventSetDisplayId =
                        InputEvent.class.getDeclaredMethod("setDisplayId", int.class);
                sInputEventSetDisplayId.setAccessible(true);
            } catch (Throwable t) {
                sInputEventSetDisplayId = null;
            }
        }

        if (sInputEventSetDisplayId == null) return;
        try {
            sInputEventSetDisplayId.invoke(ev, displayId);
        } catch (Throwable ignored) {
        }
    }

    private static void injectGammaEscapeKey(int displayId) {
        final long now = SystemClock.uptimeMillis();
        final KeyEvent down = new KeyEvent(now, now,
                KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE, 0 /* repeat */,
                0 /* metaState */, KeyCharacterMap.VIRTUAL_KEYBOARD, 0 /* scancode */,
                0 /* flags */, InputDevice.SOURCE_KEYBOARD);
        final KeyEvent up = KeyEvent.changeAction(down, KeyEvent.ACTION_UP);
 
        // Best-effort: target the display RetroArch is on so ESC does not get routed to display 0
        // while the dual-stack app is creating windows and focus is unstable.
        maybeSetInputEventDisplayId(down, displayId);
        maybeSetInputEventDisplayId(up, displayId);

        final InputManager im = InputManager.getInstance();
        if (im != null) {
            final boolean downOk = im.injectInputEvent(
                    down, InputManager.INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH);
            final boolean upOk = im.injectInputEvent(
                    up, InputManager.INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH);
            if (!downOk || !upOk) {
                Slog.w(TAG, "DualStack ESC inject failed"
                        + " displayId=" + displayId
                        + " downOk=" + downOk + " upOk=" + upOk);
            }
        }
    }

    /**
     * Reverts any forced tall size applied in {@link #applyForcedTallSizeIfNeeded()}.
     * This uses the same clearForcedDisplaySize path as `adb shell wm size reset`.
     */
    private void clearForcedTallSizeIfNeeded() {
        // Only clear once, and avoid re-entrancy via clearForcedDisplaySize()
        // triggering reconfigureDisplayLocked() which calls back into us.
        if (!mForcedTallSizeApplied || mClearingTallSize) {
            return;
        }

        mClearingTallSize = true;
        try {
            mWm.clearForcedDisplaySize(DEFAULT_DISPLAY);
            Slog.d(TAG, "DualStack cleared forced tall size on display " + DEFAULT_DISPLAY);
        } catch (Exception e) {
            Slog.w(TAG, "Failed to clear forced tall display size", e);
        } finally {
            // Mark the override as cleared and drop the guard so future
            // enable/disable cycles can clear again safely.
            mForcedTallSizeApplied = false;
            mClearingTallSize = false;
        }
    }

    private void teardown(Transaction t) {
        setRuntimeDualStackActive(false);
        if (mAppXformTarget != null) {
            try {
                t.setWindowCrop(mAppXformTarget, (Rect) null);
                t.setMatrix(mAppXformTarget, 1f, 0f, 0f, 1f);
                t.setPosition(mAppXformTarget, 0f, 0f);
                t.remove(mAppXformTarget);
            } catch (Exception ignored) {
            }
            mAppXformTarget = null;
        }
        if (mSecondaryMirror != null) {
            try {
                t.remove(mSecondaryMirror);
            } catch (Exception ignored) {
            }
            mSecondaryMirror = null;
        }
        mSourceSurface = null;
        mActiveTask = null;
        mActiveActivity = null;
        mSecondaryDisplayId = -1;
        mAwaitingFirstValidSurface = false;
    }

}
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
 
import static java.lang.Math.abs;

import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.Display.TYPE_INTERNAL;
import static android.content.Intent.ACTION_SCREEN_ON;
import static android.content.Intent.ACTION_SCREEN_OFF;
 
import android.app.ActivityManager;
import android.app.IApplicationThread;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Rect;
import android.app.TaskStackListener;
import android.hardware.input.InputManager;
import android.os.Process;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.ArraySet;
import android.util.Slog;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.DisplayInfo;
import android.view.SurfaceControl;
import android.view.SurfaceControl.Transaction;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

final class DualStackController {
    private static final String TAG = "DualStackController";
 
    // Avoid hot-path logging overhead unless explicitly enabled.
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private static final String PROP_ENABLED = "persist.gammaos.dualstack.enabled";
    private static final String PROP_SWAP = "persist.gammaos.dualstack.swap";
    private static final String PROP_PKGS = "persist.gammaos.dualstack.pkgs";
    private static final String PROP_KILL_PKGS = "persist.gammaos.dualstack.killpackages.enabled";
    private static final String PROP_LAUNCH_GUARD_ENABLED = "persist.gammaos.launch.guard.enabled";
    private static final String PROP_ENFORCE_FOCUS = "persist.gammaos.dualstack.enforce_focus";
 
    private static final String PACKAGE_LAUNCHER3 = "com.android.launcher3";

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
            "com.dsemu.drastic",
    };

    private static final int DUALSTACK_TALL_WIDTH = 640;
    private static final int DUALSTACK_TALL_HEIGHT = 960;

    private final WindowManagerService mWm;

    private boolean mEnabled;
    private boolean mKillPackagesEnabled;
    private boolean mEnforceFocus;
    private String mLastPkgsRaw = null;
    private boolean mLastPropEnabled;
    private boolean mLastPropSwap;
    private boolean mLastPropKillPkgs;
    private boolean mLastPropEnforceFocus;
    private int mLastKillTaskId = -1;
    private int mLastElevateTaskId = -1;
    private int mElevateSeq = 0;

    private static final int DUALSTACK_ELEVATE_ATTEMPTS = 3;
    private static final int DUALSTACK_ELEVATE_INTERVAL_MS = 5000;
 
    // Sleep/wake handling: defer re-projection until the device is fully interactive to avoid
    // racing WM/display reconfiguration and triggering fragile app pause/resume paths.
    private static final long WAKE_REAPPLY_DELAY_MS = 1000;
    private static final long WAKE_STABILIZE_MS = 2000;

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
 
    // While dual-stack is active, keep display-2 "mirror-only":
    //  - Hide secondary windowing layer so nothing beneath the mirror is composited.
    //  - Purge any tasks that attempt to exist on the secondary display, killing their
    //    processes so they stop producing buffers (and thus stop Skia/GammaShader work).
    private static final long SECONDARY_PURGE_MIN_INTERVAL_MS = 2000;

    private boolean mSecondaryContentHidden;
    private long mLastSecondaryPurgeUptimeMs;
    // Reuse temporary lists to reduce allocations during purge cycles.
    private final ArrayList<Task> mTmpSecondaryTasksToRemove = new ArrayList<>();
    private final ArrayList<Boolean> mTmpSecondaryKillProcFlags = new ArrayList<>();

    // Best-effort: disable bilinear filtering on mirror layers (nearest-neighbor) to reduce
    // GPU cost during DualStack. We use reflection so we do not hard-depend on a specific
    // SurfaceControl.Transaction API surface across branches/vendors.
    private static volatile boolean sFilterMethodResolved;
    private static volatile Method sSetFilteringMethod;
 
    // Best-effort: mark mirror layers as opaque so SurfaceFlinger/HWC can more easily
    // assign them to DEVICE composition (HWC planes) when available.
    private static volatile boolean sOpaqueMethodResolved;
    private static volatile Method sSetOpaqueMethod;
 
    // Avoid doing aggressive focus/purge churn while screen is off / in transition.
    private volatile boolean mScreenInteractive = true;
    private volatile long mLastWakeUptimeMs;
    private volatile long mLastUserPresentUptimeMs;
    private final Runnable mWakeReapplyRunnable;

    private static final long FOCUS_ENFORCE_MIN_INTERVAL_MS = 750;
    private volatile long mLastFocusEnforceUptimeMs;
 
    // RetroArch clean-quit relies on briefly focusing RetroArch and injecting ESC.
    // Our DualStack focus enforcement can fight that (moving the DualStack task back to front),
    // causing RetroArch to never receive ESC and then get force-stopped.
    //
    // When we start a RetroArch clean-quit, temporarily suppress DualStack focus enforcement.
    private static final long RETROARCH_FOCUS_SUPPRESS_MS = 2500;
    private volatile long mSuppressDualStackFocusUntilUptimeMs = 0;
    // Also throttle repeated clean-quit requests (for example if RetroArch is on display-2).
    private static final long RETROARCH_QUIT_MIN_INTERVAL_MS = 5000;
    private volatile long mLastRetroarchQuitAttemptUptimeMs = 0;

    /** Cached mirror state so we can avoid re-applying identical transactions every traversal. */
    private static final class MirrorState {
        final Rect crop = new Rect();
        boolean hasCrop;
        float dsdx = 1f;
        float dtdx = 0f;
        float dtdy = 0f;
        float dsdy = 1f;
        float x = 0f;
        float y = 0f;
        boolean valid;

        void reset() {
            crop.setEmpty();
            hasCrop = false;
            dsdx = 1f;
            dtdx = 0f;
            dtdy = 0f;
            dsdy = 1f;
            x = 0f;
            y = 0f;
            valid = false;
        }
    }

    // One cached state per mirror target.
    private final MirrorState mPrimaryMirrorState = new MirrorState();
    private final MirrorState mSecondaryMirrorState = new MirrorState();

    // Reuse crop rects to avoid per-traversal allocations.
    private final Rect mTmpTopRect = new Rect();
    private final Rect mTmpBottomRect = new Rect();
    private final Rect mTmpSafeSecondaryCrop = new Rect();

    DualStackController(WindowManagerService wm) {
        mWm = wm;
        reloadProperties();

        mWakeReapplyRunnable = () -> {
            synchronized (mWm.mGlobalLock) {
                WindowManagerService.boostPriorityForLockedSection();
                try {
                    if (!mEnabled) return;
                    final DisplayContent dc = mWm.mRoot.getDisplayContent(DEFAULT_DISPLAY);
                    if (dc != null) maybeApplyDisplayProjectionsLocked(dc);
                } finally {
                    WindowManagerService.resetPriorityAfterLockedSection();
                }
            }
        };

        // Re-apply dual-stack state after sleep/wake, but only once the user has unlocked.
        // Some GL apps (including DraStic) are sensitive to rapid pause/resume/config churn.
        final IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_SCREEN_ON);
        filter.addAction(ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        wm.mContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = (intent != null) ? intent.getAction() : null;
                final long now = SystemClock.uptimeMillis();
                if (action == null) return;
                if (ACTION_SCREEN_OFF.equals(action)) {
                    mScreenInteractive = false;
                    mWm.mH.removeCallbacks(mWakeReapplyRunnable);
                    return;
                }
                if (ACTION_SCREEN_ON.equals(action)) {
                    mScreenInteractive = true;
                    mLastWakeUptimeMs = now;
                    // Do not re-apply immediately on SCREEN_ON. WM/display reconfiguration is still
                    // in-flight, and some apps crash when they receive rapid lifecycle/config churn.
                    mWm.mH.removeCallbacks(mWakeReapplyRunnable);
                    return;
                }
                if (Intent.ACTION_USER_PRESENT.equals(action)) {
                    mScreenInteractive = true;
                    mLastUserPresentUptimeMs = now;
                    mWm.mH.removeCallbacks(mWakeReapplyRunnable);
                    mWm.mH.postDelayed(mWakeReapplyRunnable, WAKE_REAPPLY_DELAY_MS);
                }
            }
        }, filter);

        // Re-apply dual-stack transforms whenever focus changes (e.g. menu ↔ gameplay).
        wm.mAtmService.getTaskChangeNotificationController()
                .registerTaskStackListener(new TaskStackListener() {
                    @Override
                    public void onTaskFocusChanged(int taskId, boolean focused) {
                        if (!focused) return;
                        synchronized (mWm.mGlobalLock) {
                            WindowManagerService.boostPriorityForLockedSection();
                            try {
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
        // SystemProperties reads are cheap, but parsing/splitting/allocating is not.
        // Cache property values and only rebuild whitelist when the raw string changes.
        final boolean enabled = SystemProperties.getBoolean(PROP_ENABLED, false);
        final boolean swap = SystemProperties.getBoolean(PROP_SWAP, false);
        final boolean killPkgs = SystemProperties.getBoolean(PROP_KILL_PKGS, false);
        final boolean enforceFocus = SystemProperties.getBoolean(PROP_ENFORCE_FOCUS, false);
        final String raw = SystemProperties.get(PROP_PKGS, "");

        if (enabled != mLastPropEnabled) {
            mEnabled = enabled;
            mLastPropEnabled = enabled;
        }
        if (swap != mLastPropSwap) {
            mSwapHalves = swap;
            mLastPropSwap = swap;
        }
        if (killPkgs != mLastPropKillPkgs) {
            mKillPackagesEnabled = killPkgs;
            mLastPropKillPkgs = killPkgs;
        }
        if (enforceFocus != mLastPropEnforceFocus) {
            mEnforceFocus = enforceFocus;
            mLastPropEnforceFocus = enforceFocus;
        }

        if (mLastPkgsRaw == null || !mLastPkgsRaw.equals(raw)) {
            mLastPkgsRaw = raw;
            final ArraySet<String> list = new ArraySet<>();
            if (!raw.isEmpty()) {
                final String[] parts = raw.split(",");
                for (int i = 0; i < parts.length; i++) {
                    final String p = parts[i].trim();
                    if (!p.isEmpty()) list.add(p);
                }
            }
            mWhitelist = list;
            if (DEBUG) {
                Slog.d(TAG, "Reloaded whitelist, size=" + mWhitelist.size());
            }
        }
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
     * Returns the best available "top" activity candidate on DEFAULT_DISPLAY for determining
     * whether DualStack must be active.
     *
     * We intentionally do NOT require RESUMED here, because during in-task activity transitions
     * (e.g. Drastic GameMenu -> EmuActivity) and sleep/wake, there can be brief windows where
     * no activity is RESUMED yet, and tearing down DualStack in that gap causes the forced
     * tall size to be cleared (your observed regression).
     */
    private ActivityRecord getTopCandidateOnDefaultDisplay() {
        final DisplayContent dc0 = mWm.mRoot.getDisplayContent(DEFAULT_DISPLAY);
        if (dc0 == null) return null;

        // Prefer the system's notion of top-resumed if available.
        final ActivityRecord topResumed = mWm.mRoot.getTopResumedActivity();
        if (topResumed != null && topResumed.getDisplayId() == DEFAULT_DISPLAY) {
            return topResumed;
        }

        // Otherwise fall back to the focused root task's top-most activity.
        final Task focused = dc0.getFocusedRootTask();
        if (focused != null) {
            final ActivityRecord topMost = focused.getTopMostActivity();
            if (topMost != null) return topMost;
        }

        // Final fallback: if we already have an active dual-stack task on DEFAULT_DISPLAY,
        // keep it alive across transient gaps.
        if (mActiveTask != null && mActiveTask.getDisplayId() == DEFAULT_DISPLAY) {
            final ActivityRecord last = mActiveTask.getTopMostActivity();
            if (last != null) return last;
        }

        return null;
    }

    void updateMirroringIfNeeded(Transaction t) {
        reloadProperties();
        if (!mEnabled) {
            setRuntimeDualStackActive(false);
            mLastKillTaskId = -1;
            mLastElevateTaskId = -1;
            clearForcedTallSizeIfNeeded();
            teardown(t);
            return;
        }

        final DisplayContent primary = mWm.mRoot.getDisplayContent(DEFAULT_DISPLAY);
        if (primary == null) {
            setRuntimeDualStackActive(false);
            mLastKillTaskId = -1;
            mLastElevateTaskId = -1;
            clearForcedTallSizeIfNeeded();
            teardown(t);
            return;
        }

        final DisplayContent secondary = findSecondaryInternalDisplayLocked();
        if (secondary == null) {
            setRuntimeDualStackActive(false);
            mLastKillTaskId = -1;
            mLastElevateTaskId = -1;
            clearForcedTallSizeIfNeeded();
            teardown(t);
            return;
        }

        // Decide whether DualStack must be active based on the current top candidate on
        // DEFAULT_DISPLAY, not strictly on RESUMED. This prevents teardown during activity
        // swap gaps and sleep/wake transitions.
        final ActivityRecord top = getTopCandidateOnDefaultDisplay();
        if (top == null) {
            // Transient gap: do not tear down, just wait for the next traversal/event.
            return;
        }

        final Task topTask = top.getTask();
        if (topTask == null) {
            // No task yet (rare transient). Keep state alive; avoid clearing forced size here.
            return;
        }

        if (!mWhitelist.contains(top.packageName)) {
            // Top default-display task is not dualstack-eligible: exit immediately.
            setRuntimeDualStackActive(false);
            mLastKillTaskId = -1;
            clearForcedTallSizeIfNeeded();
            teardown(t);
            return;
        }
 
        // Dual-stack is actively in-session. This is used by DMS to hide the secondary internal
        // display from apps so they cannot present/render to it directly.
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

        // Make sure the mirror is created first. If we purge display-2 content before the mirror
        // exists, the system may relaunch secondary HOME immediately and steal focus back.
        createSurfacesIfNeeded(t, primary, secondary, top);

        final long nowUptime = SystemClock.uptimeMillis();
        final boolean wakeStabilizing = (mLastWakeUptimeMs > 0)
                && (nowUptime - mLastWakeUptimeMs) < WAKE_STABILIZE_MS;

        // Performance: enforce "mirror-only" on secondary display to prevent any underlay
        // app rendering/composition cost on display-2 while dual-stack is active.
        if (mScreenInteractive && !wakeStabilizing) {
            suppressSecondaryDisplayContentUnderMirror(t, secondary, top.packageName);
        }

        // Critical for controller focus: keep the top-focused display on DEFAULT_DISPLAY while
        // dual-stack is active. Some apps are sensitive to focus churn on wake; keep this off by
        // default and only enable if you need controller/input stability fixes.
        final boolean shouldEnforceFocus = mEnforceFocus && mScreenInteractive && !wakeStabilizing;
        if (shouldEnforceFocus) {
            final long nowFocusUptime = nowUptime;
            if (nowFocusUptime - mLastFocusEnforceUptimeMs >= FOCUS_ENFORCE_MIN_INTERVAL_MS) {
                mLastFocusEnforceUptimeMs = nowFocusUptime;
                ensurePrimaryDisplayFocus(topTask);
                ensureTopActivityFocused(top);
            }
        }

        // Track the currently top-resumed ActivityRecord for logging / debugging.
        mActiveActivity = top;

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

    /**
     * When dual-stack is active, we must keep DEFAULT_DISPLAY as the top-focused display.
     *
     * logs show mTopFocusedDisplayId=2 and imeLayeringTarget in display#2 pointing at
     * SecondaryDisplayLauncher. That focus steal breaks controller input and also interferes
     * with the portrait enforcement path in WMS mapOrientationRequest() (top-resumed/focus
     * becomes inconsistent after wake).
     *
     * We solve this by explicitly bringing the dual-stack task to the front again when we
     * detect we are not top-focused on DEFAULT_DISPLAY.
     */
    private void ensurePrimaryDisplayFocus(Task topTask) {
        if (topTask == null) return;
        // Let RetroArch clean-quit temporarily take focus.
        if (SystemClock.uptimeMillis() < mSuppressDualStackFocusUntilUptimeMs) {
            return;
        }
        final DisplayContent topFocused = mWm.mRoot.getTopFocusedDisplayContent();
        if (topFocused == null) return;
        if (topFocused.getDisplayId() == DEFAULT_DISPLAY) return;
        final int taskId = topTask.mTaskId;
        // Do NOT call into ATMS synchronously from this path. Post to handler to avoid
        // lock/transition churn during wake and activity swaps.
        mWm.mH.post(() -> {
            try {
                mWm.mAtmService.moveTaskToFront(
                        (IApplicationThread) null, "android", taskId, 0 /* flags */, null);
            } catch (Throwable e) {
                Slog.w(TAG, "DualStack failed to re-focus primary display task=" + taskId, e);
            }
        });
    }
 
    /**
     * Ensures the dual-stack app remains the *focused window* on DEFAULT_DISPLAY.
     *
     * Moving the task to front is often sufficient, but on some builds display-2 HOME/IME can
     * still become the focused display/window briefly (stealing controller input). This is a
     * best-effort reinforcement.
     */
    private void ensureTopActivityFocused(ActivityRecord top) {
        if (top == null) return;
        // Let RetroArch clean-quit temporarily take focus.
        if (SystemClock.uptimeMillis() < mSuppressDualStackFocusUntilUptimeMs) {
            return;
        }
        try {
            // Re-assert focus by moving the task to front again. This is intentionally cheap
            // and guarded by the top-focused display check in ensurePrimaryDisplayFocus().
            final Task task = top.getTask();
            if (task == null) return;
            final DisplayContent topFocused = mWm.mRoot.getTopFocusedDisplayContent();
            if (topFocused == null) return;
            if (topFocused.getDisplayId() != DEFAULT_DISPLAY) {
                final int taskId = task.mTaskId;
                mWm.mH.post(() -> {
                    try {
                        mWm.mAtmService.moveTaskToFront(
                                (IApplicationThread) null, "android", taskId, 0 /* flags */, null);
                    } catch (Throwable ignored) {
                    }
                });
            }
        } catch (Throwable ignored) {
        }
    }
 
    private void suppressSecondaryDisplayContentUnderMirror(Transaction t, DisplayContent secondary,
            String dualStackPackage) {
        if (t == null || secondary == null) return;
 
        // If RetroArch is alive on display-2, do NOT kill/remove it from here.
        // That would bypass the clean quit path and make it look like RetroArch is “killed
        // immediately” during DualStack transitions. Instead, request a clean quit and let
        // killAllAppsExcept() handle any eventual force-stop if it does not exit.
        final long nowUptime = SystemClock.uptimeMillis();

        // Hide all secondary windowing content so nothing underneath the mirror is composited.
        final SurfaceControl secondaryWindowingLayer = secondary.getWindowingLayer();
        if (!mSecondaryContentHidden
                && secondaryWindowingLayer != null
                && secondaryWindowingLayer.isValid()) {
            // Only issue the hide transaction once; repeating it every traversal increases
            // WM->SF transaction churn and can create additional fences under load.
            t.hide(secondaryWindowingLayer);
            mSecondaryContentHidden = true;
        }

        // Purge any tasks that (re)appear on the secondary display; throttle for stability.
        final long now = SystemClock.uptimeMillis();
        if (now - mLastSecondaryPurgeUptimeMs < SECONDARY_PURGE_MIN_INTERVAL_MS) {
            return;
        }
        mLastSecondaryPurgeUptimeMs = now;

        // Reuse lists to reduce allocations/GC pressure.
        mTmpSecondaryTasksToRemove.clear();
        mTmpSecondaryKillProcFlags.clear();
        secondary.forAllLeafTasks(task -> {
            if (task == null) return;
            final ActivityRecord top = task.getTopMostActivity();
            if (top == null) return;
            final String pkg = top.packageName;
            if (pkg == null || pkg.isEmpty()) return;
            // Defensive: never purge the active dual-stack package (it should not be on display-2).
            if (dualStackPackage != null && dualStackPackage.equals(pkg)) return;

            // RetroArch special-case: prefer clean quit over kill/remove from display-2.
            if (pkg.startsWith("com.retroarch")
                    && SystemProperties.getBoolean(PROP_LAUNCH_GUARD_ENABLED, false)) {
                maybeRequestRetroarchCleanQuitLocked(pkg, nowUptime);
                return;
            }

            mTmpSecondaryTasksToRemove.add(task);
            // IMPORTANT: do not kill the Launcher3 process. Killing it will also kill the
            // taskbar on DEFAULT_DISPLAY. We only want to remove the secondary-display HOME task.
            mTmpSecondaryKillProcFlags.add(!PACKAGE_LAUNCHER3.equals(pkg));
        }, true /* traverseTopToBottom */);

        for (int i = 0; i < mTmpSecondaryTasksToRemove.size(); i++) {
            final Task task = mTmpSecondaryTasksToRemove.get(i);
            try {
                final boolean doKill = mTmpSecondaryKillProcFlags.get(i);
                // doKill=true stops producers (Skia/GammaShader) from generating buffers.
                // For Launcher3 secondary display home, doKill=false to preserve taskbar process.
                mWm.mAtmService.mTaskSupervisor.removeTask(
                        task, doKill /* killProcess */, true /* removeFromRecents */,
                        "dualstack-secondary-purge");
            } catch (Throwable e) {
                Slog.w(TAG, "DualStack failed purging secondary taskId=" + task.mTaskId, e);
            }
        }
    }
 
    private void maybeRequestRetroarchCleanQuitLocked(String pkg, long nowUptime) {
        if (pkg == null || !pkg.startsWith("com.retroarch")) return;
        if (nowUptime - mLastRetroarchQuitAttemptUptimeMs < RETROARCH_QUIT_MIN_INTERVAL_MS) {
            return;
        }
        mLastRetroarchQuitAttemptUptimeMs = nowUptime;
        // Suppress our focus reassertion briefly so RetroArch can actually take focus and
        // receive the ESC injection.
        mSuppressDualStackFocusUntilUptimeMs = nowUptime + RETROARCH_FOCUS_SUPPRESS_MS;

        // Run the quit attempt off-lock on the handler thread.
        final String targetPkg = pkg;
        final int userId = mWm.mCurrentUserId;
        mWm.mH.post(() -> {
            try {
                final ActivityManager am =
                        (ActivityManager) mWm.mContext.getSystemService(Context.ACTIVITY_SERVICE);
                if (am == null) return;
                final boolean needsForceStop = requestRetroarchCleanQuit(targetPkg, userId, am);
                if (!needsForceStop) {
                    Slog.i(TAG, "DualStack RetroArch clean quit succeeded (secondary): " + targetPkg);
                } else {
                    Slog.i(TAG, "DualStack RetroArch clean quit timed out (secondary): " + targetPkg);
                }
            } catch (Throwable ignored) {
            }
        });
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
            if (DEBUG) {
                Slog.d(TAG, "DualStack forced tall size "
                        + DUALSTACK_TALL_WIDTH + "x" + DUALSTACK_TALL_HEIGHT
                        + " on display " + DEFAULT_DISPLAY);
            }
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
 
        // Force the cached state to re-apply transforms for the newly created mirrors.
        mPrimaryMirrorState.reset();
        mSecondaryMirrorState.reset();

        // Primary mirror.
        mAppXformTarget = SurfaceControl.mirrorSurface(source);
        // Attach above the real windowing tree so the mirror is the only visible content.
        t.reparent(mAppXformTarget, primary.getOverlayLayer());
        t.setLayer(mAppXformTarget, Integer.MAX_VALUE / 2);
        // Prefer DEVICE composition when possible: make the layer fully opaque and avoid
        // unnecessary blending paths.
        t.setAlpha(mAppXformTarget, 1.0f);
        setOpaqueIfSupported(t, mAppXformTarget, true);
        // Apply nearest-neighbor sampling once at creation time (no need to re-apply per frame).
        setNoBilinearFilteringIfSupported(t, mAppXformTarget);
        t.show(mAppXformTarget);

        // Secondary mirror.
        mSecondaryMirror = SurfaceControl.mirrorSurface(source);
        t.reparent(mSecondaryMirror, secondary.getOverlayLayer());
        t.setLayer(mSecondaryMirror, Integer.MAX_VALUE / 2);
        t.setAlpha(mSecondaryMirror, 1.0f);
        setOpaqueIfSupported(t, mSecondaryMirror, true);
        // Apply nearest-neighbor sampling once at creation time.
        setNoBilinearFilteringIfSupported(t, mSecondaryMirror);
        t.show(mSecondaryMirror);
    }
 
    private static boolean floatNear(float a, float b) {
        return Math.abs(a - b) <= 1e-4f;
    }

    private static float snapScale(float v) {
        // Snap common ratios to exact values to reduce tiny float drift across traversals.
        if (floatNear(v, 1.0f)) return 1.0f;
        if (floatNear(v, 2.0f)) return 2.0f;
        if (floatNear(v, 0.5f)) return 0.5f;
        return v;
    }

    private static float snapTranslate(float v) {
        // Keep translations on integer boundaries where possible.
        final float r = Math.round(v);
        return floatNear(v, r) ? r : v;
    }

    private void applyMirrorStateIfChanged(Transaction t, SurfaceControl sc, Rect crop,
            float dsdx, float dsdy, float x, float y, MirrorState st) {
        if (t == null || sc == null || !sc.isValid() || st == null) return;

        dsdx = snapScale(dsdx);
        dsdy = snapScale(dsdy);
        x = snapTranslate(x);
        y = snapTranslate(y);

        // Crop.
        if (crop == null) {
            if (st.hasCrop) {
                t.setWindowCrop(sc, (Rect) null);
                st.crop.setEmpty();
                st.hasCrop = false;
            }
        } else {
            if (!st.hasCrop || !st.crop.equals(crop)) {
                t.setWindowCrop(sc, crop);
                st.crop.set(crop);
                st.hasCrop = true;
            }
        }

        // Matrix (we only ever use axis-aligned scale here).
        if (!st.valid || !floatNear(st.dsdx, dsdx) || !floatNear(st.dsdy, dsdy)
                || !floatNear(st.dtdx, 0f) || !floatNear(st.dtdy, 0f)) {
            t.setMatrix(sc, dsdx, 0f, 0f, dsdy);
            st.dsdx = dsdx;
            st.dtdx = 0f;
            st.dtdy = 0f;
            st.dsdy = dsdy;
        }

        // Position.
        if (!st.valid || !floatNear(st.x, x) || !floatNear(st.y, y)) {
            t.setPosition(sc, x, y);
            st.x = x;
            st.y = y;
        }

        st.valid = true;
    }

    /**
     * Request nearest-neighbor sampling for the given surface if the platform exposes a
     * Transaction API for it.
     *
     * Some platforms expose Transaction#setFiltering(SurfaceControl, boolean). If present:
     *   filtering=false => nearest-neighbor
     * We intentionally keep this best-effort, because the exact API name can vary across
     * branches/vendor merges.
     */
    private static void setNoBilinearFilteringIfSupported(Transaction t, SurfaceControl sc) {
        if (t == null || sc == null || !sc.isValid()) return;

        if (!sFilterMethodResolved) {
            sFilterMethodResolved = true;
            // Try the common AOSP name first.
            try {
                sSetFilteringMethod = Transaction.class.getMethod(
                        "setFiltering", SurfaceControl.class, boolean.class);
            } catch (NoSuchMethodException ignored) {
            }
            // Try a couple of vendor/common alternates.
            if (sSetFilteringMethod == null) {
                try {
                    sSetFilteringMethod = Transaction.class.getMethod(
                            "setFilter", SurfaceControl.class, boolean.class);
                } catch (NoSuchMethodException ignored) {
                }
            }
            if (sSetFilteringMethod == null) {
                try {
                    sSetFilteringMethod = Transaction.class.getMethod(
                            "setLayerStackFiltering", SurfaceControl.class, boolean.class);
                } catch (NoSuchMethodException ignored) {
                }
            }
        }

        final Method m = sSetFilteringMethod;
        if (m == null) return;

        try {
            // Pass "false" to request nearest-neighbor sampling.
            m.invoke(t, sc, false);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            // One-time best-effort. If this fails at runtime, silently fall back to default.
        }
    }
 
    /**
     * Best-effort wrapper around Transaction#setOpaque(SurfaceControl, boolean).
     *
     * Marking the mirror layers as opaque helps SurfaceFlinger/HWC avoid blending work and can
     * increase the likelihood of DEVICE composition on SoCs with limited plane resources.
     */
    private static void setOpaqueIfSupported(Transaction t, SurfaceControl sc, boolean opaque) {
        if (t == null || sc == null || !sc.isValid()) return;

        if (!sOpaqueMethodResolved) {
            sOpaqueMethodResolved = true;
            try {
                sSetOpaqueMethod = Transaction.class.getMethod(
                        "setOpaque", SurfaceControl.class, boolean.class);
            } catch (NoSuchMethodException ignored) {
            }
        }

        final Method m = sSetOpaqueMethod;
        if (m == null) return;

        try {
            m.invoke(t, sc, opaque);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            // Best-effort only.
        }
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
                applyMirrorStateIfChanged(t, mAppXformTarget, null,
                        1f /*sx*/, 1f /*sy*/, 0f /*x*/, 0f /*y*/, mPrimaryMirrorState);
            }
            if (mSecondaryMirror != null) {
                applyMirrorStateIfChanged(t, mSecondaryMirror, null,
                        1f /*sx*/, 1f /*sy*/, 0f /*x*/, 0f /*y*/, mSecondaryMirrorState);
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
        mTmpTopRect.set(contentLeft, 0, contentLeft + appW, halfH);
        mTmpBottomRect.set(contentLeft, halfH, contentLeft + appW, appH);

        // Decide which half goes where.
        final Rect primaryCrop = mSwapHalves ? mTmpTopRect : mTmpBottomRect;   // bottom by default
        final Rect secondaryCrop = mSwapHalves ? mTmpBottomRect : mTmpTopRect; // top by default

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
        mTmpSafeSecondaryCrop.set(secondaryCrop);
        if (mTmpSafeSecondaryCrop.bottom - mTmpSafeSecondaryCrop.top > halfH) {
            mTmpSafeSecondaryCrop.bottom = mTmpSafeSecondaryCrop.top + halfH;
        }

        // PRIMARY:
        // Show the chosen half (bottom by default) scaled to the final tall logical height.
        if (mAppXformTarget != null) {
            // For the default (bottom on primary), shift it up by half a canvas.
            // When swapped, leave it at y=0 so the top half sits “normally”.
            final float primaryTy = mSwapHalves
                    ? 0f
                    : -halfH * primarySy;
            // Apply horizontal compensation so that the app content starts at X=0 on
            // the primary display even if WM laid it out with an internal margin.
            applyMirrorStateIfChanged(t, mAppXformTarget, primaryCrop,
                    primarySx, primarySy, primaryTx, primaryTy, mPrimaryMirrorState);

            if (DEBUG) {
                Slog.d(TAG, "DualStack primary crop=" + primaryCrop
                        + " sx=" + primarySx + " sy=" + primarySy);
            }
        }

        // SECONDARY:
        // Show the complementary half scaled to fill the secondary panel.
        if (mSecondaryMirror != null) {
            // Apply the same X compensation so the secondary display lines up with
            // the primary and there is no visible left gutter.
            applyMirrorStateIfChanged(t, mSecondaryMirror, mTmpSafeSecondaryCrop,
                    secondarySx, secondarySy, secondaryTx, 0f, mSecondaryMirrorState);

            if (DEBUG) {
                Slog.d(TAG, "DualStack secondary crop=" + secondaryCrop
                        + " app=" + appW + "x" + appH
                        + " disp0=" + primaryDw + "x" + primaryDh
                        + " disp2=" + secondaryDw + "x" + secondaryDh
                        + " sx=" + secondarySx + " sy=" + secondarySy);
            }
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

        // NOTE: forAllWindows typically expects a boolean-returning callback (ToBooleanFunction).
        // Return true to continue traversal.
        top.forAllWindows(ws -> {
            if (ws == null) return true;

            // Match the standard letterbox window naming used by WM:
            // "Letterbox - left", "Letterbox - right", "Letterbox - bottom".
            final CharSequence titleCs =
                    (ws.mAttrs != null) ? ws.mAttrs.getTitle() : null;
            if (titleCs == null) return true;

            final String title = titleCs.toString();
            if (!title.startsWith("Letterbox - ")) return true;

            final SurfaceControl sc = ws.getSurfaceControl();
            if (sc == null || !sc.isValid()) return true;
            t.hide(sc);
            return true;
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

        final ArraySet<String> killed = new ArraySet<>();
        for (int i = 0; i < running.size(); i++) {
            final ActivityManager.RunningAppProcessInfo proc = running.get(i);
            if (proc == null || proc.pkgList == null) {
                continue;
            }
            for (int j = 0; j < proc.pkgList.length; j++) {
                final String pkg = proc.pkgList[j];
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
                    // If GammaOS launch-guard is enabled, prefer RetroArch's clean quit path
                    // over force-stopping it. This mirrors the behaviour used in
                    // ActivityTaskManagerService.maybeForceRestartGammaLaunchGuard().
                    if (shouldAttemptRetroarchCleanQuit(pkg)) {
                        final boolean needsForceStop = requestRetroarchCleanQuit(pkg, userId, am);
                        if (!needsForceStop) {
                            killed.add(pkg);
                            Slog.i(TAG, "DualStack RetroArch clean quit succeeded: " + pkg);
                            continue;
                        }
                        Slog.i(TAG, "DualStack RetroArch clean quit timed out; force-stopping: " + pkg);
                    }

                    final ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                    if (ai == null) {
                        continue;
                    }
                    // Never kill core system UIDs, but do allow killing system apps like Launcher.
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
        }
        Slog.i(TAG, "DualStack kill sweep complete. Kept=" + keepPackages + " killed=" + killed);
    }

 
    private static boolean shouldAttemptRetroarchCleanQuit(String pkg) {
        if (pkg == null) return false;
        if (!pkg.startsWith("com.retroarch")) return false;
        return SystemProperties.getBoolean(PROP_LAUNCH_GUARD_ENABLED, /* def */ false);
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
                        break;
                    }
                }
            }
        } catch (Throwable ignored) {
            // If we fail to enumerate tasks for any reason, fall back to force-stop.
        }

        if (retroTaskId != -1) {
            try {
                // Suppress DualStack focus enforcement briefly so RetroArch can be focused and
                // receive the ESC injection. Without this, DualStack may immediately steal focus
                // back and RetroArch will be force-stopped instead of exiting cleanly.
                mSuppressDualStackFocusUntilUptimeMs =
                        SystemClock.uptimeMillis() + RETROARCH_FOCUS_SUPPRESS_MS;
                // Direct call into ATMS (same process). Use a stable callingPackage.
                mWm.mAtmService.moveTaskToFront(
                        (IApplicationThread) null, "android", retroTaskId, 0 /* flags */, null);
            } catch (Throwable t) {
                // If we cannot bring it to front, still proceed to ESC / wait.
            }

            // Give WM a brief moment to focus RetroArch.
            SystemClock.sleep(1000);
            injectGammaEscapeKey();
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
    private static void injectGammaEscapeKey() {
        final long now = SystemClock.uptimeMillis();
        final KeyEvent down = new KeyEvent(now, now,
                KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE, 0 /* repeat */,
                0 /* metaState */, KeyCharacterMap.VIRTUAL_KEYBOARD, 0 /* scancode */,
                0 /* flags */, InputDevice.SOURCE_KEYBOARD);
        final KeyEvent up = KeyEvent.changeAction(down, KeyEvent.ACTION_UP);

        final InputManager im = InputManager.getInstance();
        if (im != null) {
            im.injectInputEvent(down, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
            im.injectInputEvent(up, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
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
            if (DEBUG) {
                Slog.d(TAG, "DualStack cleared forced tall size on display " + DEFAULT_DISPLAY);
            }
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
        // Clear runtime flag so apps can see secondary display again after dual-stack ends.
        setRuntimeDualStackActive(false);

        // If we hid the secondary display's windowing layer, restore it as we exit dual-stack.
        if (mSecondaryContentHidden && mSecondaryDisplayId != -1) {
            final DisplayContent secondary = mWm.mRoot.getDisplayContent(mSecondaryDisplayId);
            if (secondary != null) {
                final SurfaceControl wl = secondary.getWindowingLayer();
                if (wl != null && wl.isValid()) {
                    try {
                        t.show(wl);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        mSecondaryContentHidden = false;
        mLastSecondaryPurgeUptimeMs = 0;

        if (mAppXformTarget != null) {
            try {
                // Reset to identity then remove. Use the cached state helper to avoid
                // emitting redundant transactions during repeated teardown calls.
                applyMirrorStateIfChanged(t, mAppXformTarget, null,
                        1f /*sx*/, 1f /*sy*/, 0f /*x*/, 0f /*y*/, mPrimaryMirrorState);
                t.remove(mAppXformTarget);
            } catch (Exception ignored) {
            }
            mAppXformTarget = null;
        }
        if (mSecondaryMirror != null) {
            try {
                // Ensure cached transform state is cleared even if remove fails.
                mSecondaryMirrorState.reset();
                t.remove(mSecondaryMirror);
            } catch (Exception ignored) {
            }
            mSecondaryMirror = null;
        }
        // Always clear cached state when leaving dual-stack.
        mPrimaryMirrorState.reset();
        mSecondaryMirrorState.reset();
        mSourceSurface = null;
        mActiveTask = null;
        mActiveActivity = null;
        mSecondaryDisplayId = -1;
        mAwaitingFirstValidSurface = false;
    }

}
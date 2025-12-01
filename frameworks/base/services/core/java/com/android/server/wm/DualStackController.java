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

import android.graphics.Rect;
import android.app.TaskStackListener;
import android.os.SystemProperties;
import android.util.ArraySet;
import android.util.Slog;
import android.util.Log;
import android.view.DisplayInfo;
import android.view.SurfaceControl;
import android.view.SurfaceControl.Transaction;

final class DualStackController {
    private static final String TAG = "DualStackController";

    private static final String PROP_ENABLED = "persist.gammaos.dualstack.enabled";
    private static final String PROP_SWAP = "persist.gammaos.dualstack.swap";
    private static final String PROP_PKGS = "persist.gammaos.dualstack.pkgs";

    private static final int DUALSTACK_TALL_WIDTH = 640;
    private static final int DUALSTACK_TALL_HEIGHT = 960;

    private final WindowManagerService mWm;

    private boolean mEnabled;

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
            clearForcedTallSizeIfNeeded();
            teardown(t);
            return;
        }

        final DisplayContent primary = mWm.mRoot.getDisplayContent(DEFAULT_DISPLAY);
        if (primary == null) {
            clearForcedTallSizeIfNeeded();
            teardown(t);
            return;
        }

        final DisplayContent secondary = findSecondaryInternalDisplayLocked();
        if (secondary == null) {
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
            clearForcedTallSizeIfNeeded();
            teardown(t);
            return;
        }
 
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
            clearForcedTallSizeIfNeeded();
            teardown(t);
            return;
        }

        applyForcedTallSizeIfNeeded();

        // Treat the dual-stack "session" as Task-scoped, not Activity-scoped.
        // This avoids tearing down mirrors on Emu <-> GameMenu swaps within the same task.
        final boolean taskOrDisplayChanged =
                mActiveTask != topTask
                || mSecondaryDisplayId != secondary.getDisplayId();

        if (taskOrDisplayChanged) {
            teardown(t);
            mActiveTask = topTask;
            mSecondaryDisplayId = secondary.getDisplayId();
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
        // Create two mirrors of the app surface:
        //  - one on the primary display overlay (bottom half by default),
        //  - one on the secondary display overlay (top half by default).
        //
        // The original app surfaces remain owned by WM and continue to receive
        // input, but visually we only show the mirrored copies on both displays.
        // This lets us apply different crops for primary and secondary without
        // fighting any letterboxing / transforms applied to the task itself.
        SurfaceControl source = null;
        final SurfaceControl arSc = top.getSurfaceControl();
        if (arSc != null && arSc.isValid()) {
            source = arSc;
        } else {
            final WindowState mainWin = top.findMainWindow();
            if (mainWin != null) {
                final SurfaceControl sc = mainWin.getSurfaceControl();
                if (sc != null && sc.isValid()) {
                    source = sc;
                }
            }
        }

        if (source == null) {
            Slog.w(TAG, "createSurfacesIfNeeded: no valid source surface for " + top);
            return;
        }

        final boolean havePrimary = mAppXformTarget != null && mAppXformTarget.isValid();
        final boolean haveSecondary = mSecondaryMirror != null && mSecondaryMirror.isValid();

        // If both mirrors are already alive *and* we are still mirroring from the same
        // render root, keep them and just update crops / matrices in applyTransforms().
        // As soon as the Activity's render root changes (new VRI/BBQ adapter, new BLAST,
        // etc.), we want to rebind our mirrors to the new SurfaceControl even if the
        // old ones are still "valid".
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

        // Primary mirror (bottom half by default).
        mAppXformTarget = SurfaceControl.mirrorSurface(source);
        t.reparent(mAppXformTarget, primary.getOverlayLayer());
        // Slightly below the secondary mirror so the secondary can use the same
        // max layer value without Z-fighting.
        t.setLayer(mAppXformTarget, (Integer.MAX_VALUE / 2) - 1);
        t.show(mAppXformTarget);

        // Secondary mirror (top half by default).
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

        // Try to infer the app's *actual* content bounds from the top activity configuration.
        // For classic dual-stack apps like DraStic that already render at 640x960, this will
        // typically be 640x960. For size-compat apps like RSDK that are running in a smaller
        // 480x640 box, this lets us scale that box up without dragging WM's own
        // letterbox bars into the mirrored view.
        int appW = DUALSTACK_TALL_WIDTH;
        int appH = DUALSTACK_TALL_HEIGHT;
        // Horizontal offset of the real app content inside the root / display coordinates.
        // For compat apps like RSDK, the live 3:4 content is narrower than the window and
        // is centered, so there are explicit left/right letterbox margins we want to skip.
        int contentLeft = 0;
        try {
            final Rect appBounds =
                    top.getConfiguration().windowConfiguration.getAppBounds();
            if (appBounds != null && !appBounds.isEmpty()) {
                final int bw = appBounds.width();
                final int bh = appBounds.height();
                if (bw > 0 && bh > 0) {
                    // Heuristic:
                    //  - If the app's height is "almost" our tall canvas (e.g. DraStic
                    //    reporting ~926px because of insets), treat it as a full 640x960
                    //    so the split is exactly 480/480.
                    //  - Otherwise (e.g. 480x640 size-compat), the underlying content is
                    //    effectively 3:4 portrait (480x640) centered inside a narrower
                    //    compat box (e.g. 400x640). In that case we:
                    //      * recover the full 3:4 width from the height, and
                    //      * compute a crop region in root coordinates that skips
                    //        any left/right letterbox bars.
                    final int tallThreshold = (DUALSTACK_TALL_HEIGHT * 4) / 5; // 80% of 960 = 768
                    if (bh >= tallThreshold) {
                        // Treat as full tall canvas.
                        appW = DUALSTACK_TALL_WIDTH;
                        appH = DUALSTACK_TALL_HEIGHT;
                        contentLeft = 0;
                    } else {
                        // Compat / letterboxed app (e.g. RSDK).
                        // bh is the vertical canvas (e.g. 640).
                        // Real content is roughly 3:4 portrait, so infer width from height.
                        final int inferredCompatW = (bh * 3) / 4;   // e.g. 480 when bh=640
                        final int effectiveW = Math.max(bw, inferredCompatW);

                        appW = Math.min(DUALSTACK_TALL_WIDTH, effectiveW);
                        appH = Math.min(DUALSTACK_TALL_HEIGHT, bh);

                        // App content is horizontally centered inside appBounds; compute
                        // the left edge in root coordinates and clamp to the 640-wide canvas.
                        final int centerX = appBounds.left + (bw / 2);
                        contentLeft = centerX - (appW / 2);
                        if (contentLeft < 0) {
                            contentLeft = 0;
                        } else if (contentLeft + appW > DUALSTACK_TALL_WIDTH) {
                            contentLeft = DUALSTACK_TALL_WIDTH - appW;
                        }
                    }
                }
            }
        } catch (Throwable tIgnored) {
            // If anything goes wrong, fall back to the fixed tall canvas.
        }

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

/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.server.wm;

import static android.view.Display.DEFAULT_DISPLAY;

import static junit.framework.Assert.assertTrue;

import android.Manifest;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.IBinder;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.window.WindowInfosListenerForTest;
import android.window.WindowInfosListenerForTest.WindowInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.CtsTouchUtils;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.SystemUtil;
import com.android.compatibility.common.util.ThrowingRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class CtsWindowInfoUtils {
    private static final int HW_TIMEOUT_MULTIPLIER = SystemProperties.getInt(
            "ro.hw_timeout_multiplier", 1);

    /**
     * Calls the provided predicate each time window information changes.
     *
     * <p>
     * <strong>Note:</strong>The caller must have
     * android.permission.ACCESS_SURFACE_FLINGER permissions.
     * </p>
     *
     * @param predicate The predicate tested each time window infos change.
     * @param timeout   The amount of time to wait for the predicate to be satisfied.
     * @param unit      The units associated with timeout.
     * @param uiAutomation Pass in a uiAutomation to use. If null is passed in, the default will
     *                     be used. Passing non null is only needed if the test has a custom version
     *                     of uiAutomtation since retrieving a uiAutomation could overwrite it.
     * @return True if the provided predicate is true for any invocation before
     * the timeout is reached. False otherwise.
     */
    public static boolean waitForWindowInfos(@NonNull Predicate<List<WindowInfo>> predicate,
            long timeout, @NonNull TimeUnit unit, @Nullable UiAutomation uiAutomation)
            throws InterruptedException {
        var latch = new CountDownLatch(1);
        var satisfied = new AtomicBoolean();

        Consumer<List<WindowInfo>> checkPredicate = windowInfos -> {
            if (satisfied.get()) {
                return;
            }
            if (predicate.test(windowInfos)) {
                satisfied.set(true);
                latch.countDown();
            }
        };

        var waitForWindow = new ThrowingRunnable() {
            @Override
            public void run() throws InterruptedException {
                var listener = new WindowInfosListenerForTest();
                try {
                    listener.addWindowInfosListener(checkPredicate);
                    latch.await(timeout, unit);
                } finally {
                    listener.removeWindowInfosListener(checkPredicate);
                }
            }
        };

        if (uiAutomation == null) {
            uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        }
        Set<String> shellPermissions = uiAutomation.getAdoptedShellPermissions();
        if (shellPermissions.isEmpty()) {
            SystemUtil.runWithShellPermissionIdentity(uiAutomation, waitForWindow,
                    Manifest.permission.ACCESS_SURFACE_FLINGER);
        } else if (shellPermissions.contains(Manifest.permission.ACCESS_SURFACE_FLINGER)) {
            waitForWindow.run();
        } else {
            throw new IllegalStateException(
                    "waitForWindowOnTop called with adopted shell permissions that don't include "
                            + "ACCESS_SURFACE_FLINGER");
        }

        return satisfied.get();
    }

    /**
     * Same as {@link #waitForWindowInfos(Predicate, long, TimeUnit, UiAutomation)}, but passes in
     * a null uiAutomation object. This should be used in most cases unless there's a custom
     * uiAutomation object used in the test.
     *
     * @param predicate The predicate tested each time window infos change.
     * @param timeout   The amount of time to wait for the predicate to be satisfied.
     * @param unit      The units associated with timeout.
     * @return True if the provided predicate is true for any invocation before
     * the timeout is reached. False otherwise.
     */
    public static boolean waitForWindowInfos(@NonNull Predicate<List<WindowInfo>> predicate,
            long timeout, @NonNull TimeUnit unit) throws InterruptedException {
        return waitForWindowInfos(predicate, timeout, unit, null /* uiAutomation */);
    }

    /**
     * Calls the provided predicate each time window information changes if a visible
     * window is found that matches the supplied window token.
     *
     * <p>
     * <strong>Note:</strong>The caller must have the
     * android.permission.ACCESS_SURFACE_FLINGER permissions.
     * </p>
     *
     * @param predicate           The predicate tested each time window infos change.
     * @param timeout             The amount of time to wait for the predicate to be satisfied.
     * @param unit                The units associated with timeout.
     * @param windowTokenSupplier Supplies the window token for the window to
     *                            call the predicate on. The supplier is called each time window
     *                            info change. If the supplier returns null, the predicate is
     *                            assumed false for the current invocation.
     * @param displayId           The id of the display on which to wait for the window of interest
     * @return True if the provided predicate is true for any invocation before the timeout is
     * reached. False otherwise.
     * @hide
     */
    public static boolean waitForWindowInfo(@NonNull Predicate<WindowInfo> predicate, long timeout,
            @NonNull TimeUnit unit, @NonNull Supplier<IBinder> windowTokenSupplier, int displayId)
            throws InterruptedException {
        Predicate<List<WindowInfo>> wrappedPredicate = windowInfos -> {
            IBinder windowToken = windowTokenSupplier.get();
            if (windowToken == null) {
                return false;
            }

            for (var windowInfo : windowInfos) {
                if (!windowInfo.isVisible) {
                    continue;
                }
                // only wait for requested display.
                if (windowInfo.windowToken == windowToken
                        && windowInfo.displayId == displayId) {
                    return predicate.test(windowInfo);
                }
            }

            return false;
        };
        return waitForWindowInfos(wrappedPredicate, timeout, unit);
    }

    /**
     * Waits for the window associated with the view to be present.
     */
    public static boolean waitForWindowVisible(@NonNull View view) throws InterruptedException {
        // Wait until view is attached to a display
        PollingCheck.waitFor(() -> view.getDisplay() != null, "View not attached to a display");
        return waitForWindowInfo(windowInfo -> true, HW_TIMEOUT_MULTIPLIER * 5L, TimeUnit.SECONDS,
                view::getWindowToken, view.getDisplay().getDisplayId());
    }

    public static boolean waitForWindowVisible(@NonNull IBinder windowToken)
            throws InterruptedException {
        return waitForWindowInfo(windowInfo -> true, HW_TIMEOUT_MULTIPLIER * 5L, TimeUnit.SECONDS,
                () -> windowToken, DEFAULT_DISPLAY);
    }

    /**
     * Calls {@link CtsWindowInfoUtils#waitForWindowOnTop(int, TimeUnit, Supplier)}. Adopts
     * required permissions and waits at least five seconds before timing out.
     *
     * @param window The window to wait on.
     * @return True if the window satisfies the visibility requirements before the timeout is
     * reached. False otherwise.
     */
    public static boolean waitForWindowOnTop(@NonNull Window window) throws InterruptedException {
        return waitForWindowOnTop(HW_TIMEOUT_MULTIPLIER * 5, TimeUnit.SECONDS,
                () -> window.getDecorView().getWindowToken());
    }

    /**
     * Waits until the window specified by the predicate is present, not occluded, and hasn't
     * had geometry changes for 200ms.
     *
     * The window is considered occluded if any part of another window is above it, excluding
     * trusted overlays.
     *
     * <p>
     * <strong>Note:</strong>If the caller has any adopted shell permissions, they must include
     * android.permission.ACCESS_SURFACE_FLINGER.
     * </p>
     *
     * @param timeout             The amount of time to wait for the window to be visible.
     * @param unit                The units associated with timeout.
     * @param windowTokenSupplier Supplies the window token for the window to wait on. The
     *                            supplier is called each time window infos change. If the
     *                            supplier returns null, the window is assumed not visible
     *                            yet.
     * @return True if the window satisfies the visibility requirements before the timeout is
     * reached. False otherwise.
     */
    public static boolean waitForWindowOnTop(long timeout, @NonNull TimeUnit unit,
                                             @NonNull Predicate<WindowInfo> predicate)
            throws InterruptedException {
        var latch = new CountDownLatch(1);
        var satisfied = new AtomicBoolean();

        var windowNotOccluded = new Consumer<List<WindowInfo>>() {
            private Timer mTimer = new Timer();
            private TimerTask mTask = null;
            private Rect mPreviousBounds = new Rect(0, 0, -1, -1);

            private void resetState() {
                if (mTask != null) {
                    mTask.cancel();
                    mTask = null;
                }
                mPreviousBounds.set(0, 0, -1, -1);
            }

            @Override
            public void accept(List<WindowInfo> windowInfos) {
                if (satisfied.get()) {
                    return;
                }

                WindowInfo targetWindowInfo = null;
                ArrayList<WindowInfo> aboveWindowInfos = new ArrayList<>();
                for (var windowInfo : windowInfos) {
                    if (predicate.test(windowInfo)) {
                        targetWindowInfo = windowInfo;
                        break;
                    }
                    if (windowInfo.isTrustedOverlay || !windowInfo.isVisible) {
                        continue;
                    }
                    aboveWindowInfos.add(windowInfo);
                }

                if (targetWindowInfo == null) {
                    // The window isn't present. If we have an active timer, we need to cancel it
                    // as it's possible the window was previously present and has since disappeared.
                    resetState();
                    return;
                }

                for (var windowInfo : aboveWindowInfos) {
                    if (targetWindowInfo.displayId == windowInfo.displayId
                            && Rect.intersects(targetWindowInfo.bounds, windowInfo.bounds)) {
                        // The window is occluded. If we have an active timer, we need to cancel it
                        // as it's possible the window was previously not occluded and now is
                        // occluded.
                        resetState();
                        return;
                    }
                }

                if (targetWindowInfo.bounds.equals(mPreviousBounds)) {
                    // The window matches previously found bounds. Let the active timer continue.
                    return;
                }

                // The window is present and not occluded but has different bounds than
                // previously seen or this is the first time we've detected the window. If
                // there's an active timer, cancel it. Schedule a task to toggle the latch in 200ms.
                resetState();
                mPreviousBounds.set(targetWindowInfo.bounds);
                mTask = new TimerTask() {
                    @Override
                    public void run() {
                        satisfied.set(true);
                        latch.countDown();
                    }
                };
                mTimer.schedule(mTask, 200 * HW_TIMEOUT_MULTIPLIER);
            }
        };

        runWithSurfaceFlingerPermission(() -> {
            var listener = new WindowInfosListenerForTest();
            try {
                listener.addWindowInfosListener(windowNotOccluded);
                latch.await(timeout, unit);
            } finally {
                listener.removeWindowInfosListener(windowNotOccluded);
            }
        });

        return satisfied.get();
    }

    private interface InterruptableRunnable {
        void run() throws InterruptedException;
    };

    private static void runWithSurfaceFlingerPermission(@NonNull InterruptableRunnable runnable)
            throws InterruptedException {
        Set<String> shellPermissions =
                InstrumentationRegistry.getInstrumentation().getUiAutomation()
                        .getAdoptedShellPermissions();
        if (shellPermissions.isEmpty()) {
            SystemUtil.runWithShellPermissionIdentity(runnable::run,
                    Manifest.permission.ACCESS_SURFACE_FLINGER);
        } else if (shellPermissions.contains(Manifest.permission.ACCESS_SURFACE_FLINGER)) {
            runnable.run();
        } else {
            throw new IllegalStateException(
                    "waitForWindowOnTop called with adopted shell permissions that don't include "
                            + "ACCESS_SURFACE_FLINGER");
        }
    }

    /**
     * Waits until the window specified by windowTokenSupplier is present, not occluded, and hasn't
     * had geometry changes for 200ms.
     *
     * The window is considered occluded if any part of another window is above it, excluding
     * trusted overlays.
     *
     * <p>
     * <strong>Note:</strong>If the caller has any adopted shell permissions, they must include
     * android.permission.ACCESS_SURFACE_FLINGER.
     * </p>
     *
     * @param timeout             The amount of time to wait for the window to be visible.
     * @param unit                The units associated with timeout.
     * @param windowTokenSupplier Supplies the window token for the window to wait on. The
     *                            supplier is called each time window infos change. If the
     *                            supplier returns null, the window is assumed not visible
     *                            yet.
     * @return True if the window satisfies the visibility requirements before the timeout is
     * reached. False otherwise.
     */
    public static boolean waitForWindowOnTop(long timeout, @NonNull TimeUnit unit,
            @NonNull Supplier<IBinder> windowTokenSupplier)
            throws InterruptedException {
        return waitForWindowOnTop(timeout, unit, windowInfo -> {
            IBinder windowToken = windowTokenSupplier.get();
            return windowToken != null && windowInfo.windowToken == windowToken;
        });
    }

    /**
     * Waits until the set of windows and their geometries are unchanged for 200ms.
     *
     * <p>
     * <strong>Note:</strong>If the caller has any adopted shell permissions, they must include
     * android.permission.ACCESS_SURFACE_FLINGER.
     * </p>
     *
     * @param timeout The amount of time to wait for the window to be visible.
     * @param unit    The units associated with timeout.
     * @return True if window geometry becomes stable before the timeout is reached. False
     * otherwise.
     */
    public static boolean waitForStableWindowGeometry(long timeout, @NonNull TimeUnit unit)
            throws InterruptedException {
        var latch = new CountDownLatch(1);
        var satisfied = new AtomicBoolean();

        var timer = new Timer();
        TimerTask[] task = {null};

        var previousBounds = new HashMap<IBinder, Rect>();
        var currentBounds = new HashMap<IBinder, Rect>();

        Consumer<List<WindowInfo>> consumer = windowInfos -> {
            if (satisfied.get()) {
                return;
            }

            currentBounds.clear();
            for (var windowInfo : windowInfos) {
                currentBounds.put(windowInfo.windowToken, windowInfo.bounds);
            }

            if (currentBounds.equals(previousBounds)) {
                // No changes detected. Let the previously scheduled timer task continue.
                return;
            }

            previousBounds.clear();
            previousBounds.putAll(currentBounds);

            // Something has changed. Cancel the previous timer task and schedule a new task
            // to countdown the latch in 200ms.
            if (task[0] != null) {
                task[0].cancel();
            }
            task[0] = new TimerTask() {
                @Override
                public void run() {
                    satisfied.set(true);
                    latch.countDown();
                }
            };
            timer.schedule(task[0], 200 * HW_TIMEOUT_MULTIPLIER);
        };

        runWithSurfaceFlingerPermission(() -> {
            var listener = new WindowInfosListenerForTest();
            try {
                listener.addWindowInfosListener(consumer);
                latch.await(timeout, unit);
            } finally {
                listener.removeWindowInfosListener(consumer);
            }
        });

        return satisfied.get();
    }

    /**
     * Tap on the center coordinates of the specified window and sends back the coordinates tapped
     * </p>
     *
     * @param instrumentation     Instrumentation object to use for tap.
     * @param windowTokenSupplier Supplies the window token for the window to wait on. The supplier
     *                            is called each time window infos change. If the supplier returns
     *                            null, the window is assumed not visible yet.
     * @param useGlobalInjection  Whether to use targeted injection (false) or global (true).
     *                            Targeted injection will only send injected events to the owned
     *                            test app, while global will send the events to the entire system,
     *                            including spy windows (launcher/System UI). Always use targeted
     *                            injection unless you know what you are doing.
     * @param outCoords           If non null, the tapped coordinates will be set in the object.
     * @return true if successfully tapped on the coordinates, false otherwise.
     * @throws InterruptedException if failed to wait for WindowInfo
     */
    public static boolean tapOnWindowCenter(Instrumentation instrumentation,
            @NonNull Supplier<IBinder> windowTokenSupplier, boolean useGlobalInjection,
            @Nullable Point outCoords)
            throws InterruptedException {
        Rect bounds = getWindowBoundsInDisplaySpace(windowTokenSupplier);
        if (bounds == null) {
            return false;
        }

        final Point coord = new Point(bounds.left + bounds.width() / 2,
                bounds.top + bounds.height() / 2);
        sendTap(instrumentation, coord, useGlobalInjection);
        if (outCoords != null) {
            outCoords.set(coord.x, coord.y);
        }
        return true;
    }

    /**
     * Tap on the coordinates of the specified window, offset by the value passed in.
     * </p>
     *
     * @param instrumentation     Instrumentation object to use for tap.
     * @param windowTokenSupplier Supplies the window token for the window to wait on. The supplier
     *                            is called each time window infos change. If the supplier returns
     *                            null, the window is assumed not visible yet.
     * @param offset              The offset from 0,0 of the window to tap on. If null, it will be
     *                            ignored and 0,0 will be tapped.
     * @param useGlobalInjection  Whether to use targeted injection (false) or global (true).
     *                            Targeted injection will only send injected events to the owned
     *                            test app, while global will send the events to the entire system,
     *                            including spy windows (launcher/System UI). Always use targeted
     *                            injection unless you know what you are doing.
     * @return true if successfully tapped on the coordinates, false otherwise.
     * @throws InterruptedException if failed to wait for WindowInfo
     */
    public static boolean tapOnWindow(Instrumentation instrumentation,
            @NonNull Supplier<IBinder> windowTokenSupplier, @Nullable Point offset,
            boolean useGlobalInjection)
            throws InterruptedException {
        Rect bounds = getWindowBoundsInDisplaySpace(windowTokenSupplier);
        if (bounds == null) {
            return false;
        }

        final Point coord = new Point(bounds.left + (offset != null ? offset.x : 0),
                bounds.top + (offset != null ? offset.y : 0));
        sendTap(instrumentation, coord, useGlobalInjection);
        return true;
    }

    public static Rect getWindowBoundsInWindowSpace(@NonNull Supplier<IBinder> windowTokenSupplier)
            throws InterruptedException {
        Rect bounds = new Rect();
        Predicate<WindowInfo> predicate = windowInfo -> {
            if (!windowInfo.bounds.isEmpty()) {
                if (!windowInfo.transform.isIdentity()) {
                    RectF rectF = new RectF(windowInfo.bounds);
                    windowInfo.transform.mapRect(rectF);
                    bounds.set((int) rectF.left, (int) rectF.top, (int) rectF.right,
                            (int) rectF.bottom);
                } else {
                    bounds.set(windowInfo.bounds);
                }
                return true;
            }

            return false;
        };

        if (!waitForWindowInfo(predicate, 5L * HW_TIMEOUT_MULTIPLIER, TimeUnit.SECONDS,
                windowTokenSupplier, DEFAULT_DISPLAY)) {
            return null;
        }
        return bounds;
    }

    public static Rect getWindowBoundsInDisplaySpace(@NonNull Supplier<IBinder> windowTokenSupplier)
            throws InterruptedException {
        Rect bounds = new Rect();
        Predicate<WindowInfo> predicate = windowInfo -> {
            if (!windowInfo.bounds.isEmpty()) {
                bounds.set(windowInfo.bounds);
                return true;
            }

            return false;
        };

        if (!waitForWindowInfo(predicate, 5L * HW_TIMEOUT_MULTIPLIER, TimeUnit.SECONDS,
                windowTokenSupplier, DEFAULT_DISPLAY)) {
            return null;
        }
        return bounds;
    }

    /**
     * Sends tap to the specified coordinates.
     * </p>
     *
     * @param instrumentation    Instrumentation object to use for tap.
     * @param coord              The coordinates to tap on in display space.
     * @param useGlobalInjection Whether to use targeted injection (false) or global (true).
     *                           Targeted injection will only send injected events to the owned
     *                           test app, while global will send the events to the entire system,
     *                           including spy windows (launcher/System UI). Always use targeted
     *                           injection unless you know what you are doing.
     * @throws InterruptedException if failed to wait for WindowInfo
     */
    public static void sendTap(Instrumentation instrumentation, Point coord,
            boolean useGlobalInjection) {
        // Get anchor coordinates on the screen
        final long downTime = SystemClock.uptimeMillis();

        CtsTouchUtils ctsTouchUtils = new CtsTouchUtils(instrumentation.getTargetContext());
        ctsTouchUtils.injectDownEvent(instrumentation, downTime, coord.x, coord.y,
                /* eventInjectionListener= */ null, useGlobalInjection);
        ctsTouchUtils.injectUpEvent(instrumentation, downTime, false, coord.x, coord.y,
                /*waitForAnimations=*/ true, null, useGlobalInjection);

        instrumentation.waitForIdleSync();
    }

    public static boolean waitForWindowFocus(final View view, boolean hasWindowFocus) {
        final CountDownLatch latch = new CountDownLatch(1);

        view.getHandler().post(() -> {
            if (view.hasWindowFocus() == hasWindowFocus) {
                latch.countDown();
                return;
            }
            view.getViewTreeObserver().addOnWindowFocusChangeListener(
                    new ViewTreeObserver.OnWindowFocusChangeListener() {
                        @Override
                        public void onWindowFocusChanged(boolean newFocusState) {
                            if (hasWindowFocus == newFocusState) {
                                view.getViewTreeObserver()
                                        .removeOnWindowFocusChangeListener(this);
                                latch.countDown();
                            }
                        }
                    });

            view.invalidate();
        });

        try {
            if (!latch.await(HW_TIMEOUT_MULTIPLIER * 10L, TimeUnit.SECONDS)) {
                return false;
            }
        } catch (InterruptedException e) {
            return false;
        }
        return true;
    }

    public static void dumpWindowsOnScreen(String tag, String message)
            throws InterruptedException {
        waitForWindowInfos(windowInfos -> {
            if (windowInfos.isEmpty()) {
                return false;
            }
            Log.d(tag, "Dumping windows on screen: " + message);
            for (var windowInfo : windowInfos) {
                Log.d(tag, "     " + windowInfo);
            }
            return true;
        }, 5L * HW_TIMEOUT_MULTIPLIER, TimeUnit.SECONDS);
    }

    /**
     * Assert the condition and dump the window states if the condition fails.
     */
    public static void assertAndDumpWindowState(String tag, String message, boolean condition)
            throws InterruptedException {
        if (!condition) {
            dumpWindowsOnScreen(tag, message);
        }

        assertTrue(message, condition);
    }
}

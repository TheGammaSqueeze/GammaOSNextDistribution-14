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

package android.app.cts.wallpapers;

import static android.app.WallpaperManager.FLAG_LOCK;
import static android.app.WallpaperManager.FLAG_SYSTEM;

import static com.google.common.truth.Truth.assertThat;

import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.compatibility.common.util.ThrowingRunnable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class WallpaperManagerTestUtils {

    private static final String TAG = "WallpaperManagerTest";

    public static final ComponentName TEST_LIVE_WALLPAPER_COMPONENT = new ComponentName(
            TestLiveWallpaper.class.getPackageName(),
            TestLiveWallpaper.class.getName());

    public static final ComponentName TEST_LIVE_WALLPAPER_NO_UNFOLD_COMPONENT = new ComponentName(
            TestLiveWallpaperNoUnfoldTransition.class.getPackageName(),
            TestLiveWallpaperNoUnfoldTransition.class.getName());

    public static final ComponentName TEST_LIVE_WALLPAPER_AMBIENT_COMPONENT = new ComponentName(
            TestLiveWallpaperSupportingAmbientMode.class.getPackageName(),
            TestLiveWallpaperSupportingAmbientMode.class.getName());

    /**
     * Runs the given runnable and waits until request number of engine events are triggered.
     *
     * @param timeout              Amount of time to wait.
     * @param unit                 Unit for the timeout.
     * @param onCreateCount        Number of times Engine::onCreate is expected to be called.
     * @param onDestroyCount       Number of times Engine::onDestroy is expected to be called.
     * @param surfaceCreationCount Number of times Engine::onSurfaceChanged is expected to be
     *                             called.
     * @param runnable             The action to perform that will trigger engine events.
     * @return True if all events were received, false if there was a timeout waiting for any of
     * the events.
     */
    public static boolean runAndAwaitChanges(long timeout, TimeUnit unit,
            int onCreateCount, int onDestroyCount, int surfaceCreationCount,
            ThrowingRunnable runnable) {
        TestWallpaperService.EngineCallbackCountdown callback =
                new TestWallpaperService.EngineCallbackCountdown(onCreateCount, onDestroyCount,
                        surfaceCreationCount);
        TestWallpaperService.Companion.addCallback(callback);
        boolean result;
        try {
            runnable.run();
            result = callback.awaitEvents(timeout, unit);
        } catch (Exception e) {
            throw new RuntimeException("Caught exception", e);
        } finally {
            TestWallpaperService.Companion.removeCallback(callback);
        }

        return result;
    }

    /**
     * Runs the given runnable and waits until request number of color change events are triggered.
     * @param timeout          Amount of time to wait.
     * @param unit             Unit for the timeout.
     * @param whichColors      Flags for which we expect a color change callback.
     * @param wallpaperManager Instance of WallpaperManager that will register the color listener.
     * @param handler          Handler that will receive the color callbacks.
     * @param runnable         The action to perform that will trigger engine events.
     * @return True if all events were received, false if there was a timeout waiting for any of
     * the events.
     *
     * @see #runAndAwaitChanges(long, TimeUnit, int, int, int, ThrowingRunnable)
     */
    public static boolean runAndAwaitColorChanges(long timeout, TimeUnit unit,
            int whichColors, WallpaperManager wallpaperManager, Handler handler,
            ThrowingRunnable runnable) {
        assertThat(whichColors).isIn(List.of(FLAG_LOCK, FLAG_SYSTEM, FLAG_LOCK | FLAG_SYSTEM));
        ColorChangeWaiter callback = new ColorChangeWaiter(whichColors);
        wallpaperManager.addOnColorsChangedListener(callback, handler);
        boolean result;
        try {
            runnable.run();
            result = callback.waitForChanges(timeout, unit);
        } catch (Exception e) {
            throw new RuntimeException("Caught exception", e);
        } finally {
            wallpaperManager.removeOnColorsChangedListener(callback);
        }
        return result;
    }

    /**
     * enumeration of all wallpapers used for test purposes: 3 static, 3 live wallpapers:   <br>
     * static1 <=> red bitmap <br>
     * static2 <=> green bitmap <br>
     * static3 <=> blue bitmap <br>
     * <br>
     * live1 <=> TestLiveWallpaper (cyan) <br>
     * live2 <=> TestLiveWallpaperNoUnfoldTransition (magenta) <br>
     * live3 <=> TestLiveWallpaperSupportingAmbientMode (yellow) <br>
     */
    public enum TestWallpaper {
        STATIC1(R.drawable.icon_red, null),
        STATIC2(R.drawable.icon_green, null),
        STATIC3(R.drawable.icon_blue, null),
        LIVE1(null, TEST_LIVE_WALLPAPER_COMPONENT),
        LIVE2(null, TEST_LIVE_WALLPAPER_NO_UNFOLD_COMPONENT),
        LIVE3(null, TEST_LIVE_WALLPAPER_AMBIENT_COMPONENT);

        private final Integer mBitmapResourceId;
        private final ComponentName mComponentName;

        TestWallpaper(Integer bitmapResourceId, ComponentName componentName) {
            mBitmapResourceId = bitmapResourceId;
            mComponentName = componentName;
        }

        int getBitmapResourceId() {
            return mBitmapResourceId;
        }

        ComponentName getComponentName() {
            return mComponentName;
        }

        private String type() {
            return isStatic() ? "static" : "live";
        }

        private boolean isStatic() {
            return mComponentName == null;
        }

        private boolean isLive() {
            return !isStatic();
        }
    }

    private static List<TestWallpaper> allStaticTestWallpapers() {
        return List.of(TestWallpaper.STATIC1, TestWallpaper.STATIC2, TestWallpaper.STATIC3);
    }

    private static List<TestWallpaper> allLiveTestWallpapers() {
        return List.of(TestWallpaper.LIVE1, TestWallpaper.LIVE2, TestWallpaper.LIVE3);
    }

    public static class WallpaperChange {
        final TestWallpaper mWallpaper;
        int mDestination;
        public WallpaperChange(
                TestWallpaper wallpaper, int destination) {
            this.mWallpaper = wallpaper;
            this.mDestination = destination;
        }
    }

    /**
     * Class representing a state in which our WallpaperManager may be during our tests.
     * A state is fully represented by the wallpaper that are present on home and lock screen.
     */
    public enum WallpaperState {
        LIVE_SAME_SINGLE(TestWallpaper.LIVE1, TestWallpaper.LIVE1, true),
        LIVE_SAME_MULTI(TestWallpaper.LIVE1, TestWallpaper.LIVE1, false),
        LIVE_DIFF_MULTI(TestWallpaper.LIVE1, TestWallpaper.LIVE2, false),
        LIVE_STATIC_MULTI(TestWallpaper.LIVE1, TestWallpaper.STATIC1, false),
        STATIC_SAME_SINGLE(TestWallpaper.STATIC1, TestWallpaper.STATIC1, true),
        STATIC_SAME_MULTI(TestWallpaper.STATIC1, TestWallpaper.STATIC1, false),
        STATIC_DIFF_MULTI(TestWallpaper.STATIC1, TestWallpaper.STATIC2, false),
        STATIC_LIVE_MULTI(TestWallpaper.STATIC1, TestWallpaper.LIVE1, false);

        private final TestWallpaper mHomeWallpaper;
        private final TestWallpaper mLockWallpaper;

        /**
         * it is possible to have two copies of the same engine on home + lock screen,
         * in which this flag would be false.
         * True means that mHomeWallpaper == mLockWallpaper and there is only one active engine.
         */
        private final boolean mSingleEngine;

        WallpaperState(
                TestWallpaper homeWallpaper, TestWallpaper lockWallpaper, boolean singleEngine) {
            mHomeWallpaper = homeWallpaper;
            mLockWallpaper = lockWallpaper;
            assertThat(!singleEngine || (homeWallpaper == lockWallpaper)).isTrue();
            assertThat(homeWallpaper).isNotNull();
            assertThat(lockWallpaper).isNotNull();
            mSingleEngine = singleEngine;
        }

        private TestWallpaper pickUnused(List<TestWallpaper> choices) {
            return choices.stream()
                    .filter(wallpaper -> wallpaper != mHomeWallpaper && wallpaper != mLockWallpaper)
                    .findFirst().orElseThrow();
        }

        private TestWallpaper pickUnusedStatic() {
            return pickUnused(allStaticTestWallpapers());
        }

        private TestWallpaper pickUnusedLive() {
            return pickUnused(allLiveTestWallpapers());
        }

        /**
         * Enumerate all the possible logically different {@link WallpaperChange} changes from
         * this state. <br>
         * Two changes are considered logically different if their destination is different,
         * or if their wallpaper type (static or live) is different.
         */
        public List<WallpaperChange> allPossibleChanges() {
            TestWallpaper unusedStatic = pickUnusedStatic();
            TestWallpaper unusedLive = pickUnusedLive();

            // one can always add a new wallpaper, either static or live, at any destination
            List<WallpaperChange> result = new ArrayList<>(Stream.of(unusedStatic, unusedLive)
                    .flatMap(newWallpaper -> Stream
                            .of(FLAG_LOCK, FLAG_SYSTEM, FLAG_LOCK | FLAG_SYSTEM)
                            .map(destination -> new WallpaperChange(newWallpaper, destination)))
                    .toList());

            // if we have a lock & home single engine, we can separate it
            if (mSingleEngine) {
                result.addAll(List.of(
                        new WallpaperChange(mHomeWallpaper, FLAG_SYSTEM),
                        new WallpaperChange(mHomeWallpaper, FLAG_LOCK)
                ));

                // else if we have the same engine twice, we can merge it
            } else if (mHomeWallpaper == mLockWallpaper) {
                result.add(new WallpaperChange(mHomeWallpaper, FLAG_SYSTEM | FLAG_LOCK));
            }

            // if we have different engines on home / lock,
            // we can set one of them at the other location or at both locations
            if (mHomeWallpaper != mLockWallpaper) {
                result.addAll(List.of(
                        new WallpaperChange(mHomeWallpaper, FLAG_LOCK | FLAG_SYSTEM),
                        new WallpaperChange(mLockWallpaper, FLAG_LOCK | FLAG_SYSTEM),
                        new WallpaperChange(mHomeWallpaper, FLAG_LOCK),
                        new WallpaperChange(mLockWallpaper, FLAG_SYSTEM)
                ));
            }
            return result;
        }

        /**
         * Given a change, return the number of times we expect an engine.onCreate operation
         * of a live wallpaper from this state
         */
        public int expectedNumberOfLiveWallpaperCreate(WallpaperChange change) {

            if (change.mWallpaper.isStatic()) return 0;
            switch (change.mDestination) {
                case FLAG_SYSTEM | FLAG_LOCK:
                    return change.mWallpaper != mHomeWallpaper ? 1 : 0;
                case FLAG_SYSTEM:
                    return mSingleEngine || (change.mWallpaper != mHomeWallpaper) ? 1 : 0;
                case FLAG_LOCK:
                    return mSingleEngine || (change.mWallpaper != mLockWallpaper) ? 1 : 0;
                default:
                    throw new IllegalArgumentException();
            }
        }


        /**
         * Given a change, return the number of times we expect an engine.onDestroy operation
         * of a live wallpaper from this state
         */
        public int expectedNumberOfLiveWallpaperDestroy(WallpaperChange change) {

            if (mSingleEngine) {
                return mHomeWallpaper.isLive()
                        && mHomeWallpaper != change.mWallpaper
                        && change.mDestination == (FLAG_LOCK | FLAG_SYSTEM) ? 1 : 0;
            }

            boolean changeSystem = (change.mDestination & FLAG_SYSTEM) != 0;
            boolean changeLock = (change.mDestination & FLAG_LOCK) != 0;
            boolean systemReplaced = changeSystem && change.mWallpaper != mHomeWallpaper;
            boolean lockReplaced =
                    changeLock && (change.mWallpaper != mLockWallpaper || changeSystem);

            int result = 0;
            if (systemReplaced && mHomeWallpaper.isLive()) result += 1;
            if (lockReplaced && mLockWallpaper.isLive()) result += 1;
            return result;
        }

        /**
         * Describes how to reproduce a failure obtained from this state with the given change
         */
        public String reproduceDescription(WallpaperChange change) {
            return String.format("To reproduce, start with:\n%s\nand %s",
                    description(), changeDescription(change));
        }

        private String description() {
            String homeType = mHomeWallpaper.type();
            String lockType = mLockWallpaper.type();
            return mLockWallpaper == mHomeWallpaper
                    ? String.format(" - the same %s wallpaper on home & lock screen (%s)", homeType,
                    mSingleEngine ? "sharing the same engine" : "each using its own engine")
                    : String.format(" - a %s wallpaper on home screen\n"
                                    + " - %s %s wallpaper on lock screen",
                            homeType, homeType.equals(lockType) ? "another" : "a", lockType);
        }

        private String changeDescription(WallpaperChange change) {
            String newWallpaperDescription =
                    change.mWallpaper == mHomeWallpaper || change.mWallpaper == mLockWallpaper
                            ? String.format("the same %s wallpaper as %s screen",
                            change.mWallpaper.type(),
                            change.mWallpaper == mHomeWallpaper ? "home" : "lock")
                            : String.format("a different %s wallpaper", change.mWallpaper.type());

            String destinationDescription =
                    change.mDestination == FLAG_SYSTEM ? "home screen only"
                            : change.mDestination == FLAG_LOCK ? "lock screen only"
                                    : "both home & lock screens";

            String methodUsed = change.mWallpaper.isLive()
                    ? "setWallpaperComponentWithFlags" : "setResource";

            String flagDescription =
                    change.mDestination == FLAG_SYSTEM ? "FLAG_SYSTEM"
                            : change.mDestination == FLAG_LOCK ? "FLAG_LOCK"
                                    : "FLAG_SYSTEM|FLAG_LOCK";

            return String.format("apply %s on %s (via WallpaperManager#%s(..., %s))",
                    newWallpaperDescription, destinationDescription, methodUsed, flagDescription);
        }
    }

    /**
     * Uses the provided wallpaperManager instance to perform a {@link WallpaperChange}.
     */
    public static void performChange(
            WallpaperManager wallpaperManager, WallpaperChange change)
            throws IOException {
        if (change.mWallpaper.isStatic()) {
            wallpaperManager.setResource(
                    change.mWallpaper.getBitmapResourceId(), change.mDestination);
        } else {
            // Up to one surface is expected to be created when switching wallpapers. It's possible
            // that this operation ends up being a no-op, in that case the wait will time out.
            final int expectedSurfaceCreations = 1;
            runAndAwaitChanges(500, TimeUnit.MILLISECONDS, 0, 0, expectedSurfaceCreations,
                    () -> {
                        wallpaperManager.setWallpaperComponentWithFlags(
                                change.mWallpaper.getComponentName(), change.mDestination);
                    });
        }
    }

    /**
     * Sets a wallpaperManager in some state. Always proceeds the same way: <br>
     *   - put the home wallpaper on lock and home screens <br>
     *   - put the lock wallpaper on lock screen, if it is different from the home screen wallpaper
     */
    public static void goToState(
            WallpaperManager wallpaperManager, WallpaperState state)
            throws IOException {
        WallpaperChange change1 = new WallpaperChange(
                state.mHomeWallpaper, FLAG_SYSTEM | (state.mSingleEngine ? FLAG_LOCK : 0));
        performChange(wallpaperManager, change1);

        WallpaperChange change2 = new WallpaperChange(state.mLockWallpaper, FLAG_LOCK);
        if (!state.mSingleEngine) performChange(wallpaperManager, change2);
    }

    static class ColorChangeWaiter implements WallpaperManager.OnColorsChangedListener {
        private CountDownLatch mHomeCountDownLatch;
        private CountDownLatch mLockCountDownLatch;
        ColorChangeWaiter(int which) {
            int expectedHomeEvents = ((which & FLAG_SYSTEM) != 0) ? 1 : 0;
            int expectedLockEvents = ((which & FLAG_LOCK) != 0) ? 1 : 0;
            mHomeCountDownLatch = new CountDownLatch(expectedHomeEvents);
            mLockCountDownLatch = new CountDownLatch(expectedLockEvents);
        }

        @Override
        public void onColorsChanged(@Nullable WallpaperColors colors, int which) {
            if ((which & FLAG_SYSTEM) != 0) {
                mHomeCountDownLatch.countDown();
            }
            if ((which & FLAG_LOCK) != 0) {
                mLockCountDownLatch.countDown();
            }
            Log.d(TAG, "color state count down: " + which + " - " + colors);
        }

        public boolean waitForChanges(long timeout, TimeUnit unit) {
            try {
                return mHomeCountDownLatch.await(timeout, unit)
                        && mLockCountDownLatch.await(timeout, unit);
            } catch (InterruptedException e) {
                throw new RuntimeException("Wallpaper colors wait interrupted");
            }
        }
    }
}

/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.wallpaper.picker;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.pressBack;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.widget.TextView;

import androidx.test.espresso.intent.Intents;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.wallpaper.R;
import com.android.wallpaper.model.WallpaperInfo;
import com.android.wallpaper.module.InjectorProvider;
import com.android.wallpaper.module.WallpaperChangedNotifier;
import com.android.wallpaper.module.WallpaperPersister;
import com.android.wallpaper.module.logging.TestUserEventLogger;
import com.android.wallpaper.testing.TestAsset;
import com.android.wallpaper.testing.TestExploreIntentChecker;
import com.android.wallpaper.testing.TestInjector;
import com.android.wallpaper.testing.TestLiveWallpaperInfo;
import com.android.wallpaper.testing.TestStaticWallpaperInfo;
import com.android.wallpaper.testing.TestWallpaperPersister;
import com.android.wallpaper.testing.TestWallpaperStatusChecker;
import com.android.wallpaper.util.ScreenSizeCalculator;
import com.android.wallpaper.util.WallpaperCropUtils;

import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

/**
 * Tests for {@link PreviewActivity}.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
@MediumTest
public class PreviewActivityTest {
    private static final float FLOAT_ERROR_MARGIN = 0.001f;
    private static final String ACTION_URL = "http://google.com";

    private TestStaticWallpaperInfo mTestStaticWallpaper;
    private TestLiveWallpaperInfo mTestLiveWallpaper;
    private TestWallpaperPersister mWallpaperPersister;
    private TestUserEventLogger mEventLogger;
    private TestExploreIntentChecker mExploreIntentChecker;
    private TestWallpaperStatusChecker mWallpaperStatusChecker;

    private final HiltAndroidRule mHiltRule = new HiltAndroidRule(this);
    private final ActivityTestRule<PreviewActivity> mActivityRule =
            new ActivityTestRule<>(PreviewActivity.class, false, false);
    @Rule
    public RuleChain rules = RuleChain.outerRule(mHiltRule).around(mActivityRule);

    @Inject TestInjector mInjector;

    @Before
    public void setUp() {
        mHiltRule.inject();
        InjectorProvider.setInjector(mInjector);

        Intents.init();

        List<String> attributions = new ArrayList<>();
        attributions.add("Title");
        attributions.add("Subtitle 1");
        attributions.add("Subtitle 2");
        mTestStaticWallpaper = new TestStaticWallpaperInfo(TestStaticWallpaperInfo.COLOR_DEFAULT);
        mTestStaticWallpaper.setAttributions(attributions);
        mTestStaticWallpaper.setCollectionId("collectionStatic");
        mTestStaticWallpaper.setWallpaperId("wallpaperStatic");
        mTestStaticWallpaper.setActionUrl(ACTION_URL);

        mTestLiveWallpaper = new TestLiveWallpaperInfo(TestStaticWallpaperInfo.COLOR_DEFAULT);
        mTestLiveWallpaper.setAttributions(attributions);
        mTestLiveWallpaper.setCollectionId("collectionLive");
        mTestLiveWallpaper.setWallpaperId("wallpaperLive");
        mTestLiveWallpaper.setActionUrl(ACTION_URL);

        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mWallpaperPersister = (TestWallpaperPersister) mInjector.getWallpaperPersister(context);
        mEventLogger = (TestUserEventLogger) mInjector.getUserEventLogger();
        mExploreIntentChecker = (TestExploreIntentChecker)
                mInjector.getExploreIntentChecker(context);
        mWallpaperStatusChecker = (TestWallpaperStatusChecker)
                mInjector.getWallpaperStatusChecker(context);
    }

    @After
    public void tearDown() {
        Intents.release();
        mActivityRule.finishActivity();
    }

    private void launchActivityIntentWithWallpaper(WallpaperInfo wallpaperInfo) {
        Intent intent = PreviewActivity.newIntent(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                wallpaperInfo, /* viewAsHome= */ false, /* isAssetIdPresent= */ true);

        mActivityRule.launchActivity(intent);
    }

    private void finishSettingWallpaperThenDo(Runnable runnable) {
        final WallpaperChangedNotifier wallpaperChangedNotifier =
                WallpaperChangedNotifier.getInstance();
        WallpaperChangedNotifier.Listener listener = new WallpaperChangedNotifier.Listener() {
            @Override
            public void onWallpaperChanged() {
                wallpaperChangedNotifier.unregisterListener(this);
                runnable.run();
            }
        };
        wallpaperChangedNotifier.registerListener(listener);

        try {
            mActivityRule.runOnUiThread(() -> mWallpaperPersister.finishSettingWallpaper());
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    private SubsamplingScaleImageView getFullResImageView(PreviewActivity activity) {
        PreviewFragment fragment =
                (PreviewFragment) activity.getSupportFragmentManager().findFragmentById(
                        R.id.fragment_container);
        if (fragment instanceof ImagePreviewFragment) {
            return ((ImagePreviewFragment) fragment).getFullResImageView();
        } else {
            return null;
        }
    }

    @Test
    public void testRendersWallpaperDrawableFromIntent() {
        launchActivityIntentWithWallpaper(mTestStaticWallpaper);
        assertTrue(getFullResImageView(mActivityRule.getActivity()).hasImage());
    }

    @Test
    public void testClickSetWallpaper_Success_HomeScreen() {
        launchActivityIntentWithWallpaper(mTestStaticWallpaper);
        assertNull(mWallpaperPersister.getCurrentHomeWallpaper());

        onView(withId(R.id.button_set_wallpaper)).perform(click());

        // Destination dialog is shown; click "Home screen".
        onView(withText(R.string.set_wallpaper_home_screen_destination)).perform(click());

        assertNull(mWallpaperPersister.getCurrentHomeWallpaper());

        finishSettingWallpaperThenDo(() -> {
            // Mock system wallpaper bitmap should be equal to the mock WallpaperInfo's bitmap.
            Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
            Bitmap srcBitmap = ((TestAsset) mTestStaticWallpaper.getAsset(context)).getBitmap();
            assertTrue(srcBitmap.sameAs(mWallpaperPersister.getCurrentHomeWallpaper()));

            // The wallpaper should have been set on the home screen.
            assertEquals(WallpaperPersister.DEST_HOME_SCREEN,
                    mWallpaperPersister.getLastDestination());
            assertEquals(1, mEventLogger.getNumWallpaperSetEvents());

            assertEquals(1, mEventLogger.getNumWallpaperSetResultEvents());
        });
    }

    @Test
    public void testClickSetWallpaper_Success_LockScreen() {
        launchActivityIntentWithWallpaper(mTestStaticWallpaper);
        assertNull(mWallpaperPersister.getCurrentLockWallpaper());

        onView(withId(R.id.button_set_wallpaper)).perform(click());

        // Destination dialog is shown; click "Lock screen."
        onView(withText(R.string.set_wallpaper_lock_screen_destination)).perform(click());

        assertNull(mWallpaperPersister.getCurrentLockWallpaper());

        finishSettingWallpaperThenDo(() -> {
            // Mock system wallpaper bitmap should be equal to the mock WallpaperInfo's bitmap.
            Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
            Bitmap srcBitmap = ((TestAsset) mTestStaticWallpaper.getAsset(context)).getBitmap();
            assertTrue(srcBitmap.sameAs(mWallpaperPersister.getCurrentLockWallpaper()));

            // The wallpaper should have been set on the lock screen.
            assertEquals(WallpaperPersister.DEST_LOCK_SCREEN,
                    mWallpaperPersister.getLastDestination());
            assertEquals(1, mEventLogger.getNumWallpaperSetEvents());

            assertEquals(1, mEventLogger.getNumWallpaperSetResultEvents());
        });
    }

    @Test
    public void testClickSetWallpaper_Success_BothHomeAndLockScreen() {
        launchActivityIntentWithWallpaper(mTestStaticWallpaper);
        assertNull(mWallpaperPersister.getCurrentHomeWallpaper());
        assertNull(mWallpaperPersister.getCurrentLockWallpaper());

        onView(withId(R.id.button_set_wallpaper)).perform(click());

        // Destination dialog is shown; click "Both."
        onView(withText(R.string.set_wallpaper_both_destination)).perform(click());

        assertNull(mWallpaperPersister.getCurrentHomeWallpaper());
        assertNull(mWallpaperPersister.getCurrentLockWallpaper());

        finishSettingWallpaperThenDo(() -> {
            // Mock system wallpaper bitmap should be equal to the mock WallpaperInfo's bitmap.
            Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
            Bitmap srcBitmap = ((TestAsset) mTestStaticWallpaper.getAsset(context)).getBitmap();
            assertTrue(srcBitmap.sameAs(mWallpaperPersister.getCurrentHomeWallpaper()));
            assertTrue(srcBitmap.sameAs(mWallpaperPersister.getCurrentLockWallpaper()));

            // The wallpaper should have been set on both the home and the lock screen.
            assertEquals(WallpaperPersister.DEST_BOTH, mWallpaperPersister.getLastDestination());
            assertEquals(1, mEventLogger.getNumWallpaperSetEvents());

            assertEquals(1, mEventLogger.getNumWallpaperSetResultEvents());
        });
    }

    @Test
    public void testClickSetWallpaper_Fails_HomeScreen_ShowsErrorDialog() {
        launchActivityIntentWithWallpaper(mTestStaticWallpaper);
        assertNull(mWallpaperPersister.getCurrentHomeWallpaper());

        mWallpaperPersister.setFailNextCall(true);

        onView(withId(R.id.button_set_wallpaper)).perform(click());

        // Destination dialog is shown; click "Home screen."
        onView(withText(R.string.set_wallpaper_home_screen_destination)).perform(click());

        finishSettingWallpaperThenDo(() -> {
            assertNull(mWallpaperPersister.getCurrentHomeWallpaper());
            onView(withText(R.string.set_wallpaper_error_message)).check(matches(isDisplayed()));

            assertEquals(1, mEventLogger.getNumWallpaperSetResultEvents());

            // Set next call to succeed and current wallpaper bitmap should not be null and
            // equals to the mock wallpaper bitmap after clicking "try again".
            mWallpaperPersister.setFailNextCall(false);

            onView(withText(R.string.try_again)).perform(click());
            finishSettingWallpaperThenDo(() -> {
                assertNotNull(mWallpaperPersister.getCurrentHomeWallpaper());
                Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
                Bitmap srcBitmap = ((TestAsset) mTestStaticWallpaper.getAsset(context)).getBitmap();
                assertTrue(srcBitmap.sameAs(mWallpaperPersister.getCurrentHomeWallpaper()));

                // The wallpaper should have been set on the home screen.
                assertEquals(WallpaperPersister.DEST_HOME_SCREEN,
                        mWallpaperPersister.getLastDestination());
            });
        });
    }

    @Test
    public void testClickSetWallpaper_Fails_LockScreen_ShowsErrorDialog() {
        launchActivityIntentWithWallpaper(mTestStaticWallpaper);
        assertNull(mWallpaperPersister.getCurrentLockWallpaper());

        mWallpaperPersister.setFailNextCall(true);

        onView(withId(R.id.button_set_wallpaper)).perform(click());

        // Destination dialog is shown; click "Lock screen."
        onView(withText(R.string.set_wallpaper_lock_screen_destination)).perform(click());

        finishSettingWallpaperThenDo(() -> {
            assertNull(mWallpaperPersister.getCurrentLockWallpaper());

            onView(withText(R.string.set_wallpaper_error_message)).check(
                    matches(isDisplayed()));

            assertEquals(1, mEventLogger.getNumWallpaperSetResultEvents());

            // Set next call to succeed and current wallpaper bitmap should not be
            // null and equals to the mock wallpaper bitmap after clicking "try again".
            mWallpaperPersister.setFailNextCall(false);

            onView(withText(R.string.try_again)).perform(click());

            finishSettingWallpaperThenDo(() -> {
                Bitmap newBitmap = mWallpaperPersister.getCurrentLockWallpaper();
                assertNotNull(newBitmap);
                Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
                Bitmap srcBitmap = ((TestAsset) mTestStaticWallpaper.getAsset(context)).getBitmap();
                assertTrue(srcBitmap.sameAs(newBitmap));

                // The wallpaper should have been set on the lock screen.
                assertEquals(WallpaperPersister.DEST_LOCK_SCREEN,
                        mWallpaperPersister.getLastDestination());
            });
        });
    }

    @Test
    public void testClickSetWallpaper_Fails_BothHomeAndLock_ShowsErrorDialog() {
        launchActivityIntentWithWallpaper(mTestStaticWallpaper);
        assertNull(mWallpaperPersister.getCurrentHomeWallpaper());
        assertNull(mWallpaperPersister.getCurrentLockWallpaper());

        mWallpaperPersister.setFailNextCall(true);

        onView(withId(R.id.button_set_wallpaper)).perform(click());

        // Destination dialog is shown; click "Both."
        onView(withText(R.string.set_wallpaper_both_destination)).perform(click());

        finishSettingWallpaperThenDo(() -> {
            assertNull(mWallpaperPersister.getCurrentHomeWallpaper());
            assertNull(mWallpaperPersister.getCurrentLockWallpaper());
            onView(withText(R.string.set_wallpaper_error_message)).check(matches(isDisplayed()));

            assertEquals(1, mEventLogger.getNumWallpaperSetResultEvents());

            // Set next call to succeed and current wallpaper bitmap should not be null and
            // equals to the mock wallpaper bitmap after clicking "try again".
            mWallpaperPersister.setFailNextCall(false);

            onView(withText(R.string.try_again)).perform(click());
            finishSettingWallpaperThenDo(() -> {
                assertNotNull(mWallpaperPersister.getCurrentHomeWallpaper());
                assertNotNull(mWallpaperPersister.getCurrentLockWallpaper());
                Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
                Bitmap srcBitmap = ((TestAsset) mTestStaticWallpaper.getAsset(context)).getBitmap();
                assertTrue(srcBitmap.sameAs(mWallpaperPersister.getCurrentHomeWallpaper()));
                assertTrue(srcBitmap.sameAs(mWallpaperPersister.getCurrentLockWallpaper()));

                // The wallpaper should have been set on both the home screen and the lock screen.
                assertEquals(WallpaperPersister.DEST_BOTH,
                        mWallpaperPersister.getLastDestination());
            });
        });
    }

    @Test
    @Ignore("b/248538709")
    public void testClickSetWallpaper_CropsAndScalesWallpaper() {
        launchActivityIntentWithWallpaper(mTestStaticWallpaper);
        // Scale should not have a meaningful value before clicking "set wallpaper".
        assertTrue(mWallpaperPersister.getScale() < 0);

        onView(withId(R.id.button_set_wallpaper)).perform(click());

        // Destination dialog is shown; click "Home screen".
        onView(withText(R.string.set_wallpaper_home_screen_destination)).perform(click());

        // WallpaperPersister's scale should match the ScaleImageView's scale.
        SubsamplingScaleImageView imageView = getFullResImageView(mActivityRule.getActivity());
        assertNotNull("R.id.full_res_image not found", imageView);

        float zoom = imageView.getScale();
        assertEquals(mWallpaperPersister.getScale(), zoom, FLOAT_ERROR_MARGIN);

        Point screenSize = ScreenSizeCalculator.getInstance().getScreenSize(
                mActivityRule.getActivity().getWindowManager().getDefaultDisplay());
        int maxDim = Math.max(screenSize.x, screenSize.y);
        Rect cropRect = mWallpaperPersister.getCropRect();

        // Crop rect should be greater or equal than screen size in both directions.
        assertTrue(cropRect.width() >= maxDim);
        assertTrue(cropRect.height() >= maxDim);
    }

    @Test
    public void testClickSetWallpaper_FailsCroppingAndScalingWallpaper_ShowsErrorDialog() {
        launchActivityIntentWithWallpaper(mTestStaticWallpaper);
        mWallpaperPersister.setFailNextCall(true);
        onView(withId(R.id.button_set_wallpaper)).perform(click());
        // Destination dialog is shown; click "Home screen".
        onView(withText(R.string.set_wallpaper_home_screen_destination)).perform(click());

        finishSettingWallpaperThenDo(() ->
                onView(withText(R.string.set_wallpaper_error_message)).check(matches(isDisplayed()))
        );
    }

    /**
     * Tests that tapping Set Wallpaper shows the destination dialog (i.e., choosing
     * between Home screen, Lock screen, or Both).
     */
    @Test
    public void testClickSetWallpaper_ShowsDestinationDialog() {
        launchActivityIntentWithWallpaper(mTestStaticWallpaper);
        onView(withId(R.id.button_set_wallpaper)).perform(click());
        onView(withText(R.string.set_wallpaper_dialog_message)).check(matches(isDisplayed()));
    }

    @Test
    public void testDestinationOptions_multiEngine_setLive_showsLockOption() {
        launchActivityIntentWithWallpaper(mTestLiveWallpaper);
        mWallpaperStatusChecker.setHomeStaticWallpaperSet(true);
        mWallpaperStatusChecker.setLockWallpaperSet(false);

        onView(withId(R.id.button_set_wallpaper)).perform(click());

        onView(withText(R.string.set_wallpaper_lock_screen_destination)).inRoot(isDialog())
                .check(matches(isDisplayed()));
    }

    @Test
    @Ignore("b/248538709")
    public void testSetsDefaultWallpaperZoomAndScroll() {
        float expectedWallpaperZoom;
        int expectedWallpaperScrollX;
        int expectedWallpaperScrollY;

        launchActivityIntentWithWallpaper(mTestStaticWallpaper);
        PreviewActivity activity = mActivityRule.getActivity();
        SubsamplingScaleImageView fullResImageView = getFullResImageView(activity);

        Point defaultCropSurfaceSize = WallpaperCropUtils.getDefaultCropSurfaceSize(
                activity.getResources(), activity.getWindowManager().getDefaultDisplay());
        Point screenSize = ScreenSizeCalculator.getInstance().getScreenSize(
                activity.getWindowManager().getDefaultDisplay());
        TestAsset asset = (TestAsset) mTestStaticWallpaper.getAsset(activity);
        Point wallpaperSize = new Point(asset.getBitmap().getWidth(),
                asset.getBitmap().getHeight());

        expectedWallpaperZoom = WallpaperCropUtils.calculateMinZoom(
                wallpaperSize, defaultCropSurfaceSize);

        // Current zoom should match the minimum zoom required to fit wallpaper
        // completely on the crop surface.
        assertEquals(expectedWallpaperZoom, fullResImageView.getScale(), FLOAT_ERROR_MARGIN);

        Point scaledWallpaperSize = new Point(
                (int) (wallpaperSize.x * expectedWallpaperZoom),
                (int) (wallpaperSize.y * expectedWallpaperZoom));
        Point cropSurfaceToScreen = WallpaperCropUtils.calculateCenterPosition(
                defaultCropSurfaceSize, screenSize, true /* alignStart */, false /* isRtl */);
        Point wallpaperToCropSurface = WallpaperCropUtils.calculateCenterPosition(
                scaledWallpaperSize, defaultCropSurfaceSize, false /* alignStart */,
                false /* isRtl */);

        expectedWallpaperScrollX = wallpaperToCropSurface.x + cropSurfaceToScreen.x;
        expectedWallpaperScrollY = wallpaperToCropSurface.y + cropSurfaceToScreen.y;

        // ZoomView should be scrolled in X and Y directions such that the crop surface is centered
        // relative to the wallpaper and the screen is centered (and aligned left) relative to the
        // crop surface.
        assertEquals(expectedWallpaperScrollX, fullResImageView.getScrollX());
        assertEquals(expectedWallpaperScrollY, fullResImageView.getScrollY());
    }

    @Test
    public void testShowSetWallpaperDialog_TemporarilyLocksScreenOrientation() {
        launchActivityIntentWithWallpaper(mTestStaticWallpaper);
        PreviewActivity activity = mActivityRule.getActivity();
        assertNotEquals(ActivityInfo.SCREEN_ORIENTATION_LOCKED, activity.getRequestedOrientation());

        // Show SetWallpaperDialog.
        onView(withId(R.id.button_set_wallpaper)).perform(click());

        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LOCKED, activity.getRequestedOrientation());

        // Press back to dismiss the dialog.
        onView(isRoot()).perform(pressBack());

        assertNotEquals(ActivityInfo.SCREEN_ORIENTATION_LOCKED, activity.getRequestedOrientation());
    }

    @Test
    public void testSetWallpaper_TemporarilyLocksScreenOrientation() {
        launchActivityIntentWithWallpaper(mTestStaticWallpaper);
        PreviewActivity activity = mActivityRule.getActivity();
        assertNotEquals(ActivityInfo.SCREEN_ORIENTATION_LOCKED, activity.getRequestedOrientation());

        // Show SetWallpaperDialog.
        onView(withId(R.id.button_set_wallpaper)).perform(click());

        // Destination dialog is shown; click "Home screen".
        onView(withText(R.string.set_wallpaper_home_screen_destination)).perform(click());

        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LOCKED, activity.getRequestedOrientation());

        // Finish setting the wallpaper to check that the screen orientation is no longer locked.
        finishSettingWallpaperThenDo(() -> assertNotEquals(ActivityInfo.SCREEN_ORIENTATION_LOCKED,
                activity.getRequestedOrientation()));
    }

    @Test
    public void testShowsWallpaperAttribution() {
        launchActivityIntentWithWallpaper(mTestStaticWallpaper);
        PreviewActivity activity = mActivityRule.getActivity();

        TextView titleView = activity.findViewById(R.id.wallpaper_info_title);
        assertEquals("Title", titleView.getText());

        TextView subtitle1View = activity.findViewById(R.id.wallpaper_info_subtitle1);
        assertEquals("Subtitle 1", subtitle1View.getText());

        TextView subtitle2View = activity.findViewById(R.id.wallpaper_info_subtitle2);
        assertEquals("Subtitle 2", subtitle2View.getText());
    }

    /**
     * Tests that if there was a failure decoding the wallpaper bitmap, then the activity shows an
     * informative error dialog with an "OK" button, when clicked finishes the activity.
     */
    @Test
    public void testLoadWallpaper_Failed_ShowsErrorDialog() {
        // Simulate a corrupted asset that fails to perform decoding operations.
        mTestStaticWallpaper.corruptAssets();
        launchActivityIntentWithWallpaper(mTestStaticWallpaper);

        onView(withText(R.string.load_wallpaper_error_message)).inRoot(isDialog()).check(
                matches(isDisplayed()));

        onView(withText(android.R.string.ok)).perform(click());

        assertTrue(mActivityRule.getActivity().isFinishing());
    }

    /**
     * Tests that the explore button is not visible, even if there is an action URL present, if
     * there is no activity on the device which can handle such an explore action.
     */
    @Test
    public void testNoActionViewHandler_ExploreButtonNotVisible() {
        mExploreIntentChecker.setViewHandlerExists(false);

        launchActivityIntentWithWallpaper(mTestStaticWallpaper);
        onView(withId(R.id.wallpaper_info_explore_button)).check(
                matches(not(isDisplayed())));
    }
}

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

package android.car.cts;

import static android.car.media.CarMediaManager.MEDIA_SOURCE_MODE_BROWSE;
import static android.car.media.CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.app.UiAutomation;
import android.car.Car;
import android.car.cts.utils.DumpUtils;
import android.car.media.CarMediaManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiLevelUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@AppModeFull(reason = "No need to run the tests in instant app mode")
public class CarMediaManagerTest {

    private static final String TAG = CarMediaManagerTest.class.getSimpleName();

    private static final String CAR_POWER_MANAGEMENT_SERVICE_NAME = "CarPowerManagementService";
    private static final String MEDIA_COMPONENT_KEY = "MEDIA";
    private static final String CAR_MEDIA_SERVICE_NAME = "CarMediaService";
    private static final String MEDIA_CONNECTOR_STARTED_KEY = "MediaConnectorService";
    private static final long TIMEOUT = 3L;
    private static final String PACKAGE = "android.car.cts";
    private static final ComponentName FAKE_0 = ComponentName.createRelative(PACKAGE, ".fake0");
    private static final ComponentName FAKE_1 = ComponentName.createRelative(PACKAGE, ".fake1");
    private static final ComponentName FAKE_2 = ComponentName.createRelative(PACKAGE, ".fake2");
    private static final ComponentName[] FAKE_1_THEN_FAKE_2 = {FAKE_1, FAKE_2};
    private static final ComponentName[] FAKE_2_THEN_FAKE_1 = {FAKE_2, FAKE_1};

    private final Context mInstrumentationContext =
            InstrumentationRegistry.getInstrumentation().getContext();

    private MediaSourceTracker mPlaybackTracker;
    private MediaSourceTracker mBrowseTracker;

    private CarMediaManager mCarMediaManager;
    private boolean mSavedIndependentPlaybackConfig;
    private ComponentName mSavedPlaybackSource;
    private ComponentName mSavedBrowseSource;

    private final UiAutomation mUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();

    /** Returns the current media source for the given mode. */
    private ComponentName getSource(int mode) {
        return mCarMediaManager.getMediaSource(mode);
    }

    /**
     * Returns the n most recent media sources for the given mode. We can only check the start of
     * the list as the tail contains values from before the test.
     */
    private List<ComponentName> getRecentSources(int mode, int n) {
        return mCarMediaManager.getLastMediaSources(mode).subList(0, n);
    }

    private void initMediaSourceTrackers(int expectedCount) {
        mPlaybackTracker = new MediaSourceTracker(expectedCount);
        mBrowseTracker = new MediaSourceTracker(expectedCount);

        // Register browse and playback listeners.
        mCarMediaManager.addMediaSourceListener(mPlaybackTracker, MEDIA_SOURCE_MODE_PLAYBACK);
        mCarMediaManager.addMediaSourceListener(mBrowseTracker, MEDIA_SOURCE_MODE_BROWSE);
    }

    @Before
    public void setUp() {
        Car car = Car.createCar(mInstrumentationContext);
        mUiAutomation.adoptShellPermissionIdentity("android.permission.MEDIA_CONTENT_CONTROL");

        mCarMediaManager = (CarMediaManager) car.getCarManager(Car.CAR_MEDIA_SERVICE);
        assertThat(mCarMediaManager).isNotNull();

        // Backup the current state of the CarMediaManager.
        mSavedIndependentPlaybackConfig = mCarMediaManager.isIndependentPlaybackConfig();
        mSavedPlaybackSource = getSource(MEDIA_SOURCE_MODE_PLAYBACK);
        mSavedBrowseSource = getSource(MEDIA_SOURCE_MODE_BROWSE);

        // Check that the Media manager has a media source for each mode
        assertThat(mSavedPlaybackSource).isNotNull();
        assertThat(mSavedBrowseSource).isNotNull();

        // Set a consistent starting source in each mode.
        mCarMediaManager.setMediaSource(FAKE_0, MEDIA_SOURCE_MODE_BROWSE);
        mCarMediaManager.setMediaSource(FAKE_0, MEDIA_SOURCE_MODE_PLAYBACK);
    }

    @After
    public void tearDown() {
        // Unregister browse and playback listeners.
        if (mPlaybackTracker != null) {
            mCarMediaManager.removeMediaSourceListener(
                    mPlaybackTracker, MEDIA_SOURCE_MODE_PLAYBACK);
        }
        if (mBrowseTracker != null) {
            mCarMediaManager.removeMediaSourceListener(mBrowseTracker, MEDIA_SOURCE_MODE_BROWSE);
        }

        // Restore the state of the CarMediaManager to what it was before the test.
        mCarMediaManager.setIndependentPlaybackConfig(mSavedIndependentPlaybackConfig);
        mCarMediaManager.setMediaSource(mSavedPlaybackSource, MEDIA_SOURCE_MODE_PLAYBACK);
        mCarMediaManager.setMediaSource(mSavedBrowseSource, MEDIA_SOURCE_MODE_BROWSE);
        mUiAutomation.dropShellPermissionIdentity();
    }

    /** Skips the test on android versions before R.*/
    private void ignoreOnAndroidPreR() {
        assumeTrue("test requires R+ to run",
                ("R".equals(Build.VERSION.CODENAME) || // Remove once R gets its api number.
                        ApiLevelUtil.isAtLeast(Build.VERSION_CODES.R)));
    }

    /**
     * When {@link CarMediaManager} uses a dependent playback config, checks that setting a media
     * source in each mode sends the appropriate notifications and ends up with the correct history.
     */
    @Test
    public void testDependentPlaybackConfig() {
        ignoreOnAndroidPreR();
        mCarMediaManager.setIndependentPlaybackConfig(false);
        initMediaSourceTrackers(/*expectedCount= */ 2);

        // Set a browse media source and verify it becomes the current browse and playback source.
        mCarMediaManager.setMediaSource(FAKE_1, MEDIA_SOURCE_MODE_BROWSE);
        assertThat(getSource(MEDIA_SOURCE_MODE_BROWSE)).isEqualTo(FAKE_1);
        assertThat(getSource(MEDIA_SOURCE_MODE_PLAYBACK)).isEqualTo(FAKE_1);

        // Set a playback media source and verify it becomes the current browse and playback source.
        mCarMediaManager.setMediaSource(FAKE_2, MEDIA_SOURCE_MODE_PLAYBACK);
        assertThat(getSource(MEDIA_SOURCE_MODE_BROWSE)).isEqualTo(FAKE_2);
        assertThat(getSource(MEDIA_SOURCE_MODE_PLAYBACK)).isEqualTo(FAKE_2);

        waitUntilAllChangesReceived();

        // Verify that both listeners were notified of each source.
        assertThat(mBrowseTracker.mSources).containsExactlyElementsIn(FAKE_1_THEN_FAKE_2).inOrder();
        assertThat(mPlaybackTracker.mSources)
                .containsExactlyElementsIn(FAKE_1_THEN_FAKE_2).inOrder();

        // Verify that the history for each mode contains each source in reverse order.
        List<ComponentName> browsedSources = getRecentSources(MEDIA_SOURCE_MODE_BROWSE, 2);
        List<ComponentName> playbackSources = getRecentSources(MEDIA_SOURCE_MODE_PLAYBACK, 2);
        assertThat(browsedSources).containsExactlyElementsIn(FAKE_2_THEN_FAKE_1).inOrder();
        assertThat(playbackSources).containsExactlyElementsIn(FAKE_2_THEN_FAKE_1).inOrder();
    }

    @Test
    public void testIndependentPlaybackConfig() {
        ignoreOnAndroidPreR();
        mCarMediaManager.setIndependentPlaybackConfig(true);
        initMediaSourceTrackers(/*expectedCount= */ 1);

        // Set a browse media source and verify it only becomes the current browse source.
        mCarMediaManager.setMediaSource(FAKE_1, MEDIA_SOURCE_MODE_BROWSE);
        assertThat(getSource(MEDIA_SOURCE_MODE_BROWSE)).isEqualTo(FAKE_1);
        assertThat(getSource(MEDIA_SOURCE_MODE_PLAYBACK)).isEqualTo(FAKE_0);

        // Set a playback media source and verify it only becomes the current playback source.
        mCarMediaManager.setMediaSource(FAKE_2, MEDIA_SOURCE_MODE_PLAYBACK);
        assertThat(getSource(MEDIA_SOURCE_MODE_BROWSE)).isEqualTo(FAKE_1);
        assertThat(getSource(MEDIA_SOURCE_MODE_PLAYBACK)).isEqualTo(FAKE_2);

        waitUntilAllChangesReceived();

        // Verify that both listeners were notified of each source.
        assertThat(mBrowseTracker.mSources).containsExactly(FAKE_1);
        assertThat(mPlaybackTracker.mSources).containsExactly(FAKE_2);

        // Verify that the history for each mode contains each source.
        List<ComponentName> browsedSources = getRecentSources(MEDIA_SOURCE_MODE_BROWSE, 1);
        List<ComponentName> playbackSources = getRecentSources(MEDIA_SOURCE_MODE_PLAYBACK, 1);
        assertThat(browsedSources).containsExactly(FAKE_1);
        assertThat(playbackSources).containsExactly(FAKE_2);
    }

    @Test
    public void testMediaConnectorServiceStarted() {
        Map<String, String> carPowerManagementServiceDump =
                DumpUtils.executeDumpShellCommand(CAR_POWER_MANAGEMENT_SERVICE_NAME);
        // Skip the test if the MEDIA power is not enabled.
        assumeTrue(carPowerManagementServiceDump.get(MEDIA_COMPONENT_KEY).equals("on"));

        Map<String, String> carMediaServiceDump =
                DumpUtils.executeDumpShellCommand(CAR_MEDIA_SERVICE_NAME);

        String startedMediaConnectorServiceName =
                carMediaServiceDump.get(MEDIA_CONNECTOR_STARTED_KEY);
        assertThat(startedMediaConnectorServiceName).isNotNull();
        assertThat(ComponentName.unflattenFromString(startedMediaConnectorServiceName)).isNotNull();
    }

    private void waitUntilAllChangesReceived() {
        boolean result = false;
        try {
            result = mPlaybackTracker.await() && mBrowseTracker.await();
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted while waiting until all changes are received.", e);
            // The assert below will fail the test in case of an exception.
        }
        assertWithMessage("All expected changes have been received").that(result).isTrue();
    }

    /** Helper that stores all the callback values in an array. */
    private static class MediaSourceTracker implements CarMediaManager.MediaSourceChangedListener {

        private final ArrayList<ComponentName> mSources;
        private final CountDownLatch mCountDownLatch;

        MediaSourceTracker(int expectedCount) {
            mSources = new ArrayList<>(expectedCount);
            mCountDownLatch = new CountDownLatch(expectedCount);
        }

        public boolean await() throws InterruptedException {
            return mCountDownLatch.await(TIMEOUT, TimeUnit.SECONDS);
        }

        @Override
        public void onMediaSourceChanged(ComponentName componentName) {
            mSources.add(componentName);
            mCountDownLatch.countDown();
        }
    }
}

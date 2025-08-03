/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.managedprovisioning.common;

import static com.android.managedprovisioning.common.RetryLaunchViewModel.LaunchActivityFailureEvent.REASON_EXCEEDED_MAXIMUM_NUMBER_ACTIVITY_LAUNCH_RETRIES;

import static com.google.common.truth.Truth.assertThat;

import android.app.Application;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.nene.utils.Poll;
import com.android.managedprovisioning.common.RetryLaunchViewModel.LaunchActivityEvent;
import com.android.managedprovisioning.common.RetryLaunchViewModel.LaunchActivityFailureEvent;
import com.android.managedprovisioning.common.RetryLaunchViewModel.LaunchActivityWaitingForRetryEvent;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.time.Duration;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;


@SmallTest
@RunWith(JUnit4.class)
public class RetryLaunchViewModelTest {
    private static final int LAUNCH_ROLE_HOLDER_UPDATER_PERIOD_MILLIS = 100;
    private static final int NO_EVENT_TIMEOUT_MILLIS = 200;
    private static final int LAUNCH_ROLE_HOLDER_MAX_RETRIES = 1;
    private static final int ROLE_HOLDER_UPDATE_MAX_RETRIES = 1;
    private static final LaunchActivityEvent
            LAUNCH_ACTIVITY_EVENT = createLaunchRoleHolderUpdaterEvent();
    private static final LaunchActivityFailureEvent
            EXCEED_MAX_NUMBER_LAUNCH_RETRIES_EVENT = createExceedMaxNumberLaunchRetriesEvent();
    private static final LaunchActivityWaitingForRetryEvent WAITING_FOR_RETRY_EVENT =
            createWaitingForRetryEvent();
    private static final String TEST_DEVICE_MANAGEMENT_ROLE_HOLDER_UPDATER_PACKAGE_NAME =
            "com.devicemanagementroleholderupdater.test.package";

    private final Context mApplicationContext = ApplicationProvider.getApplicationContext();
    private Handler mHandler;
    private TestConfig mTestConfig;
    private boolean mCanLaunchRoleHolderUpdater = true;
    private RetryLaunchViewModel mViewModel;
    private Queue<ViewModelEvent> mEvents;
    private Utils mUtils = new Utils();

    @Before
    public void setUp() {
        mTestConfig = new TestConfig(
                LAUNCH_ROLE_HOLDER_UPDATER_PERIOD_MILLIS,
                LAUNCH_ROLE_HOLDER_MAX_RETRIES,
                ROLE_HOLDER_UPDATE_MAX_RETRIES);
        mCanLaunchRoleHolderUpdater = true;
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> mHandler = new Handler(Looper.myLooper()));
        mViewModel = createViewModel();
        mEvents = subscribeToViewModelEvents();
    }

    @Test
    public void tryStartRoleHolderUpdater_launchUpdater_works() {
        mViewModel.tryStartActivity();
        blockUntilNextUiThreadCycle();

        assertThat(mEvents).containsExactly(LAUNCH_ACTIVITY_EVENT);
    }

    @Test
    public void tryStartRoleHolderUpdater_rescheduleLaunchUpdater_works() {
        mTestConfig.launchRoleHolderMaxRetries = 2;
        mCanLaunchRoleHolderUpdater = false;

        mViewModel.tryStartActivity();
        mCanLaunchRoleHolderUpdater = true;

        pollForEvents(
                mEvents,
                WAITING_FOR_RETRY_EVENT,
                LAUNCH_ACTIVITY_EVENT);
    }

    @Test
    public void tryStartRoleHolderUpdater_rescheduleLaunchUpdater_exceedsMaxRetryLimit_fails() {
        mTestConfig.roleHolderUpdateMaxRetries = 1;
        mCanLaunchRoleHolderUpdater = false;

        mViewModel.tryStartActivity();

        pollForEvents(
                mEvents,
                WAITING_FOR_RETRY_EVENT,
                EXCEED_MAX_NUMBER_LAUNCH_RETRIES_EVENT);
    }

    @Test
    public void stopLaunchRetries_works() {
        mTestConfig.roleHolderUpdateMaxRetries = 1;
        mCanLaunchRoleHolderUpdater = false;

        mViewModel.tryStartActivity();
        mViewModel.stopLaunchRetries();

        pollForEvents(mEvents, WAITING_FOR_RETRY_EVENT);
    }

    @Test
    public void markWaitingForActivityResult_works() {
        mViewModel.markWaitingForActivityResult();

        assertThat(mViewModel.isWaitingForActivityResult()).isTrue();
    }

    @Test
    public void isWaitingForActivityResult_defaultsToFalse() {
        assertThat(mViewModel.isWaitingForActivityResult()).isFalse();
    }

    private void pollForEvents(
            Queue<ViewModelEvent> actualEvents,
            ViewModelEvent... expectedEvents) {
        for (ViewModelEvent nextExpectedEvent : expectedEvents) {
            Poll.forValue("CapturedViewModelEvents", () -> actualEvents)
                    .toMeet(actualEventsQueue -> !actualEventsQueue.isEmpty())
                    .errorOnFail("Expected CapturedViewModelEvents to contain exactly "
                            + Arrays.stream(expectedEvents)
                            .map(Object::toString).collect(Collectors.joining()))
                    .await();
            assertThat(actualEvents.remove()).isEqualTo(nextExpectedEvent);
        }
        pollForNoEvent(actualEvents);
    }

    private void pollForNoEvent(Queue<ViewModelEvent> capturedViewModelEvents) {
        // TODO(b/208237942): A pattern for testing that something does not happen
        assertThat(Poll.forValue("CapturedViewModelEvents", () -> capturedViewModelEvents)
                .toMeet(viewModelEvents -> !viewModelEvents.isEmpty())
                .timeout(Duration.ofMillis(NO_EVENT_TIMEOUT_MILLIS))
                .await())
                .isEmpty();
    }

    private static LaunchActivityFailureEvent createExceedMaxNumberLaunchRetriesEvent() {
        return new LaunchActivityFailureEvent(
                REASON_EXCEEDED_MAXIMUM_NUMBER_ACTIVITY_LAUNCH_RETRIES);
    }

    private static LaunchActivityWaitingForRetryEvent createWaitingForRetryEvent() {
        return new LaunchActivityWaitingForRetryEvent();
    }

    private static LaunchActivityEvent createLaunchRoleHolderUpdaterEvent() {
        return new LaunchActivityEvent(createUpdateDeviceManagementRoleHolderIntent());
    }

    private Queue<ViewModelEvent> subscribeToViewModelEvents() {
        Queue<ViewModelEvent> capturedViewModelEvents = new ConcurrentLinkedQueue<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> mViewModel.observeViewModelEvents()
                        .observeForever(capturedViewModelEvents::add));
        return capturedViewModelEvents;
    }

    private void blockUntilNextUiThreadCycle() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {});
    }

    private RetryLaunchViewModel createViewModel() {
        return new RetryLaunchViewModel(
                (Application) mApplicationContext,
                createUpdateDeviceManagementRoleHolderIntent(),
                mHandler,
                (context, intent) -> mCanLaunchRoleHolderUpdater,
                mTestConfig);
    }

    private static Intent createUpdateDeviceManagementRoleHolderIntent() {
        return new Intent(DevicePolicyManager.ACTION_UPDATE_DEVICE_POLICY_MANAGEMENT_ROLE_HOLDER)
                .setPackage(TEST_DEVICE_MANAGEMENT_ROLE_HOLDER_UPDATER_PACKAGE_NAME);
    }

    private static final class TestConfig implements RetryLaunchViewModel.Config {
        public int launchRoleHolderUpdaterPeriodMillis;
        public int launchRoleHolderMaxRetries;
        public int roleHolderUpdateMaxRetries;

        TestConfig(
                int launchRoleHolderUpdaterPeriodMillis,
                int launchRoleHolderMaxRetries,
                int roleHolderUpdateMaxRetries) {
            this.launchRoleHolderUpdaterPeriodMillis = launchRoleHolderUpdaterPeriodMillis;
            this.launchRoleHolderMaxRetries = launchRoleHolderMaxRetries;
            this.roleHolderUpdateMaxRetries = roleHolderUpdateMaxRetries;
        }

        @Override
        public long getLaunchActivityRetryMillis() {
            return launchRoleHolderUpdaterPeriodMillis;
        }

        @Override
        public int getLaunchActivityMaxRetries() {
            return launchRoleHolderMaxRetries;
        }

    }
}

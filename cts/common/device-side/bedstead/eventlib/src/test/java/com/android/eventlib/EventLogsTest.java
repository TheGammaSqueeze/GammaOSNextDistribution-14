/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.eventlib;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import static com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_USERS_FULL;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasWorkProfile;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.users.UserReference;
import com.android.compatibility.common.util.SystemUtil;
import com.android.eventlib.events.CustomEvent;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@RunWith(BedsteadJUnit4.class)
public class EventLogsTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final String TEST_APP_PACKAGE_NAME = "com.android.eventlib.tests.testapp";
    private static final String INCORRECT_PACKAGE_NAME = "com.android.eventlib.tests.notapackage";
    private static final UserHandle NON_EXISTING_USER_HANDLE = UserHandle.of(1000);

    private static final String TEST_TAG1 = "TEST_TAG1";
    private static final String TEST_TAG2 = "TEST_TAG2";
    private static final String DATA_1 = "DATA_1";
    private static final String DATA_2 = "DATA_2";

    private static final Duration VERY_SHORT_POLL_WAIT = Duration.ofMillis(20);

    private boolean hasScheduledEvents = false;
    private boolean hasScheduledEventsOnTestApp = false;
    private final ScheduledExecutorService mScheduledExecutorService =
            Executors.newSingleThreadScheduledExecutor();
    private static final Context sContext = TestApis.context().instrumentedContext();

    @Before
    public void setUp() {
        EventLogs.resetLogs();
    }

    @After
    public void teardown() {
        if (hasScheduledEvents) {
            // Clear the queue
            CustomEvent.queryPackage(sContext.getPackageName()).poll();
        }
        if (hasScheduledEventsOnTestApp) {
            // Clear the queue
            CustomEvent.queryPackage(TEST_APP_PACKAGE_NAME).poll();
        }
    }

    @Test
    public void resetLogs_poll_doesNotReturnLogs() {
        CustomEvent.logger(sContext)
                .setTag(TEST_TAG1)
                .log();

        EventLogs.resetLogs();

        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(sContext.getPackageName());
        assertThat(eventLogs.poll(VERY_SHORT_POLL_WAIT)).isNull();
    }

    @Test
    public void resetLogs_differentPackage_poll_doesNotReturnLogs() {
        logCustomEventOnTestApp();

        EventLogs.resetLogs();

        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(TEST_APP_PACKAGE_NAME);
        assertThat(eventLogs.poll(VERY_SHORT_POLL_WAIT)).isNull();
    }

    @Test
    public void poll_nothingLogged_returnsNull() {
        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(sContext.getPackageName())
                .whereTag().isEqualTo(TEST_TAG1);

        assertThat(eventLogs.poll(VERY_SHORT_POLL_WAIT)).isNull();
    }

    @Test
    public void poll_loggedAfter_returnsNull() {
        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(sContext.getPackageName())
                .whereTag().isEqualTo(TEST_TAG1);
        scheduleCustomEventInOneSecond();

        assertThat(eventLogs.poll(VERY_SHORT_POLL_WAIT)).isNull();
    }

    @Test
    public void poll_differentPackage_nothingLogged_returnsNull() {
        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(TEST_APP_PACKAGE_NAME)
                .whereTag().isEqualTo(TEST_TAG1);

        assertThat(eventLogs.poll(VERY_SHORT_POLL_WAIT)).isNull();
    }

    @Test
    public void poll_differentPackage_loggedAfter_returnsNull() {
        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(TEST_APP_PACKAGE_NAME)
                .whereTag().isEqualTo(TEST_TAG1);
        scheduleCustomEventInOneSecondOnTestApp();

        assertThat(eventLogs.poll(VERY_SHORT_POLL_WAIT)).isNull();
    }

    @Test
    public void poll_loggedOnTwoPackages_returnsEventFromQueriedPackage() {
        logCustomEventOnTestApp(/* tag= */ TEST_TAG1, /* data= */ DATA_1);
        CustomEvent.logger(sContext).setTag(TEST_TAG1).setData(DATA_2).log();

        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(sContext.getPackageName())
                .whereTag().isEqualTo(TEST_TAG1);

        assertThat(eventLogs.poll().data()).isEqualTo(DATA_2);
    }

    @Test
    public void poll_alreadyLogged_returnsFirstEvent() {
        CustomEvent.logger(sContext)
                .setTag(TEST_TAG1)
                .setData(DATA_1)
                .log();
        CustomEvent.logger(sContext)
                .setTag(TEST_TAG1)
                .setData(DATA_2)
                .log();

        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(sContext.getPackageName())
                .whereTag().isEqualTo(TEST_TAG1);

        assertThat(eventLogs.poll().data()).isEqualTo(DATA_1);
    }

    @Test
    public void poll_differentPackage_alreadyLogged_returnsFirstEvent() {
        logCustomEventOnTestApp(/* tag= */ TEST_TAG1, /* data= */ DATA_1);
        logCustomEventOnTestApp(/* tag= */ TEST_TAG1, /* data= */ DATA_2);

        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(TEST_APP_PACKAGE_NAME)
                .whereTag().isEqualTo(TEST_TAG1);

        assertThat(eventLogs.poll().data()).isEqualTo(DATA_1);
    }

    @Test
    public void poll_hasReturnedAllEvents_returnsNull() {
        CustomEvent.logger(sContext)
                .setTag(TEST_TAG1)
                .log();
        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(sContext.getPackageName())
                .whereTag().isEqualTo(TEST_TAG1);
        eventLogs.poll();

        assertThat(eventLogs.poll(VERY_SHORT_POLL_WAIT)).isNull();
    }

    @Test
    public void poll_differentPackage_hasReturnedAllEvents_returnsNull() {
        logCustomEventOnTestApp(/* tag= */ TEST_TAG1, /* data= */ null);
        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(TEST_APP_PACKAGE_NAME)
                .whereTag().isEqualTo(TEST_TAG1);
        eventLogs.poll();

        assertThat(eventLogs.poll(VERY_SHORT_POLL_WAIT)).isNull();
    }

    @Test
    public void poll_returnsNextUnseenEvent() {
        CustomEvent.logger(sContext)
                .setTag(TEST_TAG1)
                .setData(DATA_1)
                .log();
        CustomEvent.logger(sContext)
                .setTag(TEST_TAG1)
                .setData(DATA_2)
                .log();
        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(sContext.getPackageName())
                .whereTag().isEqualTo(TEST_TAG1);
        eventLogs.poll();

        assertThat(eventLogs.poll().data()).isEqualTo(DATA_2);
    }

    @Test
    public void poll_differentPackage_returnsNextUnseenEvent() {
        logCustomEventOnTestApp(/* tag= */ TEST_TAG1, /* data= */ DATA_1);
        logCustomEventOnTestApp(/* tag= */ TEST_TAG1, /* data= */ DATA_2);
        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(TEST_APP_PACKAGE_NAME)
                .whereTag().isEqualTo(TEST_TAG1);
        eventLogs.poll();

        assertThat(eventLogs.poll().data()).isEqualTo(DATA_2);
    }

    @Test
    public void poll_loggedPreviouslyWithDifferentData_returnsCorrectEvent() {
        CustomEvent.logger(sContext)
                .setTag(TEST_TAG2)
                .log();
        CustomEvent.logger(sContext)
                .setTag(TEST_TAG1)
                .log();

        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(sContext.getPackageName())
                .whereTag().isEqualTo(TEST_TAG1);

        assertThat(eventLogs.poll().tag()).isEqualTo(TEST_TAG1);
    }

    @Test
    public void poll_differentPackage_loggedPreviouslyWithDifferentData_returnsCorrectEvent() {
        logCustomEventOnTestApp(/* tag= */ TEST_TAG2, /* data= */ null);
        logCustomEventOnTestApp(/* tag= */ TEST_TAG1, /* data= */ null);
        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(TEST_APP_PACKAGE_NAME)
                .whereTag().isEqualTo(TEST_TAG1);

        assertThat(eventLogs.poll().tag()).isEqualTo(TEST_TAG1);
    }

    @Test
    public void poll_loggedAfter_returnsEvent() {
        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(sContext.getPackageName())
                .whereTag().isEqualTo(TEST_TAG1);

        // We don't use scheduleCustomEventInOneSecond(); because we don't need any special teardown
        // as we're blocking for the event in this method
        mScheduledExecutorService.schedule(() -> {
            CustomEvent.logger(sContext)
                    .setTag(TEST_TAG1)
                    .log();
        }, 1, TimeUnit.SECONDS);

        assertThat(eventLogs.poll()).isNotNull();
    }

    @Test
    public void poll_differentPackage_loggedAfter_returnsEvent() {
        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(TEST_APP_PACKAGE_NAME)
                .whereTag().isEqualTo(TEST_TAG1);

        // We don't use scheduleCustomEventInOneSecond(); because we don't need any special teardown
        // as we're blocking for the event in this method
        mScheduledExecutorService.schedule(() -> {
            logCustomEventOnTestApp(/* tag= */ TEST_TAG1, /* data= */ null);
        }, 1, TimeUnit.SECONDS);

        assertThat(eventLogs.poll()).isNotNull();
    }

    @Test
    public void poll_loggedAfterPreviousCallToPoll_returnsNewEvent() {
        CustomEvent.logger(sContext)
                .setData(DATA_1)
                .log();
        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(sContext.getPackageName());
        eventLogs.poll();
        CustomEvent.logger(sContext)
                .setData(DATA_2)
                .log();

        assertThat(eventLogs.poll().data()).isEqualTo(DATA_2);
    }

    @Test
    public void poll_differentPackage_loggedAfterPreviousCallToPoll_returnsNewEvent() {
        logCustomEventOnTestApp(/* tag= */ null, /* data= */ DATA_1);
        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(TEST_APP_PACKAGE_NAME);
        eventLogs.poll();
        logCustomEventOnTestApp(/* tag= */ null, /* data= */ DATA_2);

        assertThat(eventLogs.poll().data()).isEqualTo(DATA_2);
    }

    @Test
    public void poll_calledOnSeparateQuery_returnsFromStartsAgain() {
        CustomEvent.logger(sContext)
                .setData(DATA_1)
                .log();
        EventLogs<CustomEvent> eventLogs1 = CustomEvent.queryPackage(sContext.getPackageName());
        EventLogs<CustomEvent> eventLogs2 = CustomEvent.queryPackage(sContext.getPackageName());

        assertThat(eventLogs1.poll()).isNotNull();
        assertThat(eventLogs2.poll()).isNotNull();
    }

    @Test
    public void poll_differentPackage_calledOnSeparateQuery_returnsFromStartsAgain() {
        logCustomEventOnTestApp(/* tag= */ null, /* data= */ DATA_1);
        EventLogs<CustomEvent> eventLogs1 = CustomEvent.queryPackage(TEST_APP_PACKAGE_NAME);
        EventLogs<CustomEvent> eventLogs2 = CustomEvent.queryPackage(TEST_APP_PACKAGE_NAME);

        assertThat(eventLogs1.poll()).isNotNull();
        assertThat(eventLogs2.poll()).isNotNull();
    }

    @Test
    public void poll_obeysLambdaFilter() {
        CustomEvent.logger(sContext)
                .setTag(TEST_TAG1)
                .log();
        CustomEvent.logger(sContext)
                .setTag(TEST_TAG2)
                .log();

        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(sContext.getPackageName())
                .filter(e -> TEST_TAG2.equals(e.tag()));

        assertThat(eventLogs.poll().tag()).isEqualTo(TEST_TAG2);
        assertThat(eventLogs.poll(VERY_SHORT_POLL_WAIT)).isNull();
    }

    @Test
    public void poll_differentPackage_obeysLambdaFilter() {
        logCustomEventOnTestApp(/* tag= */ TEST_TAG1, /* data= */ null);
        logCustomEventOnTestApp(/* tag= */ TEST_TAG2, /* data= */ null);

        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(TEST_APP_PACKAGE_NAME)
                .filter(e -> TEST_TAG2.equals(e.tag()));

        assertThat(eventLogs.poll().tag()).isEqualTo(TEST_TAG2);
        assertThat(eventLogs.poll(VERY_SHORT_POLL_WAIT)).isNull();
    }

    @Test
    public void poll_obeysMultipleLambdaFilters() {
        CustomEvent.logger(sContext)
                .setTag(TEST_TAG1)
                .log();
        CustomEvent.logger(sContext)
                .setTag(TEST_TAG2)
                .log();
        CustomEvent.logger(sContext)
                .setTag(TEST_TAG2)
                .setData(DATA_1)
                .log();

        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(sContext.getPackageName())
                .filter(e -> TEST_TAG2.equals(e.tag()))
                .filter(e -> DATA_1.equals(e.data()));

        CustomEvent event = eventLogs.poll();
        assertThat(event.tag()).isEqualTo(TEST_TAG2);
        assertThat(event.data()).isEqualTo(DATA_1);
        assertThat(eventLogs.poll(VERY_SHORT_POLL_WAIT)).isNull();
    }

    @Test
    public void poll_differentPackage_obeysMultipleLambdaFilters() {
        logCustomEventOnTestApp(/* tag= */ TEST_TAG1, /* data= */ null);
        logCustomEventOnTestApp(/* tag= */ TEST_TAG2, /* data= */ null);
        logCustomEventOnTestApp(/* tag= */ TEST_TAG2, /* data= */ DATA_1);

        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(TEST_APP_PACKAGE_NAME)
                .filter(e -> TEST_TAG2.equals(e.tag()))
                .filter(e -> DATA_1.equals(e.data()));

        CustomEvent event = eventLogs.poll();
        assertThat(event.tag()).isEqualTo(TEST_TAG2);
        assertThat(event.data()).isEqualTo(DATA_1);
        assertThat(eventLogs.poll(VERY_SHORT_POLL_WAIT)).isNull();
    }

    @Test
    public void waitForEvent_hasEvent_returnsEvent() {
        CustomEvent.logger(sContext)
                .setTag(TEST_TAG1)
                .log();

        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(sContext.getPackageName())
                .whereTag().isEqualTo(TEST_TAG1);

        assertThat(eventLogs.waitForEvent().tag()).isEqualTo(TEST_TAG1);
    }

    @Test
    public void waitForEvent_differentPackage_hasEvent_returnsEvent() {
        logCustomEventOnTestApp(/* tag= */ TEST_TAG1, /* data= */ null);

        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(TEST_APP_PACKAGE_NAME)
                .whereTag().isEqualTo(TEST_TAG1);

        assertThat(eventLogs.waitForEvent().tag()).isEqualTo(TEST_TAG1);
    }

    @Test
    public void waitForEvent_noEvent_throwsException() {
        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(sContext.getPackageName())
                .whereTag().isEqualTo(TEST_TAG1);

        assertThrows(AssertionError.class, () -> eventLogs.waitForEvent(VERY_SHORT_POLL_WAIT));
    }

    @Test
    public void waitForEvent_loggedAfter_throwsException() {
        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(sContext.getPackageName())
                .whereTag().isEqualTo(TEST_TAG1);
        scheduleCustomEventInOneSecond();

        assertThrows(AssertionError.class, () -> eventLogs.waitForEvent(VERY_SHORT_POLL_WAIT));
    }

    @Test
    public void waitForEvent_differentPackage_noEvent_throwsException() {
        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(TEST_APP_PACKAGE_NAME)
                .whereTag().isEqualTo(TEST_TAG1);

        assertThrows(AssertionError.class, () -> eventLogs.waitForEvent(VERY_SHORT_POLL_WAIT));
    }

    @Test
    public void waitForEvent_differentPackage_loggedAfter_throwsException() {
        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(TEST_APP_PACKAGE_NAME)
                .whereTag().isEqualTo(TEST_TAG1);
        scheduleCustomEventInOneSecondOnTestApp();

        assertThrows(AssertionError.class, () -> eventLogs.waitForEvent(VERY_SHORT_POLL_WAIT));
    }

    @Test
    public void waitForEvent_loggedAfter_returnsEvent() {
        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(sContext.getPackageName())
                .whereTag().isEqualTo(TEST_TAG1);

        // We don't use scheduleCustomEventInOneSecond(); because we don't need any special teardown
        // as we're blocking for the event in this method
        mScheduledExecutorService.schedule(() -> {
            CustomEvent.logger(sContext)
                    .setTag(TEST_TAG1)
                    .log();
        }, 1, TimeUnit.SECONDS);

        assertThat(eventLogs.waitForEvent()).isNotNull();
    }

    @Test
    public void waitForEvent_differentPackage_loggedAfter_returnsEvent() {
        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(TEST_APP_PACKAGE_NAME)
                .whereTag().isEqualTo(TEST_TAG1);

        // We don't use scheduleCustomEventInOneSecond(); because we don't need any special teardown
        // as we're blocking for the event in this method
        mScheduledExecutorService.schedule(() -> {
            logCustomEventOnTestApp(/* tag= */ TEST_TAG1, /* data= */ null);
        }, 1, TimeUnit.SECONDS);

        assertThat(eventLogs.waitForEvent()).isNotNull();
    }

    @Test
    public void otherProcessGetsKilled_stillReturnsLogs() {
        logCustomEventOnTestApp(/* tag= */ null, /* data= */ null);

        killTestApp();

        assertThat(CustomEvent.queryPackage(TEST_APP_PACKAGE_NAME).poll()).isNotNull();
    }

    @Test
    public void otherProcessGetsKilledMultipleTimes_stillReturnsOriginalLog() {
        logCustomEventOnTestApp(/* tag= */ TEST_TAG1, /* data= */ null);
        killTestApp();
        logCustomEventOnTestApp(/* tag= */ TEST_TAG2, /* data= */ null);
        killTestApp();

        assertThat(
                CustomEvent.queryPackage(TEST_APP_PACKAGE_NAME).poll().tag()).isEqualTo(TEST_TAG1);
    }

    @Test
    public void otherProcessGetsKilled_returnsLogsInCorrectOrder() {
        logCustomEventOnTestApp(/* tag= */ TEST_TAG1, /* data= */ null);
        logCustomEventOnTestApp(/* tag= */ TEST_TAG2, /* data= */ null);
        killTestApp();

        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(TEST_APP_PACKAGE_NAME);
        assertThat(eventLogs.poll().tag()).isEqualTo(TEST_TAG1);
        assertThat(eventLogs.poll().tag()).isEqualTo(TEST_TAG2);
    }

    @Test
    public void otherProcessGetsKilledMultipleTimes_returnsLogsInCorrectOrder() {
        logCustomEventOnTestApp(/* tag= */ TEST_TAG1, /* data= */ null);
        killTestApp();
        logCustomEventOnTestApp(/* tag= */ TEST_TAG2, /* data= */ null);
        killTestApp();

        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(TEST_APP_PACKAGE_NAME);
        assertThat(eventLogs.poll().tag()).isEqualTo(TEST_TAG1);
        assertThat(eventLogs.poll().tag()).isEqualTo(TEST_TAG2);
    }

    @Test
    @EnsureHasWorkProfile
    public void differentUser_queryWorks() {
        TestApis.packages().find(TEST_APP_PACKAGE_NAME).installExisting(sDeviceState.workProfile());
        logCustomEventOnTestApp(
                sDeviceState.workProfile(), /* tag= */ TEST_TAG1, /* data= */ null);

        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(TEST_APP_PACKAGE_NAME)
                .onUser(sDeviceState.workProfile());

        assertThat(eventLogs.poll().tag()).isEqualTo(TEST_TAG1);
    }

    @Test
    @EnsureHasWorkProfile
    public void differentUserSpecifiedByUserHandle_queryWorks() {
        TestApis.packages().find(TEST_APP_PACKAGE_NAME).installExisting(sDeviceState.workProfile());
        logCustomEventOnTestApp(
                sDeviceState.workProfile(), /* tag= */ TEST_TAG1, /* data= */ null);

        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(TEST_APP_PACKAGE_NAME)
                .onUser(sDeviceState.workProfile().userHandle());

        assertThat(eventLogs.poll().tag()).isEqualTo(TEST_TAG1);
    }

    @Test
    @EnsureHasWorkProfile
    public void differentUser_doesntGetEventsFromWrongUser() {
        TestApis.packages().find(TEST_APP_PACKAGE_NAME).installExisting(sDeviceState.workProfile());
        logCustomEventOnTestApp(/* tag= */ TEST_TAG1, /* data= */ null);
        logCustomEventOnTestApp(
                sDeviceState.workProfile(), /* tag= */ TEST_TAG2, /* data= */ null);

        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(TEST_APP_PACKAGE_NAME)
                .onUser(sDeviceState.workProfile());

        assertThat(eventLogs.poll().tag()).isEqualTo(TEST_TAG2);
    }

    @Test
    public void onUser_passesNullUser_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> CustomEvent.queryPackage(TEST_APP_PACKAGE_NAME)
                        .onUser(/* userHandle= */(UserHandle) null));
    }

    @Test
    public void onUser_passesNullUserReference_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> CustomEvent.queryPackage(TEST_APP_PACKAGE_NAME)
                        .onUser(/* userHandle= */(UserReference) null));
    }

    @Test
    public void incorrectUserHandle_fails() {
        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(TEST_APP_PACKAGE_NAME)
                .onUser(NON_EXISTING_USER_HANDLE);

        assertThrows(AssertionError.class, eventLogs::poll);
    }

    @Test
    public void incorrectPackageName_fails() {
        EventLogs<CustomEvent> eventLogs = CustomEvent.queryPackage(INCORRECT_PACKAGE_NAME);

        assertThrows(AssertionError.class, eventLogs::poll);
    }

    private void scheduleCustomEventInOneSecond() {
        hasScheduledEvents = true;

        mScheduledExecutorService.schedule(() -> {
            CustomEvent.logger(sContext)
                    .log();
        }, 1, TimeUnit.SECONDS);
    }

    private void logCustomEventOnTestApp(UserReference user, String tag, String data) {
        Intent intent = new Intent();
        intent.setPackage(TEST_APP_PACKAGE_NAME);
        intent.setClassName(TEST_APP_PACKAGE_NAME, TEST_APP_PACKAGE_NAME + ".EventLoggingActivity");
        intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra("TAG", tag);
        intent.putExtra("DATA", data);

        try (PermissionContext p =
                     TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL)) {
            sContext.startActivityAsUser(intent, user.userHandle());
        }

        CustomEvent.queryPackage(TEST_APP_PACKAGE_NAME)
                .whereTag().isEqualTo(tag)
                .whereData().isEqualTo(data)
                .onUser(user)
                .waitForEvent();
    }

    private void logCustomEventOnTestApp(String tag, String data) {
        logCustomEventOnTestApp(TestApis.users().instrumented(), tag, data);
    }

    private void logCustomEventOnTestApp() {
        logCustomEventOnTestApp(/* tag= */ TEST_TAG1, /* data= */ DATA_1);
    }

    private void scheduleCustomEventInOneSecondOnTestApp() {
        hasScheduledEventsOnTestApp = true;

        mScheduledExecutorService.schedule(
                (Runnable) this::logCustomEventOnTestApp, 1, TimeUnit.SECONDS);
    }

    private void killTestApp() {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            sContext.getSystemService(ActivityManager.class)
                    .forceStopPackage(TEST_APP_PACKAGE_NAME);
        });
    }

    // TODO: Ensure tests work on O+
}

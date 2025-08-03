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

import static android.car.CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER;
import static android.car.cts.utils.ShellPermissionUtils.runWithShellPermissionIdentity;
import static android.car.media.CarAudioManager.AUDIO_FEATURE_DYNAMIC_ROUTING;
import static android.car.media.CarAudioManager.CarVolumeCallback;

import static androidx.lifecycle.Lifecycle.State.RESUMED;
import static androidx.lifecycle.Lifecycle.State.STARTED;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.car.media.CarAudioManager;
import android.car.test.PermissionsCheckerRule.EnsureHasPermission;
import android.content.Intent;
import android.graphics.Point;
import android.os.UserHandle;
import android.util.Pair;
import android.view.Display;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.test.core.app.ActivityScenario;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.PollingCheck;
import com.android.internal.annotations.GuardedBy;

import org.junit.Before;
import org.junit.Test;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This test requires a device with one active passenger occupant. It also requires that the
 * current user is the driver and not some passenger - this test won't work if
 * `--user-type secondary_user_on_secondary_display` flag is passed.
 */
public final class CarInputTest extends AbstractCarTestCase {
    public static final String TAG = CarInputTest.class.getSimpleName();
    private static final long ACTIVITY_WAIT_TIME_OUT_MS = 10_000L;
    private static final int DEFAULT_WAIT_MS = 5_000;
    private static final int NO_EVENT_WAIT_MS = 100;

    // Inject event commands.
    private static final String OPTION_SEAT = "-s";
    private static final String OPTION_ACTION = "-a";
    private static final String OPTION_COUNT = "-c";
    private static final String OPTION_POINTER_ID = "-p";
    private static final String PREFIX_INJECTING_KEY_CMD =
            "cmd car_service inject-key " + OPTION_SEAT + " %d %d";
    private static final String PREFIX_INJECTING_MOTION_CMD = "cmd car_service inject-motion";

    private CarOccupantZoneManager mCarOccupantZoneManager;

    // Driver's associated occupant zone and display id.
    private OccupantZoneInfo mDriverZoneInfo;
    private int mDriverDisplayId;

    // This field contains the occupant zone and display id for one randomly picked logged
    //  passenger.
    private OccupantZoneInfo mPassengerZoneInfo;
    private Display mPassengerDisplay;

    @Before
    public void setUp() {
        mCarOccupantZoneManager = getCar().getCarManager(CarOccupantZoneManager.class);

        // Set the driver's zone and display and ensure this test is running with as the driver
        // user.
        var driverZoneAndDisplay = getDriverZoneAndDisplay();
        mDriverZoneInfo = driverZoneAndDisplay.first;
        mDriverDisplayId = driverZoneAndDisplay.second.getDisplayId();
        assumeTestRunAsDriver();

        // Set driver zone and display for one randomly chose passenger.
        var anyPassengerZoneAndDisplay = pickAnyPassengerZoneAndDisplay();
        assumeTrue("This test requires (at least) one active passenger occupant",
                anyPassengerZoneAndDisplay.isPresent());
        mPassengerZoneInfo = anyPassengerZoneAndDisplay.get().first;
        mPassengerDisplay = anyPassengerZoneAndDisplay.get().second;
    }

    private void assumeTestRunAsDriver() {
        int userId = mCarOccupantZoneManager.getUserForOccupant(mDriverZoneInfo);
        assumeTrue("This test can't run with the test user as a passenger (test is running as {"
                        + ActivityManager.getCurrentUser() + "}, but driver user id is {" + userId
                        + "})",
                ActivityManager.getCurrentUser() == userId);
    }

    @Test
    @CddTest(requirements = {"TODO(b/262236403)"})
    public void testHomeKeyForAnyPassengerMainDisplay_bringsHomeForThePassengerDisplayOnly() {
        // Launches TestActivity on both driver and passenger displays.
        var intent = new Intent(mContext, TestActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        // Uses ShellPermission to launch an Activity on the different displays.
        runWithShellPermissionIdentity(() -> {
            try (ActivityScenario<TestActivity> driverActivityScenario = ActivityScenario.launch(
                    intent,
                    ActivityOptions.makeBasic().setLaunchDisplayId(mDriverDisplayId).toBundle())) {
                try (ActivityScenario<TestActivity> passengerActivityScenario =
                             ActivityScenario.launch(
                                     intent,
                                     ActivityOptions.makeBasic().setLaunchDisplayId(
                                             mPassengerDisplay.getDisplayId()).toBundle())) {

                    final var latch = new CountDownLatch(2);
                    driverActivityScenario.onActivity(unused -> latch.countDown());
                    final var passengerActivity = new AtomicReference<TestActivity>();
                    passengerActivityScenario.onActivity(a -> {
                        passengerActivity.set(a);
                        latch.countDown();
                    });
                    assertWithMessage("Waited for TestActivity to start on both "
                            + "driver and passenger displays.").that(
                            latch.await(ACTIVITY_WAIT_TIME_OUT_MS, TimeUnit.MILLISECONDS)).isTrue();

                    doTestHomeKeyForAnyPassengerMainDisplay(driverActivityScenario,
                            passengerActivityScenario);
                }
            }
        });
    }

    private void doTestHomeKeyForAnyPassengerMainDisplay(
            ActivityScenario<TestActivity> driverActivityScenario,
            ActivityScenario<TestActivity> passengerActivityScenario) {

        injectKeyByShell(mPassengerZoneInfo, KeyEvent.KEYCODE_HOME);

        // Verify that driver's activity wasn't affected by the HOME key event.
        assertWithMessage("Home key should not affect the driver main display."
                + " Home key was injected to display "
                + mPassengerDisplay.getDisplayId() + ", but display "
                + mDriverDisplayId + " was affected.")
                .that(driverActivityScenario.getState()).isEqualTo(RESUMED);

        // Verify that passenger's activity was affected by the HOME key event.
        assertWithMessage("Home key should affect the passenger main display."
                + " Home key was injected to display "
                + mDriverDisplayId + ", but display wasn't affected (expected "
                + "activity state to be STARTED, but instead it was "
                + passengerActivityScenario.getState() + ")")
                .that(passengerActivityScenario.getState()).isEqualTo(STARTED);
    }

    @Test
    @CddTest(requirements = {"TODO(b/262236403)"})
    public void testBackKeyForAnyPassengerMainDisplay() {
        // Start TestActivity on passenger's display.
        var intent = new Intent(mContext, TestActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        // Uses ShellPermission to launch an Activity on the different displays.
        runWithShellPermissionIdentity(() -> {
            try (ActivityScenario<TestActivity> passengerActivityScenario =
                         ActivityScenario.launch(
                                 intent,
                                 ActivityOptions.makeBasic().setLaunchDisplayId(
                                         mPassengerDisplay.getDisplayId()).toBundle())) {
                final var latch = new CountDownLatch(1);
                final var passengerActivity = new AtomicReference<TestActivity>();
                passengerActivityScenario.onActivity(a -> {
                    passengerActivity.set(a);
                    latch.countDown();
                });
                assertWithMessage("Waited for TestActivity to start on "
                        + "passenger displays.").that(
                        latch.await(ACTIVITY_WAIT_TIME_OUT_MS, TimeUnit.MILLISECONDS)).isTrue();

                int keyCode = KeyEvent.KEYCODE_BACK;

                injectKeyByShell(mPassengerZoneInfo, keyCode);

                assertReceivedKeyCode(passengerActivity.get(), mPassengerDisplay.getDisplayId(),
                        keyCode);
            }
        });
    }

    @Test
    @CddTest(requirements = {"TODO(b/262236403)"})
    public void testAKeyForAnyPassengerMainDisplay() {
        // Start TestActivity on passenger's display.
        var intent = new Intent(mContext, TestActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        // Uses ShellPermission to launch an Activity on the different displays.
        runWithShellPermissionIdentity(() -> {
            try (ActivityScenario<TestActivity> passengerActivityScenario =
                         ActivityScenario.launch(
                                 intent,
                                 ActivityOptions.makeBasic().setLaunchDisplayId(
                                         mPassengerDisplay.getDisplayId()).toBundle())) {
                final var latch = new CountDownLatch(1);
                final var passengerActivity = new AtomicReference<TestActivity>();
                passengerActivityScenario.onActivity(a -> {
                    passengerActivity.set(a);
                    latch.countDown();
                });
                assertWithMessage("Waited for TestActivity to start on "
                        + "passenger displays.").that(
                        latch.await(ACTIVITY_WAIT_TIME_OUT_MS, TimeUnit.MILLISECONDS)).isTrue();

                int keyCode = KeyEvent.KEYCODE_A;
                injectKeyByShell(mPassengerZoneInfo, keyCode);

                assertReceivedKeyCode(passengerActivity.get(), mPassengerDisplay.getDisplayId(),
                        keyCode);
            }
        });
    }

    @Test
    @CddTest(requirements = {"TODO(b/262236403)"})
    public void testPowerKeyForAnyPassengerMainDisplay() {
        // Start TestActivity on passenger's display.
        var intent = new Intent(mContext, TestActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        // Uses ShellPermission to launch an Activity on the different displays.
        runWithShellPermissionIdentity(() -> {
            try (ActivityScenario<TestActivity> passengerActivityScenario =
                         ActivityScenario.launch(
                                 intent,
                                 ActivityOptions.makeBasic().setLaunchDisplayId(
                                         mPassengerDisplay.getDisplayId()).toBundle())) {
                final var latch = new CountDownLatch(1);
                final var passengerActivity = new AtomicReference<TestActivity>();
                passengerActivityScenario.onActivity(a -> {
                    passengerActivity.set(a);
                    latch.countDown();
                });
                assertWithMessage("Waited for TestActivity to start on "
                        + "passenger displays.").that(
                        latch.await(ACTIVITY_WAIT_TIME_OUT_MS, TimeUnit.MILLISECONDS)).isTrue();

                // Screen off
                int keyCode = KeyEvent.KEYCODE_POWER;

                injectKeyByShell(mPassengerZoneInfo, keyCode);
                PollingCheck.waitFor(DEFAULT_WAIT_MS, () -> {
                    return mPassengerDisplay.getState() == Display.STATE_OFF;
                }, "Display state should be off");

                // Screen on
                injectKeyByShell(mPassengerZoneInfo, keyCode);
                PollingCheck.waitFor(DEFAULT_WAIT_MS, () -> {
                    return mPassengerDisplay.getState() == Display.STATE_ON;
                }, "Display state should be on");
            }
        });
    }

    @Test
    @CddTest(requirements = {"TODO(b/262236403)"})
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    public void testVolumeUpKeyForAnyPassengerMainDisplay() {
        var audioManager = getCar().getCarManager(CarAudioManager.class);
        assumeTrue(audioManager.isAudioFeatureEnabled(AUDIO_FEATURE_DYNAMIC_ROUTING));

        var callback = new CarVolumeMonitor();
        audioManager.registerCarVolumeCallback(callback);

        // Start TestActivity on passenger's display.
        var intent = new Intent(mContext, TestActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        // Uses ShellPermission to launch an Activity on the different displays.
        try {
            runWithShellPermissionIdentity(() -> {
                try (ActivityScenario<TestActivity> passengerActivityScenario =
                             ActivityScenario.launch(
                                     intent,
                                     ActivityOptions.makeBasic().setLaunchDisplayId(
                                             mPassengerDisplay.getDisplayId()).toBundle())) {
                    injectKeyByShell(mPassengerZoneInfo,
                            KeyEvent.KEYCODE_VOLUME_UP);

                    assertWithMessage("CarVolumeCallback#onGroupVolumeChanged should be called")
                            .that(callback.receivedGroupVolumeChanged(
                                    mPassengerZoneInfo.zoneId))
                            .isTrue();

                    callback.reset();
                }
            });
        } finally {
            audioManager.unregisterCarVolumeCallback(callback);
        }
    }

    @Test
    @CddTest(requirements = {"TODO(b/262236403)"})
    @EnsureHasPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    public void testVolumeMuteKeyForAnyPassengerMainDisplay() {
        var audioManager = getCar().getCarManager(CarAudioManager.class);
        assumeTrue(audioManager.isAudioFeatureEnabled(AUDIO_FEATURE_DYNAMIC_ROUTING));

        var callback = new CarVolumeMonitor();
        audioManager.registerCarVolumeCallback(callback);

        // Start TestActivity on passenger's display.
        var intent = new Intent(mContext, TestActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        // Uses ShellPermission to launch an Activity on the different displays.
        try {
            runWithShellPermissionIdentity(() -> {
                try (ActivityScenario<TestActivity> passengerActivityScenario =
                             ActivityScenario.launch(
                                     intent,
                                     ActivityOptions.makeBasic().setLaunchDisplayId(
                                             mPassengerDisplay.getDisplayId()).toBundle())) {
                    injectKeyByShell(mPassengerZoneInfo, KeyEvent.KEYCODE_VOLUME_MUTE);

                    assertWithMessage("CarVolumeCallback#onMasterMuteChanged should be called")
                            .that(callback.receivedGroupMuteChanged(mPassengerZoneInfo.zoneId))
                            .isTrue();
                    assertThat(
                            audioManager.isVolumeGroupMuted(mPassengerZoneInfo.zoneId,
                                    callback.mGroupId))
                            .isTrue();
                    callback.reset();
                }
            });
        } finally {
            audioManager.unregisterCarVolumeCallback(callback);
        }
    }

    @Test
    @CddTest(requirements = {"TODO(b/262236403)"})
    public void testSingleTouchForAnyPassengerMainDisplay() {
        // Start activity on both driver and passenger displays.
        var intent = new Intent(mContext, TestActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        // Uses ShellPermission to launch an Activity on the different displays.
        runWithShellPermissionIdentity(() -> {
            try (ActivityScenario<TestActivity> driverActivityScenario = ActivityScenario.launch(
                    intent,
                    ActivityOptions.makeBasic().setLaunchDisplayId(
                            mDriverDisplayId).toBundle())) {
                try (ActivityScenario<TestActivity> passengerActivityScenario =
                             ActivityScenario.launch(
                                     intent,
                                     ActivityOptions.makeBasic().setLaunchDisplayId(
                                             mPassengerDisplay.getDisplayId()).toBundle())) {

                    final var latch = new CountDownLatch(2);
                    final var driverActivity = new AtomicReference<TestActivity>();
                    driverActivityScenario.onActivity(a -> {
                        driverActivity.set(a);
                        latch.countDown();
                    });
                    final var passengerActivity = new AtomicReference<TestActivity>();
                    passengerActivityScenario.onActivity(a -> {
                        passengerActivity.set(a);
                        latch.countDown();
                    });
                    assertWithMessage("Waited for TestActivity to start on both "
                            + "driver and passenger displays.").that(
                            latch.await(ACTIVITY_WAIT_TIME_OUT_MS, TimeUnit.MILLISECONDS)).isTrue();

                    doTestSingleTouchForAnyPassenger(driverActivity.get(), passengerActivity.get());
                }
            }
        });
    }

    private void doTestSingleTouchForAnyPassenger(
            TestActivity driverActivity,
            TestActivity passengerActivity) throws InterruptedException {

        var pointer = getDisplayCenter(passengerActivity);

        injectTouchByShell(mPassengerZoneInfo, MotionEvent.ACTION_DOWN, pointer);
        assertReceivedMotionAction(passengerActivity,
                mPassengerDisplay.getDisplayId(),
                MotionEvent.ACTION_DOWN);
        driverActivity.assertNoEvents();

        pointer.offset(1, 1);
        injectTouchByShell(mPassengerZoneInfo, MotionEvent.ACTION_MOVE, pointer);
        assertReceivedMotionAction(passengerActivity,
                mPassengerDisplay.getDisplayId(),
                MotionEvent.ACTION_MOVE);
        driverActivity.assertNoEvents();

        injectTouchByShell(mPassengerZoneInfo, MotionEvent.ACTION_UP, pointer);
        assertReceivedMotionAction(passengerActivity,
                mPassengerDisplay.getDisplayId(),
                MotionEvent.ACTION_UP);
        driverActivity.assertNoEvents();
    }

    @Test
    @CddTest(requirements = {"TODO(b/262236403)"})
    public void testMultiTouchForAnyPassengerMainDisplay() {
        // Start activity on both driver and passenger displays.
        var intent = new Intent(mContext, TestActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        // Uses ShellPermission to launch an Activity on the different displays.
        runWithShellPermissionIdentity(() -> {
            try (ActivityScenario<TestActivity> driverActivityScenario = ActivityScenario.launch(
                    intent,
                    ActivityOptions.makeBasic().setLaunchDisplayId(
                            mDriverDisplayId).toBundle())) {

                try (ActivityScenario<TestActivity> passengerActivityScenario =
                             ActivityScenario.launch(
                                     intent,
                                     ActivityOptions.makeBasic().setLaunchDisplayId(
                                             mPassengerDisplay.getDisplayId()).toBundle())) {

                    final var latch = new CountDownLatch(2);
                    final var driverActivity = new AtomicReference<TestActivity>();
                    driverActivityScenario.onActivity(a -> {
                        driverActivity.set(a);
                        latch.countDown();
                    });
                    final var passengerActivity = new AtomicReference<TestActivity>();
                    passengerActivityScenario.onActivity(a -> {
                        passengerActivity.set(a);
                        latch.countDown();
                    });
                    assertWithMessage("Waited for TestActivity to start on both "
                            + "driver and passenger displays.").that(
                            latch.await(ACTIVITY_WAIT_TIME_OUT_MS, TimeUnit.MILLISECONDS)).isTrue();

                    doTestMultiTouchForAnyPassenger(driverActivity, passengerActivity);
                }
            }
        });
    }

    private void doTestMultiTouchForAnyPassenger(AtomicReference<TestActivity> driverActivity,
            AtomicReference<TestActivity> passengerActivity) throws InterruptedException {
        var pointer1 = getDisplayCenter(passengerActivity.get());
        var pointer2 = getDisplayCenter(passengerActivity.get());
        pointer2.offset(100, 100);
        var pointers = new Point[]{pointer1, pointer2};

        injectTouchByShell(mPassengerZoneInfo, MotionEvent.ACTION_DOWN, pointer1);
        assertReceivedMotionAction(passengerActivity.get(),
                mPassengerDisplay.getDisplayId(),
                MotionEvent.ACTION_DOWN);
        driverActivity.get().assertNoEvents();

        injectTouchByShell(mPassengerZoneInfo, MotionEvent.ACTION_POINTER_DOWN,
                pointers);
        assertReceivedMotionAction(passengerActivity.get(),
                mPassengerDisplay.getDisplayId(),
                MotionEvent.ACTION_POINTER_DOWN);
        driverActivity.get().assertNoEvents();

        pointer2.offset(1, 1);
        injectTouchByShell(mPassengerZoneInfo, MotionEvent.ACTION_MOVE, pointers);
        assertReceivedMotionAction(passengerActivity.get(),
                mPassengerDisplay.getDisplayId(),
                MotionEvent.ACTION_MOVE);
        driverActivity.get().assertNoEvents();

        injectTouchByShell(mPassengerZoneInfo, MotionEvent.ACTION_POINTER_UP, pointers);
        assertReceivedMotionAction(passengerActivity.get(),
                mPassengerDisplay.getDisplayId(),
                MotionEvent.ACTION_POINTER_UP);
        driverActivity.get().assertNoEvents();

        injectTouchByShell(mPassengerZoneInfo, MotionEvent.ACTION_UP, pointer1);
        assertReceivedMotionAction(passengerActivity.get(),
                mPassengerDisplay.getDisplayId(),
                MotionEvent.ACTION_UP);
        driverActivity.get().assertNoEvents();
    }

    private Pair<OccupantZoneInfo, Display> getDriverZoneAndDisplay() {
        var zones =
                mCarOccupantZoneManager.getAllOccupantZones().stream().filter(
                        o -> o.occupantType == OCCUPANT_TYPE_DRIVER).toList();
        assertWithMessage("Expected occupant zones to contain the driver occupant zone").that(
                zones).hasSize(1);
        var driverZone = zones.get(0);
        var display = mCarOccupantZoneManager.getDisplayForOccupant(driverZone,
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN);
        return new Pair<>(driverZone, display);
    }

    private Optional<Pair<OccupantZoneInfo, Display>> pickAnyPassengerZoneAndDisplay() {
        var zones =
                mCarOccupantZoneManager.getAllOccupantZones().stream().filter(
                        o -> o.occupantType != OCCUPANT_TYPE_DRIVER
                                && mCarOccupantZoneManager.getUserForOccupant(o)
                                != UserHandle.USER_NULL).toList();
        if (zones.isEmpty()) {
            return Optional.empty();
        }
        var passengerZone = zones.get(0);
        var display = mCarOccupantZoneManager.getDisplayForOccupant(passengerZone,
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN);
        return Optional.of(new Pair<>(passengerZone, display));
    }

    private static void injectKeyByShell(OccupantZoneInfo zone, int keyCode) {
        String command = String.format(PREFIX_INJECTING_KEY_CMD, zone.seat, keyCode);
        runShellCommand(command);
    }

    private void assertReceivedKeyCode(TestActivity passengerActivity, int passengerDisplayId,
            int keyCode) {
        var downEvent = passengerActivity.getInputEvent();
        var upEvent = passengerActivity.getInputEvent();
        assertWithMessage("Activity on display " + passengerDisplayId
                + " must receive key event, keyCode="
                + KeyEvent.keyCodeToString(keyCode))
                .that(downEvent instanceof KeyEvent).isTrue();
        assertWithMessage("Activity on display " + passengerDisplayId
                + " must receive key event, keyCode="
                + KeyEvent.keyCodeToString(keyCode))
                .that(upEvent instanceof KeyEvent).isTrue();
        assertWithMessage("Activity on display " + passengerDisplayId
                + " must receive " + KeyEvent.keyCodeToString(keyCode))
                .that(((KeyEvent) downEvent).getKeyCode()).isEqualTo(keyCode);
        assertWithMessage("Activity on display " + passengerDisplayId
                + " must receive down event, keyCode=" + KeyEvent.keyCodeToString(keyCode))
                .that(((KeyEvent) downEvent).getAction()).isEqualTo(KeyEvent.ACTION_DOWN);
        assertWithMessage("Activity on display " + passengerDisplayId
                + " must receive " + KeyEvent.keyCodeToString(keyCode))
                .that(((KeyEvent) upEvent).getKeyCode()).isEqualTo(keyCode);
        assertWithMessage("Activity on display " + passengerDisplayId
                + " must receive up event, keyCode=" + KeyEvent.keyCodeToString(keyCode))
                .that(((KeyEvent) upEvent).getAction()).isEqualTo(KeyEvent.ACTION_UP);
    }

    private void assertReceivedMotionAction(TestActivity activity, int displayId,
            int actionMasked) {
        var event = activity.getInputEvent();
        assertWithMessage("Activity on display " + displayId + " must receive motion event, action="
                + MotionEvent.actionToString(actionMasked))
                .that(event instanceof MotionEvent).isTrue();
        var motionEvent = (MotionEvent) event;
        assertWithMessage("Activity on display " + displayId
                + " must receive " + MotionEvent.actionToString(actionMasked))
                .that(motionEvent.getActionMasked()).isEqualTo(actionMasked);
    }

    private static void injectTouchByShell(OccupantZoneInfo zone, int action, Point p) {
        injectTouchByShell(zone, action, new Point[]{p});
    }

    private static void injectTouchByShell(OccupantZoneInfo zone, int action, Point[] p) {
        int pointerCount = p.length;
        if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP) {
            int index = p.length - 1;
            action = (index << MotionEvent.ACTION_POINTER_INDEX_SHIFT) + action;
        }

        // Generate a command message
        var sb = new StringBuilder()
                .append(PREFIX_INJECTING_MOTION_CMD)
                .append(' ').append(OPTION_SEAT)
                .append(' ').append(zone.seat)
                .append(' ').append(OPTION_ACTION)
                .append(' ').append(action)
                .append(' ').append(OPTION_COUNT)
                .append(' ').append(pointerCount);
        sb.append(' ').append(OPTION_POINTER_ID);
        for (int i = 0; i < pointerCount; i++) {
            sb.append(' ');
            sb.append(i);
        }
        for (int i = 0; i < pointerCount; i++) {
            sb.append(' ');
            sb.append(p[i].x);
            sb.append(' ');
            sb.append(p[i].y);
        }
        runShellCommand(sb.toString());
    }

    private Point getDisplayCenter(TestActivity activity) {
        var rect = activity.getWindowManager().getCurrentWindowMetrics().getBounds();
        return new Point(rect.width() / 2, rect.height() / 2);
    }

    public static class TestActivity extends Activity {
        private LinkedBlockingQueue<InputEvent> mEvents = new LinkedBlockingQueue<>();
        public boolean mPaused = false;

        @Override
        protected void onPause() {
            super.onPause();
            mPaused = true;
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            mEvents.add(MotionEvent.obtain(ev));
            return true;
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            mEvents.add(new KeyEvent(event));
            return true;
        }

        public InputEvent getInputEvent() {
            try {
                return mEvents.poll(DEFAULT_WAIT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }

        public void assertNoEvents() throws InterruptedException {
            InputEvent event = mEvents.poll(NO_EVENT_WAIT_MS, TimeUnit.MILLISECONDS);
            assertWithMessage("Expected no events, but received %s", event).that(
                    event).isNull();
        }
    }

    private static final class CarVolumeMonitor extends CarVolumeCallback {
        // Copied from {@link android.car.CarOccupantZoneManager.OccupantZoneInfo#INVALID_ZONE_ID}
        private static final int INVALID_ZONE_ID = -1;
        // Copied from {@link android.car.media.CarAudioManager#INVALID_VOLUME_GROUP_ID}
        private static final int INVALID_VOLUME_GROUP_ID = -1;
        private final Object mLock = new Object();
        @GuardedBy("mLock")
        private CountDownLatch mGroupVolumeChangeLatch = new CountDownLatch(1);
        @GuardedBy("mLock")
        private CountDownLatch mGroupMuteChangeLatch = new CountDownLatch(1);

        public int mZoneId = INVALID_ZONE_ID;
        public int mGroupId = INVALID_VOLUME_GROUP_ID;

        boolean receivedGroupVolumeChanged(int zoneId) throws InterruptedException {
            CountDownLatch countDownLatch;
            synchronized (mLock) {
                countDownLatch = mGroupVolumeChangeLatch;
            }
            boolean succeed = countDownLatch.await(DEFAULT_WAIT_MS, TimeUnit.MILLISECONDS);
            return succeed && this.mZoneId == zoneId;
        }

        boolean receivedGroupMuteChanged(int zoneId) throws InterruptedException {
            CountDownLatch countDownLatch;
            synchronized (mLock) {
                countDownLatch = mGroupMuteChangeLatch;
            }
            boolean succeed = countDownLatch.await(DEFAULT_WAIT_MS, TimeUnit.MILLISECONDS);
            return succeed && this.mZoneId == zoneId;
        }

        void reset() {
            synchronized (mLock) {
                mGroupVolumeChangeLatch = new CountDownLatch(1);
                mGroupMuteChangeLatch = new CountDownLatch(1);
                mZoneId = INVALID_ZONE_ID;
                mGroupId = INVALID_VOLUME_GROUP_ID;
            }
        }

        @Override
        public void onGroupVolumeChanged(int zoneId, int groupId, int flags) {
            synchronized (mLock) {
                mZoneId = zoneId;
                mGroupId = groupId;
                mGroupVolumeChangeLatch.countDown();
            }
        }

        @Override
        public void onGroupMuteChanged(int zoneId, int groupId, int flags) {
            synchronized (mLock) {
                mZoneId = zoneId;
                mGroupId = groupId;
                mGroupMuteChangeLatch.countDown();
            }
        }
    }
}

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

package android.car.cts;

import static android.car.CarOccupantZoneManager.DISPLAY_TYPE_MAIN;
import static android.car.settings.CarSettings.Global.DISPLAY_POWER_MODE;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.car.hardware.power.CarPowerManager;
import android.car.hardware.power.CarPowerPolicy;
import android.car.hardware.power.CarPowerPolicyFilter;
import android.car.hardware.power.PowerComponent;
import android.car.test.PermissionsCheckerRule.EnsureHasPermission;
import android.car.view.DisplayHelper;
import android.platform.test.annotations.AppModeFull;
import android.view.Display;

import androidx.annotation.Nullable;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.PollingCheck;

import com.google.common.base.Strings;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@AppModeFull(reason = "Instant Apps cannot get car related permissions")
public final class CarPowerManagerTest extends AbstractCarTestCase {
    private static String TAG = CarPowerManagerTest.class.getSimpleName();
    private static final int LISTENER_WAIT_TIME_MS = 1000;
    private static final int DISPLAY_WAIT_TIME_MS = 2000;
    private static final int NO_WAIT = 0;
    private static final int DISPLAY_POWER_MODE_OFF = 0;
    private static final int DISPLAY_POWER_MODE_ON = 1;
    private static final int DISPLAY_POWER_MODE_ALWAYS_ON = 2;

    private static String sDefaultDisplayPowerModeValue;

    private final Executor mExecutor = mContext.getMainExecutor();

    private CarPowerManager mCarPowerManager;
    private CarOccupantZoneManager mCarOccupantZoneManager;
    private String mInitialPowerPolicyId;

    @Before
    public void setUp() throws Exception {
        mCarPowerManager = (CarPowerManager) getCar().getCarManager(Car.POWER_SERVICE);
        mCarOccupantZoneManager = (CarOccupantZoneManager) getCar().getCarManager(
                Car.CAR_OCCUPANT_ZONE_SERVICE);
        mInitialPowerPolicyId = mCarPowerManager.getCurrentPowerPolicy().getPolicyId();
    }

    @After
    public void teardown() throws Exception {
        CarPowerPolicy policy = mCarPowerManager.getCurrentPowerPolicy();
        if (!mInitialPowerPolicyId.equals(policy.getPolicyId())) {
            applyPowerPolicyForced(mInitialPowerPolicyId);
        }
    }

    @BeforeClass
    public static void loadDefaultDisplayPowerMode() throws Exception {
        sDefaultDisplayPowerModeValue = executeShellCommand(
                "settings get global %s ", DISPLAY_POWER_MODE);
    }

    @AfterClass
    public static void restoreDisplayPowerMode() throws Exception {
        executeShellCommand(
                "settings put global %s %s", DISPLAY_POWER_MODE, sDefaultDisplayPowerModeValue);
    }

    /**
     * This test verifies 1) if the current power policy is set to applied one, 2) if power
     * component states are updated according to the policy, 3) if proper power policy change
     * listeners are invoked, 4) unrelated power policy listeners are not invoked, when a new power
     * policy is applied.
     */
    @Test
    @ApiTest(apis = {"android.car.hardware.power.CarPowerManager#addPowerPolicyListener",
            "android.car.hardware.power.CarPowerManager#removePowerPolicyListener",
            "android.car.hardware.power.CarPowerManager#applyPowerPolicy",
            "android.car.hardware.power.CarPowerManager#getCurrentPowerPolicy",
            "android.car.hardware.power.CarPowerPolicy#getPolicyId",
            "android.car.hardware.power.CarPowerPolicy#isComponentEnabled"})
    @EnsureHasPermission(Car.PERMISSION_CONTROL_CAR_POWER_POLICY)
    public void testApplyNewPowerPolicy() throws Exception {
        PowerPolicyListenerImpl listenerAudioOne = new PowerPolicyListenerImpl();
        PowerPolicyListenerImpl listenerAudioTwo = new PowerPolicyListenerImpl();
        PowerPolicyListenerImpl listenerWifi = new PowerPolicyListenerImpl();
        PowerPolicyListenerImpl listenerLocation = new PowerPolicyListenerImpl();
        CarPowerPolicyFilter filterAudio = new CarPowerPolicyFilter.Builder()
                .setComponents(PowerComponent.AUDIO).build();
        CarPowerPolicyFilter filterWifi = new CarPowerPolicyFilter.Builder()
                .setComponents(PowerComponent.WIFI).build();
        CarPowerPolicyFilter filterLocation = new CarPowerPolicyFilter.Builder()
                .setComponents(PowerComponent.LOCATION).build();
        String policyId = "audio_on_wifi_off";

        definePowerPolicy(policyId, "AUDIO", "WIFI");
        mCarPowerManager.addPowerPolicyListener(mExecutor, filterAudio, listenerAudioOne);
        mCarPowerManager.addPowerPolicyListener(mExecutor, filterAudio, listenerAudioTwo);
        mCarPowerManager.addPowerPolicyListener(mExecutor, filterWifi, listenerWifi);
        mCarPowerManager.addPowerPolicyListener(mExecutor, filterLocation, listenerLocation);
        mCarPowerManager.removePowerPolicyListener(listenerAudioTwo);

        mCarPowerManager.applyPowerPolicy(policyId);

        CarPowerPolicy policy = mCarPowerManager.getCurrentPowerPolicy();
        assertWithMessage("Current power policy").that(policy).isNotNull();
        expectWithMessage("Current power policy ID").that(policy.getPolicyId()).isEqualTo(policyId);
        expectWithMessage("AUDIO component enabled status")
                .that(policy.isComponentEnabled(PowerComponent.AUDIO)).isTrue();
        expectWithMessage("WIFI component enabled status")
                .that(policy.isComponentEnabled(PowerComponent.WIFI)).isFalse();
        expectWithMessage("Added audio listener's current policy ID")
                .that(listenerAudioOne.getCurrentPolicyId(LISTENER_WAIT_TIME_MS))
                .isEqualTo(policyId);
        makeSureExecutorReady();
        expectWithMessage("Removed audio listener's current policy")
                .that(listenerAudioTwo.getCurrentPolicyId(NO_WAIT)).isNull();
        expectWithMessage("Added Wifi listener's current policy ID")
                .that(listenerWifi.getCurrentPolicyId(LISTENER_WAIT_TIME_MS)).isEqualTo(policyId);
        makeSureExecutorReady();
        expectWithMessage("Added location listener's current policy")
                .that(listenerLocation.getCurrentPolicyId(NO_WAIT)).isNull();
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.power.CarPowerManager#applyPowerPolicy"})
    @EnsureHasPermission(Car.PERMISSION_CONTROL_CAR_POWER_POLICY)
    public void testApplyPowerPolicy_nullPolicyId() {
        assertThrows(IllegalArgumentException.class,
                () -> mCarPowerManager.applyPowerPolicy(null));
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.power.CarPowerManager#applyPowerPolicy"})
    @EnsureHasPermission(Car.PERMISSION_CONTROL_CAR_POWER_POLICY)
    public void testApplyPowerPolicy_notDefinedPolicy() {
        assertThrows(IllegalArgumentException.class,
                () -> mCarPowerManager.applyPowerPolicy("not_defined_policy_id"));
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.power.CarPowerManager#applyPowerPolicy"})
    @EnsureHasPermission(Car.PERMISSION_CONTROL_CAR_POWER_POLICY)
    public void testApplyPowerPolicy_systemPowerPolicy() {
        assertThrows(IllegalArgumentException.class,
                () -> mCarPowerManager.applyPowerPolicy("system_power_policy_no_user_interaction"));
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.power.CarPowerManager#setPowerPolicyGroup"})
    @EnsureHasPermission(Car.PERMISSION_CONTROL_CAR_POWER_POLICY)
    public void testSetPowerPolicyGroup() throws Exception {
        String policyIdMediaOn = "power_policy_media_on";
        String policyIdMediaOff = "power_policy_media_off";
        definePowerPolicy(policyIdMediaOn, "MEDIA", "");
        definePowerPolicy(policyIdMediaOff, "", "MEDIA");
        String policyGroupId = "power_policy_group_id";
        definePowerPolicyGroup(policyGroupId, policyIdMediaOn, policyIdMediaOff);

        mCarPowerManager.setPowerPolicyGroup(policyGroupId);
        // Exception should not be thrown.
        // The power policy change based on policy group is tested by the hostside test.
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.power.CarPowerManager#setPowerPolicyGroup"})
    @EnsureHasPermission(Car.PERMISSION_CONTROL_CAR_POWER_POLICY)
    public void testSetPowerPolicyGroup_nullGroupId() {
        assertThrows(IllegalArgumentException.class,
                () -> mCarPowerManager.setPowerPolicyGroup(null));
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.power.CarPowerManager#setPowerPolicyGroup"})
    @EnsureHasPermission(Car.PERMISSION_CONTROL_CAR_POWER_POLICY)
    public void testSetPowerPolicyGroup_notDefinedGroup() {
        assertThrows(IllegalArgumentException.class,
                () -> mCarPowerManager.setPowerPolicyGroup("not_defined_policy_group"));
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.power.CarPowerManager#setDisplayPowerState"})
    @EnsureHasPermission(Car.PERMISSION_CAR_POWER)
    public void testSetDisplayPowerState_driverDisplayMustNotBeSupported() throws Exception {
        assumeTrue("No driver zone", mCarOccupantZoneManager.hasDriverZone());

        OccupantZoneInfo zoneInfo = mCarOccupantZoneManager.getOccupantZone(
                CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER, /* seat= */ 0);
        assertWithMessage("Driver zone").that(zoneInfo).isNotNull();

        Display display = mCarOccupantZoneManager.getDisplayForOccupant(
                zoneInfo, DISPLAY_TYPE_MAIN);
        assertWithMessage("Driver display").that(display).isNotNull();

        int displayId = display.getDisplayId();
        assertThrows(UnsupportedOperationException.class, () ->
                mCarPowerManager.setDisplayPowerState(displayId, false));

        assertThrows(UnsupportedOperationException.class, () ->
                mCarPowerManager.setDisplayPowerState(displayId, true));
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.power.CarPowerManager#setDisplayPowerState"})
    @EnsureHasPermission(Car.PERMISSION_CAR_POWER)
    public void testSetDisplayPowerState_passengerDisplays_modeOn() throws Exception {
        assumeTrue("No passenger zones", mCarOccupantZoneManager.hasPassengerZones());

        updateDisplayPowerModeSetting(DISPLAY_POWER_MODE_ON);

        for (OccupantZoneInfo zoneInfo : mCarOccupantZoneManager.getAllOccupantZones()) {
            if (zoneInfo.occupantType == CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER) {
                continue;
            }

            Display display = mCarOccupantZoneManager.getDisplayForOccupant(
                    zoneInfo, DISPLAY_TYPE_MAIN);
            if (display == null) {
                continue;
            }

            int displayId = display.getDisplayId();
            mCarPowerManager.setDisplayPowerState(displayId, true);
            PollingCheck.waitFor(DISPLAY_WAIT_TIME_MS, () -> {
                return display.getState() == Display.STATE_ON;
            });

            mCarPowerManager.setDisplayPowerState(displayId, false);
            PollingCheck.waitFor(DISPLAY_WAIT_TIME_MS, () -> {
                return display.getState() == Display.STATE_OFF;
            });

            mCarPowerManager.setDisplayPowerState(displayId, true);
            PollingCheck.waitFor(DISPLAY_WAIT_TIME_MS, () -> {
                return display.getState() == Display.STATE_ON;
            });
        }
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.power.CarPowerManager#setDisplayPowerState"})
    @EnsureHasPermission(Car.PERMISSION_CAR_POWER)
    public void testSetDisplayPowerState_passengerDisplays_modeOff() throws Exception {
        assumeTrue("No passenger zones", mCarOccupantZoneManager.hasPassengerZones());

        updateDisplayPowerModeSetting(DISPLAY_POWER_MODE_OFF);

        for (OccupantZoneInfo zoneInfo : mCarOccupantZoneManager.getAllOccupantZones()) {
            if (zoneInfo.occupantType == CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER) {
                continue;
            }

            Display display = mCarOccupantZoneManager.getDisplayForOccupant(
                    zoneInfo, DISPLAY_TYPE_MAIN);
            if (display == null) {
                continue;
            }

            int displayId = display.getDisplayId();
            mCarPowerManager.setDisplayPowerState(displayId, true);
            PollingCheck.waitFor(DISPLAY_WAIT_TIME_MS, () -> {
                return display.getState() == Display.STATE_OFF;
            });

            mCarPowerManager.setDisplayPowerState(displayId, false);
            PollingCheck.waitFor(DISPLAY_WAIT_TIME_MS, () -> {
                return display.getState() == Display.STATE_OFF;
            });

            mCarPowerManager.setDisplayPowerState(displayId, true);
            PollingCheck.waitFor(DISPLAY_WAIT_TIME_MS, () -> {
                return display.getState() == Display.STATE_OFF;
            });
        }
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.power.CarPowerManager#setDisplayPowerState"})
    @EnsureHasPermission(Car.PERMISSION_CAR_POWER)
    public void testSetDisplayPowerState_passengerDisplays_modeAlwaysOn() throws Exception {
        assumeTrue("No passenger zones", mCarOccupantZoneManager.hasPassengerZones());

        updateDisplayPowerModeSetting(DISPLAY_POWER_MODE_ALWAYS_ON);

        for (OccupantZoneInfo zoneInfo : mCarOccupantZoneManager.getAllOccupantZones()) {
            if (zoneInfo.occupantType == CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER) {
                continue;
            }

            Display display = mCarOccupantZoneManager.getDisplayForOccupant(
                    zoneInfo, DISPLAY_TYPE_MAIN);
            if (display == null) {
                continue;
            }

            int displayId = display.getDisplayId();
            mCarPowerManager.setDisplayPowerState(displayId, true);
            PollingCheck.waitFor(DISPLAY_WAIT_TIME_MS, () -> {
                return display.getState() == Display.STATE_ON;
            });

            mCarPowerManager.setDisplayPowerState(displayId, false);
            PollingCheck.waitFor(DISPLAY_WAIT_TIME_MS, () -> {
                return display.getState() == Display.STATE_OFF;
            });

            mCarPowerManager.setDisplayPowerState(displayId, true);
            PollingCheck.waitFor(DISPLAY_WAIT_TIME_MS, () -> {
                return display.getState() == Display.STATE_ON;
            });
        }
    }

    private void updateDisplayPowerModeSetting(int mode) throws Exception {
        StringBuilder value = new StringBuilder();

        for (OccupantZoneInfo zoneInfo : mCarOccupantZoneManager.getAllOccupantZones()) {
            Display display = mCarOccupantZoneManager.getDisplayForOccupant(
                    zoneInfo, DISPLAY_TYPE_MAIN);
            int displayPort = DisplayHelper.getPhysicalPort(display);
            if (displayPort == DisplayHelper.INVALID_PORT) {
                continue;
            }
            int newMode = mode;
            if (zoneInfo.occupantType == CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER) {
                newMode = DISPLAY_POWER_MODE_ALWAYS_ON;
            }
            if (value.length() != 0) {
                value.append(",");
            }
            value.append(displayPort).append(":").append(newMode);
        }

        executeShellCommand(
                "settings put global %s %s", DISPLAY_POWER_MODE, value.toString());
    }

    private void makeSureExecutorReady() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        mExecutor.execute(() -> {
            latch.countDown();
        });
        latch.await();
    }

    private static void definePowerPolicy(String policyId, String enabledComponents,
            String disabledComponents) throws Exception {
        String command = "cmd car_service define-power-policy " + policyId;
        if (!Strings.isNullOrEmpty(enabledComponents)) {
            command += " --enable " + enabledComponents;
        }
        if (!Strings.isNullOrEmpty(disabledComponents)) {
            command += " --disable " + disabledComponents;
        }
        executeShellCommand(command);
    }

    // bypasses the check that the policy isn't a system power policy
    private static void applyPowerPolicyForced(String policyId) throws Exception {
        executeShellCommand("cmd car_service apply-power-policy %s", policyId);
    }

    private static void definePowerPolicyGroup(String policyGroupId, String waitForVhalPolicyId,
            String onPolicyId) throws Exception {
        executeShellCommand("cmd car_service define-power-policy-group %s WaitForVHAL:%s On:%s",
                policyGroupId, waitForVhalPolicyId, onPolicyId);
    }

    private final class PowerPolicyListenerImpl implements
            CarPowerManager.CarPowerPolicyListener {

        private final CountDownLatch mLatch = new CountDownLatch(1);
        private String mCurrentPolicyId;

        @Override
        public void onPolicyChanged(CarPowerPolicy policy) {
            mCurrentPolicyId = policy.getPolicyId();
            mLatch.countDown();
        }

        @Nullable
        public String getCurrentPolicyId(long waitTimeMs) throws Exception {
            if (mLatch.await(waitTimeMs, TimeUnit.MILLISECONDS)) {
                return mCurrentPolicyId;
            }
            return null;
        }
    }
}

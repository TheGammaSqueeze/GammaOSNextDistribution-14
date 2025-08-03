/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.car.apitest;

import android.car.Car;
import android.car.CarAppFocusManager;
import android.car.CarBugreportManager;
import android.car.CarInfoManager;
import android.car.CarOccupantZoneManager;
import android.car.CarProjectionManager;
import android.car.CarRemoteDeviceManager;
import android.car.admin.CarDevicePolicyManager;
import android.car.app.CarActivityManager;
import android.car.app.CarDisplayCompatManager;
import android.car.cluster.CarInstrumentClusterManager;
import android.car.cluster.ClusterHomeManager;
import android.car.content.pm.CarPackageManager;
import android.car.diagnostic.CarDiagnosticManager;
import android.car.drivingstate.CarDrivingStateManager;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.car.evs.CarEvsManager;
import android.car.feature.Flags;
import android.car.hardware.CarSensorManager;
import android.car.hardware.CarVendorExtensionManager;
import android.car.hardware.cabin.CarCabinManager;
import android.car.hardware.hvac.CarHvacManager;
import android.car.hardware.power.CarPowerManager;
import android.car.hardware.property.CarPropertyManager;
import android.car.input.CarInputManager;
import android.car.media.CarAudioManager;
import android.car.media.CarMediaManager;
import android.car.navigation.CarNavigationStatusManager;
import android.car.occupantawareness.OccupantAwarenessManager;
import android.car.occupantconnection.CarOccupantConnectionManager;
import android.car.os.CarPerformanceManager;
import android.car.storagemonitoring.CarStorageMonitoringManager;
import android.car.telemetry.CarTelemetryManager;
import android.car.test.CarTestManager;
import android.car.user.CarUserManager;
import android.car.user.ExperimentalCarUserManager;
import android.car.vms.VmsClientManager;
import android.car.vms.VmsSubscriberManager;
import android.car.watchdog.CarWatchdogManager;
import android.car.wifi.CarWifiManager;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(Parameterized.class)
public final class CarGetCarManagerTest extends CarLessApiTestBase {

    private final Class<?> mCarManagerClass;
    private final String mCarServiceName;

    public CarGetCarManagerTest(Class<?> managerClass, String serviceName) {
        this.mCarManagerClass = managerClass;
        this.mCarServiceName = serviceName;
    }

    @Parameterized.Parameters
    public static List<Object[]> inputParameters() {
        List<Object[]> data = new ArrayList<>(Arrays.asList(new Object[][] {
            {CarSensorManager.class, Car.SENSOR_SERVICE},
            {CarInfoManager.class, Car.INFO_SERVICE},
            {CarAppFocusManager.class, Car.APP_FOCUS_SERVICE},
            {CarPackageManager.class, Car.PACKAGE_SERVICE},
            {CarAudioManager.class, Car.AUDIO_SERVICE},
            {CarNavigationStatusManager.class, Car.CAR_NAVIGATION_SERVICE},
            {CarOccupantConnectionManager.class, Car.CAR_OCCUPANT_CONNECTION_SERVICE},
            {CarOccupantZoneManager.class, Car.CAR_OCCUPANT_ZONE_SERVICE},
            {CarUserManager.class, Car.CAR_USER_SERVICE},
            {CarRemoteDeviceManager.class, Car.CAR_REMOTE_DEVICE_SERVICE},
            {CarDevicePolicyManager.class, Car.CAR_DEVICE_POLICY_SERVICE},
            {CarCabinManager.class, Car.CABIN_SERVICE},
            {CarDiagnosticManager.class, Car.DIAGNOSTIC_SERVICE},
            {CarHvacManager.class, Car.HVAC_SERVICE},
            {CarPowerManager.class, Car.POWER_SERVICE},
            {CarProjectionManager.class, Car.PROJECTION_SERVICE},
            {CarPropertyManager.class, Car.PROPERTY_SERVICE},
            {CarVendorExtensionManager.class, Car.VENDOR_EXTENSION_SERVICE},
            {VmsClientManager.class, Car.VEHICLE_MAP_SERVICE},
            {VmsSubscriberManager.class, Car.VMS_SUBSCRIBER_SERVICE},
            {CarDrivingStateManager.class, Car.CAR_DRIVING_STATE_SERVICE},
            {CarUxRestrictionsManager.class, Car.CAR_UX_RESTRICTION_SERVICE},
            {CarMediaManager.class, Car.CAR_MEDIA_SERVICE},
            {CarBugreportManager.class, Car.CAR_BUGREPORT_SERVICE},
            {CarStorageMonitoringManager.class, Car.STORAGE_MONITORING_SERVICE},
            {CarWatchdogManager.class, Car.CAR_WATCHDOG_SERVICE},
            {CarPerformanceManager.class, Car.CAR_PERFORMANCE_SERVICE},
            {CarInputManager.class, Car.CAR_INPUT_SERVICE},
            {ClusterHomeManager.class, Car.CLUSTER_HOME_SERVICE},
            {CarTestManager.class, Car.TEST_SERVICE},
            {CarEvsManager.class, Car.CAR_EVS_SERVICE},
            {CarTelemetryManager.class, Car.CAR_TELEMETRY_SERVICE},
            {ExperimentalCarUserManager.class, Car.EXPERIMENTAL_CAR_USER_SERVICE},
            {CarInstrumentClusterManager.class, Car.CAR_INSTRUMENT_CLUSTER_SERVICE},
            {OccupantAwarenessManager.class, Car.OCCUPANT_AWARENESS_SERVICE},
            {CarActivityManager.class, Car.CAR_ACTIVITY_SERVICE}
        }));

        if (Flags.persistApSettings()) {
            data.add(new Object[]{
                    CarWifiManager.class, Car.CAR_WIFI_SERVICE
            });
        }

        if (Flags.displayCompatibility()) {
            data.add(new Object[] {
                    CarDisplayCompatManager.class, Car.CAR_DISPLAY_COMPAT_SERVICE
            });
        }

        return data;
    }

    @Test
    @ApiTest(apis = {"android.car.Car#getCarManager(String)",
             "android.car.Car#getCarManager(Class)"})
    public void test_forCarServiceManager() throws Exception {
        Car car = Car.createCar(mContext);

        Object carManager = car.getCarManager(mCarServiceName);
        Object carManager2 = car.getCarManager(mCarServiceName);
        Object carManagerByClass = car.getCarManager(mCarManagerClass);
        Object carManagerByClass2 = car.getCarManager(mCarManagerClass);

        boolean featureEnabled = car.getAllEnabledFeatures().contains(mCarServiceName);
        if (featureEnabled) {
            expectWithMessage("first instance by service name: %s", mCarServiceName)
                    .that(carManager).isNotNull();

            expectWithMessage("second instance by service name: %s", mCarServiceName)
                    .that(carManager2).isNotNull();
            expectWithMessage("second instance by service name: %s", mCarServiceName)
                    .that(carManager2).isSameInstanceAs(carManager);

            expectWithMessage("first instance by class: %s", mCarManagerClass.getSimpleName())
                    .that(carManagerByClass).isNotNull();
            expectWithMessage("first instance by class: %s", mCarManagerClass.getSimpleName())
                    .that(carManagerByClass).isSameInstanceAs(carManager);

            expectWithMessage("second instance by class: %s", mCarManagerClass.getSimpleName())
                    .that(carManagerByClass2).isNotNull();
            expectWithMessage("second instance by class: %s", mCarManagerClass.getSimpleName())
                    .that(carManagerByClass2).isSameInstanceAs(carManager);
        } else {
            expectWithMessage("first instance by service name: %s", mCarServiceName)
                    .that(carManager).isNull();
            expectWithMessage("second instance by service name: %s", mCarServiceName)
                    .that(carManager2).isNull();

            expectWithMessage("first instance by class: %s", mCarManagerClass.getSimpleName())
                    .that(carManagerByClass).isNull();
            expectWithMessage("second instance by class: %s", mCarManagerClass.getSimpleName())
                    .that(carManagerByClass2).isNull();
        }
    }
}

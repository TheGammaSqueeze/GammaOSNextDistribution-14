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

package android.car.cts.app.watchdog;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;

import android.app.UiAutomation;
import android.car.Car;
import android.car.watchdog.CarWatchdogManager;
import android.car.watchdog.IoOveruseAlertThreshold;
import android.car.watchdog.IoOveruseConfiguration;
import android.car.watchdog.PackageKillableState;
import android.car.watchdog.PerStateBytes;
import android.car.watchdog.ResourceOveruseConfiguration;
import android.car.watchdog.ResourceOveruseStats;
import android.content.Context;
import android.os.Parcel;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Device app test for CarWatchdog CTS host side test.
 */
@RunWith(AndroidJUnit4.class)
public final class CarWatchdogDeviceAppTest {
    private static final String TAG = CarWatchdogDeviceAppTest.class.getSimpleName();

    public static final String PRIORITIZE_APP_PERFORMANCE_TEXT =
            "'Prioritize app performance' app settings is used to determine whether or not app "
                    + "performance should be prioritized over system stability or long-term "
                    + "hardware stability.";
    public static final String SETTING_TOGGLED_ON = "Setting toggled on";
    public static final String SETTING_TOGGLED_OFF = "Setting toggled off";
    public static final String SETTING_DISABLED = "Setting disabled";

    private static final long TEN_MEGABYTES = 1024 * 1024 * 10;
    private static final int TIMEOUT_MS = 10_000;
    private static final int WATCHDOG_IO_EVENT_SYNC_DELAY_MS = 4000;
    private static final int SYSTEM = 1;
    private static final int VENDOR = 2;
    private static final int THIRD_PARTY = 3;

    private final Context mContext =
            InstrumentationRegistry.getInstrumentation().getContext();
    private final UiAutomation mUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();
    private final String mPackageName = mContext.getPackageName();
    private final UserHandle mUserHandle = mContext.getUser();

    private Car mCar;
    private CarWatchdogManager mCarWatchdogManager;
    private File mTestFile;

    @Before
    public void setup() throws Exception {
        mUiAutomation.adoptShellPermissionIdentity(
                Car.PERMISSION_USE_CAR_WATCHDOG,
                Car.PERMISSION_COLLECT_CAR_WATCHDOG_METRICS,
                Car.PERMISSION_CONTROL_CAR_WATCHDOG_CONFIG,
                Car.PERMISSION_CAR_POWER);
        connectToCar();

        mTestFile = mContext.getFilesDir().toPath().resolve("testfile").toFile();
        if (!mTestFile.exists()) {
            Log.d(TAG, "Creating test file: " + mTestFile.getPath());
            mTestFile = Files.createFile(mTestFile.toPath()).toFile();
        }
    }

    @After
    public void teardown() {
        mUiAutomation.dropShellPermissionIdentity();
    }

    @Test
    public void testSetPackageKillableStateAsNo() throws Exception {
        PackageKillableState prevKillableState = getPackageKillableState(mPackageName);

        assertWithMessage("%s Default 'Prioritize app performance' app settings value for the %s "
                        + "package", PRIORITIZE_APP_PERFORMANCE_TEXT, mContext.getPackageName())
                .that(toPrioritizeAppSetting(prevKillableState.getKillableState()))
                .isEqualTo(SETTING_TOGGLED_OFF);

        mCarWatchdogManager.setKillablePackageAsUser(mPackageName, mUserHandle,
                /* isKillable= */ false);

        PackageKillableState actualKillableState = getPackageKillableState(mPackageName);

        assertWithMessage("%s New 'Prioritize app performance' app settings value for the %s "
                + "package", PRIORITIZE_APP_PERFORMANCE_TEXT, mContext.getPackageName())
                .that(toPrioritizeAppSetting(actualKillableState.getKillableState()))
                .isEqualTo(SETTING_TOGGLED_ON);
    }

    @Test
    public void testVerifyPackageKillableStateAsNo() throws Exception {
        PackageKillableState killableState = getPackageKillableState(mPackageName);

        assertWithMessage("%s 'Prioritize app performance' app settings value after reboot for the "
                        + "%s package", PRIORITIZE_APP_PERFORMANCE_TEXT, mContext.getPackageName())
                .that(toPrioritizeAppSetting(killableState.getKillableState()))
                .isEqualTo(SETTING_TOGGLED_ON);
    }

    @Test
    public void testSetPackageKillableStateAsYes() throws Exception {
        mCarWatchdogManager.setKillablePackageAsUser(mPackageName, mUserHandle,
                /* isKillable= */ true);
    }

    @Test
    public void testWriteResourceOveruseConfigurationsToDisk() throws Exception {
        List<ResourceOveruseConfiguration> configurations =
                mCarWatchdogManager.getResourceOveruseConfigurations(
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO);

        writeConfigsToDisk(configurations);
    }

    @Test
    public void testSetResourceOveruseConfigurations() throws Exception {
        List<ResourceOveruseConfiguration> expectedConfigurations =
                sampleResourceOveruseConfigurations();

        mCarWatchdogManager.setResourceOveruseConfigurations(expectedConfigurations,
                CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO);

        List<ResourceOveruseConfiguration> actualConfigurations =
                mCarWatchdogManager.getResourceOveruseConfigurations(
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO);

        ResourceOveruseConfigurationSubject
                .assertWithMessage(
                        "Must return the resource overuse configurations set by the test")
                .that(actualConfigurations)
                .containsExactlyElementsIn(expectedConfigurations);
    }

    @Test
    public void testVerifyResourceOveruseConfigurationsPersisted() throws Exception {
        List<ResourceOveruseConfiguration> expectedConfigurations =
                sampleResourceOveruseConfigurations();

        List<ResourceOveruseConfiguration> actualConfigurations =
                mCarWatchdogManager.getResourceOveruseConfigurations(
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO);

        ResourceOveruseConfigurationSubject
                .assertWithMessage("Must return the resource overuse configurations set by the test"
                        + " before reboot")
                .that(actualConfigurations)
                .containsExactlyElementsIn(expectedConfigurations);
    }

    @Test
    public void testResetOriginalResourceOveruseConfigurations() throws Exception {
        try {
            List<ResourceOveruseConfiguration> expectedConfigurations = readConfigsFromDisk();

            mCarWatchdogManager.setResourceOveruseConfigurations(expectedConfigurations,
                    CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO);

            Log.i(TAG, "Reset resource overuse configurations to original configs");
        } finally {
            mTestFile.delete();
        }
    }

    @Test
    public void testVerifyInitialResourceOveruseStats() throws Exception {
        writeToDisk(TEN_MEGABYTES);

        verifyResourceOveruseStats(TEN_MEGABYTES);
    }

    @Test
    public void testVerifyResourceOveruseStatsAfterReboot() throws Exception {
        writeToDisk(TEN_MEGABYTES);

        verifyResourceOveruseStats(TEN_MEGABYTES * 2);
    }

    @Test
    public void testDeleteTestFile() throws Exception {
        boolean wasDeleted = mTestFile.delete();
        Log.i(TAG, "Test file was deleted: " + wasDeleted);
    }

    private void verifyResourceOveruseStats(long expectedWrittenBytes) throws Exception {
        ResourceOveruseStats resourceOveruseStats = mCarWatchdogManager.getResourceOveruseStats(
                CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO,
                CarWatchdogManager.STATS_PERIOD_CURRENT_DAY);

        assertWithMessage("Resource overuse stats' package name")
                .that(resourceOveruseStats.getPackageName())
                .isEqualTo(mContext.getPackageName());
        assertWithMessage("Resource overuse stats' user handle")
                .that(resourceOveruseStats.getUserHandle())
                .isEqualTo(mContext.getUser());
        assertWithMessage("Resource overuse stats' I/O overuse stats")
                .that(resourceOveruseStats.getIoOveruseStats())
                .isNotNull();
        assertWithMessage("Resource overuse stats' I/O overuse stats' total written bytes")
                .that(resourceOveruseStats.getIoOveruseStats().getTotalBytesWritten())
                .isAtLeast(expectedWrittenBytes);
    }

    private void connectToCar() throws Exception {
        if (mCar != null && mCar.isConnected()) {
            Log.d(TAG, "Disconnecting car.");
            mCar.disconnect();
            mCar = null;
        }
        CountDownLatch connectionWait = new CountDownLatch(1);
        mCar = Car.createCar(
                mContext, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT, (car, ready) -> {
                    if (ready) {
                        mCarWatchdogManager =
                                (CarWatchdogManager) car.getCarManager(Car.CAR_WATCHDOG_SERVICE);
                    }
                    connectionWait.countDown();
                });

        // wait until either the mCar service is connected or a timeout occurs
        if (!connectionWait.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("Could not connect to car after " + TimeUnit.MILLISECONDS.toSeconds(TIMEOUT_MS)
                    + " seconds.");
        }
        assertThat(mCar).isNotNull();
        assertThat(mCar.isConnected()).isTrue();
        assertThat(mCarWatchdogManager).isNotNull();
    }

    private PackageKillableState getPackageKillableState(String packageName) {
        List<PackageKillableState> killableStates =
                mCarWatchdogManager.getPackageKillableStatesAsUser(mUserHandle);
        return killableStates.stream()
                .filter((state) -> state.getPackageName().equals(packageName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Package '" + packageName + "' not found."));
    }

    private void writeConfigsToDisk(List<ResourceOveruseConfiguration> configurations)
            throws Exception {
        Parcel p = Parcel.obtain();
        try (FileOutputStream fos = new FileOutputStream(mTestFile)) {
            p.setDataPosition(0);
            p.writeParcelableList(configurations, /* flags= */ 0);
            fos.write(p.marshall());
            fos.getFD().sync();
        } finally {
            p.recycle();
        }
    }

    private void writeToDisk(long remainingBytes) throws Exception {
        long totalBytesWritten = 0;
        try (FileOutputStream fos = new FileOutputStream(mTestFile)) {
            while (remainingBytes != 0) {
                int writeBytes = (int) Math.min(Integer.MAX_VALUE,
                        Math.min(Runtime.getRuntime().freeMemory(), remainingBytes));
                fos.write(new byte[writeBytes]);
                remainingBytes -= writeBytes;
                totalBytesWritten += writeBytes;
            }
            fos.getFD().sync();
        }
        // Wait for the I/O event to propagate to the system
        Thread.sleep(WATCHDOG_IO_EVENT_SYNC_DELAY_MS);
        Log.d(TAG, "Wrote " + totalBytesWritten + " bytes to disk");
    }

    private List<ResourceOveruseConfiguration> readConfigsFromDisk() throws Exception {
        List<ResourceOveruseConfiguration> configurations = new ArrayList<>();
        List<Byte> byteList = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(mTestFile)) {
            int b;
            while ((b = fis.read()) != -1) {
                byteList.add((byte) b);
            }
        }
        if (byteList.isEmpty()) {
            fail("Saved configuration bytes list is empty.");
        }
        Log.i(TAG, "Read " + byteList.size() + " bytes from " + mTestFile.getPath());
        byte[] bytes = new byte[byteList.size()];
        for (int i = 0; i < byteList.size(); i++) {
            bytes[i] = byteList.get(i);
        }
        Parcel p = Parcel.obtain();
        p.unmarshall(bytes, 0, bytes.length);
        p.setDataPosition(0);
        p.readParcelableList(configurations, ResourceOveruseConfiguration.class.getClassLoader());
        p.recycle();
        return configurations;
    }

    private static List<ResourceOveruseConfiguration> sampleResourceOveruseConfigurations() {
        return Arrays.asList(
                sampleResourceOveruseConfigurationBuilder(SYSTEM,
                        sampleIoOveruseConfigurationBuilder(SYSTEM).build()).build(),
                sampleResourceOveruseConfigurationBuilder(VENDOR,
                        sampleIoOveruseConfigurationBuilder(VENDOR).build()).build(),
                sampleResourceOveruseConfigurationBuilder(THIRD_PARTY,
                        sampleIoOveruseConfigurationBuilder(THIRD_PARTY).build())
                        .build());
    }

    private static ResourceOveruseConfiguration.Builder sampleResourceOveruseConfigurationBuilder(
            int componentType, IoOveruseConfiguration ioOveruseConfig) {
        String prefix = toComponentTypeStr(componentType);
        List<String> safeToKill = new ArrayList<>(0);
        List<String> vendorPrefixes = new ArrayList<>(0);
        Map<String, String> pkgToAppCategory = new ArrayMap<>();
        if (componentType != THIRD_PARTY) {
            safeToKill = Arrays.asList(prefix + "_package.non_critical.A",
                    prefix + "_pkg.non_critical.B");
            pkgToAppCategory.put("system_package.non_critical.A",
                    "android.car.watchdog.app.category.MEDIA");
            pkgToAppCategory.put("vendor_package.non_critical.A",
                    "android.car.watchdog.app.category.MEDIA");
        }
        if (componentType == VENDOR) {
            vendorPrefixes = Arrays.asList(prefix + "_package", prefix + "_pkg");
        }
        ResourceOveruseConfiguration.Builder configBuilder =
                new ResourceOveruseConfiguration.Builder(componentType, safeToKill,
                        vendorPrefixes, pkgToAppCategory);
        configBuilder.setIoOveruseConfiguration(ioOveruseConfig);
        return configBuilder;
    }

    private static IoOveruseConfiguration.Builder sampleIoOveruseConfigurationBuilder(
            int componentType) {
        String prefix = toComponentTypeStr(componentType);
        PerStateBytes componentLevelThresholds = new PerStateBytes(
                /* foregroundModeBytes= */ 5368709120L, /* backgroundModeBytes= */ 5368709120L,
                /* garageModeBytes= */ 5368709120L);
        Map<String, PerStateBytes> packageSpecificThresholds = new ArrayMap<>();
        if (componentType != THIRD_PARTY) {
            packageSpecificThresholds.put(prefix + "_package.A", new PerStateBytes(
                    /* foregroundModeBytes= */ 1073741824, /* backgroundModeBytes= */ 1073741824,
                    /* garageModeBytes= */ 1073741824));
        }

        Map<String, PerStateBytes> appCategorySpecificThresholds = new ArrayMap<>();
        if (componentType == VENDOR) {
            appCategorySpecificThresholds.put(
                    ResourceOveruseConfiguration.APPLICATION_CATEGORY_TYPE_MEDIA,
                    new PerStateBytes(/* foregroundModeBytes= */ 3221225472L,
                            /* backgroundModeBytes= */3221225472L,
                            /* garageModeBytes= */ 3221225472L));
            appCategorySpecificThresholds.put(
                    ResourceOveruseConfiguration.APPLICATION_CATEGORY_TYPE_MAPS,
                    new PerStateBytes(/* foregroundModeBytes= */ 3221225472L,
                            /* backgroundModeBytes= */ 3221225472L,
                            /* garageModeBytes= */ 3221225472L));
        }

        List<IoOveruseAlertThreshold> systemWideThresholds = new ArrayList<>();
        if (componentType == SYSTEM) {
            systemWideThresholds = Collections.singletonList(
                    new IoOveruseAlertThreshold(/* durationInSeconds= */ 100,
                            /* writtenBytesPerSecond= */ 2000));
        }

        return new IoOveruseConfiguration.Builder(componentLevelThresholds,
                packageSpecificThresholds, appCategorySpecificThresholds, systemWideThresholds);
    }

    private static String toComponentTypeStr(int componentType) {
        switch(componentType) {
            case SYSTEM:
                return "system";
            case VENDOR:
                return "vendor";
            case THIRD_PARTY:
                return "third_party";
            default:
                return "unknown";
        }
    }

    private static String toPrioritizeAppSetting(int killableState) {
        switch (killableState) {
            case PackageKillableState.KILLABLE_STATE_YES:
                return SETTING_TOGGLED_OFF;
            case PackageKillableState.KILLABLE_STATE_NO:
                return SETTING_TOGGLED_ON;
            case PackageKillableState.KILLABLE_STATE_NEVER:
            default:
                return SETTING_DISABLED;
        }
    }
}

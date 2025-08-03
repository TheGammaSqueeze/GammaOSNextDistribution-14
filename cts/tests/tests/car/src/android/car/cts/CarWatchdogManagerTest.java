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

import static android.car.settings.CarSettings.Secure.KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import android.app.UiAutomation;
import android.car.Car;
import android.car.cts.utils.watchdog.IoOveruseConfigurationSubject;
import android.car.cts.utils.watchdog.ResourceOveruseConfigurationSubject;
import android.car.watchdog.CarWatchdogManager;
import android.car.watchdog.IoOveruseAlertThreshold;
import android.car.watchdog.IoOveruseConfiguration;
import android.car.watchdog.IoOveruseStats;
import android.car.watchdog.PackageKillableState;
import android.car.watchdog.PerStateBytes;
import android.car.watchdog.ResourceOveruseConfiguration;
import android.car.watchdog.ResourceOveruseStats;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.PollingCheck;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@AppModeFull(reason = "Instant Apps cannot get car related permissions")
public final class CarWatchdogManagerTest extends AbstractCarTestCase {
    private static final String TAG = CarWatchdogManagerTest.class.getSimpleName();
    // Critical wait time for watchdog to ping service.
    private static final int HEALTH_CHECK_CRITICAL_TIMEOUT_MS = 3000;
    // Emulator must be CTS compliant, but given its slower performance wait for 10
    // times the critical timeout during health status check. Non-emulator devices
    // should maintain the same wait time, ensuring performance requirements.
    private static final int ANR_WAIT_MS =
            HEALTH_CHECK_CRITICAL_TIMEOUT_MS * (isEmulator() ? 10 : 2);
    // System event performance data collections are extended for at least 30 seconds after
    // receiving the corresponding system event completion notification. During these periods
    // (on <= Android T releases), a custom collection cannot be started. Thus, retry starting
    // custom collection for at least twice this duration.
    private static final long START_CUSTOM_COLLECTION_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(60);
    private static final String START_CUSTOM_PERF_COLLECTION_CMD =
            "dumpsys android.automotive.watchdog.ICarWatchdog/default --start_perf --max_duration"
                    + " 600 --interval 1";
    private static final String STOP_CUSTOM_PERF_COLLECTION_CMD =
            "dumpsys android.automotive.watchdog.ICarWatchdog/default --stop_perf";
    private static final String RESET_RESOURCE_OVERUSE_CMD = String.format(
            "dumpsys android.automotive.watchdog.ICarWatchdog/default "
                    + "--reset_resource_overuse_stats %s",
            InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName());
    private static final String START_CUSTOM_COLLECTION_SUCCESS_MSG =
            "Successfully started custom perf collection";
    public static final String PACKAGES_DISABLED_ON_RESOURCE_OVERUSE_SEPARATOR = ";";
    private static final long FIVE_HUNDRED_KILOBYTES = 1024 * 500;
    private static final long ONE_MEGABYTE = 1024 * 1024;
    // Wait time to sync I/O stats from proc fs -> watchdog daemon -> CarService.
    private static final int STATS_SYNC_WAIT_MS = 5000;

    private final ResourceOveruseStatsPollingCheckCondition
            mResourceOveruseStatsPollingCheckCondition =
            new ResourceOveruseStatsPollingCheckCondition();
    private final UiAutomation mUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();

    private Context mContext;
    private String mPackageName;
    private UserHandle mUserHandle;
    private CarWatchdogManager mCarWatchdogManager;
    private File mFile;

    @Before
    public void setUp() throws Exception {
        mUiAutomation.adoptShellPermissionIdentity(Car.PERMISSION_USE_CAR_WATCHDOG,
                                                   Car.PERMISSION_COLLECT_CAR_WATCHDOG_METRICS,
                                                   Car.PERMISSION_CONTROL_CAR_WATCHDOG_CONFIG);
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mPackageName = mContext.getPackageName();
        mUserHandle = UserHandle.getUserHandleForUid(Process.myUid());
        mFile = mContext.getFilesDir();
        mCarWatchdogManager = (CarWatchdogManager) getCar().getCarManager(Car.CAR_WATCHDOG_SERVICE);
    }

    @After
    public void tearDown() {
        mUiAutomation.dropShellPermissionIdentity();
    }

    @Test
    public void testCheckHealthStatus() throws Exception {
        CountDownLatch callSignal = new CountDownLatch(1);
        CarWatchdogManager.CarWatchdogClientCallback client =
                new CarWatchdogManager.CarWatchdogClientCallback() {
                    @Override
                    public boolean onCheckHealthStatus(int sessionId, int timeout) {
                        callSignal.countDown();
                        return true;
                    }

                    @Override
                    public void onPrepareProcessTermination() {
                        fail("Unexpected call to onPrepareProcessTermination");
                    }
                };

        mCarWatchdogManager.registerClient(mContext.getMainExecutor(), client,
                CarWatchdogManager.TIMEOUT_CRITICAL);
        boolean called = callSignal.await(ANR_WAIT_MS, TimeUnit.MILLISECONDS);
        mCarWatchdogManager.unregisterClient(client);

        assertWithMessage("onCheckHealthStatus called").that(called).isTrue();
    }

    @Test
    public void testThrowsExceptionOnRegisterClientWithNullClient() {
        assertThrows(NullPointerException.class,
                () -> mCarWatchdogManager.registerClient(mContext.getMainExecutor(), null,
                        CarWatchdogManager.TIMEOUT_NORMAL));
    }

    @Test
    public void testThrowsExceptionOnRegisterClientWithNullExecutor() {
        CarWatchdogManager.CarWatchdogClientCallback client =
                new CarWatchdogManager.CarWatchdogClientCallback() {};

        assertThrows(NullPointerException.class,
                () -> mCarWatchdogManager.registerClient(null, client,
                        CarWatchdogManager.TIMEOUT_NORMAL));
    }

    @Test
    public void testThrowsExceptionOnUnregisterClientWithNullClient() {
        assertThrows(NullPointerException.class,
                () -> mCarWatchdogManager.unregisterClient(null));
    }

    @Test
    public void testTellClientAlive() throws Exception {
        AtomicReference<Integer> actualSessionId = new AtomicReference<>(-1);
        CarWatchdogManager.CarWatchdogClientCallback client =
                new CarWatchdogManager.CarWatchdogClientCallback() {
                    @Override
                    public boolean onCheckHealthStatus(int sessionId, int timeout) {
                        synchronized (actualSessionId) {
                            actualSessionId.set(sessionId);
                            actualSessionId.notifyAll();
                        }
                        return false;
                    }

                    @Override
                    public void onPrepareProcessTermination() {
                        fail("Unexpected call to onPrepareProcessTermination");
                    }
                };

        mCarWatchdogManager.registerClient(mContext.getMainExecutor(), client,
                CarWatchdogManager.TIMEOUT_CRITICAL);
        synchronized (actualSessionId) {
            actualSessionId.wait(ANR_WAIT_MS);
            mCarWatchdogManager.tellClientAlive(client, actualSessionId.get());
            // Check if onPrepareProcessTermination is called.
            actualSessionId.wait(HEALTH_CHECK_CRITICAL_TIMEOUT_MS);
        }
        mCarWatchdogManager.unregisterClient(client);
    }

    @Test
    public void testThrowsExceptionOnTellClientAliveWithNullClient() {
        assertThrows(NullPointerException.class,
                () -> mCarWatchdogManager.tellClientAlive(null, -1));
    }

    @Test
    public void testGetResourceOveruseStats() throws Exception {
        runShellCommand(RESET_RESOURCE_OVERUSE_CMD);

        startCustomCollection();

        long writtenBytes = writeToDisk(mFile, FIVE_HUNDRED_KILOBYTES);

        assertWithMessage("Failed to write data to dir '" + mFile.getAbsolutePath() + "'").that(
                writtenBytes).isGreaterThan(0L);

        mResourceOveruseStatsPollingCheckCondition.setWrittenBytes(writtenBytes);

        PollingCheck.waitFor(STATS_SYNC_WAIT_MS, mResourceOveruseStatsPollingCheckCondition);

        // Stop the custom performance collection. This resets watchdog's I/O stat collection to
        // the default interval.
        runShellCommand(STOP_CUSTOM_PERF_COLLECTION_CMD);

        ResourceOveruseStats resourceOveruseStats =
                mResourceOveruseStatsPollingCheckCondition.getResourceOveruseStats();
        IoOveruseStats ioOveruseStats = resourceOveruseStats.getIoOveruseStats();
        PerStateBytes remainingWriteBytes = ioOveruseStats.getRemainingWriteBytes();
        assertWithMessage("Package name").that(resourceOveruseStats.getPackageName())
                .isEqualTo(mContext.getPackageName());
        assertWithMessage("Total bytes written to disk").that(
                ioOveruseStats.getTotalBytesWritten()).isAtLeast(writtenBytes);
        assertWithMessage("Remaining write bytes").that(remainingWriteBytes).isNotNull();
        assertWithMessage("Remaining foreground write bytes").that(
                remainingWriteBytes.getForegroundModeBytes()).isGreaterThan(0);
        assertWithMessage("Remaining background write bytes").that(
                remainingWriteBytes.getBackgroundModeBytes()).isGreaterThan(0);
        assertWithMessage("Remaining garage mode write bytes").that(
                remainingWriteBytes.getGarageModeBytes()).isGreaterThan(0);
        assertWithMessage("Duration in seconds").that(
                ioOveruseStats.getDurationInSeconds()).isGreaterThan(0);
        assertWithMessage("Start time").that(ioOveruseStats.getStartTime()).isGreaterThan(0);
        assertWithMessage("Total overuse").that(ioOveruseStats.getTotalOveruses()).isEqualTo(0);
        assertWithMessage("Total times killed").that(
                ioOveruseStats.getTotalTimesKilled()).isEqualTo(0);
        assertWithMessage("Killable on overuse").that(
                ioOveruseStats.isKillableOnOveruse()).isTrue();
        assertWithMessage("User handle").that(resourceOveruseStats.getUserHandle()).isEqualTo(
                UserHandle.getUserHandleForUid(Process.myUid()));
    }

    @Test
    public void testGetAllResourceOveruseStats() throws Exception {
        runShellCommand(RESET_RESOURCE_OVERUSE_CMD);

        startCustomCollection();
        writeToDisk(mFile, ONE_MEGABYTE);
        AtomicReference<List<ResourceOveruseStats>> statsList = new AtomicReference<>();
        PollingCheck.check(
                "Either" + mPackageName + " stats not found or less than 2 stats found.",
                STATS_SYNC_WAIT_MS, () -> {
                    statsList.set(mCarWatchdogManager.getAllResourceOveruseStats(
                            CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO,
                            CarWatchdogManager.FLAG_MINIMUM_STATS_IO_1_MB,
                            CarWatchdogManager.STATS_PERIOD_CURRENT_DAY));
                    return statsList.get().size() > 1 && containsPackage(mPackageName,
                            statsList.get());
                });
        runShellCommand(STOP_CUSTOM_PERF_COLLECTION_CMD);

        assertWithMessage(
                "Package with non-null IoOveruseStats different than the current not found").that(
                containsPackageWithStatsDifferentThan(mPackageName, statsList.get())).isTrue();
    }

    @Test
    public void testThrowsExceptionOnGetAllResourceOveruseStatsWithInvalidResourceType() {
        assertThrows(IllegalArgumentException.class,
                () -> mCarWatchdogManager.getAllResourceOveruseStats(
                        /* resourceOveruseFlag= */ -1,
                        CarWatchdogManager.FLAG_MINIMUM_STATS_IO_1_MB,
                        CarWatchdogManager.STATS_PERIOD_CURRENT_DAY));
    }

    @Test
    public void testThrowsExceptionOnGetAllResourceOveruseStatsWithInvalidStatsPeriod() {
        assertThrows(IllegalArgumentException.class,
                () -> mCarWatchdogManager.getAllResourceOveruseStats(
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO,
                        CarWatchdogManager.FLAG_MINIMUM_STATS_IO_1_MB,
                        /* maxStatsPeriod= */ -1));
    }

    @Test
    public void testGetResourceOveruseStatsForUserPackage() throws Exception {
        runShellCommand(RESET_RESOURCE_OVERUSE_CMD);

        startCustomCollection();
        writeToDisk(mFile, FIVE_HUNDRED_KILOBYTES);
        AtomicReference<ResourceOveruseStats> stats = new AtomicReference<>();
        PollingCheck.check(
                "IoOveruseStats for " + mUserHandle + " : " + mPackageName + " not found.",
                STATS_SYNC_WAIT_MS, () -> {
                    stats.set(mCarWatchdogManager.getResourceOveruseStatsForUserPackage(
                            mPackageName, mUserHandle,
                            CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO,
                            CarWatchdogManager.STATS_PERIOD_CURRENT_DAY));
                    return stats.get().getIoOveruseStats() != null;
                });
        runShellCommand(STOP_CUSTOM_PERF_COLLECTION_CMD);

        ResourceOveruseStats actualStats = stats.get();
        IoOveruseStats ioOveruseStats = actualStats.getIoOveruseStats();
        assertWithMessage("Package name").that(
                actualStats.getPackageName()).isEqualTo(mPackageName);
        assertWithMessage("User handle").that(actualStats.getUserHandle()).isEqualTo(mUserHandle);
        assertWithMessage("Total bytes written to disk").that(
                ioOveruseStats.getTotalBytesWritten()).isAtLeast(FIVE_HUNDRED_KILOBYTES);
    }

    @Test
    public void testThrowsExceptionOnGetResourceOveruseStatsForUserPackageWithNullPackageName() {
        assertThrows(NullPointerException.class,
                () -> mCarWatchdogManager.getResourceOveruseStatsForUserPackage(
                        /* packageName= */ null, mUserHandle,
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO,
                        CarWatchdogManager.STATS_PERIOD_CURRENT_DAY));
    }

    @Test
    public void testThrowsExceptionOnGetResourceOveruseStatsForUserPackageWithNullUserHandle() {
        assertThrows(NullPointerException.class,
                () -> mCarWatchdogManager.getResourceOveruseStatsForUserPackage(
                        mPackageName, /* userHandle= */ null,
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO,
                        CarWatchdogManager.STATS_PERIOD_CURRENT_DAY));
    }

    @Test
    public void
            testThrowsExceptionOnGetResourceOveruseStatsForUserPackageWithInvalidResourceType() {
        assertThrows(IllegalArgumentException.class,
                () -> mCarWatchdogManager.getResourceOveruseStatsForUserPackage(
                        mPackageName, mUserHandle,
                        /* resourceOveruseFlag= */ -1,
                        CarWatchdogManager.STATS_PERIOD_CURRENT_DAY));
    }

    @Test
    public void testThrowsExceptionOnGetResourceOveruseStatsForUserPackageWithInvalidStatsPeriod() {
        assertThrows(IllegalArgumentException.class,
                () -> mCarWatchdogManager.getResourceOveruseStatsForUserPackage(
                        mPackageName, mUserHandle,
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO,
                        /* maxStatsPeriod= */ -1));
    }

    @Test
    public void testResourceOveruseListenerForSystem() {
        CarWatchdogManager.ResourceOveruseListener listener = resourceOveruseStats -> {
            // Do nothing
        };

        mCarWatchdogManager.addResourceOveruseListenerForSystem(mContext.getMainExecutor(),
                CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO, listener);
        mCarWatchdogManager.removeResourceOveruseListenerForSystem(listener);
    }

    @Test
    public void testThrowsExceptionOnResourceOveruseListenerForSystemWithNullListener() {
        assertThrows(NullPointerException.class,
                () -> mCarWatchdogManager.addResourceOveruseListenerForSystem(
                        mContext.getMainExecutor(), CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO,
                        /* listener= */ null));
    }

    @Test
    public void testThrowsExceptionOnResourceOveruseListenerForSystemWithNullExecutor() {
        CarWatchdogManager.ResourceOveruseListener listener = resourceOveruseStats -> {
            // Do nothing
        };

        assertThrows(NullPointerException.class,
                () -> mCarWatchdogManager.addResourceOveruseListenerForSystem(
                        /* executor= */ null, CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO,
                        listener));
    }

    @Test
    public void testThrowsExceptionOnRemoveResourceOveruseListenerForSystemWithNullListener() {
        assertThrows(NullPointerException.class,
                () -> mCarWatchdogManager
                        .removeResourceOveruseListenerForSystem(/* listener= */ null));
    }

    @Test
    public void testSetKillablePackageAsUser() {
        PackageKillableState thisKillableState = getPackageKillableState(mPackageName);
        assertWithMessage("Ats package is not KILLABLE_STATE_NEVER").that(
                thisKillableState.getKillableState()).isNotEqualTo(
                PackageKillableState.KILLABLE_STATE_NEVER);
        int prevKillableState = thisKillableState.getKillableState();
        int expectedKillableState = prevKillableState == PackageKillableState.KILLABLE_STATE_YES
                ? PackageKillableState.KILLABLE_STATE_NO
                : PackageKillableState.KILLABLE_STATE_YES;

        mCarWatchdogManager.setKillablePackageAsUser(mPackageName, mUserHandle,
                expectedKillableState == PackageKillableState.KILLABLE_STATE_YES);

        PackageKillableState actualKillableState = getPackageKillableState(mPackageName);
        assertWithMessage("Ats package new killable state").that(
                actualKillableState.getKillableState()).isEqualTo(expectedKillableState);

        // Set package back to original killable state
        mCarWatchdogManager.setKillablePackageAsUser(mPackageName, mUserHandle,
                prevKillableState == PackageKillableState.KILLABLE_STATE_YES);
        actualKillableState = getPackageKillableState(mPackageName);
        assertWithMessage("Ats package original killable state").that(
                actualKillableState.getKillableState()).isEqualTo(prevKillableState);
    }

    @Test
    public void testThrowsExceptionOnSetKillablePackageAsUserWithNullPackageName() {
        assertThrows(NullPointerException.class,
                () -> mCarWatchdogManager.setKillablePackageAsUser(/* packageName= */ null,
                                                                   mUserHandle,
                                                                   /* isKillable= */ false));
    }

    @Test
    public void testThrowsExceptionOnSetKillablePackageAsUserWithNullUserHandle() {
        assertThrows(NullPointerException.class,
                () -> mCarWatchdogManager.setKillablePackageAsUser(mPackageName,
                                                                   /* userHandle= */ null,
                                                                   /* isKillable= */ false));
    }

    @Test
    public void testThrowsExceptionOnGetKillablePackageAsUserWithNullUserHandle() {
        assertThrows(NullPointerException.class,
                () -> mCarWatchdogManager.getPackageKillableStatesAsUser(/* userHandle= */ null));
    }

    @Test
    public void testThrowsExceptionOnUnkillablePackageSetKillablePackageAsUser() {
        List<PackageKillableState> killableStates =
                mCarWatchdogManager.getPackageKillableStatesAsUser(mUserHandle);
        Optional<PackageKillableState> notKillableState = killableStates.stream().filter(
                (state) -> state.getKillableState() == PackageKillableState.KILLABLE_STATE_NEVER)
                .findFirst();
        String unkillablePackageName = notKillableState.get().getPackageName();

        assertThrows(IllegalArgumentException.class,
                () -> mCarWatchdogManager.setKillablePackageAsUser(unkillablePackageName,
                        mUserHandle, /* isKillable= */ true));
    }

    @Test
    public void testSetResourceOveruseConfigurations() {
        Map<String, String> invalidPackageToCategoryTypes = new ArrayMap<>();
        invalidPackageToCategoryTypes.put("third_party_package.pkg.A",
                ResourceOveruseConfiguration.APPLICATION_CATEGORY_TYPE_MEDIA);
        ResourceOveruseConfiguration newThirdPartyConfiguration =
                new ResourceOveruseConfiguration.Builder(
                ResourceOveruseConfiguration.COMPONENT_TYPE_THIRD_PARTY,
                Collections.singletonList("third_party_package.pkg.B"),
                Collections.singletonList("vendor_invalid_prefix"), invalidPackageToCategoryTypes)
                .setIoOveruseConfiguration(
                        new IoOveruseConfiguration.Builder(
                                new PerStateBytes(6666666, 7777777, 8888888),
                                new HashMap<>(), new HashMap<>(), new ArrayList<>()).build())
                .build();
        List<ResourceOveruseConfiguration> prevConfigurations =
                mCarWatchdogManager.getResourceOveruseConfigurations(
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO);
        List<ResourceOveruseConfiguration> expectedConfigurations = new ArrayList<>();
        for (ResourceOveruseConfiguration config : prevConfigurations) {
            if (config.getComponentType()
                    == ResourceOveruseConfiguration.COMPONENT_TYPE_THIRD_PARTY) {
                config = new ResourceOveruseConfiguration.Builder(
                        ResourceOveruseConfiguration.COMPONENT_TYPE_THIRD_PARTY, new ArrayList<>(),
                        new ArrayList<>(), new HashMap<>())
                        .setIoOveruseConfiguration(
                                newThirdPartyConfiguration.getIoOveruseConfiguration())
                        .build();
            }
            expectedConfigurations.add(config);
        }

        try {
            //  Set the resource overuse configuration
            int returnCode = mCarWatchdogManager.setResourceOveruseConfigurations(
                    Collections.singletonList(newThirdPartyConfiguration),
                    CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO);
            assertWithMessage("Return code").that(returnCode).isEqualTo(
                    CarWatchdogManager.RETURN_CODE_SUCCESS);

            //  Get the resource overuse configuration.
            List<ResourceOveruseConfiguration> actualConfigurations =
                    mCarWatchdogManager.getResourceOveruseConfigurations(
                            CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO);

            ResourceOveruseConfigurationSubject.assertThat(
                    actualConfigurations).containsExactlyElementsIn(expectedConfigurations);
        } finally {
            // Set the config to the original.
            mCarWatchdogManager.setResourceOveruseConfigurations(
                    prevConfigurations,
                    CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO);
        }
    }

    @Test
    public void testSetResourceOveruseConfigurationsWithSystemComponent() {
        Map<String, String> expectedPackageToCategoryTypes = new ArrayMap<>();
        expectedPackageToCategoryTypes.put("system_package.pkg",
                ResourceOveruseConfiguration.APPLICATION_CATEGORY_TYPE_MEDIA);
        ResourceOveruseConfiguration newSystemConfiguration =
                new ResourceOveruseConfiguration.Builder(
                ResourceOveruseConfiguration.COMPONENT_TYPE_SYSTEM,
                Collections.singletonList("system_package.non_critical"),
                Collections.singletonList("invalid_vendor_prefix"),
                expectedPackageToCategoryTypes)
                .setIoOveruseConfiguration(
                        new IoOveruseConfiguration.Builder(
                                new PerStateBytes(6666666, 7777777, 8888888),
                                new HashMap<>(), new HashMap<>(), Collections.singletonList(
                                        new IoOveruseAlertThreshold(30, 35791394)))
                        .build())
                .build();
        List<ResourceOveruseConfiguration> prevConfigurations =
                mCarWatchdogManager.getResourceOveruseConfigurations(
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO);
        List<ResourceOveruseConfiguration> expectedConfigurations = new ArrayList<>();
        for (ResourceOveruseConfiguration config : prevConfigurations) {
            if (config.getComponentType() == ResourceOveruseConfiguration.COMPONENT_TYPE_SYSTEM) {
                config = new ResourceOveruseConfiguration.Builder(
                        ResourceOveruseConfiguration.COMPONENT_TYPE_SYSTEM,
                        newSystemConfiguration.getSafeToKillPackages(), new ArrayList<>(),
                        newSystemConfiguration.getPackagesToAppCategoryTypes())
                        .setIoOveruseConfiguration(
                                newSystemConfiguration.getIoOveruseConfiguration())
                        .build();
            }
            if (config.getComponentType() == ResourceOveruseConfiguration.COMPONENT_TYPE_VENDOR) {
                config = new ResourceOveruseConfiguration.Builder(
                                ResourceOveruseConfiguration.COMPONENT_TYPE_VENDOR,
                                config.getSafeToKillPackages(), config.getVendorPackagePrefixes(),
                                expectedPackageToCategoryTypes)
                        .setIoOveruseConfiguration(config.getIoOveruseConfiguration())
                        .build();
            }
            expectedConfigurations.add(config);
        }

        try {
            //  Set the resource overuse configuration
            int returnCode = mCarWatchdogManager.setResourceOveruseConfigurations(
                    Collections.singletonList(newSystemConfiguration),
                    CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO);
            assertWithMessage("Return code").that(returnCode).isEqualTo(
                    CarWatchdogManager.RETURN_CODE_SUCCESS);

            //  Get the resource overuse configuration.
            List<ResourceOveruseConfiguration> actualConfigurations =
                    mCarWatchdogManager.getResourceOveruseConfigurations(
                            CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO);

            ResourceOveruseConfigurationSubject.assertThat(
                    actualConfigurations).containsExactlyElementsIn(expectedConfigurations);
        } finally {
            // Set the config to the original.
            mCarWatchdogManager.setResourceOveruseConfigurations(
                    prevConfigurations,
                    CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO);
        }
    }

    @Test
    public void testThrowsExceptionOnSetResourceOveruseConfigurationsWithNullConfigurations() {
        assertThrows(NullPointerException.class,
                () -> mCarWatchdogManager.setResourceOveruseConfigurations(
                        /* configurations= */ null, CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO));
    }

    @Test
    public void testThrowsExceptionOnSetResourceOveruseConfigurationsWithEmptyConfigurationList() {
        assertThrows(IllegalArgumentException.class,
                () -> mCarWatchdogManager.setResourceOveruseConfigurations(
                        /* configurations= */ new ArrayList<>(),
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO));
    }

    @Test
    public void testThrowsExceptionOnSetResourceOveruseConfigurationsWithInvalidResourceType() {
        ResourceOveruseConfiguration configuration =
                new ResourceOveruseConfiguration.Builder(
                ResourceOveruseConfiguration.COMPONENT_TYPE_SYSTEM, new ArrayList<>(),
                new ArrayList<>(), new HashMap<>()).build();

        assertThrows(IllegalArgumentException.class,
                () -> mCarWatchdogManager.setResourceOveruseConfigurations(
                        Collections.singletonList(configuration),
                        /* resourceOveruseFlag= */ -1));
    }

    @Test
    public void testThrowsExceptionOnSetResourceOveruseConfigurationsWithDuplicateComponentType() {
        ResourceOveruseConfiguration configuration1 =
                new ResourceOveruseConfiguration.Builder(
                ResourceOveruseConfiguration.COMPONENT_TYPE_SYSTEM, new ArrayList<>(),
                new ArrayList<>(), new HashMap<>()).build();

        ResourceOveruseConfiguration configuration2 =
                new ResourceOveruseConfiguration.Builder(
                ResourceOveruseConfiguration.COMPONENT_TYPE_SYSTEM, new ArrayList<>(),
                new ArrayList<>(), new HashMap<>()).build();

        assertThrows(IllegalArgumentException.class,
                () -> mCarWatchdogManager.setResourceOveruseConfigurations(
                        Arrays.asList(configuration1, configuration2),
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO));
    }

    @Test
    public void testThrowsExceptionOnSetResourceOveruseConfigurationsWithNoIoConfiguration() {
        ResourceOveruseConfiguration configuration =
                new ResourceOveruseConfiguration.Builder(
                ResourceOveruseConfiguration.COMPONENT_TYPE_SYSTEM, new ArrayList<>(),
                new ArrayList<>(), new HashMap<>()).build();

        assertThrows(IllegalArgumentException.class,
                () -> mCarWatchdogManager.setResourceOveruseConfigurations(
                        Collections.singletonList(configuration),
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO));
    }

    @Test
    public void testFailsSetSystemResourceOveruseConfigsWithZeroComponentLevelIoThresholds() {
        ResourceOveruseConfiguration configuration = new ResourceOveruseConfiguration.Builder(
                ResourceOveruseConfiguration.COMPONENT_TYPE_SYSTEM, new ArrayList<>(),
                new ArrayList<>(), new HashMap<>()).setIoOveruseConfiguration(
                        new IoOveruseConfiguration.Builder(
                                new PerStateBytes(6666666, 0, 8888888),
                                new HashMap<>(), new HashMap<>(), Collections.singletonList(
                                        new IoOveruseAlertThreshold(30, 35791394)))
                        .build())
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> mCarWatchdogManager.setResourceOveruseConfigurations(
                        Collections.singletonList(configuration),
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO));
    }

    @Test
    public void testFailsSetSystemResourceOveruseConfigurationsWithNoSystemWideThreshold() {
        ResourceOveruseConfiguration configuration =
                new ResourceOveruseConfiguration.Builder(
                ResourceOveruseConfiguration.COMPONENT_TYPE_SYSTEM, new ArrayList<>(),
                new ArrayList<>(), new HashMap<>()).setIoOveruseConfiguration(
                new IoOveruseConfiguration.Builder(
                        new PerStateBytes(6666666, 7777777, 8888888),
                        new HashMap<>(), new HashMap<>(), new ArrayList<>()).build())
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> mCarWatchdogManager.setResourceOveruseConfigurations(
                        Collections.singletonList(configuration),
                        CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO));
    }

    @Test
    public void testThrowsExceptionOnGetResourceOveruseConfigurationsWithInvalidResourceType() {
        assertThrows(IllegalArgumentException.class,
                () -> mCarWatchdogManager
                        .getResourceOveruseConfigurations(/* resourceOveruseFlag= */ -1));
    }

    @Test
    public void testResourceOveruseConfigurationBuilder() {
        ResourceOveruseConfiguration configuration = new ResourceOveruseConfiguration.Builder(
                ResourceOveruseConfiguration.COMPONENT_TYPE_THIRD_PARTY, new ArrayList<>(),
                new ArrayList<>(), new HashMap<>())
                .setComponentType(ResourceOveruseConfiguration.COMPONENT_TYPE_SYSTEM)
                .setSafeToKillPackages(List.of("system_package"))
                .setVendorPackagePrefixes(List.of("vendor"))
                .setPackagesToAppCategoryTypes(Map.of("media_package",
                        ResourceOveruseConfiguration.APPLICATION_CATEGORY_TYPE_MEDIA))
                .build();

        ResourceOveruseConfiguration expectedConfiguration =
                new ResourceOveruseConfiguration.Builder(
                        ResourceOveruseConfiguration.COMPONENT_TYPE_SYSTEM,
                        List.of("system_package"), List.of("vendor"), Map.of("media_package",
                        ResourceOveruseConfiguration.APPLICATION_CATEGORY_TYPE_MEDIA)).build();

        ResourceOveruseConfigurationSubject.assertThat(Collections.singleton(configuration))
                .containsExactlyElementsIn(Collections.singleton(expectedConfiguration));
    }

    @Test
    public void testIoOveruseConfigurationBuilder() {
        Map<String, PerStateBytes> packageSpecificThresholds = new HashMap<>();
        packageSpecificThresholds.put("system_package", new PerStateBytes(1, 2, 3));
        Map<String, PerStateBytes> appCategorySpecificThresholds = new HashMap<>();
        appCategorySpecificThresholds.put(
                ResourceOveruseConfiguration.APPLICATION_CATEGORY_TYPE_MEDIA,
                new PerStateBytes(1, 2, 3));
        IoOveruseConfiguration ioOveruseConfiguration = new IoOveruseConfiguration.Builder(
                new PerStateBytes(6666666, 7777777, 8888888),
                new HashMap<>(), new HashMap<>(), new ArrayList<>())
                .setComponentLevelThresholds(new PerStateBytes(1, 2, 3))
                .setPackageSpecificThresholds(packageSpecificThresholds)
                .addPackageSpecificThresholds("vendor_package", new PerStateBytes(1, 2, 3))
                .setAppCategorySpecificThresholds(appCategorySpecificThresholds)
                .addAppCategorySpecificThresholds(
                        ResourceOveruseConfiguration.APPLICATION_CATEGORY_TYPE_MAPS,
                        new PerStateBytes(1, 2, 3))
                .setSystemWideThresholds(
                        new ArrayList<>(Collections.singletonList(
                                new IoOveruseAlertThreshold(30, 35791394))))
                .addSystemWideThresholds(new IoOveruseAlertThreshold(100, 234934))
                .build();

        IoOveruseConfiguration expectedIoOveruseConfiguration = new IoOveruseConfiguration.Builder(
                new PerStateBytes(1, 2, 3),
                Map.of("system_package", new PerStateBytes(1, 2, 3),
                        "vendor_package", new PerStateBytes(1, 2, 3)),
                Map.of(ResourceOveruseConfiguration.APPLICATION_CATEGORY_TYPE_MEDIA,
                        new PerStateBytes(1, 2, 3),
                        ResourceOveruseConfiguration.APPLICATION_CATEGORY_TYPE_MAPS,
                        new PerStateBytes(1, 2, 3)),
                List.of(new IoOveruseAlertThreshold(30, 35791394),
                        new IoOveruseAlertThreshold(100, 234934)))
                .build();

        IoOveruseConfigurationSubject.assertThat(Collections.singleton(ioOveruseConfiguration))
                .containsExactlyElementsIn(Collections.singleton(expectedIoOveruseConfiguration));
    }

    @Test
    public void testVerifyPackagesDisabledOnResourceOveruseSettingsString() throws Exception {
        ContentResolver contentResolverForUser = mContext.createContextAsUser(mUserHandle,
                /* flags= */ 0).getContentResolver();
        ArraySet<String> packages = extractPackages(Settings.Secure.getString(
                contentResolverForUser, KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE));
        assertWithMessage("Test package name in %s", KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE)
                .that(packages).doesNotContain(mPackageName);
    }

    /**
     * Test that no exception is thrown when calling the addResourceOveruseListener and
     * removeResourceOveruseListener client APIs.
     *
     * <p>The actual notification handling and killing will
     * be tested with host side tests.
     */
    @Test
    public void testListenIoOveruse() {
        CarWatchdogManager.ResourceOveruseListener listener = resourceOveruseStats -> {
            // Do nothing
        };

        mCarWatchdogManager.addResourceOveruseListener(
                mContext.getMainExecutor(), CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO, listener);
        mCarWatchdogManager.removeResourceOveruseListener(listener);
    }

    @Test
    public void testThrowsExceptionOnNullResourceOveruseListener() {
        assertThrows(NullPointerException.class,
                () -> mCarWatchdogManager.addResourceOveruseListener(
                        mContext.getMainExecutor(), CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO,
                        /* listener= */ null));
    }

    /**
     * Starts a custom performance collection with a 1-second interval.
     *
     * <p>This enables watchdog daemon to read proc stats more frequently and reduces the test wait
     * time.
     */
    private static void startCustomCollection() throws Exception {
        if (ApiLevelUtil.isAfter(Build.VERSION_CODES.TIRAMISU)) {
            String result = runShellCommand(START_CUSTOM_PERF_COLLECTION_CMD);
            assertWithMessage("Custom collection start message").that(result)
                    .contains(START_CUSTOM_COLLECTION_SUCCESS_MSG);
            return;
        }
        // TODO(b/261869056): Remove the polling check once it is safe to remove.
        PollingCheck.check("Failed to start custom collect performance data collection",
                START_CUSTOM_COLLECTION_TIMEOUT_MS,
                () -> {
                    String result = runShellCommand(START_CUSTOM_PERF_COLLECTION_CMD);
                    return result.contains(START_CUSTOM_COLLECTION_SUCCESS_MSG) || result.isEmpty();
                });
    }

    private PackageKillableState getPackageKillableState(String packageName) {
        List<PackageKillableState> killableStates =
                mCarWatchdogManager.getPackageKillableStatesAsUser(mUserHandle);
        return killableStates.stream().filter(
                (state) -> state.getPackageName().equals(packageName)).findFirst().orElseThrow(
                RuntimeException::new);
    }

    private ArraySet<String> extractPackages(String settingsString) {
        return TextUtils.isEmpty(settingsString) ? new ArraySet<>()
                : new ArraySet<>(Arrays.asList(settingsString.split(
                        PACKAGES_DISABLED_ON_RESOURCE_OVERUSE_SEPARATOR)));
    }

    private static long writeToDisk(File dir, long size) throws Exception {
        if (!dir.exists()) {
            throw new FileNotFoundException(
                    "directory '" + dir.getAbsolutePath() + "' doesn't exist");
        }
        File uniqueFile = new File(dir, Long.toString(System.nanoTime()));
        try (FileOutputStream fos = new FileOutputStream(uniqueFile)) {
            Log.d(TAG, "Attempting to write " + size + " bytes");
            writeToFos(fos, size);
            fos.getFD().sync();
        }
        return size;
    }

    private static void writeToFos(FileOutputStream fos, long maxSize) throws IOException {
        while (maxSize != 0) {
            int writeSize = (int) Math.min(Integer.MAX_VALUE,
                    Math.min(Runtime.getRuntime().freeMemory(), maxSize));
            Log.i(TAG, "writeSize:" + writeSize);
            try {
                fos.write(new byte[writeSize]);
            } catch (InterruptedIOException e) {
                Thread.currentThread().interrupt();
                continue;
            }
            maxSize -= writeSize;
        }
    }

    private static boolean containsPackage(String packageName,
            List<ResourceOveruseStats> statsList) {
        return statsList.stream().anyMatch(
                (stats) -> stats.getPackageName().equals(packageName));
    }

    private static boolean containsPackageWithStatsDifferentThan(String packageName,
            List<ResourceOveruseStats> statsList) {
        return statsList.stream().anyMatch(
                (stats) -> !stats.getPackageName().equals(packageName)
                        && stats.getIoOveruseStats() != null);
    }

    private final class ResourceOveruseStatsPollingCheckCondition
            implements PollingCheck.PollingCheckCondition {
        private ResourceOveruseStats mResourceOveruseStats;
        private long mWrittenBytes;

        @Override
        public boolean canProceed() {
            mResourceOveruseStats = mCarWatchdogManager.getResourceOveruseStats(
                    CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO,
                    CarWatchdogManager.STATS_PERIOD_CURRENT_DAY);
            // Flash memory usage stats are polled once every one second. The syncing of stats
            // from proc fs -> watchdog daemon -> CarService can happen across multiple polling,
            // so wait until the reported stats cover the entire write size.
            IoOveruseStats ioOveruseStats = mResourceOveruseStats.getIoOveruseStats();
            return ioOveruseStats != null
                    && ioOveruseStats.getTotalBytesWritten() >= mWrittenBytes;
        }

        public ResourceOveruseStats getResourceOveruseStats() {
            return mResourceOveruseStats;
        }

        public void setWrittenBytes(long writtenBytes) {
            mWrittenBytes = writtenBytes;
        }
    };

    private static boolean isEmulator() {
        return SystemProperties.getBoolean("ro.boot.qemu", false)
                || SystemProperties.getBoolean("ro.kernel.qemu", false);
    }
}

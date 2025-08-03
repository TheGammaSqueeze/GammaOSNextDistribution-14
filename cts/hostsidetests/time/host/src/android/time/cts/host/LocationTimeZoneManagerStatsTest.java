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

package android.time.cts.host;

import static android.app.time.cts.shell.DeviceConfigKeys.NAMESPACE_SYSTEM_TIME;
import static android.app.time.cts.shell.DeviceConfigShellHelper.SYNC_DISABLED_MODE_UNTIL_REBOOT;
import static android.app.time.cts.shell.FakeTimeZoneProviderAppShellHelper.FAKE_TZPS_APP_APK;
import static android.app.time.cts.shell.FakeTimeZoneProviderAppShellHelper.FAKE_TZPS_APP_PACKAGE;

import static java.util.stream.Collectors.toList;

import android.app.time.cts.shell.DeviceConfigShellHelper;
import android.app.time.cts.shell.DeviceShellCommandExecutor;
import android.app.time.cts.shell.FakeTimeZoneProviderAppShellHelper;
import android.app.time.cts.shell.LocationShellHelper;
import android.app.time.cts.shell.LocationTimeZoneManagerShellHelper;
import android.app.time.cts.shell.TimeZoneDetectorShellHelper;
import android.app.time.cts.shell.host.HostShellCommandExecutor;
import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.os.AtomsProto;
import com.android.os.AtomsProto.LocationTimeZoneProviderStateChanged;
import com.android.os.StatsLog;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.RunUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Host-side CTS tests for the location time zone manager service stats logging. Very similar to
 * {@link LocationTimeZoneManagerHostTest} but focused on stats logging.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class LocationTimeZoneManagerStatsTest extends BaseHostJUnit4Test {

    private static final String NON_EXISTENT_TZPS_APP_PACKAGE = "foobar";

    private static final int PRIMARY_PROVIDER_INDEX = 0;
    private static final int SECONDARY_PROVIDER_INDEX = 1;

    private static final int PROVIDER_STATES_COUNT =
            LocationTimeZoneProviderStateChanged.State.values().length;

    private TimeZoneDetectorShellHelper mTimeZoneDetectorShellHelper;
    private LocationTimeZoneManagerShellHelper mLocationTimeZoneManagerShellHelper;
    private LocationShellHelper mLocationShellHelper;
    private DeviceConfigShellHelper mDeviceConfigShellHelper;
    private DeviceConfigShellHelper.PreTestState mDeviceConfigPreTestState;

    private boolean mIsTelephonyDetectionSupported;
    private boolean mOriginalLocationEnabled;
    private boolean mOriginalAutoDetectionEnabled;
    private boolean mOriginalGeoDetectionEnabled;

    @Before
    public void setUp() throws Exception {
        ITestDevice device = getDevice();
        DeviceShellCommandExecutor shellCommandExecutor = new HostShellCommandExecutor(device);
        mLocationTimeZoneManagerShellHelper =
                new LocationTimeZoneManagerShellHelper(shellCommandExecutor);

        // Confirm the service being tested is present. It can be turned off permanently in config,
        // in which case there's nothing about it to test.
        mLocationTimeZoneManagerShellHelper.assumeLocationTimeZoneManagerIsPresent();

        // Install the app that hosts the fake providers.
        // Installations are tracked in BaseHostJUnit4Test and uninstalled automatically.
        installPackage(FAKE_TZPS_APP_APK);

        mTimeZoneDetectorShellHelper = new TimeZoneDetectorShellHelper(shellCommandExecutor);
        mLocationShellHelper = new LocationShellHelper(shellCommandExecutor);
        mDeviceConfigShellHelper = new DeviceConfigShellHelper(shellCommandExecutor);

        // Stop device_config updates for the duration of the test.
        mDeviceConfigPreTestState = mDeviceConfigShellHelper.setSyncModeForTest(
                SYNC_DISABLED_MODE_UNTIL_REBOOT, NAMESPACE_SYSTEM_TIME);

        // The isGeoDetectionEnabled() setting is only used when both time zone detection algorithms
        // are supported on a device. It allows the user to select between them. When only
        // location-based detection is supported then the setting is not used, i.e. the device
        // behaves like it is hardcode to ON. Attempts to change the geo detection enabled setting
        // when it isn't used by a device will fail so must be avoided.
        // Location detection must be supported to reach this point. Capture the state of telephony
        // algorithm support for later checks.
        mIsTelephonyDetectionSupported =
                mTimeZoneDetectorShellHelper.isTelephonyDetectionSupported();

        // These original values try to record the raw value of the settings before the test ran:
        // they may be ignored by the location_time_zone_manager service when they have no meaning.
        // Unfortunately, we cannot tell if the value returned is the result of setting defaults or
        // real values, which means we may not return things exactly as they were. To do better
        // would require looking at raw settings values and use internal knowledge of settings keys.
        mOriginalAutoDetectionEnabled = mTimeZoneDetectorShellHelper.isAutoDetectionEnabled();
        mOriginalGeoDetectionEnabled = mTimeZoneDetectorShellHelper.isGeoDetectionEnabled();

        mLocationTimeZoneManagerShellHelper.stop();

        // Make sure location is enabled, otherwise the geo detection feature cannot operate.
        mOriginalLocationEnabled = mLocationShellHelper.isLocationEnabledForCurrentUser();
        if (!mOriginalLocationEnabled) {
            mLocationShellHelper.setLocationEnabledForCurrentUser(true);
        }

        // Restart the location_time_zone_manager with a do-nothing test config; some settings
        // values cannot be set when the service knows that the settings won't be used. Devices
        // can be encountered with the location_time_zone_manager enabled but with no providers
        // installed. Starting the service with a valid-looking test provider config means we know
        // settings changes will be accepted regardless of the real config.
        String testPrimaryLocationTimeZoneProviderPackageName = NON_EXISTENT_TZPS_APP_PACKAGE;
        String testSecondaryLocationTimeZoneProviderPackageName = null;
        mLocationTimeZoneManagerShellHelper.startWithTestProviders(
                testPrimaryLocationTimeZoneProviderPackageName,
                testSecondaryLocationTimeZoneProviderPackageName,
                false /* recordProviderStates */);

        // Begin all tests with auto detection turned off.
        if (mOriginalAutoDetectionEnabled) {
            mTimeZoneDetectorShellHelper.setAutoDetectionEnabled(false);
        }

        if (mIsTelephonyDetectionSupported) {
            // When the telephony detection algorithm is also supported on the device, we need to
            // set the device settings so that location detection will be used. This behavior is
            // implicit when the device only supports the location detection algorithm.
            if (!mOriginalGeoDetectionEnabled) {
                mTimeZoneDetectorShellHelper.setGeoDetectionEnabled(true);
            }
        }

        // All tests begin with the location_time_zone_manager stopped so that fake providers can be
        // configured.
        mLocationTimeZoneManagerShellHelper.stop();

        // Make sure the fake provider APK install started above has completed before tests try to
        // use the fake providers.
        FakeTimeZoneProviderAppShellHelper fakeTimeZoneProviderAppShellHelper =
                new FakeTimeZoneProviderAppShellHelper(shellCommandExecutor);
        // Delay until the fake TZPS app can be found.
        fakeTimeZoneProviderAppShellHelper.waitForInstallation();

        ConfigUtils.removeConfig(device);
        ReportUtils.clearReports(device);
    }

    @After
    public void tearDown() throws Exception {
        if (!mLocationTimeZoneManagerShellHelper.isLocationTimeZoneManagerPresent()) {
            // Nothing to tear down.
            return;
        }

        // Restart the location_time_zone_manager with a test config so that the device can be set
        // back to the starting state regardless of how the test left things.
        mLocationTimeZoneManagerShellHelper.stop();
        String testPrimaryLocationTimeZoneProviderPackageName = NON_EXISTENT_TZPS_APP_PACKAGE;
        String testSecondaryLocationTimeZoneProviderPackageName = null;
        mLocationTimeZoneManagerShellHelper.startWithTestProviders(
                testPrimaryLocationTimeZoneProviderPackageName,
                testSecondaryLocationTimeZoneProviderPackageName,
                false /* recordProviderStates */);

        if (mIsTelephonyDetectionSupported) {
            if (mTimeZoneDetectorShellHelper.isGeoDetectionEnabled()
                    != mOriginalGeoDetectionEnabled) {
                mTimeZoneDetectorShellHelper.setGeoDetectionEnabled(mOriginalGeoDetectionEnabled);
            }
        }

        if (mTimeZoneDetectorShellHelper.isAutoDetectionEnabled()
                != mOriginalAutoDetectionEnabled) {
            mTimeZoneDetectorShellHelper.setAutoDetectionEnabled(mOriginalAutoDetectionEnabled);
        }

        // Everything else can be reset without worrying about the providers.
        mLocationTimeZoneManagerShellHelper.stop();

        mLocationShellHelper.setLocationEnabledForCurrentUser(mOriginalLocationEnabled);

        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        mDeviceConfigShellHelper.restoreDeviceConfigStateForTest(mDeviceConfigPreTestState);

        // Attempt to start the service without test providers. It may not start if there are no
        // providers configured, but that is ok.
        mLocationTimeZoneManagerShellHelper.start();
    }

    @Test
    public void testAtom_locationTimeZoneProviderStateChanged() throws Exception {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.LOCATION_TIME_ZONE_PROVIDER_STATE_CHANGED_FIELD_NUMBER);

        String testPrimaryLocationTimeZoneProviderPackageName = null;
        String testSecondaryLocationTimeZoneProviderPackageName = FAKE_TZPS_APP_PACKAGE;
        mLocationTimeZoneManagerShellHelper.startWithTestProviders(
                testPrimaryLocationTimeZoneProviderPackageName,
                testSecondaryLocationTimeZoneProviderPackageName,
                true /* recordProviderStates */);

        // Turn the location detection algorithm on and off, twice.
        for (int i = 0; i < 2; i++) {
            RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
            mTimeZoneDetectorShellHelper.setAutoDetectionEnabled(true);
            RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
            mTimeZoneDetectorShellHelper.setAutoDetectionEnabled(false);
        }

        // Sorted list of events in order in which they occurred.
        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        // States.
        Set<Integer> primaryProviderCreated = singletonStateId(PRIMARY_PROVIDER_INDEX,
                LocationTimeZoneProviderStateChanged.State.STOPPED);
        Set<Integer> primaryProviderFailed = singletonStateId(PRIMARY_PROVIDER_INDEX,
                LocationTimeZoneProviderStateChanged.State.PERM_FAILED);
        Set<Integer> secondaryProviderCreated = singletonStateId(SECONDARY_PROVIDER_INDEX,
                LocationTimeZoneProviderStateChanged.State.STOPPED);
        Set<Integer> secondaryProviderStarted = singletonStateId(SECONDARY_PROVIDER_INDEX,
                LocationTimeZoneProviderStateChanged.State.INITIALIZING);
        Set<Integer> secondaryProviderStopped = singletonStateId(SECONDARY_PROVIDER_INDEX,
                LocationTimeZoneProviderStateChanged.State.STOPPED);
        Function<AtomsProto.Atom, Integer> eventToStateFunction = atom -> {
            int providerIndex = atom.getLocationTimeZoneProviderStateChanged().getProviderIndex();
            return stateId(providerIndex,
                    atom.getLocationTimeZoneProviderStateChanged().getState());
        };

        // Add state sets to the list in order.
        // Assert that the events happened in the expected order. This does not check "wait" (the
        // time between events).
        List<Set<Integer>> stateSets = Arrays.asList(
                primaryProviderCreated, primaryProviderFailed,
                secondaryProviderCreated,
                secondaryProviderStarted, secondaryProviderStopped,
                secondaryProviderStarted, secondaryProviderStopped);
        AtomTestUtils.assertStatesOccurredInOrder(stateSets, data,
                0 /* wait */, eventToStateFunction);
    }

    private static Set<Integer> singletonStateId(int providerIndex,
            LocationTimeZoneProviderStateChanged.State state) {
        return Collections.singleton(stateId(providerIndex, state));
    }

    private static List<StatsLog.EventMetricData> extractEventsForProviderIndex(
            List<StatsLog.EventMetricData> data, int providerIndex) {
        return data.stream().filter(event -> {
            if (!event.getAtom().hasLocationTimeZoneProviderStateChanged()) {
                return false;
            }
            return event.getAtom().getLocationTimeZoneProviderStateChanged().getProviderIndex()
                    == providerIndex;
        }).collect(toList());
    }

    /** Maps a (provider index, provider state) pair to an integer state ID. */
    private static Integer stateId(
            int providerIndex, LocationTimeZoneProviderStateChanged.State providerState) {
        return (providerIndex * PROVIDER_STATES_COUNT) + providerState.getNumber();
    }
}

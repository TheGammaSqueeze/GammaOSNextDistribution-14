/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.tradefed.targetprep;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.tradefed.build.DeviceBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.TcpDevice;
import com.android.tradefed.device.TestDevice;
import com.android.tradefed.device.TestDeviceOptions;
import com.android.tradefed.device.TestDeviceState;
import com.android.tradefed.device.cloud.RemoteAndroidVirtualDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.util.BinaryState;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.File;
import java.util.LinkedHashMap;

/** Unit tests for {@link DeviceSetup}. */
@RunWith(JUnit4.class)
public class DeviceSetupTest {

    private DeviceSetup mDeviceSetup;
    private ITestDevice mMockDevice;
    private IDevice mMockIDevice;
    private IDeviceBuildInfo mMockBuildInfo;
    private TestInformation mTestInfo;
    private File mTmpDir;

    private static final int DEFAULT_API_LEVEL = 23;

    @Before
    public void setUp() throws Exception {
        mMockDevice = mock(TestDevice.class);
        mMockIDevice = mock(IDevice.class);
        when(mMockDevice.getSerialNumber()).thenReturn("foo");
        when(mMockDevice.getDeviceDescriptor()).thenReturn(null);
        when(mMockDevice.getIDevice()).thenReturn(mMockIDevice);
        when(mMockDevice.getDeviceState()).thenReturn(TestDeviceState.ONLINE);

        mMockBuildInfo = new DeviceBuildInfo("0", "");
        mDeviceSetup = new DeviceSetup();
        OptionSetter setter = new OptionSetter(mDeviceSetup);
        setter.setOptionValue("optimized-property-setting", Boolean.toString(false));
        mTmpDir = FileUtil.createTempDir("tmp");
        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice("device", mMockDevice);
        context.addDeviceBuildInfo("device", mMockBuildInfo);
        mTestInfo = TestInformation.newBuilder().setInvocationContext(context).build();
    }

    @After
    public void tearDown() throws Exception {
        FileUtil.recursiveDelete(mTmpDir);
    }

    /** Simple normal case test for {@link DeviceSetup#setUp(TestInformation)}. */
    @Test
    public void testSetup() throws Exception {
        ArgumentCaptor<String> setPropCapture = ArgumentCaptor.forClass(String.class);
        doSetupExpectations(true, setPropCapture);
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice, atLeastOnce()).enableAdbRoot();

        String setProp = setPropCapture.getValue();
        assertTrue(
                "Set prop doesn't contain ro.telephony.disable-call=true",
                setProp.contains("ro.telephony.disable-call=true\n"));
        assertTrue(
                "Set prop doesn't contain ro.audio.silent=1",
                setProp.contains("ro.audio.silent=1\n"));
        assertTrue(
                "Set prop doesn't contain ro.test_harness=1",
                setProp.contains("ro.test_harness=1\n"));
        assertTrue("Set prop doesn't contain ro.monkey=1", setProp.contains("ro.monkey=1\n"));
    }

    @Test
    public void testSetup_airplane_mode_on() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setAirplaneMode(BinaryState.ON);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("global", "airplane_mode_on", "1");
        verify(mMockDevice)
                .executeShellCommand(
                        "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_airplane_mode_off() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setAirplaneMode(BinaryState.OFF);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("global", "airplane_mode_on", "0");
        verify(mMockDevice)
                .executeShellCommand(
                        "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_data_on() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setData(BinaryState.ON);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("global", "mobile_data", "1");
        verify(mMockDevice).executeShellCommand("svc data enable");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_data_off() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setData(BinaryState.OFF);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("global", "mobile_data", "0");
        verify(mMockDevice).executeShellCommand("svc data disable");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_cell_on() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setCell(BinaryState.ON);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("global", "cell_on", "1");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_cell_off() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setCell(BinaryState.OFF);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("global", "cell_on", "0");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_cell_auto_setting_on() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setCellAutoSetting(BinaryState.ON);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("global", "clockwork_cell_auto_setting", "1");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_cell_auto_setting_off() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setCellAutoSetting(BinaryState.OFF);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("global", "clockwork_cell_auto_setting", "0");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_wifi_on() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setWifi(BinaryState.ON);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("global", "wifi_on", "1");
        verify(mMockDevice).executeShellCommand("svc wifi enable");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_wifi_off() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setWifi(BinaryState.OFF);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("global", "wifi_on", "0");
        verify(mMockDevice).executeShellCommand("svc wifi disable");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_wifi_network_name() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();
        LinkedHashMap<String, String> ssidToPsk = new LinkedHashMap<>();
        ssidToPsk.put("wifi_network", "psk");
        when(mMockDevice.connectToWifiNetwork(ssidToPsk)).thenReturn(true);

        mDeviceSetup.setWifiNetwork("wifi_network");
        mDeviceSetup.setWifiPsk("psk");

        mDeviceSetup.setWifi(BinaryState.ON);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("global", "wifi_on", "1");
        verify(mMockDevice).executeShellCommand("svc wifi enable");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_wifi_network_name_emptyPsk() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();
        LinkedHashMap<String, String> ssidToPsk = new LinkedHashMap<>();
        ssidToPsk.put("wifi_network", "");
        when(mMockDevice.connectToWifiNetwork(ssidToPsk)).thenReturn(true);

        mDeviceSetup.setWifiNetwork("wifi_network");
        mDeviceSetup.setWifiPsk("");

        mDeviceSetup.setWifi(BinaryState.ON);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("global", "wifi_on", "1");
        verify(mMockDevice).executeShellCommand("svc wifi enable");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_wifi_network_empty() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setWifiNetwork("");
        mDeviceSetup.setWifiPsk("psk");

        mDeviceSetup.setWifi(BinaryState.ON);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("global", "wifi_on", "1");
        verify(mMockDevice).executeShellCommand("svc wifi enable");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_wifi_multiple_network_names() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();
        LinkedHashMap<String, String> ssidToPskExpected = new LinkedHashMap<>();
        ssidToPskExpected.put("old_network", "abc");
        ssidToPskExpected.put("network", "def");
        ssidToPskExpected.put("new_network", "ghi");
        when(mMockDevice.connectToWifiNetwork(ssidToPskExpected)).thenReturn(true);

        mDeviceSetup.setWifiNetwork("old_network");
        mDeviceSetup.setWifiPsk("abc");
        LinkedHashMap<String, String> ssidToPsk = new LinkedHashMap<>();
        ssidToPsk.put("network", "def");
        ssidToPsk.put("new_network", "ghi");
        mDeviceSetup.setWifiSsidToPsk(ssidToPsk);

        mDeviceSetup.setWifi(BinaryState.ON);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("global", "wifi_on", "1");
        verify(mMockDevice).executeShellCommand("svc wifi enable");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_wifi_multiple_network_names_unsecured() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();
        LinkedHashMap<String, String> ssidToPskExpected = new LinkedHashMap<>();
        ssidToPskExpected.put("old_network", null);
        ssidToPskExpected.put("network", "");
        ssidToPskExpected.put("new_network", "");
        when(mMockDevice.connectToWifiNetwork(ssidToPskExpected)).thenReturn(true);

        mDeviceSetup.setWifiNetwork("old_network");
        mDeviceSetup.setWifiPsk(null);
        LinkedHashMap<String, String> ssidToPsk = new LinkedHashMap<>();
        ssidToPsk.put("network", "");
        ssidToPsk.put("new_network", "");
        mDeviceSetup.setWifiSsidToPsk(ssidToPsk);

        mDeviceSetup.setWifi(BinaryState.ON);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("global", "wifi_on", "1");
        verify(mMockDevice).executeShellCommand("svc wifi enable");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_wifi_watchdog_on() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setWifiWatchdog(BinaryState.ON);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("global", "wifi_watchdog", "1");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_wifi_watchdog_off() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setWifiWatchdog(BinaryState.OFF);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("global", "wifi_watchdog", "0");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_disable_cw_wifi_mediator_on() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setDisableCwWifiMediator(BinaryState.ON);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("global", "cw_disable_wifimediator", "1");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_disable_cw_wifi_mediator_off() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setDisableCwWifiMediator(BinaryState.OFF);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("global", "cw_disable_wifimediator", "0");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_wifi_scan_on() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setWifiScanAlwaysEnabled(BinaryState.ON);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("global", "wifi_scan_always_enabled", "1");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_wifi_scan_off() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setWifiScanAlwaysEnabled(BinaryState.OFF);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("global", "wifi_scan_always_enabled", "0");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_ethernet_on() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setEthernet(BinaryState.ON);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).executeShellCommand("ifconfig eth0 up");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_ethernet_off() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setEthernet(BinaryState.OFF);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).executeShellCommand("ifconfig eth0 down");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_bluetooth_on() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setBluetooth(BinaryState.ON);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice)
                .executeShellCommand(
                        "cmd bluetooth_manager enable && cmd bluetooth_manager"
                                + " wait-for-state:STATE_ON");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_bluetooth_off() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setBluetooth(BinaryState.OFF);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice)
                .executeShellCommand(
                        "cmd bluetooth_manager disable && cmd bluetooth_manager"
                                + " wait-for-state:STATE_OFF");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_nfc_on() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setNfc(BinaryState.ON);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).executeShellCommand("svc nfc enable");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_nfc_off() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setNfc(BinaryState.OFF);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).executeShellCommand("svc nfc disable");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_screen_adaptive_on() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setScreenAdaptiveBrightness(BinaryState.ON);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("system", "screen_brightness_mode", "1");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_screen_adaptive_off() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setScreenAdaptiveBrightness(BinaryState.OFF);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("system", "screen_brightness_mode", "0");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_screen_brightness() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setScreenBrightness(50);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("system", "screen_brightness", "50");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_screen_stayon_default() throws Exception {
        doSetupExpectations(
                false /* Expect no screen on command */, ArgumentCaptor.forClass(String.class));
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setScreenAlwaysOn(BinaryState.IGNORE);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_screen_stayon_off() throws Exception {
        doSetupExpectations(
                false /* Expect no screen on command */, ArgumentCaptor.forClass(String.class));
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setScreenAlwaysOn(BinaryState.OFF);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).executeShellCommand("svc power stayon false");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_screen_timeout() throws Exception {
        doSetupExpectations(
                false /* Expect no screen on command */, ArgumentCaptor.forClass(String.class));
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setScreenAlwaysOn(BinaryState.IGNORE);
        mDeviceSetup.setScreenTimeoutSecs(5L);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("system", "screen_off_timeout", "5000");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_screen_ambient_on() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setScreenAmbientMode(BinaryState.ON);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("secure", "doze_enabled", "1");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_screen_ambient_off() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setScreenAmbientMode(BinaryState.OFF);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("secure", "doze_enabled", "0");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_wake_gesture_on() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setWakeGesture(BinaryState.ON);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("secure", "wake_gesture_enabled", "1");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_wake_gesture_off() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setWakeGesture(BinaryState.OFF);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("secure", "wake_gesture_enabled", "0");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_screen_saver_on() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setScreenSaver(BinaryState.ON);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("secure", "screensaver_enabled", "1");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_screen_saver_off() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setScreenSaver(BinaryState.OFF);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("secure", "screensaver_enabled", "0");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_notification_led_on() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setNotificationLed(BinaryState.ON);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("system", "notification_light_pulse", "1");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_notification_led_off() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setNotificationLed(BinaryState.OFF);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("system", "notification_light_pulse", "0");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testInstallNonMarketApps_on() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setInstallNonMarketApps(BinaryState.ON);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("secure", "install_non_market_apps", "1");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testInstallNonMarketApps_off() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setInstallNonMarketApps(BinaryState.OFF);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("secure", "install_non_market_apps", "0");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_trigger_media_mounted() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setTriggerMediaMounted(true);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice)
                .executeShellCommand(
                        "am broadcast -a android.intent.action.MEDIA_MOUNTED "
                                + "-d file://${EXTERNAL_STORAGE} --receiver-include-background");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_location_gps_on() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setLocationGps(BinaryState.ON);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("secure", "location_providers_allowed", "+gps");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_location_gps_off() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setLocationGps(BinaryState.OFF);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("secure", "location_providers_allowed", "-gps");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_location_network_on() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setLocationNetwork(BinaryState.ON);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("secure", "location_providers_allowed", "+network");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_location_network_off() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setLocationNetwork(BinaryState.OFF);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("secure", "location_providers_allowed", "-network");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_rotate_on() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setAutoRotate(BinaryState.ON);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("system", "accelerometer_rotation", "1");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_rotate_off() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setAutoRotate(BinaryState.OFF);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("system", "accelerometer_rotation", "0");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_battery_saver_on() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setBatterySaver(BinaryState.ON);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("global", "low_power", "1");
        verify(mMockDevice).executeShellCommand("dumpsys battery unplug");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_legacy_battery_saver_on() throws Exception {
        doSetupExpectations(21); // API level Lollipop
        doCheckExternalStoreSpaceExpectations();

        OptionSetter setter = new OptionSetter(mDeviceSetup);
        setter.setOptionValue("force-skip-settings", "true"); // Changing settings requires API 22+.
        mDeviceSetup.setBatterySaver(BinaryState.ON);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).executeShellCommand("dumpsys battery set usb 0");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_battery_saver_off() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setBatterySaver(BinaryState.OFF);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("global", "low_power", "0");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_battery_saver_trigger() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setBatterySaverTrigger(50);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("global", "low_power_trigger_level", "50");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_enable_full_battery_stats_history() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setEnableFullBatteryStatsHistory(true);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).executeShellCommand("dumpsys batterystats --enable full-history");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_disable_doze() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setDisableDoze(true);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).executeShellCommand("dumpsys deviceidle disable");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_update_time_on() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setAutoUpdateTime(BinaryState.ON);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("global", "auto_time", "1");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_update_time_off() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setAutoUpdateTime(BinaryState.OFF);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("global", "auto_time", "0");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_update_timezone_on() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setAutoUpdateTimezone(BinaryState.ON);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("global", "auto_timezone", "1");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_update_timezone_off() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setAutoUpdateTimezone(BinaryState.OFF);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("global", "auto_timezone", "0");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_set_timezone_LA() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();
        when(mMockDevice.setProperty("persist.sys.timezone", "America/Los_Angeles"))
                .thenReturn(true);
        when(mMockDevice.getProperty("persist.sys.timezone")).thenReturn("America/Los_Angeles");

        mDeviceSetup.setTimezone("America/Los_Angeles");
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_no_disable_dialing() throws Exception {
        ArgumentCaptor<String> setPropCapture = ArgumentCaptor.forClass(String.class);
        doSetupExpectations(true, setPropCapture);
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setDisableDialing(false);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice, atLeastOnce()).enableAdbRoot();

        assertFalse(
                "Set prop contains ro.telephony.disable-call=true",
                setPropCapture.getValue().contains("ro.telephony.disable-call=true\n"));
    }

    @Test
    public void testSetup_sim_data() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setDefaultSimData(1);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("global", "multi_sim_data_call", "1");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_sim_voice() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setDefaultSimVoice(1);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("global", "multi_sim_voice_call", "1");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_sim_sms() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setDefaultSimSms(1);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).setSetting("global", "multi_sim_sms", "1");
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_no_disable_audio() throws Exception {
        ArgumentCaptor<String> setPropCapture = ArgumentCaptor.forClass(String.class);
        doSetupExpectations(true, setPropCapture);
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setDisableAudio(false);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice, atLeastOnce()).enableAdbRoot();

        assertFalse(
                "Set prop contains ro.audio.silent=1",
                setPropCapture.getValue().contains("ro.audio.silent=1\n"));
    }

    @Test
    public void testSetup_no_test_harness() throws Exception {
        ArgumentCaptor<String> setPropCapture = ArgumentCaptor.forClass(String.class);
        doSetupExpectations(
                true /* screen on */,
                true /* root enabled */,
                true /* root response */,
                false /* test harness mode */,
                DEFAULT_API_LEVEL,
                setPropCapture);
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setTestHarness(false);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice, atLeastOnce()).enableAdbRoot();

        String setProp = setPropCapture.getValue();
        assertFalse("Set prop contains ro.test_harness=1", setProp.contains("ro.test_harness=1\n"));
        assertFalse("Set prop contains ro.monkey=1", setProp.contains("ro.monkey=1\n"));
    }

    @Test
    public void testSetup_disalbe_dalvik_verifier() throws Exception {
        ArgumentCaptor<String> setPropCapture = ArgumentCaptor.forClass(String.class);
        doSetupExpectations(true, setPropCapture);
        doCheckExternalStoreSpaceExpectations();
        when(mMockDevice.getProperty("dalvik.vm.dexopt-flags")).thenReturn("v=n");

        mDeviceSetup.setDisableDalvikVerifier(true);
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice, atLeastOnce()).enableAdbRoot();

        String setProp = setPropCapture.getValue();
        assertTrue(
                "Set prop doesn't contain dalvik.vm.dexopt-flags=v=n",
                setProp.contains("dalvik.vm.dexopt-flags=v=n\n"));
    }

    /** Test {@link DeviceSetup#setUp(TestInformation)} when free space check fails. */
    @Test
    public void testSetup_freespace() throws Exception {
        doSetupExpectations();
        mDeviceSetup.setMinExternalStorageKb(500);
        when(mMockDevice.getExternalStoreFreeSpace()).thenReturn(1L);

        try {
            mDeviceSetup.setUp(mTestInfo);
            fail("DeviceNotAvailableException not thrown");
        } catch (DeviceNotAvailableException e) {
            // expected
        }
    }

    /** Test {@link DeviceSetup#setUp(TestInformation)} when connect to wifi fails. */
    @Test
    public void testSetup_connectToWifiFailed() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();
        when(mMockDevice.connectToWifiNetwork("wifi_network", "psk")).thenReturn(false);

        mDeviceSetup.setWifiNetwork("wifi_network");
        mDeviceSetup.setWifiPsk("psk");
        mDeviceSetup.setWifi(BinaryState.ON);

        try {
            mDeviceSetup.setUp(mTestInfo);
            fail("TargetSetupError not thrown");
        } catch (TargetSetupError e) {
            // expected identifier
            assertEquals(e.getErrorId(), InfraErrorIdentifier.WIFI_FAILED_CONNECT);
        }
    }

    /**
     * Test {@link DeviceSetup#setUp(TestInformation)} when connect to wifi fails for virtual
     * device.
     */
    @Test
    public void testSetup_connectToWifiFailedVirtualDevice() throws Exception {
        // setup virtual device
        mMockDevice = mock(RemoteAndroidVirtualDevice.class);
        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice("device", mMockDevice);
        context.addDeviceBuildInfo("device", mMockBuildInfo);

        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        when(mMockDevice.connectToWifiNetwork("wifi_network", "psk")).thenReturn(false);
        mTestInfo = TestInformation.newBuilder().setInvocationContext(context).build();
        mDeviceSetup.setWifiNetwork("wifi_network");
        mDeviceSetup.setWifiPsk("psk");
        mDeviceSetup.setWifi(BinaryState.ON);

        try {
            mDeviceSetup.setUp(mTestInfo);
            fail("TargetSetupError not thrown");
        } catch (TargetSetupError e) {
            // expected identifier
            assertEquals(e.getErrorId(), InfraErrorIdentifier.VIRTUAL_WIFI_FAILED_CONNECT);
        }
    }

    /** Test {@link DeviceSetup#setUp(TestInformation)} when local data path does not exist. */
    @Test
    public void testSetup_badLocalData() throws Exception {
        doSetupExpectations();
        mDeviceSetup.setLocalDataPath(new File("idontexist"));

        try {
            mDeviceSetup.setUp(mTestInfo);
            fail("TargetSetupError not thrown");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    /** Test normal case {@link DeviceSetup#setUp(TestInformation)} when local data is synced */
    @Test
    public void testSetup_syncData() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();
        doSyncDataExpectations(true);

        mDeviceSetup.setUp(mTestInfo);
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    /** Test case {@link DeviceSetup#setUp(TestInformation)} when local data fails to be synced. */
    @Test
    public void testSetup_syncDataFails() throws Exception {
        doSetupExpectations();
        doSyncDataExpectations(false);

        try {
            mDeviceSetup.setUp(mTestInfo);
            fail("TargetSetupError not thrown");
        } catch (TargetSetupError e) {
            // expected
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testSetup_legacy() throws Exception {
        ArgumentCaptor<String> setPropCapture = ArgumentCaptor.forClass(String.class);
        doSetupExpectations(true, setPropCapture);
        doCheckExternalStoreSpaceExpectations();
        when(mMockDevice.getProperty("key")).thenReturn("value");

        mDeviceSetup.setDeprecatedAudioSilent(false);
        mDeviceSetup.setDeprecatedMinExternalStoreSpace(1000);
        mDeviceSetup.setDeprecatedSetProp("key=value");
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice, atLeastOnce()).enableAdbRoot();

        String setProp = setPropCapture.getValue();
        assertTrue(
                "Set prop doesn't contain ro.telephony.disable-call=true",
                setProp.contains("ro.telephony.disable-call=true\n"));
        assertTrue(
                "Set prop doesn't contain ro.test_harness=1",
                setProp.contains("ro.test_harness=1\n"));
        assertTrue("Set prop doesn't contain ro.monkey=1", setProp.contains("ro.monkey=1\n"));
        assertTrue("Set prop doesn't contain key=value", setProp.contains("key=value\n"));
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testSetup_legacy_storage_conflict() throws Exception {
        doSetupExpectations();

        mDeviceSetup.setMinExternalStorageKb(1000);
        mDeviceSetup.setDeprecatedMinExternalStoreSpace(1000);
        try {
            mDeviceSetup.setUp(mTestInfo);
            fail("TargetSetupError expected");
        } catch (TargetSetupError e) {
            // Expected
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testSetup_legacy_silent_conflict() throws Exception {
        doSetupExpectations();

        mDeviceSetup.setDisableAudio(false);
        mDeviceSetup.setDeprecatedAudioSilent(false);
        try {
            mDeviceSetup.setUp(mTestInfo);
            fail("TargetSetupError expected");
        } catch (TargetSetupError e) {
            // Expected
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testSetup_legacy_setprop_conflict() throws Exception {
        doSetupExpectations();

        mDeviceSetup.setProperty("key", "value");
        mDeviceSetup.setDeprecatedSetProp("key=value");
        try {
            mDeviceSetup.setUp(mTestInfo);
            fail("TargetSetupError expected");
        } catch (TargetSetupError e) {
            // Expected
        }
    }

    @Test
    public void test_restore_properties_previous_exists() throws Exception {
        File f = new File("");
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();
        when(mMockDevice.pullFile("/data/local.prop")).thenReturn(f);
        when(mMockDevice.pushFile(f, "/data/local.prop")).thenReturn(true);

        when(mMockDevice.getProperty("key")).thenReturn("value");

        mDeviceSetup.setRestoreProperties(true);
        mDeviceSetup.setProperty("key", "value");
        mDeviceSetup.setUp(mTestInfo);
        mDeviceSetup.tearDown(mTestInfo, null);

        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
        // tearDown
        verify(mMockDevice, times(1)).reboot();
        verify(mMockDevice, times(1)).pullFile("/data/local.prop");
        verify(mMockDevice, times(1)).pushFile(f, "/data/local.prop");
    }

    @Test
    public void test_restore_properties_previous_doesnt_exists() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();
        when(mMockDevice.pullFile("/data/local.prop")).thenReturn(null);

        when(mMockDevice.getProperty("key")).thenReturn("value");

        mDeviceSetup.setRestoreProperties(true);
        mDeviceSetup.setProperty("key", "value");
        mDeviceSetup.setUp(mTestInfo);
        mDeviceSetup.tearDown(mTestInfo, null);

        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
        // tearDown
        verify(mMockDevice, times(1)).reboot();
        verify(mMockDevice, times(1)).pullFile("/data/local.prop");
        verify(mMockDevice).deleteFile("/data/local.prop");
    }

    @Test
    public void test_restore_settings() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();
        when(mMockDevice.getApiLevel()).thenReturn(23);
        when(mMockDevice.getSetting("system", "key")).thenReturn("orig");

        when(mMockDevice.getSetting("global", "key2")).thenReturn("orig2");

        when(mMockDevice.getSetting("secure", "key3")).thenReturn("orig3");

        mDeviceSetup.setRestoreSettings(true);
        mDeviceSetup.setSystemSetting("key", "value");
        mDeviceSetup.setGlobalSetting("key2", "value2");
        mDeviceSetup.setSecureSetting("key3", "value3");
        mDeviceSetup.setUp(mTestInfo);
        mDeviceSetup.tearDown(mTestInfo, null);

        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
        verify(mMockDevice, times(1)).setSetting("system", "key", "value");
        verify(mMockDevice, times(1)).setSetting("global", "key2", "value2");
        verify(mMockDevice, times(1)).setSetting("secure", "key3", "value3");
        verify(mMockDevice, times(1)).setSetting("system", "key", "orig");
        verify(mMockDevice, times(1)).setSetting("global", "key2", "orig2");
        verify(mMockDevice, times(1)).setSetting("secure", "key3", "orig3");
        verify(mMockDevice, times(1)).getSetting("system", "key");
        verify(mMockDevice, times(1)).getSetting("global", "key2");
        verify(mMockDevice, times(1)).getSetting("secure", "key3");
    }

    @Test
    public void testTearDown() throws Exception {

        mDeviceSetup.tearDown(mTestInfo, null);
    }

    @Test
    public void testTearDown_disconnectFromWifi() throws Exception {
        when(mMockDevice.isWifiEnabled()).thenReturn(Boolean.TRUE);
        when(mMockDevice.disconnectFromWifi()).thenReturn(Boolean.TRUE);
        mDeviceSetup.setWifiNetwork("wifi_network");

        mDeviceSetup.tearDown(mTestInfo, null);
    }

    /** Test that tearDown is inop when using a stub device instance. */
    @Test
    public void testTearDown_tcpDevice() throws Exception {
        when(mMockDevice.getIDevice()).thenReturn(new TcpDevice("tcp-device-0"));

        mDeviceSetup.tearDown(mTestInfo, null);
    }

    @Test
    public void testSetup_rootDisabled_withoutChangeSystemProp() throws Exception {
        OptionSetter setter = new OptionSetter(mDeviceSetup);
        setter.setOptionValue("set-property", "fooProperty", "1");
        doSetupExpectations(
                true /* screenOn */,
                true /* root enabled */,
                true /* root response */,
                true /* test harness */,
                DEFAULT_API_LEVEL,
                ArgumentCaptor.forClass(String.class));
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setDisableDialing(false);
        mDeviceSetup.setDisableAudio(false);
        mDeviceSetup.setTestHarness(false);
        mDeviceSetup.setUp(mTestInfo);
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_rootDisabled_withSkipChangeSystemProp() throws Exception {
        doSetupExpectations(
                true /* screenOn */,
                false /* root enabled */,
                false /* root response */,
                false /* test harness */,
                DEFAULT_API_LEVEL,
                ArgumentCaptor.forClass(String.class));
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setForceSkipSystemProps(true);
        mDeviceSetup.setForceRootSetup(false);
        mDeviceSetup.setUp(mTestInfo);
        verify(mMockDevice, never()).enableAdbRoot();
    }

    @Test
    public void testSetup_rootDisabled_withChangeSystemProp() throws Exception {
        doSetupExpectations(
                true /* screenOn */,
                false /* root enabled */,
                false /* root response */,
                DEFAULT_API_LEVEL,
                ArgumentCaptor.forClass(String.class));
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setProperty("key", "value");
        try {
            mDeviceSetup.setUp(mTestInfo);
            fail("TargetSetupError expected");
        } catch (TargetSetupError e) {
            // Expected
        }
    }

    @Test
    public void testSetup_rootFailed() throws Exception {
        doSetupExpectations(
                true /* screenOn */,
                true /* root enabled */,
                false /* root response */,
                DEFAULT_API_LEVEL,
                ArgumentCaptor.forClass(String.class));

        try {
            mDeviceSetup.setUp(mTestInfo);
            fail("TargetSetupError expected");
        } catch (TargetSetupError e) {
            // Expected
        }
    }

    @Test
    public void testSetup_rootDisabled_withOptimizedPropSetting() throws Exception {
        doSetupExpectations(
                true /* screenOn */,
                true /* root enabled */,
                true /* root response */,
                true /* test harness */,
                DEFAULT_API_LEVEL,
                false,
                ArgumentCaptor.forClass(String.class));
        doCheckExternalStoreSpaceExpectations();
        when(mMockDevice.getProperty("fooProperty")).thenReturn("1");

        OptionSetter setter = new OptionSetter(mDeviceSetup);
        setter.setOptionValue("optimized-non-persistent-setup", "false");
        setter.setOptionValue("optimized-property-setting", "true");
        setter.setOptionValue("set-property", "fooProperty", "1");
        mDeviceSetup.setUp(mTestInfo);
        verify(mMockDevice, atLeastOnce()).enableAdbRoot();
    }

    @Test
    public void testSetup_disable_device_config_sync() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        OptionSetter setter = new OptionSetter(mDeviceSetup);
        setter.setOptionValue("disable-device-config-sync", "true");
        mDeviceSetup.setUp(mTestInfo);
        verify(mMockDevice)
                .executeShellCommand("device_config set_sync_disabled_for_tests persistent");
    }

    @Test
    public void testSetup_no_reboot_on_generic_persist_property() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setProperty("persist.key", "value");
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice, times(0)).reboot();
    }

    @Test
    public void testSetup_reboot_on_memtag() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setProperty("arm64.memtag.bootctl", "memtag");
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).reboot();
    }

    @Test
    public void testSetup_reboot_on_enable_jdwp() throws Exception {
        doSetupExpectations();
        doCheckExternalStoreSpaceExpectations();

        mDeviceSetup.setProperty("persist.debug.dalvik.vm.jdwp.enabled", "1");
        mDeviceSetup.setUp(mTestInfo);

        verify(mMockDevice).reboot();
    }

    /** Set EasyMock expectations for a normal setup call */
    private void doSetupExpectations() throws DeviceNotAvailableException, ConfigurationException {
        doSetupExpectations(
                true /* screen on */,
                true /* root enabled */,
                true /* root response */,
                true /* test harness mode */,
                DEFAULT_API_LEVEL,
                ArgumentCaptor.forClass(String.class));
    }

    /** Set EasyMock expectations for a normal setup call */
    private void doSetupExpectations(int apiLevel)
            throws DeviceNotAvailableException, ConfigurationException {
        doSetupExpectations(
                true /* screen on */,
                true /* root enabled */,
                true /* root response */,
                true /* test harness mode */,
                apiLevel,
                ArgumentCaptor.forClass(String.class));
    }

    /** Set EasyMock expectations for a normal setup call */
    private void doSetupExpectations(boolean screenOn, ArgumentCaptor<String> setPropCapture)
            throws DeviceNotAvailableException, ConfigurationException {
        doSetupExpectations(
                screenOn,
                true /* root enabled */,
                true /* root response */,
                true /* test harness mode */,
                DEFAULT_API_LEVEL,
                setPropCapture);
    }

    /** Set EasyMock expectations for a normal setup call */
    private void doSetupExpectations(
            boolean screenOn,
            boolean adbRootEnabled,
            boolean adbRootResponse,
            int apiLevel,
            ArgumentCaptor<String> setPropCapture)
            throws DeviceNotAvailableException, ConfigurationException {
        doSetupExpectations(
                screenOn,
                adbRootEnabled,
                adbRootResponse,
                true /* test harness mode */,
                apiLevel,
                setPropCapture);
    }

    /** Set EasyMock expectations for a normal setup call */
    private void doSetupExpectations(
            boolean screenOn,
            boolean adbRootEnabled,
            boolean adbRootResponse,
            boolean testHarness,
            int apiLevel,
            ArgumentCaptor<String> setPropCapture)
            throws DeviceNotAvailableException, ConfigurationException {
        doSetupExpectations(
                screenOn,
                adbRootEnabled,
                adbRootResponse,
                testHarness,
                apiLevel,
                true, // Almost all the time we expect local prop push
                setPropCapture);
    }

    /** Set EasyMock expectations for a normal setup call */
    private void doSetupExpectations(
            boolean screenOn,
            boolean adbRootEnabled,
            boolean adbRootResponse,
            boolean testHarness,
            int apiLevel,
            boolean localPropPush,
            ArgumentCaptor<String> setPropCapture)
            throws DeviceNotAvailableException, ConfigurationException {
        TestDeviceOptions options = new TestDeviceOptions();
        OptionSetter setter = new OptionSetter(options);
        setter.setOptionValue("enable-root", Boolean.toString(adbRootEnabled));
        when(mMockDevice.getOptions()).thenReturn(options);
        if (adbRootEnabled) {
            when(mMockDevice.enableAdbRoot()).thenReturn(adbRootResponse);
        }

        when(mMockDevice.clearErrorDialogs()).thenReturn(Boolean.TRUE);
        when(mMockDevice.getApiLevel()).thenReturn(apiLevel);
        if (adbRootResponse && localPropPush) {
            // expect push of local.prop file to change system properties
            when(mMockDevice.pushString(setPropCapture.capture(), Mockito.contains("local.prop")))
                    .thenReturn(Boolean.TRUE);
            when(mMockDevice.executeShellCommand(Mockito.matches("chmod 644 .*local.prop")))
                    .thenReturn("");
        }
        if (screenOn) {
            when(mMockDevice.executeShellCommand("svc power stayon true")).thenReturn("");
            when(mMockDevice.executeShellCommand("input keyevent 82")).thenReturn("");
            when(mMockDevice.hasFeature("android.hardware.type.watch")).thenReturn(false);
            when(mMockDevice.executeShellCommand("input keyevent 3")).thenReturn("");
        }
        if (testHarness) {
            when(mMockDevice.setProperty("persist.sys.test_harness", "1")).thenReturn(true);
            when(mMockDevice.getProperty("persist.sys.test_harness")).thenReturn("1");
        }
        when(mMockDevice.getProperty("ro.telephony.disable-call")).thenReturn("true");
        when(mMockDevice.getProperty("ro.audio.silent")).thenReturn("1");
        when(mMockDevice.getProperty("ro.test_harness")).thenReturn("1");
        when(mMockDevice.getProperty("ro.monkey")).thenReturn("1");
        CommandResult successResult = new CommandResult();
        successResult.setStatus(CommandStatus.SUCCESS);
        successResult.setStdout("");
        when(mMockDevice.executeShellV2Command(
                        "am start -a com.android.setupwizard.FOUR_CORNER_EXIT"))
                .thenReturn(successResult);
        when(mMockDevice.executeShellV2Command("am start -a com.android.setupwizard.EXIT"))
                .thenReturn(successResult);
        when(mMockDevice.executeShellV2Command("dumpsys window displays | grep mCurrentFocus"))
                .thenReturn(successResult);
    }

    /** Perform common EasyMock expect operations for a setUp call which syncs local data */
    private void doSyncDataExpectations(boolean result) throws DeviceNotAvailableException {
        mDeviceSetup.setLocalDataPath(mTmpDir);
        when(mMockDevice.getIDevice()).thenReturn(mMockIDevice);
        String mntPoint = "/sdcard";
        when(mMockIDevice.getMountPoint(IDevice.MNT_EXTERNAL_STORAGE)).thenReturn(mntPoint);
        when(mMockDevice.syncFiles(mTmpDir, mntPoint)).thenReturn(result);
    }

    private void doCheckExternalStoreSpaceExpectations() throws DeviceNotAvailableException {
        when(mMockDevice.getExternalStoreFreeSpace()).thenReturn(1000L);
    }
}

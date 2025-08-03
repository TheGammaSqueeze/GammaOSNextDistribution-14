/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.media.tv.cts;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.Instrumentation;
import android.content.AttributionSource;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.SurfaceTexture;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.tv.TunedInfo;
import android.media.tv.TvContentRating;
import android.media.tv.TvContract;
import android.media.tv.TvInputHardwareInfo;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.media.tv.TvInputManager.Hardware;
import android.media.tv.TvInputManager.HardwareCallback;
import android.media.tv.TvInputManager.Session;
import android.media.tv.TvInputManager.SessionCallback;
import android.media.tv.TvInputService;
import android.media.tv.TvStreamConfig;
import android.media.tv.TvView;
import android.media.tv.cts.TvViewTest.MockCallback;
import android.media.tv.tunerresourcemanager.TunerResourceManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.test.ActivityInstrumentationTestCase2;
import android.tv.cts.R;
import android.util.Log;
import android.view.Surface;
import androidx.test.InstrumentationRegistry;
import com.android.compatibility.common.util.PollingCheck;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Test for {@link android.media.tv.TvInputManager}.
 */
public class TvInputManagerTest extends ActivityInstrumentationTestCase2<TvViewStubActivity> {
    private static final String TAG = "TvInputManagerTest";

    /** The maximum time to wait for an operation. */
    private static final long TIME_OUT_MS = 15000L;
    private static final int PRIORITY_HINT_USE_CASE_TYPE_INVALID = 1000;

    private static final int DUMMY_DEVICE_ID = Integer.MAX_VALUE;
    private static final String[] VALID_TV_INPUT_SERVICES = {
        StubTunerTvInputService.class.getName()
    };
    private static final String[] INVALID_TV_INPUT_SERVICES = {
        NoMetadataTvInputService.class.getName(), NoPermissionTvInputService.class.getName()
    };
    private static final String EXTENSION_INTERFACE_NAME_WITHOUT_PERMISSION =
            "android.media.tv.cts.TvInputManagerTest.EXTENSION_INTERFACE_NAME_WITHOUT_PERMISSION";
    private static final String EXTENSION_INTERFACE_NAME_WITH_PERMISSION_GRANTED =
            "android.media.tv.cts.TvInputManagerTest"
            + ".EXTENSION_INTERFACE_NAME_WITH_PERMISSION_GRANTED";
    private static final String EXTENSION_INTERFACE_NAME_WITH_PERMISSION_UNGRANTED =
            "android.media.tv.cts.TvInputManagerTest"
            + ".EXTENSION_INTERFACE_NAME_WITH_PERMISSION_UNGRANTED";
    private static final String PERMISSION_GRANTED =
            "android.media.tv.cts.TvInputManagerTest.PERMISSION_GRANTED";
    private static final String PERMISSION_UNGRANTED =
            "android.media.tv.cts.TvInputManagerTest.PERMISSION_UNGRANTED";

    private static final TvContentRating DUMMY_RATING = TvContentRating.createRating(
            "com.android.tv", "US_TV", "US_TV_PG", "US_TV_D", "US_TV_L");

    private static final String PERMISSION_ACCESS_WATCHED_PROGRAMS =
            "com.android.providers.tv.permission.ACCESS_WATCHED_PROGRAMS";
    private static final String PERMISSION_WRITE_EPG_DATA =
            "com.android.providers.tv.permission.WRITE_EPG_DATA";
    private static final String PERMISSION_ACCESS_TUNED_INFO =
            "android.permission.ACCESS_TUNED_INFO";
    private static final String PERMISSION_TV_INPUT_HARDWARE =
            "android.permission.TV_INPUT_HARDWARE";
    private static final String PERMISSION_TUNER_RESOURCE_ACCESS =
            "android.permission.TUNER_RESOURCE_ACCESS";
    private static final String PERMISSION_TIS_EXTENSION_INTERFACE =
            "android.permission.TIS_EXTENSION_INTERFACE";
    private static final String[] BASE_SHELL_PERMISSIONS = {
            PERMISSION_ACCESS_WATCHED_PROGRAMS,
            PERMISSION_WRITE_EPG_DATA,
            PERMISSION_ACCESS_TUNED_INFO,
            PERMISSION_TUNER_RESOURCE_ACCESS,
            PERMISSION_TIS_EXTENSION_INTERFACE
    };

    private String mStubId;
    private Context mContext;
    private TvInputManager mManager;
    private LoggingCallback mCallback = new LoggingCallback();
    private TvInputInfo mStubTvInputInfo;
    private TvView mTvView;
    private Activity mActivity;
    private Instrumentation mInstrumentation;
    private TvInputInfo mStubTunerTvInputInfo;
    private final MockCallback mMockCallback = new MockCallback();

    private static TvInputInfo getInfoForClassName(List<TvInputInfo> list, String name) {
        for (TvInputInfo info : list) {
            if (info.getServiceInfo().name.equals(name)) {
                return info;
            }
        }
        return null;
    }

    private static boolean isHardwareDeviceAdded(List<TvInputHardwareInfo> list, int deviceId) {
        if (list != null) {
            for (TvInputHardwareInfo info : list) {
                if (info.getDeviceId() == deviceId) {
                    return true;
                }
            }
        }
        return false;
    }

    private String prepareStubHardwareTvInputService() {
        String[] newPermissions = Arrays.copyOf(
                BASE_SHELL_PERMISSIONS, BASE_SHELL_PERMISSIONS.length + 1);
        newPermissions[BASE_SHELL_PERMISSIONS.length] = PERMISSION_TV_INPUT_HARDWARE;
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(newPermissions);

        // Use the test api to add an HDMI hardware device
        mManager.addHardwareDevice(DUMMY_DEVICE_ID);
        assertTrue(isHardwareDeviceAdded(mManager.getHardwareList(), DUMMY_DEVICE_ID));

        PackageManager pm = getActivity().getPackageManager();
        ComponentName component =
                new ComponentName(getActivity(), StubHardwareTvInputService.class);
        pm.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                return null != getInfoForClassName(
                        mManager.getTvInputList(), StubHardwareTvInputService.class.getName());
            }
        }.run();

        TvInputInfo info = getInfoForClassName(
                mManager.getTvInputList(), StubHardwareTvInputService.class.getName());
        assertNotNull(info);
        return info.getId();
    }

    private void cleanupStubHardwareTvInputService() {
        // Restore the base shell permissions
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(BASE_SHELL_PERMISSIONS);

        PackageManager pm = getActivity().getPackageManager();
        ComponentName component =
                new ComponentName(getActivity(), StubHardwareTvInputService.class);
        pm.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                return null == getInfoForClassName(
                        mManager.getTvInputList(), StubHardwareTvInputService.class.getName());
            }
        }.run();

        mManager.removeHardwareDevice(DUMMY_DEVICE_ID);
    }

    public TvInputManagerTest() {
        super(TvViewStubActivity.class);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mActivity = getActivity();
        if (!Utils.hasTvInputFramework(mActivity)) {
            return;
        }

        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(BASE_SHELL_PERMISSIONS);

        mInstrumentation = getInstrumentation();
        mContext = mInstrumentation.getTargetContext();
        mTvView = findTvViewById(R.id.tvview);
        mManager = (TvInputManager) mActivity.getSystemService(Context.TV_INPUT_SERVICE);
        mStubId = getInfoForClassName(
                mManager.getTvInputList(), StubTvInputService2.class.getName()).getId();
        mStubTvInputInfo = getInfoForClassName(
                mManager.getTvInputList(), StubTvInputService2.class.getName());
        for (TvInputInfo info : mManager.getTvInputList()) {
            if (info.getServiceInfo().name.equals(StubTunerTvInputService.class.getName())) {
                mStubTunerTvInputInfo = info;
                break;
            }
        }
        assertNotNull(mStubTunerTvInputInfo);
        mTvView.setCallback(mMockCallback);
    }

    @Override
    protected void tearDown() throws Exception {
        if (!Utils.hasTvInputFramework(getActivity())) {
            super.tearDown();
            return;
        }
        StubTunerTvInputService.deleteChannels(
                mActivity.getContentResolver(), mStubTunerTvInputInfo);
        StubTunerTvInputService.clearTracks();
        try {
            runTestOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mTvView.reset();
                }
            });
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
        mInstrumentation.waitForIdleSync();

        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
        super.tearDown();
    }

    private TvView findTvViewById(int id) {
        return (TvView) mActivity.findViewById(id);
    }

    private void tryTuneAllChannels() throws Throwable {
        StubTunerTvInputService.insertChannels(
                mActivity.getContentResolver(), mStubTunerTvInputInfo);

        Uri uri = TvContract.buildChannelsUriForInput(mStubTunerTvInputInfo.getId());
        String[] projection = { TvContract.Channels._ID };
        try (Cursor cursor = mActivity.getContentResolver().query(
                uri, projection, null, null, null)) {
            while (cursor != null && cursor.moveToNext()) {
                long channelId = cursor.getLong(0);
                Uri channelUri = TvContract.buildChannelUri(channelId);
                mCallback.mTunedInfos = null;
                mTvView.tune(mStubTunerTvInputInfo.getId(), channelUri);
                mInstrumentation.waitForIdleSync();
                new PollingCheck(TIME_OUT_MS) {
                    @Override
                    protected boolean check() {
                        return mMockCallback.isVideoAvailable(mStubTunerTvInputInfo.getId());
                    }
                }.run();
                new PollingCheck(TIME_OUT_MS) {
                    @Override
                    protected boolean check() {
                        return mCallback.mTunedInfos != null;
                    }
                }.run();

                List<TunedInfo> returnedInfos = mManager.getCurrentTunedInfos();
                assertEquals(1, returnedInfos.size());
                TunedInfo returnedInfo = returnedInfos.get(0);
                TunedInfo expectedInfo = new TunedInfo(
                        "android.tv.cts/android.media.tv.cts.StubTunerTvInputService",
                        channelUri,
                        false,
                        false,
                        false,
                        TunedInfo.APP_TYPE_SELF,
                        TunedInfo.APP_TAG_SELF);
                assertEquals(expectedInfo, returnedInfo);

                assertEquals(expectedInfo.getAppTag(), returnedInfo.getAppTag());
                assertEquals(expectedInfo.getAppType(), returnedInfo.getAppType());
                assertEquals(expectedInfo.getChannelUri(), returnedInfo.getChannelUri());
                assertEquals(expectedInfo.getInputId(), returnedInfo.getInputId());
                assertEquals(expectedInfo.isMainSession(), returnedInfo.isMainSession());
                assertEquals(expectedInfo.isRecordingSession(), returnedInfo.isRecordingSession());
                assertEquals(expectedInfo.isVisible(), returnedInfo.isVisible());

                assertEquals(1, mCallback.mTunedInfos.size());
                TunedInfo callbackInfo = mCallback.mTunedInfos.get(0);
                assertEquals(expectedInfo, callbackInfo);
            }
        }
    }

    public void testGetCurrentTunedInfos() throws Throwable {
        if (!Utils.hasTvInputFramework(getActivity())) {
            return;
        }
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mManager.registerCallback(mCallback, new Handler());
            }
        });
        tryTuneAllChannels();
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mManager.unregisterCallback(mCallback);
            }
        });
    }

    public void testGetInputState() throws Exception {
        if (!Utils.hasTvInputFramework(getActivity())) {
            return;
        }
        assertEquals(mManager.getInputState(mStubId), TvInputManager.INPUT_STATE_CONNECTED);
    }

    public void testGetTvInputInfo() throws Exception {
        if (!Utils.hasTvInputFramework(getActivity())) {
            return;
        }
        TvInputInfo expected = mManager.getTvInputInfo(mStubId);
        TvInputInfo actual = getInfoForClassName(mManager.getTvInputList(),
                StubTvInputService2.class.getName());
        assertTrue("expected=" + expected + " actual=" + actual,
                TvInputInfoTest.compareTvInputInfos(getActivity(), expected, actual));
    }

    public void testGetTvInputList() throws Exception {
        if (!Utils.hasTvInputFramework(getActivity())) {
            return;
        }
        List<TvInputInfo> list = mManager.getTvInputList();
        for (String name : VALID_TV_INPUT_SERVICES) {
            assertNotNull("getTvInputList() doesn't contain valid input: " + name,
                    getInfoForClassName(list, name));
        }
        for (String name : INVALID_TV_INPUT_SERVICES) {
            assertNull("getTvInputList() contains invalind input: " + name,
                    getInfoForClassName(list, name));
        }
    }

    public void testIsParentalControlsEnabled() {
        if (!Utils.hasTvInputFramework(getActivity())) {
            return;
        }
        try {
            mManager.isParentalControlsEnabled();
        } catch (Exception e) {
            fail();
        }
    }

    public void testIsRatingBlocked() {
        if (!Utils.hasTvInputFramework(getActivity())) {
            return;
        }
        try {
            mManager.isRatingBlocked(DUMMY_RATING);
        } catch (Exception e) {
            fail();
        }
    }

    public void testRegisterUnregisterCallback() {
        if (!Utils.hasTvInputFramework(getActivity())) {
            return;
        }
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    mManager.registerCallback(mCallback, new Handler());
                    mManager.unregisterCallback(mCallback);
                } catch (Exception e) {
                    fail();
                }
            }
        });
        getInstrumentation().waitForIdleSync();
    }

    public void testInputAddedAndRemoved() {
        if (!Utils.hasTvInputFramework(getActivity())) {
            return;
        }
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mManager.registerCallback(mCallback, new Handler());
            }
        });
        getInstrumentation().waitForIdleSync();

        // Test if onInputRemoved() is called.
        mCallback.resetLogs();
        PackageManager pm = getActivity().getPackageManager();
        ComponentName component = new ComponentName(getActivity(), StubTvInputService2.class);
        assertTrue(PackageManager.COMPONENT_ENABLED_STATE_DISABLED != pm.getComponentEnabledSetting(
                component));
        pm.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                return mCallback.isInputRemoved(mStubId);
            }
        }.run();

        // Test if onInputAdded() is called.
        mCallback.resetLogs();
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, pm.getComponentEnabledSetting(
                component));
        pm.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                return mCallback.isInputAdded(mStubId);
            }
        }.run();

        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mManager.unregisterCallback(mCallback);
            }
        });
        getInstrumentation().waitForIdleSync();
    }

    public void testTvInputInfoUpdated() throws IOException, XmlPullParserException {
        if (!Utils.hasTvInputFramework(getActivity())) {
            return;
        }
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mManager.registerCallback(mCallback, new Handler());
            }
        });
        getInstrumentation().waitForIdleSync();

        mCallback.resetLogs();
        TvInputInfo defaultInfo = new TvInputInfo.Builder(getActivity(),
                new ComponentName(getActivity(), StubTunerTvInputService.class)).build();
        TvInputInfo updatedInfo = new TvInputInfo.Builder(getActivity(),
                new ComponentName(getActivity(), StubTunerTvInputService.class))
                        .setTunerCount(10).setCanRecord(true).setCanPauseRecording(false).build();

        mManager.updateTvInputInfo(updatedInfo);
        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                TvInputInfo info = mCallback.getLastUpdatedTvInputInfo();
                return info !=  null && info.getTunerCount() == 10 && info.canRecord()
                        && !info.canPauseRecording();
            }
        }.run();

        mManager.updateTvInputInfo(defaultInfo);
        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                TvInputInfo info = mCallback.getLastUpdatedTvInputInfo();
                return info !=  null && info.getTunerCount() == 1 && !info.canRecord()
                        && info.canPauseRecording();
            }
        }.run();

        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mManager.unregisterCallback(mCallback);
            }
        });
        getInstrumentation().waitForIdleSync();
    }

    public void testAcquireTvInputHardware() {
        if (!Utils.hasTvInputFramework(getActivity()) || mManager == null) {
            return;
        }

        String[] newPermissions = Arrays.copyOf(
                BASE_SHELL_PERMISSIONS, BASE_SHELL_PERMISSIONS.length + 1);
        newPermissions[BASE_SHELL_PERMISSIONS.length] = PERMISSION_TV_INPUT_HARDWARE;
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(newPermissions);

        int deviceId = 0;
        Hardware hardware = null;
        boolean hardwareDeviceAdded = false;

        try {
            // Update hardware device list
            List<TvInputHardwareInfo> hardwareList = mManager.getHardwareList();
            if (hardwareList == null || hardwareList.isEmpty()) {
                // Use the test api to add an HDMI hardware device
                mManager.addHardwareDevice(deviceId);
                hardwareDeviceAdded = true;
            } else {
                deviceId = hardwareList.get(0).getDeviceId();
            }

            // Acquire Hardware with a record client
            HardwareCallback callback = new HardwareCallback() {
                @Override
                public void onReleased() {}

                @Override
                public void onStreamConfigChanged(TvStreamConfig[] configs) {}
            };
            CallbackExecutor executor = new CallbackExecutor();
            hardware = mManager.acquireTvInputHardware(deviceId, mStubTvInputInfo,
                    null /*tvInputSessionId*/, TvInputService.PRIORITY_HINT_USE_CASE_TYPE_PLAYBACK,
                    executor, callback);
            assertNotNull(hardware);

            // Acquire the same device with a LIVE client
            mManager.releaseTvInputHardware(deviceId, hardware);
            hardware = mManager.acquireTvInputHardware(deviceId, mStubTvInputInfo,
                    null /*tvInputSessionId*/, TvInputService.PRIORITY_HINT_USE_CASE_TYPE_LIVE,
                    executor, callback);

            // The request for Live may fail because it have lower priority than Playback
            // assertNotNull(hardware);
        } finally {
            // Clean up
            if (hardware != null) {
                mManager.releaseTvInputHardware(deviceId, hardware);
            }
            if (hardwareDeviceAdded) {
                mManager.removeHardwareDevice(deviceId);
            }
            // Restore the base shell permissions
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .adoptShellPermissionIdentity(BASE_SHELL_PERMISSIONS);
        }
    }

    public void testTvInputHardwareOverrideAudioSink() {
        if (!Utils.hasTvInputFramework(getActivity()) || mManager == null) {
            return;
        }

        String[] newPermissions =
                Arrays.copyOf(BASE_SHELL_PERMISSIONS, BASE_SHELL_PERMISSIONS.length + 1);
        newPermissions[BASE_SHELL_PERMISSIONS.length] = PERMISSION_TV_INPUT_HARDWARE;
        InstrumentationRegistry.getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                newPermissions);

        int deviceId = 0;
        Hardware hardware = null;
        boolean hardwareDeviceAdded = false;

        try {
            // Update hardware device list
            List<TvInputHardwareInfo> hardwareList = mManager.getHardwareList();
            if (hardwareList == null || hardwareList.isEmpty()) {
                // Use the test api to add an HDMI hardware device
                mManager.addHardwareDevice(deviceId);
                hardwareDeviceAdded = true;
            } else {
                deviceId = hardwareList.get(0).getDeviceId();
            }

            // Acquire Hardware with a record client
            HardwareCallback callback = new HardwareCallback() {
                @Override
                public void onReleased() {}

                @Override
                public void onStreamConfigChanged(TvStreamConfig[] configs) {}
            };
            CallbackExecutor executor = new CallbackExecutor();
            hardware = mManager.acquireTvInputHardware(deviceId, mStubTvInputInfo,
                    null /*tvInputSessionId*/, TvInputService.PRIORITY_HINT_USE_CASE_TYPE_PLAYBACK,
                    executor, callback);
            assertNotNull(hardware);

            // Override audio sink
            AudioManager am = mActivity.getSystemService(AudioManager.class);
            AudioDeviceInfo[] deviceInfos = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            if (deviceInfos.length > 0) {
                // test available overrideAudioSink APIs
                hardware.overrideAudioSink(deviceInfos[0], 0,
                        AudioFormat.CHANNEL_OUT_DEFAULT, AudioFormat.ENCODING_DEFAULT);
                hardware.overrideAudioSink(deviceInfos[0].getType(), deviceInfos[0].getAddress(), 0,
                        AudioFormat.CHANNEL_OUT_DEFAULT, AudioFormat.ENCODING_DEFAULT);
            }
        } catch (Exception e) {
            fail();
        } finally {
            if (hardware != null) {
                mManager.releaseTvInputHardware(deviceId, hardware);
            }
            if (hardwareDeviceAdded) {
                mManager.removeHardwareDevice(deviceId);
            }
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .adoptShellPermissionIdentity(BASE_SHELL_PERMISSIONS);
        }
    }

    public void testTvInputHardwareSetSurface() {
        if (!Utils.hasTvInputFramework(getActivity()) || mManager == null) {
            return;
        }

        String[] newPermissions =
                Arrays.copyOf(BASE_SHELL_PERMISSIONS, BASE_SHELL_PERMISSIONS.length + 1);
        newPermissions[BASE_SHELL_PERMISSIONS.length] = PERMISSION_TV_INPUT_HARDWARE;
        InstrumentationRegistry.getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                newPermissions);

        int deviceId = 0;
        Hardware hardware = null;
        SurfaceTexture dummySurfaceTexture = null;
        Surface dummySurface = null;

        try {
            dummySurfaceTexture = new SurfaceTexture(1);
            dummySurface = new Surface(dummySurfaceTexture);
            List<TvInputHardwareInfo> hardwareList = mManager.getHardwareList();
            if (hardwareList == null || hardwareList.isEmpty()) {
                Log.w(TAG, "No hardware found, skip testTvInputHardwareSetSurface.");
                return;
            }

            deviceId = hardwareList.get(0).getDeviceId();
            final List<TvStreamConfig> configList = new ArrayList<TvStreamConfig>();

            // Acquire Hardware with a record client
            HardwareCallback callback = new HardwareCallback() {
                @Override
                public void onReleased() {}

                @Override
                public void onStreamConfigChanged(TvStreamConfig[] configs) {
                    configList.addAll(Arrays.asList(configs));
                }
            };
            CallbackExecutor executor = new CallbackExecutor();
            hardware = mManager.acquireTvInputHardware(deviceId, mStubTvInputInfo,
                    null /*tvInputSessionId*/, TvInputService.PRIORITY_HINT_USE_CASE_TYPE_PLAYBACK,
                    executor, callback);
            assertNotNull(hardware);
            PollingCheck.waitFor(TIME_OUT_MS, () -> !configList.isEmpty());

            assertTrue(hardware.setSurface(dummySurface, configList.get(0)));
            assertTrue(hardware.setSurface(null, null));
        } finally {
            if (dummySurface != null) {
                dummySurface.release();
            }
            if (dummySurfaceTexture != null) {
                dummySurfaceTexture.release();
            }
            if (hardware != null) {
                mManager.releaseTvInputHardware(deviceId, hardware);
            }
            // Restore the base shell permissions
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .adoptShellPermissionIdentity(BASE_SHELL_PERMISSIONS);
        }
    }

    public void testGetAvailableExtensionInterfaceNames() {
        if (!Utils.hasTvInputFramework(getActivity())) {
            return;
        }

        try {
            String inputId = prepareStubHardwareTvInputService();

            StubHardwareTvInputService.injectAvailableExtensionInterface(
                    EXTENSION_INTERFACE_NAME_WITHOUT_PERMISSION, null);
            StubHardwareTvInputService.injectAvailableExtensionInterface(
                    EXTENSION_INTERFACE_NAME_WITH_PERMISSION_GRANTED, PERMISSION_GRANTED);
            StubHardwareTvInputService.injectAvailableExtensionInterface(
                    EXTENSION_INTERFACE_NAME_WITH_PERMISSION_UNGRANTED, PERMISSION_UNGRANTED);

            List<String> names = mManager.getAvailableExtensionInterfaceNames(inputId);
            assertTrue(names != null && !names.isEmpty());
            assertTrue(names.contains(EXTENSION_INTERFACE_NAME_WITHOUT_PERMISSION));
            assertTrue(names.contains(EXTENSION_INTERFACE_NAME_WITH_PERMISSION_GRANTED));
            assertFalse(names.contains(EXTENSION_INTERFACE_NAME_WITH_PERMISSION_UNGRANTED));

            StubHardwareTvInputService.clearAvailableExtensionInterfaces();

            names = mManager.getAvailableExtensionInterfaceNames(inputId);
            assertTrue(names != null && names.isEmpty());
        } finally {
            StubHardwareTvInputService.clearAvailableExtensionInterfaces();
            cleanupStubHardwareTvInputService();
        }
    }

    public void testGetExtensionInterface() {
        if (!Utils.hasTvInputFramework(getActivity())) {
            return;
        }

        try {
            String inputId = prepareStubHardwareTvInputService();

            StubHardwareTvInputService.injectAvailableExtensionInterface(
                    EXTENSION_INTERFACE_NAME_WITHOUT_PERMISSION, null);
            StubHardwareTvInputService.injectAvailableExtensionInterface(
                    EXTENSION_INTERFACE_NAME_WITH_PERMISSION_GRANTED, PERMISSION_GRANTED);
            StubHardwareTvInputService.injectAvailableExtensionInterface(
                    EXTENSION_INTERFACE_NAME_WITH_PERMISSION_UNGRANTED, PERMISSION_UNGRANTED);

            assertNotNull(mManager.getExtensionInterface(inputId,
                    EXTENSION_INTERFACE_NAME_WITHOUT_PERMISSION));
            assertNotNull(mManager.getExtensionInterface(inputId,
                    EXTENSION_INTERFACE_NAME_WITH_PERMISSION_GRANTED));
            assertNull(mManager.getExtensionInterface(inputId,
                    EXTENSION_INTERFACE_NAME_WITH_PERMISSION_UNGRANTED));
        } finally {
            StubHardwareTvInputService.clearAvailableExtensionInterfaces();
            cleanupStubHardwareTvInputService();
        }
    }

    public void testGetClientPriority() {
        if (!Utils.hasTvInputFramework(getActivity()) || !Utils.hasTunerFeature(getActivity())) {
            return;
        }

        // Use the test api to get priorities in tunerResourceManagerUseCaseConfig.xml
        TunerResourceManager trm = (TunerResourceManager) getActivity()
            .getSystemService(Context.TV_TUNER_RESOURCE_MGR_SERVICE);
        int fgLivePriority = trm.getConfigPriority(TvInputService.PRIORITY_HINT_USE_CASE_TYPE_LIVE,
                true);
        int bgLivePriority = trm.getConfigPriority(TvInputService.PRIORITY_HINT_USE_CASE_TYPE_LIVE,
                false);
        int fgDefaultPriority = trm.getConfigPriority(PRIORITY_HINT_USE_CASE_TYPE_INVALID, true);
        int bgDefaultPriority = trm.getConfigPriority(PRIORITY_HINT_USE_CASE_TYPE_INVALID, false);
        boolean isForeground = checkIsForeground(android.os.Process.myPid());

        int priority = mManager.getClientPriority(TvInputService.PRIORITY_HINT_USE_CASE_TYPE_LIVE);
        assertTrue(priority == (isForeground ? fgLivePriority : bgLivePriority));

        try {
            priority = mManager.getClientPriority(
                    PRIORITY_HINT_USE_CASE_TYPE_INVALID /* invalid use case type */);
        } catch (IllegalArgumentException e) {
            // pass
        }

        Handler handler = new Handler(Looper.getMainLooper());
        final SessionCallback sessionCallback = new SessionCallback();
        mManager.createSession(mStubId, mContext.getAttributionSource(), sessionCallback, handler);
        PollingCheck.waitFor(TIME_OUT_MS, () -> sessionCallback.getSession() != null);
        Session session = sessionCallback.getSession();
        String sessionId = StubTvInputService2.getSessionId();
        assertNotNull(sessionId);

        priority = mManager.getClientPriority(
                TvInputService.PRIORITY_HINT_USE_CASE_TYPE_LIVE, sessionId /* valid sessionId */);
        assertTrue(priority == (isForeground ? fgLivePriority : bgLivePriority));

        try {
            priority = mManager.getClientPriority(
                    PRIORITY_HINT_USE_CASE_TYPE_INVALID /* invalid use case type */,
                    sessionId /* valid sessionId */);
        } catch (IllegalArgumentException e) {
            // pass
        }

        session.release();
        PollingCheck.waitFor(TIME_OUT_MS, () -> StubTvInputService2.getSessionId() == null);

        priority = mManager.getClientPriority(
                TvInputService.PRIORITY_HINT_USE_CASE_TYPE_LIVE, sessionId /* invalid sessionId */);
        assertTrue(priority == bgLivePriority);

        try {
            priority = mManager.getClientPriority(
                    PRIORITY_HINT_USE_CASE_TYPE_INVALID /* invalid use case type */,
                    sessionId /* invalid sessionId */);
        } catch (IllegalArgumentException e) {
            // pass
        }
    }

    public void testGetClientPid() {
        if (!Utils.hasTvInputFramework(getActivity())) {
            return;
        }

        Handler handler = new Handler(Looper.getMainLooper());
        final SessionCallback sessionCallback = new SessionCallback();
        mManager.createSession(mStubId, mContext.getAttributionSource(), sessionCallback, handler);
        PollingCheck.waitFor(TIME_OUT_MS, () -> sessionCallback.getSession() != null);
        Session session = sessionCallback.getSession();
        String sessionId = StubTvInputService2.getSessionId();
        assertNotNull(sessionId);

        int pid = mManager.getClientPid(sessionId);
        assertTrue(pid == android.os.Process.myPid());

        session.release();
        PollingCheck.waitFor(TIME_OUT_MS, () -> StubTvInputService2.getSessionId() == null);
    }

    private static class LoggingCallback extends TvInputManager.TvInputCallback {
        private final List<String> mAddedInputs = new ArrayList<>();
        private final List<String> mRemovedInputs = new ArrayList<>();
        private TvInputInfo mLastUpdatedTvInputInfo;
        private List<TunedInfo> mTunedInfos;

        @Override
        public synchronized void onInputAdded(String inputId) {
            mAddedInputs.add(inputId);
        }

        @Override
        public synchronized void onInputRemoved(String inputId) {
            mRemovedInputs.add(inputId);
        }

        @Override
        public synchronized void onTvInputInfoUpdated(TvInputInfo info) {
            mLastUpdatedTvInputInfo = info;
        }

        @Override
        public synchronized void onCurrentTunedInfosUpdated(
                List<TunedInfo> tunedInfos) {
            super.onCurrentTunedInfosUpdated(tunedInfos);
            mTunedInfos = tunedInfos;
        }

        public synchronized void resetLogs() {
            mAddedInputs.clear();
            mRemovedInputs.clear();
            mLastUpdatedTvInputInfo = null;
        }

        public synchronized boolean isInputAdded(String inputId) {
            return mRemovedInputs.isEmpty() && mAddedInputs.size() == 1 && mAddedInputs.contains(
                    inputId);
        }

        public synchronized boolean isInputRemoved(String inputId) {
            return mAddedInputs.isEmpty() && mRemovedInputs.size() == 1 && mRemovedInputs.contains(
                    inputId);
        }

        public synchronized TvInputInfo getLastUpdatedTvInputInfo() {
            return mLastUpdatedTvInputInfo;
        }
    }

    public static class StubTvInputService2 extends StubTvInputService {
        static String sTvInputSessionId;

        public static String getSessionId() {
            return sTvInputSessionId;
        }

        @Override
        public Session onCreateSession(String inputId, String tvInputSessionId) {
            sTvInputSessionId = tvInputSessionId;
            return new StubSessionImpl2(this);
        }

        public static class StubSessionImpl2 extends StubTvInputService.StubSessionImpl {
            StubSessionImpl2(Context context) {
                super(context);
            }

            @Override
            public void onRelease() {
                sTvInputSessionId = null;
            }
        }
    }

    public static class StubHardwareTvInputService extends TvInputService {
        private static final Map<String, String> sAvailableExtensionInterfaceMap = new HashMap<>();

        private ResolveInfo mResolveInfo = null;
        private TvInputInfo mTvInputInfo = null;

        public static void clearAvailableExtensionInterfaces() {
            sAvailableExtensionInterfaceMap.clear();
        }

        public static void injectAvailableExtensionInterface(String name, String permission) {
            sAvailableExtensionInterfaceMap.put(name, permission);
        }

        @Override
        public void onCreate() {
            mResolveInfo = getPackageManager().resolveService(
                    new Intent(SERVICE_INTERFACE).setClass(this, getClass()),
                    PackageManager.GET_SERVICES | PackageManager.GET_META_DATA);
        }

        @Override
        public  TvInputInfo onHardwareAdded(TvInputHardwareInfo hardwareInfo) {
            TvInputInfo info = null;
            if (hardwareInfo.getDeviceId() == DUMMY_DEVICE_ID) {
                info = new TvInputInfo.Builder(this, mResolveInfo)
                        .setTvInputHardwareInfo(hardwareInfo)
                        .build();
                mTvInputInfo = info;
            }
            return info;
        }

        @Override
        public String onHardwareRemoved(TvInputHardwareInfo hardwareInfo) {
            String inputId = null;
            if (hardwareInfo.getDeviceId() == DUMMY_DEVICE_ID && mTvInputInfo != null) {
                inputId = mTvInputInfo.getId();
                mTvInputInfo = null;
            }
            return inputId;
        }

        @Override
        public Session onCreateSession(String inputId) {
            return null;
        }

        @Override
        public List<String> getAvailableExtensionInterfaceNames() {
            super.getAvailableExtensionInterfaceNames();
            return new ArrayList<>(sAvailableExtensionInterfaceMap.keySet());
        }

        @Override
        public String getExtensionInterfacePermission(String name) {
            super.getExtensionInterfacePermission(name);
            return sAvailableExtensionInterfaceMap.get(name);
        }

        @Override
        public IBinder getExtensionInterface(String name) {
            super.getExtensionInterface(name);
            if (sAvailableExtensionInterfaceMap.containsKey(name)) {
                return new Binder();
            } else {
                return null;
            }
        }
    }

    public class CallbackExecutor implements Executor {
        @Override
        public void execute(Runnable r) {
            r.run();
        }
    }

    private class SessionCallback extends TvInputManager.SessionCallback {
        private TvInputManager.Session mSession;

        public TvInputManager.Session getSession() {
            return mSession;
        }

        @Override
        public void onSessionCreated(TvInputManager.Session session) {
            mSession = session;
        }
    }

    private boolean checkIsForeground(int pid) {
        ActivityManager am = (ActivityManager) getActivity()
            .getSystemService(Context.ACTIVITY_SERVICE);
        List<RunningAppProcessInfo> appProcesses = am.getRunningAppProcesses();
        if (appProcesses == null) {
            return false;
        }
        for (RunningAppProcessInfo appProcess : appProcesses) {
            if (appProcess.pid == pid
                    && appProcess.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                return true;
            }
        }
        return false;
    }
}

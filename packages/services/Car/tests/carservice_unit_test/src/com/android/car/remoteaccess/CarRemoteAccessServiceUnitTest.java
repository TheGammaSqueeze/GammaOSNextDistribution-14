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

package com.android.car.remoteaccess;

import static android.car.remoteaccess.CarRemoteAccessManager.TASK_TYPE_CUSTOM;
import static android.car.remoteaccess.CarRemoteAccessManager.TASK_TYPE_ENTER_GARAGE_MODE;
import static android.car.remoteaccess.ICarRemoteAccessService.SERVICE_ERROR_CODE_GENERAL;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_UNLOCKED;

import static com.android.car.remoteaccess.RemoteAccessStorage.RemoteAccessDbHelper.DATABASE_NAME;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.Car;
import android.car.feature.FakeFeatureFlagsImpl;
import android.car.feature.Flags;
import android.car.hardware.power.CarPowerManager;
import android.car.hardware.power.ICarPowerStateListener;
import android.car.remoteaccess.CarRemoteAccessManager;
import android.car.remoteaccess.ICarRemoteAccessCallback;
import android.car.remoteaccess.RemoteTaskClientRegistrationInfo;
import android.car.remoteaccess.TaskScheduleInfo;
import android.car.test.AbstractExpectableTestCase;
import android.car.user.CarUserManager.UserLifecycleEvent;
import android.car.user.CarUserManager.UserLifecycleListener;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.hardware.automotive.remoteaccess.ScheduleInfo;
import android.hardware.automotive.remoteaccess.TaskType;
import android.net.Uri;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.Xml;

import androidx.test.InstrumentationRegistry;

import com.android.car.CarLocalServices;
import com.android.car.CarServiceUtils;
import com.android.car.R;
import com.android.car.hal.PowerHalService;
import com.android.car.power.CarPowerManagementService;
import com.android.car.remoteaccess.RemoteAccessStorage.ClientIdEntry;
import com.android.car.remoteaccess.hal.RemoteAccessHalCallback;
import com.android.car.remoteaccess.hal.RemoteAccessHalWrapper;
import com.android.car.systeminterface.SystemInterface;
import com.android.car.user.CarUserService;
import com.android.compatibility.common.util.PollingCheck;
import com.android.internal.annotations.GuardedBy;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.StringReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@RunWith(MockitoJUnitRunner.class)
public final class CarRemoteAccessServiceUnitTest extends AbstractExpectableTestCase {

    private static final String TAG = CarRemoteAccessServiceUnitTest.class.getSimpleName();
    private static final long WAIT_TIMEOUT_MS = 5000;
    private static final long ALLOWED_SYSTEM_UP_TIME_FOR_TESTING_MS = 5000;
    private static final int TASK_UNBIND_DELAY_MS = 1000;
    private static final String WAKEUP_SERVICE_NAME = "android_wakeup_service";
    private static final String TEST_VEHICLE_ID = "test_vehicle";
    private static final String TEST_PROCESSOR_ID = "test_processor";
    private static final String PERMISSION_NOT_GRANTED_PACKAGE = "life.is.beautiful";
    private static final String PERMISSION_GRANTED_PACKAGE_ONE = "we.are.the.world";
    private static final String PERMISSION_GRANTED_PACKAGE_TWO = "android.automotive.os";
    private static final String SERVERLESS_PACKAGE = "android.car.app1";
    private static final int UID_PERMISSION_NOT_GRANTED_PACKAGE = 1;
    private static final int UID_PERMISSION_GRANTED_PACKAGE_ONE = 2;
    private static final int UID_PERMISSION_GRANTED_PACKAGE_TWO = 3;
    private static final int UID_SERVERLESS_PACKAGE = 4;
    private static final String CLIENT_ID_SERVERLESS = "serverless_client_1234";
    // UID name does not necessarily equal to package name.
    private static final String UID_NAME_SERVERLESS_PACKAGE = "android.car.app1:u1234";
    private static final String CLASS_NAME_ONE = "Hello";
    private static final String CLASS_NAME_TWO = "Best";
    private static final List<PackagePrepForTest> AVAILABLE_PACKAGES = List.of(
            createPackagePrepForTest(PERMISSION_GRANTED_PACKAGE_ONE,
                    CLASS_NAME_ONE, /* permissionGranted= */ true,
                    UID_PERMISSION_GRANTED_PACKAGE_ONE),
            createPackagePrepForTest(PERMISSION_GRANTED_PACKAGE_TWO,
                    CLASS_NAME_TWO, /* permissionGranted= */ true,
                    UID_PERMISSION_GRANTED_PACKAGE_TWO),
            createPackagePrepForTest(SERVERLESS_PACKAGE,
                    CLASS_NAME_TWO, /* permissionGranted= */ true,
                    UID_SERVERLESS_PACKAGE),
            createPackagePrepForTest(PERMISSION_NOT_GRANTED_PACKAGE, "Happy",
                    /* permissionGranted= */ false,
                    UID_PERMISSION_NOT_GRANTED_PACKAGE)
    );
    // Except for PERMISSION_NOT_GRANTED_PACKAGE, the other packages are valid.
    private static final int VALID_PACKAGE_COUNT = 3;
    private static final List<ClientIdEntry> PERSISTENT_CLIENTS = List.of(
            new ClientIdEntry("12345", System.currentTimeMillis(), "we.are.the.world"),
            new ClientIdEntry("98765", System.currentTimeMillis(), "android.automotive.os")
    );
    private static final String TEST_SERVERLESS_CLIENT_ID = "serverless_client_1234";
    private static final String EMPTY_SERVERLESS_CLIENT_MAP_XML =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
            + "<ServerlessClientMap xmlns:car=\"http://schemas.android.com/apk/res-auto\">"
            + "</ServerlessClientMap>";
    private static final String SERVERLESS_CLIENT_MAP_XML =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
            + "<ServerlessClientMap xmlns:car=\"http://schemas.android.com/apk/res-auto\">"
            + "  <ServerlessClient>"
            + "    <ClientId>" + TEST_SERVERLESS_CLIENT_ID + "</ClientId>"
            + "    <PackageName>" + SERVERLESS_PACKAGE + "</PackageName>"
            + "  </ServerlessClient>"
            + "  <ServerlessClient>"
            + "    <ClientId>serverless_client_2345</ClientId>"
            + "    <PackageName>android.car.app2</PackageName>"
            + "  </ServerlessClient>"
            + "</ServerlessClientMap>";
    private static final String SERVERLESS_CLIENT_MAP_WITH_COMMENT_XML =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
            + "\n"
            + "<!-- comment -->"
            + "<ServerlessClientMap xmlns:car=\"http://schemas.android.com/apk/res-auto\">"
            + "  <ServerlessClient>"
            + "    <ClientId>" + TEST_SERVERLESS_CLIENT_ID + "</ClientId>"
            + "    <PackageName>" + SERVERLESS_PACKAGE + "</PackageName>"
            + "  </ServerlessClient>"
            + "  <ServerlessClient>"
            + "    <ClientId>serverless_client_2345</ClientId>"
            + "    <PackageName>android.car.app2</PackageName>"
            + "  </ServerlessClient>"
            + "</ServerlessClientMap>";
    private static final int TEST_TASK_TYPE = TASK_TYPE_CUSTOM;
    private static final String TEST_SCHEDULE_ID = "TEST_SCHEDULE_ID";
    private static final byte[] TEST_TASK_DATA = new byte[]{(byte) 0xDE, (byte) 0xAD, (byte) 0xBE,
            (byte) 0xEF};
    private static final int TEST_TASK_COUNT = 1234;
    private static final long TEST_START_TIME = 2345;
    private static final long TEST_PERIODIC = 3456;

    private CarRemoteAccessService mService;
    private ICarRemoteAccessCallbackImpl mRemoteAccessCallback;
    private CarPowerManagementService mOldCarPowerManagementService;
    private CarUserService mOldCarUserService;
    private File mDatabaseFile;
    private Context mContext;
    private RemoteAccessStorage mRemoteAccessStorage;
    private Runnable mBootComplete;
    private boolean mBootCompleted;
    private BroadcastReceiver mBroadcastReceiver;

    @Mock private Resources mResources;
    @Mock private PackageManager mPackageManager;
    @Mock private RemoteAccessHalWrapper mRemoteAccessHalWrapper;
    @Mock private SystemInterface mSystemInterface;
    @Mock private CarPowerManagementService mCarPowerManagementService;
    @Mock private CarUserService mCarUserService;
    @Mock private PowerHalService mPowerHalService;
    @Mock private CarRemoteAccessService.CarRemoteAccessServiceDep mDep;
    @Mock private UserManager mUserManager;

    @Captor private ArgumentCaptor<UserLifecycleListener> mUserLifecycleListenerCaptor;
    @Captor private ArgumentCaptor<RemoteTaskClientRegistrationInfo> mRegistrationInfoCaptor;
    @Captor private ArgumentCaptor<ScheduleInfo> mHalScheduleInfoCaptor;

    private CarRemoteAccessService newServiceWithSystemUpTime(long systemUpTime) {
        CarRemoteAccessService service =  new CarRemoteAccessService(mContext, mSystemInterface,
                mPowerHalService, mDep, /* remoteAccessHal= */ null, mRemoteAccessStorage,
                systemUpTime, /* inMemoryStorage= */ true);
        service.setRemoteAccessHalWrapper(mRemoteAccessHalWrapper);
        return service;
    }

    @Before
    public void setUp() throws Exception {
        mOldCarPowerManagementService = CarLocalServices.getService(
                CarPowerManagementService.class);
        CarLocalServices.removeServiceForTest(CarPowerManagementService.class);
        CarLocalServices.addService(CarPowerManagementService.class, mCarPowerManagementService);
        mOldCarUserService = CarLocalServices.getService(CarUserService.class);
        CarLocalServices.removeServiceForTest(CarUserService.class);
        CarLocalServices.addService(CarUserService.class, mCarUserService);

        mContext = InstrumentationRegistry.getTargetContext().createDeviceProtectedStorageContext();
        spyOn(mContext);

        // doReturn().when() pattern is necessary because mContext is spied.
        doReturn(mPackageManager).when(mContext).getPackageManager();
        doReturn(mResources).when(mContext).getResources();
        doReturn(mUserManager).when(mContext).getSystemService(UserManager.class);
        doReturn(true).when(mContext).bindServiceAsUser(any(), any(), anyInt(), any());
        doAnswer(i -> {
            mBroadcastReceiver = (BroadcastReceiver) i.getArguments()[0];
            return null;
        }).when(mContext).registerReceiver(any(), any(), anyInt());
        doNothing().when(mContext).unregisterReceiver(any());
        doNothing().when(mContext).unbindService(any());
        when(mUserManager.isUserUnlocked(any())).thenReturn(true);
        mDatabaseFile = mContext.getDatabasePath(DATABASE_NAME);
        when(mResources.getInteger(R.integer.config_allowedSystemUptimeForRemoteAccess))
                .thenReturn(300);
        when(mResources.getInteger(R.integer.config_notifyApStateChange_max_retry)).thenReturn(10);
        when(mResources.getInteger(R.integer.config_notifyApStateChange_retry_sleep_ms))
                .thenReturn(100);
        XmlResourceParser fakeXmlResourceParser = getFakeXmlResourceParser(
                EMPTY_SERVERLESS_CLIENT_MAP_XML);
        when(mResources.getXml(R.xml.remote_access_serverless_client_map)).thenReturn(
                fakeXmlResourceParser);
        when(mRemoteAccessHalWrapper.getWakeupServiceName()).thenReturn(WAKEUP_SERVICE_NAME);
        when(mRemoteAccessHalWrapper.getVehicleId()).thenReturn(TEST_VEHICLE_ID);
        when(mRemoteAccessHalWrapper.getProcessorId()).thenReturn(TEST_PROCESSOR_ID);
        when(mRemoteAccessHalWrapper.notifyApStateChange(anyBoolean(), anyBoolean()))
                .thenReturn(true);
        // By default task scheduling is not supported.
        when(mRemoteAccessHalWrapper.isTaskScheduleSupported()).thenReturn(false);
        when(mCarPowerManagementService.getLastShutdownState())
                .thenReturn(CarRemoteAccessManager.NEXT_POWER_STATE_OFF);
        when(mSystemInterface.getSystemCarDir()).thenReturn(mDatabaseFile.getParentFile());
        mockPackageInfo();
        setVehicleInUse(/* inUse= */ false);

        when(mPackageManager.getNameForUid(UID_PERMISSION_NOT_GRANTED_PACKAGE)).thenReturn(
                PERMISSION_NOT_GRANTED_PACKAGE);
        when(mPackageManager.getNameForUid(UID_PERMISSION_GRANTED_PACKAGE_ONE)).thenReturn(
                PERMISSION_GRANTED_PACKAGE_ONE);
        when(mPackageManager.getNameForUid(UID_PERMISSION_GRANTED_PACKAGE_TWO)).thenReturn(
                PERMISSION_GRANTED_PACKAGE_TWO);
        when(mPackageManager.getNameForUid(UID_SERVERLESS_PACKAGE)).thenReturn(
                UID_NAME_SERVERLESS_PACKAGE);
        when(mPackageManager.getPackagesForUid(UID_PERMISSION_NOT_GRANTED_PACKAGE)).thenReturn(
                new String[]{PERMISSION_NOT_GRANTED_PACKAGE});
        when(mPackageManager.getPackagesForUid(UID_PERMISSION_GRANTED_PACKAGE_ONE)).thenReturn(
                new String[]{PERMISSION_GRANTED_PACKAGE_ONE});
        when(mPackageManager.getPackagesForUid(UID_PERMISSION_GRANTED_PACKAGE_TWO)).thenReturn(
                new String[]{PERMISSION_GRANTED_PACKAGE_TWO});
        when(mPackageManager.getPackagesForUid(UID_SERVERLESS_PACKAGE)).thenReturn(
                new String[]{SERVERLESS_PACKAGE});

        mRemoteAccessCallback = new ICarRemoteAccessCallbackImpl();
        mRemoteAccessStorage = new RemoteAccessStorage(mContext, mSystemInterface,
                /* inMemoryStorage= */ true);
        mService = newServiceWithSystemUpTime(ALLOWED_SYSTEM_UP_TIME_FOR_TESTING_MS);

        FakeFeatureFlagsImpl fakeFlagsImpl = new FakeFeatureFlagsImpl();
        fakeFlagsImpl.setFlag(Flags.FLAG_SERVERLESS_REMOTE_ACCESS, true);
        mService.setFeatureFlags(fakeFlagsImpl);
    }

    @After
    public void tearDown() {
        mService.release();
        CarServiceUtils.finishAllHandlerTasks();

        CarLocalServices.removeServiceForTest(CarPowerManagementService.class);
        CarLocalServices.addService(CarPowerManagementService.class, mOldCarPowerManagementService);
        CarLocalServices.removeServiceForTest(CarUserService.class);
        CarLocalServices.addService(CarUserService.class, mOldCarUserService);

        if (mDatabaseFile.exists() && !mDatabaseFile.delete()) {
            Log.e(TAG, "Failed to delete the database file: " + mDatabaseFile.getAbsolutePath());
        }
    }

    private void verifyBindingStartedForPackages(String[] packageNames, String[] classNames) {
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);

        mService.init();

        verify(mContext, times(packageNames.length)).bindServiceAsUser(intentCaptor.capture(),
                any(ServiceConnection.class), anyInt(), any(UserHandle.class));

        Set<ComponentName> gotComponents = new ArraySet<>();
        Set<ComponentName> wantComponents = new ArraySet<>();
        for (int i = 0; i < packageNames.length; i++) {
            Intent intent = intentCaptor.getAllValues().get(i);
            gotComponents.add(intent.getComponent());
            wantComponents.add(new ComponentName(packageNames[i], classNames[i]));
        }

        assertThat(gotComponents).isEqualTo(wantComponents);
    }

    @Test
    public void testStartRemoteTaskClientService() {
        String[] packageNames = new String[]{PERMISSION_GRANTED_PACKAGE_ONE,
                PERMISSION_GRANTED_PACKAGE_TWO, SERVERLESS_PACKAGE};
        String[] classNames = new String[]{CLASS_NAME_ONE, CLASS_NAME_TWO, CLASS_NAME_TWO};

        mService.setAllowedTimeForRemoteTaskClientInitMs(100);
        runBootComplete();
        mService.init();

        verifyBindingStartedForPackages(packageNames, classNames);
        verify(mContext, timeout(WAIT_TIMEOUT_MS).times(VALID_PACKAGE_COUNT)).unbindService(any());
    }

    @Test
    public void testStartRemoteTaskClientServiceUserLocked() {
        String[] packageNames = new String[]{PERMISSION_GRANTED_PACKAGE_ONE,
                PERMISSION_GRANTED_PACKAGE_TWO, SERVERLESS_PACKAGE};
        String[] classNames = new String[]{CLASS_NAME_ONE, CLASS_NAME_TWO, CLASS_NAME_TWO};
        when(mUserManager.isUserUnlocked(any())).thenReturn(false);

        runBootComplete();
        mService.init();

        verify(mUserManager, times(VALID_PACKAGE_COUNT)).isUserUnlocked(eq(UserHandle.SYSTEM));
        verify(mCarUserService, times(VALID_PACKAGE_COUNT)).addUserLifecycleListener(any(),
                mUserLifecycleListenerCaptor.capture());
        verify(mContext, never()).bindServiceAsUser(any(), any(), anyInt(), any());

        for (int i = 0; i < packageNames.length; i++) {
            UserLifecycleListener listener = mUserLifecycleListenerCaptor.getAllValues().get(i);
            listener.onEvent(new UserLifecycleEvent(USER_LIFECYCLE_EVENT_TYPE_UNLOCKED,
                    UserHandle.USER_SYSTEM));
        }

        verifyBindingStartedForPackages(packageNames, classNames);
    }

    // If bindService is called after user unlock but before the intent arrives, we must make sure
    // only one binding is going to happen.
    @Test
    public void testStartRemoteTaskClientServiceUserLocked_bindAgainAfterUnlock() throws Exception {
        when(mUserManager.isUserUnlocked(any())).thenReturn(false);

        runBootComplete();
        mService.init();

        verify(mUserManager, times(VALID_PACKAGE_COUNT)).isUserUnlocked(eq(UserHandle.SYSTEM));
        verify(mCarUserService, times(VALID_PACKAGE_COUNT)).addUserLifecycleListener(any(),
                mUserLifecycleListenerCaptor.capture());
        verify(mContext, never()).bindServiceAsUser(any(), any(), anyInt(), any());

        // Simulate a task arrives for PACKAGE_ONE which will try to start it.
        RemoteAccessHalCallback halCallback = prepareCarRemoteTaskClient();
        String clientId = mRemoteAccessCallback.getClientId();
        byte[] data = new byte[]{1, 2, 3, 4};
        halCallback.onRemoteTaskRequested(clientId, data);

        PollingCheck.check("onRemoteTaskRequested should be called", WAIT_TIMEOUT_MS,
                () -> mRemoteAccessCallback.getTaskId() != null);
        // Must not start the service since the service is waiting for user unlock.
        verify(mContext, never()).bindServiceAsUser(any(), any(), anyInt(), any());

        // Simulate user_unlock intent arrives.
        for (int i = 0; i < VALID_PACKAGE_COUNT; i++) {
            UserLifecycleListener listener = mUserLifecycleListenerCaptor.getAllValues().get(i);
            listener.onEvent(new UserLifecycleEvent(USER_LIFECYCLE_EVENT_TYPE_UNLOCKED,
                    UserHandle.USER_SYSTEM));
        }

        verify(mContext, times(VALID_PACKAGE_COUNT)).bindServiceAsUser(
                any(), any(), anyInt(), any());
    }

    @Test
    public void testStartRemoteTaskClientServiceUserLocked_unbindWhileWaiting() throws Exception {
        when(mUserManager.isUserUnlocked(any())).thenReturn(false);

        runBootComplete();
        mService.init();

        verify(mUserManager, times(VALID_PACKAGE_COUNT)).isUserUnlocked(eq(UserHandle.SYSTEM));
        verify(mCarUserService, times(VALID_PACKAGE_COUNT)).addUserLifecycleListener(any(), any());
        verify(mContext, never()).bindServiceAsUser(any(), any(), anyInt(), any());

        // Unbinding services should cancel the wait for user unlock.
        mService.unbindAllServices();

        verify(mCarUserService, times(VALID_PACKAGE_COUNT)).removeUserLifecycleListener(any());
        verify(mContext, never()).unbindService(any());

        // Simulate a task arrives for PACKAGE_ONE which will try to start it.
        // Should start waiting for user unlock again.
        RemoteAccessHalCallback halCallback = prepareCarRemoteTaskClient();
        String clientId = mRemoteAccessCallback.getClientId();
        byte[] data = new byte[]{1, 2, 3, 4};
        halCallback.onRemoteTaskRequested(clientId, data);

        // Should register a new receiver to wait for user unlock again.
        verify(mCarUserService, times(VALID_PACKAGE_COUNT + 1))
                .addUserLifecycleListener(any(), any());
    }

    @Test
    public void testCarRemoteAccessServiceInit() throws Exception {
        mService.init();

        verify(mRemoteAccessHalWrapper, timeout(1000)).notifyApStateChange(
                /* isReadyForRemoteTask= */ true, /* isWakeupRequired= */ false);
    }

    @Test
    public void testCarRemoteAccessServiceInit_retryNotifyApState() throws Exception {
        when(mRemoteAccessHalWrapper.notifyApStateChange(anyBoolean(), anyBoolean()))
                .thenReturn(false).thenReturn(false).thenReturn(true);

        mService.init();

        // This should take about 300ms, so waiting 5s is definitely enough.
        verify(mRemoteAccessHalWrapper, timeout(WAIT_TIMEOUT_MS).times(3)).notifyApStateChange(
                anyBoolean(), anyBoolean());
    }

    @Test
    public void testCarRemoteAccessServiceInit_maxRetryNotifyApState() throws Exception {
        when(mRemoteAccessHalWrapper.notifyApStateChange(anyBoolean(), anyBoolean()))
                .thenReturn(false);

        mService.init();

        verify(mRemoteAccessHalWrapper, timeout(WAIT_TIMEOUT_MS).times(10)).notifyApStateChange(
                /* isReadyForRemoteTask= */ true, /* isWakeupRequired= */ false);

        SystemClock.sleep(100);

        // Verify no more retry.
        verify(mRemoteAccessHalWrapper, times(10)).notifyApStateChange(
                /* isReadyForRemoteTask= */ true, /* isWakeupRequired= */ false);
    }

    @Test
    public void testCarRemoteAccessServiceInit_resetRetryCountAfterSuccess() throws Exception {
        when(mRemoteAccessHalWrapper.notifyApStateChange(anyBoolean(), anyBoolean()))
                .thenReturn(false).thenReturn(false).thenReturn(true);

        mService.init();

        verify(mRemoteAccessHalWrapper, timeout(WAIT_TIMEOUT_MS).times(3)).notifyApStateChange(
                /* isReadyForRemoteTask= */ true, /* isWakeupRequired= */ false);

        ICarPowerStateListener powerStateListener = getCarPowerStateListener();
        // Success notifying should not be limited retry count and should reset retry count to 0.
        for (int i = 0; i < 10; i++) {
            powerStateListener.onStateChanged(CarPowerManager.STATE_WAIT_FOR_VHAL, 0);
        }

        // notifyApStateChange is also called when initializaing CarRemoteAccessService.
        verify(mRemoteAccessHalWrapper, times(13)).notifyApStateChange(
                /* isReadyForRemoteTask= */ true, /* isWakeupRequired= */ false);
    }

    @Test
    public void testAddCarRemoteTaskClient() throws Exception {
        when(mDep.getCallingUid()).thenReturn(UID_PERMISSION_GRANTED_PACKAGE_ONE);
        mService.init();

        mService.addCarRemoteTaskClient(mRemoteAccessCallback);

        PollingCheck.check("onClientRegistrationUpdated should be called", WAIT_TIMEOUT_MS,
                () -> Objects.equals(mRemoteAccessCallback.getServiceName(), WAKEUP_SERVICE_NAME)
                        && Objects.equals(mRemoteAccessCallback.getVehicleId(), TEST_VEHICLE_ID)
                        && Objects.equals(mRemoteAccessCallback.getProcessorId(), TEST_PROCESSOR_ID)
                        && mRemoteAccessCallback.getClientId() != null);
        assertWithMessage("Non serverless client ID must be persisted in db").that(
                mRemoteAccessStorage.getClientIdEntry(PERMISSION_GRANTED_PACKAGE_ONE)).isNotNull();
    }

    @Test
    public void testAddCarRemoteTaskClient_addTwice() throws Exception {
        when(mDep.getCallingUid()).thenReturn(UID_PERMISSION_GRANTED_PACKAGE_ONE);
        ICarRemoteAccessCallbackImpl secondCallback = new ICarRemoteAccessCallbackImpl();
        mService.init();
        mService.addCarRemoteTaskClient(mRemoteAccessCallback);

        mService.addCarRemoteTaskClient(secondCallback);

        PollingCheck.check("onClientRegistrationUpdated should be called", WAIT_TIMEOUT_MS,
                () -> Objects.equals(secondCallback.getServiceName(), WAKEUP_SERVICE_NAME)
                        && Objects.equals(secondCallback.getVehicleId(), TEST_VEHICLE_ID)
                        && Objects.equals(secondCallback.getProcessorId(), TEST_PROCESSOR_ID)
                        && secondCallback.getClientId() != null
                        && secondCallback.getClientId().equals(
                                mRemoteAccessCallback.getClientId()));
    }

    @Test
    public void testAddCarRemoteTaskClient_addMultipleClients() throws Exception {
        when(mDep.getCallingUid()).thenReturn(UID_PERMISSION_GRANTED_PACKAGE_ONE)
                .thenReturn(UID_PERMISSION_GRANTED_PACKAGE_TWO);
        ICarRemoteAccessCallbackImpl secondCallback = new ICarRemoteAccessCallbackImpl();
        mService.init();

        mService.addCarRemoteTaskClient(mRemoteAccessCallback);
        mService.addCarRemoteTaskClient(secondCallback);

        PollingCheck.check("Two clients should have different client IDs", WAIT_TIMEOUT_MS,
                () -> {
                    String clientIdOne = mRemoteAccessCallback.getClientId();
                    String clientIdTwo = secondCallback.getClientId();
                    return clientIdOne != null && !clientIdOne.equals(clientIdTwo);
                });
    }

    @Test
    public void testAddCarRemoteTaskClient_persistentClientId() throws Exception {
        String packageName = PERSISTENT_CLIENTS.get(0).uidName;
        String expectedClientId = PERSISTENT_CLIENTS.get(0).clientId;
        when(mDep.getCallingUid()).thenReturn(1234);
        when(mPackageManager.getNameForUid(1234)).thenReturn(packageName);
        when(mPackageManager.getPackagesForUid(1234)).thenReturn(new String[]{packageName});
        setupDatabase();
        mService.init();

        mService.addCarRemoteTaskClient(mRemoteAccessCallback);

        PollingCheck.check("onClientRegistrationUpdated should be called", WAIT_TIMEOUT_MS,
                () -> Objects.equals(mRemoteAccessCallback.getServiceName(), WAKEUP_SERVICE_NAME)
                        && Objects.equals(mRemoteAccessCallback.getVehicleId(), TEST_VEHICLE_ID)
                        && Objects.equals(mRemoteAccessCallback.getProcessorId(), TEST_PROCESSOR_ID)
                        && Objects.equals(mRemoteAccessCallback.getClientId(), expectedClientId));
    }

    @Test
    public void testAddCarRemoteTaskClient_serverlessClient() throws Exception {
        when(mDep.getCallingUid()).thenReturn(UID_SERVERLESS_PACKAGE);
        XmlResourceParser fakeXmlResourceParser = getFakeXmlResourceParser(
                SERVERLESS_CLIENT_MAP_XML);
        when(mResources.getXml(R.xml.remote_access_serverless_client_map)).thenReturn(
                fakeXmlResourceParser);

        mService.init();
        runBootComplete();

        mService.addCarRemoteTaskClient(mRemoteAccessCallback);

        PollingCheck.check("onServerlessClientRegistered should be called", WAIT_TIMEOUT_MS,
                () -> mRemoteAccessCallback.isServerlessClientRegistered());
        expectWithMessage("onClientRegistrationUpdated must not be called").that(
                mRemoteAccessCallback.getClientId()).isNull();
        expectWithMessage("Serverless remote task client ID must not be persisted in db").that(
                mRemoteAccessStorage.getClientIdEntry(SERVERLESS_PACKAGE)).isNull();
    }

    @Test
    public void testAddCarRemoteTaskClient_asignedDynamicClientId_thenBecomeServerlessClient()
            throws Exception {
        when(mDep.getCallingUid()).thenReturn(UID_SERVERLESS_PACKAGE);
        String dynamicClientId = "dynamic client id";
        // Store a dynamic client ID in the persistent storage for UID_NAME_SERVERLESS_PACKAGE.
        // This ID was generated when the package was not a serverless client.
        mRemoteAccessStorage.updateClientId(new ClientIdEntry(
                dynamicClientId, System.currentTimeMillis(), UID_NAME_SERVERLESS_PACKAGE));
        XmlResourceParser fakeXmlResourceParser = getFakeXmlResourceParser(
                SERVERLESS_CLIENT_MAP_XML);
        when(mResources.getXml(R.xml.remote_access_serverless_client_map)).thenReturn(
                fakeXmlResourceParser);

        mService.init();
        runBootComplete();

        mService.addCarRemoteTaskClient(mRemoteAccessCallback);

        PollingCheck.check("onServerlessClientRegistered should be called", WAIT_TIMEOUT_MS,
                () -> mRemoteAccessCallback.isServerlessClientRegistered());
        expectWithMessage("onClientRegistrationUpdated must not be called").that(
                mRemoteAccessCallback.getClientId()).isNull();
        expectWithMessage("Serverless remote task client ID must not be persisted in db").that(
                mRemoteAccessStorage.getClientIdEntry(SERVERLESS_PACKAGE)).isNull();

        RemoteAccessHalCallback halCallback = mService.getRemoteAccessHalCallback();
        // Starts an active task.
        halCallback.onRemoteTaskRequested(TEST_SERVERLESS_CLIENT_ID, new byte[]{1, 2, 3, 4});

        PollingCheck.check("onRemoteTaskRequested should be called", WAIT_TIMEOUT_MS,
                () -> mRemoteAccessCallback.getTaskId() != null);
    }

    @Test
    public void testRemoveCarRemoteTaskClient() throws Exception {
        when(mDep.getCallingUid()).thenReturn(UID_PERMISSION_GRANTED_PACKAGE_ONE);
        mService.init();
        mService.addCarRemoteTaskClient(mRemoteAccessCallback);

        mService.removeCarRemoteTaskClient(mRemoteAccessCallback);
    }

    @Test
    public void testRemoveCarRemoteTaskClient_removeNotAddedClient() throws Exception {
        mService.init();
        // Removing unregistered ICarRemoteAccessCallback is no-op.
        mService.removeCarRemoteTaskClient(mRemoteAccessCallback);
    }

    @Test
    public void testRemoveCarRemoteTaskClient_removeActiveTasks() throws Exception {
        // Only use one package.
        mockPackageInfo(1);
        mService.init();
        mService.setTaskUnbindDelayMs(100);
        runBootComplete();
        RemoteAccessHalCallback halCallback = prepareCarRemoteTaskClient();

        String clientId = mRemoteAccessCallback.getClientId();
        byte[] data = new byte[]{1, 2, 3, 4};
        // Starts an active task.
        halCallback.onRemoteTaskRequested(clientId, data);

        PollingCheck.check("onRemoteTaskRequested should be called", WAIT_TIMEOUT_MS,
                () -> mRemoteAccessCallback.getTaskId() != null);
        String taskId = mRemoteAccessCallback.getTaskId();

        // This should clear the active tasks, after 100ms, the client should be unbound and the
        // device should be shutdown.
        mService.removeCarRemoteTaskClient(mRemoteAccessCallback);

        // This should throw exception because clientId is not valid.
        assertThrows(IllegalArgumentException.class, () -> mService.reportRemoteTaskDone(
                clientId, taskId));

        mService.addCarRemoteTaskClient(mRemoteAccessCallback);

        // This should throw exception because the task was cleared so the task ID is not valid.
        assertThrows(IllegalArgumentException.class, () -> mService.reportRemoteTaskDone(
                clientId, taskId));

        // The client must be unbound.
        verify(mContext, timeout(WAIT_TIMEOUT_MS)).unbindService(any());
        // The device must be shutdown.
        verify(mCarPowerManagementService, timeout(WAIT_TIMEOUT_MS)).requestShutdownAp(
                anyInt(), anyBoolean());
    }

    @Test
    public void testRemoteTaskRequested() throws Exception {
        mService.init();
        runBootComplete();
        RemoteAccessHalCallback halCallback = prepareCarRemoteTaskClient();

        String clientId = mRemoteAccessCallback.getClientId();
        byte[] data = new byte[]{1, 2, 3, 4};
        halCallback.onRemoteTaskRequested(clientId, data);

        PollingCheck.check("onRemoteTaskRequested should be called", WAIT_TIMEOUT_MS,
                () -> mRemoteAccessCallback.getTaskId() != null);
        assertWithMessage("Data").that(mRemoteAccessCallback.getData()).asList()
                .containsExactlyElementsIn(new Byte[]{1, 2, 3, 4});
    }

    @Test
    public void testRemoteTaskRequested_removedClient() throws Exception {
        mService.init();
        runBootComplete();
        RemoteAccessHalCallback halCallback = prepareCarRemoteTaskClient();
        String clientId = mRemoteAccessCallback.getClientId();
        mService.removeCarRemoteTaskClient(mRemoteAccessCallback);

        halCallback.onRemoteTaskRequested(clientId, /* data= */ null);

        assertWithMessage("Task ID").that(mRemoteAccessCallback.getTaskId()).isNull();
    }

    @Test
    public void testRemoteTaskRequested_clientRegisteredAfterRequest() throws Exception {
        mService.init();
        runBootComplete();
        RemoteAccessHalCallback halCallback = prepareCarRemoteTaskClient();
        String clientId = mRemoteAccessCallback.getClientId();
        mService.removeCarRemoteTaskClient(mRemoteAccessCallback);
        halCallback.onRemoteTaskRequested(clientId, /* data= */ null);

        mService.addCarRemoteTaskClient(mRemoteAccessCallback);

        PollingCheck.check("onRemoteTaskRequested should be called", WAIT_TIMEOUT_MS,
                () -> mRemoteAccessCallback.getTaskId() != null);
    }

    @Test
    public void testRemoteTaskRequested_persistentClientRegisteredAfterRequest() throws Exception {
        String packageName = PERSISTENT_CLIENTS.get(0).uidName;
        String clientId = PERSISTENT_CLIENTS.get(0).clientId;
        when(mDep.getCallingUid()).thenReturn(1234);
        when(mPackageManager.getNameForUid(1234)).thenReturn(packageName);
        when(mPackageManager.getPackagesForUid(1234)).thenReturn(new String[]{packageName});
        RemoteAccessHalCallback halCallback = mService.getRemoteAccessHalCallback();
        setupDatabase();
        mService.init();
        runBootComplete();

        halCallback.onRemoteTaskRequested(clientId, /* data= */ null);
        SystemClock.sleep(500);
        mService.addCarRemoteTaskClient(mRemoteAccessCallback);

        PollingCheck.check("onRemoteTaskRequested should be called", WAIT_TIMEOUT_MS,
                () -> mRemoteAccessCallback.getTaskId() != null);
    }

    @Test
    public void testRemoteTaskRequested_serverlessClientRegisteredAfterRequest() throws Exception {
        when(mDep.getCallingUid()).thenReturn(UID_SERVERLESS_PACKAGE);
        XmlResourceParser fakeXmlResourceParser = getFakeXmlResourceParser(
                SERVERLESS_CLIENT_MAP_XML);
        when(mResources.getXml(R.xml.remote_access_serverless_client_map)).thenReturn(
                fakeXmlResourceParser);
        RemoteAccessHalCallback halCallback = mService.getRemoteAccessHalCallback();
        mService.init();
        runBootComplete();

        halCallback.onRemoteTaskRequested(TEST_SERVERLESS_CLIENT_ID, /* data= */ null);
        SystemClock.sleep(500);
        mService.addCarRemoteTaskClient(mRemoteAccessCallback);

        PollingCheck.check("onRemoteTaskRequested should be called", WAIT_TIMEOUT_MS,
                () -> mRemoteAccessCallback.getTaskId() != null);
    }

    @Test
    public void testRemoteTaskRequested_withTwoClientsRegistered() throws Exception {
        when(mDep.getCallingUid()).thenReturn(UID_PERMISSION_GRANTED_PACKAGE_ONE)
                .thenReturn(UID_PERMISSION_GRANTED_PACKAGE_TWO);
        ICarRemoteAccessCallbackImpl secondCallback = new ICarRemoteAccessCallbackImpl();
        mService.init();
        runBootComplete();
        mService.addCarRemoteTaskClient(mRemoteAccessCallback);
        mService.addCarRemoteTaskClient(secondCallback);
        PollingCheck.check("Client is registered", WAIT_TIMEOUT_MS,
                () -> mRemoteAccessCallback.getClientId() != null);
        PollingCheck.check("Client is registered", WAIT_TIMEOUT_MS,
                () -> secondCallback.getClientId() != null);
        String clientId = mRemoteAccessCallback.getClientId();
        mService.removeCarRemoteTaskClient(secondCallback);
        RemoteAccessHalCallback halCallback = mService.getRemoteAccessHalCallback();

        halCallback.onRemoteTaskRequested(clientId, /* data= */ null);

        PollingCheck.check("onRemoteTaskRequested should be called", WAIT_TIMEOUT_MS,
                () -> mRemoteAccessCallback.getTaskId() != null);
        assertWithMessage("Task ID").that(secondCallback.getTaskId()).isNull();
    }

    @Test
    public void testReportTaskDone_withPowerStateOff() throws Exception {
        mService.init();
        prepareReportTaskDoneTest();
        String clientId = mRemoteAccessCallback.getClientId();
        String taskId = mRemoteAccessCallback.getTaskId();
        mService.setPowerStatePostTaskExecution(
                CarRemoteAccessManager.NEXT_POWER_STATE_OFF, /* runGarageMode= */ true);

        mService.reportRemoteTaskDone(clientId, taskId);

        // Need to wait TASK_UNBIND_DELAY_MS before shutdown happens.
        verify(mCarPowerManagementService, never()).requestShutdownAp(anyInt(), anyBoolean());
        verify(mCarPowerManagementService, timeout(WAIT_TIMEOUT_MS)).requestShutdownAp(
                CarRemoteAccessManager.NEXT_POWER_STATE_OFF, /* runGarageMode= */ true);
    }

    @Test
    public void testReportRemoteTaskDone_withPowerStateS2R() throws Exception {
        mService.init();
        prepareReportTaskDoneTest();
        String clientId = mRemoteAccessCallback.getClientId();
        String taskId = mRemoteAccessCallback.getTaskId();
        mService.setPowerStatePostTaskExecution(
                CarRemoteAccessManager.NEXT_POWER_STATE_SUSPEND_TO_RAM, /* runGarageMode= */ true);

        mService.reportRemoteTaskDone(clientId, taskId);

        verify(mCarPowerManagementService, timeout(WAIT_TIMEOUT_MS)).requestShutdownAp(
                CarRemoteAccessManager.NEXT_POWER_STATE_SUSPEND_TO_RAM, /* runGarageMode= */ true);
    }

    @Test
    public void testReportRemoteTaskDone_withPowerStateOn() throws Exception {
        mService.init();
        prepareReportTaskDoneTest();
        String clientId = mRemoteAccessCallback.getClientId();
        String taskId = mRemoteAccessCallback.getTaskId();
        mService.setPowerStatePostTaskExecution(
                CarRemoteAccessManager.NEXT_POWER_STATE_ON, /* runGarageMode= */ false);

        mService.reportRemoteTaskDone(clientId, taskId);
        SystemClock.sleep(TASK_UNBIND_DELAY_MS);

        verify(mCarPowerManagementService, never()).requestShutdownAp(
                CarRemoteAccessManager.NEXT_POWER_STATE_SUSPEND_TO_RAM, /* runGarageMode= */ true);
    }

    @Test
    public void testReportRemoteTaskDone_vehicleInUse() throws Exception {
        setVehicleInUse(/* inUse= */ true);
        mService.init();
        prepareReportTaskDoneTest();
        String clientId = mRemoteAccessCallback.getClientId();
        String taskId = mRemoteAccessCallback.getTaskId();
        mService.setPowerStatePostTaskExecution(
                CarRemoteAccessManager.NEXT_POWER_STATE_OFF, /* runGarageMode= */ false);

        mService.reportRemoteTaskDone(clientId, taskId);
        SystemClock.sleep(TASK_UNBIND_DELAY_MS);

        verify(mCarPowerManagementService, never()).requestShutdownAp(
                CarRemoteAccessManager.NEXT_POWER_STATE_SUSPEND_TO_RAM, /* runGarageMode= */ true);
    }

    @Test
    public void testReportTaskDone_wrongTaskId() throws Exception {
        mService.init();
        prepareReportTaskDoneTest();
        String clientId = mRemoteAccessCallback.getClientId();
        String wrongTaskId = mRemoteAccessCallback.getTaskId() + "_WRONG";

        assertThrows(IllegalArgumentException.class,
                () -> mService.reportRemoteTaskDone(clientId, wrongTaskId));
    }

    @Test
    public void testReportTaskDone_wrongClientId() throws Exception {
        mService.init();
        prepareReportTaskDoneTest();
        String wrongClientId = mRemoteAccessCallback.getClientId() + "_WRONG";
        String taskId = mRemoteAccessCallback.getTaskId();

        assertThrows(IllegalArgumentException.class,
                () -> mService.reportRemoteTaskDone(wrongClientId, taskId));
    }

    @Test
    public void testUnbindServiceAfterTaskComplete() throws Exception {
        mService.init();
        mService.setTaskUnbindDelayMs(100);
        setVehicleInUse(/* inUse= */ true);
        ICarRemoteAccessCallbackImpl callback1 = new ICarRemoteAccessCallbackImpl();
        ICarRemoteAccessCallbackImpl callback2 = new ICarRemoteAccessCallbackImpl();
        runBootComplete();
        prepareReportTaskDoneTest(callback1, UID_PERMISSION_GRANTED_PACKAGE_ONE);
        String clientId1 = callback1.getClientId();
        String taskId1 = callback1.getTaskId();
        prepareReportTaskDoneTest(callback2, UID_PERMISSION_GRANTED_PACKAGE_TWO);
        String clientId2 = callback2.getClientId();
        String taskId2 = callback2.getTaskId();
        mService.setPowerStatePostTaskExecution(
                CarRemoteAccessManager.NEXT_POWER_STATE_OFF, /* runGarageMode= */ true);

        when(mDep.getCallingUid()).thenReturn(UID_PERMISSION_GRANTED_PACKAGE_ONE);
        mService.reportRemoteTaskDone(clientId1, taskId1);

        // package one should be unbound after 100ms.
        verify(mContext, timeout(WAIT_TIMEOUT_MS)).unbindService(any());
        verify(mCarPowerManagementService, never()).requestShutdownAp(anyInt(), anyBoolean());

        when(mDep.getCallingUid()).thenReturn(UID_PERMISSION_GRANTED_PACKAGE_TWO);
        mService.reportRemoteTaskDone(clientId2, taskId2);

        // package two should be unbound since no active tasks.
        verify(mContext, timeout(WAIT_TIMEOUT_MS).times(2)).unbindService(any());
    }

    @Test
    public void testUnbindServiceAfterTimeout() throws Exception {
        // Only use one package.
        mockPackageInfo(1);
        mService = newServiceWithSystemUpTime(2000L);
        // If no task arrive, the service will be unbound after 1000ms.
        mService.setAllowedTimeForRemoteTaskClientInitMs(1000);
        mService.init();
        setVehicleInUse(/* inUse= */ true);
        runBootComplete();
        RemoteAccessHalCallback halCallback = prepareCarRemoteTaskClient();
        String clientId = mRemoteAccessCallback.getClientId();

        // This task will timeout at 2000.
        halCallback.onRemoteTaskRequested(clientId, /* data= */ null);

        SystemClock.sleep(1000);

        // This task will timeout at 3000.
        halCallback.onRemoteTaskRequested(clientId, /* data= */ null);

        SystemClock.sleep(1000);

        // This is time 2000, the first task timed out but the second task is still active.
        verify(mContext, never()).unbindService(any());

        // Techcnially the service should timeout at time 3000, we leave a 1000ms buffer.
        verify(mContext, timeout(2000)).unbindService(any());
    }

    @Test
    public void testNotifyApPowerState_waitForVhal() throws Exception {
        mService.init();
        mService.setTaskUnbindDelayMs(100);
        ICarPowerStateListener powerStateListener = getCarPowerStateListener();
        verify(mRemoteAccessHalWrapper, timeout(1000)).notifyApStateChange(
                anyBoolean(), anyBoolean());

        powerStateListener.onStateChanged(CarPowerManager.STATE_WAIT_FOR_VHAL, 0);
        // notifyApStateChange is also called when initializaing CarRemoteAccessService.
        verify(mRemoteAccessHalWrapper, times(2)).notifyApStateChange(
                /* isReadyForRemoteTask= */ true, /* isWakeupRequired= */ false);
        verify(mCarPowerManagementService, never())
                .finished(eq(CarPowerManager.STATE_WAIT_FOR_VHAL), any());
    }

    @Test
    public void testNotifyApPowerState_shutdownPrepare() throws Exception {
        mService.init();
        ICarPowerStateListener powerStateListener = getCarPowerStateListener();
        verify(mRemoteAccessHalWrapper, timeout(1000)).notifyApStateChange(
                anyBoolean(), anyBoolean());

        powerStateListener.onStateChanged(CarPowerManager.STATE_SHUTDOWN_PREPARE, 0);
        verify(mRemoteAccessHalWrapper).notifyApStateChange(/* isReadyForRemoteTask= */ false,
                /* isWakeupRequired= */ false);
        verify(mCarPowerManagementService).finished(eq(CarPowerManager.STATE_SHUTDOWN_PREPARE),
                any());
    }

    @Test
    public void testUnbindAllServiceOnShutdownPrepare() throws Exception {
        mService.init();
        ICarPowerStateListener powerStateListener = getCarPowerStateListener();
        verify(mRemoteAccessHalWrapper, timeout(1000)).notifyApStateChange(
                anyBoolean(), anyBoolean());
        mBootComplete.run();

        powerStateListener.onStateChanged(CarPowerManager.STATE_SHUTDOWN_PREPARE, 0);

        verify(mRemoteAccessHalWrapper).notifyApStateChange(/* isReadyForRemoteTask= */ false,
                /* isWakeupRequired= */ false);
        verify(mCarPowerManagementService).finished(eq(CarPowerManager.STATE_SHUTDOWN_PREPARE),
                any());
        verify(mContext, times(VALID_PACKAGE_COUNT)).unbindService(any());

        // Unbind service multiple times must do nothing.
        powerStateListener.onStateChanged(CarPowerManager.STATE_SHUTDOWN_PREPARE, 0);

        verify(mContext, times(VALID_PACKAGE_COUNT)).unbindService(any());
    }

    @Test
    public void testNotifyApPowerState_postShutdownEnter() throws Exception {
        mService.init();
        ICarPowerStateListener powerStateListener = getCarPowerStateListener();
        verify(mRemoteAccessHalWrapper, timeout(1000)).notifyApStateChange(
                anyBoolean(), anyBoolean());

        powerStateListener.onStateChanged(CarPowerManager.STATE_POST_SHUTDOWN_ENTER, 0);
        verify(mRemoteAccessHalWrapper).notifyApStateChange(/* isReadyForRemoteTask= */ false,
                /* isWakeupRequired= */ true);
        verify(mCarPowerManagementService).finished(eq(CarPowerManager.STATE_POST_SHUTDOWN_ENTER),
                any());
    }

    @Test
    public void testWrappingUpCarRemoteAccessServiceAfterAllowedTime() throws Exception {
        // Use a shorter time for testing.
        mService = newServiceWithSystemUpTime(100L);

        mService.init();
        mService.setPowerStatePostTaskExecution(CarRemoteAccessManager.NEXT_POWER_STATE_OFF,
                /* runGarageMode= */ false);

        verify(mCarPowerManagementService, timeout(WAIT_TIMEOUT_MS))
                .requestShutdownAp(CarRemoteAccessManager.NEXT_POWER_STATE_OFF,
                        /* runGarageMode= */ false);
    }

    @Test
    public void testWrappingUpCarRemoteAccessServiceAfterAllowedTime_vehicleInUse()
            throws Exception {
        // Use a shorter time for testing.
        mService = newServiceWithSystemUpTime(100L);
        setVehicleInUse(/* inUse= */ true);
        mService.init();
        mService.setPowerStatePostTaskExecution(CarRemoteAccessManager.NEXT_POWER_STATE_OFF,
                /* runGarageMode= */ false);
        SystemClock.sleep(100);

        verify(mCarPowerManagementService, never())
                .requestShutdownAp(CarRemoteAccessManager.NEXT_POWER_STATE_OFF,
                        /* runGarageMode= */ false);
    }

    @Test
    public void testTaskArriveAfterAllowedTime() throws Exception {
        // Require at least 1 seconds for task to be executed. Otherwise, taskExecutionTimeInSec
        // will become 0.
        mService = newServiceWithSystemUpTime(1000L);
        mService.init();
        mService.setPowerStatePostTaskExecution(CarRemoteAccessManager.NEXT_POWER_STATE_OFF,
                /* runGarageMode= */ false);

        RemoteAccessHalCallback halCallback = prepareCarRemoteTaskClient();
        String clientId = mRemoteAccessCallback.getClientId();
        byte[] data = new byte[]{1, 2, 3, 4};
        SystemClock.sleep(1000);

        halCallback.onRemoteTaskRequested(clientId, data);

        SystemClock.sleep(1000);

        assertWithMessage("Must not dispatch remote task after shutdown is supposed to start")
                .that(mRemoteAccessCallback.getTaskId()).isNull();
    }

    // Allowed system up time must not take effect if vehicle is currently in use.
    @Test
    public void testTaskArriveAfterAllowedTime_vehicleInUse() throws Exception {
        // Require at least 1 seconds for task to be executed. Otherwise, taskExecutionTimeInSec
        // will become 0.
        mService = newServiceWithSystemUpTime(1000L);
        // Boot complete to trigger package search.
        runBootComplete();
        mService.init();
        mService.setPowerStatePostTaskExecution(CarRemoteAccessManager.NEXT_POWER_STATE_OFF,
                /* runGarageMode= */ false);
        setVehicleInUse(true);

        RemoteAccessHalCallback halCallback = prepareCarRemoteTaskClient();
        String clientId = mRemoteAccessCallback.getClientId();
        byte[] data = new byte[]{1, 2, 3, 4};
        SystemClock.sleep(1000);

        halCallback.onRemoteTaskRequested(clientId, data);

        PollingCheck.check("Remote task received", WAIT_TIMEOUT_MS,
                () -> mRemoteAccessCallback.getTaskId() != null);
        // We will round up 100ms to 1 sec.
        assertThat(mRemoteAccessCallback.getTaskMaxDurationInSec()).isEqualTo(1);
    }

    // Allowed system up time must not take effect if next power state is on.
    @Test
    public void testTaskArriveAfterAllowedTime_nextPowerStateOn() throws Exception {
        // Require at least 1 seconds for task to be executed. Otherwise, taskExecutionTimeInSec
        // will become 0.
        mService = newServiceWithSystemUpTime(1000L);
        // Boot complete to trigger package search.
        runBootComplete();
        mService.init();
        mService.setPowerStatePostTaskExecution(CarRemoteAccessManager.NEXT_POWER_STATE_ON,
                /* runGarageMode= */ false);

        RemoteAccessHalCallback halCallback = prepareCarRemoteTaskClient();
        String clientId = mRemoteAccessCallback.getClientId();
        byte[] data = new byte[]{1, 2, 3, 4};
        SystemClock.sleep(1000);

        halCallback.onRemoteTaskRequested(clientId, data);

        PollingCheck.check("Remote task received", WAIT_TIMEOUT_MS,
                () -> mRemoteAccessCallback.getTaskId() != null);
    }

    @Test
    public void testAllowedSystemUptimeMs() {
        when(mResources.getInteger(R.integer.config_allowedSystemUptimeForRemoteAccess))
                .thenReturn(300);

        mService = new CarRemoteAccessService(mContext, mSystemInterface, mPowerHalService);

        assertThat(mService.getAllowedSystemUptimeMs()).isEqualTo(300_000L);
    }

    @Test
    public void testAllowedSystemUptimeMs_lessThanMinSystemUptime() {
        // MIN_SYSTEM_UPTIME_FOR_REMOTE_ACCESS_IN_SEC is 30s.
        when(mResources.getInteger(R.integer.config_allowedSystemUptimeForRemoteAccess))
                .thenReturn(10);

        mService = new CarRemoteAccessService(mContext, mSystemInterface, mPowerHalService);

        assertThat(mService.getAllowedSystemUptimeMs()).isEqualTo(30_000L);
    }

    @Test
    public void testCallbackOnRemoteTaskRequestedException_activieTasksCleared() throws Exception {
        runBootComplete();
        when(mDep.getCallingUid()).thenReturn(UID_PERMISSION_GRANTED_PACKAGE_ONE);
        RemoteAccessHalCallback halCallback = mService.getRemoteAccessHalCallback();
        ICarRemoteAccessCallback clientCallback = mock(ICarRemoteAccessCallback.class);
        IBinder mockBinder = mock(IBinder.class);
        when(clientCallback.asBinder()).thenReturn(mockBinder);
        doThrow(new RemoteException()).when(clientCallback).onRemoteTaskRequested(any(), any(),
                any(), anyInt());
        mService.addCarRemoteTaskClient(clientCallback);

        verify(clientCallback, timeout(WAIT_TIMEOUT_MS)).onClientRegistrationUpdated(
                mRegistrationInfoCaptor.capture());

        String clientId = mRegistrationInfoCaptor.getValue().getClientId();

        halCallback.onRemoteTaskRequested(clientId, /* data= */ null);

        verify(clientCallback).onRemoteTaskRequested(eq(clientId), any(), eq(null), anyInt());
        assertThat(mService.getActiveTaskCount()).isEqualTo(0);
    }

    @Test
    public void testNotifyShutdownStarting() throws Exception {
        // Should be notified shutdown at 5100 - 5000 = 100ms.
        mService = newServiceWithSystemUpTime(5100);
        runBootComplete();
        mService.init();
        prepareCarRemoteTaskClient();

        PollingCheck.check("shutdownStarted is notified", 5000,
                () -> mRemoteAccessCallback.isShutdownStarting());
    }

    @Test
    public void testNotifyShutdownStarting_noNotifyVehicleInUse() throws Exception {
        setVehicleInUse(true);
        // Should be notified shutdown at 5100 - 5000 = 100ms.
        mService = newServiceWithSystemUpTime(5100);
        runBootComplete();
        mService.init();
        prepareCarRemoteTaskClient();

        SystemClock.sleep(1000);
        assertWithMessage("client is not notifyed shutdown when vehicle is in use").that(
                mRemoteAccessCallback.isShutdownStarting()).isFalse();
    }

    @Test
    public void testNotifyShutdownStarting_noNotifyNextPowerStateOn() throws Exception {
        // Should be notified shutdown at 5100 - 5000 = 100ms.
        mService = newServiceWithSystemUpTime(5100);
        when(mCarPowerManagementService.getLastShutdownState())
                .thenReturn(CarRemoteAccessManager.NEXT_POWER_STATE_ON);
        runBootComplete();
        mService.init();
        prepareCarRemoteTaskClient();

        SystemClock.sleep(1000);
        assertWithMessage("client is not notifyed shutdown when next power state is on").that(
                mRemoteAccessCallback.isShutdownStarting()).isFalse();
    }

    @Test
    public void testReportReadyForShutdown() throws Exception {
        mService = newServiceWithSystemUpTime(10000);
        runBootComplete();
        mService.init();
        ICarRemoteAccessCallback clientCallback1 = mock(ICarRemoteAccessCallback.class);
        IBinder mockBinder1 = mock(IBinder.class);
        when(clientCallback1.asBinder()).thenReturn(mockBinder1);
        when(mDep.getCallingUid()).thenReturn(UID_PERMISSION_GRANTED_PACKAGE_ONE);
        mService.addCarRemoteTaskClient(clientCallback1);
        verify(clientCallback1, timeout(WAIT_TIMEOUT_MS)).onClientRegistrationUpdated(
                mRegistrationInfoCaptor.capture());
        String clientId1 = mRegistrationInfoCaptor.getValue().getClientId();

        ICarRemoteAccessCallback clientCallback2 = mock(ICarRemoteAccessCallback.class);
        IBinder mockBinder2 = mock(IBinder.class);
        when(clientCallback2.asBinder()).thenReturn(mockBinder2);
        when(mDep.getCallingUid()).thenReturn(UID_PERMISSION_GRANTED_PACKAGE_TWO);
        mService.addCarRemoteTaskClient(clientCallback2);
        verify(clientCallback2, timeout(WAIT_TIMEOUT_MS)).onClientRegistrationUpdated(
                mRegistrationInfoCaptor.capture());
        String clientId2 = mRegistrationInfoCaptor.getValue().getClientId();

        when(mDep.getCallingUid()).thenReturn(UID_PERMISSION_GRANTED_PACKAGE_ONE);
        mService.confirmReadyForShutdown(clientId1);

        SystemClock.sleep(100);

        // clientCallback2 is still not ready for shutdown.
        verify(mCarPowerManagementService, never()).requestShutdownAp(anyInt(), anyBoolean());

        when(mDep.getCallingUid()).thenReturn(UID_PERMISSION_GRANTED_PACKAGE_TWO);
        // ClientId mismatch must throw exception.
        assertThrows(IllegalArgumentException.class,
                () -> mService.confirmReadyForShutdown(clientId1));

        mService.confirmReadyForShutdown(clientId2);

        verify(mCarPowerManagementService, timeout(WAIT_TIMEOUT_MS)).requestShutdownAp(
                CarRemoteAccessManager.NEXT_POWER_STATE_OFF, /* runGarageMode= */ false);
    }

    @Test
    public void testPendingRequestTimeout() throws Exception {
        mService.setMaxTaskPendingMs(100);
        mService.init();
        runBootComplete();
        RemoteAccessHalCallback halCallback = prepareCarRemoteTaskClient();
        String clientId = mRemoteAccessCallback.getClientId();
        mService.removeCarRemoteTaskClient(mRemoteAccessCallback);

        // The task should be pending because client is not registered yet.
        halCallback.onRemoteTaskRequested(clientId, new byte[]{});

        SystemClock.sleep(100);

        assertThat(mRemoteAccessCallback.getTaskId()).isNull();

        // Register the client after max task pending time.
        mService.addCarRemoteTaskClient(mRemoteAccessCallback);

        SystemClock.sleep(100);

        assertThat(mRemoteAccessCallback.getTaskId()).isNull();
    }

    @Test
    public void testPendingRequestNotTimeout() throws Exception {
        mService.setMaxTaskPendingMs(1000);
        mService.init();
        runBootComplete();
        RemoteAccessHalCallback halCallback = prepareCarRemoteTaskClient();
        String clientId = mRemoteAccessCallback.getClientId();
        mService.removeCarRemoteTaskClient(mRemoteAccessCallback);

        // The task should be pending because client is not registered yet.
        halCallback.onRemoteTaskRequested(clientId, new byte[]{});

        SystemClock.sleep(100);

        assertThat(mRemoteAccessCallback.getTaskId()).isNull();

        // Register the client before max task pending time.
        mService.addCarRemoteTaskClient(mRemoteAccessCallback);

        PollingCheck.check("Task is delivered", WAIT_TIMEOUT_MS,
                () -> mRemoteAccessCallback.getTaskId() != null);
    }

    private XmlResourceParser getFakeXmlResourceParser(String xmlContent) throws Exception {
        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        parser.setInput(new StringReader(xmlContent));
        // XmlResourceParser has additional functions than XmlPullParser so we have to wrap it here.
        XmlResourceParser fakeXmlResourceParser = mock(XmlResourceParser.class);
        when(fakeXmlResourceParser.next()).thenAnswer(i -> parser.next());
        when(fakeXmlResourceParser.nextTag()).thenAnswer(i -> parser.nextTag());
        when(fakeXmlResourceParser.getEventType()).thenAnswer(i -> parser.getEventType());
        when(fakeXmlResourceParser.getName()).thenAnswer(i -> parser.getName());
        when(fakeXmlResourceParser.getText()).thenAnswer(i -> parser.getText());
        doAnswer(i -> {
            parser.require((int) i.getArguments()[0], (String) i.getArguments()[1],
                    (String) i.getArguments()[2]);
            return null;
        }).when(fakeXmlResourceParser).require(anyInt(), any(), any());
        return fakeXmlResourceParser;
    }

    @Test
    public void testParseServerlessClientMap() throws Exception {
        XmlResourceParser fakeXmlResourceParser = getFakeXmlResourceParser(
                SERVERLESS_CLIENT_MAP_XML);
        when(mResources.getXml(R.xml.remote_access_serverless_client_map)).thenReturn(
                fakeXmlResourceParser);

        mService.init();

        ArrayMap<String, String> clientIdsByPackageName =
                mService.getServerlessClientIdsByPackageName();
        assertWithMessage("clientIdsByPackageName").that(clientIdsByPackageName).hasSize(2);
        expectWithMessage("client ID for package 1").that(
                clientIdsByPackageName.get("android.car.app1")).isEqualTo("serverless_client_1234");
        expectWithMessage("client ID for package 2").that(
                clientIdsByPackageName.get("android.car.app2")).isEqualTo("serverless_client_2345");
    }

    @Test
    public void testParseServerlessClientMapWithComment() throws Exception {
        XmlResourceParser fakeXmlResourceParser = getFakeXmlResourceParser(
                SERVERLESS_CLIENT_MAP_WITH_COMMENT_XML);
        when(mResources.getXml(R.xml.remote_access_serverless_client_map)).thenReturn(
                fakeXmlResourceParser);

        mService.init();

        ArrayMap<String, String> clientIdsByPackageName =
                mService.getServerlessClientIdsByPackageName();
        assertWithMessage("clientIdsByPackageName").that(clientIdsByPackageName).hasSize(2);
        expectWithMessage("client ID for package 1").that(
                clientIdsByPackageName.get("android.car.app1")).isEqualTo("serverless_client_1234");
        expectWithMessage("client ID for package 2").that(
                clientIdsByPackageName.get("android.car.app2")).isEqualTo("serverless_client_2345");
    }

    private void enableTaskScheduling() {
        when(mRemoteAccessHalWrapper.isTaskScheduleSupported()).thenReturn(true);
        mService = newServiceWithSystemUpTime(ALLOWED_SYSTEM_UP_TIME_FOR_TESTING_MS);
    }

    @Test
    public void testIsTaskScheduleSupported_true() throws Exception {
        enableTaskScheduling();
        when(mDep.getCallingUid()).thenReturn(UID_SERVERLESS_PACKAGE);
        XmlResourceParser fakeXmlResourceParser = getFakeXmlResourceParser(
                SERVERLESS_CLIENT_MAP_XML);
        when(mResources.getXml(R.xml.remote_access_serverless_client_map)).thenReturn(
                fakeXmlResourceParser);
        mService.init();

        assertThat(mService.isTaskScheduleSupported()).isTrue();
    }

    @Test
    public void testIsTaskScheduleSupported_halNotSupported() {
        mService.init();

        assertThat(mService.isTaskScheduleSupported()).isFalse();
    }

    @Test
    public void testIsTaskScheduleSupported_clientNotConfigured() throws Exception {
        enableTaskScheduling();
        // UID_PERMISSION_GRANTED_PACKAGE_ONE is not a serverless remote task client.
        when(mDep.getCallingUid()).thenReturn(UID_PERMISSION_GRANTED_PACKAGE_ONE);
        XmlResourceParser fakeXmlResourceParser = getFakeXmlResourceParser(
                SERVERLESS_CLIENT_MAP_XML);
        when(mResources.getXml(R.xml.remote_access_serverless_client_map)).thenReturn(
                fakeXmlResourceParser);
        mService.init();

        assertThat(mService.isTaskScheduleSupported()).isFalse();
    }

    // Sets isTaskScheduleSupported to be true. Registers the caller as a serverless remote task
    // client.
    private void prepareTaskSchedule() throws Exception {
        enableTaskScheduling();
        when(mDep.getCallingUid()).thenReturn(UID_SERVERLESS_PACKAGE);
        XmlResourceParser fakeXmlResourceParser = getFakeXmlResourceParser(
                SERVERLESS_CLIENT_MAP_XML);
        when(mResources.getXml(R.xml.remote_access_serverless_client_map)).thenReturn(
                fakeXmlResourceParser);
        mService.init();
        runBootComplete();
        mService.addCarRemoteTaskClient(mRemoteAccessCallback);
    }

    private TaskScheduleInfo getTestTaskScheduleInfo() {
        TaskScheduleInfo taskScheduleInfo = new TaskScheduleInfo();
        taskScheduleInfo.taskType = TEST_TASK_TYPE;
        taskScheduleInfo.scheduleId = TEST_SCHEDULE_ID;
        taskScheduleInfo.taskData = TEST_TASK_DATA;
        taskScheduleInfo.count = TEST_TASK_COUNT;
        taskScheduleInfo.startTimeInEpochSeconds = TEST_START_TIME;
        taskScheduleInfo.periodicInSeconds = TEST_PERIODIC;
        return taskScheduleInfo;
    }

    @Test
    public void testScheduleTask() throws Exception {
        prepareTaskSchedule();

        TaskScheduleInfo testTaskScheduleInfo = getTestTaskScheduleInfo();
        mService.scheduleTask(testTaskScheduleInfo);

        verify(mRemoteAccessHalWrapper).scheduleTask(mHalScheduleInfoCaptor.capture());
        ScheduleInfo halScheduleInfo = mHalScheduleInfoCaptor.getValue();
        expectWithMessage("ScheduleInfo.clientId").that(halScheduleInfo.clientId).isEqualTo(
                CLIENT_ID_SERVERLESS);
        expectWithMessage("ScheduleInfo.taskType").that(halScheduleInfo.taskType).isEqualTo(
                TaskType.CUSTOM);
        expectWithMessage("ScheduleInfo.scheduleId").that(halScheduleInfo.scheduleId).isEqualTo(
                TEST_SCHEDULE_ID);
        expectWithMessage("ScheduleInfo.taskData").that(halScheduleInfo.taskData).isEqualTo(
                TEST_TASK_DATA);
        expectWithMessage("ScheduleInfo.count").that(halScheduleInfo.count).isEqualTo(
                TEST_TASK_COUNT);
        expectWithMessage("ScheduleInfo.startTimeInEpochSeconds").that(
                halScheduleInfo.startTimeInEpochSeconds).isEqualTo(TEST_START_TIME);
        expectWithMessage("ScheduleInfo.periodicInSeconds").that(
                halScheduleInfo.periodicInSeconds).isEqualTo(TEST_PERIODIC);
    }

    @Test
    public void testScheduleTask_enterGarageMode() throws Exception {
        prepareTaskSchedule();

        TaskScheduleInfo testTaskScheduleInfo = getTestTaskScheduleInfo();
        testTaskScheduleInfo.taskType = TASK_TYPE_ENTER_GARAGE_MODE;
        testTaskScheduleInfo.taskData = new byte[0];
        mService.scheduleTask(testTaskScheduleInfo);

        verify(mRemoteAccessHalWrapper).scheduleTask(mHalScheduleInfoCaptor.capture());
        ScheduleInfo halScheduleInfo = mHalScheduleInfoCaptor.getValue();
        expectWithMessage("ScheduleInfo.clientId").that(halScheduleInfo.clientId).isEqualTo(
                CLIENT_ID_SERVERLESS);
        expectWithMessage("ScheduleInfo.taskType").that(halScheduleInfo.taskType).isEqualTo(
                TaskType.ENTER_GARAGE_MODE);
        expectWithMessage("ScheduleInfo.scheduleId").that(halScheduleInfo.scheduleId).isEqualTo(
                TEST_SCHEDULE_ID);
        expectWithMessage("ScheduleInfo.taskData").that(halScheduleInfo.taskData).isEmpty();
        expectWithMessage("ScheduleInfo.count").that(halScheduleInfo.count).isEqualTo(
                TEST_TASK_COUNT);
        expectWithMessage("ScheduleInfo.startTimeInEpochSeconds").that(
                halScheduleInfo.startTimeInEpochSeconds).isEqualTo(TEST_START_TIME);
        expectWithMessage("ScheduleInfo.periodicInSeconds").that(
                halScheduleInfo.periodicInSeconds).isEqualTo(TEST_PERIODIC);
    }

    @Test
    public void testScheduleTask_unsupportedTaskType() throws Exception {
        prepareTaskSchedule();

        TaskScheduleInfo testTaskScheduleInfo = getTestTaskScheduleInfo();
        testTaskScheduleInfo.taskType = -1234;

        assertThrows(IllegalArgumentException.class,
                () -> mService.scheduleTask(testTaskScheduleInfo));
    }

    @Test
    public void testScheduleTask_unsupported() throws Exception {
        mService.init();

        TaskScheduleInfo testTaskScheduleInfo = getTestTaskScheduleInfo();
        assertThrows(IllegalStateException.class, () ->
                mService.scheduleTask(testTaskScheduleInfo));
    }

    @Test
    public void testScheduleTask_notServerlessClient() throws Exception {
        enableTaskScheduling();
        runBootComplete();
        mService.init();
        prepareCarRemoteTaskClient();

        TaskScheduleInfo testTaskScheduleInfo = getTestTaskScheduleInfo();
        assertThrows(IllegalStateException.class, () ->
                mService.scheduleTask(testTaskScheduleInfo));
    }

    @Test
    public void testScheduleTask_RemoteException() throws Exception {
        prepareTaskSchedule();
        doThrow(new RemoteException()).when(mRemoteAccessHalWrapper).scheduleTask(any());

        TaskScheduleInfo testTaskScheduleInfo = getTestTaskScheduleInfo();
        ServiceSpecificException e = assertThrows(ServiceSpecificException.class, () ->
                mService.scheduleTask(testTaskScheduleInfo));
        assertThat(e.errorCode).isEqualTo(SERVICE_ERROR_CODE_GENERAL);
    }

    @Test
    public void testScheduleTask_serviceSpecificException() throws Exception {
        prepareTaskSchedule();
        doThrow(new ServiceSpecificException(1234)).when(mRemoteAccessHalWrapper)
                .scheduleTask(any());

        TaskScheduleInfo testTaskScheduleInfo = getTestTaskScheduleInfo();
        ServiceSpecificException e = assertThrows(ServiceSpecificException.class, () ->
                mService.scheduleTask(testTaskScheduleInfo));
        assertThat(e.errorCode).isEqualTo(SERVICE_ERROR_CODE_GENERAL);
    }

    @Test
    public void testUnscheduleTask() throws Exception {
        prepareTaskSchedule();

        mService.unscheduleTask(TEST_SCHEDULE_ID);

        verify(mRemoteAccessHalWrapper).unscheduleTask(CLIENT_ID_SERVERLESS, TEST_SCHEDULE_ID);
    }

    @Test
    public void testUnscheduleTask_unsupported() throws Exception {
        mService.init();

        assertThrows(IllegalStateException.class, () -> mService.unscheduleTask(TEST_SCHEDULE_ID));
    }

    @Test
    public void testUnscheduleTask_RemoteException() throws Exception {
        prepareTaskSchedule();
        doThrow(new RemoteException()).when(mRemoteAccessHalWrapper).unscheduleTask(any(), any());

        ServiceSpecificException e = assertThrows(ServiceSpecificException.class, () ->
                mService.unscheduleTask(TEST_SCHEDULE_ID));
        assertThat(e.errorCode).isEqualTo(SERVICE_ERROR_CODE_GENERAL);
    }

    @Test
    public void testUnscheduleTask_serviceSpecificException() throws Exception {
        prepareTaskSchedule();
        doThrow(new ServiceSpecificException(1234)).when(mRemoteAccessHalWrapper)
                .unscheduleTask(any(), any());

        ServiceSpecificException e = assertThrows(ServiceSpecificException.class, () ->
                mService.unscheduleTask(TEST_SCHEDULE_ID));
        assertThat(e.errorCode).isEqualTo(SERVICE_ERROR_CODE_GENERAL);
    }

    @Test
    public void testUnscheduleAllTasks() throws Exception {
        prepareTaskSchedule();

        mService.unscheduleAllTasks();

        verify(mRemoteAccessHalWrapper).unscheduleAllTasks(CLIENT_ID_SERVERLESS);
    }

    @Test
    public void testUnscheduleAllTasks_unsupported() throws Exception {
        mService.init();

        assertThrows(IllegalStateException.class, () -> mService.unscheduleAllTasks());
    }

    @Test
    public void testUnscheduleAllTasks_RemoteException() throws Exception {
        prepareTaskSchedule();
        doThrow(new RemoteException()).when(mRemoteAccessHalWrapper).unscheduleAllTasks(any());

        ServiceSpecificException e = assertThrows(ServiceSpecificException.class, () ->
                mService.unscheduleAllTasks());
        assertThat(e.errorCode).isEqualTo(SERVICE_ERROR_CODE_GENERAL);
    }

    @Test
    public void testUnscheduleAllTasks_serviceSpecificException() throws Exception {
        prepareTaskSchedule();
        doThrow(new ServiceSpecificException(1234)).when(mRemoteAccessHalWrapper)
                .unscheduleAllTasks(any());

        ServiceSpecificException e = assertThrows(ServiceSpecificException.class, () ->
                mService.unscheduleAllTasks());
        assertThat(e.errorCode).isEqualTo(SERVICE_ERROR_CODE_GENERAL);
    }

    @Test
    public void testIsTaskScheduled() throws Exception {
        prepareTaskSchedule();

        when(mRemoteAccessHalWrapper.isTaskScheduled(CLIENT_ID_SERVERLESS, TEST_SCHEDULE_ID))
                .thenReturn(true);

        assertThat(mService.isTaskScheduled(TEST_SCHEDULE_ID)).isTrue();
    }

    @Test
    public void testIsTaskScheduled_unsupported() throws Exception {
        mService.init();

        assertThrows(IllegalStateException.class, () -> mService.isTaskScheduled(
                TEST_SCHEDULE_ID));
    }

    @Test
    public void testIsTaskScheduled_RemoteException() throws Exception {
        prepareTaskSchedule();
        doThrow(new RemoteException()).when(mRemoteAccessHalWrapper).isTaskScheduled(any(), any());

        ServiceSpecificException e = assertThrows(ServiceSpecificException.class, () ->
                mService.isTaskScheduled(TEST_SCHEDULE_ID));
        assertThat(e.errorCode).isEqualTo(SERVICE_ERROR_CODE_GENERAL);
    }

    @Test
    public void testIsTaskScheduled_serviceSpecificException() throws Exception {
        prepareTaskSchedule();
        doThrow(new ServiceSpecificException(1234)).when(mRemoteAccessHalWrapper)
                .isTaskScheduled(any(), any());

        ServiceSpecificException e = assertThrows(ServiceSpecificException.class, () ->
                mService.isTaskScheduled(TEST_SCHEDULE_ID));
        assertThat(e.errorCode).isEqualTo(SERVICE_ERROR_CODE_GENERAL);
    }

    @Test
    public void testGetAllPendingScheduledTasks() throws Exception {
        prepareTaskSchedule();
        ScheduleInfo scheduleInfo = new ScheduleInfo();
        scheduleInfo.scheduleId = TEST_SCHEDULE_ID;
        scheduleInfo.taskType = TaskType.CUSTOM;
        scheduleInfo.taskData = TEST_TASK_DATA;
        scheduleInfo.count = TEST_TASK_COUNT;
        scheduleInfo.startTimeInEpochSeconds = TEST_START_TIME;
        scheduleInfo.periodicInSeconds = TEST_PERIODIC;
        when(mRemoteAccessHalWrapper.getAllPendingScheduledTasks(CLIENT_ID_SERVERLESS))
                .thenReturn(List.of(scheduleInfo));

        List<TaskScheduleInfo> taskScheduleInfoList = mService.getAllPendingScheduledTasks();

        assertWithMessage("TaskScheduleInfo list").that(taskScheduleInfoList).hasSize(1);
        assertWithMessage("TaskScheduleInfo").that(taskScheduleInfoList.get(0)).isEqualTo(
                getTestTaskScheduleInfo());
    }

    @Test
    public void testGetAllPendingScheduledTasks_convertUnknownTaskType() throws Exception {
        prepareTaskSchedule();
        ScheduleInfo scheduleInfo = new ScheduleInfo();
        scheduleInfo.scheduleId = TEST_SCHEDULE_ID;
        // This task type is unknown and should be converted to CUSTOM.
        scheduleInfo.taskType = -1234;
        scheduleInfo.taskData = TEST_TASK_DATA;
        when(mRemoteAccessHalWrapper.getAllPendingScheduledTasks(CLIENT_ID_SERVERLESS))
                .thenReturn(List.of(scheduleInfo));

        List<TaskScheduleInfo> taskScheduleInfoList = mService.getAllPendingScheduledTasks();

        assertWithMessage("TaskScheduleInfo list").that(taskScheduleInfoList).hasSize(1);
        assertWithMessage("TaskScheduleInfo.taskType").that(taskScheduleInfoList.get(0).taskType)
                .isEqualTo(TASK_TYPE_CUSTOM);
    }

    @Test
    public void testGetAllPendingScheduledTasks_unsupported() throws Exception {
        mService.init();

        assertThrows(IllegalStateException.class, () -> mService.getAllPendingScheduledTasks());
    }

    @Test
    public void testGetAllPendingScheduledTasks_remoteException() throws Exception {
        prepareTaskSchedule();
        when(mRemoteAccessHalWrapper.getAllPendingScheduledTasks(CLIENT_ID_SERVERLESS))
                .thenThrow(new RemoteException());

        ServiceSpecificException e = assertThrows(ServiceSpecificException.class, () ->
                mService.getAllPendingScheduledTasks());
        assertThat(e.errorCode).isEqualTo(SERVICE_ERROR_CODE_GENERAL);
    }

    @Test
    public void testGetAllPendingScheduledTasks_serviceSpecificException() throws Exception {
        prepareTaskSchedule();
        when(mRemoteAccessHalWrapper.getAllPendingScheduledTasks(CLIENT_ID_SERVERLESS))
                .thenThrow(new ServiceSpecificException(1234));

        ServiceSpecificException e = assertThrows(ServiceSpecificException.class, () ->
                mService.getAllPendingScheduledTasks());
        assertThat(e.errorCode).isEqualTo(SERVICE_ERROR_CODE_GENERAL);
    }

    @Test
    public void testGetSupportedTaskTypesForScheduling() throws Exception {
        prepareTaskSchedule();
        when(mRemoteAccessHalWrapper.getSupportedTaskTypesForScheduling()).thenReturn(
                new int[]{TaskType.CUSTOM, TaskType.ENTER_GARAGE_MODE});

        assertThat(mService.getSupportedTaskTypesForScheduling()).isEqualTo(
                new int[]{TASK_TYPE_CUSTOM, TASK_TYPE_ENTER_GARAGE_MODE});
    }

    @Test
    public void testGetSupportedTaskTypesForScheduling_unsupportedTypeIgnored() throws Exception {
        prepareTaskSchedule();
        when(mRemoteAccessHalWrapper.getSupportedTaskTypesForScheduling()).thenReturn(
                new int[]{TaskType.CUSTOM, -1234});

        assertThat(mService.getSupportedTaskTypesForScheduling()).isEqualTo(
                new int[]{TASK_TYPE_CUSTOM});
    }

    @Test
    public void testGetSupportedTaskTypesForScheduling_taskScheduleNotSupported() throws Exception {
        mService.init();

        assertThat(mService.getSupportedTaskTypesForScheduling()).isEmpty();
        verify(mRemoteAccessHalWrapper, never()).getSupportedTaskTypesForScheduling();
    }

    @Test
    public void testOnPackageRemoved_unscheduleAllTasks() throws Exception {
        prepareTaskSchedule();

        Intent intent = new Intent(Intent.ACTION_PACKAGE_REMOVED,
                (new Uri.Builder()).scheme("package").opaquePart(SERVERLESS_PACKAGE).build());
        mBroadcastReceiver.onReceive(mContext, intent);

        verify(mRemoteAccessHalWrapper).unscheduleAllTasks(CLIENT_ID_SERVERLESS);
    }

    private ICarPowerStateListener getCarPowerStateListener() {
        ArgumentCaptor<ICarPowerStateListener> internalListenerCaptor =
                ArgumentCaptor.forClass(ICarPowerStateListener.class);
        verify(mCarPowerManagementService).registerListenerWithCompletion(
                internalListenerCaptor.capture());
        return internalListenerCaptor.getValue();
    }

    private RemoteAccessHalCallback prepareCarRemoteTaskClient() throws Exception {
        return prepareCarRemoteTaskClient(mRemoteAccessCallback,
                UID_PERMISSION_GRANTED_PACKAGE_ONE);
    }

    private RemoteAccessHalCallback prepareCarRemoteTaskClient(
            ICarRemoteAccessCallbackImpl callback, int uid) throws Exception {
        when(mDep.getCallingUid()).thenReturn(uid);
        RemoteAccessHalCallback halCallback = mService.getRemoteAccessHalCallback();
        mService.addCarRemoteTaskClient(callback);
        PollingCheck.check("Client is registered", WAIT_TIMEOUT_MS,
                () -> callback.getClientId() != null);
        return halCallback;
    }

    private void runBootComplete() {
        if (!mBootCompleted) {
            mBootCompleted = true;
            mBootComplete.run();
        }
    }

    private void prepareReportTaskDoneTest() throws Exception {
        runBootComplete();
        RemoteAccessHalCallback halCallback = prepareCarRemoteTaskClient();
        String clientId = mRemoteAccessCallback.getClientId();
        halCallback.onRemoteTaskRequested(clientId, /* data= */ null);
        PollingCheck.check("onRemoteTaskRequested should be called", WAIT_TIMEOUT_MS,
                () -> mRemoteAccessCallback.getTaskId() != null);
    }

    private void prepareReportTaskDoneTest(ICarRemoteAccessCallbackImpl callback, int uid)
            throws Exception {
        runBootComplete();
        RemoteAccessHalCallback halCallback = prepareCarRemoteTaskClient(callback, uid);
        String clientId = callback.getClientId();
        halCallback.onRemoteTaskRequested(clientId, /* data= */ null);
        PollingCheck.check("onRemoteTaskRequested should be called", WAIT_TIMEOUT_MS,
                () -> callback.getTaskId() != null);
    }

    private void mockPackageInfo() {
        mockPackageInfo(AVAILABLE_PACKAGES.size());
    }

    private void mockPackageInfo(int size) {
        List<ResolveInfo> resolveInfos = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            PackagePrepForTest packagePrep = AVAILABLE_PACKAGES.get(i);
            ResolveInfo resolveInfo = packagePrep.resolveInfo;
            resolveInfos.add(resolveInfo);
            String packageName = resolveInfo.serviceInfo.packageName;
            int permission = packagePrep.permissionGranted ? PackageManager.PERMISSION_GRANTED
                    : PackageManager.PERMISSION_DENIED;
            when(mPackageManager.checkPermission(Car.PERMISSION_USE_REMOTE_ACCESS, packageName))
                    .thenReturn(permission);
            when(mPackageManager.checkPermission(Car.PERMISSION_CONTROL_REMOTE_ACCESS, packageName))
                    .thenReturn(permission);
        }
        when(mPackageManager.queryIntentServicesAsUser(any(), anyInt(), any()))
                .thenReturn(resolveInfos);
        doAnswer(inv -> {
            Runnable runnable = inv.getArgument(0);
            mBootComplete = runnable;
            return null;
        }).when(mSystemInterface)
                .scheduleActionForBootCompleted(any(Runnable.class), any(Duration.class),
                        any(Duration.class));
    }

    private static PackagePrepForTest createPackagePrepForTest(String packageName, String className,
            boolean permissionGranted, int uid) {
        PackagePrepForTest packagePrep = new PackagePrepForTest();
        packagePrep.resolveInfo = new ResolveInfo();
        packagePrep.resolveInfo.serviceInfo = new ServiceInfo();
        packagePrep.resolveInfo.serviceInfo.packageName = packageName;
        packagePrep.resolveInfo.serviceInfo.name = className;
        packagePrep.resolveInfo.serviceInfo.applicationInfo = new ApplicationInfo();
        packagePrep.resolveInfo.serviceInfo.applicationInfo.uid = uid;
        packagePrep.permissionGranted = permissionGranted;
        return packagePrep;
    }

    private static final class PackagePrepForTest {
        public ResolveInfo resolveInfo;
        public boolean permissionGranted;
    }

    private void setVehicleInUse(boolean inUse) {
        when(mPowerHalService.isVehicleInUse()).thenReturn(inUse);
    }

    private void setupDatabase() {
        for (int i = 0; i < PERSISTENT_CLIENTS.size(); i++) {
            ClientIdEntry entry = PERSISTENT_CLIENTS.get(i);
            mRemoteAccessStorage.updateClientId(entry);
        }
    }

    private static final class ICarRemoteAccessCallbackImpl extends ICarRemoteAccessCallback.Stub {
        private final Object mLock = new Object();

        @GuardedBy("mLock")
        private String mServiceName;
        @GuardedBy("mLock")
        private String mVehicleId;
        @GuardedBy("mLock")
        private String mProcessorId;
        @GuardedBy("mLock")
        private String mClientId;
        @GuardedBy("mLock")
        private String mTaskId;
        @GuardedBy("mLock")
        private byte[] mData;
        @GuardedBy("mLock")
        private boolean mShutdownStarting;
        @GuardedBy("mLock")
        private int mTaskMaxDurationInSec;
        @GuardedBy("mLock")
        private boolean mServerlessClientRegistered;

        @Override
        public void onClientRegistrationUpdated(RemoteTaskClientRegistrationInfo info) {
            synchronized (mLock) {
                mServiceName = info.getServiceId();
                mVehicleId = info.getVehicleId();
                mProcessorId = info.getProcessorId();
                mClientId = info.getClientId();
            }
        }

        @Override
        public void onServerlessClientRegistered(String clientId) {
            synchronized (mLock) {
                mServerlessClientRegistered = true;
            }
        }

        @Override
        public void onClientRegistrationFailed() {
        }

        @Override
        public void onRemoteTaskRequested(String clientId, String taskId, byte[] data,
                int taskMaxDurationInSec) {
            synchronized (mLock) {
                mClientId = clientId;
                mTaskId = taskId;
                mData = data;
                mTaskMaxDurationInSec = taskMaxDurationInSec;
            }
        }

        @Override
        public void onShutdownStarting() {
            synchronized (mLock) {
                mShutdownStarting = true;
            }
        }

        public String getServiceName() {
            synchronized (mLock) {
                return mServiceName;
            }
        }

        public String getVehicleId() {
            synchronized (mLock) {
                return mVehicleId;
            }
        }

        public String getProcessorId() {
            synchronized (mLock) {
                return mProcessorId;
            }
        }

        public String getClientId() {
            synchronized (mLock) {
                return mClientId;
            }
        }

        public String getTaskId() {
            synchronized (mLock) {
                return mTaskId;
            }
        }

        public byte[] getData() {
            synchronized (mLock) {
                return mData;
            }
        }

        public int getTaskMaxDurationInSec() {
            synchronized (mLock) {
                return mTaskMaxDurationInSec;
            }
        }

        public boolean isShutdownStarting() {
            synchronized (mLock) {
                return mShutdownStarting;
            }
        }

        public boolean isServerlessClientRegistered() {
            synchronized (mLock) {
                return mServerlessClientRegistered;
            }
        }
    }
}

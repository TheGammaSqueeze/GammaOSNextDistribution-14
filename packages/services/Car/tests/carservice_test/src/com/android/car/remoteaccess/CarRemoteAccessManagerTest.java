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

package com.android.car.remoteaccess;

import static android.car.remoteaccess.CarRemoteAccessManager.NEXT_POWER_STATE_OFF;
import static android.car.remoteaccess.CarRemoteAccessManager.NEXT_POWER_STATE_ON;
import static android.car.remoteaccess.CarRemoteAccessManager.NEXT_POWER_STATE_SUSPEND_TO_DISK;
import static android.car.remoteaccess.CarRemoteAccessManager.NEXT_POWER_STATE_SUSPEND_TO_RAM;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.Car;
import android.car.remoteaccess.CarRemoteAccessManager;
import android.car.remoteaccess.CarRemoteAccessManager.CompletableRemoteTaskFuture;
import android.car.remoteaccess.CarRemoteAccessManager.RemoteTaskClientCallback;
import android.car.remoteaccess.RemoteTaskClientRegistrationInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.hardware.automotive.remoteaccess.ApState;
import android.hardware.automotive.remoteaccess.IRemoteAccess;
import android.hardware.automotive.remoteaccess.IRemoteTaskCallback;
import android.hardware.automotive.vehicle.RawPropValues;
import android.hardware.automotive.vehicle.VehicleApPowerStateConfigFlag;
import android.hardware.automotive.vehicle.VehicleApPowerStateShutdownParam;
import android.hardware.automotive.vehicle.VehiclePropValue;
import android.hardware.automotive.vehicle.VehicleProperty;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.ArraySet;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import com.android.car.ICarImpl;
import com.android.car.MockedCarTestBase;
import com.android.car.hal.test.AidlMockedVehicleHal.VehicleHalPropertyHandler;
import com.android.car.systeminterface.SystemStateInterface;
import com.android.compatibility.common.util.PollingCheck;
import com.android.internal.annotations.GuardedBy;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class CarRemoteAccessManagerTest extends MockedCarTestBase {
    private static final String TAG = CarRemoteAccessManagerTest.class.getSimpleName();

    private static final int DEFAULT_TIME_OUT_MS = 10_000;

    private static final String PACKAGE_NAME_1 = "android.car.package1";
    private static final String PACKAGE_NAME_2 = "android.car.package2";
    private static final String SERVICE_NAME_1 = "android.car.package1:service1";
    private static final String SERVICE_NAME_2 = "android.car.package2:service2";
    private static final String NO_PERMISSION_PACKAGE = "android.car.nopermission";
    private static final String TEST_VEHICLE_ID = "TEST_VIN";
    private static final String TEST_WAKEUP_SERVICE_NAME = "TEST_GOOGLE_WAKEUP_SERVICE";
    private static final String TEST_PROCESSOR_ID = "TEST_PROCESSOR_ID";
    private static final byte[] TEST_DATA = new byte[]{(byte) 0xDE, (byte) 0xAD, (byte) 0xBE,
            (byte) 0xEF};
    private static final byte[] TEST_DATA_2 = new byte[]{(byte) 0xBE, (byte) 0xEF};
    private static final long TEST_REMOTE_TASK_CLIENT_INIT_MS = 1_000;
    // This must be larger than 5 seconds so we have a chance to send out onShutdownStarting.
    private static final int TEST_ALLOWED_SYSTEM_UPTIME_IN_MS = 7_000;
    // This is the same as TASK_UNBIND_DELAY_MS defined in CarRemoteAccessService.
    private static final int TASK_UNBIND_DELAY_MS = 1000;

    private Executor mExecutor = Executors.newSingleThreadExecutor();
    private CarRemoteAccessManager mCarRemoteAccessManager;
    @Mock
    private IRemoteAccess mRemoteAccessHal;
    @Mock
    private PackageManager mPackageManager;
    @Mock
    private RemoteTaskClientCallback mRemoteTaskClientCallback;
    private final MockSystemStateInterface mSystemStateInterface = new MockSystemStateInterface();
    private final VhalPropertyHandler mPropertyHandler = new VhalPropertyHandler();

    @Captor
    private ArgumentCaptor<ApState> mApStateCaptor;
    @Captor
    private ArgumentCaptor<IRemoteTaskCallback> mRemoteTaskCallbackCaptor;
    @Captor
    private ArgumentCaptor<RemoteTaskClientRegistrationInfo> mRegistrationInfoCaptor;
    @Captor
    private ArgumentCaptor<String> mStringCaptor;
    @Captor
    private ArgumentCaptor<CompletableRemoteTaskFuture> mFutureCaptor;

    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private final Map<ServiceConnection, ComponentName> mRemoteTaskClientServices =
            new ArrayMap<>();

    private final class MyMockedCarTestContext extends MockedCarTestContext {
        MyMockedCarTestContext(Context base) {
            super(base);

            when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE))
                    .thenReturn(true);
            when(mPackageManager.queryIntentServicesAsUser(any(), anyInt(), any())).thenReturn(
                    List.of(newResolveInfo(PACKAGE_NAME_1, SERVICE_NAME_1, Process.myUid()),
                    newResolveInfo(PACKAGE_NAME_2, SERVICE_NAME_2, 12345),
                    newResolveInfo(NO_PERMISSION_PACKAGE, SERVICE_NAME_1, 0)));
            when(mPackageManager.getNameForUid(Process.myUid())).thenReturn(SERVICE_NAME_1);
            when(mPackageManager.getNameForUid(12345)).thenReturn(SERVICE_NAME_2);
            when(mPackageManager.checkPermission(Car.PERMISSION_USE_REMOTE_ACCESS, PACKAGE_NAME_1))
                    .thenReturn(PackageManager.PERMISSION_GRANTED);
            when(mPackageManager.checkPermission(Car.PERMISSION_USE_REMOTE_ACCESS, PACKAGE_NAME_2))
                    .thenReturn(PackageManager.PERMISSION_GRANTED);
            when(mPackageManager.checkPermission(Car.PERMISSION_USE_REMOTE_ACCESS,
                    NO_PERMISSION_PACKAGE)).thenReturn(PackageManager.PERMISSION_DENIED);
            when(mPackageManager.checkPermission(Car.PERMISSION_CONTROL_REMOTE_ACCESS,
                    PACKAGE_NAME_1)).thenReturn(PackageManager.PERMISSION_GRANTED);
            when(mPackageManager.checkPermission(Car.PERMISSION_CONTROL_REMOTE_ACCESS,
                    PACKAGE_NAME_2)).thenReturn(PackageManager.PERMISSION_GRANTED);
            when(mPackageManager.checkPermission(Car.PERMISSION_CONTROL_REMOTE_ACCESS,
                    NO_PERMISSION_PACKAGE)).thenReturn(PackageManager.PERMISSION_DENIED);
            when(mPackageManager.getPackagesForUid(Process.myUid())).thenReturn(
                    new String[]{PACKAGE_NAME_1});
            when(mPackageManager.getPackagesForUid(12345)).thenReturn(
                    new String[]{PACKAGE_NAME_2});
        }

        @Override
        public PackageManager getPackageManager() {
            return mPackageManager;
        }

        private boolean knownPackage(String packageName) {
            return Objects.equals(packageName, PACKAGE_NAME_1)
                    || Objects.equals(packageName, PACKAGE_NAME_2)
                    || Objects.equals(packageName, NO_PERMISSION_PACKAGE);
        }

        @Override
        public boolean bindServiceAsUser(Intent service, ServiceConnection conn, int flags,
                UserHandle user) {
            ComponentName component = service.getComponent();
            String packageName = component.getPackageName();

            if (!knownPackage(packageName)) {
                return true;
            }

            conn.onNullBinding(component);
            synchronized (mLock) {
                mRemoteTaskClientServices.put(conn, component);
            }
            return true;
        }

        @Override
        public void unbindService(ServiceConnection conn) {
            synchronized (mLock) {
                mRemoteTaskClientServices.remove(conn);
            }
        }
    }

    private Set<ComponentName> getRemoteTaskClientServices() {
        synchronized (mLock) {
            return new ArraySet<ComponentName>(mRemoteTaskClientServices.values());
        }
    }

    @Override
    protected MockedCarTestContext createMockedCarTestContext(Context context) {
        return new MyMockedCarTestContext(context);
    }

    static final class MockSystemStateInterface implements SystemStateInterface {
        private final Object mActionLock = new Object();
        @GuardedBy("mActionLock")
        private final List<Runnable> mActions = new ArrayList<>();

        @Override
        public void shutdown() {}

        @Override
        public boolean enterDeepSleep() {
            return true;
        }

        @Override
        public boolean enterHibernation() {
            return true;
        }

        @Override
        public void scheduleActionForBootCompleted(Runnable action, Duration delay,
                Duration delayRange) {
            synchronized (mActionLock) {
                mActions.add(action);
            }
        }

        @Override
        public boolean isSystemSupportingDeepSleep() {
            return true;
        }

        @Override
        public boolean isSystemSupportingHibernation() {
            return true;
        }

        public void runPostBootActions() {
            synchronized (mActionLock) {
                for (Runnable action : mActions) {
                    action.run();
                }
            }
        }
    }

    private static final class VhalPropertyHandler implements VehicleHalPropertyHandler {
        private final List<VehiclePropValue> mSetPropValues = new ArrayList<>();
        private boolean mVehicleInUse = false;

        @Override
        public VehiclePropValue onPropertyGet(VehiclePropValue value) {
            VehiclePropValue returnValue = new VehiclePropValue();
            returnValue.value = new RawPropValues();
            if (mVehicleInUse) {
                returnValue.value.int32Values = new int[]{1};
            } else {
                returnValue.value.int32Values = new int[]{0};
            }
            returnValue.prop = VehicleProperty.VEHICLE_IN_USE;
            return returnValue;
        }

        @Override
        public void onPropertySet(VehiclePropValue value) {
            mSetPropValues.add(value);
        }

        public void setVehicleInUse(boolean vehicleInUse) {
            mVehicleInUse = vehicleInUse;
        }

        public List<VehiclePropValue> getSetPropValues() {
            return mSetPropValues;
        }
    }

    @Override
    protected SystemStateInterface createMockSystemStateInterface() {
        return mSystemStateInterface;
    }

    private ResolveInfo newResolveInfo(String packageName, String serviceName, int uid) {
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.serviceInfo = new ServiceInfo();
        resolveInfo.serviceInfo.packageName = packageName;
        resolveInfo.serviceInfo.name = serviceName;
        resolveInfo.serviceInfo.applicationInfo = new ApplicationInfo();
        resolveInfo.serviceInfo.applicationInfo.uid = uid;
        return resolveInfo;
    }

    @Override
    protected void configureMockedHal() {
        addAidlProperty(VehicleProperty.VEHICLE_IN_USE, mPropertyHandler);
        addAidlProperty(VehicleProperty.SHUTDOWN_REQUEST, mPropertyHandler);

        // Support STR and STD.
        addAidlProperty(VehicleProperty.AP_POWER_STATE_REQ).setConfigArray(Arrays.asList(
                VehicleApPowerStateConfigFlag.ENABLE_DEEP_SLEEP_FLAG
                | VehicleApPowerStateConfigFlag.ENABLE_HIBERNATION_FLAG));
    }

    @Override
    protected void configureFakeSystemInterface() {
        // We need to do this before ICarImpl.init and after fake system interface is set.
        CarRemoteAccessService service = new CarRemoteAccessService(getContext(),
                getFakeSystemInterface(), /* powerHalService= */ null, /* dep= */ null,
                /* remoteAccessHal= */ mRemoteAccessHal,
                /* remoteAccessStorage= */ null, TEST_ALLOWED_SYSTEM_UPTIME_IN_MS,
                /* inMemoryStorage= */ true);
        service.setAllowedTimeForRemoteTaskClientInitMs(TEST_REMOTE_TASK_CLIENT_INIT_MS);
        setCarRemoteAccessService(service);
    }

    @Override
    protected void configureResourceOverrides(MockResources resources) {
        super.configureResourceOverrides(resources);
        // Enable remote access feature.
        resources.overrideResource(com.android.car.R.array.config_allowed_optional_car_features,
                new String[] {Car.CAR_REMOTE_ACCESS_SERVICE});
    }

    @Override
    protected void spyOnBeforeCarImplInit(ICarImpl carImpl) {
        try {
            when(mRemoteAccessHal.getVehicleId()).thenReturn(TEST_VEHICLE_ID);
            when(mRemoteAccessHal.getWakeupServiceName()).thenReturn(TEST_WAKEUP_SERVICE_NAME);
            when(mRemoteAccessHal.getProcessorId()).thenReturn(TEST_PROCESSOR_ID);
        } catch (RemoteException e) {
            // Must not happen.
        }
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();

        mCarRemoteAccessManager = (CarRemoteAccessManager) getCar().getCarManager(
                Car.CAR_REMOTE_ACCESS_SERVICE);
        assertThat(mCarRemoteAccessManager).isNotNull();
    }

    @Test
    public void testNotifyApInitialState() throws Exception {
        verify(mRemoteAccessHal, timeout(DEFAULT_TIME_OUT_MS)).notifyApStateChange(
                mApStateCaptor.capture());
        ApState apState = mApStateCaptor.getValue();

        assertThat(apState.isReadyForRemoteTask).isTrue();
        assertThat(apState.isWakeupRequired).isFalse();

        verify(mRemoteAccessHal, timeout(DEFAULT_TIME_OUT_MS)).setRemoteTaskCallback(any());
    }


    @Test
    public void testInitialBindRemoteTaskClientService() throws Exception {
        mSystemStateInterface.runPostBootActions();

        verify(mPackageManager, timeout(DEFAULT_TIME_OUT_MS)).queryIntentServicesAsUser(
                argThat(intent -> intent.getAction()
                        .equals(Car.CAR_REMOTEACCESS_REMOTE_TASK_CLIENT_SERVICE)),
                eq(0), eq(UserHandle.SYSTEM));
        PollingCheck.check("remote task client services are not started", DEFAULT_TIME_OUT_MS,
                () -> getRemoteTaskClientServices().size() >= 2);
        assertThat(getRemoteTaskClientServices()).isEqualTo(new ArraySet<>(Arrays.asList(
                new ComponentName[] {
                        new ComponentName(PACKAGE_NAME_1, SERVICE_NAME_1),
                        new ComponentName(PACKAGE_NAME_2, SERVICE_NAME_2)}
                )));
    }

    @Test
    public void testSetRemoteTaskClientGetRegistrationInfo() throws Exception {

        mCarRemoteAccessManager.setRemoteTaskClient(mExecutor, mRemoteTaskClientCallback);

        verify(mRemoteTaskClientCallback, timeout(DEFAULT_TIME_OUT_MS)).onRegistrationUpdated(
                mRegistrationInfoCaptor.capture());
        RemoteTaskClientRegistrationInfo registrationInfo = mRegistrationInfoCaptor.getValue();
        assertThat(registrationInfo).isNotNull();
        assertThat(registrationInfo.getServiceId()).isEqualTo(TEST_WAKEUP_SERVICE_NAME);
        assertThat(registrationInfo.getVehicleId()).isEqualTo(TEST_VEHICLE_ID);
        assertThat(registrationInfo.getProcessorId()).isEqualTo(TEST_PROCESSOR_ID);
        assertThat(registrationInfo.getClientId()).isNotNull();
        assertThat(registrationInfo.getClientId()).isNotEqualTo("");
    }

    @Test
    public void testSetRemoteTaskClientGetRegistrationInfo_sameClientIdForSameUid()
            throws Exception {
        mCarRemoteAccessManager.setRemoteTaskClient(mExecutor, mRemoteTaskClientCallback);

        verify(mRemoteTaskClientCallback, timeout(DEFAULT_TIME_OUT_MS)).onRegistrationUpdated(
                mRegistrationInfoCaptor.capture());
        RemoteTaskClientRegistrationInfo registrationInfo = mRegistrationInfoCaptor.getValue();
        String oldClientId = registrationInfo.getClientId();

        mCarRemoteAccessManager.clearRemoteTaskClient();

        clearInvocations(mRemoteTaskClientCallback);
        mCarRemoteAccessManager.setRemoteTaskClient(mExecutor, mRemoteTaskClientCallback);

        verify(mRemoteTaskClientCallback, timeout(DEFAULT_TIME_OUT_MS)).onRegistrationUpdated(
                mRegistrationInfoCaptor.capture());
        registrationInfo = mRegistrationInfoCaptor.getValue();
        String newClientId = registrationInfo.getClientId();

        assertThat(newClientId).isEqualTo(oldClientId);
    }

    private IRemoteTaskCallback getRemoteAccessHalCallback() throws Exception {
        verify(mRemoteAccessHal, timeout(DEFAULT_TIME_OUT_MS)).setRemoteTaskCallback(
                mRemoteTaskCallbackCaptor.capture());
        return mRemoteTaskCallbackCaptor.getValue();
    }

    private String setCallbackGetClientId(RemoteTaskClientCallback callback) {
        mCarRemoteAccessManager.setRemoteTaskClient(mExecutor, callback);
        verify(callback, timeout(DEFAULT_TIME_OUT_MS)).onRegistrationUpdated(
                mRegistrationInfoCaptor.capture());
        return mRegistrationInfoCaptor.getValue().getClientId();
    }

    @Test
    public void testRemoteTaskDeliveredToClient_taskArriveAfterClientRegistration()
            throws Exception {
        IRemoteTaskCallback remoteAccessHalCallback = getRemoteAccessHalCallback();

        // Wait for the remote task client services to be started.
        mSystemStateInterface.runPostBootActions();
        PollingCheck.check("remote task client services are not started", DEFAULT_TIME_OUT_MS,
                () -> getRemoteTaskClientServices().size() >= 2);

        // During init, the service will register itself.
        String clientId = setCallbackGetClientId(mRemoteTaskClientCallback);

        // Simulates a remote task arrive for this client.
        remoteAccessHalCallback.onRemoteTaskRequested(clientId, TEST_DATA);

        verify(mRemoteTaskClientCallback, timeout(DEFAULT_TIME_OUT_MS)).onRemoteTaskRequested(
                mStringCaptor.capture(), eq(TEST_DATA), anyInt());
        String taskId1 = mStringCaptor.getValue();

        // Simulates another remote task arrive.
        remoteAccessHalCallback.onRemoteTaskRequested(clientId, TEST_DATA_2);

        verify(mRemoteTaskClientCallback, timeout(DEFAULT_TIME_OUT_MS)).onRemoteTaskRequested(
                mStringCaptor.capture(), eq(TEST_DATA_2), anyInt());
        String taskId2 = mStringCaptor.getValue();

        assertWithMessage("Task ID must not duplicate").that(taskId2).isNotEqualTo(taskId1);
    }

    @Test
    public void testRemoteTaskDeliveredToClient_taskArriveBeforePackageDiscovery()
            throws Exception {
        IRemoteTaskCallback remoteAccessHalCallback = getRemoteAccessHalCallback();

        // This simulates a previous registration process when the remote task server registered
        // the client ID.
        RemoteTaskClientCallback tmpRemoteTaskClientCallback = mock(RemoteTaskClientCallback.class);
        String clientId = setCallbackGetClientId(tmpRemoteTaskClientCallback);
        mCarRemoteAccessManager.clearRemoteTaskClient();

        // Simulates a new reboot and a remote task arrives on boot.
        remoteAccessHalCallback.onRemoteTaskRequested(clientId, TEST_DATA);

        // This should trigger the binding for remote task client services.
        mSystemStateInterface.runPostBootActions();
        PollingCheck.check("remote task client services are not started", DEFAULT_TIME_OUT_MS,
                () ->  getRemoteTaskClientServices().size() >= 2);
        mCarRemoteAccessManager.setRemoteTaskClient(mExecutor, mRemoteTaskClientCallback);

        // Task must arrive after the client service is started and registered.
        verify(mRemoteTaskClientCallback, timeout(DEFAULT_TIME_OUT_MS)).onRemoteTaskRequested(any(),
                eq(TEST_DATA), anyInt());
    }

    @Test
    public void testRemoteTaskDeliveredToClient_taskArriveAfterPackageDiscoveryBeforeRegister()
            throws Exception {
        IRemoteTaskCallback remoteAccessHalCallback = getRemoteAccessHalCallback();

        // This simulates a previous registration process when the remote task server registered
        // the client ID.
        RemoteTaskClientCallback tmpRemoteTaskClientCallback = mock(RemoteTaskClientCallback.class);
        String clientId = setCallbackGetClientId(tmpRemoteTaskClientCallback);
        mCarRemoteAccessManager.clearRemoteTaskClient();

        // Simulates a new reboot.
        mSystemStateInterface.runPostBootActions();
        PollingCheck.check("remote task client services are not started", DEFAULT_TIME_OUT_MS,
                () ->  getRemoteTaskClientServices().size() >= 2);

        // Simulates a remote task arrives after remote task clients are bound but before they
        // are registered.
        remoteAccessHalCallback.onRemoteTaskRequested(clientId, TEST_DATA);

        mCarRemoteAccessManager.setRemoteTaskClient(mExecutor, mRemoteTaskClientCallback);

        // Task must arrive after the client service is started and registered.
        verify(mRemoteTaskClientCallback, timeout(DEFAULT_TIME_OUT_MS)).onRemoteTaskRequested(any(),
                eq(TEST_DATA), anyInt());
    }

    private String getClientIdAndWaitForSystemBoot() throws Exception {
        String clientId = setCallbackGetClientId(mRemoteTaskClientCallback);
        mCarRemoteAccessManager.clearRemoteTaskClient();
        mSystemStateInterface.runPostBootActions();
        PollingCheck.check("remote task client services are not started", DEFAULT_TIME_OUT_MS,
                () ->  getRemoteTaskClientServices().size() >= 2);
        return clientId;
    }

    @Test
    public void testRemoteTaskNotDeliveredAfterClearClient() throws Exception {
        IRemoteTaskCallback remoteAccessHalCallback = getRemoteAccessHalCallback();
        String clientId = getClientIdAndWaitForSystemBoot();

        remoteAccessHalCallback.onRemoteTaskRequested(clientId, TEST_DATA);

        SystemClock.sleep(1000);

        verify(mRemoteTaskClientCallback, never()).onRemoteTaskRequested(any(), any(), anyInt());
    }

    @Test
    public void testUnbindInitRemoteTaskClientService_rebindOnRemoteTask() throws Exception {
        mPropertyHandler.setVehicleInUse(true);
        IRemoteTaskCallback remoteAccessHalCallback = getRemoteAccessHalCallback();
        String clientId = getClientIdAndWaitForSystemBoot();

        PollingCheck.check(
                "remote task client services must be unbound after the init allowed time",
                TEST_REMOTE_TASK_CLIENT_INIT_MS + DEFAULT_TIME_OUT_MS,
                () ->  getRemoteTaskClientServices().size() == 0);

        remoteAccessHalCallback.onRemoteTaskRequested(clientId, TEST_DATA);

        // If the client service is unbound, a remote task should cause it to be bound again.
        PollingCheck.check(
                "remote task client services must be bound when task arrive",
                DEFAULT_TIME_OUT_MS, () ->  getRemoteTaskClientServices().size() >= 1);

        mCarRemoteAccessManager.setRemoteTaskClient(mExecutor, mRemoteTaskClientCallback);

        verify(mRemoteTaskClientCallback, timeout(DEFAULT_TIME_OUT_MS)).onRemoteTaskRequested(any(),
                eq(TEST_DATA), anyInt());
    }

    @Test
    public void testNotUnbindInitRemoteTaskClientServiceIfRemoteTaskArrive() throws Exception {
        IRemoteTaskCallback remoteAccessHalCallback = getRemoteAccessHalCallback();
        String clientId = getClientIdAndWaitForSystemBoot();

        // One client has an active remote task so it must not be unbound.
        mCarRemoteAccessManager.setRemoteTaskClient(mExecutor, mRemoteTaskClientCallback);
        remoteAccessHalCallback.onRemoteTaskRequested(clientId, TEST_DATA);

        SystemClock.sleep(TEST_REMOTE_TASK_CLIENT_INIT_MS);

        PollingCheck.check("init remote task client service must be unbound only when no remote"
                + " task arrives", DEFAULT_TIME_OUT_MS,
                () -> getRemoteTaskClientServices().size() == 1);
    }

    private void verifyShutdownRequestSent(int shutdownParam) throws Exception {
        PollingCheck.check("shutdown request must be sent", DEFAULT_TIME_OUT_MS,
                () -> mPropertyHandler.getSetPropValues().size() == 1);
        assertThat(mPropertyHandler.getSetPropValues()).hasSize(1);
        VehiclePropValue value = mPropertyHandler.getSetPropValues().get(0);
        assertThat(value.prop).isEqualTo(VehicleProperty.SHUTDOWN_REQUEST);
        assertThat(value.areaId).isEqualTo(0);
        assertThat(value.value.int32Values).isEqualTo(new int[]{shutdownParam});
    }

    @Test
    public void testShutdownDeviceAfterAllTaskComplete() throws Exception {
        mPropertyHandler.setVehicleInUse(false);
        IRemoteTaskCallback remoteAccessHalCallback = getRemoteAccessHalCallback();
        String clientId = getClientIdAndWaitForSystemBoot();

        mCarRemoteAccessManager.setRemoteTaskClient(mExecutor, mRemoteTaskClientCallback);
        remoteAccessHalCallback.onRemoteTaskRequested(clientId, TEST_DATA);
        remoteAccessHalCallback.onRemoteTaskRequested(clientId, TEST_DATA_2);

        verify(mRemoteTaskClientCallback, timeout(DEFAULT_TIME_OUT_MS)).onRemoteTaskRequested(
                mStringCaptor.capture(), eq(TEST_DATA), anyInt());
        String taskId1 = mStringCaptor.getValue();
        verify(mRemoteTaskClientCallback, timeout(DEFAULT_TIME_OUT_MS)).onRemoteTaskRequested(
                mStringCaptor.capture(), eq(TEST_DATA_2), anyInt());
        String taskId2 = mStringCaptor.getValue();

        mCarRemoteAccessManager.reportRemoteTaskDone(taskId1);

        // One task is still pending, shutdown must not be started.
        assertWithMessage("shutdown request must not be sent when one task is still pending")
                .that(mPropertyHandler.getSetPropValues()).isEmpty();

        mCarRemoteAccessManager.reportRemoteTaskDone(taskId2);

        verifyShutdownRequestSent(VehicleApPowerStateShutdownParam.SHUTDOWN_IMMEDIATELY);
    }

    private void registerClientCompleteTask() throws Exception {
        IRemoteTaskCallback remoteAccessHalCallback = getRemoteAccessHalCallback();
        String clientId = getClientIdAndWaitForSystemBoot();
        mCarRemoteAccessManager.setRemoteTaskClient(mExecutor, mRemoteTaskClientCallback);
        remoteAccessHalCallback.onRemoteTaskRequested(clientId, TEST_DATA);
        verify(mRemoteTaskClientCallback, timeout(DEFAULT_TIME_OUT_MS)).onRemoteTaskRequested(
                mStringCaptor.capture(), eq(TEST_DATA), anyInt());
        mCarRemoteAccessManager.reportRemoteTaskDone(mStringCaptor.getValue());
    }

    @Test
    public void testShutDownDeviceAfterAllTaskComplete_nextPowerStateOn() throws Exception {
        mPropertyHandler.setVehicleInUse(false);

        mCarRemoteAccessManager.setPowerStatePostTaskExecution(NEXT_POWER_STATE_ON, false);

        registerClientCompleteTask();

        assertWithMessage("shutdown request must not be sent when next power state is on")
                .that(mPropertyHandler.getSetPropValues()).isEmpty();
    }

    @Test
    public void testShutDownDeviceAfterAllTaskComplete_nextPowerStateOffGarageMode()
            throws Exception {
        mPropertyHandler.setVehicleInUse(false);

        mCarRemoteAccessManager.setPowerStatePostTaskExecution(NEXT_POWER_STATE_OFF, true);

        registerClientCompleteTask();

        verifyShutdownRequestSent(VehicleApPowerStateShutdownParam.SHUTDOWN_ONLY);
    }

    @Test
    public void testShutDownDeviceAfterAllTaskComplete_nextPowerStateSTR() throws Exception {
        mPropertyHandler.setVehicleInUse(false);

        mCarRemoteAccessManager.setPowerStatePostTaskExecution(NEXT_POWER_STATE_SUSPEND_TO_RAM,
                false);

        registerClientCompleteTask();

        verifyShutdownRequestSent(VehicleApPowerStateShutdownParam.SLEEP_IMMEDIATELY);
    }

    @Test
    public void testShutDownDeviceAfterAllTaskComplete_nextPowerStateSTRGarageMode()
            throws Exception {
        mPropertyHandler.setVehicleInUse(false);

        mCarRemoteAccessManager.setPowerStatePostTaskExecution(NEXT_POWER_STATE_SUSPEND_TO_RAM,
                true);

        registerClientCompleteTask();

        verifyShutdownRequestSent(VehicleApPowerStateShutdownParam.CAN_SLEEP);
    }

    @Test
    public void testShutDownDeviceAfterAllTaskComplete_nextPowerStateSTD() throws Exception {
        mPropertyHandler.setVehicleInUse(false);

        mCarRemoteAccessManager.setPowerStatePostTaskExecution(NEXT_POWER_STATE_SUSPEND_TO_DISK,
                false);

        registerClientCompleteTask();

        verifyShutdownRequestSent(VehicleApPowerStateShutdownParam.HIBERNATE_IMMEDIATELY);
    }

    @Test
    public void testShutDownDeviceAfterAllTaskComplete_nextPowerStateSTDGarageMode()
            throws Exception {
        mPropertyHandler.setVehicleInUse(false);

        mCarRemoteAccessManager.setPowerStatePostTaskExecution(NEXT_POWER_STATE_SUSPEND_TO_DISK,
                true);

        registerClientCompleteTask();

        verifyShutdownRequestSent(VehicleApPowerStateShutdownParam.CAN_HIBERNATE);
    }

    @Test
    public void testNotShutdownDeviceIfVehicleInUse() throws Exception {
        mPropertyHandler.setVehicleInUse(false);
        IRemoteTaskCallback remoteAccessHalCallback = getRemoteAccessHalCallback();
        String clientId = getClientIdAndWaitForSystemBoot();

        mCarRemoteAccessManager.setRemoteTaskClient(mExecutor, mRemoteTaskClientCallback);
        remoteAccessHalCallback.onRemoteTaskRequested(clientId, TEST_DATA);

        verify(mRemoteTaskClientCallback, timeout(DEFAULT_TIME_OUT_MS)).onRemoteTaskRequested(
                mStringCaptor.capture(), eq(TEST_DATA), anyInt());
        String taskId = mStringCaptor.getValue();

        mPropertyHandler.setVehicleInUse(true);
        mCarRemoteAccessManager.reportRemoteTaskDone(taskId);
        SystemClock.sleep(1000);

        assertWithMessage("shutdown request must not be sent when vehicle is in use")
                .that(mPropertyHandler.getSetPropValues()).isEmpty();
    }

    @Test
    public void testUnbindServiceIfTaskComplete() throws Exception {
        mPropertyHandler.setVehicleInUse(true);
        IRemoteTaskCallback remoteAccessHalCallback = getRemoteAccessHalCallback();
        String clientId = getClientIdAndWaitForSystemBoot();

        mCarRemoteAccessManager.setRemoteTaskClient(mExecutor, mRemoteTaskClientCallback);
        remoteAccessHalCallback.onRemoteTaskRequested(clientId, TEST_DATA);

        verify(mRemoteTaskClientCallback, timeout(DEFAULT_TIME_OUT_MS)).onRemoteTaskRequested(
                mStringCaptor.capture(), eq(TEST_DATA), anyInt());
        String taskId = mStringCaptor.getValue();
        assertThat(getRemoteTaskClientServices()).contains(new ComponentName(
                PACKAGE_NAME_1, SERVICE_NAME_1));

        mCarRemoteAccessManager.reportRemoteTaskDone(taskId);

        assertThat(getRemoteTaskClientServices()).contains(new ComponentName(
                PACKAGE_NAME_1, SERVICE_NAME_1));
        // The service should be unbound after TASK_UNBIND_DELAY_MS.
        PollingCheck.check("service should be unbound when all tasks complete",
                TASK_UNBIND_DELAY_MS + DEFAULT_TIME_OUT_MS,
                () -> !(getRemoteTaskClientServices().contains(
                        new ComponentName(PACKAGE_NAME_1, SERVICE_NAME_1))));
    }

    @Test
    public void testShutdownDeviceAfterAllowedSystemUptime() throws Exception {
        mPropertyHandler.setVehicleInUse(false);
        IRemoteTaskCallback remoteAccessHalCallback = getRemoteAccessHalCallback();
        String clientId = getClientIdAndWaitForSystemBoot();

        mCarRemoteAccessManager.setRemoteTaskClient(mExecutor, mRemoteTaskClientCallback);
        remoteAccessHalCallback.onRemoteTaskRequested(clientId, TEST_DATA);

        verify(mRemoteTaskClientCallback, timeout(DEFAULT_TIME_OUT_MS)).onRemoteTaskRequested(
                mStringCaptor.capture(), eq(TEST_DATA), anyInt());

        SystemClock.sleep(TEST_ALLOWED_SYSTEM_UPTIME_IN_MS);

        verify(mRemoteTaskClientCallback).onShutdownStarting(any());

        verifyShutdownRequestSent(VehicleApPowerStateShutdownParam.SHUTDOWN_IMMEDIATELY);
        assertWithMessage("all remote task client services must be unbound before shutdown")
                .that(getRemoteTaskClientServices()).isEmpty();
    }

    @Test
    public void testNotShutdownDeviceAfterAllowedSystemUptimeIfVehicleInUse() throws Exception {
        mPropertyHandler.setVehicleInUse(true);
        IRemoteTaskCallback remoteAccessHalCallback = getRemoteAccessHalCallback();
        String clientId = getClientIdAndWaitForSystemBoot();

        mCarRemoteAccessManager.setRemoteTaskClient(mExecutor, mRemoteTaskClientCallback);
        remoteAccessHalCallback.onRemoteTaskRequested(clientId, TEST_DATA);

        verify(mRemoteTaskClientCallback, timeout(DEFAULT_TIME_OUT_MS)).onRemoteTaskRequested(
                mStringCaptor.capture(), eq(TEST_DATA), anyInt());

        SystemClock.sleep(TEST_ALLOWED_SYSTEM_UPTIME_IN_MS);

        assertWithMessage("shutdown request must not be sent when vehicle is in use")
                .that(mPropertyHandler.getSetPropValues()).isEmpty();
    }

    @Test
    public void testShutdownDeviceUponReadyForShutdown() throws Exception {
        mPropertyHandler.setVehicleInUse(false);
        IRemoteTaskCallback remoteAccessHalCallback = getRemoteAccessHalCallback();
        String clientId = getClientIdAndWaitForSystemBoot();

        mCarRemoteAccessManager.setRemoteTaskClient(mExecutor, mRemoteTaskClientCallback);
        remoteAccessHalCallback.onRemoteTaskRequested(clientId, TEST_DATA);

        verify(mRemoteTaskClientCallback, timeout(DEFAULT_TIME_OUT_MS)).onRemoteTaskRequested(
                mStringCaptor.capture(), eq(TEST_DATA), anyInt());

        verify(mRemoteTaskClientCallback, timeout(TEST_ALLOWED_SYSTEM_UPTIME_IN_MS))
                .onShutdownStarting(mFutureCaptor.capture());
        CompletableRemoteTaskFuture future = mFutureCaptor.getValue();

        // Client finishes the future and the device should shutdown.
        future.complete();

        verifyShutdownRequestSent(VehicleApPowerStateShutdownParam.SHUTDOWN_IMMEDIATELY);
    }

    @Test
    public void testUnbindServiceAfterTimeout() throws Exception {
        mPropertyHandler.setVehicleInUse(true);
        IRemoteTaskCallback remoteAccessHalCallback = getRemoteAccessHalCallback();
        String clientId = getClientIdAndWaitForSystemBoot();

        mCarRemoteAccessManager.setRemoteTaskClient(mExecutor, mRemoteTaskClientCallback);
        remoteAccessHalCallback.onRemoteTaskRequested(clientId, TEST_DATA);

        PollingCheck.check("remote task client service must be unbound after timeout",
                TEST_ALLOWED_SYSTEM_UPTIME_IN_MS + DEFAULT_TIME_OUT_MS,
                () -> getRemoteTaskClientServices().size() == 0);

        remoteAccessHalCallback.onRemoteTaskRequested(clientId, TEST_DATA);

        PollingCheck.check("remote task client service must be bound when new task arrive",
                DEFAULT_TIME_OUT_MS, () -> getRemoteTaskClientServices().size() == 1);

        PollingCheck.check("remote task client service must be unbound after timeout",
                TEST_ALLOWED_SYSTEM_UPTIME_IN_MS + DEFAULT_TIME_OUT_MS,
                () -> getRemoteTaskClientServices().size() == 0);
    }
}

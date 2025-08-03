/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.car;

import static android.car.Car.CAR_DISPLAY_COMPAT_SERVICE;
import static android.car.builtin.content.pm.PackageManagerHelper.PROPERTY_CAR_SERVICE_PACKAGE_NAME;

import static com.android.car.CarServiceImpl.CAR_SERVICE_INIT_TIMING_MIN_DURATION_MS;
import static com.android.car.CarServiceImpl.CAR_SERVICE_INIT_TIMING_TAG;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DEPRECATED_CODE;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;
import static com.android.car.internal.SystemConstants.ICAR_SYSTEM_SERVER_CLIENT;

import android.annotation.MainThread;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.car.Car;
import android.car.CarFeatures;
import android.car.ICar;
import android.car.ICarResultReceiver;
import android.car.builtin.CarBuiltin;
import android.car.builtin.os.BinderHelper;
import android.car.builtin.os.BuildHelper;
import android.car.builtin.os.TraceHelper;
import android.car.builtin.os.UserManagerHelper;
import android.car.builtin.util.EventLogHelper;
import android.car.builtin.util.Slogf;
import android.car.feature.Flags;
import android.car.user.CarUserManager;
import android.content.Context;
import android.content.om.OverlayInfo;
import android.content.om.OverlayManager;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.hardware.automotive.vehicle.VehicleProperty;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArrayMap;
import android.util.Log;
import android.util.proto.ProtoOutputStream;

import com.android.car.admin.CarDevicePolicyService;
import com.android.car.am.CarActivityService;
import com.android.car.am.FixedActivityService;
import com.android.car.audio.CarAudioService;
import com.android.car.bluetooth.CarBluetoothService;
import com.android.car.cluster.ClusterHomeService;
import com.android.car.cluster.ClusterNavigationService;
import com.android.car.cluster.InstrumentClusterService;
import com.android.car.evs.CarEvsService;
import com.android.car.garagemode.GarageModeService;
import com.android.car.hal.HalPropValue;
import com.android.car.hal.VehicleHal;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.ICarServiceHelper;
import com.android.car.internal.ICarSystemServerClient;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.occupantconnection.CarOccupantConnectionService;
import com.android.car.occupantconnection.CarRemoteDeviceService;
import com.android.car.oem.CarOemProxyService;
import com.android.car.os.CarPerformanceService;
import com.android.car.pm.CarPackageManagerService;
import com.android.car.power.CarPowerManagementService;
import com.android.car.remoteaccess.CarRemoteAccessService;
import com.android.car.stats.CarStatsService;
import com.android.car.systeminterface.SystemInterface;
import com.android.car.systemui.keyguard.ExperimentalCarKeyguardService;
import com.android.car.telemetry.CarTelemetryService;
import com.android.car.user.CarUserNoticeService;
import com.android.car.user.CarUserService;
import com.android.car.user.ExperimentalCarUserService;
import com.android.car.util.LimitedTimingsTraceLog;
import com.android.car.vms.VmsBrokerService;
import com.android.car.watchdog.CarWatchdogService;
import com.android.car.wifi.CarWifiService;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;

public class ICarImpl extends ICar.Stub {

    public static final String INTERNAL_INPUT_SERVICE = "internal_input";
    public static final String INTERNAL_SYSTEM_ACTIVITY_MONITORING_SERVICE =
            "system_activity_monitoring";

    @VisibleForTesting
    static final String TAG = CarLog.tagFor(ICarImpl.class);

    private static final int INITIAL_VHAL_GET_RETRY = 2;

    private final Context mContext;
    private final Context mCarServiceBuiltinPackageContext;
    private final VehicleHal mHal;

    private final CarServiceHelperWrapper mCarServiceHelperWrapper;

    private final CarFeatureController mFeatureController;

    private final SystemInterface mSystemInterface;

    private final CarOemProxyService mCarOemService;
    private final SystemActivityMonitoringService mSystemActivityMonitoringService;
    private final CarPowerManagementService mCarPowerManagementService;
    private final CarPackageManagerService mCarPackageManagerService;
    private final CarInputService mCarInputService;
    private final CarDrivingStateService mCarDrivingStateService;
    private final CarUxRestrictionsManagerService mCarUXRestrictionsService;
    private final OccupantAwarenessService mOccupantAwarenessService;
    private final CarAudioService mCarAudioService;
    private final CarProjectionService mCarProjectionService;
    private final CarPropertyService mCarPropertyService;
    private final CarNightService mCarNightService;
    private final AppFocusService mAppFocusService;
    private final FixedActivityService mFixedActivityService;
    private final GarageModeService mGarageModeService;
    private final ClusterNavigationService mClusterNavigationService;
    private final InstrumentClusterService mInstrumentClusterService;
    private final CarLocationService mCarLocationService;
    private final CarBluetoothService mCarBluetoothService;
    private final CarPerUserServiceHelper mCarPerUserServiceHelper;
    private final CarDiagnosticService mCarDiagnosticService;
    private final CarStorageMonitoringService mCarStorageMonitoringService;
    private final CarMediaService mCarMediaService;
    private final CarUserService mCarUserService;
    @Nullable
    private final ExperimentalCarUserService mExperimentalCarUserService;
    @Nullable
    private final ExperimentalCarKeyguardService mExperimentalCarKeyguardService;
    private final CarOccupantZoneService mCarOccupantZoneService;
    private final CarUserNoticeService mCarUserNoticeService;
    private final VmsBrokerService mVmsBrokerService;
    private final CarBugreportManagerService mCarBugreportManagerService;
    private final CarStatsService mCarStatsService;
    private final CarExperimentalFeatureServiceController mCarExperimentalFeatureServiceController;
    private final CarWatchdogService mCarWatchdogService;
    private final CarPerformanceService mCarPerformanceService;
    private final CarDevicePolicyService mCarDevicePolicyService;
    private final ClusterHomeService mClusterHomeService;
    private final CarEvsService mCarEvsService;
    private final CarTelemetryService mCarTelemetryService;
    private final CarActivityService mCarActivityService;
    private final CarOccupantConnectionService mCarOccupantConnectionService;
    private final CarRemoteDeviceService mCarRemoteDeviceService;
    private final CarWifiService mCarWifiService;

    // Only modified at setCarRemoteAccessService for testing.
    @Nullable
    private CarRemoteAccessService mCarRemoteAccessService;

    // Storing all the car services in the order of their init.
    private final CarSystemService[] mAllServicesInInitOrder;

    private static final boolean DBG = Slogf.isLoggable(TAG, Log.DEBUG);

    private final Object mLock = new Object();

    // This flag indicates whether priorityInit() should be called in the constructor or
    // will be deferred to CarImpl.init(). With the new boot user code flow, the boot user is set
    // in initialUserSetter as early as possible. The earliest it can be done is in the ICarImpl
    // constructor. In priorityInit() HAL and UserService are initialized which sets boot user.
    private final boolean mDoPriorityInitInConstruction;

    /** Test only service. Populate it only when necessary. */
    @GuardedBy("mLock")
    private CarTestService mCarTestService;

    private final String mVehicleInterfaceName;

    private final ICarSystemServerClientImpl mICarSystemServerClientImpl;

    private final BinderHelper.ShellCommandListener mCmdListener =
            (FileDescriptor in, FileDescriptor out, FileDescriptor err, String[] args) ->
                    newCarShellCommand().exec(ICarImpl.this, in, out, err, args);

    private ICarImpl(Builder builder) {
        LimitedTimingsTraceLog t = new LimitedTimingsTraceLog(
                CAR_SERVICE_INIT_TIMING_TAG, TraceHelper.TRACE_TAG_CAR_SERVICE,
                CAR_SERVICE_INIT_TIMING_MIN_DURATION_MS);
        t.traceBegin("ICarImpl.constructor");

        mDoPriorityInitInConstruction = builder.mDoPriorityInitInConstruction;

        mContext = builder.mContext;
        if (builder.mCarServiceBuiltinPackageContext == null) {
            mCarServiceBuiltinPackageContext = mContext;
        } else {
            mCarServiceBuiltinPackageContext = builder.mCarServiceBuiltinPackageContext;
        }

        mCarServiceHelperWrapper = CarServiceHelperWrapper.create();

        // Currently there are ~36 services, hence using 40 as the initial capacity.
        List<CarSystemService> allServices = new ArrayList<>(40);
        mCarOemService = constructWithTrace(t, CarOemProxyService.class,
                () -> new CarOemProxyService(mContext), allServices);

        mSystemInterface = builder.mSystemInterface;
        CarLocalServices.addService(SystemInterface.class, mSystemInterface);

        mHal = constructWithTrace(t, VehicleHal.class,
                () -> new VehicleHal(mContext, builder.mVehicle), allServices);

        HalPropValue disabledOptionalFeatureValue = mHal.getIfSupportedOrFailForEarlyStage(
                VehicleProperty.DISABLED_OPTIONAL_FEATURES, INITIAL_VHAL_GET_RETRY);

        String[] disabledFeaturesFromVhal = null;
        if (disabledOptionalFeatureValue != null) {
            String disabledFeatures = disabledOptionalFeatureValue.getStringValue();
            if (disabledFeatures != null && !disabledFeatures.isEmpty()) {
                disabledFeaturesFromVhal = disabledFeatures.split(",");
            }
        }
        if (disabledFeaturesFromVhal == null) {
            disabledFeaturesFromVhal = new String[0];
        }
        Resources res = mContext.getResources();
        String[] defaultEnabledFeatures = res.getStringArray(
                R.array.config_allowed_optional_car_features);
        final String[] disabledFromVhal = disabledFeaturesFromVhal;
        mFeatureController = constructWithTrace(t, CarFeatureController.class,
                () -> new CarFeatureController(mContext, defaultEnabledFeatures,
                        disabledFromVhal, mSystemInterface.getSystemCarDir()), allServices);
        mVehicleInterfaceName = builder.mVehicleInterfaceName;
        mCarPropertyService = constructWithTrace(
                t, CarPropertyService.class,
                () -> new CarPropertyService(mContext, mHal.getPropertyHal()), allServices);
        mCarDrivingStateService = constructWithTrace(
                t, CarDrivingStateService.class,
                () -> new CarDrivingStateService(mContext, mCarPropertyService), allServices);
        mCarOccupantZoneService = constructWithTrace(t, CarOccupantZoneService.class,
                () -> new CarOccupantZoneService(mContext), allServices);
        mCarUXRestrictionsService = constructWithTrace(t, CarUxRestrictionsManagerService.class,
                () -> new CarUxRestrictionsManagerService(mContext, mCarDrivingStateService,
                        mCarPropertyService, mCarOccupantZoneService), allServices);
        mCarActivityService = constructWithTrace(t, CarActivityService.class,
                () -> new CarActivityService(mContext), allServices);
        mCarPackageManagerService = constructWithTrace(t, CarPackageManagerService.class,
                () -> new CarPackageManagerService(mContext, mCarUXRestrictionsService,
                        mCarActivityService, mCarOccupantZoneService), allServices);
        UserManager userManager = mContext.getSystemService(UserManager.class);
        if (builder.mCarUserService != null) {
            mCarUserService = builder.mCarUserService;
            CarLocalServices.addService(CarUserService.class, mCarUserService);
            allServices.add(mCarUserService);
        } else {
            int maxRunningUsers = UserManagerHelper.getMaxRunningUsers(mContext);
            mCarUserService = constructWithTrace(t, CarUserService.class,
                    () -> new CarUserService(mContext, mHal.getUserHal(), userManager,
                            maxRunningUsers, mCarUXRestrictionsService, mCarPackageManagerService,
                            mCarOccupantZoneService),
                    allServices);
        }
        if (mDoPriorityInitInConstruction) {
            Slogf.i(TAG, "VHAL Priority Init Enabled");
            Slogf.i(TAG, "Car User Service Priority Init Enabled");
            priorityInit();
        }

        if (mFeatureController.isFeatureEnabled(Car.EXPERIMENTAL_CAR_USER_SERVICE)) {
            mExperimentalCarUserService = constructWithTrace(t, ExperimentalCarUserService.class,
                    () -> new ExperimentalCarUserService(mContext, mCarUserService,
                            userManager), allServices);
        } else {
            mExperimentalCarUserService = null;
        }
        if (mFeatureController.isFeatureEnabled(Car.EXPERIMENTAL_CAR_KEYGUARD_SERVICE)) {
            mExperimentalCarKeyguardService = constructWithTrace(t,
                        ExperimentalCarKeyguardService.class,
                    () -> new ExperimentalCarKeyguardService(mContext, mCarUserService,
                            mCarOccupantZoneService), allServices);
        } else {
            mExperimentalCarKeyguardService = null;
        }
        mSystemActivityMonitoringService = constructWithTrace(
                t, SystemActivityMonitoringService.class,
                () -> new SystemActivityMonitoringService(mContext), allServices);
        mCarPowerManagementService = constructWithTrace(
                t, CarPowerManagementService.class,
                () -> new CarPowerManagementService(mContext, mHal.getPowerHal(), mSystemInterface,
                        mCarUserService, builder.mPowerPolicyDaemon), allServices);
        if (mFeatureController.isFeatureEnabled(CarFeatures.FEATURE_CAR_USER_NOTICE_SERVICE)) {
            mCarUserNoticeService = constructWithTrace(
                    t, CarUserNoticeService.class, () -> new CarUserNoticeService(mContext),
                    allServices);
        } else {
            mCarUserNoticeService = null;
        }
        if (mFeatureController.isFeatureEnabled(Car.OCCUPANT_AWARENESS_SERVICE)) {
            mOccupantAwarenessService = constructWithTrace(t, OccupantAwarenessService.class,
                    () -> new OccupantAwarenessService(mContext), allServices);
        } else {
            mOccupantAwarenessService = null;
        }
        mCarPerUserServiceHelper = constructWithTrace(
                t, CarPerUserServiceHelper.class,
                () -> new CarPerUserServiceHelper(mContext, mCarUserService), allServices);
        mCarBluetoothService = constructWithTrace(t, CarBluetoothService.class,
                () -> new CarBluetoothService(mContext, mCarPerUserServiceHelper),
                allServices);
        mCarInputService = constructWithTrace(t, CarInputService.class,
                () -> new CarInputService(mContext, mHal.getInputHal(), mCarUserService,
                        mCarOccupantZoneService, mCarBluetoothService, mCarPowerManagementService,
                        mSystemInterface), allServices);
        mCarProjectionService = constructWithTrace(t, CarProjectionService.class,
                () -> new CarProjectionService(mContext, null /* handler */, mCarInputService,
                        mCarBluetoothService), allServices);
        if (builder.mGarageModeService == null) {
            mGarageModeService = constructWithTrace(t, GarageModeService.class,
                    () -> new GarageModeService(mContext), allServices);
        } else {
            mGarageModeService = builder.mGarageModeService;
            allServices.add(mGarageModeService);
        }
        mAppFocusService = constructWithTrace(t, AppFocusService.class,
                () -> new AppFocusService(mContext, mSystemActivityMonitoringService),
                allServices);
        mCarAudioService = constructWithTrace(t, CarAudioService.class,
                () -> new CarAudioService(mContext), allServices);
        mCarNightService = constructWithTrace(t, CarNightService.class,
                () -> new CarNightService(mContext, mCarPropertyService), allServices);
        mFixedActivityService = constructWithTrace(t, FixedActivityService.class,
                () -> new FixedActivityService(mContext, mCarActivityService), allServices);
        mClusterNavigationService = constructWithTrace(
                t, ClusterNavigationService.class,
                () -> new ClusterNavigationService(mContext, mAppFocusService), allServices);
        if (mFeatureController.isFeatureEnabled(Car.CAR_INSTRUMENT_CLUSTER_SERVICE)) {
            mInstrumentClusterService = constructWithTrace(t, InstrumentClusterService.class,
                    () -> new InstrumentClusterService(mContext, mClusterNavigationService,
                            mCarInputService), allServices);
        } else {
            mInstrumentClusterService = null;
        }

        mCarStatsService = constructWithTrace(t, CarStatsService.class,
                () -> new CarStatsService(mContext), allServices);

        if (mFeatureController.isFeatureEnabled(Car.VEHICLE_MAP_SERVICE)) {
            mVmsBrokerService = constructWithTrace(t, VmsBrokerService.class,
                    () -> new VmsBrokerService(mContext, mCarStatsService), allServices);
        } else {
            mVmsBrokerService = null;
        }
        if (mFeatureController.isFeatureEnabled(Car.DIAGNOSTIC_SERVICE)) {
            mCarDiagnosticService = constructWithTrace(t, CarDiagnosticService.class,
                    () -> new CarDiagnosticService(mContext, mHal.getDiagnosticHal()), allServices);
        } else {
            mCarDiagnosticService = null;
        }
        if (mFeatureController.isFeatureEnabled(Car.STORAGE_MONITORING_SERVICE)) {
            mCarStorageMonitoringService = constructWithTrace(
                    t, CarStorageMonitoringService.class,
                    () -> new CarStorageMonitoringService(mContext, mSystemInterface), allServices);
        } else {
            mCarStorageMonitoringService = null;
        }
        mCarLocationService = constructWithTrace(t, CarLocationService.class,
                () -> new CarLocationService(mContext), allServices);
        mCarMediaService = constructWithTrace(t, CarMediaService.class,
                () -> new CarMediaService(mContext, mCarOccupantZoneService, mCarUserService,
                        mCarPowerManagementService),
                allServices);
        mCarBugreportManagerService = constructWithTrace(t, CarBugreportManagerService.class,
                () -> new CarBugreportManagerService(mContext), allServices);
        if (builder.mCarWatchdogService == null) {
            mCarWatchdogService = constructWithTrace(t, CarWatchdogService.class,
                    () -> new CarWatchdogService(mContext, mCarServiceBuiltinPackageContext),
                    allServices);
        } else {
            mCarWatchdogService = builder.mCarWatchdogService;
            allServices.add(mCarWatchdogService);
            CarLocalServices.addService(CarWatchdogService.class, mCarWatchdogService);
        }
        if (builder.mCarPerformanceService == null) {
            mCarPerformanceService = constructWithTrace(t, CarPerformanceService.class,
                    () -> new CarPerformanceService(mContext), allServices);
        } else {
            mCarPerformanceService = builder.mCarPerformanceService;
            allServices.add(mCarPerformanceService);
            CarLocalServices.addService(CarPerformanceService.class, mCarPerformanceService);
        }
        mCarDevicePolicyService = constructWithTrace(
                t, CarDevicePolicyService.class, () -> new CarDevicePolicyService(mContext,
                        mCarServiceBuiltinPackageContext, mCarUserService), allServices);
        if (mFeatureController.isFeatureEnabled(Car.CLUSTER_HOME_SERVICE)) {
            if (!mFeatureController.isFeatureEnabled(Car.CAR_INSTRUMENT_CLUSTER_SERVICE)) {
                mClusterHomeService = constructWithTrace(
                        t, ClusterHomeService.class,
                        () -> new ClusterHomeService(mContext, mHal.getClusterHal(),
                                mClusterNavigationService, mCarOccupantZoneService,
                                mFixedActivityService), allServices);
            } else {
                Slogf.w(TAG, "Can't init ClusterHomeService, since Old cluster service is running");
                mClusterHomeService = null;
            }
        } else {
            mClusterHomeService = null;
        }

        if (mFeatureController.isFeatureEnabled(Car.CAR_EVS_SERVICE)) {
            mCarEvsService = constructWithTrace(t, CarEvsService.class,
                    () -> new CarEvsService(mContext, mCarServiceBuiltinPackageContext,
                            mHal.getEvsHal(), mCarPropertyService), allServices);
        } else {
            mCarEvsService = null;
        }

        if (mFeatureController.isFeatureEnabled(Car.CAR_TELEMETRY_SERVICE)) {
            if (builder.mCarTelemetryService == null) {
                mCarTelemetryService = constructWithTrace(t, CarTelemetryService.class,
                        () -> new CarTelemetryService(mContext, mCarPowerManagementService,
                                mCarPropertyService), allServices);
            } else {
                mCarTelemetryService = builder.mCarTelemetryService;
                allServices.add(mCarTelemetryService);
            }
        } else {
            mCarTelemetryService = null;
        }

        if (mFeatureController.isFeatureEnabled((Car.CAR_REMOTE_ACCESS_SERVICE))) {
            if (builder.mCarRemoteAccessService == null) {
                mCarRemoteAccessService = constructWithTrace(t, CarRemoteAccessService.class,
                        () -> new CarRemoteAccessService(
                                mContext, mSystemInterface, mHal.getPowerHal()), allServices);
            } else {
                mCarRemoteAccessService = builder.mCarRemoteAccessService;
                mCarRemoteAccessService.setPowerHal(mHal.getPowerHal());
                allServices.add(mCarRemoteAccessService);
            }
        } else {
            mCarRemoteAccessService = null;
        }

        mCarWifiService = constructWithTrace(t, CarWifiService.class,
                () -> new CarWifiService(mContext, mCarPowerManagementService,
                        mCarUserService), allServices);

        // Always put mCarExperimentalFeatureServiceController in last.
        if (!BuildHelper.isUserBuild()) {
            mCarExperimentalFeatureServiceController = constructWithTrace(
                    t, CarExperimentalFeatureServiceController.class,
                    () -> new CarExperimentalFeatureServiceController(mContext),
                    allServices);
        } else {
            mCarExperimentalFeatureServiceController = null;
        }

        if (mFeatureController.isFeatureEnabled(Car.CAR_OCCUPANT_CONNECTION_SERVICE)
                || mFeatureController.isFeatureEnabled(Car.CAR_REMOTE_DEVICE_SERVICE)) {
            mCarRemoteDeviceService = constructWithTrace(
                    t, CarRemoteDeviceService.class,
                    () -> new CarRemoteDeviceService(mContext, mCarOccupantZoneService,
                            mCarPowerManagementService, mSystemActivityMonitoringService),
                    allServices);
            mCarOccupantConnectionService = constructWithTrace(
                    t, CarOccupantConnectionService.class,
                    () -> new CarOccupantConnectionService(mContext, mCarOccupantZoneService,
                            mCarRemoteDeviceService),
                    allServices);

        } else {
            mCarOccupantConnectionService = null;
            mCarRemoteDeviceService = null;
        }

        mAllServicesInInitOrder = allServices.toArray(new CarSystemService[allServices.size()]);
        mICarSystemServerClientImpl = new ICarSystemServerClientImpl();

        t.traceEnd(); // "ICarImpl.constructor"
    }

    @MainThread
    void init() {
        LimitedTimingsTraceLog t = new LimitedTimingsTraceLog(CAR_SERVICE_INIT_TIMING_TAG,
                TraceHelper.TRACE_TAG_CAR_SERVICE, CAR_SERVICE_INIT_TIMING_MIN_DURATION_MS);

        t.traceBegin("ICarImpl.init");
        if (!mDoPriorityInitInConstruction) {
            priorityInit();
        }

        t.traceBegin("CarService.initAllServices");
        for (CarSystemService service : mAllServicesInInitOrder) {
            t.traceBegin(service.getClass().getSimpleName());
            service.init();
            t.traceEnd();
        }
        t.traceEnd(); // "CarService.initAllServices"

        t.traceBegin("CarService.onInitComplete");
        for (CarSystemService service : mAllServicesInInitOrder) {
            if (service == mCarPowerManagementService) {
                // Must make sure mCarPowerManagementService.onInitComplete runs at last since
                // it might shutdown the device.
                continue;
            }
            t.traceBegin("onInitComplete:" + service.getClass().getSimpleName());
            service.onInitComplete();
            t.traceEnd();
        }
        mCarPowerManagementService.onInitComplete();
        t.traceEnd(); // "CarService.onInitComplete"

        t.traceEnd(); // "ICarImpl.init"
    }

    void release() {
        // release done in opposite order from init
        for (int i = mAllServicesInInitOrder.length - 1; i >= 0; i--) {
            mAllServicesInInitOrder[i].release();
        }
    }

    @Override
    public void setSystemServerConnections(ICarServiceHelper carServiceHelper,
            ICarResultReceiver resultReceiver) {
        Bundle bundle;
        try {
            EventLogHelper.writeCarServiceSetCarServiceHelper(Binder.getCallingPid());
            assertCallingFromSystemProcess();

            mCarServiceHelperWrapper.setCarServiceHelper(carServiceHelper);

            bundle = new Bundle();
            bundle.putBinder(ICAR_SYSTEM_SERVER_CLIENT, mICarSystemServerClientImpl.asBinder());
        } catch (Exception e) {
            // send back a null response
            Slogf.w(TAG, "Exception in setSystemServerConnections", e);
            bundle = null;
        }

        try {
            resultReceiver.send(/* unused */ 0, bundle);
        } catch (RemoteException e) {
            Slogf.w(TAG, "RemoteException from CarServiceHelperService", e);
        }
    }

    @Override
    public boolean isFeatureEnabled(String featureName) {
        return mFeatureController.isFeatureEnabled(featureName);
    }

    @Override
    public int enableFeature(String featureName) {
        // permission check inside the controller
        return mFeatureController.enableFeature(featureName);
    }

    @Override
    public int disableFeature(String featureName) {
        // permission check inside the controller
        return mFeatureController.disableFeature(featureName);
    }

    @Override
    public List<String> getAllEnabledFeatures() {
        // permission check inside the controller
        return mFeatureController.getAllEnabledFeatures();
    }

    @Override
    public List<String> getAllPendingDisabledFeatures() {
        // permission check inside the controller
        return mFeatureController.getAllPendingDisabledFeatures();
    }

    @Override
    public List<String> getAllPendingEnabledFeatures() {
        // permission check inside the controller
        return mFeatureController.getAllPendingEnabledFeatures();
    }

    @Override
    @Nullable
    public String getCarManagerClassForFeature(String featureName) {
        if (mCarExperimentalFeatureServiceController == null) {
            return null;
        }
        return mCarExperimentalFeatureServiceController.getCarManagerClassForFeature(featureName);
    }

    static void assertCallingFromSystemProcess() {
        int uid = Binder.getCallingUid();
        if (uid != Process.SYSTEM_UID) {
            throw new SecurityException("Only allowed from system");
        }
    }

    @Override
    @Nullable
    public IBinder getCarService(String serviceName) {
        if (!mFeatureController.isFeatureEnabled(serviceName)) {
            Slogf.w(CarLog.TAG_SERVICE, "getCarService for disabled service:" + serviceName);
            return null;
        }
        switch (serviceName) {
            case Car.AUDIO_SERVICE:
                return mCarAudioService;
            case Car.APP_FOCUS_SERVICE:
                return mAppFocusService;
            case Car.PACKAGE_SERVICE:
                return mCarPackageManagerService;
            case Car.DIAGNOSTIC_SERVICE:
                CarServiceUtils.assertAnyDiagnosticPermission(mContext);
                return mCarDiagnosticService;
            case Car.POWER_SERVICE:
                return mCarPowerManagementService;
            case Car.CABIN_SERVICE:
            case Car.HVAC_SERVICE:
            case Car.INFO_SERVICE:
            case Car.PROPERTY_SERVICE:
            case Car.SENSOR_SERVICE:
            case Car.VENDOR_EXTENSION_SERVICE:
                return mCarPropertyService;
            case Car.CAR_NAVIGATION_SERVICE:
                CarServiceUtils.assertNavigationManagerPermission(mContext);
                return mClusterNavigationService;
            case Car.CAR_INSTRUMENT_CLUSTER_SERVICE:
                CarServiceUtils.assertClusterManagerPermission(mContext);
                return mInstrumentClusterService.getManagerService();
            case Car.PROJECTION_SERVICE:
                return mCarProjectionService;
            case Car.VEHICLE_MAP_SERVICE:
                CarServiceUtils.assertAnyVmsPermission(mContext);
                return mVmsBrokerService;
            case Car.VMS_SUBSCRIBER_SERVICE:
                CarServiceUtils.assertVmsSubscriberPermission(mContext);
                return mVmsBrokerService;
            case Car.TEST_SERVICE: {
                CarServiceUtils.assertPermission(mContext, Car.PERMISSION_CAR_TEST_SERVICE);
                synchronized (mLock) {
                    if (mCarTestService == null) {
                        mCarTestService = new CarTestService(mContext, this);
                    }
                    return mCarTestService;
                }
            }
            case Car.STORAGE_MONITORING_SERVICE:
                CarServiceUtils.assertPermission(mContext, Car.PERMISSION_STORAGE_MONITORING);
                return mCarStorageMonitoringService;
            case Car.CAR_DRIVING_STATE_SERVICE:
                CarServiceUtils.assertDrivingStatePermission(mContext);
                return mCarDrivingStateService;
            case Car.CAR_UX_RESTRICTION_SERVICE:
                return mCarUXRestrictionsService;
            case Car.OCCUPANT_AWARENESS_SERVICE:
                return mOccupantAwarenessService;
            case Car.CAR_MEDIA_SERVICE:
                return mCarMediaService;
            case Car.CAR_OCCUPANT_ZONE_SERVICE:
                return mCarOccupantZoneService;
            case Car.CAR_BUGREPORT_SERVICE:
                return mCarBugreportManagerService;
            case Car.CAR_USER_SERVICE:
                return mCarUserService;
            case Car.EXPERIMENTAL_CAR_USER_SERVICE:
                return mExperimentalCarUserService;
            case Car.EXPERIMENTAL_CAR_KEYGUARD_SERVICE:
                return mExperimentalCarKeyguardService;
            case Car.CAR_WATCHDOG_SERVICE:
                return mCarWatchdogService;
            case Car.CAR_PERFORMANCE_SERVICE:
                return mCarPerformanceService;
            case Car.CAR_INPUT_SERVICE:
                return mCarInputService;
            case Car.CAR_DEVICE_POLICY_SERVICE:
                return mCarDevicePolicyService;
            case Car.CLUSTER_HOME_SERVICE:
                return mClusterHomeService;
            case Car.CAR_EVS_SERVICE:
                return mCarEvsService;
            case Car.CAR_TELEMETRY_SERVICE:
                return mCarTelemetryService;
            case Car.CAR_ACTIVITY_SERVICE:
                return mCarActivityService;
            case Car.CAR_OCCUPANT_CONNECTION_SERVICE:
                return mCarOccupantConnectionService;
            case Car.CAR_REMOTE_DEVICE_SERVICE:
                return mCarRemoteDeviceService;
            case Car.CAR_REMOTE_ACCESS_SERVICE:
                return mCarRemoteAccessService;
            default:
                // CarDisplayCompatManager does not need a new service but the Car class
                // doesn't allow a new Manager class without a service.
                if (Flags.displayCompatibility()) {
                    if (serviceName.equals(CAR_DISPLAY_COMPAT_SERVICE)) {
                        return mCarActivityService;
                    }
                }
                if (Flags.persistApSettings()) {
                    if (serviceName.equals(Car.CAR_WIFI_SERVICE)) {
                        return mCarWifiService;
                    }
                }
                IBinder service = null;
                if (mCarExperimentalFeatureServiceController != null) {
                    service = mCarExperimentalFeatureServiceController.getCarService(serviceName);
                }
                if (service == null) {
                    Slogf.w(CarLog.TAG_SERVICE, "getCarService for unknown service:"
                            + serviceName);
                }
                return service;
        }
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DEPRECATED_CODE)
    public int getCarConnectionType() {
        return Car.CONNECTION_TYPE_EMBEDDED;
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                != PackageManager.PERMISSION_GRANTED) {
            writer.println("Permission Denial: can't dump CarService from from pid="
                    + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid()
                    + " without permission " + android.Manifest.permission.DUMP);
            return;
        }

        try (IndentingPrintWriter pw = new IndentingPrintWriter(writer)) {
            dumpIndenting(fd, pw, args);
        }
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    private void dumpIndenting(FileDescriptor fd, IndentingPrintWriter writer, String[] args) {
        if (args == null || args.length == 0) {
            dumpAll(writer);
            return;
        }
        switch (args[0]) {
            case "-a":
                dumpAll(writer);
                return;
            case "--list":
                dumpListOfServices(writer);
                return;
            case "--version":
                dumpVersions(writer);
                return;
            case "--services": {
                int length = args.length;
                boolean dumpToProto = false;
                if (length < 2) {
                    writer.println("Must pass services to dump when using --services");
                    return;
                }
                if (Objects.equals(args[length - 1], "--proto")) {
                    length -= 2;
                    dumpToProto = true;
                    if (length > 1) {
                        writer.println("Cannot dump multiple services to proto");
                        return;
                    }
                } else {
                    length -= 1;
                }
                String[] services = new String[length];
                System.arraycopy(args, 1, services, 0, length);
                if (dumpToProto) {
                    if (!Flags.carDumpToProto()) {
                        writer.println("Cannot dump " + services[0]
                                + " to proto since FLAG_CAR_DUMP_TO_PROTO is disabled");
                        return;
                    }
                    dumpServiceProto(writer, fd, services[0]);
                } else {
                    dumpIndividualServices(writer, services);
                }
                return;
            }
            case "--metrics":
                // Strip the --metrics flag when passing dumpsys arguments to CarStatsService
                // allowing for nested flag selection.
                if (args.length == 1 || Arrays.asList(args).contains("--vms-client")) {
                    mCarStatsService.dump(writer);
                }
                return;
            case "--vms-hal":
                mHal.getVmsHal().dumpMetrics(fd);
                return;
            case "--hal": {
                if (args.length == 1) {
                    dumpAllHals(writer);
                    return;
                }
                int length = args.length - 1;
                String[] halNames = new String[length];
                System.arraycopy(args, 1, halNames, 0, length);
                mHal.dumpSpecificHals(writer, halNames);
                return;
            }
            case "--list-hals":
                mHal.dumpListHals(writer);
                return;
            case "--data-dir":
                dumpDataDir(writer);
                return;
            case "--help":
                showDumpHelp(writer);
                return;
            case "--rro":
                dumpRROs(writer);
                return;
            case "--oem-service":
                if (args.length > 1 && args[1].equalsIgnoreCase("--name-only")) {
                    writer.println(getOemServiceName());
                } else {
                    dumpOemService(writer);
                }
                return;
            default:
                execShellCmd(args, writer);
        }
    }

    private void dumpOemService(IndentingPrintWriter writer) {
        mCarOemService.dump(writer);
    }

    public String getOemServiceName() {
        return mCarOemService.getOemServiceName();
    }

    private void dumpAll(IndentingPrintWriter writer) {
        writer.println("*Dump car service*");
        dumpVersions(writer);
        dumpAllServices(writer);
        dumpAllHals(writer);
        dumpRROs(writer);
    }

    private void dumpRROs(IndentingPrintWriter writer) {
        writer.println("*Dump Car Service RROs*");

        String packageName = SystemProperties.get(
                PROPERTY_CAR_SERVICE_PACKAGE_NAME, /*def= */null);
        if (packageName == null) {
            writer.println("Car Service updatable package name is null.");
            return;
        }

        OverlayManager manager = mContext.getSystemService(OverlayManager.class);

        List<OverlayInfo> installedOverlaysForSystem = manager.getOverlayInfosForTarget(packageName,
                UserHandle.SYSTEM);
        writer.println("RROs for System User");
        for (int i = 0; i < installedOverlaysForSystem.size(); i++) {
            OverlayInfo overlayInfo = installedOverlaysForSystem.get(i);
            writer.printf("Overlay: %s, Enabled: %b \n", overlayInfo.getPackageName(),
                    overlayInfo.isEnabled());
        }

        int currentUser = ActivityManager.getCurrentUser();
        writer.printf("RROs for Current User: %d\n", currentUser);
        List<OverlayInfo> installedOverlaysForCurrentUser = manager.getOverlayInfosForTarget(
                packageName, UserHandle.of(currentUser));
        for (int i = 0; i < installedOverlaysForCurrentUser.size(); i++) {
            OverlayInfo overlayInfo = installedOverlaysForCurrentUser.get(i);
            writer.printf("Overlay: %s, Enabled: %b \n", overlayInfo.getPackageName(),
                    overlayInfo.isEnabled());
        }
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    private void dumpVersions(IndentingPrintWriter writer) {
        writer.println("*Dump versions*");
        writer.println("Android SDK_INT: " + Build.VERSION.SDK_INT);
        writer.println("Car Version: " + Car.getCarVersion());
        writer.println("Platform Version: " + Car.getPlatformVersion());
        writer.println("CarBuiltin Platform minor: " + CarBuiltin.PLATFORM_VERSION_MINOR_INT);
        writer.println("Legacy versions (might differ from above as they can't be emulated)");
        writer.increaseIndent();
        writer.println("Car API major: " + Car.API_VERSION_MAJOR_INT);
        writer.println("Car API minor: " + Car.API_VERSION_MINOR_INT);
        writer.println("Car Platform minor: " + Car.PLATFORM_VERSION_MINOR_INT);
        writer.println("VHAL and Car User Service Priority Init: " + mDoPriorityInitInConstruction);
        writer.decreaseIndent();
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    private void dumpAllHals(IndentingPrintWriter writer) {
        writer.println("*Dump Vehicle HAL*");
        writer.println("Vehicle HAL Interface: " + mVehicleInterfaceName);
        try {
            // TODO dump all feature flags by creating a dumpable interface
            mHal.dump(writer);
        } catch (Exception e) {
            writer.println("Failed dumping: " + mHal.getClass().getName());
            e.printStackTrace(writer);
        }
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    private void showDumpHelp(IndentingPrintWriter writer) {
        writer.println("Car service dump usage:");
        writer.println("[NO ARG]");
        writer.println("\t  dumps everything (all services and HALs)");
        writer.println("--help");
        writer.println("\t  shows this help");
        writer.println("--version");
        writer.println("\t  shows the version of all car components");
        writer.println("--list");
        writer.println("\t  lists the name of all services");
        writer.println("--list-hals");
        writer.println("\t  lists the name of all HALs");
        writer.println("--services <SVC1> [SVC2] [SVCN]");
        writer.println("\t  dumps just the specific services, where SVC is just the service class");
        writer.println("\t  name (like CarUserService)");
        writer.println("--vms-hal");
        writer.println("\t  dumps the VMS HAL metrics");
        writer.println("--hal [HAL1] [HAL2] [HALN]");
        writer.println("\t  dumps just the specified HALs (or all of them if none specified),");
        writer.println("\t  where HAL is just the class name (like UserHalService)");
        writer.println("--user-metrics");
        writer.println("\t  dumps user switching and stopping metrics");
        writer.println("--first-user-metrics");
        writer.println("\t  dumps how long it took to unlock first user since Android started\n");
        writer.println("\t  (or -1 if not unlocked)");
        writer.println("--data-dir");
        writer.println("\t  dumps CarService data dir (and whether it exists)");
        writer.println("--rro");
        writer.println("\t  dumps only the RROs");
        writer.println("-h");
        writer.println("\t  shows commands usage (NOTE: commands are not available on USER builds");
        writer.println("[ANYTHING ELSE]");
        writer.println("\t  runs the given command (use --h to see the available commands)");
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    private void dumpDataDir(IndentingPrintWriter writer) {
        File dataDir = mContext.getDataDir();
        writer.printf("Data dir: %s Exists: %b\n", dataDir.getAbsolutePath(), dataDir.exists());
    }

    @Override
    public boolean onTransact(int code, @NonNull Parcel data, @Nullable Parcel reply,
            int flags) throws RemoteException {
        // Shell cmd is handled specially.
        if (BinderHelper.onTransactForCmd(code, data, reply, flags, mCmdListener)) {
            return true;
        }
        return super.onTransact(code, data, reply, flags);
    }

    private CarShellCommand newCarShellCommand() {
        Map<Class, CarSystemService> allServicesByClazz = new ArrayMap<>();
        for (CarSystemService service : mAllServicesInInitOrder) {
            allServicesByClazz.put(service.getClass(), service);
        }

        return new CarShellCommand(mContext, mHal, mFeatureController, mSystemInterface,
                allServicesByClazz);
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    private void dumpListOfServices(IndentingPrintWriter writer) {
        for (CarSystemService service : mAllServicesInInitOrder) {
            writer.println(service.getClass().getName());
        }
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    private void dumpAllServices(IndentingPrintWriter writer) {
        writer.println("*Dump all services*");
        for (CarSystemService service : mAllServicesInInitOrder) {
            if (service instanceof CarServiceBase) {
                dumpService(service, writer);
            }
        }
        synchronized (mLock) {
            if (mCarTestService != null) {
                dumpService(mCarTestService, writer);
            }
        }
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    private void dumpIndividualServices(IndentingPrintWriter writer, String... serviceNames) {
        for (String serviceName : serviceNames) {
            writer.printf("** Dumping %s\n\n", serviceName);
            CarSystemService service = getCarServiceBySubstring(serviceName);
            if (service == null) {
                writer.println("No such service!");
            } else {
                dumpService(service, writer);
            }
            writer.println();
        }
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    private void dumpServiceProto(IndentingPrintWriter writer, FileDescriptor fd,
            String serviceName) {
        CarSystemService service = getCarServiceBySubstring(serviceName);
        if (service == null) {
            writer.println("No such service!");
        } else {
            if (service instanceof CarServiceBase) {
                CarServiceBase carService = (CarServiceBase) service;
                try (FileOutputStream fileStream = new FileOutputStream(fd)) {
                    ProtoOutputStream proto = new ProtoOutputStream(fileStream);
                    carService.dumpProto(proto);
                    proto.flush();
                } catch (Exception e) {
                    writer.println("Failed dumping: " + carService.getClass().getName());
                    e.printStackTrace(writer);
                }
            } else {
                writer.println("Only services that extend CarServiceBase can dump to proto");
            }
        }
    }

    @Nullable
    private CarSystemService getCarServiceBySubstring(String className) {
        return Arrays.asList(mAllServicesInInitOrder).stream()
                .filter(s -> s.getClass().getSimpleName().equals(className))
                .findFirst().orElse(null);
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    private void dumpService(CarSystemService service, IndentingPrintWriter writer) {
        try {
            service.dump(writer);
        } catch (Exception e) {
            writer.println("Failed dumping: " + service.getClass().getName());
            e.printStackTrace(writer);
        }
    }

    void execShellCmd(String[] args, IndentingPrintWriter writer) {
        newCarShellCommand().exec(args, writer);
    }

    private <T extends CarSystemService> T constructWithTrace(LimitedTimingsTraceLog t,
            Class<T> cls, Callable<T> callable, List<CarSystemService> allServices) {
        t.traceBegin(cls.getSimpleName());
        T constructed;
        try {
            constructed = callable.call();
            CarLocalServices.addService(cls, constructed);
        } catch (Exception e) {
            throw new RuntimeException("Crash while constructing:" + cls.getSimpleName(), e);
        } finally {
            t.traceEnd();
        }
        allServices.add(constructed);
        return constructed;
    }

    private final class ICarSystemServerClientImpl extends ICarSystemServerClient.Stub {
        @Override
        public void onUserLifecycleEvent(int eventType, int fromUserId, int toUserId)
                throws RemoteException {
            assertCallingFromSystemProcess();
            EventLogHelper.writeCarServiceOnUserLifecycle(eventType, fromUserId, toUserId);
            if (DBG) {
                Slogf.d(TAG,
                        "onUserLifecycleEvent("
                                + CarUserManager.lifecycleEventTypeToString(eventType) + ", "
                                + toUserId + ")");
            }
            mCarUserService.onUserLifecycleEvent(eventType, fromUserId, toUserId);
        }

        @Override
        public void initBootUser() throws RemoteException {
            // TODO(b/277271542). Remove this code path.
        }

        // TODO(235524989): Remove this method as on user removed will now go through
        // onUserLifecycleEvent due to changes in CarServiceProxy and CarUserService.
        @Override
        public void onUserRemoved(UserHandle user) throws RemoteException {
            assertCallingFromSystemProcess();
            EventLogHelper.writeCarServiceOnUserRemoved(user.getIdentifier());
            if (DBG) Slogf.d(TAG, "onUserRemoved(): " + user);
            mCarUserService.onUserRemoved(user);
        }

        @Override
        public void onFactoryReset(ICarResultReceiver callback) {
            assertCallingFromSystemProcess();

            mCarPowerManagementService.setFactoryResetCallback(callback);
            BuiltinPackageDependency.createNotificationHelper(mCarServiceBuiltinPackageContext)
                    .showFactoryResetNotification(callback);
        }

        @Override
        public void setInitialUser(UserHandle user) {
            mCarUserService.setInitialUserFromSystemServer(user);
        }
    }

    /* package */ void dumpVhal(ParcelFileDescriptor fd, List<String> options)
            throws RemoteException {
        mHal.dumpVhal(fd, options);
    }

    /* package */ boolean hasAidlVhal() {
        return mHal.isAidlVhal();
    }

    /* package */ void priorityInit() {
        mHal.priorityInit();
        mCarUserService.priorityInit();
    }

    public static final class Builder {
        Context mContext;
        Context mCarServiceBuiltinPackageContext;
        VehicleStub mVehicle;
        SystemInterface mSystemInterface;
        String mVehicleInterfaceName;
        CarUserService mCarUserService;
        CarWatchdogService mCarWatchdogService;
        CarPerformanceService mCarPerformanceService;
        GarageModeService mGarageModeService;
        IInterface mPowerPolicyDaemon;
        CarTelemetryService mCarTelemetryService;
        CarRemoteAccessService mCarRemoteAccessService;
        boolean mDoPriorityInitInConstruction;

        /**
         * Builds the ICarImpl object represented by this builder object
         * @return ICarImpl object
         */
        public ICarImpl build() {
            return new ICarImpl(this);
        }

        /**
         * Sets ICarImpl builder service context
         * @param serviceContext The service context
         * @return Current builder object
         */
        public Builder setServiceContext(Context serviceContext) {
            mContext = serviceContext;
            return this;
        }

        /**
         * Sets ICarImpl builder built in context
         * @param builtInContext The car service built in package context
         * @return Current builder object
         */
        public Builder setBuiltInContext(Context builtInContext) {
            mCarServiceBuiltinPackageContext = builtInContext;
            return this;
        }

        /**
         * Sets ICarImpl builder vehicle
         * @param vehicle The vehicle stub object to use
         * @return Current builder object
         */
        public Builder setVehicle(VehicleStub vehicle) {
            mVehicle = vehicle;
            return this;
        }

        /**
         * Sets ICarImpl builder system interface
         * @param systemInterface The system interface object
         * @return Current builder object
         */
        public Builder setSystemInterface(SystemInterface systemInterface) {
            mSystemInterface = systemInterface;
            return this;
        }

        /**
         * Sets ICarImpl builder vehicle interface name
         * @param vehicleInterfaceName The vehicle interface name
         * @return Current builder object
         */
        public Builder setVehicleInterfaceName(String vehicleInterfaceName) {
            mVehicleInterfaceName = vehicleInterfaceName;
            return this;
        }

        /**
         * Sets ICarImpl builder car user service
         * @param carUserService The car user service
         * @return Current builder object
         */
        public Builder setCarUserService(CarUserService carUserService) {
            mCarUserService = carUserService;
            return this;
        }

        /**
         * Sets ICarImpl builder car watchdog service
         * @param carWatchdogService The car watchdog service
         * @return Current builder object
         */
        public Builder setCarWatchdogService(CarWatchdogService carWatchdogService) {
            mCarWatchdogService = carWatchdogService;
            return this;
        }

        /**
         * Sets ICarImpl builder car performance service
         * @param carPerformanceService The car performance service
         * @return Current builder object
         */
        public Builder setCarPerformanceService(CarPerformanceService carPerformanceService) {
            mCarPerformanceService = carPerformanceService;
            return this;
        }

        /**
         * Sets ICarImpl builder garage mode service
         * @param garageModeService The garage mode service
         * @return Current builder object
         */
        public Builder setGarageModeService(GarageModeService garageModeService) {
            mGarageModeService = garageModeService;
            return this;
        }

        /**
         * Sets ICarImpl power policy daemon
         * @param powerPolicyDaemon The power policy daemon interface
         * @return Current builder object
         */
        public Builder setPowerPolicyDaemon(IInterface powerPolicyDaemon) {
            mPowerPolicyDaemon = powerPolicyDaemon;
            return this;
        }

        /**
         * Sets ICarImpl car telemetry service
         * @param carTelemetryService The car telemetry service
         * @return Current builder object
         */
        public Builder setCarTelemetryService(CarTelemetryService carTelemetryService) {
            mCarTelemetryService = carTelemetryService;
            return this;
        }

        /**
         * Set ICarImpl car remote access service
         * @param carRemoteAccessService The car remote access service
         * @return Current builder object
         */
        public Builder setCarRemoteAccessService(CarRemoteAccessService carRemoteAccessService) {
            mCarRemoteAccessService = carRemoteAccessService;
            return this;
        }

        /**
         * Sets whether ICarImpl builder will make an ICarImpl object that does priority
         * initialization in construction
         * @param doPriorityInitInConstruction Whether to do priority initialization in construction
         *                                     of the ICarImpl object this builder represents
         * @return Current builder object
         */
        public Builder setDoPriorityInitInConstruction(boolean doPriorityInitInConstruction) {
            mDoPriorityInitInConstruction = doPriorityInitInConstruction;
            return this;
        }
    }
}

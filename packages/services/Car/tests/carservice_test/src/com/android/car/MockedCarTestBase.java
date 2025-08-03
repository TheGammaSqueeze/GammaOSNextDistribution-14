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

import static android.content.pm.PackageManager.PERMISSION_DENIED;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import android.automotive.powerpolicy.internal.ICarPowerPolicyDelegate;
import android.car.Car;
import android.car.feature.Flags;
import android.car.test.CarTestManager;
import android.car.testapi.FakeRefactoredCarPowerPolicyDaemon;
import android.car.user.CarUserManager.UserLifecycleListener;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.frameworks.automotive.powerpolicy.internal.ICarPowerPolicySystemNotification;
import android.hardware.automotive.vehicle.VehiclePropertyAccess;
import android.hardware.automotive.vehicle.VehiclePropertyChangeMode;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.IInterface;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;

import androidx.test.annotation.UiThreadTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.car.garagemode.GarageModeService;
import com.android.car.hal.test.AidlMockedVehicleHal;
import com.android.car.hal.test.AidlVehiclePropConfigBuilder;
import com.android.car.hal.test.HidlMockedVehicleHal;
import com.android.car.hal.test.HidlVehiclePropConfigBuilder;
import com.android.car.internal.ICarServiceHelper;
import com.android.car.os.CarPerformanceService;
import com.android.car.power.CarPowerManagementService;
import com.android.car.remoteaccess.CarRemoteAccessService;
import com.android.car.systeminterface.ActivityManagerInterface;
import com.android.car.systeminterface.DisplayInterface;
import com.android.car.systeminterface.IOInterface;
import com.android.car.systeminterface.StorageMonitoringInterface;
import com.android.car.systeminterface.SystemInterface;
import com.android.car.systeminterface.SystemStateInterface;
import com.android.car.systeminterface.TimeInterface;
import com.android.car.systeminterface.WakeLockInterface;
import com.android.car.telemetry.CarTelemetryService;
import com.android.car.test.utils.TemporaryDirectory;
import com.android.car.user.CarUserService;
import com.android.car.watchdog.CarWatchdogService;
import com.android.internal.annotations.GuardedBy;

import org.junit.After;
import org.junit.Before;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Base class for testing with mocked vehicle HAL (=car).
 * It is up to each app to start emulation by getHidlMockedVehicleHal().start() as there will be
 * per test set up that should be done before starting.
 */
public class MockedCarTestBase {
    protected static final long DEFAULT_WAIT_TIMEOUT_MS = 3000;
    protected static final long SHORT_WAIT_TIMEOUT_MS = 500;
    private static final int STATE_HANDLING_TIMEOUT = 5_000;
    private static final int MAIN_LOOPER_TIMEOUT_MS = 1_000;
    private static final String TAG = MockedCarTestBase.class.getSimpleName();
    private static final IBinder sCarServiceToken = new Binder();
    private static boolean sRealCarServiceReleased;

    // Use the Mocked AIDL VHAL backend by default.
    private boolean mUseAidlVhal = true;

    private Car mCar;
    private ICarImpl mCarImpl;
    private HidlMockedVehicleHal mHidlMockedVehicleHal;
    private AidlMockedVehicleHal mAidlMockedVehicleHal;
    private SystemInterface mFakeSystemInterface;
    private MockedCarTestContext mMockedCarTestContext;
    private CarTelemetryService mCarTelemetryService;
    private CarWatchdogService mCarWatchdogService = mock(CarWatchdogService.class);
    private CarPerformanceService mCarPerformanceService;
    private CarRemoteAccessService mCarRemoteAccessService;

    private final CarUserService mCarUserService = mock(CarUserService.class);
    private final MockIOInterface mMockIOInterface = new MockIOInterface();
    private final GarageModeService mGarageModeService = mock(GarageModeService.class);
    // TODO(286303350): Replace mPowerPolicyDaemon with mRefactoredPowerPolicyDaemon
    //                  once refactor complete
    private final ICarPowerPolicySystemNotification.Stub mPowerPolicyDaemon =
            mock(ICarPowerPolicySystemNotification.Stub.class);
    private final ICarServiceHelper mICarServiceHelper = mock(ICarServiceHelper.class);

    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private final SparseArray<HidlVehiclePropConfigBuilder> mHidlPropToConfigBuilder =
            new SparseArray<>();
    @GuardedBy("mLock")
    private final SparseArray<AidlVehiclePropConfigBuilder> mAidlPropToConfigBuilder =
            new SparseArray<>();
    @GuardedBy("mLock")
    private final Map<HidlVehiclePropConfigBuilder, HidlMockedVehicleHal.VehicleHalPropertyHandler>
            mHidlHalConfig = new ArrayMap<>();
    @GuardedBy("mLock")
    private final Map<AidlVehiclePropConfigBuilder, AidlMockedVehicleHal.VehicleHalPropertyHandler>
            mAidlHalConfig = new ArrayMap<>();
    @GuardedBy("mLock")
    private final List<UserLifecycleListener> mUserLifecycleListeners = new ArrayList<>();

    private ICarPowerPolicyDelegate mRefactoredPowerPolicyDaemon;
    private MockitoSession mSession;

    protected HidlMockedVehicleHal createHidlMockedVehicleHal() {
        return new HidlMockedVehicleHal();
    }

    protected AidlMockedVehicleHal createAidlMockedVehicleHal() {
        return new AidlMockedVehicleHal();
    }

    protected HidlMockedVehicleHal getHidlMockedVehicleHal() {
        return mHidlMockedVehicleHal;
    }

    protected AidlMockedVehicleHal getAidlMockedVehicleHal() {
        return mAidlMockedVehicleHal;
    }

    protected SystemInterface getFakeSystemInterface() {
        return mFakeSystemInterface;
    }

    protected android.os.IInterface getMockedPowerPolicyDaemon() {
        if (Flags.carPowerPolicyRefactoring()) {
            return mRefactoredPowerPolicyDaemon;
        } else {
            return mPowerPolicyDaemon;
        }
    }

    protected void configureMockedHal() {
    }

    protected CarTelemetryService createCarTelemetryService() {
        return mock(CarTelemetryService.class);
    }

    /**
     * Use the Mocked HIDL Vehicle HAL as backend. If called, must be called in
     * configureMockedHal().
     */
    protected void useHidlVhal() {
        mUseAidlVhal = false;
    }

    /**
     * Use the Mocked AIDL Vehicle HAL as backend. If called, must be called in
     * configureMockedHal().
     */
    protected void useAidlVhal() {
        mUseAidlVhal = true;
    }

    /**
     * Set the CarWatchDogService to be used during the test.
     */
    protected void setCarWatchDogService(CarWatchdogService service) {
        mCarWatchdogService = service;
    }

    /**
     * Set the CarPerformanceService to be used during the test.
     *
     * Must be called during {@link configureMockedHal}. If not called, the real service would be
     * used.
     */
    protected void setCarPerformanceService(CarPerformanceService service) {
        mCarPerformanceService = service;
    }

    /**
     * Set the CarRemoteAccessService to be used during the test.
     *
     * If not called, the real service would be used.
     */
    protected void setCarRemoteAccessService(CarRemoteAccessService service) {
        mCarRemoteAccessService = service;
    }

    /**
     * Called after {@code ICarImpl} is created and before {@code ICarImpl.init()} is called.
     *
     * <p> Subclass that intend to apply spyOn() to the service under testing should override this.
     * <pre class="prettyprint">
     * {@literal @}Override
     * protected void spyOnBeforeCarImplInit() {
     *     mServiceUnderTest = CarLocalServices.getService(CarXXXService.class);
     *     ExtendedMockito.spyOn(mServiceUnderTest);
     * }
     * </pre>
     */
    protected void spyOnBeforeCarImplInit(ICarImpl carImpl) {
    }

    protected SystemInterface.Builder getSystemInterfaceBuilder() {
        return SystemInterface.Builder.newSystemInterface()
                .withSystemStateInterface(createMockSystemStateInterface())
                .withActivityManagerInterface(new MockActivityManagerInterface())
                .withDisplayInterface(new MockDisplayInterface())
                .withIOInterface(mMockIOInterface)
                .withStorageMonitoringInterface(new MockStorageMonitoringInterface())
                .withTimeInterface(new MockTimeInterface())
                .withWakeLockInterface(new MockWakeLockInterface());
    }

    protected SystemStateInterface createMockSystemStateInterface() {
        return new MockSystemStateInterface();
    }

    protected void configureFakeSystemInterface() {}

    protected void configureResourceOverrides(MockResources resources) {
        resources.overrideResource(com.android.car.R.string.instrumentClusterRendererService, "");
        resources.overrideResource(com.android.car.R.bool.audioUseDynamicRouting, false);
        resources.overrideResource(com.android.car.R.array.config_earlyStartupServices,
                new String[0]);
        resources.overrideResource(com.android.car.R.integer.maxGarageModeRunningDurationInSecs,
                900);
    }

    protected Context getContext() {
        synchronized (mLock) {
            if (mMockedCarTestContext == null) {
                mMockedCarTestContext = createMockedCarTestContext(
                        InstrumentationRegistry.getInstrumentation().getTargetContext());
            }
            return mMockedCarTestContext;
        }
    }

    protected MockedCarTestContext createMockedCarTestContext(Context context) {
        return new MockedCarTestContext(context);
    }

    protected Context getTestContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }

    protected String getFlattenComponent(Class cls) {
        ComponentName cn = new ComponentName(getTestContext(), cls);
        return cn.flattenToString();
    }

    /** Child class should override this to configure mocking in different way */
    protected MockitoSession createMockingSession() {
        return mockitoSession()
                .initMocks(this)
                .spyStatic(ICarImpl.class)
                .strictness(Strictness.LENIENT)
                .startMocking();
    }

    @Before
    @UiThreadTest
    public void setUp() throws Exception {
        Log.i(TAG, "setUp");

        mSession = createMockingSession();

        releaseRealCarService(getContext());

        // Create mock dependencies
        mHidlMockedVehicleHal = createHidlMockedVehicleHal();
        mAidlMockedVehicleHal = createAidlMockedVehicleHal();
        configureMockedHal();

        mFakeSystemInterface = getSystemInterfaceBuilder().build();
        configureFakeSystemInterface();

        mMockedCarTestContext = (MockedCarTestContext) getContext();
        configureResourceOverrides((MockResources) mMockedCarTestContext.getResources());

        mCarTelemetryService = createCarTelemetryService();

        // Setup mocks
        doAnswer((invocation) -> {
            UserLifecycleListener listener = invocation.getArgument(/* index= */ 1);
            synchronized (mLock) {
                Log.d(TAG, "Adding UserLifecycleListener: " + listener);
                mUserLifecycleListeners.add(listener);
            }
            return null;
        }).when(mCarUserService).addUserLifecycleListener(any(), any());

        doAnswer((invocation) -> {
            UserLifecycleListener listener = invocation.getArgument(/* index= */ 0);
            synchronized (mLock) {
                Log.d(TAG, "Removing UserLifecycleListener: " + listener);
                mUserLifecycleListeners.remove(listener);
            }
            return null;
        }).when(mCarUserService).removeUserLifecycleListener(any());

        // ICarImpl will register new CarLocalServices services.
        // This prevents one test failure in tearDown from triggering assertion failure for single
        // CarLocalServices service.
        CarLocalServices.removeAllServices();

        // This should be done here as feature property is accessed inside the constructor.
        initMockedHal();

        VehicleStub mockedVehicleStub;
        if (!mUseAidlVhal) {
            mockedVehicleStub = new HidlVehicleStub(mHidlMockedVehicleHal);
        } else {
            mockedVehicleStub = new AidlVehicleStub(mAidlMockedVehicleHal);
        }

        // Setup car
        IInterface powerPolicyDaemon;
        if (Flags.carPowerPolicyRefactoring()) {
            mRefactoredPowerPolicyDaemon = new FakeRefactoredCarPowerPolicyDaemon(
                    /* fileKernelSilentMode= */ null, /* customComponents= */ null);
            powerPolicyDaemon = mRefactoredPowerPolicyDaemon;
        } else {
            powerPolicyDaemon = mPowerPolicyDaemon;
        }
        ICarImpl carImpl = new ICarImpl.Builder()
                .setServiceContext(mMockedCarTestContext)
                .setVehicle(mockedVehicleStub)
                .setVehicleInterfaceName("MockedCar")
                .setSystemInterface(mFakeSystemInterface)
                .setCarUserService(mCarUserService)
                .setCarWatchdogService(mCarWatchdogService)
                .setCarPerformanceService(mCarPerformanceService)
                .setCarTelemetryService(mCarTelemetryService)
                .setCarRemoteAccessService(mCarRemoteAccessService)
                .setGarageModeService(mGarageModeService)
                .setPowerPolicyDaemon(powerPolicyDaemon)
                .setDoPriorityInitInConstruction(false)
                .build();

        doNothing().when(() -> ICarImpl.assertCallingFromSystemProcess());
        carImpl.setSystemServerConnections(mICarServiceHelper,
                new ICarImplTest.CarServiceConnectedCallback());
        spyOnBeforeCarImplInit(carImpl);
        carImpl.init();
        mCarImpl = carImpl;
        // Wait for CPMS to handle the first power state change request.
        waitUntilPowerStateChangeHandled();
        mCar = new Car(mMockedCarTestContext, mCarImpl, /* handler= */ null);
    }

    @After
    public void tearDown() throws Exception {
        Log.i(TAG, "tearDown");

        // Wait for CPMS to finish event processing.
        if (mCarImpl != null) {
            waitUntilPowerStateChangeHandled();
        }

        try {
            if (mCar != null) {
                mCar.disconnect();
                mCar = null;
            }
            if (mCarImpl != null) {
                mCarImpl.release();
                mCarImpl = null;
            }
            CarServiceUtils.finishAllHandlerTasks();
            mMockIOInterface.tearDown();
            mHidlMockedVehicleHal = null;
            mAidlMockedVehicleHal = null;
        } finally {
            // Wait for the main looper to handle the current queued tasks before finishing the
            // mocking session since the task might use the mocked object.
            assertWithMessage("main looper not idle in %sms", MAIN_LOOPER_TIMEOUT_MS)
                    .that(Handler.getMain().runWithScissors(() -> {
                        if (mSession != null) {
                            mSession.finishMocking();
                        }
                    }, MAIN_LOOPER_TIMEOUT_MS)).isTrue();
        }
    }

    public void injectErrorEvent(int propId, int areaId, int errorCode) {
        if (mUseAidlVhal) {
            mAidlMockedVehicleHal.injectError(errorCode, propId, areaId);
        } else {
            mHidlMockedVehicleHal.injectError(errorCode, propId, areaId);
        }
    }

    /**
     * Creates new Car instance for testing.
     */
    public Car createNewCar() {
        return new Car(mMockedCarTestContext, mCarImpl, /* handler= */ null);
    }

    protected IBinder getCarService(String service) {
        return mCarImpl.getCarService(service);
    }

    private void initMockedHal() throws Exception {
        synchronized (mLock) {
            for (Map.Entry<HidlVehiclePropConfigBuilder,
                    HidlMockedVehicleHal.VehicleHalPropertyHandler> entry :
                    mHidlHalConfig.entrySet()) {
                mHidlMockedVehicleHal.addProperty(entry.getKey().build(), entry.getValue());
            }
            for (Map.Entry<AidlVehiclePropConfigBuilder,
                    AidlMockedVehicleHal.VehicleHalPropertyHandler>
                    entry : mAidlHalConfig.entrySet()) {
                mAidlMockedVehicleHal.addProperty(entry.getKey().build(), entry.getValue());
            }
            mHidlHalConfig.clear();
            mAidlHalConfig.clear();
        }
    }

    protected HidlVehiclePropConfigBuilder addHidlProperty(int propertyId,
            HidlMockedVehicleHal.VehicleHalPropertyHandler propertyHandler) {
        HidlVehiclePropConfigBuilder builder = HidlVehiclePropConfigBuilder.newBuilder(propertyId);
        setHidlConfigBuilder(builder, propertyHandler);
        return builder;
    }

    protected HidlVehiclePropConfigBuilder addHidlProperty(int propertyId) {
        HidlVehiclePropConfigBuilder builder = HidlVehiclePropConfigBuilder.newBuilder(propertyId);
        setHidlConfigBuilder(builder, new HidlMockedVehicleHal.DefaultPropertyHandler(
                builder.build(), null));
        return builder;
    }

    protected HidlVehiclePropConfigBuilder addHidlProperty(int propertyId,
            android.hardware.automotive.vehicle.V2_0.VehiclePropValue value) {
        HidlVehiclePropConfigBuilder builder = HidlVehiclePropConfigBuilder.newBuilder(propertyId);
        setHidlConfigBuilder(builder, new HidlMockedVehicleHal.DefaultPropertyHandler(
                builder.build(), value));
        return builder;
    }

    protected HidlVehiclePropConfigBuilder addStaticHidlProperty(int propertyId,
            android.hardware.automotive.vehicle.V2_0.VehiclePropValue value) {
        HidlVehiclePropConfigBuilder builder = HidlVehiclePropConfigBuilder.newBuilder(propertyId)
                .setChangeMode(VehiclePropertyChangeMode.STATIC)
                .setAccess(VehiclePropertyAccess.READ);

        setHidlConfigBuilder(builder, new HidlMockedVehicleHal.StaticPropertyHandler(value));
        return builder;
    }

    protected AidlVehiclePropConfigBuilder addAidlProperty(int propertyId,
            AidlMockedVehicleHal.VehicleHalPropertyHandler propertyHandler) {
        AidlVehiclePropConfigBuilder builder = AidlVehiclePropConfigBuilder.newBuilder(propertyId);
        setAidlConfigBuilder(builder, propertyHandler);
        return builder;
    }

    protected AidlVehiclePropConfigBuilder addAidlProperty(int propertyId) {
        AidlVehiclePropConfigBuilder builder = AidlVehiclePropConfigBuilder.newBuilder(propertyId);
        setAidlConfigBuilder(builder, new AidlMockedVehicleHal.DefaultPropertyHandler(
                builder.build(), null));
        return builder;
    }

    protected AidlVehiclePropConfigBuilder addAidlProperty(int propertyId,
            android.hardware.automotive.vehicle.VehiclePropValue value) {
        AidlVehiclePropConfigBuilder builder = AidlVehiclePropConfigBuilder.newBuilder(propertyId);
        setAidlConfigBuilder(builder, new AidlMockedVehicleHal.DefaultPropertyHandler(
                builder.build(), value));
        return builder;
    }

    protected AidlVehiclePropConfigBuilder addAidlStaticProperty(int propertyId,
            android.hardware.automotive.vehicle.VehiclePropValue value) {
        AidlVehiclePropConfigBuilder builder = AidlVehiclePropConfigBuilder.newBuilder(propertyId)
                .setChangeMode(VehiclePropertyChangeMode.STATIC)
                .setAccess(VehiclePropertyAccess.READ);

        setAidlConfigBuilder(builder, new AidlMockedVehicleHal.StaticPropertyHandler(
                value));
        return builder;
    }

    private void waitUntilPowerStateChangeHandled() {
        CarPowerManagementService cpms =
                (CarPowerManagementService) getCarService(Car.POWER_SERVICE);
        cpms.getHandler().runWithScissors(() -> {}, STATE_HANDLING_TIMEOUT);
    }

    @SuppressWarnings("CollectionIncompatibleType") // HidlVehiclePropConfigBuilder does not
                                                    // implement equals
    private void setHidlConfigBuilder(HidlVehiclePropConfigBuilder builder,
            HidlMockedVehicleHal.VehicleHalPropertyHandler propertyHandler) {
        int propId = builder.build().prop;

        synchronized (mLock) {
            // Override previous property config if exists.
            HidlVehiclePropConfigBuilder prevBuilder = mHidlPropToConfigBuilder.get(propId);
            if (prevBuilder != null) {
                mHidlHalConfig.remove(prevBuilder);
            }
            mHidlPropToConfigBuilder.put(propId, builder);
            mHidlHalConfig.put(builder, propertyHandler);
        }
    }

    @SuppressWarnings("CollectionIncompatibleType") // AidlVehiclePropConfigBuilder does not
                                                    // implement equals
    private void setAidlConfigBuilder(AidlVehiclePropConfigBuilder builder,
            AidlMockedVehicleHal.VehicleHalPropertyHandler propertyHandler) {
        int propId = builder.build().prop;

        synchronized (mLock) {
            // Override previous property config if exists.
            AidlVehiclePropConfigBuilder prevBuilder = mAidlPropToConfigBuilder.get(propId);
            if (prevBuilder != null) {
                mAidlHalConfig.remove(prevBuilder);
            }
            mAidlPropToConfigBuilder.put(propId, builder);
            mAidlHalConfig.put(builder, propertyHandler);
        }
    }

    protected android.car.Car getCar() {
        return mCar;
    }

    /*
     * In order to eliminate interfering with real car service we will disable it. It will be
     * enabled back in CarTestService when sCarServiceToken will go away (tests finish).
     */
    private static void releaseRealCarService(Context context) throws Exception {
        if (sRealCarServiceReleased) {
            return;  // We just want to release it once.
        }
        sRealCarServiceReleased = true;  // To make sure it was called once.

        Object waitForConnection = new Object();
        Car car = android.car.Car.createCar(context, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                synchronized (waitForConnection) {
                    waitForConnection.notifyAll();
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) { }
        });

        car.connect();
        synchronized (waitForConnection) {
            if (!car.isConnected()) {
                long nowMs = System.currentTimeMillis();
                long deadlineMs = nowMs + DEFAULT_WAIT_TIMEOUT_MS;
                while (!car.isConnected() && nowMs < deadlineMs) {
                    waitForConnection.wait(deadlineMs - nowMs);
                    nowMs = System.currentTimeMillis();
                }
            }
        }

        if (car.isConnected()) {
            Log.i(TAG, "Connected to real car service");
            CarTestManager carTestManager = (CarTestManager) car.getCarManager(Car.TEST_SERVICE);
            carTestManager.stopCarService(sCarServiceToken);
        }
    }

    static final class MockActivityManagerInterface implements ActivityManagerInterface {
        @Override
        public void sendBroadcastAsUser(Intent intent, UserHandle user) {
            Log.d(TAG, "Broadcast intent: " + intent.getAction() + " as user: " + user);
        }
    }

    static final class MockDisplayInterface implements DisplayInterface {

        @Override
        public void init(CarPowerManagementService carPowerManagementService,
                CarUserService carUserService) {}

        @Override
        public void setDisplayBrightness(int brightness) {}

        @Override
        public void setDisplayBrightness(int displayId, int brightness) {}

        @Override
        public void setDisplayState(int displayId, boolean on) {}

        @Override
        public void setAllDisplayState(boolean on) {}

        @Override
        public void startDisplayStateMonitoring() {}

        @Override
        public void stopDisplayStateMonitoring() {}

        @Override
        public void refreshDisplayBrightness() {}

        @Override
        public void refreshDisplayBrightness(int displayid) {}

        @Override
        public boolean isAnyDisplayEnabled() {
            return true;
        }

        @Override
        public boolean isDisplayEnabled(int displayId) {
            return true;
        }
    }

    static final class MockIOInterface implements IOInterface {
        private TemporaryDirectory mFilesDir = null;

        @Override
        public File getSystemCarDir() {
            if (mFilesDir == null) {
                try {
                    mFilesDir = new TemporaryDirectory(TAG);
                } catch (IOException e) {
                    Log.e(TAG, "failed to create temporary directory", e);
                    fail("failed to create temporary directory. exception was: " + e);
                }
            }
            return mFilesDir.getDirectory();
        }

        public void tearDown() {
            if (mFilesDir != null) {
                try {
                    mFilesDir.close();
                } catch (Exception e) {
                    Log.w(TAG, "could not remove temporary directory", e);
                }
            }
        }
    }

    /**
     * Special version of {@link ContextWrapper} that overrides {@code getResources} by returning
     * a {@link MockResources}, so tests are free to set resources. This class represents an
     * alternative of using Mockito spy (see b/148240178).
     *
     * This class also overwrites {@code checkCallingOrSelfPermission} to allow setting caller's
     * permissions. By default, caller is typically within the same process so permission will
     * always be granted. Caller can use {@code setDeniedPermissions} to set permissions
     * that should not be granted.
     *
     * Tests may specialize this class. If they decide so, then they are required to override
     * {@link createMockedCarTestContext} to provide their own context.
     */
    protected static class MockedCarTestContext extends ContextWrapper {

        private final Resources mMockedResources;
        private final Set<String> mDeniedPermissions = new ArraySet<>();

        protected MockedCarTestContext(Context base) {
            super(base);
            mMockedResources = new MockResources(base.getResources());
        }

        /**
         * Sets the permissions that should be denied.
         */
        public void setDeniedPermissions(String[] permissions) {
            for (String permission : permissions) {
                mDeniedPermissions.add(permission);
            }
        }

        @Override
        public Resources getResources() {
            return mMockedResources;
        }

        @Override
        public int checkCallingOrSelfPermission(String permission) {
            if (mDeniedPermissions.contains(permission)) {
                return PERMISSION_DENIED;
            }
            return super.checkCallingOrSelfPermission(permission);
        }
    }

    protected static final class MockResources extends Resources {
        private final HashMap<Integer, Boolean> mBooleanOverrides = new HashMap<>();
        private final HashMap<Integer, Integer> mIntegerOverrides = new HashMap<>();
        private final HashMap<Integer, String> mStringOverrides = new HashMap<>();
        private final HashMap<Integer, String[]> mStringArrayOverrides = new HashMap<>();

        MockResources(Resources resources) {
            super(resources.getAssets(),
                    resources.getDisplayMetrics(),
                    resources.getConfiguration());
        }

        @Override
        public boolean getBoolean(int id) {
            return mBooleanOverrides.getOrDefault(id,
                    super.getBoolean(id));
        }

        @Override
        public int getInteger(int id) {
            return mIntegerOverrides.getOrDefault(id,
                    super.getInteger(id));
        }

        @Override
        public String getString(int id) {
            return mStringOverrides.getOrDefault(id,
                    super.getString(id));
        }

        @Override
        public String[] getStringArray(int id) {
            return mStringArrayOverrides.getOrDefault(id,
                    super.getStringArray(id));
        }

        public MockResources overrideResource(int id, boolean value) {
            mBooleanOverrides.put(id, value);
            return this;
        }

        public MockResources overrideResource(int id, int value) {
            mIntegerOverrides.put(id, value);
            return this;
        }

        public MockResources overrideResource(int id, String value) {
            mStringOverrides.put(id, value);
            return this;
        }

        public MockResources overrideResource(int id, String[] value) {
            mStringArrayOverrides.put(id, value);
            return this;
        }
    }

    static final class MockStorageMonitoringInterface implements StorageMonitoringInterface {}

    static final class MockSystemStateInterface implements SystemStateInterface {
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
                Duration delayRange) {}
    }

    static final class MockTimeInterface implements TimeInterface {

        @Override
        public void scheduleAction(Runnable r, long delayMs) {}

        @Override
        public void cancelAllActions() {}
    }

    static final class MockWakeLockInterface implements WakeLockInterface {

        @Override
        public void releaseAllWakeLocks(int displayId) {}

        @Override
        public void switchToPartialWakeLock(int displayId) {}

        @Override
        public void switchToFullWakeLock(int displayId) {}
    }
}

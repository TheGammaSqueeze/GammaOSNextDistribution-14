/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.car;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.Nullable;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.car.test.util.ExceptionalFunction;
import android.content.ComponentName;
import android.content.Context;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;
import android.util.Pair;

import com.android.car.CarServiceUtils;
import com.android.car.internal.ICarServiceHelper;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Unit test for Car API.
 */
public final class CarTest extends AbstractExtendedMockitoTestCase {

    private static final String TAG = CarTest.class.getSimpleName();
    private static final String PKG_NAME = "Bond.James.Bond";

    @Mock
    private Context mContext;

    private int mGetServiceCallCount;


    // It is tricky to mock this. So create placeholder version instead.
    private static final class FakeService extends ICar.Stub {

        public ExceptionalFunction<String, CarVersion, RemoteException>
                getTargetCarApiVersionMocker;

        @Override
        public void setSystemServerConnections(ICarServiceHelper helper,
                ICarResultReceiver receiver) throws RemoteException {
        }

        @Override
        public boolean isFeatureEnabled(String featureName) {
            return false;
        }

        @Override
        public int enableFeature(String featureName) {
            return Car.FEATURE_REQUEST_SUCCESS;
        }

        @Override
        public int disableFeature(String featureName) {
            return Car.FEATURE_REQUEST_SUCCESS;
        }

        @Override
        public List<String> getAllEnabledFeatures() {
            return Collections.EMPTY_LIST;
        }

        @Override
        public List<String> getAllPendingDisabledFeatures() {
            return Collections.EMPTY_LIST;
        }

        @Override
        public List<String> getAllPendingEnabledFeatures() {
            return Collections.EMPTY_LIST;
        }

        @Override
        public String getCarManagerClassForFeature(String featureName) {
            return null;
        }

        @Override
        public android.os.IBinder getCarService(java.lang.String serviceName) {
            return null;
        }

        @Override
        public int getCarConnectionType() {
            return 0;
        }
    };

    private final FakeService mService = new FakeService();


    private final class LifecycleListener implements Car.CarServiceLifecycleListener {
        // Use thread safe one to prevent adding another lock for testing
        private CopyOnWriteArrayList<Pair<Car, Boolean>> mEvents = new CopyOnWriteArrayList<>();

        @Override
        public void onLifecycleChanged(Car car, boolean ready) {
            assertThat(Looper.getMainLooper()).isEqualTo(Looper.myLooper());
            mEvents.add(new Pair<>(car, ready));
        }
    }

    private final LifecycleListener mLifecycleListener = new LifecycleListener();

    @Before
    public void setUp() {
        when(mContext.getPackageName()).thenReturn(PKG_NAME);
    }

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder session) {
        session.spyStatic(ServiceManager.class);
    }

    private void expectService(@Nullable IBinder service) {
        doReturn(service).when(
                () -> ServiceManager.getService(Car.CAR_SERVICE_BINDER_SERVICE_NAME));
    }

    private void expectBindService() {
        when(mContext.bindService(anyObject(), anyObject(), anyInt())).thenReturn(true);
    }

    private void returnServiceAfterNSereviceManagerCalls(int returnNonNullAfterThisCall) {
        doAnswer((InvocationOnMock invocation)  -> {
            mGetServiceCallCount++;
            if (mGetServiceCallCount > returnNonNullAfterThisCall) {
                return mService;
            } else {
                return null;
            }
        }).when(() -> ServiceManager.getService(Car.CAR_SERVICE_BINDER_SERVICE_NAME));
    }

    private void assertServiceBoundOnce() {
        verify(mContext, times(1)).bindService(anyObject(), anyObject(), anyInt());
    }

    private void assertOneListenerCallAndClear(Car expectedCar, boolean ready) {
        assertThat(mLifecycleListener.mEvents).containsExactly(new Pair<>(expectedCar, ready));
        mLifecycleListener.mEvents.clear();
    }

    @Test
    public void testCreateCarSuccessWithCarServiceRunning() {
        expectService(mService);
        Car car = Car.createCar(mContext);
        assertThat(car).isNotNull();
        car.disconnect();
    }

    @Test
    public void testCreateCarReturnNull() {
        // car service is not running yet and bindService does not bring the service yet.
        // createCar should timeout and give up.
        expectService(null);
        assertThat(Car.createCar(mContext)).isNull();
    }

    @Test
    public void testCreateCarOkWhenCarServiceIsStarted() {
        returnServiceAfterNSereviceManagerCalls(10);
        // Car service is not running yet and binsService call should start it.
        expectBindService();
        Car car = Car.createCar(mContext);
        assertThat(car).isNotNull();
        assertServiceBoundOnce();
        car.disconnect();
    }

    @Test
    public void testCreateCarWithStatusChangeNoServiceConnectionWithCarServiceStarted() {
        returnServiceAfterNSereviceManagerCalls(10);
        expectBindService();
        Car car = Car.createCar(mContext, null,
                Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER, mLifecycleListener);
        assertThat(car).isNotNull();
        assertServiceBoundOnce();
        waitForMainToBeComplete();
        assertOneListenerCallAndClear(car, true);

        // Just call these to guarantee that nothing crashes with these call.
        runOnMainSyncSafe(() -> {
            car.getServiceConnectionListener().onServiceConnected(new ComponentName("", ""),
                    mService);
            car.getServiceConnectionListener().onServiceDisconnected(new ComponentName("", ""));
        });
    }

    @Test
    public void testCreateCarWithStatusChangeNoServiceHandleCarServiceRestart() {
        expectService(mService);
        expectBindService();
        Car car = Car.createCar(mContext, null,
                Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER, mLifecycleListener);
        assertThat(car).isNotNull();
        assertServiceBoundOnce();

        // fake connection
        runOnMainSyncSafe(() ->
                car.getServiceConnectionListener().onServiceConnected(new ComponentName("", ""),
                        mService));
        waitForMainToBeComplete();
        assertOneListenerCallAndClear(car, true);

        // fake crash
        runOnMainSyncSafe(() ->
                car.getServiceConnectionListener().onServiceDisconnected(
                        new ComponentName("", "")));
        waitForMainToBeComplete();
        assertOneListenerCallAndClear(car, false);


        // fake restart
        runOnMainSyncSafe(() ->
                car.getServiceConnectionListener().onServiceConnected(new ComponentName("", ""),
                        mService));
        waitForMainToBeComplete();
        assertOneListenerCallAndClear(car, true);
    }

    @Test
    public void testCreateCarWithStatusChangeDirectCallInsideMainForServiceAlreadyReady() {
        expectService(mService);
        expectBindService();
        runOnMainSyncSafe(() -> {
            Car car = Car.createCar(mContext, null,
                    Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER, mLifecycleListener);
            assertThat(car).isNotNull();
            verify(mContext, times(1)).bindService(anyObject(), anyObject(), anyInt());
            // mLifecycleListener should have been called as this is main thread.
            assertOneListenerCallAndClear(car, true);
        });
    }

    @Test
    public void testCreateCarWithStatusChangeDirectCallInsideMainForServiceReadyLater() {
        returnServiceAfterNSereviceManagerCalls(10);
        expectBindService();
        runOnMainSyncSafe(() -> {
            Car car = Car.createCar(mContext, null,
                    Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER, mLifecycleListener);
            assertThat(car).isNotNull();
            assertServiceBoundOnce();
            assertOneListenerCallAndClear(car, true);
        });
    }

    @Test
    public void testDevelopmentVersion() {
        /*
         * TODO(b/242311601): Once U has its own branch
         * - Change DEVELOPMENT_PLATFORM_CODENAME and DEVELOPMENT_PLATFORM to null in U branch.
         * - Change DEVELOPMENT_PLATFORM_CODENAME and DEVELOPMENT_PLATFORM to V-release code in
         *   master branch.
         * - Update the unit test testDevelopmentVersion for testing special handling of V version
         *   in master branch.
         * - Remove the unit test testDevelopmentVersion from U branch as it no longer a development
         *   branch and doesn't go through the code path of the special logic.
         */
        PlatformVersion.forMajorVersion(Integer.MAX_VALUE)
                .isAtLeast(PlatformVersion.VERSION_CODES.UPSIDE_DOWN_CAKE_0);
    }

    private void runOnMainSyncSafe(Runnable runnable) {
        if (Looper.getMainLooper() == Looper.myLooper()) {
            Log.d(TAG, "Running runnable directly on " + Thread.currentThread());
            runnable.run();
        } else {
            Log.d(TAG, "Running runnable using CarServiceUtils.runOnMainSync()");
            CarServiceUtils.runOnMainSync(runnable);
        }
    }
    private void waitForMainToBeComplete() {
        // dispatch placeholder runnable and confirm that it is done.
        runOnMainSyncSafe(() -> { });
    }

    private void onNewCar(Consumer<Car> action) throws Exception {
        expectService(mService);

        Car car = Car.createCar(mContext);
        try {
            assertThat(car).isNotNull();
            action.accept(car);
        } finally {
            car.disconnect();
        }
    }
}

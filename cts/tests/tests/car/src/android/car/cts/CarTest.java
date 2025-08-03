/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.car.Car;
import android.car.CarInfoManager;
import android.car.CarVersion;
import android.car.PlatformVersion;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemProperties;
import android.platform.test.annotations.AppModeFull;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@SmallTest
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Test relies on other server to connect to.")
public class CarTest extends AbstractCarTestCase {

    private static final long DEFAULT_WAIT_TIMEOUT_MS = 2000;

    private Context mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

    private Car mCar;
    private DefaultServiceConnectionListener mServiceConnectionListener;

    @After
    public void tearDown() {
        if (mCar != null && mCar.isConnected()) {
            mCar.disconnect();
        }
    }

    @ApiTest(apis = {
            "android.car.Car#isConnected", "android.car.Car#isConnecting", "android.car.Car#connect"
    })
    @Test
    public void testConnection() throws Exception {
        mServiceConnectionListener = new DefaultServiceConnectionListener();
        mCar = Car.createCar(mContext, mServiceConnectionListener);
        assertThat(mCar.isConnected()).isFalse();
        assertThat(mCar.isConnecting()).isFalse();
        mCar.connect();
        mServiceConnectionListener.waitForConnection(DEFAULT_WAIT_TIMEOUT_MS);
        assertThat(mServiceConnectionListener.isConnected()).isTrue();
        assertThat(mCar.getCarConnectionType()).isEqualTo(Car.CONNECTION_TYPE_EMBEDDED);
        mCar.disconnect();
        assertThat(mCar.isConnected()).isFalse();
        assertThat(mCar.isConnecting()).isFalse();
    }

    @ApiTest(apis = {"android.car.Car#createCar(Context)"})
    @Test
    public void testBlockingCreateCar() throws Exception {
        mCar = Car.createCar(mContext);
        assertConnectedCar(mCar);
    }

    @ApiTest(apis = {"android.car.Car#getCarConnectionType"})
    @Test
    public void testConnectionType() throws Exception {
        createCarAndRunOnReady((car) -> assertThat(car.getCarConnectionType()).isEqualTo(
                Car.CONNECTION_TYPE_EMBEDDED));
    }

    @ApiTest(apis = {"android.car.Car#isFeatureEnabled(String)"})
    @Test
    public void testIsFeatureEnabled() throws Exception {
        createCarAndRunOnReady(
                (car) -> assertThat(car.isFeatureEnabled(Car.AUDIO_SERVICE)).isTrue());
    }

    @ApiTest(apis = {"android.car.Car#createCar(Context,Handler,long,CarServiceLifecycleListener)"})
    @Test
    public void testCreateCarWaitForever() throws Exception {
        CarServiceLifecycleListenerImpl listenerImpl = new CarServiceLifecycleListenerImpl(null);
        mCar = Car.createCar(mContext, /* handler= */ null, Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER,
                listenerImpl);
        assertConnectedCar(mCar);
        listenerImpl.waitForReady(DEFAULT_WAIT_TIMEOUT_MS);
    }

    @ApiTest(apis = {"android.car.Car#createCar(Context,Handler,long,CarServiceLifecycleListener)"})
    @Test
    public void testCreateCarNoWait() throws Exception {
        CarServiceLifecycleListenerImpl listenerImpl = new CarServiceLifecycleListenerImpl(null);
        // car service should be already running, so for normal apps, this should always return
        // immediately
        mCar = Car.createCar(mContext, /* handler= */ null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT,
                listenerImpl);
        assertConnectedCar(mCar);
        listenerImpl.waitForReady(DEFAULT_WAIT_TIMEOUT_MS);
    }

    @Test
    @ApiTest(apis = {"android.car.Car#isApiVersionAtLeast(int)",
            "android.car.Car#isApiAndPlatformVersionAtLeast(int,int)"})
    public void testApiVersion() throws Exception {
        int ApiVersionTooHigh = 1000000;
        int MinorApiVersionTooHigh = 1000000;
        assertThat(Car.isApiVersionAtLeast(Car.API_VERSION_MAJOR_INT)).isTrue();
        assertThat(Car.isApiVersionAtLeast(ApiVersionTooHigh)).isFalse();

        assertThat(Car.isApiVersionAtLeast(Car.API_VERSION_MAJOR_INT -1 ,
                MinorApiVersionTooHigh)).isTrue();
        assertThat(Car.isApiVersionAtLeast(Car.API_VERSION_MAJOR_INT,
                Car.API_VERSION_MINOR_INT)).isTrue();
        assertThat(Car.isApiVersionAtLeast(Car.API_VERSION_MAJOR_INT,
                MinorApiVersionTooHigh)).isFalse();
        assertThat(Car.isApiVersionAtLeast(ApiVersionTooHigh, 0)).isFalse();

        assertThat(Car.isApiAndPlatformVersionAtLeast(Car.API_VERSION_MAJOR_INT,
                Build.VERSION.SDK_INT)).isTrue();
        assertThat(Car.isApiAndPlatformVersionAtLeast(Car.API_VERSION_MAJOR_INT,
                Car.API_VERSION_MINOR_INT, Build.VERSION.SDK_INT)).isTrue();
        if ("REL".equals(Build.VERSION.CODENAME)) { // SDK + 1 only works for released platform.
            assertThat(Car.isApiAndPlatformVersionAtLeast(Car.API_VERSION_MAJOR_INT,
                    Build.VERSION.SDK_INT + 1)).isFalse();
            assertThat(Car.isApiAndPlatformVersionAtLeast(Car.API_VERSION_MAJOR_INT,
                    Car.API_VERSION_MINOR_INT, Build.VERSION.SDK_INT + 1)).isFalse();
        }
    }

    @ApiTest(apis =
            {"android.car.Car#getCarManager(String)", "android.car.Car#getCarManager(Class)"})
    @Test
    public void testGetCarManager() throws Exception {
        mCar = Car.createCar(mContext);
        Object noSuchService = mCar.getCarManager("No such service");
        mExpect.that(noSuchService).isNull();

        // TODO(b/217131789): Add expectThat which wraps around mExpect.that.
        CarInfoManager carInfoManager =
                (CarInfoManager) mCar.getCarManager(Car.INFO_SERVICE);
        mExpect.that(carInfoManager).isNotNull();

        CarInfoManager carInfoManager2 =
                (CarInfoManager) mCar.getCarManager(Car.INFO_SERVICE);
        mExpect.that(carInfoManager2).isNotNull();
        mExpect.that(carInfoManager2).isSameInstanceAs(carInfoManager);

        CarInfoManager carInfoManagerByClass = mCar.getCarManager(CarInfoManager.class);
        mExpect.that(carInfoManagerByClass).isNotNull();
        mExpect.that(carInfoManagerByClass).isSameInstanceAs(carInfoManager);

        CarInfoManager carInfoManagerByClass2 = mCar.getCarManager(CarInfoManager.class);
        mExpect.that(carInfoManagerByClass2).isNotNull();
        mExpect.that(carInfoManagerByClass2).isSameInstanceAs(carInfoManager);
    }

    @ApiTest(apis = {"android.car.Car#getCarVersion"})
    @Test
    public void testGetCarVersion() {
        CarVersion version = Car.getCarVersion();

        assertWithMessage("Car.getCarVersion()").that(version).isNotNull();
        assertWithMessage("Car.getCarVersion().toString()").that(version.toString())
                .contains("name=Car.CAR_VERSION");
    }

    @ApiTest(apis = {"android.car.Car#getPlatformVersion"})
    @Test
    public void testGetPlatformVersion() {
        PlatformVersion version = Car.getPlatformVersion();

        assertWithMessage("Car.getPlatformVersion()").that(version).isNotNull();
        assertWithMessage("Car.getPlatformVersion().toString()").that(version.toString())
        .contains("name=Car.PLATFORM_VERSION");
    }

    @ApiTest(apis = {"android.car.Car#getPlatformVersion"})
    @Test
    public void testPlatformVersionIsNotEmulated() {
        assertSystemPropertyNotSet(Car.PROPERTY_EMULATED_PLATFORM_VERSION_MAJOR);
        assertSystemPropertyNotSet(Car.PROPERTY_EMULATED_PLATFORM_VERSION_MINOR);
    }

    private void assertSystemPropertyNotSet(String property) {
        String value = SystemProperties.get(property);
        if (!TextUtils.isEmpty(value)) {
            throw new AssertionError("Property '" + property + "' is set; value is " + value);
        }
    }

    private static void assertConnectedCar(Car car) {
        assertThat(car).isNotNull();
        assertThat(car.isConnected()).isTrue();
    }

    private interface ReadyListener {
        void onReady(Car car);
    }

    private void createCarAndRunOnReady(ReadyListener readyListener) throws Exception {
        CarServiceLifecycleListenerImpl listenerImpl = new CarServiceLifecycleListenerImpl(
                readyListener);
        mCar = Car.createCar(mContext, /* handler= */ null, DEFAULT_WAIT_TIMEOUT_MS,
                listenerImpl);
        assertConnectedCar(mCar);
        listenerImpl.waitForReady(DEFAULT_WAIT_TIMEOUT_MS);
    }

    private static final class CarServiceLifecycleListenerImpl
            implements Car.CarServiceLifecycleListener {

        private final ReadyListener mReadyListener;
        private final CountDownLatch mWaitLatch = new CountDownLatch(1);

        private CarServiceLifecycleListenerImpl(@Nullable ReadyListener readyListener) {
            mReadyListener = readyListener;
        }

        private void waitForReady(long waitTimeMs) throws Exception {
            assertThat(mWaitLatch.await(waitTimeMs, TimeUnit.MILLISECONDS)).isTrue();
        }

        @Override
        public void onLifecycleChanged(@NonNull Car car, boolean ready) {
            assertConnectedCar(car);
            assertThat(ready).isTrue();
            if (mReadyListener != null) {
                mReadyListener.onReady(car);
            }
            mWaitLatch.countDown();
        }
    }

    protected class DefaultServiceConnectionListener implements ServiceConnection {
        private final Semaphore mConnectionWait = new Semaphore(0);

        private boolean mIsconnected = false;

        public synchronized boolean isConnected() {
            return mIsconnected;
        }

        public void waitForConnection(long timeoutMs) throws InterruptedException {
            mConnectionWait.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            synchronized (this) {
                mIsconnected = false;
            }
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            synchronized (this) {
                mIsconnected = true;
            }
            mConnectionWait.release();
        }
    }
}

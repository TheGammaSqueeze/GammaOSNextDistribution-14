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

package android.car.cts.builtin.os;

import static androidx.test.InstrumentationRegistry.getContext;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.app.Instrumentation;
import android.car.Car;
import android.car.builtin.os.ServiceManagerHelper;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class ServiceManagerHelperTest {

    private static final String SERVICE_PACKAGE_NAME = "android.car.cts.builtin";
    private static final String CAR_SERVICE_NAME = "car_service";
    private static final String SERVICE_LIST_COMMAND = "cmd -l";
    private static final String SERVICE_LIST_SPLITTER_REGEX = "\\p{Blank}*\n\\p{Blank}*";

    private static final String[] SYSTEM_SERVICES = {
        Context.ACTIVITY_SERVICE,
        Context.ALARM_SERVICE,
        Context.AUDIO_SERVICE,
        Context.DISPLAY_SERVICE,
        Context.INPUT_SERVICE,
        Context.JOB_SCHEDULER_SERVICE,
        Context.LOCATION_SERVICE,
        Context.POWER_SERVICE,
        Context.WINDOW_SERVICE
    };
    private static final int TIMEOUT = 20_000;

    private Instrumentation mInstrumentation;
    private PeerConnection mRemoteConnection;
    private IServiceManagerTestService mRemoteService;

    public static class PeerConnection implements ServiceConnection {
        private final CountDownLatch mServiceReadyLatch = new CountDownLatch(1);

        private IServiceManagerTestService mService;

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = IServiceManagerTestService.Stub.asInterface(service);
            mServiceReadyLatch.countDown();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }

        public void waitForServiceReady() throws Exception {
            mServiceReadyLatch.await(TIMEOUT, TimeUnit.MILLISECONDS);
        }

        public IServiceManagerTestService getService() {
            return mService;
        }
    }

    @Test
    public void testInitServiceCache() throws Exception {
        // The service manager keeps a cache of system services during
        // initialization
        for (String serviceName : SYSTEM_SERVICES) {
            assertNotNull(ServiceManagerHelper.checkService(serviceName));
            assertNotNull(ServiceManagerHelper.getService(serviceName));
        }
    }

    @Test
    public void testServiceCacheWithCreatedService() throws Exception {
        // setup a new service
        String testServiceName = ServiceManagerTestService.class.getName();
        Random randomGenerator = new Random();
        int maxCount = 10;

        setUpService();

        // assert the new service works
        for (int i = 0; i < maxCount; i++) {
            int val = randomGenerator.nextInt();
            assertEquals(val, mRemoteService.echo(val));
        }

        // assert the newly created service is not in the ServiceManager cache
        assertNull(ServiceManagerHelper.checkService(testServiceName));
        assertNull(ServiceManagerHelper.getService(testServiceName));
    }

    // In the ICar binder service and CarService initialization, both waitForDeclaredService
    // and addService APIs are called. So the existence of CarService valids the correct
    // behavior of the APIs and code coverage
    @Test
    public void testCarServiceExistence() throws Exception {
        Car theCar = Car.createCar(getContext());
        assertThat(theCar).isNotNull();

        String[] serviceList = SystemUtil
                .runShellCommand(SERVICE_LIST_COMMAND).trim().split(SERVICE_LIST_SPLITTER_REGEX);
        Predicate<String> checkCarServiceName = (serviceName) -> {
            return CAR_SERVICE_NAME.equals(serviceName);
        };
        assertThat(Arrays.stream(serviceList).anyMatch(checkCarServiceName)).isTrue();
    }

    private void setUpService() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = mInstrumentation.getContext();
        // Bring up both remote processes and wire them to each other
        Intent remoteIntent = new Intent();
        remoteIntent.setComponent(new ComponentName(SERVICE_PACKAGE_NAME,
                ServiceManagerTestService.class.getName()));
        mRemoteConnection = new PeerConnection();
        getContext().bindService(remoteIntent, mRemoteConnection,
                Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT);

        mRemoteConnection.waitForServiceReady();
        mRemoteService = mRemoteConnection.getService();
        assertNotNull(mRemoteService);
    }
}

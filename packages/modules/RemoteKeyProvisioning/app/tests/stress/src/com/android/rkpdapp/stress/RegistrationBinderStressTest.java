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

package com.android.rkpdapp.stress;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.content.Context;
import android.hardware.security.keymint.IRemotelyProvisionedComponent;
import android.os.IBinder;
import android.os.Process;
import android.os.ServiceManager;
import android.os.SystemProperties;

import androidx.test.core.app.ApplicationProvider;

import com.android.rkpdapp.IGetKeyCallback;
import com.android.rkpdapp.RemotelyProvisionedKey;
import com.android.rkpdapp.database.ProvisionedKey;
import com.android.rkpdapp.database.ProvisionedKeyDao;
import com.android.rkpdapp.database.RkpdDatabase;
import com.android.rkpdapp.interfaces.ServerInterface;
import com.android.rkpdapp.interfaces.ServiceManagerInterface;
import com.android.rkpdapp.interfaces.SystemInterface;
import com.android.rkpdapp.provisioner.Provisioner;
import com.android.rkpdapp.service.RegistrationBinder;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RegistrationBinderStressTest {
    private static final int NUM_THREADS = Math.min(16, Runtime.getRuntime().availableProcessors());
    private static final Duration STRESS_THREAD_TIME_LIMIT = Duration.ofSeconds(60);
    private static final String SERVICE = IRemotelyProvisionedComponent.DESCRIPTOR + "/default";
    private final ExecutorService mExecutor = Executors.newCachedThreadPool();
    private Context mContext;
    private SystemInterface mIrpcHal;
    private ProvisionedKeyDao mKeyDao;

    @Rule
    public final TestName mName = new TestName();

    @Before
    public void setUp() throws Exception {
        assume()
                .withMessage("The RKP server hostname is not configured -- assume RKP disabled.")
                .that(SystemProperties.get("remote_provisioning.hostname"))
                .isNotEmpty();
        assume()
                .withMessage("Remotely Provisioned Component is not found -- RKP disabled.")
                .that(ServiceManager.isDeclared(SERVICE))
                .isTrue();
        mContext = ApplicationProvider.getApplicationContext();
        mIrpcHal = ServiceManagerInterface.getInstance(SERVICE);
        mKeyDao = RkpdDatabase.getDatabase(mContext).provisionedKeyDao();
        mKeyDao.deleteAllKeys();
    }

    private RegistrationBinder createRegistrationBinder() {
        boolean isAsync = false;
        return new RegistrationBinder(mContext, Process.myUid(), mIrpcHal, mKeyDao,
                new ServerInterface(mContext, isAsync), new Provisioner(mContext, mKeyDao, isAsync),
                mExecutor);
    }

    private void getKeyHelper(int keyId) {
        RegistrationBinder binder = createRegistrationBinder();
        CompletableFuture<String> result = new CompletableFuture<>();
        binder.getKey(keyId, new IGetKeyCallback.Stub() {
            @Override
            public void onSuccess(RemotelyProvisionedKey key) {
                result.complete("");
            }

            @Override
            public void onProvisioningNeeded() { /* noop */ }

            @Override
            public void onCancel() {
                result.complete("Received unexpected cancel");
            }

            @Override
            public void onError(byte error, String description) {
                result.complete(description);
            }

            @Override
            public IBinder asBinder() {
                return this;
            }
        });
        try {
            assertThat(result.get()).isEmpty();
        } catch (Exception e) {
            assertWithMessage("Unexpected exception: " + e).fail();
        }
    }

    @Test
    public void testGetSameKeyInParallel() throws Exception {
        // Run through various operations on binder objects in parallel, ensuring operations
        // work concurrently.
        int keyId = 0;
        List<Thread> stressThreads = new ArrayList<>();
        Instant endOfTest = Instant.now().plus(STRESS_THREAD_TIME_LIMIT);
        for (int i = 0; i < NUM_THREADS; ++i) {
            System.out.println("Spinning up test thread " + i);
            Thread t = new Thread(() -> {
                int counter = 0;
                while (Instant.now().isBefore(endOfTest)) {
                    ++counter;
                    if (counter % 1000 == 0) {
                        System.out.println("Thread " + Thread.currentThread().getId()
                                + " on iteration " + counter);
                    }
                    getKeyHelper(keyId);

                    // Clear the key assignment so that it can be re-used without hitting the RKP
                    // server again. It's possible another thread already unassigned the key, so
                    // we cannot assume we get a key here.
                    ProvisionedKey key = mKeyDao.getKeyForClientAndIrpc(mIrpcHal.getServiceName(),
                            Process.myUid(), keyId);
                    if (key != null) {
                        key.keyId = null;
                        key.clientUid = null;
                        mKeyDao.updateKey(key);
                    }
                }
            });
            t.start();
            stressThreads.add(t);
        }

        for (Thread t : stressThreads) {
            t.join();
        }
    }
}

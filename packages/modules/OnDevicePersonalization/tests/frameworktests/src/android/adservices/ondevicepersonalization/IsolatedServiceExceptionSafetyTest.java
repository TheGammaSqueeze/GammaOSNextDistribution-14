/*
 * Copyright 2024 The Android Open Source Project
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

package android.adservices.ondevicepersonalization;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.adservices.ondevicepersonalization.aidl.IDataAccessService;
import android.adservices.ondevicepersonalization.aidl.IDataAccessServiceCallback;
import android.adservices.ondevicepersonalization.aidl.IFederatedComputeCallback;
import android.adservices.ondevicepersonalization.aidl.IFederatedComputeService;
import android.adservices.ondevicepersonalization.aidl.IIsolatedModelService;
import android.adservices.ondevicepersonalization.aidl.IIsolatedModelServiceCallback;
import android.adservices.ondevicepersonalization.aidl.IIsolatedService;
import android.adservices.ondevicepersonalization.aidl.IIsolatedServiceCallback;
import android.content.Context;
import android.federatedcompute.common.TrainingOptions;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.RemoteException;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.federatedcompute.internal.util.AbstractServiceBinder;
import com.android.ondevicepersonalization.internal.util.ByteArrayParceledSlice;
import com.android.ondevicepersonalization.internal.util.PersistableBundleUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@SmallTest
@RunWith(Parameterized.class)
public class IsolatedServiceExceptionSafetyTest {

    private final Context mContext = ApplicationProvider.getApplicationContext();

    private IIsolatedService mIsolatedService;
    private AbstractServiceBinder<IIsolatedService> mServiceBinder;
    private int mCallbackErrorCode;
    private int mIsolatedServiceErrorCode;
    private CountDownLatch mLatch;

    @Parameterized.Parameter(0)
    public String operation;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(
                new Object[][] {
                    {RuntimeException.class.getName()},
                    {NullPointerException.class.getName()},
                    {IllegalArgumentException.class.getName()}
                });
    }

    @Before
    public void setUp() throws Exception {
        mServiceBinder = AbstractServiceBinder.getIsolatedServiceBinderByServiceName(
                mContext,
                "android.adservices.ondevicepersonalization.IsolatedServiceExceptionSafetyTestImpl",
                mContext.getPackageName(),
                "testIsolatedProcess",
                0,
                IIsolatedService.Stub::asInterface);

        mIsolatedService = mServiceBinder.getService(Runnable::run);
        mLatch = new CountDownLatch(1);
    }

    @After
    public void tearDown() {
        mServiceBinder.unbindFromService();
        mIsolatedService = null;
        mCallbackErrorCode = 0;
    }

    @Test
    public void testOnRequestExceptions() throws Exception {
        PersistableBundle appParams = new PersistableBundle();
        appParams.putString("ex", operation);
        ExecuteInputParcel input =
                new ExecuteInputParcel.Builder()
                        .setAppPackageName("com.testapp")
                        .setSerializedAppParams(new ByteArrayParceledSlice(
                                PersistableBundleUtils.toByteArray(appParams)))
                        .build();
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_INPUT, input);
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        params.putBinder(
                Constants.EXTRA_FEDERATED_COMPUTE_SERVICE_BINDER,
                new TestFederatedComputeService());
        params.putBinder(Constants.EXTRA_MODEL_SERVICE_BINDER, new TestIsolatedModelService());
        mIsolatedService.onRequest(Constants.OP_EXECUTE, params, new TestServiceCallback());
        assertTrue(mLatch.await(5000, TimeUnit.MILLISECONDS));
        assertEquals(Constants.STATUS_INTERNAL_ERROR, mCallbackErrorCode);
    }

    @Test
    public void testOnDownloadExceptions() throws Exception {
        DownloadInputParcel input =
                new DownloadInputParcel.Builder()
                        .setDataAccessServiceBinder(new TestDataAccessService(operation))
                        .build();
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_INPUT, input);
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        params.putBinder(
                Constants.EXTRA_FEDERATED_COMPUTE_SERVICE_BINDER,
                new TestFederatedComputeService());
        mIsolatedService.onRequest(Constants.OP_DOWNLOAD, params, new TestServiceCallback());
        assertTrue(mLatch.await(5000, TimeUnit.MILLISECONDS));
        assertEquals(Constants.STATUS_INTERNAL_ERROR, mCallbackErrorCode);
    }

    @Test
    public void testOnRender() throws Exception {
        RenderInputParcel input =
                new RenderInputParcel.Builder()
                        .setRenderingConfig(
                                new RenderingConfig.Builder().addKey(operation).build())
                        .build();
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_INPUT, input);
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        mIsolatedService.onRequest(Constants.OP_RENDER, params, new TestServiceCallback());
        assertTrue(mLatch.await(5000, TimeUnit.MILLISECONDS));
        assertEquals(Constants.STATUS_INTERNAL_ERROR, mCallbackErrorCode);
    }

    @Test
    public void testOnEvent() throws Exception {
        PersistableBundle appParams = new PersistableBundle();
        appParams.putString("ex", operation);
        Bundle params = new Bundle();
        params.putParcelable(
                Constants.EXTRA_INPUT,
                new EventInputParcel.Builder().setParameters(appParams).build());
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        params.putBinder(Constants.EXTRA_MODEL_SERVICE_BINDER, new TestIsolatedModelService());
        mIsolatedService.onRequest(Constants.OP_WEB_VIEW_EVENT, params, new TestServiceCallback());
        assertTrue(mLatch.await(5000, TimeUnit.MILLISECONDS));
        assertEquals(Constants.STATUS_INTERNAL_ERROR, mCallbackErrorCode);
    }

    @Test
    public void testOnTrainingExamples() throws Exception {
        TrainingExamplesInputParcel input =
                new TrainingExamplesInputParcel.Builder()
                        .setPopulationName("")
                        .setTaskName(operation)
                        .setResumptionToken(new byte[] {0})
                        .build();
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_INPUT, input);
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        mIsolatedService.onRequest(
                Constants.OP_TRAINING_EXAMPLE, params, new TestServiceCallback());
        assertTrue(mLatch.await(5000, TimeUnit.MILLISECONDS));
        assertEquals(Constants.STATUS_INTERNAL_ERROR, mCallbackErrorCode);
    }

    @Test
    public void testOnWebTrigger() throws Exception {
        WebTriggerInputParcel input =
                new WebTriggerInputParcel.Builder(
                                Uri.parse("http://desturl"), operation, new byte[] {1, 2, 3})
                        .build();
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_INPUT, input);
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        params.putBinder(Constants.EXTRA_MODEL_SERVICE_BINDER, new TestIsolatedModelService());
        mIsolatedService.onRequest(Constants.OP_WEB_TRIGGER, params, new TestServiceCallback());
        assertTrue(mLatch.await(5000, TimeUnit.MILLISECONDS));
        assertEquals(Constants.STATUS_INTERNAL_ERROR, mCallbackErrorCode);
    }

    class TestServiceCallback extends IIsolatedServiceCallback.Stub {
        @Override
        public void onSuccess(Bundle result) {
            mLatch.countDown();
        }

        @Override
        public void onError(int errorCode, int isolatedServiceErrorCode) {
            mCallbackErrorCode = errorCode;
            mIsolatedServiceErrorCode = isolatedServiceErrorCode;
            mLatch.countDown();
        }
    }

    static class TestDataAccessService extends IDataAccessService.Stub {

        String mOp;

        TestDataAccessService(String operation) {
            this.mOp = operation;
        }

        TestDataAccessService() {
            mOp = null;
        }

        @Override
        public void onRequest(int operation, Bundle params, IDataAccessServiceCallback callback) {
            // pass parameters for onDownloadCompleted testing
            if (mOp != null) {
                Bundle bndl = new Bundle();
                bndl.putParcelable(
                        Constants.EXTRA_RESULT, new ByteArrayParceledSlice(mOp.getBytes()));
                try {
                    callback.onSuccess(bndl);
                } catch (RemoteException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    static class TestFederatedComputeService extends IFederatedComputeService.Stub {
        @Override
        public void schedule(TrainingOptions trainingOptions, IFederatedComputeCallback callback) {}

        public void cancel(String populationName, IFederatedComputeCallback callback) {}
    }

    static class TestIsolatedModelService extends IIsolatedModelService.Stub {
        @Override
        public void runInference(Bundle params, IIsolatedModelServiceCallback callback) {}
    }
}

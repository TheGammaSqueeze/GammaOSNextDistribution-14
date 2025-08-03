/*
 * Copyright 2022 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.adservices.ondevicepersonalization.aidl.IDataAccessService;
import android.adservices.ondevicepersonalization.aidl.IDataAccessServiceCallback;
import android.adservices.ondevicepersonalization.aidl.IFederatedComputeCallback;
import android.adservices.ondevicepersonalization.aidl.IFederatedComputeService;
import android.adservices.ondevicepersonalization.aidl.IIsolatedModelService;
import android.adservices.ondevicepersonalization.aidl.IIsolatedModelServiceCallback;
import android.adservices.ondevicepersonalization.aidl.IIsolatedService;
import android.adservices.ondevicepersonalization.aidl.IIsolatedServiceCallback;
import android.content.ContentValues;
import android.federatedcompute.common.TrainingOptions;
import android.net.Uri;
import android.os.Bundle;
import android.os.OutcomeReceiver;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.ondevicepersonalization.internal.util.ByteArrayParceledSlice;
import com.android.ondevicepersonalization.internal.util.PersistableBundleUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/** Unit Tests of IsolatedService class. */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class IsolatedServiceTest {
    private static final String EVENT_TYPE_KEY = "event_type";
    private final TestService mTestService = new TestService();
    private final CountDownLatch mLatch = new CountDownLatch(1);
    private IIsolatedService mBinder;
    private boolean mOnExecuteCalled;
    private boolean mOnDownloadCalled;
    private boolean mOnRenderCalled;
    private boolean mOnEventCalled;
    private boolean mOnTrainingExampleCalled;
    private boolean mOnWebTriggerCalled;
    private Bundle mCallbackResult;
    private int mCallbackErrorCode;
    private int mIsolatedServiceErrorCode;

    @Before
    public void setUp() {
        mTestService.onCreate();
        mBinder = IIsolatedService.Stub.asInterface(mTestService.onBind(null));
    }

    @Test
    public void testServiceThrowsIfOpcodeInvalid() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    mBinder.onRequest(9999, new Bundle(), new TestServiceCallback());
                });
    }

    @Test
    public void testOnExecute() throws Exception {
        PersistableBundle appParams = new PersistableBundle();
        appParams.putString("x", "y");
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
        mBinder.onRequest(Constants.OP_EXECUTE, params, new TestServiceCallback());
        mLatch.await();
        assertTrue(mOnExecuteCalled);
        ExecuteOutputParcel result =
                mCallbackResult.getParcelable(Constants.EXTRA_RESULT, ExecuteOutputParcel.class);
        assertEquals(5, result.getRequestLogRecord().getRows().get(0).getAsInteger("a").intValue());
        assertEquals("123", result.getRenderingConfig().getKeys().get(0));
    }

    @Test
    public void testOnExecutePropagatesError() throws Exception {
        PersistableBundle appParams = new PersistableBundle();
        appParams.putInt("error", 1); // Trigger an error in the service.
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
        mBinder.onRequest(Constants.OP_EXECUTE, params, new TestServiceCallback());
        mLatch.await();
        assertTrue(mOnExecuteCalled);
        assertEquals(Constants.STATUS_SERVICE_FAILED, mCallbackErrorCode);
        assertEquals(1, mIsolatedServiceErrorCode);
    }

    @Test
    public void testOnExecuteWithoutAppParams() throws Exception {
        ExecuteInputParcel input = new ExecuteInputParcel.Builder().setAppPackageName("com.testapp").build();
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_INPUT, input);
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        params.putBinder(
                Constants.EXTRA_FEDERATED_COMPUTE_SERVICE_BINDER,
                new TestFederatedComputeService());
        params.putBinder(Constants.EXTRA_MODEL_SERVICE_BINDER, new TestIsolatedModelService());
        mBinder.onRequest(Constants.OP_EXECUTE, params, new TestServiceCallback());
        mLatch.await();
        assertTrue(mOnExecuteCalled);
    }

    @Test
    public void testOnExecuteThrowsIfParamsMissing() throws Exception {
        assertThrows(
                NullPointerException.class,
                () -> {
                    mBinder.onRequest(Constants.OP_EXECUTE, null, new TestServiceCallback());
                });
    }

    @Test
    public void testOnExecuteThrowsIfInputMissing() throws Exception {
        Bundle params = new Bundle();
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        params.putBinder(
                Constants.EXTRA_FEDERATED_COMPUTE_SERVICE_BINDER,
                new TestFederatedComputeService());
        mBinder.onRequest(Constants.OP_EXECUTE, params, new TestServiceCallback());
        mLatch.await();
        assertEquals(Constants.STATUS_INTERNAL_ERROR, mCallbackErrorCode);
    }

    @Test
    public void testOnExecuteThrowsIfDataAccessServiceMissing() throws Exception {
        ExecuteInputParcel input = new ExecuteInputParcel.Builder().setAppPackageName("com.testapp").build();
        Bundle params = new Bundle();
        params.putBinder(
                Constants.EXTRA_FEDERATED_COMPUTE_SERVICE_BINDER,
                new TestFederatedComputeService());
        params.putParcelable(Constants.EXTRA_INPUT, input);
        mBinder.onRequest(Constants.OP_EXECUTE, params, new TestServiceCallback());
        mLatch.await();
        assertEquals(Constants.STATUS_INTERNAL_ERROR, mCallbackErrorCode);
    }

    @Test
    public void testOnExecuteThrowsIfFederatedComputeServiceMissing() throws Exception {
        ExecuteInputParcel input = new ExecuteInputParcel.Builder().setAppPackageName("com.testapp").build();
        Bundle params = new Bundle();
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        params.putParcelable(Constants.EXTRA_INPUT, input);
        mBinder.onRequest(Constants.OP_EXECUTE, params, new TestServiceCallback());
        mLatch.await();
        assertEquals(Constants.STATUS_INTERNAL_ERROR, mCallbackErrorCode);
    }

    @Test
    public void testOnExecuteThrowsIfCallbackMissing() throws Exception {
        ExecuteInputParcel input = new ExecuteInputParcel.Builder().setAppPackageName("com.testapp").build();
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_INPUT, input);
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        assertThrows(
                NullPointerException.class,
                () -> {
                    mBinder.onRequest(Constants.OP_EXECUTE, params, null);
                });
    }

    @Test
    public void testOnDownload() throws Exception {
        DownloadInputParcel input =
                new DownloadInputParcel.Builder()
                        .setDataAccessServiceBinder(new TestDataAccessService())
                        .build();
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_INPUT, input);
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        params.putBinder(
                Constants.EXTRA_FEDERATED_COMPUTE_SERVICE_BINDER,
                new TestFederatedComputeService());
        mBinder.onRequest(Constants.OP_DOWNLOAD, params, new TestServiceCallback());
        mLatch.await();
        assertTrue(mOnDownloadCalled);
        DownloadCompletedOutputParcel result =
                mCallbackResult.getParcelable(
                        Constants.EXTRA_RESULT, DownloadCompletedOutputParcel.class);
        assertEquals("12", result.getRetainedKeys().get(0));
    }

    @Test
    public void testOnDownloadThrowsIfParamsMissing() throws Exception {
        assertThrows(
                NullPointerException.class,
                () -> {
                    mBinder.onRequest(Constants.OP_DOWNLOAD, null, new TestServiceCallback());
                });
    }

    @Test
    public void testOnDownloadThrowsIfInputMissing() throws Exception {
        Bundle params = new Bundle();
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        mBinder.onRequest(Constants.OP_DOWNLOAD, params, new TestServiceCallback());
        mLatch.await();
        assertEquals(Constants.STATUS_INTERNAL_ERROR, mCallbackErrorCode);
    }

    @Test
    public void testOnDownloadThrowsIfDataAccessServiceMissing() throws Exception {
        DownloadInputParcel input =
                new DownloadInputParcel.Builder()
                        .setDataAccessServiceBinder(new TestDataAccessService())
                        .build();
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_INPUT, input);
        mBinder.onRequest(Constants.OP_DOWNLOAD, params, new TestServiceCallback());
        mLatch.await();
        assertEquals(Constants.STATUS_INTERNAL_ERROR, mCallbackErrorCode);
    }

    @Test
    public void testOnDownloadThrowsIfFederatedComputeServiceMissing() throws Exception {
        DownloadInputParcel input =
                new DownloadInputParcel.Builder()
                        .setDataAccessServiceBinder(new TestDataAccessService())
                        .build();
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_INPUT, input);
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        mBinder.onRequest(Constants.OP_DOWNLOAD, params, new TestServiceCallback());
        mLatch.await();
        assertEquals(Constants.STATUS_INTERNAL_ERROR, mCallbackErrorCode);
    }

    @Test
    public void testOnDownloadThrowsIfCallbackMissing() throws Exception {
        ParcelFileDescriptor[] pfds = ParcelFileDescriptor.createPipe();
        DownloadInputParcel input =
                new DownloadInputParcel.Builder()
                        .setDataAccessServiceBinder(new TestDataAccessService())
                        .build();
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_INPUT, input);
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        assertThrows(
                NullPointerException.class,
                () -> {
                    mBinder.onRequest(Constants.OP_DOWNLOAD, params, null);
                });
    }

    @Test
    public void testOnRender() throws Exception {
        RenderInputParcel input =
                new RenderInputParcel.Builder()
                        .setRenderingConfig(
                                new RenderingConfig.Builder().addKey("a").addKey("b").build())
                        .build();
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_INPUT, input);
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        mBinder.onRequest(Constants.OP_RENDER, params, new TestServiceCallback());
        mLatch.await();
        assertTrue(mOnRenderCalled);
        RenderOutputParcel result =
                mCallbackResult.getParcelable(Constants.EXTRA_RESULT, RenderOutputParcel.class);
        assertEquals("htmlstring", result.getContent());
    }

    @Test
    public void testOnRenderPropagatesError() throws Exception {
        RenderInputParcel input =
                new RenderInputParcel.Builder()
                        .setRenderingConfig(
                                new RenderingConfig.Builder()
                                        .addKey("z") // Trigger error in service.
                                        .build())
                        .build();
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_INPUT, input);
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        mBinder.onRequest(Constants.OP_RENDER, params, new TestServiceCallback());
        mLatch.await();
        assertTrue(mOnRenderCalled);
        assertEquals(Constants.STATUS_SERVICE_FAILED, mCallbackErrorCode);
    }

    @Test
    public void testOnRenderThrowsIfParamsMissing() throws Exception {
        assertThrows(
                NullPointerException.class,
                () -> {
                    mBinder.onRequest(Constants.OP_RENDER, null, new TestServiceCallback());
                });
    }

    @Test
    public void testOnRenderThrowsIfInputMissing() throws Exception {
        Bundle params = new Bundle();
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        mBinder.onRequest(Constants.OP_RENDER, params, new TestServiceCallback());
        mLatch.await();
        assertEquals(Constants.STATUS_INTERNAL_ERROR, mCallbackErrorCode);
    }

    @Test
    public void testOnRenderThrowsIfDataAccessServiceMissing() throws Exception {
        RenderInputParcel input =
                new RenderInputParcel.Builder()
                        .setRenderingConfig(
                                new RenderingConfig.Builder().addKey("a").addKey("b").build())
                        .build();
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_INPUT, input);
        mBinder.onRequest(Constants.OP_RENDER, params, new TestServiceCallback());
        mLatch.await();
        assertEquals(Constants.STATUS_INTERNAL_ERROR, mCallbackErrorCode);
    }

    @Test
    public void testOnRenderThrowsIfCallbackMissing() throws Exception {
        RenderInputParcel input =
                new RenderInputParcel.Builder()
                        .setRenderingConfig(
                                new RenderingConfig.Builder().addKey("a").addKey("b").build())
                        .build();
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_INPUT, input);
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        assertThrows(
                NullPointerException.class,
                () -> {
                    mBinder.onRequest(Constants.OP_RENDER, params, null);
                });
    }

    @Test
    public void testOnEvent() throws Exception {
        Bundle params = new Bundle();
        params.putParcelable(
                Constants.EXTRA_INPUT,
                new EventInputParcel.Builder().setParameters(PersistableBundle.EMPTY).build());
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        params.putBinder(Constants.EXTRA_MODEL_SERVICE_BINDER, new TestIsolatedModelService());
        mBinder.onRequest(Constants.OP_WEB_VIEW_EVENT, params, new TestServiceCallback());
        mLatch.await();
        assertTrue(mOnEventCalled);
        EventOutputParcel result =
                mCallbackResult.getParcelable(Constants.EXTRA_RESULT, EventOutputParcel.class);
        assertEquals(1, result.getEventLogRecord().getType());
        assertEquals(2, result.getEventLogRecord().getRowIndex());
    }

    @Test
    public void testOnEventPropagatesError() throws Exception {
        PersistableBundle eventParams = new PersistableBundle();
        // Input value 9999 will trigger an error in the mock service.
        eventParams.putInt(EVENT_TYPE_KEY, 9999);
        Bundle params = new Bundle();
        params.putParcelable(
                Constants.EXTRA_INPUT,
                new EventInputParcel.Builder().setParameters(eventParams).build());
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        params.putBinder(Constants.EXTRA_MODEL_SERVICE_BINDER, new TestIsolatedModelService());
        mBinder.onRequest(Constants.OP_WEB_VIEW_EVENT, params, new TestServiceCallback());
        mLatch.await();
        assertTrue(mOnEventCalled);
        assertEquals(Constants.STATUS_SERVICE_FAILED, mCallbackErrorCode);
    }

    @Test
    public void testOnEventThrowsIfParamsMissing() throws Exception {
        assertThrows(
                NullPointerException.class,
                () -> {
                    mBinder.onRequest(Constants.OP_WEB_VIEW_EVENT, null, new TestServiceCallback());
                });
    }

    @Test
    public void testOnEventThrowsIfInputMissing() throws Exception {
        Bundle params = new Bundle();
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        mBinder.onRequest(Constants.OP_WEB_VIEW_EVENT, params, new TestServiceCallback());
        mLatch.await();
        assertEquals(Constants.STATUS_INTERNAL_ERROR, mCallbackErrorCode);
    }

    @Test
    public void testOnEventThrowsIfDataAccessServiceMissing() throws Exception {
        Bundle params = new Bundle();
        params.putParcelable(
                Constants.EXTRA_INPUT,
                new EventInputParcel.Builder().setParameters(PersistableBundle.EMPTY).build());
        mBinder.onRequest(Constants.OP_WEB_VIEW_EVENT, params, new TestServiceCallback());
        mLatch.await();
        assertEquals(Constants.STATUS_INTERNAL_ERROR, mCallbackErrorCode);
    }

    @Test
    public void testOnEventThrowsIfCallbackMissing() throws Exception {
        Bundle params = new Bundle();
        params.putParcelable(
                Constants.EXTRA_INPUT,
                new EventInputParcel.Builder().setParameters(PersistableBundle.EMPTY).build());
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        assertThrows(
                NullPointerException.class,
                () -> {
                    mBinder.onRequest(Constants.OP_WEB_VIEW_EVENT, params, null);
                });
    }

    @Test
    public void testOnTrainingExamples() throws Exception {
        JoinedLogRecord joinedLogRecord = new JoinedLogRecord.Builder().build();
        TrainingExamplesInputParcel input =
                new TrainingExamplesInputParcel.Builder()
                        .setPopulationName("")
                        .setTaskName("")
                        .setResumptionToken(new byte[] {0})
                        .build();
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_INPUT, input);
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        mBinder.onRequest(Constants.OP_TRAINING_EXAMPLE, params, new TestServiceCallback());
        mLatch.await();
        assertTrue(mOnTrainingExampleCalled);
        TrainingExamplesOutputParcel result =
                mCallbackResult.getParcelable(
                        Constants.EXTRA_RESULT, TrainingExamplesOutputParcel.class);
        List<TrainingExampleRecord> examples = result.getTrainingExampleRecords().getList();
        assertThat(examples).hasSize(1);
        assertArrayEquals(new byte[] {12}, examples.get(0).getTrainingExample());
        assertArrayEquals(new byte[] {13}, examples.get(0).getResumptionToken());
    }

    @Test
    public void testOnTrainingExampleThrowsIfParamsMissing() throws Exception {
        assertThrows(
                NullPointerException.class,
                () -> {
                    mBinder.onRequest(
                            Constants.OP_TRAINING_EXAMPLE, null, new TestServiceCallback());
                });
    }

    @Test
    public void testOnTrainingExampleThrowsIfDataAccessServiceMissing() throws Exception {
        JoinedLogRecord joinedLogRecord = new JoinedLogRecord.Builder().build();
        TrainingExamplesInputParcel input =
                new TrainingExamplesInputParcel.Builder()
                        .setPopulationName("")
                        .setTaskName("")
                        .setResumptionToken(new byte[] {0})
                        .build();
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_INPUT, input);
        mBinder.onRequest(Constants.OP_TRAINING_EXAMPLE, params, new TestServiceCallback());
        mLatch.await();
        assertEquals(Constants.STATUS_INTERNAL_ERROR, mCallbackErrorCode);
    }

    @Test
    public void testOnTrainingExampleThrowsIfCallbackMissing() throws Exception {
        JoinedLogRecord joinedLogRecord = new JoinedLogRecord.Builder().build();
        TrainingExamplesInputParcel input =
                new TrainingExamplesInputParcel.Builder()
                        .setPopulationName("")
                        .setTaskName("")
                        .setResumptionToken(new byte[] {0})
                        .build();
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_INPUT, input);
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        mBinder.onRequest(Constants.OP_TRAINING_EXAMPLE, params, new TestServiceCallback());
        assertThrows(
                NullPointerException.class,
                () -> {
                    mBinder.onRequest(Constants.OP_TRAINING_EXAMPLE, params, null);
                });
    }

    @Test
    public void testOnWebTrigger() throws Exception {
        WebTriggerInputParcel input =
                new WebTriggerInputParcel.Builder(
                        Uri.parse("http://desturl"),
                        "com.browser",
                        new byte[] {1, 2, 3})
                    .build();
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_INPUT, input);
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        params.putBinder(Constants.EXTRA_MODEL_SERVICE_BINDER, new TestIsolatedModelService());
        mBinder.onRequest(Constants.OP_WEB_TRIGGER, params, new TestServiceCallback());
        mLatch.await();
        assertTrue(mOnWebTriggerCalled);
        WebTriggerOutputParcel result =
                mCallbackResult.getParcelable(Constants.EXTRA_RESULT, WebTriggerOutputParcel.class);
        assertEquals(5, result.getRequestLogRecord().getRows().get(0).getAsInteger("a").intValue());
    }

    @Test
    public void testOnWebTriggerPropagatesError() throws Exception {
        WebTriggerInputParcel input =
                new WebTriggerInputParcel.Builder(
                        Uri.parse("http://error"),
                        "com.browser",
                        new byte[] {1, 2, 3})
                    .build();
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_INPUT, input);
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        params.putBinder(Constants.EXTRA_MODEL_SERVICE_BINDER, new TestIsolatedModelService());
        mBinder.onRequest(Constants.OP_WEB_TRIGGER, params, new TestServiceCallback());
        mLatch.await();
        assertTrue(mOnWebTriggerCalled);
        assertEquals(Constants.STATUS_SERVICE_FAILED, mCallbackErrorCode);
    }

    @Test
    public void testOnWebTriggerThrowsIfParamsMissing() throws Exception {
        assertThrows(
                NullPointerException.class,
                () -> {
                    mBinder.onRequest(Constants.OP_WEB_TRIGGER, null, new TestServiceCallback());
                });
    }

    @Test
    public void testOnWebTriggerThrowsIfInputMissing() throws Exception {
        Bundle params = new Bundle();
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        mBinder.onRequest(Constants.OP_WEB_TRIGGER, params, new TestServiceCallback());
        mLatch.await();
        assertEquals(Constants.STATUS_INTERNAL_ERROR, mCallbackErrorCode);
    }

    @Test
    public void testOnWebTriggerThrowsIfDataAccessServiceMissing() throws Exception {
        WebTriggerInputParcel input =
                new WebTriggerInputParcel.Builder(
                        Uri.parse("http://desturl"),
                        "com.browser",
                        new byte[] {1, 2, 3})
                    .build();
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_INPUT, input);
        mBinder.onRequest(Constants.OP_WEB_TRIGGER, params, new TestServiceCallback());
        mLatch.await();
        assertEquals(Constants.STATUS_INTERNAL_ERROR, mCallbackErrorCode);
    }

    @Test
    public void testOnWebTriggerThrowsIfCallbackMissing() throws Exception {
        WebTriggerInputParcel input =
                new WebTriggerInputParcel.Builder(
                        Uri.parse("http://desturl"),
                        "com.browser",
                        new byte[] {1, 2, 3})
                    .build();
        Bundle params = new Bundle();
        params.putParcelable(Constants.EXTRA_INPUT, input);
        params.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        assertThrows(
                NullPointerException.class,
                () -> {
                    mBinder.onRequest(Constants.OP_WEB_TRIGGER, params, null);
                });
    }

    static class TestDataAccessService extends IDataAccessService.Stub {
        @Override
        public void onRequest(int operation, Bundle params, IDataAccessServiceCallback callback) {}
    }

    static class TestFederatedComputeService extends IFederatedComputeService.Stub {
        @Override
        public void schedule(TrainingOptions trainingOptions, IFederatedComputeCallback callback) {}

        public void cancel(String populationName, IFederatedComputeCallback callback) {}
    }

    class TestWorker implements IsolatedWorker {
        @Override
        public void onExecute(
                ExecuteInput input,
                OutcomeReceiver<ExecuteOutput, IsolatedServiceException> receiver) {
            mOnExecuteCalled = true;
            if (input.getAppParams() != null && input.getAppParams().getInt("error") > 0) {
                receiver.onError(new IsolatedServiceException(1));
            } else {
                ContentValues row = new ContentValues();
                row.put("a", 5);
                receiver.onResult(
                        new ExecuteOutput.Builder()
                                .setRequestLogRecord(
                                        new RequestLogRecord.Builder().addRow(row).build())
                                .setRenderingConfig(
                                        new RenderingConfig.Builder().addKey("123").build())
                                .build());
            }
        }

        @Override
        public void onDownloadCompleted(
                DownloadCompletedInput input,
                OutcomeReceiver<DownloadCompletedOutput, IsolatedServiceException> receiver) {
            mOnDownloadCalled = true;
            receiver.onResult(new DownloadCompletedOutput.Builder().addRetainedKey("12").build());
        }

        @Override
        public void onRender(
                RenderInput input,
                OutcomeReceiver<RenderOutput, IsolatedServiceException> receiver) {
            mOnRenderCalled = true;
            if (input.getRenderingConfig().getKeys().size() >= 1
                    && input.getRenderingConfig().getKeys().get(0).equals("z")) {
                receiver.onError(new IsolatedServiceException(1));
            } else {
                receiver.onResult(new RenderOutput.Builder().setContent("htmlstring").build());
            }
        }

        @Override
        public void onEvent(
                EventInput input,
                OutcomeReceiver<EventOutput, IsolatedServiceException> receiver) {
            mOnEventCalled = true;
            int eventType = input.getParameters().getInt(EVENT_TYPE_KEY);
            if (eventType == 9999) {
                receiver.onError(new IsolatedServiceException(1));
            } else {
                receiver.onResult(
                        new EventOutput.Builder()
                                .setEventLogRecord(
                                        new EventLogRecord.Builder()
                                                .setType(1)
                                                .setRowIndex(2)
                                                .setData(new ContentValues())
                                                .build())
                                .build());
            }
        }

        @Override
        public void onTrainingExamples(
                TrainingExamplesInput input,
                OutcomeReceiver<TrainingExamplesOutput, IsolatedServiceException> receiver) {
            mOnTrainingExampleCalled = true;
            List<TrainingExampleRecord> exampleRecordList = new ArrayList<>();
            TrainingExampleRecord record =
                    new TrainingExampleRecord.Builder()
                            .setTrainingExample(new byte[] {12})
                            .setResumptionToken(new byte[] {13})
                            .build();
            exampleRecordList.add(record);
            receiver.onResult(
                    new TrainingExamplesOutput.Builder()
                            .setTrainingExampleRecords(exampleRecordList)
                            .build());
        }

        @Override
        public void onWebTrigger(
                WebTriggerInput input,
                OutcomeReceiver<WebTriggerOutput, IsolatedServiceException> receiver) {
            mOnWebTriggerCalled = true;
            if (input.getDestinationUrl().toString().equals("http://error")) {
                receiver.onError(new IsolatedServiceException(1));
            } else {
                ContentValues row = new ContentValues();
                row.put("a", 5);
                receiver.onResult(
                        new WebTriggerOutput.Builder()
                            .setRequestLogRecord(
                                    new RequestLogRecord.Builder().addRow(row).build())
                            .build());
            }
        }
    }

    class TestService extends IsolatedService {
        @Override
        public IsolatedWorker onRequest(RequestToken token) {
            return new TestWorker();
        }
    }

    class TestServiceCallback extends IIsolatedServiceCallback.Stub {
        @Override
        public void onSuccess(Bundle result) {
            mCallbackResult = result;
            mLatch.countDown();
        }

        @Override
        public void onError(int errorCode, int isolatedServiceErrorCode) {
            mCallbackErrorCode = errorCode;
            mIsolatedServiceErrorCode = isolatedServiceErrorCode;
            mLatch.countDown();
        }
    }

    class TestIsolatedModelService extends IIsolatedModelService.Stub {
        @Override
        public void runInference(Bundle params, IIsolatedModelServiceCallback callback) {}
    }
}

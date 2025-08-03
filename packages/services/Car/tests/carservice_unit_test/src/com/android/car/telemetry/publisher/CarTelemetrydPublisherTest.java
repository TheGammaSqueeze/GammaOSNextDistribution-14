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

package com.android.car.telemetry.publisher;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.Nullable;
import android.automotive.telemetry.internal.CarDataInternal;
import android.automotive.telemetry.internal.ICarDataListener;
import android.automotive.telemetry.internal.ICarTelemetryInternal;
import android.car.telemetry.TelemetryProto;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.os.IBinder;
import android.os.Looper;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.ArraySet;

import com.android.car.CarLog;
import com.android.car.telemetry.databroker.DataSubscriber;
import com.android.car.telemetry.sessioncontroller.SessionAnnotation;
import com.android.car.telemetry.sessioncontroller.SessionController;
import com.android.car.test.FakeHandlerWrapper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RunWith(MockitoJUnitRunner.class)
public class CarTelemetrydPublisherTest extends AbstractExtendedMockitoTestCase {
    private static final String SERVICE_NAME = ICarTelemetryInternal.DESCRIPTOR + "/default";
    private static final int CAR_DATA_ID_1 = 1;
    private static final TelemetryProto.Publisher PUBLISHER_PARAMS_1 =
            TelemetryProto.Publisher.newBuilder()
                    .setCartelemetryd(TelemetryProto.CarTelemetrydPublisher.newBuilder()
                            .setId(CAR_DATA_ID_1))
                    .build();
    private static final SessionAnnotation SESSION_ANNOTATION_BEGIN_1 =
            new SessionAnnotation(1, SessionController.STATE_ENTER_DRIVING_SESSION, 0, 0, "", 0);
    private static final String[] SESSION_ANNOTATION_KEYS =
            {Constants.ANNOTATION_BUNDLE_KEY_SESSION_ID,
                    Constants.ANNOTATION_BUNDLE_KEY_BOOT_REASON,
                    Constants.ANNOTATION_BUNDLE_KEY_SESSION_STATE,
                    Constants.ANNOTATION_BUNDLE_KEY_CREATED_AT_MILLIS,
                    Constants.ANNOTATION_BUNDLE_KEY_CREATED_AT_SINCE_BOOT_MILLIS};

    private final FakeHandlerWrapper mFakeHandlerWrapper =
            new FakeHandlerWrapper(Looper.getMainLooper(), FakeHandlerWrapper.Mode.IMMEDIATE);
    private final FakePublisherListener mFakePublisherListener = new FakePublisherListener();

    @Mock
    private IBinder mMockBinder;
    @Mock
    private DataSubscriber mMockDataSubscriber;
    @Mock
    private SessionController mMockSessionController;

    @Captor
    private ArgumentCaptor<IBinder.DeathRecipient> mLinkToDeathCallbackCaptor;
    @Captor
    private ArgumentCaptor<PersistableBundle> mBundleCaptor;

    private FakeCarTelemetryInternal mFakeCarTelemetryInternal;
    private CarTelemetrydPublisher mPublisher;

    public CarTelemetrydPublisherTest() {
        super(CarLog.TAG_TELEMETRY);
    }

    private CarDataInternal buildCarDataInternal(int id, byte[] content) {
        CarDataInternal data = new CarDataInternal();
        data.id = id;
        data.content = content;
        return data;
    }

    @Before
    public void setUp() throws Exception {
        mPublisher = new CarTelemetrydPublisher(
                mFakePublisherListener, mFakeHandlerWrapper.getMockHandler(),
                mMockSessionController);
        mFakeCarTelemetryInternal = new FakeCarTelemetryInternal(mMockBinder);
        when(mMockDataSubscriber.getPublisherParam()).thenReturn(PUBLISHER_PARAMS_1);
        when(mMockBinder.queryLocalInterface(any())).thenReturn(mFakeCarTelemetryInternal);
        when(mMockSessionController.getSessionAnnotation()).thenReturn(SESSION_ANNOTATION_BEGIN_1);
        doNothing().when(mMockBinder).linkToDeath(mLinkToDeathCallbackCaptor.capture(), anyInt());
        doReturn(mMockBinder).when(() -> ServiceManager.checkService(SERVICE_NAME));
    }

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder builder) {
        builder.spyStatic(ServiceManager.class);
    }

    @Test
    public void testAddDataSubscriber_registersNewListener() {
        mPublisher.addDataSubscriber(mMockDataSubscriber);

        assertThat(mFakeCarTelemetryInternal.mListener).isNotNull();
        assertThat(mPublisher.isConnectedToCarTelemetryd()).isTrue();
        assertThat(mPublisher.hasDataSubscriber(mMockDataSubscriber)).isTrue();
        assertThat(mFakeCarTelemetryInternal.mCarDataIds).containsExactly(CAR_DATA_ID_1);
    }

    @Test
    public void testAddDataSubscriber_withInvalidId_fails() {
        DataSubscriber invalidDataSubscriber = Mockito.mock(DataSubscriber.class);
        when(invalidDataSubscriber.getPublisherParam()).thenReturn(
                TelemetryProto.Publisher.newBuilder()
                        .setCartelemetryd(TelemetryProto.CarTelemetrydPublisher.newBuilder()
                                .setId(42000))  // invalid ID
                        .build());

        Throwable error = assertThrows(IllegalArgumentException.class,
                () -> mPublisher.addDataSubscriber(invalidDataSubscriber));

        assertThat(error).hasMessageThat().contains("Invalid CarData ID");
        assertThat(mFakeCarTelemetryInternal.mListener).isNull();
        assertThat(mPublisher.isConnectedToCarTelemetryd()).isFalse();
        assertThat(mPublisher.hasDataSubscriber(invalidDataSubscriber)).isFalse();
    }

    @Test
    public void testRemoveDataSubscriber_ignoresIfNotFound() {
        mPublisher.removeDataSubscriber(mMockDataSubscriber);
    }

    @Test
    public void testRemoveDataSubscriber_removesOnlySingleSubscriber() {
        DataSubscriber subscriber2 = Mockito.mock(DataSubscriber.class);
        when(subscriber2.getPublisherParam()).thenReturn(PUBLISHER_PARAMS_1);
        mPublisher.addDataSubscriber(mMockDataSubscriber);
        mPublisher.addDataSubscriber(subscriber2);

        mPublisher.removeDataSubscriber(subscriber2);

        assertThat(mPublisher.hasDataSubscriber(mMockDataSubscriber)).isTrue();
        assertThat(mPublisher.hasDataSubscriber(subscriber2)).isFalse();
        assertThat(mFakeCarTelemetryInternal.mListener).isNotNull();
    }

    @Test
    public void testRemoveDataSubscriber_disconnectsFromICarTelemetry() {
        mPublisher.addDataSubscriber(mMockDataSubscriber);

        mPublisher.removeDataSubscriber(mMockDataSubscriber);

        assertThat(mPublisher.hasDataSubscriber(mMockDataSubscriber)).isFalse();
        assertThat(mFakeCarTelemetryInternal.mListener).isNull();
    }

    @Test
    public void testRemoveAllDataSubscribers_succeeds() {
        DataSubscriber subscriber2 = Mockito.mock(DataSubscriber.class);
        when(subscriber2.getPublisherParam()).thenReturn(PUBLISHER_PARAMS_1);
        mPublisher.addDataSubscriber(mMockDataSubscriber);
        mPublisher.addDataSubscriber(subscriber2);

        mPublisher.removeAllDataSubscribers();

        assertThat(mPublisher.hasDataSubscriber(mMockDataSubscriber)).isFalse();
        assertThat(mPublisher.hasDataSubscriber(subscriber2)).isFalse();
        assertThat(mFakeCarTelemetryInternal.mListener).isNull();
    }

    @Test
    public void testNotifiesFailureConsumer_whenBinderDies() {
        mPublisher.addDataSubscriber(mMockDataSubscriber);

        mLinkToDeathCallbackCaptor.getValue().binderDied();

        assertThat(mFakeCarTelemetryInternal.mSetListenerCallCount).isEqualTo(1);
        assertThat(mFakePublisherListener.mPublisherFailure).hasMessageThat()
                .contains("ICarTelemetryInternal binder died");
        assertThat(mFakePublisherListener.mFailedConfigs).hasSize(1);  // got all the failed configs
    }

    @Test
    public void testNotifiesFailureConsumer_whenFailsConnectToService() {
        mFakeCarTelemetryInternal.setApiFailure(new RemoteException("tough life"));

        mPublisher.addDataSubscriber(mMockDataSubscriber);

        assertThat(mFakePublisherListener.mPublisherFailure).hasMessageThat()
                .contains("Cannot set CarData listener");
        assertThat(mFakePublisherListener.mFailedConfigs).hasSize(1);
    }

    @Test
    public void testPushesPublishedData_whenOnCarDataReceived() throws RemoteException {
        mPublisher.addDataSubscriber(mMockDataSubscriber);

        mFakeCarTelemetryInternal.mListener.onCarDataReceived(
                new CarDataInternal[]{buildCarDataInternal(CAR_DATA_ID_1, new byte[]{55, 66, 77})});

        // Also verifies that the published data is not large.
        verify(mMockDataSubscriber).push(mBundleCaptor.capture(), eq(false));
        PersistableBundle result = mBundleCaptor.getValue();
        // Verify published contents.
        assertThat(result.getInt(
                Constants.CAR_TELEMETRYD_BUNDLE_KEY_ID)).isEqualTo(
                CAR_DATA_ID_1);
        assertThat(result.getString(
                Constants.CAR_TELEMETRYD_BUNDLE_KEY_CONTENT)).isEqualTo("7BM");
        // Verify session annotations are also present.
        assertThat(result.keySet()).containsAtLeastElementsIn(SESSION_ANNOTATION_KEYS);
    }

    @Test
    public void testPushesPublishedData_multipleData() throws RemoteException {
        mPublisher.addDataSubscriber(mMockDataSubscriber);

        mFakeCarTelemetryInternal.mListener.onCarDataReceived(
                new CarDataInternal[]{buildCarDataInternal(CAR_DATA_ID_1, new byte[]{1, 2, 3}),
                        buildCarDataInternal(CAR_DATA_ID_1, new byte[]{3, 2, 1})});

        verify(mMockDataSubscriber, times(2)).push(mBundleCaptor.capture(),
                anyBoolean());
    }

    @Test
    public void testPushesPublishedData_multipleSubscribers() throws RemoteException {
        DataSubscriber subscriber2 = Mockito.mock(DataSubscriber.class);
        when(subscriber2.getPublisherParam()).thenReturn(PUBLISHER_PARAMS_1);
        mPublisher.addDataSubscriber(mMockDataSubscriber);
        mPublisher.addDataSubscriber(subscriber2);

        mFakeCarTelemetryInternal.mListener.onCarDataReceived(
                new CarDataInternal[]{buildCarDataInternal(CAR_DATA_ID_1, new byte[]{41, 52, 63}),
                        buildCarDataInternal(CAR_DATA_ID_1, new byte[]{53, 62, 71}),
                        buildCarDataInternal(CAR_DATA_ID_1, new byte[]{40, 50, 60})});

        verify(mMockDataSubscriber, times(3)).push(mBundleCaptor.capture(),
                eq(false));
        List<PersistableBundle> telemetryDataList = mBundleCaptor.getAllValues();
        // Verify published contents.
        assertThat(telemetryDataList.get(0).getInt(
                Constants.CAR_TELEMETRYD_BUNDLE_KEY_ID)).isEqualTo(
                CAR_DATA_ID_1);
        assertThat(telemetryDataList.get(0).getString(
                Constants.CAR_TELEMETRYD_BUNDLE_KEY_CONTENT)).isEqualTo(")4?");
        // Verify session annotations are also present.
        assertThat(telemetryDataList.get(0).keySet()).containsAtLeastElementsIn(
                SESSION_ANNOTATION_KEYS);


        // Verify published contents.
        assertThat(telemetryDataList.get(1).getInt(
                Constants.CAR_TELEMETRYD_BUNDLE_KEY_ID)).isEqualTo(
                CAR_DATA_ID_1);
        assertThat(telemetryDataList.get(1).getString(
                Constants.CAR_TELEMETRYD_BUNDLE_KEY_CONTENT)).isEqualTo("5>G");
        // Verify session annotations are also present.
        assertThat(telemetryDataList.get(1).keySet()).containsAtLeastElementsIn(
                SESSION_ANNOTATION_KEYS);


        // Verify published contents.
        assertThat(telemetryDataList.get(2).getInt(
                Constants.CAR_TELEMETRYD_BUNDLE_KEY_ID)).isEqualTo(
                CAR_DATA_ID_1);
        assertThat(telemetryDataList.get(2).getString(
                Constants.CAR_TELEMETRYD_BUNDLE_KEY_CONTENT)).isEqualTo("(2<");
        // Verify session annotations are also present.
        assertThat(telemetryDataList.get(2).keySet()).containsAtLeastElementsIn(
                SESSION_ANNOTATION_KEYS);


        // Verify that the other subscriber received the same data.
        verify(subscriber2, times(3)).push(mBundleCaptor.capture(), eq(false));
        telemetryDataList = mBundleCaptor.getAllValues();
        // Verify published contents.
        assertThat(telemetryDataList.get(0).getInt(
                Constants.CAR_TELEMETRYD_BUNDLE_KEY_ID)).isEqualTo(
                CAR_DATA_ID_1);
        assertThat(telemetryDataList.get(0).getString(
                Constants.CAR_TELEMETRYD_BUNDLE_KEY_CONTENT)).isEqualTo(")4?");
        // Verify session annotations are also present.
        assertThat(telemetryDataList.get(0).keySet()).containsAtLeastElementsIn(
                SESSION_ANNOTATION_KEYS);


        // Verify published contents.
        assertThat(telemetryDataList.get(1).getInt(
                Constants.CAR_TELEMETRYD_BUNDLE_KEY_ID)).isEqualTo(
                CAR_DATA_ID_1);
        assertThat(telemetryDataList.get(1).getString(
                Constants.CAR_TELEMETRYD_BUNDLE_KEY_CONTENT)).isEqualTo("5>G");
        // Verify session annotations are also present.
        assertThat(telemetryDataList.get(1).keySet()).containsAtLeastElementsIn(
                SESSION_ANNOTATION_KEYS);


        // Verify published contents.
        assertThat(telemetryDataList.get(2).getInt(
                Constants.CAR_TELEMETRYD_BUNDLE_KEY_ID)).isEqualTo(
                CAR_DATA_ID_1);
        assertThat(telemetryDataList.get(2).getString(
                Constants.CAR_TELEMETRYD_BUNDLE_KEY_CONTENT)).isEqualTo("(2<");
        // Verify session annotations are also present.
        assertThat(telemetryDataList.get(2).keySet()).containsAtLeastElementsIn(
                SESSION_ANNOTATION_KEYS);
    }

    @Test
    public void testPushesPublishedData_noMatchingSubscribers() throws RemoteException {
        mPublisher.addDataSubscriber(mMockDataSubscriber);

        mFakeCarTelemetryInternal.mListener.onCarDataReceived(
                new CarDataInternal[]{buildCarDataInternal(10, new byte[]{1, 2, 3}),
                        buildCarDataInternal(20, new byte[]{3, 2, 1}),
                        buildCarDataInternal(2000, new byte[]{30, 20, 10})});

        // No subscribers are called because the generated data ids 30, 100, 2000 are not
        // subscribed to.
        verify(mMockDataSubscriber, never()).push(mBundleCaptor.capture(), anyBoolean());
    }

    @Test
    public void testPushesPublishedData_detectsLargeData() throws RemoteException {
        mPublisher.addDataSubscriber(mMockDataSubscriber);

        mFakeCarTelemetryInternal.mListener.onCarDataReceived(new CarDataInternal[]{
                buildCarDataInternal(CAR_DATA_ID_1,
                        new byte[DataSubscriber.SCRIPT_INPUT_SIZE_THRESHOLD_BYTES + 1])});

        // Also verifies that the published data is large.
        verify(mMockDataSubscriber).push(mBundleCaptor.capture(), eq(true));
        PersistableBundle result = mBundleCaptor.getValue();
        // Verify published contents.
        assertThat(result.getInt(
                Constants.CAR_TELEMETRYD_BUNDLE_KEY_ID)).isEqualTo(
                CAR_DATA_ID_1);
        assertThat(result.getString(
                Constants.CAR_TELEMETRYD_BUNDLE_KEY_CONTENT).length())
                .isEqualTo(DataSubscriber.SCRIPT_INPUT_SIZE_THRESHOLD_BYTES + 1);
        // Verify session annotations are also present.
        assertThat(result.keySet()).containsAtLeastElementsIn(SESSION_ANNOTATION_KEYS);
    }


    private static class FakeCarTelemetryInternal implements ICarTelemetryInternal {
        private final IBinder mBinder;
        @Nullable
        ICarDataListener mListener;
        int mSetListenerCallCount = 0;
        @Nullable
        private RemoteException mApiFailure = null;
        private Set<Integer> mCarDataIds = new ArraySet<>();

        FakeCarTelemetryInternal(IBinder binder) {
            mBinder = binder;
        }

        @Override
        public IBinder asBinder() {
            return mBinder;
        }

        @Override
        public void setListener(ICarDataListener listener) throws RemoteException {
            mSetListenerCallCount += 1;
            if (mApiFailure != null) {
                throw mApiFailure;
            }
            mListener = listener;
        }

        @Override
        public void clearListener() throws RemoteException {
            if (mApiFailure != null) {
                throw mApiFailure;
            }
            mListener = null;
        }

        @Override
        public void addCarDataIds(int[] ids) throws RemoteException {
            mCarDataIds.addAll(Arrays.stream(ids).boxed().collect(Collectors.toList()));
        }

        @Override
        public void removeCarDataIds(int[] ids) throws RemoteException {
            mCarDataIds.removeAll(Arrays.stream(ids).boxed().collect(Collectors.toList()));
        }

        void setApiFailure(RemoteException e) {
            mApiFailure = e;
        }

        @Override
        public String getInterfaceHash() {
            return ICarTelemetryInternal.HASH;
        }

        @Override
        public int getInterfaceVersion() {
            return ICarTelemetryInternal.VERSION;
        }
    }
}

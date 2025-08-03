/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.car.telemetry;

import static android.car.telemetry.CarTelemetryManager.STATUS_GET_METRICS_CONFIG_FINISHED;
import static android.car.telemetry.CarTelemetryManager.STATUS_GET_METRICS_CONFIG_RUNTIME_ERROR;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.car.Car;
import android.car.telemetry.CarTelemetryManager;
import android.car.telemetry.ICarTelemetryReportListener;
import android.car.telemetry.TelemetryProto;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;

import com.android.car.CarLocalServices;
import com.android.car.ICarImpl;
import com.android.car.MockedCarTestBase;

import org.junit.Test;
import org.mockito.Mock;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.concurrent.Executor;

/**
 * Tests public entry points for CarTelemetryManager. This class spies on CarTelemetryService.
 * This class is separate from CarTelemetryManagerTest because the spying on the service
 * somehow prevents the real (un-mocked) methods from being called, which causes some tests to
 * be flaky.
 * Tests that use a real CarTelemetryService should be in CarTelemetryManagerTest.
 * Tests that use a spied CarTelemetryService should be in CarTelemetryManagerSpyServiceTest.
 */
public class CarTelemetryManagerSpyServiceTest extends MockedCarTestBase {

    private static final Executor DIRECT_EXECUTOR = Runnable::run;
    private static final String CONFIG_NAME = "my_metrics_config";

    private CarTelemetryManager mManager; // subject
    private CarTelemetryService mService; // spy
    private ParcelFileDescriptor[] mParcelFileDescriptors;

    @Mock
    private CarTelemetryManager.MetricsReportCallback mMockMetricsReportCallback;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        assumeTrue(getCar().isFeatureEnabled(Car.CAR_TELEMETRY_SERVICE));

        mManager = (CarTelemetryManager) getCar().getCarManager(
                Car.CAR_TELEMETRY_SERVICE);

        mParcelFileDescriptors = ParcelFileDescriptor.createPipe();
    }

    @Override
    protected void spyOnBeforeCarImplInit(ICarImpl carImpl) {
        mService = CarLocalServices.getService(CarTelemetryService.class);
        assertThat(mService).isNotNull();
        spyOn(mService);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        mParcelFileDescriptors[0].close();
        mParcelFileDescriptors[1].close();
    }

    @Override
    protected CarTelemetryService createCarTelemetryService() {
        // Forces the base class implementation to instantiate real instance of
        // CarTelemetryService in ICarImpl.
        return null;
    }

    @Test
    public void testGetFinishedReport_multipleReports() throws Exception {
        // set up the report and convert it to byte array
        PersistableBundle report = new PersistableBundle();
        report.putString("this is", "a test");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        report.writeToStream(bos);
        byte[] reportBytes = bos.toByteArray();
        // set up service so that it streams 3 reports
        doAnswer((invocation) -> {
            DataOutputStream dos = new DataOutputStream(
                    new ParcelFileDescriptor.AutoCloseOutputStream(mParcelFileDescriptors[1]));
            for (int i = 0; i < 3; i++) {
                dos.writeInt(reportBytes.length);
                dos.write(reportBytes);
            }
            dos.close();
            ICarTelemetryReportListener listener = invocation.getArgument(/* index= */ 1);
            listener.onResult(CONFIG_NAME, mParcelFileDescriptors[0], null,
                    STATUS_GET_METRICS_CONFIG_FINISHED);
            return null;
        }).when(mService).getFinishedReport(any(), any());

        mManager.getFinishedReport(
                CONFIG_NAME, DIRECT_EXECUTOR, mMockMetricsReportCallback);

        verify(mMockMetricsReportCallback, times(3)).onResult(
                eq(CONFIG_NAME),
                refEq(report),
                isNull(),
                eq(STATUS_GET_METRICS_CONFIG_FINISHED));
    }

    @Test
    public void testGetFinishedReport_validFdButNoReport() {
        // set up service so that it streams 0 report
        doAnswer((invocation) -> {
            mParcelFileDescriptors[1].close();
            ICarTelemetryReportListener listener = invocation.getArgument(/* index= */ 1);
            listener.onResult(CONFIG_NAME, mParcelFileDescriptors[0], null,
                    STATUS_GET_METRICS_CONFIG_FINISHED);
            return null;
        }).when(mService).getFinishedReport(any(), any());

        mManager.getFinishedReport(
                CONFIG_NAME, DIRECT_EXECUTOR, mMockMetricsReportCallback);

        verify(mMockMetricsReportCallback).onResult(
                eq(CONFIG_NAME),
                isNull(),
                isNull(),
                eq(STATUS_GET_METRICS_CONFIG_RUNTIME_ERROR));
    }

    @Test
    public void testGetFinishedReport_telemetryError() {
        byte[] errorBytes = TelemetryProto.TelemetryError.getDefaultInstance().toByteArray();
        // setup service so that it calls back with telemetry error
        doAnswer((invocation) -> {
            ICarTelemetryReportListener listener = invocation.getArgument(/* index= */ 1);
            listener.onResult(CONFIG_NAME, null, errorBytes,
                    STATUS_GET_METRICS_CONFIG_RUNTIME_ERROR);
            return null;
        }).when(mService).getFinishedReport(any(), any());

        mManager.getFinishedReport(
                CONFIG_NAME, DIRECT_EXECUTOR, mMockMetricsReportCallback);

        verify(mMockMetricsReportCallback).onResult(
                eq(CONFIG_NAME),
                isNull(),
                eq(errorBytes),
                eq(STATUS_GET_METRICS_CONFIG_RUNTIME_ERROR));
    }

    @Test
    public void testGetFinishedReport_badFd_shouldReturnRuntimeErrorStatus() throws Exception {
        // close the read end of the pipe so CarTelemetryManager fails to read from it
        mParcelFileDescriptors[0].close();
        doAnswer((invocation) -> {
            ICarTelemetryReportListener listener = invocation.getArgument(/* index= */ 1);
            listener.onResult(CONFIG_NAME, mParcelFileDescriptors[0], null,
                    STATUS_GET_METRICS_CONFIG_FINISHED);
            return null;
        }).when(mService).getFinishedReport(any(), any());

        mManager.getFinishedReport(
                CONFIG_NAME, DIRECT_EXECUTOR, mMockMetricsReportCallback);

        verify(mMockMetricsReportCallback, never()).onResult(any(), any(), any(), anyInt());
    }
}

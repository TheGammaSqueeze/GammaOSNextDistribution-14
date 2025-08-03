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

package com.android.nn.host.cts;

import com.android.tradefed.util.RunUtil;
import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.os.AtomsProto.Atom;
import com.android.os.AtomsProto.NeuralNetworksCompilationCompleted;
import com.android.os.AtomsProto.NeuralNetworksCompilationFailed;
import com.android.os.AtomsProto.NeuralNetworksExecutionCompleted;
import com.android.os.AtomsProto.NeuralNetworksExecutionFailed;
import com.android.os.StatsLog.EventMetricData;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import java.util.List;

public class NeuralNetworksStatsTests extends DeviceTestCase implements IBuildReceiver {
    private static final String APP_APK_NAME = "CtsNnapiStatsdAtomApp.apk";
    private static final String APP_PKG_NAME = "com.android.nn.stats.app";
    private static final String APP_CLASS_NAME = "NnapiDeviceActivity";
    private static final String NNAPI_TELEMETRY_FEATURE_NAMESPACE = "nnapi_native";
    private static final String NNAPI_TELEMETRY_FEATURE_KEY = "telemetry_enable";

    private IBuildInfo mCtsBuild;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.installTestApp(getDevice(), APP_APK_NAME, APP_PKG_NAME, mCtsBuild);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
    }

    @Override
    protected void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.uninstallTestApp(getDevice(), APP_PKG_NAME);
        super.tearDown();
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    private boolean isNnapiLoggingEnabled() throws Exception {
        String prop = DeviceUtils.getDeviceConfigFeature(getDevice(),
                NNAPI_TELEMETRY_FEATURE_NAMESPACE, NNAPI_TELEMETRY_FEATURE_KEY);
        if (prop == null) return false;

        // Possible "true" values from android-base/parsebool.h.
        return prop.equals("1") || prop.equals("y") || prop.equals("yes") || prop.equals("on")
                || prop.equals("true");
    }

    public void testAppNeuralNetworksCompilationCompletedNative() throws Exception {
        if (!isNnapiLoggingEnabled()) return;

        final int atomTag = Atom.NEURALNETWORKS_COMPILATION_COMPLETED_FIELD_NUMBER;
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), APP_PKG_NAME,
                atomTag,  /*uidInAttributionChain=*/false);

        DeviceUtils.runActivity(getDevice(), APP_PKG_NAME, APP_CLASS_NAME, null, null,
                /* waitTimeMs= */ 5000L);

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        assertTrue(data.size() > 0);
        NeuralNetworksCompilationCompleted atom = data.get(0).getAtom()
                .getNeuralnetworksCompilationCompleted();
        // UID should belong to the run activity, not any system service.
        assertThat(atom.getUid()).isGreaterThan(10000);
        // atom.getSessionId() can have any value
        // atom.getVersionNnapiModule() can have any value
        assertThat(atom.getModelArchHash()).hasSize(32);
        assertThat(atom.getDeviceId()).isNotEmpty();
        assertThat(atom.getInputDataClass())
                .isEqualTo(android.neuralnetworks.Enums.DataClass.DATA_CLASS_FLOAT32);
        assertThat(atom.getOutputDataClass())
                .isEqualTo(android.neuralnetworks.Enums.DataClass.DATA_CLASS_FLOAT32);
        // atom.getFallbackToCpuFromError() can have any value
        assertFalse(atom.getIntrospectionEnabled());
        assertFalse(atom.getCacheEnabled());
        assertFalse(atom.getHasControlFlow());
        assertFalse(atom.getHasDynamicTemporaries());
        assertThat(atom.getCompilationTimeSumMillis()).isAtLeast(0);
        assertThat(atom.getCompilationTimeMinMillis()).isAtLeast(0);
        assertThat(atom.getCompilationTimeMaxMillis()).isAtLeast(0);
        assertThat(atom.getCompilationTimeMinMillis()).isAtMost(atom.getCompilationTimeMaxMillis());
        assertThat(atom.getCompilationTimeSumSquaredMillis()).isAtLeast(0);
        assertThat(atom.getCompilationTimeCount()).isGreaterThan(0);
        assertThat(atom.getCount()).isGreaterThan(0);
        // atom.getModelArchHash64() can have any value

        for (EventMetricData event : data) {
            NeuralNetworksCompilationCompleted current = event.getAtom()
                    .getNeuralnetworksCompilationCompleted();
            assertThat(atom.getUid()).isEqualTo(current.getUid());
            assertThat(atom.getSessionId()).isEqualTo(current.getSessionId());
            assertThat(atom.getVersionNnapiModule()).isEqualTo(current.getVersionNnapiModule());
        }
    }

    public void testAppNeuralNetworksCompilationFailedNative() throws Exception {
        if (!isNnapiLoggingEnabled()) return;

        final int atomTag = Atom.NEURALNETWORKS_COMPILATION_FAILED_FIELD_NUMBER;
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), APP_PKG_NAME,
                atomTag,  /*uidInAttributionChain=*/false);

        DeviceUtils.runActivity(getDevice(), APP_PKG_NAME, APP_CLASS_NAME, null, null,
                /* waitTimeMs= */ 5000L);

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        assertTrue(data.size() > 0);
        NeuralNetworksCompilationFailed atom = data.get(0).getAtom()
                .getNeuralnetworksCompilationFailed();
        // UID should belong to the run activity, not any system service.
        assertThat(atom.getUid()).isGreaterThan(10000);
        // atom.getSessionId() can have any value
        // atom.getVersionNnapiModule() can have any value
        assertThat(atom.getModelArchHash()).hasSize(32);
        assertThat(atom.getDeviceId()).isNotEmpty();
        assertThat(atom.getInputDataClass())
                .isEqualTo(android.neuralnetworks.Enums.DataClass.DATA_CLASS_FLOAT32);
        assertThat(atom.getOutputDataClass())
                .isEqualTo(android.neuralnetworks.Enums.DataClass.DATA_CLASS_FLOAT32);
        assertThat(atom.getErrorCode()).isEqualTo(
                android.neuralnetworks.Enums.ResultCode.RESULT_CODE_BAD_STATE);
        assertFalse(atom.getIntrospectionEnabled());
        assertFalse(atom.getCacheEnabled());
        assertFalse(atom.getHasControlFlow());
        assertFalse(atom.getHasDynamicTemporaries());
        assertThat(atom.getCount()).isGreaterThan(0);
        // atom.getModelArchHash64() can have any value

        for (EventMetricData event : data) {
            NeuralNetworksCompilationFailed current = event.getAtom()
                    .getNeuralnetworksCompilationFailed();
            assertThat(atom.getUid()).isEqualTo(current.getUid());
            assertThat(atom.getSessionId()).isEqualTo(current.getSessionId());
            assertThat(atom.getVersionNnapiModule()).isEqualTo(current.getVersionNnapiModule());
        }
    }

    public void testAppNeuralNetworksExecutionCompletedNative() throws Exception {
        if (!isNnapiLoggingEnabled()) return;

        final int atomTag = Atom.NEURALNETWORKS_EXECUTION_COMPLETED_FIELD_NUMBER;
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), APP_PKG_NAME,
                atomTag,  /*uidInAttributionChain=*/false);

        DeviceUtils.runActivity(getDevice(), APP_PKG_NAME, APP_CLASS_NAME, null, null,
                /* waitTimeMs= */ 5000L);

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        assertTrue(data.size() > 0);
        NeuralNetworksExecutionCompleted atom = data.get(0).getAtom()
                .getNeuralnetworksExecutionCompleted();
        // UID should belong to the run activity, not any system service.
        assertThat(atom.getUid()).isGreaterThan(10000);
        // atom.getSessionId() can have any value
        // atom.getVersionNnapiModule() can have any value
        assertThat(atom.getModelArchHash()).hasSize(32);
        assertThat(atom.getDeviceId()).isNotEmpty();
        assertThat(atom.getMode()).isEqualTo(android.neuralnetworks.Enums.Mode.MODE_SYNC);
        assertThat(atom.getInputDataClass())
                .isEqualTo(android.neuralnetworks.Enums.DataClass.DATA_CLASS_FLOAT32);
        assertThat(atom.getOutputDataClass())
                .isEqualTo(android.neuralnetworks.Enums.DataClass.DATA_CLASS_FLOAT32);
        assertFalse(atom.getIntrospectionEnabled());
        assertFalse(atom.getCacheEnabled());
        assertFalse(atom.getHasControlFlow());
        assertFalse(atom.getHasDynamicTemporaries());
        assertThat(atom.getDurationDriverSumMicros()).isEqualTo(0);
        assertThat(atom.getDurationDriverMinMicros()).isEqualTo(Long.MAX_VALUE);
        assertThat(atom.getDurationDriverMaxMicros()).isEqualTo(Long.MIN_VALUE);
        assertThat(atom.getDurationDriverSumSquaredMicros()).isEqualTo(0);
        assertThat(atom.getDurationDriverCount()).isEqualTo(0);
        assertThat(atom.getDurationHardwareSumMicros()).isEqualTo(0);
        assertThat(atom.getDurationHardwareMinMicros()).isEqualTo(Long.MAX_VALUE);
        assertThat(atom.getDurationHardwareMaxMicros()).isEqualTo(Long.MIN_VALUE);
        assertThat(atom.getDurationHardwareSumSquaredMicros()).isEqualTo(0);
        assertThat(atom.getDurationHardwareCount()).isEqualTo(0);
        assertThat(atom.getDurationRuntimeSumMicros()).isAtLeast(0);
        assertThat(atom.getDurationRuntimeMinMicros()).isAtLeast(0);
        assertThat(atom.getDurationRuntimeMaxMicros()).isAtLeast(0);
        assertThat(atom.getDurationRuntimeMinMicros()).isAtMost(atom.getDurationRuntimeMaxMicros());
        assertThat(atom.getDurationRuntimeSumSquaredMicros()).isAtLeast(0);
        assertThat(atom.getDurationRuntimeCount()).isGreaterThan(0);
        assertThat(atom.getCount()).isGreaterThan(0);
        // atom.getModelArchHash64() can have any value

        for (EventMetricData event : data) {
            NeuralNetworksExecutionCompleted current = event.getAtom()
                    .getNeuralnetworksExecutionCompleted();
            assertThat(atom.getUid()).isEqualTo(current.getUid());
            assertThat(atom.getSessionId()).isEqualTo(current.getSessionId());
            assertThat(atom.getVersionNnapiModule()).isEqualTo(current.getVersionNnapiModule());
        }
    }

    public void testAppNeuralNetworksExecutionFailedNative() throws Exception {
        if (!isNnapiLoggingEnabled()) return;

        final int atomTag = Atom.NEURALNETWORKS_EXECUTION_FAILED_FIELD_NUMBER;
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), APP_PKG_NAME,
                atomTag,  /*uidInAttributionChain=*/false);

        DeviceUtils.runActivity(getDevice(), APP_PKG_NAME, APP_CLASS_NAME, null, null,
                /* waitTimeMs= */ 5000L);

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        assertTrue(data.size() > 0);
        NeuralNetworksExecutionFailed atom = data.get(0).getAtom()
                .getNeuralnetworksExecutionFailed();
        // UID should belong to the run activity, not any system service.
        assertThat(atom.getUid()).isGreaterThan(10000);
        // atom.getSessionId() can have any value
        // atom.getVersionNnapiModule() can have any value
        assertThat(atom.getModelArchHash()).hasSize(32);
        assertThat(atom.getDeviceId()).isNotEmpty();
        assertThat(atom.getMode()).isEqualTo(android.neuralnetworks.Enums.Mode.MODE_SYNC);
        assertThat(atom.getInputDataClass())
                .isEqualTo(android.neuralnetworks.Enums.DataClass.DATA_CLASS_FLOAT32);
        assertThat(atom.getOutputDataClass())
                .isEqualTo(android.neuralnetworks.Enums.DataClass.DATA_CLASS_FLOAT32);
        assertThat(atom.getErrorCode()).isEqualTo(
                android.neuralnetworks.Enums.ResultCode.RESULT_CODE_OUTPUT_INSUFFICIENT_SIZE);
        assertFalse(atom.getIntrospectionEnabled());
        assertFalse(atom.getCacheEnabled());
        assertFalse(atom.getHasControlFlow());
        assertFalse(atom.getHasDynamicTemporaries());
        assertThat(atom.getCount()).isGreaterThan(0);
        // atom.getModelArchHash64() can have any value

        for (EventMetricData event : data) {
            NeuralNetworksExecutionFailed current = event.getAtom()
                    .getNeuralnetworksExecutionFailed();
            assertThat(atom.getUid()).isEqualTo(current.getUid());
            assertThat(atom.getSessionId()).isEqualTo(current.getSessionId());
            assertThat(atom.getVersionNnapiModule()).isEqualTo(current.getVersionNnapiModule());
        }
    }
}

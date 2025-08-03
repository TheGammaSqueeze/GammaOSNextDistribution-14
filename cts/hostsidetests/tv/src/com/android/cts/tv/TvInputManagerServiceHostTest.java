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
package com.android.cts.tv;

import com.android.tradefed.util.RunUtil;
import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;
import android.stats.tv.TifTuneState;

import com.android.os.AtomsProto;
import com.android.os.StatsLog;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.truth.Correspondence;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.model.Statement;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Test {@link com.android.server.tv.TvInputManagerService} statsd metrics.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class TvInputManagerServiceHostTest extends BaseHostJUnit4Test {
    public static final String TEST_APK = "CtsTvTestCasesHelperApp.apk";
    public static final String TEST_PKG = "android.tv.cts";

    /** Compares the fields state, hdmiPort and inputId */
    private static final Correspondence<AtomsProto.TifTuneStateChanged,
            AtomsProto.TifTuneStateChanged>
            TIF_TUNE_STATE_CHANGED_CORRESPONDENCE = CorrespondenceFieldChainBuilder
            .builder(AtomsProto.TifTuneStateChanged.class)
            .addField(AtomsProto.TifTuneStateChanged::getState, "state")
            .addField(AtomsProto.TifTuneStateChanged::getHdmiPort, "hdmi_port")
            .addField(AtomsProto.TifTuneStateChanged::getInputId, "input_id")
            .build();

    @Rule
    public RequiredFeatureRule requiredFeatureRule = new RequiredFeatureRule(this,
            "android.software.live_tv");

    @Before
    public void setup() throws Exception {
        installPackage(TEST_APK);
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
    }

    @After
    public void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        uninstallPackage(TEST_APK);
    }

    @Test
    public void verifyCallbackVideoAvailable() throws Exception {
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), TEST_PKG,
                AtomsProto.Atom.TIF_TUNE_CHANGED_FIELD_NUMBER,  /*uidInAttributionChain=*/true);

        runTvInputServiceTest("verifyCallbackVideoAvailable");

        // Sorted list of events in order in which they occurred.
        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        List<AtomsProto.TifTuneStateChanged> tifTuneStateChanges = Lists.transform(data,
                input -> input.getAtom().getTifTuneChanged());
        AtomsProto.TifTuneStateChanged.Builder protoBuilder =
                AtomsProto.TifTuneStateChanged.newBuilder().
                        setInputId(1).
                        setHdmiPort(0);
        assertThat(tifTuneStateChanges).
                comparingElementsUsing(TIF_TUNE_STATE_CHANGED_CORRESPONDENCE).
                containsExactlyElementsIn(
                        createAtomsFromStateList(protoBuilder,
                                TifTuneState.CREATED,
                                TifTuneState.SURFACE_ATTACHED,
                                TifTuneState.TUNE_STARTED,
                                TifTuneState.VIDEO_AVAILABLE,
                                TifTuneState.SURFACE_DETACHED,
                                TifTuneState.RELEASED)).
                inOrder();
    }

    @Test
    public void verifyCallbackVideoUnavailable() throws Exception {
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), TEST_PKG,
                AtomsProto.Atom.TIF_TUNE_CHANGED_FIELD_NUMBER,  /*uidInAttributionChain=*/true);

        runTvInputServiceTest("verifyCallbackVideoUnavailable");

        // Sorted list of events in order in which they occurred.
        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        List<AtomsProto.TifTuneStateChanged> tifTuneStateChanges = Lists.transform(data,
                input -> input.getAtom().getTifTuneChanged());
        AtomsProto.TifTuneStateChanged.Builder protoBuilder =
                AtomsProto.TifTuneStateChanged.newBuilder().
                        setInputId(1).
                        setHdmiPort(0);
        assertThat(tifTuneStateChanges).
                comparingElementsUsing(TIF_TUNE_STATE_CHANGED_CORRESPONDENCE).
                containsExactlyElementsIn(
                        createAtomsFromStateList(protoBuilder,
                                TifTuneState.CREATED,
                                TifTuneState.SURFACE_ATTACHED,
                                TifTuneState.TUNE_STARTED,
                                TifTuneState.VIDEO_UNAVAILABLE_REASON_TUNING,
                                TifTuneState.SURFACE_DETACHED,
                                TifTuneState.RELEASED)).
                inOrder();
    }

    @Test
    public void verifyCommandTune() throws Exception {
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), TEST_PKG,
                AtomsProto.Atom.TIF_TUNE_CHANGED_FIELD_NUMBER,  /*uidInAttributionChain=*/true);

        runTvInputServiceTest("verifyCommandTune");

        // Sorted list of events in order in which they occurred.
        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        List<AtomsProto.TifTuneStateChanged> tifTuneStateChanges = Lists.transform(data,
                input -> input.getAtom().getTifTuneChanged());
        AtomsProto.TifTuneStateChanged.Builder protoBuilder =
                AtomsProto.TifTuneStateChanged.newBuilder().
                        setInputId(1).
                        setHdmiPort(0);
        assertThat(tifTuneStateChanges).
                comparingElementsUsing(TIF_TUNE_STATE_CHANGED_CORRESPONDENCE).
                containsExactlyElementsIn(
                        createAtomsFromStateList(protoBuilder,
                                TifTuneState.CREATED,
                                TifTuneState.SURFACE_ATTACHED,
                                TifTuneState.TUNE_STARTED,
                                TifTuneState.SURFACE_DETACHED,
                                TifTuneState.RELEASED)).
                inOrder();

    }

    private void runTvInputServiceTest(String testMethodName)
            throws DeviceNotAvailableException {
        DeviceUtils.runDeviceTests(getDevice(), TEST_PKG,
                "android.media.tv.cts.TvInputServiceTest", testMethodName);
    }

    private static Iterable<AtomsProto.TifTuneStateChanged> createAtomsFromStateList(
            AtomsProto.TifTuneStateChanged.Builder protoBuilder, TifTuneState... tuneStates) {
        ImmutableList.Builder<AtomsProto.TifTuneStateChanged> atoms = ImmutableList.builder();
        for (int i = 0; i < tuneStates.length; i++) {
            atoms.add(protoBuilder.setState(tuneStates[i]).build());
        }
        return atoms.build();
    }

    private static class CorrespondenceFieldChainBuilder<T> {
        private static class GetterName<T> {
            private final Function<T, Object> getter;
            private final String name;

            private GetterName(Function<T, Object> getter, String name) {
                this.getter = getter;
                this.name = name;
            }

            boolean isEqual(T actual, T expected) {
                return getter.apply(actual).equals(getter.apply(expected));
            }

            String diff(T actual, T expected) {
                return isEqual(actual, expected) ? ""
                        : String.format(
                                Locale.ENGLISH, "%s Expected: %s  Actual: %s",
                                name, getter.apply(expected),
                                getter.apply(actual));
            }
        }

        private static <T> CorrespondenceFieldChainBuilder<T> builder(
                Class<T> unused) {
            return new CorrespondenceFieldChainBuilder<T>();
        }

        List<GetterName<T>> getterNames = new ArrayList<>();

        CorrespondenceFieldChainBuilder<T> addField(Function<T, Object> getter, String name) {
            getterNames.add(new GetterName(getter, name));
            return this;
        }

        Correspondence<T, T> build() {
            Correspondence.BinaryPredicate<T, T> predicate = (actual, expected) ->
                    Iterables.all(getterNames, input -> input.isEqual(actual, expected));
            Correspondence.DiffFormatter<T, T> formatter =
                    (actual, expected) -> Joiner.on("\n").join(
                            Iterables.transform(getterNames,
                                    input -> input.diff(actual, expected)));
            return Correspondence.from(predicate,
                    "matches fields " + Lists.transform(getterNames,
                            input -> input.name)).formattingDiffsUsing(
                    formatter);
        }
    }

    // TODO(b/180429722): Move to a common CTS location.
    public static class RequiredFeatureRule implements TestRule {

        private final BaseHostJUnit4Test mTest;
        private final String mFeature;

        public RequiredFeatureRule(BaseHostJUnit4Test test, String feature) {
            mTest = test;
            mFeature = feature;
        }

        @Override
        public Statement apply(final Statement base, Description description) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    ITestDevice testDevice = mTest.getDevice();
                    // Checks if the device is available.
                    assumeTrue("Test device is not available", testDevice != null);
                    // Checks if the requested feature is available on the device.
                    assumeTrue(mFeature + " not present in DUT " + testDevice.getSerialNumber(),
                            testDevice.hasFeature(mFeature));
                    base.evaluate();
                }
            };
        }
    }
}

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

package android.broadcastradio.cts;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.radio.ProgramSelector;
import android.hardware.radio.RadioManager;
import android.hardware.radio.RadioTuner;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SafeCleanerRule;

import com.google.common.truth.Expect;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

abstract class AbstractRadioTestCase {

    protected static final String TAG = AbstractRadioTestCase.class.getSimpleName();

    protected static final int HANDLER_CALLBACK_MS = 100;
    protected static final int CANCEL_TIMEOUT_MS = 2_000;
    protected static final int TUNE_CALLBACK_TIMEOUT_MS  = 30_000;
    protected static final int PROGRAM_LIST_COMPLETE_TIMEOUT_MS = 60_000;

    private Context mContext;

    protected RadioManager mRadioManager;
    protected RadioTuner mRadioTuner;
    protected TestRadioTunerCallback mCallback;

    protected RadioManager.BandConfig mAmBandConfig;
    protected RadioManager.BandConfig mFmBandConfig;
    protected RadioManager.ModuleProperties mModule;

    @Rule
    public final Expect mExpect = Expect.create();
    @Rule
    public SafeCleanerRule mSafeCleanerRule = new SafeCleanerRule()
            .run(() -> {
                if (mRadioTuner != null) {
                    mRadioTuner.close();
                }
                assertNoTunerFailureAndResetCallback("cleaning up");
            });
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setup() {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.ACCESS_BROADCAST_RADIO);

        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        PackageManager packageManager = mContext.getPackageManager();
        boolean isRadioSupported = packageManager.hasSystemFeature(
                PackageManager.FEATURE_BROADCAST_RADIO);

        assumeTrue("Radio supported", isRadioSupported);

        mRadioManager = mContext.getSystemService(RadioManager.class);

        assertWithMessage("Radio manager exists").that(mRadioManager).isNotNull();

        mCallback = new TestRadioTunerCallback();
    }

    @After
    public void cleanUp() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

    protected void assertNoTunerFailureAndResetCallback(String operation) {
        if (mCallback == null) {
            return;
        }
        assertWithMessage("Error callback when " + operation)
                .that(mCallback.errorCount).isEqualTo(0);
        assertWithMessage("Tune failure callback when " + operation)
                .that(mCallback.tuneFailureCount).isEqualTo(0);
        assertWithMessage("Control changed callback when " + operation)
                .that(mCallback.controlChangeCount).isEqualTo(0);
        mCallback.reset();
    }

    protected void openAmFmTuner() throws Exception {
        openAmFmTuner(/* withAudio= */ true);
    }

    protected void openAmFmTuner(boolean withAudio) throws Exception {
        setAmFmConfig();

        assume().withMessage("AM/FM radio module exists").that(mModule).isNotNull();

        mRadioTuner = mRadioManager.openTuner(mModule.getId(), mFmBandConfig, withAudio, mCallback,
                /* handler= */ null);

        if (!withAudio) {
            assume().withMessage("Non-audio radio tuner").that(mRadioTuner).isNotNull();
        }

        mExpect.withMessage("Radio tuner opened").that(mRadioTuner).isNotNull();
        // Opening tuner will invoke program info change callback only when there exists a tuner
        // before with non-null program info in broadcast radio service.
        mCallback.waitForProgramInfoChangeCallback(HANDLER_CALLBACK_MS);

        assertNoTunerFailureAndResetCallback("opening AM/FM tuner");
    }

    protected void setAmFmConfig() {
        mModule = null;
        List<RadioManager.ModuleProperties> modules = new ArrayList<>();
        mRadioManager.listModules(modules);
        RadioManager.AmBandDescriptor amBandDescriptor = null;
        RadioManager.FmBandDescriptor fmBandDescriptor = null;
        for (int moduleIndex = 0; moduleIndex < modules.size(); moduleIndex++) {
            for (RadioManager.BandDescriptor band : modules.get(moduleIndex).getBands()) {
                int bandType = band.getType();
                if (bandType == RadioManager.BAND_AM || bandType == RadioManager.BAND_AM_HD) {
                    amBandDescriptor = (RadioManager.AmBandDescriptor) band;
                }
                if (bandType == RadioManager.BAND_FM || bandType == RadioManager.BAND_FM_HD) {
                    fmBandDescriptor = (RadioManager.FmBandDescriptor) band;
                }
            }
            if (amBandDescriptor != null && fmBandDescriptor != null) {
                mModule = modules.get(moduleIndex);
                mAmBandConfig = new RadioManager.AmBandConfig.Builder(amBandDescriptor).build();
                mFmBandConfig = new RadioManager.FmBandConfig.Builder(fmBandDescriptor).build();
                break;
            }
        }
    }

    static final class TestRadioTunerCallback extends RadioTuner.Callback {

        public int errorCount;
        public int error = RadioManager.STATUS_OK;
        public int tuneFailureCount;
        public int tunerFailureResult = RadioTuner.TUNER_RESULT_OK;
        public ProgramSelector tunerFailureSelector;
        public int controlChangeCount;
        public RadioManager.ProgramInfo currentProgramInfo;
        public int configFlagCount;
        private CountDownLatch mProgramInfoChangeLatch = new CountDownLatch(1);

        public void reset() {
            resetTuneFailureCallback();
            controlChangeCount = 0;
            currentProgramInfo = null;
            configFlagCount = 0;
            resetProgramInfoChangeCallback();
        }

        public void resetTuneFailureCallback() {
            errorCount = 0;
            error = RadioManager.STATUS_OK;
            tuneFailureCount = 0;
            tunerFailureResult = RadioTuner.TUNER_RESULT_OK;
            tunerFailureSelector = null;
        }

        public void resetProgramInfoChangeCallback() {
            mProgramInfoChangeLatch = new CountDownLatch(1);
        }

        public boolean waitForProgramInfoChangeCallback(int timeoutMs) throws InterruptedException {
            return waitForCallback(mProgramInfoChangeLatch, timeoutMs);
        }

        private boolean waitForCallback(CountDownLatch latch, int timeoutMs)
                throws InterruptedException {
            Log.v(TAG, "Waiting " + timeoutMs + " ms for latch " + latch);
            if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                Log.e(TAG, latch + " not called in " + timeoutMs + " ms");
                return false;
            }
            return true;
        }

        @Override
        public void onError(int status) {
            Log.e(TAG, "onError(" + status + ")");
            error = status;
            errorCount++;
        }

        @Override
        public void onTuneFailed(int result, ProgramSelector selector) {
            Log.e(TAG, "onTuneFailed(" + result + ", " + selector + ")");
            tunerFailureResult = result;
            tunerFailureSelector = selector;
            tuneFailureCount++;
        }

        @Override
        public void onProgramInfoChanged(RadioManager.ProgramInfo info) {
            currentProgramInfo = info;
            mProgramInfoChangeLatch.countDown();
        }

        @Override
        public void onConfigFlagUpdated(int flag, boolean value) {
            configFlagCount++;
        }

        @Override
        public void onControlChanged(boolean control) {
            Log.e(TAG, "onControlChanged(" + control + ")");
            controlChangeCount++;
        }
    }
}

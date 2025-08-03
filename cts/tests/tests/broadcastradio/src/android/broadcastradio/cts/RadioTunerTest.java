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

import static com.google.common.truth.TruthJUnit.assume;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.graphics.Bitmap;
import android.hardware.radio.Flags;
import android.hardware.radio.ProgramList;
import android.hardware.radio.ProgramSelector;
import android.hardware.radio.RadioManager;
import android.hardware.radio.RadioMetadata;
import android.hardware.radio.RadioTuner;
import android.platform.test.annotations.RequiresFlagsEnabled;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * CTS test for broadcast radio.
 */
@RunWith(AndroidJUnit4.class)
public final class RadioTunerTest extends AbstractRadioTestCase {

    private static final int TEST_CONFIG_FLAG = RadioManager.CONFIG_FORCE_ANALOG;

    @Test
    @ApiTest(apis = {"android.hardware.radio.RadioTuner#close"})
    public void close_twice() throws Exception {
        openAmFmTuner();

        mRadioTuner.close();
        mRadioTuner.close();

        mExpect.withMessage("Error callbacks").that(mCallback.errorCount).isEqualTo(0);
    }

    @Test
    @ApiTest(apis = {"android.hardware.radio.RadioTuner#cancel",
            "android.hardware.radio.RadioTuner#close"})
    public void cancel_afterTunerCloses_returnsInvalidOperationStatus() throws Exception {
        openAmFmTuner();
        mRadioTuner.close();

        int cancelResult = mRadioTuner.cancel();

        mExpect.withMessage("Result of cancel operation after tuner closed")
                .that(cancelResult).isEqualTo(RadioManager.STATUS_INVALID_OPERATION);
    }

    @Test
    @ApiTest(apis = {"android.hardware.radio.RadioTuner#getMute"})
    public void getMute_returnsFalse() throws Exception {
        openAmFmTuner();

        boolean isMuted = mRadioTuner.getMute();

        mExpect.withMessage("Mute status of tuner without audio").that(isMuted).isFalse();
    }

    @Test
    @ApiTest(apis = {"android.hardware.radio.RadioTuner#getMute",
            "android.hardware.radio.RadioTuner#setMute"})
    public void setMute_withTrue_MuteSucceeds() throws Exception {
        openAmFmTuner();

        int result = mRadioTuner.setMute(true);

        mExpect.withMessage("Result of setting mute with true")
                .that(result).isEqualTo(RadioManager.STATUS_OK);
        mExpect.withMessage("Mute status after setting mute with true")
                .that(mRadioTuner.getMute()).isTrue();
    }

    @Test
    @ApiTest(apis = {"android.hardware.radio.RadioTuner#getMute",
            "android.hardware.radio.RadioTuner#setMute"})
    public void setMute_withFalse_MuteSucceeds() throws Exception {
        openAmFmTuner();
        mRadioTuner.setMute(true);

        int result = mRadioTuner.setMute(false);

        mExpect.withMessage("Result of setting mute with false")
                .that(result).isEqualTo(RadioManager.STATUS_OK);
        mExpect.withMessage("Mute status after setting mute with false")
                .that(mRadioTuner.getMute()).isFalse();
    }

    @Test
    @ApiTest(apis = {"android.hardware.radio.RadioTuner#setMute"})
    public void setMute_withTunerWithoutAudio_returnsError() throws Exception {
        openAmFmTuner(/* withAudio= */ false);

        int result = mRadioTuner.setMute(false);

        mExpect.withMessage("Status of setting mute on tuner without audio")
                .that(result).isEqualTo(RadioManager.STATUS_ERROR);
    }

    @Test
    @ApiTest(apis = {"android.hardware.radio.RadioTuner#getMute"})
    public void isMuted_withTunerWithoutAudio_returnsTrue() throws Exception {
        openAmFmTuner(/* withAudio= */ false);

        boolean isMuted = mRadioTuner.getMute();

        mExpect.withMessage("Mute status of tuner without audio").that(isMuted).isTrue();
    }

    @Test
    @ApiTest(apis = {"android.hardware.radio.RadioTuner#step"})
    public void step_withDownDirection() throws Exception {
        openAmFmTuner();

        int stepResult = mRadioTuner.step(RadioTuner.DIRECTION_DOWN, /* skipSubChannel= */ true);

        mExpect.withMessage("Program info callback invoked for step operation with down direction")
                .that(mCallback.waitForProgramInfoChangeCallback(TUNE_CALLBACK_TIMEOUT_MS))
                .isTrue();
        mExpect.withMessage("Result of step operation with down direction")
                .that(stepResult).isEqualTo(RadioManager.STATUS_OK);
    }

    @Test
    @ApiTest(apis = {"android.hardware.radio.RadioTuner#step",
            "android.hardware.radio.RadioTuner.Callback#onProgramInfoChanged"})
    public void step_withUpDirection() throws Exception {
        openAmFmTuner();

        int stepResult = mRadioTuner.step(RadioTuner.DIRECTION_UP, /* skipSubChannel= */ false);

        mExpect.withMessage("Program info callback for step operation with up direction")
                .that(mCallback.waitForProgramInfoChangeCallback(TUNE_CALLBACK_TIMEOUT_MS))
                .isTrue();
        mExpect.withMessage("Result of step operation with up direction")
                .that(stepResult).isEqualTo(RadioManager.STATUS_OK);
    }

    @Test
    @ApiTest(apis = {"android.hardware.radio.RadioTuner#step",
            "android.hardware.radio.RadioTuner.Callback#onProgramInfoChanged"})
    public void step_withMultipleTimes() throws Exception {
        openAmFmTuner();

        for (int index = 0; index < 10; index++) {
            int stepResult =
                    mRadioTuner.step(RadioTuner.DIRECTION_DOWN, /* skipSubChannel= */ true);

            mExpect.withMessage("Program info callback for step operation at iteration %s",
                    index).that(mCallback.waitForProgramInfoChangeCallback(
                            TUNE_CALLBACK_TIMEOUT_MS)).isTrue();
            mExpect.withMessage("Result of step operation at iteration %s", index)
                    .that(stepResult).isEqualTo(RadioManager.STATUS_OK);

            assertNoTunerFailureAndResetCallback("step");
        }
    }

    @Test
    @ApiTest(apis = {"android.hardware.radio.RadioTuner#seek"})
    public void seek_onProgramInfoChangedInvoked() throws Exception {
        openAmFmTuner();

        int seekResult = mRadioTuner.seek(RadioTuner.DIRECTION_UP, /* skipSubChannel= */ true);

        mExpect.withMessage("Program info callback for seek operation")
                .that(mCallback.waitForProgramInfoChangeCallback(TUNE_CALLBACK_TIMEOUT_MS))
                .isTrue();
        mExpect.withMessage("Result of seek operation")
                .that(seekResult).isEqualTo(RadioManager.STATUS_OK);
    }

    @Test
    @ApiTest(apis = {"android.hardware.radio.RadioTuner#tune"})
    public void tune_withFmSelector_onProgramInfoChangedInvoked() throws Exception {
        openAmFmTuner();
        int freq = mFmBandConfig.getLowerLimit() + mFmBandConfig.getSpacing();
        ProgramSelector sel = ProgramSelector.createAmFmSelector(RadioManager.BAND_FM, freq,
                /* subChannel= */ 0);

        mRadioTuner.tune(sel);

        mExpect.withMessage("Program info callback for tune operation")
                .that(mCallback.waitForProgramInfoChangeCallback(TUNE_CALLBACK_TIMEOUT_MS))
                .isTrue();
        mExpect.withMessage("Program selector tuned to")
                .that(mCallback.currentProgramInfo.getSelector()).isEqualTo(sel);
    }

    @Test
    @ApiTest(apis = {"android.hardware.radio.RadioTuner#cancel"})
    public void cancel_afterNoOperations() throws Exception {
        openAmFmTuner();

        int cancelResult = mRadioTuner.cancel();

        mCallback.waitForProgramInfoChangeCallback(CANCEL_TIMEOUT_MS);
        mExpect.withMessage("Result of cancel operation after no operations")
                .that(cancelResult).isEqualTo(RadioManager.STATUS_OK);
        mExpect.withMessage("Tuner failure of cancel operation after no operations")
                .that(mCallback.tunerFailureResult).isNotEqualTo(RadioTuner.TUNER_RESULT_CANCELED);
    }

    @Test
    @ApiTest(apis = {"android.hardware.radio.RadioTuner#step",
            "android.hardware.radio.RadioTuner#cancel"})
    public void cancel_afterStepCompletes() throws Exception {
        openAmFmTuner();
        int stepResult = mRadioTuner.step(RadioTuner.DIRECTION_DOWN, /* skipSubChannel= */ false);
        mExpect.withMessage("Result of step operation")
                .that(stepResult).isEqualTo(RadioManager.STATUS_OK);
        mExpect.withMessage("Program info callback before cancellation")
                .that(mCallback.waitForProgramInfoChangeCallback(TUNE_CALLBACK_TIMEOUT_MS))
                .isTrue();

        int cancelResult = mRadioTuner.cancel();

        mCallback.waitForProgramInfoChangeCallback(CANCEL_TIMEOUT_MS);
        mExpect.withMessage("Result of cancel operation after step completed")
                .that(cancelResult).isEqualTo(RadioManager.STATUS_OK);
        mExpect.withMessage("Tuner failure of cancel operation after step completed")
                .that(mCallback.tunerFailureResult).isNotEqualTo(RadioTuner.TUNER_RESULT_CANCELED);
    }

    @Test
    @ApiTest(apis = {"android.hardware.radio.RadioTuner#getDynamicProgramList"})
    public void getDynamicProgramList_programListUpdated() throws Exception {
        openAmFmTuner();
        TestOnCompleteListener completeListener = new TestOnCompleteListener();

        ProgramList list = assumeNonNullProgramList();
        try {
            list.addOnCompleteListener(completeListener);

            mExpect.withMessage("List update completion").that(completeListener.waitForCallback())
                    .isTrue();
        } finally {
            list.close();
        }
    }

    @Test
    @ApiTest(apis = {"android.hardware.radio.ProgramList#getProgramInfos"})
    @RequiresFlagsEnabled(Flags.FLAG_HD_RADIO_IMPROVED)
    public void getProgramInfos_fromDynamicProgramList() throws Exception {
        openAmFmTuner();
        TestOnCompleteListener completeListener = new TestOnCompleteListener();
        ProgramList list = assumeNonNullProgramList();
        try {
            list.addOnCompleteListener(completeListener);
            mExpect.withMessage("List update completion before getting program info")
                    .that(completeListener.waitForCallback()).isTrue();
            List<RadioManager.ProgramInfo> programInfoList = list.toList();
            assume().withMessage("Non-empty program program list")
                    .that(programInfoList).isNotEmpty();
            RadioManager.ProgramInfo firstProgram = programInfoList.get(0);

            mExpect.withMessage("Program list infos")
                    .that(list.getProgramInfos(firstProgram.getSelector().getPrimaryId()))
                    .contains(firstProgram);
        } finally {
            list.close();
        }
    }

    @Test
    @ApiTest(apis = {"android.hardware.radio.RadioTuner#getDynamicProgramList",
            "android.hardware.radio.RadioTuner#tune"})
    public void tune_withHdSelectorFromDynamicProgramList() throws Exception {
        ProgramList.Filter hdFilter = new ProgramList.Filter(
                Set.of(ProgramSelector.IDENTIFIER_TYPE_HD_STATION_ID_EXT), Collections.emptySet(),
                /* includeCategories= */ true, /* excludeModifications= */ false);
        openAmFmTuner();
        TestOnCompleteListener completeListener = new TestOnCompleteListener();
        ProgramList list = assumeNonNullFiltereredDynamicProgramList(hdFilter);
        try {
            list.addOnCompleteListener(completeListener);
            mExpect.withMessage("HD program list update completion")
                    .that(completeListener.waitForCallback()).isTrue();
            List<RadioManager.ProgramInfo> programInfoList = list.toList();
            assume().withMessage("Non-empty HD radio program program list")
                    .that(programInfoList).isNotEmpty();
            ProgramSelector firstHdSelector = programInfoList.get(0).getSelector();

            mRadioTuner.tune(firstHdSelector);

            mExpect.withMessage("Program info callback for HD tune operation")
                    .that(mCallback.waitForProgramInfoChangeCallback(TUNE_CALLBACK_TIMEOUT_MS))
                    .isTrue();
            mExpect.withMessage("HD program selector tuned to")
                    .that(mCallback.currentProgramInfo.getSelector()).isEqualTo(firstHdSelector);
        } finally {
            list.close();
        }
    }

    @Test
    @ApiTest(apis = {"android.hardware.radio.RadioMetadata#getBitmapId",
            "android.hardware.radio.RadioTuner#getMetadataImage"})
    @RequiresFlagsEnabled(Flags.FLAG_HD_RADIO_IMPROVED)
    public void getMetadataImage() throws Exception {
        openAmFmTuner();
        TestOnCompleteListener completeListener = new TestOnCompleteListener();
        try (ProgramList list = assumeNonNullProgramList()) {
            list.addOnCompleteListener(completeListener);
            mExpect.withMessage("Program list update completion")
                    .that(completeListener.waitForCallback()).isTrue();
            ProgramSelector selector = getSelectorWithBitmap(list.toList());
            assume().withMessage("Program selector of program info with bitmap key in program list")
                    .that(selector).isNotNull();
            mRadioTuner.tune(selector);
            mExpect.withMessage("Program info callback for tune operation")
                    .that(mCallback.waitForProgramInfoChangeCallback(TUNE_CALLBACK_TIMEOUT_MS))
                    .isTrue();
            RadioManager.ProgramInfo currentInfo = mCallback.currentProgramInfo;
            mCallback.reset();
            int bitmapId = assumeImageMetadataId(currentInfo);

            Bitmap bitmap = mRadioTuner.getMetadataImage(bitmapId);

            mExpect.withMessage("Bitmap metadata").that(bitmap).isNotNull();
        } catch (IllegalArgumentException e) {
            mExpect.withMessage("Exception for bitmap unavailable").that(e)
                    .hasMessageThat().contains("not available");
            mExpect.withMessage("Bitmap metadata without updated from tuner")
                    .that(mCallback.currentProgramInfo).isNotNull();
        }
    }

    @Test
    @ApiTest(apis = {"android.hardware.radio.RadioTuner#isConfigFlagSupported",
            "android.hardware.radio.RadioTuner#isConfigFlagSet"})
    public void isConfigFlagSet_forTunerNotSupported_throwsException() throws Exception {
        openAmFmTuner();
        boolean isSupported = mRadioTuner.isConfigFlagSupported(TEST_CONFIG_FLAG);
        assumeFalse("Config flag supported", isSupported);

        assertThrows(UnsupportedOperationException.class,
                () -> mRadioTuner.isConfigFlagSet(TEST_CONFIG_FLAG));
    }

    @Test
    @ApiTest(apis = {"android.hardware.radio.RadioTuner#setConfigFlag",
            "android.hardware.radio.RadioTuner#setConfigFlag"})
    public void setConfigFlag_forTunerNotSupported_throwsException() throws Exception {
        openAmFmTuner();
        boolean isSupported = mRadioTuner.isConfigFlagSupported(TEST_CONFIG_FLAG);
        assumeFalse("Config flag supported", isSupported);

        assertThrows(UnsupportedOperationException.class,
                () -> mRadioTuner.setConfigFlag(TEST_CONFIG_FLAG, /* value= */ true));
    }

    @Test
    @ApiTest(apis = {"android.hardware.radio.RadioTuner#setConfigFlag",
            "android.hardware.radio.RadioTuner#setConfigFlag",
            "android.hardware.radio.RadioTuner#isConfigFlagSet"})
    public void setConfigFlag_withTrue_succeeds() throws Exception {
        openAmFmTuner();
        boolean isSupported = mRadioTuner.isConfigFlagSupported(TEST_CONFIG_FLAG);
        assumeTrue("Config flag supported", isSupported);

        mRadioTuner.setConfigFlag(TEST_CONFIG_FLAG, /* value= */ true);

        mExpect.withMessage("Config flag with true value")
                .that(mRadioTuner.isConfigFlagSet(TEST_CONFIG_FLAG)).isTrue();
        mExpect.withMessage("Config flag callback count when setting true config flag value")
                .that(mCallback.configFlagCount).isEqualTo(0);
    }

    @Test
    @ApiTest(apis = {"android.hardware.radio.RadioTuner#setConfigFlag",
            "android.hardware.radio.RadioTuner#setConfigFlag",
            "android.hardware.radio.RadioTuner#isConfigFlagSet"})
    public void setConfigFlag_withFalse_succeeds() throws Exception {
        openAmFmTuner();
        boolean isSupported = mRadioTuner.isConfigFlagSupported(TEST_CONFIG_FLAG);
        assumeTrue("Config flag supported", isSupported);

        mRadioTuner.setConfigFlag(TEST_CONFIG_FLAG, /* value= */ false);

        mExpect.withMessage("Config flag with false value")
                .that(mRadioTuner.isConfigFlagSet(TEST_CONFIG_FLAG)).isFalse();
        mExpect.withMessage("Config flag callback count when setting false config flag value")
                .that(mCallback.configFlagCount).isEqualTo(0);
    }

    private ProgramList assumeNonNullProgramList() {
        return assumeNonNullFiltereredDynamicProgramList(/* filter= */ null);
    }

    private ProgramList assumeNonNullFiltereredDynamicProgramList(ProgramList.Filter filter) {
        ProgramList list = mRadioTuner.getDynamicProgramList(filter);
        assume().withMessage("Dynamic program list supported").that(list).isNotNull();
        return list;
    }

    private ProgramSelector getSelectorWithBitmap(List<RadioManager.ProgramInfo> programInfoList) {
        for (int i = 0; i < programInfoList.size(); i++) {
            RadioMetadata metadata = programInfoList.get(i).getMetadata();
            int iconId = metadata.getBitmapId(RadioMetadata.METADATA_KEY_ICON);
            if (iconId != 0) {
                return programInfoList.get(i).getSelector();
            }
            int artId = metadata.getBitmapId(RadioMetadata.METADATA_KEY_ART);
            if (artId != 0) {
                return programInfoList.get(i).getSelector();
            }
        }
        return null;
    }

    private int assumeImageMetadataId(RadioManager.ProgramInfo info) {
        RadioMetadata metadata = info.getMetadata();
        int iconId = metadata.getBitmapId(RadioMetadata.METADATA_KEY_ICON);
        int artId = metadata.getBitmapId(RadioMetadata.METADATA_KEY_ART);
        assumeTrue("Bitmap id not supported", iconId != 0 || artId != 0);
        if (iconId != 0) {
            return iconId;
        } else {
            return artId;
        }
    }

    private static final class TestOnCompleteListener implements ProgramList.OnCompleteListener {

        private final CountDownLatch mOnCompleteLatch = new CountDownLatch(1);

        public boolean waitForCallback() throws InterruptedException {
            return mOnCompleteLatch.await(PROGRAM_LIST_COMPLETE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }

        @Override
        public void onComplete() {
            mOnCompleteLatch.countDown();
        }

    }
}

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

package android.voicerecognition.cts;

import static android.voicerecognition.cts.CallbackMethod.CALLBACK_METHOD_END_SEGMENTED_SESSION;
import static android.voicerecognition.cts.CallbackMethod.CALLBACK_METHOD_ERROR;
import static android.voicerecognition.cts.CallbackMethod.CALLBACK_METHOD_LANGUAGE_DETECTION;
import static android.voicerecognition.cts.CallbackMethod.CALLBACK_METHOD_RESULTS;
import static android.voicerecognition.cts.CallbackMethod.CALLBACK_METHOD_SEGMENTS_RESULTS;
import static android.voicerecognition.cts.CallbackMethod.CALLBACK_METHOD_UNSPECIFIED;
import static android.voicerecognition.cts.RecognizerMethod.RECOGNIZER_METHOD_CANCEL;
import static android.voicerecognition.cts.RecognizerMethod.RECOGNIZER_METHOD_DESTROY;
import static android.voicerecognition.cts.RecognizerMethod.RECOGNIZER_METHOD_START_LISTENING;
import static android.voicerecognition.cts.RecognizerMethod.RECOGNIZER_METHOD_STOP_LISTENING;
import static android.voicerecognition.cts.TestObjects.ERROR_CODE;
import static android.voicerecognition.cts.TestObjects.START_LISTENING_INTENT;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;

import android.content.Intent;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.ModelDownloadListener;
import android.speech.RecognitionService;
import android.speech.RecognitionSupport;
import android.speech.RecognitionSupportCallback;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.test.uiautomator.UiDevice;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.PollingCheck;

import com.google.common.collect.ImmutableList;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import junitparams.naming.TestCaseName;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Abstract implementation for {@link android.speech.SpeechRecognizer} CTS tests. */
@RunWith(JUnitParamsRunner.class)
abstract class AbstractRecognitionServiceTest {
    private static final String TAG = AbstractRecognitionServiceTest.class.getSimpleName();

    private static final long INDICATOR_DISMISS_TIMEOUT = 5000L;
    private static final long WAIT_TIMEOUT_MS = 30000L; // 30 secs
    private static final long SEQUENCE_TEST_WAIT_TIMEOUT_MS = 5000L;
    private static final long ACTIVITY_INIT_WAIT_TIMEOUT_MS = 5000L;

    /* package */ static final String CTS_VOICE_RECOGNITION_SERVICE =
            "android.recognitionservice.service/android.recognitionservice.service"
                    + ".CtsVoiceRecognitionService";

    /* package */ static final String IN_PACKAGE_RECOGNITION_SERVICE =
            "android.voicerecognition.cts/android.voicerecognition.cts.CtsRecognitionService";

    // Expected to create 1 more recognizer than what the concurrency limit is,
    // so that SpeechRecognizer#ERROR_RECOGNIZER_BUSY scenarios can be tested, too.
    private static final int EXPECTED_RECOGNIZER_COUNT =
            CtsRecognitionService.MAX_CONCURRENT_SESSIONS_COUNT + 1;

    @Rule
    public ActivityTestRule<SpeechRecognitionActivity> mActivityTestRule =
            new ActivityTestRule<>(SpeechRecognitionActivity.class);

    private UiDevice mUiDevice;
    private SpeechRecognitionActivity mActivity;

    private final Random mRandom = new Random();

    abstract void setCurrentRecognizer(SpeechRecognizer recognizer, String component);

    abstract boolean isOnDeviceTest();

    @Nullable
    abstract String customRecognizer();

    @Before
    public void setup() {
        prepareDevice();
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mActivity = mActivityTestRule.getActivity();
        mActivity.init(isOnDeviceTest(), customRecognizer(), EXPECTED_RECOGNIZER_COUNT);

        PollingCheck.waitFor(ACTIVITY_INIT_WAIT_TIMEOUT_MS,
                () -> mActivity.getRecognizerCount() == EXPECTED_RECOGNIZER_COUNT);
        assertWithMessage("Test activity initialization timed out.")
                .that(mActivity.getRecognizerCount()).isEqualTo(EXPECTED_RECOGNIZER_COUNT);
    }

    @Test
    public void testStartListening() throws Throwable {
        mUiDevice.waitForIdle();
        SpeechRecognitionActivity.RecognizerInfo ri = mActivity.getRecognizerInfoDefault();
        setCurrentRecognizer(ri.mRecognizer, CTS_VOICE_RECOGNITION_SERVICE);

        mUiDevice.waitForIdle();
        mActivity.startListeningDefault();
        try {
            // startListening() will call noteProxyOpNoTrow(). If the permission check passes,
            // then the RecognitionService.onStartListening() will be called. Otherwise,
            // a TimeoutException will be thrown.
            assertThat(ri.mCountDownLatch.await(
                    WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        } catch (InterruptedException e) {
            assertWithMessage("onStartListening() not called. " + e).fail();
        }

        // Wait for the privacy indicator to disappear to avoid the test becoming flaky.
        SystemClock.sleep(INDICATOR_DISMISS_TIMEOUT);
    }

    @Test
    public void testCanCheckForSupport() throws Throwable {
        mUiDevice.waitForIdle();
        SpeechRecognizer recognizer = mActivity.getRecognizerInfoDefault().mRecognizer;
        assertThat(recognizer).isNotNull();
        setCurrentRecognizer(recognizer, IN_PACKAGE_RECOGNITION_SERVICE);

        mUiDevice.waitForIdle();
        List<RecognitionSupport> supportResults = new ArrayList<>();
        List<Integer> errors = new ArrayList<>();
        RecognitionSupportCallback supportCallback = new RecognitionSupportCallback() {
            @Override
            public void onSupportResult(@NonNull RecognitionSupport recognitionSupport) {
                supportResults.add(recognitionSupport);
            }

            @Override
            public void onError(int error) {
                errors.add(error);
            }
        };
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        mActivity.checkRecognitionSupportDefault(intent, supportCallback);
        PollingCheck.waitFor(SEQUENCE_TEST_WAIT_TIMEOUT_MS,
                () -> supportResults.size() + errors.size() > 0);
        assertThat(supportResults).isEmpty();
        assertThat(errors).containsExactly(SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT);

        errors.clear();
        RecognitionSupport rs = new RecognitionSupport.Builder()
                .setInstalledOnDeviceLanguages(new ArrayList<>(List.of("es")))
                .addInstalledOnDeviceLanguage("en")
                .setPendingOnDeviceLanguages(new ArrayList<>(List.of("ru")))
                .addPendingOnDeviceLanguage("jp")
                .setSupportedOnDeviceLanguages(new ArrayList<>(List.of("pt")))
                .addSupportedOnDeviceLanguage("de")
                .setOnlineLanguages(new ArrayList<>(List.of("zh")))
                .addOnlineLanguage("fr")
                .build();
        CtsRecognitionService.sConsumerQueue.add(c -> c.onSupportResult(rs));

        mActivity.checkRecognitionSupportDefault(intent, supportCallback);
        PollingCheck.waitFor(SEQUENCE_TEST_WAIT_TIMEOUT_MS,
                () -> supportResults.size() + errors.size() > 0);
        assertThat(errors).isEmpty();
        assertThat(supportResults).containsExactly(rs);
        assertThat(rs.getInstalledOnDeviceLanguages())
                .isEqualTo(List.of("es", "en"));
        assertThat(rs.getPendingOnDeviceLanguages())
                .isEqualTo(List.of("ru", "jp"));
        assertThat(rs.getSupportedOnDeviceLanguages())
                .isEqualTo(List.of("pt", "de"));
        assertThat(rs.getOnlineLanguages())
                .isEqualTo(List.of("zh", "fr"));
        assertThat(CtsRecognitionService.sBindCount.get()).isGreaterThan(0);

        mActivity.destroyRecognizerDefault();
        mUiDevice.waitForIdle();
        PollingCheck.waitFor(() -> CtsRecognitionService.sBindCount.get() == 0);
    }

    @Test
    public void testCanTriggerModelDownload() throws Throwable {
        mUiDevice.waitForIdle();
        SpeechRecognizer recognizer = mActivity.getRecognizerInfoDefault().mRecognizer;
        assertThat(recognizer).isNotNull();
        setCurrentRecognizer(recognizer, IN_PACKAGE_RECOGNITION_SERVICE);

        mUiDevice.waitForIdle();
        CtsRecognitionService.sDownloadTriggers.clear();
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        mActivity.triggerModelDownloadDefault(intent);
        PollingCheck.waitFor(SEQUENCE_TEST_WAIT_TIMEOUT_MS,
                () -> CtsRecognitionService.sDownloadTriggers.size() > 0);
        assertThat(CtsRecognitionService.sDownloadTriggers).hasSize(1);
        assertThat(CtsRecognitionService.sBindCount.get()).isGreaterThan(0);

        mActivity.destroyRecognizerDefault();
        mUiDevice.waitForIdle();
        PollingCheck.waitFor(() -> CtsRecognitionService.sBindCount.get() == 0);
    }

    @Test
    @Parameters(method = "modelDownloadScenarios")
    @TestCaseName("{method}_{0}")
    public void testCanTriggerModelDownloadWithListener(
            ModelDownloadExecutionInfo.Scenario scenario) {
        mUiDevice.waitForIdle();
        SpeechRecognizer recognizer = mActivity.getRecognizerInfoDefault().mRecognizer;
        assertThat(recognizer).isNotNull();
        setCurrentRecognizer(recognizer, IN_PACKAGE_RECOGNITION_SERVICE);

        mUiDevice.waitForIdle();
        ModelDownloadExecutionInfo mdei = ModelDownloadExecutionInfo.fromScenario(scenario);
        CtsRecognitionService.sDownloadTriggers.clear();
        CtsRecognitionService.sInstructedModelDownloadCallbacks =
                new ArrayDeque<>(mdei.mInstructedCallbacks);
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        ModelDownloadCallbackLogger listener = new ModelDownloadCallbackLogger();

        mActivity.triggerModelDownloadWithListenerDefault(intent, listener);
        PollingCheck.waitFor(SEQUENCE_TEST_WAIT_TIMEOUT_MS,
                () -> CtsRecognitionService.sDownloadTriggers.size() > 0);

        mUiDevice.waitForIdle();
        assertThat(listener.mCallbacks)
                .containsExactlyElementsIn(mdei.mExpectedCallbacks)
                .inOrder();
        assertThat(CtsRecognitionService.sBindCount.get()).isGreaterThan(0);

        mActivity.destroyRecognizerDefault();
        mUiDevice.waitForIdle();
        PollingCheck.waitFor(() -> CtsRecognitionService.sBindCount.get() == 0);
    }

    static ModelDownloadExecutionInfo.Scenario[] modelDownloadScenarios() {
        return new ModelDownloadExecutionInfo.Scenario[] {
                ModelDownloadExecutionInfo.Scenario.PROGRESS_PROGRESS_PROGRESS,
                ModelDownloadExecutionInfo.Scenario.PROGRESS_SUCCESS_PROGRESS,
                ModelDownloadExecutionInfo.Scenario.SCHEDULED_ERROR,
                ModelDownloadExecutionInfo.Scenario.ERROR_SCHEDULED};
    }

    @Test
    @Parameters(method = "singleScenarios")
    @TestCaseName("{method}_{0}")
    public void sequenceTest(SequenceExecutionInfo.Scenario scenario) {
        Log.d(TAG, "Running a single sequence: " + scenario.name() + ".");
        executeSequenceTest(SequenceExecutionInfo.fromScenario(scenario));
    }

    static SequenceExecutionInfo.Scenario[] singleScenarios() {
        return new SequenceExecutionInfo.Scenario[] {
                SequenceExecutionInfo.Scenario.START_STOP_RESULTS,
                SequenceExecutionInfo.Scenario.START_RESULTS_STOP,
                SequenceExecutionInfo.Scenario.START_RESULTS_CANCEL,
                SequenceExecutionInfo.Scenario.START_RESULTS_START_RESULTS,
                SequenceExecutionInfo.Scenario.START_SEGMENT_ENDOFSESSION,
                SequenceExecutionInfo.Scenario.START_CANCEL,
                SequenceExecutionInfo.Scenario.START_START,
                SequenceExecutionInfo.Scenario.START_STOP_CANCEL,
                SequenceExecutionInfo.Scenario.START_ERROR_CANCEL,
                SequenceExecutionInfo.Scenario.START_STOP_DESTROY,
                SequenceExecutionInfo.Scenario.START_ERROR_DESTROY,
                SequenceExecutionInfo.Scenario.START_DESTROY_DESTROY,
                SequenceExecutionInfo.Scenario.START_DETECTION_STOP_RESULTS};
    }

    @Test
    @Parameters(method = "doubleScenarios")
    @TestCaseName("{method}_{0}_x_{1}")
    public void concurrentSequenceTest(
            SequenceExecutionInfo.Scenario scenario1,
            SequenceExecutionInfo.Scenario scenario2) {
        Log.d(TAG, "Running a double sequence: "
                + scenario1.name() + " x " + scenario2.name() + ".");
        executeSequenceTest(ImmutableList.of(
                SequenceExecutionInfo.fromScenario(scenario1),
                SequenceExecutionInfo.fromScenario(scenario2)),
                /* inOrder */ true);
    }

    static Object[] doubleScenarios() {
        // Scenarios where the results are not received in the same step when start is called.
        List<SequenceExecutionInfo.Scenario> concurrencyObservableScenarios = ImmutableList.of(
                SequenceExecutionInfo.Scenario.START_STOP_RESULTS,
                SequenceExecutionInfo.Scenario.START_SEGMENT_ENDOFSESSION,
                SequenceExecutionInfo.Scenario.START_CANCEL,
                SequenceExecutionInfo.Scenario.START_START,
                SequenceExecutionInfo.Scenario.START_STOP_CANCEL,
                SequenceExecutionInfo.Scenario.START_STOP_DESTROY,
                SequenceExecutionInfo.Scenario.START_DESTROY_DESTROY,
                SequenceExecutionInfo.Scenario.START_DETECTION_STOP_RESULTS);

        List<Object[]> scenarios = new ArrayList<>();
        for (int i = 0; i < concurrencyObservableScenarios.size(); i++) {
            for (int j = 0; j < concurrencyObservableScenarios.size(); j++) {
                scenarios.add(new Object[]{
                        concurrencyObservableScenarios.get(i),
                        concurrencyObservableScenarios.get(j)});
            }
        }
        return scenarios.toArray();
    }

    @Test
    public void testRecognitionServiceConcurrencyLimitValidity() {
        // Prepare the looper before creating a RecognitionService object.
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }

        RecognitionService defaultService = new RecognitionService() {
            @Override
            protected void onStartListening(Intent recognizerIntent, Callback listener) {}

            @Override
            protected void onCancel(Callback listener) {}

            @Override
            protected void onStopListening(Callback listener) {}
        };
        assertWithMessage("Default recognition service concurrency limit must be positive.")
                .that(defaultService.getMaxConcurrentSessionsCount()).isGreaterThan(0);

        RecognitionService testService = new CtsRecognitionService();
        assertWithMessage("Recognition service implementation concurrency limit must be positive.")
                .that(testService.getMaxConcurrentSessionsCount()).isGreaterThan(0);
    }

    @Test
    public void testRecognitionServiceBusy() {
        Log.d(TAG, "Running four sequences, one more than the concurrency limit.");
        executeSequenceTest(ImmutableList.of(
                SequenceExecutionInfo.fromScenario(
                        SequenceExecutionInfo.Scenario.START_STOP_RESULTS),
                SequenceExecutionInfo.fromScenario(
                        SequenceExecutionInfo.Scenario.START_SEGMENT_ENDOFSESSION),
                SequenceExecutionInfo.fromScenario(SequenceExecutionInfo.Scenario.START_CANCEL),

                // This sequence will fail with ERROR_RECOGNIZER_BUSY.
                SequenceExecutionInfo.fromScenario(SequenceExecutionInfo.Scenario.START_ERROR)),
                /* inOrder */ true);
    }

    private void executeSequenceTest(SequenceExecutionInfo sei) {
        executeSequenceTest(ImmutableList.of(sei), /* inOrder */ true);
    }

    private void executeSequenceTest(
            List<SequenceExecutionInfo> sequenceExecutionInfos,
            boolean inOrder) {
        mUiDevice.waitForIdle();

        // Initialize the recognizers to be used and clear their invoked callbacks list.
        for (int recognizerIndex = 0;
                recognizerIndex < sequenceExecutionInfos.size();
                recognizerIndex++) {
            SpeechRecognitionActivity.RecognizerInfo ri =
                    mActivity.getRecognizerInfo(recognizerIndex);
            assertThat(ri.mRecognizer).isNotNull();
            setCurrentRecognizer(ri.mRecognizer, IN_PACKAGE_RECOGNITION_SERVICE);
            ri.mCallbackMethodsInvoked.clear();
        }

        // Clear recognition service's invoked recognizer methods list
        // and callback instruction queue.
        CtsRecognitionService.sInvokedRecognizerMethods.clear();
        CtsRecognitionService.sInstructedCallbackMethods.clear();

        // Initialize the list of recognizers to be used.
        List<Integer> remainingRecognizerIndices = IntStream.range(0, sequenceExecutionInfos.size())
                .boxed().collect(Collectors.toList());

        int expectedServiceMethodsRunCount = 0;
        int nextRemainingRecognizerIndex = 0;
        while (!remainingRecognizerIndices.isEmpty()) {
            // If the execution should be in order, select the next recognizer by index.
            // Else, pick one of the recognizers at random. Start the next step.
            nextRemainingRecognizerIndex %= remainingRecognizerIndices.size();
            int selectedRecognizerIndex = remainingRecognizerIndices.get(inOrder
                    ? nextRemainingRecognizerIndex
                    : mRandom.nextInt(remainingRecognizerIndices.size()));
            SequenceExecutionInfo sei = sequenceExecutionInfos.get(selectedRecognizerIndex);
            int executionStep = sei.getNextStep();

            // If the flag is set, prepare the callback instruction for the service side.
            if (sei.mExpectedRecognizerServiceMethodsToPropagate.get(executionStep)) {
                CtsRecognitionService.sInstructedCallbackMethods.add(new Pair<>(
                        selectedRecognizerIndex,
                        sei.mCallbackMethodInstructions.get(executionStep)));
            }

            // Call the recognizer method.
            RecognizerMethod recognizerMethod = sei.mRecognizerMethodsToCall.get(executionStep);
            Log.i(TAG, "Sending service method " + recognizerMethod.name() + ".");
            switch (recognizerMethod) {
                case RECOGNIZER_METHOD_START_LISTENING:
                    mActivity.startListening(START_LISTENING_INTENT, selectedRecognizerIndex);
                    break;
                case RECOGNIZER_METHOD_STOP_LISTENING:
                    mActivity.stopListening(selectedRecognizerIndex);
                    break;
                case RECOGNIZER_METHOD_CANCEL:
                    mActivity.cancel(selectedRecognizerIndex);
                    break;
                case RECOGNIZER_METHOD_DESTROY:
                    mActivity.destroyRecognizer(selectedRecognizerIndex);
                    break;
                case RECOGNIZER_METHOD_UNSPECIFIED:
                default:
                    fail();
            }

            // If the flag is set, wait for the service to propagate the callback.
            if (sei.mExpectedRecognizerServiceMethodsToPropagate.get(executionStep)) {
                expectedServiceMethodsRunCount++;
                int finalExpectedServiceMethodsRunCount = expectedServiceMethodsRunCount;
                PollingCheck.waitFor(SEQUENCE_TEST_WAIT_TIMEOUT_MS,
                        () -> CtsRecognitionService.totalInvokedRecognizerMethodsCount()
                                == finalExpectedServiceMethodsRunCount);
            }

            // TODO(kiridza): Make this part of the sequence execution more robust.
            if (selectedRecognizerIndex >= CtsRecognitionService.MAX_CONCURRENT_SESSIONS_COUNT) {
                PollingCheck.waitFor(SEQUENCE_TEST_WAIT_TIMEOUT_MS,
                        () -> !mActivity.getRecognizerInfo(selectedRecognizerIndex)
                                .mErrorCodesReceived.isEmpty());
            }

            // If this was the last step of the sequence, remove it from the list.
            if (sei.isFinished()) {
                remainingRecognizerIndices.remove(Integer.valueOf(selectedRecognizerIndex));
            } else {
                nextRemainingRecognizerIndex++;
            }
        }

        // Wait until the service has propagated all callbacks
        // and the recognizers' listeners have received them.
        PollingCheck.waitFor(SEQUENCE_TEST_WAIT_TIMEOUT_MS,
                () -> CtsRecognitionService.sInstructedCallbackMethods.isEmpty());
        for (int recognizerIndex = 0;
                recognizerIndex < sequenceExecutionInfos.size();
                recognizerIndex++) {
            int finalRecognizerIndex = recognizerIndex;
            PollingCheck.waitFor(SEQUENCE_TEST_WAIT_TIMEOUT_MS,
                    () -> mActivity.getRecognizerInfo(finalRecognizerIndex)
                            .mCallbackMethodsInvoked.size()
                            >= sequenceExecutionInfos.get(finalRecognizerIndex)
                            .mExpectedClientCallbackMethods.size());
        }

        // Check for all recognizers that:
        //  - the service has executed the expected methods in the given order;
        //  - the expected client callbacks were invoked in the given order.
        //  - the expected error codes were received in the given order.
        for (int recognizerIndex = 0;
                recognizerIndex < sequenceExecutionInfos.size();
                recognizerIndex++) {
            SequenceExecutionInfo sei = sequenceExecutionInfos.get(recognizerIndex);
            SpeechRecognitionActivity.RecognizerInfo ri =
                    mActivity.getRecognizerInfo(recognizerIndex);

            List<RecognizerMethod> expectedServiceMethods = new ArrayList<>();
            for (int step = 0; step < sei.mRecognizerMethodsToCall.size(); step++) {
                if (sei.mExpectedRecognizerServiceMethodsToPropagate.get(step)) {
                    expectedServiceMethods.add(
                            RECOGNIZER_METHOD_DESTROY != sei.mRecognizerMethodsToCall.get(step)
                                    ? sei.mRecognizerMethodsToCall.get(step)
                                    : RECOGNIZER_METHOD_CANCEL);
                }
            }

            if (expectedServiceMethods.isEmpty()) {
                assertThat(CtsRecognitionService.sInvokedRecognizerMethods
                        .containsKey(recognizerIndex)).isFalse();
            } else {
                assertThat(CtsRecognitionService.sInvokedRecognizerMethods.get(recognizerIndex))
                        .isEqualTo(expectedServiceMethods);
            }
            assertThat(ri.mCallbackMethodsInvoked).isEqualTo(sei.mExpectedClientCallbackMethods);
            assertThat(ri.mErrorCodesReceived).isEqualTo(sei.mExpectedErrorCodesReceived);
        }
        assertThat(CtsRecognitionService.sInstructedCallbackMethods).isEmpty();

        mActivity.destroyAllRecognizers();
        mUiDevice.waitForIdle();
        PollingCheck.waitFor(() -> CtsRecognitionService.sBindCount.get() == 0);
    }

    private static void prepareDevice() {
        // Unlock screen.
        runShellCommand("input keyevent KEYCODE_WAKEUP");
        // Dismiss keyguard, in case it's set as "Swipe to unlock".
        runShellCommand("wm dismiss-keyguard");
    }

    /**
     * Data class containing information about a recognizer object used in the activity:
     * <ul>
     *   <li> {@link SequenceExecutionInfo#mRecognizerMethodsToCall} - list of {@link
     *   RecognizerMethod}s to be invoked by the corresponding recognizer;
     *   <li> {@link SequenceExecutionInfo#mCallbackMethodInstructions} - list of {@link
     *   CallbackMethod}s forwarded to the service to be invoked on the corresponding listener;
     *   <li> {@link SequenceExecutionInfo#mExpectedRecognizerServiceMethodsToPropagate} - list of
     *   flags denoting if the callback should be expected after corresponding recognizer methods;
     *   <li> {@link SequenceExecutionInfo#mExpectedClientCallbackMethods} - list of {@link
     *   CallbackMethod}s expected to be run on the corresponding listener.
     *   <li> {@link SequenceExecutionInfo#mNextStep} - next step to be run in the sequence.
     */
    private static class SequenceExecutionInfo {
        private final List<RecognizerMethod> mRecognizerMethodsToCall;
        private final List<CallbackMethod> mCallbackMethodInstructions;
        private final List<Boolean> mExpectedRecognizerServiceMethodsToPropagate;
        private final List<CallbackMethod> mExpectedClientCallbackMethods;
        private final List<Integer> mExpectedErrorCodesReceived;
        private int mNextStep;

        private SequenceExecutionInfo(
                List<RecognizerMethod> recognizerMethodsToCall,
                List<CallbackMethod> callbackMethodInstructions,
                List<Boolean> expectedRecognizerServiceMethodsToPropagate,
                List<CallbackMethod> expectedClientCallbackMethods,
                List<Integer> expectedErrorCodesReceived) {
            mRecognizerMethodsToCall = recognizerMethodsToCall;
            mCallbackMethodInstructions = callbackMethodInstructions;
            mExpectedRecognizerServiceMethodsToPropagate =
                    expectedRecognizerServiceMethodsToPropagate;
            mExpectedClientCallbackMethods = expectedClientCallbackMethods;
            mExpectedErrorCodesReceived = expectedErrorCodesReceived;
            mNextStep = 0;
        }

        private int getNextStep() {
            return mNextStep++;
        }

        private boolean isFinished() {
            return mNextStep >= mRecognizerMethodsToCall.size();
        }

        enum Scenario {
            // Happy scenarios.
            START_STOP_RESULTS,
            START_RESULTS_STOP,
            START_RESULTS_CANCEL,
            START_RESULTS_START_RESULTS,
            START_SEGMENT_ENDOFSESSION,
            START_CANCEL,
            START_START,
            START_STOP_CANCEL,
            START_ERROR_CANCEL,
            START_STOP_DESTROY,
            START_ERROR_DESTROY,
            START_DESTROY_DESTROY,
            START_DETECTION_STOP_RESULTS,

            // Sad scenarios.
            START_ERROR
        }

        private static SequenceExecutionInfo fromScenario(Scenario scenario) {
            switch (scenario) {
                // Happy scenarios.
                case START_STOP_RESULTS:
                    return new SequenceExecutionInfo(
                            /* service methods to call: */ ImmutableList.of(
                            RECOGNIZER_METHOD_START_LISTENING,
                            RECOGNIZER_METHOD_STOP_LISTENING),
                            /* callback methods to call: */ ImmutableList.of(
                            CALLBACK_METHOD_UNSPECIFIED,
                            CALLBACK_METHOD_RESULTS),
                            /* expected service methods propagated: */ ImmutableList.of(
                            true,
                            true),
                            /* expected callback methods invoked: */ ImmutableList.of(
                            CALLBACK_METHOD_RESULTS),
                            /* expected error codes received: */ ImmutableList.of());
                case START_RESULTS_STOP:
                    return new SequenceExecutionInfo(
                            /* service methods to call: */ ImmutableList.of(
                            RECOGNIZER_METHOD_START_LISTENING,
                            RECOGNIZER_METHOD_STOP_LISTENING),
                            /* callback methods to call: */ ImmutableList.of(
                            CALLBACK_METHOD_RESULTS),
                            /* expected service methods propagated: */ ImmutableList.of(
                            true,
                            false),
                            /* expected callback methods invoked: */ ImmutableList.of(
                            CALLBACK_METHOD_RESULTS,
                            CALLBACK_METHOD_ERROR),
                            /* expected error codes received: */ ImmutableList.of(
                            SpeechRecognizer.ERROR_CLIENT));
                case START_RESULTS_CANCEL:
                    return new SequenceExecutionInfo(
                            /* service methods to call: */ ImmutableList.of(
                            RECOGNIZER_METHOD_START_LISTENING,
                            RECOGNIZER_METHOD_CANCEL),
                            /* callback methods to call: */ ImmutableList.of(
                            CALLBACK_METHOD_RESULTS),
                            /* expected service methods propagated: */ ImmutableList.of(
                            true,
                            false),
                            /* expected callback methods invoked: */ ImmutableList.of(
                            CALLBACK_METHOD_RESULTS),
                            /* expected error codes received: */ ImmutableList.of());
                case START_RESULTS_START_RESULTS:
                    return new SequenceExecutionInfo(
                            /* service methods to call: */ ImmutableList.of(
                            RECOGNIZER_METHOD_START_LISTENING,
                            RECOGNIZER_METHOD_START_LISTENING),
                            /* callback methods to call: */ ImmutableList.of(
                            CALLBACK_METHOD_RESULTS,
                            CALLBACK_METHOD_RESULTS),
                            /* expected service methods propagated: */ ImmutableList.of(
                            true,
                            true),
                            /* expected callback methods invoked: */ ImmutableList.of(
                            CALLBACK_METHOD_RESULTS,
                            CALLBACK_METHOD_RESULTS),
                            /* expected error codes received: */ ImmutableList.of());
                case START_SEGMENT_ENDOFSESSION:
                    return new SequenceExecutionInfo(
                            /* service methods to call: */ ImmutableList.of(
                            RECOGNIZER_METHOD_START_LISTENING,
                            RECOGNIZER_METHOD_STOP_LISTENING),
                            /* callback methods to call: */ ImmutableList.of(
                            CALLBACK_METHOD_SEGMENTS_RESULTS,
                            CALLBACK_METHOD_END_SEGMENTED_SESSION),
                            /* expected service methods propagated: */ ImmutableList.of(
                            true,
                            true,
                            true),
                            /* expected callback methods invoked: */ ImmutableList.of(
                            CALLBACK_METHOD_SEGMENTS_RESULTS,
                            CALLBACK_METHOD_END_SEGMENTED_SESSION),
                            /* expected error codes received: */ ImmutableList.of());
                case START_CANCEL:
                    return new SequenceExecutionInfo(
                            /* service methods to call: */ ImmutableList.of(
                            RECOGNIZER_METHOD_START_LISTENING,
                            RECOGNIZER_METHOD_CANCEL),
                            /* callback methods to call: */ ImmutableList.of(
                            CALLBACK_METHOD_UNSPECIFIED,
                            CALLBACK_METHOD_UNSPECIFIED),
                            /* expected service methods propagated: */ ImmutableList.of(
                            true,
                            true),
                            /* expected callback methods invoked: */ ImmutableList.of(),
                            /* expected error codes received: */ ImmutableList.of());
                case START_START:
                    return new SequenceExecutionInfo(
                            /* service methods to call: */ ImmutableList.of(
                            RECOGNIZER_METHOD_START_LISTENING,
                            RECOGNIZER_METHOD_START_LISTENING),
                            /* callback methods to call: */ ImmutableList.of(
                            CALLBACK_METHOD_UNSPECIFIED),
                            /* expected service methods propagated: */ ImmutableList.of(
                            true,
                            false),
                            /* expected callback methods invoked: */ ImmutableList.of(
                            CALLBACK_METHOD_ERROR),
                            /* expected error codes received: */ ImmutableList.of(
                            SpeechRecognizer.ERROR_CLIENT));
                case START_STOP_CANCEL:
                    return new SequenceExecutionInfo(
                            /* service methods to call: */ ImmutableList.of(
                            RECOGNIZER_METHOD_START_LISTENING,
                            RECOGNIZER_METHOD_STOP_LISTENING,
                            RECOGNIZER_METHOD_CANCEL),
                            /* callback methods to call: */ ImmutableList.of(
                            CALLBACK_METHOD_UNSPECIFIED,
                            CALLBACK_METHOD_UNSPECIFIED,
                            CALLBACK_METHOD_UNSPECIFIED),
                            /* expected service methods propagated: */ ImmutableList.of(
                            true,
                            true,
                            true),
                            /* expected callback methods invoked: */ ImmutableList.of(),
                            /* expected error codes received: */ ImmutableList.of());
                case START_ERROR_CANCEL:
                    return new SequenceExecutionInfo(
                            /* service methods to call: */ ImmutableList.of(
                            RECOGNIZER_METHOD_START_LISTENING,
                            RECOGNIZER_METHOD_CANCEL),
                            /* callback methods to call: */ ImmutableList.of(
                            CALLBACK_METHOD_ERROR),
                            /* expected service methods propagated: */ ImmutableList.of(
                            true,
                            false),
                            /* expected callback methods invoked: */ ImmutableList.of(
                            CALLBACK_METHOD_ERROR),
                            /* expected error codes received: */ ImmutableList.of(
                            ERROR_CODE));
                case START_STOP_DESTROY:
                    return new SequenceExecutionInfo(
                            /* service methods to call: */ ImmutableList.of(
                            RECOGNIZER_METHOD_START_LISTENING,
                            RECOGNIZER_METHOD_STOP_LISTENING,
                            RECOGNIZER_METHOD_DESTROY),
                            /* callback methods to call: */ ImmutableList.of(
                            CALLBACK_METHOD_UNSPECIFIED,
                            CALLBACK_METHOD_UNSPECIFIED,
                            CALLBACK_METHOD_UNSPECIFIED),
                            /* expected service methods propagated: */ ImmutableList.of(
                            true,
                            true,
                            true),
                            /* expected callback methods invoked: */ ImmutableList.of(),
                            /* expected error codes received: */ ImmutableList.of());
                case START_ERROR_DESTROY:
                    return new SequenceExecutionInfo(
                            /* service methods to call: */ ImmutableList.of(
                            RECOGNIZER_METHOD_START_LISTENING,
                            RECOGNIZER_METHOD_DESTROY),
                            /* callback methods to call: */ ImmutableList.of(
                            CALLBACK_METHOD_ERROR),
                            /* expected service methods propagated: */ ImmutableList.of(
                            true,
                            false),
                            /* expected callback methods invoked: */ ImmutableList.of(
                            CALLBACK_METHOD_ERROR),
                            /* expected error codes received: */ ImmutableList.of(
                            ERROR_CODE));
                case START_DESTROY_DESTROY:
                    return new SequenceExecutionInfo(
                            /* service methods to call: */ ImmutableList.of(
                            RECOGNIZER_METHOD_START_LISTENING,
                            RECOGNIZER_METHOD_DESTROY,
                            RECOGNIZER_METHOD_DESTROY),
                            /* callback methods to call: */ ImmutableList.of(
                            CALLBACK_METHOD_UNSPECIFIED,
                            CALLBACK_METHOD_UNSPECIFIED),
                            /* expected service methods propagated: */ ImmutableList.of(
                            true,
                            true,
                            false),
                            /* expected callback methods invoked: */ ImmutableList.of(),
                            /* expected error codes received: */ ImmutableList.of());
                case START_DETECTION_STOP_RESULTS:
                    return new SequenceExecutionInfo(
                            /* service methods to call: */ ImmutableList.of(
                            RECOGNIZER_METHOD_START_LISTENING,
                            RECOGNIZER_METHOD_STOP_LISTENING),
                            /* callback methods to call: */ ImmutableList.of(
                            CALLBACK_METHOD_LANGUAGE_DETECTION,
                            CALLBACK_METHOD_RESULTS),
                            /* expected service methods propagated: */ ImmutableList.of(
                            true,
                            true),
                            /* expected callback methods invoked: */ ImmutableList.of(
                            CALLBACK_METHOD_LANGUAGE_DETECTION,
                            CALLBACK_METHOD_RESULTS),
                            /* expected error codes received: */ ImmutableList.of());

                // Sad scenarios.
                case START_ERROR:
                    return new SequenceExecutionInfo(
                            /* service methods to call: */ ImmutableList.of(
                            RECOGNIZER_METHOD_START_LISTENING),
                            /* callback methods to call: */ ImmutableList.of(),
                            /* expected service methods propagated: */ ImmutableList.of(
                            false),
                            /* expected callback methods invoked: */ ImmutableList.of(
                            CALLBACK_METHOD_ERROR),
                            /* expected error codes received: */ ImmutableList.of(
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY));

                default:
                    return new SequenceExecutionInfo(ImmutableList.of(), ImmutableList.of(),
                            ImmutableList.of(), ImmutableList.of(), ImmutableList.of());
            }
        }
    }

    /**
     * Data class containing information about model download listener callback sequence:
     * <ul>
     *   <li> {@link ModelDownloadExecutionInfo#mInstructedCallbacks} - list of {@link
     *   ModelDownloadCallback}s instructed to be invoked by the service on the given listener;
     *   <li> {@link ModelDownloadExecutionInfo#mExpectedCallbacks} - list of {@link
     *   ModelDownloadCallback}s expected to be received at the client's end by the given listener.
     */
    private static class ModelDownloadExecutionInfo {
        private final List<ModelDownloadCallback> mInstructedCallbacks;
        private final List<ModelDownloadCallback> mExpectedCallbacks;

        private ModelDownloadExecutionInfo(
                List<ModelDownloadCallback> instructedCallbacks,
                List<ModelDownloadCallback> expectedCallbacks) {
            mInstructedCallbacks = instructedCallbacks;
            mExpectedCallbacks = expectedCallbacks;
        }

        enum Scenario {
            PROGRESS_PROGRESS_PROGRESS,
            PROGRESS_SUCCESS_PROGRESS,
            SCHEDULED_ERROR,
            ERROR_SCHEDULED
        }

        private static ModelDownloadExecutionInfo fromScenario(Scenario scenario) {
            switch (scenario) {
                case PROGRESS_PROGRESS_PROGRESS:
                    return new ModelDownloadExecutionInfo(
                            /* callbacks to be invoked by the service: */ ImmutableList.of(
                            ModelDownloadCallback.ON_PROGRESS,
                            ModelDownloadCallback.ON_PROGRESS,
                            ModelDownloadCallback.ON_PROGRESS),
                            /* callbacks to be received by the client: */ ImmutableList.of(
                            ModelDownloadCallback.ON_PROGRESS,
                            ModelDownloadCallback.ON_PROGRESS,
                            ModelDownloadCallback.ON_PROGRESS));
                case PROGRESS_SUCCESS_PROGRESS:
                    return new ModelDownloadExecutionInfo(
                            /* callbacks to be invoked by the service: */ ImmutableList.of(
                            ModelDownloadCallback.ON_PROGRESS,
                            ModelDownloadCallback.ON_SUCCESS,
                            ModelDownloadCallback.ON_PROGRESS),
                            /* callbacks to be received by the client: */ ImmutableList.of(
                            ModelDownloadCallback.ON_PROGRESS,
                            ModelDownloadCallback.ON_SUCCESS));
                case SCHEDULED_ERROR:
                    return new ModelDownloadExecutionInfo(
                            /* callbacks to be invoked by the service: */ ImmutableList.of(
                            ModelDownloadCallback.ON_SCHEDULED,
                            ModelDownloadCallback.ON_ERROR),
                            /* callbacks to be received by the client: */ ImmutableList.of(
                            ModelDownloadCallback.ON_SCHEDULED));
                case ERROR_SCHEDULED:
                    return new ModelDownloadExecutionInfo(
                            /* callbacks to be invoked by the service: */ ImmutableList.of(
                            ModelDownloadCallback.ON_ERROR,
                            ModelDownloadCallback.ON_SCHEDULED),
                            /* callbacks to be received by the client: */ ImmutableList.of(
                            ModelDownloadCallback.ON_ERROR));
                default:
                    return new ModelDownloadExecutionInfo(ImmutableList.of(), ImmutableList.of());
            }
        }
    }

    private static class ModelDownloadCallbackLogger implements ModelDownloadListener {
        private List<ModelDownloadCallback> mCallbacks = new ArrayList<>();

        @Override
        public void onProgress(int completedPercent) {
            mCallbacks.add(ModelDownloadCallback.ON_PROGRESS);
        }

        @Override
        public void onSuccess() {
            mCallbacks.add(ModelDownloadCallback.ON_SUCCESS);
        }

        @Override
        public void onScheduled() {
            mCallbacks.add(ModelDownloadCallback.ON_SCHEDULED);
        }

        @Override
        public void onError(int error) {
            mCallbacks.add(ModelDownloadCallback.ON_ERROR);
        }
    }
}

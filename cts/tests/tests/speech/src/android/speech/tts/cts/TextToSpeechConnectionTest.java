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

package android.speech.tts.cts;

import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;
import static android.speech.tts.cts.TextToSpeechConstants.MOCK_TTS_ENGINE;
import static android.speech.tts.cts.TextToSpeechConstants.TTS_TEST_ON_UNBIND_ACTION;
import static android.speech.tts.cts.TextToSpeechConstants.TTS_TEST_SERVICE_CRASH_FLAG_FILE;

import android.annotation.NonNull;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.speech.tts.TextToSpeech;
import android.test.AndroidTestCase;
import android.util.Log;

import com.android.compatibility.common.util.BlockingBroadcastReceiver;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 * Tests for {@link android.speech.tts.TextToSpeech} connection related functionality.
 */
public class TextToSpeechConnectionTest extends AndroidTestCase {
    private static final String LOG_TAG = "TextToSpeechConnectionTest";
    /** TTS initialization waiting time limit millis */
    private static final long TTS_INIT_WAITING_TIME_LIMIT_MILLIS = 10L * 1000L;
    /** Waiting time for broadcast actions limit millis */
    private static final long TTS_SERVICE_BROADCAST_WAITING_TIME_LIMIT_MILLIS = 10L * 1000L;

    private static final int TTS_TEST_SHUTDOWN_CLIENTS_COUNT = 4;

    private Context mContext;
    private PackageManager mPackageManager;

    private TtsInitListener mTtsInitListener;
    private TextToSpeech mTts;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mContext = getContext();
        mPackageManager = getContext().getPackageManager();

        mTtsInitListener = new TtsInitListener();

        disableService(StubTextToSpeechService.class);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        if (mTts != null) {
            mTts.shutdown();
        }

        new File(mContext.getCacheDir(), TTS_TEST_SERVICE_CRASH_FLAG_FILE).delete();

        disableService(ConnectionTestTextToSpeechService.class);
        enableService(StubTextToSpeechService.class);
    }

    public void testConnectionSuccess() throws Exception {

        enableService(ConnectionTestTextToSpeechService.class);

        createTts();
        Integer initStatus = mTtsInitListener.waitForInitStatus();

        assertNotNull(initStatus);
        assertEquals(TextToSpeech.SUCCESS, initStatus.intValue());
    }

    public void testEngineNotAvailableConnectionFailure() throws Exception {

        // Note: ConnectionTest service is not enabled here.

        createTts();
        Integer initStatus = mTtsInitListener.waitForInitStatus();

        assertNotNull(initStatus);
        assertEquals(TextToSpeech.ERROR, initStatus.intValue());
    }

    public void testShutdownStateCleared() throws Exception {

        enableService(ConnectionTestTextToSpeechService.class);

        createTts();
        mTtsInitListener.waitForInitStatus();

        assertNotNull(mTts.getCurrentEngine());

        mTts.shutdown();

        assertNull(mTts.getCurrentEngine());
    }

    public void testClientsShutdownServiceUnbound() throws Exception {

        enableService(ConnectionTestTextToSpeechService.class);

        BlockingBroadcastReceiver unbindReceiver = new BlockingBroadcastReceiver(mContext,
                TTS_TEST_ON_UNBIND_ACTION);
        unbindReceiver.register();

        Supplier<IntStream> ttsClientsStream = () -> IntStream.range(0,
                TTS_TEST_SHUTDOWN_CLIENTS_COUNT);

        TtsInitListener[] initListeners = ttsClientsStream.get().mapToObj(
                i -> new TtsInitListener()).toArray(TtsInitListener[]::new);
        TextToSpeech[] ttsClients = ttsClientsStream.get().mapToObj(
                i -> createTtsForListener(initListeners[i])).toArray(TextToSpeech[]::new);

        ttsClientsStream.get().forEach(i -> initListeners[i].waitForInitStatus());

        ttsClientsStream.get().forEach(i -> ttsClients[i].shutdown());

        assertNotNull(unbindReceiver.awaitForBroadcast(
                TTS_SERVICE_BROADCAST_WAITING_TIME_LIMIT_MILLIS));

        unbindReceiver.unregisterQuietly();
    }

    public void testServiceBindingErrorConnectionFailure() throws Exception {

        enableService(ConnectionTestTextToSpeechService.class);

        // Indicates whether to crash the process upon ConnectionTestTextToSpeechService
        // creation.
        new File(mContext.getCacheDir(), TTS_TEST_SERVICE_CRASH_FLAG_FILE).createNewFile();

        // At this stage the service is enabled - TextToSpeech tries to connect
        createTts();
        // Service crashed and disabled - bindService will fail.
        disableService(ConnectionTestTextToSpeechService.class);

        Integer initStatus = mTtsInitListener.waitForInitStatus();


        assertNotNull(initStatus);
        assertEquals(TextToSpeech.ERROR, initStatus.intValue());
    }

    private TextToSpeech createTtsForListener(TtsInitListener initListener) {
        return new TextToSpeech(mContext, initListener, MOCK_TTS_ENGINE,
                null, /* useFallback= */ false);
    }

    private void createTts() {
        mTts = createTtsForListener(mTtsInitListener);
    }

    private void enableService(@NonNull Class<?> clazz) {
        setServiceEnabledState(clazz, /* enabled= */ true);
    }

    private void disableService(@NonNull Class<?> clazz) {
        setServiceEnabledState(clazz, /* enabled= */ false);
    }

    private void setServiceEnabledState(@NonNull Class<?> clazz, boolean enabled) {
        int newState = enabled ? COMPONENT_ENABLED_STATE_ENABLED : COMPONENT_ENABLED_STATE_DISABLED;

        Log.d(LOG_TAG,
                "Setting service state to: " + enabled + ". Service: " + clazz.getSimpleName());

        ComponentName componentName = new ComponentName(mContext, clazz);
        mPackageManager.setComponentEnabledSetting(componentName, newState, DONT_KILL_APP);
    }

    /**
     * Listener for waiting for TTS engine initialization completion.
     */
    private static class TtsInitListener implements TextToSpeech.OnInitListener {
        private final CountDownLatch mCountDownLatch;
        private Integer mStatus = null;

        TtsInitListener() {
            mCountDownLatch = new CountDownLatch(1);
        }

        public void onInit(int status) {
            Log.d(LOG_TAG, "TtsInitListener: Got Init status: " + status);
            mStatus = status;
            mCountDownLatch.countDown();
        }

        public Integer waitForInitStatus() {
            try {
                boolean resultAccepted = mCountDownLatch.await(TTS_INIT_WAITING_TIME_LIMIT_MILLIS,
                        TimeUnit.MILLISECONDS);
                Log.d(LOG_TAG, "TtsInitListener: End waiting. Expired: " + !resultAccepted);
            } catch (Exception ex) {
                Log.e(LOG_TAG, "TtsInitListener: Error waiting for TTS initialization", ex);
            }

            return mStatus;
        }
    }
}

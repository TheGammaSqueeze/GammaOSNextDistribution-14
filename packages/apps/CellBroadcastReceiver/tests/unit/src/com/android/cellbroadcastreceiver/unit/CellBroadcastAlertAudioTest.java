/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.cellbroadcastreceiver.unit;

import static com.android.cellbroadcastreceiver.CellBroadcastAlertService.SHOW_NEW_ALERT_ACTION;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Intent;
import android.content.res.Configuration;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import com.android.cellbroadcastreceiver.CellBroadcastAlertAudio;
import com.android.cellbroadcastreceiver.CellBroadcastAlertService;
import com.android.cellbroadcastreceiver.CellBroadcastSettings;

import org.junit.After;
import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.util.Locale;

public class CellBroadcastAlertAudioTest extends
        CellBroadcastServiceTestCase<CellBroadcastAlertAudio> {

    private static final String TEST_MESSAGE_BODY = "test message body";
    private static final int[] TEST_VIBRATION_PATTERN = new int[]{0, 1, 0, 1};
    private static final String TEST_MESSAGE_LANGUAGE = "en";
    private static final int TEST_MAX_VOLUME = 1001;
    private static final long MAX_INIT_WAIT_MS = 5000;

    private Configuration mConfiguration = new Configuration();
    private AudioDeviceInfo[] mDevices = new AudioDeviceInfo[0];
    private Object mLock = new Object();
    private boolean mReady;

    private static final int STATE_ALERTING = 1;
    private static final int STATE_STOPPING = 4;

    public CellBroadcastAlertAudioTest() {
        super(CellBroadcastAlertAudio.class);
    }

    private class PhoneStateListenerHandler extends HandlerThread {

        private Runnable mFunction;

        PhoneStateListenerHandler(String name, Runnable func) {
            super(name);
            mFunction = func;
        }

        @Override
        public void onLooperPrepared() {
            mFunction.run();
            setReady(true);
        }
    }

    protected void waitUntilReady() {
        synchronized (mLock) {
            if (!mReady) {
                try {
                    mLock.wait(MAX_INIT_WAIT_MS);
                } catch (InterruptedException ie) {
                }

                if (!mReady) {
                    fail("Telephony tests failed to initialize");
                }
            }
        }
    }

    protected void setReady(boolean ready) {
        synchronized (mLock) {
            mReady = ready;
            mLock.notifyAll();
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
        doReturn(mConfiguration).when(mResources).getConfiguration();
        doReturn(mDevices).when(mMockedAudioManager).getDevices(anyInt());
        enablePreference(CellBroadcastSettings.KEY_ENABLE_ALERT_VIBRATE);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    private Intent createStartAudioIntent() {
        Intent intent = new Intent(mContext, CellBroadcastAlertAudio.class);
        intent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_MESSAGE_BODY,
                TEST_MESSAGE_BODY);
        intent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_VIBRATION_PATTERN_EXTRA,
                TEST_VIBRATION_PATTERN);
        return intent;
    }

    public void testStartService() throws Throwable {
        PhoneStateListenerHandler phoneStateListenerHandler = new PhoneStateListenerHandler(
                "testStartService",
                () -> {
                    doReturn(AudioManager.RINGER_MODE_NORMAL).when(
                            mMockedAudioManager).getRingerMode();

                    Intent intent = new Intent(mContext, CellBroadcastAlertAudio.class);
                    intent.setAction(SHOW_NEW_ALERT_ACTION);
                    intent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_MESSAGE_BODY,
                            TEST_MESSAGE_BODY);
                    intent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_VIBRATION_PATTERN_EXTRA,
                            TEST_VIBRATION_PATTERN);
                    intent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_MESSAGE_LANGUAGE,
                            TEST_MESSAGE_LANGUAGE);
                    intent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_OVERRIDE_DND_EXTRA,
                            true);
                    startService(intent);
                });
        phoneStateListenerHandler.start();
        waitUntilReady();
        verify(mMockedAudioManager).getRingerMode();
        verify(mMockedVibrator).vibrate(any(), any(AudioAttributes.class));
        phoneStateListenerHandler.quit();
    }

    public void testPlayAlertToneInfo() throws Throwable {
        setWatchFeatureEnabled(false);
        doReturn(AudioManager.RINGER_MODE_NORMAL).when(
                mMockedAudioManager).getRingerMode();
        PhoneStateListenerHandler phoneStateListenerHandler = new PhoneStateListenerHandler(
                "testPlayAlertToneInfo",
                () -> {
                    Intent intent = createStartAudioIntent();
                    intent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_TONE_TYPE,
                            CellBroadcastAlertService.AlertType.INFO);
                    startService(intent);
                });
        phoneStateListenerHandler.start();
        waitUntilReady();
        verify(mMockedAudioManager).getRingerMode();
        verify(mMockedVibrator).vibrate(any(), any(AudioAttributes.class));
        phoneStateListenerHandler.quit();
    }

    public void testPlayAlertToneInfoForWatch() throws Throwable {
        setWatchFeatureEnabled(true);
        doReturn(AudioManager.RINGER_MODE_NORMAL).when(
                mMockedAudioManager).getRingerMode();
        PhoneStateListenerHandler phoneStateListenerHandler = new PhoneStateListenerHandler(
                "testPlayAlertToneInfoForWatch",
                () -> {

                    Intent intent = createStartAudioIntent();
                    intent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_TONE_TYPE,
                            CellBroadcastAlertService.AlertType.INFO);
                    startService(intent);
                });
        phoneStateListenerHandler.start();
        waitUntilReady();
        verify(mMockedAudioManager).getRingerMode();
        verify(mMockedVibrator).vibrate(any(), any(AudioAttributes.class));
        phoneStateListenerHandler.quit();
    }

    /**
     * If the user is currently not in a call and the override DND flag is set, the volume will be
     * set to max.
     */
    public void testStartServiceNotInCallOverrideDnd() throws Throwable {
        PhoneStateListenerHandler phoneStateListenerHandler = new PhoneStateListenerHandler(
                "testStartServiceNotInCallOverrideDnd",
                () -> {
                    doReturn(AudioManager.RINGER_MODE_SILENT).when(
                            mMockedAudioManager).getRingerMode();
                    doReturn(TelephonyManager.CALL_STATE_IDLE).when(
                            mMockedTelephonyManager).getCallState();
                    doReturn(TEST_MAX_VOLUME).when(mMockedAudioManager).getStreamMaxVolume(
                            anyInt());

                    Intent intent = new Intent(mContext, CellBroadcastAlertAudio.class);
                    intent.setAction(SHOW_NEW_ALERT_ACTION);
                    intent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_MESSAGE_BODY,
                            TEST_MESSAGE_BODY);
                    intent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_VIBRATION_PATTERN_EXTRA,
                            TEST_VIBRATION_PATTERN);
                    intent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_OVERRIDE_DND_EXTRA, true);
                    startService(intent);
                });
        phoneStateListenerHandler.start();
        waitUntilReady();
        verify(mMockedAudioManager).getRingerMode();
        verify(mMockedVibrator).vibrate(any(), any(AudioAttributes.class));
        verify(mMockedTelephonyManager, atLeastOnce()).getCallState();
        verify(mMockedAudioManager).requestAudioFocus(any(), any(), anyInt(), anyInt());
        verify(mMockedAudioManager).getDevices(anyInt());
        verify(mMockedAudioManager).setStreamVolume(anyInt(), eq(TEST_MAX_VOLUME), anyInt());
        phoneStateListenerHandler.quit();
    }

    public void testStartServiceEnableLedFlash() throws Throwable {
        PhoneStateListenerHandler phoneStateListenerHandler = new PhoneStateListenerHandler(
                "testStartServiceEnableLedFlash",
                () -> {
                    doReturn(AudioManager.RINGER_MODE_NORMAL).when(
                            mMockedAudioManager).getRingerMode();
                    doReturn(true).when(mResources).getBoolean(
                            eq(com.android.cellbroadcastreceiver.R.bool.enable_led_flash));

                    Intent intent = new Intent(mContext, CellBroadcastAlertAudio.class);
                    intent.setAction(SHOW_NEW_ALERT_ACTION);
                    intent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_MESSAGE_BODY,
                            TEST_MESSAGE_BODY);
                    intent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_VIBRATION_PATTERN_EXTRA,
                            TEST_VIBRATION_PATTERN);
                    startService(intent);
                });
        phoneStateListenerHandler.start();
        waitUntilReady();
        // TODO(b/134400042): we can't mock CameraManager because it's final, but let's at least
        //                    make sure the code doesn't crash. If we switch to Mockito 2 this
        //                    will be mockable.
        //verify(mMockedCameraManager).setTorchMode(anyString(), true);
        phoneStateListenerHandler.quit();
    }

    public void testStartServiceSilentRinger() throws Throwable {
        PhoneStateListenerHandler phoneStateListenerHandler = new PhoneStateListenerHandler(
                "testStartServiceSilentRinger",
                () -> {
                    doReturn(AudioManager.RINGER_MODE_SILENT).when(
                            mMockedAudioManager).getRingerMode();

                    Intent intent = new Intent(mContext, CellBroadcastAlertAudio.class);
                    intent.setAction(SHOW_NEW_ALERT_ACTION);
                    intent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_MESSAGE_BODY,
                            TEST_MESSAGE_BODY);
                    intent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_VIBRATION_PATTERN_EXTRA,
                            TEST_VIBRATION_PATTERN);
                    startService(intent);
                });
        phoneStateListenerHandler.start();
        waitUntilReady();
        verify(mMockedAudioManager).getRingerMode();
        verify(mMockedVibrator, times(0)).vibrate(any(), any(AudioAttributes.class));
        phoneStateListenerHandler.quit();
    }

    public void testStartServiceVibrateRinger() throws Throwable {
        PhoneStateListenerHandler phoneStateListenerHandler = new PhoneStateListenerHandler(
                "testStartServiceVibrateRinger",
                () -> {
                    doReturn(AudioManager.RINGER_MODE_VIBRATE).when(
                            mMockedAudioManager).getRingerMode();

                    Intent intent = new Intent(mContext, CellBroadcastAlertAudio.class);
                    intent.setAction(SHOW_NEW_ALERT_ACTION);
                    intent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_MESSAGE_BODY,
                            TEST_MESSAGE_BODY);
                    intent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_VIBRATION_PATTERN_EXTRA,
                            TEST_VIBRATION_PATTERN);
                    startService(intent);
                });
        phoneStateListenerHandler.start();
        waitUntilReady();
        verify(mMockedAudioManager).getRingerMode();
        verify(mMockedVibrator).vibrate(any(), any(AudioAttributes.class));
        phoneStateListenerHandler.quit();
    }

    public void testStartServiceWithTts() throws Throwable {
        PhoneStateListenerHandler phoneStateListenerHandler = new PhoneStateListenerHandler(
                "testStartService",
                () -> {
                    doReturn(AudioManager.RINGER_MODE_NORMAL).when(
                            mMockedAudioManager).getRingerMode();

                    Intent intent = new Intent(mContext, CellBroadcastAlertAudio.class);
                    intent.setAction(SHOW_NEW_ALERT_ACTION);
                    intent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_MESSAGE_BODY,
                            TEST_MESSAGE_BODY);
                    intent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_VIBRATION_PATTERN_EXTRA,
                            TEST_VIBRATION_PATTERN);
                    intent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_MESSAGE_LANGUAGE,
                            TEST_MESSAGE_LANGUAGE);
                    intent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_OVERRIDE_DND_EXTRA,
                            true);
                    startService(intent);
                });
        phoneStateListenerHandler.start();
        waitUntilReady();

        CellBroadcastAlertAudio audio = (CellBroadcastAlertAudio) getService();
        audio.stop();

        Field fieldTts = CellBroadcastAlertAudio.class.getDeclaredField("mTts");
        fieldTts.setAccessible(true);
        TextToSpeech mockTts = mock(TextToSpeech.class);
        fieldTts.set(audio, mockTts);

        Field fieldTtsEngineReady = CellBroadcastAlertAudio.class
                .getDeclaredField("mTtsEngineReady");
        fieldTtsEngineReady.setAccessible(true);
        fieldTtsEngineReady.set(audio, true);

        Field fieldTtsLanguageSupported = CellBroadcastAlertAudio.class
                .getDeclaredField("mTtsLanguageSupported");
        fieldTtsLanguageSupported.setAccessible(true);
        fieldTtsLanguageSupported.set(audio, true);

        Field fieldHandler = CellBroadcastAlertAudio.class.getDeclaredField("mHandler");
        fieldHandler.setAccessible(true);
        Handler handler = (Handler) fieldHandler.get(audio);

        Field fieldSpeaking = CellBroadcastAlertAudio.class
                .getDeclaredField("mIsTextToSpeechSpeaking");
        fieldSpeaking.setAccessible(true);
        ArgumentCaptor<Integer> queueMode = ArgumentCaptor.forClass(Integer.class);

        // Send empty message of ALERT_PAUSE_FINISHED to trigger tts
        handler.sendEmptyMessage(1001);
        for (int i = 0; i < 10; i++) {
            if (fieldSpeaking.getBoolean(audio)) {
                break;
            }
            Thread.sleep(100);
        }

        verify(mockTts, times(2)).speak(any(), queueMode.capture(), any(), any());
        assertEquals(TextToSpeech.QUEUE_FLUSH, queueMode.getAllValues().get(0).intValue());
        assertEquals(2, queueMode.getAllValues().get(1).intValue());

        phoneStateListenerHandler.quit();
        waitUntilReady();
    }

    public void testSetTtsLanguage() throws Throwable {
        PhoneStateListenerHandler phoneStateListenerHandler = new PhoneStateListenerHandler(
                "testStartService",
                () -> {
                    doReturn(AudioManager.RINGER_MODE_NORMAL).when(
                            mMockedAudioManager).getRingerMode();

                    Intent intent = new Intent(mContext, CellBroadcastAlertAudio.class);
                    intent.setAction(SHOW_NEW_ALERT_ACTION);
                    intent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_MESSAGE_BODY,
                            TEST_MESSAGE_BODY);
                    intent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_VIBRATION_PATTERN_EXTRA,
                            TEST_VIBRATION_PATTERN);
                    intent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_MESSAGE_LANGUAGE,
                            TEST_MESSAGE_LANGUAGE);
                    intent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_OVERRIDE_DND_EXTRA,
                            true);
                    startService(intent);
                });
        phoneStateListenerHandler.start();
        waitUntilReady();

        Locale original_locale = Locale.getDefault();
        Locale.setDefault(Locale.UK);

        CellBroadcastAlertAudio audio = (CellBroadcastAlertAudio) getService();

        Field fieldTts = CellBroadcastAlertAudio.class.getDeclaredField("mTts");
        fieldTts.setAccessible(true);
        TextToSpeech mockTts = mock(TextToSpeech.class);
        fieldTts.set(audio, mockTts);

        audio.onInit(TextToSpeech.SUCCESS);

        ArgumentCaptor<Locale> localeArgumentCaptor = ArgumentCaptor.forClass(Locale.class);
        verify(mockTts, times(1)).setLanguage(localeArgumentCaptor.capture());
        assertEquals(Locale.UK, localeArgumentCaptor.getValue());

        Locale.setDefault(original_locale);
    }

    /**
     * When an alert is triggered while an alert is already happening, the system needs to stop
     * the previous alert.
     */
    public void testStartServiceAndStop() throws Throwable {
        Intent intent = new Intent(mContext, CellBroadcastAlertAudio.class);
        intent.setAction(SHOW_NEW_ALERT_ACTION);
        intent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_MESSAGE_BODY,
                TEST_MESSAGE_BODY);
        intent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_VIBRATION_PATTERN_EXTRA,
                TEST_VIBRATION_PATTERN);
        doReturn(AudioManager.RINGER_MODE_NORMAL).when(
                mMockedAudioManager).getRingerMode();

        PhoneStateListenerHandler phoneStateListenerHandler = new PhoneStateListenerHandler(
                "testStartServiceStop",
                () -> {
                    startService(intent);
                    startService(intent);
                });
        phoneStateListenerHandler.start();
        waitUntilReady();
        verify(mMockedVibrator, atLeastOnce()).cancel();
        phoneStateListenerHandler.quit();
        waitUntilReady();
    }

    /**
     * Even the user is currently not in a call and the override DND flag is set, the alert is
     * set to mute.
     */
    public void testStartServiceMuteWithOverrideDnd() throws Throwable {
        PhoneStateListenerHandler phoneStateListenerHandler = new PhoneStateListenerHandler(
                "testStartServiceMuteWithOverrideDnd",
                () -> {
                    doReturn(AudioManager.RINGER_MODE_SILENT).when(
                            mMockedAudioManager).getRingerMode();
                    doReturn(TelephonyManager.CALL_STATE_IDLE).when(
                            mMockedTelephonyManager).getCallState();
                    doReturn(TEST_MAX_VOLUME).when(mMockedAudioManager).getStreamMaxVolume(
                            anyInt());

                    Intent intent = new Intent(mContext, CellBroadcastAlertAudio.class);
                    intent.setAction(SHOW_NEW_ALERT_ACTION);
                    intent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_MESSAGE_BODY,
                            TEST_MESSAGE_BODY);
                    intent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_VIBRATION_PATTERN_EXTRA,
                            TEST_VIBRATION_PATTERN);
                    intent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_OVERRIDE_DND_EXTRA, true);
                    intent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_TONE_TYPE,
                            CellBroadcastAlertService.AlertType.MUTE);
                    startService(intent);
                });
        phoneStateListenerHandler.start();
        waitUntilReady();
        verify(mMockedAudioManager).getRingerMode();
        verify(mMockedVibrator, never()).vibrate(any(), any(AudioAttributes.class));
        verify(mMockedTelephonyManager, never()).getCallState();
        verify(mMockedAudioManager, never()).requestAudioFocus(any(), any(), anyInt(), anyInt());
        phoneStateListenerHandler.quit();
    }

    public void testPlayAlertDuration() throws Throwable {
        int duration = 15 * 1000;
        int tolerance = 100;
        PhoneStateListenerHandler phoneStateListenerHandler = new PhoneStateListenerHandler(
                "testPlayAlertDuration",
                () -> {
                    startService(null);
                });
        phoneStateListenerHandler.start();
        waitUntilReady();

        CellBroadcastAlertAudio audio = (CellBroadcastAlertAudio) getService();

        Handler mockHandler = spy(new Handler(Looper.getMainLooper()));
        audio.mHandler = mockHandler;
        MediaPlayer mockMediaPlayer = mock(MediaPlayer.class);
        audio.mMediaPlayerInjected = mockMediaPlayer;
        Intent intent = createStartAudioIntent();
        intent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_TONE_TYPE,
                CellBroadcastAlertService.AlertType.INFO);
        intent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_OVERRIDE_DND_EXTRA, true);
        intent.putExtra(CellBroadcastAlertAudio.ALERT_AUDIO_DURATION, duration);

        ArgumentCaptor<Long> capTime = ArgumentCaptor.forClass(Long.class);
        InOrder inOrder = inOrder(mockMediaPlayer, mockHandler);
        long expTime = SystemClock.uptimeMillis() + duration;
        audio.handleStartIntent(intent);

        inOrder.verify(mockMediaPlayer).prepare();
        inOrder.verify(mockHandler).sendMessageAtTime(any(), capTime.capture());
        inOrder.verify(mockMediaPlayer).start();
        assertTrue((capTime.getValue() - expTime) < tolerance);
    }

    public void testCallConnectedDuringPlayAlert() throws Throwable {
        PhoneStateListenerHandler phoneStateListenerHandler = new PhoneStateListenerHandler(
                "testCallConnectedDuringPlayAlert",
                () -> {
                    startService(null);
                });
        phoneStateListenerHandler.start();
        waitUntilReady();

        ArgumentCaptor<PhoneStateListener> phoneStateListenerCaptor =
                ArgumentCaptor.forClass(PhoneStateListener.class);
        verify(mMockedTelephonyManager).listen(phoneStateListenerCaptor.capture(),
                eq(PhoneStateListener.LISTEN_CALL_STATE));
        PhoneStateListener mPhoneStateListener = phoneStateListenerCaptor.getValue();

        CellBroadcastAlertAudio audio = (CellBroadcastAlertAudio) getService();
        doReturn(AudioManager.RINGER_MODE_NORMAL).when(mMockedAudioManager).getRingerMode();
        doReturn(TelephonyManager.CALL_STATE_IDLE).when(mMockedTelephonyManager).getCallState();
        // prevent the IllegalStateException during the playAlertTone
        audio.mMediaPlayerInjected = mock(MediaPlayer.class);

        Intent intent = createStartAudioIntent();
        audio.handleStartIntent(intent);
        assertEquals(STATE_ALERTING, audio.getState());

        // Call state change to OFFHOOK, stop audio play
        mPhoneStateListener.onCallStateChanged(TelephonyManager.CALL_STATE_OFFHOOK, "");
        assertEquals(STATE_STOPPING, audio.getState());

        phoneStateListenerHandler.quit();
    }

    public void testOnError() throws Throwable {
        PhoneStateListenerHandler phoneStateListenerHandler = new PhoneStateListenerHandler(
                "testOnError",
                () -> {
                    startService(null);
                });
        phoneStateListenerHandler.start();
        waitUntilReady();

        doReturn(AudioManager.RINGER_MODE_NORMAL).when(mMockedAudioManager).getRingerMode();
        CellBroadcastAlertAudio audio = (CellBroadcastAlertAudio) getService();
        Handler mockHandler = spy(new Handler(Looper.getMainLooper()));
        audio.mHandler = mockHandler;
        MediaPlayer mockMediaPlayer = mock(MediaPlayer.class);
        audio.mMediaPlayerInjected = mockMediaPlayer;

        Intent intent = createStartAudioIntent();
        audio.handleStartIntent(intent);

        verify(mockHandler, never()).sendMessageAtTime(any(), anyLong());

        ArgumentCaptor<MediaPlayer.OnErrorListener> onErrorListenerArgumentCaptor =
                ArgumentCaptor.forClass(MediaPlayer.OnErrorListener.class);
        verify(mockMediaPlayer).setOnErrorListener(onErrorListenerArgumentCaptor.capture());
        MediaPlayer.OnErrorListener onErrorListener = onErrorListenerArgumentCaptor.getValue();
        onErrorListener.onError(mockMediaPlayer, 0, 0);

        // If possible will check message's 'what' equals ‘ALERT_SOUND_FINISHED’ in the future.
        verify(mockHandler, times(1)).sendMessageAtTime(any(), anyLong());

        phoneStateListenerHandler.quit();
    }

    public void testOnCompletion() throws Throwable {
        PhoneStateListenerHandler phoneStateListenerHandler = new PhoneStateListenerHandler(
                "testOnCompletion",
                () -> {
                    startService(null);
                });
        phoneStateListenerHandler.start();
        waitUntilReady();

        doReturn(AudioManager.RINGER_MODE_NORMAL).when(mMockedAudioManager).getRingerMode();
        CellBroadcastAlertAudio audio = (CellBroadcastAlertAudio) getService();
        Handler mockHandler = spy(new Handler(Looper.getMainLooper()));
        audio.mHandler = mockHandler;
        MediaPlayer mockMediaPlayer = mock(MediaPlayer.class);
        audio.mMediaPlayerInjected = mockMediaPlayer;

        Intent intent = createStartAudioIntent();
        audio.handleStartIntent(intent);

        verify(mockHandler, never()).sendMessageAtTime(any(), anyLong());

        ArgumentCaptor<MediaPlayer.OnCompletionListener> OnCompletionListenerArgumentCaptor =
                ArgumentCaptor.forClass(MediaPlayer.OnCompletionListener.class);
        verify(mockMediaPlayer).setOnCompletionListener(
                OnCompletionListenerArgumentCaptor.capture());
        MediaPlayer.OnCompletionListener onCompletionListener =
                OnCompletionListenerArgumentCaptor.getValue();
        onCompletionListener.onCompletion(mockMediaPlayer);

        // If possible will check message's 'what' equals ‘ALERT_SOUND_FINISHED’ in the future.
        verify(mockHandler, times(1)).sendMessageAtTime(any(), anyLong());

        phoneStateListenerHandler.quit();
    }
}

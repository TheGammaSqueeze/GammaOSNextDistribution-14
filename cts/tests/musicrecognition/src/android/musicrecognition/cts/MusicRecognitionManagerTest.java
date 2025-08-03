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
package android.musicrecognition.cts;

import static androidx.test.InstrumentationRegistry.getContext;
import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.app.AppOpsManager;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaMetadata;
import android.media.MediaRecorder;
import android.media.musicrecognition.MusicRecognitionManager;
import android.media.musicrecognition.RecognitionRequest;
import android.os.Binder;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.RequiredServiceRule;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for {@link MusicRecognitionManager}.
 */
@RunWith(AndroidJUnit4.class)
public class MusicRecognitionManagerTest {
    private static final String TAG = MusicRecognitionManagerTest.class.getSimpleName();
    private static final long VERIFY_TIMEOUT_MS = 40_000;
    private static final long VERIFY_APPOP_CHANGE_TIMEOUT_MS = 10000;

    @Rule public TestName mTestName = new TestName();
    @Rule
    public final RequiredServiceRule mRequiredServiceRule =
            new RequiredServiceRule(Context.MUSIC_RECOGNITION_SERVICE);

    private MusicRecognitionManager mManager;
    private CtsMusicRecognitionService.Watcher mWatcher;
    @Mock MusicRecognitionManager.RecognitionCallback mCallback;
    @Captor ArgumentCaptor<Bundle> mBundleCaptor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        // Grant permission to call the api.
        escalateTestPermissions();

        mManager = getContext().getSystemService(MusicRecognitionManager.class);
        mWatcher = CtsMusicRecognitionService.setWatcher();
        // Tell MusicRecognitionManagerService to use our no-op service instead.
        setService(CtsMusicRecognitionService.SERVICE_COMPONENT.flattenToString());
    }

    @After
    public void tearDown() {
        resetService();
        mWatcher = null;
        CtsMusicRecognitionService.clearWatcher();
        getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
    }

    @Test
    public void testRecognitionRequest() {
        AudioRecord record = new AudioRecord(MediaRecorder.AudioSource.MIC, 16_000,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, 256_000);

        RecognitionRequest request = new RecognitionRequest.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setInternalCapturePreset(MediaRecorder.AudioSource.MIC)
                        .build())
                .setAudioFormat(record.getFormat())
                .setCaptureSession(record.getAudioSessionId())
                .setMaxAudioLengthSeconds(8)
                // Drop the first second of audio.
                .setIgnoreBeginningFrames(16_000)
                .build();

        assertThat(request.getAudioFormat()).isEqualTo(record.getFormat());
        assertThat(request.getMaxAudioLengthSeconds()).isEqualTo(8);
        assertThat(request.getCaptureSession()).isEqualTo(record.getAudioSessionId());
        assertThat(request.getIgnoreBeginningFrames()).isEqualTo(16_000);
        assertThat(request.getAudioAttributes()).isEqualTo(record.getAudioAttributes());
    }

    @Test
    public void testOnRecognitionFailed() throws Exception {
        mWatcher.failureCode = MusicRecognitionManager.RECOGNITION_FAILED_NO_CONNECTIVITY;

        invokeMusicRecognitionApi();

        verify(mCallback, timeout(VERIFY_TIMEOUT_MS)).onAudioStreamClosed();
        verify(mCallback, timeout(VERIFY_TIMEOUT_MS)).onRecognitionFailed(any(),
                eq(MusicRecognitionManager.RECOGNITION_FAILED_NO_CONNECTIVITY));
        verify(mCallback, never()).onRecognitionSucceeded(any(), any(), any());
    }

    @Test
    public void testOnRecognitionSucceeded() throws Exception {
        mWatcher.result = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "artist")
                .putString(MediaMetadata.METADATA_KEY_TITLE, "title")
                .build();

        RecognitionRequest request = invokeMusicRecognitionApi();

        verify(mCallback, timeout(VERIFY_TIMEOUT_MS)).onAudioStreamClosed();
        verify(mCallback, timeout(VERIFY_TIMEOUT_MS)).onRecognitionSucceeded(eq(request),
                eq(mWatcher.result), eq(null));
        verify(mCallback, never()).onRecognitionFailed(any(), anyInt());
        // 8 seconds minus 16k frames dropped from the beginning.
        assertThat(mWatcher.stream).hasLength(256_000 - 32_000);
    }

    @Test
    public void testRemovesBindersFromBundle() throws Exception {
        ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
        mWatcher.result = new MediaMetadata.Builder().build();
        mWatcher.resultExtras = new Bundle();
        mWatcher.resultExtras.putString("stringKey", "stringValue");
        mWatcher.resultExtras.putBinder("binderKey", new Binder());
        mWatcher.resultExtras.putParcelable("fdKey", pipe[0]);

        RecognitionRequest request = invokeMusicRecognitionApi();

        verify(mCallback, timeout(VERIFY_TIMEOUT_MS)).onAudioStreamClosed();
        verify(mCallback, timeout(VERIFY_TIMEOUT_MS)).onRecognitionSucceeded(eq(request),
                eq(mWatcher.result), mBundleCaptor.capture());

        assertThat(mBundleCaptor.getValue().getString("stringKey")).isEqualTo("stringValue");
        // Binder and file descriptor removed.
        assertThat(mBundleCaptor.getValue().size()).isEqualTo(1);

        pipe[0].close();
        pipe[1].close();
    }

    /**
     * Verifies the shell override is only allowed when the caller of the api is also the owner of
     * the override service.
     */
    @Test
    public void testDoesntBindToForeignService() {
        setService(
                "android.musicrecognition.cts2/android.musicrecognition.cts2"
                        + ".OutsideOfPackageService");

        invokeMusicRecognitionApi();

        verify(mCallback, timeout(VERIFY_TIMEOUT_MS)).onRecognitionFailed(any(),
                eq(MusicRecognitionManager.RECOGNITION_FAILED_SERVICE_UNAVAILABLE));
        verify(mCallback, never()).onRecognitionSucceeded(any(), any(), any());
    }

    @Test
    public void testRecordAudioOpsAreTracked() {
        mWatcher.result = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "artist")
                .putString(MediaMetadata.METADATA_KEY_TITLE, "title")
                .build();

        final String packageName = CtsMusicRecognitionService.SERVICE_PACKAGE;
        final int uid = Process.myUid();


        final Context context = getInstrumentation().getContext();

        final AppOpsManager appOpsManager = context.getSystemService(AppOpsManager.class);
        final AppOpsManager.OnOpActiveChangedListener listener = mock(
                AppOpsManager.OnOpActiveChangedListener.class);

        // Assert the app op is not started
        assertFalse(appOpsManager.isOpActive(AppOpsManager.OPSTR_RECORD_AUDIO, uid, packageName));

        // Start watching for record audio op
        appOpsManager.startWatchingActive(new String[] { AppOpsManager.OPSTR_RECORD_AUDIO },
                context.getMainExecutor(), listener);

        // Invoke API
        RecognitionRequest request = invokeMusicRecognitionApi();

        // The app op should start
        String expectedAttributionTag = "CtsMusicRecognitionAttributionTag";
        verify(listener, timeout(VERIFY_APPOP_CHANGE_TIMEOUT_MS))
                .onOpActiveChanged(eq(AppOpsManager.OPSTR_RECORD_AUDIO),
                eq(uid), eq(packageName), eq(expectedAttributionTag), eq(true),
                        anyInt(), anyInt());

        // Wait for streaming to finish.
        reset(listener);
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // The app op should finish
        verify(listener, timeout(VERIFY_APPOP_CHANGE_TIMEOUT_MS))
                .onOpActiveChanged(eq(AppOpsManager.OPSTR_RECORD_AUDIO),
                        eq(uid), eq(packageName), eq(expectedAttributionTag), eq(false),
                        anyInt(), anyInt());


        // Start with a clean slate
        reset(listener);

        // Stop watching for app op
        appOpsManager.stopWatchingActive(listener);

        // No other callbacks expected
        verify(listener, timeout(VERIFY_APPOP_CHANGE_TIMEOUT_MS).times(0))
                .onOpActiveChanged(eq(AppOpsManager.OPSTR_RECORD_AUDIO),
                        anyInt(), anyString(), anyBoolean());
    }


    private RecognitionRequest invokeMusicRecognitionApi() {
        Log.d(TAG, "Invoking service.");

        AudioRecord record = new AudioRecord(MediaRecorder.AudioSource.MIC, 16_000,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, 256_000);

        RecognitionRequest request = new RecognitionRequest.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setInternalCapturePreset(MediaRecorder.AudioSource.MIC)
                        .build())
                .setAudioFormat(record.getFormat())
                .setCaptureSession(record.getAudioSessionId())
                .setMaxAudioLengthSeconds(8)
                // Drop the first second of audio.
                .setIgnoreBeginningFrames(16_000)
                .build();
        mManager.beginStreamingSearch(
                request,
                MoreExecutors.directExecutor(),
                mCallback);
        Log.d(TAG, "Invoking service done.");
        return request;
    }

    /**
     * Sets the music recognition service.
     */
    private static void setService(@NonNull String service) {
        Log.d(TAG, "Setting music recognition service to " + service);
        int userId = android.os.Process.myUserHandle().getIdentifier();
        runShellCommand(
                "cmd music_recognition set temporary-service %d %s 60000", userId, service);
    }

    private static void resetService() {
        Log.d(TAG, "Resetting music recognition service");
        int userId = android.os.Process.myUserHandle().getIdentifier();
        runShellCommand("cmd music_recognition set temporary-service %d", userId);
    }

    private static void escalateTestPermissions() {
        getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                "android.permission.MANAGE_MUSIC_RECOGNITION");
    }
}

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

/**
 * Tests for {@link android.app.wallpapereffectsgeneration.WallpaperEffectsGenerationManager}
 *
 * atest CtsWallpaperEffectsGenerationServiceTestCases
 */
package android.wallpapereffectsgeneration.cts;

import static androidx.test.InstrumentationRegistry.getContext;
import static com.android.compatibility.common.util.ShellUtils.runShellCommand;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.internal.verification.VerificationModeFactory.atMost;

import android.app.wallpapereffectsgeneration.CinematicEffectRequest;
import android.app.wallpapereffectsgeneration.CinematicEffectResponse;
import android.app.wallpapereffectsgeneration.WallpaperEffectsGenerationManager;
import android.app.wallpapereffectsgeneration.WallpaperEffectsGenerationManager.CinematicEffectListener;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Process;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.RequiredServiceRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Tests for {@link WallpaperEffectsGenerationManager}
 *
 * atest CtsWallpaperEffectsGenerationServiceTestCases
 */

@RunWith(AndroidJUnit4.class)
public class WallpaperEffectsGenerationManagerTest {
    private static final String TAG = "WallpaperEffectsGenerationTest";
    private static final boolean DEBUG = false;
    private static final long VERIFY_TIMEOUT_MS = 5_000;
    private static final long SERVICE_LIFECYCLE_TIMEOUT_MS = 20_000;

    @Rule
    public final RequiredServiceRule mRequiredServiceRule =
            new RequiredServiceRule(Context.WALLPAPER_EFFECTS_GENERATION_SERVICE);

    private WallpaperEffectsGenerationManager mManager;
    private CtsWallpaperEffectsGenerationService.Watcher mWatcher;
    private CinematicEffectRequest mInitialTaskRequest =
            createCinematicEffectRequest("initial-task");

    @Before
    public void setup() throws Exception {
        mWatcher = CtsWallpaperEffectsGenerationService.setWatcher();
        mManager = getContext().getSystemService(WallpaperEffectsGenerationManager.class);
        setService(CtsWallpaperEffectsGenerationService.SERVICE_NAME);
        // The wallpaper effects generation services are created lazily,
        // call one method to start the service for these tests.
        mWatcher.verifier = Mockito.mock(CtsWallpaperEffectsGenerationService.class);
        reset(mWatcher.verifier);
        CtsCinematicEffectListener ctsCinematicEffectListener = new CtsCinematicEffectListener();
        mManager.generateCinematicEffect(mInitialTaskRequest,
                Executors.newSingleThreadExecutor(),
                ctsCinematicEffectListener);
        await(mWatcher.created, "Waiting for onCreated()");
        // Check the request the server received is the request sent.
        await(ctsCinematicEffectListener.mOkResponse, "wait for initial task returned");
        verifyService().onGenerateCinematicEffect(eq(mInitialTaskRequest));
    }

    @After
    public void tearDown() throws Exception {
        setService(null);
        await(mWatcher.destroyed, "Waiting for onDestroyed()");
        mWatcher = null;
        CtsWallpaperEffectsGenerationService.clearWatcher();
    }

    @Test
    public void testGenerateCinematicEffect_okResponse() {
        mWatcher.verifier = Mockito.mock(CtsWallpaperEffectsGenerationService.class);
        reset(mWatcher.verifier);
        assertNotNull(mManager);
        CinematicEffectRequest request = createSimpleCinematicEffectRequest("ok-task");
        CtsCinematicEffectListener ctsCinematicEffectListener = new CtsCinematicEffectListener();
        mManager.generateCinematicEffect(request, Executors.newSingleThreadExecutor(),
                ctsCinematicEffectListener);
        await(ctsCinematicEffectListener.mOkResponse, "Result is okay");
        verifyService().onGenerateCinematicEffect(eq(request));
    }

    @Test
    public void testGenerateCinematicEffect_errorResponse() {
        mWatcher.verifier = Mockito.mock(CtsWallpaperEffectsGenerationService.class);
        reset(mWatcher.verifier);
        assertNotNull(mManager);
        CinematicEffectRequest request = createSimpleCinematicEffectRequest("error-task");
        CtsCinematicEffectListener ctsCinematicEffectListener = new CtsCinematicEffectListener();
        mManager.generateCinematicEffect(request, Executors.newSingleThreadExecutor(),
                ctsCinematicEffectListener);
        await(ctsCinematicEffectListener.mErrorResponse, "Result is error");
        verifyService().onGenerateCinematicEffect(eq(request));
    }

    @Test
    public void testGenerateCinematicEffect_pendingResponse() {
        mWatcher.verifier = Mockito.mock(CtsWallpaperEffectsGenerationService.class);
        reset(mWatcher.verifier);
        assertNotNull(mManager);
        CinematicEffectRequest request1 = createCinematicEffectRequest("pending-task-id");
        CinematicEffectRequest request2 = createCinematicEffectRequest("pending-task-id");
        CtsCinematicEffectListener ctsCinematicEffectListener1 = new CtsCinematicEffectListener();
        CtsCinematicEffectListener ctsCinematicEffectListener2 = new CtsCinematicEffectListener();
        mManager.generateCinematicEffect(request1, Executors.newSingleThreadExecutor(),
                ctsCinematicEffectListener1);
        mManager.generateCinematicEffect(request2, Executors.newSingleThreadExecutor(),
                ctsCinematicEffectListener2);
        await(ctsCinematicEffectListener2.mPendingResponse,
                "2nd result returned and listener invoked");
        await(ctsCinematicEffectListener1.mOkResponse,
                "1st request returned after long processing");
        verifyService().onGenerateCinematicEffect(eq(request1));
        verify(mWatcher.verifier, atMost(1)).onGenerateCinematicEffect(any());
    }

    @Test
    public void testGenerateCinematicEffect_tooManyRequestsResponse() {
        mWatcher.verifier = Mockito.mock(CtsWallpaperEffectsGenerationService.class);
        reset(mWatcher.verifier);
        assertNotNull(mManager);
        CinematicEffectRequest request1 = createCinematicEffectRequest("pending-task-id");
        CinematicEffectRequest request2 = createCinematicEffectRequest("other-task-id");
        CtsCinematicEffectListener ctsCinematicEffectListener1 = new CtsCinematicEffectListener();
        CtsCinematicEffectListener ctsCinematicEffectListener2 = new CtsCinematicEffectListener();
        mManager.generateCinematicEffect(request1, Executors.newSingleThreadExecutor(),
                ctsCinematicEffectListener1);
        mManager.generateCinematicEffect(request2, Executors.newSingleThreadExecutor(),
                ctsCinematicEffectListener2);
        await(ctsCinematicEffectListener2.mTooManyRequestsResponse,
                "Second request immediately fail with too many requests response");
        await(ctsCinematicEffectListener1.mOkResponse,
                "1st request returned after long processing");
        verifyService().onGenerateCinematicEffect(eq(request1));
        verify(mWatcher.verifier, atMost(1)).onGenerateCinematicEffect(any());
    }

    private static final class CtsCinematicEffectListener implements CinematicEffectListener {
        CountDownLatch mOkResponse = new CountDownLatch(1);
        CountDownLatch mErrorResponse = new CountDownLatch(1);
        CountDownLatch mPendingResponse = new CountDownLatch(1);
        CountDownLatch mTooManyRequestsResponse = new CountDownLatch(1);

        @Override
        public void onCinematicEffectGenerated(CinematicEffectResponse cinematicEffectResponse) {
            Log.d(TAG, "cinematic effect response taskId = " + cinematicEffectResponse.getTaskId()
                    + ", status code = " + cinematicEffectResponse.getStatusCode());
            if (cinematicEffectResponse.getStatusCode()
                    == CinematicEffectResponse.CINEMATIC_EFFECT_STATUS_OK) {
                mOkResponse.countDown();
            } else if (cinematicEffectResponse.getStatusCode()
                    == CinematicEffectResponse.CINEMATIC_EFFECT_STATUS_PENDING) {
                mPendingResponse.countDown();
            } else if (cinematicEffectResponse.getStatusCode()
                    == CinematicEffectResponse.CINEMATIC_EFFECT_STATUS_TOO_MANY_REQUESTS) {
                mTooManyRequestsResponse.countDown();
            } else if (cinematicEffectResponse.getStatusCode()
                    == CinematicEffectResponse.CINEMATIC_EFFECT_STATUS_ERROR) {
                mErrorResponse.countDown();
            }
        }
    }

    private CinematicEffectRequest createCinematicEffectRequest(String taskId) {
        Bitmap bmp = Bitmap.createBitmap(32, 48, Bitmap.Config.ARGB_8888);
        return new CinematicEffectRequest(taskId, bmp);
    }

    private CtsWallpaperEffectsGenerationService verifyService() {
        return verify(mWatcher.verifier, timeout(VERIFY_TIMEOUT_MS));
    }

    private void setService(String service) {
        if (DEBUG) {
            Log.d(TAG, "Setting WallpaperEffectsGeneration service to " + service);
        }
        int userId = Process.myUserHandle().getIdentifier();
        String shellCommand = "";
        if (service != null) {
            shellCommand = "cmd wallpaper_effects_generation set temporary-service "
                    + userId + " " + service + " 60000";
        } else {
            shellCommand = "cmd wallpaper_effects_generation set temporary-service " + userId;
        }
        if (DEBUG) {
            Log.d(TAG, "runShellCommand(): " + shellCommand);
        }
        runShellCommand(shellCommand);
    }

    private void await(@NonNull CountDownLatch latch, @NonNull String message) {
        try {
            assertWithMessage(message).that(
                    latch.await(SERVICE_LIFECYCLE_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while: " + message);
        }
    }

    private CinematicEffectRequest createSimpleCinematicEffectRequest(String taskId) {
        return new CinematicEffectRequest(taskId,
                Bitmap.createBitmap(32, 48, Bitmap.Config.ARGB_8888));
    }
}

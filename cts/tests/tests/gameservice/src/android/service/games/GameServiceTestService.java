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

package android.service.games;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.service.games.GameSession.ScreenshotCallback;
import android.service.games.TestGameSessionService.TestGameSession;
import android.service.games.testing.ActivityResult;
import android.service.games.testing.GameSessionEventInfo;
import android.service.games.testing.IGameServiceTestService;
import android.service.games.testing.OnSystemBarVisibilityChangedInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.compatibility.common.util.PollingCheck;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Service allowing external apps to verify the state of {@link TestGameService} and {@link
 * TestGameSessionService}.
 */
public final class GameServiceTestService extends Service {

    private static final long SCREENSHOT_CALLBACK_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(15);

    @Nullable
    private ActivityResult mLastActivityResult;
    private final IGameServiceTestService.Stub mStub = new IGameServiceTestService.Stub() {
        private final Handler mHandler = new Handler(Looper.getMainLooper());

        @Override
        public boolean isGameServiceConnected() {
            return TestGameService.isConnected();
        }

        @Override
        public void setGamePackageNames(List<String> gamePackageNames) {
            TestGameService.setGamePackages(gamePackageNames);
        }

        @Override
        public List<String> getActiveSessions() {
            return ImmutableList.copyOf(TestGameSessionService.getActiveSessions());
        }

        @Override
        public List<GameSessionEventInfo> getGameSessionEventHistory() {
            return ImmutableList.copyOf(TestGameSessionService.getGameSessionEventHistory());
        }

        @Override
        public void resetState() {
            TestGameService.reset();
            TestGameSessionService.reset();
            mLastActivityResult = null;

            setGameServiceComponentEnabled(true);
            setGameSessionServiceComponentEnabled(true);
        }

        @Override
        public int getFocusedTaskId() {
            TestGameSession focusedGameSession = TestGameSessionService.getFocusedSession();
            if (focusedGameSession == null) {
                return -1;
            }

            return focusedGameSession.getTaskId();
        }

        @Override
        public void startGameSessionActivity(Intent intent, Bundle options) {
            TestGameSession focusedGameSession = TestGameSessionService.getFocusedSession();
            if (focusedGameSession == null) {
                return;
            }

            focusedGameSession.startActivityFromGameSessionForResult(intent, options,
                    mHandler::post, new GameSessionActivityCallback() {
                        @Override
                        public void onActivityResult(int resultCode,
                                @Nullable Intent data) {
                            mLastActivityResult = ActivityResult.forSuccess(
                                    focusedGameSession.getPackageName(),
                                    resultCode,
                                    data);
                        }

                        @Override
                        public void onActivityStartFailed(@NonNull Throwable t) {
                            mLastActivityResult = ActivityResult.forError(
                                    focusedGameSession.getPackageName(), t);
                        }
                    });
        }

        @Override
        public ActivityResult getLastActivityResult() {
            if (mLastActivityResult == null) {
                PollingCheck.waitFor(() -> mLastActivityResult != null);
            }

            return mLastActivityResult;
        }

        @Override
        public Rect getTouchableOverlayBounds() {
            TestGameSession focusedGameSession = TestGameSessionService.getFocusedSession();
            if (focusedGameSession == null) {
                return null;
            }

            return focusedGameSession.getTouchableBounds();
        }

        @Override
        public void restartFocusedGameSession() {
            TestGameSession focusedGameSession = TestGameSessionService.getFocusedSession();
            if (focusedGameSession == null) {
                return;
            }
            focusedGameSession.restartGame();
        }

        @Override
        public boolean takeScreenshotForFocusedGameSession() {
            boolean result = false;
            TestGameSession focusedGameSession = TestGameSessionService.getFocusedSession();
            if (focusedGameSession != null) {
                CountDownLatch countDownLatch = new CountDownLatch(1);
                final boolean[] ret = new boolean[1];
                ScreenshotCallback callback =
                        new ScreenshotCallback() {
                            @Override
                            public void onFailure(int statusCode) {
                                ret[0] = false;
                                countDownLatch.countDown();
                            }

                            @Override
                            public void onSuccess() {
                                ret[0] = true;
                                countDownLatch.countDown();
                            }
                        };
                focusedGameSession.takeScreenshot(Runnable::run, callback);
                try {
                    countDownLatch.await(
                            SCREENSHOT_CALLBACK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    return false;
                }
                result = ret[0];
            }
            return result;
        }

        public OnSystemBarVisibilityChangedInfo getOnSystemBarVisibilityChangedInfo() {
            TestGameSession focusedGameSession = TestGameSessionService.getFocusedSession();
            if (focusedGameSession == null) {
                return null;
            }
            return focusedGameSession.getOnSystemBarVisibilityChangedInfo();
        }

        public void setGameServiceComponentEnabled(boolean enabled) {
            getPackageManager().setComponentEnabledSetting(
                    new ComponentName(getApplicationContext(), TestGameService.class),
                    enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP | PackageManager.SYNCHRONOUS);

            if (enabled) {
                return;
            }

            // Wait for package changes to propagate and then reset the TestGameService connection
            // state.
            try {
                Thread.sleep(3_000L);
            } catch (InterruptedException e) {
                // Do nothing.
            }
            TestGameService.reset();
        }

        public void setGameSessionServiceComponentEnabled(boolean enabled) {
            getPackageManager().setComponentEnabledSetting(
                    new ComponentName(getApplicationContext(), TestGameSessionService.class),
                    enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP | PackageManager.SYNCHRONOUS);
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mStub.asBinder();
    }
}

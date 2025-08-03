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

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import android.Manifest;
import android.util.Log;

import androidx.annotation.GuardedBy;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.Set;


/**
 * Test implementation of {@link GameService}.
 */
public final class TestGameService extends GameService {
    private static final String TAG = "TestGameService";

    private static final Object sLock = new Object();
    @GuardedBy("sLock")
    private static boolean sIsConnected = false;
    @GuardedBy("sLock")
    private static Set<String> sGamePackages = ImmutableSet.of();

    @Override
    public void onConnected() {
        getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.MANAGE_GAME_ACTIVITY);

        synchronized (sLock) {
            sIsConnected = true;
        }
    }

    @Override
    public void onDisconnected() {
        synchronized (sLock) {
            sIsConnected = false;
        }

        try {
            getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
        } catch (IllegalStateException | SecurityException e) {
            Log.w(TAG, "Failed to drop shell permission identity",  e);
        }
    }

    @Override
    public void onGameStarted(GameStartedEvent gameStartedEvent) {
        boolean isGame;
        synchronized (sLock) {
            isGame = sGamePackages.contains(gameStartedEvent.getPackageName());
        }
        if (isGame) {
            createGameSession(gameStartedEvent.getTaskId());
        }
    }

    static void reset() {
        synchronized (sLock) {
            sIsConnected = false;
        }
        setGamePackages(ImmutableList.of());
    }

    static boolean isConnected() {
        synchronized (sLock) {
            return sIsConnected;
        }
    }

    static void setGamePackages(Iterable<String> gamePackages) {
        synchronized (sLock) {
            sGamePackages = ImmutableSet.copyOf(gamePackages);
        }
    }
}


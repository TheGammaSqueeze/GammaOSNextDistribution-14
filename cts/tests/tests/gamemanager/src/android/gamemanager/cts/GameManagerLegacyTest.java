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

package android.gamemanager.cts;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertEquals;

import android.app.GameManager;
import android.app.Instrumentation;
import android.content.Context;

import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test the legacy behaviour of GameManager where the opt-in method is via using the
 * meta-data fields.
 */
@RunWith(AndroidJUnit4.class)
public class GameManagerLegacyTest {
    private static final String TAG = "GameManagerLegacyTest";

    private static final String APK_DIRECTORY = "/data/local/tmp/cts/gamemanager/test/apps/";
    private static final String LEGACY_GAME_TEST_APP_APK_PATH =
            APK_DIRECTORY + "CtsLegacyGameTestApp.apk";
    private static final String LEGACY_GAME_TEST_APP_PACKAGE_NAME =
            "android.gamemanager.cts.app.legacygametestapp";

    private Context mContext;
    private GameManager mGameManager;

    @Before
    public void setUp() {
        TestUtil.uninstallPackage(LEGACY_GAME_TEST_APP_PACKAGE_NAME);

        final Instrumentation instrumentation = getInstrumentation();
        mContext = instrumentation.getContext();
        mGameManager = mContext.getSystemService(GameManager.class);
    }

    @After
    public void tearDown() {
        TestUtil.uninstallPackage(LEGACY_GAME_TEST_APP_PACKAGE_NAME);
    }

    /**
     * Test that GameManager::getGameMode() returns the correct value when an app is a game with
     * all game modes enabled using the legacy metadata.
     */
    @Test
    public void testGetGameMode() throws InterruptedException {
        TestUtil.installPackage(LEGACY_GAME_TEST_APP_APK_PATH);
        Thread.sleep(500);

        // Without any change, the default behaviour should be STANDARD for a game.
        int gameMode =
                ShellIdentityUtils.invokeMethodWithShellPermissions(mGameManager,
                        (gameManager) -> gameManager.getGameMode(LEGACY_GAME_TEST_APP_PACKAGE_NAME),
                        "android.permission.MANAGE_GAME_MODE");
        assertEquals("Game Manager returned incorrect value for "
                + LEGACY_GAME_TEST_APP_PACKAGE_NAME, GameManager.GAME_MODE_STANDARD, gameMode);

        // Attempt to set the game mode to performance.
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mGameManager,
                (gameManager) -> gameManager.setGameMode(LEGACY_GAME_TEST_APP_PACKAGE_NAME,
                        GameManager.GAME_MODE_PERFORMANCE));
        gameMode = ShellIdentityUtils.invokeMethodWithShellPermissions(mGameManager,
                (gameManager) -> gameManager.getGameMode(LEGACY_GAME_TEST_APP_PACKAGE_NAME),
                "android.permission.MANAGE_GAME_MODE");
        assertEquals("Game Manager returned incorrect value for "
                        + LEGACY_GAME_TEST_APP_PACKAGE_NAME,
                GameManager.GAME_MODE_PERFORMANCE, gameMode);

        // Attempt to set the game mode to battery.
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mGameManager,
                (gameManager) -> gameManager.setGameMode(LEGACY_GAME_TEST_APP_PACKAGE_NAME,
                        GameManager.GAME_MODE_BATTERY));
        gameMode = ShellIdentityUtils.invokeMethodWithShellPermissions(mGameManager,
                (gameManager) -> gameManager.getGameMode(LEGACY_GAME_TEST_APP_PACKAGE_NAME),
                "android.permission.MANAGE_GAME_MODE");
        assertEquals("Game Manager returned incorrect value for "
                        + LEGACY_GAME_TEST_APP_PACKAGE_NAME,
                GameManager.GAME_MODE_BATTERY, gameMode);

        // Attempt to set the game mode to standard.
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mGameManager,
                (gameManager) -> gameManager.setGameMode(LEGACY_GAME_TEST_APP_PACKAGE_NAME,
                        GameManager.GAME_MODE_STANDARD));
        gameMode = ShellIdentityUtils.invokeMethodWithShellPermissions(mGameManager,
                (gameManager) -> gameManager.getGameMode(LEGACY_GAME_TEST_APP_PACKAGE_NAME),
                "android.permission.MANAGE_GAME_MODE");
        assertEquals("Game Manager returned incorrect value for "
                        + LEGACY_GAME_TEST_APP_PACKAGE_NAME,
                GameManager.GAME_MODE_STANDARD, gameMode);

        TestUtil.uninstallPackage(LEGACY_GAME_TEST_APP_PACKAGE_NAME);
    }
}

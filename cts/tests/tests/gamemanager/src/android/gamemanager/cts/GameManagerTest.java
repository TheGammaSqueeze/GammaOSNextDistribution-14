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

package android.gamemanager.cts;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.GameManager;
import android.app.GameModeConfiguration;
import android.app.GameModeInfo;
import android.app.GameState;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.platform.test.annotations.AppModeFull;
import android.support.test.uiautomator.UiDevice;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
public class GameManagerTest {
    private static final String TAG = "GameManagerTest";
    private static final String POWER_DUMPSYS_CMD = "dumpsys android.hardware.power.IPower/default";
    private static final Pattern GAME_LOADING_REGEX =
            Pattern.compile("^GAME_LOADING\\t(\\d*)\\t\\d*$", Pattern.MULTILINE);
    private static final String APK_DIRECTORY = "/data/local/tmp/cts/gamemanager/test/apps/";

    private static final String NOT_GAME_TEST_APP_APK_PATH =
            APK_DIRECTORY + "CtsNotGameTestApp.apk";
    private static final String NOT_GAME_TEST_APP_PACKAGE_NAME =
            "android.gamemanager.cts.app.notgametestapp";

    private static final String GAME_TEST_APP_APK_PATH =
            APK_DIRECTORY + "CtsGameTestApp.apk";
    private static final String GAME_TEST_APP_PACKAGE_NAME =
            "android.gamemanager.cts.app.gametestapp";

    private static final String GAME_TEST_APP_WITH_BATTERY_APK_PATH =
            APK_DIRECTORY + "CtsGameTestAppWithBatteryMode.apk";
    private static final String GAME_TEST_APP_WITH_BATTERY_PACKAGE_NAME =
            "android.gamemanager.cts.app.gametestapp.battery";

    private static final String GAME_TEST_APP_WITH_PERFORMANCE_APK_PATH =
            APK_DIRECTORY + "CtsGameTestAppWithPerformanceMode.apk";
    private static final String GAME_TEST_APP_WITH_PERFORMANCE_PACKAGE_NAME =
            "android.gamemanager.cts.app.gametestapp.performance";

    private static final String GAME_TEST_APP_WITH_TIRAMISU_TARGET_APK_PATH =
            APK_DIRECTORY + "CtsGameTestAppWithTiramisuTarget.apk";
    private static final String GAME_TEST_APP_WITH_TIRAMISU_TARGET_PACKAGE_NAME =
            "android.gamemanager.cts.app.gametestapp.tiramisu";

    private static final String GAME_TEST_APP_ACTIVITY_NAME = ".GameTestAppMainActivity";

    private static final String PROPERTY_RO_SURFACEFLINGER_GAME_DEFAULT_FRAME_RATE =
            "ro.surface_flinger.game_default_frame_rate_override";

    private static final String PROPERTY_RO_VENDOR_API_LEVEL = "ro.vendor.api_level";

    private static final int TEST_LABEL = 1;
    private static final int TEST_QUALITY = 2;

    private GameManagerCtsActivity mActivity;
    private Context mContext;
    private GameManager mGameManager;
    private UiDevice mUiDevice;

    @Rule
    public ActivityScenarioRule<GameManagerCtsActivity> mActivityRule =
            new ActivityScenarioRule<>(GameManagerCtsActivity.class);

    @Before
    public void setUp() {
        TestUtil.uninstallPackage(NOT_GAME_TEST_APP_PACKAGE_NAME);
        TestUtil.uninstallPackage(GAME_TEST_APP_PACKAGE_NAME);
        TestUtil.uninstallPackage(GAME_TEST_APP_WITH_BATTERY_PACKAGE_NAME);
        TestUtil.uninstallPackage(GAME_TEST_APP_WITH_PERFORMANCE_PACKAGE_NAME);
        TestUtil.uninstallPackage(GAME_TEST_APP_WITH_TIRAMISU_TARGET_PACKAGE_NAME);

        mActivityRule.getScenario().onActivity(activity -> {
            mActivity = activity;
        });

        final Instrumentation instrumentation = getInstrumentation();
        mContext = instrumentation.getContext();
        mGameManager = mContext.getSystemService(GameManager.class);
        mUiDevice = UiDevice.getInstance(instrumentation);
    }

    @After
    public void tearDown() throws Exception {
        TestUtil.uninstallPackage(NOT_GAME_TEST_APP_PACKAGE_NAME);
        TestUtil.uninstallPackage(GAME_TEST_APP_PACKAGE_NAME);
        TestUtil.uninstallPackage(GAME_TEST_APP_WITH_BATTERY_PACKAGE_NAME);
        TestUtil.uninstallPackage(GAME_TEST_APP_WITH_PERFORMANCE_PACKAGE_NAME);
        TestUtil.uninstallPackage(GAME_TEST_APP_WITH_TIRAMISU_TARGET_PACKAGE_NAME);
    }

    protected void assumeMobileDeviceFormFactor() {
        final PackageManager pm = mContext.getPackageManager();
        assumeFalse(pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE));
        assumeFalse(pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)); // TVs
        assumeFalse(pm.hasSystemFeature(PackageManager.FEATURE_WATCH));
        assumeFalse(pm.hasSystemFeature(PackageManager.FEATURE_EMBEDDED));
    }

    protected void assumeAndroidApiLevel(int androidApiLevel) {
        final int apiLevel =
                Integer.parseInt(runShellCommand("getprop " + PROPERTY_RO_VENDOR_API_LEVEL));
        assumeTrue(apiLevel >= androidApiLevel);
    }

    @Test
    public void testIsGameDefaultFrameRatePropSet() throws NumberFormatException {
        // Verify that "ro.surface_flinger.game_default_frame_rate_override"
        // is set with a positive integer.

        // This test only aims for mobile devices after Android T
        assumeMobileDeviceFormFactor();
        assumeAndroidApiLevel(Build.VERSION_CODES.TIRAMISU);
        Integer gameDefaultFrameRate = Integer.valueOf(runShellCommand("getprop "
                + PROPERTY_RO_SURFACEFLINGER_GAME_DEFAULT_FRAME_RATE));
        assertNotNull(gameDefaultFrameRate);
        assertTrue(gameDefaultFrameRate >= 0);
    }

    @Test
    public void testIsAngleEnabled() throws Exception {
        final String packageName = GAME_TEST_APP_WITH_PERFORMANCE_PACKAGE_NAME;
        TestUtil.installPackage(GAME_TEST_APP_WITH_PERFORMANCE_APK_PATH);
        Thread.sleep(1000);

        // enable Angle for BATTERY mode.
        runShellCommand("device_config put game_overlay " + packageName
                + " mode=3,useAngle=true");
        Thread.sleep(1000);

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mGameManager,
                (gameManager) -> gameManager.setGameMode(packageName,
                        GameManager.GAME_MODE_BATTERY), "android.permission.MANAGE_GAME_MODE");
        assertTrue(ShellIdentityUtils.invokeMethodWithShellPermissions(mGameManager,
                (gameManager) -> gameManager.isAngleEnabled(packageName),
                "android.permission.MANAGE_GAME_MODE"));
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mGameManager,
                (gameManager) -> gameManager.setGameMode(packageName,
                        GameManager.GAME_MODE_PERFORMANCE), "android.permission.MANAGE_GAME_MODE");
        assertFalse(ShellIdentityUtils.invokeMethodWithShellPermissions(mGameManager,
                (gameManager) -> gameManager.isAngleEnabled(packageName),
                "android.permission.MANAGE_GAME_MODE"));

        TestUtil.uninstallPackage(packageName);
    }

    /**
     * Test that GameManager::getGameMode() returns the UNSUPPORTED when an app is not a game.
     */
    @Test
    public void testGetGameModeUnsupportedOnNotGame() throws InterruptedException {
        TestUtil.installPackage(NOT_GAME_TEST_APP_APK_PATH);
        Thread.sleep(1000);

        int gameMode =
                ShellIdentityUtils.invokeMethodWithShellPermissions(mGameManager,
                        (gameManager) -> gameManager.getGameMode(NOT_GAME_TEST_APP_PACKAGE_NAME),
                        "android.permission.MANAGE_GAME_MODE");

        assertEquals("Game Manager returned incorrect value for "
                + NOT_GAME_TEST_APP_PACKAGE_NAME, GameManager.GAME_MODE_UNSUPPORTED, gameMode);

        // Attempt to set the game mode to standard.
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mGameManager,
                (gameManager) -> gameManager.setGameMode(NOT_GAME_TEST_APP_PACKAGE_NAME,
                        GameManager.GAME_MODE_STANDARD));
        gameMode = ShellIdentityUtils.invokeMethodWithShellPermissions(mGameManager,
                (gameManager) -> gameManager.getGameMode(NOT_GAME_TEST_APP_PACKAGE_NAME),
                "android.permission.MANAGE_GAME_MODE");
        assertEquals("Game Manager returned incorrect value for "
                        + NOT_GAME_TEST_APP_PACKAGE_NAME,
                GameManager.GAME_MODE_UNSUPPORTED, gameMode);

        // Attempt to set the game mode to performance.
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mGameManager,
                (gameManager) -> gameManager.setGameMode(NOT_GAME_TEST_APP_PACKAGE_NAME,
                        GameManager.GAME_MODE_PERFORMANCE));
        gameMode = ShellIdentityUtils.invokeMethodWithShellPermissions(mGameManager,
                (gameManager) -> gameManager.getGameMode(NOT_GAME_TEST_APP_PACKAGE_NAME),
                "android.permission.MANAGE_GAME_MODE");
        assertEquals("Game Manager returned incorrect value for "
                        + NOT_GAME_TEST_APP_PACKAGE_NAME,
                GameManager.GAME_MODE_UNSUPPORTED, gameMode);

        // Attempt to set the game mode to battery.
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mGameManager,
                (gameManager) -> gameManager.setGameMode(NOT_GAME_TEST_APP_PACKAGE_NAME,
                        GameManager.GAME_MODE_BATTERY));
        gameMode = ShellIdentityUtils.invokeMethodWithShellPermissions(mGameManager,
                (gameManager) -> gameManager.getGameMode(NOT_GAME_TEST_APP_PACKAGE_NAME),
                "android.permission.MANAGE_GAME_MODE");
        assertEquals("Game Manager returned incorrect value for "
                        + NOT_GAME_TEST_APP_PACKAGE_NAME,
                GameManager.GAME_MODE_UNSUPPORTED, gameMode);

        TestUtil.uninstallPackage(NOT_GAME_TEST_APP_PACKAGE_NAME);
    }

    /**
     * Test that GAME_MODE_UNSUPPORTED is not forcible for game.
     */
    @Test
    public void testGetGameModeUnsupported() {
        int expectedGameMode = mActivity.getGameMode();
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mGameManager,
                (gameManager) -> gameManager.setGameMode(mActivity.getPackageName(),
                        GameManager.GAME_MODE_UNSUPPORTED));

        int newGameMode = mActivity.getGameMode();

        assertEquals("Game mode is not expected to change after trying to set UNSUPPORTED",
                expectedGameMode, newGameMode);
    }

    /**
     * Test that GameManager::getGameMode() returns the correct value when forcing the Game Mode to
     * GAME_MODE_STANDARD.
     */
    @Test
    public void testGetGameModeStandard() {
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mGameManager,
                (gameManager) -> gameManager.setGameMode(mActivity.getPackageName(),
                        GameManager.GAME_MODE_STANDARD));

        int gameMode = mActivity.getGameMode();

        assertEquals("Game Manager returned incorrect value.",
                GameManager.GAME_MODE_STANDARD, gameMode);
    }

    /**
     * Test that GameManager::getGameMode() returns the correct value when forcing the Game Mode to
     * GAME_MODE_PERFORMANCE.
     */
    @Test
    public void testGetGameModePerformance() {
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mGameManager,
                (gameManager) -> gameManager.setGameMode(mActivity.getPackageName(),
                        GameManager.GAME_MODE_PERFORMANCE));

        int gameMode = mActivity.getGameMode();

        assertEquals("Game Manager returned incorrect value.",
                GameManager.GAME_MODE_PERFORMANCE, gameMode);
    }

    /**
     * Test that GameManager::getGameMode() returns the correct value when forcing the Game Mode to
     * GAME_MODE_BATTERY.
     */
    @Test
    public void testGetGameModeBattery() {
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mGameManager,
                (gameManager) -> gameManager.setGameMode(mActivity.getPackageName(),
                        GameManager.GAME_MODE_BATTERY));

        int gameMode = mActivity.getGameMode();

        assertEquals("Game Manager returned incorrect value.",
                GameManager.GAME_MODE_BATTERY, gameMode);
    }

    private int getGameLoadingCount() throws IOException {
        final Matcher matcher =
                GAME_LOADING_REGEX.matcher(mUiDevice.executeShellCommand(POWER_DUMPSYS_CMD));
        assumeTrue(matcher.find());
        return Integer.parseInt(matcher.group(1));
    }

    /**
     * Test that GameManager::setGameState() with an 'isLoading' state does not invokes the mode
     * on the PowerHAL when performance mode is not invoked.
     */
    @Test
    public void testSetGameStateStandardMode() throws IOException, InterruptedException {
        final int gameLoadingCountBefore = getGameLoadingCount();
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mGameManager,
                (gameManager) -> gameManager.setGameMode(mActivity.getPackageName(),
                GameManager.GAME_MODE_STANDARD));
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mGameManager, (gameManager) ->
                gameManager.setGameState(new GameState(true, GameState.MODE_NONE)));
        Thread.sleep(1000);  // Wait for change to take effect.
        assertEquals(gameLoadingCountBefore, getGameLoadingCount());
    }

    /**
     * Test that GameManager::setGameState() with an 'isLoading' state actually invokes the mode
     * on the PowerHAL when performance mode is invoked.
     */
    @Test
    public void testSetGameStatePerformanceMode() throws IOException, InterruptedException {
        final int gameLoadingCountBefore = getGameLoadingCount();
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mGameManager,
                (gameManager) -> gameManager.setGameMode(mActivity.getPackageName(),
                GameManager.GAME_MODE_PERFORMANCE));
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mGameManager, (gameManager) ->
                gameManager.setGameState(new GameState(true, GameState.MODE_NONE)));
        Thread.sleep(1000);  // Wait for change to take effect.
        assertEquals(gameLoadingCountBefore + 1, getGameLoadingCount());
    }

    /**
     * Test that GameManager::setGameState() with an 'isLoading' state and labels
     * actually invokes the mode on the PowerHAL when performance mode is invoked.
     */
    @Test
    public void testSetGameStatePerformanceMode_withParams()
            throws IOException, InterruptedException {
        final int gameLoadingCountBefore = getGameLoadingCount();
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mGameManager,
                (gameManager) -> gameManager.setGameMode(mActivity.getPackageName(),
                        GameManager.GAME_MODE_PERFORMANCE));
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mGameManager, (gameManager) ->
                gameManager.setGameState(
                        new GameState(true, GameState.MODE_NONE, TEST_LABEL, TEST_QUALITY)));
        Thread.sleep(1000);  // Wait for change to take effect.
        assertEquals(gameLoadingCountBefore + 1, getGameLoadingCount());
    }

    /**
     * Test that GameManager::getGameModeInfo() returns correct values for a game.
     */
    @Test
    public void testGetGameModeInfoWithTwoGameModes() throws InterruptedException {
        TestUtil.installPackage(GAME_TEST_APP_APK_PATH);
        // When an app is installed, some propagation work for the configuration will
        // be set up asynchronously, hence wait for 1000ms here.
        Thread.sleep(1000);

        GameModeInfo gameModeInfo =
                ShellIdentityUtils.invokeMethodWithShellPermissions(mGameManager,
                        (gameManager) -> gameManager.getGameModeInfo(GAME_TEST_APP_PACKAGE_NAME),
                        "android.permission.MANAGE_GAME_MODE");
        assertEquals("GameManager#getGameModeInfo returned incorrect available game modes.",
                4, gameModeInfo.getAvailableGameModes().length);
        assertTrue("GameManager#getGameModeInfo returned incorrect overridden game modes.",
                Arrays.asList(GameManager.GAME_MODE_PERFORMANCE,
                        GameManager.GAME_MODE_BATTERY).containsAll(Arrays.stream(
                        gameModeInfo.getOverriddenGameModes()).boxed().toList()));
        assertTrue("GameManager#getGameModeInfo returned incorrect downscaling opt-in value.",
                gameModeInfo.isDownscalingAllowed());
        assertTrue("GameManager#getGameModeInfo returned incorrect FPS override opt-in value.",
                gameModeInfo.isFpsOverrideAllowed());
        assertEquals("GameManager#getGameModeInfo returned incorrect active game mode.",
                GameManager.GAME_MODE_STANDARD, gameModeInfo.getActiveGameMode());

        // Attempt to set the game mode to standard.
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mGameManager,
                (gameManager) -> gameManager.setGameMode(GAME_TEST_APP_PACKAGE_NAME,
                        GameManager.GAME_MODE_STANDARD));
        gameModeInfo = ShellIdentityUtils.invokeMethodWithShellPermissions(mGameManager,
                (gameManager) -> gameManager.getGameModeInfo(GAME_TEST_APP_PACKAGE_NAME),
                "android.permission.MANAGE_GAME_MODE");
        assertEquals("GameManager#getGameModeInfo returned incorrect available game modes.",
                4, gameModeInfo.getAvailableGameModes().length);
        assertEquals("GameManager#getGameModeInfo returned incorrect active game mode.",
                GameManager.GAME_MODE_STANDARD, gameModeInfo.getActiveGameMode());

        // Attempt to set the game mode to performance.
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mGameManager,
                (gameManager) -> gameManager.setGameMode(GAME_TEST_APP_PACKAGE_NAME,
                        GameManager.GAME_MODE_PERFORMANCE));
        gameModeInfo = ShellIdentityUtils.invokeMethodWithShellPermissions(mGameManager,
                (gameManager) -> gameManager.getGameModeInfo(GAME_TEST_APP_PACKAGE_NAME),
                "android.permission.MANAGE_GAME_MODE");
        assertEquals("GameManager#getGameModeInfo returned incorrect available game modes.",
                4, gameModeInfo.getAvailableGameModes().length);
        assertEquals("GameManager#getGameModeInfo returned incorrect active game mode.",
                GameManager.GAME_MODE_PERFORMANCE, gameModeInfo.getActiveGameMode());

        // Attempt to set the game mode to battery.
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mGameManager,
                (gameManager) -> gameManager.setGameMode(GAME_TEST_APP_PACKAGE_NAME,
                        GameManager.GAME_MODE_BATTERY));
        gameModeInfo = ShellIdentityUtils.invokeMethodWithShellPermissions(mGameManager,
                (gameManager) -> gameManager.getGameModeInfo(GAME_TEST_APP_PACKAGE_NAME),
                "android.permission.MANAGE_GAME_MODE");
        assertEquals("GameManager#getGameModeInfo returned incorrect available game modes.",
                4, gameModeInfo.getAvailableGameModes().length);
        assertEquals("GameManager#getGameModeInfo returned incorrect active game mode.",
                GameManager.GAME_MODE_BATTERY, gameModeInfo.getActiveGameMode());

        TestUtil.uninstallPackage(GAME_TEST_APP_PACKAGE_NAME);
    }

    /**
     * Test that GameManager::getGameModeInfo() returns correct values for a game when it only
     * supports battery mode.
     */
    @Test
    public void testGetGameModeInfoWithBatteryMode() throws InterruptedException {
        final String packageName = GAME_TEST_APP_WITH_BATTERY_PACKAGE_NAME;
        TestUtil.installPackage(GAME_TEST_APP_WITH_BATTERY_APK_PATH);
        // When an app is installed, some propagation work for the configuration will
        // be set up asynchronously, hence wait for 1000ms here.
        Thread.sleep(1000);

        GameModeInfo gameModeInfo =
                ShellIdentityUtils.invokeMethodWithShellPermissions(mGameManager,
                        (gameManager) -> gameManager.getGameModeInfo(packageName),
                        "android.permission.MANAGE_GAME_MODE");
        assertEquals("GameManager#getGameModeInfo returned incorrect available game modes.",
                3, gameModeInfo.getAvailableGameModes().length);
        assertEquals("GameManager#getGameModeInfo returned incorrect active game mode.",
                GameManager.GAME_MODE_STANDARD, gameModeInfo.getActiveGameMode());

        // Attempt to set the game mode to battery.
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mGameManager,
                (gameManager) -> gameManager.setGameMode(packageName,
                        GameManager.GAME_MODE_BATTERY));
        gameModeInfo = ShellIdentityUtils.invokeMethodWithShellPermissions(mGameManager,
                (gameManager) -> gameManager.getGameModeInfo(packageName),
                "android.permission.MANAGE_GAME_MODE");
        assertEquals("GameManager#getGameModeInfo returned incorrect available game modes.",
                3, gameModeInfo.getAvailableGameModes().length);
        assertEquals("GameManager#getGameModeInfo returned incorrect active game mode.",
                GameManager.GAME_MODE_BATTERY, gameModeInfo.getActiveGameMode());

        TestUtil.uninstallPackage(packageName);
    }

    @Test
    @AppModeFull
    public void testCustomGameMode() throws Exception {
        final String packageName = GAME_TEST_APP_PACKAGE_NAME;
        TestUtil.installPackage(GAME_TEST_APP_APK_PATH);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mGameManager,
                (gameManager) -> gameManager.setGameMode(packageName,
                        GameManager.GAME_MODE_CUSTOM));

        final int gameMode = ShellIdentityUtils.invokeMethodWithShellPermissions(mGameManager,
                (gameManager) -> gameManager.getGameMode(packageName),
                "android.permission.MANAGE_GAME_MODE");
        assertEquals("Game Manager returned incorrect value.",
                GameManager.GAME_MODE_CUSTOM, gameMode);

        runShellCommand("am start -n " + GAME_TEST_APP_PACKAGE_NAME
                + "/" + GAME_TEST_APP_ACTIVITY_NAME);
        assertEquals(GameManager.GAME_MODE_CUSTOM,
                mActivity.getLastReceivedGameMode(GAME_TEST_APP_PACKAGE_NAME,
                        10000 /* timeoutMillis */));
    }

    @Test
    @AppModeFull
    public void testCustomGameModeBackwardCompatibility() throws Exception {
        final String packageName = GAME_TEST_APP_WITH_TIRAMISU_TARGET_PACKAGE_NAME;
        TestUtil.installPackage(GAME_TEST_APP_WITH_TIRAMISU_TARGET_APK_PATH);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mGameManager,
                (gameManager) -> gameManager.setGameMode(packageName,
                        GameManager.GAME_MODE_CUSTOM));

        final int gameMode = ShellIdentityUtils.invokeMethodWithShellPermissions(mGameManager,
                (gameManager) -> gameManager.getGameMode(packageName),
                "android.permission.MANAGE_GAME_MODE");
        assertEquals("Game Manager returned incorrect value.",
                GameManager.GAME_MODE_STANDARD, gameMode);

        runShellCommand("am start -n " + GAME_TEST_APP_WITH_TIRAMISU_TARGET_PACKAGE_NAME
                + "/" + GAME_TEST_APP_ACTIVITY_NAME);
        assertEquals(GameManager.GAME_MODE_STANDARD,
                mActivity.getLastReceivedGameMode(GAME_TEST_APP_WITH_TIRAMISU_TARGET_PACKAGE_NAME,
                        10000 /* timeoutMillis */));
    }

    @Test
    public void testUpdateCustomGameModeConfiguration() throws Exception {
        final String packageName = GAME_TEST_APP_PACKAGE_NAME;
        TestUtil.installPackage(GAME_TEST_APP_APK_PATH);
        final GameModeConfiguration gameModeConfiguration = new GameModeConfiguration.Builder()
                .setFpsOverride(60)
                .setScalingFactor(0.8f).build();
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mGameManager,
                (gameManager) -> gameManager.updateCustomGameModeConfiguration(packageName,
                        gameModeConfiguration),
                "android.permission.MANAGE_GAME_MODE");
        final GameModeInfo gameModeInfo = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mGameManager,
                (gameManager) -> gameManager.getGameModeInfo(packageName),
                "android.permission.MANAGE_GAME_MODE");
        assertEquals(new GameModeConfiguration.Builder(gameModeConfiguration).build(),
                gameModeInfo.getGameModeConfiguration(GameManager.GAME_MODE_CUSTOM));
    }
}

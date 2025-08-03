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

package android.service.games;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.GameManager;
import android.app.Instrumentation;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Rect;
import android.net.Uri;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.service.games.testing.ActivityResult;
import android.service.games.testing.GameSessionEventInfo;
import android.service.games.testing.GetResultActivity;
import android.service.games.testing.IGameServiceTestService;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.UiSelector;
import android.support.test.uiautomator.Until;
import android.util.Log;
import android.util.Size;
import android.view.WindowManager;
import android.view.WindowMetrics;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.FlakyTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.compatibility.common.util.ShellUtils;
import com.android.compatibility.common.util.UiAutomatorUtils;

import com.google.common.collect.ImmutableList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized.Parameter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CTS tests for {@link android.service.games.GameService}.
 */
@FlakyTest(bugId = 263181277)
@RunWith(AndroidJUnit4.class)
public final class GameServiceTest {
    static final String TAG = "GameServiceTest";

    private static final String GAME_PACKAGE_NAME = "android.service.games.cts.game";
    private static final String FALSE_POSITIVE_GAME_PACKAGE_NAME =
            "android.service.games.cts.falsepositive";
    private static final String FINISH_ON_BACK_GAME_PACKAGE_NAME =
            "android.service.games.cts.finishonbackgame";
    private static final String NOT_GAME_PACKAGE_NAME = "android.service.games.cts.notgame";
    private static final String RESTART_GAME_VERIFIER_PACKAGE_NAME =
            "android.service.games.cts.restartgameverifier";
    private static final String START_ACTIVITY_VERIFIER_PACKAGE_NAME =
            "android.service.games.cts.startactivityverifier";
    private static final String SYSTEM_BAR_VERIFIER_PACKAGE_NAME =
            "android.service.games.cts.systembarverifier";
    private static final String TAKE_SCREENSHOT_VERIFIER_PACKAGE_NAME =
            "android.service.games.cts.takescreenshotverifier";
    private static final String TOUCH_VERIFIER_PACKAGE_NAME =
            "android.service.games.cts.touchverifier";

    @Parameter(0)
    public String mVolumeName;

    private ServiceConnection mServiceConnection;
    private ContentResolver mContentResolver;

    @Before
    public void setUp() throws Exception {
        mServiceConnection = new ServiceConnection();
        assertThat(
                getInstrumentation().getContext().bindService(
                        new Intent("android.service.games.action.TEST_SERVICE").setPackage(
                                getInstrumentation().getContext().getPackageName()),
                        mServiceConnection,
                        Context.BIND_AUTO_CREATE)).isTrue();
        mServiceConnection.waitForConnection(10, TimeUnit.SECONDS);

        getTestService().setGamePackageNames(
                ImmutableList.of(
                        FINISH_ON_BACK_GAME_PACKAGE_NAME,
                        GAME_PACKAGE_NAME,
                        RESTART_GAME_VERIFIER_PACKAGE_NAME,
                        SYSTEM_BAR_VERIFIER_PACKAGE_NAME,
                        TAKE_SCREENSHOT_VERIFIER_PACKAGE_NAME,
                        TOUCH_VERIFIER_PACKAGE_NAME));

        GameManager gameManager =
                getInstrumentation().getContext().getSystemService(GameManager.class);

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(gameManager,
                manager -> manager.setGameServiceProvider(
                        getInstrumentation().getContext().getPackageName()));
        mContentResolver = getInstrumentation().getContext().getContentResolver();

        if (gameServiceFeaturePresent()) {
            waitForGameServiceConnected();
        }
    }

    @After
    public void tearDown() throws Exception {
        forceStop(GAME_PACKAGE_NAME);
        forceStop(NOT_GAME_PACKAGE_NAME);
        forceStop(FALSE_POSITIVE_GAME_PACKAGE_NAME);
        forceStop(FINISH_ON_BACK_GAME_PACKAGE_NAME);
        forceStop(RESTART_GAME_VERIFIER_PACKAGE_NAME);
        forceStop(START_ACTIVITY_VERIFIER_PACKAGE_NAME);
        forceStop(SYSTEM_BAR_VERIFIER_PACKAGE_NAME);
        forceStop(TAKE_SCREENSHOT_VERIFIER_PACKAGE_NAME);
        forceStop(TOUCH_VERIFIER_PACKAGE_NAME);

        GameManager gameManager =
                getInstrumentation().getContext().getSystemService(GameManager.class);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(gameManager,
                manager -> manager.setGameServiceProvider(""));

        getTestService().resetState();
    }

    @Test
    public void gameService_connectsOnStartup() throws Exception {
        assumeGameServiceFeaturePresent();

        waitForGameServiceConnected();
        assertThat(isGameServiceConnected()).isTrue();
    }

    @Test
    public void gameService_connectsWhenGameServiceComponentIsEnabled() throws Exception {
        assumeGameServiceFeaturePresent();

        waitForGameServiceConnected();

        getTestService().setGameServiceComponentEnabled(false);
        waitForGameServiceDisconnected();

        getTestService().setGameServiceComponentEnabled(true);
        waitForGameServiceConnected();
    }

    @Test
    public void gameService_connectsWhenGameSessionServiceComponentIsEnabled() throws Exception {
        assumeGameServiceFeaturePresent();

        waitForGameServiceConnected();

        getTestService().setGameSessionServiceComponentEnabled(false);
        waitForGameServiceDisconnected();

        getTestService().setGameSessionServiceComponentEnabled(true);
        waitForGameServiceConnected();
    }

    @Test
    public void gameService_startsGameSessionsForGames() throws Exception {
        assumeGameServiceFeaturePresent();

        launchAndWaitForPackage(NOT_GAME_PACKAGE_NAME);
        launchAndWaitForPackage(GAME_PACKAGE_NAME);
        launchAndWaitForPackage(FALSE_POSITIVE_GAME_PACKAGE_NAME);

        assertThat(getTestService().getActiveSessions()).containsExactly(
                GAME_PACKAGE_NAME);
    }

    @Test
    public void gameService_multipleGames_startsGameSessionsForGames() throws Exception {
        assumeGameServiceFeaturePresent();

        launchAndWaitForPackage(NOT_GAME_PACKAGE_NAME);
        launchAndWaitForPackage(GAME_PACKAGE_NAME);
        int gameTaskId = getActivityTaskId(
                GAME_PACKAGE_NAME,
                GAME_PACKAGE_NAME + ".MainActivity");
        launchAndWaitForPackage(FALSE_POSITIVE_GAME_PACKAGE_NAME);
        launchAndWaitForPackage(RESTART_GAME_VERIFIER_PACKAGE_NAME);
        int restartGameTaskId = getActivityTaskId(
                RESTART_GAME_VERIFIER_PACKAGE_NAME,
                RESTART_GAME_VERIFIER_PACKAGE_NAME + ".MainActivity");

        assertThat(getTestService().getActiveSessions()).containsExactly(
                GAME_PACKAGE_NAME, RESTART_GAME_VERIFIER_PACKAGE_NAME);

        List<GameSessionEventInfo> gameSessionEventHistory =
                getTestService().getGameSessionEventHistory();
        assertThat(gameSessionEventHistory)
                .containsExactly(
                        GameSessionEventInfo.create(
                                GAME_PACKAGE_NAME,
                                gameTaskId,
                                GameSessionEventInfo.GAME_SESSION_EVENT_CREATED),
                        GameSessionEventInfo.create(
                                RESTART_GAME_VERIFIER_PACKAGE_NAME,
                                restartGameTaskId,
                                GameSessionEventInfo.GAME_SESSION_EVENT_CREATED))
                .inOrder();
    }

    @Test
    public void gameService_multipleGamesIncludingStops_startsGameSessionsForGames()
            throws Exception {
        assumeGameServiceFeaturePresent();

        launchAndWaitForPackage(NOT_GAME_PACKAGE_NAME);
        launchAndWaitForPackage(GAME_PACKAGE_NAME);
        int gameTaskId = getActivityTaskId(
                GAME_PACKAGE_NAME,
                GAME_PACKAGE_NAME + ".MainActivity");
        launchAndWaitForPackage(FALSE_POSITIVE_GAME_PACKAGE_NAME);
        launchAndWaitForPackage(RESTART_GAME_VERIFIER_PACKAGE_NAME);
        int restartGameTaskId = getActivityTaskId(
                RESTART_GAME_VERIFIER_PACKAGE_NAME,
                RESTART_GAME_VERIFIER_PACKAGE_NAME + ".MainActivity");
        forceStop(GAME_PACKAGE_NAME);

        assertThat(getTestService().getActiveSessions()).containsExactly(
                RESTART_GAME_VERIFIER_PACKAGE_NAME);

        List<GameSessionEventInfo> gameSessionEventHistory =
                getTestService().getGameSessionEventHistory();
        assertThat(gameSessionEventHistory)
                .containsExactly(
                        GameSessionEventInfo.create(
                                GAME_PACKAGE_NAME,
                                gameTaskId,
                                GameSessionEventInfo.GAME_SESSION_EVENT_CREATED),
                        GameSessionEventInfo.create(
                                RESTART_GAME_VERIFIER_PACKAGE_NAME,
                                restartGameTaskId,
                                GameSessionEventInfo.GAME_SESSION_EVENT_CREATED),
                        GameSessionEventInfo.create(
                                GAME_PACKAGE_NAME,
                                gameTaskId,
                                GameSessionEventInfo.GAME_SESSION_EVENT_DESTROYED))
                .inOrder();
    }

    @Test
    public void getTaskId_returnsTaskIdOfGame() throws Exception {
        assumeGameServiceFeaturePresent();

        launchAndWaitForPackage(GAME_PACKAGE_NAME);

        int taskId = getTestService().getFocusedTaskId();

        assertThat(taskId).isEqualTo(
                getActivityTaskId(GAME_PACKAGE_NAME, GAME_PACKAGE_NAME + ".MainActivity"));
    }

    @Test
    public void setTaskOverlayView_addsViewsToOverlay() throws Exception {
        assumeGameServiceFeaturePresent();

        launchAndWaitForPackage(GAME_PACKAGE_NAME);

        waitForTouchableOverlayBounds();

        assertThat(UiAutomatorUtils.getUiDevice().findObject(
                By.text("Overlay was rendered on: " + GAME_PACKAGE_NAME))).isNotNull();
    }

    @Test
    public void setTaskOverlayView_passesTouchesOutsideOverlayToGame() throws Exception {
        assumeGameServiceFeaturePresent();

        launchAndWaitForPackage(TOUCH_VERIFIER_PACKAGE_NAME);

        Rect touchableBounds = waitForTouchableOverlayBounds();
        UiAutomatorUtils.getUiDevice().click(touchableBounds.centerX(), touchableBounds.centerY());

        UiAutomatorUtils.waitFindObject(
                By.res(TOUCH_VERIFIER_PACKAGE_NAME, "times_clicked").text("0"));

        UiAutomatorUtils.getUiDevice().click(touchableBounds.centerX(), touchableBounds.top - 30);
        UiAutomatorUtils.waitFindObject(
                By.res(TOUCH_VERIFIER_PACKAGE_NAME, "times_clicked").text("1"));

        UiAutomatorUtils.getUiDevice()
                .click(touchableBounds.centerX(), touchableBounds.bottom + 30);
        UiAutomatorUtils.waitFindObject(
                By.res(TOUCH_VERIFIER_PACKAGE_NAME, "times_clicked").text("2"));

        UiAutomatorUtils.getUiDevice().click(touchableBounds.left - 30, touchableBounds.centerY());
        UiAutomatorUtils.waitFindObject(
                By.res(TOUCH_VERIFIER_PACKAGE_NAME, "times_clicked").text("3"));

        UiAutomatorUtils.getUiDevice().click(touchableBounds.right + 30, touchableBounds.centerY());
        UiAutomatorUtils.waitFindObject(
                By.res(TOUCH_VERIFIER_PACKAGE_NAME, "times_clicked").text("4"));
    }

    @Test
    public void onTransientSystemBarVisibilityChanged_nonTransient_doesNotDispatchShow()
            throws Exception {
        assumeGameServiceFeaturePresent();

        launchAndWaitForPackage(SYSTEM_BAR_VERIFIER_PACKAGE_NAME);

        UiAutomatorUtils.getUiDevice().findObject(
                        By.text("Show system bars permanently")
                                .pkg(SYSTEM_BAR_VERIFIER_PACKAGE_NAME))
                .click();

        assertThat(
                getTestService().getOnSystemBarVisibilityChangedInfo().getTimesShown())
                .isEqualTo(0);
    }

    @Test
    public void onTransientSystemBarVisibilityFromRevealGestureChanged_dispatchesHideEvent()
            throws Exception {
        assumeGameServiceFeaturePresent();

        launchAndWaitForPackage(GAME_PACKAGE_NAME);
        swipeFromTopEdgeToShowSystemBars();

        assertThat(
                getTestService().getOnSystemBarVisibilityChangedInfo().getTimesShown())
                .isEqualTo(1);

        PollingCheck.waitFor(
                () -> {
                    try {
                        return getTestService().getOnSystemBarVisibilityChangedInfo()
                                .getTimesHidden() > 0;
                    } catch (RemoteException e) {
                        return false;
                    }
                });
        assertThat(
                getTestService().getOnSystemBarVisibilityChangedInfo().getTimesHidden())
                .isEqualTo(1);
    }

    @Test
    public void startActivityForResult_startsActivityAndReceivesResultWithData() throws Exception {
        assumeGameServiceFeaturePresent();

        launchAndWaitForPackage(GAME_PACKAGE_NAME);

        StartActivityVerifierPage.launch(getTestService());

        StartActivityVerifierPage.setResultCode(10);
        StartActivityVerifierPage.setResultData("foobar");
        StartActivityVerifierPage.clickSendResultButton();

        ActivityResult result = getTestService().getLastActivityResult();

        assertThat(result.getGameSessionPackageName()).isEqualTo(GAME_PACKAGE_NAME);
        assertThat(result.getSuccess().getResultCode()).isEqualTo(10);
        String resultData = StartActivityVerifierPage.getResultData(result.getSuccess().getData());
        assertThat(resultData).isEqualTo("foobar");
    }

    @Test
    public void startActivityForResult_startsActivityAndReceivesResultWithNoData()
            throws Exception {
        assumeGameServiceFeaturePresent();

        launchAndWaitForPackage(GAME_PACKAGE_NAME);

        StartActivityVerifierPage.launch(getTestService());

        StartActivityVerifierPage.setResultCode(10);
        StartActivityVerifierPage.clickSendResultButton();

        ActivityResult result = getTestService().getLastActivityResult();

        assertThat(result.getGameSessionPackageName()).isEqualTo(GAME_PACKAGE_NAME);
        assertThat(result.getSuccess().getResultCode()).isEqualTo(10);
        assertThat(result.getSuccess().getData()).isNull();
    }

    @Test
    public void startActivityForResult_cannotStartBlockedActivities() throws Exception {
        assumeGameServiceFeaturePresent();

        launchAndWaitForPackage(GAME_PACKAGE_NAME);

        getTestService().startGameSessionActivity(
                new Intent("android.service.games.cts.startactivityverifier.START_BLOCKED"), null);

        ActivityResult result = getTestService().getLastActivityResult();

        assertThat(result.getGameSessionPackageName()).isEqualTo(GAME_PACKAGE_NAME);
        assertThat(result.getFailure().getClazz()).isEqualTo(SecurityException.class);
    }

    @Test
    public void startActivityForResult_propagatesActivityNotFoundException() throws Exception {
        assumeGameServiceFeaturePresent();

        launchAndWaitForPackage(GAME_PACKAGE_NAME);

        getTestService().startGameSessionActivity(new Intent("NO_ACTION"), null);

        ActivityResult result = getTestService().getLastActivityResult();

        assertThat(result.getGameSessionPackageName()).isEqualTo(GAME_PACKAGE_NAME);
        assertThat(result.getFailure().getClazz()).isEqualTo(ActivityNotFoundException.class);
    }
    @Test
    public void restartGame_noPermission() throws Exception {
        assumeGameServiceFeaturePresent();
        getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
        clearCache(RESTART_GAME_VERIFIER_PACKAGE_NAME);
        RestartGameVerifierPage.launch();

        RestartGameVerifierPage.assertTimesStarted(1);
        RestartGameVerifierPage.assertHasSavedInstanceState(false);

        getTestService().restartFocusedGameSession();

        RestartGameVerifierPage.assertTimesStarted(1);
        RestartGameVerifierPage.assertHasSavedInstanceState(false);
    }

    @Test
    public void restartGame_gameAppIsRestarted() throws Exception {
        assumeGameServiceFeaturePresent();

        clearCache(RESTART_GAME_VERIFIER_PACKAGE_NAME);
        RestartGameVerifierPage.launch();

        RestartGameVerifierPage.assertTimesStarted(1);
        RestartGameVerifierPage.assertHasSavedInstanceState(false);

        getTestService().restartFocusedGameSession();

        RestartGameVerifierPage.assertTimesStarted(2);
        RestartGameVerifierPage.assertHasSavedInstanceState(true);

        getTestService().restartFocusedGameSession();

        RestartGameVerifierPage.assertTimesStarted(3);
        RestartGameVerifierPage.assertHasSavedInstanceState(true);
    }

    @Test
    public void restartGame_gameSessionIsPersisted() throws Exception {
        assumeGameServiceFeaturePresent();

        clearCache(RESTART_GAME_VERIFIER_PACKAGE_NAME);
        RestartGameVerifierPage.launch();

        int gameTaskId = getActivityTaskId(
                RESTART_GAME_VERIFIER_PACKAGE_NAME,
                RESTART_GAME_VERIFIER_PACKAGE_NAME + ".MainActivity");

        RestartGameVerifierPage.assertTimesStarted(1);
        RestartGameVerifierPage.assertHasSavedInstanceState(false);

        getTestService().restartFocusedGameSession();

        RestartGameVerifierPage.assertTimesStarted(2);
        RestartGameVerifierPage.assertHasSavedInstanceState(true);

        List<GameSessionEventInfo> gameSessionEventHistory =
                getTestService().getGameSessionEventHistory();

        assertThat(gameSessionEventHistory)
                .containsExactly(
                        GameSessionEventInfo.create(
                                RESTART_GAME_VERIFIER_PACKAGE_NAME,
                                gameTaskId,
                                GameSessionEventInfo.GAME_SESSION_EVENT_CREATED))
                .inOrder();
    }

    @Test
    public void restartGame_withNonGameActivityAbove_gameSessionIsPersisted() throws Exception {
        assumeGameServiceFeaturePresent();

        clearCache(RESTART_GAME_VERIFIER_PACKAGE_NAME);
        RestartGameVerifierPage.launch();

        int gameTaskId = getActivityTaskId(
                RESTART_GAME_VERIFIER_PACKAGE_NAME,
                RESTART_GAME_VERIFIER_PACKAGE_NAME + ".MainActivity");

        RestartGameVerifierPage.assertTimesStarted(1);
        RestartGameVerifierPage.assertHasSavedInstanceState(false);

        StartActivityVerifierPage.launch(getTestService());
        StartActivityVerifierPage.setResultCode(0x1337);
        StartActivityVerifierPage.setResultData("hello mom!");

        getTestService().restartFocusedGameSession();

        StartActivityVerifierPage.assertLaunched();
        StartActivityVerifierPage.clickSendResultButton();

        RestartGameVerifierPage.assertTimesStarted(2);
        RestartGameVerifierPage.assertHasSavedInstanceState(true);

        List<GameSessionEventInfo> gameSessionEventHistory =
                getTestService().getGameSessionEventHistory();

        assertThat(gameSessionEventHistory)
                .containsExactly(
                        GameSessionEventInfo.create(
                                RESTART_GAME_VERIFIER_PACKAGE_NAME,
                                gameTaskId,
                                GameSessionEventInfo.GAME_SESSION_EVENT_CREATED))
                .inOrder();

        ActivityResult result = getTestService().getLastActivityResult();

        assertThat(result.getGameSessionPackageName()).isEqualTo(
                RESTART_GAME_VERIFIER_PACKAGE_NAME);
        assertThat(result.getSuccess().getResultCode()).isEqualTo(0x1337);
        String resultData = StartActivityVerifierPage.getResultData(result.getSuccess().getData());
        assertThat(resultData).isEqualTo("hello mom!");
    }

    @Test
    public void gamePutInBackgroundAndRestoredViaRecentsUi_gameSessionRestarted() throws Exception {
        assumeGameServiceFeaturePresent();

        // The test game finishes its activity when it is put in the background by a press of the
        // back button.
        launchAndWaitForPackage(FINISH_ON_BACK_GAME_PACKAGE_NAME);

        assertThat(getTestService().getActiveSessions()).containsExactly(
                FINISH_ON_BACK_GAME_PACKAGE_NAME);

        // Close the game by pressing the back button.
        UiAutomatorUtils.getUiDevice().pressBack();
        UiAutomatorUtils.getUiDevice().waitForIdle();

        // With the game closed the game session should be destroyed
        PollingCheck.waitFor(TimeUnit.SECONDS.toMillis(20),
                () -> {
                    try {
                        return getTestService().getActiveSessions().isEmpty();
                    } catch (RemoteException e) {
                        throw new RuntimeException(e);
                    }
                },
                "Timed out waiting for game session to be destroyed.");

        // Restore the game via the Recents UI
        UiAutomatorUtils.getUiDevice().pressRecentApps();
        UiAutomatorUtils.getUiDevice().waitForIdle();

        logWindowHierarchy("after pressing recent apps");

        // Don't specify the full name of the test game "CtsGameServiceFinishOnBackGame" because it
        // may be ellipsized in the Recents UI.
        UiAutomatorUtils.waitFindObject(By.textStartsWith("CtsGameService")).click();

        logWindowHierarchy("after clicking on recent app");

        // There should again be an active game session for the restored game.
        PollingCheck.waitFor(TimeUnit.SECONDS.toMillis(20),
                () -> {
                    try {
                        return getTestService().getActiveSessions().size() == 1
                                && getTestService().getActiveSessions().get(0).equals(
                                FINISH_ON_BACK_GAME_PACKAGE_NAME);
                    } catch (RemoteException e) {
                        throw new RuntimeException(e);
                    }
                },
                "Timed out waiting for game session to be re-created.");
    }

    @Test
    public void takeScreenshot_noPermission() throws Exception {
        assumeGameServiceFeaturePresent();
        launchAndWaitForPackage(TAKE_SCREENSHOT_VERIFIER_PACKAGE_NAME);
        waitForTouchableOverlayBounds();
        getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
        final boolean ret = getTestService().takeScreenshotForFocusedGameSession();
        assertFalse(ret);
    }

    @Test
    public void takeScreenshot_expectedScreenshotSaved() throws Exception {
        assumeGameServiceFeaturePresent();

        launchAndWaitForPackage(TAKE_SCREENSHOT_VERIFIER_PACKAGE_NAME);

        // Make sure that the overlay is shown so that assertions can be made to check that
        // the overlay is excluded from the game screenshot.
        final Rect overlayBounds = waitForTouchableOverlayBounds();

        long startTimeSecs = Instant.now().getEpochSecond();
        final boolean ret = getTestService().takeScreenshotForFocusedGameSession();

        // Make sure a screenshot was taken, saved in media, and has the same dimensions as the
        // device screen.
        assertTrue(ret);

        List<Uri> screenshotUris = PollingCheck.waitFor(TimeUnit.SECONDS.toMillis(5),
                () -> getScreenshotsFromLastFiveSeconds(startTimeSecs), uris -> !uris.isEmpty());
        for (Uri screenshotUri : screenshotUris) {
            final ImageDecoder.Source source = ImageDecoder.createSource(mContentResolver,
                    screenshotUri);
            // convert the hardware bitmap to a mutable 4-byte bitmap to get/compare pixel
            final Bitmap gameScreenshot = ImageDecoder.decodeBitmap(source).copy(
                    Bitmap.Config.ARGB_8888, true);

            final Size screenSize = getScreenSize();
            assertThat(gameScreenshot.getWidth()).isEqualTo(screenSize.getWidth());
            assertThat(gameScreenshot.getHeight()).isEqualTo(screenSize.getHeight());

            // The test game is always fullscreen red. It is too expensive to verify that
            // the entire bitmap is red, so spot check certain areas.

            // 1. Make sure that the overlay is excluded from the screenshot by checking
            // pixels within the overlay bounds:

            // top-left of overlay bounds:
            assertThat(
                    gameScreenshot.getPixel(overlayBounds.left + 1,
                            overlayBounds.top + 1)).isEqualTo(
                    Color.RED);
            // bottom-left corner of overlay bounds:
            assertThat(gameScreenshot.getPixel(overlayBounds.left + 1,
                    overlayBounds.bottom - 1)).isEqualTo(Color.RED);
            // top-right corner of overlay bounds:
            assertThat(
                    gameScreenshot.getPixel(overlayBounds.right - 1,
                            overlayBounds.top + 1)).isEqualTo(
                    Color.RED);
            // bottom-right corner of overlay bounds:
            assertThat(gameScreenshot.getPixel(overlayBounds.right - 1,
                    overlayBounds.bottom - 1)).isEqualTo(Color.RED);
            // middle corner of overlay bounds:
            assertThat(
                    gameScreenshot.getPixel((overlayBounds.left + overlayBounds.right) / 2,
                            (overlayBounds.top + overlayBounds.bottom) / 2)).isEqualTo(
                    Color.RED);

            // 2. Also check some pixels between the edge of the screen and the overlay
            // bounds:

            // above and to the left of the overlay
            assertThat(
                    gameScreenshot.getPixel(overlayBounds.left / 2,
                            overlayBounds.top / 2)).isEqualTo(
                    Color.RED);
            // below and to the left of the overlay
            assertThat(gameScreenshot.getPixel(overlayBounds.left / 2,
                    (overlayBounds.bottom + gameScreenshot.getHeight()) / 2)).isEqualTo(
                    Color.RED);
            // above and to the right of the overlay
            assertThat(gameScreenshot.getPixel(
                    (overlayBounds.left + gameScreenshot.getWidth()) / 2,
                    overlayBounds.top / 2)).isEqualTo(Color.RED);
            // below and to the right of the overlay
            assertThat(gameScreenshot.getPixel(
                    (overlayBounds.left + gameScreenshot.getWidth()) / 2,
                    (overlayBounds.bottom + gameScreenshot.getHeight()) / 2)).isEqualTo(
                    Color.RED);

            // 3. Finally check some pixels at the corners of the screen:

            // top-left corner of screen
            assertThat(gameScreenshot.getPixel(0, 0)).isEqualTo(Color.RED);
            // bottom-left corner of screen
            assertThat(
                    gameScreenshot.getPixel(0, gameScreenshot.getHeight() - 1)).isEqualTo(
                    Color.RED);
            // top-right corner of screen
            assertThat(gameScreenshot.getPixel(gameScreenshot.getWidth() - 1, 0)).isEqualTo(
                    Color.RED);
            // bottom-right corner of screen
            assertThat(gameScreenshot.getPixel(gameScreenshot.getWidth() - 1,
                    gameScreenshot.getHeight() - 1)).isEqualTo(Color.RED);
            final PendingIntent pi = MediaStore.createDeleteRequest(mContentResolver,
                    ImmutableList.of(screenshotUri));
            final GetResultActivity.Result result = startIntentWithGrant(pi);
            assertEquals(Activity.RESULT_OK, result.resultCode);
        }
    }

    private List<Uri> getScreenshotsFromLastFiveSeconds(long startTimeSecs) {
        final Uri contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        final List<Uri> screenshotUris = new ArrayList<>();
        try (Cursor cursor = mContentResolver.query(contentUri,
                new String[]{MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
                        MediaStore.MediaColumns.DATE_ADDED}, null, null,
                MediaStore.MediaColumns.DATE_ADDED + " DESC")) {
            while (cursor.moveToNext()) {
                final long addedTimeSecs = cursor.getLong(2);
                // try to find the latest screenshot file created within 5s
                if (addedTimeSecs >= startTimeSecs && addedTimeSecs - startTimeSecs < 5) {
                    final long id = cursor.getLong(0);
                    final Uri screenshotUri = ContentUris.withAppendedId(contentUri, id);
                    final String name = cursor.getString(1);
                    Log.d(TAG, "Found screenshot with name " + name);
                    screenshotUris.add(screenshotUri);
                }
            }
        }
        return screenshotUris;
    }

    private GetResultActivity.Result startIntentWithGrant(PendingIntent pi) throws Exception {
        final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        final Intent intent = new Intent(inst.getContext(), GetResultActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        final UiDevice device = UiDevice.getInstance(inst);
        final GetResultActivity activity = (GetResultActivity) inst.startActivitySync(intent);
        inst.waitForIdleSync();
        activity.mResult.clear();
        device.waitForIdle();
        activity.startIntentSenderForResult(pi.getIntentSender(), 42, null, 0, 0, 0);
        device.waitForIdle();
        final UiSelector grant = new UiSelector().textMatches("(?i)Allow");
        final boolean grantExists = new UiObject(grant).waitForExists(5000);
        if (grantExists) {
            device.findObject(grant).click();
        }
        return activity.getResult();
    }

    private IGameServiceTestService getTestService() {
        return mServiceConnection.mService;
    }

    private static void assumeGameServiceFeaturePresent() {
        assumeTrue(gameServiceFeaturePresent());
    }

    private static boolean gameServiceFeaturePresent() {
        return getInstrumentation().getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_GAME_SERVICE);
    }

    private static void launchAndWaitForPackage(String packageName) throws Exception {
        PackageManager packageManager = getInstrumentation().getContext().getPackageManager();
        getInstrumentation().getContext().startActivity(
                packageManager.getLaunchIntentForPackage(packageName));
        UiAutomatorUtils.waitFindObject(By.pkg(packageName).depth(0));
    }

    private static void setText(String resourcePackage, String resourceId, String text)
            throws Exception {
        UiAutomatorUtils.waitFindObject(By.res(resourcePackage, resourceId))
                .setText(text);
    }

    private static void click(String resourcePackage, String resourceId) throws Exception {
        UiAutomatorUtils.waitFindObject(By.res(resourcePackage, resourceId))
                .click();
    }

    private static void swipeFromTopEdgeToShowSystemBars() {
        UiDevice uiDevice = UiAutomatorUtils.getUiDevice();
        uiDevice.swipe(
                uiDevice.getDisplayWidth() / 2, 20,
                uiDevice.getDisplayWidth() / 2, uiDevice.getDisplayHeight() / 2,
                10);
    }

    private void waitForGameServiceConnected() {
        PollingCheck.waitFor(TimeUnit.SECONDS.toMillis(40), () -> isGameServiceConnected(),
                "Timed out waiting for game service to connect");
    }

    private void waitForGameServiceDisconnected() {
        PollingCheck.waitFor(TimeUnit.SECONDS.toMillis(5), () -> !isGameServiceConnected(),
                "Timed out waiting for game service to disconnect");
    }

    private boolean isGameServiceConnected() {
        try {
            return getTestService().isGameServiceConnected();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    private Rect waitForTouchableOverlayBounds() {
        return PollingCheck.waitFor(
                TimeUnit.SECONDS.toMillis(5),
                () -> {
                    try {
                        return getTestService().getTouchableOverlayBounds();
                    } catch (RemoteException e) {
                        throw new RuntimeException(e);
                    }
                },
                bounds -> bounds != null && !bounds.isEmpty());
    }

    private void assertOverlayDoesNotAppear() {
        assertThat(waitForTouchableOverlayBounds().isEmpty()).isTrue();
    }

    private Rect waitForOverlayToDisappear() {
        return PollingCheck.waitFor(
                TimeUnit.SECONDS.toMillis(20),
                () -> {
                    try {
                        return getTestService().getTouchableOverlayBounds();
                    } catch (RemoteException e) {
                        throw new RuntimeException(e);
                    }
                },
                Rect::isEmpty);
    }

    private static void forceStop(String packageName) {
        ShellUtils.runShellCommand("am force-stop %s", packageName);
        UiAutomatorUtils.getUiDevice().wait(Until.gone(By.pkg(packageName).depth(0)),
                TimeUnit.SECONDS.toMillis(20));
    }

    private static void clearCache(String packageName) {
        ShellUtils.runShellCommand("pm clear %s", packageName);
    }

    private static int getActivityTaskId(String packageName, String componentName) {
        final String output = ShellUtils.runShellCommand("am stack list");
        final Pattern pattern = Pattern.compile(
                String.format(".*taskId=([0-9]+): %s/%s.*", packageName, componentName));

        for (String line : output.split("\\n")) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.matches()) {
                String taskId = matcher.group(1);
                return Integer.parseInt(taskId);
            }
        }

        return -1;
    }

    private static class RestartGameVerifierPage {
        private RestartGameVerifierPage() {
        }

        public static void launch() throws Exception {
            launchAndWaitForPackage(RESTART_GAME_VERIFIER_PACKAGE_NAME);
        }

        public static void assertTimesStarted(int times) throws UiObjectNotFoundException {
            UiAutomatorUtils.waitFindObject(
                    By.res(RESTART_GAME_VERIFIER_PACKAGE_NAME, "times_started").text(
                            String.valueOf(times)));
        }

        public static void assertHasSavedInstanceState(boolean hasSavedInstanceState)
                throws UiObjectNotFoundException {
            UiAutomatorUtils.waitFindObject(
                    By.res(RESTART_GAME_VERIFIER_PACKAGE_NAME, "has_saved_instance_state").text(
                            String.valueOf(hasSavedInstanceState)));
        }
    }

    private static class StartActivityVerifierPage {

        public static void launch(IGameServiceTestService testService) throws Exception {
            testService.startGameSessionActivity(
                    new Intent("android.service.games.cts.startactivityverifier.START"), null);
        }

        public static void assertLaunched() throws UiObjectNotFoundException {
            UiAutomatorUtils.waitFindObject(By.pkg(START_ACTIVITY_VERIFIER_PACKAGE_NAME).depth(0));
        }

        public static void setResultCode(int resultCode) throws Exception {
            setText(START_ACTIVITY_VERIFIER_PACKAGE_NAME, "result_code_edit_text",
                    String.valueOf(resultCode));
        }

        public static void setResultData(String resultData) throws Exception {
            setText(START_ACTIVITY_VERIFIER_PACKAGE_NAME, "result_data_edit_text", resultData);
        }

        public static void clickSendResultButton() throws Exception {
            click(START_ACTIVITY_VERIFIER_PACKAGE_NAME, "send_result_button");
        }

        public static String getResultData(Intent data) {
            return data.getStringExtra("data");
        }
    }

    private static Size getScreenSize() {
        WindowManager wm =
                (WindowManager)
                        InstrumentationRegistry.getInstrumentation()
                                .getContext()
                                .getSystemService(Context.WINDOW_SERVICE);
        WindowMetrics windowMetrics = wm.getCurrentWindowMetrics();
        Rect windowBounds = windowMetrics.getBounds();
        return new Size(windowBounds.width(), windowBounds.height());
    }

    private void logWindowHierarchy(String state) {
        try {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            UiAutomatorUtils.getUiDevice().dumpWindowHierarchy(os);

            Log.d(TAG, "Window hierarchy: " + state);
            for (String line : os.toString("UTF-8").split("\n")) {
                Log.d(TAG, line);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final class ServiceConnection implements android.content.ServiceConnection {
        private final Semaphore mSemaphore = new Semaphore(0);
        private IGameServiceTestService mService;

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = IGameServiceTestService.Stub.asInterface(service);
            mSemaphore.release();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }

        public void waitForConnection(int timeout, TimeUnit timeUnit) throws Exception {
            assertThat(mSemaphore.tryAcquire(timeout, timeUnit)).isTrue();
        }
    }
}

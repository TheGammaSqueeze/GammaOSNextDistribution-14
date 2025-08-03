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

package android.media.bettertogether.cts;

import static androidx.test.ext.truth.os.BundleSubject.assertThat;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaController2;
import android.media.MediaSession2;
import android.media.Session2Command;
import android.media.Session2CommandGroup;
import android.media.Session2Token;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Tests {@link android.media.MediaController2}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class MediaController2Test {
    private static final long WAIT_TIME_MS = 100L;

    static final Object sTestLock = new Object();

    static final int ALLOWED_COMMAND_CODE = 100;
    static final Session2CommandGroup SESSION_ALLOWED_COMMANDS = new Session2CommandGroup.Builder()
            .addCommand(new Session2Command(ALLOWED_COMMAND_CODE)).build();
    static final int SESSION_RESULT_CODE = 101;
    static final String SESSION_RESULT_KEY = "test_result_key";
    static final String SESSION_RESULT_VALUE = "test_result_value";
    static final Session2Command.Result SESSION_COMMAND_RESULT;

    private static final String TEST_KEY = "test_key";
    private static final String TEST_VALUE = "test_value";

    static {
        Bundle resultData = new Bundle();
        resultData.putString(SESSION_RESULT_KEY, SESSION_RESULT_VALUE);
        SESSION_COMMAND_RESULT = new Session2Command.Result(SESSION_RESULT_CODE, resultData);
    }

    static Handler sHandler;
    static Executor sHandlerExecutor;

    private Context mContext;
    private Bundle mExtras;
    private MediaSession2 mSession;
    private Session2Callback mSessionCallback;

    @BeforeClass
    public static void setUpThread() {
        synchronized (MediaSession2Test.class) {
            if (sHandler != null) {
                return;
            }
            HandlerThread handlerThread = new HandlerThread("MediaSessionTestBase");
            handlerThread.start();
            sHandler = new Handler(handlerThread.getLooper());
            sHandlerExecutor = (runnable) -> {
                Handler handler;
                synchronized (MediaSession2Test.class) {
                    handler = sHandler;
                }
                if (handler != null) {
                    handler.post(() -> {
                        synchronized (sTestLock) {
                            runnable.run();
                        }
                    });
                }
            };
        }
    }

    @AfterClass
    public static void cleanUpThread() {
        synchronized (MediaSession2Test.class) {
            if (sHandler == null) {
                return;
            }
            sHandler.getLooper().quitSafely();
            sHandler = null;
            sHandlerExecutor = null;
        }
    }

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mSessionCallback = new Session2Callback();
        mExtras = new Bundle();
        mExtras.putString(TEST_KEY, TEST_VALUE);
        mSession = new MediaSession2.Builder(mContext)
                .setSessionCallback(sHandlerExecutor, mSessionCallback)
                .setExtras(mExtras)
                .build();
    }

    @After
    public void cleanUp() {
        if (mSession != null) {
            mSession.close();
            mSession = null;
        }
    }

    @Test
    public void testBuilder_withIllegalArguments() {
        final Session2Token token = new Session2Token(
                mContext, new ComponentName(mContext, this.getClass()));

        assertThrows("null context shouldn't be accepted!", IllegalArgumentException.class,
                () -> new MediaController2.Builder(null, token));

        assertThrows("null token shouldn't be accepted!", IllegalArgumentException.class,
                () -> new MediaController2.Builder(mContext, null));

        assertThrows("null connectionHints shouldn't be accepted!",
                IllegalArgumentException.class, () -> {
                    MediaController2.Builder builder =
                            new MediaController2.Builder(mContext, token);
                    builder.setConnectionHints(null);
                });

        assertThrows("null Executor shouldn't be accepted!",
                IllegalArgumentException.class, () -> {
                    MediaController2.Builder builder =
                            new MediaController2.Builder(mContext, token);
                    builder.setControllerCallback(null,
                            new MediaController2.ControllerCallback() {});
                });

        assertThrows("null ControllerCallback shouldn't be accepted!",
                IllegalArgumentException.class, () -> {
                    MediaController2.Builder builder =
                            new MediaController2.Builder(mContext, token);
                    builder.setControllerCallback(Executors.newSingleThreadExecutor(), null);
                });
    }

    @Test
    public void testBuilder_setConnectionHints_withFrameworkParcelable() throws Exception {
        final List<MediaSession2.ControllerInfo> controllerInfoList = new ArrayList<>();
        final CountDownLatch latch = new CountDownLatch(1);

        try (MediaSession2 session = new MediaSession2.Builder(mContext)
                .setId("testBuilder_setConnectionHints_withFrameworkParcelable")
                .setSessionCallback(sHandlerExecutor, new MediaSession2.SessionCallback() {
                    @Override
                    public Session2CommandGroup onConnect(MediaSession2 session,
                            MediaSession2.ControllerInfo controller) {
                        if (controller.getUid() == Process.myUid()) {
                            controllerInfoList.add(controller);
                            latch.countDown();
                            return new Session2CommandGroup.Builder().build();
                        }
                        return null;
                    }
                })
                .build()) {

            final Session2Token frameworkParcelable = new Session2Token(
                    mContext, new ComponentName(mContext, this.getClass()));
            final String testKey = "test_key";

            Bundle connectionHints = new Bundle();
            connectionHints.putParcelable(testKey, frameworkParcelable);

            MediaController2 controller = new MediaController2.Builder(mContext, session.getToken())
                    .setConnectionHints(connectionHints)
                    .build();
            assertThat(latch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS)).isTrue();

            Bundle connectionHintsOut = controllerInfoList.get(0).getConnectionHints();
            assertThat(connectionHintsOut).containsKey(testKey);
            assertThat(connectionHintsOut).parcelable(testKey).isEqualTo(frameworkParcelable);
        }
    }

    @Test
    public void testBuilder_setConnectionHints_withCustomParcelable() {
        final Session2Token token = new Session2Token(
                mContext, new ComponentName(mContext, this.getClass()));
        final String testKey = "test_key";
        final MediaSession2Test.CustomParcelable customParcelable =
                new MediaSession2Test.CustomParcelable(1);

        Bundle connectionHints = new Bundle();
        connectionHints.putParcelable(testKey, customParcelable);

        assertThrows("Custom Parcelables shouldn't be accepted!",
                IllegalArgumentException.class,
                () -> new MediaController2.Builder(mContext, token)
                        .setConnectionHints(connectionHints)
                        .build());
    }

    @Test
    public void testCreatingControllerWithoutCallback() throws Exception {
        try (MediaController2 controller =
                     new MediaController2.Builder(mContext, mSession.getToken()).build()) {
            assertThat(mSessionCallback.mOnConnectedLatch.await(
                    WAIT_TIME_MS, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(mSessionCallback.mControllerInfo.getPackageName())
                    .isEqualTo(mContext.getPackageName());
        }
    }

    @Test
    public void testGetConnectedToken() {
        Controller2Callback controllerCallback = new Controller2Callback();
        try (MediaController2 controller =
                     new MediaController2.Builder(mContext, mSession.getToken())
                             .setControllerCallback(sHandlerExecutor, controllerCallback)
                             .build()) {
            assertThat(controllerCallback.awaitOnConnected(WAIT_TIME_MS)).isTrue();
            assertThat(controllerCallback.mController).isEqualTo(controller);
            assertThat(controller.getConnectedToken()).isEqualTo(mSession.getToken());

            Bundle extrasFromConnectedSessionToken =
                    controller.getConnectedToken().getExtras();
            assertThat(extrasFromConnectedSessionToken).isNotNull();
            assertThat(extrasFromConnectedSessionToken.getString(TEST_KEY))
                    .isEqualTo(TEST_VALUE);
        } finally {
            assertThat(controllerCallback.awaitOnDisconnected(WAIT_TIME_MS)).isTrue();
            assertThat(controllerCallback.mController.getConnectedToken()).isNull();
        }
    }

    @Test
    public void testCallback_onConnected_onDisconnected() {
        Controller2Callback controllerCallback = new Controller2Callback();
        try (MediaController2 controller =
                     new MediaController2.Builder(mContext, mSession.getToken())
                             .setControllerCallback(sHandlerExecutor, controllerCallback)
                             .build()) {
            assertThat(controllerCallback.awaitOnConnected(WAIT_TIME_MS)).isTrue();
            assertThat(controllerCallback.mController).isEqualTo(controller);
            assertThat(controllerCallback.mAllowedCommands.hasCommand(ALLOWED_COMMAND_CODE))
                    .isTrue();
        } finally {
            assertThat(controllerCallback.awaitOnDisconnected(WAIT_TIME_MS)).isTrue();
        }
    }

    @Test
    public void testCallback_onSessionCommand() {
        Controller2Callback controllerCallback = new Controller2Callback();
        try (MediaController2 controller =
                     new MediaController2.Builder(mContext, mSession.getToken())
                             .setControllerCallback(sHandlerExecutor, controllerCallback)
                             .build()) {
            assertThat(controllerCallback.awaitOnConnected(WAIT_TIME_MS)).isTrue();

            String commandStr = "test_command";
            String commandExtraKey = "test_extra_key";
            String commandExtraValue = "test_extra_value";
            Bundle commandExtra = new Bundle();
            commandExtra.putString(commandExtraKey, commandExtraValue);
            Session2Command command = new Session2Command(commandStr, commandExtra);

            String commandArgKey = "test_arg_key";
            String commandArgValue = "test_arg_value";
            Bundle commandArg = new Bundle();
            commandArg.putString(commandArgKey, commandArgValue);
            mSession.sendSessionCommand(mSessionCallback.mControllerInfo, command, commandArg);

            assertThat(controllerCallback.awaitOnSessionCommand(WAIT_TIME_MS)).isTrue();
            assertThat(controllerCallback.mController)
                    .isEqualTo(controller);
            assertThat(controllerCallback.mCommand.getCustomAction())
                    .isEqualTo(commandStr);
            assertThat(controllerCallback.mCommand.getCustomExtras().getString(commandExtraKey))
                    .isEqualTo(commandExtraValue);
            assertThat(controllerCallback.mCommandArgs.getString(commandArgKey))
                    .isEqualTo(commandArgValue);
        } finally {
            assertThat(controllerCallback.awaitOnDisconnected(WAIT_TIME_MS)).isTrue();
        }
    }

    @Test
    public void testCallback_onCommandResult() {
        Controller2Callback controllerCallback = new Controller2Callback();
        try (MediaController2 controller =
                     new MediaController2.Builder(mContext, mSession.getToken())
                             .setControllerCallback(sHandlerExecutor, controllerCallback)
                             .build()) {
            assertThat(controllerCallback.awaitOnConnected(WAIT_TIME_MS)).isTrue();

            String commandStr = "test_command";
            String commandExtraKey = "test_extra_key";
            String commandExtraValue = "test_extra_value";
            Bundle commandExtra = new Bundle();
            commandExtra.putString(commandExtraKey, commandExtraValue);
            Session2Command command = new Session2Command(commandStr, commandExtra);

            String commandArgKey = "test_arg_key";
            String commandArgValue = "test_arg_value";
            Bundle commandArg = new Bundle();
            commandArg.putString(commandArgKey, commandArgValue);
            controller.sendSessionCommand(command, commandArg);

            assertThat(controllerCallback.awaitOnCommandResult(WAIT_TIME_MS)).isTrue();
            assertThat(controllerCallback.mController).isEqualTo(controller);
            assertThat(controllerCallback.mCommandResult.getResultCode())
                    .isEqualTo(SESSION_RESULT_CODE);
            assertThat(
                    controllerCallback.mCommandResult.getResultData().getString(SESSION_RESULT_KEY))
                    .isEqualTo(SESSION_RESULT_VALUE);
        } finally {
            assertThat(controllerCallback.awaitOnDisconnected(WAIT_TIME_MS)).isTrue();
        }
    }

    @Test
    public void testCancelSessionCommand() {
        Controller2Callback controllerCallback = new Controller2Callback();
        try (MediaController2 controller =
                     new MediaController2.Builder(mContext, mSession.getToken())
                             .setControllerCallback(sHandlerExecutor, controllerCallback)
                             .build()) {
            assertThat(controllerCallback.awaitOnConnected(WAIT_TIME_MS)).isTrue();

            String commandStr = "test_command_";
            String commandExtraKey = "test_extra_key_";
            String commandExtraValue = "test_extra_value_";
            Bundle commandExtra = new Bundle();
            commandExtra.putString(commandExtraKey, commandExtraValue);
            Session2Command command = new Session2Command(commandStr, commandExtra);

            String commandArgKey = "test_arg_key_";
            String commandArgValue = "test_arg_value_";
            Bundle commandArg = new Bundle();
            commandArg.putString(commandArgKey, commandArgValue);
            synchronized (sTestLock) {
                Object token = controller.sendSessionCommand(command, commandArg);
                controller.cancelSessionCommand(token);
            }
            assertThat(controllerCallback.awaitOnCommandResult(WAIT_TIME_MS)).isTrue();
            assertThat(controllerCallback.mCommandResult.getResultCode())
                    .isEqualTo(Session2Command.Result.RESULT_INFO_SKIPPED);
        } finally {
            assertThat(controllerCallback.awaitOnDisconnected(WAIT_TIME_MS)).isTrue();
        }
    }

    class Session2Callback extends MediaSession2.SessionCallback {
        MediaSession2.ControllerInfo mControllerInfo;
        CountDownLatch mOnConnectedLatch = new CountDownLatch(1);

        @Override
        public Session2CommandGroup onConnect(MediaSession2 session,
                MediaSession2.ControllerInfo controller) {
            if (controller.getUid() != Process.myUid()) {
                return null;
            }
            mControllerInfo = controller;
            mOnConnectedLatch.countDown();
            return SESSION_ALLOWED_COMMANDS;
        }

        @Override
        public Session2Command.Result onSessionCommand(MediaSession2 session,
                MediaSession2.ControllerInfo controller, Session2Command command, Bundle args) {
            return SESSION_COMMAND_RESULT;
        }
    }

    class Controller2Callback extends MediaController2.ControllerCallback {
        CountDownLatch mOnConnectedLatch = new CountDownLatch(1);
        CountDownLatch mOnDisconnectedLatch = new CountDownLatch(1);
        private CountDownLatch mOnSessionCommandLatch = new CountDownLatch(1);
        private CountDownLatch mOnCommandResultLatch = new CountDownLatch(1);

        MediaController2 mController;
        Session2Command mCommand;
        Session2CommandGroup mAllowedCommands;
        Bundle mCommandArgs;
        Session2Command.Result mCommandResult;

        public boolean await(long waitMs) {
            try {
                return mOnSessionCommandLatch.await(waitMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                return false;
            }
        }

        @Override
        public void onConnected(MediaController2 controller, Session2CommandGroup allowedCommands) {
            super.onConnected(controller, allowedCommands);
            mController = controller;
            mAllowedCommands = allowedCommands;
            mOnConnectedLatch.countDown();
        }

        @Override
        public void onDisconnected(MediaController2 controller) {
            super.onDisconnected(controller);
            mController = controller;
            mOnDisconnectedLatch.countDown();
        }

        @Override
        public Session2Command.Result onSessionCommand(MediaController2 controller,
                Session2Command command, Bundle args) {
            super.onSessionCommand(controller, command, args);
            mController = controller;
            mCommand = command;
            mCommandArgs = args;
            mOnSessionCommandLatch.countDown();
            return SESSION_COMMAND_RESULT;
        }

        @Override
        public void onCommandResult(MediaController2 controller, Object token,
                Session2Command command, Session2Command.Result result) {
            super.onCommandResult(controller, token, command, result);
            mController = controller;
            mCommand = command;
            mCommandResult = result;
            mOnCommandResultLatch.countDown();
        }

        @Override
        public void onPlaybackActiveChanged(MediaController2 controller, boolean playbackActive) {
            super.onPlaybackActiveChanged(controller, playbackActive);
        }

        public boolean awaitOnConnected(long waitTimeMs) {
            try {
                return mOnConnectedLatch.await(waitTimeMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                return false;
            }
        }

        public boolean awaitOnDisconnected(long waitTimeMs) {
            try {
                return mOnDisconnectedLatch.await(waitTimeMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                return false;
            }
        }

        public boolean awaitOnSessionCommand(long waitTimeMs) {
            try {
                return mOnSessionCommandLatch.await(waitTimeMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                return false;
            }
        }

        public boolean awaitOnCommandResult(long waitTimeMs) {
            try {
                return mOnCommandResultLatch.await(waitTimeMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                return false;
            }
        }
    }
}

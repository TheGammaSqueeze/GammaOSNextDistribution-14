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

package com.android.tests.atomicinstall;

import static com.android.compatibility.common.util.MatcherUtils.assertThrows;
import static com.android.compatibility.common.util.MatcherUtils.hasMessageThat;
import static com.android.compatibility.common.util.MatcherUtils.instanceOf;
import static com.android.cts.install.lib.InstallUtils.openPackageInstallerSession;

import static com.google.common.truth.Truth.assertThat;

import static org.hamcrest.CoreMatchers.containsString;

import android.Manifest;
import android.content.pm.PackageInstaller;
import android.os.Handler;
import android.os.HandlerThread;

import androidx.annotation.NonNull;
import androidx.core.util.Preconditions;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.compatibility.common.util.PollingCheck;
import com.android.cts.install.lib.Install;
import com.android.cts.install.lib.InstallUtils;
import com.android.cts.install.lib.TestApp;
import com.android.cts.install.lib.Uninstall;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


/**
 * There are the following factors need to combine for testing the abandon behavior.
 * <ul>
 *     <li>staged vs. noStaged</li>
 *     <li>Single Package vs. MultiPackage</li>
 *     <li>Receive callback, Session Info abandoned, getNames, openWrite, open abandoned session
 *     etc.</li>
 * </ul>*
 */
@RunWith(AndroidJUnit4.class)
public class SessionAbandonBehaviorTest {
    /**
     * Please don't change too small to ensure the test run normally.
     */
    public static final int CALLBACK_TIMEOUT_SECONDS = 10;

    /**
     * To wait 1 second prevents the race condition from the framework services.
     * The child session is cleaned up asynchronously after abandoning the parent session. Even
     * if receiving the callback to tell the session is finished, it may be the race condition
     * between executing {@link PackageInstaller#getSessionInfo(int)} and cleaning up the
     * {@link android.content.pm.PackageInstaller.Session}.
     */
    public static final long PREVENT_RACE_CONDITION_TIMEOUT_SECONDS = TimeUnit.SECONDS.toMillis(1);
    private static final byte[] PLACE_HOLDER_STRING_BYTES = "Place Holder".getBytes();

    /**
     * This is a wrapper class to let the test easier to focus on the "onFinish". It implements
     * all of abstract methods with nothing in {@link PackageInstaller.SessionCallback} except for
     * onFinish function.
     */
    private static class AbandonSessionCallBack extends PackageInstaller.SessionCallback {
        private final CountDownLatch mCountDownLatch;
        private final List<Integer> mSessionIds;

        AbandonSessionCallBack(CountDownLatch countDownLatch, int[] sessionIds) {
            mCountDownLatch = countDownLatch;
            mSessionIds = new ArrayList<>();
            for (int sessionId : sessionIds) {
                mSessionIds.add(sessionId);
            }
        }

        AbandonSessionCallBack(CountDownLatch countDownLatch, int sessionId) {
            this(countDownLatch, new int[]{sessionId});
        }

        @Override
        public void onCreated(int sessionId) {
            /* Do nothing to make sub class no need to implement it*/
        }

        @Override
        public void onBadgingChanged(int sessionId) {
            /* Do nothing to make sub class no need to implement it*/
        }

        @Override
        public void onActiveChanged(int sessionId, boolean active) {
            /* Do nothing to make sub class no need to implement it*/
        }

        @Override
        public void onProgressChanged(int sessionId, float progress) {
            /* Do nothing to make sub class no need to implement it*/
        }

        @Override
        public void onFinished(int sessionId, boolean success) {
            if (!success) {
                if (mSessionIds.contains(sessionId)) {
                    mCountDownLatch.countDown();
                }
            }
        }
    }

    @Rule
    public final AdoptShellPermissionsRule mAdoptShellPermissionsRule =
            new AdoptShellPermissionsRule(
                    InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                    Manifest.permission.INSTALL_PACKAGES, Manifest.permission.DELETE_PACKAGES);

    @Rule
    public final TestName mTestName = new TestName();

    private final List<PackageInstaller.SessionCallback> mSessionCallbacks = new ArrayList<>();

    private Handler mHandler;
    private HandlerThread mHandlerThread;
    private List<Closeable> mCloseableList = new ArrayList<>();

    @After
    public void tearDown() {
        for (Closeable closeable : mCloseableList) {
            try {
                closeable.close();
            } catch (IOException e) {
                /* ensure close the resources and do no nothing */
            }
        }

        for (PackageInstaller.SessionCallback sessionCallback : mSessionCallbacks) {
            InstallUtils.getPackageInstaller().unregisterSessionCallback(sessionCallback);
        }
        mSessionCallbacks.clear();

        if (mHandlerThread != null) {
            mHandlerThread.quit();
        }
    }


    /**
     * To help the test to register the {@link PackageInstaller.SessionCallback} easier and the
     * parameter {@link PackageInstaller.SessionCallback} will unregister after the end of the
     * test.
     *
     * @param sessionCallback registers by the {@link PackageInstaller}
     */
    private void registerSessionCallbacks(
            @NonNull PackageInstaller.SessionCallback sessionCallback) {
        Preconditions.checkNotNull(sessionCallback);
        Preconditions.checkArgument(!mSessionCallbacks.contains(sessionCallback),
                "The callback has registered.");

        if (mHandler == null) {
            mHandlerThread = new HandlerThread(mTestName.getMethodName());
            mHandlerThread.start();
            mHandler = new Handler(mHandlerThread.getLooper());
        }

        InstallUtils.getPackageInstaller().registerSessionCallback(sessionCallback, mHandler);
        mSessionCallbacks.add(sessionCallback);
    }

    /**
     * To get all of child session IDs.
     *
     * @param parentSessionId the parent session id
     * @return the array of child session IDs
     * @throws IOException caused by opening parent session fail.
     */
    private int[] getChildSessionIds(int parentSessionId) throws IOException {
        try (PackageInstaller.Session parentSession =
                     openPackageInstallerSession(parentSessionId)) {
            return parentSession.getChildSessionIds();
        }
    }

    private static List<PackageInstaller.SessionInfo> getAllChildSessions(int[] sessionIds) {
        List<PackageInstaller.SessionInfo> result = new ArrayList<>();
        for (int sessionId : sessionIds) {
            final PackageInstaller.SessionInfo session =
                    InstallUtils.getPackageInstaller().getSessionInfo(sessionId);
            if (session != null) {
                result.add(session);
            }
        }
        return result;
    }

    /**
     * To open the specified session.
     * <p>
     * The opened resources will be closed in {@link #tearDown()} automatically.
     * </p>
     *
     * @param sessionId the session want to open
     * @return the opened {@link PackageInstaller.Session} instance
     * @throws IOException caused by opening {@link PackageInstaller.Session} fail.
     */
    private PackageInstaller.Session openSession(int sessionId) throws IOException {
        PackageInstaller.Session session = openPackageInstallerSession(sessionId);
        mCloseableList.add(session);

        return session;
    }

    /**
     * To open and write the file for the specified session.
     * <p>
     * The opened resources will be closed in {@link #tearDown()} automatically.
     * </p>
     *
     * @param sessionId the session want to open
     * @param fileName  the expected file name
     * @return the opened {@link OutputStream} instance
     * @throws IOException caused by opening file fail.
     */
    private OutputStream openSessionForWrite(int sessionId, String fileName) throws IOException {
        PackageInstaller.Session session = openSession(sessionId);
        OutputStream os = session.openWrite(fileName, 0, -1);
        mCloseableList.add(os);

        return os;
    }

    @Before
    public void setUp() throws Exception {
        Uninstall.packages(TestApp.A, TestApp.B);
    }

    @Test
    public void abandon_stagedSession_shouldReceiveAbandonCallBack()
            throws Exception {
        final int sessionId = Install.single(TestApp.A1).setStaged().createSession();
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        registerSessionCallbacks(
                new AbandonSessionCallBack(countDownLatch, sessionId));

        InstallUtils.getPackageInstaller().abandonSession(sessionId);

        assertThat(
                countDownLatch.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    public void abandon_nonStagedSession_shouldReceiveAbandonCallBack()
            throws Exception {
        final int sessionId = Install.single(TestApp.A1).createSession();
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        registerSessionCallbacks(
                new AbandonSessionCallBack(countDownLatch, sessionId));

        InstallUtils.getPackageInstaller().abandonSession(sessionId);

        assertThat(
                countDownLatch.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }


    @Test
    public void abandon_stagedSession_openedSession_canNotGetNames()
            throws Exception {
        final int sessionId = Install.single(TestApp.A1).setStaged().createSession();
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final PackageInstaller.Session session = openSession(sessionId);
        registerSessionCallbacks(
                new AbandonSessionCallBack(countDownLatch, sessionId));

        InstallUtils.getPackageInstaller().abandonSession(sessionId);
        countDownLatch.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThrows(instanceOf(SecurityException.class,
                hasMessageThat(containsString("getNames not allowed"))),
                () -> session.getNames());
    }

    @Test
    public void abandon_nonStagedSession_openedSession_canNotGetNames()
            throws Exception {
        final int sessionId = Install.single(TestApp.A1).createSession();
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final PackageInstaller.Session session = openSession(sessionId);
        registerSessionCallbacks(
                new AbandonSessionCallBack(countDownLatch, sessionId));

        InstallUtils.getPackageInstaller().abandonSession(sessionId);
        countDownLatch.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThrows(instanceOf(SecurityException.class,
                hasMessageThat(containsString("getNames not allowed"))),
                () -> session.getNames());
    }

    @Test
    public void abandon_stagedSession_openForWriting_shouldFail()
            throws Exception {
        final int sessionId = Install.single(TestApp.A1).setStaged().createSession();
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        registerSessionCallbacks(
                new AbandonSessionCallBack(countDownLatch, sessionId));
        final OutputStream outputStream = openSessionForWrite(sessionId,
                mTestName.getMethodName());

        InstallUtils.getPackageInstaller().abandonSession(sessionId);
        countDownLatch.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThrows(instanceOf(IOException.class,
                hasMessageThat(containsString("write failed"))),
                () -> outputStream.write(PLACE_HOLDER_STRING_BYTES));
    }

    @Test
    public void abandon_nonStagedSession_openForWriting_shouldFail()
            throws Exception {
        final int sessiondId = Install.single(TestApp.A1).createSession();
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        registerSessionCallbacks(
                new AbandonSessionCallBack(countDownLatch, sessiondId));
        final OutputStream outputStream = openSessionForWrite(sessiondId,
                mTestName.getMethodName());

        InstallUtils.getPackageInstaller().abandonSession(sessiondId);
        countDownLatch.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThrows(instanceOf(IOException.class,
                hasMessageThat(containsString("write failed"))),
                () -> outputStream.write(PLACE_HOLDER_STRING_BYTES));
    }

    @Test
    public void abandon_stagedSession_canNotOpenAgain()
            throws Exception {
        final int sessionId = Install.single(TestApp.A1).setStaged().createSession();
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        registerSessionCallbacks(
                new AbandonSessionCallBack(countDownLatch, sessionId));

        InstallUtils.getPackageInstaller().abandonSession(sessionId);
        countDownLatch.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThrows(instanceOf(SecurityException.class,
                hasMessageThat(containsString(String.valueOf(sessionId)))),
                () -> InstallUtils.getPackageInstaller().openSession(sessionId));
    }

    @Test
    public void abandon_nonStagedSession_canNotOpenAgain()
            throws Exception {
        final int sessionId = Install.single(TestApp.A1).createSession();
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        registerSessionCallbacks(
                new AbandonSessionCallBack(countDownLatch, sessionId));

        InstallUtils.getPackageInstaller().abandonSession(sessionId);
        countDownLatch.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThrows(instanceOf(SecurityException.class,
                hasMessageThat(containsString(String.valueOf(sessionId)))),
                () -> InstallUtils.getPackageInstaller().openSession(sessionId));
    }

    @Test
    public void abandon_stagedParentSession_shouldReceiveAllChildrenAbandonCallBack()
            throws Exception {
        final int parentSessionId = Install.multi(TestApp.A1,
                TestApp.B1).setStaged().createSession();
        final int[] childSessionIds = getChildSessionIds(parentSessionId);
        final CountDownLatch countDownLatch = new CountDownLatch(childSessionIds.length);
        registerSessionCallbacks(
                new AbandonSessionCallBack(countDownLatch, childSessionIds));

        InstallUtils.getPackageInstaller().abandonSession(parentSessionId);

        assertThat(
                countDownLatch.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    public void abandon_nonStagedParentSession_shouldReceiveAllChildrenAbandonCallBack()
            throws Exception {
        final int parentSessionId = Install.multi(TestApp.A1, TestApp.B1).createSession();
        final int[] childSessionIds = getChildSessionIds(parentSessionId);
        final CountDownLatch countDownLatch = new CountDownLatch(childSessionIds.length);
        registerSessionCallbacks(
                new AbandonSessionCallBack(countDownLatch, childSessionIds));

        InstallUtils.getPackageInstaller().abandonSession(parentSessionId);

        assertThat(
                countDownLatch.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    public void abandon_stagedParentSession_shouldAbandonAllChildrenSessions()
            throws Exception {
        final int parentSessionId = Install.multi(TestApp.A1, TestApp.B1)
                .setStaged().createSession();
        final int[] childSessionIds = getChildSessionIds(parentSessionId);
        final CountDownLatch countDownLatch = new CountDownLatch(childSessionIds.length);
        registerSessionCallbacks(
                new AbandonSessionCallBack(countDownLatch, childSessionIds));

        InstallUtils.getPackageInstaller().abandonSession(parentSessionId);
        countDownLatch.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // The child session is cleaned up asynchronously after abandoning the parent session.
        PollingCheck.check("The result should be an empty list.",
                PREVENT_RACE_CONDITION_TIMEOUT_SECONDS,
                () -> getAllChildSessions(childSessionIds).isEmpty());
    }

    @Test
    public void abandon_nonStagedParentSession_shouldAbandonAllChildrenSessions()
            throws Exception {
        final int parentSessionId = Install.multi(TestApp.A1, TestApp.B1).createSession();
        final int[] childSessionIds = getChildSessionIds(parentSessionId);
        final CountDownLatch countDownLatch = new CountDownLatch(childSessionIds.length);
        registerSessionCallbacks(
                new AbandonSessionCallBack(countDownLatch, childSessionIds));

        InstallUtils.getPackageInstaller().abandonSession(parentSessionId);
        countDownLatch.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // The child session is cleaned up asynchronously after abandoning the parent session.
        PollingCheck.check("The result should be empty list",
                PREVENT_RACE_CONDITION_TIMEOUT_SECONDS,
                () -> getAllChildSessions(childSessionIds).isEmpty());
    }

    @Test
    public void abandon_stagedParentSession_openedChildSession_getNamesShouldReturnEmptyList()
            throws Exception {
        final int parentSessionId = Install.multi(TestApp.A1).setStaged().createSession();
        final int[] childSessionIds = getChildSessionIds(parentSessionId);
        final int firstChildSession = childSessionIds[0];
        final CountDownLatch countDownLatch = new CountDownLatch(childSessionIds.length);
        final PackageInstaller.Session childSession = openSession(firstChildSession);
        registerSessionCallbacks(
                new AbandonSessionCallBack(countDownLatch, childSessionIds));

        InstallUtils.getPackageInstaller().abandonSession(parentSessionId);
        countDownLatch.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // TODO(b/171774482): the inconsistent behavior between staged and non-staged child session
        // The child session is cleaned up asynchronously after abandoning the parent session.
        PollingCheck.check("The result should be empty list",
                PREVENT_RACE_CONDITION_TIMEOUT_SECONDS, () -> {
                    final String[] names;
                    try {
                        names = childSession.getNames();
                    } catch (IOException e) {
                        return false;
                    }
                    return names != null && names.length == 0;
                });
    }

    @Test
    public void abandon_nonStagedParentSession_openedChildSession_canNotGetNames()
            throws Exception {
        final int parentSessionId = Install.multi(TestApp.A1).createSession();
        final int[] childSessionIds = getChildSessionIds(parentSessionId);
        final int firstChildSession = childSessionIds[0];
        final CountDownLatch countDownLatch = new CountDownLatch(childSessionIds.length);
        final PackageInstaller.Session childSession = openSession(firstChildSession);
        registerSessionCallbacks(
                new AbandonSessionCallBack(countDownLatch, childSessionIds));

        InstallUtils.getPackageInstaller().abandonSession(parentSessionId);
        countDownLatch.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // The child session is cleaned up asynchronously after abandoning the parent session.
        PollingCheck.check("getNames should get the security exception",
                PREVENT_RACE_CONDITION_TIMEOUT_SECONDS, () -> {
                    try {
                        childSession.getNames();
                    } catch (SecurityException e) {
                        if (e.getMessage().contains("getNames")) {
                            return true;
                        }
                    } catch (IOException e) {
                        return false;
                    }
                    return false;
                });
    }

    @Test
    public void abandon_stagedParentSession_openChildSessionForWriting_shouldFail()
            throws Exception {
        final int parentSessionId = Install.multi(TestApp.A1).setStaged().createSession();
        final int[] childSessionIds = getChildSessionIds(parentSessionId);
        final int firstChildSession = childSessionIds[0];
        final CountDownLatch countDownLatch = new CountDownLatch(childSessionIds.length);
        registerSessionCallbacks(
                new AbandonSessionCallBack(countDownLatch, childSessionIds));
        final OutputStream outputStream = openSessionForWrite(firstChildSession,
                mTestName.getMethodName());

        InstallUtils.getPackageInstaller().abandonSession(parentSessionId);
        countDownLatch.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThrows(instanceOf(IOException.class,
                hasMessageThat(containsString("write failed"))),
                () -> outputStream.write(PLACE_HOLDER_STRING_BYTES));
    }

    @Test
    public void abandon_nonStagedParentSession_openChildSessionForWriting_shouldFail()
            throws Exception {
        final int parentSessionId = Install.multi(TestApp.A1).createSession();
        final int[] childSessionIds = getChildSessionIds(parentSessionId);
        final int firstChildSession = childSessionIds[0];
        final CountDownLatch countDownLatch = new CountDownLatch(childSessionIds.length);
        registerSessionCallbacks(
                new AbandonSessionCallBack(countDownLatch, childSessionIds));
        final OutputStream outputStream =
                openSessionForWrite(firstChildSession, mTestName.getMethodName());

        InstallUtils.getPackageInstaller().abandonSession(parentSessionId);
        countDownLatch.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThrows(instanceOf(IOException.class,
                hasMessageThat(containsString("write failed"))),
                () -> outputStream.write(PLACE_HOLDER_STRING_BYTES));
    }

    @Test
    public void abandon_stagedParentSession_childSession_canNotOpenAgain()
            throws Exception {
        final int parentSessionId = Install.multi(TestApp.A1).setStaged().createSession();
        final int[] childSessionIds = getChildSessionIds(parentSessionId);
        final int firstChildSession = childSessionIds[0];
        final CountDownLatch countDownLatch = new CountDownLatch(childSessionIds.length);
        registerSessionCallbacks(
                new AbandonSessionCallBack(countDownLatch, childSessionIds));

        InstallUtils.getPackageInstaller().abandonSession(parentSessionId);
        countDownLatch.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThrows(instanceOf(SecurityException.class,
                hasMessageThat(containsString(String.valueOf(firstChildSession)))),
                () -> InstallUtils.getPackageInstaller().openSession(firstChildSession));
    }

    @Test
    public void abandon_nonStagedParentSession_childSession_canNotOpenAgain()
            throws Exception {
        final int parentSessionId = Install.multi(TestApp.A1).createSession();
        final int[] childSessionIds = getChildSessionIds(parentSessionId);
        final int firstChildSession = childSessionIds[0];
        final CountDownLatch countDownLatch = new CountDownLatch(childSessionIds.length);
        registerSessionCallbacks(
                new AbandonSessionCallBack(countDownLatch, childSessionIds));

        InstallUtils.getPackageInstaller().abandonSession(parentSessionId);
        countDownLatch.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThrows(instanceOf(SecurityException.class,
                hasMessageThat(containsString(String.valueOf(firstChildSession)))),
                () -> InstallUtils.getPackageInstaller().openSession(firstChildSession));
    }
}

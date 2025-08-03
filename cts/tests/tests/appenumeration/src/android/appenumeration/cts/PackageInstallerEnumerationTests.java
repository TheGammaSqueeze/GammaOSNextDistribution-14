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

package android.appenumeration.cts;

import static android.appenumeration.cts.Constants.ACTION_GET_ALL_SESSIONS;
import static android.appenumeration.cts.Constants.ACTION_GET_SESSION_INFO;
import static android.appenumeration.cts.Constants.ACTION_GET_STAGED_SESSIONS;
import static android.appenumeration.cts.Constants.QUERIES_NOTHING;
import static android.appenumeration.cts.Constants.QUERIES_NOTHING_PERM;
import static android.appenumeration.cts.Constants.QUERIES_NOTHING_Q;
import static android.appenumeration.cts.Constants.QUERIES_PACKAGE;
import static android.appenumeration.cts.Utils.adoptShellPermissions;
import static android.appenumeration.cts.Utils.cleanUpMySessions;
import static android.appenumeration.cts.Utils.dropShellPermissions;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItemInArray;
import static org.hamcrest.Matchers.not;

import android.Manifest;
import android.content.pm.PackageInstaller;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.cts.install.lib.Install;
import com.android.cts.install.lib.TestApp;

import org.hamcrest.core.IsNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;


@RunWith(AndroidJUnit4.class)
public class PackageInstallerEnumerationTests extends AppEnumerationTestsBase {

    @Before
    public void setUp() throws Exception {
        adoptShellPermissions(Manifest.permission.INSTALL_PACKAGES);
    }

    @After
    public void cleanUp() throws Exception {
        cleanUpMySessions();
        dropShellPermissions();
    }

    @Test
    public void getSessionInfo_queriesNothing_cannotSeeSession() throws Exception {
        final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A).createSession();
        final Integer[] sessionIds = getSessionInfos(ACTION_GET_SESSION_INFO, QUERIES_NOTHING,
                sessionId);
        assertThat(sessionIds, not(hasItemInArray(sessionId)));
    }

    @Test
    public void getSessionInfo_queriesNothingHasPermission_canSeeSession() throws Exception {
        final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A).createSession();
        final Integer[] sessionIds = getSessionInfos(ACTION_GET_SESSION_INFO, QUERIES_NOTHING_PERM,
                sessionId);
        assertThat(sessionIds, hasItemInArray(sessionId));
    }

    @Test
    public void getSessionInfo_queriesPackage_canSeeSession() throws Exception {
        final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A).createSession();
        final Integer[] sessionIds = getSessionInfos(ACTION_GET_SESSION_INFO, QUERIES_PACKAGE,
                sessionId);
        assertThat(sessionIds, hasItemInArray(sessionId));
    }

    @Test
    public void getSessionInfo_queriesNothingTargetsQ_canSeeSession() throws Exception {
        final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A).createSession();
        final Integer[] sessionIds = getSessionInfos(ACTION_GET_SESSION_INFO, QUERIES_NOTHING_Q,
                sessionId);
        assertThat(sessionIds, hasItemInArray(sessionId));
    }

    @Test
    public void getSessionInfo_sessionOwner_canSeeSession() throws Exception {
        final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A).createSession();
        final PackageInstaller installer = sPm.getPackageInstaller();
        final PackageInstaller.SessionInfo info = installer.getSessionInfo(sessionId);
        assertThat(info, IsNull.notNullValue());
    }

    @Test
    public void getStagedSessions_queriesNothing_cannotSeeSession() throws Exception {
        final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A).setStaged()
                .createSession();
        final Integer[] sessionIds = getSessionInfos(ACTION_GET_STAGED_SESSIONS, QUERIES_NOTHING,
                sessionId);
        assertThat(sessionIds, not(hasItemInArray(sessionId)));
    }

    @Test
    public void getStagedSessions_queriesNothingHasPermission_canSeeSession() throws Exception {
        final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A).setStaged()
                .createSession();
        final Integer[] sessionIds = getSessionInfos(ACTION_GET_STAGED_SESSIONS,
                QUERIES_NOTHING_PERM, sessionId);
        assertThat(sessionIds, hasItemInArray(sessionId));
    }

    @Test
    public void getStagedSessions_queriesPackage_canSeeSession() throws Exception {
        final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A).setStaged()
                .createSession();
        final Integer[] sessionIds = getSessionInfos(ACTION_GET_STAGED_SESSIONS, QUERIES_PACKAGE,
                sessionId);
        assertThat(sessionIds, hasItemInArray(sessionId));
    }

    @Test
    public void getStagedSessions_queriesNothingTargetsQ_canSeeSession() throws Exception {
        final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A).setStaged()
                .createSession();
        final Integer[] sessionIds = getSessionInfos(ACTION_GET_STAGED_SESSIONS, QUERIES_NOTHING_Q,
                sessionId);
        assertThat(sessionIds, hasItemInArray(sessionId));
    }

    @Test
    public void getStagedSessions_sessionOwner_canSeeSession() throws Exception {
        final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A).setStaged()
                .createSession();
        final PackageInstaller installer = sPm.getPackageInstaller();
        final Integer[] sessionIds = installer.getStagedSessions().stream()
                .map(i -> i.getSessionId())
                .distinct()
                .toArray(Integer[]::new);
        assertThat(sessionIds, hasItemInArray(sessionId));
    }

    @Test
    public void getAllSessions_queriesNothing_cannotSeeSession() throws Exception {
        final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A).createSession();
        final Integer[] sessionIds = getSessionInfos(ACTION_GET_ALL_SESSIONS, QUERIES_NOTHING,
                sessionId);
        assertThat(sessionIds, not(hasItemInArray(sessionId)));
    }

    @Test
    public void getAllSessions_queriesNothingHasPermission_canSeeSession() throws Exception {
        final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A).createSession();
        final Integer[] sessionIds = getSessionInfos(ACTION_GET_ALL_SESSIONS, QUERIES_NOTHING_PERM,
                sessionId);
        assertThat(sessionIds, hasItemInArray(sessionId));
    }

    @Test
    public void getAllSessions_queriesPackage_canSeeSession() throws Exception {
        final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A).createSession();
        final Integer[] sessionIds = getSessionInfos(ACTION_GET_ALL_SESSIONS, QUERIES_PACKAGE,
                sessionId);
        assertThat(sessionIds, hasItemInArray(sessionId));
    }

    @Test
    public void getAllSessions_queriesNothingTargetsQ_canSeeSession() throws Exception {
        final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A).createSession();
        final Integer[] sessionIds = getSessionInfos(ACTION_GET_ALL_SESSIONS, QUERIES_NOTHING_Q,
                sessionId);
        assertThat(sessionIds, hasItemInArray(sessionId));
    }

    @Test
    public void getAllSessions_sessionOwner_canSeeSession() throws Exception {
        final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A).createSession();
        final PackageInstaller installer = sPm.getPackageInstaller();
        final Integer[] sessionIds = installer.getAllSessions().stream()
                .map(i -> i.getSessionId())
                .distinct()
                .toArray(Integer[]::new);
        assertThat(sessionIds, hasItemInArray(sessionId));
    }
}

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

import static android.appenumeration.cts.Constants.ACCOUNT_NAME;
import static android.appenumeration.cts.Constants.ACCOUNT_TYPE;
import static android.appenumeration.cts.Constants.ACCOUNT_TYPE_SHARED_USER;
import static android.appenumeration.cts.Constants.ACTION_GET_IS_SYNCABLE;
import static android.appenumeration.cts.Constants.ACTION_GET_PERIODIC_SYNCS;
import static android.appenumeration.cts.Constants.ACTION_GET_SYNCADAPTER_CONTROL_PANEL;
import static android.appenumeration.cts.Constants.ACTION_GET_SYNCADAPTER_PACKAGES_FOR_AUTHORITY;
import static android.appenumeration.cts.Constants.ACTION_GET_SYNCADAPTER_TYPES;
import static android.appenumeration.cts.Constants.ACTION_GET_SYNC_AUTOMATICALLY;
import static android.appenumeration.cts.Constants.ACTION_REQUEST_PERIODIC_SYNC;
import static android.appenumeration.cts.Constants.ACTION_REQUEST_SYNC_AND_AWAIT_STATUS;
import static android.appenumeration.cts.Constants.ACTION_SET_SYNC_AUTOMATICALLY;
import static android.appenumeration.cts.Constants.AUTHORITY_SUFFIX;
import static android.appenumeration.cts.Constants.EXTRA_ACCOUNT;
import static android.appenumeration.cts.Constants.EXTRA_AUTHORITY;
import static android.appenumeration.cts.Constants.QUERIES_NOTHING;
import static android.appenumeration.cts.Constants.QUERIES_NOTHING_SHARED_USER;
import static android.appenumeration.cts.Constants.QUERIES_PACKAGE;
import static android.appenumeration.cts.Constants.SERVICE_CLASS_SYNC_ADAPTER;
import static android.appenumeration.cts.Constants.TARGET_SYNCADAPTER;
import static android.appenumeration.cts.Constants.TARGET_SYNCADAPTER_AUTHORITY;
import static android.appenumeration.cts.Constants.TARGET_SYNCADAPTER_SHARED_USER;
import static android.appenumeration.cts.Utils.allowTestApiAccess;
import static android.appenumeration.cts.Utils.resetTestApiAccess;
import static android.content.Intent.EXTRA_COMPONENT_NAME;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItemInArray;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThrows;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.content.PeriodicSync;
import android.content.SyncAdapterType;
import android.os.Bundle;
import android.os.Process;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class SyncAdapterEnumerationTests extends AppEnumerationTestsBase {

    private static AccountManager sAccountManager;

    private static final Account ACCOUNT_SYNCADAPTER = new Account(ACCOUNT_NAME, ACCOUNT_TYPE);
    private static final Account ACCOUNT_SYNCADAPTER_SHARED_USER = new Account(ACCOUNT_NAME,
            ACCOUNT_TYPE_SHARED_USER);

    @BeforeClass
    public static void setUpAccounts() {
        sAccountManager = AccountManager.get(sContext);
        assertThat(sAccountManager.addAccountExplicitly(ACCOUNT_SYNCADAPTER,
                null /* password */, null /* userdata */), is(true));
        assertThat(sAccountManager.addAccountExplicitly(ACCOUNT_SYNCADAPTER_SHARED_USER,
                null /* password */, null /* userdata */), is(true));
    }

    @AfterClass
    public static void tearDownAccounts() {
        assertThat(sAccountManager.removeAccountExplicitly(ACCOUNT_SYNCADAPTER),
                is(true));
        assertThat(sAccountManager.removeAccountExplicitly(ACCOUNT_SYNCADAPTER_SHARED_USER),
                is(true));
    }

    @Test
    public void queriesPackage_getSyncAdapterTypes_canSeeSyncAdapterTarget() throws Exception {
        assertVisible(QUERIES_PACKAGE, TARGET_SYNCADAPTER, this::getSyncAdapterTypes);
    }

    @Test
    public void queriesNothing_getSyncAdapterTypes_cannotSeeSyncAdapterTarget() throws Exception {
        assertNotVisible(QUERIES_NOTHING, TARGET_SYNCADAPTER, this::getSyncAdapterTypes);
        assertNotVisible(QUERIES_NOTHING, TARGET_SYNCADAPTER_SHARED_USER,
                this::getSyncAdapterTypes);
    }

    @Test
    public void queriesNothingSharedUser_getSyncAdapterTypes_canSeeSyncAdapterSharedUserTarget()
            throws Exception {
        assertVisible(QUERIES_NOTHING_SHARED_USER, TARGET_SYNCADAPTER_SHARED_USER,
                this::getSyncAdapterTypes);
    }

    @Test
    public void queriesPackage_getSyncAdapterPackages_canSeeSyncAdapterTarget() throws Exception {
        try {
            allowTestApiAccess(QUERIES_PACKAGE);

            assertVisible(QUERIES_PACKAGE, TARGET_SYNCADAPTER,
                    this::getSyncAdapterPackagesForAuthorityAsUser);
        } finally {
            resetTestApiAccess(QUERIES_PACKAGE);
        }
    }

    @Test
    public void queriesNothing_getSyncAdapterPackages_cannotSeeSyncAdapterTarget()
            throws Exception {
        try {
            allowTestApiAccess(QUERIES_NOTHING);

            assertNotVisible(QUERIES_NOTHING, TARGET_SYNCADAPTER,
                    this::getSyncAdapterPackagesForAuthorityAsUser);
            assertNotVisible(QUERIES_NOTHING, TARGET_SYNCADAPTER_SHARED_USER,
                    this::getSyncAdapterPackagesForAuthorityAsUser);
        } finally {
            resetTestApiAccess(QUERIES_NOTHING);
        }
    }

    @Test
    public void queriesNothingSharedUser_getSyncAdapterPackages_canSeeSyncAdapterSharedUserTarget()
            throws Exception {
        try {
            allowTestApiAccess(QUERIES_NOTHING_SHARED_USER);

            assertVisible(QUERIES_NOTHING_SHARED_USER, TARGET_SYNCADAPTER_SHARED_USER,
                    this::getSyncAdapterPackagesForAuthorityAsUser);
        } finally {
            resetTestApiAccess(QUERIES_NOTHING_SHARED_USER);
        }
    }

    @Test
    public void queriesPackage_requestSync_canSeeSyncAdapterTarget() throws Exception {
        assertThat(
                requestSyncAndAwaitStatus(QUERIES_PACKAGE, ACCOUNT_SYNCADAPTER, TARGET_SYNCADAPTER),
                is(true));
    }

    @Test
    public void queriesNothingSharedUser_requestSync_canSeeSyncAdapterSharedUserTarget()
            throws Exception {
        assertThat(requestSyncAndAwaitStatus(QUERIES_NOTHING_SHARED_USER,
                ACCOUNT_SYNCADAPTER_SHARED_USER, TARGET_SYNCADAPTER_SHARED_USER), is(true));
    }

    @Test
    public void queriesNothing_requestSync_cannotSeeSyncAdapterTarget() {
        assertThrows(MissingCallbackException.class,
                () -> requestSyncAndAwaitStatus(QUERIES_NOTHING, ACCOUNT_SYNCADAPTER,
                        TARGET_SYNCADAPTER));
        assertThrows(MissingCallbackException.class,
                () -> requestSyncAndAwaitStatus(QUERIES_NOTHING, ACCOUNT_SYNCADAPTER_SHARED_USER,
                        TARGET_SYNCADAPTER_SHARED_USER));
    }

    @Test
    public void queriesPackage_getRunningServiceControlPanel_canSeeSyncAdapterTarget()
            throws Exception {
        assertThat(getSyncAdapterControlPanel(QUERIES_PACKAGE, ACCOUNT_SYNCADAPTER,
                TARGET_SYNCADAPTER), notNullValue());
    }

    @Test
    public void queriesNothing_getRunningServiceControlPanel_cannotSeeSyncAdapterTarget()
            throws Exception {
        assertThat(getSyncAdapterControlPanel(QUERIES_NOTHING, ACCOUNT_SYNCADAPTER,
                TARGET_SYNCADAPTER), nullValue());
    }

    @Test
    public void queriesNothing_getIsSyncable_cannotSeeSyncAdapterTarget() throws Exception {
        assertThat(getIsSyncable(QUERIES_NOTHING, ACCOUNT_SYNCADAPTER, TARGET_SYNCADAPTER),
                is(0));
    }

    @Test
    public void queriesPackage_getIsSyncable_canSeeSyncAdapterTarget() throws Exception {
        assertThat(getIsSyncable(QUERIES_PACKAGE, ACCOUNT_SYNCADAPTER, TARGET_SYNCADAPTER),
                not(is(0)));
    }

    @Test
    public void queriesNothing_getSyncAutomatically_cannotSeeSyncAdapterTarget() throws Exception {
        setSyncAutomatically(TARGET_SYNCADAPTER, ACCOUNT_SYNCADAPTER);
        assertThat(getSyncAutomatically(QUERIES_NOTHING, TARGET_SYNCADAPTER), is(false));
    }

    @Test
    public void queriesPackage_getSyncAutomatically_canSeeSyncAdapterTarget() throws Exception {
        setSyncAutomatically(TARGET_SYNCADAPTER, ACCOUNT_SYNCADAPTER);
        assertThat(getSyncAutomatically(QUERIES_PACKAGE, TARGET_SYNCADAPTER), is(true));
    }

    @Test
    public void queriesNothing_getPeriodicSyncs_cannotSeeSyncAdapterTarget() throws Exception {
        assertThat(requestPeriodicSync(TARGET_SYNCADAPTER, ACCOUNT_SYNCADAPTER), is(true));
        assertThat(getPeriodicSyncs(QUERIES_NOTHING, ACCOUNT_SYNCADAPTER, TARGET_SYNCADAPTER),
                not(hasItemInArray(TARGET_SYNCADAPTER_AUTHORITY)));
    }

    @Test
    public void queriesPackage_getPeriodicSyncs_canSeeSyncAdapterTarget() throws Exception {
        assertThat(requestPeriodicSync(TARGET_SYNCADAPTER, ACCOUNT_SYNCADAPTER), is(true));
        assertThat(getPeriodicSyncs(QUERIES_PACKAGE, ACCOUNT_SYNCADAPTER, TARGET_SYNCADAPTER),
                hasItemInArray(TARGET_SYNCADAPTER_AUTHORITY));
    }


    private String[] getSyncAdapterTypes(String sourcePackageName) throws Exception {
        final Bundle response = sendCommandBlocking(sourcePackageName, null /* targetPackageName */,
                /* intentExtra */ null, ACTION_GET_SYNCADAPTER_TYPES);
        final List<SyncAdapterType> types = response.getParcelableArrayList(
                Intent.EXTRA_RETURN_RESULT, SyncAdapterType.class);
        return types.stream()
                .map(type -> type.getPackageName())
                .distinct()
                .toArray(String[]::new);
    }

    private String[] getSyncAdapterPackagesForAuthorityAsUser(String sourcePackageName,
            String targetPackageName) throws Exception {
        final Bundle extraData = new Bundle();
        extraData.putString(EXTRA_AUTHORITY, targetPackageName + AUTHORITY_SUFFIX);
        extraData.putInt(Intent.EXTRA_USER, Process.myUserHandle().getIdentifier());
        final Bundle response = sendCommandBlocking(sourcePackageName, null /* targetPackageName */,
                extraData, ACTION_GET_SYNCADAPTER_PACKAGES_FOR_AUTHORITY);
        return response.getStringArray(Intent.EXTRA_PACKAGES);
    }

    private boolean requestSyncAndAwaitStatus(String sourcePackageName, Account account,
            String targetPackageName) throws Exception {
        final Bundle extraData = new Bundle();
        extraData.putParcelable(EXTRA_ACCOUNT, account);
        extraData.putString(EXTRA_AUTHORITY, targetPackageName + AUTHORITY_SUFFIX);
        final Bundle response = sendCommandBlocking(sourcePackageName, null /* targetPackageName */,
                extraData, ACTION_REQUEST_SYNC_AND_AWAIT_STATUS);
        return response.getBoolean(Intent.EXTRA_RETURN_RESULT);
    }

    private PendingIntent getSyncAdapterControlPanel(String sourcePackageName, Account account,
            String targetPackageName) throws Exception {
        final ComponentName componentName = new ComponentName(
                targetPackageName, SERVICE_CLASS_SYNC_ADAPTER);
        final Bundle extraData = new Bundle();
        extraData.putParcelable(EXTRA_ACCOUNT, account);
        extraData.putString(EXTRA_AUTHORITY, targetPackageName + AUTHORITY_SUFFIX);
        extraData.putParcelable(EXTRA_COMPONENT_NAME, componentName);
        final Bundle response = sendCommandBlocking(sourcePackageName, null /* targetPackageName */,
                extraData, ACTION_GET_SYNCADAPTER_CONTROL_PANEL);
        return response.getParcelable(Intent.EXTRA_RETURN_RESULT, PendingIntent.class);
    }

    private boolean requestPeriodicSync(String providerPackageName, Account account)
            throws Exception {
        final String authority = providerPackageName + AUTHORITY_SUFFIX;
        final Bundle extraData = new Bundle();
        extraData.putParcelable(EXTRA_ACCOUNT, account);
        extraData.putString(EXTRA_AUTHORITY, authority);
        final Bundle response = sendCommandBlocking(providerPackageName,
                null /* targetPackageName */, extraData, ACTION_REQUEST_PERIODIC_SYNC);
        return response.getBoolean(Intent.EXTRA_RETURN_RESULT);
    }

    private void setSyncAutomatically(String providerPackageName, Account account)
            throws Exception {
        final String authority = providerPackageName + AUTHORITY_SUFFIX;
        final Bundle extraData = new Bundle();
        extraData.putParcelable(EXTRA_ACCOUNT, account);
        extraData.putString(EXTRA_AUTHORITY, authority);
        sendCommandBlocking(providerPackageName, null /* targetPackageName */, extraData,
                ACTION_SET_SYNC_AUTOMATICALLY);
    }

    private boolean getSyncAutomatically(String sourcePackageName, String targetPackageName)
            throws Exception {
        final String authority = targetPackageName + AUTHORITY_SUFFIX;
        final Bundle extraData = new Bundle();
        extraData.putString(EXTRA_AUTHORITY, authority);
        final Bundle response = sendCommandBlocking(sourcePackageName, null /* targetPackageName */,
                extraData, ACTION_GET_SYNC_AUTOMATICALLY);
        return response.getBoolean(Intent.EXTRA_RETURN_RESULT);
    }

    private int getIsSyncable(String sourcePackageName, Account account, String targetPackageName)
            throws Exception {
        final String authority = targetPackageName + AUTHORITY_SUFFIX;
        final Bundle extraData = new Bundle();
        extraData.putParcelable(EXTRA_ACCOUNT, account);
        extraData.putString(EXTRA_AUTHORITY, authority);
        final Bundle response = sendCommandBlocking(sourcePackageName, null /* targetPackageName */,
                extraData, ACTION_GET_IS_SYNCABLE);
        return response.getInt(Intent.EXTRA_RETURN_RESULT);
    }

    private String[] getPeriodicSyncs(String sourcePackageName, Account account,
            String targetPackageName) throws Exception {
        final String authority = targetPackageName + AUTHORITY_SUFFIX;
        final Bundle extraData = new Bundle();
        extraData.putParcelable(EXTRA_ACCOUNT, account);
        extraData.putString(EXTRA_AUTHORITY, authority);
        final Bundle response = sendCommandBlocking(sourcePackageName, null /* targetPackageName */,
                extraData, ACTION_GET_PERIODIC_SYNCS);
        final List<PeriodicSync> list = response.getParcelableArrayList(Intent.EXTRA_RETURN_RESULT,
                PeriodicSync.class);
        return list.stream()
                .map(sync -> sync.authority)
                .distinct()
                .toArray(String[]::new);
    }
}

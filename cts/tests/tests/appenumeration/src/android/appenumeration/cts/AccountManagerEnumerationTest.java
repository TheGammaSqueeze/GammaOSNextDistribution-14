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
import static android.appenumeration.cts.Constants.ACTION_ACCOUNT_MANAGER_GET_AUTHENTICATOR_TYPES;
import static android.appenumeration.cts.Constants.QUERIES_NOTHING;
import static android.appenumeration.cts.Constants.QUERIES_PACKAGE;
import static android.appenumeration.cts.Constants.TARGET_STUB;
import static android.appenumeration.cts.Constants.TARGET_STUB_APK;
import static android.appenumeration.cts.Constants.TARGET_SYNCADAPTER;
import static android.appenumeration.cts.Constants.TARGET_WEB;
import static android.appenumeration.cts.Utils.ensurePackageIsInstalled;
import static android.content.Intent.EXTRA_RETURN_RESULT;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsMapContaining.hasEntry;
import static org.hamcrest.collection.IsMapWithSize.anEmptyMap;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThrows;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class AccountManagerEnumerationTest extends AppEnumerationTestsBase {
    private static final Account ACCOUNT = new Account(ACCOUNT_NAME, ACCOUNT_TYPE);

    private static final String TARGET_VISIBLE = TARGET_STUB;
    private static final String TARGET_NOT_VISIBLE = TARGET_WEB;

    private AccountManager mAccountManager;

    @BeforeClass
    public static void prepareApps() {
        ensurePackageIsInstalled(TARGET_STUB, TARGET_STUB_APK);
    }

    @Before
    public void onBefore() throws Exception {
        mAccountManager = AccountManager.get(sContext);

        assertThat(sPm.canPackageQuery(sContext.getPackageName(), TARGET_VISIBLE),
                is(true));
        assertThrows(PackageManager.NameNotFoundException.class,
                () -> sPm.canPackageQuery(sContext.getPackageName(), TARGET_NOT_VISIBLE));
    }

    @After
    public void onAfter() {
        for (Account account : mAccountManager.getAccountsByType(ACCOUNT_TYPE)) {
            mAccountManager.removeAccountExplicitly(account);
        }
    }

    @Test
    public void addAccountExplicitlyWithVisibility_targetPackageIsVisible() {
        final Map<String, Integer> visibility = new HashMap();
        visibility.put(TARGET_VISIBLE, AccountManager.VISIBILITY_VISIBLE);
        assertThat(mAccountManager.addAccountExplicitly(
                ACCOUNT, null /* password */, null /* userdata */, visibility), is(true));

        assertThat(mAccountManager.getAccountVisibility(ACCOUNT, TARGET_VISIBLE),
                is(AccountManager.VISIBILITY_VISIBLE));
        assertThat(mAccountManager.getAccountsAndVisibilityForPackage(TARGET_VISIBLE, ACCOUNT_TYPE),
                hasEntry(ACCOUNT, AccountManager.VISIBILITY_VISIBLE));
    }

    @Test
    public void addAccountExplicitlyWithVisibility_targetPackageNotVisible() {
        final Map<String, Integer> visibility = new HashMap();
        visibility.put(TARGET_NOT_VISIBLE, AccountManager.VISIBILITY_VISIBLE);
        assertThat(mAccountManager.addAccountExplicitly(
                ACCOUNT, null /* password */, null /* userdata */, visibility), is(true));

        assertThat(mAccountManager.getAccountVisibility(ACCOUNT, TARGET_NOT_VISIBLE),
                is(AccountManager.VISIBILITY_NOT_VISIBLE));
        assertThat(mAccountManager.getAccountsAndVisibilityForPackage(
                TARGET_NOT_VISIBLE, ACCOUNT_TYPE), anEmptyMap());
    }

    @Test
    public void setAccountVisibility_targetPackageIsVisible() {
        assertThat(mAccountManager.addAccountExplicitly(
                ACCOUNT, null /* password */, null /* userdata */), is(true));
        assertThat(mAccountManager.setAccountVisibility(
                ACCOUNT, TARGET_VISIBLE, AccountManager.VISIBILITY_VISIBLE), is(true));
    }

    @Test
    public void setAccountVisibility_targetPackageNotVisible() {
        assertThat(mAccountManager.addAccountExplicitly(
                ACCOUNT, null /* password */, null /* userdata */), is(true));
        assertThat(mAccountManager.setAccountVisibility(
                ACCOUNT, TARGET_NOT_VISIBLE, AccountManager.VISIBILITY_VISIBLE), is(false));
    }

    @Test
    public void queriesPackage_getAuthenticatorTypes_canSeeSyncAdapterTarget()
            throws Exception {
        assertVisible(QUERIES_PACKAGE, TARGET_SYNCADAPTER, this::getAuthenticatorTypes);
    }

    @Test
    public void queriesNothing_getAuthenticatorTypes_cannotSeeSyncAdapterTarget()
            throws Exception {
        assertNotVisible(QUERIES_NOTHING, TARGET_SYNCADAPTER, this::getAuthenticatorTypes);
    }

    private String[] getAuthenticatorTypes(String sourcePackageName) throws Exception {
        final Bundle response = sendCommandBlocking(sourcePackageName, null /* targetPackageName */,
                /* intentExtra */ null, ACTION_ACCOUNT_MANAGER_GET_AUTHENTICATOR_TYPES);
        final AuthenticatorDescription[] types =
                response.getParcelableArray(EXTRA_RETURN_RESULT, AuthenticatorDescription.class);
        return Arrays.stream(types)
                .map(type -> type.packageName)
                .distinct()
                .toArray(String[]::new);
    }
}

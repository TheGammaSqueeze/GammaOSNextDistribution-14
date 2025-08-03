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

package android.provider.cts.contacts;

import static com.google.common.truth.Truth.assertThat;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.provider.ContactsContract.SimAccount;
import android.provider.ContactsContract.SimContacts;
import android.test.AndroidTestCase;

import androidx.test.filters.MediumTest;

import com.android.compatibility.common.util.SystemUtil;
import com.android.compatibility.common.util.TestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@MediumTest
public class ContactsContract_SimContactTest extends AndroidTestCase {
    private static final int ASYNC_TIMEOUT_LIMIT_SEC = 60;

    // Using unique account name and types because these tests may break or be broken by
    // other tests running.  No other tests should use the following accounts.
    private static final String SIM_ACCT_NAME_1 = "test sim acct name 1";
    private static final String SIM_ACCT_TYPE_1 = "test sim acct type 1";
    private static final String SIM_ACCT_NAME_2 = "test sim acct name 2";
    private static final String SIM_ACCT_TYPE_2 = "test sim acct type 2";

    private static final int SIM_SLOT_0 = 0;
    private static final int SIM_SLOT_1 = 1;

    private ContentResolver mResolver;
    private List<Intent> mReceivedIntents;
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mReceivedIntents.add(intent);
        }
    };

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mResolver = getContext().getContentResolver();
        mReceivedIntents = Collections.synchronizedList(new ArrayList<>());
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        // Reset SIM accounts
        SystemUtil.runWithShellPermissionIdentity(() -> {
            SimContacts.removeSimAccounts(mResolver, SIM_SLOT_0);
            SimContacts.removeSimAccounts(mResolver, SIM_SLOT_1);
        });
    }

    /**
     * SIM accounts added through
     * {@link SimContacts#addSimAccount(ContentResolver, String, String, int, int)} should be
     * returned by {@link SimContacts#getSimAccounts(ContentResolver)}
     */
    public void testAddSimAccount_returnedByGetSimAccounts() {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            SimContacts.addSimAccount(mResolver, SIM_ACCT_NAME_1, SIM_ACCT_TYPE_1, SIM_SLOT_0,
                    SimAccount.ADN_EF_TYPE);
            SimContacts.addSimAccount(mResolver, SIM_ACCT_NAME_2, SIM_ACCT_TYPE_2, SIM_SLOT_1,
                    SimAccount.ADN_EF_TYPE);
        });

        List<SimAccount> simAccounts = SimContacts.getSimAccounts(mResolver);

        assertThat(simAccounts).hasSize(2);
        SimAccount simAccount1 = simAccounts.get(0);

        assertThat(SIM_ACCT_NAME_1).isEqualTo(simAccount1.getAccountName());
        assertThat(SIM_ACCT_TYPE_1).isEqualTo(simAccount1.getAccountType());
        assertThat(SIM_SLOT_0).isEqualTo(simAccount1.getSimSlotIndex());
        assertThat(SimAccount.ADN_EF_TYPE).isEqualTo(simAccount1.getEfType());

        SimAccount simAccount2 = simAccounts.get(1);

        assertThat(SIM_ACCT_NAME_2).isEqualTo(simAccount2.getAccountName());
        assertThat(SIM_ACCT_TYPE_2).isEqualTo(simAccount2.getAccountType());
        assertThat(SIM_SLOT_1).isEqualTo(simAccount2.getSimSlotIndex());
        assertThat(SimAccount.ADN_EF_TYPE).isEqualTo(simAccount2.getEfType());
    }

    /**
     * When a SIM account is added, {@link SimContacts#ACTION_SIM_ACCOUNTS_CHANGED} should be
     * broadcast.
     */
    public void testAddSimAccount_broadcastsChange() throws Exception {
        getContext().registerReceiver(mBroadcastReceiver,
                new IntentFilter(SimContacts.ACTION_SIM_ACCOUNTS_CHANGED));

        SystemUtil.runWithShellPermissionIdentity(() -> {
            SimContacts.addSimAccount(mResolver, SIM_ACCT_NAME_1, SIM_ACCT_TYPE_1, SIM_SLOT_0,
                    SimAccount.ADN_EF_TYPE);
        });

        TestUtils.waitUntil("Broadcast has not been received in time", ASYNC_TIMEOUT_LIMIT_SEC,
                () -> mReceivedIntents.size() == 1);
        Intent receivedIntent = mReceivedIntents.get(0);
        assertThat(SimContacts.ACTION_SIM_ACCOUNTS_CHANGED).isEqualTo(receivedIntent.getAction());
    }

    /**
     * When a SIM account is removed, {@link SimContacts#ACTION_SIM_ACCOUNTS_CHANGED} should be
     * broadcast.
     */
    public void testRemoveSimAccount_broadcastsChange() throws Exception {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            SimContacts.addSimAccount(mResolver, SIM_ACCT_NAME_1, SIM_ACCT_TYPE_1, SIM_SLOT_0,
                    SimAccount.ADN_EF_TYPE);
        });
        getContext().registerReceiver(mBroadcastReceiver,
                new IntentFilter(SimContacts.ACTION_SIM_ACCOUNTS_CHANGED));

        SystemUtil.runWithShellPermissionIdentity(() -> {
            SimContacts.removeSimAccounts(mResolver, SIM_SLOT_0);
        });

        TestUtils.waitUntil("Broadcast has not been received in time", ASYNC_TIMEOUT_LIMIT_SEC,
                () -> mReceivedIntents.size() == 1);
        Intent receivedIntent = mReceivedIntents.get(0);
        assertThat(SimContacts.ACTION_SIM_ACCOUNTS_CHANGED).isEqualTo(receivedIntent.getAction());
    }
}

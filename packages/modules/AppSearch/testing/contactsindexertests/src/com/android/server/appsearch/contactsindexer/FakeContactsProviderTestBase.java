/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.appsearch.contactsindexer;

import android.annotation.NonNull;
import android.app.job.JobScheduler;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ProviderInfo;
import android.os.UserHandle;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;

import javax.annotation.Nullable;

/**
 * Test base that sets up a test context that directs CP2 queries to a {@link MockContentResolver}
 * and {@link FakeContactsProvider}. Note that notifications are not broadcast, so Contacts Indexer
 * updates will not occur automatically in response to inserts and deletes.
 *
 * <p>The test context also allows setting a {@link JobScheduler}.
 */
abstract class FakeContactsProviderTestBase {
    protected TestContext mContext;
    protected FakeContactsProvider mFakeContactsProvider;
    protected MockContentResolver mMockContentResolver;

    @Before
    public void setUp() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        mContext = new TestContext(context);
        mMockContentResolver = new MockContentResolver(context);
        mFakeContactsProvider = new FakeContactsProvider();
        ProviderInfo providerInfo = new ProviderInfo();
        providerInfo.authority = FakeContactsProvider.AUTHORITY;
        MockContentProvider.attachInfoForTesting(mFakeContactsProvider, context, providerInfo);
        mMockContentResolver.addProvider(FakeContactsProvider.AUTHORITY,
                mFakeContactsProvider);
    }

    @After
    public void tearDown() throws Exception {
        mFakeContactsProvider.shutdown();
    }

    class TestContext extends ContextWrapper {
        @Nullable
        JobScheduler mJobScheduler;

        TestContext(Context base) {
            super(base);
        }

        @Override
        public ContentResolver getContentResolver() {
            return mMockContentResolver;
        }

        @Override
        @Nullable
        public Object getSystemService(String name) {
            if (mJobScheduler != null && Context.JOB_SCHEDULER_SERVICE.equals(name)) {
                return mJobScheduler;
            }
            return getBaseContext().getSystemService(name);
        }

        public void setJobScheduler(@Nullable JobScheduler jobScheduler) {
            mJobScheduler = jobScheduler;
        }

        @Override
        public Context getApplicationContext() {
            return this;
        }

        @Override
        @NonNull
        public Context createContextAsUser(UserHandle user, int flags) {
            return this;
        }
    }
}

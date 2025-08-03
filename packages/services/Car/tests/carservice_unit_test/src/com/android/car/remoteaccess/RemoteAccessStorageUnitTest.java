/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.car.remoteaccess;

import static com.android.car.remoteaccess.RemoteAccessStorage.RemoteAccessDbHelper.DATABASE_NAME;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.Mockito.when;

import android.content.Context;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import com.android.car.remoteaccess.RemoteAccessStorage.ClientIdEntry;
import com.android.car.systeminterface.SystemInterface;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public final class RemoteAccessStorageUnitTest {

    private static final String TAG = RemoteAccessStorageUnitTest.class.getSimpleName();
    private static final String KEY_ALIAS_REMOTE_ACCESS_STORAGE_UNIT_TEST =
            "KEY_ALIAS_REMOTE_ACCESS_STORAGE_UNIT_TEST";

    @Mock
    private SystemInterface mSystemInterface;

    private Context mContext;
    private RemoteAccessStorage mRemoteAccessStorage;
    private File mDatabaseFile;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getTargetContext().createDeviceProtectedStorageContext();
        mDatabaseFile = mContext.getDatabasePath(DATABASE_NAME);
        when(mSystemInterface.getSystemCarDir()).thenReturn(mDatabaseFile.getParentFile());
        mRemoteAccessStorage = new RemoteAccessStorage(mContext, mSystemInterface,
                /* inMemoryStorage= */ true);
        RemoteAccessStorage.setKeyAlias(KEY_ALIAS_REMOTE_ACCESS_STORAGE_UNIT_TEST);
    }

    @After
    public void tearDown() {
        mRemoteAccessStorage.release();
        if (!mDatabaseFile.delete()) {
            Log.e(TAG, "Failed to delete the database file: " + mDatabaseFile.getAbsolutePath());
        }
    }

    @Test
    public void testUpdateClientId() {
        assertWithMessage("Return value from updateClientId")
                .that(mRemoteAccessStorage.updateClientId(new ClientIdEntry("client_id", 1234,
                        "we.are.the.world"))).isTrue();
    }

    @Test
    public void testUpdateClientId_entryModified() {
        ClientIdEntry inputEntryOne = new ClientIdEntry("client_id", 1234, "we.are.the.world");
        ClientIdEntry inputEntryTwo = new ClientIdEntry("new_client_id", 9876, "we.are.the.world");
        mRemoteAccessStorage.updateClientId(inputEntryOne);
        mRemoteAccessStorage.updateClientId(inputEntryTwo);

        ClientIdEntry outputEntry = mRemoteAccessStorage.getClientIdEntry("we.are.the.world");

        assertWithMessage("Client ID entry").that(outputEntry).isEqualTo(inputEntryTwo);
    }

    @Test
    public void testGetClientIdEntry() {
        ClientIdEntry inputEntryOne = new ClientIdEntry("client_id_1", 1234, "we.are.the.world");
        ClientIdEntry inputEntryTwo = new ClientIdEntry("client_id_2", 9876, "life.is.beautiful");
        mRemoteAccessStorage.updateClientId(inputEntryOne);
        mRemoteAccessStorage.updateClientId(inputEntryTwo);

        ClientIdEntry outputEntry = mRemoteAccessStorage.getClientIdEntry("we.are.the.world");

        assertWithMessage("Client ID entry").that(outputEntry).isEqualTo(inputEntryOne);
    }

    @Test
    public void testGetClientIdEntry_maxIdCreationTime() {
        ClientIdEntry inputEntry = new ClientIdEntry("client_id", Long.MAX_VALUE,
                "we.are.the.world");
        mRemoteAccessStorage.updateClientId(inputEntry);

        ClientIdEntry outputEntry = mRemoteAccessStorage.getClientIdEntry("we.are.the.world");

        assertWithMessage("Client ID entry").that(outputEntry).isEqualTo(inputEntry);
    }

    @Test
    public void testGetClientIdEntry_noEntry() {
        assertWithMessage("Client ID entry")
                .that(mRemoteAccessStorage.getClientIdEntry("we.are.the.world")).isNull();
    }

    @Test
    public void testGetClietIdEntries() {
        ClientIdEntry inputEntryOne = new ClientIdEntry("client_id_1", 1234, "we.are.the.world");
        ClientIdEntry inputEntryTwo = new ClientIdEntry("client_id_2", 9876, "life.is.beautiful");
        mRemoteAccessStorage.updateClientId(inputEntryOne);
        mRemoteAccessStorage.updateClientId(inputEntryTwo);

        List<ClientIdEntry> entries = mRemoteAccessStorage.getClientIdEntries();

        assertWithMessage("Client ID entries").that(entries)
                .containsExactly(inputEntryOne, inputEntryTwo);
    }
}

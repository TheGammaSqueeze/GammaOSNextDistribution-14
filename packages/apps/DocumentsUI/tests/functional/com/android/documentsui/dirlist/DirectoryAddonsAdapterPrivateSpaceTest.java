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

package com.android.documentsui.dirlist;

import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.UserProperties;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.DocumentsContract;
import android.test.AndroidTestCase;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.filters.SdkSuppress;

import com.android.documentsui.ActionHandler;
import com.android.documentsui.ModelId;
import com.android.documentsui.TestConfigStore;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.base.UserId;
import com.android.documentsui.testing.TestActionHandler;
import com.android.documentsui.testing.TestEnv;
import com.android.documentsui.testing.TestFileTypeLookup;
import com.android.documentsui.testing.TestProvidersAccess;
import com.android.documentsui.testing.UserManagers;
import com.android.documentsui.util.VersionUtils;

import java.util.HashMap;
import java.util.Map;

@SdkSuppress(minSdkVersion = 35, codeName = "V")
public class DirectoryAddonsAdapterPrivateSpaceTest extends AndroidTestCase {

    private static final String AUTHORITY = "test_authority";
    private static final UserId CURRENT_USER = TestProvidersAccess.USER_ID;
    private static final UserId SELECTED_USER = TestProvidersAccess.OtherUser.USER_ID;

    private TestEnv mEnv;
    private DirectoryAddonsAdapter mAdapter;
    private ActionHandler mActionHandler;
    private TestConfigStore mTestConfigStore;
    private UserManager mUserManager;

    /**
     * set up for the test assuming that private space is enabled.
     */
    public void setUp() {

        mEnv = TestEnv.create(AUTHORITY);
        mActionHandler = new TestActionHandler();
        mEnv.clear();
        mTestConfigStore = new TestConfigStore();
        mTestConfigStore.enablePrivateSpaceInPhotoPicker();
        mUserManager = UserManagers.create();

        UserProperties selectedUserProperties = new UserProperties.Builder().setShowInQuietMode(
                UserProperties.SHOW_IN_QUIET_MODE_HIDDEN).build();
        when(mUserManager.getUserProperties(
                UserHandle.of(SELECTED_USER.getIdentifier()))).thenReturn(selectedUserProperties);

        final Context testContext = TestContext.createStorageTestContext(getContext(), AUTHORITY);
        DocumentsAdapter.Environment env = new TestEnvironment(testContext, mEnv, mActionHandler);
        Map<UserId, String> userIdToLabelMap = new HashMap<>();
        userIdToLabelMap.put(CURRENT_USER, "Personal");
        userIdToLabelMap.put(SELECTED_USER, "Profile");

        mAdapter = new DirectoryAddonsAdapter(
                env,
                new ModelBackedDocumentsAdapter(
                        env,
                        new IconHelper(testContext, State.MODE_GRID, /* maybeShowBadge= */ false,
                                mTestConfigStore),
                        new TestFileTypeLookup(), mTestConfigStore),
                CURRENT_USER, SELECTED_USER, userIdToLabelMap, mUserManager, mTestConfigStore);

        mEnv.model.addUpdateListener(mAdapter.getModelUpdateListener());
    }

    // Tests that the item count is correct for a directory containing files and subdirs.
    public void testItemCount_mixed() {
        mEnv.reset();  // creates a mix of folders and files for us.

        assertEquals(mEnv.model.getItemCount() + 1, mAdapter.getItemCount());
    }

    public void testGetPosition() {
        mEnv.model.createFolder("a");  // id will be "1"...derived from insert position.
        mEnv.model.createFile("b");  // id will be "2"
        mEnv.model.update();

        assertEquals(0, mAdapter.getPosition(ModelId.build(mEnv.model.mUserId, AUTHORITY, "1")));
        // adapter inserts a view between item 0 and 1 to force layout
        // break between folders and files. This is reflected by an offset position.
        assertEquals(2, mAdapter.getPosition(ModelId.build(mEnv.model.mUserId, AUTHORITY, "2")));
    }

    // Tests that the item count is correct for a directory containing only subdirs.
    public void testItemCount_allDirs() {
        String[] names = {"Trader Joe's", "Alphabeta", "Lucky", "Vons", "Gelson's"};
        for (String name : names) {
            mEnv.model.createFolder(name);
        }
        mEnv.model.update();
        assertEquals(mEnv.model.getItemCount(), mAdapter.getItemCount());
    }

    // Tests that the item count is correct for a directory containing only files.
    public void testItemCount_allFiles() {
        String[] names = {"123.txt", "234.jpg", "abc.pdf"};
        for (String name : names) {
            mEnv.model.createFile(name);
        }
        mEnv.model.update();
        assertEquals(mEnv.model.getItemCount(), mAdapter.getItemCount());
    }

    public void testAddsInfoMessage_WithDirectoryChildren() {
        String[] names = {"123.txt", "234.jpg", "abc.pdf"};
        for (String name : names) {
            mEnv.model.createFile(name);
        }
        Bundle bundle = new Bundle();
        bundle.putString(DocumentsContract.EXTRA_INFO, "some info");
        mEnv.model.setCursorExtras(bundle);
        mEnv.model.update();
        assertEquals(mEnv.model.getItemCount() + 1, mAdapter.getItemCount());
        assertHolderType(0, DocumentsAdapter.ITEM_TYPE_HEADER_MESSAGE);
    }

    public void testItemCount_none() {
        mEnv.model.update();
        assertEquals(1, mAdapter.getItemCount());
        assertHolderType(0, DocumentsAdapter.ITEM_TYPE_INFLATED_MESSAGE);
    }

    public void testAddsInfoMessage_WithNoItem() {
        Bundle bundle = new Bundle();
        bundle.putString(DocumentsContract.EXTRA_INFO, "some info");
        mEnv.model.setCursorExtras(bundle);

        mEnv.model.update();
        assertEquals(2, mAdapter.getItemCount());
        assertHolderType(0, DocumentsAdapter.ITEM_TYPE_HEADER_MESSAGE);
    }

    public void testAddsErrorMessage_WithNoItem() {
        Bundle bundle = new Bundle();
        bundle.putString(DocumentsContract.EXTRA_ERROR, "some error");
        mEnv.model.setCursorExtras(bundle);

        mEnv.model.update();
        assertEquals(2, mAdapter.getItemCount());
        assertHolderType(0, DocumentsAdapter.ITEM_TYPE_HEADER_MESSAGE);
    }

    public void testOpenTreeMessage_shouldBlockChild() {
        if (!VersionUtils.isAtLeastR()) {
            return;
        }

        mEnv.state.action = State.ACTION_OPEN_TREE;
        mEnv.state.restrictScopeStorage = true;
        DocumentInfo info = new DocumentInfo();
        info.flags += DocumentsContract.Document.FLAG_DIR_BLOCKS_OPEN_DOCUMENT_TREE;
        mEnv.state.stack.push(info);

        mEnv.model.update();
        assertEquals(2, mAdapter.getItemCount());
        assertHolderType(0, DocumentsAdapter.ITEM_TYPE_HEADER_MESSAGE);
    }

    public void testOpenTreeMessage_normalChild() {
        mEnv.state.action = State.ACTION_OPEN_TREE;
        DocumentInfo info = new DocumentInfo();
        mEnv.state.stack.push(info);

        mEnv.model.update();
        // Should only no items message show
        assertEquals(1, mAdapter.getItemCount());
        assertHolderType(0, DocumentsAdapter.ITEM_TYPE_INFLATED_MESSAGE);
    }

    public void testOpenTreeMessage_restrictStorageAccessFalse_blockTreeChild() {
        if (!VersionUtils.isAtLeastR()) {
            return;
        }

        mEnv.state.action = State.ACTION_OPEN_TREE;
        DocumentInfo info = new DocumentInfo();
        info.flags += DocumentsContract.Document.FLAG_DIR_BLOCKS_OPEN_DOCUMENT_TREE;
        mEnv.state.stack.push(info);

        mEnv.model.update();
        // Should only no items message show
        assertEquals(1, mAdapter.getItemCount());
        assertHolderType(0, DocumentsAdapter.ITEM_TYPE_INFLATED_MESSAGE);
    }

    private void assertHolderType(int index, int type) {
        assertTrue(mAdapter.getItemViewType(index) == type);
    }

    private static class StubAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @Override
        public int getItemCount() {
            return 0;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return null;
        }
    }
}

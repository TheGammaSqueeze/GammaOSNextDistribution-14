/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.bluetooth.avrcpcontroller;

import static com.google.common.truth.Truth.assertThat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

import com.android.bluetooth.avrcpcontroller.BrowseTree.BrowseNode;

import org.junit.Before;
import org.junit.Test;

import java.util.Set;

public class BrowseTreeTest {
    private static final String ILLEGAL_ID = "illegal_id";
    private static final String TEST_HANDLE = "test_handle";
    private static final String TEST_NODE_ID = "test_node_id";

    private final byte[] mTestAddress = new byte[]{01, 01, 01, 01, 01, 01};
    private BluetoothAdapter mAdapter;
    private BluetoothDevice mTestDevice = null;

    @Before
    public void setUp() {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mTestDevice = mAdapter.getRemoteDevice(mTestAddress);
    }

    @Test
    public void constructor_withoutDevice() {
        BrowseTree browseTree = new BrowseTree(null);

        assertThat(browseTree.mRootNode.mItem.getDevice()).isEqualTo(null);
    }

    @Test
    public void constructor_withDevice() {
        BrowseTree browseTree = new BrowseTree(mTestDevice);

        assertThat(browseTree.mRootNode.mItem.getDevice()).isEqualTo(mTestDevice);
    }

    @Test
    public void clear() {
        BrowseTree browseTree = new BrowseTree(mTestDevice);

        browseTree.clear();

        assertThat(browseTree.mBrowseMap).isEmpty();
    }

    @Test
    public void getTrackFromNowPlayingList() {
        BrowseTree browseTree = new BrowseTree(mTestDevice);
        BrowseNode trackInNowPlayingList = browseTree.new BrowseNode(new AvrcpItem.Builder()
                .setUuid(ILLEGAL_ID).setTitle(ILLEGAL_ID).setBrowsable(true).build());

        browseTree.mNowPlayingNode.addChild(trackInNowPlayingList);

        assertThat(browseTree.getTrackFromNowPlayingList(0)).isEqualTo(
                trackInNowPlayingList);
    }

    @Test
    public void onConnected() {
        BrowseTree browseTree = new BrowseTree(null);

        assertThat(browseTree.mRootNode.getChildrenCount()).isEqualTo(0);

        browseTree.onConnected(mTestDevice);

        assertThat(browseTree.mRootNode.getChildrenCount()).isEqualTo(1);
    }

    @Test
    public void findBrowseNodeByIDForRoot() {
        BrowseTree browseTree = new BrowseTree(null);
        assertThat(browseTree.findBrowseNodeByID(BrowseTree.ROOT)).isEqualTo(browseTree.mRootNode);
    }

    @Test
    public void findBrowseNodeByIDForDevice() {
        BrowseTree browseTree = new BrowseTree(mTestDevice);
        final String deviceId = BrowseTree.ROOT + mTestDevice.getAddress().toString();
        assertThat(browseTree.findBrowseNodeByID(deviceId)).isEqualTo(browseTree.mRootNode);
    }

    @Test
    public void findBrowseNodeByIDForIllegalId() {
        BrowseTree browseTree = new BrowseTree(mTestDevice);
        assertThat(browseTree.findBrowseNodeByID(ILLEGAL_ID)).isNull();
    }

    @Test
    public void setAndGetCurrentBrowsedFolder() {
        BrowseTree browseTree = new BrowseTree(mTestDevice);

        assertThat(browseTree.setCurrentBrowsedFolder(ILLEGAL_ID)).isFalse();
        assertThat(browseTree.setCurrentBrowsedFolder(BrowseTree.NOW_PLAYING_PREFIX)).isTrue();
        assertThat(browseTree.getCurrentBrowsedFolder()).isEqualTo(browseTree.mNowPlayingNode);
    }

    @Test
    public void setAndGetCurrentBrowsedPlayer() {
        BrowseTree browseTree = new BrowseTree(mTestDevice);

        assertThat(browseTree.setCurrentBrowsedPlayer(ILLEGAL_ID, 0, 0)).isFalse();
        assertThat(
                browseTree.setCurrentBrowsedPlayer(BrowseTree.NOW_PLAYING_PREFIX, 2, 1)).isTrue();
        assertThat(browseTree.getCurrentBrowsedPlayer()).isEqualTo(browseTree.mNowPlayingNode);
    }

    @Test
    public void setAndGetCurrentAddressedPlayer() {
        BrowseTree browseTree = new BrowseTree(mTestDevice);

        assertThat(browseTree.setCurrentAddressedPlayer(ILLEGAL_ID)).isFalse();
        assertThat(browseTree.setCurrentAddressedPlayer(BrowseTree.NOW_PLAYING_PREFIX)).isTrue();
        assertThat(browseTree.getCurrentAddressedPlayer()).isEqualTo(browseTree.mNowPlayingNode);
    }

    @Test
    public void indicateCoverArtUsedAndUnused() {
        BrowseTree browseTree = new BrowseTree(mTestDevice);
        assertThat(browseTree.getNodesUsingCoverArt(TEST_HANDLE)).isEmpty();

        browseTree.indicateCoverArtUsed(TEST_NODE_ID, TEST_HANDLE);

        assertThat(browseTree.getNodesUsingCoverArt(TEST_HANDLE).get(0)).isEqualTo(TEST_NODE_ID);

        browseTree.indicateCoverArtUnused(TEST_NODE_ID, TEST_HANDLE);

        assertThat(browseTree.getNodesUsingCoverArt(TEST_HANDLE)).isEmpty();
        assertThat(browseTree.getAndClearUnusedCoverArt().get(0)).isEqualTo(TEST_HANDLE);
    }

    @Test
    public void notifyImageDownload() {
        BrowseTree browseTree = new BrowseTree(null);
        String testDeviceId = BrowseTree.PLAYER_PREFIX + mTestDevice.getAddress();

        browseTree.onConnected(mTestDevice);
        browseTree.indicateCoverArtUsed(TEST_NODE_ID, TEST_HANDLE);
        browseTree.indicateCoverArtUsed(testDeviceId, TEST_HANDLE);
        Set<BrowseTree.BrowseNode> parents = browseTree.notifyImageDownload(TEST_HANDLE, null);

        assertThat(parents.contains(browseTree.mRootNode)).isTrue();
    }

    @Test
    public void getEldestChild_whenNodesAreNotAncestorDescendantRelation() {
        BrowseTree browseTree = new BrowseTree(null);

        browseTree.onConnected(mTestDevice);

        assertThat(BrowseTree.getEldestChild(browseTree.mNowPlayingNode,
                browseTree.mRootNode)).isNull();
    }

    @Test
    public void getEldestChild_whenNodesAreAncestorDescendantRelation() {
        BrowseTree browseTree = new BrowseTree(null);

        browseTree.onConnected(mTestDevice);

        assertThat(BrowseTree.getEldestChild(browseTree.mRootNode,
                browseTree.mRootNode.getChild(0))).isEqualTo(browseTree.mRootNode.getChild(0));
    }

    @Test
    public void getNextStepFolder() {
        BrowseTree browseTree = new BrowseTree(null);
        BrowseNode nodeOutOfMap = browseTree.new BrowseNode(new AvrcpItem.Builder()
                .setUuid(ILLEGAL_ID).setTitle(ILLEGAL_ID).setBrowsable(true).build());

        browseTree.onConnected(mTestDevice);

        assertThat(browseTree.getNextStepToFolder(null)).isNull();
        assertThat(browseTree.getNextStepToFolder(browseTree.mRootNode)).isEqualTo(
                browseTree.mRootNode);
        assertThat(browseTree.getNextStepToFolder(browseTree.mRootNode.getChild(0))).isEqualTo(
                browseTree.mRootNode.getChild(0));
        assertThat(browseTree.getNextStepToFolder(nodeOutOfMap)).isNull();

        browseTree.setCurrentBrowsedPlayer(BrowseTree.NOW_PLAYING_PREFIX, 2, 1);
        assertThat(browseTree.getNextStepToFolder(browseTree.mRootNode.getChild(0))).isEqualTo(
                browseTree.mNavigateUpNode);
    }

    @Test
    public void toString_returnsSizeInfo() {
        BrowseTree browseTree = new BrowseTree(mTestDevice);
        assertThat(browseTree.toString()).isEqualTo(
                "[BrowseTree size=" + browseTree.mBrowseMap.size() + "]");
    }
}

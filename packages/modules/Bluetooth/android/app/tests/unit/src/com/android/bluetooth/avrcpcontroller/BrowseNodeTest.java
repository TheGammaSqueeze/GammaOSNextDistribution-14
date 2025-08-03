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

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.avrcpcontroller.BrowseTree.BrowseNode;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class BrowseNodeTest {
    private static final int TEST_PLAYER_ID = 1;
    private static final String TEST_UUID = "1111";
    private static final String TEST_NAME = "item";

    private final byte[] mTestAddress = new byte[]{01, 01, 01, 01, 01, 01};
    private BluetoothAdapter mAdapter;
    private BluetoothDevice mTestDevice = null;
    private BrowseTree mBrowseTree;
    private BrowseNode mRootNode;

    @Before
    public void setUp() {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mTestDevice = mAdapter.getRemoteDevice(mTestAddress);
        mBrowseTree = new BrowseTree(null);
        mRootNode = mBrowseTree.mRootNode;
    }

    @Test
    public void constructor_withAvrcpPlayer() {
        BrowseNode browseNode = mBrowseTree.new BrowseNode(new AvrcpPlayer.Builder().setDevice(
                mTestDevice).setPlayerId(TEST_PLAYER_ID).setSupportedFeature(
                AvrcpPlayer.FEATURE_BROWSING).build());

        assertThat(browseNode.isPlayer()).isTrue();
        assertThat(browseNode.getBluetoothID()).isEqualTo(TEST_PLAYER_ID);
        assertThat(browseNode.getDevice()).isEqualTo(mTestDevice);
        assertThat(browseNode.isBrowsable()).isTrue();
    }

    @Test
    public void getExpectedChildren() {
        int expectedChildren = 10;

        mRootNode.setExpectedChildren(expectedChildren);

        assertThat(mRootNode.getExpectedChildren()).isEqualTo(expectedChildren);
    }

    @Test
    public void addChildren() {
        AvrcpPlayer childAvrcpPlayer = new AvrcpPlayer.Builder().setPlayerId(
                TEST_PLAYER_ID).build();
        AvrcpItem childAvrcpItem = new AvrcpItem.Builder().setUuid(TEST_UUID).build();
        List<Object> children = new ArrayList<>();
        children.add(childAvrcpPlayer);
        children.add(childAvrcpItem);
        assertThat(mRootNode.getChild(0)).isNull();

        mRootNode.addChildren(children);

        assertThat(mRootNode.getChildrenCount()).isEqualTo(children.size());
        assertThat(mRootNode.getChildren().get(0).getBluetoothID()).isEqualTo(TEST_PLAYER_ID);
        assertThat(mRootNode.getChildren().get(1).getID()).isEqualTo(TEST_UUID);
    }

    @Test
    public void addChild_withImageUuid_toNowPlayingNode() {
        String coverArtUuid = "2222";
        AvrcpItem avrcpItem = new AvrcpItem.Builder().setUuid(TEST_UUID).build();
        avrcpItem.setCoverArtUuid(coverArtUuid);
        BrowseNode browseNode = mBrowseTree.new BrowseNode(avrcpItem);
        assertThat(mBrowseTree.mNowPlayingNode.isNowPlaying()).isTrue();

        mBrowseTree.mNowPlayingNode.addChild(browseNode);

        assertThat(mBrowseTree.mNowPlayingNode.isChild(browseNode)).isTrue();
        assertThat(browseNode.getParent()).isEqualTo(mBrowseTree.mNowPlayingNode);
        assertThat(browseNode.getScope()).isEqualTo(
                AvrcpControllerService.BROWSE_SCOPE_NOW_PLAYING);
        assertThat(mBrowseTree.getNodesUsingCoverArt(coverArtUuid).get(0)).isEqualTo(TEST_UUID);
    }

    @Test
    public void removeChild() {
        BrowseNode browseNode = mBrowseTree.new BrowseNode(
                new AvrcpItem.Builder().setUuid(TEST_UUID).build());
        mRootNode.addChild(browseNode);
        assertThat(mRootNode.getChildrenCount()).isEqualTo(1);

        mRootNode.removeChild(browseNode);

        assertThat(mRootNode.getChildrenCount()).isEqualTo(0);
    }

    @Test
    public void getContents() {
        mRootNode.setCached(false);
        assertThat(mRootNode.getContents()).isNull();
        AvrcpItem avrcpItem = new AvrcpItem.Builder().setUuid(TEST_UUID).build();
        BrowseNode browseNode = mBrowseTree.new BrowseNode(avrcpItem);

        mRootNode.addChild(browseNode);

        assertThat(mRootNode.getContents().size()).isEqualTo(1);
    }

    @Test
    public void setCached() {
        BrowseNode browseNode = mBrowseTree.new BrowseNode(
                new AvrcpItem.Builder().setUuid(TEST_UUID).build());
        mRootNode.addChild(browseNode);
        assertThat(mRootNode.getChildrenCount()).isEqualTo(1);

        mRootNode.setCached(false);

        assertThat(mRootNode.isCached()).isFalse();
        assertThat(mRootNode.getChildrenCount()).isEqualTo(0);
    }

    @Test
    public void getters() {
        BrowseNode browseNode = mBrowseTree.new BrowseNode(
                new AvrcpItem.Builder().setUuid(TEST_UUID).build());

        assertThat(browseNode.getFolderUID()).isEqualTo(TEST_UUID);
        assertThat(browseNode.getPlayerID()).isEqualTo(
                Integer.parseInt((TEST_UUID).replace(BrowseTree.PLAYER_PREFIX, "")));
    }

    @Test
    public void equals_withDifferentClass() {
        AvrcpItem avrcpItem = new AvrcpItem.Builder().setUuid(TEST_UUID).build();

        assertThat(mRootNode).isNotEqualTo(avrcpItem);
    }

    @Test
    public void equals_withSameId() {
        BrowseNode browseNodeOne = mBrowseTree.new BrowseNode(
                new AvrcpItem.Builder().setUuid(TEST_UUID).build());
        BrowseNode browseNodeTwo = mBrowseTree.new BrowseNode(
                new AvrcpItem.Builder().setUuid(TEST_UUID).build());

        assertThat(browseNodeOne).isEqualTo(browseNodeTwo);
    }

    @Test
    public void isDescendant() {
        BrowseNode browseNode = mBrowseTree.new BrowseNode(
                new AvrcpItem.Builder().setUuid(TEST_UUID).build());
        mRootNode.addChild(browseNode);

        assertThat(mRootNode.isDescendant(browseNode)).isTrue();
    }

    @Test
    public void toTreeString_returnFormattedString() {
        final String expected = "  [Id: 1111 Name: item Size: 2]\n"
                + "    [Id: child1 Name: child1 Size: 1]\n"
                + "      [Id: child3 Name: child3 Size: 0]\n"
                + "    [Id: child2 Name: child2 Size: 0]\n";

        BrowseNode browseNode = mBrowseTree.new BrowseNode(new AvrcpItem.Builder()
                .setUuid(TEST_UUID)
                .setDisplayableName(TEST_NAME)
                .build());
        BrowseNode childNode1 = mBrowseTree.new BrowseNode(new AvrcpItem.Builder()
                .setUuid("child1")
                .setDisplayableName("child1")
                .build());
        BrowseNode childNode2 = mBrowseTree.new BrowseNode(new AvrcpItem.Builder()
                .setUuid("child2")
                .setDisplayableName("child2")
                .build());
        BrowseNode childNode3 = mBrowseTree.new BrowseNode(new AvrcpItem.Builder()
                .setUuid("child3")
                .setDisplayableName("child3")
                .build());
        childNode1.addChild(childNode3);
        browseNode.addChild(childNode1);
        browseNode.addChild(childNode2);

        StringBuilder sb = new StringBuilder();
        browseNode.toTreeString(0, sb);
        assertThat(sb.toString()).isEqualTo(expected);
    }

    @Test
    public void toString_returnsId() {
        BrowseNode browseNode = mBrowseTree.new BrowseNode(new AvrcpItem.Builder()
                .setUuid(TEST_UUID)
                .setDisplayableName(TEST_NAME)
                .build());

        assertThat(browseNode.toString()).isEqualTo(
                "[Id: " + TEST_UUID + " Name: " + TEST_NAME + " Size: 0]");
    }
}

/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.bluetooth.audio_util;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.test.TestLooper;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public final class BrowsablePlayerConnectorTest {
    private static final int TIMEOUT_MS = 300;

    Context mContext;
    TestLooper mTestLooper;
    List<ResolveInfo> mPlayerList;
    @Mock MediaBrowser mMediaBrowser;
    MediaBrowser.ConnectionCallback mConnectionCallback;
    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = InstrumentationRegistry.getTargetContext();
        mTestLooper = new TestLooper();

        doAnswer(invocation -> {
            mConnectionCallback = invocation.getArgument(2);
            return null;
        }).when(mMediaBrowser).testInit(any(), any(), any(), any());
        doAnswer(invocation -> {
            mConnectionCallback.onConnected();
            return null;
        }).when(mMediaBrowser).connect();
        doAnswer(invocation -> {
            String id = invocation.getArgument(0);
            android.media.browse.MediaBrowser.SubscriptionCallback callback
                    = invocation.getArgument(1);
            callback.onChildrenLoaded(id, Collections.emptyList());
            return null;
        }).when(mMediaBrowser).subscribe(any(), any());
        doReturn("testRoot").when(mMediaBrowser).getRoot();
        MediaBrowserFactory.inject(mMediaBrowser);

        ResolveInfo player = new ResolveInfo();
        player.serviceInfo = new ServiceInfo();
        player.serviceInfo.packageName = "com.android.bluetooth.test";
        player.serviceInfo.name = "TestPlayer";
        mPlayerList = new ArrayList();
        mPlayerList.add(player);
    }

    @Test
    public void browsablePlayerConnectorCallback_calledAfterConnection()
            throws InterruptedException {
        mTestLooper.startAutoDispatch();
        CountDownLatch latch = new CountDownLatch(1);
        BrowsablePlayerConnector connector =
                BrowsablePlayerConnector.connectToPlayers(
                        mContext,
                        mTestLooper.getLooper(),
                        mPlayerList,
                        (List<BrowsedPlayerWrapper> players) -> latch.countDown());
        verify(mMediaBrowser, timeout(TIMEOUT_MS).atLeast(1)).connect();
        assertThat(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        connector.cleanup();
        mTestLooper.stopAutoDispatch();
        mTestLooper.dispatchAll();
    }

    @Test
    public void cleanup_doesNotCrash() {
        BrowsablePlayerConnector connector =
                BrowsablePlayerConnector.connectToPlayers(
                        mContext,
                        mTestLooper.getLooper(),
                        mPlayerList,
                        (List<BrowsedPlayerWrapper> players) -> {});
        verify(mMediaBrowser, timeout(TIMEOUT_MS).atLeast(1)).connect();
        connector.cleanup();
        mTestLooper.dispatchAll();
    }
}

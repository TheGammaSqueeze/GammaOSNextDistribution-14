/*
 * Copyright 2017 The Android Open Source Project
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

import static org.mockito.Mockito.*;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaDescription;
import android.media.browse.MediaBrowser.MediaItem;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.R;
import com.android.bluetooth.TestUtils;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class BrowserPlayerWrapperTest {

    @Captor ArgumentCaptor<MediaBrowser.ConnectionCallback> mBrowserConnCb;
    @Captor ArgumentCaptor<MediaBrowser.SubscriptionCallback> mSubscriptionCb;
    @Captor ArgumentCaptor<MediaController.Callback> mControllerCb;
    @Captor ArgumentCaptor<Handler> mTimeoutHandler;
    @Captor ArgumentCaptor<List<ListItem>> mWrapperBrowseCb;
    @Mock MediaBrowser mMockBrowser;
    @Mock BrowsedPlayerWrapper.ConnectionCallback mConnCb;
    @Mock BrowsedPlayerWrapper.BrowseCallback mBrowseCb;
    private HandlerThread mThread;

    @Mock Context mMockContext;
    @Mock Resources mMockResources;
    private Context mTargetContext;
    private Resources mTestResources;
    private MockContentResolver mTestContentResolver;

    private static final String TEST_AUTHORITY = "com.android.bluetooth.avrcp.test";
    private static final Uri TEST_CONTENT_URI = new Uri.Builder()
            .scheme(ContentResolver.SCHEME_CONTENT)
            .authority(TEST_AUTHORITY)
            .build();

    private static final String IMAGE_HANDLE_1 = "0000001";
    private static final Uri IMAGE_URI_1 = TEST_CONTENT_URI.buildUpon()
            .appendQueryParameter("handle", IMAGE_HANDLE_1)
            .build();
    private static final String IMAGE_STRING_1 = IMAGE_URI_1.toString();

    private Bitmap mTestBitmap = null;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mTargetContext = InstrumentationRegistry.getTargetContext();
        mTestResources = TestUtils.getTestApplicationResources(mTargetContext);

        mTestBitmap = loadImage(com.android.bluetooth.tests.R.raw.image_200_200);

        mTestContentResolver = new MockContentResolver(mTargetContext);
        mTestContentResolver.addProvider(TEST_AUTHORITY, new MockContentProvider() {
            @Override
            public AssetFileDescriptor openTypedAssetFile(Uri url, String mimeType, Bundle opts) {
                String handle = url.getQueryParameter("handle");
                if (IMAGE_URI_1.equals(url)) {
                    return mTestResources.openRawResourceFd(
                            com.android.bluetooth.tests.R.raw.image_200_200);
                }
                return null;
            }
        });

        when(mMockContext.getContentResolver()).thenReturn(mTestContentResolver);
        when(mMockResources.getBoolean(R.bool.avrcp_target_cover_art_uri_images)).thenReturn(true);
        when(mMockContext.getResources()).thenReturn(mMockResources);

        // Set up Looper thread for the timeout handler
        mThread = new HandlerThread("MediaPlayerWrapperTestThread");
        mThread.start();

        when(mMockBrowser.getRoot()).thenReturn("root_folder");

        MediaBrowserFactory.inject(mMockBrowser);
    }

    @After
    public void tearDown() {
        if (mThread != null) {
            mThread.quitSafely();
        }
        mThread = null;
        mTestContentResolver = null;
        mTestBitmap = null;
        mTestResources = null;
        mTargetContext = null;
    }

    private Bitmap loadImage(int resId) {
        InputStream imageInputStream = mTestResources.openRawResource(resId);
        return BitmapFactory.decodeStream(imageInputStream);
    }

    private MediaDescription getMediaDescription(String id, String title, String artist,
            String album, Bitmap bitmap, Uri uri, Bundle extras) {
        MediaDescription.Builder builder = new MediaDescription.Builder()
                .setMediaId(id)
                .setTitle(title)
                .setSubtitle(artist)
                .setDescription(album);
        if (bitmap != null) {
            builder.setIconBitmap(bitmap);
        }
        if (uri != null) {
            builder.setIconUri(uri);
        }
        if (extras != null) {
            builder.setExtras(extras);
        }
        return builder.build();
    }

    private MediaItem getMediaItem(MediaDescription description, int flags) {
        return new MediaItem(description, flags);
    }

    @Test
    public void testWrap() {
        BrowsedPlayerWrapper wrapper =
                BrowsedPlayerWrapper.wrap(mMockContext, mThread.getLooper(), "test", "test");
        wrapper.connect(mConnCb);
        verify(mMockBrowser).testInit(any(), any(), mBrowserConnCb.capture(), any());
        verify(mMockBrowser).connect();

        MediaBrowser.ConnectionCallback browserConnCb = mBrowserConnCb.getValue();
        browserConnCb.onConnected();

        verify(mConnCb).run(eq(BrowsedPlayerWrapper.STATUS_SUCCESS), eq(wrapper));
        verify(mMockBrowser).disconnect();
    }

    @Test
    public void testConnect_Successful() {
        BrowsedPlayerWrapper wrapper =
                BrowsedPlayerWrapper.wrap(mMockContext, mThread.getLooper(), "test", "test");
        wrapper.connect(mConnCb);
        verify(mMockBrowser).testInit(any(), any(), mBrowserConnCb.capture(), any());
        MediaBrowser.ConnectionCallback browserConnCb = mBrowserConnCb.getValue();

        verify(mMockBrowser, times(1)).connect();
        browserConnCb.onConnected();
        verify(mConnCb).run(eq(BrowsedPlayerWrapper.STATUS_SUCCESS), eq(wrapper));
        verify(mMockBrowser, times(1)).disconnect();
    }

    @Test
    public void testConnect_Suspended() {
        BrowsedPlayerWrapper wrapper =
                BrowsedPlayerWrapper.wrap(mMockContext, mThread.getLooper(), "test", "test");
        wrapper.connect(mConnCb);
        verify(mMockBrowser).testInit(any(), any(), mBrowserConnCb.capture(), any());
        MediaBrowser.ConnectionCallback browserConnCb = mBrowserConnCb.getValue();

        verify(mMockBrowser, times(1)).connect();
        browserConnCb.onConnectionSuspended();
        verify(mConnCb).run(eq(BrowsedPlayerWrapper.STATUS_CONN_ERROR), eq(wrapper));
        // Twice because our mConnCb is wrapped when using the plain connect() call and disconnect
        // is called for us when the callback is invoked in addition to error handling calling
        // disconnect.
        verify(mMockBrowser, times(2)).disconnect();
    }

    @Test
    public void testConnect_Failed() {
        BrowsedPlayerWrapper wrapper =
                BrowsedPlayerWrapper.wrap(mMockContext, mThread.getLooper(), "test", "test");
        wrapper.connect(mConnCb);
        verify(mMockBrowser).testInit(any(), any(), mBrowserConnCb.capture(), any());
        MediaBrowser.ConnectionCallback browserConnCb = mBrowserConnCb.getValue();

        verify(mMockBrowser, times(1)).connect();
        browserConnCb.onConnectionFailed();
        verify(mConnCb).run(eq(BrowsedPlayerWrapper.STATUS_CONN_ERROR), eq(wrapper));
        verify(mMockBrowser, times(1)).disconnect();
    }

    @Test
    public void testEmptyRoot() {
        BrowsedPlayerWrapper wrapper =
                BrowsedPlayerWrapper.wrap(mMockContext, mThread.getLooper(), "test", "test");

        doReturn("").when(mMockBrowser).getRoot();

        wrapper.connect(mConnCb);
        verify(mMockBrowser).testInit(any(), any(), mBrowserConnCb.capture(), any());
        MediaBrowser.ConnectionCallback browserConnCb = mBrowserConnCb.getValue();

        verify(mMockBrowser, times(1)).connect();

        browserConnCb.onConnected();
        verify(mConnCb).run(eq(BrowsedPlayerWrapper.STATUS_CONN_ERROR), eq(wrapper));
        verify(mMockBrowser, times(1)).disconnect();
    }

    @Test
    public void testDisconnect() {
        BrowsedPlayerWrapper wrapper =
                BrowsedPlayerWrapper.wrap(mMockContext, mThread.getLooper(), "test", "test");
        wrapper.connect(mConnCb);
        verify(mMockBrowser).testInit(any(), any(), mBrowserConnCb.capture(), any());
        MediaBrowser.ConnectionCallback browserConnCb = mBrowserConnCb.getValue();
        browserConnCb.onConnected();
        verify(mMockBrowser).disconnect();
    }

    @Test
    public void testGetRootId() {
        BrowsedPlayerWrapper wrapper =
                BrowsedPlayerWrapper.wrap(mMockContext, mThread.getLooper(), "test", "test");
        wrapper.connect(mConnCb);
        verify(mMockBrowser).testInit(any(), any(), mBrowserConnCb.capture(), any());
        MediaBrowser.ConnectionCallback browserConnCb = mBrowserConnCb.getValue();
        browserConnCb.onConnected();

        Assert.assertEquals("root_folder", wrapper.getRootId());
        verify(mMockBrowser).disconnect();
    }

    @Test
    public void testPlayItem() {
        BrowsedPlayerWrapper wrapper =
                BrowsedPlayerWrapper.wrap(mMockContext, mThread.getLooper(), "test", "test");
        verify(mMockBrowser).testInit(any(), any(), mBrowserConnCb.capture(), any());
        MediaBrowser.ConnectionCallback browserConnCb = mBrowserConnCb.getValue();

        wrapper.playItem("test_item");
        verify(mMockBrowser, times(1)).connect();

        MediaController mockController = mock(MediaController.class);
        MediaController.TransportControls mockTransport =
                mock(MediaController.TransportControls.class);
        when(mockController.getTransportControls()).thenReturn(mockTransport);
        MediaControllerFactory.inject(mockController);

        browserConnCb.onConnected();
        verify(mockTransport).playFromMediaId(eq("test_item"), eq(null));

        // Do not immediately disconnect. Non-foreground playback serves will likely fail
        verify(mMockBrowser, times(0)).disconnect();

        verify(mockController).registerCallback(mControllerCb.capture(), any());
        MediaController.Callback controllerCb = mControllerCb.getValue();
        PlaybackState.Builder builder = new PlaybackState.Builder();

        // Do not disconnect on an event that isn't "playing"
        builder.setState(PlaybackState.STATE_PAUSED, 0, 1);
        controllerCb.onPlaybackStateChanged(builder.build());
        verify(mMockBrowser, times(0)).disconnect();

        // Once we're told we're playing, make sure we disconnect
        builder.setState(PlaybackState.STATE_PLAYING, 0, 1);
        controllerCb.onPlaybackStateChanged(builder.build());
        verify(mMockBrowser, times(1)).disconnect();
    }

    @Test
    public void testPlayItem_Timeout() {
        BrowsedPlayerWrapper wrapper =
                BrowsedPlayerWrapper.wrap(mMockContext, mThread.getLooper(), "test", "test");
        verify(mMockBrowser).testInit(any(), any(), mBrowserConnCb.capture(), any());
        MediaBrowser.ConnectionCallback browserConnCb = mBrowserConnCb.getValue();

        wrapper.playItem("test_item");
        verify(mMockBrowser, times(1)).connect();

        MediaController mockController = mock(MediaController.class);
        MediaController.TransportControls mockTransport =
                mock(MediaController.TransportControls.class);
        when(mockController.getTransportControls()).thenReturn(mockTransport);
        MediaControllerFactory.inject(mockController);

        browserConnCb.onConnected();
        verify(mockTransport).playFromMediaId(eq("test_item"), eq(null));

        verify(mockController).registerCallback(any(), mTimeoutHandler.capture());
        Handler timeoutHandler = mTimeoutHandler.getValue();

        timeoutHandler.sendEmptyMessage(BrowsedPlayerWrapper.TimeoutHandler.MSG_TIMEOUT);

        verify(mMockBrowser, timeout(2000).times(1)).disconnect();
    }

    @Test
    public void testGetFolderItems() {
        BrowsedPlayerWrapper wrapper =
                BrowsedPlayerWrapper.wrap(mMockContext, mThread.getLooper(), "test", "test");
        verify(mMockBrowser).testInit(any(), any(), mBrowserConnCb.capture(), any());
        MediaBrowser.ConnectionCallback browserConnCb = mBrowserConnCb.getValue();

        wrapper.getFolderItems("test_folder", mBrowseCb);

        browserConnCb.onConnected();
        verify(mMockBrowser).subscribe(any(), mSubscriptionCb.capture());
        MediaBrowser.SubscriptionCallback subscriptionCb = mSubscriptionCb.getValue();

        MediaDescription desc = null;
        ArrayList<MediaItem> items = new ArrayList<MediaItem>();

        desc = getMediaDescription("s1", "song1", "artist", "album", null, null, null);
        items.add(getMediaItem(desc, MediaItem.FLAG_PLAYABLE));

        desc = getMediaDescription("s2", "song2", "artist", "album", mTestBitmap, null, null);
        items.add(getMediaItem(desc, MediaItem.FLAG_PLAYABLE));

        desc = getMediaDescription("s3", "song3", "artist", "album", null, IMAGE_URI_1, null);
        items.add(getMediaItem(desc, MediaItem.FLAG_PLAYABLE));

        desc = getMediaDescription("a1", "album1", "artist", null, null, null, null);
        items.add(getMediaItem(desc, MediaItem.FLAG_BROWSABLE));

        desc = getMediaDescription("a2", "album2", "artist", null, mTestBitmap, null, null);
        items.add(getMediaItem(desc, MediaItem.FLAG_BROWSABLE));

        desc = getMediaDescription("a3", "album3", "artist", null, null, IMAGE_URI_1, null);
        items.add(getMediaItem(desc, MediaItem.FLAG_BROWSABLE));

        desc = getMediaDescription("p1", "playlist1", null, null, mTestBitmap, null, null);
        items.add(getMediaItem(desc, MediaItem.FLAG_BROWSABLE | MediaItem.FLAG_PLAYABLE));

        subscriptionCb.onChildrenLoaded("test_folder", items);
        verify(mMockBrowser).unsubscribe(eq("test_folder"));
        verify(mBrowseCb).run(eq(BrowsedPlayerWrapper.STATUS_SUCCESS), eq("test_folder"),
                mWrapperBrowseCb.capture());

        // Verify returned ListItems
        List<ListItem> item_list = mWrapperBrowseCb.getValue();
        for (int i = 0; i < item_list.size(); i++) {
            MediaItem expected = items.get(i);
            ListItem item = item_list.get(i);
            Assert.assertEquals(expected.isBrowsable(), item.isFolder);
            if (item.isFolder) {
                Folder folder = item.folder;
                Assert.assertNotNull(folder);
                Assert.assertFalse(folder.isPlayable);
                Assert.assertEquals(expected.getDescription().getMediaId(), folder.mediaId);
                Assert.assertEquals(expected.getDescription().getTitle(), folder.title);
            } else {
                Metadata song = item.song;
                Assert.assertNotNull(song);
                Assert.assertEquals(expected.getDescription().getMediaId(), song.mediaId);
                Assert.assertEquals(expected.getDescription().getTitle(), song.title);
                Assert.assertEquals(expected.getDescription().getSubtitle(), song.artist);
                Assert.assertEquals(expected.getDescription().getDescription(), song.album);
                if (expected.getDescription().getIconBitmap() != null) {
                    Assert.assertNotNull(song.image);
                    Bitmap expectedBitmap = expected.getDescription().getIconBitmap();
                    Assert.assertTrue(expectedBitmap.sameAs(song.image.getImage()));
                } else if (expected.getDescription().getIconUri() != null) {
                    Assert.assertTrue(mTestBitmap.sameAs(song.image.getImage()));
                } else {
                    Assert.assertEquals(null, song.image);
                }
            }
        }

        verify(mMockBrowser).disconnect();
    }

    @Test
    public void testGetFolderItems_Timeout() {
        BrowsedPlayerWrapper wrapper =
                BrowsedPlayerWrapper.wrap(mMockContext, mThread.getLooper(), "test", "test");
        verify(mMockBrowser).testInit(any(), any(), mBrowserConnCb.capture(), any());
        MediaBrowser.ConnectionCallback browserConnCb = mBrowserConnCb.getValue();

        wrapper.getFolderItems("test_folder", mBrowseCb);

        browserConnCb.onConnected();
        verify(mMockBrowser).subscribe(any(), mSubscriptionCb.capture());
        MediaBrowser.SubscriptionCallback subscriptionCb = mSubscriptionCb.getValue();
        Handler timeoutHandler = subscriptionCb.getTimeoutHandler();

        timeoutHandler.sendEmptyMessage(BrowsedPlayerWrapper.TimeoutHandler.MSG_TIMEOUT);
        verify(mMockBrowser, timeout(2000).times(1)).disconnect();
    }
}

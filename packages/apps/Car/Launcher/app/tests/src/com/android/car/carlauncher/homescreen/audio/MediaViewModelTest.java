/*
 * Copyright (C) 2020 Google Inc.
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

package com.android.car.carlauncher.homescreen.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.content.ComponentName;
import android.graphics.drawable.Drawable;

import androidx.lifecycle.MutableLiveData;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.apps.common.testutils.InstantTaskExecutorRule;
import com.android.car.carlauncher.AppLauncherUtils;
import com.android.car.carlauncher.homescreen.HomeCardInterface;
import com.android.car.carlauncher.homescreen.ui.CardHeader;
import com.android.car.carlauncher.homescreen.ui.DescriptiveTextWithControlsView;
import com.android.car.carlauncher.homescreen.ui.SeekBarViewModel;
import com.android.car.media.common.MediaItemMetadata;
import com.android.car.media.common.playback.PlaybackProgress;
import com.android.car.media.common.playback.PlaybackViewModel;
import com.android.car.media.common.source.MediaSource;
import com.android.car.media.common.source.MediaSourceColors;
import com.android.car.media.common.source.MediaSourceViewModel;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class MediaViewModelTest extends AbstractExtendedMockitoTestCase  {

    private static final CharSequence APP_NAME = "test app name";
    private static final Drawable APP_ICON = null;
    private static final CharSequence SONG_TITLE = "test song title";
    private static final CharSequence ARTIST_NAME = "test artist name";
    private static final boolean IS_TIME_AVAILABLE = true;
    private static final CharSequence CURRENT_TIME = "1:00";
    private static final CharSequence MAX_TIME = "10:00";
    private static final double PROGRESS_FRACTION = 0.01;
    private static final int COLORS = 0;
    private static final int DEFAULT_COLORS = 1;


    private MediaViewModel mMediaViewModel;
    private MutableLiveData<MediaSource> mLiveMediaSource = new MutableLiveData<>();
    private MutableLiveData<MediaItemMetadata> mLiveMetadata = new MutableLiveData<>();
    private MutableLiveData<MediaSourceColors> mLiveColors = new MutableLiveData<>();
    private MutableLiveData<PlaybackProgress> mLiveProgress = new MutableLiveData<>();
    private MutableLiveData<PlaybackViewModel.PlaybackStateWrapper> mLivePlaybackState =
            new MutableLiveData<>();

    private MutableLiveData<PlaybackViewModel.PlaybackController> mPlaybackController =
            new MutableLiveData<>();

    @Mock
    private MediaSourceViewModel mSourceViewModel;
    @Mock
    private PlaybackViewModel mPlaybackViewModel;
    @Mock
    private MediaSource mMediaSource;
    @Mock
    private MediaItemMetadata mMetadata;
    @Mock
    private MediaSourceColors mColors;
    @Mock
    private PlaybackProgress mProgress;
    @Mock
    private HomeCardInterface.Model.OnModelUpdateListener mOnModelUpdateListener;
    @Mock
    private AudioModel.OnProgressUpdateListener mOnProgressUpdateListener;


    // The tests use the MediaViewModel's observers. To avoid errors with invoking observeForever
    // on a background thread, this rule configures LiveData to execute each task synchronously.
    @Rule
    public final InstantTaskExecutorRule mTaskExecutorRule = new InstantTaskExecutorRule();
    private int mSeekBarMax;

    @Override
    protected void onSessionBuilder(
            AbstractExtendedMockitoTestCase.CustomMockitoSessionBuilder session) {
        session.spyStatic(AppLauncherUtils.class);
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mMediaViewModel = new MediaViewModel(ApplicationProvider.getApplicationContext(),
                mSourceViewModel, mPlaybackViewModel);
        when(mSourceViewModel.getPrimaryMediaSource()).thenReturn(mLiveMediaSource);
        when(mPlaybackViewModel.getMetadata()).thenReturn(mLiveMetadata);
        when(mPlaybackViewModel.getMediaSourceColors()).thenReturn(mLiveColors);
        when(mPlaybackViewModel.getProgress()).thenReturn(mLiveProgress);
        when(mPlaybackViewModel.getPlaybackStateWrapper()).thenReturn(mLivePlaybackState);
        when(mPlaybackViewModel.getPlaybackController()).thenReturn(mPlaybackController);
        mMediaViewModel.setOnModelUpdateListener(mOnModelUpdateListener);
        mMediaViewModel.setOnProgressUpdateListener(mOnProgressUpdateListener);
        mMediaViewModel.onCreate(ApplicationProvider.getApplicationContext());
        mSeekBarMax = ApplicationProvider.getApplicationContext().getResources().getInteger(
                com.android.car.carlauncher.R.integer.optional_seekbar_max);
        reset(mOnModelUpdateListener);
    }

    @After
    public void after() {
        mMediaViewModel.onCleared();
    }

    @Test
    public void noChange_doesNotCallPresenter() {
        verify(mOnModelUpdateListener, never()).onModelUpdate(any());
        assertNull(mMediaViewModel.getCardHeader());
        DescriptiveTextWithControlsView content =
                (DescriptiveTextWithControlsView) mMediaViewModel.getCardContent();
        assertNull(content.getTitle());
        assertNull(content.getSubtitle());
    }

    @Test
    public void changeSourceAndMetadata_updatesModel() {
        when(mMediaSource.getDisplayName(any())).thenReturn(APP_NAME);
        when(mMediaSource.getIcon()).thenReturn(APP_ICON);
        when(mMetadata.getSubtitle()).thenReturn(ARTIST_NAME);
        when(mMetadata.getTitle()).thenReturn(SONG_TITLE);

        when(mMediaSource.getBrowseServiceComponentName())
                .thenReturn(ComponentName.createRelative("com.test", ".mbs"));

        mLiveMediaSource.setValue(mMediaSource);
        mLiveMetadata.setValue(mMetadata);

        // Model is updated exactly twice: once when source is set (null metadata)
        // and again when the metadata is set
        verify(mOnModelUpdateListener, times(2)).onModelUpdate(mMediaViewModel);
        CardHeader header = mMediaViewModel.getCardHeader();
        assertEquals(header.getCardTitle(), APP_NAME);
        assertNull(header.getCardIcon());
        DescriptiveTextWithControlsView content =
                (DescriptiveTextWithControlsView) mMediaViewModel.getCardContent();
        assertEquals(content.getTitle(), SONG_TITLE);
        assertEquals(content.getSubtitle(), ARTIST_NAME);
    }

    @Test
    public void changeSourceOnly_updatesModel() {
        when(mMediaSource.getDisplayName(any())).thenReturn(APP_NAME);
        when(mMediaSource.getIcon()).thenReturn(APP_ICON);

        when(mMediaSource.getBrowseServiceComponentName())
                .thenReturn(ComponentName.createRelative("com.test", ".mbs"));

        mLiveMediaSource.setValue(mMediaSource);

        verify(mOnModelUpdateListener).onModelUpdate(mMediaViewModel);
        CardHeader header = mMediaViewModel.getCardHeader();
        assertEquals(header.getCardTitle(), APP_NAME);
        assertNull(header.getCardIcon());
        DescriptiveTextWithControlsView content =
                (DescriptiveTextWithControlsView) mMediaViewModel.getCardContent();
        assertEquals(content.getTitle().toString(), "");
        assertEquals(content.getTitle().toString(), "");

    }

    @Test
    public void changeMetadataOnly_doesNotCallPresenter() {
        when(mMetadata.getSubtitle()).thenReturn(ARTIST_NAME);
        when(mMetadata.getTitle()).thenReturn(SONG_TITLE);

        mLiveMetadata.setValue(mMetadata);

        verify(mOnModelUpdateListener, never()).onModelUpdate(any());
        assertNull(mMediaViewModel.getCardHeader());
        DescriptiveTextWithControlsView content =
                (DescriptiveTextWithControlsView) mMediaViewModel.getCardContent();
        assertEquals(content.getTitle(), SONG_TITLE);
        assertEquals(content.getSubtitle(), ARTIST_NAME);
    }

    @Test
    public void changeTimeOnly_updateModel() {
        when(mProgress.getProgressFraction()).thenReturn(PROGRESS_FRACTION);
        when(mProgress.hasTime()).thenReturn(IS_TIME_AVAILABLE);
        when(mProgress.getCurrentTimeText()).thenReturn(CURRENT_TIME);
        when(mProgress.getMaxTimeText()).thenReturn(MAX_TIME);

        mLiveProgress.setValue(mProgress);

        verify(mOnProgressUpdateListener).onProgressUpdate(mMediaViewModel, IS_TIME_AVAILABLE);
        DescriptiveTextWithControlsView content =
                (DescriptiveTextWithControlsView) mMediaViewModel.getCardContent();
        SeekBarViewModel seekBarViewModel = content.getSeekBarViewModel();
        assertEquals(seekBarViewModel.getTimes().toString(), CURRENT_TIME + "/" + MAX_TIME);
        assertEquals(seekBarViewModel.getProgress(), (int) (mSeekBarMax * PROGRESS_FRACTION));
    }

    @Test
    public void changeColor_updateModel() {
        when(mColors.getAccentColor(DEFAULT_COLORS)).thenReturn(COLORS);
        when(mProgress.hasTime()).thenReturn(IS_TIME_AVAILABLE);
        when(mProgress.getCurrentTimeText()).thenReturn(CURRENT_TIME);
        when(mProgress.getMaxTimeText()).thenReturn(MAX_TIME);

        mLiveProgress.setValue(mProgress);
        mLiveColors.setValue(mColors);

        verify(mOnProgressUpdateListener, times(1)).onProgressUpdate(mMediaViewModel, false);
        DescriptiveTextWithControlsView content =
                (DescriptiveTextWithControlsView) mMediaViewModel.getCardContent();
        SeekBarViewModel seekBarViewModel = content.getSeekBarViewModel();
        assertEquals(seekBarViewModel.getSeekBarColor(), COLORS);
    }
}


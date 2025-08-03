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

import static android.car.media.CarMediaIntents.EXTRA_MEDIA_COMPONENT;
import static android.car.media.CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK;

import android.app.Application;
import android.car.media.CarMediaIntents;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.Observer;

import com.android.car.apps.common.imaging.ImageBinder;
import com.android.car.carlauncher.homescreen.HomeCardInterface;
import com.android.car.carlauncher.homescreen.ui.CardContent;
import com.android.car.carlauncher.homescreen.ui.CardHeader;
import com.android.car.carlauncher.homescreen.ui.DescriptiveTextWithControlsView;
import com.android.car.carlauncher.homescreen.ui.SeekBarViewModel;
import com.android.car.media.common.MediaItemMetadata;
import com.android.car.media.common.R;
import com.android.car.media.common.playback.PlaybackProgress;
import com.android.car.media.common.playback.PlaybackViewModel;
import com.android.car.media.common.source.MediaSource;
import com.android.car.media.common.source.MediaSourceColors;
import com.android.car.media.common.source.MediaSourceViewModel;
import com.android.internal.annotations.VisibleForTesting;


/**
 * ViewModel for media. Uses both a {@link MediaSourceViewModel} and a {@link PlaybackViewModel}
 * for data on the audio source and audio metadata (such as song title), respectively.
 */
public class MediaViewModel extends AndroidViewModel implements AudioModel {

    private static final String TAG = "MediaViewModel";

    private static final String EMPTY_TIME = "";

    private HomeCardInterface.Presenter mAudioPresenter;
    // MediaSourceViewModel is for the current or last played media app
    private MediaSourceViewModel mSourceViewModel;
    // PlaybackViewModel has the media's metadata
    private PlaybackViewModel mPlaybackViewModel;
    private PlaybackViewModel.PlaybackController mPlaybackController;
    private Context mContext;

    private CardHeader mCardHeader;
    private CharSequence mAppName;
    private Drawable mAppIcon;
    private CharSequence mSongTitle;
    private CharSequence mArtistName;
    private CharSequence mTimes;
    private CharSequence mTimesSeparator;
    private boolean mIsSeekEnabled;
    private boolean mUseMediaSourceColor;
    private int mDefaultSeekBarColor;
    private int mSeekBarColor;

    /**
     * Use int value for progress and seekbar max value from config since {@link
     * android.widget.SeekBar} only works with int. Handling the long & int conversion in {@link
     * MediaViewModel}
     */
    private int mProgress;
    private int mSeekBarMax;
    private long mRealMaxProgress;

    private ImageBinder<MediaItemMetadata.ArtworkRef> mAlbumArtBinder;
    private Drawable mAlbumImageBitmap;
    private Drawable mMediaBackground;
    private OnModelUpdateListener mOnModelUpdateListener;
    private OnProgressUpdateListener mOnProgressUpdateListener;
    private Observer<Object> mMediaSourceColorObserver = x -> updateMediaSourceColor();
    private Observer<Object> mMetadataObserver = x -> updateModelMetadata();
    private Observer<Object> mPlaybackControllerObserver = controller -> updatePlaybackController();
    private PlaybackCallback mPlaybackCallback = new PlaybackCallback() {
        @Override
        public void seekTo(int pos) {
            if (mPlaybackController != null) {
                double fraction = (double) pos / (double) mSeekBarMax;
                Double realPos = mRealMaxProgress * fraction;
                mPlaybackController.seekTo(realPos.longValue());
            }
        }
    };
    private Observer<Object> mMediaSourceObserver = x -> updateModel();
    private Observer<Object> mProgressObserver = x -> updateProgress();

    private Observer<PlaybackViewModel.PlaybackStateWrapper> mPlaybackStateWrapperObserver =
            playbackStateWrapper -> {
                if (playbackStateWrapper != null
                        && mIsSeekEnabled != playbackStateWrapper.isSeekToEnabled()) {
                    mIsSeekEnabled = playbackStateWrapper.isSeekToEnabled();
                    if (mOnProgressUpdateListener != null) {
                        mOnProgressUpdateListener.onProgressUpdate(/* model = */
                                this, /* updateProgress = */
                                false);
                    }
                }
            };

    public MediaViewModel(Application application) {
        super(application);
    }

    @VisibleForTesting
    MediaViewModel(Application application, MediaSourceViewModel sourceViewModel,
            PlaybackViewModel playbackViewModel) {
        super(application);
        mSourceViewModel = sourceViewModel;
        mPlaybackViewModel = playbackViewModel;

    }

    @Override
    public void onCreate(@NonNull Context context) {
        if (mSourceViewModel == null) {
            mSourceViewModel = MediaSourceViewModel.get(getApplication(),
                    MEDIA_SOURCE_MODE_PLAYBACK);
        }
        if (mPlaybackViewModel == null) {
            mPlaybackViewModel = PlaybackViewModel.get(getApplication(),
                    MEDIA_SOURCE_MODE_PLAYBACK);
        }

        mContext = context;
        Resources resources = mContext.getResources();
        int max = resources.getInteger(R.integer.media_items_bitmap_max_size_px);
        mMediaBackground = resources
                .getDrawable(R.drawable.control_bar_image_background);
        Size maxArtSize = new Size(max, max);
        mAlbumArtBinder = new ImageBinder<>(ImageBinder.PlaceholderType.FOREGROUND, maxArtSize,
                drawable -> {
                    mAlbumImageBitmap = drawable;
                    mOnModelUpdateListener.onModelUpdate(/* model = */ this);
                });
        mSourceViewModel.getPrimaryMediaSource().observeForever(mMediaSourceObserver);
        mPlaybackViewModel.getMetadata().observeForever(mMetadataObserver);
        mPlaybackViewModel.getMediaSourceColors().observeForever(mMediaSourceColorObserver);
        mPlaybackViewModel.getProgress().observeForever(mProgressObserver);
        mPlaybackViewModel.getPlaybackController().observeForever(mPlaybackControllerObserver);
        mPlaybackViewModel.getPlaybackStateWrapper().observeForever(mPlaybackStateWrapperObserver);

        mSeekBarColor = mDefaultSeekBarColor = resources.getColor(
                com.android.car.carlauncher.R.color.seek_bar_color, null);
        mSeekBarMax = resources.getInteger(
                com.android.car.carlauncher.R.integer.optional_seekbar_max);
        mUseMediaSourceColor = resources.getBoolean(R.bool.use_media_source_color_for_seek_bar);
        mTimesSeparator = resources.getString(com.android.car.carlauncher.R.string.times_separator);
        mOnModelUpdateListener.onModelUpdate(/* model = */ this);

        updateModel(); // Make sure the name of the media source properly reflects the locale.
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        mSourceViewModel.getPrimaryMediaSource().removeObserver(mMediaSourceObserver);
        mPlaybackViewModel.getMetadata().removeObserver(mMetadataObserver);
        mPlaybackViewModel.getPlaybackStateWrapper().removeObserver(mPlaybackStateWrapperObserver);
    }

    @Override
    public Intent getIntent() {
        MediaSource mediaSource = getMediaSourceViewModel().getPrimaryMediaSource().getValue();
        Intent intent = new Intent(CarMediaIntents.ACTION_MEDIA_TEMPLATE);
        if (mediaSource != null) {
            intent.putExtra(EXTRA_MEDIA_COMPONENT,
                    mediaSource.getBrowseServiceComponentName().flattenToString());
        }
        return intent;
    }

    @Override
    public void setOnModelUpdateListener(OnModelUpdateListener onModelUpdateListener) {
        mOnModelUpdateListener = onModelUpdateListener;
    }

    @Override
    public void setOnProgressUpdateListener(OnProgressUpdateListener onProgressUpdateListener) {
        mOnProgressUpdateListener = onProgressUpdateListener;
    }

    @Override
    public CardHeader getCardHeader() {
        return mCardHeader;
    }

    @Override
    public CardContent getCardContent() {
        return new DescriptiveTextWithControlsView(
                new CardContent.CardBackgroundImage(mAlbumImageBitmap, mMediaBackground),
                mSongTitle,
                mArtistName,
                new SeekBarViewModel(
                        mTimes,
                        mIsSeekEnabled,
                        mSeekBarColor,
                        mProgress,
                        mPlaybackCallback)
        );

    }

    /**
     * Allows the {@link HomeAudioCardPresenter} to access the model to
     * initialize the {@link com.android.car.media.common.PlaybackControlsActionBar}
     */
    public PlaybackViewModel getPlaybackViewModel() {
        return mPlaybackViewModel;
    }

    protected MediaSourceViewModel getMediaSourceViewModel() {
        return mSourceViewModel;
    }

    /**
     * Callback for the observer of the MediaSourceViewModel
     */
    private void updateModel() {
        MediaSource mediaSource = mSourceViewModel.getPrimaryMediaSource().getValue();
        if (mediaSourceChanged()) {
            if (mediaSource != null) {
                if (Log.isLoggable(TAG, Log.INFO)) {
                    Log.i(TAG, "Setting Media view to source "
                            + mediaSource.getDisplayName(mContext));
                }
                mAppName = mediaSource.getDisplayName(mContext);
                mAppIcon = mediaSource.getIcon();
                mCardHeader = new CardHeader(mAppName, mAppIcon);
                updateMetadata();
                updateProgress();
                updateMediaSourceColor();

                mOnModelUpdateListener.onModelUpdate(/* model = */ this);
            } else {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Not resetting media widget for apps "
                            + "that do not support media browse");
                }
            }
        }
    }

    /**
     * Callback for the observer of the PlaybackViewModel
     */
    private void updateModelMetadata() {
        if (metadataChanged()) {
            updateMetadata();
            if (mCardHeader != null) {
                mOnModelUpdateListener.onModelUpdate(/* model = */ this);
            }
        }
    }

    private void updateMediaSourceColor() {
        MediaSourceColors mediaSourceColors = mPlaybackViewModel.getMediaSourceColors().getValue();
        mSeekBarColor = (mediaSourceColors == null || !mUseMediaSourceColor)
                ? mDefaultSeekBarColor
                : mediaSourceColors.getAccentColor(mDefaultSeekBarColor);
        mOnProgressUpdateListener.onProgressUpdate(/* model = */ this, /* updateProgress = */
                false);
    }

    private void updateProgress() {
        PlaybackProgress playbackProgress = mPlaybackViewModel.getProgress().getValue();
        if (playbackProgress == null) {
            return;
        }
        mTimes = playbackProgress.hasTime() ? new StringBuilder(
                playbackProgress.getCurrentTimeText()).append(
                mTimesSeparator).append(playbackProgress.getMaxTimeText()).toString() : EMPTY_TIME;
        mRealMaxProgress = playbackProgress.getMaxProgress();
        int progress = playbackProgress.getProgressFraction() < 0 ? 0
                : (int) (mSeekBarMax * playbackProgress.getProgressFraction());
        if (mProgress != progress) {
            mProgress = progress;
            mOnProgressUpdateListener.onProgressUpdate(/* model = */ this, /* updateProgress = */
                    true);
        }
    }

    private void updateMetadata() {
        MediaItemMetadata metadata = mPlaybackViewModel.getMetadata().getValue();
        if (metadata == null) {
            clearMetadata();
        } else {
            mSongTitle = metadata.getTitle();
            mArtistName = metadata.getSubtitle();
            mAlbumArtBinder.setImage(mContext, metadata.getArtworkKey());
        }
    }

    private void updatePlaybackController() {
        mPlaybackController = mPlaybackViewModel.getPlaybackController().getValue();
    }

    private void clearMetadata() {
        mSongTitle = mContext.getString(R.string.default_media_song_title);
        mArtistName = null;
        mAlbumArtBinder.setImage(mContext, /* newArtRef = */ null);
    }

    /**
     * Helper method to check for a change in the media's metadata
     */
    private boolean metadataChanged() {
        MediaItemMetadata metadata = mPlaybackViewModel.getMetadata().getValue();
        if (metadata == null && (mSongTitle != null || mArtistName != null)) {
            return true;
        }
        if (metadata != null && (mSongTitle != metadata.getTitle()
                || mArtistName != metadata.getSubtitle())) {
            return true;
        }
        return false;
    }

    /**
     * Helper method to check for a change in the media source
     */
    private boolean mediaSourceChanged() {
        MediaSource mediaSource = mSourceViewModel.getPrimaryMediaSource().getValue();
        if (mediaSource == null && (mAppName != null || mAppIcon != null)) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "new media source is null...");
            }
            return true;
        }
        if (mediaSource != null && (mAppName != mediaSource.getDisplayName(mContext)
                || mAppIcon != mediaSource.getIcon())) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "new media source is " + mediaSource.toString());
            }
            return true;
        }
        return false;
    }

    /**
     * Callback for {@link com.android.car.carlauncher.homescreen.HomeCardFragment} pass the seekbar
     * info back.
     */
    public interface PlaybackCallback {
        /**
         * Moves to a new location in the media stream
         */
        void seekTo(int pos);
    }
}

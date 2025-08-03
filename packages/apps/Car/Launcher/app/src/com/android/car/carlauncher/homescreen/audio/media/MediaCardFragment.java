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

package com.android.car.carlauncher.homescreen.audio.media;

import static android.graphics.Shader.TileMode.MIRROR;

import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.RenderEffect;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.Chronometer;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import com.android.car.apps.common.BitmapUtils;
import com.android.car.carlauncher.R;
import com.android.car.carlauncher.homescreen.HomeCardFragment;
import com.android.car.carlauncher.homescreen.audio.MediaViewModel;
import com.android.car.carlauncher.homescreen.ui.CardContent;
import com.android.car.carlauncher.homescreen.ui.DescriptiveTextWithControlsView;
import com.android.car.carlauncher.homescreen.ui.SeekBarViewModel;
import com.android.car.media.common.PlaybackControlsActionBar;

/**
 * {@link HomeCardInterface.View} for the media audio card. Displays and controls the current
 * audio source such as the currently playing (or last played) media item.
 */
public class MediaCardFragment extends HomeCardFragment {

    /**
     * Interface definition for a callback to be invoked when a media layout is inflated.
     */
    public interface OnMediaViewInitializedListener {

        /**
         * Called when a media layout is inflated.
         */
        void onMediaViewInitialized();
    }

    private static final String TAG = MediaCardFragment.class.getSimpleName();

    private Chronometer mChronometer;
    private View mChronometerSeparator;
    private float mBlurRadius;
    private CardContent.CardBackgroundImage mDefaultCardBackgroundImage;

    // Views from card_content_media.xml, which is used only for the media card
    private View mMediaLayoutView;
    private View mMediaControlBarView;
    private TextView mMediaTitle;
    private TextView mMediaSubtitle;
    private ProgressBar mProgressBar;
    private SeekBar mSeekBar;
    private TextView mTimes;
    private ViewGroup mSeekBarWithTimesContainer;
    private int mSeekBarColor;
    private MediaViewModel.PlaybackCallback mPlaybackCallback;

    private boolean mShowSeekBar;
    private boolean mTrackingTouch;

    private OnMediaViewInitializedListener mOnMediaViewInitializedListener;

    private SeekBar.OnSeekBarChangeListener mOnSeekBarChangeListener =
            new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    mTrackingTouch = true;
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    if (mTrackingTouch && mPlaybackCallback != null) {
                        mPlaybackCallback.seekTo(seekBar.getProgress());
                    }
                    mTrackingTouch = false;
                }
            };

    private CardContent.CardBackgroundImage mCardBackgroundImage;
    private Size mMediaSize;
    private View.OnLayoutChangeListener mOnRootLayoutChangeListener =
            (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                boolean isWidthChanged = left - right != oldLeft - oldRight;
                boolean isHeightChanged = top - bottom != oldTop - oldBottom;
                boolean isSizeChanged = isWidthChanged || isHeightChanged;
                if (isSizeChanged) {
                    mMediaSize = new Size(right - left, bottom - top);
                    resizeCardBackgroundImage(mMediaSize);
                }
            };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBlurRadius = getResources().getFloat(R.dimen.card_background_image_blur_radius);
        mDefaultCardBackgroundImage = new CardContent.CardBackgroundImage(
                getContext().getDrawable(R.drawable.default_audio_background),
                getContext().getDrawable(R.drawable.control_bar_image_background));
        mShowSeekBar = getResources().getBoolean(R.bool.show_seek_bar);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        getRootView().addOnLayoutChangeListener(mOnRootLayoutChangeListener);
    }

    @Override
    public void updateContentViewInternal(CardContent content) {
        if (content.getType() == CardContent.HomeCardContentType.DESCRIPTIVE_TEXT_WITH_CONTROLS) {
            DescriptiveTextWithControlsView audioContent =
                    (DescriptiveTextWithControlsView) content;
            updateBackgroundImage(audioContent.getImage());
            if (audioContent.getCenterControl() == null) {
                updateMediaView(audioContent.getTitle(), audioContent.getSubtitle());
            } else {
                updateDescriptiveTextWithControlsView(audioContent.getTitle(),
                        audioContent.getSubtitle(),
                        /* optionalImage= */ null, audioContent.getLeftControl(),
                        audioContent.getCenterControl(), audioContent.getRightControl());
                updateAudioDuration(audioContent);
            }
            updateSeekBarAndTimes(audioContent.getSeekBarViewModel(), false);
        } else {
            super.updateContentViewInternal(content);
        }
    }

    @Override
    protected void hideAllViews() {
        super.hideAllViews();
        getCardBackground().setVisibility(View.GONE);
        getMediaLayoutView().setVisibility(View.GONE);
        getSeekbarWithTimesContainer().setVisibility(View.GONE);
    }

    private Chronometer getChronometer() {
        if (mChronometer == null) {
            mChronometer = getDescriptiveTextWithControlsLayoutView().findViewById(
                    R.id.optional_timer);
            mChronometerSeparator = getDescriptiveTextWithControlsLayoutView().findViewById(
                    R.id.optional_timer_separator);
        }
        return mChronometer;
    }

    private View getMediaLayoutView() {
        if (mMediaLayoutView == null) {
            ViewStub stub = getRootView().findViewById(R.id.media_layout);
            mMediaLayoutView = stub.inflate();
            mMediaTitle = mMediaLayoutView.findViewById(R.id.primary_text);
            mMediaSubtitle = mMediaLayoutView.findViewById(R.id.secondary_text);
            mMediaControlBarView = mMediaLayoutView.findViewById(
                    R.id.media_playback_controls_bar);
            mOnMediaViewInitializedListener.onMediaViewInitialized();
        }
        return mMediaLayoutView;
    }

    public PlaybackControlsActionBar getPlaybackControlsActionBar() {
        return (PlaybackControlsActionBar) mMediaControlBarView;
    }

    private void resizeCardBackgroundImage(Size cardSize) {
        if (mCardBackgroundImage == null || mCardBackgroundImage.getForeground() == null) {
            mCardBackgroundImage = mDefaultCardBackgroundImage;
        }
        int maxDimen = Math.max(getCardBackgroundImage().getWidth(),
                getCardBackgroundImage().getHeight());
        // Prioritize size of background image view. Otherwise, use size of whole card
        if (maxDimen == 0) {
            // This function may be called before a non-null cardSize is ready. Instead of waiting
            // for the next CardContent update to trigger resizeCardBackgroundImage(), resize the
            // card as soon as mOnRootChangeLayoutListener sets the mMediaSize.
            if (cardSize == null) {
                return;
            }
            maxDimen = Math.max(cardSize.getWidth(), cardSize.getHeight());
        }

        if (maxDimen == 0) {
            return;
        }
        Size scaledSize = new Size(maxDimen, maxDimen);

        Bitmap imageBitmap = BitmapUtils.fromDrawable(mCardBackgroundImage.getForeground(),
                scaledSize);
        RenderEffect blur = RenderEffect.createBlurEffect(mBlurRadius, mBlurRadius, MIRROR);
        getCardBackgroundImage().setRenderEffect(blur);

        if (mCardBackgroundImage.getBackground() != null) {
            getCardBackgroundImage().setBackground(mCardBackgroundImage.getBackground());
            getCardBackgroundImage().setClipToOutline(true);
        }
        getCardBackgroundImage().setImageBitmap(imageBitmap, /* showAnimation= */ true);
        getCardBackground().setVisibility(View.VISIBLE);
    }

    private void updateBackgroundImage(CardContent.CardBackgroundImage cardBackgroundImage) {
        mCardBackgroundImage = cardBackgroundImage;
        resizeCardBackgroundImage(mMediaSize);
    }

    private void updateMediaView(CharSequence title, CharSequence subtitle) {
        getMediaLayoutView().setVisibility(View.VISIBLE);
        mMediaTitle.setText(title);
        mMediaSubtitle.setText(subtitle);
        mMediaSubtitle.setVisibility(TextUtils.isEmpty(subtitle) ? View.GONE : View.VISIBLE);
        if (getSeekbarWithTimesContainer() != null) {
            getSeekbarWithTimesContainer().setVisibility(
                    mShowSeekBar ? View.VISIBLE : View.GONE);
        }
    }

    private void updateAudioDuration(DescriptiveTextWithControlsView content) {
        if (content.getStartTime() > 0) {
            getChronometer().setVisibility(View.VISIBLE);
            getChronometer().setBase(content.getStartTime());
            getChronometer().start();
            mChronometerSeparator.setVisibility(View.VISIBLE);
        } else {
            getChronometer().setVisibility(View.GONE);
            mChronometerSeparator.setVisibility(View.GONE);
        }
    }

    public void setOnMediaViewInitializedListener(
            OnMediaViewInitializedListener onMediaViewInitializedListener) {
        mOnMediaViewInitializedListener = onMediaViewInitializedListener;
    }

    /**
     * Updates the seekbar/progress bar progress and times
     */
    public void updateProgress(SeekBarViewModel seekBarViewModel, boolean updateProgress) {
        FragmentActivity activity = getActivity();
        if (activity == null) {
            Log.w(TAG, "attempting to update progress without activity");
            return;
        }
        activity.runOnUiThread(() -> {
            updateSeekBarAndTimes(seekBarViewModel, updateProgress);
        });
    }

    private void updateSeekBarAndTimes(SeekBarViewModel seekBarViewModel, boolean updateProgress) {
        if (!isSeekbarWithTimesAvailable() || seekBarViewModel == null) {
            return;
        }

        boolean shouldUseSeekBar = seekBarViewModel.isSeekEnabled();

        if (updateProgress) {
            ProgressBar progressBar = shouldUseSeekBar ? getSeekBar()
                    : getProgressBar();
            progressBar.setProgress(seekBarViewModel.getProgress(), /* animate = */ true);
        } else {
            SeekBar seekBar = getSeekBar();
            ProgressBar progressBar = getProgressBar();
            if (shouldUseSeekBar) {
                updateSeekBar(seekBar, seekBarViewModel);
            } else {
                updateProgressBar(progressBar, seekBarViewModel);
            }
            seekBar.setVisibility(shouldUseSeekBar ? View.VISIBLE : View.GONE);
            progressBar.setVisibility(shouldUseSeekBar ? View.GONE : View.VISIBLE);
        }

        if (seekBarViewModel.getTimes() == null || seekBarViewModel.getTimes().length() == 0) {
            getTimes().setVisibility(View.GONE);
        } else {
            getTimes().setText(seekBarViewModel.getTimes());
            getTimes().setVisibility(View.VISIBLE);
        }
    }

    private void updateProgressBar(ProgressBar progressBar, SeekBarViewModel seekBarViewModel) {
        if (mSeekBarColor != seekBarViewModel.getSeekBarColor()) {
            mSeekBarColor = seekBarViewModel.getSeekBarColor();
            progressBar.setProgressTintList(ColorStateList.valueOf(mSeekBarColor));
        }
        progressBar.setProgress(seekBarViewModel.getProgress(), /* animate = */ true);
    }

    private void updateSeekBar(SeekBar seekBar, SeekBarViewModel seekBarViewModel) {
        mPlaybackCallback = seekBarViewModel.getPlaybackCallback();
        if (mSeekBarColor != seekBarViewModel.getSeekBarColor()) {
            mSeekBarColor = seekBarViewModel.getSeekBarColor();
            seekBar.setThumbTintList(ColorStateList.valueOf(mSeekBarColor));
            seekBar.setProgressTintList(ColorStateList.valueOf(mSeekBarColor));
        }
        seekBar.setProgress(seekBarViewModel.getProgress(), /* animate = */ true);
    }

    private boolean isSeekbarWithTimesAvailable() {
        return (getSeekbarWithTimesContainer() != null
                && getSeekbarWithTimesContainer().getVisibility() == View.VISIBLE)
                && getSeekBar() != null
                && getTimes() != null;
    }

    private ProgressBar getProgressBar() {
        if (mProgressBar == null) {
            mProgressBar = getRootView().findViewById(R.id.optional_progress_bar);
        }
        return mProgressBar;
    }

    private SeekBar getSeekBar() {
        if (mSeekBar == null) {
            mSeekBar = getRootView().findViewById(R.id.optional_seek_bar);
            mSeekBar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
        }
        return mSeekBar;
    }

    private TextView getTimes() {
        if (mTimes == null) {
            mTimes = getRootView().findViewById(R.id.optional_times);
        }
        return mTimes;
    }

    protected ViewGroup getSeekbarWithTimesContainer() {
        if (mSeekBarWithTimesContainer == null) {
            mSeekBarWithTimesContainer = getRootView().findViewById(
                    R.id.optional_seek_bar_with_times_container);
        }
        return mSeekBarWithTimesContainer;
    }
}

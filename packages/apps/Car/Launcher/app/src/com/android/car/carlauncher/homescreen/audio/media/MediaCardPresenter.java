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

import android.content.Intent;

import androidx.annotation.VisibleForTesting;

import com.android.car.carlauncher.homescreen.CardPresenter;
import com.android.car.carlauncher.homescreen.HomeCardFragment;
import com.android.car.carlauncher.homescreen.HomeCardFragment.OnViewLifecycleChangeListener;
import com.android.car.carlauncher.homescreen.HomeCardInterface;
import com.android.car.carlauncher.homescreen.audio.AudioModel;
import com.android.car.carlauncher.homescreen.audio.MediaViewModel;
import com.android.car.carlauncher.homescreen.ui.CardContent;
import com.android.car.carlauncher.homescreen.ui.CardHeader;
import com.android.car.carlauncher.homescreen.ui.DescriptiveTextWithControlsView;

import java.util.List;

/**
 * A portrait UI version of {@link MediaCardPresenter}
 */
public class MediaCardPresenter extends CardPresenter {

    public final MediaIntentRouter mMediaIntentRouter = MediaIntentRouter.getInstance();

    private MediaViewModel mViewModel;
    private MediaCardFragment mFragment;
    private boolean mShowMedia = true;
    private CardContent mCachedCardContent;
    private CardHeader mCachedCardHeader;

    private final HomeCardFragment.OnViewClickListener mOnViewClickListener =
            new HomeCardFragment.OnViewClickListener() {
                @Override
                public void onViewClicked() {
                    Intent intent = mViewModel.getIntent();
                    mMediaIntentRouter.handleMediaIntent(intent);
                }
            };

    @VisibleForTesting
    final HomeCardInterface.Model.OnModelUpdateListener mOnMediaModelUpdateListener =
            new HomeCardInterface.Model.OnModelUpdateListener() {
                @Override
                public void onModelUpdate(HomeCardInterface.Model model) {
                    MediaViewModel mediaViewModel = (MediaViewModel) model;
                    if (mShowMedia) {
                        if (mediaViewModel.getCardHeader() != null) {
                            mFragment.updateHeaderView(mViewModel.getCardHeader());
                        }
                        if (mediaViewModel.getCardContent() != null) {
                            mFragment.updateContentView(mViewModel.getCardContent());
                        }
                    } else {
                        if (mediaViewModel.getCardHeader() != null) {
                            mCachedCardHeader = mViewModel.getCardHeader();
                        }
                        if (mediaViewModel.getCardContent() != null) {
                            mCachedCardContent = mViewModel.getCardContent();
                        }
                    }
                }
            };

    private final HomeCardFragment.OnViewLifecycleChangeListener
            mOnMediaViewLifecycleChangeListener =
            new OnViewLifecycleChangeListener() {
                @Override
                public void onViewCreated() {
                    mViewModel.setOnProgressUpdateListener(mOnMediaProgressUpdateListener);
                    mViewModel.setOnModelUpdateListener(mOnMediaModelUpdateListener);
                    mViewModel.onCreate(getFragment().requireContext());
                }

                @Override
                public void onViewDestroyed() {
                    mViewModel.onDestroy(getFragment().requireContext());
                }
            };

    private final MediaCardFragment.OnMediaViewInitializedListener mOnMediaViewInitializedListener =
            new MediaCardFragment.OnMediaViewInitializedListener() {
                @Override
                public void onMediaViewInitialized() {
                    mFragment.getPlaybackControlsActionBar().setModel(
                            mViewModel.getPlaybackViewModel(),
                            mFragment.getViewLifecycleOwner());
                }
            };

    private final AudioModel.OnProgressUpdateListener mOnMediaProgressUpdateListener =
            new AudioModel.OnProgressUpdateListener() {
                @Override
                public void onProgressUpdate(AudioModel model, boolean updateProgress) {
                    if (model == null || model.getCardContent() == null
                            || model.getCardHeader() == null) {
                        return;
                    }
                    DescriptiveTextWithControlsView descriptiveTextWithControlsContent =
                            (DescriptiveTextWithControlsView) model.getCardContent();
                    mFragment.updateProgress(
                            descriptiveTextWithControlsContent.getSeekBarViewModel(),
                            updateProgress);
                }
            };

    /** Informs this presenter whether or not to process model updates */
    public void setShowMedia(boolean shouldShowMedia) {
        mShowMedia = shouldShowMedia;
        if (shouldShowMedia) {
            updateFragmentWithCachedContent();
        }
    }

    // Deprecated. Use setModel instead.
    @Override
    public void setModels(List<HomeCardInterface.Model> models) {
        // No-op
    }

    public void setModel(MediaViewModel viewModel) {
        mViewModel = viewModel;
    }

    @Override
    public void setView(HomeCardInterface.View view) {
        super.setView(view);

        mFragment = (MediaCardFragment) view;
        mFragment.setOnViewLifecycleChangeListener(mOnMediaViewLifecycleChangeListener);
        mFragment.setOnViewClickListener(mOnViewClickListener);
        mFragment.setOnMediaViewInitializedListener(mOnMediaViewInitializedListener);
    }

    private void updateFragmentWithCachedContent() {
        if (mCachedCardHeader != null) {
            mFragment.updateHeaderView(mCachedCardHeader);
            mCachedCardHeader = null;
        }
        if (mCachedCardContent != null) {
            mFragment.updateContentView(mCachedCardContent);
            mCachedCardContent = null;
        }
    }
}

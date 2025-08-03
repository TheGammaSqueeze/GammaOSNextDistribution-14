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
package com.android.car.carlauncher.homescreen.audio.dialer;

import static android.graphics.Shader.TileMode.MIRROR;

import android.graphics.Bitmap;
import android.graphics.RenderEffect;
import android.os.Bundle;
import android.util.Size;
import android.view.View;
import android.widget.Chronometer;

import com.android.car.apps.common.BitmapUtils;
import com.android.car.carlauncher.R;
import com.android.car.carlauncher.homescreen.HomeCardFragment;
import com.android.car.carlauncher.homescreen.ui.CardContent;
import com.android.car.carlauncher.homescreen.ui.DescriptiveTextWithControlsView;

/** A fragment for in-call controls. Displays and controls an ongoing phone call */
public class DialerCardFragment extends HomeCardFragment {
    private Chronometer mChronometer;
    private View mChronometerSeparator;
    private float mBlurRadius;
    private CardContent.CardBackgroundImage mDefaultCardBackgroundImage;
    private CardContent.CardBackgroundImage mCardBackgroundImage;
    private Size mDialerSize;
    private View.OnLayoutChangeListener mOnRootLayoutChangeListener =
            (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                boolean isWidthChanged = left - right != oldLeft - oldRight;
                boolean isHeightChanged = top - bottom != oldTop - oldBottom;
                boolean isSizeChanged = isWidthChanged || isHeightChanged;
                if (isSizeChanged) {
                    mDialerSize = new Size(right - left, bottom - top);
                    resizeCardBackgroundImage(mDialerSize);
                }
            };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBlurRadius = getResources().getFloat(R.dimen.card_background_image_blur_radius);
        mDefaultCardBackgroundImage = new CardContent.CardBackgroundImage(
                getContext().getDrawable(R.drawable.default_audio_background),
                getContext().getDrawable(R.drawable.control_bar_image_background));
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        getRootView().addOnLayoutChangeListener(mOnRootLayoutChangeListener);
        getDescriptiveTextWithControlsLayoutView();
        getRootView().setVisibility(View.VISIBLE);
        getRootView().findViewById(R.id.optional_seek_bar_with_times_container).setVisibility(
                View.GONE);

    }

    @Override
    public void updateContentViewInternal(CardContent content) {
        if (content.getType() == CardContent.HomeCardContentType.DESCRIPTIVE_TEXT_WITH_CONTROLS) {
            DescriptiveTextWithControlsView audioContent =
                    (DescriptiveTextWithControlsView) content;
            updateBackgroundImage(audioContent.getImage());
            updateDescriptiveTextWithControlsView(audioContent.getTitle(),
                    audioContent.getSubtitle(),
                    /* optionalImage= */ null, audioContent.getLeftControl(),
                    audioContent.getCenterControl(), audioContent.getRightControl());
            updateAudioDuration(audioContent);
        } else {
            super.updateContentViewInternal(content);
        }
    }

    @Override
    protected void hideAllViews() {
        super.hideAllViews();
        getCardBackground().setVisibility(View.GONE);
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
            // card as soon as mOnRootChangeLayoutListener sets the mDialerSize.
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
        resizeCardBackgroundImage(mDialerSize);
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
}


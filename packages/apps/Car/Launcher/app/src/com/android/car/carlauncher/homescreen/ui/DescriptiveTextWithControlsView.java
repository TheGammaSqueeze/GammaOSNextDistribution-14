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

package com.android.car.carlauncher.homescreen.ui;

import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * A layout that displays a title line of text with a subtitle line below and three buttons to
 * control the audio. Displayed using card_content_descriptive_text_with_controls.xml
 */
public class DescriptiveTextWithControlsView extends CardContent {

    private CardBackgroundImage mImage;
    private CharSequence mTitle;
    private CharSequence mSubtitle;
    private Control mLeftControl;
    private Control mCenterControl;
    private Control mRightControl;
    private long mStartTime;
    private SeekBarViewModel mSeekBarViewModel;

    //TODO(b/260002616): considering using builder
    public DescriptiveTextWithControlsView(CardBackgroundImage image, CharSequence title,
            CharSequence subtitle) {
        mImage = image;
        mTitle = title;
        mSubtitle = subtitle;
    }

    public DescriptiveTextWithControlsView(CardBackgroundImage image, CharSequence title,
            CharSequence subtitle, SeekBarViewModel seekBarViewModel) {
        mImage = image;
        mTitle = title;
        mSubtitle = subtitle;
        mSeekBarViewModel = seekBarViewModel;
    }

    public DescriptiveTextWithControlsView(CardBackgroundImage image, CharSequence title,
            CharSequence subtitle, Control leftControl, Control centerControl,
            Control rightControl) {
        mImage = image;
        mTitle = title;
        mSubtitle = subtitle;
        mLeftControl = leftControl;
        mCenterControl = centerControl;
        mRightControl = rightControl;
    }

    public DescriptiveTextWithControlsView(CardBackgroundImage image, CharSequence title,
            CharSequence subtitle, long startTime, Control leftControl,
            Control centerControl, Control rightControl) {
        mImage = image;
        mTitle = title;
        mSubtitle = subtitle;
        mStartTime = startTime;
        mLeftControl = leftControl;
        mCenterControl = centerControl;
        mRightControl = rightControl;
    }

    @Override
    public HomeCardContentType getType() {
        return HomeCardContentType.DESCRIPTIVE_TEXT_WITH_CONTROLS;
    }

    public CardBackgroundImage getImage() {
        return mImage;
    }

    public CharSequence getTitle() {
        return mTitle;
    }

    public CharSequence getSubtitle() {
        return mSubtitle;
    }

    public long getStartTime() {
        return mStartTime;
    }

    public Control getLeftControl() {
        return mLeftControl;
    }

    public Control getCenterControl() {
        return mCenterControl;
    }

    public Control getRightControl() {
        return mRightControl;
    }

    public SeekBarViewModel getSeekBarViewModel() {
        return mSeekBarViewModel;
    }

    /**
     * A button shown with the DescriptiveTextWithControlsView that has a {@link Drawable} icon
     * used as the button's image and an {@link android.view.View.OnClickListener} that defines the
     * action when the button is clicked.
     */
    public static class Control {

        private Drawable mIcon;
        private View.OnClickListener mOnClickListener;

        public Control(Drawable icon, View.OnClickListener listener) {
            mIcon = icon;
            mOnClickListener = listener;
        }

        public Drawable getIcon() {
            return mIcon;
        }

        public View.OnClickListener getOnClickListener() {
            return mOnClickListener;
        }
    }
}

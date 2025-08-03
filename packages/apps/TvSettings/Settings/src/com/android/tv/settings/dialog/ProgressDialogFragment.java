/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.tv.settings.dialog;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.android.tv.settings.R;

public class ProgressDialogFragment extends Fragment {

    private ImageView mIconView;
    private TextView mTitleView;
    private TextView mExtraTextView;
    private TextView mSummaryView;
    private ProgressBar mProgressBar;
    private int mWidth = -1;

    @Override
    public @Nullable View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            Bundle savedInstanceState) {
        final ViewGroup view =
                (ViewGroup) inflater.inflate(R.layout.progress_fragment, container, false);

        mIconView = view.findViewById(android.R.id.icon);
        mTitleView = view.findViewById(android.R.id.title);
        mProgressBar = view.findViewById(android.R.id.progress);
        mSummaryView = view.findViewById(android.R.id.summary);
        mExtraTextView = view.findViewById(R.id.extra);

        if (mWidth != -1) {
            final ViewGroup.LayoutParams params = view.getLayoutParams();
            params.width = mWidth;
            view.setLayoutParams(params);
        }

        return view;
    }

    public void setIcon(@DrawableRes int resId) {
        mIconView.setImageResource(resId);
        mIconView.setVisibility(View.VISIBLE);
    }

    public void setIcon(@Nullable Drawable icon) {
        mIconView.setImageDrawable(icon);
        mIconView.setVisibility(icon == null ? View.GONE : View.VISIBLE);
    }

    public void setTitle(@StringRes int resId) {
        mTitleView.setText(resId);
    }

    public void setTitle(CharSequence title) {
        mTitleView.setText(title);
    }

    public void setExtraText(@StringRes int resId) {
        mExtraTextView.setText(resId);
    }

    public void setExtraText(CharSequence text) {
        mExtraTextView.setText(text);
        mExtraTextView.setVisibility(TextUtils.isEmpty(text) ? View.GONE : View.VISIBLE);
    }

    /**
     * This is helpful in setting the content description to be used by accessibility feature such
     * as Talkback.
     */
    public void setExtraContentDescription(CharSequence text) {
        mExtraTextView.setContentDescription(text);
    }

    public void setSummary(@StringRes int resId) {
        mSummaryView.setText(resId);
    }

    public void setSummary(CharSequence summary) {
        mSummaryView.setText(summary);
    }

    public void setIndeterminte(boolean indeterminte) {
        mProgressBar.setIndeterminate(indeterminte);
    }

    public void setProgress(int progress) {
        mProgressBar.setProgress(progress);
    }

    public void setProgressMax(int max) {
        mProgressBar.setMax(max);
    }

    public void setContentWidth(int width) {
        mWidth = width;
        final View root = getView();
        if (root == null) {
            return;
        }
        final ViewGroup.LayoutParams params = root.getLayoutParams();
        params.width = width;
        root.setLayoutParams(params);
    }

    /**
     * Sets customized line spacing
     * @param multiplier line spacing multiplier
     */
    public void setDescriptionLineSpacingMultiplier(float multiplier) {
        if (getResources() != null) {
            if (mSummaryView != null) {
                mSummaryView.setLineSpacing(mSummaryView.getLineSpacingExtra(), multiplier);
            }
            if (mExtraTextView != null) {
                mExtraTextView.setLineSpacing(mExtraTextView.getLineSpacingExtra(), multiplier);
            }
        }
    }
}

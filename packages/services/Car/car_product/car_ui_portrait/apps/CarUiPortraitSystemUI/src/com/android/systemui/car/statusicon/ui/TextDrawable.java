/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.car.statusicon.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.systemui.R;

/**
 * A simple {@link Drawable} that draws a text with given width and height in the provided layout.
 *
 * The layout should contain a textView with id {@code text}. The style of the text is defined by
 * given layout.
 */
public class TextDrawable extends Drawable {
    private final View mView;
    private final TextView mTextView;
    private final int mWidth;
    private final int mHeight;

    public TextDrawable(final Context context, @LayoutRes final int layoutId, String string,
            int width, int height) {
        mWidth = width;
        mHeight = height;
        mView = LayoutInflater.from(context).inflate(layoutId, null);
        mTextView = mView.findViewById(R.id.text);
        if (mTextView != null) {
            mTextView.setText(string);
        }
    }

    @Override
    public void setBounds(int left, int top, int right, int bottom) {
        if (mTextView == null) {
            return;
        }
        super.setBounds(left, top, left + mWidth, top + mHeight);
        mView.measure(mWidth, mHeight);
        mView.layout(left, top, left + mWidth, top + mHeight);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        mView.draw(canvas);
    }

    @Override
    public void setAlpha(int alpha) {
        mView.setAlpha(alpha / 255f);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}



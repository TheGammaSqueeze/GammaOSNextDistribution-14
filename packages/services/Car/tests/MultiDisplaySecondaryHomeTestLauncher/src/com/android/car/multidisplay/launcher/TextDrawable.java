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
package com.android.car.multidisplay.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.TypedValue;

import java.io.PrintWriter;
import java.util.Arrays;

public final class TextDrawable extends Drawable {

    private static final String TAG = TextDrawable.class.getSimpleName();

    private final Paint mPaint;
    private final CharSequence[] mText;
    private final int mIntrinsicWidth;
    private final int mIntrinsicHeight;

    // Attributes below are used by dump only
    private final int mColor;
    private final int mDefaultSize;
    private final float mTextSize;

    public TextDrawable(Context context, int color, int defaultSize, CharSequence... text) {
        mColor = color;
        mText = text;
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setColor(mColor);
        mPaint.setTextAlign(Align.CENTER);
        mDefaultSize = defaultSize;
        mTextSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                mDefaultSize, context.getResources().getDisplayMetrics());
        mPaint.setTextSize(mTextSize);
        int maxWidth = 0;
        for (int i = 0; i < text.length; i++) {
            CharSequence line = text[i];
            int width = (int) (mPaint.measureText(line, 0, line.length()) + .5);
            maxWidth = Math.max(maxWidth, width);
            Log.d(TAG, "line " + i + ": text='" + line + ", w=" + width + " max=" + maxWidth);
        }
        mIntrinsicWidth = maxWidth;
        mIntrinsicHeight = mPaint.getFontMetricsInt(/* fmi= */ null);
    }

    @Override
    public void setAlpha(int alpha) {
        mPaint.setAlpha(alpha);
    }

    @Override
    public int getOpacity() {
        return mPaint.getAlpha();
    }

    @Override
    public int getIntrinsicWidth() {
        return mIntrinsicWidth;
    }

    @Override
    public int getIntrinsicHeight() {
        return mIntrinsicHeight;
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
        mPaint.setColorFilter(filter);
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        int height = bounds.height();
        int topMargin = bounds.centerY() - (mIntrinsicHeight / 2);
        int n = mText.length;
        int x = bounds.centerX();
        int y = topMargin;
        Log.d(TAG, "Drawing " + n + " lines. Height: " + height + " Top margin: " + topMargin);
        for (int i = 0; i < n; i++) {
            CharSequence text = mText[i];
            Log.d(TAG, "Drawing line " + i + " (" + text + ") at " + x + "x" + y);
            canvas.drawText(text, 0, text.length(), x, y, mPaint);
            y += mIntrinsicHeight;
        }
    }

    public void dump(String prefix, PrintWriter writer) {
        writer.printf("%sClass name: %s\n", prefix, getClass().getName());
        writer.printf("%smText: %s\n", prefix, Arrays.toString(mText));
        writer.printf("%smTextSize: %.02f\n", prefix, mTextSize);
        writer.printf("%smDefaultSize: %d\n", prefix, mDefaultSize);
        writer.printf("%smColor: %d\n", prefix, mColor);
        writer.printf("%smIntrinsicWidth: %d\n", prefix, mIntrinsicWidth);
        writer.printf("%smIntrinsicHeight: %d\n", prefix, mIntrinsicHeight);
        writer.printf("%smPaint: %s\n", prefix, mPaint);
    }
}

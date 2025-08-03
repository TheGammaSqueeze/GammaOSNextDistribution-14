/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.intentresolver.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.widget.ImageView;

import com.android.intentresolver.R;

/**
 * {@link ImageView} that rounds the corners around the presented image while obeying view padding.
 */
public class RoundedRectImageView extends ImageView {
    private int mRadius = 0;
    private Path mPath = new Path();
    private Paint mOverlayPaint = new Paint(0);
    private Paint mRoundRectPaint = new Paint(0);
    private Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String mExtraImageCount = null;

    public RoundedRectImageView(Context context) {
        super(context);
    }

    public RoundedRectImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RoundedRectImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public RoundedRectImageView(
            Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        final TypedArray a = context.obtainStyledAttributes(
                attrs,
                R.styleable.RoundedRectImageView,
                defStyleAttr,
                0);
        mRadius = a.getDimensionPixelSize(R.styleable.RoundedRectImageView_radius, -1);
        if (mRadius < 0) {
            mRadius = context.getResources().getDimensionPixelSize(R.dimen.chooser_corner_radius);
        }
        a.recycle();

        mOverlayPaint.setColor(0x99000000);
        mOverlayPaint.setStyle(Paint.Style.FILL);

        mRoundRectPaint.setColor(context.getResources().getColor(R.color.chooser_row_divider));
        mRoundRectPaint.setStyle(Paint.Style.STROKE);
        mRoundRectPaint.setStrokeWidth(context.getResources()
                .getDimensionPixelSize(R.dimen.chooser_preview_image_border));

        mTextPaint.setColor(Color.WHITE);
        mTextPaint.setTextSize(context.getResources()
                .getDimensionPixelSize(R.dimen.chooser_preview_image_font_size));
        mTextPaint.setTextAlign(Paint.Align.CENTER);
    }

    private void updatePath(int width, int height) {
        mPath.reset();

        int imageWidth = width - getPaddingRight() - getPaddingLeft();
        int imageHeight = height - getPaddingBottom() - getPaddingTop();
        mPath.addRoundRect(getPaddingLeft(), getPaddingTop(), imageWidth, imageHeight, mRadius,
                mRadius, Path.Direction.CW);
    }

    /**
      * Sets the corner radius on all corners
      *
      * param radius 0 for no radius, &gt; 0 for a visible corner radius
      */
    public void setRadius(int radius) {
        mRadius = radius;
        updatePath(getWidth(), getHeight());
    }

    /**
      * Display an overlay with extra image count on 3rd image
      */
    public void setExtraImageCount(int count) {
        if (count > 0) {
            this.mExtraImageCount = "+" + count;
        } else {
            this.mExtraImageCount = null;
        }
        invalidate();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        updatePath(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mRadius != 0) {
            canvas.clipPath(mPath);
        }

        super.onDraw(canvas);

        int x = getPaddingLeft();
        int y = getPaddingRight();
        int width = getWidth() - getPaddingRight() - getPaddingLeft();
        int height = getHeight() - getPaddingBottom() - getPaddingTop();
        if (mExtraImageCount != null) {
            canvas.drawRect(x, y, width, height, mOverlayPaint);

            int xPos = canvas.getWidth() / 2;
            int yPos = (int) ((canvas.getHeight() / 2.0f)
                    - ((mTextPaint.descent() + mTextPaint.ascent()) / 2.0f));

            canvas.drawText(mExtraImageCount, xPos, yPos, mTextPaint);
        }

        canvas.drawRoundRect(x, y, width, height, mRadius, mRadius, mRoundRectPaint);
    }
}

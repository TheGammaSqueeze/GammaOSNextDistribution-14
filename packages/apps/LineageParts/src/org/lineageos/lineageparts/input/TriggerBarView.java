/*
 * GammaOS Trigger Bar Visualization View
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.lineageparts.input;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Custom View that draws a horizontal bar representing a trigger value (0.0 to 1.0).
 * Shows a filled portion proportional to the value and a numeric percentage.
 */
public class TriggerBarView extends View {

    private final Paint mBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();

    private float mValue = 0f; // 0.0 to 1.0

    public TriggerBarView(Context context) {
        super(context);
        init();
    }

    public TriggerBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TriggerBarView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mBgPaint.setStyle(Paint.Style.FILL);
        mBgPaint.setColor(0xFF333333);

        mFillPaint.setStyle(Paint.Style.FILL);
        mFillPaint.setColor(0xFF4CAF50);

        mTextPaint.setColor(0xFFFFFFFF);
        mTextPaint.setTextSize(28f);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
    }

    /**
     * Set the trigger value.
     * @param value Value from 0.0 to 1.0
     */
    public void setValue(float value) {
        mValue = Math.max(0f, Math.min(1f, value));
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (width == 0) width = 300;
        int height = 48;
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();
        float cornerRadius = h / 4f;

        // Background bar
        mRect.set(0, 0, w, h);
        canvas.drawRoundRect(mRect, cornerRadius, cornerRadius, mBgPaint);

        // Filled portion
        if (mValue > 0f) {
            mRect.set(0, 0, w * mValue, h);
            canvas.drawRoundRect(mRect, cornerRadius, cornerRadius, mFillPaint);
        }

        // Percentage text
        int pct = Math.round(mValue * 100f);
        String text = pct + "%";
        float textY = h / 2f - (mTextPaint.descent() + mTextPaint.ascent()) / 2f;
        canvas.drawText(text, w / 2f, textY, mTextPaint);
    }
}

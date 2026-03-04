/*
 * GammaOS Analog Stick Visualization View
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.lineageparts.input;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * Custom View that draws:
 * - A blue square grid representing the full digital output range
 * - A circle representing the analog stick range boundary
 * - A crosshair through center
 * - A filled dot at the current position
 * - An optional deadzone circle
 */
public class AnalogStickView extends View {

    private final Paint mGridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGridLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mCrosshairPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDeadzonePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float mX = 0f; // -1.0 to 1.0
    private float mY = 0f; // -1.0 to 1.0
    private float mDeadzoneRadius = 0f; // 0.0 to 1.0

    public AnalogStickView(Context context) {
        super(context);
        init();
    }

    public AnalogStickView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AnalogStickView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mGridPaint.setStyle(Paint.Style.STROKE);
        mGridPaint.setStrokeWidth(2f);
        mGridPaint.setColor(0xFF1565C0); // Blue 800

        mGridLinePaint.setStyle(Paint.Style.STROKE);
        mGridLinePaint.setStrokeWidth(1f);
        mGridLinePaint.setColor(0x441565C0); // Blue 800, translucent

        mCirclePaint.setStyle(Paint.Style.STROKE);
        mCirclePaint.setStrokeWidth(3f);
        mCirclePaint.setColor(0xFF888888);

        mCrosshairPaint.setStyle(Paint.Style.STROKE);
        mCrosshairPaint.setStrokeWidth(1f);
        mCrosshairPaint.setColor(0xFF555555);

        mDotPaint.setStyle(Paint.Style.FILL);
        mDotPaint.setColor(0xFF4CAF50);

        mDeadzonePaint.setStyle(Paint.Style.FILL);
        mDeadzonePaint.setColor(0x22FF0000);
    }

    /**
     * Set the current stick position.
     * @param x X position, -1.0 to 1.0
     * @param y Y position, -1.0 to 1.0
     */
    public void setPosition(float x, float y) {
        mX = Math.max(-1f, Math.min(1f, x));
        mY = Math.max(-1f, Math.min(1f, y));
        invalidate();
    }

    /**
     * Set the deadzone radius to display.
     * @param radius Deadzone radius, 0.0 to 1.0
     */
    public void setDeadzone(float radius) {
        mDeadzoneRadius = Math.max(0f, Math.min(1f, radius));
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = MeasureSpec.getSize(widthMeasureSpec);
        int hSize = MeasureSpec.getSize(heightMeasureSpec);
        int min = Math.min(size, hSize);
        if (min == 0) min = 200;
        setMeasuredDimension(min, min);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(cx, cy) - 8f;

        // Blue square grid (full digital output range)
        float left = cx - radius;
        float top = cy - radius;
        float right = cx + radius;
        float bottom = cy + radius;

        // Outer square border
        canvas.drawRect(left, top, right, bottom, mGridPaint);

        // Grid lines at 25% intervals (5 divisions each axis)
        float step = radius * 2f / 4f;
        for (int i = 1; i < 4; i++) {
            float offset = left + step * i;
            canvas.drawLine(offset, top, offset, bottom, mGridLinePaint);
            float yOffset = top + step * i;
            canvas.drawLine(left, yOffset, right, yOffset, mGridLinePaint);
        }

        // Deadzone circle
        if (mDeadzoneRadius > 0f) {
            canvas.drawCircle(cx, cy, radius * mDeadzoneRadius, mDeadzonePaint);
        }

        // Outer boundary circle
        canvas.drawCircle(cx, cy, radius, mCirclePaint);

        // Crosshair
        canvas.drawLine(cx - radius, cy, cx + radius, cy, mCrosshairPaint);
        canvas.drawLine(cx, cy - radius, cx, cy + radius, mCrosshairPaint);

        // Position dot
        float dotX = cx + mX * radius;
        float dotY = cy + mY * radius;
        canvas.drawCircle(dotX, dotY, 10f, mDotPaint);
    }
}

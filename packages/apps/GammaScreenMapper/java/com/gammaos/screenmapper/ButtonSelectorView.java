package com.gammaos.screenmapper;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

/**
 * Radial button selector overlay. Shows available buttons/sticks in a circle
 * around the mapping point. Sizes are responsive to screen dimensions.
 */
public class ButtonSelectorView extends View {

    public interface OnButtonSelectedListener {
        void onButtonSelected(ScreenMapConfig.MappingPoint.Type type, int buttonCode, String label);
        void onCancelled();
    }

    private static final int[][] BUTTON_DEFS = {
        { 0x130, 0 },  // A
        { 0x131, 0 },  // B
        { 0x133, 0 },  // X
        { 0x134, 0 },  // Y
        { 0x136, 0 },  // LB
        { 0x137, 0 },  // RB
        { 0x138, 0 },  // LT
        { 0x139, 0 },  // RT
        { 0x13d, 0 },  // L3
        { 0x13e, 0 },  // R3
        { 0x13b, 0 },  // Start
        { 0x13a, 0 },  // Select
        { 0, 1 },      // L-Stick
        { 0, 2 },      // R-Stick
        { 0, 3 },      // DPAD
    };

    private static final String[] BUTTON_LABELS = {
        "A", "B", "X", "Y", "LB", "RB", "LT", "RT",
        "L3", "R3", "Start", "Sel", "L", "R", "DPAD"
    };

    private final Paint mBgPaint;
    private final Paint mItemPaint;
    private final Paint mItemHighlightPaint;
    private final Paint mTextPaint;
    private final Paint mCancelPaint;

    private OnButtonSelectedListener mListener;
    private int mCenterX, mCenterY;
    private float mSelectorRadius;
    private float mItemRadius;
    private RectF[] mItemBounds;
    private int mHoverIndex = -1;

    /**
     * Compute the outer radius of the selector based on screen size.
     * Used by the editor to clamp the center position.
     */
    public static int computeRadius(Context context, int screenW, int screenH) {
        int shortEdge = Math.min(screenW, screenH);
        float density = context.getResources().getDisplayMetrics().density;
        // Selector radius = 35% of short edge — compact wheel, spacious buttons
        return Math.max((int) (100 * density), Math.min((int) (180 * density), shortEdge * 35 / 100));
    }

    public ButtonSelectorView(Context context, int centerX, int centerY,
                               int screenW, int screenH) {
        super(context);
        mCenterX = centerX;
        mCenterY = centerY;

        float density = context.getResources().getDisplayMetrics().density;
        int shortEdge = Math.min(screenW, screenH);

        // Responsive radius — smaller wheel, well-spaced items
        mSelectorRadius = computeRadius(context, screenW, screenH);
        // Item circles: small enough to not overlap, big enough to tap
        mItemRadius = mSelectorRadius * 0.16f;
        if (mItemRadius < 18 * density) mItemRadius = 18 * density;

        mBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBgPaint.setColor(Color.argb(200, 20, 20, 20));
        mBgPaint.setStyle(Paint.Style.FILL);

        mItemPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mItemPaint.setColor(Color.argb(220, 50, 50, 50));
        mItemPaint.setStyle(Paint.Style.FILL);

        mItemHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mItemHighlightPaint.setColor(Color.argb(220, 80, 120, 200));
        mItemHighlightPaint.setStyle(Paint.Style.FILL);

        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setColor(Color.WHITE);
        // Scale text to fit inside item circles
        mTextPaint.setTextSize(mItemRadius * 0.65f);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setTypeface(Typeface.DEFAULT_BOLD);

        mCancelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mCancelPaint.setColor(Color.argb(220, 120, 40, 40));
        mCancelPaint.setStyle(Paint.Style.FILL);
    }

    public void setOnButtonSelectedListener(OnButtonSelectedListener listener) {
        mListener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Background circle
        canvas.drawCircle(mCenterX, mCenterY, mSelectorRadius, mBgPaint);

        // Cancel button in center
        float cancelRadius = mItemRadius * 0.9f;
        canvas.drawCircle(mCenterX, mCenterY, cancelRadius, mCancelPaint);
        float cancelTextY = mCenterY - (mTextPaint.descent() + mTextPaint.ascent()) / 2;
        canvas.drawText("X", mCenterX, cancelTextY, mTextPaint);

        // Arrange buttons in a circle
        int count = BUTTON_DEFS.length;
        mItemBounds = new RectF[count];
        float ringRadius = mSelectorRadius * 0.68f;

        for (int i = 0; i < count; i++) {
            double angle = (2.0 * Math.PI * i / count) - Math.PI / 2;
            float ix = mCenterX + (float)(Math.cos(angle) * ringRadius);
            float iy = mCenterY + (float)(Math.sin(angle) * ringRadius);

            Paint paint = (i == mHoverIndex) ? mItemHighlightPaint : mItemPaint;
            canvas.drawCircle(ix, iy, mItemRadius, paint);

            float textY = iy - (mTextPaint.descent() + mTextPaint.ascent()) / 2;
            canvas.drawText(BUTTON_LABELS[i], ix, textY, mTextPaint);

            mItemBounds[i] = new RectF(
                ix - mItemRadius, iy - mItemRadius,
                ix + mItemRadius, iy + mItemRadius);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                mHoverIndex = -1;
                if (mItemBounds != null) {
                    for (int i = 0; i < mItemBounds.length; i++) {
                        if (mItemBounds[i].contains(x, y)) {
                            mHoverIndex = i;
                            break;
                        }
                    }
                }
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
                // Check cancel (center)
                float dx = x - mCenterX;
                float dy = y - mCenterY;
                float cancelRadius = mItemRadius * 0.9f;
                if (dx * dx + dy * dy < cancelRadius * cancelRadius) {
                    if (mListener != null) mListener.onCancelled();
                    return true;
                }

                // Check button items
                if (mItemBounds != null) {
                    for (int i = 0; i < mItemBounds.length; i++) {
                        if (mItemBounds[i].contains(x, y)) {
                            if (mListener != null) {
                                int[] def = BUTTON_DEFS[i];
                                ScreenMapConfig.MappingPoint.Type type;
                                switch (def[1]) {
                                    case 1: type = ScreenMapConfig.MappingPoint.Type.STICK_LEFT; break;
                                    case 2: type = ScreenMapConfig.MappingPoint.Type.STICK_RIGHT; break;
                                    case 3: type = ScreenMapConfig.MappingPoint.Type.DPAD; break;
                                    default: type = ScreenMapConfig.MappingPoint.Type.BUTTON; break;
                                }
                                mListener.onButtonSelected(type, def[0], BUTTON_LABELS[i]);
                            }
                            return true;
                        }
                    }
                }

                // Tap outside — cancel
                if (mListener != null) mListener.onCancelled();
                return true;
        }
        return true;
    }
}

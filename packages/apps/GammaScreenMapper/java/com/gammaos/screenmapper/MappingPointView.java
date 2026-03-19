package com.gammaos.screenmapper;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

public class MappingPointView extends View {

    private static final int[] BUTTON_COLORS = {
        0xCC2196F3, // A - blue
        0xCCF44336, // B - red
        0xCC4CAF50, // X - green
        0xCCFFEB3B, // Y - yellow
        0xCCFF9800, // LB - orange
        0xCC9C27B0, // RB - purple
        0xCC00BCD4, // LT - cyan
        0xCCE91E63, // RT - pink
        0xCC607D8B, // L3 - blue-grey
        0xCC795548, // R3 - brown
        0xCC3F51B5, // Start - indigo
        0xCC009688, // Select - teal
        0xCC8BC34A, // L-Stick - light green
        0xCCFF5722, // R-Stick - deep orange
        0xCCCDDC39, // DPAD - lime
    };

    private static int getColorForButton(int buttonCode, ScreenMapConfig.MappingPoint.Type type) {
        switch (type) {
            case STICK_LEFT: return BUTTON_COLORS[12];
            case STICK_RIGHT: return BUTTON_COLORS[13];
            case DPAD: return BUTTON_COLORS[14];
            default: break;
        }
        switch (buttonCode) {
            case 0x130: return BUTTON_COLORS[0];
            case 0x131: return BUTTON_COLORS[1];
            case 0x133: return BUTTON_COLORS[2];
            case 0x134: return BUTTON_COLORS[3];
            case 0x136: return BUTTON_COLORS[4];
            case 0x137: return BUTTON_COLORS[5];
            case 0x138: return BUTTON_COLORS[6];
            case 0x139: return BUTTON_COLORS[7];
            case 0x13d: return BUTTON_COLORS[8];
            case 0x13e: return BUTTON_COLORS[9];
            case 0x13b: return BUTTON_COLORS[10];
            case 0x13a: return BUTTON_COLORS[11];
            default: return 0xCC888888;
        }
    }

    // Corner handle identifiers
    private static final int HANDLE_NONE = -1;
    private static final int HANDLE_TL = 0;
    private static final int HANDLE_TR = 1;
    private static final int HANDLE_BL = 2;
    private static final int HANDLE_BR = 3;

    private final Paint mCirclePaint;
    private final Paint mBorderPaint;
    private final Paint mTextPaint;
    private final Paint mBboxPaint;
    private final Paint mHandlePaint;
    private final Paint mTrashPaint;
    private final Paint mTrashIconPaint;

    private ScreenMapConfig.MappingPoint mPoint;
    private boolean mEditable = true;
    private boolean mSelected = false;
    private float mDensity;
    private float mHandleRadius;

    // Interaction state
    private float mTouchOffsetX, mTouchOffsetY;
    private boolean mDragging;
    private long mDownTime;
    private float mDownX, mDownY;
    private int mActiveHandle = HANDLE_NONE;
    private int mResizeStartRadius;
    private float mResizeStartX, mResizeStartY;

    // Pinch state (kept as secondary resize method)
    private boolean mPinching;
    private float mPinchStartDist;
    private int mPinchStartRadius;

    private float mOverlayAlpha = 1.0f;

    private OnPointClickedListener mClickListener;
    private OnPointRemovedListener mRemoveListener;

    public interface OnPointClickedListener {
        void onPointClicked(MappingPointView view);
    }

    public interface OnPointRemovedListener {
        void onPointRemoved(MappingPointView view);
    }

    public MappingPointView(Context context, ScreenMapConfig.MappingPoint point) {
        super(context);
        mPoint = point;
        mDensity = context.getResources().getDisplayMetrics().density;
        mHandleRadius = Math.max(8 * mDensity, point.radius * 0.15f);

        int color = getColorForButton(point.buttonCode, point.type);

        mCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mCirclePaint.setColor(color);
        mCirclePaint.setStyle(Paint.Style.FILL);

        mBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBorderPaint.setColor(Color.WHITE);
        mBorderPaint.setStyle(Paint.Style.STROKE);
        mBorderPaint.setStrokeWidth(Math.max(2f, 2f * mDensity));

        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setColor(Color.WHITE);
        mTextPaint.setTextSize(point.radius * 0.45f);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setTypeface(Typeface.DEFAULT_BOLD);

        // Bounding box (dashed)
        mBboxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBboxPaint.setColor(Color.WHITE);
        mBboxPaint.setStyle(Paint.Style.STROKE);
        mBboxPaint.setStrokeWidth(2 * mDensity);
        mBboxPaint.setPathEffect(new DashPathEffect(new float[]{8 * mDensity, 4 * mDensity}, 0));

        // Corner handles
        mHandlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mHandlePaint.setColor(Color.WHITE);
        mHandlePaint.setStyle(Paint.Style.FILL);

        // Trash button
        mTrashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTrashPaint.setColor(Color.argb(220, 200, 40, 40));
        mTrashPaint.setStyle(Paint.Style.FILL);

        mTrashIconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTrashIconPaint.setColor(Color.WHITE);
        mTrashIconPaint.setTextSize(Math.max(10 * mDensity, point.radius * 0.3f));
        mTrashIconPaint.setTextAlign(Paint.Align.CENTER);
        mTrashIconPaint.setTypeface(Typeface.DEFAULT_BOLD);
    }

    public ScreenMapConfig.MappingPoint getPoint() { return mPoint; }

    public void setEditable(boolean editable) {
        mEditable = editable;
        mSelected = false;
        if (editable) {
            // Edit mode: show unique button color
            mCirclePaint.setColor(getColorForButton(mPoint.buttonCode, mPoint.type));
        } else {
            // Play mode: neutral dark semi-transparent
            mCirclePaint.setColor(Color.argb(120, 30, 30, 30));
        }
        applyAlpha();
        invalidate();
    }

    public void setOnPointClickedListener(OnPointClickedListener listener) {
        mClickListener = listener;
    }

    public void setOnPointRemovedListener(OnPointRemovedListener listener) {
        mRemoveListener = listener;
    }

    public void setOverlayAlpha(float alpha) {
        mOverlayAlpha = alpha;
        applyAlpha();
        invalidate();
    }

    private void applyAlpha() {
        int baseAlpha = Color.alpha(mCirclePaint.getColor());
        mCirclePaint.setAlpha((int) (baseAlpha * mOverlayAlpha));
        mBorderPaint.setAlpha((int) (255 * mOverlayAlpha));
        mTextPaint.setAlpha((int) (255 * mOverlayAlpha));
    }

    public boolean isSelected() { return mSelected; }

    public void deselect() {
        mSelected = false;
        invalidate();
    }

    // Padding around the circle for the bounding box and handles
    private float getPadding() {
        return mHandleRadius + 4 * mDensity;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        float pad = mSelected ? getPadding() : 0;
        int size = (int) (mPoint.radius * 2 + pad * 2);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float pad = mSelected ? getPadding() : 0;
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = mPoint.radius - mBorderPaint.getStrokeWidth();

        // Main circle
        canvas.drawCircle(cx, cy, r, mCirclePaint);
        canvas.drawCircle(cx, cy, r, mBorderPaint);

        // Label
        String label = mPoint.getLabel();
        mTextPaint.setTextSize(mPoint.radius * 0.45f);
        float textY = cy - (mTextPaint.descent() + mTextPaint.ascent()) / 2;
        canvas.drawText(label, cx, textY, mTextPaint);

        if (mSelected && mEditable) {
            // Bounding box
            float left = cx - mPoint.radius - 2 * mDensity;
            float top = cy - mPoint.radius - 2 * mDensity;
            float right = cx + mPoint.radius + 2 * mDensity;
            float bottom = cy + mPoint.radius + 2 * mDensity;
            canvas.drawRect(left, top, right, bottom, mBboxPaint);

            // Corner handles
            float hr = mHandleRadius;
            canvas.drawCircle(left, top, hr, mHandlePaint);     // TL
            canvas.drawCircle(right, top, hr, mHandlePaint);    // TR
            canvas.drawCircle(left, bottom, hr, mHandlePaint);  // BL
            canvas.drawCircle(right, bottom, hr, mHandlePaint); // BR

            // Trash icon centered above
            float trashR = Math.max(12 * mDensity, hr * 1.2f);
            float trashX = cx;
            float trashY = top - trashR - 4 * mDensity;
            canvas.drawCircle(trashX, trashY, trashR, mTrashPaint);
            mTrashIconPaint.setTextSize(trashR * 1.1f);
            float trashTextY = trashY - (mTrashIconPaint.descent() + mTrashIconPaint.ascent()) / 2;
            canvas.drawText("X", trashX, trashTextY, mTrashIconPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!mEditable) return false;
        int pointerCount = event.getPointerCount();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                mDownTime = System.currentTimeMillis();
                mDownX = event.getRawX();
                mDownY = event.getRawY();
                mDragging = false;
                mPinching = false;
                mActiveHandle = HANDLE_NONE;

                if (mSelected) {
                    // Check if a corner handle was grabbed
                    mActiveHandle = hitTestHandle(event.getX(), event.getY());
                    if (mActiveHandle != HANDLE_NONE) {
                        mResizeStartRadius = mPoint.radius;
                        mResizeStartX = event.getRawX();
                        mResizeStartY = event.getRawY();
                        return true;
                    }

                    // Check if trash was tapped
                    if (hitTestTrash(event.getX(), event.getY())) {
                        if (mRemoveListener != null) mRemoveListener.onPointRemoved(this);
                        return true;
                    }
                }

                mTouchOffsetX = event.getRawX() - getX();
                mTouchOffsetY = event.getRawY() - getY();
                return true;
            }

            case MotionEvent.ACTION_POINTER_DOWN:
                if (pointerCount == 2) {
                    mPinching = true;
                    mDragging = false;
                    mActiveHandle = HANDLE_NONE;
                    mPinchStartDist = getPointerDistance(event);
                    mPinchStartRadius = mPoint.radius;
                }
                return true;

            case MotionEvent.ACTION_MOVE: {
                // Corner handle resize
                if (mActiveHandle != HANDLE_NONE) {
                    float dx = event.getRawX() - mResizeStartX;
                    float dy = event.getRawY() - mResizeStartY;
                    // Use the diagonal distance for resize
                    float delta;
                    if (mActiveHandle == HANDLE_BR || mActiveHandle == HANDLE_TR) {
                        delta = dx; // right handles: drag right = bigger
                    } else {
                        delta = -dx; // left handles: drag left = bigger
                    }
                    int newRadius = mResizeStartRadius + (int) (delta);
                    int minR = (int) (15 * mDensity);
                    int maxR = (int) (200 * mDensity);
                    mPoint.radius = Math.max(minR, Math.min(maxR, newRadius));
                    mHandleRadius = Math.max(8 * mDensity, mPoint.radius * 0.15f);
                    updateLayout();
                    return true;
                }

                // Pinch resize
                if (mPinching && pointerCount >= 2) {
                    float dist = getPointerDistance(event);
                    float scale = dist / mPinchStartDist;
                    int newRadius = (int) (mPinchStartRadius * scale);
                    int minR = (int) (15 * mDensity);
                    int maxR = (int) (200 * mDensity);
                    mPoint.radius = Math.max(minR, Math.min(maxR, newRadius));
                    mHandleRadius = Math.max(8 * mDensity, mPoint.radius * 0.15f);
                    updateLayout();
                    return true;
                }

                // Drag
                float ddx = event.getRawX() - mDownX;
                float ddy = event.getRawY() - mDownY;
                if (!mDragging && (ddx * ddx + ddy * ddy) > 100) {
                    mDragging = true;
                }
                if (mDragging) {
                    float newX = event.getRawX() - mTouchOffsetX;
                    float newY = event.getRawY() - mTouchOffsetY;
                    setX(newX);
                    setY(newY);
                    float pad = mSelected ? getPadding() : 0;
                    mPoint.x = (int) (newX + pad + mPoint.radius);
                    mPoint.y = (int) (newY + pad + mPoint.radius);
                }
                return true;
            }

            case MotionEvent.ACTION_POINTER_UP:
                if (pointerCount <= 2) mPinching = false;
                return true;

            case MotionEvent.ACTION_UP: {
                if (mActiveHandle != HANDLE_NONE) {
                    mActiveHandle = HANDLE_NONE;
                    return true;
                }
                if (mPinching) {
                    mPinching = false;
                    return true;
                }
                if (!mDragging && (System.currentTimeMillis() - mDownTime) < 300) {
                    if (!mSelected) {
                        // First tap: select (show bounding box + handles + trash)
                        mSelected = true;
                        updateLayout();
                    } else {
                        // Already selected, tap on circle body: open button selector
                        mSelected = false;
                        updateLayout();
                        if (mClickListener != null) mClickListener.onPointClicked(this);
                    }
                }
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    private int hitTestHandle(float x, float y) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float left = cx - mPoint.radius - 2 * mDensity;
        float top = cy - mPoint.radius - 2 * mDensity;
        float right = cx + mPoint.radius + 2 * mDensity;
        float bottom = cy + mPoint.radius + 2 * mDensity;
        float hitR = mHandleRadius * 2.5f; // generous hit area

        if (dist2(x, y, left, top) < hitR * hitR) return HANDLE_TL;
        if (dist2(x, y, right, top) < hitR * hitR) return HANDLE_TR;
        if (dist2(x, y, left, bottom) < hitR * hitR) return HANDLE_BL;
        if (dist2(x, y, right, bottom) < hitR * hitR) return HANDLE_BR;
        return HANDLE_NONE;
    }

    private boolean hitTestTrash(float x, float y) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float top = cy - mPoint.radius - 2 * mDensity;
        float trashR = Math.max(12 * mDensity, mHandleRadius * 1.2f);
        float trashX = cx;
        float trashY = top - trashR - 4 * mDensity;
        float hitR = trashR * 2f;
        return dist2(x, y, trashX, trashY) < hitR * hitR;
    }

    private float dist2(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return dx * dx + dy * dy;
    }

    private void updateLayout() {
        requestLayout();
        invalidate();
        // Re-position with padding accounted for
        float pad = mSelected ? getPadding() : 0;
        setX(mPoint.x - mPoint.radius - pad);
        setY(mPoint.y - mPoint.radius - pad);
    }

    private float getPointerDistance(MotionEvent event) {
        float dx = event.getX(0) - event.getX(1);
        float dy = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /** Position the view so the circle center is at (cx, cy) in parent coordinates */
    public void positionAt(int cx, int cy) {
        mPoint.x = cx;
        mPoint.y = cy;
        float pad = mSelected ? getPadding() : 0;
        setX(cx - mPoint.radius - pad);
        setY(cy - mPoint.radius - pad);
    }
}

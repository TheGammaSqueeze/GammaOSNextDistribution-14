/*
 * CursorView - a tiny overlay that draws the in-app gamepad mouse pointer for
 * GammaBrowser's cursor mode. The arrow's hotspot (the click point) is the view's
 * top-left corner (0,0), so the host positions it with setX(cx)/setY(cy) and the
 * tip sits exactly under the logical cursor coordinate. White fill + dark stroke +
 * a soft shadow so it stays visible on any page. Costs nothing when GONE.
 */
package com.gammaos.browser;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

public class CursorView extends View {

    private final Path mArrow = new Path();
    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int mSize;   // bounding box in px

    public CursorView(Context c) { this(c, null); }

    public CursorView(Context c, AttributeSet attrs) {
        super(c, attrs);
        float d = c.getResources().getDisplayMetrics().density;
        mSize = (int) (30 * d);

        // Classic pointer, tip at (0,0). Coordinates are fractions of a ~18x28 box.
        float u = 18f * d;          // arrow drawn within an 18(w) x 28(h) region, scaled
        mArrow.moveTo(0f, 0f);
        mArrow.lineTo(0f,            1.00f * u);
        mArrow.lineTo(0.28f * u,     0.78f * u);
        mArrow.lineTo(0.45f * u,     1.17f * u);
        mArrow.lineTo(0.62f * u,     1.10f * u);
        mArrow.lineTo(0.44f * u,     0.72f * u);
        mArrow.lineTo(0.78f * u,     0.72f * u);
        mArrow.close();

        mFill.setStyle(Paint.Style.FILL);
        mFill.setColor(0xFFFFFFFF);
        // Soft shadow for contrast over light pages (needs no hardware layer).
        mFill.setShadowLayer(3f * d, 0f, 1f * d, 0x99000000);
        setLayerType(LAYER_TYPE_SOFTWARE, null);   // shadow layer needs software rendering

        mStroke.setStyle(Paint.Style.STROKE);
        mStroke.setColor(0xFF101522);
        mStroke.setStrokeWidth(1.4f * d);
        mStroke.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        setMeasuredDimension(mSize, (int) (mSize * 1.4f));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawPath(mArrow, mFill);
        canvas.drawPath(mArrow, mStroke);
    }
}

package org.lineageos.setupwizard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Draws:
 *  - Semi-transparent white square + grid
 *  - Thick black centre axes + black centre dot
 *  - Fading trail of past positions (green)
 *  - Solid green current-position dot
 */
public class StickTestView extends View {
    private static final int TRAIL_MAX_ALPHA  = 255;
    private static final int TRAIL_DECAY_STEP = 15;
    private static final float DOT_RADIUS     = 6f;

    private static class TrailPoint {
        float x, y; int alpha;
        TrailPoint(float x, float y, int a) { this.x = x; this.y = y; this.alpha = a; }
    }

    private final Paint backgroundPaint;
    private final Paint gridPaint;
    private final Paint centreLinePaint;
    private final Paint centreDotPaint;
    private final Paint trailPaint;
    private final Paint dotPaint;

    private final List<TrailPoint> trail = new LinkedList<>();
    private float axisX = 0f, axisY = 0f;

    public StickTestView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);

        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setStyle(Paint.Style.FILL);
        backgroundPaint.setColor(Color.WHITE);
        backgroundPaint.setAlpha(128);

        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setColor(Color.WHITE);
        gridPaint.setAlpha(128);
        gridPaint.setStrokeWidth(1f);

        centreLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        centreLinePaint.setStyle(Paint.Style.STROKE);
        centreLinePaint.setColor(Color.BLACK);
        centreLinePaint.setStrokeWidth(4f);

        centreDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        centreDotPaint.setStyle(Paint.Style.FILL);
        centreDotPaint.setColor(Color.BLACK);

        trailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trailPaint.setStyle(Paint.Style.FILL);
        trailPaint.setColor(Color.GREEN);

        dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(Color.GREEN);
    }

    /** Call on joystick move, adds trail point. */
    public void updateAxes(float x, float y) {
        axisX = x; axisY = y;
        float cx = getWidth()*0.5f, cy = getHeight()*0.5f;
        float half = Math.min(cx, cy) - 8f;
        float px = cx + axisX*half, py = cy + axisY*half;
        trail.add(new TrailPoint(px, py, TRAIL_MAX_ALPHA));
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);

        float w = getWidth(), h = getHeight();
        float cx = w*0.5f, cy = h*0.5f;
        float half = Math.min(cx, cy) - 8f;
        float size = half*2f;

        // 1) background
        c.drawRect(cx-half, cy-half, cx+half, cy+half, backgroundPaint);

        // 2) grid
        float cell = size/10f;
        for(int i=1;i<10;i++){
            float off = i*cell;
            c.drawLine(cx-half+off, cy-half, cx-half+off, cy+half, gridPaint);
            c.drawLine(cx-half, cy-half+off, cx+half, cy-half+off, gridPaint);
        }

        // 3) centre axes
        c.drawLine(cx-half, cy, cx+half, cy, centreLinePaint);
        c.drawLine(cx, cy-half, cx, cy+half, centreLinePaint);

        // 4) centre dot
        c.drawCircle(cx, cy, DOT_RADIUS, centreDotPaint);

        // 5) trail
        Iterator<TrailPoint> it = trail.iterator();
        while(it.hasNext()){
            TrailPoint tp = it.next();
            trailPaint.setAlpha(tp.alpha);
            c.drawCircle(tp.x, tp.y, DOT_RADIUS, trailPaint);
            tp.alpha -= TRAIL_DECAY_STEP;
            if(tp.alpha<=0) it.remove();
        }

        // 6) current dot
        float dx = cx + axisX*half, dy = cy + axisY*half;
        c.drawCircle(dx, dy, DOT_RADIUS, dotPaint);

        if(!trail.isEmpty()) postInvalidateOnAnimation();
    }
}

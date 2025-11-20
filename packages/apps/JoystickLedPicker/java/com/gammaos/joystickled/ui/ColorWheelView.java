
package com.gammaos.joystickled.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

public class ColorWheelView extends View {

    public interface OnColorChangeListener {
        void onColorChanged(float hue, float saturation);
    }

    private Paint paint;
    private Paint selectorPaint;
    private Bitmap bitmap;
    private float centerX, centerY, radius;
    private float selectorX, selectorY;
    private float currentHue = 0f, currentSat = 1f;
    private OnColorChangeListener listener;

    public ColorWheelView(Context context) { super(context); init(); }
    public ColorWheelView(Context c, @Nullable AttributeSet a) { super(c, a); init(); }
    public ColorWheelView(Context c, @Nullable AttributeSet a, int s) { super(c, a, s); init(); }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        selectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        selectorPaint.setStyle(Paint.Style.STROKE);
        selectorPaint.setStrokeWidth(6f);
        selectorPaint.setColor(Color.WHITE);
    }

    public void setOnColorChangeListener(OnColorChangeListener l) { listener = l; }

    public void setHueAndSaturation(float hue, float sat) {
        currentHue = hue;
        currentSat = sat;
        if (radius > 0) {
            double rad = Math.toRadians(hue);
            float r = currentSat * radius;
            selectorX = centerX + (float) (Math.cos(rad) * r);
            selectorY = centerY + (float) (Math.sin(rad) * r);
            invalidate();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2f;
        centerY = h / 2f;
        radius = Math.min(w, h) / 2f * 0.9f;
        createBitmap(w, h);
        setHueAndSaturation(currentHue, currentSat);
    }

    private void createBitmap(int width, int height) {
        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        int[] colors = new int[361];
        for (int i = 0; i <= 360; i++) {
            colors[i] = Color.HSVToColor(new float[]{i, 1f, 1f});
        }
        Shader sweep = new SweepGradient(centerX, centerY, colors, null);
        Shader radial = new RadialGradient(centerX, centerY, radius,
                Color.WHITE, 0x00FFFFFF, Shader.TileMode.CLAMP);
        ComposeShader shader = new ComposeShader(sweep, radial, PorterDuff.Mode.SRC_OVER);
        paint.setShader(shader);
        canvas.drawCircle(centerX, centerY, radius, paint);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap != null) canvas.drawBitmap(bitmap, 0, 0, null);
        if (selectorX != 0 || selectorY != 0) {
            canvas.drawCircle(selectorX, selectorY, 20f, selectorPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX() - centerX;
        float y = event.getY() - centerY;
        double dist = Math.sqrt(x * x + y * y);
        if (dist > radius) {
            x = (float) (x * radius / dist);
            y = (float) (y * radius / dist);
            dist = radius;
        }
        double angle = Math.atan2(y, x);
        float hue = (float) Math.toDegrees(angle);
        if (hue < 0) hue += 360f;
        float sat = (float) (dist / radius);
        currentHue = hue;
        currentSat = sat;
        selectorX = centerX + x;
        selectorY = centerY + y;
        if (listener != null) listener.onColorChanged(hue, sat);
        invalidate();
        return true;
    }
}

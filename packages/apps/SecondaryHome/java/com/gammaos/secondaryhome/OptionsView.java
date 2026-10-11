package com.gammaos.secondaryhome;

import android.content.Context;
import android.graphics.Canvas;
import android.os.SystemProperties;
import android.view.MotionEvent;
import android.view.View;

/**
 * Control centre "Screen Options" page (page 2), ported from NanoMenu::renderCcSettings and
 * ccOnSettingsTap. Same props as the nano CC, so the choice is shared by both modes.
 */
final class OptionsView extends View {
    static final int[] TIMEOUT_MS = {0, 15000, 30000, 60000, 120000, 300000};
    private static final String[] TIMEOUT_LBL = {"Never", "15s", "30s", "1 min", "2 min", "5 min"};
    private static final int ROWS = 2;

    private final CcDraw mD = new CcDraw();
    private float mDownX, mDownY;

    OptionsView(Context ctx) {
        super(ctx);
    }

    static boolean doubleTapWake() {
        return SystemProperties.getBoolean(CcState.PROP_DOUBLETAP, false);
    }

    static int sleepTimeoutMs() {
        return SystemProperties.getInt(CcState.PROP_TIMEOUT, 30000);
    }

    private static float rowY(int i) { return 66f + i * (74f + 16f); }

    @Override
    protected void onDraw(Canvas c) {
        final CcDraw d = mD;
        c.drawColor(CcDraw.BG);
        c.save();
        d.begin(c, getWidth(), getHeight());
        d.header(c, "SCREEN OPTIONS", 10, 8);
        final boolean dtw = doubleTapWake();
        final int tms = sleepTimeoutMs();
        for (int i = 0; i < ROWS; i++) {
            final float rx = 20, ry = rowY(i), rw = 600, rh = 74;
            d.round(c, rx, ry, rw, rh, 14f, CcDraw.argb(0.95f, 0.075f, 0.082f, 0.11f));
            final String label, desc;
            String val = null;
            final boolean on;
            if (i == 0) {
                label = "Double Tap to Wake";
                desc = "Two taps to wake the bottom screen";
                val = dtw ? "On" : "Off";
                on = dtw;
            } else {
                label = "Screen Timeout";
                desc = "Auto-sleep the bottom screen when idle";
                for (int k = 0; k < TIMEOUT_MS.length; k++) if (TIMEOUT_MS[k] == tms) val = TIMEOUT_LBL[k];
                if (val == null) val = (tms / 1000) + "s";
                on = tms > 0;
            }
            d.textL(c, label, rx + 18, ry + 14, 15f, CcDraw.rgb(0.92f, 0.94f, 0.98f));
            d.textL(c, desc, rx + 18, ry + 44, 11f, CcDraw.rgb(0.52f, 0.57f, 0.68f));
            final float pw = 120, ph = 44, pxx = rx + rw - pw - 16, pyy = ry + (rh - ph) * 0.5f;
            d.round(c, pxx, pyy, pw, ph, ph * 0.5f,
                    on ? CcDraw.rgb(0.15f, 0.45f, 0.26f) : CcDraw.rgb(0.13f, 0.14f, 0.17f));
            d.textC(c, val, pxx + pw * 0.5f, pyy + (ph - 14f) * 0.5f, 14f, CcDraw.rgb(0.95f, 0.97f, 1f));
        }
        d.textC(c, "Tap a row to change", 320, CcDraw.DH - 52f, 11f, CcDraw.rgb(0.44f, 0.49f, 0.60f));
        c.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        final float px = mD.dx(e.getX()), py = mD.dy(e.getY());
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            mDownX = px;
            mDownY = py;
        } else if (e.getActionMasked() == MotionEvent.ACTION_UP) {
            final float ddx = px - mDownX, ddy = py - mDownY;
            if (ddx * ddx + ddy * ddy < 400f) onTap(mDownX, mDownY);
        }
        return true;
    }

    private void onTap(float px, float py) {
        for (int i = 0; i < ROWS; i++) {
            final float ry = rowY(i);
            if (px < 20 || px > 620 || py < ry || py > ry + 74) continue;
            if (i == 0) {
                SystemProperties.set(CcState.PROP_DOUBLETAP, doubleTapWake() ? "0" : "1");
            } else {
                final int tms = sleepTimeoutMs();
                int idx = 0;
                for (int k = 0; k < TIMEOUT_MS.length; k++) if (TIMEOUT_MS[k] == tms) idx = k;
                idx = (idx + 1) % TIMEOUT_MS.length;
                SystemProperties.set(CcState.PROP_TIMEOUT, Integer.toString(TIMEOUT_MS[idx]));
            }
            invalidate();
            return;
        }
    }
}

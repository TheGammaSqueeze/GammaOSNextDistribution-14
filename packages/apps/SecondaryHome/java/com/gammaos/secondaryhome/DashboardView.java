package com.gammaos.secondaryhome;

import android.content.Context;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.view.MotionEvent;
import android.view.View;

import java.util.Calendar;
import java.util.Locale;

/**
 * Control centre dashboard (page 0): brightness, control tiles, volume, thermal and performance
 * cards. A 1:1 port of NanoMenu::renderCcPass / ccTouchFrame / ccOnTap / ccApplySlider, drawn and
 * hit-tested in the same 640x480 design space so a tap always matches what is drawn.
 */
final class DashboardView extends View {

    /** What the dashboard needs from the activity. */
    interface Host {
        boolean isScreenSleeping();
        void toggleScreenSleep();
        /** Package of an app this home launched that still runs on this panel, or null. */
        String bottomApp();
        void closeBottomApp();
        void takeScreenshot();
        void onSliderBrightness(int bottomLevel);
    }

    private static final float CC_TX0 = 180, CC_TY0 = 58, CC_TW = 106, CC_TH = 70, CC_GX = 114, CC_GY = 82;
    private static final int A_SLEEP = 0, A_PERF = 1, A_SPLITBRI = 2, A_SHADER = 3, A_EQ = 4,
            A_MOUSE = 5, A_SHOT = 6, A_WIFI = 7;
    private static final String[] TILE_LABEL = {
            "Sleep Screen", "Performance", "Split Bright", "Shader",
            "Gamma EQ", "Mouse", "Screenshot", "Wi-Fi" };

    private final CcState mState;
    private final Host mHost;
    private final CcDraw mD = new CcDraw();

    private int mHeld = -1;
    private float mDownX, mDownY;
    private int mVolLastSet = -1;
    private long mVolLastSetMs;
    private int mMapLastBot = -1, mMapLastTop = -1;
    private long mMapLastSetMs;

    DashboardView(Context ctx, CcState state, Host host) {
        super(ctx);
        mState = state;
        mHost = host;
    }

    private static void tileXY(int i, float[] out) {
        out[0] = CC_TX0 + (i % 4) * CC_GX;
        out[1] = CC_TY0 + (i / 4) * CC_GY;
    }

    /** 0 off, 1 on (perf: powersave), 2 perf max. */
    private int actState(int act) {
        switch (act) {
            case A_SLEEP: return mHost.isScreenSleeping() ? 1 : 0;
            case A_PERF: return CcState.perfState();
            case A_SPLITBRI: return CcState.propOn(CcState.PROP_SPLIT) ? 1 : 0;
            case A_SHADER: return CcState.propOn(CcState.PROP_SHADER) ? 1 : 0;
            case A_EQ: return CcState.propOn(CcState.PROP_EQ) ? 1 : 0;
            case A_MOUSE: return CcState.propOn(CcState.PROP_MOUSE) ? 1 : 0;
            case A_WIFI: return mState.wifiOn ? 1 : 0;
            default: return 0;
        }
    }

    // ------------------------------------------------------------------ draw

    @Override
    protected void onDraw(Canvas c) {
        final CcDraw d = mD;
        final CcState s = mState;
        c.drawColor(CcDraw.BG);
        c.save();
        d.begin(c, getWidth(), getHeight());

        // ==== status bar ====
        d.header(c, "CONTROL CENTER", 10, 8);
        final Calendar cal = Calendar.getInstance();
        d.textR(c, String.format(Locale.ROOT, "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE)), 632, 6, 15f, CcDraw.rgb(0.92f, 0.94f, 1f));
        d.textR(c, String.format(Locale.ROOT, "%s  %d%%  %.1fW", s.charging ? "CHG" : "BAT",
                s.battPct, s.watts), 570, 8, 12f, CcDraw.rgb(0.6f, 0.66f, 0.8f));

        // ==== brightness ====
        d.card(c, 8, 30, 150, 202);
        d.header(c, "BRIGHTNESS", 22, 40);
        {
            final float sy = 66, sh = 120, sw = 20;
            final float[] px = {50, 100};
            final float[] frac = {
                    s.briTopMax > 0 ? (float) s.briTop / s.briTopMax : 0,
                    s.briBotMax > 0 ? (float) s.briBot / s.briBotMax : 0 };
            for (int i = 0; i < 2; i++) {
                final float x = px[i] - sw * 0.5f;
                d.round(c, x, sy, sw, sh, sw * 0.5f, CcDraw.TRACK);
                float fh = sh * clamp01(frac[i]);
                if (fh < sw) fh = frac[i] > 0.01f ? sw : 0;
                if (fh > 0) d.round(c, x, sy + sh - fh, sw, fh, sw * 0.5f, CcDraw.rgb(0.85f, 0.9f, 1f));
            }
            d.icoSun(c, px[0], 200, 8, CcDraw.rgb(0.7f, 0.75f, 0.85f));
            d.icoSun(c, px[1], 200, 6.5f, CcDraw.rgb(0.55f, 0.6f, 0.72f));
            d.textC(c, "TOP", px[0], 216, 10f, CcDraw.rgb(0.6f, 0.66f, 0.8f));
            d.textC(c, "BTM", px[1], 216, 10f, CcDraw.rgb(0.55f, 0.6f, 0.72f));
        }

        // ==== control tiles ====
        d.card(c, 166, 30, 466, 202);
        d.header(c, "CONTROLS", 180, 40);
        final boolean closeTile = mHost.bottomApp() != null;
        final float[] xy = new float[2];
        for (int i = 0; i < 8; i++) {
            final int st = actState(i);
            final boolean isClose = i == A_SHOT && closeTile;
            final boolean on = st > 0 || isClose;
            tileXY(i, xy);
            final float x = xy[0], y = xy[1];
            int bg = on ? CcDraw.rgb(0.34f, 0.16f, 0.52f) : CcDraw.rgb(0.11f, 0.12f, 0.155f);
            if (isClose) bg = CcDraw.rgb(0.52f, 0.16f, 0.18f);
            d.round(c, x, y, CC_TW, CC_TH, 11f, bg);
            final float icx = x + CC_TW * 0.5f, icy = y + 24;
            int ic = on ? CcDraw.rgb(0.99f, 0.92f, 1f) : CcDraw.rgb(0.82f, 0.86f, 0.94f);
            if (isClose) ic = CcDraw.rgb(1f, 0.86f, 0.86f);
            switch (i) {
                case A_SLEEP: d.icoMoon(c, icx, icy, 10, ic, bg); break;
                case A_PERF: d.icoGauge(c, icx, icy, 11, ic); break;
                case A_SPLITBRI: d.icoSplitBri(c, icx, icy, 10, ic); break;
                case A_SHADER: d.icoShader(c, icx, icy, 9, ic, bg); break;
                case A_EQ: d.icoMusic(c, icx, icy, 11, ic); break;
                case A_MOUSE: d.icoMouse(c, icx, icy, 11, ic); break;
                case A_SHOT:
                    if (isClose) d.icoClose(c, icx, icy, 11, ic);
                    else d.icoCamera(c, icx, icy, 11, ic, bg);
                    break;
                case A_WIFI: d.icoWifi(c, icx, icy, 10, ic); break;
            }
            String lbl = TILE_LABEL[i];
            if (i == A_PERF) lbl = st == 2 ? "Max" : st == 1 ? "Powersave" : "Stock";
            else if (isClose) lbl = "Close App";
            d.textC(c, lbl, icx, y + CC_TH - 20, 11f, CcDraw.rgb(0.82f, 0.85f, 0.92f));
        }

        // ==== volume (master / top / bottom) ====
        d.card(c, 8, 240, 210, 200);
        d.header(c, "VOLUME", 22, 254);
        {
            final boolean mv = s.multiVol;
            final float sy = 282, sh = 100, sw = 22;
            final float[] px = {48, 113, 178};
            final int vmin = s.volMin, vmax = s.volMax > s.volMin ? s.volMax : 15;
            final float rng = Math.max(1f, vmax - vmin);
            final int master = mv ? Math.max(s.volTop, s.volBot) : s.volCur;
            final int[] vals = {master, s.volTop, s.volBot};
            for (int i = 0; i < 3; i++) {
                final float x = px[i] - sw * 0.5f;
                d.round(c, x, sy, sw, sh, sw * 0.5f, CcDraw.TRACK);
                final boolean active = i == 0 || mv;
                final float frac = clamp01((vals[i] - vmin) / rng);
                float fh = sh * frac;
                if (fh < sw) fh = frac > 0.01f ? sw : 0;
                final int col = active ? CcDraw.rgb(0.85f, 0.9f, 1f) : CcDraw.rgb(0.28f, 0.30f, 0.36f);
                if (fh > 0) d.round(c, x, sy + sh - fh, sw, fh, sw * 0.5f, col);
            }
            final int ic = CcDraw.rgb(0.7f, 0.75f, 0.85f);
            d.icoSpeaker(c, px[0], 392, 9, ic);
            d.icoSpeaker(c, px[1], 392, 7.5f, ic);
            d.icoSpeaker(c, px[2], 392, 7.5f, ic);
            final int lc = CcDraw.rgb(0.6f, 0.66f, 0.8f);
            d.textC(c, "ALL", px[0], 410, 10f, lc);
            d.textC(c, "TOP", px[1], 410, 10f, lc);
            d.textC(c, "BTM", px[2], 410, 10f, lc);
        }

        // ==== thermal ====
        d.card(c, 226, 240, 132, 200);
        d.header(c, "THERMAL", 240, 254);
        final int num = CcDraw.rgb(0.95f, 0.97f, 1f);
        final int dim = CcDraw.rgb(0.55f, 0.6f, 0.7f);
        d.icoThermo(c, 252, 292, 11, CcDraw.rgb(1f, 0.62f, 0.36f));
        d.textL(c, s.socTempC + "°", 272, 274, 40f, num);
        d.textL(c, "SoC", 272, 316, 11f, dim);
        d.icoThermo(c, 252, 384, 11, CcDraw.rgb(0.36f, 0.82f, 1f));
        d.textL(c, s.gpuTempC + "°", 272, 366, 40f, num);
        d.textL(c, "GPU", 272, 408, 11f, dim);

        // ==== performance ring gauges ====
        d.card(c, 366, 240, 266, 200);
        d.header(c, "PERFORMANCE", 380, 254);
        {
            final float cpuFrac = s.cpuMaxMhz > 0 ? (float) s.cpuMhz / s.cpuMaxMhz : 0;
            final float gpuFrac = s.gpuMaxMhz > 0 ? (float) s.gpuMhz / s.gpuMaxMhz : 0;
            final float ramFrac = s.ramTotalMb > 0 ? (float) s.ramUsedMb / s.ramTotalMb : 0;
            gauge(c, 440, 312, cpuFrac, CcDraw.rgb(0.96f, 0.2f, 0.56f),
                    String.format(Locale.ROOT, "%.2f", s.cpuMhz / 1000f), "GHz", "CPU");
            gauge(c, 560, 312, gpuFrac, CcDraw.rgb(0.24f, 0.82f, 0.98f),
                    Integer.toString(s.gpuMhz), "MHz", "GPU");
            gauge(c, 440, 400, s.watts / 15f, CcDraw.rgb(1f, 0.62f, 0.2f),
                    String.format(Locale.ROOT, "%.1f", s.watts), "W", "PWR");
            gauge(c, 560, 400, ramFrac, CcDraw.rgb(0.45f, 0.9f, 0.42f),
                    String.format(Locale.ROOT, "%.1f", s.ramUsedMb / 1024f), "GB", "RAM");
        }
        c.restore();
    }

    private void gauge(Canvas c, float cx, float cy, float frac, int col, String big, String unit,
            String label) {
        final CcDraw d = mD;
        frac = clamp01(frac);
        final float start = (float) (-Math.PI * 0.5);
        d.ringArc(c, cx, cy, 30f, 40f, 0f, (float) (2 * Math.PI), CcDraw.TRACK);
        if (frac > 0.004f) {
            d.ringArc(c, cx, cy, 30f, 40f, start, start + (float) (2 * Math.PI) * frac, col);
        }
        d.textC(c, big, cx, cy - 15, 26f, CcDraw.rgb(0.95f, 0.97f, 1f));
        d.textC(c, unit, cx, cy + 10, 12f, CcDraw.rgb(0.62f, 0.66f, 0.76f));
        d.textC(c, label, cx, cy + 30, 13f, CcDraw.rgb(0.95f, 0.96f, 0.98f));
    }

    private static float clamp01(float f) { return f < 0 ? 0 : (f > 1 ? 1 : f); }

    // ------------------------------------------------------------------ touch

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        final float px = mD.dx(e.getX()), py = mD.dy(e.getY());
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = px;
                mDownY = py;
                mHeld = sliderAt(px, py);
                mState.heldSlider = mHeld;
                if (mHeld >= 0) {
                    // a grabbed slider tracks Y only; keep the pager from turning it into a swipe
                    getParent().requestDisallowInterceptTouchEvent(true);
                    applySlider(mHeld, py);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (mHeld >= 0) applySlider(mHeld, py);
                return true;
            case MotionEvent.ACTION_UP: {
                final float ddx = px - mDownX, ddy = py - mDownY;
                if (mHeld < 0 && ddx * ddx + ddy * ddy < 400f) onTap(mDownX, mDownY);
                release();
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                release();
                return true;
        }
        return false;
    }

    private void release() {
        final CcState s = mState;
        // flush the final volume (the drag debounced the intermediate writes)
        if (mHeld >= 2) {
            if (s.multiVol) setMap(s.volBot, s.volTop);
            else if (s.volCur != mVolLastSet) sendVolume(s.volCur);
        }
        // persist a brightness change through the framework so it survives sleep/wake
        if (mHeld == 0 || mHeld == 1) s.persistBrightness(mHeld);
        mHeld = -1;
        s.heldSlider = -1;
    }

    /** 0-1 brightness (top, bottom); 2-4 volume (master, top, bottom). */
    private int sliderAt(float px, float py) {
        {
            final float sy = 66, sh = 120;
            final float[] sx = {50, 100};
            for (int i = 0; i < 2; i++) {
                if (px >= sx[i] - 20 && px <= sx[i] + 20 && py >= sy - 12 && py <= sy + sh + 12) return i;
            }
        }
        {
            final float sy = 282, sh = 100;
            final float[] sx = {48, 113, 178};
            for (int i = 0; i < 3; i++) {
                if (i > 0 && !mState.multiVol) continue;
                if (px >= sx[i] - 22 && px <= sx[i] + 22 && py >= sy - 12 && py <= sy + sh + 12) return 2 + i;
            }
        }
        return -1;
    }

    private void applySlider(int i, float py) {
        final CcState s = mState;
        if (i == 0 || i == 1) {
            final float frac = clamp01(1f - (py - 66f) / 120f);
            // Split brightness off: one slider drives both panels.
            final boolean split = SystemProperties.getBoolean(CcState.PROP_SPLIT, false);
            if (i == 0 || !split) {
                final int v = Math.max(4, Math.round(frac * s.briTopMax));
                if (v != s.briTop) { CcState.writeInt(s.blBri(false), v); s.briTop = v; }
            }
            if (i == 1 || !split) {
                final int v = Math.max(4, Math.round(frac * s.briBotMax));
                if (v != s.briBot) {
                    CcState.writeInt(s.blBri(true), v);
                    s.briBot = v;
                    mHost.onSliderBrightness(v);
                }
            }
            invalidate();
            return;
        }
        final float frac = clamp01(1f - (py - 282f) / 100f);
        final int v = CcState.clamp(s.volMin + Math.round(frac * (s.volMax - s.volMin)), s.volMin, s.volMax);
        final long now = SystemClock.uptimeMillis();
        if (!s.multiVol) {
            if (i == 2) {
                s.volCur = v; s.volTop = v; s.volBot = v;
                if (v != mVolLastSet && now - mVolLastSetMs >= 90) sendVolume(v);
            }
            invalidate();
            return;
        }
        if (i == 2) { s.volTop = v; s.volBot = v; s.volCur = v; }
        else if (i == 3) { s.volTop = v; s.volCur = Math.max(v, s.volBot); }
        else { s.volBot = v; s.volCur = Math.max(v, s.volTop); }
        if (now - mMapLastSetMs >= 90 && (s.volBot != mMapLastBot || s.volTop != mMapLastTop)) {
            setMap(s.volBot, s.volTop);
        }
        invalidate();
    }

    private void sendVolume(int v) {
        mState.setMasterVolume(v);
        mVolLastSet = v;
        mVolLastSetMs = SystemClock.uptimeMillis();
    }

    private void setMap(int bot, int top) {
        mState.setDisplayVolumeMap(bot, top);
        mMapLastBot = bot;
        mMapLastTop = top;
        mMapLastSetMs = SystemClock.uptimeMillis();
    }

    private void onTap(float px, float py) {
        final float[] xy = new float[2];
        for (int i = 0; i < 8; i++) {
            tileXY(i, xy);
            if (px < xy[0] || px > xy[0] + CC_TW || py < xy[1] || py > xy[1] + CC_TH) continue;
            switch (i) {
                case A_SLEEP: mHost.toggleScreenSleep(); break;
                case A_PERF: CcState.cyclePerf(); break;
                case A_SPLITBRI: CcState.toggleProp(CcState.PROP_SPLIT); break;
                case A_SHADER: CcState.toggleProp(CcState.PROP_SHADER); break;
                case A_EQ: CcState.toggleProp(CcState.PROP_EQ); break;
                case A_MOUSE: CcState.toggleProp(CcState.PROP_MOUSE); break;
                case A_SHOT:
                    if (mHost.bottomApp() != null) mHost.closeBottomApp();
                    else mHost.takeScreenshot();
                    break;
                case A_WIFI: mState.toggleWifi(); break;
            }
            invalidate();
            return;
        }
    }
}

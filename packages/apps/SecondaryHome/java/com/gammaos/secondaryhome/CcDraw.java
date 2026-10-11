package com.gammaos.secondaryhome;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

/**
 * Drawing kit shared by the control centre pages. Everything is drawn in the nano CC's 640x480
 * design space: {@link #begin} scales it uniformly and centres it on the view, exactly like the
 * nano renderer's X()/Y()/S() helpers, so the layout adapts to any panel size and aspect.
 */
final class CcDraw {
    static final float DW = 640f, DH = 480f;

    // Palette (the nano CC's float colours, as ARGB).
    static final int BG = rgb(0.015f, 0.017f, 0.028f);
    static final int CARD = argb(0.94f, 0.075f, 0.082f, 0.11f);
    static final int CARD_SOLID = rgb(0.075f, 0.082f, 0.11f);
    static final int TRACK = rgb(0.16f, 0.17f, 0.21f);
    static final int HEADER = rgb(0.46f, 0.52f, 0.64f);

    final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mR = new RectF();
    private final Path mPath = new Path();

    float u = 1f, ox, oy;

    CcDraw() {
        fill.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.BUTT);
        text.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    }

    /** Map the design space onto a w x h view; xoff slides the whole page (design units). */
    void begin(Canvas c, int w, int h) {
        u = Math.min(w / DW, h / DH);
        ox = (w - DW * u) * 0.5f;
        oy = (h - DH * u) * 0.5f;
        c.translate(ox, oy);
        c.scale(u, u);
    }

    /** View pixel to design space. */
    float dx(float px) { return (px - ox) / u; }
    float dy(float py) { return (py - oy) / u; }

    static int rgb(float r, float g, float b) { return argb(1f, r, g, b); }

    static int argb(float a, float r, float g, float b) {
        return ((int) (a * 255f + 0.5f) << 24) | ((int) (r * 255f + 0.5f) << 16)
                | ((int) (g * 255f + 0.5f) << 8) | (int) (b * 255f + 0.5f);
    }

    void rect(Canvas c, float x, float y, float w, float h, int col) {
        fill.setColor(col);
        c.drawRect(x, y, x + w, y + h, fill);
    }

    void round(Canvas c, float x, float y, float w, float h, float r, int col) {
        fill.setColor(col);
        mR.set(x, y, x + w, y + h);
        c.drawRoundRect(mR, r, r, fill);
    }

    void card(Canvas c, float x, float y, float w, float h) { round(c, x, y, w, h, 13f, CARD); }

    void disc(Canvas c, float cx, float cy, float r, int col) {
        fill.setColor(col);
        c.drawCircle(cx, cy, r, fill);
    }

    void tri(Canvas c, float x0, float y0, float x1, float y1, float x2, float y2, int col) {
        fill.setColor(col);
        mPath.reset();
        mPath.moveTo(x0, y0);
        mPath.lineTo(x1, y1);
        mPath.lineTo(x2, y2);
        mPath.close();
        c.drawPath(mPath, fill);
    }

    /** Ring arc between radii rI..rO from angle a0 to a1 (radians, 0 = +x, clockwise on screen). */
    void ringArc(Canvas c, float cx, float cy, float rI, float rO, float a0, float a1, int col) {
        if (a1 <= a0) return;
        final float rm = (rI + rO) * 0.5f;
        stroke.setColor(col);
        stroke.setStrokeWidth(rO - rI);
        mR.set(cx - rm, cy - rm, cx + rm, cy + rm);
        c.drawArc(mR, (float) Math.toDegrees(a0), (float) Math.toDegrees(a1 - a0), false, stroke);
    }

    /** Thick line segment (the bars of the X and the mouse tail). */
    void bar(Canvas c, float ax, float ay, float bx, float by, float halfT, int col) {
        stroke.setColor(col);
        stroke.setStrokeWidth(halfT * 2f);
        c.drawLine(ax, ay, bx, by, stroke);
    }

    // ---- text: (x, topY) is the top-left of the glyph cell, px its height, like the nano font ----

    private void prep(float px, int col) {
        text.setTextSize(px * 0.92f);
        text.setColor(col);
    }

    private float base(float topY, float px) { return topY + px * 0.80f; }

    void textL(Canvas c, String s, float x, float topY, float px, int col) {
        prep(px, col);
        text.setTextAlign(Paint.Align.LEFT);
        c.drawText(s, x, base(topY, px), text);
    }

    void textC(Canvas c, String s, float cx, float topY, float px, int col) {
        prep(px, col);
        text.setTextAlign(Paint.Align.CENTER);
        c.drawText(s, cx, base(topY, px), text);
    }

    void textR(Canvas c, String s, float rx, float topY, float px, int col) {
        prep(px, col);
        text.setTextAlign(Paint.Align.RIGHT);
        c.drawText(s, rx, base(topY, px), text);
    }

    float measure(String s, float px) {
        text.setTextSize(px * 0.92f);
        return text.measureText(s);
    }

    /** Section header: tiny dim letter-spaced caps. */
    void header(Canvas c, String s, float x, float topY) {
        prep(11f, HEADER);
        text.setTextAlign(Paint.Align.LEFT);
        float cx = x;
        for (int i = 0; i < s.length(); i++) {
            final String ch = s.substring(i, i + 1);
            c.drawText(ch, cx, base(topY, 11f), text);
            cx += text.measureText(ch) + 2.2f;
        }
    }

    /** Label shortened with ".." to fit maxw. */
    String ellipsize(String s, float px, float maxw) {
        if (measure(s, px) <= maxw) return s;
        String t = s;
        while (t.length() > 1 && measure(t + "..", px) > maxw) t = t.substring(0, t.length() - 1);
        return t + "..";
    }

    // ---- procedural monochrome icons, ports of the nano CC ones ----

    void icoSpeaker(Canvas c, float cx, float cy, float r, int col) {
        rect(c, cx - r * 0.75f, cy - r * 0.35f, r * 0.4f, r * 0.7f, col);
        tri(c, cx - r * 0.35f, cy - r * 0.7f, cx - r * 0.35f, cy + r * 0.7f, cx + r * 0.15f, cy, col);
        tri(c, cx - r * 0.35f, cy - r * 0.7f, cx + r * 0.15f, cy, cx + r * 0.15f, cy - r * 0.9f, col);
        tri(c, cx - r * 0.35f, cy + r * 0.7f, cx + r * 0.15f, cy, cx + r * 0.15f, cy + r * 0.9f, col);
        ringArc(c, cx - r * 0.35f, cy, r * 0.55f, r * 0.68f, -0.7f, 0.7f, col);
        ringArc(c, cx - r * 0.35f, cy, r * 0.9f, r * 1.03f, -0.6f, 0.6f, withAlpha(col, 0.85f));
    }

    void icoSun(Canvas c, float cx, float cy, float r, int col) {
        disc(c, cx, cy, r * 0.5f, col);
        for (int k = 0; k < 8; k++) {
            final double a = k / 8.0 * 2.0 * Math.PI;
            final float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
            final float ix = cx + ca * r * 0.72f, iy = cy + sa * r * 0.72f;
            final float ex = cx + ca * r, ey = cy + sa * r;
            final float nx = -sa * 1.4f, ny = ca * 1.4f;
            tri(c, ix + nx, iy + ny, ix - nx, iy - ny, ex, ey, col);
        }
    }

    void icoMoon(Canvas c, float cx, float cy, float r, int col, int bite) {
        disc(c, cx, cy, r * 0.85f, col);
        disc(c, cx + r * 0.42f, cy - r * 0.28f, r * 0.72f, bite);
    }

    void icoCamera(Canvas c, float cx, float cy, float r, int col, int hole) {
        round(c, cx - r * 0.95f, cy - r * 0.55f, r * 1.9f, r * 1.25f, r * 0.28f, col);
        rect(c, cx - r * 0.35f, cy - r * 0.8f, r * 0.7f, r * 0.3f, col);
        disc(c, cx, cy + r * 0.05f, r * 0.42f, hole);
        disc(c, cx, cy + r * 0.05f, r * 0.26f, col);
    }

    void icoWifi(Canvas c, float cx, float cy, float r, int col) {
        final float y = cy + r * 0.5f;
        ringArc(c, cx, y, r * 1.15f, r * 1.35f, -2.36f, -0.78f, col);
        ringArc(c, cx, y, r * 0.7f, r * 0.9f, -2.2f, -0.94f, col);
        disc(c, cx, y, r * 0.22f, col);
    }

    void icoThermo(Canvas c, float cx, float cy, float r, int col) {
        round(c, cx - r * 0.22f, cy - r, r * 0.44f, r * 1.6f, r * 0.22f, col);
        disc(c, cx, cy + r * 0.75f, r * 0.5f, col);
    }

    void icoGauge(Canvas c, float cx, float cy, float r, int col) {
        final float y = cy + r * 0.30f;
        ringArc(c, cx, y, r * 0.74f, r * 0.98f, (float) Math.PI * 0.75f, (float) Math.PI * 2.25f, col);
        final double na = Math.PI * 1.66;
        final float nx = (float) Math.cos(na), ny = (float) Math.sin(na);
        final float tx = cx + nx * r * 0.86f, ty = y + ny * r * 0.86f;
        final float pnx = -ny * 1.7f, pny = nx * 1.7f;
        tri(c, cx + pnx, y + pny, cx - pnx, y - pny, tx, ty, col);
        disc(c, cx, y, r * 0.17f, col);
    }

    void icoSplitBri(Canvas c, float cx, float cy, float r, int col) {
        round(c, cx - r, cy - r * 0.7f, r * 0.9f, r * 1.4f, r * 0.2f, col);
        round(c, cx + r * 0.1f, cy - r * 0.7f, r * 0.9f, r * 1.4f, r * 0.2f, withAlpha(col, 0.42f));
    }

    void icoShader(Canvas c, float cx, float cy, float r, int col, int hole) {
        round(c, cx - r * 1.1f, cy - r * 0.8f, r * 2.2f, r * 1.5f, r * 0.25f, col);
        disc(c, cx, cy - r * 0.05f, r * 0.55f, hole);
        rect(c, cx - r * 0.5f, cy + r * 0.75f, r, r * 0.28f, col);
    }

    void icoMusic(Canvas c, float cx, float cy, float r, int col) {
        disc(c, cx - r * 0.45f, cy + r * 0.7f, r * 0.45f, col);
        rect(c, cx - r * 0.05f, cy - r * 0.9f, r * 0.24f, r * 1.7f, col);
        tri(c, cx - r * 0.05f, cy - r * 0.9f, cx + r * 0.7f, cy - r * 0.7f, cx + r * 0.7f, cy - r * 0.2f, col);
        tri(c, cx - r * 0.05f, cy - r * 0.9f, cx + r * 0.7f, cy - r * 0.2f, cx - r * 0.05f, cy - r * 0.35f, col);
    }

    void icoMouse(Canvas c, float cx, float cy, float r, int col) {
        final float tx = cx - r * 0.55f, ty = cy - r * 0.85f;
        tri(c, tx, ty, tx, ty + r * 1.35f, tx + r * 0.42f, ty + r, col);
        tri(c, tx, ty, tx + r * 0.42f, ty + r, tx + r * 1.05f, ty + r * 0.95f, col);
        final float bx = tx + r * 0.42f, by = ty + r;
        bar(c, bx, by, bx + r * 0.35f, by + r * 0.7f, 1.6f, col);
    }

    void icoFan(Canvas c, float cx, float cy, float r, int col) {
        for (int k = 0; k < 3; k++) {
            final double a = k / 3.0 * 2.0 * Math.PI - Math.PI * 0.5;
            final float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
            final float cb = (float) Math.cos(a + 0.9), sb = (float) Math.sin(a + 0.9);
            tri(c, cx + ca * r * 0.22f, cy + sa * r * 0.22f, cx + ca * r, cy + sa * r,
                    cx + cb * r * 0.85f, cy + sb * r * 0.85f, col);
            tri(c, cx + ca * r * 0.22f, cy + sa * r * 0.22f, cx + cb * r * 0.85f, cy + sb * r * 0.85f,
                    cx + cb * r * 0.3f, cy + sb * r * 0.3f, col);
        }
        disc(c, cx, cy, r * 0.24f, col);
    }

    void icoClose(Canvas c, float cx, float cy, float r, int col) {
        final float rr = r * 0.82f;
        bar(c, cx - rr, cy - rr, cx + rr, cy + rr, 2.2f, col);
        bar(c, cx + rr, cy - rr, cx - rr, cy + rr, 2.2f, col);
    }

    static int withAlpha(int col, float a) {
        final int al = (int) (((col >>> 24) & 0xff) * a + 0.5f);
        return (al << 24) | (col & 0xffffff);
    }
}

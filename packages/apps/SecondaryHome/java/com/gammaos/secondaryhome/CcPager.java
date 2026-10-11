package com.gammaos.secondaryhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

/**
 * Horizontal pager for the control centre pages (dashboard, applications, screen options).
 * Like the nano CC: a big mostly-horizontal drag pages (left = next, right = previous) and the
 * page offset eases in. A child that grabs a slider calls requestDisallowInterceptTouchEvent so
 * its drag is never turned into a swipe.
 */
final class CcPager extends ViewGroup {
    interface Listener { void onPageChanged(int page); }

    private int mPage;
    private float mOffset;            // in pages, eased toward mPage
    private ValueAnimator mAnim;
    private float mDownX, mDownY;
    private boolean mDragging;
    private final int mSlop;
    private Listener mListener;
    private final CcDraw mDots = new CcDraw();
    private boolean mDotTap;

    // Page indicator, in the 640x480 design space: under the dashboard cards (they end at 440).
    private static final float DOT_Y = 460f, DOT_R = 4f, DOT_GAP = 18f, PILL_W = 22f, HIT = 16f;

    CcPager(Context ctx) {
        super(ctx);
        mSlop = ViewConfiguration.get(ctx).getScaledTouchSlop();
        setWillNotDraw(false);
    }

    private float dotX(int i) {
        return CcDraw.DW * 0.5f + (i - (getChildCount() - 1) * 0.5f) * DOT_GAP;
    }

    /** Page dots over the pages; the active one is a pill that follows the swipe offset. */
    @Override
    protected void dispatchDraw(Canvas c) {
        super.dispatchDraw(c);
        final int n = getChildCount();
        if (n < 2) return;
        c.save();
        mDots.begin(c, getWidth(), getHeight());
        final int idle = CcDraw.rgb(0.28f, 0.30f, 0.38f);
        for (int i = 0; i < n; i++) mDots.disc(c, dotX(i), DOT_Y, DOT_R, idle);
        final float off = Math.max(0f, Math.min(n - 1f, mOffset));
        final float cx = dotX(0) + off * DOT_GAP;
        mDots.round(c, cx - PILL_W * 0.5f, DOT_Y - DOT_R, PILL_W, DOT_R * 2f, DOT_R,
                CcDraw.rgb(0.85f, 0.90f, 1f));
        c.restore();
    }

    /** Page dot under a view-space point, or -1. */
    private int dotAt(float x, float y) {
        final float u = Math.min(getWidth() / CcDraw.DW, getHeight() / CcDraw.DH);
        final float ox = (getWidth() - CcDraw.DW * u) * 0.5f, oy = (getHeight() - CcDraw.DH * u) * 0.5f;
        final float dx = (x - ox) / u, dy = (y - oy) / u;
        if (Math.abs(dy - DOT_Y) > HIT) return -1;
        for (int i = 0; i < getChildCount(); i++) if (Math.abs(dx - dotX(i)) <= DOT_GAP * 0.5f) return i;
        return -1;
    }

    void setListener(Listener l) { mListener = l; }

    int getPage() { return mPage; }

    void setPage(int page, boolean animate) {
        page = Math.max(0, Math.min(getChildCount() - 1, page));
        final boolean changed = page != mPage;
        mPage = page;
        if (mAnim != null) mAnim.cancel();
        if (!animate) {
            mOffset = page;
            layoutChildren();
        } else {
            mAnim = ValueAnimator.ofFloat(mOffset, page);
            mAnim.setDuration(260);
            mAnim.setInterpolator(new DecelerateInterpolator(1.6f));
            mAnim.addUpdateListener(a -> {
                mOffset = (float) a.getAnimatedValue();
                layoutChildren();
            });
            mAnim.start();
        }
        if (changed && mListener != null) mListener.onPageChanged(page);
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        final int w = MeasureSpec.getSize(wSpec), h = MeasureSpec.getSize(hSpec);
        setMeasuredDimension(w, h);
        final int cw = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY);
        final int ch = MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY);
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).measure(cw, ch);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int w = r - l, h = b - t;
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).layout(0, 0, w, h);
        layoutChildren();
    }

    private void layoutChildren() {
        final int w = getWidth();
        for (int i = 0; i < getChildCount(); i++) {
            final View v = getChildAt(i);
            final float x = (i - mOffset) * w;
            v.setTranslationX(x);
            invalidate();
            // off-screen pages are not drawn and not focusable
            v.setVisibility(Math.abs(i - mOffset) < 1f ? VISIBLE : INVISIBLE);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = e.getX();
                mDownY = e.getY();
                mDragging = false;
                mDotTap = dotAt(mDownX, mDownY) >= 0;
                return mDotTap;
            case MotionEvent.ACTION_MOVE: {
                final float dx = e.getX() - mDownX, dy = e.getY() - mDownY;
                if (Math.abs(dx) > mSlop * 2 && Math.abs(dx) > 2f * Math.abs(dy)) {
                    mDragging = true;
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        final int w = Math.max(1, getWidth());
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = e.getX();
                mDownY = e.getY();
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (!mDragging) return true;
                if (mAnim != null) mAnim.cancel();
                float off = mPage - (e.getX() - mDownX) / w;
                off = Math.max(-0.15f, Math.min(getChildCount() - 0.85f, off));
                mOffset = off;
                layoutChildren();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (mDotTap) {
                    mDotTap = false;
                    final int d = dotAt(e.getX(), e.getY());
                    if (e.getActionMasked() == MotionEvent.ACTION_UP && d >= 0) setPage(d, true);
                    return true;
                }
                if (!mDragging) return true;
                mDragging = false;
                final float dx = e.getX() - mDownX, dy = e.getY() - mDownY;
                // the nano CC pages on a 120 design-unit drag (of 640): 3/16 of the width
                int target = mPage;
                if (e.getActionMasked() == MotionEvent.ACTION_UP
                        && Math.abs(dx) > w * 0.1875f && Math.abs(dx) > 2f * Math.abs(dy)) {
                    target = dx < 0 ? mPage + 1 : mPage - 1;
                }
                setPage(target, true);
                return true;
            }
        }
        return true;
    }
}

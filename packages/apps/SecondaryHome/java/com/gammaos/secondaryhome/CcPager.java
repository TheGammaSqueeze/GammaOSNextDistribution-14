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

    CcPager(Context ctx) {
        super(ctx);
        mSlop = ViewConfiguration.get(ctx).getScaledTouchSlop();
        setWillNotDraw(false);
    }

    /** A modal layer drawn over the pages and the dots (the dashboard's option picker). */
    interface Overlay {
        boolean isOpen();
        void draw(Canvas c, int w, int h);
    }

    private Overlay mOverlay;

    void setOverlay(Overlay o) { mOverlay = o; }

    // Dot geometry in view pixels, as NanoMenu::renderCcPageDots sizes it from the panel height.
    private float dotR() { return Math.max(2f, getHeight() * 0.0105f); }
    private float dotGap() { return dotR() * 3.6f; }
    private float dotY() { return getHeight() - dotR() * 3.4f; }
    private float dotX(int i) {
        return getWidth() * 0.5f + (i - (getChildCount() - 1) * 0.5f) * dotGap();
    }

    /**
     * Page dots, a port of the nano CC's: each dot brightens and stretches into a short pill as
     * its page slides in, over a soft shadow so they read on cards and on the app grid alike.
     */
    @Override
    protected void dispatchDraw(Canvas c) {
        super.dispatchDraw(c);
        final int n = getChildCount();
        if (n >= 2) {
            final float r = dotR(), cy = dotY();
            for (int i = 0; i < n; i++) {
                final float act = Math.max(0f, Math.min(1f, 1f - Math.abs(mOffset - i)));
                final float cx = dotX(i);
                final float br = 0.30f + 0.62f * act;
                final float halfW = r * (1f + 1.4f * act);
                mDots.round(c, cx - halfW, cy - r + 1f, halfW * 2f, r * 2f, r, CcDraw.argb(0.35f, 0f, 0f, 0f));
                mDots.round(c, cx - halfW, cy - r, halfW * 2f, r * 2f, r, CcDraw.argb(0.92f, br, br, br));
            }
        }
        if (mOverlay != null && mOverlay.isOpen()) mOverlay.draw(c, getWidth(), getHeight());
    }

    /** Page dot under a view-space point, or -1. */
    private int dotAt(float x, float y) {
        if (mOverlay != null && mOverlay.isOpen()) return -1;
        final float hit = dotGap() * 0.5f;
        if (Math.abs(y - dotY()) > Math.max(hit, dotR() * 3f)) return -1;
        for (int i = 0; i < getChildCount(); i++) if (Math.abs(x - dotX(i)) <= hit) return i;
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

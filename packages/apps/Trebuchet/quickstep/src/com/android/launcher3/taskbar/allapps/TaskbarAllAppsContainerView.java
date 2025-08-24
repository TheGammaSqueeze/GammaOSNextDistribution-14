/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.taskbar.allapps;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.launcher3.R;
import com.android.launcher3.allapps.ActivityAllAppsContainerView;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.taskbar.overlay.TaskbarOverlayContext;

import java.util.Optional;

/** All apps container accessible from taskbar. */
public class TaskbarAllAppsContainerView extends
        ActivityAllAppsContainerView<TaskbarOverlayContext> {

    private @Nullable OnInvalidateHeaderListener mOnInvalidateHeaderListener;

    public TaskbarAllAppsContainerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TaskbarAllAppsContainerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    void setOnInvalidateHeaderListener(OnInvalidateHeaderListener onInvalidateHeaderListener) {
        mOnInvalidateHeaderListener = onInvalidateHeaderListener;
    }

    @Override
    protected View inflateSearchBar() {
        if (isSearchSupported()) {
            return super.inflateSearchBar();
        }

        // Remove top padding of header, since we do not have any search
        mHeader.setPadding(mHeader.getPaddingLeft(), 0,
                mHeader.getPaddingRight(), mHeader.getPaddingBottom());

        TaskbarAllAppsFallbackSearchContainer searchView =
                new TaskbarAllAppsFallbackSearchContainer(getContext(), null);
        searchView.setId(R.id.search_container_all_apps);
        searchView.setVisibility(GONE);
        return searchView;
    }

    @Override
    public void invalidateHeader() {
        super.invalidateHeader();
        Optional.ofNullable(mOnInvalidateHeaderListener).ifPresent(
                OnInvalidateHeaderListener::onInvalidateHeader);
    }

    @Override
    protected boolean isSearchSupported() {
        return FeatureFlags.ENABLE_ALL_APPS_SEARCH_IN_TASKBAR.get();
    }

    @Override
    public boolean isInAllApps() {
        // All apps is always open
        return true;
    }

    interface OnInvalidateHeaderListener {
        void onInvalidateHeader();
    }


    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        // Post to run after the hierarchy is fully inflated/attached so lookups don't race.
        post(this::forceFullWidthSearchForTaskbar);
        // Also re-apply on every layout pass; cheap and robust against config/IME changes.
        getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                forceFullWidthSearchForTaskbar();
            }
        });
    }

    /**
     * For taskbar-triggered All Apps on phone UI, make the search bar span the sheet width
     * instead of using the narrow floating pill width. Also relax header side paddings.
     */
    private void forceFullWidthSearchForTaskbar() {
        try {
            final View search = findViewById(R.id.search_container_all_apps);
            if (search != null) {
                final ViewGroup.LayoutParams lp = search.getLayoutParams();
                if (lp != null && lp.width != ViewGroup.LayoutParams.MATCH_PARENT) {
                    lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                    if (lp instanceof ViewGroup.MarginLayoutParams) {
                        ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
                        mlp.leftMargin = 0;
                        mlp.rightMargin = 0;
                    }
                    search.setLayoutParams(lp);
                    Log.d("GammaTaskbar", "AllApps: forced search width=match_parent for taskbar sheet");
                } else {
                    Log.d("GammaTaskbar", "AllApps: search width already match_parent or lp null");
                }

                // Walk up to header, expanding parents and clearing side paddings / center gravity.
                widenParentChainToMatchParent(search, /*levels=*/4);

                // Remove any TextView-level maxWidth limits within the search container.
                clearTextMaxWidthRecursive(search);

                // If header exists, relax any internal max-width constraints and side paddings.
                View header = mHeader;
                if (header != null) {
                    int top = header.getPaddingTop();
                    int bottom = header.getPaddingBottom();
                    if (header.getPaddingLeft() != 0 || header.getPaddingRight() != 0) {
                        header.setPadding(0, top, 0, bottom);
                        Log.d("GammaTaskbar", "AllApps: zeroed header side paddings for full-width search");
                    }
                    relaxHeaderMaxWidthViaReflection(header);
                } else {
                    Log.w("GammaTaskbar", "AllApps: header null; skipping header tweaks");
                }

                // Make sure the measured width matches our container width.
                final int containerW = Math.max(0,
                        getWidth() - getPaddingLeft() - getPaddingRight());
                if (containerW > 0) {
                    search.measure(
                            MeasureSpec.makeMeasureSpec(containerW, MeasureSpec.EXACTLY),
                            MeasureSpec.makeMeasureSpec(search.getMeasuredHeight(), MeasureSpec.AT_MOST));
                    search.requestLayout();
                }
            } else {
                Log.w("GammaTaskbar", "AllApps: search_container_all_apps not found");
            }
        } catch (Throwable t) {
            Log.e("GammaTaskbar", "AllApps: failed to force full-width search", t);
        }
    }

    /** Expand parent chain widths/paddings so children can use the full sheet width. */
    private void widenParentChainToMatchParent(View leaf, int levels) {
        View p = leaf;
        for (int i = 0; i < levels && p != null; i++) {
            final View parent = (View) p.getParent();
            if (parent == null) break;
            final ViewGroup.LayoutParams plp = parent.getLayoutParams();
            boolean changed = false;
            if (plp != null && plp.width != ViewGroup.LayoutParams.MATCH_PARENT) {
                plp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                parent.setLayoutParams(plp);
                changed = true;
            }
            if (parent.getPaddingLeft() != 0 || parent.getPaddingRight() != 0) {
                parent.setPadding(0, parent.getPaddingTop(), 0, parent.getPaddingBottom());
                changed = true;
            }
            if (parent instanceof LinearLayout) {
                LinearLayout ll = (LinearLayout) parent;
                if ((ll.getGravity() & Gravity.HORIZONTAL_GRAVITY_MASK) != Gravity.START) {
                    ll.setGravity((ll.getGravity() & ~Gravity.HORIZONTAL_GRAVITY_MASK)
                            | Gravity.START | Gravity.CENTER_VERTICAL);
                    changed = true;
                }
            }
            if (changed) {
                parent.requestLayout();
            }
            if (parent == mHeader) break; // Reached header; no need to go further.
            p = parent;
        }
    }

    /** Clear TextView/EditText maxWidth caps inside a subtree. */
    private void clearTextMaxWidthRecursive(View v) {
        if (v instanceof TextView) {
            TextView tv = (TextView) v;
            if (tv.getMaxWidth() > 0 && tv.getMaxWidth() < Integer.MAX_VALUE) {
                tv.setMaxWidth(Integer.MAX_VALUE);
            }
            if (tv.getMinWidth() > 0) {
                tv.setMinWidth(0);
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                clearTextMaxWidthRecursive(vg.getChildAt(i));
            }
        }
    }

    /**
     * Some Launcher builds clamp header content width via a private max-width field or method.
     * Try to relax it without depending on specific resource ids or class APIs.
     */
    private void relaxHeaderMaxWidthViaReflection(View header) {
        try {
            final int huge = 1_000_000;
            // Try a method first.
            try {
                java.lang.reflect.Method m =
                        header.getClass().getDeclaredMethod("setContentMaxWidth", int.class);
                m.setAccessible(true);
                m.invoke(header, huge);
                Log.d("GammaTaskbar", "AllApps: relaxed header content max width via method");
                return;
            } catch (NoSuchMethodException ignored) { }

            // Try common field names next.
            String[] candidates = new String[]{"mContentMaxWidth", "mMaxContentWidth", "mMaxWidth"};
            for (String name : candidates) {
                try {
                    java.lang.reflect.Field f = header.getClass().getDeclaredField(name);
                    if (f.getType() == int.class) {
                        f.setAccessible(true);
                        f.setInt(header, huge);
                        Log.d("GammaTaskbar", "AllApps: relaxed header max width via field " + name);
                        return;
                    }
                } catch (NoSuchFieldException ignored) { }
            }
            // Last resort: bump any int field that looks like a max width.
            for (java.lang.reflect.Field f : header.getClass().getDeclaredFields()) {
                if (f.getType() == int.class) {
                    String n = f.getName().toLowerCase();
                    if (n.contains("max") && n.contains("width")) {
                        f.setAccessible(true);
                        f.setInt(header, huge);
                        Log.d("GammaTaskbar", "AllApps: relaxed header max width via heuristic field " + f.getName());
                        return;
                    }
                }
            }
        } catch (Throwable t) {
            Log.w("GammaTaskbar", "AllApps: header max-width reflection failed", t);
        }
    }
}

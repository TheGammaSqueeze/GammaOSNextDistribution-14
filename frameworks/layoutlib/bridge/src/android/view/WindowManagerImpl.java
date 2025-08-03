/*
 * Copyright (C) 2020 The Android Open Source Project
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
 * limitations under the License
 */
package android.view;

import static android.view.View.SYSTEM_UI_FLAG_VISIBLE;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION;

import android.app.ResourcesManager;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.DisplayMetrics;
import android.view.Display.Mode;
import android.widget.FrameLayout;

import com.android.ide.common.rendering.api.ILayoutLog;
import com.android.internal.R;
import com.android.internal.policy.DecorView;
import com.android.layoutlib.bridge.Bridge;

import java.util.ArrayList;

public class WindowManagerImpl implements WindowManager {

    private final Context mContext;
    private final DisplayMetrics mMetrics;
    private final Display mDisplay;
    /**
     * Root view of the base window, new windows will be added on top of this.
     */
    private ViewGroup mBaseRootView;
    /**
     * Root view of the current window at the top of the display,
     * null if there is only the base window present.
     */
    private ViewGroup mCurrentRootView;

    public WindowManagerImpl(Context context, DisplayMetrics metrics) {
        mContext = context;
        mMetrics = metrics;

        DisplayInfo info = new DisplayInfo();
        info.logicalHeight = mMetrics.heightPixels;
        info.logicalWidth = mMetrics.widthPixels;
        info.supportedModes = new Mode[] {
                new Mode(0, mMetrics.widthPixels, mMetrics.heightPixels, 60f)
        };
        info.logicalDensityDpi = mMetrics.densityDpi;
        mDisplay = new Display(null, Display.DEFAULT_DISPLAY, info,
                DisplayAdjustments.DEFAULT_DISPLAY_ADJUSTMENTS);
    }

    public WindowManagerImpl createLocalWindowManager(Window parentWindow) {
        return this;
    }

    public WindowManagerImpl createPresentationWindowManager(Context displayContext) {
        Bridge.getLog().fidelityWarning(ILayoutLog.TAG_UNSUPPORTED,
                "The preview does not fully support multiple windows.",
                null, null, null);
        return this;
    }

    /**
     * Sets the window token to assign when none is specified by the client or
     * available from the parent window.
     *
     * @param token The default token to assign.
     */
    public void setDefaultToken(IBinder token) {

    }

    @Override
    public Display getDefaultDisplay() {
        return mDisplay;
    }


    @Override
    public void addView(View arg0, android.view.ViewGroup.LayoutParams arg1) {
        if (mBaseRootView == null) {
            return;
        }
        if (mCurrentRootView == null) {
            FrameLayout layout = new FrameLayout(mContext) {
                @Override
                public boolean dispatchTouchEvent(MotionEvent ev) {
                    float offsetX = - getX();
                    float offsetY = - getY();
                    View baseRootParent = (View)mBaseRootView.getParent();
                    if (baseRootParent != null) {
                        offsetX -= baseRootParent.getX();
                        offsetY -= baseRootParent.getY();
                    }
                    ev.offsetLocation(offsetX, offsetY);
                    return super.dispatchTouchEvent(ev);
                }

                @Override
                protected void measureChildWithMargins(View child, int parentWidthMeasureSpec,
                        int widthUsed, int parentHeightMeasureSpec, int heightUsed) {
                    // This reproduces ViewRootImpl#measureHierarchy as this FrameLayout should
                    // be treated as a ViewRoot.
                    ViewGroup.LayoutParams lp = child.getLayoutParams();
                    int parentWidth = MeasureSpec.getSize(parentWidthMeasureSpec);
                    int parentHeight = MeasureSpec.getSize(parentHeightMeasureSpec);
                    int childWidthMeasureSpec = 0;
                    int childHeightMeasureSpec = ViewRootImpl.getRootMeasureSpec(parentHeight,
                            lp.height, 0);
                    if (lp.width == WRAP_CONTENT) {
                        int baseSize =
                                mContext.getResources().getDimensionPixelSize(R.dimen.config_prefDialogWidth);
                        if (baseSize != 0 && baseSize < parentWidth) {
                            childWidthMeasureSpec = ViewRootImpl.getRootMeasureSpec(baseSize,
                                    lp.width, 0);
                        }
                    }
                    if (childWidthMeasureSpec == 0) {
                        childWidthMeasureSpec = ViewRootImpl.getRootMeasureSpec(parentWidth,
                                lp.width, 0);
                    }
                    child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
                }
            };
            // The window root view should not handle touch events.
            // Events need to be dispatched to the base view inside the window,
            // with coordinates shifted accordingly.
            layout.setOnTouchListener((v, event) -> {
                event.offsetLocation(-arg0.getX(), -arg0.getY());
                return arg0.dispatchTouchEvent(event);
            });
            int layoutMode = WRAP_CONTENT;
            if (arg0 instanceof DecorView) {
                // DecorView background should cover the entire screen
                layoutMode = MATCH_PARENT;
            }
            mBaseRootView.addView(layout, new FrameLayout.LayoutParams(layoutMode, layoutMode));
            mCurrentRootView = layout;
        }

        FrameLayout.LayoutParams frameLayoutParams = new FrameLayout.LayoutParams(arg1);
        if (arg1 instanceof WindowManager.LayoutParams) {
            LayoutParams params = (LayoutParams) arg1;
            frameLayoutParams.gravity = params.gravity;
            if ((params.flags & LayoutParams.FLAG_DIM_BEHIND) != 0) {
                mCurrentRootView.setBackgroundColor(Color.argb(params.dimAmount, 0, 0, 0));
            } else {
                int backgroundColor = Color.WHITE;
                Drawable background = mBaseRootView.getBackground();
                if (background == null) {
                    background = mBaseRootView.getRootView().getBackground();
                }
                if (background instanceof ColorDrawable) {
                    backgroundColor = ((ColorDrawable) background).getColor();
                }
                mCurrentRootView.setBackgroundColor(backgroundColor);
            }
        }
        mCurrentRootView.addView(arg0, frameLayoutParams);
        ViewRootImpl_Accessor.setChild(mBaseRootView.getViewRootImpl(), arg0);
    }

    @Override
    public void removeView(View arg0) {
        ViewRootImpl viewRootImpl = arg0.getViewRootImpl();
        if (mCurrentRootView != null) {
            mCurrentRootView.removeView(arg0);
            if (mBaseRootView != null && mCurrentRootView.getChildCount() == 0) {
                mBaseRootView.removeView(mCurrentRootView);
                mCurrentRootView = null;
            }
        }
        if (viewRootImpl != null && viewRootImpl.getView() == arg0) {
            View newRoot = null;
            if (mCurrentRootView != null && mCurrentRootView.getChildCount() > 0) {
                ArrayList<View> childrenList = mCurrentRootView.buildOrderedChildList();
                newRoot = childrenList.get(childrenList.size() - 1);
            } else if (mBaseRootView != null) {
                View root = mBaseRootView;
                while (root.getParent() instanceof View) {
                    root = (View)root.getParent();
                }
                newRoot = root;
            }
            ViewRootImpl_Accessor.setChild(viewRootImpl, newRoot);
        }
    }

    @Override
    public void updateViewLayout(View view, android.view.ViewGroup.LayoutParams params) {
        if (view == null) {
            throw new IllegalArgumentException("view must not be null");
        }
        if (!(params instanceof WindowManager.LayoutParams)) {
            throw new IllegalArgumentException("Params must be WindowManager.LayoutParams");
        }

        WindowManager.LayoutParams wparams = (WindowManager.LayoutParams)params;
        FrameLayout.LayoutParams lparams = new FrameLayout.LayoutParams(params);
        lparams.gravity = wparams.gravity;
        view.setLayoutParams(lparams);
        if (mCurrentRootView != null) {
            Rect bounds = new Rect();
            mBaseRootView.getBoundsOnScreen(bounds);
            mCurrentRootView.setX(wparams.x - bounds.left);
            mCurrentRootView.setY(wparams.y - bounds.top);
            mCurrentRootView.setElevation(view.getElevation());
        }
    }


    @Override
    public void removeViewImmediate(View arg0) {
        removeView(arg0);
    }

    @Override
    public void requestAppKeyboardShortcuts(
            KeyboardShortcutsReceiver receiver, int deviceId) {
    }

    @Override
    public Region getCurrentImeTouchRegion() {
        return null;
    }

    @Override
    public void setShouldShowWithInsecureKeyguard(int displayId, boolean shouldShow) {
        // pass
    }

    @Override
    public void setShouldShowSystemDecors(int displayId, boolean shouldShow) {
        // pass
    }

    @Override
    public void setDisplayImePolicy(int displayId, int imePolicy) {
        // pass
    }

    @Override
    public WindowMetrics getCurrentWindowMetrics() {
        final Rect bound = getCurrentBounds(mContext);

        return new WindowMetrics(bound, computeWindowInsets());
    }

    private static Rect getCurrentBounds(Context context) {
        synchronized (ResourcesManager.getInstance()) {
            return context.getResources().getConfiguration().windowConfiguration.getBounds();
        }
    }

    @Override
    public WindowMetrics getMaximumWindowMetrics() {
        return new WindowMetrics(getMaximumBounds(), computeWindowInsets());
    }

    private Rect getMaximumBounds() {
        final Point displaySize = new Point();
        mDisplay.getRealSize(displaySize);
        return new Rect(0, 0, displaySize.x, displaySize.y);
    }

    private WindowInsets computeWindowInsets() {
        try {
            final InsetsState insetsState = new InsetsState();
            WindowManagerGlobal.getWindowManagerService().getWindowInsets(mContext.getDisplayId(),
                    null /* token */, insetsState);
            final Configuration config = mContext.getResources().getConfiguration();
            final boolean isScreenRound = config.isScreenRound();
            final int activityType = config.windowConfiguration.getActivityType();
            return insetsState.calculateInsets(getCurrentBounds(mContext),
                    null /* ignoringVisibilityState */, isScreenRound, SOFT_INPUT_ADJUST_NOTHING,
                    0 /* legacySystemUiFlags */, SYSTEM_UI_FLAG_VISIBLE, TYPE_APPLICATION,
                    activityType, null /* typeSideMap */);
        } catch (RemoteException ignore) {
        }
        return null;
    }

    // ---- Extra methods for layoutlib ----

    public void setBaseRootView(ViewGroup baseRootView) {
        // If used within Compose Preview, use the ComposeViewAdapter as the root
        // so that the preview attributes are handled correctly.
        ViewGroup composableRoot = findComposableRoot(baseRootView);
        mBaseRootView = composableRoot != null ? composableRoot : baseRootView;
    }

    private ViewGroup findComposableRoot(ViewGroup baseRootView) {
        if (baseRootView.getClass().getName().endsWith("ComposeViewAdapter")) {
            return baseRootView;
        }
        for (int i = 0; i < baseRootView.getChildCount(); i++) {
            View child = baseRootView.getChildAt(i);
            if (child instanceof ViewGroup) {
                ViewGroup composableRoot = findComposableRoot((ViewGroup)child);
                if (composableRoot != null) {
                    return composableRoot;
                }
            }
        }
        return null;
    }

    public ViewGroup getCurrentRootView() {
        return mCurrentRootView;
    }
}

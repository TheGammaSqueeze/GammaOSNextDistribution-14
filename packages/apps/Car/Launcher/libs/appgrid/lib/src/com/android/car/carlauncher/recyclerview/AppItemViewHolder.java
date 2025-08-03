/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.car.carlauncher.recyclerview;

import static android.view.View.DRAG_FLAG_GLOBAL;
import static android.view.View.DRAG_FLAG_OPAQUE;

import static com.android.car.carlauncher.AppGridConstants.AppItemBoundDirection;
import static com.android.car.carlauncher.AppGridConstants.PageOrientation;
import static com.android.car.carlauncher.AppGridConstants.isHorizontal;
import static com.android.car.hidden.apis.HiddenApiAccess.DRAG_FLAG_REQUEST_SURFACE_FOR_RETURN_ANIMATION;

import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Pair;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.android.car.carlauncher.AppGridActivity;
import com.android.car.carlauncher.AppGridPageSnapper.AppGridPageSnapCallback;
import com.android.car.carlauncher.AppItemDragShadowBuilder;
import com.android.car.carlauncher.AppMetaData;
import com.android.car.carlauncher.R;

/**
 * App item view holder that contains the app icon and name.
 */
public class AppItemViewHolder extends RecyclerView.ViewHolder {
    private static final String APP_ITEM_DRAG_TAG = "com.android.car.launcher.APP_ITEM_DRAG_TAG";
    private final long mReleaseAnimationDurationMs;
    private final long mLongPressAnimationDurationMs;
    private final long mDropAnimationDelayMs;
    private final int mHighlightTransitionDurationMs;
    private final int mIconSize;
    private final int mIconScaledSize;
    private final Context mContext;
    private final LinearLayout mAppItemView;
    private final ImageView mAppIcon;
    private final TextView mAppName;
    private final AppItemDragCallback mDragCallback;
    private final AppGridPageSnapCallback mSnapCallback;
    private final boolean mConfigReorderAllowed;
    private final int mThresholdToStartDragDrop;
    private Rect mPageBound;

    @PageOrientation
    private int mPageOrientation;
    @AppItemBoundDirection
    private int mDragExitDirection;

    private boolean mHasAppMetadata;
    private ComponentName mComponentName;
    private Point mAppIconCenter;
    private TransitionDrawable mBackgroundHighlight;
    private int mAppItemWidth;
    private int mAppItemHeight;
    private boolean mIsTargeted;
    private boolean mCanStartDragAction;

    /**
     * Information describing state of the recyclerview when this view holder was last rebinded.
     *
     * {@param isDistractionOptimizationRequired} true if driving restriction should be required.
     * {@param pageBound} the bounds of the recyclerview containing this view holder.
     */
    public static class BindInfo {
        private final boolean mIsDistractionOptimizationRequired;
        private final Rect mPageBound;
        private final AppGridActivity.Mode mMode;

        public BindInfo(boolean isDistractionOptimizationRequired,
                Rect pageBound,
                AppGridActivity.Mode mode) {
            this.mIsDistractionOptimizationRequired = isDistractionOptimizationRequired;
            this.mPageBound = pageBound;
            this.mMode = mode;
        }

        public BindInfo(boolean isDistractionOptimizationRequired, Rect pageBound) {
            this(isDistractionOptimizationRequired, pageBound, AppGridActivity.Mode.ALL_APPS);
        }
    }

    public AppItemViewHolder(View view, Context context, AppItemDragCallback dragCallback,
            AppGridPageSnapCallback snapCallback) {
        super(view);
        mContext = context;
        mAppItemView = view.findViewById(R.id.app_item);
        mAppIcon = mAppItemView.findViewById(R.id.app_icon);
        mAppName = mAppItemView.findViewById(R.id.app_name);
        mDragCallback = dragCallback;
        mSnapCallback = snapCallback;

        mIconSize = context.getResources().getDimensionPixelSize(R.dimen.app_icon_size);
        mConfigReorderAllowed = context.getResources().getBoolean(R.bool.config_allow_reordering);
        // distance that users must drag (hold and attempt to move the app icon) to initiate
        // reordering, measured in pixels on screen.
        mThresholdToStartDragDrop = context.getResources().getDimensionPixelSize(
                R.dimen.threshold_to_start_drag_drop);
        mPageOrientation = context.getResources().getBoolean(R.bool.use_vertical_app_grid)
                ? PageOrientation.VERTICAL : PageOrientation.HORIZONTAL;

        mIconScaledSize = context.getResources().getDimensionPixelSize(
                R.dimen.app_icon_scaled_size);
        // duration for animating the resizing of app icon on long press
        mLongPressAnimationDurationMs = context.getResources().getInteger(
                R.integer.ms_long_press_animation_duration);
        // duration for animating the resizing after long press is released
        mReleaseAnimationDurationMs = context.getResources().getInteger(
                R.integer.ms_release_animation_duration);
        // duration to animate the highlighting of view holder when it is targeted during drag drop
        mHighlightTransitionDurationMs = context.getResources().getInteger(
                R.integer.ms_background_highlight_duration);
        // delay before animating the drop animation when a valid drop event has been received
        mDropAnimationDelayMs = context.getResources().getInteger(
                R.integer.ms_drop_animation_delay);
    }

    /**
     * Binds the grid app item view with the app metadata.
     *
     * @param app AppMetaData to be displayed. Pass {@code null} will empty out the viewHolder.
     */
    public void bind(@Nullable AppMetaData app, @NonNull BindInfo bindInfo) {
        resetViewHolder();
        if (app == null) {
            return;
        }
        boolean isDistractionOptimizationRequired = bindInfo.mIsDistractionOptimizationRequired;
        mPageBound = bindInfo.mPageBound;
        AppGridActivity.Mode mode = bindInfo.mMode;

        mHasAppMetadata = true;
        mAppItemView.setFocusable(true);
        mAppName.setText(app.getDisplayName());
        mAppIcon.setImageDrawable(app.getIcon());
        mAppIcon.setAlpha(1.f);
        mComponentName = app.getComponentName();

        Drawable highlightedLayer = mContext.getDrawable(R.drawable.app_item_highlight);
        Drawable emptyLayer = mContext.getDrawable(R.drawable.app_item_highlight);
        emptyLayer.setAlpha(0);
        mBackgroundHighlight = new TransitionDrawable(new Drawable[]{emptyLayer, highlightedLayer});
        mBackgroundHighlight.resetTransition();
        mAppItemView.setBackground(mBackgroundHighlight);

        // app icon's relative location within view holders are only measurable after it is drawn

        // during a drag and drop operation, the user could scroll to another page and return to the
        // previous page, so we need to rebind the app with the correct visibility.
        setStateSelected(mComponentName.equals(mDragCallback.mSelectedComponent));

        boolean isLaunchableDistractionOptimized =
                !isDistractionOptimizationRequired || app.getIsDistractionOptimized();
        boolean isDisabledByTos = app.getIsDisabledByTos();
        boolean isLaunchable = isLaunchableDistractionOptimized || isDisabledByTos;

        int opacity = getOpacity(isLaunchable, isDisabledByTos);
        mAppIcon.setAlpha(mContext.getResources().getFloat(opacity));

        if (isLaunchable) {
            View.OnClickListener appLaunchListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    app.getLaunchCallback().accept(mContext);
                    mSnapCallback.notifySnapToPosition(getAbsoluteAdapterPosition());
                }
            };
            mAppItemView.setOnClickListener(appLaunchListener);
            mAppIcon.setOnClickListener(appLaunchListener);
            // long click actions should not be enabled when driving
            if (!isDistractionOptimizationRequired) {
                View.OnLongClickListener longPressListener = new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        // display set shortcut pop-up for force stop
                        app.getAlternateLaunchCallback().accept(Pair.create(mContext, v));
                        // drag and drop should only start after long click animation is complete
                        mDragCallback.notifyItemLongPressed(true);
                        mDragCallback.scheduleDragTask(new Runnable() {
                            @Override
                            public void run() {
                                mCanStartDragAction = true;
                            }
                        }, mLongPressAnimationDurationMs);
                        animateIconResize(/* scale */ ((float) mIconScaledSize / mIconSize),
                                /* duration */ mLongPressAnimationDurationMs);
                        return true;
                    }
                };
                mAppIcon.setLongClickable(true);
                mAppIcon.setOnLongClickListener(longPressListener);
                mAppIcon.setOnTouchListener(new View.OnTouchListener() {
                    private float mActionDownX;
                    private float mActionDownY;

                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        int action = event.getAction();
                        if (action == MotionEvent.ACTION_DOWN) {
                            mActionDownX = event.getX();
                            mActionDownY = event.getY();
                            mCanStartDragAction = false;
                        } else if (action == MotionEvent.ACTION_MOVE
                                && shouldStartDragAndDrop(event,
                                mActionDownX,
                                mActionDownY,
                                mode)) {
                            startDragAndDrop(app.getComponentName(), event.getX(), event.getY());
                            mCanStartDragAction = false;
                        } else if (action == MotionEvent.ACTION_UP
                                || action == MotionEvent.ACTION_CANCEL) {
                            animateIconResize(/* scale */ 1.f,
                                    /* duration */ mReleaseAnimationDurationMs);
                            mDragCallback.cancelDragTasks();
                            mDragCallback.notifyItemLongPressed(false);
                            mCanStartDragAction = false;
                        }
                        return false;
                    }
                });
            }
        } else {
            String warningText = mContext.getResources()
                    .getString(R.string.driving_toast_text, app.getDisplayName());
            View.OnClickListener appLaunchListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Toast.makeText(mContext, warningText, Toast.LENGTH_LONG).show();
                }
            };
            mAppItemView.setOnClickListener(appLaunchListener);
            mAppIcon.setOnClickListener(appLaunchListener);

            mAppIcon.setLongClickable(false);
            mAppIcon.setOnLongClickListener(null);
            mAppIcon.setOnTouchListener(null);
        }
    }

    void animateIconResize(float scale, long duration) {
        mAppIcon.animate().setDuration(duration).scaleX(scale);
        mAppIcon.animate().setDuration(duration).scaleY(scale);
    }

    /**
     * Transforms the app icon into the drop shadow's drop location in preparation for animateDrop,
     * which should be dispatched by AppGridItemAnimator shortly after prepareForDropAnimation.
     */
    public void prepareForDropAnimation() {
        // dragOffset is the offset between dragged icon center and users finger touch point
        int dragOffsetX = mDragCallback.mDragPoint.x - mIconScaledSize / 2;
        int dragOffsetY = mDragCallback.mDragPoint.y - mIconScaledSize / 2;
        // draggedIconCenter is the center of the dropped app icon, after the user finger touch
        // point offset is subtracted to another
        int draggedIconCenterX = mDragCallback.mDropPoint.x - dragOffsetX;
        int draggedIconCenterY = mDragCallback.mDropPoint.y - dragOffsetY;
        // dx and dx are the offset to translate between the dragged icon and dropped location
        int dx = draggedIconCenterX - mDragCallback.mDropDestination.x;
        int dy = draggedIconCenterY - mDragCallback.mDropDestination.y;
        mAppIcon.setScaleX((float) mIconScaledSize / mIconSize);
        mAppIcon.setScaleY((float) mIconScaledSize / mIconSize);
        mAppIcon.setAlpha(1.f);
        mAppIcon.setTranslationX(dx);
        mAppIcon.setTranslationY(dy);
        mAppItemView.setTranslationZ(.5f);
        mAppName.setTranslationZ(.5f);
        mAppIcon.setTranslationZ(1.f);
    }

    /**
     * Resets Z axis translation of all views contained by the view holder.
     */
    public void resetTranslationZ() {
        mAppItemView.setTranslationZ(0.f);
        mAppIcon.setTranslationZ(0.f);
        mAppName.setTranslationZ(0.f);
    }

    /**
     * Animates the drop transition back to the original app icon location.
     */
    public ViewPropertyAnimator getDropAnimation() {
        return mAppIcon.animate()
                .translationX(0).translationY(0)
                .scaleX(1.f).scaleY(1.f)
                .setStartDelay(mDropAnimationDelayMs);
    }

    private void resetViewHolder() {
        // TODO: Create a different item for empty app item.
        mHasAppMetadata = false;

        mAppItemView.setOnDragListener(new AppItemOnDragListener());
        mAppItemView.setFocusable(false);
        mAppItemView.setOnClickListener(null);

        mAppIcon.setLongClickable(false);
        mAppIcon.setOnLongClickListener(null);
        mAppIcon.setOnTouchListener(null);
        mAppIcon.setAlpha(0.f);
        mAppIcon.setOutlineProvider(null);

        mAppIcon.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                // remove listener since icon only need to be measured once
                mAppIcon.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                Rect appIconBound = new Rect();
                mAppIcon.getDrawingRect(appIconBound);
                mAppItemView.offsetDescendantRectToMyCoords(mAppIcon, appIconBound);
                mAppIconCenter = new Point(/* x */ (appIconBound.right + appIconBound.left) / 2,
                        /* y */ (appIconBound.bottom + appIconBound.top) / 2);
                mAppItemWidth = mAppItemView.getWidth();
                mAppItemHeight = mAppItemView.getHeight();
            }
        });

        mAppItemView.setBackground(null);
        mAppIcon.setImageDrawable(null);
        mAppName.setText(null);

        mDragExitDirection = AppItemBoundDirection.NONE;
    }

    private void setStateTargeted(boolean targeted) {
        if (mIsTargeted == targeted) return;
        mIsTargeted = targeted;
        if (targeted) {
            mDragCallback.notifyItemTargeted(AppItemViewHolder.this);
            mBackgroundHighlight.startTransition(mHighlightTransitionDurationMs);
            return;
        }
        mDragCallback.notifyItemTargeted(null);
        mBackgroundHighlight.resetTransition();
    }

    private void setStateSelected(boolean selected) {
        if (selected) {
            mAppIcon.setAlpha(0.f);
            return;
        }
        if (mHasAppMetadata) {
            mAppIcon.setAlpha(1.f);
        }
    }


    private boolean shouldStartDragAndDrop(MotionEvent event, float actionDownX,
            float actionDownY, AppGridActivity.Mode mode) {
        // If App Grid is not in all apps mode, we should not allow drag and drop
        if (mode != AppGridActivity.Mode.ALL_APPS) {
            return false;
        }
        // the move event should be with in the bounds of the app icon
        boolean isEventWithinIcon = event.getX() >= 0 && event.getY() >= 0
                && event.getX() < mIconScaledSize && event.getY() < mIconScaledSize;
        // the move event should be further by more than mThresholdToStartDragDrop pixels
        // away from the initial touch input.
        boolean isDistancePastThreshold = Math.hypot(/* dx */ Math.abs(event.getX() - actionDownX),
                /* dy */ event.getY() - actionDownY) > mThresholdToStartDragDrop;
        return mConfigReorderAllowed && mCanStartDragAction && isEventWithinIcon
                && isDistancePastThreshold;
    }

    private void startDragAndDrop(ComponentName componentName, float eventX, float eventY) {
        ClipData clipData = ClipData.newPlainText(/* label= */ APP_ITEM_DRAG_TAG,
                /* text= */ componentName.flattenToString());

        // since the app icon is scaled, the touch point that users should be holding when drag
        // shadow is deployed should also be scaled
        Point dragPoint = new Point(/* x */ (int) (eventX / mIconSize * mIconScaledSize),
                /* y */ (int) (eventY / mIconSize * mIconScaledSize));

        AppItemDragShadowBuilder dragShadowBuilder = new AppItemDragShadowBuilder(mAppIcon,
                /* touchPointX */ dragPoint.x, /* touchPointX */ dragPoint.y,
                /* size */ mIconSize, /* scaledSize */ mIconScaledSize);
        mAppIcon.startDragAndDrop(clipData, /* dragShadowBuilder */ dragShadowBuilder,
                /* myLocalState */ null, /* flags */ DRAG_FLAG_OPAQUE | DRAG_FLAG_GLOBAL
                        | DRAG_FLAG_REQUEST_SURFACE_FOR_RETURN_ANIMATION);

        mDragCallback.notifyItemSelected(AppItemViewHolder.this, dragPoint);
    }

    class AppItemOnDragListener implements View.OnDragListener {
        @Override
        public boolean onDrag(View view, DragEvent event) {
            int action = event.getAction();
            if (mHasAppMetadata) {
                if (action == DragEvent.ACTION_DRAG_STARTED) {
                    if (isSelectedViewHolder()) {
                        setStateSelected(true);
                    }
                } else if (action == DragEvent.ACTION_DRAG_LOCATION && inScrollStateIdle()) {
                    boolean shouldTargetViewHolder = isTargetIconVisible()
                            && isDraggedIconInBound(event)
                            && mDragCallback.mSelectedComponent != null;
                    setStateTargeted(shouldTargetViewHolder);
                } else if (action == DragEvent.ACTION_DRAG_EXITED && inScrollStateIdle()) {
                    setStateTargeted(false);
                } else if (action == DragEvent.ACTION_DROP) {
                    if (isTargetedViewHolder()) {
                        Point dropPoint = new Point(/* x */ (int) event.getX(),
                                /* y */ (int) event.getY());
                        mDragCallback.notifyItemDropped(dropPoint);
                    }
                    setStateTargeted(false);
                }
            }
            if (action == DragEvent.ACTION_DRAG_ENTERED && inScrollStateIdle()) {
                mDragCallback.notifyItemDragged();
            }
            if (action == DragEvent.ACTION_DRAG_LOCATION && inScrollStateIdle()) {
                mDragExitDirection = getClosestBoundDirection(event.getX(), event.getY());
                mDragCallback.notifyItemDragged();
            }
            if (action == DragEvent.ACTION_DRAG_EXITED && inScrollStateIdle()) {
                mDragCallback.notifyDragExited(AppItemViewHolder.this, mDragExitDirection);
                mDragExitDirection = AppItemBoundDirection.NONE;
            }
            if (event.getAction() == DragEvent.ACTION_DRAG_ENDED) {
                mDragExitDirection = AppItemBoundDirection.NONE;
                setStateSelected(false);
            }
            if (action == DragEvent.ACTION_DROP) {
                return false;
            }
            return true;
        }
    }

    private boolean isSelectedViewHolder() {
        return mComponentName != null && mComponentName.equals(mDragCallback.mSelectedComponent);
    }

    private boolean isTargetedViewHolder() {
        return mComponentName != null && mComponentName.equals(mDragCallback.mTargetedComponent);
    }

    private boolean inScrollStateIdle() {
        return mSnapCallback.getScrollState() == RecyclerView.SCROLL_STATE_IDLE;
    }

    /**
     * Returns whether this view holder's icon is visible to the user.
     *
     * Since the edge of the view holder from the previous/next may also receive drop events, a
     * valid drop target should have its app icon be visible to the user.
     */
    private boolean isTargetIconVisible() {
        if (mAppIcon == null || mAppIcon.getMeasuredWidth() == 0) {
            return false;
        }
        final Rect bound = new Rect();
        mAppIcon.getGlobalVisibleRect(bound);
        return bound.intersect(mPageBound);
    }

    private boolean isDraggedIconInBound(DragEvent event) {
        int iconLeft = (int) event.getX() - mDragCallback.mDragPoint.x;
        int iconTop = (int) event.getY() - mDragCallback.mDragPoint.y;
        return iconLeft >= 0 && iconTop >= 0 && (iconLeft + mIconScaledSize) < mAppItemWidth
                && (iconTop + mIconScaledSize) < mAppItemHeight;
    }

    @AppItemBoundDirection
    int getClosestBoundDirection(float eventX, float eventY) {
        float cutoffThreshold = .25f;
        if (isHorizontal(mPageOrientation)) {
            float horizontalPosition = eventX / mAppItemWidth;
            if (horizontalPosition < cutoffThreshold) {
                return AppItemBoundDirection.LEFT;
            } else if (horizontalPosition > (1 - cutoffThreshold)) {
                return AppItemBoundDirection.RIGHT;
            }
            return AppItemBoundDirection.NONE;
        }
        float verticalPosition = eventY / mAppItemHeight;
        if (verticalPosition < .5f) {
            return AppItemBoundDirection.TOP;
        } else if (verticalPosition > (1 - cutoffThreshold)) {
            return AppItemBoundDirection.BOTTOM;
        }
        return AppItemBoundDirection.NONE;
    }

    public boolean isMostRecentlySelected() {
        return mComponentName != null
                && mComponentName.equals(mDragCallback.getPreviousSelectedComponent());
    }

    /**
     * A Callback contract between AppItemViewHolders and its listener. There are multiple view
     * holders updating the callback but there should only be one listener.
     *
     * Drag drop operations will be started and listened to by each AppItemViewHolder, so all
     * visual elements should be handled directly by the AppItemViewHolder. This class should only
     * be used to communicate adapter data position changes.
     */
    public static class AppItemDragCallback {
        private static final int NONE = -1;
        private final AppItemDragListener mDragListener;
        private final Handler mHandler;
        private ComponentName mPreviousSelectedComponent;
        private ComponentName mSelectedComponent;
        private ComponentName mTargetedComponent;
        private AppItemViewHolder mTargetedViewHolder;
        private int mSelectedGridIndex = NONE;
        private int mTargetedGridIndex = NONE;
        // x y coordinate within the source app icon that the user finger is holding
        private Point mDragPoint;
        // x y coordinate within the viewHolder the drop event was registered
        private Point mDropPoint;
        // x y coordinate within the viewHolder which the drop animation should translate to
        private Point mDropDestination;

        public AppItemDragCallback(AppItemDragListener listener) {
            mDragListener = listener;
            mHandler = new Handler(Looper.getMainLooper());
        }

        /**
         * The preparation step of drag drop process. Called when a long press gesture has been
         * inputted or cancelled by the user.
         */
        public void notifyItemLongPressed(boolean isLongPressed) {
            mDragListener.onItemLongPressed(isLongPressed);
        }

        /**
         * The initial step of the drag drop process. Called when the drag shadow of an app icon has
         * been created, and should be immediately set as the drag source.
         */
        public void notifyItemSelected(AppItemViewHolder viewHolder, Point dragPoint) {
            mDragPoint = new Point(dragPoint);
            mDropDestination = new Point(viewHolder.mAppIconCenter);
            mSelectedComponent = viewHolder.mComponentName;
            mSelectedGridIndex = viewHolder.getAbsoluteAdapterPosition();
            mDragListener.onItemSelected(mSelectedGridIndex);
        }

        /**
         * The second step of the drag drop process. Called when a drag shadow enters the bounds of
         * a view holder (including the view holder containing the dragged icon itself).
         */
        public void notifyItemTargeted(@Nullable AppItemViewHolder viewHolder) {
            if (mTargetedViewHolder != null && !mTargetedViewHolder.equals(viewHolder)) {
                mTargetedViewHolder.setStateTargeted(false);
            }
            if (viewHolder == null) {
                mTargetedComponent = null;
                mTargetedViewHolder = null;
                mTargetedGridIndex = NONE;
                return;
            }
            mTargetedComponent = viewHolder.mComponentName;
            mTargetedViewHolder = viewHolder;
            mTargetedGridIndex = viewHolder.getAbsoluteAdapterPosition();
        }

        /**
         * An intermediary step of the drag drop process. Called the drag shadow enters the
         * view holder.
         */
        public void notifyItemDragged() {
            mDragListener.onItemDragged();
        }

        /**
         * An intermediary step of the drag drop process. Called the drag shadow is dragged outside
         * the view holder.
         */
        public void notifyDragExited(@NonNull AppItemViewHolder viewHolder,
                @AppItemBoundDirection int exitDirection) {
            int gridPosition = viewHolder.getAbsoluteAdapterPosition();
            mDragListener.onDragExited(gridPosition, exitDirection);
        }

        /**
         * The last step of drag and drop. Called when a ACTION_DROP event has been received by a
         * view holder.
         *
         * Note that this event may never be called if the ACTION_DROP event was consumed by
         * another onDragListener.
         */
        public void notifyItemDropped(Point dropPoint) {
            mDropPoint = new Point(dropPoint);
            if (mSelectedGridIndex != NONE && mTargetedGridIndex != NONE) {
                mDragListener.onItemDropped(mSelectedGridIndex, mTargetedGridIndex);
                resetCallbackState();
            }
        }

        /** Returns the previously selected component. */
        public ComponentName getPreviousSelectedComponent() {
            return mPreviousSelectedComponent;
        }

        /** Reset component and callback state after a drag drop event has concluded */
        public void resetCallbackState() {
            if (mSelectedComponent != null) {
                mPreviousSelectedComponent = mSelectedComponent;
            }
            mSelectedComponent = mTargetedComponent = null;
            mSelectedGridIndex = mTargetedGridIndex = NONE;
        }

        /** Schedules a delayed task that enables drag and drop to start */
        public void scheduleDragTask(Runnable runnable, long delay) {
            mHandler.postDelayed(runnable, delay);
        }

        /** Cancels all schedules tasks (i.e cancels intent to start drag drop) */
        public void cancelDragTasks() {
            mHandler.removeCallbacksAndMessages(null);
        }
    }

    /**
     * Listener class that should be implemented by AppGridActivity.
     */
    public interface AppItemDragListener {
        /** Listener method called during AppItemDragCallback.notifyLongPressed */
        void onItemLongPressed(boolean longPressed);

        /** Listener method called during AppItemDragCallback.notifyItemSelected */
        void onItemSelected(int gridPositionFrom);

        /** Listener method called during AppItemDragCallback.notifyDragEntered */
        void onItemDragged();

        /** Listener method called during AppItemDragCallback.notifyDragExited */
        void onDragExited(int gridPosition, @AppItemBoundDirection int exitDirection);

        /** Listener method called during AppItemDragCallback.notifyItemDropped */
        void onItemDropped(int gridPositionFrom, int gridPositionTo);
    }

    private int getOpacity(boolean isLaunchable, boolean isDisabledByTos) {
        if (isDisabledByTos) {
            return R.dimen.app_icon_opacity_tos_disabled;
        }
        if (isLaunchable) {
            return R.dimen.app_icon_opacity;
        }
        return R.dimen.app_icon_opacity_unavailable;
    }
}

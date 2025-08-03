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
 * limitations under the License.
 */

package com.android.systemui.car.systembar;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_UNDEFINED;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;

import static com.android.systemui.car.users.CarSystemUIUserUtil.getCurrentUserHandle;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.ActivityTaskManager;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.RemoteException;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.systemui.R;
import com.android.systemui.car.window.OverlayViewController;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.AlphaOptimizedImageView;

import java.net.URISyntaxException;

/**
 * CarSystemBarButton is an image button that allows for a bit more configuration at the
 * xml file level. This allows for more control via overlays instead of having to update
 * code.
 */
public class CarSystemBarButton extends LinearLayout implements
        OverlayViewController.OverlayViewStateListener {

    private static final String TAG = "CarSystemBarButton";
    private static final String BUTTON_FILTER_DELIMITER = ";";
    private static final String EXTRA_BUTTON_CATEGORIES = "categories";
    private static final String EXTRA_BUTTON_PACKAGES = "packages";
    private static final String EXTRA_DIALOG_CLOSE_REASON = "reason";
    private static final String DIALOG_CLOSE_REASON_CAR_SYSTEMBAR_BUTTON = "carsystembarbutton";
    private static final float DEFAULT_SELECTED_ALPHA = 1f;
    private static final float DEFAULT_UNSELECTED_ALPHA = 0.75f;
    private static final float DISABLED_ALPHA = 0.25f;

    private final Context mContext;
    private final ActivityManager mActivityManager;
    @Nullable
    private UserTracker mUserTracker;
    private AlphaOptimizedImageView mIcon;
    private AlphaOptimizedImageView mMoreIcon;
    private ImageView mUnseenIcon;
    private String mIntent;
    private String mLongIntent;
    private boolean mBroadcastIntent;
    /** Whether to clear the backstack (i.e. put the home activity directly behind) when pressed */
    private boolean mClearBackStack;
    private boolean mHasUnseen = false;
    private boolean mSelected = false;
    private boolean mDisabled = false;
    private float mSelectedAlpha;
    private float mUnselectedAlpha;
    private int mSelectedIconResourceId;
    private int mIconResourceId;
    private Drawable mAppIcon;
    private boolean mIsDefaultAppIconForRoleEnabled;
    private boolean mToggleSelectedState;
    private String[] mComponentNames;
    /** App categories that are to be used with this widget */
    private String[] mButtonCategories;
    /** App packages that are allowed to be used with this widget */
    private String[] mButtonPackages;
    /** Whether to display more icon beneath the primary icon when the button is selected */
    private boolean mShowMoreWhenSelected = false;
    /** Whether to highlight the button if the active application is associated with it */
    private boolean mHighlightWhenSelected = false;
    private Runnable mOnClickWhileDisabledRunnable;

    public CarSystemBarButton(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Do not move this init call. All logic should be carried out after this.
        init();

        mContext = context;
        mActivityManager = mContext.getSystemService(ActivityManager.class);
        View.inflate(mContext, R.layout.car_system_bar_button, /* root= */ this);
        // CarSystemBarButton attrs
        TypedArray typedArray = context.obtainStyledAttributes(attrs,
                R.styleable.CarSystemBarButton);

        setUpIntents(typedArray);
        setUpIcons(typedArray);
        typedArray.recycle();
    }

    /**
     * Initializer for child classes.
     */
    protected void init() {
    }

    /**
     * @param selected true if should indicate if this is a selected state, false otherwise
     */
    public void setSelected(boolean selected) {
        if (mDisabled) {
            // if the button is disabled, mSelected should not be modified and the button
            // should be unselectable
            return;
        }
        super.setSelected(selected);
        mSelected = selected;

        refreshIconAlpha(mIcon);

        if (mShowMoreWhenSelected && mMoreIcon != null) {
            mMoreIcon.setVisibility(selected ? VISIBLE : GONE);
        }
        updateImage(mIcon);
    }

    /** Gets whether the icon is in a selected state. */
    public boolean getSelected() {
        return mSelected;
    }

    /**
     * @param hasUnseen true if should indicate if this is a Unseen state, false otherwise.
     */
    public void setUnseen(boolean hasUnseen) {
        mHasUnseen = hasUnseen;
        updateImage(mIcon);
    }

    /**
     * @param disabled true if icon should be isabled, false otherwise.
     * @param runnable to run when button is clicked while disabled.
     */
    public void setDisabled(boolean disabled, @Nullable Runnable runnable) {
        mDisabled = disabled;
        mOnClickWhileDisabledRunnable = runnable;
        refreshIconAlpha(mIcon);
        updateImage(mIcon);
    }

    /** Gets whether the icon is disabled */
    public boolean getDisabled() {
        return mDisabled;
    }

    /** Runs the Runnable when the button is clicked while disabled */
    public void runOnClickWhileDisabled() {
        if (mOnClickWhileDisabledRunnable == null) {
            return;
        }
        mOnClickWhileDisabledRunnable.run();
    }

    /**
     * Sets the current icon of the default application associated with this button.
     */
    public void setAppIcon(Drawable appIcon) {
        mAppIcon = appIcon;
        updateImage(mIcon);
    }

    /** Gets the icon of the app currently associated to the role of this button. */
    @VisibleForTesting
    protected Drawable getAppIcon() {
        return mAppIcon;
    }

    /** Gets whether the icon is in an unseen state. */
    public boolean getUnseen() {
        return mHasUnseen;
    }

    /**
     * @return The app categories the component represents
     */
    public String[] getCategories() {
        if (mButtonCategories == null) {
            return new String[0];
        }
        return mButtonCategories;
    }

    /**
     * @return The valid packages that should be considered.
     */
    public String[] getPackages() {
        if (mButtonPackages == null) {
            return new String[0];
        }
        return mButtonPackages;
    }

    /**
     * @return The list of component names.
     */
    public String[] getComponentName() {
        if (mComponentNames == null) {
            return new String[0];
        }
        return mComponentNames;
    }

    @Override
    public void onVisibilityChanged(boolean isVisible) {
        setSelected(isVisible);
    }

    /**
     * Subclasses should override this method to return the {@link RoleManager} role associated
     * with this button.
     */
    protected String getRoleName() {
        return null;
    }

    /**
     * @return true if this button should show the icon of the default application for the
     * role returned by {@link #getRoleName()}.
     */
    protected boolean isDefaultAppIconForRoleEnabled() {
        return mIsDefaultAppIconForRoleEnabled;
    }

    /**
     * @return The id of the display the button is on or Display.INVALID_DISPLAY if it's not yet on
     * a display.
     */
    protected int getDisplayId() {
        Display display = getDisplay();
        if (display == null) {
            return Display.INVALID_DISPLAY;
        }
        return display.getDisplayId();
    }

    protected boolean hasSelectionState() {
        return mHighlightWhenSelected || mShowMoreWhenSelected;
    }

    protected float getSelectedAlpha() {
        return mSelectedAlpha;
    }

    @VisibleForTesting
    protected float getUnselectedAlpha() {
        return mUnselectedAlpha;
    }

    @VisibleForTesting
    protected float getDisabledAlpha() {
        return DISABLED_ALPHA;
    }

    @VisibleForTesting
    protected float getIconAlpha() { return mIcon.getAlpha(); }

    /**
     * Sets up intents for click, long touch, and broadcast.
     */
    protected void setUpIntents(TypedArray typedArray) {
        mIntent = typedArray.getString(R.styleable.CarSystemBarButton_intent);
        mLongIntent = typedArray.getString(R.styleable.CarSystemBarButton_longIntent);
        mBroadcastIntent = typedArray.getBoolean(R.styleable.CarSystemBarButton_broadcast, false);

        mClearBackStack = typedArray.getBoolean(R.styleable.CarSystemBarButton_clearBackStack,
                false);

        String categoryString = typedArray.getString(R.styleable.CarSystemBarButton_categories);
        String packageString = typedArray.getString(R.styleable.CarSystemBarButton_packages);
        String componentNameString =
                typedArray.getString(R.styleable.CarSystemBarButton_componentNames);

        try {
            if (mIntent != null) {
                final Intent intent = Intent.parseUri(mIntent, Intent.URI_INTENT_SCHEME);
                setOnClickListener(getButtonClickListener(intent));
                if (packageString != null) {
                    mButtonPackages = packageString.split(BUTTON_FILTER_DELIMITER);
                    intent.putExtra(EXTRA_BUTTON_PACKAGES, mButtonPackages);
                }
                if (categoryString != null) {
                    mButtonCategories = categoryString.split(BUTTON_FILTER_DELIMITER);
                    intent.putExtra(EXTRA_BUTTON_CATEGORIES, mButtonCategories);
                }
                if (componentNameString != null) {
                    mComponentNames = componentNameString.split(BUTTON_FILTER_DELIMITER);
                }
            }
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to attach intent", e);
        }

        try {
            if (mLongIntent != null && !mLongIntent.isEmpty()
                    && (Build.IS_ENG || Build.IS_USERDEBUG)) {
                final Intent intent = Intent.parseUri(mLongIntent, Intent.URI_INTENT_SCHEME);
                setOnLongClickListener(getButtonLongClickListener(intent));
            }
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to attach long press intent", e);
        }
    }

    /** Defines the behavior of a button click. */
    protected OnClickListener getButtonClickListener(Intent toSend) {
        return v -> {
            if (mDisabled) {
                runOnClickWhileDisabled();
                return;
            }
            boolean startState = mSelected;
            Intent intent = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
            intent.putExtra(EXTRA_DIALOG_CLOSE_REASON, DIALOG_CLOSE_REASON_CAR_SYSTEMBAR_BUTTON);
            mContext.sendBroadcastAsUser(intent, getCurrentUserHandle(mContext, mUserTracker));

            boolean intentLaunched = false;
            try {
                if (mBroadcastIntent) {
                    mContext.sendBroadcastAsUser(toSend,
                            getCurrentUserHandle(mContext, mUserTracker));
                    return;
                }
                ActivityOptions options = ActivityOptions.makeBasic();
                options.setLaunchDisplayId(mContext.getDisplayId());
                mContext.startActivityAsUser(toSend, options.toBundle(),
                        getCurrentUserHandle(mContext, mUserTracker));
                intentLaunched = true;
            } catch (Exception e) {
                Log.e(TAG, "Failed to launch intent", e);
            }

            if (intentLaunched && mClearBackStack) {
                try {
                    ActivityTaskManager.RootTaskInfo rootTaskInfo =
                            ActivityTaskManager.getService().getRootTaskInfoOnDisplay(
                                    WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_UNDEFINED,
                                    mContext.getDisplayId());
                    if (rootTaskInfo != null) {
                        mActivityManager.moveTaskToFront(rootTaskInfo.taskId,
                                ActivityManager.MOVE_TASK_WITH_HOME);
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "Failed getting root task info", e);
                }
            }

            if (mToggleSelectedState && (startState == mSelected)) {
                setSelected(!mSelected);
            }
        };
    }

    /** Defines the behavior of a long click. */
    protected OnLongClickListener getButtonLongClickListener(Intent toSend) {
        return v -> {
            Intent intent = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
            intent.putExtra(EXTRA_DIALOG_CLOSE_REASON, DIALOG_CLOSE_REASON_CAR_SYSTEMBAR_BUTTON);
            mContext.sendBroadcastAsUser(intent, getCurrentUserHandle(mContext, mUserTracker));
            try {
                ActivityOptions options = ActivityOptions.makeBasic();
                options.setLaunchDisplayId(mContext.getDisplayId());
                mContext.startActivityAsUser(toSend, options.toBundle(),
                        getCurrentUserHandle(mContext, mUserTracker));
            } catch (Exception e) {
                Log.e(TAG, "Failed to launch intent", e);
            }
            // consume event either way
            return true;
        };
    }

    void setUserTracker(UserTracker userTracker) {
        mUserTracker = userTracker;
    }

    /**
     * Initializes view-related aspects of the button.
     */
    private void setUpIcons(TypedArray typedArray) {
        mSelectedAlpha = typedArray.getFloat(
                R.styleable.CarSystemBarButton_selectedAlpha, DEFAULT_SELECTED_ALPHA);
        mUnselectedAlpha = typedArray.getFloat(
                R.styleable.CarSystemBarButton_unselectedAlpha, DEFAULT_UNSELECTED_ALPHA);
        mHighlightWhenSelected = typedArray.getBoolean(
                R.styleable.CarSystemBarButton_highlightWhenSelected,
                mHighlightWhenSelected);
        mShowMoreWhenSelected = typedArray.getBoolean(
                R.styleable.CarSystemBarButton_showMoreWhenSelected,
                mShowMoreWhenSelected);

        mIconResourceId = typedArray.getResourceId(
                R.styleable.CarSystemBarButton_icon, 0);
        mSelectedIconResourceId = typedArray.getResourceId(
                R.styleable.CarSystemBarButton_selectedIcon, mIconResourceId);
        mIsDefaultAppIconForRoleEnabled = typedArray.getBoolean(
                R.styleable.CarSystemBarButton_useDefaultAppIconForRole, false);
        mToggleSelectedState = typedArray.getBoolean(
                R.styleable.CarSystemBarButton_toggleSelected, false);
        mIcon = findViewById(R.id.car_nav_button_icon_image);
        refreshIconAlpha(mIcon);
        mMoreIcon = findViewById(R.id.car_nav_button_more_icon);
        mUnseenIcon = findViewById(R.id.car_nav_button_unseen_icon);
        updateImage(mIcon);
    }

    protected void updateImage(AlphaOptimizedImageView icon) {
        if (mIsDefaultAppIconForRoleEnabled && mAppIcon != null) {
            icon.setImageDrawable(mAppIcon);
        } else {
            icon.setImageResource(mSelected ? mSelectedIconResourceId : mIconResourceId);
        }
        mUnseenIcon.setVisibility(mHasUnseen ? VISIBLE : GONE);
    }

    protected void refreshIconAlpha(AlphaOptimizedImageView icon) {
        if (mDisabled) {
            icon.setAlpha(DISABLED_ALPHA);
        } else {
            icon.setAlpha(mHighlightWhenSelected && mSelected ? mSelectedAlpha : mUnselectedAlpha);
        }
    }

    @Nullable
    protected UserTracker getUserTracker() {
        return mUserTracker;
    }
}

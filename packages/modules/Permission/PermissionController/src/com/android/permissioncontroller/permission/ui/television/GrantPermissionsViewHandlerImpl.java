package com.android.permissioncontroller.permission.ui.television;

import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.ALLOW_ALWAYS_BUTTON;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.ALLOW_BUTTON;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.ALLOW_FOREGROUND_BUTTON;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.ALLOW_ONE_TIME_BUTTON;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.DENY_AND_DONT_ASK_AGAIN_BUTTON;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.RequiresApi;

import com.android.modules.utils.build.SdkLevel;
import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler;

import java.util.Collections;

/**
 * TV-specific view handler for the grant permissions activity.
 */
public final class GrantPermissionsViewHandlerImpl implements GrantPermissionsViewHandler, OnClickListener {

    private static final String ARG_GROUP_NAME = "ARG_GROUP_NAME";

    private final Context mContext;
    private final Drawable mBackgroundWithBlur;
    private final Drawable mBackgroundNoBlur;
    private final int mBackgroundBlurRadius;

    private ResultListener mResultListener;

    private String mGroupName;

    private ViewGroup mRootView;
    private TextView mMessageView;
    private ImageView mIconView;
    private TextView mCurrentGroupView;
    private Button mAllowButton;
    private Button mAllowAlwaysButton;
    private Button mAllowForegroundOnlyButton;
    private Button mAllowOneTimeButton;
    private Button mSoftDenyButton;
    private Button mHardDenyButton;

    public GrantPermissionsViewHandlerImpl(Context context, String appPackageName) {
        mContext = context;
        mBackgroundWithBlur = context.getResources().getDrawable(
                R.drawable.dialog_background_with_blur);
        mBackgroundNoBlur = context.getResources().getDrawable(R.drawable.dialog_background);
        mBackgroundBlurRadius = context.getResources().getDimensionPixelSize(
                R.dimen.grant_permissions_dialog_background_blur_radius);
    }

    @Override
    public GrantPermissionsViewHandlerImpl setResultListener(ResultListener listener) {
        mResultListener = listener;
        return this;
    }

    @Override
    public View createView() {
        mRootView = (ViewGroup) LayoutInflater.from(mContext)
                .inflate(R.layout.grant_permissions, null);

        mMessageView = (TextView) mRootView.findViewById(R.id.permission_message);
        mIconView = (ImageView) mRootView.findViewById(R.id.permission_icon);
        mCurrentGroupView = (TextView) mRootView.findViewById(R.id.current_page_text);
        mAllowButton = (Button) mRootView.findViewById(R.id.permission_allow_button);
        mAllowAlwaysButton = (Button) mRootView.findViewById(R.id.permission_allow_always_button);
        mAllowForegroundOnlyButton =
                (Button) mRootView.findViewById(R.id.permission_allow_foreground_only_button);
        mAllowOneTimeButton =
                (Button) mRootView.findViewById(R.id.permission_allow_one_time_button);
        mSoftDenyButton = (Button) mRootView.findViewById(R.id.permission_deny_button);
        mHardDenyButton = (Button) mRootView.findViewById(
                R.id.permission_deny_dont_ask_again_button);

        mAllowButton.setOnClickListener(this);
        mAllowAlwaysButton.setOnClickListener(this);
        mAllowForegroundOnlyButton.setOnClickListener(this);
        mAllowOneTimeButton.setOnClickListener(this);
        mSoftDenyButton.setOnClickListener(this);
        mHardDenyButton.setOnClickListener(this);

        if (SdkLevel.isAtLeastT()) {
            mRootView.addOnLayoutChangeListener((view, l, t, r, b, oldL, oldT, oldR, oldB) -> {
                mRootView.setUnrestrictedPreferKeepClearRects(
                        Collections.singletonList(new Rect(0, 0, r - l, b - t)));
            });
        }

        return mRootView;
    }

    @Override
    public void updateWindowAttributes(WindowManager.LayoutParams outLayoutParams) {
        DisplayMetrics metrics = mContext.getResources().getDisplayMetrics();
        int screenWidth = metrics.widthPixels;
        int screenHeight = metrics.heightPixels;
        int marginBottomPx = mContext.getResources().getDimensionPixelSize(
                R.dimen.grant_permissions_dialog_margin_bottom);
        int marginSidePx = mContext.getResources().getDimensionPixelSize(
                R.dimen.grant_permissions_dialog_side_margin);

        outLayoutParams.width = screenWidth - marginSidePx * 2;
        outLayoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        outLayoutParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        outLayoutParams.verticalMargin = (float) marginBottomPx / screenHeight;
        outLayoutParams.format = PixelFormat.TRANSPARENT;
        outLayoutParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG;
        outLayoutParams.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        outLayoutParams.flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;

        if (SdkLevel.isAtLeastS()
                && mContext.getResources().getBoolean(R.bool.config_useWindowBlur)) {
            outLayoutParams.flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
            outLayoutParams.setBlurBehindRadius(mContext.getResources().getDimensionPixelSize(
                    R.dimen.grant_permissions_dialog_blur_behind_radius));
        }
    }

    @Override
    @RequiresApi(Build.VERSION_CODES.S)
    public void onBlurEnabledChanged(Window window, boolean enabled) {
        window.setBackgroundDrawable(enabled ? mBackgroundWithBlur : mBackgroundNoBlur);
        window.setBackgroundBlurRadius(enabled ? mBackgroundBlurRadius : 0);
    }

    @Override
    public void updateUi(String groupName, int groupCount, int groupIndex, Icon icon,
            CharSequence message, CharSequence detailMessage,
            CharSequence permissionRationaleMessage, boolean[] buttonVisibilities,
            boolean[] locationVisibilities) {
        // permissionRationaleMessage ignored by television

        // TODO: Handle detailMessage

        mGroupName = groupName;

        mMessageView.setText(message);

        if (icon != null) {
            mIconView.setImageIcon(icon);
        }

        mAllowButton.setVisibility(
                buttonVisibilities[ALLOW_BUTTON] ? View.VISIBLE : View.GONE);
        mAllowAlwaysButton.setVisibility(
                buttonVisibilities[ALLOW_ALWAYS_BUTTON] ? View.VISIBLE : View.GONE);
        mAllowForegroundOnlyButton.setVisibility(
                buttonVisibilities[ALLOW_FOREGROUND_BUTTON] ? View.VISIBLE : View.GONE);
        mAllowOneTimeButton.setVisibility(
                buttonVisibilities[ALLOW_ONE_TIME_BUTTON] ? View.VISIBLE : View.GONE);
        mHardDenyButton.setVisibility(
                buttonVisibilities[DENY_AND_DONT_ASK_AGAIN_BUTTON] ? View.VISIBLE : View.GONE);
        mRootView.requestFocus();

        if (groupCount > 1) {
            mCurrentGroupView.setVisibility(View.VISIBLE);
            mCurrentGroupView.setText(mContext.getString(R.string.current_permission_template,
                    groupIndex + 1, groupCount));
        } else {
            mCurrentGroupView.setVisibility(View.GONE);
        }
    }

    @Override
    public void saveInstanceState(Bundle outState) {
        outState.putString(ARG_GROUP_NAME, mGroupName);
    }

    @Override
    public void loadInstanceState(Bundle savedInstanceState) {
        mGroupName = savedInstanceState.getString(ARG_GROUP_NAME);
    }

    @Override
    public void onClick(View view) {
        final int id = view.getId();
        if (id == R.id.permission_allow_button) {
            mResultListener.onPermissionGrantResult(mGroupName, GRANTED_ALWAYS);
        } else if (id == R.id.permission_allow_always_button) {
            mResultListener.onPermissionGrantResult(mGroupName, GRANTED_ALWAYS);
        } else if (id == R.id.permission_allow_foreground_only_button) {
            mResultListener.onPermissionGrantResult(mGroupName, GRANTED_FOREGROUND_ONLY);
        } else if (id == R.id.permission_allow_one_time_button) {
            mResultListener.onPermissionGrantResult(mGroupName, GRANTED_ONE_TIME);
        } else if (id == R.id.permission_deny_button) {
            mResultListener.onPermissionGrantResult(mGroupName, DENIED);
        } else if (id == R.id.permission_deny_dont_ask_again_button) {
            mResultListener.onPermissionGrantResult(mGroupName, DENIED_DO_NOT_ASK_AGAIN);
        }
    }

    @Override
    public void onBackPressed() {
        if (mResultListener != null) {
            mResultListener.onPermissionGrantResult(mGroupName, DENIED);
        }
    }
}

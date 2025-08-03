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

package com.android.car.testdpc;

import android.annotation.Nullable;
import android.annotation.StringRes;
import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.android.car.testdpc.remotedpm.DevicePolicyManagerInterface;

public final class DpcActivity extends Activity {

    private static final String TAG = DpcActivity.class.getSimpleName();

    private ComponentName mAdmin;
    private Context mContext;
    private DpcFactory mDpcFactory;
    private DevicePolicyManagerInterface mDoInterface;

    private EditText mUserId;
    private EditText mKey;
    private TextView mCurrentUserTitle;
    private TextView mThisUser;
    private TextView mAddUserRestriction;
    private TextView mGetUserRestrictions;
    private TextView mDisplayUserRestrictions;
    private Button mRebootButton;
    private Button mAddUserRestrictionButton;
    private Button mGetUserRestrictionsButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mContext = getApplicationContext();
        mAdmin = DpcReceiver.getComponentName(mContext);

        Log.d(TAG, "onCreate(): user= " + Process.myUserHandle() + ", admin=" + mAdmin);

        mDpcFactory = new DpcFactory(mContext);
        mDoInterface = mDpcFactory.getDevicePolicyManager(
                UserHandle.getUserHandleForUid(UserHandle.USER_SYSTEM)
        );

        setContentView(R.layout.activity_main);

        mCurrentUserTitle = findViewById(R.id.current_user_title);
        mCurrentUserTitle.setText(R.string.current_user_title);

        mThisUser = findViewById(R.id.this_user);
        mThisUser.setText(Process.myUserHandle().toString());

        mRebootButton = findViewById(R.id.reboot);
        mRebootButton.setOnClickListener(this::uiReboot);

        mAddUserRestriction = findViewById(R.id.add_user_restriction_title);
        mAddUserRestriction.setText(R.string.add_user_restriction);

        mAddUserRestrictionButton = findViewById(R.id.add_user_restriction_button);
        mAddUserRestrictionButton.setOnClickListener(this::uiAddUserRestriction);

        mGetUserRestrictions = findViewById(R.id.get_user_restriction_title);
        mGetUserRestrictions.setText(R.string.get_user_restrictions);

        mDisplayUserRestrictions = findViewById(R.id.display_user_restriction_title);
        mDisplayUserRestrictions.setText(R.string.display_user_restrictions);

        mGetUserRestrictionsButton = findViewById(R.id.get_user_restriction_button);
        mGetUserRestrictionsButton.setOnClickListener(this::uiDisplayUserRestrictions);
    }

    public void uiReboot(View v) {
        showToast(R.string.rebooting);
        mDoInterface.reboot();
    }

    public void uiAddUserRestriction(View v) {
        mUserId = findViewById(R.id.edit_user_id);
        mKey = findViewById(R.id.edit_key);

        String userId = mUserId.getText().toString();
        String restriction = mKey.getText().toString();

        UserHandle target = getUserHandleFromUserId(userId);
        if (target == null) {
            showToast(R.string.user_not_found);
            return;
        }

        DevicePolicyManagerInterface targetDpm = mDpcFactory.getDevicePolicyManager(target);

        if (targetDpm == null) {
            showToast(R.string.no_dpm);
            return;
        }

        try {
            targetDpm.addUserRestriction(restriction);
            showToast("%s: addUserRestriction(%s)", targetDpm.getUser(), restriction);
        } catch (RuntimeException e) {
            showToast(e, "Exception when calling addUserRestriction(%s)", restriction);
            return;
        }
    }

    public void uiDisplayUserRestrictions(View v) {
        DevicePolicyManager dpm = mContext.getSystemService(DevicePolicyManager.class);
        Bundle restrictions;
        try {
            restrictions = dpm.getUserRestrictions(mAdmin);
            showToast("%s: getUserRestrictions()",
                    Process.myUserHandle());
        } catch (RuntimeException e) {
            showToast(e, "Exception when calling getUserRestrictions()");
            return;
        }

        mDisplayUserRestrictions.setText(restrictions.isEmpty() ? "No restrictions." :
                DpcShellCommand.bundleToString(restrictions));
    }

    @Nullable
    public UserHandle getUserHandleFromUserId(String userId) {
        UserHandle targetUser = null;
        try {
            targetUser = UserHandle.of(Integer.parseInt(userId));
        } catch (NumberFormatException e) {
            showToast(e, R.string.target_user_not_found);
        }
        return targetUser;
    }

    public void showToast(@StringRes int text) {
        Toast.makeText(mContext, text, Toast.LENGTH_LONG).show();
    }

    public void showToast(Exception e, @StringRes int text) {
        Toast.makeText(mContext, text, Toast.LENGTH_LONG).show();
    }

    public void showToast(String msgFormat, Object... msgArgs) {
        String text = String.format(msgFormat, msgArgs);
        Toast.makeText(mContext, text, Toast.LENGTH_LONG).show();
    }

    public void showToast(Exception e, String msgFormat, Object... msgArgs) {
        String text = String.format(msgFormat, msgArgs);
        Toast.makeText(mContext, text, Toast.LENGTH_LONG).show();
        Log.e(TAG, text, e);
    }
}

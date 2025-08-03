/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.car.settings.profiles;

import android.content.Intent;
import android.content.pm.UserInfo;
import android.os.Bundle;
import android.os.UserManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.LayoutRes;
import androidx.annotation.StringRes;

import com.android.car.settings.R;
import com.android.car.settings.common.BaseFragment;
import com.android.car.ui.toolbar.MenuItem;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Collections;
import java.util.List;

/**
 * Enables user to edit their profile name.
 */
public class EditProfileNameFragment extends BaseFragment {

    private static final String PROFILE_NAME_SET = "profile_name_set";

    private UserInfo mUserInfo;

    private EditText mProfileNameEditText;
    @VisibleForTesting UserManager mUserManager;
    private MenuItem mSaveButton;

    /**
     * Creates instance of EditProfileNameFragment.
     */
    public static EditProfileNameFragment newInstance(UserInfo userInfo) {
        EditProfileNameFragment
                profileSettingsFragment = new EditProfileNameFragment();
        Bundle bundle = new Bundle();
        bundle.putParcelable(Intent.EXTRA_USER, userInfo);
        profileSettingsFragment.setArguments(bundle);
        return profileSettingsFragment;
    }

    @Override
    public List<MenuItem> getToolbarMenuItems() {
        return Collections.singletonList(mSaveButton);
    }

    @Override
    @LayoutRes
    protected int getLayoutId() {
        return R.layout.edit_profile_name_fragment;
    }

    @Override
    @StringRes
    protected int getTitleId() {
        return R.string.edit_user_name_title;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mUserInfo = getArguments().getParcelable(Intent.EXTRA_USER);

        mSaveButton = new MenuItem.Builder(getContext())
                .setTitle(android.R.string.ok)
                .setOnClickListener(i -> {
                    // Save new user's name.
                    mUserManager.setUserName(mUserInfo.id,
                            mProfileNameEditText.getText().toString());
                    Settings.Secure.putInt(getActivity().getContentResolver(),
                            PROFILE_NAME_SET, 1);
                    getActivity().onBackPressed();
                })
                .build();
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mProfileNameEditText = view.findViewById(R.id.profile_name_text_edit);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (mUserManager == null) {
            mUserManager = UserManager.get(getContext());
        }

        configureProfileNameEditing();
    }

    private void configureProfileNameEditing() {
        // Set the profile's name.
        mProfileNameEditText.setText(mUserInfo.name);
        mProfileNameEditText.setEnabled(true);
        mProfileNameEditText.setSelectAllOnFocus(true);
        mProfileNameEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (TextUtils.isEmpty(s)) {
                    mSaveButton.setEnabled(false);
                    mProfileNameEditText.setError(getString(R.string.name_input_blank_error));
                } else if (!TextUtils.isGraphic(s)) {
                    mSaveButton.setEnabled(false);
                    mProfileNameEditText.setError(getString(R.string.name_input_invalid_error));
                } else {
                    mSaveButton.setEnabled(true);
                    mProfileNameEditText.setError(null);
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }
}

/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.car.drivingstate.CarUxRestrictions;
import android.os.Bundle;

import androidx.annotation.LayoutRes;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.GridLayoutManager;

import com.android.car.settings.R;
import com.android.car.settings.common.BaseFragment;
import com.android.car.settings.common.CarUxRestrictionsHelper;

/**
 * Shows a profile switcher for all profiles available on this device.
 */
public class ProfileSwitcherFragment extends BaseFragment {

    private ProfileGridRecyclerView mProfileGridView;

    @Override
    @LayoutRes
    protected int getLayoutId() {
        return R.layout.profile_switcher;
    }

    @Override
    @StringRes
    protected int getTitleId() {
        return R.string.profiles_list_title;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mProfileGridView = getView().findViewById(R.id.profile_grid);
        GridLayoutManager layoutManager = new GridLayoutManager(getContext(),
                getContext().getResources().getInteger(R.integer.user_switcher_num_col));
        mProfileGridView.setFragment(this);
        mProfileGridView.setLayoutManager(layoutManager);
        mProfileGridView.buildAdapter();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    /**
     * Profile switcher fragment is distraction optimized, so is allowed at all times.
     */
    @Override
    public boolean canBeShown(CarUxRestrictions carUxRestrictions) {
        return true;
    }


    @Override
    public void onUxRestrictionsChanged(CarUxRestrictions restrictionInfo) {
        applyRestriction(CarUxRestrictionsHelper.isNoSetup(restrictionInfo));
    }

    private void applyRestriction(boolean restricted) {
        if (mProfileGridView != null) {
            if (restricted) {
                mProfileGridView.disableAddProfile();
            } else {
                mProfileGridView.enableAddProfile();
            }
        }
    }
}

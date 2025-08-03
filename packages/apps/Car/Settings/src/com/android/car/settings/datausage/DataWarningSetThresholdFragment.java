/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.car.settings.datausage;

import android.net.NetworkPolicyManager;
import android.net.NetworkTemplate;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.android.car.settings.R;

/** Screen used to pick the data usage warning threshold bytes. */
public class DataWarningSetThresholdFragment extends DataUsageSetThresholdBaseFragment {

    /**
     * Creates a new instance of {@link DataWarningSetThresholdFragment} with the given template. If
     * the template is {@code null}, the fragment will use the default data network template.
     */
    public static DataWarningSetThresholdFragment newInstance(@Nullable NetworkTemplate template) {
        DataWarningSetThresholdFragment fragment = new DataWarningSetThresholdFragment();
        Bundle args = new Bundle();
        args.putParcelable(NetworkPolicyManager.EXTRA_NETWORK_TEMPLATE, template);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    void onSave(long threshold) {
        mPolicyEditor.setPolicyWarningBytes(mNetworkTemplate, threshold);
    }

    @Override
    @StringRes
    protected int getTitleResId() {
        return R.string.data_usage_warning_editor_title;
    }

    @Override
    protected long getInitialBytes() {
        return mPolicyEditor.getPolicyWarningBytes(mNetworkTemplate);
    }
}

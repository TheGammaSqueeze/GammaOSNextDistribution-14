/*
 * Copyright 2019 The Android Open Source Project
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

package com.android.car.settings.system;

import static android.app.Activity.RESULT_OK;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.XmlRes;

import com.android.car.settings.R;
import com.android.car.settings.common.ActivityResultCallback;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.SettingsFragment;
import com.android.car.settings.security.CheckLockActivity;
import com.android.car.ui.recyclerview.CarUiRecyclerView;
import com.android.car.ui.toolbar.MenuItem;

import java.util.Collections;
import java.util.List;

/**
 * Presents the user with the option to reset the head unit to its default "factory" state. If a
 * user confirms, the user is first required to authenticate and then presented with a secondary
 * confirmation: {@link FactoryResetConfirmFragment}. The user must scroll to the bottom of the page
 * before proceeding.
 */
public class FactoryResetFragment extends SettingsFragment implements ActivityResultCallback {

    private static final Logger LOG = new Logger(FactoryResetFragment.class);
    // Arbitrary request code for starting CheckLockActivity when the reset button is clicked.
    @VisibleForTesting
    static final int CHECK_LOCK_REQUEST_CODE = 88;

    private MenuItem mFactoryResetButton;

    @Override
    public List<MenuItem> getToolbarMenuItems() {
        return Collections.singletonList(mFactoryResetButton);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mFactoryResetButton = new MenuItem.Builder(getContext())
                .setTitle(R.string.factory_reset_button_text)
                .setEnabled(false)
                .setOnClickListener(i ->
                        startActivityForResult(new Intent(getContext(), CheckLockActivity.class),
                                CHECK_LOCK_REQUEST_CODE, /* callback= */ FactoryResetFragment.this))
                .build();
    }

    @Override
    @XmlRes
    protected int getPreferenceScreenResId() {
        return R.xml.factory_reset_fragment;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        CarUiRecyclerView recyclerView = view.findViewById(R.id.settings_recycler_view);
        if (recyclerView != null) {
            recyclerView.getViewTreeObserver().addOnGlobalLayoutListener(
                    new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            mFactoryResetButton.setEnabled(isAtEnd(recyclerView));
                            recyclerView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        }
                    });
            recyclerView.addOnScrollListener(
                    new CarUiRecyclerView.OnScrollListener() {
                        @Override
                        public void onScrolled(CarUiRecyclerView recyclerView, int dx, int dy) {
                            if (isAtEnd(recyclerView)) {
                                mFactoryResetButton.setEnabled(true);
                            }
                        }

                        @Override
                        public void onScrollStateChanged(CarUiRecyclerView recyclerView,
                                int newState) {
                            // no-op
                        }
                    });
        } else {
            LOG.e("No RecyclerView found");
            requireActivity().onBackPressed();
        }
    }

    @Override
    public void processActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == CHECK_LOCK_REQUEST_CODE && resultCode == RESULT_OK) {
            launchFragment(new FactoryResetConfirmFragment());
        }
    }

    /** Returns {@code true} if the RecyclerView is completely displaying the last item. */
    private boolean isAtEnd(CarUiRecyclerView recyclerView) {
        if (recyclerView.getAdapter().getItemCount() == 0) {
            return true;
        }

        return recyclerView.findLastCompletelyVisibleItemPosition()
                == recyclerView.getAdapter().getItemCount() - 1;
    }
}

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

package com.android.tv.settings.device.apps;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.leanback.app.GuidedStepSupportFragment;
import androidx.leanback.widget.GuidanceStylist;
import androidx.leanback.widget.GuidedAction;

import com.android.tv.settings.util.GuidedActionsAlignUtil;
import com.android.tv.settings.util.SafeIntents;

import java.util.List;

/** Confirmation activity to handle apps related actions. */
public class ConfirmationActivity extends FragmentActivity {
    private static final String ARG_PACKAGE_NAME = "packageName";
    private static final String EXTRA_GUIDANCE_TITLE = "guidancetitle";
    private static final String EXTRA_GUIDANCE_SUBTITLE = "guidanceSubtitle";
    private static final String EXTRA_GUIDANCE_BREADCRUMB = "guidacneBreadcrumb";
    private static final String EXTRA_GUIDANCE_ICON = "guidanceIcon";


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            GuidedStepSupportFragment
                    .addAsRoot(this, ConfirmationFragment.newInstance(
                            getIntent()), android.R.id.content);
        }
    }

    public static class ConfirmationFragment extends GuidedStepSupportFragment {
        private static final int ID_OK = 0;
        private static final int ID_CANCEL = 1;
        private final Intent mIntent;

        private ConfirmationFragment(Intent intent) {
            mIntent = intent;
            setArguments(intent.getExtras());
        }

        /** @return an new activity instance. */
        public static ConfirmationFragment newInstance(Intent intent) {
            ConfirmationFragment f = new ConfirmationFragment(intent);
            return f;
        }

        @Override
        public void onCreateActions(@NonNull List<GuidedAction> actions,
                Bundle savedInstanceState) {
            actions.add(new GuidedAction.Builder()
                    .title(getString(android.R.string.ok))
                    .id(ID_OK)
                    .build());
            actions.add(new GuidedAction.Builder()
                    .title(getString(android.R.string.cancel))
                    .id(ID_CANCEL)
                    .build());
        }

        @Override
        public GuidanceStylist onCreateGuidanceStylist() {
            return GuidedActionsAlignUtil.createGuidanceStylist();
        }

        @NonNull
        @Override
        public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
            return new GuidanceStylist.Guidance(
                    getArguments().getString(EXTRA_GUIDANCE_TITLE),
                    getArguments().getString(EXTRA_GUIDANCE_SUBTITLE),
                    getArguments().getString(EXTRA_GUIDANCE_BREADCRUMB),
                    null);
        }

        @Override
        public void onGuidedActionClicked(GuidedAction action) {
            Intent sanitizedIntent = SafeIntents.forCallback(mIntent);
            switch ((int) action.getId()) {
                case ID_OK:
                    getActivity().setResult(RESULT_OK, sanitizedIntent);
                    break;
                case ID_CANCEL:
                    getActivity().setResult(RESULT_CANCELED, sanitizedIntent);
                    break;
            }
            getActivity().finish();
        }
    }
}

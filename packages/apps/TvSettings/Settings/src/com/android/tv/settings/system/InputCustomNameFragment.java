/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tv.settings.system;

import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.leanback.app.GuidedStepSupportFragment;
import androidx.leanback.widget.GuidanceStylist;
import androidx.leanback.widget.GuidedAction;
import androidx.leanback.widget.GuidedActionsStylist;

import com.android.tv.settings.R;
import com.android.tv.settings.util.AccessibilityHelper;

import java.util.List;

@Keep
public class InputCustomNameFragment extends GuidedStepSupportFragment {

    private static final String ARG_CURRENT_NAME = "current_name";
    private static final String ARG_DEFAULT_NAME = "default_name";

    private CharSequence mName;
    private GuidedAction mEditAction;

    public static void prepareArgs(@NonNull Bundle args, CharSequence defaultName,
            CharSequence currentName) {
        args.putCharSequence(ARG_DEFAULT_NAME, defaultName);
        args.putCharSequence(ARG_CURRENT_NAME, currentName);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mName = getArguments().getCharSequence(ARG_CURRENT_NAME);

        super.onCreate(savedInstanceState);
    }

    @NonNull
    @Override
    public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
        return new GuidanceStylist.Guidance(
                getString(R.string.inputs_custom_name),
                getString(R.string.inputs_custom_name_description_fmt,
                        getArguments().getCharSequence(ARG_DEFAULT_NAME)),
                null,
                getContext().getDrawable(R.drawable.ic_input_132dp)
        );
    }

    @Override
    public GuidedActionsStylist onCreateActionsStylist() {
        return new GuidedActionsStylist() {
            @Override
            public int onProvideItemLayoutId() {
                return R.layout.guided_step_input_action;
            }
            @Override
            protected void setupImeOptions(GuidedActionsStylist.ViewHolder vh,
                    GuidedAction action) {
                // keep defaults
            }
        };
    }

    @Override
    public void onCreateActions(@NonNull List<GuidedAction> actions,
            Bundle savedInstanceState) {
        mEditAction = new GuidedAction.Builder(getContext())
                .title(mName)
                .editable(true)
                .build();
        actions.add(mEditAction);
    }

    @Override
    public void onResume() {
        super.onResume();
        openInEditMode(mEditAction);
    }

    @Override
    public long onGuidedActionEditedAndProceed(GuidedAction action) {
        mName = action.getTitle();
        ((Callback) getTargetFragment()).onSetCustomName(mName);
        getFragmentManager().popBackStack();
        return GuidedAction.ACTION_ID_OK;
    }

    @Override
    public void onGuidedActionEditCanceled(GuidedAction action) {
        // We need to ensure the IME is closed before navigating back. See b/233207859.
        AccessibilityHelper.dismissKeyboard(getActivity(), getView());
        getFragmentManager().popBackStack();
    }

    public interface Callback {
        void onSetCustomName(CharSequence name);
    }
}

/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tv.settings.accessories;

import android.annotation.Nullable;
import android.content.Context;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.DynamicDrawableSpan;
import android.util.TypedValue;
import android.view.View;

import com.android.tv.settings.R;
import com.android.tv.settings.dialog.ProgressDialogFragment;
import com.android.tv.settings.widget.CenterAlignedDynamicDrawableSpan;

/**
 * Custom Content Fragment for the Bluetooth settings activity.
 */
public class AddAccessoryContentFragment extends ProgressDialogFragment {

    private static final String BUTTON1_TOKEN = "button1_token";
    private static final String BUTTON2_TOKEN = "button2_token";

    public static AddAccessoryContentFragment newInstance() {
        return new AddAccessoryContentFragment();
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setTitle(R.string.accessories_add_title);
        setIcon(R.drawable.ic_bluetooth_searching_128dp);
        setSummary(R.string.accessories_add_bluetooth_inst);
        updateExtraDescription();
        if (getResources() != null) {
            TypedValue lineSpacingMultiplier = new TypedValue();
            getResources().getValue(
                    R.dimen.description_with_inline_icon_line_spacing_multiplier,
                    lineSpacingMultiplier,
                    true);
            setDescriptionLineSpacingMultiplier(lineSpacingMultiplier.getFloat());
        }
    }

    private void updateExtraDescription() {
        String extraDescription = getString(
                R.string.accessories_add_bluetooth_inst_extra, BUTTON1_TOKEN, BUTTON2_TOKEN);
        if (TextUtils.isEmpty(extraDescription) || getActivity() == null) {
            return;
        }

        SpannableStringBuilder builder = new SpannableStringBuilder(extraDescription);
        int indexOfBackButtonImage = extraDescription.indexOf(BUTTON1_TOKEN);
        DynamicDrawableSpan imageSpan =
                new CenterAlignedDynamicDrawableSpan(
                        getActivity(), R.drawable.pairing_button1_icon);
        if (indexOfBackButtonImage != -1) {
            builder.setSpan(
                    imageSpan,
                    indexOfBackButtonImage,
                    indexOfBackButtonImage + BUTTON1_TOKEN.length(),
                    SpannableString.SPAN_INCLUSIVE_INCLUSIVE);
        }

        int indexOfHomeButtonImage = extraDescription.indexOf(BUTTON2_TOKEN);
        DynamicDrawableSpan imageSpan2 =
                new CenterAlignedDynamicDrawableSpan(
                        getActivity(), R.drawable.pairing_button2_icon);
        if (indexOfHomeButtonImage != -1) {
            builder.setSpan(
                    imageSpan2,
                    indexOfHomeButtonImage,
                    indexOfHomeButtonImage + BUTTON2_TOKEN.length(),
                    SpannableString.SPAN_INCLUSIVE_INCLUSIVE);
        }

        setExtraText(builder);
        if (getExtraInstructionContentDescription(getActivity()) != null) {
            setExtraContentDescription(getExtraInstructionContentDescription(getActivity()));
        }
    }

    static String getExtraInstructionContentDescription(Context context) {
        if (context == null) {
            return null;
        }
        String mainDescription =
                context.getResources().getString(R.string.accessories_add_bluetooth_inst_extra);
        String button1 = context.getResources().getString(R.string.accessories_pairing_button1);
        String button2 = context.getResources().getString(R.string.accessories_pairing_button2);
        if (TextUtils.isEmpty(mainDescription) || TextUtils.isEmpty(button1)
                || TextUtils.isEmpty(button2)) {
            return null;
        }
        return context.getResources()
                .getString(R.string.accessories_add_bluetooth_inst_extra, button1, button2);
    }
}

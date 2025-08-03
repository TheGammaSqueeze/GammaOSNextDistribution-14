/*
 * Copyright (C) 2015 The Android Open Source Project
 * Copyright (C) 2018 The LineageOS Project
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

package com.android.tv.settings.about;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.View;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.leanback.app.GuidedStepSupportFragment;
import androidx.leanback.widget.GuidanceStylist;
import androidx.leanback.widget.GuidedAction;

import com.android.tv.settings.R;

import java.util.List;

@Keep
public class ShutdownConfirmFragment extends GuidedStepSupportFragment {

    public static ShutdownConfirmFragment newInstance() {
        return new ShutdownConfirmFragment();
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setSelectedActionPosition(0);
    }

    @Override
    public @NonNull
    GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
        return new GuidanceStylist.Guidance(
                getString(R.string.system_shutdown_confirm),
                null,
                null,
                getActivity().getDrawable(R.drawable.ic_warning_132dp)
        );
    }

    @Override
    public void onCreateActions(@NonNull List<GuidedAction> actions,
            Bundle savedInstanceState) {
        final Context context = getActivity();
        actions.add(new GuidedAction.Builder(context)
                .icon(R.drawable.ic_cancel)
                .clickAction(GuidedAction.ACTION_ID_CANCEL)
                .build());
        actions.add(new GuidedAction.Builder(context)
                .icon(R.drawable.ic_shutdown)
                .id(GuidedAction.ACTION_ID_OK)
                .title(R.string.shutdown_button_label)
                .build());
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        if (action.getId() == GuidedAction.ACTION_ID_OK) {
            final PowerManager pm = getActivity().getSystemService(PowerManager.class);

            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    pm.shutdown(false, "", false);
                    return null;
                }
            }.execute();
        } else {
            getFragmentManager().popBackStack();
        }
    }
}

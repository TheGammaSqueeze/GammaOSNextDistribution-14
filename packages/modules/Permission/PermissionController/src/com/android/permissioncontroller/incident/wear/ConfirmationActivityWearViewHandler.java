/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.permissioncontroller.incident.wear;

import android.app.Activity;
import android.net.Uri;
import android.os.IncidentManager;
import android.view.View;

import com.android.permissioncontroller.incident.PendingList;

import kotlin.Unit;

/**
 * Wear-specific view handler for the confirmation activity.
 */
public class ConfirmationActivityWearViewHandler {

    private final Activity mActivity;
    private final IncidentManager mIncidentManager;
    private WearConfirmationActivityViewModel mViewModel;

    public ConfirmationActivityWearViewHandler(Activity activity, IncidentManager incidentManager) {
        mActivity = activity;
        mIncidentManager = incidentManager;
    }

    /**
     * Creates and returns the view hierarchy that is managed by this view
     * handler. This must be called before {@link #updateViewModel}.
     */
    public View createView() {
        WearConfirmationActivityViewModelFactory factory =
                new WearConfirmationActivityViewModelFactory();
        mViewModel = factory.create(
                WearConfirmationActivityViewModel.class);

        return WearConfirmationScreenKt.createView(mActivity, mViewModel);
    }

    /**
     * Updates the wear confirmation activity view model to reflect the specified state.
     */
    public void updateViewModel(boolean isDenyView, String dialogTitle, String message, Uri uri) {
        mViewModel.getShowDialogLiveData().setValue(true);
        mViewModel.getShowDenyReportLiveData().setValue(isDenyView);
        WearConfirmationActivityViewModel.ContentArgs args =
                new WearConfirmationActivityViewModel.ContentArgs(
                        dialogTitle,
                        message,
                        () -> {
                            mIncidentManager.denyReport(uri);
                            mActivity.finish();
                            return Unit.INSTANCE;
                        },
                        () -> {
                            mIncidentManager.approveReport(uri);
                            PendingList.getInstance().updateState(mActivity, 0);
                            mActivity.finish();
                            return Unit.INSTANCE;
                        },
                        () -> {
                            mIncidentManager.denyReport(uri);
                            PendingList.getInstance().updateState(mActivity, 0);
                            mActivity.finish();
                            return Unit.INSTANCE;
                        }
                );
        mViewModel.getContentArgsLiveData().setValue(args);
    }
}

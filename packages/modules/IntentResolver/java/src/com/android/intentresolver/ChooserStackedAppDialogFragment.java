/*
 * Copyright (C) 2019 The Android Open Source Project
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


package com.android.intentresolver;

import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;

import com.android.intentresolver.chooser.DisplayResolveInfo;
import com.android.intentresolver.chooser.MultiDisplayResolveInfo;

/**
 * Shows individual actions for a "stacked" app target - such as an app with multiple posting
 * streams represented in the Sharesheet.
 */
public class ChooserStackedAppDialogFragment extends ChooserTargetActionsDialogFragment {

    /**
     * Display a fragment for the user to select one of the members of a target "stack."
     * @param stackedTarget The display info for the full stack to select within.
     * @param stackedTargetParentWhich The "which" value that the {@link ChooserActivity} uses to
     * identify the {@code stackedTarget} as presented in the chooser menu UI. If the user selects
     * a target in this fragment, the selection will be saved in the {@link MultiDisplayResolveInfo}
     * and then the {@link ChooserActivity} will receive a {@code #startSelected()} callback using
     * this "which" value to identify the stack that's now unambiguously resolved.
     * @param userHandle
     *
     * TODO: consider taking a client-provided callback instead of {@code stackedTargetParentWhich}
     * to avoid coupling with {@link ChooserActivity}'s mechanism for handling the selection.
     */
    public static void show(
            FragmentManager fragmentManager,
            MultiDisplayResolveInfo stackedTarget,
            int stackedTargetParentWhich,
            UserHandle userHandle) {
        ChooserStackedAppDialogFragment fragment = new ChooserStackedAppDialogFragment(
                stackedTarget, stackedTargetParentWhich, userHandle);
        fragment.show(fragmentManager, TARGET_DETAILS_FRAGMENT_TAG);
    }

    private final MultiDisplayResolveInfo mMultiDisplayResolveInfo;
    private final int mParentWhich;

    @Override
    public void onClick(DialogInterface dialog, int which) {
        mMultiDisplayResolveInfo.setSelected(which);
        ((StartsSelectedItem) getActivity()).startSelected(mParentWhich, false, true);
        dismiss();
    }

    @NonNull
    @Override
    protected CharSequence getItemLabel(DisplayResolveInfo dri) {
        final PackageManager pm = getContext().getPackageManager();
        return dri.getResolveInfo().loadLabel(pm);
    }

    @Override
    protected Drawable getItemIcon(DisplayResolveInfo dri) {
        // Show no icon for the group disambig dialog, null hides the imageview
        return null;
    }

    private ChooserStackedAppDialogFragment(
            MultiDisplayResolveInfo stackedTarget,
            int stackedTargetParentWhich,
            UserHandle userHandle) {
        super(stackedTarget.getAllDisplayTargets(), userHandle);
        mMultiDisplayResolveInfo = stackedTarget;
        mParentWhich = stackedTargetParentWhich;
    }
}

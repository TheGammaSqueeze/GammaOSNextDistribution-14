/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.managedprovisioning.preprovisioning.terms;

import static android.view.View.TEXT_ALIGNMENT_TEXT_START;

import static com.google.android.setupdesign.util.ThemeHelper.shouldApplyMaterialYouStyle;

import android.app.Activity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.AccessibilityContextMenuMaker;
import com.android.managedprovisioning.common.StylerHelper;
import com.android.managedprovisioning.common.TransitionHelper;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.preprovisioning.terms.adapters.TermsListAdapter;

import com.google.auto.value.AutoValue;

import java.util.List;

/**
 * A {@link TermsActivityBridge} implementation meant for the base {@code ManagedProvisioning}.
 */
@AutoValue
abstract class TermsActivityBridgeImpl implements TermsActivityBridge {

    abstract Utils getUtils();

    abstract StylerHelper getStylerHelper();

    abstract TransitionHelper getTransitionHelper();

    @Override
    public void initiateUi(final Activity activity, final List<TermsDocument> terms,
            final TermsDocument generalTerms) {
        activity.setContentView(R.layout.terms_screen);
        activity.setTitle(R.string.terms);

        setupHeader(activity);
        setupRecyclerView(activity);
        setupToolbar(activity);
        setupTermsListForHandhelds(activity, terms, generalTerms);
    }

    private void setupHeader(Activity activity) {
        if (!shouldApplyMaterialYouStyle(activity)) {
            return;
        }
        TextView header = activity.findViewById(R.id.header);
        header.setVisibility(View.VISIBLE);
        header.setText(R.string.terms);
        getStylerHelper().applyHeaderStyling(header,
                new LinearLayout.LayoutParams(header.getLayoutParams()));
        header.setTextAlignment(TEXT_ALIGNMENT_TEXT_START);
    }

    private void setupRecyclerView(Activity activity) {
        final RecyclerView recyclerView = activity.findViewById(R.id.terms_container);
        if (recyclerView.getItemDecorationCount() > 0) {
            recyclerView.removeItemDecorationAt(/* index= */ 0);
        }
    }

    private void setupToolbar(Activity activity) {
        Toolbar toolbar = activity.findViewById(R.id.toolbar);
        toolbar.setNavigationContentDescription(R.string.navigation_button_description);
        toolbar.setNavigationIcon(activity.getDrawable(R.drawable.ic_arrow_back_24dp));
        toolbar.setNavigationOnClickListener(v ->
                getTransitionHelper().finishActivity(activity));
        if (!shouldApplyMaterialYouStyle(activity)) {
            toolbar.setTitle(R.string.terms);
        }
    }

    private void setupTermsListForHandhelds(Activity activity, List<TermsDocument> terms,
            TermsDocument generalTerms) {
        RecyclerView recyclerView = activity.findViewById(R.id.terms_container);
        recyclerView.setLayoutManager(new LinearLayoutManager(/* context= */ activity));
        recyclerView.setAdapter(new TermsListAdapter(
                activity,
                generalTerms,
                terms,
                activity.getLayoutInflater(),
                new AccessibilityContextMenuMaker(activity),
                (TermsListAdapter.TermsBridge) activity,
                getUtils(),
                getStylerHelper()));
    }

    static Builder builder() {
        return new AutoValue_TermsActivityBridgeImpl.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {
        abstract Builder setUtils(Utils utils);

        abstract Builder setStylerHelper(StylerHelper stylerHelper);

        abstract Builder setTransitionHelper(TransitionHelper transitionHelper);

        abstract TermsActivityBridgeImpl build();
    }

}

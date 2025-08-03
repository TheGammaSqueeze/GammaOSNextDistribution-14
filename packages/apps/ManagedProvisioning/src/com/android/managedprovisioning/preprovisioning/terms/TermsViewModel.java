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

package com.android.managedprovisioning.preprovisioning.terms;

import static android.content.pm.PackageManager.GET_META_DATA;
import static android.content.pm.PackageManager.MATCH_SYSTEM_ONLY;

import static java.util.Objects.requireNonNull;

import android.app.Application;
import android.util.ArraySet;

import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.android.managedprovisioning.common.StoreUtils;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A {@link ViewModel} which maintains data related to terms.
 */
final class TermsViewModel extends ViewModel {
    private final TermsProvider mTermsProvider;
    private List<TermsDocument> mTerms;
    private final Set<Integer> mExpandedGroupsPosition = new ArraySet<>();
    private final Map<Integer, Boolean> mTermsListExpandedStates = new HashMap<>();

    TermsViewModel(TermsProvider termsProvider) {
        mTermsProvider = requireNonNull(termsProvider);
    }

    List<TermsDocument> getTerms() {
        if (mTerms == null) {
            mTerms = mTermsProvider.getTerms();
        }
        return mTerms;
    }

    TermsDocument getGeneralDisclaimer() {
        return mTermsProvider.getGeneralDisclaimer();
    }

    void setTermExpanded(int position, boolean expanded) {
        validateIndex(position);
        mTermsListExpandedStates.put(position, expanded);
        if (expanded) {
            markTermRead(position);
        }
    }

    boolean isTermExpanded(int position) {
        validateIndex(position);
        return mTermsListExpandedStates.getOrDefault(position, /* defaultValue= */ false);
    }

    int getNumberOfReadTerms() {
        return mExpandedGroupsPosition.size();
    }

    private void markTermRead(int position) {
        mExpandedGroupsPosition.add(position);
    }

    private void validateIndex(int position) {
        final int termsCount = getTerms().size();
        if (position < 0 || position >= termsCount) {
            throw new IllegalArgumentException("Index " + position + " does not belong to the "
                    + "terms array with count " + termsCount);
        }
    }

    static class TermsViewModelFactory implements ViewModelProvider.Factory {
        private final Application mApplication;
        private final ProvisioningParams mParams;

        TermsViewModelFactory(Application application, ProvisioningParams params) {
            mApplication = requireNonNull(application);
            mParams = requireNonNull(params);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(Class<T> modelClass) {
            if (!TermsViewModel.class.isAssignableFrom(modelClass)) {
                throw new IllegalArgumentException("Invalid class for creating a "
                        + "TermsViewModel: " + modelClass);
            }
            return (T)
                    new TermsViewModel(
                            new TermsProvider(
                                    mApplication.getApplicationContext(),
                                    StoreUtils::readString,
                                    mParams,
                                    new Utils(),
                                    () ->
                                            mApplication
                                                    .getApplicationContext()
                                                    .getPackageManager()
                                                    .getInstalledApplications(
                                                            MATCH_SYSTEM_ONLY | GET_META_DATA)));
        }
    }
}

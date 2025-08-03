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

package android.adservices.adselection;

import android.adservices.adselection.PersistAdSelectionResultResponse;
import android.adservices.common.FledgeErrorResponse;

/**
 * Defines a callback for the persistAdSelectionResults API, which contains both an onSuccess
 * and an onFailure function. The success function accepts a {@link
 * PersistAdSelectionResultResponse}, while the failure function accepts an {@link
 * FledgeErrorResponse}
 *
 * {@hide}
 */
oneway interface PersistAdSelectionResultCallback {
    void onSuccess(in PersistAdSelectionResultResponse outcomeParcel);
    void onFailure(in FledgeErrorResponse responseParcel);
}

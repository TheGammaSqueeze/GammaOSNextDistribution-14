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

package com.android.adservices.service.ui.ux.collection;

import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.service.ui.enrollment.collection.BetaUxEnrollmentChannelCollection;
import com.android.adservices.service.ui.enrollment.collection.GaUxEnrollmentChannelCollection;
import com.android.adservices.service.ui.enrollment.collection.PrivacySandboxEnrollmentChannelCollection;
import com.android.adservices.service.ui.enrollment.collection.RvcUxEnrollmentChannelCollection;
import com.android.adservices.service.ui.enrollment.collection.U18UxEnrollmentChannelCollection;
import com.android.adservices.service.ui.ux.base.PrivacySandboxUx;
import com.android.adservices.service.ui.ux.impl.BetaUx;
import com.android.adservices.service.ui.ux.impl.GaUx;
import com.android.adservices.service.ui.ux.impl.RvcUx;
import com.android.adservices.service.ui.ux.impl.U18Ux;
import com.android.adservices.service.ui.ux.impl.UnsupportedUx;

/** Collection of privacy sandbox UXs, ordered by their priority. */
@RequiresApi(Build.VERSION_CODES.S)
public enum PrivacySandboxUxCollection {
    UNSUPPORTED_UX(
            /* priority= */ 0,
            new UnsupportedUx(),
            new PrivacySandboxEnrollmentChannelCollection[0]),

    U18_UX(/* priority= */ 1, new U18Ux(), U18UxEnrollmentChannelCollection.values()),

    RVC_UX(/* priority= */ 2, new RvcUx(), RvcUxEnrollmentChannelCollection.values()),

    GA_UX(/* priority= */ 3, new GaUx(), GaUxEnrollmentChannelCollection.values()),

    BETA_UX(/* priority= */ 4, new BetaUx(), BetaUxEnrollmentChannelCollection.values());

    private final int mPriority;
    private final PrivacySandboxUx mUx;
    private final PrivacySandboxEnrollmentChannelCollection[] mEcc;

    PrivacySandboxUxCollection(
            int priority, PrivacySandboxUx ux, PrivacySandboxEnrollmentChannelCollection[] ecc) {
        mPriority = priority;
        mUx = ux;
        mEcc = ecc;
    }

    public int getPriority() {
        return mPriority;
    }

    public PrivacySandboxUx getUx() {
        return mUx;
    }

    public PrivacySandboxEnrollmentChannelCollection[] getEnrollmentChannelCollection() {
        return mEcc;
    }
}

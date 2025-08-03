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

package com.google.android.mobly.snippet.bundled;

import android.platform.helpers.HelperAccessor;
import android.platform.helpers.IAutoDialContactDetailsHelper;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.rpc.Rpc;

/** Snippet class for exposing Contact Details App APIs. */
public class ContactDetailsSnippet implements Snippet {

    private final HelperAccessor<IAutoDialContactDetailsHelper> mContactsDetailsHelper;

    public ContactDetailsSnippet() {

        mContactsDetailsHelper = new HelperAccessor<>(IAutoDialContactDetailsHelper.class);
    }

    @Rpc(description = "Add and remove contact ( contact details are open ) from favorites.")
    public void addRemoveFavoriteContact() {
        mContactsDetailsHelper.get().addRemoveFavoriteContact();
    }

    @Rpc(description = "Make call to number with type Work from contact details page.")
    public void makeCallFromDetailsPageByTypeWork() {
        mContactsDetailsHelper
                .get()
                .makeCallFromDetailsPageByType(IAutoDialContactDetailsHelper.ContactType.WORK);
    }

    @Rpc(description = "Make call to number with type Home from contact details page.")
    public void makeCallFromDetailsPageByTypeHome() {
        mContactsDetailsHelper
                .get()
                .makeCallFromDetailsPageByType(IAutoDialContactDetailsHelper.ContactType.HOME);
    }

    @Rpc(description = "Make call to number with type Mobile from contact details page.")
    public void makeCallFromDetailsPageByTypeMobile() {
        mContactsDetailsHelper
                .get()
                .makeCallFromDetailsPageByType(IAutoDialContactDetailsHelper.ContactType.MOBILE);
    }

    @Rpc(description = "Close contact details page.")
    public void closeDetailsPage() {
        mContactsDetailsHelper.get().closeDetailsPage();
    }
}

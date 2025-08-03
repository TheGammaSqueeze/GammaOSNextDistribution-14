/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.phone.settings.fdn;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;

import com.android.phone.R;

/**
 * Activity to let the user delete an FDN contact.
 */
public class DeleteFdnContactScreen extends BaseFdnContactScreen {

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        getWindow().addSystemFlags(
                android.view.WindowManager.LayoutParams
                        .SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS);

        // Starts PIN2 authentication only for the first time.
        if (icicle == null) authenticatePin2();
        setContentView(R.layout.delete_fdn_contact_screen);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (DBG) log("onActivityResult");

        switch (requestCode) {
            case PIN2_REQUEST_CODE:
                Bundle extras = (intent != null) ? intent.getExtras() : null;
                if (extras != null) {
                    mPin2 = extras.getString("pin2");
                    processPin2(mPin2);
                } else {
                    // if they cancelled, then we just cancel too.
                    if (DBG) log("onActivityResult: CANCELLED");
                    displayProgress(false);
                    finish();
                }
                break;
        }
    }

    @Override
    protected void resolveIntent() {
        super.resolveIntent();

        if (TextUtils.isEmpty(mNumber)) {
            finish();
        }
    }

    private void deleteContact() {
        StringBuilder buf = new StringBuilder();
        if (TextUtils.isEmpty(mName)) {
            buf.append("number='");
        } else {
            buf.append("tag='");
            buf.append(mName);
            buf.append("' AND number='");
        }
        buf.append(mNumber);
        buf.append("' AND pin2='");
        buf.append(mPin2);
        buf.append("'");

        Uri uri = FdnList.getContentUri(mSubscriptionInfoHelper);

        mQueryHandler = new QueryHandler(getContentResolver());
        mQueryHandler.startDelete(0, null, uri, buf.toString(), null);
        displayProgress(true);
    }

    @Override
    protected void handleResult(boolean success) {
        if (success) {
            if (DBG) log("handleResult: success!");
            showStatus(getResources().getText(R.string.fdn_contact_deleted));
        } else {
            if (DBG) log("handleResult: failed!");
            showStatus(getResources().getText(R.string.pin2_invalid));
        }
        mHandler.postDelayed(() -> finish(), 2000);
    }

    @Override
    protected void pin2AuthenticationSucceed() {
        showStatus(getResources().getText(R.string.deleting_fdn_contact));
        deleteContact();
    }
}

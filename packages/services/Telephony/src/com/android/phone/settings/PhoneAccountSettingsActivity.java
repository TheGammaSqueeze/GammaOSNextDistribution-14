/**
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
 * limitations under the License
 */

package com.android.phone.settings;

import android.app.ActionBar;
import android.os.Bundle;
import android.os.UserManager;
import android.preference.PreferenceActivity;
import android.view.MenuItem;
import android.widget.Toast;

import com.android.phone.R;

public class PhoneAccountSettingsActivity extends PreferenceActivity {

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // Make sure we are running as an admin/work user.
        UserManager userManager = getSystemService(UserManager.class);
        if (!userManager.isAdminUser() && !userManager.isManagedProfile()) {
            Toast.makeText(this, R.string.phone_account_settings_user_restriction,
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        getWindow().addSystemFlags(
                android.view.WindowManager.LayoutParams
                        .SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS);
        final ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.phone_accounts);
        }
        getFragmentManager().beginTransaction().replace(
                android.R.id.content, new PhoneAccountSettingsFragment()).commit();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}

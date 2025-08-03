/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.provider.cts.simphonebook;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.SimPhonebookContract;
import android.provider.SimPhonebookContract.ElementaryFiles;
import android.telephony.SubscriptionInfo;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.rules.ExternalResource;

import java.util.List;
import java.util.Objects;

/** Removes all records from all the removalbe SIM cards after tests have run. */
class SimsCleanupRule extends ExternalResource {
    private static final String TAG = SimsCleanupRule.class.getSimpleName();
    private final ContentResolver mResolver;
    private final int mEfType;
    private final Bundle mExtras = new Bundle();

    SimsCleanupRule(int efType) {
        Context context = ApplicationProvider.getApplicationContext();
        mResolver = context.getContentResolver();
        mEfType = efType;
    }

    void setPin2(String pin2) {
        mExtras.putString(SimPhonebookContract.SimRecords.QUERY_ARG_PIN2, pin2);
    }

    @Override
    protected void after() {
        RemovableSims removableSims = new RemovableSims(
                ApplicationProvider.getApplicationContext());
        List<SubscriptionInfo> infos = removableSims.getSubscriptionInfoForRemovableSims();
        for (SubscriptionInfo info : infos) {
            if (mEfType == ElementaryFiles.EF_FDN) {
                clearFdn(info.getSubscriptionId());
            } else {
                clearEf(info.getSubscriptionId());
            }
        }
    }

    private void clearFdn(int subscriptionId) {
        SystemUtil.runWithShellPermissionIdentity(() -> clearEf(subscriptionId),
                Manifest.permission.MODIFY_PHONE_STATE);
    }

    private void clearEf(int subscriptionId) {
        try (Cursor cursor = Objects.requireNonNull(mResolver.query(
                SimPhonebookContract.SimRecords.getContentUri(subscriptionId, mEfType),
                new String[]{SimPhonebookContract.SimRecords.RECORD_NUMBER}, null, null))) {
            while (cursor.moveToNext()) {
                mResolver.delete(
                        SimPhonebookContract.SimRecords.getItemUri(subscriptionId, mEfType,
                                cursor.getInt(0)), mExtras);
            }
        } catch (Exception e) {
            // Swallow this so that the exception in the rule doesn't overwrite the failure in the
            // test.
            Log.e(TAG, "Failure cleaning up SIM", e);
        }
    }
}

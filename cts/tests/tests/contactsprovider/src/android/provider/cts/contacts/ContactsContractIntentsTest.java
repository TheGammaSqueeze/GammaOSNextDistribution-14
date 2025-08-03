/*
 * Copyright (C) 2013 The Android Open Source Project
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

package android.provider.cts.contacts;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.ContactsContract;
import android.test.AndroidTestCase;

import com.android.compatibility.common.util.CddTest;

import java.util.List;

/**
 * Tests to verify that common actions on {@link ContactsContract} content are
 * available.
 */
public class ContactsContractIntentsTest extends AndroidTestCase {
    public void assertCanBeHandled(Intent intent) {
        List<ResolveInfo> resolveInfoList = getContext()
                .getPackageManager().queryIntentActivities(intent, 0);
        assertNotNull("Missing ResolveInfo", resolveInfoList);
        assertTrue("No ResolveInfo found for " + intent.toString(),
                resolveInfoList.size() > 0);
    }

    public void testViewContactDir() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(ContactsContract.Contacts.CONTENT_URI);
        assertCanBeHandled(intent);
    }

    public void testPickContactDir() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setData(ContactsContract.Contacts.CONTENT_URI);
        assertCanBeHandled(intent);
    }

    public void testGetContentContactDir() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);
        assertCanBeHandled(intent);
    }

    @CddTest(requirements={"3.18/C-2-1"})
    public void testSetDefaultAccount() {
        PackageManager packageManager = getContext().getPackageManager();
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            return; // Skip test on watch since the intent is not required.
        }

        Intent intent = new Intent(ContactsContract.Settings.ACTION_SET_DEFAULT_ACCOUNT);
        List<ResolveInfo> resolveInfoList = packageManager.queryIntentActivities(intent, 0);
        assertNotNull("Missing ResolveInfo", resolveInfoList);
        int handlerCount = 0;
        for (ResolveInfo resolveInfo : resolveInfoList) {
            String packageName = resolveInfo.activityInfo.packageName;
            if (packageManager.checkPermission(
                    android.Manifest.permission.SET_DEFAULT_ACCOUNT_FOR_CONTACTS, packageName)
                    == PackageManager.PERMISSION_GRANTED) {
                handlerCount++;
            }
        }
        assertEquals(1, handlerCount);
    }
}

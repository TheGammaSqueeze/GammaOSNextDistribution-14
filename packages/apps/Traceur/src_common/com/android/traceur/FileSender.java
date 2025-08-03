/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.traceur;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.SystemProperties;
import android.util.Patterns;

import androidx.core.content.FileProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Sends bugreport-y files, adapted from fw/base/packages/Shell's BugreportReceiver.
 */
public class FileSender {

    private static final String MIME_TYPE = "application/vnd.android.systrace";

    public static List<Uri> getUriForFiles(Context context, List<File> files, String authority) {
        List<Uri> uris = new ArrayList();
        for (File file : files) {
            uris.add(FileProvider.getUriForFile(context, authority, file));
        }
        return uris;
    }

    /**
     * Build {@link Intent} that can be used to share the given bugreport.
     */
    public static Intent buildSendIntent(Context context, List<Uri> traceUris) {
        final CharSequence description = Build.FINGERPRINT;

        final Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setType(MIME_TYPE);

        intent.putExtra(Intent.EXTRA_SUBJECT, traceUris.get(0).getLastPathSegment());
        intent.putExtra(Intent.EXTRA_TEXT, description);
        intent.putExtra(Intent.EXTRA_STREAM, new ArrayList(traceUris));

        // Explicitly set the clip data; see b/119399115
        intent.setClipData(buildClipData(traceUris));

        final Account sendToAccount = findSendToAccount(context);
        if (sendToAccount != null) {
            intent.putExtra(Intent.EXTRA_EMAIL, new String[] { sendToAccount.name });
        }

        return intent;
    }

    private static ClipData buildClipData(List<Uri> uris) {
        ArrayList<ClipData.Item> items = new ArrayList();
        for (Uri uri : uris) {
            items.add(new ClipData.Item(Build.FINGERPRINT, null, uri));
        }
        ClipDescription description = new ClipDescription(null, new String[] { MIME_TYPE });
        return new ClipData(description, items);
    }


    /**
     * Find the best matching {@link Account} based on build properties.
     */
    private static Account findSendToAccount(Context context) {
        final AccountManager am = (AccountManager) context.getSystemService(
                Context.ACCOUNT_SERVICE);

        String preferredDomain = SystemProperties.get("sendbug.preferred.domain");
        if (!preferredDomain.startsWith("@")) {
            preferredDomain = "@" + preferredDomain;
        }

        final Account[] accounts = am.getAccounts();
        Account foundAccount = null;
        for (Account account : accounts) {
            if (Patterns.EMAIL_ADDRESS.matcher(account.name).matches()) {
                if (!preferredDomain.isEmpty()) {
                    // if we have a preferred domain and it matches, return; otherwise keep
                    // looking
                    if (account.name.endsWith(preferredDomain)) {
                        return account;
                    } else {
                        foundAccount = account;
                    }
                    // if we don't have a preferred domain, just return since it looks like
                    // an email address
                } else {
                    return account;
                }
            }
        }
        return foundAccount;
    }
}

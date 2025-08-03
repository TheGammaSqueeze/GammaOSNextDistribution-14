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

package android.security.cts.BUG_261036568_test;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.ClipData;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public class DeviceTest {
    private static final long WAIT_AND_ASSERT_FOUND_TIMEOUT_MS = 5000;
    private static final long WAIT_FOR_IDLE_TIMEOUT_MS = 5000;
    private static final long WAIT_AND_ASSERT_NOT_FOUND_TIMEOUT_MS = 2500;

    private static final String PROVIDER_AUTHORITY = "android.security.cts.BUG_261036568_provider";
    private static final Uri PROVIDER_AUTHORITY_URI = Uri.parse("content://" + PROVIDER_AUTHORITY);

    private ContentProviderClient mClient;
    private Uri mTargetImageUri;
    private Uri mTargetAuthorityUri;
    private Uri mTargetFileUri;

    @Before
    public void setUp() {
        Instrumentation instrumentation = getInstrumentation();
        Context context = instrumentation.getContext();

        // Get the id of a test user created by host side test
        Bundle args = InstrumentationRegistry.getArguments();
        int targetUser = Integer.parseInt(args.getString("target_user", "-1"));
        assumeTrue("Could not find target user", targetUser != -1);

        mTargetAuthorityUri = withUserId(PROVIDER_AUTHORITY_URI, targetUser);
        mTargetImageUri = withPath(mTargetAuthorityUri, "x.png");
        mTargetFileUri = withPath(mTargetAuthorityUri, "x.pdf");
    }

    @Test
    public void testShareUnownedUriAsPreview() {
        // SEND, single image
        openAndCloseSharesheet(createSendImageIntent(mTargetImageUri));
        // SEND, text with thumbnail
        openAndCloseSharesheet(createSendTextIntentWithPreview(mTargetImageUri));
        // SEND_MULTIPLE, two images
        openAndCloseSharesheet(createSendFileIntentWithPreview(mTargetImageUri, mTargetImageUri));
        // SEND_MULTIPLE, mixed types
        openAndCloseSharesheet(createSendFileIntentWithPreview(mTargetImageUri, mTargetFileUri));

        verifyNoContentProviderAccess();
    }

    private void openAndCloseSharesheet(Intent target) {
        Instrumentation instrumentation = getInstrumentation();
        UiDevice device = UiDevice.getInstance(instrumentation);
        Context context = instrumentation.getTargetContext();
        Intent chooserIntent = Intent.createChooser(target, null);
        chooserIntent.putExtra(Intent.EXTRA_AUTO_LAUNCH_SINGLE_CHOICE, false);
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        String chooserPackage = resolveChooserPackage(context);
        context.startActivity(chooserIntent);
        device.waitForIdle(WAIT_FOR_IDLE_TIMEOUT_MS);
        if (waitForPackageVisible(device, chooserPackage)) {
            device.pressBack();
            assumeTrue(waitForPackageGone(device, chooserPackage));
        }
    }

    private void verifyNoContentProviderAccess() {
        Instrumentation instrumentation = getInstrumentation();
        Context context = instrumentation.getContext();
        UiAutomation automation = instrumentation.getUiAutomation();
        ContentResolver resolver = context.getContentResolver();

        // only used for verification to access the provider directly
        automation.adoptShellPermissionIdentity("android.permission.INTERACT_ACROSS_USERS");

        try (ContentProviderClient client =
                     resolver.acquireContentProviderClient(mTargetAuthorityUri)) {
            assumeNotNull("Could not access '" + mTargetAuthorityUri, client);

            Bundle result = client.call("verify", null, null);
            assumeNotNull("Failed to fetch result from content provider", result);

            boolean passed = result.getBoolean("passed");
            ArrayList<String> accessedUris = result.getStringArrayList("accessed_uris");
            assertTrue("Failed. Cross user URI reads detected: " + accessedUris, passed);
        } catch (RemoteException e) {
            assumeNoException("Caught exception verifying result: " + e, e);
        } finally {
            automation.dropShellPermissionIdentity();
        }
    }

    private Intent createSendImageIntent(Uri image) {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_STREAM, image);
        sendIntent.setType("image/png");
        return sendIntent;
    }

    private Intent createSendTextIntentWithPreview(Uri image) {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TITLE, "Preview Title");
        sendIntent.putExtra(Intent.EXTRA_TEXT, "Sharing Text");
        sendIntent.setType("text/plain");
        sendIntent.setClipData(
                new ClipData(
                        "Clip Label",
                        new String[] {"image/png"},
                        new ClipData.Item(image)));
        return sendIntent;
    }

    private Intent createSendFileIntentWithPreview(Uri... uris) {
        Intent sendIntent = new Intent();
        if (uris.length > 1) {
            sendIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
            sendIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM,
                    new ArrayList<>(Arrays.asList(uris)));
        } else if (uris.length == 1) {
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_STREAM, uris[0]);
        }
        sendIntent.setType("application/pdf");
        return sendIntent;
    }

    private String resolveChooserPackage(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent shareIntent = Intent.createChooser(new Intent(), null);
        ResolveInfo chooser = pm.resolveActivity(shareIntent, PackageManager.MATCH_DEFAULT_ONLY);
        assertNotNull(chooser);
        assertNotNull(chooser.activityInfo);
        return chooser.activityInfo.packageName;
    }

    /**
     * Same as waitAndAssertFound but searching the entire device UI.
     */
    private boolean waitForPackageVisible(UiDevice device, String pkg) {
        return device.wait(
                Until.findObject(By.pkg(pkg).depth(0)),
                WAIT_AND_ASSERT_FOUND_TIMEOUT_MS
        ) != null;
    }

    /**
     * Same as waitAndAssertNotFound() but searching the entire device UI.
     */
    private boolean waitForPackageGone(UiDevice device, String pkg) {
        return device.wait(Until.gone(By.pkg(pkg)), WAIT_AND_ASSERT_NOT_FOUND_TIMEOUT_MS);
    }

    private static Uri withUserId(Uri uri, int userId) {
        Uri.Builder builder = uri.buildUpon();
        builder.encodedAuthority("" + userId + "@" + uri.getEncodedAuthority());
        return builder.build();
    }

    private static Uri withPath(Uri uri, String path) {
        return uri.buildUpon().appendPath(path).build();
    }
}

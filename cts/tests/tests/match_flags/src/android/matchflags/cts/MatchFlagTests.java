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

package android.matchflags.cts;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.FeatureUtil;
import com.android.compatibility.common.util.ShellUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MatchFlagTests {

    private static final String ONLY_BROWSER_URI =
            "https://nohandler-02xgpcssu1v7xvpek0skc905glnyu7ihjtza3eufox0mauqyri.com";
    private static final String UNIQUE_URI =
            "https://unique-5gle2bs6woovjn8xabwyb3js01xl0ducci3gd3fpe622h48lyg.com";

    private static final String SHARED_PKG_NAME = "android.matchflags.app.shared";
    private static final String UNIQUE_AND_SHARED_PKG_NAME =
            "android.matchflags.app.uniqueandshared";

    @Rule
    public TestName name = new TestName();

    @Before
    @After
    public void removeApprovals() {
        setDomainUserSelectionApproval(false);
    }

    @Test
    public void startNoBrowserIntentWithNoMatchingApps() throws Exception {
        assumeFalse("Skipping test for watch",
                InstrumentationRegistry.getInstrumentation().getTargetContext()
                        .getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH));

        Intent onlyBrowserIntent = new Intent(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setData(Uri.parse(ONLY_BROWSER_URI));

        if (isBrowserPresent(true)) {
            startActivity(onlyBrowserIntent);
        } else {
            try {
                startActivity(onlyBrowserIntent);
                fail("Device without browser should not launch browser only intent");
            } catch (ActivityNotFoundException e) {
                // hooray
            }
        }

        Intent noBrowserWithBrowserOnlyIntent = new Intent(onlyBrowserIntent)
                .addFlags(Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER);

        try {
            startActivity(noBrowserWithBrowserOnlyIntent);
            fail("Should not have started a browser-only intent with NON_BROWSER flag set");
        } catch (ActivityNotFoundException e) {
            // hooray!
        }
    }

    @Test
    public void startRequireDefaultWithNoDefault() throws Exception {
        Intent sharedIntent = new Intent("android.matchflags.app.SHARED_ACTION");

        startActivity(sharedIntent);

        Intent sharedIntentRequireDefault = new Intent(sharedIntent)
                .addFlags(Intent.FLAG_ACTIVITY_REQUIRE_DEFAULT);

        try {
            startActivity(sharedIntentRequireDefault);
            fail("Should have started intent with no default when default required");
        } catch (ActivityNotFoundException e) {
            // hooray!
        }
    }

    @Test
    public void startRequireDefaultWithSingleMatch() throws Exception {
        Intent uniqueIntent = new Intent("android.matchflags.app.UNIQUE_ACTION")
                .addFlags(Intent.FLAG_ACTIVITY_REQUIRE_DEFAULT);
        startActivity(uniqueIntent);
    }

    @Test
    public void startNoBrowserRequireDefault() throws Exception {
        setDomainUserSelectionApproval(true);
        startNoBrowserRequireDefaultInternal(true);
    }

    @Test
    public void startNoBrowserRequireDefaultUnapproved() throws Exception {
        assumeFalse("Skipping test for watch", FeatureUtil.isWatch());
        setDomainUserSelectionApproval(false);
        startNoBrowserRequireDefaultInternal(false);
    }

    private void startNoBrowserRequireDefaultInternal(boolean isDomainApproved) {
        Intent uniqueUriIntent = new Intent(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setData(Uri.parse(UNIQUE_URI));

        startActivity(uniqueUriIntent);

        Intent uniqueUriIntentNoBrowserRequireDefault = new Intent(uniqueUriIntent)
                .addFlags(Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER)
                .addFlags(Intent.FLAG_ACTIVITY_REQUIRE_DEFAULT);

        if (isBrowserPresent(false)) {
            // with non-browser, we'd expect the resolver
            // with require default, we'll get activity not found
            try {
                startActivity(uniqueUriIntentNoBrowserRequireDefault);
                if (!isDomainApproved) {
                    fail("Should fail to launch when started with non-browser and require default"
                            + " when browser present");
                }
            } catch (ActivityNotFoundException e) {
                // hooray!
                if (isDomainApproved) {
                    // Domain approval should force only the test Activity to be returned, which
                    // means it should pass the above flags and launch.
                    fail("Should succeed launch when started with non-browser and require default"
                            + " when browser present");
                }
            }
        } else {
            // with non-browser, but no browser present, we'd get a single result
            // with require default, we'll resolve to that single result
            try {
                startActivity(uniqueUriIntentNoBrowserRequireDefault);
                if (!isDomainApproved) {
                    fail("Should fail to launch when started with non-browser and require default"
                            + " when browser not present");
                }
            } catch (ActivityNotFoundException e) {
                if (isDomainApproved) {
                    fail("Should succeed launch when started with non-browser and require default"
                            + " when browser not present");
                }
            }
        }
    }

    private static boolean isBrowserPresent(boolean includeFallback) {
        return InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageManager()
                .queryIntentActivities(new Intent(Intent.ACTION_VIEW).addCategory(
                        Intent.CATEGORY_BROWSABLE).setData(Uri.parse(ONLY_BROWSER_URI)),
                        0 /* flags */)
                .stream().anyMatch(resolveInfo ->
                        resolveInfo.handleAllWebDataURI
                        && (includeFallback || resolveInfo.priority >= 0));
    }

    private static void startActivity(Intent onlyBrowserIntent) {
        InstrumentationRegistry.getInstrumentation().getContext().startActivity(
                onlyBrowserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private static void setDomainUserSelectionApproval(boolean approved) {
        String template = "pm set-app-links-user-selection --package %s --user all %b all";
        ShellUtils.runShellCommand(template, SHARED_PKG_NAME, approved);
        ShellUtils.runShellCommand(template, UNIQUE_AND_SHARED_PKG_NAME, approved);
    }
}

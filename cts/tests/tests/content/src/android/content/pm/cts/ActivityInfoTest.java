/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.content.pm.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Parcel;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.platform.test.annotations.IgnoreUnderRavenwood;
import android.platform.test.ravenwood.RavenwoodRule;
import android.util.StringBuilderPrinter;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link ActivityInfo}.
 */
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class ActivityInfoTest {
    @Rule
    public final RavenwoodRule mRavenwood = new RavenwoodRule();

    private static final String TEST_PKG = "android.content.cts";
    private static final String TEST_ACTIVITY = TEST_PKG + ".MockActivity";

    ActivityInfo mActivityInfo;

    private Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void testSimple() {
        ActivityInfo info = new ActivityInfo();
        new ActivityInfo(info);
        assertNotNull(info.toString());
        info.dump(new StringBuilderPrinter(new StringBuilder()), "");
    }

    @Test
    public void testConstructor() {
        new ActivityInfo();

        ActivityInfo info = new ActivityInfo();
        new ActivityInfo(info);

        try {
            new ActivityInfo(null);
            fail("should throw NullPointerException.");
        } catch (NullPointerException e) {
            // expected
        }
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = PackageManager.class)
    public void testWriteToParcel() throws NameNotFoundException {
        ComponentName componentName = new ComponentName(TEST_PKG, TEST_ACTIVITY);

        mActivityInfo = getContext().getPackageManager().getActivityInfo(
                componentName, PackageManager.GET_META_DATA);

        Parcel p = Parcel.obtain();
        mActivityInfo.writeToParcel(p, 0);
        p.setDataPosition(0);
        ActivityInfo info = ActivityInfo.CREATOR.createFromParcel(p);
        assertEquals(mActivityInfo.theme, info.theme);
        assertEquals(mActivityInfo.launchMode, info.launchMode);
        assertEquals(mActivityInfo.permission, info.permission);
        assertEquals(mActivityInfo.taskAffinity, info.taskAffinity);
        assertEquals(mActivityInfo.targetActivity, info.targetActivity);
        assertEquals(mActivityInfo.flags, info.flags);
        assertEquals(mActivityInfo.screenOrientation, info.screenOrientation);
        assertEquals(mActivityInfo.configChanges, info.configChanges);

        try {
            mActivityInfo.writeToParcel(null, 0);
            fail("should throw NullPointerException");
        } catch (NullPointerException e) {
            // expected
        }
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = PackageManager.class)
    public void testGetThemeResource() throws NameNotFoundException {
        ComponentName componentName = new ComponentName(TEST_PKG, TEST_ACTIVITY);

        mActivityInfo = getContext().getPackageManager().getActivityInfo(
                componentName, PackageManager.GET_META_DATA);

        assertEquals(mActivityInfo.applicationInfo.theme, mActivityInfo.getThemeResource());
        mActivityInfo.theme = 1;
        assertEquals(mActivityInfo.theme, mActivityInfo.getThemeResource());
    }

    @Test
    public void testToString() throws NameNotFoundException {
        mActivityInfo = new ActivityInfo();
        assertNotNull(mActivityInfo.toString());
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = PackageManager.class)
    public void testDescribeContents() throws NameNotFoundException {
        mActivityInfo = new ActivityInfo();
        assertEquals(0, mActivityInfo.describeContents());

        ComponentName componentName = new ComponentName(TEST_PKG, TEST_ACTIVITY);

        mActivityInfo = getContext().getPackageManager().getActivityInfo(
                componentName, PackageManager.GET_META_DATA);

        assertEquals(0, mActivityInfo.describeContents());
    }

    @Test
    public void testDump() {
        mActivityInfo = new ActivityInfo();

        StringBuilder sb = new StringBuilder();
        assertEquals(0, sb.length());
        StringBuilderPrinter p = new StringBuilderPrinter(sb);

        String prefix = "";
        mActivityInfo.dump(p, prefix);

        assertNotNull(sb.toString());
        assertTrue(sb.length() > 0);

        try {
            mActivityInfo.dump(null, "");
            fail("should throw NullPointerException.");
        } catch (NullPointerException e) {
            // expected
        }
    }
}

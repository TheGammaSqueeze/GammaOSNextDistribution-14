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
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Parcel;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.IgnoreUnderRavenwood;
import android.platform.test.ravenwood.RavenwoodRule;
import android.util.Printer;
import android.util.StringBuilderPrinter;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@AppModeFull // TODO(Instant) Figure out which APIs should work.
public class ResolveInfoTest {
    @Rule
    public final RavenwoodRule mRavenwood = new RavenwoodRule();

    private static final String PACKAGE_NAME = "android.content.cts";
    private static final String MAIN_ACTION_NAME = "android.intent.action.MAIN";
    private static final String ACTIVITY_NAME = "android.content.pm.cts.TestPmActivity";
    private static final String SERVICE_NAME = "android.content.pm.cts.activity.PMTEST_SERVICE";

    private Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void testSimple() {
        ResolveInfo info = new ResolveInfo();
        info.activityInfo = new ActivityInfo();
        info.activityInfo.packageName = "com.example";
        info.activityInfo.name = "com.example.Example";
        new ResolveInfo(info);
        assertNotNull(info.toString());
        info.dump(new StringBuilderPrinter(new StringBuilder()), "");
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = PackageManager.class)
    public final void testResolveInfo() {
        // Test constructor
        new ResolveInfo();

        PackageManager pm = getContext().getPackageManager();
        Intent intent = new Intent(MAIN_ACTION_NAME);
        intent.setComponent(new ComponentName(PACKAGE_NAME, ACTIVITY_NAME));
        ResolveInfo resolveInfo = pm.resolveActivity(intent, 0);
        // Test loadLabel, loadIcon, getIconResource, toString, describeContents
        String expectedLabel = "Android TestCase";
        assertEquals(expectedLabel, resolveInfo.loadLabel(pm).toString());
        assertNotNull(resolveInfo.loadIcon(pm));
        assertTrue(resolveInfo.getIconResource() != 0);
        assertNotNull(resolveInfo.toString());
        assertEquals(0, resolveInfo.describeContents());
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = PackageManager.class)
    public final void testLoadLabel_noNonLocalizedLabelAndNullPM_throwsNPE() {
        final ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.activityInfo = new ActivityInfo();

        assertThrows("Should throw NullPointerException", NullPointerException.class,
                () -> resolveInfo.loadLabel(null));
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = PackageManager.class)
    public final void testLoadLabel_hasNonLocalizedLabelAndNullPM_correctResult() {
        final ResolveInfo resolveInfo = new ResolveInfo();
        final String expectedResult = "none";
        resolveInfo.nonLocalizedLabel = expectedResult;

        assertEquals(expectedResult, resolveInfo.loadLabel(null));
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = PackageManager.class)
    public final void testDump() {
        PackageManager pm = getContext().getPackageManager();
        Intent intent = new Intent(SERVICE_NAME);
        ResolveInfo resolveInfo = pm.resolveService(intent, PackageManager.GET_RESOLVED_FILTER);

        Parcel p = Parcel.obtain();
        resolveInfo.writeToParcel(p, 0);
        p.setDataPosition(0);
        ResolveInfo infoFromParcel = ResolveInfo.CREATOR.createFromParcel(p);
        // Test writeToParcel
        assertEquals(resolveInfo.getIconResource(), infoFromParcel.getIconResource());
        assertEquals(resolveInfo.priority, infoFromParcel.priority);
        assertEquals(resolveInfo.preferredOrder, infoFromParcel.preferredOrder);
        assertEquals(resolveInfo.match, infoFromParcel.match);
        assertEquals(resolveInfo.specificIndex, infoFromParcel.specificIndex);
        assertEquals(resolveInfo.labelRes, infoFromParcel.labelRes);
        assertEquals(resolveInfo.nonLocalizedLabel, infoFromParcel.nonLocalizedLabel);
        assertEquals(resolveInfo.icon, infoFromParcel.icon);

        // Test dump
        TestPrinter printer = new TestPrinter();
        String prefix = "TestResolveInfo";
        resolveInfo.dump(printer, prefix);
        assertTrue(printer.isPrintlnCalled);
    }

    private class TestPrinter implements Printer {
        public boolean isPrintlnCalled;
        public void println(String x) {
            isPrintlnCalled = true;
        }
    }
}

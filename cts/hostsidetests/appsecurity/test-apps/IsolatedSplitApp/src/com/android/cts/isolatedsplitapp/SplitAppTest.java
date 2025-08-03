/*
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
 * limitations under the License.
 */

package com.android.cts.isolatedsplitapp;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

import androidx.test.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.model.Statement;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class SplitAppTest {
    private static final String PACKAGE = "com.android.cts.isolatedsplitapp";

    private static final ComponentName BASE_ACTIVITY =
            ComponentName.createRelative(PACKAGE, ".BaseActivity");
    private static final ComponentName FEATURE_A_ACTIVITY =
            ComponentName.createRelative(PACKAGE, ".feature_a.FeatureAActivity");
    private static final ComponentName FEATURE_B_ACTIVITY =
            ComponentName.createRelative(PACKAGE, ".feature_b.FeatureBActivity");
    private static final ComponentName FEATURE_C_ACTIVITY =
            ComponentName.createRelative(PACKAGE, ".feature_c.FeatureCActivity");

    private static final String FEATURE_A_STRING =
            "com.android.cts.isolatedsplitapp.feature_a:string/feature_a_string";
    private static final String FEATURE_B_STRING =
            "com.android.cts.isolatedsplitapp.feature_b:string/feature_b_string";
    private static final String FEATURE_C_STRING =
            "com.android.cts.isolatedsplitapp.feature_c:string/feature_c_string";
    private static final String FEATURE_A_TEXTVIEW_ID =
            "com.android.cts.isolatedsplitapp.feature_a:id/feature_a_text";
    private static final String FEATURE_B_TEXTVIEW_ID =
            "com.android.cts.isolatedsplitapp.feature_b:id/feature_b_text";
    private static final String FEATURE_C_TEXTVIEW_ID =
            "com.android.cts.isolatedsplitapp.feature_c:id/feature_c_text";

    private static final Configuration PL = new Configuration();
    static {
        PL.setLocale(Locale.forLanguageTag("pl"));
    }

    // Do not launch this activity lazily. We use this rule to launch all Activities,
    // so we use #launchActivity() with the correct Intent.
    @Rule
    public ActivityTestRule<Activity> mActivityRule =
            new ActivityTestRule<>(Activity.class, true /*initialTouchMode*/,
                    false /*launchActivity*/);

    @Rule
    public AppContextTestRule mAppContextTestRule = new AppContextTestRule();

    @Before
    public void setUp() {
        BaseActivity.setOverrideConfiguration(null);
    }

    @Test
    public void shouldLoadDefault() throws Exception {
        final Activity activity = mActivityRule.launchActivity(
                new Intent().setComponent(BASE_ACTIVITY));
        final TestTheme testTheme = new TestTheme(activity, R.style.Theme_Base);
        final Resources resources = activity.getResources();
        assertThat(resources, notNullValue());

        assertThat(resources.getString(R.string.base_string), equalTo("Base String Default"));
        testTheme.assertThemeBaseValues();

        // Test the theme applied to the activity correctly
        assertActivityThemeApplied(activity, testTheme);

        // The base does not depend on any splits so no splits should be accessible.
        assertActivitiesDoNotExist(activity, FEATURE_A_ACTIVITY, FEATURE_B_ACTIVITY,
                FEATURE_C_ACTIVITY);
        assertResourcesDoNotExist(activity, FEATURE_A_STRING, FEATURE_B_STRING, FEATURE_C_STRING,
                TestTheme.THEME_FEATURE_A, TestTheme.THEME_FEATURE_B, TestTheme.THEME_FEATURE_C);
    }

    @Test
    public void shouldLoadPolishLocale() throws Exception {
        BaseActivity.setOverrideConfiguration(PL);
        final Activity activity = mActivityRule.launchActivity(
                new Intent().setComponent(BASE_ACTIVITY));
        final TestTheme testTheme = new TestTheme(activity, R.style.Theme_Base);
        final Resources resources = activity.getResources();
        assertThat(resources, notNullValue());

        assertThat(resources.getString(R.string.base_string), equalTo("Base String Polish"));
        testTheme.assertThemeBaseValues_pl();

        // Test the theme applied to the activity correctly
        assertActivityThemeApplied(activity, testTheme);

        // The base does not depend on any splits so no splits should be accessible.
        assertActivitiesDoNotExist(activity, FEATURE_A_ACTIVITY, FEATURE_B_ACTIVITY,
                FEATURE_C_ACTIVITY);
        assertResourcesDoNotExist(activity, FEATURE_A_STRING, FEATURE_B_STRING, FEATURE_C_STRING,
                TestTheme.THEME_FEATURE_A, TestTheme.THEME_FEATURE_B, TestTheme.THEME_FEATURE_C);
    }

    @Test
    public void shouldLoadFeatureADefault() throws Exception {
        final Activity activity = mActivityRule.launchActivity(
                new Intent().setComponent(FEATURE_A_ACTIVITY));
        final TestTheme testTheme = new TestTheme(activity, TestTheme.THEME_FEATURE_A);
        final Resources resources = activity.getResources();
        assertThat(resources, notNullValue());

        assertThat(resources.getString(R.string.base_string), equalTo("Base String Default"));
        new TestTheme(activity, R.style.Theme_Base).assertThemeBaseValues();

        int resourceId = resources.getIdentifier(FEATURE_A_STRING, null, null);
        assertThat(resources.getString(resourceId), equalTo("Feature A String Default"));
        testTheme.assertThemeFeatureAValues();

        // Test the theme applied to the activity correctly
        assertActivityThemeApplied(activity, testTheme);
        assertTextViewBGColor(activity, FEATURE_A_TEXTVIEW_ID, testTheme.mColorBackground);

        assertActivitiesDoNotExist(activity, FEATURE_B_ACTIVITY, FEATURE_C_ACTIVITY);
        assertResourcesDoNotExist(activity, FEATURE_B_STRING, FEATURE_C_STRING,
                TestTheme.THEME_FEATURE_B, TestTheme.THEME_FEATURE_C);
    }

    @Test
    public void shouldLoadFeatureAPolishLocale() throws Exception {
        BaseActivity.setOverrideConfiguration(PL);
        final Activity activity = mActivityRule.launchActivity(
                new Intent().setComponent(FEATURE_A_ACTIVITY));
        final TestTheme testTheme = new TestTheme(activity, TestTheme.THEME_FEATURE_A);
        final Resources resources = activity.getResources();
        assertThat(resources, notNullValue());

        assertThat(resources.getString(R.string.base_string), equalTo("Base String Polish"));
        new TestTheme(activity, R.style.Theme_Base).assertThemeBaseValues_pl();

        int resourceId = resources.getIdentifier(FEATURE_A_STRING, null, null);
        assertThat(resources.getString(resourceId), equalTo("Feature A String Polish"));
        testTheme.assertThemeFeatureAValues_pl();

        // Test the theme applied to the activity correctly
        assertActivityThemeApplied(activity, testTheme);
        assertTextViewBGColor(activity, FEATURE_A_TEXTVIEW_ID, testTheme.mColorBackground);

        assertActivitiesDoNotExist(activity, FEATURE_B_ACTIVITY, FEATURE_C_ACTIVITY);
        assertResourcesDoNotExist(activity, FEATURE_B_STRING, FEATURE_C_STRING,
                TestTheme.THEME_FEATURE_B, TestTheme.THEME_FEATURE_C);
    }

    @Test
    public void shouldLoadFeatureAReceivers() throws Exception {
        final Context context = mAppContextTestRule.getContext();
        final ExtrasResultReceiver receiver = sendOrderedBroadcast(context);
        final Bundle results = receiver.get();
        assertThat(results.getString("base"), equalTo("Base String Default"));
        assertThat(results.getString("feature_a"), equalTo("Feature A String Default"));
        assertThat(results.getString("feature_b"), nullValue());
        assertThat(results.getString("feature_c"), nullValue());
    }

    @Test
    public void shouldLoadFeatureBDefault() throws Exception {
        // Feature B depends on A, so we expect both to be available.
        final Activity activity = mActivityRule.launchActivity(
                new Intent().setComponent(FEATURE_B_ACTIVITY));
        final TestTheme testTheme = new TestTheme(activity, TestTheme.THEME_FEATURE_B);
        final Resources resources = activity.getResources();
        assertThat(resources, notNullValue());

        assertThat(resources.getString(R.string.base_string), equalTo("Base String Default"));
        new TestTheme(activity, R.style.Theme_Base).assertThemeBaseValues();

        int resourceId = resources.getIdentifier(FEATURE_A_STRING, null, null);
        assertThat(resources.getString(resourceId), equalTo("Feature A String Default"));
        new TestTheme(activity, TestTheme.THEME_FEATURE_A).assertThemeFeatureAValues();

        resourceId = resources.getIdentifier(FEATURE_B_STRING, null, null);
        assertThat(resources.getString(resourceId), equalTo("Feature B String Default"));
        testTheme.assertThemeFeatureBValues();

        // Test the theme applied to the activity correctly
        assertActivityThemeApplied(activity, testTheme);
        assertTextViewBGColor(activity, FEATURE_B_TEXTVIEW_ID, testTheme.mColorBackground);

        assertActivitiesDoNotExist(activity, FEATURE_C_ACTIVITY);
        assertResourcesDoNotExist(activity, FEATURE_C_STRING, TestTheme.THEME_FEATURE_C);
    }

    @Test
    public void shouldLoadFeatureBPolishLocale() throws Exception {
        BaseActivity.setOverrideConfiguration(PL);
        final Activity activity = mActivityRule.launchActivity(
                new Intent().setComponent(FEATURE_B_ACTIVITY));
        final TestTheme testTheme = new TestTheme(activity, TestTheme.THEME_FEATURE_B);
        final Resources resources = activity.getResources();
        assertThat(resources, notNullValue());

        assertThat(resources.getString(R.string.base_string), equalTo("Base String Polish"));
        new TestTheme(activity, R.style.Theme_Base).assertThemeBaseValues_pl();

        int resourceId = resources.getIdentifier(FEATURE_A_STRING, null, null);
        assertThat(resources.getString(resourceId), equalTo("Feature A String Polish"));
        new TestTheme(activity, TestTheme.THEME_FEATURE_A).assertThemeFeatureAValues_pl();

        resourceId = resources.getIdentifier(FEATURE_B_STRING, null, null);
        assertThat(resources.getString(resourceId), equalTo("Feature B String Polish"));
        testTheme.assertThemeFeatureBValues_pl();

        // Test the theme applied to the activity correctly
        assertActivityThemeApplied(activity, testTheme);
        assertTextViewBGColor(activity, FEATURE_B_TEXTVIEW_ID, testTheme.mColorBackground);

        assertActivitiesDoNotExist(activity, FEATURE_C_ACTIVITY);
        assertResourcesDoNotExist(activity, FEATURE_C_STRING, TestTheme.THEME_FEATURE_C);
    }

    @Test
    public void shouldLoadFeatureAAndBReceivers() throws Exception {
        final Context context = mAppContextTestRule.getContext();
        final ExtrasResultReceiver receiver = sendOrderedBroadcast(context);
        final Bundle results = receiver.get();
        assertThat(results.getString("base"), equalTo("Base String Default"));
        assertThat(results.getString("feature_a"), equalTo("Feature A String Default"));
        assertThat(results.getString("feature_b"), equalTo("Feature B String Default"));
        assertThat(results.getString("feature_c"), nullValue());
    }

    @Test
    public void shouldLoadFeatureCDefault() throws Exception {
        final Activity activity = mActivityRule.launchActivity(
                new Intent().setComponent(FEATURE_C_ACTIVITY));
        final TestTheme testTheme = new TestTheme(activity, TestTheme.THEME_FEATURE_C);
        final Resources resources = activity.getResources();
        assertThat(resources, notNullValue());

        assertThat(resources.getString(R.string.base_string), equalTo("Base String Default"));
        new TestTheme(activity, R.style.Theme_Base).assertThemeBaseValues();

        int resourceId = resources.getIdentifier(FEATURE_C_STRING, null, null);
        assertThat(resources.getString(resourceId), equalTo("Feature C String Default"));
        testTheme.assertThemeFeatureCValues();

        // Test the theme applied to the activity correctly
        assertActivityThemeApplied(activity, testTheme);
        assertTextViewBGColor(activity, FEATURE_C_TEXTVIEW_ID, testTheme.mColorBackground);

        assertActivitiesDoNotExist(activity, FEATURE_A_ACTIVITY, FEATURE_B_ACTIVITY);
        assertResourcesDoNotExist(activity, FEATURE_A_STRING, FEATURE_B_STRING,
                TestTheme.THEME_FEATURE_A, TestTheme.THEME_FEATURE_B);
    }

    @Test
    public void shouldLoadFeatureCPolishLocale() throws Exception {
        BaseActivity.setOverrideConfiguration(PL);
        final Activity activity = mActivityRule.launchActivity(
                new Intent().setComponent(FEATURE_C_ACTIVITY));
        final TestTheme testTheme = new TestTheme(activity, TestTheme.THEME_FEATURE_C);
        final Resources resources = activity.getResources();
        assertThat(resources, notNullValue());

        assertThat(resources.getString(R.string.base_string), equalTo("Base String Polish"));
        new TestTheme(activity, R.style.Theme_Base).assertThemeBaseValues_pl();

        int resourceId = resources.getIdentifier(FEATURE_C_STRING, null, null);
        assertThat(resources.getString(resourceId), equalTo("Feature C String Polish"));
        testTheme.assertThemeFeatureCValues_pl();

        // Test the theme applied to the activity correctly
        assertActivityThemeApplied(activity, testTheme);
        assertTextViewBGColor(activity, FEATURE_C_TEXTVIEW_ID, testTheme.mColorBackground);

        assertActivitiesDoNotExist(activity, FEATURE_A_ACTIVITY, FEATURE_B_ACTIVITY);
        assertResourcesDoNotExist(activity, FEATURE_A_STRING, FEATURE_B_STRING,
                TestTheme.THEME_FEATURE_A, TestTheme.THEME_FEATURE_B);
    }

    @Test
    public void shouldLoadFeatureADiffRevision() throws Exception {
        final Activity activity = mActivityRule.launchActivity(
                new Intent().setComponent(FEATURE_A_ACTIVITY));
        final TestTheme testTheme = new TestTheme(activity, TestTheme.THEME_FEATURE_A);
        final Resources resources = activity.getResources();
        assertThat(resources, notNullValue());

        assertThat(resources.getString(R.string.base_string), equalTo("Base String Default"));
        new TestTheme(activity, R.style.Theme_Base).assertThemeBaseValues();

        int resourceId = resources.getIdentifier(FEATURE_A_STRING, null, null);
        assertThat(resources.getString(resourceId), equalTo("Feature A String Diff Revision"));
        testTheme.assertThemeFeatureAValuesDiffRev();

        // Test the theme applied to the activity correctly
        assertActivityThemeApplied(activity, testTheme);
        assertTextViewBGColor(activity, FEATURE_A_TEXTVIEW_ID, testTheme.mColorBackground);

        assertActivitiesDoNotExist(activity, FEATURE_B_ACTIVITY, FEATURE_C_ACTIVITY);
        assertResourcesDoNotExist(activity, FEATURE_B_STRING, FEATURE_C_STRING,
                TestTheme.THEME_FEATURE_B, TestTheme.THEME_FEATURE_C);
    }

    @Test
    public void shouldLoadFeatureAAndBAndCReceivers() throws Exception {
        final Context context = mAppContextTestRule.getContext();
        final ExtrasResultReceiver receiver = sendOrderedBroadcast(context);
        final Bundle results = receiver.get();
        assertThat(results.getString("base"), equalTo("Base String Default"));
        assertThat(results.getString("feature_a"), equalTo("Feature A String Default"));
        assertThat(results.getString("feature_b"), equalTo("Feature B String Default"));
        assertThat(results.getString("feature_c"), equalTo("Feature C String Default"));
    }

    @Test
    public void shouldNotFoundFeatureC() throws Exception {
        assertActivityDoNotExist(FEATURE_C_ACTIVITY);
    }

    @Test
    public void testNativeJni_shouldBeLoaded() throws Exception {
        assertThat(Native.add(7, 11), equalTo(18));
    }

    @Test
    public void testNativeSplit_withoutExtractLibs_nativeLibraryCannotBeLoaded() throws Exception {
        final Intent intent = new Intent();
        intent.setClassName(PACKAGE, "com.android.cts.isolatedsplitapp.jni.JniActivity");
        mActivityRule.launchActivity(intent);
        mActivityRule.finishActivity();
        Instrumentation.ActivityResult result = mActivityRule.getActivityResult();
        final Intent resultData = result.getResultData();
        final String errorMessage = resultData.getStringExtra(Intent.EXTRA_RETURN_RESULT);
        assertThat(errorMessage, containsString("dlopen failed"));
    }

    @Test
    public void testNative_getNumberADirectly_shouldBeSeven() throws Exception {
        assertThat(Native.getNumberADirectly(), equalTo(7));
    }

    @Test
    public void testNative_getNumberAViaProxy_shouldBeSeven() throws Exception {
        assertThat(Native.getNumberAViaProxy(), equalTo(7));
    }

    @Test
    public void testNative_getNumberBDirectly_shouldBeEleven() throws Exception {
        assertThat(Native.getNumberBDirectly(), equalTo(11));
    }

    @Test
    public void testNative_getNumberBViaProxy_shouldBeEleven() throws Exception {
        assertThat(Native.getNumberBViaProxy(), equalTo(11));
    }

    @Test
    public void testNative_cannotLoadSharedLibrary() throws Exception {
        assertThat(Native.isLoadedLibrary(), equalTo(false));
    }

    private void assertActivityDoNotExist(ComponentName activity) {
        try {
            mActivityRule.launchActivity(new Intent().setComponent(activity));
            fail("Activity " + activity + " is accessible");
        } catch (RuntimeException e) {
            // Pass.
        }
    }

    private static void assertActivitiesDoNotExist(Context context, ComponentName... activities) {
        for (ComponentName activity : activities) {
            try {
                Class.forName(activity.getClassName(), true, context.getClassLoader());
                fail("Class " + activity.getClassName() + " is accessible");
            } catch (ClassNotFoundException e) {
                // Pass.
            }
        }
    }

    private static void assertResourcesDoNotExist(Context context, String... resourceNames) {
        final Resources resources = context.getResources();
        for (String resourceName : resourceNames) {
            final int resid = resources.getIdentifier(resourceName, null, null);
            if (resid != 0) {
                fail("Found resource '" + resourceName + "' with ID " + Integer.toHexString(resid));
            }
        }
    }

    private static void assertActivityThemeApplied(Activity activity, TestTheme testTheme) {
        assertBaseLayoutBGColor(activity, testTheme.mBaseColor);
        assertThat(activity.getWindow().getStatusBarColor(), equalTo(testTheme.mStatusBarColor));
        assertThat(activity.getWindow().getNavigationBarColor(),
                equalTo(testTheme.mNavigationBarColor));
        assertDrawableColor(activity.getWindow().getDecorView().getBackground(),
                testTheme.mWindowBackground);
    }

    private static void assertBaseLayoutBGColor(Activity activity, int expected) {
        final LinearLayout layout = activity.findViewById(R.id.base_layout);
        final Drawable background = layout.getBackground();
        assertDrawableColor(background, expected);
    }

    private static void assertTextViewBGColor(Activity activity, String nameOfIdentifier,
            int expected) {
        final int viewId = activity.getResources().getIdentifier(nameOfIdentifier, null, null);
        assertThat(viewId, not(equalTo(0)));

        final View view = activity.findViewById(viewId);
        final Drawable background = view.getBackground();
        assertDrawableColor(background, expected);
    }

    private static void assertDrawableColor(Drawable drawable, int expected) {
        int color = 0;
        if (drawable instanceof ColorDrawable) {
            color = ((ColorDrawable) drawable).getColor();
        } else {
            fail("Can't get drawable color");
        }
        assertThat(color, equalTo(expected));
    }

    private static class ExtrasResultReceiver extends BroadcastReceiver {
        private final CompletableFuture<Bundle> mResult = new CompletableFuture<>();

        @Override
        public void onReceive(Context context, Intent intent) {
            mResult.complete(getResultExtras(true));
        }

        public Bundle get() throws Exception {
            return mResult.get(5000, TimeUnit.SECONDS);
        }
    }

    private static ExtrasResultReceiver sendOrderedBroadcast(Context context) {
        final ExtrasResultReceiver resultReceiver = new ExtrasResultReceiver();
        context.sendOrderedBroadcast(new Intent(PACKAGE + ".ACTION").setPackage(PACKAGE), null,
                resultReceiver, null, 0, null, null);
        return resultReceiver;
    }

    private static class AppContextTestRule implements TestRule {
        private Context mContext;

        @Override
        public Statement apply(final Statement base, Description description) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
                    base.evaluate();
                }
            };
        }

        public Context getContext() {
            return mContext;
        }
    }
}

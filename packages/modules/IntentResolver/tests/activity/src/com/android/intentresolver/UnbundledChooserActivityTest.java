/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.intentresolver;

import static android.app.Activity.RESULT_OK;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.longClick;
import static androidx.test.espresso.action.ViewActions.swipeUp;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasSibling;
import static androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static com.android.intentresolver.ChooserActivity.TARGET_TYPE_CHOOSER_TARGET;
import static com.android.intentresolver.ChooserActivity.TARGET_TYPE_DEFAULT;
import static com.android.intentresolver.ChooserActivity.TARGET_TYPE_SHORTCUTS_FROM_PREDICTION_SERVICE;
import static com.android.intentresolver.ChooserActivity.TARGET_TYPE_SHORTCUTS_FROM_SHORTCUT_MANAGER;
import static com.android.intentresolver.ChooserListAdapter.CALLER_TARGET_SCORE_BOOST;
import static com.android.intentresolver.ChooserListAdapter.SHORTCUT_TARGET_SCORE_BOOST;
import static com.android.intentresolver.MatcherUtils.first;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static junit.framework.Assert.assertNull;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.PendingIntent;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager.ShareShortcutInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.DeviceConfig;
import android.service.chooser.ChooserAction;
import android.service.chooser.ChooserTarget;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.Pair;
import android.util.SparseArray;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.espresso.matcher.BoundedDiagnosingMatcher;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.intentresolver.chooser.DisplayResolveInfo;
import com.android.intentresolver.contentpreview.ImageLoader;
import com.android.intentresolver.logging.EventLog;
import com.android.intentresolver.logging.FakeEventLog;
import com.android.intentresolver.shortcuts.ShortcutLoader;
import com.android.internal.config.sysui.SystemUiDeviceConfigFlags;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Instrumentation tests for ChooserActivity.
 * <p>
 * Legacy test suite migrated from framework CoreTests.
 * <p>
 */
@RunWith(Parameterized.class)
@HiltAndroidTest
public class UnbundledChooserActivityTest {

    private static FakeEventLog getEventLog(ChooserWrapperActivity activity) {
        return (FakeEventLog) activity.mEventLog;
    }

    private static final UserHandle PERSONAL_USER_HANDLE = InstrumentationRegistry
            .getInstrumentation().getTargetContext().getUser();
    private static final UserHandle WORK_PROFILE_USER_HANDLE = UserHandle.of(10);
    private static final UserHandle CLONE_PROFILE_USER_HANDLE = UserHandle.of(11);

    private static final Function<PackageManager, PackageManager> DEFAULT_PM = pm -> pm;
    private static final Function<PackageManager, PackageManager> NO_APP_PREDICTION_SERVICE_PM =
            pm -> {
                PackageManager mock = Mockito.spy(pm);
                when(mock.getAppPredictionServicePackageName()).thenReturn(null);
                return mock;
            };

    @Parameterized.Parameters
    public static Collection packageManagers() {
        return Arrays.asList(new Object[][] {
                // Default PackageManager
                { DEFAULT_PM },
                // No App Prediction Service
                { NO_APP_PREDICTION_SERVICE_PM}
        });
    }

    private static final String TEST_MIME_TYPE = "application/TestType";

    private static final int CONTENT_PREVIEW_IMAGE = 1;
    private static final int CONTENT_PREVIEW_FILE = 2;
    private static final int CONTENT_PREVIEW_TEXT = 3;

    @Rule(order = 0)
    public CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule(order = 1)
    public HiltAndroidRule mHiltAndroidRule = new HiltAndroidRule(this);

    @Rule(order = 2)
    public ActivityTestRule<ChooserWrapperActivity> mActivityRule =
            new ActivityTestRule<>(ChooserWrapperActivity.class, false, false);

    @Before
    public void setUp() {
        // TODO: use the other form of `adoptShellPermissionIdentity()` where we explicitly list the
        // permissions we require (which we'll read from the manifest at runtime).
        InstrumentationRegistry
                .getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity();

        cleanOverrideData();
        mHiltAndroidRule.inject();
    }

    private final Function<PackageManager, PackageManager> mPackageManagerOverride;

    public UnbundledChooserActivityTest(
                Function<PackageManager, PackageManager> packageManagerOverride) {
        mPackageManagerOverride = packageManagerOverride;
    }

    private void setDeviceConfigProperty(
            @NonNull String propertyName,
            @NonNull String value) {
        // TODO: consider running with {@link #runWithShellPermissionIdentity()} to more narrowly
        // request WRITE_DEVICE_CONFIG permissions if we get rid of the broad grant we currently
        // configure in {@link #setup()}.
        // TODO: is it really appropriate that this is always set with makeDefault=true?
        boolean valueWasSet = DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_SYSTEMUI,
                propertyName,
                value,
                true /* makeDefault */);
        if (!valueWasSet) {
            throw new IllegalStateException(
                        "Could not set " + propertyName + " to " + value);
        }
    }

    public void cleanOverrideData() {
        ChooserActivityOverrideData.getInstance().reset();
        ChooserActivityOverrideData.getInstance().createPackageManager = mPackageManagerOverride;

        setDeviceConfigProperty(
                SystemUiDeviceConfigFlags.APPLY_SHARING_APP_LIMITS_IN_SYSUI,
                Boolean.toString(true));
    }

    @Test
    public void customTitle() throws InterruptedException {
        Intent viewIntent = createViewTextIntent();
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);
        final IChooserWrapper activity = (IChooserWrapper) mActivityRule.launchActivity(
                Intent.createChooser(viewIntent, "chooser test"));

        waitForIdle();
        assertThat(activity.getAdapter().getCount(), is(2));
        assertThat(activity.getAdapter().getServiceTargetCount(), is(0));
        onView(withId(android.R.id.title)).check(matches(withText("chooser test")));
    }

    @Test
    public void customTitleIgnoredForSendIntents() throws InterruptedException {
        Intent sendIntent = createSendTextIntent();
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);
        mActivityRule.launchActivity(Intent.createChooser(sendIntent, "chooser test"));
        waitForIdle();
        onView(withId(android.R.id.title))
                .check(matches(withText(R.string.whichSendApplication)));
    }

    @Test
    public void emptyTitle() throws InterruptedException {
        Intent sendIntent = createSendTextIntent();
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);
        mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();
        onView(withId(android.R.id.title))
                .check(matches(withText(R.string.whichSendApplication)));
    }

    @Test
    public void test_shareRichTextWithRichTitle_richTextAndRichTitleDisplayed() {
        CharSequence title = new SpannableStringBuilder()
                .append("Rich", new UnderlineSpan(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
                .append(
                        "Title",
                        new ForegroundColorSpan(Color.RED),
                        Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
        CharSequence sharedText = new SpannableStringBuilder()
                .append(
                        "Rich",
                        new BackgroundColorSpan(Color.YELLOW),
                        Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
                .append(
                        "Text",
                        new StyleSpan(Typeface.ITALIC),
                        Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        Intent sendIntent = createSendTextIntent();
        sendIntent.putExtra(Intent.EXTRA_TEXT, sharedText);
        sendIntent.putExtra(Intent.EXTRA_TITLE, title);
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);
        setupResolverControllers(resolvedComponentInfos);

        mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        onView(withId(com.android.internal.R.id.content_preview_title))
                .check((view, e) -> {
                    assertThat(view).isInstanceOf(TextView.class);
                    CharSequence text = ((TextView) view).getText();
                    assertThat(text).isInstanceOf(Spanned.class);
                    Spanned spanned = (Spanned) text;
                    assertThat(spanned.getSpans(0, spanned.length(), Object.class))
                            .hasLength(2);
                    assertThat(spanned.getSpans(0, 4, UnderlineSpan.class)).hasLength(1);
                    assertThat(spanned.getSpans(4, spanned.length(), ForegroundColorSpan.class))
                            .hasLength(1);
                });

        onView(withId(com.android.internal.R.id.content_preview_text))
                .check((view, e) -> {
                    assertThat(view).isInstanceOf(TextView.class);
                    CharSequence text = ((TextView) view).getText();
                    assertThat(text).isInstanceOf(Spanned.class);
                    Spanned spanned = (Spanned) text;
                    assertThat(spanned.getSpans(0, spanned.length(), Object.class))
                            .hasLength(2);
                    assertThat(spanned.getSpans(0, 4, BackgroundColorSpan.class)).hasLength(1);
                    assertThat(spanned.getSpans(4, spanned.length(), StyleSpan.class)).hasLength(1);
                });
    }

    @Test
    public void emptyPreviewTitleAndThumbnail() throws InterruptedException {
        Intent sendIntent = createSendTextIntentWithPreview(null, null);
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);
        mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();
        onView(withId(com.android.internal.R.id.content_preview_title))
                .check(matches(not(isDisplayed())));
        onView(withId(com.android.internal.R.id.content_preview_thumbnail))
                .check(matches(not(isDisplayed())));
    }

    @Test
    public void visiblePreviewTitleWithoutThumbnail() throws InterruptedException {
        String previewTitle = "My Content Preview Title";
        Intent sendIntent = createSendTextIntentWithPreview(previewTitle, null);
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);
        mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();
        onView(withId(com.android.internal.R.id.content_preview_title))
                .check(matches(isDisplayed()));
        onView(withId(com.android.internal.R.id.content_preview_title))
                .check(matches(withText(previewTitle)));
        onView(withId(com.android.internal.R.id.content_preview_thumbnail))
                .check(matches(not(isDisplayed())));
    }

    @Test
    public void visiblePreviewTitleWithInvalidThumbnail() throws InterruptedException {
        String previewTitle = "My Content Preview Title";
        Intent sendIntent = createSendTextIntentWithPreview(previewTitle,
                Uri.parse("tel:(+49)12345789"));
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);
        mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();
        onView(withId(com.android.internal.R.id.content_preview_title))
                .check(matches(isDisplayed()));
        onView(withId(com.android.internal.R.id.content_preview_thumbnail))
                .check(matches(not(isDisplayed())));
    }

    @Test
    public void visiblePreviewTitleAndThumbnail() throws InterruptedException {
        String previewTitle = "My Content Preview Title";
        Uri uri = Uri.parse(
                "android.resource://com.android.frameworks.coretests/"
                + com.android.intentresolver.tests.R.drawable.test320x240);
        Intent sendIntent = createSendTextIntentWithPreview(previewTitle, uri);
        ChooserActivityOverrideData.getInstance().imageLoader =
                createImageLoader(uri, createBitmap());
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);
        mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();
        onView(withId(com.android.internal.R.id.content_preview_title))
                .check(matches(isDisplayed()));
        onView(withId(com.android.internal.R.id.content_preview_thumbnail))
                .check(matches(isDisplayed()));
    }

    @Test @Ignore
    public void twoOptionsAndUserSelectsOne() throws InterruptedException {
        Intent sendIntent = createSendTextIntent();
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);

        final IChooserWrapper activity = (IChooserWrapper)
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        assertThat(activity.getAdapter().getCount(), is(2));
        onView(withId(com.android.internal.R.id.profile_button)).check(doesNotExist());

        ResolveInfo[] chosen = new ResolveInfo[1];
        ChooserActivityOverrideData.getInstance().onSafelyStartInternalCallback = targetInfo -> {
            chosen[0] = targetInfo.getResolveInfo();
            return true;
        };

        ResolveInfo toChoose = resolvedComponentInfos.get(0).getResolveInfoAt(0);
        onView(withText(toChoose.activityInfo.name))
                .perform(click());
        waitForIdle();
        assertThat(chosen[0], is(toChoose));
    }

    @Test @Ignore
    public void fourOptionsStackedIntoOneTarget() throws InterruptedException {
        Intent sendIntent = createSendTextIntent();

        // create just enough targets to ensure the a-z list should be shown
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(1);

        // next create 4 targets in a single app that should be stacked into a single target
        String packageName = "xxx.yyy";
        String appName = "aaa";
        ComponentName cn = new ComponentName(packageName, appName);
        Intent intent = new Intent("fakeIntent");
        List<ResolvedComponentInfo> infosToStack = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            ResolveInfo resolveInfo = ResolverDataProvider.createResolveInfo(i,
                    UserHandle.USER_CURRENT, PERSONAL_USER_HANDLE);
            resolveInfo.activityInfo.applicationInfo.name = appName;
            resolveInfo.activityInfo.applicationInfo.packageName = packageName;
            resolveInfo.activityInfo.packageName = packageName;
            resolveInfo.activityInfo.name = "ccc" + i;
            infosToStack.add(new ResolvedComponentInfo(cn, intent, resolveInfo));
        }
        resolvedComponentInfos.addAll(infosToStack);

        setupResolverControllers(resolvedComponentInfos);

        final IChooserWrapper activity = (IChooserWrapper)
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        // expect 1 unique targets + 1 group + 4 ranked app targets
        assertThat(activity.getAdapter().getCount(), is(6));

        ResolveInfo[] chosen = new ResolveInfo[1];
        ChooserActivityOverrideData.getInstance().onSafelyStartInternalCallback = targetInfo -> {
            chosen[0] = targetInfo.getResolveInfo();
            return true;
        };

        onView(allOf(withText(appName), hasSibling(withText("")))).perform(click());
        waitForIdle();

        // clicking will launch a dialog to choose the activity within the app
        onView(withText(appName)).check(matches(isDisplayed()));
        int i = 0;
        for (ResolvedComponentInfo rci: infosToStack) {
            onView(withText("ccc" + i)).check(matches(isDisplayed()));
            ++i;
        }
    }

    @Test @Ignore
    public void updateChooserCountsAndModelAfterUserSelection() throws InterruptedException {
        Intent sendIntent = createSendTextIntent();
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);

        final IChooserWrapper activity = (IChooserWrapper)
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();
        UsageStatsManager usm = activity.getUsageStatsManager();
        verify(ChooserActivityOverrideData.getInstance().resolverListController, times(1))
                .topK(any(List.class), anyInt());
        assertThat(activity.getIsSelected(), is(false));
        ChooserActivityOverrideData.getInstance().onSafelyStartInternalCallback = targetInfo -> {
            return true;
        };
        ResolveInfo toChoose = resolvedComponentInfos.get(0).getResolveInfoAt(0);
        DisplayResolveInfo testDri =
                activity.createTestDisplayResolveInfo(
                        sendIntent, toChoose, "testLabel", "testInfo", sendIntent);
        onView(withText(toChoose.activityInfo.name))
                .perform(click());
        waitForIdle();
        verify(ChooserActivityOverrideData.getInstance().resolverListController, times(1))
                .updateChooserCounts(Mockito.anyString(), any(UserHandle.class),
                        Mockito.anyString());
        verify(ChooserActivityOverrideData.getInstance().resolverListController, times(1))
                .updateModel(testDri);
        assertThat(activity.getIsSelected(), is(true));
    }

    @Ignore // b/148158199
    @Test
    public void noResultsFromPackageManager() {
        setupResolverControllers(null);
        Intent sendIntent = createSendTextIntent();
        final ChooserActivity activity =
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        final IChooserWrapper wrapper = (IChooserWrapper) activity;

        waitForIdle();
        assertThat(activity.isFinishing(), is(false));

        onView(withId(android.R.id.empty)).check(matches(isDisplayed()));
        onView(withId(com.android.internal.R.id.profile_pager)).check(matches(not(isDisplayed())));
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> wrapper.getAdapter().handlePackagesChanged()
        );
        // backward compatibility. looks like we finish when data is empty after package change
        assertThat(activity.isFinishing(), is(true));
    }

    @Test
    public void autoLaunchSingleResult() throws InterruptedException {
        ResolveInfo[] chosen = new ResolveInfo[1];
        ChooserActivityOverrideData.getInstance().onSafelyStartInternalCallback = targetInfo -> {
            chosen[0] = targetInfo.getResolveInfo();
            return true;
        };

        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(1);
        setupResolverControllers(resolvedComponentInfos);

        Intent sendIntent = createSendTextIntent();
        final ChooserActivity activity =
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        assertThat(chosen[0], is(resolvedComponentInfos.get(0).getResolveInfoAt(0)));
        assertThat(activity.isFinishing(), is(true));
    }

    @Test @Ignore
    public void hasOtherProfileOneOption() {
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTestWithOtherProfile(2, /* userId */ 10);
        List<ResolvedComponentInfo> workResolvedComponentInfos = createResolvedComponentsForTest(4);
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
        markOtherProfileAvailability(/* workAvailable= */ true, /* cloneAvailable= */ false);

        ResolveInfo toChoose = personalResolvedComponentInfos.get(1).getResolveInfoAt(0);
        Intent sendIntent = createSendTextIntent();
        final IChooserWrapper activity = (IChooserWrapper)
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        // The other entry is filtered to the other profile slot
        assertThat(activity.getAdapter().getCount(), is(1));

        ResolveInfo[] chosen = new ResolveInfo[1];
        ChooserActivityOverrideData.getInstance().onSafelyStartInternalCallback = targetInfo -> {
            chosen[0] = targetInfo.getResolveInfo();
            return true;
        };

        // Make a stable copy of the components as the original list may be modified
        List<ResolvedComponentInfo> stableCopy =
                createResolvedComponentsForTestWithOtherProfile(2, /* userId= */ 10);
        waitForIdle();

        onView(first(withText(stableCopy.get(1).getResolveInfoAt(0).activityInfo.name)))
                .perform(click());
        waitForIdle();
        assertThat(chosen[0], is(toChoose));
    }

    @Test @Ignore
    public void hasOtherProfileTwoOptionsAndUserSelectsOne() throws Exception {
        Intent sendIntent = createSendTextIntent();
        List<ResolvedComponentInfo> resolvedComponentInfos =
                createResolvedComponentsForTestWithOtherProfile(3);
        ResolveInfo toChoose = resolvedComponentInfos.get(1).getResolveInfoAt(0);

        setupResolverControllers(resolvedComponentInfos);
        when(ChooserActivityOverrideData.getInstance().resolverListController.getLastChosen())
                .thenReturn(resolvedComponentInfos.get(0).getResolveInfoAt(0));

        final IChooserWrapper activity = (IChooserWrapper)
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        // The other entry is filtered to the other profile slot
        assertThat(activity.getAdapter().getCount(), is(2));

        ResolveInfo[] chosen = new ResolveInfo[1];
        ChooserActivityOverrideData.getInstance().onSafelyStartInternalCallback = targetInfo -> {
            chosen[0] = targetInfo.getResolveInfo();
            return true;
        };

        // Make a stable copy of the components as the original list may be modified
        List<ResolvedComponentInfo> stableCopy =
                createResolvedComponentsForTestWithOtherProfile(3);
        onView(withText(stableCopy.get(1).getResolveInfoAt(0).activityInfo.name))
                .perform(click());
        waitForIdle();
        assertThat(chosen[0], is(toChoose));
    }

    @Test @Ignore
    public void hasLastChosenActivityAndOtherProfile() throws Exception {
        Intent sendIntent = createSendTextIntent();
        List<ResolvedComponentInfo> resolvedComponentInfos =
                createResolvedComponentsForTestWithOtherProfile(3);
        ResolveInfo toChoose = resolvedComponentInfos.get(1).getResolveInfoAt(0);

        setupResolverControllers(resolvedComponentInfos);

        final IChooserWrapper activity = (IChooserWrapper)
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        // The other entry is filtered to the last used slot
        assertThat(activity.getAdapter().getCount(), is(2));

        ResolveInfo[] chosen = new ResolveInfo[1];
        ChooserActivityOverrideData.getInstance().onSafelyStartInternalCallback = targetInfo -> {
            chosen[0] = targetInfo.getResolveInfo();
            return true;
        };

        // Make a stable copy of the components as the original list may be modified
        List<ResolvedComponentInfo> stableCopy =
                createResolvedComponentsForTestWithOtherProfile(3);
        onView(withText(stableCopy.get(1).getResolveInfoAt(0).activityInfo.name))
                .perform(click());
        waitForIdle();
        assertThat(chosen[0], is(toChoose));
    }

    @Test
    @Ignore("b/285309527")
    public void testFilePlusTextSharing_ExcludeText() {
        Uri uri = createTestContentProviderUri(null, "image/png");
        Intent sendIntent = createSendImageIntent(uri);
        ChooserActivityOverrideData.getInstance().imageLoader =
                createImageLoader(uri, createBitmap());
        sendIntent.putExtra(Intent.EXTRA_TEXT, "https://google.com/search?q=google");

        List<ResolvedComponentInfo> resolvedComponentInfos = Arrays.asList(
                ResolverDataProvider.createResolvedComponentInfo(
                        new ComponentName("org.imageviewer", "ImageTarget"),
                        sendIntent, PERSONAL_USER_HANDLE),
                ResolverDataProvider.createResolvedComponentInfo(
                        new ComponentName("org.textviewer", "UriTarget"),
                        new Intent("VIEW_TEXT"), PERSONAL_USER_HANDLE)
        );

        setupResolverControllers(resolvedComponentInfos);

        mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        onView(withId(R.id.include_text_action))
                .check(matches(isDisplayed()))
                .perform(click());
        waitForIdle();

        onView(withId(R.id.content_preview_text)).check(matches(withText("File only")));

        AtomicReference<Intent> launchedIntentRef = new AtomicReference<>();
        ChooserActivityOverrideData.getInstance().onSafelyStartInternalCallback = targetInfo -> {
            launchedIntentRef.set(targetInfo.getTargetIntent());
            return true;
        };

        onView(withText(resolvedComponentInfos.get(0).getResolveInfoAt(0).activityInfo.name))
                .perform(click());
        waitForIdle();
        assertThat(launchedIntentRef.get().hasExtra(Intent.EXTRA_TEXT)).isFalse();
    }

    @Test
    @Ignore("b/285309527")
    public void testFilePlusTextSharing_RemoveAndAddBackText() {
        Uri uri = createTestContentProviderUri("application/pdf", "image/png");
        Intent sendIntent = createSendImageIntent(uri);
        ChooserActivityOverrideData.getInstance().imageLoader =
                createImageLoader(uri, createBitmap());
        final String text = "https://google.com/search?q=google";
        sendIntent.putExtra(Intent.EXTRA_TEXT, text);

        List<ResolvedComponentInfo> resolvedComponentInfos = Arrays.asList(
                ResolverDataProvider.createResolvedComponentInfo(
                        new ComponentName("org.imageviewer", "ImageTarget"),
                        sendIntent, PERSONAL_USER_HANDLE),
                ResolverDataProvider.createResolvedComponentInfo(
                        new ComponentName("org.textviewer", "UriTarget"),
                        new Intent("VIEW_TEXT"), PERSONAL_USER_HANDLE)
        );

        setupResolverControllers(resolvedComponentInfos);

        mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        onView(withId(R.id.include_text_action))
                .check(matches(isDisplayed()))
                .perform(click());
        waitForIdle();
        onView(withId(R.id.content_preview_text)).check(matches(withText("File only")));

        onView(withId(R.id.include_text_action))
                .perform(click());
        waitForIdle();

        onView(withId(R.id.content_preview_text)).check(matches(withText(text)));

        AtomicReference<Intent> launchedIntentRef = new AtomicReference<>();
        ChooserActivityOverrideData.getInstance().onSafelyStartInternalCallback = targetInfo -> {
            launchedIntentRef.set(targetInfo.getTargetIntent());
            return true;
        };

        onView(withText(resolvedComponentInfos.get(0).getResolveInfoAt(0).activityInfo.name))
                .perform(click());
        waitForIdle();
        assertThat(launchedIntentRef.get().getStringExtra(Intent.EXTRA_TEXT)).isEqualTo(text);
    }

    @Test
    @Ignore("b/285309527")
    public void testFilePlusTextSharing_TextExclusionDoesNotAffectAlternativeIntent() {
        Uri uri = createTestContentProviderUri("image/png", null);
        Intent sendIntent = createSendImageIntent(uri);
        ChooserActivityOverrideData.getInstance().imageLoader =
                createImageLoader(uri, createBitmap());
        sendIntent.putExtra(Intent.EXTRA_TEXT, "https://google.com/search?q=google");

        Intent alternativeIntent = createSendTextIntent();
        final String text = "alternative intent";
        alternativeIntent.putExtra(Intent.EXTRA_TEXT, text);

        List<ResolvedComponentInfo> resolvedComponentInfos = Arrays.asList(
                ResolverDataProvider.createResolvedComponentInfo(
                        new ComponentName("org.imageviewer", "ImageTarget"),
                        sendIntent, PERSONAL_USER_HANDLE),
                ResolverDataProvider.createResolvedComponentInfo(
                        new ComponentName("org.textviewer", "UriTarget"),
                        alternativeIntent, PERSONAL_USER_HANDLE)
        );

        setupResolverControllers(resolvedComponentInfos);

        mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        onView(withId(R.id.include_text_action))
                .check(matches(isDisplayed()))
                .perform(click());
        waitForIdle();

        AtomicReference<Intent> launchedIntentRef = new AtomicReference<>();
        ChooserActivityOverrideData.getInstance().onSafelyStartInternalCallback = targetInfo -> {
            launchedIntentRef.set(targetInfo.getTargetIntent());
            return true;
        };

        onView(withText(resolvedComponentInfos.get(1).getResolveInfoAt(0).activityInfo.name))
                .perform(click());
        waitForIdle();
        assertThat(launchedIntentRef.get().getStringExtra(Intent.EXTRA_TEXT)).isEqualTo(text);
    }

    @Test
    @Ignore("b/285309527")
    public void testImagePlusTextSharing_failedThumbnailAndExcludedText_textChanges() {
        Uri uri = createTestContentProviderUri("image/png", null);
        Intent sendIntent = createSendImageIntent(uri);
        ChooserActivityOverrideData.getInstance().imageLoader =
                new TestPreviewImageLoader(Collections.emptyMap());
        sendIntent.putExtra(Intent.EXTRA_TEXT, "https://google.com/search?q=google");

        List<ResolvedComponentInfo> resolvedComponentInfos = Arrays.asList(
                ResolverDataProvider.createResolvedComponentInfo(
                        new ComponentName("org.imageviewer", "ImageTarget"),
                        sendIntent, PERSONAL_USER_HANDLE),
                ResolverDataProvider.createResolvedComponentInfo(
                        new ComponentName("org.textviewer", "UriTarget"),
                        new Intent("VIEW_TEXT"), PERSONAL_USER_HANDLE)
        );

        setupResolverControllers(resolvedComponentInfos);

        mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        onView(withId(R.id.include_text_action))
                .check(matches(isDisplayed()))
                .perform(click());
        waitForIdle();

        onView(withId(R.id.image_view))
                .check(matches(withEffectiveVisibility(ViewMatchers.Visibility.GONE)));
        onView(withId(R.id.content_preview_text))
                .check(matches(allOf(isDisplayed(), withText("Image only"))));
    }

    @Test
    public void copyTextToClipboard() {
        Intent sendIntent = createSendTextIntent();
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);

        final ChooserActivity activity =
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        onView(withId(R.id.copy)).check(matches(isDisplayed()));
        onView(withId(R.id.copy)).perform(click());
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(
                Context.CLIPBOARD_SERVICE);
        ClipData clipData = clipboard.getPrimaryClip();
        assertThat(clipData).isNotNull();
        assertThat(clipData.getItemAt(0).getText()).isEqualTo("testing intent sending");

        ClipDescription clipDescription = clipData.getDescription();
        assertThat("text/plain", is(clipDescription.getMimeType(0)));

        assertEquals(mActivityRule.getActivityResult().getResultCode(), RESULT_OK);
    }

    @Test
    public void copyTextToClipboardLogging() {
        Intent sendIntent = createSendTextIntent();
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);

        ChooserWrapperActivity activity =
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        onView(withId(R.id.copy)).check(matches(isDisplayed()));
        onView(withId(R.id.copy)).perform(click());
        FakeEventLog eventLog = getEventLog(activity);
        assertThat(eventLog.getActionSelected())
                .isEqualTo(new FakeEventLog.ActionSelected(
                        /* targetType = */ EventLog.SELECTION_TYPE_COPY));
    }

    @Test
    @Ignore
    public void testNearbyShareLogging() throws Exception {
        Intent sendIntent = createSendTextIntent();
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);

        mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        onView(withId(com.android.internal.R.id.chooser_nearby_button))
                .check(matches(isDisplayed()));
        onView(withId(com.android.internal.R.id.chooser_nearby_button)).perform(click());

        // TODO(b/211669337): Determine the expected SHARESHEET_DIRECT_LOAD_COMPLETE events.
    }



    @Test @Ignore
    public void testEditImageLogs() {
        Uri uri = createTestContentProviderUri("image/png", null);
        Intent sendIntent = createSendImageIntent(uri);
        ChooserActivityOverrideData.getInstance().imageLoader =
                createImageLoader(uri, createBitmap());

        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);

        mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        onView(withId(com.android.internal.R.id.chooser_edit_button)).check(matches(isDisplayed()));
        onView(withId(com.android.internal.R.id.chooser_edit_button)).perform(click());

        // TODO(b/211669337): Determine the expected SHARESHEET_DIRECT_LOAD_COMPLETE events.
    }


    @Test
    public void oneVisibleImagePreview() {
        Uri uri = createTestContentProviderUri("image/png", null);

        ArrayList<Uri> uris = new ArrayList<>();
        uris.add(uri);

        Intent sendIntent = createSendUriIntentWithPreview(uris);
        ChooserActivityOverrideData.getInstance().imageLoader =
                createImageLoader(uri, createWideBitmap());

        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);
        mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();
        onView(withId(R.id.scrollable_image_preview))
                .check((view, exception) -> {
                    if (exception != null) {
                        throw exception;
                    }
                    RecyclerView recyclerView = (RecyclerView) view;
                    assertThat(recyclerView.getAdapter().getItemCount(), is(1));
                    assertThat(recyclerView.getChildCount(), is(1));
                    View imageView = recyclerView.getChildAt(0);
                    Rect rect = new Rect();
                    boolean isPartiallyVisible = imageView.getGlobalVisibleRect(rect);
                    assertThat(
                            "image preview view is not fully visible",
                            isPartiallyVisible
                                    && rect.width() == imageView.getWidth()
                                    && rect.height() == imageView.getHeight());
                });
    }

    @Test
    public void allThumbnailsFailedToLoad_hidePreview() {
        Uri uri = createTestContentProviderUri("image/jpg", null);

        ArrayList<Uri> uris = new ArrayList<>();
        uris.add(uri);
        uris.add(uri);

        Intent sendIntent = createSendUriIntentWithPreview(uris);
        ChooserActivityOverrideData.getInstance().imageLoader =
                new TestPreviewImageLoader(Collections.emptyMap());

        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);
        mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();
        onView(withId(R.id.scrollable_image_preview))
                .check(matches(withEffectiveVisibility(ViewMatchers.Visibility.GONE)));
    }

    @Test(timeout = 4_000)
    public void testSlowUriMetadata_fallbackToFilePreview() {
        Uri uri = createTestContentProviderUri(
                "application/pdf", "image/png", /*streamTypeTimeout=*/8_000);
        ArrayList<Uri> uris = new ArrayList<>(1);
        uris.add(uri);
        Intent sendIntent = createSendUriIntentWithPreview(uris);
        ChooserActivityOverrideData.getInstance().imageLoader =
                createImageLoader(uri, createBitmap());

        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);
        // The preview type resolution is expected to timeout and default to file preview, otherwise
        // the test should timeout.
        mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        onView(withId(R.id.content_preview_filename)).check(matches(isDisplayed()));
        onView(withId(R.id.content_preview_filename)).check(matches(withText("image.png")));
        onView(withId(R.id.content_preview_file_icon)).check(matches(isDisplayed()));
    }

    @Test(timeout = 4_000)
    public void testSendManyFilesWithSmallMetadataDelayAndOneImage_fallbackToFilePreviewUi() {
        Uri fileUri = createTestContentProviderUri(
                "application/pdf", "application/pdf", /*streamTypeTimeout=*/300);
        Uri imageUri = createTestContentProviderUri("application/pdf", "image/png");
        ArrayList<Uri> uris = new ArrayList<>(50);
        for (int i = 0; i < 49; i++) {
            uris.add(fileUri);
        }
        uris.add(imageUri);
        Intent sendIntent = createSendUriIntentWithPreview(uris);
        ChooserActivityOverrideData.getInstance().imageLoader =
                createImageLoader(imageUri, createBitmap());

        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);
        setupResolverControllers(resolvedComponentInfos);
        // The preview type resolution is expected to timeout and default to file preview, otherwise
        // the test should timeout.
        mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));

        waitForIdle();

        onView(withId(R.id.content_preview_filename)).check(matches(isDisplayed()));
        onView(withId(R.id.content_preview_filename)).check(matches(withText("image.png")));
        onView(withId(R.id.content_preview_file_icon)).check(matches(isDisplayed()));
    }

    @Test
    public void testManyVisibleImagePreview_ScrollableImagePreview() {
        Uri uri = createTestContentProviderUri("image/png", null);

        ArrayList<Uri> uris = new ArrayList<>();
        uris.add(uri);
        uris.add(uri);
        uris.add(uri);
        uris.add(uri);
        uris.add(uri);
        uris.add(uri);
        uris.add(uri);
        uris.add(uri);
        uris.add(uri);
        uris.add(uri);

        Intent sendIntent = createSendUriIntentWithPreview(uris);
        ChooserActivityOverrideData.getInstance().imageLoader =
                createImageLoader(uri, createBitmap());

        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);
        mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();
        onView(withId(R.id.scrollable_image_preview))
                .perform(RecyclerViewActions.scrollToLastPosition())
                .check((view, exception) -> {
                    if (exception != null) {
                        throw exception;
                    }
                    RecyclerView recyclerView = (RecyclerView) view;
                    assertThat(recyclerView.getAdapter().getItemCount(), is(uris.size()));
                });
    }

    @Test(timeout = 4_000)
    public void testPartiallyLoadedMetadata_previewIsShownForTheLoadedPart() {
        Uri imgOneUri = createTestContentProviderUri("image/png", null);
        Uri imgTwoUri = createTestContentProviderUri("image/png", null)
                .buildUpon()
                .path("image-2.png")
                .build();
        Uri docUri = createTestContentProviderUri("application/pdf", "image/png", 8_000);
        ArrayList<Uri> uris = new ArrayList<>(2);
        // two large previews to fill the screen and be presented right away and one
        // document that would be delayed by the URI metadata reading
        uris.add(imgOneUri);
        uris.add(imgTwoUri);
        uris.add(docUri);

        Intent sendIntent = createSendUriIntentWithPreview(uris);
        Map<Uri, Bitmap> bitmaps = new HashMap<>();
        bitmaps.put(imgOneUri, createWideBitmap(Color.RED));
        bitmaps.put(imgTwoUri, createWideBitmap(Color.GREEN));
        bitmaps.put(docUri, createWideBitmap(Color.BLUE));
        ChooserActivityOverrideData.getInstance().imageLoader =
                new TestPreviewImageLoader(bitmaps);

        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);
        setupResolverControllers(resolvedComponentInfos);

        // the preview type is expected to be resolved quickly based on the first provided URI
        // metadata. If, instead, it is dependent on the third URI metadata, the test should either
        // timeout or (more probably due to inner timeout) default to file preview type; anyway the
        // test will fail.
        mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        onView(withId(R.id.scrollable_image_preview))
                .check((view, exception) -> {
                    if (exception != null) {
                        throw exception;
                    }
                    RecyclerView recyclerView = (RecyclerView) view;
                    assertThat(recyclerView.getChildCount()).isAtLeast(1);
                    // the first view is a preview
                    View imageView = recyclerView.getChildAt(0).findViewById(R.id.image);
                    assertThat(imageView).isNotNull();
                })
                .perform(RecyclerViewActions.scrollToLastPosition())
                .check((view, exception) -> {
                    if (exception != null) {
                        throw exception;
                    }
                    RecyclerView recyclerView = (RecyclerView) view;
                    assertThat(recyclerView.getChildCount()).isAtLeast(1);
                    // check that the last view is a loading indicator
                    View loadingIndicator =
                            recyclerView.getChildAt(recyclerView.getChildCount() - 1);
                    assertThat(loadingIndicator).isNotNull();
                });
        waitForIdle();
    }

    @Test
    public void testImageAndTextPreview() {
        final Uri uri = createTestContentProviderUri("image/png", null);
        final String sharedText = "text-" + System.currentTimeMillis();

        ArrayList<Uri> uris = new ArrayList<>();
        uris.add(uri);

        Intent sendIntent = createSendUriIntentWithPreview(uris);
        sendIntent.putExtra(Intent.EXTRA_TEXT, sharedText);
        ChooserActivityOverrideData.getInstance().imageLoader =
                createImageLoader(uri, createBitmap());

        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);
        mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();
        onView(withText(sharedText))
                .check(matches(isDisplayed()));
    }

    @Test
    public void test_shareImageWithRichText_RichTextIsDisplayed() {
        final Uri uri = createTestContentProviderUri("image/png", null);
        final CharSequence sharedText = new SpannableStringBuilder()
                .append(
                        "text-",
                        new StyleSpan(Typeface.BOLD_ITALIC),
                        Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
                .append(
                        Long.toString(System.currentTimeMillis()),
                        new ForegroundColorSpan(Color.RED),
                        Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

        ArrayList<Uri> uris = new ArrayList<>();
        uris.add(uri);

        Intent sendIntent = createSendUriIntentWithPreview(uris);
        sendIntent.putExtra(Intent.EXTRA_TEXT, sharedText);
        ChooserActivityOverrideData.getInstance().imageLoader =
                createImageLoader(uri, createBitmap());

        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);
        mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();
        onView(withText(sharedText.toString()))
                .check(matches(isDisplayed()))
                .check((view, e) -> {
                    if (e != null) {
                        throw e;
                    }
                    assertThat(view).isInstanceOf(TextView.class);
                    CharSequence text = ((TextView) view).getText();
                    assertThat(text).isInstanceOf(Spanned.class);
                    Spanned spanned = (Spanned) text;
                    Object[] spans = spanned.getSpans(0, text.length(), Object.class);
                    assertThat(spans).hasLength(2);
                    assertThat(spanned.getSpans(0, 5, StyleSpan.class)).hasLength(1);
                    assertThat(spanned.getSpans(5, text.length(), ForegroundColorSpan.class))
                            .hasLength(1);
                });
    }

    @Test
    public void testTextPreviewWhenTextIsSharedWithMultipleImages() {
        final Uri uri = createTestContentProviderUri("image/png", null);
        final String sharedText = "text-" + System.currentTimeMillis();

        ArrayList<Uri> uris = new ArrayList<>();
        uris.add(uri);
        uris.add(uri);

        Intent sendIntent = createSendUriIntentWithPreview(uris);
        sendIntent.putExtra(Intent.EXTRA_TEXT, sharedText);
        ChooserActivityOverrideData.getInstance().imageLoader =
                createImageLoader(uri, createBitmap());

        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        when(
                ChooserActivityOverrideData
                        .getInstance()
                        .resolverListController
                        .getResolversForIntentAsUser(
                                Mockito.anyBoolean(),
                                Mockito.anyBoolean(),
                                Mockito.anyBoolean(),
                                Mockito.isA(List.class),
                                Mockito.any(UserHandle.class)))
                .thenReturn(resolvedComponentInfos);
        mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();
        onView(withText(sharedText)).check(matches(isDisplayed()));
    }

    @Test
    public void testOnCreateLogging() {
        Intent sendIntent = createSendTextIntent();
        sendIntent.setType(TEST_MIME_TYPE);

        ChooserWrapperActivity activity =
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, "logger test"));
        waitForIdle();

        FakeEventLog eventLog = getEventLog(activity);
        FakeEventLog.ChooserActivityShown event = eventLog.getChooserActivityShown();
        assertThat(event).isNotNull();
        assertThat(event.isWorkProfile()).isFalse();
        assertThat(event.getTargetMimeType()).isEqualTo(TEST_MIME_TYPE);
    }

    @Test
    public void testOnCreateLoggingFromWorkProfile() {
        Intent sendIntent = createSendTextIntent();
        sendIntent.setType(TEST_MIME_TYPE);
        ChooserActivityOverrideData.getInstance().alternateProfileSetting =
                MetricsEvent.MANAGED_PROFILE;

        ChooserWrapperActivity activity =
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, "logger test"));
        waitForIdle();

        FakeEventLog eventLog = getEventLog(activity);
        FakeEventLog.ChooserActivityShown event = eventLog.getChooserActivityShown();
        assertThat(event).isNotNull();
        assertThat(event.isWorkProfile()).isTrue();
        assertThat(event.getTargetMimeType()).isEqualTo(TEST_MIME_TYPE);
    }

    @Test
    public void testEmptyPreviewLogging() {
        Intent sendIntent = createSendTextIntentWithPreview(null, null);

        ChooserWrapperActivity activity =
                mActivityRule.launchActivity(Intent.createChooser(sendIntent,
                        "empty preview logger test"));
        waitForIdle();

        FakeEventLog eventLog = getEventLog(activity);
        FakeEventLog.ChooserActivityShown event = eventLog.getChooserActivityShown();
        assertThat(event).isNotNull();
        assertThat(event.isWorkProfile()).isFalse();
        assertThat(event.getTargetMimeType()).isNull();
    }

    @Test
    public void testTitlePreviewLogging() {
        Intent sendIntent = createSendTextIntentWithPreview("TestTitle", null);

        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);

        ChooserWrapperActivity activity =
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        FakeEventLog eventLog = getEventLog(activity);
        assertThat(eventLog.getActionShareWithPreview())
                .isEqualTo(new FakeEventLog.ActionShareWithPreview(
                        /* previewType = */ CONTENT_PREVIEW_TEXT));
    }

    @Test
    public void testImagePreviewLogging() {
        Uri uri = createTestContentProviderUri("image/png", null);

        ArrayList<Uri> uris = new ArrayList<>();
        uris.add(uri);

        Intent sendIntent = createSendUriIntentWithPreview(uris);
        ChooserActivityOverrideData.getInstance().imageLoader =
                createImageLoader(uri, createBitmap());

        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);

        ChooserWrapperActivity activity =
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        FakeEventLog eventLog = getEventLog(activity);
        assertThat(eventLog.getActionShareWithPreview())
                .isEqualTo(new FakeEventLog.ActionShareWithPreview(
                        /* previewType = */ CONTENT_PREVIEW_IMAGE));
    }

    @Test
    public void oneVisibleFilePreview() throws InterruptedException {
        Uri uri = Uri.parse("content://com.android.frameworks.coretests/app.pdf");

        ArrayList<Uri> uris = new ArrayList<>();
        uris.add(uri);

        Intent sendIntent = createSendUriIntentWithPreview(uris);

        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);
        mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();
        onView(withId(R.id.content_preview_filename)).check(matches(isDisplayed()));
        onView(withId(R.id.content_preview_filename)).check(matches(withText("app.pdf")));
        onView(withId(R.id.content_preview_file_icon)).check(matches(isDisplayed()));
    }


    @Test
    public void moreThanOneVisibleFilePreview() throws InterruptedException {
        Uri uri = Uri.parse("content://com.android.frameworks.coretests/app.pdf");

        ArrayList<Uri> uris = new ArrayList<>();
        uris.add(uri);
        uris.add(uri);
        uris.add(uri);

        Intent sendIntent = createSendUriIntentWithPreview(uris);

        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);
        mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();
        onView(withId(R.id.content_preview_filename)).check(matches(isDisplayed()));
        onView(withId(R.id.content_preview_filename)).check(matches(withText("app.pdf")));
        onView(withId(R.id.content_preview_more_files)).check(matches(isDisplayed()));
        onView(withId(R.id.content_preview_more_files)).check(matches(withText("+ 2 more files")));
        onView(withId(R.id.content_preview_file_icon)).check(matches(isDisplayed()));
    }

    @Test
    public void contentProviderThrowSecurityException() throws InterruptedException {
        Uri uri = Uri.parse("content://com.android.frameworks.coretests/app.pdf");

        ArrayList<Uri> uris = new ArrayList<>();
        uris.add(uri);

        Intent sendIntent = createSendUriIntentWithPreview(uris);

        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);
        setupResolverControllers(resolvedComponentInfos);

        ChooserActivityOverrideData.getInstance().resolverForceException = true;

        mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();
        onView(withId(R.id.content_preview_filename)).check(matches(isDisplayed()));
        onView(withId(R.id.content_preview_filename)).check(matches(withText("app.pdf")));
        onView(withId(R.id.content_preview_file_icon)).check(matches(isDisplayed()));
    }

    @Test
    public void contentProviderReturnsNoColumns() throws InterruptedException {
        Uri uri = Uri.parse("content://com.android.frameworks.coretests/app.pdf");

        ArrayList<Uri> uris = new ArrayList<>();
        uris.add(uri);
        uris.add(uri);

        Intent sendIntent = createSendUriIntentWithPreview(uris);

        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);
        setupResolverControllers(resolvedComponentInfos);

        Cursor cursor = mock(Cursor.class);
        when(cursor.getCount()).thenReturn(1);
        Mockito.doNothing().when(cursor).close();
        when(cursor.moveToFirst()).thenReturn(true);
        when(cursor.getColumnIndex(Mockito.anyString())).thenReturn(-1);

        ChooserActivityOverrideData.getInstance().resolverCursor = cursor;

        mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();
        onView(withId(R.id.content_preview_filename)).check(matches(isDisplayed()));
        onView(withId(R.id.content_preview_filename)).check(matches(withText("app.pdf")));
        onView(withId(R.id.content_preview_more_files)).check(matches(isDisplayed()));
        onView(withId(R.id.content_preview_more_files)).check(matches(withText("+ 1 more file")));
        onView(withId(R.id.content_preview_file_icon)).check(matches(isDisplayed()));
    }

    @Test
    public void testGetBaseScore() {
        final float testBaseScore = 0.89f;

        Intent sendIntent = createSendTextIntent();
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);

        when(
                ChooserActivityOverrideData
                        .getInstance()
                        .resolverListController
                        .getScore(Mockito.isA(DisplayResolveInfo.class)))
                .thenReturn(testBaseScore);

        final IChooserWrapper activity = (IChooserWrapper)
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        final DisplayResolveInfo testDri =
                activity.createTestDisplayResolveInfo(
                        sendIntent,
                        ResolverDataProvider.createResolveInfo(3, 0, PERSONAL_USER_HANDLE),
                        "testLabel",
                        "testInfo",
                        sendIntent);
        final ChooserListAdapter adapter = activity.getAdapter();

        assertThat(adapter.getBaseScore(null, 0), is(CALLER_TARGET_SCORE_BOOST));
        assertThat(adapter.getBaseScore(testDri, TARGET_TYPE_DEFAULT), is(testBaseScore));
        assertThat(adapter.getBaseScore(testDri, TARGET_TYPE_CHOOSER_TARGET), is(testBaseScore));
        assertThat(adapter.getBaseScore(testDri, TARGET_TYPE_SHORTCUTS_FROM_PREDICTION_SERVICE),
                is(testBaseScore * SHORTCUT_TARGET_SCORE_BOOST));
        assertThat(adapter.getBaseScore(testDri, TARGET_TYPE_SHORTCUTS_FROM_SHORTCUT_MANAGER),
                is(testBaseScore * SHORTCUT_TARGET_SCORE_BOOST));
    }

    // This test is too long and too slow and should not be taken as an example for future tests.
    @Test
    public void testDirectTargetSelectionLogging() {
        Intent sendIntent = createSendTextIntent();
        // We need app targets for direct targets to get displayed
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);
        setupResolverControllers(resolvedComponentInfos);

        // create test shortcut loader factory, remember loaders and their callbacks
        SparseArray<Pair<ShortcutLoader, Consumer<ShortcutLoader.Result>>> shortcutLoaders =
                createShortcutLoaderFactory();

        // Start activity
        ChooserWrapperActivity activity =
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        // verify that ShortcutLoader was queried
        ArgumentCaptor<DisplayResolveInfo[]> appTargets =
                ArgumentCaptor.forClass(DisplayResolveInfo[].class);
        verify(shortcutLoaders.get(0).first, times(1)).updateAppTargets(appTargets.capture());

        // send shortcuts
        assertThat(
                "Wrong number of app targets",
                appTargets.getValue().length,
                is(resolvedComponentInfos.size()));
        List<ChooserTarget> serviceTargets = createDirectShareTargets(1, "");
        ShortcutLoader.Result result = new ShortcutLoader.Result(
                true,
                appTargets.getValue(),
                new ShortcutLoader.ShortcutResultInfo[] {
                        new ShortcutLoader.ShortcutResultInfo(
                                appTargets.getValue()[0],
                                serviceTargets
                        )
                },
                new HashMap<>(),
                new HashMap<>()
        );
        activity.getMainExecutor().execute(() -> shortcutLoaders.get(0).second.accept(result));
        waitForIdle();

        final ChooserListAdapter activeAdapter = activity.getAdapter();
        assertThat(
                "Chooser should have 3 targets (2 apps, 1 direct)",
                activeAdapter.getCount(),
                is(3));
        assertThat(
                "Chooser should have exactly one selectable direct target",
                activeAdapter.getSelectableServiceTargetCount(),
                is(1));
        assertThat(
                "The resolver info must match the resolver info used to create the target",
                activeAdapter.getItem(0).getResolveInfo(),
                is(resolvedComponentInfos.get(0).getResolveInfoAt(0)));

        // Click on the direct target
        String name = serviceTargets.get(0).getTitle().toString();
        onView(withText(name))
                .perform(click());
        waitForIdle();

        FakeEventLog eventLog = getEventLog(activity);
        assertThat(eventLog.getShareTargetSelected()).hasSize(1);
        FakeEventLog.ShareTargetSelected call = eventLog.getShareTargetSelected().get(0);
        assertThat(call.getTargetType()).isEqualTo(EventLog.SELECTION_TYPE_SERVICE);
        assertThat(call.getDirectTargetAlsoRanked()).isEqualTo(-1);
        var hashResult = call.getDirectTargetHashed();
        var hash = hashResult == null ? "" : hashResult.hashedString;
        assertWithMessage("Hash is not predictable but must be obfuscated")
                .that(hash).isNotEqualTo(name);
    }

    // This test is too long and too slow and should not be taken as an example for future tests.
    @Test
    public void testDirectTargetLoggingWithRankedAppTarget() {
        Intent sendIntent = createSendTextIntent();
        // We need app targets for direct targets to get displayed
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);
        setupResolverControllers(resolvedComponentInfos);

        // create test shortcut loader factory, remember loaders and their callbacks
        SparseArray<Pair<ShortcutLoader, Consumer<ShortcutLoader.Result>>> shortcutLoaders =
                createShortcutLoaderFactory();

        // Start activity
        ChooserWrapperActivity activity =
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        // verify that ShortcutLoader was queried
        ArgumentCaptor<DisplayResolveInfo[]> appTargets =
                ArgumentCaptor.forClass(DisplayResolveInfo[].class);
        verify(shortcutLoaders.get(0).first, times(1)).updateAppTargets(appTargets.capture());

        // send shortcuts
        assertThat(
                "Wrong number of app targets",
                appTargets.getValue().length,
                is(resolvedComponentInfos.size()));
        List<ChooserTarget> serviceTargets = createDirectShareTargets(
                1,
                resolvedComponentInfos.get(0).getResolveInfoAt(0).activityInfo.packageName);
        ShortcutLoader.Result result = new ShortcutLoader.Result(
                true,
                appTargets.getValue(),
                new ShortcutLoader.ShortcutResultInfo[] {
                        new ShortcutLoader.ShortcutResultInfo(
                                appTargets.getValue()[0],
                                serviceTargets
                        )
                },
                new HashMap<>(),
                new HashMap<>()
        );
        activity.getMainExecutor().execute(() -> shortcutLoaders.get(0).second.accept(result));
        waitForIdle();

        final ChooserListAdapter activeAdapter = activity.getAdapter();
        assertThat(
                "Chooser should have 3 targets (2 apps, 1 direct)",
                activeAdapter.getCount(),
                is(3));
        assertThat(
                "Chooser should have exactly one selectable direct target",
                activeAdapter.getSelectableServiceTargetCount(),
                is(1));
        assertThat(
                "The resolver info must match the resolver info used to create the target",
                activeAdapter.getItem(0).getResolveInfo(),
                is(resolvedComponentInfos.get(0).getResolveInfoAt(0)));

        // Click on the direct target
        String name = serviceTargets.get(0).getTitle().toString();
        onView(withText(name))
                .perform(click());
        waitForIdle();

        FakeEventLog eventLog = getEventLog(activity);
        assertThat(eventLog.getShareTargetSelected()).hasSize(1);
        FakeEventLog.ShareTargetSelected call = eventLog.getShareTargetSelected().get(0);

        assertThat(call.getTargetType()).isEqualTo(EventLog.SELECTION_TYPE_SERVICE);
        assertThat(call.getDirectTargetAlsoRanked()).isEqualTo(0);
    }

    @Test
    public void testShortcutTargetWithApplyAppLimits() {
        // Set up resources
        Resources resources = Mockito.spy(
                InstrumentationRegistry.getInstrumentation().getContext().getResources());
        ChooserActivityOverrideData.getInstance().resources = resources;
        doReturn(1).when(resources).getInteger(R.integer.config_maxShortcutTargetsPerApp);
        Intent sendIntent = createSendTextIntent();
        // We need app targets for direct targets to get displayed
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);
        setupResolverControllers(resolvedComponentInfos);

        // create test shortcut loader factory, remember loaders and their callbacks
        SparseArray<Pair<ShortcutLoader, Consumer<ShortcutLoader.Result>>> shortcutLoaders =
                createShortcutLoaderFactory();

        // Start activity
        final IChooserWrapper activity = (IChooserWrapper) mActivityRule
                .launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        // verify that ShortcutLoader was queried
        ArgumentCaptor<DisplayResolveInfo[]> appTargets =
                ArgumentCaptor.forClass(DisplayResolveInfo[].class);
        verify(shortcutLoaders.get(0).first, times(1)).updateAppTargets(appTargets.capture());

        // send shortcuts
        assertThat(
                "Wrong number of app targets",
                appTargets.getValue().length,
                is(resolvedComponentInfos.size()));
        List<ChooserTarget> serviceTargets = createDirectShareTargets(
                2,
                resolvedComponentInfos.get(0).getResolveInfoAt(0).activityInfo.packageName);
        ShortcutLoader.Result result = new ShortcutLoader.Result(
                true,
                appTargets.getValue(),
                new ShortcutLoader.ShortcutResultInfo[] {
                        new ShortcutLoader.ShortcutResultInfo(
                                appTargets.getValue()[0],
                                serviceTargets
                        )
                },
                new HashMap<>(),
                new HashMap<>()
        );
        activity.getMainExecutor().execute(() -> shortcutLoaders.get(0).second.accept(result));
        waitForIdle();

        final ChooserListAdapter activeAdapter = activity.getAdapter();
        assertThat(
                "Chooser should have 3 targets (2 apps, 1 direct)",
                activeAdapter.getCount(),
                is(3));
        assertThat(
                "Chooser should have exactly one selectable direct target",
                activeAdapter.getSelectableServiceTargetCount(),
                is(1));
        assertThat(
                "The resolver info must match the resolver info used to create the target",
                activeAdapter.getItem(0).getResolveInfo(),
                is(resolvedComponentInfos.get(0).getResolveInfoAt(0)));
        assertThat(
                "The display label must match",
                activeAdapter.getItem(0).getDisplayLabel(),
                is("testTitle0"));
    }

    @Test
    public void testShortcutTargetWithoutApplyAppLimits() {
        setDeviceConfigProperty(
                SystemUiDeviceConfigFlags.APPLY_SHARING_APP_LIMITS_IN_SYSUI,
                Boolean.toString(false));
        // Set up resources
        Resources resources = Mockito.spy(
                InstrumentationRegistry.getInstrumentation().getContext().getResources());
        ChooserActivityOverrideData.getInstance().resources = resources;
        doReturn(1).when(resources).getInteger(R.integer.config_maxShortcutTargetsPerApp);
        Intent sendIntent = createSendTextIntent();
        // We need app targets for direct targets to get displayed
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);
        setupResolverControllers(resolvedComponentInfos);

        // create test shortcut loader factory, remember loaders and their callbacks
        SparseArray<Pair<ShortcutLoader, Consumer<ShortcutLoader.Result>>> shortcutLoaders =
                createShortcutLoaderFactory();

        // Start activity
        final IChooserWrapper activity = (IChooserWrapper)
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        // verify that ShortcutLoader was queried
        ArgumentCaptor<DisplayResolveInfo[]> appTargets =
                ArgumentCaptor.forClass(DisplayResolveInfo[].class);
        verify(shortcutLoaders.get(0).first, times(1)).updateAppTargets(appTargets.capture());

        // send shortcuts
        assertThat(
                "Wrong number of app targets",
                appTargets.getValue().length,
                is(resolvedComponentInfos.size()));
        List<ChooserTarget> serviceTargets = createDirectShareTargets(
                2,
                resolvedComponentInfos.get(0).getResolveInfoAt(0).activityInfo.packageName);
        ShortcutLoader.Result result = new ShortcutLoader.Result(
                true,
                appTargets.getValue(),
                new ShortcutLoader.ShortcutResultInfo[] {
                        new ShortcutLoader.ShortcutResultInfo(
                                appTargets.getValue()[0],
                                serviceTargets
                        )
                },
                new HashMap<>(),
                new HashMap<>()
        );
        activity.getMainExecutor().execute(() -> shortcutLoaders.get(0).second.accept(result));
        waitForIdle();

        final ChooserListAdapter activeAdapter = activity.getAdapter();
        assertThat(
                "Chooser should have 4 targets (2 apps, 2 direct)",
                activeAdapter.getCount(),
                is(4));
        assertThat(
                "Chooser should have exactly two selectable direct target",
                activeAdapter.getSelectableServiceTargetCount(),
                is(2));
        assertThat(
                "The resolver info must match the resolver info used to create the target",
                activeAdapter.getItem(0).getResolveInfo(),
                is(resolvedComponentInfos.get(0).getResolveInfoAt(0)));
        assertThat(
                "The display label must match",
                activeAdapter.getItem(0).getDisplayLabel(),
                is("testTitle0"));
        assertThat(
                "The display label must match",
                activeAdapter.getItem(1).getDisplayLabel(),
                is("testTitle1"));
    }

    @Test
    public void testLaunchWithCallerProvidedTarget() {
        setDeviceConfigProperty(
                SystemUiDeviceConfigFlags.APPLY_SHARING_APP_LIMITS_IN_SYSUI,
                Boolean.toString(false));
        // Set up resources
        Resources resources = Mockito.spy(
                InstrumentationRegistry.getInstrumentation().getContext().getResources());
        ChooserActivityOverrideData.getInstance().resources = resources;
        doReturn(1).when(resources).getInteger(R.integer.config_maxShortcutTargetsPerApp);

        // We need app targets for direct targets to get displayed
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);
        setupResolverControllers(resolvedComponentInfos, resolvedComponentInfos);
        markOtherProfileAvailability(/* workAvailable= */ true, /* cloneAvailable= */ false);

        // set caller-provided target
        Intent chooserIntent = Intent.createChooser(createSendTextIntent(), null);
        String callerTargetLabel = "Caller Target";
        ChooserTarget[] targets = new ChooserTarget[] {
                new ChooserTarget(
                        callerTargetLabel,
                        Icon.createWithBitmap(createBitmap()),
                        0.1f,
                        resolvedComponentInfos.get(0).name,
                        new Bundle())
        };
        chooserIntent.putExtra(Intent.EXTRA_CHOOSER_TARGETS, targets);

        // create test shortcut loader factory, remember loaders and their callbacks
        SparseArray<Pair<ShortcutLoader, Consumer<ShortcutLoader.Result>>> shortcutLoaders =
                createShortcutLoaderFactory();

        // Start activity
        final IChooserWrapper activity = (IChooserWrapper)
                mActivityRule.launchActivity(chooserIntent);
        waitForIdle();

        // verify that ShortcutLoader was queried
        ArgumentCaptor<DisplayResolveInfo[]> appTargets =
                ArgumentCaptor.forClass(DisplayResolveInfo[].class);
        verify(shortcutLoaders.get(0).first, times(1)).updateAppTargets(appTargets.capture());

        // send shortcuts
        assertThat(
                "Wrong number of app targets",
                appTargets.getValue().length,
                is(resolvedComponentInfos.size()));
        ShortcutLoader.Result result = new ShortcutLoader.Result(
                true,
                appTargets.getValue(),
                new ShortcutLoader.ShortcutResultInfo[0],
                new HashMap<>(),
                new HashMap<>());
        activity.getMainExecutor().execute(() -> shortcutLoaders.get(0).second.accept(result));
        waitForIdle();

        final ChooserListAdapter activeAdapter = activity.getAdapter();
        assertThat(
                "Chooser should have 3 targets (2 apps, 1 direct)",
                activeAdapter.getCount(),
                is(3));
        assertThat(
                "Chooser should have exactly two selectable direct target",
                activeAdapter.getSelectableServiceTargetCount(),
                is(1));
        assertThat(
                "The display label must match",
                activeAdapter.getItem(0).getDisplayLabel(),
                is(callerTargetLabel));

        // Switch to work profile and ensure that the target *doesn't* show up there.
        onView(withText(R.string.resolver_work_tab)).perform(click());
        waitForIdle();

        for (int i = 0; i < activity.getWorkListAdapter().getCount(); i++) {
            assertThat(
                    "Chooser target should not show up in opposite profile",
                    activity.getWorkListAdapter().getItem(i).getDisplayLabel(),
                    not(callerTargetLabel));
        }
    }

    @Test
    public void testLaunchWithCustomAction() throws InterruptedException {
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);
        setupResolverControllers(resolvedComponentInfos);

        Context testContext = InstrumentationRegistry.getInstrumentation().getContext();
        final String customActionLabel = "Custom Action";
        final String testAction = "test-broadcast-receiver-action";
        Intent chooserIntent = Intent.createChooser(createSendTextIntent(), null);
        chooserIntent.putExtra(
                Intent.EXTRA_CHOOSER_CUSTOM_ACTIONS,
                new ChooserAction[] {
                        new ChooserAction.Builder(
                                Icon.createWithResource("", Resources.ID_NULL),
                                customActionLabel,
                                PendingIntent.getBroadcast(
                                        testContext,
                                        123,
                                        new Intent(testAction),
                                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT))
                                .build()
                });
        // Start activity
        mActivityRule.launchActivity(chooserIntent);
        waitForIdle();

        final CountDownLatch broadcastInvoked = new CountDownLatch(1);
        BroadcastReceiver testReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                broadcastInvoked.countDown();
            }
        };
        testContext.registerReceiver(testReceiver, new IntentFilter(testAction),
                Context.RECEIVER_EXPORTED);

        try {
            onView(withText(customActionLabel)).perform(click());
            assertTrue("Timeout waiting for broadcast",
                    broadcastInvoked.await(5000, TimeUnit.MILLISECONDS));
        } finally {
            testContext.unregisterReceiver(testReceiver);
        }
    }

    @Test
    public void testLaunchWithShareModification() throws InterruptedException {
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);
        setupResolverControllers(resolvedComponentInfos);

        Context testContext = InstrumentationRegistry.getInstrumentation().getContext();
        final String modifyShareAction = "test-broadcast-receiver-action";
        Intent chooserIntent = Intent.createChooser(createSendTextIntent(), null);
        String label = "modify share";
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                testContext,
                123,
                new Intent(modifyShareAction),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT);
        ChooserAction action = new ChooserAction.Builder(Icon.createWithBitmap(
                createBitmap()), label, pendingIntent).build();
        chooserIntent.putExtra(
                Intent.EXTRA_CHOOSER_MODIFY_SHARE_ACTION,
                action);
        // Start activity
        mActivityRule.launchActivity(chooserIntent);
        waitForIdle();

        final CountDownLatch broadcastInvoked = new CountDownLatch(1);
        BroadcastReceiver testReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                broadcastInvoked.countDown();
            }
        };
        testContext.registerReceiver(testReceiver, new IntentFilter(modifyShareAction),
                Context.RECEIVER_EXPORTED);

        try {
            onView(withText(label)).perform(click());
            assertTrue("Timeout waiting for broadcast",
                    broadcastInvoked.await(5000, TimeUnit.MILLISECONDS));

        } finally {
            testContext.unregisterReceiver(testReceiver);
        }
    }

    @Test
    public void testUpdateMaxTargetsPerRow_columnCountIsUpdated() throws InterruptedException {
        updateMaxTargetsPerRowResource(/* targetsPerRow= */ 4);
        givenAppTargets(/* appCount= */ 16);
        Intent sendIntent = createSendTextIntent();
        final ChooserActivity activity =
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));

        updateMaxTargetsPerRowResource(/* targetsPerRow= */ 6);
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(() -> activity.onConfigurationChanged(
                        InstrumentationRegistry.getInstrumentation()
                                .getContext().getResources().getConfiguration()));

        waitForIdle();
        onView(withId(com.android.internal.R.id.resolver_list))
                .check(matches(withGridColumnCount(6)));
    }

    // This test is too long and too slow and should not be taken as an example for future tests.
    @Test @Ignore
    public void testDirectTargetLoggingWithAppTargetNotRankedPortrait()
            throws InterruptedException {
        testDirectTargetLoggingWithAppTargetNotRanked(Configuration.ORIENTATION_PORTRAIT, 4);
    }

    @Test @Ignore
    public void testDirectTargetLoggingWithAppTargetNotRankedLandscape()
            throws InterruptedException {
        testDirectTargetLoggingWithAppTargetNotRanked(Configuration.ORIENTATION_LANDSCAPE, 8);
    }

    private void testDirectTargetLoggingWithAppTargetNotRanked(
            int orientation, int appTargetsExpected) {
        Configuration configuration =
                new Configuration(InstrumentationRegistry.getInstrumentation().getContext()
                        .getResources().getConfiguration());
        configuration.orientation = orientation;

        Resources resources = Mockito.spy(
                InstrumentationRegistry.getInstrumentation().getContext().getResources());
        ChooserActivityOverrideData.getInstance().resources = resources;
        doReturn(configuration).when(resources).getConfiguration();

        Intent sendIntent = createSendTextIntent();
        // We need app targets for direct targets to get displayed
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(15);
        setupResolverControllers(resolvedComponentInfos);

        // Create direct share target
        List<ChooserTarget> serviceTargets = createDirectShareTargets(1,
                resolvedComponentInfos.get(14).getResolveInfoAt(0).activityInfo.packageName);
        ResolveInfo ri = ResolverDataProvider.createResolveInfo(16, 0, PERSONAL_USER_HANDLE);

        // Start activity
        ChooserWrapperActivity activity =
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        // Insert the direct share target
        Map<ChooserTarget, ShortcutInfo> directShareToShortcutInfos = new HashMap<>();
        directShareToShortcutInfos.put(serviceTargets.get(0), null);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> activity.getAdapter().addServiceResults(
                        activity.createTestDisplayResolveInfo(sendIntent,
                                ri,
                                "testLabel",
                                "testInfo",
                                sendIntent),
                        serviceTargets,
                        TARGET_TYPE_CHOOSER_TARGET,
                        directShareToShortcutInfos,
                        /* directShareToAppTargets */ null)
        );

        assertThat(
                String.format("Chooser should have %d targets (%d apps, 1 direct, 15 A-Z)",
                        appTargetsExpected + 16, appTargetsExpected),
                activity.getAdapter().getCount(), is(appTargetsExpected + 16));
        assertThat("Chooser should have exactly one selectable direct target",
                activity.getAdapter().getSelectableServiceTargetCount(), is(1));
        assertThat("The resolver info must match the resolver info used to create the target",
                activity.getAdapter().getItem(0).getResolveInfo(), is(ri));

        // Click on the direct target
        String name = serviceTargets.get(0).getTitle().toString();
        onView(withText(name))
                .perform(click());
        waitForIdle();

        FakeEventLog eventLog = getEventLog(activity);
        var invocations = eventLog.getShareTargetSelected();
        assertWithMessage("Only one ShareTargetSelected event logged")
                .that(invocations).hasSize(1);
        FakeEventLog.ShareTargetSelected call = invocations.get(0);
        assertWithMessage("targetType should be SELECTION_TYPE_SERVICE")
                .that(call.getTargetType()).isEqualTo(EventLog.SELECTION_TYPE_SERVICE);
        assertWithMessage(
                "The packages shouldn't match for app target and direct target")
                .that(call.getDirectTargetAlsoRanked()).isEqualTo(-1);
    }

    @Test
    public void testWorkTab_displayedWhenWorkProfileUserAvailable() {
        Intent sendIntent = createSendTextIntent();
        sendIntent.setType(TEST_MIME_TYPE);
        markOtherProfileAvailability(/* workAvailable= */ true, /* cloneAvailable= */ false);

        mActivityRule.launchActivity(Intent.createChooser(sendIntent, "work tab test"));
        waitForIdle();

        onView(withId(android.R.id.tabs)).check(matches(isDisplayed()));
    }

    @Test
    public void testWorkTab_hiddenWhenWorkProfileUserNotAvailable() {
        Intent sendIntent = createSendTextIntent();
        sendIntent.setType(TEST_MIME_TYPE);

        mActivityRule.launchActivity(Intent.createChooser(sendIntent, "work tab test"));
        waitForIdle();

        onView(withId(android.R.id.tabs)).check(matches(not(isDisplayed())));
    }

    @Test
    public void testWorkTab_eachTabUsesExpectedAdapter() {
        int personalProfileTargets = 3;
        int otherProfileTargets = 1;
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTestWithOtherProfile(
                        personalProfileTargets + otherProfileTargets, /* userID */ 10);
        int workProfileTargets = 4;
        List<ResolvedComponentInfo> workResolvedComponentInfos = createResolvedComponentsForTest(
                workProfileTargets);
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
        Intent sendIntent = createSendTextIntent();
        sendIntent.setType(TEST_MIME_TYPE);
        markOtherProfileAvailability(/* workAvailable= */ true, /* cloneAvailable= */ false);

        final IChooserWrapper activity = (IChooserWrapper)
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, "work tab test"));
        waitForIdle();

        assertThat(activity.getCurrentUserHandle().getIdentifier(), is(0));
        onView(withText(R.string.resolver_work_tab)).perform(click());
        assertThat(activity.getCurrentUserHandle().getIdentifier(), is(10));
        assertThat(activity.getPersonalListAdapter().getCount(), is(personalProfileTargets));
        assertThat(activity.getWorkListAdapter().getCount(), is(workProfileTargets));
    }

    @Test
    public void testWorkTab_workProfileHasExpectedNumberOfTargets() throws InterruptedException {
        markOtherProfileAvailability(/* workAvailable= */ true, /* cloneAvailable= */ false);
        int workProfileTargets = 4;
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTestWithOtherProfile(3, /* userId */ 10);
        List<ResolvedComponentInfo> workResolvedComponentInfos =
                createResolvedComponentsForTest(workProfileTargets);
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
        Intent sendIntent = createSendTextIntent();
        sendIntent.setType(TEST_MIME_TYPE);

        final IChooserWrapper activity = (IChooserWrapper)
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, "work tab test"));
        waitForIdle();
        onView(withText(R.string.resolver_work_tab)).perform(click());
        waitForIdle();

        assertThat(activity.getWorkListAdapter().getCount(), is(workProfileTargets));
    }

    @Test @Ignore
    public void testWorkTab_selectingWorkTabAppOpensAppInWorkProfile() {
        markOtherProfileAvailability(/* workAvailable= */ true, /* cloneAvailable= */ false);
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTestWithOtherProfile(3, /* userId */ 10);
        int workProfileTargets = 4;
        List<ResolvedComponentInfo> workResolvedComponentInfos =
                createResolvedComponentsForTest(workProfileTargets);
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
        Intent sendIntent = createSendTextIntent();
        sendIntent.setType(TEST_MIME_TYPE);
        ResolveInfo[] chosen = new ResolveInfo[1];
        ChooserActivityOverrideData.getInstance().onSafelyStartInternalCallback = targetInfo -> {
            chosen[0] = targetInfo.getResolveInfo();
            return true;
        };

        mActivityRule.launchActivity(Intent.createChooser(sendIntent, "work tab test"));
        waitForIdle();
        onView(withText(R.string.resolver_work_tab)).perform(click());
        waitForIdle();

        onView(first(allOf(
                withText(workResolvedComponentInfos.get(0)
                        .getResolveInfoAt(0).activityInfo.applicationInfo.name),
                isDisplayed())))
                .perform(click());
        waitForIdle();
        assertThat(chosen[0], is(workResolvedComponentInfos.get(0).getResolveInfoAt(0)));
    }

    @Test
    public void testWorkTab_crossProfileIntentsDisabled_personalToWork_emptyStateShown() {
        markOtherProfileAvailability(/* workAvailable= */ true, /* cloneAvailable= */ false);
        int workProfileTargets = 4;
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTestWithOtherProfile(3, /* userId */ 10);
        List<ResolvedComponentInfo> workResolvedComponentInfos =
                createResolvedComponentsForTest(workProfileTargets);
        ChooserActivityOverrideData.getInstance().hasCrossProfileIntents = false;
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
        Intent sendIntent = createSendTextIntent();
        sendIntent.setType(TEST_MIME_TYPE);

        mActivityRule.launchActivity(Intent.createChooser(sendIntent, "work tab test"));
        waitForIdle();
        onView(withText(R.string.resolver_work_tab)).perform(click());
        waitForIdle();
        onView(withId(com.android.internal.R.id.contentPanel))
                .perform(swipeUp());

        onView(withText(R.string.resolver_cross_profile_blocked))
                .check(matches(isDisplayed()));
    }

    @Test
    public void testWorkTab_workProfileDisabled_emptyStateShown() {
        markOtherProfileAvailability(/* workAvailable= */ true, /* cloneAvailable= */ false);
        int workProfileTargets = 4;
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTestWithOtherProfile(3, /* userId */ 10);
        List<ResolvedComponentInfo> workResolvedComponentInfos =
                createResolvedComponentsForTest(workProfileTargets);
        ChooserActivityOverrideData.getInstance().isQuietModeEnabled = true;
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
        Intent sendIntent = createSendTextIntent();
        sendIntent.setType(TEST_MIME_TYPE);

        mActivityRule.launchActivity(Intent.createChooser(sendIntent, "work tab test"));
        waitForIdle();
        onView(withId(com.android.internal.R.id.contentPanel))
                .perform(swipeUp());
        onView(withText(R.string.resolver_work_tab)).perform(click());
        waitForIdle();

        onView(withText(R.string.resolver_turn_on_work_apps))
                .check(matches(isDisplayed()));
    }

    @Test
    public void testWorkTab_noWorkAppsAvailable_emptyStateShown() {
        markOtherProfileAvailability(/* workAvailable= */ true, /* cloneAvailable= */ false);
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTest(3);
        List<ResolvedComponentInfo> workResolvedComponentInfos =
                createResolvedComponentsForTest(0);
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
        Intent sendIntent = createSendTextIntent();
        sendIntent.setType(TEST_MIME_TYPE);

        mActivityRule.launchActivity(Intent.createChooser(sendIntent, "work tab test"));
        waitForIdle();
        onView(withId(com.android.internal.R.id.contentPanel))
                .perform(swipeUp());
        onView(withText(R.string.resolver_work_tab)).perform(click());
        waitForIdle();

        onView(withText(R.string.resolver_no_work_apps_available))
                .check(matches(isDisplayed()));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SCROLLABLE_PREVIEW)
    public void testWorkTab_previewIsScrollable() {
        markOtherProfileAvailability(/* workAvailable= */ true, /* cloneAvailable= */ false);
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTest(300);
        List<ResolvedComponentInfo> workResolvedComponentInfos =
                createResolvedComponentsForTest(3);
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);

        Uri uri = createTestContentProviderUri("image/png", null);

        ArrayList<Uri> uris = new ArrayList<>();
        uris.add(uri);

        Intent sendIntent = createSendUriIntentWithPreview(uris);
        ChooserActivityOverrideData.getInstance().imageLoader =
                createImageLoader(uri, createWideBitmap());

        mActivityRule.launchActivity(Intent.createChooser(sendIntent, "Scrollable preview test"));
        waitForIdle();

        onView(withId(com.android.intentresolver.R.id.scrollable_image_preview))
                .check(matches(isDisplayed()));

        onView(withId(com.android.internal.R.id.contentPanel)).perform(swipeUp());
        waitForIdle();

        onView(withId(com.android.intentresolver.R.id.chooser_headline_row_container))
                .check(matches(isCompletelyDisplayed()));
        onView(withId(com.android.intentresolver.R.id.headline))
                .check(matches(isDisplayed()));
        onView(withId(com.android.intentresolver.R.id.scrollable_image_preview))
                .check(matches(not(isDisplayed())));
    }

    @Ignore // b/220067877
    @Test
    public void testWorkTab_xProfileOff_noAppsAvailable_workOff_xProfileOffEmptyStateShown() {
        markOtherProfileAvailability(/* workAvailable= */ true, /* cloneAvailable= */ false);
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTest(3);
        List<ResolvedComponentInfo> workResolvedComponentInfos =
                createResolvedComponentsForTest(0);
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
        ChooserActivityOverrideData.getInstance().isQuietModeEnabled = true;
        ChooserActivityOverrideData.getInstance().hasCrossProfileIntents = false;
        Intent sendIntent = createSendTextIntent();
        sendIntent.setType(TEST_MIME_TYPE);

        mActivityRule.launchActivity(Intent.createChooser(sendIntent, "work tab test"));
        waitForIdle();
        onView(withId(com.android.internal.R.id.contentPanel))
                .perform(swipeUp());
        onView(withText(R.string.resolver_work_tab)).perform(click());
        waitForIdle();

        onView(withText(R.string.resolver_cross_profile_blocked))
                .check(matches(isDisplayed()));
    }

    @Test
    public void testWorkTab_noAppsAvailable_workOff_noAppsAvailableEmptyStateShown() {
        markOtherProfileAvailability(/* workAvailable= */ true, /* cloneAvailable= */ false);
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTest(3);
        List<ResolvedComponentInfo> workResolvedComponentInfos =
                createResolvedComponentsForTest(0);
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
        ChooserActivityOverrideData.getInstance().isQuietModeEnabled = true;
        Intent sendIntent = createSendTextIntent();
        sendIntent.setType(TEST_MIME_TYPE);

        mActivityRule.launchActivity(Intent.createChooser(sendIntent, "work tab test"));
        waitForIdle();
        onView(withId(com.android.internal.R.id.contentPanel))
                .perform(swipeUp());
        onView(withText(R.string.resolver_work_tab)).perform(click());
        waitForIdle();

        onView(withText(R.string.resolver_no_work_apps_available))
                .check(matches(isDisplayed()));
    }

    @Test @Ignore("b/222124533")
    public void testAppTargetLogging() throws InterruptedException {
        Intent sendIntent = createSendTextIntent();
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);

        final IChooserWrapper activity = (IChooserWrapper)
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        // TODO(b/222124533): other test cases use a timeout to make sure that the UI is fully
        // populated; without one, this test flakes. Ideally we should address the need for a
        // timeout everywhere instead of introducing one to fix this particular test.

        assertThat(activity.getAdapter().getCount(), is(2));
        onView(withId(com.android.internal.R.id.profile_button)).check(doesNotExist());

        ResolveInfo[] chosen = new ResolveInfo[1];
        ChooserActivityOverrideData.getInstance().onSafelyStartInternalCallback = targetInfo -> {
            chosen[0] = targetInfo.getResolveInfo();
            return true;
        };

        ResolveInfo toChoose = resolvedComponentInfos.get(0).getResolveInfoAt(0);
        onView(withText(toChoose.activityInfo.name))
                .perform(click());
        waitForIdle();

        // TODO(b/211669337): Determine the expected SHARESHEET_DIRECT_LOAD_COMPLETE events.
    }

    @Test
    public void testDirectTargetLogging() {
        Intent sendIntent = createSendTextIntent();
        // We need app targets for direct targets to get displayed
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);
        setupResolverControllers(resolvedComponentInfos);

        // create test shortcut loader factory, remember loaders and their callbacks
        SparseArray<Pair<ShortcutLoader, Consumer<ShortcutLoader.Result>>> shortcutLoaders =
                new SparseArray<>();
        ChooserActivityOverrideData.getInstance().shortcutLoaderFactory =
                (userHandle, callback) -> {
                    Pair<ShortcutLoader, Consumer<ShortcutLoader.Result>> pair =
                            new Pair<>(mock(ShortcutLoader.class), callback);
                    shortcutLoaders.put(userHandle.getIdentifier(), pair);
                    return pair.first;
                };

        // Start activity
        ChooserWrapperActivity activity =
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        // verify that ShortcutLoader was queried
        ArgumentCaptor<DisplayResolveInfo[]> appTargets =
                ArgumentCaptor.forClass(DisplayResolveInfo[].class);
        verify(shortcutLoaders.get(0).first, times(1))
                .updateAppTargets(appTargets.capture());

        // send shortcuts
        assertThat(
                "Wrong number of app targets",
                appTargets.getValue().length,
                is(resolvedComponentInfos.size()));
        List<ChooserTarget> serviceTargets = createDirectShareTargets(1,
                resolvedComponentInfos.get(0).getResolveInfoAt(0).activityInfo.packageName);
        ShortcutLoader.Result result = new ShortcutLoader.Result(
                // TODO: test another value as well
                false,
                appTargets.getValue(),
                new ShortcutLoader.ShortcutResultInfo[] {
                        new ShortcutLoader.ShortcutResultInfo(
                                appTargets.getValue()[0],
                                serviceTargets
                        )
                },
                new HashMap<>(),
                new HashMap<>()
        );
        activity.getMainExecutor().execute(() -> shortcutLoaders.get(0).second.accept(result));
        waitForIdle();

        assertThat("Chooser should have 3 targets (2 apps, 1 direct)",
                activity.getAdapter().getCount(), is(3));
        assertThat("Chooser should have exactly one selectable direct target",
                activity.getAdapter().getSelectableServiceTargetCount(), is(1));
        assertThat(
                "The resolver info must match the resolver info used to create the target",
                activity.getAdapter().getItem(0).getResolveInfo(),
                is(resolvedComponentInfos.get(0).getResolveInfoAt(0)));

        // Click on the direct target
        String name = serviceTargets.get(0).getTitle().toString();
        onView(withText(name))
                .perform(click());
        waitForIdle();

        FakeEventLog eventLog = getEventLog(activity);
        assertThat(eventLog.getShareTargetSelected()).hasSize(1);
        FakeEventLog.ShareTargetSelected call = eventLog.getShareTargetSelected().get(0);
        assertThat(call.getTargetType()).isEqualTo(EventLog.SELECTION_TYPE_SERVICE);
    }

    @Test
    public void testDirectTargetPinningDialog() {
        Intent sendIntent = createSendTextIntent();
        // We need app targets for direct targets to get displayed
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);
        setupResolverControllers(resolvedComponentInfos);

        // create test shortcut loader factory, remember loaders and their callbacks
        SparseArray<Pair<ShortcutLoader, Consumer<ShortcutLoader.Result>>> shortcutLoaders =
                new SparseArray<>();
        ChooserActivityOverrideData.getInstance().shortcutLoaderFactory =
                (userHandle, callback) -> {
                    Pair<ShortcutLoader, Consumer<ShortcutLoader.Result>> pair =
                            new Pair<>(mock(ShortcutLoader.class), callback);
                    shortcutLoaders.put(userHandle.getIdentifier(), pair);
                    return pair.first;
                };

        // Start activity
        final IChooserWrapper activity = (IChooserWrapper)
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        // verify that ShortcutLoader was queried
        ArgumentCaptor<DisplayResolveInfo[]> appTargets =
                ArgumentCaptor.forClass(DisplayResolveInfo[].class);
        verify(shortcutLoaders.get(0).first, times(1))
                .updateAppTargets(appTargets.capture());

        // send shortcuts
        List<ChooserTarget> serviceTargets = createDirectShareTargets(
                1,
                resolvedComponentInfos.get(0).getResolveInfoAt(0).activityInfo.packageName);
        ShortcutLoader.Result result = new ShortcutLoader.Result(
                // TODO: test another value as well
                false,
                appTargets.getValue(),
                new ShortcutLoader.ShortcutResultInfo[] {
                        new ShortcutLoader.ShortcutResultInfo(
                                appTargets.getValue()[0],
                                serviceTargets
                        )
                },
                new HashMap<>(),
                new HashMap<>()
        );
        activity.getMainExecutor().execute(() -> shortcutLoaders.get(0).second.accept(result));
        waitForIdle();

        // Long-click on the direct target
        String name = serviceTargets.get(0).getTitle().toString();
        onView(withText(name)).perform(longClick());
        waitForIdle();

        onView(withId(R.id.chooser_dialog_content)).check(matches(isDisplayed()));
    }

    @Test @Ignore
    public void testEmptyDirectRowLogging() throws InterruptedException {
        Intent sendIntent = createSendTextIntent();
        // We need app targets for direct targets to get displayed
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);
        setupResolverControllers(resolvedComponentInfos);

        // Start activity
        final IChooserWrapper activity = (IChooserWrapper)
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));

        // Thread.sleep shouldn't be a thing in an integration test but it's
        // necessary here because of the way the code is structured
        Thread.sleep(3000);

        assertThat("Chooser should have 2 app targets",
                activity.getAdapter().getCount(), is(2));
        assertThat("Chooser should have no direct targets",
                activity.getAdapter().getSelectableServiceTargetCount(), is(0));

        // TODO(b/211669337): Determine the expected SHARESHEET_DIRECT_LOAD_COMPLETE events.
    }

    @Ignore // b/220067877
    @Test
    public void testCopyTextToClipboardLogging() throws Exception {
        Intent sendIntent = createSendTextIntent();
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);

        final IChooserWrapper activity = (IChooserWrapper)
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        onView(withId(com.android.internal.R.id.chooser_copy_button)).check(matches(isDisplayed()));
        onView(withId(com.android.internal.R.id.chooser_copy_button)).perform(click());

        // TODO(b/211669337): Determine the expected SHARESHEET_DIRECT_LOAD_COMPLETE events.
    }

    @Test @Ignore("b/222124533")
    public void testSwitchProfileLogging() throws InterruptedException {
        markOtherProfileAvailability(/* workAvailable= */ true, /* cloneAvailable= */ false);
        int workProfileTargets = 4;
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTestWithOtherProfile(3, /* userId */ 10);
        List<ResolvedComponentInfo> workResolvedComponentInfos =
                createResolvedComponentsForTest(workProfileTargets);
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
        Intent sendIntent = createSendTextIntent();
        sendIntent.setType(TEST_MIME_TYPE);

        final IChooserWrapper activity = (IChooserWrapper)
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, "work tab test"));
        waitForIdle();
        onView(withText(R.string.resolver_work_tab)).perform(click());
        waitForIdle();
        onView(withText(R.string.resolver_personal_tab)).perform(click());
        waitForIdle();

        // TODO(b/211669337): Determine the expected SHARESHEET_DIRECT_LOAD_COMPLETE events.
    }

    @Test
    public void testWorkTab_onePersonalTarget_emptyStateOnWorkTarget_doesNotAutoLaunch() {
        markOtherProfileAvailability(/* workAvailable= */ true, /* cloneAvailable= */ false);
        int workProfileTargets = 4;
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTestWithOtherProfile(2, /* userId */ 10);
        List<ResolvedComponentInfo> workResolvedComponentInfos =
                createResolvedComponentsForTest(workProfileTargets);
        ChooserActivityOverrideData.getInstance().hasCrossProfileIntents = false;
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
        Intent sendIntent = createSendTextIntent();
        ResolveInfo[] chosen = new ResolveInfo[1];
        ChooserActivityOverrideData.getInstance().onSafelyStartInternalCallback = targetInfo -> {
            chosen[0] = targetInfo.getResolveInfo();
            return true;
        };

        mActivityRule.launchActivity(Intent.createChooser(sendIntent, "Test"));
        waitForIdle();

        assertNull(chosen[0]);
    }

    @Test
    public void testOneInitialIntent_noAutolaunch() {
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTest(1);
        setupResolverControllers(personalResolvedComponentInfos);
        Intent chooserIntent = createChooserIntent(createSendTextIntent(),
                new Intent[] {new Intent("action.fake")});
        ResolveInfo[] chosen = new ResolveInfo[1];
        ChooserActivityOverrideData.getInstance().onSafelyStartInternalCallback = targetInfo -> {
            chosen[0] = targetInfo.getResolveInfo();
            return true;
        };
        ChooserActivityOverrideData.getInstance().packageManager = mock(PackageManager.class);
        ResolveInfo ri = createFakeResolveInfo();
        when(
                ChooserActivityOverrideData
                        .getInstance().packageManager
                        .resolveActivity(any(Intent.class), any()))
                .thenReturn(ri);
        waitForIdle();

        IChooserWrapper activity = (IChooserWrapper) mActivityRule.launchActivity(chooserIntent);
        waitForIdle();

        assertNull(chosen[0]);
        assertThat(activity
                .getPersonalListAdapter().getCallerTargetCount(), is(1));
    }

    @Test
    public void testWorkTab_withInitialIntents_workTabDoesNotIncludePersonalInitialIntents() {
        markOtherProfileAvailability(/* workAvailable= */ true, /* cloneAvailable= */ false);
        int workProfileTargets = 1;
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTestWithOtherProfile(2, /* userId */ 10);
        List<ResolvedComponentInfo> workResolvedComponentInfos =
                createResolvedComponentsForTest(workProfileTargets);
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
        Intent[] initialIntents = {
                new Intent("action.fake1"),
                new Intent("action.fake2")
        };
        Intent chooserIntent = createChooserIntent(createSendTextIntent(), initialIntents);
        ChooserActivityOverrideData.getInstance().packageManager = mock(PackageManager.class);
        when(
                ChooserActivityOverrideData
                        .getInstance()
                        .packageManager
                        .resolveActivity(any(Intent.class), any()))
                .thenReturn(createFakeResolveInfo());
        waitForIdle();

        IChooserWrapper activity = (IChooserWrapper) mActivityRule.launchActivity(chooserIntent);
        waitForIdle();

        assertThat(activity.getPersonalListAdapter().getCallerTargetCount(), is(2));
        assertThat(activity.getWorkListAdapter().getCallerTargetCount(), is(0));
    }

    @Test
    public void testWorkTab_xProfileIntentsDisabled_personalToWork_nonSendIntent_emptyStateShown() {
        markOtherProfileAvailability(/* workAvailable= */ true, /* cloneAvailable= */ false);
        int workProfileTargets = 4;
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTestWithOtherProfile(3, /* userId */ 10);
        List<ResolvedComponentInfo> workResolvedComponentInfos =
                createResolvedComponentsForTest(workProfileTargets);
        ChooserActivityOverrideData.getInstance().hasCrossProfileIntents = false;
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
        Intent[] initialIntents = {
                new Intent("action.fake1"),
                new Intent("action.fake2")
        };
        Intent chooserIntent = createChooserIntent(new Intent(), initialIntents);
        ChooserActivityOverrideData.getInstance().packageManager = mock(PackageManager.class);
        when(
                ChooserActivityOverrideData
                        .getInstance()
                        .packageManager
                        .resolveActivity(any(Intent.class), any()))
                .thenReturn(createFakeResolveInfo());

        mActivityRule.launchActivity(chooserIntent);
        waitForIdle();
        onView(withText(R.string.resolver_work_tab)).perform(click());
        waitForIdle();
        onView(withId(com.android.internal.R.id.contentPanel))
                .perform(swipeUp());

        onView(withText(R.string.resolver_cross_profile_blocked))
                .check(matches(isDisplayed()));
    }

    @Test
    public void testWorkTab_noWorkAppsAvailable_nonSendIntent_emptyStateShown() {
        markOtherProfileAvailability(/* workAvailable= */ true, /* cloneAvailable= */ false);
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTest(3);
        List<ResolvedComponentInfo> workResolvedComponentInfos =
                createResolvedComponentsForTest(0);
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
        Intent[] initialIntents = {
                new Intent("action.fake1"),
                new Intent("action.fake2")
        };
        Intent chooserIntent = createChooserIntent(new Intent(), initialIntents);
        ChooserActivityOverrideData.getInstance().packageManager = mock(PackageManager.class);
        when(
                ChooserActivityOverrideData
                        .getInstance()
                        .packageManager
                        .resolveActivity(any(Intent.class), any()))
                .thenReturn(createFakeResolveInfo());

        mActivityRule.launchActivity(chooserIntent);
        waitForIdle();
        onView(withId(com.android.internal.R.id.contentPanel))
                .perform(swipeUp());
        onView(withText(R.string.resolver_work_tab)).perform(click());
        waitForIdle();

        onView(withText(R.string.resolver_no_work_apps_available))
                .check(matches(isDisplayed()));
    }

    @Test
    public void testDeduplicateCallerTargetRankedTarget() {
        // Create 4 ranked app targets.
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTest(4);
        setupResolverControllers(personalResolvedComponentInfos);
        // Create caller target which is duplicate with one of app targets
        Intent chooserIntent = createChooserIntent(createSendTextIntent(),
                new Intent[] {new Intent("action.fake")});
        ChooserActivityOverrideData.getInstance().packageManager = mock(PackageManager.class);
        ResolveInfo ri = ResolverDataProvider.createResolveInfo(0,
                UserHandle.USER_CURRENT, PERSONAL_USER_HANDLE);
        when(
                ChooserActivityOverrideData
                        .getInstance()
                        .packageManager
                        .resolveActivity(any(Intent.class), any()))
                .thenReturn(ri);
        waitForIdle();

        IChooserWrapper activity = (IChooserWrapper) mActivityRule.launchActivity(chooserIntent);
        waitForIdle();

        // Total 4 targets (1 caller target, 3 ranked targets)
        assertThat(activity.getAdapter().getCount(), is(4));
        assertThat(activity.getAdapter().getCallerTargetCount(), is(1));
        assertThat(activity.getAdapter().getRankedTargetCount(), is(3));
    }

    @Test
    public void test_query_shortcut_loader_for_the_selected_tab() {
        markOtherProfileAvailability(/* workAvailable= */ true, /* cloneAvailable= */ false);
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTestWithOtherProfile(3, /* userId */ 10);
        List<ResolvedComponentInfo> workResolvedComponentInfos =
                createResolvedComponentsForTest(3);
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
        ShortcutLoader personalProfileShortcutLoader = mock(ShortcutLoader.class);
        ShortcutLoader workProfileShortcutLoader = mock(ShortcutLoader.class);
        final SparseArray<ShortcutLoader> shortcutLoaders = new SparseArray<>();
        shortcutLoaders.put(0, personalProfileShortcutLoader);
        shortcutLoaders.put(10, workProfileShortcutLoader);
        ChooserActivityOverrideData.getInstance().shortcutLoaderFactory =
                (userHandle, callback) -> shortcutLoaders.get(userHandle.getIdentifier(), null);
        Intent sendIntent = createSendTextIntent();
        sendIntent.setType(TEST_MIME_TYPE);

        mActivityRule.launchActivity(Intent.createChooser(sendIntent, "work tab test"));
        waitForIdle();
        onView(withId(com.android.internal.R.id.contentPanel))
                .perform(swipeUp());
        waitForIdle();

        verify(personalProfileShortcutLoader, times(1)).updateAppTargets(any());

        onView(withText(R.string.resolver_work_tab)).perform(click());
        waitForIdle();

        verify(workProfileShortcutLoader, times(1)).updateAppTargets(any());
    }

    @Test
    public void testClonedProfilePresent_personalAdapterIsSetWithPersonalProfile() {
        // enable cloneProfile
        markOtherProfileAvailability(/* workAvailable= */ false, /* cloneAvailable= */ true);
        List<ResolvedComponentInfo> resolvedComponentInfos =
                createResolvedComponentsWithCloneProfileForTest(
                        3,
                        PERSONAL_USER_HANDLE,
                        CLONE_PROFILE_USER_HANDLE);
        setupResolverControllers(resolvedComponentInfos);
        Intent sendIntent = createSendTextIntent();

        final IChooserWrapper activity = (IChooserWrapper) mActivityRule
                .launchActivity(Intent.createChooser(sendIntent, "personalProfileTest"));
        waitForIdle();

        assertThat(activity.getPersonalListAdapter().getUserHandle(), is(PERSONAL_USER_HANDLE));
        assertThat(activity.getAdapter().getCount(), is(3));
    }

    @Test
    public void testClonedProfilePresent_personalTabUsesExpectedAdapter() {
        markOtherProfileAvailability(/* workAvailable= */ true, /* cloneAvailable= */ true);
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTest(3);
        List<ResolvedComponentInfo> workResolvedComponentInfos = createResolvedComponentsForTest(
                4);
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
        Intent sendIntent = createSendTextIntent();
        sendIntent.setType(TEST_MIME_TYPE);


        final IChooserWrapper activity = (IChooserWrapper)
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, "multi tab test"));
        waitForIdle();

        assertThat(activity.getCurrentUserHandle(), is(PERSONAL_USER_HANDLE));
    }

    private Intent createChooserIntent(Intent intent, Intent[] initialIntents) {
        Intent chooserIntent = new Intent();
        chooserIntent.setAction(Intent.ACTION_CHOOSER);
        chooserIntent.putExtra(Intent.EXTRA_TEXT, "testing intent sending");
        chooserIntent.putExtra(Intent.EXTRA_TITLE, "some title");
        chooserIntent.putExtra(Intent.EXTRA_INTENT, intent);
        chooserIntent.setType("text/plain");
        if (initialIntents != null) {
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, initialIntents);
        }
        return chooserIntent;
    }

    /* This is a "test of a test" to make sure that our inherited test class
     * is successfully configured to operate on the unbundled-equivalent
     * ChooserWrapperActivity.
     *
     * TODO: remove after unbundling is complete.
     */
    @Test
    public void testWrapperActivityHasExpectedConcreteType() {
        final ChooserActivity activity = mActivityRule.launchActivity(
                Intent.createChooser(new Intent("ACTION_FOO"), "foo"));
        waitForIdle();
        assertThat(activity).isInstanceOf(ChooserWrapperActivity.class);
    }

    private ResolveInfo createFakeResolveInfo() {
        ResolveInfo ri = new ResolveInfo();
        ri.activityInfo = new ActivityInfo();
        ri.activityInfo.name = "FakeActivityName";
        ri.activityInfo.packageName = "fake.package.name";
        ri.activityInfo.applicationInfo = new ApplicationInfo();
        ri.activityInfo.applicationInfo.packageName = "fake.package.name";
        ri.userHandle = UserHandle.CURRENT;
        return ri;
    }

    private Intent createSendTextIntent() {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, "testing intent sending");
        sendIntent.setType("text/plain");
        return sendIntent;
    }

    private Intent createSendImageIntent(Uri imageThumbnail) {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_STREAM, imageThumbnail);
        sendIntent.setType("image/png");
        if (imageThumbnail != null) {
            ClipData.Item clipItem = new ClipData.Item(imageThumbnail);
            sendIntent.setClipData(new ClipData("Clip Label", new String[]{"image/png"}, clipItem));
        }

        return sendIntent;
    }

    private Uri createTestContentProviderUri(
            @Nullable String mimeType, @Nullable String streamType) {
        return createTestContentProviderUri(mimeType, streamType, 0);
    }

    private Uri createTestContentProviderUri(
            @Nullable String mimeType, @Nullable String streamType, long streamTypeTimeout) {
        String packageName =
                InstrumentationRegistry.getInstrumentation().getContext().getPackageName();
        Uri.Builder builder = Uri.parse("content://" + packageName + "/image.png")
                .buildUpon();
        if (mimeType != null) {
            builder.appendQueryParameter(TestContentProvider.PARAM_MIME_TYPE, mimeType);
        }
        if (streamType != null) {
            builder.appendQueryParameter(TestContentProvider.PARAM_STREAM_TYPE, streamType);
        }
        if (streamTypeTimeout > 0) {
            builder.appendQueryParameter(
                    TestContentProvider.PARAM_STREAM_TYPE_TIMEOUT,
                    Long.toString(streamTypeTimeout));
        }
        return builder.build();
    }

    private Intent createSendTextIntentWithPreview(String title, Uri imageThumbnail) {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, "testing intent sending");
        sendIntent.putExtra(Intent.EXTRA_TITLE, title);
        if (imageThumbnail != null) {
            ClipData.Item clipItem = new ClipData.Item(imageThumbnail);
            sendIntent.setClipData(new ClipData("Clip Label", new String[]{"image/png"}, clipItem));
        }

        return sendIntent;
    }

    private Intent createSendUriIntentWithPreview(ArrayList<Uri> uris) {
        Intent sendIntent = new Intent();

        if (uris.size() > 1) {
            sendIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
            sendIntent.putExtra(Intent.EXTRA_STREAM, uris);
        } else {
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_STREAM, uris.get(0));
        }

        return sendIntent;
    }

    private Intent createViewTextIntent() {
        Intent viewIntent = new Intent();
        viewIntent.setAction(Intent.ACTION_VIEW);
        viewIntent.putExtra(Intent.EXTRA_TEXT, "testing intent viewing");
        return viewIntent;
    }

    private List<ResolvedComponentInfo> createResolvedComponentsForTest(int numberOfResults) {
        List<ResolvedComponentInfo> infoList = new ArrayList<>(numberOfResults);
        for (int i = 0; i < numberOfResults; i++) {
            infoList.add(ResolverDataProvider.createResolvedComponentInfo(i, PERSONAL_USER_HANDLE));
        }
        return infoList;
    }

    private List<ResolvedComponentInfo> createResolvedComponentsWithCloneProfileForTest(
            int numberOfResults,
            UserHandle resolvedForPersonalUser,
            UserHandle resolvedForClonedUser) {
        List<ResolvedComponentInfo> infoList = new ArrayList<>(numberOfResults);
        for (int i = 0; i < 1; i++) {
            infoList.add(ResolverDataProvider.createResolvedComponentInfo(i,
                    resolvedForPersonalUser));
        }
        for (int i = 1; i < numberOfResults; i++) {
            infoList.add(ResolverDataProvider.createResolvedComponentInfo(i,
                    resolvedForClonedUser));
        }
        return infoList;
    }

    private List<ResolvedComponentInfo> createResolvedComponentsForTestWithOtherProfile(
            int numberOfResults) {
        List<ResolvedComponentInfo> infoList = new ArrayList<>(numberOfResults);
        for (int i = 0; i < numberOfResults; i++) {
            if (i == 0) {
                infoList.add(ResolverDataProvider.createResolvedComponentInfoWithOtherId(i,
                        PERSONAL_USER_HANDLE));
            } else {
                infoList.add(ResolverDataProvider.createResolvedComponentInfo(i,
                        PERSONAL_USER_HANDLE));
            }
        }
        return infoList;
    }

    private List<ResolvedComponentInfo> createResolvedComponentsForTestWithOtherProfile(
            int numberOfResults, int userId) {
        List<ResolvedComponentInfo> infoList = new ArrayList<>(numberOfResults);
        for (int i = 0; i < numberOfResults; i++) {
            if (i == 0) {
                infoList.add(
                        ResolverDataProvider.createResolvedComponentInfoWithOtherId(i, userId,
                                PERSONAL_USER_HANDLE));
            } else {
                infoList.add(ResolverDataProvider.createResolvedComponentInfo(i,
                        PERSONAL_USER_HANDLE));
            }
        }
        return infoList;
    }

    private List<ResolvedComponentInfo> createResolvedComponentsForTestWithUserId(
            int numberOfResults, int userId) {
        List<ResolvedComponentInfo> infoList = new ArrayList<>(numberOfResults);
        for (int i = 0; i < numberOfResults; i++) {
            infoList.add(ResolverDataProvider.createResolvedComponentInfoWithOtherId(i, userId,
                    PERSONAL_USER_HANDLE));
        }
        return infoList;
    }

    private List<ChooserTarget> createDirectShareTargets(int numberOfResults, String packageName) {
        Icon icon = Icon.createWithBitmap(createBitmap());
        String testTitle = "testTitle";
        List<ChooserTarget> targets = new ArrayList<>();
        for (int i = 0; i < numberOfResults; i++) {
            ComponentName componentName;
            if (packageName.isEmpty()) {
                componentName = ResolverDataProvider.createComponentName(i);
            } else {
                componentName = new ComponentName(packageName, packageName + ".class");
            }
            ChooserTarget tempTarget = new ChooserTarget(
                    testTitle + i,
                    icon,
                    (float) (1 - ((i + 1) / 10.0)),
                    componentName,
                    null);
            targets.add(tempTarget);
        }
        return targets;
    }

    private void waitForIdle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private Bitmap createBitmap() {
        return createBitmap(200, 200);
    }

    private Bitmap createWideBitmap() {
        return createWideBitmap(Color.RED);
    }

    private Bitmap createWideBitmap(int bgColor) {
        WindowManager windowManager = InstrumentationRegistry.getInstrumentation()
                .getTargetContext()
                .getSystemService(WindowManager.class);
        int width = 3000;
        if (windowManager != null) {
            Rect bounds = windowManager.getMaximumWindowMetrics().getBounds();
            width = bounds.width() + 200;
        }
        return createBitmap(width, 100, bgColor);
    }

    private Bitmap createBitmap(int width, int height) {
        return createBitmap(width, height, Color.RED);
    }

    private Bitmap createBitmap(int width, int height, int bgColor) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint();
        paint.setColor(bgColor);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawPaint(paint);

        paint.setColor(Color.WHITE);
        paint.setAntiAlias(true);
        paint.setTextSize(14.f);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("Hi!", (width / 2.f), (height / 2.f), paint);

        return bitmap;
    }

    private List<ShareShortcutInfo> createShortcuts(Context context) {
        Intent testIntent = new Intent("TestIntent");

        List<ShareShortcutInfo> shortcuts = new ArrayList<>();
        shortcuts.add(new ShareShortcutInfo(
                new ShortcutInfo.Builder(context, "shortcut1")
                        .setIntent(testIntent).setShortLabel("label1").setRank(3).build(), // 0  2
                new ComponentName("package1", "class1")));
        shortcuts.add(new ShareShortcutInfo(
                new ShortcutInfo.Builder(context, "shortcut2")
                        .setIntent(testIntent).setShortLabel("label2").setRank(7).build(), // 1  3
                new ComponentName("package2", "class2")));
        shortcuts.add(new ShareShortcutInfo(
                new ShortcutInfo.Builder(context, "shortcut3")
                        .setIntent(testIntent).setShortLabel("label3").setRank(1).build(), // 2  0
                new ComponentName("package3", "class3")));
        shortcuts.add(new ShareShortcutInfo(
                new ShortcutInfo.Builder(context, "shortcut4")
                        .setIntent(testIntent).setShortLabel("label4").setRank(3).build(), // 3  2
                new ComponentName("package4", "class4")));

        return shortcuts;
    }

    private void markOtherProfileAvailability(boolean workAvailable, boolean cloneAvailable) {
        AnnotatedUserHandles.Builder handles = AnnotatedUserHandles.newBuilder();
        handles
                .setUserIdOfCallingApp(1234)  // Must be non-negative.
                .setUserHandleSharesheetLaunchedAs(PERSONAL_USER_HANDLE)
                .setPersonalProfileUserHandle(PERSONAL_USER_HANDLE);
        if (workAvailable) {
            handles.setWorkProfileUserHandle(WORK_PROFILE_USER_HANDLE);
        }
        if (cloneAvailable) {
            handles.setCloneProfileUserHandle(CLONE_PROFILE_USER_HANDLE);
        }
        ChooserWrapperActivity.sOverrides.annotatedUserHandles = handles.build();
    }

    private void setupResolverControllers(
            List<ResolvedComponentInfo> personalResolvedComponentInfos) {
        setupResolverControllers(personalResolvedComponentInfos, new ArrayList<>());
    }

    private void setupResolverControllers(
            List<ResolvedComponentInfo> personalResolvedComponentInfos,
            List<ResolvedComponentInfo> workResolvedComponentInfos) {
        when(
                ChooserActivityOverrideData
                        .getInstance()
                        .resolverListController
                        .getResolversForIntentAsUser(
                                Mockito.anyBoolean(),
                                Mockito.anyBoolean(),
                                Mockito.anyBoolean(),
                                Mockito.isA(List.class),
                                eq(UserHandle.SYSTEM)))
                .thenReturn(new ArrayList<>(personalResolvedComponentInfos));
        when(
                ChooserActivityOverrideData
                        .getInstance()
                        .workResolverListController
                        .getResolversForIntentAsUser(
                                Mockito.anyBoolean(),
                                Mockito.anyBoolean(),
                                Mockito.anyBoolean(),
                                Mockito.isA(List.class),
                                eq(UserHandle.SYSTEM)))
                .thenReturn(new ArrayList<>(personalResolvedComponentInfos));
        when(
                ChooserActivityOverrideData
                        .getInstance()
                        .workResolverListController
                        .getResolversForIntentAsUser(
                                Mockito.anyBoolean(),
                                Mockito.anyBoolean(),
                                Mockito.anyBoolean(),
                                Mockito.isA(List.class),
                                eq(UserHandle.of(10))))
                .thenReturn(new ArrayList<>(workResolvedComponentInfos));
    }

    private static GridRecyclerSpanCountMatcher withGridColumnCount(int columnCount) {
        return new GridRecyclerSpanCountMatcher(Matchers.is(columnCount));
    }

    private static class GridRecyclerSpanCountMatcher extends
            BoundedDiagnosingMatcher<View, RecyclerView> {

        private final Matcher<Integer> mIntegerMatcher;

        private GridRecyclerSpanCountMatcher(Matcher<Integer> integerMatcher) {
            super(RecyclerView.class);
            this.mIntegerMatcher = integerMatcher;
        }

        @Override
        protected void describeMoreTo(Description description) {
            description.appendText("RecyclerView grid layout span count to match: ");
            this.mIntegerMatcher.describeTo(description);
        }

        @Override
        protected boolean matchesSafely(RecyclerView view, Description mismatchDescription) {
            int spanCount = ((GridLayoutManager) view.getLayoutManager()).getSpanCount();
            if (this.mIntegerMatcher.matches(spanCount)) {
                return true;
            } else {
                mismatchDescription.appendText("RecyclerView grid layout span count was ")
                        .appendValue(spanCount);
                return false;
            }
        }
    }

    private void givenAppTargets(int appCount) {
        List<ResolvedComponentInfo> resolvedComponentInfos =
                createResolvedComponentsForTest(appCount);
        setupResolverControllers(resolvedComponentInfos);
    }

    private void updateMaxTargetsPerRowResource(int targetsPerRow) {
        Resources resources = Mockito.spy(
                InstrumentationRegistry.getInstrumentation().getContext().getResources());
        ChooserActivityOverrideData.getInstance().resources = resources;
        doReturn(targetsPerRow).when(resources).getInteger(
                R.integer.config_chooser_max_targets_per_row);
    }

    private SparseArray<Pair<ShortcutLoader, Consumer<ShortcutLoader.Result>>>
            createShortcutLoaderFactory() {
        SparseArray<Pair<ShortcutLoader, Consumer<ShortcutLoader.Result>>> shortcutLoaders =
                new SparseArray<>();
        ChooserActivityOverrideData.getInstance().shortcutLoaderFactory =
                (userHandle, callback) -> {
                    Pair<ShortcutLoader, Consumer<ShortcutLoader.Result>> pair =
                            new Pair<>(mock(ShortcutLoader.class), callback);
                    shortcutLoaders.put(userHandle.getIdentifier(), pair);
                    return pair.first;
                };
        return shortcutLoaders;
    }

    private static ImageLoader createImageLoader(Uri uri, Bitmap bitmap) {
        return new TestPreviewImageLoader(Collections.singletonMap(uri, bitmap));
    }
}

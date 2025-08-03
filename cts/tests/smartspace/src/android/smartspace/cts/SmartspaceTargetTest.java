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
package android.smartspace.cts;

import static android.app.smartspace.SmartspaceTarget.FEATURE_ALARM;

import static androidx.test.InstrumentationRegistry.getContext;

import static com.google.common.truth.Truth.assertThat;

import android.app.smartspace.SmartspaceAction;
import android.app.smartspace.SmartspaceTarget;
import android.app.smartspace.flags.Flags;
import android.app.smartspace.uitemplatedata.BaseTemplateData;
import android.app.smartspace.uitemplatedata.BaseTemplateData.SubItemInfo;
import android.app.smartspace.uitemplatedata.BaseTemplateData.SubItemLoggingInfo;
import android.app.smartspace.uitemplatedata.Text;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.net.Uri;
import android.os.Parcel;
import android.os.Process;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.widget.RemoteViews;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for {@link SmartspaceTarget}
 *
 * atest CtsSmartspaceServiceTestCases
 */
@RunWith(AndroidJUnit4.class)
public class SmartspaceTargetTest {

    private static final String TAG = "SmartspaceTargetTest";

    @Test
    public void testCreateSmartspaceTarget() {
        ComponentName testComponentName = new ComponentName("package_name", "class_name");
        AppWidgetProviderInfo info = new AppWidgetProviderInfo();
        info.provider = testComponentName;
        SmartspaceTarget testTarget = new SmartspaceTarget.Builder("test_target_id",
                testComponentName, Process.myUserHandle())
                .setHeaderAction(createSmartspaceAction("header"))
                .setBaseAction(createSmartspaceAction("base"))
                .setCreationTimeMillis(10)
                .setExpiryTimeMillis(15)
                .setScore(0.5f)
                .setActionChips(createSmartspaceActionList("chips"))
                .setIconGrid(createSmartspaceActionList("icon_grid"))
                .setFeatureType(FEATURE_ALARM)
                .setSensitive(true)
                .setShouldShowExpanded(true)
                .setSourceNotificationKey("source_notification_key")
                .setAssociatedSmartspaceTargetId("associated_target_id")
                .setSliceUri(Uri.EMPTY)
                .setTemplateData(createBaseTemplateData())
                .build();


        assertThat(testTarget.getSmartspaceTargetId()).isEqualTo("test_target_id");
        assertThat(testTarget.getHeaderAction()).isEqualTo(createSmartspaceAction("header"));
        assertThat(testTarget.getBaseAction()).isEqualTo(createSmartspaceAction("base"));
        assertThat(testTarget.getCreationTimeMillis()).isEqualTo(10);
        assertThat(testTarget.getExpiryTimeMillis()).isEqualTo(15);
        assertThat(testTarget.getScore()).isEqualTo(0.5f);
        assertThat(testTarget.getActionChips().size()).isEqualTo(1);
        assertThat(testTarget.getActionChips().get(0)).isEqualTo(createSmartspaceAction("chips"));
        assertThat(testTarget.getIconGrid().size()).isEqualTo(1);
        assertThat(testTarget.getIconGrid().get(0)).isEqualTo(createSmartspaceAction("icon_grid"));
        assertThat(testTarget.getFeatureType()).isEqualTo(FEATURE_ALARM);
        assertThat(testTarget.isSensitive()).isEqualTo(true);
        assertThat(testTarget.shouldShowExpanded()).isEqualTo(true);
        assertThat(testTarget.getSourceNotificationKey()).isEqualTo("source_notification_key");
        assertThat(testTarget.getAssociatedSmartspaceTargetId()).isEqualTo("associated_target_id");
        assertThat(testTarget.getComponentName()).isEqualTo(testComponentName);
        assertThat(testTarget.getUserHandle()).isEqualTo(Process.myUserHandle());
        assertThat(testTarget.getSliceUri()).isEqualTo(Uri.EMPTY);
        assertThat(testTarget.getTemplateData()).isEqualTo(createBaseTemplateData());
        Parcel parcel = Parcel.obtain();
        parcel.setDataPosition(0);
        testTarget.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        SmartspaceTarget copyTarget = SmartspaceTarget.CREATOR.createFromParcel(parcel);
        assertThat(testTarget).isEqualTo(copyTarget);
        parcel.recycle();
    }

    @Test
    public void testCreateSmartspaceTargetFromWidget() {
        ComponentName testComponentName = new ComponentName("package_name", "class_name");
        AppWidgetProviderInfo info = new AppWidgetProviderInfo();
        info.provider = testComponentName;
        SmartspaceTarget testTarget = new SmartspaceTarget.Builder("test_target_id",
                testComponentName, Process.myUserHandle())
                .setWidget(info)
                .build();

        assertThat(testTarget.getWidget().provider).isEqualTo(testComponentName);
    }

    @RequiresFlagsEnabled(Flags.FLAG_REMOTE_VIEWS)
    @Test
    public void testCreateSmartspaceTargetFromRemoteViews() {
        ComponentName testComponentName = new ComponentName("package_name", "class_name");
        RemoteViews remoteViews = new RemoteViews(getContext().getPackageName(),
                R.layout.remoteviews);
        SmartspaceTarget testTarget = new SmartspaceTarget.Builder("test_target_id",
                testComponentName, Process.myUserHandle())
                .setRemoteViews(remoteViews)
                .build();

        assertThat(testTarget.getRemoteViews()).isEqualTo(remoteViews);
    }

    @RequiresFlagsEnabled(Flags.FLAG_REMOTE_VIEWS)
    @Test
    public void testCreateSmartspaceTargetFromWidgetAndRemoteViewsAtSameTime() {
        ComponentName testComponentName = new ComponentName("package_name", "class_name");
        AppWidgetProviderInfo info = new AppWidgetProviderInfo();
        RemoteViews remoteViews = new RemoteViews(getContext().getPackageName(),
                R.layout.remoteviews);
        info.provider = testComponentName;

        IllegalStateException actualException = null;
        try {
            SmartspaceTarget.Builder testBuilder = new SmartspaceTarget.Builder("test_target_id",
                    testComponentName, Process.myUserHandle())
                    .setWidget(info)
                    .setRemoteViews(remoteViews);
        } catch (IllegalStateException e) {
            actualException = e;
        }

        assertThat(actualException.getMessage()).isEqualTo(
                "Widget providers and RemoteViews cannot be used at the same time.");
    }

    @RequiresFlagsEnabled(Flags.FLAG_REMOTE_VIEWS)
    @Test
    public void testCreateSmartspaceTargetFromRemoteViewsAndWidgetAtSameTime() {
        ComponentName testComponentName = new ComponentName("package_name", "class_name");
        AppWidgetProviderInfo info = new AppWidgetProviderInfo();
        RemoteViews remoteViews = new RemoteViews(getContext().getPackageName(),
                R.layout.remoteviews);
        info.provider = testComponentName;

        IllegalStateException actualException = null;
        try {
            SmartspaceTarget.Builder testBuilder = new SmartspaceTarget.Builder("test_target_id",
                    testComponentName, Process.myUserHandle())
                    .setRemoteViews(remoteViews)
                    .setWidget(info);
        } catch (IllegalStateException e) {
            actualException = e;
        }

        assertThat(actualException.getMessage()).isEqualTo(
                "Widget providers and RemoteViews cannot be used at the same time.");
    }

    private List<SmartspaceAction> createSmartspaceActionList(String id) {
        List<SmartspaceAction> actionList = new ArrayList<>();
        actionList.add(new SmartspaceAction.Builder(id, "test title").build());
        return actionList;
    }

    private SmartspaceAction createSmartspaceAction(String id) {
        return new SmartspaceAction.Builder(id, "test title").build();
    }

    private BaseTemplateData createBaseTemplateData() {
        SubItemInfo primaryItem = new SubItemInfo.Builder()
                .setText(new Text.Builder("title").build())
                .setIcon(SmartspaceTestUtils.createSmartspaceIcon("title icon"))
                .setTapAction(
                        SmartspaceTestUtils.createSmartspaceTapAction(getContext(),
                                "primary action"))
                .setLoggingInfo(new SubItemLoggingInfo.Builder(0, 0).setPackageName(
                        "package name 0").build())
                .build();
        SubItemInfo subtitleItem = new SubItemInfo.Builder()
                .setText(new Text.Builder("subtitle").build())
                .setIcon(SmartspaceTestUtils.createSmartspaceIcon("subtitle icon"))
                .setTapAction(
                        SmartspaceTestUtils.createSmartspaceTapAction(getContext(),
                                "subtitle action"))
                .setLoggingInfo(new SubItemLoggingInfo.Builder(1, 1).setPackageName(
                        "package name 1").build())
                .build();
        SubItemInfo subtitleSupplementalItem = new SubItemInfo.Builder()
                .setText(new Text.Builder("subtitle supplemental").build())
                .setIcon(SmartspaceTestUtils.createSmartspaceIcon(
                        "subtitle supplemental icon"))
                .setTapAction(
                        SmartspaceTestUtils.createSmartspaceTapAction(getContext(),
                                "subtitle supplemental action"))
                .setLoggingInfo(new SubItemLoggingInfo.Builder(2, 2).setPackageName(
                        "package name 2").build())
                .build();
        SubItemInfo supplementalLineItem = new SubItemInfo.Builder()
                .setText(new Text.Builder("supplemental line").build())
                .setIcon(SmartspaceTestUtils.createSmartspaceIcon(
                        "supplemental line icon"))
                .setTapAction(
                        SmartspaceTestUtils.createSmartspaceTapAction(getContext(),
                                "supplemental line action"))
                .setLoggingInfo(new SubItemLoggingInfo.Builder(3, 3).setPackageName(
                        "package name 3").build())
                .build();
        SubItemInfo supplementalAlarmItem = new SubItemInfo.Builder()
                .setText(new Text.Builder("alarm supplemental").build())
                .setIcon(SmartspaceTestUtils.createSmartspaceIcon("alarm icon"))
                .setTapAction(
                        SmartspaceTestUtils.createSmartspaceTapAction(getContext(),
                                "alarm action"))
                .setLoggingInfo(new SubItemLoggingInfo.Builder(4, 4).setPackageName(
                        "package name 4").build())
                .build();

        return new BaseTemplateData.Builder(
                SmartspaceTarget.UI_TEMPLATE_DEFAULT)
                .setPrimaryItem(primaryItem)
                .setSubtitleItem(subtitleItem)
                .setSubtitleSupplementalItem(subtitleSupplementalItem)
                .setSupplementalLineItem(supplementalLineItem)
                .setSupplementalAlarmItem(supplementalAlarmItem)
                .setLayoutWeight(1)
                .build();
    }
}

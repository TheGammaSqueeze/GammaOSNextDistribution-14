/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.intentresolver.logging;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.AdditionalMatchers.gt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.content.Intent;
import android.metrics.LogMaker;

import com.android.intentresolver.logging.EventLogImpl.SharesheetStandardEvent;
import com.android.intentresolver.logging.EventLogImpl.SharesheetStartedEvent;
import com.android.intentresolver.logging.EventLogImpl.SharesheetTargetSelectedEvent;
import com.android.intentresolver.contentpreview.ContentPreviewType;
import com.android.internal.logging.InstanceId;
import com.android.internal.logging.InstanceIdSequence;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.UiEventLogger;
import com.android.internal.logging.UiEventLogger.UiEventEnum;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.util.FrameworkStatsLog;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class EventLogImplTest {
    @Mock private UiEventLogger mUiEventLog;
    @Mock private FrameworkStatsLogger mFrameworkLog;
    @Mock private MetricsLogger mMetricsLogger;

    private EventLogImpl mChooserLogger;

    private final InstanceIdSequence mSequence = EventLogImpl.newIdSequence();

    @Before
    public void setUp() {
        mChooserLogger = new EventLogImpl(mUiEventLog, mFrameworkLog, mMetricsLogger,
                mSequence.newInstanceId());
    }

    @After
    public void tearDown() {
        verifyNoMoreInteractions(mUiEventLog);
        verifyNoMoreInteractions(mFrameworkLog);
        verifyNoMoreInteractions(mMetricsLogger);
    }

    @Test
    public void testLogChooserActivityShown_personalProfile() {
        final boolean isWorkProfile = false;
        final String mimeType = "application/TestType";
        final long systemCost = 456;

        mChooserLogger.logChooserActivityShown(isWorkProfile, mimeType, systemCost);

        ArgumentCaptor<LogMaker> eventCaptor = ArgumentCaptor.forClass(LogMaker.class);
        verify(mMetricsLogger).write(eventCaptor.capture());
        LogMaker event = eventCaptor.getValue();

        assertThat(event.getCategory()).isEqualTo(MetricsEvent.ACTION_ACTIVITY_CHOOSER_SHOWN);
        assertThat(event.getSubtype()).isEqualTo(MetricsEvent.PARENT_PROFILE);
        assertThat(event.getTaggedData(MetricsEvent.FIELD_SHARESHEET_MIMETYPE)).isEqualTo(mimeType);
        assertThat(event.getTaggedData(MetricsEvent.FIELD_TIME_TO_APP_TARGETS))
                .isEqualTo(systemCost);
    }

    @Test
    public void testLogChooserActivityShown_workProfile() {
        final boolean isWorkProfile = true;
        final String mimeType = "application/TestType";
        final long systemCost = 456;

        mChooserLogger.logChooserActivityShown(isWorkProfile, mimeType, systemCost);

        ArgumentCaptor<LogMaker> eventCaptor = ArgumentCaptor.forClass(LogMaker.class);
        verify(mMetricsLogger).write(eventCaptor.capture());
        LogMaker event = eventCaptor.getValue();

        assertThat(event.getCategory()).isEqualTo(MetricsEvent.ACTION_ACTIVITY_CHOOSER_SHOWN);
        assertThat(event.getSubtype()).isEqualTo(MetricsEvent.MANAGED_PROFILE);
        assertThat(event.getTaggedData(MetricsEvent.FIELD_SHARESHEET_MIMETYPE)).isEqualTo(mimeType);
        assertThat(event.getTaggedData(MetricsEvent.FIELD_TIME_TO_APP_TARGETS))
                .isEqualTo(systemCost);
    }

    @Test
    public void testLogShareStarted() {
        final String packageName = "com.test.foo";
        final String mimeType = "text/plain";
        final int appProvidedDirectTargets = 123;
        final int appProvidedAppTargets = 456;
        final boolean workProfile = true;
        final int previewType = ContentPreviewType.CONTENT_PREVIEW_FILE;
        final String intentAction = Intent.ACTION_SENDTO;
        final int numCustomActions = 3;
        final boolean modifyShareProvided = true;

        mChooserLogger.logShareStarted(
                packageName,
                mimeType,
                appProvidedDirectTargets,
                appProvidedAppTargets,
                workProfile,
                previewType,
                intentAction,
                numCustomActions,
                modifyShareProvided);

        verify(mFrameworkLog).write(
                eq(FrameworkStatsLog.SHARESHEET_STARTED),
                eq(SharesheetStartedEvent.SHARE_STARTED.getId()),
                eq(packageName),
                /* instanceId=*/ gt(0),
                eq(mimeType),
                eq(appProvidedDirectTargets),
                eq(appProvidedAppTargets),
                eq(workProfile),
                eq(FrameworkStatsLog.SHARESHEET_STARTED__PREVIEW_TYPE__CONTENT_PREVIEW_FILE),
                eq(FrameworkStatsLog.SHARESHEET_STARTED__INTENT_TYPE__INTENT_ACTION_SENDTO),
                /* custom actions provided */ eq(numCustomActions),
                /* reselection action provided */ eq(modifyShareProvided));
    }

    @Test
    public void testLogShareTargetSelected() {
        final int targetType = EventLogImpl.SELECTION_TYPE_SERVICE;
        final String packageName = "com.test.foo";
        final int positionPicked = 123;
        final int directTargetAlsoRanked = -1;
        final int callerTargetCount = 0;
        final boolean isPinned = true;
        final boolean isSuccessfullySelected = true;
        final long selectionCost = 456;

        mChooserLogger.logShareTargetSelected(
                targetType,
                packageName,
                positionPicked,
                directTargetAlsoRanked,
                callerTargetCount,
                /* directTargetHashed= */ null,
                isPinned,
                isSuccessfullySelected,
                selectionCost);

        verify(mFrameworkLog).write(
                eq(FrameworkStatsLog.RANKING_SELECTED),
                eq(SharesheetTargetSelectedEvent.SHARESHEET_SERVICE_TARGET_SELECTED.getId()),
                eq(packageName),
                /* instanceId=*/ gt(0),
                eq(positionPicked),
                eq(isPinned));

        ArgumentCaptor<LogMaker> eventCaptor = ArgumentCaptor.forClass(LogMaker.class);
        verify(mMetricsLogger).write(eventCaptor.capture());
        LogMaker event = eventCaptor.getValue();
        assertThat(event.getCategory()).isEqualTo(
                MetricsEvent.ACTION_ACTIVITY_CHOOSER_PICKED_SERVICE_TARGET);
        assertThat(event.getSubtype()).isEqualTo(positionPicked);
    }

    @Test
    public void testLogActionSelected() {
        mChooserLogger.logActionSelected(EventLogImpl.SELECTION_TYPE_COPY);

        verify(mFrameworkLog).write(
                eq(FrameworkStatsLog.RANKING_SELECTED),
                eq(SharesheetTargetSelectedEvent.SHARESHEET_COPY_TARGET_SELECTED.getId()),
                eq(""),
                /* instanceId=*/ gt(0),
                eq(-1),
                eq(false));

        ArgumentCaptor<LogMaker> eventCaptor = ArgumentCaptor.forClass(LogMaker.class);
        verify(mMetricsLogger).write(eventCaptor.capture());
        LogMaker event = eventCaptor.getValue();
        assertThat(event.getCategory()).isEqualTo(
                MetricsEvent.ACTION_ACTIVITY_CHOOSER_PICKED_SYSTEM_TARGET);
        assertThat(event.getSubtype()).isEqualTo(1);
    }

    @Test
    public void testLogCustomActionSelected() {
        final int position = 4;
        mChooserLogger.logCustomActionSelected(position);

        verify(mFrameworkLog).write(
                eq(FrameworkStatsLog.RANKING_SELECTED),
                eq(SharesheetTargetSelectedEvent.SHARESHEET_CUSTOM_ACTION_SELECTED.getId()),
                any(), anyInt(), eq(position), eq(false));
    }

    @Test
    public void testLogDirectShareTargetReceived() {
        final int category = MetricsEvent.ACTION_DIRECT_SHARE_TARGETS_LOADED_SHORTCUT_MANAGER;
        final int latency = 123;

        mChooserLogger.logDirectShareTargetReceived(category, latency);

        ArgumentCaptor<LogMaker> eventCaptor = ArgumentCaptor.forClass(LogMaker.class);
        verify(mMetricsLogger).write(eventCaptor.capture());
        LogMaker event = eventCaptor.getValue();
        assertThat(event.getCategory()).isEqualTo(category);
        assertThat(event.getSubtype()).isEqualTo(latency);
    }

    @Test
    public void testLogActionShareWithPreview() {
        final int previewType = ContentPreviewType.CONTENT_PREVIEW_TEXT;

        mChooserLogger.logActionShareWithPreview(previewType);

        ArgumentCaptor<LogMaker> eventCaptor = ArgumentCaptor.forClass(LogMaker.class);
        verify(mMetricsLogger).write(eventCaptor.capture());
        LogMaker event = eventCaptor.getValue();
        assertThat(event.getCategory()).isEqualTo(MetricsEvent.ACTION_SHARE_WITH_PREVIEW);
        assertThat(event.getSubtype()).isEqualTo(previewType);
    }

    @Test
    public void testLogSharesheetTriggered() {
        mChooserLogger.logSharesheetTriggered();
        verify(mUiEventLog).logWithInstanceId(
                eq(SharesheetStandardEvent.SHARESHEET_TRIGGERED), eq(0), isNull(), any());
    }

    @Test
    public void testLogSharesheetAppLoadComplete() {
        mChooserLogger.logSharesheetAppLoadComplete();
        verify(mUiEventLog).logWithInstanceId(
                eq(SharesheetStandardEvent.SHARESHEET_APP_LOAD_COMPLETE), eq(0), isNull(), any());
    }

    @Test
    public void testLogSharesheetDirectLoadComplete() {
        mChooserLogger.logSharesheetDirectLoadComplete();
        verify(mUiEventLog).logWithInstanceId(
                eq(SharesheetStandardEvent.SHARESHEET_DIRECT_LOAD_COMPLETE),
                eq(0),
                isNull(),
                any());
    }

    @Test
    public void testLogSharesheetDirectLoadTimeout() {
        mChooserLogger.logSharesheetDirectLoadTimeout();
        verify(mUiEventLog).logWithInstanceId(
                eq(SharesheetStandardEvent.SHARESHEET_DIRECT_LOAD_TIMEOUT), eq(0), isNull(), any());
    }

    @Test
    public void testLogSharesheetProfileChanged() {
        mChooserLogger.logSharesheetProfileChanged();
        verify(mUiEventLog).logWithInstanceId(
                eq(SharesheetStandardEvent.SHARESHEET_PROFILE_CHANGED), eq(0), isNull(), any());
    }

    @Test
    public void testLogSharesheetExpansionChanged_collapsed() {
        mChooserLogger.logSharesheetExpansionChanged(/* isCollapsed=*/ true);
        verify(mUiEventLog).logWithInstanceId(
                eq(SharesheetStandardEvent.SHARESHEET_COLLAPSED), eq(0), isNull(), any());
    }

    @Test
    public void testLogSharesheetExpansionChanged_expanded() {
        mChooserLogger.logSharesheetExpansionChanged(/* isCollapsed=*/ false);
        verify(mUiEventLog).logWithInstanceId(
                eq(SharesheetStandardEvent.SHARESHEET_EXPANDED), eq(0), isNull(), any());
    }

    @Test
    public void testLogSharesheetAppShareRankingTimeout() {
        mChooserLogger.logSharesheetAppShareRankingTimeout();
        verify(mUiEventLog).logWithInstanceId(
                eq(SharesheetStandardEvent.SHARESHEET_APP_SHARE_RANKING_TIMEOUT),
                eq(0),
                isNull(),
                any());
    }

    @Test
    public void testLogSharesheetEmptyDirectShareRow() {
        mChooserLogger.logSharesheetEmptyDirectShareRow();
        verify(mUiEventLog).logWithInstanceId(
                eq(SharesheetStandardEvent.SHARESHEET_EMPTY_DIRECT_SHARE_ROW),
                eq(0),
                isNull(),
                any());
    }

    @Test
    public void testDifferentLoggerInstancesUseDifferentInstanceIds() {
        ArgumentCaptor<Integer> idIntCaptor = ArgumentCaptor.forClass(Integer.class);
        EventLogImpl chooserLogger2 =
                new EventLogImpl(mUiEventLog, mFrameworkLog, mMetricsLogger,
                        mSequence.newInstanceId());

        final int targetType = EventLogImpl.SELECTION_TYPE_COPY;
        final String packageName = "com.test.foo";
        final int positionPicked = 123;
        final int directTargetAlsoRanked = -1;
        final int callerTargetCount = 0;
        final boolean isPinned = true;
        final boolean isSuccessfullySelected = true;
        final long selectionCost = 456;

        mChooserLogger.logShareTargetSelected(
                targetType,
                packageName,
                positionPicked,
                directTargetAlsoRanked,
                callerTargetCount,
                /* directTargetHashed= */ null,
                isPinned,
                isSuccessfullySelected,
                selectionCost);

        chooserLogger2.logShareTargetSelected(
                targetType,
                packageName,
                positionPicked,
                directTargetAlsoRanked,
                callerTargetCount,
                /* directTargetHashed= */ null,
                isPinned,
                isSuccessfullySelected,
                selectionCost);

        verify(mFrameworkLog, times(2)).write(
                anyInt(), anyInt(), anyString(), idIntCaptor.capture(), anyInt(), anyBoolean());

        int id1 = idIntCaptor.getAllValues().get(0);
        int id2 = idIntCaptor.getAllValues().get(1);

        assertThat(id1).isGreaterThan(0);
        assertThat(id2).isGreaterThan(0);
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    public void testUiAndFrameworkEventsUseSameInstanceIdForSameLoggerInstance() {
        ArgumentCaptor<Integer> idIntCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<InstanceId> idObjectCaptor = ArgumentCaptor.forClass(InstanceId.class);

        final int targetType = EventLogImpl.SELECTION_TYPE_COPY;
        final String packageName = "com.test.foo";
        final int positionPicked = 123;
        final int directTargetAlsoRanked = -1;
        final int callerTargetCount = 0;
        final boolean isPinned = true;
        final boolean isSuccessfullySelected = true;
        final long selectionCost = 456;

        mChooserLogger.logShareTargetSelected(
                targetType,
                packageName,
                positionPicked,
                directTargetAlsoRanked,
                callerTargetCount,
                /* directTargetHashed= */ null,
                isPinned,
                isSuccessfullySelected,
                selectionCost);

        verify(mFrameworkLog).write(
                anyInt(), anyInt(), anyString(), idIntCaptor.capture(), anyInt(), anyBoolean());

        mChooserLogger.logSharesheetTriggered();
        verify(mUiEventLog).logWithInstanceId(
                any(UiEventEnum.class), anyInt(), any(), idObjectCaptor.capture());

        assertThat(idIntCaptor.getValue()).isGreaterThan(0);
        assertThat(idObjectCaptor.getValue().getId()).isEqualTo(idIntCaptor.getValue());
    }

    @Test
    public void testTargetSelectionCategories() {
        assertThat(EventLogImpl.getTargetSelectionCategory(
                EventLogImpl.SELECTION_TYPE_SERVICE))
                        .isEqualTo(MetricsEvent.ACTION_ACTIVITY_CHOOSER_PICKED_SERVICE_TARGET);
        assertThat(EventLogImpl.getTargetSelectionCategory(
                EventLogImpl.SELECTION_TYPE_APP))
                        .isEqualTo(MetricsEvent.ACTION_ACTIVITY_CHOOSER_PICKED_APP_TARGET);
        assertThat(EventLogImpl.getTargetSelectionCategory(
                EventLogImpl.SELECTION_TYPE_STANDARD))
                        .isEqualTo(MetricsEvent.ACTION_ACTIVITY_CHOOSER_PICKED_STANDARD_TARGET);
        assertThat(EventLogImpl.getTargetSelectionCategory(
                EventLogImpl.SELECTION_TYPE_COPY)).isEqualTo(0);
        assertThat(EventLogImpl.getTargetSelectionCategory(
                EventLogImpl.SELECTION_TYPE_NEARBY)).isEqualTo(0);
        assertThat(EventLogImpl.getTargetSelectionCategory(
                EventLogImpl.SELECTION_TYPE_EDIT)).isEqualTo(0);
    }
}

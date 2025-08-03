/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.textclassifier.common.statsd;

import static com.google.common.truth.Truth.assertThat;

import android.os.Binder;
import android.os.Parcel;
import android.view.textclassifier.TextClassificationSessionId;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import com.android.internal.os.StatsdConfigProto.StatsdConfig;
import com.android.os.AtomsProto.Atom;
import com.android.os.AtomsProto.TextClassifierApiUsageReported;
import com.android.os.AtomsProto.TextClassifierApiUsageReported.ApiType;
import com.android.os.AtomsProto.TextClassifierApiUsageReported.ResultType;
import com.android.textclassifier.common.statsd.TextClassifierApiUsageLogger.Session;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class TextClassifierApiUsageLoggerTest {
  /** A statsd config ID, which is arbitrary. */
  private static final long CONFIG_ID = 689777;

  private static final long SHORT_TIMEOUT_MS = 1000;

  private static final String SESSION_ID = "abcdef";

  @Before
  public void setup() throws Exception {
    StatsdTestUtils.cleanup(CONFIG_ID);

    StatsdConfig.Builder builder =
        StatsdConfig.newBuilder()
            .setId(CONFIG_ID)
            .addAllowedLogSource(ApplicationProvider.getApplicationContext().getPackageName());
    StatsdTestUtils.addAtomMatcher(builder, Atom.TEXT_CLASSIFIER_API_USAGE_REPORTED_FIELD_NUMBER);
    StatsdTestUtils.pushConfig(builder.build());
  }

  @After
  public void tearDown() throws Exception {
    StatsdTestUtils.cleanup(CONFIG_ID);
  }

  @Test
  public void reportSuccess() throws Exception {
    TextClassifierApiUsageLogger textClassifierApiUsageLogger =
        new TextClassifierApiUsageLogger(
            /* sampleRateSupplier= */ () -> 1, MoreExecutors.directExecutor());
    Session session =
        textClassifierApiUsageLogger.createSession(
            TextClassifierApiUsageLogger.API_TYPE_SUGGEST_SELECTION,
            createTextClassificationSessionId());
    // so that the latency we log is greater than 0.
    Thread.sleep(10);
    session.reportSuccess();

    ImmutableList<Atom> loggedAtoms = StatsdTestUtils.getLoggedAtoms(CONFIG_ID, SHORT_TIMEOUT_MS);

    ImmutableList<TextClassifierApiUsageReported> loggedEvents =
        ImmutableList.copyOf(
            loggedAtoms.stream()
                .map(Atom::getTextClassifierApiUsageReported)
                .collect(Collectors.toList()));

    assertThat(loggedEvents).hasSize(1);
    TextClassifierApiUsageReported event = loggedEvents.get(0);
    assertThat(event.getApiType()).isEqualTo(ApiType.SUGGEST_SELECTION);
    assertThat(event.getResultType()).isEqualTo(ResultType.SUCCESS);
    assertThat(event.getLatencyMillis()).isGreaterThan(0L);
    assertThat(event.getSessionId()).isEqualTo(SESSION_ID);
  }

  @Test
  public void reportFailure() throws Exception {
    TextClassifierApiUsageLogger textClassifierApiUsageLogger =
        new TextClassifierApiUsageLogger(
            /* sampleRateSupplier= */ () -> 1, MoreExecutors.directExecutor());
    Session session =
        textClassifierApiUsageLogger.createSession(
            TextClassifierApiUsageLogger.API_TYPE_CLASSIFY_TEXT,
            createTextClassificationSessionId());
    // so that the latency we log is greater than 0.
    Thread.sleep(10);
    session.reportFailure();

    ImmutableList<Atom> loggedAtoms = StatsdTestUtils.getLoggedAtoms(CONFIG_ID, SHORT_TIMEOUT_MS);

    ImmutableList<TextClassifierApiUsageReported> loggedEvents =
        ImmutableList.copyOf(
            loggedAtoms.stream()
                .map(Atom::getTextClassifierApiUsageReported)
                .collect(Collectors.toList()));

    assertThat(loggedEvents).hasSize(1);
    TextClassifierApiUsageReported event = loggedEvents.get(0);
    assertThat(event.getApiType()).isEqualTo(ApiType.CLASSIFY_TEXT);
    assertThat(event.getResultType()).isEqualTo(ResultType.FAIL);
    assertThat(event.getLatencyMillis()).isGreaterThan(0L);
    assertThat(event.getSessionId()).isEqualTo(SESSION_ID);
  }

  @Test
  public void noLog_sampleRateZero() throws Exception {
    TextClassifierApiUsageLogger textClassifierApiUsageLogger =
        new TextClassifierApiUsageLogger(
            /* sampleRateSupplier= */ () -> 0, MoreExecutors.directExecutor());
    Session session =
        textClassifierApiUsageLogger.createSession(
            TextClassifierApiUsageLogger.API_TYPE_CLASSIFY_TEXT,
            createTextClassificationSessionId());
    session.reportSuccess();

    ImmutableList<Atom> loggedAtoms = StatsdTestUtils.getLoggedAtoms(CONFIG_ID, SHORT_TIMEOUT_MS);

    ImmutableList<TextClassifierApiUsageReported> loggedEvents =
        ImmutableList.copyOf(
            loggedAtoms.stream()
                .map(Atom::getTextClassifierApiUsageReported)
                .collect(Collectors.toList()));

    assertThat(loggedEvents).isEmpty();
  }

  private static TextClassificationSessionId createTextClassificationSessionId() {
    // Used a hack to create TextClassificationSessionId because its constructor is @hide.
    Parcel parcel = Parcel.obtain();
    parcel.writeString(SESSION_ID);
    parcel.writeStrongBinder(new Binder());
    parcel.setDataPosition(0);
    return TextClassificationSessionId.CREATOR.createFromParcel(parcel);
  }
}

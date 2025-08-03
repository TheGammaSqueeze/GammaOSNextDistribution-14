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

import android.util.Log;
import androidx.test.core.app.ApplicationProvider;
import com.android.internal.os.StatsdConfigProto.StatsdConfig;
import com.android.os.AtomsProto.Atom;
import com.android.os.AtomsProto.TextClassifierDownloadReported;
import com.android.os.AtomsProto.TextClassifierDownloadWorkCompleted;
import com.android.os.AtomsProto.TextClassifierDownloadWorkScheduled;
import com.google.common.collect.ImmutableList;
import java.util.stream.Collectors;
import org.junit.rules.ExternalResource;

// TODO(licha): Make this generic and useful for other atoms.
/** Test rule to set up/clean up statsd for download logger tests. */
public final class TextClassifierDownloadLoggerTestRule extends ExternalResource {
  private static final String TAG = "DownloadLoggerTestRule";

  // Statsd config IDs, which are arbitrary.
  private static final long CONFIG_ID_DOWNLOAD_REPORTED = 423779;
  private static final long CONFIG_ID_DOWNLOAD_WORK_SCHEDULED = 42;
  private static final long CONFIG_ID_DOWNLOAD_WORK_COMPLETED = 2021;

  private static final long SHORT_TIMEOUT_MS = 1000;

  @Override
  public void before() throws Exception {
    StatsdTestUtils.cleanup(CONFIG_ID_DOWNLOAD_REPORTED);
    StatsdTestUtils.cleanup(CONFIG_ID_DOWNLOAD_WORK_SCHEDULED);
    StatsdTestUtils.cleanup(CONFIG_ID_DOWNLOAD_WORK_COMPLETED);

    StatsdConfig.Builder builder1 =
        StatsdConfig.newBuilder()
            .setId(CONFIG_ID_DOWNLOAD_REPORTED)
            .addAllowedLogSource(ApplicationProvider.getApplicationContext().getPackageName());
    StatsdTestUtils.addAtomMatcher(builder1, Atom.TEXT_CLASSIFIER_DOWNLOAD_REPORTED_FIELD_NUMBER);
    StatsdTestUtils.pushConfig(builder1.build());

    StatsdConfig.Builder builder2 =
        StatsdConfig.newBuilder()
            .setId(CONFIG_ID_DOWNLOAD_WORK_SCHEDULED)
            .addAllowedLogSource(ApplicationProvider.getApplicationContext().getPackageName());
    StatsdTestUtils.addAtomMatcher(
        builder2, Atom.TEXT_CLASSIFIER_DOWNLOAD_WORK_SCHEDULED_FIELD_NUMBER);
    StatsdTestUtils.pushConfig(builder2.build());

    StatsdConfig.Builder builder3 =
        StatsdConfig.newBuilder()
            .setId(CONFIG_ID_DOWNLOAD_WORK_COMPLETED)
            .addAllowedLogSource(ApplicationProvider.getApplicationContext().getPackageName());
    StatsdTestUtils.addAtomMatcher(
        builder3, Atom.TEXT_CLASSIFIER_DOWNLOAD_WORK_COMPLETED_FIELD_NUMBER);
    StatsdTestUtils.pushConfig(builder3.build());
  }

  @Override
  public void after() {
    try {
      StatsdTestUtils.cleanup(CONFIG_ID_DOWNLOAD_REPORTED);
      StatsdTestUtils.cleanup(CONFIG_ID_DOWNLOAD_WORK_SCHEDULED);
      StatsdTestUtils.cleanup(CONFIG_ID_DOWNLOAD_WORK_COMPLETED);
    } catch (Exception e) {
      Log.e(TAG, "Failed to clean up statsd after tests.");
    }
  }

  /**
   * Gets a list of TextClassifierDownloadReported atoms written into statsd, sorted by increasing
   * timestamp.
   */
  public ImmutableList<TextClassifierDownloadReported> getLoggedDownloadReportedAtoms()
      throws Exception {
    ImmutableList<Atom> loggedAtoms =
        StatsdTestUtils.getLoggedAtoms(CONFIG_ID_DOWNLOAD_REPORTED, SHORT_TIMEOUT_MS);
    return ImmutableList.copyOf(
        loggedAtoms.stream()
            .filter(Atom::hasTextClassifierDownloadReported)
            .map(Atom::getTextClassifierDownloadReported)
            .collect(Collectors.toList()));
  }

  /**
   * Gets a list of TextClassifierDownloadWorkScheduled atoms written into statsd, sorted by
   * increasing timestamp.
   */
  public ImmutableList<TextClassifierDownloadWorkScheduled> getLoggedDownloadWorkScheduledAtoms()
      throws Exception {
    ImmutableList<Atom> loggedAtoms =
        StatsdTestUtils.getLoggedAtoms(CONFIG_ID_DOWNLOAD_WORK_SCHEDULED, SHORT_TIMEOUT_MS);
    return ImmutableList.copyOf(
        loggedAtoms.stream()
            .filter(Atom::hasTextClassifierDownloadWorkScheduled)
            .map(Atom::getTextClassifierDownloadWorkScheduled)
            .collect(Collectors.toList()));
  }

  /**
   * Gets a list of TextClassifierDownloadWorkCompleted atoms written into statsd, sorted by
   * increasing timestamp.
   */
  public ImmutableList<TextClassifierDownloadWorkCompleted> getLoggedDownloadWorkCompletedAtoms()
      throws Exception {
    ImmutableList<Atom> loggedAtoms =
        StatsdTestUtils.getLoggedAtoms(CONFIG_ID_DOWNLOAD_WORK_COMPLETED, SHORT_TIMEOUT_MS);
    return ImmutableList.copyOf(
        loggedAtoms.stream()
            .filter(Atom::hasTextClassifierDownloadWorkCompleted)
            .map(Atom::getTextClassifierDownloadWorkCompleted)
            .collect(Collectors.toList()));
  }
}

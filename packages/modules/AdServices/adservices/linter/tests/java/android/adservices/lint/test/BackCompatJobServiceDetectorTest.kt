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

package android.adservices.lint.test

import android.adservices.lint.BackCompatJobServiceDetector
import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class BackCompatJobServiceDetectorTest : LintDetectorTest() {
    override fun getDetector(): Detector = BackCompatJobServiceDetector()

    override fun getIssues(): List<Issue> = listOf(BackCompatJobServiceDetector.ISSUE)

    override fun lint(): TestLintTask = super.lint().allowMissingSdk(true)

    @Test
    fun validJobService_safeClass_doesNotThrow() {
        lint().files(
            java(
                """
package com.android.adservices.service.topics;

import com.android.adservices.spe.AdServicesJobInfo;
import android.app.job.JobParameters;
import android.app.job.JobService;


public final class EpochJobService extends JobService {
    @SuppressWarnings("InvalidAdServicesJobService")
    private static final int TOPICS_EPOCH_JOB_ID = AdServicesJobInfo.TOPICS_EPOCH_JOB.getJobId();
    
    @Override
    public boolean onStartJob(JobParameters params) {
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
                """
            ), *stubs
        ).run().expectClean()
    }

    @Test
    fun validJobService_literalExpression_doesNotThrow() {
        lint().files(
                java("""
package com.android.adservices.service.topics;

import android.app.job.JobParameters;
import android.app.job.JobService;


public final class EpochJobService extends JobService {
    private static final int jobTimeout = 2 * 3L * 100;
    @Override
    public boolean onStartJob(JobParameters params) {
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
                """), *stubs).run().expectClean()
    }


    @Test
    fun invalidJobService_noneJobService_doesNotThrow() {
        lint().files(java("""
package com.android.adservices.service.topics;

import android.app.job.JobParameters;
import com.android.adservices.LoggerFactory;

public final class EpochJobService {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getTopicsLogger();
    public boolean onStartJob(JobParameters params) {
        return true;
    }

    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
                """).indented(),
                *stubs
        )
                .run().expectClean()
    }

    @Test
    fun invalidJobService_noneFieldInitializer_doesNotThrow() {
        lint().files(java("""
package com.android.adservices.service.topics;

import android.app.job.JobParameters;
import android.app.job.JobService;
import com.android.adservices.LoggerFactory;

public final class EpochJobService extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
        final LoggerFactory.Logger sLogger = LoggerFactory.getTopicsLogger();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
                """).indented(),
                *stubs
        )
                .run().expectClean()
    }

    @Test
    fun invalidJobService_throws() {
        lint().files(java("""
package com.android.adservices.service.topics;

import android.app.job.JobParameters;
import android.app.job.JobService;
import com.android.adservices.LoggerFactory;

public final class EpochJobService extends JobService {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getTopicsLogger();
    @Override
    public boolean onStartJob(JobParameters params) {
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
                """).indented(),
                *stubs
        )
                .issues(BackCompatJobServiceDetector.ISSUE)
                .run()
                .expect("src/com/android/adservices/service/topics/EpochJobService.java:8: Warning:" +
                        " Avoid using new classes in AdServices JobService field Initializers." +
                        " Due to the fact that ExtServices can OTA to any AdServices build," +
                        " JobServices code needs to be properly gated to avoid" +
                        " NoClassDefFoundError. NoClassDefFoundError can happen when new class" +
                        " is used in ExtServices build, and the error happens when the device" +
                        " OTA to old AdServices build on T which does not contain the new class" +
                        " definition (go/rbc-jobservice-lint). [InvalidAdServicesJobService]\n" +
                        "    private static final LoggerFactory.Logger sLogger =" +
                        " LoggerFactory.getTopicsLogger();\n" +
                        "                                                       " +
                        " ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                        "0 errors, 1 warnings")
    }

    @Test
    fun invalidJobService_getLogger_throws() {
        lint().files(java("""
package com.android.adservices.service.topics;

import android.app.job.JobParameters;
import android.app.job.JobService;
import com.android.adservices.LoggerFactory;

public final class EpochJobService extends JobService {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    @Override
    public boolean onStartJob(JobParameters params) {
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
                """).indented(),
                *stubs
        )
                .issues(BackCompatJobServiceDetector.ISSUE)
                .run()
                .expect("src/com/android/adservices/service/topics/EpochJobService.java:8: Warning:" +
                        " Avoid using new classes in AdServices JobService field Initializers." +
                        " Due to the fact that ExtServices can OTA to any AdServices build," +
                        " JobServices code needs to be properly gated to avoid" +
                        " NoClassDefFoundError. NoClassDefFoundError can happen when new class" +
                        " is used in ExtServices build, and the error happens when the device" +
                        " OTA to old AdServices build on T which does not contain the new class" +
                        " definition (go/rbc-jobservice-lint). [InvalidAdServicesJobService]\n" +
                        "    private static final LoggerFactory.Logger sLogger =" +
                        " LoggerFactory.getLogger();\n" +
                        "                                                       " +
                        " ~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                        "0 errors, 1 warnings")
    }

    private val jobParameters: TestFile =
            java(
                    """
            package android.app.job;
            public class JobParameters {
            }
        """
            )
                    .indented()

    private val jobService: TestFile =
            java(
                    """
            package android.app.job;
            public class JobService {
                public abstract boolean onStartJob(JobParameters params);
                public abstract boolean onStopJob(JobParameters params);
            }
        """
            )
                    .indented()

    private val adservicesJobInfo: TestFile =
            java(
                    """
            package com.android.adservices.spe;
            public enum AdServicesJobInfo {
                TOPICS_EPOCH_JOB("TOPICS_EPOCH_JOB", 2);
                private final String mJobServiceName;
                private final int mJobId;

                AdServicesJobInfo(String jobServiceName, int jobId) {
                    mJobServiceName = jobServiceName;
                    mJobId = jobId;
                }

                public int getJobId() {
                    return mJobId;
                }
            }
        """
            )
                    .indented()

    private val loggerFactory: TestFile =
            java(
                    """
            package com.android.adservices;
            public class LoggerFactory {
                public static Logger getTopicsLogger() {
                    return null;
                }

                public static Logger getLogger() {
                    return null;
                }
            }
        """
            )
                    .indented()

    private val stubs =
            arrayOf(
                    jobParameters,
                    jobService,
                    adservicesJobInfo,
                    loggerFactory,
            )
}
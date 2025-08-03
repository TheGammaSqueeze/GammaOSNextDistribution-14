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

package com.android.server.appsearch;

import static com.google.common.truth.Truth.assertThat;

import android.app.appsearch.AppSearchEnvironmentFactory;

import com.android.server.appsearch.external.localstorage.stats.CallStats;
import com.android.server.appsearch.util.RateLimitedExecutor;

import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class RateLimitedExecutorTest {
    private final ExecutorService mExecutorService =
            AppSearchEnvironmentFactory.getEnvironmentInstance().createExecutorService(
                    /*corePoolSize=*/ Runtime.getRuntime().availableProcessors(),
                    /*maxConcurrency=*/ Runtime.getRuntime().availableProcessors(),
                    /*keepAliveTime=*/ 0L,
                    /*unit=*/ TimeUnit.SECONDS,
                    /*workQueue=*/ new LinkedBlockingQueue<>(),
                    /*priority=*/ 0);

    @Test
    public void testAddTaskToQueue_addOk() {
        AppSearchRateLimitConfig rateLimitConfig = AppSearchRateLimitConfig.create(100, 0.9f,
                "localPutDocuments:5;localGetDocuments:40;localSetSchema:99");
        RateLimitedExecutor rateLimitedExecutor = new RateLimitedExecutor(mExecutorService,
                rateLimitConfig);
        String pkgName = "pkgName";

        assertThat(rateLimitConfig.getApiCost(CallStats.CALL_TYPE_PUT_DOCUMENTS)).isEqualTo(5);
        assertThat(rateLimitConfig.getApiCost(CallStats.CALL_TYPE_GET_DOCUMENTS)).isEqualTo(40);
        assertThat(rateLimitConfig.getApiCost(CallStats.CALL_TYPE_SET_SCHEMA)).isEqualTo(99);

        assertThat(rateLimitedExecutor.addTaskToQueue(pkgName,
                CallStats.CALL_TYPE_PUT_DOCUMENTS)).isTrue();
        assertThat(rateLimitedExecutor.addTaskToQueue(pkgName,
                CallStats.CALL_TYPE_GET_DOCUMENTS)).isTrue();
        assertThat(rateLimitedExecutor.addTaskToQueue(pkgName,
                CallStats.CALL_TYPE_GET_DOCUMENTS)).isTrue();
        assertThat(rateLimitedExecutor.addTaskToQueue(pkgName,
                CallStats.CALL_TYPE_PUT_DOCUMENTS)).isTrue();
        assertThat(rateLimitedExecutor.getTaskQueueSize()).isEqualTo(4);
        assertThat(rateLimitedExecutor.getPerPackageTaskCosts().get(pkgName).mTaskCount).isEqualTo(
                4);
        assertThat(rateLimitedExecutor.getPerPackageTaskCosts().get(pkgName).mTotalTaskCost)
                .isEqualTo(90);
    }

    @Test
    public void testAddTaskToQueue_exceedsPerPackageCapacity_cannotAddTask() {
        AppSearchRateLimitConfig rateLimitConfig = AppSearchRateLimitConfig.create(100, 0.9f,
                "localPutDocuments:5;localGetDocuments:40;localSetSchema:99");
        RateLimitedExecutor rateLimitedExecutor = new RateLimitedExecutor(mExecutorService,
                rateLimitConfig);
        String pkgName = "pkgName";

        // Cannot add task with cost that exceeds the per-package capacity.
        assertThat(rateLimitedExecutor.addTaskToQueue(pkgName,
                CallStats.CALL_TYPE_SET_SCHEMA)).isFalse();
        assertThat(rateLimitedExecutor.getTaskQueueSize()).isEqualTo(0);
        assertThat(rateLimitedExecutor.getPerPackageTaskCosts().get(pkgName)).isNull();

        // Add some tasks to fill up the task queue
        assertThat(rateLimitedExecutor.addTaskToQueue(pkgName,
                CallStats.CALL_TYPE_PUT_DOCUMENTS)).isTrue();
        assertThat(rateLimitedExecutor.addTaskToQueue(pkgName,
                CallStats.CALL_TYPE_GET_DOCUMENTS)).isTrue();
        assertThat(rateLimitedExecutor.addTaskToQueue(pkgName,
                CallStats.CALL_TYPE_GET_DOCUMENTS)).isTrue();
        assertThat(rateLimitedExecutor.addTaskToQueue(pkgName,
                CallStats.CALL_TYPE_PUT_DOCUMENTS)).isTrue();
        assertThat(rateLimitedExecutor.getTaskQueueSize()).isEqualTo(4);
        assertThat(
                rateLimitedExecutor.getPerPackageTaskCosts().get(pkgName).mTotalTaskCost).isEqualTo(
                90);

        // Can no longer add tasks once per-package capacity is full.
        assertThat(
                rateLimitedExecutor.addTaskToQueue(pkgName, CallStats.CALL_TYPE_SEARCH)).isFalse();
        assertThat(rateLimitedExecutor.getTaskQueueSize()).isEqualTo(4);
        assertThat(rateLimitedExecutor.getPerPackageTaskCosts().get(pkgName).mTaskCount).isEqualTo(
                4);
        assertThat(
                rateLimitedExecutor.getPerPackageTaskCosts().get(pkgName).mTotalTaskCost).isEqualTo(
                90);
    }

    @Test
    public void testAddTaskToQueue_otherPackageExceedsPerPackageCapacity_canAddTask() {
        AppSearchRateLimitConfig rateLimitConfig = AppSearchRateLimitConfig.create(100, 0.9f,
                "localPutDocuments:5;localGetDocuments:40;localSetSchema:99");
        RateLimitedExecutor rateLimitedExecutor = new RateLimitedExecutor(mExecutorService,
                rateLimitConfig);
        String pkgName1 = "pkgName1";
        String pkgName2 = "pkgName2";

        // Add some tasks to fill up pkgName1 capacity
        assertThat(rateLimitedExecutor.addTaskToQueue(pkgName1,
                CallStats.CALL_TYPE_PUT_DOCUMENTS)).isTrue();
        assertThat(rateLimitedExecutor.addTaskToQueue(pkgName1,
                CallStats.CALL_TYPE_GET_DOCUMENTS)).isTrue();
        assertThat(rateLimitedExecutor.addTaskToQueue(pkgName1,
                CallStats.CALL_TYPE_GET_DOCUMENTS)).isTrue();
        assertThat(rateLimitedExecutor.addTaskToQueue(pkgName1,
                CallStats.CALL_TYPE_PUT_DOCUMENTS)).isTrue();
        assertThat(rateLimitedExecutor.getPerPackageTaskCosts().get(pkgName1).mTotalTaskCost)
                .isEqualTo(90);

        // Adding task to pkgName2 is ok
        assertThat(rateLimitedExecutor.addTaskToQueue(pkgName2,
                CallStats.CALL_TYPE_SEARCH)).isTrue();
        assertThat(rateLimitedExecutor.addTaskToQueue(pkgName2,
                CallStats.CALL_TYPE_PUT_DOCUMENTS)).isTrue();
        assertThat(rateLimitedExecutor.getTaskQueueSize()).isEqualTo(6);
        assertThat(rateLimitedExecutor.getPerPackageTaskCosts().get(pkgName1).mTaskCount).isEqualTo(
                4);
        assertThat(rateLimitedExecutor.getPerPackageTaskCosts().get(pkgName1).mTotalTaskCost)
                .isEqualTo(90);
        assertThat(rateLimitedExecutor.getPerPackageTaskCosts().get(pkgName2).mTaskCount).isEqualTo(
                2);
        assertThat(rateLimitedExecutor.getPerPackageTaskCosts().get(pkgName2).mTotalTaskCost)
                .isEqualTo(6);
    }

    @Test
    public void testAddTaskToQueue_exceedsTotalCapacity_cannotAddTask() {
        AppSearchRateLimitConfig rateLimitConfig = AppSearchRateLimitConfig.create(100, 0.9f,
                "localPutDocuments:5;localGetDocuments:40;localSetSchema:99");
        RateLimitedExecutor rateLimitedExecutor = new RateLimitedExecutor(mExecutorService,
                rateLimitConfig);
        String pkgName1 = "pkgName1";
        String pkgName2 = "pkgName2";

        // Add some tasks to fill up task queue capacity
        assertThat(rateLimitedExecutor.addTaskToQueue(pkgName1,
                CallStats.CALL_TYPE_PUT_DOCUMENTS)).isTrue();
        assertThat(rateLimitedExecutor.addTaskToQueue(pkgName1,
                CallStats.CALL_TYPE_GET_DOCUMENTS)).isTrue();
        assertThat(rateLimitedExecutor.addTaskToQueue(pkgName1,
                CallStats.CALL_TYPE_GET_DOCUMENTS)).isTrue();
        assertThat(rateLimitedExecutor.addTaskToQueue(pkgName1,
                CallStats.CALL_TYPE_PUT_DOCUMENTS)).isTrue();
        assertThat(rateLimitedExecutor.addTaskToQueue(pkgName2,
                CallStats.CALL_TYPE_SEARCH)).isTrue();
        assertThat(rateLimitedExecutor.addTaskToQueue(pkgName2,
                CallStats.CALL_TYPE_PUT_DOCUMENTS)).isTrue();
        assertThat(rateLimitedExecutor.getPerPackageTaskCosts().get(pkgName1).mTotalTaskCost)
                .isEqualTo(90);
        assertThat(rateLimitedExecutor.getPerPackageTaskCosts().get(pkgName2).mTotalTaskCost)
                .isEqualTo(6);

        // Can no longer add to either package once API costs exceeds total capacity
        assertThat(rateLimitedExecutor.addTaskToQueue(pkgName1,
                CallStats.CALL_TYPE_PUT_DOCUMENTS)).isFalse();
        assertThat(rateLimitedExecutor.addTaskToQueue(pkgName2,
                CallStats.CALL_TYPE_PUT_DOCUMENTS)).isFalse();
        assertThat(rateLimitedExecutor.getTaskQueueSize()).isEqualTo(6);
        assertThat(rateLimitedExecutor.getPerPackageTaskCosts().get(pkgName1).mTaskCount).isEqualTo(
                4);
        assertThat(rateLimitedExecutor.getPerPackageTaskCosts().get(pkgName1).mTotalTaskCost)
                .isEqualTo(90);
        assertThat(rateLimitedExecutor.getPerPackageTaskCosts().get(pkgName2).mTaskCount).isEqualTo(
                2);
        assertThat(rateLimitedExecutor.getPerPackageTaskCosts().get(pkgName2).mTotalTaskCost)
                .isEqualTo(6);
    }

    @Test
    public void testRemoveTaskFromQueue_removeOk() {
        AppSearchRateLimitConfig rateLimitConfig = AppSearchRateLimitConfig.create(100, 0.9f,
                "localPutDocuments:5;localGetDocuments:40;localSetSchema:99");
        RateLimitedExecutor rateLimitedExecutor = new RateLimitedExecutor(mExecutorService,
                rateLimitConfig);
        String pkgName1 = "pkgName1";
        String pkgName2 = "pkgName2";

        // Add some tasks to fill up the task queue
        assertThat(rateLimitedExecutor.addTaskToQueue(pkgName1,
                CallStats.CALL_TYPE_PUT_DOCUMENTS)).isTrue();
        assertThat(rateLimitedExecutor.addTaskToQueue(pkgName1,
                CallStats.CALL_TYPE_PUT_DOCUMENTS)).isTrue();
        assertThat(rateLimitedExecutor.addTaskToQueue(pkgName1,
                CallStats.CALL_TYPE_GET_DOCUMENTS)).isTrue();
        assertThat(rateLimitedExecutor.addTaskToQueue(pkgName2,
                CallStats.CALL_TYPE_PUT_DOCUMENTS)).isTrue();
        assertThat(rateLimitedExecutor.addTaskToQueue(pkgName2,
                CallStats.CALL_TYPE_PUT_DOCUMENTS)).isTrue();
        assertThat(rateLimitedExecutor.addTaskToQueue(pkgName2,
                CallStats.CALL_TYPE_GET_DOCUMENTS)).isTrue();
        assertThat(rateLimitedExecutor.getTaskQueueSize()).isEqualTo(6);
        assertThat(rateLimitedExecutor.getPerPackageTaskCosts().get(pkgName1).mTotalTaskCost)
                .isEqualTo(50);
        assertThat(rateLimitedExecutor.getPerPackageTaskCosts().get(pkgName2).mTotalTaskCost)
                .isEqualTo(50);

        rateLimitedExecutor.removeTaskFromQueue(pkgName1,
                CallStats.CALL_TYPE_PUT_DOCUMENTS);
        rateLimitedExecutor.removeTaskFromQueue(pkgName1,
                CallStats.CALL_TYPE_GET_DOCUMENTS);
        rateLimitedExecutor.removeTaskFromQueue(pkgName2,
                CallStats.CALL_TYPE_GET_DOCUMENTS);
        assertThat(rateLimitedExecutor.getTaskQueueSize()).isEqualTo(3);
        assertThat(rateLimitedExecutor.getPerPackageTaskCosts().get(pkgName1).mTaskCount)
                .isEqualTo(1);
        assertThat(rateLimitedExecutor.getPerPackageTaskCosts().get(pkgName1).mTotalTaskCost)
                .isEqualTo(5);
        assertThat(rateLimitedExecutor.getPerPackageTaskCosts().get(pkgName2).mTaskCount)
                .isEqualTo(2);
        assertThat(rateLimitedExecutor.getPerPackageTaskCosts().get(pkgName2).mTotalTaskCost)
                .isEqualTo(10);
    }

    @Test
    public void testCanAddMoreTasksAfterQueueClearsUp() {
        AppSearchRateLimitConfig rateLimitConfig = AppSearchRateLimitConfig.create(100, 0.9f,
                "localPutDocuments:5;localGetDocuments:40;localSetSchema:99");
        RateLimitedExecutor rateLimitedExecutor = new RateLimitedExecutor(mExecutorService,
                rateLimitConfig);
        String pkgName1 = "pkgName1";
        String pkgName2 = "pkgName2";

        // Add some tasks to fill up the task queue
        assertThat(rateLimitedExecutor.addTaskToQueue(pkgName1,
                CallStats.CALL_TYPE_PUT_DOCUMENTS)).isTrue();
        assertThat(rateLimitedExecutor.addTaskToQueue(pkgName1,
                CallStats.CALL_TYPE_PUT_DOCUMENTS)).isTrue();
        assertThat(rateLimitedExecutor.addTaskToQueue(pkgName1,
                CallStats.CALL_TYPE_GET_DOCUMENTS)).isTrue();
        assertThat(rateLimitedExecutor.addTaskToQueue(pkgName2,
                CallStats.CALL_TYPE_PUT_DOCUMENTS)).isTrue();
        assertThat(rateLimitedExecutor.addTaskToQueue(pkgName2,
                CallStats.CALL_TYPE_PUT_DOCUMENTS)).isTrue();
        assertThat(rateLimitedExecutor.addTaskToQueue(pkgName2,
                CallStats.CALL_TYPE_GET_DOCUMENTS)).isTrue();
        assertThat(rateLimitedExecutor.getTaskQueueSize()).isEqualTo(6);
        assertThat(rateLimitedExecutor.getPerPackageTaskCosts().get(pkgName1).mTotalTaskCost)
                .isEqualTo(50);
        assertThat(rateLimitedExecutor.getPerPackageTaskCosts().get(pkgName2).mTotalTaskCost)
                .isEqualTo(50);

        // Remove some tasks to clear up the queue
        rateLimitedExecutor.removeTaskFromQueue(pkgName1,
                CallStats.CALL_TYPE_PUT_DOCUMENTS);
        rateLimitedExecutor.removeTaskFromQueue(pkgName1,
                CallStats.CALL_TYPE_GET_DOCUMENTS);
        rateLimitedExecutor.removeTaskFromQueue(pkgName2,
                CallStats.CALL_TYPE_GET_DOCUMENTS);
        assertThat(rateLimitedExecutor.getTaskQueueSize()).isEqualTo(3);
        assertThat(rateLimitedExecutor.getPerPackageTaskCosts().get(pkgName1).mTotalTaskCost)
                .isEqualTo(5);
        assertThat(rateLimitedExecutor.getPerPackageTaskCosts().get(pkgName2).mTotalTaskCost)
                .isEqualTo(10);

        // Can add more tasks now that queue has cleared up a little
        assertThat(rateLimitedExecutor.addTaskToQueue(pkgName2,
                CallStats.CALL_TYPE_GET_DOCUMENTS)).isTrue();
        assertThat(rateLimitedExecutor.getTaskQueueSize()).isEqualTo(4);
        assertThat(rateLimitedExecutor.getPerPackageTaskCosts().get(pkgName1).mTaskCount)
                .isEqualTo(1);
        assertThat(rateLimitedExecutor.getPerPackageTaskCosts().get(pkgName1).mTotalTaskCost)
                .isEqualTo(5);
        assertThat(rateLimitedExecutor.getPerPackageTaskCosts().get(pkgName2).mTaskCount)
                .isEqualTo(3);
        assertThat(rateLimitedExecutor.getPerPackageTaskCosts().get(pkgName2).mTotalTaskCost)
                .isEqualTo(50);
    }
}

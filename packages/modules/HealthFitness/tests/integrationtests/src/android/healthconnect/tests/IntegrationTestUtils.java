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

package android.healthconnect.tests;

import static com.google.common.truth.Truth.assertThat;

import static java.util.concurrent.Executors.newSingleThreadExecutor;

import android.content.Context;
import android.health.connect.HealthConnectManager;
import android.health.connect.migration.MigrationEntity;
import android.health.connect.migration.MigrationException;
import android.healthconnect.test.app.BlockingOutcomeReceiver;
import android.os.OutcomeReceiver;

import androidx.test.core.app.ApplicationProvider;

import java.util.List;
import java.util.concurrent.Executor;

/** Utils for permission tests. */
public final class IntegrationTestUtils {

    private IntegrationTestUtils() {}

    /**
     * Calls {@link HealthConnectManager#startMigration(Executor, OutcomeReceiver)} and waits for
     * the call to finish.
     */
    public static void startMigration() {
        BlockingOutcomeReceiver<Void, MigrationException> outcome = new BlockingOutcomeReceiver<>();
        getHealthConnectManager().startMigration(newSingleThreadExecutor(), outcome);
        outcome.awaitSuccess();
    }

    /**
     * Calls {@link HealthConnectManager#writeMigrationData(List, Executor, OutcomeReceiver)} and
     * waits for the call to finish.
     */
    public static void writeMigrationData(List<MigrationEntity> entities) {
        BlockingOutcomeReceiver<Void, MigrationException> outcome = new BlockingOutcomeReceiver<>();
        getHealthConnectManager().writeMigrationData(entities, newSingleThreadExecutor(), outcome);
        outcome.awaitSuccess();
    }

    /**
     * Calls {@link HealthConnectManager#finishMigration(Executor, OutcomeReceiver)} and waits for
     * the call to finish.
     */
    public static void finishMigration() {
        BlockingOutcomeReceiver<Void, MigrationException> outcome = new BlockingOutcomeReceiver<>();
        getHealthConnectManager().finishMigration(newSingleThreadExecutor(), outcome);
        outcome.awaitSuccess();
    }

    private static HealthConnectManager getHealthConnectManager() {
        Context context = ApplicationProvider.getApplicationContext();
        HealthConnectManager service = context.getSystemService(HealthConnectManager.class);
        assertThat(service).isNotNull();

        return service;
    }
}

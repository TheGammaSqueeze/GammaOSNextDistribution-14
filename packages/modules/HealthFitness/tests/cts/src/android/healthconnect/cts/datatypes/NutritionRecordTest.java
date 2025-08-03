/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.healthconnect.cts.datatypes;

import static android.health.connect.datatypes.NutritionRecord.BIOTIN_TOTAL;
import static android.health.connect.datatypes.NutritionRecord.CAFFEINE_TOTAL;
import static android.health.connect.datatypes.NutritionRecord.CALCIUM_TOTAL;
import static android.health.connect.datatypes.NutritionRecord.CHLORIDE_TOTAL;
import static android.health.connect.datatypes.NutritionRecord.CHOLESTEROL_TOTAL;
import static android.health.connect.datatypes.NutritionRecord.CHROMIUM_TOTAL;
import static android.health.connect.datatypes.NutritionRecord.COPPER_TOTAL;
import static android.health.connect.datatypes.NutritionRecord.DIETARY_FIBER_TOTAL;
import static android.health.connect.datatypes.NutritionRecord.ENERGY_FROM_FAT_TOTAL;
import static android.health.connect.datatypes.NutritionRecord.ENERGY_TOTAL;
import static android.health.connect.datatypes.NutritionRecord.FOLATE_TOTAL;
import static android.health.connect.datatypes.NutritionRecord.FOLIC_ACID_TOTAL;
import static android.health.connect.datatypes.NutritionRecord.IODINE_TOTAL;
import static android.health.connect.datatypes.NutritionRecord.IRON_TOTAL;
import static android.health.connect.datatypes.NutritionRecord.MAGNESIUM_TOTAL;
import static android.health.connect.datatypes.NutritionRecord.MANGANESE_TOTAL;
import static android.health.connect.datatypes.NutritionRecord.MOLYBDENUM_TOTAL;
import static android.health.connect.datatypes.NutritionRecord.MONOUNSATURATED_FAT_TOTAL;
import static android.health.connect.datatypes.NutritionRecord.NIACIN_TOTAL;
import static android.health.connect.datatypes.NutritionRecord.PANTOTHENIC_ACID_TOTAL;
import static android.health.connect.datatypes.NutritionRecord.PHOSPHORUS_TOTAL;
import static android.health.connect.datatypes.NutritionRecord.POLYUNSATURATED_FAT_TOTAL;
import static android.health.connect.datatypes.NutritionRecord.POTASSIUM_TOTAL;
import static android.health.connect.datatypes.NutritionRecord.PROTEIN_TOTAL;
import static android.health.connect.datatypes.NutritionRecord.RIBOFLAVIN_TOTAL;
import static android.health.connect.datatypes.NutritionRecord.SATURATED_FAT_TOTAL;
import static android.health.connect.datatypes.NutritionRecord.SELENIUM_TOTAL;
import static android.health.connect.datatypes.NutritionRecord.SODIUM_TOTAL;
import static android.health.connect.datatypes.NutritionRecord.SUGAR_TOTAL;
import static android.health.connect.datatypes.NutritionRecord.THIAMIN_TOTAL;
import static android.health.connect.datatypes.NutritionRecord.TOTAL_CARBOHYDRATE_TOTAL;
import static android.health.connect.datatypes.NutritionRecord.TOTAL_FAT_TOTAL;
import static android.health.connect.datatypes.NutritionRecord.TRANS_FAT_TOTAL;
import static android.health.connect.datatypes.NutritionRecord.UNSATURATED_FAT_TOTAL;
import static android.health.connect.datatypes.NutritionRecord.VITAMIN_A_TOTAL;
import static android.health.connect.datatypes.NutritionRecord.VITAMIN_B12_TOTAL;
import static android.health.connect.datatypes.NutritionRecord.VITAMIN_B6_TOTAL;
import static android.health.connect.datatypes.NutritionRecord.VITAMIN_C_TOTAL;
import static android.health.connect.datatypes.NutritionRecord.VITAMIN_D_TOTAL;
import static android.health.connect.datatypes.NutritionRecord.VITAMIN_E_TOTAL;
import static android.health.connect.datatypes.NutritionRecord.VITAMIN_K_TOTAL;
import static android.health.connect.datatypes.NutritionRecord.ZINC_TOTAL;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.health.connect.AggregateRecordsRequest;
import android.health.connect.AggregateRecordsResponse;
import android.health.connect.DeleteUsingFiltersRequest;
import android.health.connect.HealthConnectException;
import android.health.connect.HealthDataCategory;
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.ReadRecordsRequestUsingIds;
import android.health.connect.RecordIdFilter;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.changelog.ChangeLogTokenRequest;
import android.health.connect.changelog.ChangeLogTokenResponse;
import android.health.connect.changelog.ChangeLogsRequest;
import android.health.connect.changelog.ChangeLogsResponse;
import android.health.connect.datatypes.AggregationType;
import android.health.connect.datatypes.DataOrigin;
import android.health.connect.datatypes.Device;
import android.health.connect.datatypes.Metadata;
import android.health.connect.datatypes.NutritionRecord;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.units.Energy;
import android.health.connect.datatypes.units.Mass;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.TestUtils;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public class NutritionRecordTest {
    private static final String TAG = "NutritionRecordTest";
    private static final String PACKAGE_NAME = "android.healthconnect.cts";

    private List<AggregationType<Mass>> mMassAggregateTypesList =
            Arrays.asList(
                    BIOTIN_TOTAL,
                    CAFFEINE_TOTAL,
                    CALCIUM_TOTAL,
                    CHLORIDE_TOTAL,
                    CHOLESTEROL_TOTAL,
                    CHROMIUM_TOTAL,
                    COPPER_TOTAL,
                    DIETARY_FIBER_TOTAL,
                    FOLATE_TOTAL,
                    FOLIC_ACID_TOTAL,
                    IODINE_TOTAL,
                    IRON_TOTAL,
                    MAGNESIUM_TOTAL,
                    MANGANESE_TOTAL,
                    MOLYBDENUM_TOTAL,
                    MONOUNSATURATED_FAT_TOTAL,
                    NIACIN_TOTAL,
                    PANTOTHENIC_ACID_TOTAL,
                    PHOSPHORUS_TOTAL,
                    POLYUNSATURATED_FAT_TOTAL,
                    POTASSIUM_TOTAL,
                    PROTEIN_TOTAL,
                    RIBOFLAVIN_TOTAL,
                    SATURATED_FAT_TOTAL,
                    SELENIUM_TOTAL,
                    SODIUM_TOTAL,
                    SUGAR_TOTAL,
                    THIAMIN_TOTAL,
                    TOTAL_CARBOHYDRATE_TOTAL,
                    TRANS_FAT_TOTAL,
                    TOTAL_FAT_TOTAL,
                    UNSATURATED_FAT_TOTAL,
                    VITAMIN_A_TOTAL,
                    VITAMIN_B12_TOTAL,
                    VITAMIN_B6_TOTAL,
                    VITAMIN_C_TOTAL,
                    VITAMIN_D_TOTAL,
                    VITAMIN_E_TOTAL,
                    VITAMIN_K_TOTAL,
                    ZINC_TOTAL);

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @Before
    public void setUp() throws InterruptedException {
        TestUtils.deleteAllStagedRemoteData();
    }

    @After
    public void tearDown() throws InterruptedException {
        TestUtils.verifyDeleteRecords(
                NutritionRecord.class,
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.EPOCH)
                        .setEndTime(Instant.now())
                        .build());
        TestUtils.deleteAllStagedRemoteData();
    }

    @Test
    public void testInsertNutritionRecord() throws InterruptedException {
        List<Record> records = List.of(getBaseNutritionRecord(), getCompleteNutritionRecord());
        TestUtils.insertRecords(records);
    }

    @Test
    public void testReadNutritionRecord_usingIds() throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(getCompleteNutritionRecord(), getCompleteNutritionRecord());
        List<Record> insertedRecords = TestUtils.insertRecords(recordList);
        readNutritionRecordUsingIds(insertedRecords);
    }

    @Test
    public void testReadNutritionRecord_invalidIds() throws InterruptedException {
        ReadRecordsRequestUsingIds<NutritionRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(NutritionRecord.class)
                        .addId(UUID.randomUUID().toString())
                        .build();
        List<NutritionRecord> result = TestUtils.readRecords(request);
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testReadNutritionRecord_usingClientRecordIds() throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(getCompleteNutritionRecord(), getCompleteNutritionRecord());
        List<Record> insertedRecords = TestUtils.insertRecords(recordList);
        readNutritionRecordUsingClientId(insertedRecords);
    }

    @Test
    public void testReadNutritionRecord_invalidClientRecordIds() throws InterruptedException {
        ReadRecordsRequestUsingIds<NutritionRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(NutritionRecord.class)
                        .addClientRecordId("abc")
                        .build();
        List<NutritionRecord> result = TestUtils.readRecords(request);
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testReadNutritionRecordUsingFilters_default() throws InterruptedException {
        List<NutritionRecord> oldNutritionRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(NutritionRecord.class)
                                .build());
        NutritionRecord testRecord = getCompleteNutritionRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<NutritionRecord> newNutritionRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(NutritionRecord.class)
                                .build());
        assertThat(newNutritionRecords.size()).isEqualTo(oldNutritionRecords.size() + 1);
        assertThat(newNutritionRecords.get(newNutritionRecords.size() - 1).equals(testRecord))
                .isTrue();
    }

    @Test
    public void testReadNutritionRecordUsingFilters_timeFilter() throws InterruptedException {
        TimeInstantRangeFilter filter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(3000))
                        .build();
        NutritionRecord testRecord = getCompleteNutritionRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<NutritionRecord> newNutritionRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(NutritionRecord.class)
                                .setTimeRangeFilter(filter)
                                .build());
        assertThat(newNutritionRecords.size()).isEqualTo(1);
        assertThat(newNutritionRecords.get(newNutritionRecords.size() - 1).equals(testRecord))
                .isTrue();
    }

    @Test
    public void testReadNutritionRecordUsingFilters_dataFilter_correct()
            throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        List<NutritionRecord> oldNutritionRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(NutritionRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build());
        NutritionRecord testRecord = getCompleteNutritionRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<NutritionRecord> newNutritionRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(NutritionRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build());
        assertThat(newNutritionRecords.size() - oldNutritionRecords.size()).isEqualTo(1);
        NutritionRecord newRecord = newNutritionRecords.get(newNutritionRecords.size() - 1);
        assertThat(newNutritionRecords.get(newNutritionRecords.size() - 1).equals(testRecord))
                .isTrue();
        assertThat(newRecord.getUnsaturatedFat()).isEqualTo(testRecord.getUnsaturatedFat());
        assertThat(newRecord.getPotassium()).isEqualTo(testRecord.getPotassium());
        assertThat(newRecord.getThiamin()).isEqualTo(testRecord.getThiamin());
        assertThat(newRecord.getMealType()).isEqualTo(testRecord.getMealType());
        assertThat(newRecord.getTransFat()).isEqualTo(testRecord.getTransFat());
        assertThat(newRecord.getManganese()).isEqualTo(testRecord.getManganese());
        assertThat(newRecord.getEnergyFromFat()).isEqualTo(testRecord.getEnergyFromFat());
        assertThat(newRecord.getCaffeine()).isEqualTo(testRecord.getCaffeine());
        assertThat(newRecord.getDietaryFiber()).isEqualTo(testRecord.getDietaryFiber());
        assertThat(newRecord.getSelenium()).isEqualTo(testRecord.getSelenium());
        assertThat(newRecord.getVitaminB6()).isEqualTo(testRecord.getVitaminB6());
        assertThat(newRecord.getProtein()).isEqualTo(testRecord.getProtein());
        assertThat(newRecord.getChloride()).isEqualTo(testRecord.getChloride());
        assertThat(newRecord.getCholesterol()).isEqualTo(testRecord.getCholesterol());
        assertThat(newRecord.getCopper()).isEqualTo(testRecord.getCopper());
        assertThat(newRecord.getIodine()).isEqualTo(testRecord.getIodine());
        assertThat(newRecord.getVitaminB12()).isEqualTo(testRecord.getVitaminB12());
        assertThat(newRecord.getZinc()).isEqualTo(testRecord.getZinc());
        assertThat(newRecord.getRiboflavin()).isEqualTo(testRecord.getRiboflavin());
        assertThat(newRecord.getEnergy()).isEqualTo(testRecord.getEnergy());
        assertThat(newRecord.getMolybdenum()).isEqualTo(testRecord.getMolybdenum());
        assertThat(newRecord.getPhosphorus()).isEqualTo(testRecord.getPhosphorus());
        assertThat(newRecord.getChromium()).isEqualTo(testRecord.getChromium());
        assertThat(newRecord.getTotalFat()).isEqualTo(testRecord.getTotalFat());
        assertThat(newRecord.getCalcium()).isEqualTo(testRecord.getCalcium());
        assertThat(newRecord.getVitaminC()).isEqualTo(testRecord.getVitaminC());
        assertThat(newRecord.getVitaminE()).isEqualTo(testRecord.getVitaminE());
        assertThat(newRecord.getBiotin()).isEqualTo(testRecord.getBiotin());
        assertThat(newRecord.getVitaminD()).isEqualTo(testRecord.getVitaminD());
        assertThat(newRecord.getNiacin()).isEqualTo(testRecord.getNiacin());
        assertThat(newRecord.getMagnesium()).isEqualTo(testRecord.getMagnesium());
        assertThat(newRecord.getTotalCarbohydrate()).isEqualTo(testRecord.getTotalCarbohydrate());
        assertThat(newRecord.getVitaminK()).isEqualTo(testRecord.getVitaminK());
        assertThat(newRecord.getPolyunsaturatedFat()).isEqualTo(testRecord.getPolyunsaturatedFat());
        assertThat(newRecord.getSaturatedFat()).isEqualTo(testRecord.getSaturatedFat());
        assertThat(newRecord.getSodium()).isEqualTo(testRecord.getSodium());
        assertThat(newRecord.getFolate()).isEqualTo(testRecord.getFolate());
        assertThat(newRecord.getMonounsaturatedFat()).isEqualTo(testRecord.getMonounsaturatedFat());
        assertThat(newRecord.getPantothenicAcid()).isEqualTo(testRecord.getPantothenicAcid());
        assertThat(newRecord.getMealName()).isEqualTo(testRecord.getMealName());
        assertThat(newRecord.getIron()).isEqualTo(testRecord.getIron());
        assertThat(newRecord.getVitaminA()).isEqualTo(testRecord.getVitaminA());
        assertThat(newRecord.getFolicAcid()).isEqualTo(testRecord.getFolicAcid());
        assertThat(newRecord.getSugar()).isEqualTo(testRecord.getSugar());
    }

    @Test
    public void testReadNutritionRecordUsingFilters_dataFilter_incorrect()
            throws InterruptedException {
        TestUtils.insertRecords(Collections.singletonList(getCompleteNutritionRecord()));
        List<NutritionRecord> newNutritionRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(NutritionRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder().setPackageName("abc").build())
                                .build());
        assertThat(newNutritionRecords.size()).isEqualTo(0);
    }

    @Test
    public void testDeleteNutritionRecord_no_filters() throws InterruptedException {
        String id = TestUtils.insertRecordAndGetId(getCompleteNutritionRecord());
        TestUtils.verifyDeleteRecords(new DeleteUsingFiltersRequest.Builder().build());
        TestUtils.assertRecordNotFound(id, NutritionRecord.class);
    }

    @Test
    public void testDeleteNutritionRecord_time_filters() throws InterruptedException {
        TimeInstantRangeFilter timeInstantRangeFilter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(1000))
                        .build();
        String id = TestUtils.insertRecordAndGetId(getCompleteNutritionRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addRecordType(NutritionRecord.class)
                        .setTimeRangeFilter(timeInstantRangeFilter)
                        .build());
        TestUtils.assertRecordNotFound(id, NutritionRecord.class);
    }

    @Test
    public void testDeleteNutritionRecord_recordId_filters() throws InterruptedException {
        List<Record> records = List.of(getBaseNutritionRecord(), getCompleteNutritionRecord());
        TestUtils.insertRecords(records);

        for (Record record : records) {
            TestUtils.verifyDeleteRecords(
                    new DeleteUsingFiltersRequest.Builder()
                            .addRecordType(record.getClass())
                            .build());
            TestUtils.assertRecordNotFound(record.getMetadata().getId(), record.getClass());
        }
    }

    @Test
    public void testDeleteNutritionRecord_dataOrigin_filters() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        String id = TestUtils.insertRecordAndGetId(getCompleteNutritionRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addDataOrigin(
                                new DataOrigin.Builder()
                                        .setPackageName(context.getPackageName())
                                        .build())
                        .build());
        TestUtils.assertRecordNotFound(id, NutritionRecord.class);
    }

    @Test
    public void testDeleteNutritionRecord_dataOrigin_filter_incorrect()
            throws InterruptedException {
        String id = TestUtils.insertRecordAndGetId(getCompleteNutritionRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addDataOrigin(new DataOrigin.Builder().setPackageName("abc").build())
                        .build());
        TestUtils.assertRecordFound(id, NutritionRecord.class);
    }

    @Test
    public void testDeleteNutritionRecord_usingIds() throws InterruptedException {
        List<Record> records = List.of(getBaseNutritionRecord(), getCompleteNutritionRecord());
        List<Record> insertedRecord = TestUtils.insertRecords(records);
        List<RecordIdFilter> recordIds = new ArrayList<>(records.size());
        for (Record record : insertedRecord) {
            recordIds.add(RecordIdFilter.fromId(record.getClass(), record.getMetadata().getId()));
        }

        TestUtils.verifyDeleteRecords(recordIds);
        for (Record record : records) {
            TestUtils.assertRecordNotFound(record.getMetadata().getId(), record.getClass());
        }
    }

    @Test
    public void testDeleteNutritionRecord_time_range() throws InterruptedException {
        TimeInstantRangeFilter timeInstantRangeFilter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.now())
                        .setEndTime(Instant.now().plusMillis(1000))
                        .build();
        String id = TestUtils.insertRecordAndGetId(getCompleteNutritionRecord());
        TestUtils.verifyDeleteRecords(NutritionRecord.class, timeInstantRangeFilter);
        TestUtils.assertRecordNotFound(id, NutritionRecord.class);
    }

    @Test
    public void testAggregation_NutritionValuesTotal() throws Exception {
        TestUtils.setupAggregation(PACKAGE_NAME, HealthDataCategory.NUTRITION);
        List<Record> records =
                Arrays.asList(getCompleteNutritionRecord(), getCompleteNutritionRecord());
        AggregateRecordsResponse<Mass> oldResponse =
                TestUtils.getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Mass>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(Instant.ofEpochMilli(0))
                                                .setEndTime(Instant.now().plus(1, ChronoUnit.DAYS))
                                                .build())
                                .addAggregationType(BIOTIN_TOTAL)
                                .addAggregationType(CAFFEINE_TOTAL)
                                .addAggregationType(CALCIUM_TOTAL)
                                .addAggregationType(CHLORIDE_TOTAL)
                                .addAggregationType(CHOLESTEROL_TOTAL)
                                .addAggregationType(CHROMIUM_TOTAL)
                                .addAggregationType(COPPER_TOTAL)
                                .addAggregationType(DIETARY_FIBER_TOTAL)
                                .addAggregationType(FOLATE_TOTAL)
                                .addAggregationType(FOLIC_ACID_TOTAL)
                                .addAggregationType(IODINE_TOTAL)
                                .addAggregationType(IRON_TOTAL)
                                .addAggregationType(MAGNESIUM_TOTAL)
                                .addAggregationType(MANGANESE_TOTAL)
                                .addAggregationType(MOLYBDENUM_TOTAL)
                                .addAggregationType(MONOUNSATURATED_FAT_TOTAL)
                                .addAggregationType(NIACIN_TOTAL)
                                .addAggregationType(PANTOTHENIC_ACID_TOTAL)
                                .addAggregationType(PHOSPHORUS_TOTAL)
                                .addAggregationType(POTASSIUM_TOTAL)
                                .addAggregationType(POLYUNSATURATED_FAT_TOTAL)
                                .addAggregationType(PROTEIN_TOTAL)
                                .addAggregationType(RIBOFLAVIN_TOTAL)
                                .addAggregationType(SATURATED_FAT_TOTAL)
                                .addAggregationType(SELENIUM_TOTAL)
                                .addAggregationType(SODIUM_TOTAL)
                                .addAggregationType(SUGAR_TOTAL)
                                .addAggregationType(THIAMIN_TOTAL)
                                .addAggregationType(TOTAL_CARBOHYDRATE_TOTAL)
                                .addAggregationType(TOTAL_FAT_TOTAL)
                                .addAggregationType(TRANS_FAT_TOTAL)
                                .addAggregationType(UNSATURATED_FAT_TOTAL)
                                .addAggregationType(VITAMIN_A_TOTAL)
                                .addAggregationType(VITAMIN_B12_TOTAL)
                                .addAggregationType(VITAMIN_B6_TOTAL)
                                .addAggregationType(VITAMIN_C_TOTAL)
                                .addAggregationType(VITAMIN_D_TOTAL)
                                .addAggregationType(VITAMIN_E_TOTAL)
                                .addAggregationType(VITAMIN_K_TOTAL)
                                .addAggregationType(ZINC_TOTAL)
                                .build(),
                        records);
        List<Record> recordNew =
                Arrays.asList(getCompleteNutritionRecord(), getCompleteNutritionRecord());
        AggregateRecordsResponse<Mass> newResponse =
                TestUtils.getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Mass>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(Instant.ofEpochMilli(0))
                                                .setEndTime(Instant.now().plus(1, ChronoUnit.DAYS))
                                                .build())
                                .addAggregationType(BIOTIN_TOTAL)
                                .addAggregationType(CAFFEINE_TOTAL)
                                .addAggregationType(CALCIUM_TOTAL)
                                .addAggregationType(CHLORIDE_TOTAL)
                                .addAggregationType(CHOLESTEROL_TOTAL)
                                .addAggregationType(CHROMIUM_TOTAL)
                                .addAggregationType(COPPER_TOTAL)
                                .addAggregationType(DIETARY_FIBER_TOTAL)
                                .addAggregationType(FOLATE_TOTAL)
                                .addAggregationType(FOLIC_ACID_TOTAL)
                                .addAggregationType(IODINE_TOTAL)
                                .addAggregationType(IRON_TOTAL)
                                .addAggregationType(MAGNESIUM_TOTAL)
                                .addAggregationType(MANGANESE_TOTAL)
                                .addAggregationType(MOLYBDENUM_TOTAL)
                                .addAggregationType(MONOUNSATURATED_FAT_TOTAL)
                                .addAggregationType(NIACIN_TOTAL)
                                .addAggregationType(PANTOTHENIC_ACID_TOTAL)
                                .addAggregationType(PHOSPHORUS_TOTAL)
                                .addAggregationType(POTASSIUM_TOTAL)
                                .addAggregationType(POLYUNSATURATED_FAT_TOTAL)
                                .addAggregationType(PROTEIN_TOTAL)
                                .addAggregationType(RIBOFLAVIN_TOTAL)
                                .addAggregationType(SATURATED_FAT_TOTAL)
                                .addAggregationType(SELENIUM_TOTAL)
                                .addAggregationType(SODIUM_TOTAL)
                                .addAggregationType(SUGAR_TOTAL)
                                .addAggregationType(THIAMIN_TOTAL)
                                .addAggregationType(TOTAL_CARBOHYDRATE_TOTAL)
                                .addAggregationType(TOTAL_FAT_TOTAL)
                                .addAggregationType(UNSATURATED_FAT_TOTAL)
                                .addAggregationType(TRANS_FAT_TOTAL)
                                .addAggregationType(VITAMIN_A_TOTAL)
                                .addAggregationType(VITAMIN_B12_TOTAL)
                                .addAggregationType(VITAMIN_B6_TOTAL)
                                .addAggregationType(VITAMIN_C_TOTAL)
                                .addAggregationType(VITAMIN_D_TOTAL)
                                .addAggregationType(VITAMIN_E_TOTAL)
                                .addAggregationType(VITAMIN_K_TOTAL)
                                .addAggregationType(ZINC_TOTAL)
                                .build(),
                        recordNew);
        for (AggregationType<Mass> type : mMassAggregateTypesList) {
            Mass newTotal = newResponse.get(type);
            Mass oldTotal = oldResponse.get(type);
            assertThat(newTotal).isNotNull();
            assertThat(oldTotal).isNotNull();
            assertThat(newTotal.getInGrams() - oldTotal.getInGrams()).isEqualTo(0.2);
            Set<DataOrigin> newDataOrigin = newResponse.getDataOrigins(type);
            for (DataOrigin itr : newDataOrigin) {
                assertThat(itr.getPackageName()).isEqualTo("android.healthconnect.cts");
            }
            Set<DataOrigin> oldDataOrigin = oldResponse.getDataOrigins(type);
            for (DataOrigin itr : oldDataOrigin) {
                assertThat(itr.getPackageName()).isEqualTo("android.healthconnect.cts");
            }
        }
    }

    @Test
    public void testAggregation_NutritionEnergyValuesTotal() throws Exception {
        TestUtils.setupAggregation(PACKAGE_NAME, HealthDataCategory.NUTRITION);
        List<Record> records = Arrays.asList(getCompleteNutritionRecord());
        AggregateRecordsResponse<Energy> oldResponse =
                TestUtils.getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Energy>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(Instant.ofEpochMilli(0))
                                                .setEndTime(Instant.now().plus(1, ChronoUnit.DAYS))
                                                .build())
                                .addAggregationType(ENERGY_TOTAL)
                                .addAggregationType(ENERGY_FROM_FAT_TOTAL)
                                .build(),
                        records);
        List<Record> newRecords = Arrays.asList(getCompleteNutritionRecord());
        AggregateRecordsResponse<Energy> newResponse =
                TestUtils.getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Energy>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(Instant.ofEpochMilli(0))
                                                .setEndTime(Instant.now().plus(1, ChronoUnit.DAYS))
                                                .build())
                                .addAggregationType(ENERGY_TOTAL)
                                .addAggregationType(ENERGY_FROM_FAT_TOTAL)
                                .build(),
                        newRecords);
        Energy newEnergy = newResponse.get(ENERGY_TOTAL);
        Energy oldEnergy = oldResponse.get(ENERGY_TOTAL);
        Energy newFatEnergy = newResponse.get(ENERGY_FROM_FAT_TOTAL);
        Energy oldFatEnergy = oldResponse.get(ENERGY_FROM_FAT_TOTAL);
        assertThat(newEnergy).isNotNull();
        assertThat(oldEnergy).isNotNull();
        assertThat(newFatEnergy).isNotNull();
        assertThat(oldFatEnergy).isNotNull();
        assertThat(newEnergy.getInCalories() - oldEnergy.getInCalories()).isEqualTo(0.1);
        assertThat(newFatEnergy.getInCalories() - oldFatEnergy.getInCalories()).isEqualTo(0.1);
    }

    @Test
    public void testZoneOffsets() {
        final ZoneOffset defaultZoneOffset =
                ZoneOffset.systemDefault().getRules().getOffset(Instant.now());
        final ZoneOffset startZoneOffset = ZoneOffset.UTC;
        final ZoneOffset endZoneOffset = ZoneOffset.MAX;
        NutritionRecord.Builder builder =
                new NutritionRecord.Builder(
                        new Metadata.Builder().build(),
                        Instant.now(),
                        Instant.now().plusMillis(1000));

        assertThat(builder.setStartZoneOffset(startZoneOffset).build().getStartZoneOffset())
                .isEqualTo(startZoneOffset);
        assertThat(builder.setEndZoneOffset(endZoneOffset).build().getEndZoneOffset())
                .isEqualTo(endZoneOffset);
        assertThat(builder.clearStartZoneOffset().build().getStartZoneOffset())
                .isEqualTo(defaultZoneOffset);
        assertThat(builder.clearEndZoneOffset().build().getEndZoneOffset())
                .isEqualTo(defaultZoneOffset);
    }

    @Test
    public void testUpdateRecords_validInput_dataBaseUpdatedSuccessfully()
            throws InterruptedException {

        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(getCompleteNutritionRecord(), getCompleteNutritionRecord()));

        // read inserted records and verify that the data is same as inserted.
        readNutritionRecordUsingIds(insertedRecords);

        // Generate a new set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(getCompleteNutritionRecord(), getCompleteNutritionRecord());

        // Modify the uid of the updateRecords to the uuid that was present in the insert records.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getNutritionRecord_update(
                            updateRecords.get(itr),
                            insertedRecords.get(itr).getMetadata().getId(),
                            insertedRecords.get(itr).getMetadata().getClientRecordId()));
        }

        TestUtils.updateRecords(updateRecords);

        // assert the inserted data has been modified by reading the data.
        readNutritionRecordUsingIds(updateRecords);
    }

    @Test
    public void testUpdateRecords_invalidInputRecords_noChangeInDataBase()
            throws InterruptedException {
        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(getCompleteNutritionRecord(), getCompleteNutritionRecord()));

        // read inserted records and verify that the data is same as inserted.
        readNutritionRecordUsingIds(insertedRecords);

        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(getCompleteNutritionRecord(), getCompleteNutritionRecord());

        // Modify the Uid of the updateRecords to the UUID that was present in the insert records,
        // leaving out alternate records so that they have a new UUID which is not present in the
        // dataBase.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getNutritionRecord_update(
                            updateRecords.get(itr),
                            itr % 2 == 0
                                    ? insertedRecords.get(itr).getMetadata().getId()
                                    : UUID.randomUUID().toString(),
                            itr % 2 == 0
                                    ? insertedRecords.get(itr).getMetadata().getId()
                                    : UUID.randomUUID().toString()));
        }

        try {
            TestUtils.updateRecords(updateRecords);
            Assert.fail("Expected to fail due to invalid records ids.");
        } catch (HealthConnectException exception) {
            assertThat(exception.getErrorCode())
                    .isEqualTo(HealthConnectException.ERROR_INVALID_ARGUMENT);
        }

        // assert the inserted data has not been modified by reading the data.
        readNutritionRecordUsingIds(insertedRecords);
    }

    @Test
    public void testUpdateRecords_recordWithInvalidPackageName_noChangeInDataBase()
            throws InterruptedException {
        List<Record> insertedRecords =
                TestUtils.insertRecords(
                        Arrays.asList(getCompleteNutritionRecord(), getCompleteNutritionRecord()));

        // read inserted records and verify that the data is same as inserted.
        readNutritionRecordUsingIds(insertedRecords);

        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords =
                Arrays.asList(getCompleteNutritionRecord(), getCompleteNutritionRecord());

        // Modify the Uuid of the updateRecords to the uuid that was present in the insert records.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    getNutritionRecord_update(
                            updateRecords.get(itr),
                            insertedRecords.get(itr).getMetadata().getId(),
                            insertedRecords.get(itr).getMetadata().getClientRecordId()));
            //             adding an entry with invalid packageName.
            updateRecords.set(itr, getCompleteNutritionRecord());
        }

        try {
            TestUtils.updateRecords(updateRecords);
            Assert.fail("Expected to fail due to invalid package.");
        } catch (Exception exception) {
            // verify that the testcase failed due to invalid argument exception.
            assertThat(exception).isNotNull();
        }

        // assert the inserted data has not been modified by reading the data.
        readNutritionRecordUsingIds(insertedRecords);
    }

    @Test
    public void testInsertAndDeleteRecord_changelogs() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        ChangeLogTokenResponse tokenResponse =
                TestUtils.getChangeLogToken(
                        new ChangeLogTokenRequest.Builder()
                                .addDataOriginFilter(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .addRecordType(NutritionRecord.class)
                                .build());
        ChangeLogsRequest changeLogsRequest =
                new ChangeLogsRequest.Builder(tokenResponse.getToken()).build();
        ChangeLogsResponse response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getUpsertedRecords().size()).isEqualTo(0);
        assertThat(response.getDeletedLogs().size()).isEqualTo(0);

        List<Record> testRecord = Collections.singletonList(getCompleteNutritionRecord());
        TestUtils.insertRecords(testRecord);
        response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getUpsertedRecords().size()).isEqualTo(1);
        assertThat(
                        response.getUpsertedRecords().stream()
                                .map(Record::getMetadata)
                                .map(Metadata::getId)
                                .toList())
                .containsExactlyElementsIn(
                        testRecord.stream().map(Record::getMetadata).map(Metadata::getId).toList());
        assertThat(response.getDeletedLogs().size()).isEqualTo(0);

        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addRecordType(NutritionRecord.class)
                        .build());
        response = TestUtils.getChangeLogs(changeLogsRequest);
        assertThat(response.getDeletedLogs()).isEmpty();
    }

    private void readNutritionRecordUsingClientId(List<Record> insertedRecord)
            throws InterruptedException {
        ReadRecordsRequestUsingIds.Builder<NutritionRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(NutritionRecord.class);
        for (Record record : insertedRecord) {
            request.addClientRecordId(record.getMetadata().getClientRecordId());
        }
        List<NutritionRecord> result = TestUtils.readRecords(request.build());
        assertThat(result.size()).isEqualTo(insertedRecord.size());
        assertThat(result).containsExactlyElementsIn(insertedRecord);
    }

    private void readNutritionRecordUsingIds(List<Record> recordList) throws InterruptedException {
        ReadRecordsRequestUsingIds.Builder<NutritionRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(NutritionRecord.class);
        for (Record record : recordList) {
            request.addId(record.getMetadata().getId());
        }
        List<NutritionRecord> result = TestUtils.readRecords(request.build());
        assertThat(result).hasSize(recordList.size());
        assertThat(result).containsExactlyElementsIn(recordList);
    }

    NutritionRecord getNutritionRecord_update(Record record, String id, String clientRecordId) {
        Metadata metadata = record.getMetadata();
        Metadata metadataWithId =
                new Metadata.Builder()
                        .setId(id)
                        .setClientRecordId(clientRecordId)
                        .setClientRecordVersion(metadata.getClientRecordVersion())
                        .setDataOrigin(metadata.getDataOrigin())
                        .setDevice(metadata.getDevice())
                        .setLastModifiedTime(metadata.getLastModifiedTime())
                        .build();
        return new NutritionRecord.Builder(
                        metadataWithId, Instant.now(), Instant.now().plusMillis(2000))
                .setUnsaturatedFat(Mass.fromGrams(0.1))
                .setPotassium(Mass.fromGrams(0.1))
                .setThiamin(Mass.fromGrams(0.1))
                .setMealType(1)
                .setTransFat(Mass.fromGrams(0.1))
                .setManganese(Mass.fromGrams(0.1))
                .setEnergyFromFat(Energy.fromCalories(0.1))
                .setCaffeine(Mass.fromGrams(0.1))
                .setDietaryFiber(Mass.fromGrams(0.1))
                .setSelenium(Mass.fromGrams(0.1))
                .setVitaminB6(Mass.fromGrams(0.1))
                .setProtein(Mass.fromGrams(0.1))
                .setChloride(Mass.fromGrams(0.1))
                .setCholesterol(Mass.fromGrams(0.1))
                .setCopper(Mass.fromGrams(0.1))
                .setIodine(Mass.fromGrams(0.1))
                .setVitaminB12(Mass.fromGrams(0.1))
                .setZinc(Mass.fromGrams(0.1))
                .setRiboflavin(Mass.fromGrams(0.1))
                .setEnergy(Energy.fromCalories(0.1))
                .setMolybdenum(Mass.fromGrams(0.1))
                .setPhosphorus(Mass.fromGrams(0.1))
                .setChromium(Mass.fromGrams(0.1))
                .setTotalFat(Mass.fromGrams(0.1))
                .setCalcium(Mass.fromGrams(0.1))
                .setVitaminC(Mass.fromGrams(0.1))
                .setVitaminE(Mass.fromGrams(0.1))
                .setBiotin(Mass.fromGrams(0.1))
                .setVitaminD(Mass.fromGrams(0.1))
                .setNiacin(Mass.fromGrams(0.1))
                .setMagnesium(Mass.fromGrams(0.02))
                .setTotalCarbohydrate(Mass.fromGrams(0.1))
                .setVitaminK(Mass.fromGrams(0.1))
                .setPolyunsaturatedFat(Mass.fromGrams(0.1))
                .setSaturatedFat(Mass.fromGrams(0.1))
                .setSodium(Mass.fromGrams(0.1))
                .setFolate(Mass.fromGrams(0.1))
                .setMonounsaturatedFat(Mass.fromGrams(0.1))
                .setPantothenicAcid(Mass.fromGrams(0.1))
                .setMealName("Brunch")
                .setIron(Mass.fromGrams(0.1))
                .setVitaminA(Mass.fromGrams(0.1))
                .setFolicAcid(Mass.fromGrams(0.1))
                .setSugar(Mass.fromGrams(0.2))
                .setStartZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .setEndZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .build();
    }

    static NutritionRecord getBaseNutritionRecord() {
        return new NutritionRecord.Builder(
                        new Metadata.Builder().build(),
                        Instant.now(),
                        Instant.now().plusMillis(1000))
                .build();
    }

    static NutritionRecord getCompleteNutritionRecord() {
        Device device =
                new Device.Builder()
                        .setManufacturer("google")
                        .setModel("Pixel4a")
                        .setType(2)
                        .build();
        DataOrigin dataOrigin =
                new DataOrigin.Builder().setPackageName("android.healthconnect.cts").build();
        Metadata.Builder testMetadataBuilder = new Metadata.Builder();
        testMetadataBuilder.setDevice(device).setDataOrigin(dataOrigin);
        testMetadataBuilder.setClientRecordId("NTR" + Math.random());

        return new NutritionRecord.Builder(
                        testMetadataBuilder.build(), Instant.now(), Instant.now().plusMillis(1000))
                .setUnsaturatedFat(Mass.fromGrams(0.1))
                .setPotassium(Mass.fromGrams(0.1))
                .setThiamin(Mass.fromGrams(0.1))
                .setMealType(1)
                .setTransFat(Mass.fromGrams(0.1))
                .setManganese(Mass.fromGrams(0.1))
                .setEnergyFromFat(Energy.fromCalories(0.1))
                .setCaffeine(Mass.fromGrams(0.1))
                .setDietaryFiber(Mass.fromGrams(0.1))
                .setSelenium(Mass.fromGrams(0.1))
                .setVitaminB6(Mass.fromGrams(0.1))
                .setProtein(Mass.fromGrams(0.1))
                .setChloride(Mass.fromGrams(0.1))
                .setCholesterol(Mass.fromGrams(0.1))
                .setCopper(Mass.fromGrams(0.1))
                .setIodine(Mass.fromGrams(0.1))
                .setVitaminB12(Mass.fromGrams(0.1))
                .setZinc(Mass.fromGrams(0.1))
                .setRiboflavin(Mass.fromGrams(0.1))
                .setEnergy(Energy.fromCalories(0.1))
                .setMolybdenum(Mass.fromGrams(0.1))
                .setPhosphorus(Mass.fromGrams(0.1))
                .setChromium(Mass.fromGrams(0.1))
                .setTotalFat(Mass.fromGrams(0.1))
                .setCalcium(Mass.fromGrams(0.1))
                .setVitaminC(Mass.fromGrams(0.1))
                .setVitaminE(Mass.fromGrams(0.1))
                .setBiotin(Mass.fromGrams(0.1))
                .setVitaminD(Mass.fromGrams(0.1))
                .setNiacin(Mass.fromGrams(0.1))
                .setMagnesium(Mass.fromGrams(0.1))
                .setTotalCarbohydrate(Mass.fromGrams(0.1))
                .setVitaminK(Mass.fromGrams(0.1))
                .setPolyunsaturatedFat(Mass.fromGrams(0.1))
                .setSaturatedFat(Mass.fromGrams(0.1))
                .setSodium(Mass.fromGrams(0.1))
                .setFolate(Mass.fromGrams(0.1))
                .setMonounsaturatedFat(Mass.fromGrams(0.1))
                .setPantothenicAcid(Mass.fromGrams(0.1))
                .setMealName("Brunch")
                .setIron(Mass.fromGrams(0.1))
                .setVitaminA(Mass.fromGrams(0.1))
                .setFolicAcid(Mass.fromGrams(0.1))
                .setSugar(Mass.fromGrams(0.1))
                .setStartZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .setEndZoneOffset(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                .build();
    }
}

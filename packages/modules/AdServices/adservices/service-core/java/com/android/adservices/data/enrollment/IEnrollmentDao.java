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

package com.android.adservices.data.enrollment;

import android.adservices.common.AdTechIdentifier;
import android.net.Uri;
import android.util.Pair;

import com.android.adservices.service.enrollment.EnrollmentData;

import java.util.List;
import java.util.Set;

/** Interface for enrollment related data access operations. */
public interface IEnrollmentDao {

    /** Returns all enrollment data in enrollment table. */
    List<EnrollmentData> getAllEnrollmentData();

    /**
     * Returns the {@link EnrollmentData}.
     *
     * @param enrollmentId ID provided to the adtech during the enrollment process.
     * @return the EnrollmentData; Null in case of SQL failure
     */
    EnrollmentData getEnrollmentData(String enrollmentId);

    /**
     * Returns the {@link EnrollmentData} given measurement registration URLs.
     *
     * @param url could be source registration url or trigger registration url.
     * @return the EnrollmentData; Null in case of SQL failure.
     */
    EnrollmentData getEnrollmentDataFromMeasurementUrl(Uri url);

    /**
     * Returns the {@link EnrollmentData} with FLEDGE response-based registration URLs that match
     * the given {@link AdTechIdentifier}.
     *
     * <p>Upon enrollment, the server will validate that ad techs' RBR URLs do not share the same
     * domain. If this does happen, only return the first match in the database.
     *
     * @param adTechIdentifier the {@link AdTechIdentifier} to search against
     * @return a matching {@link EnrollmentData} or {@code null} if no matches were found
     */
    EnrollmentData getEnrollmentDataForFledgeByAdTechIdentifier(AdTechIdentifier adTechIdentifier);

    /**
     * Returns a set of {@link AdTechIdentifier} objects for all ad techs enrolled with FLEDGE.
     *
     * @return a set of all enrolled ad techs' {@link AdTechIdentifier} if they enrolled in FLEDGE;
     *     empty if none found
     */
    Set<AdTechIdentifier> getAllFledgeEnrolledAdTechs();

    /**
     * Extracts and returns the {@link AdTechIdentifier} and matching {@link EnrollmentData} from a
     * given {@link Uri}.
     *
     * <p>Upon enrollment, the server will validate that ad techs' RBR URLs do not share the same
     * domain. If this does happen, only return the first match in the database.
     *
     * @param originalUri the {@link Uri} to extract from
     * @return a matching {@link Pair} of {@link AdTechIdentifier} and {@link EnrollmentData}, or
     *     {@code null} if no matches were found
     */
    Pair<AdTechIdentifier, EnrollmentData> getEnrollmentDataForFledgeByMatchingAdTechIdentifier(
            Uri originalUri);

    /**
     * Returns the {@link EnrollmentData} that matches the given {@link AdTechIdentifier}.
     *
     * <p>Upon enrollment, the server will validate that ad techs' urls do not share the same
     * domain. If this does happen, only return the first match in the database.
     *
     * @param adTechIdentifier the {@link AdTechIdentifier} to search against
     * @return a matching {@link EnrollmentData} or {@code null} if no matches were found
     */
    EnrollmentData getEnrollmentDataForPASByAdTechIdentifier(AdTechIdentifier adTechIdentifier);

    /**
     * Returns a set of {@link AdTechIdentifier} objects for all ad techs enrolled with PAS.
     *
     * @return a set of all enrolled ad techs' {@link AdTechIdentifier} if they enrolled in PAS;
     *     empty if none found
     */
    Set<AdTechIdentifier> getAllPASEnrolledAdTechs();

    /**
     * Extracts and returns the {@link AdTechIdentifier} and matching {@link EnrollmentData} from a
     * given {@link Uri}.
     *
     * <p>Upon enrollment, the server will validate that ad techs' url do not share the same domain.
     * If this does happen, only return the first match in the database.
     *
     * @param originalUri the {@link Uri} to extract from
     * @return a matching {@link Pair} of {@link AdTechIdentifier} and {@link EnrollmentData}, or
     *     {@code null} if no matches were found
     */
    Pair<AdTechIdentifier, EnrollmentData> getEnrollmentDataForPASByMatchingAdTechIdentifier(
            Uri originalUri);

    /**
     * Returns the {@link EnrollmentData} given AdTech SDK Name.
     *
     * @param sdkName List of SDKs belonging to the same enrollment.
     * @return the EnrollmentData; Null in case of SQL failure
     */
    EnrollmentData getEnrollmentDataFromSdkName(String sdkName);

    /**
     * Returns the number of enrollment records in the DB table.
     *
     * @return count of records in the enrollment table
     */
    Long getEnrollmentRecordsCount();

    /**
     * Returns the number of enrollment records in the DB table for logging purposes.
     *
     * @return count of records in the enrollment table
     */
    int getEnrollmentRecordCountForLogging();

    /**
     * Inserts {@link EnrollmentData} into DB table.
     *
     * @param enrollmentData the EnrollmentData to insert.
     * @return true if the operation was successful, false, otherwise.
     */
    boolean insert(EnrollmentData enrollmentData);

    /**
     * Deletes {@link EnrollmentData} from DB table.
     *
     * @param enrollmentId ID provided to the adtech at the end of the enrollment process.
     * @return true if the operation was successful, false, otherwise.
     */
    boolean delete(String enrollmentId);

    /**
     * Deletes the whole EnrollmentData table.
     *
     * @return true if the operation was successful, false, otherwise.
     */
    boolean deleteAll();

    /**
     * Overwrites all {@link EnrollmentData} in DB table. Delete records that aren't included list.
     * Updates/inserts those included.
     *
     * @param newEnrollments List of {@link EnrollmentData} to overwrite table with.
     * @return true if the operation was successful, false, otherwise.
     */
    boolean overwriteData(List<EnrollmentData> newEnrollments);
}

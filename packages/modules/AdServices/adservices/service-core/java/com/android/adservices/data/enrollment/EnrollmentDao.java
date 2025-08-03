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

import static com.android.adservices.service.enrollment.EnrollmentUtil.ENROLLMENT_SHARED_PREF;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_DATA_DELETE_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_DATA_INSERT_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_SHARED_PREFERENCES_SEED_SAVE_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT;

import android.adservices.common.AdTechIdentifier;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.adservices.LogUtil;
import com.android.adservices.data.shared.SharedDbHelper;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.WebAddresses;
import com.android.adservices.service.enrollment.EnrollmentData;
import com.android.adservices.service.enrollment.EnrollmentStatus;
import com.android.adservices.service.enrollment.EnrollmentUtil;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Data Access Object for the EnrollmentData. */
public class EnrollmentDao implements IEnrollmentDao {

    private static EnrollmentDao sSingleton;
    private final SharedDbHelper mDbHelper;
    private final Context mContext;
    private final Flags mFlags;
    private final AdServicesLogger mLogger;
    private final EnrollmentUtil mEnrollmentUtil;
    @VisibleForTesting static final String IS_SEEDED = "is_seeded";
    static final int READ_QUERY = EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue();
    static final int WRITE_QUERY =
            EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue();

    @VisibleForTesting
    public EnrollmentDao(Context context, SharedDbHelper dbHelper, Flags flags) {
        this(
                context,
                dbHelper,
                flags,
                flags.isEnableEnrollmentTestSeed(),
                AdServicesLoggerImpl.getInstance(),
                EnrollmentUtil.getInstance(context));
    }

    @VisibleForTesting
    public EnrollmentDao(
            Context context,
            SharedDbHelper dbHelper,
            Flags flags,
            boolean enableTestSeed,
            AdServicesLogger logger,
            EnrollmentUtil enrollmentUtil) {
        // enableTestSeed is needed
        mContext = context;
        mDbHelper = dbHelper;
        mFlags = flags;
        mLogger = logger;
        mEnrollmentUtil = enrollmentUtil;
        if (enableTestSeed) {
            seed();
        }
    }

    /** Returns an instance of the EnrollmentDao given a context. */
    @NonNull
    public static EnrollmentDao getInstance(@NonNull Context context) {
        synchronized (EnrollmentDao.class) {
            if (sSingleton == null) {
                Flags flags = FlagsFactory.getFlags();
                sSingleton =
                        new EnrollmentDao(
                                context,
                                SharedDbHelper.getInstance(context),
                                flags,
                                flags.isEnableEnrollmentTestSeed(),
                                AdServicesLoggerImpl.getInstance(),
                                EnrollmentUtil.getInstance(context));
            }
            return sSingleton;
        }
    }

    @VisibleForTesting
    boolean isSeeded() {
        SharedPreferences prefs =
                mContext.getSharedPreferences(ENROLLMENT_SHARED_PREF, Context.MODE_PRIVATE);
        boolean isSeeded = prefs.getBoolean(IS_SEEDED, false);
        LogUtil.v("Persisted enrollment database seed status: %s", isSeeded);
        return isSeeded;
    }

    @VisibleForTesting
    void seed() {
        LogUtil.v("Seeding enrollment database");

        if (!isSeeded()) {
            boolean success = true;
            for (EnrollmentData enrollment : PreEnrolledAdTechForTest.getList()) {
                success = success && insert(enrollment);
            }

            LogUtil.v("Enrollment database seed insertion status: %s", success);

            if (success) {
                SharedPreferences prefs =
                        mContext.getSharedPreferences(ENROLLMENT_SHARED_PREF, Context.MODE_PRIVATE);
                SharedPreferences.Editor edit = prefs.edit();
                edit.putBoolean(IS_SEEDED, true);
                if (!edit.commit()) {
                    LogUtil.e(
                            "Saving shared preferences - %s , %s failed",
                            ENROLLMENT_SHARED_PREF, IS_SEEDED);
                    ErrorLogUtil.e(
                            AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_SHARED_PREFERENCES_SEED_SAVE_FAILURE,
                            AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
                }
            }
        }

        LogUtil.v("Enrollment database seeding complete");
    }

    @VisibleForTesting
    void unSeed() {
        LogUtil.v("Clearing enrollment database seed status");

        SharedPreferences prefs =
                mContext.getSharedPreferences(ENROLLMENT_SHARED_PREF, Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = prefs.edit();
        edit.putBoolean(IS_SEEDED, false);
        if (!edit.commit()) {
            LogUtil.e(
                    "Saving shared preferences - %s , %s failed",
                    ENROLLMENT_SHARED_PREF, IS_SEEDED);
            ErrorLogUtil.e(
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_SHARED_PREFERENCES_SEED_SAVE_FAILURE,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
        }
    }

    @Override
    @NonNull
    public List<EnrollmentData> getAllEnrollmentData() {
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        List<EnrollmentData> enrollmentDataList = new ArrayList<>();
        if (db == null) {
            return enrollmentDataList;
        }

        try (Cursor cursor =
                db.query(
                        EnrollmentTables.EnrollmentDataContract.TABLE,
                        /*columns=*/ null,
                        /*selection=*/ null,
                        /*selectionArgs=*/ null,
                        /*groupBy=*/ null,
                        /*having=*/ null,
                        /*orderBy=*/ null,
                        /*limit=*/ null)) {
            if (cursor == null || cursor.getCount() == 0) {
                LogUtil.d("Can't get all enrollment data from DB.");
                return enrollmentDataList;
            }
            while (cursor.moveToNext()) {
                enrollmentDataList.add(
                        SqliteObjectMapper.constructEnrollmentDataFromCursor(cursor));
            }
            return enrollmentDataList;
        } catch (SQLException e) {
            LogUtil.e(e, "Failed to get all enrollment data from DB.");
        }
        return enrollmentDataList;
    }

    @Override
    @Nullable
    public EnrollmentData getEnrollmentData(String enrollmentId) {
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        if (db == null) {
            return null;
        }

        try (Cursor cursor =
                db.query(
                        EnrollmentTables.EnrollmentDataContract.TABLE,
                        /*columns=*/ null,
                        EnrollmentTables.EnrollmentDataContract.ENROLLMENT_ID + " = ? ",
                        new String[] {enrollmentId},
                        /*groupBy=*/ null,
                        /*having=*/ null,
                        /*orderBy=*/ null,
                        /*limit=*/ null)) {
            if (cursor == null || cursor.getCount() == 0) {
                LogUtil.d("Failed to match enrollment for enrollment ID \"%s\"", enrollmentId);
                return null;
            }
            cursor.moveToNext();
            return SqliteObjectMapper.constructEnrollmentDataFromCursor(cursor);
        }
    }

    @Override
    @Nullable
    public EnrollmentData getEnrollmentDataFromMeasurementUrl(Uri url) {
        int buildId = mEnrollmentUtil.getBuildId();
        boolean originMatch = mFlags.getEnforceEnrollmentOriginMatch();
        Optional<Uri> registrationBaseUri =
                originMatch ? WebAddresses.originAndScheme(url)
                        : WebAddresses.topPrivateDomainAndScheme(url);
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        if (!registrationBaseUri.isPresent()) {
            return null;
        }
        if (db == null) {
            mEnrollmentUtil.logEnrollmentDataStats(mLogger, READ_QUERY, false, buildId);
            return null;
        }
        mEnrollmentUtil.logEnrollmentDataStats(mLogger, READ_QUERY, true, buildId);

        String selectionQuery =
                getAttributionUrlSelection(
                                EnrollmentTables.EnrollmentDataContract
                                        .ATTRIBUTION_SOURCE_REGISTRATION_URL,
                                registrationBaseUri.get(),
                                /* isSiteMatch = */ !originMatch)
                        + " OR "
                        + getAttributionUrlSelection(
                                EnrollmentTables.EnrollmentDataContract
                                        .ATTRIBUTION_TRIGGER_REGISTRATION_URL,
                                registrationBaseUri.get(),
                                /* isSiteMatch = */ !originMatch);
        try (Cursor cursor =
                db.query(
                        EnrollmentTables.EnrollmentDataContract.TABLE,
                        /*columns=*/ null,
                        selectionQuery,
                        null,
                        /*groupBy=*/ null,
                        /*having=*/ null,
                        /*orderBy=*/ null,
                        /*limit=*/ null)) {
            if (cursor == null || cursor.getCount() == 0) {
                LogUtil.d("Failed to match enrollment for url \"%s\"", url);
                mEnrollmentUtil.logEnrollmentMatchStats(mLogger, false, buildId);
                return null;
            }

            while (cursor.moveToNext()) {
                EnrollmentData data = SqliteObjectMapper.constructEnrollmentDataFromCursor(cursor);
                if (validateAttributionUrl(
                                data.getAttributionSourceRegistrationUrl(),
                                registrationBaseUri,
                                originMatch)
                        || validateAttributionUrl(
                                data.getAttributionTriggerRegistrationUrl(),
                                registrationBaseUri,
                                originMatch)) {
                    mEnrollmentUtil.logEnrollmentMatchStats(mLogger, true, buildId);
                    return data;
                }
            }
            mEnrollmentUtil.logEnrollmentMatchStats(mLogger, false, buildId);
            return null;
        }
    }

    /**
     * Validates enrollment urls returned by selection query by matching its scheme + first
     * subdomain to that of registration uri.
     *
     * @param enrolledUris : urls returned by selection query
     * @param registrationBaseUri : registration base url
     * @return : true if validation is success
     */
    private boolean validateAttributionUrl(
            List<String> enrolledUris, Optional<Uri> registrationBaseUri, boolean originMatch) {
        // This match is needed to avoid matching .co in registration url to .com in enrolled url
        for (String uri : enrolledUris) {
            Optional<Uri> enrolledBaseUri =
                    originMatch
                            ? WebAddresses.originAndScheme(Uri.parse(uri))
                            : WebAddresses.topPrivateDomainAndScheme(Uri.parse(uri));
            if (registrationBaseUri.equals(enrolledBaseUri)) {
                return true;
            }
        }
        return false;
    }

    private String getAttributionUrlSelection(String field, Uri baseUri, boolean isSiteMatch) {
        String selectionQuery =
                String.format(
                        Locale.ENGLISH,
                        "(%1$s LIKE %2$s)",
                        field,
                        DatabaseUtils.sqlEscapeString("%" + baseUri.toString() + "%"));

        if (isSiteMatch) {
            // site match needs to also match https://%.host.com
            selectionQuery +=
                    String.format(
                            Locale.ENGLISH,
                            "OR (%1$s LIKE %2$s)",
                            field,
                            DatabaseUtils.sqlEscapeString(
                                    "%"
                                            + baseUri.getScheme()
                                            + "://%."
                                            + baseUri.getEncodedAuthority()
                                            + "%"));
        }
        return selectionQuery;
    }

    @Override
    @Nullable
    public EnrollmentData getEnrollmentDataForFledgeByAdTechIdentifier(
            AdTechIdentifier adTechIdentifier) {
        int buildId = mEnrollmentUtil.getBuildId();
        String adTechIdentifierString = adTechIdentifier.toString();
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        if (db == null) {
            mEnrollmentUtil.logEnrollmentDataStats(mLogger, READ_QUERY, false, buildId);
            return null;
        }
        mEnrollmentUtil.logEnrollmentDataStats(mLogger, READ_QUERY, true, buildId);

        try (Cursor cursor =
                db.query(
                        EnrollmentTables.EnrollmentDataContract.TABLE,
                        /*columns=*/ null,
                        EnrollmentTables.EnrollmentDataContract
                                        .REMARKETING_RESPONSE_BASED_REGISTRATION_URL
                                + " LIKE '%"
                                + adTechIdentifierString
                                + "%'",
                        null,
                        /*groupBy=*/ null,
                        /*having=*/ null,
                        /*orderBy=*/ null,
                        /*limit=*/ null)) {
            if (cursor == null || cursor.getCount() <= 0) {
                LogUtil.d(
                        "Failed to match enrollment for ad tech identifier \"%s\"",
                        adTechIdentifierString);
                mEnrollmentUtil.logEnrollmentMatchStats(mLogger, false, buildId);
                return null;
            }

            LogUtil.v(
                    "Found %d rows potentially matching ad tech identifier \"%s\"",
                    cursor.getCount(), adTechIdentifierString);

            while (cursor.moveToNext()) {
                EnrollmentData potentialMatch =
                        SqliteObjectMapper.constructEnrollmentDataFromCursor(cursor);
                for (String rbrUriString :
                        potentialMatch.getRemarketingResponseBasedRegistrationUrl()) {
                    try {
                        // Make sure the URI can be parsed and the parsed host matches the ad tech
                        if (adTechIdentifierString.equalsIgnoreCase(
                                Uri.parse(rbrUriString).getHost())) {
                            LogUtil.v(
                                    "Found positive match RBR URL \"%s\" for ad tech identifier"
                                            + " \"%s\"",
                                    rbrUriString, adTechIdentifierString);
                            mEnrollmentUtil.logEnrollmentMatchStats(mLogger, true, buildId);

                            return potentialMatch;
                        }
                    } catch (IllegalArgumentException exception) {
                        LogUtil.v(
                                "Error while matching ad tech %s to FLEDGE RBR URI %s; skipping"
                                        + " URI. Error message: %s",
                                adTechIdentifierString, rbrUriString, exception.getMessage());
                    }
                }
            }
            mEnrollmentUtil.logEnrollmentMatchStats(mLogger, false, buildId);
            return null;
        }
    }

    @Override
    @NonNull
    public Set<AdTechIdentifier> getAllFledgeEnrolledAdTechs() {
        int buildId = mEnrollmentUtil.getBuildId();
        Set<AdTechIdentifier> enrolledAdTechIdentifiers = new HashSet<>();
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        if (db == null) {
            mEnrollmentUtil.logEnrollmentDataStats(mLogger, READ_QUERY, false, buildId);
            return enrolledAdTechIdentifiers;
        }
        mEnrollmentUtil.logEnrollmentDataStats(mLogger, READ_QUERY, true, buildId);

        try (Cursor cursor =
                db.query(
                        /*distinct=*/ true,
                        /*table=*/ EnrollmentTables.EnrollmentDataContract.TABLE,
                        /*columns=*/ new String[] {
                            EnrollmentTables.EnrollmentDataContract
                                    .REMARKETING_RESPONSE_BASED_REGISTRATION_URL
                        },
                        /*selection=*/ EnrollmentTables.EnrollmentDataContract
                                        .REMARKETING_RESPONSE_BASED_REGISTRATION_URL
                                + " IS NOT NULL",
                        /*selectionArgs=*/ null,
                        /*groupBy=*/ null,
                        /*having=*/ null,
                        /*orderBy=*/ null,
                        /*limit=*/ null)) {
            if (cursor == null || cursor.getCount() <= 0) {
                LogUtil.d("Failed to find any FLEDGE-enrolled ad techs");
                return enrolledAdTechIdentifiers;
            }

            LogUtil.v("Found %d FLEDGE enrollment entries", cursor.getCount());

            while (cursor.moveToNext()) {
                enrolledAdTechIdentifiers.addAll(
                        SqliteObjectMapper.getAdTechIdentifiersFromFledgeCursor(cursor));
            }

            LogUtil.v(
                    "Found %d FLEDGE enrolled ad tech identifiers",
                    enrolledAdTechIdentifiers.size());

            return enrolledAdTechIdentifiers;
        }
    }

    @Override
    @Nullable
    public Pair<AdTechIdentifier, EnrollmentData>
            getEnrollmentDataForFledgeByMatchingAdTechIdentifier(Uri originalUri) {
        if (originalUri == null) {
            return null;
        }

        String originalUriHost = originalUri.getHost();
        if (originalUriHost == null || originalUriHost.isEmpty()) {
            return null;
        }

        // Instead of searching through all enrollment rows, narrow the search by searching only
        //  the rows with FLEDGE RBR URLs which may match the TLD
        String[] subdomains = originalUriHost.split("\\.");
        if (subdomains.length < 1) {
            return null;
        }

        String topLevelDomain = subdomains[subdomains.length - 1];

        int buildId = mEnrollmentUtil.getBuildId();
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        if (db == null) {
            mEnrollmentUtil.logEnrollmentDataStats(mLogger, READ_QUERY, false, buildId);
            return null;
        }
        mEnrollmentUtil.logEnrollmentDataStats(mLogger, READ_QUERY, true, buildId);

        try (Cursor cursor =
                db.query(
                        EnrollmentTables.EnrollmentDataContract.TABLE,
                        /*columns=*/ null,
                        EnrollmentTables.EnrollmentDataContract
                                        .REMARKETING_RESPONSE_BASED_REGISTRATION_URL
                                + " LIKE '%"
                                + topLevelDomain
                                + "%'",
                        /*selectionArgs=*/ null,
                        /*groupBy=*/ null,
                        /*having=*/ null,
                        /*orderBy=*/ null,
                        /*limit=*/ null)) {
            if (cursor == null || cursor.getCount() <= 0) {
                LogUtil.d(
                        "Failed to match enrollment for URI \"%s\" with top level domain \"%s\"",
                        originalUri, topLevelDomain);
                mEnrollmentUtil.logEnrollmentMatchStats(mLogger, false, buildId);
                return null;
            }

            LogUtil.v(
                    "Found %d rows potentially matching URI \"%s\" with top level domain \"%s\"",
                    cursor.getCount(), originalUri, topLevelDomain);

            while (cursor.moveToNext()) {
                EnrollmentData potentialMatch =
                        SqliteObjectMapper.constructEnrollmentDataFromCursor(cursor);
                for (String rbrUriString :
                        potentialMatch.getRemarketingResponseBasedRegistrationUrl()) {
                    try {
                        // Make sure the URI can be parsed and the parsed host matches the ad tech
                        String rbrUriHost = Uri.parse(rbrUriString).getHost();
                        if (originalUriHost.equalsIgnoreCase(rbrUriHost)
                                || originalUriHost
                                        .toLowerCase(Locale.ENGLISH)
                                        .endsWith("." + rbrUriHost.toLowerCase(Locale.ENGLISH))) {
                            LogUtil.v(
                                    "Found positive match RBR URL \"%s\" for given URI \"%s\"",
                                    rbrUriString, originalUri);
                            mEnrollmentUtil.logEnrollmentMatchStats(mLogger, true, buildId);

                            // AdTechIdentifiers are currently expected to only contain eTLD+1
                            return new Pair<>(
                                    AdTechIdentifier.fromString(rbrUriHost), potentialMatch);
                        }
                    } catch (IllegalArgumentException exception) {
                        LogUtil.v(
                                "Error while matching URI %s to FLEDGE RBR URI %s; skipping URI. "
                                        + "Error message: %s",
                                originalUri, rbrUriString, exception.getMessage());
                    }
                }
            }

            mEnrollmentUtil.logEnrollmentMatchStats(mLogger, false, buildId);
            return null;
        }
    }

    @Override
    @Nullable
    public EnrollmentData getEnrollmentDataFromSdkName(String sdkName) {
        if (sdkName.contains(" ") || sdkName.contains(",")) {
            return null;
        }
        int buildId = mEnrollmentUtil.getBuildId();
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        if (db == null) {
            mEnrollmentUtil.logEnrollmentDataStats(mLogger, READ_QUERY, false, buildId);
            return null;
        }
        mEnrollmentUtil.logEnrollmentDataStats(mLogger, READ_QUERY, true, buildId);

        try (Cursor cursor =
                db.query(
                        EnrollmentTables.EnrollmentDataContract.TABLE,
                        /*columns=*/ null,
                        EnrollmentTables.EnrollmentDataContract.SDK_NAMES
                                + " LIKE '%"
                                + sdkName
                                + "%'",
                        null,
                        /*groupBy=*/ null,
                        /*having=*/ null,
                        /*orderBy=*/ null,
                        /*limit=*/ null)) {
            if (cursor == null || cursor.getCount() == 0) {
                LogUtil.d("Failed to match enrollment for sdk \"%s\"", sdkName);
                mEnrollmentUtil.logEnrollmentMatchStats(mLogger, false, buildId);
                return null;
            }
            mEnrollmentUtil.logEnrollmentMatchStats(mLogger, true, buildId);
            cursor.moveToNext();
            return SqliteObjectMapper.constructEnrollmentDataFromCursor(cursor);
        }
    }

    @Override
    @Nullable
    public Pair<AdTechIdentifier, EnrollmentData> getEnrollmentDataForPASByMatchingAdTechIdentifier(
            Uri originalUri) {
        if (originalUri == null) {
            return null;
        }

        Optional<Uri> topDomainUri = WebAddresses.topPrivateDomainAndScheme(originalUri);
        if (topDomainUri.isEmpty()) {
            return null;
        }
        String originalUriHost = topDomainUri.get().getHost();

        int buildId = mEnrollmentUtil.getBuildId();
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        if (db == null) {
            mEnrollmentUtil.logEnrollmentDataStats(
                    mLogger, READ_QUERY, /* isSuccessful= */ false, buildId);
            return null;
        }
        mEnrollmentUtil.logEnrollmentDataStats(
                mLogger, READ_QUERY, /* isSuccessful= */ true, buildId);

        try (Cursor cursor =
                db.query(
                        EnrollmentTables.EnrollmentDataContract.TABLE,
                        /*columns=*/ null,
                        EnrollmentTables.EnrollmentDataContract.COMPANY_ID
                                + " LIKE '%"
                                + "PRIVACY_SANDBOX_API_PROTECTED_APP_SIGNALS"
                                + "%'"
                                + " AND "
                                + EnrollmentTables.EnrollmentDataContract.ENCRYPTION_KEY_URL
                                + " LIKE '%"
                                + originalUriHost
                                + "%'",
                        /*selectionArgs=*/ null,
                        /*groupBy=*/ null,
                        /*having=*/ null,
                        /*orderBy=*/ null,
                        /*limit=*/ null)) {
            if (cursor == null || cursor.getCount() <= 0) {
                LogUtil.d("Failed to match enrollment for PAS URI \"%s\" ", originalUri);
                mEnrollmentUtil.logEnrollmentMatchStats(
                        mLogger, /* isSuccessful= */ false, buildId);
                return null;
            }

            LogUtil.v(
                    "Found %d rows potentially matching URI \"%s\".",
                    cursor.getCount(), originalUri);

            while (cursor.moveToNext()) {
                EnrollmentData potentialMatch =
                        SqliteObjectMapper.constructEnrollmentDataFromCursor(cursor);
                // EncryptionKeyUrl contains adtech site
                String pasUriString = potentialMatch.getEncryptionKeyUrl();
                try {
                    // Make sure the URI can be parsed and the parsed host matches the ad tech
                    String pasUriHost = Uri.parse(pasUriString).getHost();
                    if (originalUriHost.equalsIgnoreCase(pasUriHost)
                            || originalUriHost
                                    .toLowerCase(Locale.ENGLISH)
                                    .endsWith("." + pasUriHost.toLowerCase(Locale.ENGLISH))) {
                        LogUtil.v(
                                "Found positive match PAS URL \"%s\" for given URI \"%s\"",
                                pasUriString, originalUri);
                        mEnrollmentUtil.logEnrollmentMatchStats(mLogger, true, buildId);

                        // AdTechIdentifiers are currently expected to only contain eTLD+1
                        return new Pair<>(
                                AdTechIdentifier.fromString(originalUriHost), potentialMatch);
                    }
                } catch (IllegalArgumentException exception) {
                    LogUtil.v(
                            "Error while matching URI %s to PAS URI %s; skipping URI. "
                                    + "Error message: %s",
                            originalUri, pasUriString, exception.getMessage());
                }
            }

            mEnrollmentUtil.logEnrollmentMatchStats(mLogger, /* isSuccessful= */ false, buildId);
            return null;
        }
    }

    @Override
    @Nullable
    // TODO (b/325661647): Remove if not used in PAS implementation
    public EnrollmentData getEnrollmentDataForPASByAdTechIdentifier(
            AdTechIdentifier adTechIdentifier) {
        int buildId = mEnrollmentUtil.getBuildId();
        String adTechIdentifierString = adTechIdentifier.toString();
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        if (db == null) {
            mEnrollmentUtil.logEnrollmentDataStats(
                    mLogger, READ_QUERY, /* isSuccessful= */ false, buildId);
            return null;
        }
        mEnrollmentUtil.logEnrollmentDataStats(
                mLogger, READ_QUERY, /* isSuccessful= */ true, buildId);

        try (Cursor cursor =
                db.query(
                        EnrollmentTables.EnrollmentDataContract.TABLE,
                        /*columns=*/ null,
                        EnrollmentTables.EnrollmentDataContract.COMPANY_ID
                                + " LIKE '%"
                                + "PRIVACY_SANDBOX_API_PROTECTED_APP_SIGNALS"
                                + "%'"
                                + " AND "
                                + EnrollmentTables.EnrollmentDataContract.ENCRYPTION_KEY_URL
                                + " LIKE '%"
                                + adTechIdentifierString
                                + "%'",
                        null,
                        /*groupBy=*/ null,
                        /*having=*/ null,
                        /*orderBy=*/ null,
                        /*limit=*/ null)) {
            if (cursor == null || cursor.getCount() <= 0) {
                LogUtil.d(
                        "Failed to match enrollment for ad tech identifier \"%s\"",
                        adTechIdentifierString);
                mEnrollmentUtil.logEnrollmentMatchStats(
                        mLogger, /* isSuccessful= */ false, buildId);
                return null;
            }

            LogUtil.v(
                    "Found %d rows potentially matching ad tech identifier \"%s\"",
                    cursor.getCount(), adTechIdentifierString);

            while (cursor.moveToNext()) {
                EnrollmentData potentialMatch =
                        SqliteObjectMapper.constructEnrollmentDataFromCursor(cursor);
                // EncryptionKeyUrl contains adtech site
                String pasUriString = potentialMatch.getEncryptionKeyUrl();
                try {
                    // Make sure the URI can be parsed and the parsed host matches the ad tech
                    if (adTechIdentifierString.equalsIgnoreCase(
                            Uri.parse(pasUriString).getHost())) {
                        LogUtil.v(
                                "Found positive match PAS URL \"%s\" for ad tech identifier"
                                        + " \"%s\"",
                                pasUriString, adTechIdentifierString);
                        mEnrollmentUtil.logEnrollmentMatchStats(
                                mLogger, /* isSuccessful= */ true, buildId);

                        return potentialMatch;
                    }
                } catch (IllegalArgumentException exception) {
                    LogUtil.v(
                            "Error while matching ad tech %s to PAS URI %s; skipping"
                                    + " URI. Error message: %s",
                            adTechIdentifierString, pasUriString, exception.getMessage());
                }
            }
            mEnrollmentUtil.logEnrollmentMatchStats(mLogger, /* isSuccessful= */ false, buildId);
            return null;
        }
    }

    @Override
    @NonNull
    public Set<AdTechIdentifier> getAllPASEnrolledAdTechs() {
        int buildId = mEnrollmentUtil.getBuildId();
        Set<AdTechIdentifier> enrolledAdTechIdentifiers = new HashSet<>();
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        if (db == null) {
            mEnrollmentUtil.logEnrollmentDataStats(
                    mLogger, READ_QUERY, /* isSuccessful= */ false, buildId);
            return enrolledAdTechIdentifiers;
        }
        mEnrollmentUtil.logEnrollmentDataStats(
                mLogger, READ_QUERY, /* isSuccessful= */ true, buildId);

        try (Cursor cursor =
                db.query(
                        /*distinct=*/ true,
                        /*table=*/ EnrollmentTables.EnrollmentDataContract.TABLE,
                        /*columns=*/ new String[] {
                            EnrollmentTables.EnrollmentDataContract.ENCRYPTION_KEY_URL
                        },
                        /*selection=*/ EnrollmentTables.EnrollmentDataContract.COMPANY_ID
                                + " LIKE '%"
                                + "PRIVACY_SANDBOX_API_PROTECTED_APP_SIGNALS"
                                + "%'",
                        /*selectionArgs=*/ null,
                        /*groupBy=*/ null,
                        /*having=*/ null,
                        /*orderBy=*/ null,
                        /*limit=*/ null)) {
            if (cursor == null || cursor.getCount() <= 0) {
                LogUtil.d("Failed to find any PAS-enrolled ad techs");
                return enrolledAdTechIdentifiers;
            }

            LogUtil.v("Found %d PAS enrollment entries", cursor.getCount());

            while (cursor.moveToNext()) {
                enrolledAdTechIdentifiers.add(
                        SqliteObjectMapper.getAdTechIdentifierFromPASCursor(cursor));
            }

            LogUtil.v(
                    "Found %d PAS enrolled ad tech identifiers", enrolledAdTechIdentifiers.size());

            return enrolledAdTechIdentifiers;
        }
    }

    @Override
    public Long getEnrollmentRecordsCount() {
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return null;
        }
        Long count =
                DatabaseUtils.queryNumEntries(db, EnrollmentTables.EnrollmentDataContract.TABLE);
        return count;
    }

    @Override
    public int getEnrollmentRecordCountForLogging() {
        int limitedLoggingEnabled = -2;
        int dbError = -1;
        if (mFlags.getEnrollmentEnableLimitedLogging()) {
            return limitedLoggingEnabled;
        }
        Long count = getEnrollmentRecordsCount();
        if (count == null) {
            return dbError;
        }
        return count.intValue();
    }

    @Override
    public boolean insert(EnrollmentData enrollmentData) {
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            int buildId = mEnrollmentUtil.getBuildId();
            mEnrollmentUtil.logEnrollmentDataStats(mLogger, WRITE_QUERY, false, buildId);
            return false;
        }
        try {
            insertToDb(enrollmentData, db);
        } catch (SQLException e) {
            LogUtil.e(e, "Failed to insert EnrollmentData.");
            return false;
        }
        return true;
    }

    private void insertToDb(EnrollmentData enrollmentData, SQLiteDatabase db) throws SQLException {
        ContentValues values = new ContentValues();
        values.put(
                EnrollmentTables.EnrollmentDataContract.ENROLLMENT_ID,
                enrollmentData.getEnrollmentId());
        // Deprecating company_id, temporarily being reused by enrolled_apis
        values.put(
                EnrollmentTables.EnrollmentDataContract.COMPANY_ID,
                enrollmentData.getEnrolledAPIsString());
        values.put(
                EnrollmentTables.EnrollmentDataContract.SDK_NAMES,
                String.join(" ", enrollmentData.getSdkNames()));
        values.put(
                EnrollmentTables.EnrollmentDataContract.ATTRIBUTION_SOURCE_REGISTRATION_URL,
                String.join(" ", enrollmentData.getAttributionSourceRegistrationUrl()));
        values.put(
                EnrollmentTables.EnrollmentDataContract.ATTRIBUTION_TRIGGER_REGISTRATION_URL,
                String.join(" ", enrollmentData.getAttributionTriggerRegistrationUrl()));
        values.put(
                EnrollmentTables.EnrollmentDataContract.ATTRIBUTION_REPORTING_URL,
                String.join(" ", enrollmentData.getAttributionReportingUrl()));
        values.put(
                EnrollmentTables.EnrollmentDataContract.REMARKETING_RESPONSE_BASED_REGISTRATION_URL,
                String.join(" ", enrollmentData.getRemarketingResponseBasedRegistrationUrl()));
        values.put(
                EnrollmentTables.EnrollmentDataContract.ENCRYPTION_KEY_URL,
                enrollmentData.getEncryptionKeyUrl());
        LogUtil.d("Inserting Enrollment record. ID : \"%s\"", enrollmentData.getEnrollmentId());
        try {
            db.insertWithOnConflict(
                    EnrollmentTables.EnrollmentDataContract.TABLE,
                    /*nullColumnHack=*/ null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE);
        } catch (SQLException e) {
            int buildId = mEnrollmentUtil.getBuildId();
            mEnrollmentUtil.logEnrollmentDataStats(mLogger, WRITE_QUERY, false, buildId);
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_DATA_INSERT_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
            throw e;
        }
        int buildId = mEnrollmentUtil.getBuildId();
        mEnrollmentUtil.logEnrollmentDataStats(mLogger, WRITE_QUERY, true, buildId);
    }

    @Override
    public boolean delete(String enrollmentId) {
        Objects.requireNonNull(enrollmentId);
        int buildId = mEnrollmentUtil.getBuildId();
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            mEnrollmentUtil.logEnrollmentDataStats(mLogger, WRITE_QUERY, false, buildId);
            return false;
        }

        try {
            db.delete(
                    EnrollmentTables.EnrollmentDataContract.TABLE,
                    EnrollmentTables.EnrollmentDataContract.ENROLLMENT_ID + " = ?",
                    new String[] {enrollmentId});
        } catch (SQLException e) {
            LogUtil.e(e, "Failed to delete EnrollmentData.");
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_DATA_DELETE_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
            mEnrollmentUtil.logEnrollmentDataStats(mLogger, WRITE_QUERY, false, buildId);
            return false;
        }
        mEnrollmentUtil.logEnrollmentDataStats(mLogger, WRITE_QUERY, true, buildId);
        return true;
    }

    /** Deletes the whole EnrollmentData table. */
    @Override
    public boolean deleteAll() {
        boolean success = false;
        int buildId = mEnrollmentUtil.getBuildId();
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            mEnrollmentUtil.logEnrollmentDataStats(mLogger, WRITE_QUERY, success, buildId);
            return success;
        }

        // Handle this in a transaction.
        db.beginTransaction();
        try {
            db.delete(EnrollmentTables.EnrollmentDataContract.TABLE, null, null);
            success = true;
            unSeed();
            // Mark the transaction successful.
            db.setTransactionSuccessful();
        } catch (SQLiteException e) {
            LogUtil.e(e, "Failed to perform delete all on EnrollmentData.");
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_DATA_DELETE_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
            mEnrollmentUtil.logEnrollmentDataStats(mLogger, WRITE_QUERY, success, buildId);
        } finally {
            db.endTransaction();
        }
        mEnrollmentUtil.logEnrollmentDataStats(mLogger, WRITE_QUERY, success, buildId);
        return success;
    }

    @Override
    public boolean overwriteData(List<EnrollmentData> newEnrollments) {
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return false;
        }

        boolean success = false;
        db.beginTransaction();
        String[] ids =
                newEnrollments.stream().map(EnrollmentData::getEnrollmentId).toArray(String[]::new);
        try {
            db.delete(
                    EnrollmentTables.EnrollmentDataContract.TABLE,
                    EnrollmentTables.EnrollmentDataContract.ENROLLMENT_ID + " NOT IN (?)",
                    new String[] {String.join(",", ids)});
            db.setTransactionSuccessful();
        } catch (SQLException e) {
            LogUtil.e(e, "Failed to delete EnrollmentData during overwriting.");
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_DATA_DELETE_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
        } finally {
            db.endTransaction();
        }

        db.beginTransaction();
        try {
            for (EnrollmentData enrollmentData : newEnrollments) {
                insertToDb(enrollmentData, db);
            }
            // Mark the transaction successful.
            db.setTransactionSuccessful();
            unSeed();
            success = true;
        } catch (SQLException e) {
            LogUtil.e(e, "Failed to overwrite EnrollmentData.");
        } finally {
            db.endTransaction();
        }
        // TODO (b/289506805) Look at extracting Seeding logic out of EnrollmentDao
        if (mFlags.isEnableEnrollmentTestSeed()) {
            seed();
        }
        return success;
    }
}

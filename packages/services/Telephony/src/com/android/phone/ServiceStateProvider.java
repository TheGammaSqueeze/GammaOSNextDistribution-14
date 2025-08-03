/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.phone;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.provider.Telephony.ServiceStateTable;
import static android.provider.Telephony.ServiceStateTable.CONTENT_URI;
import static android.provider.Telephony.ServiceStateTable.DATA_NETWORK_TYPE;
import static android.provider.Telephony.ServiceStateTable.DATA_REG_STATE;
import static android.provider.Telephony.ServiceStateTable.DUPLEX_MODE;
import static android.provider.Telephony.ServiceStateTable.IS_MANUAL_NETWORK_SELECTION;
import static android.provider.Telephony.ServiceStateTable.VOICE_REG_STATE;
import static android.provider.Telephony.ServiceStateTable.getUriForSubscriptionId;
import static android.provider.Telephony.ServiceStateTable.getUriForSubscriptionIdAndField;

import android.Manifest;
import android.app.compat.CompatChanges;
import android.compat.annotation.ChangeId;
import android.compat.annotation.EnabledAfter;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MatrixCursor.RowBuilder;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Parcel;
import android.telephony.LocationAccessPolicy;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.TelephonyPermissions;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The class to provide base facility to access ServiceState related content,
 * which is stored in a SQLite database.
 */
public class ServiceStateProvider extends ContentProvider {
    private static final String TAG = "ServiceStateProvider";

    public static final String AUTHORITY = ServiceStateTable.AUTHORITY;
    public static final Uri AUTHORITY_URI = Uri.parse("content://" + AUTHORITY);

    /**
     * The current service state.
     *
     * This is the entire {@link ServiceState} object in byte array.
     *
     * @hide
     */
    public static final String SERVICE_STATE = "service_state";

    /**
     * An integer value indicating the current voice roaming type.
     * <p>
     * This is the same as {@link ServiceState#getVoiceRoamingType()}.
     * @hide
     */
    public static final String VOICE_ROAMING_TYPE = "voice_roaming_type";

    /**
     * An integer value indicating the current data roaming type.
     * <p>
     * This is the same as {@link ServiceState#getDataRoamingType()}.
     * @hide
     */
    public static final String DATA_ROAMING_TYPE = "data_roaming_type";

    /**
     * The current registered voice network operator name in long alphanumeric format.
     * <p>
     * This is the same as {@link ServiceState#getOperatorAlphaLong()}.
     * @hide
     */
    public static final String VOICE_OPERATOR_ALPHA_LONG = "voice_operator_alpha_long";

    /**
     * The current registered operator name in short alphanumeric format.
     * <p>
     * In GSM/UMTS, short format can be up to 8 characters long. The current registered voice
     * network operator name in long alphanumeric format.
     * <p>
     * This is the same as {@link ServiceState#getOperatorAlphaShort()}.
     * @hide
     */
    public static final String VOICE_OPERATOR_ALPHA_SHORT = "voice_operator_alpha_short";

    /**
     * The current registered operator numeric id.
     * <p>
     * In GSM/UMTS, numeric format is 3 digit country code plus 2 or 3 digit
     * network code.
     * <p>
     * This is the same as {@link ServiceState#getOperatorNumeric()}.
     */
    public static final String VOICE_OPERATOR_NUMERIC = "voice_operator_numeric";

    /**
     * The current registered data network operator name in long alphanumeric format.
     * <p>
     * This is the same as {@link ServiceState#getOperatorAlphaLong()}.
     * @hide
     */
    public static final String DATA_OPERATOR_ALPHA_LONG = "data_operator_alpha_long";

    /**
     * The current registered data network operator name in short alphanumeric format.
     * <p>
     * This is the same as {@link ServiceState#getOperatorAlphaShort()}.
     * @hide
     */
    public static final String DATA_OPERATOR_ALPHA_SHORT = "data_operator_alpha_short";

    /**
     * The current registered data network operator numeric id.
     * <p>
     * This is the same as {@link ServiceState#getOperatorNumeric()}.
     * @hide
     */
    public static final String DATA_OPERATOR_NUMERIC = "data_operator_numeric";

    /**
     * This is the same as {@link ServiceState#getRilVoiceRadioTechnology()}.
     * @hide
     */
    public static final String RIL_VOICE_RADIO_TECHNOLOGY = "ril_voice_radio_technology";

    /**
     * This is the same as {@link ServiceState#getRilDataRadioTechnology()}.
     * @hide
     */
    public static final String RIL_DATA_RADIO_TECHNOLOGY = "ril_data_radio_technology";

    /**
     * This is the same as {@link ServiceState#getCssIndicator()}.
     * @hide
     */
    public static final String CSS_INDICATOR = "css_indicator";

    /**
     * This is the same as {@link ServiceState#getCdmaNetworkId()}.
     * @hide
     */
    public static final String NETWORK_ID = "network_id";

    /**
     * This is the same as {@link ServiceState#getCdmaSystemId()}.
     * @hide
     */
    public static final String SYSTEM_ID = "system_id";

    /**
     * This is the same as {@link ServiceState#getCdmaRoamingIndicator()}.
     * @hide
     */
    public static final String CDMA_ROAMING_INDICATOR = "cdma_roaming_indicator";

    /**
     * This is the same as {@link ServiceState#getCdmaDefaultRoamingIndicator()}.
     * @hide
     */
    public static final String CDMA_DEFAULT_ROAMING_INDICATOR =
            "cdma_default_roaming_indicator";

    /**
     * This is the same as {@link ServiceState#getCdmaEriIconIndex()}.
     * @hide
     */
    public static final String CDMA_ERI_ICON_INDEX = "cdma_eri_icon_index";

    /**
     * This is the same as {@link ServiceState#getCdmaEriIconMode()}.
     * @hide
     */
    public static final String CDMA_ERI_ICON_MODE = "cdma_eri_icon_mode";

    /**
     * This is the same as {@link ServiceState#isEmergencyOnly()}.
     * @hide
     */
    public static final String IS_EMERGENCY_ONLY = "is_emergency_only";

    /**
     * This is the same as {@link ServiceState#getDataRoamingFromRegistration()}.
     * @hide
     */
    public static final String IS_DATA_ROAMING_FROM_REGISTRATION =
            "is_data_roaming_from_registration";

    /**
     * This is the same as {@link ServiceState#isUsingCarrierAggregation()}.
     * @hide
     */
    public static final String IS_USING_CARRIER_AGGREGATION = "is_using_carrier_aggregation";

    /**
     * The current registered raw data network operator name in long alphanumeric format.
     * <p>
     * This is the same as {@link ServiceState#getOperatorAlphaLongRaw()}.
     * @hide
     */
    public static final String OPERATOR_ALPHA_LONG_RAW = "operator_alpha_long_raw";

    /**
     * The current registered raw data network operator name in short alphanumeric format.
     * <p>
     * This is the same as {@link ServiceState#getOperatorAlphaShortRaw()}.
     * @hide
     */
    public static final String OPERATOR_ALPHA_SHORT_RAW = "operator_alpha_short_raw";

    /**
     * If the change Id is enabled, location permission is required to access location sensitive
     * columns in the ServiceStateTable.
     */
    @ChangeId
    @EnabledAfter(targetSdkVersion = Build.VERSION_CODES.R)
    @VisibleForTesting
    /* package */ static final long ENFORCE_LOCATION_PERMISSION_CHECK = 191911306;

    private final HashMap<Integer, ServiceState> mServiceStates = new HashMap<>();

    @VisibleForTesting
    /* package */ static final String[] ALL_COLUMNS = {
        VOICE_REG_STATE,
        DATA_REG_STATE,
        VOICE_ROAMING_TYPE,
        DATA_ROAMING_TYPE,
        VOICE_OPERATOR_ALPHA_LONG,
        VOICE_OPERATOR_ALPHA_SHORT,
        VOICE_OPERATOR_NUMERIC,
        DATA_OPERATOR_ALPHA_LONG,
        DATA_OPERATOR_ALPHA_SHORT,
        DATA_OPERATOR_NUMERIC,
        IS_MANUAL_NETWORK_SELECTION,
        RIL_VOICE_RADIO_TECHNOLOGY,
        RIL_DATA_RADIO_TECHNOLOGY,
        CSS_INDICATOR,
        NETWORK_ID,
        SYSTEM_ID,
        CDMA_ROAMING_INDICATOR,
        CDMA_DEFAULT_ROAMING_INDICATOR,
        CDMA_ERI_ICON_INDEX,
        CDMA_ERI_ICON_MODE,
        IS_EMERGENCY_ONLY,
        IS_USING_CARRIER_AGGREGATION,
        OPERATOR_ALPHA_LONG_RAW,
        OPERATOR_ALPHA_SHORT_RAW,
        DATA_NETWORK_TYPE,
        DUPLEX_MODE,
    };

    /**
     * Columns that are exposed to public surface.
     * These are the columns accessible to apps target S+ and lack
     * {@link android.Manifest.permission#READ_PRIVILEGED_PHONE_STATE} permission.
     */
    @VisibleForTesting
    /* package */ static final String[] PUBLIC_COLUMNS = {
            VOICE_REG_STATE,
            DATA_REG_STATE,
            VOICE_OPERATOR_NUMERIC,
            IS_MANUAL_NETWORK_SELECTION,
            DATA_NETWORK_TYPE,
            DUPLEX_MODE
    };

    /**
     * Columns protected by location permissions (either FINE or COARSE).
     * SecurityException will throw if applications without location permissions try to put those
     * columns explicitly into cursor (e.g. through {@code projection} parameter in
     * {@link #query(Uri, String[], String, String[], String)} method).
     * Default (scrub-out) value will return if applications try to put all columns into cursor by
     * specifying null of {@code projection} parameter and get values through the returned cursor.
     */
    private static final Set<String> LOCATION_PROTECTED_COLUMNS_SET = Set.of(
            NETWORK_ID,
            SYSTEM_ID
    );

    @Override
    public boolean onCreate() {
        return true;
    }

    /**
     * Returns the {@link ServiceState} information on specified subscription.
     *
     * @param subId whose subscriber id is returned
     * @return the {@link ServiceState} information on specified subscription.
     */
    @VisibleForTesting
    public ServiceState getServiceState(int subId) {
        return mServiceStates.get(subId);
    }

    /**
     * Returns the system's default subscription id.
     *
     * @return the "system" default subscription id.
     */
    @VisibleForTesting
    public int getDefaultSubId() {
        return SubscriptionManager.getDefaultSubscriptionId();
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        if (isPathPrefixMatch(uri, CONTENT_URI)) {
            // Parse the subId
            int subId = 0;
            try {
                subId = Integer.parseInt(uri.getLastPathSegment());
            } catch (NumberFormatException e) {
                Log.e(TAG, "insert: no subId provided in uri");
                throw e;
            }
            Log.d(TAG, "subId=" + subId);

            // handle DEFAULT_SUBSCRIPTION_ID
            if (subId == SubscriptionManager.DEFAULT_SUBSCRIPTION_ID) {
                subId = getDefaultSubId();
            }

            final Parcel p = Parcel.obtain();
            final byte[] rawBytes = values.getAsByteArray(SERVICE_STATE);
            p.unmarshall(rawBytes, 0, rawBytes.length);
            p.setDataPosition(0);

            // create the new service state
            final ServiceState newSS = ServiceState.CREATOR.createFromParcel(p);

            // notify listeners
            // if ss is null (e.g. first service state update) we will notify for all fields
            ServiceState ss = getServiceState(subId);
            notifyChangeForSubIdAndField(getContext(), ss, newSS, subId);
            notifyChangeForSubId(getContext(), ss, newSS, subId);

            // store the new service state
            mServiceStates.put(subId, newSS);
            return uri;
        }
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public String getType(Uri uri) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        if (!isPathPrefixMatch(uri, CONTENT_URI)) {
            throw new IllegalArgumentException("Invalid URI: " + uri);
        } else {
            // Parse the subId
            int subId = 0;
            try {
                subId = Integer.parseInt(uri.getLastPathSegment());
            } catch (NumberFormatException e) {
                Log.d(TAG, "query: no subId provided in uri, using default.");
                subId = getDefaultSubId();
            }
            Log.d(TAG, "subId=" + subId);

            // handle DEFAULT_SUBSCRIPTION_ID
            if (subId == SubscriptionManager.DEFAULT_SUBSCRIPTION_ID) {
                subId = getDefaultSubId();
            }

            // Get the service state
            ServiceState unredactedServiceState = getServiceState(subId);
            if (unredactedServiceState == null) {
                Log.d(TAG, "returning null");
                return null;
            }

            final boolean enforceLocationPermission =
                    CompatChanges.isChangeEnabled(ENFORCE_LOCATION_PERMISSION_CHECK);
            final boolean targetingAtLeastS = TelephonyPermissions.getTargetSdk(getContext(),
                    getCallingPackage()) >= Build.VERSION_CODES.S;
            final boolean canReadPrivilegedPhoneState = getContext().checkCallingOrSelfPermission(
                    Manifest.permission.READ_PRIVILEGED_PHONE_STATE) == PERMISSION_GRANTED;

            final String[] availableColumns;
            final ServiceState ss;
            if (enforceLocationPermission && targetingAtLeastS && !canReadPrivilegedPhoneState) {
                // targetSdkVersion S+ without read privileged phone state permission can only
                // access public columns which have no location sensitive info.
                availableColumns = PUBLIC_COLUMNS;
                ss = unredactedServiceState;
            } else {
                availableColumns = ALL_COLUMNS;
                if (!enforceLocationPermission) {
                    // No matter the targetSdkVersion, return unredacted ServiceState if location
                    // permission enforcement is not introduced
                    ss = unredactedServiceState;
                } else {
                    boolean implicitlyQueryLocation = projection == null;
                    boolean explicitlyQueryLocation = false;
                    if (projection != null) {
                        for (String requiredColumn : projection) {
                            if (LOCATION_PROTECTED_COLUMNS_SET.contains(requiredColumn)) {
                                explicitlyQueryLocation = true;
                                break;
                            }
                        }
                    }

                    // Check location permission only when location sensitive info are queried
                    // (either explicitly or implicitly) to avoid caller get blamed with location
                    // permission when query non sensitive info.
                    if (implicitlyQueryLocation || explicitlyQueryLocation) {
                        if (hasLocationPermission()) {
                            ss = unredactedServiceState;
                        } else {
                            if (targetingAtLeastS) {
                                // Throw SecurityException to fail loudly if caller is targetSDK S+
                                throw new SecurityException(
                                        "Querying location sensitive info requires location "
                                                + "permissions");
                            } else {
                                // For backward compatibility, return redacted value for old SDK
                                ss = getLocationRedactedServiceState(unredactedServiceState);
                            }
                        }
                    } else {
                        // The caller is not interested in location sensitive info, return result
                        // that scrub out all sensitive info. And no permission check is needed.
                        ss = getLocationRedactedServiceState(unredactedServiceState);
                    }
                }
            }

            // Build the result
            final int voice_reg_state = ss.getState();
            final int data_reg_state = ss.getDataRegistrationState();
            final int voice_roaming_type = ss.getVoiceRoamingType();
            final int data_roaming_type = ss.getDataRoamingType();
            final String voice_operator_alpha_long = ss.getOperatorAlphaLong();
            final String voice_operator_alpha_short = ss.getOperatorAlphaShort();
            final String voice_operator_numeric = ss.getOperatorNumeric();
            final String data_operator_alpha_long = ss.getOperatorAlphaLong();
            final String data_operator_alpha_short = ss.getOperatorAlphaShort();
            final String data_operator_numeric = ss.getOperatorNumeric();
            final int is_manual_network_selection = (ss.getIsManualSelection()) ? 1 : 0;
            final int ril_voice_radio_technology = ss.getRilVoiceRadioTechnology();
            final int ril_data_radio_technology = ss.getRilDataRadioTechnology();
            final int css_indicator = ss.getCssIndicator();
            final int network_id = ss.getCdmaNetworkId();
            final int system_id = ss.getCdmaSystemId();
            final int cdma_roaming_indicator = ss.getCdmaRoamingIndicator();
            final int cdma_default_roaming_indicator = ss.getCdmaDefaultRoamingIndicator();
            final int cdma_eri_icon_index = ss.getCdmaEriIconIndex();
            final int cdma_eri_icon_mode = ss.getCdmaEriIconMode();
            final int is_emergency_only = (ss.isEmergencyOnly()) ? 1 : 0;
            final int is_using_carrier_aggregation = (ss.isUsingCarrierAggregation()) ? 1 : 0;
            final String operator_alpha_long_raw = ss.getOperatorAlphaLongRaw();
            final String operator_alpha_short_raw = ss.getOperatorAlphaShortRaw();
            final int data_network_type = ss.getDataNetworkType();
            final int duplex_mode = ss.getDuplexMode();

            Object[] data = availableColumns == ALL_COLUMNS ? new Object[]{
                    // data for all columns
                    voice_reg_state,
                    data_reg_state,
                    voice_roaming_type,
                    data_roaming_type,
                    voice_operator_alpha_long,
                    voice_operator_alpha_short,
                    voice_operator_numeric,
                    data_operator_alpha_long,
                    data_operator_alpha_short,
                    data_operator_numeric,
                    is_manual_network_selection,
                    ril_voice_radio_technology,
                    ril_data_radio_technology,
                    css_indicator,
                    network_id,
                    system_id,
                    cdma_roaming_indicator,
                    cdma_default_roaming_indicator,
                    cdma_eri_icon_index,
                    cdma_eri_icon_mode,
                    is_emergency_only,
                    is_using_carrier_aggregation,
                    operator_alpha_long_raw,
                    operator_alpha_short_raw,
                    data_network_type,
                    duplex_mode,
            } : new Object[]{
                    // data for public columns only
                    voice_reg_state,
                    data_reg_state,
                    voice_operator_numeric,
                    is_manual_network_selection,
                    data_network_type,
                    duplex_mode,
            };

            return buildSingleRowResult(projection, availableColumns, data);
        }
    }

    private static Cursor buildSingleRowResult(String[] projection, String[] availableColumns,
            Object[] data) {
        if (projection == null) {
            projection = availableColumns;
        }
        final MatrixCursor c = new MatrixCursor(projection, 1);
        final RowBuilder row = c.newRow();
        for (int i = 0; i < c.getColumnCount(); i++) {
            final String columnName = c.getColumnName(i);
            boolean found = false;
            for (int j = 0; j < availableColumns.length; j++) {
                if (availableColumns[j].equals(columnName)) {
                    row.add(data[j]);
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new IllegalArgumentException("Invalid column " + projection[i]);
            }
        }
        return c;
    }

    /**
     * Notify interested apps that certain fields of the ServiceState have changed.
     *
     * Apps which want to wake when specific fields change can use
     * JobScheduler's TriggerContentUri.  This replaces the waking functionality of the implicit
     * broadcast of ACTION_SERVICE_STATE_CHANGED for apps targeting version O.
     *
     * We will only notify for certain fields. This is an intentional change from the behavior of
     * the broadcast. Listeners will be notified when the voice or data registration state or
     * roaming type changes.
     */
    @VisibleForTesting
    public static void notifyChangeForSubIdAndField(Context context, ServiceState oldSS,
            ServiceState newSS, int subId) {
        final boolean firstUpdate = (oldSS == null) ? true : false;

        // for every field, if the field has changed values, notify via the provider
        if (firstUpdate || voiceRegStateChanged(oldSS, newSS)) {
            context.getContentResolver().notifyChange(
                    getUriForSubscriptionIdAndField(subId, VOICE_REG_STATE),
                    /* observer= */ null, /* syncToNetwork= */ false);
        }
        if (firstUpdate || dataRegStateChanged(oldSS, newSS)) {
            context.getContentResolver().notifyChange(
                    getUriForSubscriptionIdAndField(subId, DATA_REG_STATE), null, false);
        }
        if (firstUpdate || voiceRoamingTypeChanged(oldSS, newSS)) {
            context.getContentResolver().notifyChange(
                    getUriForSubscriptionIdAndField(subId, VOICE_ROAMING_TYPE), null, false);
        }
        if (firstUpdate || dataRoamingTypeChanged(oldSS, newSS)) {
            context.getContentResolver().notifyChange(
                    getUriForSubscriptionIdAndField(subId, DATA_ROAMING_TYPE), null, false);
        }
        if (firstUpdate || dataNetworkTypeChanged(oldSS, newSS)) {
            context.getContentResolver().notifyChange(
                    getUriForSubscriptionIdAndField(subId, DATA_NETWORK_TYPE), null, false);
        }
    }

    private static boolean voiceRegStateChanged(ServiceState oldSS, ServiceState newSS) {
        return oldSS.getState() != newSS.getState();
    }

    private static boolean dataRegStateChanged(ServiceState oldSS, ServiceState newSS) {
        return oldSS.getDataRegistrationState() != newSS.getDataRegistrationState();
    }

    private static boolean voiceRoamingTypeChanged(ServiceState oldSS, ServiceState newSS) {
        return oldSS.getVoiceRoamingType() != newSS.getVoiceRoamingType();
    }

    private static boolean dataRoamingTypeChanged(ServiceState oldSS, ServiceState newSS) {
        return oldSS.getDataRoamingType() != newSS.getDataRoamingType();
    }

    private static boolean dataNetworkTypeChanged(ServiceState oldSS, ServiceState newSS) {
        return oldSS.getDataNetworkType() != newSS.getDataNetworkType();
    }

    /**
     * Notify interested apps that the ServiceState has changed.
     *
     * Apps which want to wake when any field in the ServiceState has changed can use
     * JobScheduler's TriggerContentUri.  This replaces the waking functionality of the implicit
     * broadcast of ACTION_SERVICE_STATE_CHANGED for apps targeting version O.
     *
     * We will only notify for certain fields. This is an intentional change from the behavior of
     * the broadcast. Listeners will only be notified when the voice/data registration state or
     * roaming type changes.
     */
    @VisibleForTesting
    public static void notifyChangeForSubId(Context context, ServiceState oldSS, ServiceState newSS,
            int subId) {
        // if the voice or data registration or roaming state field has changed values, notify via
        // the provider.
        // If oldSS is null and newSS is not (e.g. first update of service state) this will also
        // notify
        if (oldSS == null || voiceRegStateChanged(oldSS, newSS) || dataRegStateChanged(oldSS, newSS)
                || voiceRoamingTypeChanged(oldSS, newSS) || dataRoamingTypeChanged(oldSS, newSS)
                || dataNetworkTypeChanged(oldSS, newSS)) {
            context.getContentResolver().notifyChange(getUriForSubscriptionId(subId), null, false);
        }
    }

    /**
     * Test if this is a path prefix match against the given Uri. Verifies that
     * scheme, authority, and atomic path segments match.
     *
     * Copied from frameworks/base/core/java/android/net/Uri.java
     */
    private boolean isPathPrefixMatch(Uri uriA, Uri uriB) {
        if (!Objects.equals(uriA.getScheme(), uriB.getScheme())) return false;
        if (!Objects.equals(uriA.getAuthority(), uriB.getAuthority())) return false;

        List<String> segA = uriA.getPathSegments();
        List<String> segB = uriB.getPathSegments();

        final int size = segB.size();
        if (segA.size() < size) return false;

        for (int i = 0; i < size; i++) {
            if (!Objects.equals(segA.get(i), segB.get(i))) {
                return false;
            }
        }

        return true;
    }

    /**
     * Used to insert a ServiceState into the ServiceStateProvider as a ContentValues instance.
     *
     * @param state the ServiceState to convert into ContentValues
     * @return the convertedContentValues instance
     * @hide
     */
    public static ContentValues getContentValuesForServiceState(ServiceState state) {
        ContentValues values = new ContentValues();
        final Parcel p = Parcel.obtain();
        state.writeToParcel(p, 0);
        // Turn the parcel to byte array. Safe to do this because the content values were never
        // written into a persistent storage. ServiceStateProvider keeps values in the memory.
        values.put(SERVICE_STATE, p.marshall());
        return values;
    }

    /**
     * Check location permission with same policy as {@link TelephonyManager#getServiceState()}
     * which enforces location permission check starting from Q.
     */
    private boolean hasLocationPermission() {
        LocationAccessPolicy.LocationPermissionResult locationPermissionResult =
                LocationAccessPolicy.checkLocationPermission(getContext(),
                        new LocationAccessPolicy.LocationPermissionQuery.Builder()
                                .setCallingPackage(getCallingPackage())
                                .setCallingFeatureId(getCallingAttributionTag())
                                .setCallingPid(Binder.getCallingPid())
                                .setCallingUid(Binder.getCallingUid())
                                .setMethod("ServiceStateProvider#query")
                                .setLogAsInfo(true)
                                .setMinSdkVersionForFine(Build.VERSION_CODES.Q)
                                .setMinSdkVersionForCoarse(Build.VERSION_CODES.Q)
                                .setMinSdkVersionForEnforcement(Build.VERSION_CODES.Q)
                                .build());
        return locationPermissionResult == LocationAccessPolicy.LocationPermissionResult.ALLOWED;
    }

    // Return a copy of ServiceState with all sensitive info redacted.
    @VisibleForTesting
    /* package */ static ServiceState getLocationRedactedServiceState(ServiceState serviceState) {
        ServiceState ss =
                serviceState.createLocationInfoSanitizedCopy(true /*removeCoarseLocation*/);
        return ss;
    }
}

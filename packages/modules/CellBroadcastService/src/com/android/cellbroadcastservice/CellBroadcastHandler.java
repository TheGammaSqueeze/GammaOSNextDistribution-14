/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.cellbroadcastservice;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;

import static com.android.cellbroadcastservice.CbSendMessageCalculator.SEND_MESSAGE_ACTION_AMBIGUOUS;
import static com.android.cellbroadcastservice.CbSendMessageCalculator.SEND_MESSAGE_ACTION_DONT_SEND;
import static com.android.cellbroadcastservice.CbSendMessageCalculator.SEND_MESSAGE_ACTION_NO_COORDINATES;
import static com.android.cellbroadcastservice.CbSendMessageCalculator.SEND_MESSAGE_ACTION_SEND;
import static com.android.cellbroadcastservice.CbSendMessageCalculator.SEND_MESSAGE_ACTION_SENT;
import static com.android.cellbroadcastservice.CellBroadcastMetrics.ERRSRC_CBS;
import static com.android.cellbroadcastservice.CellBroadcastMetrics.ERRTYPE_FOUND_MULTIPLECBRPKGS;
import static com.android.cellbroadcastservice.CellBroadcastMetrics.ERRTYPE_NOTFOUND_DEFAULTCBRPKGS;
import static com.android.cellbroadcastservice.CellBroadcastMetrics.ERR_UNEXPECTED_CDMA_MSG_FROM_FWK;
import static com.android.cellbroadcastservice.CellBroadcastMetrics.FILTER_DUPLICATE;
import static com.android.cellbroadcastservice.CellBroadcastMetrics.FILTER_GEOFENCED;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationRequest;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Telephony;
import android.provider.Telephony.CellBroadcasts;
import android.telephony.CbGeoUtils.LatLng;
import android.telephony.CellBroadcastIntents;
import android.telephony.SmsCbMessage;
import android.telephony.SubscriptionManager;
import android.telephony.cdma.CdmaSmsCbProgramData;
import android.text.TextUtils;
import android.util.LocalLog;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.HandlerExecutor;
import com.android.modules.utils.build.SdkLevel;

import java.io.File;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Dispatch new Cell Broadcasts to receivers. Acquires a private wakelock until the broadcast
 * completes and our result receiver is called.
 */
public class CellBroadcastHandler extends WakeLockStateMachine {
    private static final String TAG = "CellBroadcastHandler";

    /**
     * CellBroadcast apex name
     */
    private static final String CB_APEX_NAME = "com.android.cellbroadcast";

    /**
     * CellBroadcast app platform name
     */
    private static final String CB_APP_PLATFORM_NAME = "CellBroadcastAppPlatform";

    /**
     * Path where CB apex is mounted (/apex/com.android.cellbroadcast)
     */
    private static final String CB_APEX_PATH = new File("/apex", CB_APEX_NAME).getAbsolutePath();

    private static final String EXTRA_MESSAGE = "message";

    /**
     * To disable cell broadcast duplicate detection for debugging purposes
     * <code>adb shell am broadcast -a com.android.cellbroadcastservice.action.DUPLICATE_DETECTION
     * --ez enable false</code>
     *
     * To enable cell broadcast duplicate detection for debugging purposes
     * <code>adb shell am broadcast -a com.android.cellbroadcastservice.action.DUPLICATE_DETECTION
     * --ez enable true</code>
     */
    private static final String ACTION_DUPLICATE_DETECTION =
            "com.android.cellbroadcastservice.action.DUPLICATE_DETECTION";

    /**
     * The extra for cell broadcast duplicate detection enable/disable
     */
    private static final String EXTRA_ENABLE = "enable";

    private final LocalLog mLocalLog = new LocalLog(100);

    private static final boolean IS_DEBUGGABLE = SystemProperties.getInt("ro.debuggable", 0) == 1;

    /**
     * Used to register receivers as exported to other apps on the device. The Context flag was
     * introduced in T, and all previous releases use a default value of 0 to export the receiver.
     */
    protected static final int RECEIVER_EXPORTED =
            SdkLevel.isAtLeastT() ? Context.RECEIVER_EXPORTED : 0;

    /** Uses to request the location update. */
    private final LocationRequester mLocationRequester;

    /** Used to inject new calculators during unit testing */
    @NonNull
    protected final CbSendMessageCalculatorFactory mCbSendMessageCalculatorFactory;

    /** Timestamp of last airplane mode on */
    protected long mLastAirplaneModeTime = 0;

    /** Resource cache used for test purpose, to be removed by b/223644462 */
    protected final Map<Integer, Resources> mResourcesCache = new HashMap<>();

    /** Whether performing duplicate detection or not. Note this is for debugging purposes only. */
    private boolean mEnableDuplicateDetection = true;

    /**
     * Service category equivalent map. The key is the GSM service category, the value is the CDMA
     * service category.
     */
    private final Map<Integer, Integer> mServiceCategoryCrossRATMap;

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case Intent.ACTION_AIRPLANE_MODE_CHANGED:
                    boolean airplaneModeOn = intent.getBooleanExtra("state", false);
                    if (airplaneModeOn) {
                        mLastAirplaneModeTime = System.currentTimeMillis();
                        log("Airplane mode on.");
                    }
                    break;
                case ACTION_DUPLICATE_DETECTION:
                    mEnableDuplicateDetection = intent.getBooleanExtra(EXTRA_ENABLE,
                            true);
                    log("Duplicate detection " + (mEnableDuplicateDetection
                            ? "enabled" : "disabled"));
                    break;
                default:
                    log("Unhandled broadcast " + intent.getAction());
            }
        }
    };

    private CellBroadcastHandler(Context context) {
        this(CellBroadcastHandler.class.getSimpleName(), context, Looper.myLooper(),
                new CbSendMessageCalculatorFactory(), null);
    }

    /**
     * Allows tests to inject new calculators
     */
    @VisibleForTesting
    public static class CbSendMessageCalculatorFactory {
        public CbSendMessageCalculatorFactory() {
        }

        /**
         * Creates new calculator
         *
         * @param context context
         * @param fences  the geo fences to use in the calculator
         * @return a new instance of the calculator
         */
        public CbSendMessageCalculator createNew(@NonNull final Context context,
                @NonNull final List<android.telephony.CbGeoUtils.Geometry> fences) {
            return new CbSendMessageCalculator(context, fences);
        }
    }

    @VisibleForTesting
    public CellBroadcastHandler(String debugTag, Context context, Looper looper,
            @NonNull final CbSendMessageCalculatorFactory cbSendMessageCalculatorFactory,
            @Nullable HandlerHelper handlerHelper) {
        super(debugTag, context, looper);

        if (handlerHelper == null) {
            // Would have preferred to not have handlerHelper has nullable and pass this through the
            // default ctor.  Had trouble doing this because getHander() can't be called until
            // the type is fully constructed.
            handlerHelper = new HandlerHelper(getHandler());
        }
        mCbSendMessageCalculatorFactory = cbSendMessageCalculatorFactory;
        mLocationRequester = new LocationRequester(
                context,
                (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE),
                handlerHelper, getName());

        // Adding GSM / CDMA service category mapping.
        mServiceCategoryCrossRATMap = Stream.of(new Integer[][] {
                // Presidential alert
                { SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL,
                        CdmaSmsCbProgramData.CATEGORY_CMAS_PRESIDENTIAL_LEVEL_ALERT},
                { SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL_LANGUAGE,
                        CdmaSmsCbProgramData.CATEGORY_CMAS_PRESIDENTIAL_LEVEL_ALERT},

                // Extreme alert
                { SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED,
                        CdmaSmsCbProgramData.CATEGORY_CMAS_EXTREME_THREAT},
                { SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED_LANGUAGE,
                        CdmaSmsCbProgramData.CATEGORY_CMAS_EXTREME_THREAT},
                { SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY,
                        CdmaSmsCbProgramData.CATEGORY_CMAS_EXTREME_THREAT},
                { SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY_LANGUAGE,
                        CdmaSmsCbProgramData.CATEGORY_CMAS_EXTREME_THREAT},

                // Severe alert
                { SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED,
                        CdmaSmsCbProgramData.CATEGORY_CMAS_SEVERE_THREAT},
                { SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED_LANGUAGE,
                        CdmaSmsCbProgramData.CATEGORY_CMAS_SEVERE_THREAT},
                { SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_LIKELY,
                        CdmaSmsCbProgramData.CATEGORY_CMAS_SEVERE_THREAT},
                { SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_LIKELY_LANGUAGE,
                        CdmaSmsCbProgramData.CATEGORY_CMAS_SEVERE_THREAT},
                { SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_OBSERVED,
                        CdmaSmsCbProgramData.CATEGORY_CMAS_SEVERE_THREAT},
                { SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_OBSERVED_LANGUAGE,
                        CdmaSmsCbProgramData.CATEGORY_CMAS_SEVERE_THREAT},
                { SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_LIKELY,
                        CdmaSmsCbProgramData.CATEGORY_CMAS_SEVERE_THREAT},
                { SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_LIKELY_LANGUAGE,
                        CdmaSmsCbProgramData.CATEGORY_CMAS_SEVERE_THREAT},
                { SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_OBSERVED,
                        CdmaSmsCbProgramData.CATEGORY_CMAS_SEVERE_THREAT},
                { SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_OBSERVED_LANGUAGE,
                        CdmaSmsCbProgramData.CATEGORY_CMAS_SEVERE_THREAT},
                { SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY,
                        CdmaSmsCbProgramData.CATEGORY_CMAS_SEVERE_THREAT},
                { SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY_LANGUAGE,
                        CdmaSmsCbProgramData.CATEGORY_CMAS_SEVERE_THREAT},

                // Amber alert
                { SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY,
                        CdmaSmsCbProgramData.CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY},
                { SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY_LANGUAGE,
                        CdmaSmsCbProgramData.CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY},

                // Monthly test alert
                { SmsCbConstants.MESSAGE_ID_CMAS_ALERT_REQUIRED_MONTHLY_TEST,
                        CdmaSmsCbProgramData.CATEGORY_CMAS_TEST_MESSAGE},
                { SmsCbConstants.MESSAGE_ID_CMAS_ALERT_REQUIRED_MONTHLY_TEST_LANGUAGE,
                        CdmaSmsCbProgramData.CATEGORY_CMAS_TEST_MESSAGE},
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        if (IS_DEBUGGABLE) {
            intentFilter.addAction(ACTION_DUPLICATE_DETECTION);
        }

        mContext.registerReceiver(mReceiver, intentFilter, RECEIVER_EXPORTED);
    }

    public void cleanup() {
        if (DBG) log("CellBroadcastHandler cleanup");
        mContext.unregisterReceiver(mReceiver);
    }

    /**
     * Create a new CellBroadcastHandler.
     * @param context the context to use for dispatching Intents
     * @return the new handler
     */
    public static CellBroadcastHandler makeCellBroadcastHandler(Context context) {
        CellBroadcastHandler handler = new CellBroadcastHandler(context);
        handler.start();
        return handler;
    }

    /**
     * Handle Cell Broadcast messages from {@code CdmaInboundSmsHandler}.
     * 3GPP-format Cell Broadcast messages sent from radio are handled in the subclass.
     *
     * @param message the message to process
     * @return true if need to wait for geo-fencing or an ordered broadcast was sent.
     */
    @Override
    protected boolean handleSmsMessage(Message message) {
        if (message.obj instanceof SmsCbMessage) {
            if (!isDuplicate((SmsCbMessage) message.obj)) {
                handleBroadcastSms((SmsCbMessage) message.obj);
                return true;
            } else {
                CellBroadcastServiceMetrics.getInstance()
                        .logMessageFiltered(FILTER_DUPLICATE, (SmsCbMessage) message.obj);

            }
            return false;
        } else {
            final String errorMessage =
                    "handleSmsMessage got object of type: " + message.obj.getClass().getName();
            loge(errorMessage);
            CellBroadcastServiceMetrics.getInstance().logMessageError(
                    ERR_UNEXPECTED_CDMA_MSG_FROM_FWK, errorMessage);
            return false;
        }
    }

    /**
     * Get the maximum time for waiting location.
     *
     * @param message Cell broadcast message
     * @return The maximum waiting time in second
     */
    protected int getMaxLocationWaitingTime(SmsCbMessage message) {
        int maximumTime = message.getMaximumWaitingDuration();
        if (maximumTime == SmsCbMessage.MAXIMUM_WAIT_TIME_NOT_SET) {
            Resources res = getResources(message.getSubscriptionId());
            maximumTime = res.getInteger(R.integer.max_location_waiting_time);
        }
        return maximumTime;
    }

    /**
     * Dispatch a Cell Broadcast message to listeners.
     * @param message the Cell Broadcast to broadcast
     */
    protected void handleBroadcastSms(SmsCbMessage message) {
        int slotIndex = message.getSlotIndex();

        // TODO: Database inserting can be time consuming, therefore this should be changed to
        // asynchronous.
        ContentValues cv = message.getContentValues();
        Uri uri = mContext.getContentResolver().insert(CellBroadcasts.CONTENT_URI, cv);

        if (message.needGeoFencingCheck()) {
            int maximumWaitingTime = getMaxLocationWaitingTime(message);
            if (DBG) {
                log("Requesting location for geo-fencing. serialNumber = "
                        + message.getSerialNumber() + ", maximumWaitingTime = "
                        + maximumWaitingTime);
            }

            CbSendMessageCalculator calculator =
                    mCbSendMessageCalculatorFactory.createNew(mContext, message.getGeometries());
            requestLocationUpdate(new LocationUpdateCallback() {
                @Override
                public void onLocationUpdate(@NonNull LatLng location,
                        float accuracy) {
                    if (VDBG) {
                        logd("onLocationUpdate: location=" + location
                                + ", acc=" + accuracy + ". "  + getMessageString(message));
                    }
                    performGeoFencing(message, uri, calculator, location, slotIndex,
                            accuracy);
                }

                @Override
                public boolean areAllMessagesHandled() {
                    return !isMessageInAmbiguousState(calculator);
                }

                @Override
                public void onLocationUnavailable() {
                    CellBroadcastHandler.this.onLocationUnavailable(
                            calculator, message, uri, slotIndex);
                }
            }, maximumWaitingTime);
        } else {
            if (DBG) {
                log("Broadcast the message directly because no geo-fencing required, "
                        + " needGeoFencing = " + message.needGeoFencingCheck() + ". "
                        + getMessageString(message));
            }
            broadcastMessage(message, uri, slotIndex);
        }
    }

    /**
     * Returns true if the message calculator is in a non-ambiguous state.
     *
     * </b>Note:</b> NO_COORDINATES is considered ambiguous because we received no information
     * in this case.
     * @param calculator the message calculator
     * @return whether or not the message is handled
     */
    protected boolean isMessageInAmbiguousState(CbSendMessageCalculator calculator) {
        return calculator.getAction() == SEND_MESSAGE_ACTION_AMBIGUOUS
                || calculator.getAction() == SEND_MESSAGE_ACTION_NO_COORDINATES;
    }

    /**
     * When location requester cannot send anymore updates, we look at the calculated action and
     * determine whether or not we should send it.
     *
     * see: {@code CellBroadcastHandler.LocationUpdateCallback#onLocationUnavailable} for more info.
     *
     * @param calculator the send message calculator
     * @param message the cell broadcast message received
     * @param uri the message's uri
     * @param slotIndex the slot
     */
    protected void onLocationUnavailable(CbSendMessageCalculator calculator, SmsCbMessage message,
            Uri uri, int slotIndex) {
        @CbSendMessageCalculator.SendMessageAction int action = calculator.getAction();
        if (DBG) {
            logd("onLocationUnavailable: action="
                    + CbSendMessageCalculator.getActionString(action) + ". "
                    + getMessageString(message));
        }

        if (isMessageInAmbiguousState(calculator)) {
            /* Case 1. If we reached the end of the location time out and we are still in an
                       ambiguous state or no coordinates state, we send the message.
               Case 2. If we don't have permissions, then no location was received and the
                       calculator's action is NO_COORDINATES, which means we also send. */
            broadcastGeofenceMessage(message, uri, slotIndex, calculator);
        } else if (action == SEND_MESSAGE_ACTION_DONT_SEND) {
            geofenceMessageNotRequired(message);
        }
    }

    /**
     * Check the location based on geographical scope defined in 3GPP TS 23.041 section 9.4.1.2.1.
     *
     * The Geographical Scope (GS) indicates the geographical area over which the Message Code
     * is unique, and the display mode. The CBS message is not necessarily broadcast by all cells
     * within the geographical area. When two CBS messages are received with identical Serial
     * Numbers/Message Identifiers in two different cells, the Geographical Scope may be used to
     * determine if the CBS messages are indeed identical.
     *
     * @param message The current message
     * @param messageToCheck The older message in the database to be checked
     * @return {@code true} if within the same area, otherwise {@code false}, which should be
     * be considered as a new message.
     */
    private boolean isSameLocation(SmsCbMessage message,
            SmsCbMessage messageToCheck) {
        if (message.getGeographicalScope() != messageToCheck.getGeographicalScope()) {
            return false;
        }

        // only cell wide (which means that if a message is displayed it is desirable that the
        // message is removed from the screen when the UE selects the next cell and if any CBS
        // message is received in the next cell it is to be regarded as "new").
        if (message.getGeographicalScope() == SmsCbMessage.GEOGRAPHICAL_SCOPE_CELL_WIDE_IMMEDIATE
                || message.getGeographicalScope() == SmsCbMessage.GEOGRAPHICAL_SCOPE_CELL_WIDE) {
            return message.getLocation().isInLocationArea(messageToCheck.getLocation());
        }

        // Service Area wide (which means that a CBS message with the same Message Code and Update
        // Number may or may not be "new" in the next cell according to whether the next cell is
        // in the same Service Area as the current cell)
        if (message.getGeographicalScope() == SmsCbMessage.GEOGRAPHICAL_SCOPE_LOCATION_AREA_WIDE) {
            if (!message.getLocation().getPlmn().equals(messageToCheck.getLocation().getPlmn())) {
                return false;
            }

            return message.getLocation().getLac() != -1
                    && message.getLocation().getLac() == messageToCheck.getLocation().getLac();
        }

        // PLMN wide (which means that the Message Code and/or Update Number must change in the
        // next cell, of the PLMN, for the CBS message to be "new". The CBS message is only relevant
        // to the PLMN in which it is broadcast, so any change of PLMN (including a change to
        // another PLMN which is an ePLMN) means the CBS message is "new")
        if (message.getGeographicalScope() == SmsCbMessage.GEOGRAPHICAL_SCOPE_PLMN_WIDE) {
            return !TextUtils.isEmpty(message.getLocation().getPlmn())
                    && message.getLocation().getPlmn().equals(
                    messageToCheck.getLocation().getPlmn());
        }

        return false;
    }

    /**
     * Check if the message is a duplicate
     *
     * @param message Cell broadcast message
     * @return {@code true} if this message is a duplicate
     */
    @VisibleForTesting
    public boolean isDuplicate(SmsCbMessage message) {
        if (!mEnableDuplicateDetection) {
            log("Duplicate detection was disabled for debugging purposes.");
            return false;
        }

        // Find the cell broadcast message identify by the message identifier and serial number
        // and is not broadcasted.
        String where = CellBroadcasts.RECEIVED_TIME + ">?";

        Resources res = getResources(message.getSubscriptionId());

        // Only consider cell broadcast messages received within certain period.
        // By default it's 24 hours.
        long expirationDuration = res.getInteger(R.integer.message_expiration_time);
        long dupCheckTime = System.currentTimeMillis() - expirationDuration;

        // Some carriers require reset duplication detection after airplane mode or reboot.
        if (res.getBoolean(R.bool.reset_on_power_cycle_or_airplane_mode)) {
            dupCheckTime = Long.max(dupCheckTime, mLastAirplaneModeTime);
            dupCheckTime = Long.max(dupCheckTime,
                    System.currentTimeMillis() - SystemClock.elapsedRealtime());
        }

        List<SmsCbMessage> cbMessages = new ArrayList<>();

        try (Cursor cursor = mContext.getContentResolver().query(CellBroadcasts.CONTENT_URI,
                CellBroadcastProvider.QUERY_COLUMNS,
                where,
                new String[] {Long.toString(dupCheckTime)},
                null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    cbMessages.add(SmsCbMessage.createFromCursor(cursor));
                }
            }
        }

        boolean compareMessageBody = res.getBoolean(R.bool.duplicate_compare_body);
        boolean compareServiceCategory = res.getBoolean(R.bool.duplicate_compare_service_category);
        boolean crossSimDuplicateDetection = res.getBoolean(R.bool.cross_sim_duplicate_detection);

        log("Found " + cbMessages.size() + " messages since "
                + DateFormat.getDateTimeInstance().format(dupCheckTime));
        log("compareMessageBody=" + compareMessageBody + ", compareServiceCategory="
                + compareServiceCategory + ", crossSimDuplicateDetection="
                + crossSimDuplicateDetection);
        for (SmsCbMessage messageToCheck : cbMessages) {
            // If messages are from different slots, then we only compare the message body.
            if (VDBG) log("Checking the message " + messageToCheck);
            if (crossSimDuplicateDetection
                    && message.getSubscriptionId() != messageToCheck.getSubscriptionId()) {
                if (TextUtils.equals(message.getMessageBody(), messageToCheck.getMessageBody())) {
                    log("Duplicate message detected from different slot. " + message);
                    return true;
                }
                if (VDBG) log("Not from the same slot.");
            } else {
                // Check serial number if message is from the same carrier.
                if (message.getSerialNumber() != messageToCheck.getSerialNumber()) {
                    if (VDBG) log("Serial number does not match.");
                    // Not a dup. Check next one.
                    continue;
                }

                // ETWS primary / secondary should be treated differently.
                if (message.isEtwsMessage() && messageToCheck.isEtwsMessage()
                        && message.getEtwsWarningInfo().isPrimary()
                        != messageToCheck.getEtwsWarningInfo().isPrimary()) {
                    if (VDBG) log("ETWS primary/secondary does not match.");
                    // Not a dup. Check next one.
                    continue;
                }

                // Check if the message category is different.
                if (compareServiceCategory
                        && message.getServiceCategory() != messageToCheck.getServiceCategory()
                        && !Objects.equals(mServiceCategoryCrossRATMap.get(
                                message.getServiceCategory()), messageToCheck.getServiceCategory())
                        && !Objects.equals(mServiceCategoryCrossRATMap.get(
                                messageToCheck.getServiceCategory()),
                        message.getServiceCategory())) {
                    if (VDBG) log("Category does not match.");
                    // Not a dup. Check next one.
                    continue;
                }

                // Check if the message location is different. Note this is only applicable to
                // 3GPP format cell broadcast messages.
                if (message.getMessageFormat() == SmsCbMessage.MESSAGE_FORMAT_3GPP
                        && messageToCheck.getMessageFormat() == SmsCbMessage.MESSAGE_FORMAT_3GPP
                        && !isSameLocation(message, messageToCheck)) {
                    if (VDBG) log("Location does not match.");
                    // Not a dup. Check next one.
                    continue;
                }

                // Compare message body if needed.
                if (!compareMessageBody || TextUtils.equals(
                        message.getMessageBody(), messageToCheck.getMessageBody())) {
                    log("Duplicate message detected. " + message);
                    return true;
                } else {
                    if (VDBG) log("Body does not match.");
                }
            }
        }

        log("Not a duplicate message. " + message);
        return false;
    }

    /**
     * Perform a geo-fencing check for {@code message}. Broadcast the {@code message} if the
     * {@code location} is inside the {@code broadcastArea}.
     * @param message the message need to geo-fencing check
     * @param uri the message's uri
     * @param calculator the message calculator
     * @param location current location
     * @param slotIndex the index of the slot
     * @param accuracy the accuracy of the coordinate given in meters
     */
    protected void performGeoFencing(SmsCbMessage message, Uri uri,
            CbSendMessageCalculator calculator, LatLng location, int slotIndex, float accuracy) {

        logd(calculator.toString() + ", current action="
                + CbSendMessageCalculator.getActionString(calculator.getAction()));

        if (calculator.getAction() == SEND_MESSAGE_ACTION_SENT) {
            if (VDBG) {
                logd("performGeoFencing:" + getMessageString(message));
            }
            return;
        }

        if (uri != null) {
            ContentValues cv = new ContentValues();
            cv.put(CellBroadcasts.LOCATION_CHECK_TIME, System.currentTimeMillis());
            mContext.getContentResolver().update(CellBroadcasts.CONTENT_URI, cv,
                    CellBroadcasts._ID + "=?", new String[] {uri.getLastPathSegment()});
        }


        calculator.addCoordinate(location, accuracy);

        if (VDBG) {
            logd("Device location new action = "
                    + CbSendMessageCalculator.getActionString(calculator.getAction())
                    + ", threshold = " + calculator.getThreshold()
                    + ", geos=" + CbGeoUtils.encodeGeometriesToString(calculator.getFences())
                    + ". " + getMessageString(message));
        }

        if (calculator.getAction() == SEND_MESSAGE_ACTION_SEND) {
            broadcastGeofenceMessage(message, uri, slotIndex, calculator);
            return;
        }
    }

    protected void geofenceMessageNotRequired(SmsCbMessage msg) {
        if (msg.getMessageFormat() == SmsCbMessage.MESSAGE_FORMAT_3GPP) {
            CellBroadcastServiceMetrics.getInstance().logMessageFiltered(FILTER_GEOFENCED, msg);
        } else if (msg.getMessageFormat() == SmsCbMessage.MESSAGE_FORMAT_3GPP2) {
            CellBroadcastServiceMetrics.getInstance().logMessageFiltered(FILTER_GEOFENCED, msg);
        }
        sendMessage(EVENT_BROADCAST_NOT_REQUIRED);
    }

    /**
     * send message that broadcast is not required due to geo-fencing check
     */
    @VisibleForTesting
    public void sendMessageBroadcastNotRequired() {
        sendMessage(EVENT_BROADCAST_NOT_REQUIRED);
    }

    /**
     * Requests a stream of updates for {@code maximumWaitTimeSec} seconds.
     * @param callback the callback used to communicate back to the caller
     * @param maximumWaitTimeSec the maximum wait time of this request. If location is not updated
     * within the maximum wait time, {@code callback#onLocationUnavailable()} will be called.
     */
    protected void requestLocationUpdate(LocationUpdateCallback callback, int maximumWaitTimeSec) {
        mLocationRequester.requestLocationUpdate(callback, maximumWaitTimeSec);
    }

    /**
     * Get the subscription id from the phone id.
     *
     * @param phoneId the phone id (i.e. logical SIM slot index)
     *
     * @return The associated subscription id. {@link SubscriptionManager#INVALID_SUBSCRIPTION_ID}
     * if the phone does not have an active sub or when {@code phoneId} is not valid.
     */
    public static int getSubIdForPhone(Context context, int phoneId) {
        if (SdkLevel.isAtLeastU()) {
            return SubscriptionManager.getSubscriptionId(phoneId);
        } else {
            SubscriptionManager subMan = context.getSystemService(SubscriptionManager.class);
            int[] subIds = subMan.getSubscriptionIds(phoneId);
            if (subIds != null) {
                return subIds[0];
            } else {
                return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
            }
        }
    }

    /**
     * Put the phone ID and sub ID into an intent as extras.
     */
    public static void putPhoneIdAndSubIdExtra(Context context, Intent intent, int phoneId) {
        int subId = getSubIdForPhone(context, phoneId);
        if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            intent.putExtra("subscription", subId);
            intent.putExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, subId);
        }
        intent.putExtra("phone", phoneId);
        intent.putExtra(SubscriptionManager.EXTRA_SLOT_INDEX, phoneId);
    }

    /**
     * Call when dealing with messages that are checked against a geofence.
     *
     * @param message the message being broadcast
     * @param messageUri the message uri
     * @param slotIndex the slot index
     * @param calculator the messages send message calculator
     */
    protected void broadcastGeofenceMessage(@NonNull SmsCbMessage message, @Nullable Uri messageUri,
            int slotIndex, CbSendMessageCalculator calculator) {
        // Check that the message wasn't already SENT
        if (calculator.getAction() == CbSendMessageCalculator.SEND_MESSAGE_ACTION_SENT) {
            return;
        }

        if (VDBG) {
            logd("broadcastGeofenceMessage: mark as sent. " + getMessageString(message));
        }
        // Mark the message as SENT
        calculator.markAsSent();

        // Broadcast the message
        broadcastMessage(message, messageUri, slotIndex);
    }

    /**
     * Broadcast the {@code message} to the applications.
     * @param message a message need to broadcast
     * @param messageUri message's uri
     */
    // TODO(b/193460475): Remove when tooling supports SystemApi to public API.
    @SuppressLint("NewApi")
    protected void broadcastMessage(@NonNull SmsCbMessage message, @Nullable Uri messageUri,
            int slotIndex) {
        String msg;
        Intent intent;
        if (VDBG) {
            logd("broadcastMessage: " + getMessageString(message));
        }

        if (message.isEmergencyMessage()) {
            msg = "Dispatching emergency SMS CB, SmsCbMessage is: " + message;
            log(msg);
            mLocalLog.log(msg);
            intent = new Intent(Telephony.Sms.Intents.ACTION_SMS_EMERGENCY_CB_RECEIVED);
            //Emergency alerts need to be delivered with high priority
            intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);

            intent.putExtra(EXTRA_MESSAGE, message);
            putPhoneIdAndSubIdExtra(mContext, intent, slotIndex);

            if (IS_DEBUGGABLE) {
                // Send additional broadcast intent to the specified package. This is only for sl4a
                // automation tests.
                String[] testPkgs = mContext.getResources().getStringArray(
                        R.array.test_cell_broadcast_receiver_packages);
                if (testPkgs != null) {
                    Intent additionalIntent = new Intent(intent);
                    for (String pkg : testPkgs) {
                        additionalIntent.setPackage(pkg);
                        mLocalLog.log("intent=" + intent + " package=" + pkg);
                        mContext.createContextAsUser(UserHandle.ALL, 0).sendOrderedBroadcast(
                                intent, null, (Bundle) null, null, getHandler(),
                                Activity.RESULT_OK, null, null);

                    }
                }
            }

            List<String> pkgs = new ArrayList<>();
            pkgs.add(getDefaultCBRPackageName(mContext, intent));
            pkgs.addAll(Arrays.asList(mContext.getResources().getStringArray(
                    R.array.additional_cell_broadcast_receiver_packages)));
            if (pkgs != null) {
                mReceiverCount.addAndGet(pkgs.size());
                CellBroadcastServiceMetrics.getInstance().getFeatureMetrics(mContext)
                        .onChangedAdditionalCbrPackage(pkgs.size() > 1 ? true : false);
                for (String pkg : pkgs) {
                    // Explicitly send the intent to all the configured cell broadcast receivers.
                    intent.setPackage(pkg);
                    mLocalLog.log("intent=" + intent + " package=" + pkg);
                    mContext.createContextAsUser(UserHandle.ALL, 0).sendOrderedBroadcast(
                            intent, null, (Bundle) null, mOrderedBroadcastReceiver, getHandler(),
                            Activity.RESULT_OK, null, null);
                }
            }
        } else {
            msg = "Dispatching SMS CB, SmsCbMessage is: " + message;
            log(msg);
            mLocalLog.log(msg);
            // Send implicit intent since there are various 3rd party carrier apps listen to
            // this intent.

            mReceiverCount.incrementAndGet();
            CellBroadcastIntents.sendSmsCbReceivedBroadcast(
                    mContext, UserHandle.ALL, message, mOrderedBroadcastReceiver, getHandler(),
                    Activity.RESULT_OK, slotIndex);
        }

        if (messageUri != null) {
            ContentValues cv = new ContentValues();
            cv.put(CellBroadcasts.MESSAGE_BROADCASTED, 1);
            mContext.getContentResolver().update(CellBroadcasts.CONTENT_URI, cv,
                    CellBroadcasts._ID + "=?", new String[] {messageUri.getLastPathSegment()});
        }

        CellBroadcastServiceMetrics.getInstance().logFeatureChangedAsNeeded(mContext);
    }

    /**
     * Checks if the app's path starts with CB_APEX_PATH
     */
    private static boolean isAppInCBApexOrAlternativeApp(ApplicationInfo appInfo) {
        return appInfo.sourceDir.startsWith(CB_APEX_PATH) ||
               appInfo.sourceDir.contains(CB_APP_PLATFORM_NAME);
    }

    /**
     * Find the name of the default CBR package. The criteria is that it belongs to CB apex and
     * handles the given intent.
     */
    static String getDefaultCBRPackageName(Context context, Intent intent) {
        PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> cbrPackages = packageManager.queryBroadcastReceivers(intent, 0);

        // remove apps that don't live in the CellBroadcast apex
        cbrPackages.removeIf(info ->
                !isAppInCBApexOrAlternativeApp(info.activityInfo.applicationInfo));

        if (cbrPackages.isEmpty()) {
            CellBroadcastServiceMetrics.getInstance().logModuleError(
                    ERRSRC_CBS, ERRTYPE_NOTFOUND_DEFAULTCBRPKGS);
            Log.e(TAG, "getCBRPackageNames: no package found");
            return null;
        }

        if (cbrPackages.size() > 1) {
            // multiple apps found, log an error but continue
            CellBroadcastServiceMetrics.getInstance().logModuleError(
                    ERRSRC_CBS, ERRTYPE_FOUND_MULTIPLECBRPKGS);
            Log.e(TAG, "Found > 1 APK in CB apex that can resolve " + intent.getAction() + ": "
                    + cbrPackages.stream()
                    .map(info -> info.activityInfo.applicationInfo.packageName)
                    .collect(Collectors.joining(", ")));
        }

        // Assume the first ResolveInfo is the one we're looking for
        ResolveInfo info = cbrPackages.get(0);
        return info.activityInfo.applicationInfo.packageName;
    }

    /**
     * Get the device resource based on SIM
     *
     * @param subId Subscription index
     *
     * @return The resource
     */
    public @NonNull Resources getResources(int subId) {
        if (subId == SubscriptionManager.DEFAULT_SUBSCRIPTION_ID
                || !SubscriptionManager.isValidSubscriptionId(subId)) {
            return mContext.getResources();
        }

        if (mResourcesCache.containsKey(subId)) {
            return mResourcesCache.get(subId);
        }

        return SubscriptionManager.getResourcesForSubId(mContext, subId);
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("CellBroadcastHandler:");
        mLocalLog.dump(fd, pw, args);
        pw.flush();
        try {
            super.dump(fd, pw, args);
        } catch (NullPointerException e) {
            // StateMachine.dump() throws a NPE if there is no current state in the stack. Since
            // StateMachine is defined in the framework and CBS is updated through mailine, we
            // catch the NPE here as well as fixing the exception in the framework.
            pw.println("StateMachine: no state info");
        }
    }

    /** The callback interface of a location request. */
    public interface LocationUpdateCallback {
        /**
         * Called when the location update is available.
         * @param location a location in (latitude, longitude) format.
         * @param accuracy the accuracy of the location given from location manager.  Given in
         *                 meters.
         */
        void onLocationUpdate(@NonNull LatLng location, float accuracy);

        /**
         * This is called in the following scenarios:
         *   1. The max time limit of the LocationRequester was reached, and consequently,
         *      no more location updates will be sent.
         *   2. The service does not have permission to request a location update.
         *   3. The LocationRequester was explicitly cancelled.
         */
        void onLocationUnavailable();

        /**
         * Returns true if all messages are handled.
         */
        boolean areAllMessagesHandled();
    }

    private static final class LocationRequester {
        /**
         * Fused location provider, which means GPS plus network based providers (cell, wifi, etc..)
         */
        //TODO: Should make LocationManager.FUSED_PROVIDER system API in S.
        private static final String FUSED_PROVIDER = "fused";

        /**
         * The interval in which location requests will be sent.
         * see more: <code>LocationRequest#setInterval</code>
         */
        private static final long LOCATION_REQUEST_INTERVAL_MILLIS = 1000;

        private final LocationManager mLocationManager;
        private final List<LocationUpdateCallback> mCallbacks;
        private final HandlerHelper mHandlerHelper;
        private final Context mContext;
        private final LocationListener mLocationListener;

        private boolean mLocationUpdateInProgress;
        private final Runnable mLocationUnavailable;
        private final String mDebugTag;

        LocationRequester(Context context, LocationManager locationManager,
                HandlerHelper handlerHelper, String debugTag) {
            mLocationManager = locationManager;
            mCallbacks = new ArrayList<>();
            mContext = context;
            mHandlerHelper = handlerHelper;
            mLocationUpdateInProgress = false;
            mLocationUnavailable = this::onLocationUnavailable;

            // Location request did not cancel itself when using this::onLocationListener
            mLocationListener = this::onLocationUpdate;
            mDebugTag = debugTag;
        }

        /**
         * Requests a stream of updates for {@code maximumWaitTimeSec} seconds.
         * @param callback the callback used to communicate back to the caller
         * @param maximumWaitTimeSec the maximum wait time of this request. If location is not
         *                           updated within the maximum wait time,
         *                           {@code callback#onLocationUnavailable()} will be called.
         */
        void requestLocationUpdate(@NonNull LocationUpdateCallback callback,
                int maximumWaitTimeSec) {
            mHandlerHelper.post(() -> requestLocationUpdateInternal(callback, maximumWaitTimeSec));
        }

        private void onLocationUpdate(@NonNull Location location) {
            if (location == null) {
                /* onLocationUpdate should neverreceive a null location, but, covering all of our
                   bases here. */
                Log.wtf(mDebugTag, "Location is never supposed to be null");
                return;
            }

            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
            float accuracy = location.getAccuracy();
            if (DBG) {
                Log.d(mDebugTag, "onLocationUpdate: received location update");
            }

            boolean canCancel = true;
            for (LocationUpdateCallback callback : mCallbacks) {
                callback.onLocationUpdate(latLng, accuracy);
                canCancel = canCancel && callback.areAllMessagesHandled();
            }
            if (canCancel) {
                Log.d(mDebugTag, "call cancel because all messages are handled.");
                cancel();
            }
        }

        private void onLocationUnavailable() {
            Log.d(mDebugTag, "onLocationUnavailable: called");
            locationRequesterCycleComplete();
        }

        /* This should only be called if all of the messages are handled. */
        public void cancel() {
            if (mLocationUpdateInProgress) {
                Log.d(mDebugTag, "cancel: location update in progress");
                mHandlerHelper.removeCallbacks(mLocationUnavailable);
                locationRequesterCycleComplete();
            } else {
                Log.d(mDebugTag, "cancel: location update NOT in progress");
            }
        }

        private void locationRequesterCycleComplete() {
            try {
                for (LocationUpdateCallback callback : mCallbacks) {
                    callback.onLocationUnavailable();
                }
            } finally {
                mLocationManager.removeUpdates(mLocationListener);
                // Reset the state of location requester for the next request
                mCallbacks.clear();
                mLocationUpdateInProgress = false;
            }
        }

        private void requestLocationUpdateInternal(@NonNull LocationUpdateCallback callback,
                int maximumWaitTimeS) {
            if (DBG) Log.d(mDebugTag, "requestLocationUpdate");
            if (!hasPermission(ACCESS_FINE_LOCATION) && !hasPermission(ACCESS_COARSE_LOCATION)) {
                if (DBG) {
                    Log.e(mDebugTag,
                            "Can't request location update because of no location permission");
                }
                callback.onLocationUnavailable();
                return;
            }

            if (!mLocationUpdateInProgress) {
                try {
                    // If the user does not turn on location, immediately report location
                    // unavailable.
                    if (!mLocationManager.isLocationEnabled()) {
                        Log.d(mDebugTag, "Location is turned off.");
                        callback.onLocationUnavailable();
                        return;
                    }

                    /* We will continue to send updates until the location timeout is reached. The
                    location timeout case is handled through onLocationUnavailable. */
                    LocationRequest request = LocationRequest.create()
                            .setProvider(FUSED_PROVIDER)
                            .setQuality(LocationRequest.ACCURACY_FINE)
                            .setInterval(LOCATION_REQUEST_INTERVAL_MILLIS);
                    if (DBG) {
                        Log.d(mDebugTag, "Location request=" + request);
                    }
                    mLocationManager.requestLocationUpdates(request,
                            new HandlerExecutor(mHandlerHelper.getHandler()),
                            mLocationListener);

                    // TODO: Remove the following workaround in S. We need to enforce the timeout
                    // before location manager adds the support for timeout value which is less
                    // than 30 seconds. After that we can rely on location manager's timeout
                    // mechanism.
                    mHandlerHelper.postDelayed(mLocationUnavailable,
                            TimeUnit.SECONDS.toMillis(maximumWaitTimeS));
                } catch (IllegalArgumentException e) {
                    Log.e(mDebugTag, "Cannot get current location. e=" + e);
                    callback.onLocationUnavailable();
                    return;
                }
                mLocationUpdateInProgress = true;
            }
            mCallbacks.add(callback);
        }

        private boolean hasPermission(String permission) {
            // TODO: remove the check. This will always return true because cell broadcast service
            // is running under the UID Process.NETWORK_STACK_UID, which is below 10000. It will be
            // automatically granted with all runtime permissions.
            return mContext.checkPermission(permission, Process.myPid(), Process.myUid())
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    /**
     * Provides message identifiers that are helpful when logging messages.
     *
     * @param message the message to log
     * @return a helpful message
     */
    protected static String getMessageString(SmsCbMessage message) {
        return "msg=("
                + message.getServiceCategory() + ","
                + message.getSerialNumber() + ")";
    }


    /**
     * Wraps the {@code Handler} in order to mock the methods.
     */
    @VisibleForTesting
    public static class HandlerHelper {

        private final Handler mHandler;

        public HandlerHelper(@NonNull final Handler handler) {
            mHandler = handler;
        }

        /**
         * Posts {@code r} to {@code handler} with a delay of {@code delayMillis}
         *
         * @param r the runnable callback
         * @param delayMillis the number of milliseconds to delay
         */
        public void postDelayed(Runnable r, long delayMillis) {
            mHandler.postDelayed(r, delayMillis);
        }

        /**
         * Posts {@code r} to the underlying handler
         *
         * @param r the runnable callback
         */
        public void post(Runnable r) {
            mHandler.post(r);
        }

        /**
         * Gets the underlying handler
         * @return the handler
         */
        public Handler getHandler() {
            return mHandler;
        }

        /**
         * Remove any pending posts of Runnable r that are in the message queue.
         */
        public void removeCallbacks(Runnable r) {
            mHandler.removeCallbacks(r);
        }
    }
}

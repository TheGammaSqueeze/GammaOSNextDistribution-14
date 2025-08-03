/**
 * Copyright (c) 2015-2017, 2020-2021 The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * Changes from Qualcomm Innovation Center are provided under the following license:
 *
 * Copyright (c) 2022-2023 Qualcomm Innovation Center, Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted (subject to the limitations in the
 * disclaimer below) provided that the following conditions are met:
 *
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   * Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *
 *   * Neither the name of Qualcomm Innovation Center, Inc. nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * NO EXPRESS OR IMPLIED LICENSES TO ANY PARTY'S PATENT RIGHTS ARE
 * GRANTED BY THIS LICENSE. THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT
 * HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.codeaurora.ims.utils;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.IllegalArgumentException;
import java.lang.SecurityException;

import org.codeaurora.ims.QtiCallConstants;
import org.codeaurora.ims.QtiCarrierConfigs;
import org.codeaurora.ims.QtiImsException;
import org.codeaurora.ims.QtiImsExtManager;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * This class contains QtiImsExt specific utiltity functions.
 */
public class QtiImsExtUtils {

    private static final String LOG_TAG = "QtiImsExtUtils";

    public static final String QTI_IMS_CALL_DEFLECT_NUMBER =
            "ims_call_deflect_number";

    /* Default success value */
    public static final int QTI_IMS_REQUEST_SUCCESS = 0;

    /* Default error value */
    public static final int QTI_IMS_REQUEST_ERROR = 1;

    /* name for carrier property */
    public static final String PROPERTY_RADIO_ATEL_CARRIER = "persist.vendor.radio.atel.carrier";

    /* Carrier one default mcc mnc */
    public static final String CARRIER_ONE_DEFAULT_MCC_MNC = "405854";

    public static final String QTI_IMS_STATIC_IMAGE_SETTING =
            "ims_vt_call_static_image";

    /* name for call transfer setting */
    private static final String IMS_CALL_TRANSFER_SETTING = "ims_call_transfer";

    /**
     * Definitions for the call transfer type. For easier implementation,
     * the transfer type is defined as a bit mask value.
     */
    //Value representing blind call transfer type
    public static final int QTI_IMS_BLIND_TRANSFER = 0x01;
    //Value representing assured call transfer type
    public static final int QTI_IMS_ASSURED_TRANSFER = 0x02;
    //Value representing consultative call transfer type
    public static final int QTI_IMS_CONSULTATIVE_TRANSFER = 0x04;

    /* Call transfer extra key */
    public static final String QTI_IMS_TRANSFER_EXTRA_KEY = "transferType";

    /* Ims phoneId extra key */
    public static final String QTI_IMS_PHONE_ID_EXTRA_KEY = "phoneId";

    /* Constants used for VOPS and SSAC feature */
    // Intent action
    public static final String ACTION_VOPS_SSAC_STATUS =
            "org.codeaurora.VOIP_VOPS_SSAC_STATUS";
    /* Intent extra
     * if true  : Voice is supported on LTE
     * if false : Voice is not supported on LTE
     */
    public static final String EXTRA_VOPS = "Vops";
    /* Intent extra
     * if true  : Access barring factor for voice calls is 0
     * if false : Access barring factor for voice calls is non-zero
     *            Range: 0 to 100. Value 100 is used when
     *            the UE goes to the LTE Connected state
     */
    public static final String EXTRA_SSAC = "Ssac";

    /* @Deprecated
     * Use SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX
     * as key to carry sub id value instead
     */
    public static final String SUBSCRIPTION_ID = "subId";

    /**
     * Definitions for the volte preference values.
     */
    //Value representing volte preference is OFF
    public static final int QTI_IMS_VOLTE_PREF_OFF = 0;
    //Value representing volte preference is ON
    public static final int QTI_IMS_VOLTE_PREF_ON = 1;
    //Value representing volte preference is NOT known
    public static final int QTI_IMS_VOLTE_PREF_UNKNOWN = 2;

    /* Incoming conference call extra key */
    public static final String QTI_IMS_INCOMING_CONF_EXTRA_KEY = "incomingConference";

    /* Handover config params */
    public static final int QTI_IMS_HO_INVALID = 0x00;
    public static final int QTI_IMS_HO_ENABLE_ALL = 0x01;
    public static final int QTI_IMS_HO_DISABLE_ALL = 0x02;
    public static final int QTI_IMS_HO_ENABLED_WLAN_TO_WWAN_ONLY = 0x03;
    public static final int QTI_IMS_HO_ENABLED_WWAN_TO_WLAN_ONLY = 0x04;

    /*Rcs app config params */
    public static final int QTI_IMS_SMS_APP_INVALID = -1;
    public static final int QTI_IMS_SMS_APP_SELECTION_NOT_ALLOWED = 0;
    public static final int QTI_IMS_SMS_APP_RCS = 1;
    public static final int QTI_IMS_SMS_APP_NOT_RCS = 2;

    /*Vvm app config params */
    public static final int QTI_IMS_VVM_APP_INVALID = -1;
    public static final int QTI_IMS_VVM_APP_NOT_RCS = 0;
    public static final int QTI_IMS_VVM_APP_RCS = 1;

    /*TIR mode extra key */
    public static final String EXTRA_TIR_OVERWRITE_ALLOWED = "incomingTir";
    /*TIR presentation params */
    /*TIR presentation extra key */
    public static final String EXTRA_ANSWER_OPTION_TIR_CONFIG = "tirConfig";
    public static final int QTI_IMS_TIR_PRESENTATION_UNRESTRICTED = 0;
    public static final int QTI_IMS_TIR_PRESENTATION_RESTRICTED = 1;
    public static final int QTI_IMS_TIR_PRESENTATION_DEFAULT = 2;

    /*RTT downgrade not supported */
    public static final int QTI_IMS_RTT_DOWNGRADE_NOT_SUPPORTED = 0;
    /*RTT upgrade not supported */
    public static final int QTI_IMS_RTT_NOT_SUPPORTED = 0;

    // Phone permissions
    public static final String READ_PHONE_STATE = Manifest.permission.READ_PHONE_STATE;
    public static final String MODIFY_PHONE_STATE = Manifest.permission.MODIFY_PHONE_STATE;

    /**
     * Private constructor for QtiImsExtUtils as we don't want to instantiate this class
     */
    private QtiImsExtUtils() {
    }

    /** Define Video quality constants as per modem interface
     *  Existing values are referred from
     *      {@link ImsConfig.VideoQualityFeatureValuesConstants}
     */
    public static class VideoQualityFeatureValuesConstants {
        public static final int LOW = 0;
        public static final int MEDIUM = 1;
        public static final int HIGH = 2;
    }

    /**
     * Retrieves the call deflection stored by the user
     * Returns stored number, or null otherwise.
     */
    public static String getCallDeflectNumber(ContentResolver contentResolver) {
        String deflectcall = android.provider.Settings.Global.getString(contentResolver,
                                     QTI_IMS_CALL_DEFLECT_NUMBER);

        /* Consider being null or empty as "Not Set" */
        if ((deflectcall != null) && (deflectcall.isEmpty())) {
            deflectcall = null;
        }

        return deflectcall;
    }

    /* Stores the call deflection provided by the user */
    public static void setCallDeflectNumber(ContentResolver contentResolver, String value) {
        String deflectNum = value;

        if (value == null || value.isEmpty()) {
            deflectNum = "";
        }

        android.provider.Settings.Global.putString(contentResolver,
                QTI_IMS_CALL_DEFLECT_NUMBER, deflectNum);
    }

   /**
     * Retrieves the static image stored by the user
     * Returns stored static image file path, or null otherwise.
     */
    public static String getStaticImageUriStr(ContentResolver contentResolver) {
        return android.provider.Settings.Global.getString(contentResolver,
                                     QTI_IMS_STATIC_IMAGE_SETTING);
    }

    private static boolean isValidUriStr(String uriStr) {
        /* uri is not valid if
         * 1. uriStr is null
         * 2. uriStr is empty
         */
        return uriStr != null && !uriStr.isEmpty();
    }

    /**
     * Calculate an inSampleSize for use in a {@link android.graphics.BitmapFactory.Options} object
     * when decoding bitmaps using the decode* methods from {@link android.graphics.BitmapFactory}.
     * This implementation calculates the closest inSampleSize that is a power of 2 and will result
     * in the final decoded bitmap having a width and height equal to or larger than the requested
     * width and height.
     *
     * @param options An options object with out* params already populated (run through a decode*
     *            method with inJustDecodeBounds==true
     * @param reqWidth The requested width of the resulting bitmap
     * @param reqHeight The requested height of the resulting bitmap
     * @return The value to be used for inSampleSize
     */
    private static int calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        Log.d(LOG_TAG, "calculateInSampleSize: reqWidth = " + reqWidth + " reqHeight = " + reqHeight
                + " raw width = " + width + " raw height = " + height);

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) > reqHeight
                    && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }
        Log.d(LOG_TAG, "calculateInSampleSize: inSampleSize = " + inSampleSize);
        return inSampleSize;
    }

    /**
     * Decodes an image pointed to by uri as per requested Width and requested Height
     * and returns a bitmap
     */
    public static Bitmap decodeImage(String uriStr, Context context, int reqWidth, int reqHeight) {
        if (uriStr == null) {
            return null;
        }
        ParcelFileDescriptor parcelFileDescriptor = null;

        Uri uri = Uri.parse(uriStr);

        try {
            parcelFileDescriptor = context.getContentResolver().openFileDescriptor(uri, "r");
            FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();

            BitmapFactory.Options options = new BitmapFactory.Options();
            // Each pixel is stored on 4 bytes
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            /* If set to true, the decoder will return null (no bitmap),
               but the out... fields (i.e. outWidth, outHeight and outMimeType)
               will still be set, allowing the caller to query the bitmap
               without having to allocate the memory for its pixels */
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFileDescriptor(fileDescriptor, null, options);

            // Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

            // Decode bitmap with inSampleSize set
            options.inJustDecodeBounds = false;
            Bitmap image = BitmapFactory.decodeFileDescriptor(fileDescriptor, null, options);

            return scaleImage(image, reqWidth, reqHeight);
        } catch (FileNotFoundException e) {
            Log.e(LOG_TAG, "File not found for uri: " + uri + " exception : " + e);
        } catch (IllegalArgumentException e) {
            Log.e(LOG_TAG, "Check arguments passed to decodeFileDescriptor, exception : " + e);
        } catch (SecurityException e) {
            //If the selected static image file located under file path "/sdcard/" is deleted,
            //SecurityException is thrown by ContentResolver#openFileDescriptor.
            Log.e(LOG_TAG, "SecurityException, exception : " + e);
        } finally {
            try {
                if (parcelFileDescriptor != null) {
                    parcelFileDescriptor.close();
                }
            } catch (IOException e) {
                Log.e(LOG_TAG, "Closing parcelFileDescriptor " + " exception : " + e);
            }
        }
        return null;
    }

    // scales the image using reqWidth/reqHeight and returns a scaled bitmap
    private static Bitmap scaleImage(Bitmap bitmap, int reqWidth, int reqHeight) {
        if (bitmap == null) {
            return null;
        }

        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        float scaleWidth = ((float) reqWidth) / w;
        float scaleHeight = ((float) reqHeight) / h;
        Log.d(LOG_TAG, "scaleImage bitmap w = " + w + " bitmap h = " + h);

        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);
        Bitmap resizedBitmap = Bitmap.createBitmap(
               bitmap, 0, 0, w, h, matrix, false);
        return resizedBitmap;
    }

    /**
     * Decode and sample down a bitmap from a resource to the requested width and height.
     *
     * @param res The resources containing the resId
     * @param resId The resource id
     * @param reqWidth The requested width of the resulting bitmap
     * @param reqHeight The requested height of the resulting bitmap
     * @return A down sampled bitmap
     */
    public static Bitmap decodeImage(Resources res, int resId,
            int reqWidth, int reqHeight) {

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        // Each pixel is stored on 4 bytes
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        /* If set to true, the decoder will return null (no bitmap),
           but the out... fields (i.e. outWidth, outHeight and outMimeType)
           will still be set, allowing the caller to query the bitmap
           without having to allocate the memory for its pixels */
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(res, resId, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeResource(res, resId, options);
        return scaleImage(bitmap, reqWidth, reqHeight);
    }

    /*
     * This API:
     * 1. Checks to see if user has preconfigured any image
     * 2. Throws an INVALID_FILE_PATH exception if image file path is invalid
     * 3. Throws an IMAGE_DECODING_FAILURE exception if image decoding failed
     * 4. Returns a downsampled bitmap in all other cases.
     */
    public static Bitmap getStaticImage(Context context, int reqWidth, int reqHeight)
            throws QtiImsException {
        // Query database for user selected image
        String uriStr = getStaticImageUriStr(context.getContentResolver());
        Log.d(LOG_TAG, "getStaticImage: uriStr = " + uriStr + " reqWidth = " + reqWidth +
                " reqHeight = " + reqHeight);

        if (!isValidUriStr(uriStr)) {
            throw new QtiImsException("invalid file path");
        }

        Bitmap imageBitmap = decodeImage(uriStr, context, reqWidth, reqHeight);
        if (imageBitmap == null) {
            throw new QtiImsException("image decoding error");
        }
        return imageBitmap;
    }

    /***
     * Checks if the IMS Video call hide me feature is enabled or not.
     * Returns true if enabled, or false otherwise.
     */
    public static boolean shallTransmitStaticImage(int phoneId, Context context) {
        return isCarrierConfigEnabled(phoneId, context, QtiCarrierConfigs.TRANSMIT_STATIC_IMAGE);
    }

    /***
     * Checks whether static image related UI elements are to be shown or not.
     * Returns true if the UI elements are to be shown, or false otherwise.
     */
    public static boolean shallShowStaticImageUi(int phoneId, Context context) {
        return isCarrierConfigEnabled(phoneId, context, QtiCarrierConfigs.SHOW_STATIC_IMAGE_UI);
    }

    /***
     * Checks whether video quality realted UI elements are to be shown or not.
     * Returns true if the UI elements are to be shown, or false otherwise.
     */
    public static boolean shallShowVideoQuality(int phoneId, Context context) {
        return isCarrierConfigEnabled(phoneId, context, QtiCarrierConfigs.SHOW_VIDEO_QUALITY_UI);
    }

    /***
     * Checks if the IMS call transfer property is enabled or not.
     * Returns true if enabled, or false otherwise.
     */
    public static boolean isCallTransferEnabled(Context context) {
        return Settings.Global.getInt(context.getContentResolver(), IMS_CALL_TRANSFER_SETTING, 0)
                == 1;
    }

   /**
     * This API checks to see whether we are going to use ui extension for video call or not.
     * @param context context for getting video call ui ext configuration value
     * Returns true if enabled, or false otherwise.
     */
    public static boolean useExt(int phoneId, Context context) {
        return isCarrierConfigEnabled(phoneId, context, QtiCarrierConfigs.USE_VIDEO_UI_EXTENSIONS);
    }

   /**
     * This API checks to see whether custom video ui is enabled or not.
     * @param context context for getting custom video ui configuration value
     * Returns true if enabled, or false otherwise.
     */
    public static boolean useCustomVideoUi(int phoneId, Context context) {
        return isCarrierConfigEnabled(phoneId, context, QtiCarrierConfigs.USE_CUSTOM_VIDEO_UI);
    }

    /**
     * This API checks to see whether IMS to CS retry is enabled or not.
     * @param context context for getting the CS retry configuration value
     * Returns true if enabled, or false otherwise.
     */
    public static boolean isCsRetryConfigEnabled(int phoneId, Context context) {
        return isCarrierConfigEnabled(phoneId, context, QtiCarrierConfigs.CONFIG_CS_RETRY);
    }

    /**
     * This API checks to see whether VoWiFi call quality feature is enabled or not.
     * @param phoneId phone Id to read the configuration for specific subscription.
     * @param context context for getting the VoWiFi call quality config value.
     * Returns true if enabled, or false otherwise.
     */
    public static boolean isVoWiFiCallQualityEnabled(int phoneId, Context context) {
        return isCarrierConfigEnabled(phoneId, context, QtiCarrierConfigs.VOWIFI_CALL_QUALITY);
    }

    /**
     * Check is carrier one supported or not
     */
    public static boolean isCarrierOneSupported() {
        return CARRIER_ONE_DEFAULT_MCC_MNC.equals(SystemProperties.get(
                PROPERTY_RADIO_ATEL_CARRIER));
    }

    /**
     * Returns true if config flag is enabled.
     */
    public static boolean isCarrierConfigEnabled(int phoneId, Context context,
            String carrierConfig) {
        return QtiCarrierConfigHelper.getInstance().getBoolean(context, phoneId,
                carrierConfig);
    }

    public static boolean allowVideoCallsInLowBattery(int phoneId, Context context) {
        return isCarrierConfigEnabled(phoneId, context,
                QtiCarrierConfigs.ALLOW_VIDEO_CALL_IN_LOW_BATTERY);
    }

    public static boolean shallHidePreviewInVtConference(int phoneId, Context context) {
        return isCarrierConfigEnabled(phoneId, context,
                QtiCarrierConfigs.HIDE_PREVIEW_IN_VT_CONFERENCE);
    }

    public static boolean canHoldVideoCall(int phoneId, Context context) {
        return isCarrierConfigEnabled(phoneId, context,
                CarrierConfigManager.KEY_ALLOW_HOLD_VIDEO_CALL_BOOL);
    }

    //TODO not removing this deprecated API to avoid compilation errors.
    public static boolean shallRemoveModifyCallCapability(Context context) {
        return shallRemoveModifyCallCapability(QtiCallConstants.INVALID_PHONE_ID, context);
    }

    public static boolean shallRemoveModifyCallCapability(int phoneId, Context context) {
        return isCarrierConfigEnabled(phoneId, context,
                QtiCarrierConfigs.REMOVE_MODIFY_CALL_CAPABILITY);
    }

    /**
     * Returns subscription id for given phone id.
     */
    public static int getSubscriptionIdFromPhoneId(Context context, int phoneId) {
        SubscriptionManager subscriptionManager = SubscriptionManager.from(context);
        if (subscriptionManager == null) {
            return subscriptionManager.INVALID_SUBSCRIPTION_ID;
        }

        SubscriptionInfo subInfo = subscriptionManager.
                getActiveSubscriptionInfoForSimSlotIndex(phoneId);
        if (subInfo == null) {
            return subscriptionManager.INVALID_SUBSCRIPTION_ID;
        }
        return subInfo.getSubscriptionId();
    }

    // Returns true if global setting has stored value as true
    // if phone id is not provided, uses default value of 0
    public static boolean isRttOn(Context context) {
        return isRttOn(context, QtiCallConstants.RTT_DEFAULT_PHONE_ID);
    }

    // Returns true if global setting has stored value as true
    public static boolean isRttOn(Context context, int phoneId) {
        return getRttMode(context, phoneId) !=  QtiCallConstants.RTT_MODE_DISABLED;
    }

    // Returns true if global setting has stored value as true
    // if phone id is not provided, uses default value of 0
    public static boolean isRttVisibilityOn(Context context) {
        return isRttVisibilityOn(context, QtiCallConstants.RTT_DEFAULT_PHONE_ID);
    }

    // Returns true if global setting has stored value as true
    public static boolean isRttVisibilityOn(Context context, int phoneId) {
        return (getRttVisibility(context, phoneId) !=  QtiCallConstants.RTT_VISIBILITY_DISABLED);
    }

    // To maintain backwards compatibility
    // if phone id is equal to 0 return an empty string
    private static String convertRttPhoneId(int phoneId) {
        return (phoneId != QtiCallConstants.RTT_DEFAULT_PHONE_ID) ? Integer.toString(phoneId) : "";
    }

    // Returns value of RTT mode
    public static int getRttMode(Context context) {
        return getRttMode(context, QtiCallConstants.RTT_DEFAULT_PHONE_ID);
    }

    public static int getRttMode(Context context, int phoneId) {
        return android.provider.Settings.Secure.getInt(context.getContentResolver(),
            Settings.Secure.RTT_CALLING_MODE + convertRttPhoneId(phoneId), 0);
    }

    // Sets RTT mode to global settings
    public static void setRttMode(boolean value, Context context) {
        setRttMode(value, context, QtiCallConstants.RTT_DEFAULT_PHONE_ID);
    }

    public static void setRttMode(boolean value, Context context, int phoneId) {
        android.provider.Settings.Secure.putInt(context.getContentResolver(),
                    Settings.Secure.RTT_CALLING_MODE + convertRttPhoneId(phoneId), value ? 1 : 0);
    }

    // Sets global settings with call type preference
    public static void setCanStartRttCall(boolean value, Context context, int phoneId) {
        android.provider.Settings.Global.putInt(context.getContentResolver(),
                    QtiCallConstants.QTI_IMS_CAN_START_RTT_CALL + convertRttPhoneId(phoneId),
                    value ? QtiCallConstants.RTT_CALL_TYPE_VOICE
                          : QtiCallConstants.RTT_CALL_TYPE_RTT);
    }

    // Returns true if can start RTT call
    public static boolean canStartRttCall(Context context, int phoneId) {
        if (!shallShowRttVisibilitySetting(phoneId, context)) {
            return true;
        }
        return android.provider.Settings.Global.getInt(context.getContentResolver(),
                QtiCallConstants.QTI_IMS_CAN_START_RTT_CALL + convertRttPhoneId(phoneId),
                QtiCallConstants.RTT_CALL_TYPE_RTT) == QtiCallConstants.RTT_CALL_TYPE_RTT;
    }

    // Returns value of RTT visibility
    public static int getRttVisibility(Context context) {
        return getRttVisibility(context, QtiCallConstants.RTT_DEFAULT_PHONE_ID);
    }

    public static int getRttVisibility(Context context, int phoneId) {
        return (android.provider.Settings.Global.getInt(context.getContentResolver(),
            QtiCallConstants.QTI_IMS_RTT_VISIBILITY + convertRttPhoneId(phoneId), 0));
    }

    // Sets RTT visibility to global settings
    public static void setRttVisibility(boolean value, Context context) {
       setRttVisibility(value, context, QtiCallConstants.RTT_DEFAULT_PHONE_ID);
    }

    public static void setRttVisibility(boolean value, Context context, int phoneId) {
       android.provider.Settings.Global.putInt(context.getContentResolver(),
                QtiCallConstants.QTI_IMS_RTT_VISIBILITY + convertRttPhoneId(phoneId), value ?
                        QtiCallConstants.RTT_VISIBILITY_ENABLED :
                        QtiCallConstants.RTT_VISIBILITY_DISABLED);
    }

    // Returns true if Carrier supports RTT
    public static boolean isRttSupported(int phoneId, Context context) {
        return isCarrierConfigEnabled(phoneId, context,
            CarrierConfigManager.KEY_RTT_SUPPORTED_BOOL);
    }

    // Returns true if Carrier supports RTT auto upgrade
    public static boolean isRttAutoUpgradeSupported(int phoneId, Context context) {
        return isCarrierConfigEnabled(phoneId, context,
            CarrierConfigManager.KEY_RTT_AUTO_UPGRADE_BOOL);
    }

    // Returns true if Carrier supports RTT for Video Calls
    public static boolean isRttSupportedOnVtCalls(int phoneId, Context context) {
        return isCarrierConfigEnabled(phoneId, context,
            CarrierConfigManager.KEY_RTT_SUPPORTED_FOR_VT_BOOL);
    }

    // Returns true if Carrier supports RTT upgrade
    // False otherwise
    public static boolean isRttUpgradeSupported(int phoneId, Context context) {
        return isCarrierConfigEnabled(phoneId, context,
            CarrierConfigManager.KEY_RTT_UPGRADE_SUPPORTED_BOOL);
    }

    // Utility to get the RTT Mode that is set through ImsSettings
    // Mode can be either RTT_UPON_REQUEST_MODE or RTT_AUTOMATIC_MODE
    public static int getRttOperatingMode(Context context) {
        return getRttOperatingMode(context, QtiCallConstants.RTT_DEFAULT_PHONE_ID);
    }

    // Utility to get the RTT Operation Mode that is set through ImsSettings
    // Mode can be either RTT_UPON_REQUEST_MODE or RTT_AUTOMATIC_MODE
    // Takes a phoneId to support DSDS configuration.
    public static int getRttOperatingMode(Context context, int phoneId) {
        if (shallShowRttVisibilitySetting(phoneId, context)) {
            return QtiCallConstants.RTT_AUTOMATIC_MODE;
        }
        return android.provider.Settings.Global.getInt(context.getContentResolver(),
                QtiCallConstants.QTI_IMS_RTT_OPERATING_MODE + convertRttPhoneId(phoneId),
                QtiCallConstants.RTT_UPON_REQUEST_MODE);
    }

    // Returns true if Carrier supports RTT downgrade
    // False otherwise
    public static boolean isRttDowngradeSupported(int phoneId, Context context) {
        return isCarrierConfigEnabled(phoneId, context,
            CarrierConfigManager.KEY_RTT_DOWNGRADE_SUPPORTED_BOOL);
    }

    /* @Deprecated
     * Returns true if Carrier supports RTT downgrade
     * False otherwise
     * This functionality will now be supported using {@link PhoneAccount.CAPABILITY_RTT}
     * check
     */
    public static boolean isSimlessRttDowgradeSupported(int phoneId, Context context) {
        int simLessRttDowngradeSupportedValue = android.provider.Settings.Secure.getInt(
                context.getContentResolver(), QtiCallConstants.
                SIMLESS_RTT_DOWNGRADE_SUPPORTED + convertRttPhoneId(phoneId),
                QTI_IMS_RTT_DOWNGRADE_NOT_SUPPORTED);
        return simLessRttDowngradeSupportedValue != QTI_IMS_RTT_DOWNGRADE_NOT_SUPPORTED;
    }

    /* @Deprecated
     * Returns true if previous carrier supported RTT upgrade
     * False otherwise
     * This functionality will now be supported using
     * {@link PhoneAccount.CAPABILITY_DOWNGRADE_RTT} check
     */
    public static boolean isSimlessRttSupported(int phoneId, Context context) {
        int simLessRttSupportedValue = android.provider.Settings.Secure.getInt(
                context.getContentResolver(), QtiCallConstants.
                SIMLESS_RTT_SUPPORTED + convertRttPhoneId(phoneId),
                QTI_IMS_RTT_NOT_SUPPORTED);
        return simLessRttSupportedValue != QTI_IMS_RTT_NOT_SUPPORTED;
    }

    // Returns true if Carrier support RTT visibility setting
    // False otherwise
    public static boolean shallShowRttVisibilitySetting(int phoneId, Context context) {
        return (isCarrierConfigEnabled(phoneId, context,
                QtiCarrierConfigs.KEY_SHOW_RTT_VISIBILITY_SETTING));
    }

    // Returns true if Carrier supports merging RTT calls
    // False otherwise
    public static boolean isRttMergeSupported(int phoneId, Context context) {
        return isCarrierConfigEnabled(phoneId, context,
            "allow_merging_rtt_calls_bool");
    }

    // Returns true if Carrier supports Cancel Modify Call
    public static boolean isCancelModifyCallSupported(int phoneId, Context context) {
        return (isCarrierConfigEnabled(phoneId, context,
                QtiCarrierConfigs.KEY_CARRIER_CANCEL_MODIFY_CALL_SUPPORTED));
    }

    // Compat method for legacy IMS APKs
    public static void setAutoReject(ContentResolver contentResolver, int phoneId, boolean turnOn) {
        setAutoRejectMode(contentResolver, phoneId,
                turnOn ? QtiCallConstants.AR_MODE_AUTO_REJECT
                       : QtiCallConstants.AR_MODE_ALLOW_INCOMING);
    }

    // Supported for multi sim only. Allows user to set auto reject call mode for IMS MT calls
    // when high priority data is on the other sub
    public static void setAutoRejectMode(ContentResolver contentResolver, int phoneId,
            int arMode) {
        android.provider.Settings.Global.putInt(contentResolver,
                QtiCallConstants.IMS_AUTO_REJECT_MODE + phoneId, arMode);
    }

    // Compat method for legacy IMS APKs
    public static int getAutoReject(ContentResolver contentResolver, int phoneId) {
        return getAutoRejectMode(contentResolver, phoneId);
    }

    // Supported for multi sim only.
    public static int getAutoRejectMode(ContentResolver contentResolver, int phoneId) {
        return android.provider.Settings.Global.getInt(contentResolver,
                QtiCallConstants.IMS_AUTO_REJECT_MODE + phoneId,
                QtiCallConstants.AR_MODE_ALLOW_INCOMING);
    }

    public static boolean canAcceptAsOneWayVideo(int phoneId, Context context) {
        return (isCarrierConfigEnabled(phoneId, context,
                QtiCarrierConfigs.ALLOW_ONE_WAY_ACCEPT_FOR_VIDEO_CALL));
    }

    // Returns true if Carrier supports Call Composer
    public static boolean isCallComposerSupported(int phoneId, Context context) {
        return isCarrierConfigEnabled(phoneId, context,
                QtiCarrierConfigs.KEY_CARRIER_CALL_COMPOSER_SUPPORTED);
    }

    // Stores user setting for Call Composer
    public static void setCallComposerMode(ContentResolver contentResolver, int phoneId,
            boolean turnOn) {
        final int value = turnOn ? QtiCallConstants.CALL_COMPOSER_ENABLED :
                QtiCallConstants.CALL_COMPOSER_DISABLED;
        android.provider.Settings.Global.putInt(contentResolver,
                QtiCallConstants.IMS_CALL_COMPOSER + phoneId, value);
    }

    // retrieves the stored user setting from the database per phone id
    public static int getCallComposerMode(ContentResolver contentResolver, int phoneId) {
        return android.provider.Settings.Global.getInt(contentResolver,
                QtiCallConstants.IMS_CALL_COMPOSER + phoneId,
                QtiCallConstants.CALL_COMPOSER_DISABLED);
    }

    // Returns true if Carrier supports B2C Enriched Calling
    public static boolean isB2cEnrichedCallingSupported(int phoneId, Context context) {
        return isCarrierConfigEnabled(phoneId, context,
                QtiCarrierConfigs.KEY_CARRIER_B2C_ENRICHED_CALLING_SUPPORTED);
    }

    // Stores user setting for B2C Enriched Calling
    public static void setB2cEnrichedCallingMode(ContentResolver contentResolver, int phoneId,
            boolean turnOn) {
        final int value = turnOn ? QtiCallConstants.B2C_ENRICHED_CALLING_ENABLED :
                QtiCallConstants.B2C_ENRICHED_CALLING_DISABLED;
        android.provider.Settings.Global.putInt(contentResolver,
                QtiCallConstants.B2C_ENRICHED_CALLING + phoneId, value);
    }

    // retrieves the stored user setting from the database per phone id
    public static int getB2cEnrichedCallingMode(ContentResolver contentResolver, int phoneId) {
        return android.provider.Settings.Global.getInt(contentResolver,
                QtiCallConstants.B2C_ENRICHED_CALLING + phoneId,
                QtiCallConstants.B2C_ENRICHED_CALLING_DISABLED);
    }

    // Sets RTT Operation Mode to global settings
    // Takes a phoneId to support DSDS configuration.
    public static void setRttOperatingMode(ContentResolver contentResolver, int phoneId,
            int rttOpMode) {
        android.provider.Settings.Global.putInt(contentResolver,
                QtiCallConstants.QTI_IMS_RTT_OPERATING_MODE + convertRttPhoneId(phoneId),
                rttOpMode);
    }

    // Returns true if Carrier supports video CRS
    public static boolean isVideoCrsSupported(int phoneId, Context context) {
        return isCarrierConfigEnabled(phoneId, context,
                QtiCarrierConfigs.KEY_CARRIER_VIDEO_CRS_SUPPORTED);
    }

    // Returns true if Carrier supports video CRBT
    public static boolean isVideoCrbtSupported(int phoneId, Context context) {
        return isCarrierConfigEnabled(phoneId, context,
                QtiCarrierConfigs.KEY_CARRIER_VIDEO_CRBT_SUPPORTED);
    }

    // Returns true if carrier supports call progress notification.
    public static boolean isCallProgressNotificationSupported(int phoneId, Context context) {
        return isCarrierConfigEnabled(phoneId, context,
                QtiCarrierConfigs.KEY_CARRIER_CALL_PROGRESS_NOTIFICATION_SUPPORTED);
    }

    /**
     * Retrieves the customer service numbers stored for specific operator
     * Returns stored numbers, or null otherwise.
     */
    public static String[] getCustomerServiceNumbers(int phoneId, Context context) {
        return QtiCarrierConfigHelper.getInstance().getStringArray(context, phoneId,
                QtiCarrierConfigs.KEY_CARRIER_VIDEO_CUSTOMER_SERVICE_NUMBERS);
    }

    /**
     * Returns true if carrier supports set media config(screen size, max
     * avc/hevc resolution) to modem.
     */
    public static boolean isSendMediaConfigurationSupported(int phoneId, Context context) {
        return isCarrierConfigEnabled(phoneId, context,
                QtiCarrierConfigs.KEY_CARRIER_SEND_MEDIA_CONFIG_SUPPORTED);
    }

    // Returns true if Carrier supports data channel
    public static boolean isDataChannelSupported(int phoneId, Context context) {
        return isCarrierConfigEnabled(phoneId, context,
                QtiCarrierConfigs.KEY_CARRIER_DATA_CHANNEL_SUPPORTED);
    }

    // Stores user setting for data channel
    public static void setDataChannelUserPreference(ContentResolver contentResolver,
            int phoneId, boolean turnOn) {
        final int value = turnOn ? QtiCallConstants.DATA_CHANNEL_ENABLED :
                QtiCallConstants.DATA_CHANNEL_DISABLED;
        android.provider.Settings.Global.putInt(contentResolver,
                QtiCallConstants.DATA_CHANNEL + phoneId, value);
    }

    // retrieves the stored user setting from the database per phone id
    public static int getDataChannelUserPreference(ContentResolver contentResolver,
            int phoneId) {
        return android.provider.Settings.Global.getInt(contentResolver,
                QtiCallConstants.DATA_CHANNEL + phoneId,
                QtiCallConstants.DATA_CHANNEL_DISABLED);
    }

    // Returns true if Carrier supports video online service
    public static boolean isVosSupported(int phoneId, Context context) {
        return isCarrierConfigEnabled(phoneId, context,
                QtiCarrierConfigs.KEY_CARRIER_VIDEO_ONLINE_SERVICE_SUPPORTED);
    }

    public static void executeMethodAsync(Runnable r, String errorLogName, Executor executor,
            String permission, Context context) throws RemoteException {
        context.enforceCallingOrSelfPermission(permission, errorLogName);
        try {
            CompletableFuture.runAsync(r, executor).join();
        } catch (CancellationException | CompletionException e) {
            Log.w(LOG_TAG, "executeMethodAsync for " + errorLogName + " failed with: " +
                    e.getMessage());
            throw new RemoteException(e.getMessage());
        }
    }

    public static <T> T executeMethodAsyncForResult(Supplier<T> r, String errorLogName,
            Executor executor, String permission, Context context) throws RemoteException {
        context.enforceCallingOrSelfPermission(permission, errorLogName);
        try {
            CompletableFuture<T> future = CompletableFuture.supplyAsync(r, executor);
            return future.get();
        } catch (ExecutionException | InterruptedException e) {
            Log.w(LOG_TAG, "executeMethodAsyncForResult for " + errorLogName + " failed with: " +
                    e.getMessage());
            throw new RemoteException(e.getMessage());
        }
    }
}

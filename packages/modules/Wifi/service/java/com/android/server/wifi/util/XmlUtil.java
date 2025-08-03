/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.server.wifi.util;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.compat.CompatChanges;
import android.net.InetAddresses;
import android.net.IpConfiguration;
import android.net.IpConfiguration.IpAssignment;
import android.net.IpConfiguration.ProxySettings;
import android.net.LinkAddress;
import android.net.MacAddress;
import android.net.ProxyInfo;
import android.net.RouteInfo;
import android.net.StaticIpConfiguration;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.SecurityParams;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.NetworkSelectionStatus;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiMigration;
import android.net.wifi.WifiSsid;
import android.os.ParcelUuid;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.util.SparseIntArray;

import com.android.modules.utils.build.SdkLevel;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * Utils for manipulating XML data. This is essentially a wrapper over XmlUtils provided by core.
 * The utility provides methods to write/parse section headers and write/parse values.
 * This utility is designed for formatting the XML into the following format:
 * <Document Header>
 *  <Section 1 Header>
 *   <Value 1>
 *   <Value 2>
 *   ...
 *   <Sub Section 1 Header>
 *    <Value 1>
 *    <Value 2>
 *    ...
 *   </Sub Section 1 Header>
 *  </Section 1 Header>
 * </Document Header>
 *
 * Note: These utility methods are meant to be used for:
 * 1. Backup/restore wifi network data to/from cloud.
 * 2. Persisting wifi network data to/from disk.
 */
public class XmlUtil {
    private static final String TAG = "WifiXmlUtil";

    /**
     * Ensure that the XML stream is at a start tag or the end of document.
     *
     * @throws XmlPullParserException if parsing errors occur.
     */
    private static void gotoStartTag(XmlPullParser in)
            throws XmlPullParserException, IOException {
        int type = in.getEventType();
        while (type != XmlPullParser.START_TAG && type != XmlPullParser.END_DOCUMENT) {
            type = in.next();
        }
    }

    /**
     * Ensure that the XML stream is at an end tag or the end of document.
     *
     * @throws XmlPullParserException if parsing errors occur.
     */
    private static void gotoEndTag(XmlPullParser in)
            throws XmlPullParserException, IOException {
        int type = in.getEventType();
        while (type != XmlPullParser.END_TAG && type != XmlPullParser.END_DOCUMENT) {
            type = in.next();
        }
    }

    /**
     * Start processing the XML stream at the document header.
     *
     * @param in         XmlPullParser instance pointing to the XML stream.
     * @param headerName expected name for the start tag.
     * @throws XmlPullParserException if parsing errors occur.
     */
    public static void gotoDocumentStart(XmlPullParser in, String headerName)
            throws XmlPullParserException, IOException {
        XmlUtilHelper.beginDocument(in, headerName);
    }

    /**
     * Move the XML stream to the next section header or indicate if there are no more sections.
     * The provided outerDepth is used to find sub sections within that depth.
     *
     * Use this to move across sections if the ordering of sections are variable. The returned name
     * can be used to decide what section is next.
     *
     * @param in         XmlPullParser instance pointing to the XML stream.
     * @param headerName An array of one string, used to return the name of the next section.
     * @param outerDepth Find section within this depth.
     * @return {@code true} if a next section is found, {@code false} if there are no more sections.
     * @throws XmlPullParserException if parsing errors occur.
     */
    public static boolean gotoNextSectionOrEnd(
            XmlPullParser in, String[] headerName, int outerDepth)
            throws XmlPullParserException, IOException {
        if (XmlUtilHelper.nextElementWithin(in, outerDepth)) {
            headerName[0] = in.getName();
            return true;
        }
        return false;
    }

    /**
     * Move the XML stream to the next section header or indicate if there are no more sections.
     * If a section, exists ensure that the name matches the provided name.
     * The provided outerDepth is used to find sub sections within that depth.
     *
     * Use this to move across repeated sections until the end.
     *
     * @param in           XmlPullParser instance pointing to the XML stream.
     * @param expectedName expected name for the section header.
     * @param outerDepth   Find section within this depth.
     * @return {@code true} if a next section is found, {@code false} if there are no more sections.
     * @throws XmlPullParserException if the section header name does not match |expectedName|,
     *                                or if parsing errors occur.
     */
    public static boolean gotoNextSectionWithNameOrEnd(
            XmlPullParser in, String expectedName, int outerDepth)
            throws XmlPullParserException, IOException {
        String[] headerName = new String[1];
        if (gotoNextSectionOrEnd(in, headerName, outerDepth)) {
            if (headerName[0].equals(expectedName)) {
                return true;
            }
            throw new XmlPullParserException(
                    "Next section name does not match expected name: " + expectedName);
        }
        return false;
    }

    /**
     * Move the XML stream to the next section header and ensure that the name matches the provided
     * name.
     * The provided outerDepth is used to find sub sections within that depth.
     *
     * Use this to move across sections if the ordering of sections are fixed.
     *
     * @param in           XmlPullParser instance pointing to the XML stream.
     * @param expectedName expected name for the section header.
     * @param outerDepth   Find section within this depth.
     * @throws XmlPullParserException if the section header name does not match |expectedName|,
     *                                there are no more sections or if parsing errors occur.
     */
    public static void gotoNextSectionWithName(
            XmlPullParser in, String expectedName, int outerDepth)
            throws XmlPullParserException, IOException {
        if (!gotoNextSectionWithNameOrEnd(in, expectedName, outerDepth)) {
            throw new XmlPullParserException("Section not found. Expected: " + expectedName);
        }
    }

    /**
     * Checks if the stream is at the end of a section of values. This moves the stream to next tag
     * and checks if it finds an end tag at the specified depth.
     *
     * @param in           XmlPullParser instance pointing to the XML stream.
     * @param sectionDepth depth of the start tag of this section. Used to match the end tag.
     * @return {@code true} if a end tag at the provided depth is found, {@code false} otherwise
     * @throws XmlPullParserException if parsing errors occur.
     */
    public static boolean isNextSectionEnd(XmlPullParser in, int sectionDepth)
            throws XmlPullParserException, IOException {
        return !XmlUtilHelper.nextElementWithin(in, sectionDepth);
    }

    /**
     * Read the current value in the XML stream using core XmlUtils and stores the retrieved
     * value name in the string provided. This method reads the value contained in current start
     * tag.
     * Note: Because there could be genuine null values being read from the XML, this method raises
     * an exception to indicate errors.
     *
     * @param in        XmlPullParser instance pointing to the XML stream.
     * @param valueName An array of one string, used to return the name attribute
     *                  of the value's tag.
     * @return value retrieved from the XML stream.
     * @throws XmlPullParserException if parsing errors occur.
     */
    public static Object readCurrentValue(XmlPullParser in, String[] valueName)
            throws XmlPullParserException, IOException {
        Object value = XmlUtilHelper.readValueXml(in, valueName);
        // XmlUtils.readValue does not always move the stream to the end of the tag. So, move
        // it to the end tag before returning from here.
        gotoEndTag(in);
        return value;
    }

    /**
     * Read the next value in the XML stream using core XmlUtils and ensure that it matches the
     * provided name. This method moves the stream to the next start tag and reads the value
     * contained in it.
     * Note: Because there could be genuine null values being read from the XML, this method raises
     * an exception to indicate errors.
     *
     * @param in XmlPullParser instance pointing to the XML stream.
     * @return value retrieved from the XML stream.
     * @throws XmlPullParserException if the value read does not match |expectedName|,
     *                                or if parsing errors occur.
     */
    public static Object readNextValueWithName(XmlPullParser in, String expectedName)
            throws XmlPullParserException, IOException {
        String[] valueName = new String[1];
        XmlUtilHelper.nextElement(in);
        Object value = readCurrentValue(in, valueName);
        if (valueName[0].equals(expectedName)) {
            return value;
        }
        throw new XmlPullParserException(
                "Value not found. Expected: " + expectedName + ", but got: " + valueName[0]);
    }

    /**
     * Write the XML document start with the provided document header name.
     *
     * @param out        XmlSerializer instance pointing to the XML stream.
     * @param headerName name for the start tag.
     */
    public static void writeDocumentStart(XmlSerializer out, String headerName)
            throws IOException {
        out.startDocument(null, true);
        out.startTag(null, headerName);
    }

    /**
     * Write the XML document end with the provided document header name.
     *
     * @param out        XmlSerializer instance pointing to the XML stream.
     * @param headerName name for the end tag.
     */
    public static void writeDocumentEnd(XmlSerializer out, String headerName)
            throws IOException {
        out.endTag(null, headerName);
        out.endDocument();
    }

    /**
     * Write a section start header tag with the provided section name.
     *
     * @param out        XmlSerializer instance pointing to the XML stream.
     * @param headerName name for the start tag.
     */
    public static void writeNextSectionStart(XmlSerializer out, String headerName)
            throws IOException {
        out.startTag(null, headerName);
    }

    /**
     * Write a section end header tag with the provided section name.
     *
     * @param out        XmlSerializer instance pointing to the XML stream.
     * @param headerName name for the end tag.
     */
    public static void writeNextSectionEnd(XmlSerializer out, String headerName)
            throws IOException {
        out.endTag(null, headerName);
    }

    /**
     * Write the value with the provided name in the XML stream using core XmlUtils.
     *
     * @param out   XmlSerializer instance pointing to the XML stream.
     * @param name  name of the value.
     * @param value value to be written.
     */
    public static void writeNextValue(XmlSerializer out, String name, Object value)
            throws XmlPullParserException, IOException {
        XmlUtilHelper.writeValueXml(value, name, out);
    }

    /**
     * Utility class to serialize and deserialize {@link WifiConfiguration} object to XML &
     * vice versa.
     * This is used by both {@link com.android.server.wifi.WifiConfigStore} &
     * {@link com.android.server.wifi.WifiBackupRestore} modules.
     * The |writeConfigurationToXml| has 2 versions, one for backup and one for config store.
     * There is only 1 version of |parseXmlToConfiguration| for both backup & config store.
     * The parse method is written so that any element added/deleted in future revisions can
     * be easily handled.
     */
    public static class WifiConfigurationXmlUtil {
        /**
         * List of XML tags corresponding to WifiConfiguration object elements.
         */
        public static final String XML_TAG_SSID = "SSID";
        public static final String XML_TAG_BSSID = "BSSID";
        public static final String XML_TAG_CONFIG_KEY = "ConfigKey";
        public static final String XML_TAG_PRE_SHARED_KEY = "PreSharedKey";
        public static final String XML_TAG_WEP_KEYS = "WEPKeys";
        public static final String XML_TAG_WEP_TX_KEY_INDEX = "WEPTxKeyIndex";
        public static final String XML_TAG_HIDDEN_SSID = "HiddenSSID";
        public static final String XML_TAG_REQUIRE_PMF = "RequirePMF";
        public static final String XML_TAG_ALLOWED_KEY_MGMT = "AllowedKeyMgmt";
        public static final String XML_TAG_ALLOWED_PROTOCOLS = "AllowedProtocols";
        public static final String XML_TAG_ALLOWED_AUTH_ALGOS = "AllowedAuthAlgos";
        public static final String XML_TAG_ALLOWED_GROUP_CIPHERS = "AllowedGroupCiphers";
        public static final String XML_TAG_ALLOWED_PAIRWISE_CIPHERS = "AllowedPairwiseCiphers";
        public static final String XML_TAG_ALLOWED_GROUP_MGMT_CIPHERS = "AllowedGroupMgmtCiphers";
        public static final String XML_TAG_ALLOWED_SUITE_B_CIPHERS = "AllowedSuiteBCiphers";
        public static final String XML_TAG_SHARED = "Shared";
        public static final String XML_TAG_STATUS = "Status";
        public static final String XML_TAG_FQDN = "FQDN";
        public static final String XML_TAG_PROVIDER_FRIENDLY_NAME = "ProviderFriendlyName";
        public static final String XML_TAG_LINKED_NETWORKS_LIST = "LinkedNetworksList";
        public static final String XML_TAG_DEFAULT_GW_MAC_ADDRESS = "DefaultGwMacAddress";
        public static final String XML_TAG_VALIDATED_INTERNET_ACCESS = "ValidatedInternetAccess";
        public static final String XML_TAG_NO_INTERNET_ACCESS_EXPECTED = "NoInternetAccessExpected";
        public static final String XML_TAG_METERED_HINT = "MeteredHint";
        public static final String XML_TAG_METERED_OVERRIDE = "MeteredOverride";
        public static final String XML_TAG_USE_EXTERNAL_SCORES = "UseExternalScores";
        public static final String XML_TAG_CREATOR_UID = "CreatorUid";
        public static final String XML_TAG_CREATOR_NAME = "CreatorName";
        public static final String XML_TAG_LAST_UPDATE_UID = "LastUpdateUid";
        public static final String XML_TAG_LAST_UPDATE_NAME = "LastUpdateName";
        public static final String XML_TAG_LAST_CONNECT_UID = "LastConnectUid";
        public static final String XML_TAG_IS_LEGACY_PASSPOINT_CONFIG = "IsLegacyPasspointConfig";
        public static final String XML_TAG_ROAMING_CONSORTIUM_OIS = "RoamingConsortiumOIs";
        public static final String XML_TAG_RANDOMIZED_MAC_ADDRESS = "RandomizedMacAddress";
        public static final String XML_TAG_MAC_RANDOMIZATION_SETTING = "MacRandomizationSetting";
        public static final String XML_TAG_SEND_DHCP_HOSTNAME = "SendDhcpHostname";
        public static final String XML_TAG_CARRIER_ID = "CarrierId";
        public static final String XML_TAG_SUBSCRIPTION_ID = "SubscriptionId";
        public static final String XML_TAG_IS_AUTO_JOIN = "AutoJoinEnabled";
        public static final String XML_TAG_PRIORITY = "Priority";
        public static final String XML_TAG_DELETION_PRIORITY = "DeletionPriority";
        public static final String XML_TAG_NUM_REBOOTS_SINCE_LAST_USE = "NumRebootsSinceLastUse";

        public static final String XML_TAG_IS_TRUSTED = "Trusted";
        public static final String XML_TAG_IS_OEM_PAID = "OemPaid";
        public static final String XML_TAG_IS_OEM_PRIVATE = "OemPrivate";
        public static final String XML_TAG_IS_CARRIER_MERGED = "CarrierMerged";
        public static final String XML_TAG_SECURITY_PARAMS_LIST = "SecurityParamsList";
        public static final String XML_TAG_SECURITY_PARAMS = "SecurityParams";
        public static final String XML_TAG_SECURITY_TYPE = "SecurityType";
        public static final String XML_TAG_IS_ENABLED = "IsEnabled";
        public static final String XML_TAG_SAE_IS_H2E_ONLY_MODE = "SaeIsH2eOnlyMode";
        public static final String XML_TAG_SAE_IS_PK_ONLY_MODE = "SaeIsPkOnlyMode";
        public static final String XML_TAG_IS_ADDED_BY_AUTO_UPGRADE = "IsAddedByAutoUpgrade";
        private static final String XML_TAG_IS_MOST_RECENTLY_CONNECTED = "IsMostRecentlyConnected";
        private static final String XML_TAG_IS_RESTRICTED = "IsRestricted";
        private static final String XML_TAG_SUBSCRIPTION_GROUP = "SubscriptionGroup";
        public static final String XML_TAG_BSSID_ALLOW_LIST = "bssidAllowList";
        public static final String XML_TAG_IS_REPEATER_ENABLED = "RepeaterEnabled";
        public static final String XML_TAG_DPP_PRIVATE_EC_KEY = "DppPrivateEcKey";
        public static final String XML_TAG_DPP_CONNECTOR = "DppConnector";
        public static final String XML_TAG_DPP_CSIGN_KEY = "DppCSignKey";
        public static final String XML_TAG_DPP_NET_ACCESS_KEY = "DppNetAccessKey";
        public static final String XML_TAG_ENABLE_WIFI7 = "EnableWifi7";

        /**
         * Write Wep Keys to the XML stream.
         * WepKeys array is initialized in WifiConfiguration constructor and all the elements
         * are set to null. User may choose to set any one of the key elements in WifiConfiguration.
         * XmlUtils serialization doesn't handle this array of nulls well .
         * So, write empty strings if the keys are not initialized and null if all
         * the elements are empty.
         */
        private static void writeWepKeysToXml(XmlSerializer out, String[] wepKeys,
                @Nullable WifiConfigStoreEncryptionUtil encryptionUtil)
                throws XmlPullParserException, IOException {
            final int len = wepKeys == null ? 0 : wepKeys.length;
            String[] wepKeysToWrite = new String[len];
            boolean hasWepKey = false;
            for (int i = 0; i < len; i++) {
                if (wepKeys[i] == null) {
                    wepKeysToWrite[i] = new String();
                } else {
                    wepKeysToWrite[i] = wepKeys[i];
                    hasWepKey = true;
                }
            }
            if (!hasWepKey) {
                XmlUtil.writeNextValue(out, XML_TAG_WEP_KEYS, null);
                return;
            }
            if (encryptionUtil == null) {
                XmlUtil.writeNextValue(out, XML_TAG_WEP_KEYS, wepKeysToWrite);
                return;
            }
            EncryptedData[] encryptedDataArray = new EncryptedData[len];
            for (int i = 0; i < len; i++) {
                if (wepKeys[i] == null) {
                    encryptedDataArray[i] = new EncryptedData(new byte[0], new byte[0]);
                } else {
                    encryptedDataArray[i] = encryptionUtil.encrypt(wepKeys[i].getBytes());
                    if (encryptedDataArray[i] == null) {
                        // We silently fail encryption failures!
                        Log.wtf(TAG, "Encryption of WEP keys failed");
                        // If any key encryption fails, we just fall back with unencrypted keys.
                        XmlUtil.writeNextValue(out, XML_TAG_WEP_KEYS, wepKeysToWrite);
                        return;
                    }
                }
            }
            XmlUtil.writeNextSectionStart(out, XML_TAG_WEP_KEYS);
            for (int i = 0; i < len; i++) {
                XmlUtil.EncryptedDataXmlUtil.writeToXml(out, encryptedDataArray[i]);
            }
            XmlUtil.writeNextSectionEnd(out, XML_TAG_WEP_KEYS);
        }

        /**
         * Write preshared key to the XML stream.
         *
         * If encryptionUtil is null or if encryption fails for some reason, the pre-shared
         * key is stored in plaintext, else the encrypted psk is stored.
         */
        private static void writePreSharedKeyToXml(
                XmlSerializer out, WifiConfiguration wifiConfig,
                @Nullable WifiConfigStoreEncryptionUtil encryptionUtil)
                throws XmlPullParserException, IOException {
            EncryptedData encryptedData = null;
            if (encryptionUtil != null && wifiConfig.preSharedKey != null) {
                if (wifiConfig.hasEncryptedPreSharedKey() && !wifiConfig.hasPreSharedKeyChanged()) {
                    encryptedData = new EncryptedData(wifiConfig.getEncryptedPreSharedKey(),
                            wifiConfig.getEncryptedPreSharedKeyIv());
                } else {
                    encryptedData = encryptionUtil.encrypt(wifiConfig.preSharedKey.getBytes());
                    if (encryptedData == null) {
                        // We silently fail encryption failures!
                        Log.wtf(TAG, "Encryption of preSharedKey failed");
                    }
                }
            }
            if (encryptedData != null) {
                writeNextSectionStart(out, XML_TAG_PRE_SHARED_KEY);
                EncryptedDataXmlUtil.writeToXml(out, encryptedData);
                wifiConfig.setEncryptedPreSharedKey(encryptedData.getEncryptedData(),
                        encryptedData.getIv());
                wifiConfig.setHasPreSharedKeyChanged(false);
                writeNextSectionEnd(out, XML_TAG_PRE_SHARED_KEY);
            } else {
                writeNextValue(out, XML_TAG_PRE_SHARED_KEY, wifiConfig.preSharedKey);
            }
        }

        private static void writeSecurityParamsListToXml(
                XmlSerializer out, WifiConfiguration configuration)
                throws XmlPullParserException, IOException {
            XmlUtil.writeNextSectionStart(out, XML_TAG_SECURITY_PARAMS_LIST);
            for (SecurityParams params: configuration.getSecurityParamsList()) {
                XmlUtil.writeNextSectionStart(out, XML_TAG_SECURITY_PARAMS);
                XmlUtil.writeNextValue(
                        out, XML_TAG_SECURITY_TYPE,
                        params.getSecurityType());
                XmlUtil.writeNextValue(
                        out, XML_TAG_IS_ENABLED,
                        params.isEnabled());
                XmlUtil.writeNextValue(
                        out, XML_TAG_SAE_IS_H2E_ONLY_MODE,
                        params.isSaeH2eOnlyMode());
                XmlUtil.writeNextValue(
                        out, XML_TAG_SAE_IS_PK_ONLY_MODE,
                        params.isSaePkOnlyMode());
                XmlUtil.writeNextValue(
                        out, XML_TAG_IS_ADDED_BY_AUTO_UPGRADE,
                        params.isAddedByAutoUpgrade());
                XmlUtil.writeNextValue(
                        out, XML_TAG_ALLOWED_SUITE_B_CIPHERS,
                        params.getAllowedSuiteBCiphers().toByteArray());
                XmlUtil.writeNextSectionEnd(out, XML_TAG_SECURITY_PARAMS);
            }

            XmlUtil.writeNextSectionEnd(out, XML_TAG_SECURITY_PARAMS_LIST);
        }

        private static void writeEncryptedBytesToXml(
                XmlSerializer out, @Nullable WifiConfigStoreEncryptionUtil encryptionUtil,
                String tag, byte[] data)
                throws XmlPullParserException, IOException {
            EncryptedData encryptedData = null;
            if (encryptionUtil != null) {
                encryptedData = encryptionUtil.encrypt(data);
                if (encryptedData == null && data != null && data.length != 0) {
                    // We silently fail encryption failures!
                    Log.wtf(TAG, "Encryption of " + tag + " failed");
                }
            }
            if (encryptedData != null) {
                XmlUtil.writeNextSectionStart(out, tag);
                EncryptedDataXmlUtil.writeToXml(out, encryptedData);
                XmlUtil.writeNextSectionEnd(out, tag);
            } else {
                XmlUtil.writeNextValue(out, tag, data);
            }
        }

        /**
         * Write dpp configuration and connection keys to the XML stream.
         *
         * If encryptionUtil is null or if encryption fails for some reason, the dpp
         * keys are stored in plaintext, else the encrypted keys are stored.
         */
        private static void writeDppConfigurationToXml(
                XmlSerializer out, WifiConfiguration configuration,
                @Nullable WifiConfigStoreEncryptionUtil encryptionUtil)
                throws XmlPullParserException, IOException {
            writeEncryptedBytesToXml(out, encryptionUtil, XML_TAG_DPP_PRIVATE_EC_KEY,
                    configuration.getDppPrivateEcKey());
            writeEncryptedBytesToXml(out, encryptionUtil, XML_TAG_DPP_CONNECTOR,
                    configuration.getDppConnector());
            writeEncryptedBytesToXml(out, encryptionUtil, XML_TAG_DPP_CSIGN_KEY,
                    configuration.getDppCSignKey());
            writeEncryptedBytesToXml(out, encryptionUtil, XML_TAG_DPP_NET_ACCESS_KEY,
                    configuration.getDppNetAccessKey());
        }

        /**
         * Write the Configuration data elements that are common for backup & config store to the
         * XML stream.
         *
         * @param out XmlSerializer instance pointing to the XML stream.
         * @param configuration WifiConfiguration object to be serialized.
         * @param encryptionUtil Instance of {@link EncryptedDataXmlUtil}. Backup/restore stores
         *                       keys unencrypted.
         */
        public static void writeCommonElementsToXml(
                XmlSerializer out, WifiConfiguration configuration,
                @Nullable WifiConfigStoreEncryptionUtil encryptionUtil)
                throws XmlPullParserException, IOException {
            XmlUtil.writeNextValue(out, XML_TAG_CONFIG_KEY, configuration.getKey());
            XmlUtil.writeNextValue(out, XML_TAG_SSID, configuration.SSID);
            writePreSharedKeyToXml(out, configuration, encryptionUtil);
            writeWepKeysToXml(out, configuration.wepKeys, encryptionUtil);
            XmlUtil.writeNextValue(out, XML_TAG_WEP_TX_KEY_INDEX, configuration.wepTxKeyIndex);
            XmlUtil.writeNextValue(out, XML_TAG_HIDDEN_SSID, configuration.hiddenSSID);
            XmlUtil.writeNextValue(out, XML_TAG_REQUIRE_PMF, configuration.requirePmf);
            XmlUtil.writeNextValue(
                    out, XML_TAG_ALLOWED_KEY_MGMT,
                    configuration.allowedKeyManagement.toByteArray());
            XmlUtil.writeNextValue(
                    out, XML_TAG_ALLOWED_PROTOCOLS,
                    configuration.allowedProtocols.toByteArray());
            XmlUtil.writeNextValue(
                    out, XML_TAG_ALLOWED_AUTH_ALGOS,
                    configuration.allowedAuthAlgorithms.toByteArray());
            XmlUtil.writeNextValue(
                    out, XML_TAG_ALLOWED_GROUP_CIPHERS,
                    configuration.allowedGroupCiphers.toByteArray());
            XmlUtil.writeNextValue(
                    out, XML_TAG_ALLOWED_PAIRWISE_CIPHERS,
                    configuration.allowedPairwiseCiphers.toByteArray());
            XmlUtil.writeNextValue(
                    out, XML_TAG_ALLOWED_GROUP_MGMT_CIPHERS,
                    configuration.allowedGroupManagementCiphers.toByteArray());
            XmlUtil.writeNextValue(
                    out, XML_TAG_ALLOWED_SUITE_B_CIPHERS,
                    configuration.allowedSuiteBCiphers.toByteArray());
            XmlUtil.writeNextValue(out, XML_TAG_SHARED, configuration.shared);
            XmlUtil.writeNextValue(out, XML_TAG_IS_AUTO_JOIN, configuration.allowAutojoin);
            XmlUtil.writeNextValue(out, XML_TAG_PRIORITY, configuration.priority);
            XmlUtil.writeNextValue(
                    out, XML_TAG_DELETION_PRIORITY,
                    configuration.getDeletionPriority());
            XmlUtil.writeNextValue(
                    out, XML_TAG_NUM_REBOOTS_SINCE_LAST_USE,
                    configuration.numRebootsSinceLastUse);
            XmlUtil.writeNextValue(out, XML_TAG_IS_REPEATER_ENABLED,
                    configuration.isRepeaterEnabled());
            XmlUtil.writeNextValue(out, XML_TAG_ENABLE_WIFI7, configuration.isWifi7Enabled());
            writeSecurityParamsListToXml(out, configuration);
            XmlUtil.writeNextValue(out, XML_TAG_SEND_DHCP_HOSTNAME,
                    configuration.isSendDhcpHostnameEnabled());
        }

        /**
         * Write the Configuration data elements for backup from the provided Configuration to the
         * XML stream.
         * Note: This is a subset of the elements serialized for config store.
         *
         * @param out           XmlSerializer instance pointing to the XML stream.
         * @param configuration WifiConfiguration object to be serialized.
         */
        public static void writeToXmlForBackup(XmlSerializer out, WifiConfiguration configuration)
                throws XmlPullParserException, IOException {
            writeCommonElementsToXml(out, configuration, null);
            XmlUtil.writeNextValue(out, XML_TAG_METERED_OVERRIDE, configuration.meteredOverride);
        }

        /**
         * Write the Configuration data elements for config store from the provided Configuration
         * to the XML stream.
         *
         * @param out XmlSerializer instance pointing to the XML stream.
         * @param configuration WifiConfiguration object to be serialized.
         * @param encryptionUtil Instance of {@link EncryptedDataXmlUtil}.
         */
        public static void writeToXmlForConfigStore(
                XmlSerializer out, WifiConfiguration configuration,
                @Nullable WifiConfigStoreEncryptionUtil encryptionUtil)
                throws XmlPullParserException, IOException {
            writeCommonElementsToXml(out, configuration, encryptionUtil);
            XmlUtil.writeNextValue(out, XML_TAG_IS_TRUSTED, configuration.trusted);
            XmlUtil.writeNextValue(out, XML_TAG_IS_RESTRICTED, configuration.restricted);
            XmlUtil.writeNextValue(out, XML_TAG_IS_OEM_PAID, configuration.oemPaid);
            XmlUtil.writeNextValue(out, XML_TAG_IS_OEM_PRIVATE, configuration.oemPrivate);
            XmlUtil.writeNextValue(out, XML_TAG_IS_CARRIER_MERGED,
                    configuration.carrierMerged);
            XmlUtil.writeNextValue(out, XML_TAG_BSSID, configuration.BSSID);
            XmlUtil.writeNextValue(out, XML_TAG_STATUS, configuration.status);
            XmlUtil.writeNextValue(out, XML_TAG_FQDN, configuration.FQDN);
            XmlUtil.writeNextValue(
                    out, XML_TAG_PROVIDER_FRIENDLY_NAME, configuration.providerFriendlyName);
            XmlUtil.writeNextValue(
                    out, XML_TAG_LINKED_NETWORKS_LIST, configuration.linkedConfigurations);
            XmlUtil.writeNextValue(
                    out, XML_TAG_DEFAULT_GW_MAC_ADDRESS, configuration.defaultGwMacAddress);
            XmlUtil.writeNextValue(
                    out, XML_TAG_VALIDATED_INTERNET_ACCESS, configuration.validatedInternetAccess);
            XmlUtil.writeNextValue(
                    out, XML_TAG_NO_INTERNET_ACCESS_EXPECTED,
                    configuration.noInternetAccessExpected);
            XmlUtil.writeNextValue(out, XML_TAG_METERED_HINT, configuration.meteredHint);
            XmlUtil.writeNextValue(out, XML_TAG_METERED_OVERRIDE, configuration.meteredOverride);
            XmlUtil.writeNextValue(
                    out, XML_TAG_USE_EXTERNAL_SCORES, configuration.useExternalScores);
            XmlUtil.writeNextValue(out, XML_TAG_CREATOR_UID, configuration.creatorUid);
            XmlUtil.writeNextValue(out, XML_TAG_CREATOR_NAME, configuration.creatorName);
            XmlUtil.writeNextValue(out, XML_TAG_LAST_UPDATE_UID, configuration.lastUpdateUid);
            XmlUtil.writeNextValue(out, XML_TAG_LAST_UPDATE_NAME, configuration.lastUpdateName);
            XmlUtil.writeNextValue(out, XML_TAG_LAST_CONNECT_UID, configuration.lastConnectUid);
            XmlUtil.writeNextValue(
                    out, XML_TAG_IS_LEGACY_PASSPOINT_CONFIG,
                    configuration.isLegacyPasspointConfig);
            XmlUtil.writeNextValue(
                    out, XML_TAG_ROAMING_CONSORTIUM_OIS, configuration.roamingConsortiumIds);
            XmlUtil.writeNextValue(out, XML_TAG_RANDOMIZED_MAC_ADDRESS,
                    configuration.getRandomizedMacAddress().toString());
            XmlUtil.writeNextValue(out, XML_TAG_MAC_RANDOMIZATION_SETTING,
                    configuration.macRandomizationSetting);
            XmlUtil.writeNextValue(out, XML_TAG_CARRIER_ID, configuration.carrierId);
            XmlUtil.writeNextValue(out, XML_TAG_IS_MOST_RECENTLY_CONNECTED,
                    configuration.isMostRecentlyConnected);
            XmlUtil.writeNextValue(out, XML_TAG_SUBSCRIPTION_ID, configuration.subscriptionId);
            if (configuration.getSubscriptionGroup() != null) {
                XmlUtil.writeNextValue(out, XML_TAG_SUBSCRIPTION_GROUP,
                        configuration.getSubscriptionGroup().toString());
            }
            if (configuration.getBssidAllowlistInternal() != null) {
                XmlUtil.writeNextValue(out, XML_TAG_BSSID_ALLOW_LIST,
                        covertMacAddressListToStringList(configuration
                                .getBssidAllowlistInternal()));
            }
            writeDppConfigurationToXml(out, configuration, encryptionUtil);
        }

        private static List<String> covertMacAddressListToStringList(List<MacAddress> macList) {
            List<String> bssidList = new ArrayList<>();
            for (MacAddress address : macList) {
                bssidList.add(address.toString());
            }
            return bssidList;
        }

        private static List<MacAddress> covertStringListToMacAddressList(List<String> stringList) {
            List<MacAddress> macAddressList = new ArrayList<>();
            for (String address : stringList) {
                try {
                    macAddressList.add(MacAddress.fromString(address));
                } catch (Exception e) {
                    Log.e(TAG, "Invalid BSSID String: " + address);
                }
            }
            return macAddressList;
        }

        /**
         * Populate wepKeys array elements only if they were non-empty in the backup data.
         *
         * @throws XmlPullParserException if parsing errors occur.
         */
        private static void populateWepKeysFromXmlValue(Object value, String[] wepKeys)
                throws XmlPullParserException, IOException {
            String[] wepKeysInData = (String[]) value;
            if (wepKeysInData == null) {
                return;
            }
            if (wepKeysInData.length != wepKeys.length) {
                throw new XmlPullParserException(
                        "Invalid Wep Keys length: " + wepKeysInData.length);
            }
            for (int i = 0; i < wepKeys.length; i++) {
                if (wepKeysInData[i].isEmpty()) {
                    wepKeys[i] = null;
                } else {
                    wepKeys[i] = wepKeysInData[i];
                }
            }
        }

        private static String[] populateWepKeysFromXmlValue(XmlPullParser in,
                int outerTagDepth, @NonNull WifiConfigStoreEncryptionUtil encryptionUtil)
                throws XmlPullParserException, IOException {
            List<String> wepKeyList = new ArrayList<>();
            final List<EncryptedData> encryptedDataList =
                    XmlUtil.EncryptedDataXmlUtil.parseListFromXml(in, outerTagDepth);
            EncryptedData emptyData = new EncryptedData(new byte[0], new byte[0]);
            for (int i = 0; i < encryptedDataList.size(); i++) {
                if (encryptedDataList.get(i).equals(emptyData)) {
                    wepKeyList.add(null);
                    continue;
                }
                byte[] passphraseBytes = encryptionUtil.decrypt(encryptedDataList.get(i));
                if (passphraseBytes == null) {
                    Log.wtf(TAG, "Decryption of passphraseBytes failed");
                } else {
                    wepKeyList.add(new String(passphraseBytes, StandardCharsets.UTF_8));
                }
            }
            return wepKeyList.size() > 0 ? wepKeyList.toArray(
                    new String[wepKeyList.size()]) : null;
        }

        private static SecurityParams parseSecurityParamsFromXml(
                XmlPullParser in, int outerTagDepth) throws XmlPullParserException, IOException {
            SecurityParams params = null;
            while (!XmlUtil.isNextSectionEnd(in, outerTagDepth)) {
                String[] valueName = new String[1];
                Object value = XmlUtil.readCurrentValue(in, valueName);
                String tagName = valueName[0];
                if (tagName == null) {
                    throw new XmlPullParserException("Missing value name");
                }
                switch (tagName) {
                    case WifiConfigurationXmlUtil.XML_TAG_SECURITY_TYPE:
                        params = SecurityParams.createSecurityParamsBySecurityType((int) value);
                        break;
                    case WifiConfigurationXmlUtil.XML_TAG_IS_ENABLED:
                        params.setEnabled((boolean) value);
                        break;
                    case WifiConfigurationXmlUtil.XML_TAG_SAE_IS_H2E_ONLY_MODE:
                        if (null == params) {
                            throw new XmlPullParserException("Missing security type.");
                        }
                        params.enableSaeH2eOnlyMode((boolean) value);
                        break;
                    case WifiConfigurationXmlUtil.XML_TAG_SAE_IS_PK_ONLY_MODE:
                        if (null == params) {
                            throw new XmlPullParserException("Missing security type.");
                        }
                        params.enableSaePkOnlyMode((boolean) value);
                        break;
                    case WifiConfigurationXmlUtil.XML_TAG_IS_ADDED_BY_AUTO_UPGRADE:
                        if (null == params) {
                            throw new XmlPullParserException("Missing security type.");
                        }
                        params.setIsAddedByAutoUpgrade((boolean) value);
                        break;
                    case WifiConfigurationXmlUtil.XML_TAG_ALLOWED_SUITE_B_CIPHERS:
                        if (null == params) {
                            throw new XmlPullParserException("Missing security type.");
                        }
                        byte[] suiteBCiphers = (byte[]) value;
                        BitSet suiteBCiphersBitSet = BitSet.valueOf(suiteBCiphers);
                        params.enableSuiteBCiphers(
                                suiteBCiphersBitSet.get(WifiConfiguration.SuiteBCipher.ECDHE_ECDSA),
                                suiteBCiphersBitSet.get(WifiConfiguration.SuiteBCipher.ECDHE_RSA));
                        break;
                }
            }
            return params;
        }

        private static byte[] readEncrytepdBytesFromXml(
                @Nullable WifiConfigStoreEncryptionUtil encryptionUtil,
                XmlPullParser in, int outerTagDepth)
                throws XmlPullParserException, IOException {
            if (encryptionUtil == null) {
                throw new XmlPullParserException(
                        "Encrypted preSharedKey section not expected");
            }
            EncryptedData encryptedData =
                    EncryptedDataXmlUtil.parseFromXml(in, outerTagDepth + 1);
            return encryptionUtil.decrypt(encryptedData);
        }

        private static void parseSecurityParamsListFromXml(
                XmlPullParser in, int outerTagDepth,
                WifiConfiguration configuration)
                throws XmlPullParserException, IOException {
            List<SecurityParams> paramsList = new ArrayList<>();
            while (!XmlUtil.isNextSectionEnd(in, outerTagDepth)) {
                switch (in.getName()) {
                    case WifiConfigurationXmlUtil.XML_TAG_SECURITY_PARAMS:
                        SecurityParams params = parseSecurityParamsFromXml(in, outerTagDepth + 1);
                        if (params != null) {
                            paramsList.add(params);
                        }
                        break;
                }
            }
            if (!paramsList.isEmpty()) {
                configuration.setSecurityParams(paramsList);
            }
        }

        /**
         * Parses the configuration data elements from the provided XML stream to a
         * WifiConfiguration object.
         * Note: This is used for parsing both backup data and config store data. Looping through
         * the tags make it easy to add or remove elements in the future versions if needed.
         *
         * @param in XmlPullParser instance pointing to the XML stream.
         * @param outerTagDepth depth of the outer tag in the XML document.
         * @param shouldExpectEncryptedCredentials Whether to expect encrypted credentials or not.
         * @param encryptionUtil Instance of {@link EncryptedDataXmlUtil}.
         * @param fromSuggestion Is this WifiConfiguration created from a WifiNetworkSuggestion.
         * @return Pair<Config key, WifiConfiguration object> if parsing is successful,
         * null otherwise.
         */
        public static Pair<String, WifiConfiguration> parseFromXml(
                XmlPullParser in, int outerTagDepth, boolean shouldExpectEncryptedCredentials,
                @Nullable WifiConfigStoreEncryptionUtil encryptionUtil, boolean fromSuggestion)
                throws XmlPullParserException, IOException {
            WifiConfiguration configuration = new WifiConfiguration();
            String configKeyInData = null;
            boolean macRandomizationSettingExists = false;
            boolean sendDhcpHostnameExists = false;
            byte[] dppConnector = null;
            byte[] dppCSign = null;
            byte[] dppNetAccessKey = null;

            // Loop through and parse out all the elements from the stream within this section.
            while (!XmlUtil.isNextSectionEnd(in, outerTagDepth)) {
                if (in.getAttributeValue(null, "name") != null) {
                    // Value elements.
                    String[] valueName = new String[1];
                    Object value = XmlUtil.readCurrentValue(in, valueName);
                    if (valueName[0] == null) {
                        throw new XmlPullParserException("Missing value name");
                    }
                    switch (valueName[0]) {
                        case XML_TAG_CONFIG_KEY:
                            configKeyInData = (String) value;
                            break;
                        case XML_TAG_SSID:
                            configuration.SSID = (String) value;
                            break;
                        case XML_TAG_BSSID:
                            configuration.BSSID = (String) value;
                            break;
                        case XML_TAG_PRE_SHARED_KEY:
                            configuration.preSharedKey = (String) value;
                            break;
                        case XML_TAG_WEP_KEYS:
                            populateWepKeysFromXmlValue(value, configuration.wepKeys);
                            break;
                        case XML_TAG_WEP_TX_KEY_INDEX:
                            configuration.wepTxKeyIndex = (int) value;
                            break;
                        case XML_TAG_HIDDEN_SSID:
                            configuration.hiddenSSID = (boolean) value;
                            break;
                        case XML_TAG_REQUIRE_PMF:
                            configuration.requirePmf = (boolean) value;
                            break;
                        case XML_TAG_ALLOWED_KEY_MGMT:
                            byte[] allowedKeyMgmt = (byte[]) value;
                            configuration.allowedKeyManagement = BitSet.valueOf(allowedKeyMgmt);
                            break;
                        case XML_TAG_ALLOWED_PROTOCOLS:
                            byte[] allowedProtocols = (byte[]) value;
                            configuration.allowedProtocols = BitSet.valueOf(allowedProtocols);
                            break;
                        case XML_TAG_ALLOWED_AUTH_ALGOS:
                            byte[] allowedAuthAlgorithms = (byte[]) value;
                            configuration.allowedAuthAlgorithms = BitSet.valueOf(
                                    allowedAuthAlgorithms);
                            break;
                        case XML_TAG_ALLOWED_GROUP_CIPHERS:
                            byte[] allowedGroupCiphers = (byte[]) value;
                            configuration.allowedGroupCiphers = BitSet.valueOf(allowedGroupCiphers);
                            break;
                        case XML_TAG_ALLOWED_PAIRWISE_CIPHERS:
                            byte[] allowedPairwiseCiphers = (byte[]) value;
                            configuration.allowedPairwiseCiphers =
                                    BitSet.valueOf(allowedPairwiseCiphers);
                            break;
                        case XML_TAG_ALLOWED_GROUP_MGMT_CIPHERS:
                            byte[] allowedGroupMgmtCiphers = (byte[]) value;
                            configuration.allowedGroupManagementCiphers =
                                    BitSet.valueOf(allowedGroupMgmtCiphers);
                            break;
                        case XML_TAG_ALLOWED_SUITE_B_CIPHERS:
                            byte[] allowedSuiteBCiphers = (byte[]) value;
                            configuration.allowedSuiteBCiphers =
                                    BitSet.valueOf(allowedSuiteBCiphers);
                            break;
                        case XML_TAG_SHARED:
                            configuration.shared = (boolean) value;
                            break;
                        case XML_TAG_STATUS:
                            int status = (int) value;
                            // Any network which was CURRENT before reboot needs
                            // to be restored to ENABLED.
                            if (status == WifiConfiguration.Status.CURRENT) {
                                status = WifiConfiguration.Status.ENABLED;
                            }
                            configuration.status = status;
                            break;
                        case XML_TAG_FQDN:
                            configuration.FQDN = (String) value;
                            break;
                        case XML_TAG_PROVIDER_FRIENDLY_NAME:
                            configuration.providerFriendlyName = (String) value;
                            break;
                        case XML_TAG_LINKED_NETWORKS_LIST:
                            configuration.linkedConfigurations = (HashMap<String, Integer>) value;
                            break;
                        case XML_TAG_DEFAULT_GW_MAC_ADDRESS:
                            configuration.defaultGwMacAddress = (String) value;
                            break;
                        case XML_TAG_VALIDATED_INTERNET_ACCESS:
                            configuration.validatedInternetAccess = (boolean) value;
                            break;
                        case XML_TAG_NO_INTERNET_ACCESS_EXPECTED:
                            configuration.noInternetAccessExpected = (boolean) value;
                            break;
                        case XML_TAG_METERED_HINT:
                            configuration.meteredHint = (boolean) value;
                            break;
                        case XML_TAG_METERED_OVERRIDE:
                            configuration.meteredOverride = (int) value;
                            break;
                        case XML_TAG_USE_EXTERNAL_SCORES:
                            configuration.useExternalScores = (boolean) value;
                            break;
                        case XML_TAG_CREATOR_UID:
                            configuration.creatorUid = (int) value;
                            break;
                        case XML_TAG_CREATOR_NAME:
                            configuration.creatorName = (String) value;
                            break;
                        case XML_TAG_LAST_UPDATE_UID:
                            configuration.lastUpdateUid = (int) value;
                            break;
                        case XML_TAG_LAST_UPDATE_NAME:
                            configuration.lastUpdateName = (String) value;
                            break;
                        case XML_TAG_LAST_CONNECT_UID:
                            configuration.lastConnectUid = (int) value;
                            break;
                        case XML_TAG_IS_LEGACY_PASSPOINT_CONFIG:
                            configuration.isLegacyPasspointConfig = (boolean) value;
                            break;
                        case XML_TAG_ROAMING_CONSORTIUM_OIS:
                            configuration.roamingConsortiumIds = (long[]) value;
                            break;
                        case XML_TAG_RANDOMIZED_MAC_ADDRESS:
                            configuration.setRandomizedMacAddress(
                                    MacAddress.fromString((String) value));
                            break;
                        case XML_TAG_MAC_RANDOMIZATION_SETTING:
                            configuration.macRandomizationSetting = (int) value;
                            macRandomizationSettingExists = true;
                            break;
                        case XML_TAG_SEND_DHCP_HOSTNAME:
                            configuration.setSendDhcpHostnameEnabled((boolean) value);
                            sendDhcpHostnameExists = true;
                            break;
                        case XML_TAG_CARRIER_ID:
                            configuration.carrierId = (int) value;
                            break;
                        case XML_TAG_SUBSCRIPTION_ID:
                            configuration.subscriptionId = (int) value;
                            break;
                        case XML_TAG_IS_AUTO_JOIN:
                            configuration.allowAutojoin = (boolean) value;
                            break;
                        case XML_TAG_PRIORITY:
                            configuration.priority = (int) value;
                            break;
                        case XML_TAG_DELETION_PRIORITY:
                            configuration.setDeletionPriority((int) value);
                            break;
                        case XML_TAG_NUM_REBOOTS_SINCE_LAST_USE:
                            configuration.numRebootsSinceLastUse = (int) value;
                            break;
                        case XML_TAG_IS_TRUSTED:
                            configuration.trusted = (boolean) value;
                            break;
                        case XML_TAG_IS_OEM_PAID:
                            configuration.oemPaid = (boolean) value;
                            break;
                        case XML_TAG_IS_OEM_PRIVATE:
                            configuration.oemPrivate = (boolean) value;
                            break;
                        case XML_TAG_IS_MOST_RECENTLY_CONNECTED:
                            configuration.isMostRecentlyConnected = (boolean) value;
                            break;
                        case XML_TAG_IS_CARRIER_MERGED:
                            configuration.carrierMerged = (boolean) value;
                            break;
                        case XML_TAG_IS_RESTRICTED:
                            configuration.restricted = (boolean) value;
                            break;
                        case XML_TAG_SUBSCRIPTION_GROUP:
                            configuration.setSubscriptionGroup(
                                    ParcelUuid.fromString((String) value));
                            break;
                        case XML_TAG_BSSID_ALLOW_LIST:
                            configuration.setBssidAllowlist(
                                    covertStringListToMacAddressList((List<String>) value));
                            break;
                        case XML_TAG_IS_REPEATER_ENABLED:
                            configuration.setRepeaterEnabled((boolean) value);
                            break;
                        case XML_TAG_DPP_PRIVATE_EC_KEY:
                            configuration.setDppConfigurator((byte[]) value);
                            break;
                        case XML_TAG_DPP_CONNECTOR:
                            dppConnector = (byte[]) value;
                            break;
                        case XML_TAG_DPP_CSIGN_KEY:
                            dppCSign = (byte[]) value;
                            break;
                        case XML_TAG_DPP_NET_ACCESS_KEY:
                            dppNetAccessKey = (byte[]) value;
                            break;
                        case XML_TAG_ENABLE_WIFI7:
                            configuration.setWifi7Enabled((boolean) value);
                            break;
                        default:
                            Log.w(TAG, "Ignoring unknown value name found: " + valueName[0]);
                            break;
                    }
                } else {
                    String tagName = in.getName();
                    if (tagName == null) {
                        throw new XmlPullParserException("Unexpected null tag found");
                    }
                    switch (tagName) {
                        case XML_TAG_PRE_SHARED_KEY:
                            if (!shouldExpectEncryptedCredentials || encryptionUtil == null) {
                                throw new XmlPullParserException(
                                        "Encrypted preSharedKey section not expected");
                            }
                            EncryptedData encryptedData =
                                    EncryptedDataXmlUtil.parseFromXml(in, outerTagDepth + 1);
                            byte[] preSharedKeyBytes = encryptionUtil.decrypt(encryptedData);
                            if (preSharedKeyBytes == null) {
                                Log.wtf(TAG, "Decryption of preSharedKey failed");
                            } else {
                                configuration.preSharedKey = new String(preSharedKeyBytes);
                                configuration.setEncryptedPreSharedKey(
                                        encryptedData.getEncryptedData(),
                                        encryptedData.getIv());
                            }
                            break;
                        case XML_TAG_WEP_KEYS:
                            if (!shouldExpectEncryptedCredentials || encryptionUtil == null) {
                                throw new XmlPullParserException(
                                        "Encrypted wepKeys section not expected");
                            }
                            configuration.wepKeys = populateWepKeysFromXmlValue(in,
                                    outerTagDepth + 1, encryptionUtil);
                            break;
                        case XML_TAG_SECURITY_PARAMS_LIST:
                            parseSecurityParamsListFromXml(in, outerTagDepth + 1, configuration);
                            break;
                        case XML_TAG_DPP_PRIVATE_EC_KEY:
                            configuration.setDppConfigurator(readEncrytepdBytesFromXml(
                                    encryptionUtil, in, outerTagDepth));
                            break;
                        case XML_TAG_DPP_CONNECTOR:
                            dppConnector = readEncrytepdBytesFromXml(encryptionUtil, in,
                                    outerTagDepth);
                            break;
                        case XML_TAG_DPP_CSIGN_KEY:
                            dppCSign = readEncrytepdBytesFromXml(encryptionUtil, in,
                                    outerTagDepth);
                            break;
                        case XML_TAG_DPP_NET_ACCESS_KEY:
                            dppNetAccessKey = readEncrytepdBytesFromXml(encryptionUtil, in,
                                    outerTagDepth);
                            break;
                        default:
                            Log.w(TAG, "Ignoring unknown tag found: " + tagName);
                            break;
                    }
                }
            }
            if (!macRandomizationSettingExists) {
                configuration.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_NONE;
            }
            if (configuration.macRandomizationSetting
                    == WifiConfiguration.RANDOMIZATION_PERSISTENT && !fromSuggestion) {
                configuration.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_AUTO;
            }
            if (!sendDhcpHostnameExists) {
                // Update legacy configs to send the DHCP hostname for secure networks only.
                configuration.setSendDhcpHostnameEnabled(
                        !configuration.isSecurityType(WifiConfiguration.SECURITY_TYPE_OPEN)
                        && !configuration.isSecurityType(WifiConfiguration.SECURITY_TYPE_OWE));
            }
            configuration.convertLegacyFieldsToSecurityParamsIfNeeded();
            configuration.setDppConnectionKeys(dppConnector, dppCSign, dppNetAccessKey);
            return Pair.create(configKeyInData, configuration);
        }
    }

    /**
     * Utility class to serialize and deseriaize {@link IpConfiguration} object to XML & vice versa.
     * This is used by both {@link com.android.server.wifi.WifiConfigStore} &
     * {@link com.android.server.wifi.WifiBackupRestore} modules.
     */
    public static class IpConfigurationXmlUtil {

        /**
         * List of XML tags corresponding to IpConfiguration object elements.
         */
        public static final String XML_TAG_IP_ASSIGNMENT = "IpAssignment";
        public static final String XML_TAG_LINK_ADDRESS = "LinkAddress";
        public static final String XML_TAG_LINK_PREFIX_LENGTH = "LinkPrefixLength";
        public static final String XML_TAG_GATEWAY_ADDRESS = "GatewayAddress";
        public static final String XML_TAG_DNS_SERVER_ADDRESSES = "DNSServers";
        public static final String XML_TAG_PROXY_SETTINGS = "ProxySettings";
        public static final String XML_TAG_PROXY_HOST = "ProxyHost";
        public static final String XML_TAG_PROXY_PORT = "ProxyPort";
        public static final String XML_TAG_PROXY_PAC_FILE = "ProxyPac";
        public static final String XML_TAG_PROXY_EXCLUSION_LIST = "ProxyExclusionList";

        private static List<String> parseProxyExclusionListString(
                @Nullable String exclusionListString) {
            if (exclusionListString == null) {
                return Collections.emptyList();
            } else {
                return Arrays.asList(exclusionListString.toLowerCase(Locale.ROOT).split(","));
            }
        }

        private static String generateProxyExclusionListString(@NonNull String[] exclusionList) {
            return TextUtils.join(",", exclusionList);
        }

        /**
         * Write the static IP configuration data elements to XML stream.
         */
        private static void writeStaticIpConfigurationToXml(
                XmlSerializer out, StaticIpConfiguration staticIpConfiguration)
                throws XmlPullParserException, IOException {
            if (staticIpConfiguration.getIpAddress() != null) {
                XmlUtil.writeNextValue(
                        out, XML_TAG_LINK_ADDRESS,
                        staticIpConfiguration.getIpAddress().getAddress().getHostAddress());
                XmlUtil.writeNextValue(
                        out, XML_TAG_LINK_PREFIX_LENGTH,
                        staticIpConfiguration.getIpAddress().getPrefixLength());
            } else {
                XmlUtil.writeNextValue(
                        out, XML_TAG_LINK_ADDRESS, null);
                XmlUtil.writeNextValue(
                        out, XML_TAG_LINK_PREFIX_LENGTH, null);
            }
            if (staticIpConfiguration.getGateway() != null) {
                XmlUtil.writeNextValue(
                        out, XML_TAG_GATEWAY_ADDRESS,
                        staticIpConfiguration.getGateway().getHostAddress());
            } else {
                XmlUtil.writeNextValue(
                        out, XML_TAG_GATEWAY_ADDRESS, null);

            }
            // Create a string array of DNS server addresses
            String[] dnsServers = new String[staticIpConfiguration.getDnsServers().size()];
            int dnsServerIdx = 0;
            for (InetAddress inetAddr : staticIpConfiguration.getDnsServers()) {
                dnsServers[dnsServerIdx++] = inetAddr.getHostAddress();
            }
            XmlUtil.writeNextValue(
                    out, XML_TAG_DNS_SERVER_ADDRESSES, dnsServers);
        }

        /**
         * Write the IP configuration data elements from the provided Configuration to the XML
         * stream.
         *
         * @param out             XmlSerializer instance pointing to the XML stream.
         * @param ipConfiguration IpConfiguration object to be serialized.
         */
        public static void writeToXml(XmlSerializer out, IpConfiguration ipConfiguration)
                throws XmlPullParserException, IOException {
            // Write IP assignment settings
            XmlUtil.writeNextValue(out, XML_TAG_IP_ASSIGNMENT,
                    ipConfiguration.getIpAssignment().toString());
            switch (ipConfiguration.getIpAssignment()) {
                case STATIC:
                    writeStaticIpConfigurationToXml(
                            out, ipConfiguration.getStaticIpConfiguration());
                    break;
                case DHCP:
                case UNASSIGNED:
                    break;
                default:
                    Log.w(TAG, "Ignoring unknown ip assignment type: "
                            + ipConfiguration.getIpAssignment());
                    break;
            }

            // Write proxy settings
            XmlUtil.writeNextValue(
                    out, XML_TAG_PROXY_SETTINGS,
                    ipConfiguration.getProxySettings().toString());
            switch (ipConfiguration.getProxySettings()) {
                case STATIC:
                    XmlUtil.writeNextValue(
                            out, XML_TAG_PROXY_HOST,
                            ipConfiguration.getHttpProxy().getHost());
                    XmlUtil.writeNextValue(
                            out, XML_TAG_PROXY_PORT,
                            ipConfiguration.getHttpProxy().getPort());
                    XmlUtil.writeNextValue(
                            out, XML_TAG_PROXY_EXCLUSION_LIST,
                            generateProxyExclusionListString(
                                    ipConfiguration.getHttpProxy().getExclusionList()));
                    break;
                case PAC:
                    XmlUtil.writeNextValue(
                            out, XML_TAG_PROXY_PAC_FILE,
                            ipConfiguration.getHttpProxy().getPacFileUrl().toString());
                    break;
                case NONE:
                case UNASSIGNED:
                    break;
                default:
                    Log.w(TAG, "Ignoring unknown proxy settings type: "
                            + ipConfiguration.getProxySettings());
                    break;
            }
        }

        /**
         * Parse out the static IP configuration from the XML stream.
         */
        private static StaticIpConfiguration parseStaticIpConfigurationFromXml(XmlPullParser in)
                throws XmlPullParserException, IOException {
            StaticIpConfiguration.Builder builder = new StaticIpConfiguration.Builder();

            String linkAddressString =
                    (String) XmlUtil.readNextValueWithName(in, XML_TAG_LINK_ADDRESS);
            Integer linkPrefixLength =
                    (Integer) XmlUtil.readNextValueWithName(in, XML_TAG_LINK_PREFIX_LENGTH);
            if (linkAddressString != null && linkPrefixLength != null) {
                LinkAddress linkAddress = new LinkAddress(
                        InetAddresses.parseNumericAddress(linkAddressString),
                        linkPrefixLength);
                if (linkAddress.getAddress() instanceof Inet4Address) {
                    builder.setIpAddress(linkAddress);
                } else {
                    Log.w(TAG, "Non-IPv4 address: " + linkAddress);
                }
            }
            String gatewayAddressString =
                    (String) XmlUtil.readNextValueWithName(in, XML_TAG_GATEWAY_ADDRESS);
            if (gatewayAddressString != null) {
                InetAddress gateway =
                        InetAddresses.parseNumericAddress(gatewayAddressString);
                RouteInfo route = new RouteInfo(null, gateway, null, RouteInfo.RTN_UNICAST);
                if (route.isDefaultRoute()
                        && route.getDestination().getAddress() instanceof Inet4Address) {
                    builder.setGateway(gateway);
                } else {
                    Log.w(TAG, "Non-IPv4 default route: " + route);
                }
            }
            String[] dnsServerAddressesString =
                    (String[]) XmlUtil.readNextValueWithName(in, XML_TAG_DNS_SERVER_ADDRESSES);
            if (dnsServerAddressesString != null) {
                List<InetAddress> dnsServerAddresses = new ArrayList<>();
                for (String dnsServerAddressString : dnsServerAddressesString) {
                    InetAddress dnsServerAddress =
                            InetAddresses.parseNumericAddress(dnsServerAddressString);
                    dnsServerAddresses.add(dnsServerAddress);
                }
                builder.setDnsServers(dnsServerAddresses);
            }
            return builder.build();
        }

        /**
         * Parses the IP configuration data elements from the provided XML stream to an
         * IpConfiguration object.
         *
         * @param in            XmlPullParser instance pointing to the XML stream.
         * @param outerTagDepth depth of the outer tag in the XML document.
         * @return IpConfiguration object if parsing is successful, null otherwise.
         */
        public static IpConfiguration parseFromXml(XmlPullParser in, int outerTagDepth)
                throws XmlPullParserException, IOException {
            IpConfiguration ipConfiguration = new IpConfiguration();

            // Parse out the IP assignment info first.
            String ipAssignmentString =
                    (String) XmlUtil.readNextValueWithName(in, XML_TAG_IP_ASSIGNMENT);
            IpAssignment ipAssignment = IpAssignment.valueOf(ipAssignmentString);
            ipConfiguration.setIpAssignment(ipAssignment);
            switch (ipAssignment) {
                case STATIC:
                    ipConfiguration.setStaticIpConfiguration(parseStaticIpConfigurationFromXml(in));
                    break;
                case DHCP:
                case UNASSIGNED:
                    break;
                default:
                    Log.w(TAG, "Ignoring unknown ip assignment type: " + ipAssignment);
                    break;
            }

            // Parse out the proxy settings next.
            String proxySettingsString =
                    (String) XmlUtil.readNextValueWithName(in, XML_TAG_PROXY_SETTINGS);
            ProxySettings proxySettings = ProxySettings.valueOf(proxySettingsString);
            ipConfiguration.setProxySettings(proxySettings);
            switch (proxySettings) {
                case STATIC:
                    String proxyHost =
                            (String) XmlUtil.readNextValueWithName(in, XML_TAG_PROXY_HOST);
                    int proxyPort =
                            (int) XmlUtil.readNextValueWithName(in, XML_TAG_PROXY_PORT);
                    String proxyExclusionList =
                            (String) XmlUtil.readNextValueWithName(
                                    in, XML_TAG_PROXY_EXCLUSION_LIST);
                    ipConfiguration.setHttpProxy(
                            ProxyInfo.buildDirectProxy(
                                    proxyHost, proxyPort,
                                    parseProxyExclusionListString(proxyExclusionList)));
                    break;
                case PAC:
                    String proxyPacFile =
                            (String) XmlUtil.readNextValueWithName(in, XML_TAG_PROXY_PAC_FILE);
                    ipConfiguration.setHttpProxy(
                            ProxyInfo.buildPacProxy(Uri.parse(proxyPacFile)));
                    break;
                case NONE:
                case UNASSIGNED:
                    break;
                default:
                    Log.w(TAG, "Ignoring unknown proxy settings type: " + proxySettings);
                    break;
            }
            return ipConfiguration;
        }
    }

    /**
     * Utility class to serialize and deserialize {@link NetworkSelectionStatus} object to XML &
     * vice versa. This is used by {@link com.android.server.wifi.WifiConfigStore} module.
     */
    public static class NetworkSelectionStatusXmlUtil {

        /**
         * List of XML tags corresponding to NetworkSelectionStatus object elements.
         */
        public static final String XML_TAG_SELECTION_STATUS = "SelectionStatus";
        public static final String XML_TAG_DISABLE_REASON = "DisableReason";
        public static final String XML_TAG_CONNECT_CHOICE = "ConnectChoice";
        public static final String XML_TAG_HAS_EVER_CONNECTED = "HasEverConnected";
        public static final String XML_TAG_IS_CAPTIVE_PORTAL_NEVER_DETECTED =
                "CaptivePortalNeverDetected";
        public static final String XML_TAG_HAS_EVER_VALIDATED_INTERNET_ACCESS =
                "HasEverValidatedInternetAccess";
        public static final String XML_TAG_CONNECT_CHOICE_RSSI = "ConnectChoiceRssi";

        /**
         * Write the NetworkSelectionStatus data elements from the provided status to the XML
         * stream.
         *
         * @param out             XmlSerializer instance pointing to the XML stream.
         * @param selectionStatus NetworkSelectionStatus object to be serialized.
         */
        public static void writeToXml(XmlSerializer out, NetworkSelectionStatus selectionStatus)
                throws XmlPullParserException, IOException {
            XmlUtil.writeNextValue(
                    out, XML_TAG_SELECTION_STATUS, selectionStatus.getNetworkStatusString());
            XmlUtil.writeNextValue(
                    out, XML_TAG_DISABLE_REASON,
                    selectionStatus.getNetworkSelectionDisableReasonString());
            XmlUtil.writeNextValue(out, XML_TAG_CONNECT_CHOICE, selectionStatus.getConnectChoice());
            XmlUtil.writeNextValue(out, XML_TAG_CONNECT_CHOICE_RSSI,
                    selectionStatus.getConnectChoiceRssi());
            XmlUtil.writeNextValue(
                    out, XML_TAG_HAS_EVER_CONNECTED, selectionStatus.hasEverConnected());
            XmlUtil.writeNextValue(out, XML_TAG_IS_CAPTIVE_PORTAL_NEVER_DETECTED,
                    selectionStatus.hasNeverDetectedCaptivePortal());
            XmlUtil.writeNextValue(out, XML_TAG_HAS_EVER_VALIDATED_INTERNET_ACCESS,
                    selectionStatus.hasEverValidatedInternetAccess());
        }

        /**
         * Parses the NetworkSelectionStatus data elements from the provided XML stream to a
         * NetworkSelectionStatus object.
         *
         * @param in            XmlPullParser instance pointing to the XML stream.
         * @param outerTagDepth depth of the outer tag in the XML document.
         * @return NetworkSelectionStatus object if parsing is successful, null otherwise.
         */
        public static NetworkSelectionStatus parseFromXml(XmlPullParser in, int outerTagDepth)
                throws XmlPullParserException, IOException {
            NetworkSelectionStatus selectionStatus = new NetworkSelectionStatus();
            String statusString = "";
            String disableReasonString = "";
            // Initialize hasNeverDetectedCaptivePortal to "false" for upgrading legacy configs
            // which do not have the XML_TAG_IS_CAPTIVE_PORTAL_NEVER_DETECTED tag.
            selectionStatus.setHasNeverDetectedCaptivePortal(false);

            // Initialize hasEverValidatedInternetAccess to "true" for existing configs which don't
            // have any value stored.
            selectionStatus.setHasEverValidatedInternetAccess(true);

            // Loop through and parse out all the elements from the stream within this section.
            while (!XmlUtil.isNextSectionEnd(in, outerTagDepth)) {
                String[] valueName = new String[1];
                Object value = XmlUtil.readCurrentValue(in, valueName);
                if (valueName[0] == null) {
                    throw new XmlPullParserException("Missing value name");
                }
                switch (valueName[0]) {
                    case XML_TAG_SELECTION_STATUS:
                        statusString = (String) value;
                        break;
                    case XML_TAG_DISABLE_REASON:
                        disableReasonString = (String) value;
                        break;
                    case XML_TAG_CONNECT_CHOICE:
                        selectionStatus.setConnectChoice((String) value);
                        break;
                    case XML_TAG_CONNECT_CHOICE_RSSI:
                        selectionStatus.setConnectChoiceRssi((int) value);
                        break;
                    case XML_TAG_HAS_EVER_CONNECTED:
                        selectionStatus.setHasEverConnected((boolean) value);
                        break;
                    case XML_TAG_IS_CAPTIVE_PORTAL_NEVER_DETECTED:
                        selectionStatus.setHasNeverDetectedCaptivePortal((boolean) value);
                        break;
                    case XML_TAG_HAS_EVER_VALIDATED_INTERNET_ACCESS:
                        selectionStatus.setHasEverValidatedInternetAccess((boolean) value);
                        break;
                    default:
                        Log.w(TAG, "Ignoring unknown value name found: " + valueName[0]);
                        break;
                }
            }
            // Now figure out the network selection status codes from |selectionStatusString| &
            // |disableReasonString|.
            int status =
                    Arrays.asList(NetworkSelectionStatus.QUALITY_NETWORK_SELECTION_STATUS)
                            .indexOf(statusString);
            int disableReason =
                    NetworkSelectionStatus.getDisableReasonByString(disableReasonString);

            // If either of the above codes are invalid or if the network was temporarily disabled
            // (blacklisted), restore the status as enabled. We don't want to persist blacklists
            // across reboots.
            if (status == -1 || disableReason == -1 ||
                    status == NetworkSelectionStatus.NETWORK_SELECTION_TEMPORARY_DISABLED) {
                status = NetworkSelectionStatus.NETWORK_SELECTION_ENABLED;
                disableReason = NetworkSelectionStatus.DISABLED_NONE;
            }
            selectionStatus.setNetworkSelectionStatus(status);
            selectionStatus.setNetworkSelectionDisableReason(disableReason);
            if (status == NetworkSelectionStatus.NETWORK_SELECTION_PERMANENTLY_DISABLED) {
                // Make the counter non-zero so that logging code works properly
                selectionStatus.setDisableReasonCounter(disableReason, 1);
            }
            return selectionStatus;
        }
    }

    /**
     * Utility class to serialize and deseriaize {@link WifiEnterpriseConfig} object to XML &
     * vice versa. This is used by {@link com.android.server.wifi.WifiConfigStore} module.
     */
    public static class WifiEnterpriseConfigXmlUtil {

        /**
         * List of XML tags corresponding to WifiEnterpriseConfig object elements.
         */
        public static final String XML_TAG_IDENTITY = "Identity";
        public static final String XML_TAG_ANON_IDENTITY = "AnonIdentity";
        public static final String XML_TAG_PASSWORD = "Password";
        public static final String XML_TAG_CLIENT_CERT = "ClientCert";
        public static final String XML_TAG_CA_CERT = "CaCert";
        public static final String XML_TAG_SUBJECT_MATCH = "SubjectMatch";
        public static final String XML_TAG_ENGINE = "Engine";
        public static final String XML_TAG_ENGINE_ID = "EngineId";
        public static final String XML_TAG_PRIVATE_KEY_ID = "PrivateKeyId";
        public static final String XML_TAG_ALT_SUBJECT_MATCH = "AltSubjectMatch";
        public static final String XML_TAG_DOM_SUFFIX_MATCH = "DomSuffixMatch";
        public static final String XML_TAG_CA_PATH = "CaPath";
        public static final String XML_TAG_EAP_METHOD = "EapMethod";
        public static final String XML_TAG_PHASE2_METHOD = "Phase2Method";
        public static final String XML_TAG_PLMN = "PLMN";
        public static final String XML_TAG_REALM = "Realm";
        public static final String XML_TAG_OCSP = "Ocsp";
        public static final String XML_TAG_WAPI_CERT_SUITE = "WapiCertSuite";
        public static final String XML_TAG_APP_INSTALLED_ROOT_CA_CERT = "AppInstalledRootCaCert";
        public static final String XML_TAG_APP_INSTALLED_PRIVATE_KEY = "AppInstalledPrivateKey";
        public static final String XML_TAG_KEYCHAIN_KEY_ALIAS = "KeyChainAlias";
        public static final String XML_TAG_DECORATED_IDENTITY_PREFIX = "DecoratedIdentityPrefix";
        public static final String XML_TAG_TRUST_ON_FIRST_USE = "TrustOnFirstUse";
        public static final String XML_TAG_USER_APPROVE_NO_CA_CERT = "UserApproveNoCaCert";
        public static final String XML_TAG_MINIMUM_TLS_VERSION = "MinimumTlsVersion";
        public static final String XML_TAG_TOFU_DIALOG_STATE = "TofuDialogState";
        public static final String XML_TAG_TOFU_CONNECTION_STATE = "TofuConnectionState";

        /**
         * Write password key to the XML stream.
         *
         * If encryptionUtil is null or if encryption fails for some reason, the password is stored
         * in plaintext, else the encrypted psk is stored.
         */
        private static void writePasswordToXml(
                XmlSerializer out, String password,
                @Nullable WifiConfigStoreEncryptionUtil encryptionUtil)
                throws XmlPullParserException, IOException {
            EncryptedData encryptedData = null;
            if (encryptionUtil != null) {
                if (password != null) {
                    encryptedData = encryptionUtil.encrypt(password.getBytes());
                    if (encryptedData == null) {
                        // We silently fail encryption failures!
                        Log.wtf(TAG, "Encryption of password failed");
                    }
                }
            }
            if (encryptedData != null) {
                XmlUtil.writeNextSectionStart(out, XML_TAG_PASSWORD);
                EncryptedDataXmlUtil.writeToXml(out, encryptedData);
                XmlUtil.writeNextSectionEnd(out, XML_TAG_PASSWORD);
            } else {
                XmlUtil.writeNextValue(out, XML_TAG_PASSWORD, password);
            }
        }

        /**
         * Write the WifiEnterpriseConfig data elements from the provided config to the XML
         * stream.
         *
         * @param out XmlSerializer instance pointing to the XML stream.
         * @param enterpriseConfig WifiEnterpriseConfig object to be serialized.
         * @param encryptionUtil Instance of {@link EncryptedDataXmlUtil}.
         */
        public static void writeToXml(XmlSerializer out, WifiEnterpriseConfig enterpriseConfig,
                @Nullable WifiConfigStoreEncryptionUtil encryptionUtil)
                throws XmlPullParserException, IOException {
            XmlUtil.writeNextValue(out, XML_TAG_IDENTITY,
                    enterpriseConfig.getFieldValue(WifiEnterpriseConfig.IDENTITY_KEY));
            XmlUtil.writeNextValue(out, XML_TAG_ANON_IDENTITY,
                    enterpriseConfig.getFieldValue(WifiEnterpriseConfig.ANON_IDENTITY_KEY));
            writePasswordToXml(
                    out, enterpriseConfig.getFieldValue(WifiEnterpriseConfig.PASSWORD_KEY),
                    encryptionUtil);
            XmlUtil.writeNextValue(out, XML_TAG_CLIENT_CERT,
                    enterpriseConfig.getFieldValue(WifiEnterpriseConfig.CLIENT_CERT_KEY));
            XmlUtil.writeNextValue(out, XML_TAG_CA_CERT,
                    enterpriseConfig.getFieldValue(WifiEnterpriseConfig.CA_CERT_KEY));
            XmlUtil.writeNextValue(out, XML_TAG_SUBJECT_MATCH,
                    enterpriseConfig.getFieldValue(WifiEnterpriseConfig.SUBJECT_MATCH_KEY));
            XmlUtil.writeNextValue(out, XML_TAG_ENGINE,
                    enterpriseConfig.getFieldValue(WifiEnterpriseConfig.ENGINE_KEY));
            XmlUtil.writeNextValue(out, XML_TAG_ENGINE_ID,
                    enterpriseConfig.getFieldValue(WifiEnterpriseConfig.ENGINE_ID_KEY));
            XmlUtil.writeNextValue(out, XML_TAG_PRIVATE_KEY_ID,
                    enterpriseConfig.getFieldValue(WifiEnterpriseConfig.PRIVATE_KEY_ID_KEY));
            XmlUtil.writeNextValue(out, XML_TAG_ALT_SUBJECT_MATCH,
                    enterpriseConfig.getFieldValue(WifiEnterpriseConfig.ALTSUBJECT_MATCH_KEY));
            XmlUtil.writeNextValue(out, XML_TAG_DOM_SUFFIX_MATCH,
                    enterpriseConfig.getFieldValue(WifiEnterpriseConfig.DOM_SUFFIX_MATCH_KEY));
            XmlUtil.writeNextValue(out, XML_TAG_CA_PATH,
                    enterpriseConfig.getFieldValue(WifiEnterpriseConfig.CA_PATH_KEY));
            XmlUtil.writeNextValue(out, XML_TAG_EAP_METHOD, enterpriseConfig.getEapMethod());
            XmlUtil.writeNextValue(out, XML_TAG_PHASE2_METHOD, enterpriseConfig.getPhase2Method());
            XmlUtil.writeNextValue(out, XML_TAG_PLMN, enterpriseConfig.getPlmn());
            XmlUtil.writeNextValue(out, XML_TAG_REALM, enterpriseConfig.getRealm());
            XmlUtil.writeNextValue(out, XML_TAG_OCSP, enterpriseConfig.getOcsp());
            XmlUtil.writeNextValue(out,
                    XML_TAG_WAPI_CERT_SUITE, enterpriseConfig.getWapiCertSuite());
            XmlUtil.writeNextValue(out, XML_TAG_APP_INSTALLED_ROOT_CA_CERT,
                    enterpriseConfig.isAppInstalledCaCert());
            XmlUtil.writeNextValue(out, XML_TAG_APP_INSTALLED_PRIVATE_KEY,
                    enterpriseConfig.isAppInstalledDeviceKeyAndCert());
            XmlUtil.writeNextValue(out, XML_TAG_KEYCHAIN_KEY_ALIAS,
                    enterpriseConfig.getClientKeyPairAliasInternal());
            if (SdkLevel.isAtLeastS()) {
                XmlUtil.writeNextValue(out, XML_TAG_DECORATED_IDENTITY_PREFIX,
                        enterpriseConfig.getDecoratedIdentityPrefix());
            }
            XmlUtil.writeNextValue(out, XML_TAG_TRUST_ON_FIRST_USE,
                    enterpriseConfig.isTrustOnFirstUseEnabled());
            XmlUtil.writeNextValue(out, XML_TAG_USER_APPROVE_NO_CA_CERT,
                    enterpriseConfig.isUserApproveNoCaCert());
            XmlUtil.writeNextValue(out, XML_TAG_MINIMUM_TLS_VERSION,
                    enterpriseConfig.getMinimumTlsVersion());
            XmlUtil.writeNextValue(out, XML_TAG_TOFU_DIALOG_STATE,
                    enterpriseConfig.getTofuDialogState());
            XmlUtil.writeNextValue(out, XML_TAG_TOFU_CONNECTION_STATE,
                    enterpriseConfig.getTofuConnectionState());
        }

        /**
         * Parses the data elements from the provided XML stream to a WifiEnterpriseConfig object.
         *
         * @param in XmlPullParser instance pointing to the XML stream.
         * @param outerTagDepth depth of the outer tag in the XML document.
         * @param shouldExpectEncryptedCredentials Whether to expect encrypted credentials or not.
         * @param encryptionUtil Instance of {@link EncryptedDataXmlUtil}.
         * @return WifiEnterpriseConfig object if parsing is successful, null otherwise.
         */
        public static WifiEnterpriseConfig parseFromXml(XmlPullParser in, int outerTagDepth,
                boolean shouldExpectEncryptedCredentials,
                @Nullable WifiConfigStoreEncryptionUtil encryptionUtil)
                throws XmlPullParserException, IOException {
            WifiEnterpriseConfig enterpriseConfig = new WifiEnterpriseConfig();

            // Loop through and parse out all the elements from the stream within this section.
            while (XmlUtilHelper.nextElementWithin(in, outerTagDepth)) {
                if (in.getAttributeValue(null, "name") != null) {
                    // Value elements.
                    String[] valueName = new String[1];
                    Object value = XmlUtil.readCurrentValue(in, valueName);
                    if (valueName[0] == null) {
                        throw new XmlPullParserException("Missing value name");
                    }
                    switch (valueName[0]) {
                        case XML_TAG_IDENTITY:
                            enterpriseConfig.setFieldValue(
                                    WifiEnterpriseConfig.IDENTITY_KEY, (String) value);
                            break;
                        case XML_TAG_ANON_IDENTITY:
                            enterpriseConfig.setFieldValue(
                                    WifiEnterpriseConfig.ANON_IDENTITY_KEY, (String) value);
                            break;
                        case XML_TAG_PASSWORD:
                            enterpriseConfig.setFieldValue(
                                    WifiEnterpriseConfig.PASSWORD_KEY, (String) value);
                            if (shouldExpectEncryptedCredentials
                                    && !TextUtils.isEmpty(enterpriseConfig.getFieldValue(
                                    WifiEnterpriseConfig.PASSWORD_KEY))) {
                                // Indicates that encryption of password failed when it was last
                                // written.
                                Log.e(TAG, "password value not expected");
                            }
                            break;
                        case XML_TAG_CLIENT_CERT:
                            enterpriseConfig.setFieldValue(
                                    WifiEnterpriseConfig.CLIENT_CERT_KEY, (String) value);
                            break;
                        case XML_TAG_CA_CERT:
                            enterpriseConfig.setFieldValue(
                                    WifiEnterpriseConfig.CA_CERT_KEY, (String) value);
                            break;
                        case XML_TAG_SUBJECT_MATCH:
                            enterpriseConfig.setFieldValue(
                                    WifiEnterpriseConfig.SUBJECT_MATCH_KEY, (String) value);
                            break;
                        case XML_TAG_ENGINE:
                            enterpriseConfig.setFieldValue(
                                    WifiEnterpriseConfig.ENGINE_KEY, (String) value);
                            break;
                        case XML_TAG_ENGINE_ID:
                            enterpriseConfig.setFieldValue(
                                    WifiEnterpriseConfig.ENGINE_ID_KEY, (String) value);
                            break;
                        case XML_TAG_PRIVATE_KEY_ID:
                            enterpriseConfig.setFieldValue(
                                    WifiEnterpriseConfig.PRIVATE_KEY_ID_KEY, (String) value);
                            break;
                        case XML_TAG_ALT_SUBJECT_MATCH:
                            enterpriseConfig.setFieldValue(
                                    WifiEnterpriseConfig.ALTSUBJECT_MATCH_KEY, (String) value);
                            break;
                        case XML_TAG_DOM_SUFFIX_MATCH:
                            enterpriseConfig.setFieldValue(
                                    WifiEnterpriseConfig.DOM_SUFFIX_MATCH_KEY, (String) value);
                            break;
                        case XML_TAG_CA_PATH:
                            enterpriseConfig.setFieldValue(
                                    WifiEnterpriseConfig.CA_PATH_KEY, (String) value);
                            break;
                        case XML_TAG_OCSP:
                            enterpriseConfig.setOcsp((int) value);
                            break;
                        case XML_TAG_EAP_METHOD:
                            enterpriseConfig.setEapMethod((int) value);
                            break;
                        case XML_TAG_PHASE2_METHOD:
                            enterpriseConfig.setPhase2Method((int) value);
                            break;
                        case XML_TAG_PLMN:
                            enterpriseConfig.setPlmn((String) value);
                            break;
                        case XML_TAG_REALM:
                            enterpriseConfig.setRealm((String) value);
                            break;
                        case XML_TAG_WAPI_CERT_SUITE:
                            enterpriseConfig.setWapiCertSuite((String) value);
                            break;
                        case XML_TAG_APP_INSTALLED_ROOT_CA_CERT:
                            enterpriseConfig.initIsAppInstalledCaCert((boolean) value);
                            break;
                        case XML_TAG_APP_INSTALLED_PRIVATE_KEY:
                            enterpriseConfig.initIsAppInstalledDeviceKeyAndCert((boolean) value);
                            break;
                        case XML_TAG_KEYCHAIN_KEY_ALIAS:
                            if (SdkLevel.isAtLeastS()) {
                                enterpriseConfig.setClientKeyPairAlias((String) value);
                            }
                            break;
                        case XML_TAG_DECORATED_IDENTITY_PREFIX:
                            if (SdkLevel.isAtLeastS()) {
                                enterpriseConfig.setDecoratedIdentityPrefix((String) value);
                            }
                            break;
                        case XML_TAG_TRUST_ON_FIRST_USE:
                            enterpriseConfig.enableTrustOnFirstUse((boolean) value);
                            break;
                        case XML_TAG_USER_APPROVE_NO_CA_CERT:
                            enterpriseConfig.setUserApproveNoCaCert((boolean) value);
                            break;
                        case XML_TAG_MINIMUM_TLS_VERSION:
                            enterpriseConfig.setMinimumTlsVersion((int) value);
                            break;
                        case XML_TAG_TOFU_DIALOG_STATE:
                            enterpriseConfig.setTofuDialogState((int) value);
                            break;
                        case XML_TAG_TOFU_CONNECTION_STATE:
                            enterpriseConfig.setTofuConnectionState((int) value);
                            break;
                        default:
                            Log.w(TAG, "Ignoring unknown value name found: " + valueName[0]);
                            break;
                    }
                } else {
                    String tagName = in.getName();
                    if (tagName == null) {
                        throw new XmlPullParserException("Unexpected null tag found");
                    }
                    switch (tagName) {
                        case XML_TAG_PASSWORD:
                            if (!shouldExpectEncryptedCredentials || encryptionUtil == null) {
                                throw new XmlPullParserException(
                                        "encrypted password section not expected");
                            }
                            EncryptedData encryptedData =
                                    EncryptedDataXmlUtil.parseFromXml(in, outerTagDepth + 1);
                            byte[] passwordBytes = encryptionUtil.decrypt(encryptedData);
                            if (passwordBytes == null) {
                                Log.wtf(TAG, "Decryption of password failed");
                            } else {
                                enterpriseConfig.setFieldValue(
                                        WifiEnterpriseConfig.PASSWORD_KEY,
                                        new String(passwordBytes));
                            }
                            break;
                        default:
                            Log.w(TAG, "Ignoring unknown tag name found: " + tagName);
                            break;
                    }
                }
            }
            return enterpriseConfig;
        }
    }

    /**
     * Utility class to serialize and deseriaize {@link EncryptedData} object to XML &
     * vice versa. This is used by {@link com.android.server.wifi.WifiConfigStore} module.
     */
    public static class EncryptedDataXmlUtil {
        /**
         * List of XML tags corresponding to EncryptedData object elements.
         */
        private static final String XML_TAG_ENCRYPTED_DATA = "EncryptedData";
        private static final String XML_TAG_IV = "IV";

        /**
         * Write the NetworkSelectionStatus data elements from the provided status to the XML
         * stream.
         *
         * @param out           XmlSerializer instance pointing to the XML stream.
         * @param encryptedData EncryptedData object to be serialized.
         */
        public static void writeToXml(XmlSerializer out, EncryptedData encryptedData)
                throws XmlPullParserException, IOException {
            XmlUtil.writeNextValue(
                    out, XML_TAG_ENCRYPTED_DATA, encryptedData.getEncryptedData());
            XmlUtil.writeNextValue(out, XML_TAG_IV, encryptedData.getIv());
        }

        /**
         * Parses the EncryptedData data elements from the provided XML stream to a
         * EncryptedData object.
         *
         * @param in            XmlPullParser instance pointing to the XML stream.
         * @param outerTagDepth depth of the outer tag in the XML document.
         * @return EncryptedData object if parsing is successful, null otherwise.
         */
        public static EncryptedData parseFromXml(XmlPullParser in, int outerTagDepth)
                throws XmlPullParserException, IOException {
            byte[] encryptedData = null;
            byte[] iv = null;

            // Loop through and parse out all the elements from the stream within this section.
            while (!XmlUtil.isNextSectionEnd(in, outerTagDepth)) {
                String[] valueName = new String[1];
                Object value = XmlUtil.readCurrentValue(in, valueName);
                if (valueName[0] == null) {
                    throw new XmlPullParserException("Missing value name");
                }
                switch (valueName[0]) {
                    case XML_TAG_ENCRYPTED_DATA:
                        encryptedData = (byte[]) value;
                        break;
                    case XML_TAG_IV:
                        iv = (byte[]) value;
                        break;
                    default:
                        Log.e(TAG, "Unknown value name found: " + valueName[0]);
                        break;
                }
            }
            return new EncryptedData(encryptedData, iv);
        }

        /**
         * Parses the EncryptedData data elements arrays from the provided XML stream to a list of
         * EncryptedData object.
         *
         * @param in            XmlPullParser instance pointing to the XML stream.
         * @param outerTagDepth depth of the outer tag in the XML document.
         * @return List of encryptedData object if parsing is successful, empty otherwise.
         */
        public static @NonNull List<EncryptedData> parseListFromXml(XmlPullParser in,
                int outerTagDepth) throws XmlPullParserException, IOException {
            List<EncryptedData> encryptedDataList = new ArrayList<>();
            // Loop through and parse out all the elements from the stream within this section.
            while (!XmlUtil.isNextSectionEnd(in, outerTagDepth)) {
                if (in.getAttributeValue(null, "name") != null) {
                    String[] valueName = new String[1];
                    Object value = XmlUtil.readCurrentValue(in, valueName);
                    if (valueName[0] == null) {
                        throw new XmlPullParserException("Missing value name");
                    }
                    byte[] encryptedData;
                    byte[] iv;
                    if (XML_TAG_ENCRYPTED_DATA.equals(valueName[0])) {
                        encryptedData = (byte[]) value;
                        if (!XmlUtil.isNextSectionEnd(in, outerTagDepth) && in.getAttributeValue(
                                null, "name") != null) {
                            value = XmlUtil.readCurrentValue(in, valueName);
                            if (valueName[0] == null) {
                                throw new XmlPullParserException("Missing value name");
                            }
                            if (XML_TAG_IV.equals(valueName[0])) {
                                iv = (byte[]) value;
                                encryptedDataList.add(new EncryptedData(encryptedData, iv));
                            }
                        }
                    }
                }
            }
            return encryptedDataList;
        }
    }

    public static boolean nextElementWithin(XmlPullParser parser, int outerDepth)
            throws IOException, XmlPullParserException {
        return XmlUtilHelper.nextElementWithin(parser, outerDepth);
    }

    /**
     * Utility class to serialize and deseriaize {@link SoftApConfiguration} object to XML
     * & vice versa. This is used by both {@link com.android.server.wifi.SoftApStore}  modules.
     */
    public static class SoftApConfigurationXmlUtil {
        /**
         * List of XML tags corresponding to SoftApConfiguration object elements.
         */
        public static final String XML_TAG_CLIENT_MACADDRESS = "ClientMacAddress";
        public static final String XML_TAG_BAND_CHANNEL = "BandChannel";
        public static final String XML_TAG_SSID = "SSID"; // Use XML_TAG_WIFI_SSID instead
        public static final String XML_TAG_WIFI_SSID = "WifiSsid";
        public static final String XML_TAG_BSSID = "Bssid";
        public static final String XML_TAG_BAND = "Band";
        public static final String XML_TAG_CHANNEL = "Channel";
        public static final String XML_TAG_HIDDEN_SSID = "HiddenSSID";
        public static final String XML_TAG_SECURITY_TYPE = "SecurityType";
        public static final String XML_TAG_WPA2_PASSPHRASE = "Wpa2Passphrase";
        public static final String XML_TAG_AP_BAND = "ApBand";
        public static final String XML_TAG_PASSPHRASE = "Passphrase";
        public static final String XML_TAG_MAX_NUMBER_OF_CLIENTS = "MaxNumberOfClients";
        public static final String XML_TAG_AUTO_SHUTDOWN_ENABLED = "AutoShutdownEnabled";
        public static final String XML_TAG_SHUTDOWN_TIMEOUT_MILLIS = "ShutdownTimeoutMillis";
        public static final String XML_TAG_CLIENT_CONTROL_BY_USER = "ClientControlByUser";
        public static final String XML_TAG_BLOCKED_CLIENT_LIST = "BlockedClientList";
        public static final String XML_TAG_ALLOWED_CLIENT_LIST = "AllowedClientList";
        public static final String XML_TAG_BRIDGED_MODE_OPPORTUNISTIC_SHUTDOWN_ENABLED =
                "BridgedModeOpportunisticShutdownEnabled";
        public static final String XML_TAG_MAC_RAMDOMIZATION_SETTING = "MacRandomizationSetting";
        public static final String XML_TAG_BAND_CHANNEL_MAP = "BandChannelMap";
        public static final String XML_TAG_80211_AX_ENABLED = "80211axEnabled";
        public static final String XML_TAG_80211_BE_ENABLED = "80211beEnabled";
        public static final String XML_TAG_USER_CONFIGURATION = "UserConfiguration";
        public static final String XML_TAG_BRIDGED_MODE_OPPORTUNISTIC_SHUTDOWN_TIMEOUT_MILLIS =
                "BridgedModeOpportunisticShutdownTimeoutMillis";
        public static final String XML_TAG_VENDOR_ELEMENT = "VendorElement";
        public static final String XML_TAG_VENDOR_ELEMENTS = "VendorElements";
        public static final String XML_TAG_PERSISTENT_RANDOMIZED_MAC_ADDRESS =
                "PersistentRandomizedMacAddress";


        /**
         * Parses the client list from the provided XML stream to a ArrayList object.
         *
         * @param in            XmlPullParser instance pointing to the XML stream.
         * @param outerTagDepth depth of the outer tag in the XML document.
         * @return ArrayList object if parsing is successful, null otherwise.
         */
        public static List<MacAddress> parseClientListFromXml(XmlPullParser in,
                int outerTagDepth) throws XmlPullParserException, IOException,
                IllegalArgumentException {
            List<MacAddress> clientList = new ArrayList<>();
            // Loop through and parse out all the elements from the stream within this section.
            while (!XmlUtil.isNextSectionEnd(in, outerTagDepth)) {
                String[] valueName = new String[1];
                Object value = XmlUtil.readCurrentValue(in, valueName);
                if (valueName[0] == null) {
                    throw new XmlPullParserException("Missing value name");
                }
                switch (valueName[0]) {
                    case XML_TAG_CLIENT_MACADDRESS:
                        MacAddress client = MacAddress.fromString((String) value);
                        clientList.add(client);
                        break;
                    default:
                        Log.e(TAG, "Unknown value name found: " + valueName[0]);
                        break;
                }
            }
            return clientList;
        }

        /**
         * Write the SoftApConfiguration client control list data elements
         * from the provided list to the XML stream.
         *
         * @param out           XmlSerializer instance pointing to the XML stream.
         * @param clientList Client list object to be serialized.
         */
        public static void writeClientListToXml(XmlSerializer out, List<MacAddress> clientList)
                throws XmlPullParserException, IOException {
            for (MacAddress mac: clientList) {
                XmlUtil.writeNextValue(out, XML_TAG_CLIENT_MACADDRESS, mac.toString());
            }
        }

        /**
         * Write the SoftApConfiguration vendor elements list information elements to the XML
         *
         * @param out XmlSerializer instance pointing to the XML stream
         * @param elements Vendor elements list
         */
        public static void writeVendorElementsSetToXml(
                XmlSerializer out, List<ScanResult.InformationElement> elements)
                throws XmlPullParserException, IOException {
            for (ScanResult.InformationElement e : elements) {
                XmlUtil.writeNextValue(out, XML_TAG_VENDOR_ELEMENT,
                        InformationElementUtil.toHexString(e));
            }
        }

        /**
         * Parses the vendor elements from the provided XML stream to HashSet object.
         *
         * @param in XmlPullParser instance pointing to the XML stream
         * @param outerTagDepth depth of the outer tag in the XML document
         * @return HashSet object if parsing is successful, empty set otherwise
         */
        public static List<ScanResult.InformationElement> parseVendorElementsFromXml(
                XmlPullParser in, int outerTagDepth)
                throws XmlPullParserException, IOException, IllegalArgumentException {
            List<ScanResult.InformationElement> elements = new ArrayList<>();
            while (!XmlUtil.isNextSectionEnd(in, outerTagDepth)) {
                String[] valueName = new String[1];
                Object value = XmlUtil.readCurrentValue(in, valueName);
                if (valueName[0] == null) {
                    throw new XmlPullParserException("Missing value name");
                }
                if (XML_TAG_VENDOR_ELEMENT.equals(valueName[0])) {
                    ScanResult.InformationElement[] data =
                            InformationElementUtil.parseInformationElements((String) value);
                    elements.addAll(Arrays.asList(data));
                } else {
                    Log.e(TAG, "Unknown value name found: " + valueName[0]);
                }
            }
            return elements;
        }

        /**
         * Parses the band and channel from the provided XML stream to a SparseIntArray object.
         *
         * @param in            XmlPullParser instance pointing to the XML stream.
         * @param outerTagDepth depth of the outer tag in the XML document.
         * @return SparseIntArray object if parsing is successful, null otherwise.
         */
        public static SparseIntArray parseChannelsFromXml(XmlPullParser in,
                int outerTagDepth) throws XmlPullParserException, IOException,
                IllegalArgumentException {
            SparseIntArray channels = new SparseIntArray();
            while (!XmlUtil.isNextSectionEnd(in, outerTagDepth)) {
                int band = ApConfigUtil.INVALID_VALUE_FOR_BAND_OR_CHANNEL;
                int channel = ApConfigUtil.INVALID_VALUE_FOR_BAND_OR_CHANNEL;
                switch (in.getName()) {
                    case XML_TAG_BAND_CHANNEL:
                        while (!XmlUtil.isNextSectionEnd(in, outerTagDepth + 1)) {
                            String[] valueName = new String[1];
                            Object value = XmlUtil.readCurrentValue(in, valueName);
                            if (valueName[0] == null) {
                                throw new XmlPullParserException("Missing value name");
                            }
                            switch (valueName[0]) {
                                case XML_TAG_BAND:
                                    band = (int) value;
                                    break;
                                case XML_TAG_CHANNEL:
                                    channel = (int) value;
                                    break;
                                default:
                                    Log.e(TAG, "Unknown value name found: " + valueName[0]);
                                    break;
                            }
                        }
                        channels.put(band, channel);
                        break;
                }
            }
            return channels;
        }

        /**
         * Write the SoftApConfiguration channels data elements
         * from the provided SparseIntArray to the XML stream.
         *
         * @param out       XmlSerializer instance pointing to the XML stream.
         * @param channels  SparseIntArray, which includes bands and channels, to be serialized.
         */
        public static void writeChannelsToXml(XmlSerializer out, SparseIntArray channels)
                throws XmlPullParserException, IOException {
            for (int i = 0; i < channels.size(); i++) {
                XmlUtil.writeNextSectionStart(out, XML_TAG_BAND_CHANNEL);
                XmlUtil.writeNextValue(out, XML_TAG_BAND, channels.keyAt(i));
                XmlUtil.writeNextValue(out, XML_TAG_CHANNEL, channels.valueAt(i));
                XmlUtil.writeNextSectionEnd(out, XML_TAG_BAND_CHANNEL);
            }
        }

        /**
         * Write the SoftApConfiguration data elements to the XML stream.
         *
         * @param out          XmlSerializer instance pointing to the XML stream.
         * @param softApConfig configuration of the Soft AP.
         */
        public static void writeSoftApConfigurationToXml(@NonNull XmlSerializer out,
                @NonNull SoftApConfiguration softApConfig,
                WifiConfigStoreEncryptionUtil encryptionUtil)
                throws XmlPullParserException, IOException {
            if (softApConfig.getWifiSsid() != null) {
                XmlUtil.writeNextValue(out, XML_TAG_WIFI_SSID,
                        softApConfig.getWifiSsid().toString());
            }
            if (softApConfig.getBssid() != null) {
                XmlUtil.writeNextValue(out, XML_TAG_BSSID, softApConfig.getBssid().toString());
            }
            if (!SdkLevel.isAtLeastS()) {
                // Band and channel change to store in Tag:BandChannelMap from S.
                XmlUtil.writeNextValue(out, XML_TAG_AP_BAND, softApConfig.getBand());
                XmlUtil.writeNextValue(out, XML_TAG_CHANNEL, softApConfig.getChannel());
            }
            XmlUtil.writeNextValue(out, XML_TAG_HIDDEN_SSID, softApConfig.isHiddenSsid());
            XmlUtil.writeNextValue(out, XML_TAG_SECURITY_TYPE, softApConfig.getSecurityType());
            if (!ApConfigUtil.isNonPasswordAP(softApConfig.getSecurityType())) {
                XmlUtil.writeSoftApPassphraseToXml(out, softApConfig.getPassphrase(),
                        encryptionUtil);
            }

            XmlUtil.writeNextValue(out, XML_TAG_MAX_NUMBER_OF_CLIENTS,
                    softApConfig.getMaxNumberOfClients());
            XmlUtil.writeNextValue(out, XML_TAG_CLIENT_CONTROL_BY_USER,
                    softApConfig.isClientControlByUserEnabled());
            XmlUtil.writeNextValue(out, XML_TAG_AUTO_SHUTDOWN_ENABLED,
                    softApConfig.isAutoShutdownEnabled());
            XmlUtil.writeNextValue(out, XML_TAG_SHUTDOWN_TIMEOUT_MILLIS,
                    softApConfig.getShutdownTimeoutMillis());
            XmlUtil.writeNextSectionStart(out, XML_TAG_BLOCKED_CLIENT_LIST);
            XmlUtil.SoftApConfigurationXmlUtil.writeClientListToXml(out,
                    softApConfig.getBlockedClientList());
            XmlUtil.writeNextSectionEnd(out, XML_TAG_BLOCKED_CLIENT_LIST);

            XmlUtil.writeNextSectionStart(out, XML_TAG_ALLOWED_CLIENT_LIST);
            XmlUtil.SoftApConfigurationXmlUtil.writeClientListToXml(out,
                    softApConfig.getAllowedClientList());
            XmlUtil.writeNextSectionEnd(out, XML_TAG_ALLOWED_CLIENT_LIST);
            if (SdkLevel.isAtLeastS()) {
                XmlUtil.writeNextValue(out, XML_TAG_BRIDGED_MODE_OPPORTUNISTIC_SHUTDOWN_ENABLED,
                        softApConfig.isBridgedModeOpportunisticShutdownEnabled());
                XmlUtil.writeNextValue(out, XML_TAG_MAC_RAMDOMIZATION_SETTING,
                        softApConfig.getMacRandomizationSetting());

                XmlUtil.writeNextSectionStart(out, XML_TAG_BAND_CHANNEL_MAP);
                XmlUtil.SoftApConfigurationXmlUtil.writeChannelsToXml(out,
                        softApConfig.getChannels());
                XmlUtil.writeNextSectionEnd(out, XML_TAG_BAND_CHANNEL_MAP);
                XmlUtil.writeNextValue(out, XML_TAG_80211_AX_ENABLED,
                        softApConfig.isIeee80211axEnabled());
                XmlUtil.writeNextValue(out, XML_TAG_USER_CONFIGURATION,
                        softApConfig.isUserConfiguration());
            }
            if (SdkLevel.isAtLeastT()) {
                XmlUtil.writeNextValue(out,
                        XML_TAG_BRIDGED_MODE_OPPORTUNISTIC_SHUTDOWN_TIMEOUT_MILLIS,
                        softApConfig.getBridgedModeOpportunisticShutdownTimeoutMillisInternal());
                XmlUtil.writeNextSectionStart(out, XML_TAG_VENDOR_ELEMENTS);
                XmlUtil.SoftApConfigurationXmlUtil.writeVendorElementsSetToXml(out,
                        softApConfig.getVendorElementsInternal());
                XmlUtil.writeNextSectionEnd(out, XML_TAG_VENDOR_ELEMENTS);
                XmlUtil.writeNextValue(out, XML_TAG_80211_BE_ENABLED,
                        softApConfig.isIeee80211beEnabled());
                if (softApConfig.getPersistentRandomizedMacAddress() != null) {
                    XmlUtil.writeNextValue(out, XML_TAG_PERSISTENT_RANDOMIZED_MAC_ADDRESS,
                            softApConfig.getPersistentRandomizedMacAddress().toString());
                }
            }
        } // End of writeSoftApConfigurationToXml

        /**
         * Returns configuration of the SoftAp from the XML stream.
         *
         * @param in XmlPullParser instance pointing to the XML stream.
         * @param outerTagDepth depth of the outer tag in the XML document.
         * @param settingsMigrationDataHolder the class instance of SettingsMigrationDataHolder
         */
        @Nullable
        public static SoftApConfiguration parseFromXml(XmlPullParser in, int outerTagDepth,
                SettingsMigrationDataHolder settingsMigrationDataHolder,
                boolean shouldExpectEncryptedCredentials,
                @Nullable WifiConfigStoreEncryptionUtil encryptionUtil)
                throws XmlPullParserException, IOException  {
            SoftApConfiguration.Builder softApConfigBuilder = new SoftApConfiguration.Builder();
            int securityType = SoftApConfiguration.SECURITY_TYPE_OPEN;
            String passphrase = null;
            // SSID may be retrieved from the old encoding (XML_TAG_SSID) or the new encoding
            // (XML_TAG_WIFI_SSID).
            boolean hasSsid = false;
            String bssid = null;
            // Note that, during deserialization, we may read the old band encoding (XML_TAG_BAND)
            // or the new band encoding (XML_TAG_AP_BAND) that is used after the introduction of the
            // 6GHz band. If the old encoding is found, a conversion is done.
            int channel = -1;
            int apBand = -1;
            boolean hasBandChannelMap = false;
            List<MacAddress> blockedList = new ArrayList<>();
            List<MacAddress> allowedList = new ArrayList<>();
            boolean autoShutdownEnabledTagPresent = false;
            try {
                while (!XmlUtil.isNextSectionEnd(in, outerTagDepth)) {
                    if (in.getAttributeValue(null, "name") != null) {
                        String[] valueName = new String[1];
                        Object value = XmlUtil.readCurrentValue(in, valueName);
                        if (TextUtils.isEmpty(valueName[0])) {
                            throw new XmlPullParserException("Missing value name");
                        }
                        switch (valueName[0]) {
                            case XML_TAG_SSID:
                                hasSsid = true;
                                softApConfigBuilder.setSsid((String) value);
                                break;
                            case XML_TAG_WIFI_SSID:
                                hasSsid = true;
                                final WifiSsid wifiSsid = WifiSsid.fromString((String) value);
                                if (SdkLevel.isAtLeastT()) {
                                    softApConfigBuilder.setWifiSsid(wifiSsid);
                                } else {
                                    // If the SSID is non-UTF-8, then use WifiManager.UNKNOWN_SSID.
                                    // This should not happen since non-UTF-8 SSIDs may only be set
                                    // with SoftApConfiguration#Builder#setWifiSsid(WifiSsid),
                                    // which is only available in T and above.
                                    final CharSequence utf8Ssid = wifiSsid.getUtf8Text();
                                    softApConfigBuilder.setSsid(utf8Ssid != null
                                            ? utf8Ssid.toString() : WifiManager.UNKNOWN_SSID);
                                }
                                break;
                            case XML_TAG_BSSID:
                                bssid = (String) value;
                                softApConfigBuilder.setBssid(MacAddress.fromString(bssid));
                                break;
                            case XML_TAG_BAND:
                                apBand = ApConfigUtil.convertWifiConfigBandToSoftApConfigBand(
                                        (int) value);
                                break;
                            case XML_TAG_AP_BAND:
                                apBand = (int) value;
                                break;
                            case XML_TAG_CHANNEL:
                                channel = (int) value;
                                break;
                            case XML_TAG_HIDDEN_SSID:
                                softApConfigBuilder.setHiddenSsid((boolean) value);
                                break;
                            case XML_TAG_SECURITY_TYPE:
                                securityType = (int) value;
                                break;
                            case XML_TAG_WPA2_PASSPHRASE:
                            case XML_TAG_PASSPHRASE:
                                passphrase = (String) value;
                                break;
                            case XML_TAG_MAX_NUMBER_OF_CLIENTS:
                                softApConfigBuilder.setMaxNumberOfClients((int) value);
                                break;
                            case XML_TAG_AUTO_SHUTDOWN_ENABLED:
                                softApConfigBuilder.setAutoShutdownEnabled((boolean) value);
                                autoShutdownEnabledTagPresent = true;
                                break;
                            case XML_TAG_SHUTDOWN_TIMEOUT_MILLIS:
                                long shutDownMillis = 0;
                                if (value instanceof Integer) {
                                    shutDownMillis = Long.valueOf((int) value);
                                } else if (value instanceof Long) {
                                    shutDownMillis = (long) value;
                                }
                                if (shutDownMillis == 0
                                        && CompatChanges.isChangeEnabled(
                                        SoftApConfiguration.REMOVE_ZERO_FOR_TIMEOUT_SETTING)) {
                                    shutDownMillis = SoftApConfiguration.DEFAULT_TIMEOUT;
                                }
                                softApConfigBuilder.setShutdownTimeoutMillis(shutDownMillis);
                                break;
                            case XML_TAG_CLIENT_CONTROL_BY_USER:
                                softApConfigBuilder.setClientControlByUserEnabled((boolean) value);
                                break;
                            case XML_TAG_BRIDGED_MODE_OPPORTUNISTIC_SHUTDOWN_ENABLED:
                                if (SdkLevel.isAtLeastS()) {
                                    softApConfigBuilder.setBridgedModeOpportunisticShutdownEnabled(
                                            (boolean) value);
                                }
                                break;
                            case XML_TAG_MAC_RAMDOMIZATION_SETTING:
                                if (SdkLevel.isAtLeastS()) {
                                    softApConfigBuilder.setMacRandomizationSetting((int) value);
                                }
                                break;
                            case XML_TAG_80211_AX_ENABLED:
                                if (SdkLevel.isAtLeastS()) {
                                    softApConfigBuilder.setIeee80211axEnabled((boolean) value);
                                }
                                break;
                            case XML_TAG_80211_BE_ENABLED:
                                if (SdkLevel.isAtLeastT()) {
                                    softApConfigBuilder.setIeee80211beEnabled((boolean) value);
                                }
                                break;
                            case XML_TAG_USER_CONFIGURATION:
                                if (SdkLevel.isAtLeastS()) {
                                    softApConfigBuilder.setUserConfiguration((boolean) value);
                                }
                                break;
                            case XML_TAG_BRIDGED_MODE_OPPORTUNISTIC_SHUTDOWN_TIMEOUT_MILLIS:
                                if (SdkLevel.isAtLeastT()) {
                                    long bridgedTimeout = (long) value;
                                    bridgedTimeout = bridgedTimeout == 0
                                            ? SoftApConfiguration.DEFAULT_TIMEOUT : bridgedTimeout;
                                    softApConfigBuilder
                                            .setBridgedModeOpportunisticShutdownTimeoutMillis(
                                                    bridgedTimeout);
                                }
                                break;
                            case XML_TAG_PERSISTENT_RANDOMIZED_MAC_ADDRESS:
                                if (SdkLevel.isAtLeastT()) {
                                    softApConfigBuilder.setRandomizedMacAddress(
                                            MacAddress.fromString((String) value));
                                }
                                break;
                            default:
                                Log.w(TAG, "Ignoring unknown value name " + valueName[0]);
                                break;
                        }
                    } else {
                        String tagName = in.getName();
                        List<MacAddress> parseredList;
                        if (tagName == null) {
                            throw new XmlPullParserException("Unexpected null tag found");
                        }
                        switch (tagName) {
                            case XML_TAG_BLOCKED_CLIENT_LIST:
                                parseredList =
                                        XmlUtil.SoftApConfigurationXmlUtil.parseClientListFromXml(
                                        in, outerTagDepth + 1);
                                if (parseredList != null) {
                                    blockedList = new ArrayList<>(parseredList);
                                }
                                break;
                            case XML_TAG_ALLOWED_CLIENT_LIST:
                                parseredList =
                                        XmlUtil.SoftApConfigurationXmlUtil.parseClientListFromXml(
                                        in, outerTagDepth + 1);
                                if (parseredList != null) {
                                    allowedList = new ArrayList<>(parseredList);
                                }
                                break;
                            case XML_TAG_BAND_CHANNEL_MAP:
                                if (SdkLevel.isAtLeastS()) {
                                    hasBandChannelMap = true;
                                    SparseIntArray channels = XmlUtil.SoftApConfigurationXmlUtil
                                            .parseChannelsFromXml(in, outerTagDepth + 1);
                                    softApConfigBuilder.setChannels(channels);
                                }
                                break;
                            case XML_TAG_VENDOR_ELEMENTS:
                                if (SdkLevel.isAtLeastT()) {
                                    softApConfigBuilder.setVendorElements(
                                            SoftApConfigurationXmlUtil.parseVendorElementsFromXml(
                                                    in, outerTagDepth + 1));
                                }
                                break;
                            case XML_TAG_PASSPHRASE:
                                passphrase = readSoftApPassphraseFromXml(in, outerTagDepth,
                                        shouldExpectEncryptedCredentials, encryptionUtil);
                                break;
                            default:
                                Log.w(TAG, "Ignoring unknown tag found: " + tagName);
                                break;
                        }
                    }
                }
                softApConfigBuilder.setBlockedClientList(blockedList);
                softApConfigBuilder.setAllowedClientList(allowedList);
                if (!hasBandChannelMap) {
                    // Set channel and band
                    if (channel == 0) {
                        softApConfigBuilder.setBand(apBand);
                    } else {
                        softApConfigBuilder.setChannel(channel, apBand);
                    }
                }

                // We should at least have an SSID restored from store.
                if (!hasSsid) {
                    Log.e(TAG, "Failed to parse SSID");
                    return null;
                }
                if (ApConfigUtil.isNonPasswordAP(securityType)) {
                    softApConfigBuilder.setPassphrase(null, securityType);
                } else {
                    softApConfigBuilder.setPassphrase(passphrase, securityType);
                }
                if (!autoShutdownEnabledTagPresent) {
                    // Migrate data out of settings.
                    WifiMigration.SettingsMigrationData migrationData =
                            settingsMigrationDataHolder.retrieveData();
                    if (migrationData == null) {
                        Log.e(TAG, "No migration data present");
                    } else {
                        softApConfigBuilder.setAutoShutdownEnabled(
                                migrationData.isSoftApTimeoutEnabled());
                    }
                }
                if (bssid != null && SdkLevel.isAtLeastS()) {
                    // Force MAC randomization setting to none when BSSID is configured
                    softApConfigBuilder.setMacRandomizationSetting(
                            SoftApConfiguration.RANDOMIZATION_NONE);
                }
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Failed to parse configuration " + e);
                return null;
            }
            return softApConfigBuilder.build();
        } // End of parseFromXml
    }

    private static void writeSoftApPassphraseToXml(
            XmlSerializer out, String passphrase, WifiConfigStoreEncryptionUtil encryptionUtil)
            throws XmlPullParserException, IOException {
        EncryptedData encryptedData = null;
        if (encryptionUtil != null && passphrase != null) {
            encryptedData = encryptionUtil.encrypt(passphrase.getBytes());
            if (encryptedData == null) {
                // We silently fail encryption failures!
                Log.wtf(TAG, "Encryption of softAp passphrase failed");
            }
        }
        if (encryptedData != null) {
            writeNextSectionStart(out, SoftApConfigurationXmlUtil.XML_TAG_PASSPHRASE);
            EncryptedDataXmlUtil.writeToXml(out, encryptedData);
            writeNextSectionEnd(out, SoftApConfigurationXmlUtil.XML_TAG_PASSPHRASE);
        } else {
            writeNextValue(out, SoftApConfigurationXmlUtil.XML_TAG_PASSPHRASE, passphrase);
        }
    }

    private static String readSoftApPassphraseFromXml(XmlPullParser in, int outerTagDepth,
            boolean shouldExpectEncryptedCredentials, WifiConfigStoreEncryptionUtil encryptionUtil)
            throws XmlPullParserException, IOException {
        if (!shouldExpectEncryptedCredentials || encryptionUtil == null) {
            throw new XmlPullParserException(
                    "Encrypted passphraseBytes section not expected");
        }
        EncryptedData encryptedData =
                XmlUtil.EncryptedDataXmlUtil.parseFromXml(in, outerTagDepth + 1);
        byte[] passphraseBytes = encryptionUtil.decrypt(encryptedData);
        if (passphraseBytes == null) {
            Log.wtf(TAG, "Decryption of passphraseBytes failed");
            return null;
        } else {
            return new String(passphraseBytes);
        }
    }
}


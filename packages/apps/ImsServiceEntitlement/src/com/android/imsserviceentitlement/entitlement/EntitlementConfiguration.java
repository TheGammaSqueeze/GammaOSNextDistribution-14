/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.imsserviceentitlement.entitlement;

import android.content.Context;

import com.android.imsserviceentitlement.ts43.Ts43Constants.ResponseXmlAttributes;
import com.android.imsserviceentitlement.ts43.Ts43Constants.ResponseXmlNode;
import com.android.imsserviceentitlement.utils.XmlDoc;
import com.android.libraries.entitlement.ServiceEntitlement;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** Provides the entitlement characteristic which stored from previous query. */
public class EntitlementConfiguration {
    /** Default value of version for VERS characteristic. */
    private static final int DEFAULT_VERSION = 0;
    /** Default value of validity for VERS and TOKEN characteristics. */
    private static final long DEFAULT_VALIDITY = 0;
    /** Default value of VoLTE/VoWifi/SMSoverIP entitlemenet status. */
    private static final int INCOMPATIBLE_STATE = 2;

    private final EntitlementConfigurationsDataStore mConfigurationsDataStore;

    private XmlDoc mXmlDoc = new XmlDoc(null);

    public EntitlementConfiguration(Context context, int subId) {
        mConfigurationsDataStore = EntitlementConfigurationsDataStore.getInstance(context, subId);
        mConfigurationsDataStore.get().ifPresent(rawXml -> mXmlDoc = new XmlDoc(rawXml));
    }

    /** Update VERS characteristics with given version and validity. */
    public void update(String rawXml) {
        mConfigurationsDataStore.set(rawXml);
        mXmlDoc = new XmlDoc(rawXml);
    }

    /**
     * Returns VoLTE entitlement status from the {@link EntitlementConfigurationsDataStore}. If no
     * data exist then return the default value {@link #INCOMPATIBLE_STATE}.
     */
    public int getVolteStatus() {
        return mXmlDoc.get(
                ResponseXmlNode.APPLICATION,
                ResponseXmlAttributes.ENTITLEMENT_STATUS,
                ServiceEntitlement.APP_VOLTE)
                .map(Integer::parseInt)
                .orElse(INCOMPATIBLE_STATE);
    }

    /**
     * Returns VoWiFi entitlement status from the {@link EntitlementConfigurationsDataStore}. If no
     * data exist then return the default value {@link #INCOMPATIBLE_STATE}.
     */
    public int getVoWifiStatus() {
        return mXmlDoc.get(
                ResponseXmlNode.APPLICATION,
                ResponseXmlAttributes.ENTITLEMENT_STATUS,
                ServiceEntitlement.APP_VOWIFI)
                .map(Integer::parseInt)
                .orElse(INCOMPATIBLE_STATE);
    }

    /**
     * Returns SMSoIP entitlement status from the {@link EntitlementConfigurationsDataStore}. If no
     * data exist then return the default value {@link #INCOMPATIBLE_STATE}.
     */
    public int getSmsOverIpStatus() {
        return mXmlDoc.get(
                ResponseXmlNode.APPLICATION,
                ResponseXmlAttributes.ENTITLEMENT_STATUS,
                ServiceEntitlement.APP_SMSOIP)
                .map(Integer::parseInt)
                .orElse(INCOMPATIBLE_STATE);
    }

    /**
     * Returns token stored in the {@link EntitlementConfigurationsDataStore} if it is in validity
     * period. Returns {@link Optional#empty()} if the token was expired or the value of token
     * validity not positive.
     */
    public Optional<String> getToken() {
        return isTokenInValidityPeriod()
                ? mXmlDoc.get(ResponseXmlNode.TOKEN, ResponseXmlAttributes.TOKEN, null)
                : Optional.empty();
    }

    private boolean isTokenInValidityPeriod() {
        long queryTimeMillis = mConfigurationsDataStore.getQueryTimeMillis();
        long tokenValidityMillis = TimeUnit.SECONDS.toMillis(getTokenValidity());

        if (queryTimeMillis <= 0) {
            // False if the query time not been set.
            return false;
        }

        // When the token validity is set to 0, the Entitlement Client shall store the token without
        // any limitation of duration.
        if (tokenValidityMillis <= 0) {
            return true;
        }

        return (System.currentTimeMillis() - queryTimeMillis) < tokenValidityMillis;
    }

    /**
     * Returns the validity of the token, in seconds. The validity is counted from the time it is
     * received by the client. If no data exist then returns default value 0.
     */
    public long getTokenValidity() {
        return mXmlDoc.get(
                ResponseXmlNode.TOKEN,
                ResponseXmlAttributes.VALIDITY,
                null)
                .map(Long::parseLong)
                .orElse(DEFAULT_VALIDITY);
    }

    /**
     * Returns version stored in the {@link EntitlementCharacteristicDataStore}.
     * If no data exists then return the default value {@link #DEFAULT_VERSION}.
     */
    public String getVersion() {
        return mXmlDoc.get(ResponseXmlNode.VERS, ResponseXmlAttributes.VERSION, null)
                .orElse(String.valueOf(DEFAULT_VERSION));
    }

    /**
     * Returns the validity of the version, in seconds. The validity is counted from the time it is
     * received by the client. If no data exist then returns default value 0.
     */
    public long getVersValidity() {
        return mXmlDoc.get(
                ResponseXmlNode.VERS,
                ResponseXmlAttributes.VALIDITY,
                null)
                .map(Long::parseLong)
                .orElse(DEFAULT_VALIDITY);
    }

    public enum ClientBehavior {
        /** Unknown behavior. */
        UNKNOWN_BEHAVIOR,
        /** Entitlement data is valid during validity seconds. */
        VALID_DURING_VALIDITY,
        /** Entitlement data is valid without any limitation of duration. */
        VALID_WITHOUT_DURATION,
        /** Entitlement data has to be reset to default configuration */
        NEEDS_TO_RESET,
        /**
         * Entitlement data has to be reset to default configuration except for version and
         * validity. The Entitlement Client shall not perform client configuration requests anymore
         * for the services just configured.
         */
        NEEDS_TO_RESET_EXCEPT_VERS,
        /**
         * entitlement data has to be reset to default configuration except for version and
         * validity. The Entitlement Client shall not perform client configuration requests anymore
         * for the services just configured, until the end-user switches the setting to On.
         */
        NEEDS_TO_RESET_EXCEPT_VERS_UNTIL_SETTING_ON,
    }

    /** Returns {@link ClientBehavior} for the service to be configured. */
    public ClientBehavior entitlementValidation() {
        int version = mXmlDoc.get(
                ResponseXmlNode.VERS,
                ResponseXmlAttributes.VERSION,
                null)
                .map(Integer::parseInt)
                .orElse(DEFAULT_VERSION);
        long validity = mXmlDoc.get(
                ResponseXmlNode.VERS,
                ResponseXmlAttributes.VALIDITY,
                null)
                .map(Long::parseLong)
                .orElse(DEFAULT_VALIDITY);

        if (version > 0 && validity > 0) {
            return ClientBehavior.VALID_DURING_VALIDITY;
        } else if (version > 0 && validity == 0) {
            return ClientBehavior.VALID_WITHOUT_DURATION;
        } else if (version == 0 && validity == 0) {
            return ClientBehavior.NEEDS_TO_RESET;
        } else if (version == -1 && validity == -1) {
            return ClientBehavior.NEEDS_TO_RESET_EXCEPT_VERS;
        } else if (version == -2 && validity == -2) {
            return ClientBehavior.NEEDS_TO_RESET_EXCEPT_VERS_UNTIL_SETTING_ON;
        }

        // Should never reach.
        return ClientBehavior.UNKNOWN_BEHAVIOR;
    }

    /**
     * Removes the stored configuration and reverts to the default configuration when:
     * <ul>
     *   <li>on SIM card change
     *   <li>on menu Factory reset
     *   <li>if a service is barred by the Service Provider (i.e. configuration version set to 0,
     *       -1, -2). In that case, version and validity are not reset
     * </ul>
     */
    public void reset() {
        // Default configuration of the Characteristics:
        //   - VERS.version = 0
        //   - VERS.validity = 0
        //   - TOKEN.token = null (i.e. no value assigned)
        //   - TOKEN.validity =0
        //   - VoLTE.EntitlementStatus=2 (INCOMPATIBLE_STATE)
        //   - VoWiFi.EntitlementStatus=2 (INCOMPATIBLE_STATE)
        //   - SMSoIP.EntitlementStatus=2 (INCOMPATIBLE_STATE)
        update(null);
    }

    /** Reverts to the default configurations except the version and validity. */
    public void resetConfigsExceptVers() {
        String rawXml =
                "<wap-provisioningdoc version=\"1.1\">"
                + "  <characteristic type=\"VERS\">"
                + "    <parm name=\"version\" value=\"" + getVersion() + "\"/>"
                + "    <parm name=\"validity\" value=\"" + getVersValidity() + "\"/>"
                + "  </characteristic>"
                + "  <characteristic type=\"TOKEN\">"
                + "    <parm name=\"token\" value=\"\"/>"
                + "    <parm name=\"validity\" value=\"0\"/>"
                + "  </characteristic>"
                + "  <characteristic type=\"APPLICATION\">"
                + "    <parm name=\"AppID\" value=\"ap2003\"/>"
                + "    <parm name=\"EntitlementStatus\" value=\"2\"/>"
                + "  </characteristic>"
                + "  <characteristic type=\"APPLICATION\">"
                + "    <parm name=\"AppID\" value=\"ap2004\"/>"
                + "    <parm name=\"EntitlementStatus\" value=\"2\"/>"
                + "  </characteristic>"
                + "  <characteristic type=\"APPLICATION\">"
                + "    <parm name=\"AppID\" value=\"ap2005\"/>"
                + "    <parm name=\"EntitlementStatus\" value=\"2\"/>"
                + "  </characteristic>"
                + "</wap-provisioningdoc>";
        update(rawXml);
    }
}

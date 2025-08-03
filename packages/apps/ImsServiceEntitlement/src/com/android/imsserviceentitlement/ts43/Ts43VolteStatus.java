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

package com.android.imsserviceentitlement.ts43;

import com.android.imsserviceentitlement.ts43.Ts43Constants.ResponseXmlAttributes;
import com.android.imsserviceentitlement.ts43.Ts43Constants.ResponseXmlNode;
import com.android.imsserviceentitlement.utils.XmlDoc;
import com.android.libraries.entitlement.ServiceEntitlement;

import com.google.auto.value.AutoValue;

/**
 * Implementation of Volte entitlement status and server data availability for TS.43 entitlement
 * solution. This class is only used to report the entitlement status of Volte.
 */
@AutoValue
public abstract class Ts43VolteStatus {
    /** The entitlement status of Volte service. */
    public static class EntitlementStatus {
        public EntitlementStatus() {}

        public static final int DISABLED = 0;
        public static final int ENABLED = 1;
        public static final int INCOMPATIBLE = 2;
        public static final int PROVISIONING = 3;
    }

    /** The entitlement status of Volte service. */
    public abstract int entitlementStatus();

    public static Ts43VolteStatus.Builder builder() {
        return new AutoValue_Ts43VolteStatus.Builder()
                .setEntitlementStatus(EntitlementStatus.DISABLED);
    }

    public static Ts43VolteStatus.Builder builder(XmlDoc doc) {
        return builder()
                .setEntitlementStatus(
                        doc.get(ResponseXmlNode.APPLICATION,
                                ResponseXmlAttributes.ENTITLEMENT_STATUS,
                                ServiceEntitlement.APP_VOLTE)
                                .map(status -> Integer.parseInt(status))
                                .orElse(EntitlementStatus.INCOMPATIBLE));
    }

    /** Builder of {@link Ts43VolteStatus}. */
    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Ts43VolteStatus build();

        public abstract Builder setEntitlementStatus(int entitlementStatus);
    }

    public boolean isActive() {
        return entitlementStatus() == EntitlementStatus.ENABLED;
    }

    public final String toString() {
        return "Ts43VolteStatus {"
                + "entitlementStatus="
                + entitlementStatus()
                + "}";
    }
}

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
package com.android.tradefed.util;

import com.android.os.adservices.AdservicesExtensionAtoms;
import com.android.os.dnd.DndExtensionAtoms;
import com.android.os.expresslog.ExpresslogExtensionAtoms;
import com.android.os.framework.FrameworkExtensionAtoms;
import com.android.os.healthfitness.api.ApiExtensionAtoms;
import com.android.os.healthfitness.ui.UiExtensionAtoms;
import com.android.os.location.LocationExtensionAtoms;
import com.android.os.memorysafety.MemorysafetyExtensionAtoms;
import com.android.os.permissioncontroller.PermissioncontrollerExtensionAtoms;
import com.android.os.telephony.TelephonyExtensionAtoms;
import com.android.os.telephony.qns.QnsExtensionAtoms;
import com.android.os.uwb.UwbExtensionAtoms;
import com.android.os.wear.media.WearMediaExtensionAtoms;
import com.android.os.wearpas.WearpasExtensionAtoms;
import com.android.os.wearservices.WearservicesExtensionAtoms;
import com.android.os.wifi.WifiExtensionAtoms;
import com.android.os.credentials.CredentialsExtensionAtoms;
import com.android.os.sdksandbox.SdksandboxExtensionAtoms;

import com.google.protobuf.ExtensionRegistry;

/** ExtensionAtomsRegistry for local use of statsd. */
public final class ExtensionAtomsRegistry {
    @SuppressWarnings("NonFinalStaticField")
    public static ExtensionRegistry registry;

    static {
        /*
         * In Java, when parsing a message containing extensions, you must provide an
         * ExtensionRegistry which contains definitions of all of the extensions which you want the
         * parser to recognize. This is necessary because Java's bytecode loading semantics do not
         * provide any way for the protocol buffers library to automatically discover all extensions
         * defined in your binary.
         *
         * <p>See http://sites/protocol-buffers/user-docs/miscellaneous-howtos/extensions
         * #Java_ExtensionRegistry_
         */
        registry = ExtensionRegistry.newInstance();
        registerAllExtensions(registry);
        registry = registry.getUnmodifiable();
    }

    /* Registers all proto2 extensions. */
    private static void registerAllExtensions(ExtensionRegistry extensionRegistry) {
        AdservicesExtensionAtoms.registerAllExtensions(extensionRegistry);
        DndExtensionAtoms.registerAllExtensions(extensionRegistry);
        ExpresslogExtensionAtoms.registerAllExtensions(extensionRegistry);
        FrameworkExtensionAtoms.registerAllExtensions(extensionRegistry);
        ApiExtensionAtoms.registerAllExtensions(extensionRegistry);
        UiExtensionAtoms.registerAllExtensions(extensionRegistry);
        LocationExtensionAtoms.registerAllExtensions(extensionRegistry);
        MemorysafetyExtensionAtoms.registerAllExtensions(extensionRegistry);
        PermissioncontrollerExtensionAtoms.registerAllExtensions(extensionRegistry);
        TelephonyExtensionAtoms.registerAllExtensions(extensionRegistry);
        QnsExtensionAtoms.registerAllExtensions(extensionRegistry);
        UwbExtensionAtoms.registerAllExtensions(extensionRegistry);
        WearMediaExtensionAtoms.registerAllExtensions(extensionRegistry);
        WearpasExtensionAtoms.registerAllExtensions(extensionRegistry);
        WearservicesExtensionAtoms.registerAllExtensions(extensionRegistry);
        WifiExtensionAtoms.registerAllExtensions(extensionRegistry);
        CredentialsExtensionAtoms.registerAllExtensions(extensionRegistry);
        SdksandboxExtensionAtoms.registerAllExtensions(extensionRegistry);
    }

    private ExtensionAtomsRegistry() {}
}

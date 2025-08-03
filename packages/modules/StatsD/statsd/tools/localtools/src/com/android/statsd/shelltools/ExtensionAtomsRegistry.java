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
package com.android.statsd.shelltools;

import com.android.internal.os.ExperimentIdsProto;
import com.android.internal.os.UidDataProto;
import com.android.os.ActiveConfigProto;
import com.android.os.ShellConfig;
import com.android.os.adservices.AdservicesExtensionAtoms;
import com.android.os.art.ArtExtensionAtoms;
import com.android.os.automotive.caruilib.AutomotiveCaruilibAtoms;
import com.android.os.devicelogs.DeviceLogsAtoms;
import com.android.os.dnd.DndAtoms;
import com.android.os.dnd.DndExtensionAtoms;
import com.android.os.expresslog.ExpresslogExtensionAtoms;
import com.android.os.framework.FrameworkExtensionAtoms;
import com.android.os.gps.GpsAtoms;
import com.android.os.grammaticalinflection.GrammaticalInflectionExtensionAtoms;
import com.android.os.hardware.biometrics.BiometricsAtoms;
import com.android.os.healthfitness.api.ApiExtensionAtoms;
import com.android.os.healthfitness.ui.UiExtensionAtoms;
import com.android.os.hotword.HotwordAtoms;
import com.android.os.kernel.KernelAtoms;
import com.android.os.locale.LocaleAtoms;
import com.android.os.location.LocationAtoms;
import com.android.os.location.LocationExtensionAtoms;
import com.android.os.media.MediaDrmAtoms;
import com.android.os.memorysafety.MemorysafetyExtensionAtoms;
import com.android.os.permissioncontroller.PermissioncontrollerExtensionAtoms;
import com.android.os.providers.mediaprovider.MediaProviderAtoms;
import com.android.os.settings.SettingsExtensionAtoms;
import com.android.os.statsd.ShellDataProto;
import com.android.os.sysui.SysuiAtoms;
import com.android.os.telecom.TelecomExtensionAtom;
import com.android.os.telephony.SatelliteExtensionAtoms;
import com.android.os.telephony.TelephonyExtensionAtoms;
import com.android.os.telephony.qns.QnsExtensionAtoms;
import com.android.os.usb.UsbAtoms;
import com.android.os.uwb.UwbExtensionAtoms;
import com.android.os.view.inputmethod.InputmethodAtoms;
import com.android.os.wear.media.WearMediaAtoms;
import com.android.os.wear.media.WearMediaExtensionAtoms;
import com.android.os.wearpas.WearpasExtensionAtoms;
import com.android.os.wearservices.WearservicesAtoms;
import com.android.os.wearservices.WearservicesExtensionAtoms;
import com.android.os.wearsysui.WearsysuiAtoms;
import com.android.os.wifi.WifiExtensionAtoms;
import android.os.statsd.media.MediaCodecExtensionAtoms;
import com.android.os.credentials.CredentialsExtensionAtoms;
import com.android.os.sdksandbox.SdksandboxExtensionAtoms;

import com.google.protobuf.ExtensionRegistry;

/**
 * CustomExtensionRegistry for local use of statsd.
 */
public class ExtensionAtomsRegistry {

    public static ExtensionRegistry REGISTRY;

    static {
        /** In Java, when parsing a message containing extensions, you must provide an
         * ExtensionRegistry which contains definitions of all of the extensions which you
         * want the parser to recognize. This is necessary because Java's bytecode loading
         * semantics do not provide any way for the protocol buffers library to automatically
         * discover all extensions defined in your binary.
         *
         * See http://sites/protocol-buffers/user-docs/miscellaneous-howtos/extensions
         * #Java_ExtensionRegistry_
         */
        REGISTRY = ExtensionRegistry.newInstance();
        registerAllExtensions(REGISTRY);
        REGISTRY = REGISTRY.getUnmodifiable();
    }

    /**
     * Registers all proto2 extensions.
     */
    private static void registerAllExtensions(ExtensionRegistry extensionRegistry) {
        ExperimentIdsProto.registerAllExtensions(extensionRegistry);
        UidDataProto.registerAllExtensions(extensionRegistry);
        ActiveConfigProto.registerAllExtensions(extensionRegistry);
        ShellConfig.registerAllExtensions(extensionRegistry);
        AdservicesExtensionAtoms.registerAllExtensions(extensionRegistry);
        AutomotiveCaruilibAtoms.registerAllExtensions(extensionRegistry);
        DeviceLogsAtoms.registerAllExtensions(extensionRegistry);
        DndAtoms.registerAllExtensions(extensionRegistry);
        DndExtensionAtoms.registerAllExtensions(extensionRegistry);
        ExpresslogExtensionAtoms.registerAllExtensions(extensionRegistry);
        FrameworkExtensionAtoms.registerAllExtensions(extensionRegistry);
        GpsAtoms.registerAllExtensions(extensionRegistry);
        GrammaticalInflectionExtensionAtoms.registerAllExtensions(extensionRegistry);
        BiometricsAtoms.registerAllExtensions(extensionRegistry);
        ApiExtensionAtoms.registerAllExtensions(extensionRegistry);
        UiExtensionAtoms.registerAllExtensions(extensionRegistry);
        HotwordAtoms.registerAllExtensions(extensionRegistry);
        KernelAtoms.registerAllExtensions(extensionRegistry);
        LocaleAtoms.registerAllExtensions(extensionRegistry);
        LocationAtoms.registerAllExtensions(extensionRegistry);
        LocationExtensionAtoms.registerAllExtensions(extensionRegistry);
        MediaDrmAtoms.registerAllExtensions(extensionRegistry);
        MemorysafetyExtensionAtoms.registerAllExtensions(extensionRegistry);
        PermissioncontrollerExtensionAtoms.registerAllExtensions(extensionRegistry);
        MediaProviderAtoms.registerAllExtensions(extensionRegistry);
        SettingsExtensionAtoms.registerAllExtensions(extensionRegistry);
        ShellDataProto.registerAllExtensions(extensionRegistry);
        SysuiAtoms.registerAllExtensions(extensionRegistry);
        TelecomExtensionAtom.registerAllExtensions(extensionRegistry);
        SatelliteExtensionAtoms.registerAllExtensions(extensionRegistry);
        TelephonyExtensionAtoms.registerAllExtensions(extensionRegistry);
        QnsExtensionAtoms.registerAllExtensions(extensionRegistry);
        UsbAtoms.registerAllExtensions(extensionRegistry);
        UwbExtensionAtoms.registerAllExtensions(extensionRegistry);
        InputmethodAtoms.registerAllExtensions(extensionRegistry);
        WearMediaAtoms.registerAllExtensions(extensionRegistry);
        WearMediaExtensionAtoms.registerAllExtensions(extensionRegistry);
        WearpasExtensionAtoms.registerAllExtensions(extensionRegistry);
        WearservicesAtoms.registerAllExtensions(extensionRegistry);
        WearservicesExtensionAtoms.registerAllExtensions(extensionRegistry);
        WearsysuiAtoms.registerAllExtensions(extensionRegistry);
        WifiExtensionAtoms.registerAllExtensions(extensionRegistry);
        MediaCodecExtensionAtoms.registerAllExtensions(extensionRegistry);
        CredentialsExtensionAtoms.registerAllExtensions(extensionRegistry);
        SdksandboxExtensionAtoms.registerAllExtensions(extensionRegistry);
        ArtExtensionAtoms.registerAllExtensions(extensionRegistry);
    }
}

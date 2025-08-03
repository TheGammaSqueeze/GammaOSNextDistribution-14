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

package com.android.server.uwb.params;

import com.android.server.uwb.UwbInjector;

import com.google.uwb.support.aliro.AliroParams;
import com.google.uwb.support.base.Params;
import com.google.uwb.support.base.ProtocolVersion;
import com.google.uwb.support.ccc.CccParams;
import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.generic.GenericParams;
import com.google.uwb.support.radar.RadarParams;

public abstract class TlvDecoder {
    public static TlvDecoder getDecoder(String protocolName, UwbInjector uwbInjector) {
        if (protocolName.equals(FiraParams.PROTOCOL_NAME)) {
            return new FiraDecoder(uwbInjector);
        }
        if (protocolName.equals(CccParams.PROTOCOL_NAME)) {
            return new CccDecoder(uwbInjector);
        }
        if (protocolName.equals(AliroParams.PROTOCOL_NAME)) {
            return new AliroDecoder(uwbInjector);
        }
        if (protocolName.equals(RadarParams.PROTOCOL_NAME)) {
            return new RadarDecoder();
        }
        if (protocolName.equals(GenericParams.PROTOCOL_NAME)) {
            return new GenericDecoder(uwbInjector);
        }
        return null;
    }

    public abstract <T extends Params> T getParams(TlvDecoderBuffer tlvs, Class<T> paramType,
            ProtocolVersion protocolVersion);
}

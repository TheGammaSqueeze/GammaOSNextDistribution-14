/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.bluetooth.btservice;

import com.android.bluetooth.a2dp.A2dpService;
import com.android.bluetooth.avrcp.AvrcpTargetService;
import com.android.bluetooth.bas.BatteryService;
import com.android.bluetooth.bass_client.BassClientService;
import com.android.bluetooth.csip.CsipSetCoordinatorService;
import com.android.bluetooth.hap.HapClientService;
import com.android.bluetooth.hearingaid.HearingAidService;
import com.android.bluetooth.hfp.HeadsetService;
import com.android.bluetooth.hid.HidDeviceService;
import com.android.bluetooth.hid.HidHostService;
import com.android.bluetooth.le_audio.LeAudioService;
import com.android.bluetooth.mcp.McpService;
import com.android.bluetooth.pan.PanService;
import com.android.bluetooth.tbs.TbsService;
import com.android.bluetooth.vc.VolumeControlService;

// Factory class to create instances of static services. Useful in mocking the service objects.
public class ServiceFactory {
    public A2dpService getA2dpService() {
        return A2dpService.getA2dpService();
    }

    public CsipSetCoordinatorService getCsipSetCoordinatorService() {
        return CsipSetCoordinatorService.getCsipSetCoordinatorService();
    }

    public HeadsetService getHeadsetService() {
        return HeadsetService.getHeadsetService();
    }

    public HidHostService getHidHostService() {
        return HidHostService.getHidHostService();
    }

    public HidDeviceService getHidDeviceService() {
        return HidDeviceService.getHidDeviceService();
    }

    public PanService getPanService() {
        return PanService.getPanService();
    }

    public HearingAidService getHearingAidService() {
        return HearingAidService.getHearingAidService();
    }

    public LeAudioService getLeAudioService() {
        return LeAudioService.getLeAudioService();
    }

    public AvrcpTargetService getAvrcpTargetService() {
        return AvrcpTargetService.get();
    }

    public McpService getMcpService() {
        return McpService.getMcpService();
    }

    public TbsService getTbsService() {
        return TbsService.getTbsService();
    }

    public VolumeControlService getVolumeControlService() {
        return VolumeControlService.getVolumeControlService();
    }

    public HapClientService getHapClientService() {
        return HapClientService.getHapClientService();
    }

    public BassClientService getBassClientService() {
        return BassClientService.getBassClientService();
    }

    public BatteryService getBatteryService() {
        return BatteryService.getBatteryService();
    }
}

/*
 * Copyright (C) 2023 The LineageOS Project
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

package com.android.systemui.lineage

import com.android.systemui.qs.tileimpl.QSTileImpl
import com.android.systemui.qs.tiles.AmbientDisplayTile
import com.android.systemui.qs.tiles.AODTile
import com.android.systemui.qs.tiles.PerformanceTile;
import com.android.systemui.qs.tiles.FanTile;
import com.android.systemui.qs.tiles.ABXYTile;
import com.android.systemui.qs.tiles.AnalogAxisTile;
import com.android.systemui.qs.tiles.AnalogSensitivityTile;
import com.android.systemui.qs.tiles.RightAnalogAxisTile;
import com.android.systemui.qs.tiles.DpadAnalogToggleTile;
import com.android.systemui.qs.tiles.GammaRGBTile;
import com.android.systemui.qs.tiles.DCDimmingEmuTile;
import com.android.systemui.qs.tiles.AnalogDeadzoneTile;
import com.android.systemui.qs.tiles.AnalogCalibrationTile;
import com.android.systemui.qs.tiles.DeepSleepModeTile;
import com.android.systemui.qs.tiles.MappingEditorTile;
import com.android.systemui.qs.tiles.RetroArchMenuButtonOverrideTile;
import com.android.systemui.qs.tiles.USBControllerSwitchTile;
import com.android.systemui.qs.tiles.CaffeineTile
import com.android.systemui.qs.tiles.CellularTile
import com.android.systemui.qs.tiles.HeadsUpTile
import com.android.systemui.qs.tiles.PowerShareTile
import com.android.systemui.qs.tiles.ProfilesTile
import com.android.systemui.qs.tiles.ReadingModeTile
import com.android.systemui.qs.tiles.SyncTile
import com.android.systemui.qs.tiles.UsbTetherTile
import com.android.systemui.qs.tiles.VpnTile
import com.android.systemui.qs.tiles.WifiTile

import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey

@Module
interface LineageModule {
    /** Inject AmbientDisplayTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(AmbientDisplayTile.TILE_SPEC)
    fun bindAmbientDisplayTile(ambientDisplayTile: AmbientDisplayTile): QSTileImpl<*>

    /** Inject AODTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(AODTile.TILE_SPEC)
    fun bindAODTile(aodTile: AODTile): QSTileImpl<*>

    /** Inject PerformanceTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(PerformanceTile.TILE_SPEC)
    fun bindPerformanceTile(performanceTile: PerformanceTile): QSTileImpl<*>
	
    /** Inject FanTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(FanTile.TILE_SPEC)
    fun bindFanTile(fanTile: FanTile): QSTileImpl<*>

    /** Inject ABXYTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(ABXYTile.TILE_SPEC)
    fun bindABXYTile(abxyTile: ABXYTile): QSTileImpl<*>

    /** Inject AnalogAxisTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(AnalogAxisTile.TILE_SPEC)
    fun bindAnalogAxisTile(analogaxisTile: AnalogAxisTile): QSTileImpl<*>

    /** Inject AnalogSensitivityTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(AnalogSensitivityTile.TILE_SPEC)
    fun bindAnalogSensitivityTile(analogsensitivityTile: AnalogSensitivityTile): QSTileImpl<*>

    /** Inject RightAnalogAxisTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(RightAnalogAxisTile.TILE_SPEC)
    fun bindRightAnalogAxisTile(rightanalogaxisTile: RightAnalogAxisTile): QSTileImpl<*>

    /** Inject DpadAnalogToggleTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(DpadAnalogToggleTile.TILE_SPEC)
    fun bindDpadAnalogToggleTile(dpadAnalogToggleTile: DpadAnalogToggleTile): QSTileImpl<*>

    /** Inject GammaRGBTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(GammaRGBTile.TILE_SPEC)
    fun bindGammaRGBTile(gammaRGBTile: GammaRGBTile): QSTileImpl<*>

    /** Inject DCDimmingEmuTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(DCDimmingEmuTile.TILE_SPEC)
    fun bindDCDimmingEmuTile(dcDimmingEmuTile: DCDimmingEmuTile): QSTileImpl<*>

    /** Inject AnalogDeadzoneTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(AnalogDeadzoneTile.TILE_SPEC)
    fun bindAnalogDeadzoneTile(analogDeadzoneTile: AnalogDeadzoneTile): QSTileImpl<*>

    /** Inject AnalogCalibrationTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(AnalogCalibrationTile.TILE_SPEC)
    fun bindAnalogCalibrationTile(analogCalibrationTile: AnalogCalibrationTile): QSTileImpl<*>

    /** Inject DeepSleepModeTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(DeepSleepModeTile.TILE_SPEC)
    fun bindDeepSleepModeTile(deepSleepModeTile: DeepSleepModeTile): QSTileImpl<*>

    /** Inject RetroArchMenuButtonOverrideTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(RetroArchMenuButtonOverrideTile.TILE_SPEC)
    fun bindRetroArchMenuButtonOverrideTile(retroArchMenuButtonOverrideTile: RetroArchMenuButtonOverrideTile): QSTileImpl<*>

    /** Inject USBControllerSwitchTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(USBControllerSwitchTile.TILE_SPEC)
    fun bindUSBControllerSwitchTile(usbControllerSwitchTile: USBControllerSwitchTile): QSTileImpl<*>

    /** Inject MappingEditorTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(MappingEditorTile.TILE_SPEC)
    fun bindMappingEditorTile(mappingEditorTile: MappingEditorTile): QSTileImpl<*>

    /** Inject CaffeineTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(CaffeineTile.TILE_SPEC)
    fun bindCaffeineTile(caffeineTile: CaffeineTile): QSTileImpl<*>

    /** Inject CellularTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(CellularTile.TILE_SPEC)
    fun bindCellularTile(cellularTile: CellularTile): QSTileImpl<*>

    /** Inject HeadsUpTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(HeadsUpTile.TILE_SPEC)
    fun bindHeadsUpTile(headsUpTile: HeadsUpTile): QSTileImpl<*>

    /** Inject PowerShareTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(PowerShareTile.TILE_SPEC)
    fun bindPowerShareTile(powerShareTile: PowerShareTile): QSTileImpl<*>

    /** Inject ProfilesTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(ProfilesTile.TILE_SPEC)
    fun bindProfilesTile(profilesTile: ProfilesTile): QSTileImpl<*>

    /** Inject ReadingModeTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(ReadingModeTile.TILE_SPEC)
    fun bindReadingModeTile(readingModeTile: ReadingModeTile): QSTileImpl<*>

    /** Inject SyncTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(SyncTile.TILE_SPEC)
    fun bindSyncTile(syncTile: SyncTile): QSTileImpl<*>

    /** Inject UsbTetherTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(UsbTetherTile.TILE_SPEC)
    fun bindUsbTetherTile(usbTetherTile: UsbTetherTile): QSTileImpl<*>

    /** Inject VpnTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(VpnTile.TILE_SPEC)
    fun bindVpnTile(vpnTile: VpnTile): QSTileImpl<*>

    /** Inject WifiTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(WifiTile.TILE_SPEC)
    fun bindWifiTile(wifiTile: WifiTile): QSTileImpl<*>
}

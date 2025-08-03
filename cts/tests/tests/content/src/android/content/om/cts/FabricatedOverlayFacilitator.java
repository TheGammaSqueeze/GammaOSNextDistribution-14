/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.content.om.cts;

import android.content.Context;
import android.content.om.FabricatedOverlay;
import android.content.om.OverlayInfo;
import android.content.om.OverlayManager;
import android.content.om.OverlayManagerTransaction;
import android.graphics.Color;
import android.util.TypedValue;

import java.util.List;

/** This class help tests to fill less information. */
public class FabricatedOverlayFacilitator {
    public static final String TARGET_COLOR_RES = "color/target_overlayable_color1";
    public static final String TARGET_STRING_RES = "string/target_overlayable_string1";
    public static final String OVERLAYABLE_NAME = "SelfTargetingOverlayable";

    private final Context mContext;

    public FabricatedOverlayFacilitator(Context context) {
        mContext = context;
    }

    public void removeAllOverlays() throws Exception {
        final OverlayManager overlayManager = mContext.getSystemService(OverlayManager.class);
        final OverlayManagerTransaction transaction = OverlayManagerTransaction.newInstance();
        final List<OverlayInfo> overlayInfoList =
                overlayManager.getOverlayInfosForTarget(mContext.getPackageName());
        for (OverlayInfo overlayInfo : overlayInfoList) {
            transaction.unregisterFabricatedOverlay(overlayInfo.getOverlayIdentifier());
        }
        overlayManager.commit(transaction);
    }

    public FabricatedOverlay prepare(String overlayName, String overlayableName) {
        return prepare(overlayName, overlayableName, false);
    }

    public FabricatedOverlay prepare(String overlayName, String overlayableName,
            boolean isSelfTarget) {
        final String packageName = mContext.getPackageName();
        final FabricatedOverlay overlay =  isSelfTarget
                ? new FabricatedOverlay(overlayName, packageName)
                : new FabricatedOverlay.Builder(packageName, overlayName, packageName).build();
        overlay.setTargetOverlayable(overlayableName);
        overlay.setResourceValue(TARGET_COLOR_RES, TypedValue.TYPE_INT_COLOR_ARGB8, Color.WHITE,
                null /* configuration */);
        overlay.setResourceValue(TARGET_STRING_RES, TypedValue.TYPE_STRING, "HELLO",
                null /* configuration */);
        return overlay;
    }
}

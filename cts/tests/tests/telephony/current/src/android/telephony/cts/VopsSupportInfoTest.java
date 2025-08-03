/*
 * Copyright (C) 2019 The Android Open Source Project
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
package android.telephony.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Parcel;
import android.telephony.LteVopsSupportInfo;
import android.telephony.NrVopsSupportInfo;
import android.telephony.VopsSupportInfo;

import org.junit.Test;

public class VopsSupportInfoTest {

    private static final int[] NR_VOPS_VALUE = {
        NrVopsSupportInfo.NR_STATUS_VOPS_NOT_SUPPORTED,
        NrVopsSupportInfo.NR_STATUS_VOPS_3GPP_SUPPORTED,
        NrVopsSupportInfo.NR_STATUS_VOPS_NON_3GPP_SUPPORTED};
    private static final int[] NR_EMC_VALUE = {
        NrVopsSupportInfo.NR_STATUS_EMC_NOT_SUPPORTED,
        NrVopsSupportInfo.NR_STATUS_EMC_5GCN_ONLY,
        NrVopsSupportInfo.NR_STATUS_EMC_EUTRA_5GCN_ONLY,
        NrVopsSupportInfo.NR_STATUS_EMC_NR_EUTRA_5GCN};
    private static final int[] NR_EMF_VALUE = {
        NrVopsSupportInfo.NR_STATUS_EMF_NOT_SUPPORTED,
        NrVopsSupportInfo.NR_STATUS_EMF_5GCN_ONLY,
        NrVopsSupportInfo.NR_STATUS_EMF_EUTRA_5GCN_ONLY,
        NrVopsSupportInfo.NR_STATUS_EMF_NR_EUTRA_5GCN};

    @Test
    public void testLteVopsSupportInfoApi() {
        VopsSupportInfo vops = new LteVopsSupportInfo(
                LteVopsSupportInfo.LTE_STATUS_NOT_SUPPORTED,
                LteVopsSupportInfo.LTE_STATUS_NOT_SUPPORTED);

        assertEquals(LteVopsSupportInfo.LTE_STATUS_NOT_SUPPORTED,
                ((LteVopsSupportInfo) vops).getVopsSupport());
        assertEquals(LteVopsSupportInfo.LTE_STATUS_NOT_SUPPORTED,
                ((LteVopsSupportInfo) vops).getEmcBearerSupport());
        assertFalse(vops.isVopsSupported());
        assertFalse(vops.isEmergencyServiceSupported());
        assertFalse(vops.isEmergencyServiceFallbackSupported());

        vops = new LteVopsSupportInfo(LteVopsSupportInfo.LTE_STATUS_SUPPORTED,
                LteVopsSupportInfo.LTE_STATUS_SUPPORTED);

        assertEquals(LteVopsSupportInfo.LTE_STATUS_SUPPORTED,
                ((LteVopsSupportInfo) vops).getVopsSupport());
        assertEquals(LteVopsSupportInfo.LTE_STATUS_SUPPORTED,
                ((LteVopsSupportInfo) vops).getEmcBearerSupport());
        assertTrue(vops.isVopsSupported());
        assertTrue(vops.isEmergencyServiceSupported());
        assertFalse(vops.isEmergencyServiceFallbackSupported());
    }

    @Test
    public void testLteVopsSupportInfoParcel() {
        LteVopsSupportInfo vops = new LteVopsSupportInfo(LteVopsSupportInfo.LTE_STATUS_SUPPORTED,
                LteVopsSupportInfo.LTE_STATUS_SUPPORTED);

        Parcel vopsParcel = Parcel.obtain();
        vops.writeToParcel(vopsParcel, 0);
        vopsParcel.setDataPosition(0);
        LteVopsSupportInfo checkLteVops =
                LteVopsSupportInfo.CREATOR.createFromParcel(vopsParcel);

        assertEquals(0, vops.describeContents());
        assertEquals(vops, checkLteVops);
    }

    @Test
    public void testNrVopsSupportInfoApi() {
        for (int i = 0; i < NR_VOPS_VALUE.length; i++) {
            for (int j = 0; j < NR_EMC_VALUE.length; j++) {
                for (int k = 0; k < NR_EMF_VALUE.length; k++) {
                    VopsSupportInfo vops = new NrVopsSupportInfo(NR_VOPS_VALUE[i],
                            NR_EMC_VALUE[j], NR_EMF_VALUE[k]);

                    assertEquals(NR_VOPS_VALUE[i], ((NrVopsSupportInfo) vops).getVopsSupport());
                    assertEquals(NR_EMC_VALUE[j], ((NrVopsSupportInfo) vops).getEmcSupport());
                    assertEquals(NR_EMF_VALUE[k], ((NrVopsSupportInfo) vops).getEmfSupport());
                    assertEquals(isVopsSupportedByNrVopsInfo(NR_VOPS_VALUE[i]),
                            vops.isVopsSupported());
                    assertEquals(isEmcSupportedByNrVopsInfo(NR_EMC_VALUE[j]),
                            vops.isEmergencyServiceSupported());
                    assertEquals(isEmfSupportedByNrVopsInfo(NR_EMF_VALUE[k]),
                            vops.isEmergencyServiceFallbackSupported());
                }
            }
        }
    }

    @Test
    public void testNrVopsSupportInfoParcel() {
        NrVopsSupportInfo vops = new NrVopsSupportInfo(
                NrVopsSupportInfo.NR_STATUS_VOPS_3GPP_SUPPORTED,
                NrVopsSupportInfo.NR_STATUS_EMC_NR_EUTRA_5GCN,
                NrVopsSupportInfo.NR_STATUS_EMF_NR_EUTRA_5GCN);

        Parcel vopsParcel = Parcel.obtain();
        vops.writeToParcel(vopsParcel, 0);
        vopsParcel.setDataPosition(0);
        NrVopsSupportInfo checkNrVops =
                NrVopsSupportInfo.CREATOR.createFromParcel(vopsParcel);

        assertEquals(0, vops.describeContents());
        assertEquals(vops, checkNrVops);
    }

    private boolean isVopsSupportedByNrVopsInfo(int value) {
        return value != NrVopsSupportInfo.NR_STATUS_VOPS_NOT_SUPPORTED;
    }

    private boolean isEmcSupportedByNrVopsInfo(int value) {
        return value != NrVopsSupportInfo.NR_STATUS_EMC_NOT_SUPPORTED;
    }

    private boolean isEmfSupportedByNrVopsInfo(int value) {
        return value != NrVopsSupportInfo.NR_STATUS_EMF_NOT_SUPPORTED;
    }
}

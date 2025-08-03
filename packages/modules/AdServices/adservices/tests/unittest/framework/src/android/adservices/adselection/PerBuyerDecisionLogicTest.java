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

package android.adservices.adselection;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.os.Parcel;

import com.android.adservices.common.SdkLevelSupportRule;

import com.google.common.collect.ImmutableMap;

import org.junit.Rule;
import org.junit.Test;

import java.util.Map;

public class PerBuyerDecisionLogicTest {

    private static final AdTechIdentifier BUYER_1 = CommonFixture.VALID_BUYER_1;
    private static final AdTechIdentifier BUYER_2 = CommonFixture.VALID_BUYER_2;
    private static final String DECISION_LOGIC_JS = "function test() { return \"hello world\"; }";
    private static final DecisionLogic DECISION_LOGIC = new DecisionLogic(DECISION_LOGIC_JS);

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testBuildValidSuccess() {
        PerBuyerDecisionLogic obj =
                new PerBuyerDecisionLogic(
                        ImmutableMap.of(BUYER_1, DECISION_LOGIC, BUYER_2, DECISION_LOGIC));
        assertThat(obj.getPerBuyerLogicMap()).containsEntry(BUYER_1, DECISION_LOGIC);
        assertThat(obj.getPerBuyerLogicMap()).containsEntry(BUYER_2, DECISION_LOGIC);
    }

    @Test
    public void testParcelValid_Success() {
        PerBuyerDecisionLogic valid =
                new PerBuyerDecisionLogic(
                        ImmutableMap.of(BUYER_1, DECISION_LOGIC, BUYER_2, DECISION_LOGIC));

        Parcel p = Parcel.obtain();
        valid.writeToParcel(p, 0);
        p.setDataPosition(0);

        PerBuyerDecisionLogic fromParcel = PerBuyerDecisionLogic.CREATOR.createFromParcel(p);
        Map<AdTechIdentifier, DecisionLogic> mapFromParcel = fromParcel.getPerBuyerLogicMap();
        assertNotNull(mapFromParcel);
        assertThat(mapFromParcel.get(BUYER_1)).isEqualTo(DECISION_LOGIC);
        assertThat(mapFromParcel.get(BUYER_2)).isEqualTo(DECISION_LOGIC);
    }

    @Test
    public void testDescribeContents() {
        PerBuyerDecisionLogic obj = new PerBuyerDecisionLogic(ImmutableMap.of());
        assertEquals(0, obj.describeContents());
    }

    @Test
    public void testDefaultEmpty() {
        PerBuyerDecisionLogic empty = PerBuyerDecisionLogic.EMPTY;
        assertEquals(0, empty.getPerBuyerLogicMap().size());
    }

    @Test
    public void testAssertEquals() {
        PerBuyerDecisionLogic obj =
                new PerBuyerDecisionLogic(
                        ImmutableMap.of(BUYER_1, DECISION_LOGIC, BUYER_2, DECISION_LOGIC));
        PerBuyerDecisionLogic obj2 =
                new PerBuyerDecisionLogic(
                        ImmutableMap.of(BUYER_1, DECISION_LOGIC, BUYER_2, DECISION_LOGIC));
        assertEquals(obj, obj2);
    }
}

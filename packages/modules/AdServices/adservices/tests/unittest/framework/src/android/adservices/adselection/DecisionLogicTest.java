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
import static org.junit.Assert.assertThrows;

import android.os.Parcel;

import androidx.test.filters.SmallTest;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;

@SmallTest
public class DecisionLogicTest {

    private static final String DECISION_LOGIC = "reportWin()";

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testBuildValid_Success() {
        DecisionLogic valid = new DecisionLogic(DECISION_LOGIC);
        assertThat(valid.getLogic()).isEqualTo(DECISION_LOGIC);
    }

    @Test
    public void testParcelValid_Success() {
        DecisionLogic valid = new DecisionLogic(DECISION_LOGIC);

        Parcel p = Parcel.obtain();
        valid.writeToParcel(p, 0);
        p.setDataPosition(0);

        DecisionLogic fromParcel = DecisionLogic.CREATOR.createFromParcel(p);
        assertThat(fromParcel.getLogic()).isEqualTo(DECISION_LOGIC);
    }

    @Test
    public void testBuildWith_NullDecisionLogic_Failure() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    new DecisionLogic(null);
                });
    }

    @Test
    public void testDescribeContents() {
        DecisionLogic obj = new DecisionLogic(DECISION_LOGIC);
        assertEquals(0, obj.describeContents());
    }
}

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

package android.net.ipsec.ike.ike3gpp;

import static org.junit.Assert.assertEquals;

import android.net.ipsec.ike.ike3gpp.Ike3gppExtension.Ike3gppDataListener;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Ike3gppDataListenerTest {
    @Test
    public void testOnIke3gppDataReceived() {
        List<Ike3gppData> ike3gppData =
                Arrays.asList(Ike3gppN1ModeInformationTest.newN1ModeInformation());

        TestIke3gppDataListener dataListener = new TestIke3gppDataListener();
        dataListener.onIke3gppDataReceived(ike3gppData);

        assertEquals(ike3gppData, dataListener.lastDataList);
    }

    public static class TestIke3gppDataListener implements Ike3gppDataListener {
        public final List<Ike3gppData> lastDataList = new ArrayList<>();

        @Override
        public void onIke3gppDataReceived(List<Ike3gppData> ike3gppDataList) {
            lastDataList.clear();
            lastDataList.addAll(ike3gppDataList);
        }
    }
}

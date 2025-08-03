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
package android.security.rkp.service.test;

import static com.google.common.truth.Truth.assertThat;

import android.security.rkp.service.RkpProxyException;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class RkpProxyExceptionTest {
    @Test
    public void testGetError() {
        for (int error: new int[] {0, 1, 2, 3}) {
            assertThat(new RkpProxyException(error, "").getError()).isEqualTo(error);
        }
    }

    @Test
    public void testGetMessage() {
        Exception unknown = new RkpProxyException(0, "oh no");
        assertThat(unknown).hasMessageThat().contains("ERROR_UNKNOWN");
        assertThat(unknown).hasMessageThat().contains("oh no");

        Exception permanent = new RkpProxyException(3, "fail");
        assertThat(permanent).hasMessageThat().contains("ERROR_PERMANENT");
        assertThat(permanent).hasMessageThat().contains("fail");
    }

    @Test
    public void testGetCause() {
        Exception e = new RkpProxyException(
                RkpProxyException.ERROR_UNKNOWN, "", new Exception("nope"));
        assertThat(e).hasCauseThat().hasMessageThat().isEqualTo("nope");
    }

    @Test
    public void testGetCauseNull() {
        Exception e = new RkpProxyException(RkpProxyException.ERROR_UNKNOWN, "");
        assertThat(e.getCause()).isNull();
    }
}


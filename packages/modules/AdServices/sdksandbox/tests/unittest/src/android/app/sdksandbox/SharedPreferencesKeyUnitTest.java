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

package android.app.sdksandbox;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests {@link SharedPreferencesKey} APIs. */
@RunWith(JUnit4.class)
public class SharedPreferencesKeyUnitTest {

    @Test
    public void testSharedPreferencesKey_DescribeContents() throws Exception {
        final SharedPreferencesKey keyWithType =
                new SharedPreferencesKey("key", SharedPreferencesKey.KEY_TYPE_LONG);
        assertThat(keyWithType.describeContents()).isEqualTo(0);
    }

    @Test
    public void testSharedPreferencesKey_GetName() throws Exception {
        final SharedPreferencesKey keyWithType =
                new SharedPreferencesKey("key", SharedPreferencesKey.KEY_TYPE_LONG);
        assertThat(keyWithType.getName()).isEqualTo("key");
    }

    @Test
    public void testSharedPreferencesKey_GetType() throws Exception {
        final SharedPreferencesKey keyWithType =
                new SharedPreferencesKey("key", SharedPreferencesKey.KEY_TYPE_LONG);
        assertThat(keyWithType.getType()).isEqualTo(SharedPreferencesKey.KEY_TYPE_LONG);
    }

    @Test
    public void testSharedPreferencesKey_IsParcelable() throws Exception {
        final SharedPreferencesKey keyWithType =
                new SharedPreferencesKey("key", SharedPreferencesKey.KEY_TYPE_LONG);

        final Parcel p = Parcel.obtain();
        keyWithType.writeToParcel(p, /*flags=*/ 0);

        // Create SharedPreferencesKey with the same parcel
        p.setDataPosition(0); // rewind
        final SharedPreferencesKey newSharedPreferencesKey =
                SharedPreferencesKey.CREATOR.createFromParcel(p);

        assertThat(newSharedPreferencesKey.getName()).isEqualTo(keyWithType.getName());
        assertThat(newSharedPreferencesKey.getType()).isEqualTo(keyWithType.getType());
    }
}

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

import android.os.Bundle;
import android.os.Parcel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;

/** Tests {@link SharedPreferencesUpdate} APIs. */
@RunWith(JUnit4.class)
public class SharedPreferencesUpdateUnitTest {

    private static final String KEY_LONG = "long";
    private static final String KEY_STRING = "string";
    private static final long VALUE_LONG = 1L;
    private static final String VALUE_STRING = "value";

    private static final List<SharedPreferencesKey> KEYS_TO_SYNC =
            List.of(
                    new SharedPreferencesKey(KEY_LONG, SharedPreferencesKey.KEY_TYPE_LONG),
                    new SharedPreferencesKey(KEY_STRING, SharedPreferencesKey.KEY_TYPE_STRING));

    @Test
    public void testSharedPreferencesUpdate_DescribeContents() throws Exception {
        final SharedPreferencesUpdate update =
                new SharedPreferencesUpdate(KEYS_TO_SYNC, getTestBundle());
        assertThat(update.describeContents()).isEqualTo(0);
    }

    @Test
    public void testSharedPreferencesUpdate_IsParcelable() throws Exception {
        final SharedPreferencesUpdate update =
                new SharedPreferencesUpdate(KEYS_TO_SYNC, getTestBundle());

        final Parcel p = Parcel.obtain();
        update.writeToParcel(p, /*flags=*/ 0);

        // Create SharedPreferencesUpdate with the same parcel
        p.setDataPosition(0); // rewind
        final SharedPreferencesUpdate newUpdate =
                SharedPreferencesUpdate.CREATOR.createFromParcel(p);

        // Verify the two objects have same value
        assertThat(newUpdate.getKeysInUpdate()).containsExactlyElementsIn(update.getKeysInUpdate());
        assertThat(newUpdate.getData().getString(KEY_STRING)).isEqualTo(VALUE_STRING);
        assertThat(newUpdate.getData().getLong(KEY_LONG)).isEqualTo(VALUE_LONG);
    }

    Bundle getTestBundle() {
        final Bundle data = new Bundle();
        data.putLong(KEY_LONG, VALUE_LONG);
        data.putString(KEY_STRING, VALUE_STRING);
        return data;
    }
}

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
package com.android.car.internal.util;

import static com.google.common.truth.Truth.assertThat;

import android.os.IBinder;
import android.os.IInterface;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public final class BinderKeyValueContainerTest {

    private final String mKey1 = "key1";
    private final TestBinderInterface mInterface1 = new TestBinderInterface();
    private final String mKey2 = "key2";
    private final TestBinderInterface mInterface2 = new TestBinderInterface();
    private final BinderKeyValueContainer<String, TestBinderInterface> mContainer =
            new BinderKeyValueContainer<>();

    private String mDeadKey;

    @Test
    public void testBasicFunctions() {
        assertThat(mContainer.size()).isEqualTo(0);

        // Put key1.
        mContainer.put(mKey1, mInterface1);
        assertThat(mContainer.get(mKey1)).isEqualTo(mInterface1);
        assertThat(mContainer.get(mKey2)).isNull();
        assertThat(mContainer.size()).isEqualTo(1);
        assertThat(mContainer.containsKey(mKey1)).isTrue();
        assertThat(mContainer.containsKey(mKey2)).isFalse();

        // Put key2.
        mContainer.put(mKey2, mInterface2);
        assertThat(mContainer.get(mKey2)).isEqualTo(mInterface2);
        assertThat(mContainer.size()).isEqualTo(2);

        List<String> keys = new ArrayList<>(Arrays.asList(mKey1, mKey2));
        List<TestBinderInterface> values = new ArrayList<>(Arrays.asList(mInterface1, mInterface2));
        for (int i = 0; i < mContainer.size(); i++) {
            String key = mContainer.keyAt(i);
            TestBinderInterface value = mContainer.valueAt(i);
            assertThat(keys.contains(key)).isTrue();
            assertThat(values.contains(value)).isTrue();
            assertThat(mContainer.get(key)).isEqualTo(value);
        }

        Set<String> keySet = mContainer.keySet();
        assertThat(keySet.size()).isEqualTo(2);
        assertThat(keySet.contains(mKey1)).isTrue();
        assertThat(keySet.contains(mKey2)).isTrue();

        // Remove key1.
        mContainer.remove(mKey1);
        assertThat(mContainer.get(mKey1)).isNull();
        assertThat(mContainer.size()).isEqualTo(1);
        assertThat(!mContainer.containsKey(mKey1)).isTrue();

        // Remove the item at index 0 (key2).
        mContainer.removeAt(0);
        assertThat(mContainer.size()).isEqualTo(0);
    }

    @Test
    public void testBinderDied() {
        mContainer.setBinderDeathCallback(deadKey -> mDeadKey = deadKey);
        mContainer.put(mKey1, mInterface1);
        mContainer.put(mKey2, mInterface2);
        mInterface1.die();

        assertThat(mContainer.size()).isEqualTo(1);
        assertThat(mContainer.containsKey(mKey1)).isFalse();
        assertThat(mContainer.containsKey(mKey2)).isTrue();
        assertThat(mDeadKey).isEqualTo(mKey1);
    }

    private static final class TestBinderInterface extends android.os.Binder implements IInterface {

        private DeathRecipient mRecipient;

        @Override
        public void linkToDeath(DeathRecipient recipient, int flags) {
            // In any situation, a single binder object should only have at most one death
            // recipient.
            assertThat(mRecipient).isNull();

            mRecipient = recipient;
        }

        @Override
        public boolean unlinkToDeath(DeathRecipient recipient, int flags) {
            assertThat(mRecipient).isSameInstanceAs(recipient);
            mRecipient = null;
            return true;
        }

        @Override
        public IBinder asBinder() {
            return this;
        }

        private void die() {
            if (mRecipient != null) {
                mRecipient.binderDied(this);
            }
            mRecipient = null;
        }
    }
}

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

package com.android.ondevicepersonalization.services.federatedcompute;

import android.adservices.ondevicepersonalization.TrainingExampleRecord;

import java.util.List;

/** Factory for creating iterators */
public class OdpExampleStoreIteratorFactory {
    private static volatile OdpExampleStoreIteratorFactory sSingleton;

    private OdpExampleStoreIteratorFactory() {}

    /** Returns an instance of OdpExampleStoreIteratorFactory */
    public static OdpExampleStoreIteratorFactory getInstance() {
        if (null == sSingleton) {
            synchronized (OdpExampleStoreIteratorFactory.class) {
                if (null == sSingleton) {
                    sSingleton = new OdpExampleStoreIteratorFactory();
                }
            }
        }
        return sSingleton;
    }

    /** Creates an OdpExampleStoreIterator */
    public OdpExampleStoreIterator createIterator(List<TrainingExampleRecord> exampleRecordList) {
        return new OdpExampleStoreIterator(exampleRecordList);
    }
}

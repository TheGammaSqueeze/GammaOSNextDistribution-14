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

package com.android.car.internal.property;

import com.android.car.internal.property.GetSetValueResultList;

/**
 * Callback interface for async {@link CarPropertyService#getPropertiesAsync} when successful.
 *
 * @hide
 */
oneway interface IAsyncPropertyResultCallback {
    /**
     * Method called when {@link com.android.car.getPropertiesAsync} return results.
     */
    void onGetValueResults(in GetSetValueResultList getValueResults);

    /**
     * Method called when {@link com.android.car.setPropertiesAsync} return results.
     *
     * If the result is successful, it means the property is updated successfully. If the result
     * is not successful, it means either the request fails to get through or the property is not
     * updated to the target value within timeout.
     */
    void onSetValueResults(in GetSetValueResultList setValueResults);
}

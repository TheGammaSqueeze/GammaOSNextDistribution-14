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

package com.android.systemui.car.hvac;

/**
 * An interface that enables {@link HvacView}s to set new HVAC properties.
 */
public interface HvacPropertySetter {

    /**
     * Sets an {@link HvacController.HvacProperty} of integer type for a given {@link
     * HvacController.AreaId}.
     */
    void setHvacProperty(@HvacController.HvacProperty Integer propertyId, int areaId, int val);

    /**
     * Sets an {@link HvacController.HvacProperty} of float type for a given {@link
     * HvacController.AreaId}.
     */
    void setHvacProperty(@HvacController.HvacProperty Integer propertyId, int areaId, float val);

    /**
     * Sets an {@link HvacController.HvacProperty} of boolean type for a given {@link
     * HvacController.AreaId}.
     */
    void setHvacProperty(@HvacController.HvacProperty Integer propertyId, int areaId, boolean val);
}

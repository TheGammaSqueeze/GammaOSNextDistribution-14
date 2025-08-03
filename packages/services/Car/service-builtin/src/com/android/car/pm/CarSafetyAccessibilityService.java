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

package com.android.car.pm;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

import com.android.car.UpdatablePackageContext;
import com.android.car.UpdatablePackageDependency;
import com.android.car.internal.CarSafetyAccessibilityServiceImplBase;

import java.lang.reflect.Constructor;

/** Proxy for CarSafetyAccessibilityServiceImpl */
public class CarSafetyAccessibilityService extends AccessibilityService {
    private CarSafetyAccessibilityServiceImplBase mImpl;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        mImpl.onAccessibilityEvent(event);
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onCreate() {
        UpdatablePackageContext updatablePackageContext = UpdatablePackageContext.create(this);
        try {
            Class implClass = updatablePackageContext.getClassLoader().loadClass(
                    UpdatablePackageDependency.CAR_ACCESSIBILITY_IMPL_CLASS);
            // Use default constructor always
            Constructor constructor = implClass.getConstructor();
            mImpl = (CarSafetyAccessibilityServiceImplBase) constructor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Cannot load impl class", e);
        }
    }
}

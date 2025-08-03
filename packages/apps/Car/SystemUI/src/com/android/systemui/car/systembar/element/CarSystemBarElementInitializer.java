/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.car.systembar.element;

import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.android.systemui.dagger.SysUISingleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Provider;

/** Helper class for retrieving and initializing CarSystemBarElements and their controllers. */
@SysUISingleton
public class CarSystemBarElementInitializer {
    private static final String TAG = CarSystemBarElementInitializer.class.getSimpleName();

    private final Map<Class<?>, Provider<CarSystemBarElementController.Factory>>
            mElementControllerFactories;

    /** Convert a class string to a class instance of CarSystemBarElementController */
    public static Class<?> getElementControllerClassFromString(String str) {
        if (!TextUtils.isEmpty(str)) {
            try {
                Class<?> clazz = Class.forName(str);
                if (clazz != null && CarSystemBarElementController.class.isAssignableFrom(clazz)) {
                    return clazz;
                }
            } catch (ClassNotFoundException e) {
                Log.w(TAG, "cannot find class for string " + str, e);
            }
        }
        return null;
    }

    @Inject
    public CarSystemBarElementInitializer(
            Map<Class<?>, Provider<CarSystemBarElementController.Factory>> factories) {
        mElementControllerFactories = factories;
    }

    /** Instantiate all CarSystemBarElements found within the provided rootView */
    public List<CarSystemBarElementController> initializeCarSystemBarElements(
            ViewGroup rootView) {
        List<ElementViewControllerData> elementData = findSystemBarElements(rootView);
        List<CarSystemBarElementController> controllers = new ArrayList<>();
        for (ElementViewControllerData element : elementData) {
            if (element.getControllerClass() != null) {
                Provider<CarSystemBarElementController.Factory> factoryProvider =
                        mElementControllerFactories.get(element.getControllerClass());
                if (factoryProvider == null) {
                    Log.d(TAG, "cannot find factory provider for class "
                            + element.getControllerClass());
                    continue;
                }
                CarSystemBarElementController.Factory factory = factoryProvider.get();
                if (factory == null) {
                    Log.d(TAG, "cannot find factory for class " + element.getControllerClass());
                    continue;
                }
                CarSystemBarElementController controller = factory.create(element.getView());
                controller.init();
                controllers.add(controller);
            }
        }
        return controllers;
    }

    // Returns information as a pair of (View, ElementControllerClass)
    private static List<ElementViewControllerData> findSystemBarElements(ViewGroup rootView) {
        List<ElementViewControllerData> info = new ArrayList<>();
        for (int i = 0; i < rootView.getChildCount(); i++) {
            View v = rootView.getChildAt(i);
            if (v instanceof CarSystemBarElement) {
                info.add(new ElementViewControllerData(v,
                        ((CarSystemBarElement) v).getElementControllerClass()));
            }
            if (v instanceof ViewGroup) {
                info.addAll(findSystemBarElements((ViewGroup) v));
            }
        }
        return info;
    }

    private static final class ElementViewControllerData {
        private final View mView;
        private final Class<?> mControllerClass;

        ElementViewControllerData(View view, Class<?> clazz) {
            mView = view;
            mControllerClass = clazz;
        }

        View getView() {
            return mView;
        }

        Class<?> getControllerClass() {
            return mControllerClass;
        }
    }
}

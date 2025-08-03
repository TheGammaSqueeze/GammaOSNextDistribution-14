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

package com.android.interactive;

import java.lang.reflect.InvocationTargetException;

/**
 * Wrapper around a dynamically loaded {@link Automation}.
 *
 * <p>This is used to avoid having multiple places need to deal with the reflection involved in
 * dynamic loading of automations.
 */
public class AutomationExecutor<E> implements Automation<E> {

    private final Object mObject;

    public AutomationExecutor(Object o) {
        mObject = o;
    }

    /**
     * Call the {@link Automation#automate()} method on the wrapped {@link Automation}.
     */
    public E automate() throws Exception {
        try {
            return (E) mObject.getClass().getMethod("automate").invoke(mObject);
        } catch (InvocationTargetException e) {
            // This can only be an exception because "automate" throws Exception
            throw (Exception) e.getCause();
        } catch (IllegalAccessException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}

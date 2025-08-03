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

package android.car.cts.powerpolicy;

import com.android.tradefed.log.LogUtil.CLog;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Function;

public final class SystemInfoParser<T> implements Function<String, T> {
    private final Class<T> mType;

    public SystemInfoParser(Class<T> type) {
        mType = type;
    }

    public T apply(String cmdOutput) {
        T t = null;
        try {
            Method m = mType.getMethod("parse", String.class);
            Object ret = m.invoke(null, cmdOutput);
            t = mType.cast(ret);
        } catch (InvocationTargetException e) {
            CLog.wtf("ERROR: Apply failed with " + cmdOutput, e.getTargetException());
        } catch (Exception e) {
            CLog.wtf("ERROR: Apply failed with " + cmdOutput, e);
        }
        return t;
    }
}

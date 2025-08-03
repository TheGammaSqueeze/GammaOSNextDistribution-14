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

package android.content.res;

import java.util.Map;

/** Stub class to compile the linter for host execution. */
public final class Resources {
    /** Constant used in the Safety Center config parser. */
    public static final int ID_NULL = 0;

    private static final String STRING_TYPE = "string";

    private final String mPackageName;
    private final Map<String, Integer> mNameToIndex;
    private final Map<Integer, String> mIndexToValue;

    /** Class used in the Safety Center config parser. */
    public Resources(
            String packageName,
            Map<String, Integer> nameToIndex,
            Map<Integer, String> indexToValue) {
        mPackageName = packageName;
        mNameToIndex = nameToIndex;
        mIndexToValue = indexToValue;
    }

    /** This exception is thrown by the resource APIs when a requested resource can not be found. */
    public static final class NotFoundException extends RuntimeException {
        public NotFoundException() {}
    }

    /** Method used in the Safety Center config parser. */
    public int getIdentifier(String name, String defType, String defPackage) {
        if (!mPackageName.equals(defPackage)
                || !STRING_TYPE.equals(defType)
                || !mNameToIndex.containsKey(name)) {
            return ID_NULL;
        }
        return mNameToIndex.get(name);
    }

    /** Method used in the Safety Center config parser. */
    public String getString(int id) {
        if (mIndexToValue.containsKey(id)) {
            return mIndexToValue.get(id);
        }
        throw new NotFoundException();
    }
}

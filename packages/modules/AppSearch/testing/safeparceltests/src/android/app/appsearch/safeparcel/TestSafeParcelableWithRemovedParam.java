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

package android.app.appsearch.safeparcel;

import android.os.Parcel;

@SafeParcelable.Class(creator = "TestSafeParcelableWithRemovedParamCreator")
@SafeParcelable.Reserved({1})
public class TestSafeParcelableWithRemovedParam extends AbstractSafeParcelable {

    public static final Creator<TestSafeParcelableWithRemovedParam> CREATOR =
            new TestSafeParcelableWithRemovedParamCreator();

    @Constructor
    public TestSafeParcelableWithRemovedParam(
            @RemovedParam(id = 1, defaultValue = "-1") String oldVal, @Param(id = 2) int newVal) {
        if (newVal == -1) {
            newVal = Integer.parseInt(oldVal);
        }
        newField = newVal;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        TestSafeParcelableWithRemovedParamCreator.writeToParcel(this, out, flags);
    }

    @Field(id = 2, defaultValue = "-1")
    public final int newField;
}

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

package android.os;

import java.util.ArrayList;
import java.util.List;

/** Stub interface to compile the linter for host execution. */
public interface Parcel {
    /** Method used in the Safety Center config data structures. */
    <T> ArrayList<T> createTypedArrayList(Parcelable.Creator<T> creator);
    /** Method used in the Safety Center config data structures. */
    boolean readBoolean();
    /** Method used in the Safety Center config data structures. */
    int readInt();
    /** Method used in the Safety Center config data structures. */
    String readString();
    /** Method used in the Safety Center config data structures. */
    ArrayList<String> createStringArrayList();

    /** Method used in the Safety Center config data structures. */
    void writeBoolean(boolean value);
    /** Method used in the Safety Center config data structures. */
    void writeInt(int value);
    /** Method used in the Safety Center config data structures. */
    void writeString(String value);
    /** Method used in the Safety Center config data structures. */
    void writeStringList(List<String> value);
    /** Method used in the Safety Center config data structures. */
    <T extends Parcelable> void writeTypedList(List<T> value);
}

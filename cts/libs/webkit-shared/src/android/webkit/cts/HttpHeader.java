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

package android.webkit.cts;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * This class attempts to match the Apache HttpRequest as close as possible so that it can be used
 * in place of it. The apache HttpRequest is not parcelable.
 */
public class HttpHeader implements Parcelable {
    private final String mHeader;
    private final String mValue;

    public static final Parcelable.Creator<HttpHeader> CREATOR =
            new Parcelable.Creator<HttpHeader>() {
                public HttpHeader createFromParcel(Parcel in) {
                    return new HttpHeader(in);
                }

                public HttpHeader[] newArray(int size) {
                    return new HttpHeader[size];
                }
            };

    /** Create a new HttpHeader from a header name and value string. */
    public static HttpHeader create(String header, String value) {
        return new HttpHeader(header, value);
    }

    /** Convert a list of HttpHeaders to a List of pairs to be used with CtsTestServer. */
    public static List<Pair<String, String>> asPairList(List<HttpHeader> headers) {
        List<Pair<String, String>> pairList = new ArrayList<>();
        if (headers != null) {
            for (HttpHeader header : headers) {
                pairList.add(header.getPair());
            }
        }
        return pairList;
    }

    HttpHeader(Parcel in) {
        // Note: This must be read in the same order we write
        // to the parcel in {@link #wroteToParcel(Parcel out, int flags)}.
        this(in.readString(), in.readString());
    }

    HttpHeader(String header, String value) {
        mHeader = header;
        mValue = value;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        // Note: This must be written in the same order we read
        // from the parcel in {@link #HttpRequest(Parcel in)}.
        out.writeString(mHeader);
        out.writeString(mValue);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    private Pair<String, String> getPair() {
        return Pair.create(mHeader, mValue);
    }
}

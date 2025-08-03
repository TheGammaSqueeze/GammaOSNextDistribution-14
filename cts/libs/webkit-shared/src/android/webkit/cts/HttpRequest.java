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

package android.webkit.cts;

import static org.junit.Assert.fail;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

/**
 * This class attempts to match the Apache HttpRequest as close as possible so that it can be used
 * in place of it. The apache HttpRequest is not parcelable.
 */
public class HttpRequest implements Parcelable {
    private final Locale mLowerCaseLocale = Locale.forLanguageTag("en-US");
    private Bundle mHeaders;
    private String mUrl;
    private String mMethod;
    private String mBody;

    public static final Parcelable.Creator<HttpRequest> CREATOR =
            new Parcelable.Creator<HttpRequest>() {
                public HttpRequest createFromParcel(Parcel in) {
                    return new HttpRequest(in);
                }

                public HttpRequest[] newArray(int size) {
                    return new HttpRequest[size];
                }
            };

    public HttpRequest(String url, org.apache.http.HttpRequest apacheRequest) {
        mHeaders = new Bundle();
        mUrl = url;
        mMethod = apacheRequest.getRequestLine().getMethod();
        mBody = null;

        try {
            if (apacheRequest instanceof HttpEntityEnclosingRequest) {
                HttpEntity entity = ((HttpEntityEnclosingRequest) apacheRequest).getEntity();
                mBody = EntityUtils.toString(entity);
            }
        } catch (IOException err) {
            fail("Failed to request request body");
        }

        for (Header header : apacheRequest.getAllHeaders()) {
            ArrayList<String> headerValues = mHeaders.getStringArrayList(header.getName());
            if (headerValues == null) {
                headerValues = new ArrayList<String>();
            }

            headerValues.add(header.getValue());
            // Http Headers are case insensitive so storing all headers as lowercase.
            // Lowercasing by locale to avoid the infamous turkish locale bug.
            mHeaders.putStringArrayList(
                    header.getName().toLowerCase(mLowerCaseLocale), headerValues);
        }
    }

    HttpRequest(Parcel in) {
        // Note: This must be read in the same order we write
        // to the parcel in {@link #wroteToParcel(Parcel out, int flags)}.
        mHeaders = in.readBundle();
        mUrl = in.readString();
        mMethod = in.readString();
        mBody = in.readString();
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        // Note: This must be written in the same order we read
        // from the parcel in {@link #HttpRequest(Parcel in)}.
        out.writeBundle(mHeaders);
        out.writeString(mUrl);
        out.writeString(mMethod);
        out.writeString(mBody);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /** Retrieve all the headers for the URL request matching a header string. */
    public String[] getHeaders(String header) {
        ArrayList<String> headers =
                mHeaders.getStringArrayList(header.toLowerCase(mLowerCaseLocale));
        if (headers != null) {
            return headers.toArray(new String[] {});
        }
        return new String[] {};
    }

    /** Returns the url of the request. */
    public String getUrl() {
        return mUrl;
    }

    /** Returns the method of the request. */
    public String getMethod() {
        return mMethod;
    }

    /** Returns the body of the request. */
    public String getBody() {
        return mBody;
    }
}

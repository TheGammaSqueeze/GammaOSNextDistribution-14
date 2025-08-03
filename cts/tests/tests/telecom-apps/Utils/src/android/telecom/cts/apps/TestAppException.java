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

package android.telecom.cts.apps;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public class TestAppException extends RuntimeException implements Parcelable {
    private String mPackageName;
    private final List<String> mStackTrace;
    private String mMessage;

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(mPackageName);
        dest.writeStringList(new ArrayList<>(mStackTrace));
        dest.writeString(mMessage);
    }

    public static final Parcelable.Creator<TestAppException> CREATOR = new Parcelable.Creator<>() {
        @Override
        public TestAppException createFromParcel(Parcel source) {
            String packageName = source.readString();
            List<String> stackTrace = new ArrayList<>();
            source.readStringList(stackTrace);
            String message = source.readString();
            return new TestAppException(packageName, stackTrace, message);
        }

        @Override
        public TestAppException[] newArray(int size) {
            return new TestAppException[size];
        }
    };

    public TestAppException(
            String packageName,
            List<String> stackTrace,
            String message) {
        super(getMessage(packageName, stackTrace, message));
        mPackageName = packageName;
        mStackTrace = stackTrace;
        mMessage = message;
    }

    private static String getMessage(String packageName, List<String> stackTrace,
            String message) {
        StringBuilder builder;
        if (!TextUtils.isEmpty(message)) {
            builder = new StringBuilder(message);

            builder.append(" ; Process=[");
            builder.append(packageName);
            builder.append("]");
            builder.append("\n");

            for (int i = stackTrace.size() - 1; i >= 0; i--) {
                builder.append("\t");
                builder.append("at ");
                builder.append(stackTrace.get(i));
                if (i != 0) {
                    builder.append("\n");
                }
            }
            return builder.toString();
        } else {
            return "message is empty for TestAppException";
        }
    }
}

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

package com.android.car.telemetry.util;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.car.builtin.util.Slogf;
import android.os.PersistableBundle;

import com.android.car.CarLog;
import com.android.car.telemetry.MetricsReportProto.MetricsReportContainer;
import com.android.car.telemetry.MetricsReportProto.MetricsReportList;

import com.google.protobuf.ByteString;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Utility class for working with {@link com.android.car.telemetry.MetricsReportProto}. */
public final class MetricsReportProtoUtils {

    private MetricsReportProtoUtils() { }

    /**
     * Serialize a PersistableBundle into bytes. If conversion failed, return null.
     */
    @Nullable
    public static byte[] getBytes(PersistableBundle bundle) {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            bundle.writeToStream(byteArrayOutputStream);
            return byteArrayOutputStream.toByteArray();
        } catch (IOException e) {
            Slogf.w(CarLog.TAG_TELEMETRY, "Serializing PersistableBundle failed.", e);
        }
        return null;
    }

    /**
     * Serialize a PersistableBundle into ByteString, which is an immutable byte array.
     * ByteString is the corresponding Java type for {@code bytes} in protobuf.
     */
    @Nullable
    public static ByteString getByteString(PersistableBundle bundle) {
        byte[] bytes = getBytes(bundle);
        return bytes == null ? null : ByteString.copyFrom(bytes);
    }

    /**
     * Deserialize the ByteString into a PersistableBundle. Returns null if failed.
     */
    @Nullable
    public static PersistableBundle getBundle(ByteString byteString) {
        byte[] bytes = byteString.toByteArray();
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes)) {
            return PersistableBundle.readFromStream(bis);
        } catch (IOException e) {
            Slogf.w(CarLog.TAG_TELEMETRY, "Deserializing bytes into PersistableBundle failed.", e);
        }
        return null;
    }

    /**
     * Reads a PersistableBundle from the given MetricsReportList at the given index.
     */
    @Nullable
    public static PersistableBundle getBundle(MetricsReportList reportList, int index) {
        if (index >= reportList.getReportCount()) {
            return null;
        }
        return getBundle(reportList.getReport(index).getReportBytes());
    }

    /**
     * Constructs a MetricsReportList from bundles. All bundles are marked as not the last report.
     */
    @NonNull
    public static MetricsReportList buildMetricsReportList(PersistableBundle... bundles) {
        MetricsReportList.Builder reportListBuilder = MetricsReportList.newBuilder();
        for (PersistableBundle bundle : bundles) {
            MetricsReportContainer reportContainer = MetricsReportContainer.newBuilder()
                    .setReportBytes(getByteString(bundle))
                    .build();
            reportListBuilder.addReport(reportContainer);
        }
        return reportListBuilder.build();
    }
}

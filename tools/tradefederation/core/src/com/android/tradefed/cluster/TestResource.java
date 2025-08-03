/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tradefed.cluster;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** A class to model a TestResource message returned by TFC API. */
public class TestResource {

    private static class TestResourceParameters {
        private final List<String> mDecompressFiles;

        TestResourceParameters(List<String> decompressFiles) {
            mDecompressFiles = decompressFiles != null ? decompressFiles : new ArrayList<>();
        }

        JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("decompress_files", new JSONArray(mDecompressFiles));
            return json;
        }

        static TestResourceParameters fromJson(JSONObject json) {
            List<String> decompressFiles = new ArrayList<>();
            if (json != null) {
                JSONArray jsonDecompressFiles = json.optJSONArray("decompress_files");
                if (jsonDecompressFiles != null) {
                    for (int i = 0; i < jsonDecompressFiles.length(); i++) {
                        decompressFiles.add(jsonDecompressFiles.optString(i));
                    }
                }
            }
            return new TestResourceParameters(decompressFiles);
        }
    }

    private final String mName;
    private final String mUrl;
    private final boolean mDecompress;
    private final String mDecompressDir;
    private final boolean mMountZip;
    private final TestResourceParameters mParams;

    TestResource(String name, String url) {
        this(name, url, false, null, false, (List<String>) null);
    }

    TestResource(
            String name,
            String url,
            boolean decompress,
            String decompressDir,
            boolean mountZip,
            List<String> decompressFiles) {
        this(
                name,
                url,
                decompress,
                decompressDir,
                mountZip,
                new TestResourceParameters(decompressFiles));
    }

    private TestResource(
            String name,
            String url,
            boolean decompress,
            String decompressDir,
            boolean mountZip,
            TestResourceParameters params) {
        mName = name;
        mUrl = url;
        mDecompress = decompress;
        mDecompressDir = decompressDir != null ? decompressDir : "";
        mMountZip = mountZip;
        mParams = params;
    }

    public String getName() {
        return mName;
    }

    public String getUrl() {
        return mUrl;
    }

    public boolean getDecompress() {
        return mDecompress;
    }

    public String getDecompressDir() {
        return mDecompressDir;
    }

    public File getDecompressDir(File parentDir) {
        return new File(parentDir, mDecompressDir);
    }

    public File getFile(File parentDir) {
        return new File(parentDir, mName);
    }

    public boolean mountZip() {
        return mMountZip;
    }

    public List<String> getDecompressFiles() {
        return Collections.unmodifiableList(mParams.mDecompressFiles);
    }

    public JSONObject toJson() throws JSONException {
        final JSONObject json = new JSONObject();
        json.put("name", mName);
        json.put("url", mUrl);
        json.put("decompress", mDecompress);
        json.put("decompress_dir", mDecompressDir);
        json.put("mount_zip", mMountZip);
        json.put("params", mParams.toJson());
        return json;
    }

    public static TestResource fromJson(JSONObject json) {
        return new TestResource(
                json.optString("name"),
                json.optString("url"),
                json.optBoolean("decompress"),
                json.optString("decompress_dir"),
                json.optBoolean("mount_zip"),
                TestResourceParameters.fromJson(json.optJSONObject("params")));
    }

    public static List<TestResource> fromJsonArray(JSONArray jsonArray) throws JSONException {
        final List<TestResource> objs = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            objs.add(TestResource.fromJson(jsonArray.getJSONObject(i)));
        }
        return objs;
    }
}

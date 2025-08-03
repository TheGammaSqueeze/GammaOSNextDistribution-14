/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.sts.common;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import javax.ws.rs.core.UriBuilder;

// https://docs.github.com/en/rest/releases/releases
public class GitHubUtils {

    public static class GitHubRepo {
        private String owner;
        private String repo;

        public GitHubRepo(String owner, String repo) {
            if (owner == null || repo == null) {
                throw new IllegalArgumentException("Owner or repo cannot be null");
            }
            this.owner = owner;
            this.repo = repo;
        }

        public URI getReleasesUri() throws URISyntaxException {
            return UriBuilder.fromUri(
                            new URI(
                                    "https",
                                    "api.github.com",
                                    null /* path */,
                                    null /* fragment */))
                    .path("repos")
                    .path(owner)
                    .path(repo)
                    .path("releases")
                    .build();
        }

        /**
         * Creates map of 'name' (under 'assets') to 'browser_download_url' for the given
         * 'tag_name', or for the latest release if no 'tag_name' is provided.
         *
         * @param tagName The optional tag name for which to retrieve release assets.
         * @return A map containing values under 'names' (under 'assets') as keys and their
         *     corresponding 'browser_download_url' as values.
         */
        public Map<String, URI> getReleaseAssetUris(Optional<String> tagName)
                throws IOException, MalformedURLException, URISyntaxException {
            URI releasesUri = getReleasesUri();
            JsonObject releaseObject;

            if (tagName.isPresent()) {
                JsonArray releases = getJson(releasesUri).getAsJsonArray();
                releaseObject = findReleaseByTagName(releases, tagName.get());
            } else {
                releaseObject =
                        getJson(UriBuilder.fromUri(releasesUri).segment("latest").build())
                                .getAsJsonObject();
            }

            // Initialize map to store asset names and URLs
            Map<String, URI> assetUris = new HashMap<>();
            parseAssets(releaseObject, assetUris);
            return assetUris;
        }

        private JsonObject findReleaseByTagName(JsonArray releases, String tagName) {
            for (JsonElement release : releases) {
                JsonObject releaseObject = release.getAsJsonObject();
                if (Pattern.matches(tagName, releaseObject.get("tag_name").getAsString())) {
                    return releaseObject;
                }
            }
            throw new IllegalArgumentException(
                    "Release with tag name '" + tagName + "' not found.");
        }

        private void parseAssets(JsonObject releaseObject, Map<String, URI> assetUris)
                throws URISyntaxException {
            for (JsonElement asset : releaseObject.getAsJsonArray("assets")) {
                JsonObject assetObject = asset.getAsJsonObject();
                String assetNameValue = assetObject.get("name").getAsString();
                URI assetUri = new URI(assetObject.get("browser_download_url").getAsString());
                assetUris.put(assetNameValue, assetUri);
            }
        }

        private static JsonElement getJson(URI uri) throws IOException {
            URLConnection conn = uri.toURL().openConnection();
            InputStreamReader reader = new InputStreamReader(conn.getInputStream());
            return new JsonParser().parse(reader);
        }
    }
}

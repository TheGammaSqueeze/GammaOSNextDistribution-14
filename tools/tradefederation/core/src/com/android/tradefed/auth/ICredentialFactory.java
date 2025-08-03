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

package com.android.tradefed.auth;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import com.google.api.client.auth.oauth2.Credential;

/**
 * An interface for credential factory to create oauth2 {@link Credential}. Also provide information
 * about the credentials.
 */
public interface ICredentialFactory {

    /**
     * Creates a {@link Credential} for the given scopes.
     *
     * @param scopes a list of API scopes.
     * @return an oauth2 {@link Credential}
     * @throws IOException
     */
    public Credential createCredential(Collection<String> scopes) throws IOException;

    /**
     * Get information about the credential factory's meta data, e.g. key file path, email, etc.
     *
     * @return a {@link Map} with information key to value.
     */
    public Map<String, String> getInfo();
}

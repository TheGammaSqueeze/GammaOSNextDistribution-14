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

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.SOURCE)
@IntDef({
    SslMode.INSECURE,
    SslMode.NO_CLIENT_AUTH,
    SslMode.WANTS_CLIENT_AUTH,
    SslMode.NEEDS_CLIENT_AUTH,
    SslMode.TRUST_ANY_CLIENT
})
public @interface SslMode {
    int INSECURE = 0;
    int NO_CLIENT_AUTH = 1;
    int WANTS_CLIENT_AUTH = 2;
    int NEEDS_CLIENT_AUTH = 3;
    int TRUST_ANY_CLIENT = 4;
}

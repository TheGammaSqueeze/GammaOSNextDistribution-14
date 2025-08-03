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

package android.platform.test.flag.util;

/** Exception to set flag values. */
public class FlagSetException extends RuntimeException {
    public FlagSetException(String flag, String msg) {
        super(String.format("Flag %s set error: %s", flag, msg));
    }

    public FlagSetException(String flag, Throwable cause) {
        super(String.format("Flag %s set error", flag), cause);
    }

    public FlagSetException(String flag, String msg, Throwable cause) {
        super(String.format("Flag %s read error: %s", flag, msg), cause);
    }
}

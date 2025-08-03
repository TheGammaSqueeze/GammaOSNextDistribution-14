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

package android.car.builtin.os;

import android.annotation.SystemApi;
import android.os.Process;

/**
 * Helper for {@link Process}.
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public final class ProcessHelper {

    /** See {@code Process.THREAD_GROUP_DEFAULT} */
    public static final int THREAD_GROUP_DEFAULT = Process.THREAD_GROUP_DEFAULT;

    /** See {@code Process.THREAD_GROUP_FOREGROUND} */
    public static final int THREAD_GROUP_FOREGROUND = 1;

    /** See {@code Process.THREAD_GROUP_TOP_APP} */
    public static final int THREAD_GROUP_TOP_APP = Process.THREAD_GROUP_TOP_APP;

    private ProcessHelper()  {
        throw new UnsupportedOperationException();
    }
}

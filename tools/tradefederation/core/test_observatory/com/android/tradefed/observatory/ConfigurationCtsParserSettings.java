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

package com.android.tradefed.observatory;

import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple class to accept settings for the ConfigurationCtsParserSettings
 *
 * <p>To pass settings to this class, the alias is mandatory. So something like {@code --cts-params
 * --compatibility:include-filter --cts-params CtsWebkitTestCases} will work.
 */
@OptionClass(alias = "compatibility")
public class ConfigurationCtsParserSettings {
    @Option(
            name = "cts-params",
            description = "This option is for the purpose of filtering in all of its values.")
    public List<String> mCtsParams = new ArrayList<>();

    @Option(
            name = "config-name",
            description = "This option is for the purpose of filtering in all of its values.")
    public String mConfigName = null;

    @Option(
            name = "rootdir-var",
            description =
                    "Name of the variable to be passed as -D "
                            + "parameter to the java call to specify the root directory.")
    public String mRootdirVar = "CTS_ROOT";
}

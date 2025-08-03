/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.managedprovisioning.parser;

import static com.android.internal.util.Preconditions.checkNotNull;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.VisibleForTesting;

import com.android.managedprovisioning.common.IllegalProvisioningArgumentException;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;

/**
 * This class can initialize a {@link ProvisioningParams} object from an intent.
 *
 * <p>A {@link ProvisioningParams} object stores various parameters both for the device owner
 * provisioning and profile owner provisioning.
 */
public class MessageParser implements ProvisioningDataParser {

    private final Utils mUtils;
    private final ParserUtils mParserUtils;
    private final SettingsFacade mSettingsFacade;
    private final Context mContext;

    public MessageParser(Context context, Utils utils) {
        this(context, utils, new ParserUtils(), new SettingsFacade());
    }

    @VisibleForTesting
    MessageParser(Context context, Utils utils, ParserUtils parserUtils,
            SettingsFacade settingsFacade) {
        mContext = checkNotNull(context);
        mUtils = checkNotNull(utils);
        mParserUtils = checkNotNull(parserUtils);
        mSettingsFacade = checkNotNull(settingsFacade);
    }

    @Override
    public ProvisioningParams parse(Intent provisioningIntent)
            throws IllegalProvisioningArgumentException {
        return getParser().parse(provisioningIntent);
    }

    @VisibleForTesting
    ProvisioningDataParser getParser() {
        return new ExtrasProvisioningDataParser(mContext, mUtils, mParserUtils, mSettingsFacade);
    }
}

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
package com.android.tradefed.config.filter;

import com.android.tradefed.command.ICommandOptions;
import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.retry.IRetryDecision;
import com.android.tradefed.sandbox.SandboxOptions;
import com.android.tradefed.service.IRemoteFeature;
import com.proto.tradefed.feature.ErrorInfo;
import com.proto.tradefed.feature.FeatureRequest;
import com.proto.tradefed.feature.FeatureResponse;
import com.proto.tradefed.feature.MultiPartResponse;
import com.proto.tradefed.feature.PartResponse;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Service implementation that returns the command options value of a given invocation.
 * This can be extended in the future to support more options.
 */
public class CommandOptionsGetter implements IRemoteFeature, IConfigurationReceiver {

    public static final String COMMAND_OPTIONS_GETTER = "getCommandOptions";
    public static final String OPTION_NAME = "option_name";

    private IConfiguration mConfig;

    @Override
    public String getName() {
        return COMMAND_OPTIONS_GETTER;
    }

    @Override
    public void setConfiguration(IConfiguration configuration) {
        mConfig = configuration;
    }

    @Override
    public FeatureResponse execute(FeatureRequest request) {
        FeatureResponse.Builder responseBuilder = FeatureResponse.newBuilder();
        if (mConfig == null) {
            responseBuilder.setErrorInfo(
                    ErrorInfo.newBuilder().setErrorTrace("Internal error, configuration not set."));
            return responseBuilder.build();
        }
        if (request.getArgsMap().isEmpty() || !request.getArgsMap().containsKey(OPTION_NAME)) {
            responseBuilder.setErrorInfo(
                    ErrorInfo.newBuilder().setErrorTrace("No option_name specified in the args."));
            return responseBuilder.build();
        }
        List<String> optionsToFill = new ArrayList<>(Arrays.asList(request.getArgsMap()
                .get(OPTION_NAME).split(",")));
        // Capture options of CommandOptions & RetryDecision
        ICommandOptions commandOptions = mConfig.getCommandOptions();
        IRetryDecision retryOptions = mConfig.getRetryDecision();
        SandboxOptions sandboxOptions =
                (SandboxOptions) mConfig
                    .getConfigurationObject(Configuration.SANBOX_OPTIONS_TYPE_NAME);
        List<Object> optionObjects = Arrays.asList(commandOptions, retryOptions, sandboxOptions);

        List<PartResponse> partResponses = new ArrayList<>();
        for (Object o : optionObjects) {
            partResponses.addAll(findOptionsForObject(o, optionsToFill));
        }
        if (partResponses.isEmpty()) {
            responseBuilder.setErrorInfo(
                    ErrorInfo.newBuilder().setErrorTrace(
                            String.format("No option or not value set for '%s'",
                                    request.getArgsMap().get(OPTION_NAME))));
        } else if (partResponses.size() == 1) {
            responseBuilder.setResponse(partResponses.get(0).getValue());
        } else {
            responseBuilder.setMultiPartResponse(MultiPartResponse.newBuilder()
                    .addAllResponsePart(partResponses));
        }
        return responseBuilder.build();
    }

    private List<PartResponse> findOptionsForObject(Object objectForFields,
                List<String> optionsToResolve) {
        List<PartResponse> responses = new ArrayList<>();
        Collection<Field> allFields = OptionSetter.getOptionFieldsForClass(
                objectForFields.getClass());
        for (String toResolve : optionsToResolve) {
            for (Field field : allFields) {
                final Option option = field.getAnnotation(Option.class);
                if (option.name().equals(toResolve)) {
                    Object fieldValue = OptionSetter.getFieldValue(field, objectForFields);
                    if (fieldValue != null) {
                        if (fieldValue instanceof Set) {
                            for (Object o : (Set) fieldValue) {
                                responses.add(
                                        PartResponse.newBuilder()
                                                .setKey(toResolve)
                                                .setValue(o.toString())
                                                .build());
                            }
                        } else if (fieldValue instanceof List) {
                            for (Object o : (List) fieldValue) {
                                responses.add(
                                        PartResponse.newBuilder()
                                                .setKey(toResolve)
                                                .setValue(o.toString())
                                                .build());
                            }
                        } else {
                            responses.add(
                                    PartResponse.newBuilder()
                                            .setKey(toResolve)
                                            .setValue(fieldValue.toString())
                                            .build());
                        }
                        continue;
                    }
                }
            }

        }
        return responses;
    }
}

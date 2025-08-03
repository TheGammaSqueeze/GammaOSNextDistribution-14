/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tradefed.config.proxy;

import com.android.tradefed.command.CommandOptions;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.Option;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.UniqueMultiMap;

import com.google.common.base.Joiner;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** Objects that helps delegating the invocation to another Tradefed binary. */
public class TradefedDelegator {

    /** The object reference in the configuration. */
    public static final String DELEGATE_OBJECT = "DELEGATE";

    private static final String DELETEGATED_OPTION_NAME = "delegated-tf";

    @Option(
            name = DELETEGATED_OPTION_NAME,
            description =
                    "Points to the root dir of another Tradefed binary that will be used to drive"
                            + " the invocation")
    private File mDelegatedTfRootDir;

    @Option(
            name = CommandOptions.INVOCATION_DATA,
            description = "Mirror of CommandOptions#INVOCATION_DATA")
    private UniqueMultiMap<String, String> mInvocationData = new UniqueMultiMap<>();

    // Not used directly but needed for proper parsing.
    @SuppressWarnings("unused")
    @Option(
            name = "cts-params",
            description =
                    "This option is special and can mess up the best effort parser, define "
                            + "it so it knows how to parse it.")
    private List<String> mCtsParams = new ArrayList<>();

    private String[] mCommandLine = null;

    /** Whether or not trigger the delegation logic. */
    public boolean shouldUseDelegation() {
        return mDelegatedTfRootDir != null;
    }

    /** Returns the directory of a Tradefed binary. */
    public File getTfRootDir() {
        return mDelegatedTfRootDir;
    }

    /** Creates the classpath out of the jars in the directory. */
    public String createClasspath() throws IOException {
        Set<File> jars = FileUtil.findFilesObject(mDelegatedTfRootDir, ".*\\.jar");
        return Joiner.on(":").join(jars);
    }

    public void setCommandLine(String[] command) {
        mCommandLine = command;
    }

    public String[] getCommandLine() {
        return mCommandLine;
    }

    /**
     * Returns whether or not this is the staging environment. We do not want to delegate in staging
     * by default, only if the "staging_delegated" is set.
     */
    public boolean isStaging() {
        return mInvocationData.containsKey("staging")
                && !mInvocationData.containsKey("staging_delegated");
    }

    /**
     * Remove from the original command line the delegate options so the underlying config does not
     * delegate again.
     */
    public static String[] clearCommandline(String[] originalCommand)
            throws ConfigurationException {
        String[] commandLine = clearCommandlineFromOneArg(originalCommand, DELETEGATED_OPTION_NAME);
        return commandLine;
    }

    /** Remove a given option from the command line. */
    private static String[] clearCommandlineFromOneArg(String[] originalCommand, String optionName)
            throws ConfigurationException {
        List<String> argsList = new ArrayList<>(Arrays.asList(originalCommand));
        try {
            while (argsList.contains("--" + optionName)) {
                int index = argsList.indexOf("--" + optionName);
                if (index != -1) {
                    argsList.remove(index + 1);
                    argsList.remove(index);
                }
            }
        } catch (RuntimeException e) {
            throw new ConfigurationException(e.getMessage(), e);
        }
        return argsList.toArray(new String[0]);
    }
}

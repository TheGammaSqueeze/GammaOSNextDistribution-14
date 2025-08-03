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
package com.android.csuite.config;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.DynamicRemoteFileResolver;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.config.remote.IRemoteFileResolver;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * An implementation of {@code IRemoteFileResolver} for downloading Android apps.
 *
 * <p>The scheme supported by this resolver allows Trade Federation test configs to abstract the
 * actual service used to download Android app APK files. Note that this is a 'meta' resolver that
 * resolves abstract 'app://' URIs into a URI with a different scheme using a custom template. The
 * actual downloading of this resolved URI is then delegated to another registered {@link
 * IRemoteFileResolver} implementation. Variable placeholders in the URI template string are
 * expanded with corresponding values.
 *
 * <h2>Syntax and usage</h2>
 *
 * <p>References to apps in TradeFed test configs must have the following syntax:
 *
 * <blockquote>
 *
 * <b>{@code app://}</b><i>package-name</i>
 *
 * </blockquote>
 *
 * where <i>package-name</i> is the name of the application package such as:
 *
 * <blockquote>
 *
 * <table cellpadding=0 cellspacing=0 summary="layout">
 * <tr><td>{@code app://com.example.myapp}<td></tr>
 * </table>
 *
 * </blockquote>
 *
 * App APK files are downloaded to a directory and must be used in contexts that can handle File
 * objects pointing to directories.
 *
 * <h2>Configuration</h2>
 *
 * <p>The URI template to use is specified using the {@code dynamic-download-args} TradeFed
 * command-line argument:
 *
 * <blockquote>
 *
 * <pre>
 * --dynamic-download-args app:uri-template=file:///app_files/{package}
 * </pre>
 *
 * </blockquote>
 *
 * <p>Where {package} expands to the actual package name being downloaded. Any illegal URI
 * characters must also be properly escaped as expected by {@link java.net.URI}.
 *
 * <p><span style="font-weight: bold; padding-right: 1em">Usage Note:</span> The {@code
 * --enable-module-dynamic-download} flag must be set to {@code true} when used in test suites.
 *
 * @see com.android.tradefed.config.Option
 */
@NotThreadSafe
@OptionClass(alias = "app")
public final class AppRemoteFileResolver implements IRemoteFileResolver {

    private static final String URI_SCHEME = "app";
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{(\\w+)\\}");

    @VisibleForTesting static final String URI_TEMPLATE_OPTION = "uri-template";

    @Option(name = URI_TEMPLATE_OPTION)
    private String mUriTemplate;

    @Nullable private ITestDevice mPrimaryDevice;

    @Override
    public String getSupportedProtocol() {
        return URI_SCHEME;
    }

    @Override
    public void setPrimaryDevice(@Nullable ITestDevice primaryDevice) {
        this.mPrimaryDevice = primaryDevice;
    }

    @Override
    public File resolveRemoteFiles(File uriSchemeAndPathAsFile) throws BuildRetrievalError {
        // Note that this method is not really supported or even called by the framework. We
        // only override it to simplify automated null pointer testing.
        return resolveRemoteFiles(uriSchemeAndPathAsFile, ImmutableMap.of());
    }

    @Override
    public File resolveRemoteFiles(
            File uriSchemeAndPathAsFile, Map<String, String> uriQueryAndExtraParameters)
            throws BuildRetrievalError {
        URI appUri = checkAppUri(toUri(uriSchemeAndPathAsFile));
        Objects.requireNonNull(uriQueryAndExtraParameters);

        // TODO(hzalek): Remove this and make the corresponding option mandatory once test configs
        // are using app URIs.
        if (mUriTemplate == null) {
            CLog.w("Resolver is not properly configured, skipping resolution of URI (%s)", appUri);
            return null;
        }

        Preconditions.checkState(
                !mUriTemplate.isEmpty(),
                String.format("%s=%s is empty", URI_TEMPLATE_OPTION, mUriTemplate));

        String packageName = appUri.getAuthority();
        String expanded = expandVars(mUriTemplate, ImmutableMap.of("package", packageName));

        URI uri;
        try {
            uri = new URI(expanded);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(
                    String.format(
                            "%s: URI template (%s) did not expand to a a valid URI (%s)",
                            URI_TEMPLATE_OPTION, mUriTemplate, expanded),
                    e);
        }

        if (URI_SCHEME.equals(uri.getScheme())) {
            throw new BuildRetrievalError(
                    String.format(
                            "Providers must return URIs with a scheme different than '%s': %s > %s",
                            URI_SCHEME, appUri, uri));
        }

        return resolveUriToFile(packageName, uri, uriQueryAndExtraParameters);
    }

    private static URI toUri(File uriSchemeAndPathAsFile) {
        try {
            // TradeFed forces a URI into a File instance which is lossy and forces us to attempt
            // restoring the original format here so we don't have to use regular expressions. Be
            // warned that using getAbsolutePath() will incorrectly strip the scheme.
            String path = uriSchemeAndPathAsFile.getPath();
            // Restore the original URI form since the first two forward slashes in the URI string
            // get normalized into one when stored as a file.
            path = path.replaceFirst(":/", "://");
            return new URI(path);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Could not parse provided URI", e);
        }
    }

    private static URI checkAppUri(URI uri) {
        String uriScheme = uri.getScheme();
        if (!URI_SCHEME.equals(uriScheme)) {
            throw new IllegalArgumentException(
                    String.format("Unsupported scheme (%s) in provided URI (%s)", uriScheme, uri));
        }

        // Note that the below code accesses the 'authority' component of the URI and not 'path'
        // like the dynamic resolver implementation. The latter has to do so because the authority
        // component is no longer defined once the '//' gets converted to a single '/'.
        String packageName = uri.getAuthority();
        if (Strings.isNullOrEmpty(packageName)) {
            throw new IllegalArgumentException(
                    String.format(
                            "Invalid package name (%s) in provided URI (%s)", packageName, uri));
        }

        if (!Strings.isNullOrEmpty(uri.getPath())) {
            throw new IllegalArgumentException(
                    String.format(
                            "Path component (%s) incorrectly specified in provided URI (%s); "
                                    + "app URIs must be of the form 'app://com.example.app'",
                            uri.getPath(), uri));
        }

        return uri;
    }

    private static String expandVars(CharSequence template, Map<String, String> vars) {
        StringBuilder sb = new StringBuilder();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        int position = 0;

        while (matcher.find()) {
            sb.append(template.subSequence(position, matcher.start(0)));

            String varName = matcher.group(1);
            String varValue = vars.get(varName);

            if (varValue == null) {
                throw new IllegalStateException(
                        String.format(
                                "URI template (%s) contains a placeholder for undefined var (%s)",
                                template, varName));
            }

            sb.append(varValue);
            position = matcher.end(0);
        }

        sb.append(template.subSequence(position, template.length()));
        String expanded = sb.toString();

        CLog.i("Template (%s) expanded (%s) using vars (%s)", template, expanded, vars);
        return expanded;
    }

    private File resolveUriToFile(String packageName, URI uri, Map<String, String> params)
            throws BuildRetrievalError {
        DynamicRemoteFileResolver resolver = new DynamicRemoteFileResolver();
        resolver.setDevice(mPrimaryDevice);
        resolver.addExtraArgs(params);

        FileOptionSource optionSource = new FileOptionSource();
        Stopwatch stopwatch = Stopwatch.createStarted();

        try {
            OptionSetter setter = new OptionSetter(optionSource);
            setter.setOptionValue(FileOptionSource.OPTION_NAME, uri.toString());
            setter.validateRemoteFilePath(resolver);
            CLog.i("Resolution of files took %d ms", stopwatch.elapsed().toMillis());
        } catch (BuildRetrievalError e) {
            throw new BuildRetrievalError(
                    String.format("Could not resolve URI (%s) for package '%s'", uri, packageName),
                    e);
        } catch (ConfigurationException impossible) {
            throw new AssertionError(impossible);
        }

        if (!optionSource.file.exists()) {
            CLog.w("URI (%s) resolved to non-existent local file (%s)", uri, optionSource.file);
        } else {
            CLog.i("URI (%s) resolved to local file (%s)", uri, optionSource.file);
        }

        return optionSource.file;
    }

    /** This is required to resolve URIs since the remote resolver only deals with options. */
    private static final class FileOptionSource {
        static final String OPTION_NAME = "file";

        @Option(name = OPTION_NAME, mandatory = true)
        public File file;
    }
}

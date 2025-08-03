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

import static com.android.csuite.testing.Correspondences.instanceOf;
import static com.android.csuite.testing.MoreAsserts.assertThrows;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.config.remote.IRemoteFileResolver;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.testing.NullPointerTester;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ServiceLoader;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

@RunWith(JUnit4.class)
public final class AppRemoteFileResolverTest {

    private static final String PACKAGE_NAME = "com.example.app";
    private static final File APP_URI_FILE = uriToFile("app://" + PACKAGE_NAME);
    private static final ImmutableMap<String, String> EMPTY_PARAMS = ImmutableMap.of();

    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    // Class sanity tests.

    @Test
    public void isServiceLoadable() throws Exception {
        ClassLoader classLoader = classLoaderWithProviders(AppRemoteFileResolver.class.getName());

        ServiceLoader<IRemoteFileResolver> serviceLoader =
                ServiceLoader.load(IRemoteFileResolver.class, classLoader);

        // Copy the list to provide better failure error messages since ServiceLoader's string
        // representation is not very informative.
        assertThat(ImmutableList.copyOf(serviceLoader))
                .comparingElementsUsing(instanceOf())
                .contains(AppRemoteFileResolver.class);
    }

    @Test
    public void nullPointers() {
        NullPointerTester tester = new NullPointerTester();
        tester.setDefault(File.class, APP_URI_FILE);
        tester.testAllPublicConstructors(AppRemoteFileResolver.class);
        tester.testAllPublicInstanceMethods(new AppRemoteFileResolver());
    }

    // URI validation tests.

    @Test
    public void unsupportedUriScheme_throwsException() throws Exception {
        AppRemoteFileResolver resolver = newResolverWithAnyTemplate();
        String uri = "gs://" + PACKAGE_NAME;
        File f = uriToFile(uri);

        Throwable thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> resolver.resolveRemoteFiles(f, EMPTY_PARAMS));

        assertThat(thrown).hasMessageThat().contains("(gs)");
        assertThat(thrown).hasMessageThat().contains(uri);
    }

    @Test
    public void opaqueUri_throwsException() throws Exception {
        AppRemoteFileResolver resolver = newResolverWithAnyTemplate();
        File uri = uriToFile("app:" + PACKAGE_NAME);

        Throwable thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> resolver.resolveRemoteFiles(uri, EMPTY_PARAMS));

        assertThat(thrown).hasMessageThat().contains("package name");
    }

    @Test
    public void uriHasPathComponent_throwsException() throws Exception {
        AppRemoteFileResolver resolver = newResolverWithAnyTemplate();
        File uri = uriToFile("app://" + PACKAGE_NAME + "/invalid");

        Throwable thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> resolver.resolveRemoteFiles(uri, EMPTY_PARAMS));

        assertThat(thrown).hasMessageThat().contains("invalid");
    }

    // Template validation and expansion tests.

    @Test
    public void templateNotSet_returnsNull() throws Exception {
        AppRemoteFileResolver resolver = new AppRemoteFileResolver();

        File actual = resolver.resolveRemoteFiles(APP_URI_FILE, EMPTY_PARAMS);

        assertThat(actual).isNull();
    }

    @Test
    public void emptyTemplate_throwsException() throws Exception {
        AppRemoteFileResolver resolver = newResolverWithTemplate("");

        Throwable thrown =
                assertThrows(
                        IllegalStateException.class,
                        () -> resolver.resolveRemoteFiles(APP_URI_FILE, EMPTY_PARAMS));

        assertThat(thrown).hasMessageThat().contains(AppRemoteFileResolver.URI_TEMPLATE_OPTION);
    }

    @Test
    public void templateHasNoPlaceholders_returnsFileWithoutExpansion() throws Exception {
        File expected = temporaryFolder.newFolder();
        String expectedPathWithFilePrefix = "file:" + expected.toString();
        AppRemoteFileResolver resolver = newResolverWithTemplate(expected.toURI().toString());

        File actual = resolver.resolveRemoteFiles(APP_URI_FILE, EMPTY_PARAMS);

        if (!actual.equals(expected) && !actual.toString().equals(expectedPathWithFilePrefix)) {
            fail(String.format("Actual %s does not match expected %s", actual, expected));
        }
    }

    @Test
    public void templateContainsPlaceholderForUndefinedVar_throwsException() throws Exception {
        AppRemoteFileResolver resolver = newResolverWithTemplate("file://{undefined}");

        Throwable thrown =
                assertThrows(
                        IllegalStateException.class,
                        () -> resolver.resolveRemoteFiles(APP_URI_FILE, EMPTY_PARAMS));

        assertThat(thrown).hasMessageThat().contains("undefined");
    }

    @Test
    public void templateExpandsToInvalidUri_throwsException() throws Exception {
        AppRemoteFileResolver resolver = newResolverWithTemplate("file:\\{package}");

        Throwable thrown =
                assertThrows(
                        IllegalStateException.class,
                        () -> resolver.resolveRemoteFiles(APP_URI_FILE, EMPTY_PARAMS));

        assertThat(thrown).hasMessageThat().contains(AppRemoteFileResolver.URI_TEMPLATE_OPTION);
    }

    @Test
    public void templateContainsPlaceholder_resolvesUriToFile() throws Exception {
        File parent = temporaryFolder.newFolder();
        File expected = new File(parent, PACKAGE_NAME);
        String template = new File(parent, "{package}").toString();
        AppRemoteFileResolver resolver = newResolverWithTemplate(template);
        File uri = uriToFile("app://" + PACKAGE_NAME);

        File actual = resolver.resolveRemoteFiles(uri, EMPTY_PARAMS);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void templateExpandsToAppUri_throwsException() throws Exception {
        AppRemoteFileResolver resolver = newResolverWithTemplate("app://{package}");

        Throwable thrown =
                assertThrows(
                        BuildRetrievalError.class,
                        () -> resolver.resolveRemoteFiles(APP_URI_FILE, EMPTY_PARAMS));

        assertThat(thrown).hasMessageThat().contains("'app'");
    }

    @Test
    public void templateExpandsToUriWithUnsupportedScheme_returnsExpandedUri() throws Exception {
        String uri = "unsupported://" + PACKAGE_NAME;
        AppRemoteFileResolver resolver = newResolverWithTemplate(uri);
        File expected = uriToFile(uri);

        File actual = resolver.resolveRemoteFiles(APP_URI_FILE, EMPTY_PARAMS);

        assertThat(actual).isEqualTo(expected);
    }

    // Utility classes and methods.

    /**
     * Constructs a File from a URI string using the same logic TradeFed uses since it's tricky and
     * has some gotchas such as stripping slashes.
     */
    private static File uriToFile(String str) {
        FileOptionSource optionSource = new FileOptionSource();

        try {
            OptionSetter setter = new OptionSetter(optionSource);
            setter.setOptionValue(FileOptionSource.OPTION_NAME, str);
        } catch (ConfigurationException e) {
            throw new RuntimeException(e);
        }

        return optionSource.file;
    }

    private static final class FileOptionSource {
        static final String OPTION_NAME = "file";

        @Option(name = OPTION_NAME)
        public File file;
    }

    private static AppRemoteFileResolver newResolverWithAnyTemplate()
            throws ConfigurationException {
        return newResolverWithTemplate("file:///tmp/{package}");
    }

    private static AppRemoteFileResolver newResolverWithTemplate(String uriTemplate)
            throws ConfigurationException {
        AppRemoteFileResolver resolver = new AppRemoteFileResolver();
        OptionSetter setter = new OptionSetter(resolver);
        setter.setOptionValue("app:" + AppRemoteFileResolver.URI_TEMPLATE_OPTION, uriTemplate);
        return resolver;
    }

    private ClassLoader classLoaderWithProviders(String... lines) throws IOException {
        String service = IRemoteFileResolver.class.getName();
        File jar = temporaryFolder.newFile();

        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jar))) {
            JarEntry jarEntry = new JarEntry("META-INF/services/" + service);

            out.putNextEntry(jarEntry);
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, UTF_8));

            for (String line : lines) {
                writer.println(line);
            }

            writer.flush();
        }

        return new URLClassLoader(new URL[] {jar.toURI().toURL()});
    }
}

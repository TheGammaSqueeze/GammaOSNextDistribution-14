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

package com.android.csuite.core;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.OptionSetter;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RunWith(JUnit4.class)
public final class FileBasedTemplateMappingProviderTest {
    @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void get_emptyFile_returnsEmptyStream() throws Exception {
        String filePath = createTemplateMappingFile("");
        FileBasedTemplateMappingProvider sut = createSubjectUnderTest(filePath);

        Stream<Entry<String, String>> entries = sut.get();

        assertThat(entries.collect(Collectors.toList())).isEmpty();
    }

    @Test
    public void get_fileOptionNotSet_returnsEmptyStream() throws Exception {
        FileBasedTemplateMappingProvider sut = new FileBasedTemplateMappingProvider();

        Stream<Entry<String, String>> entries = sut.get();

        assertThat(entries.collect(Collectors.toList())).isEmpty();
    }

    @Test
    public void get_fileContainsCommentLines_ignoresComments() throws Exception {
        String filePath =
                createTemplateMappingFile(
                        FileBasedTemplateMappingProvider.COMMENT_LINE_PREFIX + " comments");
        FileBasedTemplateMappingProvider sut = createSubjectUnderTest(filePath);

        Stream<Entry<String, String>> entries = sut.get();

        assertThat(entries.collect(Collectors.toList())).isEmpty();
    }

    @Test
    public void get_fileContainsEmptyLines_ignoresEmptyLines() throws Exception {
        String filePath = createTemplateMappingFile("\n\n\n");
        FileBasedTemplateMappingProvider sut = createSubjectUnderTest(filePath);

        Stream<Entry<String, String>> entries = sut.get();

        assertThat(entries.collect(Collectors.toList())).isEmpty();
    }

    @Test
    public void get_lineContainsTooMuchItems_throwsException() throws Exception {
        String filePath =
                createTemplateMappingFile(
                        "1"
                                + FileBasedTemplateMappingProvider.MODULE_TEMPLATE_SEPARATOR
                                + "2"
                                + FileBasedTemplateMappingProvider.MODULE_TEMPLATE_SEPARATOR
                                + "3");
        FileBasedTemplateMappingProvider sut = createSubjectUnderTest(filePath);

        assertThrows(IllegalArgumentException.class, () -> sut.get());
    }

    @Test
    public void get_lineContainsWrongSeparater_throwsException() throws Exception {
        String filePath = createTemplateMappingFile("1::::2");
        FileBasedTemplateMappingProvider sut = createSubjectUnderTest(filePath);

        assertThrows(IllegalArgumentException.class, () -> sut.get());
    }

    @Test
    public void get_fileContainsValidMapping_returnsEntries() throws Exception {
        String filePath =
                createTemplateMappingFile(
                        "module1"
                                + FileBasedTemplateMappingProvider.MODULE_TEMPLATE_SEPARATOR
                                + "template1\n"
                                + "module2"
                                + FileBasedTemplateMappingProvider.MODULE_TEMPLATE_SEPARATOR
                                + "template2\n");
        FileBasedTemplateMappingProvider sut = createSubjectUnderTest(filePath);

        Stream<Entry<String, String>> entries = sut.get();

        Map<String, String> map = convertToMap(entries);
        assertThat(map.size()).isEqualTo(2);
        assertThat(map.get("module1")).isEqualTo("template1");
        assertThat(map.get("module2")).isEqualTo("template2");
    }

    public Map<String, String> convertToMap(Stream<Entry<String, String>> entryStream) {
        HashMap<String, String> res = new HashMap<String, String>();
        entryStream.forEach(entry -> res.put(entry.getKey(), entry.getValue()));
        return res;
    }

    private FileBasedTemplateMappingProvider createSubjectUnderTest(String file)
            throws ConfigurationException {
        FileBasedTemplateMappingProvider sut = new FileBasedTemplateMappingProvider();
        new OptionSetter(sut)
                .setOptionValue(
                        FileBasedTemplateMappingProvider.TEMPLATE_MAPPING_FILE_OPTION, file);
        return sut;
    }

    private String createTemplateMappingFile(String content) throws IOException {
        Path tempFile = Files.createTempFile(tempFolder.getRoot().toPath(), "mapping", ".txt");
        Files.write(tempFile, content.getBytes());
        return tempFile.toString();
    }
}

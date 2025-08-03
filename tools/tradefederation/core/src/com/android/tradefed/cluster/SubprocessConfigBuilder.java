/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tradefed.cluster;

import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.ConfigurationUtil;
import com.android.tradefed.result.LegacySubprocessResultsReporter;
import com.android.tradefed.util.FileUtil;

import com.google.common.base.Strings;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * Build a wrapper TF config XML for an existing TF config.
 *
 * <p>A wrapper XML allows to enable subprocess reporting on an existing TF config.
 */
public class SubprocessConfigBuilder {
    private static final String REPORTER_CLASS = LegacySubprocessResultsReporter.class.getName();
    private static final String OPTION_KEY = "subprocess-report-port";
    private String mClasspath;

    private File mWorkDir;

    private String mOriginalConfig;

    private String mPort;

    public SubprocessConfigBuilder setClasspath(String classpath) {
        mClasspath = classpath;
        return this;
    }

    public SubprocessConfigBuilder setWorkingDir(File dir) {
        mWorkDir = dir;
        return this;
    }

    public SubprocessConfigBuilder setOriginalConfig(String config) {
        mOriginalConfig = config;
        return this;
    }

    public SubprocessConfigBuilder setPort(String port) {
        mPort = port;
        return this;
    }

    /**
     * Current handling of ATS for the naming of injected config. Exposed so it can be used to align
     * the test harness side.
     */
    public static String createConfigName(String originalConfigName) {
        return "_" + originalConfigName.replace("/", "$") + ".xml";
    }

    public File build() throws IOException {
        final List<URL> urls = new ArrayList<>();
        for (final String path : mClasspath.split(File.pathSeparator)) {
            if (path.endsWith("*")) {
                final File dir = new File(path.substring(0, path.length() - 1));
                if (!dir.exists()) {
                    continue;
                }
                for (final File file :
                        dir.listFiles((parent, name) -> name.toLowerCase().endsWith(".jar"))) {
                    urls.add(file.toURI().toURL());
                }
            } else {
                urls.add(new File(path).toURI().toURL());
            }
        }

        // Read the original config file.
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        Document doc = null;
        try (URLClassLoader loader = new URLClassLoader(urls.toArray(new URL[urls.size()]), null)) {
            final DocumentBuilder builder = factory.newDocumentBuilder();
            final String ext = FileUtil.getExtension(mOriginalConfig);
            InputStream in = null;
            if (Strings.isNullOrEmpty(ext)) {
                in = loader.getResourceAsStream(String.format("config/%s.xml", mOriginalConfig));
            } else {
                in = loader.getResourceAsStream(String.format("config/%s", mOriginalConfig));
            }
            if (in == null) {
                File f = new File(mOriginalConfig);
                if (!f.isAbsolute()) {
                    f = new File(mWorkDir, mOriginalConfig);
                }
                try {
                    in = new FileInputStream(f);
                } catch (FileNotFoundException e) {
                    throw new RuntimeException(
                            String.format("Could not find configuration '%s'", mOriginalConfig));
                }
            }
            doc = builder.parse(in);
        } catch (ParserConfigurationException | SAXException e) {
            throw new RuntimeException(e);
        }

        if (mPort != null) {
            // Add subprocess result reporter to a config file.
            final Node root = doc.getElementsByTagName("configuration").item(0);
            final Element reporter = doc.createElement(Configuration.RESULT_REPORTER_TYPE_NAME);
            reporter.setAttribute(ConfigurationUtil.CLASS_NAME, REPORTER_CLASS);
            final Element options = doc.createElement(ConfigurationUtil.OPTION_NAME);
            options.setAttribute(ConfigurationUtil.NAME_NAME, OPTION_KEY);
            options.setAttribute(ConfigurationUtil.VALUE_NAME, mPort);
            reporter.appendChild(options);
            root.appendChild(reporter);
        }

        File f = new File(mWorkDir, mOriginalConfig);
        if (!f.exists() || !f.isFile()) {
            // If the original config is an existing file, we need to update it since some old TFs
            // check the file system first before bundled configs when loading configs.
            // If the original config is not an existing file, we can use any name since the
            // original config name will be assigned when creating a injection jar.
            f = File.createTempFile("subprocess_config_", ".xml", mWorkDir);
        }
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        try {
            Transformer transformer = transformerFactory.newTransformer();
            transformer.transform(new DOMSource(doc), new StreamResult(f));
        } catch (TransformerException e) {
            throw new RuntimeException(e);
        }

        return f;
    }
}

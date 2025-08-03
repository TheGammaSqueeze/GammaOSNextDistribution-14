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

package com.android.tests.odsign;

import static com.google.common.truth.Truth.assertThat;

import com.android.tradefed.invoker.TestInformation;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/** A helper class that can mutate the device state and restore it afterwards. */
public class DeviceState {
    private static final String TEST_JAR_RESOURCE_NAME = "/art-gtest-jars-Main.jar";
    private static final String PHENOTYPE_FLAG_NAMESPACE = "runtime_native_boot";
    private static final String ART_APEX_DALVIK_CACHE_BACKUP_DIRNAME =
            OdsignTestUtils.ART_APEX_DALVIK_CACHE_DIRNAME + ".bak";

    private final TestInformation mTestInfo;
    private final OdsignTestUtils mTestUtils;

    private Set<String> mTempFiles = new HashSet<>();
    private Set<String> mMountPoints = new HashSet<>();
    private Map<String, String> mMutatedProperties = new HashMap<>();
    private Map<String, String> mMutatedPhenotypeFlags = new HashMap<>();
    private Map<String, String> mDeletedFiles = new HashMap<>();
    private boolean mHasArtifactsBackup = false;

    public DeviceState(TestInformation testInfo) throws Exception {
        mTestInfo = testInfo;
        mTestUtils = new OdsignTestUtils(testInfo);
    }

    /** Restores the device state. */
    public void restore() throws Exception {
        for (String mountPoint : mMountPoints) {
            mTestInfo.getDevice().executeShellV2Command(String.format("umount '%s'", mountPoint));
        }

        for (String tempFile : mTempFiles) {
            mTestInfo.getDevice().deleteFile(tempFile);
        }

        for (var entry : mMutatedProperties.entrySet()) {
            mTestInfo.getDevice().setProperty(
                    entry.getKey(), entry.getValue() != null ? entry.getValue() : "");
        }

        for (var entry : mMutatedPhenotypeFlags.entrySet()) {
            if (entry.getValue() != null) {
                mTestInfo.getDevice().executeShellV2Command(
                        String.format("device_config put '%s' '%s' '%s'", PHENOTYPE_FLAG_NAMESPACE,
                                entry.getKey(), entry.getValue()));
            } else {
                mTestInfo.getDevice().executeShellV2Command(
                        String.format("device_config delete '%s' '%s'", PHENOTYPE_FLAG_NAMESPACE,
                                entry.getKey()));
            }
        }

        for (var entry : mDeletedFiles.entrySet()) {
            mTestInfo.getDevice().executeShellV2Command(
                    String.format("cp '%s' '%s'", entry.getValue(), entry.getKey()));
            mTestInfo.getDevice().executeShellV2Command(String.format("rm '%s'", entry.getValue()));
            mTestInfo.getDevice().executeShellV2Command(
                    String.format("restorecon '%s'", entry.getKey()));
        }

        if (mHasArtifactsBackup) {
            mTestInfo.getDevice().executeShellV2Command(
                    String.format("rm -rf '%s'", OdsignTestUtils.ART_APEX_DALVIK_CACHE_DIRNAME));
            mTestInfo.getDevice().executeShellV2Command(
                    String.format("mv '%s' '%s'", ART_APEX_DALVIK_CACHE_BACKUP_DIRNAME,
                            OdsignTestUtils.ART_APEX_DALVIK_CACHE_DIRNAME));
        }
    }

    /** Simulates that the ART APEX has been upgraded. */
    public void simulateArtApexUpgrade() throws Exception {
        updateApexInfo("com.android.art", false /* isFactory */);
    }

    /**
     * Simulates that the new ART APEX has been uninstalled (i.e., the ART module goes back to the
     * factory version).
     */
    public void simulateArtApexUninstall() throws Exception {
        updateApexInfo("com.android.art", true /* isFactory */);
    }

    /**
     * Simulates that an APEX has been upgraded. We could install a real APEX, but that would
     * introduce an extra dependency to this test, which we want to avoid.
     */
    public void simulateApexUpgrade() throws Exception {
        updateApexInfo("com.android.wifi", false /* isFactory */);
    }

    /**
     * Simulates that the new APEX has been uninstalled (i.e., the module goes back to the factory
     * version).
     */
    public void simulateApexUninstall() throws Exception {
        updateApexInfo("com.android.wifi", true /* isFactory */);
    }

    private void updateApexInfo(String moduleName, boolean isFactory) throws Exception {
        try (var xmlMutator = new XmlMutator(OdsignTestUtils.APEX_INFO_FILE)) {
            NodeList list = xmlMutator.getDocument().getElementsByTagName("apex-info");
            for (int i = 0; i < list.getLength(); i++) {
                Element node = (Element) list.item(i);
                if (node.getAttribute("moduleName").equals(moduleName)
                        && node.getAttribute("isActive").equals("true")) {
                    node.setAttribute("isFactory", String.valueOf(isFactory));
                    node.setAttribute(
                            "lastUpdateMillis", String.valueOf(System.currentTimeMillis()));
                }
            }
        }
    }

    /** Simulates that there is an OTA that updates a boot classpath jar. */
    public void simulateBootClasspathOta() throws Exception {
        File localFile = mTestUtils.copyResourceToFile(TEST_JAR_RESOURCE_NAME);
        pushAndBindMount(localFile, "/system/framework/framework-graphics.jar");
    }

    /** Simulates that there is an OTA that updates a system server jar. */
    public void simulateSystemServerOta() throws Exception {
        File localFile = mTestUtils.copyResourceToFile(TEST_JAR_RESOURCE_NAME);
        pushAndBindMount(localFile, "/system/framework/services.jar");
    }

    /** Simulates that a system server jar is bad. */
    public void simulateBadSystemServerJar() throws Exception {
        File tempFile = File.createTempFile("empty", ".jar");
        tempFile.deleteOnExit();
        pushAndBindMount(tempFile, "/system/framework/services.jar");
    }

    public void makeDex2oatFail() throws Exception {
        setProperty("dalvik.vm.boot-dex2oat-threads", "-1");
    }

    /** Sets a system property. */
    public void setProperty(String key, String value) throws Exception {
        if (!mMutatedProperties.containsKey(key)) {
            // Backup the original value.
            mMutatedProperties.put(key, mTestInfo.getDevice().getProperty(key));
        }

        mTestInfo.getDevice().setProperty(key, value);
    }

    /** Sets a phenotype flag. */
    public void setPhenotypeFlag(String key, String value) throws Exception {
        if (!mMutatedPhenotypeFlags.containsKey(key)) {
            String output = mTestUtils.assertCommandSucceeds(
                    String.format("device_config get '%s' '%s'", PHENOTYPE_FLAG_NAMESPACE, key));
            mMutatedPhenotypeFlags.put(key, output.equals("null") ? null : output);
        }

        if (value != null) {
            mTestUtils.assertCommandSucceeds(String.format(
                    "device_config put '%s' '%s' '%s'", PHENOTYPE_FLAG_NAMESPACE, key, value));
        } else {
            mTestUtils.assertCommandSucceeds(
                    String.format("device_config delete '%s' '%s'", PHENOTYPE_FLAG_NAMESPACE, key));
        }
    }

    public void backupAndDeleteFile(String remotePath) throws Exception {
        String tempFile = "/data/local/tmp/odsign_e2e_tests_" + UUID.randomUUID() + ".tmp";
        // Backup the file before deleting it.
        mTestUtils.assertCommandSucceeds(String.format("cp '%s' '%s'", remotePath, tempFile));
        mTestUtils.assertCommandSucceeds(String.format("rm '%s'", remotePath));
        mDeletedFiles.put(remotePath, tempFile);
    }

    public void backupArtifacts() throws Exception {
        mTestInfo.getDevice().executeShellV2Command(
                String.format("rm -rf '%s'", ART_APEX_DALVIK_CACHE_BACKUP_DIRNAME));
        mTestUtils.assertCommandSucceeds(
                String.format("cp -r '%s' '%s'", OdsignTestUtils.ART_APEX_DALVIK_CACHE_DIRNAME,
                        ART_APEX_DALVIK_CACHE_BACKUP_DIRNAME));
        mHasArtifactsBackup = true;
    }

    /**
     * Pushes the file to a temporary location and bind-mount it at the given path. This is useful
     * when the path is readonly.
     */
    private void pushAndBindMount(File localFile, String remotePath) throws Exception {
        String tempFile = "/data/local/tmp/odsign_e2e_tests_" + UUID.randomUUID() + ".tmp";
        assertThat(mTestInfo.getDevice().pushFile(localFile, tempFile)).isTrue();
        mTempFiles.add(tempFile);

        // If the path has already been bind-mounted by this method before, unmount it first.
        if (mMountPoints.contains(remotePath)) {
            mTestUtils.assertCommandSucceeds(String.format("umount '%s'", remotePath));
            mMountPoints.remove(remotePath);
        }

        mTestUtils.assertCommandSucceeds(
                String.format("mount --bind '%s' '%s'", tempFile, remotePath));
        mMountPoints.add(remotePath);
        mTestUtils.assertCommandSucceeds(String.format("restorecon '%s'", remotePath));
    }

    /** A helper class for mutating an XML file. */
    private class XmlMutator implements AutoCloseable {
        private final Document mDocument;
        private final String mRemoteXmlFile;
        private final File mLocalFile;

        public XmlMutator(String remoteXmlFile) throws Exception {
            // Load the XML file.
            mRemoteXmlFile = remoteXmlFile;
            mLocalFile = mTestInfo.getDevice().pullFile(remoteXmlFile);
            assertThat(mLocalFile).isNotNull();
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            mDocument = builder.parse(mLocalFile);
        }

        @Override
        public void close() throws Exception {
            // Save the XML file.
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.transform(new DOMSource(mDocument), new StreamResult(mLocalFile));
            pushAndBindMount(mLocalFile, mRemoteXmlFile);
        }

        /** Returns a mutable XML document. */
        public Document getDocument() {
            return mDocument;
        }
    }
}

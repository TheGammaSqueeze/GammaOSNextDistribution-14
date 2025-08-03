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

import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.io.Resources;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ModuleTemplate {
    @VisibleForTesting static final String XML_FILE_EXTENSION = ".xml";
    @VisibleForTesting static final String TEMPLATE_FILE_EXTENSION = ".xml.template";

    @VisibleForTesting
    static final String MODULE_TEMPLATE_PROVIDER_OBJECT_TYPE = "MODULE_TEMPLATE_PROVIDER";

    @VisibleForTesting static final String DEFAULT_TEMPLATE_OPTION = "default-template";
    @VisibleForTesting static final String EXTRA_TEMPLATES_OPTION = "extra-templates";
    @VisibleForTesting static final String TEMPLATE_ROOT_OPTION = "template-root";

    @Option(
            name = DEFAULT_TEMPLATE_OPTION,
            description = "The default module config template resource path.",
            importance = Importance.ALWAYS)
    private String mDefaultTemplate;

    @Option(
            name = TEMPLATE_ROOT_OPTION,
            description = "The root path of the template files.",
            importance = Importance.ALWAYS)
    private String mTemplateRoot;

    @Option(
            name = EXTRA_TEMPLATES_OPTION,
            description = "Extra module config template resource paths.",
            importance = Importance.NEVER)
    private List<String> mExtraTemplates = new ArrayList<>();

    private final ResourceLoader mResourceLoader;
    private String mDefaultTemplateContent;
    private Map<String, String> mTemplateContentMap;
    private Map<String, String> mTemplateMapping;

    /**
     * Load the ModuleTemplate object from a suite configuration.
     *
     * <p>An error will be thrown if there's no such objects or more than one objects.
     *
     * @param configuration The suite configuration.
     * @return A ModuleTemplate object.
     * @throws IOException
     */
    public static ModuleTemplate loadFrom(IConfiguration configuration) throws IOException {
        List<?> moduleTemplates =
                configuration.getConfigurationObjectList(MODULE_TEMPLATE_PROVIDER_OBJECT_TYPE);
        Preconditions.checkNotNull(
                moduleTemplates, "Missing " + MODULE_TEMPLATE_PROVIDER_OBJECT_TYPE);
        Preconditions.checkArgument(
                moduleTemplates.size() == 1,
                "Only one module template object is expected. Found " + moduleTemplates.size());
        ModuleTemplate moduleTemplate = (ModuleTemplate) moduleTemplates.get(0);
        moduleTemplate.init(configuration);
        return moduleTemplate;
    }

    public ModuleTemplate() {
        this(new ClassResourceLoader());
    }

    @VisibleForTesting
    ModuleTemplate(ResourceLoader resourceLoader) {
        mResourceLoader = resourceLoader;
    }

    @SuppressWarnings("MustBeClosedChecker")
    private void init(IConfiguration configuration) throws IOException {
        if (mDefaultTemplateContent != null) { // Already loaded.
            return;
        }

        mTemplateContentMap = new HashMap<>();

        String defaultTemplateContent = mResourceLoader.load(mDefaultTemplate);
        mDefaultTemplateContent = defaultTemplateContent;
        mTemplateContentMap.put(
                getTemplateNameFromTemplateFile(mDefaultTemplate), defaultTemplateContent);

        for (String extraTemplate : mExtraTemplates) {
            mTemplateContentMap.put(
                    getTemplateNameFromTemplateFile(extraTemplate),
                    mResourceLoader.load(extraTemplate));
        }

        mTemplateMapping = new HashMap<>();

        List<?> templateMappingObjects =
                configuration.getConfigurationObjectList(
                        TemplateMappingProvider.TEMPLATE_MAPPING_PROVIDER_OBJECT_TYPE);

        if (templateMappingObjects == null) { // No mapping objects found.
            return;
        }

        for (Object provider : templateMappingObjects) {
            ((TemplateMappingProvider) provider)
                    .get()
                    .forEach(
                            entry -> {
                                String moduleName = entry.getKey();
                                String templateName =
                                        getTemplateNameFromTemplateMapping(entry.getValue());

                                Preconditions.checkArgument(
                                        !mTemplateMapping.containsKey(moduleName),
                                        "Duplicated module template map key: " + moduleName);
                                Preconditions.checkArgument(
                                        mTemplateContentMap.containsKey(templateName),
                                        "The template specified in module template map does not"
                                                + " exist: "
                                                + templateName);

                                mTemplateMapping.put(moduleName, templateName);
                            });
        }
    }

    private String getTemplateNameFromTemplateMapping(String name) {
        String fileName = Path.of(name).toString();
        if (fileName.toLowerCase().endsWith(XML_FILE_EXTENSION)) {
            return fileName.substring(0, fileName.length() - XML_FILE_EXTENSION.length());
        }
        return fileName;
    }

    private String getTemplateNameFromTemplateFile(String path) {
        Preconditions.checkArgument(
                path.endsWith(TEMPLATE_FILE_EXTENSION),
                "Unexpected file extension for template path: " + path);
        String fileName = Path.of(mTemplateRoot).relativize(Path.of(path)).toString();
        return fileName.substring(0, fileName.length() - TEMPLATE_FILE_EXTENSION.length());
    }

    public String substitute(String moduleName, Map<String, String> replacementPairs) {
        Preconditions.checkNotNull(
                mDefaultTemplateContent, "The module template object is not fully loaded.");
        return replacementPairs.keySet().stream()
                .reduce(
                        getTemplateContent(moduleName),
                        (res, placeholder) ->
                                res.replace(placeholder, replacementPairs.get(placeholder)));
    }

    private String getTemplateContent(String moduleName) {
        if (!mTemplateMapping.containsKey(moduleName)) {
            return mDefaultTemplateContent;
        }

        return mTemplateContentMap.get(mTemplateMapping.get(moduleName));
    }

    public interface ResourceLoader {
        String load(String resourceName) throws IOException;
    }

    public static final class ClassResourceLoader implements ResourceLoader {
        @Override
        public String load(String resourceName) throws IOException {
            return Resources.toString(
                    getClass().getClassLoader().getResource(resourceName), StandardCharsets.UTF_8);
        }
    }
}

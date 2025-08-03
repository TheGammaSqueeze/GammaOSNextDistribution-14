// Copyright 2020 Google Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package csuite

import (
	"android/soong/android"
	"android/soong/java"
	"strings"
)

var (
	pctx = android.NewPackageContext("android/soong/csuite")
)

func init() {
	android.RegisterModuleType("csuite_test", CSuiteTestFactory)
}

type csuiteTestProperties struct {
	// Local path to a default module template xml file.
	// The content of the template will be used to generate test modules at runtime.
	Test_config_template *string `android:"path"`

	// Local paths to extra module template xml files.
	Extra_test_config_templates []string `android:"path"`

	// Local path to a test plan config xml to be included in the generated plan.
	Test_plan_include *string `android:"path"`
}

type CSuiteTest struct {
	// Java TestHost.
	java.TestHost

	// C-Suite test properties struct.
	csuiteTestProperties csuiteTestProperties
}

func (cSuiteTest *CSuiteTest) buildCopyConfigTemplateCommand(ctx android.ModuleContext, rule *android.RuleBuilder, templatePath string) string {
	if !strings.HasSuffix(templatePath, xmlFileExtension) {
		ctx.ModuleErrorf(`Config template path should ends with ` + xmlFileExtension)
	}

	inputPath := android.PathForModuleSrc(ctx, templatePath)
	genPath := android.PathForModuleGen(ctx, configDirName, ctx.ModuleName(), inputPath.Rel()+configTemplateFileExtension)
	rule.Command().Textf("cp").Input(inputPath).Output(genPath)
	cSuiteTest.AddExtraResource(genPath)
	return genPath.Rel()
}

func (cSuiteTest *CSuiteTest) buildCopyExtraConfigTemplatesCommand(ctx android.ModuleContext, rule *android.RuleBuilder) []string {
	output := make([]string, len(cSuiteTest.csuiteTestProperties.Extra_test_config_templates))

	for idx, templatePath := range cSuiteTest.csuiteTestProperties.Extra_test_config_templates {
		output[idx] = cSuiteTest.buildCopyConfigTemplateCommand(ctx, rule, templatePath)
	}

	return output
}

func (cSuiteTest *CSuiteTest) buildCopyPlanIncludeCommand(ctx android.ModuleContext, rule *android.RuleBuilder) string {
	if cSuiteTest.csuiteTestProperties.Test_plan_include == nil {
		return emptyPlanIncludePath
	}
	inputPath := android.PathForModuleSrc(ctx, *cSuiteTest.csuiteTestProperties.Test_plan_include)
	genPath := android.PathForModuleGen(ctx, configDirName, "includes", ctx.ModuleName()+".xml")
	rule.Command().Textf("cp").Input(inputPath).Output(genPath)
	cSuiteTest.AddExtraResource(genPath)
	return strings.Replace(genPath.Rel(), "config/", "", -1)
}

func (cSuiteTest *CSuiteTest) buildWritePlanConfigRule(ctx android.ModuleContext, configTemplatePath string, extraConfigTemplatePaths []string, planIncludePath string) {
	planName := ctx.ModuleName()
	content := strings.Replace(planTemplate, "{planName}", planName, -1)
	content = strings.Replace(content, "{templatePath}", configTemplatePath, -1)
	content = strings.Replace(content, "{templateRoot}", android.PathForModuleGen(ctx, configDirName, ctx.ModuleName()).Rel(), -1)
	content = strings.Replace(content, "{planInclude}", planIncludePath, -1)

	extraTemplateConfigLines := ""
	for _, extraPath := range extraConfigTemplatePaths {
		extraTemplateConfigLines += strings.Replace(extraTemplatePathsTemplate, "{templatePath}", extraPath, -1)
	}
	content = strings.Replace(content, "{extraTemplatePaths}", extraTemplateConfigLines, -1)

	genPath := android.PathForModuleGen(ctx, configDirName, planName+xmlFileExtension)
	android.WriteFileRule(ctx, genPath, content)
	cSuiteTest.AddExtraResource(genPath)
}

func (cSuiteTest *CSuiteTest) GenerateAndroidBuildActions(ctx android.ModuleContext) {
	rule := android.NewRuleBuilder(pctx, ctx)

	if cSuiteTest.csuiteTestProperties.Test_config_template == nil {
		ctx.ModuleErrorf(`'test_config_template' is missing.`)
		return
	}

	configTemplatePath := cSuiteTest.buildCopyConfigTemplateCommand(ctx, rule, *cSuiteTest.csuiteTestProperties.Test_config_template)
	extraConfigTemplatePaths := cSuiteTest.buildCopyExtraConfigTemplatesCommand(ctx, rule)
	planIncludePath := cSuiteTest.buildCopyPlanIncludeCommand(ctx, rule)
	cSuiteTest.buildWritePlanConfigRule(ctx, configTemplatePath, extraConfigTemplatePaths, planIncludePath)

	rule.Build("CSuite", "generate C-Suite config files")
	cSuiteTest.TestHost.GenerateAndroidBuildActions(ctx)
}

func CSuiteTestFactory() android.Module {
	module := &CSuiteTest{}
	module.AddProperties(&module.csuiteTestProperties)
	installable := true
	autoGenConfig := false
	java.InitTestHost(&module.TestHost, &installable, []string{"csuite"}, &autoGenConfig)

	java.InitJavaModuleMultiTargets(module, android.HostSupported)

	return module
}

const (
	emptyPlanIncludePath        = `empty`
	configDirName               = `config`
	configTemplateFileExtension = `.template`
	xmlFileExtension            = `.xml`
	extraTemplatePathsTemplate  = `
          <option name="extra-templates" value="{templatePath}"/>`
	planTemplate = `<?xml version="1.0" encoding="utf-8"?>
<!-- Copyright (C) 2020 The Android Open Source Project

     Licensed under the Apache License, Version 2.0 (the "License");
     you may not use this file except in compliance with the License.
     You may obtain a copy of the License at

          http://www.apache.org/licenses/LICENSE-2.0

     Unless required by applicable law or agreed to in writing, software
     distributed under the License is distributed on an "AS IS" BASIS,
     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     See the License for the specific language governing permissions and
     limitations under the License.
-->
<configuration>
     <!-- Generates module files in the beginning of the test. -->
     <test class="com.android.csuite.core.ModuleGenerator" />
     <!-- Cleans the generated module files after the test. -->
     <target_preparer class="com.android.csuite.core.ModuleGenerator" />
     <object type="MODULE_TEMPLATE_PROVIDER" class="com.android.csuite.core.ModuleTemplate" >
        <option name="template-root" value="{templateRoot}" />
        <option name="default-template" value="{templatePath}" />{extraTemplatePaths}
    </object>

     <include name="csuite-base" />
     <include name="{planInclude}" />
     <option name="plan" value="{planName}" />
</configuration>
`
)

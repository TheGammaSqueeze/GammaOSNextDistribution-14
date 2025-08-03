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
	"io/ioutil"
	"os"
	"strings"
	"testing"
)

var buildDir string

func TestBpContainsTestHostPropsThrowsError(t *testing.T) {
	createContextAndConfigExpectingErrors(t, `
		csuite_test {
			name: "plan_name",
			test_config_template: "config_template.xml",
			data_native_bins: "bin"
		}
	`,
		"unrecognized property",
	)
}

func TestBpContainsManifestThrowsError(t *testing.T) {
	createContextAndConfigExpectingErrors(t, `
		csuite_test {
			name: "plan_name",
			test_config_template: "config_template.xml",
			test_config: "AndroidTest.xml"
		}
	`,
		"unrecognized property",
	)
}

func TestBpMissingNameThrowsError(t *testing.T) {
	createContextAndConfigExpectingErrors(t, `
		csuite_test {
			test_config_template: "config_template.xml"
		}
	`,
		`'name' is missing`,
	)
}

func TestBpMissingTemplatePathThrowsError(t *testing.T) {
	createContextAndConfigExpectingErrors(t, `
		csuite_test {
			name: "plan_name",
		}
	`,
		`'test_config_template' is missing`,
	)
}

func TestBpTemplatePathUnexpectedFileExtensionThrowsError(t *testing.T) {
	createContextAndConfigExpectingErrors(t, `
		csuite_test {
			name: "plan_name",
			test_config_template: "config_template.xml.template"
		}
	`,
		`Config template path should ends with .xml`,
	)
}

func TestBpExtraTemplateUnexpectedFileExtensionThrowsError(t *testing.T) {
	createContextAndConfigExpectingErrors(t, `
		csuite_test {
			name: "plan_name",
			test_config_template: "config_template.xml",
			extra_test_config_templates: ["another.xml.template"]
		}
	`,
		`Config template path should ends with .xml`,
	)
}

func TestBpValidExtraTemplateDoesNotThrowError(t *testing.T) {
	createContextAndConfig(t, `
		csuite_test {
			name: "plan_name",
			test_config_template: "config_template.xml",
			extra_test_config_templates: ["another.xml"]
		}
	`)
}

func TestValidBpMissingPlanIncludeDoesNotThrowError(t *testing.T) {
	createContextAndConfig(t, `
		csuite_test {
			name: "plan_name",
			test_config_template: "config_template.xml"
		}
	`)
}

func TestValidBpMissingPlanIncludeGeneratesPlanXmlWithoutPlaceholders(t *testing.T) {
	ctx, config := createContextAndConfig(t, `
		csuite_test {
			name: "plan_name",
			test_config_template: "config_template.xml"
		}
	`)

	module := ctx.ModuleForTests("plan_name", config.BuildOS.String()+"_common")
	content := android.ContentFromFileRuleForTests(t, ctx, module.Output("config/plan_name.xml"))
	if strings.Contains(content, "{") || strings.Contains(content, "}") {
		t.Errorf("The generated plan name contains a placeholder: %s", content)
	}
}

func TestGeneratedTestPlanContainsPlanName(t *testing.T) {
	ctx, config := createContextAndConfig(t, `
		csuite_test {
			name: "plan_name",
			test_config_template: "config_template.xml"
		}
	`)

	module := ctx.ModuleForTests("plan_name", config.BuildOS.String()+"_common")
	content := android.ContentFromFileRuleForTests(t, ctx, module.Output("config/plan_name.xml"))
	if !strings.Contains(content, "plan_name") {
		t.Errorf("The plan name is missing from the generated plan: %s", content)
	}
}

func TestGeneratedTestPlanContainsTemplatePath(t *testing.T) {
	ctx, config := createContextAndConfig(t, `
		csuite_test {
			name: "plan_name",
			test_config_template: "config_template.xml"
		}
	`)

	module := ctx.ModuleForTests("plan_name", config.BuildOS.String()+"_common")
	content := android.ContentFromFileRuleForTests(t, ctx, module.Output("config/plan_name.xml"))
	if !strings.Contains(content, "config/plan_name/config_template.xml.template") {
		t.Errorf("The template path is missing from the generated plan: %s", content)
	}
}

func TestGeneratedTestPlanContainsExtraTemplatePath(t *testing.T) {
	ctx, config := createContextAndConfig(t, `
		csuite_test {
			name: "plan_name",
			test_config_template: "config_template.xml",
			extra_test_config_templates: ["extra.xml"]
		}
	`)

	module := ctx.ModuleForTests("plan_name", config.BuildOS.String()+"_common")
	content := android.ContentFromFileRuleForTests(t, ctx, module.Output("config/plan_name.xml"))
	if !strings.Contains(content, "config/plan_name/extra.xml.template") {
		t.Errorf("The extra template path is missing from the generated plan: %s", content)
	}
	if !strings.Contains(content, "extra-templates") {
		t.Errorf("The extra-templates param is missing from the generated plan: %s", content)
	}
}

func TestGeneratedTestPlanDoesNotContainExtraTemplatePath(t *testing.T) {
	ctx, config := createContextAndConfig(t, `
		csuite_test {
			name: "plan_name",
			test_config_template: "config_template.xml"
		}
	`)

	module := ctx.ModuleForTests("plan_name", config.BuildOS.String()+"_common")
	content := android.ContentFromFileRuleForTests(t, ctx, module.Output("config/plan_name.xml"))
	if strings.Contains(content, "extra-templates") {
		t.Errorf("The extra-templates param should not be included in the generated plan: %s", content)
	}
}

func TestTemplateFileCopyRuleExists(t *testing.T) {
	ctx, config := createContextAndConfig(t, `
		csuite_test {
			name: "plan_name",
			test_config_template: "config_template.xml"
		}
	`)

	params := ctx.ModuleForTests("plan_name", config.BuildOS.String()+"_common").Rule("CSuite")
	assertFileCopyRuleExists(t, params, "config_template.xml", "config/plan_name/config_template.xml.template")
}

func TestExtraTemplateFileCopyRuleExists(t *testing.T) {
	ctx, config := createContextAndConfig(t, `
		csuite_test {
			name: "plan_name",
			test_config_template: "config_template.xml",
			extra_test_config_templates: ["extra.xml"]
		}
	`)

	params := ctx.ModuleForTests("plan_name", config.BuildOS.String()+"_common").Rule("CSuite")
	assertFileCopyRuleExists(t, params, "config_template.xml", "config/plan_name/extra.xml.template")
}

func TestGeneratedTestPlanContainsPlanInclude(t *testing.T) {
	ctx, config := createContextAndConfig(t, `
		csuite_test {
			name: "plan_name",
			test_config_template: "config_template.xml",
			test_plan_include: "include.xml"
		}
	`)

	module := ctx.ModuleForTests("plan_name", config.BuildOS.String()+"_common")
	content := android.ContentFromFileRuleForTests(t, ctx, module.Output("config/plan_name.xml"))
	if !strings.Contains(content, `"includes/plan_name.xml"`) {
		t.Errorf("The plan include path is missing from the generated plan: %s", content)
	}
}

func TestPlanIncludeFileCopyRuleExists(t *testing.T) {
	ctx, config := createContextAndConfig(t, `
		csuite_test {
			name: "plan_name",
			test_config_template: "config_template.xml",
			test_plan_include: "include.xml"
		}
	`)

	params := ctx.ModuleForTests("plan_name", config.BuildOS.String()+"_common").Rule("CSuite")
	assertFileCopyRuleExists(t, params, "include.xml", "config/includes/plan_name.xml")
}

func TestMain(m *testing.M) {
	run := func() int {
		setUp()
		defer tearDown()

		return m.Run()
	}

	os.Exit(run())
}

func assertFileCopyRuleExists(t *testing.T, params android.TestingBuildParams, src string, dst string) {
	assertPathsContains(t, getAllInputPaths(params), src)
	assertWritablePathsContainsRel(t, getAllOutputPaths(params), dst)
	if !strings.HasPrefix(params.RuleParams.Command, "cp") {
		t.Errorf("'cp' command is missing.")
	}
}

func assertPathsContains(t *testing.T, paths android.Paths, path string) {
	for _, p := range paths {
		if p.String() == path {
			return
		}
	}
	t.Errorf("Cannot find expected path %s", path)
}

func assertWritablePathsContainsRel(t *testing.T, paths android.WritablePaths, relPath string) {
	for _, path := range paths {
		if path.Rel() == relPath {
			return
		}
	}
	t.Errorf("Cannot find expected relative path %s", relPath)
}

func getAllOutputPaths(params android.TestingBuildParams) android.WritablePaths {
	var paths []android.WritablePath
	if params.Output != nil {
		paths = append(paths, params.Output)
	}
	if params.ImplicitOutput != nil {
		paths = append(paths, params.ImplicitOutput)
	}
	paths = append(paths, params.Outputs...)
	paths = append(paths, params.ImplicitOutputs...)

	return paths
}

func getAllInputPaths(params android.TestingBuildParams) android.Paths {
	var paths []android.Path
	if params.Input != nil {
		paths = append(paths, params.Input)
	}
	if params.Implicit != nil {
		paths = append(paths, params.Implicit)
	}
	paths = append(paths, params.Inputs...)
	paths = append(paths, params.Implicits...)

	return paths
}

func setUp() {
	var err error
	buildDir, err = ioutil.TempDir("", "soong_csuite_test")
	if err != nil {
		panic(err)
	}
}

func tearDown() {
	os.RemoveAll(buildDir)
}

func createContextAndConfig(t *testing.T, bp string) (*android.TestContext, android.Config) {
	return createContextAndConfigExpectingErrors(t, bp, "")
}

func createContextAndConfigExpectingErrors(t *testing.T, bp string, error string) (*android.TestContext, android.Config) {
	t.Helper()

	testPreparer := android.GroupFixturePreparers(
		java.PrepareForTestWithJavaDefaultModules,
		android.FixtureRegisterWithContext(func(ctx android.RegistrationContext) {
			ctx.RegisterModuleType("csuite_test", CSuiteTestFactory)
		}),
		android.FixtureWithRootAndroidBp(bp),
	)

	if error != "" {
		testPreparer = testPreparer.ExtendWithErrorHandler(android.FixtureExpectsOneErrorPattern(error))
	}

	result := testPreparer.RunTest(t)

	return result.TestContext, result.Config
}

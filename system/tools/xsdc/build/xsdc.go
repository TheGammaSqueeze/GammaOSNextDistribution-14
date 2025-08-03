// Copyright 2018 Google Inc. All rights reserved.
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

package xsdc

import (
	"android/soong/android"
	"android/soong/java"
	"fmt"
	"path/filepath"
	"strings"

	"github.com/google/blueprint"
	"github.com/google/blueprint/proptools"
)

func init() {
	pctx.Import("android/soong/java/config")
	android.RegisterModuleType("xsd_config", xsdConfigFactory)

	android.PreArchMutators(func(ctx android.RegisterMutatorsContext) {
		ctx.TopDown("xsd_config", xsdConfigMutator).Parallel()
	})
}

var (
	pctx = android.NewPackageContext("android/xsdc")

	xsdc = pctx.HostBinToolVariable("xsdcCmd", "xsdc")

	xsdConfigRule = pctx.StaticRule("xsdConfigRule", blueprint.RuleParams{
		Command:     "cp -f ${in} ${output}",
		Description: "copy the xsd file: ${in} => ${output}",
	}, "output")
)

type xsdConfigProperties struct {
	Srcs         []string
	Package_name *string
	Api_dir      *string
	Gen_writer   *bool
	Nullability  *bool

	// Whether has{element or atrribute} methods are set to public.
	// It is not applied to C++, because these methods are always
	// generated to public for C++.
	Gen_has *bool
	// Only generate code for enum converters. Applies to C++ only.
	// This is useful for memory footprint reduction since it avoids
	// depending on libxml2.
	Enums_only *bool
	// Only generate complementary code for XML parser. Applies to C++ only.
	// The code being generated depends on the enum converters module.
	Parser_only *bool
	// Whether getter name of boolean element or attribute is getX or isX.
	// Default value is false. If the property is true, getter name is isX.
	Boolean_getter *bool
	// Generate code that uses libtinyxml2 instead of libxml2. Applies to
	// C++ only and does not perform the XInclude substitution, or
	// ENTITY_REFs.
	// This can improve memory footprint. Default value is false.
	Tinyxml *bool
	// Specify root elements explicitly. If not set, XSDC generates parsers and
	// writers for all elements which can be root element. When set, XSDC
	// generates parsers and writers for specified root elements. This can be
	// used to avoid unnecessary code.
	Root_elements []string
	// Additional xsd files included by the main xsd file using xs:include
	// The paths are relative to the module directory.
	Include_files []string
}

type xsdConfig struct {
	android.ModuleBase

	properties xsdConfigProperties

	genOutputDir android.Path
	genOutputs_j android.WritablePath
	genOutputs_c android.WritablePaths
	genOutputs_h android.WritablePaths

	docsPath android.Path

	xsdConfigPath         android.Path
	xsdIncludeConfigPaths android.Paths
	genOutputs            android.WritablePaths
}

var _ android.SourceFileProducer = (*xsdConfig)(nil)

type ApiToCheck struct {
	Api_file         *string
	Removed_api_file *string
	Args             *string
}

type CheckApi struct {
	Last_released ApiToCheck
	Current       ApiToCheck
}
type DroidstubsProperties struct {
	Name                 *string
	Installable          *bool
	Srcs                 []string
	Sdk_version          *string
	Args                 *string
	Api_filename         *string
	Removed_api_filename *string
	Check_api            CheckApi
}

func (module *xsdConfig) GeneratedSourceFiles() android.Paths {
	return module.genOutputs_c.Paths()
}

func (module *xsdConfig) Srcs() android.Paths {
	var srcs android.WritablePaths
	srcs = append(srcs, module.genOutputs...)
	srcs = append(srcs, module.genOutputs_j)
	return srcs.Paths()
}

func (module *xsdConfig) GeneratedDeps() android.Paths {
	return module.genOutputs_h.Paths()
}

func (module *xsdConfig) GeneratedHeaderDirs() android.Paths {
	return android.Paths{module.genOutputDir}
}

func (module *xsdConfig) DepsMutator(ctx android.BottomUpMutatorContext) {
	android.ExtractSourcesDeps(ctx, module.properties.Srcs)
}

func (module *xsdConfig) generateXsdConfig(ctx android.ModuleContext) {
	output := android.PathForModuleGen(ctx, module.Name()+".xsd")
	module.genOutputs = append(module.genOutputs, output)

	ctx.ModuleBuild(pctx, android.ModuleBuildParams{
		Rule:   xsdConfigRule,
		Input:  module.xsdConfigPath,
		Output: output,
		Args: map[string]string{
			"output": output.String(),
		},
	})
}

// This creates a ninja rule to convert xsd file to java sources
// The ninja rule runs in a sandbox
func (module *xsdConfig) generateJavaSrcInSbox(ctx android.ModuleContext, args string) {
	rule := android.NewRuleBuilder(pctx, ctx).
		Sbox(android.PathForModuleGen(ctx, "java"),
			android.PathForModuleGen(ctx, "java.sbox.textproto")).
		SandboxInputs()
	// Run xsdc tool to generate sources
	genCmd := rule.Command()
	genCmd.
		BuiltTool("xsdc").
		ImplicitTool(ctx.Config().HostJavaToolPath(ctx, "xsdc.jar")).
		Input(module.xsdConfigPath).
		FlagWithArg("-p ", *module.properties.Package_name).
		// Soong will change execution root to sandbox root. Generate srcs relative to that.
		Flag("-o ").OutputDir("xsdc").
		FlagWithArg("-j ", args)
	if module.xsdIncludeConfigPaths != nil {
		genCmd.Implicits(module.xsdIncludeConfigPaths)
	}
	if module.docsPath != nil {
		genCmd.Implicit(module.docsPath)
	}
	// Zip the source file to a srcjar
	rule.Command().
		BuiltTool("soong_zip").
		Flag("-jar").
		FlagWithOutput("-o ", module.genOutputs_j).
		Flag("-C ").OutputDir("xsdc").
		Flag("-D ").OutputDir("xsdc")

	rule.Build("xsdc_java_"+module.xsdConfigPath.String(), "xsdc java")
}

// This creates a ninja rule to convert xsd file to cpp sources
// The ninja rule runs in a sandbox
func (module *xsdConfig) generateCppSrcInSbox(ctx android.ModuleContext, args string) {
	outDir := android.PathForModuleGen(ctx, "cpp")
	rule := android.NewRuleBuilder(pctx, ctx).
		Sbox(outDir,
			android.PathForModuleGen(ctx, "cpp.sbox.textproto")).
		SandboxInputs()
	// Run xsdc tool to generate sources
	genCmd := rule.Command()
	genCmd.
		BuiltTool("xsdc").
		ImplicitTool(ctx.Config().HostJavaToolPath(ctx, "xsdc.jar")).
		Input(module.xsdConfigPath).
		FlagWithArg("-p ", *module.properties.Package_name).
		// Soong will change execution root to sandbox root. Generate srcs relative to that.
		Flag("-o ").OutputDir().
		FlagWithArg("-c ", args).
		ImplicitOutputs(module.genOutputs_c).
		ImplicitOutputs(module.genOutputs_h)
	if module.xsdIncludeConfigPaths != nil {
		genCmd.Implicits(module.xsdIncludeConfigPaths)
	}

	rule.Build("xsdc_cpp_"+module.xsdConfigPath.String(), "xsdc cpp")
}

func (module *xsdConfig) GenerateAndroidBuildActions(ctx android.ModuleContext) {
	if len(module.properties.Srcs) != 1 {
		ctx.PropertyErrorf("srcs", "xsd_config must be one src")
	}

	ctx.VisitDirectDeps(func(to android.Module) {
		if doc, ok := to.(java.ApiFilePath); ok {
			docsPath, err := doc.ApiFilePath(java.Everything)
			if err != nil {
				ctx.ModuleErrorf(err.Error())
			} else {
				module.docsPath = docsPath
			}
		}
	})

	srcFiles := ctx.ExpandSources(module.properties.Srcs, nil)
	module.xsdConfigPath = srcFiles[0]
	module.xsdIncludeConfigPaths = android.PathsForModuleSrc(ctx, module.properties.Include_files)

	pkgName := *module.properties.Package_name
	filenameStem := strings.Replace(pkgName, ".", "_", -1)

	args := ""
	if proptools.Bool(module.properties.Gen_writer) {
		args = "-w"
	}

	if proptools.Bool(module.properties.Nullability) {
		args = args + " -n "
	}

	if proptools.Bool(module.properties.Gen_has) {
		args = args + " -g "
	}

	if proptools.Bool(module.properties.Enums_only) {
		args = args + " -e "
	}

	if proptools.Bool(module.properties.Parser_only) {
		args = args + " -x "
	}

	if proptools.Bool(module.properties.Boolean_getter) {
		args = args + " -b "
	}

	if proptools.Bool(module.properties.Tinyxml) {
		args = args + " -t "
	}

	for _, elem := range module.properties.Root_elements {
		args = args + " -r " + elem
	}

	module.genOutputs_j = android.PathForModuleGen(ctx, "java", filenameStem+"_xsdcgen.srcjar")

	module.generateJavaSrcInSbox(ctx, args)

	if proptools.Bool(module.properties.Enums_only) {
		module.genOutputs_c = android.WritablePaths{
			android.PathForModuleGen(ctx, "cpp", filenameStem+"_enums.cpp")}
		module.genOutputs_h = android.WritablePaths{
			android.PathForModuleGen(ctx, "cpp", "include/"+filenameStem+"_enums.h")}
	} else if proptools.Bool(module.properties.Parser_only) {
		module.genOutputs_c = android.WritablePaths{
			android.PathForModuleGen(ctx, "cpp", filenameStem+".cpp")}
		module.genOutputs_h = android.WritablePaths{
			android.PathForModuleGen(ctx, "cpp", "include/"+filenameStem+".h")}
	} else {
		module.genOutputs_c = android.WritablePaths{
			android.PathForModuleGen(ctx, "cpp", filenameStem+".cpp"),
			android.PathForModuleGen(ctx, "cpp", filenameStem+"_enums.cpp")}
		module.genOutputs_h = android.WritablePaths{
			android.PathForModuleGen(ctx, "cpp", "include/"+filenameStem+".h"),
			android.PathForModuleGen(ctx, "cpp", "include/"+filenameStem+"_enums.h")}
	}

	module.genOutputDir = android.PathForModuleGen(ctx, "cpp", "include")

	module.generateCppSrcInSbox(ctx, args)

	module.generateXsdConfig(ctx)
}

func xsdConfigMutator(mctx android.TopDownMutatorContext) {
	if module, ok := mctx.Module().(*xsdConfig); ok {
		name := module.BaseModuleName()

		args := " --stub-packages " + *module.properties.Package_name +
			" --hide MissingPermission --hide BroadcastBehavior" +
			" --hide HiddenSuperclass --hide DeprecationMismatch --hide UnavailableSymbol" +
			" --hide SdkConstant --hide HiddenTypeParameter --hide Todo"

		api_dir := proptools.StringDefault(module.properties.Api_dir, "api")

		currentApiFileName := filepath.Join(api_dir, "current.txt")
		removedApiFileName := filepath.Join(api_dir, "removed.txt")

		check_api := CheckApi{}

		check_api.Current.Api_file = proptools.StringPtr(currentApiFileName)
		check_api.Current.Removed_api_file = proptools.StringPtr(removedApiFileName)

		check_api.Last_released.Api_file = proptools.StringPtr(
			filepath.Join(api_dir, "last_current.txt"))
		check_api.Last_released.Removed_api_file = proptools.StringPtr(
			filepath.Join(api_dir, "last_removed.txt"))

		mctx.CreateModule(java.DroidstubsFactory, &DroidstubsProperties{
			Name:                 proptools.StringPtr(name + ".docs"),
			Srcs:                 []string{":" + name},
			Args:                 proptools.StringPtr(args),
			Api_filename:         proptools.StringPtr(currentApiFileName),
			Removed_api_filename: proptools.StringPtr(removedApiFileName),
			Check_api:            check_api,
			Installable:          proptools.BoolPtr(false),
			Sdk_version:          proptools.StringPtr("core_platform"),
		})
	}
}

func xsdConfigFactory() android.Module {
	module := &xsdConfig{}
	module.AddProperties(&module.properties)
	android.InitAndroidModule(module)

	return module
}

func (module *xsdConfig) OutputFiles(tag string) (android.Paths, error) {
	switch tag {
	case "":
		var outputs android.WritablePaths
		outputs = append(outputs, module.genOutputs_j)
		outputs = append(outputs, module.genOutputs_c...)
		outputs = append(outputs, module.genOutputs_h...)
		outputs = append(outputs, module.genOutputs...)
		return outputs.Paths(), nil
	case "java":
		return android.Paths{module.genOutputs_j}, nil
	case "cpp":
		return module.genOutputs_c.Paths(), nil
	case "h":
		return module.genOutputs_h.Paths(), nil
	default:
		return nil, fmt.Errorf("unsupported module reference tag %q", tag)
	}
}

var _ android.OutputFileProducer = (*xsdConfig)(nil)

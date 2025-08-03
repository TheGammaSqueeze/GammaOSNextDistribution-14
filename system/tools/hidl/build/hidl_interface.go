// Copyright (C) 2017 The Android Open Source Project
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

package hidl

import (
	"fmt"
	"strings"

	"github.com/google/blueprint"
	"github.com/google/blueprint/proptools"

	"android/soong/android"
	"android/soong/cc"
	"android/soong/genrule"
	"android/soong/java"
)

var (
	hidlInterfaceSuffix       = "_interface"
	hidlMetadataSingletonName = "hidl_metadata_json"

	pctx = android.NewPackageContext("android/hidl")

	hidl             = pctx.HostBinToolVariable("hidl", "hidl-gen")
	hidlLint         = pctx.HostBinToolVariable("lint", "hidl-lint")
	soong_zip        = pctx.HostBinToolVariable("soong_zip", "soong_zip")
	intermediatesDir = pctx.IntermediatesPathVariable("intermediatesDir", "")

	hidlRule = pctx.StaticRule("hidlRule", blueprint.RuleParams{
		Depfile:     "${out}.d",
		Deps:        blueprint.DepsGCC,
		Command:     "rm -rf ${genDir} && ${hidl} -R -p . -d ${out}.d -o ${genDir} -L ${language} ${options} ${fqName}",
		CommandDeps: []string{"${hidl}"},
		Description: "HIDL ${language}: ${in} => ${out}",
	}, "fqName", "genDir", "language", "options")

	hidlSrcJarRule = pctx.StaticRule("hidlSrcJarRule", blueprint.RuleParams{
		Depfile: "${out}.d",
		Deps:    blueprint.DepsGCC,
		Command: "rm -rf ${genDir} && " +
			"${hidl} -R -p . -d ${out}.d -o ${genDir}/srcs -L ${language} ${options} ${fqName} && " +
			"${soong_zip} -o ${genDir}/srcs.srcjar -C ${genDir}/srcs -D ${genDir}/srcs",
		CommandDeps: []string{"${hidl}", "${soong_zip}"},
		Description: "HIDL ${language}: ${in} => srcs.srcjar",
	}, "fqName", "genDir", "language", "options")

	lintRule = pctx.StaticRule("lintRule", blueprint.RuleParams{
		Command:     "rm -f ${output} && touch ${output} && ${lint} -j -e -R -p . ${options} ${fqName} > ${output}",
		CommandDeps: []string{"${lint}"},
		Description: "hidl-lint ${fqName}: ${out}",
	}, "output", "options", "fqName")

	zipLintRule = pctx.StaticRule("zipLintRule", blueprint.RuleParams{
		Rspfile:        "$out.rsp",
		RspfileContent: "$files",
		Command:        "rm -f ${output} && ${soong_zip} -o ${output} -C ${intermediatesDir} -l ${out}.rsp",
		CommandDeps:    []string{"${soong_zip}"},
		Description:    "Zipping hidl-lints into ${output}",
	}, "output", "files")

	inheritanceHierarchyRule = pctx.StaticRule("inheritanceHierarchyRule", blueprint.RuleParams{
		Command:     "rm -f ${out} && ${hidl} -L inheritance-hierarchy ${options} ${fqInterface} > ${out}",
		CommandDeps: []string{"${hidl}"},
		Description: "HIDL inheritance hierarchy: ${fqInterface} => ${out}",
	}, "options", "fqInterface")

	joinJsonObjectsToArrayRule = pctx.StaticRule("joinJsonObjectsToArrayRule", blueprint.RuleParams{
		Rspfile:        "$out.rsp",
		RspfileContent: "$files",
		Command: "rm -rf ${out} && " +
			// Start the output array with an opening bracket.
			"echo '[' >> ${out} && " +
			// Add prebuilt declarations
			"echo \"${extras}\" >> ${out} && " +
			// Append each input file and a comma to the output.
			"for file in $$(cat ${out}.rsp); do " +
			"cat $$file >> ${out}; echo ',' >> ${out}; " +
			"done && " +
			// Remove the last comma, replacing it with the closing bracket.
			"sed -i '$$d' ${out} && echo ']' >> ${out}",
		Description: "Joining JSON objects into array ${out}",
	}, "extras", "files")
)

func init() {
	android.RegisterModuleType("prebuilt_hidl_interfaces", prebuiltHidlInterfaceFactory)
	android.RegisterModuleType("hidl_interface", HidlInterfaceFactory)
	android.RegisterParallelSingletonType("all_hidl_lints", allHidlLintsFactory)
	android.RegisterModuleType("hidl_interfaces_metadata", hidlInterfacesMetadataSingletonFactory)
	pctx.Import("android/soong/android")
}

func hidlInterfacesMetadataSingletonFactory() android.Module {
	i := &hidlInterfacesMetadataSingleton{}
	android.InitAndroidModule(i)
	return i
}

type hidlInterfacesMetadataSingleton struct {
	android.ModuleBase

	inheritanceHierarchyPath android.OutputPath
}

var _ android.OutputFileProducer = (*hidlInterfacesMetadataSingleton)(nil)

func (m *hidlInterfacesMetadataSingleton) GenerateAndroidBuildActions(ctx android.ModuleContext) {
	if m.Name() != hidlMetadataSingletonName {
		ctx.PropertyErrorf("name", "must be %s", hidlMetadataSingletonName)
		return
	}

	var inheritanceHierarchyOutputs android.Paths
	additionalInterfaces := []string{}
	ctx.VisitDirectDeps(func(m android.Module) {
		if !m.ExportedToMake() {
			return
		}
		if t, ok := m.(*hidlGenRule); ok {
			if t.properties.Language == "inheritance-hierarchy" {
				inheritanceHierarchyOutputs = append(inheritanceHierarchyOutputs, t.genOutputs.Paths()...)
			}
		} else if t, ok := m.(*prebuiltHidlInterface); ok {
			additionalInterfaces = append(additionalInterfaces, t.properties.Interfaces...)
		}
	})

	m.inheritanceHierarchyPath = android.PathForIntermediates(ctx, "hidl_inheritance_hierarchy.json")

	ctx.Build(pctx, android.BuildParams{
		Rule:   joinJsonObjectsToArrayRule,
		Inputs: inheritanceHierarchyOutputs,
		Output: m.inheritanceHierarchyPath,
		Args: map[string]string{
			"extras": strings.Join(wrap("{\\\"interface\\\":\\\"", additionalInterfaces, "\\\"},"), " "),
			"files":  strings.Join(inheritanceHierarchyOutputs.Strings(), " "),
		},
	})
}

func (m *hidlInterfacesMetadataSingleton) OutputFiles(tag string) (android.Paths, error) {
	if tag != "" {
		return nil, fmt.Errorf("unsupported tag %q", tag)
	}

	return android.Paths{m.inheritanceHierarchyPath}, nil
}

func allHidlLintsFactory() android.Singleton {
	return &allHidlLintsSingleton{}
}

type allHidlLintsSingleton struct {
	outPath string
}

func (m *allHidlLintsSingleton) GenerateBuildActions(ctx android.SingletonContext) {
	var hidlLintOutputs android.Paths
	ctx.VisitAllModules(func(m android.Module) {
		if t, ok := m.(*hidlGenRule); ok {
			if t.properties.Language == "lint" {
				if len(t.genOutputs) == 1 {
					hidlLintOutputs = append(hidlLintOutputs, t.genOutputs[0])
				} else {
					panic("-hidl-lint target was not configured correctly")
				}
			}
		}
	})

	outPath := android.PathForIntermediates(ctx, "hidl-lint.zip")
	m.outPath = outPath.String()

	ctx.Build(pctx, android.BuildParams{
		Rule:   zipLintRule,
		Inputs: hidlLintOutputs,
		Output: outPath,
		Args: map[string]string{
			"output": outPath.String(),
			"files":  strings.Join(hidlLintOutputs.Strings(), " "),
		},
	})
}

func (m *allHidlLintsSingleton) MakeVars(ctx android.MakeVarsContext) {
	ctx.Strict("ALL_HIDL_LINTS_ZIP", m.outPath)
}

type hidlGenProperties struct {
	Language       string
	FqName         string
	Root           string
	Interfaces     []string
	Inputs         []string
	Outputs        []string
	Apex_available []string
}

type hidlGenRule struct {
	android.ModuleBase

	properties hidlGenProperties

	genOutputDir android.Path
	genInputs    android.Paths
	genOutputs   android.WritablePaths
}

var _ android.SourceFileProducer = (*hidlGenRule)(nil)
var _ genrule.SourceFileGenerator = (*hidlGenRule)(nil)

func (g *hidlGenRule) GenerateAndroidBuildActions(ctx android.ModuleContext) {
	g.genOutputDir = android.PathForModuleGen(ctx)

	for _, input := range g.properties.Inputs {
		g.genInputs = append(g.genInputs, android.PathForModuleSrc(ctx, input))
	}

	var interfaces []string
	for _, src := range g.properties.Inputs {
		if strings.HasSuffix(src, ".hal") && strings.HasPrefix(src, "I") {
			interfaces = append(interfaces, strings.TrimSuffix(src, ".hal"))
		}
	}

	switch g.properties.Language {
	case "lint":
		g.genOutputs = append(g.genOutputs, android.PathForModuleGen(ctx, "lint.json"))
	case "inheritance-hierarchy":
		for _, intf := range interfaces {
			g.genOutputs = append(g.genOutputs, android.PathForModuleGen(ctx, intf+"_inheritance_hierarchy.json"))
		}
	default:
		for _, output := range g.properties.Outputs {
			g.genOutputs = append(g.genOutputs, android.PathForModuleGen(ctx, output))
		}
	}

	var extraOptions []string // including roots
	var currentPath android.OptionalPath
	ctx.VisitDirectDeps(func(dep android.Module) {
		switch t := dep.(type) {
		case *hidlInterface:
			extraOptions = append(extraOptions, t.properties.Full_root_option)
		case *hidlPackageRoot:
			if currentPath.Valid() {
				panic(fmt.Sprintf("Expecting only one path, but found %v %v", currentPath, t.getCurrentPath()))
			}

			currentPath = t.getCurrentPath()

			if t.requireFrozen() {
				extraOptions = append(extraOptions, "-F")
			}
		}
	})

	extraOptions = android.FirstUniqueStrings(extraOptions)

	inputs := g.genInputs
	if currentPath.Valid() {
		inputs = append(inputs, currentPath.Path())
	}

	rule := hidlRule
	if g.properties.Language == "java" {
		rule = hidlSrcJarRule
	}

	if g.properties.Language == "lint" {
		ctx.Build(pctx, android.BuildParams{
			Rule:   lintRule,
			Inputs: inputs,
			Output: g.genOutputs[0],
			Args: map[string]string{
				"output":  g.genOutputs[0].String(),
				"fqName":  g.properties.FqName,
				"options": strings.Join(extraOptions, " "),
			},
		})

		return
	}

	if g.properties.Language == "inheritance-hierarchy" {
		for i, intf := range interfaces {
			ctx.Build(pctx, android.BuildParams{
				Rule:   inheritanceHierarchyRule,
				Inputs: inputs,
				Output: g.genOutputs[i],
				Args: map[string]string{
					"fqInterface": g.properties.FqName + "::" + intf,
					"options":     strings.Join(extraOptions, " "),
				},
			})
		}

		return
	}

	ctx.ModuleBuild(pctx, android.ModuleBuildParams{
		Rule:            rule,
		Inputs:          inputs,
		Output:          g.genOutputs[0],
		ImplicitOutputs: g.genOutputs[1:],
		Args: map[string]string{
			"genDir":   g.genOutputDir.String(),
			"fqName":   g.properties.FqName,
			"language": g.properties.Language,
			"options":  strings.Join(extraOptions, " "),
		},
	})
}

func (g *hidlGenRule) GeneratedSourceFiles() android.Paths {
	return g.genOutputs.Paths()
}

func (g *hidlGenRule) Srcs() android.Paths {
	return g.genOutputs.Paths()
}

func (g *hidlGenRule) GeneratedDeps() android.Paths {
	return g.genOutputs.Paths()
}

func (g *hidlGenRule) GeneratedHeaderDirs() android.Paths {
	return android.Paths{g.genOutputDir}
}

func (g *hidlGenRule) DepsMutator(ctx android.BottomUpMutatorContext) {
	ctx.AddDependency(ctx.Module(), nil, g.properties.FqName+hidlInterfaceSuffix)
	ctx.AddDependency(ctx.Module(), nil, wrap("", g.properties.Interfaces, hidlInterfaceSuffix)...)
	ctx.AddDependency(ctx.Module(), nil, g.properties.Root)

	ctx.AddReverseDependency(ctx.Module(), nil, hidlMetadataSingletonName)
}

func hidlGenFactory() android.Module {
	g := &hidlGenRule{}
	g.AddProperties(&g.properties)
	android.InitAndroidModule(g)
	return g
}

type prebuiltHidlInterfaceProperties struct {
	// List of interfaces to consider valid, e.g. "vendor.foo.bar@1.0::IFoo" for typo checking
	// between init.rc, VINTF, and elsewhere. Note that inheritance properties will not be
	// checked for these (but would be checked in a branch where the actual hidl_interface
	// exists).
	Interfaces []string
}

type prebuiltHidlInterface struct {
	android.ModuleBase

	properties prebuiltHidlInterfaceProperties
}

func (p *prebuiltHidlInterface) GenerateAndroidBuildActions(ctx android.ModuleContext) {}

func (p *prebuiltHidlInterface) DepsMutator(ctx android.BottomUpMutatorContext) {
	ctx.AddReverseDependency(ctx.Module(), nil, hidlMetadataSingletonName)
}

func prebuiltHidlInterfaceFactory() android.Module {
	i := &prebuiltHidlInterface{}
	i.AddProperties(&i.properties)
	android.InitAndroidModule(i)
	return i
}

type hidlInterfaceProperties struct {
	// Vndk properties for interface library only.
	cc.VndkProperties

	// List of .hal files which compose this interface.
	Srcs []string

	// List of hal interface packages that this library depends on.
	Interfaces []string

	// Package root for this package, must be a prefix of name
	Root string

	// Unused/deprecated: List of non-TypeDef types declared in types.hal.
	Types []string

	// Whether to generate the Java library stubs.
	// Default: true
	Gen_java *bool

	// Whether to generate a Java library containing constants
	// expressed by @export annotations in the hal files.
	Gen_java_constants bool

	// Whether to generate VTS-related testing libraries.
	Gen_vts *bool

	// example: -randroid.hardware:hardware/interfaces
	Full_root_option string `blueprint:"mutated"`

	// List of APEX modules this interface can be used in.
	//
	// WARNING: HIDL is not fully supported in APEX since VINTF currently doesn't
	// read files from APEXes (b/130058564).
	//
	// "//apex_available:anyapex" is a pseudo APEX name that matches to any APEX.
	// "//apex_available:platform" refers to non-APEX partitions like "system.img"
	//
	// Note, this only applies to C++ libs, Java libs, and Java constant libs. It
	// does  not apply to VTS targets targets/fuzzers since these components
	// should not be shipped on device.
	Apex_available []string

	// Installs the vendor variant of the module to the /odm partition instead of
	// the /vendor partition.
	Odm_available *bool
}

type hidlInterface struct {
	android.ModuleBase

	properties hidlInterfaceProperties
}

func processSources(mctx android.LoadHookContext, srcs []string) ([]string, []string, bool) {
	var interfaces []string
	var types []string // hidl-gen only supports types.hal, but don't assume that here

	hasError := false

	for _, v := range srcs {
		if !strings.HasSuffix(v, ".hal") {
			mctx.PropertyErrorf("srcs", "Source must be a .hal file: "+v)
			hasError = true
			continue
		}

		name := strings.TrimSuffix(v, ".hal")

		if strings.HasPrefix(name, "I") {
			baseName := strings.TrimPrefix(name, "I")
			interfaces = append(interfaces, baseName)
		} else {
			types = append(types, name)
		}
	}

	return interfaces, types, !hasError
}

func processDependencies(mctx android.LoadHookContext, interfaces []string) ([]string, []string, bool) {
	var dependencies []string
	var javaDependencies []string

	hasError := false

	for _, v := range interfaces {
		name, err := parseFqName(v)
		if err != nil {
			mctx.PropertyErrorf("interfaces", err.Error())
			hasError = true
			continue
		}
		dependencies = append(dependencies, name.string())
		javaDependencies = append(javaDependencies, name.javaName())
	}

	return dependencies, javaDependencies, !hasError
}

func removeCoreDependencies(mctx android.LoadHookContext, dependencies []string) []string {
	var ret []string

	for _, i := range dependencies {
		if !isCorePackage(i) {
			ret = append(ret, i)
		}
	}

	return ret
}

func hidlInterfaceMutator(mctx android.LoadHookContext, i *hidlInterface) {
	if !canInterfaceExist(i.ModuleBase.Name()) {
		mctx.PropertyErrorf("name", "No more HIDL interfaces can be added to Android. Please use AIDL.")
		return
	}

	name, err := parseFqName(i.ModuleBase.Name())
	if err != nil {
		mctx.PropertyErrorf("name", err.Error())
	}

	if !name.inPackage(i.properties.Root) {
		mctx.PropertyErrorf("root", i.properties.Root+" must be a prefix of  "+name.string()+".")
	}
	if lookupPackageRoot(i.properties.Root) == nil {
		mctx.PropertyErrorf("interfaces", `Cannot find package root specification for package `+
			`root '%s' needed for module '%s'. Either this is a mispelling of the package `+
			`root, or a new hidl_package_root module needs to be added. For example, you can `+
			`fix this error by adding the following to <some path>/Android.bp:

hidl_package_root {
name: "%s",
// if you want to require <some path>/current.txt for interface versioning
use_current: true,
}

This corresponds to the "-r%s:<some path>" option that would be passed into hidl-gen.`,
			i.properties.Root, name, i.properties.Root, i.properties.Root)
	}

	interfaces, types, _ := processSources(mctx, i.properties.Srcs)

	if len(interfaces) == 0 && len(types) == 0 {
		mctx.PropertyErrorf("srcs", "No sources provided.")
	}

	dependencies, javaDependencies, _ := processDependencies(mctx, i.properties.Interfaces)
	cppDependencies := removeCoreDependencies(mctx, dependencies)

	if mctx.Failed() {
		return
	}

	shouldGenerateLibrary := !isCorePackage(name.string())
	// explicitly true if not specified to give early warning to devs
	shouldGenerateJava := proptools.BoolDefault(i.properties.Gen_java, true)
	shouldGenerateJavaConstants := i.properties.Gen_java_constants

	var productAvailable *bool
	if !mctx.ProductSpecific() {
		productAvailable = proptools.BoolPtr(true)
	}

	var vendorAvailable *bool
	if !proptools.Bool(i.properties.Odm_available) {
		vendorAvailable = proptools.BoolPtr(true)
	}

	// TODO(b/69002743): remove filegroups
	mctx.CreateModule(android.FileGroupFactory, &fileGroupProperties{
		Name: proptools.StringPtr(name.fileGroupName()),
		Srcs: i.properties.Srcs,
	})

	mctx.CreateModule(hidlGenFactory, &nameProperties{
		Name: proptools.StringPtr(name.sourcesName()),
	}, &hidlGenProperties{
		Language:   "c++-sources",
		FqName:     name.string(),
		Root:       i.properties.Root,
		Interfaces: i.properties.Interfaces,
		Inputs:     i.properties.Srcs,
		Outputs:    concat(wrap(name.dir(), interfaces, "All.cpp"), wrap(name.dir(), types, ".cpp")),
	})

	mctx.CreateModule(hidlGenFactory, &nameProperties{
		Name: proptools.StringPtr(name.headersName()),
	}, &hidlGenProperties{
		Language:   "c++-headers",
		FqName:     name.string(),
		Root:       i.properties.Root,
		Interfaces: i.properties.Interfaces,
		Inputs:     i.properties.Srcs,
		Outputs: concat(wrap(name.dir()+"I", interfaces, ".h"),
			wrap(name.dir()+"Bs", interfaces, ".h"),
			wrap(name.dir()+"BnHw", interfaces, ".h"),
			wrap(name.dir()+"BpHw", interfaces, ".h"),
			wrap(name.dir()+"IHw", interfaces, ".h"),
			wrap(name.dir(), types, ".h"),
			wrap(name.dir()+"hw", types, ".h")),
	})

	if shouldGenerateLibrary {
		mctx.CreateModule(cc.LibraryFactory, &ccProperties{
			Name:               proptools.StringPtr(name.string()),
			Host_supported:     proptools.BoolPtr(true),
			Recovery_available: proptools.BoolPtr(true),
			Vendor_available:   vendorAvailable,
			Odm_available:      i.properties.Odm_available,
			Product_available:  productAvailable,
			Double_loadable:    proptools.BoolPtr(isDoubleLoadable(name.string())),
			Defaults:           []string{"hidl-module-defaults"},
			Generated_sources:  []string{name.sourcesName()},
			Generated_headers:  []string{name.headersName()},
			Shared_libs: concat(cppDependencies, []string{
				"libhidlbase",
				"liblog",
				"libutils",
				"libcutils",
			}),
			Export_shared_lib_headers: concat(cppDependencies, []string{
				"libhidlbase",
				"libutils",
			}),
			Export_generated_headers: []string{name.headersName()},
			Apex_available:           i.properties.Apex_available,
			Min_sdk_version:          getMinSdkVersion(name.string()),
		}, &i.properties.VndkProperties)
	}

	if shouldGenerateJava {
		mctx.CreateModule(hidlGenFactory, &nameProperties{
			Name: proptools.StringPtr(name.javaSourcesName()),
		}, &hidlGenProperties{
			Language:   "java",
			FqName:     name.string(),
			Root:       i.properties.Root,
			Interfaces: i.properties.Interfaces,
			Inputs:     i.properties.Srcs,
			Outputs:    []string{"srcs.srcjar"},
		})

		commonJavaProperties := javaProperties{
			Defaults:    []string{"hidl-java-module-defaults"},
			Installable: proptools.BoolPtr(true),
			Srcs:        []string{":" + name.javaSourcesName()},

			// This should ideally be system_current, but android.hidl.base-V1.0-java is used
			// to build framework, which is used to build system_current.  Use core_current
			// plus hwbinder.stubs, which together form a subset of system_current that does
			// not depend on framework.
			Sdk_version:     proptools.StringPtr("core_current"),
			Libs:            []string{"hwbinder.stubs"},
			Apex_available:  i.properties.Apex_available,
			Min_sdk_version: getMinSdkVersion(name.string()),
		}

		mctx.CreateModule(java.LibraryFactory, &javaProperties{
			Name:        proptools.StringPtr(name.javaName()),
			Static_libs: javaDependencies,
		}, &commonJavaProperties)
		mctx.CreateModule(java.LibraryFactory, &javaProperties{
			Name: proptools.StringPtr(name.javaSharedName()),
			Libs: javaDependencies,
		}, &commonJavaProperties)
	}

	if shouldGenerateJavaConstants {
		mctx.CreateModule(hidlGenFactory, &nameProperties{
			Name: proptools.StringPtr(name.javaConstantsSourcesName()),
		}, &hidlGenProperties{
			Language:   "java-constants",
			FqName:     name.string(),
			Root:       i.properties.Root,
			Interfaces: i.properties.Interfaces,
			Inputs:     i.properties.Srcs,
			Outputs:    []string{name.sanitizedDir() + "Constants.java"},
		})
		mctx.CreateModule(java.LibraryFactory, &javaProperties{
			Name:            proptools.StringPtr(name.javaConstantsName()),
			Defaults:        []string{"hidl-java-module-defaults"},
			Sdk_version:     proptools.StringPtr("core_current"),
			Srcs:            []string{":" + name.javaConstantsSourcesName()},
			Apex_available:  i.properties.Apex_available,
			Min_sdk_version: getMinSdkVersion(name.string()),
		})
	}

	mctx.CreateModule(hidlGenFactory, &nameProperties{
		Name: proptools.StringPtr(name.lintName()),
	}, &hidlGenProperties{
		Language:   "lint",
		FqName:     name.string(),
		Root:       i.properties.Root,
		Interfaces: i.properties.Interfaces,
		Inputs:     i.properties.Srcs,
	})

	mctx.CreateModule(hidlGenFactory, &nameProperties{
		Name: proptools.StringPtr(name.inheritanceHierarchyName()),
	}, &hidlGenProperties{
		Language:   "inheritance-hierarchy",
		FqName:     name.string(),
		Root:       i.properties.Root,
		Interfaces: i.properties.Interfaces,
		Inputs:     i.properties.Srcs,
	})
}

func (h *hidlInterface) Name() string {
	return h.ModuleBase.Name() + hidlInterfaceSuffix
}
func (h *hidlInterface) GenerateAndroidBuildActions(ctx android.ModuleContext) {
	visited := false
	ctx.VisitDirectDeps(func(dep android.Module) {
		if r, ok := dep.(*hidlPackageRoot); ok {
			if visited {
				panic("internal error, multiple dependencies found but only one added")
			}
			visited = true
			h.properties.Full_root_option = r.getFullPackageRoot()
		}
	})
	if !visited {
		panic("internal error, no dependencies found but dependency added")
	}

}
func (h *hidlInterface) DepsMutator(ctx android.BottomUpMutatorContext) {
	ctx.AddDependency(ctx.Module(), nil, h.properties.Root)
}

func HidlInterfaceFactory() android.Module {
	i := &hidlInterface{}
	i.AddProperties(&i.properties)
	android.InitAndroidModule(i)
	android.AddLoadHook(i, func(ctx android.LoadHookContext) { hidlInterfaceMutator(ctx, i) })

	return i
}

var minSdkVersion = map[string]string{
	"android.hardware.audio.common@5.0":            "30",
	"android.hardware.audio.common@6.0":            "31",
	"android.hardware.automotive.audiocontrol@1.0": "31",
	"android.hardware.automotive.audiocontrol@2.0": "31",
	"android.hardware.automotive.vehicle@2.0":      "31",
	"android.hardware.bluetooth.a2dp@1.0":          "30",
	"android.hardware.bluetooth.audio@2.0":         "30",
	"android.hardware.bluetooth.audio@2.1":         "30",
	"android.hardware.bluetooth.audio@2.2":         "30",
	"android.hardware.bluetooth@1.0":               "30",
	"android.hardware.bluetooth@1.1":               "30",
	"android.hardware.health@1.0":                  "31",
	"android.hardware.health@2.0":                  "31",
	"android.hardware.neuralnetworks@1.0":          "30",
	"android.hardware.neuralnetworks@1.1":          "30",
	"android.hardware.neuralnetworks@1.2":          "30",
	"android.hardware.neuralnetworks@1.3":          "30",
	"android.hardware.wifi@1.0":                    "30",
	"android.hardware.wifi@1.1":                    "30",
	"android.hardware.wifi@1.2":                    "30",
	"android.hardware.wifi@1.3":                    "30",
	"android.hardware.wifi@1.4":                    "30",
	"android.hardware.wifi@1.5":                    "30",
	"android.hardware.wifi@1.6":                    "30",
	"android.hardware.wifi.hostapd@1.0":            "30",
	"android.hardware.wifi.hostapd@1.1":            "30",
	"android.hardware.wifi.hostapd@1.2":            "30",
	"android.hardware.wifi.hostapd@1.3":            "30",
	"android.hardware.wifi.supplicant@1.0":         "30",
	"android.hardware.wifi.supplicant@1.1":         "30",
	"android.hardware.wifi.supplicant@1.2":         "30",
	"android.hardware.wifi.supplicant@1.3":         "30",
	"android.hardware.wifi.supplicant@1.4":         "30",
	"android.hidl.manager@1.0":                     "30",
	"android.hidl.manager@1.1":                     "30",
	"android.hidl.manager@1.2":                     "30",
}

func getMinSdkVersion(name string) *string {
	if ver, ok := minSdkVersion[name]; ok {
		return proptools.StringPtr(ver)
	}
	// legacy, as used
	if name == "android.hardware.tetheroffload.config@1.0" ||
		name == "android.hardware.tetheroffload.control@1.0" ||
		name == "android.hardware.tetheroffload.control@1.1" ||
		name == "android.hardware.radio@1.0" ||
		name == "android.hidl.base@1.0" {

		return nil
	}
	return proptools.StringPtr("29")
}

var doubleLoadablePackageNames = []string{
	"android.frameworks.bufferhub@1.0",
	"android.hardware.cas@1.0",
	"android.hardware.cas.native@1.0",
	"android.hardware.configstore@",
	"android.hardware.drm@",
	"android.hardware.graphics.allocator@",
	"android.hardware.graphics.bufferqueue@",
	"android.hardware.media@",
	"android.hardware.media.bufferpool@",
	"android.hardware.media.c2@",
	"android.hardware.media.omx@",
	"android.hardware.memtrack@1.0",
	"android.hardware.neuralnetworks@",
	"android.hidl.allocator@",
	"android.hidl.token@",
	"android.system.suspend@1.0",
}

func isDoubleLoadable(name string) bool {
	for _, pkgname := range doubleLoadablePackageNames {
		if strings.HasPrefix(name, pkgname) {
			return true
		}
	}
	return false
}

// packages in libhidlbase
var coreDependencyPackageNames = []string{
	"android.hidl.base@",
	"android.hidl.manager@",
}

func isCorePackage(name string) bool {
	for _, pkgname := range coreDependencyPackageNames {
		if strings.HasPrefix(name, pkgname) {
			return true
		}
	}
	return false
}

var fuzzerPackageNameBlacklist = []string{
	"android.hardware.keymaster@", // to avoid deleteAllKeys()
	// Same-process HALs are always opened in the same process as their client.
	// So stability guarantees don't apply to them, e.g. it's OK to crash on
	// NULL input from client. Disable corresponding fuzzers as they create too
	// much noise.
	"android.hardware.graphics.mapper@",
	"android.hardware.renderscript@",
	"android.hidl.memory@",
}

func isFuzzerEnabled(name string) bool {
	// TODO(151338797): re-enable fuzzers
	return false
}

func canInterfaceExist(name string) bool {
	if strings.HasPrefix(name, "android.") {
		return allAospHidlInterfaces[name]
	}

	return true
}

var allAospHidlInterfaces = map[string]bool{
	"android.frameworks.automotive.display@1.0":    true,
	"android.frameworks.bufferhub@1.0":             true,
	"android.frameworks.cameraservice.common@2.0":  true,
	"android.frameworks.cameraservice.device@2.0":  true,
	"android.frameworks.cameraservice.device@2.1":  true,
	"android.frameworks.cameraservice.service@2.0": true,
	"android.frameworks.cameraservice.service@2.1": true,
	"android.frameworks.cameraservice.service@2.2": true,
	"android.frameworks.displayservice@1.0":        true,
	"android.frameworks.schedulerservice@1.0":      true,
	"android.frameworks.sensorservice@1.0":         true,
	"android.frameworks.stats@1.0":                 true,
	"android.frameworks.vr.composer@1.0":           true,
	"android.frameworks.vr.composer@2.0":           true,
	"android.hardware.atrace@1.0":                  true,
	"android.hardware.audio@2.0":                   true,
	"android.hardware.audio@4.0":                   true,
	"android.hardware.audio@5.0":                   true,
	"android.hardware.audio@6.0":                   true,
	"android.hardware.audio@7.0":                   true,
	"android.hardware.audio@7.1":                   true,
	"android.hardware.audio.common@2.0":            true,
	"android.hardware.audio.common@4.0":            true,
	"android.hardware.audio.common@5.0":            true,
	"android.hardware.audio.common@6.0":            true,
	"android.hardware.audio.common@7.0":            true,
	"android.hardware.audio.effect@2.0":            true,
	"android.hardware.audio.effect@4.0":            true,
	"android.hardware.audio.effect@5.0":            true,
	"android.hardware.audio.effect@6.0":            true,
	"android.hardware.audio.effect@7.0":            true,
	"android.hardware.authsecret@1.0":              true,
	"android.hardware.automotive.audiocontrol@1.0": true,
	"android.hardware.automotive.audiocontrol@2.0": true,
	"android.hardware.automotive.can@1.0":          true,
	"android.hardware.automotive.evs@1.0":          true,
	"android.hardware.automotive.evs@1.1":          true,
	"android.hardware.automotive.sv@1.0":           true,
	"android.hardware.automotive.vehicle@2.0":      true,
	"android.hardware.biometrics.face@1.0":         true,
	"android.hardware.biometrics.fingerprint@2.1":  true,
	"android.hardware.biometrics.fingerprint@2.2":  true,
	"android.hardware.biometrics.fingerprint@2.3":  true,
	"android.hardware.bluetooth@1.0":               true,
	"android.hardware.bluetooth@1.1":               true,
	"android.hardware.bluetooth.a2dp@1.0":          true,
	"android.hardware.bluetooth.audio@2.0":         true,
	"android.hardware.bluetooth.audio@2.1":         true,
	"android.hardware.bluetooth.audio@2.2":         true,
	"android.hardware.boot@1.0":                    true,
	"android.hardware.boot@1.1":                    true,
	"android.hardware.boot@1.2":                    true,
	"android.hardware.broadcastradio@1.0":          true,
	"android.hardware.broadcastradio@1.1":          true,
	"android.hardware.broadcastradio@2.0":          true,
	"android.hardware.camera.common@1.0":           true,
	"android.hardware.camera.device@1.0":           true,
	"android.hardware.camera.device@3.2":           true,
	"android.hardware.camera.device@3.3":           true,
	"android.hardware.camera.device@3.4":           true,
	"android.hardware.camera.device@3.5":           true,
	"android.hardware.camera.device@3.6":           true,
	"android.hardware.camera.device@3.7":           true,
	"android.hardware.camera.device@3.8":           true,
	"android.hardware.camera.metadata@3.2":         true,
	"android.hardware.camera.metadata@3.3":         true,
	"android.hardware.camera.metadata@3.4":         true,
	"android.hardware.camera.metadata@3.5":         true,
	"android.hardware.camera.metadata@3.6":         true,
	// TODO: Remove metadata@3.8 after AIDL migration b/196432585
	"android.hardware.camera.metadata@3.7":              true,
	"android.hardware.camera.metadata@3.8":              true,
	"android.hardware.camera.provider@2.4":              true,
	"android.hardware.camera.provider@2.5":              true,
	"android.hardware.camera.provider@2.6":              true,
	"android.hardware.camera.provider@2.7":              true,
	"android.hardware.cas@1.0":                          true,
	"android.hardware.cas@1.1":                          true,
	"android.hardware.cas@1.2":                          true,
	"android.hardware.cas.native@1.0":                   true,
	"android.hardware.configstore@1.0":                  true,
	"android.hardware.configstore@1.1":                  true,
	"android.hardware.confirmationui@1.0":               true,
	"android.hardware.contexthub@1.0":                   true,
	"android.hardware.contexthub@1.1":                   true,
	"android.hardware.contexthub@1.2":                   true,
	"android.hardware.drm@1.0":                          true,
	"android.hardware.drm@1.1":                          true,
	"android.hardware.drm@1.2":                          true,
	"android.hardware.drm@1.3":                          true,
	"android.hardware.drm@1.4":                          true,
	"android.hardware.dumpstate@1.0":                    true,
	"android.hardware.dumpstate@1.1":                    true,
	"android.hardware.fastboot@1.0":                     true,
	"android.hardware.fastboot@1.1":                     true,
	"android.hardware.gatekeeper@1.0":                   true,
	"android.hardware.gnss@1.0":                         true,
	"android.hardware.gnss@1.1":                         true,
	"android.hardware.gnss@2.0":                         true,
	"android.hardware.gnss@2.1":                         true,
	"android.hardware.gnss.measurement_corrections@1.0": true,
	"android.hardware.gnss.measurement_corrections@1.1": true,
	"android.hardware.gnss.visibility_control@1.0":      true,
	"android.hardware.graphics.allocator@2.0":           true,
	"android.hardware.graphics.allocator@3.0":           true,
	"android.hardware.graphics.allocator@4.0":           true,
	"android.hardware.graphics.bufferqueue@1.0":         true,
	"android.hardware.graphics.bufferqueue@2.0":         true,
	"android.hardware.graphics.common@1.0":              true,
	"android.hardware.graphics.common@1.1":              true,
	"android.hardware.graphics.common@1.2":              true,
	"android.hardware.graphics.composer@2.1":            true,
	"android.hardware.graphics.composer@2.2":            true,
	"android.hardware.graphics.composer@2.3":            true,
	"android.hardware.graphics.composer@2.4":            true,
	"android.hardware.graphics.mapper@2.0":              true,
	"android.hardware.graphics.mapper@2.1":              true,
	"android.hardware.graphics.mapper@3.0":              true,
	"android.hardware.graphics.mapper@4.0":              true,
	"android.hardware.health@1.0":                       true,
	"android.hardware.health@2.0":                       true,
	"android.hardware.health@2.1":                       true,
	"android.hardware.health.storage@1.0":               true,
	"android.hardware.input.classifier@1.0":             true,
	"android.hardware.input.common@1.0":                 true,
	"android.hardware.ir@1.0":                           true,
	"android.hardware.keymaster@3.0":                    true,
	"android.hardware.keymaster@4.0":                    true,
	"android.hardware.keymaster@4.1":                    true,
	"android.hardware.light@2.0":                        true,
	"android.hardware.media@1.0":                        true,
	"android.hardware.media.bufferpool@1.0":             true,
	"android.hardware.media.bufferpool@2.0":             true,
	"android.hardware.media.c2@1.0":                     true,
	"android.hardware.media.c2@1.1":                     true,
	"android.hardware.media.c2@1.2":                     true,
	"android.hardware.media.omx@1.0":                    true,
	"android.hardware.memtrack@1.0":                     true,
	"android.hardware.neuralnetworks@1.0":               true,
	"android.hardware.neuralnetworks@1.1":               true,
	"android.hardware.neuralnetworks@1.2":               true,
	"android.hardware.neuralnetworks@1.3":               true,
	"android.hardware.nfc@1.0":                          true,
	"android.hardware.nfc@1.1":                          true,
	"android.hardware.nfc@1.2":                          true,
	"android.hardware.oemlock@1.0":                      true,
	"android.hardware.power@1.0":                        true,
	"android.hardware.power@1.1":                        true,
	"android.hardware.power@1.2":                        true,
	"android.hardware.power@1.3":                        true,
	"android.hardware.power.stats@1.0":                  true,
	"android.hardware.radio@1.0":                        true,
	"android.hardware.radio@1.1":                        true,
	"android.hardware.radio@1.2":                        true,
	"android.hardware.radio@1.3":                        true,
	"android.hardware.radio@1.4":                        true,
	"android.hardware.radio@1.5":                        true,
	"android.hardware.radio@1.6":                        true,
	"android.hardware.radio.config@1.0":                 true,
	"android.hardware.radio.config@1.1":                 true,
	"android.hardware.radio.config@1.2":                 true,
	"android.hardware.radio.config@1.3":                 true,
	"android.hardware.radio.deprecated@1.0":             true,
	"android.hardware.renderscript@1.0":                 true,
	"android.hardware.secure_element@1.0":               true,
	"android.hardware.secure_element@1.1":               true,
	"android.hardware.secure_element@1.2":               true,
	"android.hardware.sensors@1.0":                      true,
	"android.hardware.sensors@2.0":                      true,
	"android.hardware.sensors@2.1":                      true,
	"android.hardware.soundtrigger@2.0":                 true,
	"android.hardware.soundtrigger@2.1":                 true,
	"android.hardware.soundtrigger@2.2":                 true,
	"android.hardware.soundtrigger@2.3":                 true,
	"android.hardware.soundtrigger@2.4":                 true,
	"android.hardware.tests.bar@1.0":                    true,
	"android.hardware.tests.baz@1.0":                    true,
	"android.hardware.tests.expression@1.0":             true,
	"android.hardware.tests.extension.light@2.0":        true,
	"android.hardware.tests.foo@1.0":                    true,
	"android.hardware.tests.hash@1.0":                   true,
	"android.hardware.tests.inheritance@1.0":            true,
	"android.hardware.tests.lazy@1.0":                   true,
	"android.hardware.tests.lazy@1.1":                   true,
	"android.hardware.tests.lazy_cb@1.0":                true,
	"android.hardware.tests.libhwbinder@1.0":            true,
	"android.hardware.tests.memory@1.0":                 true,
	"android.hardware.tests.memory@2.0":                 true,
	"android.hardware.tests.msgq@1.0":                   true,
	"android.hardware.tests.multithread@1.0":            true,
	"android.hardware.tests.safeunion@1.0":              true,
	"android.hardware.tests.safeunion.cpp@1.0":          true,
	"android.hardware.tests.trie@1.0":                   true,
	"android.hardware.tetheroffload.config@1.0":         true,
	"android.hardware.tetheroffload.control@1.0":        true,
	"android.hardware.tetheroffload.control@1.1":        true,
	"android.hardware.thermal@1.0":                      true,
	"android.hardware.thermal@1.1":                      true,
	"android.hardware.thermal@2.0":                      true,
	"android.hardware.tv.cec@1.0":                       true,
	"android.hardware.tv.cec@1.1":                       true,
	"android.hardware.tv.input@1.0":                     true,
	"android.hardware.tv.tuner@1.0":                     true,
	"android.hardware.tv.tuner@1.1":                     true,
	"android.hardware.usb@1.0":                          true,
	"android.hardware.usb@1.1":                          true,
	"android.hardware.usb@1.2":                          true,
	"android.hardware.usb@1.3":                          true,
	"android.hardware.usb.gadget@1.0":                   true,
	"android.hardware.usb.gadget@1.1":                   true,
	"android.hardware.usb.gadget@1.2":                   true,
	"android.hardware.vibrator@1.0":                     true,
	"android.hardware.vibrator@1.1":                     true,
	"android.hardware.vibrator@1.2":                     true,
	"android.hardware.vibrator@1.3":                     true,
	"android.hardware.vr@1.0":                           true,
	"android.hardware.weaver@1.0":                       true,
	"android.hardware.wifi@1.0":                         true,
	"android.hardware.wifi@1.1":                         true,
	"android.hardware.wifi@1.2":                         true,
	"android.hardware.wifi@1.3":                         true,
	"android.hardware.wifi@1.4":                         true,
	"android.hardware.wifi@1.5":                         true,
	"android.hardware.wifi@1.6":                         true,
	"android.hardware.wifi.hostapd@1.0":                 true,
	"android.hardware.wifi.hostapd@1.1":                 true,
	"android.hardware.wifi.hostapd@1.2":                 true,
	"android.hardware.wifi.hostapd@1.3":                 true,
	"android.hardware.wifi.offload@1.0":                 true,
	"android.hardware.wifi.supplicant@1.0":              true,
	"android.hardware.wifi.supplicant@1.1":              true,
	"android.hardware.wifi.supplicant@1.2":              true,
	"android.hardware.wifi.supplicant@1.3":              true,
	"android.hardware.wifi.supplicant@1.4":              true,
	"android.hidl.allocator@1.0":                        true,
	"android.hidl.base@1.0":                             true,
	"android.hidl.manager@1.0":                          true,
	"android.hidl.manager@1.1":                          true,
	"android.hidl.manager@1.2":                          true,
	"android.hidl.memory@1.0":                           true,
	"android.hidl.memory.block@1.0":                     true,
	"android.hidl.memory.token@1.0":                     true,
	"android.hidl.safe_union@1.0":                       true,
	"android.hidl.token@1.0":                            true,
	"android.system.net.netd@1.0":                       true,
	"android.system.net.netd@1.1":                       true,
	"android.system.suspend@1.0":                        true,
	"android.system.wifi.keystore@1.0":                  true,
}

// Copyright 2014 Google Inc. All rights reserved.
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

package bootstrap

import (
	"fmt"
	"path/filepath"
	"runtime"
	"strings"

	"github.com/google/blueprint"
	"github.com/google/blueprint/pathtools"
)

var (
	pctx = blueprint.NewPackageContext("github.com/google/blueprint/bootstrap")

	goTestMainCmd   = pctx.StaticVariable("goTestMainCmd", filepath.Join("$ToolDir", "gotestmain"))
	goTestRunnerCmd = pctx.StaticVariable("goTestRunnerCmd", filepath.Join("$ToolDir", "gotestrunner"))
	pluginGenSrcCmd = pctx.StaticVariable("pluginGenSrcCmd", filepath.Join("$ToolDir", "loadplugins"))

	parallelCompile = pctx.StaticVariable("parallelCompile", func() string {
		numCpu := runtime.NumCPU()
		// This will cause us to recompile all go programs if the
		// number of cpus changes. We don't get a lot of benefit from
		// higher values, so cap this to make it cheaper to move trees
		// between machines.
		if numCpu > 8 {
			numCpu = 8
		}
		return fmt.Sprintf("-c %d", numCpu)
	}())

	compile = pctx.StaticRule("compile",
		blueprint.RuleParams{
			Command: "GOROOT='$goRoot' $compileCmd $parallelCompile -o $out.tmp " +
				"$debugFlags -p $pkgPath -complete $incFlags -pack $in && " +
				"if cmp --quiet $out.tmp $out; then rm $out.tmp; else mv -f $out.tmp $out; fi",
			CommandDeps: []string{"$compileCmd"},
			Description: "compile $out",
			Restat:      true,
		},
		"pkgPath", "incFlags")

	link = pctx.StaticRule("link",
		blueprint.RuleParams{
			Command: "GOROOT='$goRoot' $linkCmd -o $out.tmp $libDirFlags $in && " +
				"if cmp --quiet $out.tmp $out; then rm $out.tmp; else mv -f $out.tmp $out; fi",
			CommandDeps: []string{"$linkCmd"},
			Description: "link $out",
			Restat:      true,
		},
		"libDirFlags")

	goTestMain = pctx.StaticRule("gotestmain",
		blueprint.RuleParams{
			Command:     "$goTestMainCmd -o $out -pkg $pkg $in",
			CommandDeps: []string{"$goTestMainCmd"},
			Description: "gotestmain $out",
		},
		"pkg")

	pluginGenSrc = pctx.StaticRule("pluginGenSrc",
		blueprint.RuleParams{
			Command:     "$pluginGenSrcCmd -o $out -p $pkg $plugins",
			CommandDeps: []string{"$pluginGenSrcCmd"},
			Description: "create $out",
		},
		"pkg", "plugins")

	test = pctx.StaticRule("test",
		blueprint.RuleParams{
			Command:     "$goTestRunnerCmd -p $pkgSrcDir -f $out -- $in -test.short",
			CommandDeps: []string{"$goTestRunnerCmd"},
			Description: "test $pkg",
		},
		"pkg", "pkgSrcDir")

	cp = pctx.StaticRule("cp",
		blueprint.RuleParams{
			Command:     "cp $in $out",
			Description: "cp $out",
		},
		"generator")

	bootstrap = pctx.StaticRule("bootstrap",
		blueprint.RuleParams{
			Command:     "BUILDDIR=$soongOutDir $bootstrapCmd -i $in",
			CommandDeps: []string{"$bootstrapCmd"},
			Description: "bootstrap $in",
			Generator:   true,
		})

	touch = pctx.StaticRule("touch",
		blueprint.RuleParams{
			Command:     "touch $out",
			Description: "touch $out",
		},
		"depfile", "generator")

	generateBuildNinja = pctx.StaticRule("build.ninja",
		blueprint.RuleParams{
			// TODO: it's kinda ugly that some parameters are computed from
			// environment variables and some from Ninja parameters, but it's probably
			// better to not to touch that while Blueprint and Soong are separate
			// NOTE: The spaces at EOL are important because otherwise Ninja would
			// omit all spaces between the different options.
			Command: `cd "$$(dirname "$builder")" && ` +
				`BUILDER="$$PWD/$$(basename "$builder")" && ` +
				`cd / && ` +
				`env -i $env "$$BUILDER" ` +
				`    --top "$$TOP" ` +
				`    --soong_out "$soongOutDir" ` +
				`    --out "$outDir" ` +
				`    $extra`,
			CommandDeps: []string{"$builder"},
			Description: "$builder $out",
			Deps:        blueprint.DepsGCC,
			Depfile:     "$out.d",
			Restat:      true,
		},
		"builder", "env", "extra", "pool")

	// Work around a Ninja issue.  See https://github.com/martine/ninja/pull/634
	phony = pctx.StaticRule("phony",
		blueprint.RuleParams{
			Command:     "# phony $out",
			Description: "phony $out",
			Generator:   true,
		},
		"depfile")

	_ = pctx.VariableFunc("ToolDir", func(ctx blueprint.VariableFuncContext, config interface{}) (string, error) {
		return config.(BootstrapConfig).HostToolDir(), nil
	})
)

type GoBinaryTool interface {
	InstallPath() string

	// So that other packages can't implement this interface
	isGoBinary()
}

func pluginDeps(ctx blueprint.BottomUpMutatorContext) {
	if pkg, ok := ctx.Module().(*GoPackage); ok {
		if ctx.PrimaryModule() == ctx.Module() {
			for _, plugin := range pkg.properties.PluginFor {
				ctx.AddReverseDependency(ctx.Module(), nil, plugin)
			}
		}
	}
}

type goPackageProducer interface {
	GoPkgRoot() string
	GoPackageTarget() string
	GoTestTargets() []string
}

func isGoPackageProducer(module blueprint.Module) bool {
	_, ok := module.(goPackageProducer)
	return ok
}

type goPluginProvider interface {
	GoPkgPath() string
	IsPluginFor(string) bool
}

func isGoPluginFor(name string) func(blueprint.Module) bool {
	return func(module blueprint.Module) bool {
		if plugin, ok := module.(goPluginProvider); ok {
			return plugin.IsPluginFor(name)
		}
		return false
	}
}

func IsBootstrapModule(module blueprint.Module) bool {
	_, isPackage := module.(*GoPackage)
	_, isBinary := module.(*GoBinary)
	return isPackage || isBinary
}

func isBootstrapBinaryModule(module blueprint.Module) bool {
	_, isBinary := module.(*GoBinary)
	return isBinary
}

// A GoPackage is a module for building Go packages.
type GoPackage struct {
	blueprint.SimpleName
	properties struct {
		Deps      []string
		PkgPath   string
		Srcs      []string
		TestSrcs  []string
		TestData  []string
		PluginFor []string

		Darwin struct {
			Srcs     []string
			TestSrcs []string
		}
		Linux struct {
			Srcs     []string
			TestSrcs []string
		}
	}

	// The root dir in which the package .a file is located.  The full .a file
	// path will be "packageRoot/PkgPath.a"
	pkgRoot string

	// The path of the .a file that is to be built.
	archiveFile string

	// The path of the test result file.
	testResultFile []string
}

var _ goPackageProducer = (*GoPackage)(nil)

func newGoPackageModuleFactory() func() (blueprint.Module, []interface{}) {
	return func() (blueprint.Module, []interface{}) {
		module := &GoPackage{}
		return module, []interface{}{&module.properties, &module.SimpleName.Properties}
	}
}

func (g *GoPackage) DynamicDependencies(ctx blueprint.DynamicDependerModuleContext) []string {
	if ctx.Module() != ctx.PrimaryModule() {
		return nil
	}
	return g.properties.Deps
}

func (g *GoPackage) GoPkgPath() string {
	return g.properties.PkgPath
}

func (g *GoPackage) GoPkgRoot() string {
	return g.pkgRoot
}

func (g *GoPackage) GoPackageTarget() string {
	return g.archiveFile
}

func (g *GoPackage) GoTestTargets() []string {
	return g.testResultFile
}

func (g *GoPackage) IsPluginFor(name string) bool {
	for _, plugin := range g.properties.PluginFor {
		if plugin == name {
			return true
		}
	}
	return false
}

func (g *GoPackage) GenerateBuildActions(ctx blueprint.ModuleContext) {
	// Allow the primary builder to create multiple variants.  Any variants after the first
	// will copy outputs from the first.
	if ctx.Module() != ctx.PrimaryModule() {
		primary := ctx.PrimaryModule().(*GoPackage)
		g.pkgRoot = primary.pkgRoot
		g.archiveFile = primary.archiveFile
		g.testResultFile = primary.testResultFile
		return
	}

	var (
		name       = ctx.ModuleName()
		hasPlugins = false
		pluginSrc  = ""
		genSrcs    = []string{}
	)

	if g.properties.PkgPath == "" {
		ctx.ModuleErrorf("module %s did not specify a valid pkgPath", name)
		return
	}

	g.pkgRoot = packageRoot(ctx)
	g.archiveFile = filepath.Join(g.pkgRoot,
		filepath.FromSlash(g.properties.PkgPath)+".a")

	ctx.VisitDepsDepthFirstIf(isGoPluginFor(name),
		func(module blueprint.Module) { hasPlugins = true })
	if hasPlugins {
		pluginSrc = filepath.Join(moduleGenSrcDir(ctx), "plugin.go")
		genSrcs = append(genSrcs, pluginSrc)
	}

	if hasPlugins && !buildGoPluginLoader(ctx, g.properties.PkgPath, pluginSrc) {
		return
	}

	var srcs, testSrcs []string
	if runtime.GOOS == "darwin" {
		srcs = append(g.properties.Srcs, g.properties.Darwin.Srcs...)
		testSrcs = append(g.properties.TestSrcs, g.properties.Darwin.TestSrcs...)
	} else if runtime.GOOS == "linux" {
		srcs = append(g.properties.Srcs, g.properties.Linux.Srcs...)
		testSrcs = append(g.properties.TestSrcs, g.properties.Linux.TestSrcs...)
	}

	testArchiveFile := filepath.Join(testRoot(ctx),
		filepath.FromSlash(g.properties.PkgPath)+".a")
	g.testResultFile = buildGoTest(ctx, testRoot(ctx), testArchiveFile,
		g.properties.PkgPath, srcs, genSrcs, testSrcs)

	// Don't build for test-only packages
	if len(srcs) == 0 && len(genSrcs) == 0 {
		ctx.Build(pctx, blueprint.BuildParams{
			Rule:     touch,
			Outputs:  []string{g.archiveFile},
			Optional: true,
		})
		return
	}

	buildGoPackage(ctx, g.pkgRoot, g.properties.PkgPath, g.archiveFile,
		srcs, genSrcs)
	blueprint.SetProvider(ctx, blueprint.SrcsFileProviderKey, blueprint.SrcsFileProviderData{SrcPaths: srcs})
}

func (g *GoPackage) Srcs() []string {
	return g.properties.Srcs
}

func (g *GoPackage) LinuxSrcs() []string {
	return g.properties.Linux.Srcs
}

func (g *GoPackage) DarwinSrcs() []string {
	return g.properties.Darwin.Srcs
}

func (g *GoPackage) TestSrcs() []string {
	return g.properties.TestSrcs
}

func (g *GoPackage) LinuxTestSrcs() []string {
	return g.properties.Linux.TestSrcs
}

func (g *GoPackage) DarwinTestSrcs() []string {
	return g.properties.Darwin.TestSrcs
}

func (g *GoPackage) Deps() []string {
	return g.properties.Deps
}

func (g *GoPackage) TestData() []string {
	return g.properties.TestData
}

// A GoBinary is a module for building executable binaries from Go sources.
type GoBinary struct {
	blueprint.SimpleName
	properties struct {
		Deps           []string
		Srcs           []string
		TestSrcs       []string
		TestData       []string
		PrimaryBuilder bool
		Default        bool

		Darwin struct {
			Srcs     []string
			TestSrcs []string
		}
		Linux struct {
			Srcs     []string
			TestSrcs []string
		}
	}

	installPath string
}

var _ GoBinaryTool = (*GoBinary)(nil)

func newGoBinaryModuleFactory() func() (blueprint.Module, []interface{}) {
	return func() (blueprint.Module, []interface{}) {
		module := &GoBinary{}
		return module, []interface{}{&module.properties, &module.SimpleName.Properties}
	}
}

func (g *GoBinary) DynamicDependencies(ctx blueprint.DynamicDependerModuleContext) []string {
	if ctx.Module() != ctx.PrimaryModule() {
		return nil
	}
	return g.properties.Deps
}

func (g *GoBinary) isGoBinary() {}
func (g *GoBinary) InstallPath() string {
	return g.installPath
}

func (g *GoBinary) Srcs() []string {
	return g.properties.Srcs
}

func (g *GoBinary) LinuxSrcs() []string {
	return g.properties.Linux.Srcs
}

func (g *GoBinary) DarwinSrcs() []string {
	return g.properties.Darwin.Srcs
}

func (g *GoBinary) TestSrcs() []string {
	return g.properties.TestSrcs
}

func (g *GoBinary) LinuxTestSrcs() []string {
	return g.properties.Linux.TestSrcs
}

func (g *GoBinary) DarwinTestSrcs() []string {
	return g.properties.Darwin.TestSrcs
}

func (g *GoBinary) Deps() []string {
	return g.properties.Deps
}

func (g *GoBinary) TestData() []string {
	return g.properties.TestData
}

func (g *GoBinary) GenerateBuildActions(ctx blueprint.ModuleContext) {
	// Allow the primary builder to create multiple variants.  Any variants after the first
	// will copy outputs from the first.
	if ctx.Module() != ctx.PrimaryModule() {
		primary := ctx.PrimaryModule().(*GoBinary)
		g.installPath = primary.installPath
		return
	}

	var (
		name            = ctx.ModuleName()
		objDir          = moduleObjDir(ctx)
		archiveFile     = filepath.Join(objDir, name+".a")
		testArchiveFile = filepath.Join(testRoot(ctx), name+".a")
		aoutFile        = filepath.Join(objDir, "a.out")
		hasPlugins      = false
		pluginSrc       = ""
		genSrcs         = []string{}
	)

	g.installPath = filepath.Join(ctx.Config().(BootstrapConfig).HostToolDir(), name)
	ctx.VisitDepsDepthFirstIf(isGoPluginFor(name),
		func(module blueprint.Module) { hasPlugins = true })
	if hasPlugins {
		pluginSrc = filepath.Join(moduleGenSrcDir(ctx), "plugin.go")
		genSrcs = append(genSrcs, pluginSrc)
	}

	var testDeps []string

	if hasPlugins && !buildGoPluginLoader(ctx, "main", pluginSrc) {
		return
	}

	var srcs, testSrcs []string
	if runtime.GOOS == "darwin" {
		srcs = append(g.properties.Srcs, g.properties.Darwin.Srcs...)
		testSrcs = append(g.properties.TestSrcs, g.properties.Darwin.TestSrcs...)
	} else if runtime.GOOS == "linux" {
		srcs = append(g.properties.Srcs, g.properties.Linux.Srcs...)
		testSrcs = append(g.properties.TestSrcs, g.properties.Linux.TestSrcs...)
	}

	testDeps = buildGoTest(ctx, testRoot(ctx), testArchiveFile,
		name, srcs, genSrcs, testSrcs)

	buildGoPackage(ctx, objDir, "main", archiveFile, srcs, genSrcs)

	var linkDeps []string
	var libDirFlags []string
	ctx.VisitDepsDepthFirstIf(isGoPackageProducer,
		func(module blueprint.Module) {
			dep := module.(goPackageProducer)
			linkDeps = append(linkDeps, dep.GoPackageTarget())
			libDir := dep.GoPkgRoot()
			libDirFlags = append(libDirFlags, "-L "+libDir)
			testDeps = append(testDeps, dep.GoTestTargets()...)
		})

	linkArgs := map[string]string{}
	if len(libDirFlags) > 0 {
		linkArgs["libDirFlags"] = strings.Join(libDirFlags, " ")
	}

	ctx.Build(pctx, blueprint.BuildParams{
		Rule:      link,
		Outputs:   []string{aoutFile},
		Inputs:    []string{archiveFile},
		Implicits: linkDeps,
		Args:      linkArgs,
		Optional:  true,
	})

	var validations []string
	if ctx.Config().(BootstrapConfig).RunGoTests() {
		validations = testDeps
	}

	ctx.Build(pctx, blueprint.BuildParams{
		Rule:        cp,
		Outputs:     []string{g.installPath},
		Inputs:      []string{aoutFile},
		Validations: validations,
		Optional:    !g.properties.Default,
	})
	blueprint.SetProvider(ctx, blueprint.SrcsFileProviderKey, blueprint.SrcsFileProviderData{SrcPaths: srcs})
}

func buildGoPluginLoader(ctx blueprint.ModuleContext, pkgPath, pluginSrc string) bool {
	ret := true
	name := ctx.ModuleName()

	var pluginPaths []string
	ctx.VisitDepsDepthFirstIf(isGoPluginFor(name),
		func(module blueprint.Module) {
			plugin := module.(goPluginProvider)
			pluginPaths = append(pluginPaths, plugin.GoPkgPath())
		})

	ctx.Build(pctx, blueprint.BuildParams{
		Rule:    pluginGenSrc,
		Outputs: []string{pluginSrc},
		Args: map[string]string{
			"pkg":     pkgPath,
			"plugins": strings.Join(pluginPaths, " "),
		},
		Optional: true,
	})

	return ret
}

func buildGoPackage(ctx blueprint.ModuleContext, pkgRoot string,
	pkgPath string, archiveFile string, srcs []string, genSrcs []string) {

	srcDir := moduleSrcDir(ctx)
	srcFiles := pathtools.PrefixPaths(srcs, srcDir)
	srcFiles = append(srcFiles, genSrcs...)

	var incFlags []string
	var deps []string
	ctx.VisitDepsDepthFirstIf(isGoPackageProducer,
		func(module blueprint.Module) {
			dep := module.(goPackageProducer)
			incDir := dep.GoPkgRoot()
			target := dep.GoPackageTarget()
			incFlags = append(incFlags, "-I "+incDir)
			deps = append(deps, target)
		})

	compileArgs := map[string]string{
		"pkgPath": pkgPath,
	}

	if len(incFlags) > 0 {
		compileArgs["incFlags"] = strings.Join(incFlags, " ")
	}

	ctx.Build(pctx, blueprint.BuildParams{
		Rule:      compile,
		Outputs:   []string{archiveFile},
		Inputs:    srcFiles,
		Implicits: deps,
		Args:      compileArgs,
		Optional:  true,
	})
}

func buildGoTest(ctx blueprint.ModuleContext, testRoot, testPkgArchive,
	pkgPath string, srcs, genSrcs, testSrcs []string) []string {

	if len(testSrcs) == 0 {
		return nil
	}

	srcDir := moduleSrcDir(ctx)
	testFiles := pathtools.PrefixPaths(testSrcs, srcDir)

	mainFile := filepath.Join(testRoot, "test.go")
	testArchive := filepath.Join(testRoot, "test.a")
	testFile := filepath.Join(testRoot, "test")
	testPassed := filepath.Join(testRoot, "test.passed")

	buildGoPackage(ctx, testRoot, pkgPath, testPkgArchive,
		append(srcs, testSrcs...), genSrcs)

	ctx.Build(pctx, blueprint.BuildParams{
		Rule:    goTestMain,
		Outputs: []string{mainFile},
		Inputs:  testFiles,
		Args: map[string]string{
			"pkg": pkgPath,
		},
		Optional: true,
	})

	linkDeps := []string{testPkgArchive}
	libDirFlags := []string{"-L " + testRoot}
	testDeps := []string{}
	ctx.VisitDepsDepthFirstIf(isGoPackageProducer,
		func(module blueprint.Module) {
			dep := module.(goPackageProducer)
			linkDeps = append(linkDeps, dep.GoPackageTarget())
			libDir := dep.GoPkgRoot()
			libDirFlags = append(libDirFlags, "-L "+libDir)
			testDeps = append(testDeps, dep.GoTestTargets()...)
		})

	ctx.Build(pctx, blueprint.BuildParams{
		Rule:      compile,
		Outputs:   []string{testArchive},
		Inputs:    []string{mainFile},
		Implicits: []string{testPkgArchive},
		Args: map[string]string{
			"pkgPath":  "main",
			"incFlags": "-I " + testRoot,
		},
		Optional: true,
	})

	ctx.Build(pctx, blueprint.BuildParams{
		Rule:      link,
		Outputs:   []string{testFile},
		Inputs:    []string{testArchive},
		Implicits: linkDeps,
		Args: map[string]string{
			"libDirFlags": strings.Join(libDirFlags, " "),
		},
		Optional: true,
	})

	ctx.Build(pctx, blueprint.BuildParams{
		Rule:        test,
		Outputs:     []string{testPassed},
		Inputs:      []string{testFile},
		Validations: testDeps,
		Args: map[string]string{
			"pkg":       pkgPath,
			"pkgSrcDir": filepath.Dir(testFiles[0]),
		},
		Optional: true,
	})

	return []string{testPassed}
}

type singleton struct {
}

func newSingletonFactory() func() blueprint.Singleton {
	return func() blueprint.Singleton {
		return &singleton{}
	}
}

func (s *singleton) GenerateBuildActions(ctx blueprint.SingletonContext) {
	// Find the module that's marked as the "primary builder", which means it's
	// creating the binary that we'll use to generate the non-bootstrap
	// build.ninja file.
	var primaryBuilders []*GoBinary
	// blueprintTools contains blueprint go binaries that will be built in StageMain
	var blueprintTools []string
	// blueprintTools contains the test outputs of go tests that can be run in StageMain
	var blueprintTests []string
	// blueprintGoPackages contains all blueprint go packages that can be built in StageMain
	var blueprintGoPackages []string
	ctx.VisitAllModulesIf(IsBootstrapModule,
		func(module blueprint.Module) {
			if ctx.PrimaryModule(module) == module {
				if binaryModule, ok := module.(*GoBinary); ok {
					blueprintTools = append(blueprintTools, binaryModule.InstallPath())
					if binaryModule.properties.PrimaryBuilder {
						primaryBuilders = append(primaryBuilders, binaryModule)
					}
				}

				if packageModule, ok := module.(*GoPackage); ok {
					blueprintGoPackages = append(blueprintGoPackages,
						packageModule.GoPackageTarget())
					blueprintTests = append(blueprintTests,
						packageModule.GoTestTargets()...)
				}
			}
		})

	var primaryBuilderCmdlinePrefix []string
	var primaryBuilderName string

	if len(primaryBuilders) == 0 {
		ctx.Errorf("no primary builder module present")
		return
	} else if len(primaryBuilders) > 1 {
		ctx.Errorf("multiple primary builder modules present:")
		for _, primaryBuilder := range primaryBuilders {
			ctx.ModuleErrorf(primaryBuilder, "<-- module %s",
				ctx.ModuleName(primaryBuilder))
		}
		return
	} else {
		primaryBuilderName = ctx.ModuleName(primaryBuilders[0])
	}

	primaryBuilderFile := filepath.Join("$ToolDir", primaryBuilderName)
	ctx.SetOutDir(pctx, "${outDir}")

	for _, subninja := range ctx.Config().(BootstrapConfig).Subninjas() {
		ctx.AddSubninja(subninja)
	}

	for _, i := range ctx.Config().(BootstrapConfig).PrimaryBuilderInvocations() {
		flags := make([]string, 0)
		flags = append(flags, primaryBuilderCmdlinePrefix...)
		flags = append(flags, i.Args...)

		pool := ""
		if i.Console {
			pool = "console"
		}

		envAssignments := ""
		for k, v := range i.Env {
			// NB: This is rife with quoting issues but we don't care because we trust
			// soong_ui to not abuse this facility too much
			envAssignments += k + "=" + v + " "
		}

		// Build the main build.ninja
		ctx.Build(pctx, blueprint.BuildParams{
			Rule:      generateBuildNinja,
			Outputs:   i.Outputs,
			Inputs:    i.Inputs,
			Implicits: i.Implicits,
			OrderOnly: i.OrderOnlyInputs,
			Args: map[string]string{
				"builder": primaryBuilderFile,
				"env":     envAssignments,
				"extra":   strings.Join(flags, " "),
				"pool":    pool,
			},
			// soong_ui explicitly requests what it wants to be build. This is
			// because the same Ninja file contains instructions to run
			// soong_build, run bp2build and to generate the JSON module graph.
			Optional:    true,
			Description: i.Description,
		})
	}

	// Add a phony target for building various tools that are part of blueprint
	ctx.Build(pctx, blueprint.BuildParams{
		Rule:    blueprint.Phony,
		Outputs: []string{"blueprint_tools"},
		Inputs:  blueprintTools,
	})

	// Add a phony target for running various tests that are part of blueprint
	ctx.Build(pctx, blueprint.BuildParams{
		Rule:    blueprint.Phony,
		Outputs: []string{"blueprint_tests"},
		Inputs:  blueprintTests,
	})

	// Add a phony target for running go tests
	ctx.Build(pctx, blueprint.BuildParams{
		Rule:     blueprint.Phony,
		Outputs:  []string{"blueprint_go_packages"},
		Inputs:   blueprintGoPackages,
		Optional: true,
	})
}

// packageRoot returns the module-specific package root directory path.  This
// directory is where the final package .a files are output and where dependant
// modules search for this package via -I arguments.
func packageRoot(ctx blueprint.ModuleContext) string {
	toolDir := ctx.Config().(BootstrapConfig).HostToolDir()
	return filepath.Join(toolDir, "go", ctx.ModuleName(), "pkg")
}

// testRoot returns the module-specific package root directory path used for
// building tests. The .a files generated here will include everything from
// packageRoot, plus the test-only code.
func testRoot(ctx blueprint.ModuleContext) string {
	toolDir := ctx.Config().(BootstrapConfig).HostToolDir()
	return filepath.Join(toolDir, "go", ctx.ModuleName(), "test")
}

// moduleSrcDir returns the path of the directory that all source file paths are
// specified relative to.
func moduleSrcDir(ctx blueprint.ModuleContext) string {
	return ctx.ModuleDir()
}

// moduleObjDir returns the module-specific object directory path.
func moduleObjDir(ctx blueprint.ModuleContext) string {
	toolDir := ctx.Config().(BootstrapConfig).HostToolDir()
	return filepath.Join(toolDir, "go", ctx.ModuleName(), "obj")
}

// moduleGenSrcDir returns the module-specific generated sources path.
func moduleGenSrcDir(ctx blueprint.ModuleContext) string {
	toolDir := ctx.Config().(BootstrapConfig).HostToolDir()
	return filepath.Join(toolDir, "go", ctx.ModuleName(), "gen")
}

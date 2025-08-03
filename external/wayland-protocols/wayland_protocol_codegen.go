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

/*
Package wayland_protocol defines an plugin module for the Soong build system,
which makes it easier to generate code from a list of Wayland protocol files.

The primary build module is "wayland_protocol_codegen", which takes a list of
protocol files, and runs a configurable code-generation tool to generate
source code for each one. There is also a "wayland_protocol_codegen_defaults"
for setting common properties.

This package is substantially similar to the base "android/soong/genrule"
package, which was originally used for inspiration for this one, and has
been recently restructured so that it can be kept in sync with a tool like
"vimdiff" to keep things in sync as needed.

Notable differences:

  - This package implements a more powerful template mechanism for specifying
    what output path/filename should be used for each source filename. The
    genrule package only allows the extension on each source filename to be
    replaced.

  - This package drops support for depfiles, after observing comments that
    they are problematic in the genrule package sources.

  - This package drops "Extra" and "CmdModifier" from the public Module
    structure, as this module is not expected to be extended.

  - This package drops "rule" from the public Module structure, as it was
    unused but present in genrule.

# Usage

	wayland_protocol_codegen {
		// A standard target name.
		name: "wayland_extension_protocol_sources",

		// A simple template for generating output filenames.
		output: "$(in).c"

		// The command line template. See "Cmd".
		cmd: "$(location wayland_scanner) code < $(in) > $(out)",

		// Protocol source files for the expansion.
		srcs: [":wayland_extension_protocols"],

		// Any buildable binaries to use as tools
		tools: ["wayland_scanner"],

		// Any source files to be used  (scripts, template files)
		tools_files: [],
	}
*/
package soong_wayland_protocol_codegen

import (
	"fmt"
	"strconv"
	"strings"

	"github.com/google/blueprint"
	"github.com/google/blueprint/bootstrap"
	"github.com/google/blueprint/proptools"

	"android/soong/android"
	"android/soong/genrule"
)

func init() {
	registerCodeGenBuildComponents(android.InitRegistrationContext)
}

func registerCodeGenBuildComponents(ctx android.RegistrationContext) {
	ctx.RegisterModuleType("wayland_protocol_codegen_defaults", defaultsFactory)

	ctx.RegisterModuleType("wayland_protocol_codegen", codegenFactory)

	ctx.FinalDepsMutators(func(ctx android.RegisterMutatorsContext) {
		ctx.BottomUp("wayland_protocol_codegen_tool_deps", toolDepsMutator).Parallel()
	})
}

var (
	pctx = android.NewPackageContext("android/soong/external/wayland_protocol_codegen")

	// Used by wayland_protocol_codegen when there is more than 1 shard to merge the outputs
	// of each shard into a zip file.
	gensrcsMerge = pctx.AndroidStaticRule("wayland_protocol_codegenMerge", blueprint.RuleParams{
		Command:        "${soongZip} -o ${tmpZip} @${tmpZip}.rsp && ${zipSync} -d ${genDir} ${tmpZip}",
		CommandDeps:    []string{"${soongZip}", "${zipSync}"},
		Rspfile:        "${tmpZip}.rsp",
		RspfileContent: "${zipArgs}",
	}, "tmpZip", "genDir", "zipArgs")
)

func init() {
	pctx.Import("android/soong/android")

	pctx.HostBinToolVariable("soongZip", "soong_zip")
	pctx.HostBinToolVariable("zipSync", "zipsync")
}

type hostToolDependencyTag struct {
	blueprint.BaseDependencyTag
	android.LicenseAnnotationToolchainDependencyTag
	label string
}

func (t hostToolDependencyTag) AllowDisabledModuleDependency(target android.Module) bool {
	// Allow depending on a disabled module if it's replaced by a prebuilt
	// counterpart. We get the prebuilt through android.PrebuiltGetPreferred in
	// GenerateAndroidBuildActions.
	return target.IsReplacedByPrebuilt()
}

var _ android.AllowDisabledModuleDependency = (*hostToolDependencyTag)(nil)

type generatorProperties struct {
	// The command to run on one or more input files. Cmd supports
	// substitution of a few variables (the actual substitution is implemented
	// in GenerateAndroidBuildActions below)
	//
	// Available variables for substitution:
	//
	//	- $(location)
	//		the path to the first entry in tools or tool_files
	//	- $(location <label>)
	//		the path to the tool, tool_file, input or output with name <label>. Use
	//		$(location) if <label> refers to a rule that outputs exactly one file.
	//	- $(locations <label>)
	//		the paths to the tools, tool_files, inputs or outputs with name
	//		<label>. Use $(locations) if <label> refers to a rule that outputs two
	//		or more files.
	//	- $(in)
	//		one or more input files
	//	- $(out)
	//		a single output file
	//	- $(genDir)
	//		the sandbox directory for this tool; contains $(out)
	//	- $$
	//		a literal '$'
	//
	// All files used must be declared as inputs (to ensure proper up-to-date
	// checks). Use "$(in)" directly in Cmd to ensure that all inputs used are
	// declared.
	Cmd *string

	// name of the modules (if any) that produces the host executable. Leave
	// empty for prebuilts or scripts that do not need a module to build them.
	Tools []string

	// Local source files that are used as scripts or other input files needed
	// by a tool.
	Tool_files []string `android:"path"`

	// List of directories to export generated headers from.
	Export_include_dirs []string

	// List of input files.
	Srcs []string `android:"path,arch_variant"`

	// Input files to exclude.
	Exclude_srcs []string `android:"path,arch_variant"`
}

type Module struct {
	android.ModuleBase
	android.DefaultableModuleBase
	android.ApexModuleBase

	android.ImageInterface

	properties generatorProperties

	taskGenerator taskFunc

	rawCommands []string

	exportedIncludeDirs android.Paths

	outputFiles android.Paths
	outputDeps  android.Paths

	subName string
	subDir  string

	// Collect the module directory for IDE info in java/jdeps.go.
	modulePaths []string
}

type taskFunc func(ctx android.ModuleContext, rawCommand string, srcFiles android.Paths) []generateTask

type generateTask struct {
	in     android.Paths
	out    android.WritablePaths
	copyTo android.WritablePaths
	genDir android.WritablePath
	cmd    string

	shard  int
	shards int
}

// Part of genrule.SourceFileGenerator.
// Returns the list of generated source files.
func (g *Module) GeneratedSourceFiles() android.Paths {
	return g.outputFiles
}

// Part of genrule.SourceFileGenerator.
// Returns the list of input source files.
func (g *Module) Srcs() android.Paths {
	return append(android.Paths{}, g.outputFiles...)
}

// Part of genrule.SourceFileGenerator.
// Returns the list of the list of exported include paths.
func (g *Module) GeneratedHeaderDirs() android.Paths {
	return g.exportedIncludeDirs
}

// Part of genrule.SourceFileGenerator.
// Returns the list of files to be used as dependencies when using
// GeneratedHeaderDirs
func (g *Module) GeneratedDeps() android.Paths {
	return g.outputDeps
}

// Part of android.OutputFileProducer.
// Returns the list of output files matching a tag, or an error if there is no
// match.
func (g *Module) OutputFiles(tag string) (android.Paths, error) {
	if tag == "" {
		return append(android.Paths{}, g.outputFiles...), nil
	}
	// otherwise, tag should match one of outputs
	for _, outputFile := range g.outputFiles {
		if outputFile.Rel() == tag {
			return android.Paths{outputFile}, nil
		}
	}
	return nil, fmt.Errorf("unsupported module reference tag %q", tag)
}

// Ensure Module implements the genrule.SourceFileGenerator interface.
var _ genrule.SourceFileGenerator = (*Module)(nil)

// Ensure Module implements the android.SourceFileProducer interface.
var _ android.SourceFileProducer = (*Module)(nil)

// Ensure Module implements the android.OutputFileProducer interface.
var _ android.OutputFileProducer = (*Module)(nil)

func toolDepsMutator(ctx android.BottomUpMutatorContext) {
	if g, ok := ctx.Module().(*Module); ok {
		for _, tool := range g.properties.Tools {
			tag := hostToolDependencyTag{label: tool}
			if m := android.SrcIsModule(tool); m != "" {
				tool = m
			}
			ctx.AddFarVariationDependencies(ctx.Config().BuildOSTarget.Variations(), tag, tool)
		}
	}
}

// Part of android.Module.
// Generates all the rules and builds commands used by this module instance.
func (g *Module) generateCommonBuildActions(ctx android.ModuleContext) {
	g.subName = ctx.ModuleSubDir()

	// Collect the module directory for IDE info in java/jdeps.go.
	g.modulePaths = append(g.modulePaths, ctx.ModuleDir())

	if len(g.properties.Export_include_dirs) > 0 {
		for _, dir := range g.properties.Export_include_dirs {
			g.exportedIncludeDirs = append(g.exportedIncludeDirs,
				android.PathForModuleGen(ctx, g.subDir, ctx.ModuleDir(), dir))
		}
	} else {
		g.exportedIncludeDirs = append(g.exportedIncludeDirs, android.PathForModuleGen(ctx, g.subDir))
	}

	locationLabels := map[string]location{}
	firstLabel := ""

	addLocationLabel := func(label string, loc location) {
		if firstLabel == "" {
			firstLabel = label
		}
		if _, exists := locationLabels[label]; !exists {
			locationLabels[label] = loc
		} else {
			ctx.ModuleErrorf("multiple locations for label %q: %q and %q (do you have duplicate srcs entries?)",
				label, locationLabels[label], loc)
		}
	}

	var tools android.Paths
	var packagedTools []android.PackagingSpec
	if len(g.properties.Tools) > 0 {
		seenTools := make(map[string]bool)

		ctx.VisitDirectDepsBlueprint(func(module blueprint.Module) {
			switch tag := ctx.OtherModuleDependencyTag(module).(type) {
			case hostToolDependencyTag:
				tool := ctx.OtherModuleName(module)
				if m, ok := module.(android.Module); ok {
					// Necessary to retrieve any prebuilt replacement for the tool, since
					// toolDepsMutator runs too late for the prebuilt mutators to have
					// replaced the dependency.
					module = android.PrebuiltGetPreferred(ctx, m)
				}

				switch t := module.(type) {
				case android.HostToolProvider:
					// A HostToolProvider provides the path to a tool, which will be copied
					// into the sandbox.
					if !t.(android.Module).Enabled() {
						if ctx.Config().AllowMissingDependencies() {
							ctx.AddMissingDependencies([]string{tool})
						} else {
							ctx.ModuleErrorf("depends on disabled module %q", tool)
						}
						return
					}
					path := t.HostToolPath()
					if !path.Valid() {
						ctx.ModuleErrorf("host tool %q missing output file", tool)
						return
					}
					if specs := t.TransitivePackagingSpecs(); specs != nil {
						// If the HostToolProvider has PackgingSpecs, which are definitions of the
						// required relative locations of the tool and its dependencies, use those
						// instead.  They will be copied to those relative locations in the sbox
						// sandbox.
						packagedTools = append(packagedTools, specs...)
						// Assume that the first PackagingSpec of the module is the tool.
						addLocationLabel(tag.label, packagedToolLocation{specs[0]})
					} else {
						tools = append(tools, path.Path())
						addLocationLabel(tag.label, toolLocation{android.Paths{path.Path()}})
					}
				case bootstrap.GoBinaryTool:
					// A GoBinaryTool provides the install path to a tool, which will be copied.
					p := android.PathForGoBinary(ctx, t)
					tools = append(tools, p)
					addLocationLabel(tag.label, toolLocation{android.Paths{p}})
				default:
					ctx.ModuleErrorf("%q is not a host tool provider", tool)
					return
				}

				seenTools[tag.label] = true
			}
		})

		// If AllowMissingDependencies is enabled, the build will not have stopped when
		// AddFarVariationDependencies was called on a missing tool, which will result in nonsensical
		// "cmd: unknown location label ..." errors later.  Add a placeholder file to the local label.
		// The command that uses this placeholder file will never be executed because the rule will be
		// replaced with an android.Error rule reporting the missing dependencies.
		if ctx.Config().AllowMissingDependencies() {
			for _, tool := range g.properties.Tools {
				if !seenTools[tool] {
					addLocationLabel(tool, errorLocation{"***missing tool " + tool + "***"})
				}
			}
		}
	}

	if ctx.Failed() {
		return
	}

	for _, toolFile := range g.properties.Tool_files {
		paths := android.PathsForModuleSrc(ctx, []string{toolFile})
		tools = append(tools, paths...)
		addLocationLabel(toolFile, toolLocation{paths})
	}

	includeDirInPaths := ctx.DeviceConfig().BuildBrokenInputDir(g.Name())
	var srcFiles android.Paths
	for _, in := range g.properties.Srcs {
		paths, missingDeps := android.PathsAndMissingDepsRelativeToModuleSourceDir(android.SourceInput{
			Context: ctx, Paths: []string{in}, ExcludePaths: g.properties.Exclude_srcs, IncludeDirs: includeDirInPaths,
		})
		if len(missingDeps) > 0 {
			if !ctx.Config().AllowMissingDependencies() {
				panic(fmt.Errorf("should never get here, the missing dependencies %q should have been reported in DepsMutator",
					missingDeps))
			}

			// If AllowMissingDependencies is enabled, the build will not have stopped when
			// the dependency was added on a missing SourceFileProducer module, which will result in nonsensical
			// "cmd: label ":..." has no files" errors later.  Add a placeholder file to the local label.
			// The command that uses this placeholder file will never be executed because the rule will be
			// replaced with an android.Error rule reporting the missing dependencies.
			ctx.AddMissingDependencies(missingDeps)
			addLocationLabel(in, errorLocation{"***missing srcs " + in + "***"})
		} else {
			srcFiles = append(srcFiles, paths...)
			addLocationLabel(in, inputLocation{paths})
		}
	}

	var copyFrom android.Paths
	var outputFiles android.WritablePaths
	var zipArgs strings.Builder

	cmd := proptools.String(g.properties.Cmd)

	tasks := g.taskGenerator(ctx, cmd, srcFiles)
	if ctx.Failed() {
		return
	}

	for _, task := range tasks {
		if len(task.out) == 0 {
			ctx.ModuleErrorf("must have at least one output file")
			return
		}

		// Pick a unique path outside the task.genDir for the sbox manifest textproto,
		// a unique rule name, and the user-visible description.
		manifestName := "wayland_protocol_codegen.sbox.textproto"
		desc := "generate"
		name := "generator"
		if task.shards > 0 {
			manifestName = "wayland_protocol_codegen_" + strconv.Itoa(task.shard) + ".sbox.textproto"
			desc += " " + strconv.Itoa(task.shard)
			name += strconv.Itoa(task.shard)
		} else if len(task.out) == 1 {
			desc += " " + task.out[0].Base()
		}

		manifestPath := android.PathForModuleOut(ctx, manifestName)

		// Use a RuleBuilder to create a rule that runs the command inside an sbox sandbox.
		rule := android.NewRuleBuilder(pctx, ctx).Sbox(task.genDir, manifestPath).SandboxTools()
		cmd := rule.Command()

		for _, out := range task.out {
			addLocationLabel(out.Rel(), outputLocation{out})
		}

		rawCommand, err := android.Expand(task.cmd, func(name string) (string, error) {
			// Report the error directly without returning an error to android.Expand to catch multiple errors in a
			// single run
			reportError := func(fmt string, args ...interface{}) (string, error) {
				ctx.PropertyErrorf("cmd", fmt, args...)
				return "SOONG_ERROR", nil
			}

			// Apply shell escape to each cases to prevent source file paths containing $ from being evaluated in shell
			switch name {
			case "location":
				if len(g.properties.Tools) == 0 && len(g.properties.Tool_files) == 0 {
					return reportError("at least one `tools` or `tool_files` is required if $(location) is used")
				}
				loc := locationLabels[firstLabel]
				paths := loc.Paths(cmd)
				if len(paths) == 0 {
					return reportError("default label %q has no files", firstLabel)
				} else if len(paths) > 1 {
					return reportError("default label %q has multiple files, use $(locations %s) to reference it",
						firstLabel, firstLabel)
				}
				return proptools.ShellEscape(paths[0]), nil
			case "in":
				return strings.Join(proptools.ShellEscapeList(cmd.PathsForInputs(srcFiles)), " "), nil
			case "out":
				var sandboxOuts []string
				for _, out := range task.out {
					sandboxOuts = append(sandboxOuts, cmd.PathForOutput(out))
				}
				return strings.Join(proptools.ShellEscapeList(sandboxOuts), " "), nil
			case "genDir":
				return proptools.ShellEscape(cmd.PathForOutput(task.genDir)), nil
			default:
				if strings.HasPrefix(name, "location ") {
					label := strings.TrimSpace(strings.TrimPrefix(name, "location "))
					if loc, ok := locationLabels[label]; ok {
						paths := loc.Paths(cmd)
						if len(paths) == 0 {
							return reportError("label %q has no files", label)
						} else if len(paths) > 1 {
							return reportError("label %q has multiple files, use $(locations %s) to reference it",
								label, label)
						}
						return proptools.ShellEscape(paths[0]), nil
					} else {
						return reportError("unknown location label %q is not in srcs, out, tools or tool_files.", label)
					}
				} else if strings.HasPrefix(name, "locations ") {
					label := strings.TrimSpace(strings.TrimPrefix(name, "locations "))
					if loc, ok := locationLabels[label]; ok {
						paths := loc.Paths(cmd)
						if len(paths) == 0 {
							return reportError("label %q has no files", label)
						}
						return proptools.ShellEscape(strings.Join(paths, " ")), nil
					} else {
						return reportError("unknown locations label %q is not in srcs, out, tools or tool_files.", label)
					}
				} else {
					return reportError("unknown variable '$(%s)'", name)
				}
			}
		})

		if err != nil {
			ctx.PropertyErrorf("cmd", "%s", err.Error())
			return
		}

		g.rawCommands = append(g.rawCommands, rawCommand)

		cmd.Text(rawCommand)
		cmd.ImplicitOutputs(task.out)
		cmd.Implicits(task.in)
		cmd.ImplicitTools(tools)
		cmd.ImplicitPackagedTools(packagedTools)

		// Create the rule to run the genrule command inside sbox.
		rule.Build(name, desc)

		if len(task.copyTo) > 0 {
			// If copyTo is set, multiple shards need to be copied into a single directory.
			// task.out contains the per-shard paths, and copyTo contains the corresponding
			// final path.  The files need to be copied into the final directory by a
			// single rule so it can remove the directory before it starts to ensure no
			// old files remain.  zipsync already does this, so build up zipArgs that
			// zip all the per-shard directories into a single zip.
			outputFiles = append(outputFiles, task.copyTo...)
			copyFrom = append(copyFrom, task.out.Paths()...)
			zipArgs.WriteString(" -C " + task.genDir.String())
			zipArgs.WriteString(android.JoinWithPrefix(task.out.Strings(), " -f "))
		} else {
			outputFiles = append(outputFiles, task.out...)
		}
	}

	if len(copyFrom) > 0 {
		// Create a rule that zips all the per-shard directories into a single zip and then
		// uses zipsync to unzip it into the final directory.
		ctx.Build(pctx, android.BuildParams{
			Rule:        gensrcsMerge,
			Implicits:   copyFrom,
			Outputs:     outputFiles,
			Description: "merge shards",
			Args: map[string]string{
				"zipArgs": zipArgs.String(),
				"tmpZip":  android.PathForModuleGen(ctx, g.subDir+".zip").String(),
				"genDir":  android.PathForModuleGen(ctx, g.subDir).String(),
			},
		})
	}

	g.outputFiles = outputFiles.Paths()
}

func (g *Module) GenerateAndroidBuildActions(ctx android.ModuleContext) {
	g.generateCommonBuildActions(ctx)

	// When there are less than six outputs, we directly give those as the
	// output dependency for this module. However, if there are more outputs,
	// we inject a phony target. This potentially saves space in the generated
	// ninja file, as well as simplifying any visualizations of the dependency
	// graph.
	if len(g.outputFiles) <= 6 {
		g.outputDeps = g.outputFiles
	} else {
		phonyFile := android.PathForModuleGen(ctx, "genrule-phony")
		ctx.Build(pctx, android.BuildParams{
			Rule:   blueprint.Phony,
			Output: phonyFile,
			Inputs: g.outputFiles,
		})
		g.outputDeps = android.Paths{phonyFile}
	}
}

// Part of android.IDEInfo.
// Collect information for opening IDE project files in java/jdeps.go.
func (g *Module) IDEInfo(dpInfo *android.IdeInfo) {
	dpInfo.Srcs = append(dpInfo.Srcs, g.Srcs().Strings()...)
	for _, src := range g.properties.Srcs {
		if strings.HasPrefix(src, ":") {
			src = strings.Trim(src, ":")
			dpInfo.Deps = append(dpInfo.Deps, src)
		}
	}
	dpInfo.Paths = append(dpInfo.Paths, g.modulePaths...)
}

// Ensure Module implements android.ApexModule
// Note: gensrcs implements it but it's possible we do not actually need to.
var _ android.ApexModule = (*Module)(nil)

// Part of android.ApexModule.
func (g *Module) ShouldSupportSdkVersion(ctx android.BaseModuleContext,
	sdkVersion android.ApiLevel) error {
	// Because generated outputs are checked by client modules(e.g. cc_library, ...)
	// we can safely ignore the check here.
	return nil
}

func generatorFactory(taskGenerator taskFunc, props ...interface{}) *Module {
	module := &Module{
		taskGenerator: taskGenerator,
	}

	module.AddProperties(props...)
	module.AddProperties(&module.properties)

	module.ImageInterface = noopImageInterface{}

	return module
}

type noopImageInterface struct{}

func (x noopImageInterface) ImageMutatorBegin(android.BaseModuleContext)                 {}
func (x noopImageInterface) CoreVariantNeeded(android.BaseModuleContext) bool            { return false }
func (x noopImageInterface) RamdiskVariantNeeded(android.BaseModuleContext) bool         { return false }
func (x noopImageInterface) VendorRamdiskVariantNeeded(android.BaseModuleContext) bool   { return false }
func (x noopImageInterface) DebugRamdiskVariantNeeded(android.BaseModuleContext) bool    { return false }
func (x noopImageInterface) RecoveryVariantNeeded(android.BaseModuleContext) bool        { return false }
func (x noopImageInterface) ExtraImageVariations(ctx android.BaseModuleContext) []string { return nil }
func (x noopImageInterface) SetImageVariation(ctx android.BaseModuleContext, variation string, module android.Module) {
}

// Constructs a Module for handling the code generation.
func newCodegen() *Module {
	properties := &codegenProperties{}

	// finalSubDir is the name of the subdirectory that output files will be generated into.
	// It is used so that per-shard directories can be placed alongside it an then finally
	// merged into it.
	const finalSubDir = "wayland_protocol_codegen"

	// Code generation commands are sharded so that up to this many files
	// are generated as part of one sandbox process.
	const defaultShardSize = 100

	taskGenerator := func(ctx android.ModuleContext, rawCommand string, srcFiles android.Paths) []generateTask {
		shardSize := defaultShardSize

		if len(srcFiles) == 0 {
			ctx.ModuleErrorf("must have at least one source file")
			return []generateTask{}
		}

		// wayland_protocol_codegen rules can easily hit command line limits by
		// repeating the command for every input file.  Shard the input files into
		// groups.
		shards := android.ShardPaths(srcFiles, shardSize)
		var generateTasks []generateTask

		distinctOutputs := make(map[string]android.Path)

		for i, shard := range shards {
			var commands []string
			var outFiles android.WritablePaths
			var copyTo android.WritablePaths

			// When sharding is enabled (i.e. len(shards) > 1), the sbox rules for each
			// shard will be write to their own directories and then be merged together
			// into finalSubDir.  If sharding is not enabled (i.e. len(shards) == 1),
			// the sbox rule will write directly to finalSubDir.
			genSubDir := finalSubDir
			if len(shards) > 1 {
				genSubDir = strconv.Itoa(i)
			}

			genDir := android.PathForModuleGen(ctx, genSubDir)
			// NOTE: This TODO is copied from gensrcs, as applies here too.
			// TODO(ccross): this RuleBuilder is a hack to be able to call
			// rule.Command().PathForOutput.  Replace this with passing the rule into the
			// generator.
			rule := android.NewRuleBuilder(pctx, ctx).Sbox(genDir, nil).SandboxTools()

			for _, in := range shard {
				outFileRaw := expandOutputPath(ctx, *properties, in)

				if conflictWith, hasKey := distinctOutputs[outFileRaw]; hasKey {
					ctx.ModuleErrorf("generation conflict: both '%v' and '%v' generate '%v'",
						conflictWith.String(), in.String(), outFileRaw)
				}

				distinctOutputs[outFileRaw] = in

				outFile := android.PathForModuleGen(ctx, finalSubDir, outFileRaw)

				// If sharding is enabled, then outFile is the path to the output file in
				// the shard directory, and copyTo is the path to the output file in the
				// final directory.
				if len(shards) > 1 {
					shardFile := android.PathForModuleGen(ctx, genSubDir, outFileRaw)
					copyTo = append(copyTo, outFile)
					outFile = shardFile
				}

				outFiles = append(outFiles, outFile)

				// pre-expand the command line to replace $in and $out with references to
				// a single input and output file.
				command, err := android.Expand(rawCommand, func(name string) (string, error) {
					switch name {
					case "in":
						return in.String(), nil
					case "out":
						return rule.Command().PathForOutput(outFile), nil
					default:
						return "$(" + name + ")", nil
					}
				})
				if err != nil {
					ctx.PropertyErrorf("cmd", err.Error())
				}

				// escape the command in case for example it contains '#', an odd number of '"', etc
				command = fmt.Sprintf("bash -c %v", proptools.ShellEscape(command))
				commands = append(commands, command)
			}
			fullCommand := strings.Join(commands, " && ")

			generateTasks = append(generateTasks, generateTask{
				in:     shard,
				out:    outFiles,
				copyTo: copyTo,
				genDir: genDir,
				cmd:    fullCommand,
				shard:  i,
				shards: len(shards),
			})
		}

		return generateTasks
	}

	g := generatorFactory(taskGenerator, properties)
	g.subDir = finalSubDir
	return g
}

// Factory for code generation modules
func codegenFactory() android.Module {
	m := newCodegen()
	android.InitAndroidModule(m)
	android.InitDefaultableModule(m)
	return m
}

// The custom properties specific to this code generation module.
type codegenProperties struct {
	// The string to prepend to every protocol filename to generate the
	// corresponding output filename. The empty string by default.
	// Deprecated. Prefer "Output" instead.
	Prefix *string

	// The suffix to append to every protocol filename to generate the
	// corresponding output filename. The empty string by default.
	// Deprecated. Prefer "Output" instead.
	Suffix *string

	// The output filename template.
	//
	// This template string allows the output file name to be generated for
	// each source file, using some limited properties of the source file.
	//
	//	$(in:base): The base filename, no path or extension
	//	$(in:base.ext): The filename, no path
	//	$(in:path/base): The filename with path but no extension
	//	$(in:path/base.ext): The full source filename
	//	$(in): An alias for $(in:base) for the base filename, no extension
	//
	// Note that the path that is maintained is the relative path used when
	// including the source in an Android.bp file.
	//
	// The template allows arbitrary prefixes and suffixes to be added to the
	// output filename. For example, "a_$(in).d" would take an source filename
	// of "b.c" and turn it into "a_b.d".
	//
	// The output template does not have to generate a unique filename,
	// however the implementation will raise an error if the same output file
	// is generated by more than one source file.
	Output *string
}

// Expands the output path pattern to form the output path for the given
// input path.
func expandOutputPath(ctx android.ModuleContext, properties codegenProperties, in android.Path) string {
	template := proptools.String(properties.Output)
	if len(template) == 0 {
		prefix := proptools.String(properties.Prefix)
		suffix := proptools.String(properties.Suffix)
		return prefix + removeExtension(in.Base()) + suffix
	}

	outPath, _ := android.Expand(template, func(name string) (string, error) {
		// Report the error directly without returning an error to
		// android.Expand to catch multiple errors in a single run.
		reportError := func(fmt string, args ...interface{}) (string, error) {
			ctx.PropertyErrorf("output", fmt, args...)
			return "EXPANSION_ERROR", nil
		}

		switch name {
		case "in":
			return removeExtension(in.Base()), nil
		case "in:base":
			return removeExtension(in.Base()), nil
		case "in:base.ext":
			return in.Base(), nil
		case "in:path/base":
			return removeExtension(in.Rel()), nil
		case "in:path/base.ext":
			return in.Rel(), nil
		default:
			return reportError("unknown variable '$(%s)'", name)
		}
	})

	return outPath
}

// Removes any extension from the final component of a path.
func removeExtension(path string) string {
	// Note: This implementation does not handle files like ".bashrc" correctly.
	if dot := strings.LastIndex(path, "."); dot != -1 {
		return path[:dot]
	}
	return path
}

// Defaults module.
type Defaults struct {
	android.ModuleBase
	android.DefaultsModuleBase
}

func defaultsFactory() android.Module {
	return DefaultsFactory()
}

func DefaultsFactory(props ...interface{}) android.Module {
	module := &Defaults{}

	module.AddProperties(props...)
	module.AddProperties(
		&generatorProperties{},
		&codegenProperties{},
	)

	android.InitDefaultsModule(module)

	return module
}

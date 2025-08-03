// Copyright (C) 2021 The Android Open Source Project
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

package aidl

import (
	"android/soong/android"
	"android/soong/genrule"
	"strconv"

	"path/filepath"
	"strings"

	"github.com/google/blueprint"
	"github.com/google/blueprint/pathtools"
	"github.com/google/blueprint/proptools"
)

var (
	aidlDirPrepareRule = pctx.StaticRule("aidlDirPrepareRule", blueprint.RuleParams{
		Command:     `mkdir -p "${outDir}" && touch ${out} # ${in}`,
		Description: "create ${out}",
	}, "outDir")

	aidlCppRule = pctx.StaticRule("aidlCppRule", blueprint.RuleParams{
		Command: `mkdir -p "${headerDir}" && ` +
			`mkdir -p "${outDir}/staging" && ` +
			`mkdir -p "${headerDir}/staging" && ` +
			`${aidlCmd} --lang=${lang} ${optionalFlags} --ninja -d ${outStagingFile}.d ` +
			`-h ${headerDir}/staging -o ${outDir}/staging ${imports} ${nextImports} ${in} && ` +
			`rsync --checksum ${outStagingFile}.d ${out}.d && ` +
			`rsync --checksum ${outStagingFile} ${out} && ` +
			`( [ -z "${stagingHeaders}" ] || rsync --checksum ${stagingHeaders} ${fullHeaderDir} ) && ` +
			`sed -i 's/\/gen\/staging\//\/gen\//g' ${out}.d && ` +
			`rm ${outStagingFile} ${outStagingFile}.d ${stagingHeaders}`,
		Depfile:     "${out}.d",
		Deps:        blueprint.DepsGCC,
		CommandDeps: []string{"${aidlCmd}"},
		Restat:      true,
		Description: "AIDL ${lang} ${in}",
	}, "imports", "nextImports", "lang", "headerDir", "outDir", "optionalFlags", "stagingHeaders", "outStagingFile",
		"fullHeaderDir")

	aidlJavaRule = pctx.StaticRule("aidlJavaRule", blueprint.RuleParams{
		Command: `${aidlCmd} --lang=java ${optionalFlags} --ninja -d ${out}.d ` +
			`-o ${outDir} ${imports} ${nextImports} ${in}`,
		Depfile:     "${out}.d",
		Deps:        blueprint.DepsGCC,
		CommandDeps: []string{"${aidlCmd}"},
		Restat:      true,
		Description: "AIDL Java ${in}",
	}, "imports", "nextImports", "outDir", "optionalFlags")

	aidlRustRule = pctx.StaticRule("aidlRustRule", blueprint.RuleParams{
		Command: `${aidlCmd} --lang=rust ${optionalFlags} --ninja -d ${out}.d ` +
			`-o ${outDir} ${imports} ${nextImports} ${in}`,
		Depfile:     "${out}.d",
		Deps:        blueprint.DepsGCC,
		CommandDeps: []string{"${aidlCmd}"},
		Restat:      true,
		Description: "AIDL Rust ${in}",
	}, "imports", "nextImports", "outDir", "optionalFlags")
)

type aidlGenProperties struct {
	Srcs                []string `android:"path"`
	AidlRoot            string   // base directory for the input aidl file
	Imports             []string
	Headers             []string
	Stability           *string
	Min_sdk_version     *string
	Platform_apis       bool
	Lang                string // target language [java|cpp|ndk|rust]
	BaseName            string
	GenLog              bool
	Version             string
	GenRpc              bool
	GenTrace            bool
	Unstable            *bool
	NotFrozen           bool
	RequireFrozenReason string
	Visibility          []string
	Flags               []string
	UseUnfrozen         bool
}

type aidlGenRule struct {
	android.ModuleBase

	properties aidlGenProperties

	deps            deps
	implicitInputs  android.Paths
	importFlags     string
	nextImportFlags string

	// A frozen aidl_interface always have a hash file
	hashFile android.Path

	genOutDir     android.ModuleGenPath
	genHeaderDir  android.ModuleGenPath
	genHeaderDeps android.Paths
	genOutputs    android.WritablePaths
}

var _ android.SourceFileProducer = (*aidlGenRule)(nil)
var _ genrule.SourceFileGenerator = (*aidlGenRule)(nil)

func (g *aidlGenRule) aidlInterface(ctx android.BaseModuleContext) *aidlInterface {
	return ctx.GetDirectDepWithTag(g.properties.BaseName, interfaceDep).(*aidlInterface)
}

func (g *aidlGenRule) getImports(ctx android.ModuleContext) map[string]string {
	iface := g.aidlInterface(ctx)
	return iface.getImports(g.properties.Version)
}

func (g *aidlGenRule) GenerateAndroidBuildActions(ctx android.ModuleContext) {
	srcs, nextImports := getPaths(ctx, g.properties.Srcs, g.properties.AidlRoot)

	g.deps = getDeps(ctx, g.getImports(ctx))

	if ctx.Failed() {
		return
	}

	genDirTimestamp := android.PathForModuleGen(ctx, "timestamp") // $out/gen/timestamp
	g.implicitInputs = append(g.implicitInputs, genDirTimestamp)
	g.implicitInputs = append(g.implicitInputs, g.deps.implicits...)
	g.implicitInputs = append(g.implicitInputs, g.deps.preprocessed...)

	g.nextImportFlags = strings.Join(wrap("-N", nextImports, ""), " ")
	g.importFlags = strings.Join(wrap("-I", g.deps.imports, ""), " ")

	g.genOutDir = android.PathForModuleGen(ctx)
	g.genHeaderDir = android.PathForModuleGen(ctx, "include")
	for _, src := range srcs {
		outFile, headers := g.generateBuildActionsForSingleAidl(ctx, src)
		g.genOutputs = append(g.genOutputs, outFile)
		g.genHeaderDeps = append(g.genHeaderDeps, headers...)
	}

	// This is to clean genOutDir before generating any file
	ctx.Build(pctx, android.BuildParams{
		Rule:   aidlDirPrepareRule,
		Inputs: srcs,
		Output: genDirTimestamp,
		Args: map[string]string{
			"outDir": g.genOutDir.String(),
		},
	})

	// This is to trigger genrule alone
	ctx.Build(pctx, android.BuildParams{
		Rule:   android.Phony,
		Output: android.PathForModuleOut(ctx, "timestamp"), // $out/timestamp
		Inputs: g.genOutputs.Paths(),
	})
}

func (g *aidlGenRule) generateBuildActionsForSingleAidl(ctx android.ModuleContext, src android.Path) (android.WritablePath, android.Paths) {
	relPath := src.Rel()
	baseDir := strings.TrimSuffix(strings.TrimSuffix(src.String(), relPath), "/")

	var ext string
	if g.properties.Lang == langJava {
		ext = "java"
	} else if g.properties.Lang == langRust {
		ext = "rs"
	} else {
		ext = "cpp"
	}
	outFile := android.PathForModuleGen(ctx, pathtools.ReplaceExtension(relPath, ext))
	outStagingFile := android.PathForModuleGen(ctx, pathtools.ReplaceExtension("staging/"+relPath, ext))
	implicits := g.implicitInputs

	// default version is 1 for any stable interface
	version := "1"
	previousVersion := ""
	previousApiDir := ""
	if g.properties.Version != "" {
		version = g.properties.Version
	}
	versionInt, err := strconv.Atoi(version)
	if err != nil && g.properties.Version != "" {
		ctx.PropertyErrorf(g.properties.Version, "Invalid Version string: %s", g.properties.Version)
	} else if err == nil && versionInt > 1 {
		previousVersion = strconv.Itoa(versionInt - 1)
		previousApiDir = filepath.Join(ctx.ModuleDir(), aidlApiDir, g.properties.BaseName, previousVersion)
	}

	optionalFlags := append([]string{}, g.properties.Flags...)
	if proptools.Bool(g.properties.Unstable) != true {
		optionalFlags = append(optionalFlags, "--structured")
		optionalFlags = append(optionalFlags, "--version "+version)
		hash := "notfrozen"
		if !strings.HasPrefix(baseDir, ctx.Config().SoongOutDir()) {
			hashFile := android.ExistentPathForSource(ctx, baseDir, ".hash")
			if hashFile.Valid() {
				hash = "$$(tail -1 '" + hashFile.Path().String() + "')"
				implicits = append(implicits, hashFile.Path())

				g.hashFile = hashFile.Path()
			}
		}
		optionalFlags = append(optionalFlags, "--hash "+hash)
	}
	if g.properties.GenRpc {
		optionalFlags = append(optionalFlags, "--rpc")
	}
	if g.properties.GenTrace {
		optionalFlags = append(optionalFlags, "-t")
	}
	if g.properties.Stability != nil {
		optionalFlags = append(optionalFlags, "--stability", *g.properties.Stability)
	}
	if g.properties.Platform_apis {
		optionalFlags = append(optionalFlags, "--min_sdk_version platform_apis")
	} else {
		minSdkVer := proptools.StringDefault(g.properties.Min_sdk_version, "current")
		optionalFlags = append(optionalFlags, "--min_sdk_version "+minSdkVer)
	}
	optionalFlags = append(optionalFlags, wrap("-p", g.deps.preprocessed.Strings(), "")...)

	// If this is an unfrozen version of a previously frozen interface, we want (1) the location
	// of the previously frozen source and (2) the previously frozen hash so the generated
	// library can behave like both versions at run time.
	if !g.properties.UseUnfrozen && previousVersion != "" &&
		!proptools.Bool(g.properties.Unstable) && g.hashFile == nil {
		apiDirPath := android.ExistentPathForSource(ctx, previousApiDir)
		if apiDirPath.Valid() {
			optionalFlags = append(optionalFlags, "--previous_api_dir="+apiDirPath.Path().String())
		} else {
			ctx.PropertyErrorf("--previous_api_dir is invalid: %s", apiDirPath.Path().String())
		}
		hashFile := android.ExistentPathForSource(ctx, previousApiDir, ".hash")
		if hashFile.Valid() {
			previousHash := "$$(tail -1 '" + hashFile.Path().String() + "')"
			implicits = append(implicits, hashFile.Path())
			optionalFlags = append(optionalFlags, "--previous_hash "+previousHash)
		} else {
			ctx.ModuleErrorf("Failed to find previous version's hash file in %s", previousApiDir)
		}
	}

	var headers android.WritablePaths
	if g.properties.Lang == langJava {
		ctx.Build(pctx, android.BuildParams{
			Rule:      aidlJavaRule,
			Input:     src,
			Implicits: implicits,
			Output:    outFile,
			Args: map[string]string{
				"imports":       g.importFlags,
				"nextImports":   g.nextImportFlags,
				"outDir":        g.genOutDir.String(),
				"optionalFlags": strings.Join(optionalFlags, " "),
			},
		})
	} else if g.properties.Lang == langRust {
		ctx.Build(pctx, android.BuildParams{
			Rule:      aidlRustRule,
			Input:     src,
			Implicits: implicits,
			Output:    outFile,
			Args: map[string]string{
				"imports":       g.importFlags,
				"nextImports":   g.nextImportFlags,
				"outDir":        g.genOutDir.String(),
				"optionalFlags": strings.Join(optionalFlags, " "),
			},
		})
	} else {
		typeName := strings.TrimSuffix(filepath.Base(relPath), ".aidl")
		packagePath := filepath.Dir(relPath)
		baseName := typeName
		// TODO(b/111362593): aidl_to_cpp_common.cpp uses heuristics to figure out if
		//   an interface name has a leading I. Those same heuristics have been
		//   moved here.
		if len(baseName) >= 2 && baseName[0] == 'I' &&
			strings.ToUpper(baseName)[1] == baseName[1] {
			baseName = strings.TrimPrefix(typeName, "I")
		}

		prefix := ""
		if g.properties.Lang == langNdk || g.properties.Lang == langNdkPlatform {
			prefix = "aidl"
		}

		var stagingHeaders []string
		var fullHeaderDir = g.genHeaderDir.Join(ctx, prefix, packagePath)
		if g.properties.Lang != langCppAnalyzer {
			headers = append(headers, g.genHeaderDir.Join(ctx, prefix, packagePath, typeName+".h"))
			stagingHeaders = append(stagingHeaders, g.genHeaderDir.Join(ctx, "staging/"+prefix, packagePath, typeName+".h").String())
			headers = append(headers, g.genHeaderDir.Join(ctx, prefix, packagePath, "Bp"+baseName+".h"))
			stagingHeaders = append(stagingHeaders, g.genHeaderDir.Join(ctx, "staging/"+prefix, packagePath, "Bp"+baseName+".h").String())
			headers = append(headers, g.genHeaderDir.Join(ctx, prefix, packagePath, "Bn"+baseName+".h"))
			stagingHeaders = append(stagingHeaders, g.genHeaderDir.Join(ctx, "staging/"+prefix, packagePath, "Bn"+baseName+".h").String())
		}

		if g.properties.GenLog {
			optionalFlags = append(optionalFlags, "--log")
		}

		aidlLang := g.properties.Lang
		if aidlLang == langNdkPlatform {
			aidlLang = "ndk"
		}

		ctx.Build(pctx, android.BuildParams{
			Rule:            aidlCppRule,
			Input:           src,
			Implicits:       implicits,
			Output:          outFile,
			ImplicitOutputs: headers,
			Args: map[string]string{
				"imports":        g.importFlags,
				"nextImports":    g.nextImportFlags,
				"lang":           aidlLang,
				"headerDir":      g.genHeaderDir.String(),
				"fullHeaderDir":  fullHeaderDir.String(),
				"outDir":         g.genOutDir.String(),
				"outStagingFile": outStagingFile.String(),
				"optionalFlags":  strings.Join(optionalFlags, " "),
				"stagingHeaders": strings.Join(stagingHeaders, " "),
			},
		})
	}

	return outFile, headers.Paths()
}

func (g *aidlGenRule) GeneratedSourceFiles() android.Paths {
	return g.genOutputs.Paths()
}

func (g *aidlGenRule) Srcs() android.Paths {
	return g.genOutputs.Paths()
}

func (g *aidlGenRule) GeneratedDeps() android.Paths {
	return g.genHeaderDeps
}

func (g *aidlGenRule) GeneratedHeaderDirs() android.Paths {
	return android.Paths{g.genHeaderDir}
}

func (g *aidlGenRule) DepsMutator(ctx android.BottomUpMutatorContext) {
	ctx.AddReverseDependency(ctx.Module(), nil, aidlMetadataSingletonName)
}
func aidlGenFactory() android.Module {
	g := &aidlGenRule{}
	g.AddProperties(&g.properties)
	android.InitAndroidModule(g)
	return g
}

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
	"android/soong/rust"

	"strings"

	"github.com/google/blueprint"
	"github.com/google/blueprint/proptools"
)

var (
	aidlRustGlueRule = pctx.StaticRule("aidlRustGlueRule", blueprint.RuleParams{
		Command:     `${aidlRustGlueCmd} ${out} ${root} ${in} ${imports}`,
		CommandDeps: []string{"${aidlRustGlueCmd}"},
	}, "root", "imports")
)

type aidlRustSourceProviderProperties struct {
	SourceGen         string `android:"path"`
	Imports           []string
	Version           string
	AidlInterfaceName string
}

type aidlRustSourceProvider struct {
	*rust.BaseSourceProvider

	properties aidlRustSourceProviderProperties
}

var aidlRustSourceTag = struct {
	blueprint.DependencyTag
}{}

func (sp *aidlRustSourceProvider) GenerateSource(ctx rust.ModuleContext, _ rust.PathDeps) android.Path {
	sourceStem := proptools.String(sp.BaseSourceProvider.Properties.Source_stem)
	topLevelOutputFile := android.PathForModuleOut(ctx, sourceStem+".rs")

	aidlGenModule := ctx.GetDirectDepWithTag(sp.properties.SourceGen, aidlRustSourceTag)
	// Find the gen directory for the source module
	srcGenDir := aidlGenModule.(*aidlGenRule).genOutDir
	srcPaths := aidlGenModule.(*aidlGenRule).genOutputs.Paths()

	// In Rust, we import our dependency crates into `mangled`:
	//   use dependency::mangled::*;
	// so we can use the crate:: Rust path prefix to refer to
	// both crate-local and imported paths (from dependencies)
	importFlags := make([]string, len(sp.properties.Imports))
	for i, dep := range trimVersionSuffixInList(sp.properties.Imports) {
		importFlags[i] = "-I" + fixRustName(dep)
	}

	// In Rust, we need to do some extra post-processing:
	// emit a top-level foo.rs that includes all the generated .rs
	// files verbatim ("glued" together). The generated glue file
	// replicates the AIDL package hierarchy from the input
	// .aidl files in two ways:
	//   * 1:1 mapping in the crate::aidl namespace, used by downstream users
	//   * mangled in the crate::mangled namespace, used internally
	//     to resolve AIDL package paths between dependencies
	ctx.Build(pctx, android.BuildParams{
		Rule:   aidlRustGlueRule,
		Inputs: srcPaths,
		Output: topLevelOutputFile,
		Args: map[string]string{
			"root":    srcGenDir.String(),
			"imports": strings.Join(importFlags, " "),
		},
	})

	sp.BaseSourceProvider.OutputFiles = android.Paths{topLevelOutputFile}
	return topLevelOutputFile
}

func (sp *aidlRustSourceProvider) SourceProviderProps() []interface{} {
	return append(sp.BaseSourceProvider.SourceProviderProps(),
		&sp.properties)
}

func (sp *aidlRustSourceProvider) SourceProviderDeps(ctx rust.DepsContext, deps rust.Deps) rust.Deps {
	deps = sp.BaseSourceProvider.SourceProviderDeps(ctx, deps)
	deps.Rustlibs = append(deps.Rustlibs, "libbinder_rs")
	deps.Rustlibs = append(deps.Rustlibs, wrap("", getImportsWithVersion(ctx, sp.properties.AidlInterfaceName, sp.properties.Version), "-"+langRust)...)
	deps.ProcMacros = append(deps.ProcMacros, "libasync_trait")
	// Add a depencency to the source module (*-rust-source) directly via `ctx` because
	// the source module is specific to aidlRustSourceProvider and we don't want the rust module
	// to know about it.
	ctx.AddDependency(ctx.Module(), aidlRustSourceTag, sp.properties.SourceGen)

	return deps
}

func (sp *aidlRustSourceProvider) AndroidMk(ctx rust.AndroidMkContext, ret *android.AndroidMkEntries) {
	ctx.SubAndroidMk(ret, sp.BaseSourceProvider)
	ret.ExtraEntries = append(ret.ExtraEntries,
		func(ctx android.AndroidMkExtraEntriesContext, entries *android.AndroidMkEntries) {
			entries.SetBool("LOCAL_UNINSTALLABLE_MODULE", true)
		})
}

func aidlRustLibraryFactory() android.Module {
	sourceProvider := &aidlRustSourceProvider{
		BaseSourceProvider: rust.NewSourceProvider(),
		properties:         aidlRustSourceProviderProperties{},
	}

	module := rust.NewSourceProviderModule(android.HostAndDeviceSupported, sourceProvider, false, false)
	return module.Init()
}

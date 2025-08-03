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
	"bufio"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"runtime"
	"runtime/debug"
	"runtime/pprof"
	"runtime/trace"
	"strings"

	"github.com/google/blueprint"
)

type Args struct {
	ModuleListFile string
	OutFile        string

	EmptyNinjaFile bool

	NoGC       bool
	Cpuprofile string
	Memprofile string
	TraceFile  string

	// Debug data json file
	ModuleDebugFile string
}

// RegisterGoModuleTypes adds module types to build tools written in golang
func RegisterGoModuleTypes(ctx *blueprint.Context) {
	ctx.RegisterModuleType("bootstrap_go_package", newGoPackageModuleFactory())
	ctx.RegisterModuleType("blueprint_go_binary", newGoBinaryModuleFactory())
}

// RunBlueprint emits `args.OutFile` (a Ninja file) and returns the list of
// its dependencies. These can be written to a `${args.OutFile}.d` file
// so that it is correctly rebuilt when needed in case Blueprint is itself
// invoked from Ninja
func RunBlueprint(args Args, stopBefore StopBefore, ctx *blueprint.Context, config interface{}) ([]string, error) {
	runtime.GOMAXPROCS(runtime.NumCPU())

	if args.NoGC {
		debug.SetGCPercent(-1)
	}

	if args.Cpuprofile != "" {
		f, err := os.Create(joinPath(ctx.SrcDir(), args.Cpuprofile))
		if err != nil {
			return nil, fmt.Errorf("error opening cpuprofile: %s", err)
		}
		pprof.StartCPUProfile(f)
		defer f.Close()
		defer pprof.StopCPUProfile()
	}

	if args.TraceFile != "" {
		f, err := os.Create(joinPath(ctx.SrcDir(), args.TraceFile))
		if err != nil {
			return nil, fmt.Errorf("error opening trace: %s", err)
		}
		trace.Start(f)
		defer f.Close()
		defer trace.Stop()
	}

	if args.ModuleListFile == "" {
		return nil, fmt.Errorf("-l <moduleListFile> is required and must be nonempty")
	}
	ctx.SetModuleListFile(args.ModuleListFile)

	var ninjaDeps []string
	ninjaDeps = append(ninjaDeps, args.ModuleListFile)

	ctx.BeginEvent("list_modules")
	var filesToParse []string
	if f, err := ctx.ListModulePaths("."); err != nil {
		return nil, fmt.Errorf("could not enumerate files: %v\n", err.Error())
	} else {
		filesToParse = f
	}
	ctx.EndEvent("list_modules")

	ctx.RegisterBottomUpMutator("bootstrap_plugin_deps", pluginDeps)
	ctx.RegisterSingletonType("bootstrap", newSingletonFactory(), false)
	RegisterGoModuleTypes(ctx)
	blueprint.RegisterPackageIncludesModuleType(ctx)

	ctx.BeginEvent("parse_bp")
	if blueprintFiles, errs := ctx.ParseFileList(".", filesToParse, config); len(errs) > 0 {
		return nil, fatalErrors(errs)
	} else {
		ctx.EndEvent("parse_bp")
		ninjaDeps = append(ninjaDeps, blueprintFiles...)
	}

	if resolvedDeps, errs := ctx.ResolveDependencies(config); len(errs) > 0 {
		return nil, fatalErrors(errs)
	} else {
		ninjaDeps = append(ninjaDeps, resolvedDeps...)
	}

	if stopBefore == StopBeforePrepareBuildActions {
		return ninjaDeps, nil
	}

	if ctx.BeforePrepareBuildActionsHook != nil {
		if err := ctx.BeforePrepareBuildActionsHook(); err != nil {
			return nil, fatalErrors([]error{err})
		}
	}

	if buildActionsDeps, errs := ctx.PrepareBuildActions(config); len(errs) > 0 {
		return nil, fatalErrors(errs)
	} else {
		ninjaDeps = append(ninjaDeps, buildActionsDeps...)
	}

	if args.ModuleDebugFile != "" {
		ctx.GenerateModuleDebugInfo(args.ModuleDebugFile)
	}

	if stopBefore == StopBeforeWriteNinja {
		return ninjaDeps, nil
	}

	providersValidationChan := make(chan []error, 1)
	if ctx.GetVerifyProvidersAreUnchanged() {
		go func() {
			providersValidationChan <- ctx.VerifyProvidersWereUnchanged()
		}()
	} else {
		providersValidationChan <- nil
	}

	const outFilePermissions = 0666
	var out blueprint.StringWriterWriter
	var f *os.File
	var buf *bufio.Writer

	ctx.BeginEvent("write_files")
	defer ctx.EndEvent("write_files")
	if args.EmptyNinjaFile {
		if err := os.WriteFile(joinPath(ctx.SrcDir(), args.OutFile), []byte(nil), outFilePermissions); err != nil {
			return nil, fmt.Errorf("error writing empty Ninja file: %s", err)
		}
		out = io.Discard.(blueprint.StringWriterWriter)
	} else {
		f, err := os.OpenFile(joinPath(ctx.SrcDir(), args.OutFile), os.O_WRONLY|os.O_CREATE|os.O_TRUNC, outFilePermissions)
		if err != nil {
			return nil, fmt.Errorf("error opening Ninja file: %s", err)
		}
		defer f.Close()
		buf = bufio.NewWriterSize(f, 16*1024*1024)
		out = buf
	}

	if err := ctx.WriteBuildFile(out); err != nil {
		return nil, fmt.Errorf("error writing Ninja file contents: %s", err)
	}

	if buf != nil {
		if err := buf.Flush(); err != nil {
			return nil, fmt.Errorf("error flushing Ninja file contents: %s", err)
		}
	}

	if f != nil {
		if err := f.Close(); err != nil {
			return nil, fmt.Errorf("error closing Ninja file: %s", err)
		}
	}

	providerValidationErrors := <-providersValidationChan
	if providerValidationErrors != nil {
		var sb strings.Builder
		for i, err := range providerValidationErrors {
			if i != 0 {
				sb.WriteString("\n")
			}
			sb.WriteString(err.Error())
		}
		return nil, errors.New(sb.String())
	}

	if args.Memprofile != "" {
		f, err := os.Create(joinPath(ctx.SrcDir(), args.Memprofile))
		if err != nil {
			return nil, fmt.Errorf("error opening memprofile: %s", err)
		}
		defer f.Close()
		pprof.WriteHeapProfile(f)
	}

	return ninjaDeps, nil
}

func fatalErrors(errs []error) error {
	red := "\x1b[31m"
	unred := "\x1b[0m"

	for _, err := range errs {
		switch err := err.(type) {
		case *blueprint.BlueprintError,
			*blueprint.ModuleError,
			*blueprint.PropertyError:
			fmt.Printf("%serror:%s %s\n", red, unred, err.Error())
		default:
			fmt.Printf("%sinternal error:%s %s\n", red, unred, err)
		}
	}

	return errors.New("fatal errors encountered")
}

func joinPath(base, path string) string {
	if filepath.IsAbs(path) {
		return path
	}
	return filepath.Join(base, path)
}

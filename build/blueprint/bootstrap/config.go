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
	"os"
	"path/filepath"
	"runtime"
	"strings"

	"github.com/google/blueprint"
)

func bootstrapVariable(name string, value func(BootstrapConfig) string) blueprint.Variable {
	return pctx.VariableFunc(name, func(ctx blueprint.VariableFuncContext, config interface{}) (string, error) {
		c, ok := config.(BootstrapConfig)
		if !ok {
			panic(fmt.Sprintf("Bootstrap rules were passed a configuration that does not include theirs, config=%q",
				config))
		}
		return value(c), nil
	})
}

var (
	// These variables are the only configuration needed by the bootstrap
	// modules.
	srcDirVariable = bootstrapVariable("srcDir", func(c BootstrapConfig) string {
		return "."
	})
	soongOutDirVariable = bootstrapVariable("soongOutDir", func(c BootstrapConfig) string {
		return c.SoongOutDir()
	})
	outDirVariable = bootstrapVariable("outDir", func(c BootstrapConfig) string {
		return c.OutDir()
	})
	goRootVariable = bootstrapVariable("goRoot", func(c BootstrapConfig) string {
		goroot := runtime.GOROOT()
		// Prefer to omit absolute paths from the ninja file
		if cwd, err := os.Getwd(); err == nil {
			if relpath, err := filepath.Rel(cwd, goroot); err == nil {
				if !strings.HasPrefix(relpath, "../") {
					goroot = relpath
				}
			}
		}
		return goroot
	})
	compileCmdVariable = bootstrapVariable("compileCmd", func(c BootstrapConfig) string {
		return "$goRoot/pkg/tool/" + runtime.GOOS + "_" + runtime.GOARCH + "/compile"
	})
	linkCmdVariable = bootstrapVariable("linkCmd", func(c BootstrapConfig) string {
		return "$goRoot/pkg/tool/" + runtime.GOOS + "_" + runtime.GOARCH + "/link"
	})
	debugFlagsVariable = bootstrapVariable("debugFlags", func(c BootstrapConfig) string {
		if c.DebugCompilation() {
			// -N: disable optimizations, -l: disable inlining
			return "-N -l"
		} else {
			return ""
		}
	})
)

type BootstrapConfig interface {
	// The directory where tools run during the build are located.
	HostToolDir() string

	// The directory where files emitted during bootstrapping are located.
	// Usually OutDir() + "/soong".
	SoongOutDir() string

	// The output directory for the build.
	OutDir() string

	// Whether to compile Go code in such a way that it can be debugged
	DebugCompilation() bool

	// Whether to run tests for Go code
	RunGoTests() bool

	Subninjas() []string
	PrimaryBuilderInvocations() []PrimaryBuilderInvocation
}

type StopBefore int

const (
	DoEverything StopBefore = iota
	StopBeforePrepareBuildActions
	StopBeforeWriteNinja
)

type PrimaryBuilderInvocation struct {
	Inputs          []string
	Implicits       []string
	OrderOnlyInputs []string
	Outputs         []string
	Args            []string
	Console         bool
	Description     string
	Env             map[string]string
}

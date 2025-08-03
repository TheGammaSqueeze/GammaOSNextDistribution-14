// Copyright 2022 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package report

import (
	"context"
	"fmt"
	"sync"

	"tools/treble/build/report/app"
)

// Channel data structures, include explicit error field to reply to each input
type buildTargetData struct {
	input      *app.BuildInput
	buildSteps int
	error      bool
}
type buildSourceData struct {
	source string
	query  *app.BuildQuery
	error  bool
}
type buildPathData struct {
	filename string
	path     *app.BuildPath
	error    bool
}

//
// create build target from  using repo data
//
func createBuildTarget(ctx context.Context, rtx *Context, buildTarget *buildTargetData) *app.BuildTarget {
	out := &app.BuildTarget{Name: buildTarget.input.Target,
		Steps:     buildTarget.buildSteps,
		Projects:  make(map[string]*app.GitProject),
		FileCount: len(buildTarget.input.Files),
	}

	for _, f := range buildTarget.input.Files {
		proj, buildFile := lookupProjectFile(ctx, rtx, f)
		if buildFile != nil {
			if buildProj, exists := out.Projects[proj.Name]; exists {
				buildProj.Files[buildFile.Filename] = buildFile
			} else {
				out.Projects[proj.Name] =
					&app.GitProject{
						RepoDir:   proj.GitProj.RepoDir,
						WorkDir:   proj.GitProj.WorkDir,
						GitDir:    proj.GitProj.GitDir,
						Remote:    proj.GitProj.Remote,
						RemoteUrl: proj.GitProj.RemoteUrl,
						Revision:  proj.GitProj.Revision,
						Files:     map[string]*app.GitTreeObj{buildFile.Filename: buildFile}}
			}
		}
	}
	return (out)
}

// Setup routines to resolve target names to app.BuildInput objects
func targetResolvers(ctx context.Context, rtx *Context) (chan string, chan *buildTargetData) {
	var wg sync.WaitGroup
	inChan := make(chan string)
	outChan := make(chan *buildTargetData)
	for i := 0; i < rtx.BuildWorkerCount; i++ {
		wg.Add(1)
		go func() {
			for targetName := range inChan {
				var buildSteps int
				cmds, err := rtx.Build.Command(ctx, targetName)
				if err == nil {
					buildSteps = len(cmds.Cmds)
				}
				input, err := rtx.Build.Input(ctx, targetName)
				if input == nil {
					fmt.Printf("Failed to get input %s (%s)\n", targetName, err)
				} else {
					outChan <- &buildTargetData{input: input, buildSteps: buildSteps, error: err != nil}
				}
			}
			wg.Done()
		}()
	}
	go func() {
		wg.Wait()
		close(outChan)
	}()

	return inChan, outChan
}

//
// Setup routines to resolve build input targets to BuildTarget
func resolveBuildInputs(ctx context.Context, rtx *Context, inChan chan *buildTargetData) chan *app.BuildTarget {
	var wg sync.WaitGroup
	outChan := make(chan *app.BuildTarget)
	for i := 0; i < rtx.BuildWorkerCount; i++ {
		wg.Add(1)
		go func() {
			for buildTarget := range inChan {
				outChan <- createBuildTarget(ctx, rtx, buildTarget)
			}
			wg.Done()
		}()
	}
	go func() {
		wg.Wait()
		close(outChan)
	}()
	return outChan
}

// Setup routines to resolve source file to query
func queryResolvers(ctx context.Context, rtx *Context) (chan string, chan *buildSourceData) {
	var wg sync.WaitGroup
	inChan := make(chan string)
	outChan := make(chan *buildSourceData)
	for i := 0; i < rtx.BuildWorkerCount; i++ {
		wg.Add(1)
		go func() {
			for srcName := range inChan {
				query, err := rtx.Build.Query(ctx, srcName)
				outChan <- &buildSourceData{source: srcName, query: query, error: err != nil}
			}
			wg.Done()
		}()
	}
	go func() {
		wg.Wait()
		close(outChan)
	}()

	return inChan, outChan
}

// Setup routines to resolve paths
func pathsResolvers(ctx context.Context, rtx *Context, target string, singlePath bool) (chan string, chan *buildPathData) {
	var wg sync.WaitGroup
	inChan := make(chan string)
	outChan := make(chan *buildPathData)
	for i := 0; i < rtx.BuildWorkerCount; i++ {
		wg.Add(1)
		go func() {
			for dep := range inChan {
				if singlePath {
					path, err := rtx.Build.Path(ctx, target, dep)
					outChan <- &buildPathData{filename: dep, path: path, error: err != nil}
				} else {
					paths, err := rtx.Build.Paths(ctx, target, dep)
					if err != nil {
						outChan <- &buildPathData{filename: dep, path: nil, error: true}
					} else {
						for _, path := range paths {

							outChan <- &buildPathData{filename: dep, path: path, error: false}
						}
					}
				}
			}
			wg.Done()
		}()
	}
	go func() {
		wg.Wait()
		close(outChan)
	}()

	return inChan, outChan
}

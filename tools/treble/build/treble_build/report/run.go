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
	"errors"
	"fmt"
	"io/fs"
	"path/filepath"

	"tools/treble/build/report/app"
)

// Find all binary executables under the given directory along with the number
// of symlinks
//
func binaryExecutables(ctx context.Context, dir string, recursive bool) ([]string, int, error) {
	var files []string
	numSymLinks := 0
	err := filepath.WalkDir(dir, func(path string, d fs.DirEntry, err error) error {
		if err != nil {
			return err
		}
		if !d.IsDir() {
			if info, err := d.Info(); err == nil {
				if info.Mode()&0111 != 0 {
					files = append(files, path)
				}
				if d.Type()&fs.ModeSymlink != 0 {
					numSymLinks++
				}
			}
		} else {
			if !recursive {
				if path != dir {
					return filepath.SkipDir
				}
			}
		}
		return nil
	})

	return files, numSymLinks, err
}

// Resolve the manifest
func (rtx *Context) ResolveProjectMap(ctx context.Context, manifest string, upstreamBranch string) {
	if rtx.Info == nil {
		rtx.Info = resolveProjectMap(ctx, rtx, manifest, true, upstreamBranch)
	}
}

// Find host tools
func ResolveHostTools(ctx context.Context, hostToolPath string) (*app.HostReport, error) {
	out := &app.HostReport{Path: hostToolPath}
	out.Targets, out.SymLinks, _ = binaryExecutables(ctx, hostToolPath, true)
	return out, nil
}

// Run reports

//
// Run report request
//
// Setup routines to:
//    - resolve the manifest projects
//    - resolve build queries
//
// Once the manifest projects have been resolved the build
// queries can be fully resolved
//
func RunReport(ctx context.Context, rtx *Context, req *app.ReportRequest) (*app.Report, error) {
	inChan, targetCh := targetResolvers(ctx, rtx)
	go func() {
		for i, _ := range req.Targets {
			inChan <- req.Targets[i]
		}
		close(inChan)
	}()

	// Resolve the build inputs into build target projects
	buildTargetChan := resolveBuildInputs(ctx, rtx, targetCh)

	out := &app.Report{Targets: make(map[string]*app.BuildTarget)}
	for bt := range buildTargetChan {
		out.Targets[bt.Name] = bt
	}

	return out, nil
}

// Resolve commit into git commit info
func ResolveCommit(ctx context.Context, rtx *Context, commit *app.ProjectCommit) (*app.GitCommit, []string, error) {
	if proj, exists := rtx.Info.ProjMap[commit.Project]; exists {
		info, err := rtx.Project.CommitInfo(ctx, proj.GitProj, commit.Revision)
		files := []string{}
		if err == nil {
			for _, f := range info.Files {
				if f.Type != app.GitFileRemoved {
					files = append(files, filepath.Join(proj.GitProj.RepoDir, f.Filename))
				}
			}
		}
		return info, files, err
	}
	return nil, nil, errors.New(fmt.Sprintf("Unknown project %s", commit.Project))

}

// Run query report based on the input request.
//
// For each input file query the target and
// create a set of the inputs and outputs associated
// with all the input files.
//
//
func RunQuery(ctx context.Context, rtx *Context, req *app.QueryRequest) (*app.QueryResponse, error) {
	inChan, queryCh := queryResolvers(ctx, rtx)

	go func() {
		// Convert source files to outputs
		for _, target := range req.Files {
			inChan <- target
		}
		close(inChan)
	}()

	inFiles := make(map[string]bool)
	outFiles := make(map[string]bool)
	unknownSrcFiles := make(map[string]bool)
	for result := range queryCh {
		if result.error {
			unknownSrcFiles[result.source] = true
		} else {
			for _, outFile := range result.query.Outputs {
				outFiles[outFile] = true
			}
			for _, inFile := range result.query.Inputs {
				inFiles[inFile] = true
			}

		}
	}

	out := &app.QueryResponse{}
	for k, _ := range outFiles {
		out.OutputFiles = append(out.OutputFiles, k)
	}
	for k, _ := range inFiles {
		out.InputFiles = append(out.InputFiles, k)
	}
	for k, _ := range unknownSrcFiles {
		out.UnknownFiles = append(out.UnknownFiles, k)
	}

	return out, nil
}

// Get paths
func RunPaths(ctx context.Context, rtx *Context, target string, singlePath bool, files []string) []*app.BuildPath {
	out := []*app.BuildPath{}
	inChan, pathCh := pathsResolvers(ctx, rtx, target, singlePath)
	// Convert source files to outputs
	go func() {
		for _, f := range files {
			inChan <- f
		}
		close(inChan)
	}()

	for result := range pathCh {
		if !result.error {
			out = append(out, result.path)
		}
	}
	return out

}

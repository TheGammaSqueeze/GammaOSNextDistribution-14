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
	"os"
	"path/filepath"
	"strings"
	"sync"

	"tools/treble/build/report/app"
)

//
// Repo and project related functions
//
type project struct {
	Name    string          // Name
	GitProj *app.GitProject // Git project data
}

var unknownProject = &project{Name: "unknown", GitProj: &app.GitProject{}}

// Convert repo project to project with source files and revision
// information
func resolveProject(ctx context.Context, repoProj *app.RepoProject, remote *app.RepoRemote, proj ProjectDependencies, getFiles bool, upstreamBranch string) *project {

	path := repoProj.Path
	if path == "" {
		path = repoProj.Name
	}
	gitDir := ""
	if strings.HasPrefix(path, "overlays/") {
		// Assume two levels of overlay path (overlay/XYZ)
		path = strings.Join(strings.Split(path, "/")[2:], "/")
		// The overlays .git symbolic links are not mapped correctly
		// into the jails.   Resolve them here, inside the nsjail the
		// absolute path for all git repos will be in the form of
		// /src/.git/
		symlink, _ := os.Readlink(filepath.Join(path, ".git"))
		parts := strings.Split(symlink, "/")
		repostart := 0
		for ; repostart < len(parts); repostart++ {
			if parts[repostart] != ".." {
				if repostart > 1 {
					repostart--
					parts[repostart] = "/src"
				}
				break
			}
		}
		gitDir = filepath.Join(parts[repostart:]...)

	}
	gitProj, err := proj.Project(ctx, path, gitDir, remote.Name, repoProj.Revision)
	if err != nil {
		return nil
	}
	out := &project{Name: repoProj.Name, GitProj: gitProj}
	if getFiles {
		_ = proj.PopulateFiles(ctx, gitProj, upstreamBranch)
	}
	return out
}

// Get the build file for a given filename, this is a two step lookup.
// First find the project associated with the file via the file cache,
// then resolve the file via the project found.
//
// Most files will be relative paths from the repo workspace
func lookupProjectFile(ctx context.Context, rtx *Context, filename string) (*project, *app.GitTreeObj) {
	if proj, exists := rtx.Info.FileCache[filename]; exists {
		repoName := (filename)[len(proj.GitProj.RepoDir)+1:]
		if gitObj, exists := proj.GitProj.Files[repoName]; exists {
			return proj, gitObj
		}
		return proj, nil
	} else {
		// Try resolving any symlinks
		if realpath, err := filepath.EvalSymlinks(filename); err == nil {
			if realpath != filename {
				return lookupProjectFile(ctx, rtx, realpath)
			}
		}

		if strings.HasPrefix(filename, rtx.RepoBase) {
			// Some dependencies pick up the full path try stripping out
			relpath := (filename)[len(rtx.RepoBase):]
			return lookupProjectFile(ctx, rtx, relpath)
		}
	}
	return unknownProject, &app.GitTreeObj{Filename: filename, Sha: ""}
}

// Create a mapping of projects from the input source manifest
func resolveProjectMap(ctx context.Context, rtx *Context, manifestFile string, getFiles bool, upstreamBranch string) *ProjectInfo {
	// Parse the manifest file
	manifest, err := rtx.Repo.Manifest(manifestFile)
	if err != nil {
		return nil
	}
	info := &ProjectInfo{}
	// Create map of remotes
	remotes := make(map[string]*app.RepoRemote)
	var defRemotePtr *app.RepoRemote
	for i, _ := range manifest.Remotes {
		remotes[manifest.Remotes[i].Name] = &manifest.Remotes[i]
	}

	defRemotePtr, exists := remotes[manifest.Default.Remote]
	if !exists {
		fmt.Printf("Failed to find default remote")
	}
	info.FileCache = make(map[string]*project)
	info.ProjMap = make(map[string]*project)

	var wg sync.WaitGroup
	projChan := make(chan *project)
	repoChan := make(chan *app.RepoProject)

	for i := 0; i < rtx.WorkerCount; i++ {
		wg.Add(1)
		go func() {
			for repoProj := range repoChan {
				remotePtr := defRemotePtr
				if manifest.Projects[i].Remote != nil {
					remotePtr = remotes[*manifest.Projects[i].Remote]
				}
				proj := resolveProject(ctx, repoProj, remotePtr, rtx.Project, getFiles, upstreamBranch)
				if proj != nil {
					projChan <- proj
				} else {
					projChan <- &project{Name: repoProj.Name}
				}
			}
			wg.Done()
		}()
	}
	go func() {
		wg.Wait()
		close(projChan)
	}()
	go func() {
		for i, _ := range manifest.Projects {
			repoChan <- &manifest.Projects[i]
		}
		close(repoChan)
	}()
	for r := range projChan {
		if r.GitProj != nil {
			info.ProjMap[r.Name] = r
			if len(r.GitProj.Files) > 0 {
				for n := range r.GitProj.Files {
					info.FileCache[filepath.Join(r.GitProj.RepoDir, n)] = r
				}

			}

		} else {
			fmt.Printf("Failed to resolve %s\n", r.Name)
		}
	}
	return info
}

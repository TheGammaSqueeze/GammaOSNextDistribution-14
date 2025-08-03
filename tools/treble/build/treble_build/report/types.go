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
	"tools/treble/build/report/app"
)

type RepoMan struct {
}

func (r *RepoMan) Manifest(filename string) (*app.RepoManifest, error) {
	return app.ParseXml(filename)
}

// Project information containing a map of projects, this also contains a
// map between a source file and the project it belongs to
// allowing a quicker lookup of source file to project
type ProjectInfo struct {
	ProjMap   map[string]*project // Map project name to project
	FileCache map[string]*project // Map source files to project
}

// Report context
type Context struct {
	RepoBase         string              // Absolute path to repo base
	Repo             RepoDependencies    // Repo interface
	Build            BuildDependencies   // Build interface
	Project          ProjectDependencies // Project interface
	WorkerCount      int                 // Number of worker threads
	BuildWorkerCount int                 // Number of build worker threads
	Info             *ProjectInfo        // Project information
}

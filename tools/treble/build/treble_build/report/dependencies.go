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

	"tools/treble/build/report/app"
)

type BuildDependencies interface {
	Command(ctx context.Context, target string) (*app.BuildCommand, error)
	Input(ctx context.Context, target string) (*app.BuildInput, error)
	Query(ctx context.Context, target string) (*app.BuildQuery, error)
	Path(ctx context.Context, target string, dependency string) (*app.BuildPath, error)
	Paths(ctx context.Context, target string, dependency string) ([]*app.BuildPath, error)
	Deps(ctx context.Context) (*app.BuildDeps, error)
}

type ProjectDependencies interface {
	Project(ctx context.Context, path string, gitDir string, remote string, revision string) (*app.GitProject, error)
	PopulateFiles(ctx context.Context, proj *app.GitProject, upstream string) error
	CommitInfo(ctx context.Context, proj *app.GitProject, sha string) (*app.GitCommit, error)
}

type RepoDependencies interface {
	Manifest(filename string) (*app.RepoManifest, error)
}

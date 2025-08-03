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

package app

// GIT diff
type GitDiff struct {
	AddedLines   int  `json:"added_lines"`
	DeletedLines int  `json:"deleted_lines"`
	BinaryDiff   bool `json:"binary_diff"`
}

// GIT tree object (files,dirs...)
type GitTreeObj struct {
	Permissions string   `json:"permissions"`
	Type        string   `json:"type"`
	Sha         string   `json:"sha"`
	Filename    string   `json:"filename"`
	BranchDiff  *GitDiff `json:"branch_diff"`
}

// GitProject
type GitProject struct {
	RepoDir   string                 `json:"repo_dir"`    // Relative directory within repo
	WorkDir   string                 `json:"working_dir"` // Working directory
	GitDir    string                 `json:"git_dir"`     // GIT directory
	Remote    string                 `json:"remote"`      // Remote Name
	RemoteUrl string                 `json:"remote_url"`  // Remote URL
	Revision  string                 `json:"revision"`    // Revision (SHA)
	Files     map[string]*GitTreeObj `json:"files"`       // Files within the project
}

type GitCommitFileType int

const (
	GitFileAdded GitCommitFileType = iota
	GitFileModified
	GitFileRemoved
)

type GitCommitFile struct {
	Filename string            `json:"filename"`
	Type     GitCommitFileType `json:"type"`
}

// Git commit
type GitCommit struct {
	Sha   string          `json:"sha"`
	Files []GitCommitFile `json:"files"`
}

func (t GitCommitFileType) String() string {
	switch t {
	case GitFileModified:
		return "M"
	case GitFileAdded:
		return "A"
	case GitFileRemoved:
		return "R"
	}
	return ""
}

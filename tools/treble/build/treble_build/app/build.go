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

// Query
type BuildQuery struct {
	Target  string   `json:"target"`
	Inputs  []string `json:"inputs"`
	Outputs []string `json:"outputs"`
}

// Input
type BuildInput struct {
	Target string   `json:"target"`
	Files  []string `json:"files"`
}

// Commands
type BuildCommand struct {
	Target string   `json:"target"`
	Cmds   []string `json:"cmds"`
}

// Path
type BuildPath struct {
	Target     string   `json:"target"`
	Dependency string   `json:"dependency"`
	Paths      []string `json:paths"`
}

// Build target
type BuildTarget struct {
	Name      string                 `json:"name"`        // Target name
	Steps     int                    `json:"build_steps"` // Number of steps to build target
	FileCount int                    `json:"files"`       // Number of input files for a target
	Projects  map[string]*GitProject `json:"projects"`    // Inputs projects/files of a target
}

// Build command result
type BuildCmdResult struct {
	Name    string   `json:"name"`
	Output  []string `json:"output"`
	Success bool     `json:"success"`
}

// Build dependencies
type BuildDeps struct {
	Targets map[string][]string `json:"targets"`
}

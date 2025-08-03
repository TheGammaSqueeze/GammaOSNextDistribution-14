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

// Report request structure
type ReportRequest struct {
	Targets []string `json:"targets"` // Targets
}

// Report response data
type Report struct {
	Targets map[string]*BuildTarget `json:"targets"` // Build target data
}

// Host tool report response data
type HostReport struct {
	Path     string   `json:"path"`      // Path to find host tools
	SymLinks int      `json:"sym_links"` // Number of symlinks found
	Targets  []string `json:"targets"`   // Target for tools found
}

// Project level commit
type ProjectCommit struct {
	Project  string `json:"project"`  // Project
	Revision string `json:"revision"` // Revision
}

// Query request
type QueryRequest struct {
	Files []string `json:"files"` // Files to resolve
}

// Output response
type QueryResponse struct {
	InputFiles   []string `json:"input_files"`             // Input files found
	OutputFiles  []string `json:"output_files"`            // Output files found
	UnknownFiles []string `json:"unknown_files,omitempty"` // Unknown files
}

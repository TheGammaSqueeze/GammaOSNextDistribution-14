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

package main

import (
	"context"
	"fmt"
	"io"
	"log"

	"tools/treble/build/report/report"
)

type pathsReport struct {
	build_target string // Target used to filter build request
	single       bool   // Get single path
}

func (p pathsReport) Run(ctx context.Context, rtx *report.Context, rsp *response) error {

	log.Printf("Resolving paths for  %s (single : %v)\n", rsp.Inputs, p.single)
	rsp.Paths = report.RunPaths(ctx, rtx, p.build_target, p.single, rsp.Inputs)

	//  The path is returned in an array in the form [build_target, path_stop1,...,path_stopN,source_file]
	//  Choose the closest build target (path_stopN) to the source file to build to reduce the amount that
	//  is built.
	const buildPathIndex = 2
	build_targets := make(map[string]bool)
	for _, path := range rsp.Paths {
		// Default to build closest build target
		if len(path.Paths) > buildPathIndex {
			build_targets[path.Paths[len(path.Paths)-buildPathIndex]] = true
		}
	}
	for b := range build_targets {
		rsp.Targets = append(rsp.Targets, b)
	}

	return nil
}

func (h *pathsReport) PrintText(w io.Writer, rsp *response, verbose bool) {
	if len(rsp.Paths) > 0 {
		fmt.Fprintln(w, "  Paths")
		for _, p := range rsp.Paths {
			// Provide path from target to dependency with the
			// path length, since target and dependency are in the
			// path subtract them out from length
			fmt.Fprintf(w, "      %s..(%d)..%-s\n", p.Target, len(p.Paths)-2, p.Dependency)
		}
	}
}

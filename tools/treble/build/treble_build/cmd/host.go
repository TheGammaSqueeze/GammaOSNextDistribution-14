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
	"path/filepath"

	"tools/treble/build/report/report"
)

type hostReport struct {
	toolPath string
}

// Determine host tools
func (h *hostReport) Run(ctx context.Context, rtx *report.Context, rsp *response) error {
	var err error
	rsp.Host, err = report.ResolveHostTools(ctx, h.toolPath)
	if err != nil {
		return err
	}
	rsp.Targets = append(rsp.Targets, rsp.Host.Targets...)
	return nil
}

func (h *hostReport) PrintText(w io.Writer, rsp *response, verbose bool) {
	if rsp.Host != nil {
		// Get the unique number of inputs
		hostSourceFileMap := make(map[string]bool)
		hostSourceProjectMap := make(map[string]bool)

		for _, t := range rsp.Host.Targets {
			// Find target in report
			if bt, exists := rsp.Report.Targets[t]; exists {
				for name, proj := range bt.Projects {
					hostSourceProjectMap[name] = true
					for f := range proj.Files {
						hostSourceFileMap[filepath.Join(name, f)] = true
					}
				}
				// Remove the target from being printed
				delete(rsp.Report.Targets, t)
			}
		}

		fmt.Fprintln(w, "  Host Tools")
		fmt.Fprintf(w, "      %-20s       : %s\n", "Directory", rsp.Host.Path)
		fmt.Fprintf(w, "         %-20s    : %d\n", "Tools", len(rsp.Host.Targets))
		fmt.Fprintf(w, "         %-20s    : %d\n", "Prebuilts", rsp.Host.SymLinks)
		fmt.Fprintf(w, "         %-20s    : %d\n", "Inputs", len(hostSourceFileMap))
		fmt.Fprintf(w, "         %-20s    : %d\n", "Projects", len(hostSourceProjectMap))

		if verbose {
			for proj, _ := range hostSourceProjectMap {
				fmt.Fprintf(w, "            %s\n", proj)
			}
		}
	}

}

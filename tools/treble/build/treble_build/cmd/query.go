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
	"io"
	"log"

	"tools/treble/build/report/app"
	"tools/treble/build/report/report"
)

// Command arguments
type queryReport struct {
}

// Run query
func (o queryReport) Run(ctx context.Context, rtx *report.Context, rsp *response) error {
	var err error
	log.Printf("Querying files %s\n", rsp.Inputs)
	req := &app.QueryRequest{Files: rsp.Inputs}
	rsp.Query, err = report.RunQuery(ctx, rtx, req)
	if err != nil {
		return err
	}

	return nil

}
func (h *queryReport) PrintText(w io.Writer, rsp *response, verbose bool) {
}

// Copyright 2015 Google Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// bpglob is the command line tool that checks if the list of files matching a glob has
// changed, and only updates the output file list if it has changed.  It is used to optimize
// out build.ninja regenerations when non-matching files are added.  See
// github.com/google/blueprint/bootstrap/glob.go for a longer description.
package main

import (
	"flag"
	"fmt"
	"io/ioutil"
	"os"
	"time"

	"github.com/google/blueprint/deptools"
	"github.com/google/blueprint/pathtools"
)

var (
	out = flag.String("o", "", "file to write list of files that match glob")

	globs []globArg
)

func init() {
	flag.Var((*patternsArgs)(&globs), "p", "pattern to include in results")
	flag.Var((*excludeArgs)(&globs), "e", "pattern to exclude from results from the most recent pattern")
}

// A glob arg holds a single -p argument with zero or more following -e arguments.
type globArg struct {
	pattern  string
	excludes []string
}

// patternsArgs implements flag.Value to handle -p arguments by adding a new globArg to the list.
type patternsArgs []globArg

func (p *patternsArgs) String() string { return `""` }

func (p *patternsArgs) Set(s string) error {
	globs = append(globs, globArg{
		pattern: s,
	})
	return nil
}

// excludeArgs implements flag.Value to handle -e arguments by adding to the last globArg in the
// list.
type excludeArgs []globArg

func (e *excludeArgs) String() string { return `""` }

func (e *excludeArgs) Set(s string) error {
	if len(*e) == 0 {
		return fmt.Errorf("-p argument is required before the first -e argument")
	}

	glob := &(*e)[len(*e)-1]
	glob.excludes = append(glob.excludes, s)
	return nil
}

func usage() {
	fmt.Fprintln(os.Stderr, "usage: bpglob -o out -p glob [-e excludes ...] [-p glob ...]")
	flag.PrintDefaults()
	os.Exit(2)
}

func main() {
	flag.Parse()

	if *out == "" {
		fmt.Fprintln(os.Stderr, "error: -o is required")
		usage()
	}

	if flag.NArg() > 0 {
		usage()
	}

	err := globsWithDepFile(*out, *out+".d", globs)
	if err != nil {
		// Globs here were already run in the primary builder without error.  The only errors here should be if the glob
		// pattern was made invalid by a change in the pathtools glob implementation, in which case the primary builder
		// needs to be rerun anyways.  Update the output file with something that will always cause the primary builder
		// to rerun.
		writeErrorOutput(*out, err)
	}
}

// writeErrorOutput writes an error to the output file with a timestamp to ensure that it is
// considered dirty by ninja.
func writeErrorOutput(path string, globErr error) {
	s := fmt.Sprintf("%s: error: %s\n", time.Now().Format(time.StampNano), globErr.Error())
	err := ioutil.WriteFile(path, []byte(s), 0666)
	if err != nil {
		fmt.Fprintf(os.Stderr, "error: %s\n", err.Error())
		os.Exit(1)
	}
}

// globsWithDepFile finds all files and directories that match glob.  Directories
// will have a trailing '/'.  It compares the list of matches against the
// contents of fileListFile, and rewrites fileListFile if it has changed.  It
// also writes all of the directories it traversed as dependencies on fileListFile
// to depFile.
//
// The format of glob is either path/*.ext for a single directory glob, or
// path/**/*.ext for a recursive glob.
func globsWithDepFile(fileListFile, depFile string, globs []globArg) error {
	var results pathtools.MultipleGlobResults
	for _, glob := range globs {
		result, err := pathtools.Glob(glob.pattern, glob.excludes, pathtools.FollowSymlinks)
		if err != nil {
			return err
		}
		results = append(results, result)
	}

	// Only write the output file if it has changed.
	err := pathtools.WriteFileIfChanged(fileListFile, results.FileList(), 0666)
	if err != nil {
		return fmt.Errorf("failed to write file list to %q: %w", fileListFile, err)
	}

	// The depfile can be written unconditionally as its timestamp doesn't affect ninja's restat
	// feature.
	err = deptools.WriteDepFile(depFile, fileListFile, results.Deps())
	if err != nil {
		return fmt.Errorf("failed to write dep file to %q: %w", depFile, err)
	}

	return nil
}

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
	"bufio"
	"context"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"log"
	"os"
	"runtime"
	"strings"
	"time"

	"tools/treble/build/report/app"
	"tools/treble/build/report/local"
	"tools/treble/build/report/report"
)

type Build interface {
	Build(ctx context.Context, target string) *app.BuildCmdResult
}

type tool interface {
	Run(ctx context.Context, rtx *report.Context, rsp *response) error
	PrintText(w io.Writer, rsp *response, verbose bool)
}
type repoFlags []app.ProjectCommit

func (r *repoFlags) Set(value string) error {
	commit := app.ProjectCommit{}
	items := strings.Split(value, ":")
	if len(items) > 2 {
		return (errors.New("Invalid repo value expected (proj:sha) format"))
	}
	commit.Project = items[0]
	if len(items) > 1 {
		commit.Revision = items[1]
	}
	*r = append(*r, commit)
	return nil
}
func (r *repoFlags) String() string {
	items := []string{}
	for _, fl := range *r {
		items = append(items, fmt.Sprintf("%s:%s", fl.Project, fl.Revision))
	}
	return strings.Join(items, " ")
}

var (
	// Common flags
	ninjaDbPtr          = flag.String("ninja", local.DefNinjaDb(), "Set the .ninja file to use when building metrics")
	ninjaExcPtr         = flag.String("ninja_cmd", local.DefNinjaExc(), "Set the ninja executable")
	ninjaTimeoutStr     = flag.String("ninja_timeout", local.DefaultNinjaTimeout, "Default ninja timeout")
	buildTimeoutStr     = flag.String("build_timeout", local.DefaultNinjaBuildTimeout, "Default build timeout")
	manifestPtr         = flag.String("manifest", local.DefManifest(), "Set the location of the manifest file")
	upstreamPtr         = flag.String("upstream", "", "Upstream branch to compare files against")
	repoBasePtr         = flag.String("repo_base", local.DefRepoBase(), "Set the repo base directory")
	workerCountPtr      = flag.Int("worker_count", runtime.NumCPU(), "Number of worker routines")
	buildWorkerCountPtr = flag.Int("build_worker_count", local.MaxNinjaCliWorkers, "Number of build worker routines")
	clientServerPtr     = flag.Bool("client_server", false, "Run client server mode")
	buildPtr            = flag.Bool("build", false, "Build targets")
	jsonPtr             = flag.Bool("json", false, "Print json data")
	verbosePtr          = flag.Bool("v", false, "Print verbose text data")
	outputPtr           = flag.String("o", "", "Output to file")
	projsPtr            = flag.Bool("projects", false, "Include project repo data")

	hostFlags  = flag.NewFlagSet("host", flag.ExitOnError)
	queryFlags = flag.NewFlagSet("query", flag.ExitOnError)
	pathsFlags = flag.NewFlagSet("paths", flag.ExitOnError)
)

// Add profiling data
type profTime struct {
	Description  string  `json:"description"`
	DurationSecs float64 `json:"duration"`
}

type commit struct {
	Project app.ProjectCommit `json:"project"`
	Commit  *app.GitCommit    `json:"commit"`
}

// Use one structure for output for now
type response struct {
	Commits    []commit              `json:"commits,omitempty"`
	Inputs     []string              `json:"files,omitempty"`
	BuildFiles []*app.BuildCmdResult `json:"build_files,omitempty"`
	Targets    []string              `json:"targets,omitempty"`
	Report     *app.Report           `json:"report,omitempty"`

	// Subcommand data
	Query    *app.QueryResponse         `json:"query,omitempty"`
	Paths    []*app.BuildPath           `json:"build_paths,omitempty"`
	Host     *app.HostReport            `json:"host,omitempty"`
	Projects map[string]*app.GitProject `json:"projects,omitempty"`
	// Profile data
	Profile []*profTime `json:"profile"`
}

func main() {
	startTime := time.Now()
	ctx := context.Background()
	rsp := &response{}

	var addProfileData = func(desc string) {
		rsp.Profile = append(rsp.Profile, &profTime{Description: desc, DurationSecs: time.Since(startTime).Seconds()})
		startTime = time.Now()
	}
	flag.Parse()

	ninjaTimeout, err := time.ParseDuration(*ninjaTimeoutStr)
	if err != nil {
		log.Fatalf("Invalid ninja timeout %s", *ninjaTimeoutStr)
	}

	buildTimeout, err := time.ParseDuration(*buildTimeoutStr)
	if err != nil {
		log.Fatalf("Invalid build timeout %s", *buildTimeoutStr)
	}

	subArgs := flag.Args()
	defBuildTarget := "droid"
	log.SetFlags(log.LstdFlags | log.Llongfile)

	ninja := local.NewNinjaCli(*ninjaExcPtr, *ninjaDbPtr, ninjaTimeout, buildTimeout, *clientServerPtr)

	if *clientServerPtr {
		ninjaServ := local.NewNinjaServer(*ninjaExcPtr, *ninjaDbPtr)
		defer ninjaServ.Kill()
		go func() {

			ninjaServ.Start(ctx)
		}()
		if err := ninja.WaitForServer(ctx, int(ninjaTimeout.Seconds())); err != nil {
			log.Fatalf("Failed to connect to server")
		}
	}
	rtx := &report.Context{
		RepoBase:         *repoBasePtr,
		Repo:             &report.RepoMan{},
		Build:            ninja,
		Project:          local.NewGitCli(),
		WorkerCount:      *workerCountPtr,
		BuildWorkerCount: *buildWorkerCountPtr,
	}

	var subcommand tool
	var commits repoFlags
	if len(subArgs) > 0 {
		switch subArgs[0] {
		case "host":
			hostToolPathPtr := hostFlags.String("hostbin", local.DefHostBinPath(), "Set the output directory for host tools")
			hostFlags.Parse(subArgs[1:])

			subcommand = &hostReport{toolPath: *hostToolPathPtr}
			rsp.Targets = hostFlags.Args()

		case "query":
			queryFlags.Var(&commits, "repo", "Repo:SHA to query")
			queryFlags.Parse(subArgs[1:])
			subcommand = &queryReport{}
			rsp.Targets = queryFlags.Args()

		case "paths":
			pathsFlags.Var(&commits, "repo", "Repo:SHA to build")
			singlePathPtr := pathsFlags.Bool("1", false, "Get single path to output target")
			pathsFlags.Parse(subArgs[1:])

			subcommand = &pathsReport{build_target: defBuildTarget, single: *singlePathPtr}

			rsp.Inputs = pathsFlags.Args()

		default:
			rsp.Targets = subArgs
		}
	}
	addProfileData("Init")
	rtx.ResolveProjectMap(ctx, *manifestPtr, *upstreamPtr)
	addProfileData("Project Map")

	// Add project to output if requested
	if *projsPtr == true {
		rsp.Projects = make(map[string]*app.GitProject)
		for k, p := range rtx.Info.ProjMap {
			rsp.Projects[k] = p.GitProj
		}
	}

	// Resolve any commits
	if len(commits) > 0 {
		log.Printf("Resolving %s", commits.String())
		for _, c := range commits {
			commit := commit{Project: c}
			info, files, err := report.ResolveCommit(ctx, rtx, &c)
			if err != nil {
				log.Fatalf("Failed to resolve commit %s:%s", c.Project, c.Revision)
			}
			commit.Commit = info
			rsp.Commits = append(rsp.Commits, commit)

			// Add files to list of inputs
			rsp.Inputs = append(rsp.Inputs, files...)
		}
		addProfileData("Commit Resolution")
	}

	// Run any sub tools
	if subcommand != nil {
		if err := subcommand.Run(ctx, rtx, rsp); err != nil {
			log.Fatal(err)
		}
		addProfileData(subArgs[0])
	}

	buildErrors := 0
	if *buildPtr {
		// Only support default builder (non server-client)
		builder := local.NewNinjaCli(local.DefNinjaExc(), *ninjaDbPtr, ninjaTimeout, buildTimeout, false /*clientMode*/)
		for _, t := range rsp.Targets {
			log.Printf("Building %s\n", t)
			res := builder.Build(ctx, t)
			addProfileData(fmt.Sprintf("Build %s", t))
			log.Printf("%s\n", res.Output)
			if res.Success != true {
				buildErrors++
			}
			rsp.BuildFiles = append(rsp.BuildFiles, res)
		}
	}

	// Generate report
	log.Printf("Generating report for targets %s", rsp.Targets)
	req := &app.ReportRequest{Targets: rsp.Targets}
	rsp.Report, err = report.RunReport(ctx, rtx, req)
	addProfileData("Report")
	if err != nil {
		log.Fatal(fmt.Sprintf("Report failure <%s>", err))
	}

	if *jsonPtr {
		b, _ := json.MarshalIndent(rsp, "", "\t")
		if *outputPtr == "" {
			os.Stdout.Write(b)
		} else {
			os.WriteFile(*outputPtr, b, 0644)
		}
	} else {
		if *outputPtr == "" {
			printTextReport(os.Stdout, subcommand, rsp, *verbosePtr)
		} else {
			file, err := os.Create(*outputPtr)
			if err != nil {
				log.Fatalf("Failed to create output file %s (%s)", *outputPtr, err)
			}
			w := bufio.NewWriter(file)
			printTextReport(w, subcommand, rsp, *verbosePtr)
			w.Flush()
		}

	}

	if buildErrors > 0 {
		log.Fatal(fmt.Sprintf("Failed to build %d targets", buildErrors))
	}
}

func printTextReport(w io.Writer, subcommand tool, rsp *response, verbose bool) {
	fmt.Fprintln(w, "Metric Report")
	if subcommand != nil {
		subcommand.PrintText(w, rsp, verbose)
	}

	if len(rsp.Commits) > 0 {
		fmt.Fprintln(w, "")
		fmt.Fprintln(w, "  Commit Results")
		for _, c := range rsp.Commits {
			fmt.Fprintf(w, "   %-120s : %s\n", c.Project.Project, c.Project.Revision)
			fmt.Fprintf(w, "       SHA   : %s\n", c.Commit.Sha)
			fmt.Fprintf(w, "       Files : \n")
			for _, f := range c.Commit.Files {
				fmt.Fprintf(w, "         %s  %s\n", f.Type.String(), f.Filename)
			}
		}
	}
	if len(rsp.BuildFiles) > 0 {
		fmt.Fprintln(w, "")
		fmt.Fprintln(w, "  Build Files")
		for _, b := range rsp.BuildFiles {
			fmt.Fprintf(w, "            %-120s : %t \n", b.Name, b.Success)
		}
	}

	targetPrint := func(target *app.BuildTarget) {
		fmt.Fprintf(w, "      %-20s       : %s\n", "Name", target.Name)
		fmt.Fprintf(w, "         %-20s    : %d\n", "Build Steps", target.Steps)
		fmt.Fprintf(w, "         %-20s        \n", "Inputs")
		fmt.Fprintf(w, "            %-20s : %d\n", "Files", target.FileCount)
		fmt.Fprintf(w, "            %-20s : %d\n", "Projects", len(target.Projects))
		fmt.Fprintln(w)
		for name, proj := range target.Projects {
			forkCount := 0
			for _, file := range proj.Files {
				if file.BranchDiff != nil {
					forkCount++
				}
			}
			fmt.Fprintf(w, "            %-120s : %d ", name, len(proj.Files))
			if forkCount != 0 {
				fmt.Fprintf(w, " (%d)\n", forkCount)
			} else {
				fmt.Fprintf(w, " \n")
			}

			if verbose {
				for _, file := range proj.Files {
					var fork string
					if file.BranchDiff != nil {
						fork = fmt.Sprintf("(%d+ %d-)", file.BranchDiff.AddedLines, file.BranchDiff.DeletedLines)
					}
					fmt.Fprintf(w, "               %-20s %s\n", fork, file.Filename)
				}

			}
		}

	}
	fmt.Fprintln(w, "  Targets")
	for _, t := range rsp.Report.Targets {
		targetPrint(t)
	}

	fmt.Fprintln(w, "  Run Times")
	for _, p := range rsp.Profile {
		fmt.Fprintf(w, "     %-30s : %f secs\n", p.Description, p.DurationSecs)
	}

}

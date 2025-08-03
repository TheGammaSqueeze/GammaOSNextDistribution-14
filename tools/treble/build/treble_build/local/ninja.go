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

package local

import (
	"bufio"
	"bytes"
	"context"
	"errors"
	"fmt"
	"io"
	"io/ioutil"
	"os/exec"
	"strings"
	"time"

	"tools/treble/build/report/app"
)

// Performance degrades running multiple CLIs
const (
	MaxNinjaCliWorkers       = 4
	DefaultNinjaTimeout      = "100s"
	DefaultNinjaBuildTimeout = "30m"
)

// Separate out the executable to allow tests to override the results
type ninjaExec interface {
	Command(ctx context.Context, target string) (*bytes.Buffer, error)
	Input(ctx context.Context, target string) (*bytes.Buffer, error)
	Query(ctx context.Context, target string) (*bytes.Buffer, error)
	Path(ctx context.Context, target string, dependency string) (*bytes.Buffer, error)
	Paths(ctx context.Context, target string, dependency string) (*bytes.Buffer, error)
	Deps(ctx context.Context) (*bytes.Buffer, error)
	Build(ctx context.Context, target string) (*bytes.Buffer, error)
}

// Parse data

// Add all lines to a given array removing any leading whitespace
func linesToArray(s *bufio.Scanner, arr *[]string) {
	for s.Scan() {
		line := strings.TrimSpace(s.Text())
		*arr = append(*arr, line)
	}
}

// parse -t commands
func parseCommand(target string, data *bytes.Buffer) (*app.BuildCommand, error) {
	out := &app.BuildCommand{Target: target, Cmds: []string{}}
	s := bufio.NewScanner(data)
	// This tool returns all the commands needed to build a target.
	// When running against a target like droid the default capacity
	// will be overrun.   Extend the capacity here.
	const capacity = 1024 * 1024
	buf := make([]byte, capacity)
	s.Buffer(buf, capacity)
	linesToArray(s, &out.Cmds)
	return out, nil
}

// parse -t inputs
func parseInput(target string, data *bytes.Buffer) (*app.BuildInput, error) {
	out := &app.BuildInput{Target: target, Files: []string{}}
	s := bufio.NewScanner(data)
	linesToArray(s, &out.Files)
	return out, nil
}

// parse -t query
func parseQuery(target string, data *bytes.Buffer) (*app.BuildQuery, error) {
	out := &app.BuildQuery{Target: target, Inputs: []string{}, Outputs: []string{}}
	const (
		unknown = iota
		inputs
		outputs
	)
	state := unknown
	s := bufio.NewScanner(data)
	for s.Scan() {
		line := strings.TrimSpace(s.Text())
		if strings.HasPrefix(line, "input:") {
			state = inputs
		} else if strings.HasPrefix(line, "outputs:") {
			state = outputs
		} else {
			switch state {
			case inputs:
				out.Inputs = append(out.Inputs, line)
			case outputs:
				out.Outputs = append(out.Outputs, line)
			}
		}
	}
	return out, nil
}

// parse -t path
func parsePath(target string, dependency string, data *bytes.Buffer) (*app.BuildPath, error) {
	out := &app.BuildPath{Target: target, Dependency: dependency, Paths: []string{}}
	s := bufio.NewScanner(data)
	linesToArray(s, &out.Paths)
	return out, nil
}

// parse -t paths
func parsePaths(target string, dependency string, data *bytes.Buffer) ([]*app.BuildPath, error) {
	out := []*app.BuildPath{}
	s := bufio.NewScanner(data)
	for s.Scan() {
		path := strings.Fields(s.Text())
		out = append(out, &app.BuildPath{Target: target, Dependency: dependency, Paths: path})
	}
	return out, nil
}

// parse build output
func parseBuild(target string, data *bytes.Buffer, success bool) *app.BuildCmdResult {
	out := &app.BuildCmdResult{Name: target, Output: []string{}}
	s := bufio.NewScanner(data)
	out.Success = success
	linesToArray(s, &out.Output)
	return out
}

// parse deps command
func parseDeps(data *bytes.Buffer) (*app.BuildDeps, error) {
	out := &app.BuildDeps{Targets: make(map[string][]string)}
	s := bufio.NewScanner(data)
	curTarget := ""
	var deps []string
	for s.Scan() {
		line := strings.TrimSpace(s.Text())
		// Check if it's a new target
		tokens := strings.Split(line, ":")
		if len(tokens) > 1 {
			if curTarget != "" {
				out.Targets[curTarget] = deps
			}
			deps = []string{}
			curTarget = tokens[0]
		} else if line != "" {
			deps = append(deps, line)
		}

	}
	if curTarget != "" {
		out.Targets[curTarget] = deps
	}
	return out, nil
}

//
// Command line interface to ninja binary.
//
// This file implements the ninja.Ninja interface by querying
// the build graph via the ninja binary.  The mapping between
// the interface and the binary are as follows:
//    Command()   -t commands
//    Input()     -t inputs
//    Query()     -t query
//    Path()      -t path
//    Paths()     -t paths
//    Deps()      -t deps
//
//

type ninjaCmd struct {
	cmd string
	db  string

	clientMode   bool
	timeout      time.Duration
	buildTimeout time.Duration
}

func (n *ninjaCmd) runTool(ctx context.Context, tool string, targets []string) (out *bytes.Buffer, err error) {

	args := []string{"-f", n.db}

	if n.clientMode {
		args = append(args, []string{
			"-t", "client",
			"-c", tool}...)
	} else {
		args = append(args, []string{"-t", tool}...)
	}
	args = append(args, targets...)
	data := []byte{}
	err, _ = runPipe(ctx, n.timeout, n.cmd, args, func(r io.Reader) {
		data, _ = ioutil.ReadAll(r)
	})
	return bytes.NewBuffer(data), err

}
func (n *ninjaCmd) Command(ctx context.Context, target string) (*bytes.Buffer, error) {
	return n.runTool(ctx, "commands", []string{target})
}
func (n *ninjaCmd) Input(ctx context.Context, target string) (*bytes.Buffer, error) {
	return n.runTool(ctx, "inputs", []string{target})
}
func (n *ninjaCmd) Query(ctx context.Context, target string) (*bytes.Buffer, error) {
	return n.runTool(ctx, "query", []string{target})
}
func (n *ninjaCmd) Path(ctx context.Context, target string, dependency string) (*bytes.Buffer, error) {
	return n.runTool(ctx, "path", []string{target, dependency})
}
func (n *ninjaCmd) Paths(ctx context.Context, target string, dependency string) (*bytes.Buffer, error) {
	return n.runTool(ctx, "paths", []string{target, dependency})
}
func (n *ninjaCmd) Deps(ctx context.Context) (*bytes.Buffer, error) {
	return n.runTool(ctx, "deps", []string{})
}

func (n *ninjaCmd) Build(ctx context.Context, target string) (*bytes.Buffer, error) {

	args := append([]string{
		"-f", n.db,
		target})
	data := []byte{}
	err, _ := runPipe(ctx, n.buildTimeout, n.cmd, args, func(r io.Reader) {
		data, _ = ioutil.ReadAll(r)
	})

	return bytes.NewBuffer(data), err
}

// Command line ninja
type ninjaCli struct {
	n ninjaExec
}

// ninja -t commands
func (cli *ninjaCli) Command(ctx context.Context, target string) (*app.BuildCommand, error) {
	raw, err := cli.n.Command(ctx, target)
	if err != nil {
		return nil, err
	}
	return parseCommand(target, raw)
}

// ninja -t inputs
func (cli *ninjaCli) Input(ctx context.Context, target string) (*app.BuildInput, error) {
	raw, err := cli.n.Input(ctx, target)
	if err != nil {
		return nil, err
	}
	return parseInput(target, raw)
}

// ninja -t query
func (cli *ninjaCli) Query(ctx context.Context, target string) (*app.BuildQuery, error) {
	raw, err := cli.n.Query(ctx, target)
	if err != nil {
		return nil, err
	}
	return parseQuery(target, raw)
}

// ninja -t path
func (cli *ninjaCli) Path(ctx context.Context, target string, dependency string) (*app.BuildPath, error) {
	raw, err := cli.n.Path(ctx, target, dependency)
	if err != nil {
		return nil, err
	}
	return parsePath(target, dependency, raw)
}

// ninja -t paths
func (cli *ninjaCli) Paths(ctx context.Context, target string, dependency string) ([]*app.BuildPath, error) {
	raw, err := cli.n.Paths(ctx, target, dependency)
	if err != nil {
		return nil, err
	}
	return parsePaths(target, dependency, raw)
}

// ninja -t deps
func (cli *ninjaCli) Deps(ctx context.Context) (*app.BuildDeps, error) {
	raw, err := cli.n.Deps(ctx)
	if err != nil {
		return nil, err
	}
	return parseDeps(raw)
}

// Build given target
func (cli *ninjaCli) Build(ctx context.Context, target string) *app.BuildCmdResult {
	raw, err := cli.n.Build(ctx, target)
	return parseBuild(target, raw, err == nil)

}

// Wait for server
func (cli *ninjaCli) WaitForServer(ctx context.Context, maxTries int) error {
	// Wait for server to response to an empty input request
	fmt.Printf("Waiting for server.")
	for i := 0; i < maxTries; i++ {
		_, err := cli.Input(ctx, "")
		if err == nil {
			fmt.Printf("\nConnected\n")
			return nil
		}
		fmt.Printf(".")
		time.Sleep(time.Second)
	}
	fmt.Printf(" failed\n")
	return errors.New("Failed to connect")
}
func NewNinjaCli(cmd string, db string, timeout, buildTimeout time.Duration, client bool) *ninjaCli {
	cli := &ninjaCli{n: &ninjaCmd{cmd: cmd, db: db, timeout: timeout, buildTimeout: buildTimeout, clientMode: client}}
	return cli
}

type ninjaServer struct {
	cmdName string
	db      string
	ctx     *exec.Cmd
}

// Run server
func (srv *ninjaServer) Start(ctx context.Context) error {
	args := []string{"-f", srv.db, "-t", "server"}
	srv.ctx = exec.CommandContext(ctx, srv.cmdName, args[0:]...)
	err := srv.ctx.Start()
	if err != nil {
		return err
	}
	srv.ctx.Wait()
	return nil
}
func (srv *ninjaServer) Kill() {
	if srv.ctx != nil {
		srv.ctx.Process.Kill()
	}
}
func NewNinjaServer(cmd string, db string) *ninjaServer {
	return &ninjaServer{cmdName: cmd, db: db}
}

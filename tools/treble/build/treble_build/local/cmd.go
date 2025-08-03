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
	"io"
	"os/exec"
	"time"
)

// Run the input command via pipe with given arguments, stdout of the pipe is passed to input parser
// argument.
func runPipe(ctx context.Context, timeout time.Duration, cmdName string, args []string, parser func(r io.Reader)) (err error, stdErr string) {
	ctx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()

	cmd := exec.CommandContext(ctx, cmdName, args[0:]...)
	errorBuf := bytes.Buffer{}
	cmd.Stderr = &errorBuf
	stdout, err := cmd.StdoutPipe()
	if err != nil {
		return err, errorBuf.String()
	}

	if err = cmd.Start(); err != nil {
		return err, errorBuf.String()
	}
	parser(stdout)
	if err = cmd.Wait(); err != nil {
		return err, errorBuf.String()
	}
	return nil, ""
}

// Run input command, stdout is passed via out parameter to user, if error the stderr is provided via
// stdErr string to the user.
func run(ctx context.Context, timeout time.Duration, cmdName string, args []string) (out *bytes.Buffer, err error, stdErr string) {
	ctx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()

	cmd := exec.CommandContext(ctx, cmdName, args[0:]...)
	errorBuf := bytes.Buffer{}
	outputBuf := bytes.Buffer{}
	cmd.Stderr = &errorBuf
	cmd.Stdout = &outputBuf
	if err = cmd.Run(); err != nil {
		return nil, err, errorBuf.String()
	}

	return &outputBuf, nil, ""
}

// lineScanner
//
//  Map output lines to strings, with expected number of
// lines
type lineScanner struct {
	Lines []string
}

// Parse into lines
func (l *lineScanner) Parse(s *bufio.Scanner) error {
	i := 0
	for s.Scan() {
		if i < len(l.Lines) {
			l.Lines[i] = s.Text()
		} else {
			i++
			break
		}
		i++
	}
	if i != len(l.Lines) {
		return errors.New("cmd: incorrect number of lines")
	}
	return nil
}

func newLineScanner(numLines int) *lineScanner {
	out := &lineScanner{Lines: make([]string, numLines)}
	return (out)
}

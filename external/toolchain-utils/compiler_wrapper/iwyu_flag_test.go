// Copyright 2022 The ChromiumOS Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package main

import (
	"errors"
	"io"
	"strings"
	"testing"
)

func TestIWYUArgOrder(t *testing.T) {
	withIWYUTestContext(t, func(ctx *testContext) {
		ctx.cmdMock = func(cmd *command, stdin io.Reader, stdout io.Writer, stderr io.Writer) error {
			if ctx.cmdCount == 2 {
				if err := verifyArgOrder(cmd, "-checks=.*", mainCc, "--", "-resource-dir=.*", mainCc, "--some_arg"); err != nil {
					return err
				}
			}
			return nil
		}
		ctx.must(callCompiler(ctx, ctx.cfg,
			ctx.newCommand(clangX86_64, mainCc, "--some_arg")))
		if ctx.cmdCount < 2 {
			t.Error("expected multiple calls.")
		}
	})
}

func TestIgnoreNonZeroExitCodeFromIWYU(t *testing.T) {
	withIWYUTestContext(t, func(ctx *testContext) {
		ctx.cmdMock = func(cmd *command, stdin io.Reader, stdout io.Writer, stderr io.Writer) error {
			if ctx.cmdCount == 2 {
				return newExitCodeError(23)
			}
			return nil
		}
		ctx.must(callCompiler(ctx, ctx.cfg,
			ctx.newCommand(clangX86_64, mainCc)))
		stderr := ctx.stderrString()
		if err := verifyNonInternalError(stderr, "include-what-you-use failed"); err != nil {
			t.Error(err)
		}
	})
}

func TestReportGeneralErrorsFromIWYU(t *testing.T) {
	withIWYUTestContext(t, func(ctx *testContext) {
		ctx.cmdMock = func(cmd *command, stdin io.Reader, stdout io.Writer, stderr io.Writer) error {
			if ctx.cmdCount > 1 {
				return errors.New("someerror")
			}
			return nil
		}
		stderr := ctx.mustFail(callCompiler(ctx, ctx.cfg,
			ctx.newCommand(clangX86_64, mainCc)))
		if err := verifyInternalError(stderr); err != nil {
			t.Fatal(err)
		}
		if !strings.Contains(stderr, "someerror") {
			t.Errorf("unexpected error. Got: %s", stderr)
		}
	})
}

func TestUseIWYUBasedOnFileExtension(t *testing.T) {
	withIWYUTestContext(t, func(ctx *testContext) {
		testData := []struct {
			args []string
			iwyu bool
		}{
			{[]string{"main.cc"}, true},
			{[]string{"main.cc"}, true},
			{[]string{"main.C"}, true},
			{[]string{"main.cxx"}, true},
			{[]string{"main.c++"}, true},
			{[]string{"main.xy"}, false},
			{[]string{"-o", "main.cc"}, false},
			{[]string{}, false},
		}
		for _, tt := range testData {
			ctx.cmdCount = 0
			ctx.must(callCompiler(ctx, ctx.cfg,
				ctx.newCommand(clangX86_64, tt.args...)))
			if ctx.cmdCount == 2 && !tt.iwyu {
				t.Errorf("expected a call to iwyu but got none for args %s", tt.args)
			}
			if ctx.cmdCount == 1 && tt.iwyu {
				t.Errorf("expected no call to iwyu but got one for args %s", tt.args)
			}
		}
	})
}

func TestIWYUFiltersIWYUFlags(t *testing.T) {
	withIWYUTestContext(t, func(ctx *testContext) {
		addedFlag := "--some_iwyu_flag=flag"
		ctx.cmdMock = func(cmd *command, stdin io.Reader, stdout io.Writer, stderr io.Writer) error {
			switch ctx.cmdCount {
			case 1:
				if err := verifyPath(cmd, "usr/bin/clang"); err != nil {
					t.Error(err)
				} else if err := verifyArgCount(cmd, 0, addedFlag); err != nil {
					t.Error(err)
				}
				return nil
			case 2:
				if err := verifyPath(cmd, "usr/bin/include-what-you-use"); err != nil {
					t.Error(err)
				} else if verifyArgCount(cmd, 1, addedFlag); err != nil {
					t.Error(err)
				}
				return nil
			default:
				return nil
			}
		}
		cmd := ctx.must(callCompiler(ctx, ctx.cfg, ctx.newCommand(clangX86_64, mainCc, "-iwyu-flag="+addedFlag)))
		if ctx.cmdCount < 2 {
			t.Errorf("expected multiple calls.")
		}
		if err := verifyPath(cmd, "usr/bin/clang"); err != nil {
			t.Error(err)
		}
	})
}

func withIWYUTestContext(t *testing.T, work func(ctx *testContext)) {
	withTestContext(t, func(ctx *testContext) {
		artifactDir := t.TempDir()
		ctx.env = []string{"WITH_IWYU=1", "CROS_ARTIFACTS_TMP_DIR=" + artifactDir}
		work(ctx)
	})
}

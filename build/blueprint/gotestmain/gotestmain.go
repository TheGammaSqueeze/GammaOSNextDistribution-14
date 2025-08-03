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

package main

import (
	"bytes"
	"flag"
	"fmt"
	"go/ast"
	"go/doc"
	"go/parser"
	"go/token"
	"io/ioutil"
	"os"
	"reflect"
	"sort"
	"strings"
	"testing"
	"text/template"
)

var (
	output   = flag.String("o", "", "output filename")
	pkg      = flag.String("pkg", "", "test package")
	exitCode = 0
)

type data struct {
	Package               string
	Tests                 []string
	Examples              []*doc.Example
	HasMain               bool
	MainStartTakesFuzzers bool
}

func findTests(srcs []string) (tests []string, examples []*doc.Example, hasMain bool) {
	for _, src := range srcs {
		f, err := parser.ParseFile(token.NewFileSet(), src, nil, parser.ParseComments)
		if err != nil {
			panic(err)
		}
		for _, obj := range f.Scope.Objects {
			if obj.Kind != ast.Fun || !strings.HasPrefix(obj.Name, "Test") {
				continue
			}
			if obj.Name == "TestMain" {
				hasMain = true
			} else {
				tests = append(tests, obj.Name)
			}
		}

		examples = append(examples, doc.Examples(f)...)
	}
	sort.Strings(tests)
	return
}

// Returns true for go1.18+, where testing.MainStart takes an extra slice of fuzzers.
func mainStartTakesFuzzers() bool {
	return reflect.TypeOf(testing.MainStart).NumIn() > 4
}

func main() {
	flag.Parse()

	if flag.NArg() == 0 {
		fmt.Fprintln(os.Stderr, "error: must pass at least one input")
		exitCode = 1
		return
	}

	buf := &bytes.Buffer{}

	tests, examples, hasMain := findTests(flag.Args())

	d := data{
		Package:               *pkg,
		Tests:                 tests,
		Examples:              examples,
		HasMain:               hasMain,
		MainStartTakesFuzzers: mainStartTakesFuzzers(),
	}

	err := testMainTmpl.Execute(buf, d)
	if err != nil {
		panic(err)
	}

	err = ioutil.WriteFile(*output, buf.Bytes(), 0666)
	if err != nil {
		panic(err)
	}
}

var testMainTmpl = template.Must(template.New("testMain").Parse(`
package main

import (
	"io"
{{if not .HasMain}}
	"os"
{{end}}
	"reflect"
	"regexp"
	"testing"
	"time"

	pkg "{{.Package}}"
)

var t = []testing.InternalTest{
{{range .Tests}}
	{"{{.}}", pkg.{{.}}},
{{end}}
}

var e = []testing.InternalExample{
{{range .Examples}}
	{{if or .Output .EmptyOutput}}
		{"{{.Name}}", pkg.Example{{.Name}}, {{.Output | printf "%q" }}, {{.Unordered}}},
	{{end}}
{{end}}
}

var matchPat string
var matchRe *regexp.Regexp

type matchString struct{}

func MatchString(pat, str string) (result bool, err error) {
	if matchRe == nil || matchPat != pat {
		matchPat = pat
		matchRe, err = regexp.Compile(matchPat)
		if err != nil {
			return
		}
	}
	return matchRe.MatchString(str), nil
}

func (matchString) MatchString(pat, str string) (bool, error) {
	return MatchString(pat, str)
}

func (matchString) StartCPUProfile(w io.Writer) error {
	panic("shouldn't get here")
}

func (matchString) StopCPUProfile() {
}

func (matchString) WriteHeapProfile(w io.Writer) error {
    panic("shouldn't get here")
}

func (matchString) WriteProfileTo(string, io.Writer, int) error {
    panic("shouldn't get here")
}

func (matchString) ImportPath() string {
	return "{{.Package}}"
}

func (matchString) StartTestLog(io.Writer) {
	panic("shouldn't get here")
}

func (matchString) StopTestLog() error {
	panic("shouldn't get here")
}

func (matchString) SetPanicOnExit0(bool) {
	panic("shouldn't get here")
}

func (matchString) CoordinateFuzzing(time.Duration, int64, time.Duration, int64, int, []corpusEntry, []reflect.Type, string, string) error {
	panic("shouldn't get here")
}

func (matchString) RunFuzzWorker(func(corpusEntry) error) error {
	panic("shouldn't get here")
}

func (matchString) ReadCorpus(string, []reflect.Type) ([]corpusEntry, error) {
	panic("shouldn't get here")
}

func (matchString) CheckCorpus([]interface{}, []reflect.Type) error {
	panic("shouldn't get here")
}

func (matchString) ResetCoverage() {
	panic("shouldn't get here")
}

func (matchString) SnapshotCoverage() {
	panic("shouldn't get here")
}

type corpusEntry = struct {
	Parent     string
	Path       string
	Data       []byte
	Values     []interface{}
	Generation int
	IsSeed     bool
}

func main() {
{{if .MainStartTakesFuzzers }}
	m := testing.MainStart(matchString{}, t, nil, nil, e)
{{else}}
	m := testing.MainStart(matchString{}, t, nil, e)
{{end}}
{{if .HasMain}}
	pkg.TestMain(m)
{{else}}
	os.Exit(m.Run())
{{end}}
}
`))

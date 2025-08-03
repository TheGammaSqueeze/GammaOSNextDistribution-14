// Copyright 2014 Google Inc. All rights reserved.
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

package blueprint

import (
	"bytes"
	"fmt"
	"io"
	"slices"
	"strings"
)

const eof = -1

var (
	defaultEscaper = strings.NewReplacer(
		"\n", "$\n")
	inputEscaper = strings.NewReplacer(
		"\n", "$\n",
		" ", "$ ")
	outputEscaper = strings.NewReplacer(
		"\n", "$\n",
		" ", "$ ",
		":", "$:")
)

// ninjaString contains the parsed result of a string that can contain references to variables (e.g. $cflags) that will
// be propagated to the build.ninja file.  For literal strings with no variable references, the variables field will be
// nil. For strings with variable references str contains the original, unparsed string, and variables contains a
// pointer to a list of references, each with a span of bytes they should replace and a Variable interface.
type ninjaString struct {
	str       string
	variables *[]variableReference
}

// variableReference contains information about a single reference to a variable (e.g. $cflags) inside a parsed
// ninjaString.  start and end are int32 to reduce memory usage.  A nil variable is a special case of an inserted '$'
// at the beginning of the string to handle leading whitespace that must not be stripped by ninja.
type variableReference struct {
	// start is the offset of the '$' character from the beginning of the unparsed string.
	start int32

	// end is the offset of the character _after_ the final character of the variable name (or '}' if using the
	//'${}'  syntax)
	end int32

	variable Variable
}

type scope interface {
	LookupVariable(name string) (Variable, error)
	IsRuleVisible(rule Rule) bool
	IsPoolVisible(pool Pool) bool
}

func simpleNinjaString(str string) *ninjaString {
	return &ninjaString{str: str}
}

type parseState struct {
	scope        scope
	str          string
	varStart     int
	varNameStart int
	result       *ninjaString
}

func (ps *parseState) pushVariable(start, end int, v Variable) {
	if ps.result.variables == nil {
		ps.result.variables = &[]variableReference{{start: int32(start), end: int32(end), variable: v}}
	} else {
		*ps.result.variables = append(*ps.result.variables, variableReference{start: int32(start), end: int32(end), variable: v})
	}
}

type stateFunc func(*parseState, int, rune) (stateFunc, error)

// parseNinjaString parses an unescaped ninja string (i.e. all $<something>
// occurrences are expected to be variables or $$) and returns a *ninjaString
// that contains the original string and a list of the referenced variables.
func parseNinjaString(scope scope, str string) (*ninjaString, error) {
	ninjaString, str, err := parseNinjaOrSimpleString(scope, str)
	if err != nil {
		return nil, err
	}
	if ninjaString != nil {
		return ninjaString, nil
	}
	return simpleNinjaString(str), nil
}

// parseNinjaOrSimpleString parses an unescaped ninja string (i.e. all $<something>
// occurrences are expected to be variables or $$) and returns either a *ninjaString
// if the string contains ninja variable references, or the original string and nil
// for the *ninjaString if it doesn't.
func parseNinjaOrSimpleString(scope scope, str string) (*ninjaString, string, error) {
	// naively pre-allocate slice by counting $ signs
	n := strings.Count(str, "$")
	if n == 0 {
		if len(str) > 0 && str[0] == ' ' {
			str = "$" + str
		}
		return nil, str, nil
	}
	variableReferences := make([]variableReference, 0, n)
	result := &ninjaString{
		str:       str,
		variables: &variableReferences,
	}

	parseState := &parseState{
		scope:  scope,
		str:    str,
		result: result,
	}

	state := parseFirstRuneState
	var err error
	for i := 0; i < len(str); i++ {
		r := rune(str[i])
		state, err = state(parseState, i, r)
		if err != nil {
			return nil, "", fmt.Errorf("error parsing ninja string %q: %s", str, err)
		}
	}

	_, err = state(parseState, len(parseState.str), eof)
	if err != nil {
		return nil, "", err
	}

	// All the '$' characters counted initially could have been "$$" escapes, leaving no
	// variable references.  Deallocate the variables slice if so.
	if len(*result.variables) == 0 {
		result.variables = nil
	}

	return result, "", nil
}

func parseFirstRuneState(state *parseState, i int, r rune) (stateFunc, error) {
	if r == ' ' {
		state.pushVariable(0, 1, nil)
	}
	return parseStringState(state, i, r)
}

func parseStringState(state *parseState, i int, r rune) (stateFunc, error) {
	switch {
	case r == '$':
		state.varStart = i
		return parseDollarStartState, nil

	case r == eof:
		return nil, nil

	default:
		return parseStringState, nil
	}
}

func parseDollarStartState(state *parseState, i int, r rune) (stateFunc, error) {
	switch {
	case r >= 'a' && r <= 'z', r >= 'A' && r <= 'Z',
		r >= '0' && r <= '9', r == '_', r == '-':
		// The beginning of a of the variable name.
		state.varNameStart = i
		return parseDollarState, nil

	case r == '$':
		// Just a "$$".  Go back to parseStringState.
		return parseStringState, nil

	case r == '{':
		// This is a bracketted variable name (e.g. "${blah.blah}").
		state.varNameStart = i + 1
		return parseBracketsState, nil

	case r == eof:
		return nil, fmt.Errorf("unexpected end of string after '$'")

	default:
		// This was some arbitrary character following a dollar sign,
		// which is not allowed.
		return nil, fmt.Errorf("invalid character after '$' at byte "+
			"offset %d", i)
	}
}

func parseDollarState(state *parseState, i int, r rune) (stateFunc, error) {
	switch {
	case r >= 'a' && r <= 'z', r >= 'A' && r <= 'Z',
		r >= '0' && r <= '9', r == '_', r == '-':
		// A part of the variable name.  Keep going.
		return parseDollarState, nil
	}

	// The variable name has ended, output what we have.
	v, err := state.scope.LookupVariable(state.str[state.varNameStart:i])
	if err != nil {
		return nil, err
	}

	state.pushVariable(state.varStart, i, v)

	switch {
	case r == '$':
		// A dollar after the variable name (e.g. "$blah$").  Start a new one.
		state.varStart = i
		return parseDollarStartState, nil

	case r == eof:
		return nil, nil

	default:
		return parseStringState, nil
	}
}

func parseBracketsState(state *parseState, i int, r rune) (stateFunc, error) {
	switch {
	case r >= 'a' && r <= 'z', r >= 'A' && r <= 'Z',
		r >= '0' && r <= '9', r == '_', r == '-', r == '.':
		// A part of the variable name.  Keep going.
		return parseBracketsState, nil

	case r == '}':
		if state.varNameStart == i {
			// The brackets were immediately closed.  That's no good.
			return nil, fmt.Errorf("empty variable name at byte offset %d",
				i)
		}

		// This is the end of the variable name.
		v, err := state.scope.LookupVariable(state.str[state.varNameStart:i])
		if err != nil {
			return nil, err
		}

		state.pushVariable(state.varStart, i+1, v)
		return parseStringState, nil

	case r == eof:
		return nil, fmt.Errorf("unexpected end of string in variable name")

	default:
		// This character isn't allowed in a variable name.
		return nil, fmt.Errorf("invalid character in variable name at "+
			"byte offset %d", i)
	}
}

// parseNinjaStrings converts a list of strings to *ninjaStrings by finding the references
// to ninja variables contained in the strings.
func parseNinjaStrings(scope scope, strs []string) ([]*ninjaString,
	error) {

	if len(strs) == 0 {
		return nil, nil
	}
	result := make([]*ninjaString, len(strs))
	for i, str := range strs {
		ninjaStr, err := parseNinjaString(scope, str)
		if err != nil {
			return nil, fmt.Errorf("error parsing element %d: %s", i, err)
		}
		result[i] = ninjaStr
	}
	return result, nil
}

// parseNinjaOrSimpleStrings splits a list of strings into *ninjaStrings if they have ninja
// variable references or a list of strings if they don't.  If none of the input strings contain
// ninja variable references (a very common case) then it returns the unmodified input slice as
// the output slice.
func parseNinjaOrSimpleStrings(scope scope, strs []string) ([]*ninjaString, []string, error) {
	if len(strs) == 0 {
		return nil, strs, nil
	}

	// allSimpleStrings is true until the first time a string with ninja variable references is found.
	allSimpleStrings := true
	var simpleStrings []string
	var ninjaStrings []*ninjaString

	for i, str := range strs {
		ninjaStr, simpleStr, err := parseNinjaOrSimpleString(scope, str)
		if err != nil {
			return nil, nil, fmt.Errorf("error parsing element %d: %s", i, err)
		} else if ninjaStr != nil {
			ninjaStrings = append(ninjaStrings, ninjaStr)
			if allSimpleStrings && i > 0 {
				// If all previous strings had no ninja variable references then they weren't copied into
				// simpleStrings to avoid allocating it if the input slice is reused as the output.  Allocate
				// simpleStrings and copy all the previous strings into it.
				simpleStrings = make([]string, i, len(strs))
				copy(simpleStrings, strs[:i])
			}
			allSimpleStrings = false
		} else {
			if !allSimpleStrings {
				// Only copy into the output slice if at least one string with ninja variable references
				// was found.  Skipped strings will be copied the first time a string with ninja variable
				// is found.
				simpleStrings = append(simpleStrings, simpleStr)
			}
		}
	}
	if allSimpleStrings {
		// None of the input strings had ninja variable references, return the input slice as the output.
		return nil, strs, nil
	}
	return ninjaStrings, simpleStrings, nil
}

func (n *ninjaString) Value(nameTracker *nameTracker) string {
	if n.variables == nil || len(*n.variables) == 0 {
		return defaultEscaper.Replace(n.str)
	}
	str := &strings.Builder{}
	n.ValueWithEscaper(str, nameTracker, defaultEscaper)
	return str.String()
}

func (n *ninjaString) ValueWithEscaper(w io.StringWriter, nameTracker *nameTracker, escaper *strings.Replacer) {

	if n.variables == nil || len(*n.variables) == 0 {
		w.WriteString(escaper.Replace(n.str))
		return
	}

	i := 0
	for _, v := range *n.variables {
		w.WriteString(escaper.Replace(n.str[i:v.start]))
		if v.variable == nil {
			w.WriteString("$ ")
		} else {
			w.WriteString("${")
			w.WriteString(nameTracker.Variable(v.variable))
			w.WriteString("}")
		}
		i = int(v.end)
	}
	w.WriteString(escaper.Replace(n.str[i:len(n.str)]))
}

func (n *ninjaString) Eval(variables map[Variable]*ninjaString) (string, error) {
	if n.variables == nil || len(*n.variables) == 0 {
		return n.str, nil
	}

	w := &strings.Builder{}
	i := 0
	for _, v := range *n.variables {
		w.WriteString(n.str[i:v.start])
		if v.variable == nil {
			w.WriteString(" ")
		} else {
			variable, ok := variables[v.variable]
			if !ok {
				return "", fmt.Errorf("no such global variable: %s", v.variable)
			}
			value, err := variable.Eval(variables)
			if err != nil {
				return "", err
			}
			w.WriteString(value)
		}
		i = int(v.end)
	}
	w.WriteString(n.str[i:len(n.str)])
	return w.String(), nil
}

func (n *ninjaString) Variables() []Variable {
	if n.variables == nil || len(*n.variables) == 0 {
		return nil
	}

	variables := make([]Variable, 0, len(*n.variables))
	for _, v := range *n.variables {
		if v.variable != nil {
			variables = append(variables, v.variable)
		}
	}
	return variables
}

func validateNinjaName(name string) error {
	for i, r := range name {
		valid := (r >= 'a' && r <= 'z') ||
			(r >= 'A' && r <= 'Z') ||
			(r >= '0' && r <= '9') ||
			(r == '_') ||
			(r == '-') ||
			(r == '.')
		if !valid {

			return fmt.Errorf("%q contains an invalid Ninja name character "+
				"%q at byte offset %d", name, r, i)
		}
	}
	return nil
}

func toNinjaName(name string) string {
	ret := bytes.Buffer{}
	ret.Grow(len(name))
	for _, r := range name {
		valid := (r >= 'a' && r <= 'z') ||
			(r >= 'A' && r <= 'Z') ||
			(r >= '0' && r <= '9') ||
			(r == '_') ||
			(r == '-') ||
			(r == '.')
		if valid {
			ret.WriteRune(r)
		} else {
			// TODO(jeffrygaston): do escaping so that toNinjaName won't ever output duplicate
			// names for two different input names
			ret.WriteRune('_')
		}
	}

	return ret.String()
}

var builtinRuleArgs = []string{"out", "in"}

func validateArgName(argName string) error {
	err := validateNinjaName(argName)
	if err != nil {
		return err
	}

	// We only allow globals within the rule's package to be used as rule
	// arguments.  A global in another package can always be mirrored into
	// the rule's package by defining a new variable, so this doesn't limit
	// what's possible.  This limitation prevents situations where a Build
	// invocation in another package must use the rule-defining package's
	// import name for a 3rd package in order to set the rule's arguments.
	if strings.ContainsRune(argName, '.') {
		return fmt.Errorf("%q contains a '.' character", argName)
	}

	if argName == "tags" {
		return fmt.Errorf("\"tags\" is a reserved argument name")
	}

	for _, builtin := range builtinRuleArgs {
		if argName == builtin {
			return fmt.Errorf("%q conflicts with Ninja built-in", argName)
		}
	}

	return nil
}

func validateArgNames(argNames []string) error {
	for _, argName := range argNames {
		err := validateArgName(argName)
		if err != nil {
			return err
		}
	}

	return nil
}

func ninjaStringsEqual(a, b *ninjaString) bool {
	return a.str == b.str &&
		(a.variables == nil) == (b.variables == nil) &&
		(a.variables == nil ||
			slices.Equal(*a.variables, *b.variables))
}

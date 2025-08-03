// Mostly copied from Go's src/cmd/gofmt:
// Copyright 2009 The Go Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.
package main

import (
	"bytes"
	"flag"
	"fmt"
	"io"
	"io/ioutil"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"syscall"
	"unicode"

	"github.com/google/blueprint/parser"
)

var (
	// main operation modes
	list               = flag.Bool("l", false, "list files that would be modified by bpmodify")
	write              = flag.Bool("w", false, "write result to (source) file instead of stdout")
	doDiff             = flag.Bool("d", false, "display diffs instead of rewriting files")
	sortLists          = flag.Bool("s", false, "sort touched lists, even if they were unsorted")
	targetedModules    = new(identSet)
	targetedProperties = new(qualifiedProperties)
	addIdents          = new(identSet)
	removeIdents       = new(identSet)
	removeProperty     = flag.Bool("remove-property", false, "remove the property")
	moveProperty       = flag.Bool("move-property", false, "moves contents of property into newLocation")
	newLocation        string
	setString          *string
	addLiteral         *string
	setBool            *string
	replaceProperty    = new(replacements)
)

func init() {
	flag.Var(targetedModules, "m", "comma or whitespace separated list of modules on which to operate")
	flag.Var(targetedProperties, "parameter", "alias to -property=`name1[,name2[,... […]")
	flag.StringVar(&newLocation, "new-location", "", " use with moveProperty to move contents of -property into a property with name -new-location ")
	flag.Var(targetedProperties, "property", "comma-separated list of fully qualified `name`s of properties to modify (default \"deps\")")
	flag.Var(addIdents, "a", "comma or whitespace separated list of identifiers to add")
	flag.Var(stringPtrFlag{&addLiteral}, "add-literal", "a literal to add to a list")
	flag.Var(removeIdents, "r", "comma or whitespace separated list of identifiers to remove")
	flag.Var(stringPtrFlag{&setString}, "str", "set a string property")
	flag.Var(replaceProperty, "replace-property", "property names to be replaced, in the form of oldName1=newName1,oldName2=newName2")
	flag.Var(stringPtrFlag{&setBool}, "set-bool", "a boolean value to set a property with (not a list)")
	flag.Usage = usage
}

var (
	exitCode = 0
)

func report(err error) {
	fmt.Fprintln(os.Stderr, err)
	exitCode = 2
}

func usage() {
	fmt.Fprintf(flag.CommandLine.Output(), "Usage: %s [flags] [path ...]\n", os.Args[0])
	flag.PrintDefaults()
}

// If in == nil, the source is the contents of the file with the given filename.
func processFile(filename string, in io.Reader, out io.Writer) error {
	if in == nil {
		f, err := os.Open(filename)
		if err != nil {
			return err
		}
		defer f.Close()
		if *write {
			syscall.Flock(int(f.Fd()), syscall.LOCK_EX)
		}
		in = f
	}
	src, err := ioutil.ReadAll(in)
	if err != nil {
		return err
	}
	r := bytes.NewBuffer(src)
	file, errs := parser.Parse(filename, r, parser.NewScope(nil))
	if len(errs) > 0 {
		for _, err := range errs {
			fmt.Fprintln(os.Stderr, err)
		}
		return fmt.Errorf("%d parsing errors", len(errs))
	}
	modified, errs := findModules(file)
	if len(errs) > 0 {
		for _, err := range errs {
			fmt.Fprintln(os.Stderr, err)
		}
		fmt.Fprintln(os.Stderr, "continuing...")
	}
	if modified {
		res, err := parser.Print(file)
		if err != nil {
			return err
		}
		if *list {
			fmt.Fprintln(out, filename)
		}
		if *write {
			err = ioutil.WriteFile(filename, res, 0644)
			if err != nil {
				return err
			}
		}
		if *doDiff {
			data, err := diff(src, res)
			if err != nil {
				return fmt.Errorf("computing diff: %s", err)
			}
			fmt.Printf("diff %s bpfmt/%s\n", filename, filename)
			out.Write(data)
		}
		if !*list && !*write && !*doDiff {
			_, err = out.Write(res)
		}
	}
	return err
}
func findModules(file *parser.File) (modified bool, errs []error) {
	for _, def := range file.Defs {
		if module, ok := def.(*parser.Module); ok {
			for _, prop := range module.Properties {
				if prop.Name == "name" && prop.Value.Type() == parser.StringType && targetedModule(prop.Value.Eval().(*parser.String).Value) {
					for _, p := range targetedProperties.properties {
						m, newErrs := processModuleProperty(module, prop.Name, file, p)
						errs = append(errs, newErrs...)
						modified = modified || m
					}
				}
			}
		}
	}
	return modified, errs
}

func processModuleProperty(module *parser.Module, moduleName string,
	file *parser.File, property qualifiedProperty) (modified bool, errs []error) {
	prop, parent, err := getRecursiveProperty(module, property.name(), property.prefixes())
	if err != nil {
		return false, []error{err}
	}
	if prop == nil {
		if len(addIdents.idents) > 0 || addLiteral != nil {
			// We are adding something to a non-existing list prop, so we need to create it first.
			prop, modified, err = createRecursiveProperty(module, property.name(), property.prefixes(), &parser.List{})
		} else if setString != nil {
			// We setting a non-existent string property, so we need to create it first.
			prop, modified, err = createRecursiveProperty(module, property.name(), property.prefixes(), &parser.String{})
		} else if setBool != nil {
			// We are setting a non-existent property, so we need to create it first.
			prop, modified, err = createRecursiveProperty(module, property.name(), property.prefixes(), &parser.Bool{})
		} else {
			// We cannot find an existing prop, and we aren't adding anything to the prop,
			// which means we must be removing something from a non-existing prop,
			// which means this is a noop.
			return false, nil
		}
		if err != nil {
			// Here should be unreachable, but still handle it for completeness.
			return false, []error{err}
		}
	} else if *removeProperty {
		// remove-property is used solely, so return here.
		return parent.RemoveProperty(prop.Name), nil
	} else if *moveProperty {
		return parent.MovePropertyContents(prop.Name, newLocation), nil
	}
	m, errs := processParameter(prop.Value, property.String(), moduleName, file)
	modified = modified || m
	return modified, errs
}
func getRecursiveProperty(module *parser.Module, name string, prefixes []string) (prop *parser.Property, parent *parser.Map, err error) {
	prop, parent, _, err = getOrCreateRecursiveProperty(module, name, prefixes, nil)
	return prop, parent, err
}
func createRecursiveProperty(module *parser.Module, name string, prefixes []string,
	empty parser.Expression) (prop *parser.Property, modified bool, err error) {
	prop, _, modified, err = getOrCreateRecursiveProperty(module, name, prefixes, empty)
	return prop, modified, err
}
func getOrCreateRecursiveProperty(module *parser.Module, name string, prefixes []string,
	empty parser.Expression) (prop *parser.Property, parent *parser.Map, modified bool, err error) {
	m := &module.Map
	for i, prefix := range prefixes {
		if prop, found := m.GetProperty(prefix); found {
			if mm, ok := prop.Value.Eval().(*parser.Map); ok {
				m = mm
			} else {
				// We've found a property in the AST and such property is not of type
				// *parser.Map, which must mean we didn't modify the AST.
				return nil, nil, false, fmt.Errorf("Expected property %q to be a map, found %s",
					strings.Join(prefixes[:i+1], "."), prop.Value.Type())
			}
		} else if empty != nil {
			mm := &parser.Map{}
			m.Properties = append(m.Properties, &parser.Property{Name: prefix, Value: mm})
			m = mm
			// We've created a new node in the AST. This means the m.GetProperty(name)
			// check after this for loop must fail, because the node we inserted is an
			// empty parser.Map, thus this function will return |modified| is true.
		} else {
			return nil, nil, false, nil
		}
	}
	if prop, found := m.GetProperty(name); found {
		// We've found a property in the AST, which must mean we didn't modify the AST.
		return prop, m, false, nil
	} else if empty != nil {
		prop = &parser.Property{Name: name, Value: empty}
		m.Properties = append(m.Properties, prop)
		return prop, m, true, nil
	} else {
		return nil, nil, false, nil
	}
}
func processParameter(value parser.Expression, paramName, moduleName string,
	file *parser.File) (modified bool, errs []error) {
	if _, ok := value.(*parser.Variable); ok {
		return false, []error{fmt.Errorf("parameter %s in module %s is a variable, unsupported",
			paramName, moduleName)}
	}
	if _, ok := value.(*parser.Operator); ok {
		return false, []error{fmt.Errorf("parameter %s in module %s is an expression, unsupported",
			paramName, moduleName)}
	}

	if (*replaceProperty).size() != 0 {
		if list, ok := value.Eval().(*parser.List); ok {
			return parser.ReplaceStringsInList(list, (*replaceProperty).oldNameToNewName), nil
		} else if str, ok := value.Eval().(*parser.String); ok {
			oldVal := str.Value
			replacementValue := (*replaceProperty).oldNameToNewName[oldVal]
			if replacementValue != "" {
				str.Value = replacementValue
				return true, nil
			} else {
				return false, nil
			}
		}
		return false, []error{fmt.Errorf("expected parameter %s in module %s to be a list or string, found %s",
			paramName, moduleName, value.Type().String())}
	}
	if len(addIdents.idents) > 0 || len(removeIdents.idents) > 0 {
		list, ok := value.(*parser.List)
		if !ok {
			return false, []error{fmt.Errorf("expected parameter %s in module %s to be list, found %s",
				paramName, moduleName, value.Type())}
		}
		wasSorted := parser.ListIsSorted(list)
		for _, a := range addIdents.idents {
			m := parser.AddStringToList(list, a)
			modified = modified || m
		}
		for _, r := range removeIdents.idents {
			m := parser.RemoveStringFromList(list, r)
			modified = modified || m
		}
		if (wasSorted || *sortLists) && modified {
			parser.SortList(file, list)
		}
	} else if addLiteral != nil {
		if *sortLists {
			return false, []error{fmt.Errorf("sorting not supported when adding a literal")}
		}
		list, ok := value.(*parser.List)
		if !ok {
			return false, []error{fmt.Errorf("expected parameter %s in module %s to be list, found %s",
				paramName, moduleName, value.Type().String())}
		}
		value, errs := parser.ParseExpression(strings.NewReader(*addLiteral))
		if errs != nil {
			return false, errs
		}
		list.Values = append(list.Values, value)
		modified = true
	} else if setBool != nil {
		res, ok := value.(*parser.Bool)
		if !ok {
			return false, []error{fmt.Errorf("expected parameter %s in module %s to be bool, found %s",
				paramName, moduleName, value.Type().String())}
		}
		if *setBool == "true" {
			res.Value = true
		} else if *setBool == "false" {
			res.Value = false
		} else {
			return false, []error{fmt.Errorf("expected parameter %s to be true or false, found %s",
				paramName, *setBool)}
		}
		modified = true
	} else if setString != nil {
		str, ok := value.(*parser.String)
		if !ok {
			return false, []error{fmt.Errorf("expected parameter %s in module %s to be string, found %s",
				paramName, moduleName, value.Type().String())}
		}
		str.Value = *setString
		modified = true
	}
	return modified, nil
}
func targetedModule(name string) bool {
	if targetedModules.all {
		return true
	}
	for _, m := range targetedModules.idents {
		if m == name {
			return true
		}
	}
	return false
}
func visitFile(path string, f os.FileInfo, err error) error {
	//TODO(dacek): figure out a better way to target intended .bp files without parsing errors
	if err == nil && (f.Name() == "Blueprints" || strings.HasSuffix(f.Name(), ".bp")) {
		err = processFile(path, nil, os.Stdout)
	}
	if err != nil {
		report(err)
	}
	return nil
}
func walkDir(path string) {
	filepath.Walk(path, visitFile)
}
func main() {
	defer func() {
		if err := recover(); err != nil {
			report(fmt.Errorf("error: %s", err))
		}
		os.Exit(exitCode)
	}()
	flag.Parse()

	if len(targetedProperties.properties) == 0 && *moveProperty {
		report(fmt.Errorf("-move-property must specify property"))
		return
	}

	if len(targetedProperties.properties) == 0 {
		targetedProperties.Set("deps")
	}
	if flag.NArg() == 0 {
		if *write {
			report(fmt.Errorf("error: cannot use -w with standard input"))
			return
		}
		if err := processFile("<standard input>", os.Stdin, os.Stdout); err != nil {
			report(err)
		}
		return
	}
	if len(targetedModules.idents) == 0 {
		report(fmt.Errorf("-m parameter is required"))
		return
	}

	if len(addIdents.idents) == 0 && len(removeIdents.idents) == 0 && setString == nil && addLiteral == nil && !*removeProperty && !*moveProperty && (*replaceProperty).size() == 0 && setBool == nil {
		report(fmt.Errorf("-a, -add-literal, -r, -remove-property, -move-property, replace-property or -str parameter is required"))
		return
	}
	if *removeProperty && (len(addIdents.idents) > 0 || len(removeIdents.idents) > 0 || setString != nil || addLiteral != nil || (*replaceProperty).size() > 0) {
		report(fmt.Errorf("-remove-property cannot be used with other parameter(s)"))
		return
	}
	if *moveProperty && (len(addIdents.idents) > 0 || len(removeIdents.idents) > 0 || setString != nil || addLiteral != nil || (*replaceProperty).size() > 0) {
		report(fmt.Errorf("-move-property cannot be used with other parameter(s)"))
		return
	}
	if *moveProperty && newLocation == "" {
		report(fmt.Errorf("-move-property must specify -new-location"))
		return
	}
	for i := 0; i < flag.NArg(); i++ {
		path := flag.Arg(i)
		switch dir, err := os.Stat(path); {
		case err != nil:
			report(err)
		case dir.IsDir():
			walkDir(path)
		default:
			if err := processFile(path, nil, os.Stdout); err != nil {
				report(err)
			}
		}
	}
}

func diff(b1, b2 []byte) (data []byte, err error) {
	f1, err := ioutil.TempFile("", "bpfmt")
	if err != nil {
		return
	}
	defer os.Remove(f1.Name())
	defer f1.Close()
	f2, err := ioutil.TempFile("", "bpfmt")
	if err != nil {
		return
	}
	defer os.Remove(f2.Name())
	defer f2.Close()
	f1.Write(b1)
	f2.Write(b2)
	data, err = exec.Command("diff", "-uw", f1.Name(), f2.Name()).CombinedOutput()
	if len(data) > 0 {
		// diff exits with a non-zero status when the files don't match.
		// Ignore that failure as long as we get output.
		err = nil
	}
	return
}

type stringPtrFlag struct {
	s **string
}

func (f stringPtrFlag) Set(s string) error {
	*f.s = &s
	return nil
}
func (f stringPtrFlag) String() string {
	if f.s == nil || *f.s == nil {
		return ""
	}
	return **f.s
}

type replacements struct {
	oldNameToNewName map[string]string
}

func (m *replacements) String() string {
	ret := ""
	sep := ""
	for k, v := range m.oldNameToNewName {
		ret += sep
		ret += k
		ret += ":"
		ret += v
		sep = ","
	}
	return ret
}

func (m *replacements) Set(s string) error {
	usedNames := make(map[string]struct{})

	pairs := strings.Split(s, ",")
	length := len(pairs)
	m.oldNameToNewName = make(map[string]string)
	for i := 0; i < length; i++ {

		pair := strings.SplitN(pairs[i], "=", 2)
		if len(pair) != 2 {
			return fmt.Errorf("Invalid replacement pair %s", pairs[i])
		}
		oldName := pair[0]
		newName := pair[1]
		if _, seen := usedNames[oldName]; seen {
			return fmt.Errorf("Duplicated replacement name %s", oldName)
		}
		if _, seen := usedNames[newName]; seen {
			return fmt.Errorf("Duplicated replacement name %s", newName)
		}
		usedNames[oldName] = struct{}{}
		usedNames[newName] = struct{}{}
		m.oldNameToNewName[oldName] = newName
	}
	return nil
}

func (m *replacements) Get() interface{} {
	//TODO(dacek): Remove Get() method from interface as it seems unused.
	return m.oldNameToNewName
}

func (m *replacements) size() (length int) {
	return len(m.oldNameToNewName)
}

type identSet struct {
	idents []string
	all    bool
}

func (m *identSet) String() string {
	return strings.Join(m.idents, ",")
}
func (m *identSet) Set(s string) error {
	m.idents = strings.FieldsFunc(s, func(c rune) bool {
		return unicode.IsSpace(c) || c == ','
	})
	if len(m.idents) == 1 && m.idents[0] == "*" {
		m.all = true
	}
	return nil
}
func (m *identSet) Get() interface{} {
	return m.idents
}

type qualifiedProperties struct {
	properties []qualifiedProperty
}

type qualifiedProperty struct {
	parts []string
}

var _ flag.Getter = (*qualifiedProperties)(nil)

func (p *qualifiedProperty) name() string {
	return p.parts[len(p.parts)-1]
}
func (p *qualifiedProperty) prefixes() []string {
	return p.parts[:len(p.parts)-1]
}
func (p *qualifiedProperty) String() string {
	return strings.Join(p.parts, ".")
}

func parseQualifiedProperty(s string) (*qualifiedProperty, error) {
	parts := strings.Split(s, ".")
	if len(parts) == 0 {
		return nil, fmt.Errorf("%q is not a valid property name", s)
	}
	for _, part := range parts {
		if part == "" {
			return nil, fmt.Errorf("%q is not a valid property name", s)
		}
	}
	prop := qualifiedProperty{parts}
	return &prop, nil

}

func (p *qualifiedProperties) Set(s string) error {
	properties := strings.Split(s, ",")
	if len(properties) == 0 {
		return fmt.Errorf("%q is not a valid property name", s)
	}

	p.properties = make([]qualifiedProperty, len(properties))
	for i := 0; i < len(properties); i++ {
		tmp, err := parseQualifiedProperty(properties[i])
		if err != nil {
			return err
		}
		p.properties[i] = *tmp
	}
	return nil
}

func (p *qualifiedProperties) String() string {
	arrayLength := len(p.properties)
	props := make([]string, arrayLength)
	for i := 0; i < len(p.properties); i++ {
		props[i] = p.properties[i].String()
	}
	return strings.Join(props, ",")
}
func (p *qualifiedProperties) Get() interface{} {
	return p.properties
}

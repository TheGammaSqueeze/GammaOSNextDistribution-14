package main

import (
	"bytes"
	"flag"
	"fmt"
	"io"
	"io/ioutil"
	"os"
	"path/filepath"
	"strings"

	"github.com/google/blueprint/parser"
)

var (
	result	= make(map[string]string)
	defaults = make(map[string]string)
	Root = ""
)

var (
	exitCode = 0
)

func report(err error) {
	fmt.Fprintln(os.Stderr, err)
	exitCode = 2
}

func usage() {
	usageViolation("")
}

func usageViolation(violation string) {
	fmt.Fprintln(os.Stderr, violation)
	fmt.Fprintln(os.Stderr, "usage: fuzzparser [flags] [path ...]")
	flag.PrintDefaults()
	os.Exit(2)
}

func processFile(filename string, out io.Writer) error {
	f, err := os.Open(filename)
	if err != nil {
		return err
	}
	defer f.Close()

	return processReader(filename, f, out)
}

func processReader(filename string, in io.Reader, out io.Writer) error {
	src, err := ioutil.ReadAll(in)
	if err != nil {
		return err
	}

	r := bytes.NewBuffer(src)
	file, errs := parser.ParseAndEval(filename, r, parser.NewScope(nil))

	modules := findModules(file)
	// First collect all the defaults into a dictionary
	for _, mod := range modules {
	    if mod.Type == "cc_defaults" {
	    	default_name := ""
	    	for _, prop := range mod.Map.Properties {
				if prop.Name == "name" {
					value := prop.Value.String()
					default_name = value[1:strings.Index(value, "@")-1]
				} else if prop.Name == "static_libs" || prop.Name == "shared_libs" {
					value := prop.Value.String()
					for strings.Index(value, "\"") > -1 {
						value = value[strings.Index(value, "\"")+1:]
						lib_name := value[:strings.Index(value, "\"")]
						if _ , ok := defaults[default_name]; ok {
							defaults[default_name] += "," + lib_name
						} else {
							defaults[default_name] += lib_name
						}
						value = value[strings.Index(value, "\"")+1:]
					}
				} else if prop.Name == "defaults" {
					// Get the defaults of the default
					value := prop.Value.String()
					for strings.Index(value, "\"") > -1 {
						value = value[strings.Index(value, "\"")+1:]
						sub_default_name := value[:strings.Index(value, "\"")]
						if _ , ok := defaults[default_name]; ok {
							defaults[default_name] += "," + defaults[sub_default_name]
						} else {
							defaults[default_name] += defaults[sub_default_name]
						}
						value = value[strings.Index(value, "\"")+1:]
					}
				} else if prop.Name == "target" {
					value := prop.Value.String()
					if default_name == "binder_fuzz_defaults" {
						fmt.Printf("---> target value for %s: %s\n", default_name ,value)
					}
					for strings.Index(value, "\"") > -1 {
						value = value[strings.Index(value, "\"")+1:]
						lib := value[:strings.Index(value, "\"")]
						if _ , ok := defaults[default_name]; ok {
							defaults[default_name] += "," + lib
						} else {
							defaults[default_name] += lib
						}
						value = value[strings.Index(value, "\"")+1:]
					}
				}
			}
		}
    }

	for _, mod := range modules {
		if mod.Type == "cc_fuzz" {
			fuzzer_name := ""
			for _, prop := range mod.Map.Properties {
				// First get the name of the fuzzer
				if prop.Name == "name" {
					value := prop.Value.String()
					fuzzer_name = value[1:strings.Index(value, "@")-1]
				} else if prop.Name == "defaults" {
					value := prop.Value.String()
					if strings.Index(value, "@") == 0 {
						value = value[1:]
					}
					default_name := value[strings.Index(value, "[")+2: strings.Index(value, "@")-1]
					if _, ok := result[fuzzer_name]; ok {
						result[fuzzer_name] += "," + defaults[default_name]
					} else {
						result[fuzzer_name] += defaults[default_name]
					}
				} else if prop.Name == "static_libs" || prop.Name == "shared_libs" {
					value := prop.Value.String()
					for strings.Index(value, "\"") > -1 {
						value = value[strings.Index(value, "\"")+1:]
						lib_name := value[:strings.Index(value, "\"")]
						if _ , ok := result[fuzzer_name]; ok {
							result[fuzzer_name] += "," + lib_name
						} else {
							result[fuzzer_name] += lib_name
						}
						value = value[strings.Index(value, "\"")+1:]
					}
				}
			}
	    }
	}

	if len(errs) > 0 {
		for _, err := range errs {
			fmt.Fprintln(os.Stderr, err)
		}
		return fmt.Errorf("%d parsing errors", len(errs))
	}

    return err
}

func findModules(file *parser.File) (modules []*parser.Module) {
    if file != nil {
		for _, def := range file.Defs {
			if module, ok := def.(*parser.Module); ok {
				modules = append(modules, module)
			}
		}
	}
	return modules
}

func walkDir(path string) {
	visitFile := func(path string, f os.FileInfo, err error) error {
		if err == nil && f.Name() == "Android.bp" {
			err = processFile(path, os.Stdout)
		}
		if err != nil {
			fmt.Printf("ERROR")
			report(err)
		}
		return nil
	}
    fmt.Printf("Parsing %s recursively...\n", path)
	filepath.Walk(path, visitFile)
}

func main() {
	flag.Usage = usage
	flag.Parse()

	for i := 0; i < flag.NArg(); i++ {
		Root := flag.Arg(i)
		fmt.Printf("Root %s\n", Root)
		switch dir, err := os.Stat(Root); {
		case err != nil:
			report(err)
		case dir.IsDir():
			walkDir(Root)
		default:
			if err := processFile(Root, os.Stdout); err != nil {
				report(err)
			}
		}
	}

	fmt.Printf("-------------------------------------\n")
	fmt.Printf("Fuzzer name -------Library name------\n")
	if len(result) > 0 {
		for k, v := range result {
			if len(v) == 0 {
				v = "NOT FOUND"
			}
			fmt.Printf("%s:%s\n", k, v)
		}
	}

	os.Exit(exitCode)
}

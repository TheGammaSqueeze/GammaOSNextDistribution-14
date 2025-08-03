package main

// elfdiff compares two ELF files. Each one can be a standalone file or an archive (.a file)
// member.
import (
	"android/bazel/mkcompare"
	"bytes"
	"debug/elf"
	"flag"
	"fmt"
	"io"
	"os"
	"sort"
	"strconv"
	"strings"
)

type myElf struct {
	*elf.File
	path           string
	sectionsByName map[string]*elf.Section
}

func always(_ string) bool {
	return true
}

func processArgs() {
	flag.Parse()
	if len(flag.Args()) != 2 {
		maybeQuit(fmt.Errorf("usage: %s REF-ELF OUR-ELF\n", os.Args[0]))
		os.Exit(1)
	}
}

func maybeQuit(err error) {
	if err == nil {
		return
	}

	fmt.Fprintln(os.Stderr, err)
	os.Exit(1)
}

func main() {
	processArgs()
	elfRef := elfRead(flag.Arg(0))
	elfOur := elfRead(flag.Arg(1))
	missing, common, extra := mkcompare.Classify(elfRef.sectionsByName, elfOur.sectionsByName, always)
	var hasDiff bool
	newDifference := func() {
		if !hasDiff {
			hasDiff = true
		}
	}

	if len(missing)+len(extra) > 0 {
		newDifference()
	}
	if len(missing) > 0 {
		sort.Strings(missing)
		fmt.Print("Missing sections:\n  ", strings.Join(missing, "\n  "), "\n")
	}
	if len(extra) > 0 {
		sort.Strings(extra)
		fmt.Print("Extra sections:\n  ", strings.Join(extra, "\n  "), "\n")
	}
	commonDiff := false
	newCommonDifference := func(format string, args ...interface{}) {
		if !commonDiff {
			fmt.Print("Sections that differ:\n")
			commonDiff = true
		}
		newDifference()
		fmt.Printf(format, args...)
	}
	sort.Strings(common)
	for _, sname := range common {
		sectionRef := elfRef.sectionsByName[sname]
		sectionOur := elfOur.sectionsByName[sname]
		refSize := int64(sectionRef.Size)
		ourSize := int64(sectionOur.Size)
		if refSize != ourSize {
			newCommonDifference("    %s:%d%+d\n", sname, refSize, ourSize-refSize)
			continue
		}
		dataOur, err := sectionOur.Data()
		maybeQuit(err)
		dataRef, err := sectionRef.Data()
		maybeQuit(err)
		if bytes.Compare(dataRef, dataOur) != 0 {
			newCommonDifference("    %s:%d(data)\n", sname, refSize)
		}
	}

	if hasDiff {
		os.Exit(1)
	}
}

const arMagic = "!<arch>\n"
const arExtendedEntry = "//"

// elfRead returns ELF file reader for URI. If URI has <path>(<member>) format,
// <path> is an archive (usually an .a file) and <member> is an ELF file in it.
func elfRead(path string) *myElf {
	var reader io.ReaderAt
	var err error
	n := strings.LastIndex(path, "(")
	if n > 0 && strings.HasSuffix(path, ")") {
		reader = newArchiveReader(path[0:n], path[n+1:len(path)-1])
	} else {
		reader, err = os.Open(path)
		maybeQuit(err)
	}
	res := &myElf{path: path}
	res.File, err = elf.NewFile(reader)
	maybeQuit(err)

	// Build ELF sections map. Only allocatable sections are considered.
	res.sectionsByName = make(map[string]*elf.Section)
	for _, s := range res.File.Sections {
		if _, ok := res.sectionsByName[s.Name]; ok {
			fmt.Fprintf(os.Stderr, "%s: duplicate section %s, ignoring\n", res.path, s.Name)
			continue
		}
		if s.Flags&elf.SHF_ALLOC != 0 && s.Type != elf.SHT_NOBITS {
			res.sectionsByName[s.Name] = s
		}
	}
	return res
}

type memberHeader []byte

const headerSize = 60

// memberHeader represents a member in an archive. It implements os.ReaderAt interface
// so it can be passed to elf.NewFile
type memberReader struct {
	file  *os.File
	start int64
	size  int64
}

func (m memberReader) ReadAt(p []byte, off int64) (n int, err error) {
	nToRead := int64(len(p))
	nHas := m.size - off
	if nHas <= 0 {
		return 0, io.EOF
	}
	if nToRead > nHas {
		nToRead = nHas
	}
	return m.file.ReadAt(p[0:nToRead], m.start+off)
}

func (h memberHeader) memberSize() int64 {
	n, err := strconv.ParseInt(strings.TrimSpace(string(h[48:58])), 10, 64)
	maybeQuit(err)
	return (n + 1) & -2 // The size is always an even number
}

// newArchiveReader returns a reader for an archive member.
// The format of the ar archive is sort of documented in Wikipedia:
// https://en.wikipedia.org/wiki/Ar_(Unix)
func newArchiveReader(path string, member string) io.ReaderAt {
	f, err := os.Open(path)
	maybeQuit(err)
	fStat, err := f.Stat()
	maybeQuit(err)
	fileSize := fStat.Size()

	var nextHeaderPos int64 = 8
	var contentPos int64
	var header memberHeader = make([]byte, headerSize)

	// fill the buffer, reading from given position.
	readFully := func(buf []byte, at int64) {
		n, err := f.ReadAt(buf, at)
		maybeQuit(err)
		if n < len(buf) {
			maybeQuit(fmt.Errorf("%s is corrupt, read %d bytes instead of %d\n", path, n, len(buf)))
		}
	}
	// Read the header, update contents and next header pointers
	readHeader := func() {
		readFully(header, nextHeaderPos)
		contentPos = nextHeaderPos + headerSize
		nextHeaderPos = contentPos + header.memberSize()
	}

	// Read the file header
	buf := make([]byte, len(arMagic))
	readFully(buf, 0)
	if bytes.Compare([]byte(arMagic), buf) != 0 {
		maybeQuit(fmt.Errorf("%s is not an ar archive\n", path))
	}

	entry := []byte(member + "/") // `/` is member name sentinel
	if len(entry) <= 16 {
		// the name fits into a section header, so just scan the sections.
		for nextHeaderPos < fileSize {
			readHeader()
			if bytes.Compare(entry, header[0:len(entry)]) == 0 {
				return &memberReader{f, contentPos, header.memberSize()}
			}
		}
	} else {
		// If section's name is `/` followed by digits, these digits are an offset to
		// its real name in the 'extended names' section.
		// The name of the extended names section is `//`, and it should precede the
		// sections with longer names.
		var extendedNames []byte
		for nextHeaderPos < fileSize {
			readHeader()
			if bytes.Compare(header[0:2], []byte(arExtendedEntry)) == 0 {
				extendedNames = make([]byte, header.memberSize())
				readFully(extendedNames, contentPos)
			} else if bytes.Compare(header[0:1], []byte("/")) != 0 {
				continue
			}
			if off, err := strconv.ParseInt(strings.TrimSpace(string(header[1:16])), 10, 64); err == nil {
				// A section with extended name.
				if extendedNames == nil {
					maybeQuit(fmt.Errorf("%s: extended names entry is missing in archive\n", path))
				}
				if off+int64(len(entry)) <= int64(len(extendedNames)) &&
					bytes.Compare(entry, extendedNames[off:off+int64(len(entry))]) == 0 {
					return &memberReader{f, contentPos, header.memberSize()}
				}
			}
		}
	}
	maybeQuit(fmt.Errorf("%s: no such member %s", path, member))
	return nil
}

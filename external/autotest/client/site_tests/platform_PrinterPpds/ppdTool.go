// Copyright 2020 The Chromium OS Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// ppdTool is a command line tool that can:
//  * download all PPD files from the database kept on the SCS server;
//  * cluster given set of PPD files and return a minimal subset of PPDs that
//    represents resultant clusters. This is useful for choosing a subset of
//    PPD files for testing.
//
// The tool can be run with the command:
//    go run ppdTool.go
// Use -h parameter to print some help and list of accepted parameters.
//
// The tool can be also compiled to the binary file with the following command:
//    go build pdfTool.go

package main

import (
	"bufio"
	"bytes"
	"compress/gzip"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"io/ioutil"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strings"
	"sync"
)

// downloadFile starts to download the content from given url with HTTP GET. It
// returns a reader to the content. In case of an error the function terminates
// the program.
func downloadFile(url string) io.ReadCloser {
	response, err := http.Get(url)
	if err != nil {
		log.Fatalf("Cannot HTTP GET the file %s: %s.\n", url, err)
	}
	if response.StatusCode != 200 {
		response.Body.Close()
		log.Fatalf("HTTP GET for the file %s returned status code %d.\n", url, response.StatusCode)
	}
	return response.Body
}

// downloadFilenamesFromPPDIndex retrieves from the index a list of all PPD
// files. Returned PPD filenames are sorted and unique. In case of an error
// the function terminates the program.
func downloadFilenamesFromPPDIndex() []string {
	const urlMetadata = "https://printerconfigurations.googleusercontent.com/chromeos_printing/metadata_v3/"

	output := make(map[string]bool)
	for i := 0; i < 20; i++ {
		// Calculate a URL of the index file.
		urlPPDIndex := fmt.Sprintf("%sindex-%02d.json", urlMetadata, i)
		// Download and parse the index file.
		respBody := downloadFile(urlPPDIndex)
		defer respBody.Close()
		body, err := ioutil.ReadAll(respBody)
		if err != nil {
			log.Fatalf("Cannot read the content of %s: %s.\n", urlPPDIndex, err)
		}
		// Parse the json structure and extract PPD filenames.
		type jsonName struct {
			Name string `json:"name"`
		}
		type jsonMetadata struct {
			PPDMetadata []jsonName `json:"ppdMetadata"`
		}
		type jsonPrinters struct {
			PPDIndex map[string]jsonMetadata `json:"ppdIndex"`
		}
		var data jsonPrinters
		if err = json.Unmarshal(body, &data); err != nil {
			log.Fatalf("Cannot parse the content of %s: %s.\n", urlPPDIndex, err)
		}
		for _, entry := range data.PPDIndex {
			for _, element := range entry.PPDMetadata {
				output[element.Name] = true
			}
		}
	}

	// Sort filenames.
	results := make([]string, 0, len(output))
	for filename := range output {
		results = append(results, filename)
	}
	sort.Strings(results)

	return results
}

// listFilenamesFromDirectory returns a list of filenames from the given
// directory. In case of an error the function terminates the program.
func listFilenamesFromDirectory(path string) []string {
	files, err := ioutil.ReadDir(path)
	if err != nil {
		log.Fatalf("Cannot open the directory %s: %s.\n", path, err)
	}
	filenames := make([]string, 0, len(files))
	for _, file := range files {
		if !file.IsDir() {
			filenames = append(filenames, file.Name())
		}
	}
	return filenames
}

// Statement represents a single statement from a PPD file.
type Statement struct {
	keyword string
	option  string
	value   string
}

// PPD represents a content of a single PPD file as an array of Statements.
// The field name holds the filename of the PPD file while the field
// originalDataSize holds the initial size of the field data.
type PPD struct {
	name             string
	data             []Statement
	originalDataSize int
}

var reComment = regexp.MustCompile(`^\*[ \t]*%`)
var reKeywordOptionValue = regexp.MustCompile(`^\*[ \t]*([^: \t]+)([ \t]+[^:]+)?[ \t]*:[ \t]*([^ \t].*)?$`)
var reKeywordOnly = regexp.MustCompile(`^\*[ \t]*([^: \t]+)[ \t]*$`)
var reEmptyLine = regexp.MustCompile(`^[ \t]*$`)

// parseLine parses a single line from PPD file. The line is supposed to be the
// first line of statement's definition. If the line contains white characters
// only or is a comment the function returns empty Statement (st.keyword == "")
// and finish with success (ok == true).
func parseLine(line string) (st Statement, ok bool) {
	if reComment.MatchString(line) {
		return st, true
	}
	if m := reKeywordOptionValue.FindStringSubmatch(line); m != nil {
		st.keyword = m[1]
		st.option = m[2]
		st.value = m[3]
		return st, true
	}
	if m := reKeywordOnly.FindStringSubmatch(line); m != nil {
		st.keyword = m[1]
		return st, true
	}
	if reEmptyLine.MatchString(line) {
		return st, true
	}
	return st, false
}

// ParsePPD parses a content of a PPD file. The parameter name is the filename
// of the PPD file (the source of the content).
func ParsePPD(name string, content []byte) (PPD, error) {
	ppd := PPD{name: name, data: make([]Statement, 0, 512)}
	scanner := bufio.NewScanner(bytes.NewReader(content))
	var multilineValue = false
	for lineNo := 1; scanner.Scan(); lineNo++ {
		line := scanner.Text()
		if multilineValue {
			// We are inside a multiline value.
			ppd.data[len(ppd.data)-1].value += "\n" + line
			// Check for closing ".
			multilineValue = (strings.Count(line, "\"")%2 == 0)
			continue
		}

		st, ok := parseLine(line)
		if !ok {
			return ppd, fmt.Errorf("Cannot parse line %d: %s", lineNo, line)
		}
		if st.keyword == "" {
			// A comment or an empty line.
			continue
		}
		ppd.data = append(ppd.data, st)
		// Check for unmatched " in the value.
		multilineValue = (strings.Count(st.value, "\"")%2 != 0)
	}
	ppd.originalDataSize = len(ppd.data)
	return ppd, scanner.Err()
}

var reWhiteSpaces = regexp.MustCompile(`[ \t]+`)

// normalizeSpacesAndTabs normalizes subsequences of spaces and tabulators in
// the given string. All leading and trailing spaces and tabs are removed.
// Every subsequence consisting of spaces and tabulators is replaced by a
// single space.
func normalizeSpacesAndTabs(str *string) {
	*str = strings.TrimSpace(*str)
	*str = reWhiteSpaces.ReplaceAllString(*str, " ")
}

var keywordsToRemove = map[string]bool{
	"1284DeviceID":           true,
	"cupsLanguages":          true,
	"cupsVersion":            true,
	"DefaultDocCutType":      true,
	"DefaultInstalledMemory": true,
	"DefaultPageCutType":     true,
	"DocCutType":             true,
	"driverUrl":              true,
	"End":                    true,
	"FileVersion":            true,
	"FoomaticIDs":            true,
	"InstalledMemory":        true,
	"Manufacturer":           true,
	"ModelName":              true,
	"NickName":               true,
	"PageCutType":            true,
	"PCFileName":             true,
	"Product":                true,
	"ShortNickName":          true,
	"Throughput":             true}

var shortLang = regexp.MustCompile(`^[a-z][a-z]\.`)
var longLang = regexp.MustCompile(`^[a-z][a-z]_[A-Za-z][A-Za-z]\.`)

// normalizePPD processes the given PPD content to make it suitable for
// comparison with other PPDs. The PPD may be no longer valid after this
// transformation. The following operations are performed on the PPD:
// * all statements with keyword included in the global variable
//   keywordsToRemove are removed;
// * all statements with keyword with prefix matching ^[a-z][a-z]\. or
//   ^[a-z][a-z]_[A-Za-z][A-Za-z]\. are removed (like *pl.MediaType,
//	 *de.Translation, *fr_CA.Translation, *zh_TW.MediaType, etc.);
// * subsequences of white spaces in all statements are normalized with
//   the use of normalizeSpacesAndTabs(...)
func normalizePPD(ppd *PPD) {
	newData := make([]Statement, 0, len(ppd.data))
	for _, s := range ppd.data {
		if keywordsToRemove[s.keyword] {
			continue
		}
		if shortLang.MatchString(s.keyword) || longLang.MatchString(s.keyword) {
			continue
		}
		normalizeSpacesAndTabs(&s.option)
		normalizeSpacesAndTabs(&s.value)
		newData = append(newData, s)
	}
	ppd.data = newData
}

// parseAndNormalizePPDFile reads the content of a PPD file from the given
// reader and parses it. The content is also normalized with the normalizePPD
// function. In case of an error the function terminates the program.
func parseAndNormalizePPDFile(reader io.ReadCloser, filename string) PPD {
	// Decompress the content if needed.
	if strings.HasSuffix(filename, ".gz") {
		defer reader.Close()
		decomp, err := gzip.NewReader(reader)
		if err != nil {
			log.Fatalf("Error when decompressing the file %s: %s.\n", filename, err)
		}
		reader = decomp
	}
	defer reader.Close()
	content, err := ioutil.ReadAll(reader)
	if err != nil {
		log.Fatalf("Error when reading a content of the file %s: %s.\n", filename, err)
	}
	ppd, err := ParsePPD(filename, content)
	if err != nil {
		log.Fatalf("Error when parsing a content of the file %s: %s.\n", filename, err)
	}
	normalizePPD(&ppd)
	return ppd
}

// checkNotExists terminates the program when the given path exists.
func checkNotExists(path string) {
	_, err := os.Stat(path)
	if os.IsNotExist(err) {
		return
	}
	if err == nil {
		log.Fatal("File or directory '" + path + "' already exists.")
	}
	log.Fatalf("Cannot access '%s': %s.\n", path, err)
}

// divideIntoLargeClusters divides the input set of PPDs into clusters of PPDs
// with the same content (data). The output slice contains the resultant
// clusters saved as a list of PPD names.
func divideIntoLargeClusters(ppds []PPD) [][]string {
	type ppdTypeDefinition struct {
		cupsFilter             string
		cupsModelNumber        string
		cupsPreFilter          string
		driverName             string
		driverType             string
		foomaticRIPCommandLine string
	}

	groups := make(map[ppdTypeDefinition][]int)
	for iPPD, ppd := range ppds {
		chosenKeywords := make(map[string][]string)
		for _, st := range ppd.data {
			switch st.keyword {
			case "cupsFilter", "cupsFilter2", "cupsModelNumber", "cupsPreFilter", "FoomaticRIPCommandLine":
				chosenKeywords[st.keyword] = append(chosenKeywords[st.keyword], st.value)
			case "driverName", "driverType":
				chosenKeywords[st.keyword] = append(chosenKeywords[st.keyword], st.option)
			}
		}
		if values, ok := chosenKeywords["cupsFilter2"]; ok {
			chosenKeywords["cupsFilter"] = values
			delete(chosenKeywords, "cupsFilter2")
		}
		var hash ppdTypeDefinition
		for keyword, values := range chosenKeywords {
			sort.Slice(values, func(i, j int) bool { return values[i] < values[j] })
			switch keyword {
			case "cupsFilter":
				hash.cupsFilter = strings.Join(values, " | ")
			case "cupsModelNumber":
				hash.cupsModelNumber = strings.Join(values, " | ")
			case "cupsPreFilter":
				hash.cupsPreFilter = strings.Join(values, " | ")
			case "driverName":
				hash.driverName = strings.Join(values, " | ")
			case "driverType":
				hash.driverType = strings.Join(values, " | ")
			case "FoomaticRIPCommandLine":
				hash.foomaticRIPCommandLine = strings.Join(values, " | ")
			}
		}
		groups[hash] = append(groups[hash], iPPD)
	}

	// Sort every group by originalDataSize(decreasing), name(alphabetically).
	for _, ppdIDs := range groups {
		sort.Slice(ppdIDs, func(i, j int) bool {
			p1 := ppdIDs[i]
			p2 := ppdIDs[j]
			if ppds[p1].originalDataSize == ppds[p2].originalDataSize {
				return ppds[p1].name < ppds[p2].name
			}
			return ppds[p1].originalDataSize > ppds[p2].originalDataSize
		})
	}

	// Convert groups to a slice of slices with names.
	groupsSlice := make([][]string, 0, len(groups))
	for _, group := range groups {
		names := make([]string, len(group))
		for i, iPPD := range group {
			names[i] = ppds[iPPD].name
		}
		groupsSlice = append(groupsSlice, names)
	}

	sort.Slice(groupsSlice, func(i, j int) bool {
		return groupsSlice[i][0] < groupsSlice[j][0]
	})

	return groupsSlice
}

// compareSameSizePPDs is a helper function for divideIntoSmallClusters. It
// divides the set of PPDs into clusters of PPDs with the same data. The input
// PPDs must have the same size of data field. The function returns resultant
// clusters as slices with PPDs names.
func compareSameSizePPDs(ppds []PPD) [][]string {
	// This map holds PPDID->groupID. At the beginning, every PPD is assigned
	// to a one-element group.
	ppdsGroups := make([]int, len(ppds))
	for i := range ppdsGroups {
		ppdsGroups[i] = i
	}

	// Find PPDs with the same data and assign them to the same group.
	for i1, e1 := range ppds {
		if ppdsGroups[i1] != i1 {
			// This PPD was already assigned.
			continue
		}
		for i2 := i1 + 1; i2 < len(ppds); i2++ {
			e2 := ppds[i2]
			if ppdsGroups[i2] != i2 {
				// This PPD was already assigned.
				continue
			}
			// Compare data.
			match := true
			for ip, s1 := range e1.data {
				s2 := e2.data[ip]
				if s1 != s2 {
					match = false
					break
				}
			}
			if match {
				// Assign i2 to the same group as i1.
				ppdsGroups[i2] = i1
			}
		}
	}

	// This map contains groupID->[]PPDID.
	groups := make(map[int][]int)
	for iPPD, iGroup := range ppdsGroups {
		groups[iGroup] = append(groups[iGroup], iPPD)
	}
	// Sort every group by originalDataSize(decreasing), name(alphabetically).
	for _, ppdIDs := range groups {
		sort.Slice(ppdIDs, func(i, j int) bool {
			p1 := ppdIDs[i]
			p2 := ppdIDs[j]
			if ppds[p1].originalDataSize == ppds[p2].originalDataSize {
				return ppds[p1].name < ppds[p2].name
			}
			return ppds[p1].originalDataSize > ppds[p2].originalDataSize
		})
	}

	// Convert groups to a slice of slices with names.
	groupsSlice := make([][]string, 0, len(groups))
	for _, group := range groups {
		names := make([]string, len(group))
		for i, iPPD := range group {
			names[i] = ppds[iPPD].name
		}
		groupsSlice = append(groupsSlice, names)
	}

	return groupsSlice
}

// divideIntoSmallClusters divides the input set of PPDs into clusters of PPDs
// with the same content (data). The output slice contains the resultant
// clusters saved as a list of PPD names.
func divideIntoSmallClusters(ppds []PPD) [][]string {

	type ppdHash struct {
		dataSize        int
		firstStatement  Statement
		middleStatement Statement
		lastStatement   Statement
	}

	ppdsByHash := make(map[ppdHash][]PPD)
	for _, ppd := range ppds {
		var hash ppdHash
		hash.dataSize = len(ppd.data)
		hash.firstStatement = ppd.data[0]
		hash.middleStatement = ppd.data[len(ppd.data)/2]
		hash.lastStatement = ppd.data[len(ppd.data)-1]
		ppdsByHash[hash] = append(ppdsByHash[hash], ppd)
	}

	chGroups := make(chan [][]string, len(ppdsByHash))
	for _, ppdsToCompare := range ppdsByHash {
		go func(ppdsToCompare []PPD) {
			chGroups <- compareSameSizePPDs(ppdsToCompare)
		}(ppdsToCompare)
	}
	var groups [][]string
	for range ppdsByHash {
		groups = append(groups, <-chGroups...)
	}
	close(chGroups)

	sort.Slice(groups, func(i, j int) bool {
		return groups[i][0] < groups[j][0]
	})

	return groups
}

// saveClustersToFile creates a new file at given path and saves there the
// given list of clusters. In case of any error the function terminates the
// program.
func saveClustersToFile(clusters [][]string, path string) {
	file, err := os.Create(path)
	if err != nil {
		log.Fatalf("Cannot create a file %s: %s.\n", path, err)
	}
	defer file.Close()
	for _, cluster := range clusters {
		file.WriteString(strings.Join(cluster, "\t"))
		file.WriteString("\n")
	}
}

// createDirectoryWithPPDs creates directory given in the parameter pathTrg and
// copies there the given set of files from the directory defined in pathSrc.
// In case of any error the function terminates the program.
func createDirectoryWithPPDs(pathSrc string, filenames []string, pathTrg string) {
	if err := os.MkdirAll(pathTrg, 0755); err != nil {
		log.Fatalf("Cannot create a directory '%s': %s.\n", pathTrg, err)
	}
	for _, filename := range filenames {
		src := filepath.Join(pathSrc, filename)
		trg := filepath.Join(pathTrg, filename)
		if err := os.Link(src, trg); err != nil {
			log.Fatalf("Cannot create a hard link %s for the file %s: %s.\n", trg, src, err)
		}
	}
}

func commandCompare(args []string) {
	const filenameLargeClusters = "large_clusters.txt"
	const filenameSmallClusters = "small_clusters.txt"
	const dirnameCorePPDs = "ppds_core"
	const dirnameExtPPDs = "ppds_ext"

	flags := flag.NewFlagSet("compare", flag.ExitOnError)
	flagInput := flags.String("input", "ppds_all", "Directory with PPD files.")
	flagOutput := flags.String("output", ".", "Directory to save results. It is created if not exists.")
	flags.Parse(args)

	if len(flags.Args()) > 0 {
		log.Fatal("Unknown parameter. Run with -h or --help to see the list of supported parameters.")
	}

	pathLargeClusters := filepath.Join(*flagOutput, filenameLargeClusters)
	pathSmallClusters := filepath.Join(*flagOutput, filenameSmallClusters)
	pathCorePPDs := filepath.Join(*flagOutput, dirnameCorePPDs)
	pathExtPPDs := filepath.Join(*flagOutput, dirnameExtPPDs)

	checkNotExists(pathLargeClusters)
	checkNotExists(pathSmallClusters)
	checkNotExists(pathCorePPDs)
	checkNotExists(pathExtPPDs)

	fmt.Println("Reading a list of PPD files from the directory...")
	filenames := listFilenamesFromDirectory(*flagInput)
	fmt.Printf("Found %d files.\n", len(filenames))

	fmt.Println("Processing all files...")
	ppds := make([]PPD, len(filenames))
	var wg sync.WaitGroup
	for i, filename := range filenames {
		wg.Add(1)
		go func(i int, filename string) {
			defer wg.Done()
			path := filepath.Join(*flagInput, filename)
			reader, err := os.Open(path)
			if err != nil {
				log.Fatalf("Cannot open the file %s: %s.\n", path, err)
			}
			ppds[i] = parseAndNormalizePPDFile(reader, filename)
		}(i, filename)
	}
	wg.Wait()
	fmt.Println("Done.")

	fmt.Println("Calculating small clusters...")
	groupsSmall := divideIntoSmallClusters(ppds)
	fmt.Printf("Done. The number of small clusters: %d.\n", len(groupsSmall))

	fmt.Println("Calculating large clusters...")
	groupsLarge := divideIntoLargeClusters(ppds)
	fmt.Printf("Done. The number of large clusters: %d.\n", len(groupsLarge))

	filenamesCore := make([]string, 0, len(groupsLarge))
	setFilenameCore := make(map[string]bool)
	for _, group := range groupsLarge {
		filenamesCore = append(filenamesCore, group[0])
		setFilenameCore[group[0]] = true
	}
	filenamesExt := make([]string, 0, len(groupsSmall))
	for _, group := range groupsSmall {
		if !setFilenameCore[group[0]] {
			filenamesExt = append(filenamesExt, group[0])
		}
	}

	// Save results.
	createDirectoryWithPPDs(*flagInput, filenamesCore, pathCorePPDs)
	createDirectoryWithPPDs(*flagInput, filenamesExt, pathExtPPDs)
	saveClustersToFile(groupsSmall, pathSmallClusters)
	saveClustersToFile(groupsLarge, pathLargeClusters)
}

func commandDownload(args []string) {
	const urlPPD = "https://printerconfigurations.googleusercontent.com/chromeos_printing/ppds_for_metadata_v3/"
	const maxNumberOfParallelDownloads = 4

	flags := flag.NewFlagSet("download", flag.ExitOnError)
	flagOutput := flags.String("output", "ppds_all", "Directory to save PPD files, it cannot exist.")
	flags.Parse(args)

	if len(flags.Args()) > 0 {
		log.Fatal("Unknown parameter. Run with -h or --help to see the list of supported parameters.")
	}
	checkNotExists(*flagOutput)
	if err := os.MkdirAll(*flagOutput, 0755); err != nil {
		log.Fatalf("Cannot create a directory '%s': %s.\n", *flagOutput, err)
	}

	fmt.Println("Downloading a list of PPD files from the index...")
	filenames := downloadFilenamesFromPPDIndex()
	fmt.Printf("Found %d files.\n", len(filenames))

	fmt.Println("Downloading PPD files...")
	chFilenames := make(chan string)
	var wgEnd sync.WaitGroup
	for i := 0; i < maxNumberOfParallelDownloads; i++ {
		wgEnd.Add(1)
		go func() {
			defer wgEnd.Done()
			for filename := range chFilenames {
				reader := downloadFile(urlPPD + filename)
				path := filepath.Join(*flagOutput, filename)
				file, err := os.Create(path)
				if err != nil {
					log.Fatalf("Cannot create file %s on the disk: %s.\n", path, err)
				}
				if _, err = io.Copy(file, reader); err != nil {
					log.Fatalf("Cannot copy the content of the file %s: %s.\n", path, err)
				}
				reader.Close()
				file.Close()
			}
		}()
	}
	for _, filename := range filenames {
		chFilenames <- filename
	}
	close(chFilenames)
	wgEnd.Wait()
	fmt.Println("Done")
}

const usageText = `
The first parameter must be one of the following commands:

  download - downloads all PPDs from the index to the given directory.

  compare - perform two independent clusterizations on the given set of PPD
      files. Two sets of clusters are calculated:
       * a set of large clusters where PPD are grouped together by pipeline
           types;
       * a set of small clusters where PPD are grouped together by their
           similarity.
      For both results a minimal subsets of representative PPDs are calculated.
      In the output directory, the following files and directories are created:
       * large_clusters.txt - a file with PPD names grouped in large clusters
       * small_clusters.txt - a file with PPD names grouped in small clusters
       * ppds_core - a directory with hard links to PPD files representing
           large clusters, each cluster is represented by exactly one PPD file.
           For the full PPD dataset given on the input, this directory is
           supposed to have around ~100 PPD files;
       * ppds_ext - a directory with hard links to PPD files representing
           small clusters, each cluster is represented by exactly one PPD file.
           IF A PPD FILE IS ALREADY PRESENT IN core_ppds IT IS OMITTED. For the
           full PPD dataset given on the input, this directory is supposed to
           have around ~1500 PPD files minus ~100 PPD files already present in
           the core_ppd directory.

Run one of the commands with '-h' or '--help' to get a list of parameters.
`

func main() {
	if len(os.Args) < 2 {
		fmt.Println(usageText)
		return
	}

	switch os.Args[1] {
	case "compare":
		commandCompare(os.Args[2:])
	case "download":
		commandDownload(os.Args[2:])
	default:
		fmt.Println(usageText)
	}
}

#include <fuzzer/FuzzedDataProvider.h>
#include <libxml/parser.h>
#include <cstdio>
#include <unistd.h>
#include <fcntl.h>

extern "C" int LLVMFuzzerTestOneInput(const uint8_t *data, size_t size) {
    FuzzedDataProvider stream(data, size);

    // Initialize the XML parser
    xmlInitParser();

    // Create and initialize parser context
    xmlParserCtxtPtr ctxt = xmlNewParserCtxt();
    if (ctxt == nullptr) {
        return 0;
    }

    // Create a temporary file
    std::unique_ptr<std::FILE, decltype(&fclose)> fp(tmpfile(), &fclose);
    if (!fp) {
        xmlFreeParserCtxt(ctxt);
        return 0;
    }

    // Write fuzzed data to the temporary file
    fwrite(data, 1, size, fp.get());
    fflush(fp.get());
    rewind(fp.get());

    // Get the file descriptor from the FILE object
    int fd = fileno(fp.get());

    // Generate other fuzzed inputs
    std::string URL = stream.ConsumeRandomLengthString();
    std::string encoding = stream.ConsumeRandomLengthString();
    int options = stream.ConsumeIntegral<int>();

    // Call the function under test
    xmlDocPtr doc = xmlCtxtReadFd(ctxt, fd, URL.c_str(), encoding.c_str(), options);

    // Cleanup
    if (doc != nullptr) {
        xmlFreeDoc(doc);
    }
    xmlFreeParserCtxt(ctxt);
    xmlCleanupParser();

    return 0;
}

#include <fuzzer/FuzzedDataProvider.h>
#include <string>
#include <libxml/parser.h>


std::string GenerateRandomXML(FuzzedDataProvider& stream) {
    std::string xml = "<root>";
    int numberOfElements = stream.ConsumeIntegralInRange<int>(1, 10);

    for (int i = 0; i < numberOfElements; ++i) {
        std::string elementName = stream.ConsumeRandomLengthString(10); // Limiting name length to 10
        xml += "<" + elementName + ">";

        if (stream.ConsumeBool()) {
            std::string textContent = stream.ConsumeRandomLengthString(20); // Limiting text content length
            xml += textContent;
        }

        int numberOfAttributes = stream.ConsumeIntegralInRange<int>(0, 5);
        for (int j = 0; j < numberOfAttributes; ++j) {
            std::string attributeName = stream.ConsumeRandomLengthString(10); // Limiting attribute name length
            std::string attributeValue = stream.ConsumeRandomLengthString(20); // Limiting attribute value length
            xml += " " + attributeName + "=\"" + attributeValue + "\"";
        }

        xml += "</" + elementName + ">";
    }

    xml += "</root>";
    return xml;
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t *data, size_t size) {
    FuzzedDataProvider stream(data, size);

    // Initialize the XML parser
    xmlInitParser();

    // Create and initialize parser context
    xmlParserCtxtPtr ctxt = xmlNewParserCtxt();
    if (ctxt == nullptr) {
        // If cannot allocate context, early return
        return 0;
    }

    // Generate fuzzed inputs
    std::string buffer = GenerateRandomXML(stream);
    int bufferSize = buffer.length();
    std::string URL = stream.ConsumeRandomLengthString();
    std::string encoding = stream.ConsumeRandomLengthString();
    int options = stream.ConsumeIntegral<int>();

    // Call the function under test
    xmlDocPtr doc = xmlCtxtReadMemory(ctxt, buffer.data(), bufferSize, URL.c_str(), encoding.c_str(), options);

    // Cleanup
    if (doc != nullptr) {
        xmlFreeDoc(doc);
    }
    xmlFreeParserCtxt(ctxt);
    xmlCleanupParser();

    return 0;
}

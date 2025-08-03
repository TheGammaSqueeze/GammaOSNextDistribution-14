#include <fuzzer/FuzzedDataProvider.h>
#include <tinyxml2.h>
#include <string>
#include <vector>

struct XMLElement {
    std::string name;
    std::vector<std::pair<std::string, std::string>> attributes;
    std::vector<XMLElement> children;
    std::string textContent;
};

std::string serializeXML(const XMLElement& element) {
    std::string xml = "<" + element.name;

    // Add attributes
    for (const auto& attr : element.attributes) {
        xml += " " + attr.first + "=\"" + attr.second + "\"";
    }

    xml += ">";

    // Add text content
    xml += element.textContent;

    // Add child elements (recursively)
    for (const auto& child : element.children) {
        xml += serializeXML(child);
    }

    xml += "</" + element.name + ">";
    return xml;
}

void GenerateXML(FuzzedDataProvider* stream, XMLElement* element, int maxDepth = 3) {
    element->name = stream->ConsumeRandomLengthString(20);

    int numAttributes = stream->ConsumeIntegralInRange<int>(0, 5);
    for (int i = 0; i < numAttributes; ++i) {
    element->attributes.push_back({
      stream->ConsumeRandomLengthString(15), // Attribute name
      stream->ConsumeRandomLengthString(30)  // Attribute value
    });
    }

    int numChildren = stream->ConsumeIntegralInRange<int>(0, 3);
    for (int i = 0; i < numChildren; ++i) {
        XMLElement child;

        // Recursive generation for nested elements
        if (maxDepth > 0) {
            GenerateXML(stream, &child, maxDepth - 1);
        } else {
            // Populate text content at the leaves
            child.textContent = stream->ConsumeRandomLengthString(30);
        }

        element->children.push_back(child);
    }
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t *data, size_t size) {
    FuzzedDataProvider stream(data, size);

    XMLElement rootElement;
    GenerateXML(&stream, &rootElement);

    // Convert the generated XMLElement structure into an XML string
    std::string xmlString = serializeXML(rootElement);

    tinyxml2::XMLDocument doc;
    doc.Parse(xmlString.c_str(), xmlString.length());

    return 0;
}

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList

import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

import static org.gradle.api.internal.lambdas.SerializableLambdas.spec;

/**
 * Gradle task to update sources link in sdk
 */
class SdkSourceUpdaterTask extends DefaultTask  {

    private static final JDK_TABLE_PATH = "out/gradle/AndroidStudio/config/options/jdk.table.xml"
    private static final JAVA_CORE_PATH = "frameworks/base/core/java"
    private static final JAVA_GRAPHICS_PATH = "frameworks/base/graphics/java"

    @Input
    String androidRoot

    public SdkSourceUpdaterTask() {
        setOnlyIf("Sdk file is missing", spec(task -> new File(androidRoot, JDK_TABLE_PATH).exists()))
        outputs.upToDateWhen {
            String sdkDefLines = new File(androidRoot, JDK_TABLE_PATH).text
            String corePath = new File(androidRoot, JAVA_CORE_PATH).getCanonicalPath()
            String graphicsPath = new File(androidRoot, JAVA_GRAPHICS_PATH).getCanonicalPath()
            return sdkDefLines.contains(corePath) && sdkDefLines.contains(graphicsPath)
        }
    }

    @OutputFile
    public File getOutputFile() {
        return new File(androidRoot, JDK_TABLE_PATH)
    }

    @TaskAction
    void execute() throws Exception {
        File sdkDef = new File(androidRoot, JDK_TABLE_PATH)
        if (!sdkDef.exists()) {
            throw new IllegalStateException("Sdk config file not found at " + sdkDef);
        }

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(sdkDef);

        NodeList list = doc.getElementsByTagName("jdk");
        for (int i = 0; i < list.getLength(); i++) {
            Node node = list.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                Element homePath = findFirstElement(element, "homePath");
                if (homePath == null) {
                    continue;
                }

                String pathValue = homePath.getAttribute("value");
                if (pathValue == null || pathValue.isBlank()) {
                    continue;
                }


                if (!pathValue.contains("out/gradle/MockSdk")) {
                    continue;
                }

                // Found the right SDK
                Element sourcePath = findFirstElement(element, "sourcePath");
                if (sourcePath == null) {
                    // TODO: Add source path
                    continue;
                }

                while (sourcePath.hasChildNodes())
                    sourcePath.removeChild(sourcePath.getFirstChild());

                // Create root
                Element el = createRoot(doc, "type", "composite");
                sourcePath.appendChild(el);

                // Create paths
                el.appendChild(createRoot(doc, "type", "simple", "url", "file://" + new File(androidRoot, JAVA_CORE_PATH).getCanonicalPath()));
                el.appendChild(createRoot(doc, "type", "simple", "url", "file://" + new File(androidRoot, JAVA_GRAPHICS_PATH).getCanonicalPath()));
            }
        }

        // Write the xml
        TransformerFactory.newInstance().newTransformer()
                .transform(new DOMSource(doc), new StreamResult(sdkDef))

        System.out.println("======================================")
        System.out.println("======================================")
        System.out.println("       Android sources linked")
        System.out.println("Restart IDE for changes to take effect")
        System.out.println("======================================")
        System.out.println("======================================")
    }

    private Element createRoot(Document doc, String... attrs) {
        Element el = doc.createElement("root");
        for (int i = 0; i < attrs.length; i += 2) {
            el.setAttribute(attrs[i], attrs[i + 1]);
        }
        return el;
    }

    private Element findFirstElement(Element node, String tag) {
        NodeList paths = node.getElementsByTagName(tag);
        if (paths.getLength() < 1) {
            return null;
        }
        Node n = paths.item(0);
        if (n.getNodeType() != Node.ELEMENT_NODE) {
            return null;
        }
        return (Element) n;
    }
}
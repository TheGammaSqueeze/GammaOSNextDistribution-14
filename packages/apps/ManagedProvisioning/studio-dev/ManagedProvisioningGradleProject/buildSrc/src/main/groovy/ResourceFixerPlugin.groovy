import com.android.build.api.variant.AndroidComponentsExtension
import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.w3c.dom.Document
import org.w3c.dom.Element

import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Utility plugin to fix some common resource issues with gradle:
 *  - add support for androidprv attributes
 */
class ResourceFixerPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {

        def extension = project.getExtensions().getByType(AndroidComponentsExtension.class);
        def allVariants = []

        def androidTop = project.extensions.extraProperties.get("ANDROID_TOP");
        String buildTools = project.extensions.extraProperties.get("BUILD_TOOLS_VERSION")
        buildTools = buildTools.replace(' ', '-')
        def aapt = "$androidTop/out/gradle/MockSdk/build-tools/$buildTools/aapt2"

        // Store all variant names
        extension.onVariants(extension.selector().all(), variant -> {
            allVariants.add(variant.name)
            allVariants.add("${variant.name}AndroidTest")
            allVariants.add("${variant.name}UnitTest")
        })

        // After the project is evaluated, update the mergeResource task
        project.afterEvaluate {
            allVariants.forEach(variant -> {
                def taskName = "merge${variant.capitalize()}Resources";
                def mergeTask = project.tasks.findByName(taskName);
                if (mergeTask == null) {
                    System.out.println("Task not found " + taskName);
                    return
                }
                mergeTask.doLast {
                    processResources(
                            new File(project.buildDir, "intermediates/incremental/${variant}/${taskName}/merged.dir"),
                            new File(project.buildDir, "intermediates/merged_res/${variant}"),
                            new File(aapt))
                }
            })
        }
    }

    void processResources(File xmlDir, File outputDir, File aapt) {
        for (File values: xmlDir.listFiles()) {
            if (values.getName().startsWith("values") && values.isDirectory()) {
                for (File xml : values.listFiles()) {
                    if (xml.isFile() && xml.getName().endsWith(".xml")
                            && xml.getText().contains("androidprv:")) {
                        processAndroidPrv(xml, outputDir, aapt);
                    }
                }
            }
        }
    }

    private void processAndroidPrv(File xmlFile, File outputDir, File aapt) {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(xmlFile);

        Element root = doc.getDocumentElement();
        if (root.hasAttribute("xmlns:androidprv")) {
            // This file is already processed
            System.out.println("Skipping " + xmlFile.absolutePath);
            return
        }
        root.setAttribute("xmlns:androidprv", "http://schemas.android.com/apk/prv/res/android");

        // Update the file
        TransformerFactory.newInstance().newTransformer()
                .transform(new DOMSource(doc), new StreamResult(xmlFile))

        // recompile
        String command = aapt.getAbsolutePath() +
                " compile " +
                xmlFile.getAbsolutePath() +
                " -o " +
                outputDir.getAbsolutePath()
        def proc = command.execute()
        def sout = new StringBuilder(), serr = new StringBuilder()
        proc.consumeProcessOutput(sout, serr)
        proc.waitForOrKill(5000)
        System.out.println("Processed " + xmlFile.absolutePath + "  " + sout + "  " + serr);
    }
}

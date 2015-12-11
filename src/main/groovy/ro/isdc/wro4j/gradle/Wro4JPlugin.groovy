package ro.isdc.wro4j.gradle

import org.apache.commons.lang.StringUtils
import org.gradle.api.GradleException
import org.gradle.api.Plugin;
import org.gradle.api.Project
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.file.RelativePath
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.SourceSet
import ro.isdc.wro4j.extensions.CssUrlUnrootPostProcessor

class Wro4JPlugin implements Plugin<Project> {
    private Copy processWebResources
    private Copy processWebTestResources

    @Override
    public void apply(Project project) {
        def javaConvention = project.convention.findPlugin(JavaPluginConvention)
        if (javaConvention == null) {
            throw new GradleException("wro4j requires java plugin to be applied first")
        }

        def webResources = project.extensions.create(WebResourceSet.NAME, WebResourceSet, project)
        project.configurations.create("webjars")
        project.configurations.create("webjarsTest")

        processWebResources = project.tasks.create("processWebResources", Copy)
        processWebTestResources = project.tasks.create("processWebTestResources", Copy)

        project.tasks
                .getByName("classes")
                .dependsOn processWebResources

        project.tasks
                .getByName("testClasses")
                .dependsOn processWebTestResources

        project.afterEvaluate {
            configureTasks(webResources, project, javaConvention)
        }
    }

    private void configureTasks(WebResourceSet webResources, Project project, JavaPluginConvention javaConvention) {
        def srcMain = javaConvention.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);

        def model = webResources.createWroModel()
        def srcMainDir = webResources.srcMainDir
        def srcTestDir = webResources.srcTestDir
        def buildMainDir = webResources.buildMainDir
        def buildTestDir = webResources.buildTestDir
        def dstDir = new File(srcMain.output.resourcesDir, webResources.staticFolder)

        buildMainDir.mkdirs()
        buildTestDir.mkdirs()

        /* Configure processWebResources task */
        def webjars = project.configurations.getByName("webjars")
        def prepareAssets = project.tasks.create("prepareAssets", Copy)
        prepareAssets.with {
            from srcMainDir
            from (webjars.collect { project.zipTree(it) }) {
                eachFile { unwrapWebjar(it) }
            }
            into buildMainDir
        }
        processWebResources.dependsOn prepareAssets
        project.configurations.getByName("runtime").extendsFrom(webjars)

        webResources.bundles.each { bundle ->
            def compileWeb = project.tasks.create(nameFor("compileWeb", bundle.name), WebCompileTask)
            compileWeb.with {
                wroModel = model
                targetGroups = [bundle.name]
                preProcessors = bundle.preProcessors
                postProcessors = bundle.postProcessors
                if (bundle.hasCss) {
                    postProcessors.add(CssUrlUnrootPostProcessor.ALIAS)
                }
                configProperties = bundle.configProperties
                sourcesDir = buildMainDir
                outputDir = dstDir

                mustRunAfter prepareAssets
            }
            processWebResources.dependsOn compileWeb
        }

        processWebResources.with {
            from new File(buildMainDir, webResources.staticFolder)
            into dstDir
        }
        if (webResources.mainAssets != null) {
            processWebResources.with webResources.mainAssets.from(buildMainDir)
        }
        /* end of processWebResources task */

        /* Configure processWebTestResources task */
        def webjarsTest = project.configurations.getByName("webjarsTest")
        processWebTestResources.with {
            from (webjarsTest.collect { project.zipTree(it) }) {
                eachFile { unwrapWebjar(it) }
            }
            into buildTestDir

            dependsOn prepareAssets
        }
        if (webResources.testAssets != null) {
            processWebTestResources.with webResources.testAssets.from(srcTestDir)
        } else {
            processWebTestResources.from(srcTestDir)
        }
        /* end of processWebTestResources task */
    }

    private static String nameFor(String prefix, String specName) {
        def name = new StringBuilder(prefix)
        specName.split("\\.|-|_").each { word ->
            name.append(StringUtils.capitalize(word))
        }
        return name.toString()
    }

    private static void unwrapWebjar(FileCopyDetails file) {
        def segments = file.relativePath.segments;
        def index = segments.findIndexOf { StringUtils.equalsIgnoreCase(it, "webjars") }
        if (index > 0) {
            file.relativePath = new RelativePath(
                    file.relativePath.isFile(),
                    Arrays.copyOfRange(segments, index, segments.length)
            )
        }
    }
}

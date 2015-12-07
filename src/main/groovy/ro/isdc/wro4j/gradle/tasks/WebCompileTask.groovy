package ro.isdc.wro4j.gradle.tasks
import org.apache.commons.io.IOUtils
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*
import org.mockito.Mockito
import ro.isdc.wro.config.Context
import ro.isdc.wro.config.jmx.WroConfiguration
import ro.isdc.wro.http.support.DelegatingServletOutputStream
import ro.isdc.wro.manager.WroManager
import ro.isdc.wro.model.resource.ResourceType
import ro.isdc.wro.model.resource.locator.factory.ConfigurableLocatorFactory
import ro.isdc.wro.model.resource.processor.factory.ConfigurableProcessorsFactory
import ro.isdc.wro.util.io.UnclosableBufferedInputStream
import ro.isdc.wro4j.gradle.EmbeddedWroManagerFactory

import javax.servlet.FilterConfig
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

public class WebCompileTask extends DefaultTask {
    private File wroFile
    private Set<String> targetGroups = []
    private List<String> uriLocators = ["servletContext", "classpath"]
    private List<String> preProcessors = []
    private List<String> postProcessors = []
    private File sourcesDir;
    private File outputDir;

    WebCompileTask() {
    }

    @InputFile
    File getWroFile() {
        return wroFile != null ? wroFile : (wroFile = new File(project.projectDir, "wro.xml"))
    }

    void setWroFile(File file) {
        wroFile = file
    }

    @Input
    Set<String> getTargetGroups() {
        return targetGroups
    }

    void setTargetGroup(Set<String> groups) {
        targetGroups = groups
    }

    void targetGroup(String group) {
        targetGroups += group
    }

    @Input
    List<String> getUriLocators() {
        return uriLocators
    }

    void setUriLocators(List<String> locators) {
        uriLocators = locators
    }

    void uriLocator(String locator) {
        uriLocators += locator
    }

    @Input
    List<String> getPreProcessors() {
        return preProcessors
    }

    void setPreProcessors(List<String> pre) {
        preProcessors = pre
    }

    void preProcessor(String pre) {
        preProcessors += pre
    }

    @Input
    List<String> getPostProcessors() {
        return postProcessors
    }

    void setPostProcessors(List<String> post) {
        postProcessors = post
    }

    void postProcessor(String post) {
        postProcessors += post
    }

    @InputDirectory
    File getSourcesDir() {
        return sourcesDir
    }

    void setSourcesDir(File src) {
        sourcesDir = src
    }

    @OutputDirectory
    File getOutputDir() {
        return outputDir
    }

    void setOutputDir(File dst) {
        outputDir = dst
    }

    @TaskAction
    private void compile() {
        for (def group: targetGroups) {
            for (def resourceType: ResourceType.values()) {
                def groupWithExt = group + "." + resourceType.name().toLowerCase()
                processGroup(groupWithExt)
            }
        }
    }

    private WroManager createWroManager() {
        def configProps = createConfigProperties()
        def factory = new EmbeddedWroManagerFactory(wroFile, configProps)

        return factory.create()
    }

    private Properties createConfigProperties() {
        def props = new Properties()

        props.setProperty(ConfigurableLocatorFactory.PARAM_URI_LOCATORS, uriLocators.join(","));
        props.setProperty(ConfigurableProcessorsFactory.PARAM_PRE_PROCESSORS, preProcessors.join(","))
        props.setProperty(ConfigurableProcessorsFactory.PARAM_POST_PROCESSORS, postProcessors.join(","))

        return props
    }

    private void processGroup(String group) {
        getLogger().info("Processing group '{}'...", group)

        def requestUrl = new StringBuffer()
        requestUrl.append(sourcesDir.toURI().toURL())

        def request = Mockito.mock(HttpServletRequest)
        Mockito.when(request.getContextPath()).thenReturn(".")
        Mockito.when(request.getServletPath()).thenReturn("")
        Mockito.when(request.getRequestURI()).thenReturn(group)
        Mockito.when(request.getRequestURL()).thenReturn(requestUrl)

        def output = new ByteArrayOutputStream()
        def servletOutput = new DelegatingServletOutputStream(output)
        def response = Mockito.mock(HttpServletResponse)
        Mockito.when(response.getOutputStream()).thenReturn(servletOutput)

        def filterConfig = Mockito.mock(FilterConfig)

        def config = new WroConfiguration()
        // plugin should ignore empty groups, since it will try to process all types of resources
        config.setIgnoreEmptyGroup(true)

        def ctx = Context.webContext(request, response, filterConfig)
        ctx.aggregatedFolderPath = ""

        Context.set(ctx, config)
        try {
            getLogger().debug("  initiating WroManager")
            def wroManager = createWroManager()

            getLogger().debug("  applying pre- and post-processors")
            wroManager.process()

            if (output.size() == 0) {
                getLogger().info("There is no content generated. Skipping empty group.")
            } else {
                def input = new UnclosableBufferedInputStream(output.toByteArray())
                def stampedName = wroManager.getNamingStrategy().rename(group, input)
                def destinationFile = new File(outputDir, stampedName)

                outputDir.mkdirs()

                new FileOutputStream(destinationFile).withStream {
                    input.reset()
                    IOUtils.copy(input, it)
                };

                getLogger().info("There are {}KB generated into '{}'.", output.size() / 1024, destinationFile)
            }
        } finally {
            Context.unset()
        }
    }
}

package ro.isdc.wro4j.gradle

import org.apache.commons.io.FilenameUtils
import org.apache.commons.lang.StringUtils
import org.gradle.api.Project
import org.gradle.api.file.CopySpec
import ro.isdc.wro.model.WroModel

class WebResourceSet {
    private static final String STATIC = "static"
    static final String NAME = "webResources"

    private final Project project
    private final Map<String, WebBundle> bundles = new HashMap<>()
    private CopySpec mainAssets
    private CopySpec testAssets
    private File srcMainDir
    private File srcTestDir
    private String staticFolder
    private File buildMainDir
    private File buildTestDir

    WebResourceSet(Project project) {
        this.project = project
        this.srcMainDir = project.file("src/main/webResources")
        this.srcTestDir = project.file("src/test/webResources")
        this.staticFolder = STATIC
        this.buildMainDir = new File(project.buildDir, "wro")
        this.buildTestDir = new File(project.buildDir, "wroTest")
    }

    /**
     * Main sources directory. Default 'src/main/webResources'
     *
     * @return main sources dir
     */
    File getSrcMainDir() {
        return srcMainDir
    }

    void setSrcMainDir(File srcMainDir) {
        this.srcMainDir = srcMainDir
    }

    /**
     * Test sources directory. Default 'src/test/webResources'
     *
     * @return test sources dir
     */
    File getSrcTestDir() {
        return srcTestDir
    }

    void setSrcTestDir(File srcTestDir) {
        this.srcTestDir = srcTestDir
    }

    /**
     * Folder name for webapp static content. Default 'static'.
     *
     * @return folder for static content
     */
    String getStaticFolder() {
        return staticFolder
    }

    void setStaticFolder(String staticFolder) {
        this.staticFolder = staticFolder
    }

    /**
     * Intermediate build directory for main sources.
     *
     * @return intermediate build directory for main sources
     */
    File getBuildMainDir() {
        return buildMainDir
    }

    /**
     * Intermediate build directory for test sources.
     *
     * @return intermediate build directory for test sources
     */
    File getBuildTestDir() {
        return buildTestDir
    }

    String getBuildMainUri() {
        return asNormalizedUriPath(buildMainDir)
    }

    String getBuildTestUri() {
        return asNormalizedUriPath(buildTestDir)
    }

    private static String asNormalizedUriPath(File file) {
        def uri = file.toURI().path
        uri = StringUtils.removeStart(uri, "/")
        return StringUtils.removeEnd(uri, "/")
    }

    Collection<WebBundle> getBundles() {
        return Collections.unmodifiableCollection(bundles.values())
    }

    /**
     * Defines web resource bundle. Resources from bundle will be pre- and post-processed by specified set of processors.
     *
     * @param name  bundle name; generated output files will be <name>.js and/or <name>.css
     * @param configure    closure to configure bundle
     * @see WebBundle
     */
    void bundle(String name, Closure configure) {
        def bundle = bundles.get(name)
        if (bundle == null) {
            bundle = new WebBundle(name)
            bundles.put(name, bundle)
        }

        configure.delegate = bundle
        configure.resolveStrategy = Closure.DELEGATE_FIRST
        configure()
    }

    /**
     * Defines web assets. Assets will be copied to static folder as is.
     *
     * @param configure    closure to configure assets
     * @see CopySpec
     */
    void assets(Closure configure) {
        if (mainAssets == null) {
            mainAssets = project.copySpec()
        }
        configure.delegate = mainAssets
        configure.resolveStrategy = Closure.DELEGATE_FIRST
        configure()
    }

    /**
     * Defines web test assets. Test assets will be copied to static folder as is.
     *
     * @param configure    closure to configure assets
     * @see CopySpec
     */
    void testAssets(Closure configure) {
        if (testAssets == null) {
            testAssets = project.copySpec()
        }
        configure.delegate = testAssets
        configure.resolveStrategy = Closure.DELEGATE_FIRST
        configure()
    }

    WroModel createWroModel() {
        def wro = new WroModel()
        bundles.values().each { bundle ->
            wro.addGroup(bundle.group)
        }
        return wro
    }

    CopySpec getMainAssets() {
        return mainAssets
    }

    CopySpec getTestAssets() {
        return testAssets
    }

    static WebResourceSet get(Project project) {
        return project.extensions.getByType(WebResourceSet)
    }
}

package ro.isdc.wro4j.gradle.tasks

import org.gradle.api.DefaultTask;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.file.FileCollection;

import ro.isdc.wro.model.WroModel

class ProcessResourcesTask  extends DefaultTask {
    static final Logger LOGGER = Logging.getLogger(ProcessResourcesTask)
    
    @Input WroModel model
    
    @InputFiles FileCollection srcFiles
    @OutputDirectory File destDir
    
    @TaskAction
    def processResources(){
        
    }
}

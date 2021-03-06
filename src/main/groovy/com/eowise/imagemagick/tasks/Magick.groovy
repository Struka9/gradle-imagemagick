package com.eowise.imagemagick.tasks

import com.eowise.imagemagick.specs.FormattingSpec
import com.eowise.imagemagick.specs.DefaultMagickSpec
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.incremental.IncrementalTaskInputs
import org.gradle.api.tasks.util.PatternSet

/**
 * Created by aurel on 14/12/13.
 */
class Magick extends DefaultTask {

    
    @InputFiles
    FileTree inputFiles
    @OutputDirectory
    File outputDir
    @Input
    String inputSpec

    DefaultMagickSpec spec
    FormattingSpec formattingSpec;
    Closure output
    Closure outputFileFormInputFileClosure

    Magick() {
        this.spec = new DefaultMagickSpec(this)
        this.formattingSpec = new FormattingSpec(this)
    }


    def convert(String baseDir, PatternSet pattern) {
        this.inputFiles = project.fileTree(baseDir).matching(pattern)
        this.output = { relativePath -> "${baseDir}/${relativePath}"  }
        this.outputDir = project.file(output(''))
        this.spec.setInputBasePath(baseDir)
        this.formattingSpec.setInputBasePath(baseDir)
    }

    def convert(String baseDir, Closure closure) {
        PatternSet pattern = project.configure(new PatternSet(), closure) as PatternSet

        convert(baseDir, pattern)
    }

    def into(Closure outputClosure) {
        this.output = outputClosure
        this.outputDir = project.file(output(''))
        this.spec.setOutput(outputClosure)
    }

    def into(String path) {
        into({ relativePath -> "${path}/${relativePath}"  })
    }

    def formatting(Closure closure) {
        project.configure(formattingSpec, closure)
    }

    def actions(Closure closure) {
        project.configure(spec, closure)
        inputSpec = spec.toString()
    }

    def outputFileFormInputFile(Closure outputFileFormInputFile) {
        this.outputFileFormInputFileClosure = outputFileFormInputFile
    }

    LinkedList<String> buildArgs(FileVisitDetails file) {

        LinkedList<String> execArgs = []

        spec.params.each {
            p ->
                execArgs.addAll(p.toParams(file))
        }

        return execArgs
    }
    
    @TaskAction
    void execute(IncrementalTaskInputs incrementalInputs) {
        LinkedList<String> execArgs
        FileCollection changedFiles = project.files()

        incrementalInputs.outOfDate {
            change ->
                changedFiles.from(change.file)
        }


        inputFiles.visit {
            FileVisitDetails f ->

                if (changedFiles.contains(f.getFile())) {

                    if (!f.getFile().isDirectory()) {

                        formattingSpec.formats.each {
                            id, param ->
                                project.exec {
                                    commandLine 'convert'
                                    args param.toParams(f)
                                    standardOutput new FileOutputStream("${temporaryDir}/${f.getRelativePath()}.${id}.mvg")
                                }
                        }

                        execArgs = buildArgs(f)

                        project.exec {
                            commandLine 'convert'
                            args execArgs
                        }
                    }

                }
        }

        if (incrementalInputs.isIncremental() && outputFileFormInputFileClosure != null) {
            incrementalInputs.removed {
                remove ->
                    File outputFileToRemove = outputFileFormInputFileClosure(remove.file)
                    outputFileToRemove.delete()
            }
        }
    }
}

/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.plugins
import org.gradle.api.DefaultTask
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskDependencyMatchers
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.ClassDirectoryBinarySpec
import org.gradle.language.base.ProjectSourceSet
import org.gradle.language.base.plugins.LanguageBasePlugin
import org.gradle.language.java.JavaSourceSet
import org.gradle.language.jvm.JvmResourceSet
import org.gradle.model.internal.core.ModelPath
import org.gradle.model.internal.type.ModelType
import org.gradle.model.internal.type.ModelTypes
import org.gradle.platform.base.BinarySpec
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.SetSystemProperties
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.api.file.FileCollectionMatchers.sameCollection
import static org.gradle.util.WrapUtil.toLinkedSet

class JavaBasePluginTest extends Specification {
    @Rule
    public SetSystemProperties sysProperties = new SetSystemProperties()
    private final DefaultProject project = TestUtil.createRootProject()

    void appliesBasePluginsAndAddsConventionObject() {
        when:
        project.pluginManager.apply(JavaBasePlugin)

        then:
        project.plugins.hasPlugin(ReportingBasePlugin)
        project.plugins.hasPlugin(BasePlugin)
        project.plugins.hasPlugin(LanguageBasePlugin)
        project.convention.plugins.java instanceof JavaPluginConvention
    }

    void "creates tasks and applies mappings for source set"() {
        when:
        project.pluginManager.apply(JavaBasePlugin)
        project.sourceSets.create('custom')
        new TestFile(project.file("src/custom/java/File.java")) << "foo"
        new TestFile(project.file("src/custom/resouces/resource.txt")) << "foo"

        then:
        SourceSet set = project.sourceSets.custom
        set.java.srcDirs == toLinkedSet(project.file('src/custom/java'))
        set.resources.srcDirs == toLinkedSet(project.file('src/custom/resources'))
        set.output.classesDir == new File(project.buildDir, 'classes/custom')
        set.output.resourcesDir == new File(project.buildDir, 'resources/custom')

        def processResources = project.tasks['processCustomResources']
        processResources.description == "Processes custom resources."
        processResources instanceof Copy
        TaskDependencyMatchers.dependsOn().matches(processResources)
        processResources.destinationDir == new File(project.buildDir, 'resources/custom')
        def resources = processResources.source
        resources.files == project.sourceSets.custom.resources.files

        def compileJava = project.tasks['compileCustomJava']
        compileJava.description == "Compiles custom Java source."
        compileJava instanceof JavaCompile
        TaskDependencyMatchers.dependsOn().matches(compileJava)
        compileJava.classpath.is(project.sourceSets.custom.compileClasspath)
        compileJava.destinationDir == new File(project.buildDir, 'classes/custom')

        def sources = compileJava.source
        sources.files == project.sourceSets.custom.java.files

        def classes = project.tasks['customClasses']
        classes.description == "Assembles custom classes."
        classes instanceof DefaultTask
        TaskDependencyMatchers.dependsOn('processCustomResources', 'compileCustomJava').matches(classes)
        TaskDependencyMatchers.builtBy('customClasses').matches(project.sourceSets.custom.output)
    }

    void "creates tasks and applies mappings for main source set"() {
        when:
        project.pluginManager.apply(JavaBasePlugin)
        project.sourceSets.create('main')

        then:
        SourceSet set = project.sourceSets.main
        set.java.srcDirs == toLinkedSet(project.file('src/main/java'))
        set.resources.srcDirs == toLinkedSet(project.file('src/main/resources'))
        set.output.classesDir == new File(project.buildDir, 'classes/main')
        set.output.resourcesDir == new File(project.buildDir, 'resources/main')

        def processResources = project.tasks.processResources
        processResources.description == "Processes main resources."
        processResources instanceof Copy

        def compileJava = project.tasks.compileJava
        compileJava.description == "Compiles main Java source."
        compileJava instanceof JavaCompile

        def classes = project.tasks.classes
        classes.description == "Assembles main classes."
        TaskDependencyMatchers.dependsOn('processResources', 'compileJava').matches(classes)
    }

    void "wires generated resources task into classes task for sourceset"() {
        when:
        project.pluginManager.apply(JavaBasePlugin)
        project.sourceSets.create('custom')

        and:
        final someTask = project.task("someTask")
        project.sourceSets.custom.output.dir('some-dir', builtBy: someTask)

        then:
        def customClasses = project.tasks['customClasses']
        TaskDependencyMatchers.dependsOn('someTask', 'processCustomResources', 'compileCustomJava').matches(customClasses)
    }

    void tasksReflectChangesToSourceSetConfiguration() {
        def classesDir = project.file('target/classes')
        def resourcesDir = project.file('target/resources')

        when:
        project.pluginManager.apply(JavaBasePlugin)
        project.sourceSets.create('custom')
        project.sourceSets.custom.output.classesDir = classesDir
        project.sourceSets.custom.output.resourcesDir = resourcesDir

        then:
        def processResources = project.tasks['processCustomResources']
        processResources.destinationDir == resourcesDir

        def compileJava = project.tasks['compileCustomJava']
        compileJava.destinationDir == classesDir
    }

    void createsConfigurationsForNewSourceSet() {
        when:
        project.pluginManager.apply(JavaBasePlugin)
        def sourceSet = project.sourceSets.create('custom')

        then:
        def compile = project.configurations.customCompile
        compile.transitive
        !compile.visible
        compile.extendsFrom == [] as Set
        compile.description == "Compile classpath for source set 'custom'."

        and:
        def runtime = project.configurations.customRuntime
        runtime.transitive
        !runtime.visible
        runtime.extendsFrom == [compile] as Set
        runtime.description == "Runtime classpath for source set 'custom'."

        and:
        def runtimeClasspath = sourceSet.runtimeClasspath
        def compileClasspath = sourceSet.compileClasspath
        compileClasspath == compile
        runtimeClasspath sameCollection(sourceSet.output + runtime)
    }

    void appliesMappingsToTasksDefinedByBuildScript() {
        when:
        project.pluginManager.apply(JavaBasePlugin)

        then:
        def compile = project.task('customCompile', type: JavaCompile)
        compile.sourceCompatibility == project.sourceCompatibility.toString()

        def test = project.task('customTest', type: Test.class)
        test.workingDir == project.projectDir
        test.reports.junitXml.destination == project.testResultsDir
        test.reports.html.destination == project.testReportDir
        test.reports.junitXml.enabled
        test.reports.html.enabled

        def javadoc = project.task('customJavadoc', type: Javadoc)
        javadoc.destinationDir == project.file("$project.docsDir/javadoc")
        javadoc.title == project.extensions.getByType(ReportingExtension).apiDocTitle
    }

    void appliesMappingsToCustomJarTasks() {
        when:
        project.pluginManager.apply(JavaBasePlugin)
        def task = project.task('customJar', type: Jar)

        then:
        TaskDependencyMatchers.dependsOn().matches(task)
        task.destinationDir == project.libsDir
    }

    void createsLifecycleBuildTasks() {
        when:
        project.pluginManager.apply(JavaBasePlugin)

        then:
        def build = project.tasks[JavaBasePlugin.BUILD_TASK_NAME]
        TaskDependencyMatchers.dependsOn(JavaBasePlugin.CHECK_TASK_NAME, BasePlugin.ASSEMBLE_TASK_NAME).matches(build)

        def buildDependent = project.tasks[JavaBasePlugin.BUILD_DEPENDENTS_TASK_NAME]
        TaskDependencyMatchers.dependsOn(JavaBasePlugin.BUILD_TASK_NAME).matches(buildDependent)

        def buildNeeded = project.tasks[JavaBasePlugin.BUILD_NEEDED_TASK_NAME]
        TaskDependencyMatchers.dependsOn(JavaBasePlugin.BUILD_TASK_NAME).matches(buildNeeded)
    }

    def configuresTestTaskWhenDebugSystemPropertyIsSet() {
        project.pluginManager.apply(JavaBasePlugin)
        def task = project.tasks.create('test', Test.class)

        when:
        System.setProperty("test.debug", "true")
        project.projectEvaluationBroadcaster.afterEvaluate(project, null)

        then:
        task.debug
    }

    def "configures test task when test.single is used"() {
        project.pluginManager.apply(JavaBasePlugin)
        def task = project.tasks.create('test', Test.class)
        task.include 'ignoreme'

        when:
        System.setProperty("test.single", "pattern")
        project.projectEvaluationBroadcaster.afterEvaluate(project, null)

        then:
        task.includes == ['**/pattern*.class'] as Set
        task.inputs.getSourceFiles().empty
    }

    def "adds language source sets for each source set added to the 'sourceSets' container"() {
        project.pluginManager.apply(JavaBasePlugin)

        given:
        project.sourceSets {
            custom {
                java {
                    srcDirs = [project.file("src1"), project.file("src2")]
                }
                resources {
                    srcDirs = [project.file("resrc1"), project.file("resrc2")]
                }
                compileClasspath = project.files("jar1.jar", "jar2.jar")
            }
        }

        when:
        def sources = project.modelRegistry.realize(ModelPath.path("sources"), ModelType.of(ProjectSourceSet))

        then:
        sources.size() == 2

        and:
        def java = sources.withType(JavaSourceSet).iterator().next()
        java.source.srcDirs as Set == [project.file("src1"), project.file("src2")] as Set
        java.compileClasspath.files as Set == project.files("jar1.jar", "jar2.jar") as Set

        and:
        def resources = sources.withType(JvmResourceSet).iterator().next()
        resources.source.srcDirs as Set == [project.file("resrc1"), project.file("resrc2")] as Set
    }

    def "adds a class directory binary for each source set added to the 'sourceSets' container"() {
        project.pluginManager.apply(JavaBasePlugin)

        given:
        project.sourceSets {
            custom {
                output.classesDir = project.file("classes")
                output.resourcesDir = project.file("resources")
            }
        }

        when:
        def binaries = project.modelRegistry.realize(ModelPath.path("binaries"), ModelTypes.modelMap(BinarySpec))
        def sources = project.modelRegistry.realize(ModelPath.path("sources"), ModelType.of(ProjectSourceSet))

        then:
        binaries.size() == 1
        def binary = binaries.get("customClasses")
        binary instanceof ClassDirectoryBinarySpec
        binary.classesDir == project.file("classes")
        binary.resourcesDir == project.file("resources")
        binary.inputs.size() == 2
        binary.inputs as Set == sources as Set
    }

    def "attaches tasks to binary associated with each source set"() {
        when:
        project.pluginManager.apply(JavaBasePlugin)

        project.sourceSets {
            custom {
                output.classesDir = project.file("classes")
                output.resourcesDir = project.file("resources")
            }
        }
        def binaries = project.modelRegistry.realize(ModelPath.path("binaries"), ModelTypes.modelMap(BinarySpec))

        then:
        def binary = binaries.get("customClasses")
        assert binary instanceof ClassDirectoryBinarySpec
        def classesTask = project.tasks.findByName("customClasses")
        binary.buildTask == classesTask
        binary.tasks.contains(classesTask)
        binary.tasks.contains(project.tasks.findByName("compileCustomJava"))
        binary.tasks.contains(project.tasks.findByName("processCustomResources"))
    }

}

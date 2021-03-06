/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.language.java

class JarBinaryTypeVariantsTest extends VariantAwareDependencyResolutionSpec {

    def "can depend on a component without specifying any variant dimension"() {
        given:
        applyJavaPlugin(buildFile)
        addCustomLibraryType(buildFile)

        buildFile << '''

model {
    components {
        first(FlavorAndBuildTypeAwareLibrary) {
            sources {
                java(JavaSourceSet) {
                    dependencies {
                        library 'second'
                    }
                }
            }
        }
        second(FlavorAndBuildTypeAwareLibrary) {
            sources {
                java(JavaSourceSet)
            }
        }
    }

    tasks {
        firstDefaultDefaultJar {
            doLast {
                assert compileFirstDefaultDefaultJarFirstJava.taskDependencies.getDependencies(compileFirstDefaultDefaultJarFirstJava).contains(secondDefaultDefaultJar)
                assert compileFirstDefaultDefaultJarFirstJava.classpath.files == [file("${buildDir}/jars/secondDefaultDefaultApiJar/second.jar")] as Set
            }
        }
    }
}
'''
        file('src/first/java/FirstApp.java') << 'public class FirstApp extends SecondApp {}'
        file('src/second/java/SecondApp.java') << 'public class SecondApp {}'

        expect:
        succeeds ':firstDefaultDefaultJar'

    }

    def "can depend on a component with explicit flavors"() {
        given:
        applyJavaPlugin(buildFile)
        addCustomLibraryType(buildFile)

        buildFile << '''

model {
    components {
        first(FlavorAndBuildTypeAwareLibrary) {
            flavors 'paid', 'free'
            sources {
                java(JavaSourceSet) {
                    dependencies {
                        library 'second'
                    }
                }
            }
        }
        second(FlavorAndBuildTypeAwareLibrary) {
            flavors 'paid', 'free'
            sources {
                java(JavaSourceSet)
            }
        }
    }

    tasks {
        firstPaidDefaultJar {
            doLast {
                assert compileFirstPaidDefaultJarFirstJava.taskDependencies.getDependencies(compileFirstPaidDefaultJarFirstJava).contains(secondPaidDefaultJar)
                assert compileFirstPaidDefaultJarFirstJava.classpath.files == [file("${buildDir}/jars/secondPaidDefaultApiJar/second.jar")] as Set
            }
        }
        firstFreeDefaultJar {
            doLast {
                assert compileFirstFreeDefaultJarFirstJava.taskDependencies.getDependencies(compileFirstFreeDefaultJarFirstJava).contains(secondFreeDefaultJar)
                assert compileFirstFreeDefaultJarFirstJava.classpath.files == [file("${buildDir}/jars/secondFreeDefaultApiJar/second.jar")] as Set
            }
        }
    }
}
'''
        file('src/first/java/FirstApp.java') << 'public class FirstApp extends SecondApp {}'
        file('src/second/java/SecondApp.java') << 'public class SecondApp {}'

        expect:
        succeeds ':firstPaidDefaultJar'
        succeeds ':firstFreeDefaultJar'

    }

}

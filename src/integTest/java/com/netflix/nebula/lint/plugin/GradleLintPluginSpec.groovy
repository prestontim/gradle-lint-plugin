/*
 * Copyright 2015-2016 Netflix, Inc.
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

package com.netflix.nebula.lint.plugin

import com.netflix.nebula.lint.TestKitSpecification
import spock.lang.Unroll

class GradleLintPluginSpec extends TestKitSpecification {
    def 'run multiple rules on a single module project'() {
        when:
        buildFile << """
            apply plugin: 'java'
            apply plugin: ${GradleLintPlugin.name}

            gradleLint.rules = ['dependency-parentheses', 'dependency-tuple']

            dependencies {
                compile('com.google.guava:guava:18.0')
                testCompile group: 'junit',
                    name: 'junit',
                    version: '4.11'
            }
        """

        then:
        def results = runTasksSuccessfully('assemble')

        when:
        def console = results.output.readLines()

        then:
        console.findAll { it.startsWith('warning') }.size() == 2
        console.any { it.contains('dependency-parentheses') }
        console.any { it.contains('dependency-tuple') }
    }

    def 'run rules on multi-module project where one of the subprojects has no build.gradle'() {
        when:
        buildFile << """
            allprojects {
                plugins {
                    id 'nebula.lint'
                    id 'java'
                }

                gradleLint.rules = ['dependency-parentheses', 'dependency-tuple']
            }
        """

        addSubproject('sub')

        then:
        runTasksSuccessfully('lintGradle')
    }

    def 'run rules on multi-module project where one of the subprojects does not apply gradle lint'() {
        when:
        buildFile << """
            allprojects {
                apply plugin: 'java'
            }

            apply plugin: ${GradleLintPlugin.name}
            gradleLint.rules = ['dependency-parentheses', 'dependency-tuple']
        """

        addSubproject('sub', """
            dependencies {
                compile('a:a:1')
            }
        """)

        then:
        runTasksSuccessfully('lintGradle')
    }

    @Unroll
    def 'auto correct all violations on a single module project with task #taskName'() {
        when:
        buildFile.text = """
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint.rules = ['dependency-parentheses', 'dependency-tuple']

            dependencies {
                compile('com.google.guava:guava:18.0')
            }

            configurations { }
        """

        then:
        runTasksSuccessfully(taskName)

        buildFile.text == """
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint.rules = ['dependency-parentheses', 'dependency-tuple']

            dependencies {
                compile 'com.google.guava:guava:18.0'
            }

        """.toString()

        where:
        taskName << ['fixGradleLint', 'fixLintGradle']
    }

    @Unroll
    def 'auto correct all violations on a multi-module project with task #taskName'() {
        when:
        buildFile.text = """
            allprojects {
                plugins {
                    id 'nebula.lint'
                }
                gradleLint.rules = ['dependency-parentheses', 'dependency-tuple']
            }

            subprojects {
                apply plugin: 'java'
                dependencies {
                    compile('com.google.guava:guava:18.0')
                }
            }
        """

        def subDir = addSubproject('sub', """
            dependencies {
                testCompile group: 'junit',
                    name: 'junit',
                    version: '4.11'
            }

            task taskA {}
        """)

        then:
        runTasksSuccessfully(":sub:$taskName") // prove that this links to the root project task

        buildFile.text == """
            allprojects {
                plugins {
                    id 'nebula.lint'
                }
                gradleLint.rules = ['dependency-parentheses', 'dependency-tuple']
            }

            subprojects {
                apply plugin: 'java'
                dependencies {
                    compile 'com.google.guava:guava:18.0'
                }
            }
        """.toString()

        new File(subDir, 'build.gradle').text == """
            dependencies {
                testCompile 'junit:junit:4.11'
            }

            task taskA {}
        """

        where:
        taskName << ['fixGradleLint', 'fixLintGradle']
    }

    @Unroll
    def 'auto correct violations on a multi-module project where one of the subprojects does not apply gradle lint with task #taskName'() {
        when:
        buildFile << """
            plugins {
                id 'nebula.lint'
            }
            gradleLint.rules = ['dependency-parentheses', 'dependency-tuple']
        """

        addSubproject('sub', """
            apply plugin: 'java'
            dependencies {
                compile('a:a:1')
            }
        """)

        then:
        runTasksSuccessfully(taskName)

        where:
        taskName << ['fixGradleLint', 'fixLintGradle']
    }

    def 'rules relative to each project'() {
        when:
        buildFile.text = """
            allprojects {
                plugins {
                    id 'nebula.lint'
                }
                gradleLint.rules = ['dependency-parentheses', 'dependency-tuple']
            }

            subprojects {
                apply plugin: 'java'
                dependencies {
                    compile('com.google.guava:guava:18.0')
                }
            }
        """

        addSubproject('sub', """
            dependencies {
                testCompile group: 'junit',
                    name: 'junit',
                    version: '4.11'
            }

            task taskA {}
        """)

        then:
        def results = runTasksSuccessfully('taskA')

        when:
        def console = results.output.readLines()

        then:
        console.findAll { it.startsWith('warning') }.size() == 2
        console.any { it.contains('dependency-parentheses') }
        console.any { it.contains('dependency-tuple') }
    }

    def 'generate a lint report for a single module project'() {
        when:
        buildFile << """
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint.rules = ['dependency-parentheses', 'dependency-tuple']

            dependencies {
                compile('com.google.guava:guava:18.0')
                testCompile group: 'junit',
                    name: 'junit',
                    version: '4.11'
            }
        """

        then:
        def results = runTasksSuccessfully('generateGradleLintReport')

        when:
        def console = results.output.readLines()

        then:
        console.findAll { it.startsWith('warning') }.size() == 2
        console.any { it.contains('dependency-parentheses') }
        console.any { it.contains('dependency-tuple') }
        new File(projectDir, "build/reports/gradleLint/${moduleName}.html").exists()
    }
}

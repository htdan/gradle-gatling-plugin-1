package uk.gov.gradle.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project

import java.util.regex.Pattern

class GatlingPlugin implements Plugin<Project> {

    final String GATLING_VERSION = '2.1.3'

    private String gatlingReportsDirectory
    private Project project

    void apply(Project project) {
        this.project = project
        project.plugins.apply 'scala'
        project.extensions.create('gatling', GatlingPluginExtension)

        project.dependencies {
            testCompile "io.gatling.highcharts:gatling-charts-highcharts:${GATLING_VERSION}",
                        'com.nimbusds:nimbus-jose-jwt:2.22.1'
        }
        project.repositories {
            jcenter()
            mavenLocal()
            mavenCentral()
        }
        project.sourceSets {
            test {
                scala {
                    srcDirs = ['user-files/simulations']
                }
                resources {
                    srcDirs = ['user-files/conf', 'user-files/data']
                }
                // change the build directory to gatling default
                output.classesDir = 'target/test-classes'
            }
        }
        gatlingReportsDirectory = "$project.buildDir.absolutePath/gatling-reports"
        project.task('gatlingTest',
                     dependsOn: 'build') << {
            project.gatling.verifySettings()
            final def sourceSet = project.sourceSets.test
            final def gatlingRequestBodiesDirectory = firstPath(sourceSet.resources.srcDirs) + "/bodies"
            final def gatlingClasspath = sourceSet.output + sourceSet.runtimeClasspath
            final def scenarios = project.gatling._scenarios ?: getGatlingScenarios(sourceSet)
            final def localSystemProperties = System.getProperties()?:[:]

            def include = project.getProperties().get('include')
            def exclude = project.getProperties().get('exclude')
            def whiteListPattern = createPatternFromList(include==null?null:Arrays.asList(include));
            def blackListPattern = createPatternFromList(exclude==null?null:Arrays.asList(exclude));

            logger.lifecycle "Scenarios found before filter: $scenarios"
            scenarios?.each { scenario ->
                if (check(scenario, blackListPattern, whiteListPattern)) {
                    logger.lifecycle "Scenario to be executed: $scenario"
                    project.javaexec {
                        main = 'io.gatling.app.Gatling'
                        classpath = gatlingClasspath
                        if (project.gatling.verbose) {
                            jvmArgs '-verbose'
                        }
                        // If a user has the GATLING_HOME env var set, gradle will try to compile
                        // simulations which are saved in GATLING_HOME.  This can break the build.
                        environment GATLING_HOME: ''
                        args '-rf', gatlingReportsDirectory,
                             '-s', scenario,
                             '-bdf', gatlingRequestBodiesDirectory
                        systemProperties(project.gatling.systemProperties ?
                                         localSystemProperties + project.gatling.systemProperties: localSystemProperties)
                    }
                }
                else{
                    logger.lifecycle "Scenario is excluded to be executed: $scenario"
                }
            }
            logger.lifecycle "Gatling scenarios completed."
        }
        project.task('openGatlingReport') << {
            def mostRecent
            withGatlingReportsDirs { projectDir ->
                if (projectDir > mostRecent) {
                    mostRecent = projectDir
                }
            }
            openReport mostRecent
        }
        project.task('openGatlingReports') << {
            withGatlingReportsDirs openReport
        }
    }

    private getGatlingScenarios(sourceSet) {
        final String scenarioSrcDir = "$project.projectDir.absolutePath/user-files/simulations"
        final int scenarioPathPrefix = "$scenarioSrcDir/".size()
        final int scenarioPathSuffix = -('.scala'.size() + 1)
        sourceSet.allScala.files*.toString().
                findAll { it.endsWith 'Scenario.scala' }.
                collect { it[scenarioPathPrefix..scenarioPathSuffix] }*.
                replace(System.getProperty("file.separator"), '.')
    }

    private firstPath(Set<File> files) {
        return files.toList().first().toString()
    }

    private openReport = { reportDir ->
        project.exec { commandLine 'open', "$reportDir/index.html" }
    }

    private withGatlingReportsDirs(Closure c) {
        new File(gatlingReportsDirectory).eachDirMatch(~/.*-\d+/, c)
    }

    static boolean check(String clazz, Pattern blackListPattern, Pattern whiteListPattern) {
        if (inWhiteList(clazz, whiteListPattern)) {
            return true;
        } else {
            if (inBlackList(clazz, blackListPattern)) {
                return false;
            } else {
                return true;
            }
        }
    }

    static boolean inBlackList(String path, Pattern blackListPattern) {
        if (blackListPattern == null) {
            return false
        };
        return blackListPattern.matcher(path).matches();
    }

    static boolean inWhiteList(String path, Pattern whiteListPattern) {
        if (whiteListPattern == null) {
            return false
        };
        return whiteListPattern.matcher(path).matches();
    }

    static Pattern createPatternFromList(List<String> list) {
        if (list == null || list.size() == 0) {
            return null
        };
        StringBuilder sb = new StringBuilder(500);
        for (int i = 0; i < list.size(); i++) {
            sb.append(list.get(i));
            if (i < list.size() - 1) {
                sb.append("|")
            };
        }
        return Pattern.compile(sb.toString());
    }

}

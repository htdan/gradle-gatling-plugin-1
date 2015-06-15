package uk.gov.gradle.plugins

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

import static org.junit.Assert.assertTrue

/**
 * Created by haitao on 10/06/15.
 */
class GradleGatlingTest {

    @Test
    public void gatlingPluginTest() {
        Project project = ProjectBuilder.builder().build()
        project.apply plugin: 'gatling'
        println(project.getPlugins())
    }

}

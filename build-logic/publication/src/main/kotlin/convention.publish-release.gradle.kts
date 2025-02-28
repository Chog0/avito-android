plugins {
    id("convention.publish-base")
    id("convention.teamcity")
    id("convention.sonatype")
    id("convention.artifactory")
}

/**
 * used in ci/publish.sh
 */
tasks.register("publishRelease") {
    group = "publication"

    dependsOn(tasks.named("publishToSonatype"))

    finalizedBy(tasks.named("teamcityPrintReleasedVersion"))
}

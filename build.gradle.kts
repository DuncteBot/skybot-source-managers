/*
 * Copyright 2020 Duncan "duncte123" Sterken
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

import org.gradle.api.tasks.wrapper.Wrapper.DistributionType
import com.jfrog.bintray.gradle.BintrayExtension
import com.jfrog.bintray.gradle.tasks.BintrayUploadTask
import java.util.*

plugins {
    idea
    `java-library`
    `maven-publish`

    id("com.jfrog.bintray") version "1.8.1"
}

project.group = "com.dunctebot"
project.version = "1.3.0"
val archivesBaseName = "sourcemanagers"

repositories {
    jcenter()

    maven {
        url = uri("https://jitpack.io")
    }
}

dependencies {
    // build override for age-restricted videos
    implementation(group = "com.github.duncte123", name = "lavaplayer", version = "dd595a1")
//    api(group = "com.sedmelluq", name = "lavaplayer", version = "1.3.33")
    implementation(group = "io.sentry", name = "sentry-logback", version = "1.7.17")

    implementation(group = "com.google.code.findbugs", name = "jsr305", version = "3.0.2")
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<Wrapper> {
    distributionType = DistributionType.ALL
    gradleVersion = "5.6.3"
}

val bintrayUpload: BintrayUploadTask by tasks
val jar: Jar by tasks
val build: Task by tasks
val clean: Task by tasks

val sourcesJar = task<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from(sourceSets["main"].allJava)
}

build.apply {
//    dependsOn(clean)
    dependsOn(jar)
    dependsOn(sourcesJar)

    jar.mustRunAfter(clean)
    sourcesJar.mustRunAfter(jar)
}

bintrayUpload.apply {
    dependsOn(build)

    onlyIf { System.getenv("BINTRAY_USER") != null }
    onlyIf { System.getenv("BINTRAY_KEY") != null }
}

publishing {
    publications {
        register("BintrayRelease", MavenPublication::class) {
            from(components["java"])
            artifact(sourcesJar)

            artifactId = archivesBaseName
            groupId = project.group as String
            version = project.version as String
        }
    }
}

bintray {
    user = System.getenv("BINTRAY_USER")
    key = System.getenv("BINTRAY_KEY")
    setPublications("BintrayRelease")
    pkg(delegateClosureOf<BintrayExtension.PackageConfig> {
        repo = "maven"
        name = "sourcemanagers"
        setLicenses("Apache-2.0")
        vcsUrl = "https://github.com/dunctebot/skybot-source-managers.git"
        publish = true
        version(delegateClosureOf<BintrayExtension.VersionConfig> {
            name = project.version as String
            released = Date().toString()
        })
    })
}


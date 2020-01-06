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

plugins {
    idea
    `java-library`
    `maven-publish`
}

group = "com.dunctebot.sourcemanagers"
version = "1.0-SNAPSHOT"

repositories {
    jcenter()

    maven {
        url = uri("https://jitpack.io")
    }
}

dependencies {
//    api(group = "com.github.duncte123", name = "lavaplayer", version = "1dff250")
    api(group = "com.sedmelluq", name = "lavaplayer", version = "1.3.33")
    api(group = "io.sentry", name = "sentry-logback", version = "1.7.17")
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_11
}

tasks.withType<Wrapper> {
    distributionType = DistributionType.ALL
    gradleVersion = "5.6.3"
}

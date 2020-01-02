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
    application
    `java-library`
}

group = "com.dunctebot.sourcemanagers"
version = "1.0-SNAPSHOT"

repositories {
    jcenter()

    maven {
        url = uri("https://maven.notfab.net/Hosted")
    }

    maven {
        url = uri("https://jitpack.io")
    }
}

dependencies {
    api(group = "com.github.duncte123", name = "lavaplayer", version = "1dff250")
    api(group = "net.notfab.cache", name = "cache-client", version = "2.2")
    api(group = "io.sentry", name = "sentry-logback", version = "1.7.17")
    api(group = "com.google.apis", name = "google-api-services-youtube", version = "v3-rev212-1.25.0")
    api(group = "me.duncte123", name = "botCommons", version = "1.0.65")
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<Wrapper> {
    distributionType = DistributionType.ALL
    gradleVersion = "5.6.3"
}
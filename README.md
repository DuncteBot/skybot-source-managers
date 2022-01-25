# Skybot source managers
A bunch of extra source managers for LavaPlayer:

- ocremix.org
- Clyp.it
- Reddit
- getyarn.io
- Text To Speech (if prefixed with `speak:`)
- TikTok (not functional yet)
- PornHub

## Lavalink users
A lavalink plugin version of these source managers can be found here: https://github.com/DuncteBot/skybot-lavalink-plugin


## Installation
Installing this plugin can be done via gradle

```gradle
repositories {
    maven("https://m2.duncte123.dev/releases")
}

dependencies {
    implementation("com.dunctebot:sourcemanagers:VERSION")
}
```
Replace version with the latest version: ![Maven metadata URL][VERSION]

## Usage
You are able to register all the source managers via the following code snippet:
```java
AudioPlayerManager playerManager = new DefaultAudioPlayerManager(); // Your lavaplayer player manager
String ttsLange = "en-US"; // The language for the speak/tts item

DuncteBotSources.registerCustom(playerManager, ttsLange, 6);
```

[VERSION]: https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fduncte123.jfrog.io%2Fartifactory%2Fmaven%2Fcom%2Fdunctebot%2Fsourcemanagers%2Fmaven-metadata.xml

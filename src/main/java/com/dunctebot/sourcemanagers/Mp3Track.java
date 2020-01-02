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

package com.dunctebot.sourcemanagers;

import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

public class Mp3Track extends DelegatedAudioTrack {

    private static final Logger log = LoggerFactory.getLogger(Mp3Track.class);

    private final HttpAudioSourceManager manager;

    public Mp3Track(AudioTrackInfo trackInfo, AudioSourceManager manager) {
        super(trackInfo);
        this.manager = (HttpAudioSourceManager) manager;
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        try (HttpInterface httpInterface = manager.getHttpInterface()) {
            loadStream(executor, httpInterface);
        }
    }

    private void loadStream(LocalAudioTrackExecutor localExecutor, HttpInterface httpInterface) throws Exception {
        final String trackUrl = trackInfo.identifier;
        log.debug("Starting {} track from URL: {}", manager.getSourceName(), trackUrl);
        try (PersistentHttpStream stream = new PersistentHttpStream(httpInterface, new URI(trackUrl), null)) {
            processDelegate(new Mp3AudioTrack(trackInfo, stream), localExecutor);
        }
    }

    @Override
    public AudioSourceManager getSourceManager() {
        return manager;
    }
}

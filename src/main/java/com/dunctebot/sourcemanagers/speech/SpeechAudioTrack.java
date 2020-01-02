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

package com.dunctebot.sourcemanagers.speech;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import ml.duncte123.skybot.Author;
import ml.duncte123.skybot.audio.sourcemanagers.Mp3Track;

@Author(nickname = "ramidzkh", author = "Ramid Khan")
public class SpeechAudioTrack extends Mp3Track {

    SpeechAudioTrack(AudioTrackInfo trackInfo, SpeechAudioSourceManager manager) {
        super(trackInfo, manager);
    }

    @Override
    public AudioTrack makeClone() {
        return new SpeechAudioTrack(trackInfo, (SpeechAudioSourceManager) getSourceManager());
    }
}

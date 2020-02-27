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

package com.dunctebot.sourcemanagers.getyarn;

import com.dunctebot.sourcemanagers.MpegTrack;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

public class GetyarnAudioTrack extends MpegTrack {
    public GetyarnAudioTrack(AudioTrackInfo trackInfo, AudioSourceManager manager) {
        super(trackInfo, manager);
    }

    @Override
    public AudioTrack makeClone() {
        return new GetyarnAudioTrack(trackInfo, getSourceManager());
    }

    @Override
    protected String getPlaybackUrl() {
        return "https://y.yarn.co/" + this.trackInfo.identifier + ".mp4?v=0";
    }
}

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

package com.dunctebot.sourcemanagers.extra;

import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import jdk.internal.jline.internal.Nullable;

import java.util.List;

public class YoutubePlaylistMetadata {
    private final String id;
    private final String title;
    private final String nextPageKey;
    private final List<AudioTrackInfo> tracks;

    public YoutubePlaylistMetadata(String id, String title, @Nullable String nextPageKey, List<AudioTrackInfo> tracks) {
        this.id = id;
        this.title = title;
        this.nextPageKey = nextPageKey;
        this.tracks = tracks;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    @Nullable
    public String getNextPageKey() {
        return nextPageKey;
    }

    public List<AudioTrackInfo> getTracks() {
        return tracks;
    }

    @Override
    public String toString() {
        return "YoutubePlaylistMetadata{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                '}';
    }
}

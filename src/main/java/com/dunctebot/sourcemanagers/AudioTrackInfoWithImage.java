/*
 * Copyright 2021 Duncan "duncte123" Sterken
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

import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

public class AudioTrackInfoWithImage extends AudioTrackInfo {

    private final String image;

    public AudioTrackInfoWithImage(String title, String author, long length, String identifier, boolean isStream, String uri, String image) {
        super(title, author, length, identifier, isStream, uri);
        this.image = image;
    }

    public String getImage() {
        return image;
    }
}

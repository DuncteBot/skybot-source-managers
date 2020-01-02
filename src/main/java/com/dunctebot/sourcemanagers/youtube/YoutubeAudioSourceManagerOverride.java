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

package com.dunctebot.sourcemanagers.youtube;

import com.sedmelluq.discord.lavaplayer.source.youtube.*;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.notfab.caching.client.CacheClient;
import net.notfab.caching.shared.CacheResponse;

import java.io.IOException;

import static com.dunctebot.sourcemanagers.extra.YoutubeUtils.getVideoById;
import static com.dunctebot.sourcemanagers.extra.YoutubeUtils.videoToTrack;

public class YoutubeAudioSourceManagerOverride extends YoutubeAudioSourceManager {

    private final CacheClient cacheClient;
    private final String ytApiKey;

    public YoutubeAudioSourceManagerOverride(CacheClient cacheClient, String ytApiKey) {
        super(
            true,
            new DefaultYoutubeTrackDetailsLoader(),
            new YoutubeApiSearchProvider(ytApiKey, cacheClient),
            new YoutubeSignatureCipherManager(),
            new YoutubeApiPlaylistLoader(ytApiKey),
            new DefaultYoutubeLinkRouter()
        );

        this.cacheClient = cacheClient;
        this.ytApiKey = ytApiKey;
    }

    @Override
    public AudioItem loadTrackWithVideoId(String videoId, boolean mustExist) {
        final CacheResponse cacheResponse = this.cacheClient.get(videoId);

        if (!cacheResponse.failure && cacheResponse.getTrack() != null) {
            final AudioTrack track = cacheResponse.getTrack().toAudioTrack(this);
            return new DoNotCache(track);
        }

        if (mustExist) {
            return getFromYoutubeApi(videoId);
        }

        return super.loadTrackWithVideoId(videoId, mustExist);
    }

    private AudioItem getFromYoutubeApi(String videoId) {
        try {
            return videoToTrack(
                getVideoById(videoId, this.ytApiKey),
                this
            );
        } catch (IOException e) {
            throw new FriendlyException("This video does not exist", FriendlyException.Severity.SUSPICIOUS, e);
        }
    }

    /**
     * Don't cache tracks that are already retrieved from the cache
     */
    public static class DoNotCache extends YoutubeAudioTrack {
        DoNotCache(AudioTrack track) {
            super(track.getInfo(), (YoutubeAudioSourceManager) track.getSourceManager());
        }
    }
}

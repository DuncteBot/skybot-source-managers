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

import com.google.api.services.youtube.model.SearchResult;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeSearchResultLoader;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.http.ExtendedHttpConfigurable;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import ml.duncte123.skybot.audio.sourcemanagers.youtube.YoutubeAudioSourceManagerOverride.DoNotCache;
import net.notfab.caching.client.CacheClient;
import net.notfab.caching.shared.SearchParams;
import net.notfab.caching.shared.YoutubeTrack;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;

import static ml.duncte123.skybot.utils.YoutubeUtils.*;

public class YoutubeApiSearchProvider implements YoutubeSearchResultLoader {
    private final String apiKey;
    private final CacheClient cacheClient;

    public YoutubeApiSearchProvider(String apiKey, CacheClient cacheClient) {
        this.apiKey = apiKey;
        this.cacheClient = cacheClient;
    }

    @Override
    public AudioItem loadSearchResult(String query, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        final AudioItem cacheItem = this.searchCache(query, trackFactory);

        if (cacheItem == null) {
            return this.searchYoutubeAPI(query, trackFactory);
        }

        return cacheItem;
    }

    @Override
    public ExtendedHttpConfigurable getHttpConfiguration() {
        return null;
    }

    private AudioItem searchYoutubeAPI(String query, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        try {
            final List<SearchResult> searchResults = searchYoutube(query, this.apiKey, 1L);

            if (searchResults.isEmpty()) {
                return null;
            }

            final AudioTrackInfo info = videoToTrackInfo(
                getVideoById(searchResults.get(0).getId().getVideoId(), this.apiKey)
            );

            return trackFactory.apply(info);
        }
        catch (IOException e) {
            throw ExceptionTools.wrapUnfriendlyExceptions(e);
        }
    }

    private AudioItem searchCache(String query, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        final SearchParams searchParams = new SearchParams().setSearch(query);
        final List<YoutubeTrack> found = this.cacheClient.search(searchParams);

        if (found.isEmpty()) {
            return null;
        }

        final AudioTrackInfo info = found.get(0).toAudioTrack(null).getInfo();
        final AudioTrack audioTrack = trackFactory.apply(info);

        return new DoNotCache(audioTrack);
    }
}

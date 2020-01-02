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

import com.dunctebot.sourcemanagers.clypit.ClypitAudioSourceManager;
import com.dunctebot.sourcemanagers.extra.YoutubeContextFilterOverride;
import com.dunctebot.sourcemanagers.pornhub.PornHubAudioSourceManager;
import com.dunctebot.sourcemanagers.speech.SpeechAudioSourceManager;
import com.dunctebot.sourcemanagers.spotify.SpotifyAudioSourceManager;
import com.dunctebot.sourcemanagers.youtube.YoutubeAudioSourceManagerOverride;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import net.notfab.caching.client.CacheClient;

public class DuncteBotSources {
    public static void registerCustom(AudioPlayerManager playerManager, String speechLanguage,
                                      int playlistPageCount, boolean updateYoutubeData,
                                      String youtubeApiKey, CacheClient cacheClient,
                                      String spotifyClientId, String spotifyClientSecret, int playlistLimit) {

        final YoutubeAudioSourceManagerOverride youtubeAudioSourceManager = new YoutubeAudioSourceManagerOverride(
            cacheClient,
            youtubeApiKey
        );

        youtubeAudioSourceManager.setPlaylistPageCount(playlistPageCount);
        youtubeAudioSourceManager.getMainHttpConfiguration().setHttpContextFilter(new YoutubeContextFilterOverride(updateYoutubeData));

        playerManager.registerSourceManager(
            new SpotifyAudioSourceManager(
                youtubeAudioSourceManager,
                spotifyClientId,
                spotifyClientSecret,
                youtubeApiKey,
                playlistLimit
            )
        );
        playerManager.registerSourceManager(new ClypitAudioSourceManager());
        playerManager.registerSourceManager(new SpeechAudioSourceManager(speechLanguage));
        playerManager.registerSourceManager(new PornHubAudioSourceManager());
        playerManager.registerSourceManager(youtubeAudioSourceManager);

    }
}

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

import com.dunctebot.sourcemanagers.extra.YoutubePlaylistMetadata;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubePlaylistLoader;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;
import io.sentry.Sentry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static com.dunctebot.sourcemanagers.extra.YoutubeUtils.getPlaylistPageById;
import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.COMMON;

public class YoutubeApiPlaylistLoader implements YoutubePlaylistLoader {
    private final String apiKey;
    private volatile int playlistPageCount = 6;

    public YoutubeApiPlaylistLoader(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public void setPlaylistPageCount(int playlistPageCount) {
        this.playlistPageCount = playlistPageCount;
    }

    @Override
    public AudioPlaylist load(HttpInterface httpInterface, String playlistId, String selectedVideoId, Function<AudioTrackInfo, AudioTrack> trackFactory) {
        try {
            final YoutubePlaylistMetadata firstPage = getPlaylistPageById(playlistId, this.apiKey, null, true);

            if (firstPage == null) {
                throw new FriendlyException("This playlist does not exist", COMMON, null);
            }

            return buildPlaylist(firstPage, playlistId, selectedVideoId, trackFactory);
        }
        catch (IOException e) {
            Sentry.capture(e);

            throw ExceptionTools.wrapUnfriendlyExceptions(e);
        }
    }

    private AudioPlaylist buildPlaylist(YoutubePlaylistMetadata firstPage, String playlistId, String selectedVideoId,
                                        Function<AudioTrackInfo, AudioTrack> trackFactory) throws IOException {
        final List<AudioTrack> convertedTracks = new ArrayList<>();

        firstPage.getTracks()
            .stream()
            .map(trackFactory)
            .forEach(convertedTracks::add);

        String nextPageKey = firstPage.getNextPageKey();
        int loadCount = 0;
        final int pageCount = playlistPageCount;

        while (nextPageKey != null && ++loadCount < pageCount) {
            nextPageKey = fetchNextPage(nextPageKey, playlistId, trackFactory, convertedTracks);
        }

        return new BasicAudioPlaylist(
            firstPage.getTitle(),
            convertedTracks,
            getSelectedTrack(selectedVideoId, convertedTracks),
            false
        );
    }

    private AudioTrack getSelectedTrack(String selectedVideoId, List<AudioTrack> tracks) {
        if (selectedVideoId == null) {
            return null;
        }

        for (final AudioTrack track : tracks) {
            if (selectedVideoId.equals(track.getIdentifier())) {
                return track;
            }
        }

        return null;
    }

    private String fetchNextPage(String nextPageKey, String playlistId, Function<AudioTrackInfo, AudioTrack> trackFactory,
                                                  List<AudioTrack> tracks) throws IOException {
        final YoutubePlaylistMetadata nextPage = getPlaylistPageById(playlistId, this.apiKey, nextPageKey, false);

        if (nextPage == null) {
            return null;
        }

        nextPage.getTracks()
            .stream()
            .map(trackFactory)
            .forEach(tracks::add);

        return nextPage.getNextPageKey();
    }
}

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

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.*;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioTrack;
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import me.duncte123.botcommons.web.WebUtils;
import okhttp3.Request;

import javax.annotation.Nullable;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class YoutubeUtils {

    private static YouTube youtube;

    static {
        try {
            youtube = new YouTube.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(), JacksonFactory.getDefaultInstance(), null)
                    .setApplicationName("SkyBot-youtube-search")
                    .build();
        }
        catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
        }
    }

    public static Video getVideoById(String videoID, String apiKey) throws IOException {
        return getVideosByIdBase(videoID, apiKey)
                .setMaxResults(1L)
                .execute()
                .getItems()
                .get(0);
    }

    public static List<Video> getVideosByIds(String videoIds, String apiKey) throws IOException {
        return getVideosByIdBase(videoIds, apiKey)
                .execute()
                .getItems();
    }

    public static List<SearchResult> searchYoutubeIdOnly(String query, String apiKey, long size) throws IOException {
        return youtube.search().list("id")
                .setKey(apiKey)
                .setQ(query)
                .setType("video")
                .setMaxResults(size)
                .execute()
                .getItems();
    }

    public static List<SearchResult> searchYoutube(String query, String apiKey, long size) throws IOException {
        return youtube.search().list("snippet")
                .setKey(apiKey)
                .setQ(query)
                .setType("video")
                .setFields("items(id/kind,id/videoId,snippet/title)")
                .setMaxResults(size)
                .execute()
                .getItems();
    }

    @Nullable
    public static YoutubePlaylistMetadata getPlaylistPageById(String playlistId, String apiKey, @Nullable String nextPageKey, boolean withExtraData) throws IOException {
        String title = "";

        if (withExtraData) {
            title = getPlayListName(playlistId, apiKey);
        }

        if (title == null) {
            return null;
        }

        final PlaylistItemListResponse playlistItems = youtube.playlistItems()
                .list("snippet,contentDetails")
                .setPageToken(nextPageKey)
                .setPlaylistId(playlistId)
                .setMaxResults(20L)
//            .setMaxResults(1L)
                .setKey(apiKey)
                .execute();

        final List<PlaylistItem> items = playlistItems.getItems();

        if (items.isEmpty()) {
            return new YoutubePlaylistMetadata(playlistId, title, null, new ArrayList<>());
        }

        final List<AudioTrackInfo> changedItems = items.stream()
                .map((playlistItem) -> playListItemToTrackInfo(playlistItem, apiKey))
                .collect(Collectors.toList());

        return new YoutubePlaylistMetadata(playlistId, title, playlistItems.getNextPageToken(), changedItems);
    }

    public static String getThumbnail(Video video) {
        return getThumbnail(video.getId());
    }

    public static String getThumbnail(String videoID) {
        return "https://i.ytimg.com/vi/" + videoID + "/hq720.jpg";
    }

    public static AudioTrackInfo videoToTrackInfo(Video video) {
        final VideoSnippet snippet = video.getSnippet();
        final VideoContentDetails details = video.getContentDetails();

        return new AudioTrackInfo(
                snippet.getTitle(),
                snippet.getChannelTitle(),
                Duration.parse(details.getDuration()).toMillis(),
                video.getId(),
                false,
                "https://www.youtube.com/watch?v=" + video.getId()
        );
    }

    public static AudioTrackInfo playListItemToTrackInfo(PlaylistItem playlistItem, String apiKey) {
        try {
            final String videoId = playlistItem.getContentDetails().getVideoId();
            final Video video = getVideoById(videoId, apiKey);

            return videoToTrackInfo(video);
        }
        catch (IOException e) {
            e.printStackTrace();
            return null; // Should never happen tbh
        }
    }

    public static YoutubeAudioTrack videoToTrack(Video video, YoutubeAudioSourceManager sourceManager) {
        return new YoutubeAudioTrack(videoToTrackInfo(video), sourceManager);
    }

    public static YoutubeVersionData getYoutubeHeaderDetails() throws IOException {
        final Request request = WebUtils.defaultRequest()
                .url("https://www.youtube.com/")
                .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/78.0.3904.108 Safari/537.36")
                .build();

        final String html = WebUtils.ins.prepareRaw(request, (response) -> response.body().string()).execute();

        final String extracted = DataFormatTools.extractBetween(html,
                "window.ytplayer = {};ytcfg.set(",
                ");ytcfg.set(");
        final JsonBrowser json = JsonBrowser.parse(extracted);

        return YoutubeVersionData.fromBrowser(json);
    }

    private static YouTube.Videos.List getVideosByIdBase(String videoIds, String apiKey) throws IOException {
        return youtube.videos().list("id,snippet,contentDetails")
                .setId(videoIds)
                .setKey(apiKey)
                .setFields("items(id/*,snippet/title,snippet/channelTitle,contentDetails/duration)");
    }

    /**
     * Gets the name for a playlist
     * <p>
     * IMPORTANT: returns null if the playlist does not exist
     */
    @Nullable
    private static String getPlayListName(String playlistId, String apiKey) throws IOException {
        final List<Playlist> playlists = youtube.playlists()
                .list("snippet")
                .setId(playlistId)
                .setKey(apiKey)
                .execute()
                .getItems();

        if (playlists.isEmpty()) {
            return null;
        }

        final Playlist playlist = playlists.get(0);

        return playlist.getSnippet().getTitle();
    }

    /*private static YoutubeTrack searchCache(String title, String author, CacheClient cacheClient) {
        final SearchParams params = new SearchParams()
            .setSearch(title + " " + author)
            .setTitle(title.split("\\s+"))
            .setAuthor(author.split("\\s+"));

        final List<YoutubeTrack> found = cacheClient.search(params);

        if (found.isEmpty()) {
            return null;
        }

        return found.get(0);
    }

    private static Video cacheToYoutubeVideo(YoutubeTrack track) {
        return new Video()
            .setId(track.getId())
            .setKind("youtube#video")
            .setSnippet(
                new VideoSnippet()
                    .setTitle(track.getTitle())
                    .setChannelTitle(track.getAuthor())
            )
            .setContentDetails(
                new VideoContentDetails()
                    .setDuration(
                        Duration.ofMillis(track.getLength()).toString()
                    )
            );
    }*/
}

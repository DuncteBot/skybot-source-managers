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

package com.dunctebot.sourcemanagers.spotify;

import com.dunctebot.sourcemanagers.AudioTrackInfoWithImage;
import com.dunctebot.sourcemanagers.extra.LimitReachedException;
import com.google.api.services.youtube.model.SearchResult;
import com.google.api.services.youtube.model.Video;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity;
import com.sedmelluq.discord.lavaplayer.track.*;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.credentials.ClientCredentials;
import com.wrapper.spotify.model_objects.specification.*;
import com.wrapper.spotify.requests.authorization.client_credentials.ClientCredentialsRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.dunctebot.sourcemanagers.extra.YoutubeUtils.*;

public class SpotifyAudioSourceManager implements AudioSourceManager {

    private static final Logger logger = LoggerFactory.getLogger(SpotifyAudioSourceManager.class);

    private static final String PROTOCOL_REGEX = "?:spotify:(track:)|(?:http://|https://)[a-z]+\\.";
    private static final String DOMAIN_REGEX = "spotify\\.com/";
    private static final String TRACK_REGEX = "track/([a-zA-z0-9]+)";
    private static final String ALBUM_REGEX = "album/([a-zA-z0-9]+)";
    private static final String USER_PART = "user/(?:.*)/";
    private static final String PLAYLIST_REGEX = "playlist/([a-zA-z0-9]+)";
    private static final String REST_REGEX = "(?:.*)";
    private static final String SPOTIFY_BASE_REGEX = PROTOCOL_REGEX + DOMAIN_REGEX;

    private static final Pattern SPOTIFY_TRACK_REGEX = Pattern.compile("^(" + SPOTIFY_BASE_REGEX + TRACK_REGEX + ")" + REST_REGEX + "$");
    private static final Pattern SPOTIFY_ALBUM_REGEX = Pattern.compile("^(" + SPOTIFY_BASE_REGEX + ALBUM_REGEX + ")" + REST_REGEX + "$");
    private static final Pattern SPOTIFY_PLAYLIST_REGEX = Pattern.compile("^(" + SPOTIFY_BASE_REGEX + ")" + PLAYLIST_REGEX + REST_REGEX + "$");
    private static final Pattern SPOTIFY_PLAYLIST_REGEX_USER = Pattern.compile("^(" + SPOTIFY_BASE_REGEX + ")" +
        USER_PART + PLAYLIST_REGEX + REST_REGEX + "$");
    private static final Pattern SPOTIFY_SECOND_PLAYLIST_REGEX = Pattern.compile("^(?:spotify:user:)(?:.*)(?::playlist:)(.*)$");
    private final SpotifyApi spotifyApi;
    private final YoutubeAudioSourceManager youtubeAudioSourceManager;
    private final ScheduledExecutorService service;
    /*private final String spotifyClientId;
    private final String spotifyClientSecret;*/
    private final String youtubeApiKey;
    private final int playlistLimit;

    public SpotifyAudioSourceManager(YoutubeAudioSourceManager youtubeAudioSourceManager,
                                    String spotifyClientId, String spotifyClientSecret, String youtubeApiKey,
                                     int playlistLimit) {
        /*this.spotifyClientId = spotifyClientId;
        this.spotifyClientSecret = spotifyClientSecret;*/
        this.youtubeApiKey = youtubeApiKey;
        this.playlistLimit = playlistLimit;

        if (spotifyClientId == null || spotifyClientSecret == null || youtubeApiKey == null) {
            logger.error("Could not load Spotify keys");
            this.spotifyApi = null;
            this.service = null;
            this.youtubeAudioSourceManager = null;
        } else {
            this.youtubeAudioSourceManager = youtubeAudioSourceManager;
            this.spotifyApi = new SpotifyApi.Builder()
                .setClientId(spotifyClientId)
                .setClientSecret(spotifyClientSecret)
                .build();

            this.service = Executors.newScheduledThreadPool(2, (r) -> new Thread(r, "Spotify-Token-Update-Thread"));
            service.scheduleAtFixedRate(this::updateAccessToken, 0, 1, TimeUnit.HOURS);
        }
    }

    @Override
    public String getSourceName() {
        return "spotify";
    }

    @Override
    public AudioItem loadItem(DefaultAudioPlayerManager manager, AudioReference reference) {
        return loadItem(reference, false);
    }

    public AudioItem loadItem(AudioReference reference, boolean isPatron) {

        AudioItem item = getSpotifyAlbum(reference);

        if (item == null) {
            item = getSpotifyPlaylist(reference, isPatron);
        }

        if (item == null) {
            item = getSpotifyTrack(reference);
        }

        return item;
    }

    private AudioItem getSpotifyAlbum(AudioReference reference) {
        final Matcher res = SPOTIFY_ALBUM_REGEX.matcher(reference.identifier);

        if (!res.matches()) {
            return null;
        }

        try {
            final List<String> videoIDs = new ArrayList<>();
            final Future<Album> albumFuture = this.spotifyApi.getAlbum(res.group(res.groupCount())).build().executeAsync();
            final Album album = albumFuture.get();

            for (final TrackSimplified t : album.getTracks().getItems()) {
                final String videoId = searchYoutube(t.getName(), album.getArtists()[0].getName());

                if (videoId != null) {
                    videoIDs.add(videoId);
                }
            }

            final List<AudioTrack> playList = getTrackListFromVideoIds(videoIDs, album.getImages());

            return new BasicAudioPlaylist(album.getName(), playList, playList.get(0), false);
        }
        catch (Exception e) {
            //logger.error("Something went wrong!", e);
            throw new FriendlyException(e.getMessage(), Severity.FAULT, e);
        }
    }

    private AudioItem getSpotifyPlaylist(AudioReference reference, boolean isPatron) {

        final Matcher res = getSpotifyPlaylistFromString(reference.identifier);

        if (!res.matches()) {
            return null;
        }

        final String playListId = res.group(res.groupCount());

        try {
            final List<String> videoIDs = new ArrayList<>();

            final Playlist spotifyPlaylist = this.spotifyApi.getPlaylist(playListId).build().execute();
            final PlaylistTrack[] playlistTracks = spotifyPlaylist.getTracks().getItems();

            if (playlistTracks.length == 0) {
                return null;
            }

            if (playlistTracks.length > this.playlistLimit && !isPatron) {
                throw new LimitReachedException("The playlist is too big", this.playlistLimit);
            }

            for (final PlaylistTrack playlistTrack : playlistTracks) {
                if (playlistTrack.getIsLocal()) {
                    continue;
                }

                final Track track = playlistTrack.getTrack();
                final String videoId = searchYoutube(track.getName(), track.getArtists()[0].getName());

                if (videoId != null) {
                    videoIDs.add(videoId);
                }
            }

            final List<AudioTrack> finalPlaylist = getTrackListFromVideoIds(videoIDs, spotifyPlaylist.getImages());

            return new BasicAudioPlaylist(spotifyPlaylist.getName(), finalPlaylist, finalPlaylist.get(0), false);
        }
        catch (IllegalArgumentException ex) {
            throw new FriendlyException("This playlist could not be loaded, make sure that it's public", Severity.COMMON, ex);
        }
        catch (LimitReachedException e) {
            throw e;
        }
        catch (Exception e) {
            //logger.error("Something went wrong!", e);
            throw new FriendlyException(e.getMessage(), Severity.FAULT, e);
        }

    }

    private AudioItem getSpotifyTrack(AudioReference reference) {

        final Matcher res = SPOTIFY_TRACK_REGEX.matcher(reference.identifier);

        if (!res.matches()) {
            return null;
        }

        try {
            final Track track = this.spotifyApi.getTrack(res.group(res.groupCount())).build().execute();
            final String videoId = searchYoutube(track.getName(), track.getArtists()[0].getName());

            if (videoId == null) {
                return null;
            }

            final Video v = getVideoById(videoId, this.youtubeApiKey);

            return audioTrackFromVideo(v, track.getAlbum().getImages());
        }
        catch (Exception e) {
            //logger.error("Something went wrong!", e);
            throw new FriendlyException(e.getMessage(), Severity.FAULT, e);
        }
    }

    @Override
    public boolean isTrackEncodable(AudioTrack track) {
        return true;
    }

    @Override
    public void encodeTrack(AudioTrack track, DataOutput output) {
        // Nothing to encode
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) {
        return new SpotifyAudioTrack(trackInfo, this.youtubeAudioSourceManager);
    }

    @Override
    public void shutdown() {
        if (this.service != null) {
            this.service.shutdown();
        }

    }

    private void updateAccessToken() {
        try {
            final ClientCredentialsRequest request = this.spotifyApi.clientCredentials().build();
            final ClientCredentials clientCredentials = request.execute();

            // Set access token for further "spotifyApi" object usage
            this.spotifyApi.setAccessToken(clientCredentials.getAccessToken());

            logger.debug("Successfully retrieved an access token! " + clientCredentials.getAccessToken());
            logger.debug("The access token expires in " + clientCredentials.getExpiresIn() + " seconds");
        }
        catch (IOException | SpotifyWebApiException e) {
            e.printStackTrace();
            logger.error("Error while fetching Spotify token", e);

            // Retry after 10 seconds
            this.service.schedule(this::updateAccessToken, 10L, TimeUnit.SECONDS);
        }
    }

    private Matcher getSpotifyPlaylistFromString(String input) {
        final Matcher match = SPOTIFY_PLAYLIST_REGEX.matcher(input);

        if (match.matches()) {
            return match;
        }

        final Matcher withUser = SPOTIFY_PLAYLIST_REGEX_USER.matcher(input);

        if (withUser.matches()) {
            return withUser;
        }

        return SPOTIFY_SECOND_PLAYLIST_REGEX.matcher(input);
    }

    private List<AudioTrack> getTrackListFromVideoIds(List<String> videoIds, Image[] images) throws Exception {
        final String videoIdsJoined = String.join(",", videoIds);
        final List<Video> videosByIds = getVideosByIds(videoIdsJoined, this.youtubeApiKey);
        final List<AudioTrack> playList = new ArrayList<>();

        videosByIds.forEach((video) -> playList.add(audioTrackFromVideo(video, images)));

        return playList;
    }

    @Nullable
    private String searchYoutube(String title, String author) throws IOException {
        final List<SearchResult> results = searchYoutubeIdOnly(title + " " + author, this.youtubeApiKey, 1L);

        if (!results.isEmpty()) {
            return results.get(0).getId().getVideoId();
        }

        return null;
    }

    private AudioTrack audioTrackFromVideo(Video v, Image[] images) {
        return new SpotifyAudioTrack(new AudioTrackInfoWithImage(
            v.getSnippet().getTitle(),
            v.getSnippet().getChannelTitle(),
            toLongDuration(v.getContentDetails().getDuration()),
            v.getId(),
            false,
            "https://youtube.com/watch?v=" + v.getId(),
            imageUrlOrThumbnail(images, v)
        ), this.youtubeAudioSourceManager);
    }

    private String imageUrlOrThumbnail(Image[] images, Video video) {
        if (images.length > 0) {
            return images[0].getUrl();
        }

        return getThumbnail(video);
    }

    private long toLongDuration(String dur) {
        return Duration.parse(dur).toMillis();
    }
}

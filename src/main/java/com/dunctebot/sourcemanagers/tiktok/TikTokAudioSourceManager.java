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

package com.dunctebot.sourcemanagers.tiktok;

import com.dunctebot.sourcemanagers.AbstractDuncteBotHttpSource;
import com.dunctebot.sourcemanagers.AudioTrackInfoWithImage;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.dunctebot.sourcemanagers.Utils.fakeChrome;

public class TikTokAudioSourceManager extends AbstractDuncteBotHttpSource {
    private static final String BASE = "https:\\/\\/(?:www\\.)?tiktok\\.com";
    private static final String USER = "@(?<user>[a-z0-9A-Z]+)";
    private static final String VIDEO = "(?<video>[0-9]+)";
    private static final Pattern VIDEO_REGEX = Pattern.compile("^" + BASE + "\\/" + USER + "\\/video\\/" + VIDEO + "(?:.*)$");
    private static final Pattern JS_REGEX = Pattern.compile(
        "<script id=\"__NEXT_DATA__\" type=\"application/json\" crossorigin=\"anonymous\">(.*)<\\/script>");

    public TikTokAudioSourceManager() {
        super(false);
    }

    @Override
    public String getSourceName() {
        return "tiktok";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        final Matcher matcher = VIDEO_REGEX.matcher(reference.identifier);

        if (!matcher.matches()) {
            return null;
        }

        final String user = matcher.group("user");
        final String video = matcher.group("video");

        try {
            final MetaData metaData = extractData(user, video);

            return new TikTokAudioTrack(metaData.toTrackInfo(), this);
        } catch (Exception e) {
            throw ExceptionTools.wrapUnfriendlyExceptions("Something went wrong", Severity.SUSPICIOUS, e);
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
        return new TikTokAudioTrack(trackInfo, this);
    }

    private MetaData extractData(String userId, String videoId) throws IOException {
        return extractData("https://www.tiktok.com/@" + userId + "/video/" + videoId);
    }

    protected MetaData extractData(String url) throws IOException {
        final JsonBrowser json = extractDataRaw(url);
        final JsonBrowser base = json.get("props").get("pageProps").get("itemInfo").get("itemStruct");
        final MetaData metaData = new MetaData();

        final JsonBrowser video = base.get("video");

        metaData.pageUrl = url;
        metaData.videoId = video.get("id").safeText();
        metaData.cover = video.get("cover").safeText();
        metaData.title = video.get("desc").safeText();

        metaData.uri = video.get("playAddr").safeText();
        metaData.duration = Integer.parseInt(video.get("duration").safeText());

        final JsonBrowser author = base.get("author");

        metaData.uniqueId = author.get("uniqueId").safeText();

        System.out.println(metaData);

        return metaData;
    }

    protected JsonBrowser extractDataRaw(String url) throws IOException {
        final HttpGet httpGet = new HttpGet(url);

        fakeChrome(httpGet);

        try (final CloseableHttpResponse response = getHttpInterface().execute(httpGet)) {
            final int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode != 200) {
                if (statusCode == 302) { // most likely a 404
                    return null;
                }

                throw new IOException("Unexpected status code for video page response: " + statusCode);
            }

            final String html = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            final Matcher matcher = JS_REGEX.matcher(html);

            if (!matcher.find()) {
                // TODO: temp
                System.out.println(html);
                throw new FriendlyException("Failed to find data for tiktok video", Severity.SUSPICIOUS, null);
            }

            return JsonBrowser.parse(matcher.group(1).trim());
        }
    }

    private static class MetaData {
        // video
        String cover; // image url
        String pageUrl;
        String videoId;
        String uri;
        int duration; // in seconds
        String title;

        // author
        String uniqueId;

        AudioTrackInfoWithImage toTrackInfo() {
            return new AudioTrackInfoWithImage(
                this.title,
                this.uniqueId,
                this.duration * 1000L,
                this.videoId,
                false,
                this.pageUrl,
                this.cover
            );
        }

        // TEMP
        @Override
        public String toString() {
            return "MetaData{" +
                "cover='" + cover + '\'' +
                ", pageUrl='" + pageUrl + '\'' +
                ", videoId='" + videoId + '\'' +
                ", uri='" + uri + '\'' +
                ", duration=" + duration +
                ", title='" + title + '\'' +
                ", uniqueId='" + uniqueId + '\'' +
                '}';
        }
    }
}

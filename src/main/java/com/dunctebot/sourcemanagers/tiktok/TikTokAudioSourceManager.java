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
import com.sedmelluq.discord.lavaplayer.tools.http.HttpContextFilter;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.dunctebot.sourcemanagers.Utils.fakeChrome;

public class TikTokAudioSourceManager extends AbstractDuncteBotHttpSource {
    private static final String BASE = "https:\\/\\/(?:www\\.|m\\.)?tiktok\\.com";
    private static final String USER = "@(?<user>[a-z0-9A-Z]+)";
    private static final String VIDEO = "(?<video>[0-9]+)";
    private static final Pattern VIDEO_REGEX = Pattern.compile("^" + BASE + "\\/" + USER + "\\/video\\/" + VIDEO + "(?:.*)$");
    private static final Pattern JS_REGEX = Pattern.compile(
        "<script id=\"__NEXT_DATA__\" type=\"application/json\" crossorigin=\"anonymous\">(.*)<\\/script>");

    public TikTokAudioSourceManager() {
        super(false);

        this.httpInterfaceManager.setHttpContextFilter(new TikTokFilter());
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

        return getMetaData(url, base);
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

    protected MetaData extractFromJson(String url) throws IOException {
        final Matcher matcher = VIDEO_REGEX.matcher(url);

        if (!matcher.matches()) {
            throw new IOException("Url does not match tiktok anymore? wtf");
        }

        final String user = matcher.group("user");
        final String video = matcher.group("video");

        final HttpGet httpGet = new HttpGet(
            "https://www.tiktok.com/node/share/video/@" + user + '/' + video
        );

        fakeChrome(httpGet);

        try (final CloseableHttpResponse response = getHttpInterface().execute(httpGet)) {
            final int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode != 200) {
                if (statusCode == 302) { // most likely a 404
                    return null;
                }

                throw new IOException("Unexpected status code for video page response: " + statusCode);
            }

            final String string = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            final JsonBrowser json = JsonBrowser.parse(string);
            final JsonBrowser base = json.get("itemInfo").get("itemStruct");

            return getMetaData(url, base);
        }
    }

    private MetaData getMetaData(String url, JsonBrowser base) {
        final MetaData metaData = new MetaData();

        final JsonBrowser videoJson = base.get("video");

        metaData.pageUrl = url;
        metaData.videoId = videoJson.get("id").safeText();
        metaData.cover = videoJson.get("cover").safeText();
        metaData.title = base.get("desc").safeText();

        metaData.uri = videoJson.get("playAddr").safeText();
        metaData.duration = Integer.parseInt(videoJson.get("duration").safeText());

        final JsonBrowser author = base.get("author");

        metaData.uniqueId = author.get("uniqueId").safeText();

        System.out.println(metaData);

        return metaData;
    }

    protected static class MetaData {
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

    public static class TikTokFilter implements HttpContextFilter {
        @Override
        public void onContextOpen(HttpClientContext context) {
            //
        }

        @Override
        public void onContextClose(HttpClientContext context) {
            // Not used
        }

        @Override
        public void onRequest(HttpClientContext context, HttpUriRequest request, boolean isRepetition) {
            request.setHeader("Referer", "https://www.tiktok.com/");
            request.setHeader("Cookie", "tt_webid_v2=68" + makeId(16));

            final String ua = fakeUserAgent();

            System.out.println("NEW USER AGENT " + ua);

            request.setHeader("User-Agent", ua);

        }

        @Override
        public boolean onRequestResponse(HttpClientContext context, HttpUriRequest request, HttpResponse response) {
            return false;
        }

        @Override
        public boolean onRequestException(HttpClientContext context, HttpUriRequest request, Throwable error) {
            return false;
        }

        private static String makeId(int length) {
            final StringBuilder sb = new StringBuilder();
            final String charList = "0123456789";

            for (int i = 0; i < length; i += 1) {
                sb.append(charList.charAt((int) Math.floor(Math.random() * charList.length())));
            }

            return sb.toString();
        }

        private static String fakeUserAgent() {
            final String[] parts = {
                "Macintosh; Intel Mac OS X 10_15_7",
                "Macintosh; Intel Mac OS X 10_15_5",
                "Macintosh; Intel Mac OS X 10_11_6",
                "Macintosh; Intel Mac OS X 10_6_6",
                "Macintosh; Intel Mac OS X 10_9_5",
                "Macintosh; Intel Mac OS X 10_10_5",
                "Macintosh; Intel Mac OS X 10_7_5",
                "Macintosh; Intel Mac OS X 10_11_3",
                "Macintosh; Intel Mac OS X 10_10_3",
                "Macintosh; Intel Mac OS X 10_6_8",
                "Macintosh; Intel Mac OS X 10_10_2",
                "Macintosh; Intel Mac OS X 10_10_3",
                "Macintosh; Intel Mac OS X 10_11_5",
                "Windows NT 10.0; Win64; x64",
                "Windows NT 10.0; WOW64",
                "Windows NT 10.0",
            };

            final ThreadLocalRandom rnd = ThreadLocalRandom.current();

            return "Mozilla/5.0 (" + parts[rnd.nextInt(parts.length)] + ") AppleWebKit/537.36 (KHTML, like Gecko) Chrome/" +
                (rnd.nextInt(3) + 85) + ".0." + (rnd.nextInt(190) + 4100) + '.' + (rnd.nextInt(50) + 140) +
                "Safari/537.36";
        }
    }
}

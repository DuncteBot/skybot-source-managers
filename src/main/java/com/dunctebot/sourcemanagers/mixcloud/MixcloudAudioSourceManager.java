/*
 * Copyright 2022 Duncan "duncte123" Sterken & devoxin
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

package com.dunctebot.sourcemanagers.mixcloud;

import com.dunctebot.sourcemanagers.AbstractDuncteBotHttpSource;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
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

import static com.dunctebot.sourcemanagers.Utils.urlDecode;
import static com.dunctebot.sourcemanagers.Utils.urlEncode;

public class MixcloudAudioSourceManager extends AbstractDuncteBotHttpSource {
    private static final String REQUEST_STRUCTURE = "audioLength\n" +
        "    name\n" +
        "    owner {\n" +
        "      username\n" +
        "    }\n" +
        "    streamInfo {\n" +
        "      dashUrl\n" +
        "      hlsUrl\n" +
        "      url\n" +
        "    }";
    private static final Pattern URL_REGEX = Pattern.compile("https?://(?:(?:www|beta|m)\\.)?mixcloud\\.com/([^/]+)/(?!stream|uploads|favorites|listens|playlists)([^/]+)/?");

    @Override
    public String getSourceName() {
        return "mixcloud";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        final Matcher matcher = URL_REGEX.matcher(reference.identifier);

        if (!matcher.matches()) {
            return null;
        }

        // retry if possible
        while (true) {
            try {
                return this.loadItemOnce(reference, matcher);
            } catch (Exception e) {
                if (!HttpClientTools.isRetriableNetworkException(e)) {
                    throw ExceptionTools.wrapUnfriendlyExceptions(
                        "Loading information for a MixCloud track failed.",
                        FriendlyException.Severity.FAULT, e);
                }
            }
        }
    }

    private AudioItem loadItemOnce(AudioReference reference, Matcher matcher) throws IOException {
        final String username = urlDecode(matcher.group(1));
        final String slug = urlDecode(matcher.group(2));
        final JsonBrowser trackInfo = this.extractTrackInfoGraphQl(username, slug);

        if (trackInfo == null) {
            return AudioReference.NO_TRACK;
        }

        final String title = trackInfo.get("name").text();
        final long duration = trackInfo.get("audioLength").as(Long.class) * 1000;
        final String uploader = trackInfo.get("owner").get("username").text(); // displayName

        return new MixcloudAudioTrack(
            new AudioTrackInfo(
                title,
                uploader,
                duration,
                slug,
                false,
                reference.identifier
            ),
            this
        );
    }

    protected JsonBrowser extractTrackInfoGraphQl(String username, String slug) throws IOException {
        final String slugFormatted = slug == null ? "" : String.format(", slug: \"%s\"", slug);
        final String query = String.format(
            "{\n  cloudcastLookup(lookup: {username: \"%s\"%s}) {\n    %s\n  }\n}",
            username,
            slugFormatted,
            REQUEST_STRUCTURE
        );
        final String encodedQuery = urlEncode(query);
        final HttpGet httpGet = new HttpGet("https://www.mixcloud.com/graphql?query=" + encodedQuery);

        try (final CloseableHttpResponse res = getHttpInterface().execute(httpGet)) {
            final int statusCode = res.getStatusLine().getStatusCode();

            if (statusCode != 200) {
                if (statusCode == 404) {
                    return null;
                }

                throw new IOException("Invalid status code for Mixcloud track page response: " + statusCode);
            }

            final String content = IOUtils.toString(res.getEntity().getContent(), StandardCharsets.UTF_8);
            final JsonBrowser json = JsonBrowser.parse(content).get("data").get("cloudcastLookup");

            if (json.get("streamInfo").isNull()) {
                return null;
            }

            return json;
        }
    }

    @Override
    public boolean isTrackEncodable(AudioTrack track) {
        return true;
    }

    @Override
    public void encodeTrack(AudioTrack track, DataOutput output) {
        // nothing to encode
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) {
        return new MixcloudAudioTrack(trackInfo, this);
    }
}

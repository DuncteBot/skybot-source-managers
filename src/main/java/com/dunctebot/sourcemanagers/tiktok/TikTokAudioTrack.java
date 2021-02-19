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
import com.dunctebot.sourcemanagers.MpegTrack;
import com.dunctebot.sourcemanagers.tiktok.TikTokAudioSourceManager.MetaData;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;

import static com.dunctebot.sourcemanagers.Utils.fakeChrome;
import static com.dunctebot.sourcemanagers.tiktok.TikTokAudioSourceManager.VIDEO_REGEX;
import static com.dunctebot.sourcemanagers.tiktok.TikTokAudioSourceManager.getMetaData;
import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

public class TikTokAudioTrack extends MpegTrack {
    private final TikTokAudioTrackHttpManager httpManager = new TikTokAudioTrackHttpManager();

    public TikTokAudioTrack(AudioTrackInfo trackInfo, AbstractDuncteBotHttpSource manager) {
        super(trackInfo, manager);
    }

    @Override
    protected String getPlaybackUrl() {
        try {
            final MetaData metaData = extractFromJson(this.trackInfo.uri);

            return metaData.uri;
        } catch (IOException e) {
            throw new FriendlyException("Could not load TikTok video", SUSPICIOUS, e);
        }
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        this.httpManager.loadCookies();

        try (HttpInterface httpInterface = this.httpManager.getHttpInterface()) {
            loadStream(executor, httpInterface);
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

        try (final CloseableHttpResponse response = this.httpManager.getHttpInterface().execute(httpGet)) {
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

    @Override
    protected AudioTrack makeShallowClone() {
        return new TikTokAudioTrack(this.trackInfo, getSourceManager());
    }
}

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

package com.dunctebot.sourcemanagers.pornhub;

import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAudioTrack;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PornHubAudioTrack extends DelegatedAudioTrack {
    private static final Pattern MEDIA_STRING = Pattern.compile("(var\\s+?mediastring.+?)<\\/script>");
    private static final Pattern MEDIA_STRING_FILTER = Pattern.compile("\\/\\* \\+ [a-zA-Z0-9_]+ \\+ \\*\\/");

    private final PornHubAudioSourceManager sourceManager;

    public PornHubAudioTrack(AudioTrackInfo trackInfo, PornHubAudioSourceManager sourceManager) {
        super(trackInfo);
        this.sourceManager = sourceManager;
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        if (!this.trackInfo.identifier.equals(this.trackInfo.uri)) {
            return;
        }

        final String playbackUrl = loadTrackUrl(this.trackInfo, this.sourceManager.getHttpInterface());

        try (final PersistentHttpStream stream = new PersistentHttpStream(this.sourceManager.getHttpInterface(), new URI(playbackUrl), Long.MAX_VALUE)) {
            processDelegate(
                new MpegAudioTrack(this.trackInfo, stream),
                executor
            );
        }
    }

    private static String loadTrackUrl(AudioTrackInfo trackInfo, HttpInterface httpInterface) throws IOException {
        final HttpGet httpGet = new HttpGet(trackInfo.identifier);

        httpGet.setHeader("Cookie", "platform=tv");

        try (final CloseableHttpResponse response = httpInterface.execute(httpGet)) {
            final String html = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            final Matcher matcher = MEDIA_STRING.matcher(html);

            if (!matcher.find()) {
                throw new FriendlyException("Could not find media info", FriendlyException.Severity.SUSPICIOUS, null);
            }

            final String js = matcher.group(matcher.groupCount());

            return parseJsValueToUrl(html, js);
        }
    }

    private static String parseJsValueToUrl(String htmlPage, String js) {
        final String filteredJsValue = MEDIA_STRING_FILTER.matcher(js).replaceAll("");
        final String variables = filteredJsValue.split("=")[1].split(";")[0];
        final String[] items = variables.split("\\+");
        final List<String> videoParts = new ArrayList<>();

        for (final String i : items) {
            final String item = i.trim();
            final String regex = "var\\s+?" + item + "=\"([a-zA-Z0-9=?&%~_\\-\\.\\/\"\\+: ]+)\";";
            final Pattern pattern = Pattern.compile(regex);
            final Matcher matcher = pattern.matcher(htmlPage);

            if (!matcher.find()) {
                System.out.println(htmlPage);
                throw new FriendlyException("URL part " + item + " missing", FriendlyException.Severity.SUSPICIOUS, null);
            }

            videoParts.add(
                matcher.group(matcher.groupCount()).replaceAll("\"\\s+?\\+\\s+?\"", "")
            );
        }

        return String.join("", videoParts);
    }

    @Override
    public PornHubAudioSourceManager getSourceManager() {
        return sourceManager;
    }
}

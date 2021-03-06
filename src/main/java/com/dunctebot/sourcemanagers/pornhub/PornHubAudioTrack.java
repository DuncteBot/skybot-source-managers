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

package com.dunctebot.sourcemanagers.pornhub;

import com.dunctebot.sourcemanagers.AbstractDuncteBotHttpSource;
import com.dunctebot.sourcemanagers.MpegTrack;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.apache.commons.io.IOUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.dunctebot.sourcemanagers.pornhub.PornHubAudioSourceManager.getPlayerPage;
import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.*;

public class PornHubAudioTrack extends MpegTrack {
    private static final String[] FORMAT_PREFIXES = {"media", "quality", "qualityItems"};
    private static final String FORMAT_REGEX = String.format("(var\\s+(?:%s)_.+)", String.join("|", FORMAT_PREFIXES));
    private static final Pattern FORMAT_PATTERN = Pattern.compile(FORMAT_REGEX);
    private static final Pattern MEDIA_STRING = Pattern.compile("(var\\s+?mediastring.+?)<\\/script>");
    private static final Pattern MEDIA_STRING_FILTER = Pattern.compile("\\/\\* \\+ [a-zA-Z0-9_]+ \\+ \\*\\/");
    private static final Pattern VIDEO_SHOW = Pattern.compile("var\\s+?VIDEO_SHOW\\s+?=\\s+?([^;]+);?<\\/script>");

    public PornHubAudioTrack(AudioTrackInfo trackInfo, AbstractDuncteBotHttpSource sourceManager) {
        super(trackInfo, sourceManager);
    }

    @Override
    protected String getPlaybackUrl() {
        try {
            return loadTrackUrl();
        } catch (IOException e) {
            throw new FriendlyException("Could not load PornHub video", SUSPICIOUS, e);
        }
    }

    private String loadTrackUrl_old() throws IOException {
        final HttpGet httpGet = new HttpGet(getPlayerPage(this.trackInfo.identifier));

        httpGet.setHeader("Cookie", "platform=tv");

        try (final CloseableHttpResponse response = this.getSourceManager().getHttpInterface().execute(httpGet)) {
            final String html = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            final Matcher matcher = MEDIA_STRING.matcher(html);

            if (matcher.find()) {
                final String js = matcher.group(matcher.groupCount());

                return parseJsValueToUrl(html, js);
            }

            final Matcher videoMatcher = VIDEO_SHOW.matcher(html);

            if (videoMatcher.find()) {
                final String cookies = Arrays.stream(response.getHeaders("Set-Cookie"))
                    .map(NameValuePair::getValue)
                    .map((s) -> s.split(";", 2)[0])
                    .collect(Collectors.joining("; "));
                final String js = videoMatcher.group(videoMatcher.groupCount());

                return extractVideoFromVideoShow(js, cookies);
            }

            throw new FriendlyException("Could not find media info", SUSPICIOUS, null);
        }
    }

    private String parseQualityItems(String json) throws IOException {
        final JsonBrowser parse = JsonBrowser.parse(json);

        if (!parse.isList()) {
            return null;
        }

        final JsonBrowser url = parse.values().stream()
            .filter((it) -> !it.get("url").safeText().isEmpty())
            .findFirst().orElse(null);

        if (url == null) {
            return null;
        }

        return url.get("url").text();
    }

    private String loadTrackUrl() throws IOException {
        final HttpGet httpGet = new HttpGet("https://www.pornhub.com/view_video.php?viewkey=" + this.trackInfo.identifier);

        try (final CloseableHttpResponse response = this.getSourceManager().getHttpInterface().execute(httpGet)) {
            final String html = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);

            if (Pattern.compile("<[^>]+\\bid=[\"']lockedPlayer").matcher(html).find()) {
                throw new FriendlyException("Video " + this.trackInfo.identifier + " is locked.", COMMON, null);
            }

            final Map<String, String> jsVars = extractJsVars(html, FORMAT_PATTERN);

            if (jsVars == null) {
                throw new FriendlyException("Could not load js vars", SUSPICIOUS, null);
            }

            for (final Map.Entry<String, String> entry : jsVars.entrySet()) {
                if (entry.getKey().startsWith("qualityItems")) {
                    final String playbackUrl = parseQualityItems(entry.getValue());

                    if (playbackUrl != null) {
                        return playbackUrl;
                    }
                }/* else if (entry.getKey().startsWith("media") || entry.getKey().startsWith("quality")) {
                    // TODO: these are probably broken anyway
                }*/
            }

            throw new FriendlyException("Could not extract video url", COMMON, null);
        }
    }

    private String parseJsValue(String input, Map<String, String> jsVars) {
        String inp = input.replaceAll("/\\*(?:(?!\\*/).)*?\\*/", "");

        if (input.contains("+")) {
            return Arrays.stream(input.split("\\+"))
                .map(s -> parseJsValue(s, jsVars))
                .collect(Collectors.joining(" "));
        }

        inp = inp.trim();

        if (jsVars.containsKey(inp)) {
            return jsVars.get(inp);
        }


        // can't remove quotes if less than 2 chars
        if (inp.length() < 2) {
            return inp;
        }

        // remove quotes
        if (
            (inp.charAt(0) == '"' && inp.charAt(inp.length() - 1) == '"') ||
                (inp.charAt(0) == '\'' && inp.charAt(inp.length() - 1) == '\'')
        ) {
            return inp.substring(1, inp.length() - 1);
        }

        return inp;
    }

    private Map<String, String> extractJsVars(String html, Pattern pattern) {
        final Matcher matcher = pattern.matcher(html);

        if (!matcher.find()) {
            return null;
        }

        final String[] assignments = matcher.group(1).split(";");
        final Map<String, String> jsVars = new HashMap<>();

        for (String assn : assignments) {
            assn = assn.trim();

            if (assn.isBlank()) {
                continue;
            }

            assn = assn.replaceFirst("var\\s+", "");
            final String[] parts = assn.split("=", 2);

            jsVars.put(parts[0], this.parseJsValue(parts[1], jsVars));
        }

        return jsVars;
    }

    private String parseJsValueToUrl(String htmlPage, String js) {
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
                throw new FriendlyException("URL part " + item + " missing", SUSPICIOUS, null);
            }

            videoParts.add(
                matcher.group(matcher.groupCount()).replaceAll("\"\\s+?\\+\\s+?\"", "")
            );
        }

        return String.join("", videoParts);
    }

    private String extractVideoFromVideoShow(String obj, String cookie) throws IOException {
        final JsonBrowser browser = JsonBrowser.parse(obj);
        final String mediaUrl = browser.get("mediaUrl").safeText();

        System.out.println("https://www.pornhub.com" + mediaUrl);

        final HttpGet mediaGet = new HttpGet("https://www.pornhub.com" + mediaUrl);

        mediaGet.setHeader("Cookie", cookie + "; quality=720; platform=tv");
        mediaGet.setHeader("Referer", getPlayerPage(this.trackInfo.identifier));

        try (final CloseableHttpResponse response = this.getSourceManager().getHttpInterface().execute(mediaGet)) {
            final String body = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);

            System.out.println("body " + body);

            final JsonBrowser json = JsonBrowser.parse(body);

            if (!"OK".equals(json.get("status").safeText())) {
                throw new FriendlyException("Pornhub video returned non OK status for video info", COMMON, null);
            }

            final String videoUrl = json.get("videoUrl").text();

            if (videoUrl == null) {
                throw new FriendlyException("Video url missing on playback page", FAULT, null);
            }

            return videoUrl;
        }
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new PornHubAudioTrack(trackInfo, getSourceManager());
    }
}

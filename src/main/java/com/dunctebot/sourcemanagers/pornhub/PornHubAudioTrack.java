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
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.CookieStore;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.cookie.BasicClientCookie;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.*;

public class PornHubAudioTrack extends MpegTrack {
    private static final String[] FORMAT_PREFIXES = {"media", "quality", "qualityItems"};
    private static final String FORMAT_REGEX = String.format("(var\\s+(?:%s)_.+)", String.join("|", FORMAT_PREFIXES));
    private static final Pattern FORMAT_PATTERN = Pattern.compile(FORMAT_REGEX);
    private static final Pattern MEDIA_STRING = Pattern.compile("(var\\s+?mediastring.+?)<\\/script>");
    private static final Pattern MEDIA_STRING_FILTER = Pattern.compile("\\/\\* \\+ [a-zA-Z0-9_]+ \\+ \\*\\/");
    private static final Pattern VIDEO_SHOW = Pattern.compile("var\\s+?VIDEO_SHOW\\s+?=\\s+?([^;]+);?<\\/script>");

    private final HttpInterfaceManager httpManager;

    public PornHubAudioTrack(AudioTrackInfo trackInfo, AbstractDuncteBotHttpSource sourceManager) {
        super(trackInfo, sourceManager);

        final CookieStore cookieStore = new BasicCookieStore();

        cookieStore.addCookie(new BasicClientCookie("platform", "tv"));
//        cookieStore.addCookie(new BasicClientCookie("age_verified", "1"));

        final HttpInterfaceManager manager = HttpClientTools.createDefaultThreadLocalManager();
        manager.configureBuilder(
            (config) -> config.setDefaultCookieStore(cookieStore)
        );

        this.httpManager = manager;
    }

    private HttpInterface getInterface() {
        return this.httpManager.getInterface();
    }

    @Override
    protected String getPlaybackUrl() {
        try {
            return loadTrackUrl(this.trackInfo);
        } catch (IOException e) {
            throw new FriendlyException("Could not load PornHub video", SUSPICIOUS, e);
        }
    }

    private String loadTrackUrl(AudioTrackInfo trackInfo) throws IOException {
        final HttpGet httpGet = new HttpGet("https://www.pornhub.com/view_video.php?viewkey=" + trackInfo.identifier);

        httpGet.setHeader("Cookie", "platform=tv");

        try (final CloseableHttpResponse response = this.getInterface().execute(httpGet)) {
            final String html = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            final Matcher matcher = MEDIA_STRING.matcher(html);

            if (matcher.find()) {
                final String js = matcher.group(matcher.groupCount());

                return parseJsValueToUrl(html, js);
            }

            final Matcher videoMatcher = VIDEO_SHOW.matcher(html);

            if (videoMatcher.find()) {
                final String js = videoMatcher.group(videoMatcher.groupCount());

                return extractVideoFromVideoShow(js, getInterface());
            }

            System.out.println(html);

            throw new FriendlyException("Could not find media info", SUSPICIOUS, null);
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

    private String loadTrackUrl_new(AudioTrackInfo trackInfo) throws IOException {
        final HttpGet httpGet = new HttpGet("https://www.pornhub.com/view_video.php?viewkey=" + trackInfo.identifier);

        try (final CloseableHttpResponse response = this.getInterface().execute(httpGet)) {
            final String html = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            final Map<String, String> jsVars = extractJsVars(html, FORMAT_PATTERN);

            if (jsVars == null) {
                throw new FriendlyException("Could not load media info", SUSPICIOUS, null);
            }

            for (final Map.Entry<String, String> entry : jsVars.entrySet()) {
                if (entry.getKey().startsWith("qualityItems")) {
                    //
                }
            }

            return "";
        }
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

    private String extractVideoFromVideoShow(String obj, HttpInterface httpInterface) throws IOException {
        final JsonBrowser browser = JsonBrowser.parse(obj);
        final String mediaUrl = browser.get("mediaUrl").safeText();

        System.out.println("https://www.pornhub.com" + mediaUrl);

        final HttpGet mediaGet = new HttpGet("https://www.pornhub.com" + mediaUrl);

        try (final CloseableHttpResponse response = httpInterface.execute(mediaGet)) {
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

    private HttpGet makeGet(String url) {
        final HttpGet httpGet = new HttpGet(url);

        httpGet.setHeader("Cookie", "platform=tv");

        return httpGet;
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new PornHubAudioTrack(trackInfo, getSourceManager());
    }
}

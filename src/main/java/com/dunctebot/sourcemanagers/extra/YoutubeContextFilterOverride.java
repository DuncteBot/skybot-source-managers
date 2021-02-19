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

package com.dunctebot.sourcemanagers.extra;

import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeHttpContextFilter;
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import io.sentry.Sentry;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YoutubeContextFilterOverride extends YoutubeHttpContextFilter implements Closeable {
    private static final Logger logger = LoggerFactory.getLogger(YoutubeContextFilterOverride.class);
    private static final Pattern YOUTUBE_INFO_REGEX = Pattern.compile("window\\.ytplayer=\\{\\};\\n?ytcfg\\.set\\((.*)\\);");
    private YoutubeVersionData youtubeVersionData = null;
    private final HttpInterface httpInterface;
    private final ScheduledExecutorService dataUpdateThread = Executors.newSingleThreadScheduledExecutor((r) -> {
        final Thread t = new Thread(r);
        t.setName("YouTube-data-updater");
        t.setDaemon(true);
        return t;
    });
    private static YoutubeContextFilterOverride INSTANCE;

    public synchronized static YoutubeContextFilterOverride getOrCreate(boolean shouldUpdate, HttpInterface httpInterface) {
        if (INSTANCE == null) {
            INSTANCE = new YoutubeContextFilterOverride(shouldUpdate, httpInterface);
        }

        return INSTANCE;
    }

    private YoutubeContextFilterOverride(boolean shouldUpdate, HttpInterface httpInterface) {
        this.httpInterface = httpInterface;

        if (shouldUpdate) {
            dataUpdateThread.scheduleAtFixedRate(this::updateYoutubeData, 0L, 1L, TimeUnit.DAYS);
        }
    }

    private void updateYoutubeData() {
        try {
            logger.info("Updating youtube version data");
            this.youtubeVersionData = getYoutubeHeaderDetails();
            logger.info("New youtube version data {}", this.youtubeVersionData);
        } catch (IOException e) {
            Sentry.capture(e);
            logger.error("Failed to capture youtube data", e);
        }
    }

    private YoutubeVersionData getYoutubeHeaderDetails() throws IOException {
        final HttpGet httpGet = new HttpGet("https://www.youtube.com/");
        httpGet.setHeader("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/78.0.3904.108 Safari/537.36");

        try (final CloseableHttpResponse response = this.httpInterface.execute(httpGet)) {
            final String html = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);

            final Matcher matcher = YOUTUBE_INFO_REGEX.matcher(html);

            if (matcher.find()) {
                final String extracted = matcher.group(matcher.groupCount())
                    .trim()
                    .replaceAll("undefined", "null");
                final JsonBrowser json = JsonBrowser.parse(extracted);

                return YoutubeVersionData.fromBrowser(json);
            }

            return null;
        }
    }

    @Override
    public void onRequest(HttpClientContext context, HttpUriRequest request, boolean isRepetition) {
        super.onRequest(context, request, isRepetition);

        if (youtubeVersionData == null) {
            return;
        }

        request.setHeader("x-youtube-client-version", youtubeVersionData.getVersion());
        request.setHeader("x-youtube-page-cl", youtubeVersionData.getPageCl());
        request.setHeader("x-youtube-page-label", youtubeVersionData.getLabel());
    }

    @Override
    public void close() {
        dataUpdateThread.shutdown();
    }
}

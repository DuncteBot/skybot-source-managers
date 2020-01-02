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

import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeHttpContextFilter;
import io.sentry.Sentry;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.dunctebot.sourcemanagers.extra.YoutubeUtils.getYoutubeHeaderDetails;

public class YoutubeContextFilterOverride extends YoutubeHttpContextFilter implements Closeable {
    private YoutubeVersionData youtubeVersionData = null;
    private final ScheduledExecutorService dataUpdateThread = Executors.newSingleThreadScheduledExecutor((r) -> {
        final Thread t = new Thread();
        t.setName("YouTube-data-updater");
        t.setDaemon(true);
        return t;
    });

    public YoutubeContextFilterOverride() {
        this(true);
    }

    public YoutubeContextFilterOverride(boolean shouldUpdate) {
        if (shouldUpdate) {
            dataUpdateThread.scheduleAtFixedRate(this::updateYoutubeData, 0L, 1L, TimeUnit.DAYS);
        }
    }

    private void updateYoutubeData() {
        try {
            this.youtubeVersionData = getYoutubeHeaderDetails();
        } catch (IOException e) {
            Sentry.capture(e);
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
        request.setHeader("x-youtube-variants-checksum", youtubeVersionData.getChecksum());
    }

    @Override
    public void close() throws IOException {
        dataUpdateThread.shutdown();
    }
}

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

import com.sedmelluq.discord.lavaplayer.tools.http.HttpContextFilter;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.dunctebot.sourcemanagers.Utils.fakeChrome;

public class TikTokAudioTrackHttpManager {
    private Header cookie = null;

    protected final HttpInterfaceManager httpInterfaceManager;

    public TikTokAudioTrackHttpManager() {
        httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();

        httpInterfaceManager.setHttpContextFilter(new TikTokFilter());
    }

    public HttpInterface getHttpInterface() {
        return httpInterfaceManager.getInterface();
    }

    protected void loadCookies() throws IOException {
        try (final HttpInterface httpInterface = httpInterfaceManager.getInterface()) {
            final HttpGet httpGet = new HttpGet("https://www.tiktok.com/");

            try (final CloseableHttpResponse response = httpInterface.execute(httpGet)) {
                final Header[] allHeaders = response.getAllHeaders();

                System.out.println(Arrays.toString(allHeaders));

                this.cookie = response.getLastHeader("set-cookie");
            }
        }
    }

    private class TikTokFilter implements HttpContextFilter {
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
            // set standard headers
            fakeChrome(request);

            if (cookie != null) {
                request.setHeader("cookie", cookie.getValue());
            }
        }

        @Override
        public boolean onRequestResponse(HttpClientContext context, HttpUriRequest request, HttpResponse response) {
            return false;
        }

        @Override
        public boolean onRequestException(HttpClientContext context, HttpUriRequest request, Throwable error) {
            return false;
        }
    }
}

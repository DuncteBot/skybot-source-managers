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

package com.dunctebot.sourcemanagers;

import org.apache.http.HttpRequest;

public class Utils {

    public static boolean isURL(String url) {
        return url.matches("^https?:\\/\\/[-a-zA-Z0-9+&@#\\/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#\\/%=~_|]");
    }

    public static void fakeChrome(HttpRequest request) {
        request.setHeader("dnt", "1");
        request.setHeader("upgrade-insecure-requests", "1");
        request.setHeader("Accept", "*/*");
        request.setHeader("Accept-Encoding", "identity;q=1, *;q=0");
        request.setHeader("Accept-Language", "en-US,en;q=0.9");
        request.setHeader("sec-ch-ua", "\"Chromium\";v=\"88\", \"Google Chrome\";v=\"88\", \";Not A Brand\";v=\"99\"");
        request.setHeader("sec-ch-ua-mobile", "?0");
        request.setHeader("sec-fetch-dest", "video");
        request.setHeader("sec-fetch-mode", "no-cors");
        request.setHeader("sec-fetch-site", "cross-site");
        request.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.150 Safari/537.36");
    }

}

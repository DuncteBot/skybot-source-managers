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
    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/97.0.4692.99 Safari/537.36";

    public static boolean isURL(String url) {
        return url.matches("^https?:\\/\\/[-a-zA-Z0-9+&@#\\/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#\\/%=~_|]");
    }

    public static void fakeChrome(HttpRequest request) {
        request.setHeader("Connection", "keep-alive");
        request.setHeader("DNT", "1");
        request.setHeader("Upgrade-Insecure-Requests", "1");
        request.setHeader("Accept", "*/*");
//        request.setHeader("Accept-Encoding", "gzip, deflate, br");
        request.setHeader("Accept-Encoding", "none");
        request.setHeader("Accept-Language", "en-US,en;q=0.9");
        request.setHeader("Sec-Ch-Ua", "\" Not;A Brand\";v=\"99\", \"Google Chrome\";v=\"97\", \"Chromium\";v=\"97\"");
        request.setHeader("Sec-Ch-Ua-Mobile", "?0");
        request.setHeader("Sec-Fetch-Dest", "document");
        request.setHeader("Sec-Fetch-Mode", "no-cors");
        request.setHeader("Sec-Fetch-Site", "cross-site");
        request.setHeader("User-Agent", USER_AGENT);
    }

}

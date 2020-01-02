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

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;

public class YoutubeVersionData {

    /* INNERTUBE_CONTEXT_CLIENT_VERSION  x-youtube-client-version */
    private final String version;
    /* VARIANTS_CHECKSUM  x-youtube-variants-checksum */
    private final String checksum;
    /* PAGE_BUILD_LABEL  x-youtube-page-label */
    private final String label;
    /* ID_TOKEN  x-youtube-identity-token */
//    private final String idToken;
    /* PAGE_CL  x-youtube-page-cl */
    private final String pageCl;
    /* DEVICE  x-youtube-device */
//    private final String device;

    public YoutubeVersionData(String version, String checksum, String label, String pageCl) {
        this.version = version;
        this.checksum = checksum;
        this.label = label;
        this.pageCl = pageCl;
    }

    public String getVersion() {
        return version;
    }

    public String getChecksum() {
        return checksum;
    }

    public String getLabel() {
        return label;
    }

    public String getPageCl() {
        return pageCl;
    }

    @Override
    public String toString() {
        return "YoutubeVersionData{" +
                "version='" + version + '\'' +
                ", checksum='" + checksum + '\'' +
                ", label='" + label + '\'' +
                ", pageCl='" + pageCl + '\'' +
                '}';
    }

    public static YoutubeVersionData fromBrowser(JsonBrowser json) {
        return new YoutubeVersionData(
                json.get("INNERTUBE_CONTEXT_CLIENT_VERSION").safeText(),
                json.get("VARIANTS_CHECKSUM").safeText(),
                json.get("PAGE_BUILD_LABEL").safeText(),
                json.get("PAGE_CL").safeText()
        );
    }
}
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
import com.dunctebot.sourcemanagers.AudioTrackInfoWithImage;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.http.HttpContextFilter;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.CookieStore;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.BasicCookieStore;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.dunctebot.sourcemanagers.Utils.fakeChrome;

public class TikTokAudioSourceManager extends AbstractDuncteBotHttpSource {
    private static final String BASE = "https:\\/\\/(?:www\\.|m\\.)?tiktok\\.com";
    private static final String USER = "@(?<user>[a-z0-9A-Z_-]+)";
    private static final String VIDEO = "(?<video>[0-9]+)";
    protected static final Pattern VIDEO_REGEX = Pattern.compile("^" + BASE + "\\/" + USER + "\\/video\\/" + VIDEO + "(?:.*)$");
    private static final Pattern JS_REGEX = Pattern.compile(
        "<script id=\"__NEXT_DATA__\" type=\"application/json\" crossorigin=\"anonymous\">(.*)<\\/script>");

    public TikTokAudioSourceManager() {
        super(false);

        this.httpInterfaceManager.setHttpContextFilter(new TikTokFilter());
    }

    @Override
    public String getSourceName() {
        return "tiktok";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        final Matcher matcher = VIDEO_REGEX.matcher(reference.identifier);

        if (!matcher.matches()) {
            return null;
        }

        final String user = matcher.group("user");
        final String video = matcher.group("video");

        try {
            final MetaData metaData = extractData(user, video);

            return new TikTokAudioTrack(metaData.toTrackInfo(), this);
        } catch (Exception e) {
            throw ExceptionTools.wrapUnfriendlyExceptions("Something went wrong", Severity.SUSPICIOUS, e);
        }
    }

    @Override
    public boolean isTrackEncodable(AudioTrack track) {
        return true;
    }

    @Override
    public void encodeTrack(AudioTrack track, DataOutput output) {
        // Nothing to encode
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) {
        return new TikTokAudioTrack(trackInfo, this);
    }

    private MetaData extractData(String userId, String videoId) throws IOException {
        return extractData("https://www.tiktok.com/@" + userId + "/video/" + videoId);
    }

    protected MetaData extractData(String url) throws IOException {
        final JsonBrowser json = extractDataRaw(url);
        final JsonBrowser base = json.get("props").get("pageProps").get("itemInfo").get("itemStruct");

        return getMetaData(url, base);
    }

    protected JsonBrowser extractDataRaw(String url) throws IOException {
        final HttpGet httpGet = new HttpGet(url);

        fakeChrome(httpGet);

        try (final CloseableHttpResponse response = getHttpInterface().execute(httpGet)) {
            final int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode != 200) {
                if (statusCode == 302) { // most likely a 404
                    return null;
                }

                throw new IOException("Unexpected status code for video page response: " + statusCode);
            }

            final String html = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            final Matcher matcher = JS_REGEX.matcher(html);

            if (!matcher.find()) {
                // TODO: temp
                System.out.println(html);
                throw new FriendlyException("Failed to find data for tiktok video", Severity.SUSPICIOUS, null);
            }

            return JsonBrowser.parse(matcher.group(1).trim());
        }
    }

    protected static MetaData getMetaData(String url, JsonBrowser base) {
        final MetaData metaData = new MetaData();

        final JsonBrowser videoJson = base.get("video");

        metaData.pageUrl = url;
        metaData.videoId = videoJson.get("id").safeText();
        metaData.cover = videoJson.get("cover").safeText();
        metaData.title = base.get("desc").safeText();

        metaData.uri = videoJson.get("playAddr").safeText();
        metaData.duration = Integer.parseInt(videoJson.get("duration").safeText());

        final JsonBrowser author = base.get("author");

        metaData.uniqueId = author.get("uniqueId").safeText();

        System.out.println(metaData);

        return metaData;
    }

    protected static class MetaData {
        // video
        String cover; // image url
        String pageUrl;
        String videoId;
        String uri;
        int duration; // in seconds
        String title;

        // author
        String uniqueId;

        AudioTrackInfoWithImage toTrackInfo() {
            return new AudioTrackInfoWithImage(
                this.title,
                this.uniqueId,
                this.duration * 1000L,
                this.videoId,
                false,
                this.pageUrl,
                this.cover
            );
        }

        // TEMP
        @Override
        public String toString() {
            return "MetaData{" +
                "cover='" + cover + '\'' +
                ", pageUrl='" + pageUrl + '\'' +
                ", videoId='" + videoId + '\'' +
                ", uri='" + uri + '\'' +
                ", duration=" + duration +
                ", title='" + title + '\'' +
                ", uniqueId='" + uniqueId + '\'' +
                '}';
        }
    }

    public static class TikTokFilter implements HttpContextFilter {
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
            request.setHeader("Referer", "https://www.tiktok.com/");
//            request.setHeader("Referer", request.getURI().toString());
//            request.setHeader("Cookie", "tt_webid_v2=68" + makeId(16));
//            request.setHeader("Cookie", "tt_webid_v2=6930951741719643654; tt_webid=6930951741719643654; tt_csrf_token=oIE-pGapg3HhLpw4okMKouQE; bm_sz=1D7B2A5C3CE40E72AA2EDF957F29F996~YAAQ9vp7XCr1eB13AQAA4xpHugqAHEd7XhOGx7ADrTdeJSOGXeu3dMn3D8VK2tLtRzg3hHvrJu8zVhDHNbCVuJLKSYJNqO4hGQV1Wv5an9yjhHHni+OXPLEq2Z8jnH8CJJ9/mDYsa38iK4uhWoj1LWpTomOIDQ04lDQ/+ptIS/j2qymZYhNfGWfb1VPE5J7w; csrf_session_id=59db840225234be19cae4f09b50d65fc; R6kq3TV7=AH0eR7p3AQAAmf0BkeXsHuBeFsHfupJ0Uz70iJ1I3lFWmBwfxEqA2pyrlXEm|1|0|cb2e2a7b2c53e039465b82f493fb6a8309e2c12e; s_v_web_id=verify_klc9wz5x_RnKm6BBB_zGUE_4NIv_Amyp_iQnEJuUqiGiI; _abck=E031D1F37D5F1FB86124B6209D9D9621~0~YAAQ9vp7XGz1eB13AQAA1iNHugWhOYMPXUH6lDl8u7JREPlG5fKjcchhZuQSIyfQjQvGBkleZUGxcwOvdaTIJ+y7PCnijTNmCqpa/6h0blaIJsgUhSqxFtzGvSQ1AF3zLM/sEmBTL8272gZdv5wLZ9IDBs5EdeVwkXe83MN+Xkeu/A3Enkl7Ww7b2AJXPF7I1UAvljjXl4uSsay/UplBzWYd/GzwY1dewZj1aqbnGUk+UdMp+EHbpFO4DPVf4nAK6A76226/wxTKZlhwGtCD4wCY7IpCTpUrGT6eEy1pXduCTrZqQr02/rAwwi5aPJB+lHzkQulGH/pL4z1YOSc7GWXEG0vM4g==~-1~||-1||~-1; MONITOR_WEB_ID=6930951741719643654; bm_mi=C903011609E9246F5705C37DAB23438C~TLbWkU2lK07SJd4AEnfIep1qyvRMLo8dk0Ia/9JqiizV0Je06cnPffkpuaiXK1owXx8d+5hQfAxq2Rzyhn8vx/O9irH7i8jM/Opke2qkeGVwEMC/j3ZHQcKNXZ7bCeHicfR94IjDT90WeByQdRs21uGdiqzJVed+hx604WVvLCOmf+2PZCVvyZkT3YhzRvM3o/2vsL0mPH/le/mnnSkSaXvfSOjVC1Ejnj8TXkqGcHs=; bm_sv=43029414F4FFB9469BC50FFC27EABFD5~U8fwrdQmK9DaRqNyRL+2Q5LdaYbFXujau64u0KV6hPHmbobEUj/aPqfsDXY6x4R9B3lmiBdjlkVnCpthdFrrq7OwvK3aTwkKSCW2NbFxFCocthusvTtJ/9hC+CLQVf7cvScr9zPn8HJR2GhlAD8f+T4wQZRgfIss3JaqXs2ORxI=; ak_bmsc=16F1ADB9A7C2ACBFC688E7DA7FC6D44D5C7BFAF6853C0000E6AF2F6033BAB61D~plD1kBh5z0AC8DOgQbkp/GkJKVUKri6+qoZr1c9l6f6OqvIJ6HeOoLqrZERsUxmR1jYJl7lfkAqk/M+PLt5eDR+0nlZtibK459604wHaMzu3OYykuWT68qA6ahNblylWF1b57gOnB608r3uXGAaL6d7QE7ilVdGZDynW3ZpVifnt285Q+tsPrhf86cuG8Ltg4KYL527tXmj8jtpNmItmciM4UeAGrB2LT5vXkRGrXWJ4k18GlH4MXlMPLRsNBaA9vu");

            final String ua = fakeUserAgent();

            System.out.println("NEW USER AGENT " + ua);

            request.setHeader("User-Agent", ua);

        }

        @Override
        public boolean onRequestResponse(HttpClientContext context, HttpUriRequest request, HttpResponse response) {
            return false;
        }

        @Override
        public boolean onRequestException(HttpClientContext context, HttpUriRequest request, Throwable error) {
            return false;
        }

        private static String makeId(int length) {
            final StringBuilder sb = new StringBuilder();
            final String charList = "0123456789";

            for (int i = 0; i < length; i += 1) {
                sb.append(charList.charAt((int) Math.floor(Math.random() * charList.length())));
            }

            return sb.toString();
        }

        private static String fakeUserAgent() {
            final String[] parts = {
                "Macintosh; Intel Mac OS X 10_15_7",
                "Macintosh; Intel Mac OS X 10_15_5",
                "Macintosh; Intel Mac OS X 10_11_6",
                "Macintosh; Intel Mac OS X 10_6_6",
                "Macintosh; Intel Mac OS X 10_9_5",
                "Macintosh; Intel Mac OS X 10_10_5",
                "Macintosh; Intel Mac OS X 10_7_5",
                "Macintosh; Intel Mac OS X 10_11_3",
                "Macintosh; Intel Mac OS X 10_10_3",
                "Macintosh; Intel Mac OS X 10_6_8",
                "Macintosh; Intel Mac OS X 10_10_2",
                "Macintosh; Intel Mac OS X 10_10_3",
                "Macintosh; Intel Mac OS X 10_11_5",
                "Windows NT 10.0; Win64; x64",
                "Windows NT 10.0; WOW64",
                "Windows NT 10.0",
            };

            final ThreadLocalRandom rnd = ThreadLocalRandom.current();

            return "Mozilla/5.0 (" + parts[rnd.nextInt(parts.length)] + ") AppleWebKit/537.36 (KHTML, like Gecko) Chrome/" +
                (rnd.nextInt(3) + 85) + ".0." + (rnd.nextInt(190) + 4100) + '.' + (rnd.nextInt(50) + 140) +
                "Safari/537.36";
        }
    }
}

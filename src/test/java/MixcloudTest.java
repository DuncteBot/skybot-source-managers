/*
 * Copyright 2022 Duncan "duncte123" Sterken
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

import com.dunctebot.sourcemanagers.mixcloud.MixcloudAudioSourceManager;
import com.dunctebot.sourcemanagers.mixcloud.MixcloudAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;

public class MixcloudTest {
    public static void main(String[] args) {
        final var url = "https://www.mixcloud.com/jordy-boesten2/the-egotripper-lets-walk-to-my-house-mix-259/";
        final var mnrg = new MixcloudAudioSourceManager();
        final var track = (MixcloudAudioTrack) mnrg.loadItem(null, new AudioReference(url, null));
        final var playbackUrl = track.getPlaybackUrl();

        System.out.println(playbackUrl);
    }
}

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

import com.dunctebot.sourcemanagers.ocremix.OCRemixAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;

public class OCRegexTest {

    public static void main(String[] args) {
        final var id1 = "OCR03310";
        final var id2 = "https://ocremix.org/remix/OCR03310";

        final var mngr = new OCRemixAudioSourceManager();

        final var res1 = mngr.loadItem(null, new AudioReference(id1, ""));
        final var res2 = mngr.loadItem(null, new AudioReference(id2, ""));

        System.out.println(res1);
        System.out.println(res2);
    }
}

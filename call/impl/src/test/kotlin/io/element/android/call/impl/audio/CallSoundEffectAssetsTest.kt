/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.call.impl.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** Web's files, unchanged: the hashes in docs/sounds.md, which iOS quotes too. InterCom's own are the desktop's, unchanged. */
class CallSoundEffectAssetsTest {
    /** InterCom's own, the desktop's files unchanged: `intercom_join.ogg` and `intercom_leave.ogg` of its call. */
    @Test
    fun `the join and leave chimes are InterCom's`() {
        assertThat(sha256("element_call_intercom_join.ogg")).isEqualTo("36561404f74404918037bd82603d9253ab1711875bfdea5760b74d52699840ca")
        assertThat(sha256("element_call_intercom_leave.ogg")).isEqualTo("af07fc52adf088c28427e5daffc86fd9b16f7a02e0e1a576659773ec26e0597e")
    }

    /** InterCom's own, the desktop's files unchanged: `res/media/intercom/` of its web app. */
    @Test
    fun `the mute and deafen cues are InterCom's`() {
        assertThat(sha256("element_call_intercom_mute.ogg")).isEqualTo("e108a4deb7369e2fd31c532a12f0dfafa2dea11e952cb2d5aa9dde2991df81a3")
        assertThat(sha256("element_call_intercom_unmute.ogg")).isEqualTo("50b8aca8e1bd74cf5dae0e06390c41c252d3b559afcbb811fbfd7bd1e843d247")
        assertThat(sha256("element_call_intercom_deafen.ogg")).isEqualTo("28b72917fab79dc3121797d2a77ffc7a5d3fc56385ee913922f63d881fa74996")
        assertThat(sha256("element_call_intercom_undeafen.ogg")).isEqualTo("a680e5e2d625cda49283df13693fe3a0d094eb93c225dc8de2a161db2576491e")
    }

    @Test
    fun `the raised hand sound is web's`() {
        assertThat(sha256("element_call_raise_hand.mp3")).isEqualTo("0601948ad6e4937cc6bca126f0decb2218037f39f6f7dc99cee359efdb66212b")
    }

    @Test
    fun `the ringback is InterCom's and the end sounds are web's`() {
        assertThat(sha256("element_call_intercom_ringback.ogg")).isEqualTo("1d8cc60f918eaab1e7be9825dc45df13f782baf91e6a58965407f644fe2232a7")
        assertThat(sha256("element_call_call_declined.mp3")).isEqualTo("bb8d93337c6d8a391fbc7750ce5fb955413ba084183a76c22aba5016f8d0e06b")
        assertThat(sha256("element_call_call_timeout.mp3")).isEqualTo("c0aed0c09a85d580824847988e6868121c1329997cf8a3df88a42895adbf3ddd")
    }

    @Test
    fun `the reaction sounds are web's`() {
        mapOf(
            "baduntss" to "48c30e2259598e3de9880d95b1075fcb7aa35a9b41ab94447f22534dc3b3d1e0",
            "bell" to "d489ccd5e386072e9db47c9a332ef4f33130cf940e1ddde55d062835ae197bb8",
            "cat" to "cbf49e888bcf2213145fa43045c3493746c8b2fc3cbebadf6727a244ec60ac4d",
            "clap" to "b927e4bd51c781fb0232b7430b13aa56cf7e9e1bf8a3b049a0011312ce5c8830",
            "crickets" to "f7949e8f092a8653f3da9f4d117be8cbc61b68c580bb43f610838df0061da8de",
            "deer" to "e9fdba54df8e31091c311b61bc16413accaebfc5b3bf18c6b7761577ac354c6c",
            "dog" to "0b0ce7ea208cd900c11e4743e9c25a6633d7d6e750bd8d5d34f1d3119a469260",
            "generic" to "748e6d03a559ac909d04b9fa907fca3424c6bf00aa45bcad3f6a07238e58f6cb",
            "lightbulb" to "2471aac761484adb8c2485954016b93af907d14a4d03afdcffe0a5749f489bb0",
            "party" to "3ab05da72c326143027e1bae16e005512e94eb8985d978a10eb9016f088a6b79",
            "rock" to "337166ae2fb7cac96b8c5fd6d2872dfd88105fc59363101846f3d55c30986531",
            "wave" to "4391d019809c9951333cd8ab9f5329b039602334bb55f5855b7852321109a6ac",
        ).forEach { (name, hash) -> assertThat(sha256("element_call_reaction_$name.mp3")).isEqualTo(hash) }
    }

    private fun sha256(name: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(File("src/main/res/raw/$name").readBytes())
            .joinToString("") { "%02x".format(it) }
}

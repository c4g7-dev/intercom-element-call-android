/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.call.impl.rtc

import com.google.common.truth.Truth.assertThat
import io.element.android.call.api.rtc.MatrixRtcStreamKind
import io.element.android.call.api.rtc.MatrixRtcStreamRef
import io.element.android.call.api.rtc.MatrixRtcTileKind
import io.element.android.call.test.aRoster
import io.element.android.call.test.aTile
import org.junit.Test

class PlaybackCandidatesTest {
    /** A sharer is two tiles and two things to hear: their voice, and what their screen plays. */
    @Test
    fun `every remote person tile is a candidate for its voice and every remote share for its audio, once`() {
        val order = aRoster(aTile("A", MatrixRtcTileKind.SCREEN_SHARE), aTile("A"), aTile("B")).order

        assertThat(playbackCandidates(order, localMemberId = "me")).containsExactly(
            MatrixRtcStreamRef("A", MatrixRtcStreamKind.SCREEN_SHARE_AUDIO),
            MatrixRtcStreamRef("A", MatrixRtcStreamKind.MICROPHONE),
            MatrixRtcStreamRef("B", MatrixRtcStreamKind.MICROPHONE),
        )
    }

    /** Our own tile is never in the order, but the id guard holds even if it were; our own share's audio is what the phone already plays. */
    @Test
    fun `we are never a candidate, and neither is our own share`() {
        val order = aRoster(aTile("me"), aTile("me", MatrixRtcTileKind.SCREEN_SHARE)).order

        assertThat(playbackCandidates(order, localMemberId = "me")).isEmpty()
    }
}

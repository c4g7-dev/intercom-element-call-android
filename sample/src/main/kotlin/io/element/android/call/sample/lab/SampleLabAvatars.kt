/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.call.sample.lab

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import io.element.android.call.sample.matrix.runCatchingExceptions
import io.element.android.call.ui.theme.DefaultElementCallAvatar
import io.element.android.call.ui.theme.ElementCallAvatarData
import io.element.android.call.ui.theme.ElementCallAvatarSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.MediaSource
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * The members' own avatars in a lab call, loaded through the session's [Client]. The library draws initials only, because
 * loading media is the host's: this is what a host's [io.element.android.call.ui.theme.ElementCallStyle.avatar] looks
 * like over the Rust SDK. Until a picture has loaded, or when there is none, the default initials avatar is drawn, so a
 * picture arriving changes nothing else on the tile.
 */
class SampleLabAvatars(private val client: Client, private val scope: CoroutineScope) {
    // A tile asks again on every recomposition, and remounts when the layout moves it; without the cache a picture would
    // drop back to initials and be fetched again each time. Keyed by URL alone: every thumbnail is fetched at the largest
    // size the call draws, so a tile promoted to the spotlight is not blurry.
    private val images = ConcurrentHashMap<String, ImageBitmap>()
    private val loads = HashMap<String, Deferred<ImageBitmap?>>()

    @Composable
    fun Avatar(data: ElementCallAvatarData, size: Dp, modifier: Modifier = Modifier) {
        val url = data.url
        val pixels = with(LocalDensity.current) { ElementCallAvatarSize.Spotlight.roundToPx().toLong() }
        val image by produceState(initialValue = url?.let(images::get), url) {
            // The state outlives a change of URL, so it is reset rather than trusted.
            value = url?.let(images::get)
            if (url != null && value == null) value = load(url, pixels).await()
        }
        val current = image
        if (current != null) {
            Image(
                bitmap = current,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = modifier
                    .size(size)
                    .clip(CircleShape),
            )
        } else {
            DefaultElementCallAvatar(data = data, size = size, modifier = modifier)
        }
    }

    private fun load(url: String, pixels: Long): Deferred<ImageBitmap?> = synchronized(loads) {
        loads.getOrPut(url) {
            scope.async(Dispatchers.IO) {
                Timber.d("Lab: avatar $url: loading")
                runCatchingExceptions {
                    val bytes = MediaSource.fromUrl(url).use { client.getMediaThumbnail(it, pixels.toULong(), pixels.toULong()) }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                        ?: error("not an image")
                }.onSuccess {
                    images[url] = it
                }.onFailure {
                    Timber.w(it, "Lab: avatar $url failed; drawing initials")
                }.getOrNull()
            }
        }
    }
}

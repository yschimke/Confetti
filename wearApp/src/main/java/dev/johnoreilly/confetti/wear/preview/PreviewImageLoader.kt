@file:OptIn(ExperimentalCoilApi::class)

package dev.johnoreilly.confetti.wear.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.test.FakeImageLoaderEngine
import dev.johnoreilly.confetti.R

/**
 * A Coil [ImageLoader] for `@Preview` / offline renders (Android Studio, the
 * `compose-preview` pipeline, `preview.coo.ee`).
 *
 * Speaker avatars are loaded from network URLs (sessionize) via
 * `SubcomposeAsyncImage`, which can't fetch anything in the render sandbox —
 * so the chip would sit in its `loading` slot forever (the circle-outline
 * `CircularProgressIndicator` you see in a raw render). This engine maps any
 * image request to the [R.drawable.preview_avatar] placeholder so previews
 * show a real image instead of a spinner. Mirrors the `FakeImageLoaderEngine`
 * the screenshot tests use, but lives in `main` so it's reachable from the
 * preview scaffold.
 */
@Composable
fun rememberPreviewImageLoader(): ImageLoader {
    val context = LocalContext.current
    return remember(context) {
        val avatar = context.getDrawable(R.drawable.preview_avatar)!!
        val engine = FakeImageLoaderEngine.Builder()
            .default(avatar)
            .build()

        ImageLoader.Builder(context)
            .components { add(engine) }
            .build()
    }
}

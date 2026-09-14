package com.vachak.ui

import android.net.Uri
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.vachak.R
import com.vachak.engine.VachakLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val EaseSnap = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * Launch splash: plays `res/raw/loadingscreen.mp4` EXACTLY once while models
 * preload underneath (MainActivity's sequential preload is untouched — the
 * video covers it, it never gates on it). Muted (classroom-appropriate;
 * unmute by dropping the setVolume call). On completion (or error, or a 30s
 * safety cap if completion never fires) the video fades out over 600ms into
 * the app. No fixed-duration splash remains.
 */
@Composable
fun VideoSplash(onDone: () -> Unit) {
    var dismiss by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun finish(source: String) {
        if (dismiss) return
        dismiss = true
        VachakLog.d("Vachak-Splash", "video done ($source) — fading out")
        scope.launch {
            delay(650)
            onDone()
        }
    }
    // Safety: never trap the user on this screen.
    LaunchedEffect(Unit) {
        delay(30_000)
        finish("timeout")
    }
    AnimatedVisibility(
        visible = !dismiss,
        exit = fadeOut(animationSpec = tween(durationMillis = 600, easing = EaseSnap)),
        label = "splashFade"
    ) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    setVideoURI(Uri.parse("android.resource://${ctx.packageName}/${R.raw.loadingscreen}"))
                    setOnPreparedListener { mp ->
                        mp.setVolume(0f, 0f)
                        mp.isLooping = false
                        VachakLog.d("Vachak-Splash", "video prepared — playing once")
                        start()
                    }
                    setOnCompletionListener { finish("completed") }
                    setOnErrorListener { _, what, extra ->
                        VachakLog.e("Vachak-Splash", "video error what=$what extra=$extra — skipping")
                        finish("error")
                        true
                    }
                }
            },
            onRelease = { it.stopPlayback() },
            modifier = Modifier.fillMaxSize().background(Color.Black)
        )
    }
}

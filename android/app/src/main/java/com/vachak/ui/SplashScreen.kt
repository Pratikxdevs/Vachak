package com.vachak.ui

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
 * video covers it, it never gates on it). Muted (classroom-appropriate).
 *
 * Natural 9:16 stage: the video box itself is 9:16 (the source's own
 *  ratio), centered, as large as the screen allows. No stretch, no crop —
 *  the pixels are shown exactly as authored. On completion (or error, or a
 *  30s safety cap) the video fades out over 600ms into the app.
 */
@Composable
fun VideoSplash(onDone: () -> Unit) {
    var dismiss by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // MediaPlayer outlives recompositions; released once in onRelease.
    val player = remember { MediaPlayer() }
    var playerReleased by remember { mutableStateOf(false) }
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
        // 9:16 stage, centered: fills height on tall phones, fills width on
        // wider screens — the video is never stretched or cropped.
        // Stage backdrop is WHITE (the video's own field color), so the
        // letterbox gaps on taller screens vanish instead of reading as bars.
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            val useWidth = maxWidth / maxHeight < 9f / 16f
            val stage = if (useWidth) {
                Modifier.fillMaxWidth().aspectRatio(9f / 16f)
            } else {
                Modifier.fillMaxHeight().aspectRatio(9f / 16f)
            }
            Box(modifier = stage) {
                AndroidView(
                    factory = { ctx ->
                        TextureView(ctx).apply {
                            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                    try {
                                        player.reset()
                                        val afd = ctx.resources.openRawResourceFd(R.raw.loadingscreen)
                                        player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                                        afd.close()
                                        player.setSurface(Surface(st))
                                        player.setVolume(0f, 0f)
                                        player.isLooping = false
                                        player.setOnPreparedListener { mp ->
                                            VachakLog.d("Vachak-Splash", "video prepared ${mp.videoWidth}x${mp.videoHeight} — playing once")
                                            mp.start()
                                        }
                                        player.setOnCompletionListener { finish("completed") }
                                        player.setOnErrorListener { _, what, extra ->
                                            VachakLog.e("Vachak-Splash", "video error what=$what extra=$extra — skipping")
                                            finish("error")
                                            true
                                        }
                                        player.prepareAsync()
                                    } catch (e: Exception) {
                                        VachakLog.e("Vachak-Splash", "video setup threw — skipping", e)
                                        finish("error")
                                    }
                                }

                                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = Unit
                                override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = true
                                override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                            }
                        }
                    },
                    onRelease = {
                        if (!playerReleased) {
                            playerReleased = true
                            try {
                                player.stop()
                            } catch (_: Exception) {}
                            player.release()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

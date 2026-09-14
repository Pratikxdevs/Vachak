package com.vachak.ui

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
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
 * video covers it, it never gates on it). Muted (classroom-appropriate).
 *
 * Natural centered fit: whole video visible, aspect preserved, centered on
 * black. On completion (or error, or a 30s safety cap) the video fades out
 * over 600ms into the app. No fixed-duration splash.
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
                                    // Natural fit: whole video, centered, aspect
                                    // preserved. No crop, no stretch.
                                    val vw = mp.videoWidth.toFloat()
                                    val vh = mp.videoHeight.toFloat()
                                    if (vw > 0 && vh > 0 && w > 0 && h > 0) {
                                        val scale = minOf(w / vw, h / vh)
                                        val dx = (w - vw * scale) / 2f
                                        val dy = (h - vh * scale) / 2f
                                        val m = Matrix()
                                        m.setScale(scale, scale)
                                        m.postTranslate(dx, dy)
                                        setTransform(m)
                                    }
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
            modifier = Modifier.fillMaxSize().background(Color.Black)
        )
    }
}

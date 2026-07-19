package com.smashhaulers.game

import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * Hosts the game loop thread and forwards touch input to the world.
 * All coordinates are converted to world space (1920x1080 design space)
 * so the game plays identically on every screen size.
 */
class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    private var thread: Thread? = null
    @Volatile private var running = false
    @Volatile private var surfaceReady = false

    private val world = GameWorld()
    private var scale = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        startLoop()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // Letterbox the fixed 1920x1080 world into whatever surface we get.
        scale = minOf(width / GameWorld.W, height / GameWorld.H)
        offsetX = (width - GameWorld.W * scale) / 2f
        offsetY = (height - GameWorld.H * scale) / 2f
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        stopLoop()
    }

    fun resumeGame() {
        if (surfaceReady) startLoop()
    }

    fun pauseGame() = stopLoop()

    private fun startLoop() {
        if (running) return
        running = true
        thread = Thread(this, "GameLoop").also { it.start() }
    }

    private fun stopLoop() {
        running = false
        thread?.join()
        thread = null
    }

    override fun run() {
        var last = System.nanoTime()
        while (running) {
            val now = System.nanoTime()
            // Clamp dt so a hiccup never tunnels bodies through each other.
            val dt = ((now - last) / 1_000_000_000.0).toFloat().coerceIn(0f, 1f / 30f)
            last = now

            world.update(dt)

            if (holder.surface.isValid) {
                val canvas: Canvas? = holder.lockCanvas()
                if (canvas != null) {
                    try {
                        canvas.drawColor(0xFF1A2238.toInt())
                        canvas.save()
                        canvas.translate(offsetX, offsetY)
                        canvas.scale(scale, scale)
                        world.draw(canvas)
                        canvas.restore()
                    } finally {
                        holder.unlockCanvasAndPost(canvas)
                    }
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val wx = (event.x - offsetX) / scale
        val wy = (event.y - offsetY) / scale
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> world.touchDown(wx, wy)
            MotionEvent.ACTION_MOVE -> world.touchMove(wx, wy)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> world.touchUp(wx, wy)
        }
        return true
    }
}

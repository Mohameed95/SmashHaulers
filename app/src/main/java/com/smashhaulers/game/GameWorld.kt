package com.smashhaulers.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * The whole game: simple 2D physics (non-rotating boxes + a circular
 * projectile), levels, scoring and rendering. World space is a fixed
 * 1920x1080; GameView letterboxes it onto the real screen.
 */
class GameWorld {

    companion object {
        const val W = 1920f
        const val H = 1080f
        const val GROUND_Y = 960f
        const val GRAVITY = 2600f
        const val LAUNCH_X = 260f
        const val LAUNCH_Y = 820f
        const val MAX_DRAG = 260f
        const val LAUNCH_POWER = 9.5f
        const val TRUCK_RADIUS = 46f
    }

    // ---------- entities ----------

    class Truck {
        var x = LAUNCH_X; var y = LAUNCH_Y
        var vx = 0f; var vy = 0f
        var launched = false
        var alive = true
        val r = TRUCK_RADIUS
    }

    class Block(var x: Float, var y: Float, val w: Float, val h: Float, val tough: Boolean = false) {
        var vx = 0f; var vy = 0f
        var alive = true
        var hp = if (tough) 3 else 1
        val left get() = x - w / 2
        val right get() = x + w / 2
        val top get() = y - h / 2
        val bottom get() = y + h / 2
    }

    class Alien(var x: Float, var y: Float) {
        var vx = 0f; var vy = 0f
        var alive = true
        val r = 34f
    }

    class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val color: Int)

    // ---------- state ----------

    private var truck = Truck()
    private val blocks = mutableListOf<Block>()
    private val aliens = mutableListOf<Alien>()
    private val particles = mutableListOf<Particle>()

    private var levelIndex = 0
    private var trucksLeft = 3
    private var score = 0
    private var settleTimer = 0f

    private enum class State { AIMING, FLYING, LEVEL_WON, GAME_OVER, ALL_CLEAR }
    private var state = State.AIMING

    private var dragging = false
    private var dragX = LAUNCH_X
    private var dragY = LAUNCH_Y

    init { loadLevel(0) }

    // ---------- levels ----------

    private fun loadLevel(index: Int) {
        levelIndex = index
        blocks.clear(); aliens.clear(); particles.clear()
        truck = Truck()
        state = State.AIMING
        val b = 90f // standard crate size

        fun crate(cx: Float, groundStack: Int, tough: Boolean = false) =
            blocks.add(Block(cx, GROUND_Y - b / 2 - groundStack * b, b, b, tough))

        fun alienOn(cx: Float, groundStack: Int) =
            aliens.add(Alien(cx, GROUND_Y - 34f - groundStack * b))

        when (index) {
            0 -> {
                trucksLeft = 3
                crate(1300f, 0); crate(1300f, 1)
                alienOn(1300f, 2)
                crate(1550f, 0)
                alienOn(1550f, 1)
            }
            1 -> {
                trucksLeft = 3
                crate(1200f, 0); crate(1200f, 1); crate(1200f, 2)
                alienOn(1200f, 3)
                crate(1450f, 0, tough = true); crate(1450f, 1)
                alienOn(1450f, 2)
                alienOn(1680f, 0)
            }
            else -> {
                trucksLeft = 4
                crate(1100f, 0); crate(1100f, 1)
                crate(1320f, 0, tough = true); crate(1320f, 1, tough = true); crate(1320f, 2)
                alienOn(1320f, 3)
                crate(1540f, 0); crate(1540f, 1)
                alienOn(1100f, 2)
                alienOn(1540f, 2)
                alienOn(1760f, 0)
            }
        }
    }

    private val levelCount = 3

    // ---------- input ----------

    fun touchDown(x: Float, y: Float) {
        when (state) {
            State.AIMING -> {
                if (hypot(x - LAUNCH_X, y - LAUNCH_Y) < 220f) {
                    dragging = true
                    dragX = x; dragY = y
                }
            }
            State.LEVEL_WON -> loadLevel(min(levelIndex + 1, levelCount - 1))
            State.GAME_OVER -> { score = 0; loadLevel(levelIndex) }
            State.ALL_CLEAR -> { score = 0; loadLevel(0) }
            else -> {}
        }
    }

    fun touchMove(x: Float, y: Float) {
        if (dragging) { dragX = x; dragY = y }
    }

    fun touchUp(x: Float, y: Float) {
        if (!dragging) return
        dragging = false
        var dx = LAUNCH_X - x
        var dy = LAUNCH_Y - y
        val len = hypot(dx, dy)
        if (len < 20f) return // too small, cancel
        if (len > MAX_DRAG) { dx *= MAX_DRAG / len; dy *= MAX_DRAG / len }
        truck.vx = dx * LAUNCH_POWER
        truck.vy = dy * LAUNCH_POWER
        truck.launched = true
        trucksLeft--
        state = State.FLYING
        settleTimer = 0f
    }

    // ---------- simulation ----------

    fun update(dt: Float) {
        if (dt <= 0f) return

        if (truck.launched && truck.alive) stepTruck(dt)
        stepBlocksAndAliens(dt)
        collide()
        stepParticles(dt)

        if (state == State.FLYING) {
            if (worldIsSettled()) settleTimer += dt else settleTimer = 0f
            val truckGone = !truck.alive || truck.x > W + 200 || truck.x < -200
            if (settleTimer > 0.8f || truckGone) {
                truck.alive = false
                when {
                    aliens.none { it.alive } ->
                        state = if (levelIndex == levelCount - 1) State.ALL_CLEAR else State.LEVEL_WON
                    trucksLeft > 0 -> { truck = Truck(); state = State.AIMING }
                    else -> state = State.GAME_OVER
                }
            }
        }
    }

    private fun stepTruck(dt: Float) {
        val t = truck
        t.vy += GRAVITY * dt
        t.x += t.vx * dt
        t.y += t.vy * dt
        if (t.y + t.r > GROUND_Y) {
            t.y = GROUND_Y - t.r
            if (t.vy > 0) t.vy = -t.vy * 0.35f
            t.vx *= 0.94f
        }
    }

    private fun stepBlocksAndAliens(dt: Float) {
        for (bl in blocks) {
            if (!bl.alive) continue
            bl.vy += GRAVITY * dt
            bl.x += bl.vx * dt
            bl.y += bl.vy * dt
            if (bl.bottom > GROUND_Y) {
                bl.y = GROUND_Y - bl.h / 2
                if (bl.vy > 0) bl.vy = -bl.vy * 0.1f
                bl.vx *= 0.85f
            }
        }
        for (a in aliens) {
            if (!a.alive) continue
            a.vy += GRAVITY * dt
            a.x += a.vx * dt
            a.y += a.vy * dt
            if (a.y + a.r > GROUND_Y) {
                a.y = GROUND_Y - a.r
                if (a.vy > 0) a.vy = -a.vy * 0.2f
                a.vx *= 0.85f
            }
        }
    }

    private fun collide() {
        // block vs block (AABB, resolve along smallest overlap axis)
        for (i in blocks.indices) for (j in i + 1 until blocks.size) {
            val a = blocks[i]; val b = blocks[j]
            if (!a.alive || !b.alive) continue
            val ox = min(a.right, b.right) - max(a.left, b.left)
            val oy = min(a.bottom, b.bottom) - max(a.top, b.top)
            if (ox <= 0 || oy <= 0) continue
            if (ox < oy) {
                val dir = if (a.x < b.x) -1 else 1
                a.x += dir * ox / 2; b.x -= dir * ox / 2
                val v = (a.vx + b.vx) / 2; a.vx = v; b.vx = v
            } else {
                val dir = if (a.y < b.y) -1 else 1
                a.y += dir * oy / 2; b.y -= dir * oy / 2
                val v = (a.vy + b.vy) / 2; a.vy = v; b.vy = v
            }
        }

        // truck vs blocks
        if (truck.launched && truck.alive) {
            for (bl in blocks) {
                if (!bl.alive) continue
                val cx = truck.x.coerceIn(bl.left, bl.right)
                val cy = truck.y.coerceIn(bl.top, bl.bottom)
                val dx = truck.x - cx; val dy = truck.y - cy
                val d = hypot(dx, dy)
                if (d < truck.r) {
                    val impact = hypot(truck.vx - bl.vx, truck.vy - bl.vy)
                    // push truck out
                    if (d > 0.001f) {
                        val nx = dx / d; val ny = dy / d
                        truck.x = cx + nx * truck.r
                        truck.y = cy + ny * truck.r
                    } else truck.y = bl.top - truck.r
                    // transfer momentum
                    bl.vx += truck.vx * 0.55f
                    bl.vy += truck.vy * 0.35f
                    truck.vx *= 0.55f
                    truck.vy *= 0.55f
                    if (impact > 700f) {
                        bl.hp--
                        spawnBurst(cx, cy, if (bl.tough) 0xFF8D99AE.toInt() else 0xFFC9863B.toInt())
                        if (bl.hp <= 0) { bl.alive = false; score += if (bl.tough) 300 else 100 }
                    }
                }
            }
        }

        // aliens squashed by truck or blocks
        for (a in aliens) {
            if (!a.alive) continue
            if (truck.launched && truck.alive &&
                hypot(truck.x - a.x, truck.y - a.y) < truck.r + a.r
            ) { squash(a); continue }
            for (bl in blocks) {
                if (!bl.alive) continue
                val cx = a.x.coerceIn(bl.left, bl.right)
                val cy = a.y.coerceIn(bl.top, bl.bottom)
                if (hypot(a.x - cx, a.y - cy) < a.r) {
                    val impact = hypot(bl.vx - a.vx, bl.vy - a.vy)
                    if (impact > 350f) { squash(a); break }
                    // otherwise the alien just gets shoved
                    a.vx += bl.vx * 0.5f
                }
            }
        }
    }

    private fun squash(a: Alien) {
        a.alive = false
        score += 500
        spawnBurst(a.x, a.y, 0xFF7BC950.toInt())
    }

    private fun spawnBurst(x: Float, y: Float, color: Int) {
        repeat(14) {
            val ang = Random.nextFloat() * (Math.PI * 2).toFloat()
            val sp = 200f + Random.nextFloat() * 500f
            particles.add(
                Particle(x, y, kotlin.math.cos(ang) * sp, kotlin.math.sin(ang) * sp - 250f,
                    0.5f + Random.nextFloat() * 0.4f, color)
            )
        }
    }

    private fun stepParticles(dt: Float) {
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dt
            if (p.life <= 0) { it.remove(); continue }
            p.vy += GRAVITY * 0.6f * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
        }
    }

    private fun worldIsSettled(): Boolean {
        if (truck.alive && hypot(truck.vx, truck.vy) > 60f) return false
        for (bl in blocks) if (bl.alive && hypot(bl.vx, bl.vy) > 60f) return false
        for (a in aliens) if (a.alive && hypot(a.vx, a.vy) > 60f) return false
        return true
    }

    // ---------- rendering ----------

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 56f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val bigText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 110f
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    fun draw(c: Canvas) {
        // sky gradient bands + ground
        paint.color = 0xFF16324F.toInt()
        c.drawRect(0f, 0f, W, GROUND_Y, paint)
        paint.color = 0xFF3E5622.toInt()
        c.drawRect(0f, GROUND_Y, W, H, paint)
        paint.color = 0xFF553311.toInt()
        c.drawRect(0f, GROUND_Y, W, GROUND_Y + 14f, paint)

        drawLaunchPad(c)
        for (bl in blocks) if (bl.alive) drawBlock(c, bl)
        for (a in aliens) if (a.alive) drawAlien(c, a)
        if (truck.alive) drawTruck(c)
        for (p in particles) {
            paint.color = p.color
            paint.alpha = (255 * (p.life / 0.9f)).toInt().coerceIn(0, 255)
            c.drawCircle(p.x, p.y, 9f, paint)
            paint.alpha = 255
        }
        if (dragging) drawAimGuide(c)
        drawHud(c)
    }

    private fun drawLaunchPad(c: Canvas) {
        paint.color = 0xFF444B5A.toInt()
        c.drawRect(LAUNCH_X - 110f, GROUND_Y - 30f, LAUNCH_X + 110f, GROUND_Y, paint)
        paint.color = 0xFF5C6578.toInt()
        c.drawRect(LAUNCH_X - 8f, LAUNCH_Y, LAUNCH_X + 8f, GROUND_Y - 30f, paint)
    }

    private fun drawTruck(c: Canvas) {
        val t = truck
        // body
        paint.color = 0xFFD62828.toInt()
        c.drawRoundRect(RectF(t.x - 55f, t.y - 30f, t.x + 35f, t.y + 18f), 10f, 10f, paint)
        // cab
        paint.color = 0xFFF77F00.toInt()
        c.drawRoundRect(RectF(t.x + 10f, t.y - 52f, t.x + 55f, t.y + 18f), 10f, 10f, paint)
        // window
        paint.color = 0xFFBEE3F8.toInt()
        c.drawRect(t.x + 20f, t.y - 42f, t.x + 46f, t.y - 16f, paint)
        // wheels
        paint.color = 0xFF222222.toInt()
        c.drawCircle(t.x - 32f, t.y + 26f, 20f, paint)
        c.drawCircle(t.x + 32f, t.y + 26f, 20f, paint)
        paint.color = 0xFF888888.toInt()
        c.drawCircle(t.x - 32f, t.y + 26f, 9f, paint)
        c.drawCircle(t.x + 32f, t.y + 26f, 9f, paint)
    }

    private fun drawBlock(c: Canvas, bl: Block) {
        paint.color = if (bl.tough) 0xFF8D99AE.toInt() else 0xFFC9863B.toInt()
        c.drawRect(bl.left, bl.top, bl.right, bl.bottom, paint)
        paint.color = if (bl.tough) 0xFF6C7688.toInt() else 0xFF9C6527.toInt()
        paint.strokeWidth = 6f
        c.drawLine(bl.left, bl.top, bl.right, bl.bottom, paint)
        c.drawLine(bl.right, bl.top, bl.left, bl.bottom, paint)
        paint.style = Paint.Style.STROKE
        c.drawRect(bl.left + 3f, bl.top + 3f, bl.right - 3f, bl.bottom - 3f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawAlien(c: Canvas, a: Alien) {
        paint.color = 0xFF7BC950.toInt()
        c.drawCircle(a.x, a.y, a.r, paint)
        // eyes
        paint.color = Color.WHITE
        c.drawCircle(a.x - 13f, a.y - 8f, 10f, paint)
        c.drawCircle(a.x + 13f, a.y - 8f, 10f, paint)
        paint.color = Color.BLACK
        c.drawCircle(a.x - 11f, a.y - 8f, 4.5f, paint)
        c.drawCircle(a.x + 15f, a.y - 8f, 4.5f, paint)
        // antenna
        paint.strokeWidth = 5f
        paint.color = 0xFF5AA83A.toInt()
        c.drawLine(a.x, a.y - a.r, a.x, a.y - a.r - 16f, paint)
        c.drawCircle(a.x, a.y - a.r - 20f, 6f, paint)
    }

    private fun drawAimGuide(c: Canvas) {
        var dx = LAUNCH_X - dragX
        var dy = LAUNCH_Y - dragY
        val len = hypot(dx, dy)
        if (len > MAX_DRAG) { dx *= MAX_DRAG / len; dy *= MAX_DRAG / len }
        val vx = dx * LAUNCH_POWER
        val vy = dy * LAUNCH_POWER
        // dotted predicted trajectory
        paint.color = 0x99FFFFFF.toInt()
        var px = LAUNCH_X; var py = LAUNCH_Y
        var pvx = vx; var pvy = vy
        val step = 1f / 30f
        repeat(22) {
            pvy += GRAVITY * step
            px += pvx * step
            py += pvy * step
            if (py < GROUND_Y) c.drawCircle(px, py, 7f, paint)
        }
        // rubber band
        paint.strokeWidth = 10f
        paint.color = 0xFFAA3333.toInt()
        c.drawLine(LAUNCH_X, LAUNCH_Y, dragX.coerceIn(0f, W), dragY.coerceIn(0f, H), paint)
    }

    private fun drawHud(c: Canvas) {
        textPaint.textAlign = Paint.Align.LEFT
        c.drawText("Score: $score", 40f, 70f, textPaint)
        c.drawText("Level ${levelIndex + 1}", 40f, 140f, textPaint)
        // remaining trucks as icons
        for (i in 0 until trucksLeft) {
            paint.color = 0xFFD62828.toInt()
            c.drawRoundRect(RectF(W - 120f - i * 90f, 40f, W - 50f - i * 90f, 90f), 8f, 8f, paint)
        }
        when (state) {
            State.LEVEL_WON -> banner(c, "LEVEL CLEAR!", "Tap for next level")
            State.GAME_OVER -> banner(c, "OUT OF TRUCKS", "Tap to retry")
            State.ALL_CLEAR -> banner(c, "YOU WIN!  Score: $score", "Tap to play again")
            else -> {}
        }
    }

    private fun banner(c: Canvas, title: String, sub: String) {
        paint.color = 0xAA000000.toInt()
        c.drawRect(0f, H / 2 - 160f, W, H / 2 + 100f, paint)
        c.drawText(title, W / 2, H / 2 - 30f, bigText)
        bigText.textSize = 60f
        c.drawText(sub, W / 2, H / 2 + 60f, bigText)
        bigText.textSize = 110f
    }
}

package com.techsketch

import android.annotation.SuppressLint
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.provider.OpenableColumns
import android.net.Uri
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.techsketch.engine.*
import com.techsketch.io.Formats
import com.techsketch.io.ObjCodec
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private enum class Tool { ORBIT, RECT, PULL, PAINT, ERASE }
    private interface Op { fun move(x: Float, y: Float); fun end(); fun cancel() }

    private val model = Model()
    private lateinit var gl: GLSurfaceView
    private lateinit var r: SketchRenderer
    private var tool = Tool.PULL
    private var op: Op? = null
    private var color = 0xFFC9A27E.toInt()
    private var lx = 0f; private var ly = 0f

    private val openDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { u -> transfer(u, false) } }
    private val saveDoc = registerForActivityResult(ActivityResultContracts.CreateDocument("model/obj")) { it?.let { u -> transfer(u, true) } }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        model.addRect(-2f, -2f, 2f, 2f, color); model.faces[0].let { model.pushPull(it, 2f) }
        r = SketchRenderer(model)
        gl = GLSurfaceView(this).apply { setEGLContextClientVersion(3); setEGLConfigChooser(8, 8, 8, 8, 16, 0); setRenderer(r); renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY }

        val bar = LinearLayout(this).apply { setPadding(8, 8, 8, 8) }
        fun btn(t: String, a: () -> Unit) = Button(this).apply { text = t; minHeight = 132; setOnClickListener { a() } }.also { bar.addView(it) }
        btn("Orbit") { tool = Tool.ORBIT }; btn("Rect") { tool = Tool.RECT }; btn("Push/Pull") { tool = Tool.PULL }
        btn("Paint") { tool = Tool.PAINT; color = listOf(0xFFC9A27E, 0xFF5B8DEF, 0xFFE0645A, 0xFF6BBF7A).random().toInt() }
        btn("Erase") { tool = Tool.ERASE }
        btn("Undo") { synchronized(model) { model.undo() }; r.dirty = true }
        btn("Import") { openDoc.launch(arrayOf("*/*")) }; btn("Export") { saveDoc.launch("model.obj") }
        val scroll = HorizontalScrollView(this).apply { addView(bar) }
        val root = FrameLayout(this).apply {
            addView(gl, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(scroll, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM))
        }
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, i -> scroll.setPadding(0, 0, 0, i.getInsets(WindowInsetsCompat.Type.systemBars()).bottom); i }

        val pinch = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean { r.dist = (r.dist / d.scaleFactor).coerceIn(2f, 120f); return true }
        })
        gl.setOnTouchListener { _, e ->
            pinch.onTouchEvent(e)
            if (e.pointerCount > 1) { op?.cancel(); op = null; return@setOnTouchListener true }
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { lx = e.x; ly = e.y; if (tool != Tool.ORBIT) startTool(e.x, e.y) }
                MotionEvent.ACTION_MOVE -> {
                    if (tool == Tool.ORBIT) { r.th -= (e.x - lx) * .008f; r.ph = (r.ph - (e.y - ly) * .008f).coerceIn(.05f, 1.52f) } else op?.move(e.x, e.y)
                    lx = e.x; ly = e.y
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { op?.end(); op = null }
            }
            true
        }
    }

    private fun snap(v: V) = V((v.x / .5f).roundToInt() * .5f, 0f, (v.z / .5f).roundToInt() * .5f)

    private fun startTool(x: Float, y: Float) {
        when (tool) {
            Tool.RECT -> {
                val a = r.ground(x, y)?.let(::snap) ?: return
                model.snapshot()
                op = object : Op {
                    var ok = false
                    override fun move(x: Float, y: Float) {
                        val b = r.ground(x, y)?.let(::snap) ?: return
                        synchronized(model) { model.restoreTop(); ok = abs(b.x - a.x) >= .5f && abs(b.z - a.z) >= .5f; if (ok) model.addRect(a.x, a.z, b.x, b.z, color) }
                        r.dirty = true
                    }
                    override fun end() { if (!ok) cancel() }
                    override fun cancel() { synchronized(model) { model.restoreTop(); model.dropTop() }; r.dirty = true }
                }
            }
            Tool.PULL -> {
                val (o, d) = r.ray(x, y)
                val hit = synchronized(model) { model.pick(o, d) } ?: return
                val fi = model.faces.indexOf(hit.first); val p = o + d * hit.second; val n = model.normal(hit.first)
                val s0 = r.screen(p); val s1 = r.screen(p + n)
                val vx = s1.first - s0.first; val vy = s1.second - s0.second; val l2 = max(vx * vx + vy * vy, 4f)
                model.snapshot()
                op = object : Op {
                    var moved = false
                    override fun move(mx: Float, my: Float) {
                        val dd = (((mx - x) * vx + (my - y) * vy) / l2).coerceIn(-80f, 80f)
                        val s = (dd / .1f).roundToInt() * .1f
                        synchronized(model) { model.restoreTop(); moved = abs(s) > 1e-4f; if (moved) model.pushPull(model.faces[fi], s) }
                        r.dirty = true
                    }
                    override fun end() { if (!moved) cancel() }
                    override fun cancel() { synchronized(model) { model.restoreTop(); model.dropTop() }; r.dirty = true }
                }
            }
            Tool.PAINT, Tool.ERASE -> {
                val (o, d) = r.ray(x, y)
                synchronized(model) {
                    val f = model.pick(o, d)?.first ?: return
                    model.snapshot(); if (tool == Tool.PAINT) f.color = color else model.faces.remove(f)
                }
                r.dirty = true
            }
            else -> {}
        }
    }

    private fun transfer(uri: Uri, write: Boolean) {
        try {
            val name = contentResolver.query(uri, null, null, null, null)?.use { c -> c.moveToFirst(); c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) } ?: "model.obj"
            val codec = if (write) ObjCodec else Formats.forName(name) ?: throw UnsupportedOperationException("Unsupported file type: $name")
            if (write) contentResolver.openOutputStream(uri)!!.use { synchronized(model) { codec.write(model, it) } }
            else { val m = contentResolver.openInputStream(uri)!!.use { codec.read(it) }; synchronized(model) { model.load(m) } }
            r.dirty = true
        } catch (e: Exception) { Toast.makeText(this, e.message ?: "Failed", Toast.LENGTH_LONG).show() }
    }
}

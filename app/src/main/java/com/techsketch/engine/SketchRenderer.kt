package com.techsketch.engine

import android.opengl.GLES30 as GL
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class SketchRenderer(private val model: Model) : GLSurfaceView.Renderer {
    @Volatile var dirty = true
    @Volatile var th = 0.8f; @Volatile var ph = 1.0f; @Volatile var dist = 14f
    var target = V(0f, 1f, 0f)
    private var w = 1; private var h = 1
    private val proj = FloatArray(16); private val view = FloatArray(16); private val vp = FloatArray(16); private val inv = FloatArray(16)
    private var prog = 0
    private var tri: FloatBuffer? = null; private var triN = 0
    private var lin: FloatBuffer? = null; private var linN = 0

    private val vs = """#version 300 es
        uniform mat4 uMVP; in vec3 aPos; in vec3 aNor; in vec3 aCol; out vec3 vN; out vec3 vC;
        void main(){ vN = aNor; vC = aCol; gl_Position = uMVP * vec4(aPos, 1.0); }"""
    private val fs = """#version 300 es
        precision mediump float; uniform float uFlat; in vec3 vN; in vec3 vC; out vec4 o;
        void main(){ float l = 1.0; if (uFlat < 0.5) l = 0.35 + 0.65 * abs(dot(normalize(vN), normalize(vec3(0.4, 0.8, 0.5)))); o = vec4(vC * l, 1.0); }"""

    private fun shader(type: Int, src: String) = GL.glCreateShader(type).also { GL.glShaderSource(it, src); GL.glCompileShader(it) }

    override fun onSurfaceCreated(g: GL10?, c: EGLConfig?) {
        GL.glClearColor(0.93f, 0.95f, 0.97f, 1f); GL.glEnable(GL.GL_DEPTH_TEST)
        prog = GL.glCreateProgram().also { GL.glAttachShader(it, shader(GL.GL_VERTEX_SHADER, vs)); GL.glAttachShader(it, shader(GL.GL_FRAGMENT_SHADER, fs)); GL.glLinkProgram(it) }
    }

    override fun onSurfaceChanged(g: GL10?, width: Int, height: Int) {
        w = width; h = height; GL.glViewport(0, 0, w, h)
        Matrix.perspectiveM(proj, 0, 45f, w.toFloat() / h, 0.1f, 500f)
    }

    override fun onDrawFrame(g: GL10?) {
        GL.glClear(GL.GL_COLOR_BUFFER_BIT or GL.GL_DEPTH_BUFFER_BIT)
        val ex = target.x + dist * sin(ph) * sin(th); val ey = target.y + dist * cos(ph); val ez = target.z + dist * sin(ph) * cos(th)
        Matrix.setLookAtM(view, 0, ex, ey, ez, target.x, target.y, target.z, 0f, 1f, 0f)
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0); Matrix.invertM(inv, 0, vp, 0)
        if (dirty) { rebuild(); dirty = false }
        GL.glUseProgram(prog)
        GL.glUniformMatrix4fv(GL.glGetUniformLocation(prog, "uMVP"), 1, false, vp, 0)
        GL.glEnable(GL.GL_POLYGON_OFFSET_FILL); GL.glPolygonOffset(1f, 1f)
        tri?.let { draw(it, triN, GL.GL_TRIANGLES, 0f) }
        GL.glDisable(GL.GL_POLYGON_OFFSET_FILL)
        lin?.let { draw(it, linN, GL.GL_LINES, 1f) }
    }

    private fun draw(b: FloatBuffer, n: Int, mode: Int, flat: Float) {
        GL.glUniform1f(GL.glGetUniformLocation(prog, "uFlat"), flat)
        for ((i, name) in listOf("aPos", "aNor", "aCol").withIndex()) {
            val loc = GL.glGetAttribLocation(prog, name); b.position(i * 3)
            GL.glVertexAttribPointer(loc, 3, GL.GL_FLOAT, false, 36, b); GL.glEnableVertexAttribArray(loc)
        }
        GL.glDrawArrays(mode, 0, n)
    }

    private fun rebuild() {
        val t = ArrayList<Float>(); val l = ArrayList<Float>()
        fun vert(a: ArrayList<Float>, p: V, n: V, c: FloatArray) { a += p.x; a += p.y; a += p.z; a += n.x; a += n.y; a += n.z; a += c[0]; a += c[1]; a += c[2] }
        val z = V(0f, 0f, 0f); val dark = floatArrayOf(.12f, .14f, .17f); val gray = floatArrayOf(.66f, .7f, .74f)
        for (i in -20..20) { vert(l, V(i.toFloat(), 0f, -20f), z, gray); vert(l, V(i.toFloat(), 0f, 20f), z, gray); vert(l, V(-20f, 0f, i.toFloat()), z, gray); vert(l, V(20f, 0f, i.toFloat()), z, gray) }
        synchronized(model) {
            for (f in model.faces) {
                val n = model.normal(f); val col = f.color
                val c = floatArrayOf((col shr 16 and 255) / 255f, (col shr 8 and 255) / 255f, (col and 255) / 255f)
                val p = f.idx.map { model.verts[it] }
                for (k in 1 until p.size - 1) { vert(t, p[0], n, c); vert(t, p[k], n, c); vert(t, p[k + 1], n, c) }
                for (k in p.indices) { vert(l, p[k], n, dark); vert(l, p[(k + 1) % p.size], n, dark) }
            }
        }
        tri = buf(t); triN = t.size / 9; lin = buf(l); linN = l.size / 9
    }

    private fun buf(a: List<Float>) = ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().also { it.put(a.toFloatArray()); it.position(0) }

    // ---- picking helpers (UI thread; use the matrices from the last frame)
    fun ray(px: Float, py: Float): Pair<V, V> {
        val nx = 2f * px / w - 1f; val ny = 1f - 2f * py / h
        val a = FloatArray(4); val b = FloatArray(4)
        Matrix.multiplyMV(a, 0, inv, 0, floatArrayOf(nx, ny, -1f, 1f), 0); Matrix.multiplyMV(b, 0, inv, 0, floatArrayOf(nx, ny, 1f, 1f), 0)
        val p0 = V(a[0] / a[3], a[1] / a[3], a[2] / a[3]); val p1 = V(b[0] / b[3], b[1] / b[3], b[2] / b[3])
        return p0 to (p1 - p0).unit()
    }
    fun ground(px: Float, py: Float): V? { val (o, d) = ray(px, py); if (abs(d.y) < 1e-6f) return null; val t = -o.y / d.y; return if (t > 0) o + d * t else null }
    fun screen(p: V): Pair<Float, Float> {
        val o = FloatArray(4); Matrix.multiplyMV(o, 0, vp, 0, floatArrayOf(p.x, p.y, p.z, 1f), 0)
        return ((o[0] / o[3] + 1f) / 2f * w) to ((1f - o[1] / o[3]) / 2f * h)
    }
}

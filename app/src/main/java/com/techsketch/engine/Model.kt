package com.techsketch.engine

import kotlin.math.abs
import kotlin.math.sqrt

data class V(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: V) = V(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: V) = V(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = V(x * s, y * s, z * s)
    infix fun dot(o: V) = x * o.x + y * o.y + z * o.z
    infix fun cross(o: V) = V(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun unit(): V { val l = sqrt(this dot this); return if (l < 1e-9f) this else this * (1f / l) }
}

/** A planar polygon: indices into Model.verts, wound counter-clockwise from outside. */
class Face(var idx: IntArray, var color: Int = 0xFFC9A27E.toInt())

/** Polygon-mesh model: the foundation for Line/Offset/Follow Me/groups later. */
class Model {
    val verts = ArrayList<V>()
    val faces = ArrayList<Face>()
    private class Snap(val v: List<V>, val f: List<Face>)
    private val stack = ArrayDeque<Snap>()

    // ---- undo: snapshot() before an edit; restoreTop() re-applies it while keeping it (live drags)
    fun snapshot() { stack.addLast(Snap(verts.toList(), faces.map { Face(it.idx.copyOf(), it.color) })); if (stack.size > 60) stack.removeFirst() }
    fun restoreTop() { stack.lastOrNull()?.let { s -> verts.clear(); verts.addAll(s.v); faces.clear(); faces.addAll(s.f.map { Face(it.idx.copyOf(), it.color) }) } }
    fun dropTop() { if (stack.isNotEmpty()) stack.removeLast() }
    fun undo() { restoreTop(); dropTop() }
    fun load(o: Model) { snapshot(); verts.clear(); verts.addAll(o.verts); faces.clear(); faces.addAll(o.faces) }

    // ---- geometry
    fun normal(f: Face): V { // Newell's method
        var x = 0f; var y = 0f; var z = 0f
        for (i in f.idx.indices) {
            val a = verts[f.idx[i]]; val b = verts[f.idx[(i + 1) % f.idx.size]]
            x += (a.y - b.y) * (a.z + b.z); y += (a.z - b.z) * (a.x + b.x); z += (a.x - b.x) * (a.y + b.y)
        }
        return V(x, y, z).unit()
    }

    fun addRect(x0: Float, z0: Float, x1: Float, z1: Float, color: Int) {
        val b = verts.size
        verts += V(x0, 0f, z0); verts += V(x0, 0f, z1); verts += V(x1, 0f, z1); verts += V(x1, 0f, z0)
        val f = Face(intArrayOf(b, b + 1, b + 2, b + 3), color)
        if (normal(f).y < 0) f.idx = f.idx.reversedArray()
        faces += f
    }

    /** SketchUp-style Push/Pull: extrude face along its normal by d (negative = push in). */
    fun pushPull(f: Face, d: Float) {
        val n = normal(f); val old = f.idx
        val attached = faces.any { it !== f && it.idx.any(old::contains) }
        val top = IntArray(old.size) { verts += verts[old[it]] + n * d; verts.size - 1 }
        for (i in old.indices) { val j = (i + 1) % old.size; faces += Face(intArrayOf(old[i], old[j], top[j], top[i]), f.color) }
        if (!attached) faces += Face(old.reversedArray(), f.color)
        f.idx = top
    }

    /** Closest face hit by a ray (double-sided Möller–Trumbore over a triangle fan). */
    fun pick(o: V, d: V): Pair<Face, Float>? {
        var best: Pair<Face, Float>? = null
        for (f in faces) for (k in 1 until f.idx.size - 1) {
            val t = rayTri(o, d, verts[f.idx[0]], verts[f.idx[k]], verts[f.idx[k + 1]]) ?: continue
            if (best == null || t < best.second) best = f to t
        }
        return best
    }

    private fun rayTri(o: V, d: V, a: V, b: V, c: V): Float? {
        val e1 = b - a; val e2 = c - a; val p = d cross e2; val det = e1 dot p
        if (abs(det) < 1e-9f) return null
        val s = o - a; val u = (s dot p) / det; if (u < 0f || u > 1f) return null
        val q = s cross e1; val v = (d dot q) / det; if (v < 0f || u + v > 1f) return null
        val t = (e2 dot q) / det; return if (t > 0f) t else null
    }
}

package com.techsketch.io

import com.techsketch.engine.*
import java.io.*

interface ModelCodec { fun read(i: InputStream): Model; fun write(m: Model, o: OutputStream) }

object Formats {
    fun forName(name: String): ModelCodec? = when (name.substringAfterLast('.').lowercase()) {
        "obj" -> ObjCodec; "skp" -> SkpCodec; "dwg" -> DwgCodec; else -> null
    }
}

object ObjCodec : ModelCodec {
    override fun read(i: InputStream): Model {
        val m = Model()
        BufferedReader(InputStreamReader(i)).forEachLine { line ->
            val t = line.trim().split(Regex("\\s+"))
            when (t[0]) {
                "v" -> m.verts += V(t[1].toFloat(), t[2].toFloat(), t[3].toFloat())
                "f" -> m.faces += Face(IntArray(t.size - 1) { val k = t[it + 1].substringBefore('/').toInt(); if (k < 0) m.verts.size + k else k - 1 })
            }
        }
        return m
    }
    override fun write(m: Model, o: OutputStream) {
        val w = o.bufferedWriter()
        m.verts.forEach { w.write("v ${it.x} ${it.y} ${it.z}\n") }
        m.faces.forEach { f -> w.write("f " + f.idx.joinToString(" ") { (it + 1).toString() } + "\n") }
        w.flush()
    }
}

/** Needs Trimble's licensed SketchUp C SDK (native, via JNI). See README. */
object SkpCodec : ModelCodec {
    override fun read(i: InputStream): Model = throw UnsupportedOperationException("SKP needs the licensed Trimble SketchUp SDK - export DAE/OBJ from SketchUp for now")
    override fun write(m: Model, o: OutputStream) = throw UnsupportedOperationException("SKP export needs the Trimble SketchUp SDK")
}

/** Needs a licensed library (Open Design Alliance) or LibreDWG (GPL). See README. */
object DwgCodec : ModelCodec {
    override fun read(i: InputStream): Model = throw UnsupportedOperationException("DWG needs ODA or LibreDWG - export DXF/OBJ for now")
    override fun write(m: Model, o: OutputStream) = throw UnsupportedOperationException("DWG export needs ODA or LibreDWG")
}

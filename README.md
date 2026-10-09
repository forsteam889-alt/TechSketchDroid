# TechSketch (Android 14+, Kotlin, OpenGL ES 3.0)

Open this folder in Android Studio (Koala or newer); it generates the Gradle wrapper and syncs. Run on an Android 14+ device.
NOT yet compiled or run by the author - expect to fix small build errors on first sync.

## Working now
Rectangle, Push/Pull, Paint, Erase, Undo, orbit + pinch zoom, grid snapping, OBJ import/export.

## Architecture
- engine/Model.kt - polygon mesh, undo snapshots, picking, pushPull
- engine/SketchRenderer.kt - GLES 3.0 renderer, camera, ray/screen helpers
- io/Formats.kt - ModelCodec per format; OBJ done, SKP/DWG are stubs
- MainActivity.kt - tool palette, touch gestures, file picker (Storage Access Framework)

## SKP and DWG (proprietary)
- SKP: wrap Trimble's SketchUp C SDK (license required) as JNI/NDK and implement SkpCodec. Interim: export DAE/OBJ from SketchUp.
- DWG: license the Open Design Alliance toolkit, or use LibreDWG (GPL - affects how you distribute). Interim: DXF/OBJ.

## Roadmap
1. Line tool + edge model, Rotate, Scale, Offset, Follow Me  2. Groups/components/layers, Outliner
3. Measurements box (typed values), Tape Measure, Dimensions  4. Scenes, shadows, styles, sections
5. DAE/DXF codecs, then SKP/DWG via SDKs

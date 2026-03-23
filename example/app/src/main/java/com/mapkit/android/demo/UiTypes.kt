package com.studiomk.mapkit.demo

internal enum class AppTab { Map, Settings }
internal enum class DrawMode { None, Annotation, Polyline, Polygon, Circle }
internal enum class AnnotationVisualStyle { Marker, Custom }
internal enum class MarkerGlyphMode { GlyphText, GlyphImage }
internal enum class ZoomRangePreset {
    none,
    city,
    district
}
internal enum class PoiFilterPreset {
    all,
    none,
    includeCafePark,
    excludeCafePark
}

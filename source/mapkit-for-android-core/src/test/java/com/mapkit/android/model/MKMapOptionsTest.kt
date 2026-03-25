package com.studiomk.mapkit.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MKMapOptionsTest {

    @Test
    fun mapOptions_defaultsToPoiAll() {
        val options = MKMapOptions()
        assertEquals(MKPoiFilter.All, options.poiFilter)
    }
}

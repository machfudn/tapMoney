package id.tapmoney.nfc.parser.transit

import org.junit.Assert.assertEquals
import org.junit.Test

class KmtStationCatalogTest {
    @Test
    fun getStationName_resolvesKnownStations() {
        assertEquals("Bogor", KmtStationCatalog.getStationName(0x0101))
        assertEquals("Tanah Abang", KmtStationCatalog.getStationName(0x0001))
        assertEquals("Manggarai", KmtStationCatalog.getStationName(0x0111))
        assertEquals("Jakarta Kota", KmtStationCatalog.getStationName(0x0119))
    }

    @Test
    fun getStationName_returnsFallbackForUnknown() {
        assertEquals("Station 0x9999", KmtStationCatalog.getStationName(0x9999))
    }
}

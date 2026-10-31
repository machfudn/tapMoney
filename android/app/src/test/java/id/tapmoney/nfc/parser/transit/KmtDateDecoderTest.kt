package id.tapmoney.nfc.parser.transit

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class KmtDateDecoderTest {
    @Test
    fun decode_zeroBytes_returnsNull() {
        assertNull(KmtDateDecoder.decode(byteArrayOf(0, 0, 0, 0)))
    }

    @Test
    fun decode_validOffset_returnsFormattedDateTime() {
        // Offset seconds sample: 600,000,000 seconds past epoch
        val offset = 600000000
        val bytes = byteArrayOf(
            ((offset shr 24) and 0xFF).toByte(),
            ((offset shr 16) and 0xFF).toByte(),
            ((offset shr 8) and 0xFF).toByte(),
            (offset and 0xFF).toByte()
        )
        val result = KmtDateDecoder.decode(bytes)
        assertNotNull(result)
        assertNotNull(result?.first)
        assertNotNull(result?.second)
    }
}

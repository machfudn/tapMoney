package id.tapmoney.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NfcEngineTest {
    @Test fun statusWordNeeds9000Or9100() {
        assertTrue(NfcEngine.isSuccess(byteArrayOf(0x01, 0x90.toByte(), 0x00)))
        assertTrue(NfcEngine.isSuccess(byteArrayOf(0x91.toByte(), 0x00)))
        assertFalse(NfcEngine.isSuccess(byteArrayOf(0x61, 0x10)))
        assertFalse(NfcEngine.isSuccess(byteArrayOf(0x90.toByte(), 0x01)))
    }

    @Test fun hexConversionRejectsOddLength() {
        assertEquals("A0FF", NfcEngine.bytesToHex(NfcEngine.hexToBytes("a0 ff")))
        try {
            NfcEngine.hexToBytes("ABC")
            throw AssertionError("Expected odd-length APDU to fail")
        } catch (_: IllegalArgumentException) {
        }
    }
}

package ir.pocora.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageCodecTest {
    // --- Encode ---

    @Test
    fun encode_namesTheType() {
        val text = String(MessageCodec.encode(PairAnswer(true, "id", "Pixel 6")), Charsets.UTF_8)
        assertTrue(text.contains("\"type\":\"pair_answer\""))
    }

    // --- Decode ---

    @Test
    fun decode_roundTripRequest() {
        val request = PairRequest("id", "Samsung Galaxy A15", "14")
        assertEquals(request, MessageCodec.decode(MessageCodec.encode(request)))
    }

    @Test
    fun decode_roundTripRequestWithVersion() {
        val request = PairRequest("id", "Samsung Galaxy A15", "14", version = "1.2.0")
        assertEquals(request, MessageCodec.decode(MessageCodec.encode(request)))
    }

    @Test
    fun decode_roundTripRequestWithToken() {
        val request = PairRequest("id", "Samsung Galaxy A15", "14", "token")
        assertEquals(request, MessageCodec.decode(MessageCodec.encode(request)))
    }

    @Test
    fun decode_requestFromOlderAppHasNoToken() {
        val text = """{"type":"pair_request","id":"id","deviceName":"Pixel 6","androidVersion":"14"}"""
        assertEquals(PairRequest("id", "Pixel 6", "14"), MessageCodec.decode(text.toByteArray()))
    }

    @Test
    fun decode_roundTripAnswer() {
        val answer = PairAnswer(false, "id", "موبایل مادر")
        assertEquals(answer, MessageCodec.decode(MessageCodec.encode(answer)))
    }

    @Test
    fun decode_roundTripChildName() {
        val answer = SyncAnswer(1_700_000_000_000, "علی")
        assertEquals(answer, MessageCodec.decode(MessageCodec.encode(answer)))
    }

    @Test
    fun decode_syncAnswerWithoutName() {
        val text = """{"type":"sync_answer","time":1}"""
        assertEquals(SyncAnswer(1), MessageCodec.decode(text.toByteArray()))
    }

    @Test
    fun decode_ignoresUnknownFields() {
        val text = """{"type":"pair_answer","accepted":true,"id":"id","deviceName":"Pixel 6","later":1}"""
        assertEquals(PairAnswer(true, "id", "Pixel 6"), MessageCodec.decode(text.toByteArray()))
    }

    @Test
    fun decode_unknownType() {
        assertNull(MessageCodec.decode("""{"type":"something_else"}""".toByteArray()))
    }

    @Test
    fun decode_notJson() {
        assertNull(MessageCodec.decode("hello".toByteArray()))
    }

    @Test
    fun decode_missingField() {
        assertNull(MessageCodec.decode("""{"type":"pair_request","id":"id"}""".toByteArray()))
    }
}

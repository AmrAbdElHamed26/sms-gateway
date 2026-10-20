package dev.smsgateway.app

import dev.smsgateway.app.capture.sms.SmsAssembler
import dev.smsgateway.app.capture.sms.SmsPart
import org.junit.Assert.assertEquals
import org.junit.Test

class SmsAssemblerTest {
    @Test
    fun `joins multipart parts in order without separators`() {
        val parts = listOf(
            SmsPart("VF-Cash", "تم استلام مبلغ 150.00 جنيه من ", 1000L),
            SmsPart("VF-Cash", "01012345678 رقم العملية 987654", 1001L),
        )
        val result = SmsAssembler.assemble(parts)
        assertEquals(1, result.size)
        assertEquals("تم استلام مبلغ 150.00 جنيه من 01012345678 رقم العملية 987654", result[0].body)
        assertEquals("VF-Cash", result[0].sender)
        assertEquals(1000L, result[0].timestampMs)
    }

    @Test
    fun `separates messages from different senders`() {
        val result = SmsAssembler.assemble(
            listOf(SmsPart("A", "one", 1), SmsPart("B", "two", 2), SmsPart("A", "three", 3))
        )
        assertEquals(listOf("A" to "onethree", "B" to "two"), result.map { it.sender to it.body })
    }

    @Test
    fun `missing sender and body are tolerated`() {
        val result = SmsAssembler.assemble(listOf(SmsPart(null, null, 5), SmsPart("  ", "x", 6)))
        assertEquals(1, result.size)
        assertEquals(SmsAssembler.UNKNOWN_SENDER, result[0].sender)
        assertEquals("x", result[0].body)
    }
}

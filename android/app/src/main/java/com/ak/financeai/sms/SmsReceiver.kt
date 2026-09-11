package com.ak.financeai.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.ak.financeai.data.FinanceDb
import com.ak.financeai.data.SmsRecord
import com.ak.financeai.ml.LocalAnalyzer

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return

        val sender = messages.first().originatingAddress.orEmpty()
        val body = messages.joinToString("") { it.messageBody.orEmpty() }
        if (body.isBlank()) return

        val result = LocalAnalyzer().analyze(sender, body)
        val sourceId = "rx:${System.currentTimeMillis()}:${sender.hashCode()}:${body.hashCode()}"
        FinanceDb(context.applicationContext).insertSms(
            SmsRecord(
                sourceId = sourceId,
                sender = sender,
                bank = result.bank,
                type = result.type,
                amount = result.amount,
                body = body,
                sanitizedBody = result.sanitizedText,
                confidence = result.confidence,
                createdAt = System.currentTimeMillis()
            )
        )
    }
}

package com.ak.financeai.sms

import android.content.Context
import android.provider.Telephony
import com.ak.financeai.data.FinanceDb
import com.ak.financeai.data.SmsRecord
import com.ak.financeai.ml.LocalAnalyzer

object SmsHistoryImporter {
    fun importAll(context: Context): Int {
        val db = FinanceDb(context.applicationContext)
        val analyzer = LocalAnalyzer()
        var inserted = 0

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )

        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            null,
            null,
            Telephony.Sms.DATE + " DESC"
        )?.use { c ->
            val idIx = c.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addressIx = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIx = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIx = c.getColumnIndexOrThrow(Telephony.Sms.DATE)

            while (c.moveToNext()) {
                val sourceId = "sms:${c.getString(idIx)}"
                val sender = c.getString(addressIx).orEmpty()
                val body = c.getString(bodyIx).orEmpty()
                val date = c.getLong(dateIx)
                if (body.isBlank()) continue

                val a = analyzer.analyze(sender, body)
                val id = db.insertSms(
                    SmsRecord(
                        sourceId = sourceId,
                        sender = sender,
                        bank = a.bank,
                        type = a.type,
                        amount = a.amount,
                        body = body,
                        sanitizedBody = a.sanitizedText,
                        confidence = a.confidence,
                        createdAt = date
                    )
                )
                if (id > 0) inserted++
            }
        }
        return inserted
    }
}

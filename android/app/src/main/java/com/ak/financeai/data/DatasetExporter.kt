package com.ak.financeai.data

import android.content.Context
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt

object DatasetExporter {

    fun exportZip(context: Context, uri: android.net.Uri): Int {
        val db = FinanceDb(context)
        val all = db.getAllSms().filter { it.reviewed && !it.userLabel.isNullOrBlank() }
        val labeled = all.shuffled(java.util.Random(42))

        val byLabel = labeled.groupBy { it.userLabel ?: MessageType.UNKNOWN.name }
        val train = mutableListOf<SmsRecord>()
        val validation = mutableListOf<SmsRecord>()
        val test = mutableListOf<SmsRecord>()

        for ((_, rows) in byLabel) {
            rows.forEachIndexed { i, row ->
                when (i % 10) {
                    0 -> test += row
                    1 -> validation += row
                    else -> train += row
                }
            }
        }

        context.contentResolver.openOutputStream(uri)?.use { raw ->
            ZipOutputStream(raw).use { zip ->
                writeEntry(zip, "dataset.jsonl", jsonLines(labeled))
                writeEntry(zip, "dataset.csv", csv(labeled))
                writeEntry(zip, "train.jsonl", jsonLines(train))
                writeEntry(zip, "validation.jsonl", jsonLines(validation))
                writeEntry(zip, "test.jsonl", jsonLines(test))
                writeEntry(zip, "label_schema.json", schema())
                writeEntry(
                    zip,
                    "manifest.json",
                    """{
  "format": "AK-Finance-AI-ML-v1",
  "records": ${labeled.size},
  "train": ${train.size},
  "validation": ${validation.size},
  "test": ${test.size},
  "seed": 42,
  "raw_sms_exported": false,
  "otp_values_exported": false,
  "text_field": "sanitized_text"
}"""
                )
            }
        }
        return labeled.size
    }

    private fun jsonLines(rows: List<SmsRecord>): String =
        rows.joinToString("\n") { r ->
            """{"message_id":${r.id},"sender":"${esc(r.sender)}","bank":"${esc(r.bank ?: "")}","is_bank":${r.type != MessageType.NON_BANK},"message_type":"${r.type.name}","transaction_direction":"${r.direction.name}","transaction_status":"${r.status.name}","amount":${r.amount ?: "null"},"currency":"IRR","payment_channel":"${r.channel.name}","predicted_type":"${r.type.name}","user_label":"${esc(r.userLabel ?: r.type.name)}","confidence":${String.format(Locale.US, "%.4f", r.confidence)},"sanitized_text":"${esc(r.sanitizedBody)}","created_at":${r.createdAt}}"""
        }

    private fun csv(rows: List<SmsRecord>): String {
        val header = "message_id,sender,bank,is_bank,message_type,transaction_direction,transaction_status,amount,currency,payment_channel,predicted_type,user_label,confidence,sanitized_text,created_at"
        return header + "\n" + rows.joinToString("\n") { r ->
            listOf(
                r.id.toString(), r.sender, r.bank ?: "", (r.type != MessageType.NON_BANK).toString(),
                r.type.name, r.direction.name, r.status.name, r.amount?.toString() ?: "",
                "IRR", r.channel.name, r.type.name, r.userLabel ?: r.type.name,
                String.format(Locale.US, "%.4f", r.confidence), r.sanitizedBody, r.createdAt.toString()
            ).joinToString(",") { csvEsc(it) }
        }
    }

    private fun schema(): String = """{
  "version": "1.0",
  "target": "user_label",
  "labels": {
    "BANK_TRANSACTION": "completed or financial bank transaction message",
    "BANK_OTP": "one-time password/payment authorization message",
    "BANK_ALERT": "bank-related alert that is not a completed transaction",
    "NON_BANK": "not a bank message",
    "UNKNOWN": "insufficient evidence"
  },
  "features": ["sender","bank","sanitized_text","amount","transaction_direction","transaction_status","payment_channel"],
  "privacy": {"raw_sms": false, "otp_values": false}
}"""

    private fun writeEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        BufferedWriter(OutputStreamWriter(zip, Charsets.UTF_8)).use { w -> w.write(content) }
        zip.closeEntry()
    }

    private fun esc(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")

    private fun csvEsc(s: String): String = "\"" + s.replace("\"", "\"\"").replace("\n", " ").replace("\r", " ") + "\""
}

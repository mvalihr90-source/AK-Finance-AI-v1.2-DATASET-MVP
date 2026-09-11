package com.ak.financeai.data

enum class MessageType {
    BANK_TRANSACTION, BANK_OTP, BANK_ALERT, NON_BANK, UNKNOWN
}

enum class TransactionDirection {
    DEBIT, CREDIT, UNKNOWN
}

enum class TransactionStatus {
    PENDING, COMPLETED, FAILED, REFUNDED, REVERSED, UNKNOWN
}

enum class PaymentChannel {
    ONLINE, POS, ATM, TRANSFER, CARD_TO_CARD, UNKNOWN
}

data class AnalysisResult(
    val bank: String?,
    val type: MessageType,
    val amount: Long?,
    val confidence: Float,
    val sanitizedText: String
)

data class SmsRecord(
    val id: Long = 0,
    val sourceId: String? = null,
    val sender: String,
    val bank: String?,
    val type: MessageType,
    val amount: Long?,
    val body: String,
    val sanitizedBody: String,
    val confidence: Float,
    val createdAt: Long,
    val reviewed: Boolean = false,
    val direction: TransactionDirection = TransactionDirection.UNKNOWN,
    val status: TransactionStatus = TransactionStatus.UNKNOWN,
    val channel: PaymentChannel = PaymentChannel.UNKNOWN,
    val userLabel: String? = null
)

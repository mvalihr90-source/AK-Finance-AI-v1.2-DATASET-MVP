package com.ak.financeai.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class FinanceDb(context: Context) : SQLiteOpenHelper(context, "ak_finance.db", null, 3) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE sms_records (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                source_id TEXT UNIQUE,
                sender TEXT NOT NULL,
                bank TEXT,
                type TEXT NOT NULL,
                amount INTEGER,
                body TEXT NOT NULL,
                sanitized_body TEXT NOT NULL,
                confidence REAL NOT NULL,
                created_at INTEGER NOT NULL,
                reviewed INTEGER NOT NULL DEFAULT 0,
                direction TEXT NOT NULL DEFAULT 'UNKNOWN',
                status TEXT NOT NULL DEFAULT 'UNKNOWN',
                channel TEXT NOT NULL DEFAULT 'UNKNOWN',
                user_label TEXT
            )"""
        )
        db.execSQL(
            """CREATE TABLE transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                bank TEXT,
                amount INTEGER,
                status TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                source_sms_id INTEGER
            )"""
        )
        db.execSQL(
            """CREATE TABLE feedback (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sms_id INTEGER NOT NULL,
                label TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE sms_records ADD COLUMN reviewed INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE sms_records ADD COLUMN linked_transaction_id INTEGER")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE sms_records ADD COLUMN source_id TEXT")
            db.execSQL("ALTER TABLE sms_records ADD COLUMN direction TEXT NOT NULL DEFAULT 'UNKNOWN'")
            db.execSQL("ALTER TABLE sms_records ADD COLUMN status TEXT NOT NULL DEFAULT 'UNKNOWN'")
            db.execSQL("ALTER TABLE sms_records ADD COLUMN channel TEXT NOT NULL DEFAULT 'UNKNOWN'")
            db.execSQL("ALTER TABLE sms_records ADD COLUMN user_label TEXT")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_sms_source_id ON sms_records(source_id)")
        }
    }

    fun insertSms(r: SmsRecord): Long {
        val v = ContentValues().apply {
            put("source_id", r.sourceId)
            put("sender", r.sender)
            put("bank", r.bank)
            put("type", r.type.name)
            if (r.amount == null) putNull("amount") else put("amount", r.amount)
            put("body", r.body)
            put("sanitized_body", r.sanitizedBody)
            put("confidence", r.confidence)
            put("created_at", r.createdAt)
            put("reviewed", if (r.reviewed) 1 else 0)
            put("direction", r.direction.name)
            put("status", r.status.name)
            put("channel", r.channel.name)
            put("user_label", r.userLabel)
        }
        return try {
            writableDatabase.insertWithOnConflict(
                "sms_records", null, v, SQLiteDatabase.CONFLICT_IGNORE
            )
        } catch (_: Exception) {
            -1L
        }
    }

    fun updateLabel(
        id: Long,
        type: MessageType,
        bank: String?,
        amount: Long?,
        direction: TransactionDirection,
        status: TransactionStatus,
        channel: PaymentChannel
    ) {
        val v = ContentValues().apply {
            put("type", type.name)
            put("bank", bank)
            if (amount == null) putNull("amount") else put("amount", amount)
            put("direction", direction.name)
            put("status", status.name)
            put("channel", channel.name)
            put("user_label", type.name)
            put("reviewed", 1)
        }
        writableDatabase.update("sms_records", v, "id=?", arrayOf(id.toString()))

        val feedback = ContentValues().apply {
            put("sms_id", id)
            put("label", type.name)
            put("created_at", System.currentTimeMillis())
        }
        writableDatabase.insert("feedback", null, feedback)
    }

    fun countSms(): Int = count("sms_records")
    fun countReviewed(): Int = countWhere("sms_records", "reviewed=1")
    fun countTransactions(): Int = count("transactions")

    private fun count(table: String): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $table", null).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }

    private fun countWhere(table: String, where: String): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $table WHERE $where", null).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }

    fun getAllSms(): List<SmsRecord> {
        val out = mutableListOf<SmsRecord>()
        readableDatabase.rawQuery(
            """SELECT id,source_id,sender,bank,type,amount,body,sanitized_body,confidence,
               created_at,reviewed,direction,status,channel,user_label
               FROM sms_records ORDER BY created_at DESC""", null
        ).use { c ->
            while (c.moveToNext()) out += fromCursor(c)
        }
        return out
    }

    fun latestSms(): SmsRecord? = readableDatabase.rawQuery(
        """SELECT id,source_id,sender,bank,type,amount,body,sanitized_body,confidence,
           created_at,reviewed,direction,status,channel,user_label
           FROM sms_records ORDER BY created_at DESC LIMIT 1""", null
    ).use { c -> if (c.moveToFirst()) fromCursor(c) else null }

    private fun fromCursor(c: android.database.Cursor): SmsRecord =
        SmsRecord(
            id = c.getLong(0),
            sourceId = c.getString(1),
            sender = c.getString(2),
            bank = c.getString(3),
            type = runCatching { MessageType.valueOf(c.getString(4)) }.getOrDefault(MessageType.UNKNOWN),
            amount = if (c.isNull(5)) null else c.getLong(5),
            body = c.getString(6),
            sanitizedBody = c.getString(7),
            confidence = c.getFloat(8),
            createdAt = c.getLong(9),
            reviewed = c.getInt(10) == 1,
            direction = runCatching { TransactionDirection.valueOf(c.getString(11)) }.getOrDefault(TransactionDirection.UNKNOWN),
            status = runCatching { TransactionStatus.valueOf(c.getString(12)) }.getOrDefault(TransactionStatus.UNKNOWN),
            channel = runCatching { PaymentChannel.valueOf(c.getString(13)) }.getOrDefault(PaymentChannel.UNKNOWN),
            userLabel = c.getString(14)
        )

    fun createTransaction(bank: String?, amount: Long?, sourceSmsId: Long): Long {
        val v = ContentValues().apply {
            put("bank", bank)
            if (amount == null) putNull("amount") else put("amount", amount)
            put("status", TransactionStatus.COMPLETED.name)
            put("created_at", System.currentTimeMillis())
            put("source_sms_id", sourceSmsId)
        }
        return writableDatabase.insert("transactions", null, v)
    }
}

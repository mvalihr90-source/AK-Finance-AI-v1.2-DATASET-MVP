package com.ak.financeai

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.ak.financeai.data.*
import com.ak.financeai.sms.SmsHistoryImporter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private lateinit var db: FinanceDb
    private lateinit var status: TextView
    private lateinit var summary: TextView
    private lateinit var list: LinearLayout
    private lateinit var importButton: Button
    private lateinit var exportButton: Button
    private val createExportCode = 7001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = FinanceDb(this)
        buildUi()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun buildUi() {
        val root = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }
        root.addView(box)

        box.addView(TextView(this).apply {
            text = "AK Finance AI  v1.2"
            textSize = 28f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })

        status = TextView(this).apply { textSize = 16f }
        box.addView(status)

        val permission = Button(this).apply {
            text = "فعال‌سازی دسترسی پیامک"
            setOnClickListener { requestSmsPermissions() }
        }
        box.addView(permission)

        importButton = Button(this).apply {
            text = "وارد کردن همه پیامک‌های قبلی"
            setOnClickListener { importHistory() }
        }
        box.addView(importButton)

        exportButton = Button(this).apply {
            text = "خروجی Dataset برای Machine Learning"
            setOnClickListener { chooseExportLocation() }
        }
        box.addView(exportButton)

        summary = TextView(this).apply {
            textSize = 17f
            setPadding(0, 16, 0, 16)
        }
        box.addView(summary)

        box.addView(TextView(this).apply {
            text = "پیام‌ها — برای Label کردن روی هر پیام بزنید"
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(list)

        setContentView(root)
    }

    private fun requestSmsPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS),
            1001
        )
    }

    private fun importHistory() {
        if (!hasSmsPermission()) {
            Toast.makeText(this, "ابتدا دسترسی SMS را فعال کنید.", Toast.LENGTH_LONG).show()
            requestSmsPermissions()
            return
        }

        importButton.isEnabled = false
        Toast.makeText(this, "در حال وارد کردن پیامک‌های قبلی...", Toast.LENGTH_SHORT).show()

        thread {
            val inserted = runCatching { SmsHistoryImporter.importAll(this) }.getOrDefault(0)
            runOnUiThread {
                importButton.isEnabled = true
                Toast.makeText(this, "$inserted پیامک جدید وارد شد.", Toast.LENGTH_LONG).show()
                refresh()
            }
        }
    }

    private fun chooseExportLocation() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, "AK-Finance-AI-dataset.zip")
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(intent, createExportCode)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != createExportCode || resultCode != RESULT_OK) return
        val uri = data?.data ?: return

        thread {
            val count = runCatching { DatasetExporter.exportZip(this, uri) }.getOrDefault(-1)
            runOnUiThread {
                if (count >= 0) {
                    Toast.makeText(this, "Dataset با $count رکورد Label شده خروجی گرفته شد.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "ساخت Dataset ناموفق بود.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun refresh() {
        val permissionOk = hasSmsPermission()
        status.text = if (permissionOk)
            "وضعیت SMS: فعال"
        else
            "وضعیت SMS: نیازمند دسترسی"

        summary.text =
            "کل پیامک‌های ذخیره‌شده: ${db.countSms()}\n" +
            "Label شده: ${db.countReviewed()}\n" +
            "تراکنش‌های قطعی: ${db.countTransactions()}"

        renderMessages()
    }

    private fun renderMessages() {
        list.removeAllViews()
        val records = db.getAllSms()

        if (records.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "هنوز پیامکی وارد نشده است. روی «وارد کردن همه پیامک‌های قبلی» بزنید."
                textSize = 16f
                setPadding(0, 12, 0, 12)
            })
            return
        }

        records.forEach { r ->
            val row = TextView(this).apply {
                text = formatRow(r)
                textSize = 15f
                setPadding(12, 16, 12, 16)
                setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
                setOnClickListener { showLabelDialog(r) }
            }
            list.addView(row)
        }
    }

    private fun formatRow(r: SmsRecord): String {
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(r.createdAt))
        val label = r.userLabel ?: r.type.name
        val preview = r.sanitizedBody.replace("\n", " ").take(110)
        return "$date\n${r.sender} | ${r.bank ?: "بانک نامشخص"}\n$label | مبلغ: ${r.amount ?: "—"} | ${r.confidence.times(100).toInt()}%\n$preview"
    }

    private fun showLabelDialog(r: SmsRecord) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 10, 30, 10)
        }

        fun label(text: String) = TextView(this).apply {
            this.text = text
            textSize = 15f
            setPadding(0, 8, 0, 4)
        }

        val typeSpinner = Spinner(this)
        typeSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            MessageType.values().map { it.name }.toTypedArray()
        )
        typeSpinner.setSelection(MessageType.values().indexOf(r.type))

        val bank = EditText(this).apply {
            hint = "بانک (مثلاً بانک ملت)"
            setText(r.bank ?: "")
        }

        val amount = EditText(this).apply {
            hint = "مبلغ (ریال)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(r.amount?.toString() ?: "")
        }

        val direction = Spinner(this)
        direction.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            TransactionDirection.values().map { it.name }.toTypedArray()
        )
        direction.setSelection(TransactionDirection.values().indexOf(r.direction))

        val status = Spinner(this)
        status.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            TransactionStatus.values().map { it.name }.toTypedArray()
        )
        status.setSelection(TransactionStatus.values().indexOf(r.status))

        val channel = Spinner(this)
        channel.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            PaymentChannel.values().map { it.name }.toTypedArray()
        )
        channel.setSelection(PaymentChannel.values().indexOf(r.channel))

        container.addView(label("نوع پیام"))
        container.addView(typeSpinner)
        container.addView(bank)
        container.addView(amount)
        container.addView(label("جهت تراکنش"))
        container.addView(direction)
        container.addView(label("وضعیت"))
        container.addView(status)
        container.addView(label("کانال پرداخت"))
        container.addView(channel)

        AlertDialog.Builder(this)
            .setTitle("Label کردن پیام")
            .setView(container)
            .setPositiveButton("ذخیره Label") { _, _ ->
                val type = MessageType.values()[typeSpinner.selectedItemPosition]
                val bankText = bank.text.toString().trim().ifBlank { null }
                val amountValue = amount.text.toString().trim().toLongOrNull()
                val dir = TransactionDirection.values()[direction.selectedItemPosition]
                val stat = TransactionStatus.values()[status.selectedItemPosition]
                val ch = PaymentChannel.values()[channel.selectedItemPosition]

                db.updateLabel(r.id, type, bankText, amountValue, dir, stat, ch)
                refresh()
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun hasSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
}

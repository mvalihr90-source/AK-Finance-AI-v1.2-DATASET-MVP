package com.ak.financeai.ml

import com.ak.financeai.data.AnalysisResult
import com.ak.financeai.data.MessageType
import java.util.regex.Pattern

class LocalAnalyzer {
    private val banks=listOf("MELLAT" to "بانک ملت","ملت" to "بانک ملت","MELLI" to "بانک ملی","ملی" to "بانک ملی","SAMAN" to "بانک سامان","سامان" to "بانک سامان","TEJARAT" to "بانک تجارت","تجارت" to "بانک تجارت")
    fun analyze(sender:String,body:String):AnalysisResult {
        val text=normalize(body)
        val bank=banks.firstOrNull{sender.contains(it.first,true)||text.contains(it.first,true)}?.second
        val otp=looksOtp(text)
        val transaction=looksTransaction(text)
        val type=when{otp->MessageType.BANK_OTP;transaction->MessageType.BANK_TRANSACTION;bank!=null->MessageType.BANK_ALERT;else->MessageType.UNKNOWN}
        val amount=extractAmount(text)
        val confidence=when{otp&&bank!=null->.96f;transaction&&bank!=null&&amount!=null->.94f;transaction&&amount!=null->.82f;bank!=null->.72f;else->.20f}
        return AnalysisResult(bank,type,amount,confidence,sanitizeOtp(text))
    }
    private fun looksOtp(t:String)=((t.contains("رمز پویا")||t.contains("رمز دوم")||t.contains("رمز یکبار")||t.contains("OTP",true))&&Regex("""\b\d{4,8}\b""").containsMatchIn(t))
    private fun looksTransaction(t:String)=listOf("کسر از حساب","برداشت","خرید موفق","تراکنش موفق","واریز","پرداخت موفق","مبلغ کسر").any{t.contains(it)}
    private fun extractAmount(t:String):Long?{
        val m=Pattern.compile("""(\d{1,3}(?:[, ]\d{3})+|\d{4,})""").matcher(t)
        while(m.find()){val v=m.group(1).replace(",","").replace(" ","").toLongOrNull();if(v!=null&&v>0)return v};return null
    }
    private fun sanitizeOtp(t:String):String{
        var o=t
        o=Regex("""(رمز\s*(?:پویا|دوم|یکبار)[^۰-۹0-9]*)([۰-۹0-9]{4,8})""").replace(o,"$1<OTP>")
        o=Regex("""(OTP[^۰-۹0-9]*)([۰-۹0-9]{4,8})""",RegexOption.IGNORE_CASE).replace(o,"$1<OTP>")
        return o
    }
    private fun normalize(t:String):String{
        val fa="۰۱۲۳۴۵۶۷۸۹";val ar="٠١٢٣٤٥٦٧٨٩"
        return buildString(t.length){for(c in t){val f=fa.indexOf(c);val a=ar.indexOf(c);append(when{f>=0->('0'.code+f).toChar();a>=0->('0'.code+a).toChar();c=='٬'->',';else->c})}}
    }
}

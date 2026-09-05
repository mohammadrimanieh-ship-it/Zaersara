package com.imanieh.zaersara.util

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.imanieh.zaersara.model.Reservation
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object ReportExport {
    fun shareExcelCsv(context: Context, rows: List<Reservation>) {
        val file = File(context.cacheDir, "zaersara-report.csv")
        val header = listOf("خانواده/سرپرست","واحد","ورود","خروج","نفرات","نوع اقامت","صبحانه","ناهار","شام","وضعیت مالی","مبلغ","تاریخ ثبت")
        file.bufferedWriter(Charsets.UTF_8).use { w ->
            w.write("\uFEFF"); w.appendLine(header.joinToString(","))
            rows.forEach { r ->
                val name = r.leaderName.ifBlank { r.primaryLastName }
                val values = listOf(name,r.unitName,r.startDate,r.endDate,r.guestCount.toString(),serviceLabel(r.serviceType),r.breakfastCount.toString(),r.lunchCount.toString(),r.dinnerCount.toString(),paymentLabel(r.paymentKind),r.amount.toString(),r.registeredAt)
                w.appendLine(values.joinToString(",") { "\"${it.replace("\"","\"\"")}\"" })
            }
        }
        share(context,file,"text/csv","گزارش زائرسرا - Excel/CSV")
    }

    fun sharePdf(context: Context, rows: List<Reservation>) {
        val doc = PdfDocument()
        val pageWidth = 842; val pageHeight = 595
        val margin = 28f
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize=18f; isFakeBoldText=true; textAlign=Paint.Align.RIGHT }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize=10f; textAlign=Paint.Align.RIGHT }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize=9f; isFakeBoldText=true; textAlign=Paint.Align.CENTER }
        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize=8.5f; textAlign=Paint.Align.CENTER }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth=.7f }
        var pageNo=0
        var page: PdfDocument.Page? = null
        var y=0f
        val cols = floatArrayOf(28f,120f,210f,285f,360f,410f,490f,555f,620f,685f,814f)
        val headers = listOf("ردیف","خانواده/سرپرست","واحد","ورود","خروج","نفر","نوع اقامت","صبحانه","ناهار","شام","مالی/مبلغ")

        fun finishPage() { page?.let { p ->
            val footer = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize=8f; textAlign=Paint.Align.CENTER }
            p.canvas.drawText("صفحه $pageNo", pageWidth/2f, pageHeight-12f, footer)
            doc.finishPage(p)
        } }
        fun startPage() {
            finishPage(); pageNo++
            page=doc.startPage(PdfDocument.PageInfo.Builder(pageWidth,pageHeight,pageNo).create())
            val c=page!!.canvas; y=34f
            c.drawText("گزارش مدیریت زائرسرا مشهد", pageWidth-margin, y, titlePaint); y+=20f
            val guests=rows.sumOf{it.guestCount}; val b=rows.sumOf{it.breakfastCount}; val l=rows.sumOf{it.lunchCount}; val d=rows.sumOf{it.dinnerCount}; val amount=rows.sumOf{it.amount}
            c.drawText("تعداد رزرو: ${rows.size}    |    نفرات: $guests    |    صبحانه: $b    |    ناهار: $l    |    شام: $d    |    مبلغ: $amount تومان", pageWidth-margin, y, subPaint); y+=15f
            c.drawText("تاریخ تهیه: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"))}", pageWidth-margin, y, subPaint); y+=16f
            for(i in headers.indices){ val left=cols[i]; val right=if(i==headers.lastIndex) pageWidth-margin else cols[i+1]; c.drawText(headers[i],(left+right)/2f,y+11f,headerPaint); c.drawRect(left,y,right,y+18f,linePaint) }
            y+=18f
        }
        fun fit(s:String,max:Int)=if(s.length<=max)s else s.take(max-1)+"…"
        startPage()
        rows.forEachIndexed { index,r ->
            if(y>pageHeight-42f) startPage()
            val c=page!!.canvas
            val name=r.leaderName.ifBlank{r.primaryLastName}
            val vals=listOf((index+1).toString(),fit(name,16),fit(r.unitName,14),JalaliCalendar.isoToJalali(r.startDate),JalaliCalendar.isoToJalali(r.endDate),r.guestCount.toString(),if(r.serviceType=="stay_with_food")"با غذا" else "بدون غذا",r.breakfastCount.toString(),r.lunchCount.toString(),r.dinnerCount.toString(),fit("${paymentLabel(r.paymentKind)} ${if(r.amount>0) r.amount else ""}",20))
            for(i in vals.indices){ val left=cols[i]; val right=if(i==vals.lastIndex) pageWidth-margin else cols[i+1]; c.drawText(vals[i],(left+right)/2f,y+12f,cellPaint); c.drawRect(left,y,right,y+19f,linePaint) }
            y+=19f
        }
        y+=10f
        if(y>pageHeight-55f) startPage()
        page!!.canvas.drawText("جمع کل: ${rows.sumOf{it.guestCount}} نفر  |  صبحانه ${rows.sumOf{it.breakfastCount}}  |  ناهار ${rows.sumOf{it.lunchCount}}  |  شام ${rows.sumOf{it.dinnerCount}}  |  مبلغ ${rows.sumOf{it.amount}} تومان",pageWidth-margin,y+12f,subPaint)
        finishPage()
        val file=File(context.cacheDir,"zaersara-report.pdf")
        FileOutputStream(file).use{doc.writeTo(it)}; doc.close()
        share(context,file,"application/pdf","گزارش PDF زائرسرا")
    }

    private fun serviceLabel(v:String)=if(v=="stay_with_food") "اقامت با غذا" else "اقامت بدون غذا"
    private fun paymentLabel(v:String)=when(v){"paid"->"پرداخت وجه";"gift"->"هدیه";else->"رایگان"}
    private fun share(context:Context,file:File,mime:String,title:String){
        val uri=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file)
        val intent=Intent(Intent.ACTION_SEND).apply{type=mime;putExtra(Intent.EXTRA_STREAM,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)}
        context.startActivity(Intent.createChooser(intent,title))
    }
}

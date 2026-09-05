package com.imanieh.zaersara.util

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.imanieh.zaersara.model.Reservation
import java.io.File
import java.io.FileOutputStream

object ReportExport {
    fun shareExcelCsv(context: Context, rows: List<Reservation>) {
        val file = File(context.cacheDir, "zaersara-report.csv")
        val header = listOf("family","unit","start","end","guests","service","breakfast","lunch","dinner","payment","amount","registered_at")
        file.bufferedWriter(Charsets.UTF_8).use { w ->
            w.write("\uFEFF")
            w.appendLine(header.joinToString(","))
            rows.forEach { r ->
                val values = listOf(r.primaryLastName,r.unitName,r.startDate,r.endDate,r.guestCount.toString(),r.serviceType,r.breakfastCount.toString(),r.lunchCount.toString(),r.dinnerCount.toString(),r.paymentKind,r.amount.toString(),r.registeredAt)
                w.appendLine(values.joinToString(",") { "\"${it.replace("\"","\"\"")}\"" })
            }
        }
        share(context,file,"text/csv","گزارش زائرسرا - Excel/CSV")
    }

    fun sharePdf(context: Context, rows: List<Reservation>) {
        val doc = PdfDocument()
        val paint = Paint().apply { textSize = 11f }
        val titlePaint = Paint().apply { textSize = 16f; isFakeBoldText = true }
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(595,842,pageNo).create())
        var canvas = page.canvas
        var y = 42f
        canvas.drawText("Zaersara Mashhad Report",36f,y,titlePaint); y += 28f
        fun newPage() {
            doc.finishPage(page); pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(595,842,pageNo).create()); canvas=page.canvas; y=42f
        }
        rows.forEachIndexed { i,r ->
            if(y>790f) newPage()
            val line = "${i+1}. ${r.primaryLastName} | ${r.unitName} | ${r.startDate} - ${r.endDate} | ${r.guestCount} | B:${r.breakfastCount} L:${r.lunchCount} D:${r.dinnerCount} | ${r.amount}"
            canvas.drawText(line.take(95),36f,y,paint); y += 18f
        }
        doc.finishPage(page)
        val file=File(context.cacheDir,"zaersara-report.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }; doc.close()
        share(context,file,"application/pdf","گزارش PDF زائرسرا")
    }

    private fun share(context:Context,file:File,mime:String,title:String){
        val uri=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file)
        val intent=Intent(Intent.ACTION_SEND).apply { type=mime; putExtra(Intent.EXTRA_STREAM,uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        context.startActivity(Intent.createChooser(intent,title))
    }
}

package com.example.utils

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.widget.Toast
import com.example.data.local.dao.OrderWithItems
import com.example.data.local.entities.Invoice
import org.json.JSONArray
import org.json.JSONObject
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

object InvoicePrinter {

    data class PrintableItem(
        val name: String,
        val quantity: Int,
        val price: Double
    )

    fun printOrderAsPdf(context: Context, orderWithItems: OrderWithItems) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val jobName = "GazaCash_Order_${orderWithItems.order.uuid.take(6)}"

        try {
            printManager.print(jobName, object : PrintDocumentAdapter() {
                private var pdfDocument: PdfDocument? = null

                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes?,
                    cancellationSignal: CancellationSignal?,
                    callback: LayoutResultCallback?,
                    extras: Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback?.onLayoutCancelled()
                        return
                    }

                    val info = PrintDocumentInfo.Builder(jobName)
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(1)
                        .build()

                    callback?.onLayoutFinished(info, true)
                }

                override fun onWrite(
                    pages: Array<out PageRange>?,
                    destination: ParcelFileDescriptor?,
                    cancellationSignal: CancellationSignal?,
                    callback: WriteResultCallback?
                ) {
                    pdfDocument = PdfDocument()
                    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Size (595 x 842 points)
                    val page = pdfDocument!!.startPage(pageInfo)
                    val canvas = page.canvas

                    val printableItems = orderWithItems.items.map {
                        PrintableItem(it.itemName, it.quantity, it.price)
                    }

                    drawInvoiceCanvas(
                        canvas = canvas,
                        docType = "طلبية معلقة محلياً",
                        docId = orderWithItems.order.uuid.take(8).uppercase(),
                        docDate = orderWithItems.order.orderDate,
                        customerName = orderWithItems.order.customerName,
                        notes = orderWithItems.order.notes,
                        items = printableItems,
                        totalAmount = orderWithItems.order.totalAmount,
                        discount = 0.0,
                        finalAmount = orderWithItems.order.totalAmount,
                        paymentType = "ميداني"
                    )

                    pdfDocument!!.finishPage(page)

                    try {
                        FileOutputStream(destination?.fileDescriptor).use { output ->
                            pdfDocument!!.writeTo(output)
                        }
                        callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: IOException) {
                        callback?.onWriteFailed(e.message)
                    } finally {
                        pdfDocument!!.close()
                        pdfDocument = null
                    }
                }
            }, null)
        } catch (e: Exception) {
            Toast.makeText(context, "فشل بدء عملية الطباعة: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun printInvoiceAsPdf(context: Context, invoice: Invoice) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val jobName = "GazaCash_Invoice_${invoice.uuid.take(6)}"

        try {
            printManager.print(jobName, object : PrintDocumentAdapter() {
                private var pdfDocument: PdfDocument? = null

                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes?,
                    cancellationSignal: CancellationSignal?,
                    callback: LayoutResultCallback?,
                    extras: Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback?.onLayoutCancelled()
                        return
                    }

                    val info = PrintDocumentInfo.Builder(jobName)
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(1)
                        .build()

                    callback?.onLayoutFinished(info, true)
                }

                override fun onWrite(
                    pages: Array<out PageRange>?,
                    destination: ParcelFileDescriptor?,
                    cancellationSignal: CancellationSignal?,
                    callback: WriteResultCallback?
                ) {
                    pdfDocument = PdfDocument()
                    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
                    val page = pdfDocument!!.startPage(pageInfo)
                    val canvas = page.canvas

                    val printableItems = mutableListOf<PrintableItem>()
                    try {
                        if (invoice.itemsJson.isNotBlank()) {
                            val jsonArray = JSONArray(invoice.itemsJson)
                            for (i in 0 until jsonArray.length()) {
                                val obj = jsonArray.getJSONObject(i)
                                val name = obj.optString("name", "صنف غير معروف")
                                val price = obj.optDouble("price", 0.0)
                                val qty = obj.optInt("quantity", 0)
                                printableItems.add(PrintableItem(name, qty, price))
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    drawInvoiceCanvas(
                        canvas = canvas,
                        docType = "فاتورة بيع رسمية",
                        docId = invoice.uuid.take(8).uppercase(),
                        docDate = invoice.invoiceDate,
                        customerName = invoice.customerName,
                        notes = "فاتورة مسجلة ومؤكدة على الخادم الرئيسي",
                        items = printableItems,
                        totalAmount = invoice.totalAmount,
                        discount = invoice.discount,
                        finalAmount = invoice.finalAmount,
                        paymentType = if (invoice.paymentType == "cash") "نقدي" else "ذمم (آجل)"
                    )

                    pdfDocument!!.finishPage(page)

                    try {
                        FileOutputStream(destination?.fileDescriptor).use { output ->
                            pdfDocument!!.writeTo(output)
                        }
                        callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: IOException) {
                        callback?.onWriteFailed(e.message)
                    } finally {
                        pdfDocument!!.close()
                        pdfDocument = null
                    }
                }
            }, null)
        } catch (e: Exception) {
            Toast.makeText(context, "فشل بدء عملية الطباعة: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun drawInvoiceCanvas(
        canvas: Canvas,
        docType: String,
        docId: String,
        docDate: Long,
        customerName: String,
        notes: String,
        items: List<PrintableItem>,
        totalAmount: Double,
        discount: Double,
        finalAmount: Double,
        paymentType: String
    ) {
        val paint = Paint().apply {
            isAntiAlias = true
        }

        // Header purple band
        paint.color = Color.parseColor("#6750A4")
        canvas.drawRect(0f, 0f, 595f, 130f, paint)

        // Banner Title
        paint.color = Color.WHITE
        paint.textSize = 28f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("غزة كاش - Gaza Cash", 595f / 2, 55f, paint)

        // Banner Subtitle
        paint.textSize = 13f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("نظام الفواتير الميداني الذكي • غزة، فلسطين", 595f / 2, 85f, paint)
        canvas.drawText("مزامنة فورية ودعم غير منقطع Offline-First ERP", 595f / 2, 105f, paint)

        // Document Metadata section
        paint.color = Color.BLACK
        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        val sdf = SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale.getDefault())
        val dateStr = sdf.format(Date(docDate))

        canvas.drawText("نوع المستند: $docType", 550f, 165f, paint)
        canvas.drawText("الرقم المرجعي: $docId", 550f, 190f, paint)
        canvas.drawText("تاريخ الإصدار: $dateStr", 550f, 215f, paint)

        // Customer and payment details
        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = 13f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("تفاصيل العميل والمعاملة:", 550f, 255f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 12f
        canvas.drawText("اسم العميل: $customerName", 550f, 280f, paint)
        canvas.drawText("طريقة السداد: $paymentType", 550f, 302f, paint)
        canvas.drawText("الملاحظات: ${notes.ifBlank { "لا يوجد ملاحظات" }}", 550f, 324f, paint)

        // Items table header
        val tableY = 360f
        paint.color = Color.parseColor("#F3EDF7")
        canvas.drawRect(45f, tableY, 550f, tableY + 30f, paint)

        paint.color = Color.parseColor("#1D192B")
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 11f

        // Table column alignment and draw
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("اسم الصنف والمنتج الميداني", 450f, tableY + 20f, paint)
        canvas.drawText("الكمية", 250f, tableY + 20f, paint)
        canvas.drawText("سعر الوحدة", 160f, tableY + 20f, paint)
        canvas.drawText("الإجمالي", 90f, tableY + 20f, paint)

        // Thin table border
        paint.color = Color.parseColor("#49454F")
        paint.strokeWidth = 1.5f
        canvas.drawLine(45f, tableY + 30f, 550f, tableY + 30f, paint)

        // Draw items loop
        var currentY = tableY + 52f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 11f

        for (item in items) {
            paint.color = Color.BLACK
            // Item name
            canvas.drawText(item.name, 450f, currentY, paint)
            // Quantity
            canvas.drawText("${item.quantity}", 250f, currentY, paint)
            // Price
            canvas.drawText("${String.format("%.2f", item.price)} \$", 160f, currentY, paint)
            // Total
            val lineTotal = item.price * item.quantity
            canvas.drawText("${String.format("%.2f", lineTotal)} \$", 90f, currentY, paint)

            // Divider line
            paint.color = Color.parseColor("#E8DEF8")
            paint.strokeWidth = 1f
            canvas.drawLine(45f, currentY + 10f, 550f, currentY + 10f, paint)

            currentY += 34f
        }

        // Totals section
        currentY += 15f
        paint.color = Color.BLACK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 12f
        paint.textAlign = Paint.Align.RIGHT

        if (discount > 0) {
            canvas.drawText("المجموع قبل الخصم: ${String.format("%.2f", totalAmount)} \$", 550f, currentY, paint)
            currentY += 22f
            canvas.drawText("قيمة الخصم الممنوح: ${String.format("%.2f", discount)} \$", 550f, currentY, paint)
            currentY += 22f
        }

        paint.textSize = 14f
        paint.color = Color.parseColor("#6750A4")
        canvas.drawText("إجمالي القيمة النهائية المستحقة: ${String.format("%.2f", finalAmount)} \$", 550f, currentY, paint)

        // Draw Palestine Map/Icon watermark or badge
        paint.color = Color.parseColor("#49454F")
        paint.textSize = 11f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        canvas.drawText("* تم إنشاء هذا المستند عبر تطبيق المندوب الذكي غزة كاش وتعتبر هذه الفاتورة رسمية وموثقة.", 550f, currentY + 45f, paint)

        // Footer Banner
        val footerY = 780f
        paint.color = Color.parseColor("#6750A4")
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, footerY, 595f, 842f, paint)

        paint.color = Color.WHITE
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("صنع بفخر في فلسطين • شكراً لثقتكم بخدماتنا الميدانية", 595f / 2, footerY + 25f, paint)
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("غزة كاش ERP • الاتصال والتحصيل والبيانات الموحدة", 595f / 2, footerY + 45f, paint)
    }

    fun generateThermalTextForOrder(orderWithItems: OrderWithItems): String {
        val order = orderWithItems.order
        val items = orderWithItems.items
        val sdf = SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale.getDefault())
        val dateStr = sdf.format(Date(order.orderDate))

        val sb = StringBuilder()
        sb.append("      غزة كاش - GAZA CASH      \n")
        sb.append("   نظام الفواتير الميداني الذكي   \n")
        sb.append("================================\n")
        sb.append("نوع المستند: طلبية معلقة محلياً\n")
        sb.append("الرقم المرجعي: ${order.uuid.take(8).uppercase()}\n")
        sb.append("التاريخ: $dateStr\n")
        sb.append("العميل: ${order.customerName}\n")
        sb.append("الملاحظات: ${order.notes.ifBlank { "لا يوجد" }}\n")
        sb.append("--------------------------------\n")
        sb.append(String.format("%-14s %3s %6s %6s\n", "الصنف", "كم", "سعر", "إجمالي"))
        sb.append("--------------------------------\n")

        for (item in items) {
            val shortName = if (item.itemName.length > 13) item.itemName.take(11) + ".." else item.itemName
            val lineTotal = item.price * item.quantity
            sb.append(String.format("%-14s %3d %6.1f %6.1f\n", shortName, item.quantity, item.price, lineTotal))
        }

        sb.append("--------------------------------\n")
        sb.append(String.format("القيمة الإجمالية:        %.2f $\n", order.totalAmount))
        sb.append("================================\n")
        sb.append("      صنع بفخر في فلسطين      \n")
        sb.append("   شكراً لتعاملكم الموثوق معنا   \n")
        return sb.toString()
    }

    fun generateThermalTextForInvoice(invoice: Invoice): String {
        val sdf = SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale.getDefault())
        val dateStr = sdf.format(Date(invoice.invoiceDate))

        val items = mutableListOf<PrintableItem>()
        try {
            if (invoice.itemsJson.isNotBlank()) {
                val jsonArray = JSONArray(invoice.itemsJson)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val name = obj.optString("name", "صنف غير معروف")
                    val price = obj.optDouble("price", 0.0)
                    val qty = obj.optInt("quantity", 0)
                    items.add(PrintableItem(name, qty, price))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val sb = StringBuilder()
        sb.append("      غزة كاش - GAZA CASH      \n")
        sb.append("   نظام الفواتير الميداني الذكي   \n")
        sb.append("================================\n")
        sb.append("نوع المستند: فاتورة بيع رسمية\n")
        sb.append("الرقم المرجعي: ${invoice.uuid.take(8).uppercase()}\n")
        sb.append("التاريخ: $dateStr\n")
        sb.append("العميل: ${invoice.customerName}\n")
        sb.append("طريقة الدفع: ${if (invoice.paymentType == "cash") "نقدي" else "ذمم (آجل)"}\n")
        sb.append("--------------------------------\n")
        sb.append(String.format("%-14s %3s %6s %6s\n", "الصنف", "كم", "سعر", "إجمالي"))
        sb.append("--------------------------------\n")

        for (item in items) {
            val shortName = if (item.name.length > 13) item.name.take(11) + ".." else item.name
            val lineTotal = item.price * item.quantity
            sb.append(String.format("%-14s %3d %6.1f %6.1f\n", shortName, item.quantity, item.price, lineTotal))
        }

        sb.append("--------------------------------\n")
        if (invoice.discount > 0) {
            sb.append(String.format("المجموع قبل الخصم:       %.2f $\n", invoice.totalAmount))
            sb.append(String.format("قيمة الخصم الممنوح:      %.2f $\n", invoice.discount))
            sb.append("--------------------------------\n")
        }
        sb.append(String.format("إجمالي القيمة المستحقة:   %.2f $\n", invoice.finalAmount))
        sb.append("================================\n")
        sb.append("      صنع بفخر في فلسطين      \n")
        sb.append("   شكراً لتعاملكم الموثوق معنا   \n")
        return sb.toString()
    }
}

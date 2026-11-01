package com.maliktextile.barcode

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.ZipInputStream
import android.graphics.*
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.app.ActivityCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import java.util.UUID
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val spp = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var socket: BluetoothSocket? = null
    private lateinit var status: TextView
    private lateinit var deviceSpinner: Spinner
    private lateinit var nameInput: EditText
    private lateinit var codeInput: EditText
    private lateinit var mrpInput: EditText
    private lateinit var priceInput: EditText
    private lateinit var qtyInput: EditText
    private lateinit var widthInput: EditText
    private lateinit var heightInput: EditText
    private lateinit var gapInput: EditText
    private lateinit var offsetInput: EditText
    private var paired: List<BluetoothDevice> = emptyList()
    private val bulkItems = mutableListOf<BulkItem>()
    private lateinit var bulkSummary: TextView

    data class BulkItem(val name: String, val code: String, val mrp: String, val price: String, val qty: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        requestBtPermission()
        loadPaired()
    }

    private fun buildUi() {
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 30, 28, 28)
            setBackgroundColor(Color.rgb(243, 246, 251))
        }
        scroll.addView(col)
        setContentView(scroll)

        val header = TextView(this).apply {
            text = "MALIK TEXTILE\nBarcode Printer V2"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(18, 24, 18, 24)
            setBackgroundColor(Color.rgb(16, 45, 89))
        }
        col.addView(header, match())
        status = TextView(this).apply {
            text = "Printer not connected"
            textSize = 14f
            setPadding(4, 18, 4, 12)
            setTextColor(Color.DKGRAY)
        }
        col.addView(status)
        deviceSpinner = Spinner(this)
        col.addView(deviceSpinner, match())
        addButton(col, "REFRESH BLUETOOTH PRINTERS") { loadPaired() }
        addButton(col, "CONNECT SELECTED PRINTER") { connectSelected() }

        nameInput = field(col, "Product Name", "T-SHIRT")
        codeInput = field(col, "Barcode / Product Code", "MT001")
        mrpInput = field(col, "MRP (₹)", "150")
        priceInput = field(col, "Selling Price (₹)", "120")
        qtyInput = field(col, "Number of Labels", "1", true)

        val sizeTitle = TextView(this).apply {
            text = "CUSTOM STICKER SIZE (mm)"
            textSize = 15f
            setTextColor(Color.rgb(16, 45, 89))
            setPadding(2, 12, 2, 8)
        }
        col.addView(sizeTitle, match())
        widthInput = field(col, "Sticker Width (mm) — maximum 58 for 58mm printer", "50", true)
        heightInput = field(col, "Sticker Height (mm)", "27", true)
        gapInput = field(col, "Gap Between Labels (mm)", "2", true)
        offsetInput = field(col, "Horizontal Print Offset (mm) — negative = left, positive = right", "-4", true)
        addButton(col, "PRINT LABELS", true) { printLabels() }

        val bulkTitle = TextView(this).apply {
            text = "BULK PRINT FROM EXCEL"
            textSize = 17f
            setTextColor(Color.rgb(16, 45, 89))
            setPadding(2, 24, 2, 8)
        }
        col.addView(bulkTitle, match())
        addButton(col, "IMPORT EXCEL / CSV FILE") { chooseBulkFile() }
        bulkSummary = TextView(this).apply {
            text = "No bulk items imported"
            textSize = 13f
            setTextColor(Color.DKGRAY)
            setPadding(4, 8, 4, 8)
        }
        col.addView(bulkSummary, match())
        addButton(col, "PRINT ALL IMPORTED ITEMS", true) { printBulkItems() }
        addButton(col, "CLEAR IMPORTED ITEMS") {
            bulkItems.clear()
            updateBulkSummary()
        }

        val note = TextView(this).apply {
            text = "Bluetooth POS-58 / SR588 • CODE128\nSet sticker width, height, gap and horizontal offset in mm. On a 58mm roll, width cannot exceed 58mm. Pair the printer in Android Bluetooth settings first."
            textSize = 12f
            setTextColor(Color.DKGRAY)
            setPadding(4, 20, 4, 8)
        }
        col.addView(note)
    }

    private fun match() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 }

    private fun field(parent: LinearLayout, label: String, hint: String, numeric: Boolean = false): EditText {
        val t = TextView(this).apply {
            text = label
            textSize = 13f
            setTextColor(Color.DKGRAY)
        }
        parent.addView(t, match())
        val e = EditText(this).apply {
            this.hint = hint
            textSize = 16f
            setSingleLine(true)
            if (numeric) inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setPadding(14, 8, 14, 8)
            setBackgroundColor(Color.WHITE)
        }
        parent.addView(e, match())
        return e
    }

    private fun addButton(parent: LinearLayout, text: String, primary: Boolean = false, action: () -> Unit) {
        val b = Button(this).apply {
            this.text = text
            if (primary) setBackgroundColor(Color.rgb(21, 88, 192))
            if (primary) setTextColor(Color.WHITE)
            setOnClickListener { action() }
        }
        parent.addView(b, match())
    }

    private fun requestBtPermission() {
        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 77)
        }
    }

    @Suppress("MissingPermission")
    private fun loadPaired() {
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter == null) { status.text = "This phone has no Bluetooth adapter"; return }
            if (!adapter.isEnabled) { status.text = "Turn on Bluetooth first"; return }
            paired = adapter.bondedDevices?.sortedBy { it.name ?: it.address }?.toList() ?: emptyList()
            val labels = if (paired.isEmpty()) listOf("No paired printer - pair POS-58 in Android Settings")
                         else paired.map { "${it.name ?: "Bluetooth device"}  (${it.address})" }
            deviceSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
            status.text = "Found ${paired.size} paired Bluetooth device(s)"
        } catch (_: SecurityException) {
            status.text = "Allow Nearby Devices permission, then refresh"
        }
    }

    @Suppress("MissingPermission")
    private fun connectSelected() {
        if (paired.isEmpty()) { status.text = "Pair POS-58 in Android Bluetooth settings first"; return }
        val device = paired.getOrNull(deviceSpinner.selectedItemPosition) ?: return
        status.text = "Connecting to ${device.name}..."
        thread {
            try {
                socket?.close()
                val s = device.createRfcommSocketToServiceRecord(spp)
                s.connect()
                socket = s
                runOnUiThread { status.text = "Connected: ${device.name}" }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Connection failed. Pair POS-58 and retry. ${e.message ?: ""}" }
            }
        }
    }

    private fun chooseBulkFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "text/csv", "text/comma-separated-values", "application/vnd.ms-excel"
            ))
        }
        startActivityForResult(intent, 501)
    }

    @Deprecated("Handled for compatibility with the simple Activity project")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 501 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            thread {
                try {
                    val rows = if ((uri.lastPathSegment ?: "").lowercase().endsWith(".csv")) {
                        readCsv(uri)
                    } else {
                        readXlsx(uri)
                    }
                    runOnUiThread {
                        bulkItems.clear()
                        bulkItems.addAll(rows)
                        updateBulkSummary()
                        status.text = "Imported ${rows.size} item(s)"
                    }
                } catch (e: Exception) {
                    runOnUiThread { status.text = "Import failed: ${e.message ?: "Check Excel columns"}" }
                }
            }
        }
    }

    private fun updateBulkSummary() {
        bulkSummary.text = if (bulkItems.isEmpty()) "No bulk items imported" else
            "${bulkItems.size} product row(s) ready • ${bulkItems.sumOf { it.qty }} total label(s)\nFirst: ${bulkItems.first().name} (${bulkItems.first().code})"
    }

    private fun readCsv(uri: Uri): List<BulkItem> {
        val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: throw IllegalArgumentException("Cannot open file")
        val lines = text.replace("\r", "").lines().filter { it.isNotBlank() }
        if (lines.size < 2) throw IllegalArgumentException("CSV needs a header and at least one product row")
        val headers = splitCsvLine(lines.first()).map { it.trim().lowercase() }
        fun index(vararg names: String) = headers.indexOfFirst { h -> names.any { h == it || h.contains(it) } }
        val ni = index("product name", "name", "item")
        val ci = index("barcode", "product code", "code", "sku")
        val mi = index("mrp", "maximum retail")
        val pi = index("selling price", "sale price", "price")
        val qi = index("qty", "quantity", "labels")
        if (ni < 0 || ci < 0 || pi < 0) throw IllegalArgumentException("Required columns: Product Name, Barcode, Selling Price")
        return lines.drop(1).mapNotNull { line ->
            val cells = splitCsvLine(line)
            fun v(i: Int) = if (i >= 0) cells.getOrNull(i)?.trim().orEmpty() else ""
            val name = v(ni); val code = v(ci); val price = v(pi)
            if (name.isBlank() || code.isBlank() || price.isBlank()) null else
                BulkItem(name, code, v(mi), price, (v(qi).toIntOrNull() ?: 1).coerceIn(1, 100))
        }
    }

    private fun splitCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (ch == '"') {
                if (quoted && i + 1 < line.length && line[i + 1] == '"') { cell.append('"'); i++ }
                else quoted = !quoted
            } else if (ch == ',' && !quoted) { out.add(cell.toString()); cell.setLength(0) }
            else cell.append(ch)
            i++
        }
        out.add(cell.toString())
        return out
    }

    private fun readXlsx(uri: Uri): List<BulkItem> {
        val entries = mutableMapOf<String, ByteArray>()
        contentResolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(raw).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "xl/sharedStrings.xml" || entry.name == "xl/worksheets/sheet1.xml") {
                        entries[entry.name] = zip.readBytes()
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: throw IllegalArgumentException("Cannot open Excel file")
        val shared = entries["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()
        val sheet = entries["xl/worksheets/sheet1.xml"] ?: throw IllegalArgumentException("Excel first sheet not found")
        val rows = parseSheetRows(sheet, shared)
        if (rows.size < 2) throw IllegalArgumentException("Excel needs a header and product rows")
        val headers = rows.first().map { it.trim().lowercase() }
        fun index(vararg names: String) = headers.indexOfFirst { h -> names.any { h == it || h.contains(it) } }
        val ni = index("product name", "name", "item")
        val ci = index("barcode", "product code", "code", "sku")
        val mi = index("mrp", "maximum retail")
        val pi = index("selling price", "sale price", "price")
        val qi = index("qty", "quantity", "labels")
        if (ni < 0 || ci < 0 || pi < 0) throw IllegalArgumentException("Required columns: Product Name, Barcode, Selling Price")
        return rows.drop(1).mapNotNull { cells ->
            fun v(i: Int) = if (i >= 0) cells.getOrNull(i)?.trim().orEmpty() else ""
            val name = v(ni); val code = v(ci); val price = v(pi)
            if (name.isBlank() || code.isBlank() || price.isBlank()) null else
                BulkItem(name, code, v(mi), price, (v(qi).toIntOrNull() ?: 1).coerceIn(1, 100))
        }
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val result = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setInput(bytes.inputStream(), "UTF-8")
        var inSi = false
        var current = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> if (parser.name == "si") { inSi = true; current = StringBuilder() }
                XmlPullParser.TEXT -> if (inSi) current.append(parser.text)
                XmlPullParser.END_TAG -> if (parser.name == "si") { result.add(current.toString()); inSi = false }
            }
            event = parser.next()
        }
        return result
    }

    private fun parseSheetRows(bytes: ByteArray, shared: List<String>): List<List<String>> {
        val result = mutableListOf<List<String>>()
        val parser = Xml.newPullParser()
        parser.setInput(bytes.inputStream(), "UTF-8")
        var row: MutableMap<Int, String>? = null
        var col = 0
        var cellType = ""
        var value = StringBuilder()
        var inValue = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> row = mutableMapOf()
                    "c" -> {
                        val ref = parser.getAttributeValue(null, "r") ?: "A1"
                        col = columnIndex(ref)
                        cellType = parser.getAttributeValue(null, "t") ?: ""
                        value = StringBuilder()
                    }
                    "v", "t" -> inValue = true
                }
                XmlPullParser.TEXT -> if (inValue) value.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v", "t" -> inValue = false
                    "c" -> {
                        val raw = value.toString()
                        val parsed = if (cellType == "s") shared.getOrNull(raw.toIntOrNull() ?: -1).orEmpty() else raw
                        row?.set(col, parsed)
                    }
                    "row" -> {
                        val r = row ?: mutableMapOf()
                        val max = (r.keys.maxOrNull() ?: -1)
                        result.add((0..max).map { r[it].orEmpty() })
                        row = null
                    }
                }
            }
            event = parser.next()
        }
        return result
    }

    private fun columnIndex(ref: String): Int {
        val letters = ref.takeWhile { it.isLetter() }.uppercase()
        var value = 0
        letters.forEach { value = value * 26 + (it - 'A' + 1) }
        return (value - 1).coerceAtLeast(0)
    }

    private fun printBulkItems() {
        val s = socket
        if (s == null || !s.isConnected) { status.text = "Connect POS-58 first"; return }
        if (bulkItems.isEmpty()) { status.text = "Import an Excel or CSV file first"; return }
        val widthMm = widthInput.text.toString().toFloatOrNull() ?: 50f
        val heightMm = heightInput.text.toString().toFloatOrNull() ?: 27f
        val gapMm = gapInput.text.toString().toFloatOrNull() ?: 2f
        val offsetMm = offsetInput.text.toString().toFloatOrNull() ?: -4f
        if (widthMm !in 20f..58f || heightMm !in 15f..200f || gapMm !in 0f..30f || offsetMm !in -10f..10f) {
            status.text = "Check size, gap (0–30mm) and offset (-10 to +10mm)"; return
        }
        val total = bulkItems.sumOf { it.qty }
        status.text = "Bulk printing $total label(s)..."
        thread {
            try {
                val out = s.outputStream
                bulkItems.forEach { item ->
                    repeat(item.qty) {
                        val bitmap = makeLabel(item.name, item.code, item.mrp, item.price, widthMm, heightMm, offsetMm)
                        out.write(byteArrayOf(0x1B, 0x40))
                        out.write(raster(bitmap))
                        feedGap(out, gapMm)
                        out.flush()
                    }
                }
                runOnUiThread { status.text = "Bulk print sent: ${bulkItems.size} product row(s), $total label(s)" }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Bulk print failed: ${e.message ?: "Bluetooth error"}" }
            }
        }
    }

    private fun printLabels() {
        val s = socket
        if (s == null || !s.isConnected) { status.text = "Connect POS-58 first"; return }
        val name = nameInput.text.toString().trim()
        val code = codeInput.text.toString().trim().ifBlank { "MT${System.currentTimeMillis()}" }
        val mrp = mrpInput.text.toString().trim()
        val price = priceInput.text.toString().trim()
        val qty = qtyInput.text.toString().toIntOrNull()?.coerceIn(1, 100) ?: 1
        val widthMm = widthInput.text.toString().toFloatOrNull()
        val heightMm = heightInput.text.toString().toFloatOrNull()
        val gapMm = gapInput.text.toString().toFloatOrNull() ?: 2f
        val offsetMm = offsetInput.text.toString().toFloatOrNull() ?: -4f

        if (name.isBlank() || price.isBlank()) { status.text = "Enter Product Name and Selling Price"; return }
        if (widthMm == null || heightMm == null || widthMm < 20f || widthMm > 58f || heightMm < 15f || heightMm > 200f) {
            status.text = "Sticker size: width 20–58 mm, height 15–200 mm for this 58mm printer"
            return
        }

        if (gapMm !in 0f..30f || offsetMm !in -10f..10f) { status.text = "Gap 0–30mm; offset -10 to +10mm"; return }

        status.text = "Printing $qty label(s) • ${widthMm} × ${heightMm} mm • gap ${gapMm}mm"
        thread {
            try {
                val out = s.outputStream
                repeat(qty) {
                    val bitmap = makeLabel(name, code, mrp, price, widthMm, heightMm, offsetMm)
                    out.write(byteArrayOf(0x1B, 0x40))
                    out.write(raster(bitmap))
                    feedGap(out, gapMm)
                    out.flush()
                }
                runOnUiThread { status.text = "Print sent: $qty label(s) • gap ${gapMm}mm • offset ${offsetMm}mm" }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Print failed: ${e.message ?: "Bluetooth error"}" }
            }
        }
    }

    private fun feedGap(out: java.io.OutputStream, gapMm: Float) {
        var dots = (gapMm * 8f).toInt().coerceIn(0, 240)
        while (dots > 0) {
            val part = minOf(dots, 255)
            out.write(byteArrayOf(0x1B, 0x4A, part.toByte())) // ESC J: feed paper by dots
            dots -= part
        }
    }

    private fun fitText(paint: Paint, text: String, maxWidth: Float, desired: Float, minSize: Float = 9f) {
        paint.textSize = desired
        while (paint.textSize > minSize && paint.measureText(text) > maxWidth) paint.textSize -= 1f
    }

    private fun makeLabel(name: String, code: String, mrp: String, price: String, widthMm: Float, heightMm: Float, offsetMm: Float): Bitmap {
        // Keep the print image at the full 58mm roll width; center the requested label within it.
        // 8 printer dots per mm at approximately 203 dpi.
        val paperW = 464
        val labelW = (widthMm * 8f).toInt().coerceIn(160, paperW)
        val labelH = (heightMm * 8f).toInt().coerceIn(120, 1600)
        val centeredLeft = (paperW - labelW) / 2f
        val requestedLeft = centeredLeft + offsetMm * 8f
        val left = requestedLeft.coerceIn(0f, (paperW - labelW).toFloat())
        val right = left + labelW
        val top = 2f
        val bottom = labelH - 2f
        val w = paperW
        val h = labelH
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val center = (left + right) / 2f
        val pad = labelW * 0.055f
        val innerW = labelW - pad * 2f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }

        p.style = Paint.Style.STROKE
        p.strokeWidth = (labelW * 0.004f).coerceAtLeast(1.5f)
        c.drawRect(left + 2f, top, right - 2f, bottom, p)
        p.style = Paint.Style.FILL

        val brand = "MALIK TEXTILE"
        fitText(p, brand, innerW, (h * 0.085f).coerceAtMost(labelW * 0.075f))
        c.drawText(brand, center, h * 0.145f, p)

        fitText(p, name, innerW, (h * 0.078f).coerceAtMost(labelW * 0.07f))
        c.drawText(name, center, h * 0.275f, p)

        val moneySize = (h * 0.125f).coerceAtMost(labelW * 0.12f)
        p.textAlign = Paint.Align.LEFT
        val mrpText = if (mrp.isNotBlank()) "₹$mrp" else ""
        if (mrpText.isNotBlank()) {
            fitText(p, mrpText, innerW * 0.43f, moneySize)
            val mrpX = left + pad
            val mrpY = h * 0.425f
            c.drawText(mrpText, mrpX, mrpY, p)
            val textW = p.measureText(mrpText)
            val strike = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                strokeWidth = (h * 0.009f).coerceAtLeast(1.5f)
            }
            c.drawLine(mrpX, mrpY - p.textSize * 0.35f, mrpX + textW, mrpY - p.textSize * 0.35f, strike)
        }

        val priceText = "₹$price"
        p.textAlign = Paint.Align.RIGHT
        fitText(p, priceText, innerW * 0.48f, moneySize)
        c.drawText(priceText, right - pad, h * 0.425f, p)

        val barcodeTop = h * 0.51f
        val barcodeBottom = h * 0.82f
        val barcodeW = (innerW * 0.88f).toInt().coerceAtLeast(100)
        val barcodeH = (barcodeBottom - barcodeTop).toInt().coerceAtLeast(24)
        try {
            val matrix = MultiFormatWriter().encode(code, BarcodeFormat.CODE_128, barcodeW, barcodeH)
            val bc = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
            for (x in 0 until matrix.width) for (y in 0 until matrix.height) {
                bc.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
            val dstLeft = center - barcodeW / 2f
            c.drawBitmap(bc, null, RectF(dstLeft, barcodeTop, dstLeft + barcodeW, barcodeBottom), null)
        } catch (_: Exception) {
            // Leave barcode area blank if the supplied product code is not valid for CODE128.
        }

        p.textAlign = Paint.Align.CENTER
        fitText(p, code, innerW, (h * 0.07f).coerceAtMost(labelW * 0.065f))
        c.drawText(code, center, h * 0.94f, p)
        return bmp
    }

    private fun raster(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val bytesPerRow = (width + 7) / 8
        val data = ByteArray(bytesPerRow * height)
        for (y in 0 until height) for (x in 0 until width) {
            val pixel = bitmap.getPixel(x, y)
            val lum = (Color.red(pixel) * 299 + Color.green(pixel) * 587 + Color.blue(pixel) * 114) / 1000
            if (lum < 160) {
                val idx = y * bytesPerRow + x / 8
                data[idx] = (data[idx].toInt() or (0x80 shr (x % 8))).toByte()
            }
        }
        val header = byteArrayOf(
            0x1D, 0x76, 0x30, 0x00,
            (bytesPerRow and 0xFF).toByte(), ((bytesPerRow shr 8) and 0xFF).toByte(),
            (height and 0xFF).toByte(), ((height shr 8) and 0xFF).toByte()
        )
        return header + data
    }

    override fun onDestroy() {
        try { socket?.close() } catch (_: Exception) { }
        super.onDestroy()
    }
}

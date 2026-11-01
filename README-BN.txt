MALIK TEXTILE BARCODE PRINTER V3

Features:
- Bluetooth SPP printer connection (POS-58 / SR588 compatible models)
- Custom sticker width and height in mm
- User-controlled gap between labels (0–30 mm)
- Horizontal print offset in mm (left/right alignment adjustment)
- Single-item barcode label printing
- Import .xlsx Excel workbook (first worksheet) or .csv file
- Bulk print all imported product rows
- Per-row label quantity

Excel/CSV columns (first row must be headers):
Product Name | Barcode | MRP | Selling Price | Qty

Required columns: Product Name, Barcode, Selling Price
Optional columns: MRP, Qty (Qty defaults to 1)
Recognized header alternatives include Name/Item, Product Code/Code/SKU, Sale Price/Price, Quantity/Labels.

Example CSV:
Product Name,Barcode,MRP,Selling Price,Qty
T-Shirt,T110,140,120,3
Kurti,K205,350,299,2

For .xlsx files, data must be on the first worksheet. Use normal cell values (not merged headers). For older .xls files, open in Excel and Save As .xlsx or .csv.

Bulk import reads the file locally on the phone. The app does not upload the spreadsheet to a server.

Build:
Run: gradle assembleDebug
APK: app/build/outputs/apk/debug/app-debug.apk

Important:
- Pair the printer in Android Bluetooth settings before connecting in the app.
- Print width must be 20-58 mm for a 58 mm printer. Height is 15-200 mm.
- Gap is sent as ESC/POS dot-feed (about 8 dots per mm). Actual feed can vary by printer firmware.
- Horizontal offset is clamped to the printable 58 mm paper width to reduce clipping. Start with -4 mm for a 50 mm sticker, or 0 mm for a 58 mm sticker.
- Actual print alignment and label feed depend on the printer model and media calibration. Test one label before bulk printing.
- This project has not been physically tested on every POS-58/SR588 firmware variant.

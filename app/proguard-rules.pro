# PDFCrate release shrinking rules.
#
# The PDF layer selects implementations at runtime from SdkExtensions and the platform
# version. Keep those classes intact so R8 never inlines or merges extension-only
# framework references into code that older Android versions load.
-keep class dev.pdfcrate.app.pdf.** { *; }

# Stack traces in user reports stay readable after obfuscation.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

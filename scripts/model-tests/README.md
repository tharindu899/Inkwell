Pure-Kotlin checks for the editor model (no Android / Compose needed). Needs `kotlinc`.

    E=app/src/main/java/com/inkwell/notes/editor
    kotlinc $E/RichDoc.kt $E/TableData.kt $E/ImageData.kt $E/HtmlCodec.kt $E/MarkdownCodec.kt scripts/model-tests/RichDocTests.kt -include-runtime -d /tmp/t1.jar && java -jar /tmp/t1.jar
    kotlinc $E/RichDoc.kt $E/TableData.kt $E/ImageData.kt $E/HtmlCodec.kt $E/MarkdownCodec.kt $E/RichLayout.kt scripts/model-tests/RichLayoutTests.kt -include-runtime -d /tmp/t2.jar && java -jar /tmp/t2.jar
    kotlinc $E/RichDoc.kt $E/TableData.kt $E/ImageData.kt $E/HtmlCodec.kt $E/MarkdownCodec.kt $E/RichLayout.kt scripts/model-tests/Part5Tests.kt -include-runtime -d /tmp/t3.jar && java -jar /tmp/t3.jar
    kotlinc $E/RichDoc.kt $E/TableData.kt $E/ImageData.kt $E/HtmlCodec.kt $E/MarkdownCodec.kt $E/RichLayout.kt scripts/model-tests/Part6Tests.kt -include-runtime -d /tmp/t4.jar && java -jar /tmp/t4.jar
    kotlinc $E/RichDoc.kt $E/TableData.kt $E/ImageData.kt $E/HtmlCodec.kt $E/MarkdownCodec.kt $E/RichLayout.kt $E/NoteExport.kt scripts/model-tests/Part7Tests.kt -include-runtime -d /tmp/t5.jar && java -jar /tmp/t5.jar
    kotlinc $E/TableData.kt $E/EditorStats.kt scripts/model-tests/Part8Tests.kt -include-runtime -d /tmp/t6.jar && java -jar /tmp/t6.jar
    kotlinc $E/SourceEdit.kt scripts/model-tests/Part9Tests.kt -include-runtime -d /tmp/t9.jar && java -jar /tmp/t9.jar
    kotlinc $E/RichDoc.kt $E/TableData.kt $E/ImageData.kt $E/HtmlCodec.kt $E/MarkdownCodec.kt $E/RichLayout.kt scripts/model-tests/Part9SliceTests.kt -include-runtime -d /tmp/t10.jar && java -jar /tmp/t10.jar

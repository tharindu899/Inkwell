Pure-Kotlin checks for the editor model (no Android / Compose needed). Needs `kotlinc`.

    E=app/src/main/java/com/inkwell/notes/editor
    kotlinc $E/RichDoc.kt $E/TableData.kt $E/HtmlCodec.kt $E/MarkdownCodec.kt scripts/model-tests/RichDocTests.kt -include-runtime -d /tmp/t1.jar && java -jar /tmp/t1.jar
    kotlinc $E/RichDoc.kt $E/TableData.kt $E/HtmlCodec.kt $E/MarkdownCodec.kt $E/RichLayout.kt scripts/model-tests/RichLayoutTests.kt -include-runtime -d /tmp/t2.jar && java -jar /tmp/t2.jar
    kotlinc $E/RichDoc.kt $E/TableData.kt $E/HtmlCodec.kt $E/MarkdownCodec.kt $E/RichLayout.kt scripts/model-tests/Part5Tests.kt -include-runtime -d /tmp/t3.jar && java -jar /tmp/t3.jar

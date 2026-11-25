# Editor logic checks (JVM, no Android SDK)

`Doc.kt`, `HtmlCodec.kt` and `EditorHistory.kt` are pure Kotlin. Run:

```
E=app/src/main/java/com/inkwell/notes/editor
kotlinc $E/Doc.kt $E/HtmlCodec.kt $E/EditorHistory.kt tools/editor-tests/UnitChecks.kt -include-runtime -d /tmp/u.jar && java -jar /tmp/u.jar
kotlinc $E/Doc.kt $E/HtmlCodec.kt $E/EditorHistory.kt tools/editor-tests/Fuzz.kt  -include-runtime -d /tmp/f.jar && java -jar /tmp/f.jar
```
(Both files define `main`, so compile them separately.)

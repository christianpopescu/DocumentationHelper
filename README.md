# DocumentHelper

## Java desktop application

Requires JDK 21, Maven, and Graphviz (`dot.exe` on `PATH`).

The Maven application is in `src/Java/DocumentHelperApp`. From the repository root:

```console
cd src/Java/DocumentHelperApp
mvn package
java -jar target/DocumentHelper.jar
```

Keep `target/lib/` beside the JAR. The Swing screen selects an AsciiDoc document,
an output folder, and HTML/PDF formats (both selected initially).
See [build and usage notes](docs/Notes/DocumentHelperUsage.adoc).

## Earlier experiments

Help to create documentation

Restart unsing of the projectas helper to generate documentation from ascidoc and md documents

Initial use was to generate adoc documents.

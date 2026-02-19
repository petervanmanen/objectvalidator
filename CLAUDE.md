# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

ObjectValidator (aka InwonerplanValidator) is a Java Swing desktop application for validating, editing, and sanitizing JSON documents against a JSON Schema. It is built for the municipality of Utrecht to work with "Inwonerplan" (resident plan) data — a domain model of goals (doelen), sub-goals (subdoelen), offers (aanbod), and activities (activiteiten).

## Build & Run Commands

```bash
# Run the application (Swing GUI)
./gradlew run

# Build fat JAR (all dependencies bundled)
./gradlew jar

# Run tests
./gradlew test

# Generate POJOs from JSON Schema (runs automatically as part of build)
./gradlew generateJsonSchema2Pojo

# Publish to GitHub Packages (requires USERNAME and TOKEN env vars)
./gradlew publish
```

## Architecture

**Three source files** in `src/main/java/com/ritense/`:
- `InwonerplanValidator.java` — Main application: Swing GUI (FlatLaf L&F, RSyntaxTextArea editor), menu bar, file open/save, JSON formatting, tree visualization
- `InwonerplanSanitizer.java` — Sanitization logic: deduplication of doelen, subdoelen, aanbod, and activiteiten
- `InwonerplanDomainValidator.java` — Domain-specific validation rules (beyond JSON Schema validation)

**Generated code** in `build/generated-sources/js2p/com/ritense/`:
- POJOs generated from `src/main/resources/schemas/inwonerplan.schema.json` by the `jsonschema2pojo` Gradle plugin
- Classes: `InwonerplanSchema`, `Inwonerplan`, `Doel`, `Subdoel`, `Aanbod`, `Activiteit`
- Uses Jackson2 annotations, `LocalDate` for dates, `ZonedDateTime` for date-times

**Resources** in `src/main/resources/`:
- `schemas/inwonerplan.schema.json` — JSON Schema (Draft-07) defining the Inwonerplan structure
- `inwonerplan.json` — Sample data loaded as default in the editor

## UI Stack

- **FlatLaf** (`com.formdev:flatlaf:3.5.4`) — Modern flat Look & Feel with light (FlatIntelliJLaf) and dark (FlatDarculaLaf) themes
- **RSyntaxTextArea** (`com.fifesoft:rsyntaxtextarea:3.5.3`) — JSON syntax highlighting, code folding, built-in line numbers, bracket matching
- Menu bar with keyboard shortcuts: File (New/Open/Save/Save As), Edit (Undo/Redo/Find/Format), Tools (Validate F5/Sanitize F6/Visualize F7), View (Dark Theme/Word Wrap), Help
- Status bar showing filename, cursor position (Ln/Col), and last operation result

## Key Technical Details

- **Java 17+** (CI uses Temurin-17, Gradle toolchain supports 17 and 21)
- **Gradle 8.5** with Kotlin DSL (`build.gradle.kts`)
- **JSON validation** uses `com.github.erosb:json-sKema` (not everit or networknt)
- **JSON manipulation** uses Jackson for serialization and tree building
- **Domain language** is Dutch — field names, comments, and validation messages are in Dutch
- **No tests exist** currently; the test framework (JUnit 5) is configured but `src/test/` has no files
- **No linter/formatter** is configured

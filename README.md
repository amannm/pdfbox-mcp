# PDFBox MCP Server

A Model Context Protocol (MCP) server that provides PDF processing capabilities using Apache PDFBox.

## Features

- **extract_text** - Extract text content from PDF files with optional page range support
- **get_metadata** - Extract PDF metadata (title, author, creation date, etc.)
- **get_page_count** - Get the total number of pages in a PDF file

## Dependencies

- Java 17+
- Apache PDFBox 3.0.5
- MCP SDK 0.10.0
- Maven for build management

## Usage

Build the project:
```bash
mvn compile
```

Run the server:
```bash
mvn exec:java
```

## Tools

### extract_text
Extract text content from a PDF file.

Parameters:
- `file_path` (required): Path to the PDF file
- `page_range` (optional): Page range (e.g., '1-5' or 'all')

### get_metadata
Extract metadata from a PDF file including title, author, creation date, etc.

Parameters:
- `file_path` (required): Path to the PDF file

### get_page_count
Get the number of pages in a PDF file.

Parameters:
- `file_path` (required): Path to the PDF file

## Status

Currently in development. The core functionality is implemented but compilation requires MCP SDK API adjustments.

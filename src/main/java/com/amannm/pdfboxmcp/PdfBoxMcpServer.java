package com.amannm.pdfboxmcp;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;

import com.fasterxml.jackson.databind.ObjectMapper;


import reactor.core.publisher.Mono;

import java.io.File;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

public class PdfBoxMcpServer {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    public static void main(String[] args) {
        StdioServerTransportProvider transport = new StdioServerTransportProvider();

        ServerCapabilities capabilities = new ServerCapabilities(
            null, null, null, null, null,
            new ServerCapabilities.ToolCapabilities(true)
        );

        McpServer.sync(transport)
            .objectMapper(objectMapper)
            .serverInfo("pdfbox-mcp", "1.0.0")
            .capabilities(capabilities)
            .tools(
                new McpServerFeatures.SyncToolSpecification(
                    createExtractTextTool(),
                    (exchange, params) -> handleExtractText(params).block()
                ),
                new McpServerFeatures.SyncToolSpecification(
                    createGetMetadataTool(),
                    (exchange, params) -> handleGetMetadata(params).block()
                ),
                new McpServerFeatures.SyncToolSpecification(
                    createGetPageCountTool(),
                    (exchange, params) -> handleGetPageCount(params).block()
                )
            )
            .build();
    }
    
    
    private static Tool createExtractTextTool() {
        Map<String, Object> properties = Map.of(
            "file_path", new SchemaProperty("string", "Path to the PDF file"),
            "page_range", new SchemaProperty("string", "Page range (e.g., '1-5' or 'all')")
        );
        List<String> required = List.of("file_path");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", properties, required, null, null, null
        );
        return new Tool("extract_text", "Extract text content from a PDF file", schema);
    }

    private static Tool createFileTool(String name, String description) {
        Map<String, Object> properties = Map.of(
            "file_path", new SchemaProperty("string", "Path to the PDF file")
        );
        List<String> required = List.of("file_path");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", properties, required, null, null, null
        );
        return new Tool(name, description, schema);
    }

    private static Tool createGetMetadataTool() {
        return createFileTool("get_metadata", "Extract metadata from a PDF file");
    }

    private static Tool createGetPageCountTool() {
        return createFileTool("get_page_count", "Get the number of pages in a PDF file");
    }
    
    
    static Mono<CallToolResult> handleExtractText(Map<String, Object> arguments) {
        try {
            String filePath = String.valueOf(arguments.get("file_path"));
            String pageRange = arguments.containsKey("page_range")
                ? String.valueOf(arguments.get("page_range"))
                : "all";
            
            File pdfFile = new File(filePath);
            if (!pdfFile.exists()) {
                return Mono.just(createErrorResult("File not found: " + filePath));
            }
            
            try (PDDocument document = Loader.loadPDF(pdfFile)) {
                PDFTextStripper pdfStripper = new PDFTextStripper();

                if (!"all".equals(pageRange)) {
                    java.util.Optional<PageRange> range = parsePageRange(pageRange);
                    if (range.isEmpty()) {
                        return Mono.just(createErrorResult("Invalid page range: " + pageRange));
                    }
                    pdfStripper.setStartPage(range.get().start());
                    pdfStripper.setEndPage(range.get().end());
                }
                
                String text = pdfStripper.getText(document);
                return Mono.just(createTextResult(text));
            }
        } catch (Exception e) {
            return Mono.just(createErrorResult("Error extracting text: " + e.getMessage()));
        }
    }
    
    private static Mono<CallToolResult> handleGetMetadata(Map<String, Object> arguments) {
        try {
            String filePath = String.valueOf(arguments.get("file_path"));
            
            File pdfFile = new File(filePath);
            if (!pdfFile.exists()) {
                return Mono.just(createErrorResult("File not found: " + filePath));
            }
            
            try (PDDocument document = Loader.loadPDF(pdfFile)) {
                PDDocumentInformation info = document.getDocumentInformation();

                Calendar creationDate = info.getCreationDate();
                Calendar modificationDate = info.getModificationDate();

                PdfMetadata metadata = new PdfMetadata(
                    info.getTitle(),
                    info.getAuthor(),
                    info.getSubject(),
                    info.getKeywords(),
                    info.getCreator(),
                    info.getProducer(),
                    creationDate != null ? creationDate.getTime().toString() : null,
                    modificationDate != null ? modificationDate.getTime().toString() : null,
                    document.getNumberOfPages()
                );

                String json = objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(metadata);
                return Mono.just(createTextResult(json));
            }
        } catch (Exception e) {
            return Mono.just(createErrorResult("Error extracting metadata: " + e.getMessage()));
        }
    }
    
    private static Mono<CallToolResult> handleGetPageCount(Map<String, Object> arguments) {
        try {
            String filePath = String.valueOf(arguments.get("file_path"));
            
            File pdfFile = new File(filePath);
            if (!pdfFile.exists()) {
                return Mono.just(createErrorResult("File not found: " + filePath));
            }
            
            try (PDDocument document = Loader.loadPDF(pdfFile)) {
                int pageCount = document.getNumberOfPages();
                return Mono.just(createTextResult("Page count: " + pageCount));
            }
        } catch (Exception e) {
            return Mono.just(createErrorResult("Error getting page count: " + e.getMessage()));
        }
    }

    /**
     * Parse a page range string like "3" or "2-5".
     *
     * @param pageRange the range expression
     * @return an Optional containing the parsed {@link PageRange} or empty if invalid
     */
    static java.util.Optional<PageRange> parsePageRange(String pageRange) {
        if (pageRange == null || pageRange.isBlank()) {
            return java.util.Optional.empty();
        }
        String[] parts = pageRange.split("-", -1);
        try {
            if (parts.length == 1) {
                int page = Integer.parseInt(parts[0]);
                if (page < 1) {
                    return java.util.Optional.empty();
                }
                return java.util.Optional.of(new PageRange(page, page));
            }
            if (parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank()) {
                int start = Integer.parseInt(parts[0]);
                int end = Integer.parseInt(parts[1]);
                if (start < 1 || end < start) {
                    return java.util.Optional.empty();
                }
                return java.util.Optional.of(new PageRange(start, end));
            }
        } catch (NumberFormatException e) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.empty();
    }
    
    static CallToolResult createTextResult(String text) {
        TextContent content = new TextContent(text);
        return new CallToolResult(List.of(content), false);
    }
    
    static CallToolResult createErrorResult(String error) {
        TextContent content = new TextContent(error);
        return new CallToolResult(List.of(content), true);
    }
}

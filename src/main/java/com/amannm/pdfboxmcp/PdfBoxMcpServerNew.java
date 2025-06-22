package com.amannm.pdfboxmcp;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.*;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.*;

public class PdfBoxMcpServerNew {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) {
        StdioServerTransportProvider transport = new StdioServerTransportProvider();
        
        McpServer.sync(transport)
            .serverInfo("pdfbox-mcp", "1.0.0")
            .instructions("Ready")
            .tools(
                new McpServerFeatures.SyncToolSpecification(createExtractTextTool(), PdfBoxMcpServerNew::handleExtractText),
                new McpServerFeatures.SyncToolSpecification(createGetMetadataTool(), PdfBoxMcpServerNew::handleGetMetadata),
                new McpServerFeatures.SyncToolSpecification(createGetPageCountTool(), PdfBoxMcpServerNew::handleGetPageCount)
            )
            .build();
    }

    private static Tool createExtractTextTool() {
        Map<String, Object> properties = Map.of(
            "file_path", Map.of("type", "string", "description", "Path to the PDF file"),
            "start_page", Map.of("type", "integer", "description", "Starting page number (1-based, optional)"),
            "end_page", Map.of("type", "integer", "description", "Ending page number (1-based, optional)")
        );
        List<String> required = List.of("file_path");
        JsonSchema schema = new JsonSchema("object", properties, required, false, null, null);
        return new Tool("extract_text", "Extract text content from a PDF file", schema);
    }

    private static Tool createGetMetadataTool() {
        Map<String, Object> properties = Map.of(
            "file_path", Map.of("type", "string", "description", "Path to the PDF file")
        );
        List<String> required = List.of("file_path");
        JsonSchema schema = new JsonSchema("object", properties, required, false, null, null);
        return new Tool("get_metadata", "Get metadata information from a PDF file", schema);
    }

    private static Tool createGetPageCountTool() {
        Map<String, Object> properties = Map.of(
            "file_path", Map.of("type", "string", "description", "Path to the PDF file")
        );
        List<String> required = List.of("file_path");
        JsonSchema schema = new JsonSchema("object", properties, required, false, null, null);
        return new Tool("get_page_count", "Get the number of pages in a PDF file", schema);
    }

    private static CallToolResult handleExtractText(McpSyncServerExchange exchange, Map<String, Object> arguments) {
        try {
            String filePath = String.valueOf(arguments.get("file_path"));
            
            File pdfFile = new File(filePath);
            if (!pdfFile.exists()) {
                return createErrorResult("File not found: " + filePath);
            }

            try (PDDocument document = Loader.loadPDF(pdfFile)) {
                PDFTextStripper pdfStripper = new PDFTextStripper();
                
                // Handle page range if specified
                if (arguments.get("start_page") != null) {
                    int startPage = ((Number) arguments.get("start_page")).intValue();
                    pdfStripper.setStartPage(Math.max(1, startPage));
                }
                
                if (arguments.get("end_page") != null) {
                    int endPage = ((Number) arguments.get("end_page")).intValue();
                    pdfStripper.setEndPage(Math.min(document.getNumberOfPages(), endPage));
                }

                String text = pdfStripper.getText(document);
                return createTextResult(text);
            }
        } catch (Exception e) {
            return createErrorResult("Error extracting text: " + e.getMessage());
        }
    }

    private static CallToolResult handleGetMetadata(McpSyncServerExchange exchange, Map<String, Object> arguments) {
        try {
            String filePath = String.valueOf(arguments.get("file_path"));
            
            File pdfFile = new File(filePath);
            if (!pdfFile.exists()) {
                return createErrorResult("File not found: " + filePath);
            }

            try (PDDocument document = Loader.loadPDF(pdfFile)) {
                PDDocumentInformation info = document.getDocumentInformation();
                
                PdfMetadata metadata = new PdfMetadata(
                    info.getTitle(),
                    info.getAuthor(),
                    info.getSubject(),
                    info.getKeywords(),
                    info.getCreator(),
                    info.getProducer(),
                    info.getCreationDate() != null ? info.getCreationDate().getTime().toString() : null,
                    info.getModificationDate() != null ? info.getModificationDate().getTime().toString() : null,
                    document.getNumberOfPages()
                );

                String json = objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(metadata);
                return createTextResult(json);
            }
        } catch (Exception e) {
            return createErrorResult("Error extracting metadata: " + e.getMessage());
        }
    }

    private static CallToolResult handleGetPageCount(McpSyncServerExchange exchange, Map<String, Object> arguments) {
        try {
            String filePath = String.valueOf(arguments.get("file_path"));
            
            File pdfFile = new File(filePath);
            if (!pdfFile.exists()) {
                return createErrorResult("File not found: " + filePath);
            }
            
            try (PDDocument document = Loader.loadPDF(pdfFile)) {
                int pageCount = document.getNumberOfPages();
                return createTextResult("Page count: " + pageCount);
            }
        } catch (Exception e) {
            return createErrorResult("Error getting page count: " + e.getMessage());
        }
    }

    static CallToolResult createTextResult(String text) {
        TextContent content = new TextContent(text);
        return new CallToolResult(List.of(content), false);
    }

    static CallToolResult createErrorResult(String error) {
        TextContent content = new TextContent(error);
        return new CallToolResult(List.of(content), true);
    }

    static class PdfMetadata {
        public final String title;
        public final String author;
        public final String subject;
        public final String keywords;
        public final String creator;
        public final String producer;
        public final String creationDate;
        public final String modificationDate;
        public final int pageCount;

        public PdfMetadata(String title, String author, String subject, String keywords,
                          String creator, String producer, String creationDate, 
                          String modificationDate, int pageCount) {
            this.title = title;
            this.author = author;
            this.subject = subject;
            this.keywords = keywords;
            this.creator = creator;
            this.producer = producer;
            this.creationDate = creationDate;
            this.modificationDate = modificationDate;
            this.pageCount = pageCount;
        }
    }
}
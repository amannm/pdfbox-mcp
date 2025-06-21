package com.amannm.pdfboxmcp;

import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.*;
import io.modelcontextprotocol.spec.McpSchema.*;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import reactor.core.publisher.Mono;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PdfBoxMcpServer {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    public static void main(String[] args) {
        StdioServerTransportProvider transport = new StdioServerTransportProvider();
        
        transport.setSessionFactory(new McpServerSession.Factory() {
            @Override
            public McpServerSession create(McpServerTransport serverTransport) {
                return new McpServerSession(
                    "pdfbox-mcp-session",
                    Duration.ofMinutes(5),
                    serverTransport,
                    createInitRequestHandler(),
                    createInitNotificationHandler(),
                    createRequestHandlers(),
                    Map.of()
                );
            }
        });
    }
    
    private static McpServerSession.InitRequestHandler createInitRequestHandler() {
        return new McpServerSession.InitRequestHandler() {
            @Override
            public Mono<InitializeResult> handle(InitializeRequest request) {
                ServerCapabilities capabilities = new ServerCapabilities(
                    null, null, null, null, null, 
                    new ServerCapabilities.ToolCapabilities(true)
                );
                
                Implementation impl = new Implementation("pdfbox-mcp", "1.0.0");
                
                InitializeResult result = new InitializeResult(
                    "1.0", capabilities, impl, "Ready"
                );
                
                return Mono.just(result);
            }
        };
    }
    
    private static McpServerSession.InitNotificationHandler createInitNotificationHandler() {
        return () -> Mono.empty();
    }
    
    private static Map<String, McpServerSession.RequestHandler<?>> createRequestHandlers() {
        Map<String, McpServerSession.RequestHandler<?>> handlers = new HashMap<>();
        
        handlers.put("tools/list", params -> {
            ListToolsResult result = new ListToolsResult(List.of(
                createExtractTextTool(),
                createGetMetadataTool(),
                createGetPageCountTool()
            ));
            return Mono.just(result);
        });
        
        handlers.put("tools/call", params -> {
            try {
                CallToolRequest request = objectMapper.treeToValue(params, CallToolRequest.class);
                return handleToolCall(request);
            } catch (Exception e) {
                return Mono.just(createErrorResult("Error parsing tool call: " + e.getMessage()));
            }
        });
        
        return handlers;
    }
    
    private static Tool createExtractTextTool() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        
        ObjectNode properties = objectMapper.createObjectNode();
        ObjectNode filePathProp = objectMapper.createObjectNode();
        filePathProp.put("type", "string");
        filePathProp.put("description", "Path to the PDF file");
        properties.set("file_path", filePathProp);
        
        ObjectNode pageRangeProp = objectMapper.createObjectNode();
        pageRangeProp.put("type", "string");
        pageRangeProp.put("description", "Page range (e.g., '1-5' or 'all')");
        properties.set("page_range", pageRangeProp);
        
        schema.set("properties", properties);
        
        ArrayNode required = objectMapper.createArrayNode();
        required.add("file_path");
        schema.set("required", required);
        
        return new Tool("extract_text", "Extract text content from a PDF file", schema);
    }
    
    private static Tool createGetMetadataTool() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        
        ObjectNode properties = objectMapper.createObjectNode();
        ObjectNode filePathProp = objectMapper.createObjectNode();
        filePathProp.put("type", "string");
        filePathProp.put("description", "Path to the PDF file");
        properties.set("file_path", filePathProp);
        
        schema.set("properties", properties);
        
        ArrayNode required = objectMapper.createArrayNode();
        required.add("file_path");
        schema.set("required", required);
        
        return new Tool("get_metadata", "Extract metadata from a PDF file", schema);
    }
    
    private static Tool createGetPageCountTool() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        
        ObjectNode properties = objectMapper.createObjectNode();
        ObjectNode filePathProp = objectMapper.createObjectNode();
        filePathProp.put("type", "string");
        filePathProp.put("description", "Path to the PDF file");
        properties.set("file_path", filePathProp);
        
        schema.set("properties", properties);
        
        ArrayNode required = objectMapper.createArrayNode();
        required.add("file_path");
        schema.set("required", required);
        
        return new Tool("get_page_count", "Get the number of pages in a PDF file", schema);
    }
    
    private static Mono<CallToolResult> handleToolCall(CallToolRequest request) {
        try {
            switch (request.name()) {
                case "extract_text":
                    return handleExtractText(request.arguments());
                case "get_metadata":
                    return handleGetMetadata(request.arguments());
                case "get_page_count":
                    return handleGetPageCount(request.arguments());
                default:
                    return Mono.just(createErrorResult("Unknown tool: " + request.name()));
            }
        } catch (Exception e) {
            return Mono.just(createErrorResult("Error executing tool: " + e.getMessage()));
        }
    }
    
    private static Mono<CallToolResult> handleExtractText(JsonNode arguments) {
        try {
            String filePath = arguments.get("file_path").asText();
            String pageRange = arguments.has("page_range") ? arguments.get("page_range").asText() : "all";
            
            File pdfFile = new File(filePath);
            if (!pdfFile.exists()) {
                return Mono.just(createErrorResult("File not found: " + filePath));
            }
            
            try (PDDocument document = Loader.loadPDF(pdfFile)) {
                PDFTextStripper pdfStripper = new PDFTextStripper();
                
                if (!"all".equals(pageRange)) {
                    String[] range = pageRange.split("-");
                    if (range.length == 2) {
                        int startPage = Integer.parseInt(range[0]);
                        int endPage = Integer.parseInt(range[1]);
                        pdfStripper.setStartPage(startPage);
                        pdfStripper.setEndPage(endPage);
                    }
                }
                
                String text = pdfStripper.getText(document);
                return Mono.just(createTextResult(text));
            }
        } catch (Exception e) {
            return Mono.just(createErrorResult("Error extracting text: " + e.getMessage()));
        }
    }
    
    private static Mono<CallToolResult> handleGetMetadata(JsonNode arguments) {
        try {
            String filePath = arguments.get("file_path").asText();
            
            File pdfFile = new File(filePath);
            if (!pdfFile.exists()) {
                return Mono.just(createErrorResult("File not found: " + filePath));
            }
            
            try (PDDocument document = Loader.loadPDF(pdfFile)) {
                PDDocumentInformation info = document.getDocumentInformation();
                
                ObjectNode metadata = objectMapper.createObjectNode();
                metadata.put("title", info.getTitle());
                metadata.put("author", info.getAuthor());
                metadata.put("subject", info.getSubject());
                metadata.put("keywords", info.getKeywords());
                metadata.put("creator", info.getCreator());
                metadata.put("producer", info.getProducer());
                
                Calendar creationDate = info.getCreationDate();
                if (creationDate != null) {
                    metadata.put("creation_date", creationDate.getTime().toString());
                }
                
                Calendar modificationDate = info.getModificationDate();
                if (modificationDate != null) {
                    metadata.put("modification_date", modificationDate.getTime().toString());
                }
                
                metadata.put("page_count", document.getNumberOfPages());
                
                return Mono.just(createTextResult(metadata.toPrettyString()));
            }
        } catch (Exception e) {
            return Mono.just(createErrorResult("Error extracting metadata: " + e.getMessage()));
        }
    }
    
    private static Mono<CallToolResult> handleGetPageCount(JsonNode arguments) {
        try {
            String filePath = arguments.get("file_path").asText();
            
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
    
    private static CallToolResult createTextResult(String text) {
        TextContent content = new TextContent(text);
        return new CallToolResult(List.of(content), false);
    }
    
    private static CallToolResult createErrorResult(String error) {
        TextContent content = new TextContent(error);
        return new CallToolResult(List.of(content), true);
    }
}
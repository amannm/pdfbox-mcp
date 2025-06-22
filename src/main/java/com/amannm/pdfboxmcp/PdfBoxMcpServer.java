package com.amannm.pdfboxmcp;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpServerSession;
import io.modelcontextprotocol.spec.McpServerTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpSchema.InitializeResult;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
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
import java.time.Duration;
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

        // Keep the server running by blocking on a never-completing mono
        Mono.never().block();
    }

    private static McpServerSession.InitRequestHandler createInitRequestHandler() {
        return request -> {
            ServerCapabilities capabilities = new ServerCapabilities(
                null, null, null, null, null,
                new ServerCapabilities.ToolCapabilities(true)
            );

            Implementation impl = new Implementation("pdfbox-mcp", "1.0.0");

            InitializeResult result = new InitializeResult(
                "1.0", capabilities, impl, "Ready"
            );

            return Mono.just(result);
        };
    }

    private static McpServerSession.InitNotificationHandler createInitNotificationHandler() {
        return Mono::empty;
    }

    private static Map<String, McpServerSession.RequestHandler<?>> createRequestHandlers() {
        McpServerSession.RequestHandler<ListToolsResult> listHandler = (exchange, params) -> {
            ListToolsResult result = new ListToolsResult(
                List.of(
                    createExtractTextTool(),
                    createGetMetadataTool(),
                    createGetPageCountTool()
                ),
                null
            );
            return Mono.just(result);
        };

        McpServerSession.RequestHandler<CallToolResult> callHandler = (exchange, params) -> {
            try {
                CallToolRequest request = objectMapper.convertValue(params, CallToolRequest.class);
                return handleToolCall(request);
            } catch (Exception e) {
                return Mono.just(createErrorResult("Error parsing tool call: " + e.getMessage()));
            }
        };
        return Map.of(
            "tools/list", listHandler,
            "tools/call", callHandler
        );
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

    private static Mono<CallToolResult> handleToolCall(CallToolRequest request) {
        try {
            return switch (request.name()) {
                case "extract_text" -> handleExtractText(request.arguments());
                case "get_metadata" -> handleGetMetadata(request.arguments());
                case "get_page_count" -> handleGetPageCount(request.arguments());
                default -> Mono.just(createErrorResult("Unknown tool: " + request.name()));
            };
        } catch (Exception e) {
            return Mono.just(createErrorResult("Error executing tool: " + e.getMessage()));
        }
    }

    /**
     * Utility to open a PDF document, returning a result from the provided processor.
     */
    private static Mono<CallToolResult> withDocument(String filePath,
            java.util.function.Function<PDDocument, CallToolResult> processor) {
        File pdfFile = new File(filePath);
        if (!pdfFile.exists()) {
            return Mono.just(createErrorResult("File not found: " + filePath));
        }
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            return Mono.just(processor.apply(document));
        } catch (Exception e) {
            return Mono.just(createErrorResult("Error processing file: " + e.getMessage()));
        }
    }

    static Mono<CallToolResult> handleExtractText(Map<String, Object> arguments) {
        String filePath = String.valueOf(arguments.get("file_path"));
        String pageRange = arguments.containsKey("page_range")
            ? String.valueOf(arguments.get("page_range"))
            : "all";

        return withDocument(filePath, document -> {
            try {
                PDFTextStripper pdfStripper = new PDFTextStripper();

                if (!"all".equals(pageRange)) {
                    java.util.Optional<PageRange> range = PageRange.parse(pageRange);
                    if (range.isEmpty()) {
                        return createErrorResult("Invalid page range: " + pageRange);
                    }
                    pdfStripper.setStartPage(range.get().start());
                    pdfStripper.setEndPage(range.get().end());
                }

                String text = pdfStripper.getText(document);
                return createTextResult(text);
            } catch (Exception e) {
                return createErrorResult("Error extracting text: " + e.getMessage());
            }
        });
    }

    private static Mono<CallToolResult> handleGetMetadata(Map<String, Object> arguments) {
        String filePath = String.valueOf(arguments.get("file_path"));

        return withDocument(filePath, document -> {
            try {
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
                return createTextResult(json);
            } catch (Exception e) {
                return createErrorResult("Error extracting metadata: " + e.getMessage());
            }
        });
    }

    private static Mono<CallToolResult> handleGetPageCount(Map<String, Object> arguments) {
        String filePath = String.valueOf(arguments.get("file_path"));

        return withDocument(filePath, document -> {
            try {
                int pageCount = document.getNumberOfPages();
                return createTextResult("Page count: " + pageCount);
            } catch (Exception e) {
                return createErrorResult("Error getting page count: " + e.getMessage());
            }
        });
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

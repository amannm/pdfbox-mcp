package com.amannm.pdfboxmcp;

import static org.junit.jupiter.api.Assertions.*;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.Test;
import org.apache.pdfbox.pdmodel.PDDocument;
import java.io.File;
import java.util.Map;

import com.amannm.pdfboxmcp.PageRange;


public class PdfBoxMcpServerTest {
    @Test
    void testParsePageRangeSingle() {
        var result = PageRange.parse("3");
        assertTrue(result.isPresent());
        assertEquals(new PageRange(3, 3), result.get());
    }

    @Test
    void testParsePageRangeRange() {
        var result = PageRange.parse("2-5");
        assertTrue(result.isPresent());
        assertEquals(new PageRange(2, 5), result.get());
    }

    @Test
    void testParsePageRangeInvalid() {
        assertTrue(PageRange.parse("abc").isEmpty());
        assertTrue(PageRange.parse("3-").isEmpty());
        assertTrue(PageRange.parse("").isEmpty());
        assertTrue(PageRange.parse("5-3").isEmpty());
        assertTrue(PageRange.parse("0").isEmpty());
    }

    @Test
    void testCreateResults() {
        CallToolResult ok = PdfBoxMcpServer.createTextResult("hi");
        assertFalse(ok.isError());
        assertEquals("hi", ((io.modelcontextprotocol.spec.McpSchema.TextContent) ok.content().get(0)).text());

        CallToolResult err = PdfBoxMcpServer.createErrorResult("bad");
        assertTrue(err.isError());
        assertEquals("bad", ((io.modelcontextprotocol.spec.McpSchema.TextContent) err.content().get(0)).text());
    }

    @Test
    void testHandleExtractTextInvalidRange() throws Exception {
        File tmp = File.createTempFile("test", ".pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            doc.save(tmp);
        }

        var args = Map.of(
            "file_path", (Object) tmp.getAbsolutePath(),
            "page_range", (Object) "2-1"
        );
        CallToolResult result = PdfBoxMcpServer.handleExtractText(args).block();
        assertTrue(result.isError());
        tmp.delete();
    }
}

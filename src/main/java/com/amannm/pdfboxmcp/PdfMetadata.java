package com.amannm.pdfboxmcp;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Simple record to hold PDF metadata for JSON serialization.
 */
public record PdfMetadata(
    String title,
    String author,
    String subject,
    String keywords,
    String creator,
    String producer,
    @JsonProperty("creation_date") String creationDate,
    @JsonProperty("modification_date") String modificationDate,
    @JsonProperty("page_count") int pageCount
) {}

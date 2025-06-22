package com.amannm.pdfboxmcp;

/**
 * Represents a page range with inclusive start and end pages.
 */
public record PageRange(int start, int end) {

    /**
     * Parse a page range string like {@code "3"} or {@code "2-5"}.
     *
     * @param pageRange the range expression
     * @return an {@link java.util.Optional} containing the parsed range or empty if invalid
     */
    public static java.util.Optional<PageRange> parse(String pageRange) {
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
}

/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.renderer.html;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.eclipse.jdt.annotation.Nullable;

import de.sebthom.eclipse.previewer.api.ContentSource;
import de.sebthom.eclipse.previewer.api.HtmlPreviewRenderer;
import de.sebthom.eclipse.previewer.util.MiscUtils;
import de.sebthom.eclipse.previewer.util.StringUtils;

/**
 * Renders delimited text files as a bounded HTML table.
 *
 * @author Sebastian Thomschke
 */
public class CsvPreviewRenderer implements HtmlPreviewRenderer {

   private record DelimiterCandidate(char delimiter, int score) {
   }

   private record ParsedCsv(char delimiter, boolean hasHeader, boolean rowsTruncated, boolean columnsTruncated, List<List<String>> rows,
         int visibleColumnCount, int detectedColumnCount, List<String> columnSums) {
   }

   private static final char[] DELIMITER_CANDIDATES = {',', ';', '\t', '|'};
   private static final int MAX_CELL_LENGTH = 2000;
   private static final int MAX_COLUMNS = 200;
   private static final int MAX_DETECTION_RECORDS = 50;
   private static final int MAX_PREVIEW_ROWS = 1000;
   private static final int SAMPLE_CHAR_LIMIT = 64 * 1024;
   private static final Pattern BOOLEAN_PATTERN = Pattern.compile("(?i:true|false|yes|no|y|n)");
   private static final Pattern DATE_PATTERN = Pattern.compile("\\d{4}-\\d{1,2}-\\d{1,2}|\\d{1,2}[./-]\\d{1,2}[./-]\\d{2,4}");
   private static final Pattern HEADER_LABEL_PATTERN = Pattern.compile("[\\p{L}_][\\p{L}\\p{N}_ ./%()#-]{0,59}");

   private static void appendEscapedHtml(final Appendable out, final String value) throws IOException {
      for (int i = 0; i < value.length(); i++) {
         switch (value.charAt(i)) {
            case '&' -> out.append("&amp;");
            case '<' -> out.append("&lt;");
            case '>' -> out.append("&gt;");
            case '"' -> out.append("&quot;");
            case '\'' -> out.append("&#39;");
            default -> out.append(value.charAt(i));
         }
      }
   }

   @SuppressWarnings("null")
   private static CSVFormat csvFormat(final char delimiter) {
      return CSVFormat.DEFAULT.builder() //
         .setDelimiter(delimiter) //
         .setIgnoreEmptyLines(false) //
         .get();
   }

   private static DelimiterCandidate detectDelimiter(final String sample, final boolean preferTab) {
      var best = new DelimiterCandidate(preferTab ? '\t' : ',', Integer.MIN_VALUE);
      for (final char delimiter : DELIMITER_CANDIDATES) {
         final int score = scoreDelimiter(sample, delimiter, preferTab);
         if (score > best.score) {
            best = new DelimiterCandidate(delimiter, score);
         }
      }
      return best;
   }

   private static boolean detectHeader(final List<List<String>> rows) {
      if (rows.size() < 2)
         return false;

      final var firstRow = rows.get(0);
      final int columns = firstRow.size();
      if (columns < 2)
         return false;

      final var normalizedHeaders = new HashSet<String>();
      int blankColumn = -1;
      int labeledColumnCount = 0;
      for (int column = 0; column < columns; column++) {
         final String headerCell = firstRow.get(column).trim();
         if (headerCell.isBlank()) {
            if (blankColumn >= 0)
               return false;
            blankColumn = column;
            continue;
         }
         labeledColumnCount++;
         if (!normalizedHeaders.add(headerCell.toLowerCase(Locale.ROOT)))
            return false;
      }

      // Empty edge headers are common for row labels or trailing delimiters; middle blanks make detection too ambiguous.
      if (blankColumn > 0 && blankColumn < columns - 1)
         return false;

      int evidence = 0;
      for (int column = 0; column < columns; column++) {
         final String headerCell = firstRow.get(column).trim();
         if (!isHeaderLabel(headerCell)) {
            continue;
         }

         int inspectedValues = 0;
         int dataLikeValues = 0;
         for (int rowIndex = 1; inspectedValues < 8 && rowIndex < rows.size(); rowIndex++) {
            final var row = rows.get(rowIndex);
            if (column >= row.size()) {
               continue;
            }
            final String value = row.get(column).trim();
            if (value.isEmpty()) {
               continue;
            }
            inspectedValues++;
            if (isDataLike(value) || !isHeaderLabel(value)) {
               dataLikeValues++;
            }
         }

         if (dataLikeValues > 0 && dataLikeValues * 2 >= inspectedValues) {
            evidence++;
         }
      }

      return evidence >= Math.max(1, labeledColumnCount / 2);
   }

   private static String delimiterLabel(final char delimiter) {
      return switch (delimiter) {
         case '\t' -> "tab";
         case ',' -> "comma";
         case ';' -> "semicolon";
         case '|' -> "pipe";
         default -> Character.toString(delimiter);
      };
   }

   @Override
   public void dispose() {
   }

   private static boolean isDataLike(final String value) {
      return numericSortKey(value) != null //
            || BOOLEAN_PATTERN.matcher(value).matches() //
            || DATE_PATTERN.matcher(value).matches() //
            || value.indexOf('@') > 0 //
            || value.startsWith("http://") //
            || value.startsWith("https://");
   }

   private static boolean isHeaderLabel(final String value) {
      return !isDataLike(value) && HEADER_LABEL_PATTERN.matcher(value).matches();
   }

   private static String normalizeCell(final String value) {
      if (value.length() <= MAX_CELL_LENGTH)
         return value;

      int end = MAX_CELL_LENGTH;
      // Truncation is by UTF-16 code units; do not leave an unpaired high surrogate before the ellipsis.
      if (Character.isHighSurrogate(value.charAt(end - 1))) {
         end--;
      }
      return value.substring(0, end) + "...";
   }

   private static boolean isGroupedInteger(final String value, final char separator) {
      final String[] groups = value.split(Pattern.quote(Character.toString(separator)), -1);
      if (groups.length < 2 || !groups[0].matches("\\d{1,3}"))
         return false;
      for (int i = 1; i < groups.length; i++) {
         if (!groups[i].matches("\\d{3}"))
            return false;
      }
      return true;
   }

   private static List<String> normalizeRow(final Iterable<String> record) {
      final var row = new ArrayList<String>();
      for (final String value : record) {
         row.add(normalizeCell(value));
      }
      return row;
   }

   private static ParsedCsv parse(final ContentSource source) throws IOException {
      final var rows = new ArrayList<List<String>>();
      boolean rowsTruncated = false;
      int detectedColumnCount = 0;
      final boolean preferTab = source.path().getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".tsv");
      final char delimiter;
      try (BufferedReader reader = new BufferedReader(source.contentAsReader())) {
         // BufferedReader supplies mark/reset for sources that only expose a forward reader.
         // readSample is capped at the mark limit, so reset remains valid before Commons CSV parses the same stream.
         reader.mark(SAMPLE_CHAR_LIMIT);
         final String sample = readSample(reader);
         delimiter = detectDelimiter(sample, preferTab).delimiter;
         reader.reset();

         try (CSVParser parser = csvFormat(delimiter).parse(reader)) {
            for (final CSVRecord rec : parser) {
               if (rows.size() >= MAX_PREVIEW_ROWS) {
                  rowsTruncated = true;
                  break;
               }
               @SuppressWarnings("null")
               final var row = normalizeRow(rec);
               detectedColumnCount = Math.max(detectedColumnCount, row.size());
               rows.add(row);
            }
         }
      }

      final int visibleColumnCount = Math.min(detectedColumnCount, MAX_COLUMNS);
      final boolean columnsTruncated = detectedColumnCount > visibleColumnCount;
      final boolean hasHeader = detectHeader(rows);
      // A partial total looks authoritative, so only show sums when the complete file fits in the preview row limit.
      final List<String> columnSums = rowsTruncated ? List.of() : numericColumnSums(rows, hasHeader, visibleColumnCount);
      return new ParsedCsv(delimiter, hasHeader, rowsTruncated, columnsTruncated, rows, visibleColumnCount, detectedColumnCount,
         columnSums);
   }

   private static String readSample(final Reader reader) throws IOException {
      final var sample = new StringBuilder(SAMPLE_CHAR_LIMIT);
      final char[] buffer = new char[4096];
      while (sample.length() < SAMPLE_CHAR_LIMIT) {
         final int maxToRead = Math.min(buffer.length, SAMPLE_CHAR_LIMIT - sample.length());
         final int read = reader.read(buffer, 0, maxToRead);
         if (read == -1) {
            break;
         }
         sample.append(buffer, 0, read);
      }
      return sample.toString();
   }

   private static @Nullable String numericSortKey(final String value) {
      var trimmed = value.trim();
      if (trimmed.isEmpty())
         return null;
      if (trimmed.endsWith("%")) {
         trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
      }
      if (trimmed.isEmpty())
         return null;

      var sign = "";
      final char firstChar = trimmed.charAt(0);
      if (firstChar == '+' || firstChar == '-') {
         sign = Character.toString(firstChar);
         trimmed = trimmed.substring(1);
      }
      if (trimmed.isEmpty() || !trimmed.matches("[0-9,. ]+"))
         return null;

      final int lastComma = trimmed.lastIndexOf(',');
      final int lastDot = trimmed.lastIndexOf('.');
      Character decimalSeparator = null;
      if (lastComma >= 0 && lastDot >= 0) {
         decimalSeparator = lastComma > lastDot ? ',' : '.';
      } else {
         final @Nullable Character separator = lastComma >= 0 ? Character.valueOf(',') : lastDot >= 0 ? Character.valueOf('.') : null;
         if (separator != null) {
            final int separatorCount = trimmed.length() - trimmed.replace(separator.toString(), "").length();
            final int lastSeparator = trimmed.lastIndexOf(separator);
            final int digitsBefore = lastSeparator;
            final int digitsAfter = trimmed.length() - lastSeparator - 1;
            if (separatorCount == 1 && (digitsAfter != 3 || digitsBefore > 3 || trimmed.indexOf(' ') >= 0)) {
               decimalSeparator = separator;
            }
         }
      }

      String integerPart = trimmed;
      String fractionPart = "";
      if (decimalSeparator != null) {
         final int decimalIndex = trimmed.lastIndexOf(decimalSeparator);
         integerPart = trimmed.substring(0, decimalIndex);
         fractionPart = trimmed.substring(decimalIndex + 1);
         if (integerPart.indexOf(decimalSeparator) >= 0 || !fractionPart.matches("\\d+"))
            return null;
      }

      Character groupingSeparator = null;
      if (integerPart.indexOf(' ') >= 0) {
         groupingSeparator = ' ';
      } else if (Character.valueOf(',').equals(decimalSeparator)) {
         groupingSeparator = '.';
      } else if (Character.valueOf('.').equals(decimalSeparator)) {
         groupingSeparator = ',';
      } else if (integerPart.indexOf(',') >= 0) {
         groupingSeparator = ',';
      } else if (integerPart.indexOf('.') >= 0) {
         groupingSeparator = '.';
      }

      final boolean plainInteger = integerPart.matches("\\d+");
      if (!plainInteger && (groupingSeparator == null || !isGroupedInteger(integerPart, groupingSeparator)))
         return null;

      // Keep header detection and browser sorting on the same conservative parser; malformed grouping stays textual.
      final String normalizedInteger = groupingSeparator == null ? integerPart
            : integerPart.replace(Character.toString(groupingSeparator), "");
      return sign + normalizedInteger + (fractionPart.isEmpty() ? "" : "." + fractionPart);
   }

   private static List<String> numericColumnSums(final List<List<String>> rows, final boolean hasHeader, final int visibleColumnCount) {
      final int firstBodyRow = hasHeader ? 1 : 0;
      if (visibleColumnCount == 0 || firstBodyRow >= rows.size())
         return List.of();

      final var sums = new ArrayList<BigDecimal>(visibleColumnCount);
      final var numericCellCounts = new int[visibleColumnCount];
      final var hasNonNumericCells = new boolean[visibleColumnCount];
      for (int column = 0; column < visibleColumnCount; column++) {
         sums.add(BigDecimal.ZERO);
      }

      for (int rowIndex = firstBodyRow; rowIndex < rows.size(); rowIndex++) {
         final var row = rows.get(rowIndex);
         for (int column = 0; column < visibleColumnCount; column++) {
            final String value = column < row.size() ? row.get(column).trim() : "";
            if (value.isEmpty()) {
               continue;
            }
            final BigDecimal numericValue = numericSumValue(value);
            if (numericValue == null) {
               hasNonNumericCells[column] = true;
            } else {
               sums.set(column, sums.get(column).add(numericValue));
               numericCellCounts[column]++;
            }
         }
      }

      var hasSums = false;
      final var columnSums = new ArrayList<String>(visibleColumnCount);
      for (int column = 0; column < visibleColumnCount; column++) {
         if (!hasNonNumericCells[column] && numericCellCounts[column] > 0) {
            columnSums.add(sums.get(column).toPlainString());
            hasSums = true;
         } else {
            columnSums.add("");
         }
      }
      return hasSums ? columnSums : List.of();
   }

   private static @Nullable BigDecimal numericSumValue(final String value) {
      final String trimmed = value.trim();
      // Sorting accepts percentages, but a footer sum would drop the percent unit and imply a plain numeric total.
      if (trimmed.endsWith("%"))
         return null;

      final String numericValue = numericSortKey(trimmed);
      if (numericValue == null)
         return null;
      return new BigDecimal(numericValue);
   }

   private static void renderBody(final ParsedCsv csv, final Appendable out) throws IOException {
      final int firstBodyRow = csv.hasHeader() ? 1 : 0;
      out.append("<tbody>");
      for (int rowIndex = firstBodyRow; rowIndex < csv.rows().size(); rowIndex++) {
         final var row = csv.rows().get(rowIndex);
         final String displayRowNumber = Integer.toString(rowIndex - firstBodyRow + 1);
         out.append("<tr><th class=\"row-number\" scope=\"row\" data-sort-number=\"").append(displayRowNumber).append("\">").append(
            displayRowNumber).append("</th>");
         for (int column = 0; column < csv.visibleColumnCount(); column++) {
            renderCell(out, "td", column < row.size() ? row.get(column) : "");
         }
         out.append("</tr>");
      }
      out.append("</tbody>");
   }

   private static void renderFooter(final ParsedCsv csv, final Appendable out) throws IOException {
      if (csv.columnSums().isEmpty())
         return;

      // Keep aggregates outside tbody so column sorting never moves them with the data rows.
      out.append("<tfoot><tr><th class=\"row-number\" scope=\"row\">Sum</th>");
      for (int column = 0; column < csv.visibleColumnCount(); column++) {
         renderCell(out, "td", column < csv.columnSums().size() ? csv.columnSums().get(column) : "");
      }
      out.append("</tr></tfoot>");
   }

   private static void renderCell(final Appendable out, final String tagName, final String value) throws IOException {
      out.append('<').append(tagName);
      final String numericSortKey = numericSortKey(value);
      if (numericSortKey != null) {
         out.append(" data-sort-number=\"").append(numericSortKey).append('"');
      }
      out.append('>');
      appendEscapedHtml(out, value);
      out.append("</").append(tagName).append('>');
   }

   private static void renderSortableHeaderCell(final Appendable out, final int column, final String cssClass, final String value)
         throws IOException {
      out.append("<th aria-sort=\"").append(column < 0 ? "ascending" : "none").append("\" data-column=\"").append(Integer.toString(column))
         .append('"');
      if (!cssClass.isBlank()) {
         out.append(" class=\"").append(cssClass).append('"');
      }
      out.append(" scope=\"col\">");
      out.append("<button class=\"sort-button\" type=\"button\" aria-label=\"Sort by ");
      appendEscapedHtml(out, value);
      out.append("\"><span class=\"sort-label\">");
      appendEscapedHtml(out, value);
      out.append("</span><span class=\"sort-indicator\" aria-hidden=\"true\"></span></button></th>");
   }

   private static void renderHeader(final ParsedCsv csv, final Appendable out) throws IOException {
      if (csv.rows().isEmpty())
         return;

      out.append("<thead><tr>");
      renderSortableHeaderCell(out, -1, "row-number", "#");
      final var headerRow = csv.hasHeader() ? csv.rows().get(0) : List.<String>of();
      for (int column = 0; column < csv.visibleColumnCount(); column++) {
         final String header = csv.hasHeader() && column < headerRow.size() && !headerRow.get(column).isBlank() ? headerRow.get(column)
               : "Column " + (column + 1);
         renderSortableHeaderCell(out, column, "", header);
      }
      out.append("</tr></thead>");
   }

   @Override
   public void renderToHtml(final ContentSource source, final Appendable out) throws IOException {
      final ParsedCsv csv = parse(source);

      out.append("""
         <!DOCTYPE html>
         <html>
         <head>
           <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
           <style>
             body {
               margin: 0;
               padding: 8px;
               color: #202124;
               background: #ffffff;
               font-family: Arial, sans-serif;
               font-size: 13px;
             }
             table {
               border-collapse: collapse;
               table-layout: fixed;
               width: max-content;
               min-width: 100%;
             }
             th, td {
               border: 1px solid #d0d7de;
               max-width: 360px;
               min-width: 80px;
               overflow-wrap: anywhere;
               padding: 4px 6px;
               text-align: left;
               vertical-align: top;
               white-space: pre-wrap;
             }
             thead th {
               background: #f6f8fa;
               font-weight: 600;
               position: sticky;
               top: 0;
               z-index: 1;
             }
             .sort-button {
               align-items: flex-start;
               background: transparent;
               border: 0;
               color: inherit;
               cursor: pointer;
               display: flex;
               font: inherit;
               gap: 6px;
               justify-content: space-between;
               margin: -4px -6px;
               padding: 4px 6px;
               text-align: left;
               width: calc(100% + 12px);
             }
             .sort-button:focus {
               outline: 2px solid #0969da;
               outline-offset: -2px;
             }
             .row-number .sort-button {
               justify-content: flex-end;
               text-align: right;
             }
             .sort-indicator {
               color: #57606a;
               min-width: 1em;
               text-align: right;
             }
             th[aria-sort="ascending"] .sort-indicator::after {
               content: "^";
             }
             th[aria-sort="descending"] .sort-indicator::after {
               content: "v";
             }
             tbody tr:nth-child(even) td {
               background: #f8fafc;
             }
             tfoot th, tfoot td {
               background: #f6f8fa;
               font-weight: 600;
             }
             .row-number {
               background: #f6f8fa;
               color: #57606a;
               font-weight: 400;
               min-width: 48px;
               position: sticky;
               left: 0;
               text-align: right;
               width: 48px;
               z-index: 2;
             }
             .table-wrapper {
               overflow: auto;
               width: 100%;
             }
             .notice {
               background: #fff8c5;
               border: 1px solid #eac54f;
               margin-bottom: 8px;
               padding: 6px 8px;
             }
             @media (prefers-color-scheme: dark) {
               body {
                 color: #e6edf3;
                 background: #0d1117;
               }
               th, td {
                 border-color: #30363d;
               }
               thead th, .row-number {
                 background: #161b22;
                 color: #8b949e;
               }
               tbody tr:nth-child(even) td {
                 background: #111820;
               }
               tfoot th, tfoot td {
                 background: #161b22;
               }
               .notice {
                 background: #3b2f00;
                 border-color: #9e6a03;
               }
               .sort-indicator {
                 color: #8b949e;
               }
             }
           </style>
         </head>
         <body>
         """);

      if (csv.rowsTruncated() || csv.columnsTruncated()) {
         out.append("<div class=\"notice\">");
         if (csv.rowsTruncated()) {
            out.append("Preview is limited to ").append(Integer.toString(MAX_PREVIEW_ROWS)).append(" rows.");
         }
         if (csv.rowsTruncated() && csv.columnsTruncated()) {
            out.append(" ");
         }
         if (csv.columnsTruncated()) {
            out.append("Showing ").append(Integer.toString(csv.visibleColumnCount())).append(" of ").append(Integer.toString(csv
               .detectedColumnCount())).append(" columns.");
         }
         out.append("</div>");
      }

      out.append("<div class=\"table-wrapper\"><table>");
      renderHeader(csv, out);
      renderBody(csv, out);
      renderFooter(csv, out);
      out.append("</table></div>");
      out.append("""
         <script>
         (function() {
           var table = document.querySelector("table");
           if (!table || !table.tBodies.length)
             return;

           var tbody = table.tBodies[0];
           var headers = table.querySelectorAll("thead th[data-column]");

           function parseDate(value) {
             var trimmed = value.trim();
             if (!/^\\d{4}-\\d{1,2}-\\d{1,2}(?:[T ].*)?$/.test(trimmed))
               return null;
             var parsed = Date.parse(trimmed);
             return isNaN(parsed) ? null : parsed;
           }

           function compareValues(left, right, ascending) {
             var leftText = left.value.trim();
             var rightText = right.value.trim();
             if (!leftText && !rightText)
               return 0;
             if (!leftText)
               return 1;
             if (!rightText)
               return -1;

             var result;
             if (left.sortNumber !== null && right.sortNumber !== null) {
               result = Number(left.sortNumber) - Number(right.sortNumber);
             } else {
               var leftDate = parseDate(leftText);
               var rightDate = parseDate(rightText);
               if (leftDate !== null && rightDate !== null) {
                 result = leftDate - rightDate;
               } else {
                 leftText = leftText.toLocaleLowerCase();
                 rightText = rightText.toLocaleLowerCase();
                 result = leftText < rightText ? -1 : leftText > rightText ? 1 : 0;
               }
             }
             return ascending ? result : -result;
           }

           function sortByColumn(header) {
             var column = parseInt(header.getAttribute("data-column"), 10);
             var ascending = header.getAttribute("aria-sort") != "ascending";
             for (var i = 0; i < headers.length; i++)
               headers[i].setAttribute("aria-sort", "none");
             header.setAttribute("aria-sort", ascending ? "ascending" : "descending");

             var rows = [];
             for (var rowIndex = 0; rowIndex < tbody.rows.length; rowIndex++) {
               var row = tbody.rows[rowIndex];
               var cell = row.cells[column + 1];
               rows.push({
                 row: row,
                 index: rowIndex,
                 value: cell ? cell.textContent : "",
                 sortNumber: cell ? cell.getAttribute("data-sort-number") : null
               });
             }

             rows.sort(function(left, right) {
               var result = compareValues(left, right, ascending);
               return result == 0 ? left.index - right.index : result;
             });

             for (var sortedIndex = 0; sortedIndex < rows.length; sortedIndex++)
               tbody.appendChild(rows[sortedIndex].row);
           }

           for (var headerIndex = 0; headerIndex < headers.length; headerIndex++) {
             (function(header) {
               var button = header.querySelector("button");
               if (button)
                 button.onclick = function() {
                   sortByColumn(header);
                 };
             })(headers[headerIndex]);
           }
         }());
         </script>
         """);

      final var shortPath = source.shortDisplayPath();
      final var info = new StringBuilder();
      appendEscapedHtml(info, shortPath);
      info.append(" (delimiter: ").append(delimiterLabel(csv.delimiter())).append(", ");
      info.append(csv.hasHeader() ? "header detected" : "no header detected").append(", ");
      info.append(csv.rows().size()).append(csv.rowsTruncated() ? "+ rows" : " rows").append(") ");
      info.append(MiscUtils.getCurrentTime());
      out.append(StringUtils.htmlInfoBox(info.toString()));
      out.append("</body></html>");
   }

   private static int scoreDelimiter(final String sample, final char delimiter, final boolean preferTab) {
      int records = 0;
      int multiColumnRecords = 0;
      int maxColumns = 0;
      final var columnCounts = new ArrayList<Integer>();

      try (CSVParser parser = csvFormat(delimiter).parse(new StringReader(sample))) {
         for (final CSVRecord rec : parser) {
            records++;
            final int columns = rec.size();
            columnCounts.add(columns);
            maxColumns = Math.max(maxColumns, columns);
            if (columns > 1) {
               multiColumnRecords++;
            }
            if (records >= MAX_DETECTION_RECORDS) {
               break;
            }
         }
      } catch (final IOException | IllegalArgumentException ex) {
         return Integer.MIN_VALUE;
      }

      if (records == 0)
         return Integer.MIN_VALUE + 1;

      int modeColumnCount = 0;
      int modeFrequency = 0;
      for (final int columns : columnCounts) {
         int frequency = 0;
         for (final int other : columnCounts) {
            if (columns == other) {
               frequency++;
            }
         }
         if (frequency > modeFrequency) {
            modeColumnCount = columns;
            modeFrequency = frequency;
         }
      }

      int score = multiColumnRecords * 20 + maxColumns * 8 + modeFrequency * 10;
      for (final int columns : columnCounts) {
         score -= Math.abs(columns - modeColumnCount) * 5;
      }
      if (maxColumns <= 1) {
         score -= 100;
      }
      if (preferTab && delimiter == '\t') {
         score += 50;
      } else if (!preferTab && delimiter == ',') {
         score += 5;
      }
      return score;
   }
}

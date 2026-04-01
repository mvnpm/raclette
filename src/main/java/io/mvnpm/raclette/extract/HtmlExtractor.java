package io.mvnpm.raclette.extract;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.Range;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

import io.mvnpm.raclette.types.RawUri;

/**
 * Extracts links and fragments from HTML content using JSoup.
 *
 * Translated from lychee's extract/html/html5gum.rs.
 * Uses JSoup (DOM-based) instead of html5gum (streaming tokenizer),
 * but preserves the same filtering logic:
 * - Skip verbatim elements (pre, code, script, etc.) unless includeVerbatim
 * - Skip nofollow, preconnect, dns-prefetch links
 * - Skip disabled stylesheets
 * - Skip prefix attributes
 * - Skip virtual stylesheet paths (/@...)
 * - Skip bare email addresses (only mailto: and tel: in href)
 * - Extract srcset URLs via SrcsetParser
 * - Extract id and name attributes as fragments
 */
public class HtmlExtractor {

    private static final Set<String> VERBATIM_ELEMENTS = Set.of(
            "code", "kbd", "listing", "noscript", "plaintext",
            "pre", "samp", "script", "textarea", "var", "xmp");

    private static final Set<String> LINK_ATTRIBUTES = Set.of(
            "href", "src", "cite", "usemap");

    // Simple email pattern for bare email detection in plaintext
    private static final Pattern BARE_EMAIL_PATTERN = Pattern.compile(
            "[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}");

    /**
     * Extract all links from HTML content.
     *
     * @param html the HTML content
     * @param includeVerbatim whether to include links found inside pre/code blocks
     * @return list of extracted raw URIs
     */
    public List<RawUri> extractLinks(String html, boolean includeVerbatim) {
        Parser parser = Parser.htmlParser().setTrackPosition(true);
        Document doc = Jsoup.parse(html, "", parser);
        List<RawUri> links = new ArrayList<>();
        LinkVisitor visitor = new LinkVisitor(links, includeVerbatim);
        NodeTraversor.traverse(visitor, doc);
        return links;
    }

    /**
     * Extract all fragment targets (id and name attributes) from HTML content.
     *
     * @param html the HTML content
     * @return set of fragment identifiers
     */
    public Set<String> extractFragments(String html) {
        Document doc = Jsoup.parse(html, "", Parser.htmlParser());
        Set<String> fragments = new HashSet<>();
        for (Element el : doc.getAllElements()) {
            String id = el.attr("id");
            if (!id.isEmpty()) {
                fragments.add(id);
            }
            String name = el.attr("name");
            if (!name.isEmpty()) {
                fragments.add(name);
            }
        }
        return fragments;
    }

    private static boolean isVerbatimElement(String tagName) {
        return VERBATIM_ELEMENTS.contains(tagName);
    }

    /**
     * Check if the given text looks like an email address.
     * Mirrors lychee's is_email_link: the whole string must be an email.
     */
    private static boolean isEmailLink(String input) {
        String check = input.startsWith("mailto:") ? input.substring(7) : input;
        // Simple email check: contains @, the @ is not at start/end,
        // and there's a dot after the @
        int at = check.indexOf('@');
        if (at <= 0 || at == check.length() - 1) {
            return false;
        }
        // Must not contain spaces
        if (check.contains(" ")) {
            return false;
        }
        int dot = check.indexOf('.', at);
        return dot > at + 1 && dot < check.length() - 1;
    }

    /**
     * NodeVisitor that extracts links while respecting verbatim blocks and filtering rules.
     */
    private static class LinkVisitor implements NodeVisitor {
        private final List<RawUri> links;
        private final boolean includeVerbatim;
        private int verbatimDepth = 0;

        LinkVisitor(List<RawUri> links, boolean includeVerbatim) {
            this.links = links;
            this.includeVerbatim = includeVerbatim;
        }

        @Override
        public void head(Node node, int depth) {
            if (node instanceof Element el) {
                String tag = el.tagName();

                // Track verbatim nesting
                if (!includeVerbatim && isVerbatimElement(tag)) {
                    verbatimDepth++;
                }

                // Don't extract from verbatim blocks
                if (!includeVerbatim && verbatimDepth > 0) {
                    return;
                }

                // Check rel attribute for nofollow, preconnect, dns-prefetch
                String rel = el.attr("rel");
                if (!rel.isEmpty()) {
                    // Simple split on comma + trim (matches lychee html5gum.rs:162-167)
                    for (String r : rel.split(",")) {
                        String trimmed = r.trim();
                        if (trimmed.equals("nofollow") || trimmed.equals("preconnect")
                                || trimmed.equals("dns-prefetch")) {
                            return;
                        }
                    }
                }

                // Skip prefix attribute on ANY element (not just <html>).
                // Matches lychee html5gum.rs:170 — applies to all elements, not only RDFa on <html>.
                if (el.hasAttr("prefix")) {
                    return;
                }

                // Skip disabled stylesheets
                if (rel.contains("stylesheet")) {
                    if (el.hasAttr("disabled")) {
                        return;
                    }
                    String href = el.attr("href");
                    if (href.startsWith("/@") || href.startsWith("@")) {
                        return;
                    }
                }

                // Process srcset
                if (el.hasAttr("srcset")) {
                    String srcset = el.attr("srcset");
                    Range.AttributeRange srcsetRange = el.attributes().sourceRange("srcset");
                    Range.Position srcsetPos = srcsetRange.valueRange().start();
                    List<String> urls = SrcsetParser.parse(srcset);
                    for (String url : urls) {
                        // Apply same email filter as other attributes
                        if (isEmailLink(url)) {
                            continue;
                        }
                        links.add(new RawUri(url, tag, "srcset", srcsetPos.lineNumber(), srcsetPos.columnNumber()));
                    }
                }

                // Process standard link attributes
                for (Attribute attr : el.attributes()) {
                    String attrName = attr.getKey();
                    String attrValue = attr.getValue();

                    if (!isLinkAttribute(tag, attrName)) {
                        continue;
                    }

                    if (attrValue == null || attrValue.isEmpty()) {
                        continue;
                    }

                    // Filter email-like links
                    if (isEmailLink(attrValue)) {
                        boolean isMailto = attrValue.startsWith("mailto:");
                        boolean isTel = attrValue.startsWith("tel:");
                        boolean isHref = attrName.equals("href");
                        if (!((isMailto && isHref) || (isTel && isHref))) {
                            continue;
                        }
                    }

                    Range.AttributeRange attrRange = el.attributes().sourceRange(attrName);
                    Range.Position attrPos = attrRange.valueRange().start();
                    links.add(new RawUri(attrValue, tag, attrName, attrPos.lineNumber(), attrPos.columnNumber()));
                }
            } else if (node instanceof TextNode textNode) {
                // When includeVerbatim, extract plain text URLs from ALL text nodes
                // (matching lychee's behavior: text URLs are always extracted,
                // but filtered out at the end unless includeVerbatim=true).
                // When !includeVerbatim, skip text nodes entirely (no text URLs).
                if (includeVerbatim) {
                    Range sourceRange = textNode.sourceRange();
                    int startLine = sourceRange.isTracked() ? sourceRange.start().lineNumber() : 0;
                    int startCol = sourceRange.isTracked() ? sourceRange.start().columnNumber() : 0;
                    extractPlainTextUrls(textNode.getWholeText(), links, startLine, startCol);
                }
            }
        }

        @Override
        public void tail(Node node, int depth) {
            if (node instanceof Element el) {
                String tag = el.tagName();
                if (!includeVerbatim && isVerbatimElement(tag) && verbatimDepth > 0) {
                    verbatimDepth--;
                }
            }
        }

        private static boolean isLinkAttribute(String element, String attribute) {
            // Universal link attributes
            if (LINK_ATTRIBUTES.contains(attribute)) {
                return true;
            }
            // Element-specific attributes
            return switch (element) {
                case "applet" -> attribute.equals("codebase");
                case "body" -> attribute.equals("background");
                case "button", "input" -> attribute.equals("formaction");
                case "command" -> attribute.equals("icon");
                case "form" -> attribute.equals("action");
                case "frame", "iframe", "img" -> attribute.equals("longdesc");
                case "head" -> attribute.equals("profile");
                case "html" -> attribute.equals("manifest");
                case "object" -> attribute.equals("classid") || attribute.equals("codebase")
                        || attribute.equals("data");
                case "video" -> attribute.equals("poster");
                default -> false;
            };
        }

        /**
         * Extract URLs from plain text (for verbatim blocks when includeVerbatim is true).
         * Finds http://, https://, and mailto: URIs.
         * Calculates line/column offsets relative to the text node's start position.
         */
        private static void extractPlainTextUrls(String text, List<RawUri> links, int startLine, int startCol) {
            int i = 0;
            // Track current line/col as we scan through the text
            int currentLine = startLine;
            int currentCol = startCol;
            int lastTrackedPos = 0;

            while (i < text.length()) {
                int httpIdx = text.indexOf("http://", i);
                int httpsIdx = text.indexOf("https://", i);
                int mailtoIdx = text.indexOf("mailto:", i);

                // Find the earliest match
                int startIdx = -1;
                if (httpIdx >= 0) {
                    startIdx = httpIdx;
                }
                if (httpsIdx >= 0 && (startIdx < 0 || httpsIdx < startIdx)) {
                    startIdx = httpsIdx;
                }
                if (mailtoIdx >= 0 && (startIdx < 0 || mailtoIdx < startIdx)) {
                    startIdx = mailtoIdx;
                }

                if (startIdx < 0) {
                    break;
                }

                // Update line/col tracking from lastTrackedPos to startIdx
                for (int j = lastTrackedPos; j < startIdx; j++) {
                    if (text.charAt(j) == '\n') {
                        currentLine++;
                        currentCol = 1;
                    } else {
                        currentCol++;
                    }
                }
                lastTrackedPos = startIdx;

                // Find end of URL (whitespace, <, >, ", ')
                int endIdx = startIdx;
                while (endIdx < text.length()) {
                    char c = text.charAt(endIdx);
                    if (Character.isWhitespace(c) || c == '<' || c == '>' || c == '"' || c == '\'') {
                        break;
                    }
                    endIdx++;
                }

                String url = text.substring(startIdx, endIdx);
                // For mailto: URIs in plaintext, strip query params (matches lychee's linkify behavior)
                if (url.startsWith("mailto:")) {
                    int qmark = url.indexOf('?');
                    if (qmark > 0) {
                        url = url.substring(0, qmark);
                    }
                }
                links.add(RawUri.ofText(url, currentLine, currentCol));
                i = endIdx;
            }

            // Also extract bare email addresses (matches lychee's plaintext extractor)
            extractBareEmails(text, links, startLine, startCol);
        }

        /**
         * Extract bare email addresses from text and add as mailto: URIs.
         * Skips emails that are already part of a mailto: URI (already captured above).
         */
        private static void extractBareEmails(String text, List<RawUri> links, int startLine,
                int startCol) {
            Matcher matcher = BARE_EMAIL_PATTERN.matcher(text);
            while (matcher.find()) {
                int pos = matcher.start();
                // Skip if preceded by "mailto:" — already captured
                if (pos >= 7 && text.substring(pos - 7, pos).equals("mailto:")) {
                    continue;
                }
                String email = matcher.group();
                links.add(RawUri.ofText("mailto:" + email, startLine, startCol));
            }
        }
    }
}

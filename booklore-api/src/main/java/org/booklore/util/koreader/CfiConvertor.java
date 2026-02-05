package org.booklore.util.koreader;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class CfiConvertor {

    private static final Pattern CFI_PATTERN = Pattern.compile("^epubcfi\\((.+)\\)$");
    private static final Pattern CFI_SPINE_PATTERN = Pattern.compile("^/6/(\\d+)!(.*)$");
    private static final Pattern CFI_PATH_STEP_PATTERN = Pattern.compile("/(\\d+)(?:\\[(.*?)\\])?(?::(\\d+))?");
    private static final Pattern XPOINTER_DOC_FRAGMENT_PATTERN = Pattern.compile("^/body/DocFragment\\[(\\d+)\\]/body(.*)$");
    private static final Pattern XPOINTER_TEXT_OFFSET_PATTERN = Pattern.compile("/text\\(\\)(?:\\[\\d+\\])?\\.(\\d+)$");
    private static final Pattern XPOINTER_SEGMENT_WITH_INDEX_PATTERN = Pattern.compile("^(\\w+)\\[(\\d+)\\]$");
    private static final Pattern XPOINTER_SEGMENT_WITHOUT_INDEX_PATTERN = Pattern.compile("^(\\w+)$");
    private static final Pattern TRAILING_TEXT_OFFSET_PATTERN = Pattern.compile("/text\\(\\).*$");
    private static final Pattern SUFFIX_NODE_OFFSET_PATTERN = Pattern.compile("\\.\\d+$");

    private static final Set<String> INLINE_ELEMENTS = Set.of(
            "span", "em", "strong", "i", "b", "u", "small", "mark", "sup", "sub",
            "a", "abbr", "cite", "code", "dfn", "kbd", "q", "ruby", "s", "samp",
            "time", "var", "wbr", "del", "ins"
    );

    private final Document document;
    private final int spineItemIndex;

    public CfiConvertor(Document htmlDocument, int spineIndex) {
        this.document = htmlDocument;
        this.spineItemIndex = spineIndex;
    }

    public CfiConvertor(Document htmlDocument) {
        this(htmlDocument, 0);
    }

    public static int extractSpineIndex(String cfiOrXPath) {
        if (cfiOrXPath == null || cfiOrXPath.isBlank()) {
            throw new IllegalArgumentException("CFI/XPointer string cannot be null or empty");
        }

        if (cfiOrXPath.startsWith("epubcfi(")) {
            return extractSpineIndexFromCfi(cfiOrXPath);
        } else if (cfiOrXPath.startsWith("/body/DocFragment[")) {
            return extractSpineIndexFromXPointer(cfiOrXPath);
        } else {
            throw new IllegalArgumentException("Unsupported format for spine index extraction: " + cfiOrXPath);
        }
    }

    private static int extractSpineIndexFromCfi(String cfi) {
        Matcher cfiMatcher = CFI_PATTERN.matcher(cfi);
        if (!cfiMatcher.matches()) {
            throw new IllegalArgumentException("Invalid CFI format: " + cfi);
        }

        String innerCfi = cfiMatcher.group(1);
        Matcher spineMatcher = CFI_SPINE_PATTERN.matcher(innerCfi);
        if (!spineMatcher.matches()) {
            throw new IllegalArgumentException("Cannot extract spine index from CFI: " + cfi);
        }

        int spineStep = Integer.parseInt(spineMatcher.group(1));
        return (spineStep - 2) / 2;
    }

    private static int extractSpineIndexFromXPointer(String xpointer) {
        Pattern pattern = Pattern.compile("DocFragment\\[(\\d+)\\]");
        Matcher matcher = pattern.matcher(xpointer);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1)) - 1;
        }
        throw new IllegalArgumentException("Cannot extract spine index from XPointer: " + xpointer);
    }

    public String xPointerToCfi(String startXPointer, String endXPointer) {
        if (endXPointer != null && !endXPointer.isBlank()) {
            return convertRangeXPointerToCfi(startXPointer, endXPointer);
        }
        return convertPointXPointerToCfi(startXPointer);
    }

    public String xPointerToCfi(String xpointer) {
        return xPointerToCfi(xpointer, null);
    }

    public XPointerResult cfiToXPointer(String cfi) {
        Matcher cfiMatcher = CFI_PATTERN.matcher(cfi);
        if (!cfiMatcher.matches()) {
            throw new IllegalArgumentException("Invalid CFI format: " + cfi);
        }

        String innerCfi = cfiMatcher.group(1);
        Matcher spineMatcher = CFI_SPINE_PATTERN.matcher(innerCfi);
        if (!spineMatcher.matches()) {
            throw new IllegalArgumentException("Cannot parse CFI spine step: " + cfi);
        }

        int spineStep = Integer.parseInt(spineMatcher.group(1));
        int cfiSpineIndex = (spineStep - 2) / 2;

        if (cfiSpineIndex != spineItemIndex) {
            throw new IllegalArgumentException(
                    String.format("CFI spine index %d does not match converter spine index %d",
                            cfiSpineIndex, spineItemIndex));
        }

        String contentPath = resolveRangeCfiToStartPoint(spineMatcher.group(2));
        CfiPathResult pathResult = parseCfiPath(contentPath);

        // The first step (/4) is the body reference in EPUB CFI spec (2nd child of html root).
        // Since we already start at document.body(), skip it.
        List<CfiStep> steps = pathResult.steps();
        if (!steps.isEmpty() && steps.get(0).index() == 4) {
            steps = steps.subList(1, steps.size());
        }

        Element element = resolveElementFromCfiSteps(steps);
        if (element == null) {
            throw new IllegalArgumentException("Element not found for CFI: " + cfi);
        }

        String xpointer;
        if (pathResult.textOffset() != null) {
            xpointer = handleTextOffset(element, pathResult.textOffset());
        } else {
            xpointer = buildXPointerPath(element);
        }

        return new XPointerResult(xpointer, xpointer, xpointer);
    }

    public String cfiToKoreaderProgressXPointer(String cfi) {
        Matcher cfiMatcher = CFI_PATTERN.matcher(cfi);
        if (!cfiMatcher.matches()) {
            throw new IllegalArgumentException("Invalid CFI format: " + cfi);
        }

        String innerCfi = cfiMatcher.group(1);
        Matcher spineMatcher = CFI_SPINE_PATTERN.matcher(innerCfi);
        if (!spineMatcher.matches()) {
            throw new IllegalArgumentException("Cannot parse CFI spine step: " + cfi);
        }

        int spineStep = Integer.parseInt(spineMatcher.group(1));
        int cfiSpineIndex = (spineStep - 2) / 2;

        if (cfiSpineIndex != spineItemIndex) {
            throw new IllegalArgumentException(
                    String.format("CFI spine index %d does not match converter spine index %d",
                            cfiSpineIndex, spineItemIndex));
        }

        String rawContentPath = spineMatcher.group(2);
        String contentPath = resolveRangeCfiToStartPoint(rawContentPath);
        log.debug("CFI to XPointer: raw path='{}', resolved start path='{}'", rawContentPath, contentPath);

        CfiPathResult pathResult = parseCfiPath(contentPath);
        log.debug("Parsed CFI path: steps={}, textOffset={}", pathResult.steps(), pathResult.textOffset());

        List<CfiStep> steps = pathResult.steps();
        if (!steps.isEmpty() && steps.get(0).index() == 4) {
            steps = steps.subList(1, steps.size());
            log.debug("Skipped body reference /4, remaining steps: {}", steps);
        }

        Element element = resolveElementFromCfiSteps(steps);
        if (element == null) {
            throw new IllegalArgumentException("Element not found for CFI: " + cfi);
        }

        Element originalElement = element;
        // Walk up to the nearest block-level element
        while (element != null && INLINE_ELEMENTS.contains(element.tagName().toLowerCase())) {
            element = element.parent();
        }
        if (element == null || element.tagName().equalsIgnoreCase("body")) {
            throw new IllegalArgumentException("Could not find block-level element for CFI: " + cfi);
        }

        if (element != originalElement) {
            log.debug("Walked up from inline <{}> to block-level <{}>", originalElement.tagName(), element.tagName());
        }

        String xpointer = buildKoreaderXPointer(element);
        log.debug("Built KOReader XPointer: {}", xpointer);
        return xpointer;
    }

    private String buildKoreaderXPointer(Element targetElement) {
        // Build hierarchical path from body to target element
        // KOReader expects paths like /body/DocFragment[N]/body/div[1]/section/p[3]
        List<String> pathParts = new ArrayList<>();
        Element current = targetElement;
        Element body = document.body();

        while (current != null && current != body) {
            Element parent = current.parent();
            if (parent == null) {
                break;
            }

            String tagName = current.tagName().toLowerCase();

            // Count same-tag siblings before this element
            int siblingIndex = 0;
            int totalSameTagSiblings = 0;
            for (Element sibling : parent.children()) {
                if (sibling.tagName().equalsIgnoreCase(tagName)) {
                    if (sibling == current) {
                        siblingIndex = totalSameTagSiblings;
                    }
                    totalSameTagSiblings++;
                }
            }

            // Only add index if there are multiple siblings of the same tag
            if (totalSameTagSiblings == 1) {
                pathParts.add(0, tagName);
            } else {
                pathParts.add(0, String.format("%s[%d]", tagName, siblingIndex + 1));
            }

            current = parent;
        }

        String elementPath = pathParts.isEmpty() ? "" : "/" + String.join("/", pathParts);

        log.debug("Building KOReader XPointer (hierarchical): path='{}', elementId='{}', elementText='{}'",
                elementPath, targetElement.id(),
                targetElement.text().substring(0, Math.min(50, targetElement.text().length())));

        return String.format("/body/DocFragment[%d]/body%s",
                spineItemIndex + 1, elementPath);
    }

    public boolean validateCfi(String cfi) {
        try {
            cfiToXPointer(cfi);
            return true;
        } catch (Exception e) {
            log.debug("CFI validation failed: {}", e.getMessage());
            return false;
        }
    }

    public boolean validateXPointer(String xpointer, String pos1) {
        try {
            xPointerToCfi(xpointer, pos1);
            return true;
        } catch (Exception e) {
            log.debug("XPointer validation failed: {}", e.getMessage());
            return false;
        }
    }

    public boolean validateXPointer(String xpointer) {
        return validateXPointer(xpointer, null);
    }

    public static String normalizeProgressXPointer(String xpointer) {
        if (xpointer == null) {
            return null;
        }
        String result = TRAILING_TEXT_OFFSET_PATTERN.matcher(xpointer).replaceAll("");
        result = SUFFIX_NODE_OFFSET_PATTERN.matcher(result).replaceAll("");
        return result;
    }

    private String convertPointXPointerToCfi(String xpointer) {
        XPointerParseResult parseResult = parseXPointer(xpointer);
        String cfiPath = buildCfiPathFromElement(parseResult.element());

        if (parseResult.textOffset() != null) {
            // CFI requires /1 to indicate first text node child before the character offset
            cfiPath += "/1:" + parseResult.textOffset();
        }

        return buildFullCfi(cfiPath);
    }

    private String convertRangeXPointerToCfi(String startXPointer, String endXPointer) {
        XPointerParseResult startResult = parseXPointer(startXPointer);
        XPointerParseResult endResult = parseXPointer(endXPointer);

        String startPath = buildCfiPathFromElement(startResult.element());
        String endPath = buildCfiPathFromElement(endResult.element());

        if (startResult.textOffset() != null) {
            // CFI requires /1 to indicate first text node child before the character offset
            startPath += "/1:" + startResult.textOffset();
        }
        if (endResult.textOffset() != null) {
            // CFI requires /1 to indicate first text node child before the character offset
            endPath += "/1:" + endResult.textOffset();
        }

        if (!startPath.equals(endPath)) {
            return buildFullCfi(startPath + "," + endPath);
        }
        return buildFullCfi(startPath);
    }

    private XPointerParseResult parseXPointer(String xpointer) {
        Matcher textOffsetMatcher = XPOINTER_TEXT_OFFSET_PATTERN.matcher(xpointer);
        Integer textOffset = null;
        String elementPath = xpointer;

        if (textOffsetMatcher.find()) {
            textOffset = Integer.parseInt(textOffsetMatcher.group(1));
            elementPath = XPOINTER_TEXT_OFFSET_PATTERN.matcher(xpointer).replaceAll("");
        }

        Element element = resolveXPointerPath(elementPath);
        if (element == null) {
            throw new IllegalArgumentException("Cannot resolve XPointer path: " + elementPath);
        }

        return new XPointerParseResult(element, textOffset);
    }

    private Element resolveXPointerPath(String path) {
        Matcher pathMatcher = XPOINTER_DOC_FRAGMENT_PATTERN.matcher(path);
        if (!pathMatcher.matches()) {
            throw new IllegalArgumentException("Invalid XPointer format: " + path);
        }

        String elementPath = pathMatcher.group(2);
        Element body = document.body();

        if (body == null) {
            throw new IllegalArgumentException("Document has no body element");
        }

        if (elementPath == null || elementPath.isEmpty()) {
            return body;
        }

        String[] segments = elementPath.split("/");

        // First, try hierarchical traversal (preferred for new paths)
        Element hierarchicalResult = tryHierarchicalTraversal(body, segments);
        if (hierarchicalResult != null) {
            log.debug("XPointer resolved via hierarchical traversal: <{}>", hierarchicalResult.tagName());
            return hierarchicalResult;
        }

        // Fall back to global counting for backwards compatibility
        // Extract the final indexed segment (e.g., p[54] from /div/div/p[54])
        String lastSegment = null;
        for (int i = segments.length - 1; i >= 0; i--) {
            if (!segments[i].isEmpty()) {
                lastSegment = segments[i];
                break;
            }
        }

        if (lastSegment == null) {
            return body;
        }

        Matcher withIndexMatcher = XPOINTER_SEGMENT_WITH_INDEX_PATTERN.matcher(lastSegment);
        if (withIndexMatcher.matches()) {
            String tagName = withIndexMatcher.group(1);
            int index = Integer.parseInt(withIndexMatcher.group(2)) - 1;

            // Find the element globally in the document body
            Elements allElements = body.getElementsByTag(tagName);
            if (index < allElements.size()) {
                log.debug("XPointer resolved via global counting: found {}[{}] among {} total in document",
                        tagName, index + 1, allElements.size());
                return allElements.get(index);
            } else {
                throw new IllegalArgumentException(
                        String.format("Element index %d out of bounds for tag %s (found %d in document)",
                                index, tagName, allElements.size()));
            }
        }

        throw new IllegalArgumentException("Could not resolve XPointer path: " + path);
    }

    /**
     * Try to resolve XPointer path using hierarchical traversal.
     * Returns null if traversal fails at any point.
     */
    private Element tryHierarchicalTraversal(Element body, String[] segments) {
        Element current = body;

        for (String segment : segments) {
            if (segment.isEmpty()) {
                continue;
            }

            Matcher segWithIndex = XPOINTER_SEGMENT_WITH_INDEX_PATTERN.matcher(segment);
            Matcher segWithoutIndex = XPOINTER_SEGMENT_WITHOUT_INDEX_PATTERN.matcher(segment);

            String tagName;
            int index;

            if (segWithIndex.matches()) {
                tagName = segWithIndex.group(1);
                index = Integer.parseInt(segWithIndex.group(2)) - 1;
            } else if (segWithoutIndex.matches()) {
                tagName = segWithoutIndex.group(1);
                index = 0;
            } else {
                return null; // Invalid segment, try fallback
            }

            Elements children = current.children();
            List<Element> matchingChildren = new ArrayList<>();
            for (Element child : children) {
                if (child.tagName().equalsIgnoreCase(tagName)) {
                    matchingChildren.add(child);
                }
            }

            if (index < matchingChildren.size()) {
                current = matchingChildren.get(index);
            } else {
                return null; // Out of bounds, try fallback
            }
        }

        return current;
    }

    private String resolveRangeCfiToStartPoint(String contentPath) {
        int firstComma = contentPath.indexOf(',');
        if (firstComma == -1) {
            return contentPath;
        }
        String prefix = contentPath.substring(0, firstComma);
        int secondComma = contentPath.indexOf(',', firstComma + 1);
        String startOffset = secondComma != -1
                ? contentPath.substring(firstComma + 1, secondComma)
                : contentPath.substring(firstComma + 1);
        return prefix + startOffset;
    }

    private CfiPathResult parseCfiPath(String contentPath) {
        List<CfiStep> steps = new ArrayList<>();
        Integer textOffset = null;

        Matcher stepMatcher = CFI_PATH_STEP_PATTERN.matcher(contentPath);
        while (stepMatcher.find()) {
            int stepIndex = Integer.parseInt(stepMatcher.group(1));
            String assertion = stepMatcher.group(2);
            String offsetStr = stepMatcher.group(3);

            if (offsetStr != null) {
                textOffset = Integer.parseInt(offsetStr);
            }

            steps.add(new CfiStep(stepIndex, assertion));
        }

        return new CfiPathResult(steps, textOffset);
    }

    private Element resolveElementFromCfiSteps(List<CfiStep> steps) {
        Element current = document.body();
        if (current == null) {
            return null;
        }

        log.debug("Starting CFI navigation from body, steps={}", steps);
        int successfulSteps = 0;

        for (int i = 0; i < steps.size(); i++) {
            CfiStep step = steps.get(i);
            int childIndex = (step.index() / 2) - 1;

            Elements children = current.children();
            log.debug("Step {}: index={}, assertion={}, childIndex={}, available children={}, current tag=<{}>",
                    i, step.index(), step.assertion(), childIndex, children.size(), current.tagName());

            if (childIndex < 0 || childIndex >= children.size()) {
                log.warn("CFI navigation failed at step {}/{}: index {} (childIndex={}) out of bounds, " +
                                "element <{}> has only {} children. Remaining steps: {}",
                        i, steps.size(), step.index(), childIndex, current.tagName(), children.size(),
                        steps.subList(i, steps.size()));

                // Try to find element by ID assertion if present in remaining steps
                Element fallbackElement = tryFindByIdAssertions(steps.subList(i, steps.size()));
                if (fallbackElement != null) {
                    log.info("Found fallback element by ID assertion: <{}> id='{}'",
                            fallbackElement.tagName(), fallbackElement.id());
                    return fallbackElement;
                }

                return current;
            }

            Element nextElement = children.get(childIndex);

            // Validate ID assertion if present
            if (step.assertion() != null && !step.assertion().isEmpty()) {
                String elementId = nextElement.id();
                if (!step.assertion().equals(elementId)) {
                    log.warn("CFI assertion mismatch at step {}: expected id='{}', found id='{}' on <{}>. " +
                            "Attempting to find element by ID.", i, step.assertion(), elementId, nextElement.tagName());

                    // Try to find element by ID instead
                    Element byId = document.getElementById(step.assertion());
                    if (byId != null) {
                        log.info("Found element by ID assertion '{}': <{}>", step.assertion(), byId.tagName());
                        nextElement = byId;
                    }
                }
            }

            current = nextElement;
            successfulSteps++;
        }

        log.debug("CFI navigation completed ({}/{} steps), resolved to <{}> id='{}'",
                successfulSteps, steps.size(), current.tagName(), current.id());
        return current;
    }

    /**
     * Try to find an element by ID from the remaining CFI steps' assertions.
     * This is a fallback when step-based navigation fails.
     */
    private Element tryFindByIdAssertions(List<CfiStep> remainingSteps) {
        // Try each remaining step's assertion to find an element by ID
        for (CfiStep step : remainingSteps) {
            if (step.assertion() != null && !step.assertion().isEmpty()) {
                Element element = document.getElementById(step.assertion());
                if (element != null) {
                    log.debug("Found element by ID '{}' from remaining assertions", step.assertion());
                    return element;
                }
            }
        }
        return null;
    }

    private String buildCfiPathFromElement(Element element) {
        List<String> pathParts = new ArrayList<>();
        Element current = element;

        while (current != null && !current.tagName().equalsIgnoreCase("body")) {
            Element parent = current.parent();
            if (parent == null) {
                break;
            }

            int siblingIndex = 0;
            for (Element sibling : parent.children()) {
                siblingIndex++;
                if (sibling == current) {
                    break;
                }
            }

            int cfiStep = siblingIndex * 2;
            pathParts.add(0, "/" + cfiStep);

            current = parent;
        }

        pathParts.add(0, "/4");

        return String.join("", pathParts);
    }

    private String buildFullCfi(String contentPath) {
        int spineStep = (spineItemIndex + 1) * 2;
        return String.format("epubcfi(/6/%d!%s)", spineStep, contentPath);
    }

    private String buildXPointerPath(Element targetElement) {
        List<String> pathParts = new ArrayList<>();
        Element current = targetElement;

        Element root = document.body() != null ? document.body().parent() : null;
        while (current != null && current != root) {
            Element parent = current.parent();
            if (parent == null) {
                break;
            }

            String tagName = current.tagName().toLowerCase();

            int siblingIndex = 0;
            int totalSameTagSiblings = 0;
            for (Element sibling : parent.children()) {
                if (sibling.tagName().equalsIgnoreCase(tagName)) {
                    if (sibling == current) {
                        siblingIndex = totalSameTagSiblings;
                    }
                    totalSameTagSiblings++;
                }
            }

            if (totalSameTagSiblings == 1) {
                pathParts.add(0, tagName);
            } else {
                pathParts.add(0, String.format("%s[%d]", tagName, siblingIndex + 1));
            }

            current = parent;
        }

        StringBuilder xpointer = new StringBuilder("/body/DocFragment[")
                .append(spineItemIndex + 1)
                .append("]");

        if (!pathParts.isEmpty() && pathParts.get(0).startsWith("body")) {
            pathParts.remove(0);
        }

        xpointer.append("/body");

        if (!pathParts.isEmpty()) {
            xpointer.append("/").append(String.join("/", pathParts));
        }

        return xpointer.toString();
    }

    private String handleTextOffset(Element element, int cfiOffset) {
        List<TextNode> textNodes = collectTextNodes(element);

        int totalChars = 0;
        TextNode targetTextNode = null;
        int offsetInNode = 0;

        for (TextNode textNode : textNodes) {
            String nodeText = textNode.text();
            int nodeLength = nodeText.length();

            if (totalChars + nodeLength >= cfiOffset) {
                targetTextNode = textNode;
                offsetInNode = cfiOffset - totalChars;
                break;
            }

            totalChars += nodeLength;
        }

        if (targetTextNode == null) {
            return buildXPointerPath(element);
        }

        Element textParent = (Element) targetTextNode.parent();
        while (textParent != null && !isSignificantElement(textParent)) {
            textParent = textParent.parent();
        }

        if (textParent == null) {
            textParent = element;
        }

        String basePath = buildXPointerPath(textParent);
        return basePath + "/text()." + offsetInNode;
    }

    private List<TextNode> collectTextNodes(Element element) {
        List<TextNode> textNodes = new ArrayList<>();
        collectTextNodesRecursive(element, textNodes);
        return textNodes;
    }

    private void collectTextNodesRecursive(Node node, List<TextNode> textNodes) {
        for (Node child : node.childNodes()) {
            if (child instanceof TextNode textNode) {
                String text = textNode.text();
                if (!text.isEmpty()) {
                    textNodes.add(textNode);
                }
            } else if (child instanceof Element) {
                collectTextNodesRecursive(child, textNodes);
            }
        }
    }

    private boolean isSignificantElement(Element element) {
        String tagName = element.tagName().toLowerCase();
        return !INLINE_ELEMENTS.contains(tagName);
    }

    @Getter
    public static class XPointerResult {
        private final String xpointer;
        private final String pos0;
        private final String pos1;

        public XPointerResult(String xpointer, String pos0, String pos1) {
            this.xpointer = xpointer;
            this.pos0 = pos0;
            this.pos1 = pos1;
        }

        public XPointerResult(String xpointer) {
            this(xpointer, null, null);
        }
    }

    private record XPointerParseResult(Element element, Integer textOffset) {
    }

    private record CfiStep(int index, String assertion) {
    }

    private record CfiPathResult(List<CfiStep> steps, Integer textOffset) {
    }
}

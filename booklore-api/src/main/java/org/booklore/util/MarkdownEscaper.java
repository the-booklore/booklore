package org.booklore.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class MarkdownEscaper {

    private static final char[] MARKDOWN_SPECIAL_CHARS = {
            '\\', '`', '*', '_', '{', '}', '[', ']', '(', ')', '#', '+', '!', '|', '<', '>'
    };

    public String escape(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        StringBuilder sb = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isMarkdownSpecial(c)) {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private boolean isMarkdownSpecial(char c) {
        for (char special : MARKDOWN_SPECIAL_CHARS) {
            if (c == special) {
                return true;
            }
        }
        return false;
    }
}

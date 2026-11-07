package io.github.carloscamacho.xroar;

import java.util.ArrayList;
import java.util.List;

/* Checks text that is about to be typed into the CoCo as a BASIC program.
 *
 * Color BASIC takes a line at a time, so each non-blank line has to start
 * with a line number (0-63999), have something after it, and be no longer than
 * 249 characters.  (Measured on a real Color BASIC 1.3 / Extended 1.1 ROM in
 * tools/hosttest: a longer line is not rejected, it is silently cut off at 249
 * characters, which would quietly corrupt a program.)  A character the CoCo's keyboard doesn't have (a tab, an
 * accented letter) is a warning: it is dropped when typed. */
final class BasicText {
    static final int MAX_LINE_LENGTH = 249;
    static final int MAX_LINE_NUMBER = 63999;

    static final class Problem {
        final int line;           // 1-based line in the text
        final boolean error;      // false = warning only
        final String message;

        Problem(int line, boolean error, String message) {
            this.line = line;
            this.error = error;
            this.message = message;
        }

        @Override
        public String toString() {
            return "Line " + line + ": " + message;
        }
    }

    private BasicText() {
    }

    /* Split into lines, whatever the file used for line ends (CR, LF, CRLF);
     * a UTF-8 byte order mark is dropped. */
    static List<String> lines(String text) {
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\r' || c == '\n') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        return out;
    }

    /* Every problem found.  asProgram: also require line numbers. */
    static List<Problem> check(String text, boolean asProgram) {
        List<Problem> out = new ArrayList<>();
        List<String> lines = lines(text);
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            int n = i + 1;
            if (l.trim().isEmpty()) {
                continue;
            }
            if (l.length() > MAX_LINE_LENGTH) {
                out.add(new Problem(n, true, "is " + l.length() + " characters long; the limit is " + MAX_LINE_LENGTH));
            }
            if (asProgram) {
                int p = 0;
                while (p < l.length() && (l.charAt(p) == ' ' || l.charAt(p) == '\t')) {
                    p++;
                }
                int digits = p;
                while (digits < l.length() && l.charAt(digits) >= '0' && l.charAt(digits) <= '9') {
                    digits++;
                }
                if (digits == p) {
                    out.add(new Problem(n, true, "doesn't start with a line number"));
                } else {
                    long num = digits - p > 6 ? Long.MAX_VALUE : Long.parseLong(l.substring(p, digits));
                    if (num > MAX_LINE_NUMBER) {
                        out.add(new Problem(n, true, "line number " + (digits - p > 6 ? "is too big" : num + " is over " + MAX_LINE_NUMBER)));
                    } else if (l.substring(digits).trim().isEmpty()) {
                        out.add(new Problem(n, true, "has a line number but nothing after it (that would delete the line)"));
                    }
                }
            }
            for (int k = 0; k < l.length(); k++) {
                char c = l.charAt(k);
                if (c < 0x20 || c > 0x7e) {
                    out.add(new Problem(n, false, "has a character the CoCo can't type (" + describe(c) + "); it will be left out"));
                    break;
                }
            }
        }
        return out;
    }

    private static String describe(char c) {
        if (c == '\t') {
            return "a tab";
        }
        return c < 0x20 ? String.format("control code %d", (int) c) : "'" + c + "'";
    }

    static boolean hasErrors(List<Problem> problems) {
        for (Problem p : problems) {
            if (p.error) {
                return true;
            }
        }
        return false;
    }

    /* The text to type: line ends normalised to \n, blank lines dropped, and
     * (skipBad) any line with an error left out.  Always ends with a newline
     * if there is anything. */
    static String forTyping(String text, boolean asProgram, boolean skipBad) {
        List<String> lines = lines(text);
        java.util.Set<Integer> bad = new java.util.HashSet<>();
        if (skipBad) {
            for (Problem p : check(text, asProgram)) {
                if (p.error) {
                    bad.add(p.line);
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            if (l.trim().isEmpty() || bad.contains(i + 1)) {
                continue;
            }
            sb.append(l).append('\n');
        }
        return sb.toString();
    }

    /* "Line 3: ...", "Line 9: ..." for up to max problems, then "and N more". */
    static String summary(List<Problem> problems, int max) {
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (Problem p : problems) {
            if (shown == max) {
                break;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(p.error ? "• " : "• (warning) ").append(p);
            shown++;
        }
        if (problems.size() > shown) {
            sb.append("\n… and ").append(problems.size() - shown).append(" more");
        }
        return sb.toString();
    }
}

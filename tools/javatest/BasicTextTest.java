package io.github.carloscamacho.xroar;

import java.util.List;

/* Desktop test of BasicText.  Usage: java -cp <classes> ...BasicTextTest */
public class BasicTextTest {
    static int failures;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + what);
        if (!ok) {
            failures++;
        }
    }

    static String rep(char c, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(c);
        return sb.toString();
    }

    public static void main(String[] a) {
        System.out.println("== a good program");
        String good = "10 PRINT \"HELLO\"\r\n20 GOTO 10\r\n";
        check(BasicText.check(good, true).isEmpty(), "no problems");
        check(BasicText.lines(good).size() == 2, "CRLF is two lines");
        check(BasicText.lines("10 A\r20 B\n30 C\r\n40 D").size() == 4, "CR, LF, CRLF and a last line with no end all count");
        check(BasicText.lines("﻿10 A").get(0).equals("10 A"), "a UTF-8 byte order mark is dropped");

        System.out.println("== missing line numbers");
        List<BasicText.Problem> p = BasicText.check("10 PRINT 1\nPRINT 2\n  30 PRINT 3\nREM\n", true);
        check(p.size() == 2 && p.get(0).line == 2 && p.get(1).line == 4, "lines 2 and 4 flagged: " + p);
        check(BasicText.check("PRINT 2\n", false).isEmpty(), "line numbers are not required when it isn't a program");

        System.out.println("== line number range and empty lines");
        check(BasicText.check("63999 END\n", true).isEmpty(), "63999 is allowed");
        check(BasicText.check("64000 END\n", true).size() == 1, "64000 is too big");
        check(BasicText.check("1234567890 END\n", true).size() == 1, "a huge number is flagged, not crashed on");
        check(BasicText.check("0 REM\n", true).isEmpty(), "line 0 is allowed");
        check(BasicText.check("10\n", true).size() == 1, "a bare line number is flagged");
        check(BasicText.check("10 PRINT\n\n   \n20 END\n", true).isEmpty(), "blank lines are fine");

        System.out.println("== 249 characters (what a real CoCo keeps)");
        String l249 = "10 REM " + rep('X', 249 - 7);
        check(l249.length() == 249 && BasicText.check(l249, true).isEmpty(), "249 characters is allowed");
        check(BasicText.check(l249 + "X", true).size() == 1, "250 characters is flagged");
        check(BasicText.check(l249 + rep('X', 6), true).get(0).message.contains("249"), "the message names the limit");
        check(BasicText.check(rep('X', 300), false).size() == 1, "the limit applies even when it isn't a program");

        System.out.println("== characters the CoCo can't type");
        List<BasicText.Problem> w = BasicText.check("10 PRINT \"café\"\n20 PRINT\t1\n", true);
        check(w.size() == 2 && !w.get(0).error && !w.get(1).error && !BasicText.hasErrors(w), "accented letter and tab are warnings, not errors: " + w);

        System.out.println("== what gets typed");
        String t = BasicText.forTyping("10 A\r\n\r\nPRINT\r\n20 B", true, false);
        check(t.equals("10 A\nPRINT\n20 B\n"), "line ends become \\n, blank lines go, bad lines stay: " + t.replace("\n", "|"));
        t = BasicText.forTyping("10 A\r\n\r\nPRINT\r\n20 B", true, true);
        check(t.equals("10 A\n20 B\n"), "with skip, the line with no number is left out: " + t.replace("\n", "|"));
        check(BasicText.forTyping("  \n\n", true, false).isEmpty(), "nothing to type from blank text");

        System.out.println("== summary");
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 9; i++) many.append("PRINT ").append(i).append('\n');
        String s = BasicText.summary(BasicText.check(many.toString(), true), 5);
        check(s.split("\n").length == 6 && s.endsWith("and 4 more"), "five shown, then 'and 4 more'");

        System.out.println(failures == 0 ? "ALL PASSED" : failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}

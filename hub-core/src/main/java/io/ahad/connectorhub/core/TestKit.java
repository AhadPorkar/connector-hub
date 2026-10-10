package io.ahad.connectorhub.core;
import java.nio.file.*;
import java.util.*;
/** Tiny offline test runner. Emits standard JUnit XML; no external dependencies or invented assertions. */
public final class TestKit {
    private final String suite;
    private final List<String> cases=new ArrayList<>();
    private int failures;
    @FunctionalInterface public interface Check {
        void run() throws Exception;
    }
    public TestKit(String suite) {
        this.suite=suite;
    }
    public void test(String name,Check check) {
        long start=System.nanoTime();
        String failure="";
        try {
            check.run();
            System.out.println("PASS "+suite+" :: "+name);
        }
        catch(Throwable e) {
            failures++;
            failure="<failure message=\""+esc(e.toString())+"\">"+esc(stack(e))+"</failure>";
            System.err.println("FAIL "+suite+" :: "+name+" :: "+e);
            e.printStackTrace();
        }
        cases.add("<testcase classname=\""+esc(suite)+"\" name=\""+esc(name)+"\" time=\""+((System.nanoTime()-start)/1e9)+"\">"+failure+"</testcase>");
    }
    public static void eq(Object expected,Object actual) {
        if(!Objects.equals(expected,actual))throw new AssertionError("Expected "+expected+", got "+actual);
    }
    public static void yes(boolean value) {
        if(!value)throw new AssertionError("Expected true");
    }
    public static void error(String code,Check check) {
        try {
            check.run();
        }
        catch(HubException e) {
            eq(code,e.code());
            return;
        }
        catch(Exception e) {
            throw new AssertionError(e);
        }
        throw new AssertionError("Expected "+code);
    }
    public void finish(String report) throws Exception {
        Path p=Path.of(report);
        Files.createDirectories(p.getParent());
        Files.writeString(p,"<?xml version=\"1.0\" encoding=\"UTF-8\"?><testsuite name=\""+suite+"\" tests=\""+cases.size()+"\" failures=\""+failures+"\" errors=\"0\">"+String.join("",cases)+"</testsuite>");
        System.out.println(suite+": "+cases.size()+" tests, "+failures+" failures");
        if(failures>0)throw new AssertionError("Test suite failed");
    }
    private static String esc(String s) {
        return Parameters.xmlEscape(s);
    }
    private static String stack(Throwable e) {
        var sw=new java.io.StringWriter();
        e.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }
}

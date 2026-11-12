package com.lifelink119.verify;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.util.Xml;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.io.StringWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.xmlpull.v1.XmlSerializer;

/** Reads the visible accessibility tree without waiting for animations to finish.
 * Targets its own test package, so starting it does not restart the app under test.
 * No activities, network access, vehicle access, or persistent data.
 */
public final class SnapshotInstrumentation extends Instrumentation {
    private Bundle arguments;
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); this.arguments=arguments; start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            UiAutomation automation = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            AccessibilityServiceInfo info = automation.getServiceInfo();
            info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            automation.setServiceInfo(info);
            if (arguments != null && "true".equals(arguments.getString("persistent"))) {
                serve(automation);
                result.putString("stopped", "true");
            } else {
                result=read(automation,arguments == null ? null : arguments.getString("click"));
            }
            finish(Activity.RESULT_OK,result);
        } catch (Exception error) {
            result.putString("error", error.toString());
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    // ADB-forwarded abstract Unix socket: no network permission, activity or
    // persistent service. Keeps UiAutomation alive during one bounded test run.
    private void serve(UiAutomation automation) throws Exception {
        final LocalServerSocket server=new LocalServerSocket("lifelink-ui-verify");
        Thread expiry=new Thread(() -> {
            try { Thread.sleep(30*60*1000); server.close(); } catch (Exception ignored) { }
        },"snapshot-expiry");
        expiry.setDaemon(true);expiry.start();
        try {
            while (true) {
                try (LocalSocket client=server.accept()) {
                    client.setSoTimeout(10000);
                    BufferedReader reader=new BufferedReader(new InputStreamReader(client.getInputStream(),StandardCharsets.UTF_8));
                    PrintWriter writer=new PrintWriter(new OutputStreamWriter(client.getOutputStream(),StandardCharsets.UTF_8),true);
                    String command=reader.readLine();
                    if ("STOP".equals(command)) { writer.println("{\"stopped\":true}");break; }
                    if ("PING".equals(command)) { writer.println("{\"ready\":true}");continue; }
                    JSONObject response=new JSONObject();
                    try {
                        String click=null;
                        if (command!=null && (command.startsWith("CLICK com.lifelink119.app:id/") || command.equals("CLICK android:id/button1") || command.equals("CLICK android:id/button2")))click=command.substring(6);
                        else if (command!=null && command.startsWith("GET com.lifelink119.app:id/"))click=command;
                        else if (command!=null && command.matches("SET_TEXT com\\.lifelink119\\.app:id/x_glucose [0-9]{1,3}"))click=command;
                        else if (command!=null && command.matches("SET_TEXT com\\.lifelink119\\.app:id/privacy_(pin|confirm) [0-9]{6,12}"))click=command;
                        else if (command!=null && command.matches("SET_TEXT com\\.lifelink119\\.app:id/x_my_health_(name|conditions|allergies|medications) QA[A-Z0-9_-]{1,64}"))click=command;
                        else if (command!=null && command.equals("SET_TEXT com.lifelink119.app:id/x_health_phone 00000000000"))click=command;
                        else if (command!=null && command.matches("SET_PROGRESS com\\.lifelink119\\.app:id/x_ev_(capacity|soc|reserve|efficiency|load) [0-9]{1,3}"))click=command;
                        else if ("SCROLL_FORWARD".equals(command) || "SCROLL_BACKWARD".equals(command))click=command;
                        else if (!"DUMP".equals(command))throw new IllegalArgumentException("Unknown test command");
                        Bundle value=read(automation,click);
                        for (String key:value.keySet())response.put(key,value.getString(key));
                    } catch (Exception error) { response.put("error",error.toString()); }
                    writer.println(response.toString());
                }
            }
        } finally { expiry.interrupt();server.close(); }
    }

    private Bundle read(UiAutomation automation,String click) throws Exception {
            Bundle result=new Bundle();
            if (android.os.Build.VERSION.SDK_INT>=33) automation.clearCache();
            AccessibilityNodeInfo root = null;
            for (int i = 0; i < 10 && root == null; i++) {
                AccessibilityNodeInfo active=automation.getRootInActiveWindow();
                if (active != null && "com.lifelink119.app".equals(clean(active.getPackageName()))) {
                    root=active;
                    break;
                }
                // Pleos has several simultaneously resumed windows. Select the
                // tested app's visible window rather than a transient home root.
                for (AccessibilityWindowInfo window : allWindows(automation)) {
                    AccessibilityNodeInfo candidate = window.getRoot();
                    if (candidate != null && "com.lifelink119.app".equals(clean(candidate.getPackageName()))) {
                        root = candidate;
                        break;
                    }
                }
                if (root == null) SystemClock.sleep(100);
            }
            if (root == null) throw new IllegalStateException("No active accessibility root");
            Rect viewport=new Rect();root.getBoundsInScreen(viewport);
            if (click != null && (click.startsWith("SET_TEXT ") || click.startsWith("SET_PROGRESS "))) {
                String[] parts=click.split(" ");boolean changed=false;
                for (AccessibilityNodeInfo control:root.findAccessibilityNodeInfosByViewId(parts[1])) {
                    if (!inViewport(control,viewport) || !control.isEnabled())continue;
                    Bundle argument=new Bundle();
                    if (click.startsWith("SET_TEXT ")) {
                        argument.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,parts[2]);
                        changed=control.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,argument);
                    } else {
                        argument.putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,Float.parseFloat(parts[2]));
                        changed=control.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(),argument);
                    }
                    if(changed)break;
                }
                result.putString("changed",Boolean.toString(changed));return result;
            }
            if (click != null && click.startsWith("GET com.lifelink119.app:id/")) {
                java.util.List<AccessibilityNodeInfo> matches=root.findAccessibilityNodeInfosByViewId(click.substring(4));
                if (matches.isEmpty()) { result.putString("hierarchy","<hierarchy/>");return result; }
                root=matches.get(0);click=null;
            }
            if ("SCROLL_FORWARD".equals(click) || "SCROLL_BACKWARD".equals(click)) {
                boolean scrolled=false;
                int action="SCROLL_FORWARD".equals(click) ? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD : AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD;
                java.util.List<AccessibilityNodeInfo> scrolls=root.findAccessibilityNodeInfosByViewId("com.lifelink119.app:id/content_scroll");
                scrolls.addAll(root.findAccessibilityNodeInfosByViewId("com.lifelink119.app:id/x_scroll"));
                for (AccessibilityNodeInfo control : scrolls) {
                    if (inViewport(control,viewport) && control.isScrollable()) {
                        scrolled=control.performAction(action);
                        if (scrolled) break;
                    }
                }
                result.putString("scrolled", Boolean.toString(scrolled));
                return result;
            }
            if (click != null) {
                boolean clicked=false;
                for (AccessibilityNodeInfo control : root.findAccessibilityNodeInfosByViewId(click)) {
                    if (inViewport(control,viewport) && control.isEnabled() && control.isClickable()) {
                        clicked=control.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        if (clicked) break;
                    }
                }
                result.putString("clicked", Boolean.toString(clicked));
                return result;
            }
            StringWriter output = new StringWriter();
            XmlSerializer xml = Xml.newSerializer();
            xml.setOutput(output);
            xml.startDocument("UTF-8", true);
            xml.startTag(null, "hierarchy");
            writeNode(xml, root, 0, viewport);
            xml.endTag(null, "hierarchy");
            xml.endDocument();
            result.putString("hierarchy", output.toString());
            return result;
    }

    private java.util.List<AccessibilityWindowInfo> allWindows(UiAutomation automation) {
        java.util.List<AccessibilityWindowInfo> result=new java.util.ArrayList<>(automation.getWindows());
        if (android.os.Build.VERSION.SDK_INT>=30) {
            android.util.SparseArray<java.util.List<AccessibilityWindowInfo>> displays=automation.getWindowsOnAllDisplays();
            for (int i=0;i<displays.size();i++)result.addAll(displays.valueAt(i));
        }
        return result;
    }

    private static String clean(CharSequence text) {
        return text == null ? "" : text.toString().replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
    }

    private static boolean inViewport(AccessibilityNodeInfo node,Rect viewport) {
        Rect bounds=new Rect();node.getBoundsInScreen(bounds);
        return bounds.intersect(viewport) && !bounds.isEmpty();
    }

    private static void writeNode(XmlSerializer xml, AccessibilityNodeInfo node, int depth, Rect viewport) throws Exception {
        if (depth > 100) throw new IllegalStateException("Accessibility tree too deep");
        Rect box = new Rect();
        node.getBoundsInScreen(box);
        if (!box.intersect(viewport) || box.isEmpty())return;
        xml.startTag(null, "node");
        xml.attribute(null, "resource-id", clean(node.getViewIdResourceName()));
        xml.attribute(null, "class", clean(node.getClassName()));
        xml.attribute(null, "package", clean(node.getPackageName()));
        xml.attribute(null, "text", node.isPassword() ? "[PROTECTED]" : clean(node.getText()));
        xml.attribute(null, "content-desc", clean(node.getContentDescription()));
        xml.attribute(null, "bounds", "["+box.left+","+box.top+"]["+box.right+","+box.bottom+"]");
        xml.attribute(null, "enabled", Boolean.toString(node.isEnabled()));
        xml.attribute(null, "checked", Boolean.toString(node.isChecked()));
        xml.attribute(null, "clickable", Boolean.toString(node.isClickable()));
        xml.attribute(null, "provider-visible", Boolean.toString(node.isVisibleToUser()));
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) writeNode(xml, child, depth+1, box);
        }
        xml.endTag(null, "node");
    }
}

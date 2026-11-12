package com.lifelink119.nativeverify;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import android.util.Xml;
import org.xmlpull.v1.XmlSerializer;

/** External recording harness: real installed APK, public windows only. */
public final class WindowInstrumentation extends Instrumentation {
  private volatile Activity activity;
  private String backend="native_view_draw";
  // AAOS can recreate the Activity when its application panel is resized.
  // Follow the actual resumed Activity rather than drawing a detached old View.
  @Override public void callActivityOnResume(Activity current){
    super.callActivityOnResume(current);
    if("com.lifelink119.app".equals(current.getPackageName()))activity=current;
  }
  @Override public void onCreate(Bundle b){super.onCreate(b);start();}
  @Override public void onStart(){
    try {
      Intent launch=getTargetContext().getPackageManager().getLaunchIntentForPackage("com.lifelink119.app");
      launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      activity=startActivitySync(launch);SystemClock.sleep(1200);
      LocalServerSocket server=new LocalServerSocket("lifelink-native-verify");
      while(true){
        try(LocalSocket socket=server.accept()){
          String command=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8)).readLine();
          if("FRAME".equals(command)){
            DataOutputStream out=new DataOutputStream(socket.getOutputStream());
            if(secure()){out.writeInt(-1);continue;}
            Bitmap image=null;
            // Native View drawing is an explicit fallback when the host compositor
            // cannot return a usable public window. This uses the real running
            // Activity's Views; no interface or state is re-created in the harness.
            if(image==null || isBlank(image)){
              backend="native_view_draw";final Bitmap[] value=new Bitmap[1];
              runOnMainSync(()->{
                if(secureOnMain())return;
                View root=activity.getWindow().getDecorView();
                Bitmap b=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);
                root.draw(new Canvas(b));value[0]=b;
              });image=value[0];
            }
            if(image==null || secure()){if(image!=null)image.recycle();out.writeInt(-1);continue;}
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();image.compress(Bitmap.CompressFormat.JPEG,94,bytes);image.recycle();
            byte[] data=bytes.toByteArray();out.writeInt(data.length);out.write(data);out.flush();continue;
          }
          PrintWriter out=new PrintWriter(new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.UTF_8),true);
          if("STOP".equals(command)){out.println("{\"stopped\":true}");break;}
          JSONObject result=new JSONObject();
          try {
            if("PING".equals(command)){result.put("ready",true);result.put("backend",backend);result.put("secureRefused",secure());}
            else if(command.startsWith("CLICK ")){
              final boolean[] clicked={false};runOnMainSync(()->{View v=find(command.substring(6));if(v!=null && v.isEnabled() && visible(v)){boolean before=v instanceof CompoundButton && ((CompoundButton)v).isChecked();clicked[0]=v.performClick();if(v instanceof CompoundButton)clicked[0]=clicked[0] || before!=((CompoundButton)v).isChecked();}});result.put("clicked",Boolean.toString(clicked[0]));
            }else if(command.startsWith("FOCUS ")){
              final boolean[] focused={false};runOnMainSync(()->{View v=find(command.substring(6));if(!secureOnMain() && v!=null && v.isEnabled() && visible(v))focused[0]=v.requestFocusFromTouch();});result.put("focused",focused[0]);
            }else if("CANCEL_AUTH".equals(command)){
              result.put("cancelled",cancelOwnAuthentication());
            }else if(command.startsWith("GET ") || "DUMP".equals(command)){
              final String[] xml={"<hierarchy/>"};runOnMainSync(()->{try{View v="DUMP".equals(command)?activity.getWindow().getDecorView():find(command.substring(4));xml[0]=dump(v);}catch(Exception e){throw new RuntimeException(e);}});result.put("hierarchy",xml[0]);
            }else if(command.startsWith("SCROLL_")){
              final boolean[] changed={false};runOnMainSync(()->{View v=find("x_scroll");if(v==null)v=find("content_scroll");if(v instanceof ScrollView){ScrollView s=(ScrollView)v;int before=s.getScrollY();s.scrollBy(0,(command.equals("SCROLL_FORWARD")?1:-1)*Math.max(200,s.getHeight()*2/3));changed[0]=before!=s.getScrollY();}});result.put("scrolled",Boolean.toString(changed[0]));
            }else throw new IllegalArgumentException("Unsupported recording command");
          }catch(Exception e){result.put("error",e.toString());}
          out.println(result.toString());
        }catch(java.io.IOException e){System.err.println("Capture connection closed: "+e);}
      }
      server.close();finish(Activity.RESULT_OK,new Bundle());
    }catch(Exception e){Bundle b=new Bundle();b.putString("error",e.toString());finish(Activity.RESULT_CANCELED,b);}
  }
  private View find(String key){int id=getTargetContext().getResources().getIdentifier(key,"id","com.lifelink119.app");return activity.findViewById(id);}
  /** Only click our own standard Cancel button. Never read fields or submit credentials. */
  private boolean cancelOwnAuthentication(){
    android.app.UiAutomation automation=getUiAutomation();
    android.accessibilityservice.AccessibilityServiceInfo info=automation.getServiceInfo();
    info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS|android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
    automation.setServiceInfo(info);
    for(android.view.accessibility.AccessibilityWindowInfo window:automation.getWindows()){
      android.view.accessibility.AccessibilityNodeInfo root=window.getRoot();
      if(root==null || !"com.lifelink119.app".contentEquals(root.getPackageName()))continue;
      for(android.view.accessibility.AccessibilityNodeInfo button:root.findAccessibilityNodeInfosByViewId("android:id/button2")){
        CharSequence label=button.getText();
        if(button.isEnabled() && button.isVisibleToUser() && ("취소".contentEquals(label) || "Cancel".contentEquals(label)))
          return button.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);
      }
    }
    return false;
  }
  private boolean secureOnMain(){return activity==null || activity.isDestroyed() || activity.isFinishing() || (activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE)!=0;}
  private boolean secure(){final boolean[] b={true};runOnMainSync(()->b[0]=secureOnMain());return b[0];}
  private boolean visible(View v){Rect r=new Rect();return v.getGlobalVisibleRect(r) && r.width()>0 && r.height()>0;}
  private boolean isBlank(Bitmap b){
    Bitmap a=b.copy(Bitmap.Config.ARGB_8888,false);if(a==null)return true;
    int dark=0,light=0,total=0;for(int y=0;y<a.getHeight();y+=25)for(int x=0;x<a.getWidth();x+=25){int c=a.getPixel(x,y);int l=(Color.red(c)+Color.green(c)+Color.blue(c))/3;if(l<40)dark++;if(l>235)light++;total++;}a.recycle();return dark>total*.98 || light>total*.98;
  }
  private String dump(View v)throws Exception{
    StringWriter output=new StringWriter();XmlSerializer xml=Xml.newSerializer();xml.setOutput(output);xml.startTag(null,"hierarchy");if(v!=null)write(xml,v);xml.endTag(null,"hierarchy");xml.flush();return output.toString();
  }
  private void write(XmlSerializer xml,View v)throws Exception{
    if(v.getVisibility()!=View.VISIBLE || !visible(v))return;
    int[] p=new int[2],origin=new int[2];v.getLocationOnScreen(p);activity.getWindow().getDecorView().getLocationOnScreen(origin);
    String id="";if(v.getId()!=View.NO_ID)try{id=v.getResources().getResourceName(v.getId());}catch(Exception ignored){}
    String text=v instanceof TextView?((TextView)v).getText().toString():"";
    if(v instanceof EditText)text="[PROTECTED]";
    xml.startTag(null,"node");xml.attribute(null,"resource-id",id);xml.attribute(null,"text",text);xml.attribute(null,"class",v.getClass().getName());xml.attribute(null,"enabled",Boolean.toString(v.isEnabled()));xml.attribute(null,"clickable",Boolean.toString(v.isClickable()));
    xml.attribute(null,"checked",Boolean.toString(v instanceof CompoundButton && ((CompoundButton)v).isChecked()));
    xml.attribute(null,"focused",Boolean.toString(v.isFocused()));
    if(v instanceof TextView){
      TextView t=(TextView)v;
      xml.attribute(null,"gravity",Integer.toString(t.getGravity()));
      xml.attribute(null,"text-size-px",Float.toString(t.getTextSize()));
      xml.attribute(null,"text-color",Integer.toHexString(t.getCurrentTextColor()));
      xml.attribute(null,"typeface-weight",Integer.toString(t.getTypeface()==null?400:t.getTypeface().getWeight()));
      if(t.getBackground()!=null && t.getBackground().getCurrent() instanceof android.graphics.drawable.GradientDrawable){
        android.content.res.ColorStateList colors=((android.graphics.drawable.GradientDrawable)t.getBackground().getCurrent()).getColor();
        if(colors!=null)xml.attribute(null,"background-color",Integer.toHexString(colors.getDefaultColor()));
      }
      android.text.Layout layout=t.getLayout();
      int ellipses=0;boolean horizontalClipped=false;
      if(layout!=null)for(int i=0;i<layout.getLineCount();i++){
        ellipses+=layout.getEllipsisCount(i);
        horizontalClipped|=layout.getLineMax(i)>t.getWidth()-t.getCompoundPaddingLeft()-t.getCompoundPaddingRight()+1;
      }
      xml.attribute(null,"ellipsis-count",Integer.toString(ellipses));
      xml.attribute(null,"text-clipped",Boolean.toString(layout!=null && (horizontalClipped || layout.getHeight()>t.getHeight()-t.getCompoundPaddingTop()-t.getCompoundPaddingBottom()+1)));
    }
    xml.attribute(null,"bounds","["+(p[0]-origin[0])+","+(p[1]-origin[1])+"]["+(p[0]-origin[0]+v.getWidth())+","+(p[1]-origin[1]+v.getHeight())+"]");
    if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++)write(xml,group.getChildAt(i));}xml.endTag(null,"node");
  }
}

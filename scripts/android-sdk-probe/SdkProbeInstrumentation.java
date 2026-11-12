package com.lifelink119.sdkprobe;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.content.pm.PackageManager;
import com.lifelink119.app.core.VehicleDiagnostic;
import com.lifelink119.app.core.VehicleMetric;
import com.lifelink119.app.vehicle.PleosVehicleGateway;
import kotlin.Unit;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Debug test APK only. Reads the installed application's real gateway on Pleos AVD.
 * No fixture branch, Activity UI bypass, vehicle writes, calls or medical access.
 */
public final class SdkProbeInstrumentation extends Instrumentation {
    private String label;
    private int warmupSeconds=90;
    private boolean includeAirbag=false;
    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        label=args==null?"snapshot":args.getString("label","snapshot");
        if(!label.matches("[A-Za-z0-9_-]{1,48}"))throw new IllegalArgumentException("Invalid label");
        if(args!=null)warmupSeconds=Integer.parseInt(args.getString("warmupSeconds","90"));
        if(warmupSeconds<0 || warmupSeconds>180)throw new IllegalArgumentException("Invalid warm-up budget");
        if(args!=null)includeAirbag=Boolean.parseBoolean(args.getString("includeAirbag","false"));
        start();
    }
    @Override public void onStart() {
        Bundle output=new Bundle();PleosVehicleGateway gateway=null;
        try {
            if(!"ranchu".equals(Build.HARDWARE) || !"crp_avd".equals(Build.PRODUCT) ||
               !getTargetContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE))
                throw new IllegalStateException("Dedicated Pleos emulator required; physical devices refused");
            JSONObject report=new JSONObject();report.put("label",label);report.put("source","Installed application PleosVehicleGateway / Vehicle SDK 2.3.2");
            report.put("hardware",Build.HARDWARE);report.put("product",Build.PRODUCT);report.put("fingerprint",Build.FINGERPRINT);
            report.put("appVersion",getTargetContext().getPackageManager().getPackageInfo("com.lifelink119.app",0).versionName);
            Intent launch=getTargetContext().getPackageManager().getLaunchIntentForPackage("com.lifelink119.app");
            if(launch==null)throw new IllegalStateException("Target launch Activity missing");
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivitySync(launch);
            report.put("foregroundActivity","com.lifelink119.app.MainActivity");
            report.put("airbagRequested",includeAirbag);
            report.put("api",Build.VERSION.SDK_INT);report.put("elapsedStartMs",SystemClock.elapsedRealtime());
            gateway=new PleosVehicleGateway(getTargetContext());JSONArray values=new JSONArray();JSONArray attempts=new JSONArray();
            long deadline=SystemClock.elapsedRealtime()+warmupSeconds*1000L;boolean readable=false;
            do {
              values=new JSONArray();boolean doorRead=false,socRead=false;
              for(VehicleMetric metric:VehicleMetric.values()) {
                if(metric==VehicleMetric.DRIVER_AIRBAG && !includeAirbag)continue;
                CountDownLatch done=new CountDownLatch(1);AtomicReference<VehicleDiagnostic> result=new AtomicReference<>();
                gateway.readObservation(metric,reading->{result.set(reading);done.countDown();return Unit.INSTANCE;});
                if(!done.await(18,TimeUnit.SECONDS))throw new IllegalStateException("Gateway callback missing: "+metric.name());
                VehicleDiagnostic reading=result.get();JSONObject value=new JSONObject();value.put("metric",metric.name());
                value.put("status",reading.getStatus().name());value.put("rawValue",reading.getRawValue()==null?JSONObject.NULL:reading.getRawValue());
                value.put("minimum",reading.getMinimum()==null?JSONObject.NULL:reading.getMinimum());
                value.put("maximum",reading.getMaximum()==null?JSONObject.NULL:reading.getMaximum());
                value.put("diagnostic",reading.getMessage());
                value.put("receivedElapsedMs",SystemClock.elapsedRealtime());values.put(value);
                boolean read="READ".equals(reading.getStatus().name()) && reading.getRawValue()!=null && Double.isFinite(reading.getRawValue());
                if(metric==VehicleMetric.DRIVER_DOOR)doorRead=read;
                if(metric==VehicleMetric.BATTERY_SOC)socRead=read;
              }
              readable=doorRead && socRead;attempts.put(values);
              Bundle progress=new Bundle();progress.putString("stream","SDK attempt "+attempts.length()+": "+values.toString()+"\n");sendStatus(0,progress);
              if(readable || SystemClock.elapsedRealtime()>=deadline)break;
              SystemClock.sleep(3000);
            } while(true);
            report.put("attempts",attempts);report.put("sdkReadable",readable);
            report.put("values",values);report.put("elapsedEndMs",SystemClock.elapsedRealtime());
            output.putString("sdkProbe",report.toString());finish(Activity.RESULT_OK,output);
        } catch(Exception e) {
            output.putString("error",e.getClass().getSimpleName()+": "+e.getMessage());finish(Activity.RESULT_CANCELED,output);
        } finally {if(gateway!=null)gateway.release();}
    }
}

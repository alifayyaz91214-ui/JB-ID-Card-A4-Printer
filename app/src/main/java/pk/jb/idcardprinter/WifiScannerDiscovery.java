package pk.jb.idcardprinter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.widget.*;
import java.util.*;

public final class WifiScannerDiscovery {
  private WifiScannerDiscovery(){}
  public static void show(Activity activity){
    NsdManager nsd=(NsdManager)activity.getSystemService(Context.NSD_SERVICE);
    if(nsd==null){new AlertDialog.Builder(activity).setMessage("Network discovery is unavailable on this phone.").setPositiveButton("OK",null).show();return;}
    LinearLayout layout=new LinearLayout(activity);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(24,16,24,12);
    TextView info=new TextView(activity);info.setText("Searching this Wi-Fi for scanner services…");info.setTextSize(16);layout.addView(info);
    LinearLayout found=new LinearLayout(activity);found.setOrientation(LinearLayout.VERTICAL);layout.addView(found);
    AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Choose Wi-Fi Scanner").setView(layout).setPositiveButton("Close",(d,w)->{}).create();
    Map<String,NsdServiceInfo> devices=new LinkedHashMap<>();
    ArrayList<NsdManager.DiscoveryListener> listeners=new ArrayList<>();
    Handler handler=new Handler(Looper.getMainLooper());
    final boolean[] stopped={false};
    Runnable stop=()->{if(stopped[0])return;stopped[0]=true;for(NsdManager.DiscoveryListener listener:listeners)try{nsd.stopServiceDiscovery(listener);}catch(Exception ignored){}};
    for(String type:new String[]{"_uscan._tcp.","_scanner._tcp."}){
      NsdManager.DiscoveryListener listener=new NsdManager.DiscoveryListener(){
        public void onDiscoveryStarted(String serviceType){}
        public void onStartDiscoveryFailed(String serviceType,int error){handler.post(()->info.setText("Scanner discovery unavailable ("+error+")."));}
        public void onStopDiscoveryFailed(String serviceType,int error){}
        public void onDiscoveryStopped(String serviceType){}
        public void onServiceLost(NsdServiceInfo service){}
        public void onServiceFound(NsdServiceInfo service){
          handler.post(()->{
            String key=service.getServiceName()+"|"+service.getServiceType();
            if(devices.containsKey(key))return;
            devices.put(key,service);
            info.setText("Found "+devices.size()+" scanner service(s). Select one to see connection details.");
            Button button=new Button(activity);button.setAllCaps(false);button.setText(service.getServiceName()+"  ("+service.getServiceType()+")");found.addView(button);
            button.setOnClickListener(v->{
              new AlertDialog.Builder(activity).setTitle(service.getServiceName())
                .setMessage("Scanner service found on your Wi-Fi. Direct scan commands are not yet implemented for this device. This test confirms discovery only.")
                .setPositiveButton("OK",null).show();
            });
          });
        }
      };
      listeners.add(listener);
      try{nsd.discoverServices(type,NsdManager.PROTOCOL_DNS_SD,listener);}catch(Exception ignored){}
    }
    dialog.setOnDismissListener(d->stop.run());
    dialog.show();
    handler.postDelayed(()->{stop.run();if(devices.isEmpty()&&dialog.isShowing())info.setText("No compatible scanner service discovered. Some printers do not advertise scanner services. This does not mean they cannot scan.");},10000);
  }
}

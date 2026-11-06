package pk.jb.idcardprinter;

import android.app.Activity;
import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.*;

@SuppressWarnings("deprecation")
public class CardCameraActivity extends Activity implements SurfaceHolder.Callback {
  Camera camera;
  SurfaceView surface;
  FrameLayout frame;
  TextView guidance;
  boolean taking=false;
  int facing=0;
  @Override public void onCreate(Bundle b){
    super.onCreate(b);
    frame=new FrameLayout(this);frame.setBackgroundColor(Color.BLACK);setContentView(frame);
    surface=new SurfaceView(this);frame.addView(surface,new FrameLayout.LayoutParams(-1,-1));surface.getHolder().addCallback(this);
    View overlay=new View(this){
      Paint p=new Paint(3);
      @Override protected void onDraw(Canvas c){
        float w=getWidth(),h=getHeight(),cw=w*.88f,ch=cw*54f/85.6f;
        if(ch>h*.55f){ch=h*.55f;cw=ch*85.6f/54f;}
        float l=(w-cw)/2,t=(h-ch)/2,r=l+cw,b=t+ch;
        p.setColor(0x99000000);c.drawRect(0,0,w,t,p);c.drawRect(0,b,w,h,p);c.drawRect(0,t,l,b,p);c.drawRect(r,t,w,b,p);
        p.setColor(0xff32e6a1);p.setStrokeWidth(5);p.setStyle(Paint.Style.STROKE);c.drawRoundRect(l,t,r,b,14,14,p);
        p.setStrokeWidth(2);p.setColor(0x99ffffff);c.drawLine(l+cw/3,t,l+cw/3,b,p);c.drawLine(l+2*cw/3,t,l+2*cw/3,b,p);c.drawLine(l,t+ch/3,r,t+ch/3,p);c.drawLine(l,t+2*ch/3,r,t+2*ch/3,p);p.setStyle(Paint.Style.FILL);
      }
    };frame.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
    LinearLayout controls=new LinearLayout(this);controls.setOrientation(1);controls.setPadding(18,12,18,26);controls.setBackgroundColor(0x99000000);
    FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);frame.addView(controls,cp);
    guidance=new TextView(this);guidance.setTextColor(Color.WHITE);guidance.setTextSize(16);guidance.setGravity(Gravity.CENTER);guidance.setText("Keep the full ID card inside the green frame. Hold steady and avoid glare. Perspective correction runs after capture.");controls.addView(guidance);
    Button shoot=new Button(this);shoot.setText("CAPTURE ID CARD");controls.addView(shoot);shoot.setOnClickListener(v->capture());
    Button cancel=new Button(this);cancel.setText("CANCEL");controls.addView(cancel);cancel.setOnClickListener(v->finish());
    if(android.os.Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.CAMERA},50);
  }
  @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==50){if(results.length==0||results[0]!=PackageManager.PERMISSION_GRANTED){guidance.setText("Camera permission required.");}else startCamera();}}
  @Override public void surfaceCreated(SurfaceHolder holder){startCamera();}
  @Override public void surfaceChanged(SurfaceHolder holder,int format,int width,int height){if(camera!=null){try{camera.stopPreview();camera.setPreviewDisplay(holder);camera.startPreview();}catch(Exception e){guidance.setText("Camera preview unavailable.");}}}
  @Override public void surfaceDestroyed(SurfaceHolder holder){stopCamera();}
  void startCamera(){
    if(camera!=null||surface==null||!surface.getHolder().getSurface().isValid())return;
    if(android.os.Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)return;
    try{
      camera=Camera.open();Camera.Parameters params=camera.getParameters();
      if(params.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)){params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);camera.setParameters(params);}
      camera.setDisplayOrientation(90);camera.setPreviewDisplay(surface.getHolder());camera.startPreview();
    }catch(Exception e){stopCamera();guidance.setText("Cannot start camera: "+e.getMessage());}
  }
  void capture(){
    if(camera==null||taking)return;taking=true;guidance.setText("Capturing...");
    try{camera.takePicture(null,null,(bytes,cam)->{
      try{
        File file=new File(getCacheDir(),"jb-card-camera-"+System.currentTimeMillis()+".jpg");
        try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes);}
        Intent result=new Intent();result.putExtra("cameraPath",file.getAbsolutePath());setResult(RESULT_OK,result);finish();
      }catch(Exception e){taking=false;guidance.setText("Capture failed: "+e.getMessage());try{cam.startPreview();}catch(Exception ignored){}}
    });}catch(Exception e){taking=false;guidance.setText("Camera error: "+e.getMessage());}
  }
  void stopCamera(){if(camera!=null){try{camera.stopPreview();camera.release();}catch(Exception ignored){}camera=null;}}
  @Override protected void onPause(){stopCamera();super.onPause();}
  @Override protected void onResume(){super.onResume();if(surface!=null)startCamera();}
}

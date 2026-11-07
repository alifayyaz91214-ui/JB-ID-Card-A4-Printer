package pk.jb.idcardprinter;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.*;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import java.io.*;

public class CropActivity extends Activity {
  CropView cropView;
  TextView help;
  TextView selectedLabel;
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(12,12,12,12);
    help=new TextView(this);help.setText("Zoom in to inspect corners. Select TL/TR/BR/BL then use arrow buttons for precise adjustments without covering the corner with your finger.");help.setTextSize(16);root.addView(help);
    cropView=new CropView();root.addView(cropView,new LinearLayout.LayoutParams(-1,0,1));
    TextView zoomText=new TextView(this);zoomText.setText("Zoom: 100% • Drag outside corner dots or use arrow controls");root.addView(zoomText);
    SeekBar zoomBar=new SeekBar(this);zoomBar.setMax(300);zoomBar.setProgress(0);root.addView(zoomBar);
    zoomBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
      public void onProgressChanged(SeekBar bar,int value,boolean user){cropView.zoom=1f+value/100f;zoomText.setText("Zoom: "+Math.round(cropView.zoom*100)+"% • Drag outside dots or use arrows");cropView.invalidate();}
      public void onStartTrackingTouch(SeekBar bar){}
      public void onStopTrackingTouch(SeekBar bar){}
    });
    selectedLabel=new TextView(this);selectedLabel.setText("Selected corner: Top Left • tap a dot to change");root.addView(selectedLabel);
    LinearLayout cornerButtons=new LinearLayout(this);cornerButtons.setOrientation(0);
    String[] cornerNames={"TL","TR","BR","BL"};
    for(int k=0;k<4;k++){final int index=k;Button btn=new Button(this);btn.setText(cornerNames[k]);cornerButtons.addView(btn,new LinearLayout.LayoutParams(0,-2,1));btn.setOnClickListener(v->{cropView.selected=index;selectedLabel.setText("Selected corner: "+cornerNames[index]);cropView.invalidate();});}
    root.addView(cornerButtons);
    LinearLayout nudges=new LinearLayout(this);nudges.setOrientation(0);
    String[] arrows={"←","↑","↓","→"};int[] dx={-1,0,0,1},dy={0,-1,1,0};
    for(int k=0;k<4;k++){final int index=k;Button btn=new Button(this);btn.setText(arrows[k]);nudges.addView(btn,new LinearLayout.LayoutParams(0,-2,1));btn.setOnClickListener(v->cropView.nudge(dx[index],dy[index]));}
    root.addView(nudges);
    LinearLayout buttons=new LinearLayout(this);buttons.setOrientation(0);
    Button reset=new Button(this);reset.setText("Reset corners");buttons.addView(reset,new LinearLayout.LayoutParams(0,-2,1));reset.setOnClickListener(v->cropView.reset());
    Button save=new Button(this);save.setText("Crop & Save");buttons.addView(save,new LinearLayout.LayoutParams(0,-2,1));save.setOnClickListener(v->save());
    root.addView(buttons);setContentView(root);
    try{
      Uri uri=getIntent().getData();if(uri==null)throw new IOException("Missing photo");
      BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
      try(InputStream in=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,options);}
      options.inSampleSize=1;while(Math.max(options.outWidth/options.inSampleSize,options.outHeight/options.inSampleSize)>2400)options.inSampleSize*=2;
      options.inJustDecodeBounds=false;
      try(InputStream in=getContentResolver().openInputStream(uri)){cropView.bitmap=BitmapFactory.decodeStream(in,null,options);}
      if(cropView.bitmap==null)throw new IOException("Cannot decode image");
      cropView.reset();
      try {org.opencv.core.Point[] corners=CardDetector.findCorners(cropView.bitmap);
        if(corners!=null){float[] p=new float[8];for(int k=0;k<4;k++){p[2*k]=(float)corners[k].x;p[2*k+1]=(float)corners[k].y;}cropView.points=p;cropView.invalidate();help.setText("Auto-detected corners are shown. Drag only the dots that need adjustment, then Crop & Save.");}
      }catch(Throwable ignored){}
    }catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();finish();}
  }
  void save(){
    try{
      Bitmap original=cropView.bitmap;
      float[] p=cropView.points;
      double top=Math.hypot(p[2]-p[0],p[3]-p[1]),bottom=Math.hypot(p[4]-p[6],p[5]-p[7]);
      double left=Math.hypot(p[6]-p[0],p[7]-p[1]),right=Math.hypot(p[4]-p[2],p[5]-p[3]);
      int width=(int)Math.max(1,Math.min(2400,Math.max(top,bottom)));
      int height=(int)Math.max(1,Math.min(2400,Math.max(left,right)));
      if(width<60||height<40)throw new IOException("Corners are too close");
      float[] dest={0,0,width,0,width,height,0,height};
      Matrix matrix=new Matrix();
      if(!matrix.setPolyToPoly(p,0,dest,0,4))throw new IOException("Invalid corners");
      Bitmap output=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
      Canvas canvas=new Canvas(output);canvas.drawColor(Color.WHITE);
      canvas.drawBitmap(original,matrix,new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));
      File outputFile=new File(getCacheDir(),"manual-crop-"+System.currentTimeMillis()+".png");
      try(FileOutputStream out=new FileOutputStream(outputFile)){output.compress(Bitmap.CompressFormat.PNG,100,out);}
      Intent result=new Intent();result.putExtra("cropPath",outputFile.getAbsolutePath());setResult(RESULT_OK,result);finish();
    }catch(Exception e){Toast.makeText(this,"Crop failed: "+e.getMessage(),Toast.LENGTH_LONG).show();}
  }
  class CropView extends View{
    Bitmap bitmap;float[] points=new float[8];Paint paint=new Paint(3);
    float scale,ox,oy;float zoom=1f;int active=-1,selected=0;
    float touchOffsetX,touchOffsetY;
    CropView(){super(CropActivity.this);setBackgroundColor(Color.rgb(30,30,30));}
    void reset(){
      if(bitmap==null)return;
      float w=bitmap.getWidth(),h=bitmap.getHeight();
      points=new float[]{w*.08f,h*.08f,w*.92f,h*.08f,w*.92f,h*.92f,w*.08f,h*.92f};invalidate();
    }
    boolean selectCardAt(float x,float y){
      if(bitmap==null)return false;
      // Detect the tapped card in a local image region, rather than always using
      // the first card in the whole scanned sheet.
      int w=bitmap.getWidth(),h=bitmap.getHeight();
      int regionW=Math.min(w,Math.max(240,(int)(w*.78f)));
      int regionH=Math.min(h,Math.max(180,(int)(h*.40f)));
      int left=Math.max(0,Math.min(w-regionW,Math.round(x-regionW/2f)));
      int top=Math.max(0,Math.min(h-regionH,Math.round(y-regionH/2f)));
      Bitmap region=null;
      try{
        region=Bitmap.createBitmap(bitmap,left,top,regionW,regionH);
        org.opencv.core.Point[] corners=CardDetector.findCorners(region);
        if(corners==null)return false;
        float[] candidate=new float[8];
        for(int k=0;k<4;k++){candidate[k*2]=(float)corners[k].x+left;candidate[k*2+1]=(float)corners[k].y+top;}
        Path shape=new Path();shape.moveTo(candidate[0],candidate[1]);
        for(int k=1;k<4;k++)shape.lineTo(candidate[2*k],candidate[2*k+1]);shape.close();
        android.graphics.Region hit=new android.graphics.Region();
        android.graphics.RectF bounds=new android.graphics.RectF();shape.computeBounds(bounds,true);
        hit.setPath(shape,new android.graphics.Region((int)bounds.left-2,(int)bounds.top-2,(int)bounds.right+2,(int)bounds.bottom+2));
        if(!hit.contains(Math.round(x),Math.round(y)))return false;
        points=candidate;selected=0;active=-1;
        selectedLabel.setText("Selected corner: Top Left • tap another card to switch");
        help.setText("Card selected. Adjust corners if needed, then Crop & Save. Tap another card to switch.");
        invalidate();return true;
      }catch(Throwable ignored){return false;}
      finally{if(region!=null&&!region.isRecycled())region.recycle();}
    }
    void nudge(int dx,int dy){
      if(bitmap==null)return;
      float step=2f/Math.max(.05f,scale);
      points[2*selected]=Math.max(0,Math.min(bitmap.getWidth(),points[2*selected]+dx*step));
      points[2*selected+1]=Math.max(0,Math.min(bitmap.getHeight(),points[2*selected+1]+dy*step));
      invalidate();
    }
    @Override protected void onDraw(Canvas canvas){
      super.onDraw(canvas);if(bitmap==null)return;
      scale=Math.min((float)getWidth()/bitmap.getWidth(),(float)getHeight()/bitmap.getHeight())*zoom;
      ox=(getWidth()-bitmap.getWidth()*scale)/2f;oy=(getHeight()-bitmap.getHeight()*scale)/2f;
      canvas.save();canvas.clipRect(0,0,getWidth(),getHeight());
      canvas.drawBitmap(bitmap,null,new RectF(ox,oy,ox+bitmap.getWidth()*scale,oy+bitmap.getHeight()*scale),paint);
      Path path=new Path();path.moveTo(ox+points[0]*scale,oy+points[1]*scale);
      for(int i=1;i<4;i++)path.lineTo(ox+points[i*2]*scale,oy+points[i*2+1]*scale);
      path.close();paint.setColor(Color.YELLOW);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(4);canvas.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);
      for(int i=0;i<4;i++){float x=ox+points[2*i]*scale,y=oy+points[2*i+1]*scale;paint.setColor(Color.BLACK);canvas.drawCircle(x,y,19,paint);paint.setColor(i==selected?Color.CYAN:Color.YELLOW);canvas.drawCircle(x,y,13,paint);}
      paint.setColor(Color.WHITE);canvas.restore();
    }
    @Override public boolean onTouchEvent(android.view.MotionEvent e){
      if(bitmap==null)return false;
      float x=(e.getX()-ox)/scale,y=(e.getY()-oy)/scale;
      if(e.getAction()==MotionEvent.ACTION_DOWN){
        float best=Float.MAX_VALUE;active=-1;
        for(int i=0;i<4;i++){float d=(x-points[2*i])*(x-points[2*i])+(y-points[2*i+1])*(y-points[2*i+1]);if(d<best){best=d;active=i;}}
        // A tap well away from corner handles selects another card in this scan.
        float nearestPixels=(float)Math.sqrt(best)*scale;
        if(nearestPixels>38f){
          if(selectCardAt(x,y))return true;
          // If no card was found, leave the existing corners unchanged.
          active=-1;return true;
        }
        selected=active;
        // Maintain a finger-to-corner offset: the thumb can stay away from the exact edge.
        touchOffsetX=points[2*active]-x;touchOffsetY=points[2*active+1]-y;
        invalidate();return true;
      }
      if(e.getAction()==MotionEvent.ACTION_MOVE&&active>=0){
        points[2*active]=Math.max(0,Math.min(bitmap.getWidth(),x+touchOffsetX));points[2*active+1]=Math.max(0,Math.min(bitmap.getHeight(),y+touchOffsetY));invalidate();return true;
      }
      if(e.getAction()==MotionEvent.ACTION_UP){active=-1;return true;}
      return true;
    }
  }
}

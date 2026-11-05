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
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(12,12,12,12);
    help=new TextView(this);help.setText("Drag the 4 circles to the card corners. Zoom is not required; use the preview.\nTop-left, top-right, bottom-right, bottom-left.");help.setTextSize(16);root.addView(help);
    cropView=new CropView();root.addView(cropView,new LinearLayout.LayoutParams(-1,0,1));
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
    float scale,ox,oy;int active=-1;
    CropView(){super(CropActivity.this);setBackgroundColor(Color.rgb(30,30,30));}
    void reset(){
      if(bitmap==null)return;
      float w=bitmap.getWidth(),h=bitmap.getHeight();
      points=new float[]{w*.08f,h*.08f,w*.92f,h*.08f,w*.92f,h*.92f,w*.08f,h*.92f};invalidate();
    }
    @Override protected void onDraw(Canvas canvas){
      super.onDraw(canvas);if(bitmap==null)return;
      scale=Math.min((float)getWidth()/bitmap.getWidth(),(float)getHeight()/bitmap.getHeight());
      ox=(getWidth()-bitmap.getWidth()*scale)/2f;oy=(getHeight()-bitmap.getHeight()*scale)/2f;
      canvas.drawBitmap(bitmap,null,new RectF(ox,oy,ox+bitmap.getWidth()*scale,oy+bitmap.getHeight()*scale),paint);
      Path path=new Path();path.moveTo(ox+points[0]*scale,oy+points[1]*scale);
      for(int i=1;i<4;i++)path.lineTo(ox+points[i*2]*scale,oy+points[i*2+1]*scale);
      path.close();paint.setColor(Color.YELLOW);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(4);canvas.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);
      for(int i=0;i<4;i++){float x=ox+points[2*i]*scale,y=oy+points[2*i+1]*scale;paint.setColor(Color.BLACK);canvas.drawCircle(x,y,19,paint);paint.setColor(Color.YELLOW);canvas.drawCircle(x,y,13,paint);}
      paint.setColor(Color.WHITE);
    }
    @Override public boolean onTouchEvent(android.view.MotionEvent e){
      if(bitmap==null)return false;
      float x=(e.getX()-ox)/scale,y=(e.getY()-oy)/scale;
      if(e.getAction()==MotionEvent.ACTION_DOWN){
        float best=Float.MAX_VALUE;active=-1;
        for(int i=0;i<4;i++){float d=(x-points[2*i])*(x-points[2*i])+(y-points[2*i+1])*(y-points[2*i+1]);if(d<best){best=d;active=i;}}
        return true;
      }
      if(e.getAction()==MotionEvent.ACTION_MOVE&&active>=0){
        points[2*active]=Math.max(0,Math.min(bitmap.getWidth(),x));points[2*active+1]=Math.max(0,Math.min(bitmap.getHeight(),y));invalidate();return true;
      }
      if(e.getAction()==MotionEvent.ACTION_UP){active=-1;return true;}
      return true;
    }
  }
}

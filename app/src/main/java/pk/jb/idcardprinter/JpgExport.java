package pk.jb.idcardprinter;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import java.io.OutputStream;

public final class JpgExport {
  private JpgExport(){}
  public static void choose(Activity activity,int request,String filename){
    Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
    i.addCategory(Intent.CATEGORY_OPENABLE);
    i.setType("image/jpeg");
    i.putExtra(Intent.EXTRA_TITLE,filename);
    activity.startActivityForResult(i,request);
  }
  public static void save(Activity activity,Uri destination,int width,int height,Drawer drawer)throws Exception{
    Bitmap image=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
    try{
      Canvas canvas=new Canvas(image);
      canvas.drawColor(Color.WHITE);
      drawer.draw(canvas);
      try(OutputStream out=activity.getContentResolver().openOutputStream(destination)){
        if(out==null||!image.compress(Bitmap.CompressFormat.JPEG,98,out))throw new java.io.IOException("JPG export failed");
      }
    }finally{image.recycle();}
  }
  public interface Drawer{void draw(Canvas canvas);}
}

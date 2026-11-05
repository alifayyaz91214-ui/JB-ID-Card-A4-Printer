package pk.jb.idcardprinter;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.print.*;
import android.view.*;
import android.widget.*;
import java.io.*;

public class PassportActivity extends Activity {
  static final int PICK=201,SAVE=202;
  Bitmap photo;
  int photoCount=6;
  ImageView preview;
  TextView status;
  @Override public void onCreate(Bundle b){
    super.onCreate(b);
    LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(24,24,24,24);
    ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
    TextView title=new TextView(this);title.setText("JB Passport Photo Maker\n4 or 6 photos on 4R (102 x 152 mm)");title.setTextSize(22);title.setGravity(Gravity.CENTER);root.addView(title);
    Button pick=new Button(this);pick.setText("Select portrait from gallery");root.addView(pick);
    pick.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_GET_CONTENT);i.setType("image/*");startActivityForResult(i,PICK);});
    preview=new ImageView(this);preview.setAdjustViewBounds(true);preview.setMinimumHeight(200);root.addView(preview);
    TextView note=new TextView(this);note.setText("Four 35 x 45 mm portraits, white background, cutting gaps and thin borders. Use a front-facing, properly lit photo with a plain background. Crop is centered; review before printing.");root.addView(note);
    RadioGroup countGroup=new RadioGroup(this);countGroup.setOrientation(RadioGroup.HORIZONTAL);
    RadioButton four=new RadioButton(this);four.setText("4 photos");four.setId(501);countGroup.addView(four);
    RadioButton six=new RadioButton(this);six.setText("6 photos");six.setId(502);countGroup.addView(six);
    countGroup.check(502);countGroup.setOnCheckedChangeListener((g,id)->photoCount=id==501?4:6);root.addView(countGroup);
    Button pdf=new Button(this);pdf.setText("Save 4R PDF");root.addView(pdf);
    pdf.setOnClickListener(v->{if(!ready())return;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/pdf");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,"JB-Passport-Photos-4R.pdf");startActivityForResult(i,SAVE);});
    Button print=new Button(this);print.setText("Print 4R directly");root.addView(print);print.setOnClickListener(v->{if(ready())printSheet();});
    status=new TextView(this);status.setText("Select a photo to begin.");root.addView(status);
  }
  boolean ready(){if(photo==null){status.setText("Select a portrait first.");return false;}return true;}
  @Override protected void onActivityResult(int req,int result,Intent data){
    super.onActivityResult(req,result,data);
    if(result!=RESULT_OK||data==null||data.getData()==null)return;
    if(req==SAVE){try(OutputStream out=getContentResolver().openOutputStream(data.getData())){PdfDocument doc=makePdf();doc.writeTo(out);doc.close();status.setText("4R PDF saved.");}catch(Exception e){status.setText("Save failed: "+e.getMessage());}return;}
    if(req==PICK){try{
      BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;
      try(InputStream in=getContentResolver().openInputStream(data.getData())){BitmapFactory.decodeStream(in,null,o);}
      o.inSampleSize=1;while(Math.max(o.outWidth/o.inSampleSize,o.outHeight/o.inSampleSize)>2000)o.inSampleSize*=2;
      o.inJustDecodeBounds=false;
      try(InputStream in=getContentResolver().openInputStream(data.getData())){photo=BitmapFactory.decodeStream(in,null,o);}
      if(photo==null)throw new IOException("Invalid image");
      preview.setImageBitmap(photo);status.setText("Photo loaded. Check face position before printing.");
    }catch(Exception e){status.setText("Photo error: "+e.getMessage());}}
  }
  // PDF points at 72 dpi: 4R portrait = 102 x 152 mm; photo = 35 x 45 mm.
  PdfDocument makePdf(){
    int pageW=289,pageH=431;
    PdfDocument doc=new PdfDocument();PdfDocument.Page page=doc.startPage(new PdfDocument.PageInfo.Builder(pageW,pageH,1).create());
    Canvas canvas=page.getCanvas();canvas.drawColor(Color.WHITE);
    float w=35f*72/25.4f,h=45f*72/25.4f;
    float gapX=14f,gapY=photoCount==6?9f:14f;
    int rows=photoCount/2;
    float left=(pageW-(2*w+gapX))/2f,top=(pageH-(rows*h+(rows-1)*gapY))/2f;
    Paint imagePaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    Paint border=new Paint(3);border.setColor(Color.BLACK);border.setStrokeWidth(.65f);border.setStyle(Paint.Style.STROKE);
    for(int row=0;row<rows;row++)for(int col=0;col<2;col++){
      float x=left+col*(w+gapX),y=top+row*(h+gapY);
      RectF target=new RectF(x,y,x+w,y+h);
      float desired=w/h,ratio=(float)photo.getWidth()/photo.getHeight();
      int sw=photo.getWidth(),sh=photo.getHeight();
      Rect source;
      if(ratio>desired){int cw=Math.round(sh*desired);int x0=(sw-cw)/2;source=new Rect(x0,0,x0+cw,sh);}
      else{int ch=Math.round(sw/desired);int y0=(sh-ch)/2;source=new Rect(0,y0,sw,y0+ch);}
      canvas.drawBitmap(photo,source,target,imagePaint);canvas.drawRect(target,border);
    }
    doc.finishPage(page);return doc;
  }
  void printSheet(){
    PrintManager manager=(PrintManager)getSystemService(PRINT_SERVICE);
    manager.print("JB Passport Photos 4R",new PrintDocumentAdapter(){
      @Override public void onLayout(PrintAttributes oldA,PrintAttributes newA,android.os.CancellationSignal signal,LayoutResultCallback callback,Bundle extras){
        callback.onLayoutFinished(new PrintDocumentInfo.Builder("JB-Passport-Photos-4R.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(1).build(),true);
      }
      @Override public void onWrite(PageRange[] pages,ParcelFileDescriptor destination,android.os.CancellationSignal signal,WriteResultCallback callback){
        try(FileOutputStream out=new FileOutputStream(destination.getFileDescriptor())){PdfDocument doc=makePdf();doc.writeTo(out);doc.close();callback.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});}
        catch(Exception e){callback.onWriteFailed(e.getMessage());}
      }
    },new PrintAttributes.Builder().setMediaSize(new PrintAttributes.MediaSize("JB_4R","4R 102 x 152 mm",4016,5984)).setColorMode(PrintAttributes.COLOR_MODE_COLOR).build());
  }
}

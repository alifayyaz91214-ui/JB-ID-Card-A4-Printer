package pk.jb.idcardprinter;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.print.*;
import android.print.pdf.PrintedPdfDocument;
import android.view.*;
import android.widget.*;
import java.io.*;

public class MainActivity extends Activity {
  Bitmap front, back, originalFront, originalBack;
  Uri frontUri, backUri;
  ImageView frontView, backView;
  TextView status;
  boolean cvReady=false;
  int printMode=0; // 0 original, 1 balanced grayscale, 2 stronger grayscale
  static final int PICK_FRONT=101, PICK_BACK=102, SAVE=103, CROP_FRONT=104, CROP_BACK=105;
  @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    try{cvReady=org.opencv.android.OpenCVLoader.initLocal();}catch(Throwable e){cvReady=false;}
    LinearLayout root=new LinearLayout(this); root.setOrientation(1); root.setPadding(24,24,24,24);
    ScrollView scroll=new ScrollView(this); scroll.addView(root); setContentView(scroll);
    TextView title=new TextView(this); title.setText("JB Mobile & PhotoState\nID Card A4 Printer"); title.setTextSize(24); title.setGravity(17); root.addView(title);
    frontView=new ImageView(this); backView=new ImageView(this);
    frontView.setAdjustViewBounds(true); backView.setAdjustViewBounds(true);
    frontView.setMinimumHeight(130); backView.setMinimumHeight(130);
    addButton(root,"1. Select FRONT photo",()->pick(PICK_FRONT)); root.addView(frontView);
    addButton(root,"2. Select BACK photo",()->pick(PICK_BACK)); root.addView(backView);
    addButton(root,"Manual crop FRONT (4 corners)",()->openCrop(frontUri,CROP_FRONT));
    addButton(root,"Manual crop BACK (4 corners)",()->openCrop(backUri,CROP_BACK));
    addButton(root,"Auto crop both photos",()->{if(!ready())return;Bitmap f=autoCrop(originalFront!=null?originalFront:front);Bitmap croppedBack=autoCrop(originalBack!=null?originalBack:back);boolean fc=f!=(originalFront!=null?originalFront:front),bc=croppedBack!=(originalBack!=null?originalBack:back);front=f;back=croppedBack;refresh();status.setText("Front: "+(fc?"cropped":"NOT detected")+" | Back: "+(bc?"cropped":"NOT detected")+". Use Manual Crop if not detected.");});
    TextView printLabel=new TextView(this);printLabel.setText("Print quality / color mode");printLabel.setTextSize(17);root.addView(printLabel);
    RadioGroup modes=new RadioGroup(this);modes.setOrientation(RadioGroup.VERTICAL);
    String[] names={"Original Color","B&W Enhanced (recommended)","B&W High Contrast"};
    for(int k=0;k<names.length;k++){final int mode=k;RadioButton rb=new RadioButton(this);rb.setText(names[k]);rb.setId(400+k);modes.addView(rb);}
    modes.check(400);modes.setOnCheckedChangeListener((group,id)->{printMode=id-400;refresh();status.setText("Preview and PDF will use selected print mode. Review fine text before printing.");});
    root.addView(modes);
    addButton(root,"Print A4 directly",()->{if(!ready())return;printSheet();});
    addButton(root,"Save A4 as PDF",()->{if(!ready())return;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/pdf");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,"JB-ID-Cards-A4.pdf");startActivityForResult(i,SAVE);});
    status=new TextView(this);status.setText("Select both photos. Print at actual size (100%).");status.setTextSize(15);root.addView(status);
  }
  void addButton(LinearLayout root,String name,Runnable action){Button b=new Button(this);b.setText(name);root.addView(b);b.setOnClickListener(v->action.run());}
  void openCrop(Uri uri,int code){if(uri==null){status.setText("Select the photo first.");return;}Intent i=new Intent(this,CropActivity.class);i.setData(uri);startActivityForResult(i,code);}
  void pick(int code){Intent i=new Intent(Intent.ACTION_GET_CONTENT);i.setType("image/*");startActivityForResult(i,code);}
  @Override protected void onActivityResult(int request,int result,Intent data){
    super.onActivityResult(request,result,data);
    if(result!=RESULT_OK||data==null)return;
    if(request!=CROP_FRONT&&request!=CROP_BACK&&data.getData()==null)return;
    if(request==CROP_FRONT||request==CROP_BACK){try{String path=data.getStringExtra("cropPath");Bitmap bmp=BitmapFactory.decodeFile(path);if(bmp==null)throw new IOException("Cropped image unavailable");if(request==CROP_FRONT)front=bmp;else back=bmp;refresh();status.setText("Manual crop saved.");}catch(Exception e){status.setText("Crop error: "+e.getMessage());}return;}
    if(request==SAVE){try(OutputStream out=getContentResolver().openOutputStream(data.getData())){PdfDocument pdf=makePdf();pdf.writeTo(out);pdf.close();status.setText("PDF saved successfully.");}catch(Exception e){status.setText("PDF error: "+e.getMessage());}return;}
    try{
      BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;
      try(InputStream in=getContentResolver().openInputStream(data.getData())){BitmapFactory.decodeStream(in,null,opts);}
      opts.inSampleSize=1;while(Math.max(opts.outWidth/opts.inSampleSize,opts.outHeight/opts.inSampleSize)>3600)opts.inSampleSize*=2;
      opts.inJustDecodeBounds=false;
      Bitmap bmp;
      try(InputStream in=getContentResolver().openInputStream(data.getData())){bmp=BitmapFactory.decodeStream(in,null,opts);}
      if(bmp==null)throw new IOException("Image could not be decoded");
      if(request==PICK_FRONT)frontUri=data.getData();else if(request==PICK_BACK)backUri=data.getData();
      if(request==PICK_FRONT)originalFront=bmp;else if(request==PICK_BACK)originalBack=bmp;
      Bitmap cropped=autoCrop(bmp);
      if(request==PICK_FRONT)front=cropped;else if(request==PICK_BACK)back=cropped;
      status.setText(cropped==bmp ? "Auto detect did not find a reliable CNIC rectangle. Original kept; use Manual Crop." : "Auto crop changed the photo. Check preview before printing.");
      refresh();
    }catch(Exception e){status.setText("Image error: "+e.getMessage());}
  }
  void refresh(){frontView.setImageDrawable(null);backView.setImageDrawable(null);frontView.setImageBitmap(previewBitmap(front));backView.setImageBitmap(previewBitmap(back));frontView.invalidate();backView.invalidate();}
  boolean ready(){if(front==null||back==null){status.setText("Please select FRONT and BACK first.");return false;}return true;}
  Bitmap autoCrop(Bitmap src){
    if(src==null||!cvReady)return src;
    try{Bitmap cropped=CardDetector.detect(src);return cropped==null?src:cropped;}
    catch(Exception e){return src;}
  }
  Paint qualityPaint(){
    Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG|Paint.DITHER_FLAG);
    if(printMode==0)return paint;
    ColorMatrix gray=new ColorMatrix();gray.setSaturation(0);
    ColorMatrix contrast=new ColorMatrix();
    float c=printMode==1?1.12f:1.30f;
    float offset=printMode==1?7f:3f;
    float t=128f*(1f-c)+offset;
    contrast.set(new float[]{c,0,0,0,t, 0,c,0,0,t, 0,0,c,0,t, 0,0,0,1,0});
    gray.postConcat(contrast);
    paint.setColorFilter(new ColorMatrixColorFilter(gray));
    return paint;
  }
  Bitmap previewBitmap(Bitmap source){
    if(source==null||printMode==0)return source;
    int w=source.getWidth(),h=source.getHeight();
    float scale=Math.min(1f,900f/Math.max(w,h));
    Bitmap result=Bitmap.createBitmap(Math.max(1,Math.round(w*scale)),Math.max(1,Math.round(h*scale)),Bitmap.Config.ARGB_8888);
    Canvas canvas=new Canvas(result);canvas.drawColor(Color.WHITE);
    canvas.drawBitmap(source,null,new Rect(0,0,result.getWidth(),result.getHeight()),qualityPaint());
    return result;
  }
  // A4 at 72dpi: 595 x 842 points. Each card is 85.6 x 54mm.
  // Front on left, back on right, repeated in 5 rows with cutting gaps.
  PdfDocument makePdf(){
    PdfDocument pdf=new PdfDocument();PdfDocument.PageInfo info=new PdfDocument.PageInfo.Builder(595,842,1).create();
    PdfDocument.Page page=pdf.startPage(info);Canvas c=page.getCanvas();c.drawColor(Color.WHITE);
    Paint p=new Paint(3);p.setColor(Color.BLACK);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(.65f);
    float cardW=242.65f,cardH=153.07f,gap=12f;
    float x1=(595-(2*cardW+gap))/2,x2=x1+cardW+gap,top=16f,rowGap=10f;
    for(int row=0;row<5;row++){
      float y=top+row*(cardH+rowGap);
      drawCard(c,front,x1,y,cardW,cardH,p);drawCard(c,back,x2,y,cardW,cardH,p);
    }
    pdf.finishPage(page);return pdf;
  }
  void drawCard(Canvas c,Bitmap bmp,float x,float y,float w,float h,Paint border){
    RectF r=new RectF(x,y,x+w,y+h);
    Paint fill=qualityPaint();
    c.drawBitmap(bmp,null,r,fill);
    c.drawRect(r,border);
  }
  void printSheet(){
    PrintManager manager=(PrintManager)getSystemService(PRINT_SERVICE);
    manager.print("JB ID Card A4",new PrintDocumentAdapter(){
      @Override public void onLayout(PrintAttributes oldA,PrintAttributes newA,android.os.CancellationSignal signal,LayoutResultCallback callback,Bundle extras){
        PrintDocumentInfo info=new PrintDocumentInfo.Builder("JB-ID-Cards-A4.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(1).build();
        callback.onLayoutFinished(info,true);
      }
      @Override public void onWrite(PageRange[] pages,ParcelFileDescriptor destination,android.os.CancellationSignal signal,WriteResultCallback callback){
        try(FileOutputStream out=new FileOutputStream(destination.getFileDescriptor())){PdfDocument pdf=makePdf();pdf.writeTo(out);pdf.close();callback.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});}
        catch(Exception e){callback.onWriteFailed(e.getMessage());}
      }
    },new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).setColorMode(printMode==0?PrintAttributes.COLOR_MODE_COLOR:PrintAttributes.COLOR_MODE_MONOCHROME).build());
  }
}

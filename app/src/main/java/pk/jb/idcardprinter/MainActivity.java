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
  View sheetPreview;
  boolean cvReady=false;
  int printMode=0; // 0 original, 1 balanced grayscale, 2 stronger grayscale, 3 dark text
  int darkness=12;
  int copyCount=5;
  static final int PICK_FRONT=101, PICK_BACK=102, SAVE=103, CROP_FRONT=104, CROP_BACK=105;
  @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    try{cvReady=org.opencv.android.OpenCVLoader.initLocal();}catch(Throwable e){cvReady=false;}
    LinearLayout root=new LinearLayout(this); root.setOrientation(1); root.setPadding(28,30,28,36);root.setBackgroundColor(Color.rgb(244,247,252));
    ScrollView scroll=new ScrollView(this); scroll.addView(root); setContentView(scroll);
    TextView title=new TextView(this); title.setText("JB Mobile & PhotoState\nID Card A4 Printer"); title.setTextSize(24); title.setGravity(17); root.addView(title);
    TextView previewTitle=new TextView(this);previewTitle.setText("LIVE A4 PRINT PREVIEW");previewTitle.setTextSize(17);previewTitle.setTextColor(Color.rgb(21,38,65));root.addView(previewTitle);
    sheetPreview=new View(this){
      Paint paper=new Paint(3);
      @Override protected void onDraw(Canvas c){
        super.onDraw(c);c.drawColor(Color.rgb(230,235,244));
        float scale=Math.min((getWidth()-24f)/595f,(getHeight()-24f)/842f);
        c.save();c.translate((getWidth()-595f*scale)/2f,(getHeight()-842f*scale)/2f);c.scale(scale,scale);
        c.drawColor(Color.WHITE);drawSheet(c);c.restore();
      }
    };
    LinearLayout.LayoutParams sheetParams=new LinearLayout.LayoutParams(-1,460);sheetParams.bottomMargin=18;root.addView(sheetPreview,sheetParams);
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
    String[] names={"Original Color","B&W Enhanced (recommended)","B&W High Contrast","Dark Text / Print Enhance"};
    for(int k=0;k<names.length;k++){final int mode=k;RadioButton rb=new RadioButton(this);rb.setText(names[k]);rb.setId(400+k);modes.addView(rb);}
    modes.check(400);modes.setOnCheckedChangeListener((group,id)->{printMode=id-400;refresh();status.setText("Preview and PDF will use selected print mode. Review fine text before printing.");});
    root.addView(modes);
    TextView darknessLabel=new TextView(this);darknessLabel.setText("Text darkness: "+darkness+" (0-30)");root.addView(darknessLabel);
    SeekBar darknessSlider=new SeekBar(this);darknessSlider.setMax(30);darknessSlider.setProgress(darkness);
    darknessSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
      public void onProgressChanged(SeekBar bar,int value,boolean fromUser){darkness=value;darknessLabel.setText("Text darkness: "+value+" (0-30)");if(fromUser&&printMode!=0)refresh();}
      public void onStartTrackingTouch(SeekBar bar){}
      public void onStopTrackingTouch(SeekBar bar){}
    });
    root.addView(darknessSlider);
    TextView copiesLabel=new TextView(this);copiesLabel.setText("ID card copies per A4 sheet (front + back)");copiesLabel.setTextSize(17);root.addView(copiesLabel);
    RadioGroup copies=new RadioGroup(this);copies.setOrientation(RadioGroup.HORIZONTAL);
    int[] copyOptions={1,2,4,5};
    for(int count:copyOptions){RadioButton rb=new RadioButton(this);rb.setId(600+count);rb.setText(""+count);copies.addView(rb);}
    copies.check(605);copies.setOnCheckedChangeListener((g,id)->{copyCount=id-600;updateSheet();});root.addView(copies);
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
  void updateSheet(){if(sheetPreview!=null)sheetPreview.invalidate();}
  void refresh(){updateSheet();frontView.setImageDrawable(null);backView.setImageDrawable(null);frontView.setImageBitmap(previewBitmap(front));backView.setImageBitmap(previewBitmap(back));frontView.invalidate();backView.invalidate();}
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
    float c=printMode==1?1.12f:(printMode==2?1.30f:1.16f);
    // Dark Text mode reduces excessive white and strengthens gray lettering.
    // User-adjustable offset, without destructive binary thresholding.
    float offset=printMode==1?7f:(printMode==2?3f:-darkness);
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
    PdfDocument.Page page=pdf.startPage(info);Canvas c=page.getCanvas();c.drawColor(Color.WHITE);drawSheet(c);
    pdf.finishPage(page);return pdf;
  }
  void drawSheet(Canvas c){
    float cardW=242.65f,cardH=153.07f,gap=16f;
    float x1=(595-(2*cardW+gap))/2,x2=x1+cardW+gap,top=12.5f,rowGap=12f;
    for(int row=0;row<copyCount;row++){
      float y=top+row*(cardH+rowGap);
      if(front!=null)drawCard(c,front,x1,y,cardW,cardH);if(back!=null)drawCard(c,back,x2,y,cardW,cardH);
    }
  }
  void drawCard(Canvas c,Bitmap bmp,float x,float y,float w,float h){
    RectF r=new RectF(x,y,x+w,y+h);
    // Slightly rounded physical card corners (2 mm); no artificial black outline.
    float radius=2f*72f/25.4f;
    Path rounded=new Path();
    rounded.addRoundRect(r,radius,radius,Path.Direction.CW);
    c.save();
    c.clipPath(rounded);
    c.drawBitmap(bmp,null,r,qualityPaint());
    c.restore();
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

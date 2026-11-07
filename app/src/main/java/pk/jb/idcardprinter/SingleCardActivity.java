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
import android.graphics.drawable.GradientDrawable;
import java.io.*;

public class SingleCardActivity extends Activity {
  Bitmap front, back, originalFront, originalBack;
  Uri frontUri, backUri;
  ImageView frontView, backView;
  TextView status;
  View sheetPreview;
  boolean cvReady=false;
  int printMode=0; // 0 original, 1 balanced grayscale, 2 stronger grayscale, 3 dark text
  int darkness=12;
  int brightness=10;
  int copyCount=5;
  int verticalPosition=1; // 0 top, 1 center, 2 bottom
  static final int PICK_FRONT=101, PICK_BACK=102, SAVE=103, CROP_FRONT=104, CROP_BACK=105, CAMERA_FRONT=106, CAMERA_BACK=107, SAVE_JPG=108;
  int navy=Color.rgb(23,43,72),accent=Color.rgb(25,105,205);
  GradientDrawable rounded(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
  TextView section(String title){TextView t=new TextView(this);t.setText(title);t.setTextColor(navy);t.setTextSize(18);t.setTypeface(null,Typeface.BOLD);t.setPadding(4,18,4,8);return t;}
  void styleAction(Button b,boolean primary){b.setAllCaps(false);b.setTextSize(16);b.setTypeface(null,Typeface.BOLD);b.setTextColor(primary?Color.WHITE:navy);b.setBackground(rounded(primary?accent:Color.rgb(226,236,249),22));b.setElevation(3);b.setPadding(14,13,14,13);}
  @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    try{cvReady=org.opencv.android.OpenCVLoader.initLocal();}catch(Throwable e){cvReady=false;}
    LinearLayout root=new LinearLayout(this); root.setOrientation(1); root.setPadding(20,22,20,34);root.setBackgroundColor(Color.rgb(244,247,252));
    ScrollView scroll=new ScrollView(this); scroll.addView(root); setContentView(scroll);
    TextView title=new TextView(this); title.setText("JB Mobile & PhotoState\nID Card A4 Printer"); title.setTextSize(24); title.setGravity(17); root.addView(title);
    TextView previewTitle=new TextView(this);previewTitle.setText("Your A4 preview");previewTitle.setTextSize(16);previewTitle.setTextColor(Color.rgb(21,38,65));root.addView(previewTitle);
    sheetPreview=new View(this){
      Paint paper=new Paint(3);
      @Override protected void onDraw(Canvas c){
        super.onDraw(c);c.drawColor(Color.rgb(230,235,244));
        float scale=Math.min((getWidth()-24f)/595f,(getHeight()-24f)/842f);
        c.save();c.translate((getWidth()-595f*scale)/2f,(getHeight()-842f*scale)/2f);c.scale(scale,scale);
        paper.setColor(Color.WHITE);c.drawRect(0,0,595,842,paper);drawSheet(c);c.restore();
      }
    };
    LinearLayout.LayoutParams sheetParams=new LinearLayout.LayoutParams(-1,440);sheetParams.bottomMargin=18;root.addView(sheetPreview,sheetParams);
    frontView=new ImageView(this); backView=new ImageView(this);
    frontView.setAdjustViewBounds(true); backView.setAdjustViewBounds(true);
    frontView.setMinimumHeight(210); backView.setMinimumHeight(210);frontView.setMaxHeight(340);backView.setMaxHeight(340);frontView.setScaleType(ImageView.ScaleType.FIT_CENTER);backView.setScaleType(ImageView.ScaleType.FIT_CENTER);
    root.addView(section("1  Front of ID card"));
    addButton(root,"＋  Select FRONT",()->showSourceChoices(true));
    addButton(root,"✂  Manual Crop FRONT",()->openCrop(frontUri,CROP_FRONT));
    root.addView(frontView);
    root.addView(section("2  Back of ID card"));
    addButton(root,"＋  Select BACK",()->showSourceChoices(false));
    addButton(root,"✂  Manual Crop BACK",()->openCrop(backUri,CROP_BACK));
    root.addView(backView);
    Button advancedToggle=new Button(this);advancedToggle.setText("⚙  More options");styleAction(advancedToggle,false);root.addView(advancedToggle);
    LinearLayout advanced=new LinearLayout(this);advanced.setOrientation(1);advanced.setPadding(12,12,12,12);advanced.setBackground(rounded(Color.WHITE,22));advanced.setVisibility(View.GONE);root.addView(advanced);
    advancedToggle.setOnClickListener(v->{boolean show=advanced.getVisibility()!=View.VISIBLE;advanced.setVisibility(show?View.VISIBLE:View.GONE);advancedToggle.setText(show?"Hide extra options":"⚙  More options");});
    addButton(advanced,"Rotate FRONT 90°",()->rotateCard(true));
    addButton(advanced,"Rotate BACK 90°",()->rotateCard(false));


    addButton(advanced,"Auto crop both photos",()->{if(!ready())return;Bitmap f=autoCrop(originalFront!=null?originalFront:front);Bitmap croppedBack=autoCrop(originalBack!=null?originalBack:back);boolean fc=f!=(originalFront!=null?originalFront:front),bc=croppedBack!=(originalBack!=null?originalBack:back);front=f;back=croppedBack;refresh();status.setText("Front: "+(fc?"cropped":"NOT detected")+" | Back: "+(bc?"cropped":"NOT detected")+". Use Manual Crop if not detected.");});
    TextView printLabel=new TextView(this);printLabel.setText("Print quality / color mode");printLabel.setTextSize(17);advanced.addView(printLabel);
    RadioGroup modes=new RadioGroup(this);modes.setOrientation(RadioGroup.VERTICAL);
    String[] names={"Original Color","B&W Enhanced (recommended)","B&W High Contrast","Dark Text / Print Enhance"};
    for(int k=0;k<names.length;k++){final int mode=k;RadioButton rb=new RadioButton(this);rb.setText(names[k]);rb.setId(400+k);modes.addView(rb);}
    modes.check(400);modes.setOnCheckedChangeListener((group,id)->{printMode=id-400;refresh();status.setText("Preview and PDF will use selected print mode. Review fine text before printing.");});
    advanced.addView(modes);
    TextView darknessLabel=new TextView(this);darknessLabel.setText("Text darkness: "+darkness+" (0-30)");advanced.addView(darknessLabel);
    SeekBar darknessSlider=new SeekBar(this);darknessSlider.setMax(30);darknessSlider.setProgress(darkness);
    darknessSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
      public void onProgressChanged(SeekBar bar,int value,boolean fromUser){darkness=value;darknessLabel.setText("Text darkness: "+value+" (0-30)");if(fromUser&&printMode!=0)refresh();}
      public void onStartTrackingTouch(SeekBar bar){}
      public void onStopTrackingTouch(SeekBar bar){}
    });
    advanced.addView(darknessSlider);
    TextView brightLabel=new TextView(this);brightLabel.setText("Photo brightness: +"+brightness+" (-40 to +60)");advanced.addView(brightLabel);
    SeekBar brightSlider=new SeekBar(this);brightSlider.setMax(100);brightSlider.setProgress(brightness+40);
    brightSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
      public void onProgressChanged(SeekBar b,int value,boolean user){brightness=value-40;brightLabel.setText("Photo brightness: "+(brightness>=0?"+":"")+brightness+" (-40 to +60)");if(user)refresh();}
      public void onStartTrackingTouch(SeekBar b){}
      public void onStopTrackingTouch(SeekBar b){}
    });advanced.addView(brightSlider);
    TextView copiesLabel=new TextView(this);copiesLabel.setText("ID card copies per A4 sheet (front + back)");copiesLabel.setTextSize(17);advanced.addView(copiesLabel);
    RadioGroup copies=new RadioGroup(this);copies.setOrientation(RadioGroup.HORIZONTAL);
    int[] copyOptions={1,2,4,5};
    for(int count:copyOptions){RadioButton rb=new RadioButton(this);rb.setId(600+count);rb.setText(""+count);copies.addView(rb);}
    copies.check(605);copies.setOnCheckedChangeListener((g,id)->{copyCount=id-600;updateSheet();});advanced.addView(copies);
    TextView positionLabel=new TextView(this);positionLabel.setText("A4 card placement");positionLabel.setTextSize(17);advanced.addView(positionLabel);
    RadioGroup positions=new RadioGroup(this);positions.setOrientation(RadioGroup.HORIZONTAL);
    String[] positionNames={"Top","Center","Bottom"};
    for(int i=0;i<3;i++){RadioButton rb=new RadioButton(this);rb.setId(720+i);rb.setText(positionNames[i]);positions.addView(rb);}
    positions.check(721);positions.setOnCheckedChangeListener((g,id)->{verticalPosition=id-720;updateSheet();});advanced.addView(positions);
    root.addView(section("3  Print or save"));
    addButton(root,"🖨  Print A4",()->{if(!ready())return;printSheet();});
    addButton(root,"Save as JPG",()->{if(ready())JpgExport.choose(this,SAVE_JPG,"JB-Single-ID-Card-A4.jpg");});
    addButton(root,"Save PDF",()->{if(!ready())return;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/pdf");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,"JB-ID-Cards-A4.pdf");startActivityForResult(i,SAVE);});
    status=new TextView(this);status.setText("Capture both sides, then print or save.");status.setTextSize(14);status.setTextColor(Color.rgb(85,103,125));status.setPadding(6,12,6,12);root.addView(status);
  }
  void showSourceChoices(boolean isFront){
    java.util.ArrayList<String> labels=new java.util.ArrayList<>();
    java.util.ArrayList<Runnable> actions=new java.util.ArrayList<>();
    final int pickCode=isFront?PICK_FRONT:PICK_BACK;
    final int cameraCode=isFront?CAMERA_FRONT:CAMERA_BACK;
    labels.add("Gallery / Saved scan");actions.add(()->pick(pickCode));
    labels.add("Camera");actions.add(()->openCamera(cameraCode));
    labels.add("Find Wi-Fi Scanners");actions.add(()->WifiScannerDiscovery.show(this));
    boolean epsonInstalled=getPackageManager().getLaunchIntentForPackage("com.epson.epsonsmart")!=null
        ||getPackageManager().getLaunchIntentForPackage("com.epson.epsonsmartpanel")!=null;
    if(epsonInstalled){
      labels.add("Epson Smart Panel");actions.add(()->openEpsonScanner());
    }
    new android.app.AlertDialog.Builder(this)
      .setTitle(isFront?"Select FRONT source":"Select BACK source")
      .setItems(labels.toArray(new String[0]),(dialog,which)->actions.get(which).run())
      .setNegativeButton("Cancel",null).show();
  }
  void addButtonRow(LinearLayout parent,String left,Runnable leftAction,String right,Runnable rightAction){
    LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
    row.setWeightSum(2f);
    Button a=new Button(this),b=new Button(this);
    a.setText(left);b.setText(right);
    styleAction(a,false);styleAction(b,false);
    a.setTextSize(13);b.setTextSize(13);a.setPadding(4,8,4,8);b.setPadding(4,8,4,8);
    LinearLayout.LayoutParams lpA=new LinearLayout.LayoutParams(0,-2,1f),lpB=new LinearLayout.LayoutParams(0,-2,1f);
    lpA.rightMargin=3;lpB.leftMargin=3;
    row.addView(a,lpA);row.addView(b,lpB);
    parent.addView(row);a.setOnClickListener(v->leftAction.run());b.setOnClickListener(v->rightAction.run());
  }
  void addButton(LinearLayout root,String name,Runnable action){Button b=new Button(this);b.setText(name);styleAction(b,name.contains("Capture")||name.contains("Print A4"));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=10;root.addView(b,lp);b.setOnClickListener(v->action.run());}
  void openCrop(Uri uri,int code){if(uri==null){status.setText("Select the photo first.");return;}Intent i=new Intent(this,CropActivity.class);i.setData(uri);startActivityForResult(i,code);}
  void openCamera(int code){Intent i=new Intent(this,CardCameraActivity.class);startActivityForResult(i,code);}
  void openEpsonScanner(){
    Intent launch=getPackageManager().getLaunchIntentForPackage("com.epson.epsonsmart");
    if(launch==null)launch=getPackageManager().getLaunchIntentForPackage("com.epson.epsonsmartpanel");
    if(launch!=null){
      try{startActivity(launch);status.setText("Scan in Epson Smart Panel, save the image, return here and tap Import Scanned Image.");return;}
      catch(Exception ignored){}
    }
    new android.app.AlertDialog.Builder(this).setTitle("Epson Smart Panel")
      .setMessage("Open Epson Smart Panel to scan and save a JPG. Then return to JB and import the saved scan. If Smart Panel is not installed, install it first.")
      .setPositiveButton("Open app settings",(d,w)->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("market://search?q=Epson Smart Panel")));}catch(Exception e){status.setText("Open Epson Smart Panel manually.");}})
      .setNegativeButton("Cancel",null).show();
  }
  void pick(int code){Intent i=new Intent(Intent.ACTION_GET_CONTENT);i.setType("image/*");startActivityForResult(i,code);}
  @Override protected void onActivityResult(int request,int result,Intent data){
    super.onActivityResult(request,result,data);
    if(result!=RESULT_OK||data==null)return;
    if(request!=CROP_FRONT&&request!=CROP_BACK&&request!=CAMERA_FRONT&&request!=CAMERA_BACK&&data.getData()==null)return;
    if(request==CAMERA_FRONT||request==CAMERA_BACK){
      final boolean isFront=request==CAMERA_FRONT;final String path=data.getStringExtra("cameraPath");
      if(path==null)return;status.setText("Straightening captured card...");
      new Thread(()->{Bitmap bmp=null;Bitmap cropped=null;String error=null;
        try{BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;BitmapFactory.decodeFile(path,opts);opts.inSampleSize=1;while(Math.max(opts.outWidth/opts.inSampleSize,opts.outHeight/opts.inSampleSize)>2400)opts.inSampleSize*=2;opts.inJustDecodeBounds=false;bmp=BitmapFactory.decodeFile(path,opts);if(bmp==null)throw new IOException("Camera photo unavailable");
          android.media.ExifInterface exif=new android.media.ExifInterface(path);int orientation=exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1);int angle=orientation==6?90:(orientation==3?180:(orientation==8?270:0));if(angle!=0){Matrix m=new Matrix();m.postRotate(angle);bmp=Bitmap.createBitmap(bmp,0,0,bmp.getWidth(),bmp.getHeight(),m,true);}
          cropped=autoCrop(bmp);
        }catch(Exception e){error=e.getMessage();}
        final Bitmap source=bmp,resultBitmap=cropped;final String failure=error;
        runOnUiThread(()->{if(isFinishing()||isDestroyed())return;if(failure!=null){status.setText("Camera image error: "+failure);return;}if(isFront){originalFront=source;front=resultBitmap;frontUri=Uri.fromFile(new File(path));}else{originalBack=source;back=resultBitmap;backUri=Uri.fromFile(new File(path));}refresh();if(resultBitmap==source){status.setText("Card edges not detected. Opening 4-corner crop so hands and background are removed.");openCrop(Uri.fromFile(new File(path)),isFront?CROP_FRONT:CROP_BACK);}else{status.setText("Card captured and perspective-straightened. Check edges in preview.");}});
      }).start();return;
    }
    if(request==CROP_FRONT||request==CROP_BACK){try{String path=data.getStringExtra("cropPath");Bitmap bmp=BitmapFactory.decodeFile(path);if(bmp==null)throw new IOException("Cropped image unavailable");if(request==CROP_FRONT)front=bmp;else back=bmp;refresh();status.setText("Manual crop saved.");}catch(Exception e){status.setText("Crop error: "+e.getMessage());}return;}
    if(request==SAVE_JPG){try{JpgExport.save(this,data.getData(),2480,3508,c->{c.scale(2480f/595f,3508f/842f);drawSheet(c);});status.setText("JPG saved.");}catch(Exception e){status.setText("JPG save failed: "+e.getMessage());}return;}
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
  void rotateCard(boolean isFront){
    Bitmap src=isFront?front:back;
    if(src==null){status.setText("Select a photo first.");return;}
    Matrix rotation=new Matrix();rotation.postRotate(90);
    Bitmap turned=Bitmap.createBitmap(src,0,0,src.getWidth(),src.getHeight(),rotation,true);
    if(isFront)front=turned;else back=turned;
    refresh();status.setText("Photo rotated. Check the A4 preview.");
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
    if(printMode==0){if(brightness!=0){ColorMatrix light=new ColorMatrix(new float[]{1,0,0,0,brightness,0,1,0,0,brightness,0,0,1,0,brightness,0,0,0,1,0});paint.setColorFilter(new ColorMatrixColorFilter(light));}return paint;}
    ColorMatrix gray=new ColorMatrix();gray.setSaturation(0);
    ColorMatrix contrast=new ColorMatrix();
    float c=printMode==1?1.12f:(printMode==2?1.30f:1.16f);
    // Dark Text mode reduces excessive white and strengthens gray lettering.
    // User-adjustable offset, without destructive binary thresholding.
    float offset=printMode==1?7f:(printMode==2?3f:-darkness);
    float t=128f*(1f-c)+offset+brightness;
    contrast.set(new float[]{c,0,0,0,t, 0,c,0,0,t, 0,0,c,0,t, 0,0,0,1,0});
    gray.postConcat(contrast);
    paint.setColorFilter(new ColorMatrixColorFilter(gray));
    return paint;
  }
  Bitmap previewBitmap(Bitmap source){
    if(source==null||(printMode==0&&brightness==0))return source;
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
    final float pageW=595f,pageH=842f;
    final float cardW=85.6f*72f/25.4f,cardH=54f*72f/25.4f;
    // Keep actual card dimensions. Center the entire group on the page.
    // Fewer selected rows leave room for larger, easier-to-cut vertical gaps.
    final float middleGap=32f; // 11.3 mm between front and back
    final float rowGap=copyCount==5?14f:(copyCount==4?48f:(copyCount==2?65f:0f));
    final float totalW=2f*cardW+middleGap;
    final float totalH=copyCount*cardH+Math.max(0,copyCount-1)*rowGap;
    final float x1=(pageW-totalW)/2f,x2=x1+cardW+middleGap;
    final float freeSpace=Math.max(0,pageH-totalH);
    final float top=verticalPosition==0?Math.min(18f,freeSpace):(verticalPosition==2?Math.max(0,freeSpace-18f):freeSpace/2f);
    for(int row=0;row<copyCount;row++){
      float y=top+row*(cardH+rowGap);
      if(front!=null)drawCard(c,front,x1,y,cardW,cardH);
      if(back!=null)drawCard(c,back,x2,y,cardW,cardH);
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

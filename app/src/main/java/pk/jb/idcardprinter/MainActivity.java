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

public class MainActivity extends Activity {
  Bitmap front, back, originalFront, originalBack;
  Bitmap secondFront,secondBack;
  Bitmap[] extraFront=new Bitmap[4],extraBack=new Bitmap[4];
  int selectedExtra=1,firstCardNumber=1;
  int[] cardCopies={1,1,1,1};
  Uri sourceDocument;
  Uri[] cardFrontSources=new Uri[4],cardBackSources=new Uri[4];
  int pendingCropCard=0;
  boolean pendingCropFront=true;
  boolean secondFirst=false;
  boolean mixedA4=false;
  Uri frontUri, backUri;
  ImageView frontView, backView;
  LinearLayout cardList;
  LinearLayout copiesPanel;
  ImageView importedPreview;
  int shownCards=1;
  int importPage=0;
  int scanSerial=0;
  boolean importingFront=true;
  String[] frontNumbers=new String[4],backNumbers=new String[4];
  boolean[] selectedCards={true,true,true,true};
  TextView status;
  View sheetPreview;
  boolean cvReady=false;
  int printMode=0; // 0 original, 1 balanced grayscale, 2 stronger grayscale, 3 dark text
  int darkness=12;
  int brightness=10;
  int copyCount=5;
  int verticalPosition=1; // 0 top, 1 center, 2 bottom
  static final int PICK_FRONT=101, PICK_BACK=102, SAVE=103, CROP_FRONT=104, CROP_BACK=105, CAMERA_FRONT=106, CAMERA_BACK=107, SAVE_JPG=108, PICK_DOCUMENT=109, CROP_SECOND_FRONT=110, CROP_SECOND_BACK=111;
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
    root.addView(section("1  Add ID cards"));
    addButton(root,"1  Add FRONT document",()->beginSideImport(true));
    addButton(root,"2  Add BACK document",()->beginSideImport(false));
    TextView instruction=new TextView(this);instruction.setText("Add all fronts first, then add backs in the same order. Check each card below.");instruction.setTextColor(navy);instruction.setPadding(8,4,8,12);root.addView(instruction);
    importedPreview=new ImageView(this);importedPreview.setAdjustViewBounds(true);importedPreview.setMaxHeight(300);root.addView(importedPreview);
    addButton(root,"Start new sheet / Clear all cards",()->{scanSerial++;selectedCards=new boolean[]{true,true,true,true};frontNumbers=new String[4];backNumbers=new String[4];front=null;back=null;originalFront=null;originalBack=null;extraFront=new Bitmap[4];extraBack=new Bitmap[4];cardFrontSources=new Uri[4];cardBackSources=new Uri[4];shownCards=1;sourceDocument=null;importedPreview.setImageDrawable(null);refresh();status.setText("New empty sheet. Add a photo or document.");});
    cardList=new LinearLayout(this);cardList.setOrientation(1);root.addView(cardList);
    frontView=new ImageView(this);backView=new ImageView(this);
    frontView.setAdjustViewBounds(true);backView.setAdjustViewBounds(true);
    sheetPreview=new View(this){
      Paint paper=new Paint(3);
      @Override protected void onDraw(Canvas c){
        super.onDraw(c);c.drawColor(Color.rgb(230,235,244));
        float scale=Math.min((getWidth()-24f)/595f,(getHeight()-24f)/842f);
        c.save();c.translate((getWidth()-595f*scale)/2f,(getHeight()-842f*scale)/2f);c.scale(scale,scale);
        paper.setColor(Color.WHITE);c.drawRect(0,0,595,842,paper);drawSheet(c);c.restore();
      }
    };
    renderCardList();
    Button advancedToggle=new Button(this);advancedToggle.setText("⚙  More options");styleAction(advancedToggle,false);root.addView(advancedToggle);
    LinearLayout advanced=new LinearLayout(this);advanced.setOrientation(1);advanced.setPadding(12,12,12,12);advanced.setBackground(rounded(Color.WHITE,22));advanced.setVisibility(View.GONE);root.addView(advanced);
    advancedToggle.setOnClickListener(v->{boolean show=advanced.getVisibility()!=View.VISIBLE;advanced.setVisibility(show?View.VISIBLE:View.GONE);advancedToggle.setText(show?"Hide extra options":"⚙  More options");});
    addButton(advanced,"Rotate FRONT 90°",()->rotateCard(true));
    addButton(advanced,"Rotate BACK 90°",()->rotateCard(false));

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
    root.addView(section("2  Choose copies for each card"));
    CheckBox mixedToggle=new CheckBox(this);mixedToggle.setText("Mix selected cards on one A4 sheet");mixedToggle.setChecked(true);mixedA4=true;root.addView(mixedToggle);
    mixedToggle.setOnCheckedChangeListener((button,checked)->{mixedA4=checked;refresh();});
    TextView hint=new TextView(this);hint.setText("Use − / + for each card. Set 0 to skip. Up to 5 pairs fit on one A4 page.");hint.setTextColor(navy);root.addView(hint);
    copiesPanel=new LinearLayout(this);copiesPanel.setOrientation(1);root.addView(copiesPanel);
    renderCopiesPanel();
    TextView previewTitle=section("A4 print preview");root.addView(previewTitle);
    LinearLayout.LayoutParams sheetParams=new LinearLayout.LayoutParams(-1,490);sheetParams.bottomMargin=18;root.addView(sheetPreview,sheetParams);
    root.addView(section("3  Print or save"));
    addButton(root,"🖨  Print A4",()->{if(!ready())return;printSheet();});
    addButton(root,"Save as JPG",()->{if(ready()){if(pageCount()>1)status.setText("JPG saves page 1 only. Save PDF for all A4 sheets.");JpgExport.choose(this,SAVE_JPG,"JB-ID-Cards-A4.jpg");}});
    addButton(root,"Save PDF",()->{if(!ready())return;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/pdf");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,"JB-ID-Cards-A4.pdf");startActivityForResult(i,SAVE);});
    status=new TextView(this);status.setText("Capture both sides, then print or save.");status.setTextSize(14);status.setTextColor(Color.rgb(85,103,125));status.setPadding(6,12,6,12);root.addView(status);
  }
  void addButton(LinearLayout root,String name,Runnable action){Button b=new Button(this);b.setText(name);styleAction(b,name.contains("Capture")||name.contains("Print A4"));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=10;root.addView(b,lp);b.setOnClickListener(v->action.run());}
  void openCrop(Uri uri,int code){if(uri==null){status.setText("Select the photo first.");return;}Intent i=new Intent(this,CropActivity.class);i.setData(uri);startActivityForResult(i,code);}
  void openCamera(int code){Intent i=new Intent(this,CardCameraActivity.class);startActivityForResult(i,code);}
  void pick(int code){Intent i=new Intent(Intent.ACTION_GET_CONTENT);i.setType("image/*");startActivityForResult(i,code);}
  @Override protected void onActivityResult(int request,int result,Intent data){
    super.onActivityResult(request,result,data);
    if(result!=RESULT_OK||data==null)return;
    if(request!=CROP_FRONT&&request!=CROP_BACK&&request!=CROP_SECOND_FRONT&&request!=CROP_SECOND_BACK&&request!=CAMERA_FRONT&&request!=CAMERA_BACK&&data.getData()==null)return;
    if(request==CAMERA_FRONT||request==CAMERA_BACK){
      final boolean isFront=request==CAMERA_FRONT;final String path=data.getStringExtra("cameraPath");
      if(path==null)return;status.setText("Straightening captured card...");
      new Thread(()->{Bitmap bmp=null;Bitmap cropped=null;String error=null;
        try{BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;BitmapFactory.decodeFile(path,opts);opts.inSampleSize=1;while(Math.max(opts.outWidth/opts.inSampleSize,opts.outHeight/opts.inSampleSize)>2400)opts.inSampleSize*=2;opts.inJustDecodeBounds=false;bmp=BitmapFactory.decodeFile(path,opts);if(bmp==null)throw new IOException("Camera photo unavailable");
          android.media.ExifInterface exif=new android.media.ExifInterface(path);int orientation=exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1);int angle=orientation==6?90:(orientation==3?180:(orientation==8?270:0));if(angle!=0){Matrix m=new Matrix();m.postRotate(angle);bmp=Bitmap.createBitmap(bmp,0,0,bmp.getWidth(),bmp.getHeight(),m,true);}
          cropped=autoCrop(bmp);
        }catch(Exception e){error=e.getMessage();}
        final Bitmap source=bmp,resultBitmap=cropped;final String failure=error;
        runOnUiThread(()->{if(isFinishing()||isDestroyed())return;if(failure!=null){status.setText("Camera image error: "+failure);return;}sourceDocument=Uri.fromFile(new File(path));importedPreview.setImageBitmap(source);scanDocument(source,sourceDocument);});
      }).start();return;
    }
    if(request==PICK_DOCUMENT){sourceDocument=data.getData();try{String mime=getContentResolver().getType(sourceDocument);if("application/pdf".equals(mime)){android.graphics.pdf.PdfRenderer renderer=new android.graphics.pdf.PdfRenderer(getContentResolver().openFileDescriptor(sourceDocument,"r"));android.graphics.pdf.PdfRenderer.Page page=renderer.openPage(0);float scale=Math.min(5f,3508f/Math.max(page.getWidth(),page.getHeight()));Bitmap image=Bitmap.createBitmap(Math.max(1,(int)(page.getWidth()*scale)),Math.max(1,(int)(page.getHeight()*scale)),Bitmap.Config.ARGB_8888);image.eraseColor(Color.WHITE);page.render(image,null,null,android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);page.close();renderer.close();File file=new File(getCacheDir(),"jb-imported-document.png");try(FileOutputStream out=new FileOutputStream(file)){image.compress(Bitmap.CompressFormat.PNG,100,out);}sourceDocument=Uri.fromFile(file);status.setText("PDF page 1 loaded. Choose card count and crop each side.");}else{status.setText("Image loaded. Choose card count and crop each side.");}}catch(Exception e){status.setText("Document import failed: "+e.getMessage());return;}try{BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=4;Bitmap thumb=BitmapFactory.decodeStream(getContentResolver().openInputStream(sourceDocument),null,options);importedPreview.setImageBitmap(thumb);}catch(Exception ignored){}try{BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;try(InputStream in=getContentResolver().openInputStream(sourceDocument)){BitmapFactory.decodeStream(in,null,opts);}opts.inSampleSize=1;while(Math.max(opts.outWidth/opts.inSampleSize,opts.outHeight/opts.inSampleSize)>3600)opts.inSampleSize*=2;opts.inJustDecodeBounds=false;Bitmap original;try(InputStream in=getContentResolver().openInputStream(sourceDocument)){original=BitmapFactory.decodeStream(in,null,opts);}scanDocument(original,sourceDocument);}catch(Exception e){status.setText("Scan failed: "+e.getMessage());}return;}
    if(request==CROP_FRONT||request==CROP_BACK||request==CROP_SECOND_FRONT||request==CROP_SECOND_BACK){try{String path=data.getStringExtra("cropPath");Bitmap bmp=BitmapFactory.decodeFile(path);if(bmp==null)throw new IOException("Cropped image unavailable");int id=pendingCropCard;boolean isFront=pendingCropFront;if(id==0){if(isFront)front=bmp;else back=bmp;}else{if(isFront)extraFront[id]=bmp;else extraBack[id]=bmp;}if(isFront)cardFrontSources[id]=sourceDocument;else cardBackSources[id]=sourceDocument;shownCards=Math.max(shownCards,id+1);refresh();status.setText("Selected card crop saved.");}catch(Exception e){status.setText("Crop error: "+e.getMessage());}return;}
    if(request==SAVE_JPG){try{JpgExport.save(this,data.getData(),2480,3508,c->{c.scale(2480f/595f,3508f/842f);drawSheet(c);});status.setText("A4 JPG saved at 300 DPI.");}catch(Exception e){status.setText("JPG save failed: "+e.getMessage());}return;}
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
  void beginSideImport(boolean frontSide){importingFront=frontSide;showImportChoices();}
  void showImportChoices(){
    String[] choices={"Camera","Gallery / JPG / PNG / PDF"};
    new android.app.AlertDialog.Builder(this).setTitle(importingFront?"Add FRONT document":"Add BACK document").setItems(choices,(dialog,which)->{
      if(which==0)openCamera(CAMERA_FRONT);
      else{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/jpeg","image/png","image/webp","application/pdf"});startActivityForResult(i,PICK_DOCUMENT);}
    }).show();
  }
  void setCopiesForCard(int index,int count){
    cardCopies[index]=Math.max(0,Math.min(20,count));
    selectedCards[index]=cardCopies[index]>0;
    renderCopiesPanel();renderCardList();updateSheet();
    if(status!=null)status.setText("Selected "+totalPairs()+" pair(s) on "+pageCount()+" A4 page(s).");
  }
  int totalPairs(){
    int total=0;for(int id:activeCards())total+=cardCopies[id];return total;
  }
  void renderCopiesPanel(){
    if(copiesPanel==null)return;
    copiesPanel.removeAllViews();
    for(int i=0;i<4;i++){
      final int id=i;
      LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);line.setPadding(8,6,8,6);
      TextView label=new TextView(this);label.setText("Card "+(i+1));label.setTextColor(navy);label.setTextSize(17);label.setTypeface(null,Typeface.BOLD);
      line.addView(label,new LinearLayout.LayoutParams(0,-2,1));
      Button minus=new Button(this);minus.setText("−");styleAction(minus,false);
      line.addView(minus,new LinearLayout.LayoutParams(65,-2));
      TextView number=new TextView(this);number.setText(""+(selectedCards[i]?cardCopies[i]:0));number.setTextColor(navy);number.setTextSize(20);number.setGravity(Gravity.CENTER);
      line.addView(number,new LinearLayout.LayoutParams(56,-2));
      Button plus=new Button(this);plus.setText("+");styleAction(plus,false);
      line.addView(plus,new LinearLayout.LayoutParams(65,-2));
      minus.setOnClickListener(v->setCopiesForCard(id,(selectedCards[id]?cardCopies[id]:0)-1));
      plus.setOnClickListener(v->{
        if(sideBitmap(id,true)==null&&sideBitmap(id,false)==null){if(status!=null)status.setText("Add Card "+(id+1)+" first.");return;}
        setCopiesForCard(id,(selectedCards[id]?cardCopies[id]:0)+1);
      });
      copiesPanel.addView(line);
    }
    TextView total=new TextView(this);total.setText("Total: "+totalPairs()+" card pair(s)  •  "+pageCount()+" A4 page(s)");total.setTextColor(navy);total.setTextSize(16);total.setPadding(8,12,8,12);copiesPanel.addView(total);
  }
  void applyCopyPreset(int[] counts){
    mixedA4=true;
    for(int i=0;i<4;i++){
      cardCopies[i]=Math.max(1,counts[i]);
      selectedCards[i]=counts[i]>0&&sideBitmap(i,true)!=null||counts[i]>0&&sideBitmap(i,false)!=null;
    }
    refresh();
    status.setText("Mixed A4 ready: "+pageCount()+" page(s). Check the A4 preview before printing.");
  }
  Bitmap cardDisplayPreview(Bitmap source){
    Bitmap bitmap=previewBitmap(source);
    if(bitmap==null)return null;
    int w=bitmap.getWidth(),h=bitmap.getHeight();
    if(w<30||h<30)return bitmap;
    // Trim only blank white outer margins in the on-screen preview; keep the print bitmap unchanged.
    int left=w,top=h,right=-1,bottom=-1;
    int step=Math.max(1,Math.min(w,h)/350);
    for(int y=0;y<h;y+=step)for(int x=0;x<w;x+=step){
      int color=bitmap.getPixel(x,y);
      int r=Color.red(color),g=Color.green(color),b=Color.blue(color);
      if(Math.min(r,Math.min(g,b))<218 || Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))>28){
        left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);
      }
    }
    if(right<=left||bottom<=top)return bitmap;
    int cropW=Math.min(w-left,right-left+step),cropH=Math.min(h-top,bottom-top+step);
    // Avoid zooming into a tiny logo or text when a nearly white card has weak boundaries.
    if(cropW<w*.48||cropH<h*.40)return bitmap;
    if(cropW>w*.96&&cropH>h*.96)return bitmap;
    return Bitmap.createBitmap(bitmap,left,top,cropW,cropH);
  }
  void renderCardList(){
    if(cardList==null)return;
    cardList.removeAllViews();
    for(int id=0;id<4;id++){
      final int index=id;
      Bitmap f=id==0?front:extraFront[id],b=id==0?back:extraBack[id];
      if(id>=shownCards&&f==null&&b==null)continue;
      LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(12,10,12,14);panel.setBackground(rounded(Color.WHITE,18));
      LinearLayout.LayoutParams margin=new LinearLayout.LayoutParams(-1,-2);margin.bottomMargin=12;cardList.addView(panel,margin);
      CheckBox include=new CheckBox(this);include.setText("Card "+(id+1)+"  •  "+(f!=null&&b!=null?"Front + Back":f!=null?"Front only":b!=null?"Back only":"Add card"));include.setTextSize(17);include.setTypeface(null,Typeface.BOLD);include.setChecked(selectedCards[id]);panel.addView(include);include.setOnCheckedChangeListener((button,checked)->{selectedCards[index]=checked;if(checked&&cardCopies[index]==0)cardCopies[index]=1;renderCopiesPanel();updateSheet();});
      if(f!=null){ImageView image=new ImageView(this);image.setImageBitmap(cardDisplayPreview(f));image.setAdjustViewBounds(true);image.setScaleType(ImageView.ScaleType.FIT_CENTER);LinearLayout.LayoutParams imageParams=new LinearLayout.LayoutParams(-1,320);panel.addView(image,imageParams);}
      addButton(panel,"✂  Front: "+(f==null?"select and crop":"edit crop"),()->cropSlot(index,true));
      if(b!=null){ImageView image=new ImageView(this);image.setImageBitmap(cardDisplayPreview(b));image.setAdjustViewBounds(true);image.setScaleType(ImageView.ScaleType.FIT_CENTER);LinearLayout.LayoutParams imageParams=new LinearLayout.LayoutParams(-1,320);panel.addView(image,imageParams);}
      addButton(panel,"✂  Back: "+(b==null?"select and crop":"edit crop"),()->cropSlot(index,false));
      if(f!=null&&b==null)addButton(panel,"This is BACK (not Front)",()->{if(index==0){back=front;front=null;}else{extraBack[index]=extraFront[index];extraFront[index]=null;}cardBackSources[index]=cardFrontSources[index];cardFrontSources[index]=null;refresh();});
      if(b!=null&&f==null)addButton(panel,"This is FRONT (not Back)",()->{if(index==0){front=back;back=null;}else{extraFront[index]=extraBack[index];extraBack[index]=null;}cardFrontSources[index]=cardBackSources[index];cardBackSources[index]=null;refresh();});
      if(f!=null||b!=null){
        addButton(panel,"Move Front / Back to another card",()->{
          String[] options={"Move Front","Move Back"};
          new android.app.AlertDialog.Builder(this).setTitle("Choose side").setItems(options,(dialog,side)->{
            boolean moveFront=side==0;Bitmap picture=sideBitmap(index,moveFront);
            if(picture==null){status.setText("This side is empty.");return;}
            String[] destinations={"Card 1","Card 2","Card 3","Card 4"};
            new android.app.AlertDialog.Builder(this).setTitle("Move to which card?").setItems(destinations,(d,target)->{
              if(target==index)return;
              if(sideBitmap(target,moveFront)!=null){status.setText("Destination already has this side. Remove it first.");return;}
              Uri old=moveFront?cardFrontSources[index]:cardBackSources[index];
              if(index==0){if(moveFront)front=null;else back=null;}
              else{if(moveFront)extraFront[index]=null;else extraBack[index]=null;}
              if(moveFront)cardFrontSources[index]=null;else cardBackSources[index]=null;
              assignSide(target,moveFront,picture,old);refresh();
              status.setText("Side moved to Card "+(target+1)+".");
            }).show();
          }).show();
        });
        addButton(panel,"Remove this card",()->{if(index==0){front=null;back=null;}else{extraFront[index]=null;extraBack[index]=null;}cardFrontSources[index]=null;cardBackSources[index]=null;refresh();});
        TextView count=section("Copies: "+cardCopies[id]);panel.addView(count);
        SeekBar slider=new SeekBar(this);slider.setMax(20);slider.setProgress(cardCopies[id]);panel.addView(slider);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
          public void onProgressChanged(SeekBar bar,int value,boolean fromUser){cardCopies[index]=value;selectedCards[index]=value>0;count.setText("Copies: "+cardCopies[index]);renderCopiesPanel();updateSheet();}
          public void onStartTrackingTouch(SeekBar bar){}
          public void onStopTrackingTouch(SeekBar bar){}
        });
      }
    }
  }
  Bitmap sideBitmap(int id,boolean isFront){return id==0?(isFront?front:back):(isFront?extraFront[id]:extraBack[id]);}
  void assignSide(int id,boolean isFront,Bitmap bitmap,Uri uri){
    if(id==0){if(isFront)front=bitmap;else back=bitmap;}
    else{if(isFront)extraFront[id]=bitmap;else extraBack[id]=bitmap;}
    if(isFront){cardFrontSources[id]=uri;frontNumbers[id]=null;}else{cardBackSources[id]=uri;backNumbers[id]=null;}
    selectedCards[id]=true;shownCards=Math.max(shownCards,id+1);
  }
  void assignDetected(java.util.List<Bitmap> found,Uri uri,int position){
    if(position>=found.size()){refresh();status.setText("Cards arranged. Review Front / Back, copies and A4 preview.");return;}
    Bitmap candidate=found.get(position);
    LinearLayout layout=new LinearLayout(this);layout.setOrientation(1);layout.setPadding(24,12,24,0);
    ImageView preview=new ImageView(this);preview.setImageBitmap(candidate);preview.setAdjustViewBounds(true);preview.setMaxHeight(230);layout.addView(preview);
    TextView note=new TextView(this);note.setText("Detected image "+(position+1)+" of "+found.size()+". Select its correct card and side.");note.setPadding(4,10,4,10);layout.addView(note);
    String[] labels={"Card 1 - Front","Card 1 - Back","Card 2 - Front","Card 2 - Back","Card 3 - Front","Card 3 - Back","Card 4 - Front","Card 4 - Back"};
    new android.app.AlertDialog.Builder(this).setTitle("Which card side is this?").setView(layout)
      .setItems(labels,(dialog,which)->{
        int id=which/2;boolean isFront=which%2==0;
        Bitmap previous=sideBitmap(id,isFront);
        if(previous!=null){
          new android.app.AlertDialog.Builder(this).setTitle("Replace existing side?")
            .setMessage("Card "+(id+1)+" already has a "+(isFront?"Front":"Back")+".")
            .setPositiveButton("Replace",(d,w)->{assignSide(id,isFront,candidate,uri);refresh();assignDetected(found,uri,position+1);})
            .setNegativeButton("Choose again",(d,w)->assignDetected(found,uri,position)).show();
        }else{assignSide(id,isFront,candidate,uri);refresh();assignDetected(found,uri,position+1);}
      }).setNegativeButton("Skip image",(dialog,which)->assignDetected(found,uri,position+1))
      .setCancelable(false).show();
  }
  void matchScannedSides(java.util.List<Bitmap> cards,Uri uri,boolean frontSide,int position,int serial,int matched,int review){
    if(serial!=scanSerial||isFinishing()||isDestroyed())return;
    if(position>=cards.size()||position>=4){
      refresh();
      status.setText(matched+" side(s) matched by CNIC number; "+review+" need manual review. Confirm the pairs before printing.");
      return;
    }
    Bitmap candidate=cards.get(position);
    CnicOcr.read(candidate,number->{
      if(serial!=scanSerial||isFinishing()||isDestroyed())return;
      int match=-1;
      if(number!=null){
        for(int i=0;i<4;i++){
          String opposite=frontSide?backNumbers[i]:frontNumbers[i];
          if(number.equals(opposite)&&sideBitmap(i,frontSide)==null){match=i;break;}
        }
      }
      if(match>=0){
        assignSide(match,frontSide,candidate,uri);
        if(frontSide)frontNumbers[match]=number;else backNumbers[match]=number;
        matchScannedSides(cards,uri,frontSide,position+1,serial,matched+1,review);
        return;
      }
      // Show an explicit, scrollable selection with large buttons. Never silently guess a pair.
      final String detected=number;
      android.widget.ScrollView scroll=new android.widget.ScrollView(this);
      LinearLayout content=new LinearLayout(this);
      content.setOrientation(LinearLayout.VERTICAL);
      content.setPadding(22,12,22,12);
      scroll.addView(content);
      ImageView image=new ImageView(this);
      image.setImageBitmap(candidate);
      image.setScaleType(ImageView.ScaleType.FIT_CENTER);
      content.addView(image,new LinearLayout.LayoutParams(-1,260));
      TextView prompt=new TextView(this);
      prompt.setText("BACK image "+(position+1)+" of "+cards.size()+"\\nSelect which Card this BACK belongs to. The FRONT image shown below each button is for comparison only.");
      prompt.setTextSize(16);prompt.setTextColor(navy);prompt.setPadding(0,12,0,12);
      content.addView(prompt);
      android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this)
        .setTitle("Choose this BACK belongs to")
        .setView(scroll).setCancelable(false).create();
      for(int i=0;i<4;i++){
        final int target=i;
        Bitmap frontPreview=sideBitmap(i,true);
        String label="Add as BACK of Card "+(i+1);
        Button button=new Button(this);button.setText(label);styleAction(button,false);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.bottomMargin=8;
        content.addView(button,params);
        if(frontPreview!=null){
          TextView frontLabel=new TextView(this);frontLabel.setText("FRONT of Card "+(i+1)+" (reference only)");frontLabel.setTextSize(13);frontLabel.setTextColor(navy);content.addView(frontLabel);
          ImageView small=new ImageView(this);small.setImageBitmap(previewBitmap(frontPreview));
          small.setScaleType(ImageView.ScaleType.FIT_CENTER);
          content.addView(small,new LinearLayout.LayoutParams(-1,180));
        }
        button.setOnClickListener(v->{
          dialog.dismiss();
          if(sideBitmap(target,frontSide)!=null){
            new android.app.AlertDialog.Builder(this).setTitle("Replace existing BACK?")
              .setMessage("Card "+(target+1)+" already has a back image.")
              .setPositiveButton("Replace",(d,w)->{assignSide(target,frontSide,candidate,uri);backNumbers[target]=detected;refresh();matchScannedSides(cards,uri,frontSide,position+1,serial,matched,review+1);})
              .setNegativeButton("Choose again",(d,w)->matchScannedSides(cards,uri,frontSide,position,serial,matched,review)).show();
          }else{
            assignSide(target,frontSide,candidate,uri);backNumbers[target]=detected;refresh();
            matchScannedSides(cards,uri,frontSide,position+1,serial,matched,review+1);
          }
        });
      }
      Button skip=new Button(this);skip.setText("Skip this image");styleAction(skip,false);
      content.addView(skip);
      skip.setOnClickListener(v->{dialog.dismiss();matchScannedSides(cards,uri,frontSide,position+1,serial,matched,review+1);});
      dialog.show();
    });
  }
  void scanDocument(Bitmap image,Uri uri){
    if(image==null){status.setText("Could not read photo.");return;}
    final int serial=++scanSerial;
    final boolean sideIsFront=importingFront;
    status.setText("Finding ID cards automatically...");
    new Thread(()->{
      java.util.List<Bitmap> found=new java.util.ArrayList<>();
      String error=null;
      try{if(cvReady)found=CardDetector.detectAll(image,8);}catch(Exception e){error=e.getMessage();}
      final java.util.List<Bitmap> cards=found;final String problem=error;
      runOnUiThread(()->{
        if(serial!=scanSerial||isFinishing()||isDestroyed())return;
        if(cards.isEmpty()){
          shownCards=Math.max(shownCards,1);renderCardList();
          status.setText("No reliable card edges found. Use Front / Back manual crop."+(problem==null?"":" "+problem));
          return;
        }
        if(sideIsFront){
          // Fronts are placed in the first available slots, then their OCR numbers are stored.
          int added=0;
          for(Bitmap card:cards){
            int slot=-1;for(int i=0;i<4;i++)if(sideBitmap(i,true)==null){slot=i;break;}
            if(slot<0)break;
            final int id=slot;assignSide(id,true,card,uri);added++;
            CnicOcr.read(card,number->{if(serial==scanSerial){frontNumbers[id]=number;refresh();}});
          }
          refresh();status.setText(added+" FRONT sides added. Now import BACK document to auto-match CNIC numbers.");
        }else{
          status.setText("Reading CNIC numbers to match BACK sides...");
          matchScannedSides(cards,uri,false,0,serial,0,0);
        }
      });
    }).start();
  }
  void chooseCardCount(){
    String[] options={"1 card","2 cards","3 cards","4 cards"};
    new android.app.AlertDialog.Builder(this).setTitle("How many ID cards are in this photo?").setSingleChoiceItems(options,shownCards-1,(dialog,which)->{shownCards=which+1;renderCardList();dialog.dismiss();}).setPositiveButton("Done",(dialog,which)->renderCardList()).show();
  }
  int firstEmptyCard(){for(int i=1;i<4;i++)if(extraFront[i]==null&&extraBack[i]==null)return i;return 3;}
  void cropSlot(int index,boolean isFront){
    pendingCropCard=index;pendingCropFront=isFront;
    Uri uri=isFront?cardFrontSources[index]:cardBackSources[index];
    if(uri==null)uri=sourceDocument;
    if(uri==null){status.setText("First add a JPG, PDF or camera photo, then crop this card.");showImportChoices();return;}
    openCrop(uri,index==0?(isFront?CROP_FRONT:CROP_BACK):(isFront?CROP_SECOND_FRONT:CROP_SECOND_BACK));
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
  void refresh(){updateSheet();renderCardList();renderCopiesPanel();}
  boolean ready(){if(activeCards().length==0){status.setText("Please crop at least one ID card side first.");return false;}return true;}
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
  int[] activeCards(){
    java.util.ArrayList<Integer> ids=new java.util.ArrayList<>();
    if(selectedCards[0]&&cardCopies[0]>0&&(front!=null||back!=null))ids.add(0);
    for(int i=1;i<4;i++)if(selectedCards[i]&&cardCopies[i]>0&&(extraFront[i]!=null||extraBack[i]!=null))ids.add(i);
    if(ids.size()>1){int chosen=firstCardNumber-1;if(ids.remove(Integer.valueOf(chosen)))ids.add(0,chosen);}
    int[] result=new int[ids.size()];for(int i=0;i<result.length;i++)result[i]=ids.get(i);return result;
  }
  boolean hasSecondCard(){return activeCards().length>1;}
  PdfDocument makePdf(){
    PdfDocument pdf=new PdfDocument();int[] cards=activeCards();
    for(int n=0;n<pageCount();n++){
      PdfDocument.Page page=pdf.startPage(new PdfDocument.PageInfo.Builder(595,842,n+1).create());
      Canvas canvas=page.getCanvas();canvas.drawColor(Color.WHITE);drawSheetPage(canvas,n);pdf.finishPage(page);
    }
    return pdf;
  }
  int pageCount(){int[] ids=activeCards();if(!mixedA4)return Math.max(1,ids.length);int total=0;for(int id:ids)total+=cardCopies[id];return Math.max(1,(total+4)/5);}
  void drawSheet(Canvas c){drawSheetPage(c,0);}
  void drawSheetPage(Canvas c,int pageIndex){
    int[] cards=activeCards();if(cards.length==0)return;
    final float pageW=595f,pageH=842f;
    final float cardW=85.6f*72f/25.4f,cardH=54f*72f/25.4f;
    final float middleGap=32f;
    java.util.ArrayList<Integer> layout=new java.util.ArrayList<>();
    if(mixedA4){
      for(int id:cards)for(int n=0;n<cardCopies[id];n++)layout.add(id);
      int from=pageIndex*5,to=Math.min(layout.size(),from+5);
      if(from>=to)return;
      layout=new java.util.ArrayList<>(layout.subList(from,to));
    }else{
      int id=cards[Math.min(pageIndex,cards.length-1)];
      int rows=cards.length>1?cardCopies[id]:copyCount;
      for(int n=0;n<rows;n++)layout.add(id);
    }
    int rows=layout.size();
    final float rowGap=rows==5?14f:(rows==4?48f:(rows==2?65f:0f));
    final float totalW=2f*cardW+middleGap;
    final float totalH=rows*cardH+Math.max(0,rows-1)*rowGap;
    final float x1=(pageW-totalW)/2f,x2=x1+cardW+middleGap;
    final float freeSpace=Math.max(0,pageH-totalH);
    final float top=verticalPosition==0?Math.min(18f,freeSpace):(verticalPosition==2?Math.max(0,freeSpace-18f):freeSpace/2f);
    for(int row=0;row<rows;row++){
      int id=layout.get(row);
      Bitmap f=id==0?front:extraFront[id],backImage=id==0?back:extraBack[id];
      float y=top+row*(cardH+rowGap);
      if(f!=null)drawCard(c,f,x1,y,cardW,cardH);
      if(backImage!=null)drawCard(c,backImage,x2,y,cardW,cardH);
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
        PrintDocumentInfo info=new PrintDocumentInfo.Builder("JB-ID-Cards-A4.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(pageCount()).build();
        callback.onLayoutFinished(info,true);
      }
      @Override public void onWrite(PageRange[] pages,ParcelFileDescriptor destination,android.os.CancellationSignal signal,WriteResultCallback callback){
        try(FileOutputStream out=new FileOutputStream(destination.getFileDescriptor())){PdfDocument pdf=makePdf();pdf.writeTo(out);pdf.close();callback.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});}
        catch(Exception e){callback.onWriteFailed(e.getMessage());}
      }
    },new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).setColorMode(printMode==0?PrintAttributes.COLOR_MODE_COLOR:PrintAttributes.COLOR_MODE_MONOCHROME).build());
  }
}

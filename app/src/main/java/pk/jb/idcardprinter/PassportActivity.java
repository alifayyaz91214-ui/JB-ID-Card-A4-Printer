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
  static final int PICK=201,SAVE=202,CAMERA=203,SAVE_JPG=204;
  Bitmap photo;
  RectF cropArea=null; // normalized crop coordinates, shared by preview and PDF
  Button cropButton;
  int photoCount=6;
  boolean autoFill=true;
  int sizeIndex=0;
  boolean a4=false;
  int paperIndex=0; // 0=4R, 1=A4, 2=5x7, 3=A5
  int sheetPosition=1; // 0 top, 1 center, 2 bottom
  TextView capacityLabel;
  int brightness=0;
  int contrast=100;
  int smoothing=0;
  int backgroundChoice=0; // 0 original, 1 white, 2 blue, 3 gray
  final String[] sizeNames={"Pakistan / UK - 35 x 45 mm","USA / India - 51 x 51 mm","EU - 35 x 45 mm","40 x 50 mm","30 x 40 mm","25 x 35 mm","20 x 30 mm","50 x 50 mm"};
  final float[] widths={35,51,35,40,30,25,20,50}, heights={45,51,45,50,40,35,30,50};
  ImageView preview;
  TextView status;
  View sheetPreview;
  Bitmap previewEdited;
  void polishButton(Button b,boolean primary){
    b.setAllCaps(false);b.setTextSize(15);b.setTypeface(null,Typeface.BOLD);
    b.setTextColor(primary?Color.WHITE:Color.rgb(22,43,73));
    android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
    bg.setColor(primary?Color.rgb(24,104,201):Color.rgb(231,239,250));
    bg.setCornerRadius(20);b.setBackground(bg);b.setElevation(2);
    b.setPadding(14,14,14,14);
  }
  @Override public void onCreate(Bundle b){
    super.onCreate(b);
    LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(20,24,20,36);root.setBackgroundColor(Color.rgb(245,248,253));
    ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
    TextView title=new TextView(this);title.setText("Passport Photo Studio\nChoose size • Auto-fill • Print");title.setTextSize(23);title.setTextColor(Color.rgb(21,38,65));title.setTypeface(null,android.graphics.Typeface.BOLD);title.setGravity(Gravity.CENTER);title.setPadding(0,0,0,18);root.addView(title);
    TextView liveTitle=new TextView(this);liveTitle.setText("LIVE FINAL PRINT PREVIEW");liveTitle.setTextSize(17);liveTitle.setTextColor(Color.rgb(21,38,65));root.addView(liveTitle);
    sheetPreview=new View(this){
      @Override protected void onDraw(Canvas c){
        super.onDraw(c);c.drawColor(Color.rgb(232,238,247));
        int pw=paperWidth(),ph=paperHeight();
        float scale=Math.min((getWidth()-20f)/pw,(getHeight()-20f)/ph);
        c.save();c.translate((getWidth()-pw*scale)/2f,(getHeight()-ph*scale)/2f);c.scale(scale,scale);
        Paint paper=new Paint(3);paper.setColor(Color.WHITE);c.drawRect(0,0,pw,ph,paper);
        if(previewEdited!=null)drawSheet(c,pw,ph,previewEdited);
        c.restore();
      }
    };
    LinearLayout.LayoutParams previewParams=new LinearLayout.LayoutParams(-1,380);previewParams.bottomMargin=14;root.addView(sheetPreview,previewParams);
    Button pick=new Button(this);pick.setText("Select portrait from gallery");polishButton(pick,false);root.addView(pick);
    pick.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_GET_CONTENT);i.setType("image/*");startActivityForResult(i,PICK);});
    Button camera=new Button(this);camera.setText("Scan / take photo with camera");polishButton(camera,false);root.addView(camera);
    camera.setOnClickListener(v->{Intent i=new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);try{startActivityForResult(i,CAMERA);}catch(Exception e){status.setText("Camera app unavailable.");}});
    preview=new ImageView(this);preview.setAdjustViewBounds(true);preview.setMinimumHeight(200);root.addView(preview);
    cropButton=new Button(this);cropButton.setText("CROP & POSITION PHOTO (EXACT SELECTED SIZE)");polishButton(cropButton,false);root.addView(cropButton);
    cropButton.setOnClickListener(v->showCropEditor());
    TextView note=new TextView(this);note.setText("The sheet automatically fills with as many photos as fit at the selected physical size. Uncheck Auto-fill by entering an exact count. Check face position and crop before printing.");root.addView(note);
    TextView sizeLabel=new TextView(this);sizeLabel.setText("Photo size preset (confirm official application rules)");root.addView(sizeLabel);
    Spinner sizes=new Spinner(this);ArrayAdapter<String> sizeAdapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,sizeNames);sizes.setAdapter(sizeAdapter);
    sizes.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
      public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){sizeIndex=position;cropArea=null;updateSheet();}
      public void onNothingSelected(android.widget.AdapterView<?> parent){}
    });root.addView(sizes);
    CheckBox autoCount=new CheckBox(this);
    autoCount.setText("Auto-fill sheet with maximum photos");
    autoCount.setChecked(true);root.addView(autoCount);
    autoCount.setOnCheckedChangeListener((button,checked)->{
      autoFill=checked;
      if(checked)photoCount=maximumCopies();
      updateSheet();
    });
    TextView customLabel=new TextView(this);
    customLabel.setText("Or choose exact number of photos (e.g. 2, 3, 4)");
    root.addView(customLabel);
    EditText customCount=new EditText(this);
    customCount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    customCount.setSingleLine(true);customCount.setHint("Enter photo count");
    root.addView(customCount);
    customCount.addTextChangedListener(new android.text.TextWatcher(){
      public void beforeTextChanged(CharSequence t,int start,int count,int after){}
      public void onTextChanged(CharSequence t,int start,int before,int count){
        if(t.length()==0)return;
        try{
          int requested=Integer.parseInt(t.toString());
          if(requested<1)return;
          autoFill=false;
          autoCount.setChecked(false);
          photoCount=requested;updateSheet();
        }catch(NumberFormatException ignored){}
      }
      public void afterTextChanged(android.text.Editable e){}
    });
    TextView paperLabel=new TextView(this);paperLabel.setText("Paper size");paperLabel.setTextSize(17);root.addView(paperLabel);
    Spinner paper=new Spinner(this);
    paper.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,
      new String[]{"4R / 4 x 6 inch","A4 / 210 x 297 mm","5 x 7 inch","A5 / 148 x 210 mm"}));
    paper.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
      public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){paperIndex=position;a4=position==1;updateSheet();}
      public void onNothingSelected(android.widget.AdapterView<?> parent){}
    });root.addView(paper);
    capacityLabel=new TextView(this);capacityLabel.setTextSize(16);capacityLabel.setTextColor(Color.rgb(21,83,148));root.addView(capacityLabel);
    TextView positionLabel=new TextView(this);
    positionLabel.setText("Place photos on sheet");
    positionLabel.setTextSize(17);
    positionLabel.setTextColor(Color.rgb(21,38,65));
    root.addView(positionLabel);
    RadioGroup positionOptions=new RadioGroup(this);
    positionOptions.setOrientation(RadioGroup.HORIZONTAL);
    String[] positions={"Top","Center","Bottom"};
    for(int k=0;k<positions.length;k++){
      final int choice=k;
      RadioButton option=new RadioButton(this);
      option.setText(positions[k]);option.setTextSize(14);
      option.setId(View.generateViewId());
      positionOptions.addView(option,new RadioGroup.LayoutParams(0,-2,1f));
      if(k==sheetPosition)option.setChecked(true);
      option.setOnClickListener(v->{sheetPosition=choice;updateSheet();});
    }
    root.addView(positionOptions);

    TextView enhanceLabel=new TextView(this);enhanceLabel.setText("Photo finishing - brightness");enhanceLabel.setTextSize(17);root.addView(enhanceLabel);
    SeekBar light=new SeekBar(this);light.setMax(100);light.setProgress(50);
    light.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
      public void onProgressChanged(SeekBar bar,int value,boolean user){brightness=value-50;if(user)refreshPreview();}
      public void onStartTrackingTouch(SeekBar bar){}
      public void onStopTrackingTouch(SeekBar bar){}
    });root.addView(light);
    TextView contrastLabel=new TextView(this);contrastLabel.setText("Photo finishing - contrast");root.addView(contrastLabel);
    SeekBar contrastBar=new SeekBar(this);contrastBar.setMax(160);contrastBar.setProgress(100);
    contrastBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
      public void onProgressChanged(SeekBar bar,int value,boolean user){contrast=value;if(user)refreshPreview();}
      public void onStartTrackingTouch(SeekBar bar){}
      public void onStopTrackingTouch(SeekBar bar){}
    });root.addView(contrastBar);
    TextView editTitle=new TextView(this);editTitle.setText("PHOTO EDITING  •  OFFLINE");editTitle.setTextSize(18);editTitle.setTextColor(Color.rgb(21,38,65));root.addView(editTitle);
    TextView smoothTitle=new TextView(this);smoothTitle.setText("Skin / photo smoothing (0-5)");root.addView(smoothTitle);
    SeekBar smoothBar=new SeekBar(this);smoothBar.setMax(5);smoothBar.setProgress(0);
    smoothBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
      public void onProgressChanged(SeekBar b,int v,boolean user){smoothing=v;if(user)refreshPreview();}
      public void onStartTrackingTouch(SeekBar b){}
      public void onStopTrackingTouch(SeekBar b){}
    });root.addView(smoothBar);
    TextView bgTitle=new TextView(this);bgTitle.setText("Plain background replacement (best on solid backgrounds)");root.addView(bgTitle);
    Spinner bg=new Spinner(this);
    bg.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Keep original","White background","Light blue background","Light gray background"}));
    bg.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
      public void onItemSelected(android.widget.AdapterView<?> parent,View v,int pos,long id){backgroundChoice=pos;refreshPreview();}
      public void onNothingSelected(android.widget.AdapterView<?> parent){}
    });root.addView(bg);
    TextView editNote=new TextView(this);editNote.setText("Background replacement uses corner-color matching, not AI person segmentation. Review hair edges and official photo rules. Clothes change requires a separate AI model and is not available yet.");root.addView(editNote);
    TextView fitting=new TextView(this);fitting.setText("Auto-fill places the maximum photos that fit; entering a number prints only that many. All sizes use physical millimeter dimensions and cutting gaps.");root.addView(fitting);
    Button jpg=new Button(this);jpg.setText("Save selected sheet as JPG");polishButton(jpg,false);root.addView(jpg);jpg.setOnClickListener(v->{if(ready())JpgExport.choose(this,SAVE_JPG,"JB-Passport-Photos-"+paperName()+".jpg");});
    Button pdf=new Button(this);pdf.setText("Save selected sheet as PDF");polishButton(pdf,false);root.addView(pdf);
    pdf.setOnClickListener(v->{if(!ready())return;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/pdf");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,"JB-Passport-Photos-"+paperName()+".pdf");startActivityForResult(i,SAVE);});
    Button print=new Button(this);print.setText("Print selected sheet directly");polishButton(print,true);root.addView(print);print.setOnClickListener(v->{if(ready())printSheet();});
    status=new TextView(this);status.setText("Select a photo to begin.");root.addView(status);
  }
  int paperWidth(){return new int[]{289,595,360,420}[paperIndex];}
  int paperHeight(){return new int[]{431,842,504,595}[paperIndex];}
  String paperName(){return new String[]{"4R","A4","5x7","A5"}[paperIndex];}
  float gapX(){return 9f;}
  float gapY(){return 9f;}
  int columns(){
    float w=widths[sizeIndex]*72f/25.4f;
    return Math.max(1,(int)Math.floor((paperWidth()-24f+gapX())/(w+gapX())));
  }
  int maximumCopies(){
    float h=heights[sizeIndex]*72f/25.4f;
    int rows=Math.max(1,(int)Math.floor((paperHeight()-24f+gapY())/(h+gapY())));
    return columns()*rows;
  }
  boolean ready(){
    if(photo==null){status.setText("Select a portrait first.");return false;}
    if(photoCount<1||photoCount>maximumCopies()){
      status.setText("Only "+maximumCopies()+" photos fit at exact size on "+paperName()+". Choose fewer photos or larger paper.");
      return false;
    }
    return true;
  }
  @Override protected void onActivityResult(int req,int result,Intent data){
    super.onActivityResult(req,result,data);
    if(result!=RESULT_OK||data==null)return;
    if(req==CAMERA){Object taken=data.getExtras()==null?null:data.getExtras().get("data");if(taken instanceof Bitmap){photo=(Bitmap)taken;cropArea=null;refreshPreview();status.setText("Camera image loaded. Review resolution and preview before printing.");}else status.setText("Camera returned no image.");return;}
    if(data.getData()==null)return;
    if(req==SAVE_JPG){try{final int pw=paperWidth(),ph=paperHeight();final int jw=Math.round(pw*300f/72f),jh=Math.round(ph*300f/72f);final Bitmap image=editedPhoto(2000);JpgExport.save(this,data.getData(),jw,jh,c->{c.scale((float)jw/pw,(float)jh/ph);drawSheet(c,pw,ph,image);});status.setText("JPG photo sheet saved.");}catch(Exception e){status.setText("JPG save failed: "+e.getMessage());}return;}
    if(req==SAVE){try(OutputStream out=getContentResolver().openOutputStream(data.getData())){PdfDocument doc=makePdf();doc.writeTo(out);doc.close();status.setText(paperName()+" PDF saved.");}catch(Exception e){status.setText("Save failed: "+e.getMessage());}return;}
    if(req==PICK){try{
      BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;
      try(InputStream in=getContentResolver().openInputStream(data.getData())){BitmapFactory.decodeStream(in,null,o);}
      o.inSampleSize=1;while(Math.max(o.outWidth/o.inSampleSize,o.outHeight/o.inSampleSize)>2000)o.inSampleSize*=2;
      o.inJustDecodeBounds=false;
      try(InputStream in=getContentResolver().openInputStream(data.getData())){photo=BitmapFactory.decodeStream(in,null,o);cropArea=null;}
      if(photo==null)throw new IOException("Invalid image");
      refreshPreview();status.setText("Photo loaded. Check face position before printing.");
    }catch(Exception e){status.setText("Photo error: "+e.getMessage());}}
  }
  Paint finishingPaint(){
    Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG|Paint.DITHER_FLAG);
    float c=contrast/100f,offset=128f*(1f-c)+brightness;
    ColorMatrix matrix=new ColorMatrix(new float[]{c,0,0,0,offset,0,c,0,0,offset,0,0,c,0,offset,0,0,0,1,0});
    p.setColorFilter(new ColorMatrixColorFilter(matrix));return p;
  }
  Bitmap editedPhoto(int maxEdge){
    if(photo==null)return null;
    float scale=Math.min(1f,(float)maxEdge/Math.max(photo.getWidth(),photo.getHeight()));
    int w=Math.max(1,Math.round(photo.getWidth()*scale)),h=Math.max(1,Math.round(photo.getHeight()*scale));
    Bitmap result=Bitmap.createScaledBitmap(photo,w,h,true).copy(Bitmap.Config.ARGB_8888,true);
    if(smoothing>0){
      try{
        org.opencv.core.Mat src=new org.opencv.core.Mat(),dst=new org.opencv.core.Mat();
        org.opencv.android.Utils.bitmapToMat(result,src);
        org.opencv.core.Mat rgb=new org.opencv.core.Mat(),rgba=new org.opencv.core.Mat();
        org.opencv.imgproc.Imgproc.cvtColor(src,rgb,org.opencv.imgproc.Imgproc.COLOR_RGBA2RGB);
        org.opencv.imgproc.Imgproc.bilateralFilter(rgb,dst,5+smoothing*2,15+smoothing*10,10+smoothing*8);
        org.opencv.imgproc.Imgproc.cvtColor(dst,rgba,org.opencv.imgproc.Imgproc.COLOR_RGB2RGBA);
        org.opencv.android.Utils.matToBitmap(rgba,result);src.release();dst.release();rgb.release();rgba.release();
      }catch(Exception ignored){}
    }
    if(backgroundChoice>0){
      int[] pixels=new int[w*h];result.getPixels(pixels,0,w,0,0,w,h);
      int corner=pixels[0],corner2=pixels[w-1],corner3=pixels[(h-1)*w],corner4=pixels[w*h-1];
      int r=(Color.red(corner)+Color.red(corner2)+Color.red(corner3)+Color.red(corner4))/4;
      int g=(Color.green(corner)+Color.green(corner2)+Color.green(corner3)+Color.green(corner4))/4;
      int b=(Color.blue(corner)+Color.blue(corner2)+Color.blue(corner3)+Color.blue(corner4))/4;
      int replacement=backgroundChoice==1?Color.WHITE:(backgroundChoice==2?Color.rgb(204,228,248):Color.rgb(225,225,225));
      // Only replace corner-connected pixels; avoids recoloring isolated foreground areas.
      boolean[] seen=new boolean[pixels.length];int[] queue=new int[pixels.length];int head=0,tail=0;
      for(int idx:new int[]{0,w-1,(h-1)*w,w*h-1}){if(!seen[idx]){seen[idx]=true;queue[tail++]=idx;}}
      while(head<tail){
        int idx=queue[head++],color=pixels[idx];
        int dr=Color.red(color)-r,dg=Color.green(color)-g,db=Color.blue(color)-b;
        if(dr*dr+dg*dg+db*db>2700)continue;
        pixels[idx]=replacement;int x=idx%w,y=idx/w;
        if(x>0&&!seen[idx-1]){seen[idx-1]=true;queue[tail++]=idx-1;}
        if(x+1<w&&!seen[idx+1]){seen[idx+1]=true;queue[tail++]=idx+1;}
        if(y>0&&!seen[idx-w]){seen[idx-w]=true;queue[tail++]=idx-w;}
        if(y+1<h&&!seen[idx+w]){seen[idx+w]=true;queue[tail++]=idx+w;}
      }
      result.setPixels(pixels,0,w,0,0,w,h);
    }
    return result;
  }
  void refreshPreview(){
    if(photo==null)return;
    Bitmap edited=editedPhoto(800);
    Bitmap output=Bitmap.createBitmap(edited.getWidth(),edited.getHeight(),Bitmap.Config.ARGB_8888);
    new Canvas(output).drawBitmap(edited,0,0,finishingPaint());
    preview.setImageBitmap(output);previewEdited=edited;updateSheet();
  }
  void updateSheet(){
    if(autoFill)photoCount=maximumCopies();
    if(sheetPreview!=null)sheetPreview.invalidate();
    if(capacityLabel!=null)capacityLabel.setText(
      "Fits "+maximumCopies()+" photos on "+paperName()+" at "+(int)widths[sizeIndex]+" x "+(int)heights[sizeIndex]+" mm"
      +" • Printing: "+photoCount+(photoCount>maximumCopies()?" (too many for this sheet)":""));
  }
  // PDF points at 72 dpi: 4R portrait = 102 x 152 mm; photo = 35 x 45 mm.
  PdfDocument makePdf(){
    int pageW=paperWidth(),pageH=paperHeight();
    PdfDocument doc=new PdfDocument();PdfDocument.Page page=doc.startPage(new PdfDocument.PageInfo.Builder(pageW,pageH,1).create());
    Canvas canvas=page.getCanvas();canvas.drawColor(Color.WHITE);
    drawSheet(canvas,pageW,pageH,editedPhoto(2000));
    doc.finishPage(page);return doc;
  }
  void drawSheet(Canvas canvas,int pageW,int pageH,Bitmap outputPhoto){
    if(outputPhoto==null)return;
    float w=widths[sizeIndex]*72/25.4f,h=heights[sizeIndex]*72/25.4f;
    float gapX=gapX(),gapY=gapY();
    int cols=columns();
    int rows=(photoCount+cols-1)/cols;
    int maxRows=Math.max(1,(int)Math.floor((pageH-24f+gapY)/(h+gapY)));
    int visibleCount=Math.min(photoCount,cols*maxRows);
    rows=(visibleCount+cols-1)/cols;
    float left=(pageW-(cols*w+(cols-1)*gapX))/2f;
    float blockHeight=rows*h+Math.max(0,rows-1)*gapY;
    float top=sheetPosition==0?12f:sheetPosition==2?pageH-12f-blockHeight:(pageH-blockHeight)/2f;
    Paint imagePaint=finishingPaint();
    Paint border=new Paint(3);border.setColor(Color.BLACK);border.setStrokeWidth(.65f);border.setStyle(Paint.Style.STROKE);
    for(int row=0;row<rows;row++)for(int col=0;col<cols;col++){
      if(row*cols+col>=visibleCount)continue;
      float x=left+col*(w+gapX),y=top+row*(h+gapY);
      RectF target=new RectF(x,y,x+w,y+h);
      float desired=w/h,ratio=(float)outputPhoto.getWidth()/outputPhoto.getHeight();
      int sw=outputPhoto.getWidth(),sh=outputPhoto.getHeight();
      Rect source;
      if(cropArea!=null){source=new Rect(Math.max(0,Math.round(cropArea.left*sw)),Math.max(0,Math.round(cropArea.top*sh)),Math.min(sw,Math.round(cropArea.right*sw)),Math.min(sh,Math.round(cropArea.bottom*sh)));}
      else if(ratio>desired){int cw=Math.round(sh*desired);int x0=(sw-cw)/2;source=new Rect(x0,0,x0+cw,sh);}
      else{int ch=Math.round(sw/desired);int y0=(sh-ch)/2;source=new Rect(0,y0,sw,y0+ch);}
      canvas.drawBitmap(outputPhoto,source,target,imagePaint);canvas.drawRect(target,border);
    }
  }

  void showCropEditor(){
    if(photo==null){status.setText("Select a portrait first.");return;}
    final float aspect=widths[sizeIndex]/heights[sizeIndex];
    final float imageAspect=(float)photo.getWidth()/photo.getHeight();
    final float initialW=imageAspect>aspect?aspect/imageAspect:1f;
    final float initialH=imageAspect>aspect?1f:imageAspect/aspect;
    final RectF selection=cropArea==null?new RectF((1-initialW)/2,(1-initialH)/2,(1+initialW)/2,(1+initialH)/2):new RectF(cropArea);
    final LinearLayout layout=new LinearLayout(this);layout.setOrientation(1);layout.setPadding(16,8,16,8);
    TextView help=new TextView(this);help.setText("Drag inside the frame to move it. Drag a corner to resize. The crop ratio is locked to "+widths[sizeIndex]+" x "+heights[sizeIndex]+" mm. Leave space above the hair.");layout.addView(help);
    View editor=new View(this){
      final Paint paint=new Paint(3);
      float x0,y0,displayW,displayH,downX,downY;
      RectF start=new RectF();int mode=0;
      @Override protected void onDraw(Canvas c){
        c.drawColor(Color.rgb(30,35,44));
        float fit=Math.min((getWidth()-12f)/photo.getWidth(),(getHeight()-12f)/photo.getHeight());
        displayW=photo.getWidth()*fit;displayH=photo.getHeight()*fit;
        x0=(getWidth()-displayW)/2;y0=(getHeight()-displayH)/2;
        paint.setColor(Color.WHITE);paint.setStyle(Paint.Style.FILL);
        c.drawBitmap(photo,null,new RectF(x0,y0,x0+displayW,y0+displayH),paint);
        float l=x0+selection.left*displayW,t=y0+selection.top*displayH,r=x0+selection.right*displayW,b=y0+selection.bottom*displayH;
        paint.setColor(0x99000000);c.drawRect(x0,y0,x0+displayW,t,paint);c.drawRect(x0,b,x0+displayW,y0+displayH,paint);
        c.drawRect(x0,t,l,b,paint);c.drawRect(r,t,x0+displayW,b,paint);
        paint.setColor(Color.WHITE);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3);c.drawRect(l,t,r,b,paint);
        paint.setStyle(Paint.Style.FILL);
        for(float[] p:new float[][]{{l,t},{r,t},{l,b},{r,b}}){paint.setColor(Color.rgb(0,200,255));c.drawCircle(p[0],p[1],10,paint);}
      }
      @Override public boolean onTouchEvent(android.view.MotionEvent e){
        float x=(e.getX()-x0)/Math.max(1,displayW),y=(e.getY()-y0)/Math.max(1,displayH);
        if(e.getAction()==MotionEvent.ACTION_DOWN){
          downX=x;downY=y;start.set(selection);
          float d=Float.MAX_VALUE;int nearest=0;
          float[][] corners={{selection.left,selection.top},{selection.right,selection.top},{selection.left,selection.bottom},{selection.right,selection.bottom}};
          for(int i=0;i<4;i++){float dx=(x-corners[i][0])*displayW,dy=(y-corners[i][1])*displayH;float dd=dx*dx+dy*dy;if(dd<d){d=dd;nearest=i;}}
          mode=d<55*55?nearest+2:1;return true;
        }
        if(e.getAction()==MotionEvent.ACTION_MOVE){
          if(mode==1){
            float dx=Math.max(-start.left,Math.min(1-start.right,x-downX)),dy=Math.max(-start.top,Math.min(1-start.bottom,y-downY));
            selection.set(start.left+dx,start.top+dy,start.right+dx,start.bottom+dy);
          }else{
            // Resize around the diagonally opposite fixed corner, preserving the chosen physical aspect ratio.
            boolean left=mode==2||mode==4,top=mode==2||mode==3;
            float anchorX=left?start.right:start.left,anchorY=top?start.bottom:start.top;
            float requestedW=Math.abs(x-anchorX),requestedH=Math.abs(y-anchorY);
            float normalizedRatio=aspect/imageAspect;
            float maxW=left?anchorX:1-anchorX,maxH=top?anchorY:1-anchorY;
            float newW=Math.max(.12f,Math.min(maxW,Math.min(requestedW,requestedH*normalizedRatio)));
            float newH=newW/normalizedRatio;
            if(newH>maxH){newH=maxH;newW=newH*normalizedRatio;}
            selection.set(left?anchorX-newW:anchorX,top?anchorY-newH:anchorY,left?anchorX:anchorX+newW,top?anchorY:anchorY+newH);
          }
          invalidate();return true;
        }
        return true;
      }
    };
    layout.addView(editor,new LinearLayout.LayoutParams(-1,(int)(getResources().getDisplayMetrics().heightPixels*.48f)));
    TextView dimensions=new TextView(this);dimensions.setText("Output: "+widths[sizeIndex]+" x "+heights[sizeIndex]+" mm • crop ratio locked • no stretching");layout.addView(dimensions);
    new android.app.AlertDialog.Builder(this).setTitle("Precise Passport Photo Crop").setView(layout)
      .setNegativeButton("Cancel",(d,w)->{})
      .setNeutralButton("Reset",(d,w)->{cropArea=null;updateSheet();})
      .setPositiveButton("Apply Crop",(d,w)->{cropArea=new RectF(selection);updateSheet();status.setText("Crop applied. Printed photo size remains "+widths[sizeIndex]+" x "+heights[sizeIndex]+" mm.");})
      .show();
  }
  void printSheet(){
    PrintManager manager=(PrintManager)getSystemService(PRINT_SERVICE);
    manager.print("JB Passport Photos "+paperName(),new PrintDocumentAdapter(){
      @Override public void onLayout(PrintAttributes oldA,PrintAttributes newA,android.os.CancellationSignal signal,LayoutResultCallback callback,Bundle extras){
        callback.onLayoutFinished(new PrintDocumentInfo.Builder("JB-Passport-Photos-"+paperName()+".pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(1).build(),true);
      }
      @Override public void onWrite(PageRange[] pages,ParcelFileDescriptor destination,android.os.CancellationSignal signal,WriteResultCallback callback){
        try(FileOutputStream out=new FileOutputStream(destination.getFileDescriptor())){PdfDocument doc=makePdf();doc.writeTo(out);doc.close();callback.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});}
        catch(Exception e){callback.onWriteFailed(e.getMessage());}
      }
    },new PrintAttributes.Builder().setMediaSize(paperIndex==1?PrintAttributes.MediaSize.ISO_A4:paperIndex==2?new PrintAttributes.MediaSize("JB_5X7","5 x 7 inch",5000,7000):paperIndex==3?PrintAttributes.MediaSize.ISO_A5:new PrintAttributes.MediaSize("JB_4R","4R 102 x 152 mm",4000,6000)).setColorMode(PrintAttributes.COLOR_MODE_COLOR).build());
  }
}

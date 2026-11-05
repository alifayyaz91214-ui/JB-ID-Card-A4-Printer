package pk.jb.idcardprinter;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.os.ParcelFileDescriptor;
import android.print.*;
import android.view.*;
import android.widget.*;
import android.text.*;
import java.io.*;

public class PhotoCopyActivity extends Activity {
  static final int PICK=901,SAVE=902,CAMERA=903;
  Uri cameraUri;
  boolean scanMode=true;
  Bitmap photo;
  int copies=12,black=20,white=0;
  float photoW=35f,photoH=45f;
  TextView status,summary;
  View sheet;
  EditText count;
  @Override public void onCreate(Bundle b){
    super.onCreate(b);
    ScrollView scroll=new ScrollView(this);
    LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(22,22,22,30);root.setBackgroundColor(Color.rgb(244,247,252));scroll.addView(root);setContentView(scroll);
    TextView title=new TextView(this);title.setText("A4 PHOTO COPY STUDIO");title.setTextSize(24);title.setTypeface(null,Typeface.BOLD);title.setTextColor(Color.rgb(20,48,88));root.addView(title);
    TextView sub=new TextView(this);sub.setText("One photo • multiple copies • black and white print enhancement");root.addView(sub);
    sheet=new View(this){
      Paint p=new Paint(3);
      @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);canvas.drawColor(Color.rgb(224,232,242));
        float scale=Math.min((getWidth()-20f)/595f,(getHeight()-20f)/842f);
        int saved=canvas.save();canvas.translate((getWidth()-595*scale)/2f,(getHeight()-842*scale)/2f);canvas.scale(scale,scale);
        p.setColor(Color.WHITE);canvas.drawRect(0,0,595,842,p);
        drawPhotos(canvas);canvas.restoreToCount(saved);
      }
    };
    LinearLayout.LayoutParams previewLp=new LinearLayout.LayoutParams(-1,470);previewLp.topMargin=12;previewLp.bottomMargin=12;root.addView(sheet,previewLp);
    summary=new TextView(this);summary.setTextSize(14);root.addView(summary);
    Button select=new Button(this);select.setText("Select / scan photo from gallery");root.addView(select);
    select.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_GET_CONTENT);i.setType("image/*");startActivityForResult(i,PICK);});
    Button camera=new Button(this);camera.setText("Scan using camera (full resolution)");root.addView(camera);
    camera.setOnClickListener(v->{
      try{
        android.content.ContentValues values=new android.content.ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME,"JB_Scan_"+System.currentTimeMillis()+".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");
        cameraUri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
        if(cameraUri==null)throw new IOException("Cannot create camera image");
        Intent intent=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT,cameraUri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startActivityForResult(intent,CAMERA);
      }catch(Exception e){status.setText("Camera unavailable: "+e.getMessage());}
    });
    CheckBox scan=new CheckBox(this);scan.setText("Document scan mode: crisp black text and clean white paper");scan.setChecked(true);root.addView(scan);
    scan.setOnCheckedChangeListener((button,checked)->{scanMode=checked;refresh();});
    TextView sizes=new TextView(this);sizes.setText("Photo size");sizes.setTextSize(17);root.addView(sizes);
    Spinner size=new Spinner(this);String[] labels={"35 x 45 mm","40 x 50 mm","30 x 40 mm","51 x 51 mm","25 x 35 mm"};
    size.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,labels));
    size.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
      public void onItemSelected(android.widget.AdapterView<?> a,View v,int index,long id){
        float[] widths={35,40,30,51,25},heights={45,50,40,51,35};photoW=widths[index];photoH=heights[index];refresh();
      }
      public void onNothingSelected(android.widget.AdapterView<?> a){}
    });root.addView(size);
    TextView quantity=new TextView(this);quantity.setText("Number of copies on A4");quantity.setTextSize(17);root.addView(quantity);
    count=new EditText(this);count.setSelectAllOnFocus(true);count.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);count.setSingleLine(true);count.setText("12");root.addView(count);
    count.addTextChangedListener(new TextWatcher(){
      public void beforeTextChanged(CharSequence s,int st,int n,int a){}
      public void onTextChanged(CharSequence s,int st,int before,int n){try{copies=Math.min(100,Math.max(1,Integer.parseInt(s.toString())));}catch(Exception e){copies=1;}refresh();}
      public void afterTextChanged(Editable e){}
    });
    TextView blackLabel=new TextView(this);blackLabel.setText("Black / text darkness: 20");root.addView(blackLabel);
    SeekBar blacks=new SeekBar(this);blacks.setMax(100);blacks.setProgress(20);root.addView(blacks);
    blacks.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
      public void onProgressChanged(SeekBar b,int n,boolean user){black=n;blackLabel.setText("Black / text darkness: "+n);refresh();}
      public void onStartTrackingTouch(SeekBar b){}
      public void onStopTrackingTouch(SeekBar b){}
    });
    TextView whiteLabel=new TextView(this);whiteLabel.setText("Reduce white / highlights: 0");root.addView(whiteLabel);
    SeekBar whites=new SeekBar(this);whites.setMax(80);root.addView(whites);
    whites.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
      public void onProgressChanged(SeekBar b,int n,boolean user){white=n;whiteLabel.setText("Reduce white / highlights: "+n);refresh();}
      public void onStartTrackingTouch(SeekBar b){}
      public void onStopTrackingTouch(SeekBar b){}
    });
    TextView tip=new TextView(this);tip.setText("The live preview uses the same layout and tone filter as the PDF. Excessive darkness can hide fine details.");root.addView(tip);
    Button save=new Button(this);save.setText("Save A4 PDF");root.addView(save);
    save.setOnClickListener(v->{if(!ready())return;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/pdf");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,"JB-A4-Photo-Copies.pdf");startActivityForResult(i,SAVE);});
    Button print=new Button(this);print.setText("Print A4 directly");root.addView(print);print.setOnClickListener(v->{if(ready())printSheet();});
    status=new TextView(this);status.setText("Select a photo first.");root.addView(status);refresh();
  }
  int columns(){float w=photoW*72/25.4f;return Math.max(1,(int)((595-24+10)/(w+10)));}
  int capacity(){float h=photoH*72/25.4f;return columns()*Math.max(1,(int)((842-24+10)/(h+10)));}
  void refresh(){if(sheet!=null)sheet.invalidate();if(summary!=null)summary.setText("A4 sheet: "+Math.min(copies,capacity())+" / "+copies+" copies fit • "+photoW+" x "+photoH+" mm");}
  boolean ready(){if(photo==null){status.setText("Select a photo first.");return false;}if(copies>capacity()){status.setText("Only "+capacity()+" photos fit on one A4 sheet at this size. Reduce copies.");return false;}return true;}
  Paint photoPaint(){
    Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG|Paint.DITHER_FLAG);
    float contrast=1f+black/125f;
    float offset=128f*(1f-contrast)+white*.60f-black*.38f+(scanMode?12f:0f);
    ColorMatrix gray=new ColorMatrix();gray.setSaturation(0);
    ColorMatrix tone=new ColorMatrix(new float[]{contrast,0,0,0,offset,0,contrast,0,0,offset,0,0,contrast,0,offset,0,0,0,1,0});
    gray.postConcat(tone);p.setColorFilter(new ColorMatrixColorFilter(gray));return p;
  }
  void drawPhotos(Canvas c){
    if(photo==null)return;
    float w=photoW*72/25.4f,h=photoH*72/25.4f,gap=10f;
    int cols=columns(),visible=Math.min(copies,capacity()),rows=(visible+cols-1)/cols;
    float totalW=cols*w+(cols-1)*gap,totalH=rows*h+(rows-1)*gap;
    float left=(595-totalW)/2f,top=(842-totalH)/2f;
    float ratio=w/h,sourceRatio=(float)photo.getWidth()/photo.getHeight();
    Rect source;
    if(sourceRatio>ratio){int sw=Math.round(photo.getHeight()*ratio),x=(photo.getWidth()-sw)/2;source=new Rect(x,0,x+sw,photo.getHeight());}
    else{int sh=Math.round(photo.getWidth()/ratio),y=(photo.getHeight()-sh)/2;source=new Rect(0,y,photo.getWidth(),y+sh);}
    Paint paint=photoPaint();
    for(int i=0;i<visible;i++){float x=left+(i%cols)*(w+gap),y=top+(i/cols)*(h+gap);c.drawBitmap(photo,source,new RectF(x,y,x+w,y+h),paint);}
  }
  PdfDocument makePdf(){PdfDocument doc=new PdfDocument();PdfDocument.Page page=doc.startPage(new PdfDocument.PageInfo.Builder(595,842,1).create());Canvas c=page.getCanvas();c.drawColor(Color.WHITE);drawPhotos(c);doc.finishPage(page);return doc;}
  @Override protected void onActivityResult(int request,int result,Intent data){
    super.onActivityResult(request,result,data);if(result!=RESULT_OK)return;
    Uri selected=request==CAMERA?cameraUri:(data==null?null:data.getData());
    if(selected==null)return;
    if(request==SAVE){try(OutputStream out=getContentResolver().openOutputStream(data.getData())){PdfDocument d=makePdf();d.writeTo(out);d.close();status.setText("A4 PDF saved.");}catch(Exception e){status.setText("Save error: "+e.getMessage());}return;}
    if(request==PICK||request==CAMERA){try{
      BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;
      try(InputStream in=getContentResolver().openInputStream(selected)){BitmapFactory.decodeStream(in,null,o);}
      o.inSampleSize=1;while(Math.max(o.outWidth/o.inSampleSize,o.outHeight/o.inSampleSize)>2600)o.inSampleSize*=2;o.inJustDecodeBounds=false;
      try(InputStream in=getContentResolver().openInputStream(selected)){photo=BitmapFactory.decodeStream(in,null,o);}
      if(photo==null)throw new IOException("Cannot read photo");
      // Respect camera/gallery EXIF orientation before composing the A4 sheet.
      try(InputStream exifStream=getContentResolver().openInputStream(selected)){
        android.media.ExifInterface exif=new android.media.ExifInterface(exifStream);
        int orientation=exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1);
        int degrees=orientation==6?90:(orientation==3?180:(orientation==8?270:0));
        if(degrees!=0){Matrix m=new Matrix();m.postRotate(degrees);photo=Bitmap.createBitmap(photo,0,0,photo.getWidth(),photo.getHeight(),m,true);}
      }catch(Exception ignored){}
      status.setText("High-resolution image loaded. Adjust scan darkness and check live A4 preview.");refresh();
    }catch(Exception e){status.setText("Photo error: "+e.getMessage());}}
  }
  void printSheet(){
    PrintManager m=(PrintManager)getSystemService(PRINT_SERVICE);
    m.print("JB A4 Photo Copies",new PrintDocumentAdapter(){
      @Override public void onLayout(PrintAttributes oldA,PrintAttributes newA,android.os.CancellationSignal signal,LayoutResultCallback cb,Bundle extras){
        cb.onLayoutFinished(new PrintDocumentInfo.Builder("JB-A4-Photo-Copies.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(1).build(),true);
      }
      @Override public void onWrite(PageRange[] pages,ParcelFileDescriptor dest,android.os.CancellationSignal signal,WriteResultCallback cb){
        try(FileOutputStream out=new FileOutputStream(dest.getFileDescriptor())){PdfDocument d=makePdf();d.writeTo(out);d.close();cb.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});}
        catch(Exception e){cb.onWriteFailed(e.getMessage());}
      }
    },new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME).build());
  }
}

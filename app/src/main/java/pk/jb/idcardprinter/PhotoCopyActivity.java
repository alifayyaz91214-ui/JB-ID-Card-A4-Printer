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
  int copies=1,black=20,white=0;
  float photoW=35f,photoH=45f;
  TextView status,summary;
  View sheet;
  EditText count;
  @Override public void onCreate(Bundle b){
    super.onCreate(b);
    ScrollView scroll=new ScrollView(this);
    LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(22,22,22,30);root.setBackgroundColor(Color.rgb(244,247,252));scroll.addView(root);setContentView(scroll);
    TextView title=new TextView(this);title.setText("A4 PHOTO COPY STUDIO");title.setTextSize(24);title.setTypeface(null,Typeface.BOLD);title.setTextColor(Color.rgb(20,48,88));root.addView(title);
    TextView sub=new TextView(this);sub.setText("Full A4 document scanning • adjustable print quality • multiple pages");root.addView(sub);
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
    Button select=new Button(this);select.setText("Select A4 document from gallery");root.addView(select);
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
    TextView sizeInfo=new TextView(this);sizeInfo.setText("Paper: A4 (210 x 297 mm) • Full-page document • 1 document per page");sizeInfo.setTextSize(16);root.addView(sizeInfo);
    TextView quantity=new TextView(this);quantity.setText("Number of full A4 copies / pages");quantity.setTextSize(17);root.addView(quantity);
    count=new EditText(this);count.setSelectAllOnFocus(true);count.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);count.setSingleLine(true);count.setText("1");root.addView(count);
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
    TextView tip=new TextView(this);tip.setText("One document fills each A4 page with small printer-safe margins. Copies create separate full-size pages. Preview and PDF use identical settings.");root.addView(tip);
    Button save=new Button(this);save.setText("Save A4 PDF");root.addView(save);
    save.setOnClickListener(v->{if(!ready())return;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/pdf");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,"JB-A4-Photo-Copies.pdf");startActivityForResult(i,SAVE);});
    Button print=new Button(this);print.setText("Print A4 directly");root.addView(print);print.setOnClickListener(v->{if(ready())printSheet();});
    status=new TextView(this);status.setText("Select a photo first.");root.addView(status);refresh();
  }
  void refresh(){
    if(sheet!=null)sheet.invalidate();
    if(summary!=null)summary.setText("A4 (210 x 297 mm) • Full-page preview • "+copies+" complete page"+(copies==1?"":"s"));
  }
  boolean ready(){
    if(photo==null){status.setText("Select a document photo first.");return false;}
    if(copies<1||copies>100){status.setText("Enter 1 to 100 A4 pages.");return false;}
    return true;
  }
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
    // Fill as much of A4 as possible without cutting or distorting the document.
    // The 8pt safety margin protects edges on ordinary office printers.
    final float margin=8f,availableW=595f-2*margin,availableH=842f-2*margin;
    float scale=Math.min(availableW/photo.getWidth(),availableH/photo.getHeight());
    float w=photo.getWidth()*scale,h=photo.getHeight()*scale;
    float left=(595f-w)/2f,top=(842f-h)/2f;
    c.drawBitmap(photo,null,new RectF(left,top,left+w,top+h),photoPaint());
  }
  PdfDocument makePdf(){
    PdfDocument doc=new PdfDocument();
    for(int i=0;i<copies;i++){
      PdfDocument.Page page=doc.startPage(new PdfDocument.PageInfo.Builder(595,842,i+1).create());
      Canvas c=page.getCanvas();c.drawColor(Color.WHITE);drawPhotos(c);doc.finishPage(page);
    }
    return doc;
  }
  @Override protected void onActivityResult(int request,int result,Intent data){
    super.onActivityResult(request,result,data);if(result!=RESULT_OK)return;
    Uri selected=request==CAMERA?cameraUri:(data==null?null:data.getData());
    if(selected==null)return;
    if(request==SAVE){try(OutputStream out=getContentResolver().openOutputStream(data.getData())){PdfDocument d=makePdf();d.writeTo(out);d.close();status.setText("A4 PDF saved.");}catch(Exception e){status.setText("Save error: "+e.getMessage());}return;}
    if(request==PICK||request==CAMERA){
      status.setText("Loading document safely...");
      final Uri imageUri=selected;
      new Thread(()->{
        Bitmap decoded=null;
        String error=null;
        try{
          BitmapFactory.Options o=new BitmapFactory.Options();
          o.inJustDecodeBounds=true;
          try(InputStream in=getContentResolver().openInputStream(imageUri)){BitmapFactory.decodeStream(in,null,o);}
          if(o.outWidth<=0||o.outHeight<=0)throw new IOException("Invalid image dimensions");
          o.inSampleSize=1;
          while(Math.max(o.outWidth/o.inSampleSize,o.outHeight/o.inSampleSize)>2200)o.inSampleSize*=2;
          o.inJustDecodeBounds=false;o.inPreferredConfig=Bitmap.Config.RGB_565;
          try(InputStream in=getContentResolver().openInputStream(imageUri)){decoded=BitmapFactory.decodeStream(in,null,o);}
          if(decoded==null)throw new IOException("Cannot decode image");
          try(InputStream exifStream=getContentResolver().openInputStream(imageUri)){
            android.media.ExifInterface exif=new android.media.ExifInterface(exifStream);
            int orientation=exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1);
            int degrees=orientation==6?90:(orientation==3?180:(orientation==8?270:0));
            if(degrees!=0){Matrix matrix=new Matrix();matrix.postRotate(degrees);
              Bitmap rotated=Bitmap.createBitmap(decoded,0,0,decoded.getWidth(),decoded.getHeight(),matrix,true);
              if(rotated!=decoded)decoded.recycle();decoded=rotated;
            }
          }catch(Exception ignored){}
        }catch(Exception e){error=e.getMessage();}
        final Bitmap resultBitmap=decoded;final String resultError=error;
        runOnUiThread(()->{
          if(isFinishing()||isDestroyed()){if(resultBitmap!=null)resultBitmap.recycle();return;}
          if(resultError!=null){if(resultBitmap!=null)resultBitmap.recycle();status.setText("Photo error: "+resultError);return;}
          Bitmap old=photo;photo=resultBitmap;
          if(old!=null&&old!=resultBitmap)old.recycle();
          status.setText("Document loaded. Adjust scan settings and check A4 preview.");
          refresh();
        });
      }).start();
    }
  }

  void printSheet(){
    PrintManager m=(PrintManager)getSystemService(PRINT_SERVICE);
    m.print("JB A4 Photo Copies",new PrintDocumentAdapter(){
      @Override public void onLayout(PrintAttributes oldA,PrintAttributes newA,android.os.CancellationSignal signal,LayoutResultCallback cb,Bundle extras){
        cb.onLayoutFinished(new PrintDocumentInfo.Builder("JB-A4-Photo-Copies.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(copies).build(),true);
      }
      @Override public void onWrite(PageRange[] pages,ParcelFileDescriptor dest,android.os.CancellationSignal signal,WriteResultCallback cb){
        try(FileOutputStream out=new FileOutputStream(dest.getFileDescriptor())){PdfDocument d=makePdf();d.writeTo(out);d.close();cb.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});}
        catch(Exception e){cb.onWriteFailed(e.getMessage());}
      }
    },new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME).build());
  }
}

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
  int sizeIndex=0;
  boolean a4=false;
  int brightness=0;
  int contrast=100;
  int smoothing=0;
  int backgroundChoice=0; // 0 original, 1 white, 2 blue, 3 gray
  final String[] sizeNames={"Pakistan commonly used / UK - 35 x 45 mm","USA / India - 51 x 51 mm","EU common - 35 x 45 mm","Custom - 40 x 50 mm","Custom - 30 x 40 mm"};
  final float[] widths={35,51,35,40,30}, heights={45,51,45,50,40};
  ImageView preview;
  TextView status;
  @Override public void onCreate(Bundle b){
    super.onCreate(b);
    LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(28,30,28,36);root.setBackgroundColor(Color.rgb(244,247,252));
    ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
    TextView title=new TextView(this);title.setText("JB Passport Photo Maker\n1, 2, 4 or 6 photos on 4R (102 x 152 mm)");title.setTextSize(25);title.setTextColor(Color.rgb(21,38,65));title.setTypeface(null,android.graphics.Typeface.BOLD);title.setGravity(Gravity.CENTER);root.addView(title);
    Button pick=new Button(this);pick.setText("Select portrait from gallery");root.addView(pick);
    pick.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_GET_CONTENT);i.setType("image/*");startActivityForResult(i,PICK);});
    preview=new ImageView(this);preview.setAdjustViewBounds(true);preview.setMinimumHeight(200);root.addView(preview);
    TextView note=new TextView(this);note.setText("Choose 1, 2, 4 or 6 portraits (35 x 45 mm each), with white cutting gaps and thin borders. Use a front-facing, properly lit photo with a plain background. Crop is centered; review before printing.");root.addView(note);
    TextView sizeLabel=new TextView(this);sizeLabel.setText("Photo size preset (confirm official application rules)");root.addView(sizeLabel);
    Spinner sizes=new Spinner(this);ArrayAdapter<String> sizeAdapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,sizeNames);sizes.setAdapter(sizeAdapter);
    sizes.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
      public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){sizeIndex=position;}
      public void onNothingSelected(android.widget.AdapterView<?> parent){}
    });root.addView(sizes);
    TextView countLabel=new TextView(this);countLabel.setText("Photos per 4R sheet");countLabel.setTextSize(17);root.addView(countLabel);
    RadioGroup countGroup=new RadioGroup(this);countGroup.setOrientation(RadioGroup.HORIZONTAL);
    int[] counts={1,2,4,6};
    for(int count:counts){RadioButton rb=new RadioButton(this);rb.setText(""+count);rb.setId(500+count);countGroup.addView(rb);}
    countGroup.check(506);countGroup.setOnCheckedChangeListener((g,id)->photoCount=id-500);root.addView(countGroup);
    TextView paperLabel=new TextView(this);paperLabel.setText("Paper size");paperLabel.setTextSize(17);root.addView(paperLabel);
    RadioGroup paper=new RadioGroup(this);paper.setOrientation(RadioGroup.HORIZONTAL);
    RadioButton fourR=new RadioButton(this);fourR.setId(710);fourR.setText("4R (4 x 6 inch)");paper.addView(fourR);
    RadioButton a4Option=new RadioButton(this);a4Option.setId(711);a4Option.setText("A4");paper.addView(a4Option);
    paper.check(710);paper.setOnCheckedChangeListener((g,id)->a4=id==711);root.addView(paper);
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
    TextView fitting=new TextView(this);fitting.setText("Larger sizes may fit fewer photos on 4R. The PDF will never shrink the selected physical photo size.");root.addView(fitting);
    Button pdf=new Button(this);pdf.setText("Save 4R PDF");root.addView(pdf);
    pdf.setOnClickListener(v->{if(!ready())return;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/pdf");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,a4?"JB-Passport-Photos-A4.pdf":"JB-Passport-Photos-4R.pdf");startActivityForResult(i,SAVE);});
    Button print=new Button(this);print.setText("Print 4R directly");root.addView(print);print.setOnClickListener(v->{if(ready())printSheet();});
    status=new TextView(this);status.setText("Select a photo to begin.");root.addView(status);
  }
  boolean ready(){
    if(photo==null){status.setText("Select a portrait first.");return false;}
    float w=widths[sizeIndex]*72/25.4f,h=heights[sizeIndex]*72/25.4f;
    float gapX=14f,gapY=photoCount==6?9f:14f;
    int cols=2*w+gapX<=277f?2:1;
    int rows=(photoCount+cols-1)/cols;
    float pageW=a4?595f:289f,pageH=a4?842f:431f;
    cols=(int)Math.max(1,Math.floor((pageW-24f+gapX)/(w+gapX)));
    rows=(photoCount+cols-1)/cols;
    if(rows*h+(rows-1)*gapY>pageH-24f){
      status.setText("Selected quantity does not fit on 4R at this size. Choose fewer photos. Image dimensions will not be reduced.");
      return false;
    }
    return true;
  }
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
    preview.setImageBitmap(output);
  }
  // PDF points at 72 dpi: 4R portrait = 102 x 152 mm; photo = 35 x 45 mm.
  PdfDocument makePdf(){
    int pageW=a4?595:289,pageH=a4?842:431;
    PdfDocument doc=new PdfDocument();PdfDocument.Page page=doc.startPage(new PdfDocument.PageInfo.Builder(pageW,pageH,1).create());
    Canvas canvas=page.getCanvas();canvas.drawColor(Color.WHITE);
    float w=widths[sizeIndex]*72/25.4f,h=heights[sizeIndex]*72/25.4f;
    float gapX=14f,gapY=photoCount==6?9f:14f;
    int cols=Math.max(1,(int)Math.floor((pageW-24f+gapX)/(w+gapX)));
    int rows=(photoCount+cols-1)/cols;
    int maxRows=Math.max(1,(int)Math.floor((pageH-12f+gapY)/(h+gapY)));
    int visibleCount=Math.min(photoCount,cols*maxRows);
    rows=(visibleCount+cols-1)/cols;
    float left=(pageW-(cols*w+(cols-1)*gapX))/2f,top=(pageH-(rows*h+(rows-1)*gapY))/2f;
    Bitmap outputPhoto=editedPhoto(2000);
    Paint imagePaint=finishingPaint();
    Paint border=new Paint(3);border.setColor(Color.BLACK);border.setStrokeWidth(.65f);border.setStyle(Paint.Style.STROKE);
    for(int row=0;row<rows;row++)for(int col=0;col<cols;col++){
      if(row*cols+col>=visibleCount)continue;
      float x=left+col*(w+gapX),y=top+row*(h+gapY);
      RectF target=new RectF(x,y,x+w,y+h);
      float desired=w/h,ratio=(float)outputPhoto.getWidth()/outputPhoto.getHeight();
      int sw=outputPhoto.getWidth(),sh=outputPhoto.getHeight();
      Rect source;
      if(ratio>desired){int cw=Math.round(sh*desired);int x0=(sw-cw)/2;source=new Rect(x0,0,x0+cw,sh);}
      else{int ch=Math.round(sw/desired);int y0=(sh-ch)/2;source=new Rect(0,y0,sw,y0+ch);}
      canvas.drawBitmap(outputPhoto,source,target,imagePaint);canvas.drawRect(target,border);
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
    },new PrintAttributes.Builder().setMediaSize(a4?PrintAttributes.MediaSize.ISO_A4:new PrintAttributes.MediaSize("JB_4R","4R 102 x 152 mm",4016,5984)).setColorMode(PrintAttributes.COLOR_MODE_COLOR).build());
  }
}

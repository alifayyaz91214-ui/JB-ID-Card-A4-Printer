package pk.jb.idcardprinter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.*;
import android.graphics.pdf.*;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

public class PdfEditorActivity extends Activity {
  static final int OPEN=801,SAVE=802;
  Uri sourceUri;
  ParcelFileDescriptor descriptor;
  PdfRenderer renderer;
  int pageIndex=0;
  Bitmap pageBitmap;
  ImageView preview;
  TextView info;
  EditText textInput;
  Spinner language;
  SeekBar sizeBar;
  float textX=.1f,textY=.2f;
  final ArrayList<Overlay> overlays=new ArrayList<>();
  static class Overlay {int page;String text;float x,y;int size,lang;Overlay(int p,String t,float a,float b,int s,int l){page=p;text=t;x=a;y=b;size=s;lang=l;}}
  @Override public void onCreate(Bundle b){
    super.onCreate(b);
    ScrollView scroll=new ScrollView(this);
    LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(22,22,22,32);root.setBackgroundColor(Color.rgb(245,248,252));scroll.addView(root);setContentView(scroll);
    TextView title=new TextView(this);title.setText("JB PDF EDITOR STUDIO");title.setTextSize(25);title.setTypeface(null,Typeface.BOLD);root.addView(title);
    TextView notice=new TextView(this);notice.setText("Offline PDF page viewer and multilingual text overlay. This version does not modify existing embedded text or replicate original fonts.");root.addView(notice);
    Button open=new Button(this);open.setText("OPEN PDF");root.addView(open);open.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("application/pdf");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,OPEN);});
    info=new TextView(this);info.setText("Select a PDF to begin.");root.addView(info);
    preview=new ImageView(this);preview.setAdjustViewBounds(true);preview.setMinimumHeight(320);preview.setBackgroundColor(Color.LTGRAY);root.addView(preview);
    TextView hint=new TextView(this);hint.setText("Tap the page to choose where new text appears.");root.addView(hint);
    preview.setOnTouchListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_UP&&pageBitmap!=null){float dw=preview.getWidth(),dh=preview.getHeight();float scale=Math.min(dw/pageBitmap.getWidth(),dh/pageBitmap.getHeight());float w=pageBitmap.getWidth()*scale,h=pageBitmap.getHeight()*scale;float left=(dw-w)/2,top=(dh-h)/2;textX=Math.max(0,Math.min(1,(e.getX()-left)/w));textY=Math.max(0,Math.min(1,(e.getY()-top)/h));info.setText("Text position selected. Page "+(pageIndex+1));return true;}return true;});
    LinearLayout nav=new LinearLayout(this);nav.setOrientation(LinearLayout.HORIZONTAL);root.addView(nav);
    Button prev=new Button(this);prev.setText("PREVIOUS");nav.addView(prev,new LinearLayout.LayoutParams(0,-2,1));prev.setOnClickListener(v->{if(renderer!=null&&pageIndex>0){pageIndex--;render();}});
    Button next=new Button(this);next.setText("NEXT");nav.addView(next,new LinearLayout.LayoutParams(0,-2,1));next.setOnClickListener(v->{if(renderer!=null&&pageIndex+1<renderer.getPageCount()){pageIndex++;render();}});
    language=new Spinner(this);language.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"English","Urdu / اردو","Hindi / हिन्दी"}));root.addView(language);
    textInput=new EditText(this);textInput.setHint("Type text to add");textInput.setMinLines(2);root.addView(textInput);
    TextView sizeLabel=new TextView(this);sizeLabel.setText("Text size");root.addView(sizeLabel);
    sizeBar=new SeekBar(this);sizeBar.setMax(64);sizeBar.setProgress(20);root.addView(sizeBar);
    Button add=new Button(this);add.setText("ADD TEXT TO CURRENT PAGE");root.addView(add);add.setOnClickListener(v->{if(renderer==null)return;String t=textInput.getText().toString();if(t.trim().isEmpty())return;overlays.add(new Overlay(pageIndex,t,textX,textY,Math.max(10,sizeBar.getProgress()+8),language.getSelectedItemPosition()));render();});
    Button undo=new Button(this);undo.setText("UNDO LAST TEXT");root.addView(undo);undo.setOnClickListener(v->{if(!overlays.isEmpty()){overlays.remove(overlays.size()-1);render();}});
    Button save=new Button(this);save.setText("SAVE EDITED COPY AS PDF");root.addView(save);save.setOnClickListener(v->{if(renderer==null)return;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/pdf");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,"JB-Edited-Document.pdf");startActivityForResult(i,SAVE);});
  }
  void closePdf(){if(pageBitmap!=null){pageBitmap.recycle();pageBitmap=null;}if(renderer!=null){renderer.close();renderer=null;}if(descriptor!=null){try{descriptor.close();}catch(Exception ignored){}descriptor=null;}}
  @Override protected void onDestroy(){closePdf();super.onDestroy();}
  @Override protected void onActivityResult(int req,int result,Intent data){
    super.onActivityResult(req,result,data);
    if(result!=RESULT_OK||data==null||data.getData()==null)return;
    if(req==OPEN){try{closePdf();sourceUri=data.getData();descriptor=getContentResolver().openFileDescriptor(sourceUri,"r");renderer=new PdfRenderer(descriptor);pageIndex=0;overlays.clear();render();}catch(Exception e){info.setText("Cannot open PDF: "+e.getMessage());}}
    if(req==SAVE){final Uri destination=data.getData();info.setText("Saving edited PDF...");new Thread(()->{String error=null;try(OutputStream out=getContentResolver().openOutputStream(destination)){PdfDocument doc=new PdfDocument();for(int i=0;i<renderer.getPageCount();i++){try(PdfRenderer.Page page=renderer.openPage(i)){int w=page.getWidth(),h=page.getHeight();Bitmap bmp=Bitmap.createBitmap(w*2,h*2,Bitmap.Config.RGB_565);bmp.eraseColor(Color.WHITE);page.render(bmp,null,null,PdfRenderer.Page.RENDER_MODE_FOR_PRINT);PdfDocument.Page dst=doc.startPage(new PdfDocument.PageInfo.Builder(w,h,i+1).create());Canvas c=dst.getCanvas();c.drawColor(Color.WHITE);c.drawBitmap(bmp,null,new RectF(0,0,w,h),new Paint(3));drawOverlays(c,i,w,h);doc.finishPage(dst);bmp.recycle();}}doc.writeTo(out);doc.close();}catch(Exception e){error=e.getMessage();}final String msg=error==null?"Edited PDF saved (rasterized pages).":"PDF save failed: "+error;runOnUiThread(()->info.setText(msg));}).start();}
  }
  void drawOverlays(Canvas c,int page,float w,float h){
    Paint p=new Paint(3);p.setColor(Color.BLACK);
    for(Overlay o:overlays)if(o.page==page){
      p.setTextSize(o.size);p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
      // TextView provides Android's complex-script shaping for Arabic and Devanagari.
      android.text.TextPaint tp=new android.text.TextPaint(p);
      android.text.StaticLayout layout=android.text.StaticLayout.Builder.obtain(o.text,0,o.text.length(),tp,Math.max(20,(int)(w-o.x*w-8))).setAlignment(o.lang==1?android.text.Layout.Alignment.ALIGN_OPPOSITE:android.text.Layout.Alignment.ALIGN_NORMAL).build();
      c.save();c.translate(o.x*w,Math.max(0,o.y*h-layout.getHeight()));layout.draw(c);c.restore();
    }
  }
  void render(){
    if(renderer==null)return;
    try(PdfRenderer.Page page=renderer.openPage(pageIndex)){
      int w=page.getWidth(),h=page.getHeight();int factor=Math.max(1,Math.min(2,1800/Math.max(w,h)));
      Bitmap bmp=Bitmap.createBitmap(w*factor,h*factor,Bitmap.Config.RGB_565);bmp.eraseColor(Color.WHITE);
      page.render(bmp,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
      Bitmap edited=Bitmap.createBitmap(bmp.getWidth(),bmp.getHeight(),Bitmap.Config.ARGB_8888);Canvas c=new Canvas(edited);c.drawBitmap(bmp,0,0,null);c.save();c.scale(factor,factor);drawOverlays(c,pageIndex,w,h);c.restore();bmp.recycle();
      if(pageBitmap!=null)pageBitmap.recycle();pageBitmap=edited;preview.setImageBitmap(edited);info.setText("Page "+(pageIndex+1)+" of "+renderer.getPageCount());
    }catch(Exception e){info.setText("Page preview error: "+e.getMessage());}
  }
}

package pk.jb.idcardprinter;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.*;

public class HomeActivity extends Activity {
  int navy=Color.rgb(20,40,70),blue=Color.rgb(25,103,205);
  GradientDrawable shape(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(26);return d;}
  TextView text(String value,int size,boolean bold,int color){
    TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(null,Typeface.BOLD);return t;
  }
  class BrandBanner extends View {
    Paint p=new Paint(3);
    BrandBanner(){super(HomeActivity.this);}
    void label(Canvas c,String str,int color,int size,boolean bold,float x,float y){
      p.setColor(color);p.setTextSize(size);p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));c.drawText(str,x,y,p);
    }
    @Override protected void onDraw(Canvas c){
      super.onDraw(c);
      float w=getWidth(),h=getHeight();
      p.setColor(Color.WHITE);c.drawRoundRect(new RectF(0,0,w,h),18,18,p);
      p.setColor(Color.rgb(16,48,125));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);c.drawRoundRect(new RectF(2,2,w-2,h-2),18,18,p);p.setStyle(Paint.Style.FILL);
      float unit=Math.min(w/350f,h/104f),left=(w-350*unit)/2f,top=(h-104*unit)/2f;
      int save=c.save();c.translate(left,top);c.scale(unit,unit);
      p.setColor(Color.rgb(18,49,124));c.drawRoundRect(new RectF(12,12,78,75),12,12,p);
      label(c,"JB",Color.WHITE,37,true,20,56);
      label(c,"JB",Color.rgb(201,19,38),29,true,90,37);
      label(c,"Mobile & PhotoState",Color.rgb(17,48,119),19,true,143,36);
      label(c,"03061427341",Color.rgb(188,23,39),24,true,92,66);
      p.setColor(Color.rgb(226,31,47));c.drawRoundRect(new RectF(12,80,338,83),2,2,p);
      label(c,"PHOTO PRINT  •  PASSPORT  •  MOBILE  •  ACCESSORIES",Color.rgb(17,48,119),9,true,23,98);
      c.restoreToCount(save);
    }
  }
  class ServiceArt extends View {
    boolean idCard;Paint p=new Paint(3);
    ServiceArt(boolean card){super(HomeActivity.this);idCard=card;}
    void rect(Canvas c,int color,float l,float t,float r,float b,float rad){p.setColor(color);p.setStyle(Paint.Style.FILL);c.drawRoundRect(new RectF(l,t,r,b),rad,rad,p);}
    @Override protected void onDraw(Canvas actual){
      super.onDraw(actual);Canvas c=actual;int saved=c.save();
      float scale=Math.min(getWidth()/320f,getHeight()/180f);c.translate((getWidth()-320f*scale)/2f,(getHeight()-180f*scale)/2f);c.scale(scale,scale);
      rect(c,Color.rgb(233,241,250),0,0,320,180,16);
      if(idCard){
        // Two realistic card-format mockups, without personal identifiers or official insignia.
        rect(c,Color.rgb(192,207,220),27,30,207,145,12);
        rect(c,Color.WHITE,22,24,202,139,12);
        rect(c,Color.rgb(23,112,85),22,24,202,48,9);
        rect(c,Color.rgb(207,230,218),34,57,80,120,5);
        p.setColor(Color.rgb(69,122,102));c.drawCircle(57,76,12,p);
        rect(c,Color.rgb(69,122,102),41,89,73,113,9);
        for(int i=0;i<4;i++)rect(c,Color.rgb(157,175,190),91,63+i*14,184-i*10,68+i*14,2);
        rect(c,Color.rgb(202,216,230),169,53,289,135,10);
        rect(c,Color.WHITE,174,47,294,129,10);
        rect(c,Color.rgb(22,107,84),174,47,294,64,8);
        for(int i=0;i<4;i++)rect(c,Color.rgb(157,175,190),186,75+i*11,281-i*9,79+i*11,2);
      }else{
        rect(c,Color.WHITE,28,20,125,156,8);
        rect(c,Color.rgb(209,226,244),36,28,117,148,5);
        rect(c,Color.WHITE,111,12,213,161,8);
        rect(c,Color.rgb(205,226,246),119,20,205,153,5);
        rect(c,Color.WHITE,200,20,296,156,8);
        rect(c,Color.rgb(209,226,244),208,28,288,148,5);
        p.setColor(Color.rgb(49,62,82));c.drawOval(new RectF(144,40,182,79),p);
        p.setColor(Color.rgb(233,183,147));c.drawOval(new RectF(148,48,178,90),p);
        p.setColor(Color.rgb(34,71,110));c.drawRoundRect(new RectF(130,91,196,147),21,21,p);
        p.setColor(Color.rgb(49,62,82));c.drawOval(new RectF(54,46,96,81),p);
        p.setColor(Color.rgb(233,183,147));c.drawOval(new RectF(58,53,92,91),p);
        p.setColor(Color.rgb(34,71,110));c.drawRoundRect(new RectF(43,94,107,142),19,19,p);
        p.setColor(Color.rgb(49,62,82));c.drawOval(new RectF(227,46,269,81),p);
        p.setColor(Color.rgb(233,183,147));c.drawOval(new RectF(231,53,265,91),p);
        p.setColor(Color.rgb(34,71,110));c.drawRoundRect(new RectF(216,94,280,142),19,19,p);
      }
      c.restoreToCount(saved);
    }
  }
  void service(LinearLayout root,String symbol,String title,String desc,Class<?> destination){
    LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(20,20,20,20);box.setBackground(shape(Color.WHITE));box.setElevation(5);
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=18;root.addView(box,lp);
    ServiceArt illustration=new ServiceArt(destination==MainActivity.class||destination==SingleCardActivity.class);
    LinearLayout.LayoutParams artLp=new LinearLayout.LayoutParams(-1,160);artLp.bottomMargin=14;box.addView(illustration,artLp);
    TextView heading=text(symbol+"   "+title,20,true,navy);box.addView(heading);
    TextView detail=text(desc,14,false,Color.rgb(92,108,130));detail.setPadding(0,12,0,18);box.addView(detail);
    Button open=new Button(this);open.setText("Continue  →");open.setAllCaps(false);open.setTextSize(17);open.setTypeface(null,Typeface.BOLD);open.setPadding(12,14,12,14);open.setTextColor(Color.WHITE);open.setBackground(shape(blue));box.addView(open);
    open.setOnClickListener(v->startActivity(new Intent(this,destination)));
  }
  @Override public void onCreate(Bundle b){
    super.onCreate(b);
    ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
    LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(18,24,18,34);root.setBackgroundColor(Color.rgb(245,248,253));scroll.addView(root);
    BrandBanner banner=new BrandBanner();
    LinearLayout.LayoutParams bannerLp=new LinearLayout.LayoutParams(-1,125);bannerLp.bottomMargin=14;root.addView(banner,bannerLp);
    TextView title=text("Your print studio",27,true,navy);title.setPadding(0,4,0,8);root.addView(title);
    TextView sub=text("Select a service below to scan, edit and print.",15,false,Color.rgb(92,108,130));sub.setPadding(0,0,0,20);root.addView(sub);
    service(root,"01","Single ID Card Printer","One ID card: Front + Back, crop and print on A4.",SingleCardActivity.class);
    service(root,"02","Multiple ID Card Printer","Two to four ID cards: auto-detect, crop and mix copies on A4.",MainActivity.class);
    service(root,"03","Passport Photo Studio","Make passport photos in the correct size.",PassportActivity.class);
    service(root,"04","A4 Photo Copy Studio","Scan documents and print clean A4 copies.",PhotoCopyActivity.class);
    TextView footer=text("LOCAL PROCESSING  •  NO ACCOUNT REQUIRED",12,true,Color.rgb(92,108,130));footer.setGravity(Gravity.CENTER);footer.setPadding(0,18,0,0);root.addView(footer);
    setContentView(scroll);
  }
}

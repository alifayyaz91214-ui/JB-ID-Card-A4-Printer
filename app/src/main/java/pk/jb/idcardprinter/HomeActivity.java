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
  int navy=Color.rgb(21,38,65),blue=Color.rgb(32,104,218);
  GradientDrawable shape(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(28);return d;}
  TextView text(String value,int size,boolean bold,int color){
    TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(null,Typeface.BOLD);return t;
  }
  class ServiceArt extends View {
    boolean idCard;Paint p=new Paint(3);
    ServiceArt(boolean card){super(HomeActivity.this);idCard=card;}
    void rect(Canvas c,int color,float l,float t,float r,float b,float rad){p.setColor(color);p.setStyle(Paint.Style.FILL);c.drawRoundRect(new RectF(l,t,r,b),rad,rad,p);}
    @Override protected void onDraw(Canvas actual){
      super.onDraw(actual);Canvas c=actual;int saved=c.save();
      float sx=getWidth()/320f,sy=getHeight()/140f;c.scale(sx,sy);
      rect(c,Color.rgb(233,241,250),0,0,320,140,16);
      if(idCard){
        // Illustrative Pakistani-style identity card; no official logo or personal details.
        rect(c,Color.WHITE,40,15,280,126,10);
        rect(c,Color.rgb(21,113,83),40,15,280,39,10);
        rect(c,Color.rgb(204,231,217),53,49,104,110,6);
        p.setColor(Color.rgb(52,115,89));c.drawCircle(78,68,13,p);
        rect(c,Color.rgb(52,115,89),60,83,96,105,8);
        for(int i=0;i<4;i++)rect(c,Color.rgb(160,180,195),120,55+i*15,260-i*15,61+i*15,3);
        rect(c,Color.rgb(21,113,83),210,104,260,111,3);
      }else{
        rect(c,Color.WHITE,87,8,233,132,8);
        rect(c,Color.rgb(207,224,243),96,17,224,121,6);
        p.setColor(Color.rgb(238,197,169));c.drawOval(new RectF(139,37,181,83),p);
        p.setColor(Color.rgb(40,53,69));c.drawArc(new RectF(137,30,183,67),180,180,false,p);
        rect(c,Color.rgb(37,74,112),117,84,203,121,23);
        rect(c,Color.WHITE,153,84,167,109,3);
      }
      c.restoreToCount(saved);
    }
  }
  void service(LinearLayout root,String symbol,String title,String desc,Class<?> destination){
    LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(30,24,30,24);box.setBackground(shape(Color.WHITE));
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=22;root.addView(box,lp);
    ServiceArt illustration=new ServiceArt(destination==MainActivity.class);
    LinearLayout.LayoutParams artLp=new LinearLayout.LayoutParams(-1,190);artLp.bottomMargin=16;box.addView(illustration,artLp);
    TextView heading=text(symbol+"   "+title,20,true,navy);box.addView(heading);
    TextView detail=text(desc,14,false,Color.rgb(92,108,130));detail.setPadding(0,12,0,18);box.addView(detail);
    Button open=new Button(this);open.setText("Open studio");open.setAllCaps(false);open.setTextColor(Color.WHITE);open.setBackground(shape(blue));box.addView(open);
    open.setOnClickListener(v->startActivity(new Intent(this,destination)));
  }
  @Override public void onCreate(Bundle b){
    super.onCreate(b);
    ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
    LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(28,48,28,40);root.setBackgroundColor(Color.rgb(244,247,252));scroll.addView(root);
    TextView brand=text("JB  /  MOBILE & PHOTOSTATE",13,true,blue);root.addView(brand);
    TextView title=text("Your Print Studio",32,true,navy);title.setPadding(0,28,0,12);root.addView(title);
    TextView sub=text("Professional ID copies and passport photos, prepared on your phone.",15,false,Color.rgb(92,108,130));sub.setPadding(0,0,0,34);root.addView(sub);
    service(root,"01","ID Card A4 Printer","Auto crop, corner adjustment, print enhancement and 1-5 front/back copies.",MainActivity.class);
    service(root,"02","Passport Photo Studio","Country size presets, 1-6 prints, cutting margins and 4R PDF.",PassportActivity.class);
    TextView footer=text("LOCAL PROCESSING  •  NO ACCOUNT REQUIRED",12,true,Color.rgb(92,108,130));footer.setGravity(Gravity.CENTER);footer.setPadding(0,18,0,0);root.addView(footer);
    setContentView(scroll);
  }
}

package pk.jb.idcardprinter;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.graphics.Color;
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
  void service(LinearLayout root,String symbol,String title,String desc,Class<?> destination){
    LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(30,24,30,24);box.setBackground(shape(Color.WHITE));
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=22;root.addView(box,lp);
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

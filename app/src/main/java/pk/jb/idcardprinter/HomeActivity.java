package pk.jb.idcardprinter;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;

public class HomeActivity extends Activity {
  @Override public void onCreate(Bundle b){
    super.onCreate(b);
    LinearLayout root=new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);root.setPadding(28,50,28,28);
    TextView title=new TextView(this);title.setText("JB Mobile & PhotoState");title.setTextSize(26);title.setGravity(Gravity.CENTER);root.addView(title);
    TextView sub=new TextView(this);sub.setText("Choose your printing service");sub.setGravity(Gravity.CENTER);root.addView(sub);
    Button cnic=new Button(this);cnic.setText("1. CNIC / ID Card A4 Printer");root.addView(cnic);
    cnic.setOnClickListener(v->startActivity(new Intent(this,MainActivity.class)));
    Button passport=new Button(this);passport.setText("2. Passport Photo Maker - 4R");root.addView(passport);
    passport.setOnClickListener(v->startActivity(new Intent(this,PassportActivity.class)));
  }
}

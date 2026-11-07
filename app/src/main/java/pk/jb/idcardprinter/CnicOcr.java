package pk.jb.idcardprinter;

import android.graphics.Bitmap;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.mlkit.vision.text.TextRecognizer;
import java.util.regex.*;
import java.util.*;

public final class CnicOcr {
  private CnicOcr(){}
  public interface Callback {void done(String number);}
  public static String parse(String text){
    if(text==null)return null;
    String normalized=text.replace('۰','0').replace('۱','1').replace('۲','2').replace('۳','3').replace('۴','4').replace('۵','5').replace('۶','6').replace('۷','7').replace('۸','8').replace('۹','9');
    Matcher m=Pattern.compile("(?<![0-9])([0-9]{5})[\\s\\-–]*([0-9]{7})[\\s\\-–]*([0-9])(?![0-9])").matcher(normalized);
    if(m.find())return m.group(1)+m.group(2)+m.group(3);
    // OCR sometimes inserts line breaks or spaces in a CNIC.
    m=Pattern.compile("(?<![0-9])(?:[0-9][ \\t\\-]*){13}(?![0-9])").matcher(normalized);
    while(m.find()){String digits=m.group().replaceAll("[^0-9]","");if(digits.length()==13)return digits;}
    return null;
  }
  public static void read(Bitmap bitmap,Callback callback){
    if(bitmap==null){callback.done(null);return;}
    TextRecognizer recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    recognizer.process(InputImage.fromBitmap(bitmap,0))
      .addOnSuccessListener(result->{String number=parse(result.getText());recognizer.close();callback.done(number);})
      .addOnFailureListener(error->{recognizer.close();callback.done(null);});
  }
}

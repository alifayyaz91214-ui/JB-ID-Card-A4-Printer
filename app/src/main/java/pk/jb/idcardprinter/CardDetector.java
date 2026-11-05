package pk.jb.idcardprinter;

import android.graphics.Bitmap;
import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import java.util.*;

public final class CardDetector {
  private CardDetector(){}
  public static Bitmap detect(Bitmap source){
    if(source==null)return null;
    Mat rgba=new Mat(),small=new Mat(),gray=new Mat(),edges=new Mat(),hierarchy=new Mat();
    try{
      Utils.bitmapToMat(source,rgba);
      double factor=Math.min(1.0,1000.0/Math.max(source.getWidth(),source.getHeight()));
      Imgproc.resize(rgba,small,new Size(),factor,factor,Imgproc.INTER_AREA);
      Imgproc.cvtColor(small,gray,Imgproc.COLOR_RGBA2GRAY);
      Imgproc.GaussianBlur(gray,gray,new Size(5,5),0);
      Imgproc.Canny(gray,edges,45,125);
      Imgproc.dilate(edges,edges,Imgproc.getStructuringElement(Imgproc.MORPH_RECT,new Size(3,3)));
      List<MatOfPoint> contours=new ArrayList<>();
      Imgproc.findContours(edges,contours,hierarchy,Imgproc.RETR_LIST,Imgproc.CHAIN_APPROX_SIMPLE);
      Point[] best=null;double bestScore=0,frame=small.width()*small.height();
      for(MatOfPoint contour:contours){
        MatOfPoint2f curve=new MatOfPoint2f(contour.toArray()),approx=new MatOfPoint2f();
        double perimeter=Imgproc.arcLength(curve,true);
        Imgproc.approxPolyDP(curve,approx,.025*perimeter,true);
        Point[] p=approx.toArray();
        if(p.length==4 && Imgproc.isContourConvex(new MatOfPoint(p))){
          double area=Math.abs(Imgproc.contourArea(new MatOfPoint(p)));
          if(area<frame*.06||area>frame*.90)continue;
          Point[] ordered=order(p);
          double width=(dist(ordered[0],ordered[1])+dist(ordered[3],ordered[2]))/2;
          double height=(dist(ordered[0],ordered[3])+dist(ordered[1],ordered[2]))/2;
          double ratio=width/Math.max(1,height);
          if(ratio<1.32||ratio>1.95)continue;
          double score=area/frame - .18*Math.abs(ratio-85.6/54.0);
          if(score>bestScore){bestScore=score;best=ordered;}
        }
        curve.release();approx.release();
      }
      for(MatOfPoint c:contours)c.release();
      if(best==null)return null;
      // Correct perspective and produce consistent landscape CNIC aspect ratio.
      int outW=1000,outH=631;
      MatOfPoint2f srcPts=new MatOfPoint2f(),dstPts=new MatOfPoint2f();
      Point[] original=new Point[4];
      for(int i=0;i<4;i++)original[i]=new Point(best[i].x/factor,best[i].y/factor);
      srcPts.fromArray(original);
      dstPts.fromArray(new Point(0,0),new Point(outW-1,0),new Point(outW-1,outH-1),new Point(0,outH-1));
      Mat transform=Imgproc.getPerspectiveTransform(srcPts,dstPts),output=new Mat();
      Imgproc.warpPerspective(rgba,output,transform,new Size(outW,outH));
      Bitmap result=Bitmap.createBitmap(outW,outH,Bitmap.Config.ARGB_8888);
      Utils.matToBitmap(output,result);
      srcPts.release();dstPts.release();transform.release();output.release();
      return result;
    }finally{rgba.release();small.release();gray.release();edges.release();hierarchy.release();}
  }
  static double dist(Point a,Point b){return Math.hypot(a.x-b.x,a.y-b.y);}
  static Point[] order(Point[] points){
    Point tl=null,tr=null,br=null,bl=null;
    double minSum=Double.MAX_VALUE,maxSum=-Double.MAX_VALUE,minDiff=Double.MAX_VALUE,maxDiff=-Double.MAX_VALUE;
    for(Point p:points){
      double sum=p.x+p.y,diff=p.y-p.x;
      if(sum<minSum){minSum=sum;tl=p;}
      if(sum>maxSum){maxSum=sum;br=p;}
      if(diff<minDiff){minDiff=diff;tr=p;}
      if(diff>maxDiff){maxDiff=diff;bl=p;}
    }
    return new Point[]{tl,tr,br,bl};
  }
}

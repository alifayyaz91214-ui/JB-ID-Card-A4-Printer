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
    Point[] original=findCorners(source);
    if(original==null)return null;
    Mat rgba=new Mat();
    Utils.bitmapToMat(source,rgba);
    try{
      // Correct perspective for either portrait or landscape source orientation.
      double edgeW=(dist(original[0],original[1])+dist(original[3],original[2]))/2;
      double edgeH=(dist(original[0],original[3])+dist(original[1],original[2]))/2;
      boolean portrait=edgeH>edgeW;
      int longSide=Math.min(2800,Math.max(1000,(int)Math.round(Math.max(edgeW,edgeH))));
      int shortSide=(int)Math.round(longSide*54.0/85.6);
      int outW=portrait?shortSide:longSide,outH=portrait?longSide:shortSide;
      MatOfPoint2f srcPts=new MatOfPoint2f(),dstPts=new MatOfPoint2f();
      // Add a very small 0.5% edge safety margin (avoid surrounding fabric) about its centre.
      // Clamp to source bounds to avoid sampling outside the photo.
      double cx=0,cy=0;
      for(Point point:original){cx+=point.x;cy+=point.y;}
      cx/=4.0;cy/=4.0;
      for(Point point:original){
        point.x=Math.max(0,Math.min(source.getWidth()-1,cx+(point.x-cx)*1.005));
        point.y=Math.max(0,Math.min(source.getHeight()-1,cy+(point.y-cy)*1.005));
      }
      srcPts.fromArray(original);
      dstPts.fromArray(new Point(0,0),new Point(outW-1,0),new Point(outW-1,outH-1),new Point(0,outH-1));
      Mat transform=Imgproc.getPerspectiveTransform(srcPts,dstPts),output=new Mat();
      Imgproc.warpPerspective(rgba,output,transform,new Size(outW,outH));
      if(portrait){
        Mat rotated=new Mat();
        Core.rotate(output,rotated,Core.ROTATE_90_CLOCKWISE);
        output.release();output=rotated;
      }
      Bitmap result=Bitmap.createBitmap(output.cols(),output.rows(),Bitmap.Config.ARGB_8888);
      Utils.matToBitmap(output,result);
      srcPts.release();dstPts.release();transform.release();output.release();
      return result;
    }finally{rgba.release();}
  }
  public static Point[] findCorners(Bitmap source){
    if(source==null)return null;
    Mat rgba=new Mat(),small=new Mat(),gray=new Mat(),edges=new Mat(),hierarchy=new Mat();
    try{
      Utils.bitmapToMat(source,rgba);
      double factor=Math.min(1.0,1000.0/Math.max(source.getWidth(),source.getHeight()));
      Imgproc.resize(rgba,small,new Size(),factor,factor,Imgproc.INTER_AREA);
      Imgproc.cvtColor(small,gray,Imgproc.COLOR_RGBA2GRAY);
      Imgproc.GaussianBlur(gray,gray,new Size(5,5),0);
      Point[] best=null;
      double bestScore=-Double.MAX_VALUE;
      double frame=small.width()*small.height();
      // Try several edge sensitivities: textured backgrounds and pale card edges vary.
      int[][] thresholds={{18,55},{30,85},{35,100},{55,150},{80,190}};
      for(int[] threshold:thresholds){
        Imgproc.Canny(gray,edges,threshold[0],threshold[1]);
        Mat kernel=Imgproc.getStructuringElement(Imgproc.MORPH_RECT,new Size(3,3));
        Imgproc.morphologyEx(edges,edges,Imgproc.MORPH_CLOSE,kernel);
        kernel.release();
        List<MatOfPoint> contours=new ArrayList<>();
        Imgproc.findContours(edges,contours,hierarchy,Imgproc.RETR_LIST,Imgproc.CHAIN_APPROX_SIMPLE);
        for(MatOfPoint contour:contours){
          double area=Math.abs(Imgproc.contourArea(contour));
          if(area<frame*.008||area>frame*.85)continue;
          MatOfPoint2f curve=new MatOfPoint2f(contour.toArray()),approx=new MatOfPoint2f();
          double perimeter=Imgproc.arcLength(curve,true);
          Imgproc.approxPolyDP(curve,approx,.022*perimeter,true);
          Point[] p=approx.toArray();
          if(p.length==4){
            MatOfPoint polygon=new MatOfPoint(p);
            boolean convex=Imgproc.isContourConvex(polygon);
            polygon.release();
            if(convex){
              Point[] ordered=order(p);
              double width=(dist(ordered[0],ordered[1])+dist(ordered[3],ordered[2]))/2;
              double height=(dist(ordered[0],ordered[3])+dist(ordered[1],ordered[2]))/2;
              double ratio=width/Math.max(1,height);
              double cx=(ordered[0].x+ordered[1].x+ordered[2].x+ordered[3].x)/4;
              double cy=(ordered[0].y+ordered[1].y+ordered[2].y+ordered[3].y)/4;
              if(((ratio>=1.35&&ratio<=1.88)||(ratio>=.53&&ratio<=.74)) && cx>small.width()*.08&&cx<small.width()*.92 && cy>small.height()*.08&&cy<small.height()*.92){
                double normalizedRatio=ratio>=1?ratio:1.0/ratio;
                double score=Math.sqrt(area/frame)-.45*Math.abs(normalizedRatio-85.6/54.0);
                if(score>bestScore){bestScore=score;best=ordered;}
              }
            }
          }
          curve.release();approx.release();
        }
        for(MatOfPoint contour:contours)contour.release();
      }
      if(best==null)return null;
      Point[] original=new Point[4];
      for(int i=0;i<4;i++)original[i]=new Point(best[i].x/factor,best[i].y/factor);
      return original;
    }finally{rgba.release();small.release();gray.release();edges.release();hierarchy.release();}
  }
  static double dist(Point a,Point b){return Math.hypot(a.x-b.x,a.y-b.y);}
  static Point[] order(Point[] points){
    // Angle-based ordering avoids duplicated corners on steeply rotated cards.
    double cx=0,cy=0;
    for(Point p:points){cx+=p.x;cy+=p.y;}
    cx/=points.length;cy/=points.length;
    final double centerX=cx,centerY=cy;
    Point[] sorted=points.clone();
    Arrays.sort(sorted,(a,b)->Double.compare(Math.atan2(a.y-centerY,a.x-centerX),Math.atan2(b.y-centerY,b.x-centerX)));
    int first=0;double min=Double.MAX_VALUE;
    for(int i=0;i<4;i++){double sum=sorted[i].x+sorted[i].y;if(sum<min){min=sum;first=i;}}
    return new Point[]{sorted[first],sorted[(first+1)%4],sorted[(first+2)%4],sorted[(first+3)%4]};
  }
}

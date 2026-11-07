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
    return warp(source,original);
  }
  public static Bitmap warp(Bitmap source,Point[] original){
    if(source==null||original==null||original.length!=4)return null;
    original=original.clone();
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
      // Perspective correction maps all four independently detected corners to a true rectangle.
      // No perspective skew remains in the output when corner detection is accurate.
      // Add a very small 0.5% edge safety margin about its centre.
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
      Imgproc.warpPerspective(rgba,output,transform,new Size(outW,outH),Imgproc.INTER_CUBIC,Core.BORDER_REPLICATE);
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
      Imgproc.GaussianBlur(gray,gray,new Size(3,3),0);
      Point[] best=null;
      double bestScore=-Double.MAX_VALUE;
      double frame=small.width()*small.height();
      // Multi-pass thresholding and rectangular hull fallback help pale cards on hands.
      int[][] thresholds={{8,25},{12,40},{20,65},{35,105},{55,150},{85,200}};
      for(int[] threshold:thresholds){
        Imgproc.Canny(gray,edges,threshold[0],threshold[1]);
        Mat kernel=Imgproc.getStructuringElement(Imgproc.MORPH_RECT,new Size(5,5));
        Imgproc.morphologyEx(edges,edges,Imgproc.MORPH_CLOSE,kernel);
        kernel.release();
        List<MatOfPoint> contours=new ArrayList<>();
        Imgproc.findContours(edges,contours,hierarchy,Imgproc.RETR_LIST,Imgproc.CHAIN_APPROX_SIMPLE);
        for(MatOfPoint contour:contours){
          double area=Math.abs(Imgproc.contourArea(contour));
          if(area<frame*.018||area>frame*.75){contour.release();continue;}
          MatOfPoint2f curve=new MatOfPoint2f(contour.toArray()),approx=new MatOfPoint2f();
          double perimeter=Imgproc.arcLength(curve,true);
          Imgproc.approxPolyDP(curve,approx,.025*perimeter,true);
          Point[] candidate=approx.toArray();
          boolean exact=candidate.length==4;
          if(!exact){
            // A hand or low-contrast border can break a card edge. Fit a rotated
            // rectangle only when the contour itself fills that rectangle closely.
            RotatedRect rect=Imgproc.minAreaRect(curve);
            Point[] corners=new Point[4];rect.points(corners);
            double rectArea=Math.max(1,rect.size.width*rect.size.height);
            if(area/rectArea>=.79)candidate=corners;
          }
          if(candidate.length==4){
            MatOfPoint polygon=new MatOfPoint(candidate);
            boolean convex=Imgproc.isContourConvex(polygon);polygon.release();
            if(convex){
              Point[] ordered=order(candidate);
              double width=(dist(ordered[0],ordered[1])+dist(ordered[3],ordered[2]))/2;
              double height=(dist(ordered[0],ordered[3])+dist(ordered[1],ordered[2]))/2;
              double ratio=Math.max(width,height)/Math.max(1,Math.min(width,height));
              double cx=0,cy=0;for(Point p:ordered){cx+=p.x;cy+=p.y;}cx/=4;cy/=4;
              MatOfPoint quad=new MatOfPoint(ordered);double quadArea=Math.abs(Imgproc.contourArea(quad));quad.release();
              double fill=area/Math.max(1,quadArea);
              if(ratio>=1.30&&ratio<=1.90&&fill>=.68&&cx>small.width()*.08&&cx<small.width()*.92&&cy>small.height()*.08&&cy<small.height()*.92){
                double score=Math.sqrt(quadArea/frame)*2.0-1.15*Math.abs(ratio-85.6/54.0)+(exact?.12:0);
                if(score>bestScore){bestScore=score;best=ordered;}
              }
            }
          }
          curve.release();approx.release();contour.release();
        }
      }
      if(best==null){
        // Contrast-limited histogram equalization can reveal faint black-and-white card borders.
        Mat enhanced=new Mat();org.opencv.imgproc.CLAHE clahe=Imgproc.createCLAHE(3.0,new Size(8,8));clahe.apply(gray,enhanced);
        Imgproc.adaptiveThreshold(enhanced,edges,255,Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,Imgproc.THRESH_BINARY,31,8);
        Mat kernel=Imgproc.getStructuringElement(Imgproc.MORPH_RECT,new Size(3,3));Imgproc.morphologyEx(edges,edges,Imgproc.MORPH_CLOSE,kernel);kernel.release();
        List<MatOfPoint> extra=new ArrayList<>();Imgproc.findContours(edges,extra,hierarchy,Imgproc.RETR_LIST,Imgproc.CHAIN_APPROX_SIMPLE);
        for(MatOfPoint contour:extra){double area=Math.abs(Imgproc.contourArea(contour));if(area<frame*.018||area>frame*.75){contour.release();continue;}
          MatOfPoint2f curve=new MatOfPoint2f(contour.toArray()),approx=new MatOfPoint2f();Imgproc.approxPolyDP(curve,approx,.025*Imgproc.arcLength(curve,true),true);
          Point[] pts=approx.toArray();if(pts.length==4){MatOfPoint poly=new MatOfPoint(pts);boolean convex=Imgproc.isContourConvex(poly);poly.release();if(convex){Point[] q=order(pts);double w=(dist(q[0],q[1])+dist(q[2],q[3]))/2,h=(dist(q[0],q[3])+dist(q[1],q[2]))/2;double ratio=Math.max(w,h)/Math.max(1,Math.min(w,h));double cx=0,cy=0;for(Point p:q){cx+=p.x;cy+=p.y;}cx/=4;cy/=4;if(ratio>=1.3&&ratio<=1.9&&cx>small.width()*.08&&cx<small.width()*.92&&cy>small.height()*.08&&cy<small.height()*.92){best=q;curve.release();approx.release();break;}}}
          curve.release();approx.release();contour.release();
        }for(MatOfPoint c:extra)if(!c.empty())c.release();enhanced.release();
      }
      if(best==null)return null;
      Point[] original=new Point[4];
      for(int i=0;i<4;i++)original[i]=new Point(best[i].x/factor,best[i].y/factor);
      return original;
    }finally{rgba.release();small.release();gray.release();edges.release();hierarchy.release();}
  }
  // Find multiple non-overlapping card rectangles; returns largest reliable matches first.
  public static java.util.List<Bitmap> detectAll(Bitmap source,int limit){
    java.util.ArrayList<Bitmap> results=new java.util.ArrayList<>();
    if(source==null||limit<1)return results;
    Mat rgba=new Mat(),small=new Mat(),gray=new Mat(),edges=new Mat(),hierarchy=new Mat();
    try{
      Utils.bitmapToMat(source,rgba);
      double factor=Math.min(1.0,1100.0/Math.max(source.getWidth(),source.getHeight()));
      Imgproc.resize(rgba,small,new Size(),factor,factor,Imgproc.INTER_AREA);
      Imgproc.cvtColor(small,gray,Imgproc.COLOR_RGBA2GRAY);
      Imgproc.GaussianBlur(gray,gray,new Size(3,3),0);
      java.util.ArrayList<Point[]> candidates=new java.util.ArrayList<>();
      java.util.ArrayList<Double> scores=new java.util.ArrayList<>();
      Mat enhanced=new Mat();
      Imgproc.createCLAHE(3.0,new Size(8,8)).apply(gray,enhanced);
      for(int pass=0;pass<4;pass++){
        if(pass==3)Imgproc.adaptiveThreshold(enhanced,edges,255,Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,Imgproc.THRESH_BINARY,31,8);
        else Imgproc.Canny(pass==2?enhanced:gray,edges,pass==0?12:pass==1?30:15,pass==0?40:pass==1?95:50);
        Mat kernel=Imgproc.getStructuringElement(Imgproc.MORPH_RECT,new Size(3,3));
        Imgproc.morphologyEx(edges,edges,Imgproc.MORPH_CLOSE,kernel);kernel.release();
        java.util.List<MatOfPoint> contours=new java.util.ArrayList<>();
        Imgproc.findContours(edges,contours,hierarchy,Imgproc.RETR_LIST,Imgproc.CHAIN_APPROX_SIMPLE);
        double frame=small.width()*small.height();
        for(MatOfPoint contour:contours){
          double area=Math.abs(Imgproc.contourArea(contour));
          if(area<frame*.003||area>frame*.80){contour.release();continue;}
          MatOfPoint2f curve=new MatOfPoint2f(contour.toArray()),approx=new MatOfPoint2f();
          Imgproc.approxPolyDP(curve,approx,.025*Imgproc.arcLength(curve,true),true);
          Point[] points=approx.toArray();
          if(points.length!=4){
            RotatedRect rect=Imgproc.minAreaRect(curve);
            if(area/Math.max(1,rect.size.width*rect.size.height)>.82){points=new Point[4];rect.points(points);}
          }
          if(points.length==4){
            MatOfPoint poly=new MatOfPoint(points);boolean convex=Imgproc.isContourConvex(poly);poly.release();
            if(convex){
              Point[] q=order(points);
              double w=(dist(q[0],q[1])+dist(q[2],q[3]))/2,h=(dist(q[0],q[3])+dist(q[1],q[2]))/2;
              double ratio=Math.max(w,h)/Math.max(1,Math.min(w,h));
              Rect bounds=Imgproc.boundingRect(new MatOfPoint(q));
              double score=area/frame-Math.abs(ratio-85.6/54.0)*.2;
              if(ratio>1.34&&ratio<1.86&&bounds.width>25&&bounds.height>18&&score>-.06){
                boolean duplicate=false;
                for(Point[] prior:candidates){
                  Rect old=Imgproc.boundingRect(new MatOfPoint(prior));
                  double ow=Math.max(0,Math.min(old.x+old.width,bounds.x+bounds.width)-Math.max(old.x,bounds.x));
                  double oh=Math.max(0,Math.min(old.y+old.height,bounds.y+bounds.height)-Math.max(old.y,bounds.y));
                  double shared=ow*oh/Math.max(1,Math.min(old.area(),bounds.area()));
                  if(shared>.65){duplicate=true;break;}
                }
                if(!duplicate){candidates.add(q);scores.add(score);}
              }
            }
          }
          curve.release();approx.release();contour.release();
        }
      }
      enhanced.release();
      java.util.ArrayList<Integer> orderIds=new java.util.ArrayList<>();
      for(int i=0;i<candidates.size();i++)orderIds.add(i);
      java.util.Collections.sort(orderIds,(a,b)->Double.compare(scores.get(b),scores.get(a)));
      java.util.ArrayList<Integer> chosen=new java.util.ArrayList<>();
      for(int idx:orderIds){
        if(chosen.size()>=limit)break;
        Point[] candidate=candidates.get(idx);
        double cx=0,cy=0;for(Point p:candidate){cx+=p.x;cy+=p.y;}cx/=4;cy/=4;
        double cw=(dist(candidate[0],candidate[1])+dist(candidate[2],candidate[3]))/2;
        double ch=(dist(candidate[0],candidate[3])+dist(candidate[1],candidate[2]))/2;
        boolean overlap=false;
        for(int prior:chosen){
          Point[] existing=candidates.get(prior);double px=0,py=0;
          for(Point p:existing){px+=p.x;py+=p.y;}px/=4;py/=4;
          double pw=(dist(existing[0],existing[1])+dist(existing[2],existing[3]))/2;
          double ph=(dist(existing[0],existing[3])+dist(existing[1],existing[2]))/2;
          if(Math.abs(cx-px)<Math.min(cw,pw)*.55&&Math.abs(cy-py)<Math.min(ch,ph)*.55){overlap=true;break;}
        }
        if(!overlap)chosen.add(idx);
      }
      // Document reading order: top to bottom, then left to right.
      java.util.Collections.sort(chosen,(a,b)->{
        Point[] qa=candidates.get(a),qb=candidates.get(b);
        double ax=0,ay=0,bx=0,by=0;
        for(int i=0;i<4;i++){ax+=qa[i].x;ay+=qa[i].y;bx+=qb[i].x;by+=qb[i].y;}
        ay/=4;by/=4;ax/=4;bx/=4;
        double rowTolerance=small.height()*.055;
        return Math.abs(ay-by)>rowTolerance?Double.compare(ay,by):Double.compare(ax,bx);
      });
      for(int idx:chosen){
        Point[] q=candidates.get(idx),scaled=new Point[4];
        for(int i=0;i<4;i++)scaled[i]=new Point(q[i].x/factor,q[i].y/factor);
        try{Bitmap card=warp(source,scaled);if(card!=null)results.add(card);}catch(Exception ignored){}
      }
      return results;
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

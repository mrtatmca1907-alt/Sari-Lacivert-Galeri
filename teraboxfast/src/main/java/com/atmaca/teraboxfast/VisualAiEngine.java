package com.atmaca.teraboxfast;

import android.graphics.Bitmap;
import android.graphics.Rect;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.objects.DetectedObject;
import com.google.mlkit.vision.objects.ObjectDetection;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions;
import java.util.ArrayList;
import java.util.List;

public final class VisualAiEngine {
    public interface Callback { void onObjects(List<Hit> hits); }

    public static final class Hit {
        public final Rect box;
        public final float confidence;
        public Hit(Rect b,float c){ box=b;confidence=c; }
    }

    private final ObjectDetector detector;

    public VisualAiEngine(){
        ObjectDetectorOptions opts=new ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableClassification()
            .enableMultipleObjects()
            .build();
        detector=ObjectDetection.getClient(opts);
    }

    public void analyze(Bitmap bmp,Callback cb){
        detector.process(InputImage.fromBitmap(bmp,0))
            .addOnSuccessListener(list->{
                ArrayList<Hit> out=new ArrayList<>();
                for(DetectedObject o:list){
                    float best=0f;
                    for(DetectedObject.Label l:o.getLabels()) best=Math.max(best,l.getConfidence());
                    Rect r=o.getBoundingBox();
                    if(r.width()>80 && r.height()>80) out.add(new Hit(new Rect(r),best));
                }
                cb.onObjects(out);
            })
            .addOnFailureListener(e->cb.onObjects(new ArrayList<>()));
    }

    public void close(){ detector.close(); }
}

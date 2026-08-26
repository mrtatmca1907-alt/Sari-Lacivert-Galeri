package com.sarilacivert.atmaca;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.ArrayDeque;

public class BackupWorker extends Worker {
    public BackupWorker(@NonNull Context c, @NonNull WorkerParameters p) { super(c,p); }

    @NonNull @Override public Result doWork() {
        Context ctx=getApplicationContext();
        SharedPreferences prefs=ctx.getSharedPreferences("atmaca",Context.MODE_PRIVATE);
        String value=prefs.getString("auto_tree_uri","");
        if(value==null||value.isEmpty()) return Result.success();
        try{
            Uri tree=Uri.parse(value);
            DocumentFile root=DocumentFile.fromTreeUri(ctx,tree);
            if(root==null||!root.exists()) return Result.failure();
            QueueDb q=new QueueDb(ctx);
            String rootName=MainActivity.safe(root.getName());
            ArrayDeque<Node> stack=new ArrayDeque<>();
            stack.push(new Node(root,"Otomatik/"+rootName));
            while(!stack.isEmpty()){
                Node n=stack.pop();
                DocumentFile[] children;
                try{ children=n.file.listFiles(); }catch(Exception e){ continue; }
                for(DocumentFile f:children){
                    String path=n.path+"/"+MainActivity.safe(f.getName());
                    if(f.isDirectory()) stack.push(new Node(f,path));
                    else if(f.isFile()) q.enqueue(f.getUri().toString(),path,Math.max(0,f.length()),Math.max(0,f.lastModified()));
                }
            }
            MainActivity.startUploader(ctx);
            return Result.success();
        }catch(Exception e){
            return Result.retry();
        }
    }

    static class Node{
        final DocumentFile file;
        final String path;
        Node(DocumentFile f,String p){file=f;path=p;}
    }
}

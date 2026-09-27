package com.atmaca.teraboxfast;

import android.accessibilityservice.AccessibilityService;
import android.os.SystemClock;
import android.view.accessibility.*;
import java.util.*;

public class TeraReadService extends AccessibilityService {
    private long lastScroll=0;
    private final HashSet<String> noise=new HashSet<>(Arrays.asList(
        "Home","Files","File","Video","Photo","Photos","Music","Document","Documents",
        "Transfer","Profile","Me","Search","Upload","Download","Downloads","Share",
        "Ana Sayfa","Dosyalar","Video","Fotoğraf","Fotoğraflar","Müzik","Belge","Belgeler",
        "Aktarım","Profil","Ara","Yükle","İndir","İndirilenler","Paylaş"
    ));

    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(!IndexDb.isEnabled(this))return;
        CharSequence p=event.getPackageName();
        String pkg=p==null?"":p.toString().toLowerCase(Locale.US);
        if(!(pkg.contains("dubox")||pkg.contains("terabox")))return;

        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null)return;
        IndexDb db=new IndexDb(this);
        String screen=screenHint(root);
        scan(root,db,screen,0);

        long now=SystemClock.uptimeMillis();
        if(now-lastScroll>900){
            AccessibilityNodeInfo sc=findScrollable(root);
            if(sc!=null && sc.isVisibleToUser()){
                sc.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
                lastScroll=now;
            }
        }
    }

    private void scan(AccessibilityNodeInfo n,IndexDb db,String screen,int depth){
        if(n==null||depth>30)return;
        CharSequence t=n.getText(), d=n.getContentDescription(), c=n.getClassName();
        String ts=t==null?"":t.toString().trim();
        String ds=d==null?"":d.toString().trim();
        String cs=c==null?"":c.toString();

        if(meaningful(ts,ds,cs)) db.save(ts,ds,cs,screen);

        for(int i=0;i<n.getChildCount();i++) scan(n.getChild(i),db,screen,depth+1);
    }

    private boolean meaningful(String t,String d,String cls){
        String v=!t.isEmpty()?t:d;
        if(v.isEmpty()||v.length()<2)return false;
        if(noise.contains(v))return false;
        if(v.matches("^[0-9]{1,3}%$"))return false;
        if(v.matches("^[0-9]{1,2}:[0-9]{2}$"))return false;
        if(v.equals("⋮")||v.equals("…"))return false;
        return cls.contains("Text")||cls.contains("View")||cls.contains("Button");
    }

    private String screenHint(AccessibilityNodeInfo root){
        ArrayList<String> parts=new ArrayList<>();
        collectTop(root,parts,0);
        if(parts.isEmpty())return "TeraBox";
        String s=String.join(" | ",parts);
        return s.length()>250?s.substring(0,250):s;
    }

    private void collectTop(AccessibilityNodeInfo n,ArrayList<String> out,int depth){
        if(n==null||depth>5||out.size()>=6)return;
        CharSequence t=n.getText();
        if(t!=null){
            String s=t.toString().trim();
            if(s.length()>1&&!noise.contains(s)&&!out.contains(s))out.add(s);
        }
        for(int i=0;i<n.getChildCount()&&out.size()<6;i++)collectTop(n.getChild(i),out,depth+1);
    }

    private AccessibilityNodeInfo findScrollable(AccessibilityNodeInfo n){
        if(n==null)return null;
        if(n.isScrollable())return n;
        for(int i=0;i<n.getChildCount();i++){
            AccessibilityNodeInfo r=findScrollable(n.getChild(i));
            if(r!=null)return r;
        }
        return null;
    }

    @Override public void onInterrupt(){}
}

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
        "Cancel","Select All","Deselect All","Selected items","Sort by name",
        "Ana Sayfa","Dosyalar","Video","Fotoğraf","Fotoğraflar","Müzik","Belge","Belgeler",
        "Aktarım","Profil","Ara","Yükle","İndir","İndirilenler","Paylaş","İptal","Tümünü Seç",
        "Tüm Seçimi Kaldır","Seçili öğeler","Ada göre sırala"
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

        boolean selectAllMode=containsText(root,"Deselect All") || containsText(root,"Tüm Seçimi Kaldır");
        if(selectAllMode){
            collectVisibleFolderRows(root,db);
        }else{
            collectCheckedRows(root,db);
        }

        long now=SystemClock.uptimeMillis();
        if(now-lastScroll>650){
            AccessibilityNodeInfo sc=findScrollable(root);
            if(sc!=null && sc.isVisibleToUser()){
                sc.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
                lastScroll=now;
            }
        }
    }

    private void collectVisibleFolderRows(AccessibilityNodeInfo root,IndexDb db){
        ArrayList<AccessibilityNodeInfo> candidates=new ArrayList<>();
        collectTextNodes(root,candidates,0);
        for(AccessibilityNodeInfo n:candidates){
            String t=textOf(n);
            if(isFolderNameCandidate(t)) db.saveSelectedFolder(t);
        }
    }

    private void collectCheckedRows(AccessibilityNodeInfo root,IndexDb db){
        collectChecked(root,db,0);
    }

    private void collectChecked(AccessibilityNodeInfo n,IndexDb db,int depth){
        if(n==null||depth>30)return;
        String desc=n.getContentDescription()==null?"":n.getContentDescription().toString().toLowerCase(Locale.US);
        if(n.isChecked()||n.isSelected()||desc.contains("checked")||desc.contains("selected")||desc.contains("seçili")){
            AccessibilityNodeInfo row=n;
            for(int i=0;i<4 && row.getParent()!=null;i++)row=row.getParent();
            ArrayList<AccessibilityNodeInfo> texts=new ArrayList<>();
            collectTextNodes(row,texts,0);
            for(AccessibilityNodeInfo x:texts){
                String t=textOf(x);
                if(isFolderNameCandidate(t)){ db.saveSelectedFolder(t); break; }
            }
        }
        for(int i=0;i<n.getChildCount();i++)collectChecked(n.getChild(i),db,depth+1);
    }

    private boolean isFolderNameCandidate(String t){
        if(t==null)return false;
        t=t.trim();
        if(t.isEmpty()||noise.contains(t))return false;
        if(t.matches("^\\d+ file\\(s\\) selected$"))return false;
        if(t.matches("^\\d+$"))return true;
        if(t.matches(".*\\(\\d+\\).*$"))return true;
        if(t.length()>80)return false;
        if(t.matches(".*\\.(jpg|jpeg|png|gif|webp|mp4|mkv|avi|mov)$"))return false;
        if(t.matches(".*\\b(Yesterday|Today|Nis|Oca|Şub|Mar|May|Haz|Tem|Ağu|Eyl|Eki|Kas|Ara)\\b.*"))return false;
        return false;
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
        String s=textOf(n);
        if(s.length()>1&&!noise.contains(s)&&!out.contains(s))out.add(s);
        for(int i=0;i<n.getChildCount()&&out.size()<6;i++)collectTop(n.getChild(i),out,depth+1);
    }

    private void collectTextNodes(AccessibilityNodeInfo n,ArrayList<AccessibilityNodeInfo> out,int depth){
        if(n==null||depth>30)return;
        String t=textOf(n);
        if(!t.isEmpty())out.add(n);
        for(int i=0;i<n.getChildCount();i++)collectTextNodes(n.getChild(i),out,depth+1);
    }

    private boolean containsText(AccessibilityNodeInfo n,String needle){
        if(n==null)return false;
        String t=textOf(n);
        String d=n.getContentDescription()==null?"":n.getContentDescription().toString();
        if(t.equalsIgnoreCase(needle)||d.equalsIgnoreCase(needle))return true;
        for(int i=0;i<n.getChildCount();i++)if(containsText(n.getChild(i),needle))return true;
        return false;
    }

    private String textOf(AccessibilityNodeInfo n){
        CharSequence t=n==null?null:n.getText();
        return t==null?"":t.toString().trim();
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

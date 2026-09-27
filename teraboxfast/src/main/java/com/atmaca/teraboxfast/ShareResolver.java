package com.atmaca.teraboxfast;

import android.net.Uri;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

public class ShareResolver {
    public static final String UA="Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0 Mobile Safari/537.36";
    private static final String BASE="https://www.terabox.com";
    private static String COOKIE="";

    public static class FileItem {
        public String name, path, fsId, downloadUrl;
        public long size;
    }
    public static class Result {
        public final ArrayList<FileItem> files=new ArrayList<>();
        public String cookie="";
    }

    public static Result resolveAll(String raw) throws Exception {
        String surl=extractShareId(raw);
        if(surl==null) throw new Exception("Geçerli TeraBox paylaşım bağlantısı değil.");

        Result out=new Result();
        JSONObject root=list(surl,true,"/");
        int errno=root.optInt("errno",0);
        if(errno!=0) throw new Exception("TeraBox liste hatası: "+errno);

        String shareId=String.valueOf(root.optLong("share_id", root.optLong("shareid",0)));
        String uk=String.valueOf(root.optLong("uk",0));
        if("0".equals(shareId) || "0".equals(uk)) {
            JSONObject info=shortInfo(surl);
            shareId=String.valueOf(info.optLong("shareid",info.optLong("share_id",0)));
            uk=String.valueOf(info.optLong("uk",0));
        }

        Tokens tok=tokens(surl);
        collectRecursive(surl, "/", root.optJSONArray("list"), shareId, uk, tok, out.files);
        out.cookie=COOKIE;
        return out;
    }

    private static void collectRecursive(String surl,String dir,JSONArray arr,String shareId,String uk,Tokens tok,ArrayList<FileItem> out) throws Exception {
        if(arr==null) return;
        for(int i=0;i<arr.length();i++){
            JSONObject o=arr.optJSONObject(i);
            if(o==null) continue;
            boolean isDir="1".equals(o.optString("isdir")) || o.optInt("isdir",0)==1 || "1".equals(o.optString("is_dir"));
            String name=o.optString("server_filename",o.optString("filename","file"));
            String path=o.optString("path", dir.endsWith("/")?dir+name:dir+"/"+name);
            if(isDir){
                JSONObject child=list(surl,false,path);
                collectRecursive(surl,path,child.optJSONArray("list"),shareId,uk,tok,out);
            }else{
                String fs=String.valueOf(o.optLong("fs_id",0));
                if("0".equals(fs)) fs=o.optString("fs_id");
                if(fs==null || fs.isEmpty()) continue;
                String dl=o.optString("dlink","");
                if(dl.isEmpty()) dl=download(shareId,uk,fs,tok,surl);
                FileItem f=new FileItem();
                f.name=name; f.path=path; f.fsId=fs; f.size=o.optLong("size",0); f.downloadUrl=dl;
                out.add(f);
            }
        }
    }

    private static JSONObject list(String surl,boolean root,String dir) throws Exception {
        StringBuilder u=new StringBuilder(BASE+"/share/list?app_id=250528");
        u.append("&shorturl=").append(enc(surl));
        u.append("&root=").append(root?"1":"0");
        u.append("&scene=&nested=0&pwd=&page=1&num=1000&order=time&by=desc");
        if(!root) u.append("&dir=").append(enc(dir));
        return getJson(u.toString(), BASE+"/sharing/link?surl="+enc(surl));
    }

    private static JSONObject shortInfo(String surl) throws Exception {
        String u=BASE+"/api/shorturlinfo?app_id=250528&web=1&channel=dubox&clienttype=0&shorturl="+enc("1"+surl)+"&root=1&jsToken="+enc(tokensOnlyJs(surl));
        return getJson(u, BASE+"/sharing/link?surl="+enc(surl));
    }

    private static class Tokens { String jsToken="",sign="",timestamp="",bdstoken=""; }

    private static Tokens tokens(String surl) throws Exception {
        String html=getText(BASE+"/sharing/link?surl="+enc(surl), null);
        Tokens t=new Tokens();
        t.jsToken=first(html,
            "fn%28%22([^%]+)%22%29",
            "window\\.jsToken[^=]*=\\s*fn\\s*\\(\\s*[\"']([^\"']+)[\"']",
            "fn\\(\\s*[\"']([^\"']+)[\"']\\s*\\)"
        );
        t.sign=first(html,
            "[?&](?:amp;)?sign=([^&\"'\\s<>]+)",
            "\"sign\"\\s*:\\s*\"([^\"]+)\"",
            "window\\.sign\\s*=\\s*[\"']([^\"']+)[\"']"
        );
        t.timestamp=first(html,
            "[?&](?:amp;)?time=(\\d+)",
            "\"timestamp\"\\s*:\\s*(\\d+)",
            "window\\.timestamp\\s*=\\s*[\"']?(\\d+)"
        );
        t.bdstoken=first(html,
            "\"bdstoken\"\\s*:\\s*\"([^\"]+)\"",
            "window\\.bdstoken\\s*=\\s*[\"']([^\"']+)[\"']"
        );
        if(t.sign.isEmpty() || t.timestamp.isEmpty()) {
            JSONObject info=shortInfo(surl);
            if(t.sign.isEmpty()) t.sign=info.optString("sign","");
            if(t.timestamp.isEmpty()) t.timestamp=String.valueOf(info.optLong("timestamp",0));
        }
        return t;
    }

    private static String download(String shareId,String uk,String fs,Tokens t,String surl) throws Exception {
        String ref=BASE+"/sharing/link?surl="+enc(surl);

        // 1) New GET /api/download contract
        String q=BASE+"/api/download?app_id=250528&web=1&channel=dubox&clienttype=0"
            +"&jsToken="+enc(t.jsToken)
            +"&sign="+enc(t.sign)
            +"&timestamp="+enc(t.timestamp)
            +"&shareid="+enc(shareId)
            +"&uk="+enc(uk)
            +"&fid_list="+enc("["+fs+"]")
            +"&primaryid="+enc(shareId)
            +"&product=share";
        String d=parseDlink(safeGet(q,ref));
        if(!d.isEmpty()) return d;

        // 2) Legacy /api/sharedownload
        String legacyUrl=BASE+"/api/sharedownload?app_id=250528&web=1&channel=dubox&clienttype=0"
            +"&jsToken="+enc(t.jsToken)
            +"&sign="+enc(t.sign)
            +"&timestamp="+enc(t.timestamp);
        String legacyBody="shareid="+enc(shareId)
            +"&uk="+enc(uk)
            +"&fid_list="+enc("["+fs+"]")
            +"&primaryid="+enc(shareId)
            +"&product=share";
        d=parseDlink(safePost(legacyUrl,legacyBody,ref));
        if(!d.isEmpty()) return d;

        // 3) extdownload fallback
        String ext=BASE+"/share/extdownload?app_id=250528&web=1&channel=dubox&clienttype=0"
            +"&jsToken="+enc(t.jsToken)
            +"&sign="+enc(t.sign)
            +"&timestamp="+enc(t.timestamp)
            +"&shareid="+enc(shareId)
            +"&uk="+enc(uk)
            +"&fid_list="+enc("["+fs+"]")
            +"&primaryid="+enc(shareId)
            +"&product=share&nozip=1";
        d=parseDlink(safeGet(ext,ref));
        if(!d.isEmpty()) return d;

        throw new Exception("Doğrudan indirme bağlantısı alınamadı.");
    }

    private static String parseDlink(String txt){
        if(txt==null || txt.isEmpty()) return "";
        try{
            JSONObject j=new JSONObject(txt);
            String d=j.optString("dlink",j.optString("url",j.optString("download_url","")));
            if(!d.isEmpty()) return d;
            JSONArray list=j.optJSONArray("list");
            if(list!=null){
                for(int i=0;i<list.length();i++){
                    JSONObject x=list.optJSONObject(i);
                    if(x==null) continue;
                    d=x.optString("dlink",x.optString("url",""));
                    if(!d.isEmpty()) return d;
                }
            }
        }catch(Exception ignored){}
        return "";
    }

    private static String safeGet(String u,String ref){
        try{return getText(u,ref);}catch(Exception e){return "";}
    }

    private static String safePost(String u,String body,String ref){
        try{return post(u,body,ref);}catch(Exception e){return "";}
    }

    private static String tokensOnlyJs(String surl) throws Exception {
        String html=getText(BASE+"/sharing/link?surl="+enc(surl), null);
        return first(html,
            "fn%28%22([^%]+)%22%29",
            "window\\.jsToken[^=]*=\\s*fn\\s*\\(\\s*[\\\"']([^\\\"']+)[\\\"']",
            "fn\\(\\s*[\\\"']([^\\\"']+)[\\\"']\\s*\\)"
        );
    }

    private static JSONObject getJson(String u,String ref) throws Exception { return new JSONObject(getText(u,ref)); }

    private static String getText(String u,String ref) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();
        c.setConnectTimeout(20000); c.setReadTimeout(30000); c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent",UA);
        c.setRequestProperty("Accept","*/*");
        if(!COOKIE.isEmpty()) c.setRequestProperty("Cookie",COOKIE);
        if(ref!=null)c.setRequestProperty("Referer",ref);
        int rc=c.getResponseCode();
        captureCookies(c);
        InputStream in=rc>=200&&rc<400?c.getInputStream():c.getErrorStream();
        String s=read(in); c.disconnect();
        if(rc<200||rc>=400) throw new IOException("HTTP "+rc);
        return s;
    }

    private static String post(String u,String body,String ref) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();
        c.setConnectTimeout(20000); c.setReadTimeout(30000); c.setDoOutput(true); c.setRequestMethod("POST");
        c.setRequestProperty("User-Agent",UA);
        c.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
        if(!COOKIE.isEmpty()) c.setRequestProperty("Cookie",COOKIE);
        c.setRequestProperty("Referer",ref);
        byte[] b=body.getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(b.length);
        try(OutputStream o=c.getOutputStream()){o.write(b);}
        int rc=c.getResponseCode();
        captureCookies(c);
        InputStream in=rc>=200&&rc<400?c.getInputStream():c.getErrorStream();
        String s=read(in); c.disconnect();
        if(rc<200||rc>=400) throw new IOException("HTTP "+rc);
        return s;
    }

    private static void captureCookies(HttpURLConnection c){
        try{
            Map<String,List<String>> h=c.getHeaderFields();
            List<String> sc=h.get("Set-Cookie");
            if(sc==null) sc=h.get("set-cookie");
            if(sc==null) return;
            LinkedHashMap<String,String> m=new LinkedHashMap<>();
            if(!COOKIE.isEmpty()){
                for(String p:COOKIE.split(";")){
                    int x=p.indexOf('=');
                    if(x>0)m.put(p.substring(0,x).trim(),p.substring(x+1).trim());
                }
            }
            for(String s:sc){
                String first=s.split(";",2)[0];
                int x=first.indexOf('=');
                if(x>0)m.put(first.substring(0,x).trim(),first.substring(x+1).trim());
            }
            StringBuilder b=new StringBuilder();
            for(Map.Entry<String,String> e:m.entrySet()){
                if(b.length()>0)b.append("; ");
                b.append(e.getKey()).append("=").append(e.getValue());
            }
            COOKIE=b.toString();
        }catch(Exception ignored){}
    }

    private static String read(InputStream in) throws Exception {
        if(in==null) return "";
        ByteArrayOutputStream o=new ByteArrayOutputStream();
        byte[] b=new byte[8192]; int n; while((n=in.read(b))>0)o.write(b,0,n);
        return o.toString("UTF-8");
    }

    private static String first(String s,String... pats){
        for(String p:pats){
            try{Matcher m=Pattern.compile(p,Pattern.CASE_INSENSITIVE|Pattern.DOTALL).matcher(s); if(m.find())return URLDecoder.decode(m.group(1),StandardCharsets.UTF_8);}catch(Exception ignored){}
        }
        return "";
    }

    private static String extractShareId(String raw){
        if(raw==null) return null;
        Matcher q=Pattern.compile("[?&]surl=([A-Za-z0-9_-]+)").matcher(raw);
        if(q.find()) return q.group(1);
        Matcher m=Pattern.compile("/s/1?([A-Za-z0-9_-]+)").matcher(raw);
        if(m.find()) return m.group(1);
        return null;
    }

    private static String enc(String s) throws Exception { return URLEncoder.encode(s==null?"":s,"UTF-8"); }
}
package com.atmaca.teraboxfast;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

public class TeraboxSessionClient {
    private static final String UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36";
    private final LinkedHashMap<String,String> cookies=new LinkedHashMap<>();
    private String loginPageUrl, loginOrigin, browserId, pcfToken, uuid, vCode;
    private long seq;
    private int step=0;
    private String baseUrl="", jsToken="", bdstoken="";

    public static class QrStart { public String qrDataUrl; }
    public static class QrStatus { public boolean success, confirmed; }
    public static class FileEntry {
        public String path,name,fsId;
        public long size;
        public boolean dir;
    }

    public QrStart startQr() throws Exception {
        cookies.clear();
        HttpResp page=request("GET","https://www.1024terabox.com/ai/index",null,null,5);
        loginPageUrl=page.finalUrl;
        URL pu=new URL(loginPageUrl);
        loginOrigin=pu.getProtocol()+"://"+pu.getHost();
        browserId=cookies.get("browserid");
        pcfToken=match(page.body,"\\\"pcftoken\\\":\\\"([^\\\"]+)\\\"");
        if(empty(browserId)) throw new Exception("browserid alınamadı.");
        if(empty(pcfToken)) throw new Exception("pcftoken alınamadı.");

        String body=form(mapOf(
            "browserid",browserId,"client","web","clientfrom","h5","lang","en",
            "pass_version","2.8","pcftoken",pcfToken
        ));
        JSONObject j=new JSONObject(request("POST",loginOrigin+"/passport/qrcode/get?t="+System.currentTimeMillis(),body,qrHeaders(),2).body);
        JSONObject d=j.optJSONObject("data");
        if(d==null) throw new Exception("QR verisi gelmedi.");
        uuid=d.optString("uuid","");
        seq=d.optLong("seq",-1);
        String qr=d.optString("qrcode","");
        if(empty(uuid)||seq<0||empty(qr)) throw new Exception("QR yanıtı eksik.");
        QrStart out=new QrStart(); out.qrDataUrl=qr; return out;
    }

    public QrStatus checkQr() throws Exception {
        LinkedHashMap<String,String> p=mapOf(
            "browserid",browserId,"client","web","clientfrom","h5","lang","en",
            "pass_version","2.8","pcftoken",pcfToken,"reg_source","web",
            "seq",String.valueOf(seq),"step",String.valueOf(step),"uuid",uuid
        );
        if(!empty(vCode))p.put("v",vCode);
        JSONObject j=new JSONObject(request("POST",loginOrigin+"/passport/qrcode/login?t="+System.currentTimeMillis(),form(p),qrHeaders(),2).body);
        int code=j.has("code")?j.optInt("code",-1):j.optInt("errno",-1);
        QrStatus out=new QrStatus();
        if(code==39){ out.confirmed=step==1; return out; }
        if(code!=0) throw new Exception("TeraBox QR kodu: "+code+" "+j.optString("msg",j.optString("show_msg","")));

        JSONObject d=j.optJSONObject("data");
        if(d==null)d=new JSONObject();
        String vv=j.optString("v",d.optString("v",j.optString("vcode",d.optString("vcode",""))));
        if(!empty(vv))vCode=vv;

        String ndus=d.optString("ndus","");
        if(empty(ndus))ndus=cookies.get("ndus");
        if(empty(ndus))ndus=d.optString("bduss","");

        if(!empty(ndus)||d.has("userid")){
            if(!empty(ndus))cookies.put("ndus",ndus);
            finalizeSession(d);
            out.success=true;
            return out;
        }
        if(step==0){ step=1; out.confirmed=true; return out; }
        out.confirmed=true; return out;
    }

    private void finalizeSession(JSONObject payload) throws Exception {
        String region=payload.optString("region_domain_prefix","");
        ArrayList<String> origins=new ArrayList<>();
        if(!empty(region)){
            try{
                URL u=new URL(loginOrigin);
                String host=u.getHost();
                String[] parts=host.split("\\.");
                if(parts.length>=2){
                    String base=parts.length>2?String.join(".",Arrays.copyOfRange(parts,1,parts.length)):host;
                    origins.add(u.getProtocol()+"://"+region+"."+base);
                }
            }catch(Exception ignored){}
        }
        origins.add(loginOrigin);
        Exception last=null;
        for(String o:origins){
            for(String path:new String[]{"/main?category=all&path=%2F","/main",""}){
                try{
                    HttpResp r=request("GET",o+path,null,null,5);
                    String jt=first(r.body,
                        "fn%28%22([A-Fa-f0-9]{32,512})%22%29",
                        "fn\\(\\\"([A-Fa-f0-9]{32,512})\\\"\\)",
                        "\\"jsToken\\\":\\\"([A-Fa-f0-9]{32,512})\\\"");
                    if(!empty(jt)){
                        jsToken=jt;
                        bdstoken=first(r.body,
                            "\\"bdstoken\\\":\\\"([^\\\"]+)\\\"",
                            "bdstoken=([A-Fa-f0-9]{16,128})");
                        URL fu=new URL(r.finalUrl);
                        baseUrl=fu.getProtocol()+"://"+fu.getHost();
                        return;
                    }
                }catch(Exception e){last=e;}
            }
        }
        throw new Exception("Oturum alındı ama jsToken çıkarılamadı"+(last!=null?": "+last.getMessage():""));
    }

    public ArrayList<FileEntry> listAllFiles() throws Exception {
        if(empty(jsToken))throw new Exception("Önce QR ile giriş yap.");
        ArrayList<FileEntry> out=new ArrayList<>();
        ArrayDeque<String> dirs=new ArrayDeque<>();
        dirs.add("/");
        int guard=0;
        while(!dirs.isEmpty()){
            if(++guard>20000)throw new Exception("Çok fazla klasör; güvenlik sınırı aşıldı.");
            String dir=dirs.removeFirst();
            JSONObject j=apiGet("/api/list",mapOf("dir",dir,"folder","0","num","10000","page","1"),true);
            JSONArray a=j.optJSONArray("list");
            if(a==null)a=j.optJSONArray("info");
            if(a==null)continue;
            for(int i=0;i<a.length();i++){
                JSONObject x=a.optJSONObject(i); if(x==null)continue;
                FileEntry f=new FileEntry();
                f.path=x.optString("path","");
                f.name=x.optString("server_filename",x.optString("filename",""));
                f.fsId=String.valueOf(x.optLong("fs_id",0));
                f.size=x.optLong("size",0);
                f.dir=x.optInt("isdir",0)==1;
                if(f.dir){ if(!empty(f.path))dirs.add(f.path); }
                else out.add(f);
            }
        }
        return out;
    }

    private JSONObject apiGet(String endpoint, LinkedHashMap<String,String> extra, boolean withBd) throws Exception {
        LinkedHashMap<String,String> q=new LinkedHashMap<>();
        q.put("app_id","250528"); q.put("web","1"); q.put("channel","dubox"); q.put("clienttype","0");
        q.put("jsToken",jsToken); q.put("dp-logid",String.valueOf(System.currentTimeMillis()));
        if(withBd&&!empty(bdstoken))q.put("bdstoken",bdstoken);
        q.putAll(extra);
        String url=baseUrl+endpoint+"?"+form(q);
        HttpResp r=request("GET",url,null,apiHeaders(),2);
        JSONObject j=new JSONObject(r.body);
        int errno=j.optInt("errno",0);
        if(errno==-6||errno==111){
            refreshTokens();
            q.put("jsToken",jsToken);
            if(withBd&&!empty(bdstoken))q.put("bdstoken",bdstoken);
            r=request("GET",baseUrl+endpoint+"?"+form(q),null,apiHeaders(),2);
            j=new JSONObject(r.body);
            errno=j.optInt("errno",0);
        }
        if(errno!=0)throw new Exception("TeraBox API errno "+errno+" "+j.optString("show_msg",j.optString("errmsg","")));
        return j;
    }

    private void refreshTokens() throws Exception {
        HttpResp r=request("GET",baseUrl+"/main?category=all&path=%2F",null,null,5);
        String jt=first(r.body,
            "fn%28%22([A-Fa-f0-9]{32,512})%22%29",
            "fn\\(\\\"([A-Fa-f0-9]{32,512})\\\"\\)",
            "\\"jsToken\\\":\\\"([A-Fa-f0-9]{32,512})\\\"");
        if(empty(jt))throw new Exception("jsToken yenilenemedi.");
        jsToken=jt;
        String bd=first(r.body,"\\\"bdstoken\\\":\\\"([^\\\"]+)\\\"","bdstoken=([A-Fa-f0-9]{16,128})");
        if(!empty(bd))bdstoken=bd;
    }

    private LinkedHashMap<String,String> qrHeaders(){
        LinkedHashMap<String,String> h=new LinkedHashMap<>();
        h.put("Accept","application/json, text/plain, */*");
        h.put("Accept-Language","en-US,en;q=0.9");
        h.put("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");
        h.put("Origin",loginOrigin);
        h.put("Referer",loginPageUrl);
        h.put("X-Requested-With","XMLHttpRequest");
        return h;
    }
    private LinkedHashMap<String,String> apiHeaders(){
        LinkedHashMap<String,String> h=new LinkedHashMap<>();
        h.put("Accept","application/json, text/plain, */*");
        h.put("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");
        h.put("Origin",baseUrl);
        h.put("Referer",baseUrl+"/main?category=all&path=%2F");
        return h;
    }

    private static class HttpResp{String body,finalUrl;int code;}
    private HttpResp request(String method,String url,String body,Map<String,String> headers,int redirects) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setInstanceFollowRedirects(false);
        c.setConnectTimeout(15000); c.setReadTimeout(35000); c.setRequestMethod(method);
        c.setRequestProperty("User-Agent",UA);
        c.setRequestProperty("Connection","keep-alive");
        if(!cookies.isEmpty())c.setRequestProperty("Cookie",cookieHeader());
        if(headers!=null)for(Map.Entry<String,String> e:headers.entrySet())c.setRequestProperty(e.getKey(),e.getValue());
        if(body!=null){
            c.setDoOutput(true);
            byte[] b=body.getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(b.length);
            try(OutputStream o=c.getOutputStream()){o.write(b);}
        }
        int code=c.getResponseCode();
        captureCookies(c);
        String loc=c.getHeaderField("Location");
        if(code>=300&&code<400&&loc!=null&&redirects>0){
            URL next=new URL(new URL(url),loc);
            c.disconnect();
            String nm=(code==303||((code==301||code==302)&&"POST".equals(method)))?"GET":method;
            return request(nm,next.toString(),"GET".equals(nm)?null:body,headers,redirects-1);
        }
        InputStream in=code>=200&&code<400?c.getInputStream():c.getErrorStream();
        String txt=read(in);
        String finalUrl=url;
        c.disconnect();
        if(code<200||code>=400)throw new IOException("HTTP "+code+" "+txt.substring(0,Math.min(120,txt.length())));
        HttpResp r=new HttpResp();r.body=txt;r.finalUrl=finalUrl;r.code=code;return r;
    }

    private void captureCookies(HttpURLConnection c){
        Map<String,List<String>> hs=c.getHeaderFields();
        for(Map.Entry<String,List<String>> e:hs.entrySet()){
            if(e.getKey()==null||!"set-cookie".equalsIgnoreCase(e.getKey()))continue;
            for(String s:e.getValue()){
                String p=s.split(";",2)[0];
                int k=p.indexOf('=');
                if(k>0)cookies.put(p.substring(0,k).trim(),p.substring(k+1).trim());
            }
        }
    }
    private String cookieHeader(){
        StringBuilder b=new StringBuilder();
        for(Map.Entry<String,String> e:cookies.entrySet()){if(b.length()>0)b.append("; ");b.append(e.getKey()).append("=").append(e.getValue());}
        return b.toString();
    }
    private static String read(InputStream in)throws Exception{
        if(in==null)return "";
        ByteArrayOutputStream o=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;
        while((n=in.read(b))>0)o.write(b,0,n);
        return o.toString("UTF-8");
    }
    private static String match(String s,String p){try{Matcher m=Pattern.compile(p,Pattern.CASE_INSENSITIVE|Pattern.DOTALL).matcher(s);return m.find()?m.group(1):"";}catch(Exception e){return "";}}
    private static String first(String s,String...p){for(String x:p){String v=match(s,x);if(!empty(v))return v;}return "";}
    private static boolean empty(String s){return s==null||s.trim().isEmpty();}
    private static String form(Map<String,String> m)throws Exception{
        StringBuilder b=new StringBuilder();
        for(Map.Entry<String,String> e:m.entrySet()){if(b.length()>0)b.append("&");b.append(URLEncoder.encode(e.getKey(),"UTF-8")).append("=").append(URLEncoder.encode(e.getValue()==null?"":e.getValue(),"UTF-8"));}
        return b.toString();
    }
    private static LinkedHashMap<String,String> mapOf(String...kv){
        LinkedHashMap<String,String> m=new LinkedHashMap<>();
        for(int i=0;i+1<kv.length;i+=2)m.put(kv[i],kv[i+1]);
        return m;
    }
}

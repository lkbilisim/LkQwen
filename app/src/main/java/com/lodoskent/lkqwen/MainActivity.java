package com.lodoskent.lkqwen;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.widget.*;
import android.net.Uri;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static final String MODEL_URL = "https://huggingface.co/unsloth/Qwen3.5-2B-GGUF/resolve/718c63702535e6f3cc1318de25f020b258d6cdf5/Qwen3.5-2B-Q4_K_M.gguf";
    private static final String SHA = "cbb39d5102a6e6ef9675912b1c21d02335f4374c6bc267faa6cb96d7987d4a7b";
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final JSONArray history = new JSONArray();
    private volatile boolean canceled, closed;
    private volatile Process engine;
    private volatile HttpURLConnection active;
    private int port;
    private String key;
    private File model;
    private TextView status, transcript;
    private EditText prompt;
    private ScrollView scroll;
    private Button download, importModel, send, stop, clear;
    private boolean busy;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        model = new File(getFilesDir(), "model.gguf");
        getWindow().setStatusBarColor(0xff10221d);
        getWindow().setNavigationBarColor(0xff10221d);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(8));
        root.setBackgroundColor(0xff10221d);
        TextView title = new TextView(this);
        title.setText("LK Qwen");
        title.setTextSize(28);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(0xff40d695);
        root.addView(title);
        status = new TextView(this);
        status.setTextColor(0xffc8d8cf);
        status.setPadding(0,dp(6),0,dp(6));
        root.addView(status);
        LinearLayout tools = new LinearLayout(this);
        download = button("Model indir");
        importModel = button("GGUF seç");
        clear = button("Temizle");
        tools.addView(download,new LinearLayout.LayoutParams(0,-2,1));
        tools.addView(importModel,new LinearLayout.LayoutParams(0,-2,1));
        tools.addView(clear,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(tools);
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        transcript = new TextView(this);
        transcript.setTextSize(16);
        transcript.setTextColor(Color.WHITE);
        transcript.setTextIsSelectable(true);
        transcript.setPadding(dp(8),dp(12),dp(8),dp(12));
        scroll.addView(transcript);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        prompt = new EditText(this);
        prompt.setHint("Mesajını yaz...");
        prompt.setTextColor(Color.WHITE);
        prompt.setHintTextColor(0xffa9bdb3);
        prompt.setMaxLines(5);
        root.addView(prompt);
        LinearLayout actions = new LinearLayout(this);
        send = button("Gönder");
        stop = button("Durdur");
        actions.addView(send,new LinearLayout.LayoutParams(0,-2,2));
        actions.addView(stop,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(actions);
        setContentView(root);
        try {
            JSONArray old = new JSONArray(getPreferences(0).getString("history","[]"));
            for(int i=0;i<old.length();i++) history.put(old.getJSONObject(i));
        } catch(Exception ignored) {}
        render("");
        idle();
        download.setOnClickListener(v -> new AlertDialog.Builder(this)
            .setTitle("Qwen3.5 2B").setMessage("Yaklaşık 1,28 GB model internetten bir kez indirilir. Wi-Fi önerilir. Mevcut model varsa değiştirilir.")
            .setNegativeButton("Vazgeç",null).setPositiveButton("İndir",(d,w)-> startDownload()).show());
        importModel.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.setType("*/*"); i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(i,7);
        });
        clear.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Sohbeti sil?")
            .setNegativeButton("Vazgeç",null).setPositiveButton("Sil",(d,w)->{
                while(history.length()>0) history.remove(history.length()-1);
                persist(); render("");
            }).show());
        send.setOnClickListener(v -> sendMessage());
        stop.setOnClickListener(v -> { cancel(); status.setText("Durduruluyor..."); });
    }
    private int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    private Button button(String text) { Button b=new Button(this); b.setText(text); b.setAllCaps(false); return b; }
    private void setBusy(boolean value) {
        busy=value; send.setEnabled(!value && model.isFile());
        download.setEnabled(!value); importModel.setEnabled(!value); clear.setEnabled(!value); stop.setEnabled(value);
    }
    private void idle() { setBusy(false); status.setText(model.isFile()?"Model hazır • Yerel sohbet":"Model yok • İndir veya GGUF seç"); }
    private void ui(Runnable r) { runOnUiThread(()->{ if(!closed) r.run(); }); }
    private void begin(String text,Runnable action) { canceled=false; setBusy(true); status.setText(text); worker.execute(action); }
    private void fail(Exception e) { ui(()->{idle(); if(!canceled) status.setText("Hata: "+e.getMessage()); else status.setText("İşlem durduruldu.");}); }
    private void persist() { getPreferences(0).edit().putString("history",history.toString()).apply(); }
    private JSONObject message(String role,String content) throws JSONException { return new JSONObject().put("role",role).put("content",content); }
    private void render(String partial) {
        StringBuilder out=new StringBuilder();
        for(int i=0;i<history.length();i++) {
            JSONObject m=history.optJSONObject(i);
            if(m!=null) out.append("user".equals(m.optString("role"))?"Sen\n":"LK Qwen\n").append(m.optString("content")).append("\n\n");
        }
        if(!partial.isEmpty()) out.append("LK Qwen\n").append(partial);
        if(out.length()==0) out.append("Merhaba! Modelini hazırla, ardından çevrim dışı sohbet edebiliriz.");
        transcript.setText(out.toString()); scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));
    }
    private void ensureNotCanceled() throws IOException { if(canceled||closed) throw new IOException("Durduruldu"); }
    private void startDownload() {
        begin("Model indiriliyor...",()->{
            try {
                stopEngine();
                HttpURLConnection c=(HttpURLConnection)new URL(MODEL_URL).openConnection();
                active=c; c.setConnectTimeout(20000); c.setReadTimeout(30000);
                if(c.getResponseCode()!=200) throw new IOException("HTTP "+c.getResponseCode());
                try(InputStream in=c.getInputStream()) { install(in,c.getContentLengthLong(),SHA); }
                ui(()->idle());
            } catch(Exception e) { fail(e); }
            finally { HttpURLConnection c=active; active=null; if(c!=null)c.disconnect(); }
        });
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=7 || result!=RESULT_OK || data==null || data.getData()==null || busy) return;
        Uri uri=data.getData();
        begin("GGUF kopyalanıyor...",()->{
            try { stopEngine(); try(InputStream in=getContentResolver().openInputStream(uri)) {
                if(in==null) throw new IOException("Dosya açılamadı");
                install(in,-1,null);
            } ui(()->idle()); } catch(Exception e) { fail(e); }
        });
    }
    private void install(InputStream in,long length,String expected) throws Exception {
        File temp=new File(getFilesDir(),"model.part");
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            long total=0,last=0; byte[] b=new byte[1024*1024];
            try(FileOutputStream out=new FileOutputStream(temp)) {
                int n; while((n=in.read(b))!=-1) {
                    ensureNotCanceled(); out.write(b,0,n); digest.update(b,0,n); total+=n;
                    if(total-last>8*1024*1024) {
                        last=total; final long mb=total/1000000;
                        final String t=length>0?mb+" / "+length/1000000+" MB":mb+" MB";
                        ui(()->status.setText("Model: "+t));
                    }
                }
                out.getFD().sync();
            }
            ensureNotCanceled();
            if(length>0 && total!=length) throw new IOException("Eksik indirme");
            if(total<1024*1024) throw new IOException("Dosya çok küçük");
            try(RandomAccessFile f=new RandomAccessFile(temp,"r")){
                if(f.readInt()!=0x47475546) throw new IOException("GGUF dosyası değil");
            }
            StringBuilder hex=new StringBuilder();
            for(byte x:digest.digest())hex.append(String.format(java.util.Locale.ROOT,"%02x",x & 255));
            if(expected!=null && !expected.equals(hex.toString()))throw new IOException("SHA-256 doğrulaması başarısız");
            if(!temp.renameTo(model))throw new IOException("Model kaydedilemedi");
        } finally { if(temp.exists())temp.delete(); }
    }
    private void sendMessage() {
        String input=prompt.getText().toString().trim();
        if(input.isEmpty()||busy||!model.isFile())return;
        if(input.length()>3000){ status.setText("Mesaj çok uzun (en fazla 3000 karakter).");return; }
        try { history.put(message("user",input)); persist(); } catch(Exception e){return;}
        prompt.setText(""); render("");
        String snapshot=history.toString();
        begin("Yerel motor hazırlanıyor...",()->{
            StringBuilder answer=new StringBuilder();
            try {
                ensureEngine();
                ui(()->status.setText("Qwen cevap üretiyor..."));
                JSONArray h=new JSONArray(snapshot), messages=new JSONArray();
                messages.put(message("system","Sen LK Qwen, LK Bilişim'in yerel yapay zeka asistanısın. Kullanıcının dilinde açık, doğru ve kısa cevap ver. Bilmediğin şeyi uydurma."));
                int start=h.length()-1, budget=3000;
                for(int i=h.length()-1;i>=0;i--){
                    int len=h.getJSONObject(i).optString("content").length();
                    if(i!=h.length()-1 && len>budget)break;
                    start=i; budget-=len;
                }
                while(start<h.length()-1 && !"user".equals(h.getJSONObject(start).optString("role")))start++;
                for(int i=start;i<h.length();i++)messages.put(h.getJSONObject(i));
                JSONObject body=new JSONObject().put("messages",messages).put("stream",true).put("max_tokens",384).put("temperature",0.7).put("top_p",0.9);
                HttpURLConnection c=local("/v1/chat/completions"); active=c;
                c.setRequestMethod("POST"); c.setDoOutput(true); c.setRequestProperty("Content-Type","application/json");
                try(OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}
                if(c.getResponseCode()!=200)throw new IOException("Motor HTTP "+c.getResponseCode()+": "+readError(c));
                try(BufferedReader reader=new BufferedReader(new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8))){
                    String line; while((line=reader.readLine())!=null){
                        ensureNotCanceled(); if(!line.startsWith("data:"))continue;
                        String payload=line.substring(5).trim(); if("[DONE]".equals(payload))break;
                        JSONArray choices=new JSONObject(payload).optJSONArray("choices");
                        if(choices==null||choices.length()==0)continue;
                        JSONObject delta=choices.getJSONObject(0).optJSONObject("delta");
                        if(delta!=null && !delta.isNull("content")){
                            answer.append(delta.optString("content",""));
                            final String s=answer.toString();ui(()->render(s));
                        }
                    }
                }
                ensureNotCanceled();
                if(answer.length()==0)throw new IOException("Model boş cevap verdi");
                final String s=answer.toString();
                ui(()->{try{history.put(message("assistant",s));persist();}catch(Exception ignored){} render("");idle();});
            } catch(Exception e) {
                stopEngine();
                final String s=answer.toString();ui(()->{if(!s.isEmpty()){try{history.put(message("assistant",s+" [yarım kaldı]"));persist();}catch(Exception ignored){}}render("");});
                fail(e);
            } finally { HttpURLConnection c=active;active=null;if(c!=null)c.disconnect(); }
        });
    }
    private String readError(HttpURLConnection c) {
        try(InputStream in=c.getErrorStream()){if(in==null)return "";byte[] b=new byte[1024];int n=in.read(b);return n>0?new String(b,0,n,StandardCharsets.UTF_8):"";}catch(Exception e){return "";}
    }
    private HttpURLConnection local(String path)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL("http://127.0.0.1:"+port+path).openConnection(java.net.Proxy.NO_PROXY);
        c.setConnectTimeout(3000);c.setReadTimeout(180000);c.setRequestProperty("Authorization","Bearer "+key);return c;
    }
    private void ensureEngine()throws Exception {
        ensureNotCanceled();
        if(engine!=null&&engine.isAlive())return;
        try(ServerSocket socket=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){port=socket.getLocalPort();}
        key=UUID.randomUUID().toString()+UUID.randomUUID().toString();
        File binary=new File(getApplicationInfo().nativeLibraryDir,"liblkqwen.so");
        if(!binary.canExecute())throw new IOException("ARM64 motoru APK içinde bulunamadı");
        File log=new File(getCacheDir(),"engine.log");
        ProcessBuilder pb=new ProcessBuilder(binary.getAbsolutePath(),"-m",model.getAbsolutePath(),
            "--host","127.0.0.1","--port",Integer.toString(port),"--api-key",key,
            "-c","2048","-t","4","-tb","4","-ngl","0","-np","1","-b","128","-ub","64","--reasoning-budget","0","--chat-template-kwargs","{\"enable_thinking\":false}");
        pb.directory(getFilesDir());pb.redirectErrorStream(true);pb.redirectOutput(log);
        engine=pb.start();
        for(int i=0;i<240;i++){
            ensureNotCanceled();
            if(!engine.isAlive())throw new IOException("Motor açılamadı. "+tail(log));
            HttpURLConnection c=null;
            try { c=local("/health");c.setReadTimeout(1000);if(c.getResponseCode()==200)return; }
            catch(IOException ignored){}finally{if(c!=null)c.disconnect();}
            Thread.sleep(500);
        }
        throw new IOException("Model yükleme zaman aşımı. "+tail(log));
    }
    private String tail(File f){try(RandomAccessFile r=new RandomAccessFile(f,"r")){int n=(int)Math.min(r.length(),700);byte[] b=new byte[n];r.seek(r.length()-n);r.readFully(b);return new String(b,StandardCharsets.UTF_8);}catch(Exception e){return "";}}
    private synchronized void stopEngine(){Process p=engine;engine=null;if(p!=null)p.destroyForcibly();}
    private void cancel(){canceled=true;HttpURLConnection c=active;if(c!=null)c.disconnect();stopEngine();}
    @Override protected void onDestroy(){closed=true;cancel();worker.shutdownNow();super.onDestroy();}
}

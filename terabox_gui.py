import os, sys, json, time, threading, subprocess, urllib.request, urllib.error
from pathlib import Path
import tkinter as tk
from tkinter import ttk, filedialog, messagebox

def resource(p):
    base = Path(getattr(sys, "_MEIPASS", Path(__file__).resolve().parent))
    return base / p

class App:
    def __init__(self, root):
        self.root = root
        root.title("TeraBox Hesap Yedekleyici")
        root.geometry("760x540")
        root.minsize(650, 440)
        self.proc = None
        self.aria = None
        self.running = False
        self.rpc_secret = "tb-local-" + str(os.getpid())
        self.dest = tk.StringVar(value=str(Path.home() / "TeraBox_Yedek"))
        self.cookie = tk.StringVar()
        self.status = tk.StringVar(value="Cookie ve kayıt klasörünü seçip başlat.")
        frm = ttk.Frame(root, padding=14); frm.pack(fill="both", expand=True)
        ttk.Label(frm, text="TeraBox ndus cookie").pack(anchor="w")
        ttk.Entry(frm, textvariable=self.cookie, show="•").pack(fill="x", pady=(3,10))
        ttk.Label(frm, text="İndirme klasörü").pack(anchor="w")
        line = ttk.Frame(frm); line.pack(fill="x", pady=(3,10))
        ttk.Entry(line, textvariable=self.dest).pack(side="left", fill="x", expand=True)
        ttk.Button(line, text="Gözat", command=self.choose).pack(side="left", padx=(8,0))
        buttons = ttk.Frame(frm); buttons.pack(fill="x", pady=4)
        self.start_btn = ttk.Button(buttons, text="Hesabı Tara ve İndir", command=self.start); self.start_btn.pack(side="left")
        self.stop_btn = ttk.Button(buttons, text="Durdur", command=self.stop, state="disabled"); self.stop_btn.pack(side="left", padx=8)
        ttk.Label(frm, textvariable=self.status).pack(anchor="w", pady=8)
        self.log = tk.Text(frm, height=18, wrap="word", state="disabled")
        self.log.pack(fill="both", expand=True)
        ttk.Label(frm, text="Cookie yalnızca bu bilgisayarda kullanılır; sohbet veya sunucuya gönderilmez.").pack(anchor="w", pady=(8,0))
        root.protocol("WM_DELETE_WINDOW", self.close)

    def choose(self):
        p = filedialog.askdirectory()
        if p: self.dest.set(p)

    def write(self, s):
        self.root.after(0, self._write, s)
    def _write(self, s):
        self.log.configure(state="normal"); self.log.insert("end", s); self.log.see("end"); self.log.configure(state="disabled")

    def start(self):
        cookie = self.cookie.get().strip()
        dest = Path(self.dest.get().strip())
        if not cookie:
            messagebox.showwarning("Cookie gerekli", "TeraBox ndus cookie değerini gir.")
            return
        if not dest:
            messagebox.showwarning("Klasör gerekli", "İndirme klasörünü seç.")
            return
        try: dest.mkdir(parents=True, exist_ok=True)
        except Exception as e:
            messagebox.showerror("Klasör hatası", str(e)); return
        self.start_btn.configure(state="disabled"); self.stop_btn.configure(state="normal")
        self.status.set("TeraBox bağlantısı hazırlanıyor…")
        threading.Thread(target=self.worker, args=(cookie, dest), daemon=True).start()

    def worker(self, cookie, dest):
        try:
            engine = resource("terabox-node")
            config = {
                "accounts": {"main": cookie},
                "aria2": {"url": "http://127.0.0.1:6800/jsonrpc", "secret": self.rpc_secret}
            }
            (engine / "app" / ".config.yaml").write_text(
                "accounts:\n  main: " + json.dumps(cookie, ensure_ascii=False) +
                "\naria2:\n  url: http://127.0.0.1:6800/jsonrpc\n  secret: " + self.rpc_secret + "\n",
                encoding="utf-8")
            session = dest / ".terabox-aria2.session"
            aria = str(resource("aria2c.exe"))
            node = str(resource("node.exe"))
            self.aria = subprocess.Popen([
                aria, "--enable-rpc=true", "--rpc-listen-all=false", "--rpc-listen-port=6800",
                "--rpc-secret=" + self.rpc_secret, "--dir=" + str(dest), "--continue=true",
                "--always-resume=true", "--save-session=" + str(session),
                "--input-file=" + str(session), "--save-session-interval=60",
                "--max-connection-per-server=8", "--split=8", "--max-concurrent-downloads=4",
                "--console-log-level=notice", "--summary-interval=30"
            ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
            time.sleep(2)
            self.write("TeraBox hesabı kontrol ediliyor…\n")
            self.proc = subprocess.Popen([node, "app/app-getdl.js", "-a", "main", "-r", "/"],
                cwd=str(engine), stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                text=True, encoding="utf-8", errors="replace",
                creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
            for line in self.proc.stdout:
                self.write(line)
            code = self.proc.wait()
            if code == 0:
                self.write("\nTarama komutu tamamlandı. Aria2 kuyruğunu kontrol et.\n")
                self.root.after(0, self.status.set, "Tarama komutu tamamlandı.")
            else:
                self.write("\nTarama işlemi hata koduyla kapandı: " + str(code) + "\n")
                self.root.after(0, self.status.set, "Tarama başarısız; günlükteki hatayı kontrol et.")
        except Exception as e:
            self.write("\nHATA: " + str(e) + "\n")
            self.root.after(0, self.status.set, "Başlatılamadı.")
        finally:
            self.root.after(0, self.start_btn.configure, {"state":"normal"})
            self.root.after(0, self.stop_btn.configure, {"state":"disabled"})

    def stop(self):
        for p in (self.proc, self.aria):
            try:
                if p and p.poll() is None: p.terminate()
            except: pass
        self.status.set("Durduruldu. Aria2 oturum dosyası kaldığı için devam ettirilebilir.")
        self.write("\nDurdurma istendi.\n")

    def close(self):
        if self.aria and self.aria.poll() is None:
            if not messagebox.askyesno("Kapat", "İndirme motoru da kapatılacak. Devam edilsin mi?"): return
            try: self.aria.terminate()
            except: pass
        self.root.destroy()

if __name__ == "__main__":
    r = tk.Tk()
    App(r)
    r.mainloop()

import os
import shutil
import threading
import queue
import tkinter as tk
from tkinter import ttk, filedialog, messagebox
from pathlib import Path

APP_TITLE = "ATMACA | Gorsel Yoneticisi"
IMAGE_EXTS = {
    ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp", ".tif", ".tiff",
    ".heic", ".heif", ".jfif", ".avif", ".raw", ".cr2", ".nef", ".arw",
    ".dng", ".orf", ".rw2", ".pef", ".srw"
}

class PhotoManager:
    def __init__(self, root):
        self.root = root
        self.root.title(APP_TITLE)
        self.root.geometry("1000x680")
        self.root.minsize(820, 560)
        self.root.configure(bg="#071b3a")
        self.source = tk.StringVar()
        self.destination = tk.StringVar()
        self.query = tk.StringVar()
        self.recursive = tk.BooleanVar(value=False)
        self.status = tk.StringVar(value="Kaynak ve hedef klasorlerini secin.")
        self.results = []
        self.busy = False
        self.events = queue.Queue()
        self._style()
        self._build()
        self.root.after(100, self._process_events)

    def _style(self):
        style = ttk.Style()
        style.theme_use("clam")
        style.configure("Treeview", background="#10294e", foreground="#f5f7fb",
                        fieldbackground="#10294e", rowheight=27, borderwidth=0)
        style.configure("Treeview.Heading", background="#f4c400", foreground="#071b3a",
                        font=("Segoe UI", 10, "bold"))
        style.map("Treeview", background=[("selected", "#d7a900")],
                  foreground=[("selected", "#071b3a")])
        style.configure("TProgressbar", troughcolor="#10294e",
                        background="#f4c400", thickness=13)

    def _build(self):
        header = tk.Frame(self.root, bg="#071b3a")
        header.pack(fill="x", padx=22, pady=(18, 8))
        tk.Label(header, text="ATMACA", bg="#071b3a", fg="#f4c400",
                 font=("Segoe UI", 25, "bold")).pack(side="left")
        tk.Label(header, text="  GORSEL YONETICISI", bg="#071b3a", fg="white",
                 font=("Segoe UI", 16, "bold")).pack(side="left", pady=(7, 0))

        panel = tk.Frame(self.root, bg="#10294e", padx=16, pady=14)
        panel.pack(fill="x", padx=22, pady=8)
        self._path_row(panel, "Kaynak klasor", self.source, 0)
        self._path_row(panel, "Hedef klasor", self.destination, 1)
        opts = tk.Frame(panel, bg="#10294e")
        opts.grid(row=2, column=0, columnspan=3, sticky="w", pady=(10, 0))
        tk.Checkbutton(opts, text="Alt klasorleri de tara", variable=self.recursive,
                       bg="#10294e", fg="white", selectcolor="#071b3a",
                       activebackground="#10294e", activeforeground="#f4c400").pack(side="left")

        search = tk.Frame(self.root, bg="#071b3a")
        search.pack(fill="x", padx=22, pady=(10, 5))
        tk.Label(search, text="Gorsel ismi ara:", bg="#071b3a", fg="white",
                 font=("Segoe UI", 11, "bold")).pack(side="left")
        entry = tk.Entry(search, textvariable=self.query, font=("Segoe UI", 11),
                         bg="white", fg="#071b3a", relief="flat")
        entry.pack(side="left", fill="x", expand=True, padx=10, ipady=7)
        entry.bind("<Return>", lambda e: self.search())
        self._button(search, "ARA", self.search).pack(side="left", padx=4)
        self._button(search, "LISTEYI TEMIZLE", self.clear).pack(side="left", padx=4)

        list_frame = tk.Frame(self.root, bg="#10294e", padx=10, pady=10)
        list_frame.pack(fill="both", expand=True, padx=22, pady=8)
        columns = ("name", "folder", "size")
        self.tree = ttk.Treeview(list_frame, columns=columns, show="headings", selectmode="extended")
        self.tree.heading("name", text="Gorsel adi")
        self.tree.heading("folder", text="Bulundugu klasor")
        self.tree.heading("size", text="Boyut")
        self.tree.column("name", width=260, minwidth=160)
        self.tree.column("folder", width=570, minwidth=250)
        self.tree.column("size", width=90, anchor="e")
        yscroll = ttk.Scrollbar(list_frame, orient="vertical", command=self.tree.yview)
        xscroll = ttk.Scrollbar(list_frame, orient="horizontal", command=self.tree.xview)
        self.tree.configure(yscrollcommand=yscroll.set, xscrollcommand=xscroll.set)
        self.tree.grid(row=0, column=0, sticky="nsew")
        yscroll.grid(row=0, column=1, sticky="ns")
        xscroll.grid(row=1, column=0, sticky="ew")
        list_frame.rowconfigure(0, weight=1)
        list_frame.columnconfigure(0, weight=1)
        self.tree.bind("<Double-1>", self.open_selected)

        actions = tk.Frame(self.root, bg="#071b3a")
        actions.pack(fill="x", padx=22, pady=(3, 8))
        self._button(actions, "SECILENLERI TASI", self.move_selected, big=True).pack(side="left", padx=(0, 8))
        self._button(actions, "ARAMA SONUCLARINI TASI", self.move_results).pack(side="left", padx=8)
        self._button(actions, "KAYNAKTAKI TUM GORSELLERI TASI", self.move_all).pack(side="left", padx=8)
        self.progress = ttk.Progressbar(self.root, mode="determinate")
        self.progress.pack(fill="x", padx=22, pady=(4, 4))
        tk.Label(self.root, textvariable=self.status, bg="#071b3a", fg="#f4c400",
                 anchor="w", font=("Segoe UI", 10)).pack(fill="x", padx=22, pady=(2, 14))

    def _path_row(self, parent, label, variable, row):
        tk.Label(parent, text=label, bg="#10294e", fg="white",
                 font=("Segoe UI", 10, "bold"), width=16, anchor="w").grid(row=row, column=0, sticky="w", pady=5)
        tk.Entry(parent, textvariable=variable, bg="white", fg="#071b3a",
                 relief="flat").grid(row=row, column=1, sticky="ew", padx=8, ipady=6)
        self._button(parent, "KLASOR SEC", lambda v=variable: self.pick(v)).grid(row=row, column=2, padx=4)
        parent.columnconfigure(1, weight=1)

    def _button(self, parent, text, command, big=False):
        return tk.Button(parent, text=text, command=command, bg="#f4c400", fg="#071b3a",
                         activebackground="#d7a900", activeforeground="#071b3a",
                         relief="flat", bd=0, padx=13 if big else 10, pady=10 if big else 8,
                         font=("Segoe UI", 9, "bold"), cursor="hand2")

    def pick(self, variable):
        path = filedialog.askdirectory()
        if path:
            variable.set(path)

    def _files(self, root_path, recursive):
        root = Path(root_path)
        if not root.is_dir():
            raise ValueError("Gecerli bir kaynak klasor secin.")
        iterator = root.rglob("*") if recursive else root.iterdir()
        found = []
        for p in iterator:
            try:
                if p.is_file() and p.suffix.lower() in IMAGE_EXTS:
                    found.append(p)
            except OSError:
                continue
        return found

    def _start_scan(self, mode):
        if self.busy:
            return
        if not Path(self.source.get().strip()).is_dir():
            messagebox.showerror("Kaynak klasor", "Gecerli bir kaynak klasor secin.")
            return
        self.busy = True
        self.status.set("Klasor taraniyor... Uygulama yanit vermeye devam edecek.")
        root_path = self.source.get().strip()
        recursive = self.recursive.get()
        threading.Thread(target=self._scan_worker, args=(mode, root_path, recursive), daemon=True).start()

    def _scan_worker(self, mode, root_path, recursive):
        try:
            self.events.put(("scan_done", mode, self._files(root_path, recursive), None))
        except Exception as exc:
            self.events.put(("scan_done", mode, [], str(exc)))

    def search(self):
        self._start_scan("search")

    def _show(self, files):
        self.tree.delete(*self.tree.get_children())
        self.results = list(files)
        self._render_files = self.results
        self._render_index = 0
        self.status.set(f"Liste hazirlaniyor... {len(self.results):,} gorsel")
        self.root.after(1, self._render_chunk)

    def _render_chunk(self):
        end = min(self._render_index + 300, len(self._render_files))
        for p in self._render_files[self._render_index:end]:
            try:
                size = f"{p.stat().st_size / 1024:,.0f} KB"
            except OSError:
                size = "?"
            self.tree.insert("", "end", values=(p.name, str(p.parent), size))
        self._render_index = end
        if end < len(self._render_files):
            self.status.set(f"Liste ekleniyor... {end:,} / {len(self._render_files):,}")
            self.root.after(1, self._render_chunk)
        else:
            self.busy = False
            self.status.set(f"Arama tamamlandi: {len(self.results):,} gorsel bulundu. Cift tikla ac.")

    def clear(self):
        self.query.set("")
        self.results = []
        self.tree.delete(*self.tree.get_children())
        self.status.set("Liste temizlendi.")

    def open_selected(self, _event=None):
        selected = self.tree.selection()
        if not selected:
            return
        vals = self.tree.item(selected[0], "values")
        path = Path(vals[1]) / vals[0]
        if path.exists():
            os.startfile(str(path))

    def _move(self, files, label):
        if self.busy:
            return
        dest = Path(self.destination.get().strip())
        if not dest.is_dir():
            messagebox.showerror("Hedef klasor", "Gecerli bir hedef klasor secin.")
            return
        source_root = Path(self.source.get().strip())
        if not source_root.is_dir():
            messagebox.showerror("Kaynak klasor", "Gecerli bir kaynak klasor secin.")
            return
        files = [Path(p) for p in files if Path(p).is_file()]
        if not files:
            messagebox.showinfo("Bilgi", "Tasinacak gorsel bulunamadi.")
            return
        if source_root == dest or source_root in dest.parents or dest in source_root.parents:
            messagebox.showerror("Klasor cakismasi", "Kaynak ve hedef klasorler birbirinin icinde olamaz.")
            return
        collisions = sum(1 for p in files if (dest / p.name).exists())
        msg = (f"{len(files):,} gorsel hedef klasore TASINACAK.\\n"
               f"Ayni isimli dosyalarin uzerine yazilmayacak; +1, +2... eklenecek.\\n"
               f"Mevcut isim cakismasi: {collisions:,}\\n\\nDevam edilsin mi?")
        if not messagebox.askyesno("Tasima onayi", msg, icon="warning"):
            return
        self.busy = True
        self.progress["maximum"] = len(files)
        self.progress["value"] = 0
        self.status.set("Tasima basladi...")
        threading.Thread(target=self._worker, args=(files, dest), daemon=True).start()

    @staticmethod
    def _unique_target(dest, filename):
        original = Path(filename)
        candidate = dest / filename
        if not candidate.exists():
            return candidate
        number = 1
        while True:
            candidate = dest / f"{original.stem}+{number}{original.suffix}"
            if not candidate.exists():
                return candidate
            number += 1

    def _worker(self, files, dest):
        moved = renamed = failed = 0
        errors = []
        for i, src in enumerate(files, 1):
            try:
                target = self._unique_target(dest, src.name)
                if src.resolve() == target.resolve():
                    continue
                if src.stat().st_dev == dest.stat().st_dev:
                    src.rename(target)
                else:
                    temp = dest / (".atmacatmp_" + target.name)
                    number = 1
                    while temp.exists():
                        temp = dest / f".atmacatmp_{number}_" + target.name
                        number += 1
                    shutil.copy2(src, temp)
                    temp.rename(target)
                    src.unlink()
                moved += 1
                if target.name != src.name:
                    renamed += 1
            except Exception as exc:
                failed += 1
                if len(errors) < 8:
                    errors.append(f"{src}: {exc}")
            self.events.put(("progress", i, len(files)))
        self.events.put(("finished", moved, renamed, failed, errors))

    def _process_events(self):
        try:
            while True:
                event = self.events.get_nowait()
                if event[0] == "scan_done":
                    _, mode, files, error = event
                    if error:
                        self.busy = False
                        messagebox.showerror("Tarama hatasi", error)
                        self.status.set("Tarama tamamlanamadi.")
                    elif mode == "search":
                        term = self.query.get().strip().casefold()
                        self._show([p for p in files if term in p.name.casefold()])
                    else:
                        self.busy = False
                        self._move(files, "all")
                elif event[0] == "progress":
                    _, value, total = event
                    self.progress["value"] = value
                    self.status.set(f"Tasiniyor... {value:,} / {total:,}")
                elif event[0] == "finished":
                    _, moved, renamed, failed, errors = event
                    self.busy = False
                    self.status.set(f"Tamamlandi. Tasindi: {moved:,} | Yeni ad verilen: {renamed:,} | Hata: {failed:,}")
                    msg = f"Tasindi: {moved:,}\\nYeni ad verildi (+1, +2...): {renamed:,}\\nHata: {failed:,}"
                    if errors:
                        msg += "\\n\\nIlk hatalar:\\n" + "\\n".join(errors)
                    messagebox.showinfo("Islem tamamlandi", msg)
                    self.search()
        except queue.Empty:
            pass
        self.root.after(100, self._process_events)

    def move_selected(self):
        selected = self.tree.selection()
        files = []
        for item in selected:
            vals = self.tree.item(item, "values")
            files.append(Path(vals[1]) / vals[0])
        self._move(files, "selected")

    def move_results(self):
        self._move(self.results, "results")

    def move_all(self):
        self._start_scan("move_all")

if __name__ == "__main__":
    root = tk.Tk()
    app = PhotoManager(root)
    root.mainloop()

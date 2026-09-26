using System;
using System.Collections.Generic;
using System.Drawing;
using System.IO;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace AtmacaBosKlasor
{
    static class Program
    {
        [STAThread]
        static void Main()
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new MainForm());
        }
    }

    public class MainForm : Form
    {
        TextBox txtRoot = new TextBox();
        Button btnPick = new Button();
        Button btnScan = new Button();
        Button btnClean = new Button();
        Button btnStop = new Button();
        CheckBox chkRecursive = new CheckBox();
        ListBox list = new ListBox();
        Label lblStatus = new Label();
        ProgressBar bar = new ProgressBar();
        CancellationTokenSource cts;
        readonly List<string> found = new List<string>();

        public MainForm()
        {
            Text = "ATMACA Boş Klasör Temizleyici";
            Width = 820;
            Height = 600;
            MinimumSize = new Size(760, 520);
            StartPosition = FormStartPosition.CenterScreen;
            Font = new Font("Segoe UI", 10F);
            BackColor = Color.FromArgb(247,249,252);
            Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath);

            var header = new Panel { Dock=DockStyle.Top, Height=78, BackColor=Color.FromArgb(9,37,74) };
            header.Controls.Add(new Label {
                Text="ATMACA BOŞ KLASÖR TEMİZLEYİCİ",
                ForeColor=Color.FromArgb(255,210,0),
                Font=new Font("Segoe UI Semibold",18F,FontStyle.Bold),
                AutoSize=true, Left=22, Top=15
            });
            header.Controls.Add(new Label {
                Text="Boş klasörleri bulur • Alt klasörleri güvenli sırayla temizler",
                ForeColor=Color.WhiteSmoke, AutoSize=true, Left=25, Top=49
            });
            Controls.Add(header);

            var body = new Panel { Dock=DockStyle.Fill, Padding=new Padding(22,18,22,18) };
            Controls.Add(body);

            var lbl = new Label { Text="Ana klasör", AutoSize=true, Left=0, Top=10, ForeColor=Color.FromArgb(45,58,76) };
            body.Controls.Add(lbl);

            txtRoot.SetBounds(0,37,625,32);
            btnPick.Text="Seç";
            btnPick.SetBounds(640,36,110,34);
            btnPick.BackColor=Color.FromArgb(255,210,0);
            btnPick.FlatStyle=FlatStyle.Flat;
            btnPick.FlatAppearance.BorderSize=0;
            btnPick.Click += (s,e)=>Pick();
            body.Controls.Add(txtRoot); body.Controls.Add(btnPick);

            chkRecursive.Text="Alt klasörlerin tamamını tara";
            chkRecursive.Checked=true;
            chkRecursive.AutoSize=true;
            chkRecursive.SetBounds(0,84,260,28);
            body.Controls.Add(chkRecursive);

            btnScan.Text="TARA";
            btnScan.Font=new Font("Segoe UI Semibold",10F,FontStyle.Bold);
            btnScan.BackColor=Color.FromArgb(9,37,74);
            btnScan.ForeColor=Color.White;
            btnScan.FlatStyle=FlatStyle.Flat;
            btnScan.FlatAppearance.BorderSize=0;
            btnScan.SetBounds(0,126,130,38);
            btnScan.Click += async (s,e)=>await Scan();

            btnClean.Text="BULUNANLARI TEMİZLE";
            btnClean.Enabled=false;
            btnClean.BackColor=Color.FromArgb(255,210,0);
            btnClean.FlatStyle=FlatStyle.Flat;
            btnClean.FlatAppearance.BorderSize=0;
            btnClean.SetBounds(145,126,205,38);
            btnClean.Click += async (s,e)=>await Clean();

            btnStop.Text="DURDUR";
            btnStop.Enabled=false;
            btnStop.BackColor=Color.FromArgb(224,229,237);
            btnStop.FlatStyle=FlatStyle.Flat;
            btnStop.FlatAppearance.BorderSize=0;
            btnStop.SetBounds(365,126,115,38);
            btnStop.Click += (s,e)=>cts?.Cancel();

            body.Controls.Add(btnScan); body.Controls.Add(btnClean); body.Controls.Add(btnStop);

            list.SetBounds(0,180,750,250);
            list.HorizontalScrollbar=true;
            body.Controls.Add(list);

            bar.SetBounds(0,444,750,20);
            bar.Style=ProgressBarStyle.Marquee;
            bar.MarqueeAnimationSpeed=0;
            body.Controls.Add(bar);

            lblStatus.Text="Hazır.";
            lblStatus.AutoEllipsis=true;
            lblStatus.SetBounds(0,475,750,44);
            body.Controls.Add(lblStatus);
        }

        void Pick()
        {
            using(var d=new FolderBrowserDialog())
            {
                d.Description="Taranacak ana klasörü seç";
                if(Directory.Exists(txtRoot.Text)) d.SelectedPath=txtRoot.Text;
                if(d.ShowDialog(this)==DialogResult.OK) txtRoot.Text=d.SelectedPath;
            }
        }

        void SetBusy(bool busy)
        {
            btnScan.Enabled=!busy;
            btnClean.Enabled=!busy && found.Count>0;
            btnPick.Enabled=!busy;
            btnStop.Enabled=busy;
            bar.MarqueeAnimationSpeed=busy ? 25 : 0;
        }

        async Task Scan()
        {
            string root=txtRoot.Text.Trim();
            if(!Directory.Exists(root))
            {
                MessageBox.Show(this,"Ana klasör bulunamadı.","ATMACA",MessageBoxButtons.OK,MessageBoxIcon.Information);
                return;
            }

            found.Clear();
            list.Items.Clear();
            SetBusy(true);
            cts=new CancellationTokenSource();
            var token=cts.Token;
            var progress=new Progress<ScanInfo>(x=>{
                lblStatus.Text="Taranan: "+x.Scanned.ToString("N0")+"  •  Boş: "+x.Empty.ToString("N0")+"  •  "+x.Current;
            });

            try
            {
                var result=await Task.Run(()=>FindEmpty(root,chkRecursive.Checked,token,progress));
                found.AddRange(result);
                foreach(var p in found) list.Items.Add(p);
                lblStatus.Text=found.Count.ToString("N0")+" boş klasör bulundu.";
            }
            catch(OperationCanceledException)
            {
                lblStatus.Text="Tarama durduruldu.";
            }
            catch(Exception ex)
            {
                MessageBox.Show(this,ex.Message,"ATMACA",MessageBoxButtons.OK,MessageBoxIcon.Information);
                lblStatus.Text="Hata oluştu.";
            }
            finally
            {
                cts.Dispose(); cts=null; SetBusy(false);
            }
        }

        List<string> FindEmpty(string root,bool recursive,CancellationToken token,IProgress<ScanInfo> progress)
        {
            var result=new List<string>();
            long scanned=0;

            if(!recursive)
            {
                foreach(var d in SafeDirs(root))
                {
                    token.ThrowIfCancellationRequested();
                    scanned++;
                    if(IsEmptyNow(d)) result.Add(d);
                    if(scanned%100==0) progress.Report(new ScanInfo(scanned,result.Count,d));
                }
                progress.Report(new ScanInfo(scanned,result.Count,root));
                return result;
            }

            var stack=new Stack<Tuple<string,bool>>();
            foreach(var d in SafeDirs(root)) stack.Push(Tuple.Create(d,false));

            while(stack.Count>0)
            {
                token.ThrowIfCancellationRequested();
                var item=stack.Pop();
                string dir=item.Item1;

                if(!item.Item2)
                {
                    stack.Push(Tuple.Create(dir,true));
                    foreach(var child in SafeDirs(dir))
                        stack.Push(Tuple.Create(child,false));
                }
                else
                {
                    scanned++;
                    if(IsEmptyNow(dir)) result.Add(dir);
                    if(scanned%100==0) progress.Report(new ScanInfo(scanned,result.Count,dir));
                }
            }

            progress.Report(new ScanInfo(scanned,result.Count,root));
            return result;
        }

        IEnumerable<string> SafeDirs(string path)
        {
            try { return Directory.EnumerateDirectories(path); }
            catch { return Array.Empty<string>(); }
        }

        bool IsEmptyNow(string path)
        {
            try
            {
                using(var e=Directory.EnumerateFileSystemEntries(path).GetEnumerator())
                    return !e.MoveNext();
            }
            catch { return false; }
        }

        async Task Clean()
        {
            if(found.Count==0) return;

            var q=MessageBox.Show(this,
                found.Count.ToString("N0")+" boş klasör silinecek.\n\nDosyalara dokunulmaz. Devam edilsin mi?",
                "ATMACA",MessageBoxButtons.YesNo,MessageBoxIcon.Question);
            if(q!=DialogResult.Yes) return;

            SetBusy(true);
            cts=new CancellationTokenSource();
            var token=cts.Token;
            var progress=new Progress<ScanInfo>(x=>{
                lblStatus.Text="Kontrol edilen: "+x.Scanned.ToString("N0")+"  •  Silinen: "+x.Empty.ToString("N0");
            });

            try
            {
                int deleted=await Task.Run(()=>DeleteStillEmpty(found,token,progress));
                lblStatus.Text=deleted.ToString("N0")+" boş klasör silindi.";
                MessageBox.Show(this,deleted.ToString("N0")+" boş klasör silindi.","ATMACA",MessageBoxButtons.OK,MessageBoxIcon.Information);
                await Scan();
            }
            catch(OperationCanceledException) { lblStatus.Text="Temizleme durduruldu."; }
            finally
            {
                if(cts!=null){ cts.Dispose(); cts=null; }
                SetBusy(false);
            }
        }

        int DeleteStillEmpty(List<string> paths,CancellationToken token,IProgress<ScanInfo> progress)
        {
            int deleted=0; long checkedCount=0;

            paths.Sort((a,b)=>b.Length.CompareTo(a.Length));
            foreach(var p in paths)
            {
                token.ThrowIfCancellationRequested();
                checkedCount++;
                try
                {
                    if(Directory.Exists(p) && IsEmptyNow(p))
                    {
                        Directory.Delete(p,false);
                        deleted++;
                    }
                }
                catch { }

                if(checkedCount%100==0 || checkedCount==paths.Count)
                    progress.Report(new ScanInfo(checkedCount,deleted,p));
            }
            return deleted;
        }

        class ScanInfo
        {
            public long Scanned;
            public long Empty;
            public string Current;
            public ScanInfo(long scanned,long empty,string current)
            { Scanned=scanned; Empty=empty; Current=current; }
        }
    }
}

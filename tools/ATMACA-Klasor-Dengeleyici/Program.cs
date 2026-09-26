using System;
using System.Collections.Generic;
using System.Drawing;
using System.IO;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace AtmacaFolderBalance
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
    Button btnBalance50 = new Button();
    Button btnBalance100 = new Button();
    Button btnStop = new Button();
    CheckBox chkRecursive = new CheckBox();
    ListBox list = new ListBox();
    Label lbl = new Label();
    ProgressBar bar = new ProgressBar();
    CancellationTokenSource cts;
    List<FolderInfo> folders = new List<FolderInfo>();

    public MainForm()
    {
      Text = "ATMACA Klasör Dengeleyici";
      Width = 900;
      Height = 640;
      MinimumSize = new Size(820, 560);
      StartPosition = FormStartPosition.CenterScreen;
      Font = new Font("Segoe UI", 10F);
      BackColor = Color.FromArgb(247,249,252);
      Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath);

      var header = new Panel { Dock=DockStyle.Top, Height=76, BackColor=Color.FromArgb(9,37,74) };
      header.Controls.Add(new Label {
        Text="ATMACA KLASÖR DENGELEYİCİ",
        ForeColor=Color.FromArgb(255,210,0),
        Font=new Font("Segoe UI Semibold",18F,FontStyle.Bold),
        AutoSize=true, Left=22, Top=15
      });
      header.Controls.Add(new Label {
        Text="Dosya say • Az/faz klasörleri bul • 50 veya 100 dosyalık klasörlere yeniden dağıt",
        ForeColor=Color.WhiteSmoke, AutoSize=true, Left=25, Top=49
      });
      Controls.Add(header);

      var body = new Panel { Dock=DockStyle.Fill, Padding=new Padding(22,18,22,18) };
      Controls.Add(body);

      body.Controls.Add(new Label { Text="Ana klasör", AutoSize=true, Left=0, Top=6 });
      txtRoot.SetBounds(0,32,690,32);
      btnPick.Text="Seç";
      btnPick.SetBounds(705,31,110,34);
      btnPick.BackColor=Color.FromArgb(255,210,0);
      btnPick.FlatStyle=FlatStyle.Flat;
      btnPick.FlatAppearance.BorderSize=0;
      btnPick.Click += (s,e)=>Pick();
      body.Controls.Add(txtRoot);
      body.Controls.Add(btnPick);

      chkRecursive.Text="Alt klasörleri tara";
      chkRecursive.Checked=false;
      chkRecursive.AutoSize=true;
      chkRecursive.SetBounds(0,78,180,28);
      body.Controls.Add(chkRecursive);

      btnScan.Text="SAY / TARA";
      btnScan.SetBounds(0,120,135,40);
      btnScan.BackColor=Color.FromArgb(9,37,74);
      btnScan.ForeColor=Color.White;
      btnScan.FlatStyle=FlatStyle.Flat;
      btnScan.FlatAppearance.BorderSize=0;
      btnScan.Click += async (s,e)=>await Scan();

      btnBalance50.Text="TAM 50'ŞERLİ DAĞIT";
      btnBalance50.SetBounds(150,120,190,40);
      btnBalance50.BackColor=Color.FromArgb(255,210,0);
      btnBalance50.FlatStyle=FlatStyle.Flat;
      btnBalance50.FlatAppearance.BorderSize=0;
      btnBalance50.Click += async (s,e)=>await Rebalance(50);

      btnBalance100.Text="TAM 100'LÜ DAĞIT";
      btnBalance100.SetBounds(355,120,190,40);
      btnBalance100.BackColor=Color.FromArgb(255,210,0);
      btnBalance100.FlatStyle=FlatStyle.Flat;
      btnBalance100.FlatAppearance.BorderSize=0;
      btnBalance100.Click += async (s,e)=>await Rebalance(100);

      btnStop.Text="DURDUR";
      btnStop.SetBounds(560,120,120,40);
      btnStop.Enabled=false;
      btnStop.Click += (s,e)=>cts?.Cancel();

      body.Controls.Add(btnScan);
      body.Controls.Add(btnBalance50);
      body.Controls.Add(btnBalance100);
      body.Controls.Add(btnStop);

      list.SetBounds(0,178,815,300);
      list.HorizontalScrollbar=true;
      body.Controls.Add(list);

      bar.SetBounds(0,492,815,20);
      bar.Minimum=0; bar.Maximum=100;
      body.Controls.Add(bar);

      lbl.Text="Hazır.";
      lbl.AutoEllipsis=true;
      lbl.SetBounds(0,524,815,48);
      body.Controls.Add(lbl);
    }

    void Pick()
    {
      using(var d=new FolderBrowserDialog())
      {
        if(Directory.Exists(txtRoot.Text)) d.SelectedPath=txtRoot.Text;
        if(d.ShowDialog(this)==DialogResult.OK) txtRoot.Text=d.SelectedPath;
      }
    }

    void Busy(bool busy)
    {
      btnPick.Enabled=!busy;
      btnScan.Enabled=!busy;
      btnBalance50.Enabled=!busy;
      btnBalance100.Enabled=!busy;
      btnStop.Enabled=busy;
    }

    async Task Scan()
    {
      string root=txtRoot.Text.Trim();
      if(!Directory.Exists(root))
      {
        MessageBox.Show(this,"Ana klasör bulunamadı.","ATMACA",MessageBoxButtons.OK,MessageBoxIcon.Information);
        return;
      }

      list.Items.Clear();
      folders.Clear();
      Busy(true);
      bar.Value=0;
      cts=new CancellationTokenSource();

      var progress=new Progress<ScanProgress>(p=>{
        lbl.Text="Taranan klasör: "+p.Done.ToString("N0")+" • "+p.Current;
        if(p.Total>0) bar.Value=(int)Math.Min(100,(p.Done*100L)/p.Total);
      });

      try
      {
        folders=await Task.Run(()=>ReadFolders(root,chkRecursive.Checked,cts.Token,progress));
        var ordered=folders.OrderBy(f=>f.Count).ThenBy(f=>f.Path,StringComparer.OrdinalIgnoreCase).ToList();

        int lt50=ordered.Count(x=>x.Count<50);
        int gt50=ordered.Count(x=>x.Count>50);
        int lt100=ordered.Count(x=>x.Count<100);
        int gt100=ordered.Count(x=>x.Count>100);

        list.Items.Add("KLASÖR SAYISI: "+ordered.Count.ToString("N0"));
        list.Items.Add("50'DEN AZ: "+lt50.ToString("N0")+"   |   50'DEN FAZLA: "+gt50.ToString("N0"));
        list.Items.Add("100'DEN AZ: "+lt100.ToString("N0")+"   |   100'DEN FAZLA: "+gt100.ToString("N0"));
        list.Items.Add("");
        foreach(var f in ordered)
          list.Items.Add(f.Count.ToString("N0").PadLeft(8)+"  |  "+f.Path);

        bar.Value=100;
        lbl.Text="Tarama tamamlandı.";
      }
      catch(OperationCanceledException)
      {
        lbl.Text="Tarama durduruldu.";
      }
      finally
      {
        cts.Dispose();
        cts=null;
        Busy(false);
      }
    }

    List<FolderInfo> ReadFolders(string root,bool recursive,CancellationToken token,IProgress<ScanProgress> progress)
    {
      var dirs=new List<string>();

      if(recursive)
      {
        var stack=new Stack<string>();
        stack.Push(root);
        while(stack.Count>0)
        {
          token.ThrowIfCancellationRequested();
          var d=stack.Pop();
          IEnumerable<string> children=Array.Empty<string>();
          try { children=Directory.EnumerateDirectories(d); } catch { }
          foreach(var c in children)
          {
            dirs.Add(c);
            stack.Push(c);
          }
        }
      }
      else
      {
        try { dirs=Directory.EnumerateDirectories(root).ToList(); } catch { }
      }

      var result=new List<FolderInfo>(dirs.Count);
      for(int i=0;i<dirs.Count;i++)
      {
        token.ThrowIfCancellationRequested();
        int count=0;
        try { count=Directory.EnumerateFiles(dirs[i],"*",SearchOption.TopDirectoryOnly).Count(); } catch { }
        result.Add(new FolderInfo{Path=dirs[i],Count=count});
        if((i+1)%50==0 || i+1==dirs.Count)
          progress.Report(new ScanProgress(i+1,dirs.Count,dirs[i]));
      }
      return result;
    }

    async Task Rebalance(int targetCount)
    {
      string root=txtRoot.Text.Trim();
      if(!Directory.Exists(root))
      {
        MessageBox.Show(this,"Ana klasör bulunamadı.","ATMACA",MessageBoxButtons.OK,MessageBoxIcon.Information);
        return;
      }

      var confirm=MessageBox.Show(this,
        "Ana klasörün doğrudan altındaki tüm dosyalar ve alt klasörlerin içindeki dosyalar yeni klasörlere dağıtılacak.\n\nHer klasörde tam "+targetCount+" dosya olacak; son klasörde kalan dosyalar bulunabilir.\n\nDosya adları çakışırsa güvenli yeni ad verilir. Devam edilsin mi?",
        "ATMACA",MessageBoxButtons.YesNo,MessageBoxIcon.Question);

      if(confirm!=DialogResult.Yes) return;

      Busy(true);
      bar.Value=0;
      cts=new CancellationTokenSource();

      var progress=new Progress<ScanProgress>(p=>{
        lbl.Text=p.Current;
        if(p.Total>0) bar.Value=(int)Math.Min(100,(p.Done*100L)/p.Total);
      });

      try
      {
        int moved=await Task.Run(()=>DoRebalance(root,targetCount,cts.Token,progress));
        bar.Value=100;
        MessageBox.Show(this,moved.ToString("N0")+" dosya yeniden dağıtıldı.","ATMACA",MessageBoxButtons.OK,MessageBoxIcon.Information);
        await Scan();
      }
      catch(OperationCanceledException)
      {
        lbl.Text="Dağıtma durduruldu.";
      }
      finally
      {
        if(cts!=null){cts.Dispose();cts=null;}
        Busy(false);
      }
    }

    int DoRebalance(string root,int targetCount,CancellationToken token,IProgress<ScanProgress> progress)
    {
      var files=new List<string>();

      foreach(var f in SafeFiles(root)) files.Add(f);
      foreach(var d in SafeDirs(root))
      {
        foreach(var f in EnumerateAllFiles(d,token)) files.Add(f);
      }

      int moved=0;
      for(int i=0;i<files.Count;i++)
      {
        token.ThrowIfCancellationRequested();

        int start=(i/targetCount)*targetCount+1;
        int end=Math.Min(start+targetCount-1,files.Count);
        string group=Path.Combine(root,start+"-"+end);
        Directory.CreateDirectory(group);

        string src=files[i];
        if(IsInside(src,group)) continue;

        string dst=Unique(group,Path.GetFileName(src));
        try
        {
          File.Move(src,dst);
          moved++;
        }
        catch { }

        if((i+1)%50==0 || i+1==files.Count)
          progress.Report(new ScanProgress(i+1,files.Count,(i+1).ToString("N0")+" / "+files.Count.ToString("N0")+" dosya işlendi"));
      }

      RemoveEmptyChildren(root);
      return moved;
    }

    IEnumerable<string> SafeFiles(string d)
    {
      try { return Directory.EnumerateFiles(d); } catch { return Array.Empty<string>(); }
    }

    IEnumerable<string> SafeDirs(string d)
    {
      try { return Directory.EnumerateDirectories(d).Where(x=>!IsGroupFolder(Path.GetFileName(x))).ToList(); }
      catch { return Array.Empty<string>(); }
    }

    IEnumerable<string> EnumerateAllFiles(string root,CancellationToken token)
    {
      var stack=new Stack<string>();
      stack.Push(root);
      while(stack.Count>0)
      {
        token.ThrowIfCancellationRequested();
        var d=stack.Pop();
        foreach(var f in SafeFiles(d)) yield return f;
        foreach(var c in SafeDirs(d)) stack.Push(c);
      }
    }

    bool IsGroupFolder(string name)
    {
      if(string.IsNullOrWhiteSpace(name)) return false;
      int dash=name.IndexOf('-');
      if(dash<=0 || dash>=name.Length-1) return false;
      long a,b;
      return long.TryParse(name.Substring(0,dash),out a) && long.TryParse(name.Substring(dash+1),out b);
    }

    bool IsInside(string file,string dir)
    {
      try
      {
        var f=Path.GetFullPath(file).TrimEnd(Path.DirectorySeparatorChar)+Path.DirectorySeparatorChar;
        var d=Path.GetFullPath(dir).TrimEnd(Path.DirectorySeparatorChar)+Path.DirectorySeparatorChar;
        return f.StartsWith(d,StringComparison.OrdinalIgnoreCase);
      }
      catch { return false; }
    }

    string Unique(string dir,string name)
    {
      string p=Path.Combine(dir,name);
      if(!File.Exists(p)) return p;
      string b=Path.GetFileNameWithoutExtension(name);
      string e=Path.GetExtension(name);
      int n=2;
      while(File.Exists(p))
      {
        p=Path.Combine(dir,b+" ("+n+")"+e);
        n++;
      }
      return p;
    }

    void RemoveEmptyChildren(string root)
    {
      var all=new List<string>();
      var stack=new Stack<string>();
      foreach(var d in SafeDirs(root)) stack.Push(d);

      while(stack.Count>0)
      {
        var d=stack.Pop();
        all.Add(d);
        foreach(var c in SafeDirs(d)) stack.Push(c);
      }

      all.Sort((a,b)=>b.Length.CompareTo(a.Length));
      foreach(var d in all)
      {
        try
        {
          if(!Directory.EnumerateFileSystemEntries(d).Any())
            Directory.Delete(d,false);
        }
        catch { }
      }
    }

    class FolderInfo
    {
      public string Path;
      public int Count;
    }

    class ScanProgress
    {
      public long Done;
      public long Total;
      public string Current;
      public ScanProgress(long done,long total,string current){Done=done;Total=total;Current=current;}
    }
  }
}

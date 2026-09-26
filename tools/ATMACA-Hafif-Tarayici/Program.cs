using System;
using System.Collections.Generic;
using System.Drawing;
using System.IO;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace AtmacaLiteBrowser
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
    TextBox txtPath=new TextBox();
    Button btnPick=new Button(), btnLoad=new Button(), btnUp=new Button(), btnStop=new Button();
    ListView list=new ListView();
    Label lbl=new Label();
    CancellationTokenSource cts;

    public MainForm()
    {
      Text="ATMACA Hafif Dosya Tarayıcı";
      Width=1000; Height=680; StartPosition=FormStartPosition.CenterScreen;
      Font=new Font("Segoe UI",10F); BackColor=Color.FromArgb(247,249,252);
      Icon=Icon.ExtractAssociatedIcon(Application.ExecutablePath);

      var h=new Panel{Dock=DockStyle.Top,Height=76,BackColor=Color.FromArgb(9,37,74)};
      h.Controls.Add(new Label{Text="ATMACA HAFİF DOSYA TARAYICI",ForeColor=Color.FromArgb(255,210,0),Font=new Font("Segoe UI Semibold",18F,FontStyle.Bold),AutoSize=true,Left=22,Top=15});
      h.Controls.Add(new Label{Text="Explorer açmadan listele • Dosya sayısı • Toplam boyut",ForeColor=Color.WhiteSmoke,AutoSize=true,Left=25,Top=49});
      Controls.Add(h);

      var b=new Panel{Dock=DockStyle.Fill,Padding=new Padding(18)};Controls.Add(b);
      txtPath.SetBounds(0,5,690,32);
      btnPick.Text="Seç";btnPick.SetBounds(705,4,90,34);btnPick.BackColor=Color.FromArgb(255,210,0);btnPick.Click+=(s,e)=>Pick();
      btnLoad.Text="AÇ";btnLoad.SetBounds(805,4,70,34);btnLoad.BackColor=Color.FromArgb(9,37,74);btnLoad.ForeColor=Color.White;btnLoad.Click+=async(s,e)=>await LoadPath();
      btnUp.Text="ÜST";btnUp.SetBounds(885,4,70,34);btnUp.Click+=async(s,e)=>await GoUp();
      b.Controls.Add(txtPath);b.Controls.Add(btnPick);b.Controls.Add(btnLoad);b.Controls.Add(btnUp);

      btnStop.Text="DURDUR";btnStop.SetBounds(0,50,110,34);btnStop.Enabled=false;btnStop.Click+=(s,e)=>cts?.Cancel();b.Controls.Add(btnStop);
      lbl.Text="Hazır.";lbl.SetBounds(125,54,830,28);lbl.AutoEllipsis=true;b.Controls.Add(lbl);

      list.SetBounds(0,95,955,500);
      list.View=View.Details;list.FullRowSelect=true;list.GridLines=false;list.HideSelection=false;
      list.Columns.Add("Ad",420);list.Columns.Add("Tür",100);list.Columns.Add("Dosya",100);list.Columns.Add("Boyut",140);list.Columns.Add("Yol",500);
      list.DoubleClick+=async(s,e)=>await OpenSelected();
      b.Controls.Add(list);
    }

    void Pick()
    {
      using(var d=new FolderBrowserDialog())
      {
        if(Directory.Exists(txtPath.Text)) d.SelectedPath=txtPath.Text;
        if(d.ShowDialog(this)==DialogResult.OK){txtPath.Text=d.SelectedPath;_ = LoadPath();}
      }
    }

    void Busy(bool x)
    {
      btnPick.Enabled=!x;btnLoad.Enabled=!x;btnUp.Enabled=!x;btnStop.Enabled=x;
    }

    async Task LoadPath()
    {
      string path=txtPath.Text.Trim();
      if(!Directory.Exists(path)){MessageBox.Show(this,"Klasör bulunamadı.","ATMACA");return;}
      list.Items.Clear();Busy(true);cts=new CancellationTokenSource();
      var pr=new Progress<string>(s=>lbl.Text=s);
      try
      {
        var rows=await Task.Run(()=>Read(path,cts.Token,pr));
        list.BeginUpdate();
        foreach(var r in rows)
        {
          var it=new ListViewItem(r.Name);
          it.SubItems.Add(r.Kind);
          it.SubItems.Add(r.FileCount<0?"":r.FileCount.ToString("N0"));
          it.SubItems.Add(r.Size<0?"":FormatSize(r.Size));
          it.SubItems.Add(r.Path);
          it.Tag=r;
          list.Items.Add(it);
        }
        list.EndUpdate();
        lbl.Text=rows.Count.ToString("N0")+" öğe listelendi.";
      }
      catch(OperationCanceledException){lbl.Text="İşlem durduruldu.";}
      finally{cts.Dispose();cts=null;Busy(false);}
    }

    List<Row> Read(string root,CancellationToken token,IProgress<string> pr)
    {
      var rows=new List<Row>();
      IEnumerable<string> dirs=Array.Empty<string>(),files=Array.Empty<string>();
      try{dirs=Directory.EnumerateDirectories(root).ToList();}catch{}
      try{files=Directory.EnumerateFiles(root).ToList();}catch{}

      int done=0;
      foreach(var d in dirs)
      {
        token.ThrowIfCancellationRequested();
        long size=0;int count=0;
        GetStats(d,token,ref count,ref size);
        rows.Add(new Row{Name=Path.GetFileName(d),Kind="Klasör",FileCount=count,Size=size,Path=d});
        done++; if(done%5==0)pr.Report("Klasör hesaplanıyor: "+done+" / "+dirs.Count());
      }

      foreach(var f in files)
      {
        token.ThrowIfCancellationRequested();
        long s=-1;try{s=new FileInfo(f).Length;}catch{}
        rows.Add(new Row{Name=Path.GetFileName(f),Kind="Dosya",FileCount=-1,Size=s,Path=f});
      }

      rows=rows.OrderByDescending(x=>x.Kind=="Klasör").ThenBy(x=>x.Name,StringComparer.OrdinalIgnoreCase).ToList();
      return rows;
    }

    void GetStats(string root,CancellationToken token,ref int count,ref long size)
    {
      var stack=new Stack<string>();stack.Push(root);
      while(stack.Count>0)
      {
        token.ThrowIfCancellationRequested();
        string d=stack.Pop();
        try
        {
          foreach(var f in Directory.EnumerateFiles(d))
          {
            token.ThrowIfCancellationRequested();
            count++;
            try{size+=new FileInfo(f).Length;}catch{}
          }
        }catch{}
        try{foreach(var c in Directory.EnumerateDirectories(d))stack.Push(c);}catch{}
      }
    }

    async Task OpenSelected()
    {
      if(list.SelectedItems.Count==0)return;
      var r=list.SelectedItems[0].Tag as Row;if(r==null)return;
      if(r.Kind=="Klasör" && Directory.Exists(r.Path)){txtPath.Text=r.Path;await LoadPath();}
      else if(File.Exists(r.Path)){System.Diagnostics.Process.Start("explorer.exe","/select,\""+r.Path+"\"");}
    }

    async Task GoUp()
    {
      try
      {
        var p=Directory.GetParent(txtPath.Text);
        if(p!=null){txtPath.Text=p.FullName;await LoadPath();}
      }catch{}
    }

    string FormatSize(long n)
    {
      if(n<0)return "";
      string[] u={"B","KB","MB","GB","TB"};double v=n;int i=0;
      while(v>=1024&&i<u.Length-1){v/=1024;i++;}
      return v.ToString(i==0?"0":"0.##")+" "+u[i];
    }

    class Row
    {
      public string Name,Kind,Path;
      public int FileCount;
      public long Size;
    }
  }
}

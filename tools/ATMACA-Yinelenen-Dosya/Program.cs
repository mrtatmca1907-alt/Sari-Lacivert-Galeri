using System;
using System.Collections.Generic;
using System.Drawing;
using System.IO;
using System.Linq;
using System.Security.Cryptography;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace AtmacaTekrar
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

  public class MainForm:Form
  {
    TextBox src=new TextBox(),dst=new TextBox();
    Button bSrc=new Button(),bDst=new Button(),bScan=new Button(),bMove=new Button(),bRename=new Button();
    CheckBox recursive=new CheckBox();
    ListBox list=new ListBox();
    Label status=new Label();
    List<string> duplicateCopies=new List<string>();
    List<List<string>> sameNameGroups=new List<List<string>>();

    public MainForm()
    {
      Text="ATMACA Yinelenen Dosya Yöneticisi";
      Width=900;Height=650;StartPosition=FormStartPosition.CenterScreen;
      Font=new Font("Segoe UI",10);BackColor=Color.FromArgb(247,249,252);
      var h=new Panel{Dock=DockStyle.Top,Height=74,BackColor=Color.FromArgb(9,37,74)};
      h.Controls.Add(new Label{Text="ATMACA YİNELENEN DOSYA YÖNETİCİSİ",ForeColor=Color.FromArgb(255,210,0),Font=new Font("Segoe UI",18,FontStyle.Bold),AutoSize=true,Left=22,Top=16});
      Controls.Add(h);
      var p=new Panel{Dock=DockStyle.Fill,Padding=new Padding(22)};Controls.Add(p);
      p.Controls.Add(new Label{Text="Kaynak klasör",AutoSize=true,Left=0,Top=5});
      src.SetBounds(0,30,690,32);bSrc.Text="Seç";bSrc.SetBounds(705,29,105,34);bSrc.BackColor=Color.FromArgb(255,210,0);bSrc.Click+=(s,e)=>Pick(src);p.Controls.Add(src);p.Controls.Add(bSrc);
      p.Controls.Add(new Label{Text="Yinelenenleri taşıma hedefi",AutoSize=true,Left=0,Top=72});
      dst.SetBounds(0,97,690,32);bDst.Text="Seç";bDst.SetBounds(705,96,105,34);bDst.Click+=(s,e)=>Pick(dst);p.Controls.Add(dst);p.Controls.Add(bDst);
      recursive.Text="Alt klasörleri tara";recursive.Checked=true;recursive.AutoSize=true;recursive.SetBounds(0,140,180,28);p.Controls.Add(recursive);
      bScan.Text="TARA";bScan.SetBounds(0,178,110,38);bScan.BackColor=Color.FromArgb(9,37,74);bScan.ForeColor=Color.White;bScan.Click+=async(s,e)=>await Scan();p.Controls.Add(bScan);
      bMove.Text="AYNILARI TAŞI";bMove.SetBounds(125,178,155,38);bMove.BackColor=Color.FromArgb(255,210,0);bMove.Enabled=false;bMove.Click+=async(s,e)=>await MoveCopies();p.Controls.Add(bMove);
      bRename.Text="AYNI İSİMLERİ DÜZELT";bRename.SetBounds(295,178,205,38);bRename.Enabled=false;bRename.Click+=async(s,e)=>await RenameNames();p.Controls.Add(bRename);
      list.SetBounds(0,235,810,300);list.HorizontalScrollbar=true;p.Controls.Add(list);
      status.Text="Hazır.";status.SetBounds(0,550,810,35);p.Controls.Add(status);
    }

    void Pick(TextBox t){using(var d=new FolderBrowserDialog()){if(d.ShowDialog()==DialogResult.OK)t.Text=d.SelectedPath;}}

    async Task Scan()
    {
      if(!Directory.Exists(src.Text)){MessageBox.Show("Kaynak klasör bulunamadı.");return;}
      SetBusy(true);list.Items.Clear();duplicateCopies.Clear();sameNameGroups.Clear();status.Text="Taranıyor...";
      try
      {
        var data=await Task.Run(()=>Analyze());
        duplicateCopies=data.Item1;sameNameGroups=data.Item2;
        list.Items.Add("Aynı içerikli fazladan kopya: "+duplicateCopies.Count);
        foreach(var x in duplicateCopies.Take(1500))list.Items.Add(x);
        list.Items.Add("");
        list.Items.Add("Aynı isim grubu: "+sameNameGroups.Count);
        foreach(var g in sameNameGroups.Take(500))list.Items.Add(Path.GetFileName(g[0])+"  ("+g.Count+")");
        status.Text="Bitti. Fazladan kopya: "+duplicateCopies.Count+" • Aynı isim grubu: "+sameNameGroups.Count;
      }
      finally{SetBusy(false);}
    }

    Tuple<List<string>,List<List<string>>> Analyze()
    {
      var opt=recursive.Checked?SearchOption.AllDirectories:SearchOption.TopDirectoryOnly;
      List<string> files;
      try{files=Directory.EnumerateFiles(src.Text,"*",opt).ToList();}catch{files=Directory.EnumerateFiles(src.Text).ToList();}
      var sameNames=files.GroupBy(x=>Path.GetFileName(x),StringComparer.OrdinalIgnoreCase).Where(g=>g.Count()>1).Select(g=>g.ToList()).ToList();
      var dup=new List<string>();
      foreach(var sizeGroup in files.Select(f=>new {P=f,S=SafeSize(f)}).Where(x=>x.S>=0).GroupBy(x=>x.S).Where(g=>g.Count()>1))
      {
        var hashes=new Dictionary<string,List<string>>(StringComparer.Ordinal);
        foreach(var item in sizeGroup)
        {
          string h=Hash(item.P);
          if(h==null)continue;
          List<string> bucket;
          if(!hashes.TryGetValue(h,out bucket)){bucket=new List<string>();hashes[h]=bucket;}
          bucket.Add(item.P);
        }
        foreach(var g in hashes.Values.Where(x=>x.Count>1))dup.AddRange(g.Skip(1));
      }
      return Tuple.Create(dup,sameNames);
    }

    long SafeSize(string p){try{return new FileInfo(p).Length;}catch{return -1;}}

    string Hash(string p)
    {
      try
      {
        using(var sha=SHA256.Create())
        using(var fs=new FileStream(p,FileMode.Open,FileAccess.Read,FileShare.Read,1024*1024,FileOptions.SequentialScan))
          return BitConverter.ToString(sha.ComputeHash(fs)).Replace("-","");
      }
      catch{return null;}
    }

    async Task MoveCopies()
    {
      if(duplicateCopies.Count==0)return;
      if(string.IsNullOrWhiteSpace(dst.Text)){MessageBox.Show("Hedef klasörü seç.");return;}
      Directory.CreateDirectory(dst.Text);
      if(MessageBox.Show(duplicateCopies.Count+" fazladan kopya hedefe taşınacak. Devam?","ATMACA",MessageBoxButtons.YesNo)!=DialogResult.Yes)return;
      SetBusy(true);
      int n=await Task.Run(()=>{int k=0;foreach(var f in duplicateCopies){try{File.Move(f,Unique(dst.Text,Path.GetFileName(f)));k++;}catch{}}return k;});
      MessageBox.Show(n+" dosya taşındı.");SetBusy(false);await Scan();
    }

    async Task RenameNames()
    {
      if(sameNameGroups.Count==0)return;
      if(MessageBox.Show("Aynı isimli dosyaların ilk örneği korunacak. Diğerlerine _2, _3... eklenecek. Devam?","ATMACA",MessageBoxButtons.YesNo)!=DialogResult.Yes)return;
      SetBusy(true);
      int n=await Task.Run(()=>{int k=0;foreach(var g in sameNameGroups){int i=2;foreach(var f in g.Skip(1)){try{var dir=Path.GetDirectoryName(f);var name=Path.GetFileNameWithoutExtension(f);var ext=Path.GetExtension(f);File.Move(f,Unique(dir,name+"_"+i+ext));i++;k++;}catch{}}}return k;});
      MessageBox.Show(n+" dosya yeniden adlandırıldı.");SetBusy(false);await Scan();
    }

    string Unique(string dir,string name)
    {
      var p=Path.Combine(dir,name);if(!File.Exists(p))return p;
      var b=Path.GetFileNameWithoutExtension(name);var e=Path.GetExtension(name);int i=2;
      while(File.Exists(p)){p=Path.Combine(dir,b+" ("+i+")"+e);i++;}
      return p;
    }

    void SetBusy(bool b){bSrc.Enabled=!b;bDst.Enabled=!b;bScan.Enabled=!b;bMove.Enabled=!b&&duplicateCopies.Count>0;bRename.Enabled=!b&&sameNameGroups.Count>0;}
  }
}

using System;using System.Collections.Generic;using System.Drawing;using System.IO;using System.Linq;using System.Threading;using System.Threading.Tasks;using System.Windows.Forms;
namespace AtmacaBoyut{
static class Program{[STAThread]static void Main(){Application.EnableVisualStyles();Application.SetCompatibleTextRenderingDefault(false);Application.Run(new F());}}
class F:Form{
TextBox root=new TextBox();Button pick=new Button(),scan=new Button(),stop=new Button();ComboBox mode=new ComboBox();ListView list=new ListView();Label st=new Label();CancellationTokenSource cts;
public F(){Text="ATMACA Boyut Sıralayıcı";Width=950;Height=650;StartPosition=FormStartPosition.CenterScreen;Font=new Font("Segoe UI",10);BackColor=Color.FromArgb(247,249,252);
var h=new Panel{Dock=DockStyle.Top,Height=74,BackColor=Color.FromArgb(9,37,74)};h.Controls.Add(new Label{Text="ATMACA BOYUT SIRALAYICI",ForeColor=Color.FromArgb(255,210,0),Font=new Font("Segoe UI",18,FontStyle.Bold),AutoSize=true,Left=22,Top=16});Controls.Add(h);
var p=new Panel{Dock=DockStyle.Fill,Padding=new Padding(20)};Controls.Add(p);root.SetBounds(0,10,700,32);pick.Text="Seç";pick.SetBounds(715,9,100,34);pick.BackColor=Color.FromArgb(255,210,0);pick.Click+=(s,e)=>Pick();p.Controls.Add(root);p.Controls.Add(pick);
mode.DropDownStyle=ComboBoxStyle.DropDownList;mode.Items.AddRange(new object[]{"Klasörler","Dosyalar","Klasör + Dosya"});mode.SelectedIndex=2;mode.SetBounds(0,58,220,32);p.Controls.Add(mode);
scan.Text="TARA VE SIRALA";scan.SetBounds(235,57,170,36);scan.BackColor=Color.FromArgb(9,37,74);scan.ForeColor=Color.White;scan.Click+=async(s,e)=>await Scan();p.Controls.Add(scan);
stop.Text="DURDUR";stop.SetBounds(420,57,110,36);stop.Enabled=false;stop.Click+=(s,e)=>cts?.Cancel();p.Controls.Add(stop);
list.SetBounds(0,110,875,430);list.View=View.Details;list.FullRowSelect=true;list.Columns.Add("Ad",350);list.Columns.Add("Tür",100);list.Columns.Add("Boyut",140);list.Columns.Add("Yol",500);p.Controls.Add(list);
st.SetBounds(0,555,875,35);st.Text="Hazır.";p.Controls.Add(st);}
void Pick(){using(var d=new FolderBrowserDialog()){if(d.ShowDialog()==DialogResult.OK)root.Text=d.SelectedPath;}}
void Busy(bool b){pick.Enabled=!b;scan.Enabled=!b;stop.Enabled=b;}
async Task Scan(){if(!Directory.Exists(root.Text)){MessageBox.Show("Klasör bulunamadı.");return;}list.Items.Clear();Busy(true);cts=new CancellationTokenSource();var pr=new Progress<string>(x=>st.Text=x);try{var rows=await Task.Run(()=>Read(cts.Token,pr));foreach(var r in rows.OrderByDescending(x=>x.Size)){var it=new ListViewItem(r.Name);it.SubItems.Add(r.Kind);it.SubItems.Add(Fmt(r.Size));it.SubItems.Add(r.Path);list.Items.Add(it);}st.Text=rows.Count+" öğe boyuta göre sıralandı.";}catch(OperationCanceledException){st.Text="Durduruldu.";}finally{cts.Dispose();cts=null;Busy(false);}}
List<R> Read(CancellationToken t,IProgress<string> pr){var a=new List<R>();if(mode.SelectedIndex!=1){int n=0;foreach(var d in SafeDirs(root.Text)){t.ThrowIfCancellationRequested();long s=Size(d,t);a.Add(new R{Name=Path.GetFileName(d),Kind="Klasör",Size=s,Path=d});if(++n%5==0)pr.Report("Klasör boyutu hesaplanıyor: "+n);}}if(mode.SelectedIndex!=0){foreach(var f in SafeFiles(root.Text)){t.ThrowIfCancellationRequested();try{a.Add(new R{Name=Path.GetFileName(f),Kind="Dosya",Size=new FileInfo(f).Length,Path=f});}catch{}}}return a;}
IEnumerable<string> SafeDirs(string d){try{return Directory.EnumerateDirectories(d);}catch{return Array.Empty<string>();}}
IEnumerable<string> SafeFiles(string d){try{return Directory.EnumerateFiles(d);}catch{return Array.Empty<string>();}}
long Size(string root,CancellationToken t){long s=0;var stack=new Stack<string>();stack.Push(root);while(stack.Count>0){t.ThrowIfCancellationRequested();var d=stack.Pop();foreach(var f in SafeFiles(d)){try{s+=new FileInfo(f).Length;}catch{}}foreach(var c in SafeDirs(d))stack.Push(c);}return s;}
string Fmt(long n){string[] u={"B","KB","MB","GB","TB"};double v=n;int i=0;while(v>=1024&&i<4){v/=1024;i++;}return v.ToString(i==0?"0":"0.##")+" "+u[i];}
class R{public string Name,Kind,Path;public long Size;}}}

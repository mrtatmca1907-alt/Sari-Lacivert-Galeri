using System;
using System.IO;
using System.Linq;
using System.Collections.Generic;
using System.Threading.Tasks;
using System.Windows.Forms;
using System.Drawing;
class Program {
 [STAThread] static void Main(){Application.EnableVisualStyles();Application.Run(new MainForm());}
}
class MainForm:Form {
 ListBox sources=new ListBox(){Dock=DockStyle.Fill};
 TextBox destination=new TextBox(){Dock=DockStyle.Fill,ReadOnly=true};
 Button add=new Button(){Text="Kaynak klasör ekle",Width=145};
 Button remove=new Button(){Text="Seçileni kaldır",Width=125};
 Button target=new Button(){Text="Hedef seç",Width=110};
 Button start=new Button(){Text="FOTOĞRAFLARI TAŞI",Dock=DockStyle.Bottom,Height=48};
 Label status=new Label(){Text="Yalnızca seçtiğin klasörler taranır.",Dock=DockStyle.Bottom,Height=35};
 ProgressBar progress=new ProgressBar(){Dock=DockStyle.Bottom,Height=18,Style=ProgressBarStyle.Marquee,Visible=false};
 HashSet<string> extensions=new HashSet<string>(StringComparer.OrdinalIgnoreCase){".jpg",".jpeg",".png",".webp",".heic",".bmp",".gif",".tif",".tiff",".jfif",".avif"};
 public MainForm(){
 Text="ATMACA Fotoğraf Taşıyıcı";Width=650;Height=400;MinimumSize=new Size(540,340);
 var top=new FlowLayoutPanel(){Dock=DockStyle.Top,Height=42};
 top.Controls.Add(add);top.Controls.Add(remove);
 var bottom=new TableLayoutPanel(){Dock=DockStyle.Bottom,Height=45,ColumnCount=2};
 bottom.ColumnStyles.Add(new ColumnStyle(SizeType.Percent,100));bottom.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute,120));
 bottom.Controls.Add(destination,0,0);bottom.Controls.Add(target,1,0);
 Controls.Add(sources);Controls.Add(top);Controls.Add(bottom);Controls.Add(progress);Controls.Add(status);Controls.Add(start);
 add.Click+=(s,e)=>{using(var d=new FolderBrowserDialog()){if(d.ShowDialog()==DialogResult.OK && !sources.Items.Contains(d.SelectedPath))sources.Items.Add(d.SelectedPath);}};
 remove.Click+=(s,e)=>{if(sources.SelectedIndex>=0)sources.Items.RemoveAt(sources.SelectedIndex);};
 target.Click+=(s,e)=>{using(var d=new FolderBrowserDialog()){if(d.ShowDialog()==DialogResult.OK)destination.Text=d.SelectedPath;}};
 start.Click+=async(s,e)=>await Move();
 }
 async Task Move(){
 if(sources.Items.Count==0||!Directory.Exists(destination.Text)){MessageBox.Show("Kaynak klasörleri ve hedef klasörü seç.");return;}
 var roots=sources.Items.Cast<string>().ToArray();string dest=Path.GetFullPath(destination.Text).TrimEnd(Path.DirectorySeparatorChar);
 foreach(var root in roots){string src=Path.GetFullPath(root).TrimEnd(Path.DirectorySeparatorChar);
 if(dest.Equals(src,StringComparison.OrdinalIgnoreCase)||dest.StartsWith(src+Path.DirectorySeparatorChar,StringComparison.OrdinalIgnoreCase)){MessageBox.Show("Hedef klasör kaynak klasörün içinde olamaz.");return;}}
 if(MessageBox.Show("Seçtiğin klasörlerdeki fotoğraflar TAŞINACAK, kopyalanmayacak. Devam?","Onay",MessageBoxButtons.YesNo,MessageBoxIcon.Warning)!=DialogResult.Yes)return;
 start.Enabled=false;add.Enabled=false;remove.Enabled=false;target.Enabled=false;progress.Visible=true;
 int moved=0,failed=0;
 await Task.Run(()=>{
 var visited=new HashSet<string>(StringComparer.OrdinalIgnoreCase);
 Action<string> scan=null;
 scan=(folder)=>{
 try{
 var info=new DirectoryInfo(folder);
 if((info.Attributes & FileAttributes.ReparsePoint)!=0)return;
 string full=Path.GetFullPath(folder);
 if(!visited.Add(full))return;
 foreach(var file in Directory.EnumerateFiles(folder)){
 if(!extensions.Contains(Path.GetExtension(file)))continue;
 try{
 string name=Path.GetFileNameWithoutExtension(file),ext=Path.GetExtension(file);
 string output=Path.Combine(dest,name+ext);int n=1;
 while(File.Exists(output)){output=Path.Combine(dest,name+"+"+(n++)+ext);}
 File.Move(file,output);moved++;
 if((moved+failed)%100==0){int m=moved,e=failed;BeginInvoke((Action)(()=>status.Text="Taşınan: "+m+" | Hata: "+e));}
 }catch{failed++;}
 }
 foreach(var sub in Directory.EnumerateDirectories(folder))scan(sub);
 }catch{failed++;}
 };
 foreach(var root in roots)scan(root);
 });
 progress.Visible=false;status.Text="Bitti. Taşınan: "+moved+" | Hata: "+failed;
 start.Enabled=true;add.Enabled=true;remove.Enabled=true;target.Enabled=true;
 }
}
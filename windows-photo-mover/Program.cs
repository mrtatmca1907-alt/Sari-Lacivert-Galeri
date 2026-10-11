using System;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using System.Windows.Forms;
using System.Drawing;
class Program {
 [STAThread] static void Main(){Application.EnableVisualStyles();Application.Run(new MainForm());}
}
class MainForm:Form {
 Button start=new Button(){Text="FOTOĞRAFLARI TAŞI",Dock=DockStyle.Top,Height=55};
 Label status=new Label(){Text="C: → E:\\TUM_FOTOGRAFLAR",Dock=DockStyle.Top,Height=45,TextAlign=ContentAlignment.MiddleCenter};
 ProgressBar bar=new ProgressBar(){Dock=DockStyle.Top,Style=ProgressBarStyle.Marquee,Visible=false};
 public MainForm(){Text="ATMACA Fotoğraf Taşıyıcı";Width=530;Height=220;Controls.Add(bar);Controls.Add(status);Controls.Add(start);start.Click+=async(s,e)=>await Move();}
 bool IsPhoto(string p){string ext=Path.GetExtension(p).ToLowerInvariant();return new[]{".jpg",".jpeg",".png",".webp",".heic",".bmp",".gif",".tif",".tiff",".jfif",".avif"}.Contains(ext);}
 async Task Move(){
  if(MessageBox.Show("C: sürücüsündeki fotoğraflar E: sürücüsüne TAŞINACAK. Devam?", "Onay",MessageBoxButtons.YesNo,MessageBoxIcon.Warning)!=DialogResult.Yes)return;
  start.Enabled=false;bar.Visible=true;
  int moved=0,failed=0;
  await Task.Run(()=>{
   string dest=@"E:\TUM_FOTOGRAFLAR";Directory.CreateDirectory(dest);
   Action<string> scan=null;
   scan=(folder)=>{
    try{
     foreach(var file in Directory.EnumerateFiles(folder)){
      if(!IsPhoto(file))continue;
      try{
       string name=Path.GetFileNameWithoutExtension(file),ext=Path.GetExtension(file);
       string target=Path.Combine(dest,name+ext);int n=1;
       while(File.Exists(target)){target=Path.Combine(dest,name+"+"+(n++)+ext);}
       File.Move(file,target);moved++;
       if((moved+failed)%100==0)BeginInvoke((Action)(()=>status.Text="Taşınan: "+moved+" | Hata: "+failed));
      }catch{failed++;}
     }
     foreach(var dir in Directory.EnumerateDirectories(folder)){
      var d=Path.GetFileName(dir).ToLowerInvariant();
      if(new[]{"windows","program files","program files (x86)","programdata","$recycle.bin","system volume information","appdata","recovery"}.Contains(d))continue;
      scan(dir);
     }
    }catch{failed++;}
   };
   string profile=Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
   foreach(string folder in new[]{"Desktop","Pictures","Downloads","Documents"}){
    string root=Path.Combine(profile,folder);
    if(Directory.Exists(root))scan(root);
   }
  });
  bar.Visible=false;status.Text="Bitti! Taşınan: "+moved+" | Hata: "+failed;start.Enabled=true;
 }
}
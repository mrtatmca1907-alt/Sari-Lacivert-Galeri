using System;
using System.Collections.Generic;
using System.Drawing;
using System.IO;
using System.Linq;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace AtmacaGruplayici
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
        TextBox txtSource = new TextBox();
        TextBox txtTarget = new TextBox();
        ComboBox cmbType = new ComboBox();
        ComboBox cmbSize = new ComboBox();
        CheckBox chkRenameFolders = new CheckBox();
        Button btnSource = new Button();
        Button btnTarget = new Button();
        Button btnStart = new Button();
        Button btnStop = new Button();
        ProgressBar progress = new ProgressBar();
        Label lblStatus = new Label();
        CancellationTokenSource cts;

        readonly HashSet<string> imageExts = new HashSet<string>(StringComparer.OrdinalIgnoreCase)
        { ".jpg",".jpeg",".png",".webp",".bmp",".gif",".tif",".tiff",".heic",".heif",".avif",".jfif" };

        readonly HashSet<string> videoExts = new HashSet<string>(StringComparer.OrdinalIgnoreCase)
        { ".mp4",".mkv",".avi",".mov",".wmv",".m4v",".webm",".3gp",".mts",".m2ts",".ts",".flv",".mpeg",".mpg" };

        public MainForm()
        {
            Text = "ATMACA Gruplayıcı";
            Width = 760;
            Height = 500;
            MinimumSize = new Size(720, 460);
            StartPosition = FormStartPosition.CenterScreen;
            Font = new Font("Segoe UI", 10F);
            BackColor = Color.FromArgb(247, 249, 252);
            Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath);

            var header = new Panel { Dock = DockStyle.Top, Height = 76, BackColor = Color.FromArgb(9, 37, 74) };
            var title = new Label
            {
                Text = "ATMACA GRUPLAYICI",
                ForeColor = Color.FromArgb(255, 210, 0),
                Font = new Font("Segoe UI Semibold", 19F, FontStyle.Bold),
                AutoSize = true,
                Left = 24,
                Top = 17
            };
            var sub = new Label
            {
                Text = "Klasör • Görsel • Video  |  50 / 100 / 200",
                ForeColor = Color.WhiteSmoke,
                AutoSize = true,
                Left = 27,
                Top = 48
            };
            header.Controls.Add(title);
            header.Controls.Add(sub);

            var body = new Panel { Dock = DockStyle.Fill, Padding = new Padding(24, 18, 24, 18) };
            Controls.Add(body);
            Controls.Add(header);

            int y = 16;
            body.Controls.Add(MakeLabel("Kaynak klasör", 0, y));
            y += 27;
            txtSource.SetBounds(0, y, 575, 32);
            btnSource.Text = "Seç";
            btnSource.SetBounds(590, y - 1, 105, 34);
            btnSource.BackColor = Color.FromArgb(255, 210, 0);
            btnSource.FlatStyle = FlatStyle.Flat;
            btnSource.FlatAppearance.BorderSize = 0;
            btnSource.Click += (s, e) => PickFolder(txtSource, true);
            body.Controls.Add(txtSource);
            body.Controls.Add(btnSource);

            y += 52;
            body.Controls.Add(MakeLabel("Hedef klasör", 0, y));
            y += 27;
            txtTarget.SetBounds(0, y, 575, 32);
            btnTarget.Text = "Seç";
            btnTarget.SetBounds(590, y - 1, 105, 34);
            btnTarget.BackColor = Color.FromArgb(224, 229, 237);
            btnTarget.FlatStyle = FlatStyle.Flat;
            btnTarget.FlatAppearance.BorderSize = 0;
            btnTarget.Click += (s, e) => PickFolder(txtTarget, false);
            body.Controls.Add(txtTarget);
            body.Controls.Add(btnTarget);

            y += 56;
            body.Controls.Add(MakeLabel("Gruplanacak öğe", 0, y));
            body.Controls.Add(MakeLabel("Grup büyüklüğü", 280, y));
            y += 28;

            cmbType.DropDownStyle = ComboBoxStyle.DropDownList;
            cmbType.Items.AddRange(new object[] { "Klasörler", "Görseller", "Videolar", "Görsel + Video" });
            cmbType.SelectedIndex = 0;
            cmbType.SetBounds(0, y, 250, 32);
            cmbType.SelectedIndexChanged += (s,e) => chkRenameFolders.Enabled = cmbType.SelectedIndex == 0;

            cmbSize.DropDownStyle = ComboBoxStyle.DropDownList;
            cmbSize.Items.AddRange(new object[] { "50", "100", "200" });
            cmbSize.SelectedIndex = 1;
            cmbSize.SetBounds(280, y, 170, 32);

            body.Controls.Add(cmbType);
            body.Controls.Add(cmbSize);

            chkRenameFolders.Text = "Klasörleri taşımadan önce 1, 2, 3... diye yeniden adlandır";
            chkRenameFolders.AutoSize = true;
            chkRenameFolders.SetBounds(0, y + 48, 520, 28);
            body.Controls.Add(chkRenameFolders);

            btnStart.Text = "BAŞLAT";
            btnStart.Font = new Font("Segoe UI Semibold", 11F, FontStyle.Bold);
            btnStart.BackColor = Color.FromArgb(9, 37, 74);
            btnStart.ForeColor = Color.White;
            btnStart.FlatStyle = FlatStyle.Flat;
            btnStart.FlatAppearance.BorderSize = 0;
            btnStart.SetBounds(0, y + 90, 180, 42);
            btnStart.Click += async (s,e) => await StartWork();

            btnStop.Text = "DURDUR";
            btnStop.Enabled = false;
            btnStop.BackColor = Color.FromArgb(224, 229, 237);
            btnStop.FlatStyle = FlatStyle.Flat;
            btnStop.FlatAppearance.BorderSize = 0;
            btnStop.SetBounds(194, y + 90, 130, 42);
            btnStop.Click += (s,e) => cts?.Cancel();

            body.Controls.Add(btnStart);
            body.Controls.Add(btnStop);

            progress.SetBounds(0, y + 148, 695, 22);
            body.Controls.Add(progress);

            lblStatus.Text = "Hazır.";
            lblStatus.AutoEllipsis = true;
            lblStatus.SetBounds(0, y + 178, 695, 45);
            lblStatus.ForeColor = Color.FromArgb(45, 58, 76);
            body.Controls.Add(lblStatus);
        }

        Label MakeLabel(string text, int x, int y)
        {
            return new Label { Text = text, Left = x, Top = y, AutoSize = true, ForeColor = Color.FromArgb(45,58,76) };
        }

        void PickFolder(TextBox box, bool setTargetToo)
        {
            using (var dlg = new FolderBrowserDialog())
            {
                dlg.Description = "Klasör seç";
                if (Directory.Exists(box.Text)) dlg.SelectedPath = box.Text;
                if (dlg.ShowDialog(this) == DialogResult.OK)
                {
                    box.Text = dlg.SelectedPath;
                    if (setTargetToo && string.IsNullOrWhiteSpace(txtTarget.Text))
                        txtTarget.Text = dlg.SelectedPath;
                }
            }
        }

        async Task StartWork()
        {
            var source = txtSource.Text.Trim();
            var target = txtTarget.Text.Trim();

            if (!Directory.Exists(source))
            {
                MessageBox.Show(this, "Kaynak klasör bulunamadı.", "ATMACA", MessageBoxButtons.OK, MessageBoxIcon.Information);
                return;
            }
            if (string.IsNullOrWhiteSpace(target))
                target = source;

            try { Directory.CreateDirectory(target); }
            catch (Exception ex)
            {
                MessageBox.Show(this, "Hedef klasör oluşturulamadı:\n" + ex.Message, "ATMACA", MessageBoxButtons.OK, MessageBoxIcon.Information);
                return;
            }

            int groupSize = int.Parse(cmbSize.SelectedItem.ToString());
            int type = cmbType.SelectedIndex;
            bool rename = chkRenameFolders.Checked && type == 0;

            var confirm = MessageBox.Show(this,
                "İşlem başlayacak.\n\nTür: " + cmbType.SelectedItem + "\nGrup: " + groupSize +
                (rename ? "\nKlasörler 1, 2, 3... diye yeniden adlandırılacak." : "") +
                "\n\nDevam edilsin mi?",
                "ATMACA Gruplayıcı", MessageBoxButtons.YesNo, MessageBoxIcon.Question);

            if (confirm != DialogResult.Yes) return;

            cts = new CancellationTokenSource();
            btnStart.Enabled = false;
            btnStop.Enabled = true;
            progress.Value = 0;
            lblStatus.Text = "Öğeler hazırlanıyor...";

            var ui = new Progress<WorkProgress>(p =>
            {
                if (p.Total > 0)
                {
                    int value = (int)Math.Min(100, Math.Max(0, (p.Done * 100L) / p.Total));
                    progress.Value = value;
                }
                lblStatus.Text = p.Message;
            });

            try
            {
                var result = await Task.Run(() => GroupItems(source, target, type, groupSize, rename, cts.Token, ui));
                progress.Value = 100;
                lblStatus.Text = result;
                MessageBox.Show(this, result, "ATMACA", MessageBoxButtons.OK, MessageBoxIcon.Information);
            }
            catch (OperationCanceledException)
            {
                lblStatus.Text = "İşlem kullanıcı tarafından durduruldu.";
            }
            catch (Exception ex)
            {
                lblStatus.Text = "Hata: " + ex.Message;
                MessageBox.Show(this, ex.Message, "ATMACA", MessageBoxButtons.OK, MessageBoxIcon.Information);
            }
            finally
            {
                btnStart.Enabled = true;
                btnStop.Enabled = false;
                cts.Dispose();
                cts = null;
            }
        }

        string GroupItems(string source, string target, int type, int groupSize, bool renameFolders,
            CancellationToken token, IProgress<WorkProgress> progressReport)
        {
            List<string> items;

            if (type == 0)
            {
                items = Directory.EnumerateDirectories(source)
                    .Where(p => !IsGroupFolderName(Path.GetFileName(p)))
                    .Where(p => IsUserFolder(p))
                    .OrderBy(p => Path.GetFileName(p), StringComparer.OrdinalIgnoreCase)
                    .ToList();
            }
            else
            {
                items = Directory.EnumerateFiles(source)
                    .Where(p => MatchType(p, type))
                    .OrderBy(p => Path.GetFileName(p), StringComparer.OrdinalIgnoreCase)
                    .ToList();
            }

            if (items.Count == 0)
                return "Gruplanacak öğe bulunamadı.";

            if (type == 0 && renameFolders)
            {
                progressReport.Report(new WorkProgress(0, items.Count, "Klasörler güvenli biçimde yeniden adlandırılıyor..."));
                items = RenameFoldersSafely(items, source, token, progressReport);
            }

            long moved = 0;
            for (int i = 0; i < items.Count; i++)
            {
                token.ThrowIfCancellationRequested();

                int start = (i / groupSize) * groupSize + 1;
                int end = Math.Min(start + groupSize - 1, items.Count);
                string groupDir = Path.Combine(target, start + "-" + end);
                Directory.CreateDirectory(groupDir);

                string src = items[i];
                string name = Path.GetFileName(src);
                string dst = UniqueDestination(groupDir, name, type == 0);

                if (!PathsEqual(src, dst))
                {
                    if (type == 0) Directory.Move(src, dst);
                    else File.Move(src, dst);
                }

                moved++;
                if (moved == 1 || moved % 25 == 0 || moved == items.Count)
                    progressReport.Report(new WorkProgress(moved, items.Count,
                        moved + " / " + items.Count + " işlendi  •  " + Path.GetFileName(groupDir)));
            }

            return items.Count + " öğe başarıyla " + groupSize + "'lik gruplara ayrıldı.";
        }

        List<string> RenameFoldersSafely(List<string> folders, string root, CancellationToken token, IProgress<WorkProgress> report)
        {
            var tempPaths = new List<string>(folders.Count);
            string stamp = "__ATMACA_TMP_" + Guid.NewGuid().ToString("N") + "_";

            for (int i = 0; i < folders.Count; i++)
            {
                token.ThrowIfCancellationRequested();
                string tmp = Path.Combine(root, stamp + (i + 1).ToString("D9"));
                Directory.Move(folders[i], tmp);
                tempPaths.Add(tmp);
                if ((i + 1) % 100 == 0 || i + 1 == folders.Count)
                    report.Report(new WorkProgress(i + 1, folders.Count, "Geçici adlandırma: " + (i + 1) + " / " + folders.Count));
            }

            var finalPaths = new List<string>(folders.Count);
            for (int i = 0; i < tempPaths.Count; i++)
            {
                token.ThrowIfCancellationRequested();
                string final = Path.Combine(root, (i + 1).ToString());
                if (Directory.Exists(final))
                    final = UniqueDestination(root, (i + 1).ToString(), true);
                Directory.Move(tempPaths[i], final);
                finalPaths.Add(final);
                if ((i + 1) % 100 == 0 || i + 1 == tempPaths.Count)
                    report.Report(new WorkProgress(i + 1, tempPaths.Count, "Numaralandırma: " + (i + 1) + " / " + tempPaths.Count));
            }
            return finalPaths;
        }

        bool MatchType(string path, int type)
        {
            string ext = Path.GetExtension(path);
            if (type == 1) return imageExts.Contains(ext);
            if (type == 2) return videoExts.Contains(ext);
            return imageExts.Contains(ext) || videoExts.Contains(ext);
        }

        bool IsGroupFolderName(string name)
        {
            return Regex.IsMatch(name ?? "", @"^\d+\-\d+$");
        }

        bool IsUserFolder(string path)
        {
            try
            {
                string name = Path.GetFileName(path.TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar));
                if (string.Equals(name, "$RECYCLE.BIN", StringComparison.OrdinalIgnoreCase)) return false;
                if (string.Equals(name, "System Volume Information", StringComparison.OrdinalIgnoreCase)) return false;
                if (string.Equals(name, "Recovery", StringComparison.OrdinalIgnoreCase)) return false;

                var attr = File.GetAttributes(path);
                if ((attr & FileAttributes.ReparsePoint) != 0) return false;
                if ((attr & FileAttributes.System) != 0) return false;

                // Access check only; nothing is changed here.
                using (var e = Directory.EnumerateFileSystemEntries(path).GetEnumerator())
                {
                    e.MoveNext();
                }
                return true;
            }
            catch
            {
                return false;
            }
        }

        string UniqueDestination(string folder, string name, bool isDirectory)
        {
            string candidate = Path.Combine(folder, name);
            bool exists = isDirectory ? Directory.Exists(candidate) : File.Exists(candidate);
            if (!exists) return candidate;

            string baseName = isDirectory ? name : Path.GetFileNameWithoutExtension(name);
            string ext = isDirectory ? "" : Path.GetExtension(name);
            int n = 2;
            do
            {
                candidate = Path.Combine(folder, baseName + " (" + n + ")" + ext);
                exists = isDirectory ? Directory.Exists(candidate) : File.Exists(candidate);
                n++;
            } while (exists);
            return candidate;
        }

        bool PathsEqual(string a, string b)
        {
            return string.Equals(Path.GetFullPath(a).TrimEnd('\\'), Path.GetFullPath(b).TrimEnd('\\'), StringComparison.OrdinalIgnoreCase);
        }

        class WorkProgress
        {
            public long Done;
            public long Total;
            public string Message;
            public WorkProgress(long done, long total, string message)
            {
                Done = done; Total = total; Message = message;
            }
        }
    }
}

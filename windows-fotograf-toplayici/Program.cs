using System;
using System.Collections.Generic;
using System.Drawing;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace AtmacaFotografToplayici
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
        readonly ListBox sourceList = new ListBox();
        readonly TextBox targetBox = new TextBox();
        readonly Button addButton = new Button();
        readonly Button removeButton = new Button();
        readonly Button clearButton = new Button();
        readonly Button targetButton = new Button();
        readonly Button startButton = new Button();
        readonly Label statusLabel = new Label();
        readonly ProgressBar progress = new ProgressBar();

        static readonly HashSet<string> ImageExtensions = new HashSet<string>(StringComparer.OrdinalIgnoreCase)
        {
            ".jpg",".jpeg",".png",".bmp",".gif",".tif",".tiff",".webp",".jfif",".heic",".heif",".avif"
        };

        public MainForm()
        {
            Text = "ATMACA - Fotoğraf Toplayıcı";
            Width = 760;
            Height = 560;
            MinimumSize = new Size(680, 500);
            StartPosition = FormStartPosition.CenterScreen;
            Font = new Font("Segoe UI", 10F);
            AllowDrop = true;

            var title = new Label {
                Text = "Seçilen klasörlerdeki tüm fotoğrafları tek klasörde topla",
                AutoSize = false, Height = 42, Dock = DockStyle.Top,
                TextAlign = ContentAlignment.MiddleCenter,
                Font = new Font("Segoe UI Semibold", 14F, FontStyle.Bold)
            };

            var info = new Label {
                Text = "Aynı isimli dosyalar: foto.jpg → foto+1.jpg → foto+2.jpg",
                AutoSize = false, Height = 30, Dock = DockStyle.Top,
                TextAlign = ContentAlignment.MiddleCenter
            };

            var panel = new Panel { Dock = DockStyle.Fill, Padding = new Padding(14) };

            sourceList.Anchor = AnchorStyles.Top | AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right;
            sourceList.Left = 14; sourceList.Top = 8; sourceList.Width = 540; sourceList.Height = 290;
            sourceList.HorizontalScrollbar = true;

            addButton.Text = "Klasör Ekle";
            addButton.SetBounds(570, 8, 150, 42);
            addButton.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            addButton.Click += (s,e) => AddFolder();

            removeButton.Text = "Seçileni Çıkar";
            removeButton.SetBounds(570, 58, 150, 42);
            removeButton.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            removeButton.Click += (s,e) => {
                while (sourceList.SelectedIndices.Count > 0)
                    sourceList.Items.RemoveAt(sourceList.SelectedIndices[0]);
            };

            clearButton.Text = "Listeyi Temizle";
            clearButton.SetBounds(570, 108, 150, 42);
            clearButton.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            clearButton.Click += (s,e) => sourceList.Items.Clear();

            var targetLabel = new Label { Text = "Hedef klasör:", AutoSize = true, Left = 14, Top = 315 };
            targetBox.SetBounds(14, 340, 540, 32);
            targetBox.Anchor = AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right;

            targetButton.Text = "Hedef Seç";
            targetButton.SetBounds(570, 337, 150, 38);
            targetButton.Anchor = AnchorStyles.Bottom | AnchorStyles.Right;
            targetButton.Click += (s,e) => SelectTarget();

            progress.SetBounds(14, 390, 706, 24);
            progress.Anchor = AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right;
            progress.Style = ProgressBarStyle.Marquee;
            progress.Visible = false;

            statusLabel.Text = "Hazır.";
            statusLabel.SetBounds(14, 420, 706, 30);
            statusLabel.Anchor = AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right;

            startButton.Text = "FOTOĞRAFLARI TOPLA";
            startButton.SetBounds(14, 458, 706, 48);
            startButton.Anchor = AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right;
            startButton.Font = new Font("Segoe UI Semibold", 11F, FontStyle.Bold);
            startButton.Click += async (s,e) => await StartCopyAsync();

            panel.Controls.AddRange(new Control[] {
                sourceList, addButton, removeButton, clearButton,
                targetLabel, targetBox, targetButton, progress, statusLabel, startButton
            });

            Controls.Add(panel);
            Controls.Add(info);
            Controls.Add(title);

            DragEnter += (s,e) => {
                if (e.Data.GetDataPresent(DataFormats.FileDrop)) e.Effect = DragDropEffects.Copy;
            };
            DragDrop += (s,e) => {
                var paths = (string[])e.Data.GetData(DataFormats.FileDrop);
                foreach (var p in paths.Where(Directory.Exists)) AddSourcePath(p);
            };
        }

        void AddFolder()
        {
            using (var dlg = new FolderBrowserDialog())
            {
                dlg.Description = "Fotoğrafların bulunduğu klasörü seç";
                if (dlg.ShowDialog(this) == DialogResult.OK) AddSourcePath(dlg.SelectedPath);
            }
        }

        void AddSourcePath(string path)
        {
            path = Path.GetFullPath(path).TrimEnd(Path.DirectorySeparatorChar);
            foreach (var item in sourceList.Items)
                if (string.Equals(item.ToString(), path, StringComparison.OrdinalIgnoreCase)) return;
            sourceList.Items.Add(path);
        }

        void SelectTarget()
        {
            using (var dlg = new FolderBrowserDialog())
            {
                dlg.Description = "Tüm fotoğrafların toplanacağı hedef klasörü seç";
                if (dlg.ShowDialog(this) == DialogResult.OK) targetBox.Text = dlg.SelectedPath;
            }
        }

        async Task StartCopyAsync()
        {
            var sources = sourceList.Items.Cast<object>().Select(x => x.ToString()).Where(Directory.Exists).ToArray();
            var target = targetBox.Text.Trim();

            if (sources.Length == 0) { MessageBox.Show(this, "En az bir kaynak klasör ekle."); return; }
            if (string.IsNullOrWhiteSpace(target)) { MessageBox.Show(this, "Hedef klasörü seç."); return; }

            try { Directory.CreateDirectory(target); target = Path.GetFullPath(target).TrimEnd(Path.DirectorySeparatorChar); }
            catch (Exception ex) { MessageBox.Show(this, "Hedef klasör açılamadı:\n" + ex.Message); return; }

            SetBusy(true);
            int copied = 0, skipped = 0, errors = 0;
            var finalTarget = target;

            await Task.Run(() =>
            {
                foreach (var src in sources)
                {
                    foreach (var file in EnumerateFilesSafe(src, finalTarget))
                    {
                        try
                        {
                            if (!ImageExtensions.Contains(Path.GetExtension(file))) continue;
                            var dest = GetUniqueDestination(finalTarget, Path.GetFileName(file));
                            MoveFileRobust(file, dest);
                            copied++;
                            if ((copied % 50) == 0) BeginInvoke((Action)(() => statusLabel.Text = copied + " fotoğraf taşındı/g..."));
                        }
                        catch { errors++; }
                    }
                }
            });

            statusLabel.Text = "Tamamlandı. Taşınan: " + copied + " | Hata: " + errors + " | Atlanan: " + skipped;
            SetBusy(false);
            MessageBox.Show(this,
                "İşlem tamamlandı.\n\nTaşınan fotoğraf: " + copied +
                "\nHata: " + errors +
                "\n\nKaynak fotoğraflar hedefe taşındı.",
                "ATMACA - Fotoğraf Toplayıcı", MessageBoxButtons.OK, MessageBoxIcon.Information);
        }

        static IEnumerable<string> EnumerateFilesSafe(string root, string target)
        {
            var stack = new Stack<string>();
            stack.Push(root);

            while (stack.Count > 0)
            {
                var dir = stack.Pop();
                string full;
                try { full = Path.GetFullPath(dir).TrimEnd(Path.DirectorySeparatorChar); }
                catch { continue; }

                if (string.Equals(full, target, StringComparison.OrdinalIgnoreCase) ||
                    full.StartsWith(target + Path.DirectorySeparatorChar, StringComparison.OrdinalIgnoreCase))
                    continue;

                string[] files = null;
                try { files = Directory.GetFiles(full); } catch { }
                if (files != null)
                    foreach (var f in files) yield return f;

                string[] dirs = null;
                try { dirs = Directory.GetDirectories(full); } catch { }
                if (dirs != null)
                    foreach (var d in dirs) stack.Push(d);
            }
        }

        static void MoveFileRobust(string source, string destination)
        {
            try
            {
                File.Move(source, destination);
            }
            catch (IOException)
            {
                File.Copy(source, destination, false);
                File.Delete(source);
            }
        }

        static string GetUniqueDestination(string target, string fileName)
        {
            var first = Path.Combine(target, fileName);
            if (!File.Exists(first)) return first;

            var ext = Path.GetExtension(fileName);
            var stem = Path.GetFileNameWithoutExtension(fileName);
            int i = 1;
            while (true)
            {
                var candidate = Path.Combine(target, stem + "+" + i + ext);
                if (!File.Exists(candidate)) return candidate;
                i++;
            }
        }

        void SetBusy(bool busy)
        {
            addButton.Enabled = removeButton.Enabled = clearButton.Enabled = targetButton.Enabled = startButton.Enabled = !busy;
            sourceList.Enabled = targetBox.Enabled = !busy;
            progress.Visible = busy;
            if (busy) statusLabel.Text = "Fotoğraflar toplanıyor...";
        }
    }
}

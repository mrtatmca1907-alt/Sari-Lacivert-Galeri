from pathlib import Path
import runpy

# Once V5 modern motor.
runpy.run_path('../bestgallery/patch_v5.py', run_name='__main__')

# Harmless easter egg: About screen copyright line long-press.
about = Path('commons/src/main/kotlin/com/eagle/commons/activities/AboutActivity.kt')
s = about.read_text()
old = '''    private fun setupCopyright() {
        val versionName = intent.getStringExtra(APP_VERSION_NAME) ?: ""
        val year = Calendar.getInstance().get(Calendar.YEAR)
        about_copyright.text = String.format(getString(R.string.copyright), versionName, year)
    }'''
new = '''    private fun setupCopyright() {
        val versionName = intent.getStringExtra(APP_VERSION_NAME) ?: ""
        val year = Calendar.getInstance().get(Calendar.YEAR)
        about_copyright.text = String.format(getString(R.string.copyright), versionName, year)
        about_copyright.setOnLongClickListener {
            toast("ATMACA virüsü aktif 😂 Şaka lan, zararsız. Motorun sahibine selam var 😎")
            true
        }
    }'''
if old not in s:
    raise RuntimeError('About easter egg patch target not found')
about.write_text(s.replace(old, new, 1))

print('V5 harmless easter egg added')

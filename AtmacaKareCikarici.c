#define UNICODE
#define _UNICODE
#include <windows.h>
#include <commdlg.h>
#include <shlobj.h>
#include <stdio.h>
#include <stdlib.h>
#include <wchar.h>
#include <string.h>
#include <math.h>

#define ID_INPUT 101
#define ID_OUTPUT 102
#define ID_BROWSE_INPUT 103
#define ID_BROWSE_OUTPUT 104
#define ID_START 105
#define ID_LOG 106
#define ID_FILE 107
#define WM_LOG (WM_APP+1)
#define WM_DONE (WM_APP+2)
#define FFMPEG_BYTES 102856192ULL

static HWND window, inputEdit, outputEdit, logEdit, startButton;
static wchar_t inputPath[32768], outputPath[32768], ffmpegPath[32768];
static unsigned long long completed, removed, failed, framesWritten;
static int stopRequested;
static wchar_t **videos;
static size_t videoCount, videoCapacity;

static void log_text(const wchar_t *s) {
    size_t n=wcslen(s)+1;
    wchar_t *copy=(wchar_t*)malloc(n*sizeof(wchar_t));
    if (copy) { wcscpy(copy,s); PostMessageW(window,WM_LOG,0,(LPARAM)copy); }
}
static void log_item(const wchar_t *prefix,const wchar_t *value) {
    wchar_t msg[1200]; swprintf(msg,1200,L"%ls%ls\r\n",prefix,value);log_text(msg);
}
static void append_log(wchar_t *s) {
    int len=GetWindowTextLengthW(logEdit);
    SendMessageW(logEdit,EM_SETSEL,len,len);
    SendMessageW(logEdit,EM_REPLACESEL,FALSE,(LPARAM)s);
    free(s);
}
static void quote(wchar_t *out,size_t cap,const wchar_t *path) {
    size_t j=0;out[j++]=L'"';
    for(size_t i=0;path[i]&&j+3<cap;i++) {
        if(path[i]==L'"')out[j++]=L'\\';
        out[j++]=path[i];
    }
    out[j++]=L'"';out[j]=0;
}
static int ensure_dir(const wchar_t *path) {
    if(CreateDirectoryW(path,NULL))return 1;
    return GetLastError()==ERROR_ALREADY_EXISTS;
}
static int exists_nonempty(const wchar_t *path) {
    WIN32_FILE_ATTRIBUTE_DATA a;
    return GetFileAttributesExW(path,GetFileExInfoStandard,&a) &&
        a.ftLastWriteTime.dwLowDateTime!=0 && (a.nFileSizeHigh||a.nFileSizeLow);
}
static int extract_ffmpeg(void) {
    wchar_t appdata[32768],self[32768],folder[32768];
    if(!GetEnvironmentVariableW(L"LOCALAPPDATA",appdata,32768))return 0;
    swprintf(folder,32768,L"%ls\\AtmacaKareCikarici",appdata);
    if(!ensure_dir(folder))return 0;
    swprintf(ffmpegPath,32768,L"%ls\\ffmpeg.exe",folder);
    if(!GetModuleFileNameW(NULL,self,32768))return 0;
    HANDLE src=CreateFileW(self,GENERIC_READ,FILE_SHARE_READ,NULL,OPEN_EXISTING,0,NULL);
    if(src==INVALID_HANDLE_VALUE)return 0;
    LARGE_INTEGER len; if(!GetFileSizeEx(src,&len)||len.QuadPart<(LONGLONG)(FFMPEG_BYTES+16)) {CloseHandle(src);return 0;}
    LARGE_INTEGER pos;pos.QuadPart=len.QuadPart-FFMPEG_BYTES-16;
    SetFilePointerEx(src,pos,NULL,FILE_BEGIN);
    char marker[16];DWORD read=0;
    if(!ReadFile(src,marker,16,&read,NULL)||read!=16||memcmp(marker,"ATMACA_FFMPEG_V1",16)!=0){CloseHandle(src);return 0;}
    wchar_t temp[32768];swprintf(temp,32768,L"%ls\\ffmpeg.new",folder);
    HANDLE dst=CreateFileW(temp,GENERIC_WRITE,0,NULL,CREATE_ALWAYS,FILE_ATTRIBUTE_NORMAL,NULL);
    if(dst==INVALID_HANDLE_VALUE){CloseHandle(src);return 0;}
    char *buf=(char*)malloc(1<<20);unsigned long long left=FFMPEG_BYTES;int okay=buf!=NULL;
    while(okay&&left){DWORD want=left>(1<<20)?(1<<20):(DWORD)left,got=0,wrote=0;
        okay=ReadFile(src,buf,want,&got,NULL)&&got==want&&WriteFile(dst,buf,got,&wrote,NULL)&&wrote==got;
        left-=got;
    }
    free(buf);CloseHandle(dst);CloseHandle(src);
    if(!okay){DeleteFileW(temp);return 0;}
    if(!MoveFileExW(temp,ffmpegPath,MOVEFILE_REPLACE_EXISTING|MOVEFILE_WRITE_THROUGH)){DeleteFileW(temp);return 0;}
    return 1;
}
static int run_ffmpeg(const wchar_t *args,wchar_t *diagnostic,DWORD diagnosticCap) {
    wchar_t cmd[65536],qexe[65536],logfile[32768],temp[32768];
    quote(qexe,65536,ffmpegPath);
    swprintf(cmd,65536,L"%ls %ls",qexe,args);
    if(!GetTempPathW(32768,temp)||!GetTempFileNameW(temp,L"AKC",0,logfile))return -1;
    HANDLE log=CreateFileW(logfile,GENERIC_WRITE,FILE_SHARE_READ,NULL,CREATE_ALWAYS,FILE_ATTRIBUTE_TEMPORARY,NULL);
    if(log==INVALID_HANDLE_VALUE){DeleteFileW(logfile);return -1;}
    SetHandleInformation(log,HANDLE_FLAG_INHERIT,HANDLE_FLAG_INHERIT);
    STARTUPINFOW si={0};si.cb=sizeof(si);si.dwFlags=STARTF_USESTDHANDLES;
    HANDLE nullInput=CreateFileW(L"NUL",GENERIC_READ,FILE_SHARE_READ|FILE_SHARE_WRITE,NULL,OPEN_EXISTING,0,NULL);
    if(nullInput!=INVALID_HANDLE_VALUE)SetHandleInformation(nullInput,HANDLE_FLAG_INHERIT,HANDLE_FLAG_INHERIT);
    si.hStdInput=nullInput;si.hStdOutput=log;si.hStdError=log;
    PROCESS_INFORMATION pi={0};
    BOOL ok=CreateProcessW(ffmpegPath,cmd,NULL,NULL,TRUE,CREATE_NO_WINDOW|BELOW_NORMAL_PRIORITY_CLASS,NULL,NULL,&si,&pi);
    CloseHandle(log);
    if(nullInput!=INVALID_HANDLE_VALUE)CloseHandle(nullInput);
    int code=-1;
    if(ok){WaitForSingleObject(pi.hProcess,INFINITE);DWORD exitCode=1;GetExitCodeProcess(pi.hProcess,&exitCode);code=(int)exitCode;CloseHandle(pi.hThread);CloseHandle(pi.hProcess);}
    if(diagnostic&&diagnosticCap){
        diagnostic[0]=0;HANDLE f=CreateFileW(logfile,GENERIC_READ,FILE_SHARE_READ,NULL,OPEN_EXISTING,0,NULL);
        if(f!=INVALID_HANDLE_VALUE){char *bytes=(char*)malloc(200000);
            if(bytes){DWORD got=0;ReadFile(f,bytes,199999,&got,NULL);bytes[got]=0;
                MultiByteToWideChar(CP_UTF8,0,bytes,-1,diagnostic,diagnosticCap);
                free(bytes);}
            CloseHandle(f);}
    }
    DeleteFileW(logfile);return code;
}
static int duration_seconds(const wchar_t *video) {
    wchar_t q[65536],args[65536],diagnostic[200000];quote(q,65536,video);
    swprintf(args,65536,L"-hide_banner -nostdin -i %ls",q);
    run_ffmpeg(args,diagnostic,200000);
    wchar_t *s=wcsstr(diagnostic,L"Duration: ");
    if(!s)return 0;
    int h=0,m=0;double sec=0;
    if(swscanf(s,L"Duration: %d:%d:%lf",&h,&m,&sec)!=3)return 0;
    double total=3600.0*h+60.0*m+sec;
    if(!isfinite(total)||total<=0||total>86400.0)return 0;
    int count=(int)floor(total+0.00001);
    return count>0?count:1;
}
static int is_video(const wchar_t *name){
    const wchar_t *ext=wcsrchr(name,L'.');if(!ext)return 0;
    const wchar_t *list[]={L".mp4",L".mkv",L".avi",L".mov",L".wmv",L".flv",L".webm",L".m4v",L".mpg",L".mpeg",L".3gp",L".ts",L".mts",L".m2ts",NULL};
    for(int i=0;list[i];i++)if(!_wcsicmp(ext,list[i]))return 1;
    return 0;
}
static int frame_count(const wchar_t *folder,const wchar_t *stem){
    wchar_t glob[32768];swprintf(glob,32768,L"%ls\\%ls_*.jpg",folder,stem);
    WIN32_FIND_DATAW d;HANDLE h=FindFirstFileW(glob,&d);if(h==INVALID_HANDLE_VALUE)return 0;
    int n=0;do{if(!(d.dwFileAttributes&FILE_ATTRIBUTE_DIRECTORY) && (d.nFileSizeLow||d.nFileSizeHigh))n++;}while(FindNextFileW(h,&d));
    FindClose(h);return n;
}
static void frame_path(wchar_t *dest,const wchar_t *folder,const wchar_t *stem,int second){swprintf(dest,32768,L"%ls\\%ls_%08d.jpg",folder,stem,second);}
static int first_missing_frame(const wchar_t *folder,const wchar_t *stem,int expected){
    wchar_t path[32768];int n=0;
    for(;;n++){
        if(expected>0 && n>=expected)return n;
        frame_path(path,folder,stem,n);
        if(!exists_nonempty(path))return n;
    }
}
static void process_video(const wchar_t *video){
    wchar_t name[512],stem[512],folder[32768],qVideo[65536],qPattern[65536],pattern[32768],args[131072],msg[1200];
    const wchar_t *base=wcsrchr(video,L'\\');base=base?base+1:video;
    wcsncpy(name,base,511);name[511]=0;wcsncpy(stem,name,511);stem[511]=0;
    wchar_t *dot=wcsrchr(stem,L'.');if(dot)*dot=0;
    for(wchar_t *p=stem;*p;p++)if(wcschr(L"<>:\"/\\|?*",*p))*p=L'_';

    // Aynı video her çalıştırmada aynı klasörü kullanır. Böylece yarıda kalan iş devam eder;
    // stem_2, stem_3 ve Windows'un (1)/(2) kopyaları oluşmaz.
    swprintf(folder,32768,L"%ls\\%ls",outputPath,stem);
    if(!ensure_dir(folder)){failed++;log_item(L"Klasör oluşturulamadı: ",video);return;}

    int expected=duration_seconds(video);
    int resume=first_missing_frame(folder,stem,expected);
    int before=frame_count(folder,stem);
    quote(qVideo,65536,video);
    swprintf(pattern,32768,L"%ls\\%ls_%%08d.jpg",folder,stem);quote(qPattern,65536,pattern);

    if(expected>0 && resume>=expected){
        swprintf(msg,1200,L"Devam: %ls | %d kare zaten hazır\r\n",name,before);log_text(msg);
    } else {
        if(resume>0){
            swprintf(msg,1200,L"Devam ediliyor: %ls | %d. saniyeden\r\n",name,resume);log_text(msg);
            swprintf(args,131072,L"-hide_banner -nostdin -y -threads 1 -ss %d -fflags +genpts+discardcorrupt -err_detect ignore_err -i %ls -an -sn -vf fps=1 -q:v 3 -start_number %d %ls",resume,qVideo,resume,qPattern);
        } else {
            swprintf(args,131072,L"-hide_banner -nostdin -y -threads 1 -fflags +genpts+discardcorrupt -err_detect ignore_err -i %ls -an -sn -vf fps=1:start_time=0 -q:v 3 -start_number 0 %ls",qVideo,qPattern);
        }
        int exitcode=run_ffmpeg(args,NULL,0);
        int after=frame_count(folder,stem);

        // Hızlı seek bazı ağır hasarlı videolarda çalışmazsa bir kez güvenli yoldan dene.
        if(resume>0 && after<=before && !stopRequested){
            swprintf(args,131072,L"-hide_banner -nostdin -y -threads 1 -fflags +genpts+discardcorrupt -err_detect ignore_err -i %ls -ss %d -an -sn -vf fps=1 -q:v 3 -start_number %d %ls",qVideo,resume,resume,qPattern);
            exitcode=run_ffmpeg(args,NULL,0);
            after=frame_count(folder,stem);
        }

        // Eksik son saniyeleri ayrı ayrı FFmpeg açarak sistemi kilitlemek yerine son sağlam kareyle tamamla.
        if(expected>0 && after>0 && after<expected && !stopRequested){
            wchar_t last[32768],current[32768];
            int lastIndex=first_missing_frame(folder,stem,expected)-1;
            if(lastIndex>=0){
                frame_path(last,folder,stem,lastIndex);
                for(int n=lastIndex+1;n<expected&&!stopRequested;n++){
                    frame_path(current,folder,stem,n);
                    if(!exists_nonempty(current))CopyFileW(last,current,TRUE);
                }
            }
        }
        (void)exitcode;
    }

    int count=frame_count(folder,stem);
    completed++;
    if(count>0 && !stopRequested){
        framesWritten+=count;
        if(DeleteFileW(video)){removed++;swprintf(msg,1200,L"Silindi: %ls | %d kare\r\n",name,count);log_text(msg);}
        else {failed++;log_item(L"Kareler çıktı, video silinemedi: ",video);}
    } else if(!stopRequested){failed++;log_item(L"Kare çıkarılamadı, video korundu: ",video);}
}
static void add_video(const wchar_t *path){
    if(videoCount==videoCapacity){size_t next=videoCapacity?videoCapacity*2:1024;
        wchar_t **expanded=(wchar_t**)realloc(videos,next*sizeof(wchar_t*));if(!expanded){stopRequested=1;return;}
        videos=expanded;videoCapacity=next;}
    videos[videoCount]=_wcsdup(path);if(videos[videoCount])videoCount++;else stopRequested=1;
}
static void scan_folder(const wchar_t *folder){
    if(stopRequested)return;
    wchar_t glob[32768];swprintf(glob,32768,L"%ls\\*",folder);
    WIN32_FIND_DATAW data;HANDLE h=FindFirstFileW(glob,&data);if(h==INVALID_HANDLE_VALUE)return;
    do{
        if(stopRequested)break;
        if(!wcscmp(data.cFileName,L".")||!wcscmp(data.cFileName,L".."))continue;
        wchar_t full[32768];swprintf(full,32768,L"%ls\\%ls",folder,data.cFileName);
        if(data.dwFileAttributes&FILE_ATTRIBUTE_DIRECTORY){
            if(data.dwFileAttributes&FILE_ATTRIBUTE_REPARSE_POINT)continue;
            if(!_wcsicmp(full,outputPath))continue;
            if(!_wcsicmp(data.cFileName,L"$RECYCLE.BIN")||!_wcsicmp(data.cFileName,L"System Volume Information"))continue;
            scan_folder(full);
        }else if(is_video(data.cFileName))add_video(full);
    }while(FindNextFileW(h,&data));
    FindClose(h);
}
static DWORD WINAPI worker(LPVOID unused){
    (void)unused;
    if(!extract_ffmpeg()){log_text(L"FFmpeg çıkarılamadı. EXE dosyasını yeniden indirin.\r\n");PostMessageW(window,WM_DONE,0,0);return 0;}
    log_text(L"Tarama ve kare çıkarma başladı.\r\n");
    DWORD attr=GetFileAttributesW(inputPath);
    if(attr!=INVALID_FILE_ATTRIBUTES && (attr&FILE_ATTRIBUTE_DIRECTORY))scan_folder(inputPath);
    else if(attr!=INVALID_FILE_ATTRIBUTES && is_video(inputPath))add_video(inputPath);
    wchar_t found[160];swprintf(found,160,L"Bulunan video: %llu\r\n",(unsigned long long)videoCount);log_text(found);
    for(size_t i=0;i<videoCount&&!stopRequested;i++){process_video(videos[i]);free(videos[i]);videos[i]=NULL;}
    for(size_t i=0;i<videoCount;i++)free(videos[i]);free(videos);videos=NULL;videoCount=videoCapacity=0;
    wchar_t summary[500];swprintf(summary,500,L"Bitti. Video: %llu | Silinen: %llu | Hata: %llu | Kare: %llu\r\n",completed,removed,failed,framesWritten);
    log_text(summary);PostMessageW(window,WM_DONE,0,0);return 0;
}
static void choose_folder(HWND edit){
    BROWSEINFOW b={0};b.hwndOwner=window;b.lpszTitle=L"Klasör seçin";b.ulFlags=BIF_RETURNONLYFSDIRS|BIF_NEWDIALOGSTYLE;
    PIDLIST_ABSOLUTE id=SHBrowseForFolderW(&b);
    if(id){wchar_t path[32768];if(SHGetPathFromIDListW(id,path))SetWindowTextW(edit,path);CoTaskMemFree(id);}
}
static LRESULT CALLBACK wndproc(HWND hwnd,UINT msg,WPARAM w,LPARAM l){
    switch(msg){
    case WM_CREATE:{
        CreateWindowW(L"STATIC",L"Video klasörü veya tek video:",WS_CHILD|WS_VISIBLE,16,15,650,22,hwnd,NULL,NULL,NULL);
        inputEdit=CreateWindowExW(WS_EX_CLIENTEDGE,L"EDIT",L"E:\\",WS_CHILD|WS_VISIBLE|ES_AUTOHSCROLL,16,42,590,27,hwnd,(HMENU)ID_INPUT,NULL,NULL);
        CreateWindowW(L"BUTTON",L"Klasör seç",WS_CHILD|WS_VISIBLE,615,42,120,27,hwnd,(HMENU)ID_BROWSE_INPUT,NULL,NULL);
        CreateWindowW(L"STATIC",L"Karelerin kaydedileceği klasör:",WS_CHILD|WS_VISIBLE,16,86,650,22,hwnd,NULL,NULL,NULL);
        outputEdit=CreateWindowExW(WS_EX_CLIENTEDGE,L"EDIT",L"E:\\Kareler",WS_CHILD|WS_VISIBLE|ES_AUTOHSCROLL,16,113,590,27,hwnd,(HMENU)ID_OUTPUT,NULL,NULL);
        CreateWindowW(L"BUTTON",L"Klasör seç",WS_CHILD|WS_VISIBLE,615,113,120,27,hwnd,(HMENU)ID_BROWSE_OUTPUT,NULL,NULL);
        startButton=CreateWindowW(L"BUTTON",L"BAŞLAT",WS_CHILD|WS_VISIBLE,16,155,160,36,hwnd,(HMENU)ID_START,NULL,NULL);
        CreateWindowW(L"STATIC",L"En az bir kare çıkan video işlem sonunda doğrudan silinir. Kare çıkmayan video kalır.",WS_CHILD|WS_VISIBLE,190,158,540,40,hwnd,NULL,NULL,NULL);
        logEdit=CreateWindowExW(WS_EX_CLIENTEDGE,L"EDIT",L"Hazır.\r\n",WS_CHILD|WS_VISIBLE|ES_MULTILINE|ES_READONLY|WS_VSCROLL|ES_AUTOVSCROLL,16,210,720,340,hwnd,(HMENU)ID_LOG,NULL,NULL);
        HFONT font=(HFONT)GetStockObject(DEFAULT_GUI_FONT);for(int i=ID_INPUT;i<=ID_LOG;i++){HWND child=GetDlgItem(hwnd,i);if(child)SendMessageW(child,WM_SETFONT,(WPARAM)font,TRUE);}
        return 0;}
    case WM_COMMAND:
        if(LOWORD(w)==ID_BROWSE_INPUT){choose_folder(inputEdit);return 0;}
        if(LOWORD(w)==ID_BROWSE_OUTPUT){choose_folder(outputEdit);return 0;}
        if(LOWORD(w)==ID_START){
            GetWindowTextW(inputEdit,inputPath,32768);GetWindowTextW(outputEdit,outputPath,32768);
            if(!*inputPath||!*outputPath){MessageBoxW(hwnd,L"Kaynak ve hedef seçin.",L"ATMACA",MB_OK);return 0;}
            DWORD attr=GetFileAttributesW(inputPath);
            if(attr==INVALID_FILE_ATTRIBUTES){MessageBoxW(hwnd,L"Video veya kaynak klasör bulunamadı.",L"ATMACA",MB_OK);return 0;}
            if(!ensure_dir(outputPath)){MessageBoxW(hwnd,L"Hedef klasör oluşturulamadı.",L"ATMACA",MB_OK);return 0;}
            if(!_wcsicmp(inputPath,outputPath)){MessageBoxW(hwnd,L"Kaynak ve hedef aynı olamaz.",L"ATMACA",MB_OK);return 0;}
            if(MessageBoxW(hwnd,L"En az bir kare çıkarılan HER video doğrudan silinecek. Başlatılsın mı?",L"ATMACA Video Kare Çıkarıcı",MB_YESNO|MB_ICONWARNING)!=IDYES)return 0;
            completed=removed=failed=framesWritten=0;stopRequested=0;EnableWindow(startButton,FALSE);
            HANDLE thread=CreateThread(NULL,0,worker,NULL,0,NULL);if(thread)CloseHandle(thread);else EnableWindow(startButton,TRUE);
            return 0;}
        break;
    case WM_LOG:append_log((wchar_t*)l);return 0;
    case WM_DONE:EnableWindow(startButton,TRUE);return 0;
    case WM_CLOSE:stopRequested=1;DestroyWindow(hwnd);return 0;
    case WM_DESTROY:PostQuitMessage(0);return 0;
    }
    return DefWindowProcW(hwnd,msg,w,l);
}
int WINAPI wWinMain(HINSTANCE instance,HINSTANCE prev,LPWSTR cmd,int show){
    (void)prev;(void)cmd;
    WNDCLASSW wc={0};wc.lpfnWndProc=wndproc;wc.hInstance=instance;wc.lpszClassName=L"AtmacaVideoKareCikariciV2";
    wc.hCursor=LoadCursorW(NULL,IDC_ARROW);wc.hbrBackground=(HBRUSH)(COLOR_WINDOW+1);RegisterClassW(&wc);
    window=CreateWindowW(wc.lpszClassName,L"ATMACA Video Kare Çıkarıcı - Hasarlı Video Kurtarma",WS_OVERLAPPEDWINDOW|WS_VISIBLE,CW_USEDEFAULT,CW_USEDEFAULT,780,610,NULL,NULL,instance,NULL);
    ShowWindow(window,show);MSG m;while(GetMessageW(&m,NULL,0,0)>0){TranslateMessage(&m);DispatchMessageW(&m);}return 0;
}
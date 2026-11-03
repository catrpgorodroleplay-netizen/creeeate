package com.cr.arcade;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.hardware.HardwareBuffer;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioAttributes;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.*;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.*;

import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import java.net.NetworkInterface;
import java.util.Enumeration;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * ARCADE — платформа поверх игры: WebView (мессенджер, голос), макросы с анализом экрана,
 * запись экрана с «невидимым» персонажем, настройки производительности и встроенный сервер Minecraft.
 * При выходе из приложения WebView уезжает в оверлей и продолжает жить (микрофон, звук, таймеры),
 * а поверх игры появляется кнопка-геймпад: нажатие открывает меню из шести вкладок.
 */
public class MainActivity extends BridgeActivity {

    private static final String TAG = "ArcadeMP";

    // ===== playit.gg (UDP-туннель: локальный порт > публичный host:port) =====
    // Secret key агента и публичный адрес туннеля вводятся в приложении (раздел «PLAYIT.GG») и хранятся в SharedPreferences.
    private static final int PL_DEFAULT_PORT = 19140;   // локальный порт форвардера; в панели playit туннель должен вести на 127.0.0.1:этот_порт

    private static final String[] MC_PACKAGES = {"com.mojang.minecraftpe", "com.mojang.minecraftpe.preview"};
    private static final int MC_PORT = 19132;
    private static final int SRV_PORT = 19134;   // порт встроенного сервера; на 19132 стоит LAN-ответчик приложения («Друзья»)
    private static final int RC_OVERLAY = 101, RC_PERMS = 110, RC_VPN = 120, RC_PICK = 130, RC_PLUGIN = 140, RC_PACK = 150, RC_PROJ = 160, RC_TEX = 170, RC_REF = 171, RC_IMPORT = 172, RC_CHAR = 173;
    private static final String EXTRA_RELAY = "arcade_relay";
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT, WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final int FL_NF = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;

    private static final int C_BG = 0xFF111114, C_CARD = 0xFF1B1B20, C_CARD2 = 0xFF26262D, C_STROKE = 0xFF383840;
    private static final int C_RED = 0xFFE5252A, C_TEXT = 0xFFF1F1F4, C_MUTED = 0xFF8F8F9B, C_GREEN = 0xFF2ECC71;

    private static final byte[] MAGIC = {0x00, (byte) 0xff, (byte) 0xff, 0x00, (byte) 0xfe, (byte) 0xfe, (byte) 0xfe, (byte) 0xfe,
            (byte) 0xfd, (byte) 0xfd, (byte) 0xfd, (byte) 0xfd, 0x12, 0x34, 0x56, 0x78};
    private static final String ALPHA = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final int M_IDLE = 0, M_HOST = 1, M_GUEST = 2;

    // ===== Состояние =====
    private android.content.SharedPreferences prefs;
    private WindowManager wm;
    private Context appCtx;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final SecureRandom rnd = new SecureRandom();
    private final long guid = new SecureRandom().nextLong();

    private volatile int mode = M_IDLE;
    private volatile boolean stopFlag;
    private volatile String status = "Готово";
    private volatile String code = "";
    private volatile Thread engine;
    private volatile Process rtProc;               // агент playit
    private volatile DatagramSocket fwdSock;       // хост: сюда агент playit приносит пакеты друзей
    private volatile DatagramSocket guestOut;      // гость: исходящий сокет к публичному адресу хоста
    private volatile String publicAddr = "";       // host:port туннеля playit
    private volatile String rtErr = "";
    private final ArrayDeque<String> rtTail = new ArrayDeque<>();
    private volatile boolean serverStandalone;     // сервер включён кнопкой и живёт отдельно от комнаты
    private volatile boolean srvBusy;
    private PowerManager.WakeLock wakeLock;
    private WifiManager.MulticastLock mcLock;

    // host
    private volatile InetSocketAddress hostTarget;
    private final ConcurrentHashMap<String, HostPeer> hostPeers = new ConcurrentHashMap<>();

    // guest
    private volatile DatagramSocket guestSock;
    private volatile InetSocketAddress hostRelay;
    private volatile InetSocketAddress clientAddr;
    private volatile String motd;
    private volatile boolean gotGameData;
    private volatile int cntFromGuest, cntFromWorld, gSent, gRecv;
    private volatile long hostGuid;
    private volatile boolean hostGuidKnown;
    private volatile String guestBase = "";

    // UI
    private LinearLayout formBox, sessionBox;
    private EditText codeInput, portInput, tokenInput, urlInput, plPortInput;
    private TextView srvToggleBtn;
    private final ArrayList<TextView> statusViews = new ArrayList<>();
    private final ArrayList<TextView> codeViews = new ArrayList<>();
    private FrameLayout overlayRoot;
    private WindowManager.LayoutParams overlayLp;
    private WindowManager overlayWm;

    private static class HostPeer {
        final InetSocketAddress peer;
        final DatagramSocket sock;
        volatile long last = SystemClock.elapsedRealtime();
        volatile int sent, recv, idx;

        private static final java.util.concurrent.atomic.AtomicInteger LB = new java.util.concurrent.atomic.AtomicInteger();

        HostPeer(InetSocketAddress peer) throws Exception { this(peer, false); }

        /**
         * loopback = true: сокет получает СВОЙ адрес 127.0.x.y. Сервер (PocketMine/RakLib) считает игроков по IP:
         * с одного 127.0.0.1 все друзья выглядели как один клиент, и мусорный пакет от сканера или всплеск трафика
         * одного игрока блокировал всех сразу. Если привязать не удалось — обычный сокет.
         */
        HostPeer(InetSocketAddress peer, boolean loopback) throws Exception {
            this.peer = peer;
            DatagramSocket s = null;
            if (loopback) {
                for (int i = 0; i < 6 && s == null; i++) {
                    int c = LB.getAndIncrement();
                    try {
                        InetAddress a = InetAddress.getByAddress(new byte[]{127, 0, (byte) ((c / 250) % 250), (byte) (2 + c % 250)});
                        s = new DatagramSocket(new InetSocketAddress(a, 0));
                    } catch (Exception ignored) { s = null; }
                }
            }
            this.sock = s != null ? s : new DatagramSocket();
        }
    }

    // ==================== Старт / стоп сессии ====================

    private void begin(int m, String s) {
        mode = m;
        stopFlag = false;
        code = "";
        publicAddr = ""; rtErr = "";
        gotGameData = false;
        cntFromGuest = cntFromWorld = gSent = gRecv = 0;
        tunWritten = tunRead = tunErr = tunBig = tunOther = 0;
        firstGuestAt = 0; warnedSilent = false;
        hostGuidKnown = false;
        peerMismatch = 0; pingsSeen = 0;
        motd = null;
        clientAddr = null;
        try { prefs.edit().putBoolean("active", true).apply(); } catch (Exception ignored) { }
        setStatus(s);
        startKeepAlive();
        acquireLocks();
    }

    private void startHost() { startHost(false); }

    private void startHost(boolean useBuiltin) {
        if (mode != M_IDLE || !ensureOverlay()) return;
        saveRtSettings();
        if (prefs.getString("rt_token", "").isEmpty()) { setStatus("Вставьте secret key агента playit (раздел «PLAYIT.GG» выше) — он нужен, чтобы сервер стал доступен из интернета"); return; }
        if (prefs.getString("pl_addr", "").trim().isEmpty()) { setStatus("Впишите публичный адрес туннеля playit (раздел «PLAYIT.GG» выше), например name.at.ply.gg:12345"); return; }
        builtin = useBuiltin;
        int hp = MC_PORT;
        try { hp = Integer.parseInt(prefs.getString("host_port", "").trim()); } catch (Exception ignored) { }
        if (hp < 1 || hp > 65535) hp = MC_PORT;
        hostPort = hp;
        if (useBuiltin && !externalSrv) hostPort = SRV_PORT;
        if (useVpn && !vpnDenied && !useBuiltin) {
            Intent vi = null;
            try { vi = VpnService.prepare(this); } catch (Exception e) { log("VPN: проверка разрешения не удалась: " + e.getMessage()); }
            if (vi != null) {
                if (!vpnAsked) {
                    vpnAsked = true;
                    log("Прошу разрешение VPN (нужно, чтобы Minecraft принимал друзей как устройства в сети)");
                    try { relay(vi, RC_VPN); return; } catch (Exception e) { log("Не открылось окно разрешения VPN: " + e.getMessage()); vpnDenied = true; }
                } else vpnDenied = true;
            }
        }
        vpnAsked = false;
        begin(M_HOST, useBuiltin ? "Запускаю встроенный сервер…" : "Запускаю Minecraft…");
        if (!useBuiltin && !launchMinecraft()) { stopSession("Minecraft не установлен"); return; }
        Thread t = new Thread(this::hostLoop, "mp-host");
        engine = t;
        t.start();
    }

    private void startGuest(String raw) {
        if (mode != M_IDLE || !ensureOverlay()) return;
        final InetSocketAddress h = decodeCode(raw);
        if (h == null) { setStatus("Неверный код комнаты"); return; }
        stopOwnerRelay();   // порт 19132 нужен гостю
        begin(M_GUEST, "Подключаюсь к хосту…");
        Thread t = new Thread(() -> guestLoop(h), "mp-guest");
        engine = t;
        t.start();
    }

    private synchronized void stopSession(String msg) {
        stopFlag = true;
        mode = M_IDLE;
        code = "";
        stopAgent();
        DatagramSocket g = guestSock; guestSock = null;
        if (g != null) g.close();
        DatagramSocket go = guestOut; guestOut = null;
        if (go != null) go.close();
        DatagramSocket fs = fwdSock; fwdSock = null;
        if (fs != null) fs.close();
        publicAddr = "";
        for (HostPeer p : hostPeers.values()) { try { p.sock.close(); } catch (Exception ignored) { } }
        hostPeers.clear();
        tunReady = false;
        tunIn = null; tunOut = null;
        MpVpnService.shutdown();
        if (!serverStandalone) stopBuiltinServer();
        vipByKey.clear();
        java.util.Arrays.fill(vipPeers, null);
        vipNext = 2;
        log("Остановлено: " + msg.split("\n")[0]);
        Thread e = engine; engine = null;
        if (e != null && e != Thread.currentThread()) e.interrupt();
        clientAddr = null; motd = null; hostRelay = null;
        releaseLocks();
        stopKeepAlive();
        status = msg;
        if (serverStandalone && !externalSrv && srvAlive()) startOwnerRelay();   // сервер живёт отдельно от комнаты
        try { prefs.edit().putBoolean("active", false).putString("last", msg).apply(); } catch (Exception ignored) { }
        ui.post(() -> {
            pushText();
            if ("Остановлено".equals(msg)) refreshBubble();
            else ui.postDelayed(() -> { if (mode == M_IDLE) refreshBubble(); }, 60000); // оставляем текст ошибки на экране
        });
    }

    private boolean launchMinecraft() {
        for (String pkg : MC_PACKAGES) {
            try {
                Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
                if (i != null) {
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                    return true;
                }
            } catch (Exception e) { Log.w(TAG, "launch " + pkg, e); }
        }
        return false;
    }

    private void acquireLocks() {
        try {
            if (wakeLock == null) {
                PowerManager pm = (PowerManager) appCtx.getSystemService(Context.POWER_SERVICE);
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "arcade:mp");
            }
            if (!wakeLock.isHeld()) wakeLock.acquire(6 * 60 * 60 * 1000L);
        } catch (Exception ignored) { }
        try {
            if (mcLock == null) {
                WifiManager w = (WifiManager) appCtx.getSystemService(Context.WIFI_SERVICE);
                mcLock = w.createMulticastLock("arcade:mp");
                mcLock.setReferenceCounted(false);
            }
            if (!mcLock.isHeld()) mcLock.acquire();
        } catch (Exception ignored) { }
    }

    private void releaseLocks() {
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Exception ignored) { }
        try { if (mcLock != null && mcLock.isHeld()) mcLock.release(); } catch (Exception ignored) { }
    }

    private void startKeepAlive() {
        try {
            Intent i = new Intent(this, MpService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        } catch (Exception e) { Log.w(TAG, "keepalive: " + e); }
    }

    private void stopKeepAlive() {
        if (gameMode || bubble != null) return;
        try { stopService(new Intent(this, MpService.class)); } catch (Exception ignored) { }
    }

    /** VPN-служба: создаёт виртуальную сеть 10.77.0.0/24. Интернет-трафик других приложений она НЕ перехватывает. */
    public static class MpVpnService extends VpnService {
        static volatile ParcelFileDescriptor tun;
        static volatile MpVpnService instance;
        static volatile String err = "";

        @Override public int onStartCommand(Intent i, int flags, int startId) {
            instance = this;
            try {
                if (tun == null) {
                    VpnService.Builder b = new VpnService.Builder();
                    b.setSession("ARCADE");
                    b.addAddress("10.77.0.1", 24);
                    b.addRoute("10.77.0.0", 24);
                    b.setMtu(1500);
                    b.setBlocking(true);
                    tun = b.establish();
                    if (tun == null) err = "establish() вернул null — разрешение VPN не выдано или отозвано";
                    else err = "";
                }
            } catch (Exception e) {
                tun = null;
                err = e.getClass().getSimpleName() + ": " + e.getMessage();
                Log.e(TAG, "vpn", e);
            }
            return START_NOT_STICKY;
        }

        static void shutdown() {
            ParcelFileDescriptor t = tun; tun = null;
            if (t != null) { try { t.close(); } catch (Exception ignored) { } }
            MpVpnService s = instance; instance = null;
            if (s != null) { try { s.stopSelf(); } catch (Exception ignored) { } }
        }

        @Override public void onRevoke() { err = "VPN отозван системой (включили другой VPN?)"; ParcelFileDescriptor t = tun; tun = null; if (t != null) { try { t.close(); } catch (Exception ignored) { } } super.onRevoke(); }
        @Override public void onDestroy() { ParcelFileDescriptor t = tun; tun = null; if (t != null) { try { t.close(); } catch (Exception ignored) { } } instance = null; super.onDestroy(); }
    }

    /** Служба доступности нужна только как «хозяин» оверлея: её окна Minecraft скрыть не может. */
    public static class OverlayAccService extends android.accessibilityservice.AccessibilityService {
        static volatile OverlayAccService instance;
        @Override protected void onServiceConnected() { instance = this; }
        @Override public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent e) { }
        @Override public void onInterrupt() { }
        @Override public boolean onUnbind(Intent i) { instance = null; return super.onUnbind(i); }
        @Override public void onDestroy() { instance = null; super.onDestroy(); }
    }

    /** Foreground-сервис: держит процесс живым, пока вы в игре (микрофон WebView, макросы, сервер). */
    public static class MpService extends Service {
        @Override public IBinder onBind(Intent i) { return null; }

        @Override public int onStartCommand(Intent i, int flags, int startId) {
            String ch = "arcade_mp";
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                nm.createNotificationChannel(new NotificationChannel(ch, "ARCADE", NotificationManager.IMPORTANCE_LOW));
            }
            Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, ch) : new Notification.Builder(this);
            int icon = getApplicationInfo().icon;
            b.setContentTitle("ARCADE работает").setContentText("Оверлей, голос и макросы активны")
                    .setSmallIcon(icon != 0 ? icon : android.R.drawable.ic_dialog_info).setOngoing(true);
            Notification n = b.build();
            boolean mic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
            try {
                if (Build.VERSION.SDK_INT >= 34) startForeground(77, n, 0x40000000 | (mic ? 0x80 : 0));
                else if (Build.VERSION.SDK_INT >= 30) startForeground(77, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC | (mic ? 0x80 : 0));
                else if (Build.VERSION.SDK_INT >= 29) startForeground(77, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
                else startForeground(77, n);
            } catch (Exception e) {
                Log.w(TAG, "fg", e);
                try { if (Build.VERSION.SDK_INT >= 29) startForeground(77, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC); else startForeground(77, n); } catch (Exception e2) { stopSelf(); }
            }
            return START_NOT_STICKY;
        }
    }

    // ==================== ХОСТ ====================

    // ===== VPN-туннель: друг приходит в Minecraft как «другое устройство» 10.77.0.x =====
    private volatile boolean vpnDenied, vpnAsked;
    private volatile boolean useVpn;
    private volatile boolean tunReady;
    private volatile FileInputStream tunIn;
    private volatile FileOutputStream tunOut;
    private final InetSocketAddress[] vipPeers = new InetSocketAddress[256];
    private final ConcurrentHashMap<String, Integer> vipByKey = new ConcurrentHashMap<>();
    private int vipNext = 2;
    private volatile int tunWritten, tunRead, tunErr, tunBig, tunOther;
    private volatile long firstGuestAt;
    private volatile boolean warnedSilent;

    // ===== Журнал событий =====
    private final ArrayDeque<String> logBuf = new ArrayDeque<>();
    private final java.text.SimpleDateFormat logFmt = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US);
    private final ArrayList<TextView> logViews = new ArrayList<>();

    private volatile File logFile;

    /** Дописывает строку в файл журнала (чтобы после краша было что прочитать). */
    private void writeLogFile(String line) {
        File f = logFile;
        if (f == null) return;
        synchronized (this) {
            try (FileOutputStream o = new FileOutputStream(f, true)) {
                o.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) { }
        }
    }

    /** Последние n строк файла журнала. */
    private String readLogTail(int n) {
        File f = logFile;
        if (f == null || !f.exists()) return "(журнала нет)";
        try {
            byte[] b = new byte[(int) Math.min(f.length(), 64 * 1024)];
            try (FileInputStream in = new FileInputStream(f)) {
                in.skip(Math.max(0, f.length() - b.length));
                int got = 0;
                while (got < b.length) { int r = in.read(b, got, b.length - got); if (r < 0) break; got += r; }
            }
            String[] ls = new String(b, StandardCharsets.UTF_8).split("\n");
            StringBuilder sb = new StringBuilder();
            for (int i = Math.max(0, ls.length - n); i < ls.length; i++) sb.append(ls[i].length() > 200 ? ls[i].substring(0, 200) : ls[i]).append('\n');
            return sb.toString();
        } catch (Exception e) { return "(не удалось прочитать журнал: " + e.getMessage() + ")"; }
    }

    private void log(String msg) {
        Log.i(TAG, msg);
        String line = logFmt.format(new java.util.Date()) + " " + msg;
        synchronized (logBuf) {
            logBuf.addLast(line);
            while (logBuf.size() > 80) logBuf.removeFirst();
        }
        writeLogFile(line);
        pushLog();
    }

    private String logText() {
        synchronized (logBuf) {
            StringBuilder sb = new StringBuilder();
            for (String l : logBuf) sb.append(l).append('\n');
            return sb.toString();
        }
    }

    private void pushLog() {
        ui.post(() -> { String t = logText(); for (TextView v : logViews) v.setText(t); });
    }

    private void copyLog() {
        try {
            ClipboardManager cm = (ClipboardManager) appCtx.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("arcade_log", status + "\n\n" + logText()));
            toast("Журнал скопирован");
        } catch (Exception e) { toast("Не удалось скопировать"); }
    }

    private final java.util.Set<String> foreignSeen = java.util.Collections.synchronizedSet(new java.util.HashSet<String>());
    private volatile int peerMismatch;
    private volatile int pingsSeen;
    private volatile boolean worldFound;
    private volatile String probeInfo = "";
    private volatile int hostPort = MC_PORT;
    private volatile String localIp = "", publicIp = "";

    private String localIpv4() {
        try {
            Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
            while (en != null && en.hasMoreElements()) {
                NetworkInterface ni = en.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses()))
                    if (a instanceof Inet4Address && !a.isLoopbackAddress() && a.isSiteLocalAddress()) return a.getHostAddress();
            }
        } catch (Exception ignored) { }
        return "";
    }

    // ==================== Встроенный сервер (PocketMine-MP) ====================
    // Нужны два файла, которые я не могу вам дать:
    //   app/src/main/jniLibs/arm64-v8a/libphp.so      — PHP 8 для Android arm64 (с расширениями PocketMine)
    //   app/src/main/assets/PocketMine-MP.phar        — сервер PocketMine-MP под вашу версию игры

    private static final String PHAR_ASSET = "PocketMine-MP.phar";
    private volatile boolean builtin;
    private volatile boolean externalSrv;
    private volatile Process srvProc;
    private final ArrayDeque<String> srvTail = new ArrayDeque<>();

    // Версия игры > версия PocketMine-MP (по официальным спискам изменений). Один сервер = одна версия игры.
    private static final String[][] SRV_VERSIONS = {
            {"1.21.40", "5.20.0"},
            {"1.21.50 / 1.21.51", "5.22.0"},
            {"1.21.70", "5.27.0"},
            {"1.21.93", "5.31.0"},
            {"1.21.120", "5.37.0"},
    };
    private static final String[] GAMEMODES = {"survival", "creative"};
    private static final String[] GAMEMODE_NAMES = {"Выживание", "Творчество"};
    private volatile File curPhar;
    private final ArrayDeque<String> srvOut = new ArrayDeque<>();
    private volatile boolean consoleDirty;
    private LinearLayout consoleBox;
    private TextView consoleView;
    private ScrollView consoleScroll;
    private EditText consoleInput;

    private int verIdx() { int i = prefs.getInt("srv_ver", 1); return (i < 0 || i >= SRV_VERSIONS.length) ? 1 : i; }
    private String verTag() { return SRV_VERSIONS[verIdx()][1]; }
    private File dataDir() { File d = new File(srvDir(), "data-" + verTag()); d.mkdirs(); return d; }

    private void scheduleConsole() {
        if (consoleDirty) return;
        consoleDirty = true;
        ui.postDelayed(() -> {
            consoleDirty = false;
            if (consoleView == null) return;
            StringBuilder sb = new StringBuilder();
            synchronized (srvOut) { for (String l : srvOut) sb.append(l).append('\n'); }
            consoleView.setText(sb.toString());
            if (consoleScroll != null) consoleScroll.post(() -> consoleScroll.fullScroll(View.FOCUS_DOWN));
        }, 300);
    }

    private void consoleAdd(String l) {
        synchronized (srvOut) { srvOut.addLast(l); while (srvOut.size() > 300) srvOut.removeFirst(); }
        scheduleConsole();
    }

    private void sendConsole(final String cmd) {
        final Process p = srvProc;
        if (p == null || !alive(p)) { toast("Сервер не запущен"); return; }
        consoleAdd("> " + cmd);
        new Thread(() -> {
            try { p.getOutputStream().write((cmd + "\n").getBytes(StandardCharsets.UTF_8)); p.getOutputStream().flush(); }
            catch (Exception e) { log("Ошибка: Не удалось отправить команду: " + e.getMessage()); }
        }, "mp-srv-cmd").start();
    }

    private File srvDir() { File d = new File(getFilesDir(), "pmmp"); d.mkdirs(); return d; }
    /** Если в jniLibs лежит libphp.so — берём его, иначе PHP, скачанный самим приложением. */
    private File phpBin() {
        File lib = new File(getApplicationInfo().nativeLibraryDir, "libphp.so");
        return lib.exists() ? lib : new File(srvDir(), "php/bin/php7/bin/php");
    }

    /** Распаковывает .tar.gz в папку (без внешних библиотек). Возвращает число файлов. */
    private int untarGz(File tgz, File dest) throws IOException {
        int files = 0;
        String destPath = dest.getCanonicalPath() + File.separator;
        try (InputStream in = new java.util.zip.GZIPInputStream(new java.io.BufferedInputStream(new FileInputStream(tgz), 65536))) {
            byte[] h = new byte[512];
            String longName = null;
            while (readFully(in, h, 512)) {
                if (h[0] == 0) break;
                String name = tarStr(h, 0, 100);
                String prefix = tarStr(h, 345, 155);
                if (!prefix.isEmpty()) name = prefix + "/" + name;
                long size = 0;
                try { size = Long.parseLong(tarStr(h, 124, 12).trim(), 8); } catch (Exception ignored) { }
                char type = (char) (h[156] == 0 ? '0' : h[156]);
                int mode = 0;
                try { mode = Integer.parseInt(tarStr(h, 100, 8).trim(), 8); } catch (Exception ignored) { }
                long pad = (512 - size % 512) % 512;
                if (type == 'L') { // длинное имя GNU
                    byte[] nb = new byte[(int) size];
                    readFully(in, nb, nb.length);
                    longName = new String(nb, StandardCharsets.UTF_8).trim();
                    skipFully(in, pad);
                    continue;
                }
                if (longName != null) { name = longName; longName = null; }
                File out = new File(dest, name);
                if (!out.getCanonicalPath().startsWith(destPath) && !out.getCanonicalPath().equals(dest.getCanonicalPath()))
                    throw new IOException("небезопасный путь в архиве: " + name);
                if (type == '5') { out.mkdirs(); skipFully(in, size + pad); }
                else if (type == '0' || type == '7') {
                    out.getParentFile().mkdirs();
                    try (FileOutputStream fo = new FileOutputStream(out)) {
                        byte[] b = new byte[65536];
                        long left = size;
                        while (left > 0) {
                            int n = in.read(b, 0, (int) Math.min(b.length, left));
                            if (n < 0) throw new IOException("архив оборван");
                            fo.write(b, 0, n);
                            left -= n;
                        }
                    }
                    if ((mode & 0111) != 0) out.setExecutable(true, false);
                    skipFully(in, pad);
                    files++;
                } else skipFully(in, size + pad); // ссылки и прочее пропускаем
            }
        }
        return files;
    }

    private static String tarStr(byte[] b, int off, int len) {
        int e = off;
        while (e < off + len && b[e] != 0) e++;
        return new String(b, off, e - off, StandardCharsets.UTF_8);
    }

    private static boolean readFully(InputStream in, byte[] b, int len) throws IOException {
        int got = 0;
        while (got < len) { int n = in.read(b, got, len - got); if (n < 0) return false; got += n; }
        return true;
    }

    private static void skipFully(InputStream in, long n) throws IOException {
        byte[] b = new byte[8192];
        while (n > 0) { int r = in.read(b, 0, (int) Math.min(b.length, n)); if (r < 0) throw new IOException("архив оборван"); n -= r; }
    }

    /** Ставит PHP (статическая сборка pmmp для Android arm64). Возвращает текст ошибки или null. */
    private String ensurePhp() {
        File php = phpBin();
        if (php.exists()) { php.setExecutable(true, false); return null; }
        if (Build.SUPPORTED_ABIS.length == 0 || !Build.SUPPORTED_ABIS[0].contains("arm64"))
            return "Встроенный сервер работает только на arm64, у вас: " + (Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "?");
        File tgz = new File(srvDir(), "php.tar.gz");
        String[] vers = {"8.2", "8.3"};
        String lastErr = "";
        for (String v : vers) {
            String url = "https://github.com/pmmp/PHP-Binaries/releases/download/pm5-php-" + v + "-latest/PHP-" + v + "-Android-arm64-PM5.tar.gz";
            setStatus("Скачиваю PHP " + v + " для телефона (один раз, ~30 МБ)…");
            log("Скачиваю PHP: " + url);
            String err = downloadFile(url, tgz, 1L << 20);
            if (err != null) { lastErr = err; log("Ошибка: PHP " + v + ": " + err); continue; }
            setStatus("Распаковываю PHP…");
            try {
                File dest = new File(srvDir(), "php");
                deleteRec(dest);
                dest.mkdirs();
                int n = untarGz(tgz, dest);
                tgz.delete();
                log("PHP распакован (" + n + " файлов)");
            } catch (Exception e) { lastErr = "распаковка: " + e.getMessage(); log("Ошибка: " + lastErr); continue; }
            php = phpBin();
            if (!php.exists()) { lastErr = "в архиве нет bin/php7/bin/php"; log("Ошибка: " + lastErr); continue; }
            php.setExecutable(true, false);
            return null;
        }
        return "Не удалось установить PHP: " + lastErr + "\nПроверьте интернет. Либо положите свой libphp.so в jniLibs/arm64-v8a/.";
    }

    private String srvTailText() {
        synchronized (srvTail) { StringBuilder sb = new StringBuilder(); for (String l : srvTail) sb.append(l).append('\n'); return sb.toString(); }
    }

    /** Скачивает файл по https; возвращает текст ошибки или null. */
    private String downloadFile(String url, File dest) { return downloadFile(url, dest, 1L << 20); }

    private String downloadFile(String url, File dest, long minSize) {
        File part = new File(dest.getPath() + ".part");
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setInstanceFollowRedirects(true);
            int code = c.getResponseCode();
            if (code != 200) return "HTTP " + code + " (" + url + ")";
            long total = c.getContentLengthLong();
            long got = 0, nextLog = 5L << 20;
            try (InputStream in = c.getInputStream(); java.io.FileOutputStream out = new java.io.FileOutputStream(part)) {
                byte[] b = new byte[65536];
                int n;
                while ((n = in.read(b)) > 0) {
                    if (stopFlag) return "остановлено";
                    out.write(b, 0, n);
                    got += n;
                    if (got >= nextLog) { nextLog += 5L << 20; log("Скачано " + (got >> 20) + " МБ" + (total > 0 ? " из " + (total >> 20) : "")); }
                }
            }
            if (part.length() < minSize) return "файл слишком маленький (" + part.length() + " байт) — это не сервер";
            if (dest.exists()) dest.delete();
            if (!part.renameTo(dest)) return "не удалось сохранить файл";
            return null;
        } catch (Exception e) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        } finally {
            if (c != null) c.disconnect();
            if (part.exists() && !dest.exists()) part.delete();
        }
    }

    private static final String PATCH_PHP = "<?php\n"
                + "// Ослабляет проверку ника в PocketMine: разрешает любые символы (кириллица, #, точка) и длину до 64 байт.\n"
                + "$f = $argv[1];\n"
                + "try {\n"
                + "    $p = new Phar($f);\n"
                + "    $k = 'src/player/Player.php';\n"
                + "    if (!isset($p[$k])) { echo \"NOFILE\\n\"; exit(1); }\n"
                + "    $c = file_get_contents('phar://' . $f . '/' . $k);\n"
                + "    $n = preg_replace('/\\$len <= 16 && preg_match\\(\"\\/\\[\\^A-Za-z0-9_ \\]\\/\", \\$name\\) === 0/', '$len <= 64', $c, 1, $cnt);\n"
                + "    if ($cnt !== 1) { echo \"NOMATCH\\n\"; exit(2); }\n"
                + "    $p[$k] = $n;\n"
                + "    unset($p);\n"
                + "    echo \"PATCHED\\n\";\n"
                + "} catch (Throwable $e) { echo \"ERR \" . $e->getMessage() . \"\\n\"; exit(3); }\n";

    /** Ослабляет в сервере проверку ника (иначе «Invalid name» для ников с кириллицей, # и т.п.). Возвращает ошибку или null. */
    private String patchPharNames(File phar) {
        File mark = new File(phar.getPath() + ".namefix");
        if (mark.exists()) return null;
        try {
            File script = new File(srvDir(), "patch.php");
            try (FileOutputStream o = new FileOutputStream(script)) { o.write(PATCH_PHP.getBytes(StandardCharsets.UTF_8)); }
            ProcessBuilder pb = new ProcessBuilder(phpBin().getAbsolutePath(), "-d", "phar.readonly=0", "-d", "memory_limit=1024M",
                    script.getAbsolutePath(), phar.getAbsolutePath());
            pb.redirectErrorStream(true);
            pb.environment().put("TMPDIR", getCacheDir().getAbsolutePath());
            Process pr = pb.start();
            String out = readStream(pr.getInputStream()).trim();
            pr.waitFor();
            if (!out.contains("PATCHED")) return out.isEmpty() ? "нет ответа от PHP" : out;
            new FileOutputStream(mark).close();
            log("Ограничение на ники в сервере снято");
            return null;
        } catch (Exception e) { return e.getClass().getSimpleName() + ": " + e.getMessage(); }
    }

    /** Возвращает текст ошибки или null, если всё готово. */
    private String prepareServerFiles() {
        String phpErr = ensurePhp();
        if (phpErr != null) return phpErr;
        final String tag = verTag();
        final String gameVer = SRV_VERSIONS[verIdx()][0];
        File phar = new File(srvDir(), "PocketMine-MP-" + tag + ".phar");
        if (!phar.exists()) {
            String url = "https://github.com/pmmp/PocketMine-MP/releases/download/" + tag + "/PocketMine-MP.phar";
            setStatus("Скачиваю сервер для Minecraft " + gameVer + " (PocketMine-MP " + tag + ")…");
            log("Скачиваю " + url);
            String dl = downloadFile(url, phar);
            if (dl != null) {
                log("Ошибка: Не удалось скачать сервер: " + dl);
                try (InputStream in = getAssets().open(PHAR_ASSET); java.io.FileOutputStream out = new java.io.FileOutputStream(phar)) {
                    log("Беру запасной сервер из ресурсов приложения (assets/" + PHAR_ASSET + ") — его версия может не совпасть с выбранной!");
                    byte[] b = new byte[65536]; int n;
                    while ((n = in.read(b)) > 0) out.write(b, 0, n);
                } catch (IOException e) {
                    phar.delete();
                    return "Не удалось получить сервер для " + gameVer + ".\nСкачивание: " + dl + "\nЗапасного файла в assets нет.\nПроверьте интернет или положите PocketMine-MP.phar в app/src/main/assets/.";
                }
            } else log("Сервер скачан: PocketMine-MP " + tag);
        }
        curPhar = phar;
        String pe = patchPharNames(phar);
        if (pe != null) log("Внимание: Не удалось снять ограничение на ники: " + pe + ". Ники с кириллицей, # или длиннее 16 символов сервер не пустит.");
        try {
            int gm = prefs.getInt("srv_gm", 0) % GAMEMODES.length;
            String world = prefs.getString("srv_world", "world");
            String props = "motd=" + sp("motd", "Arcade") + "\nserver-port=" + SRV_PORT + "\nserver-portv6=" + (SRV_PORT + 1) + "\nenable-ipv6=off\n"
                    + "gamemode=" + sp("gamemode", GAMEMODES[gm]) + "\ndifficulty=" + sp("difficulty", "normal") + "\nmax-players=" + sp("max-players", "10") + "\n"
                    + "pvp=" + sp("pvp", "on") + "\nwhite-list=" + sp("white-list", "off") + "\nview-distance=" + sp("view-distance", "8") + "\n"
                    + "level-seed=" + sp("level-seed", "") + "\nxbox-auth=off\nenable-query=off\nlevel-name=" + world + "\n";
            java.io.FileOutputStream o = new java.io.FileOutputStream(new File(dataDir(), "server.properties"));
            o.write(props.getBytes(StandardCharsets.UTF_8));
            o.close();
        } catch (Exception e) { return "Не удалось записать server.properties: " + e.getMessage(); }
        return null;
    }

    private boolean pingLocal(int port) {
        DatagramSocket s = null;
        try {
            s = new DatagramSocket();
            s.setSoTimeout(800);
            byte[] ping = makePing();
            s.send(new DatagramPacket(ping, ping.length, InetAddress.getByName("127.0.0.1"), port));
            byte[] r = new byte[2048];
            DatagramPacket rp = new DatagramPacket(r, r.length);
            s.receive(rp);
            return rp.getLength() > 0 && (r[0] & 0xff) == 0x1c;
        } catch (Exception e) { return false; }
        finally { if (s != null) s.close(); }
    }

    private static boolean alive(Process p) {
        try { p.exitValue(); return false; } catch (IllegalThreadStateException e) { return true; }
    }

    /** Запускает PocketMine-MP и ждёт, пока он ответит на пинг. Возвращает false (и останавливает сессию) при ошибке. */
    private boolean srvAlive() { Process p = srvProc; return p != null && alive(p); }

    /** Для комнаты: если сервер уже включён кнопкой — просто используем его. */
    private boolean startBuiltinServer() throws InterruptedException {
        if (srvAlive()) { log("Встроенный сервер уже запущен — использую его"); return true; }
        String err = bootServer();
        if (err != null) { stopSession(err); return false; }
        return true;
    }

    /** Запускает PocketMine-MP и ждёт ответа на пинг. Возвращает текст ошибки или null. */
    private String bootServer() throws InterruptedException {
        String err = prepareServerFiles();
        if (err != null) return err;
        setStatus("Запускаю встроенный сервер…");
        final Process p;
        try {
            File dir = dataDir();
            new File(dir, "plugins").mkdirs();
            synchronized (srvOut) { srvOut.clear(); }
            consoleAdd("— Minecraft " + SRV_VERSIONS[verIdx()][0] + " · PocketMine-MP " + verTag() + " —");
            ProcessBuilder pb = new ProcessBuilder(phpBin().getAbsolutePath(), curPhar.getAbsolutePath(),
                    "--no-wizard", "--disable-ansi", "--data=" + dir.getAbsolutePath(), "--plugins=" + new File(dir, "plugins").getAbsolutePath());
            pb.directory(dir);
            pb.redirectErrorStream(true);
            pb.environment().put("HOME", dir.getAbsolutePath());
            pb.environment().put("TMPDIR", getCacheDir().getAbsolutePath());
            p = pb.start();
            srvProc = p;
            log("Процесс сервера запущен: " + phpBin().getName());
        } catch (Exception e) {
            String hint = String.valueOf(e.getMessage()).contains("denied") ? (getApplicationInfo().targetSdkVersion > 28
                    ? "\nПричина: Android запрещает запуск скачанных программ при targetSdkVersion " + getApplicationInfo().targetSdkVersion + ". В app/build.gradle поставьте targetSdkVersion 28 (targetSdk 28) и пересоберите приложение."
                    : "\nПодсказка: файл не имеет права на запуск. Очистите данные приложения и попробуйте снова.") : "";
            return "Не удалось запустить сервер: " + e.getMessage() + hint;
        }
        Thread rd = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String l = line.length() > 160 ? line.substring(0, 160) : line;
                    synchronized (srvTail) { srvTail.addLast(l); while (srvTail.size() > 10) srvTail.removeFirst(); }
                    consoleAdd(l);
                    String lo = l.toLowerCase(java.util.Locale.ROOT);
                    if (lo.contains("error") || lo.contains("critical") || lo.contains("fatal") || lo.contains("exception") || lo.contains("done (") || lo.contains("outdated") || lo.contains("unsupported"))
                        log("[сервер] " + l);
                }
            } catch (Exception ignored) { }
            consoleAdd("— сервер остановлен —");
            pushText();
        }, "mp-srv-out");
        rd.setDaemon(true);
        rd.start();

        long end = SystemClock.elapsedRealtime() + 150000;
        while (!stopFlag && SystemClock.elapsedRealtime() < end) {
            if (srvProc != p) return "Остановлено";
            if (!alive(p)) {
                Thread.sleep(300);
                return "Сервер завершился (код " + p.exitValue() + "). Последние строки:\n" + srvTailText();
            }
            if (pingLocal(SRV_PORT)) { log("Встроенный сервер запущен и отвечает на порту " + SRV_PORT); return null; }
            Thread.sleep(1000);
        }
        return stopFlag ? "Остановлено" : "Сервер не ответил за 150 с. Последние строки:\n" + srvTailText();
    }


    private void stopBuiltinServer() {
        stopOwnerRelay();
        final Process p = srvProc;
        srvProc = null;
        if (p == null) return;
        new Thread(() -> {
            try { p.getOutputStream().write("stop\n".getBytes(StandardCharsets.UTF_8)); p.getOutputStream().flush(); } catch (Exception ignored) { }
            try { if (!p.waitFor(5, TimeUnit.SECONDS)) p.destroyForcibly(); } catch (Exception e) { p.destroyForcibly(); }
        }, "mp-srv-stop").start();
    }

    // ===== Настройки сервера, плагины, ресурспаки =====
    private static final String[][] SP_ENUMS = {
            {"gamemode", "Режим игры", "survival", "creative", "adventure", "spectator"},
            {"difficulty", "Сложность", "peaceful", "easy", "normal", "hard"},
            {"pvp", "PvP между игроками", "on", "off"},
            {"white-list", "Белый список", "off", "on"},
    };
    private static final String[][] SP_TEXTS = {
            {"motd", "Название сервера (видно в «Друзья»)", "Arcade"},
            {"max-players", "Максимум игроков (1–100)", "10"},
            {"view-distance", "Дальность прорисовки, чанков (1–32)", "8"},
            {"level-seed", "Сид мира (пусто = случайный)", ""},
    };

    /** Значение настройки сервера из SharedPreferences (без переводов строк, чтобы не сломать server.properties). */
    private String sp(String key, String def) {
        return prefs.getString("sp_" + key, def).replace("\n", " ").replace("\r", " ").trim();
    }

    private EditText settingsField(String text, boolean number) {
        EditText et = new EditText(this);
        et.setText(text);
        et.setTextColor(C_TEXT);
        et.setHintTextColor(C_MUTED);
        et.setTextSize(13);
        et.setSingleLine(true);
        if (number) et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setBackground(rect(C_CARD2, 10, C_STROKE, 1));
        et.setPadding(dp(12), dp(10), dp(12), dp(10));
        return et;
    }

    private void pickFile(int rc) {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            relay(i, rc);
        } catch (Exception e) { toast("Не открылся выбор файла: " + e.getMessage()); }
    }

    private String displayName(Uri u) {
        try (android.database.Cursor c = getContentResolver().query(u, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (i >= 0) return c.getString(i);
            }
        } catch (Exception ignored) { }
        String last = u.getLastPathSegment();
        return last == null ? "file" : last;
    }

    /** Копирует плагин (.phar) в plugins/ или ресурспак (.mcpack/.zip) в resource_packs/ сервера. */
    private void installFile(Uri uri, boolean plugin) {
        try {
            String name = displayName(uri).replaceAll("[^A-Za-z0-9._\\- ]", "_");
            String lo = name.toLowerCase(Locale.ROOT);
            if (plugin) {
                if (!lo.endsWith(".phar")) { toast("Плагин должен быть файлом .phar"); return; }
            } else {
                if (lo.endsWith(".mcpack")) name = name.substring(0, name.length() - 7) + ".zip";
                else if (!lo.endsWith(".zip")) { toast("Нужен ресурспак .mcpack или .zip"); return; }
            }
            File dir = new File(dataDir(), plugin ? "plugins" : "resource_packs");
            dir.mkdirs();
            File out = new File(dir, name);
            try (InputStream in = getContentResolver().openInputStream(uri); FileOutputStream fo = new FileOutputStream(out)) {
                byte[] b = new byte[65536];
                int n;
                while ((n = in.read(b)) > 0) fo.write(b, 0, n);
            }
            if (!plugin) writeResourcePacksYml();
            log("" + (plugin ? "Плагин" : "Ресурспак") + " добавлен: " + name + (srvAlive() ? " (применится после перезапуска сервера)" : ""));
            toast((plugin ? "Плагин добавлен: " : "Ресурспак добавлен: ") + name);
        } catch (Exception e) {
            log("Ошибка: Не удалось добавить файл: " + e.getClass().getSimpleName() + " " + e.getMessage());
            toast("Ошибка: " + e.getMessage());
        }
    }

    /** Перечисляет все паки из resource_packs/ в resource_packs.yml (формат PocketMine-MP 5). */
    private void writeResourcePacksYml() throws IOException {
        File[] fs = new File(dataDir(), "resource_packs").listFiles();
        StringBuilder sb = new StringBuilder("force_resources: false\n");
        int n = 0;
        if (fs != null) {
            Arrays.sort(fs);
            for (File f : fs) {
                String lo = f.getName().toLowerCase(Locale.ROOT);
                if (f.isFile() && (lo.endsWith(".zip") || lo.endsWith(".mcpack"))) { if (n++ == 0) sb.append("resource_stack:\n"); sb.append("- \"").append(f.getName()).append("\"\n"); }
            }
        }
        if (n == 0) sb.append("resource_stack: []\n");
        try (FileOutputStream o = new FileOutputStream(new File(dataDir(), "resource_packs.yml"))) { o.write(sb.toString().getBytes(StandardCharsets.UTF_8)); }
    }

    private void pickWorld() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            relay(i, RC_PICK);
        } catch (Exception e) { toast("Не открылся выбор файла: " + e.getMessage()); }
    }

    private static void deleteRec(File f) {
        File[] ch = f.listFiles();
        if (ch != null) for (File c : ch) deleteRec(c);
        f.delete();
    }

    /** Распаковывает .mcworld в папку сервера worlds/world. */
    private void importWorld(Uri uri) {
        try {
            File root = new File(dataDir(), "worlds");
            File dest = new File(root, "world");
            deleteRec(dest);
            dest.mkdirs();
            String destPath = dest.getCanonicalPath() + File.separator;
            int files = 0;
            boolean hasLevel = false;
            try (InputStream in = getContentResolver().openInputStream(uri); ZipInputStream zin = new ZipInputStream(in)) {
                ZipEntry e;
                byte[] b = new byte[65536];
                while ((e = zin.getNextEntry()) != null) {
                    File out = new File(dest, e.getName());
                    if (!out.getCanonicalPath().startsWith(destPath)) throw new IOException("небезопасный путь в архиве: " + e.getName());
                    if (e.isDirectory()) { out.mkdirs(); continue; }
                    out.getParentFile().mkdirs();
                    try (java.io.FileOutputStream fo = new java.io.FileOutputStream(out)) {
                        int n;
                        while ((n = zin.read(b)) > 0) fo.write(b, 0, n);
                    }
                    files++;
                    if (e.getName().equals("level.dat")) hasLevel = true;
                }
            }
            prefs.edit().putString("srv_world", "world").apply();
            if (files == 0) log("Ошибка: Архив пустой или это не .mcworld");
            else if (!hasLevel) log("Внимание: Мир распакован (" + files + " файлов), но level.dat не в корне архива — сервер может не найти мир");
            else log("Мир импортирован в сервер версии " + SRV_VERSIONS[verIdx()][0] + ": " + files + " файлов. Он загрузится при следующем запуске");
            toast("Мир импортирован");
        } catch (Exception e) {
            log("Ошибка: Не удалось импортировать мир: " + e.getClass().getSimpleName() + " " + e.getMessage());
            toast("Ошибка импорта мира");
        }
    }

    private String hostStatus() {
        InetSocketAddress ht = hostTarget;
        int port = ht != null ? ht.getPort() : hostPort;
        StringBuilder sb = new StringBuilder("Код создан — отправьте другу.\n");
        sb.append(pubLine());
        sb.append("Агент playit: ").append(agentState).append("\n");
        if (builtin) {
            sb.append("Встроенный сервер работает").append("\nСвой вход: Minecraft > Друзья > «Arcade» (или Серверы > 127.0.0.1 : " + MC_PORT + ")\nПрямой вход в обход «Друзей»: Серверы > 127.0.0.1 : " + SRV_PORT);
            int bn = hostPeers.size();
            if (bn > 0) sb.append("\nПодключено игроков: ").append(bn).append(" · пакетов от друга: ").append(cntFromGuest).append(", ответов сервера: ").append(cntFromWorld);
            return sb.toString();
        }
        if (worldFound) sb.append("Мир найден");
        else sb.append("Внимание: Ваш мир не отвечает на порту ").append(port).append(". Скорее всего, в настройках мира выключено «Виден игрокам в локальной сети» (или мир открыт только для друзей Xbox). Включите — приложение подхватит само за пару секунд.\n").append(probeInfo)
                .append(nethernetSuspected ? "\n\nВнимание: Minecraft занял UDP-порт 7551 — это NetherNet (протокол Xbox/WebRTC). Мир на этой версии, вероятно, не принимает обычные подключения на 19132, и через наш туннель его не передать. Используйте «СОЗДАТЬ НА ВСТРОЕННОМ СЕРВЕРЕ» или приглашайте друзей средствами самого Minecraft (через Xbox)." : "");
        int n = hostPeers.size();
        if (n > 0) sb.append("\nПодключено игроков: ").append(n).append(" · пакетов от друга: ").append(cntFromGuest).append(", ответов мира: ").append(cntFromWorld);
        if (useVpn) sb.append("\nVPN: ").append(tunReady ? "включён (записано " + tunWritten + ", получено " + tunRead + ")" : (vpnDenied ? "нет разрешения" : "не создан"));
        if (!localIp.isEmpty()) sb.append("\nТа же Wi-Fi сеть: Серверы > Добавить > ").append(localIp).append(" : ").append(port);
        if (!publicIp.isEmpty()) sb.append("\nВнешний IP: ").append(publicIp).append(" (нужен проброс UDP ").append(port).append(")");
        return sb.toString();
    }

    private void hostLoop() {
        try {
            worldFound = false;
            publicIp = "";
            localIp = localIpv4();
            // Minecraft раздаёт LAN-мир на адресе Wi-Fi; на 127.0.0.1 он может не слушать
            hostTarget = new InetSocketAddress(localIp.isEmpty() ? "127.0.0.1" : localIp, hostPort);
            setStatus("Создаю сервер…");
            log("Хост: адрес Wi-Fi " + (localIp.isEmpty() ? "не найден" : localIp) + ", порт мира " + hostPort);
            if (builtin) {
                { if (!startBuiltinServer()) return; hostPort = SRV_PORT; if (!externalSrv) startOwnerRelay(); }
                hostTarget = new InetSocketAddress("127.0.0.1", hostPort);
                worldFound = true;
            } else setupTun();
            // Пакеты друзей агент playit отдаёт на локальный порт 127.0.0.1:tunnelPort — это наш форвардер.
            // Он работает и для мира из Minecraft (сам найдёт, где слушает мир), и для встроенного сервера.
            final int tunnelPort = plLocalPort();
            final DatagramSocket fs = openFwd(tunnelPort);
            if (fs == null) { stopSession("Локальный порт " + tunnelPort + " занят. Смените «Локальный порт туннеля» (и порт туннеля в панели playit)."); return; }
            fwdSock = fs;
            Thread ft = new Thread(() -> fwdLoop(fs), "mp-fwd");
            ft.setDaemon(true);
            ft.start();
            log("Форвардер пакетов: 127.0.0.1:" + tunnelPort + " (в панели playit туннель должен вести именно сюда)");
            String rerr = startAgent();
            if (rerr != null) { stopSession(rerr); return; }
            log("playit: публичный адрес " + publicAddr + " > код " + pretty(code));
            setStatus(hostStatus());
            if (builtin) { log("Ваш вход на сервер: Minecraft > Друзья > «Arcade» (или Серверы > Добавить > 127.0.0.1, порт " + MC_PORT + ")"); ui.post(this::launchMinecraft); }

            // поиск мира в фоне: когда найдём — обновим порт пересылки
            Thread pr = new Thread(() -> {
                try {
                    while (!stopFlag && !worldFound) {
                        if (probeWorld()) worldFound = true; else Thread.sleep(3000);
                    }
                } catch (Exception ignored) { }
            }, "mp-probe");
            pr.setDaemon(true);
            pr.start();

            // внешний IP (для прямого подключения с пробросом порта)
            Thread ip = new Thread(() -> {
                try {
                    HttpURLConnection h = (HttpURLConnection) new URL("https://api.ipify.org").openConnection();
                    h.setConnectTimeout(4000);
                    h.setReadTimeout(4000);
                    if (h.getResponseCode() == 200) publicIp = readStream(h.getInputStream()).trim();
                } catch (Exception ignored) { }
            }, "mp-ip");
            ip.setDaemon(true);
            ip.start();

            long lastClean = SystemClock.elapsedRealtime();
            while (!stopFlag) {
                Thread.sleep(1000);
                long n = SystemClock.elapsedRealtime();
                Process rp = rtProc;
                if (rp != null && !alive(rp)) { Thread.sleep(300); stopSession("Агент playit остановился (код " + rp.exitValue() + "):\n" + rtTailText()); return; }
                if (n - lastClean > 10000) {
                    lastClean = n;
                    for (Map.Entry<String, HostPeer> e : hostPeers.entrySet()) {
                        if (n - e.getValue().last > 120000) { try { e.getValue().sock.close(); } catch (Exception ignored) { } hostPeers.remove(e.getKey()); }
                    }
                }
                if (cntFromGuest > 0 && firstGuestAt == 0) firstGuestAt = n;
                if (!warnedSilent && !builtin && cntFromGuest > 0 && cntFromWorld == 0 && n - firstGuestAt > 12000) {
                    warnedSilent = true;
                    log("Внимание: Друг шлёт пакеты (" + cntFromGuest + "), но ваш мир не отвечает ни на один путь. Это значит, что мир, скорее всего, не слушает порт " + hostTarget.getPort()
                            + ". Проверьте в настройках мира: «Мультиплеер» ВКЛ и «Виден игрокам в локальной сети» ВКЛ. Пока проверка не найдёт мир, вход друга невозможен.");
                }
                String hs = hostStatus();
                if (!hs.equals(status)) setStatus(hs);
            }
        } catch (InterruptedException ignored) {
        } catch (Exception e) {
            Log.e(TAG, "host", e);
            if (!stopFlag) stopSession("Ошибка: " + e.getMessage());
        }
    }

    private static String modeName(int m) { return m == 0 ? "VPN 10.77.0.x" : (m == 1 ? "адрес Wi-Fi" : "127.0.0.1"); }

    private void hostOnData(InetSocketAddress peer, byte[] d, int len) {
        String k = peer.getAddress().getHostAddress() + ":" + peer.getPort();
        HostPeer hp = hostPeers.get(k);
        if (hp == null && !looksLikeRakStart(d, len)) {
            // из интернета в туннель постоянно летит мусор от сканеров; настоящий клиент начинает с RakNet-пинга или запроса соединения
            if (junkDropped++ < 5) log("Отброшен посторонний пакет из туннеля от " + k + " (" + len + " байт" + (len > 0 ? ", id 0x" + Integer.toHexString(d[0] & 0xff) : "") + ")");
            return;
        }
        if (hp == null) {
            try {
                hp = new HostPeer(peer, builtin);
                hostPeers.put(k, hp);
                final HostPeer fp = hp;
                Thread r = new Thread(() -> hostReader(fp), "mp-peer");
                r.setDaemon(true);
                r.start();
                log("Новое подключение от друга: " + k);
            } catch (Exception e) { log("Не удалось завести сокет для друга: " + e.getMessage()); return; }
        }
        hp.last = SystemClock.elapsedRealtime();
        cntFromGuest++;
        if (cntFromGuest <= 12 && len > 0) log("← от " + k + ": " + len + " байт, id 0x" + Integer.toHexString(d[0] & 0xff));
        hp.sent++;
        // пути: 1 = адрес, где найден мир / Wi-Fi, 2 = 127.0.0.1, 0 = VPN (если включён). Для встроенного сервера путь один
        final int[] order = tunReady ? new int[]{1, 2, 0} : new int[]{1, 2};
        if (!builtin && hp.recv == 0 && hp.sent % 8 == 0) {
            hp.idx = (hp.idx + 1) % order.length;
            log("Мир молчит на этом пути, пробую: " + modeName(order[hp.idx]));
        }
        int mode = builtin ? 2 : order[hp.idx % order.length];
        if (mode == 0) { writeTun(peer, d, len); return; }
        InetSocketAddress ht = hostTarget;
        InetSocketAddress tgt = mode == 1 ? ht : new InetSocketAddress("127.0.0.1", ht.getPort());
        try { hp.sock.send(new DatagramPacket(d, len, tgt)); } catch (Exception e) { if (hp.sent < 3) log("Ошибка отправки в мир (" + modeName(mode) + "): " + e.getMessage()); }
    }

    private volatile int junkDropped;

    /** true, если пакет похож на начало RakNet-сессии: пинг (0x01/0x02) или запрос соединения (0x05/0x07) с «магией» RakNet. */
    private static boolean looksLikeRakStart(byte[] d, int len) {
        if (len < 17) return false;
        int id = d[0] & 0xff;
        int off = (id == 0x01 || id == 0x02) ? 9 : ((id == 0x05 || id == 0x07) ? 1 : -1);
        if (off < 0 || len < off + 16) return false;
        for (int i = 0; i < 16; i++) if (d[off + i] != MAGIC[i]) return false;
        return true;
    }

    private void hostReader(HostPeer hp) {
        byte[] buf = new byte[4096];
        while (!stopFlag) {
            try {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                hp.sock.receive(p);
                hp.last = SystemClock.elapsedRealtime();
                hp.recv++;
                if (cntFromWorld < 12 && p.getLength() > 0) log("→ ответ для " + hp.peer.getPort() + ": " + p.getLength() + " байт, id 0x" + Integer.toHexString(buf[0] & 0xff));
                if (++cntFromWorld == 1) log("Мир ответил (прямой путь, с " + p.getAddress().getHostAddress() + ":" + p.getPort() + ")");
                fwdSend(hp.peer, buf, p.getLength());
            } catch (Exception e) { break; }
        }
    }

    // ---------- VPN-туннель ----------

    private void setupTun() {
        tunReady = false;
        if (!useVpn) { log("VPN-режим выключен (включается кнопкой в приложении) — используются прямые пути"); return; }
        if (vpnDenied) { log("VPN отключён (нет разрешения) — только прямые пути"); return; }
        try {
            MpVpnService.err = "";
            startService(new Intent(this, MpVpnService.class));
            long end = SystemClock.elapsedRealtime() + 4000;
            while (MpVpnService.tun == null && SystemClock.elapsedRealtime() < end && MpVpnService.err.isEmpty()) Thread.sleep(100);
            ParcelFileDescriptor t = MpVpnService.tun;
            if (t == null) {
                log("Ошибка: VPN-туннель не создан: " + (MpVpnService.err.isEmpty() ? "служба не ответила за 4 с (проверьте, что MpVpnService добавлен в манифест)" : MpVpnService.err));
                return;
            }
            tunIn = new FileInputStream(t.getFileDescriptor());
            tunOut = new FileOutputStream(t.getFileDescriptor());
            tunReady = true;
            Thread r = new Thread(this::tunReaderLoop, "mp-tun");
            r.setDaemon(true);
            r.start();
            log("VPN-туннель создан (адрес 10.77.0.1). Друзья будут видны Minecraft как 10.77.0.x");
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log("Ошибка: Ошибка VPN: " + e.getClass().getSimpleName() + " " + e.getMessage());
        }
    }

    private int vipFor(InetSocketAddress peer) {
        String k = peer.getAddress().getHostAddress() + ":" + peer.getPort();
        synchronized (vipPeers) {
            Integer v = vipByKey.get(k);
            if (v != null) return v;
            if (vipNext > 250) return -1;
            int n = vipNext++;
            vipByKey.put(k, n);
            vipPeers[n] = peer;
            return n;
        }
    }

    private static byte[] ipUdp(byte[] src, byte[] dst, int sport, int dport, byte[] data, int len) {
        int total = 28 + len;
        byte[] p = new byte[total];
        p[0] = 0x45;
        p[2] = (byte) (total >> 8); p[3] = (byte) total;
        p[6] = 0x40;
        p[8] = 64; p[9] = 17;
        System.arraycopy(src, 0, p, 12, 4);
        System.arraycopy(dst, 0, p, 16, 4);
        int sum = 0;
        for (int i = 0; i < 20; i += 2) if (i != 10) sum += ((p[i] & 0xff) << 8) | (p[i + 1] & 0xff);
        while ((sum >> 16) != 0) sum = (sum & 0xffff) + (sum >> 16);
        sum = ~sum & 0xffff;
        p[10] = (byte) (sum >> 8); p[11] = (byte) sum;
        p[20] = (byte) (sport >> 8); p[21] = (byte) sport;
        p[22] = (byte) (dport >> 8); p[23] = (byte) dport;
        int ul = 8 + len;
        p[24] = (byte) (ul >> 8); p[25] = (byte) ul;
        System.arraycopy(data, 0, p, 28, len);
        return p;
    }

    private void writeTun(InetSocketAddress peer, byte[] d, int len) {
        FileOutputStream o = tunOut;
        if (o == null) return;
        int vip = vipFor(peer);
        if (vip < 0) { if (tunErr++ == 0) log("Ошибка: Слишком много подключений для VPN-адресов"); return; }
        InetSocketAddress ht = hostTarget;
        int dport = ht != null ? ht.getPort() : hostPort;
        byte[] pkt = ipUdp(new byte[]{10, 77, 0, (byte) vip}, new byte[]{10, 77, 0, 1}, peer.getPort(), dport, d, len);
        if (pkt.length > 1500) {
            if (tunBig++ == 0) log("Пакет друга больше 1500 байт — пропущен (Minecraft сам подберёт меньший размер)");
            return;
        }
        try {
            synchronized (o) { o.write(pkt); }
            if (++tunWritten == 1) log("> Первый пакет друга записан в VPN: 10.77.0." + vip + ":" + peer.getPort() + " > 10.77.0.1:" + dport);
        } catch (Exception e) {
            if (tunErr++ < 3) log("Ошибка: Ошибка записи в VPN: " + e.getClass().getSimpleName() + " " + e.getMessage());
        }
    }

    private void tunReaderLoop() {
        FileInputStream in = tunIn;
        if (in == null) return;
        byte[] buf = new byte[4096];
        try {
            StructPollfd pf = new StructPollfd();
            pf.fd = in.getFD();
            pf.events = (short) OsConstants.POLLIN;
            while (!stopFlag && tunReady) {
                pf.revents = 0;
                int r = Os.poll(new StructPollfd[]{pf}, 500);
                if (r <= 0) continue;
                int n = in.read(buf);
                if (n < 28) continue;
                if ((buf[0] >> 4) != 4) { tunOther++; continue; }
                int ihl = (buf[0] & 0x0f) * 4;
                int proto = buf[9] & 0xff;
                boolean frag = ((buf[6] & 0x20) != 0) || (((buf[6] & 0x1f) << 8) | (buf[7] & 0xff)) != 0;
                if (proto != 17 || frag || n < ihl + 8) { tunOther++; if (frag && tunOther < 3) log("Фрагментированный пакет от мира пропущен"); continue; }
                int last = buf[19] & 0xff;
                InetSocketAddress peer = vipPeers[last];
                if (peer == null) { tunOther++; continue; }
                int ulen = ((buf[ihl + 4] & 0xff) << 8) | (buf[ihl + 5] & 0xff);
                int plen = Math.min(ulen - 8, n - ihl - 8);
                if (plen < 0) continue;
                byte[] payload = java.util.Arrays.copyOfRange(buf, ihl + 8, ihl + 8 + plen);
                HostPeer hp = hostPeers.get(peer.getAddress().getHostAddress() + ":" + peer.getPort());
                if (hp != null) { hp.recv++; hp.last = SystemClock.elapsedRealtime(); }
                tunRead++;
                if (++cntFromWorld == 1) log("Мир ответил через VPN (" + plen + " байт)");
                fwdSend(peer, payload, plen);
            }
        } catch (Exception e) {
            if (!stopFlag) log("Ошибка: Чтение VPN остановилось: " + e.getClass().getSimpleName() + " " + e.getMessage());
        }
    }

    /** Проверка: Minecraft открыл мир. Пингуем localhost, адреса Wi-Fi и broadcast на портах 19132..19142. */
    private boolean probeWorld() {
        ArrayList<InetAddress> uni = new ArrayList<>();
        ArrayList<InetAddress> bc = new ArrayList<>();
        try { uni.add(InetAddress.getByName("127.0.0.1")); } catch (Exception ignored) { }
        InetAddress lo6 = null;
        try { lo6 = InetAddress.getByName("::1"); } catch (Exception ignored) { }
        try { bc.add(InetAddress.getByName("255.255.255.255")); } catch (Exception ignored) { }
        try {
            Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
            while (en != null && en.hasMoreElements()) {
                NetworkInterface ni = en.nextElement();
                if (!ni.isUp()) continue;
                for (java.net.InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress a = ia.getAddress();
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                        uni.add(a);
                        if (ia.getBroadcast() != null) bc.add(ia.getBroadcast());
                    }
                }
            }
        } catch (Exception ignored) { }
        DatagramSocket s = null;
        try {
            s = new DatagramSocket();
            s.setBroadcast(true);
            byte[] ping = makePing();
            ArrayList<Integer> ports = new ArrayList<>();
            for (int p = MC_PORT; p <= MC_PORT + 10; p++) ports.add(p);
            if (!ports.contains(hostPort)) ports.add(hostPort);
            if (!ports.contains(7551)) ports.add(7551);   // по предположению пользователя; в источниках я это не нашёл
            for (InetAddress a : uni)
                for (int p : ports) {
                    try { s.send(new DatagramPacket(ping, ping.length, a, p)); } catch (Exception ignored) { }
                }
            for (InetAddress a : bc) {
                try { s.send(new DatagramPacket(ping, ping.length, a, MC_PORT)); } catch (Exception ignored) { }
            }
            if (lo6 != null) {
                try { s.send(new DatagramPacket(ping, ping.length, lo6, 19133)); } catch (Exception ignored) { }
                try { s.send(new DatagramPacket(ping, ping.length, lo6, MC_PORT)); } catch (Exception ignored) { }
            }
            StringBuilder pi = new StringBuilder("Проверено: ");
            for (InetAddress a : uni) pi.append(a.getHostAddress()).append(' ');
            pi.append("порты ").append(MC_PORT).append('–').append(MC_PORT + 10).append(", 7551");
            probeInfo = pi.toString();
            long end = SystemClock.elapsedRealtime() + 1500;
            byte[] r = new byte[2048];
            while (SystemClock.elapsedRealtime() < end) {
                try {
                    s.setSoTimeout((int) Math.max(50, end - SystemClock.elapsedRealtime()));
                    DatagramPacket rp = new DatagramPacket(r, r.length);
                    s.receive(rp);
                    if (rp.getLength() > 0 && (r[0] & 0xff) == 0x1c) {
                        InetAddress from = rp.getAddress();
                        boolean mine = false;
                        for (InetAddress a : uni) if (a.equals(from)) { mine = true; break; }
                        if (!mine) {
                            // любой адрес этого же телефона (в том числе IPv6 и loopback) — тоже наш мир
                            try { mine = from.isLoopbackAddress() || NetworkInterface.getByInetAddress(from) != null; } catch (Exception ignored) { }
                        }
                        if (!mine) {
                            // ответил мир на ДРУГОМ устройстве (например у друга в той же Wi-Fi) — это не наш мир
                            String fh = from.getHostAddress();
                            if (!foreignSeen.contains(fh)) { foreignSeen.add(fh); log("Ответил мир на другом устройстве " + fh + ":" + rp.getPort() + " — игнорирую, это не ваш мир"); }
                            continue;
                        }
                        hostTarget = new InetSocketAddress(from, rp.getPort());
                        log("Найден ваш мир: " + from.getHostAddress() + ":" + rp.getPort());
                        return true;
                    }
                } catch (SocketTimeoutException ignored) { break; }
            }
        } catch (Exception ignored) {
        } finally {
            if (s != null) s.close();
        }
        String bp = "";
        if (++probeMisses <= 3 || probeMisses % 5 == 0) { bp = busyPorts(); nethernetSuspected = bp.contains("7551"); }
        if (!bp.isEmpty() && (probeMisses <= 3 || probeMisses % 10 == 0)) log("Проба " + probeMisses + ": ни один адрес не ответил на пинг мира (" + probeInfo + "). Занятые UDP-порты " + MC_PORT + "–" + (MC_PORT + 10) + ", 7551: " + bp
                + (probeMisses <= 3 ? "\n   (если там пусто — Minecraft вообще не слушает LAN-порт; если порт занят, но не отвечает — Minecraft слушает, но игнорирует пинги от приложения)" : ""));
        return false;
    }

    /** Какие UDP-порты из диапазона мира заняты кем-то другим (скорее всего Minecraft). Порт нашего форвардера пропускаем. */
    private String busyPorts() {
        StringBuilder sb = new StringBuilder();
        int skip = plLocalPort();
        ArrayList<Integer> cand = new ArrayList<>();
        for (int q = MC_PORT; q <= MC_PORT + 10; q++) cand.add(q);
        cand.add(7551);
        for (int pt : cand) {
            if (pt == skip) continue;
            DatagramSocket t = null;
            try {
                t = new DatagramSocket(null);
                t.setReuseAddress(false);
                t.bind(new InetSocketAddress(pt));
            } catch (java.net.BindException e) {
                sb.append(pt).append(' ');
            } catch (Exception ignored) {
            } finally { if (t != null) t.close(); }
        }
        return sb.length() == 0 ? "нет" : sb.toString().trim();
    }

    private volatile int probeMisses;
    private volatile boolean nethernetSuspected;

    // ==================== ГОСТЬ ====================

    private void guestLoop(InetSocketAddress hu) {
        try {
            final InetSocketAddress h = new InetSocketAddress(hu.getHostString(), hu.getPort());
            if (h.isUnresolved()) { stopSession("Не удалось найти адрес хоста: " + hu.getHostString() + "\nПроверьте интернет."); return; }
            log("Гость: адрес хоста " + h.getAddress().getHostAddress() + ":" + h.getPort() + " (" + hu.getHostString() + ")");
            hostRelay = h;

            final DatagramSocket out = new DatagramSocket();
            guestOut = out;
            Thread rd = new Thread(() -> guestReader(out), "mp-greader");
            rd.setDaemon(true);
            rd.start();

            DatagramSocket gs = new DatagramSocket(null);
            gs.setReuseAddress(true);
            gs.setBroadcast(true);
            try { gs.bind(new InetSocketAddress(MC_PORT)); }
            catch (Exception e) { gs.close(); stopSession("Порт 19132 занят — закройте мир, который вы хостите"); return; }
            guestSock = gs;

            Thread pinger = new Thread(() -> {
                try {
                    while (!stopFlag) {
                        byte[] ping = makePing();
                        guestSend(h, ping, ping.length);
                        Thread.sleep(motd == null ? 2000 : 10000);
                    }
                } catch (Exception ignored) { }
            }, "mp-ping");
            pinger.setDaemon(true);
            pinger.start();

            guestBase = ("Готово! Minecraft > Друзья > «Мой мир».\nНе видно? Minecraft прячет локальный мир, если такой адрес уже сохранён во вкладке «Серверы» — удалите его оттуда. Или добавьте сервер: адрес 127.0.0.1, порт " + MC_PORT + ". Список «1/10» и пинг в «Серверах» — это ответ самого приложения, а не друга.");
            setStatus(guestBase);
            ui.post(this::launchMinecraft);

            byte[] buf = new byte[4096];
            gs.setSoTimeout(1000);
            while (!stopFlag) {
                String gst = guestBase + "\nОтправлено хосту: " + gSent + ", получено от хоста: " + gRecv;
                if (!gst.equals(status)) setStatus(gst);
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                try { gs.receive(p); } catch (SocketTimeoutException e) { continue; }
                int len = p.getLength();
                if (len < 1) continue;
                int b0 = buf[0] & 0xff;
                if (b0 == 0x01 || b0 == 0x02) {
                    if (pingsSeen++ == 0) log("Гость: Minecraft прислал первый пинг с " + p.getAddress().getHostAddress() + ":" + p.getPort() + " — отвечаю («" + (motd == null ? "запасное описание" : "описание от хоста") + "»)");
                    if (len >= 9) sendPong(gs, p, buf);
                    continue;
                }
                clientAddr = (InetSocketAddress) p.getSocketAddress();
                gSent++;
                guestSend(h, buf, len);
            }
        } catch (Exception e) {
            Log.e(TAG, "guest", e);
            if (!stopFlag) stopSession("Ошибка: " + e.getMessage());
        }
    }

    private void guestSend(InetSocketAddress h, byte[] d, int len) {
        DatagramSocket o = guestOut;
        if (o == null) return;
        try { o.send(new DatagramPacket(d, 0, len, h)); } catch (Exception ignored) { }
    }

    private void guestReader(DatagramSocket out) {
        byte[] buf = new byte[4096];
        while (!stopFlag && !out.isClosed()) {
            try {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                out.receive(p);
                guestOnData((InetSocketAddress) p.getSocketAddress(), buf, p.getLength());
            } catch (Exception e) { if (out.isClosed()) break; }
        }
    }

    private void guestOnData(InetSocketAddress peer, byte[] d, int len) {
        InetSocketAddress h = hostRelay;
        if (h == null) return;
        if (!peer.equals(h) && !(peer.getPort() == h.getPort() && Arrays.equals(peer.getAddress().getAddress(), h.getAddress().getAddress()))) {
            if (peerMismatch++ < 3) log("Гость: пакет пришёл от " + peer.getAddress().getHostAddress() + ":" + peer.getPort() + ", а ожидался хост " + h.getAddress().getHostAddress() + ":" + h.getPort() + " — отброшен");
            return;
        }
        if (len < 1) return;
        if ((d[0] & 0xff) == 0x1c) {
            if (!hostGuidKnown) log("Гость: пинг дошёл до мира хоста и вернулся — мир отвечает");
            if (len >= 17) { hostGuid = ByteBuffer.wrap(d, 9, 8).getLong(); hostGuidKnown = true; }
            if (len > 35) {
                int sl = ((d[33] & 0xff) << 8) | (d[34] & 0xff);
                sl = Math.min(sl, len - 35);
                motd = new String(d, 35, sl, StandardCharsets.UTF_8);
            }
            return;
        }
        InetSocketAddress ca = clientAddr;
        DatagramSocket gs = guestSock;
        if (ca == null || gs == null) return;
        try { gs.send(new DatagramPacket(d, 0, len, ca)); } catch (Exception ignored) { }
        gRecv++;
        if (!gotGameData) { gotGameData = true; guestBase = "Подключено к миру друга"; log("Гость: первый игровой пакет от хоста получен"); }
    }

    private String buildMotd() {
        String m = motd;
        if (m != null) {
            String[] p = m.split(";", -1);
            if (p.length >= 9) {
                p[1] = "Мой мир";
                if (p.length > 11) { p[10] = String.valueOf(MC_PORT); p[11] = String.valueOf(MC_PORT + 1); }
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < p.length; i++) { if (i > 0) sb.append(';'); sb.append(p[i]); }
                return sb.toString();
            }
        }
        // запасное значение, пока мир хоста не прислал настоящее (протокол 766 для 1.21.50/51 — из памяти, не проверено)
        return "MCPE;Мой мир;766;1.21.51;0;10;" + guid + ";Arcade;Survival;1;" + MC_PORT + ";" + (MC_PORT + 1) + ";";
    }

    private void sendPong(DatagramSocket gs, DatagramPacket ping, byte[] buf) {
        try {
            byte[] sb = buildMotd().getBytes(StandardCharsets.UTF_8);
            ByteBuffer b = ByteBuffer.allocate(1 + 8 + 8 + 16 + 2 + sb.length);
            b.put((byte) 0x1c).put(buf, 1, 8).putLong(hostGuidKnown ? hostGuid : guid).put(MAGIC).putShort((short) sb.length).put(sb);
            byte[] out = b.array();
            gs.send(new DatagramPacket(out, out.length, ping.getSocketAddress()));
        } catch (Exception ignored) { }
    }

    // ===== «Друзья» для владельца: фейковый LAN-сервер на 19132 > встроенный сервер на SRV_PORT =====
    private volatile DatagramSocket ownerSock, ownerOut;
    private volatile Thread ownerThread;
    private volatile int ownerPings;
    private volatile InetSocketAddress ownerClient;
    private volatile String ownerMotd;
    private volatile long ownerSrvGuid;
    private volatile boolean ownerSrvGuidKnown;

    private synchronized void startOwnerRelay() {
        Thread old = ownerThread;
        if (old != null && old.isAlive() && ownerSock != null && !ownerSock.isClosed()) return;
        final InetSocketAddress srv = new InetSocketAddress("127.0.0.1", SRV_PORT);
        DatagramSocket gs = null, out = null;
        try {
            gs = new DatagramSocket(null);
            gs.setReuseAddress(true);
            gs.setBroadcast(true);
            gs.bind(new InetSocketAddress(MC_PORT));
            out = new DatagramSocket();
        } catch (Exception e) {
            if (gs != null) gs.close();
            if (out != null) out.close();
            log("Внимание: Не удалось показать сервер во вкладке «Друзья» (порт " + MC_PORT + " занят: " + e.getMessage() + "). Заходите через Серверы > 127.0.0.1 : " + SRV_PORT);
            return;
        }
        final DatagramSocket fg = gs, fo = out;
        ownerSock = fg; ownerOut = fo; ownerClient = null; ownerMotd = null; ownerSrvGuidKnown = false;
        Thread t = new Thread(() -> ownerLoop(fg, fo, srv), "mp-owner");
        t.setDaemon(true);
        ownerThread = t;
        t.start();
        log("Сервер будет виден в Minecraft > Друзья (LAN-ответчик на порту " + MC_PORT + ")");
    }

    private synchronized void stopOwnerRelay() {
        DatagramSocket g = ownerSock; ownerSock = null;
        DatagramSocket o = ownerOut; ownerOut = null;
        if (g != null) g.close();
        if (o != null) o.close();
        Thread t = ownerThread; ownerThread = null;
        if (t != null) t.interrupt();
        ownerClient = null;
        for (HostPeer hp : ownerPeers.values()) { try { hp.sock.close(); } catch (Exception ignored) { } }
        ownerPeers.clear();
    }

    /** У каждого клиента, зашедшего через «Друзья», свой сокет к серверу — иначе несколько устройств путаются между собой. */
    private final ConcurrentHashMap<String, HostPeer> ownerPeers = new ConcurrentHashMap<>();

    private void ownerForward(final InetSocketAddress client, final DatagramSocket gs, byte[] d, int len, InetSocketAddress srv) {
        String k = client.getAddress().getHostAddress() + ":" + client.getPort();
        HostPeer hp = ownerPeers.get(k);
        if (hp == null) {
            try {
                hp = new HostPeer(client, true);
                ownerPeers.put(k, hp);
                final HostPeer fp = hp;
                Thread t = new Thread(() -> {
                    byte[] b = new byte[4096];
                    while (!gs.isClosed() && !fp.sock.isClosed()) {
                        try {
                            DatagramPacket pk = new DatagramPacket(b, b.length);
                            fp.sock.receive(pk);
                            fp.last = SystemClock.elapsedRealtime();
                            gs.send(new DatagramPacket(b, 0, pk.getLength(), client));
                        } catch (Exception e) { break; }
                    }
                }, "mp-owner-peer");
                t.setDaemon(true);
                t.start();
                log("Подключение к серверу через «Друзья»: " + k);
            } catch (Exception e) { log("Ошибка: Не удалось завести сокет для " + k + ": " + e.getMessage()); return; }
        }
        hp.last = SystemClock.elapsedRealtime();
        try { hp.sock.send(new DatagramPacket(d, 0, len, srv)); } catch (Exception ignored) { }
    }

    private void ownerClean() {
        long n = SystemClock.elapsedRealtime();
        for (Map.Entry<String, HostPeer> e : ownerPeers.entrySet()) {
            if (n - e.getValue().last > 120000) { try { e.getValue().sock.close(); } catch (Exception ignored) { } ownerPeers.remove(e.getKey()); }
        }
    }

    private void ownerLoop(final DatagramSocket gs, final DatagramSocket out, final InetSocketAddress srv) {
        Thread rd = new Thread(() -> {
            byte[] b = new byte[4096];
            while (!out.isClosed()) {
                try {
                    DatagramPacket p = new DatagramPacket(b, b.length);
                    out.receive(p);
                    int len = p.getLength();
                    if (len < 1) continue;
                    if ((b[0] & 0xff) == 0x1c) {
                        if (len >= 17) { ownerSrvGuid = ByteBuffer.wrap(b, 9, 8).getLong(); ownerSrvGuidKnown = true; }
                        if (len > 35) {
                            int sl = Math.min(((b[33] & 0xff) << 8) | (b[34] & 0xff), len - 35);
                            ownerMotd = new String(b, 35, sl, StandardCharsets.UTF_8);
                        }
                        continue;
                    }
                    InetSocketAddress ca = ownerClient;
                    if (ca != null) gs.send(new DatagramPacket(b, 0, len, ca));
                } catch (Exception e) { if (out.isClosed()) break; }
            }
        }, "mp-owner-rd");
        rd.setDaemon(true);
        rd.start();
        Thread pg = new Thread(() -> {
            try {
                while (!gs.isClosed()) {
                    byte[] ping = makePing();
                    out.send(new DatagramPacket(ping, ping.length, srv));
                    Thread.sleep(ownerMotd == null ? 1500 : 8000);
                    ownerClean();
                }
            } catch (Exception ignored) { }
        }, "mp-owner-ping");
        pg.setDaemon(true);
        pg.start();
        byte[] buf = new byte[4096];
        try { gs.setSoTimeout(1000); } catch (Exception ignored) { }
        while (!gs.isClosed()) {
            try {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                try { gs.receive(p); } catch (SocketTimeoutException e) { continue; }
                int len = p.getLength();
                if (len < 1) continue;
                int b0 = buf[0] & 0xff;
                if (b0 == 0x01 || b0 == 0x02) {
                    if (ownerPings++ < 8) log("«Друзья»: пинг от " + p.getAddress().getHostAddress() + ":" + p.getPort() + " (отвечаю)");
                    if (len >= 9) sendOwnerPong(gs, p, buf);
                    continue;
                }
                ownerForward((InetSocketAddress) p.getSocketAddress(), gs, buf, len, srv);
            } catch (Exception e) { if (gs.isClosed()) break; }
        }
    }

    private String buildOwnerMotd() {
        String m = ownerMotd;
        if (m != null) {
            String[] p = m.split(";", -1);
            if (p.length >= 9) {
                if (p.length > 11) { p[10] = String.valueOf(MC_PORT); p[11] = String.valueOf(MC_PORT + 1); }
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < p.length; i++) { if (i > 0) sb.append(';'); sb.append(p[i]); }
                return sb.toString();
            }
        }
        // запасное значение, пока сервер не прислал настоящее
        return "MCPE;Arcade;766;1.21.51;0;10;" + guid + ";Arcade;Survival;1;" + MC_PORT + ";" + (MC_PORT + 1) + ";";
    }

    private void sendOwnerPong(DatagramSocket gs, DatagramPacket ping, byte[] buf) {
        try {
            byte[] sb = buildOwnerMotd().getBytes(StandardCharsets.UTF_8);
            ByteBuffer b = ByteBuffer.allocate(1 + 8 + 8 + 16 + 2 + sb.length);
            b.put((byte) 0x1c).put(buf, 1, 8).putLong(ownerSrvGuidKnown ? ownerSrvGuid : guid).put(MAGIC).putShort((short) sb.length).put(sb);
            byte[] o = b.array();
            gs.send(new DatagramPacket(o, o.length, ping.getSocketAddress()));
        } catch (Exception ignored) { }
    }

    private byte[] makePing() {
        ByteBuffer b = ByteBuffer.allocate(33);
        b.put((byte) 0x01).putLong(System.currentTimeMillis()).put(MAGIC).putLong(guid);
        return b.array();
    }

    // ==================== Код комнаты ====================

    /** Код: тип(2 бита, сейчас всегда 0 = IPv4) + IPv4(32) + порт(16) + контрольная сумма(8) = 58 бит > 12 символов. */
    private String encodeAddr(String host, int port) throws Exception {
        int type = 0;
        byte[] ip = new byte[4];
        if (type == 0) {
            byte[] ab = null;
            for (InetAddress a : InetAddress.getAllByName(host)) if (a instanceof Inet4Address) { ab = a.getAddress(); break; }
            if (ab == null) throw new IOException("у " + host + " нет IPv4-адреса");
            ip = ab;
        }
        int chk = 0xA7 ^ type ^ ((port >> 8) & 0xff) ^ (port & 0xff);
        for (byte x : ip) chk ^= (x & 0xff);
        chk &= 0xff;
        long v = type;
        for (int i = 0; i < 4; i++) v = (v << 8) | (ip[i] & 0xff);
        v = (v << 16) | (port & 0xffff);
        v = (v << 8) | chk;
        v <<= 2;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) sb.append(ALPHA.charAt((int) ((v >> (55 - 5 * i)) & 31)));
        return sb.toString();
    }

    /** Возвращает НЕразрешённый адрес (DNS делается позже, не в UI-потоке) или null, если код неверный. */
    private InetSocketAddress decodeCode(String raw) {
        try {
            StringBuilder sb = new StringBuilder();
            for (char ch : raw.toUpperCase().toCharArray()) if (ALPHA.indexOf(ch) >= 0) sb.append(ch);
            if (sb.length() != 12) return null;
            long v = 0;
            for (int i = 0; i < 12; i++) v = (v << 5) | ALPHA.indexOf(sb.charAt(i));
            v >>= 2;
            int chk = (int) (v & 0xff); v >>= 8;
            int port = (int) (v & 0xffff); v >>= 16;
            byte[] ip = new byte[4];
            for (int i = 3; i >= 0; i--) { ip[i] = (byte) (v & 0xff); v >>= 8; }
            int type = (int) (v & 3);
            int c2 = 0xA7 ^ type ^ ((port >> 8) & 0xff) ^ (port & 0xff);
            for (byte x : ip) c2 ^= (x & 0xff);
            if ((c2 & 0xff) != chk) return null;
            String host = type == 0 ? ((ip[0] & 0xff) + "." + (ip[1] & 0xff) + "." + (ip[2] & 0xff) + "." + (ip[3] & 0xff)) : ((ip[0] & 0xff) + "." + (ip[1] & 0xff) + "." + (ip[2] & 0xff) + "." + (ip[3] & 0xff));
            return InetSocketAddress.createUnresolved(host, port);
        } catch (Exception e) { return null; }
    }

    private String pretty(String c) {
        if (c == null || c.length() != 12) return "";
        return c.substring(0, 4) + "-" + c.substring(4, 8) + "-" + c.substring(8, 12);
    }

    // ==================== playit.gg ====================

    private void saveRtSettings() {
        try {
            android.content.SharedPreferences.Editor ed = prefs.edit();
            if (tokenInput != null) { String t = tokenInput.getText().toString().trim(); if (!t.isEmpty()) ed.putString("rt_token", t); }
            if (urlInput != null) ed.putString("pl_addr", urlInput.getText().toString().trim());
            if (plPortInput != null) ed.putString("pl_port", plPortInput.getText().toString().trim());
            if (portInput != null) ed.putString("host_port", portInput.getText().toString().trim());
            ed.apply();
        } catch (Exception ignored) { }
    }

    private String pubLine() {
        String pa = publicAddr;
        if (pa == null || pa.isEmpty()) return "";
        int c = pa.lastIndexOf(':');
        if (c < 0) return "";
        return "Друг может зайти и без приложения: Minecraft > Серверы > Добавить: адрес " + pa.substring(0, c) + ", порт " + pa.substring(c + 1) + "\n";
    }

    private void fwdSend(InetSocketAddress peer, byte[] d, int len) {
        DatagramSocket f = fwdSock;
        if (f == null) return;
        try { f.send(new DatagramPacket(d, 0, len, peer)); } catch (Exception e) { if (fwdErr++ < 3) log("Ошибка: Не удалось отправить ответ в туннель: " + e.getClass().getSimpleName() + " " + e.getMessage()); }
    }

    private volatile int fwdErr;

    /** Принимает пакеты друзей от агента playit (у каждого друга свой локальный порт-источник). */
    private void fwdLoop(DatagramSocket fs) {
        byte[] buf = new byte[4096];
        while (!stopFlag && !fs.isClosed()) {
            try {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                fs.receive(p);
                hostOnData((InetSocketAddress) p.getSocketAddress(), buf, p.getLength());
            } catch (Exception e) { if (fs.isClosed()) break; }
        }
    }

    private void rtTailAdd(String l) {
        synchronized (rtTail) { rtTail.addLast(l.length() > 200 ? l.substring(0, 200) : l); while (rtTail.size() > 12) rtTail.removeFirst(); }
    }

    private String rtTailText() {
        synchronized (rtTail) { StringBuilder sb = new StringBuilder(); for (String l : rtTail) sb.append(l).append('\n'); return sb.toString(); }
    }

    /** Агент playit: libplayit.so (или libplayitd.so) из jniLibs — его Android разрешает запускать. */
    private File rtBin() {
        File d = new File(getApplicationInfo().nativeLibraryDir);
        File a = new File(d, "libplayit.so");
        if (a.exists()) return a;
        File b = new File(d, "libplayitd.so");
        return b.exists() ? b : a;
    }

    private int plLocalPort() {
        int p = PL_DEFAULT_PORT;
        try { p = Integer.parseInt(prefs.getString("pl_port", "").trim()); } catch (Exception ignored) { }
        return (p < 1024 || p > 65535) ? PL_DEFAULT_PORT : p;
    }

    private DatagramSocket openFwd(int port) {
        try { return new DatagramSocket(port, InetAddress.getByName("127.0.0.1")); } catch (Exception e) { log("Ошибка: Не удалось открыть 127.0.0.1:" + port + ": " + e.getMessage()); return null; }
    }

    private static File findFile(File dir, String name) {
        File[] ch = dir.listFiles();
        if (ch == null) return null;
        for (File c : ch) {
            if (c.isFile() && c.getName().equals(name)) return c;
            if (c.isDirectory()) { File r = findFile(c, name); if (r != null) return r; }
        }
        return null;
    }

    /** true, если ELF собран под обычный Linux (glibc): на Android он не запускается. */
    private static boolean isGlibcElf(File f) {
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[4096];
            int n = in.read(b);
            return n > 0 && new String(b, 0, n, StandardCharsets.ISO_8859_1).contains("ld-linux");
        } catch (Exception e) { return false; }
    }

    /** Проверяет, что агент playit на месте и подходит для Android. Возвращает текст ошибки или null. */
    private String ensureAgent(boolean force) {
        File b = rtBin();
        if (!b.exists())
            return "Нет агента playit для Android.\nСоберите его через GitHub Actions (файл build-playit-android.yml) и положите libplayit.so в app/src/main/jniLibs/arm64-v8a/ (в манифесте нужен android:extractNativeLibs=\"true\").";
        if (Build.SUPPORTED_ABIS.length == 0 || !Build.SUPPORTED_ABIS[0].contains("arm64"))
            return "Агент playit собран только для arm64, у вас: " + (Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "?");
        b.setExecutable(true, false);
        if (isGlibcElf(b))
            return "Этот файл — сборка playit для обычного Linux (glibc), на Android она не запустится.\nНужна сборка под Android (aarch64-linux-android) — см. build-playit-android.yml.";
        return null;
    }

    /** Разбирает публичный адрес туннеля (host:port) из настроек и строит код комнаты. Ошибку кладёт в rtErr. */
    private void applyPublicAddr(String pa) {
        try {
            pa = pa.trim().replaceFirst("^[A-Za-z0-9+.-]+://", "").replaceAll("/.*$", "");
            int c = pa.lastIndexOf(':');
            if (c < 0) throw new IOException("нет порта в адресе «" + pa + "» (нужен вид host:port)");
            String host = pa.substring(0, c);
            int port = Integer.parseInt(pa.substring(c + 1));
            String cd = encodeAddr(host, port);
            publicAddr = pa;
            code = cd;
            log("Адрес туннеля playit: " + pa + " > код " + pretty(cd));
            pushText();
        } catch (Exception e) {
            rtErr = "не удалось разобрать публичный адрес «" + pa + "»: " + e.getMessage();
        }
    }

    private volatile int rtLogged;

    /** Для отладки: выводит в журнал справку агента (`--help`), чтобы видеть, какие аргументы он принимает. */
    private void dumpAgentHelp(File bin, File home) {
        try {
            ProcessBuilder hb = new ProcessBuilder(bin.getAbsolutePath(), "--help");
            hb.directory(home);
            hb.redirectErrorStream(true);
            hb.environment().put("HOME", home.getAbsolutePath());
            final Process hp = hb.start();
            final StringBuilder sb = new StringBuilder();
            Thread rd = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(hp.getInputStream(), StandardCharsets.UTF_8))) {
                    String l; int n = 0;
                    while ((l = br.readLine()) != null && n++ < 40) sb.append("  ").append(l.length() > 120 ? l.substring(0, 120) : l).append('\n');
                } catch (Exception ignored) { }
            }, "mp-rt-help");
            rd.setDaemon(true);
            rd.start();
            if (!hp.waitFor(4, TimeUnit.SECONDS)) hp.destroyForcibly();
            rd.join(500);
            log("Справка агента (--help):\n" + sb);
        } catch (Exception e) { log("Справка агента недоступна: " + e.getMessage()); }
    }

    private volatile String agentState = "запускается…";
    private volatile long lastAgentBad;

    private void rtOnLine(String raw) {
        // агент красит вывод ANSI-кодами (ESC[2m…) — убираем, чтобы журнал читался
        String line = raw.replaceAll("\u001B\\[[0-9;]*m", "").replaceAll("\\[[0-9;]*m", "").trim();
        if (line.isEmpty()) return;
        rtTailAdd(line);
        String lo = line.toLowerCase(Locale.ROOT);
        if (lo.contains("playit.gg/claim")) {
            // агент не принял secret key и просит привязать его к аккаунту
            rtErr = "агент не принял secret key и просит привязку:\n" + line;
            log("Ошибка: playit: " + line);
            return;
        }
        if (lo.contains("tunnels loaded")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("tunnel_count=(\\d+)").matcher(line);
            String n = m.find() ? m.group(1) : "?";
            agentState = "подключён, туннелей у агента: " + n;
            if ("0".equals(n)) log("Внимание: У агента в панели playit НЕТ туннелей (tunnel_count=0): создайте туннель именно для этого агента (UDP, 127.0.0.1:" + plLocalPort() + ").");
        } else if (lo.contains("control session expired") || lo.contains("timeout waiting for pong") || lo.contains("failed to authenticate") || lo.contains("failed to reload_control_addr")) {
            agentState = "связь с playit нестабильна (переподключается)";
            lastAgentBad = SystemClock.elapsedRealtime();
        }
        boolean important = lo.contains("error") || lo.contains("warn") || lo.contains("fail") || lo.contains("denied") || lo.contains("limit") || lo.contains("invalid") || lo.contains("connected") || lo.contains("tunnel");
        if (important || rtLogged < 25) {
            if (rtLogged++ < 150) log("[playit] " + (line.length() > 260 ? line.substring(0, 260) : line));
        }
    }

    /** Запускает агент playit с общим secret key и берёт адрес туннеля из настроек. Возвращает текст ошибки или null. */
    private String startAgent() throws InterruptedException {
        setStatus("Запускаю агент playit…");
        String err = ensureAgent(false);
        if (err != null) return err;
        String secret = prefs.getString("rt_token", "");
        rtErr = "";
        applyPublicAddr(prefs.getString("pl_addr", ""));
        if (!rtErr.isEmpty()) return "playit: " + rtErr;
        File home = new File(srvDir(), "rthome");
        home.mkdirs();
        final File bin = rtBin();
        rtLogged = 0;
        dumpAgentHelp(bin, home);
        final Process p;
        try {
            ArrayList<String> cmd = new ArrayList<>();
            cmd.add(bin.getAbsolutePath());
            // Демон playitd 1.0 не читает SECRET_KEY из окружения: ключ ему нужно передать через --secret
            // (иначе он ждёт ключ от внешнего интерфейса по IPC). Сокет кладём в папку приложения.
            cmd.add("--secret"); cmd.add(secret);
            // Ключ создан мастером «Docker» в панели playit, поэтому регистрируем агента как docker-платформу.
            cmd.add("--platform-docker");
            log("Ключ: длина " + secret.length() + ", " + (secret.matches("[0-9a-fA-F]+") ? "только hex-символы" : "есть НЕ hex-символы (пробел, перенос или лишний текст?)"));
            cmd.add("--socket-path"); cmd.add(new File(home, "playitd.sock").getAbsolutePath());
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(home);
            pb.redirectErrorStream(true);
            pb.environment().put("HOME", home.getAbsolutePath());
            pb.environment().put("TMPDIR", getCacheDir().getAbsolutePath());
            File ca = buildCaBundle(home);
            if (ca != null) {
                pb.environment().put("SSL_CERT_FILE", ca.getAbsolutePath());
                pb.environment().put("SSL_CERT_DIR", home.getAbsolutePath());
            } else log("Внимание: Не нашёл системные сертификаты Android — агент playit может не подключиться");
            pb.environment().put("SECRET_KEY", secret);
            synchronized (rtTail) { rtTail.clear(); }
            p = pb.start();
            rtProc = p;
        } catch (Exception e) {
            String m = String.valueOf(e.getMessage());
            String hint = m.contains("denied") ? (getApplicationInfo().targetSdkVersion > 28
                    ? "\nAndroid не даёт запускать скачанные файлы при targetSdkVersion " + getApplicationInfo().targetSdkVersion + ". Положите агент как app/src/main/jniLibs/arm64-v8a/libplayit.so (и extractNativeLibs=true) либо поставьте targetSdkVersion 28."
                    : "\nУ файла нет права на запуск. Очистите данные приложения и повторите.")
                    : (m.contains("No such file") ? "\nОбычно это значит, что агент собран не под Android (нет загрузчика glibc). Нужна сборка под Android — см. build-playit-android.yml." : "");
            return "Не удалось запустить агент playit: " + m + hint;
        }
        Thread o = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String l;
                while ((l = br.readLine()) != null) rtOnLine(l);
            } catch (Exception ignored) { }
        }, "mp-rt-out");
        o.setDaemon(true);
        o.start();
        log("Агент playit запущен: " + bin.getName());

        // У агента нет события «туннель готов»: считаем, что всё хорошо, если он проработал несколько секунд без ошибок.
        long t0 = SystemClock.elapsedRealtime();
        while (!stopFlag && SystemClock.elapsedRealtime() - t0 < 25000) {
            if (!rtErr.isEmpty()) { Thread.sleep(200); return "playit: " + rtErr; }
            if (!alive(p)) { Thread.sleep(300); return "Агент playit завершился (код " + p.exitValue() + "):\n" + rtTailText(); }
            if (SystemClock.elapsedRealtime() - t0 >= 8000) return null;
            Thread.sleep(200);
        }
        return stopFlag ? "Остановлено" : "Агент playit не запустился.\n" + rtTailText();
    }

    /** На Android нет /etc/ssl/certs: собираем из системных сертификатов один PEM-файл для клиента. */
    private File buildCaBundle(File dir) {
        try {
            File out = new File(dir, "cacert.pem");
            java.util.HashSet<String> seen = new java.util.HashSet<>();
            StringBuilder sb = new StringBuilder();
            int n = 0;
            for (String d : new String[]{"/apex/com.android.conscrypt/cacerts", "/system/etc/security/cacerts", "/system/etc/security/cacerts_google"}) {
                File[] fs = new File(d).listFiles();
                if (fs == null) continue;
                for (File f : fs) {
                    try {
                        if (!f.isFile() || f.length() > 100000) continue;
                        byte[] b = new byte[(int) f.length()];
                        try (FileInputStream in = new FileInputStream(f)) {
                            int got = 0;
                            while (got < b.length) { int r = in.read(b, got, b.length - got); if (r < 0) break; got += r; }
                        }
                        String t = new String(b, StandardCharsets.ISO_8859_1);
                        int a = t.indexOf("-----BEGIN CERTIFICATE-----");
                        int e = t.indexOf("-----END CERTIFICATE-----");
                        if (a < 0 || e < a) continue;
                        String pem = t.substring(a, e + 25).trim();
                        if (seen.add(pem)) { sb.append(pem).append('\n'); n++; }
                    } catch (Exception ignored) { }
                }
            }
            if (n == 0) return null;
            try (FileOutputStream o = new FileOutputStream(out)) { o.write(sb.toString().getBytes(StandardCharsets.ISO_8859_1)); }
            log("Сертификаты Android: " + n + " шт. > " + out.getName());
            return out;
        } catch (Exception e) { return null; }
    }

    private void stopAgent() {
        final Process p = rtProc;
        rtProc = null;
        if (p == null) return;
        new Thread(() -> {
            try { p.destroy(); if (!p.waitFor(2, TimeUnit.SECONDS)) p.destroyForcibly(); } catch (Exception e) { p.destroyForcibly(); }
        }, "mp-rt-stop").start();
    }

    // ==================== Сервер: включить / выключить, файлы ====================

    private void toggleServer() {
        if (srvBusy) { toast("Подождите…"); return; }
        if (srvAlive()) {
            serverStandalone = false;
            stopBuiltinServer();
            setStatus("Сервер выключается…");
            ui.postDelayed(this::pushText, 1500);
            return;
        }
        if (mode != M_IDLE) { toast("Сначала остановите комнату"); return; }
        srvBusy = true;
        serverStandalone = true;
        stopFlag = false;
        pushText();
        new Thread(() -> {
            try {
                setStatus("Запускаю встроенный сервер…");
                String err = bootServer();
                if (err != null) {
                    serverStandalone = false;
                    stopBuiltinServer();
                    setStatus("Сервер не запущен: " + err);
                } else {
                    startOwnerRelay();
                    setStatus("Сервер включён\nВход с этого телефона: Minecraft > Друзья > «Arcade» (или Серверы > 127.0.0.1 : " + MC_PORT + ")\nЧтобы пустить друзей — «СОЗДАТЬ НА ВСТРОЕННОМ СЕРВЕРЕ».");
                }
            } catch (InterruptedException ignored) {
            } finally { srvBusy = false; pushText(); }
        }, "mp-srv-start").start();
    }

    private File fbRoot, fbCur;

    private static String humanSize(long n) {
        if (n < 1024) return n + " Б";
        if (n < 1024 * 1024) return (n / 1024) + " КБ";
        return String.format(Locale.US, "%.1f МБ", n / 1048576.0);
    }

    private static String readStream(InputStream is) throws java.io.IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line).append('\n');
        r.close();
        return sb.toString();
    }

    // ==================== UI ====================

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private GradientDrawable rect(int fill, float rDp, int stroke, float strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(rDp));
        if (strokeDp > 0) g.setStroke(Math.max(1, dp(strokeDp)), stroke);
        return g;
    }

    private TextView tv(String text, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView btn(String text, int fill, float sp, View.OnClickListener l) {
        TextView t = tv(text, sp, C_TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(10), dp(12), dp(10), dp(12));
        t.setBackground(rect(fill, 10, 0, 0));
        t.setOnClickListener(l);
        return t;
    }

    private LinearLayout.LayoutParams lin(int w, int h, float weight, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h, weight);
        p.setMargins(l, t, r, b);
        return p;
    }

    private TextView sectionLabel(String s) {
        TextView t = tv(s, 11, C_RED, true);
        t.setLetterSpacing(0.12f);
        t.setPadding(0, dp(18), 0, dp(6));
        return t;
    }

    // ==================================================================================================
    //  НОВОЕ: ИКОНКИ, ОФОРМЛЕНИЕ, АНИМАЦИИ
    // ==================================================================================================

    static final int C_HI = 0xFFFF7A45;          // второй цвет градиента (акцент)
    static final int C_PANEL = 0xFF131317;
    static final int C_BLUE = 0xFF4C8DFF;
    static final int C_AMBER = 0xFFFFB020;

    /** Векторные иконки: рисуются кодом в сетке 24x24, эмодзи нигде не используются. */
    static final class Ico extends android.graphics.drawable.Drawable {
        final String n;
        int color;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path path = new Path();

        Ico(String n, int color) { this.n = n; this.color = color; }

        private void ln(Canvas c, float a, float b, float x, float y) { c.drawLine(a, b, x, y, p); }

        private void poly(Canvas c, float... v) {
            path.reset();
            path.moveTo(v[0], v[1]);
            for (int i = 2; i + 1 < v.length; i += 2) path.lineTo(v[i], v[i + 1]);
            c.drawPath(path, p);
        }

        private void st() { p.setStyle(Paint.Style.STROKE); }
        private void fl() { p.setStyle(Paint.Style.FILL); }

        @Override public void draw(@NonNull Canvas c) {
            Rect b = getBounds();
            float s = Math.min(b.width(), b.height()) / 24f;
            c.save();
            c.translate(b.left, b.top);
            c.scale(s, s);
            p.setColor(color);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeWidth(1.9f);
            st();
            switch (n) {
                case "web": c.drawCircle(12, 12, 9, p); c.drawOval(new RectF(8, 3, 16, 21), p); ln(c, 3, 12, 21, 12); break;
                case "settings":
                    ln(c, 4, 6, 20, 6); ln(c, 4, 12, 20, 12); ln(c, 4, 18, 20, 18);
                    fl(); c.drawCircle(9, 6, 2.5f, p); c.drawCircle(15, 12, 2.5f, p); c.drawCircle(8, 18, 2.5f, p); break;
                case "macro": poly(c, 6, 3, 6, 19, 10.5f, 15, 14, 21, 16.5f, 19.8f, 13, 14, 19, 13.5f, 6, 3); break;
                case "rec": c.drawCircle(12, 12, 9, p); fl(); c.drawCircle(12, 12, 4.6f, p); break;
                case "server":
                    c.drawRoundRect(new RectF(3, 4, 21, 10), 2.5f, 2.5f, p); c.drawRoundRect(new RectF(3, 14, 21, 20), 2.5f, 2.5f, p);
                    fl(); c.drawCircle(7, 7, 1.1f, p); c.drawCircle(7, 17, 1.1f, p); break;
                case "close": ln(c, 6, 6, 18, 18); ln(c, 18, 6, 6, 18); break;
                case "gamepad":
                    c.drawRoundRect(new RectF(2, 6.5f, 22, 18), 5.5f, 5.5f, p);
                    ln(c, 7, 9.6f, 7, 14.8f); ln(c, 4.4f, 12.2f, 9.6f, 12.2f);
                    fl(); c.drawCircle(15.5f, 10.8f, 1.2f, p); c.drawCircle(18.3f, 13.6f, 1.2f, p); break;
                case "play": fl(); poly(c, 8, 5, 19, 12, 8, 19, 8, 5); break;
                case "stop": fl(); c.drawRoundRect(new RectF(6, 6, 18, 18), 2.2f, 2.2f, p); break;
                case "pause": fl(); c.drawRoundRect(new RectF(6, 5, 10, 19), 1.5f, 1.5f, p); c.drawRoundRect(new RectF(14, 5, 18, 19), 1.5f, 1.5f, p); break;
                case "plus": ln(c, 12, 5, 12, 19); ln(c, 5, 12, 19, 12); break;
                case "minus": ln(c, 5, 12, 19, 12); break;
                case "folder": poly(c, 3, 7, 3, 19, 21, 19, 21, 9, 11, 9, 9, 6, 4, 6, 3, 7); break;
                case "file": poly(c, 6, 3, 14, 3, 19, 8, 19, 21, 6, 21, 6, 3); poly(c, 14, 3, 14, 8, 19, 8); break;
                case "trash": ln(c, 4, 7, 20, 7); poly(c, 6, 7, 7, 20, 17, 20, 18, 7); poly(c, 9, 7, 9, 4, 15, 4, 15, 7); break;
                case "save": poly(c, 5, 3, 17, 3, 21, 7, 21, 21, 5, 21, 5, 3); poly(c, 8, 3, 8, 8, 15, 8, 15, 3); c.drawRect(8, 13, 16, 21, p); break;
                case "target":
                    c.drawCircle(12, 12, 7, p); ln(c, 12, 2, 12, 6); ln(c, 12, 18, 12, 22); ln(c, 2, 12, 6, 12); ln(c, 18, 12, 22, 12);
                    fl(); c.drawCircle(12, 12, 1.6f, p); break;
                case "loop":
                    c.drawArc(new RectF(4, 4, 20, 20), 200, 250, false, p); poly(c, 17.5f, 2.5f, 17.5f, 6.5f, 13.5f, 6.5f); break;
                case "refresh":
                    c.drawArc(new RectF(4, 4, 20, 20), 30, 290, false, p); poly(c, 19.5f, 2.5f, 19.5f, 7.5f, 14.5f, 7.5f); break;
                case "image":
                    c.drawRoundRect(new RectF(3, 4, 21, 20), 2.5f, 2.5f, p); poly(c, 3, 17, 9, 12, 13, 16, 16, 13, 21, 18);
                    fl(); c.drawCircle(8.5f, 9, 1.7f, p); break;
                case "text": ln(c, 5, 6, 19, 6); ln(c, 12, 6, 12, 19); ln(c, 9, 19, 15, 19); break;
                case "chev": poly(c, 9, 5, 16, 12, 9, 19); break;
                case "back": poly(c, 15, 5, 8, 12, 15, 19); break;
                case "up": poly(c, 6, 15, 12, 9, 18, 15); break;
                case "down": poly(c, 6, 9, 12, 15, 18, 9); break;
                case "check": poly(c, 5, 12.5f, 10, 17.5f, 19, 7); break;
                case "bolt": poly(c, 13, 2, 5, 13, 11, 13, 10, 22, 19, 10, 13, 10, 13, 2); break;
                case "cpu":
                    c.drawRoundRect(new RectF(7, 7, 17, 17), 2, 2, p);
                    ln(c, 10, 3, 10, 7); ln(c, 14, 3, 14, 7); ln(c, 10, 17, 10, 21); ln(c, 14, 17, 14, 21);
                    ln(c, 3, 10, 7, 10); ln(c, 3, 14, 7, 14); ln(c, 17, 10, 21, 10); ln(c, 17, 14, 21, 14); break;
                case "mem":
                    c.drawRoundRect(new RectF(3, 7, 21, 17), 2, 2, p); ln(c, 7, 10.5f, 7, 13.5f); ln(c, 11, 10.5f, 11, 13.5f); ln(c, 15, 10.5f, 15, 13.5f);
                    ln(c, 7, 17, 7, 20); ln(c, 17, 17, 17, 20); break;
                case "battery":
                    c.drawRoundRect(new RectF(3, 8, 19, 16), 2, 2, p); ln(c, 21, 11, 21, 13); fl(); c.drawRect(5, 10, 12, 14, p); break;
                case "copy": c.drawRoundRect(new RectF(8, 8, 20, 20), 2.5f, 2.5f, p); poly(c, 16, 8, 16, 4, 4, 4, 4, 16, 8, 16); break;
                case "edit": poly(c, 4, 20, 5, 15, 16, 4, 20, 8, 9, 19, 4, 20); ln(c, 14, 6, 18, 10); break;
                case "mic": c.drawRoundRect(new RectF(9, 3, 15, 14), 3, 3, p); c.drawArc(new RectF(6, 7, 18, 19), 0, 180, false, p); ln(c, 12, 19, 12, 22); break;
                case "user": c.drawCircle(12, 8, 4, p); path.reset(); path.moveTo(4, 21); path.quadTo(12, 9, 20, 21); c.drawPath(path, p); break;
                case "smile":
                    c.drawCircle(12, 12, 9, p); c.drawArc(new RectF(7.5f, 8, 16.5f, 17), 25, 130, false, p);
                    fl(); c.drawCircle(9, 10, 1, p); c.drawCircle(15, 10, 1, p); break;
                case "eye": path.reset(); path.moveTo(2, 12); path.quadTo(12, 2, 22, 12); path.quadTo(12, 22, 2, 12); c.drawPath(path, p); c.drawCircle(12, 12, 3, p); break;
                case "export": ln(c, 12, 15, 12, 3); poly(c, 7, 8, 12, 3, 17, 8); poly(c, 4, 15, 4, 20, 20, 20, 20, 15); break;
                case "import": ln(c, 12, 3, 12, 15); poly(c, 7, 10, 12, 15, 17, 10); poly(c, 4, 15, 4, 20, 20, 20, 20, 15); break;
                case "info": c.drawCircle(12, 12, 9, p); ln(c, 12, 11, 12, 17); fl(); c.drawCircle(12, 7.6f, 1.2f, p); break;
                case "clock": c.drawCircle(12, 12, 9, p); poly(c, 12, 6.5f, 12, 12, 16, 14); break;
                case "layers": poly(c, 12, 3, 21, 8, 12, 13, 3, 8, 12, 3); poly(c, 3, 12, 12, 17, 21, 12); poly(c, 3, 16, 12, 21, 21, 16); break;
                case "crop": poly(c, 7, 2, 7, 17, 22, 17); poly(c, 2, 7, 17, 7, 17, 22); break;
                case "link": c.drawRoundRect(new RectF(3, 9, 12, 15), 3, 3, p); c.drawRoundRect(new RectF(12, 9, 21, 15), 3, 3, p); break;
                case "power": c.drawArc(new RectF(5, 5, 19, 19), -60, 300, false, p); ln(c, 12, 3, 12, 11); break;
                case "wand": ln(c, 4, 20, 15, 9); ln(c, 15, 4, 15, 8); ln(c, 13, 6, 17, 6); ln(c, 19, 10, 19, 14); ln(c, 17, 12, 21, 12); break;
                case "hand": poly(c, 8, 21, 5, 13, 7, 12, 9, 15, 9, 4, 11.5f, 4, 11.5f, 11, 13.5f, 11, 13.5f, 5, 16, 5, 16, 12, 18.5f, 12, 18.5f, 21, 8, 21); break;
                default: c.drawCircle(12, 12, 8, p);
            }
            c.restore();
        }

        void draw2(Canvas c, float x, float y, float size) { setBounds((int) x, (int) y, (int) (x + size), (int) (y + size)); draw(c); }

        @Override public void setAlpha(int a) { p.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter f) { p.setColorFilter(f); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private ImageView icon(String n, int color, float sizeDp) {
        ImageView v = new ImageView(this);
        v.setImageDrawable(new Ico(n, color));
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return v;
    }

    private GradientDrawable grad(int c1, int c2, float rDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{c1, c2});
        g.setCornerRadius(dp(rDp));
        return g;
    }

    private GradientDrawable pill(int fill, int stroke) {
        GradientDrawable g = rect(fill, 100, stroke, stroke == 0 ? 0 : 1);
        return g;
    }

    /** Лёгкая анимация нажатия: кнопка чуть «проседает» и пружинит назад. */
    private void pressable(final View v) {
        v.setOnTouchListener((x, e) -> {
            if (lowAnim()) return false;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: x.animate().scaleX(0.95f).scaleY(0.95f).setDuration(70).start(); break;
                case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
                    x.animate().scaleX(1f).scaleY(1f).setDuration(180).setInterpolator(new OvershootInterpolator(2.2f)).start(); break;
            }
            return false;
        });
    }

    private void popIn(View v) {
        if (lowAnim()) return;
        v.setAlpha(0f); v.setScaleX(0.92f); v.setScaleY(0.92f);
        v.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(240).setInterpolator(new OvershootInterpolator(1.2f)).start();
    }

    private void slideIn(View v, float fromDp) {
        if (lowAnim()) return;
        v.setAlpha(0f); v.setTranslationX(dp(fromDp));
        v.animate().alpha(1f).translationX(0f).setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
    }

    private boolean lowAnim() { return prefs != null && prefs.getBoolean("ps_noanim", false) && gameMode; }

    /** Кнопка с иконкой слева и подписью. */
    private LinearLayout ibtn(String ic, String text, int fill, float sp, View.OnClickListener l) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER);
        r.setPadding(dp(12), dp(10), dp(12), dp(10));
        r.setBackground(rect(fill, 11, 0, 0));
        if (ic != null) r.addView(icon(ic, C_TEXT, 17), lin(dp(17), dp(17), 0, 0, 0, text.isEmpty() ? 0 : dp(7), 0));
        if (!text.isEmpty()) r.addView(tv(text, sp, C_TEXT, true));
        r.setOnClickListener(l);
        pressable(r);
        return r;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(12), dp(12), dp(12), dp(12));
        c.setBackground(rect(C_CARD, 14, C_STROKE, 1));
        return c;
    }

    private TextView hint(String s) {
        TextView t = tv(s, 11.5f, C_MUTED, false);
        t.setLineSpacing(0, 1.1f);
        return t;
    }

    private EditText field(String text, String hintText, boolean number) {
        EditText et = new EditText(this);
        et.setText(text);
        et.setHint(hintText);
        et.setTextColor(C_TEXT);
        et.setHintTextColor(0xFF5E5E6A);
        et.setTextSize(13);
        et.setSingleLine(true);
        if (number) et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setBackground(rect(C_CARD2, 10, C_STROKE, 1));
        et.setPadding(dp(12), dp(10), dp(12), dp(10));
        return et;
    }

    private View gap(float dpH) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(dpH)));
        return v;
    }

    /** Переключатель-«таблетка» с анимацией. */
    private View toggle(boolean on, final java.util.function.Consumer<Boolean> cb) {
        final FrameLayout f = new FrameLayout(this);
        final View knob = new View(this);
        knob.setBackground(rect(0xFFFFFFFF, 20, 0, 0));
        f.addView(knob, new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER_VERTICAL));
        final boolean[] st = {on};
        final Runnable apply = () -> {
            f.setBackground(grad(st[0] ? C_RED : 0xFF3A3A44, st[0] ? C_HI : 0xFF3A3A44, 20));
            float tx = st[0] ? dp(22) : dp(3);
            if (lowAnim()) knob.setTranslationX(tx); else knob.animate().translationX(tx).setDuration(160).start();
        };
        knob.setTranslationX(on ? dp(22) : dp(3));
        f.setBackground(grad(on ? C_RED : 0xFF3A3A44, on ? C_HI : 0xFF3A3A44, 20));
        f.setOnClickListener(v -> { st[0] = !st[0]; apply.run(); cb.accept(st[0]); });
        f.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(24)));
        return f;
    }

    /** Строка настройки: заголовок + пояснение слева, переключатель справа. */
    private LinearLayout toggleRow(String title, String sub, boolean on, java.util.function.Consumer<Boolean> cb) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(6), 0, dp(6));
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.addView(tv(title, 13, C_TEXT, true));
        if (sub != null && !sub.isEmpty()) t.addView(hint(sub));
        r.addView(t, lin(0, WRAP, 1f, 0, 0, dp(10), 0));
        r.addView(toggle(on, cb));
        return r;
    }

    /** Ползунок с подписью значения. */
    private LinearLayout sliderRow(String title, final int min, final int max, int val, final String unit, final java.util.function.IntConsumer cb) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(0, dp(6), 0, dp(6));
        LinearLayout h = new LinearLayout(this);
        h.addView(tv(title, 13, C_TEXT, true), lin(0, WRAP, 1f, 0, 0, 0, 0));
        final TextView vt = tv(val + unit, 12, C_HI, true);
        h.addView(vt);
        r.addView(h);
        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(Math.max(0, Math.min(max - min, val - min)));
        try {
            sb.getProgressDrawable().setColorFilter(new PorterDuffColorFilter(C_RED, PorterDuff.Mode.SRC_IN));
            sb.getThumb().setColorFilter(new PorterDuffColorFilter(C_HI, PorterDuff.Mode.SRC_IN));
        } catch (Throwable ignored) { }
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) { vt.setText((p + min) + unit); if (fromUser) cb.accept(p + min); }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        r.addView(sb, lin(MATCH, WRAP, 0, 0, dp(2), 0, 0));
        return r;
    }

    /** Заголовок раздела внутри вкладки: иконка + капс. */
    private LinearLayout sect(String ic, String title) {
        LinearLayout h = new LinearLayout(this);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(0, dp(16), 0, dp(7));
        h.addView(icon(ic, C_HI, 15), lin(dp(15), dp(15), 0, 0, 0, dp(7), 0));
        TextView t = tv(title, 11, C_MUTED, true);
        t.setLetterSpacing(0.14f);
        h.addView(t);
        return h;
    }


    // ==================================================================================================
    //  НОВОЕ: ЖИЗНЕННЫЙ ЦИКЛ, ОВЕРЛЕЙ-ГЕЙМПАД, МЕНЮ, WEBVIEW В ОВЕРЛЕЕ
    // ==================================================================================================

    private static MainActivity self;
    private volatile boolean resumed, gameMode, relayFromGame, overlayOff;
    private static Intent relayIntent;
    private static int relayRc;
    interface ResultCb { void on(int res, Intent data); }
    private final HashMap<Integer, ResultCb> resultCbs = new HashMap<>();

    // WebView
    private ViewGroup webOrigParent; private int webOrigIndex; private ViewGroup.LayoutParams webOrigLp;
    private FrameLayout webPark;
    private final Runnable webTick = new Runnable() {
        @Override public void run() {
            WebView w = web();
            if (w != null && gameMode && prefs.getBoolean("ps_voice", true)) { try { w.onResume(); w.resumeTimers(); } catch (Throwable ignored) { } }
            ui.postDelayed(this, Math.max(1000, prefs.getInt("ps_tick", 3000)));
        }
    };

    private WebView web() { try { return getBridge().getWebView(); } catch (Throwable t) { return null; } }

    private void webDetach() {
        WebView w = web();
        if (w == null) return;
        ViewParent p = w.getParent();
        if (p instanceof ViewGroup) {
            if (webOrigParent == null) { webOrigParent = (ViewGroup) p; webOrigIndex = webOrigParent.indexOfChild(w); webOrigLp = w.getLayoutParams(); }
            ((ViewGroup) p).removeView(w);
        }
    }

    private void webAttach(ViewGroup to, ViewGroup.LayoutParams lp) {
        WebView w = web();
        if (w == null) return;
        webDetach();
        to.addView(w, lp);
        try { w.onResume(); w.resumeTimers(); } catch (Throwable ignored) { }
    }

    private void webRestore() {
        WebView w = web();
        if (w == null || webOrigParent == null) return;
        webDetach();
        webOrigParent.addView(w, Math.max(0, Math.min(webOrigIndex, webOrigParent.getChildCount())), webOrigLp);
        removeView(webPark); webPark = null;
    }

    /** WebView уезжает в «невидимое» окно 1x1: микрофон, звук и таймеры продолжают работать, пока вы в игре. */
    private void webPark() {
        if (web() == null || webPark != null) return;
        FrameLayout f = new FrameLayout(appCtx);
        f.setClipChildren(true);
        webAttach(f, new FrameLayout.LayoutParams(screenW(), screenH()));
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(1, 1, overlayType(), FL_NF | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        try { ow().addView(f, lp); webPark = f; } catch (Exception e) { log("Не удалось спрятать WebView в оверлей: " + e.getMessage()); webRestore(); }
    }

    private void removeView(View v) { if (v == null) return; try { ow().removeViewImmediate(v); } catch (Exception ignored) { } }

    // ---------- окна-оверлеи ----------

    private boolean canOverlay() { return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this); }

    private int overlayType() {
        if (!canOverlay() && accHost() != null) return 2032; // TYPE_ACCESSIBILITY_OVERLAY
        return Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
    }

    private Context accHost() {
        try { Context m = MacroService.getInstance(); if (m != null) return m; } catch (Throwable ignored) { }
        return OverlayAccService.instance;
    }

    private WindowManager ow() {
        if (!canOverlay()) { Context a = accHost(); if (a != null) return (WindowManager) a.getSystemService(Context.WINDOW_SERVICE); }
        return wm;
    }

    private int screenW() { return rm().widthPixels; }
    private int screenH() { return rm().heightPixels; }

    private DisplayMetrics rm() {
        DisplayMetrics m = new DisplayMetrics();
        try { wm.getDefaultDisplay().getRealMetrics(m); } catch (Throwable t) { m = getResources().getDisplayMetrics(); }
        return m;
    }

    private boolean ensureOverlay() {
        if (canOverlay() || accHost() != null) return true;
        toast("Разрешите показ поверх других приложений");
        try { relay(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())), RC_OVERLAY); } catch (Exception ignored) { }
        return false;
    }

    // ---------- вызов системных окон из игры ----------

    /** Запуск системного окна (выбор файла, разрешение записи). Если вы в игре — приложение выйдет вперёд и вернёт вас обратно. */
    private void relay(Intent i, int rc) {
        if (resumed) {
            try { startActivityForResult(i, rc); } catch (Exception e) { toast("Не удалось открыть окно: " + e.getMessage()); }
            return;
        }
        relayIntent = i; relayRc = rc;
        try {
            Intent b = new Intent(appCtx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            b.putExtra(EXTRA_RELAY, rc);
            appCtx.startActivity(b);
        } catch (Exception e) { toast("Не удалось открыть приложение: " + e.getMessage()); }
    }

    private void relay(Intent i, int rc, ResultCb cb) { resultCbs.put(rc, cb); relay(i, rc); }

    private void handleRelay(Intent i) {
        if (i == null || !i.hasExtra(EXTRA_RELAY) || relayIntent == null) return;
        Intent r = relayIntent; relayIntent = null;
        relayFromGame = true;
        i.removeExtra(EXTRA_RELAY);
        try { startActivityForResult(r, relayRc); } catch (Exception e) { toast("Не удалось открыть окно: " + e.getMessage()); relayFromGame = false; moveTaskToBack(true); }
    }

    // ---------- жизненный цикл ----------

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        self = this;
        appCtx = getApplicationContext();
        wm = (WindowManager) appCtx.getSystemService(Context.WINDOW_SERVICE);
        prefs = getSharedPreferences("arcade_mp", MODE_PRIVATE);
        logFile = new File(getFilesDir(), "arcade.log");
        try { if (logFile.length() > 200 * 1024) logFile.delete(); } catch (Exception ignored) { }
        final Thread.UncaughtExceptionHandler prevH = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try { writeLogFile("CRASH в потоке " + t.getName() + ": " + Log.getStackTraceString(e)); } catch (Throwable ignored) { }
            if (prevH != null) prevH.uncaughtException(t, e);
        });
        useVpn = prefs.getBoolean("use_vpn", false);
        if (prefs.getBoolean("active", false)) {
            status = "Прошлая сессия оборвалась (краш или система убила приложение).\nПоследний статус: " + prefs.getString("last", "?")
                    + "\n\nХвост журнала прошлой сессии:\n" + readLogTail(30);
            prefs.edit().putBoolean("active", false).apply();
        }
        try {
            WebView w = getBridge().getWebView();
            w.addJavascriptInterface(new ArcadeJs(), "ArcadeNative");
            w.getSettings().setMediaPlaybackRequiresUserGesture(false);
        } catch (Throwable t) { log("Не удалось подключить мост к веб-части: " + t.getClass().getSimpleName() + " " + t.getMessage()); }
        ArrayList<String> need = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.POST_NOTIFICATIONS);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.RECORD_AUDIO);
        if (!need.isEmpty()) { try { ActivityCompat.requestPermissions(this, need.toArray(new String[0]), RC_PERMS); } catch (Exception ignored) { } }
        loadMacroState();
        handleRelay(getIntent());
        if (!canOverlay() && accHost() == null && !prefs.getBoolean("ov_asked", false)) {
            prefs.edit().putBoolean("ov_asked", true).apply();
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Оверлей поверх игры")
                    .setMessage("Чтобы при выходе из приложения поверх игры появлялась кнопка-геймпад, разрешите показ поверх других приложений.")
                    .setPositiveButton("Разрешить", (d, w) -> relay(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())), RC_OVERLAY))
                    .setNegativeButton("Позже", null).show();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleRelay(intent);
    }

    @Override
    public void onStart() {
        super.onStart();
        resumed = true;
        leaveGameMode();
    }

    @Override
    public void onResume() {
        super.onResume();
        resumed = true;
        ensureKeepService();
    }

    @Override
    public void onStop() {
        super.onStop();
        resumed = false;
        if (!isFinishing() && !isChangingConfigurations()) enterGameMode();
    }

    @Override
    public void onDestroy() {
        if (isFinishing()) {
            closeEverything();
            if (mode != M_IDLE) stopSession("Остановлено");
            if (serverStandalone) stopBuiltinServer();
            self = null;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (menuRoot != null && menuInApp) { hideMenu(); return; }
        super.onBackPressed();
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        ensureKeepService();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == RC_PICK && res == RESULT_OK && data != null && data.getData() != null) {
            final Uri u = data.getData();
            new Thread(() -> importWorld(u), "mp-import").start();
        } else if ((req == RC_PLUGIN || req == RC_PACK) && res == RESULT_OK && data != null && data.getData() != null) {
            final Uri u2 = data.getData();
            final boolean isPlugin = req == RC_PLUGIN;
            new Thread(() -> installFile(u2, isPlugin), "mp-install").start();
        } else if (req == RC_VPN) {
            vpnAsked = false;
            if (res == RESULT_OK) { log("Разрешение VPN выдано"); vpnDenied = false; }
            else { vpnDenied = true; log("Разрешение VPN отклонено — работаю без него"); }
            ui.post(this::startHost);
        } else if (req == RC_PROJ) {
            onProjectionResult(res, data);
        } else {
            ResultCb cb = resultCbs.remove(req);
            if (cb != null) cb.on(res, data);
        }
        if (relayFromGame) { relayFromGame = false; ui.postDelayed(() -> moveTaskToBack(true), 250); }
    }

    private void enterGameMode() {
        if (overlayOff) return;
        gameMode = true;
        if (!canOverlay() && accHost() == null) return;
        ensureKeepService();
        webPark();
        showBubble();
        showMacroButtons();
        ui.removeCallbacks(webTick);
        ui.postDelayed(webTick, 1500);
        applyPerfProfile();
    }

    private void leaveGameMode() {
        gameMode = false;
        overlayOff = false;
        ui.removeCallbacks(webTick);
        hideMenu();
        hideBubble();
        hideMacroButtons();
        webRestore();
    }

    private void closeEverything() {
        stopAllMacros();
        try { stopRecording(); } catch (Throwable ignored) { }
        hideMenu(); hideBubble(); hideMacroButtons(); hideCaptureOverlays();
        ui.removeCallbacks(webTick);
        gameMode = false;
        webRestore();
        try { stopService(new Intent(this, MpService.class)); } catch (Exception ignored) { }
        try { stopService(new Intent(this, CaptureService.class)); } catch (Exception ignored) { }
    }

    private void ensureKeepService() {
        if (!gameMode && !resumed) return;
        startKeepAlive();
    }

    // ---------- мост для HTML ----------

    private class ArcadeJs {
        @android.webkit.JavascriptInterface public void openPanel() { ui.post(() -> showMenu(true)); }
        @android.webkit.JavascriptInterface public String getPublicAddr() { return prefs.getString("pl_addr", "").trim(); }
        @android.webkit.JavascriptInterface public String getStatus() {
            try {
                JSONObject o = new JSONObject();
                o.put("running", mode != M_IDLE || srvAlive());
                o.put("code", pretty(code));
                o.put("agent", agentState);
                o.put("macro", macroRunning());
                o.put("rec", recActive());
                return o.toString();
            } catch (Exception e) { return "{}"; }
        }
    }

    // ---------- статус / уведомление ----------

    private void toast(String s) { ui.post(() -> Toast.makeText(appCtx, s, Toast.LENGTH_SHORT).show()); }

    private void setStatus(String s) {
        status = s;
        try { prefs.edit().putString("last", s).apply(); } catch (Exception ignored) { }
        pushText();
    }

    private void copyCode() {
        String pc = pretty(code);
        if (pc.isEmpty()) { toast("Кода ещё нет"); return; }
        try {
            ClipboardManager cm = (ClipboardManager) appCtx.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("arcade_code", pc));
            toast("Код скопирован");
        } catch (Exception e) { toast("Не удалось скопировать"); }
    }

    private void updateNotification() {
        if (mode == M_IDLE) return;
        try {
            NotificationManager nm = (NotificationManager) appCtx.getSystemService(Context.NOTIFICATION_SERVICE);
            Intent oi = new Intent(appCtx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pi = PendingIntent.getActivity(appCtx, 1, oi, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
            Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(appCtx, "arcade_mp") : new Notification.Builder(appCtx);
            String pc = pretty(code);
            int icon = appCtx.getApplicationInfo().icon;
            b.setContentTitle(pc.isEmpty() ? "ARCADE — мультиплеер" : "Код комнаты: " + pc)
                    .setContentText(status.split("\n")[0])
                    .setStyle(new Notification.BigTextStyle().bigText(status))
                    .setSmallIcon(icon != 0 ? icon : android.R.drawable.ic_dialog_info)
                    .setContentIntent(pi).setOnlyAlertOnce(true).setOngoing(true);
            nm.notify(77, b.build());
        } catch (Exception e) { Log.w(TAG, "notify", e); }
    }

    /** Обновляет все видимые тексты (статус, код, консоль, кнопки сервера, значки на геймпаде). */
    private void pushText() {
        ui.post(() -> {
            updateNotification();
            String pc = pretty(code);
            for (TextView t : statusViews) t.setText(status);
            for (TextView t : codeViews) { t.setText(pc); t.setVisibility(pc.isEmpty() ? View.GONE : View.VISIBLE); }
            if (formBox != null && sessionBox != null) {
                formBox.setVisibility(mode == M_IDLE ? View.VISIBLE : View.GONE);
                sessionBox.setVisibility(mode == M_IDLE ? View.GONE : View.VISIBLE);
            }
            boolean sa = srvAlive();
            if (consoleBox != null) consoleBox.setVisibility(sa || (mode == M_HOST && builtin) ? View.VISIBLE : View.GONE);
            if (srvToggleBtn != null) {
                srvToggleBtn.setText(srvBusy ? "Сервер запускается…" : (sa ? "Выключить сервер" : "Включить сервер без комнаты"));
                srvToggleBtn.setBackground(rect(sa ? C_RED : 0xFF2A7A4B, 11, 0, 0));
            }
            refreshBubble();
        });
    }

    // ---------- кнопка-геймпад ----------

    private BubbleView bubble;
    private WindowManager.LayoutParams bubbleLp;
    private WindowManager bubbleWm;

    private final class BubbleView extends View {
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG), dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Ico glyph = new Ico("gamepad", 0xFFFFFFFF);
        float pulse;
        ValueAnimator anim;
        boolean active, rec;
        float dx, dy; int ox, oy; boolean moved, longDone;
        final Runnable lp = () -> { longDone = true; vib(); onBubbleLong(); };

        BubbleView(Context c) {
            super(c);
            setLayerType(LAYER_TYPE_SOFTWARE, null);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(dp(2));
            dot.setColor(0xFFFFFFFF);
        }

        void setActive(boolean a, boolean r) {
            active = a; rec = r;
            boolean need = (a || r) && !lowAnim();
            if (need && anim == null) {
                anim = ValueAnimator.ofFloat(0f, 1f);
                anim.setDuration(1200);
                anim.setRepeatCount(ValueAnimator.INFINITE);
                anim.addUpdateListener(v -> { pulse = (float) v.getAnimatedValue(); invalidate(); });
                anim.start();
            } else if (!need && anim != null) { anim.cancel(); anim = null; pulse = 0; }
            invalidate();
        }

        @Override protected void onDetachedFromWindow() { if (anim != null) { anim.cancel(); anim = null; } super.onDetachedFromWindow(); }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), cx = w / 2f, r = w / 2f - dp(5);
            fill.setShader(new LinearGradient(0, 0, w, w, rec ? 0xFFD81B3A : C_RED, C_HI, Shader.TileMode.CLAMP));
            fill.setShadowLayer(dp(5), 0, dp(2), 0x80000000);
            c.drawCircle(cx, cx, r, fill);
            if (active || rec) {
                ring.setColor(rec ? 0xFFFF4B5C : C_HI);
                ring.setAlpha((int) (200 * (1f - pulse)));
                c.drawCircle(cx, cx, r + dp(1) + pulse * dp(5), ring);
            }
            int g = dp(26);
            glyph.setBounds((int) (cx - g / 2f), (int) (cx - g / 2f), (int) (cx + g / 2f), (int) (cx + g / 2f));
            glyph.draw(c);
            if (rec) { dot.setColor(0xFFFFFFFF); c.drawCircle(w - dp(9), dp(9), dp(5), dot); dot.setColor(0xFFFF2D3F); c.drawCircle(w - dp(9), dp(9), dp(3.2f), dot); }
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dx = e.getRawX(); dy = e.getRawY(); ox = bubbleLp.x; oy = bubbleLp.y; moved = false; longDone = false;
                    animate().alpha(1f).scaleX(0.92f).scaleY(0.92f).setDuration(80).start();
                    postDelayed(lp, 600);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(e.getRawX() - dx) > dp(8) || Math.abs(e.getRawY() - dy) > dp(8)) { moved = true; removeCallbacks(lp); }
                    if (moved) {
                        bubbleLp.x = (int) (ox + e.getRawX() - dx); bubbleLp.y = (int) (oy + e.getRawY() - dy);
                        try { bubbleWm.updateViewLayout(this, bubbleLp); } catch (Exception ignored) { }
                    }
                    return true;
                case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
                    removeCallbacks(lp);
                    animate().scaleX(1f).scaleY(1f).setDuration(200).setInterpolator(new OvershootInterpolator(2f)).start();
                    if (moved) snapBubble();
                    else if (!longDone && e.getActionMasked() == MotionEvent.ACTION_UP) toggleMenu();
                    return true;
            }
            return true;
        }
    }

    private void vib() {
        if (!prefs.getBoolean("ps_haptic", true)) return;
        try {
            Vibrator v = (Vibrator) appCtx.getSystemService(Context.VIBRATOR_SERVICE);
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE)); else v.vibrate(25);
        } catch (Throwable ignored) { }
    }

    private void showBubble() {
        if (bubble != null) return;
        int size = dp(prefs.getInt("ps_bsize", 58));
        bubbleWm = ow();
        bubble = new BubbleView(appCtx);
        bubbleLp = new WindowManager.LayoutParams(size, size, overlayType(), FL_NF | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
        bubbleLp.gravity = Gravity.TOP | Gravity.START;
        bubbleLp.x = prefs.getInt("b_x", 0);
        bubbleLp.y = prefs.getInt("b_y", dp(160));
        bubble.setAlpha(prefs.getInt("ps_balpha", 100) / 100f);
        try { bubbleWm.addView(bubble, bubbleLp); popIn(bubble); } catch (Exception e) { bubble = null; log("Кнопка-геймпад не показана: " + e.getMessage()); return; }
        refreshBubble();
    }

    private void hideBubble() {
        if (bubble == null) return;
        try { bubbleWm.removeViewImmediate(bubble); } catch (Exception ignored) { }
        bubble = null;
    }

    private void refreshBubble() {
        final BubbleView b = bubble;
        if (b != null) b.setActive(macroRunning() || mode != M_IDLE, recActive());
    }

    private void snapBubble() {
        final int size = bubbleLp.width, W = screenW();
        final int from = bubbleLp.x, to = (from + size / 2 < W / 2) ? 0 : W - size;
        bubbleLp.y = Math.max(0, Math.min(screenH() - size, bubbleLp.y));
        ValueAnimator a = ValueAnimator.ofInt(from, to);
        a.setDuration(lowAnim() ? 1 : 220);
        a.setInterpolator(new DecelerateInterpolator());
        a.addUpdateListener(v -> {
            if (bubble == null) return;
            bubbleLp.x = (int) v.getAnimatedValue();
            try { bubbleWm.updateViewLayout(bubble, bubbleLp); } catch (Exception ignored) { }
        });
        a.start();
        prefs.edit().putInt("b_x", to).putInt("b_y", bubbleLp.y).apply();
    }

    private void onBubbleLong() {
        if (macroRunning()) { stopAllMacros(); toast("Макросы остановлены"); }
        else if (recActive()) { toggleRecPause(); toast(recPaused ? "Запись на паузе" : "Запись продолжена"); }
        else toast("Нажмите, чтобы открыть меню");
    }

    // ---------- меню ----------

    private FrameLayout menuRoot, menuLayer, contentHost;
    private LinearLayout menuPanel;
    private boolean menuInApp;
    private int curTab = -1;
    private View tabIndicator;
    private LinearLayout railBox;
    private final ArrayList<LinearLayout> railItems = new ArrayList<>();
    private TextView menuTitle, menuSub;
    private ImageView menuTitleIco;

    private static final String[] TAB_NAMES = {"WebView", "Настройки", "Макросы", "Запись", "Серверы", "Закрыть"};
    private static final String[] TAB_ICONS = {"web", "settings", "macro", "rec", "server", "power"};

    private void toggleMenu() { if (menuRoot != null) hideMenu(); else showMenu(false); }

    private final class MenuRoot extends FrameLayout {
        MenuRoot(Context c) { super(c); }
        @Override public boolean dispatchKeyEvent(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.KEYCODE_BACK && e.getAction() == KeyEvent.ACTION_UP) {
                if (menuLayer != null && menuLayer.getChildCount() > 0) closeModal(); else if (subBack != null) { Runnable r = subBack; r.run(); } else hideMenu();
                return true;
            }
            return super.dispatchKeyEvent(e);
        }
    }

    /** Показ меню: поверх игры (оверлей) или, если вы в приложении, прямо внутри него. */
    private void showMenu(boolean fromApp) {
        if (menuRoot != null) return;
        boolean inApp = fromApp && resumed;
        if (!inApp && !ensureOverlay()) return;
        menuInApp = inApp;
        Context cx = inApp ? this : appCtx;
        menuRoot = new MenuRoot(cx);
        menuRoot.setBackgroundColor(0xB0000000);
        menuRoot.setFocusable(true);
        menuRoot.setFocusableInTouchMode(true);
        menuRoot.setOnClickListener(v -> hideMenu());

        int pw = Math.min(screenW() - dp(16), dp(740)), ph = Math.min(screenH() - dp(16), dp(480));
        menuPanel = new LinearLayout(this);
        menuPanel.setOrientation(LinearLayout.HORIZONTAL);
        menuPanel.setBackground(grad(0xFF1A1A20, 0xFF0F0F12, 22));
        menuPanel.setClipToOutline(true);
        menuPanel.setClickable(true);
        menuRoot.addView(menuPanel, new FrameLayout.LayoutParams(pw, ph, Gravity.CENTER));

        // левая рейка вкладок
        final FrameLayout railWrap = new FrameLayout(this);
        railWrap.setBackgroundColor(C_PANEL);
        railBox = new LinearLayout(this);
        railBox.setOrientation(LinearLayout.VERTICAL);
        railBox.setPadding(dp(5), dp(8), dp(5), dp(8));
        railItems.clear();
        int itemH = Math.max(dp(46), Math.min(dp(62), (ph - dp(16)) / 6));
        for (int i = 0; i < 6; i++) {
            final int idx = i;
            LinearLayout it = new LinearLayout(this);
            it.setOrientation(LinearLayout.VERTICAL);
            it.setGravity(Gravity.CENTER);
            it.addView(icon(TAB_ICONS[i], C_MUTED, 21));
            TextView t = tv(TAB_NAMES[i], 9, C_MUTED, true);
            t.setSingleLine(true);
            t.setGravity(Gravity.CENTER);
            it.addView(t, lin(WRAP, WRAP, 0, 0, dp(2), 0, 0));
            it.setOnClickListener(v -> { vib(); selectTab(idx); });
            pressable(it);
            railItems.add(it);
            railBox.addView(it, lin(MATCH, itemH, 0, 0, 0, 0, dp(2)));
        }
        railWrap.addView(railBox, new FrameLayout.LayoutParams(dp(66), MATCH));
        tabIndicator = new View(this);
        tabIndicator.setBackground(grad(C_RED, C_HI, 3));
        railWrap.addView(tabIndicator, new FrameLayout.LayoutParams(dp(3), itemH - dp(14), Gravity.START | Gravity.TOP));
        menuPanel.addView(railWrap, new LinearLayout.LayoutParams(dp(66), MATCH));

        // правая часть: шапка + контент
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(14), dp(8), dp(8), dp(8));
        menuTitleIco = icon("gamepad", C_HI, 20);
        head.addView(menuTitleIco, lin(dp(20), dp(20), 0, 0, 0, dp(9), 0));
        LinearLayout tt = new LinearLayout(this);
        tt.setOrientation(LinearLayout.VERTICAL);
        menuTitle = tv("ARCADE", 15, C_TEXT, true);
        menuSub = tv("", 10.5f, C_MUTED, false);
        tt.addView(menuTitle); tt.addView(menuSub);
        head.addView(tt, lin(0, WRAP, 1f, 0, 0, 0, 0));
        ImageView x = icon("close", C_TEXT, 18);
        x.setPadding(dp(8), dp(8), dp(8), dp(8));
        x.setBackground(rect(C_CARD2, 12, 0, 0));
        x.setOnClickListener(v -> hideMenu());
        pressable(x);
        head.addView(x, lin(dp(36), dp(36), 0, 0, 0, 0, 0));
        right.addView(head, lin(MATCH, WRAP, 0, 0, 0, 0, 0));
        View line = new View(this);
        line.setBackgroundColor(C_STROKE);
        right.addView(line, lin(MATCH, 1, 0, 0, 0, 0, 0));
        contentHost = new FrameLayout(this);
        right.addView(contentHost, lin(MATCH, 0, 1f, 0, 0, 0, 0));
        menuPanel.addView(right, new LinearLayout.LayoutParams(0, MATCH, 1f));

        menuLayer = new FrameLayout(this);
        menuRoot.addView(menuLayer, new FrameLayout.LayoutParams(MATCH, MATCH));

        if (inApp) {
            addContentView(menuRoot, new ViewGroup.LayoutParams(MATCH, MATCH));
        } else {
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(MATCH, MATCH, overlayType(),
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT);
            lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
            try { ow().addView(menuRoot, lp); } catch (Exception e) { log("Меню не открылось: " + e.getMessage()); menuRoot = null; return; }
        }
        if (!lowAnim()) {
            menuRoot.setAlpha(0f);
            menuRoot.animate().alpha(1f).setDuration(160).start();
            menuPanel.setScaleX(0.9f); menuPanel.setScaleY(0.9f);
            menuPanel.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(new OvershootInterpolator(1.1f)).start();
        }
        menuRoot.requestFocus();
        curTab = -1;
        selectTab(Math.min(5, prefs.getInt("last_tab", 2)));
    }

    private void hideMenu() {
        final FrameLayout r = menuRoot;
        if (r == null) return;
        menuRoot = null; menuLayer = null; contentHost = null; subBack = null;
        webDetachIfInMenu();
        statusViews.clear(); codeViews.clear(); logViews.clear();
        formBox = null; sessionBox = null; consoleBox = null; consoleView = null; consoleScroll = null; consoleInput = null; srvToggleBtn = null;
        recTimeView = null; perfView = null;
        Runnable rm = () -> {
            try {
                if (menuInApp) { ViewParent p = r.getParent(); if (p instanceof ViewGroup) ((ViewGroup) p).removeView(r); }
                else ow().removeViewImmediate(r);
            } catch (Exception ignored) { }
        };
        if (lowAnim() || !r.isAttachedToWindow()) rm.run();
        else r.animate().alpha(0f).setDuration(130).withEndAction(rm).start();
        ui.removeCallbacks(perfTick);
        InputMethodManager im = (InputMethodManager) appCtx.getSystemService(Context.INPUT_METHOD_SERVICE);
        try { im.hideSoftInputFromWindow(r.getWindowToken(), 0); } catch (Exception ignored) { }
        if (gameMode) restoreWebPark();
    }

    private Runnable subBack;   // «назад» внутри вкладки (например, из редактора макроса в список)
    private boolean webInMenu;

    private void webDetachIfInMenu() { if (webInMenu) { webDetach(); webInMenu = false; } }

    private void restoreWebPark() {
        if (!gameMode || web() == null) return;
        if (webPark == null) webPark();
        else if (webPark.getChildCount() == 0) webAttach(webPark, new FrameLayout.LayoutParams(screenW(), screenH()));
    }

    private void selectTab(final int i) {
        if (menuRoot == null || contentHost == null) return;
        if (i == 0 && menuInApp) { hideMenu(); return; }   // уже в приложении — вы и так в WebView
        prefs.edit().putInt("last_tab", i).apply();
        subBack = null;
        webDetachIfInMenu();
        ui.removeCallbacks(perfTick);
        statusViews.clear(); codeViews.clear(); logViews.clear();
        formBox = null; sessionBox = null; consoleBox = null; srvToggleBtn = null; recTimeView = null; perfView = null;
        curTab = i;
        for (int k = 0; k < railItems.size(); k++) {
            LinearLayout it = railItems.get(k);
            boolean s = k == i;
            ((ImageView) it.getChildAt(0)).setImageDrawable(new Ico(TAB_ICONS[k], s ? 0xFFFFFFFF : C_MUTED));
            ((TextView) it.getChildAt(1)).setTextColor(s ? C_TEXT : C_MUTED);
            it.setBackground(s ? grad(0x33E5252A, 0x22FF7A45, 14) : null);
        }
        final int itemH = railItems.get(0).getLayoutParams().height + dp(2);
        float ty = dp(8) + i * itemH + dp(7);
        if (lowAnim()) tabIndicator.setTranslationY(ty); else tabIndicator.animate().translationY(ty).setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
        menuTitle.setText(TAB_NAMES[i]);
        menuTitleIco.setImageDrawable(new Ico(TAB_ICONS[i], C_HI));
        menuSub.setText("");
        contentHost.removeAllViews();
        View v;
        try {
            switch (i) {
                case 0: v = tabWeb(); break;
                case 1: v = tabSettings(); break;
                case 2: v = tabMacros(); break;
                case 3: v = tabRecord(); break;
                case 4: v = tabServers(); break;
                default: v = tabClose();
            }
        } catch (Throwable t) {
            Log.e(TAG, "tab", t);
            v = scroll(tv("Ошибка вкладки: " + t, 12, C_RED, false));
        }
        contentHost.addView(v, new FrameLayout.LayoutParams(MATCH, MATCH));
        slideIn(v, 14);
        pushText();
    }

    /** Заменяет содержимое вкладки (внутренние экраны: список > редактор). */
    private void setContent(View v, Runnable back) {
        if (contentHost == null) return;
        subBack = back;
        contentHost.removeAllViews();
        contentHost.addView(v, new FrameLayout.LayoutParams(MATCH, MATCH));
        slideIn(v, back == null ? -14 : 14);
    }

    private ScrollView scroll(View child) {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(14), dp(6), dp(14), dp(18));
        col.addView(child, new LinearLayout.LayoutParams(MATCH, WRAP));
        sv.addView(col, new ViewGroup.LayoutParams(MATCH, WRAP));
        return sv;
    }

    private LinearLayout col() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        return c;
    }

    // ---------- модальные окна внутри меню ----------

    private void modal(View body) {
        final FrameLayout l = menuLayer;
        if (l == null) return;
        FrameLayout w = new FrameLayout(this);
        w.setBackgroundColor(0xAA000000);
        w.setClickable(true);
        w.setOnClickListener(v -> closeModal());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.min(screenW() - dp(40), dp(420)), WRAP, Gravity.CENTER);
        body.setClickable(true);
        w.addView(body, lp);
        l.addView(w, new FrameLayout.LayoutParams(MATCH, MATCH));
        if (!lowAnim()) { w.setAlpha(0f); w.animate().alpha(1f).setDuration(120).start(); popIn(body); }
    }

    private void closeModal() {
        FrameLayout l = menuLayer;
        if (l == null || l.getChildCount() == 0) return;
        try { InputMethodManager im = (InputMethodManager) appCtx.getSystemService(Context.INPUT_METHOD_SERVICE); im.hideSoftInputFromWindow(l.getWindowToken(), 0); } catch (Exception ignored) { }
        l.removeViewAt(l.getChildCount() - 1);
    }

    private interface IntCb { void on(int i); }
    private interface StrCb { void on(String s); }

    private void sheet(String title, final String[] labels, final String[] icons, final IntCb cb) {
        LinearLayout b = col();
        b.setBackground(rect(C_CARD, 18, C_STROKE, 1));
        b.setPadding(dp(8), dp(12), dp(8), dp(8));
        TextView t = tv(title, 14, C_TEXT, true);
        t.setPadding(dp(8), 0, dp(8), dp(8));
        b.addView(t);
        LinearLayout list = col();
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(dp(10), dp(11), dp(10), dp(11));
            r.setBackground(rect(C_CARD2, 10, 0, 0));
            if (icons != null && icons[i] != null) r.addView(icon(icons[i], C_HI, 18), lin(dp(18), dp(18), 0, 0, 0, dp(10), 0));
            r.addView(tv(labels[i], 13, C_TEXT, false), lin(0, WRAP, 1f, 0, 0, 0, 0));
            r.setOnClickListener(v -> { closeModal(); cb.on(idx); });
            pressable(r);
            list.addView(r, lin(MATCH, WRAP, 0, 0, 0, 0, dp(4)));
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(list);
        b.addView(sv, new LinearLayout.LayoutParams(MATCH, Math.min(dp(260), WRAP)));
        modal(b);
    }

    private void prompt(String title, String hintText, String init, boolean multi, final StrCb cb) {
        LinearLayout b = col();
        b.setBackground(rect(C_CARD, 18, C_STROKE, 1));
        b.setPadding(dp(14), dp(14), dp(14), dp(12));
        b.addView(tv(title, 14, C_TEXT, true), lin(MATCH, WRAP, 0, 0, 0, 0, dp(8)));
        final EditText et = field(init, hintText, false);
        if (multi) { et.setSingleLine(false); et.setMinLines(3); et.setGravity(Gravity.TOP); }
        b.addView(et, lin(MATCH, WRAP, 0, 0, 0, 0, dp(10)));
        LinearLayout row = new LinearLayout(this);
        row.addView(ibtn(null, "Отмена", C_CARD2, 13, v -> closeModal()), lin(0, WRAP, 1f, 0, 0, dp(6), 0));
        row.addView(ibtn("check", "Готово", C_RED, 13, v -> { String s = et.getText().toString(); closeModal(); cb.on(s); }), lin(0, WRAP, 1f, 0, 0, 0, 0));
        b.addView(row);
        modal(b);
        et.requestFocus();
    }

    private void confirm(String msg, String yes, final Runnable r) {
        LinearLayout b = col();
        b.setBackground(rect(C_CARD, 18, C_STROKE, 1));
        b.setPadding(dp(14), dp(14), dp(14), dp(12));
        b.addView(tv(msg, 13, C_TEXT, false), lin(MATCH, WRAP, 0, 0, 0, 0, dp(12)));
        LinearLayout row = new LinearLayout(this);
        row.addView(ibtn(null, "Отмена", C_CARD2, 13, v -> closeModal()), lin(0, WRAP, 1f, 0, 0, dp(6), 0));
        row.addView(ibtn("check", yes, C_RED, 13, v -> { closeModal(); r.run(); }), lin(0, WRAP, 1f, 0, 0, 0, 0));
        b.addView(row);
        modal(b);
    }

    private void info(String title, String text) {
        LinearLayout b = col();
        b.setBackground(rect(C_CARD, 18, C_STROKE, 1));
        b.setPadding(dp(14), dp(14), dp(14), dp(12));
        b.addView(tv(title, 14, C_TEXT, true), lin(MATCH, WRAP, 0, 0, 0, 0, dp(8)));
        TextView t = tv(text, 12, C_MUTED, false);
        t.setTypeface(Typeface.MONOSPACE);
        ScrollView sv = new ScrollView(this);
        sv.addView(t);
        b.addView(sv, lin(MATCH, Math.min(dp(260), WRAP), 0, 0, 0, 0, dp(10)));
        b.addView(ibtn(null, "Закрыть", C_CARD2, 13, v -> closeModal()));
        modal(b);
    }

    // ---------- вкладка 1: WebView ----------

    private View tabWeb() {
        final WebView w = web();
        if (w == null) return scroll(tv("WebView недоступен", 13, C_MUTED, false));
        FrameLayout f = new FrameLayout(this);
        f.setBackgroundColor(0xFF000000);
        webAttach(f, new FrameLayout.LayoutParams(MATCH, MATCH));
        webInMenu = true;
        menuSub.setText("Приложение работает прямо здесь — микрофон и звук не прерываются");
        return f;
    }

    // ---------- вкладка 6: закрыть ----------

    private View tabClose() {
        LinearLayout c = col();
        c.addView(hint("Что сделать с оверлеем? Макросы и запись экрана при этом останавливаются."), lin(MATCH, WRAP, 0, dp(8), 0, 0, dp(12)));
        c.addView(ibtn("close", "Скрыть оверлей до следующего запуска", C_CARD2, 13, v -> { overlayOff = true; closeEverythingKeepApp(); }), lin(MATCH, WRAP, 0, 0, 0, 0, dp(8)));
        c.addView(ibtn("power", "Остановить всё и выйти из приложения", C_RED, 13, v -> confirm("Остановить сервер, макросы, запись и выйти?", "Выйти", () -> {
            closeEverything();
            if (mode != M_IDLE) stopSession("Остановлено");
            if (serverStandalone) stopBuiltinServer();
            finishAndRemoveTask();
        })), lin(MATCH, WRAP, 0, 0, 0, 0, dp(8)));
        c.addView(ibtn("back", "Вернуться в меню", C_CARD2, 13, v -> selectTab(2)), lin(MATCH, WRAP, 0, 0, 0, 0, 0));
        return scroll(c);
    }

    private void closeEverythingKeepApp() {
        stopAllMacros();
        try { stopRecording(); } catch (Throwable ignored) { }
        hideMenu(); hideBubble(); hideMacroButtons(); hideCaptureOverlays();
        ui.removeCallbacks(webTick);
        webRestore();
    }


    // ==================================================================================================
    //  НОВОЕ: МАКРОСЫ — МОДЕЛЬ, СКРИПТ, ИСПОЛНЕНИЕ, АНАЛИЗАТОРЫ, ЗАХВАТ КООРДИНАТ
    // ==================================================================================================

    static final class Macro {
        String name = "Макрос"; String script = ""; int repeat = 1; int delay = 0; int jitter = 0;
        JSONObject toJson() throws Exception { JSONObject o = new JSONObject(); o.put("name", name); o.put("script", script); o.put("repeat", repeat); o.put("delay", delay); o.put("jitter", jitter); return o; }
        static Macro from(JSONObject o) { Macro m = new Macro(); m.name = o.optString("name", "Макрос"); m.script = o.optString("script", ""); m.repeat = o.optInt("repeat", 1); m.delay = o.optInt("delay", 0); m.jitter = o.optInt("jitter", 0); return m; }
    }

    static final class Anl {
        String name = "Анализатор"; String macro = ""; String ref = ""; int thr = 85; int tol = 28; float rx = 0, ry = 0, rw = 1, rh = 1;
        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject(); o.put("name", name); o.put("macro", macro); o.put("ref", ref); o.put("thr", thr); o.put("tol", tol);
            o.put("rx", rx); o.put("ry", ry); o.put("rw", rw); o.put("rh", rh); return o;
        }
        static Anl from(JSONObject o) {
            Anl a = new Anl(); a.name = o.optString("name", "Анализатор"); a.macro = o.optString("macro", ""); a.ref = o.optString("ref", "");
            a.thr = o.optInt("thr", 85); a.tol = o.optInt("tol", 28);
            a.rx = (float) o.optDouble("rx", 0); a.ry = (float) o.optDouble("ry", 0); a.rw = (float) o.optDouble("rw", 1); a.rh = (float) o.optDouble("rh", 1);
            return a;
        }
    }

    static final class Btn {
        String macro = ""; float x = 0.85f, y = 0.5f; int size = 56; String tex = ""; String label = "";
        JSONObject toJson() throws Exception { JSONObject o = new JSONObject(); o.put("macro", macro); o.put("x", x); o.put("y", y); o.put("size", size); o.put("tex", tex); o.put("label", label); return o; }
        static Btn from(JSONObject o) { Btn b = new Btn(); b.macro = o.optString("macro", ""); b.x = (float) o.optDouble("x", 0.85); b.y = (float) o.optDouble("y", 0.5); b.size = o.optInt("size", 56); b.tex = o.optString("tex", ""); b.label = o.optString("label", ""); return b; }
    }

    static final class Cfg {
        String name = "Новый конфиг";
        final ArrayList<Macro> macros = new ArrayList<>();
        final ArrayList<Anl> an = new ArrayList<>();
        final ArrayList<Btn> btns = new ArrayList<>();
        boolean anOn = false; String trigger = ""; int delay = 700; String onFail = "stop"; String fallback = ""; int rounds = 1;
        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject(); o.put("name", name); o.put("anOn", anOn); o.put("trigger", trigger); o.put("delay", delay);
            o.put("onFail", onFail); o.put("fallback", fallback); o.put("rounds", rounds);
            JSONArray a = new JSONArray(); for (Macro m : macros) a.put(m.toJson()); o.put("macros", a);
            a = new JSONArray(); for (Anl m : an) a.put(m.toJson()); o.put("an", a);
            a = new JSONArray(); for (Btn m : btns) a.put(m.toJson()); o.put("btns", a);
            return o;
        }
        static Cfg from(JSONObject o) {
            Cfg c = new Cfg(); c.name = o.optString("name", "Конфиг"); c.anOn = o.optBoolean("anOn", false); c.trigger = o.optString("trigger", "");
            c.delay = o.optInt("delay", 700); c.onFail = o.optString("onFail", "stop"); c.fallback = o.optString("fallback", ""); c.rounds = o.optInt("rounds", 1);
            JSONArray a = o.optJSONArray("macros"); if (a != null) for (int i = 0; i < a.length(); i++) c.macros.add(Macro.from(a.optJSONObject(i)));
            a = o.optJSONArray("an"); if (a != null) for (int i = 0; i < a.length(); i++) c.an.add(Anl.from(a.optJSONObject(i)));
            a = o.optJSONArray("btns"); if (a != null) for (int i = 0; i < a.length(); i++) c.btns.add(Btn.from(a.optJSONObject(i)));
            return c;
        }
        Macro find(String n) { for (Macro m : macros) if (m.name.equalsIgnoreCase(n)) return m; return null; }
    }

    private Cfg cfg;     // активный конфиг
    private File cfgDir() { File d = new File(getFilesDir(), "macros"); d.mkdirs(); return d; }
    private File mediaDir() { File d = new File(getFilesDir(), "media"); d.mkdirs(); return d; }
    private String safeName(String n) { String s = n.trim().replaceAll("[\\\\/:*?\"<>|\\n\\r]", "_"); return s.isEmpty() ? "config" : s; }
    private File cfgFile(String name) { return new File(cfgDir(), safeName(name) + ".json"); }

    private Cfg readCfg(File f) {
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            int got = 0; while (got < b.length) { int r = in.read(b, got, b.length - got); if (r < 0) break; got += r; }
            return Cfg.from(new JSONObject(new String(b, StandardCharsets.UTF_8)));
        } catch (Exception e) { log("Конфиг " + f.getName() + " не прочитан: " + e.getMessage()); return null; }
    }

    private void saveCfg(Cfg c) {
        try (FileOutputStream o = new FileOutputStream(cfgFile(c.name))) { o.write(c.toJson().toString(1).getBytes(StandardCharsets.UTF_8)); }
        catch (Exception e) { toast("Не удалось сохранить: " + e.getMessage()); }
    }

    private void loadMacroState() {
        String act = prefs.getString("mc_active", "");
        File f = cfgFile(act.isEmpty() ? "Основной" : act);
        Cfg c = f.exists() ? readCfg(f) : null;
        if (c == null) {
            c = new Cfg(); c.name = "Основной";
            Macro m = new Macro(); m.name = "Пример";
            m.script = "# Пример: тап, пауза, свайп, цикл\nrepeat 3\n  tap 50% 50%\n  wait 400\nend\nswipe 30% 70% 70% 70% 300";
            c.macros.add(m);
            saveCfg(c);
        }
        cfg = c;
        prefs.edit().putString("mc_active", c.name).apply();
    }

    private void setActiveCfg(Cfg c) {
        stopAllMacros();
        cfg = c;
        prefs.edit().putString("mc_active", c.name).apply();
        hideMacroButtons(); if (gameMode) showMacroButtons();
    }

    private ArrayList<File> listCfgFiles() {
        File[] fs = cfgDir().listFiles((d, n) -> n.endsWith(".json"));
        ArrayList<File> l = new ArrayList<>();
        if (fs != null) { Arrays.sort(fs, (a, b) -> a.getName().compareToIgnoreCase(b.getName())); l.addAll(Arrays.asList(fs)); }
        return l;
    }

    // ---------- скрипт ----------

    static final class Node { String cmd; String[] a; ArrayList<Node> kids; int line; }
    static final class ParseEx extends Exception { final int line; ParseEx(int l, String m) { super("Строка " + l + ": " + m); line = l; } }

    private static String canon(String c) {
        switch (c) {
            case "click": case "тап": case "клик": return "tap";
            case "свайп": return "swipe";
            case "hold": case "зажать": return "long";
            case "sleep": case "ждать": case "пауза": case "delay": return "wait";
            case "loop": case "цикл": case "повтор": return "repeat";
            case "всегда": case "бесконечно": return "forever";
            case "конец": case "endloop": return "end";
            case "вызвать": case "call": return "run";
            case "анализ": return "analyze";
            case "стоп": return "stop";
            case "рандом": case "randwait": return "waitrand";
            case "сообщение": case "text": return "toast";
            default: return c;
        }
    }

    private ArrayList<Node> parseScript(String src) throws ParseEx {
        String[] lines = src.split("\n");
        ArrayList<Node> root = new ArrayList<>();
        ArrayDeque<ArrayList<Node>> st = new ArrayDeque<>();
        ArrayList<Node> cur = root;
        ArrayDeque<Integer> openAt = new ArrayDeque<>();
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i].trim();
            int h = l.indexOf('#'); if (h >= 0) l = l.substring(0, h).trim();
            if (l.isEmpty()) continue;
            String[] t = l.split("\\s+");
            String c = canon(t[0].toLowerCase(Locale.ROOT));
            Node n = new Node(); n.cmd = c; n.line = i + 1; n.a = Arrays.copyOfRange(t, 1, t.length);
            int need;
            switch (c) {
                case "tap": need = 2; break; case "swipe": need = 4; break; case "long": need = 2; break;
                case "wait": need = 1; break; case "repeat": need = 1; break; case "run": need = 1; break; case "waitrand": need = 2; break; case "jitter": need = 1; break;
                case "forever": case "end": case "analyze": case "stop": case "toast": need = 0; break;
                default: throw new ParseEx(n.line, "неизвестная команда «" + t[0] + "»");
            }
            if (n.a.length < need) throw new ParseEx(n.line, "для «" + c + "» нужно аргументов: " + need);
            if (c.equals("end")) {
                if (st.isEmpty()) throw new ParseEx(n.line, "лишний «end»");
                cur = st.pop(); openAt.pop();
            } else if (c.equals("repeat") || c.equals("forever")) {
                n.kids = new ArrayList<>(); cur.add(n); st.push(cur); openAt.push(n.line); cur = n.kids;
            } else cur.add(n);
        }
        if (!st.isEmpty()) throw new ParseEx(openAt.peek(), "цикл не закрыт — добавьте «end»");
        return root;
    }

    private int num(String s, int def) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; } }

    private int pxv(String s, boolean x) {
        s = s.trim();
        try { if (s.endsWith("%")) return Math.round(Float.parseFloat(s.substring(0, s.length() - 1)) / 100f * (x ? screenW() : screenH())); } catch (Exception ignored) { }
        return num(s, 0);
    }

    private int jit(int v, int j) { return j <= 0 ? v : v + rnd.nextInt(j * 2 + 1) - j; }

    // ---------- исполнение ----------

    private final ConcurrentHashMap<String, Run> runs = new ConcurrentHashMap<>();
    private volatile boolean anBusy;
    private volatile Thread anThread;

    private boolean macroRunning() { return !runs.isEmpty() || anBusy; }

    private void stopAllMacros() {
        for (Run r : runs.values()) r.stopNow();
        runs.clear();
        Thread t = anThread; anThread = null; anBusy = false;
        if (t != null) t.interrupt();
        ui.post(this::refreshBubble);
    }

    private boolean needAcc() {
        MacroService s = null;
        try { s = MacroService.getInstance(); } catch (Throwable ignored) { }
        if (s != null) return true;
        toast("Включите службу «Макросы» в специальных возможностях");
        try { relay(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), 190); } catch (Exception ignored) { }
        return false;
    }

    private Run startMacro(Cfg c, Macro m, boolean afterAnalyze) {
        if (!needAcc()) return null;
        try { parseScript(m.script); } catch (ParseEx e) { toast(e.getMessage()); return null; }
        Run old = runs.get(m.name);
        if (old != null) old.stopNow();
        Run r = new Run(c, m, afterAnalyze);
        runs.put(m.name, r);
        r.start();
        ui.post(this::refreshBubble);
        return r;
    }

    final class Run extends Thread {
        final Cfg c; final Macro m; final boolean fromAnalysis; volatile boolean stop; int jit;
        Run(Cfg c, Macro m, boolean fa) { super("macro-" + m.name); this.c = c; this.m = m; this.fromAnalysis = fa; jit = m.jitter; }
        void stopNow() { stop = true; interrupt(); }
        boolean sleepMs(long ms) {
            long end = SystemClock.uptimeMillis() + ms;
            while (!stop) {
                long left = end - SystemClock.uptimeMillis();
                if (left <= 0) return true;
                try { Thread.sleep(Math.min(left, 50)); } catch (InterruptedException e) { return !stop; }
            }
            return false;
        }
        void gest(int x1, int y1, int x2, int y2, long dur, boolean click) {
            final MacroService s = MacroService.getInstance();
            if (s == null) { stop = true; toast("Служба макросов выключена"); return; }
            final int ax = jit(x1, jit), ay = jit(y1, jit), bx = jit(x2, jit), by = jit(y2, jit);
            ui.post(() -> { try { if (click) s.performClick(ax, ay); else s.performSwipe(ax, ay, bx, by, dur); } catch (Throwable t) { Log.e(TAG, "gesture", t); } });
            if (!click) sleepMs(dur + 40); else sleepMs(30);
        }
        boolean exec(ArrayList<Node> list, int depth) {
            if (depth > 6) { toast("Слишком глубокая вложенность"); return false; }
            for (Node n : list) {
                if (stop) return false;
                switch (n.cmd) {
                    case "tap":
                        if (n.a.length >= 3 && num(n.a[2], 0) > 0) gest(pxv(n.a[0], true), pxv(n.a[1], false), pxv(n.a[0], true) + 1, pxv(n.a[1], false), num(n.a[2], 0), false);
                        else gest(pxv(n.a[0], true), pxv(n.a[1], false), 0, 0, 0, true);
                        break;
                    case "long": { int x = pxv(n.a[0], true), y = pxv(n.a[1], false); gest(x, y, x + 1, y, Math.max(350, n.a.length > 2 ? num(n.a[2], 600) : 600), false); break; }
                    case "swipe": gest(pxv(n.a[0], true), pxv(n.a[1], false), pxv(n.a[2], true), pxv(n.a[3], false), Math.max(60, n.a.length > 4 ? num(n.a[4], 300) : 300), false); break;
                    case "wait": sleepMs(Math.max(0, num(n.a[0], 100))); break;
                    case "waitrand": { int a = num(n.a[0], 100), b = num(n.a[1], a); sleepMs(a + (b > a ? rnd.nextInt(b - a + 1) : 0)); break; }
                    case "jitter": jit = Math.max(0, num(n.a[0], 0)); break;
                    case "toast": { StringBuilder sb = new StringBuilder(); for (String s : n.a) sb.append(s).append(' '); toast(sb.toString().trim()); break; }
                    case "stop": stop = true; return false;
                    case "analyze": if (!analyzeOnce(c, this)) { /* нет совпадений — идём дальше */ } break;
                    case "run": {
                        Macro sub = c.find(String.join(" ", n.a));
                        if (sub == null) { toast("Макрос «" + String.join(" ", n.a) + "» не найден"); break; }
                        try { if (!exec(parseScript(sub.script), depth + 1)) return false; } catch (ParseEx e) { toast(e.getMessage()); return false; }
                        break;
                    }
                    case "repeat": { int k = Math.max(0, num(n.a[0], 1)); for (int i = 0; i < k && !stop; i++) if (!exec(n.kids, depth + 1)) return false; break; }
                    case "forever": while (!stop) { if (!exec(n.kids, depth + 1)) return false; if (!sleepMs(1)) return false; } break;
                }
            }
            return !stop;
        }
        @Override public void run() {
            boolean ok = false;
            try {
                if (m.delay > 0 && !sleepMs(m.delay)) return;
                ArrayList<Node> tree = parseScript(m.script);
                int reps = m.repeat;
                for (int i = 0; (reps <= 0 || i < reps) && !stop; i++) if (!exec(tree, 0)) break;
                ok = !stop;
            } catch (ParseEx e) { toast(e.getMessage()); }
            catch (Throwable t) { Log.e(TAG, "macro", t); }
            finally {
                runs.remove(m.name, this);
                ui.post(MainActivity.this::refreshBubble);
                ui.post(MainActivity.this::updateMacroBtns);
            }
            if (ok && !fromAnalysis && c.anOn && !c.trigger.isEmpty() && c.trigger.equalsIgnoreCase(m.name)) startAnalysisLoop(c);
        }
    }

    // ---------- анализаторы ----------

    private final HashMap<String, int[]> sigCache = new HashMap<>();

    /** Серая миниатюра 48x48 нужной области экрана. */
    private int[] sig(Bitmap b, float rx, float ry, float rw, float rh) {
        int x = Math.max(0, Math.min(b.getWidth() - 2, Math.round(rx * b.getWidth())));
        int y = Math.max(0, Math.min(b.getHeight() - 2, Math.round(ry * b.getHeight())));
        int w = Math.max(2, Math.min(b.getWidth() - x, Math.round(rw * b.getWidth())));
        int h = Math.max(2, Math.min(b.getHeight() - y, Math.round(rh * b.getHeight())));
        Bitmap c = Bitmap.createBitmap(b, x, y, w, h);
        Bitmap s = Bitmap.createScaledBitmap(c, 48, 48, true);
        int[] px = new int[48 * 48];
        s.getPixels(px, 0, 48, 0, 0, 48, 48);
        int[] g = new int[px.length];
        for (int i = 0; i < px.length; i++) { int p = px[i]; g[i] = (((p >> 16) & 255) * 30 + ((p >> 8) & 255) * 59 + (p & 255) * 11) / 100; }
        if (s != c && s != b) s.recycle();
        if (c != b) c.recycle();
        return g;
    }

    private Bitmap refBitmap(Anl a) {
        if (a.ref.isEmpty()) return null;
        File f = new File(mediaDir(), a.ref);
        if (!f.exists()) return null;
        return BitmapFactory.decodeFile(f.getAbsolutePath());
    }

    /** Похожесть экрана на эталон в процентах (доля пикселей, отличающихся не больше допуска). */
    private float similarity(Bitmap cur, Anl a) {
        Bitmap ref = refBitmap(a);
        if (ref == null || cur == null) return -1f;
        try {
            int[] r = sig(ref, a.rx, a.ry, a.rw, a.rh), c = sig(cur, a.rx, a.ry, a.rw, a.rh);
            int ok = 0;
            for (int i = 0; i < r.length; i++) if (Math.abs(r[i] - c[i]) <= a.tol) ok++;
            return ok * 100f / r.length;
        } finally { ref.recycle(); }
    }

    /** Одна проверка цепочки: анализатор 1 > 2 > 3… Первый похожий запускает свой макрос, цепочка останавливается. */
    private boolean analyzeOnce(Cfg c, Run parent) {
        Bitmap cur = grabFrame();
        if (cur == null) { toast("Нет доступа к экрану — дайте разрешение на вкладке «Макросы > Анализ»"); return false; }
        try {
            for (Anl a : c.an) {
                if (parent != null && parent.stop) return false;
                float s = similarity(cur, a);
                log("Анализатор «" + a.name + "»: " + (s < 0 ? "нет эталона" : Math.round(s) + "% (нужно " + a.thr + "%)"));
                if (s >= a.thr) {
                    Macro m = c.find(a.macro);
                    if (m == null) { toast("Для «" + a.name + "» не выбран макрос"); return true; }
                    Run r = startMacro(c, m, true);
                    if (r != null) { try { r.join(); } catch (InterruptedException e) { r.stopNow(); return true; } }
                    return true;
                }
            }
        } finally { cur.recycle(); }
        return false;
    }

    private void startAnalysisLoop(final Cfg c) {
        if (anBusy) return;
        anBusy = true;
        Thread t = new Thread(() -> {
            try {
                int round = 0;
                while (anBusy) {
                    try { Thread.sleep(Math.max(100, c.delay)); } catch (InterruptedException e) { break; }
                    boolean hit = analyzeOnce(c, null);
                    round++;
                    if (!hit) {
                        if (c.onFail.equals("fallback")) {
                            Macro m = c.find(c.fallback);
                            if (m != null) { Run r = startMacro(c, m, true); if (r != null) { try { r.join(); } catch (InterruptedException e) { r.stopNow(); break; } } }
                        } else if (!c.onFail.equals("repeat")) break;
                    }
                    if (c.rounds > 0 && round >= c.rounds) break;
                    if (c.rounds == 1) break;
                }
            } catch (Throwable ex) { Log.e(TAG, "analysis", ex); }
            finally { anBusy = false; anThread = null; ui.post(this::refreshBubble); }
        }, "macro-analysis");
        anThread = t;
        t.start();
        ui.post(this::refreshBubble);
    }

    // ---------- кнопки макросов на экране ----------

    private final ArrayList<View> mbViews = new ArrayList<>();
    private final ArrayList<WindowManager.LayoutParams> mbLps = new ArrayList<>();
    private boolean mbEdit;

    private final class MBtnView extends View {
        final Btn b; final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); Bitmap tex; float dx, dy; int ox, oy; boolean moved; boolean on;
        final WindowManager.LayoutParams lp;
        MBtnView(Context c, Btn b, WindowManager.LayoutParams lp) {
            super(c); this.b = b; this.lp = lp;
            if (!b.tex.isEmpty()) {
                try {
                    Bitmap raw = BitmapFactory.decodeFile(new File(mediaDir(), b.tex).getAbsolutePath());
                    if (raw != null) { int s = dp(b.size); tex = Bitmap.createScaledBitmap(raw, s, s, true); }
                } catch (Throwable ignored) { }
            }
        }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), r = w / 2f - dp(2);
            if (tex != null) {
                Path clip = new Path(); clip.addCircle(w / 2f, w / 2f, r, Path.Direction.CW);
                c.save(); c.clipPath(clip); c.drawBitmap(tex, 0, 0, null); c.restore();
            } else {
                p.setShader(new LinearGradient(0, 0, w, w, on ? 0xFF2ECC71 : C_RED, on ? 0xFF1E8E4E : C_HI, Shader.TileMode.CLAMP));
                c.drawCircle(w / 2f, w / 2f, r, p); p.setShader(null);
            }
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(2));
            p.setColor(mbEdit ? 0xFFFFFFFF : (on ? 0xFF2ECC71 : 0x66FFFFFF));
            c.drawCircle(w / 2f, w / 2f, r, p); p.setStyle(Paint.Style.FILL);
            if (!b.label.isEmpty() && tex == null) {
                p.setColor(0xFFFFFFFF); p.setTextSize(dp(b.size > 50 ? 12 : 10)); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
                c.drawText(b.label.length() > 5 ? b.label.substring(0, 5) : b.label, w / 2f, w / 2f + dp(4), p);
            } else if (tex == null) { new Ico(on ? "stop" : "play", 0xFFFFFFFF).draw2(c, w / 2f - dp(9), w / 2f - dp(9), dp(18)); }
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: dx = e.getRawX(); dy = e.getRawY(); ox = lp.x; oy = lp.y; moved = false; return true;
                case MotionEvent.ACTION_MOVE:
                    if (mbEdit && (Math.abs(e.getRawX() - dx) > dp(4) || Math.abs(e.getRawY() - dy) > dp(4))) {
                        moved = true; lp.x = (int) (ox + e.getRawX() - dx); lp.y = (int) (oy + e.getRawY() - dy);
                        try { ow().updateViewLayout(this, lp); } catch (Exception ignored) { }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (mbEdit) { if (moved) { b.x = (lp.x + lp.width / 2f) / screenW(); b.y = (lp.y + lp.height / 2f) / screenH(); } }
                    else { vib(); toggleBtnMacro(b); }
                    return true;
            }
            return true;
        }
    }

    private void toggleBtnMacro(Btn b) {
        Macro m = cfg.find(b.macro);
        if (m == null) { toast("К кнопке не привязан макрос"); return; }
        Run r = runs.get(m.name);
        if (r != null) { r.stopNow(); runs.remove(m.name); updateMacroBtns(); refreshBubble(); return; }
        startMacro(cfg, m, false);
        updateMacroBtns();
    }

    private void updateMacroBtns() {
        ui.post(() -> { for (View v : mbViews) { MBtnView mv = (MBtnView) v; Macro m = cfg.find(mv.b.macro); mv.on = m != null && runs.containsKey(m.name); mv.invalidate(); } });
    }

    private void showMacroButtons() {
        hideMacroButtons();
        if (cfg == null || !prefs.getBoolean("mb_show", true) || (!canOverlay() && accHost() == null)) return;
        for (Btn b : cfg.btns) {
            int s = dp(b.size);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(s, s, overlayType(), FL_NF | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.x = Math.round(b.x * screenW() - s / 2f); lp.y = Math.round(b.y * screenH() - s / 2f);
            MBtnView v = new MBtnView(appCtx, b, lp);
            v.setAlpha(prefs.getInt("mb_alpha", 85) / 100f);
            try { ow().addView(v, lp); mbViews.add(v); mbLps.add(lp); popIn(v); } catch (Exception e) { log("Кнопка макроса не показана: " + e.getMessage()); }
        }
    }

    private void hideMacroButtons() {
        for (View v : mbViews) { try { ow().removeViewImmediate(v); } catch (Exception ignored) { } }
        mbViews.clear(); mbLps.clear(); mbEdit = false;
    }

    /** Режим расстановки: кнопки можно перетаскивать, сверху плашка «Готово». */
    private void editButtonsOnScreen() {
        if (cfg.btns.isEmpty()) { toast("Сначала добавьте кнопку"); return; }
        if (!ensureOverlay()) return;
        showMacroButtons();
        mbEdit = true;
        for (View v : mbViews) { v.setAlpha(1f); v.invalidate(); }
        if (menuRoot != null) menuRoot.setVisibility(View.INVISIBLE);
        TextView done = tv("Готово — сохранить расположение", 13, C_TEXT, true);
        done.setPadding(dp(18), dp(11), dp(18), dp(11));
        done.setBackground(grad(C_RED, C_HI, 30));
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(WRAP, WRAP, overlayType(), FL_NF, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL; lp.y = dp(24);
        capViews.add(done);
        done.setOnClickListener(v -> {
            saveCfg(cfg); mbEdit = false; removeView(done); capViews.remove(done);
            hideMacroButtons(); if (gameMode) showMacroButtons();
            if (menuRoot != null) menuRoot.setVisibility(View.VISIBLE);
        });
        try { ow().addView(done, lp); popIn(done); } catch (Exception ignored) { }
    }

    // ---------- захват координат / жестов / областей ----------

    private final ArrayList<View> capViews = new ArrayList<>();

    private void hideCaptureOverlays() {
        for (View v : new ArrayList<>(capViews)) removeView(v);
        capViews.clear();
        if (menuRoot != null) menuRoot.setVisibility(View.VISIBLE);
    }

    interface GestureCb { void on(float x1, float y1, float x2, float y2, long dur); }
    interface RectCb { void on(float rx, float ry, float rw, float rh); }

    private final class CaptureView extends View {
        final int kind;   // 0 жест, 1 запись последовательности, 2 область
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float x1, y1, x2, y2; boolean down; long t0, lastEnd;
        final StringBuilder seq = new StringBuilder(); int count;
        final GestureCb gcb; final StrCb scb; final RectCb rcb; final Runnable cancel;
        final RectF doneBtn = new RectF(); final ArrayList<float[]> marks = new ArrayList<>();

        CaptureView(Context c, int kind, GestureCb g, StrCb s, RectCb r, Runnable cancel) { super(c); this.kind = kind; gcb = g; scb = s; rcb = r; this.cancel = cancel; lastEnd = 0; }

        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            c.drawColor(kind == 2 ? 0x55000000 : 0x33000000);
            p.setTextAlign(Paint.Align.CENTER); p.setTextSize(dp(13)); p.setFakeBoldText(true);
            String msg = kind == 0 ? "Нажмите точку или проведите пальцем — получится тап или свайп" : kind == 1 ? "Записываю жесты: " + count + ". Нажмите «Готово»" : "Обведите область, которую нужно сравнивать";
            float tw = p.measureText(msg) + dp(30);
            p.setColor(0xE6131317); c.drawRoundRect(new RectF(w / 2f - tw / 2f, dp(14), w / 2f + tw / 2f, dp(52)), dp(20), dp(20), p);
            p.setColor(0xFFFFFFFF); c.drawText(msg, w / 2f, dp(38), p);
            // кнопки
            float bw = dp(96), bh = dp(38);
            RectF cr = new RectF(w - bw - dp(14), h - bh - dp(14), w - dp(14), h - dp(14));
            p.setColor(0xE6131317); c.drawRoundRect(cr, dp(19), dp(19), p);
            p.setColor(0xFFFFFFFF); c.drawText("Отмена", cr.centerX(), cr.centerY() + dp(5), p);
            doneBtn.set(0, 0, 0, 0);
            if (kind == 1) {
                doneBtn.set(cr.left - bw - dp(10), cr.top, cr.left - dp(10), cr.bottom);
                p.setShader(new LinearGradient(doneBtn.left, 0, doneBtn.right, 0, C_RED, C_HI, Shader.TileMode.CLAMP));
                c.drawRoundRect(doneBtn, dp(19), dp(19), p); p.setShader(null);
                p.setColor(0xFFFFFFFF); c.drawText("Готово", doneBtn.centerX(), doneBtn.centerY() + dp(5), p);
            }
            for (float[] m : marks) { p.setColor(0x88FF7A45); c.drawCircle(m[0], m[1], dp(8), p); }
            if (down || kind != 1) {
                if (down) {
                    p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(2));
                    if (kind == 2) { p.setColor(0xFFFF7A45); c.drawRect(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2), Math.max(y1, y2), p); }
                    else {
                        p.setColor(0xFFFFFFFF); c.drawLine(x1, y1, x2, y2, p);
                        c.drawCircle(x2, y2, dp(14), p); c.drawLine(x2 - dp(22), y2, x2 + dp(22), y2, p); c.drawLine(x2, y2 - dp(22), x2, y2 + dp(22), p);
                        p.setStyle(Paint.Style.FILL); p.setColor(0xFFFF7A45); c.drawCircle(x1, y1, dp(6), p);
                    }
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(0xFFFFFFFF); p.setTextSize(dp(12));
                    c.drawText("x=" + Math.round(x2) + "  y=" + Math.round(y2), Math.max(dp(70), Math.min(w - dp(70), x2)), Math.max(dp(70), y2 - dp(30)), p);
                }
            }
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            float x = e.getRawX(), y = e.getRawY();
            int w = getWidth(), h = getHeight();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (x > w - dp(110) && y > h - dp(52) && x < w - dp(14) && y < h - dp(14)) { finish(); if (cancel != null) cancel.run(); return true; }
                    if (kind == 1 && doneBtn.contains(x, y)) { finish(); if (scb != null) scb.on(seq.toString()); return true; }
                    down = true; x1 = x2 = x; y1 = y2 = y; t0 = SystemClock.uptimeMillis(); invalidate(); return true;
                case MotionEvent.ACTION_MOVE: x2 = x; y2 = y; invalidate(); return true;
                case MotionEvent.ACTION_UP:
                    if (!down) return true;
                    down = false; x2 = x; y2 = y;
                    long dur = Math.max(1, SystemClock.uptimeMillis() - t0);
                    vib();
                    if (kind == 2) {
                        float sw = screenW(), sh = screenH();
                        float rx = Math.max(0, Math.min(x1, x2)) / sw, ry = Math.max(0, Math.min(y1, y2)) / sh;
                        float rw = Math.abs(x2 - x1) / sw, rh = Math.abs(y2 - y1) / sh;
                        finish();
                        if (rw < 0.03f || rh < 0.03f) { toast("Область слишком маленькая"); if (cancel != null) cancel.run(); }
                        else if (rcb != null) rcb.on(rx, ry, Math.min(rw, 1 - rx), Math.min(rh, 1 - ry));
                        return true;
                    }
                    boolean moved = Math.hypot(x2 - x1, y2 - y1) > dp(18);
                    if (kind == 0) { finish(); if (gcb != null) gcb.on(x1, y1, x2, y2, dur); return true; }
                    long now = SystemClock.uptimeMillis();
                    if (count > 0) seq.append("wait ").append(Math.max(50, Math.round((t0 - lastEnd) / 10f) * 10)).append('\n');
                    if (moved) seq.append("swipe ").append(Math.round(x1)).append(' ').append(Math.round(y1)).append(' ').append(Math.round(x2)).append(' ').append(Math.round(y2)).append(' ').append(Math.max(80, dur)).append('\n');
                    else if (dur > 450) seq.append("long ").append(Math.round(x1)).append(' ').append(Math.round(y1)).append(' ').append(dur).append('\n');
                    else seq.append("tap ").append(Math.round(x1)).append(' ').append(Math.round(y1)).append('\n');
                    lastEnd = now; count++; marks.add(new float[]{x1, y1}); invalidate();
                    return true;
                case MotionEvent.ACTION_CANCEL: down = false; invalidate(); return true;
            }
            return true;
        }

        void finish() { capViews.remove(this); removeView(this); if (menuRoot != null) menuRoot.setVisibility(View.VISIBLE); }
    }

    private void startCapture(int kind, GestureCb g, StrCb s, RectCb r) {
        if (!ensureOverlay()) return;
        if (menuRoot != null) menuRoot.setVisibility(View.INVISIBLE);
        CaptureView v = new CaptureView(appCtx, kind, g, s, r, null);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(MATCH, MATCH, overlayType(),
                FL_NF | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
        try { ow().addView(v, lp); capViews.add(v); } catch (Exception e) { toast("Не удалось открыть захват: " + e.getMessage()); if (menuRoot != null) menuRoot.setVisibility(View.VISIBLE); }
    }


    // ==================================================================================================
    //  НОВОЕ: ВКЛАДКА «МАКРОСЫ» — проводник конфигов, редактор скриптов, кнопки, анализ
    // ==================================================================================================

    private LinearLayout fileItem(String ic, String title, String sub, boolean active, View.OnClickListener c, View.OnLongClickListener lc) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(10), dp(10), dp(10), dp(10));
        r.setBackground(rect(active ? 0xFF2A1D20 : C_CARD, 12, active ? C_RED : C_STROKE, 1));
        FrameLayout ib = new FrameLayout(this);
        ib.setBackground(grad(active ? C_RED : 0xFF30303A, active ? C_HI : 0xFF30303A, 10));
        ImageView i = icon(ic, 0xFFFFFFFF, 19);
        ib.addView(i, new FrameLayout.LayoutParams(dp(19), dp(19), Gravity.CENTER));
        r.addView(ib, lin(dp(38), dp(38), 0, 0, 0, dp(10), 0));
        LinearLayout t = col();
        t.addView(tv(title, 13.5f, C_TEXT, true));
        if (sub != null && !sub.isEmpty()) t.addView(tv(sub, 11, C_MUTED, false));
        r.addView(t, lin(0, WRAP, 1f, 0, 0, 0, 0));
        if (active) r.addView(icon("check", C_GREEN, 18));
        else r.addView(icon("chev", C_MUTED, 16));
        r.setOnClickListener(c);
        if (lc != null) r.setOnLongClickListener(lc);
        pressable(r);
        r.setLayoutParams(lin(MATCH, WRAP, 0, 0, 0, 0, dp(6)));
        return r;
    }

    private View tabMacros() { return cfgList(); }

    // ----- список конфигов (как в проводнике) -----

    private View cfgList() {
        subBack = null;
        LinearLayout c = col();
        c.setPadding(dp(14), dp(10), dp(14), dp(14));
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(8), dp(8), dp(8));
        bar.setBackground(rect(C_CARD2, 12, 0, 0));
        bar.addView(icon("folder", C_HI, 17), lin(dp(17), dp(17), 0, 0, dp(8), 0, 0));
        bar.addView(tv("Конфиги / ", 12, C_MUTED, false));
        bar.addView(tv(cfg.name, 12, C_TEXT, true), lin(0, WRAP, 1f, 0, 0, 0, 0));
        bar.addView(ibtn("plus", "", C_RED, 12, v -> newCfg()), lin(dp(38), dp(34), 0, 0, dp(6), 0, 0));
        bar.addView(ibtn("import", "", 0xFF30303A, 12, v -> importCfg()), lin(dp(38), dp(34), 0, 0, 0, 0, 0));
        c.addView(bar, lin(MATCH, WRAP, 0, 0, 0, 0, dp(10)));

        LinearLayout list = col();
        ArrayList<File> fs = listCfgFiles();
        for (final File f : fs) {
            final Cfg k = readCfg(f);
            if (k == null) continue;
            boolean act = k.name.equals(cfg.name);
            String sub = k.macros.size() + " макр. · " + k.an.size() + " анализ. · " + k.btns.size() + " кнопок · " + humanSize(f.length());
            list.addView(fileItem(act ? "check" : "file", k.name, sub, act, v -> openCfg(k, 0), v -> { vib(); cfgMenu(k); return true; }));
        }
        c.addView(list);
        c.addView(hint("Нажмите на конфиг, чтобы открыть. Долгое нажатие — действия: сделать активным, переименовать, копия, экспорт, удалить. Один конфиг содержит несколько макросов, кнопки на экране и цепочку анализаторов."));
        View sv = scroll(c);
        menuSub.setText("Активный конфиг: " + cfg.name);
        return sv;
    }

    private void newCfg() {
        prompt("Название конфига", "Например: Фарм", "", false, s -> {
            if (s.trim().isEmpty()) return;
            Cfg k = new Cfg(); k.name = s.trim();
            if (cfgFile(k.name).exists()) { toast("Такой конфиг уже есть"); return; }
            saveCfg(k); openCfg(k, 0);
        });
    }

    private void cfgMenu(final Cfg k) {
        sheet(k.name, new String[]{"Сделать активным", "Переименовать", "Дублировать", "Экспорт в Загрузки", "Удалить"},
                new String[]{"check", "edit", "copy", "export", "trash"}, i -> {
            switch (i) {
                case 0: setActiveCfg(k); setContent(cfgList(), null); toast("Активен: " + k.name); break;
                case 1: prompt("Новое имя", "", k.name, false, s -> {
                    if (s.trim().isEmpty() || cfgFile(s).exists()) { toast("Имя занято или пустое"); return; }
                    cfgFile(k.name).delete(); boolean wasActive = k.name.equals(cfg.name); k.name = s.trim(); saveCfg(k);
                    if (wasActive) { cfg = k; prefs.edit().putString("mc_active", k.name).apply(); }
                    setContent(cfgList(), null);
                }); break;
                case 2: { Cfg d = Cfg.from(jsonOf(k)); d.name = k.name + " копия"; saveCfg(d); setContent(cfgList(), null); break; }
                case 3: exportCfg(k); break;
                default:
                    if (k.name.equals(cfg.name) && listCfgFiles().size() <= 1) { toast("Нельзя удалить единственный конфиг"); return; }
                    confirm("Удалить конфиг «" + k.name + "»?", "Удалить", () -> {
                        cfgFile(k.name).delete();
                        if (k.name.equals(cfg.name)) { ArrayList<File> l = listCfgFiles(); Cfg n = l.isEmpty() ? null : readCfg(l.get(0)); if (n != null) setActiveCfg(n); else loadMacroState(); }
                        setContent(cfgList(), null);
                    });
            }
        });
    }

    private JSONObject jsonOf(Cfg k) { try { return k.toJson(); } catch (Exception e) { return new JSONObject(); } }

    private void exportCfg(Cfg k) {
        try {
            byte[] data = k.toJson().toString(1).getBytes(StandardCharsets.UTF_8);
            String fn = safeName(k.name) + ".json";
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.MediaColumns.DISPLAY_NAME, fn);
                cv.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
                cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ArcadeConfigs");
                Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                try (OutputStream o = getContentResolver().openOutputStream(u)) { o.write(data); }
            } else {
                File d = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "ArcadeConfigs"); d.mkdirs();
                try (FileOutputStream o = new FileOutputStream(new File(d, fn))) { o.write(data); }
            }
            toast("Сохранено: Загрузки/ArcadeConfigs/" + fn);
        } catch (Exception e) { toast("Экспорт не удался: " + e.getMessage()); }
    }

    private void importCfg() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        relay(i, RC_IMPORT, (res, d) -> {
            if (res != RESULT_OK || d == null || d.getData() == null) return;
            try (InputStream in = getContentResolver().openInputStream(d.getData())) {
                String s = readStream(in);
                Cfg k = Cfg.from(new JSONObject(s));
                if (cfgFile(k.name).exists()) k.name = k.name + " (импорт)";
                saveCfg(k);
                ui.post(() -> { if (menuRoot != null && curTab == 2) setContent(cfgList(), null); toast("Импортирован: " + k.name); });
            } catch (Exception e) { toast("Не файл конфига: " + e.getMessage()); }
        });
    }

    // ----- внутри конфига -----

    private void openCfg(final Cfg k, final int sub) {
        LinearLayout c = col();
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(10), dp(8), dp(10), dp(6));
        ImageView bk = icon("back", C_TEXT, 18);
        bk.setPadding(dp(7), dp(7), dp(7), dp(7));
        bk.setBackground(rect(C_CARD2, 11, 0, 0));
        bk.setOnClickListener(v -> setContent(cfgList(), null));
        pressable(bk);
        top.addView(bk, lin(dp(34), dp(34), 0, 0, dp(8), 0, 0));
        top.addView(tv(k.name, 14, C_TEXT, true), lin(0, WRAP, 1f, 0, 0, 0, 0));
        if (!k.name.equals(cfg.name)) top.addView(ibtn("check", "Активировать", 0xFF2A7A4B, 11, v -> { setActiveCfg(k); openCfg(k, sub); }));
        else top.addView(tv("активный", 11, C_GREEN, true));
        c.addView(top);
        String[] names = {"Макросы", "Кнопки", "Анализ"};
        String[] ics = {"macro", "target", "eye"};
        LinearLayout chips = new LinearLayout(this);
        chips.setPadding(dp(10), 0, dp(10), dp(4));
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            LinearLayout ch = ibtn(ics[i], names[i], i == sub ? C_RED : C_CARD2, 12, v -> openCfg(k, idx));
            chips.addView(ch, lin(0, WRAP, 1f, 0, 0, i < 2 ? dp(6) : 0, 0));
        }
        c.addView(chips);
        FrameLayout body = new FrameLayout(this);
        View inner = sub == 0 ? macrosView(k) : sub == 1 ? buttonsView(k) : analysisView(k);
        body.addView(inner, new FrameLayout.LayoutParams(MATCH, MATCH));
        c.addView(body, lin(MATCH, 0, 1f, 0, 0, 0, 0));
        setContent(c, () -> setContent(cfgList(), null));
        menuSub.setText(k.name + " · " + names[sub]);
    }

    // ----- макросы -----

    private View macrosView(final Cfg k) {
        LinearLayout c = col();
        c.addView(ibtn("plus", "Новый макрос", C_RED, 13, v -> prompt("Название макроса", "Например: Клик", "", false, s -> {
            if (s.trim().isEmpty() || k.find(s.trim()) != null) { toast("Имя занято или пустое"); return; }
            Macro m = new Macro(); m.name = s.trim(); k.macros.add(m); saveCfg(k); editMacro(k, m);
        })), lin(MATCH, WRAP, 0, dp(6), 0, 0, dp(10)));
        for (final Macro m : k.macros) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), dp(8), dp(8), dp(8));
            row.setBackground(rect(C_CARD, 12, C_STROKE, 1));
            LinearLayout t = col();
            t.addView(tv(m.name, 13.5f, C_TEXT, true));
            int lines = 0; for (String s : m.script.split("\n")) if (!s.trim().isEmpty() && !s.trim().startsWith("#")) lines++;
            t.addView(tv(lines + " команд · повтор " + (m.repeat <= 0 ? "∞" : String.valueOf(m.repeat)), 11, C_MUTED, false));
            row.addView(t, lin(0, WRAP, 1f, 0, 0, 0, 0));
            final boolean running = runs.containsKey(m.name);
            row.addView(ibtn(running ? "stop" : "play", "", running ? C_RED : 0xFF2A7A4B, 12, v -> {
                if (runs.containsKey(m.name)) stopAllMacros(); else startMacro(k, m, false);
                ui.postDelayed(() -> { if (menuRoot != null && curTab == 2) openCfg(k, 0); }, 150);
            }), lin(dp(40), dp(36), 0, 0, dp(6), 0, 0));
            row.addView(ibtn("edit", "", 0xFF30303A, 12, v -> editMacro(k, m)), lin(dp(40), dp(36), 0, 0, dp(6), 0, 0));
            row.addView(ibtn("trash", "", 0xFF30303A, 12, v -> confirm("Удалить макрос «" + m.name + "»?", "Удалить", () -> { k.macros.remove(m); saveCfg(k); openCfg(k, 0); })), lin(dp(40), dp(36), 0, 0, 0, 0, 0));
            c.addView(row, lin(MATCH, WRAP, 0, 0, 0, 0, dp(6)));
        }
        if (k.macros.isEmpty()) c.addView(hint("Макросов пока нет."));
        ScrollView sv = scroll(c);
        return sv;
    }

    private static final String HELP =
            "tap X Y [мс]       — нажатие (мс — удержание)\n" +
            "long X Y [мс]      — долгое нажатие\n" +
            "swipe X1 Y1 X2 Y2 [мс] — свайп\n" +
            "wait мс            — пауза\n" +
            "waitrand от до     — случайная пауза\n" +
            "repeat N … end     — цикл N раз\n" +
            "forever … end      — бесконечный цикл\n" +
            "run Имя            — вызвать другой макрос\n" +
            "analyze            — проверить экран анализаторами\n" +
            "jitter N           — разброс координат в пикселях\n" +
            "toast текст        — всплывающее сообщение\n" +
            "stop               — остановить макрос\n\n" +
            "Координаты — пиксели или проценты: tap 50% 80%\n" +
            "Комментарии начинаются с #\n" +
            "Кнопки «Точка», «Жест» и «Запись» сами вписывают координаты.";

    private void editMacro(final Cfg k, final Macro m) {
        final LinearLayout c = col();
        c.setPadding(dp(12), dp(8), dp(12), dp(14));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView bk = icon("back", C_TEXT, 18);
        bk.setPadding(dp(7), dp(7), dp(7), dp(7));
        bk.setBackground(rect(C_CARD2, 11, 0, 0));
        final Runnable back = () -> openCfg(k, 0);
        bk.setOnClickListener(v -> { saveMacroFromUi(k, m); back.run(); });
        pressable(bk);
        top.addView(bk, lin(dp(34), dp(34), 0, 0, dp(8), 0, 0));
        final EditText name = field(m.name, "Название", false);
        top.addView(name, lin(0, WRAP, 1f, 0, 0, 0, 0));
        c.addView(top, lin(MATCH, WRAP, 0, 0, 0, 0, dp(8)));

        final EditText ed = new EditText(this);
        ed.setText(m.script);
        ed.setTextColor(0xFFE9E9F0);
        ed.setHintTextColor(0xFF5E5E6A);
        ed.setHint("# команды, по одной на строку");
        ed.setTextSize(12.5f);
        ed.setTypeface(Typeface.MONOSPACE);
        ed.setGravity(Gravity.TOP | Gravity.START);
        ed.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        ed.setMinLines(7); ed.setMaxLines(12);
        ed.setBackground(rect(0xFF0C0C0F, 12, C_STROKE, 1));
        ed.setPadding(dp(12), dp(10), dp(12), dp(10));
        c.addView(ed, lin(MATCH, WRAP, 0, 0, 0, 0, dp(8)));

        final TextView err = tv("", 11, C_AMBER, false);
        c.addView(err, lin(MATCH, WRAP, 0, 0, 0, 0, dp(6)));
        final Runnable validate = () -> {
            try { ArrayList<Node> t = parseScript(ed.getText().toString()); err.setTextColor(C_GREEN); err.setText("Скрипт корректен"); }
            catch (ParseEx e) { err.setTextColor(C_AMBER); err.setText(e.getMessage()); }
        };

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        final java.util.function.Consumer<String> ins = s -> {
            int pos = Math.max(0, ed.getSelectionStart());
            Editable ea = ed.getText();
            String pre = (pos > 0 && ea.charAt(pos - 1) != '\n') ? "\n" : "";
            ea.insert(pos, pre + s + (s.endsWith("\n") ? "" : "\n"));
            validate.run();
        };
        chips.addView(chip("target", "Точка", v -> startCapture(0, (x1, y1, x2, y2, d) -> {
            if (Math.hypot(x2 - x1, y2 - y1) > dp(18)) ins.accept("swipe " + Math.round(x1) + " " + Math.round(y1) + " " + Math.round(x2) + " " + Math.round(y2) + " " + Math.max(80, d));
            else if (d > 450) ins.accept("long " + Math.round(x1) + " " + Math.round(y1) + " " + d);
            else ins.accept("tap " + Math.round(x1) + " " + Math.round(y1));
        }, null, null)));
        chips.addView(chip("rec", "Запись жестов", v -> startCapture(1, null, s -> ins.accept(s.trim()), null)));
        chips.addView(chip("clock", "Пауза", v -> ins.accept("wait 500")));
        chips.addView(chip("loop", "Цикл", v -> ins.accept("repeat 5\n  tap 50% 50%\n  wait 300\nend")));
        chips.addView(chip("loop", "Бесконечно", v -> ins.accept("forever\n  tap 50% 50%\n  wait 300\nend")));
        chips.addView(chip("link", "Вызвать", v -> {
            ArrayList<String> n = new ArrayList<>(); for (Macro o : k.macros) if (o != m) n.add(o.name);
            if (n.isEmpty()) { toast("Других макросов нет"); return; }
            sheet("Вызвать макрос", n.toArray(new String[0]), null, i -> ins.accept("run " + n.get(i)));
        }));
        chips.addView(chip("eye", "Анализ", v -> ins.accept("analyze")));
        chips.addView(chip("info", "Справка", v -> info("Команды скрипта", HELP)));
        hs.addView(chips);
        c.addView(hs, lin(MATCH, WRAP, 0, 0, 0, 0, dp(8)));

        final int[] rep = {m.repeat}, dl = {m.delay}, jt = {m.jitter};
        c.addView(sliderRow("Повторов всего (0 = бесконечно)", 0, 200, m.repeat, "", v -> rep[0] = v));
        c.addView(sliderRow("Задержка перед стартом", 0, 5000, m.delay, " мс", v -> dl[0] = v));
        c.addView(sliderRow("Разброс координат (антидетект)", 0, 40, m.jitter, " px", v -> jt[0] = v));

        LinearLayout row = new LinearLayout(this);
        row.addView(ibtn("check", "Проверить", C_CARD2, 12, v -> validate.run()), lin(0, WRAP, 1f, 0, 0, dp(6), 0));
        row.addView(ibtn("play", "Пуск", 0xFF2A7A4B, 12, v -> {
            m.name = name.getText().toString().trim().isEmpty() ? m.name : name.getText().toString().trim();
            m.script = ed.getText().toString(); m.repeat = rep[0]; m.delay = dl[0]; m.jitter = jt[0];
            startMacro(k, m, false);
        }), lin(0, WRAP, 1f, 0, 0, dp(6), 0));
        row.addView(ibtn("stop", "Стоп", C_CARD2, 12, v -> stopAllMacros()), lin(0, WRAP, 1f, 0, 0, dp(6), 0));
        row.addView(ibtn("save", "Сохранить", C_RED, 12, v -> {
            m.script = ed.getText().toString(); m.repeat = rep[0]; m.delay = dl[0]; m.jitter = jt[0];
            String nn = name.getText().toString().trim();
            if (!nn.isEmpty() && !nn.equals(m.name) && k.find(nn) == null) m.name = nn;
            saveCfg(k); toast("Сохранено"); validate.run();
        }), lin(0, WRAP, 1.2f, 0, 0, 0, 0));
        c.addView(row, lin(MATCH, WRAP, 0, dp(6), 0, 0, 0));
        editMacroState = new Object[]{m, ed, rep, dl, jt, name};
        validate.run();
        setContent(scroll(c), () -> { saveMacroFromUi(k, m); back.run(); });
        menuSub.setText(k.name + " · редактор");
    }

    private Object[] editMacroState;

    private void saveMacroFromUi(Cfg k, Macro m) {
        try {
            Object[] s = editMacroState;
            if (s == null || s[0] != m) return;
            m.script = ((EditText) s[1]).getText().toString();
            m.repeat = ((int[]) s[2])[0]; m.delay = ((int[]) s[3])[0]; m.jitter = ((int[]) s[4])[0];
            String nn = ((EditText) s[5]).getText().toString().trim();
            if (!nn.isEmpty() && !nn.equals(m.name) && k.find(nn) == null) m.name = nn;
            saveCfg(k);
        } catch (Throwable ignored) { }
    }

    private View chip(String ic, String text, View.OnClickListener l) {
        LinearLayout r = ibtn(ic, text, C_CARD2, 11.5f, l);
        r.setPadding(dp(11), dp(8), dp(11), dp(8));
        r.setLayoutParams(lin(WRAP, WRAP, 0, 0, 0, dp(6), 0));
        return r;
    }

    // ----- кнопки на экране -----

    private View buttonsView(final Cfg k) {
        LinearLayout c = col();
        c.addView(toggleRow("Показывать кнопки в игре", "Круглые кнопки поверх игры: нажали — макрос пошёл, нажали ещё раз — остановился", prefs.getBoolean("mb_show", true), on -> {
            prefs.edit().putBoolean("mb_show", on).apply(); hideMacroButtons(); if (gameMode) showMacroButtons();
        }), lin(MATCH, WRAP, 0, dp(4), 0, 0, 0));
        c.addView(sliderRow("Прозрачность кнопок", 20, 100, prefs.getInt("mb_alpha", 85), " %", v -> prefs.edit().putInt("mb_alpha", v).apply()));
        LinearLayout r0 = new LinearLayout(this);
        r0.addView(ibtn("plus", "Добавить кнопку", C_RED, 12, v -> {
            if (k.macros.isEmpty()) { toast("Сначала создайте макрос"); return; }
            ArrayList<String> n = new ArrayList<>(); for (Macro m : k.macros) n.add(m.name);
            sheet("Какой макрос запускает кнопка?", n.toArray(new String[0]), null, i -> {
                Btn b = new Btn(); b.macro = n.get(i); b.label = n.get(i); b.y = 0.3f + 0.1f * (k.btns.size() % 5); k.btns.add(b); saveCfg(k); openCfg(k, 1);
            });
        }), lin(0, WRAP, 1f, 0, 0, dp(6), 0));
        r0.addView(ibtn("hand", "Расставить на экране", C_CARD2, 12, v -> editButtonsOnScreen()), lin(0, WRAP, 1.3f, 0, 0, 0, 0));
        c.addView(r0, lin(MATCH, WRAP, 0, dp(8), 0, 0, dp(8)));
        for (final Btn b : k.btns) {
            LinearLayout card = card();
            LinearLayout h = new LinearLayout(this);
            h.setGravity(Gravity.CENTER_VERTICAL);
            h.addView(tv("Кнопка → " + (b.macro.isEmpty() ? "нет макроса" : b.macro), 13, C_TEXT, true), lin(0, WRAP, 1f, 0, 0, 0, 0));
            h.addView(ibtn("trash", "", 0xFF30303A, 12, v -> { k.btns.remove(b); saveCfg(k); openCfg(k, 1); }), lin(dp(38), dp(34), 0, 0, 0, 0, 0));
            card.addView(h);
            LinearLayout r1 = new LinearLayout(this);
            r1.addView(ibtn("macro", "Макрос", C_CARD2, 11.5f, v -> {
                ArrayList<String> n = new ArrayList<>(); for (Macro m : k.macros) n.add(m.name);
                sheet("Макрос для кнопки", n.toArray(new String[0]), null, i -> { b.macro = n.get(i); saveCfg(k); openCfg(k, 1); });
            }), lin(0, WRAP, 1f, 0, dp(8), dp(6), 0));
            r1.addView(ibtn("image", b.tex.isEmpty() ? "Текстура" : "Сменить", C_CARD2, 11.5f, v -> pickTexture(k, b)), lin(0, WRAP, 1f, 0, dp(8), dp(6), 0));
            r1.addView(ibtn("text", "Подпись", C_CARD2, 11.5f, v -> prompt("Подпись на кнопке", "до 5 символов", b.label, false, s -> { b.label = s.trim(); saveCfg(k); openCfg(k, 1); })), lin(0, WRAP, 1f, 0, dp(8), 0, 0));
            card.addView(r1);
            if (!b.tex.isEmpty()) card.addView(ibtn("trash", "Убрать текстуру", 0xFF30303A, 11.5f, v -> { b.tex = ""; saveCfg(k); openCfg(k, 1); }), lin(MATCH, WRAP, 0, dp(6), 0, 0, 0));
            card.addView(sliderRow("Размер", 36, 96, b.size, " dp", v -> { b.size = v; saveCfg(k); }));
            c.addView(card, lin(MATCH, WRAP, 0, 0, 0, 0, dp(8)));
        }
        if (k.btns.isEmpty()) c.addView(hint("Кнопок нет. Добавьте — и она появится поверх игры, когда вы выйдете из приложения."));
        return scroll(c);
    }

    private void pickTexture(final Cfg k, final Btn b) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("image/*");
        relay(i, RC_TEX, (res, d) -> {
            if (res != RESULT_OK || d == null || d.getData() == null) return;
            String fn = "tex_" + System.currentTimeMillis() + ".png";
            if (copyImage(d.getData(), new File(mediaDir(), fn), 256)) { b.tex = fn; saveCfg(k); ui.post(() -> { if (menuRoot != null && curTab == 2) openCfg(k, 1); }); }
            else toast("Не удалось прочитать картинку");
        });
    }

    /** Копирует картинку из галереи в память приложения (с уменьшением по большей стороне). */
    private boolean copyImage(Uri u, File dest, int maxSide) {
        try (InputStream in = getContentResolver().openInputStream(u)) {
            Bitmap b = BitmapFactory.decodeStream(in);
            if (b == null) return false;
            if (maxSide > 0 && Math.max(b.getWidth(), b.getHeight()) > maxSide) {
                float s = maxSide / (float) Math.max(b.getWidth(), b.getHeight());
                b = Bitmap.createScaledBitmap(b, Math.max(1, Math.round(b.getWidth() * s)), Math.max(1, Math.round(b.getHeight() * s)), true);
            }
            try (FileOutputStream o = new FileOutputStream(dest)) { b.compress(Bitmap.CompressFormat.PNG, 100, o); }
            return true;
        } catch (Exception e) { return false; }
    }

    // ----- анализ -----

    private View analysisView(final Cfg k) {
        LinearLayout c = col();
        c.addView(hint("Когда выполнится выбранный макрос, приложение посмотрит на экран и сравнит его с эталонами по порядку: анализатор 1, потом 2 и так далее. Первый «достаточно похожий» запускает свой макрос и цепочка заканчивается."), lin(MATCH, WRAP, 0, dp(6), 0, 0, dp(6)));
        c.addView(toggleRow("Анализ включён", "Запускается после макроса-триггера или командой analyze", k.anOn, on -> { k.anOn = on; saveCfg(k); }));
        boolean ready = capReady();
        LinearLayout perm = card();
        perm.addView(tv(ready ? "Доступ к экрану выдан" : "Нужен доступ к экрану", 13, ready ? C_GREEN : C_AMBER, true));
        perm.addView(hint(ready ? "Приложение видит экран для анализа и записи." : "Разрешение выдаётся один раз за сеанс. Система покажет окно подтверждения."));
        if (!ready) perm.addView(ibtn("eye", "Дать доступ к экрану", C_RED, 12, v -> requestProjection(() -> { if (menuRoot != null && curTab == 2) openCfg(k, 2); })), lin(MATCH, WRAP, 0, dp(8), 0, 0, 0));
        c.addView(perm, lin(MATCH, WRAP, 0, dp(6), 0, 0, dp(8)));

        LinearLayout set = card();
        set.addView(sect("settings", "ПАРАМЕТРЫ ЦЕПОЧКИ"), lin(MATCH, WRAP, 0, -dp(14), 0, 0, 0));
        final TextView trig = btn("Триггер: " + (k.trigger.isEmpty() ? "не выбран" : k.trigger), C_CARD2, 12.5f, null);
        trig.setOnClickListener(v -> {
            ArrayList<String> n = new ArrayList<>(); n.add("— не выбран —"); for (Macro m : k.macros) n.add(m.name);
            sheet("После какого макроса анализировать?", n.toArray(new String[0]), null, i -> { k.trigger = i == 0 ? "" : n.get(i); saveCfg(k); openCfg(k, 2); });
        });
        set.addView(trig, lin(MATCH, WRAP, 0, 0, 0, 0, dp(6)));
        set.addView(sliderRow("Подождать перед снимком экрана", 0, 5000, k.delay, " мс", v -> { k.delay = v; saveCfg(k); }));
        final String[] of = {"stop", "repeat", "fallback"};
        String[] ofn = {"Остановиться", "Повторить проверку", "Запустить запасной макрос"};
        int oi = Math.max(0, Arrays.asList(of).indexOf(k.onFail));
        final TextView fail = btn("Если ничего не похоже: " + ofn[oi], C_CARD2, 12.5f, null);
        fail.setOnClickListener(v -> sheet("Если ничего не похоже", ofn, null, i -> {
            k.onFail = of[i];
            if (i == 2) {
                ArrayList<String> n = new ArrayList<>(); for (Macro m : k.macros) n.add(m.name);
                if (n.isEmpty()) { toast("Нет макросов"); return; }
                sheet("Запасной макрос", n.toArray(new String[0]), null, j -> { k.fallback = n.get(j); saveCfg(k); openCfg(k, 2); });
            } else { saveCfg(k); openCfg(k, 2); }
        }));
        set.addView(fail, lin(MATCH, WRAP, 0, 0, 0, 0, dp(6)));
        set.addView(sliderRow("Кругов проверки (0 = бесконечно)", 0, 50, k.rounds, "", v -> { k.rounds = v; saveCfg(k); }));
        c.addView(set, lin(MATCH, WRAP, 0, 0, 0, 0, dp(8)));

        c.addView(sect("layers", "АНАЛИЗАТОРЫ ПО ПОРЯДКУ"));
        c.addView(ibtn("plus", "Добавить анализатор", C_RED, 12, v -> {
            Anl a = new Anl(); a.name = "Анализатор " + (k.an.size() + 1); k.an.add(a); saveCfg(k); editAnalyzer(k, a);
        }), lin(MATCH, WRAP, 0, 0, 0, 0, dp(8)));
        for (int i = 0; i < k.an.size(); i++) {
            final int idx = i; final Anl a = k.an.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), dp(8), dp(8), dp(8));
            row.setBackground(rect(C_CARD, 12, C_STROKE, 1));
            TextView num = tv(String.valueOf(i + 1), 13, 0xFFFFFFFF, true);
            num.setGravity(Gravity.CENTER); num.setBackground(grad(C_RED, C_HI, 20));
            row.addView(num, lin(dp(28), dp(28), 0, 0, dp(10), 0, 0));
            LinearLayout t = col();
            t.addView(tv(a.name, 13, C_TEXT, true));
            t.addView(tv((a.ref.isEmpty() ? "нет эталона" : "эталон есть") + " · " + a.thr + "% → " + (a.macro.isEmpty() ? "макрос не выбран" : a.macro), 11, C_MUTED, false));
            row.addView(t, lin(0, WRAP, 1f, 0, 0, 0, 0));
            row.addView(ibtn("up", "", 0xFF30303A, 12, v -> { if (idx > 0) { k.an.remove(idx); k.an.add(idx - 1, a); saveCfg(k); openCfg(k, 2); } }), lin(dp(32), dp(32), 0, 0, dp(4), 0, 0));
            row.addView(ibtn("down", "", 0xFF30303A, 12, v -> { if (idx < k.an.size() - 1) { k.an.remove(idx); k.an.add(idx + 1, a); saveCfg(k); openCfg(k, 2); } }), lin(dp(32), dp(32), 0, 0, dp(4), 0, 0));
            row.addView(ibtn("edit", "", 0xFF30303A, 12, v -> editAnalyzer(k, a)), lin(dp(32), dp(32), 0, 0, 0, 0, 0));
            c.addView(row, lin(MATCH, WRAP, 0, 0, 0, 0, dp(6)));
        }
        if (!k.an.isEmpty()) c.addView(ibtn("play", "Проверить цепочку сейчас", 0xFF2A7A4B, 12, v -> {
            if (!needAcc()) return;
            new Thread(() -> { boolean ok = analyzeOnce(k, null); toast(ok ? "Совпадение найдено" : "Ничего не похоже"); }, "an-test").start();
        }), lin(MATCH, WRAP, 0, dp(6), 0, 0, 0));
        return scroll(c);
    }

    private void editAnalyzer(final Cfg k, final Anl a) {
        final LinearLayout c = col();
        c.setPadding(dp(12), dp(8), dp(12), dp(14));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView bk = icon("back", C_TEXT, 18);
        bk.setPadding(dp(7), dp(7), dp(7), dp(7));
        bk.setBackground(rect(C_CARD2, 11, 0, 0));
        final EditText name = field(a.name, "Название", false);
        bk.setOnClickListener(v -> { if (!name.getText().toString().trim().isEmpty()) a.name = name.getText().toString().trim(); saveCfg(k); openCfg(k, 2); });
        pressable(bk);
        top.addView(bk, lin(dp(34), dp(34), 0, 0, dp(8), 0, 0));
        top.addView(name, lin(0, WRAP, 1f, 0, 0, 0, 0));
        c.addView(top, lin(MATCH, WRAP, 0, 0, 0, 0, dp(8)));

        final ImageView prev = new ImageView(this);
        prev.setScaleType(ImageView.ScaleType.CENTER_CROP);
        prev.setBackground(rect(0xFF0C0C0F, 12, C_STROKE, 1));
        prev.setClipToOutline(true);
        Bitmap rb = refBitmap(a);
        if (rb != null) prev.setImageBitmap(rb);
        c.addView(prev, lin(MATCH, dp(120), 0, 0, 0, 0, dp(8)));
        LinearLayout r1 = new LinearLayout(this);
        r1.addView(ibtn("image", "Скриншот из галереи", C_CARD2, 11.5f, v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("image/*");
            relay(i, RC_REF, (res, d) -> {
                if (res != RESULT_OK || d == null || d.getData() == null) return;
                String fn = "ref_" + System.currentTimeMillis() + ".png";
                if (copyImage(d.getData(), new File(mediaDir(), fn), 720)) { a.ref = fn; saveCfg(k); ui.post(() -> { if (menuRoot != null && curTab == 2) editAnalyzer(k, a); }); }
                else toast("Не удалось прочитать картинку");
            });
        }), lin(0, WRAP, 1f, 0, 0, dp(6), 0));
        r1.addView(ibtn("rec", "Снять с экрана", C_CARD2, 11.5f, v -> {
            if (!capReady()) { toast("Сначала дайте доступ к экрану (Анализ)"); return; }
            if (menuRoot != null) menuRoot.setVisibility(View.INVISIBLE);
            ui.postDelayed(() -> {
                Bitmap f = grabFrame();
                if (menuRoot != null) menuRoot.setVisibility(View.VISIBLE);
                if (f == null) { toast("Кадр ещё не готов, попробуйте снова"); return; }
                String fn = "ref_" + System.currentTimeMillis() + ".png";
                try (FileOutputStream o = new FileOutputStream(new File(mediaDir(), fn))) { f.compress(Bitmap.CompressFormat.PNG, 100, o); a.ref = fn; saveCfg(k); editAnalyzer(k, a); }
                catch (Exception e) { toast("Не удалось сохранить кадр"); }
            }, 900);
        }), lin(0, WRAP, 1f, 0, 0, 0, 0));
        c.addView(r1, lin(MATCH, WRAP, 0, 0, 0, 0, dp(8)));

        final TextView mc = btn("Если похоже, запустить: " + (a.macro.isEmpty() ? "не выбран" : a.macro), C_CARD2, 12.5f, null);
        mc.setOnClickListener(v -> {
            ArrayList<String> n = new ArrayList<>(); for (Macro m : k.macros) n.add(m.name);
            if (n.isEmpty()) { toast("Сначала создайте макрос"); return; }
            sheet("Какой макрос запускать?", n.toArray(new String[0]), null, i -> { a.macro = n.get(i); mc.setText("Если похоже, запустить: " + a.macro); });
        });
        c.addView(mc, lin(MATCH, WRAP, 0, 0, 0, 0, dp(6)));
        final TextView reg = btn(a.rw >= 0.99f && a.rh >= 0.99f ? "Область сравнения: весь экран" : "Область: " + Math.round(a.rw * 100) + "% x " + Math.round(a.rh * 100) + "%", C_CARD2, 12.5f, null);
        reg.setOnClickListener(v -> sheet("Область сравнения", new String[]{"Весь экран", "Выделить на экране"}, new String[]{"layers", "crop"}, i -> {
            if (i == 0) { a.rx = 0; a.ry = 0; a.rw = 1; a.rh = 1; reg.setText("Область сравнения: весь экран"); }
            else startCapture(2, null, null, (rx, ry, rw, rh) -> { a.rx = rx; a.ry = ry; a.rw = rw; a.rh = rh; reg.setText("Область: " + Math.round(rw * 100) + "% x " + Math.round(rh * 100) + "%"); });
        }));
        c.addView(reg, lin(MATCH, WRAP, 0, 0, 0, 0, dp(6)));
        c.addView(sliderRow("Порог схожести", 40, 100, a.thr, " %", v -> a.thr = v));
        c.addView(sliderRow("Допуск по яркости пикселя", 4, 90, a.tol, "", v -> a.tol = v));
        final TextView res = tv("", 12, C_MUTED, false);
        c.addView(ibtn("eye", "Сравнить с экраном сейчас", C_CARD2, 12, v -> {
            if (!capReady()) { toast("Нет доступа к экрану"); return; }
            Bitmap f = grabFrame();
            float s = similarity(f, a);
            res.setText(s < 0 ? "Нет эталона или кадра" : "Сейчас похоже на " + Math.round(s) + "%  (порог " + a.thr + "%) — " + (s >= a.thr ? "сработает" : "не сработает"));
            res.setTextColor(s >= a.thr ? C_GREEN : C_AMBER);
        }), lin(MATCH, WRAP, 0, dp(6), 0, 0, dp(6)));
        c.addView(res);
        c.addView(ibtn("trash", "Удалить анализатор", 0xFF30303A, 12, v -> confirm("Удалить «" + a.name + "»?", "Удалить", () -> { k.an.remove(a); saveCfg(k); openCfg(k, 2); })), lin(MATCH, WRAP, 0, dp(6), 0, 0, dp(6)));
        c.addView(ibtn("save", "Сохранить", C_RED, 13, v -> { if (!name.getText().toString().trim().isEmpty()) a.name = name.getText().toString().trim(); saveCfg(k); toast("Сохранено"); openCfg(k, 2); }));
        setContent(scroll(c), () -> { saveCfg(k); openCfg(k, 2); });
        menuSub.setText(k.name + " · анализатор");
    }


    // ==================================================================================================
    //  НОВОЕ: ДОСТУП К ЭКРАНУ (MediaProjection), ЗАПИСЬ ЭКРАНА, СЦЕНА С ПЕРСОНАЖЕМ
    // ==================================================================================================

    /** Служба держит разрешение на захват экрана и один VirtualDisplay: его кадры берут и анализаторы, и запись. */
    public static class CaptureService extends Service {
        static volatile CaptureService inst;
        static volatile boolean ready;
        static final Object LOCK = new Object();
        static Bitmap frame;
        static int fw, fh;
        static long frameAt;
        static volatile boolean fast;
        static volatile MediaProjection projection;
        MediaProjection proj;
        VirtualDisplay vd;
        ImageReader ir;
        HandlerThread ht;
        Handler hh;
        int w, h, dpi;

        @Override public IBinder onBind(Intent i) { return null; }

        @Override public int onStartCommand(Intent i, int flags, int startId) {
            inst = this;
            String ch = "arcade_cap";
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                nm.createNotificationChannel(new NotificationChannel(ch, "Доступ к экрану", NotificationManager.IMPORTANCE_LOW));
            }
            Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, ch) : new Notification.Builder(this);
            int icon = getApplicationInfo().icon;
            b.setContentTitle("ARCADE — доступ к экрану").setContentText("Анализ экрана и запись").setSmallIcon(icon != 0 ? icon : android.R.drawable.ic_menu_camera).setOngoing(true);
            try {
                if (Build.VERSION.SDK_INT >= 29) startForeground(78, b.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
                else startForeground(78, b.build());
            } catch (Exception e) { Log.w(TAG, "cap fg", e); stopSelf(); return START_NOT_STICKY; }
            if (i != null && i.hasExtra("code")) {
                try {
                    MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                    Intent data = i.getParcelableExtra("data");
                    proj = mpm.getMediaProjection(i.getIntExtra("code", 0), data);
                    projection = proj;
                    ht = new HandlerThread("arcade-cap");
                    ht.start();
                    hh = new Handler(ht.getLooper());
                    proj.registerCallback(new MediaProjection.Callback() { @Override public void onStop() { cleanup(); } }, hh);
                    setup();
                    ready = true;
                } catch (Throwable t) { Log.e(TAG, "projection", t); cleanup(); }
            }
            return START_NOT_STICKY;
        }

        void setup() {
            DisplayMetrics m = new DisplayMetrics();
            ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(m);
            float sc = (self != null ? self.prefs.getInt("rec_scale", 67) : 67) / 100f;
            int nw = Math.max(64, ((int) (m.widthPixels * sc)) & ~1), nh = Math.max(64, ((int) (m.heightPixels * sc)) & ~1);
            dpi = m.densityDpi;
            ImageReader nr = ImageReader.newInstance(nw, nh, PixelFormat.RGBA_8888, 3);
            nr.setOnImageAvailableListener(this::onFrame, hh);
            if (vd == null) vd = proj.createVirtualDisplay("arcade", nw, nh, dpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, nr.getSurface(), null, hh);
            else { vd.resize(nw, nh, dpi); vd.setSurface(nr.getSurface()); }
            ImageReader old = ir; ir = nr; w = nw; h = nh;
            if (old != null) { try { old.close(); } catch (Exception ignored) { } }
        }

        void onFrame(ImageReader r) {
            Image im = null;
            try {
                im = r.acquireLatestImage();
                if (im == null) return;
                long now = SystemClock.uptimeMillis();
                if (!fast && now - frameAt < 250) return;
                Image.Plane p = im.getPlanes()[0];
                ByteBuffer b = p.getBuffer();
                int bw = p.getRowStride() / p.getPixelStride();
                synchronized (LOCK) {
                    if (frame == null || frame.getWidth() != bw || frame.getHeight() != im.getHeight()) frame = Bitmap.createBitmap(bw, im.getHeight(), Bitmap.Config.ARGB_8888);
                    b.rewind();
                    frame.copyPixelsFromBuffer(b);
                    fw = im.getWidth(); fh = im.getHeight(); frameAt = now;
                }
                // поворот экрана: подстраиваем размер захвата
                DisplayMetrics m = new DisplayMetrics();
                ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(m);
                if ((m.widthPixels > m.heightPixels) != (w > h)) setup();
            } catch (Throwable t) { Log.w(TAG, "frame", t); }
            finally { if (im != null) im.close(); }
        }

        void cleanup() {
            ready = false; projection = null;
            try { if (vd != null) vd.release(); } catch (Exception ignored) { }
            try { if (ir != null) ir.close(); } catch (Exception ignored) { }
            vd = null; ir = null;
            try { if (proj != null) proj.stop(); } catch (Exception ignored) { }
            proj = null;
            if (ht != null) { ht.quitSafely(); ht = null; }
            synchronized (LOCK) { frame = null; }
            inst = null;
            try { stopForeground(true); } catch (Exception ignored) { }
            stopSelf();
        }

        @Override public void onDestroy() { cleanup(); super.onDestroy(); }
    }

    private boolean capReady() { return CaptureService.ready; }

    /** Копия последнего кадра экрана (или null, если доступ не выдан). */
    private Bitmap grabFrame() {
        synchronized (CaptureService.LOCK) {
            Bitmap f = CaptureService.frame;
            if (f == null) return null;
            try { return Bitmap.createBitmap(f, 0, 0, Math.min(f.getWidth(), CaptureService.fw), Math.min(f.getHeight(), CaptureService.fh)); } catch (Throwable t) { return null; }
        }
    }

    private Runnable projAfter;

    private void requestProjection(Runnable after) {
        if (Build.VERSION.SDK_INT < 21) { toast("Нужен Android 5+"); return; }
        if (capReady()) { if (after != null) after.run(); return; }
        projAfter = after;
        try {
            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            relay(mpm.createScreenCaptureIntent(), RC_PROJ);
        } catch (Exception e) { toast("Запрос доступа к экрану не открылся: " + e.getMessage()); }
    }

    private void onProjectionResult(int res, Intent data) {
        if (res != RESULT_OK || data == null) { toast("Доступ к экрану не выдан"); projAfter = null; return; }
        Intent i = new Intent(this, CaptureService.class);
        i.putExtra("code", res); i.putExtra("data", data);
        try { if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i); }
        catch (Exception e) { toast("Не удалось запустить захват: " + e.getMessage()); return; }
        final Runnable a = projAfter; projAfter = null;
        final int[] tries = {0};
        ui.post(new Runnable() { @Override public void run() {
            if (capReady()) { toast("Доступ к экрану выдан"); if (a != null) a.run(); }
            else if (++tries[0] < 30) ui.postDelayed(this, 150);
            else toast("Захват экрана не стартовал");
        } });
    }

    // ---------- сцена: персонаж, надписи, эмодзи (видны только в записи) ----------

    static final class Stk {
        String t = "text"; String v = ""; float x = 0.5f, y = 0.5f, s = 0.18f, r = 0; int a = 100, anim = 0, col = 0xFFFFFFFF; boolean on = true;
        JSONObject toJson() throws Exception { JSONObject o = new JSONObject(); o.put("t", t); o.put("v", v); o.put("x", x); o.put("y", y); o.put("s", s); o.put("r", r); o.put("a", a); o.put("anim", anim); o.put("col", col); o.put("on", on); return o; }
        static Stk from(JSONObject o) { Stk k = new Stk(); k.t = o.optString("t", "text"); k.v = o.optString("v", ""); k.x = (float) o.optDouble("x", .5); k.y = (float) o.optDouble("y", .5); k.s = (float) o.optDouble("s", .18); k.r = (float) o.optDouble("r", 0); k.a = o.optInt("a", 100); k.anim = o.optInt("anim", 0); k.col = o.optInt("col", 0xFFFFFFFF); k.on = o.optBoolean("on", true); return k; }
    }

    private final ArrayList<Stk> scene = new ArrayList<>();
    private boolean sceneLoaded;
    private final HashMap<String, Bitmap> stkBmps = new HashMap<>();
    private static final String[] ANIMS = {"Нет", "Покачивание", "Пульс", "Вращение", "Мигание"};

    private void loadScene() {
        if (sceneLoaded) return;
        sceneLoaded = true;
        try {
            JSONArray a = new JSONArray(prefs.getString("rec_scene", "[]"));
            for (int i = 0; i < a.length(); i++) scene.add(Stk.from(a.getJSONObject(i)));
        } catch (Exception ignored) { }
    }

    private void saveScene() {
        try { JSONArray a = new JSONArray(); for (Stk k : scene) a.put(k.toJson()); prefs.edit().putString("rec_scene", a.toString()).apply(); } catch (Exception ignored) { }
    }

    private Bitmap stkBitmap(String file) {
        Bitmap b = stkBmps.get(file);
        if (b == null) { b = BitmapFactory.decodeFile(new File(mediaDir(), file).getAbsolutePath()); if (b != null) stkBmps.put(file, b); }
        return b;
    }

    /** Рисует сцену: для записи — в кадр кодека, для предпросмотра — в редакторе. */
    private void drawScene(Canvas c, float W, float H, long ms, Stk selected) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        float base = Math.min(W, H);
        for (Stk k : scene) {
            if (!k.on) continue;
            float size = k.s * base, cx = k.x * W, cy = k.y * H, rot = k.r, sc = 1f, al = k.a / 100f;
            switch (k.anim) {
                case 1: cy += (float) Math.sin(ms / 380.0) * 0.025f * H; break;
                case 2: sc = 1f + 0.08f * (float) Math.sin(ms / 260.0); break;
                case 3: rot += (ms / 12f) % 360f; break;
                case 4: al *= 0.55f + 0.45f * (float) Math.sin(ms / 300.0); break;
            }
            c.save();
            c.translate(cx, cy); c.rotate(rot); c.scale(sc, sc);
            p.setAlpha((int) (255 * Math.max(0, Math.min(1, al))));
            float hw = size / 2f, hh = size / 2f;
            if (k.t.equals("img")) {
                Bitmap b = stkBitmap(k.v);
                if (b != null) { float ar = b.getWidth() / (float) b.getHeight(); hw = ar >= 1 ? size / 2f : size * ar / 2f; hh = ar >= 1 ? size / ar / 2f : size / 2f; c.drawBitmap(b, null, new RectF(-hw, -hh, hw, hh), p); }
            } else {
                p.setTextAlign(Paint.Align.CENTER); p.setTextSize(size * 0.62f); p.setColor(k.col); p.setFakeBoldText(true);
                p.setShadowLayer(size * 0.05f, 0, size * 0.02f, 0xCC000000);
                p.setAlpha((int) (255 * Math.max(0, Math.min(1, al))));
                c.drawText(k.v, 0, size * 0.22f, p);
                p.clearShadowLayer();
                hw = Math.max(size / 2f, p.measureText(k.v) / 2f); hh = size / 2f;
            }
            if (selected == k) {
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1.5f)); p.setColor(0xFFFF7A45); p.setAlpha(255);
                c.drawRoundRect(new RectF(-hw - dp(3), -hh - dp(3), hw + dp(3), hh + dp(3)), dp(6), dp(6), p);
                p.setStyle(Paint.Style.FILL);
            }
            c.restore();
        }
    }

    // ---------- запись ----------

    private volatile Recorder rec;
    private volatile boolean recPaused;
    private TextView recTimeView;

    private boolean recActive() { return rec != null; }

    private void toggleRecPause() {
        Recorder r = rec;
        if (r == null) return;
        r.setPaused(!r.paused);
        recPaused = r.paused;
        refreshBubble();
        pushRecUi();
    }

    private void startRecording() {
        if (rec != null) return;
        if (Build.VERSION.SDK_INT < 21) { toast("Нужен Android 5+"); return; }
        if (!capReady()) { requestProjection(this::startRecording); return; }
        final int cd = prefs.getInt("rec_cd", 3);
        if (cd <= 0) { beginRec(); return; }
        // обратный отсчёт: он заканчивается до начала записи, поэтому в видео не попадает
        if (menuRoot != null) menuRoot.setVisibility(View.INVISIBLE);
        final TextView t = tv("", 72, 0xFFFFFFFF, true);
        t.setGravity(Gravity.CENTER);
        t.setShadowLayer(12, 0, 0, 0xFF000000);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(WRAP, WRAP, overlayType(), FL_NF | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.CENTER;
        try { ow().addView(t, lp); } catch (Exception ignored) { beginRec(); return; }
        final int[] n = {cd};
        ui.post(new Runnable() { @Override public void run() {
            if (n[0] <= 0) { removeView(t); beginRec(); return; }
            t.setText(String.valueOf(n[0]--));
            if (!lowAnim()) { t.setScaleX(1.6f); t.setScaleY(1.6f); t.setAlpha(0.2f); t.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(350).start(); }
            ui.postDelayed(this, 1000);
        } });
    }

    private void beginRec() {
        try {
            Recorder r = new Recorder();
            r.start();
            rec = r; recPaused = false;
            CaptureService.fast = true;
            if (prefs.getBoolean("rec_hide_bubble", false) && gameMode) hideBubble();
            if (prefs.getBoolean("rec_pill", true) && gameMode) showRecPill();
            refreshBubble(); pushRecUi();
            toast("Запись началась");
        } catch (Throwable t) {
            Log.e(TAG, "rec", t);
            rec = null;
            toast("Запись не началась: " + t.getClass().getSimpleName() + " " + t.getMessage());
            if (menuRoot != null) menuRoot.setVisibility(View.VISIBLE);
        }
        if (menuRoot != null) menuRoot.setVisibility(View.VISIBLE);
    }

    private void stopRecording() {
        final Recorder r = rec;
        if (r == null) return;
        rec = null; recPaused = false;
        CaptureService.fast = false;
        hideRecPill();
        if (gameMode && bubble == null && !overlayOff) showBubble();
        refreshBubble(); pushRecUi();
        new Thread(() -> {
            String res = r.finish();
            toast(res == null ? "Не удалось сохранить запись" : "Запись сохранена: " + res);
        }, "rec-finish").start();
    }

    private final class Recorder {
        MediaCodec venc, aenc; MediaMuxer mux; Surface in; AudioRecord ar;
        Thread vt, rt, at;
        volatile boolean run = true, paused;
        long pausedUs, pauseAt, startUs = -1, startWall;
        int vTrack = -1, aTrack = -1; boolean muxStarted; boolean wantAudio;
        File out; int W, H, fps;
        final ArrayList<Object[]> pending = new ArrayList<>();
        long aSamples;
        final String wm; final boolean stamp;

        Recorder() {
            wm = prefs.getString("rec_wm", "");
            stamp = prefs.getBoolean("rec_stamp", false);
        }

        void start() throws Exception {
            loadScene();
            DisplayMetrics m = rm();
            float sc = prefs.getInt("rec_scale", 67) / 100f;
            boolean land = m.widthPixels > m.heightPixels;
            W = Math.max(128, ((int) (m.widthPixels * sc)) & ~1); H = Math.max(128, ((int) (m.heightPixels * sc)) & ~1);
            fps = prefs.getInt("rec_fps", 30);
            int br = prefs.getInt("rec_mbps", 8) * 1_000_000;
            File dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES);
            if (dir == null) dir = getFilesDir();
            dir.mkdirs();
            out = new File(dir, "ARCADE_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".mp4");
            MediaFormat vf = MediaFormat.createVideoFormat("video/avc", W, H);
            vf.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            vf.setInteger(MediaFormat.KEY_BIT_RATE, br);
            vf.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
            vf.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
            venc = MediaCodec.createEncoderByType("video/avc");
            venc.configure(vf, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            in = venc.createInputSurface();
            venc.start();
            mux = new MediaMuxer(out.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            String asrc = prefs.getString("rec_audio", "mic");
            wantAudio = !asrc.equals("off") && ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
            if (wantAudio) {
                try { initAudio(asrc.equals("system")); } catch (Throwable t) { log("Звук для записи недоступен: " + t.getMessage()); wantAudio = false; aenc = null; ar = null; }
            }
            startWall = SystemClock.uptimeMillis();
            vt = new Thread(this::drainVideo, "rec-vdrain");
            rt = new Thread(this::renderLoop, "rec-render");
            vt.start(); rt.start();
            if (wantAudio) { at = new Thread(this::audioLoop, "rec-audio"); at.start(); }
        }

        void initAudio(boolean system) throws Exception {
            int rate = 44100;
            int minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            AudioFormat af = new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build();
            AudioRecord a = null;
            if (system && Build.VERSION.SDK_INT >= 29 && CaptureService.projection != null) {
                try {
                    android.media.AudioPlaybackCaptureConfiguration cfgc = new android.media.AudioPlaybackCaptureConfiguration.Builder(CaptureService.projection)
                            .addMatchingUsage(AudioAttributes.USAGE_MEDIA).addMatchingUsage(AudioAttributes.USAGE_GAME).addMatchingUsage(AudioAttributes.USAGE_UNKNOWN).build();
                    a = new AudioRecord.Builder().setAudioFormat(af).setBufferSizeInBytes(minBuf * 4).setAudioPlaybackCaptureConfig(cfgc).build();
                    if (a.getState() != AudioRecord.STATE_INITIALIZED) { a.release(); a = null; }
                } catch (Throwable t) { a = null; }
                if (a == null) log("Звук игры не захватывается (игра может запрещать) — записываю микрофон");
            }
            if (a == null) a = new AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 4);
            if (a.getState() != AudioRecord.STATE_INITIALIZED) throw new IllegalStateException("AudioRecord не инициализировался");
            ar = a;
            MediaFormat f = MediaFormat.createAudioFormat("audio/mp4a-latm", rate, 1);
            f.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            f.setInteger(MediaFormat.KEY_BIT_RATE, 96000);
            f.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);
            aenc = MediaCodec.createEncoderByType("audio/mp4a-latm");
            aenc.configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            aenc.start();
            ar.startRecording();
        }

        void setPaused(boolean p) {
            if (p == paused) return;
            if (p) pauseAt = SystemClock.elapsedRealtimeNanos() / 1000; else pausedUs += SystemClock.elapsedRealtimeNanos() / 1000 - pauseAt;
            paused = p;
        }

        long elapsedMs() { return SystemClock.uptimeMillis() - startWall; }

        void renderLoop() {
            lowPrio();
            Paint bg = new Paint(); bg.setColor(0xFF000000);
            Paint fp = new Paint(Paint.FILTER_BITMAP_FLAG);
            Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
            long frameMs = 1000 / Math.max(1, fps);
            RectF dst = new RectF();
            SimpleDateFormat tf = new SimpleDateFormat("HH:mm:ss", Locale.US);
            while (run) {
                long t0 = SystemClock.uptimeMillis();
                if (!paused) {
                    Canvas c = null;
                    try {
                        c = in.lockCanvas(null);
                        c.drawRect(0, 0, W, H, bg);
                        synchronized (CaptureService.LOCK) {
                            Bitmap f = CaptureService.frame;
                            if (f != null && CaptureService.fw > 0) {
                                float ar = CaptureService.fw / (float) CaptureService.fh, tar = W / (float) H;
                                if (ar > tar) { float hh = W / ar; dst.set(0, (H - hh) / 2f, W, (H + hh) / 2f); } else { float ww = H * ar; dst.set((W - ww) / 2f, 0, (W + ww) / 2f, H); }
                                c.drawBitmap(f, new Rect(0, 0, CaptureService.fw, CaptureService.fh), dst, fp);
                            }
                        }
                        drawScene(c, W, H, elapsedMs(), null);
                        if (stamp || !wm.isEmpty()) {
                            tp.setColor(0xCCFFFFFF); tp.setTextSize(Math.min(W, H) * 0.035f); tp.setShadowLayer(4, 0, 1, 0xFF000000);
                            String s = (wm.isEmpty() ? "" : wm + "  ") + (stamp ? tf.format(new Date()) : "");
                            c.drawText(s.trim(), Math.min(W, H) * 0.03f, H - Math.min(W, H) * 0.03f, tp);
                        }
                    } catch (Throwable t) { Log.w(TAG, "render", t); }
                    finally { if (c != null) { try { in.unlockCanvasAndPost(c); } catch (Throwable ignored) { } } }
                }
                long sl = frameMs - (SystemClock.uptimeMillis() - t0);
                if (sl > 0) { try { Thread.sleep(sl); } catch (InterruptedException e) { break; } }
            }
        }

        synchronized void writeSample(boolean video, ByteBuffer buf, MediaCodec.BufferInfo bi) {
            if (!muxStarted) {
                byte[] copy = new byte[bi.size]; buf.position(bi.offset); buf.get(copy);
                MediaCodec.BufferInfo b2 = new MediaCodec.BufferInfo(); b2.set(0, bi.size, bi.presentationTimeUs, bi.flags);
                pending.add(new Object[]{video, copy, b2});
                return;
            }
            try { buf.position(bi.offset); buf.limit(bi.offset + bi.size); mux.writeSampleData(video ? vTrack : aTrack, buf, bi); } catch (Throwable t) { Log.w(TAG, "mux", t); }
        }

        synchronized void trackAdded(boolean video, MediaFormat f) {
            if (video) vTrack = mux.addTrack(f); else aTrack = mux.addTrack(f);
            if (vTrack >= 0 && (!wantAudio || aTrack >= 0) && !muxStarted) {
                mux.start(); muxStarted = true;
                for (Object[] o : pending) {
                    try { mux.writeSampleData((Boolean) o[0] ? vTrack : aTrack, ByteBuffer.wrap((byte[]) o[1]), (MediaCodec.BufferInfo) o[2]); } catch (Throwable ignored) { }
                }
                pending.clear();
            }
        }

        void drainVideo() {
            lowPrio();
            MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
            while (true) {
                int i;
                try { i = venc.dequeueOutputBuffer(bi, 10000); } catch (Throwable t) { break; }
                if (i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) trackAdded(true, venc.getOutputFormat());
                else if (i >= 0) {
                    ByteBuffer b = venc.getOutputBuffer(i);
                    if ((bi.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) bi.size = 0;
                    if (bi.size > 0 && b != null) {
                        if (startUs < 0) startUs = bi.presentationTimeUs;
                        bi.presentationTimeUs = Math.max(0, bi.presentationTimeUs - startUs - pausedUs);
                        writeSample(true, b, bi);
                    }
                    venc.releaseOutputBuffer(i, false);
                    if ((bi.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break;
                } else if (i == MediaCodec.INFO_TRY_AGAIN_LATER && !run && venc == null) break;
            }
        }

        void audioLoop() {
            lowPrio();
            byte[] pcm = new byte[4096];
            MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
            boolean eos = false;
            while (!eos) {
                if (run) {
                    int n = ar.read(pcm, 0, pcm.length);
                    if (n > 0 && !paused) {
                        int ii = aenc.dequeueInputBuffer(10000);
                        if (ii >= 0) {
                            ByteBuffer ib = aenc.getInputBuffer(ii); ib.clear(); ib.put(pcm, 0, n);
                            long pts = aSamples * 1_000_000L / 44100; aSamples += n / 2;
                            aenc.queueInputBuffer(ii, 0, n, pts, 0);
                        }
                    }
                } else {
                    int ii = aenc.dequeueInputBuffer(10000);
                    if (ii >= 0) { aenc.queueInputBuffer(ii, 0, 0, aSamples * 1_000_000L / 44100, MediaCodec.BUFFER_FLAG_END_OF_STREAM); }
                }
                while (true) {
                    int o = aenc.dequeueOutputBuffer(bi, 0);
                    if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) trackAdded(false, aenc.getOutputFormat());
                    else if (o >= 0) {
                        ByteBuffer b = aenc.getOutputBuffer(o);
                        if ((bi.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) bi.size = 0;
                        if (bi.size > 0 && b != null) writeSample(false, b, bi);
                        aenc.releaseOutputBuffer(o, false);
                        if ((bi.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) eos = true;
                    } else break;
                }
            }
        }

        String finish() {
            run = false;
            try { if (rt != null) rt.join(1500); } catch (Exception ignored) { }
            try { venc.signalEndOfInputStream(); } catch (Throwable ignored) { }
            try { if (vt != null) vt.join(3000); } catch (Exception ignored) { }
            try { if (at != null) at.join(3000); } catch (Exception ignored) { }
            try { if (ar != null) { ar.stop(); ar.release(); } } catch (Throwable ignored) { }
            try { venc.stop(); venc.release(); } catch (Throwable ignored) { }
            try { if (aenc != null) { aenc.stop(); aenc.release(); } } catch (Throwable ignored) { }
            try { in.release(); } catch (Throwable ignored) { }
            try { if (muxStarted) mux.stop(); mux.release(); } catch (Throwable t) { Log.w(TAG, "mux stop", t); }
            if (!muxStarted || out == null || !out.exists() || out.length() < 1024) return null;
            return publish();
        }

        String publish() {
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.Video.Media.DISPLAY_NAME, out.getName());
                    cv.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
                    cv.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/ARCADE");
                    Uri u = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv);
                    try (InputStream i = new FileInputStream(out); OutputStream o = getContentResolver().openOutputStream(u)) {
                        byte[] b = new byte[65536]; int n; while ((n = i.read(b)) > 0) o.write(b, 0, n);
                    }
                    out.delete();
                    return "Фильмы/ARCADE/" + out.getName();
                }
            } catch (Throwable t) { Log.w(TAG, "publish", t); }
            return out.getAbsolutePath();
        }
    }

    // ---------- плашка управления записью (в записи не нужна — гаснет сама) ----------

    private LinearLayout recPill;
    private TextView pillTime;
    private final Runnable pillTick = new Runnable() {
        @Override public void run() {
            Recorder r = rec;
            if (r == null || recPill == null) return;
            long s = r.elapsedMs() / 1000;
            if (pillTime != null) pillTime.setText(String.format(Locale.US, "%02d:%02d", s / 60, s % 60));
            if (recTimeView != null) recTimeView.setText(String.format(Locale.US, "%02d:%02d", s / 60, s % 60));
            ui.postDelayed(this, 500);
        }
    };

    private void showRecPill() {
        if (recPill != null) return;
        LinearLayout p = new LinearLayout(appCtx);
        p.setGravity(Gravity.CENTER_VERTICAL);
        p.setPadding(dp(10), dp(6), dp(6), dp(6));
        p.setBackground(rect(0xE6131317, 30, C_RED, 1.2f));
        View dot = new View(appCtx); dot.setBackground(rect(0xFFFF2D3F, 10, 0, 0));
        p.addView(dot, lin(dp(9), dp(9), 0, 0, 0, dp(7), 0));
        pillTime = tv("00:00", 12, C_TEXT, true);
        p.addView(pillTime, lin(WRAP, WRAP, 0, 0, 0, dp(8), 0));
        ImageView pa = icon("pause", C_TEXT, 16); pa.setPadding(dp(6), dp(6), dp(6), dp(6)); pa.setBackground(rect(C_CARD2, 20, 0, 0));
        pa.setOnClickListener(v -> toggleRecPause());
        p.addView(pa, lin(dp(30), dp(30), 0, 0, 0, dp(4), 0));
        ImageView st = icon("stop", 0xFFFFFFFF, 16); st.setPadding(dp(6), dp(6), dp(6), dp(6)); st.setBackground(rect(C_RED, 20, 0, 0));
        st.setOnClickListener(v -> stopRecording());
        p.addView(st, lin(dp(30), dp(30), 0, 0, 0, 0, 0));
        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(WRAP, WRAP, overlayType(), FL_NF | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START; lp.x = dp(70); lp.y = dp(10);
        p.setOnTouchListener(new View.OnTouchListener() {
            float dx, dy; int ox, oy;
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { dx = e.getRawX(); dy = e.getRawY(); ox = lp.x; oy = lp.y; v.setAlpha(1f); }
                else if (e.getActionMasked() == MotionEvent.ACTION_MOVE) { lp.x = (int) (ox + e.getRawX() - dx); lp.y = (int) (oy + e.getRawY() - dy); try { ow().updateViewLayout(v, lp); } catch (Exception ignored) { } }
                else if (e.getActionMasked() == MotionEvent.ACTION_UP) v.animate().alpha(0.35f).setStartDelay(2500).setDuration(400).start();
                return false;
            }
        });
        try { ow().addView(p, lp); recPill = p; p.animate().alpha(0.35f).setStartDelay(3000).setDuration(500).start(); } catch (Exception e) { return; }
        ui.removeCallbacks(pillTick); ui.post(pillTick);
    }

    private void hideRecPill() { if (recPill != null) { removeView(recPill); recPill = null; pillTime = null; } }

    private void pushRecUi() { ui.post(() -> { if (menuRoot != null && curTab == 3) selectTab(3); }); }


    // ==================================================================================================
    //  НОВОЕ: ВКЛАДКА «ЗАПИСЬ» (сцена, настройки, кнопки записи)
    // ==================================================================================================

    private Stk selStk;
    private SceneView sceneView;
    private LinearLayout stkPanelHost;

    private final class SceneView extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float lx, ly; boolean drag;
        SceneView(Context c) { super(c); }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            p.setColor(0xFF0C0C0F); c.drawRoundRect(new RectF(0, 0, w, h), dp(12), dp(12), p);
            p.setColor(0x14FFFFFF); p.setStrokeWidth(1);
            for (int i = 1; i < 6; i++) { c.drawLine(w * i / 6f, 0, w * i / 6f, h, p); c.drawLine(0, h * i / 6f, w, h * i / 6f, p); }
            p.setColor(0x55FFFFFF); p.setTextSize(dp(10)); p.setTextAlign(Paint.Align.CENTER);
            c.drawText("Так будет выглядеть кадр записи", w / 2f, dp(14), p);
            drawScene(c, w, h, SystemClock.uptimeMillis(), selStk);
            boolean anim = false; for (Stk k : scene) if (k.on && k.anim != 0) anim = true;
            if (anim && isAttachedToWindow()) postInvalidateDelayed(40);
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            float w = getWidth(), h = getHeight();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    Stk best = null; float bd = 1e9f;
                    for (Stk k : scene) { float d = (float) Math.hypot(k.x * w - e.getX(), k.y * h - e.getY()); float lim = Math.max(dp(28), k.s * Math.min(w, h) * 0.6f); if (d < lim && d < bd) { bd = d; best = k; } }
                    if (best != selStk) { selStk = best; rebuildStkPanel(); }
                    drag = best != null; lx = e.getX(); ly = e.getY();
                    getParent().requestDisallowInterceptTouchEvent(true);
                    invalidate(); return true;
                }
                case MotionEvent.ACTION_MOVE:
                    if (drag && selStk != null) { selStk.x = Math.max(0, Math.min(1, selStk.x + (e.getX() - lx) / w)); selStk.y = Math.max(0, Math.min(1, selStk.y + (e.getY() - ly) / h)); lx = e.getX(); ly = e.getY(); invalidate(); }
                    return true;
                case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
                    if (drag) saveScene(); drag = false; getParent().requestDisallowInterceptTouchEvent(false); return true;
            }
            return true;
        }
    }

    private void rebuildStkPanel() {
        if (stkPanelHost == null) return;
        stkPanelHost.removeAllViews();
        final Stk k = selStk;
        if (k == null) { stkPanelHost.addView(hint("Нажмите на объект в предпросмотре, чтобы изменить его, и перетащите, чтобы передвинуть.")); return; }
        stkPanelHost.addView(tv(k.t.equals("img") ? "Персонаж" : (k.t.equals("emoji") ? "Эмодзи: " + k.v : "Надпись: " + k.v), 13, C_TEXT, true), lin(MATCH, WRAP, 0, 0, 0, 0, dp(4)));
        stkPanelHost.addView(sliderRow("Размер", 3, 90, Math.round(k.s * 100), " %", v -> { k.s = v / 100f; saveScene(); if (sceneView != null) sceneView.invalidate(); }));
        stkPanelHost.addView(sliderRow("Прозрачность", 10, 100, k.a, " %", v -> { k.a = v; saveScene(); if (sceneView != null) sceneView.invalidate(); }));
        stkPanelHost.addView(sliderRow("Поворот", 0, 359, Math.round(k.r) % 360, "°", v -> { k.r = v; saveScene(); if (sceneView != null) sceneView.invalidate(); }));
        TextView an = btn("Анимация: " + ANIMS[k.anim], C_CARD2, 12.5f, null);
        an.setOnClickListener(v -> sheet("Анимация в записи", ANIMS, null, i -> { k.anim = i; saveScene(); rebuildStkPanel(); if (sceneView != null) sceneView.invalidate(); }));
        stkPanelHost.addView(an, lin(MATCH, WRAP, 0, dp(4), 0, 0, dp(6)));
        if (!k.t.equals("img")) {
            LinearLayout cs = new LinearLayout(this);
            int[] cols = {0xFFFFFFFF, 0xFFFF4B5C, 0xFFFFD23F, 0xFF2ECC71, 0xFF4C8DFF, 0xFFB46BFF};
            for (final int cc : cols) {
                View d = new View(this);
                d.setBackground(rect(cc, 30, k.col == cc ? 0xFFFFFFFF : 0, k.col == cc ? 2 : 0));
                d.setOnClickListener(v -> { k.col = cc; saveScene(); rebuildStkPanel(); if (sceneView != null) sceneView.invalidate(); });
                cs.addView(d, lin(dp(26), dp(26), 0, 0, 0, dp(8), 0));
            }
            stkPanelHost.addView(cs, lin(MATCH, WRAP, 0, 0, 0, 0, dp(6)));
        }
        LinearLayout r = new LinearLayout(this);
        r.addView(ibtn("eye", k.on ? "Скрыть" : "Показать", C_CARD2, 12, v -> { k.on = !k.on; saveScene(); rebuildStkPanel(); if (sceneView != null) sceneView.invalidate(); }), lin(0, WRAP, 1f, 0, 0, dp(6), 0));
        r.addView(ibtn("trash", "Удалить", 0xFF30303A, 12, v -> { scene.remove(k); selStk = null; saveScene(); rebuildStkPanel(); if (sceneView != null) sceneView.invalidate(); }), lin(0, WRAP, 1f, 0, 0, 0, 0));
        stkPanelHost.addView(r);
    }

    private void addStk(Stk k) { scene.add(k); selStk = k; saveScene(); rebuildStkPanel(); if (sceneView != null) sceneView.invalidate(); }

    private View tabRecord() {
        loadScene();
        LinearLayout c = col();
        // --- управление ---
        LinearLayout ctl = card();
        LinearLayout h = new LinearLayout(this);
        h.setGravity(Gravity.CENTER_VERTICAL);
        recTimeView = tv("00:00", 26, rec != null ? 0xFFFF4B5C : C_TEXT, true);
        recTimeView.setTypeface(Typeface.DEFAULT_BOLD);
        h.addView(recTimeView, lin(0, WRAP, 1f, 0, 0, 0, 0));
        boolean ready = capReady();
        h.addView(tv(rec != null ? (recPaused ? "ПАУЗА" : "ИДЁТ ЗАПИСЬ") : (ready ? "ЭКРАН ДОСТУПЕН" : "НУЖЕН ДОСТУП"), 10.5f, rec != null ? 0xFFFF4B5C : (ready ? C_GREEN : C_AMBER), true));
        ctl.addView(h, lin(MATCH, WRAP, 0, 0, 0, 0, dp(10)));
        LinearLayout b = new LinearLayout(this);
        if (rec == null) {
            b.addView(ibtn("rec", "Начать запись", C_RED, 14, v -> startRecording()), lin(0, WRAP, 1f, 0, 0, 0, 0));
        } else {
            b.addView(ibtn(recPaused ? "play" : "pause", recPaused ? "Продолжить" : "Пауза", C_CARD2, 13, v -> toggleRecPause()), lin(0, WRAP, 1f, 0, 0, dp(6), 0));
            b.addView(ibtn("stop", "Стоп и сохранить", C_RED, 13, v -> stopRecording()), lin(0, WRAP, 1.3f, 0, 0, 0, 0));
        }
        ctl.addView(b);
        ctl.addView(hint("Запись сохраняется в «Фильмы/ARCADE». Персонаж и надписи вы не видите на экране, но они есть в видео."), lin(MATCH, WRAP, 0, dp(8), 0, 0, 0));
        c.addView(ctl, lin(MATCH, WRAP, 0, dp(6), 0, 0, dp(4)));
        if (rec != null) { ui.removeCallbacks(pillTickMenu); ui.post(pillTickMenu); }

        // --- сцена ---
        c.addView(sect("smile", "СЦЕНА: ПЕРСОНАЖ, НАДПИСИ, ЭМОДЗИ"));
        DisplayMetrics m = rm();
        float ar = Math.max(m.widthPixels, m.heightPixels) / (float) Math.min(m.widthPixels, m.heightPixels);
        boolean land = m.widthPixels > m.heightPixels;
        int pw = Math.min(screenW() - dp(140), dp(380));
        int phh = land ? Math.round(pw / ar) : Math.min(dp(300), Math.round(pw * ar));
        sceneView = new SceneView(this);
        c.addView(sceneView, lin(land ? pw : Math.round(phh / ar), phh, 0, 0, 0, 0, dp(8)));
        LinearLayout ad = new LinearLayout(this);
        ad.addView(ibtn("user", "Персонаж", C_CARD2, 11.5f, v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("image/*");
            relay(i, RC_CHAR, (res, d) -> {
                if (res != RESULT_OK || d == null || d.getData() == null) return;
                String fn = "char_" + System.currentTimeMillis() + ".png";
                if (copyImage(d.getData(), new File(mediaDir(), fn), 600)) { Stk k = new Stk(); k.t = "img"; k.v = fn; k.s = 0.4f; k.x = 0.85f; k.y = 0.7f; ui.post(() -> addStk(k)); }
                else toast("Не удалось прочитать картинку");
            });
        }), lin(0, WRAP, 1f, 0, 0, dp(6), 0));
        ad.addView(ibtn("text", "Надпись", C_CARD2, 11.5f, v -> prompt("Текст надписи", "Например: Привет!", "", false, s -> { if (s.trim().isEmpty()) return; Stk k = new Stk(); k.t = "text"; k.v = s.trim(); k.s = 0.16f; k.y = 0.2f; addStk(k); })), lin(0, WRAP, 1f, 0, 0, dp(6), 0));
        ad.addView(ibtn("smile", "Эмодзи", C_CARD2, 11.5f, v -> prompt("Эмодзи", "Вставьте с клавиатуры телефона", "", false, s -> { if (s.trim().isEmpty()) return; Stk k = new Stk(); k.t = "emoji"; k.v = s.trim(); k.s = 0.22f; k.x = 0.15f; k.y = 0.2f; addStk(k); })), lin(0, WRAP, 1f, 0, 0, 0, 0));
        c.addView(ad, lin(MATCH, WRAP, 0, 0, 0, 0, dp(8)));
        stkPanelHost = col();
        c.addView(stkPanelHost);
        selStk = null;
        rebuildStkPanel();

        // --- настройки записи ---
        c.addView(sect("settings", "НАСТРОЙКИ ЗАПИСИ"));
        LinearLayout s = card();
        s.addView(sliderRow("Качество картинки (масштаб экрана)", 30, 100, prefs.getInt("rec_scale", 67), " %", v -> prefs.edit().putInt("rec_scale", v).apply()));
        s.addView(sliderRow("Битрейт видео", 2, 30, prefs.getInt("rec_mbps", 8), " Мбит/с", v -> prefs.edit().putInt("rec_mbps", v).apply()));
        String[] fpsN = {"15", "24", "30", "60"};
        final int[] fpsV = {15, 24, 30, 60};
        TextView fp = btn("Частота кадров: " + prefs.getInt("rec_fps", 30) + " к/с", C_CARD2, 12.5f, null);
        fp.setOnClickListener(v -> sheet("Частота кадров", fpsN, null, i -> { prefs.edit().putInt("rec_fps", fpsV[i]).apply(); selectTab(3); }));
        s.addView(fp, lin(MATCH, WRAP, 0, dp(4), 0, 0, dp(6)));
        final String[] ak = {"off", "mic", "system"};
        String[] an = {"Без звука", "Микрофон", "Звук игры (Android 10+)"};
        int ai = Math.max(0, Arrays.asList(ak).indexOf(prefs.getString("rec_audio", "mic")));
        TextView au = btn("Звук: " + an[ai], C_CARD2, 12.5f, null);
        au.setOnClickListener(v -> sheet("Источник звука", an, null, i -> { prefs.edit().putString("rec_audio", ak[i]).apply(); selectTab(3); }));
        s.addView(au, lin(MATCH, WRAP, 0, 0, 0, 0, dp(6)));
        s.addView(sliderRow("Обратный отсчёт перед стартом", 0, 10, prefs.getInt("rec_cd", 3), " с", v -> prefs.edit().putInt("rec_cd", v).apply()));
        s.addView(toggleRow("Плашка управления в игре", "Таймер, пауза и стоп; через 3 секунды становится полупрозрачной", prefs.getBoolean("rec_pill", true), on -> prefs.edit().putBoolean("rec_pill", on).apply()));
        s.addView(toggleRow("Прятать геймпад во время записи", "Чтобы кнопка не попала в видео. Пауза — долгое нажатие на плашку", prefs.getBoolean("rec_hide_bubble", false), on -> prefs.edit().putBoolean("rec_hide_bubble", on).apply()));
        s.addView(toggleRow("Время в углу видео", "Часы поверх записи", prefs.getBoolean("rec_stamp", false), on -> prefs.edit().putBoolean("rec_stamp", on).apply()));
        final EditText wmk = field(prefs.getString("rec_wm", ""), "Подпись в углу (водяной знак)", false);
        wmk.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence a, int b2, int c2, int d) { }
            @Override public void onTextChanged(CharSequence a, int b2, int c2, int d) { prefs.edit().putString("rec_wm", a.toString()).apply(); }
            @Override public void afterTextChanged(Editable e) { }
        });
        s.addView(wmk, lin(MATCH, WRAP, 0, dp(6), 0, 0, 0));
        c.addView(s);
        if (capReady()) c.addView(ibtn("power", "Отозвать доступ к экрану", 0xFF30303A, 12, v -> { try { stopService(new Intent(this, CaptureService.class)); } catch (Exception ignored) { } ui.postDelayed(() -> selectTab(3), 300); }), lin(MATCH, WRAP, 0, dp(10), 0, 0, 0));
        return scroll(c);
    }

    private final Runnable pillTickMenu = new Runnable() {
        @Override public void run() {
            Recorder r = rec;
            if (r == null || recTimeView == null) return;
            long s = r.elapsedMs() / 1000;
            recTimeView.setText(String.format(Locale.US, "%02d:%02d", s / 60, s % 60));
            ui.postDelayed(this, 500);
        }
    };

    // ==================================================================================================
    //  НОВОЕ: ВКЛАДКА «НАСТРОЙКИ» — производительность в игре
    // ==================================================================================================

    private TextView perfView;
    private final ArrayList<ProgressBar> perfBars = new ArrayList<>();
    private final ArrayList<TextView> perfLabels = new ArrayList<>();
    private long lastCpuTicks, lastCpuAt;

    private final Runnable perfTick = new Runnable() {
        @Override public void run() {
            if (menuRoot == null || curTab != 1 || perfBars.size() < 4) return;
            try {
                Debug.MemoryInfo mi = new Debug.MemoryInfo(); Debug.getMemoryInfo(mi);
                int appMb = mi.getTotalPss() / 1024;
                android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                android.app.ActivityManager.MemoryInfo sm = new android.app.ActivityManager.MemoryInfo(); am.getMemoryInfo(sm);
                int usedPct = (int) (100 - sm.availMem * 100 / Math.max(1, sm.totalMem));
                long ticks = readCpuTicks(), now = SystemClock.elapsedRealtime();
                int cpu = lastCpuAt == 0 ? 0 : (int) Math.min(100, (ticks - lastCpuTicks) * 1000 / Math.max(1, now - lastCpuAt));
                lastCpuTicks = ticks; lastCpuAt = now;
                Intent b = registerReceiver(null, new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                int lvl = b == null ? 0 : b.getIntExtra("level", 0) * 100 / Math.max(1, b.getIntExtra("scale", 100));
                float temp = b == null ? 0 : b.getIntExtra("temperature", 0) / 10f;
                int th = 0;
                if (Build.VERSION.SDK_INT >= 29) { try { th = ((PowerManager) getSystemService(Context.POWER_SERVICE)).getCurrentThermalStatus(); } catch (Throwable ignored) { } }
                setBar(0, "Память приложения", appMb + " МБ", Math.min(100, appMb * 100 / 600));
                setBar(1, "Память устройства занята", usedPct + " %", usedPct);
                setBar(2, "Нагрузка процессора (приложение)", cpu + " %", cpu);
                setBar(3, "Температура батареи", String.format(Locale.US, "%.1f °C", temp) + (th >= 3 ? "  · перегрев" : ""), (int) Math.min(100, Math.max(0, (temp - 25) * 100 / 25)));
                if (perfView != null) perfView.setText("Заряд " + lvl + " %  ·  потоков записи/макросов: " + (runs.size() + (rec != null ? 1 : 0)));
            } catch (Throwable t) { Log.w(TAG, "perf", t); }
            ui.postDelayed(this, 1500);
        }
    };

    private long readCpuTicks() {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream("/proc/self/stat")))) {
            String[] p = r.readLine().split("\\) ", 2)[1].split(" ");
            return Long.parseLong(p[11]) + Long.parseLong(p[12]);
        } catch (Throwable t) { return 0; }
    }

    private void setBar(int i, String name, String val, int pct) {
        perfLabels.get(i).setText(name + ":  " + val);
        ProgressBar pb = perfBars.get(i);
        if (lowAnim()) pb.setProgress(pct);
        else { android.animation.ObjectAnimator a = android.animation.ObjectAnimator.ofInt(pb, "progress", pb.getProgress(), pct); a.setDuration(500); a.start(); }
        int col = pct > 85 ? C_RED : (pct > 60 ? C_AMBER : C_GREEN);
        try { pb.getProgressDrawable().setColorFilter(new PorterDuffColorFilter(col, PorterDuff.Mode.SRC_IN)); } catch (Throwable ignored) { }
    }

    /** Применяет выбранный профиль к работающим компонентам (вызывается при уходе в игру и при смене настроек). */
    private void applyPerfProfile() {
        WebView w = web();
        if (w != null && gameMode) {
            try {
                if (prefs.getBoolean("ps_voice", true)) { w.onResume(); w.resumeTimers(); }
                else { w.onPause(); w.pauseTimers(); }
            } catch (Throwable ignored) { }
        }
        if (bubble != null) {
            bubble.setAlpha(prefs.getInt("ps_balpha", 100) / 100f);
            bubble.setActive(macroRunning() || mode != M_IDLE, recActive());
        }
    }

    private void lowPrio() {
        if (!prefs.getBoolean("ps_lowprio", true)) return;
        try { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND); } catch (Throwable ignored) { }
    }

    private void setProfile(int p) {
        SharedPreferences.Editor e = prefs.edit();
        if (p == 0) { e.putBoolean("ps_voice", false).putInt("ps_tick", 10000).putBoolean("ps_noanim", true).putBoolean("ps_lowprio", true).putInt("rec_scale", 50).putInt("rec_fps", 24).putInt("rec_mbps", 5); }
        else if (p == 1) { e.putBoolean("ps_voice", true).putInt("ps_tick", 4000).putBoolean("ps_noanim", false).putBoolean("ps_lowprio", true).putInt("rec_scale", 67).putInt("rec_fps", 30).putInt("rec_mbps", 8); }
        else { e.putBoolean("ps_voice", true).putInt("ps_tick", 2000).putBoolean("ps_noanim", false).putBoolean("ps_lowprio", false).putInt("rec_scale", 85).putInt("rec_fps", 60).putInt("rec_mbps", 16); }
        e.putInt("ps_profile", p).apply();
        applyPerfProfile();
        selectTab(1);
    }

    private View tabSettings() {
        LinearLayout c = col();
        perfBars.clear(); perfLabels.clear();
        c.addView(sect("cpu", "ЖИВОЙ МОНИТОР"));
        LinearLayout mon = card();
        String[] ic = {"mem", "mem", "cpu", "battery"};
        for (int i = 0; i < 4; i++) {
            TextView l = tv("…", 12, C_TEXT, false);
            ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            pb.setMax(100);
            mon.addView(l, lin(MATCH, WRAP, 0, i == 0 ? 0 : dp(8), 0, 0, dp(3)));
            mon.addView(pb, lin(MATCH, dp(6), 0, 0, 0, 0, 0));
            perfLabels.add(l); perfBars.add(pb);
        }
        perfView = hint("");
        mon.addView(perfView, lin(MATCH, WRAP, 0, dp(8), 0, 0, 0));
        c.addView(mon);
        ui.removeCallbacks(perfTick); ui.post(perfTick);

        c.addView(sect("bolt", "ПРОФИЛЬ ПРОИЗВОДИТЕЛЬНОСТИ"));
        c.addView(hint("Профиль сразу меняет всё ниже. Для динамичных матчей берите «Эконом»: приложение почти не занимает процессор, а игре достаётся вся мощность."), lin(MATCH, WRAP, 0, 0, 0, 0, dp(6)));
        LinearLayout pr = new LinearLayout(this);
        String[] pn = {"Эконом", "Баланс", "Максимум"};
        String[] pi = {"battery", "settings", "bolt"};
        int cur = prefs.getInt("ps_profile", 1);
        for (int i = 0; i < 3; i++) { final int ii = i; pr.addView(ibtn(pi[i], pn[i], i == cur ? C_RED : C_CARD2, 12, v -> setProfile(ii)), lin(0, WRAP, 1f, 0, 0, i < 2 ? dp(6) : 0, 0)); }
        c.addView(pr);
        c.addView(hint(cur == 0 ? "Эконом: голос в фоне выключен, WebView замирает, анимации отключены, записи по умолчанию 50% / 24 к/с."
                : cur == 1 ? "Баланс: голос работает, проверка раз в 4 секунды, средние параметры записи."
                : "Максимум: частые проверки и запись 85% / 60 к/с — нужен мощный телефон."), lin(MATCH, WRAP, 0, dp(6), 0, 0, 0));

        c.addView(sect("settings", "ТОНКАЯ НАСТРОЙКА"));
        LinearLayout t = card();
        t.addView(toggleRow("Голосовой чат в фоне", "Микрофон и звук WebView работают, пока вы в игре. Выключите — WebView замирает и не греет телефон", prefs.getBoolean("ps_voice", true), on -> { prefs.edit().putBoolean("ps_voice", on).apply(); applyPerfProfile(); }));
        t.addView(toggleRow("Без анимаций в игре", "Кнопка и меню без плавных эффектов — меньше нагрузка на графику", prefs.getBoolean("ps_noanim", false), on -> { prefs.edit().putBoolean("ps_noanim", on).apply(); applyPerfProfile(); }));
        t.addView(toggleRow("Низкий приоритет потоков", "Запись, макросы и анализ работают в фоновом приоритете и не мешают игре", prefs.getBoolean("ps_lowprio", true), on -> prefs.edit().putBoolean("ps_lowprio", on).apply()));
        t.addView(toggleRow("Вибрация при нажатиях", "", prefs.getBoolean("ps_haptic", true), on -> prefs.edit().putBoolean("ps_haptic", on).apply()));
        t.addView(sliderRow("Проверка WebView в фоне (реже — экономнее)", 1000, 15000, prefs.getInt("ps_tick", 3000), " мс", v -> prefs.edit().putInt("ps_tick", v).apply()));
        t.addView(sliderRow("Размер кнопки-геймпада", 40, 80, prefs.getInt("ps_bsize", 58), " dp", v -> { prefs.edit().putInt("ps_bsize", v).apply(); if (bubble != null) { hideBubble(); showBubble(); } }));
        t.addView(sliderRow("Прозрачность кнопки-геймпада", 30, 100, prefs.getInt("ps_balpha", 100), " %", v -> { prefs.edit().putInt("ps_balpha", v).apply(); if (bubble != null) bubble.setAlpha(v / 100f); }));
        c.addView(t);

        c.addView(sect("refresh", "ОЧИСТКА"));
        LinearLayout cl = new LinearLayout(this);
        cl.addView(ibtn("refresh", "Освободить память", C_CARD2, 12, v -> {
            System.gc(); try { WebView w = web(); if (w != null) w.clearCache(false); } catch (Throwable ignored) { }
            stkBmps.clear(); toast("Память освобождена");
        }), lin(0, WRAP, 1f, 0, 0, dp(6), 0));
        cl.addView(ibtn("trash", "Сбросить кэш WebView", C_CARD2, 12, v -> { try { WebView w = web(); if (w != null) { w.clearCache(true); toast("Кэш очищен"); } } catch (Throwable ignored) { } }), lin(0, WRAP, 1f, 0, 0, 0, 0));
        c.addView(cl);
        c.addView(sect("info", "ДОСТУПЫ"));
        LinearLayout ac = card();
        ac.addView(permRow("Поверх других приложений", canOverlay(), () -> relay(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())), RC_OVERLAY)));
        boolean accOn = false; try { accOn = MacroService.getInstance() != null; } catch (Throwable ignored) { }
        ac.addView(permRow("Служба макросов (спец. возможности)", accOn, () -> relay(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), 190)));
        ac.addView(permRow("Доступ к экрану (запись, анализ)", capReady(), () -> requestProjection(() -> selectTab(1))));
        ac.addView(permRow("Микрофон", ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED, () -> relay(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())), 191)));
        c.addView(ac);
        return scroll(c);
    }

    private View permRow(String name, boolean ok, final Runnable fix) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(5), 0, dp(5));
        r.addView(icon(ok ? "check" : "info", ok ? C_GREEN : C_AMBER, 17), lin(dp(17), dp(17), 0, 0, 0, dp(8), 0));
        r.addView(tv(name, 12.5f, C_TEXT, false), lin(0, WRAP, 1f, 0, 0, 0, 0));
        if (!ok) r.addView(ibtn(null, "Выдать", C_RED, 11, v -> fix.run()));
        return r;
    }

    // ==================================================================================================
    //  НОВОЕ: ВКЛАДКА «СЕРВЕРЫ» (встроенный PocketMine-MP, комнаты, playit.gg)
    // ==================================================================================================

    private LinearLayout buildSessionCard() {
        LinearLayout box = col();
        box.setPadding(dp(12), dp(12), dp(12), dp(12));
        box.setBackground(rect(C_CARD, 14, C_RED, 1.2f));
        TextView st = tv(status, 12, C_TEXT, false);
        statusViews.add(st);
        box.addView(st, lin(MATCH, WRAP, 0, 0, 0, 0, dp(6)));
        TextView cd = tv("", 24, C_GREEN, true);
        cd.setGravity(Gravity.CENTER); cd.setLetterSpacing(0.08f); cd.setPadding(0, dp(4), 0, dp(8)); cd.setVisibility(View.GONE);
        codeViews.add(cd);
        box.addView(cd);
        LinearLayout row = new LinearLayout(this);
        row.addView(ibtn("copy", "Код", C_CARD2, 12, v -> copyCode()), lin(0, WRAP, 1f, 0, 0, dp(6), 0));
        row.addView(ibtn("play", "Minecraft", C_CARD2, 12, v -> { if (!launchMinecraft()) toast("Minecraft не найден"); }), lin(0, WRAP, 1.4f, 0, 0, 0, 0));
        box.addView(row, lin(MATCH, WRAP, 0, 0, 0, 0, dp(6)));
        final TextView lv = tv(logText(), 10, C_MUTED, false);
        lv.setTypeface(Typeface.MONOSPACE);
        logViews.add(lv);
        final ScrollView ls = new ScrollView(this);
        ls.addView(lv);
        ls.setVisibility(View.GONE);
        LinearLayout row2 = new LinearLayout(this);
        row2.addView(ibtn("file", "Журнал", C_CARD2, 12, v -> ls.setVisibility(ls.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE)), lin(0, WRAP, 1f, 0, 0, dp(6), 0));
        row2.addView(ibtn("copy", "Копировать", C_CARD2, 12, v -> copyLog()), lin(0, WRAP, 1f, 0, 0, 0, 0));
        box.addView(row2, lin(MATCH, WRAP, 0, 0, 0, 0, dp(6)));
        box.addView(ls, lin(MATCH, dp(150), 0, 0, 0, 0, dp(6)));
        box.addView(ibtn("stop", "Остановить комнату", C_RED, 12, v -> stopSession("Остановлено")));
        return box;
    }

    private void saveFields() {
        SharedPreferences.Editor ed = prefs.edit();
        if (tokenInput != null) { String t = tokenInput.getText().toString().trim(); if (!t.isEmpty()) ed.putString("rt_token", t); }
        if (urlInput != null) ed.putString("pl_addr", urlInput.getText().toString().trim());
        if (plPortInput != null) ed.putString("pl_port", plPortInput.getText().toString().trim());
        if (portInput != null) ed.putString("host_port", portInput.getText().toString().trim());
        ed.apply();
    }

    private View tabServers() {
        LinearLayout c = col();
        c.setPadding(dp(14), dp(6), dp(14), dp(18));
        formBox = col();
        sessionBox = buildSessionCard();
        sessionBox.setVisibility(View.GONE);
        c.addView(sessionBox, lin(MATCH, WRAP, 0, dp(8), 0, 0, dp(4)));

        TextView idle = tv(status, 12, C_MUTED, false);
        idle.setPadding(0, dp(8), 0, dp(2));
        statusViews.add(idle);
        formBox.addView(idle);

        formBox.addView(sect("play", "КОМНАТА"));
        LinearLayout rm = card();
        rm.addView(hint("Код появится сразу. Друзья вводят его в приложении или добавляют сервер по публичному адресу. Для встроенного сервера вы заходите через «Друзья» > «Arcade»."), lin(MATCH, WRAP, 0, 0, 0, 0, dp(8)));
        rm.addView(ibtn("server", "Создать на встроенном сервере", 0xFF1E5AAE, 13, v -> { saveFields(); startHost(true); }), lin(MATCH, WRAP, 0, 0, 0, 0, dp(6)));
        rm.addView(ibtn("gamepad", "Создать из мира Minecraft", C_RED, 13, v -> { saveFields(); startHost(false); }), lin(MATCH, WRAP, 0, 0, 0, 0, dp(10)));
        codeInput = field("", "XXXX-XXXX-XXXX", false);
        codeInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        codeInput.setGravity(Gravity.CENTER); codeInput.setTextSize(17); codeInput.setLetterSpacing(0.1f);
        rm.addView(codeInput, lin(MATCH, WRAP, 0, 0, 0, 0, dp(6)));
        rm.addView(ibtn("link", "Войти по коду", 0xFF1E8E4E, 13, v -> startGuest(codeInput.getText().toString())));
        formBox.addView(rm);

        formBox.addView(sect("server", "ВСТРОЕННЫЙ СЕРВЕР"));
        LinearLayout sv = card();
        sv.addView(hint("PocketMine-MP запускается прямо в приложении. PHP и ядро нужной версии скачаются при первом запуске (~100 МБ)."), lin(MATCH, WRAP, 0, 0, 0, 0, dp(8)));
        final TextView verBtn = btn("", C_CARD2, 12.5f, null);
        final Runnable updVer = () -> verBtn.setText("Версия Minecraft: " + SRV_VERSIONS[verIdx()][0] + "  ·  сменить");
        updVer.run();
        verBtn.setOnClickListener(v -> { prefs.edit().putInt("srv_ver", (verIdx() + 1) % SRV_VERSIONS.length).apply(); updVer.run(); toast("Мир нужно загрузить заново для этой версии"); });
        sv.addView(verBtn, lin(MATCH, WRAP, 0, 0, 0, 0, dp(6)));
        srvToggleBtn = btn("", 0xFF2A7A4B, 13, v -> toggleServer());
        sv.addView(srvToggleBtn, lin(MATCH, WRAP, 0, 0, 0, 0, dp(6)));
        sv.addView(ibtn("settings", "Настройки сервера", C_CARD2, 12.5f, v -> serverSettingsPage()), lin(MATCH, WRAP, 0, 0, 0, 0, dp(6)));
        sv.addView(ibtn("folder", "Файлы и папки сервера", C_CARD2, 12.5f, v -> filesPage(srvDir(), srvDir())), lin(MATCH, WRAP, 0, 0, 0, 0, dp(6)));
        LinearLayout up = new LinearLayout(this);
        up.addView(ibtn("import", "Мир", C_CARD2, 12, v -> pickWorld()), lin(0, WRAP, 1f, 0, 0, dp(6), 0));
        up.addView(ibtn("import", "Плагин", C_CARD2, 12, v -> pickFile(RC_PLUGIN)), lin(0, WRAP, 1f, 0, 0, dp(6), 0));
        up.addView(ibtn("import", "Ресурспак", C_CARD2, 12, v -> pickFile(RC_PACK)), lin(0, WRAP, 1f, 0, 0, 0, 0));
        sv.addView(up);
        formBox.addView(sv);

        formBox.addView(sect("link", "ТУННЕЛЬ PLAYIT.GG"));
        LinearLayout pl = card();
        pl.addView(hint("Даёт серверу публичный адрес. В панели playit.gg создайте UDP-туннель Minecraft Bedrock на 127.0.0.1 и порт из поля ниже, затем вставьте secret key агента и адрес."), lin(MATCH, WRAP, 0, 0, 0, 0, dp(8)));
        tokenInput = field(prefs.getString("rt_token", ""), "Secret key агента", false);
        tokenInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        pl.addView(tokenInput, lin(MATCH, WRAP, 0, 0, 0, 0, dp(6)));
        urlInput = field(prefs.getString("pl_addr", ""), "Публичный адрес: name.at.ply.gg:12345", false);
        pl.addView(urlInput, lin(MATCH, WRAP, 0, 0, 0, 0, dp(6)));
        plPortInput = field(prefs.getString("pl_port", ""), "Локальный порт туннеля (" + PL_DEFAULT_PORT + ")", true);
        pl.addView(plPortInput, lin(MATCH, WRAP, 0, 0, 0, 0, dp(6)));
        portInput = field(prefs.getString("host_port", ""), "Порт мира Minecraft (19132)", true);
        pl.addView(portInput, lin(MATCH, WRAP, 0, 0, 0, 0, dp(6)));
        pl.addView(toggleRow("VPN-режим (эксперимент)", "Друзья видны как устройства в сети 10.77.0.x", useVpn, on -> { useVpn = on; prefs.edit().putBoolean("use_vpn", on).apply(); }));
        pl.addView(ibtn("check", "Сохранить и проверить агент", C_CARD2, 12.5f, v -> {
            saveFields();
            new Thread(() -> { String e = ensureAgent(true); setStatus(e == null ? "Агент playit на месте (" + rtBin().getName() + ")" : e); }, "mp-rt-dl").start();
        }), lin(MATCH, WRAP, 0, dp(6), 0, 0, 0));
        formBox.addView(pl);
        c.addView(formBox);

        consoleBox = col();
        consoleBox.setPadding(dp(12), dp(12), dp(12), dp(12));
        consoleBox.setBackground(rect(C_CARD, 14, C_STROKE, 1));
        consoleBox.setVisibility(View.GONE);
        consoleBox.addView(sect("cpu", "КОНСОЛЬ СЕРВЕРА"), lin(MATCH, WRAP, 0, -dp(16), 0, 0, 0));
        consoleView = tv("", 10, C_TEXT, false);
        consoleView.setTypeface(Typeface.MONOSPACE);
        consoleScroll = new ScrollView(this);
        consoleScroll.addView(consoleView);
        consoleBox.addView(consoleScroll, lin(MATCH, dp(200), 0, 0, dp(6), 0, dp(6)));
        LinearLayout crow = new LinearLayout(this);
        consoleInput = field("", "команда, например: op ник", false);
        crow.addView(consoleInput, lin(0, WRAP, 1f, 0, 0, dp(6), 0));
        crow.addView(ibtn("chev", "", C_RED, 14, v -> { String cmd = consoleInput.getText().toString().trim(); if (!cmd.isEmpty()) { consoleInput.setText(""); sendConsole(cmd); } }), lin(dp(48), dp(40), 0, 0, 0, 0, 0));
        consoleBox.addView(crow);
        c.addView(consoleBox, lin(MATCH, WRAP, 0, dp(12), 0, 0, 0));
        View v = scroll(c);
        menuSub.setText("Встроенный сервер и комнаты");
        return v;
    }

    private void serverSettingsPage() {
        saveFields();
        final LinearLayout box = col();
        box.setPadding(dp(12), dp(8), dp(12), dp(14));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView bk = icon("back", C_TEXT, 18); bk.setPadding(dp(7), dp(7), dp(7), dp(7)); bk.setBackground(rect(C_CARD2, 11, 0, 0));
        bk.setOnClickListener(v -> selectTab(4)); pressable(bk);
        top.addView(bk, lin(dp(34), dp(34), 0, 0, dp(8), 0, 0));
        top.addView(tv("Настройки сервера", 14, C_TEXT, true));
        box.addView(top, lin(MATCH, WRAP, 0, 0, 0, 0, dp(8)));
        final HashMap<String, String> vals = new HashMap<>();
        for (final String[] e : SP_ENUMS) {
            final String key = e[0];
            String cur = sp(key, key.equals("gamemode") ? GAMEMODES[prefs.getInt("srv_gm", 0) % GAMEMODES.length] : e[2]);
            boolean ok = false;
            for (int j = 2; j < e.length; j++) if (e[j].equals(cur)) ok = true;
            vals.put(key, ok ? cur : e[2]);
            final TextView b = btn("", C_CARD2, 12.5f, null);
            final Runnable upd = () -> b.setText(e[1] + ": " + vals.get(key));
            upd.run();
            b.setOnClickListener(v -> {
                int idx = 2;
                for (int j = 2; j < e.length; j++) if (e[j].equals(vals.get(key))) idx = j;
                idx = (idx + 1 >= e.length) ? 2 : idx + 1;
                vals.put(key, e[idx]); upd.run();
            });
            box.addView(b, lin(MATCH, WRAP, 0, 0, 0, 0, dp(6)));
        }
        final HashMap<String, EditText> ets = new HashMap<>();
        for (String[] t : SP_TEXTS) {
            box.addView(tv(t[1], 11, C_MUTED, false), lin(MATCH, WRAP, 0, dp(4), 0, 0, dp(3)));
            EditText et = settingsField(sp(t[0], t[2]), t[0].equals("max-players") || t[0].equals("view-distance"));
            ets.put(t[0], et);
            box.addView(et, lin(MATCH, WRAP, 0, 0, 0, 0, dp(4)));
        }
        box.addView(hint("Имя мира (папка в worlds/). Новое имя создаст новый мир; мир из .mcworld лежит в «world». Меньшая дальность прорисовки — меньше нагрузка на телефон."), lin(MATCH, WRAP, 0, dp(6), 0, 0, dp(4)));
        final EditText wn = settingsField(prefs.getString("srv_world", "world"), false);
        box.addView(wn, lin(MATCH, WRAP, 0, 0, 0, 0, dp(10)));
        box.addView(ibtn("save", "Сохранить", C_RED, 13, v -> {
            SharedPreferences.Editor ed = prefs.edit();
            for (Map.Entry<String, String> en : vals.entrySet()) ed.putString("sp_" + en.getKey(), en.getValue());
            for (String[] t : SP_TEXTS) {
                String s = ets.get(t[0]).getText().toString().trim();
                if (t[0].equals("max-players") || t[0].equals("view-distance")) {
                    try { s = String.valueOf(Math.max(1, Math.min(t[0].equals("max-players") ? 100 : 32, Integer.parseInt(s)))); } catch (Exception x) { s = t[2]; }
                }
                ed.putString("sp_" + t[0], s);
            }
            String w2 = wn.getText().toString().trim().replaceAll("[^A-Za-z0-9_\\-]", "_");
            ed.putString("srv_world", w2.isEmpty() ? "world" : w2);
            ed.apply();
            toast(srvAlive() ? "Сохранено. Применится после перезапуска сервера" : "Сохранено");
            selectTab(4);
        }));
        setContent(scroll(box), () -> selectTab(4));
    }

    // ----- проводник файлов сервера -----

    private void filesPage(final File root, final File dir) {
        LinearLayout c = col();
        c.setPadding(dp(12), dp(8), dp(12), dp(14));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(6), dp(8), dp(6));
        top.setBackground(rect(C_CARD2, 12, 0, 0));
        ImageView bk = icon("back", C_TEXT, 18); bk.setPadding(dp(6), dp(6), dp(6), dp(6));
        final Runnable up = () -> { if (dir.equals(root)) selectTab(4); else filesPage(root, dir.getParentFile()); };
        bk.setOnClickListener(v -> up.run()); pressable(bk);
        top.addView(bk, lin(dp(32), dp(32), 0, 0, dp(6), 0, 0));
        String rel = dir.getAbsolutePath().substring(root.getAbsolutePath().length());
        top.addView(tv("pmmp" + (rel.isEmpty() ? "/" : rel) + (srvAlive() ? "  · сервер работает" : ""), 11.5f, C_MUTED, false), lin(0, WRAP, 1f, 0, 0, 0, 0));
        c.addView(top, lin(MATCH, WRAP, 0, 0, 0, 0, dp(8)));
        File[] fs = dir.listFiles();
        if (fs == null || fs.length == 0) c.addView(hint("(пусто)"));
        else {
            Arrays.sort(fs, (a, b) -> { if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1; return a.getName().compareToIgnoreCase(b.getName()); });
            int shown = 0;
            for (final File f : fs) {
                if (shown++ >= 300) { c.addView(hint("… и ещё " + (fs.length - 300))); break; }
                final boolean d = f.isDirectory();
                c.addView(fileItem(d ? "folder" : "file", f.getName(), d ? "папка" : humanSize(f.length()), false,
                        v -> { if (d) filesPage(root, f); else viewFilePage(root, dir, f); },
                        v -> { vib(); if (srvAlive()) { toast("Сначала выключите сервер"); return true; } confirm((d ? "Папку «" : "Файл «") + f.getName() + "» удалить безвозвратно?", "Удалить", () -> { deleteRec(f); filesPage(root, dir); }); return true; }));
            }
            c.addView(hint("Нажатие — открыть, долгое нажатие — удалить."));
        }
        setContent(scroll(c), up);
    }

    private void viewFilePage(final File root, final File dir, final File f) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        boolean text = false;
        for (String ext : new String[]{".txt", ".json", ".yml", ".yaml", ".properties", ".log", ".md", ".ini", ".cfg", ".conf", ".xml", ".csv", ".php"}) if (n.endsWith(ext)) { text = true; break; }
        if (!text) { toast("Этот тип файла нельзя открыть в приложении"); return; }
        if (f.length() > 256 * 1024) { toast("Файл слишком большой (" + humanSize(f.length()) + ")"); return; }
        String content;
        try {
            byte[] b = new byte[(int) f.length()];
            try (FileInputStream in = new FileInputStream(f)) { int got = 0; while (got < b.length) { int r = in.read(b, got, b.length - got); if (r < 0) break; got += r; } }
            content = new String(b, StandardCharsets.UTF_8);
        } catch (Exception e) { toast("Не удалось прочитать: " + e.getMessage()); return; }
        LinearLayout c = col();
        c.setPadding(dp(12), dp(8), dp(12), dp(14));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView bk = icon("back", C_TEXT, 18); bk.setPadding(dp(7), dp(7), dp(7), dp(7)); bk.setBackground(rect(C_CARD2, 11, 0, 0));
        bk.setOnClickListener(v -> filesPage(root, dir)); pressable(bk);
        top.addView(bk, lin(dp(34), dp(34), 0, 0, dp(8), 0, 0));
        top.addView(tv(f.getName(), 13.5f, C_TEXT, true));
        c.addView(top, lin(MATCH, WRAP, 0, 0, 0, 0, dp(8)));
        final EditText et = new EditText(this);
        et.setText(content); et.setTextColor(C_TEXT); et.setTextSize(11); et.setTypeface(Typeface.MONOSPACE);
        et.setGravity(Gravity.TOP | Gravity.START);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        et.setBackground(rect(0xFF0C0C0F, 12, C_STROKE, 1)); et.setPadding(dp(10), dp(10), dp(10), dp(10)); et.setMinHeight(dp(220));
        c.addView(et, lin(MATCH, WRAP, 0, 0, 0, 0, dp(8)));
        c.addView(ibtn("save", "Сохранить", C_RED, 13, v -> {
            try (FileOutputStream o = new FileOutputStream(f)) { o.write(et.getText().toString().getBytes(StandardCharsets.UTF_8)); toast(srvAlive() ? "Сохранено. Вступит в силу после перезапуска сервера" : "Сохранено"); }
            catch (Exception e) { toast("Не удалось сохранить: " + e.getMessage()); }
        }));
        setContent(scroll(c), () -> filesPage(root, dir));
    }

}

package com.cr.arcade;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.BridgeActivity;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.Locale;


/**
 * ARCADE — мультиплеер для Minecraft Bedrock (аналог Omlet Arcade).
 *
 * Как это работает (без собственного сервера):
 *  - Rustunnel (rustunnel.com) — туннель: клиент rustunnel запускается прямо в приложении командой
 *    «rustunnel udp <порт>» и выдаёт публичный адрес host:port. Он кодируется в 12-символьный код комнаты.
 *  - Хост: пакеты друзей из туннеля попадают в локальный мир Minecraft (UDP 19132) или во встроенный сервер.
 *  - Гость: приложение поднимает на 19132 «фейковый LAN-сервер» (отвечает на RakNet-пинги), поэтому
 *    в Minecraft во вкладке «Друзья» появляется «Мой мир». Пакеты идут на публичный адрес хоста.
 *    Друг может зайти и без приложения: «Серверы» > «Добавить» > публичный адрес и порт.
 *  - Встроенный сервер (PocketMine-MP) можно включать и выключать кнопкой, а его файлы — смотреть и править.
 */
public class MainActivity extends BridgeActivity {

    private static final String TAG = "ArcadeMP";

    // ===== Rustunnel (UDP-туннель: локальный сервер > публичный host:port) =====
    // API-ключ вводится в приложении (раздел «RUSTUNNEL») и хранится в SharedPreferences, в коде его нет.
    private static final String[] RT_REGION_NAMES = {"Европа (Хельсинки)", "США (Орегон)", "Азия (Сингапур)"};
    private static final String[] RT_HOSTS = {"eu.edge.rustunnel.com", "us.edge.rustunnel.com", "ap.edge.rustunnel.com"};
    private static final int RT_CONTROL_PORT = 4040;

    private static final String[] MC_PACKAGES = {"com.mojang.minecraftpe", "com.mojang.minecraftpe.preview"};
    private static final int MC_PORT = 19132;
    private static final int RC_OVERLAY = 101, RC_PERMS = 110, RC_VPN = 120, RC_PICK = 130;
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
    private volatile Process rtProc;               // клиент rustunnel
    private volatile DatagramSocket fwdSock;       // хост: сюда rustunnel приносит пакеты друзей
    private volatile DatagramSocket guestOut;      // гость: исходящий сокет к публичному адресу хоста
    private volatile String publicAddr = "";       // host:port, выданный Rustunnel
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
    private EditText codeInput, portInput, tokenInput, urlInput;
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

        HostPeer(InetSocketAddress peer) throws Exception { this.peer = peer; this.sock = new DatagramSocket(); }
    }

    // ==================== Жизненный цикл ====================

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appCtx = getApplicationContext();
        wm = (WindowManager) appCtx.getSystemService(Context.WINDOW_SERVICE);
        prefs = getSharedPreferences("arcade_mp", MODE_PRIVATE);
        useVpn = prefs.getBoolean("use_vpn", false);
        if (prefs.getBoolean("active", false)) {
            // прошлая сессия не завершилась штатно > процесс убила система
            status = "Прошлая сессия оборвалась: систему убила приложение.\nПоследний статус: " + prefs.getString("last", "?");
            prefs.edit().putBoolean("active", false).apply();
        }
        buildScreen();
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            try { ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, RC_PERMS); } catch (Exception ignored) { }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        hideOverlay();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mode != M_IDLE && !isFinishing() && !isChangingConfigurations()) showOverlay();
    }

    @Override
    public void onDestroy() {
        // если система просто выгрузила Activity — сессия и оверлей продолжают жить
        if (isFinishing()) {
            hideOverlay();
            if (mode != M_IDLE) stopSession("Остановлено");
            if (serverStandalone) stopBuiltinServer();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (mode != M_IDLE) moveTaskToBack(true); else super.onBackPressed();
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == RC_PICK && res == RESULT_OK && data != null && data.getData() != null) {
            final Uri u = data.getData();
            new Thread(() -> importWorld(u), "mp-import").start();
            return;
        }
        if (req == RC_VPN) {
            vpnAsked = false;
            if (res == RESULT_OK) { log("Разрешение VPN выдано"); vpnDenied = false; }
            else { vpnDenied = true; log("Разрешение VPN отклонено — работаю без него (мир, скорее всего, не будет отвечать друзьям)"); }
            ui.post(this::startHost);
        }
    }

    private boolean canOverlay() { return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this); }

    private boolean ensureOverlay() {
        if (canOverlay() || OverlayAccService.instance != null) return true;
        new AlertDialog.Builder(this)
                .setTitle("Нужен оверлей")
                .setMessage("Разрешите показ поверх других приложений — код и статус будут видны поверх Minecraft.")
                .setPositiveButton("Открыть настройки", (d, w) ->
                        startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())), RC_OVERLAY))
                .setNegativeButton("Отмена", null).show();
        return false;
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
        if (prefs.getString("rt_token", "").isEmpty()) { setStatus("Вставьте API-ключ Rustunnel (раздел «RUSTUNNEL» выше) — он нужен, чтобы сервер стал доступен из интернета"); return; }
        builtin = useBuiltin;
        int hp = MC_PORT;
        try { hp = Integer.parseInt(portInput.getText().toString().trim()); } catch (Exception ignored) { }
        if (hp < 1 || hp > 65535) hp = MC_PORT;
        hostPort = hp;
        if (useBuiltin && !externalSrv) hostPort = 19132;
        if (useVpn && !vpnDenied && !useBuiltin) {
            Intent vi = null;
            try { vi = VpnService.prepare(this); } catch (Exception e) { log("VPN: проверка разрешения не удалась: " + e.getMessage()); }
            if (vi != null) {
                if (!vpnAsked) {
                    vpnAsked = true;
                    log("Прошу разрешение VPN (нужно, чтобы Minecraft принимал друзей как устройства в сети)");
                    try { startActivityForResult(vi, RC_VPN); return; } catch (Exception e) { log("Не открылось окно разрешения VPN: " + e.getMessage()); vpnDenied = true; }
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
        begin(M_GUEST, "Подключаюсь к хосту…");
        Thread t = new Thread(() -> guestLoop(h), "mp-guest");
        engine = t;
        t.start();
    }

    private synchronized void stopSession(String msg) {
        stopFlag = true;
        mode = M_IDLE;
        code = "";
        stopRustunnel();
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
        try { prefs.edit().putBoolean("active", false).putString("last", msg).apply(); } catch (Exception ignored) { }
        ui.post(() -> {
            pushText();
            if ("Остановлено".equals(msg)) hideOverlay();
            else ui.postDelayed(() -> { if (mode == M_IDLE) hideOverlay(); }, 60000); // оставляем текст ошибки на экране
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

    /** Foreground-сервис, чтобы Android не убивал процесс, пока вы в Minecraft. */
    public static class MpService extends Service {
        @Override public IBinder onBind(Intent i) { return null; }

        @Override public int onStartCommand(Intent i, int flags, int startId) {
            String ch = "arcade_mp";
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                nm.createNotificationChannel(new NotificationChannel(ch, "Arcade мультиплеер", NotificationManager.IMPORTANCE_LOW));
            }
            Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, ch) : new Notification.Builder(this);
            int icon = getApplicationInfo().icon;
            b.setContentTitle("ARCADE — мультиплеер активен")
                    .setContentText("Комната работает, пока вы играете")
                    .setSmallIcon(icon != 0 ? icon : android.R.drawable.ic_dialog_info)
                    .setOngoing(true);
            Notification n = b.build();
            try {
                if (Build.VERSION.SDK_INT >= 34) startForeground(77, n, 0x40000000); // specialUse
                else if (Build.VERSION.SDK_INT >= 29) startForeground(77, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
                else startForeground(77, n);
            } catch (Exception e) {
                Log.w(TAG, "fg", e);
                stopSelf(); // иначе Android убьёт приложение через ~10 с
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

    private void log(String msg) {
        Log.i(TAG, msg);
        synchronized (logBuf) {
            logBuf.addLast(logFmt.format(new java.util.Date()) + " " + msg);
            while (logBuf.size() > 80) logBuf.removeFirst();
        }
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
            catch (Exception e) { log("✗ Не удалось отправить команду: " + e.getMessage()); }
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
            if (err != null) { lastErr = err; log("✗ PHP " + v + ": " + err); continue; }
            setStatus("Распаковываю PHP…");
            try {
                File dest = new File(srvDir(), "php");
                deleteRec(dest);
                dest.mkdirs();
                int n = untarGz(tgz, dest);
                tgz.delete();
                log("✓ PHP распакован (" + n + " файлов)");
            } catch (Exception e) { lastErr = "распаковка: " + e.getMessage(); log("✗ " + lastErr); continue; }
            php = phpBin();
            if (!php.exists()) { lastErr = "в архиве нет bin/php7/bin/php"; log("✗ " + lastErr); continue; }
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
            log("✓ Ограничение на ники в сервере снято");
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
                log("✗ Не удалось скачать сервер: " + dl);
                try (InputStream in = getAssets().open(PHAR_ASSET); java.io.FileOutputStream out = new java.io.FileOutputStream(phar)) {
                    log("Беру запасной сервер из ресурсов приложения (assets/" + PHAR_ASSET + ") — его версия может не совпасть с выбранной!");
                    byte[] b = new byte[65536]; int n;
                    while ((n = in.read(b)) > 0) out.write(b, 0, n);
                } catch (IOException e) {
                    phar.delete();
                    return "Не удалось получить сервер для " + gameVer + ".\nСкачивание: " + dl + "\nЗапасного файла в assets нет.\nПроверьте интернет или положите PocketMine-MP.phar в app/src/main/assets/.";
                }
            } else log("✓ Сервер скачан: PocketMine-MP " + tag);
        }
        curPhar = phar;
        String pe = patchPharNames(phar);
        if (pe != null) log("⚠ Не удалось снять ограничение на ники: " + pe + ". Ники с кириллицей, # или длиннее 16 символов сервер не пустит.");
        try {
            int gm = prefs.getInt("srv_gm", 0);
            String world = prefs.getString("srv_world", "world");
            String props = "motd=Arcade\nserver-port=19132\nserver-portv6=19133\nenable-ipv6=off\ngamemode=" + GAMEMODES[gm] + "\nmax-players=10\n"
                    + "xbox-auth=off\nwhite-list=off\nenable-query=off\nlevel-name=" + world + "\n";
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
            if (pingLocal(19132)) { log("✓ Встроенный сервер запущен и отвечает на порту 19132"); return null; }
            Thread.sleep(1000);
        }
        return stopFlag ? "Остановлено" : "Сервер не ответил за 150 с. Последние строки:\n" + srvTailText();
    }

    /** Режим «сервер уже запущен в Termux»: просто ждём ответа на 127.0.0.1:порт. */
    private boolean waitExternalServer() throws InterruptedException {
        setStatus("Ищу сервер на 127.0.0.1:" + hostPort + "…");
        long end = SystemClock.elapsedRealtime() + 20000;
        int tries = 0;
        while (!stopFlag && SystemClock.elapsedRealtime() < end) {
            if (pingLocal(hostPort)) { log("✓ Сервер отвечает на 127.0.0.1:" + hostPort); return true; }
            if (++tries % 5 == 0) log("Сервер на 127.0.0.1:" + hostPort + " пока не отвечает (попытка " + tries + ")");
            Thread.sleep(1000);
        }
        if (!stopFlag) stopSession("Сервер на 127.0.0.1:" + hostPort + " не отвечает.\nЗапустите сервер в Termux (он должен слушать UDP-порт " + hostPort + ") и нажмите ещё раз.");
        return false;
    }

    private void stopBuiltinServer() {
        final Process p = srvProc;
        srvProc = null;
        if (p == null) return;
        new Thread(() -> {
            try { p.getOutputStream().write("stop\n".getBytes(StandardCharsets.UTF_8)); p.getOutputStream().flush(); } catch (Exception ignored) { }
            try { if (!p.waitFor(5, TimeUnit.SECONDS)) p.destroyForcibly(); } catch (Exception e) { p.destroyForcibly(); }
        }, "mp-srv-stop").start();
    }

    private void pickWorld() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, RC_PICK);
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
            if (files == 0) log("✗ Архив пустой или это не .mcworld");
            else if (!hasLevel) log("⚠ Мир распакован (" + files + " файлов), но level.dat не в корне архива — сервер может не найти мир");
            else log("✓ Мир импортирован в сервер версии " + SRV_VERSIONS[verIdx()][0] + ": " + files + " файлов. Он загрузится при следующем запуске");
            toast("Мир импортирован");
        } catch (Exception e) {
            log("✗ Не удалось импортировать мир: " + e.getClass().getSimpleName() + " " + e.getMessage());
            toast("Ошибка импорта мира");
        }
    }

    private String hostStatus() {
        InetSocketAddress ht = hostTarget;
        int port = ht != null ? ht.getPort() : hostPort;
        StringBuilder sb = new StringBuilder("Код создан — отправьте другу.\n");
        sb.append(pubLine());
        if (builtin) {
            sb.append(externalSrv ? "Сервер в Termux найден ✓" : "Встроенный сервер работает ✓").append("\nСвой вход: Серверы > 127.0.0.1 : ").append(hostPort);
            int bn = hostPeers.size();
            if (bn > 0) sb.append("\nПодключено игроков: ").append(bn).append(" · пакетов от друга: ").append(cntFromGuest).append(", ответов сервера: ").append(cntFromWorld);
            return sb.toString();
        }
        if (worldFound) sb.append("Мир найден ✓");
        else sb.append("⚠ Ваш мир не отвечает на порту ").append(port).append(". Скорее всего, в настройках мира выключено «Виден игрокам в локальной сети» (или мир открыт только для друзей Xbox). Включите — приложение подхватит само за пару секунд.\n").append(probeInfo);
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
                if (externalSrv) { if (!waitExternalServer()) return; }
                else { if (!startBuiltinServer()) return; hostPort = 19132; }
                hostTarget = new InetSocketAddress("127.0.0.1", hostPort);
                worldFound = true;
            } else setupTun();
            // Пакеты друзей rustunnel отдаёт на локальный порт. Для мира из Minecraft это наш форвардер
            // (он сам найдёт, где слушает мир), для встроенного сервера — прямо порт сервера.
            int tunnelPort = hostPort;
            if (!builtin) {
                DatagramSocket fs = new DatagramSocket(0, InetAddress.getByName("127.0.0.1"));
                fwdSock = fs;
                tunnelPort = fs.getLocalPort();
                Thread ft = new Thread(() -> fwdLoop(fs), "mp-fwd");
                ft.setDaemon(true);
                ft.start();
                log("Форвардер пакетов: 127.0.0.1:" + tunnelPort);
            }
            String rerr = startRustunnel(tunnelPort);
            if (rerr != null) { stopSession(rerr); return; }
            log("Rustunnel: публичный адрес " + publicAddr + " > код " + pretty(code));
            setStatus(hostStatus());
            if (builtin) { log("Ваш вход на сервер: Minecraft > Серверы > Добавить > 127.0.0.1, порт " + hostPort); ui.post(this::launchMinecraft); }

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
                if (rp != null && !alive(rp)) { Thread.sleep(300); stopSession("Rustunnel остановился (код " + rp.exitValue() + "):\n" + rtTailText()); return; }
                if (n - lastClean > 10000) {
                    lastClean = n;
                    for (Map.Entry<String, HostPeer> e : hostPeers.entrySet()) {
                        if (n - e.getValue().last > 120000) { try { e.getValue().sock.close(); } catch (Exception ignored) { } hostPeers.remove(e.getKey()); }
                    }
                }
                if (cntFromGuest > 0 && firstGuestAt == 0) firstGuestAt = n;
                if (!warnedSilent && !builtin && cntFromGuest > 0 && cntFromWorld == 0 && n - firstGuestAt > 12000) {
                    warnedSilent = true;
                    log("⚠ Друг шлёт пакеты (" + cntFromGuest + "), но ваш мир не отвечает ни на один путь. Это значит, что мир, скорее всего, не слушает порт " + hostTarget.getPort()
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
        if (hp == null) {
            try {
                hp = new HostPeer(peer);
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

    private void hostReader(HostPeer hp) {
        byte[] buf = new byte[4096];
        while (!stopFlag) {
            try {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                hp.sock.receive(p);
                hp.last = SystemClock.elapsedRealtime();
                hp.recv++;
                if (++cntFromWorld == 1) log("✓ Мир ответил (прямой путь, с " + p.getAddress().getHostAddress() + ":" + p.getPort() + ")");
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
                log("✗ VPN-туннель не создан: " + (MpVpnService.err.isEmpty() ? "служба не ответила за 4 с (проверьте, что MpVpnService добавлен в манифест)" : MpVpnService.err));
                return;
            }
            tunIn = new FileInputStream(t.getFileDescriptor());
            tunOut = new FileOutputStream(t.getFileDescriptor());
            tunReady = true;
            Thread r = new Thread(this::tunReaderLoop, "mp-tun");
            r.setDaemon(true);
            r.start();
            log("✓ VPN-туннель создан (адрес 10.77.0.1). Друзья будут видны Minecraft как 10.77.0.x");
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log("✗ Ошибка VPN: " + e.getClass().getSimpleName() + " " + e.getMessage());
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
        if (vip < 0) { if (tunErr++ == 0) log("✗ Слишком много подключений для VPN-адресов"); return; }
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
            if (tunErr++ < 3) log("✗ Ошибка записи в VPN: " + e.getClass().getSimpleName() + " " + e.getMessage());
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
                if (++cntFromWorld == 1) log("✓ Мир ответил через VPN (" + plen + " байт)");
                fwdSend(peer, payload, plen);
            }
        } catch (Exception e) {
            if (!stopFlag) log("✗ Чтение VPN остановилось: " + e.getClass().getSimpleName() + " " + e.getMessage());
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
            pi.append("порты ").append(MC_PORT).append('–').append(MC_PORT + 10);
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
                            // ответил мир на ДРУГОМ устройстве (например у друга в той же Wi-Fi) — это не наш мир
                            String fh = from.getHostAddress();
                            if (!foreignSeen.contains(fh)) { foreignSeen.add(fh); log("Ответил мир на другом устройстве " + fh + ":" + rp.getPort() + " — игнорирую, это не ваш мир"); }
                            continue;
                        }
                        hostTarget = new InetSocketAddress(from, rp.getPort());
                        log("✓ Найден ваш мир: " + from.getHostAddress() + ":" + rp.getPort());
                        return true;
                    }
                } catch (SocketTimeoutException ignored) { break; }
            }
        } catch (Exception ignored) {
        } finally {
            if (s != null) s.close();
        }
        return false;
    }

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
        } catch (InterruptedException ignored) {
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

    private byte[] makePing() {
        ByteBuffer b = ByteBuffer.allocate(33);
        b.put((byte) 0x01).putLong(System.currentTimeMillis()).put(MAGIC).putLong(guid);
        return b.array();
    }

    // ==================== Код комнаты ====================

    /** Код: тип(2 бита: 0 = IPv4, 1..3 = регион eu/us/ap) + IPv4(32) + порт(16) + контрольная сумма(8) = 58 бит > 12 символов. */
    private String encodeAddr(String host, int port) throws Exception {
        int type = 0;
        byte[] ip = new byte[4];
        for (int i = 0; i < RT_HOSTS.length; i++) if (RT_HOSTS[i].equalsIgnoreCase(host)) type = i + 1;
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
            String host = type == 0 ? ((ip[0] & 0xff) + "." + (ip[1] & 0xff) + "." + (ip[2] & 0xff) + "." + (ip[3] & 0xff)) : RT_HOSTS[type - 1];
            return InetSocketAddress.createUnresolved(host, port);
        } catch (Exception e) { return null; }
    }

    private String pretty(String c) {
        if (c == null || c.length() != 12) return "";
        return c.substring(0, 4) + "-" + c.substring(4, 8) + "-" + c.substring(8, 12);
    }

    // ==================== Rustunnel ====================

    private void saveRtSettings() {
        try {
            android.content.SharedPreferences.Editor ed = prefs.edit();
            if (tokenInput != null) { String t = tokenInput.getText().toString().trim(); if (!t.isEmpty()) ed.putString("rt_token", t); }
            if (urlInput != null) ed.putString("rt_url", urlInput.getText().toString().trim());
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
        try { f.send(new DatagramPacket(d, 0, len, peer)); } catch (Exception ignored) { }
    }

    /** Принимает пакеты друзей от rustunnel (у каждого друга свой локальный порт-источник). */
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

    /** Файл клиента: сначала librustunnel.so из jniLibs (его Android разрешает запускать), иначе скачанный. */
    private File rtBin() {
        File lib = new File(getApplicationInfo().nativeLibraryDir, "librustunnel.so");
        return lib.exists() ? lib : new File(new File(srvDir(), "rustunnel"), "rustunnel");
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

    /** Готовит клиент rustunnel. Возвращает текст ошибки или null. */
    private String ensureRustunnel(boolean force) {
        File b = rtBin();
        boolean fromLib = b.getPath().startsWith(getApplicationInfo().nativeLibraryDir);
        if (force && !fromLib && b.exists()) b.delete();
        if (!b.exists()) {
            String url = prefs.getString("rt_url", "").trim();
            if (url.isEmpty())
                return "Нет клиента Rustunnel для Android.\nЛибо положите librustunnel.so в app/src/main/jniLibs/arm64-v8a/ (лучший вариант), либо впишите ссылку на сборку под Android в поле «Ссылка на клиент» (см. README_RUSTUNNEL.md).";
            if (Build.SUPPORTED_ABIS.length == 0 || !Build.SUPPORTED_ABIS[0].contains("arm64"))
                return "Клиент Rustunnel есть только для arm64, у вас: " + (Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "?");
            File dir = new File(srvDir(), "rustunnel");
            dir.mkdirs();
            boolean tgz = url.endsWith(".tar.gz") || url.endsWith(".tgz");
            File dl = new File(dir, tgz ? "rt.tar.gz" : "rustunnel");
            setStatus("Скачиваю клиент Rustunnel…");
            log("Скачиваю клиент: " + url);
            String err = downloadFile(url, dl, 1L << 20);
            if (err != null) return "Не удалось скачать клиент: " + err;
            if (tgz) {
                try {
                    File tmp = new File(dir, "x");
                    deleteRec(tmp);
                    tmp.mkdirs();
                    untarGz(dl, tmp);
                    dl.delete();
                    File f = findFile(tmp, "rustunnel");
                    if (f == null) { deleteRec(tmp); return "В архиве нет файла «rustunnel»"; }
                    File target = new File(dir, "rustunnel");
                    target.delete();
                    if (!f.renameTo(target)) { deleteRec(tmp); return "Не удалось сохранить файл клиента"; }
                    deleteRec(tmp);
                } catch (Exception e) { return "Распаковка клиента: " + e.getMessage(); }
            }
            b = rtBin();
        }
        if (!b.exists()) return "Файл клиента не найден";
        b.setExecutable(true, false);
        if (isGlibcElf(b)) {
            if (!fromLib) b.delete();
            return "Этот файл — сборка Rustunnel для обычного Linux (glibc), на Android она не запустится.\nНужна сборка под Android (bionic) — см. README_RUSTUNNEL.md.";
        }
        return null;
    }

    private void applyPublicAddr(String pa) {
        try {
            pa = pa.trim();
            int c = pa.lastIndexOf(':');
            if (c < 0) throw new IOException("нет порта в адресе «" + pa + "»");
            String host = pa.substring(0, c);
            int port = Integer.parseInt(pa.substring(c + 1));
            String cd = encodeAddr(host, port);
            publicAddr = pa;
            code = cd;
            log("✓ Rustunnel открыл туннель: " + pa + " > код " + pretty(cd));
            pushText();
        } catch (Exception e) {
            rtErr = "не удалось разобрать публичный адрес «" + pa + "»: " + e.getMessage();
        }
    }

    private void rtOnLine(String line) {
        rtTailAdd(line);
        if (!line.startsWith("{")) return;
        try {
            JSONObject o = new JSONObject(line);
            String ev = o.optString("event", "");
            if ("tunnel_ready".equals(ev)) {
                String pa = o.optString("public_addr", "");
                if (pa.isEmpty()) pa = o.optString("public_url", "").replaceFirst("^[A-Za-z0-9+.-]+://", "");
                pa = pa.replaceAll("/.*$", "");
                if (!pa.isEmpty()) applyPublicAddr(pa);
            } else if ("reconnecting".equals(ev)) {
                log("Rustunnel: связь потеряна, переподключаюсь (" + o.optString("reason", "?") + ")");
            } else if ("reconnected".equals(ev)) {
                log("Rustunnel: переподключился");
            } else if ("error".equals(ev)) {
                String m = o.optString("message", "?");
                String hint = o.optString("hint", "");
                rtErr = m + (hint.isEmpty() ? "" : "\n" + hint);
                log("✗ Rustunnel: " + m);
            }
        } catch (Exception ignored) { }
    }

    /** Запускает `rustunnel udp <порт>` и ждёт публичный адрес. Возвращает текст ошибки или null. */
    private String startRustunnel(int port) throws InterruptedException {
        setStatus("Запускаю Rustunnel…");
        String err = ensureRustunnel(false);
        if (err != null) return err;
        int ri = Math.max(0, Math.min(RT_HOSTS.length - 1, prefs.getInt("rt_region", 0)));
        String token = prefs.getString("rt_token", "");
        File home = new File(srvDir(), "rthome");
        home.mkdirs();
        final Process p;
        try {
            ProcessBuilder pb = new ProcessBuilder(rtBin().getAbsolutePath(), "udp", String.valueOf(port),
                    "--server", RT_HOSTS[ri] + ":" + RT_CONTROL_PORT, "--local-host", "127.0.0.1", "--json", "--no-tui", "--no-inspect");
            pb.directory(home);
            pb.environment().put("HOME", home.getAbsolutePath());
            pb.environment().put("TMPDIR", getCacheDir().getAbsolutePath());
            pb.environment().put("RUSTUNNEL_TOKEN", token); // через окружение, чтобы ключ не светился в списке процессов
            synchronized (rtTail) { rtTail.clear(); }
            rtErr = "";
            p = pb.start();
            rtProc = p;
        } catch (Exception e) {
            String m = String.valueOf(e.getMessage());
            String hint = m.contains("denied") ? (getApplicationInfo().targetSdkVersion > 28
                    ? "\nAndroid не даёт запускать скачанные файлы при targetSdkVersion " + getApplicationInfo().targetSdkVersion + ". Положите клиент как app/src/main/jniLibs/arm64-v8a/librustunnel.so (и extractNativeLibs=true) либо поставьте targetSdkVersion 28."
                    : "\nУ файла нет права на запуск. Очистите данные приложения и повторите.")
                    : (m.contains("No such file") ? "\nОбычно это значит, что клиент собран не под Android (нет загрузчика glibc). Нужна сборка под Android — см. README_RUSTUNNEL.md." : "");
            return "Не удалось запустить Rustunnel: " + m + hint;
        }
        Thread o = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String l;
                while ((l = br.readLine()) != null) rtOnLine(l);
            } catch (Exception ignored) { }
        }, "mp-rt-out");
        o.setDaemon(true);
        o.start();
        Thread e2 = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))) {
                String l;
                while ((l = br.readLine()) != null) {
                    rtTailAdd(l);
                    String lo = l.toLowerCase(Locale.ROOT);
                    if (lo.contains("error") || lo.contains("fail") || lo.contains("denied") || lo.contains("limit")) log("[rustunnel] " + (l.length() > 140 ? l.substring(0, 140) : l));
                }
            } catch (Exception ignored) { }
        }, "mp-rt-err");
        e2.setDaemon(true);
        e2.start();
        log("Rustunnel запущен: udp " + port + " > " + RT_HOSTS[ri]);

        long end = SystemClock.elapsedRealtime() + 45000;
        while (!stopFlag && SystemClock.elapsedRealtime() < end) {
            if (!publicAddr.isEmpty() && !code.isEmpty()) return null;
            if (!rtErr.isEmpty()) { Thread.sleep(200); return "Rustunnel: " + rtErr; }
            if (!alive(p)) { Thread.sleep(300); return "Rustunnel завершился (код " + p.exitValue() + "):\n" + rtTailText(); }
            Thread.sleep(200);
        }
        return stopFlag ? "Остановлено" : "Rustunnel не открыл туннель за 45 с.\n" + rtTailText();
    }

    private void stopRustunnel() {
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
                    setStatus("Сервер включён ✓\nВход с этого телефона: Minecraft > Серверы > 127.0.0.1 : 19132\nЧтобы пустить друзей — «СОЗДАТЬ НА ВСТРОЕННОМ СЕРВЕРЕ».");
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

    private TextView fileRow(String text, int color, View.OnClickListener c, View.OnLongClickListener lc) {
        TextView t = tv(text, 13, color, false);
        t.setPadding(dp(10), dp(10), dp(10), dp(10));
        t.setBackground(rect(C_CARD, 8, 0, 0));
        t.setOnClickListener(c);
        if (lc != null) t.setOnLongClickListener(lc);
        t.setLayoutParams(lin(MATCH, WRAP, 0, 0, 0, 0, dp(4)));
        return t;
    }

    /** Окно со списком файлов и папок сервера: нажатие — открыть, долгое нажатие — удалить. */
    private void openFiles(File root) {
        fbRoot = root;
        fbCur = root;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(C_BG);
        box.setPadding(dp(10), dp(10), dp(10), dp(10));
        final TextView path = tv("", 11, C_MUTED, false);
        box.addView(path);
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView sc = new ScrollView(this);
        sc.addView(list);
        box.addView(sc, lin(MATCH, dp(380), 0, 0, dp(6), 0, 0));
        box.addView(tv("Нажатие — открыть · долгое нажатие — удалить", 10, C_MUTED, false));
        fillFiles(list, path);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Файлы сервера").setView(box).setNegativeButton("Закрыть", null).show();
    }

    private void fillFiles(final LinearLayout list, final TextView path) {
        list.removeAllViews();
        String rel = fbCur.getAbsolutePath().substring(fbRoot.getAbsolutePath().length());
        path.setText("pmmp" + (rel.isEmpty() ? "/" : rel) + (srvAlive() ? "   (сервер работает)" : ""));
        if (!fbCur.equals(fbRoot))
            list.addView(fileRow("⬆  назад", C_TEXT, v -> { fbCur = fbCur.getParentFile(); fillFiles(list, path); }, null));
        File[] fs = fbCur.listFiles();
        if (fs == null || fs.length == 0) { list.addView(tv("(пусто)", 12, C_MUTED, false)); return; }
        Arrays.sort(fs, (a, b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
        int shown = 0;
        for (final File f : fs) {
            if (shown++ >= 400) { list.addView(tv("… и ещё " + (fs.length - 400), 12, C_MUTED, false)); break; }
            final boolean dir = f.isDirectory();
            String label = (dir ? "📁  " : "📄  ") + f.getName() + (dir ? "" : "    " + humanSize(f.length()));
            list.addView(fileRow(label, dir ? C_TEXT : C_MUTED,
                    v -> { if (dir) { fbCur = f; fillFiles(list, path); } else viewFile(f); },
                    v -> { confirmDelete(f, list, path); return true; }));
        }
    }

    private void confirmDelete(final File f, final LinearLayout list, final TextView path) {
        if (srvAlive()) { toast("Сначала выключите сервер"); return; }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Удалить?")
                .setMessage((f.isDirectory() ? "Папка «" : "Файл «") + f.getName() + "» будет удалён безвозвратно.")
                .setPositiveButton("Удалить", (d, w) -> { deleteRec(f); fillFiles(list, path); })
                .setNegativeButton("Отмена", null).show();
    }

    private void viewFile(final File f) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        boolean text = false;
        for (String ext : new String[]{".txt", ".json", ".yml", ".yaml", ".properties", ".log", ".md", ".ini", ".cfg", ".conf", ".xml", ".csv", ".php"})
            if (n.endsWith(ext)) { text = true; break; }
        if (!text) { toast("Этот тип файла нельзя открыть в приложении"); return; }
        if (f.length() > 256 * 1024) { toast("Файл слишком большой (" + humanSize(f.length()) + ")"); return; }
        String content;
        try {
            byte[] b = new byte[(int) f.length()];
            try (FileInputStream in = new FileInputStream(f)) {
                int got = 0;
                while (got < b.length) { int r = in.read(b, got, b.length - got); if (r < 0) break; got += r; }
            }
            content = new String(b, StandardCharsets.UTF_8);
        } catch (Exception e) { toast("Не удалось прочитать: " + e.getMessage()); return; }
        final EditText et = new EditText(this);
        et.setText(content);
        et.setTextColor(C_TEXT);
        et.setTextSize(11);
        et.setTypeface(Typeface.MONOSPACE);
        et.setGravity(Gravity.TOP | Gravity.START);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        et.setBackgroundColor(C_BG);
        et.setPadding(dp(10), dp(10), dp(10), dp(10));
        et.setMinHeight(dp(260));
        ScrollView sc = new ScrollView(this);
        sc.addView(et);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(f.getName()).setView(sc)
                .setPositiveButton("Сохранить", (d, w) -> {
                    try (FileOutputStream o = new FileOutputStream(f)) {
                        o.write(et.getText().toString().getBytes(StandardCharsets.UTF_8));
                        toast(srvAlive() ? "Сохранено. Изменения вступят в силу после перезапуска сервера" : "Сохранено");
                    } catch (Exception e) { toast("Не удалось сохранить: " + e.getMessage()); }
                })
                .setNegativeButton("Закрыть", null).show();
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

    private void buildScreen() {
        FrameLayout screen = new FrameLayout(this);
        screen.setBackgroundColor(C_BG);
        screen.setClickable(true);
        ScrollView sv = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(48), dp(20), dp(24));
        sv.addView(col);
        screen.addView(sv, new FrameLayout.LayoutParams(MATCH, MATCH));

        TextView title = tv("ARCADE", 24, C_RED, true);
        title.setLetterSpacing(0.18f);
        col.addView(title);
        col.addView(tv("Мультиплеер Minecraft Bedrock", 13, C_MUTED, false));

        // форма
        formBox = new LinearLayout(this);
        formBox.setOrientation(LinearLayout.VERTICAL);
        formBox.addView(sectionLabel("ОВЕРЛЕЙ ПОВЕРХ MINECRAFT"));
        formBox.addView(tv("Minecraft может скрывать обычные окна. Включите службу «ARCADE» в «Специальных возможностях» — служба нужна только для показа окна с кодом.", 12, C_MUTED, false),
                lin(MATCH, WRAP, 0, 0, 0, 0, dp(8)));
        formBox.addView(btn("ВКЛЮЧИТЬ В СПЕЦ. ВОЗМОЖНОСТЯХ", C_CARD2, 12, v -> {
            try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); } catch (Exception e) { toast("Не удалось открыть настройки"); }
        }));
        formBox.addView(sectionLabel("RUSTUNNEL (ТУННЕЛЬ В ИНТЕРНЕТ)"));
        formBox.addView(tv("Через него ваш локальный сервер получает публичный адрес. API-ключ: rustunnel.com > Dashboard > API Keys. Вставьте один раз — он сохранится на телефоне.", 12, C_MUTED, false),
                lin(MATCH, WRAP, 0, 0, 0, 0, dp(8)));
        tokenInput = new EditText(this);
        tokenInput.setHint("API-ключ Rustunnel");
        tokenInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        tokenInput.setTextColor(C_TEXT);
        tokenInput.setHintTextColor(C_MUTED);
        tokenInput.setTextSize(13);
        tokenInput.setSingleLine(true);
        tokenInput.setText(prefs.getString("rt_token", ""));
        tokenInput.setBackground(rect(C_CARD2, 10, C_STROKE, 1));
        tokenInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        formBox.addView(tokenInput, lin(MATCH, WRAP, 0, 0, 0, 0, dp(8)));
        final TextView regBtn = btn("", C_CARD2, 12, null);
        final Runnable updReg = () -> regBtn.setText("Сервер Rustunnel: " + RT_REGION_NAMES[Math.max(0, Math.min(2, prefs.getInt("rt_region", 0)))] + "  (нажмите, чтобы сменить)");
        updReg.run();
        regBtn.setOnClickListener(v -> { prefs.edit().putInt("rt_region", (prefs.getInt("rt_region", 0) + 1) % RT_HOSTS.length).apply(); updReg.run(); });
        formBox.addView(regBtn, lin(MATCH, WRAP, 0, 0, 0, 0, dp(8)));
        urlInput = new EditText(this);
        urlInput.setHint("Ссылка на клиент под Android (если нет librustunnel.so)");
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setTextColor(C_TEXT);
        urlInput.setHintTextColor(C_MUTED);
        urlInput.setTextSize(12);
        urlInput.setSingleLine(true);
        urlInput.setText(prefs.getString("rt_url", ""));
        urlInput.setBackground(rect(C_CARD2, 10, C_STROKE, 1));
        urlInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        formBox.addView(urlInput, lin(MATCH, WRAP, 0, 0, 0, 0, dp(8)));
        formBox.addView(btn("СКАЧАТЬ / ПРОВЕРИТЬ КЛИЕНТ", C_CARD2, 12, v -> {
            saveRtSettings();
            if (mode == M_IDLE) stopFlag = false;
            new Thread(() -> {
                String e = ensureRustunnel(true);
                setStatus(e == null ? "Клиент Rustunnel готов ✓ (" + rtBin().getName() + ")" : e);
            }, "mp-rt-dl").start();
        }));
        formBox.addView(sectionLabel("СОЗДАТЬ МИР"));
        formBox.addView(tv("Код появится сразу (при первом запуске система спросит разрешение VPN — оно нужно, чтобы Minecraft принимал друзей). Потом зайдите в мир (включите «Мультиплеер») — друг подключится по коду. Порт менять нужно только для своего сервера на телефоне (например Termux + Nukkit).", 12, C_MUTED, false),
                lin(MATCH, WRAP, 0, 0, 0, 0, dp(8)));
        portInput = new EditText(this);
        portInput.setHint("Порт мира (по умолчанию 19132)");
        portInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        portInput.setTextColor(C_TEXT);
        portInput.setHintTextColor(C_MUTED);
        portInput.setTextSize(14);
        portInput.setSingleLine(true);
        portInput.setBackground(rect(C_CARD2, 10, C_STROKE, 1));
        portInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        formBox.addView(portInput, lin(MATCH, WRAP, 0, 0, 0, 0, dp(8)));
        final TextView vpnBtn = btn(useVpn ? "VPN-режим: ВКЛ (эксперимент)" : "VPN-режим: выкл", C_CARD2, 12, null);
        vpnBtn.setOnClickListener(v -> {
            useVpn = !useVpn;
            prefs.edit().putBoolean("use_vpn", useVpn).apply();
            vpnBtn.setText(useVpn ? "VPN-режим: ВКЛ (эксперимент)" : "VPN-режим: выкл");
        });
        formBox.addView(vpnBtn, lin(MATCH, WRAP, 0, 0, 0, 0, dp(8)));
        formBox.addView(btn("СОЗДАТЬ (мир Minecraft)", C_RED, 15, v -> startHost(false)));

        formBox.addView(sectionLabel("ВСТРОЕННЫЙ СЕРВЕР (БЕЗ TERMUX)"));
        formBox.addView(tv("Сервер запускается прямо в приложении. Вы заходите на него через «Серверы» > 127.0.0.1:19132, друг — по коду. Всё нужное (PHP и сервер нужной версии) приложение скачает само при первом запуске, нужен интернет и ~100 МБ места.", 12, C_MUTED, false),
                lin(MATCH, WRAP, 0, 0, 0, 0, dp(8)));
        final TextView verBtn = btn("", C_CARD2, 13, null);
        final Runnable updVer = () -> verBtn.setText("Версия сервера: " + SRV_VERSIONS[verIdx()][0] + "  (нажмите, чтобы сменить)");
        updVer.run();
        verBtn.setOnClickListener(v -> {
            prefs.edit().putInt("srv_ver", (verIdx() + 1) % SRV_VERSIONS.length).apply();
            updVer.run();
            toast("Мир нужно загрузить заново для этой версии");
        });
        formBox.addView(verBtn, lin(MATCH, WRAP, 0, 0, 0, 0, dp(8)));
        final TextView gmBtn = btn("", C_CARD2, 13, null);
        final Runnable updGm = () -> gmBtn.setText("Режим: " + GAMEMODE_NAMES[prefs.getInt("srv_gm", 0) % GAMEMODES.length] + "  (нажмите, чтобы сменить)");
        updGm.run();
        gmBtn.setOnClickListener(v -> { prefs.edit().putInt("srv_gm", (prefs.getInt("srv_gm", 0) + 1) % GAMEMODES.length).apply(); updGm.run(); });
        formBox.addView(gmBtn, lin(MATCH, WRAP, 0, 0, 0, 0, dp(8)));
        formBox.addView(btn("ЗАГРУЗИТЬ СВОЙ МИР (.mcworld)", C_CARD2, 13, v -> pickWorld()), lin(MATCH, WRAP, 0, 0, 0, 0, dp(8)));
        srvToggleBtn = btn("", 0xFF2A7A4B, 13, v -> toggleServer());
        formBox.addView(srvToggleBtn, lin(MATCH, WRAP, 0, 0, 0, 0, dp(8)));
        formBox.addView(btn("ФАЙЛЫ И ПАПКИ СЕРВЕРА", C_CARD2, 13, v -> openFiles(srvDir())), lin(MATCH, WRAP, 0, 0, 0, 0, dp(8)));
        formBox.addView(btn("СОЗДАТЬ НА ВСТРОЕННОМ СЕРВЕРЕ", 0xFF1E5AAE, 14, v -> { externalSrv = false; startHost(true); }), lin(MATCH, WRAP, 0, 0, 0, 0, dp(8)));
        formBox.addView(tv("Сервер уже запущен в Termux? Укажите его порт выше (по умолчанию 19132) и нажмите:", 12, C_MUTED, false), lin(MATCH, WRAP, 0, 0, 0, 0, dp(6)));
        formBox.addView(btn("СОЗДАТЬ НА СЕРВЕРЕ В TERMUX", 0xFF7A3FB5, 14, v -> { externalSrv = true; startHost(true); }));

        formBox.addView(sectionLabel("ВОЙТИ ПО КОДУ"));
        codeInput = new EditText(this);
        codeInput.setHint("XXXX-XXXX-XXXX");
        codeInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        codeInput.setTextColor(C_TEXT);
        codeInput.setHintTextColor(C_MUTED);
        codeInput.setTextSize(18);
        codeInput.setLetterSpacing(0.1f);
        codeInput.setGravity(Gravity.CENTER);
        codeInput.setSingleLine(true);
        codeInput.setBackground(rect(C_CARD2, 10, C_STROKE, 1));
        codeInput.setPadding(dp(12), dp(12), dp(12), dp(12));
        formBox.addView(codeInput, lin(MATCH, WRAP, 0, 0, 0, 0, dp(8)));
        formBox.addView(btn("ВОЙТИ", 0xFF1E8E4E, 15, v -> startGuest(codeInput.getText().toString())));
        TextView idleStatus = tv(status, 12, C_MUTED, false);
        idleStatus.setPadding(0, dp(18), 0, 0);
        statusViews.add(idleStatus);
        formBox.addView(idleStatus);
        col.addView(formBox);

        // активная сессия
        sessionBox = buildSessionCard();
        sessionBox.setVisibility(View.GONE);
        sessionBox.setPadding(dp(14), dp(14), dp(14), dp(14));
        sessionBox.setBackground(rect(C_CARD, 14, C_RED, 1.2f));
        col.addView(sessionBox, lin(MATCH, WRAP, 0, 0, dp(20), 0, 0));

        // консоль встроенного сервера
        consoleBox = new LinearLayout(this);
        consoleBox.setOrientation(LinearLayout.VERTICAL);
        consoleBox.setPadding(dp(12), dp(12), dp(12), dp(12));
        consoleBox.setBackground(rect(C_CARD, 14, C_STROKE, 1));
        consoleBox.setVisibility(View.GONE);
        consoleBox.addView(tv("КОНСОЛЬ СЕРВЕРА", 11, C_RED, true));
        consoleView = tv("", 10, C_TEXT, false);
        consoleView.setTypeface(Typeface.MONOSPACE);
        consoleScroll = new ScrollView(this);
        consoleScroll.addView(consoleView);
        consoleBox.addView(consoleScroll, lin(MATCH, dp(220), 0, 0, dp(6), 0, dp(6)));
        LinearLayout crow = new LinearLayout(this);
        consoleInput = new EditText(this);
        consoleInput.setHint("команда, например: op ник");
        consoleInput.setHintTextColor(C_MUTED);
        consoleInput.setTextColor(C_TEXT);
        consoleInput.setTextSize(13);
        consoleInput.setSingleLine(true);
        consoleInput.setBackground(rect(C_CARD2, 10, C_STROKE, 1));
        consoleInput.setPadding(dp(10), dp(8), dp(10), dp(8));
        crow.addView(consoleInput, lin(0, WRAP, 1f, 0, 0, dp(6), 0));
        crow.addView(btn(">", C_RED, 14, v -> {
            String cmd = consoleInput.getText().toString().trim();
            if (!cmd.isEmpty()) { consoleInput.setText(""); sendConsole(cmd); }
        }), lin(dp(56), WRAP, 0, 0, 0, 0, 0));
        consoleBox.addView(crow);
        col.addView(consoleBox, lin(MATCH, WRAP, 0, dp(12), 0, 0, 0));

        addContentView(screen, new ViewGroup.LayoutParams(MATCH, MATCH));
    }

    /** Карточка сессии: статус, код, кнопки. Используется и в приложении, и в оверлее. */
    private LinearLayout buildSessionCard() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10), dp(8), dp(10), dp(10));

        TextView st = tv(status, 12, C_TEXT, false);
        statusViews.add(st);
        box.addView(st, lin(MATCH, WRAP, 0, 0, 0, 0, dp(6)));

        TextView cd = tv("", 22, C_GREEN, true);
        cd.setGravity(Gravity.CENTER);
        cd.setLetterSpacing(0.08f);
        cd.setPadding(0, dp(4), 0, dp(8));
        cd.setVisibility(View.GONE);
        codeViews.add(cd);
        box.addView(cd);

        LinearLayout row = new LinearLayout(this);
        row.addView(btn("КОД", C_CARD2, 12, v -> copyCode()), lin(0, WRAP, 1f, 0, 0, dp(4), 0));
        row.addView(btn("MINECRAFT", C_CARD2, 12, v -> { if (!launchMinecraft()) toast("Minecraft не найден"); }), lin(0, WRAP, 1.5f, 0, 0, 0, 0));
        box.addView(row, lin(MATCH, WRAP, 0, 0, 0, 0, dp(6)));

        final TextView lv = tv(logText(), 10, C_MUTED, false);
        lv.setTypeface(Typeface.MONOSPACE);
        logViews.add(lv);
        final ScrollView ls = new ScrollView(this);
        ls.addView(lv);
        ls.setVisibility(View.GONE);
        LinearLayout row2 = new LinearLayout(this);
        row2.addView(btn("ЖУРНАЛ", C_CARD2, 12, v -> ls.setVisibility(ls.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE)), lin(0, WRAP, 1f, 0, 0, dp(4), 0));
        row2.addView(btn("КОПИРОВАТЬ", C_CARD2, 12, v -> copyLog()), lin(0, WRAP, 1f, 0, 0, 0, 0));
        box.addView(row2, lin(MATCH, WRAP, 0, 0, 0, 0, dp(6)));
        box.addView(ls, lin(MATCH, dp(150), 0, 0, 0, 0, dp(6)));
        box.addView(btn("■ ОСТАНОВИТЬ", C_RED, 12, v -> stopSession("Остановлено")));
        return box;
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

    private void toast(String s) { ui.post(() -> Toast.makeText(appCtx, s, Toast.LENGTH_SHORT).show()); }

    private void setStatus(String s) {
        status = s;
        try { prefs.edit().putString("last", s).apply(); } catch (Exception ignored) { }
        pushText();
    }

    /** Код и статус в уведомлении — виден даже если Minecraft скрыл оверлей. */
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
                    .setContentIntent(pi)
                    .setOnlyAlertOnce(true)
                    .setOngoing(true);
            nm.notify(77, b.build());
        } catch (Exception e) { Log.w(TAG, "notify", e); }
    }

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
            if (consoleBox != null) consoleBox.setVisibility(sa || (mode == M_HOST && builtin && !externalSrv) ? View.VISIBLE : View.GONE);
            if (srvToggleBtn != null) {
                srvToggleBtn.setText(srvBusy ? "СЕРВЕР: ЗАПУСКАЕТСЯ…" : (sa ? "■ ВЫКЛЮЧИТЬ СЕРВЕР" : "▶ ВКЛЮЧИТЬ СЕРВЕР (без комнаты)"));
                srvToggleBtn.setBackground(rect(sa ? C_RED : 0xFF2A7A4B, 10, 0, 0));
            }
        });
    }

    // ==================== Оверлей поверх Minecraft ====================

    private int screenW() { return getResources().getDisplayMetrics().widthPixels; }
    private int screenH() { return getResources().getDisplayMetrics().heightPixels; }

    private void showOverlay() {
        OverlayAccService acc = OverlayAccService.instance;
        if (overlayRoot != null || (!canOverlay() && acc == null)) return;
        final FrameLayout root = new FrameLayout(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackground(rect(0xF0111114, 14, C_RED, 1.2f));
        col.setClipToOutline(true);

        LinearLayout hdr = new LinearLayout(this);
        hdr.setGravity(Gravity.CENTER_VERTICAL);
        hdr.setPadding(dp(12), dp(4), dp(6), dp(4));
        hdr.setBackgroundColor(C_CARD);
        TextView t = tv("🎮  ARCADE MP", 12, C_RED, true);
        t.setLetterSpacing(0.1f);
        hdr.addView(t, lin(0, WRAP, 1f, 0, 0, 0, 0));

        final LinearLayout body = buildSessionCard();
        TextView min = tv("—", 13, C_TEXT, true);
        min.setGravity(Gravity.CENTER);
        min.setBackground(rect(C_CARD2, 9, 0, 0));
        min.setOnClickListener(v -> body.setVisibility(body.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        hdr.addView(min, lin(dp(34), dp(34), 0, 0, 0, 0, 0));

        col.addView(hdr, lin(MATCH, WRAP, 0, 0, 0, 0, 0));
        col.addView(body, lin(MATCH, WRAP, 0, 0, 0, 0, 0));
        root.addView(col, new FrameLayout.LayoutParams(MATCH, WRAP));

        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        overlayWm = wm;
        if (acc != null) {
            // окно службы доступности: Minecraft не может его скрыть
            type = 2032; // TYPE_ACCESSIBILITY_OVERLAY
            overlayWm = (WindowManager) acc.getSystemService(Context.WINDOW_SERVICE);
        }
        overlayLp = new WindowManager.LayoutParams(Math.min(dp(260), screenW() - dp(16)), WRAP, type, FL_NF, PixelFormat.TRANSLUCENT);
        overlayLp.gravity = Gravity.TOP | Gravity.START;
        overlayLp.x = dp(8);
        overlayLp.y = dp(70);
        makeDraggable(hdr, root, overlayLp);
        try {
            overlayWm.addView(root, overlayLp);
            overlayRoot = root;
            pushText();
        } catch (Exception e) { Log.e(TAG, "overlay", e); }
    }

    private void hideOverlay() {
        if (overlayRoot == null) return;
        try { (overlayWm != null ? overlayWm : wm).removeViewImmediate(overlayRoot); } catch (Exception ignored) { }
        overlayRoot = null;
        for (int i = statusViews.size() - 1; i >= 0; i--) if (!isInApp(statusViews.get(i))) statusViews.remove(i);
        for (int i = codeViews.size() - 1; i >= 0; i--) if (!isInApp(codeViews.get(i))) codeViews.remove(i);
        for (int i = logViews.size() - 1; i >= 0; i--) if (!isInApp(logViews.get(i))) logViews.remove(i);
    }

    /** true, если view принадлежит основному экрану (formBox / sessionBox). */
    private boolean isInApp(View v) {
        View p = v;
        while (p != null) {
            if (p == formBox || p == sessionBox || p == consoleBox) return true;
            if (!(p.getParent() instanceof View)) return false;
            p = (View) p.getParent();
        }
        return false;
    }

    private void makeDraggable(View handle, final View win, final WindowManager.LayoutParams lp) {
        handle.setOnTouchListener(new View.OnTouchListener() {
            float tx, ty; int ox, oy;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN: tx = e.getRawX(); ty = e.getRawY(); ox = lp.x; oy = lp.y; return true;
                    case MotionEvent.ACTION_MOVE:
                        lp.x = Math.max(0, Math.min(screenW() - dp(70), ox + (int) (e.getRawX() - tx)));
                        lp.y = Math.max(0, Math.min(screenH() - dp(50), oy + (int) (e.getRawY() - ty)));
                        try { (overlayWm != null ? overlayWm : wm).updateViewLayout(win, lp); } catch (Exception ignored) { }
                        return true;
                    case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL: return true;
                }
                return false;
            }
        });
    }
}

package com.cr.arcade;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
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
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * ARCADE — мультиплеер для Minecraft Bedrock (аналог Omlet Arcade).
 *
 * Как это работает (без собственного сервера):
 *  - Metered Open Relay — это TURN-сервер. Хост получает на нём relay-адрес (ip:port),
 *    он и кодируется в 12-символьный код комнаты.
 *  - Хост: приложение ждёт, пока Minecraft откроет мир (UDP 19132), и пробрасывает UDP-пакеты
 *    гостей из relay в локальный мир.
 *  - Гость: приложение поднимает на 19132 «фейковый LAN-сервер» (отвечает на RakNet-пинги),
 *    поэтому в Minecraft во вкладке «Друзья» появляется «Мой мир». Пакеты идут через relay к хосту.
 */
public class MainActivity extends BridgeActivity {

    private static final String TAG = "ArcadeMP";

    // ===== Metered Open Relay =====
    private static final String METERED_API_KEY = "6adc7dfbc05290c87a5adc84";
    private static final String METERED_URL = "https://openrelay.metered.ca/api/v1/turn/credentials?apiKey=" + METERED_API_KEY;

    private static final String[] MC_PACKAGES = {"com.mojang.minecraftpe", "com.mojang.minecraftpe.preview"};
    private static final int MC_PORT = 19132;
    private static final int RC_OVERLAY = 101, RC_PERMS = 110;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT, WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final int FL_NF = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;

    private static final int C_BG = 0xFF111114, C_CARD = 0xFF1B1B20, C_CARD2 = 0xFF26262D, C_STROKE = 0xFF383840;
    private static final int C_RED = 0xFFE5252A, C_TEXT = 0xFFF1F1F4, C_MUTED = 0xFF8F8F9B, C_GREEN = 0xFF2ECC71;

    private static final byte[] MAGIC = {0x00, (byte) 0xff, (byte) 0xff, 0x00, (byte) 0xfe, (byte) 0xfe, (byte) 0xfe, (byte) 0xfe,
            (byte) 0xfd, (byte) 0xfd, (byte) 0xfd, (byte) 0xfd, 0x12, 0x34, 0x56, 0x78};
    private static final String ALPHA = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final int M_IDLE = 0, M_HOST = 1, M_GUEST = 2;

    // ===== Состояние =====
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
    private volatile Turn turn;
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

    // UI
    private LinearLayout formBox, sessionBox;
    private EditText codeInput, portInput;
    private final ArrayList<TextView> statusViews = new ArrayList<>();
    private final ArrayList<TextView> codeViews = new ArrayList<>();
    private FrameLayout overlayRoot;
    private WindowManager.LayoutParams overlayLp;

    private static class HostPeer {
        final InetSocketAddress peer;
        final DatagramSocket sock;
        volatile long last = SystemClock.elapsedRealtime();

        HostPeer(InetSocketAddress peer) throws Exception { this.peer = peer; this.sock = new DatagramSocket(); }
    }

    // ==================== Жизненный цикл ====================

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appCtx = getApplicationContext();
        wm = (WindowManager) appCtx.getSystemService(Context.WINDOW_SERVICE);
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
        hideOverlay();
        if (mode != M_IDLE) stopSession("Остановлено");
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

    private boolean canOverlay() { return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this); }

    private boolean ensureOverlay() {
        if (canOverlay()) return true;
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
        gotGameData = false;
        motd = null;
        clientAddr = null;
        setStatus(s);
        startKeepAlive();
        acquireLocks();
    }

    private void startHost() {
        if (mode != M_IDLE || !ensureOverlay()) return;
        int hp = MC_PORT;
        try { hp = Integer.parseInt(portInput.getText().toString().trim()); } catch (Exception ignored) { }
        if (hp < 1 || hp > 65535) hp = MC_PORT;
        hostPort = hp;
        begin(M_HOST, "Запускаю Minecraft…");
        if (!launchMinecraft()) { stopSession("Minecraft не установлен"); return; }
        Thread t = new Thread(this::hostLoop, "mp-host");
        engine = t;
        t.start();
    }

    private void startGuest(String raw) {
        if (mode != M_IDLE || !ensureOverlay()) return;
        final InetSocketAddress h = decodeCode(raw);
        if (h == null) { setStatus("Неверный код комнаты"); return; }
        begin(M_GUEST, "Подключаюсь к relay…");
        Thread t = new Thread(() -> guestLoop(h), "mp-guest");
        engine = t;
        t.start();
    }

    private synchronized void stopSession(String msg) {
        stopFlag = true;
        mode = M_IDLE;
        code = "";
        Turn t = turn; turn = null;
        if (t != null) t.close();
        DatagramSocket g = guestSock; guestSock = null;
        if (g != null) g.close();
        for (HostPeer p : hostPeers.values()) { try { p.sock.close(); } catch (Exception ignored) { } }
        hostPeers.clear();
        Thread e = engine; engine = null;
        if (e != null && e != Thread.currentThread()) e.interrupt();
        clientAddr = null; motd = null; hostRelay = null;
        releaseLocks();
        stopKeepAlive();
        status = msg;
        ui.post(() -> { hideOverlay(); pushText(); });
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
                if (Build.VERSION.SDK_INT >= 29) startForeground(77, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
                else startForeground(77, n);
            } catch (Exception e) { Log.w(TAG, "fg", e); }
            return START_NOT_STICKY;
        }
    }

    // ==================== ХОСТ ====================

    private volatile boolean worldFound;
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

    private String hostStatus() {
        InetSocketAddress ht = hostTarget;
        int port = ht != null ? ht.getPort() : hostPort;
        StringBuilder sb = new StringBuilder("Код создан — отправьте другу.\n");
        if (worldFound) sb.append("Мир найден ✓");
        else sb.append("Мир пока не найден: зайдите в мир и включите «Мультиплеер». Пересылаю на порт ").append(port);
        int n = hostPeers.size();
        if (n > 0) sb.append("\nПодключено игроков: ").append(n);
        if (!localIp.isEmpty()) sb.append("\nТа же Wi-Fi сеть: Серверы → Добавить → ").append(localIp).append(" : ").append(port);
        if (!publicIp.isEmpty()) sb.append("\nВнешний IP: ").append(publicIp).append(" (нужен проброс UDP ").append(port).append(")");
        return sb.toString();
    }

    private void hostLoop() {
        try {
            worldFound = false;
            publicIp = "";
            localIp = localIpv4();
            hostTarget = new InetSocketAddress("127.0.0.1", hostPort);
            setStatus("Создаю сервер…");
            Creds c = fetchCreds();
            if (c == null) { stopSession("Не удалось получить ключи Metered (проверьте интернет и API-ключ)"); return; }
            Turn t = null;
            for (InetSocketAddress s : c.servers) {
                if (stopFlag) return;
                Turn x = new Turn(s, c.user, c.pass);
                if (x.allocate()) { t = x; break; }
                x.close();
            }
            if (t == null) { stopSession("Relay недоступен (UDP заблокирован?)"); return; }
            turn = t;
            t.createPermission(t.relayed.getAddress());
            t.listener = this::hostOnData;
            code = encodeCode(t.relayed);
            setStatus(hostStatus());

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

            long lastRef = SystemClock.elapsedRealtime(), lastClean = lastRef;
            while (!stopFlag) {
                Thread.sleep(1000);
                long n = SystemClock.elapsedRealtime();
                if (n - lastRef > 240000) { t.refresh(); t.createPermission(t.relayed.getAddress()); lastRef = n; }
                if (n - lastClean > 10000) {
                    lastClean = n;
                    for (Map.Entry<String, HostPeer> e : hostPeers.entrySet()) {
                        if (n - e.getValue().last > 120000) { try { e.getValue().sock.close(); } catch (Exception ignored) { } hostPeers.remove(e.getKey()); }
                    }
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
            } catch (Exception e) { return; }
        }
        hp.last = SystemClock.elapsedRealtime();
        try { hp.sock.send(new DatagramPacket(d, len, hostTarget)); } catch (Exception ignored) { }
    }

    private void hostReader(HostPeer hp) {
        byte[] buf = new byte[4096];
        while (!stopFlag) {
            try {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                hp.sock.receive(p);
                hp.last = SystemClock.elapsedRealtime();
                Turn t = turn;
                if (t != null) t.send(hp.peer, buf, p.getLength());
            } catch (Exception e) { break; }
        }
    }

    /** Проверка: Minecraft открыл мир. Пингуем localhost, адреса Wi-Fi и broadcast на портах 19132..19142. */
    private boolean probeWorld() {
        ArrayList<InetAddress> uni = new ArrayList<>();
        ArrayList<InetAddress> bc = new ArrayList<>();
        try { uni.add(InetAddress.getByName("127.0.0.1")); } catch (Exception ignored) { }
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
            long end = SystemClock.elapsedRealtime() + 1500;
            byte[] r = new byte[2048];
            while (SystemClock.elapsedRealtime() < end) {
                try {
                    s.setSoTimeout((int) Math.max(50, end - SystemClock.elapsedRealtime()));
                    DatagramPacket rp = new DatagramPacket(r, r.length);
                    s.receive(rp);
                    if (rp.getLength() > 0 && (r[0] & 0xff) == 0x1c) {
                        hostTarget = new InetSocketAddress(rp.getAddress(), rp.getPort());
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

    private void guestLoop(InetSocketAddress h) {
        try {
            Creds c = fetchCreds();
            if (c == null) { stopSession("Не удалось получить ключи Metered"); return; }

            ArrayList<InetSocketAddress> cand = new ArrayList<>();
            for (InetSocketAddress s : c.servers) cand.add(new InetSocketAddress(h.getAddress(), s.getPort()));
            cand.addAll(c.servers);
            Turn t = null;
            for (InetSocketAddress s : cand) {
                if (stopFlag) return;
                Turn x = new Turn(s, c.user, c.pass);
                if (x.allocate()) { t = x; break; }
                x.close();
            }
            if (t == null) { stopSession("Relay недоступен (UDP заблокирован?)"); return; }
            turn = t;
            t.createPermission(h.getAddress());
            hostRelay = h;

            DatagramSocket gs = new DatagramSocket(null);
            gs.setReuseAddress(true);
            gs.setBroadcast(true);
            try { gs.bind(new InetSocketAddress(MC_PORT)); }
            catch (Exception e) { gs.close(); stopSession("Порт 19132 занят — закройте мир, который вы хостите"); return; }
            guestSock = gs;
            t.listener = this::guestOnData;

            Thread pinger = new Thread(() -> {
                try {
                    while (!stopFlag) {
                        byte[] ping = makePing();
                        Turn tt = turn;
                        if (tt != null) tt.send(h, ping, ping.length);
                        Thread.sleep(motd == null ? 2000 : 10000);
                    }
                } catch (Exception ignored) { }
            }, "mp-ping");
            pinger.setDaemon(true);
            pinger.start();

            String li = localIpv4();
            setStatus("Готово! Minecraft → Друзья → «Мой мир».\nНе видно? Серверы → Добавить сервер: адрес " + (li.isEmpty() ? "127.0.0.1" : li) + ", порт " + MC_PORT);
            ui.post(this::launchMinecraft);

            byte[] buf = new byte[4096];
            long lastRef = SystemClock.elapsedRealtime();
            gs.setSoTimeout(1000);
            while (!stopFlag) {
                long n = SystemClock.elapsedRealtime();
                if (n - lastRef > 240000) { t.refresh(); t.createPermission(h.getAddress()); lastRef = n; }
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                try { gs.receive(p); } catch (SocketTimeoutException e) { continue; }
                int len = p.getLength();
                if (len < 1) continue;
                int b0 = buf[0] & 0xff;
                if (b0 == 0x01 || b0 == 0x02) {
                    if (len >= 9) sendPong(gs, p, buf);
                    continue;
                }
                clientAddr = (InetSocketAddress) p.getSocketAddress();
                t.send(h, buf, len);
            }
        } catch (InterruptedException ignored) {
        } catch (Exception e) {
            Log.e(TAG, "guest", e);
            if (!stopFlag) stopSession("Ошибка: " + e.getMessage());
        }
    }

    private void guestOnData(InetSocketAddress peer, byte[] d, int len) {
        InetSocketAddress h = hostRelay;
        if (h == null || !peer.equals(h) && !(peer.getPort() == h.getPort() && Arrays.equals(peer.getAddress().getAddress(), h.getAddress().getAddress()))) return;
        if (len < 1) return;
        if ((d[0] & 0xff) == 0x1c) {
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
        if (!gotGameData) { gotGameData = true; setStatus("Подключено к миру друга"); }
    }

    private String buildMotd() {
        String m = motd;
        if (m != null) {
            String[] p = m.split(";", -1);
            if (p.length >= 9) {
                p[1] = "Мой мир";
                p[6] = String.valueOf(guid);
                if (p.length > 11) { p[10] = String.valueOf(MC_PORT); p[11] = String.valueOf(MC_PORT + 1); }
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < p.length; i++) { if (i > 0) sb.append(';'); sb.append(p[i]); }
                return sb.toString();
            }
        }
        return "MCPE;Мой мир;0;1.0.0;0;10;" + guid + ";Arcade;Survival;1;" + MC_PORT + ";" + (MC_PORT + 1) + ";";
    }

    private void sendPong(DatagramSocket gs, DatagramPacket ping, byte[] buf) {
        try {
            byte[] sb = buildMotd().getBytes(StandardCharsets.UTF_8);
            ByteBuffer b = ByteBuffer.allocate(1 + 8 + 8 + 16 + 2 + sb.length);
            b.put((byte) 0x1c).put(buf, 1, 8).putLong(guid).put(MAGIC).putShort((short) sb.length).put(sb);
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

    /** relay ip(4) + port(2) + контрольный байт → 12 символов. */
    private String encodeCode(InetSocketAddress a) {
        byte[] ip = a.getAddress().getAddress();
        byte[] b = new byte[7];
        System.arraycopy(ip, 0, b, 0, 4);
        b[4] = (byte) (a.getPort() >> 8);
        b[5] = (byte) a.getPort();
        int chk = 0xA7;
        for (int i = 0; i < 6; i++) chk ^= (b[i] & 0xff);
        b[6] = (byte) chk;
        long v = 0;
        for (int i = 0; i < 7; i++) v = (v << 8) | (b[i] & 0xff);
        v <<= 4;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) sb.append(ALPHA.charAt((int) ((v >> (55 - 5 * i)) & 31)));
        return sb.toString();
    }

    private InetSocketAddress decodeCode(String raw) {
        try {
            StringBuilder sb = new StringBuilder();
            for (char ch : raw.toUpperCase().toCharArray()) if (ALPHA.indexOf(ch) >= 0) sb.append(ch);
            if (sb.length() != 12) return null;
            long v = 0;
            for (int i = 0; i < 12; i++) v = (v << 5) | ALPHA.indexOf(sb.charAt(i));
            v >>= 4;
            byte[] b = new byte[7];
            for (int i = 0; i < 7; i++) b[i] = (byte) ((v >> (8 * (6 - i))) & 0xff);
            int chk = 0xA7;
            for (int i = 0; i < 6; i++) chk ^= (b[i] & 0xff);
            if ((byte) chk != b[6]) return null;
            int port = ((b[4] & 0xff) << 8) | (b[5] & 0xff);
            return new InetSocketAddress(InetAddress.getByAddress(new byte[]{b[0], b[1], b[2], b[3]}), port);
        } catch (Exception e) { return null; }
    }

    private String pretty(String c) {
        if (c == null || c.length() != 12) return "";
        return c.substring(0, 4) + "-" + c.substring(4, 8) + "-" + c.substring(8, 12);
    }

    // ==================== Metered: ключи ====================

    private static class Creds {
        String user, pass;
        final ArrayList<InetSocketAddress> servers = new ArrayList<>();
    }

    private Creds fetchCreds() {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(METERED_URL).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            if (c.getResponseCode() != 200) { Log.w(TAG, "metered http " + c.getResponseCode()); return null; }
            String body = readStream(c.getInputStream());
            JSONArray arr = new JSONArray(body.trim());
            Creds r = new Creds();
            HashMap<String, InetAddress> dns = new HashMap<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String u = o.optString("username", ""), p = o.optString("credential", "");
                if (u.isEmpty()) continue;
                ArrayList<String> urls = new ArrayList<>();
                Object uo = o.opt("urls");
                if (uo instanceof JSONArray) { JSONArray ua = (JSONArray) uo; for (int k = 0; k < ua.length(); k++) urls.add(ua.getString(k)); }
                else if (uo != null) urls.add(String.valueOf(uo));
                for (String s : urls) {
                    if (!s.startsWith("turn:") || s.contains("transport=tcp")) continue;
                    String hp = s.substring(5);
                    int q = hp.indexOf('?');
                    if (q >= 0) hp = hp.substring(0, q);
                    int col = hp.lastIndexOf(':');
                    if (col < 0) continue;
                    String host = hp.substring(0, col);
                    int port = Integer.parseInt(hp.substring(col + 1));
                    InetAddress ip = dns.get(host);
                    if (ip == null) {
                        for (InetAddress a : InetAddress.getAllByName(host)) if (a instanceof Inet4Address) { ip = a; break; }
                        if (ip == null) continue;
                        dns.put(host, ip);
                    }
                    if (r.user == null) { r.user = u; r.pass = p; }
                    r.servers.add(new InetSocketAddress(ip, port));
                }
            }
            return (r.user != null && !r.servers.isEmpty()) ? r : null;
        } catch (Exception e) {
            Log.e(TAG, "creds", e);
            return null;
        }
    }

    private static String readStream(InputStream is) throws java.io.IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line).append('\n');
        r.close();
        return sb.toString();
    }

    // ==================== TURN-клиент (RFC 5766) ====================

    private static class Turn {
        interface Listener { void onData(InetSocketAddress peer, byte[] d, int len); }

        static final byte[] COOKIE = {0x21, 0x12, (byte) 0xA4, 0x42};

        final DatagramSocket sock;
        final InetSocketAddress server;
        final String user, pass;
        volatile byte[] realm, nonce, key;
        volatile InetSocketAddress relayed;
        volatile Listener listener;
        volatile boolean closed;
        final ConcurrentHashMap<String, ArrayBlockingQueue<byte[]>> pending = new ConcurrentHashMap<>();
        final SecureRandom rnd = new SecureRandom();

        Turn(InetSocketAddress server, String user, String pass) throws Exception {
            this.server = server; this.user = user; this.pass = pass;
            this.sock = new DatagramSocket();
            Thread t = new Thread(this::rx, "turn-rx");
            t.setDaemon(true);
            t.start();
        }

        static int u16(byte[] b, int o) { return ((b[o] & 0xff) << 8) | (b[o + 1] & 0xff); }

        static String hex(byte[] b, int o, int n) {
            StringBuilder sb = new StringBuilder();
            for (int i = o; i < o + n; i++) sb.append(String.format("%02x", b[i]));
            return sb.toString();
        }

        static byte[] concat(byte[]... parts) {
            int n = 0;
            for (byte[] p : parts) n += p.length;
            byte[] r = new byte[n];
            int o = 0;
            for (byte[] p : parts) { System.arraycopy(p, 0, r, o, p.length); o += p.length; }
            return r;
        }

        static byte[] attr(int type, byte[] val) {
            byte[] out = new byte[4 + ((val.length + 3) & ~3)];
            out[0] = (byte) (type >> 8); out[1] = (byte) type;
            out[2] = (byte) (val.length >> 8); out[3] = (byte) val.length;
            System.arraycopy(val, 0, out, 4, val.length);
            return out;
        }

        static Map<Integer, byte[]> attrs(byte[] m, int n) {
            HashMap<Integer, byte[]> r = new HashMap<>();
            int end = Math.min(n, 20 + u16(m, 2));
            int i = 20;
            while (i + 4 <= end) {
                int t = u16(m, i), l = u16(m, i + 2);
                if (i + 4 + l > end) break;
                if (!r.containsKey(t)) r.put(t, Arrays.copyOfRange(m, i + 4, i + 4 + l));
                i += 4 + ((l + 3) & ~3);
            }
            return r;
        }

        static int errCode(Map<Integer, byte[]> a) {
            byte[] v = a.get(0x0009);
            return (v == null || v.length < 4) ? -1 : (v[2] & 7) * 100 + (v[3] & 0xff);
        }

        static byte[] xorAddrBytes(InetSocketAddress a) {
            byte[] ip = a.getAddress().getAddress();
            byte[] o = new byte[8];
            o[1] = 1;
            int port = a.getPort() ^ 0x2112;
            o[2] = (byte) (port >> 8); o[3] = (byte) port;
            for (int i = 0; i < 4; i++) o[4 + i] = (byte) (ip[i] ^ COOKIE[i]);
            return o;
        }

        static InetSocketAddress xorAddr(byte[] v) {
            try {
                if (v == null || v.length < 8 || v[1] != 1) return null;
                int port = ((((v[2] & 0xff) << 8) | (v[3] & 0xff)) ^ 0x2112) & 0xffff;
                byte[] ip = new byte[4];
                for (int i = 0; i < 4; i++) ip[i] = (byte) (v[4 + i] ^ COOKIE[i]);
                return new InetSocketAddress(InetAddress.getByAddress(ip), port);
            } catch (Exception e) { return null; }
        }

        byte[] build(int type, byte[] txid, byte[] extra, boolean auth) throws Exception {
            byte[] body = extra;
            if (auth) body = concat(extra, attr(0x0006, user.getBytes(StandardCharsets.UTF_8)), attr(0x0014, realm), attr(0x0015, nonce));
            int total = 20 + body.length + (auth ? 24 : 0);
            ByteBuffer b = ByteBuffer.allocate(total);
            b.putShort((short) type).putShort((short) (body.length + (auth ? 24 : 0))).put(COOKIE).put(txid).put(body);
            if (auth) {
                Mac mac = Mac.getInstance("HmacSHA1");
                mac.init(new SecretKeySpec(key, "HmacSHA1"));
                byte[] h = mac.doFinal(Arrays.copyOf(b.array(), 20 + body.length));
                b.putShort((short) 0x0008).putShort((short) 20).put(h);
            }
            return b.array();
        }

        byte[] transact(int method, byte[] extra, boolean auth) throws Exception {
            for (int a = 0; a < 3; a++) {
                byte[] tx = new byte[12];
                rnd.nextBytes(tx);
                String id = hex(tx, 0, 12);
                ArrayBlockingQueue<byte[]> q = new ArrayBlockingQueue<>(2);
                pending.put(id, q);
                try {
                    byte[] m = build(method, tx, extra, auth);
                    sock.send(new DatagramPacket(m, m.length, server));
                    byte[] r = q.poll(1500, TimeUnit.MILLISECONDS);
                    if (r != null) return r;
                } finally { pending.remove(id); }
            }
            return null;
        }

        byte[] authed(int method, byte[] extra) throws Exception {
            byte[] r = transact(method, extra, true);
            if (r != null && u16(r, 0) == (method | 0x0110)) {
                Map<Integer, byte[]> a = attrs(r, r.length);
                if (errCode(a) == 438 && a.get(0x0015) != null) {
                    nonce = a.get(0x0015);
                    if (a.get(0x0014) != null) realm = a.get(0x0014);
                    r = transact(method, extra, true);
                }
            }
            return r;
        }

        boolean allocate() {
            try {
                byte[] extra = attr(0x0019, new byte[]{17, 0, 0, 0});
                byte[] r = transact(0x0003, extra, false);
                if (r == null) return false;
                if (u16(r, 0) == 0x0113) {
                    Map<Integer, byte[]> a = attrs(r, r.length);
                    if (errCode(a) != 401 || a.get(0x0014) == null || a.get(0x0015) == null) return false;
                    realm = a.get(0x0014);
                    nonce = a.get(0x0015);
                    MessageDigest md = MessageDigest.getInstance("MD5");
                    key = md.digest(concat(user.getBytes(StandardCharsets.UTF_8), ":".getBytes(), realm, ":".getBytes(), pass.getBytes(StandardCharsets.UTF_8)));
                    r = authed(0x0003, extra);
                    if (r == null) return false;
                }
                if (u16(r, 0) != 0x0103) return false;
                relayed = xorAddr(attrs(r, r.length).get(0x0016));
                return relayed != null;
            } catch (Exception e) { return false; }
        }

        boolean createPermission(InetAddress ip) {
            try {
                byte[] r = authed(0x0008, attr(0x0012, xorAddrBytes(new InetSocketAddress(ip, 0))));
                return r != null && u16(r, 0) == 0x0108;
            } catch (Exception e) { return false; }
        }

        void refresh() {
            try { authed(0x0004, attr(0x000D, new byte[]{0, 0, 0x02, 0x58})); } catch (Exception ignored) { }
        }

        void send(InetSocketAddress peer, byte[] data, int len) {
            try {
                byte[] tx = new byte[12];
                rnd.nextBytes(tx);
                byte[] extra = concat(attr(0x0012, xorAddrBytes(peer)), attr(0x0013, Arrays.copyOf(data, len)));
                byte[] m = build(0x0016, tx, extra, false);
                sock.send(new DatagramPacket(m, m.length, server));
            } catch (Exception ignored) { }
        }

        void rx() {
            byte[] buf = new byte[8192];
            while (!closed) {
                try {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    sock.receive(p);
                    int n = p.getLength();
                    if (n < 20 || buf[4] != COOKIE[0] || buf[5] != COOKIE[1] || buf[6] != COOKIE[2] || buf[7] != COOKIE[3]) continue;
                    int type = u16(buf, 0);
                    if (type == 0x0017) {
                        Map<Integer, byte[]> a = attrs(buf, n);
                        InetSocketAddress peer = xorAddr(a.get(0x0012));
                        byte[] d = a.get(0x0013);
                        Listener l = listener;
                        if (peer != null && d != null && l != null) l.onData(peer, d, d.length);
                    } else {
                        ArrayBlockingQueue<byte[]> q = pending.get(hex(buf, 8, 12));
                        if (q != null) q.offer(Arrays.copyOf(buf, n));
                    }
                } catch (Exception e) { if (closed || sock.isClosed()) break; }
            }
        }

        void close() {
            closed = true;
            new Thread(() -> {
                try {
                    if (relayed != null && key != null) {
                        byte[] tx = new byte[12];
                        rnd.nextBytes(tx);
                        byte[] m = build(0x0004, tx, attr(0x000D, new byte[4]), true);
                        sock.send(new DatagramPacket(m, m.length, server));
                    }
                } catch (Exception ignored) { }
                try { sock.close(); } catch (Exception ignored) { }
            }, "turn-close").start();
        }
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
        formBox.addView(sectionLabel("СОЗДАТЬ МИР"));
        formBox.addView(tv("Код появится сразу. Потом зайдите в мир (включите «Мультиплеер») — друг подключится по коду. Порт менять нужно только для своего сервера на телефоне (например Termux + Nukkit).", 12, C_MUTED, false),
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
        formBox.addView(btn("СОЗДАТЬ", C_RED, 15, v -> startHost()));

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

    private void setStatus(String s) { status = s; pushText(); }

    private void pushText() {
        ui.post(() -> {
            String pc = pretty(code);
            for (TextView t : statusViews) t.setText(status);
            for (TextView t : codeViews) { t.setText(pc); t.setVisibility(pc.isEmpty() ? View.GONE : View.VISIBLE); }
            if (formBox != null && sessionBox != null) {
                formBox.setVisibility(mode == M_IDLE ? View.VISIBLE : View.GONE);
                sessionBox.setVisibility(mode == M_IDLE ? View.GONE : View.VISIBLE);
            }
        });
    }

    // ==================== Оверлей поверх Minecraft ====================

    private int screenW() { return getResources().getDisplayMetrics().widthPixels; }
    private int screenH() { return getResources().getDisplayMetrics().heightPixels; }

    private void showOverlay() {
        if (overlayRoot != null || !canOverlay()) return;
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

        overlayLp = new WindowManager.LayoutParams(Math.min(dp(260), screenW() - dp(16)), WRAP,
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                FL_NF, PixelFormat.TRANSLUCENT);
        overlayLp.gravity = Gravity.TOP | Gravity.START;
        overlayLp.x = dp(8);
        overlayLp.y = dp(70);
        makeDraggable(hdr, root, overlayLp);
        try {
            wm.addView(root, overlayLp);
            overlayRoot = root;
            pushText();
        } catch (Exception e) { Log.e(TAG, "overlay", e); }
    }

    private void hideOverlay() {
        if (overlayRoot == null) return;
        try { wm.removeViewImmediate(overlayRoot); } catch (Exception ignored) { }
        overlayRoot = null;
        for (int i = statusViews.size() - 1; i >= 0; i--) if (!isInApp(statusViews.get(i))) statusViews.remove(i);
        for (int i = codeViews.size() - 1; i >= 0; i--) if (!isInApp(codeViews.get(i))) codeViews.remove(i);
    }

    /** true, если view принадлежит основному экрану (formBox / sessionBox). */
    private boolean isInApp(View v) {
        View p = v;
        while (p != null) {
            if (p == formBox || p == sessionBox) return true;
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
                        try { wm.updateViewLayout(win, lp); } catch (Exception ignored) { }
                        return true;
                    case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL: return true;
                }
                return false;
            }
        });
    }
}


package com.bdcyberninja.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Lightweight TCP/UDP scanner with banner‑grabbing, meant to be embedded in GUI or CLI apps.
 */
public class PortScanner {

    public static final int DEFAULT_THREADS = 400;
    private final ExecutorService pool;

    public PortScanner() {
        this(DEFAULT_THREADS);
    }

    public PortScanner(int threads) {
        this.pool = Executors.newFixedThreadPool(threads);
    }

    public enum ScanMode {
        COMPREHENSIVE_TCP,
        FAST_TCP,
        UDP,
        BANNER_GRAB
    }

    public record Result(int port, String proto, boolean open, String banner) {}

    public List<Result> scan(InetAddress target, ScanMode mode, ProgressCallback cb) throws InterruptedException {
        Instant start = Instant.now();
        List<Callable<Result>> tasks = new ArrayList<>();
        switch (mode) {
            case COMPREHENSIVE_TCP -> buildTcpTasks(target, 1, 65535, tasks, cb);
            case FAST_TCP         -> buildTcpTasks(target, 1, 1024, tasks, cb);
            case UDP              -> buildUdpTasks(target, 1, 1024, tasks, cb);
            case BANNER_GRAB      -> {
                int[] top = {21,22,23,25,53,80,110,143,443,445,993,995,3306,3389,5900,8080,8443,8888,9200,27017};
                int step = 0;
                for (int p : top) {
                    final int progressIndex = ++step;
                    tasks.add(() -> {
                        Result r = tcpProbe(target, p);
                        if (cb != null) cb.update(progressIndex, top.length);
                        return r;
                    });
                }
            }
        }
        List<Future<Result>> fut = pool.invokeAll(tasks);
        List<Result> results = new ArrayList<>();
        for (Future<Result> f : fut) {
            try { results.add(f.get()); } catch (ExecutionException ignore) {}
        }
        if (cb != null) cb.done(Duration.between(start, Instant.now()));
        return results;
    }

    private void buildTcpTasks(InetAddress target, int from, int to, List<Callable<Result>> tasks, ProgressCallback cb) {
        int total = to - from + 1;
        for (int port = from; port <= to; port++) {
            final int idx = port - from + 1;
            final int p = port;
            tasks.add(() -> {
                Result r = tcpProbe(target, p);
                if (cb != null) cb.update(idx, total);
                return r;
            });
        }
    }

    private void buildUdpTasks(InetAddress target, int from, int to, List<Callable<Result>> tasks, ProgressCallback cb) {
        int total = to - from + 1;
        for (int port = from; port <= to; port++) {
            final int idx = port - from + 1;
            final int p = port;
            tasks.add(() -> {
                Result r = udpProbe(target, p);
                if (cb != null) cb.update(idx, total);
                return r;
            });
        }
    }

    private Result tcpProbe(InetAddress addr, int port) {
        try (Socket sock = new Socket()) {
            sock.connect(new InetSocketAddress(addr, port), 200);
            sock.setSoTimeout(250);
            String banner = bannerLine(sock);
            return new Result(port, "tcp", true, banner);
        } catch (IOException ex) {
            return new Result(port, "tcp", false, null);
        }
    }

    private String bannerLine(Socket sock) {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(sock.getInputStream(), StandardCharsets.US_ASCII))) {
            return br.readLine();
        } catch (IOException ignore) {
            return null;
        }
    }

    private Result udpProbe(InetAddress addr, int port) {
        try (DatagramSocket ds = new DatagramSocket()) {
            ds.setSoTimeout(1000);
            byte[] payload = {0};
            ds.send(new DatagramPacket(payload, payload.length, addr, port));

            byte[] buf = new byte[16];
            DatagramPacket resp = new DatagramPacket(buf, buf.length);
            ds.receive(resp);
            return new Result(port, "udp", true, "len=" + resp.getLength());
        } catch (SocketTimeoutException toe) {
            return new Result(port, "udp", true, null); // open|filtered
        } catch (IOException ioe) {
            return new Result(port, "udp", false, null);
        }
    }

    public void shutdown() {
        pool.shutdownNow();
    }

    /* ---- callback interface ---- */
    public interface ProgressCallback {
        void update(int current, int total);
        void done(Duration elapsed);
    }
}

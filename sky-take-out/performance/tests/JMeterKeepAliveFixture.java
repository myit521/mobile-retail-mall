import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerBase;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.collections.HashTree;

// Removing/false-setting keepalive in any shipped sampler must break socket reuse.
// Runs the actual pinned JMeter sampler implementation; no app, JWT, or Docker.
public class JMeterKeepAliveFixture {
    private static final int WORKERS = 2;
    private static final int REQUESTS_PER_WORKER = 8;

    public static void main(String[] args) throws Exception {
        JMeterUtils.setJMeterHome(args[0]);
        JMeterUtils.loadJMeterProperties(Path.of(args[0], "bin", "jmeter.properties").toString());
        JMeterUtils.initLocale();
        if (!"5.6.3".equals(JMeterUtils.getJMeterVersion())) {
            throw new AssertionError("Fixture requires actual JMeter 5.6.3");
        }
        List<String> failures = new ArrayList<>();
        int samplerCount = 0;
        for (String plan : List.of("read-only", "light-write", "order-submit")) {
            List<HTTPSamplerBase> samplers = new ArrayList<>();
            collect(SaveService.loadTree(Path.of(args[1], plan + ".jmx").toFile()), samplers);
            for (HTTPSamplerBase original : samplers) {
                samplerCount++;
                String label = plan + "/" + original.getName();
                try (Listener listener = new Listener()) {
                    ExecutorService workers = Executors.newFixedThreadPool(WORKERS);
                    try {
                        List<Future<?>> runs = new ArrayList<>();
                        for (int worker = 0; worker < WORKERS; worker++) {
                            runs.add(workers.submit(() -> {
                                HTTPSamplerBase sampler = (HTTPSamplerBase) original.clone();
                                // Preserve method and transport properties, including the missing/false case.
                                // Never run the surrounding credential processors, headers, or data sets.
                                sampler.setPath("http://127.0.0.1:" + listener.port() + "/fixture");
                                sampler.setArguments(new Arguments());
                                if ("POST".equals(sampler.getMethod())) {
                                    sampler.addNonEncodedArgument("", "{}", "");
                                }
                                sampler.setConnectTimeout("3000");
                                sampler.setResponseTimeout("3000");
                                sampler.threadStarted();
                                try {
                                    for (int request = 0; request < REQUESTS_PER_WORKER; request++) {
                                        SampleResult result = sampler.sample();
                                        if (!result.isSuccessful() || !"200".equals(result.getResponseCode())) {
                                            throw new AssertionError(label + " HTTP request failed: " + result.getResponseCode());
                                        }
                                    }
                                } finally {
                                    sampler.threadFinished();
                                }
                            }));
                        }
                        for (Future<?> run : runs) run.get(20, TimeUnit.SECONDS);
                    } finally {
                        workers.shutdownNow();
                        if (!workers.awaitTermination(5, TimeUnit.SECONDS)) {
                            throw new AssertionError("Fixture workers did not terminate");
                        }
                    }
                    int requests = listener.requests.get();
                    int sockets = listener.accepted.get();
                    int closeHeaders = listener.closeHeaders.get();
                    System.out.printf("%s requests=%d acceptedSockets=%d connectionClose=%d bound=%d%n",
                            label, requests, sockets, closeHeaders, WORKERS);
                    if (requests != WORKERS * REQUESTS_PER_WORKER || sockets < 1 || sockets > WORKERS
                            || closeHeaders != 0 || !listener.errors.isEmpty()) {
                        failures.add(label + " did not reuse bounded worker connections");
                    }
                }
            }
        }
        if (samplerCount != 12) throw new AssertionError("Expected all 12 shipped HTTP samplers");
        if (!failures.isEmpty()) throw new AssertionError(String.join("; ", failures));
        System.out.println("TASK17_JMETER_KEEPALIVE_TEST_PASS samplers=12 requests=192 socketBound=24");
    }

    private static void collect(HashTree tree, List<HTTPSamplerBase> samplers) {
        for (Object node : tree.list()) {
            if (node instanceof HTTPSamplerBase) samplers.add((HTTPSamplerBase) node);
            collect(tree.getTree(node), samplers);
        }
    }

    private static final class Listener implements AutoCloseable {
        private final ServerSocket server = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        private final ExecutorService handlers = Executors.newCachedThreadPool();
        private final Set<Socket> clients = ConcurrentHashMap.newKeySet();
        private final Queue<Exception> errors = new ConcurrentLinkedQueue<>();
        private final AtomicInteger accepted = new AtomicInteger();
        private final AtomicInteger requests = new AtomicInteger();
        private final AtomicInteger closeHeaders = new AtomicInteger();
        private final Thread acceptor;

        Listener() throws IOException {
            acceptor = new Thread(() -> {
                while (!server.isClosed()) {
                    try {
                        Socket socket = server.accept();
                        socket.setSoTimeout(5000);
                        accepted.incrementAndGet();
                        clients.add(socket);
                        handlers.submit(() -> serve(socket));
                    } catch (IOException e) {
                        if (!server.isClosed()) errors.add(e);
                    }
                }
            }, "keepalive-fixture-listener");
            acceptor.start();
        }

        int port() { return server.getLocalPort(); }

        private void serve(Socket socket) {
            try (socket) {
                BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                OutputStream output = socket.getOutputStream();
                while (input.readLine() != null) {
                    int length = 0;
                    boolean close = false;
                    String header;
                    while ((header = input.readLine()) != null && !header.isEmpty()) {
                        String lower = header.toLowerCase(Locale.ROOT);
                        if (lower.startsWith("content-length:")) length = Integer.parseInt(header.substring(15).trim());
                        if (lower.startsWith("connection:") && lower.contains("close")) close = true;
                    }
                    for (int i = 0; i < length; i++) {
                        if (input.read() < 0) throw new EOFException("Incomplete fixture body");
                    }
                    requests.incrementAndGet();
                    if (close) closeHeaders.incrementAndGet();
                    // Explicit length and persistent HTTP/1.1 response permit reuse. Only obey client closure.
                    output.write(("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: "
                            + (close ? "close" : "keep-alive") + "\r\n\r\n{}").getBytes(StandardCharsets.US_ASCII));
                    output.flush();
                    if (close) return;
                }
            } catch (IOException e) {
                if (!server.isClosed()) errors.add(e);
            } finally {
                clients.remove(socket);
            }
        }

        public void close() throws Exception {
            server.close();
            acceptor.join(5000);
            for (Socket socket : clients) socket.close();
            handlers.shutdownNow();
            if (acceptor.isAlive() || !handlers.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Fixture listener did not terminate");
            }
        }
    }
}

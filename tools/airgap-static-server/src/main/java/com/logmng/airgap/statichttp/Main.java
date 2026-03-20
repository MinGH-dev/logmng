package com.logmng.airgap.statichttp;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;

/**
 * Serves a Create React App (or any SPA) build directory using only the JDK ({@code jdk.httpserver}).
 * No Node/npm at runtime. Unknown paths fall back to {@code index.html}.
 * <p>
 * {@code GET /runtime-config.js} injects {@code window.__LOGMNG_RUNTIME_CONFIG__.apiBaseUrl} from
 * env {@code LOGMNG_API_BASE_URL} or {@code REACT_APP_API_BASE_URL} so the UI can call the API without rebuild.
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args.length > 0 ? args[0] : "www").toAbsolutePath().normalize();
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 3001;
        if (!Files.isDirectory(root)) {
            System.err.println("Not a directory: " + root);
            System.exit(1);
        }
        Path index = root.resolve("index.html");
        if (!Files.isRegularFile(index)) {
            System.err.println("Missing index.html under: " + root);
            System.exit(1);
        }

        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 64);
        server.createContext("/runtime-config.js", Main::serveRuntimeConfig);
        server.createContext("/", new StaticSpaHandler(root, index));
        int threads = Math.min(8, Math.max(2, Runtime.getRuntime().availableProcessors() * 2));
        server.setExecutor(Executors.newFixedThreadPool(threads));
        server.start();
        System.out.println("LogMng static UI: http://0.0.0.0:" + port + "  root=" + root);
    }

    static void serveRuntimeConfig(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }
        String v = System.getenv("LOGMNG_API_BASE_URL");
        if (v == null || v.isBlank()) {
            v = System.getenv("REACT_APP_API_BASE_URL");
        }
        v = v != null ? v.trim() : "";
        byte[] body = buildRuntimeConfigJs(v).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/javascript; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        if ("HEAD".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(200, body.length);
            exchange.close();
            return;
        }
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    static String buildRuntimeConfigJs(String apiBaseUrl) {
        StringBuilder sb = new StringBuilder();
        sb.append("window.__LOGMNG_RUNTIME_CONFIG__=window.__LOGMNG_RUNTIME_CONFIG__||{};\n");
        if (apiBaseUrl.isEmpty()) {
            sb.append("try{delete window.__LOGMNG_RUNTIME_CONFIG__.apiBaseUrl;}catch(e){window.__LOGMNG_RUNTIME_CONFIG__.apiBaseUrl=undefined;}\n");
        } else {
            sb.append("window.__LOGMNG_RUNTIME_CONFIG__.apiBaseUrl=\"")
                    .append(jsEscape(apiBaseUrl))
                    .append("\";\n");
        }
        return sb.toString();
    }

    static String jsEscape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static final class StaticSpaHandler implements HttpHandler {
        private final Path root;
        private final Path index;

        StaticSpaHandler(Path root, Path index) {
            this.root = root;
            this.index = index;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            try {
                String raw = exchange.getRequestURI().getPath();
                if (raw == null || raw.isEmpty() || "/".equals(raw)) {
                    serveFile(exchange, index, method);
                    return;
                }
                String relative = raw.startsWith("/") ? raw.substring(1) : raw;
                Path resolved = root.resolve(relative).normalize();
                if (!resolved.startsWith(root)) {
                    exchange.sendResponseHeaders(403, -1);
                    exchange.close();
                    return;
                }
                Path file = resolved;
                if (Files.isDirectory(file)) {
                    Path idx = file.resolve("index.html").normalize();
                    if (Files.isRegularFile(idx) && idx.startsWith(root)) {
                        file = idx;
                    }
                }
                if (Files.isRegularFile(file) && Files.isReadable(file)) {
                    serveFile(exchange, file, method);
                } else {
                    serveFile(exchange, index, method);
                }
            } catch (Exception e) {
                e.printStackTrace();
                try {
                    exchange.sendResponseHeaders(500, -1);
                } catch (IOException ignored) {
                    // ignore
                }
                exchange.close();
            }
        }

        private static void serveFile(HttpExchange exchange, Path file, String method) throws IOException {
            String ctype = contentType(file);
            byte[] body;
            if ("HEAD".equalsIgnoreCase(method)) {
                exchange.getResponseHeaders().set("Content-Type", ctype);
                long len = Files.size(file);
                exchange.sendResponseHeaders(200, len);
                exchange.close();
                return;
            }
            body = Files.readAllBytes(file);
            exchange.getResponseHeaders().set("Content-Type", ctype);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        }

        private static String contentType(Path path) throws IOException {
            String n = path.getFileName().toString();
            int dot = n.lastIndexOf('.');
            String ext = dot >= 0 ? n.substring(dot + 1).toLowerCase() : "";
            return switch (ext) {
                case "html", "htm" -> "text/html; charset=utf-8";
                case "js" -> "text/javascript; charset=utf-8";
                case "css" -> "text/css; charset=utf-8";
                case "json" -> "application/json; charset=utf-8";
                case "png" -> "image/png";
                case "jpg", "jpeg" -> "image/jpeg";
                case "gif" -> "image/gif";
                case "svg" -> "image/svg+xml";
                case "ico" -> "image/x-icon";
                case "woff" -> "font/woff";
                case "woff2" -> "font/woff2";
                case "map" -> "application/json; charset=utf-8";
                case "txt" -> "text/plain; charset=utf-8";
                default -> {
                    String probe = Files.probeContentType(path);
                    yield probe != null ? probe : "application/octet-stream";
                }
            };
        }
    }
}

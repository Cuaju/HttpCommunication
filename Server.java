import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class Server {
    // In-memory store: id = message text
    private static final Map<Long, String> STORE = new ConcurrentHashMap<>();
    private static final AtomicLong NEXT_ID = new AtomicLong(1);

    public static void main(String[] args) throws Exception {
        int port = 6969;
        HttpServer http = HttpServer.create(new InetSocketAddress(port), 0);
        http.createContext("/messages", new MessagesHandler());
        http.setExecutor(Executors.newCachedThreadPool());
        System.out.println("Server running on http://localhost:" + port);
        http.start();
    }

    static class MessagesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            try {

                String method = ex.getRequestMethod();
                String path = ex.getRequestURI().getPath(); // e.g. /messages or /messages/123

                if ("OPTIONS".equalsIgnoreCase(method)) {
                    send(ex, 204, new byte[0], "text/plain");
                    return;
                }

                if ("/messages".equals(path)) {
                    if ("POST".equalsIgnoreCase(method)) {
                        handlePost(ex);
                        return;
                    }
                    if ("GET".equalsIgnoreCase(method)) {
                        // simple index of ids
                        String ids = STORE.keySet().stream()
                                .sorted()
                                .map(Object::toString)
                                .reduce("[]", (acc, id) -> {
                                    if ("[]".equals(acc)) return "[" + id;
                                    return acc + "," + id;
                                });
                        if (!"[]".equals(ids)) ids = ids + "]";
                        byte[] body = ids.getBytes(StandardCharsets.UTF_8);
                        send(ex, 200, body, "application/json");
                        return;
                    }
                    methodNotAllowed(ex, "GET, POST, OPTIONS");
                    return;
                }

                if (path.startsWith("/messages/")) {
                    if ("GET".equalsIgnoreCase(method)) {
                        String idStr = path.substring("/messages/".length()).trim();
                        try {
                            long id = Long.parseLong(idStr);
                            String message = STORE.get(id);
                            if (message == null) {
                                notFound(ex, "No message with id " + id);
                                return;
                            }
                            byte[] body = message.getBytes(StandardCharsets.UTF_8);
                            send(ex, 200, body, "text/plain; charset=utf-8");
                            return;
                        } catch (NumberFormatException nfe) {
                            badRequest(ex, "Invalid id: " + idStr);
                            return;
                        }
                    }
                    methodNotAllowed(ex, "GET, OPTIONS");
                    return;
                }

                notFound(ex, "Unknown path");
            } catch (Exception e) {
                e.printStackTrace();
                serverError(ex, "Internal error");
            } finally {
                ex.close();
            }
        }

        private void handlePost(HttpExchange ex) throws IOException {
            String contentType = ex.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null || !contentType.toLowerCase().startsWith("text/plain")) {
                badRequest(ex, "Content-Type must be text/plain");
                return;
            }
            String body = readBody(ex);
            if (body == null || body.isBlank()) {
                badRequest(ex, "Body is required (text/plain)");
                return;
            }
            long id = NEXT_ID.getAndIncrement();
            STORE.put(id, body);

            // Respond 201 Created with Location header and plain-text id in body
            ex.getResponseHeaders().add("Location", "/messages/" + id);
            byte[] resp = Long.toString(id).getBytes(StandardCharsets.UTF_8);
            send(ex, 201, resp, "text/plain; charset=utf-8");
        }

        private static String readBody(HttpExchange ex) throws IOException {
            try (InputStream is = ex.getRequestBody()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        private static void methodNotAllowed(HttpExchange ex, String allow) throws IOException {
            ex.getResponseHeaders().add("Allow", allow);
            send(ex, 405, "Method Not Allowed".getBytes(StandardCharsets.UTF_8), "text/plain");
        }

        private static void notFound(HttpExchange ex, String msg) throws IOException {
            send(ex, 404, msg.getBytes(StandardCharsets.UTF_8), "text/plain");
        }

        private static void badRequest(HttpExchange ex, String msg) throws IOException {
            send(ex, 400, msg.getBytes(StandardCharsets.UTF_8), "text/plain");
        }

        private static void serverError(HttpExchange ex, String msg) throws IOException {
            send(ex, 500, msg.getBytes(StandardCharsets.UTF_8), "text/plain");
        }

        private static void send(HttpExchange ex, int status, byte[] body, String contentType) throws IOException {
            ex.getResponseHeaders().set("Content-Type", contentType);
            ex.sendResponseHeaders(status, body.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body);
            }
        }
    }
}

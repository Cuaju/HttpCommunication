import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class SimpleClient {
    // Usage:
    //   java SimpleClient post "hello there"
    //   java SimpleClient get 1
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage:\n  java SimpleClient post \"your text\"\n  java SimpleClient get <id>");
            return;
        }

        String base = "http://localhost:6969";
        HttpClient client = HttpClient.newHttpClient();

        switch (args[0].toLowerCase()) {
            case "post": {
                if (args.length < 2) {
                    System.out.println("post needs a message: java SimpleClient post \"your text\"");
                    return;
                }
                String msg = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(base + "/messages"))
                        .header("Content-Type", "text/plain; charset=utf-8")
                        .POST(HttpRequest.BodyPublishers.ofString(msg, StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
                System.out.println("HTTP " + res.statusCode());
                System.out.println("Location: " + res.headers().firstValue("Location").orElse("(none)"));
                System.out.println("ID: " + res.body());
                break;
            }
            case "get": {
                if (args.length != 2) {
                    System.out.println("get needs an id: java SimpleClient get 1");
                    return;
                }
                String id = args[1];
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(base + "/messages/" + id))
                        .GET()
                        .build();

                HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
                System.out.println("HTTP " + res.statusCode());
                System.out.println("Body:");
                System.out.println(res.body());
                break;
            }
            default:
                System.out.println("Unknown command. Use: post|get");
        }
    }
}

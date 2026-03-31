import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.util.*;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;


// class User {
//     int id;
//     String name; 
//     int age;
// }

public class App {
    public static List<JsonObject> users = new ArrayList<>();

    public static int idCounter = 0;

    public static void main(String[] args) throws Exception {
        
        HttpServer server = HttpServer.create(new InetSocketAddress(8000),0);
        
        server.createContext("/users", new UserHandler());
        server.setExecutor(null);
        server.start();

        System.out.println("Server started at http://localhost:8000");
        
    }
}
class UserHandler implements HttpHandler{

    public static final Gson gson = new Gson();

    public void handle(HttpExchange exchange) throws IOException{

        try{
        String method = exchange.getRequestMethod();

        switch(method){

            case "GET":
                handleGet(exchange);
                break;

            case "POST":
                handlePost(exchange);
                break;
            
            case "PUT":
                handlePut(exchange);
                break;

            case "DELETE":
                handleDelete(exchange);
                break;

            default:
                sendJson(exchange, 405, "Method not allowed", null);
    
        }
    }   catch (JsonSyntaxException e) {
            sendJson(exchange, 400, "Invalid JSON format", null);
        } catch (Exception e) {
            sendJson(exchange, 500, "Internal server error: " + e.getMessage(), null);
        }
    }
    

    private void handleGet(HttpExchange exchange) throws IOException {
        if(App.users.isEmpty()){
            sendJson(exchange, 200, "No users found",null);
            return;
        }

        sendJson(exchange, 200, "Users fetched successfully", App.users);
    }

     private void handlePost(HttpExchange exchange) throws IOException {

        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.contains("application/json")) {
            sendJson(exchange, 400, "Content-Type must be application/json", null);
            return;
        }

        String body = readBody(exchange);

        if (body == null || body.trim().isEmpty()) {
            sendJson(exchange, 400, "Request body is empty", null);
            return;
        }

        //User newUser = gson.fromJson(body, User.class);
       JsonObject obj = JsonParser.parseString(body).getAsJsonObject();

        if (!obj.has("name") || obj.get("name").isJsonNull()) {
            sendJson(exchange, 400, "Name is required", null);
            return;
        }

        if (obj.has("age") && !obj.get("age").isJsonPrimitive()) {
            sendJson(exchange, 400, "Age must be a number", null);
            return;
        }

        obj.addProperty("id", App.idCounter++);

        App.users.add(obj);

        sendJson(exchange, 201, "User created successfully", obj);
    }         
    
    private void handlePut(HttpExchange exchange) throws IOException {

        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.contains("application/json")) {
            sendJson(exchange, 400, "Content-Type must be application/json", null);
            return;
}

        String body = readBody(exchange);

        if (body == null || body.trim().isEmpty()) {
            sendJson(exchange, 400, "Request body is empty", null);
            return;
        }

        //User updatedUser = gson.fromJson(body, User.class);
        JsonObject obj = JsonParser.parseString(body).getAsJsonObject();

        if (!obj.has("id")) {
            sendJson(exchange, 400, "ID is required", null);
            return;
        }

        int id = obj.get("id").getAsInt();

        JsonObject existing = null;
        for (JsonObject user : App.users) {
            if (user.get("id").getAsInt() == id) {
                existing = user;
                break;
            }
        }

        if (existing == null) {
            sendJson(exchange, 404, "User not found", null);
            return;
        }

        if (obj.has("name")) {
            existing.addProperty("name", obj.get("name").getAsString());
        }

        if (obj.has("age")) {
            existing.addProperty("age", obj.get("age").getAsInt());
        }

        sendJson(exchange, 200, "User updated successfully", existing);
    }

    private void handleDelete(HttpExchange exchange) throws IOException {

        String query = exchange.getRequestURI().getQuery();

        if (query == null || !query.contains("id=")) {
            sendJson(exchange, 400, "Missing id in query", null);
            return;
        }

       int id;

        // ✅ CHANGE: Safe query parsing
        try {
            id = Integer.parseInt(query.split("=")[1]);
        } catch (Exception e) {
            sendJson(exchange, 400, "Invalid ID format", null);
            return;
        }

        // ✅ CHANGE: Find user safely
        JsonObject toRemove = null;
        for (JsonObject user : App.users) {
            if (user.get("id").getAsInt() == id) {
                toRemove = user;
                break;
            }
        }

        if (toRemove == null) {
            sendJson(exchange, 404, "User not found", null);
            return;
        }

        App.users.remove(toRemove);

        sendJson(exchange, 200, "User deleted successfully", toRemove);
    }

    private String readBody(HttpExchange exchange) throws IOException {

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(exchange.getRequestBody()));

        StringBuilder body = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            body.append(line);
        }

        return body.toString();
    }

    private void sendJson(HttpExchange exchange, int status, String message, Object data) throws IOException {

        JsonObject response = new JsonObject();

        response.addProperty("status", status);
        response.addProperty("message", message);

        if (data != null) {
    if (data instanceof JsonElement) {
        response.add("data", (JsonElement) data);
        } else {
        response.add("data", gson.toJsonTree(data));
        }
    } else {
    response.add("data", null);
    }

        // response.put("status", status);
        // response.put("message", message);
        // response.put("data", data);

        String json = response.toString();

        exchange.getResponseHeaders().set("Content-Type", "application/json");

        exchange.sendResponseHeaders(status, json.getBytes().length);

        OutputStream os = exchange.getResponseBody();
        os.write(json.getBytes());
        os.close();
    }
}
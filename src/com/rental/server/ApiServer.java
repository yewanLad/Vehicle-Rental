package com.rental.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;


public class ApiServer {

    public static void main(String[] args) throws IOException {
        new File("data").mkdirs();
        AppData.loadAll();

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        // Auth + Users
        server.createContext("/api/login", safe(UserHandler::login));
        server.createContext("/api/register/customer", safe(UserHandler::registerCustomer));
        server.createContext("/api/register/admin", safe(UserHandler::registerAdmin));
        server.createContext("/api/users", safe(UserHandler::handle));

        // Vehicles, Bookings, Payments, Maintenance, Fines, Reports
        // (HttpServer picks the longest matching prefix, so sub-paths such as
        //  /api/vehicles/update reach the same handler as /api/vehicles)
        server.createContext("/api/vehicles", safe(VehicleHandler::handle));
        server.createContext("/api/bookings", safe(BookingHandler::handle));
        server.createContext("/api/payments", safe(PaymentHandler::handle));
        server.createContext("/api/maintenance", safe(MaintenanceHandler::handle));
        server.createContext("/api/fines", safe(FineHandler::handle));
        server.createContext("/api/reports", safe(ReportHandler::handle));

        // Static frontend (everything not matched above falls through to here)
        server.createContext("/", new StaticFileHandler());

        server.setExecutor(null);
        server.start();
        System.out.println("Server running at http://localhost:8080");
    }

    /** Turns an unexpected exception into a JSON 500 instead of a hung request. */
    private static HttpHandler safe(HttpHandler inner) {
        return ex -> {
            try {
                inner.handle(ex);
            } catch (Exception e) {
                e.printStackTrace();
                try {
                    ApiUtil.sendJson(ex, 500, ApiUtil.errorJson("Server error: " + e.getMessage()));
                } catch (Exception ignored) { }
            }
        };
    }

    // ==================== STATIC FILE SERVING ====================

    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            String path = ex.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";

            File root = new File("frontend").getCanonicalFile();
            File file = new File(root, path).getCanonicalFile();
            // block ../ tricks that would escape the frontend folder
            boolean inside = file.getPath().startsWith(root.getPath() + File.separator);
            if (!inside || !file.exists() || file.isDirectory()) {
                byte[] body = "404 Not Found".getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(404, body.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(body); }
                return;
            }

            ex.getResponseHeaders().set("Content-Type", contentType(path));
            byte[] bytes = Files.readAllBytes(file.toPath());
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
        }

        String contentType(String path) {
            if (path.endsWith(".html")) return "text/html; charset=utf-8";
            if (path.endsWith(".css")) return "text/css; charset=utf-8";
            if (path.endsWith(".js")) return "application/javascript; charset=utf-8";
            return "application/octet-stream";
        }
    }
}

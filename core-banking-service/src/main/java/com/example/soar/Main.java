package com.example.soar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class Main {

    static final List<Account> accounts = new CopyOnWriteArrayList<>();
    static final List<User> users = new CopyOnWriteArrayList<>();
    private static final ObjectMapper mapper = new ObjectMapper();
    static {
        mapper.registerModule(new JavaTimeModule());
    }

    public static void main(String[] args) {
        loadData();

        Javalin app = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(mapper));
        }).start(7002);

        // GET /accounts - list all or search with query params
        app.get("/accounts", ctx -> {
            String country = ctx.queryParam("country");
            String frozenStr = ctx.queryParam("frozen");
            String username = ctx.queryParam("username");
            String limitStr = ctx.queryParam("limit");
            String offsetStr = ctx.queryParam("offset");

            int limit = limitStr != null ? Integer.parseInt(limitStr) : 100;
            int offset = offsetStr != null ? Integer.parseInt(offsetStr) : 0;

            List<Account> filtered = accounts.stream()
                .filter(a -> country == null || country.equals(a.getPreviousCountryOfOrigin()))
                .filter(a -> frozenStr == null || Boolean.parseBoolean(frozenStr) == a.isFrozen())
                .filter(a -> username == null || a.getUsername().contains(username))
                .skip(offset)
                .limit(limit)
                .collect(Collectors.toList());
            ctx.json(filtered);
        });

        // GET /accounts/count
        app.get("/accounts/count", ctx -> ctx.json(Map.of("count", accounts.size())));

        // GET /accounts/{username}
        app.get("/accounts/{username}", ctx -> {
            String username = ctx.pathParam("username");
            findByUsername(username)
                .ifPresentOrElse(ctx::json, () -> ctx.status(404).result("Account not found"));
        });

        // POST /accounts/{username}/freeze
        app.post("/accounts/{username}/freeze", ctx -> {
            String username = ctx.pathParam("username");
            
            // 20% random failure rate
            if (new Random().nextInt(5) == 0) {
                System.err.println("[core-banking] Randomly failing freeze for: " + username);
                ctx.status(500).result("Random internal failure");
                return;
            }

            Optional<Account> acc = findByUsername(username);
            if (acc.isPresent()) {
                acc.get().setFrozen(true);
                System.out.println("[core-banking] Account FROZEN: " + username);
                ctx.status(204);
            } else {
                ctx.status(404).result("Account not found");
            }
        });

        // POST /accounts/{username}/unfreeze
        app.post("/accounts/{username}/unfreeze", ctx -> {
            String username = ctx.pathParam("username");
            Optional<Account> acc = findByUsername(username);
            if (acc.isPresent()) {
                acc.get().setFrozen(false);
                System.out.println("[core-banking] Account UNFROZEN: " + username);
                ctx.status(204);
            } else {
                ctx.status(404).result("Account not found");
            }
        });

        // GET /users - list users
        app.get("/users", ctx -> {
            String limitStr = ctx.queryParam("limit");
            String offsetStr = ctx.queryParam("offset");
            int limit = limitStr != null ? Integer.parseInt(limitStr) : 100;
            int offset = offsetStr != null ? Integer.parseInt(offsetStr) : 0;
            List<User> page = users.stream().skip(offset).limit(limit).collect(Collectors.toList());
            ctx.json(page);
        });

        // GET /users/{userId}
        app.get("/users/{userId}", ctx -> {
            String userId = ctx.pathParam("userId");
            users.stream().filter(u -> userId.equals(u.getUserId())).findFirst()
                .ifPresentOrElse(ctx::json, () -> ctx.status(404).result("User not found"));
        });

        System.out.println("[core-banking] Started on port 7002 with " + accounts.size() + " accounts, " + users.size() + " users");
    }

    private static Optional<Account> findByUsername(String username) {
        return accounts.stream().filter(a -> username.equals(a.getUsername())).findFirst();
    }

    private static void loadData() {
        try (InputStream in = Main.class.getResourceAsStream("/accounts.json")) {
            List<Account> loaded = mapper.readValue(in,
                mapper.getTypeFactory().constructCollectionType(List.class, Account.class));
            accounts.addAll(loaded);
            System.out.println("[core-banking] Loaded " + loaded.size() + " accounts");
        } catch (Exception e) {
            System.err.println("[core-banking] Failed to load accounts: " + e.getMessage());
        }
        try (InputStream in = Main.class.getResourceAsStream("/users.json")) {
            List<User> loaded = mapper.readValue(in,
                mapper.getTypeFactory().constructCollectionType(List.class, User.class));
            users.addAll(loaded);
            System.out.println("[core-banking] Loaded " + loaded.size() + " users");
        } catch (Exception e) {
            System.err.println("[core-banking] Failed to load users: " + e.getMessage());
        }
    }
}

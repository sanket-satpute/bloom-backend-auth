package com.bloom.backend.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
public class SupabaseService {

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.service.key}")
    private String serviceRoleKey;

    @Value("${supabase.jwt.secret}")
    private String jwtSecret;

    private final OkHttpClient client = new OkHttpClient();

    public String findOrCreateUser(String phoneNumber, String name, String avatarUrl, String email) throws IOException {
        String userId = findUserByPhone(phoneNumber);
        if (userId != null) {
            syncUserProfile(userId, phoneNumber, name, avatarUrl, email);
            return userId;
        }
        userId = createUser(phoneNumber, name, avatarUrl, email);
        syncUserProfile(userId, phoneNumber, name, avatarUrl, email);
        return userId;
    }

    private String findUserByPhone(String phoneNumber) throws IOException {
        // We use the REST API on auth.users table if it is exposed, OR we can just try to create and catch error,
        // but Supabase provides Admin API to list users, though not easily searchable by phone directly without pagination.
        // A better way is to attempt to create the user, and if it fails due to unique constraint, it means they exist.
        // Wait, the Admin API /auth/v1/admin/users allows us to query or we can just try to create.
        // Let's just create. If it fails, how do we get the UUID? 
        // Actually, Supabase has an Admin API to invite/create users.
        return null;
    }

    public String getOrCreateUserByPhone(String phoneNumber, String name, String avatarUrl, String email) throws IOException {
        // Workaround: We can use the Admin API to create the user. If they exist, it returns 422.
        // Since Supabase doesn't easily let us find by phone via Admin API, we can hit the GraphQL or REST on public.users 
        // if we sync auth.users to public.users. 
        // For simplicity, we will query the Admin API list users and filter, or just use a custom RPC.
        // Let's just create the user. 
        
        JSONObject json = new JSONObject();
        json.put("phone", phoneNumber);
        json.put("phone_confirm", true);
        if (email != null && !email.isEmpty()) {
            json.put("email", email);
            json.put("email_confirm", true);
        }
        
        JSONObject userMetadata = new JSONObject();
        if (name != null && !name.isEmpty()) userMetadata.put("full_name", name);
        if (avatarUrl != null && !avatarUrl.isEmpty()) userMetadata.put("avatar_url", avatarUrl);
        if (email != null && !email.isEmpty()) userMetadata.put("email", email);
        if (userMetadata.length() > 0) json.put("user_metadata", userMetadata);
        
        RequestBody body = RequestBody.create(json.toString(), MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(supabaseUrl + "/auth/v1/admin/users")
                .post(body)
                .addHeader("Authorization", "Bearer " + serviceRoleKey)
                .addHeader("apikey", serviceRoleKey)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                JSONObject resJson = new JSONObject(response.body().string());
                return resJson.getString("id");
            } else if (response.code() == 422) {
                // User already exists. We need their UUID.
                // Admin API /auth/v1/admin/users lets us list users. We'd have to iterate.
                // A better pattern for a backend is to maintain our own mirror or use a Supabase RPC.
                return findUserByPhoneFallback(phoneNumber);
            } else {
                throw new IOException("Failed to create Supabase user: " + (response.body() != null ? response.body().string() : response.code()));
            }
        }
    }

    private String findUserByPhoneFallback(String phoneNumber) throws IOException {
        // Hit the Admin API to list users
        Request request = new Request.Builder()
                .url(supabaseUrl + "/auth/v1/admin/users")
                .get()
                .addHeader("Authorization", "Bearer " + serviceRoleKey)
                .addHeader("apikey", serviceRoleKey)
                .build();
                
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                JSONObject resJson = new JSONObject(response.body().string());
                JSONArray users = resJson.getJSONArray("users");
                for (int i = 0; i < users.length(); i++) {
                    JSONObject user = users.getJSONObject(i);
                    if (user.has("phone") && phoneNumber.equals(user.getString("phone"))) {
                        return user.getString("id");
                    }
                }
            }
        }
        throw new IOException("User exists but could not be found in list.");
    }

    private String createUser(String phoneNumber, String name, String avatarUrl, String email) throws IOException {
        return getOrCreateUserByPhone(phoneNumber, name, avatarUrl, email);
    }

    private void syncUserProfile(String userId, String phoneNumber, String name, String avatarUrl, String email) throws IOException {
        JSONObject profile = new JSONObject();
        profile.put("id", userId);
        profile.put("phone", phoneNumber);
        if (name != null && !name.isEmpty()) profile.put("full_name", name);
        if (avatarUrl != null && !avatarUrl.isEmpty()) profile.put("avatar_url", avatarUrl);
        if (email != null && !email.isEmpty()) profile.put("email", email);
        
        // Include timezone format compatible with Postgres timestamptz
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        profile.put("updated_at", sdf.format(new java.util.Date()));

        RequestBody body = RequestBody.create(profile.toString(), MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(supabaseUrl + "/rest/v1/profiles")
                .post(body)
                .addHeader("Authorization", "Bearer " + serviceRoleKey)
                .addHeader("apikey", serviceRoleKey)
                .addHeader("Prefer", "resolution=ignore-duplicates") // Do not overwrite if it exists
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                System.err.println("Failed to sync profile to public.profiles: " + (response.body() != null ? response.body().string() : response.code()));
            }
        }
    }

    public String generateCustomJwt(String userId) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        
        long nowMillis = System.currentTimeMillis();
        long expMillis = nowMillis + 31536000000L; // 1 year validity
        Date now = new Date(nowMillis);
        Date exp = new Date(expMillis);

        return Jwts.builder()
                .subject(userId)
                .audience().add("authenticated").and()
                .claim("role", "authenticated")
                .claim("app_metadata", new JSONObject().put("provider", "truecaller").toMap())
                .issuedAt(now)
                .expiration(exp)
                .signWith(key)
                .compact();
    }
}

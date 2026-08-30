package com.bloom.backend.service;

import okhttp3.*;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class TruecallerService {

    @Value("${truecaller.client.id}")
    private String clientId;

    @Value("${truecaller.client.secret:}")
    private String clientSecret;

    private final OkHttpClient client = new OkHttpClient();

    public String exchangeCodeForAccessToken(String authorizationCode, String codeVerifier) throws IOException {
        FormBody.Builder formBuilder = new FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("client_id", clientId)
                .add("code", authorizationCode)
                .add("code_verifier", codeVerifier);

        if (clientSecret != null && !clientSecret.isEmpty()) {
            formBuilder.add("client_secret", clientSecret);
        }

        RequestBody body = formBuilder.build();

        Request request = new Request.Builder()
                .url("https://oauth-account-noneu.truecaller.com/v1/token")
                .post(body)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Failed to exchange Truecaller code: " + (response.body() != null ? response.body().string() : response.code()));
            }

            JSONObject resJson = new JSONObject(response.body().string());
            return resJson.getString("access_token");
        }
    }

    public java.util.Map<String, String> fetchUserProfile(String accessToken) throws IOException {
        Request request = new Request.Builder()
                .url("https://oauth-account-noneu.truecaller.com/v1/userinfo")
                .get()
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Failed to fetch Truecaller profile: " + (response.body() != null ? response.body().string() : response.code()));
            }

            JSONObject resJson = new JSONObject(response.body().string());
            java.util.Map<String, String> profile = new java.util.HashMap<>();
            profile.put("phone_number", resJson.optString("phone_number", ""));
            
            // Truecaller may return 'name' or separate 'given_name'/'family_name'
            String name = resJson.optString("name", "");
            if (name.isEmpty()) {
                String givenName = resJson.optString("given_name", "");
                String familyName = resJson.optString("family_name", "");
                name = (givenName + " " + familyName).trim();
            }
            profile.put("name", name);
            
            // Try 'avatar' or 'picture'
            String avatar = resJson.optString("avatar", "");
            if (avatar.isEmpty()) avatar = resJson.optString("picture", "");
            if (avatar.isEmpty()) avatar = resJson.optString("avatar_url", "");
            profile.put("avatar_url", avatar);

            String email = resJson.optString("email", "");
            profile.put("email", email);

            return profile;
        }
    }
}

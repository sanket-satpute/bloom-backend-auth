package com.bloom.backend.controller;

import com.bloom.backend.dto.AuthResponse;
import com.bloom.backend.dto.TruecallerAuthRequest;
import com.bloom.backend.service.SupabaseService;
import com.bloom.backend.service.TruecallerService;
import org.apache.juli.logging.Log;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private TruecallerService truecallerService;

    Logger logger = org.slf4j.LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private SupabaseService supabaseService;

    @PostMapping("/truecaller/verify")
    public ResponseEntity<?> verifyTruecaller(@RequestBody TruecallerAuthRequest request) {
        logger.info("Received Truecaller auth request for code: " + request.getAuthorizationCode());
        try {
            // 1. Exchange auth code for access token
            String accessToken = truecallerService.exchangeCodeForAccessToken(
                    request.getAuthorizationCode(),
                    request.getCodeVerifier()
            );
            logger.info("in try block 34 " + request.getAuthorizationCode());
            // 2. Fetch user's profile
            java.util.Map<String, String> profile = truecallerService.fetchUserProfile(accessToken);
            String phoneNumber = profile.get("phone_number");
            String name = profile.get("name");
            String avatarUrl = profile.get("avatar_url");
            String email = profile.get("email");

            try {
                // 3. Find or Create the user in Supabase
                String userId = supabaseService.findOrCreateUser(phoneNumber, name, avatarUrl, email);

                // 4. Generate custom Supabase JWT
                String jwt = supabaseService.generateCustomJwt(userId);
                return ResponseEntity.ok(new AuthResponse(jwt));
            } catch (Exception se) {
                // DEV MODE FALLBACK: Generate local JWT if Supabase is not configured
                String devJwt = otpService.generateDevJwt(phoneNumber);
                System.out.println("Supabase failed, using dev JWT for Truecaller: " + phoneNumber);
                return ResponseEntity.ok(new AuthResponse(devJwt));
            }

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(401).body("Authentication failed: " + e.getMessage());
        }
    }

    @Autowired
    private com.bloom.backend.service.OtpService otpService;

    @PostMapping("/otp/send")
    public ResponseEntity<?> sendOtp(@RequestBody java.util.Map<String, String> request) {
        String phone = request.get("phone");
        if (phone == null || phone.isEmpty()) {
            return ResponseEntity.badRequest().body("Phone number is required");
        }
        String otp = otpService.generateAndSendOtp(phone);
        // DEV MODE: Return OTP in response so app can show it in a toast
        // TODO: Remove this in production — only send via real SMS
        java.util.Map<String, String> response = new java.util.HashMap<>();
        response.put("message", "OTP sent successfully");
        response.put("dev_otp", otp);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<?> verifyOtp(@RequestBody java.util.Map<String, String> request) {
        String phone = request.get("phone");
        String otp = request.get("token"); // matching supabase terminology
        
        if (phone == null || otp == null) {
            return ResponseEntity.badRequest().body("Phone and token are required");
        }
        
        String jwt = otpService.verifyOtp(phone, otp);
        if (jwt != null) {
            return ResponseEntity.ok(new AuthResponse(jwt));
        } else {
            return ResponseEntity.status(401).body("Invalid OTP");
        }
    }
}

package com.bloom.backend.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OtpService {

    // Store OTPs temporarily in memory. Key: phone number, Value: OTP
    private final Map<String, String> otpStorage = new ConcurrentHashMap<>();

    @Value("${supabase.jwt.secret}")
    private String jwtSecret;

    @Value("${twilio.account.sid}")
    private String twilioAccountSid;

    @Value("${twilio.auth.token}")
    private String twilioAuthToken;

    @Value("${twilio.phone.number}")
    private String twilioPhoneNumber;

    public String generateAndSendOtp(String phone) {
        // Generate a new, unique, random 6-digit OTP every single time
        String otp = String.format("%06d", new Random().nextInt(999999));
        otpStorage.put(phone, otp);
        
        boolean useTwilio = twilioAccountSid != null && !twilioAccountSid.startsWith("YOUR_")
                         && twilioAuthToken != null && !twilioAuthToken.startsWith("YOUR_")
                         && twilioPhoneNumber != null && !twilioPhoneNumber.startsWith("YOUR_");

        if (useTwilio) {
            try {
                Twilio.init(twilioAccountSid, twilioAuthToken);
                // We send a generic template name because Twilio trial blocks random OTP strings in India.
                Message message = Message.creator(
                        new PhoneNumber(phone),
                        new PhoneNumber(twilioPhoneNumber),
                        "sms_2fa"
                ).create();
                System.out.println("Twilio SMS sent successfully! SID: " + message.getSid());
            } catch (Exception e) {
                System.err.println("Failed to send Twilio SMS: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        mockSmsFallback(phone, otp);
        return otp;
    }

    private void mockSmsFallback(String phone, String otp) {
        System.out.println("=================================================");
        System.out.println("🚀 MOCK SMS PROVIDER 🚀");
        System.out.println("To: " + phone);
        System.out.println("Your Bloom verification code is: " + otp);
        System.out.println("=================================================");
    }

    public String verifyOtp(String phone, String otp) {
        String storedOtp = otpStorage.get(phone);
        if (storedOtp != null && storedOtp.equals(otp)) {
            otpStorage.remove(phone); // Clear OTP after successful verification
            
            // Generate a dev JWT token directly
            // TODO: In production, create/find user in Supabase first
            return generateDevJwt(phone);
        }
        return null; // Invalid OTP
    }

    public String generateDevJwt(String phone) {
        // Use a stable secret for dev mode
        String secret = jwtSecret;
        if (secret == null || secret.startsWith("YOUR_")) {
            // Fallback dev secret (at least 32 bytes for HS256)
            secret = "bloom-dev-secret-key-for-local-testing-only-32bytes!!";
        }
        
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        String userId = UUID.nameUUIDFromBytes(phone.getBytes(StandardCharsets.UTF_8)).toString();
        
        long nowMillis = System.currentTimeMillis();
        Date now = new Date(nowMillis);
        Date exp = new Date(nowMillis + 86400000L); // 24 hour validity

        return Jwts.builder()
                .subject(userId)
                .claim("phone", phone)
                .claim("role", "authenticated")
                .issuedAt(now)
                .expiration(exp)
                .signWith(key)
                .compact();
    }
}


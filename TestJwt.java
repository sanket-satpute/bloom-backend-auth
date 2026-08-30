import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.io.Decoders;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

public class TestJwt {
    public static void main(String[] args) {
        String jwtSecret = "iPTtSpV+YijuA1Nfna33KWfNu3WicsQIN+Z+SI2Ov9hjriW7PUfEqJGe/ftBOq9c7+ca7eCddPkzA4c8j5dCQA==";
        
        // 1. Literal bytes
        var key1 = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        
        // 2. Base64 decoded bytes
        var key2 = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));

        long nowMillis = System.currentTimeMillis();
        long expMillis = nowMillis + 31536000000L;
        Date now = new Date(nowMillis);
        Date exp = new Date(expMillis);

        String token1 = Jwts.builder()
                .subject("test-uuid")
                .audience().add("authenticated").and()
                .claim("role", "authenticated")
                .claim("app_metadata", Map.of("provider", "truecaller"))
                .issuedAt(now)
                .expiration(exp)
                .signWith(key1)
                .compact();
                
        String token2 = Jwts.builder()
                .subject("test-uuid")
                .audience().add("authenticated").and()
                .claim("role", "authenticated")
                .claim("app_metadata", Map.of("provider", "truecaller"))
                .issuedAt(now)
                .expiration(exp)
                .signWith(key2)
                .compact();
                
        System.out.println("Token with getBytes(UTF-8):\n" + token1);
        System.out.println("Token with Base64.decode():\n" + token2);
    }
}

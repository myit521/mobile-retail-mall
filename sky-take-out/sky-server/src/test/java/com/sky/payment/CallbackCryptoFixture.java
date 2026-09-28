package com.sky.payment;

import com.alibaba.fastjson.JSON;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Disposable, public test-only RSA key/certificate; never used outside test fixtures. */
final class CallbackCryptoFixture {
    static final String SERIAL = "1B14A4DB131124A5";
    static final String AES_KEY = "0123456789abcdef0123456789abcdef";
    private static final String CERTIFICATE = "MIICwDCCAaigAwIBAgIIGxSk2xMRJKUwDQYJKoZIhvcNAQELBQAwIDEeMBwGA1UEAxMVUGF5bWVudCBjYWxsYmFjayB0ZXN0MB4XDTI2MDkwNjEzMzYwMVoXDTM2MDkwNzEzMzYwMVowIDEeMBwGA1UEAxMVUGF5bWVudCBjYWxsYmFjayB0ZXN0MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAuxSCazDuS/TVY1uKCLiQ6lFcm5rGsYcYmHxQytlDH/NlxLUNEeRALYW8aho2D4zHMp/XTQ8eA6eGWYW40eicL6jLfCLQPC+hwCIjqFaMqG57/SOfEQa3jwk6Z1RbNlNEUYNIjqjJlQvvVKgA1WPD8gEf3g3YO9fzV1vV6sD1C4PoS4M14FwjR7La7kdbQEUD/eIHdb+kLgELGjl6JDeco2yhnGuVj0CJAsfLnac31ZiuBY58HHX+B3kPLwSpruWmvnhDtQt6Q7dPO1s7X2GbGpWrO03KponIXg5pa1dUhGZLEfRjgSw6h35D4Sm2yXMAn9iXupJMd4ntS2eLTtZyMQIDAQABMA0GCSqGSIb3DQEBCwUAA4IBAQBMqIZcjh1OxETmqaycJhkoNbRe6HVQcZUtmRo9x7LZprh0EKZVz3IenDi+auyiAmBNERc6l9dZxaI+MMJcC83HfAciCf6O83MPJ4xOYrv5cUJvKuMaJjdzZ4Db6xT90qzJXYv/fu4K6nqlvjQXuTOnb1OvXXaJmuTKu3wlW8/v6ztOA8LWo7iQTbqpcmMp3nU9t1vhbqhEhsSn2FJ71bQ4ixhctj8wzZvTAvrZnnW+kUJKaW4G9pM/HImh06sgMWig/xz6PKP1ZiyILBm68VVHF0geNwmzKapRyvlOwRzb3iHJQJZ5catu8CC0sjUwfwscv4OD35OfXgOTpbxQTSa0";
    private static final String PRIVATE_KEY = "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQC7FIJrMO5L9NVjW4oIuJDqUVybmsaxhxiYfFDK2UMf82XEtQ0R5EAthbxqGjYPjMcyn9dNDx4Dp4ZZhbjR6JwvqMt8ItA8L6HAIiOoVoyobnv9I58RBrePCTpnVFs2U0RRg0iOqMmVC+9UqADVY8PyAR/eDdg71/NXW9XqwPULg+hLgzXgXCNHstruR1tARQP94gd1v6QuAQsaOXokN5yjbKGca5WPQIkCx8udpzfVmK4Fjnwcdf4HeQ8vBKmu5aa+eEO1C3pDt087WztfYZsalas7TcqmicheDmlrV1SEZksR9GOBLDqHfkPhKbbJcwCf2Je6kkx3ie1LZ4tO1nIxAgMBAAECggEAJ+gsGemKK7HCgztXqpyUbSeF9buCfwQufvil01+dLgehweBNNW/XMN7CXm/Q8Gg7ZdUq/EkpQeZOhnI8Bqr0BkafgY97lBslCfM+X/52aseGs20R1XP1XBG/36LjAieo/ypeI/Blb+Hn38smwl5RwiHzRk71vW5Hfm8cpsMagqX3WaUGNAUqFzawEFBDd+/da7LzoqjbxksfEUyXKbRsUebLk2vXJW/XUTfSOHKWBh7TQmaUvesLo1udpUASbCs2XBYI7hDkwCHzXRc7QGwlFz/Prx4rJS8FUoW9ioSWivfeDxwV9rbvzGK8wmdWOfVepoXtNw5bASs6sAoWhr3YsQKBgQDW2fXoe3GlJShm9XgtmTk38IypDo1lXUwJQXbXH6RwKOOyylngUSewkIP7PCepfZ2NDw0nwSR6XW3cLvOJRin/gViUY+tSn3uxi7WyjFkLYqN71jXNpiKU4iepIgxsBy3SOr3x7PYmees4q5lJy9bEvF8dqi7QJEACYV8Sq5fRiwKBgQDe6PBDMZTBJkMfiSySqDrxkBLMia/R/Cm9IgOZTqK9BtHu/+7Lj0YZQECstFOP8TL9yBsXeplXc7UwM3SHiYSZHWZ/HkIp0faNhBcP02klqtF2ia77sepXa5WXe5McW0NLOek4nUcGloAyvfMBXcqpZUIuA98ENUoj10R8uW6KswKBgQCvaPANyIr2K6oatRpTEB/Y/fm5JHpoYY1smRfvdpQIRjnwhKhwLZHb44D9oxU2maoBumIhLN0dUh4Zf/OxBanQQsgRDqrXoAGa6NeFWHRyiSu0NSVkKtlc+G8s9PFzWUEdvHvLgo1MyIk0kVTrHRLROIHndEQYByCDUQAP/CxNFQKBgCuxYRbqqwvJIjMWQkRk8VjUMrsg6fOxB8Vo22e7gC6pHZFJXVdNOCJO895mYlK+po4UIStS/qejqIpQK7E9hV414xdbqQBlhXxrvdF+UQfvGauwhToUv6hvEB1nPYi7Ys9mBI7yVS+3ZF2N2goUhlr53CUE4TwD7gpkcao49q/9AoGBALUmTkfTQ7xrNPkesViFdLgCKwUmajTiakzBUu/hVNWl7PzzJJqYrF5jQHAtCaA1aHhu9/kuCoG60vP44QA3VbLJvF4mf1ILNrbh5iKmofy5NsXBRYvAfHsEEF06+tQpusBQcBsEvX3rJP0fAa3teLBpFeWegkYSLQDWTqPcdbGN";

    static Path certificate(Path directory) throws Exception {
        Path file = directory.resolve("platform.cer");
        Files.write(file, Base64.getDecoder().decode(CERTIFICATE));
        return file;
    }

    static String body(String plaintext) throws Exception {
        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(AES_KEY.getBytes(StandardCharsets.UTF_8), "AES"),
                new GCMParameterSpec(128, "123456789012".getBytes(StandardCharsets.UTF_8)));
        aes.updateAAD("payment".getBytes(StandardCharsets.UTF_8));
        String ciphertext = Base64.getEncoder().encodeToString(aes.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
        return JSON.toJSONString(Map.of("resource", Map.of("ciphertext", ciphertext,
                "nonce", "123456789012", "associated_data", "payment"))) + "\n";
    }

    static String signature(String timestamp, String nonce, String body) throws Exception {
        Signature rsa = Signature.getInstance("SHA256withRSA");
        rsa.initSign(KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(PRIVATE_KEY))));
        rsa.update((timestamp + "\n" + nonce + "\n" + body + "\n").getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(rsa.sign());
    }
}

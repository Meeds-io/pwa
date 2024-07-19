package io.meeds.pwa.utils;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;

import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;

import nl.martijndwars.webpush.Base64Encoder;
import nl.martijndwars.webpush.Utils;
import nl.martijndwars.webpush.cli.handlers.GenerateKeyHandler;

public class VapidKeysUtils {

  public static final String CURVE     = "prime256v1";

  public static final String ALGORITHM = "ECDH";

  private VapidKeysUtils() {
    // Utils class, thus private constructor
  }

  public static KeyPair generateKeys() throws InvalidAlgorithmParameterException,
                                       NoSuchAlgorithmException,
                                       NoSuchProviderException {
    return new GenerateKeyHandler(null).generateKeyPair();
  }

  public static String encode(ECPublicKey publicKey) {
    byte[] encodedPublicKey = Utils.encode(publicKey);
    return Base64Encoder.encodeUrl(encodedPublicKey);
  }

  public static String encode(ECPrivateKey privateKey) {
    byte[] encodedPrivateKey = Utils.encode(privateKey);
    return Base64Encoder.encodeUrl(encodedPrivateKey);
  }

  public static PublicKey decodePublicKey(String publicKey) throws NoSuchAlgorithmException,
                                                            NoSuchProviderException,
                                                            InvalidKeySpecException {
    return Utils.loadPublicKey(publicKey);
  }

  public static PrivateKey decodePrivateKey(String privateKey) throws NoSuchAlgorithmException,
                                                               NoSuchProviderException,
                                                               InvalidKeySpecException {
    return Utils.loadPrivateKey(privateKey);
  }

}

/*
 * Copyright 2019, OnGres.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 * following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */


package com.ongres.scram.common;


import static com.ongres.scram.common.util.Preconditions.checkNotNull;
import static com.ongres.scram.common.util.Preconditions.gt0;

import com.ongres.scram.common.bouncycastle.pbkdf2.Digest;
import com.ongres.scram.common.bouncycastle.pbkdf2.DigestFactory;
import com.ongres.scram.common.bouncycastle.pbkdf2.HMac;
import com.ongres.scram.common.bouncycastle.pbkdf2.KeyParameter;
import com.ongres.scram.common.bouncycastle.pbkdf2.PBEParametersGenerator;
import com.ongres.scram.common.bouncycastle.pbkdf2.PKCS5S2ParametersGenerator;
import com.ongres.scram.common.stringprep.StringPreparation;
import com.ongres.scram.common.util.CryptoUtil;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.SecretKeySpec;

/**
 * SCRAM Mechanisms supported by this library.
 * At least, SCRAM-SHA-1 and SCRAM-SHA-256 are provided, since both the hash and the HMAC implementations
 * are provided by the Java JDK version 6 or greater.
 *
 * {@link java.security.MessageDigest}: "Every implementation of the Java platform is required to support the
 * following standard MessageDigest algorithms: MD5, SHA-1, SHA-256".
 *
 * {@link javax.crypto.Mac}: "Every implementation of the Java platform is required to support the following
 * standard Mac algorithms: HmacMD5, HmacSHA1, HmacSHA256".
 *
 * @see <a href="https://www.iana.org/assignments/sasl-mechanisms/sasl-mechanisms.xhtml#scram">
 *      SASL SCRAM Family Mechanisms</a>
 */
public enum ScramMechanisms implements ScramMechanism {
    SCRAM_SHA_1         (   "SHA-1",    "SHA-1",    160,    "HmacSHA1",     false,  1   ),
    SCRAM_SHA_1_PLUS    (   "SHA-1",    "SHA-1",    160,    "HmacSHA1",     true,   1   ),
    SCRAM_SHA_256       (   "SHA-256",  "SHA-256",  256,    "HmacSHA256",   false,  10  ),
    SCRAM_SHA_256_PLUS  (   "SHA-256",  "SHA-256",  256,    "HmacSHA256",   true,   10  )
    ;

    private static final String SCRAM_MECHANISM_NAME_PREFIX = "SCRAM-";
    private static final String CHANNEL_BINDING_SUFFIX = "-PLUS";
    private static final String PBKDF2_PREFIX_ALGORITHM_NAME = "PBKDF2With";
    private static final Map<String,ScramMechanisms> BY_NAME_MAPPING = valuesAsMap();

    private final String mechanismName;
    private final String hashAlgorithmName;
    private final int keyLength;
    private final String hmacAlgorithmName;
    private final boolean channelBinding;
    private final int priority;

    ScramMechanisms(
            String name, String hashAlgorithmName, int keyLength, String hmacAlgorithmName, boolean channelBinding,
            int priority
    ) {
        this.mechanismName = SCRAM_MECHANISM_NAME_PREFIX
                + checkNotNull(name, "name")
                + (channelBinding ? CHANNEL_BINDING_SUFFIX : "")
        ;
        this.hashAlgorithmName = checkNotNull(hashAlgorithmName, "hashAlgorithmName");
        this.keyLength = gt0(keyLength, "keyLength");
        this.hmacAlgorithmName = checkNotNull(hmacAlgorithmName, "hmacAlgorithmName");
        this.channelBinding = channelBinding;
        this.priority = gt0(priority, "priority");
    }

    /**
     * Method that returns the name of the hash algorithm.
     * It is protected since should be of no interest for direct users.
     * The instance is supposed to provide abstractions over the algorithm names,
     * and are not meant to be directly exposed.
     * @return The name of the hash algorithm
     */
    protected String getHashAlgorithmName() {
        return hashAlgorithmName;
    }

    /**
     * Method that returns the name of the HMAC algorithm.
     * It is protected since should be of no interest for direct users.
     * The instance is supposed to provide abstractions over the algorithm names,
     * and are not meant to be directly exposed.
     * @return The name of the HMAC algorithm
     */
    protected String getHmacAlgorithmName() {
        return hmacAlgorithmName;
    }

    @Override
    public String getName() {
        return mechanismName;
    }

    @Override
    public boolean supportsChannelBinding() {
        return channelBinding;
    }

    @Override
    public int algorithmKeyLength() {
        return keyLength;
    }

    @Override
    public byte[] digest(byte[] message) {
        try {
            return MessageDigest.getInstance(hashAlgorithmName).digest(message);
        } catch (NoSuchAlgorithmException e) {
            if(!ScramMechanisms.SCRAM_SHA_256.getHmacAlgorithmName().equals(getHmacAlgorithmName())) {
              throw new RuntimeException("Algorithm " + hashAlgorithmName + " not present in current JVM");
            }

            Digest digest = DigestFactory.createSHA256();
            digest.update(message, 0, message.length);
            byte[] out = new byte[digest.getDigestSize()];
            digest.doFinal(out, 0);
            return out;
        }
    }

    @Override
    public byte[] hmac(byte[] key, byte[] message) {
        try {
            return CryptoUtil.hmac(new SecretKeySpec(key, hmacAlgorithmName), Mac.getInstance(hmacAlgorithmName), message);
        } catch (NoSuchAlgorithmException e) {
            if(!ScramMechanisms.SCRAM_SHA_256.getHmacAlgorithmName().equals(getHmacAlgorithmName())) {
              throw new RuntimeException("MAC Algorithm " + hmacAlgorithmName + " not present in current JVM");
            }

            HMac mac = new HMac(DigestFactory.createSHA256());
            mac.init(new KeyParameter(key));
            mac.update(message, 0, message.length);
            byte[] out = new byte[mac.getMacSize()];
            mac.doFinal(out, 0);
            return out;
        }
    }

    @Override
    public byte[] saltedPassword(StringPreparation stringPreparation, String password, byte[] salt,
            int iterations) {
        char[] normalizedString = stringPreparation.normalize(password).toCharArray();
        try {
            return CryptoUtil.hi(
                SecretKeyFactory.getInstance(PBKDF2_PREFIX_ALGORITHM_NAME + hmacAlgorithmName),
                algorithmKeyLength(),
                normalizedString,
                salt,
                iterations);
        } catch (NoSuchAlgorithmException e) {
            if(!ScramMechanisms.SCRAM_SHA_256.getHmacAlgorithmName().equals(getHmacAlgorithmName())) {
              throw new RuntimeException("Unsupported PBKDF2 for " + mechanismName);
            }

            PBEParametersGenerator generator = new PKCS5S2ParametersGenerator(DigestFactory.createSHA256());
            generator.init(PBEParametersGenerator.PKCS5PasswordToUTF8Bytes(normalizedString), salt, iterations);
            KeyParameter params = (KeyParameter)generator.generateDerivedParameters(algorithmKeyLength());
            return params.getKey();
        }
    }

    /**
     * Gets a SCRAM mechanism, given its standard IANA name.
     * @param name The standard IANA full name of the mechanism.
     * @return An Optional instance that contains the ScramMechanism if it was found, or empty otherwise.
     */
    public static ScramMechanisms byName(String name) {
        checkNotNull(name, "name");

        return BY_NAME_MAPPING.get(name);
    }

    /**
     * This class classifies SCRAM mechanisms by two properties: whether they support channel binding;
     * and a priority, which is higher for safer algorithms (like SHA-256 vs SHA-1).
     *
     * Given a list of SCRAM mechanisms supported by the peer, pick one that matches the channel binding requirements
     * and has the highest priority.
     *
     * @param channelBinding The type of matching mechanism searched for
     * @param peerMechanisms The mechanisms supported by the other peer
     * @return The selected mechanism, or null if no mechanism matched
     */
    public static ScramMechanism selectMatchingMechanism(boolean channelBinding, String... peerMechanisms) {
        ScramMechanisms selectedScramMechanisms = null;
        for (String peerMechanism : peerMechanisms) {
            ScramMechanisms matchedScramMechanisms = BY_NAME_MAPPING.get(peerMechanism);
            if (matchedScramMechanisms != null) {
                for (ScramMechanisms candidateScramMechanisms : ScramMechanisms.values()) {
                    if (channelBinding == candidateScramMechanisms.channelBinding
                        && candidateScramMechanisms.mechanismName.equals(matchedScramMechanisms.mechanismName)
                        && (selectedScramMechanisms == null 
                            || selectedScramMechanisms.priority < candidateScramMechanisms.priority)) {
                        selectedScramMechanisms = candidateScramMechanisms;
                    }
                }
            }
        }
        return selectedScramMechanisms;
    }
    
    private static Map<String, ScramMechanisms> valuesAsMap() {
        Map<String, ScramMechanisms> mapScramMechanisms = new HashMap<>(values().length);
        for (ScramMechanisms scramMechanisms : values()) {
            mapScramMechanisms.put(scramMechanisms.getName(), scramMechanisms);
        }
        return mapScramMechanisms;
    }

}

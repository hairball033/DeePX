package com.smallhairball.deepx;

/**
 * Local API route/auth string provider.  The plaintext never appears in the DEX: the primary
 * source is the native core (z15.f, per-string XOR key), and this class only carries a
 * masked fallback used when the .so cannot be loaded.  Keeping the Local API route and auth
 * header names out of the DEX string pool breaks the string -> method -> call-graph shortcut.
 */
public final class z3 {
    public static final int MESSAGES = 0;
    public static final int MESSAGES_COUNT_TOKENS = 1;
    public static final int RESPONSES = 2;
    public static final int CHAT_COMPLETIONS = 3;
    public static final int MODELS = 4;
    public static final int MODELS_PREFIX = 5;
    public static final int USER_BALANCE = 6;
    public static final int HDR_AUTHORIZATION = 7;
    public static final int HDR_X_API_KEY = 8;
    public static final int BEARER = 9;

    private z3() {}

    public static String get(int id) {
        if (z15.available()) {
            try {
                String value = z15.f(id);
                if (value != null) return value;
            } catch (Throwable ignored) {}
        }
        return fallback(id);
    }

    private static String fallback(int id) {
        byte[] encoded;
        byte key;
        switch (id) {
            case MESSAGES: encoded = E0; key = K0; break;
            case MESSAGES_COUNT_TOKENS: encoded = E1; key = K1; break;
            case RESPONSES: encoded = E2; key = K2; break;
            case CHAT_COMPLETIONS: encoded = E3; key = K3; break;
            case MODELS: encoded = E4; key = K4; break;
            case MODELS_PREFIX: encoded = E5; key = K5; break;
            case USER_BALANCE: encoded = E6; key = K6; break;
            case HDR_AUTHORIZATION: encoded = E7; key = K7; break;
            case HDR_X_API_KEY: encoded = E8; key = K8; break;
            case BEARER: encoded = E9; key = K9; break;
            default: return null;
        }
        char[] out = new char[encoded.length];
        for (int i = 0; i < encoded.length; i++) out[i] = (char) (encoded[i] ^ key);
        return new String(out);
    }

    private static final byte[] E0 = new byte[]{(byte)0x52,(byte)0x0b,(byte)0x4c,(byte)0x52,(byte)0x10,(byte)0x18,(byte)0x0e,(byte)0x0e,(byte)0x1c,(byte)0x1a,(byte)0x18,(byte)0x0e};
    private static final byte K0 = (byte)0x7d;
    private static final byte[] E1 = new byte[]{(byte)0x9f,(byte)0xc6,(byte)0x81,(byte)0x9f,(byte)0xdd,(byte)0xd5,(byte)0xc3,(byte)0xc3,(byte)0xd1,(byte)0xd7,(byte)0xd5,(byte)0xc3,(byte)0x9f,(byte)0xd3,(byte)0xdf,(byte)0xc5,(byte)0xde,(byte)0xc4,(byte)0xef,(byte)0xc4,(byte)0xdf,(byte)0xdb,(byte)0xd5,(byte)0xde,(byte)0xc3};
    private static final byte K1 = (byte)0xb0;
    private static final byte[] E2 = new byte[]{(byte)0x91,(byte)0xc8,(byte)0x8f,(byte)0x91,(byte)0xcc,(byte)0xdb,(byte)0xcd,(byte)0xce,(byte)0xd1,(byte)0xd0,(byte)0xcd,(byte)0xdb,(byte)0xcd};
    private static final byte K2 = (byte)0xbe;
    private static final byte[] E3 = new byte[]{(byte)0xe1,(byte)0xb8,(byte)0xff,(byte)0xe1,(byte)0xad,(byte)0xa6,(byte)0xaf,(byte)0xba,(byte)0xe1,(byte)0xad,(byte)0xa1,(byte)0xa3,(byte)0xbe,(byte)0xa2,(byte)0xab,(byte)0xba,(byte)0xa7,(byte)0xa1,(byte)0xa0,(byte)0xbd};
    private static final byte K3 = (byte)0xce;
    private static final byte[] E4 = new byte[]{(byte)0x8b,(byte)0xd2,(byte)0x95,(byte)0x8b,(byte)0xc9,(byte)0xcb,(byte)0xc0,(byte)0xc1,(byte)0xc8,(byte)0xd7};
    private static final byte K4 = (byte)0xa4;
    private static final byte[] E5 = new byte[]{(byte)0x6b,(byte)0x32,(byte)0x75,(byte)0x6b,(byte)0x29,(byte)0x2b,(byte)0x20,(byte)0x21,(byte)0x28,(byte)0x37,(byte)0x6b};
    private static final byte K5 = (byte)0x44;
    private static final byte[] E6 = new byte[]{(byte)0xa2,(byte)0xfb,(byte)0xbc,(byte)0xa2,(byte)0xf8,(byte)0xfe,(byte)0xe8,(byte)0xff,(byte)0xa2,(byte)0xef,(byte)0xec,(byte)0xe1,(byte)0xec,(byte)0xe3,(byte)0xee,(byte)0xe8};
    private static final byte K6 = (byte)0x8d;
    private static final byte[] E7 = new byte[]{(byte)0xcc,(byte)0xd8,(byte)0xd9,(byte)0xc5,(byte)0xc2,(byte)0xdf,(byte)0xc4,(byte)0xd7,(byte)0xcc,(byte)0xd9,(byte)0xc4,(byte)0xc2,(byte)0xc3};
    private static final byte K7 = (byte)0xad;
    private static final byte[] E8 = new byte[]{(byte)0xa4,(byte)0xf1,(byte)0xbd,(byte)0xac,(byte)0xb5,(byte)0xf1,(byte)0xb7,(byte)0xb9,(byte)0xa5};
    private static final byte K8 = (byte)0xdc;
    private static final byte[] E9 = new byte[]{(byte)0x4e,(byte)0x69,(byte)0x6d,(byte)0x7e,(byte)0x69,(byte)0x7e,(byte)0x2c};
    private static final byte K9 = (byte)0x0c;
}

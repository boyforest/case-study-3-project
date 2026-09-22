package com.greenhill.coop.auth;

public final class UserContext {
    private static final ThreadLocal<MemberContext> CURRENT = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(MemberContext context) {
        CURRENT.set(context);
    }

    public static MemberContext get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}

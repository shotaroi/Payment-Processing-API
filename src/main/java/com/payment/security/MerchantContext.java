package com.payment.security;

import org.springframework.core.NamedThreadLocal;

public final class MerchantContext {

    private static final ThreadLocal<Long> MERCHANT_ID = new NamedThreadLocal<>("merchantId");

    private MerchantContext() {
    }

    public static void setMerchantId(Long merchantId) {
        MERCHANT_ID.set(merchantId);
    }

    public static Long getMerchantId() {
        return MERCHANT_ID.get();
    }

    public static void clear() {
        MERCHANT_ID.remove();
    }
}

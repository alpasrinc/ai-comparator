package com.example.aicomparator.dto;

/**
 * Bir prompt'un cache açısından iki parçası.
 *
 * <p>Prompt caching bir prefix eşleşmesidir: prefix'in herhangi bir yerindeki
 * tek bir byte değişikliği o noktadan sonrasının tamamını geçersiz kılar. Bu
 * yüzden stabil kısım fiziksel olarak değişken kısımdan önce gelmeli ve
 * sağlayıcıya hangisinin hangisi olduğu söylenebilmelidir.
 *
 * @param cacheablePrefix istekler arasında byte-byte aynı kalan kısım
 * @param volatileSuffix  her istekte değişen kısım
 */
public record PromptParts(String cacheablePrefix, String volatileSuffix) {

    /** Cache'lenecek stabil kısmı olmayan prompt (ör. münazara turları). */
    public static PromptParts volatileOnly(String whole) {
        return new PromptParts("", whole);
    }

    public boolean hasCacheablePrefix() {
        return !cacheablePrefix.isBlank();
    }

    public String joined() {
        return cacheablePrefix.isEmpty()
                ? volatileSuffix
                : cacheablePrefix + volatileSuffix;
    }
}

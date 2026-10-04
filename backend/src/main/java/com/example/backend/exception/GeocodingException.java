package com.example.backend.exception;

/**
 * Geocoding 処理の失敗内容と、実際に使用した住所候補を保持する例外。
 */
public class GeocodingException extends RuntimeException {

    private final String address;

    /**
     * 原因例外を保持せず、失敗内容と住所候補を持つ Geocoding 例外を生成する。
     *
     * @param message 失敗内容を示すメッセージ
     * @param address Geocoding に使用した住所候補
     */
    public GeocodingException(String message, String address) {
        super(message);
        this.address = address;
    }

    /**
     * 原因例外、失敗内容、住所候補を持つ Geocoding 例外を生成する。
     *
     * @param message 失敗内容を示すメッセージ
     * @param address Geocoding に使用した住所候補
     * @param cause Geocoding 処理中に発生した原因例外
     */
    public GeocodingException(String message, String address, Throwable cause) {
        super(message, cause);
        this.address = address;
    }

    /**
     * Geocoding に使用した住所候補を取得する。
     *
     * @return Geocoding に使用した住所候補
     */
    public String getAddress() {
        return address;
    }
}

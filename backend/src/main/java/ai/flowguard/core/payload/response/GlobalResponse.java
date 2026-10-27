package ai.flowguard.core.payload.response;

import ai.flowguard.core.enums.ApiStatusEnum;

public record GlobalResponse<T>(String status, String message, T data) {

    public static <T> GlobalResponse<T> success(T data) {
        return new GlobalResponse<>(ApiStatusEnum.SUCCESS.name(), "Success", data);
    }

    public static <T> GlobalResponse<T> success(String message, T data) {
        return new GlobalResponse<>(ApiStatusEnum.SUCCESS.name(), message, data);
    }

    public static GlobalResponse<Void> error(String message) {
        return new GlobalResponse<>(ApiStatusEnum.ERROR.name(), message, null);
    }
}

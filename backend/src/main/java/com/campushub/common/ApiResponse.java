package com.campushub.common;
public record ApiResponse<T>(boolean success, String message, T data) {
  public static <T> ApiResponse<T> ok(String m, T d){ return new ApiResponse<>(true,m,d); }
  public static <T> ApiResponse<T> fail(String m, T d){ return new ApiResponse<>(false,m,d); }
}

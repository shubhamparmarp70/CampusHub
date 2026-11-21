package com.campushub.common;
import org.springframework.http.HttpStatus;
/** Expected, user-facing failures: invalid state, bad OTP, insufficient stock, invalid discount... */
public class BusinessException extends RuntimeException {
  private final HttpStatus status; private final String code;
  public BusinessException(HttpStatus s, String code, String msg){ super(msg); this.status=s; this.code=code; }
  public HttpStatus status(){ return status; } public String code(){ return code; }
  public static BusinessException badRequest(String code,String m){ return new BusinessException(HttpStatus.BAD_REQUEST,code,m); }
  public static BusinessException conflict(String code,String m){ return new BusinessException(HttpStatus.CONFLICT,code,m); }
  public static BusinessException notFound(String m){ return new BusinessException(HttpStatus.NOT_FOUND,"NOT_FOUND",m); }
}

package com.campushub.files;
public interface StorageService {
  void put(String key, byte[] data, String contentType);
  void delete(String key);
  /** Short-lived signed download link. The bucket itself stays private. */
  String presignGet(String key, String downloadName);
}

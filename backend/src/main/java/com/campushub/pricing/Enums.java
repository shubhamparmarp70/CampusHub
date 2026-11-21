package com.campushub.pricing;
public final class Enums {
  private Enums(){}
  public enum PageSize { A4, A3, LEGAL }
  public enum ColorMode { BLACK_WHITE, COLOR }
  public enum Sides { SINGLE, DOUBLE }
  public enum Orientation { PORTRAIT, LANDSCAPE }
  public enum Binding { NONE, SPIRAL, TAPE }
  /** PER_PAGE: rate applies per page (double-sided gets an optional per-page discount). PER_SHEET: rate applies per physical sheet. */
  public enum DuplexRule { PER_PAGE, PER_SHEET }
}

package com.campushub.pricing;
import com.campushub.pricing.Enums.*;
public record PrintConfig(int totalPages, String pageRange, PageSize size, ColorMode color, Sides sides, int copies,
                          Binding binding, boolean lamination, boolean stapling) {}

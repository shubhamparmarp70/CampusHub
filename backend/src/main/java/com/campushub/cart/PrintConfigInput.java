package com.campushub.cart;
import com.campushub.pricing.Enums.*;
import jakarta.validation.constraints.*;
public record PrintConfigInput(@NotNull PageSize size, @NotNull ColorMode color, @NotNull Sides sides, @Min(1) @Max(500) int copies, @Size(max=120) String pageRange,
  Orientation orientation, Binding binding, boolean lamination, boolean stapling) {
  public PrintConfigInput {
    if (orientation == null) orientation = Orientation.PORTRAIT;
    if (binding == null) binding = Binding.NONE;
  }
}
